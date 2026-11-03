package org.ymxr;

import java.util.List;

/**
 * Tune columns, one byte per column per frame, plus the repeat row,
 * effects used and optional transformations (SPEC.md 1). Version 9 has
 * thirty columns; version 10 adds four mapping columns. Mapping 0 uses
 * source values unchanged.
 *
 * <p>{@link Schema} encodes a YMXS tune; {@link Ym} imports a dump into
 * that structure. Constants here define register columns, set bits,
 * prescalers and source-duration arithmetic (doc/ymxs.md).
 */
final class Columns {

    /** The column count of versions 7 to 9, and a row's bytes there. */
    static final int C = 30;

    /**
     * The first mapping column. Effect i uses column 30 + i in version
     * 10 (SPEC.md 1.10).
     */
    static final int MAPPING = 30;

    /**
     * The version 10 column count, including four mapping columns
     * (SPEC.md 3.3.5).
     */
    static final int MAPPED = 34;

    /**
     * The transformation limit: mapping numbers 1 to 127 select
     * lookups; 0 selects identity (SPEC.md 1.10).
     */
    static final int MOST_TRANSFORMATIONS = 127;

    /**
     * The entry-count limit for a transformation: one entry per byte
     * key (SPEC.md 3.4).
     */
    static final int MOST_ENTRIES = 256;

    /** The first effect's target column; four columns an effect. */
    static final int EFFECT = 14;

    static final int TIMER_RESET = 0x40;
    static final int PLACE_RESET = 0x20;
    /** Bit 3 of the control column, a deferred start (SPEC.md 1.9, 4.8). */
    static final int DEFERRED = 0x08;
    /** Bit 4 of the effects used: a row defers a start (SPEC.md 3.3). */
    static final int DEFERS = 0x10;
    /**
     * Effects-used bit 5: version 10 mapping columns and transformation
     * block are present (SPEC.md 3.3).
     */
    static final int MAPS = 0x20;

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

    /**
     * The largest setR7 lookup key. Keys use bits 5 to 0; stored key k
     * is k + 192, with both port-direction bits set (SPEC.md 6, rule
     * 2(f)).
     */
    static final int MOST_MIXER_KEY = ~PORTS & 0xFF;

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

    /**
     * Effects used: bits 3 to 0 select effects, bit 4 marks deferred
     * starts, and bit 5 marks version 10 mapping columns (SPEC.md 3.3).
     */
    final int effects;

    /**
     * Optional lookup tables: transformation n is at index n - 1 and
     * key k selects entry k. Empty with thirty columns (SPEC.md 3.4).
     */
    final List<byte[]> transformations;

    /**
     * Builds columns directly: {@code column[c][frame]}, repeat row or
     * frame count for a tune played once, and effects used. This
     * constructor uses identity without transformation tables.
     */
    Columns(byte[][] column, int repeat, int effects) {
        this(column, repeat, effects, List.of());
    }

    /**
     * Builds columns with the lookup tables selected by their mapping
     * columns. Transformation n is at index n - 1.
     */
    Columns(byte[][] column, int repeat, int effects, List<byte[]> transformations) {
        this.column = column;
        this.repeat = repeat;
        this.effects = effects;
        this.transformations = List.copyOf(transformations);
    }

    /** The column count, 30 or 34, and a row's bytes. */
    int count() {
        return column.length;
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
