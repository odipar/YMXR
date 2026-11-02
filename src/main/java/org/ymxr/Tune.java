package org.ymxr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;
import org.dtx.Dtx;
import org.dtx.Dtx1;
import org.dtx.Dtx3;
import org.dtx.Packager;
import org.dtx.Packer;
import org.dtx.St4;
import org.dtx.Table;

/**
 * A tune file (SPEC.md 3.3): a tune's fixed values, the tune's table as a
 * DTX3 file, and one DTX1 table a source. The file contains the
 * tune's tables and no code; a tool binds them with DTX's reader into what
 * the player reads (doc/BINARIES.md, {@link Bound}).
 *
 * <pre>
 *  offset  bytes  what it is
 *  0       4      YMXR
 *  4       2      the version, $0009, or $000A where a row maps a value
 *  6       2      the frame rate, in Hz
 *  8       1      effects used: bits 3 to 0 the effects, bit 4 a deferred
 *                 start, bit 5 the mapping columns
 *  9       1      S, the source count, 0 to 127
 *  10      2      where the name begins, or zero where the tune has none
 *  12      4      where the DTX3 table begins
 *  16      4      version 10: where the transformation index begins
 *  16, 20  4S     the source index, at 16 and at 20 in version 10: where
 *                 source 1 to S's DTX1 table begins
 *          ..     the name, its bytes and a zero, where there is one
 *          ..     version 10: the transformation index, on a long, a word
 *                 T and T longs, each where a transformation begins
 *                 counted from the index's first byte; then the T
 *                 transformations, each on a long, a word E and E entries
 *          ..     the DTX3 table, on a long
 *          ..     the DTX1 tables, each on a long
 * </pre>
 *
 * Every offset but the T offsets of the transformation index counts from
 * the file's first byte. The name reads as the dump or the YMXS file
 * titles the tune, in UTF-8 and ended by a zero byte. It lies between the
 * source index and the tables, so its offset fits the word at 10.
 */
final class Tune {

    static final byte[] MAGIC = {'Y', 'M', 'X', 'R'};

    /** The highest version a reader reads, and the one a writer writes
     *  where a row maps a value (SPEC.md 3.3.5): the table has the four
     *  mapping columns, and the long at 16 is the transformation index's
     *  offset. */
    static final int VERSION = 0x000A;

    /** The version a writer writes where no row maps a value (SPEC.md
     *  3.3.5): its table is DTX3, which a player of an earlier version
     *  reads wrongly, bit 3 of a control column marks a deferred start,
     *  and the place of one with bit 5 at 0 continues the count of the
     *  rows (4.8). */
    static final int UNMAPPED = 0x0009;

    /** The first version with a deferred start: a file of version 8 reads
     *  as version 9 (SPEC.md 3.3.5). */
    static final int DEFERRED = 0x0008;

    /** The oldest version a reader reads: a file of version 7 has bit 3 of
     *  every control column at 0 (SPEC.md 3.3.5). */
    static final int OLDEST = 0x0007;

    /** The versions a reader reads, as its report names them. */
    static final String READS = OLDEST + ", " + DEFERRED + ", " + UNMAPPED + " or " + VERSION;

    /** Whether a reader reads a file of that version. */
    static boolean reads(int version) {
        return version >= OLDEST && version <= VERSION;
    }

    /** The version a writer writes for a table of {@code columns} columns:
     *  10 for the thirty-four of a tune that maps a value, and 9 for
     *  thirty. */
    static int version(int columns) {
        return columns == Columns.MAPPED ? VERSION : UNMAPPED;
    }

    /** The column count of a table of that version: 34 in version 10, and
     *  30 in 7 to 9 (SPEC.md 3.3.3). */
    static int columns(int version) {
        return version == VERSION ? Columns.MAPPED : Columns.C;
    }

    /** Where the source index begins in a file of that version: at 16, and
     *  at 20 in version 10, after the transformation index's offset
     *  (SPEC.md 3.3). */
    static int indexAt(int version) {
        return version == VERSION ? TRANSFORMATIONS_AT + 4 : INDEX_AT;
    }

