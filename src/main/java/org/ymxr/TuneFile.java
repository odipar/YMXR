package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dtx.Dtx;
import org.dtx.Table;

/**
 * A tune file read back: its header fields, its DTX3 table as the bytes
 * in the file and as the table unpacked out of them, and its
 * sources. What a reader reports (R2.4).
 */
record TuneFile(int version, int frameRate, int effects, byte[] dtx3, Table table,
                List<Table> sources, List<Boolean> counted) {

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
                    + " table, and this reads " + Tune.VERSION + ": write the tune again"
                    + " with ym-to-ymxr or ymxs-to-ymxr");
        }
        if (version != Tune.VERSION) {
            throw new IllegalArgumentException("version " + version + " is not "
                    + Tune.VERSION);
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
            throw new IllegalArgumentException("the table is at " + tableAt + " to "
                    + end + ", and the file has " + file.length + " bytes");
        }
        byte[] dtx3 = Arrays.copyOfRange(file, tableAt, end);
        int variant = dtx3.length > 3 ? dtx3[3] & 0xFF : 0;
        if (variant != Dtx.DTX3) {
            throw new IllegalArgumentException("the table is DTX" + variant
                    + ", and a tune's table is DTX3 (SPEC.md 3.3.3)");
        }
        // The shape is read off the header before the payload: a DTX3
        // payload's map is a byte a column, so a table of another C reads
        // as no payload at all (DTX, SPEC.md 2.4).
        Dtx.Header shape = Dtx.header(dtx3);
        if (shape.columns() != Columns.C || shape.width() != 1) {
            throw new IllegalArgumentException("the table is " + shape.columns()
                    + " columns of " + shape.width() + " bytes, and a tune's table is "
                    + Columns.C + " of one (SPEC.md 3.3.3)");
        }
        Table table = Dtx.read(dtx3);
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
        return new TuneFile(version, Tune.getWord(file, Tune.FRAME_RATE_AT),
                file[Tune.EFFECTS_AT] & 0xFF, dtx3, table, sources, counted);
    }
}
