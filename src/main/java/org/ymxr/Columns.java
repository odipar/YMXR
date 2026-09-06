package org.ymxr;

import java.util.Arrays;

/**
 * The thirty columns of a tune, one byte a column a frame (SPEC.md 1),
 * from a YM dump. A column is set where its value differs from what the
 * player holds; an unset value is 0, which R3.6 does not read.
 *
 * <p>An effect starts on the frame the dump flags it, with the timer's and
 * the place's reset (1.9, section 6), and runs on while the dump flags the
 * same voice at the same value, its rate moving where the dump's moves. A
 * digidrum runs for the frames its rows take at its rate, the dump flagging
 * only the trigger, and the row it ends on stops it and sets the voice's
 * volume again (1.3). A SID voice or a digidrum owns its volume register
 * while it runs (section 6); a sync buzzer owns nothing, the frame's own
 * write to R13 restarting the envelope beside its ticks, as the reference
 * player has it.
 *
 * <p>The row the tune repeats to sets every register and every effect, so
 * the wrap lands on a known state whatever the last row left.
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
        int used = 0;
        for (int f = 0; f < frames; f++) {
            boolean keyframe = f == repeat && f > 0;
            if (keyframe) {
                Arrays.fill(held, -1);
            }
            int[] reg = registers(song, f);
            Effects.Slot[] slots = Effects.of(song, f);
            byte[] out = new byte[C];
            int owned = 0;
            for (int i = 0; i < 2; i++) {
                int t = EFFECT + 4 * i;
                Effects.Slot slot = slots[i];
                int number = slot.on() ? sources.number(slot, report) : 0;
                if (number == 0) {
                    slot = Effects.Slot.EMPTY;
                }
                boolean drum = running[i].kind() == Effects.DRUM;
                if (!slot.on()) {
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
                    boolean starting = keyframe || slot.kind() == Effects.DRUM
                            || running[i].kind() != slot.kind()
                            || running[i].target() != slot.target()
                            || runningNumber[i] != number;
                    if (slot.target() != targetHeld[i]) {
                        out[t] = (byte) (0x80 | slot.target());
                        targetHeld[i] = slot.target();
                    }
                    if (starting) {
                        out[t + 1] = (byte) (0x80 | number);
                        out[t + 2] = (byte) (0x80 | TIMER_RESET | PLACE_RESET | slot.select());
                        out[t + 3] = (byte) slot.count();
                        running[i] = slot;
                        runningNumber[i] = number;
                        used |= 1 << i;
                        if (slot.kind() == Effects.DRUM) {
                            drumEnd[i] = f + duration(sources.get(number).rows().length,
                                    slot.select(), slot.count(), song.playerHz());
                        }
                    } else {
                        if (slot.select() != selectHeld[i]) {
                            out[t + 2] = (byte) (0x80 | slot.select());
                        }
                        if (slot.count() != countHeld[i]) {
                            out[t + 3] = (byte) slot.count();
                        }
                    }
                    selectHeld[i] = slot.select();
                    countHeld[i] = slot.count();
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
                    held[c] = -1;
                } else if (reg[c] != held[c]) {
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