    /** The first and the last version whose table is DTX2, which a reader
     *  reports with the tool that writes the tune again (SPEC.md 3.3.4). */
    static final int VERSION_DTX2 = 0x0003;
    static final int VERSION_DTX2_LAST = 0x0006;

    /** Bit 31 of a source's index entry: the source's rows are whole
     *  bytes and a player counts them (SPEC.md 3.1). */
    static final int COUNTED = 0x80000000;

    static final int FRAME_RATE_AT = 6;

    /** The largest frame rate the word at {@link #FRAME_RATE_AT} reads. A
     *  YMXS rate reaches 2,147,483,647 (YMXS, SPEC.md 1.3), so a writer
     *  reads this bound rather than writing the low sixteen bits of a
     *  rate above it. */
    static final int MOST_RATE = 65535;
    static final int EFFECTS_AT = 8;
    static final int COUNT_AT = 9;
    static final int NAME_AT = 10;
    static final int TABLE_AT = 12;

    /** Where the source index begins in versions 7 to 9, and the bytes of
     *  their header. */
    static final int INDEX_AT = 16;

    /** Where the long is in version 10 that is the transformation index's
     *  offset (SPEC.md 3.3). */
    static final int TRANSFORMATIONS_AT = 16;

    /** The most bytes a name reaches, its zero aside. A longer one is cut
     *  to this: the player reads a name onto a screen of forty columns and
     *  an SNDH tag numbers its bytes in a word. */
    static final int MOST_NAME = 255;

    /** What a writer's ring reads where none is named: the writer chooses
     *  one of {@link #ringsOf} (SPEC.md 3.3.3). */
    static final int CHOOSE = 0;

    /** The rings a writer chooses among for a table of {@code columns}
     *  columns: two periods of that many rows and each double of that, to
     *  64 periods; 60 to 1,920 for thirty columns, and 68 to 2,176 for
     *  thirty-four. */
    static int[] ringsOf(int columns) {
        int[] rings = new int[6];
        for (int i = 0; i < rings.length; i++) {
            rings[i] = 2 * columns << i;
        }
        return rings;
    }

    /** The furthest the player reads a column from the row pointer: a
     *  16-bit displacement. */
    static final int REACH = 32767;

    /** The heading's words for a ring the writer chose: for the tune, and
     *  for the set it is a subtune of (tools.md 4.5, 4.7). */
    static final String CHOSE = ", the one the writer chose";
    static final String CHOSE_FOR_SET = ", the one the writer chose for the set";

    /** A table packed: its bytes, and the state block its reader needs. */
    record Packed(byte[] table, int state) {
    }

    /** A tune file written: the file, the row it repeats to, `R` where it
     *  plays once, its DTX3 table, the table's rows and unit, and the
     *  sources' tables, rows and bytes the packing report reads (tools.md
     *  4.5). `plain` is the table packed without copies through each ring
     *  the writer chose among, by ring, and empty where a ring was named:
     *  a set's rings are chosen among these (tools.md 4.7). */
    record Written(byte[] file, int repeat, byte[] table, int frames, int unit, int sources,
                   int sourceRows, int sourceBytes, SortedMap<Integer, Packed> plain) {

        /** The same tune in another file, its table as it is. */
        Written withFile(byte[] other) {
            return new Written(other, repeat, table, frames, unit, sources, sourceRows,
                    sourceBytes, plain);
        }
    }

    private Tune() {
    }

    /** What a live column packs with by default: ST4 without copies from
     *  the literal stream (SPEC.md 3.3.3). */
    static List<Packer> packers() {
        return List.of(new St4());
    }

    /** What a live column packs with under {@code -copies}: ST4 with copies
     *  and without, the smaller kept, the copies searched for
     *  {@code seconds} beyond ST4's opening passes. */
    static List<Packer> packers(double seconds) {
        return List.of(new St4(), new St4(true, seconds));
    }

