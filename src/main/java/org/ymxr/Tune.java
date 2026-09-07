package org.ymxr;

import java.util.List;
import org.dtx.Dtx1;
import org.dtx.Dtx2;
import org.dtx.St4;
import org.dtx.Table;

/**
 * A tune file (SPEC.md 3.3): the values a tune states once, the tune's
 * table as a DTX2 file, and one DTX1 table a source. The file holds the
 * tune's tables and no code; a tool binds them with DTX's reader into what
 * the player takes (doc/BINARIES.md, {@link Bound}).
 *
 * <pre>
 *  offset  bytes  gives
 *  0       4      YMXR
 *  4       2      the version, $0002
 *  6       2      the frame rate, in Hz
 *  8       1      effects used, bits 3 to 0
 *  9       1      S, the source count, 0 to 127
 *  10      2      zero
 *  12      4      where the DTX2 table begins
 *  16      4S     the source index: where source 1 to S's DTX1 table begins
 *          ..     the DTX2 table, on a long
 *          ..     the DTX1 tables, each on a long
 * </pre>
 *
 * Every offset counts from the file's first byte.
 */
final class Tune {

    static final byte[] MAGIC = {'Y', 'M', 'X', 'R'};
    static final int VERSION = 0x0002;
    static final int FRAME_RATE_AT = 6;
    static final int EFFECTS_AT = 8;
    static final int COUNT_AT = 9;
    static final int TABLE_AT = 12;
    static final int INDEX_AT = 16;

    /** The ring a column unpacks through, dtx-write's own default. */
    static final int RING = 960;

    /** The widest ring the player reads: column 29's value stands 29 rings
     *  past column 0's, and the player reaches it through a 16-bit
     *  displacement. */
    static final int MAX_RING = 32767 / (Columns.C - 1);

    /** A tune file written: the file, and the row it repeats to, `R` where
     *  it plays once. */
    record Written(byte[] file, int repeat) {
    }

    private Tune() {
    }

    /** The file: its table packed at `unit` through a ring of `ring` bytes,
     *  as a DTX2 file.
     *
     *  <p>The table is the dump's frames row for row, the row it repeats
     *  to the dump's loop frame, and no row is added anywhere. A column's
     *  bytes and its loop begin on a unit (DTX's R5.6 and R5.11), so a
     *  tune whose row count or repeat row does not divide by the unit
     *  asked for packs at unit 1. The table packs at a period of `C`
     *  rows, the smallest DTX allows, since a refill decodes a period's
     *  rows of one column at once and the period is what a refill costs;
     *  the ring is a multiple of `C` for that, the one nearest what was
     *  asked for within what the player reaches. A loop longer than the
     *  ring is replayed at its exact rows by DTX's reader. */
    static Written write(Columns columns, Sources sources, int frameRate, int unit, int ring,
                         Report report) {
        int frames = columns.column[0].length;
        int repeat = columns.repeat;
        if (unit > 1 && (frames % unit != 0 || (repeat < frames && repeat % unit != 0))) {
            report.note("packed at unit 1: " + (frames % unit != 0 ? "the row count " + frames
                    : "the repeat row " + repeat) + " does not divide by " + unit);
            unit = 1;
        }
        int at = ringOf(ring);
        if (at != ring) {
            report.note("the ring is " + at + " bytes: a multiple of the period within the"
                    + " player's reach");
        }
        byte[] table = table(columns.column, frames, repeat, unit, at);
        List<Sources.Source> all = sources.all();
        byte[][] tables = new byte[all.size()][];
        for (int i = 0; i < tables.length; i++) {
            Sources.Source s = all.get(i);
            tables[i] = Dtx1.write(Table.of(s.rows().length, s.repeat(), 1,
                    new byte[][] {s.rows()}));
        }
        int here = align(INDEX_AT + 4 * tables.length);
        int tableAt = here;
        here = align(here + table.length);
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
        putLong(file, TABLE_AT, tableAt);
        for (int i = 0; i < tables.length; i++) {
            putLong(file, INDEX_AT + 4 * i, sourceAt[i]);
        }
        System.arraycopy(table, 0, file, tableAt, table.length);
        for (int i = 0; i < tables.length; i++) {
            System.arraycopy(tables[i], 0, file, sourceAt[i], tables[i].length);
        }
        return new Written(file, repeat < frames ? repeat : frames);
    }

    /** The columns as a DTX2 file, packed. */
    private static byte[] table(byte[][] column, int frames, int repeat, int unit, int ring) {
        int rr = repeat < frames ? repeat : frames;
        return Dtx2.write(Table.of(frames, rr, 1, column), new St4(), unit, ring);
    }

    /** The ring the table packs through: the multiple of `C` nearest `ring`,
     *  at least two periods and at most what the player reaches. */
    static int ringOf(int ring) {
        int nearest = Math.round((float) ring / Columns.C) * Columns.C;
        return Math.max(2 * Columns.C, Math.min(MAX_RING / Columns.C * Columns.C, nearest));
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
