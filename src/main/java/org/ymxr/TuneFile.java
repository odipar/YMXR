package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dtx.Dtx;
import org.dtx.Table;

/**
 * A tune file read back: its header fields, its DTX2 table as the bytes
 * in the file and as the table unpacked out of them, and its
 * sources. What a reader reports (R2.4).
 */
record TuneFile(int version, int frameRate, int effects, byte[] dtx2, Table table,
                List<Table> sources, List<Boolean> counted) {

    static TuneFile read(byte[] file) {
        if (file.length < Tune.INDEX_AT || !Arrays.equals(Arrays.copyOf(file, 4), Tune.MAGIC)) {
            throw new IllegalArgumentException("not a YMXR file");
        }
        int version = Tune.getWord(file, 4);
        if (version != Tune.VERSION && version != Tune.VERSION_COLUMNS
                && version != Tune.VERSION_COUNTED) {
            throw new IllegalArgumentException("version " + version + " is not "
                    + Tune.VERSION + ", " + Tune.VERSION_COLUMNS + " or "
                    + Tune.VERSION_COUNTED);
        }
        int count = file[Tune.COUNT_AT] & 0xFF;
        int tableAt = Tune.getLong(file, Tune.TABLE_AT);
        int end = count == 0 ? file.length
                : Tune.getLong(file, Tune.INDEX_AT) & ~Tune.COUNTED;
        // SPEC.md 3.3.4: each offset of the header is read against the
        // file's length before a byte is read through it, so a file cut
        // short or written wrong is a line rather than an exception the
        // reader's caller sees.
        if (tableAt < 0 || end > file.length || tableAt > end) {
            throw new IllegalArgumentException("the table stands at " + tableAt + " to "
                    + end + ", and the file has " + file.length + " bytes");
        }
        byte[] dtx2 = Arrays.copyOfRange(file, tableAt, end);
        int variant = dtx2.length > 3 ? dtx2[3] & 0xFF : 0;
        if (variant != Dtx.DTX2) {
            throw new IllegalArgumentException("the table is DTX" + variant
                    + ", and a tune's table is DTX2 (SPEC.md 3.3.3)");
        }
        Table table = Dtx.read(dtx2);
        if (table.columns() != Columns.C || table.width() != 1) {
            throw new IllegalArgumentException("the table is " + table.columns()
                    + " columns of " + table.width() + " bytes, and a tune's table is "
                    + Columns.C + " of one (SPEC.md 3.3.3)");
        }
        List<Table> sources = new ArrayList<>();
        List<Boolean> counted = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int entry = Tune.getLong(file, Tune.INDEX_AT + 4 * i);
            int at = entry & ~Tune.COUNTED;
            counted.add((entry & Tune.COUNTED) != 0);
            int to = i + 1 < count
                    ? Tune.getLong(file, Tune.INDEX_AT + 4 * (i + 1)) & ~Tune.COUNTED
                    : file.length;
            if (at < 0 || to > file.length || at > to) {
                throw new IllegalArgumentException("source " + (i + 1) + " stands at " + at
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
            // SPEC.md 3.3.5: version 3 writes a source of one column, so a
            // wider one under that version is a file written wrong rather
            // than a tune of a version this reads.
            if (version == Tune.VERSION && source.columns() > 1) {
                throw new IllegalArgumentException("source " + (i + 1) + " is "
                        + source.columns() + " columns, and version " + Tune.VERSION
                        + " writes one");
            }
            // SPEC.md 3.3.5: the versions below this one write no counted
            // source, so bit 31 under one of them is a file written wrong.
            if (version != Tune.VERSION_COUNTED && counted.get(i)) {
                throw new IllegalArgumentException("source " + (i + 1) + " is counted,"
                        + " and version " + version + " writes the marker");
            }
            sources.add(source);
        }
        return new TuneFile(version, Tune.getWord(file, Tune.FRAME_RATE_AT),
                file[Tune.EFFECTS_AT] & 0xFF, dtx2, table, sources, counted);
    }
}