    /** The file: its table packed at `unit` through a ring of `ring` bytes,
     *  or the ring the writer chooses where `ring` is {@link #CHOOSE}, as a
     *  DTX3 file.
     *
     *  <p>The table is the tune's rows, the row it repeats to the tune's,
     *  and this adds no row. A column's bytes and its loop begin on a
     *  unit (DTX's R8.9), which {@link Padding} provides before
     *  the columns are made (SPEC.md 6, rule 6); a table that does not
     *  divide packs at unit 1. A constant column is its value, and each
     *  live column is packed by every packer named, the smallest kept:
     *  with copies from the literal stream and without, the smaller kept,
     *  where `-copies` names both. The table packs at a period of `C`
     *  rows, `C` its column count, the smallest DTX allows, since a refill
     *  decodes a period's rows of one column at once and a refill costs a
     *  period; the ring is a multiple of `C` for that. The writer packs the
     *  table with the first packer through each of {@link #ringsOf} the
     *  player reaches and keeps the one whose bytes and state block come to
     *  the least; with a second packer, it packs with every packer through
     *  the two that came to the least and keeps the one of those two that
     *  comes to the less. A ring named is the multiple of `C` nearest it
     *  within that reach. A loop longer than the ring is replayed at its
     *  exact rows by DTX's reader. */
    static Written write(Columns columns, Sources sources, int frameRate, int unit, int ring,
                         Report report) {
        return write(columns, sources, frameRate, unit, ring, packers(), report, "");
    }

    /** The same, with the tune named. */
    static Written write(Columns columns, Sources sources, int frameRate, int unit, int ring,
                         Report report, String name) {
        return write(columns, sources, frameRate, unit, ring, packers(), report, name);
    }

    /** The same, packed by the packers the tool's flags asked for. */
    static Written write(Columns columns, Sources sources, int frameRate, int unit, int ring,
                         List<Packer> packers, Report report) {
        return write(columns, sources, frameRate, unit, ring, packers, report, "");
    }

    /** The same, with the tune named. */
    static Written write(Columns columns, Sources sources, int frameRate, int unit, int ring,
                         List<Packer> packers, Report report, String name) {
        Written written = pack(columns, sources, frameRate, unit, ring, packers, report, name);
        packed(report, written, ring == CHOOSE ? CHOSE : "");
        return written;
    }

