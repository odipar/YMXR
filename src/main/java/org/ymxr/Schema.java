package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.ymxs.Chip;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Prescaler;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Stop;
import org.ymxs.YMXS.Timer;

/**
 * A YMXS tune as this schema's columns (doc/ymxs.md): the structure YMXS
 * defines, encoded as SPEC.md 1 encodes it.
 *
 * <p>YMXS defines what is true of a row; a column is written where its
 * value differs from the value the player keeps (R3.6, R4.6). So this
 * keeps the target, the select and the count of each effect as a player
 * does, and a retune whose select did not move leaves the control column
 * unset. The registers a row sets are the row's, since a YMXS row lists
 * what it sets and leaves the rest alone.
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

    /** {@code tune} as columns and sources.
     *
     * @throws IllegalArgumentException where the structure is one this
     *     format cannot encode: a count of 256, a source value past seven
     *     bits, more sources than the source column numbers, or a source
     *     of more rows than a table has
     */
    static Made of(org.ymxs.YMXS.Tune tune) {
        List<Row> rows = Tunes.rows(tune);
        int frames = rows.size();
        int repeat = tune.table().repeat().orElse(frames);
        List<Source> sources = Tunes.sources(tune);
        if (sources.size() > Sources.MOST) {
            throw new IllegalArgumentException("the tune runs " + sources.size()
                    + " sources, and a source column numbers " + Sources.MOST);
        }
        byte[][] column = new byte[Columns.C][frames];
        int[] target = new int[4];
        int[] select = new int[4];
        // Whether the timer is known to be counting: a start sets it where
        // the source it runs repeats, and a stop clears it.
        boolean[] counting = new boolean[4];
        int[] count = new int[4];
        Arrays.fill(target, -1);
        int used = 0;
        for (int f = 0; f < frames; f++) {
            // After the wrap the player keeps what the last row left, so
            // the row the tune repeats to writes its targets again.
            if (f == repeat) {
                Arrays.fill(target, -1);
            }
            Row row = rows.get(f);
            byte[] out = new byte[Columns.C];
            registers(row, out);
            for (Map.Entry<Timer, Effect> one : Tunes.effects(row).entrySet()) {
                int i = effect(one.getKey());
                used |= effect(out, i, one.getValue(), sources, target, select, count,
                        counting, f);
            }
            for (int c = 0; c < Columns.C; c++) {
                column[c][f] = out[c];
            }
        }
        return new Made(new Columns(column, repeat, used),
                new Sources(sources(sources, markers(rows, sources))), tune.rate());
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

    /** One row's operation on one effect, as its four columns; the bit of
     *  the effects word where the row starts one. */
    private static int effect(byte[] out, int i, Effect effect, List<Source> sources,
                              int[] target, int[] select, int[] count, boolean[] counting,
                              int at) {
        int t = Columns.EFFECT + 4 * i;
        switch (effect) {
            case Stop ignored -> {
                // The set bit with no source under it: the timer stops,
                // and the row leaves the rate columns unset (SPEC.md 1.8).
                out[t + 1] = (byte) 0x80;
                counting[i] = false;
                return 0;
            }
            case Start start -> {
                int reaches = Tunes.number(Tunes.target(start));
                if (reaches != target[i]) {
                    out[t] = (byte) (0x80 | reaches);
                    target[i] = reaches;
                }
                out[t + 1] = (byte) (0x80 | (sources.indexOf(Tunes.source(start)) + 1));
                int now = select(Tunes.prescaler(start));
                int rate = counted(Tunes.count(start), at);
                int resets = resets(Tunes.timerReset(start), Tunes.placeReset(start));
                // A start on a timer already counting, at the rate it
                // counts, sets no rate column: step 2 resolves the source
                // and the ticks read it from here on, at the rate the
                // control register already has (SPEC.md 4). The timer is
                // known to be counting only where the source it runs
                // repeats, since a source that plays once stops it at its
                // marker (section 5) and no row says when.
                if (resets != 0 || now != select[i] || rate != count[i] || !counting[i]) {
                    out[t + 2] = (byte) (0x80 | resets | marked(rate) | now);
                    out[t + 3] = (byte) rate;
                }
                select[i] = now;
                count[i] = rate;
                counting[i] = Tunes.rows(Tunes.source(start)).repeat().isPresent();
                return 1 << i;
            }
            case Retune retune -> {
                int now = select(retune.timing().prescaler());
                int resets = resets(Tunes.timerReset(retune), Tunes.placeReset(retune));
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
            default -> throw new IllegalArgumentException("row " + at
                    + ": an effect this version does not read");
        }
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

    /** The column of each source's row the marker stands in, off the
     *  targets the tune starts that source on (SPEC.md 2.1, 3.2.1).
     *
     * @throws IllegalArgumentException where a target this version does not
     *     encode starts a source, or where two targets of one source name
     *     different columns (rule 2(d))
     */
    private static int[] markers(List<Row> rows, List<Source> sources) {
        int[] marker = new int[sources.size()];
        Arrays.fill(marker, -1);
        for (Row row : rows) {
            for (Effect one : Tunes.effects(row).values()) {
                if (!(one instanceof Start start)) {
                    continue;
                }
                int n = sources.indexOf(Tunes.source(start));
                int number = Tunes.number(Tunes.target(start));
                int at = number < Columns.MARKER.length ? Columns.MARKER[number] : -1;
                if (at < 0) {
                    throw new IllegalArgumentException("the source " + Tunes.name(
                            Tunes.source(start)) + " runs on " + Tunes.name(
                            Tunes.target(start)) + ", whose registers read every bit of"
                            + " their value, and bit 7 of a column is the marker"
                            + " (SPEC.md 2.1.2, 2.1.3)");
                }
                if (marker[n] >= 0 && marker[n] != at) {
                    throw new IllegalArgumentException("the source " + Tunes.name(
                            Tunes.source(start)) + " runs on targets that mark column "
                            + marker[n] + " and column " + at + ", and a source has one"
                            + " marker column (SPEC.md rule 2(d))");
                }
                marker[n] = at;
            }
        }
        return marker;
    }

    /** The sources as tables of this format: a column a value of the row,
     *  bit 7 set on the last row of the marker's column (SPEC.md 3.2), and
     *  the row the source repeats to, its row count where it plays once. */
    private static List<Sources.Source> sources(List<Source> sources, int[] marker) {
        List<Sources.Source> out = new ArrayList<>();
        for (int n = 0; n < sources.size(); n++) {
            Source source = sources.get(n);
            List<List<Integer>> values = Tunes.rows(source).rows();
            int at = Math.max(marker[n], 0);
            byte[][] columns = new byte[Tunes.columns(source)][values.size()];
            for (int r = 0; r < values.size(); r++) {
                for (int c = 0; c < columns.length; c++) {
                    int value = values.get(r).get(c);
                    int most = c == at ? Sources.MARK - 1 : 0xFF;
                    if (value < 0 || value > most) {
                        throw new IllegalArgumentException("the source " + Tunes.name(source)
                                + " has the value " + value + " in row " + r + " of column "
                                + c + ", and " + (c == at
                                ? "bit 7 of the marker's column is the marker"
                                : "a column is one byte"));
                    }
                    columns[c][r] = (byte) value;
                }
            }
            columns[at][values.size() - 1] |= (byte) Sources.MARK;
            out.add(new Sources.Source(kind(source), 0, columns,
                    Tunes.rows(source).repeat().orElse(values.size())));
        }
        return out;
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
