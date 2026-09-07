package org.ymxr;

import java.util.Arrays;

/**
 * The thirty columns of a tune, one byte a column a frame (SPEC.md 1),
 * from a YM dump. A column is set where its value differs from what the
 * player holds; an unset value is 0, which R3.6 does not read.
 *
 * <p>An effect starts on the frame the dump flags it, with the place's
 * reset and, where the timer is stopped, the timer's (1.9, section 6 rule
 * 5): a source that changes under a running timer reloads the count, and
 * the timer takes it at its next zero. An effect runs on while the dump
 * flags the same voice at the same value, its rate moving where the dump's
 * moves. A digidrum runs for the frames its rows take at its rate, the dump
 * flagging only the trigger, and the row it ends on stops it and sets the
 * voice's volume again (1.3). A SID voice or a digidrum owns its volume
 * register while it runs (section 6); a sync buzzer owns nothing, the
 * frame's own write to R13 restarting the envelope beside its ticks, as the
 * reference player has it.
 *
 * <p>The row the tune repeats to sets every register but R13 and the ones
 * an effect owns there, and every effect, so the wrap lands on a known
 * state whatever the last row left.
 */
final class Columns {

    /** The column count, and a row's bytes. */
    static final int C = 30;

    /** The first effect's target column; four columns an effect. */
    static final int EFFECT = 14;

    static final int TIMER_RESET = 0x40;
    static final int PLACE_RESET = 0x20;

    /** The register bits a YM dump keeps for its own flags, masked off. */
    static final int[] MASK = {0xFF, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0x1F, 0x3F,
                               0x1F, 0x1F, 0x1F, 0xFF, 0xFF, 0x0F};

    /** A column that fills its byte, and the column and bit beside it that
     *  keep its 0 a value (SPEC.md 1.1). */
    static final int[] BESIDE_COLUMN = {1, -1, 3, -1, 5, -1, -1, -1, -1, -1, -1, 13, 13};
    static final int[] BESIDE_BIT = {0x40, 0, 0x40, 0, 0x40, 0, 0, 0, 0, 0, 0, 0x40, 0x20};

    static final int MFP = 2457600;
    static final int[] PRESCALER = {0, 4, 10, 16, 50, 64, 100, 200};

    /** {@code column[c][frame]}. */
    final byte[][] column;

    /** The row the tune repeats to, `R` where it does not. */
    final int repeat;

    /** Bits 3 to 0: the effects the tune ever runs. */
    final int effects;

    /** Columns given whole, {@code column[c][frame]}, for a tune built
     *  rather than converted: the row it repeats to, or the frame count
     *  where it plays once, and the effects it runs. */
    Columns(byte[][] column, int repeat, int effects) {
        this.column = column;
        this.repeat = repeat;
        this.effects = effects;
    }

