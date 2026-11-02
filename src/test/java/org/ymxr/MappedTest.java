package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dtx.Table;
import org.junit.jupiter.api.Test;

/**
 * A tune file of version 10 (SPEC.md 1.10, 3.3, 3.4): the thirty-four
 * columns of {@link BuiltTunes#mapped} packed into a file and read back
 * field by field, its record, the lines of the reader for a file written
 * wrong, the lines of the check, and the tools that bind a tune file,
 * which report a line for version 10.
 */
final class MappedTest {

    /** The tune file of {@link BuiltTunes#mapped}. */
    private static byte[] file() {
        return BuiltTunes.mapped().file();
    }

    @Test
    void theColumnsOfAMappedTuneReadBackFieldByField() {
        Tune.Written written = BuiltTunes.mapped();
        byte[] file = written.file();
        assertEquals(Tune.VERSION, Tune.getWord(file, 4), "34 columns write version 10");
        int effects = Columns.MAPS | Columns.DEFERS | 0b1001;
        assertEquals(effects, file[Tune.EFFECTS_AT] & 0xFF, "bit 5 of the effects used");
        // the source index at 20, after the long at 16
        int count = file[Tune.COUNT_AT] & 0xFF;
        assertEquals(3, count);
        int index = Tune.getLong(file, Tune.TRANSFORMATIONS_AT);
        assertEquals(Tune.align(Tune.indexAt(Tune.VERSION) + 4 * count), index,
                "the transformation index follows the source index, the name empty");
        assertEquals(0, Tune.getWord(file, Tune.NAME_AT), "no name");
        // the index: a word T and T longs from its first byte, each
        // transformation on a long, a word E and its entries
        List<byte[]> fade = BuiltTunes.fade();
        assertEquals(fade.size(), Tune.getWord(file, index));
        int end = 0;
        for (int n = 0; n < fade.size(); n++) {
            int at = index + Tune.getLong(file, index + 2 + 4 * n);
            assertEquals(0, at & 3, "transformation " + (n + 1) + " begins on a long");
            assertEquals(16, Tune.getWord(file, at));
            assertArrayEquals(fade.get(n), Arrays.copyOfRange(file, at + 2, at + 18),
                    "the entries of transformation " + (n + 1));
            end = at + 18;
        }
        // the DTX3 table after the last transformation, and source 1 after it
        int tableAt = Tune.getLong(file, Tune.TABLE_AT);
        assertEquals(Tune.align(end), tableAt, "the DTX3 table follows the transformations");
        assertArrayEquals(written.table(), Arrays.copyOfRange(file, tableAt,
                tableAt + written.table().length), "the DTX3 table");
        assertEquals(Tune.align(tableAt + written.table().length),
                Tune.getLong(file, Tune.indexAt(Tune.VERSION)) & ~Tune.COUNTED,
                "source 1 follows the DTX3 table");

        TuneFile read = TuneFile.read(file);
        assertEquals(Tune.VERSION, read.version());
        assertEquals(50, read.frameRate());
        assertEquals(effects, read.effects());
        assertEquals(fade.size(), read.transformations().size());
        for (int n = 0; n < fade.size(); n++) {
            assertArrayEquals(fade.get(n), read.transformations().get(n),
                    "transformation " + (n + 1) + " read back");
        }
        Table table = read.table();
        assertEquals(Columns.MAPPED, table.columns());
        assertEquals(48, table.rows());
        assertEquals(0, table.repeat());
        byte[][] built = columns();
        for (int c = 0; c < Columns.MAPPED; c++) {
            assertArrayEquals(built[c], table.column(c), "column " + c);
        }
        assertEquals(count, read.sources().size());
        assertEquals(List.of(false, false, false), read.counted());
        assertEquals(4, read.sources().get(0).rows());
        assertEquals(16, read.sources().get(1).rows());
        assertEquals(3, read.sources().get(2).rows());
    }

    /** The columns {@link BuiltTunes#mapped} builds, read back out of the
     *  table the writer packed. */
    private static byte[][] columns() {
        Table table = org.dtx.Dtx3.read(BuiltTunes.mapped().table());
        byte[][] out = new byte[table.columns()][];
        for (int c = 0; c < out.length; c++) {
            out[c] = table.column(c);
        }
        return out;
    }

