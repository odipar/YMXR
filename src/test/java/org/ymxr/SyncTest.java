package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.ymxs.Chip;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Cut;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Identity;
import org.ymxs.YMXS.Pair;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Transformation;
import org.ymxs.YMXS.Two;
import org.ymxs.YMXS.Voice;

/**
 * A tune that syncs a voice, written as version 13 and read back: bit 5 of
 * each coarse column marks a voice the row syncs, read on every row, and
 * targets 25 to 27 sync their voice at a tick (SPEC.md 1.12, 2.1, 3.3.5).
 * Its record (7.3), the errors of such a file (3.3.4), and the binder, which
 * binds it at the layout of version 9, 10 or 11 with bit 7 of byte 10 set
 * (BINARIES.md 1.2).
 */
class SyncTest {

    /** The tune of the YMXS conformance kit's {@code sync.json}: row 0
     *  syncs the three voices; copy from frame on syncToneA on Timer A from
     *  row 1, a source of two rows on syncToneB on Timer B from row 3, and
     *  copy from frame on syncToneC on Timer C from row 4, its coarse value
     *  through a transformation; rows 2, 5, 6 and 7 sync a voice, row 5
     *  while Timer A runs on it; Timers A and B stop at row 6, and C at row
     *  7. */
    static org.ymxs.YMXS.Tune tune() {
        Pair pulse = new Pair("pulse", Tunes.repeating(List.of(new Two(150, 0),
                new Two(140, 0)), 0));
        Transformation up = new Transformation("coarse up", List.of(1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 15));
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            effects.add(new EnumMap<>(Timer.class));
        }
        effects.get(1).put(Timer.A, Tunes.copying(Tunes.target(25), Chip.prescaler(4), 100,
                true, true, false, Tunes.NO_PARTS, new Cut(0, 0)));
        effects.get(3).put(Timer.B, Tunes.starting(Tunes.target(26), pulse,
                Chip.prescaler(10), 80, true, true, false, Tunes.NO_PARTS, new Cut(0, 1)));
        effects.get(4).put(Timer.C, Tunes.copying(Tunes.target(27), Chip.prescaler(4), 120,
                true, true, false, Tunes.mapping(new Identity(), up), new Cut(0, 0)));
        effects.get(6).put(Timer.A, Tunes.STOP);
        effects.get(6).put(Timer.B, Tunes.STOP);
        effects.get(7).put(Timer.C, Tunes.STOP);
        Map<Register, Integer> first = new EnumMap<>(Register.class);
        first.putAll(Map.of(Register.R0, 200, Register.R1, 1, Register.R2, 100,
                Register.R3, 0, Register.R4, 50, Register.R5, 2, Register.R7, 56));
        first.putAll(Map.of(Register.R8, 15, Register.R9, 15, Register.R10, 15));
        List<Map<Register, Integer>> registers = List.of(first, Map.of(),
                Map.of(Register.R0, 180), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(Register.R4, 60));
        List<Set<Voice>> syncs = List.of(Set.of(Voice.A, Voice.B, Voice.C), Set.of(),
                Set.of(Voice.B), Set.of(), Set.of(), Set.of(Voice.A), Set.of(Voice.A),
                Set.of(Voice.C));
        List<Row> rows = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            rows.add(new Row(registers.get(r), effects.get(r), syncs.get(r)));
        }
        return new org.ymxs.YMXS.Tune("Sync squares", "", "SyncTest", 50,
                Tunes.repeating(rows, 0));
    }

    /** A tune that syncs voice B at its frame alone, at rows 0 and 2, and
     *  sets R3 at row 0 alone. */
    static org.ymxs.YMXS.Tune frames() {
        List<Row> rows = List.of(
                new Row(Map.of(Register.R2, 100, Register.R3, 1), Map.of(), Set.of(Voice.B)),
                new Row(Map.of(Register.R2, 90), Map.of(), Set.of()),
                new Row(Map.of(), Map.of(), Set.of(Voice.B)),
                new Row(Map.of(), Map.of(), Set.of()));
        return new org.ymxs.YMXS.Tune("Sync at frames", "", "SyncTest", 50,
                Tunes.repeating(rows, 0));
    }

    static byte[] file() {
        return ConformanceTest.schema(tune()).file();
    }

    /** The index entry of source n, 1 upward, of a version 13 file. */
    private static int entry(byte[] file, int n) {
        return Tune.getLong(file, Tune.indexAt(Tune.SYNC) + 4 * (n - 1));
    }

    @Test
    void aTuneThatSyncsIsVersionThirteen() {
        byte[] file = file();
        assertEquals(Tune.SYNC, Tune.getWord(file, 4));
        assertEquals(Columns.COPIES | Columns.MAPS | 0x0D, file[Tune.EFFECTS_AT] & 0xFF,
                "effects 0, 2 and 3, the mapping columns, and copy from frame");
        assertEquals(2, file[Tune.COUNT_AT] & 0xFF, "pulse and copy from frame");
        assertEquals(0, entry(file, 2), "index entry S is copy from frame");
        TuneFile read = TuneFile.read(file);
        assertEquals(Tune.SYNC, read.version());
        assertTrue(Columns.syncs(read.effects()) && read.copies());
        assertEquals(Columns.MAPPED, read.table().columns(), "the mapping columns");
        byte[] a = read.table().column(1);
        byte[] b = read.table().column(3);
        byte[] c = read.table().column(5);
        assertEquals(0x80 | Columns.SYNC | 1, a[0] & 0xFF, "row 0 sets R1 to 1 and syncs A");
        assertEquals(0x80 | Columns.SYNC | 0, b[0] & 0xFF, "row 0 sets R3 to 0 and syncs B");
        assertEquals(Columns.SYNC, b[2] & 0xFF, "row 2 syncs B and leaves R3 unset");
        assertEquals(Columns.SYNC, a[5] & 0xFF, "row 5 syncs A while Timer A runs on it");
        assertEquals(Columns.SYNC, c[7] & 0xFF, "row 7 syncs C and sets R4 alone");
        assertEquals(0, a[1] & 0xFF, "row 1 syncs no voice");
        assertEquals(0x80 | 25, read.table().column(Columns.EFFECT)[1] & 0xFF,
                "the start of row 1 on syncToneA");
    }

    @Test
    void aTuneThatSyncsAtItsFrameAloneIsVersionThirteenWithBitSevenClear() {
        byte[] file = ConformanceTest.schema(frames()).file();
        assertEquals(Tune.SYNC, Tune.getWord(file, 4));
        assertEquals(0, file[Tune.EFFECTS_AT] & 0xFF, "no effect, and no copy from frame");
        assertEquals(0, file[Tune.COUNT_AT] & 0xFF);
        TuneFile read = TuneFile.read(file);
        assertEquals(Columns.C, read.table().columns());
        assertEquals(0x80 | Columns.SYNC | 1, read.table().column(3)[0] & 0xFF);
        assertEquals(Columns.SYNC, read.table().column(3)[2] & 0xFF);
    }

    @Test
    void theRecordListsTheVoicesEachRowSyncs() {
        String record = new String(Trace.record(file(), 8), StandardCharsets.UTF_8);
        List<String> lines = record.lines().toList();
        assertTrue(lines.get(0).startsWith("{\"rate\":50,\"effects\":173,\"sources\":["),
                lines.get(0));
        assertTrue(lines.get(1).contains("},\"s\":[\"A\",\"B\",\"C\"],\"e\":{}}"), lines.get(1));
        assertTrue(!lines.get(2).contains("\"s\""), "row 1 syncs no voice: " + lines.get(2));
        assertTrue(lines.get(2).contains("\"0\":{\"target\":25,\"source\":2,"), lines.get(2));
        assertTrue(lines.get(3).contains("{\"0\":180},\"s\":[\"B\"],"), lines.get(3));
        assertTrue(lines.get(6).contains("\"w\":{},\"s\":[\"A\"],"),
                "a voice whose sync the frame leaves to an effect: " + lines.get(6));
    }

    /** Files of version 13 written wrong, each with the line a reader reports
     *  of it (SPEC.md 3.3.4). {@code ParityTest} reads each in both trees. */
    static Map<String, byte[]> wrong() {
        byte[] file = file();
        Map<String, byte[]> out = new LinkedHashMap<>();
        byte[] table = file.clone();
        Tune.putLong(table, Tune.indexAt(Tune.SYNC) + 4, entry(file, 1));
        out.put("version 13 with source 2 other than copy from frame", table);
        byte[] later = file.clone();
        Tune.putWord(later, 4, Tune.SYNC + 1);
        out.put("version 14 is not " + Tune.READS, later);
        byte[] eleven = CutTest.file();
        eleven[Tune.EFFECTS_AT] |= (byte) Columns.COPIES;
        out.put("version 11 with bit 7 of the effects used set", eleven);
        return out;
    }

    @Test
    void aFileOfVersionThirteenWrittenWrongIsOneLine() {
        for (Map.Entry<String, byte[]> one : wrong().entrySet()) {
            assertEquals(one.getKey(), assertThrows(IllegalArgumentException.class,
                    () -> TuneFile.read(one.getValue())).getMessage());
        }
    }

    /** The binder binds a tune file of version 13 at the layout of its
     *  columns, version 10 for this one, which maps a value: the effects
     *  byte as the file has it, the S - 1 tables in the index, and bit 7 of
     *  byte 10 set; a tune of version 12 keeps that bit clear (BINARIES.md
     *  1.2). */
    @Test
    void theBinderMarksATuneThatSyncs() {
        byte[] tune = file();
        byte[] bound = Bound.of(tune);
        assertEquals(Tune.VERSION, Tune.getWord(bound, 4));
        assertEquals(tune[Tune.EFFECTS_AT], bound[Tune.EFFECTS_AT]);
        assertEquals(1, bound[Tune.COUNT_AT]);
        assertEquals(Bound.SYNC_MARK, bound[Bound.SYNC_AT] & Bound.SYNC_MARK);
        assertEquals(tune[Bound.SYNC_AT + 1], bound[Bound.SYNC_AT + 1], "byte 11 as it is");
        byte[] frames = Bound.of(ConformanceTest.schema(frames()).file());
        assertEquals(Tune.UNMAPPED, Tune.getWord(frames, 4));
        assertEquals(Bound.SYNC_MARK, frames[Bound.SYNC_AT] & Bound.SYNC_MARK);
        byte[] copies = Bound.of(CopyTest.file());
        assertEquals(0, copies[Bound.SYNC_AT] & Bound.SYNC_MARK, "a tune of version 12");
    }
}
