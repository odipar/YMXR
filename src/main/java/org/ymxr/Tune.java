package org.ymxr;

import java.util.List;
import org.dtx.Dtx1;
import org.dtx.Dtx2;
import org.dtx.Packager;
import org.dtx.St4;
import org.dtx.Table;

/**
 * A tune file (SPEC.md 3.3): the values a tune states once, the DTX2 image
 * DTX packages its table with, and one DTX1 table a source.
 *
 * <pre>
 *  offset  bytes  gives
 *  0       4      YMXR
 *  4       2      the version, $0001
 *  6       2      the frame rate, in Hz
 *  8       1      effects used, bits 3 to 0
 *  9       1      S, the source count, 0 to 127
 *  10      2      zero
 *  12      4      the state block's bytes the image's reader needs
 *  16      4      where the image begins
 *  20      4S     the source index: where source 1 to S's DTX1 table begins
 *          ..     the image, on a long
 *          ..     the DTX1 tables, each on a long
 * </pre>
 *
 * Every offset counts from the file's first byte.
 */
final class Tune {

    static final byte[] MAGIC = {'Y', 'M', 'X', 'R'};
    static final int VERSION = 0x0001;
    static final int FRAME_RATE_AT = 6;
    static final int EFFECTS_AT = 8;
    static final int COUNT_AT = 9;
    static final int STATE_AT = 12;
    static final int IMAGE_AT = 16;
    static final int INDEX_AT = 20;

    /** The image's format block, and where the state block's bytes stand in
     *  it (DTX, abi.md 1). */
    static final int FORMAT_AT = 16;
    static final int FORMAT_STATE_AT = 4;
    static final int FORMAT_TABLE_AT = 8;

    /** The ring a column unpacks through, dtx-write's own default. */
    static final int RING = 960;

    /** The widest ring the player reads: column 29's value stands 29 rings
     *  past column 0's, and the player reaches it through a 16-bit
     *  displacement. */
    static final int MAX_RING = 32767 / (Columns.C - 1);

    /** A tune file written, and the rows it holds beyond the dump's frames:
     *  {@code before} silent rows put in ahead of the row the tune repeats
     *  to, and {@code after} appended, where DTX's period asked for them. */
    record Written(byte[] file, int repeat, int before, int after) {
    }

    private Tune() {
    }

    /** The file: its table packed at `unit` through a ring of `ring` bytes,
     *  packaged with DTX's reader.
     *
     *  <p>A DTX2 image refills one column a row, `P` bytes of it at once
     *  over a period `P` of at least `C` rows, so the period is what a
     *  refill costs. A table that repeats over more rows than the ring
     *  holds is replayed at the wrap, and DTX asks that its repeat row and
     *  its loop's rows divide by `P`; a loop the ring holds is looped in
     *  the ring instead, which DTX 0.4.0's reader gets wrong past the
     *  wrap. So a table that repeats packs at a period of `C`, the
     *  smallest, with silent rows padding the repeat row and the loop up to
     *  multiples of `C`, the loop to three periods at least, and at a ring
     *  shorter than the loop, the multiple of `C` nearest the one asked
     *  for. */
    static Written write(Columns columns, Sources sources, int frameRate, int unit, int ring,
                         Report report) {
        int frames = columns.column[0].length;
        int repeat = columns.repeat;
        int before = 0;
        int after = Math.max(0, Columns.C - frames);
        int at = ring;
        if (repeat < frames) {
            before = (Columns.C - repeat % Columns.C) % Columns.C;
            int loop = frames + after - repeat;
            after += (Columns.C - loop % Columns.C) % Columns.C;
            after += Math.max(0, 3 * Columns.C - (frames + after - repeat));
            at = ringFor(frames + after - repeat, ring);
        }
        byte[] image = image(columns.column, frames, repeat, before, after, unit, at);
        if (before > 0 || after > 0) {
            report.note(before + " silent rows before the repeat row and " + after
                    + " after the last, so that a period of " + Columns.C + " divides the loop");
        }
        if (at != ring) {
            report.note("the ring is " + at + " bytes: a multiple of the period, under the loop");
        }
        List<Sources.Source> all = sources.all();
        byte[][] tables = new byte[all.size()][];
        for (int i = 0; i < tables.length; i++) {
            Sources.Source s = all.get(i);
            tables[i] = Dtx1.write(Table.of(s.rows().length, s.repeat(), 1,
                    new byte[][] {s.rows()}));
        }
        int here = align(INDEX_AT + 4 * tables.length);
        int imageAt = here;
        here = align(here + image.length);
        int[] sourceAt = new int[tables.length];
        for (int i = 0; i < tables.length; i++) {
            sourceAt[i] = here;
            here = align(here + tables[i].length);
        }
        byte[] file = new byte[here];
        System.arraycopy(MAGIC, 0, file, 0, 4);
        putWord(file, 4, VERSION);
        putWord(file, FRAME_RATE_AT, frameRate);
        file[EFFECTS_AT] = (byte) columns.effects;
        file[COUNT_AT] = (byte) tables.length;
        putLong(file, STATE_AT, getLong(image, FORMAT_AT + FORMAT_STATE_AT));
        putLong(file, IMAGE_AT, imageAt);
        for (int i = 0; i < tables.length; i++) {
            putLong(file, INDEX_AT + 4 * i, sourceAt[i]);
        }
        System.arraycopy(image, 0, file, imageAt, image.length);
        for (int i = 0; i < tables.length; i++) {
            System.arraycopy(tables[i], 0, file, sourceAt[i], tables[i].length);
        }
        return new Written(file, repeat < frames ? repeat + before : frames + before + after,
                before, after);
    }

    /** The image of the columns with the pads in, packed and packaged. */
    private static byte[] image(byte[][] column, int frames, int repeat, int before, int after,
                                int unit, int ring) {
        int rows = frames + before + after;
        byte[][] padded = new byte[Columns.C][rows];
        for (int c = 0; c < Columns.C; c++) {
            System.arraycopy(column[c], 0, padded[c], 0, repeat);
            System.arraycopy(column[c], repeat, padded[c], repeat + before, frames - repeat);
        }
        int rr = repeat < frames ? repeat + before : rows;
        Table table = Table.of(rows, rr, 1, padded);
        return Packager.image(Dtx2.write(table, new St4(), unit, ring));
    }

    /** The ring for a loop of `loop` rows, a multiple of `C`: the multiple
     *  of `C` nearest `ring`, at least two periods and under the loop. */
    static int ringFor(int loop, int ring) {
        int most = Math.min(MAX_RING, loop - 1) / Columns.C * Columns.C;
        int nearest = Math.round((float) ring / Columns.C) * Columns.C;
        return Math.max(2 * Columns.C, Math.min(most, nearest));
    }

    static int align(int at) {
        return (at + 3) & ~3;
    }

    static void putWord(byte[] b, int at, int value) {
        b[at] = (byte) (value >> 8);
        b[at + 1] = (byte) value;
    }

    static void putLong(byte[] b, int at, int value) {
        putWord(b, at, value >>> 16);
        putWord(b, at + 2, value);
    }

    static int getWord(byte[] b, int at) {
        return (b[at] & 0xFF) << 8 | (b[at + 1] & 0xFF);
    }

    static int getLong(byte[] b, int at) {
        return getWord(b, at) << 16 | getWord(b, at + 2);
    }
}
