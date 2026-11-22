package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dtx.Table;
import org.junit.jupiter.api.Test;
import org.ymxs.YMXS.Cut;

/**
 * A tune file of version 11 read: the errors of its header and its cut
 * index (SPEC.md 3.3.4), the lines the check of a file alone reports of its
 * cut columns (6.4), and the bound tune the binder writes of it, alone and
 * in a set whose sources are pooled (BINARIES.md 1.2, 1.3).
 */
class CutTest {

    /** The kit's {@code cuts}: version 11, two sources, four cuts and the
     *  cut columns at 30 to 33. */
    static byte[] file() {
        return ConformanceTest.schema(ConformanceTest.cuts()).file();
    }

    /**
     * Files of version 11 written wrong, each with the line a reader
     * reports of it (SPEC.md 3.3.4), in the order the reader reads them.
     * {@code ParityTest} reads each in both trees.
     */
    static Map<String, byte[]> wrong() {
        byte[] file = file();
        int index = Tune.getLong(file, Tune.CUTS_AT);
        Map<String, byte[]> out = new LinkedHashMap<>();
        byte[] clear = file.clone();
        clear[Tune.EFFECTS_AT] &= (byte) ~Columns.CUTS;
        out.put("version 11 with bit 6 of the effects used clear", clear);
        byte[] ten = MappedTest.file();
        ten[Tune.EFFECTS_AT] |= Columns.CUTS;
        out.put("version 10 with bit 6 of the effects used set", ten);
        out.put("not a YMXR file", Arrays.copyOf(file, Tune.CUTS_AT + 3));
        byte[] far = file.clone();
        Tune.putLong(far, Tune.CUTS_AT, file.length - 1);
        out.put("the cut index at " + (file.length - 1) + " is outside the file", far);
        byte[] none = file.clone();
        Tune.putWord(none, index, 0);
        out.put("the file has 0 cuts, and a version 11 file has 1 to 127", none);
        byte[] many = file.clone();
        Tune.putWord(many, index, 128);
        out.put("the file has 128 cuts, and a version 11 file has 1 to 127", many);
        // an index of four cuts two bytes from the end
        byte[] short4 = file.clone();
        Tune.putLong(short4, Tune.CUTS_AT, file.length - 2);
        Tune.putWord(short4, file.length - 2, 4);
        out.put("the cut index at " + (file.length - 2) + " is outside the file", short4);
        byte[] reversed = file.clone();
        Tune.putLong(reversed, index + 2 + 8, 3);
        Tune.putLong(reversed, index + 6 + 8, 1);
        out.put("cut 2 is rows 3 to 1, and a cut ends at or after the row it begins at",
                reversed);
        return out;
    }

    @Test
    void aFileOfVersionElevenWrittenWrongIsOneLine() {
        for (Map.Entry<String, byte[]> one : wrong().entrySet()) {
            assertEquals(one.getKey(), assertThrows(IllegalArgumentException.class,
                    () -> TuneFile.read(one.getValue())).getMessage());
            assertEquals(0, Trace.record(one.getValue(), -1).length,
                    "a file written wrong has no record: " + one.getKey());
        }
    }

    @Test
    void theFileReadsBackItsCuts() {
        TuneFile tune = TuneFile.read(file());
        assertEquals(Tune.CUT, tune.version());
        assertEquals(Columns.MAPPED, tune.table().columns(), "the cut columns at 30 to 33");
        assertEquals(List.of(new TuneFile.Cut(1, 3), new TuneFile.Cut(0, 3),
                new TuneFile.Cut(1, 2), new TuneFile.Cut(0, 1)), tune.cuts());
        assertEquals(List.of(), tune.transformations());
        assertEquals(List.of(-1, Columns.MAPPING), List.of(tune.mapping(0), tune.cut(0)));
    }

