package org.ymxr;

import java.util.Arrays;

/**
 * The thirty columns of a tune, one byte a column a frame (SPEC.md 1):
 * the columns themselves, the row the tune repeats to, and the effects the
 * tune runs.
 *
 * <p>{@link Schema} writes them from a YMXS structure, which {@link Ym}
 * and {@link Ymx} read a dump into (doc/ymxs.md). What stands here beside
 * the columns is the arithmetic both stages read: which column a register
 * reaches, the bit beside a column that fills its byte, the prescalers,
 * and how long a source of so many rows runs at a rate.
 */
final class Columns {

    /** The column count, and a row's bytes. */
    static final int C = 30;

    /** The first effect's target column; four columns an effect. */
    static final int EFFECT = 14;

    static final int TIMER_RESET = 0x40;
    static final int PLACE_RESET = 0x20;

    /** The register bits a YM dump uses for its flags, masked off. */
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

    /** Columns passed whole, {@code column[c][frame]}, for a tune built
     *  rather than converted: the row it repeats to, or the frame count
     *  where it plays once, and the effects it runs. */
    Columns(byte[][] column, int repeat, int effects) {
        this.column = column;
        this.repeat = repeat;
        this.effects = effects;
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

    /** The frames a source of `rows` rows runs for at a rate, rounded up,
     *  with a sixteenth of a frame added for a start that falls inside the
     *  frame it begins in: the reckoning YMX's player was measured
     *  against. */
    static int duration(int rows, int select, int count, int frameRate) {
        long divisor = (long) PRESCALER[select] * count;
        long scaled = (long) rows * divisor * frameRate + MFP / 16;
        return (int) ((scaled + MFP - 1) / MFP);
    }
}
