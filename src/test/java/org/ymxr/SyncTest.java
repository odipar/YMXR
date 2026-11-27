package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
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
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Transformation;
import org.ymxs.YMXS.Two;
import org.ymxs.YMXS.Voice;

/**
 * A tune that syncs a voice, written as version 13 and read back: bit 5 of
 * each coarse column marks a voice the row syncs, read on every row (SPEC.md
 * 1.12, 3.3.5). Its record (7.3), the errors of such a file (3.3.4), and the
 * binder, which binds it at the layout of version 9, 10 or 11 with bit 7 of
 * byte 10 set (BINARIES.md 1.2).
 */
class SyncTest {

    /** Syncs at frames beside two effects, for the kit: rows 0, 5, 9 and 13
     *  sync voice A, which no effect runs on; copy from frame runs on
     *  setToneB on Timer A from row 1 to row 9, so the frame leaves the
     *  syncs of voice B at rows 3 and 8 to it and makes the one at row 12;
     *  a source of four rows runs on setToneC on Timer B from row 2, and a
     *  deferred start at row 5 of a drum on setR8 waits for its last row
     *  at about one tick a frame, so the frame leaves the syncs of voice C
     *  at rows 5 to 7 to the running source and makes those at rows 11 and
     *  14, after the drum took the timer and after the stop at row 13. */
    static org.ymxs.YMXS.Tune tune() {
        Pair tone = new Pair("tone C", Tunes.repeating(List.of(new Two(50, 2),
                new Two(55, 2), new Two(60, 2), new Two(65, 2)), 0));
        Single drum = new Single("drum", Tunes.once(List.of(15, 12, 9, 6)));
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        for (int r = 0; r < 16; r++) {
            effects.add(new EnumMap<>(Timer.class));
        }
        effects.get(1).put(Timer.A, Tunes.copying(Tunes.target(15), Chip.prescaler(4), 100,
                true, true, false, Tunes.NO_PARTS, new Cut(0, 0)));
        effects.get(10).put(Timer.A, Tunes.STOP);
        effects.get(2).put(Timer.B, Tunes.starting(Tunes.target(16), tone,
                Chip.prescaler(200), 255, true, true, false, Tunes.NO_PARTS, new Cut(0, 3)));
        effects.get(5).put(Timer.B, Tunes.starting(Tunes.target(8), drum,
                Chip.prescaler(200), 255, false, true, true, Tunes.NO_PARTS, new Cut(0, 3)));
        effects.get(13).put(Timer.B, Tunes.STOP);
        Map<Register, Integer> first = new EnumMap<>(Register.class);
        first.putAll(Map.of(Register.R0, 200, Register.R1, 1, Register.R2, 100,
                Register.R3, 0, Register.R4, 50, Register.R5, 2, Register.R7, 56));
        first.putAll(Map.of(Register.R8, 15, Register.R9, 15, Register.R10, 15));
        List<Map<Register, Integer>> registers = new ArrayList<>();
        for (int r = 0; r < 16; r++) {
            registers.add(Map.of());
        }
        registers.set(0, first);
        registers.set(3, Map.of(Register.R2, 90));
        registers.set(5, Map.of(Register.R0, 150));
        registers.set(9, Map.of(Register.R0, 120));
        registers.set(10, Map.of(Register.R2, 80));
        List<Set<Voice>> syncs = new ArrayList<>();
        for (int r = 0; r < 16; r++) {
            Set<Voice> one = EnumSet.noneOf(Voice.class);
            if (r == 0 || r == 5 || r == 9 || r == 13) {
                one.add(Voice.A);
            }
            if (r == 0 || r == 3 || r == 8 || r == 12) {
                one.add(Voice.B);
            }
            if (r == 0 || r == 5 || r == 6 || r == 7 || r == 11 || r == 14) {
                one.add(Voice.C);
            }
            syncs.add(one);
        }
        List<Row> rows = new ArrayList<>();
        for (int r = 0; r < 16; r++) {
            rows.add(new Row(registers.get(r), effects.get(r), syncs.get(r), Set.of()));
        }
        return new org.ymxs.YMXS.Tune("Syncs at frames", "", "SyncTest", 50,
                Tunes.repeating(rows, 0));
    }

