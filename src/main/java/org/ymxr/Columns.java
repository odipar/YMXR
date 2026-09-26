package org.ymxr;

import java.util.Arrays;

/**
 * The thirty columns of a tune, one byte a column a frame (SPEC.md 1):
 * the columns themselves, the row the tune repeats to, and the effects the
 * tune runs.
 *
 * <p>{@link Schema} writes them from a YMXS structure, which {@link Ym}
 * reads a dump into (doc/ymxs.md). What is here beside the columns is
 * the arithmetic both stages read: which column a register reaches, the
 * bit beside a column that fills its byte, the prescalers, and how long a
 * source of so many rows runs at a rate.
 */
final class Columns {

    /** The column count, and a row's bytes. */
    static final int C = 30;

    /** The first effect's target column; four columns an effect. */
    static final int EFFECT = 14;

    static final int TIMER_RESET = 0x40;
    static final int PLACE_RESET = 0x20;

    /** Bit 4 of a control column: the count column beside it is 0, and
     *  that 0 is the value the MFP counts 256 for (SPEC.md 1.1, 1.9). */
    static final int COUNT_VALUE = 0x10;

    /** The register bits a YM dump uses for its flags, masked off. */
    static final int[] MASK = {0xFF, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0x1F, 0x3F,
                               0x1F, 0x1F, 0x1F, 0xFF, 0xFF, 0x0F};

    /** The column of a source's row the marker is in, a target
     *  (SPEC.md 2.1): the column whose register reads seven bits or fewer,
     *  and -1 where every column writes a register that reads all eight,
     *  which is a counted source (3.1.6). Targets 0 to 13 write one
     *  register, so the marker is in their one column where the
     *  register leaves bit 7 and the source is counted where it does not;
     *  14 to 19 mark the coarse nibble, 20, `setEnvelope`, writes two
     *  whole bytes and is counted, 21 marks the envelope shape, and 22 to
     *  24 the noise period. */
    static final int[] MARKER = {-1, 0, -1, 0, -1, 0, 0, -1, 0, 0, 0, -1, -1, 0,
                                 1, 1, 1, 1, 1, 1, -1, 2, 0, 0, 0};

    /** Bits 7 and 6 of R7, the directions of the two I/O ports, which an
     *  Atari ST writes as 1 (SPEC.md 1.4.2). A row's write reads them
     *  from the player; a tick of a counted source on `setR7` writes the
     *  row whole, so a writer sets them in every row of such a source
     *  (rule 2(f)). */
    static final int PORTS = 0xC0;

    /** The target whose register is R7, the mixer (SPEC.md 2.1). */
    static final int MIXER_TARGET = 7;

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
     *  frame it begins in. A count of 0 is the 256 the MFP counts
     *  (SPEC.md 1.9.1, 6.4). */
    static int duration(int rows, int select, int count, int frameRate) {
        long divisor = (long) PRESCALER[select] * (count == 0 ? 256 : count);
        long scaled = (long) rows * divisor * frameRate + MFP / 16;
        return (int) ((scaled + MFP - 1) / MFP);
    }
}