    /** The tune file {@link #write} writes, without the packing report: a
     *  set reports its tunes once their rings are chosen for it. */
    static Written pack(Columns columns, Sources sources, int frameRate, int unit, int ring,
                        List<Packer> packers, Report report, String name) {
        if (frameRate < 1 || frameRate > MOST_RATE) {
            throw new IllegalArgumentException("a frame rate of " + frameRate
                    + ", and the frame rate is a word, 1 to " + MOST_RATE);
        }
        int frames = columns.column[0].length;
        int repeat = columns.repeat;
        // Padding runs before this in every tool (SPEC.md 6, rule 6), so a
        // table reaching here divides; one from a caller that skipped it
        // packs at unit 1, which the note reports.
        if (unit > 1 && (frames % unit != 0 || (repeat < frames && repeat % unit != 0))) {
            report.note("packed at unit 1: " + (frames % unit != 0 ? "the row count " + frames
                    : "the repeat row " + repeat) + " does not divide by " + unit);
            unit = 1;
        }
        int live = live(columns.column);
        List<Integer> rings = new ArrayList<>();
        if (ring == CHOOSE) {
            rings = rings(live, columns.count());
        } else {
            int at = ringOf(ring, live, columns.count());
            if (at != ring) {
                report.note("the ring is " + Report.count(at, "byte", "bytes")
                        + ": a multiple of the period within the player's reach");
            }
            rings.add(at);
        }
        // The copies search is the most of a packing's time, so every ring
        // is tried with the first packer alone, and the two that come to the
        // least with every packer, where there is a second.
        int tried = packers.size() > 1 ? Math.min(2, rings.size()) : 1;
        Watched watched = new Watched(report, live * (rings.size() > 1 ? rings.size() : 0)
                + (packers.size() > 1 || rings.size() == 1 ? live * packers.size() * tried : 0));
        List<Packer> first = List.of(watched.around(packers.get(0)));
        List<Packer> every = new ArrayList<>();
        for (Packer packer : packers) {
            every.add(watched.around(packer));
        }
        List<Integer> kept = rings;
        byte[] table = new byte[0];
        int at = 0;
        long least = Long.MAX_VALUE;
        SortedMap<Integer, Packed> plain = new TreeMap<>();
        if (rings.size() > 1) {
            plain = candidates(columns.column, frames, repeat, unit, rings, first.get(0), report);
            // in ring order, and the sort is stable: of two that come to the
            // same, the smaller ring is first
            List<java.util.Map.Entry<Integer, Packed>> cheapest =
                    new ArrayList<>(plain.entrySet());
            cheapest.sort(java.util.Comparator.comparingLong(
                    one -> one.getValue().table().length + (long) one.getValue().state()));
            Packed best = cheapest.get(0).getValue();
            least = best.table().length + (long) best.state();
            table = best.table();
            at = cheapest.get(0).getKey();
            // With one packer the pass above is the packing: its least is
            // the table.
            kept = new ArrayList<>();
            if (packers.size() > 1) {
                for (int i = 0; i < Math.min(tried, cheapest.size()); i++) {
                    kept.add(cheapest.get(i).getKey());
                }
                least = Long.MAX_VALUE;
            }
        }
        for (int one : kept) {
            byte[] made = table(columns.column, frames, repeat, unit, one, every);
            long cost = made.length + state(made);
            if (cost < least || cost == least && one < at) {
                least = cost;
                table = made;
                at = one;
            }
        }
        List<Sources.Source> all = sources.all();
        byte[][] tables = new byte[all.size()][];
        int sourceRows = 0;
        int sourceBytes = 0;
        for (int i = 0; i < tables.length; i++) {
            Sources.Source s = all.get(i);
            tables[i] = Dtx1.write(Table.of(s.rows(), s.repeat(), 1, s.columns()));
            sourceRows += s.rows();
            sourceBytes += tables[i].length;
        }
        byte[] named = named(name);
        int version = version(columns.count());
        int index = indexAt(version);
        int after = index + 4 * tables.length;
        int nameAt = named.length == 0 ? 0 : after;
        int here = align(after + named.length);
        // Version 10: the transformation index and the transformations
        // after the name, before the DTX3 table, so that table ends where
        // source 1 begins as in version 9 (SPEC.md 3.4).
        byte[] block = version == VERSION ? transformations(columns.transformations) : new byte[0];
        int blockAt = here;
        here = align(here + block.length);
        int tableAt = here;
        here = align(here + table.length);
        int[] sourceAt = new int[tables.length];
        for (int i = 0; i < tables.length; i++) {
            sourceAt[i] = here;
            here = align(here + tables[i].length);
        }
        byte[] file = new byte[here];
        System.arraycopy(MAGIC, 0, file, 0, 4);
        putWord(file, 4, version);
        putWord(file, FRAME_RATE_AT, frameRate);
        file[EFFECTS_AT] = (byte) columns.effects;
        file[COUNT_AT] = (byte) tables.length;
        putWord(file, NAME_AT, nameAt);
        putLong(file, TABLE_AT, tableAt);
        if (version == VERSION) {
            putLong(file, TRANSFORMATIONS_AT, blockAt);
            System.arraycopy(block, 0, file, blockAt, block.length);
        }
        System.arraycopy(named, 0, file, nameAt, named.length);
        for (int i = 0; i < tables.length; i++) {
            putLong(file, index + 4 * i,
                    sourceAt[i] | (all.get(i).counted() ? COUNTED : 0));
        }
        System.arraycopy(table, 0, file, tableAt, table.length);
        for (int i = 0; i < tables.length; i++) {
            System.arraycopy(tables[i], 0, file, sourceAt[i], tables[i].length);
        }
        return new Written(file, repeat < frames ? repeat : frames, table, frames, unit,
                tables.length, sourceRows, sourceBytes, plain);
    }

