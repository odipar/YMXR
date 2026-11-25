package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.ymxs.Chip;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Cut;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Remap;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Transformation;

/**
 * A tune that copies from frame, written as version 12 and read back:
 * copy from frame is source S, index entry 0, after the tables, and bit 7 of
 * the effects used marks it (SPEC.md 3.1.7, 3.3). The errors of such a
 * file (3.3.4), its record (7.2, 7.3), its check (6.4), the writer's rule
 * 2(j), and the binder, which binds it at the layout of version 9, 10 or
 * 11 (BINARIES.md 1.2).
 */
class CopyTest {

    /** Copy from frame on setR13 on Timer A at row 0, on setVoiceB on Timer D
     *  at row 1, and a square on setR10 on Timer B, the tune's one table;
     *  each stopped at row 3. */
    static org.ymxs.YMXS.Tune tune() {
        Single square = Tunes.repeating("square 12", List.of(12, 0), 0);
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            effects.add(new EnumMap<>(Timer.class));
        }
        effects.get(0).put(Timer.A, copying(13, 16, 96, Tunes.NO_PARTS));
        effects.get(0).put(Timer.B, Tunes.struck(Tunes.setting(Register.R10), square,
                Chip.prescaler(4), 150));
        effects.get(1).put(Timer.D, copying(18, 4, 200, Tunes.NO_PARTS));
        for (Timer timer : List.of(Timer.A, Timer.B, Timer.D)) {
            effects.get(3).put(timer, Tunes.STOP);
        }
        List<Map<Register, Integer>> registers = List.of(
                Map.of(Register.R2, 100, Register.R3, 1, Register.R7, 56, Register.R9, 15,
                        Register.R11, 0, Register.R12, 4, Register.R13, 8),
                Map.of(Register.R13, 10),
                Map.of(Register.R2, 90, Register.R9, 12),
                Map.of());
        List<Row> rows = new ArrayList<>();
        for (int r = 0; r < 4; r++) {
            rows.add(new Row(registers.get(r), effects.get(r)));
        }
        return new org.ymxs.YMXS.Tune("copy", "", "CopyTest", 50, Tunes.repeating(rows, 0));
    }

    /** A start of copy from frame on target {@code target} at the divisor
     *  and count, both resets set, under rows 0 to 0. */
    private static Effect copying(int target, int divisor, int count,
                                  org.ymxs.YMXS.Mapping mapping) {
        return Tunes.copying(Tunes.target(target), Chip.prescaler(divisor), count, true, true,
                false, mapping, new Cut(0, 0));
    }

    static byte[] file() {
        return ConformanceTest.schema(tune()).file();
    }

    /** The index entry of source n, 1 upward, of a version 12 file. */
    private static int entry(byte[] file, int n) {
        return Tune.getLong(file, Tune.indexAt(Tune.COPY) + 4 * (n - 1));
    }

    @Test
    void copyFromFrameIsSourceSAfterTheTables() {
        byte[] file = file();
        assertEquals(Tune.COPY, Tune.getWord(file, 4));
        assertEquals(Columns.COPIES | 0x07, file[Tune.EFFECTS_AT] & 0xFF,
                "effects 0, 1 and 2, and bit 7");
        assertEquals(2, file[Tune.COUNT_AT] & 0xFF, "the square and copy from frame");
        assertEquals(0, entry(file, 2), "index entry S is copy from frame");
        TuneFile read = TuneFile.read(file);
        assertEquals(1, read.sources().size(), "one table");
        assertEquals(2, read.count());
        assertEquals(Columns.C, read.table().columns(), "no mapping and no cut columns");
        int column = Columns.EFFECT + 1;
        assertEquals(0x80 | 2, read.table().column(column)[0] & 0xFF,
                "the start of row 0 selects source S");
        assertEquals(0x80 | 1, read.table().column(column + 8)[0] & 0xFF,
                "the square on effect 2, Timer B, is source 1");
        assertEquals(List.of(), Check.places(read));
    }

    @Test
    void theRecordLeavesCopyFromFrameOutOfTheSources() {
        String record = new String(Trace.record(file(), 3), StandardCharsets.UTF_8);
        List<String> lines = record.lines().toList();
        assertTrue(lines.get(0).startsWith("{\"rate\":50,\"effects\":135,\"sources\":[{\"rows\":"),
                lines.get(0));
        assertEquals(1, lines.get(0).split("\"rows\"").length - 1, "one table in the line");
        assertTrue(!lines.get(0).contains("cuts") && !lines.get(0).contains("transformations"),
                "a tune of version 12 that cuts no source and maps no value: "
                        + lines.get(0));
        assertTrue(lines.get(1).contains("\"0\":{\"target\":13,\"source\":2,"), lines.get(1));
        assertTrue(lines.get(1).contains("\"13\":8"), "the row sets R13: " + lines.get(1));
        assertTrue(lines.get(1).contains(",\"mapping\":0,\"cut\":0}"),
                "a version 12 entry has the mapping and the cut: " + lines.get(1));
        assertTrue(lines.get(3).contains("\"9\":12"),
                "a register copy from frame runs on is in w: " + lines.get(3));
    }

    /** Files of version 12 written wrong, each with the line a reader reports
     *  of it (SPEC.md 3.3.4). {@code ParityTest} reads each in both trees. */
    static Map<String, byte[]> wrong() {
        byte[] file = file();
        Map<String, byte[]> out = new LinkedHashMap<>();
        byte[] clear = file.clone();
        clear[Tune.EFFECTS_AT] &= (byte) ~Columns.COPIES;
        out.put("version 12 with bit 7 of the effects used clear", clear);
        byte[] eleven = CutTest.file();
        eleven[Tune.EFFECTS_AT] |= (byte) Columns.COPIES;
        out.put("version 11 with bit 7 of the effects used set", eleven);
        byte[] table = file.clone();
        Tune.putLong(table, Tune.indexAt(Tune.COPY) + 4, entry(file, 1));
        out.put("version 12 with source 2 other than copy from frame", table);
        byte[] first = file.clone();
        Tune.putLong(first, Tune.indexAt(Tune.COPY), 0);
        out.put("source 1 is copy from frame, and copy from frame is source 2", first);
        return out;
    }

    @Test
    void aFileOfVersionTwelveWrittenWrongIsOneLine() {
        for (Map.Entry<String, byte[]> one : wrong().entrySet()) {
            assertEquals(one.getKey(), assertThrows(IllegalArgumentException.class,
                    () -> TuneFile.read(one.getValue())).getMessage());
        }
    }

    @Test
    void aTableRewrittenKeepsCopyFromFrameAtZero() {
        byte[] file = file();
        TuneFile read = TuneFile.read(file);
        byte[] past = Tune.withTable(file, read.dtx3());
        assertEquals(0, entry(past, 2), "a table rewritten keeps copy from frame at 0");
        assertEquals(entry(file, 1), entry(past, 1));
    }

    /** The tune of {@link #tune} with copy from frame on setR13 through
     *  transformation 1, half, from row 0 and through transformation 2,
     *  third, from a remap at row 2. */
    static org.ymxs.YMXS.Tune mapped() {
        Transformation half = new Transformation("half", List.of(0, 0, 1, 1, 2, 2, 3, 3, 4,
                4, 5, 5, 6, 6, 7, 7));
        Transformation third = new Transformation("third", List.of(0, 0, 0, 1, 1, 1, 2, 2, 2,
                3, 3, 3, 4, 4, 4, 5));
        List<Row> rows = new ArrayList<>(tune().table().rows());
        Map<Timer, Effect> mapped = new EnumMap<>(rows.get(0).effects());
        mapped.put(Timer.A, copying(13, 16, 96, Tunes.mapping(half)));
        rows.set(0, new Row(rows.get(0).registers(), mapped));
        Map<Timer, Effect> remapped = new EnumMap<>(rows.get(2).effects());
        remapped.put(Timer.A, new Remap(Tunes.mapping(third)));
        rows.set(2, new Row(rows.get(2).registers(), remapped));
        return new org.ymxs.YMXS.Tune("copy mapped", "", "CopyTest", 50,
                Tunes.repeating(rows, 0));
    }

    /** Copy from frame through a transformation, at a start and at a remap
     *  of its timer (rule 2(j)): the mapping column selects each, and the
     *  list of pages has both in the form of a counted source, plain
     *  (SPEC.md 3.4.4). */
    @Test
    void copyFromFrameRunsThroughATransformation() {
        byte[] file = ConformanceTest.schema(mapped()).file();
        TuneFile read = TuneFile.read(file);
        assertEquals(Tune.COPY, read.version());
        assertTrue(Columns.maps(read.effects()) && read.copies());
        byte[] column = read.table().column(read.mapping(0));
        assertEquals(0x81, column[0] & 0xFF, "the start's mapping column");
        assertEquals(0x82, column[2] & 0xFF, "the remap's mapping column");
        assertArrayEquals(new byte[] {'Y', 'P', 2, 0, Pages.PLAIN, 1, 0, Pages.PLAIN, 2},
                Pages.of(read), "the pages of copy from frame on setR13");
    }

    /** The binder binds a tune file of version 12 whose rows map no value
     *  and cut no source at the layout of version 9: the effects byte as the
     *  file has it, bit 7 at 1, and the S - 1 tables in the index, copy from
     *  frame being source S past them (BINARIES.md 1.2). */
    @Test
    void theBinderBindsVersionTwelveAtTheLayoutOfVersionNine() {
        byte[] tune = file();
        byte[] bound = Bound.of(tune);
        assertEquals(Tune.UNMAPPED, Tune.getWord(bound, 4));
        assertEquals(tune[Tune.EFFECTS_AT], bound[Tune.EFFECTS_AT]);
        assertEquals(0x80, bound[Tune.EFFECTS_AT] & 0x80);
        assertEquals(2, tune[Tune.COUNT_AT]);
        assertEquals(1, bound[Tune.COUNT_AT]);
    }
}
