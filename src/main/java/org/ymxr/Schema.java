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
 * Encodes YMXS tune data as YMXR columns (doc/ymxs.md). Every converter
 * uses this stage after constructing a YMXS tune. Register writes
 * follow the row; effect columns are omitted when the player can reuse
 * kept values.
 *
 * <p>Transformation lookups are optional. Identity-only tunes use
 * version 9. Version 10 permits a lookup on the target's last value
 * alone. Kept, running and pending mappings are tracked separately
 * because a deferred start can change the running mapping between
 * frames (SPEC.md 6, rule 2(g)).
 */
final class Schema {

    /** The effect each timer runs, SPEC.md 2.3: A, D, B, C. */
    private static final Timer[] TIMERS = {Timer.A, Timer.D, Timer.B, Timer.C};

    /**
     * Encoded columns, sources in first-start order and frame rate.
     * Columns also includes the optional transformation tables (SPEC.md
     * 3.1, 3.4).
     */
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

    /**
     * Encodes a padded tune as columns and sources. Padding records the
     * original structure row for each output row, or -1 for an added
     * row; diagnostics use original row numbers (SPEC.md 6.5).
     *
     * <p>A row selecting a transformation produces version 10.
     * Identity-only rows produce version 9, without mapping columns or
     * tables.
     *
     * @throws IllegalArgumentException for an unencodable structure:
     * excess sources or transformations, count outside byte range,
     * lookup before the last target value, unsupported target,
     * incompatible shared-source markers or setR7 use, or a source
     * value or lookup key outside the encoded range
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
        // Transformation numbers follow YMXS first-use order. Version
        // 10 includes the full list; version 9 omits it (SPEC.md
        // 3.4.1).
        List<Transformation> list = Tunes.transformations(tune);
        Kept kept = new Kept(rows, list, Tunes.timers(tune));
        // Ignore remaps on timers outside the tune. The
        // transformation-count limit applies only when an encoded start
        // or remap uses a lookup (SPEC.md 6, rule 2(a)).
        if (kept.transforms() && list.size() > Columns.MOST_TRANSFORMATIONS) {
            throw new IllegalArgumentException("the tune maps through " + list.size()
                    + " transformations, and a mapping column numbers "
                    + Columns.MOST_TRANSFORMATIONS);
        }
        byte[][] column = new byte[Columns.MAPPED][size];
        int used = 0;
        for (int f = 0; f < size; f++) {
            // The wrap preserves player state. Write targets again
            // after the repeat row, restore mappings for effects using
            // lookups, and restore rate values that differ from the end
            // of the tune (SPEC.md 6, rules 2(g), 4(e)).
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
        // Choose version 10 only when a row selects a transformation.
        // Otherwise retain thirty columns and version 9 (SPEC.md
        // 3.3.5).
        Columns columns = maps(column)
                ? new Columns(column, repeat, used | Columns.MAPS, tables(list))
                : new Columns(Arrays.copyOf(column, Columns.C), repeat, used);
        return new Made(columns, new Sources(sources(sources, markers(rows, sources))),
                tune.rate());
    }

    /**
     * Whether any row selects a transformation number, requiring
     * version 10 (SPEC.md 3.3.5).
     */
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

    /**
     * Lookup tables in transformation-number order, with key k at entry
     * k (SPEC.md 3.4.1).
     */
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
     * The rate the rows leave each effect (rule 4(e)): the kept select and
     * count, each OPEN where the rows leave it open, after a deferred start
     * or at the repeat row, and whether the timer is known to be counting.
     */
    private static final class Rate {

        final int[] select = new int[4];
        final int[] count = new int[4];
        /** Whether the timer is known to be counting: a start sets it
         *  where the source it runs repeats, and a stop clears it. */
        final boolean[] counting = new boolean[4];

        /**
         * {@code effect} on effect {@code i}. A start or a retune sets the
         * select and the count to its rate, and a start sets the flag where
         * its source repeats. Until the tick that performs a deferred start
         * the timer counts at the rate of the effect before it, apart from
         * the rate a row keeps, so a deferred start opens the select and
         * the count and clears the flag: the next row that sets a rate sets
         * both columns (4.8). A stop clears the flag. Each value after the
         * operation follows from the operation alone, so the values after
         * the last row are the same whether the repeat row opens them or
         * not.
         */
        void keep(int i, Effect effect) {
            switch (effect) {
                case Start start -> {
                    boolean deferred = start.deferred();
                    select[i] = deferred ? OPEN : select(start.timing().prescaler());
                    count[i] = deferred ? OPEN : start.timing().count();
                    counting[i] = !deferred
                            && Tunes.rows(Tunes.source(start.connection())).repeat().isPresent();
                }
                case Retune retune -> {
                    select[i] = select(retune.timing().prescaler());
                    count[i] = retune.timing().count();
                }
                case Stop ignored -> counting[i] = false;
                case Remap ignored -> {
                }
            }
        }

