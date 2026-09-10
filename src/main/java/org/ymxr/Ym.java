package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.ymxs.Chip;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Source;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * A YM5!/YM6! dump read into a YMXS tune: one row a frame, and a source
 * for each distinct sound the dump's effect slots produce. {@link Schema}
 * maps what this produces onto the columns.
 *
 * <p>A dump contains every register of every frame, so a row here sets a
 * register where the dump's value changed. A register an effect is running
 * on belongs to that effect, and no row sets it before the row that stops
 * it, as rule 1 of SPEC.md 6 requires.
 *
 * <p>The two slots run on Timers A and D (SPEC.md 2.3). A slot sounding a
 * square wave becomes a source of a level and a silence; one restarting
 * the envelope, a source of the shape; one playing a recording, a source
 * of the sample's levels and a closing row at mid-scale. A recording owns
 * the voice's volume for its duration at its rate, and silences that
 * voice's tone and noise meanwhile.
 *
 * <p>A recording on a voice excludes a square wave on it: the dump's
 * player runs one effect a voice, and the recording is that effect.
 *
 * <p>The row a tune repeats to sets every register except those an effect
 * is running on, and starts every effect that ran up to it or runs into
 * the wrap, so the wrap resumes from the chip as the rows left it.
 */
final class Ym {

    private Ym() {
    }