    /** The transformation index and the transformations (SPEC.md 3.4): a
     *  word T and T longs, each where transformation n begins counted
     *  from the index's first byte, then each transformation on a long, a
     *  word E and its E entries. The index begins on a long, so the block
     *  is laid out from 0. */
    static byte[] transformations(List<byte[]> tables) {
        int here = align(2 + 4 * tables.size());
        int[] at = new int[tables.size()];
        for (int n = 0; n < tables.size(); n++) {
            at[n] = here;
            here = align(here + 2 + tables.get(n).length);
        }
        byte[] block = new byte[here];
        putWord(block, 0, tables.size());
        for (int n = 0; n < tables.size(); n++) {
            putLong(block, 2 + 4 * n, at[n]);
            putWord(block, at[n], tables.get(n).length);
            System.arraycopy(tables.get(n), 0, block, at[n] + 2, tables.get(n).length);
        }
        return block;
    }

    /** The table packed with `packer` through each of `rings`, by ring. A
     *  ring the packager finds no period for at this unit is left out, with
     *  a note (DTX abi.md 4): at a unit of 4 a period divides by 4.
     *
     *  @throws IllegalArgumentException where no ring has a period */
    static SortedMap<Integer, Packed> candidates(byte[][] column, int frames, int repeat,
                                                 int unit, List<Integer> rings, Packer packer,
                                                 Report report) {
        SortedMap<Integer, Packed> plain = new TreeMap<>();
        for (int one : rings) {
            byte[] made = table(column, frames, repeat, unit, one, List.of(packer));
            try {
                plain.put(one, new Packed(made, state(made)));
            } catch (IllegalArgumentException unbound) {
                report.note("a ring of " + one + " has no period at unit " + unit);
            }
        }
        if (plain.isEmpty()) {
            throw new IllegalArgumentException("no ring the writer chooses among has a"
                    + " period at unit " + unit + " (DTX abi.md 4)");
        }
        return plain;
    }

    /** The rings the writer chooses among for a table of `columns`
     *  columns, `live` of them live: each of {@link #ringsOf} the player
     *  reaches. */
    static List<Integer> rings(int live, int columns) {
        List<Integer> rings = new ArrayList<>();
        for (int one : ringsOf(columns)) {
            if (one <= reach(live)) {
                rings.add(one);
            }
        }
        return rings;
    }