        /** The repeat row: the select and the count, each open where its
         *  value before the repeat row differs from its value after the
         *  last row, {@code last}, and the flag cleared where it differs
         *  from the flag there. A value equal to it keeps its value: the
         *  second pass starts from that value, as the first does. */
        void wrap(Rate last) {
            for (int i = 0; i < 4; i++) {
                if (select[i] != last.select[i]) {
                    select[i] = OPEN;
                }
                if (count[i] != last.count[i]) {
                    count[i] = OPEN;
                }
                if (counting[i] != last.counting[i]) {
                    counting[i] = false;
                }
            }
        }
    }

    /**
     * Tracks kept target, select, count and mapping per effect, plus
     * information needed to omit redundant writes. OPEN marks a value
     * not determined by the rows. Running and pending mappings differ
     * from the kept mapping after deferred operations (SPEC.md 6, rule
     * 2(g)).
     */
    private static final class Kept {

        final int[] target = new int[4];
        /** The kept select, count and counting flag, as the rows read so
         *  far leave them. */
        final Rate rate = new Rate();
        /** The same three after the last row, which the repeat row reads
         *  (rule 4(e)). */
        final Rate last = new Rate();
        /**
         * Mapping reused by later starts, initially 0 for identity
         * (SPEC.md 1.10.3).
         */
        final int[] mapping = new int[4];
        /**
         * Mapping used by current ticks, or OPEN when a deferred start
         * may have installed a different mapping between frames.
         */
        final int[] running = new int[4];
        /**
         * Captured mapping of a possibly pending start, NONE when
         * absent, or OPEN after the wrap.
         */
        final int[] pending = new int[4];
        /**
         * Whether an encoded start or remap on this effect uses a
         * transformation.
         */
        final boolean[] maps = new boolean[4];
        /** Whether the effect's timer is a timer of the tune, which a row
         *  starts a source on (YMXS, SPEC.md 1.10). */
        final boolean[] claimed = new boolean[4];
        /**
         * Target of the latest start, or of the first start before one
         * occurs. Used for remap diagnostics.
         */
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
                    last.keep(i, one.getValue());
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

        /**
         * Whether an encoded start or remap uses a transformation. Its
         * first selection requires version 10 (SPEC.md 3.3.5).
         */
        boolean transforms() {
            for (boolean one : maps) {
                if (one) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Restores required writes at the repeat row. Open targets and
         * all three mapping values for effects using lookups;
         * identity-only effects retain mapping 0. Open rate values only
         * when the end state differs from the state before the repeat
         * row (SPEC.md 6, rules 2(g), 4(e)).
         */
        void wrap() {
            Arrays.fill(target, OPEN);
            rate.wrap(last);
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

    /**
     * Encodes one effect operation into its four columns and optional
     * mapping column, updating effects-used bits. {@code at} is the
     * original structure row for diagnostics.
     */
    private static int effect(byte[] out, int i, Effect effect, List<Source> sources,
                              Kept kept, int at) {
        int t = Columns.EFFECT + 4 * i;
        int[] target = kept.target;
        int[] select = kept.rate.select;
        int[] count = kept.rate.count;
        boolean[] counting = kept.rate.counting;
        switch (effect) {
            case Stop ignored -> {
                // A stop sets source 0 and leaves rate and mapping
                // columns unset. It cancels a pending start but
                // preserves the kept mapping for later starts (SPEC.md
                // 6, rule 3(c)).
                out[t + 1] = (byte) 0x80;
                kept.rate.keep(i, effect);
                kept.pending[i] = NONE;
                kept.running[i] = kept.mapping[i];
                return 0;
            }
            case Remap remap -> {
                // Ignore a remap outside the tune. Otherwise write it
                // with the source column unset when it differs from the
                // running mapping, even if it equals the kept mapping.
                // A deferred start may have changed current ticks
                // (SPEC.md 6, rule 2(g)).
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
                // An unset mapping column reuses the kept value. A
                // deferred start captures it now for installation at
                // the boundary (SPEC.md 1.10.3).
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
                kept.rate.keep(i, effect);
                if (deferred) {
                    // A deferred start preserves current ticks until
                    // the boundary; frame rows alone do not determine
                    // when it is installed.
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
                kept.rate.keep(i, effect);
                return 0;
            }
        }
    }

    /**
     * Encodes the mapping's last part: 0 for identity or an empty
     * mapping; otherwise its transformation number in {@code list}.
     * Earlier parts must be identity. The last value is volume for
     * voice/noise targets, shape for buzzer, the coarse nibble for tone,
     * envelope high byte for envelope, or the sole register value
     * (SPEC.md 1.10.1).
     *
     * @throws IllegalArgumentException if an earlier part uses a
     * transformation, reporting structure row {@code at}
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

    /**
     * Source encoding attributes: marker column (-1 for counted
     * full-byte targets), target number, whether every start uses a
     * lookup, and whether a setR7 start uses one. These select counted
     * encoding for high keys and preserve mixer port bits (SPEC.md
     * 3.1.6; rule 2(f)).
     */
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

    /**
     * Encodes DTX1 sources with one byte column per target value.
     * Marked sources set bit 7 of the marker column on the last row
     * alone; counted sources preserve all byte bits. Full-byte targets
     * require counting. A one-column source used only through lookups
     * also requires counting when a key reaches 128 (SPEC.md 3.1.6,
     * 3.2).
     */
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
                    // Store setR7 key k as k + 192, preserving both
                    // port directions (SPEC.md 6, rule 2(f)).
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
