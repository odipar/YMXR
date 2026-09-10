package org.ymxr;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.ymxs.Chip;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Target;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * A YMX file read into a YMXS tune: its fourteen register streams as the
 * rows a dump's registers make ({@link Ym}), and its script's four
 * channels as the four effects.
 *
 * <p>A channel of YMX and an effect here are the same thing: a source on a
 * target at a timer's rate. What each opcode does to one is YMX's SPEC.md
 * 3; what an effect is here is SPEC.md 1.8 and 1.9, so the walk below
 * reads one and writes the other.
 */
final class Ymx {

    /** Voice 3 is no voice: three opcodes read it as a second form. */
    private static final int NO_VOICE = 3;

    /** The streams the script stands in (YMX, SPEC.md 2). */
    private static final int STREAM_M = 14;
    private static final int STREAM_X = 15;
    private static final int STREAM_A0 = 17;

    /** The opcodes an action byte's top three bits select. */
    private static final int RESUME = 0;
    private static final int HOLD = 1;
    private static final int RELEASE = 2;
    private static final int START_TOGGLE = 3;
    private static final int RETUNE = 4;
    private static final int START_RETRIGGER = 5;
    private static final int START_PCM = 6;
    private static final int START_PCM_PREEMPT = 7;

    /** The sources a script names, one for each distinct table: two
     *  channels running one shape run one source (SPEC.md 2.2). */
    private static final class Built {

        private final Map<String, Source> made = new LinkedHashMap<>();

        Source of(String name, List<Integer> values, int repeat) {
            String key = values + "/" + repeat;
            Source known = made.get(key);
            if (known != null) {
                return known;
            }
            Source source = repeat < values.size() ? Tunes.repeating(name, values, repeat)
                    : Tunes.once(name, values);
            made.put(key, source);
            return source;
        }
    }

    private Ymx() {
    }

    /** The file as a tune, repeating to {@code repeat}. */
    static Tune read(YmxToYmxr.Dumped read, YmDump.Song song, int repeat, Report report) {
        List<Row> registers = Tunes.rows(Ym.read(song, new Sources(List.of()), repeat, report));
        int frames = read.frames();
        int[] owned = owned(read);
        Built built = new Built();
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        for (int f = 0; f < frames; f++) {
            effects.add(new EnumMap<>(Timer.class));
        }
        int used = script(read, built, effects, repeat, report);
        List<Row> rows = new ArrayList<>();
        for (int f = 0; f < frames; f++) {
            Map<Register, Integer> sets = new EnumMap<>(registers.get(f).registers());
            // A voice a timer owns is one the frame leaves unwritten (YMX,
            // SPEC.md 2.1), which is what SPEC.md 6 rule 1 requires of a
            // row here. M's bits 7 to 5 are read only where bit 4 is set.
            for (int voice = 0; voice < 3; voice++) {
                if ((owned[f] & (1 << voice)) != 0) {
                    sets.remove(Chip.register(8 + voice));
                }
            }
            rows.add(new Row(sets, effects.get(f)));
        }
        // The row a tune repeats to stops every effect it does not start,
        // so the wrap resumes from a known setting, as a dump's conversion
        // does it ({@link Ym}). Only the effects the tune runs: a row sets
        // no column of one it does not run (section 6 rule 2), so this
        // waits until the walk says which run.
        if (repeat < frames) {
            Map<Timer, Effect> at = new EnumMap<>(rows.get(repeat).effects());
            for (int c = 0; c < 4; c++) {
                if ((used & (1 << c)) != 0 && !at.containsKey(Schema.timer(c))) {
                    at.put(Schema.timer(c), Tunes.STOP);
                }
            }
            rows.set(repeat, new Row(rows.get(repeat).registers(), at));
        }
        return new Tune(song.name(), "", "ymx-to-ymxs", read.rate(),
                repeat < frames ? Tunes.repeating(rows, repeat) : Tunes.once(rows));
    }

    /** The voices YMX's master stream reserves for a timer, a frame each:
     *  bits 7 to 5 of M, read where its bit 4 is set. */
    private static int[] owned(YmxToYmxr.Dumped read) {
        int[] out = new int[read.frames()];
        int skip = 0;
        for (int f = 0; f < read.frames(); f++) {
            int master = read.streams()[STREAM_M][f] & 0xFF;
            if ((master & 0x10) != 0) {
                skip = (master >> 5) & 7;
            }
            out[f] = skip;
        }
        return out;
    }

