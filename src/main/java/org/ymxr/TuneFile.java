package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dtx.Dtx;
import org.dtx.Table;

/**
 * A decoded tune file: metadata, packed and unpacked DTX3 table,
 * sources, optional transformation entries and cuts. Transformation n
 * and cut n are at index n - 1; source key k selects entry k. A file
 * without mapping columns has an empty transformation list, and one without
 * cut columns an empty cut list (SPEC.md 3.3, 3.4, 3.5). The sources are
 * the tables; in version 12, and in version 13 where bit 7 of the effects
 * used is set, copy from frame is source S, after them, and has none
 * (3.1.7). In version 13 the effects carry {@link Columns#SYNCS} (3.3.5).
 */
record TuneFile(int version, int frameRate, int effects, byte[] dtx3, Table table,
                List<Table> sources, List<Boolean> counted, List<byte[]> transformations,
                List<Cut> cuts) {

    /** A cut of the index: its first row and its last row, each a long the
     *  file reads as 0 to 4,294,967,295 (SPEC.md 3.5). */
    record Cut(long first, long last) {
    }

    /** Whether a row starts copy from frame, source S (SPEC.md 3.1.7). */
    boolean copies() {
        return Columns.copies(effects);
    }

    /** S, the source count: the tables, and copy from frame where a row
     *  starts it (SPEC.md 3.1.7, 3.3). */
    int count() {
        return sources.size() + (copies() ? 1 : 0);
    }

    /** The table's mapping column of effect i, or -1 where it has none. */
    int mapping(int i) {
        return Columns.mapping(effects, i);
    }

    /** The table's cut column of effect i, or -1 where it has none. */
    int cut(int i) {
        return Columns.cut(effects, i);
    }

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
        // SPEC.md 3.3: bit 5 of the effects used is 1 in version 10, either
        // in versions 11 to 13, and 0 in versions 7 to 9; bit 6 is 1 in
        // version 11, either in versions 12 and 13, and 0 in every other
        // version; bit 7 is 1 in version 12, either in version 13, and 0 in
        // every other version.
        boolean maps = Columns.maps(effects);
        if (version == Tune.VERSION && !maps || version < Tune.VERSION && maps) {
            throw new IllegalArgumentException("version " + version + " with bit 5 of the"
                    + " effects used " + (maps ? "set" : "clear"));
        }
        boolean cuts = Columns.cuts(effects);
        if (version == Tune.CUT && !cuts || version < Tune.CUT && cuts) {
            throw new IllegalArgumentException("version " + version + " with bit 6 of the"
                    + " effects used " + (cuts ? "set" : "clear"));
        }
        boolean copies = Columns.copies(effects);
        if (version == Tune.COPY && !copies || version < Tune.COPY && copies) {
            throw new IllegalArgumentException("version " + version + " with bit 7 of the"
                    + " effects used " + (copies ? "set" : "clear"));
        }
        if (version >= Tune.CUT && file.length < Tune.CUTS_AT + 4) {
            throw new IllegalArgumentException("not a YMXR file");
        }
        List<byte[]> transformations = maps ? transformations(file) : List.of();
        List<Cut> cutList = cuts ? cuts(file) : List.of();
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
        // SPEC.md 3.1.7: where bit 7 is set index entry S is copy from
        // frame, 0, and every entry before it is the offset of a table.
        if (copies && (count == 0 || Tune.getLong(file, index + 4 * (count - 1)) != 0)) {
            throw new IllegalArgumentException("version " + version + " with source "
                    + count + " other than copy from frame");
        }
        int tables = copies ? count - 1 : count;
        for (int i = 0; copies && i < tables; i++) {
            if (Tune.getLong(file, index + 4 * i) == 0) {
                throw new IllegalArgumentException("source " + (i + 1) + " is copy from"
                        + " frame, and copy from frame is source " + count);
            }
        }
        long tableAt = Tune.getLong(file, Tune.TABLE_AT) & 0xFFFFFFFFL;
        int end = tables == 0 ? file.length
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
        int columns = Tune.columns(version, effects);
        if (shape.columns() != columns || shape.width() != 1) {
            throw new IllegalArgumentException("the table is " + shape.columns()
                    + " columns of " + shape.width() + " bytes, and a tune's table is "
                    + columns + " of one (SPEC.md 3.3.3)");
        }
        Table table = Dtx.read(dtx3);
        List<Table> sources = new ArrayList<>();
        List<Boolean> counted = new ArrayList<>();
        for (int i = 0; i < tables; i++) {
            int entry = Tune.getLong(file, index + 4 * i);
            int at = entry & ~Tune.COUNTED;
            counted.add((entry & Tune.COUNTED) != 0);
            int to = i + 1 < tables
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
        // SPEC.md 3.3.5: version 13 is a tune that syncs a voice, which the
        // effects carry in the bit above their byte.
        return new TuneFile(version, Tune.getWord(file, Tune.FRAME_RATE_AT),
                effects | (version == Tune.SYNC ? Columns.SYNCS : 0), dtx3, table, sources,
                counted, transformations, cutList);
    }

    /**
     * Reads the version 11 cut index in validation order: the word U, its
     * count, the 8U bytes after it, then each cut's two rows in number
     * order (SPEC.md 3.3.4, 3.5).
     */
    private static List<Cut> cuts(byte[] file) {
        long at = Tune.getLong(file, Tune.CUTS_AT) & 0xFFFFFFFFL;
        if (at + 2 > file.length) {
            throw new IllegalArgumentException("the cut index at " + at
                    + " is outside the file");
        }
        int u = Tune.getWord(file, (int) at);
        if (u < 1 || u > Columns.MOST_CUTS) {
            throw new IllegalArgumentException("the file has " + u + " cuts, and a version "
                    + Tune.CUT + " file has 1 to " + Columns.MOST_CUTS);
        }
        if (at + 2 + 8L * u > file.length) {
            throw new IllegalArgumentException("the cut index at " + at
                    + " is outside the file");
        }
        List<Cut> out = new ArrayList<>();
        for (int n = 1; n <= u; n++) {
            long first = Tune.getLong(file, (int) at + 2 + 8 * (n - 1)) & 0xFFFFFFFFL;
            long last = Tune.getLong(file, (int) at + 6 + 8 * (n - 1)) & 0xFFFFFFFFL;
            if (last < first) {
                throw new IllegalArgumentException("cut " + n + " is rows " + first + " to "
                        + last + ", and a cut ends at or after the row it begins at");
            }
            out.add(new Cut(first, last));
        }
        return List.copyOf(out);
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
