package org.ymxr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Tune;

/**
 * Rows added so that a tune's table packs at its unit (SPEC.md 6, rule
 * 6).
 *
 * <p>DTX packs a column in units of {@code k} bytes, so the row count
 * divides by {@code k} and so does the repeat row (DTX R5.6, R5.11). A
 * tune whose row count or repeat row does not divide packed at unit 1
 * instead, and a refill at unit 1 spans twice the rows of one at unit 2
 * and parses up to twice the blocks (experiments.md). So the table is
 * lengthened. At the repeat row, rows that set no column go in until it
 * divides, which moves the loop's first row later and lengthens what
 * plays before it. Then, where the count does not divide, a loop of fewer
 * than {@link #SHORT} rows is written again until it does, and a longer
 * loop, or a tune that plays once, gets rows that set no column at the
 * end. A row that sets no column leaves every register as it is and * every timer running, so the tune plays one more frame there with its effects
 * running through it; a loop written again plays as it did, since a pass
 * plays the same rows. At most {@code k} minus one rows that set no
 * column go in each place, and a tune that already divides is returned
 * as it is.
 */
final class Padding {

    /** A loop of fewer rows than this is written again rather than
     *  padded: a frame added to a loop of a few rows lengthens every pass
     *  by a sixty-fourth or more (experiments.md), and the rows written
     *  again keep the period the tune had, for a match a column. */
    static final int SHORT = 64;

    /** A row that sets no register and leaves every timer running. */
    static final Row UNSET = new Row(Map.of(), Map.of());

    private Padding() {
    }

    /** A tune padded, and the frame of the dump each row of its table
     *  answers to: -1 for a row that sets no column, and the rows of a
     *  loop written again answer to its frames again. Row for row where
     *  the tune divided as it was. */
    record Padded(Tune tune, int[] frames) {
    }

    /**
     * {@code tune} with rows added so that its table packs at {@code unit},
     * each addition noted on {@code report}.
     */
    static Padded toUnit(Tune tune, int unit, Report report) {
        Table<Row> table = tune.table();
        List<Row> rows = new ArrayList<>(table.rows());
        List<Integer> frames = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            frames.add(i);
        }
        OptionalInt repeat = table.repeat();
        int at = repeat.isPresent() ? repeat.getAsInt() : -1;
        if (unit > 1 && at >= 0 && at % unit != 0) {
            int added = unit - at % unit;
            rows.addAll(at, Collections.nCopies(added, UNSET));
            frames.addAll(at, Collections.nCopies(added, -1));
            report.note(noted(added, at, true, unit));
            at += added;
        }
        if (unit > 1 && rows.size() % unit != 0) {
            int loop = at >= 0 ? rows.size() - at : 0;
            if (loop > 0 && loop < SHORT) {
                List<Row> once = List.copyOf(rows.subList(at, rows.size()));
                List<Integer> onceFrames = List.copyOf(frames.subList(at, rows.size()));
                int times = 1;
                while (rows.size() % unit != 0) {
                    rows.addAll(once);
                    frames.addAll(onceFrames);
                    times++;
                }
                report.note(written(loop, times, unit));
            } else {
                int added = unit - rows.size() % unit;
                report.note(noted(added, rows.size(), false, unit));
                rows.addAll(Collections.nCopies(added, UNSET));
                frames.addAll(Collections.nCopies(added, -1));
            }
        }
        int[] of = frames.stream().mapToInt(Integer::intValue).toArray();
        if (rows.size() == table.rows().size()) {
            return new Padded(tune, of);
        }
        Table<Row> padded = new Table<>(List.copyOf(rows),
                at >= 0 ? OptionalInt.of(at) : OptionalInt.empty());
        return new Padded(new Tune(tune.title(), tune.composer(), tune.writer(), tune.rate(),
                padded), of);
    }

    /** The note for rows that set no column, which the Go tree writes
     *  word for word. */
    static String noted(int added, int at, boolean beforeRepeat, int unit) {
        return "padded: " + added + (added == 1 ? " unset row" : " unset rows") + " at row "
                + at + (beforeRepeat ? ", before the repeat row" : "")
                + ", so the table packs at unit " + unit;
    }

    /** The note for a loop written again, which the Go tree writes word
     *  for word. */
    static String written(int loop, int times, int unit) {
        return "padded: the loop's " + loop + (loop == 1 ? " row" : " rows") + " written "
                + (times == 2 ? "twice" : times + " times") + ", so the table packs at unit "
                + unit;
    }
}