    @Test
    void aTableOfThirtyColumnsIsTheFileOfVersionNine() {
        byte[] file = BuiltTunes.deferred().file();
        assertEquals(Tune.UNMAPPED, Tune.getWord(file, 4));
        TuneFile read = TuneFile.read(file);
        assertEquals(List.of(), read.transformations());
        assertEquals(Columns.C, read.table().columns());
        String record = new String(Trace.record(file, -1), StandardCharsets.US_ASCII);
        assertFalse(record.contains("transformations"), "a version 9 record has none");
        assertFalse(record.contains("mapping"), "a version 9 record has no mapping");
    }

    @Test
    void theRecordReportsTheTransformationsAndEachKeptMapping() {
        String[] lines = new String(Trace.record(file(), -1), StandardCharsets.US_ASCII)
                .split("\n");
        StringBuilder entries = new StringBuilder();
        for (byte[] t : BuiltTunes.fade()) {
            entries.append(entries.length() == 0 ? "[" : ",[");
            for (int k = 0; k < t.length; k++) {
                entries.append(k == 0 ? "" : ",").append(t[k] & 0xFF);
            }
            entries.append(']');
        }
        assertTrue(lines[0].startsWith("{\"rate\":50,\"effects\":57,\"sources\":[{\"rows\":"
                + "[15,12,9,134],\"repeat\":0},"), lines[0]);
        assertTrue(lines[0].endsWith("],\"transformations\":[" + entries + "]}"), lines[0]);
        // row r is line r + 1; the kept mapping after each row
        assertEffect(lines[1], 0, "{\"target\":8,\"source\":1,\"select\":7,\"count\":200,"
                + "\"timer\":true,\"place\":true,\"deferred\":false,\"mapping\":1}");
        assertEffect(lines[1], 3, "{\"target\":0,\"source\":0,\"select\":0,\"count\":0,"
                + "\"timer\":false,\"place\":false,\"deferred\":false,\"mapping\":3}");
        // a remap: the source as it was, the control column unset
        assertEffect(lines[6], 0, "{\"target\":8,\"source\":1,\"select\":7,\"count\":200,"
                + "\"timer\":false,\"place\":false,\"deferred\":false,\"mapping\":6}");
        assertEffect(lines[16], 0, "{\"target\":8,\"source\":1,\"select\":7,\"count\":200,"
                + "\"timer\":false,\"place\":false,\"deferred\":false,\"mapping\":16}");
        // a start that leaves the mapping column unset keeps V0
        assertEffect(lines[21], 0, "{\"target\":8,\"source\":2,\"select\":4,\"count\":100,"
                + "\"timer\":true,\"place\":true,\"deferred\":false,\"mapping\":16}");
        assertEffect(lines[37], 0, "{\"target\":8,\"source\":2,\"select\":4,\"count\":100,"
                + "\"timer\":false,\"place\":false,\"deferred\":false,\"mapping\":0}");
        assertEffect(lines[3], 3, "{\"target\":9,\"source\":1,\"select\":7,\"count\":180,"
                + "\"timer\":true,\"place\":true,\"deferred\":false,\"mapping\":3}");
        assertEffect(lines[7], 3, "{\"target\":9,\"source\":3,\"select\":7,\"count\":120,"
                + "\"timer\":false,\"place\":true,\"deferred\":true,\"mapping\":1}");
        // a remap while the start is pending, then a start that keeps it
        assertEffect(lines[8], 3, "{\"target\":9,\"source\":3,\"select\":7,\"count\":120,"
                + "\"timer\":false,\"place\":false,\"deferred\":false,\"mapping\":2}");
        assertEffect(lines[13], 3, "{\"target\":9,\"source\":1,\"select\":7,\"count\":180,"
                + "\"timer\":true,\"place\":true,\"deferred\":false,\"mapping\":2}");
        // a remap and a retune in one row
        assertEffect(lines[25], 3, "{\"target\":9,\"source\":1,\"select\":5,\"count\":90,"
                + "\"timer\":false,\"place\":false,\"deferred\":false,\"mapping\":4}");
        // a row that sets no column of an effect leaves it out
        assertFalse(lines[18].contains("\"0\":{"), lines[18]);
    }

    /** The effect {@code i} of an entry of the record, as {@code want}. */
    private static void assertEffect(String line, int i, String want) {
        assertTrue(line.contains("\"" + i + "\":" + want), () -> "effect " + i + " of " + line
                + " is not " + want);
    }

