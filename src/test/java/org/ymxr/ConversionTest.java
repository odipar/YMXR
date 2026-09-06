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
        assertEquals(1, tune.image()[Tune.FORMAT_AT + 18] & 0xFF, "the image's unit");
        assertTrue(report.notes().stream().anyMatch(n -> n.contains("packed at unit 1")),
                "the tool says why: " + report.notes());
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
        assertEquals(9, sid.rows()[0]);
        assertEquals(Sources.MARK, sid.rows()[1] & 0xFF);
        assertEquals(0, sid.repeat());
    }
}