    /** The script's four channels as the four effects, and the effects the
     *  tune ever starts. */
    private static int script(YmxToYmxr.Dumped read, Built built,
                              List<Map<Timer, Effect>> effects, int repeat, Report report) {
        int used = 0;
        @Nullable Target[] target = new Target[4];
        // The select and the count each channel runs at: an opcode that
        // repatches a parameter leaves the rest of the rate as it is, and
        // a start writes the whole rate (SPEC.md 1.9).
        int[] select = {0, 0, 0, 0};
        int[] count = {0, 0, 0, 0};
        // Whether each channel's timer runs. Bit 6 affects a running timer
        // and a stopped one starts on the select with it or without (1.9),
        // so a start sets the reset where the timer is stopped, as section
        // 6 rule 5 requires, and a start over a running stream leaves it
        // clear rather than restarting the period.
        boolean[] running = {false, false, false, false};
        List<String> left = new ArrayList<>();
        int kept = 0;
        for (int f = 0; f < read.frames(); f++) {
            int master = read.streams()[STREAM_M][f] & 0xFF;
            Map<Timer, Effect> here = effects.get(f);
            int preempted = 0;
            for (int c = 0; c < 4; c++) {
                if ((master & (1 << c)) == 0) {
                    continue;
                }
                Timer timer = Schema.timer(c);
                int action = read.streams()[STREAM_A0 + 2 * c][f] & 0xFF;
                int written = read.streams()[STREAM_A0 + 2 * c + 1][f] & 0xFF;
                int opcode = action >> 5;
                int voice = (action >> 3) & 3;
                int low = action & 7;
                // A count byte of 0 is the MFP's 256, which the count
                // column does not reach (SPEC.md 1.9), and a select of 0
                // stops a timer. Either way the rate the effect runs at
                // does not move, which is what the columns encoded before
                // this conversion read the structure.
                if (written == 0 && count[c] != 0) {
                    kept++;
                }
                int rate = written == 0 ? count[c] : written;
                if (low == 0 && select[c] != 0) {
                    kept++;
                }
                int at = low == 0 ? select[c] : low;
                int volume = voice == NO_VOICE ? 0 : read.streams()[8 + voice][f] & 0x1F;
                switch (opcode) {
                    case START_TOGGLE -> {
                        Target on = Tunes.setting(Chip.register(8 + voice));
                        target[c] = on;
                        here.put(timer, new Start(on,
                                built.of("square " + volume, toggle(volume), 0),
                                Chip.prescaler(Columns.PRESCALER[at]), rate,
                                !running[c], true));
                        select[c] = at;
                        count[c] = rate;
                        running[c] = true;
                        used |= 1 << c;
                    }
                    case START_RETRIGGER -> {
                        int shape = (read.streams()[STREAM_X][f] >> 4) & 0x0F;
                        Target on = Tunes.setting(Register.R13);
                        target[c] = on;
                        here.put(timer, new Start(on,
                                built.of("buzzer " + shape, retrigger(shape), 0),
                                Chip.prescaler(Columns.PRESCALER[at]), rate,
                                !running[c], true));
                        select[c] = at;
                        count[c] = rate;
                        running[c] = true;
                        used |= 1 << c;
                    }
                    case START_PCM, START_PCM_PREEMPT -> {
                        if (volume >= read.samples().length) {
                            left.add("frame " + f + " starts sample " + volume
                                    + ", which the file does not carry");
                            break;
                        }
                        int loop = read.loops()[volume];
                        List<Integer> rows = values(read.samples()[volume]);
                        Target on = Tunes.setting(Chip.register(8 + voice));
                        target[c] = on;
                        here.put(timer, new Start(on,
                                built.of("drum " + volume, rows,
                                        loop == 0xFFFF ? rows.size() : loop),
                                Chip.prescaler(Columns.PRESCALER[at]), rate,
                                !running[c], true));
                        select[c] = at;
                        count[c] = rate;
                        running[c] = true;
                        used |= 1 << c;
                        if (opcode == START_PCM_PREEMPT) {
                            preempted |= (read.streams()[STREAM_X][f] & 0x0F) & ~(1 << c);
                        }
                    }
                    case RELEASE -> {
                        here.put(timer, Tunes.STOP);
                        running[c] = false;
                    }
                    case RETUNE -> {
                        // A new rate on a running stream, its place kept.
                        // Addressed to a voice it repatches the volume from
                        // the voice's byte first (YMX, SPEC.md 3.1), which
                        // is a source of the row count the effect already
                        // runs, so rule 5 lets the row leave the place
                        // alone: the toggle keeps its phase and the half it
                        // stands in.
                        Target on = target[c];
                        if (voice != NO_VOICE && on != null && volume(on)) {
                            here.put(timer, new Start(on,
                                    built.of("square " + volume, toggle(volume), 0),
                                    Chip.prescaler(Columns.PRESCALER[at]), rate,
                                    false, false));
                        } else {
                            here.put(timer, new Retune(Chip.prescaler(Columns.PRESCALER[at]),
                                    rate, false, false));
                        }
                        select[c] = at;
                        count[c] = rate;
                    }
                    case HOLD -> {
                        // HOLD's low bits are flags and not a prescaler
                        // (YMX, SPEC.md 2.4): 1 reloads the count, 2 the
                        // toggle's volume, 4 the retrigger's shape.
                        if ((low & 1) != 0 && written != 0) {
                            count[c] = written;
                        }
                        Target on = target[c];
                        @Nullable Source source = null;
                        if ((low & 2) != 0 && on != null && volume(on)) {
                            source = built.of("square " + volume, toggle(volume), 0);
                        } else if ((low & 4) != 0) {
                            int shape = (read.streams()[STREAM_X][f] >> 4) & 0x0F;
                            source = built.of("buzzer " + shape, retrigger(shape), 0);
                        }
                        if (source != null && on != null) {
                            // The parameter is repatched and the stream runs
                            // on, so the place stands: the source has the
                            // row count the effect already runs and rule 5
                            // lets the row leave the place alone.
                            here.put(timer, new Start(on, source,
                                    Chip.prescaler(Columns.PRESCALER[select[c]]), count[c],
                                    false, false));
                        } else if ((low & 1) != 0) {
                            here.put(timer, new Retune(
                                    Chip.prescaler(Columns.PRESCALER[select[c]]), count[c],
                                    false, false));
                        }
                    }
                    case RESUME -> left.add("frame " + f + " resumes channel " + c
                            + ", which this version does not carry");
                    default -> left.add("frame " + f + " runs opcode " + opcode);
                }
            }
            // A preempt stops other channels (YMX, SPEC.md 3, opcode 7),
            // after the frame's channels have acted.
            for (int other = 0; other < 4; other++) {
                if ((preempted & (1 << other)) != 0) {
                    here.put(Schema.timer(other), Tunes.STOP);
                    running[other] = false;
                }
            }
        }
        if (kept > 0) {
            report.note(kept + " rows write a count or a select of 0, which the columns"
                    + " read as the rate the effect already runs at");
        }
        for (String said : left.subList(0, Math.min(left.size(), 3))) {
            report.note(said);
        }
        if (left.size() > 3) {
            report.note((left.size() - 3) + " more the script does that this"
                    + " version leaves behind");
        }
        return used;
    }

    /** Whether a target writes a voice's volume register. */
    private static boolean volume(Target target) {
        int at = Tunes.number(target);
        return at >= 8 && at <= 10;
    }

    /** A toggle stream's two rows: the loud half, then the silent one. */
    private static List<Integer> toggle(int level) {
        return List.of(level & 0x1F, 0);
    }

    /** A retrigger stream's one row, the shape. */
    private static List<Integer> retrigger(int shape) {
        return List.of(shape & 0x0F);
    }

    /** A sample's bytes as source values: YMX writes its end marker in
     *  bit 7, which is this format's marker (SPEC.md 3.2), so the sample
     *  ends at the first byte that carries one and the values are the
     *  seven bits under it. */
    private static List<Integer> values(byte[] rows) {
        List<Integer> out = new ArrayList<>(rows.length);
        for (byte row : rows) {
            out.add(row & 0x7F);
            if ((row & 0x80) != 0) {
                break;
            }
        }
        return out;
    }
}
