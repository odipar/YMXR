package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.ymxs.Chip;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Identity;
import org.ymxs.YMXS.Mapping;
import org.ymxs.YMXS.Part;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Remap;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Target;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Timing;
import org.ymxs.YMXS.Transformation;

/**
 * A YMXS tune as this schema's columns (doc/ymxs.md): the structure YMXS
 * defines, encoded as SPEC.md 1 encodes it.
 *
 * <p>YMXS defines what is true of a row; a column is written where its
 * value differs from the value the player keeps (R3.6, R4.6). So this
 * keeps the target, the select, the count and the mapping of each effect
 * as a player does, and a retune whose select did not move leaves the
 * control column unset. The registers a row sets are the row's, since a
 * YMXS row lists what it sets and leaves the rest alone.
 *
 * <p>Every conversion here passes through this: a dump is read into a
 * YMXS tune ({@link Ym}, {@link Ymx}) and mapped by this class, so the
 * columns follow from the structure alone.
 */
final class Schema {

    /** The effect each timer runs, SPEC.md 2.3: A, D, B, C. */
    private static final Timer[] TIMERS = {Timer.A, Timer.D, Timer.B, Timer.C};

    /** A tune mapped: its columns, its sources numbered as SPEC.md 3.1
     *  numbers them, and the rate its header records. */
    record Made(Columns columns, Sources sources, int rate) {
    }

    private Schema() {
    }

    /** {@code tune} as columns and sources, the line of an error numbering
     *  a row of the tune. */
    static Made of(org.ymxs.YMXS.Tune tune) {
        return of(new Padding.Padded(tune, IntStream.range(0, Tunes.rows(tune).size())
                .toArray()));
    }

    /** A tune that {@link Padding#toUnit} padded, as columns and sources:
     *  the frames of {@code padded} are, for each row of the tune, the row
     *  of the structure it comes from, or -1 for a row the padding added,
     *  and the line of an error numbers that row (SPEC.md 6.5).
     *
     * @throws IllegalArgumentException where the structure is one this
     *     format cannot encode: more sources than the source column
     *     numbers, more transformations than the mapping column numbers
     *     in a tune that maps a value, a count outside 0 to 255, a mapping
     *     through a transformation in a part other than the last, a target
     *     past 24, a source on two targets that mark different columns, a
     *     source on `setR7` and on another target, a source value past
     *     seven bits in its marker's column or past eight bits in another
     *     column, or a key past 63 on `setR7`
     */
    static Made of(Padding.Padded padded) {
        org.ymxs.YMXS.Tune tune = padded.tune();
        List<Row> rows = Tunes.rows(tune);
        int size = rows.size();
        int repeat = tune.table().repeat().orElse(size);
        List<Source> sources = Tunes.sources(tune);
        if (sources.size() > Sources.MOST) {
            throw new IllegalArgumentException("the tune runs " + sources.size()
                    + " sources, and a source column numbers " + Sources.MOST);
        }
        // Transformation n is the one a mapping column numbers n, in the
        // order of YMXS, SPEC.md 1.9 (SPEC.md 3.4.1). A file of version 10
        // has every one, and a file of version 9 none (SPEC.md 3.3.5).
        List<Transformation> list = Tunes.transformations(tune);
        Kept kept = new Kept(rows, list, Tunes.timers(tune));
        // A remap on a timer outside the tune leaves every column as it is
        // (rule 2(a)), so more than 127 transformations is an error only
        // where a start, or a remap on a timer of the tune, maps through
        // one.
        if (kept.transforms() && list.size() > Columns.MOST_TRANSFORMATIONS) {
            throw new IllegalArgumentException("the tune maps through " + list.size()
                    + " transformations, and a mapping column numbers "
                    + Columns.MOST_TRANSFORMATIONS);
        }
        byte[][] column = new byte[Columns.MAPPED][size];
        int used = 0;
        for (int f = 0; f < size; f++) {
            // After the wrap the player keeps what the last row left, so
            // the row the tune repeats to writes its targets again, and the
            // mapping of each effect a row maps through a transformation.
            if (f == repeat) {
                kept.wrap();
            }
            Row row = rows.get(f);
            byte[] out = new byte[Columns.MAPPED];
            registers(row, out);
            for (Map.Entry<Timer, Effect> one : Tunes.effects(row).entrySet()) {
                int i = effect(one.getKey());
                used |= effect(out, i, one.getValue(), sources, kept, padded.frames()[f]);
            }
            for (int c = 0; c < Columns.MAPPED; c++) {
                column[c][f] = out[c];
            }
        }
        // The version follows the rows: 10 where a row sets a mapping
        // column to a transformation, and otherwise 9, the thirty columns
        // and no transformation (SPEC.md 3.3.5).
        Columns columns = maps(column)
                ? new Columns(column, repeat, used | Columns.MAPS, tables(list))
                : new Columns(Arrays.copyOf(column, Columns.C), repeat, used);
        return new Made(columns, new Sources(sources(sources, markers(rows, sources))),
                tune.rate());
    }