    Columns(YmDump.Song song, Sources sources, int repeat, Report report) {
        int frames = song.frames();
        this.repeat = repeat;
        column = new byte[C][frames];
        int[] held = new int[14];
        Arrays.fill(held, -1);
        Effects.Slot[] running = {Effects.Slot.EMPTY, Effects.Slot.EMPTY};
        int[] runningNumber = {0, 0};
        int[] targetHeld = {-1, -1};
        int[] selectHeld = {0, 0};
        int[] countHeld = {0, 0};
        int[] drumEnd = {-1, -1};
        boolean[] stopAtRepeat = {false, false};
        // What each effect last ran, whether or not it runs now: a row that
        // stops an effect leaves the place where the last tick left it, so a
        // square that starts again takes up the half it was in.
        int[] lastKind = {0, 0};
        int[] lastTarget = {-1, -1};
        int used = 0;
        for (int f = 0; f < frames; f++) {
            // The row the tune repeats to sets every register but R13 and
            // the ones an effect owns there, and every effect, so the wrap
            // lands on a known state: row 0 too, where the tune repeats to
            // it.
            boolean keyframe = f == repeat;
            if (keyframe) {
                Arrays.fill(held, -1);
                Arrays.fill(targetHeld, -1);
                Arrays.fill(lastKind, 0);
                Arrays.fill(lastTarget, -1);
                for (int i = 0; i < 2; i++) {
                    stopAtRepeat[i] = running[i].on();
                }
            }
            int[] reg = registers(song, f);
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
            byte[] out = new byte[C];
            int owned = 0;
            for (int i = 0; i < 2; i++) {
                int t = EFFECT + 4 * i;
                boolean drum = running[i].kind() == Effects.DRUM;
                if (!slot[i].on()) {
                    if (keyframe && drum && f < drumEnd[i]) {
                        report.cutAtRepeat++;
                    }
                    if (drum && f < drumEnd[i] && !keyframe) {
                        // the drum plays on: the dump flags only its trigger
                    } else if (running[i].on() || keyframe) {
                        out[t + 1] = (byte) 0x80;
                        running[i] = Effects.Slot.EMPTY;
                    }
                } else {
                    boolean starting = keyframe || slot[i].kind() == Effects.DRUM
                            || running[i].kind() != slot[i].kind()
                            || running[i].target() != slot[i].target()
                            || runningNumber[i] != number[i];
                    if (slot[i].target() != targetHeld[i]) {
                        out[t] = (byte) (0x80 | slot[i].target());
                        targetHeld[i] = slot[i].target();
                    }
                    if (starting) {
                        // Bit 6 stops the timer, writes the count and starts
                        // it, so the timer takes a whole period and loses
                        // what it had run of the last one. Section 6 rule 5
                        // sets the bit where the timer is stopped; where the
                        // timer runs, the count this row writes is taken
                        // when the running count reaches zero, which moves
                        // the pitch without a break (1.9). A timer is
                        // stopped where no effect runs on it, the row that
                        // stopped the effect having written select 0, and
                        // where a digidrum's source has run out: that source
                        // does not repeat, so its last tick stops the timer
                        // (section 5). A SID voice's source and a sync
                        // buzzer's repeat, and run until a row stops them.
                        // The keyframe sets the bit whatever the wrap left.
                        boolean stopped = keyframe || !running[i].on()
                                || running[i].kind() == Effects.DRUM && f >= drumEnd[i];
                        // Where a square replaces a square on the same
                        // target the place stands where it is: the row leaves
                        // bit 5 clear, and the new source's rows go under the
                        // place at the row it stands on (1.9). Every level is
                        // its own source, so a square whose level moves
                        // starts one each time; the half in flight runs to
                        // its end and the alternation holds its period. A
                        // drum struck again begins at its first row, so it
                        // takes bit 5 as any other start does.
                        boolean keeps = !keyframe && slot[i].kind() == Effects.SID
                                && lastKind[i] == Effects.SID
                                && lastTarget[i] == slot[i].target();
                        out[t + 1] = (byte) (0x80 | number[i]);
                        out[t + 2] = (byte) (0x80 | (stopped ? TIMER_RESET : 0)
                                | (keeps ? 0 : PLACE_RESET) | slot[i].select());
                        out[t + 3] = (byte) slot[i].count();
                        running[i] = slot[i];
                        runningNumber[i] = number[i];
                        lastKind[i] = slot[i].kind();
                        lastTarget[i] = slot[i].target();
                        used |= 1 << i;
                        if (slot[i].kind() == Effects.DRUM) {
                            drumEnd[i] = f + duration(sources.get(number[i]).rows().length,
                                    slot[i].select(), slot[i].count(), song.playerHz());
                        }
                    } else {
                        if (slot[i].select() != selectHeld[i]) {
                            out[t + 2] = (byte) (0x80 | slot[i].select());
                        }
                        if (slot[i].count() != countHeld[i]) {
                            out[t + 3] = (byte) slot[i].count();
                        }
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
            for (int c = 0; c < 13; c++) {
                if (BESIDE_COLUMN[c] >= 0) {
                    if (reg[c] != held[c]) {
                        out[c] = (byte) reg[c];
                        if (reg[c] == 0) {
                            out[BESIDE_COLUMN[c]] |= (byte) BESIDE_BIT[c];
                        }
                        held[c] = reg[c];
                    }
                } else if ((owned & 1 << c) != 0) {
                    held[c] = -1;                   // the effect's register,
                } else if (reg[c] != held[c]) {     // and no row's
                    out[c] |= (byte) (0x80 | reg[c]);
                    held[c] = reg[c];
                }
            }
            if (reg[13] >= 0) {
                out[13] |= (byte) (0x80 | reg[13]);
            }
            for (int c = 0; c < C; c++) {
                column[c][f] = out[c];
            }
        }
        // The keyframe's stop is for an effect that ran up to the repeat
        // row, or runs into the wrap, so that the wrap lands on a known
        // state; an effect that did neither, or that the tune never runs,
        // keeps its columns unset there.
        if (repeat < frames) {
            for (int i = 0; i < 2; i++) {
                int t = EFFECT + 4 * i;
                boolean stopped = (column[t + 1][repeat] & 0xFF) == 0x80;
                if (stopped && !stopAtRepeat[i] && !running[i].on()) {
                    column[t + 1][repeat] = 0;
                }
            }
        }
        effects = used;
    }

    /** R0 to R13 of one frame with the dump's flag bits masked off, and -1
     *  for R13 where the dump does not write it. */
    static int[] registers(YmDump.Song song, int frame) {
        byte[][] r = song.registers();
        int[] out = new int[14];
        for (int i = 0; i < 14; i++) {
            out[i] = r[i][frame] & MASK[i];
        }
        if ((r[13][frame] & 0xFF) == 0xFF) {
            out[13] = -1;
        }
        return out;
    }

    /** The frames a source of `rows` rows takes at a rate, rounded up, with
     *  a sixteenth of a frame added for the start running into its own
     *  frame: the reckoning YMX's player was measured against. */
    static int duration(int rows, int select, int count, int frameRate) {
        long divisor = (long) PRESCALER[select] * count;
        long scaled = (long) rows * divisor * frameRate + MFP / 16;
        return (int) ((scaled + MFP - 1) / MFP);
    }
}
