package org.ymxr;

import java.util.List;
import java.util.Locale;
import org.dtx.Dtx1;
import org.dtx.Dtx2;
import org.dtx.Packer;
import org.dtx.St4;
import org.dtx.Table;

/**
 * A tune file (SPEC.md 3.3): a tune's fixed values, the tune's table as a
 * DTX2 file, and one DTX1 table a source. The file contains the
 * tune's tables and no code; a tool binds them with DTX's reader into what
 * the player reads (doc/BINARIES.md, {@link Bound}).
 *
 * <pre>
 *  offset  bytes  what it is
 *  0       4      YMXR
 *  4       2      the version, $0003
 *  6       2      the frame rate, in Hz
 *  8       1      effects used, bits 3 to 0
 *  9       1      S, the source count, 0 to 127
 *  10      2      where the name begins, or zero where the tune has none
 *  12      4      where the DTX2 table begins
 *  16      4S     the source index: where source 1 to S's DTX1 table begins
 *          ..     the name, its bytes and a zero, where there is one
 *          ..     the DTX2 table, on a long
 *          ..     the DTX1 tables, each on a long
 * </pre>
 *
 * Every offset counts from the file's first byte. The name is what the
 * dump or the YMXS file titles the tune, in UTF-8 and ended by a zero
 * byte. It lies between the index and the table, so its offset fits the
 * word that records it however large the tables are.
 */
final class Tune {

    static final byte[] MAGIC = {'Y', 'M', 'X', 'R'};
    static final int VERSION = 0x0003;
    static final int FRAME_RATE_AT = 6;
    static final int EFFECTS_AT = 8;
    static final int COUNT_AT = 9;
    static final int NAME_AT = 10;
    static final int TABLE_AT = 12;
    static final int INDEX_AT = 16;

    /** The most bytes a name takes, its zero aside. A longer one is cut
     *  to this: the player reads a name onto a screen of forty columns and
     *  an SNDH tag numbers its bytes in a word. */
    static final int MOST_NAME = 255;

    /** The ring a column unpacks through, dtx-write's default. */
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
     *  bytes and its loop begin on a unit (DTX's R5.6 and R5.11), which
     *  {@link Padding} provides before the columns are made; a table that
     *  still does not divide packs at unit 1. The table packs at a period of `C`
     *  rows, the smallest DTX allows, since a refill decodes a period's
     *  rows of one column at once and a refill costs a period; the ring is
     *  a multiple of `C` for that, the one nearest what was
     *  asked for within what the player reaches. A loop longer than the
     *  ring is replayed at its exact rows by DTX's reader. */
    static Written write(Columns columns, Sources sources, int frameRate, int unit, int ring,
                         Report report) {
        return write(columns, sources, frameRate, unit, ring, new St4(), report, "");
    }

    /** The same, with the tune named. */
    static Written write(Columns columns, Sources sources, int frameRate, int unit, int ring,
                         Report report, String name) {
        return write(columns, sources, frameRate, unit, ring, new St4(), report, name);
    }

    /** The same, packed by the packer the tool's flags asked for. */
    static Written write(Columns columns, Sources sources, int frameRate, int unit, int ring,
                         Packer packer, Report report) {
        return write(columns, sources, frameRate, unit, ring, packer, report, "");
    }

    /** The same, with the tune named. */
    static Written write(Columns columns, Sources sources, int frameRate, int unit, int ring,
                         Packer packer, Report report, String name) {
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
        Watched watched = new Watched(report, Columns.C, packer);
        byte[] table = table(columns.column, frames, repeat, unit, at, watched);
        List<Sources.Source> all = sources.all();
        byte[][] tables = new byte[all.size()][];
        int sourceRows = 0;
        int sourceBytes = 0;
        for (int i = 0; i < tables.length; i++) {
            Sources.Source s = all.get(i);
            tables[i] = Dtx1.write(Table.of(s.rows().length, s.repeat(), 1,
                    new byte[][] {s.rows()}));
            sourceRows += s.rows().length;
            sourceBytes += tables[i].length;
        }
        byte[] named = named(name);
        int after = INDEX_AT + 4 * tables.length;
        int nameAt = named.length == 0 ? 0 : after;
        int here = align(after + named.length);
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
        putWord(file, NAME_AT, nameAt);
        putLong(file, TABLE_AT, tableAt);
        System.arraycopy(named, 0, file, nameAt, named.length);
        for (int i = 0; i < tables.length; i++) {
            putLong(file, INDEX_AT + 4 * i, sourceAt[i]);
        }
        System.arraycopy(table, 0, file, tableAt, table.length);
        for (int i = 0; i < tables.length; i++) {
            System.arraycopy(tables[i], 0, file, sourceAt[i], tables[i].length);
        }
        packed(report, watched, frames, table.length, tables.length, sourceRows, sourceBytes,
                at, unit, file.length);
        return new Written(file, repeat < frames ? repeat : frames);
    }

    /**
     * What a name comes to in the file: its UTF-8 and a zero, cut to
     * {@link #MOST_NAME} bytes. A name of no printable characters writes
     * none, and the file records zero for it. Bytes under a space are
     * dropped, since a name reaches an ST screen and an SNDH tag.
     */
    private static byte[] named(String name) {
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char one = name.charAt(i);
            if (one >= ' ') {
                kept.append(one);
            }
        }
        byte[] said = kept.toString().strip()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (said.length == 0) {
            return said;
        }
        int cut = Math.min(said.length, MOST_NAME);
        // a cut lands on a whole character, so the name stays UTF-8
        while (cut > 0 && cut < said.length && (said[cut] & 0xC0) == 0x80) {
            cut--;
        }
        byte[] out = new byte[cut + 1];
        System.arraycopy(said, 0, out, 0, cut);
        return out;
    }

