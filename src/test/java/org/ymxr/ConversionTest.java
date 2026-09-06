package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        YmDump.Song song = YmDump.read(Files.readAllBytes(path));
        Report report = new Report();
        int repeat = (int) Math.min(song.loopFrame(), song.frames());
        Sources sources = new Sources(song);
        Columns columns = new Columns(song, sources, repeat, report);
        Tune.Written written = Tune.write(columns, sources, song.playerHz(), 1, Tune.RING, report);
        TuneFile tune = TuneFile.read(written.file());
        assertEquals(song.playerHz(), tune.frameRate());
        assertEquals(song.frames() + written.before() + written.after(), tune.table().rows());
        assertEquals(written.repeat(), tune.table().repeat());
        assertEquals(sources.count(), tune.sources().size());
        for (int i = 0; i < sources.count(); i++) {
            Table s = tune.sources().get(i);
            assertEquals(1, s.columns());
            byte[] rows = s.column(0);
            assertEquals(sources.get(i + 1).rows().length, rows.length);
            assertTrue((rows[rows.length - 1] & 0x80) != 0, "the last row is the marker");
            for (int r = 0; r < rows.length - 1; r++) {
                assertTrue((rows[r] & 0x80) == 0, "only the last row is the marker");
            }
        }
        Replay model = new Replay(tune.table());
        int[] drumEnd = {-1, -1};
        List<String> wrong = new ArrayList<>();
        for (int f = 0; f < song.frames(); f++) {
            if (f == repeat && repeat > 0) {
                for (int pad = 0; pad < written.before(); pad++) {
                    model.step();
                }
            }
            model.step();
            int[] dump = Columns.registers(song, f);
            Effects.Slot[] slots = Effects.of(song, f);
            int owned = 0;
            int mixer = 0;
            for (int i = 0; i < 2; i++) {
                Replay.Effect e = model.effect[i];
                Effects.Slot slot = slots[i];
                if (slot.on() && sources.number(slot, new Report()) != 0) {
                    if (e.source() == 0) {
                        wrong.add(f + ": effect " + i + " runs nothing where the dump flags kind "
                                + slot.kind());
                    } else {
                        Sources.Source s = sources.get(e.source());
                        if (s.kind() != slot.kind() || e.target() != slot.target()
                                || e.select() != slot.select() || e.count() != slot.count()) {
                            wrong.add(f + ": effect " + i + " runs kind " + s.kind() + " on R"
                                    + e.target() + " at " + e.select() + "/" + e.count()
                                    + ", not kind " + slot.kind() + " on R" + slot.target()
                                    + " at " + slot.select() + "/" + slot.count());
                        }
                        if (slot.kind() == Effects.DRUM) {
                            assertTrue(e.started(), f + ": the drum is started");
                            drumEnd[i] = f + Columns.duration(s.rows().length, slot.select(),
                                    slot.count(), song.playerHz());
                        }
                    }
                } else if (e.source() != 0) {
                    Sources.Source s = sources.get(e.source());
                    boolean drum = s.kind() == Effects.DRUM;
                    if (!drum || f >= drumEnd[i]) {
                        wrong.add(f + ": effect " + i + " runs source " + e.source()
                                + " where the dump flags nothing");
                    }
                }
                if (e.source() != 0 && e.target() < 13) {
                    owned |= 1 << e.target();
                    if (sources.get(e.source()).kind() == Effects.DRUM) {
                        mixer |= 0x09 << (e.target() - 8);
                    }
                }
            }
            for (int c = 0; c < 13; c++) {
                int want = c == 7 ? dump[7] | mixer : dump[c];
                if ((owned & 1 << c) == 0 && model.registers[c] != want) {
                    wrong.add(f + ": R" + c + " holds " + model.registers[c] + ", not " + want);
                }
            }
            if (model.envelopeWritten != (dump[13] >= 0)
                    || (dump[13] >= 0 && model.registers[13] != dump[13])) {
                wrong.add(f + ": R13 " + (model.envelopeWritten ? "written " + model.registers[13]
                        : "not written") + ", the dump " + (dump[13] >= 0 ? "writes " + dump[13]
                        : "does not write"));
            }
            if (wrong.size() > 20) {
                break;
            }
        }
        assertTrue(wrong.isEmpty(), () -> path + ":\n" + String.join("\n", wrong));
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