    /** The dump as a tune, repeating to {@code repeat}, its row count
     *  where it plays once. {@code sources} numbers the sources as it
     *  meets them, and the report counts what was dropped. */
    static Tune read(YmDump.Song song, Sources sources, int repeat, Report report) {
        int frames = song.frames();
        List<Row> rows = new ArrayList<>();
        Map<Integer, Source> made = new LinkedHashMap<>();
        int[] wrote = new int[14];
        Arrays.fill(wrote, -1);
        Effects.Slot[] running = {Effects.Slot.EMPTY, Effects.Slot.EMPTY};
        int[] runningNumber = {0, 0};
        int[] selectHeld = {0, 0};
        int[] countHeld = {0, 0};
        int[] drumEnd = {-1, -1};
        boolean[] stopAtRepeat = {false, false};
        // What each effect last ran, whether or not it runs now: a row
        // that stops an effect moves no place, so a square that starts
        // again reads the row it left off at.
        int[] lastKind = {0, 0};
        int[] lastTarget = {-1, -1};
        for (int f = 0; f < frames; f++) {
            // The row the tune repeats to sets every register but R13 and
            // the ones an effect owns there, and every effect, so the wrap
            // resumes from a known setting: row 0 too, where the tune
            // repeats to it.
            boolean keyframe = f == repeat;
            if (keyframe) {
                Arrays.fill(wrote, -1);
                Arrays.fill(lastKind, 0);
                Arrays.fill(lastTarget, -1);
                for (int i = 0; i < 2; i++) {
                    stopAtRepeat[i] = running[i].on();
                }
            }
            int[] reg = Columns.registers(song, f);
            Effects.Slot[] slot = Effects.of(song, f).clone();
            int[] number = new int[2];
            // The drums' numbers first: a drum on a voice preempts a SID
            // there, so a SID the dump flags on a voice where the other
            // slot's drum runs, or starts in this frame, is not started. At
            // the repeat row a running drum is cut and the SID starts.
            for (int i = 0; i < 2; i++) {
                if (slot[i].on() && slot[i].kind() == Effects.DRUM) {
                    number[i] = sources.number(slot[i], report);
                    if (number[i] == 0) {
                        slot[i] = Effects.Slot.EMPTY;
                    }
                }
            }
            for (int i = 0; i < 2; i++) {
                if (!slot[i].on() || slot[i].kind() == Effects.DRUM) {
                    continue;
                }
                int other = 1 - i;
                boolean drumStarts = slot[other].on() && slot[other].kind() == Effects.DRUM
                        && slot[other].voice() == slot[i].voice();
                // a drum the other slot replaces in this frame, by another
                // kind or another voice, runs no longer
                boolean replaced = slot[other].on() && !drumStarts;
                boolean drumRuns = running[other].kind() == Effects.DRUM
                        && running[other].voice() == slot[i].voice() && f < drumEnd[other]
                        && !keyframe && !replaced;
                if (slot[i].kind() == Effects.SID && (drumRuns || drumStarts)) {
                    slot[i] = Effects.Slot.EMPTY;
                    report.preempted++;
                    continue;
                }
                number[i] = sources.number(slot[i], report);
                if (number[i] == 0) {
                    slot[i] = Effects.Slot.EMPTY;
                }
            }
            Map<Timer, Effect> effects = new EnumMap<>(Timer.class);
            int owned = 0;
            for (int i = 0; i < 2; i++) {
                Timer timer = Schema.timer(i);
                boolean drum = running[i].kind() == Effects.DRUM;
                if (!slot[i].on()) {
                    if (keyframe && drum && f < drumEnd[i]) {
                        report.cutAtRepeat++;
                    }
                    if (drum && f < drumEnd[i] && !keyframe) {
                        // the drum plays on: the dump flags only its trigger
                    } else if (running[i].on() || keyframe) {
                        effects.put(timer, Tunes.STOP);
                        running[i] = Effects.Slot.EMPTY;
                    }
                } else {
                    boolean starting = keyframe || slot[i].kind() == Effects.DRUM
                            || running[i].kind() != slot[i].kind()
                            || running[i].target() != slot[i].target()
                            || runningNumber[i] != number[i];
                    if (starting) {
                        // Bit 6 stops the timer, writes the count and starts
                        // it, so the timer runs a whole period and loses
                        // what it had run of the last one. Section 6 rule 5
                        // sets the bit where the timer is stopped; where the
                        // timer runs, the count this row writes loads when
                        // the running count reaches zero, which moves the
                        // pitch without a break (1.9). A timer is stopped
                        // where no effect runs on it, the row that stopped
                        // the effect having written select 0, and where a
                        // digidrum's source has run out: that source does
                        // not repeat, so its last tick stops the timer
                        // (section 5). A SID voice's source and a sync
                        // buzzer's repeat, and run until a row stops them.
                        // The keyframe sets the bit in either case.
                        boolean stopped = keyframe || !running[i].on()
                                || running[i].kind() == Effects.DRUM && f >= drumEnd[i];
                        // Where a square replaces a square on the same
                        // target the row leaves the place alone: the row
                        // number in the place counts into the new source's
                        // rows (1.9). Every level is a separate source, so
                        // a square whose level moves starts one each time,
                        // and its two ticks either side of the start fall a
                        // whole period apart. A drum struck again reads its
                        // first row, so it moves the place as any other
                        // start does.
                        boolean unmoved = !keyframe && slot[i].kind() == Effects.SID
                                && lastKind[i] == Effects.SID
                                && lastTarget[i] == slot[i].target();
                        effects.put(timer, new Start(Tunes.setting(Chip.register(slot[i].target())),
                                source(made, sources, number[i]),
                                Chip.prescaler(Columns.PRESCALER[slot[i].select()]),
                                slot[i].count(), stopped, !unmoved));
                        running[i] = slot[i];
                        runningNumber[i] = number[i];
                        lastKind[i] = slot[i].kind();
                        lastTarget[i] = slot[i].target();
                        if (slot[i].kind() == Effects.DRUM) {
                            drumEnd[i] = f + Columns.duration(
                                    sources.get(number[i]).rows().length,
                                    slot[i].select(), slot[i].count(), song.playerHz());
                        }
                    } else if (slot[i].select() != selectHeld[i]
                            || slot[i].count() != countHeld[i]) {
                        effects.put(timer, new org.ymxs.YMXS.Retune(
                                Chip.prescaler(Columns.PRESCALER[slot[i].select()]),
                                slot[i].count(), false, false));
                    }
                    selectHeld[i] = slot[i].select();
                    countHeld[i] = slot[i].count();
                }
                if (running[i].kind() == Effects.SID || running[i].kind() == Effects.DRUM) {
                    owned |= 1 << running[i].target();
                }
                if (running[i].kind() == Effects.DRUM) {
                    reg[7] |= 0x09 << running[i].voice();
                }
            }
            Map<Register, Integer> registers = new EnumMap<>(Register.class);
            for (int c = 0; c < 13; c++) {
                if (Columns.BESIDE_COLUMN[c] >= 0) {
                    if (reg[c] != wrote[c]) {
                        registers.put(Chip.register(c), reg[c]);
                        wrote[c] = reg[c];
                    }
                } else if ((owned & 1 << c) != 0) {
                    wrote[c] = -1;                   // the effect's register,
                } else if (reg[c] != wrote[c]) {     // and no row's
                    registers.put(Chip.register(c), reg[c]);
                    wrote[c] = reg[c];
                }
            }
            if (reg[13] >= 0) {
                registers.put(Register.R13, reg[13]);
            }
            rows.add(new Row(registers, effects));
        }
        // The keyframe's stop is for an effect that ran up to the repeat
        // row, or runs into the wrap, so that the wrap resumes from a known
        // setting; the row leaves an effect that did neither, or that the
        // tune never runs, alone.
        if (repeat < frames) {
            Map<Timer, Effect> keyframe = new EnumMap<>(rows.get(repeat).effects());
            for (int i = 0; i < 2; i++) {
                Timer timer = Schema.timer(i);
                if (keyframe.get(timer) instanceof org.ymxs.YMXS.Stop
                        && !stopAtRepeat[i] && !running[i].on()) {
                    keyframe.remove(timer);
                }
            }
            rows.set(repeat, new Row(rows.get(repeat).registers(), keyframe));
        }
        Table<Row> table = repeat < frames ? Tunes.repeating(rows, repeat) : Tunes.once(rows);
        return new Tune(song.name().strip(), song.author().strip(), "ym-to-ymxs",
                song.playerHz(), table);
    }

    /** The source of that number, built on first use: its rows without the
     *  marker bit 7, which is this format's and not the structure's
     *  (SPEC.md 3.2). */
    private static Source source(Map<Integer, Source> made, Sources sources, int number) {
        Source known = made.get(number);
        if (known != null) {
            return known;
        }
        Sources.Source of = sources.get(number);
        List<Integer> values = new ArrayList<>();
        for (byte row : of.rows()) {
            values.add(row & ~Sources.MARK & 0xFF);
        }
        String name = name(of.kind()) + " " + of.data();
        Source source = of.repeat() < of.rows().length
                ? Tunes.repeating(name, values, of.repeat()) : Tunes.once(name, values);
        made.put(number, source);
        return source;
    }

    /** What a source of that kind is called, so that two sources of one
     *  shape and two values stay two sources. */
    private static String name(int kind) {
        return switch (kind) {
            case Effects.SID -> "square";
            case Effects.DRUM -> "drum";
            case Effects.BUZZER -> "buzzer";
            default -> "source";
        };
    }

    /** The row a table repeats to, or none where it plays once. */
    static OptionalInt repeat(Table<Row> table) {
        return table.repeat();
    }
}