    /**
     * The name a tune file records, or an empty string where it records
     * none. A file of a version before the name reads as none, since the
     * word was zero there.
     */
    static String name(byte[] file) {
        if (file.length < INDEX_AT) {
            return "";
        }
        int at = getWord(file, NAME_AT);
        if (at == 0 || at >= file.length) {
            return "";
        }
        int end = at;
        while (end < file.length && file[end] != 0) {
            end++;
        }
        return new String(file, at, end - at, java.nio.charset.StandardCharsets.UTF_8);
    }

    /** The columns as a DTX2 file, packed. */
    private static byte[] table(byte[][] column, int frames, int repeat, int unit, int ring,
                                Packer packer) {
        int rr = repeat < frames ? repeat : frames;
        return Dtx2.write(Table.of(frames, rr, 1, column), packer, unit, ring);
    }

    /**
     * A packer that reports: it packs a column through the one the file
     * reads, records what each column came to, and reports how far through the
     * thirty it is. The packer is what {@link Dtx2#write} calls a column
     * at a time, so this is where a column's packed bytes are to be had
     * without unpacking the file again.
     */
    private static final class Watched implements Packer {

        private final Packer inner;
        private final Report report;
        private final int[] bytes;
        private int done;

        Watched(Report report, int columns, Packer inner) {
            this.report = report;
            this.bytes = new int[columns];
            this.inner = inner;
        }

        @Override
        public byte[] pack(byte[] column, int unit, int ring, int loop) {
            report.progress("packing the columns", done, bytes.length);
            byte[] out = inner.pack(column, unit, ring, loop);
            if (done < bytes.length) {
                bytes[done] = out.length;
            }
            done++;
            return out;
        }

        @Override
        public boolean copies() {
            return inner.copies();
        }
    }

    /** What the packing came to, a column at a time and in all. */
    private static void packed(Report report, Watched packer, int frames, int table, int sources,
                               int sourceRows, int sourceBytes, int ring, int unit, int file) {
        if (!report.says()) {
            return;
        }
        report.say("the table: " + Columns.C + " columns of " + frames + " rows, "
                + Columns.C * frames + " bytes, packed at unit " + unit + " through a ring of "
                + ring);
        for (int c = 0; c < Columns.C; c++) {
            int bytes = packer.bytes[c];
            report.row(name(c), String.format(Locale.ROOT, "%7d -> %6d bytes  (%5.1f%%)",
                    frames, bytes, 100.0 * bytes / frames));
        }
        report.say("the sources: " + sources + (sources == 1 ? " table of " : " tables of ")
                + sourceRows + (sourceRows == 1 ? " row, " : " rows, ") + sourceBytes + " bytes");
        int raw = Columns.C * frames;
        report.say(String.format(Locale.ROOT,
                "packed %d bytes into %d (%.1f%%), the file %d bytes",
                raw, table, 100.0 * table / raw, file));
    }

    /** What a column is, for a reported row: a register by its number, and
     *  an effect column by its effect and its part of it. */
    static String name(int c) {
        if (c < Columns.EFFECT) {
            return "R" + c;
        }
        int effect = (c - Columns.EFFECT) / 4;
        return "effect " + effect + " " + switch ((c - Columns.EFFECT) % 4) {
            case 0 -> "target";
            case 1 -> "source";
            case 2 -> "timer control";
            default -> "timer count";
        };
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