    /**
     * Files of version 10 written wrong, each with the line a reader
     * reports of it (SPEC.md 3.3.4), in the order the reader reads them.
     * {@code ParityTest} reads each in both trees.
     */
    static Map<String, byte[]> wrong() {
        byte[] file = file();
        int index = Tune.getLong(file, Tune.TRANSFORMATIONS_AT);
        Map<String, byte[]> out = new LinkedHashMap<>();
        byte[] clear = file.clone();
        clear[Tune.EFFECTS_AT] &= (byte) ~Columns.MAPS;
        out.put("version 10 with bit 5 of the effects used clear", clear);
        byte[] nine = BuiltTunes.deferred().file();
        nine[Tune.EFFECTS_AT] |= Columns.MAPS;
        out.put("version 9 with bit 5 of the effects used set", nine);
        out.put("not a YMXR file", Arrays.copyOf(file, Tune.TRANSFORMATIONS_AT + 3));
        byte[] far = file.clone();
        Tune.putLong(far, Tune.TRANSFORMATIONS_AT, file.length - 1);
        out.put("the transformation index at " + (file.length - 1) + " is outside the file",
                far);
        byte[] none = file.clone();
        Tune.putWord(none, index, 0);
        out.put("the file has 0 transformations, and a version 10 file has 1 to 127", none);
        byte[] many = file.clone();
        Tune.putWord(many, index, 128);
        out.put("the file has 128 transformations, and a version 10 file has 1 to 127", many);
        // an index of four transformations two bytes from the end
        byte[] cut = file.clone();
        Tune.putLong(cut, Tune.TRANSFORMATIONS_AT, file.length - 2);
        Tune.putWord(cut, file.length - 2, 4);
        out.put("the transformation index at " + (file.length - 2) + " is outside the file",
                cut);
        byte[] past = file.clone();
        Tune.putLong(past, index + 2, file.length - index);
        out.put("transformation 1 at " + file.length + " is outside the file", past);
        int first = index + Tune.getLong(file, index + 2);
        byte[] empty = file.clone();
        Tune.putWord(empty, first, 0);
        out.put("transformation 1 has 0 entries, and a transformation has 1 to 256", empty);
        byte[] wide = file.clone();
        Tune.putWord(wide, first, 257);
        out.put("transformation 1 has 257 entries, and a transformation has 1 to 256", wide);
        // transformation 16 at the file's last four bytes, of sixteen entries
        byte[] end = file.clone();
        Tune.putLong(end, index + 2 + 4 * 15, file.length - 4 - index);
        Tune.putWord(end, file.length - 4, 16);
        out.put("transformation 16 at " + (file.length - 4) + " is outside the file", end);
        // 127 sources, an index of 508 bytes at 20, in a file of 527 bytes,
        // past the transformations; and a version 9 file cut inside its
        // index
        byte[] index127 = Arrays.copyOf(file, Tune.indexAt(Tune.VERSION) + 4 * 127 - 1);
        index127[Tune.COUNT_AT] = 127;
        out.put("the source index at 20 is outside the file", index127);
        byte[] index9 = Arrays.copyOf(BuiltTunes.deferred().file(), Tune.INDEX_AT + 1);
        out.put("the source index at 16 is outside the file", index9);
        byte[] narrow = file.clone();
        narrow[Tune.getLong(file, Tune.TABLE_AT) + 9] = (byte) Columns.C;
        out.put("the table is 30 columns of 1 bytes, and a tune's table is 34 of one"
                + " (SPEC.md 3.3.3)", narrow);
        return out;
    }

    @Test
    void aFileOfVersionTenWrittenWrongIsOneLine() {
        for (Map.Entry<String, byte[]> one : wrong().entrySet()) {
            assertEquals(one.getKey(), assertThrows(IllegalArgumentException.class,
                    () -> TuneFile.read(one.getValue())).getMessage());
            assertEquals(0, Trace.record(one.getValue(), -1).length,
                    "a file written wrong has no record: " + one.getKey());
        }
    }

    @Test
    void theVersionLinesNameTheFourVersionsRead() {
        byte[] eleven = file();
        Tune.putWord(eleven, 4, Tune.VERSION + 1);
        assertEquals("version 11 is not 7, 8, 9 or 10", assertThrows(
                IllegalArgumentException.class, () -> TuneFile.read(eleven)).getMessage());
        byte[] six = file();
        Tune.putWord(six, 4, 6);
        assertEquals("version 6 has a DTX2 table, and this reads 7, 8, 9 or 10: write the"
                + " tune again with ym-to-ymxr or ymxs-to-ymxr", assertThrows(
                IllegalArgumentException.class, () -> TuneFile.read(six)).getMessage());
    }

