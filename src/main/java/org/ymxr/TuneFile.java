package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dtx.Dtx;
import org.dtx.Table;

/**
 * A tune file read back: what its header states, its DTX2 table as the
 * bytes the file holds and as the table unpacked out of them, and its
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
            sources.add(Dtx.read(Arrays.copyOfRange(file, at, to)));
        }
        return new TuneFile(version, Tune.getWord(file, Tune.FRAME_RATE_AT),
                file[Tune.EFFECTS_AT] & 0xFF, dtx2, table, sources);
    }
}
