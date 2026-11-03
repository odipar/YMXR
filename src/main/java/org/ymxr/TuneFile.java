package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dtx.Dtx;
import org.dtx.Table;

/**
 * A decoded tune file: metadata, packed and unpacked DTX3 table,
 * sources and optional transformation entries. Transformation n is at
 * index n - 1; source key k selects entry k. Versions below 10 have an
 * empty transformation list (SPEC.md 3.3, 3.4).
 */
record TuneFile(int version, int frameRate, int effects, byte[] dtx3, Table table,
                List<Table> sources, List<Boolean> counted, List<byte[]> transformations) {

    static TuneFile read(byte[] file) {
        if (file.length < Tune.INDEX_AT || !Arrays.equals(Arrays.copyOf(file, 4), Tune.MAGIC)) {
            throw new IllegalArgumentException("not a YMXR file");
        }
        int version = Tune.getWord(file, 4);
        // SPEC.md 3.3.5: versions 3 to 6 have a DTX2 table, which a player of
        // this version reads wrongly, and the tools write the tune again from
        // what it was converted from.
        if (version >= Tune.VERSION_DTX2 && version <= Tune.VERSION_DTX2_LAST) {
            throw new IllegalArgumentException("version " + version + " has a DTX2"
                    + " table, and this reads " + Tune.READS + ": write the tune again"
                    + " with ym-to-ymxr or ymxs-to-ymxr");
        }
        if (!Tune.reads(version)) {
            throw new IllegalArgumentException("version " + version + " is not "
                    + Tune.READS);
        }
        int effects = file[Tune.EFFECTS_AT] & 0xFF;
        // SPEC.md 3.3: bit 5 of the effects used is 1 in version 10 and 0
        // in every other version.
        boolean maps = (effects & Columns.MAPS) != 0;
        if (maps != (version == Tune.VERSION)) {
            throw new IllegalArgumentException("version " + version + " with bit 5 of the"
                    + " effects used " + (maps ? "set" : "clear"));
        }
        List<byte[]> transformations = version == Tune.VERSION
                ? transformations(file) : List.of();
        int index = Tune.indexAt(version);
        int count = file[Tune.COUNT_AT] & 0xFF;
        // SPEC.md 3.3.4: each offset of the header is read against the
        // file's length before a byte is read through it, so a file cut
        // short or written wrong is a line rather than an exception the
        // reader's caller sees. The source index comes first, since the
        // table ends at its first entry.
        if (index + 4 * count > file.length) {
            throw new IllegalArgumentException("the source index at " + index
                    + " is outside the file");
        }
        long tableAt = Tune.getLong(file, Tune.TABLE_AT) & 0xFFFFFFFFL;
        int end = count == 0 ? file.length
                : Tune.getLong(file, index) & ~Tune.COUNTED;
        if (end > file.length || tableAt > end) {
            throw new IllegalArgumentException("the table is at " + tableAt + " to "
                    + end + ", and the file has " + file.length + " bytes");
        }
        byte[] dtx3 = Arrays.copyOfRange(file, (int) tableAt, end);
        int variant = dtx3.length > 3 ? dtx3[3] & 0xFF : 0;
        if (variant != Dtx.DTX3) {
            throw new IllegalArgumentException("the table is DTX" + variant
                    + ", and a tune's table is DTX3 (SPEC.md 3.3.3)");
        }
        // The shape is read off the header before the payload: a DTX3
        // payload's map is a byte a column, so a table of another C reads
        // as no payload at all (DTX, SPEC.md 2.4).
        Dtx.Header shape = Dtx.header(dtx3);
        int columns = Tune.columns(version);
        if (shape.columns() != columns || shape.width() != 1) {
            throw new IllegalArgumentException("the table is " + shape.columns()
                    + " columns of " + shape.width() + " bytes, and a tune's table is "
                    + columns + " of one (SPEC.md 3.3.3)");
        }
        Table table = Dtx.read(dtx3);
        List<Table> sources = new ArrayList<>();
        List<Boolean> counted = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int entry = Tune.getLong(file, index + 4 * i);
            int at = entry & ~Tune.COUNTED;
            counted.add((entry & Tune.COUNTED) != 0);
            int to = i + 1 < count
                    ? Tune.getLong(file, index + 4 * (i + 1)) & ~Tune.COUNTED
                    : file.length;
            if (at < 0 || to > file.length || at > to) {
                throw new IllegalArgumentException("source " + (i + 1) + " is at " + at
                        + " to " + to + ", and the file has " + file.length + " bytes");
            }
            Table source = Dtx.read(Arrays.copyOfRange(file, at, to));
            // SPEC.md 3.1.3: a source is one, two or three columns of one
            // byte, the row shape the target that runs it reads (2.1). A
            // wider value is a later version's, and the player would read
            // its rows a byte at a time and play something else, so it is
            // rejected here as a tune of another version is.
            if (source.columns() < 1 || source.columns() > 3 || source.width() != 1) {
                throw new IllegalArgumentException("source " + (i + 1) + " is "
                        + source.columns() + " columns of " + source.width()
                        + " bytes, and a source is one, two or three columns of one"
                        + " (SPEC.md 3.1)");
            }
            sources.add(source);
        }
        return new TuneFile(version, Tune.getWord(file, Tune.FRAME_RATE_AT), effects, dtx3,
                table, sources, counted, transformations);
    }

    /**
     * Reads version 10 transformations in validation order: header
     * offset, index bounds and count, then each table's bounds and
     * entry count in number order (SPEC.md 3.3.4). Offsets are unsigned
     * and checked against the file length before reading entries.
     * Earlier versions return an empty list.
     */
    private static List<byte[]> transformations(byte[] file) {
        // the header of version 10 has the long at 16 before the source
        // index
        if (file.length < Tune.TRANSFORMATIONS_AT + 4) {
            throw new IllegalArgumentException("not a YMXR file");
        }
        long at = Tune.getLong(file, Tune.TRANSFORMATIONS_AT) & 0xFFFFFFFFL;
        if (at + 2 > file.length) {
            throw new IllegalArgumentException("the transformation index at " + at
                    + " is outside the file");
        }
        int t = Tune.getWord(file, (int) at);
        if (t < 1 || t > Columns.MOST_TRANSFORMATIONS) {
            throw new IllegalArgumentException("the file has " + t + " transformations, and a"
                    + " version " + Tune.VERSION + " file has 1 to "
                    + Columns.MOST_TRANSFORMATIONS);
        }
        if (at + 2 + 4L * t > file.length) {
            throw new IllegalArgumentException("the transformation index at " + at
                    + " is outside the file");
        }
        List<byte[]> tables = new ArrayList<>();
        for (int n = 1; n <= t; n++) {
            long table = at + (Tune.getLong(file, (int) at + 2 + 4 * (n - 1)) & 0xFFFFFFFFL);
            if (table + 2 > file.length) {
                throw new IllegalArgumentException("transformation " + n + " at " + table
                        + " is outside the file");
            }
            int e = Tune.getWord(file, (int) table);
            if (e < 1 || e > Columns.MOST_ENTRIES) {
                throw new IllegalArgumentException("transformation " + n + " has " + e
                        + " entries, and a transformation has 1 to " + Columns.MOST_ENTRIES);
            }
            if (table + 2 + e > file.length) {
                throw new IllegalArgumentException("transformation " + n + " at " + table
                        + " is outside the file");
            }
            tables.add(Arrays.copyOfRange(file, (int) table + 2, (int) table + 2 + e));
        }
        return List.copyOf(tables);
    }
}
