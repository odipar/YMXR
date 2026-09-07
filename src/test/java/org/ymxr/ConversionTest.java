package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.dtx.Dtx;
import org.dtx.Table;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Every tune under ym/test, converted, read back and replayed against the
 * dump it came from: the register a row leaves is the dump's, an effect
 * runs where the dump flags one, and a digidrum runs for the frames its
 * rows take.
 */
final class ConversionTest {

    static List<Path> tunes() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("ym/test"))) {
            return files.filter(p -> p.toString().endsWith(".ym")).sorted().toList();
        }
    }

    @TestFactory
    Stream<DynamicTest> everyTuneReplaysToItsDump() throws IOException {
        return tunes().stream().map(p -> DynamicTest.dynamicTest(p.getFileName().toString(),
                () -> replays(p)));
    }

    private static void replays(Path path) throws IOException {
        List<String> wrong = Check.of(YmDump.read(Files.readAllBytes(path)));
        assertTrue(wrong.isEmpty(), () -> path + ":\n" + String.join("\n", wrong));
    }

    @Test
    void anOddRowCountPacksAtUnitOne() throws IOException {
        // Thirty-one frames of a dump that repeats, cut to a tune that plays
        // once: a column of 31 bytes does not divide by a unit of 2 (DTX's
        // R5.6), so the table packs at unit 1, and no row is added.
        YmDump.Song whole = YmDump.read(Files.readAllBytes(Path.of("ym/test/Turrican - world 4-3.ym")));
        int frames = 31;
        byte[][] registers = new byte[whole.registers().length][];
        for (int r = 0; r < registers.length; r++) {
            registers[r] = Arrays.copyOf(whole.registers()[r], frames);
        }
        YmDump.Song song = new YmDump.Song(whole.format(), frames, whole.playerHz(),
                whole.masterClock(), frames, whole.interleaved(), whole.attributes(),
                whole.drums(), whole.name(), whole.author(), whole.comment(), registers);
        Report report = new Report();
        Sources sources = new Sources(song);
        Columns columns = new Columns(song, sources, frames, report);
        Tune.Written written = Tune.write(columns, sources, song.playerHz(), 2, Tune.RING, report);
        TuneFile tune = TuneFile.read(written.file());
        assertEquals(31, tune.table().rows(), "the rows are the frames");
        assertEquals(31, tune.table().repeat(), "a tune that plays once repeats at its row count");
        // the payload's byte 2 is k, the unit (DTX, SPEC.md 2.3)
        assertEquals(1, tune.dtx2()[Dtx.HEADER + 2] & 0xFF, "the table's unit");
        assertTrue(report.notes().stream().anyMatch(n -> n.contains("packed at unit 1")),
                "the tool says why: " + report.notes());
    }

    @Test
    void aSourceChangedUnderARunningTimerPlacesItWithoutStoppingTheTimer() throws IOException {
        // SPEC.md 6 rule 5: a row that sets the source column sets bit 5 of
        // the control column with it, and bit 6 where the timer is stopped.
        // Effect 1 of Synergy Credits starts on row 12, where nothing runs
        // on its timer, and takes another source at another count on row 36,
        // where the timer has run since. The count row 36 gives is taken
        // when the running count reaches zero (1.9), so the pitch moves and
        // the phase holds.
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Synergy Credits.ym"));
        Table table = TuneFile.read(YmToYmxr.convert(dump, List.of(), new Report())
                .written().file()).table();
        int t = Columns.EFFECT + 4;
        assertEquals(0x80 | 10, table.column(t)[12] & 0xFF, "row 12 gives effect 1 R10");
        assertEquals(0x80 | 3, table.column(t + 1)[12] & 0xFF, "row 12 starts source 3");
        assertEquals(0x80 | Columns.TIMER_RESET | Columns.PLACE_RESET | 5,
                table.column(t + 2)[12] & 0xFF, "row 12 starts the stopped timer");
        assertEquals(0xE7, table.column(t + 3)[12] & 0xFF, "row 12's count");
        assertEquals(0x80 | 1, table.column(t + 1)[36] & 0xFF, "row 36 takes source 1");
        assertEquals(0x80 | Columns.PLACE_RESET | 5, table.column(t + 2)[36] & 0xFF,
                "row 36 places the source without stopping the timer");
        assertEquals(0xEB, table.column(t + 3)[36] & 0xFF, "row 36's count");
        for (int i = 0; i < 2; i++) {
            byte[] source = table.column(Columns.EFFECT + 4 * i + 1);
            byte[] control = table.column(Columns.EFFECT + 4 * i + 2);
            for (int f = 0; f < table.rows(); f++) {
                if ((source[f] & 0xFF) > 0x80) {
                    assertEquals(Columns.PLACE_RESET, control[f] & Columns.PLACE_RESET,
                            "row " + f + " starts effect " + i + " without the place's reset");
                }
            }
        }
    }

    @Test
    void aSidVoiceIsTwoRowsAndABuzzerOne() throws IOException {
        YmDump.Song song = YmDump.read(Files.readAllBytes(Path.of("ym/test/Retrigger retune, built.ym")));
        Sources sources = new Sources(song);
        Report report = new Report();
        int number = 0;
        for (int f = 0; f < song.frames() && number == 0; f++) {
            for (Effects.Slot slot : Effects.of(song, f)) {
                if (slot.kind() == Effects.BUZZER) {
                    number = sources.number(slot, report);
                    assertEquals(13, slot.target());
                    assertEquals(1, sources.get(number).rows().length);
                    assertEquals(0, sources.get(number).repeat());
                    assertEquals(Sources.MARK | (slot.data() & 15), sources.get(number).rows()[0] & 0xFF);
                }
            }
        }
        assertTrue(number > 0, "the tune has a sync buzzer");
        Sources.Source sid = sources.get(sources.number(
                new Effects.Slot(Effects.SID, 0, 8, 9, 1, 100), report));
        assertEquals(2, sid.rows().length);
        assertEquals(0, sid.rows()[0], "a square wave begins on its silent half");
        assertEquals(Sources.MARK | 9, sid.rows()[1] & 0xFF);
        assertEquals(0, sid.repeat());
    }
}