    /** The targets of a sync, for the kit: the tune of the YMXS kit's
     *  {@code sync.json} with timer C under the identity and Timers A and
     *  C at a prescaler of 10, where that tune's is 4. Row 0 syncs the
     *  three voices; copy from frame on syncToneA on Timer A from row 1,
     *  a source of two rows on syncToneB on Timer B from row 3, and copy
     *  from frame on syncToneC on Timer C from row 4; rows 2, 5, 6 and 7
     *  sync a voice at their frame, row 5 while Timer A runs on it; Timers
     *  A and B stop at row 6, and C at row 7. */
    static org.ymxs.YMXS.Tune targets() {
        Pair pulse = new Pair("pulse", Tunes.repeating(List.of(new Two(150, 0),
                new Two(140, 0)), 0));
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            effects.add(new EnumMap<>(Timer.class));
        }
        effects.get(1).put(Timer.A, Tunes.copying(Tunes.target(25), Chip.prescaler(10), 100,
                true, true, false, Tunes.NO_PARTS, new Cut(0, 0)));
        effects.get(3).put(Timer.B, Tunes.starting(Tunes.target(26), pulse,
                Chip.prescaler(10), 80, true, true, false, Tunes.NO_PARTS, new Cut(0, 1)));
        effects.get(4).put(Timer.C, Tunes.copying(Tunes.target(27), Chip.prescaler(10), 120,
                true, true, false, Tunes.NO_PARTS, new Cut(0, 0)));
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
            rows.add(new Row(registers.get(r), effects.get(r), syncs.get(r), Set.of()));
        }
        return new org.ymxs.YMXS.Tune("Sync squares", "", "SyncTest", 50,
                Tunes.repeating(rows, 0));
    }

    /** A target of a sync beside volumes through transformations, for the
     *  kit: a square of two rows on syncToneA on Timer A from row 1 under
     *  the identity, a sample of ten rows on setR8, the volume of voice A,
     *  through "half volume" on Timer B from row 2, and the same sample on
     *  setR10 through "inverted", deferred, on Timer D from row 3. Rows 0
     *  and 8 sync voice A at their frame, row 8 while Timer A runs on it,
     *  and row 6 sets R0 under it. Timer A stops at row 9, B at 10 and D
     *  at 11. */
    static org.ymxs.YMXS.Tune volume() {
        Pair pulse = new Pair("pulse", Tunes.repeating(List.of(new Two(150, 0),
                new Two(140, 0)), 0));
        Single sample = Tunes.repeating("sample", List.of(15, 13, 10, 6, 2, 0, 3, 8, 12, 14),
                0);
        Transformation half = new Transformation("half volume", List.of(0, 0, 1, 1, 2, 2, 3,
                3, 4, 4, 5, 5, 6, 6, 7, 7));
        Transformation inverted = new Transformation("inverted", List.of(15, 14, 13, 12, 11,
                10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0));
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        for (int r = 0; r < 12; r++) {
            effects.add(new EnumMap<>(Timer.class));
        }
        effects.get(1).put(Timer.A, Tunes.starting(Tunes.target(25), pulse, Chip.prescaler(4),
                100, true, true, false, Tunes.NO_PARTS, new Cut(0, 1)));
        effects.get(2).put(Timer.B, Tunes.starting(Tunes.target(8), sample,
                Chip.prescaler(10), 80, true, true, false, Tunes.mapping(half), new Cut(0, 9)));
        effects.get(3).put(Timer.D, Tunes.starting(Tunes.target(10), sample,
                Chip.prescaler(16), 100, true, true, true, Tunes.mapping(inverted),
                new Cut(0, 9)));
        effects.get(9).put(Timer.A, Tunes.STOP);
        effects.get(10).put(Timer.B, Tunes.STOP);
        effects.get(11).put(Timer.D, Tunes.STOP);
        Map<Register, Integer> first = new EnumMap<>(Register.class);
        first.putAll(Map.of(Register.R0, 200, Register.R1, 1, Register.R2, 100,
                Register.R3, 0, Register.R4, 50, Register.R5, 2, Register.R7, 56));
        first.putAll(Map.of(Register.R8, 15, Register.R9, 15, Register.R10, 15));
        List<Row> rows = new ArrayList<>();
        for (int r = 0; r < 12; r++) {
            Map<Register, Integer> registers = r == 0 ? first
                    : r == 6 ? Map.of(Register.R0, 180) : Map.of();
            Set<Voice> syncs = r == 0 || r == 8 ? Set.of(Voice.A) : Set.of();
            rows.add(new Row(registers, effects.get(r), syncs, Set.of()));
        }
        return new org.ymxs.YMXS.Tune("Sync square under a mapped volume", "", "SyncTest", 50,
                Tunes.repeating(rows, 0));
    }

    /** Odd and even syncs, for the kit: rows that sync each voice at their
     *  frame, odd and even, to periods whose fine is 0 and other than 0,
     *  rows 6 and 7 after a fine of 0; a square of four rows on syncToneA
     *  on Timer A from row 8 to row 11, two of its rows a fine of 0, whose
     *  syncs rows 9 and 10 make even; and row 14, which lists voice C among
     *  its even syncs without a sync. */
    static org.ymxs.YMXS.Tune evens() {
        Pair square = new Pair("fine zero", Tunes.repeating(List.of(new Two(0, 1),
                new Two(150, 0), new Two(0, 2), new Two(90, 0)), 0));
        int count = 16;
        List<Map<Register, Integer>> registers = new ArrayList<>();
        for (int r = 0; r < count; r++) {
            registers.add(new EnumMap<>(Register.class));
        }
        registers.get(0).putAll(Map.of(Register.R0, 200, Register.R1, 1, Register.R2, 100,
                Register.R3, 0, Register.R4, 50, Register.R5, 2, Register.R7, 56));
        registers.get(0).putAll(Map.of(Register.R8, 15, Register.R9, 15, Register.R10, 15));
        registers.get(3).putAll(Map.of(Register.R2, 0, Register.R3, 1));
        registers.get(6).putAll(Map.of(Register.R0, 0, Register.R1, 1));
        registers.get(9).putAll(Map.of(Register.R2, 120, Register.R3, 0));
        registers.get(12).putAll(Map.of(Register.R4, 0, Register.R5, 2));
        Map<Integer, Set<Voice>> syncs = Map.of(0, Set.of(Voice.A), 1, Set.of(Voice.B),
                2, Set.of(Voice.A), 3, Set.of(Voice.B), 4, Set.of(Voice.B), 5, Set.of(Voice.C),
                6, Set.of(Voice.A), 7, Set.of(Voice.A), 9, Set.of(Voice.B),
                10, Set.of(Voice.B));
        Map<Integer, Set<Voice>> more = Map.of(12, Set.of(Voice.C), 13, Set.of(Voice.C));
        Map<Integer, Set<Voice>> evens = Map.of(2, Set.of(Voice.A), 3, Set.of(Voice.B),
                7, Set.of(Voice.A), 9, Set.of(Voice.A), 10, Set.of(Voice.A, Voice.B),
                13, Set.of(Voice.C), 14, Set.of(Voice.C));
        List<Row> rows = new ArrayList<>();
        for (int r = 0; r < count; r++) {
            Map<Timer, Effect> effects = new EnumMap<>(Timer.class);
            if (r == 8) {
                effects.put(Timer.A, Tunes.starting(Tunes.target(25), square,
                        Chip.prescaler(50), 100, true, true, false, Tunes.NO_PARTS,
                        new Cut(0, 3)));
            } else if (r == 11) {
                effects.put(Timer.A, Tunes.STOP);
            }
            Set<Voice> synced = new java.util.HashSet<>(syncs.getOrDefault(r, Set.of()));
            synced.addAll(more.getOrDefault(r, Set.of()));
            rows.add(new Row(registers.get(r), effects, synced,
                    evens.getOrDefault(r, Set.of())));
        }
        return new org.ymxs.YMXS.Tune("Odd and even syncs", "", "SyncTest", 50,
                Tunes.repeating(rows, 0));
    }

    /** The heads the frame writes of the handlers of a target of a sync,
     *  for the kit: copy from frame on syncToneA on Timer A from row 1 to
     *  row 14 and on syncToneC on Timer C from row 3 to row 14, and a source
     *  of four rows with no fine of 0 on syncToneB on Timer B from row 2
     *  to row 13, each odd and even by the rows' even syncs. Row 8 sets
     *  voice A's fine frame value to 0 and row 9 back, and row 10 voice
     *  C's and row 11 back, so those frames and the frames after them run
     *  the general head. */
    static org.ymxs.YMXS.Tune heads() {
        Pair steps = new Pair("steps", Tunes.repeating(List.of(new Two(150, 0),
                new Two(140, 0), new Two(130, 1), new Two(120, 0)), 0));
        int count = 16;
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        List<Map<Register, Integer>> registers = new ArrayList<>();
        for (int r = 0; r < count; r++) {
            effects.add(new EnumMap<>(Timer.class));
            registers.add(new EnumMap<>(Register.class));
        }
        effects.get(1).put(Timer.A, Tunes.copying(Tunes.target(25), Chip.prescaler(10), 100,
                true, true, false, Tunes.NO_PARTS, new Cut(0, 0)));
        effects.get(2).put(Timer.B, Tunes.starting(Tunes.target(26), steps,
                Chip.prescaler(10), 80, true, true, false, Tunes.NO_PARTS, new Cut(0, 3)));
        effects.get(3).put(Timer.C, Tunes.copying(Tunes.target(27), Chip.prescaler(10), 120,
                true, true, false, Tunes.NO_PARTS, new Cut(0, 0)));
        effects.get(13).put(Timer.B, Tunes.STOP);
        effects.get(14).put(Timer.A, Tunes.STOP);
        effects.get(14).put(Timer.C, Tunes.STOP);
        registers.get(0).putAll(Map.of(Register.R0, 200, Register.R1, 1, Register.R2, 100,
                Register.R3, 0, Register.R4, 50, Register.R5, 2, Register.R7, 56));
        registers.get(0).putAll(Map.of(Register.R8, 15, Register.R9, 15, Register.R10, 15));
        registers.get(6).put(Register.R0, 180);
        registers.get(8).putAll(Map.of(Register.R0, 0, Register.R1, 2));
        registers.get(9).put(Register.R0, 160);
        registers.get(10).putAll(Map.of(Register.R4, 0, Register.R5, 1));
        registers.get(11).put(Register.R4, 70);
        Map<Integer, Set<Voice>> evens = Map.of(3, Set.of(Voice.A), 4, Set.of(Voice.A, Voice.B),
                5, Set.of(Voice.B), 6, Set.of(Voice.C), 7, Set.of(Voice.A, Voice.C),
                8, Set.of(Voice.A), 10, Set.of(Voice.C), 11, Set.of(Voice.B),
                12, Set.of(Voice.A, Voice.C));
        List<Row> rows = new ArrayList<>();
        for (int r = 0; r < count; r++) {
            Set<Voice> syncs = r == 0 ? Set.of(Voice.A, Voice.B, Voice.C) : Set.of();
            rows.add(new Row(registers.get(r), effects.get(r), syncs,
                    evens.getOrDefault(r, Set.of())));
        }
        return new org.ymxs.YMXS.Tune("Heads of a sync", "", "SyncTest", 50,
                Tunes.repeating(rows, 0));
    }

    /** {@link #targets} with one start changed at row {@code at} on timer
     *  {@code timer}. */
    static org.ymxs.YMXS.Tune changed(int at, Timer timer, Effect effect) {
        List<Row> rows = new ArrayList<>(targets().table().rows());
        Map<Timer, Effect> effects = new EnumMap<>(Timer.class);
        effects.putAll(rows.get(at).effects());
        effects.put(timer, effect);
        rows.set(at, new Row(rows.get(at).registers(), effects, rows.get(at).syncs(), Set.of()));
        return new org.ymxs.YMXS.Tune("Sync squares", "", "SyncTest", 50,
                Tunes.repeating(rows, 0));
    }

    /** Copy from frame on syncToneC through a transformation, which rule 2(k)
     *  leaves to a later version (SPEC.md section 8). */
    static org.ymxs.YMXS.Tune throughATransformation() {
        Transformation up = new Transformation("coarse up", List.of(1, 2, 3, 4, 5, 6, 7, 8,
                9, 10, 11, 12, 13, 14, 15, 15));
        return changed(4, Timer.C, Tunes.copying(Tunes.target(27), Chip.prescaler(10), 120,
                true, true, false, Tunes.mapping(new Identity(), up), new Cut(0, 0)));
    }

    /** The source of two rows on syncToneB under its second row alone. */
    static org.ymxs.YMXS.Tune underACut() {
        Pair pulse = new Pair("pulse", Tunes.repeating(List.of(new Two(150, 0),
                new Two(140, 0)), 0));
        return changed(3, Timer.B, Tunes.starting(Tunes.target(26), pulse,
                Chip.prescaler(10), 80, true, true, false, Tunes.NO_PARTS, new Cut(1, 1)));
    }

    /** The source of two rows on syncToneB, started deferred. */
    static org.ymxs.YMXS.Tune deferred() {
        Pair pulse = new Pair("pulse", Tunes.repeating(List.of(new Two(150, 0),
                new Two(140, 0)), 0));
        return changed(3, Timer.B, Tunes.starting(Tunes.target(26), pulse,
                Chip.prescaler(10), 80, true, true, true, Tunes.NO_PARTS, new Cut(0, 1)));
    }

    @Test
    void aTargetOfASyncThroughATransformationOrUnderACutIsALaterVersions() {
        assertEquals("row 3: syncToneB starts deferred, which this version leaves to a later"
                + " one (SPEC.md section 8)", assertThrows(IllegalArgumentException.class,
                        () -> ConformanceTest.schema(deferred())).getMessage());
        assertEquals("row 4: the mapping maps syncToneC through a transformation, which this"
                + " version leaves to a later one (SPEC.md section 8)",
                assertThrows(IllegalArgumentException.class,
                        () -> ConformanceTest.schema(throughATransformation())).getMessage());
        assertEquals("row 3: syncToneB runs under a cut, which this version leaves to a later"
                + " one (SPEC.md section 8)", assertThrows(IllegalArgumentException.class,
                        () -> ConformanceTest.schema(underACut())).getMessage());
    }

    /** A start of each target of a sync: the target column selects 25 to
     *  27, the source of two rows is source 1 and copy from frame source 2,
     *  and the record reads them (SPEC.md 2.1, 7.3). */
    @Test
    void aStartOnATargetOfASyncIsTwentyFiveToTwentySeven() {
        byte[] file = ConformanceTest.schema(targets()).file();
        TuneFile read = TuneFile.read(file);
        assertEquals(Tune.SYNC, read.version());
        assertEquals(0x80 | 25, read.table().column(Columns.EFFECT)[1] & 0xFF);
        assertEquals(0x80 | 26, read.table().column(Columns.EFFECT + 8)[3] & 0xFF);
        assertEquals(0x80 | 27, read.table().column(Columns.EFFECT + 12)[4] & 0xFF);
        String record = new String(Trace.record(file, 8), StandardCharsets.UTF_8);
        assertTrue(record.contains("\"0\":{\"target\":25,\"source\":2,"), record);
    }

    /** A tune that syncs voice B at its frame alone, at rows 0 and 2, and
     *  sets R3 at row 0 alone. */
    static org.ymxs.YMXS.Tune frames() {
        List<Row> rows = List.of(
                new Row(Map.of(Register.R2, 100, Register.R3, 1), Map.of(), Set.of(Voice.B), Set.of()),
                new Row(Map.of(Register.R2, 90), Map.of(), Set.of(), Set.of()),
                new Row(Map.of(), Map.of(), Set.of(Voice.B), Set.of()),
                new Row(Map.of(), Map.of(), Set.of(), Set.of()));
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
        assertEquals(Columns.COPIES | Columns.DEFERS | 0x05, file[Tune.EFFECTS_AT] & 0xFF,
                "effects 0 and 2, a deferred start, and copy from frame");
        assertEquals(3, file[Tune.COUNT_AT] & 0xFF, "two tables and copy from frame");
        assertEquals(0, entry(file, 3), "index entry S is copy from frame");
        TuneFile read = TuneFile.read(file);
        assertEquals(Tune.SYNC, read.version());
        assertTrue(Columns.syncs(read.effects()) && read.copies());
        assertEquals(Columns.C, read.table().columns(), "no mapping and no cut columns");
        byte[] a = read.table().column(1);
        byte[] b = read.table().column(3);
        byte[] c = read.table().column(5);
        assertEquals(0x80 | Columns.SYNC | 1, a[0] & 0xFF, "row 0 sets R1 to 1 and syncs A");
        assertEquals(0x80 | Columns.SYNC | 0, b[0] & 0xFF, "row 0 sets R3 to 0 and syncs B");
        assertEquals(Columns.SYNC, b[3] & 0xFF, "row 3 syncs B and leaves R3 unset");
        assertEquals(Columns.SYNC, a[5] & 0xFF, "row 5 syncs A and leaves R1 unset");
        assertEquals(Columns.SYNC, c[6] & 0xFF, "row 6 syncs C while Timer B runs on it");
        assertEquals(0, a[1] & 0xFF, "row 1 syncs no voice");
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
        String record = new String(Trace.record(file(), 16), StandardCharsets.UTF_8);
        List<String> lines = record.lines().toList();
        assertTrue(lines.get(0).startsWith("{\"rate\":50,\"effects\":149,\"sources\":["),
                lines.get(0));
        assertTrue(lines.get(1).contains("},\"s\":[\"A\",\"B\",\"C\"],\"e\":{}}"), lines.get(1));
        assertTrue(!lines.get(2).contains("\"s\""), "row 1 syncs no voice: " + lines.get(2));
        assertTrue(lines.get(4).contains("{\"2\":90},\"s\":[\"B\"],"),
                "a voice whose sync the frame leaves to an effect: " + lines.get(4));
        assertTrue(lines.get(6).contains("{\"0\":150},\"s\":[\"A\",\"C\"],"), lines.get(6));
    }

    /** Version 14: bit 4 of the coarse column of each voice a row lists
     *  among its even syncs, beside bit 5 of each voice it syncs, read on
     *  every row, and the record's even after s (SPEC.md 1.12.2, 3.3.5,
     *  7.3). */
    @Test
    void aTuneOfEvenSyncsIsVersionFourteen() {
        byte[] file = ConformanceTest.schema(evens()).file();
        assertEquals(Tune.EVEN, Tune.getWord(file, 4));
        TuneFile read = TuneFile.read(file);
        assertEquals(Tune.EVEN, read.version());
        assertTrue(Columns.syncs(read.effects()) && Columns.evens(read.effects()));
        byte[] a = read.table().column(1);
        byte[] b = read.table().column(3);
        byte[] c = read.table().column(5);
        assertEquals(0x80 | Columns.SYNC | 1, a[0] & 0xFF, "row 0 sets R1 to 1 and syncs A");
        assertEquals(Columns.SYNC | Columns.EVEN, a[2] & 0xFF, "row 2 syncs A, even");
        assertEquals(Columns.EVEN, a[9] & 0xFF, "row 9 lists A among its even syncs alone");
        assertEquals(0x80 | 0x40 | Columns.SYNC | Columns.EVEN | 1, b[3] & 0xFF,
                "row 3 sets R2 to 0, its bit 6, and R3 to 1, and syncs B, even");
        assertEquals(Columns.EVEN, c[14] & 0xFF, "row 14 lists C without a sync");
        assertEquals(0, a[1] & 0xFF, "row 1 leaves voice A as it is");
        List<String> lines = new String(Trace.record(file, 16), StandardCharsets.UTF_8)
                .lines().toList();
        assertTrue(lines.get(3).contains(",\"s\":[\"A\"],\"even\":[\"A\"],\"e\":"),
                lines.get(3));
        assertTrue(lines.get(15).contains("},\"even\":[\"C\"],\"e\":"), lines.get(15));
        assertTrue(!lines.get(2).contains("\"even\""), "row 1 lists no voice: " + lines.get(2));
        assertEquals(Tune.SYNC, Tune.getWord(ConformanceTest.schema(tune()).file(), 4),
                "a tune whose rows list no even sync stays version 13");
    }

    /** Files of version 13 written wrong, each with the line a reader reports
     *  of it (SPEC.md 3.3.4). {@code ParityTest} reads each in both trees. */
    static Map<String, byte[]> wrong() {
        byte[] file = file();
        Map<String, byte[]> out = new LinkedHashMap<>();
        byte[] table = file.clone();
        Tune.putLong(table, Tune.indexAt(Tune.SYNC) + 8, entry(file, 1));
        out.put("version 13 with source 3 other than copy from frame", table);
        byte[] later = file.clone();
        Tune.putWord(later, 4, Tune.EVEN + 1);
        out.put("version 15 is not " + Tune.READS, later);
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
     *  columns, version 9 for this one, which maps no value and cuts no
     *  source: the effects byte as the file has it, the S - 1 tables in the
     *  index, and bit 7 of byte 10 set; a tune of version 12 keeps that bit
     *  clear (BINARIES.md 1.2). */
    @Test
    void theBinderMarksATuneThatSyncs() {
        byte[] tune = file();
        byte[] bound = Bound.of(tune);
        assertEquals(Tune.UNMAPPED, Tune.getWord(bound, 4));
        assertEquals(tune[Tune.EFFECTS_AT], bound[Tune.EFFECTS_AT]);
        assertEquals(2, bound[Tune.COUNT_AT]);
        assertEquals(Bound.SYNC_MARK, bound[Bound.SYNC_AT] & Bound.SYNC_MARK);
        assertEquals(tune[Bound.SYNC_AT + 1], bound[Bound.SYNC_AT + 1], "byte 11 as it is");
        byte[] frames = Bound.of(ConformanceTest.schema(frames()).file());
        assertEquals(Tune.UNMAPPED, Tune.getWord(frames, 4));
        assertEquals(Bound.SYNC_MARK, frames[Bound.SYNC_AT] & Bound.SYNC_MARK);
        byte[] copies = Bound.of(CopyTest.file());
        assertEquals(0, copies[Bound.SYNC_AT] & Bound.SYNC_MARK, "a tune of version 12");
    }
}