    /**
     * Files of version 10 whose rows break a rule the check reads, each
     * with its lines (SPEC.md 6.4): a mapping column that numbers a
     * transformation past the file's, and a stop that sets the mapping
     * column. {@code ParityTest} checks each in both trees.
     */
    static Map<List<String>, byte[]> unchecked() {
        Map<List<String>, byte[]> out = new LinkedHashMap<>();
        byte[][] c = columns();
        c[Columns.MAPPING][5] = (byte) (0x80 | 17);
        c[Columns.MAPPING + 3][9] = (byte) (0x80 | 127);
        out.put(List.of("5: effect 0 maps through transformation 17, and the file has 16"
                + " transformations", "9: effect 3 maps through transformation 127, and the"
                + " file has 16 transformations"), written(c, BuiltTunes.fade()));
        byte[][] stop = columns();
        stop[Columns.EFFECT + 1][40] = (byte) 0x80;
        stop[Columns.MAPPING][40] = (byte) (0x80 | 2);
        out.put(List.of("40: effect 0 stops and sets its mapping column, and a stop leaves"
                + " the mapping as it is"), written(stop, BuiltTunes.fade()));
        // one transformation, and a row that maps through a second
        byte[][] one = columns();
        for (int i = 0; i < 4; i++) {
            Arrays.fill(one[Columns.MAPPING + i], (byte) 0);
        }
        one[Columns.MAPPING][0] = (byte) (0x80 | 1);
        one[Columns.MAPPING][5] = (byte) (0x80 | 2);
        out.put(List.of("5: effect 0 maps through transformation 2, and the file has 1"
                + " transformation"), written(one, BuiltTunes.fade().subList(0, 1)));
        return out;
    }

    /** The tune of these columns, the sources of {@link BuiltTunes#mapped}
     *  and these transformations. */
    private static byte[] written(byte[][] column, List<byte[]> transformations) {
        TuneFile mapped = TuneFile.read(file());
        List<Sources.Source> sources = new java.util.ArrayList<>();
        for (Table s : mapped.sources()) {
            sources.add(new Sources.Source(Effects.SID, 0, new byte[][] {s.column(0)},
                    s.repeat()));
        }
        Columns columns = new Columns(column, 0, mapped.effects(), transformations);
        return Tune.write(columns, new Sources(sources), 50, YmToYmxr.UNIT, BuiltTunes.RING,
                new Report()).file();
    }

    @Test
    void theCheckReadsTheMappingColumns() {
        assertEquals(List.of(), Check.places(TuneFile.read(file())),
                "every row of the built tune follows the rules");
        assertArrayEquals(file(), written(columns(), BuiltTunes.fade()),
                "the columns and the sources read back write the same file");
        for (Map.Entry<List<String>, byte[]> one : unchecked().entrySet()) {
            assertEquals(one.getKey(), Check.places(TuneFile.read(one.getValue())));
        }
    }

    @Test
    void theStopLeavesTheKeptMappingAsItIs() {
        // a stop reads the source column alone (SPEC.md 1.10), so the record
        // keeps the identity row 36 set, where the stop's column numbers 2
        byte[] stop = unchecked().values().toArray(new byte[0][])[1];
        String[] lines = new String(Trace.record(stop, -1), StandardCharsets.US_ASCII)
                .split("\n");
        assertEffect(lines[41], 0, "{\"target\":8,\"source\":0,\"select\":4,\"count\":100,"
                + "\"timer\":false,\"place\":false,\"deferred\":false,\"mapping\":0}");
    }

    @Test
    void theBinderReportsALineForVersionTen() {
        String line = "the binder writes bound tunes to version 9, and this tune file is"
                + " version 10";
        assertEquals(line, Bound.UNBOUND);
        assertEquals(line, assertThrows(IllegalArgumentException.class,
                () -> Bound.of(file())).getMessage());
        assertEquals(line, assertThrows(IllegalArgumentException.class,
                () -> Bound.of(List.of(BuiltTunes.deferred().file(), file()))).getMessage());
        assertEquals("subtune 2: " + line, assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(List.of(BuiltTunes.deferred().file(), file()),
                        new Sndh.Options("Mapped", null, null, false, false))).getMessage());
    }

    @Test
    void aSetWithAMappedTuneBindsAtTen() {
        byte[] nine = BuiltTunes.deferred().file();
        assertEquals(Tune.UNMAPPED, Sndh.binds(List.of(nine)));
        assertEquals(Tune.VERSION, Sndh.binds(List.of(nine, file())));
        byte[] multi = Multi.of(List.of(nine, file()), List.of("nine", "ten"));
        assertEquals(Tune.VERSION, Tune.getWord(multi, 4), "the multi file's version");
        Multi.Read read = Multi.read(multi);
        assertArrayEquals(file(), read.tunes().get(1));
    }
}
