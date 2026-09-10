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
                List<Table> sources) {

    static TuneFile read(byte[] file) {
        if (file.length < Tune.INDEX_AT || !Arrays.equals(Arrays.copyOf(file, 4), Tune.MAGIC)) {
            throw new IllegalArgumentException("not a YMXR file");
        }
        int version = Tune.getWord(file, 4);
        if (version != Tune.VERSION) {
            throw new IllegalArgumentException("version " + version + " is not "
                    + Tune.VERSION);
        }
        int count = file[Tune.COUNT_AT] & 0xFF;
        int tableAt = Tune.getLong(file, Tune.TABLE_AT);
        int end = count == 0 ? file.length : Tune.getLong(file, Tune.INDEX_AT);
        byte[] dtx2 = Arrays.copyOfRange(file, tableAt, end);
        Table table = Dtx.read(dtx2);
        List<Table> sources = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int at = Tune.getLong(file, Tune.INDEX_AT + 4 * i);
            int to = i + 1 < count ? Tune.getLong(file, Tune.INDEX_AT + 4 * (i + 1)) : file.length;
            Table source = Dtx.read(Arrays.copyOfRange(file, at, to));
            // SPEC.md 3.1: a source is one column of one byte at this
            // version, the row shape 2.1's procedures read. A wider one or one of
            // more columns is a later version's, and the player would read
            // its rows a byte at a time and play something else, so it is
            // rejected here as a tune of another version is.
            if (source.columns() != 1 || source.width() != 1) {
                throw new IllegalArgumentException("source " + (i + 1) + " is "
                        + source.columns() + " columns of " + source.width()
                        + " bytes, and a source is one column of one (SPEC.md 3.1)");
            }
            sources.add(source);
        }
        return new TuneFile(version, Tune.getWord(file, Tune.FRAME_RATE_AT),
                file[Tune.EFFECTS_AT] & 0xFF, dtx2, table, sources);
    }
}
