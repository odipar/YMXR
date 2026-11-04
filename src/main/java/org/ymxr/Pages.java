package org.ymxr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.dtx.Table;

/**
 * The pages a version 10 tune maps through (SPEC.md 3.4.4), as the bound
 * tune lists them after its transformation block (doc/BINARIES.md 1.2): the
 * tag {@code YP}, a count, then for each page its effect, its form and its
 * transformation, a byte each. A page is one transformation in one form on one effect,
 * the 256 bytes a lookup handler reads its key through.
 *
 * <p>The rows are read in the order a player reads them, through the
 * repeat row until each effect's state there is one it has had before. An
 * effect's state is its kept target, its kept mapping, whether a start
 * runs it, and the forms of the handlers its timer may run: a deferred
 * start adds its form to the forms before it, since the tick that performs
 * it is outside what the rows record.
 */
final class Pages {

    /** The forms of a page (SPEC.md 3.4.4): entry k at k; a marked
     *  column's, entry k at k and at k + 128 with bit 7 set; and setR7's,
     *  entry k at k + 192 with bits 7 and 6 set. */
    static final int PLAIN = 0;
    static final int MARKED = 1;
    static final int PORTS = 2;

    /** The most pages a list counts, its count being a byte. */
    static final int MOST = 255;

    /** The two bytes a list begins with, which mark it after the block. */
    static final byte[] TAG = {'Y', 'P'};

    /** The first target that maps its first value, setNoiseA, and the
     *  target of three columns that maps its last, setBuzzer (SPEC.md
     *  1.10.1, 2.1). */
    private static final int NOISE = 22;
    private static final int BUZZER = 21;

    /** setR7, whose counted source writes the ports' bits (2.1.5). */
    private static final int MIXER = 7;

    private Pages() {
    }

    /** One effect as the rows leave it. */
    private static final class Effect {
        int target;
        int mapping;
        boolean running;
        Set<Integer> forms = new TreeSet<>();

        List<Integer> state() {
            List<Integer> state = new ArrayList<>(List.of(target, mapping, running ? 1 : 0));
            state.addAll(forms);
            return state;
        }
    }

    /** The list of a tune file, empty below version 10. */
    static byte[] of(TuneFile tune) {
        if (tune.version() != Tune.VERSION) {
            return new byte[0];
        }
        List<LinkedHashSet<Integer>> pages = new ArrayList<>();
        Effect[] effects = new Effect[4];
        for (int i = 0; i < 4; i++) {
            pages.add(new LinkedHashSet<>());
            effects[i] = new Effect();
        }
        Table table = tune.table();
        byte[][] columns = new byte[Columns.MAPPED][];
        for (int c = Columns.EFFECT; c < Columns.MAPPED; c++) {
            columns[c] = table.column(c);
        }
        for (int row = 0; row < table.rows(); row++) {
            play(tune, columns, row, effects, pages);
        }
        // the loop, played again until each effect is at the repeat row as
        // a pass before left it
        Set<List<List<Integer>>> seen = new HashSet<>();
        while (table.repeat() < table.rows()) {
            List<List<Integer>> state = new ArrayList<>();
            for (Effect one : effects) {
                state.add(one.state());
            }
            if (!seen.add(state)) {
                break;
            }
            for (int row = table.repeat(); row < table.rows(); row++) {
                play(tune, columns, row, effects, pages);
            }
        }
        List<Integer> listed = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            for (int key : pages.get(i)) {
                if (listed.size() / 3 < MOST) {
                    listed.add(i);
                    listed.add(key & 0xFF);
                    listed.add(key >> 8);
                }
            }
        }
        byte[] list = new byte[TAG.length + 1 + listed.size()];
        System.arraycopy(TAG, 0, list, 0, TAG.length);
        list[TAG.length] = (byte) (listed.size() / 3);
        for (int k = 0; k < listed.size(); k++) {
            list[TAG.length + 1 + k] = listed.get(k).byteValue();
        }
        return list;
    }

    /** One row's effect columns, each effect the tune runs (SPEC.md 4.3,
     *  1.10.2). */
    private static void play(TuneFile tune, byte[][] columns, int row, Effect[] effects,
                             List<LinkedHashSet<Integer>> pages) {
        for (int i = 0; i < 4; i++) {
            if ((tune.effects() & 1 << i) == 0) {
                continue;
            }
            Effect one = effects[i];
            int t = Columns.EFFECT + 4 * i;
            int target = columns[t][row] & 0xFF;
            int source = columns[t + 1][row] & 0xFF;
            int control = columns[t + 2][row] & 0xFF;
            int mapping = columns[Columns.MAPPING + i][row] & 0xFF;
            if ((target & 0x80) != 0) {
                one.target = target & 0x7F;
            }
            if ((source & 0x80) != 0) {
                int s = source & 0x7F;
                if (s == 0) {
                    // a stop, its mapping column left unread
                    one.running = false;
                    one.forms.clear();
                    continue;
                }
                if ((mapping & 0x80) != 0) {
                    one.mapping = mapping & 0x7F;
                }
                int form = form(tune, s, one.target);
                if (one.mapping != 0 && form >= 0) {
                    pages.get(i).add(one.mapping << 8 | form);
                }
                boolean deferred = (control & 0x80) != 0 && (control & Columns.DEFERRED) != 0;
                if (!deferred || !one.running) {
                    one.forms.clear();
                }
                if (form >= 0) {
                    one.forms.add(form);
                }
                one.running = true;
            } else if ((mapping & 0x80) != 0) {
                one.mapping = mapping & 0x7F;
                if (one.running && one.mapping != 0) {
                    for (int form : one.forms) {
                        pages.get(i).add(one.mapping << 8 | form);
                    }
                }
            }
        }
    }

    /**
     * The form source {@code s} runs on {@code target} through a
     * transformation, or -1 for a square or a source of one row, whose
     * entries are in the handler (SPEC.md 3.4.4): a counted source plain,
     * and the ports' form on setR7; a marked source of one column marked;
     * of two, plain on the noise targets and marked on the others; of
     * three, marked on setBuzzer and plain on the voices.
     */
    static int form(TuneFile tune, int s, int target) {
        if (s > tune.sources().size()) {
            return -1;
        }
        Table source = tune.sources().get(s - 1);
        if (tune.counted().get(s - 1)) {
            return target == MIXER ? PORTS : PLAIN;
        }
        if (source.columns() == 1) {
            boolean shaped = source.repeat() == 0 && (source.rows() == 1 || source.rows() == 2);
            return shaped ? -1 : MARKED;
        }
        if (source.columns() == 2) {
            return target >= NOISE ? PLAIN : MARKED;
        }
        return target == BUZZER ? MARKED : PLAIN;
    }
}