    /** Whether a row sets a mapping column to a transformation, 1 to 127
     *  (SPEC.md 1.10.1). */
    private static boolean maps(byte[][] column) {
        for (int c = Columns.MAPPING; c < Columns.MAPPED; c++) {
            for (byte one : column[c]) {
                if ((one & 0x80) != 0 && (one & 0x7F) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The transformations as the tables of a tune file, entry k of each
     *  the value of key k (SPEC.md 3.4.1). */
    private static List<byte[]> tables(List<Transformation> list) {
        List<byte[]> out = new ArrayList<>();
        for (Transformation one : list) {
            byte[] entries = new byte[one.entries().size()];
            for (int k = 0; k < entries.length; k++) {
                entries[k] = (byte) (int) one.entries().get(k);
            }
            out.add(entries);
        }
        return out;
    }

    /** No deferred start is pending on the timer. */
    private static final int NONE = -2;

    /** A value the writer leaves open: the player may keep one of
     *  several. */
    private static final int OPEN = -1;

    /**
     * What a player keeps of each effect, as the rows set it (R4.6): the
     * kept target, select, count and mapping, OPEN where the rows leave
     * it open, and beside them what the writer reads off the rows to
     * leave a column unset.
     */
    private static final class Kept {

        final int[] target = new int[4];
        final int[] select = new int[4];
        final int[] count = new int[4];
        /** Whether the timer is known to be counting: a start sets it
         *  where the source it runs repeats, and a stop clears it. */
        final boolean[] counting = new boolean[4];
        /** The kept mapping (SPEC.md 1.10.3), 0 at the first row. */
        final int[] mapping = new int[4];
        /** The mapping the running effect writes through, where the rows
         *  fix it, and OPEN where a deferred start through another may be
         *  pending. */
        final int[] running = new int[4];
        /** The mapping of a deferred start that may be pending, NONE, or
         *  OPEN after the wrap. */
        final int[] pending = new int[4];
        /** Whether a row of the effect maps through a transformation: a
         *  start, or a remap on a timer of the tune. */
        final boolean[] maps = new boolean[4];
        /** Whether the effect's timer is a timer of the tune, which a row
         *  starts a source on (YMXS, SPEC.md 1.10). */
        final boolean[] claimed = new boolean[4];
        /** The target of the last start on the effect, and before its
         *  first start the target of that start: the target a line
         *  reports for a remap. */
        final Target[] runs = new Target[4];
        final List<Transformation> list;

        Kept(List<Row> rows, List<Transformation> list, Set<Timer> timers) {
            this.list = list;
            Arrays.fill(target, OPEN);
            Arrays.fill(pending, NONE);
            for (Timer timer : timers) {
                claimed[effect(timer)] = true;
            }
            for (Row row : rows) {
                for (Map.Entry<Timer, Effect> one : Tunes.effects(row).entrySet()) {
                    int i = effect(one.getKey());
                    if (one.getValue() instanceof Start start) {
                        if (runs[i] == null) {
                            runs[i] = Tunes.target(start.connection());
                        }
                        maps[i] |= !Tunes.isIdentity(start.mapping());
                    } else if (one.getValue() instanceof Remap remap && claimed[i]) {
                        maps[i] |= !Tunes.isIdentity(remap.mapping());
                    }
                }
            }
        }

        /** Whether a start, or a remap on a timer of the tune, maps
         *  through a transformation: the first such row on an effect sets
         *  its mapping column to 1 to T, so the file is version 10 (SPEC.md
         *  3.3.5). */
        boolean transforms() {
            for (boolean one : maps) {
                if (one) {
                    return true;
                }
            }
            return false;
        }

        /** The repeat row: the player keeps what the last row left, so
         *  the first pass leaves open the target of each effect, and the
         *  mapping of each effect a row maps through a transformation. An
         *  effect through the identity alone keeps 0 through the wrap. */
        void wrap() {
            Arrays.fill(target, OPEN);
            for (int i = 0; i < 4; i++) {
                if (maps[i]) {
                    mapping[i] = OPEN;
                    running[i] = OPEN;
                    pending[i] = OPEN;
                }
            }
        }
    }

    /** The registers the row sets, each in its column: the set bit where
     *  the column has one, and the bit beside it where a column that fills
     *  its byte is 0 (SPEC.md 1.1). */
    private static void registers(Row row, byte[] out) {
        for (Map.Entry<Register, Integer> one : Tunes.registers(row).entrySet()) {
            int c = Chip.number(one.getKey());
            int value = one.getValue();
            if (c < Columns.BESIDE_COLUMN.length && Columns.BESIDE_COLUMN[c] >= 0) {
                out[c] = (byte) value;
                if (value == 0) {
                    out[Columns.BESIDE_COLUMN[c]] |= (byte) Columns.BESIDE_BIT[c];
                }
            } else {
                out[c] |= (byte) (0x80 | value);
            }
        }
    }

    /** One row's operation on one effect, as its four columns and its
     *  mapping column; the bit of the effects word where the row starts
     *  one. {@code at} is the row of the structure, for the line of an
     *  error. */
    private static int effect(byte[] out, int i, Effect effect, List<Source> sources,
                              Kept kept, int at) {
        int t = Columns.EFFECT + 4 * i;
        int[] target = kept.target;
        int[] select = kept.select;
        int[] count = kept.count;
        boolean[] counting = kept.counting;
        switch (effect) {
            case Stop ignored -> {
                // The set bit with no source under it: the timer stops,
                // and the row leaves the rate columns and the mapping
                // column unset (SPEC.md 1.8, rule 3(c)). The stop drops a
                // pending start, and the next start keeps the kept mapping.
                out[t + 1] = (byte) 0x80;
                counting[i] = false;
                kept.pending[i] = NONE;
                kept.running[i] = kept.mapping[i];
                return 0;
            }
            case Remap remap -> {
                // A remap on a timer outside the tune leaves every column
                // as it is (rule 2(a)). On a timer of the tune, idle or
                // running, it sets the mapping column with the source
                // column unset (SPEC.md 1.10.2), and is left out where the
                // effect runs through its mapping already. Equal to the
                // kept mapping it is written all the same where a deferred
                // start through another mapping may be pending or may
                // have been performed (rule 2(g)).
                if (!kept.claimed[i]) {
                    return 0;
                }
                int m = mapped(remap.mapping(), kept.runs[i], kept.list, at);
                if (kept.running[i] == m) {
                    return 0;
                }
                out[Columns.MAPPING + i] = (byte) (0x80 | m);
                kept.mapping[i] = m;
                kept.running[i] = kept.pending[i] == NONE || kept.pending[i] == m ? m : OPEN;
                return 0;
            }
            case Start start -> {
                Target on = Tunes.target(start.connection());
                int m = mapped(start.mapping(), on, kept.list, at);
                kept.runs[i] = on;
                // A start whose mapping column is unset runs through the
                // kept mapping, and a deferred one keeps it from its row
                // (SPEC.md 1.10.2, 1.10.3).
                if (m != kept.mapping[i]) {
                    out[Columns.MAPPING + i] = (byte) (0x80 | m);
                    kept.mapping[i] = m;
                }
                int reaches = Tunes.number(on);
                if (reaches != target[i]) {
                    out[t] = (byte) (0x80 | reaches);
                    target[i] = reaches;
                }
                out[t + 1] = (byte) (0x80
                        | (sources.indexOf(Tunes.source(start.connection())) + 1));
                Timing timing = start.timing();
                int now = select(timing.prescaler());
                int rate = counted(timing.count(), at);
                int resets = resets(timing.timerReset(), timing.placeReset());
                boolean deferred = start.deferred();
                // A start on a timer already counting, at the rate it
                // counts, sets no rate column: step 2 resolves the source
                // and the ticks read it from here on, at the rate the
                // control register already has (SPEC.md 4). The timer is
                // known to be counting only where the source it runs
                // repeats, since a source that plays once stops it at its
                // marker (section 5) and no row says when. A deferred start
                // sets the control column, whose bit 3 marks it (1.9).
                if (resets != 0 || now != select[i] || rate != count[i] || !counting[i]
                        || deferred) {
                    out[t + 2] = (byte) (0x80 | resets | (deferred ? Columns.DEFERRED : 0)
                            | marked(rate) | now);
                    out[t + 3] = (byte) rate;
                }
                select[i] = now;
                count[i] = rate;
                counting[i] = Tunes.rows(Tunes.source(start.connection())).repeat()
                        .isPresent();
                if (deferred) {
                    // Until the tick that performs it, the timer counts at the
                    // rate of the effect before it, apart from the rate a row
                    // keeps: the next row that sets a rate sets both columns
                    // (4.8). The effect before it runs through its mapping
                    // until that tick, which the rows leave open.
                    select[i] = OPEN;
                    count[i] = OPEN;
                    counting[i] = false;
                    kept.running[i] = kept.running[i] == m ? m : OPEN;
                    kept.pending[i] = m;
                } else {
                    kept.running[i] = m;
                    kept.pending[i] = NONE;
                }
                return 1 << i | (deferred ? Columns.DEFERS : 0);
            }
            case Retune retune -> {
                int now = select(retune.timing().prescaler());
                int resets = resets(retune.timing().timerReset(),
                        retune.timing().placeReset());
                int rate = counted(retune.timing().count(), at);
                // A count of 0 is the value the MFP counts 256 for, and the
                // count column reserves 0 for the row that does not set it,
                // so bit 4 of the control column marks it (SPEC.md 1.9). The
                // row then sets the control column whether the select moved
                // or not.
                int mark = rate != count[i] ? marked(rate) : 0;
                if (now != select[i] || resets != 0 || mark != 0) {
                    out[t + 2] = (byte) (0x80 | resets | mark | now);
                }
                if (rate != count[i]) {
                    out[t + 3] = (byte) rate;
                }
                select[i] = now;
                count[i] = rate;
                return 0;
            }
        }
    }

    /**
     * The number a mapping column is written with for {@code mapping} on
     * {@code target} (SPEC.md 1.10.1): 0 for a mapping of no parts, and
     * otherwise the number of its last part, 0 for the identity and n for
     * transformation n of {@code list}. The last part maps the one value
     * of `setR0` to `setR13`, the volume of `setVoiceX` and `setNoiseX`,
     * the shape of `setBuzzer`, the coarse nibble of `setToneX` and the
     * high byte of `setEnvelope`.
     *
     * @throws IllegalArgumentException where a part before the last is a
     *     transformation, with the row {@code at}
     */
    private static int mapped(Mapping mapping, Target target, List<Transformation> list,
                              int at) {
        List<Part> parts = mapping.parts();
        if (parts.isEmpty()) {
            return 0;
        }
        int last = parts.size() - 1;
        for (int v = 0; v < last; v++) {
            if (!(parts.get(v) instanceof Identity)) {
                throw new IllegalArgumentException("row " + at + ": the mapping maps value " + v
                        + " of " + Tunes.name(target) + ", and a mapping column maps its value "
                        + last + " alone");
            }
        }
        return Tunes.number(list, parts.get(last));
    }

    /** The count column, which is the timer's data register (SPEC.md 1.9).
     *  Every value of that register is a count, 0 among them. */
    private static int counted(int count, int at) {
        if (count < 0 || count > Chip.MOST_COUNT) {
            throw new IllegalArgumentException("row " + at + ": a count of " + count
                    + ", and the count column reaches 0 to " + Chip.MOST_COUNT);
        }
        return count;
    }

    /** Bit 4 of the control column, which marks the count column's 0 as the
     *  value the MFP counts 256 for (SPEC.md 1.1, 1.9). */
    private static int marked(int count) {
        return count == 0 ? Columns.COUNT_VALUE : 0;
    }

    private static int resets(boolean timer, boolean place) {
        return (timer ? Columns.TIMER_RESET : 0) | (place ? Columns.PLACE_RESET : 0);
    }

    /** The prescaler select the control register reads (SPEC.md 1.9). */
    private static int select(Prescaler prescaler) {
        int by = Chip.divides(prescaler);
        for (int i = 1; i < Columns.PRESCALER.length; i++) {
            if (Columns.PRESCALER[i] == by) {
                return i;
            }
        }
        throw new IllegalArgumentException("no select divides by " + by);
    }

    /** The effect a timer runs (SPEC.md 2.3). */
    private static int effect(Timer timer) {
        for (int i = 0; i < TIMERS.length; i++) {
            if (TIMERS[i] == timer) {
                return i;
            }
        }
        throw new IllegalArgumentException("no effect runs on Timer " + timer);
    }

    /** The timer effect {@code i} runs (SPEC.md 2.3). */
    static Timer timer(int i) {
        return TIMERS[i];
    }

    /** A source no row starts: no target names a column for its marker,
     *  so the marker is in column 0, as every version before this one
     *  wrote every source. */
    private static final int UNSTARTED = Integer.MIN_VALUE;

    /** Where each source's marker is and which target it runs on: the
     *  column bit 7 marks, -1 where the target's register fills its byte;
     *  the target's number, which a counted source on `setR7` reads the
     *  port directions in for (rule 2(f)); whether every start of the
     *  source maps it through a transformation, so its rows are keys
     *  (3.1.6); and whether a start on `setR7` maps it through one
     *  (rule 2(f)). */
    private record Marks(int[] column, int[] target, boolean[] keyed, boolean[] mixer) {
    }

    /** The column of each source's row the marker is in, off the
     *  targets the tune starts that source on (SPEC.md 2.1, 3.2.1).
     *
     * @throws IllegalArgumentException where a target this version does not
     *     encode starts a source, where two targets of one source name
     *     different columns (rule 2(d)), or where a source runs on `setR7`
     *     and on another target (rule 2(f))
     */
    private static Marks markers(List<Row> rows, List<Source> sources) {
        int[] marker = new int[sources.size()];
        int[] target = new int[sources.size()];
        // The target of the last start of each source, for the line of
        // rule 2(f).
        Target[] last = new Target[sources.size()];
        boolean[] identity = new boolean[sources.size()];
        boolean[] mixer = new boolean[sources.size()];
        Arrays.fill(marker, UNSTARTED);   // no row starts it, so no target
                                          // names a column and the marker
                                          // is in column 0, as every
                                          // version before this one wrote it
        Arrays.fill(target, UNSTARTED);
        for (Row row : rows) {
            for (Effect one : Tunes.effects(row).values()) {
                if (!(one instanceof Start start)) {
                    continue;
                }
                Source source = Tunes.source(start.connection());
                int n = sources.indexOf(source);
                int number = Tunes.number(Tunes.target(start.connection()));
                boolean through = !Tunes.isIdentity(start.mapping());
                identity[n] |= !through;
                mixer[n] |= through && number == Columns.MIXER_TARGET;
                if (number >= Columns.MARKER.length) {
                    throw new IllegalArgumentException("the source " + Tunes.name(source)
                            + " runs on " + Tunes.name(Tunes.target(start.connection()))
                            + ", a target this version leaves to a later one"
                            + " (SPEC.md section 8)");
                }
                // -1: the target's register fills its byte, so the rows carry
                // no marker and a player counts them (SPEC.md 3.1).
                int at = Columns.MARKER[number];
                if (marker[n] != UNSTARTED && marker[n] != at) {
                    throw new IllegalArgumentException("the source " + Tunes.name(source)
                            + " runs on targets that mark column "
                            + marker[n] + " and column " + at + ", and a source has one"
                            + " marker column (SPEC.md rule 2(d))");
                }
                // Every row of a source on `setR7` has the two port
                // directions, and a tick on another target writes the row
                // whole to its register (rule 2(f)).
                if (target[n] != UNSTARTED && target[n] != number
                        && (target[n] == Columns.MIXER_TARGET
                        || number == Columns.MIXER_TARGET)) {
                    Target other = number == Columns.MIXER_TARGET
                            ? last[n] : Tunes.target(start.connection());
                    throw new IllegalArgumentException("the source " + Tunes.name(source)
                            + " runs on setR7 and on " + Tunes.name(other)
                            + ", and every row of a source on setR7 has bits 7 and 6 set"
                            + " (SPEC.md rule 2(f))");
                }
                marker[n] = at;
                target[n] = number;
                last[n] = Tunes.target(start.connection());
            }
        }
        boolean[] keyed = new boolean[sources.size()];
        for (int n = 0; n < keyed.length; n++) {
            keyed[n] = marker[n] != UNSTARTED && !identity[n];
        }
        return new Marks(marker, target, keyed, mixer);
    }

    /** The sources as tables of this format: a column a value of the row,
     *  bit 7 set on the last row of the marker's column (SPEC.md 3.2), and
     *  the row the source repeats to, its row count where it plays once. A
     *  source whose target's register fills its byte has no bit for the
     *  marker: every row is a whole byte and a player counts them (3.1).
     *  So is a source of one column whose every start maps it through a
     *  transformation, where a key of 128 or more is in a row (3.1.6). */
    private static List<Sources.Source> sources(List<Source> sources, Marks marks) {
        int[] marker = marks.column();
        List<Sources.Source> out = new ArrayList<>();
        for (int n = 0; n < sources.size(); n++) {
            Source source = sources.get(n);
            List<List<Integer>> values = Tunes.rows(source).rows();
            boolean counted = marker[n] == -1
                    || marker[n] == 0 && Tunes.columns(source) == 1 && marks.keyed()[n]
                    && pastMarker(values);
            // A tick of a counted source on `setR7` writes the row whole,
            // so the two port directions are in the source rather than
            // in the player (SPEC.md rule 2(f)).
            int ports = counted && marks.target()[n] == Columns.MIXER_TARGET
                    ? Columns.PORTS : 0;
            int at = counted || marker[n] == UNSTARTED ? 0 : marker[n];
            byte[][] columns = new byte[Tunes.columns(source)][values.size()];
            for (int r = 0; r < values.size(); r++) {
                for (int c = 0; c < columns.length; c++) {
                    int value = values.get(r).get(c);
                    // Through a transformation on `setR7` a key k is read
                    // as k + 192, the two port directions set (rule 2(f)).
                    if (marks.mixer()[n] && value > Columns.MOST_MIXER_KEY) {
                        throw new IllegalArgumentException("the source " + Tunes.name(source)
                                + " has the key " + value + " in row " + r
                                + " on setR7, and a key on setR7 is 0 to "
                                + Columns.MOST_MIXER_KEY);
                    }
                    int most = !counted && c == at ? Sources.MARK - 1 : 0xFF;
                    if (value < 0 || value > most) {
                        throw new IllegalArgumentException("the source " + Tunes.name(source)
                                + " has the value " + value + " in row " + r + " of column "
                                + c + ", and " + (!counted && c == at
                                ? "bit 7 of the marker's column is the marker"
                                : "a column is one byte"));
                    }
                    columns[c][r] = (byte) (value | ports);
                }
            }
            if (!counted) {
                columns[at][values.size() - 1] |= (byte) Sources.MARK;
            }
            out.add(new Sources.Source(kind(source), 0, columns,
                    Tunes.rows(source).repeat().orElse(values.size()), counted));
        }
        return out;
    }

    /** Whether a row of a source of one column is 128 or more, where bit
     *  7 of a marked source is the marker (3.2.1). */
    private static boolean pastMarker(List<List<Integer>> values) {
        for (List<Integer> row : values) {
            if (row.get(0) >= Sources.MARK) {
                return true;
            }
        }
        return false;
    }

    /** What a source of this shape sounds, for a report: the format names
     *  no kind, and a tune's use of the shape settles it (SPEC.md 2.2). */
    private static int kind(Source source) {
        int rows = Tunes.size(Tunes.rows(source));
        boolean repeats = Tunes.rows(source).repeat().isPresent();
        if (!repeats) {
            return Effects.DRUM;
        }
        return rows == 1 ? Effects.BUZZER : Effects.SID;
    }
}