    /**
     * Version 11 check failures: a stop with its cut column set, a cut
     * number above the file's count, and a cut that ends past the last row
     * of the source it runs. Java and Go report the same lines (SPEC.md
     * 6.4).
     */
    static Map<List<String>, byte[]> unchecked() {
        Map<List<String>, byte[]> out = new LinkedHashMap<>();
        TuneFile tune = TuneFile.read(file());
        List<Cut> cuts = List.of(new Cut(1, 3), new Cut(0, 3), new Cut(1, 2), new Cut(0, 1));
        byte[][] stop = columns(tune);
        stop[Columns.MAPPING][4] = (byte) 0x81;
        out.put(List.of("4: effect 0 stops and sets its cut column, and a stop leaves the cut"
                + " as it is"), written(tune, stop, cuts));
        byte[][] above = columns(tune);
        above[Columns.MAPPING][0] = (byte) (0x80 | 5);
        out.put(List.of("0: effect 0 runs under cut 5, and the file has 4 cuts"),
                written(tune, above, cuts));
        // cut 1 ends at row 6 of the wave's six rows, at row 0 and at row 5,
        // the start of the second pass the padding wrote
        out.put(List.of("0: effect 0 runs source 1 of 6 rows under cut 1, rows 1 to 6",
                "5: effect 0 runs source 1 of 6 rows under cut 1, rows 1 to 6"),
                written(tune, columns(tune), List.of(new Cut(1, 6), new Cut(0, 3),
                        new Cut(1, 2), new Cut(0, 1))));
        return out;
    }

    private static byte[][] columns(TuneFile tune) {
        Table table = tune.table();
        byte[][] out = new byte[table.columns()][];
        for (int c = 0; c < out.length; c++) {
            out[c] = table.column(c).clone();
        }
        return out;
    }

    /** The tune of these columns and cuts, with the sources of {@code tune}. */
    private static byte[] written(TuneFile tune, byte[][] column, List<Cut> cuts) {
        List<Sources.Source> sources = new java.util.ArrayList<>();
        for (Table s : tune.sources()) {
            sources.add(new Sources.Source(Effects.SID, 0, new byte[][] {s.column(0)},
                    s.repeat()));
        }
        Columns columns = new Columns(column, tune.table().repeat(), tune.effects(), List.of(),
                cuts);
        return Tune.write(columns, new Sources(sources), 50, YmToYmxr.UNIT, BuiltTunes.RING,
                new Report()).file();
    }

    @Test
    void theCheckReadsTheCutColumns() {
        assertEquals(List.of(), Check.places(TuneFile.read(file())),
                "every row of the kit's tune follows the rules");
        for (Map.Entry<List<String>, byte[]> one : unchecked().entrySet()) {
            assertEquals(one.getKey(), Check.places(TuneFile.read(one.getValue())));
        }
    }

    /** The bound tune of version 11: the cut index copied after the source
     *  index, its offset at 28, the source index at 32, and the field at 24
     *  0 where the tune maps no value (BINARIES.md 1.2, 1.3). */
    @Test
    void theBoundTuneHasTheCutIndexAfterTheSourceIndex() {
        byte[] file = file();
        byte[] bound = Bound.of(file);
        int at = Tune.getLong(file, Tune.CUTS_AT);
        byte[] index = Arrays.copyOfRange(file, at, at + 2 + 8 * Tune.getWord(file, at));
        int cuts = Tune.getLong(bound, Bound.CUTS_AT);
        assertEquals(32, Bound.indexAt(Tune.CUT));
        assertEquals(Tune.align(32 + 4 * 2), cuts, "after the index of two sources");
        assertArrayEquals(index, Arrays.copyOfRange(bound, cuts, cuts + index.length));
        assertEquals(0, Tune.getLong(bound, Bound.TRANSFORMATIONS_AT));
        int first = Tune.getLong(bound, 32) & ~Tune.COUNTED;
        assertEquals(Tune.align(cuts + index.length), first, "the first source after it");
        // two copies of the tune share their sources, so an SNDH file pools
        // them, and each subtune keeps its header, its index and its cuts
        Bound.Set set = Bound.of(List.of(file, file));
        assertEquals(cuts + index.length, set.tuneBytes(0));
        assertArrayEquals(index, Arrays.copyOfRange(set.tunes().get(1), cuts,
                cuts + index.length));
    }
}