    /** The tune file with `table` in place of its DTX3 table, every other
     *  part as it is: the source tables after it move by what the table's
     *  length moves them, and the index records where each now begins
     *  (SPEC.md 3.3). The transformation index and the transformations
     *  are before the DTX3 table, where they were. */
    static byte[] withTable(byte[] file, byte[] table) {
        int tableAt = getLong(file, TABLE_AT);
        int count = file[COUNT_AT] & 0xFF;
        int index = indexAt(getWord(file, 4));
        int from = count == 0 ? file.length : getLong(file, index) & ~COUNTED;
        int to = align(tableAt + table.length);
        byte[] out = new byte[to + file.length - from];
        System.arraycopy(file, 0, out, 0, tableAt);
        System.arraycopy(table, 0, out, tableAt, table.length);
        System.arraycopy(file, from, out, to, file.length - from);
        for (int i = 0; i < count; i++) {
            int entry = getLong(out, index + 4 * i);
            putLong(out, index + 4 * i, ((entry & ~COUNTED) + to - from)
                    | (entry & COUNTED));
        }
        return out;
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

    /** The columns as a DTX3 file, packed. */
    static byte[] table(byte[][] column, int frames, int repeat, int unit, int ring,
                        List<Packer> packers) {
        int rr = repeat < frames ? repeat : frames;
        return Dtx3.write(Table.of(frames, rr, 1, column), packers, unit, ring);
    }

    /** The state block a reader of this DTX3 table needs, in bytes, as the
     *  format block of its image defines it (DTX abi.md 1). */
    static int state(byte[] table) {
        return getLong(Packager.image(table), Bound.FORMAT_AT + Bound.FORMAT_STATE_AT);
    }

    /** The columns whose values change, which DTX3 packs: every other is
     *  constant and is its value. */
    static int live(byte[][] column) {
        int live = 0;
        for (byte[] c : column) {
            for (byte b : c) {
                if (b != c[0]) {
                    live++;
                    break;
                }
            }
        }
        return live;
    }

    /** The widest ring the player reaches with `live` live columns: the
     *  last slot is that many less one rings past the first, through a
     *  16-bit displacement, and DTX3 bounds a ring at 32,512. */
    static int reach(int live) {
        return Math.min(Dtx3.MAX_RING, live < 2 ? Dtx3.MAX_RING : REACH / (live - 1));
    }

    /**
     * A packer that reports how far through the packing a write is: every
     * packer the file is written with is one of these around it, and the
     * count runs over each pack of a live column. DTX packs the columns at
     * once, so the count is one every pack shares.
     */
    static final class Watched {

        private final Report report;
        private final int total;
        private final java.util.concurrent.atomic.AtomicInteger done =
                new java.util.concurrent.atomic.AtomicInteger();

        Watched(Report report, int total) {
            this.report = report;
            this.total = total;
        }

        Packer around(Packer inner) {
            return new Packer() {
                @Override
                public byte[] pack(byte[] column, int unit, int ring, int loop) {
                    report.progress("packing the columns", done.getAndIncrement(), total);
                    return inner.pack(column, unit, ring, loop);
                }

                @Override
                public boolean copies() {
                    return inner.copies();
                }
            };
        }
    }

    /** What the packing came to, a column at a time and in all. */
    static void packed(Report report, Written written, String chose) {
        if (!report.says()) {
            return;
        }
        byte[] table = written.table();
        int frames = written.frames();
        int columns = Dtx.header(table).columns();
        Dtx3.Payload payload = Dtx3.payload(table);
        report.say("the table: " + Report.count(columns, "column", "columns") + " of "
                + Report.count(frames, "row", "rows") + ", "
                + Report.count((long) columns * frames, "byte", "bytes") + ", packed at unit "
                + written.unit() + " through a ring of " + payload.ring() + chose
                + ", " + Report.count(payload.live(), "live column", "live columns"));
        for (int c = 0; c < columns; c++) {
            if (!payload.live(c)) {
                report.row(name(c), String.format(Locale.ROOT, "%7d -> constant %d",
                        frames, payload.constants()[c]));
                continue;
            }
            int bytes = payload.bytes(payload.map()[c], table.length - Dtx.HEADER);
            report.row(name(c), String.format(Locale.ROOT, "%7d -> %6d bytes  (%5.1f%%)",
                    frames, bytes, 100.0 * bytes / frames));
        }
        report.say("the sources: " + Report.count(written.sources(), "table", "tables") + " of "
                + Report.count(written.sourceRows(), "row", "rows") + ", "
                + Report.count(written.sourceBytes(), "byte", "bytes"));
        int raw = columns * frames;
        report.say(String.format(Locale.ROOT, "packed %s into %d (%.1f%%), the state block %s,"
                        + " the file %s",
                Report.count(raw, "byte", "bytes"), table.length, 100.0 * table.length / raw,
                Report.count(state(table), "byte", "bytes"),
                Report.count(written.file().length, "byte", "bytes")));
    }

    /** What a column is, for a reported row: a register by its number, an
     *  effect column by its effect and its part of it, and a mapping column
     *  by its effect. */
    static String name(int c) {
        if (c < Columns.EFFECT) {
            return "R" + c;
        }
        if (c >= Columns.MAPPING) {
            return "effect " + (c - Columns.MAPPING) + " mapping";
        }
        int effect = (c - Columns.EFFECT) / 4;
        return "effect " + effect + " " + switch ((c - Columns.EFFECT) % 4) {
            case 0 -> "target";
            case 1 -> "source";
            case 2 -> "timer control";
            default -> "timer count";
        };
    }

    /** The ring a table of `columns` columns packs through: the multiple
     *  of `columns` nearest `ring`, at least two periods and at most what
     *  the player reaches with `live` live columns. */
    static int ringOf(int ring, int live, int columns) {
        int nearest = Math.round((float) ring / columns) * columns;
        return Math.max(2 * columns, Math.min(reach(live) / columns * columns, nearest));
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
