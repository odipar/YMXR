package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
 * rows run.
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
    void anOddRowCountIsPaddedToTheUnit() throws IOException {
        // Thirty-one frames of a dump that repeats, cut to a tune that plays
        // once: a column of 31 bytes does not divide by a unit of 2 (DTX's
        // R5.6). SPEC.md 6 rule 6: a row that sets no column goes in at the
        // end, the table packs at unit 2, and the tool notes it.
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
        Schema.Made made = Schema.of(Padding.toUnit(
                Ym.read(song, sources, frames, report), 2, report).tune());
        Tune.Written written = Tune.write(made.columns(), made.sources(), song.playerHz(),
                2, Tune.RING, report);
        TuneFile tune = TuneFile.read(written.file());
        assertEquals(32, tune.table().rows(), "the frames and one row that sets no column");
        assertEquals(32, tune.table().repeat(), "a tune that plays once repeats at its row count");
        // the payload's byte 2 is k, the unit (DTX, SPEC.md 2.3)
        assertEquals(2, tune.dtx2()[Dtx.HEADER + 2] & 0xFF, "the table's unit");
        assertEquals(List.of("padded: 1 unset row at row 31, so the table packs at unit 2"),
                report.notes().stream().filter(n -> n.startsWith("padded")).toList(),
                "the tool says what it added: " + report.notes());
    }

    @Test
    void anOddRepeatRowIsPaddedBeforeIt() throws IOException {
        // A tune of 180 rows repeating to row 177: the repeat row does not
        // divide by 2, so a row goes in at 177 and the loop starts at 178.
        // The count, 181, then does not divide either, and the loop is three
        // rows, fewer than Padding.SHORT, so it is written twice: 184 rows.
        YmDump.Song song = cut(180, 177);
        Report report = new Report();
        Padding.Padded padded = Padding.toUnit(
                Ym.read(song, new Sources(song), 177, report), 2, report);
        List<org.ymxs.YMXS.Row> rows = padded.tune().table().rows();
        assertEquals(184, rows.size(), "180 rows, one before the repeat, and the loop of 3 again");
        assertEquals(178, padded.tune().table().repeat().getAsInt(), "the repeat row moved past the added row");
        assertTrue(rows.get(177).registers().isEmpty() && rows.get(177).effects().isEmpty(),
                "the added row sets no column");
        assertEquals(rows.subList(178, 181), rows.subList(181, 184), "the loop's rows, written again");
        assertEquals(List.of("padded: 1 unset row at row 177, before the repeat row, so the table packs at unit 2",
                             "padded: the loop's 3 rows written twice, so the table packs at unit 2"),
                report.notes().stream().filter(n -> n.startsWith("padded")).toList(),
                "one note an addition: " + report.notes());
        // the frame each row answers to: -1 for the added row, and the loop's
        // frames 177 to 179 twice over
        int[] frames = padded.frames();
        assertEquals(184, frames.length, "a frame a row");
        assertEquals(176, frames[176]);
        assertEquals(-1, frames[177], "a row that sets no column answers to no frame");
        assertEquals(177, frames[178]);
        assertEquals(179, frames[180]);
        assertEquals(177, frames[181], "the loop written again answers to its frames again");
        assertEquals(179, frames[183]);
    }

    @Test
    void aLongOddLoopIsPaddedAtTheEnd() throws IOException {
        // 165 rows repeating to row 100: the loop is 65 rows, SHORT or more,
        // so a row that sets no column goes in at the end rather than the
        // loop being written again, and the frame it adds is one in 65.
        YmDump.Song song = cut(165, 100);
        Report report = new Report();
        Padding.Padded padded = Padding.toUnit(
                Ym.read(song, new Sources(song), 100, report), 2, report);
        assertEquals(166, padded.tune().table().rows().size(), "165 rows and one at the end");
        assertEquals(100, padded.tune().table().repeat().getAsInt(), "the repeat row divides as it is");
        assertEquals(List.of("padded: 1 unset row at row 165, so the table packs at unit 2"),
                report.notes().stream().filter(n -> n.startsWith("padded")).toList(),
                "one note: " + report.notes());
        assertEquals(-1, padded.frames()[165], "the added row answers to no frame");
    }

    @Test
    void aShortOddLoopIsWrittenAgain() throws IOException {
        // 163 rows repeating to row 100: the loop is 63 rows, one under
        // SHORT, so it is written twice and the tune plays as it did.
        YmDump.Song song = cut(163, 100);
        Report report = new Report();
        Padding.Padded padded = Padding.toUnit(
                Ym.read(song, new Sources(song), 100, report), 2, report);
        List<org.ymxs.YMXS.Row> rows = padded.tune().table().rows();
        assertEquals(226, rows.size(), "100 rows before the loop and the loop of 63 twice");
        assertEquals(rows.subList(100, 163), rows.subList(163, 226), "the loop's rows, written again");
        assertEquals(List.of("padded: the loop's 63 rows written twice, so the table packs at unit 2"),
                report.notes().stream().filter(n -> n.startsWith("padded")).toList(),
                "one note: " + report.notes());
        assertEquals(100, padded.frames()[163], "the loop written again answers to its frames again");
        assertEquals(162, padded.frames()[225]);
    }

    @Test
    void aLoopIsWrittenUntilTheCountDivides() throws IOException {
        // 101 rows repeating to row 100 at unit 4: the loop of one row is
        // written until the count divides by 4, which is four times.
        YmDump.Song song = cut(101, 100);
        Report report = new Report();
        Padding.Padded padded = Padding.toUnit(
                Ym.read(song, new Sources(song), 100, report), 4, report);
        assertEquals(104, padded.tune().table().rows().size(), "100 rows and the loop's row four times");
        assertEquals(List.of("padded: the loop's 1 row written 4 times, so the table packs at unit 4"),
                report.notes().stream().filter(n -> n.startsWith("padded")).toList(),
                "one note: " + report.notes());
    }

    /** The first {@code frames} frames of Turrican - world 4-3 as a dump
     *  whose loop frame is {@code loop}. */
    private static YmDump.Song cut(int frames, int loop) throws IOException {
        YmDump.Song whole = YmDump.read(Files.readAllBytes(Path.of("ym/test/Turrican - world 4-3.ym")));
        byte[][] registers = new byte[whole.registers().length][];
        for (int r = 0; r < registers.length; r++) {
            registers[r] = Arrays.copyOf(whole.registers()[r], frames);
        }
        return new YmDump.Song(whole.format(), frames, whole.playerHz(),
                whole.masterClock(), loop, whole.interleaved(), whole.attributes(),
                whole.drums(), whole.name(), whole.author(), whole.comment(), registers);
    }

    @Test
    void aSourceOfAnotherShapeIsRejected() throws IOException {
        // SPEC.md 3.1: a source is one column of one byte at this version,
        // the row shape 2.1's procedures read, and a reader rejects another
        // shape as it rejects a tune of another version. The player reads a
        // source's rows a byte at a time from byte 16 of its table, so a
        // width of 2 read as this version reads it plays something else.
        byte[] file = YmToYmxr.convert(
                Files.readAllBytes(Path.of("ym/test/Synergy Credits.ym")),
                List.of(), new Report()).written().file();
        assertEquals(1, TuneFile.read(file).sources().get(0).width(),
                "the converter writes a source of one byte");
        int at = Tune.getLong(file, Tune.INDEX_AT);
        // the DTX header has W at byte 14 and C at bytes 8 and 9
        byte[] wide = file.clone();
        wide[at + 14] = 2;
        assertThrows(IllegalArgumentException.class, () -> TuneFile.read(wide),
                "a source of two-byte values is not read");
        byte[] many = file.clone();
        many[at + 9] = 2;
        assertThrows(IllegalArgumentException.class, () -> TuneFile.read(many),
                "a source of two columns is not read");
    }

    @Test
    void aFieldThatStandsOutsideTheFileIsALine() throws IOException {
        // SPEC.md 3.3.4: a reader reads each offset against the file's
        // length before it reads bytes through it, and reads the table's
        // variant and shape, so a file written wrong is one line rather
        // than the exception a copy out of range throws at the caller.
        byte[] file = YmToYmxr.convert(
                Files.readAllBytes(Path.of("ym/test/Synergy Credits.ym")),
                List.of(), new Report()).written().file();
        int tableAt = Tune.getLong(file, Tune.TABLE_AT);
        byte[] far = file.clone();
        Tune.putLong(far, Tune.TABLE_AT, file.length + 8);
        assertEquals("the table stands at " + (file.length + 8) + " to "
                        + Tune.getLong(file, Tune.INDEX_AT) + ", and the file has "
                        + file.length + " bytes",
                assertThrows(IllegalArgumentException.class,
                        () -> TuneFile.read(far)).getMessage(),
                "a table past the file's end is read against its length");
        // the last source's offset, since the table ends where source 1
        // begins and a source 1 moved would be the table's line instead
        int count = file[Tune.COUNT_AT] & 0xFF;
        assertTrue(count > 1, "the tune has sources to move: " + count);
        byte[] source = file.clone();
        Tune.putLong(source, Tune.INDEX_AT + 4 * (count - 1), file.length + 4);
        // source N - 1 ends where source N begins, so the line is its
        // alone: the reader reports the first condition in the order of
        // SPEC.md 3.3.4's table
        int before = Tune.getLong(file, Tune.INDEX_AT + 4 * (count - 2));
        assertEquals("source " + (count - 1) + " stands at " + before + " to "
                        + (file.length + 4) + ", and the file has " + file.length
                        + " bytes",
                assertThrows(IllegalArgumentException.class,
                        () -> TuneFile.read(source)).getMessage(),
                "a source past the file's end is read against its length");
        byte[] variant = file.clone();
        variant[tableAt + 3] = 1;
        assertEquals("the table is DTX1, and a tune's table is DTX2 (SPEC.md 3.3.3)",
                assertThrows(IllegalArgumentException.class,
                        () -> TuneFile.read(variant)).getMessage(),
                "a table of another variant is read as none");
        // the DTX header has C at bytes 8 and 9
        byte[] narrow = file.clone();
        narrow[tableAt + 9] = 29;
        assertEquals("the table is 29 columns of 1 bytes, and a tune's table is 30 of"
                        + " one (SPEC.md 3.3.3)",
                assertThrows(IllegalArgumentException.class,
                        () -> TuneFile.read(narrow)).getMessage(),
                "a table of another column count is read as none");
    }

    @Test
    void theCopiesFlagIsTakenAndStatedInTheTable() throws IOException {
        // -copies packs a match beyond the ring as a copy from the column's
        // separate literal stream, which packs a small ring far smaller
        // (DTX, dtx-write). The format block's byte 3 records it, and the binder
        // reads that byte to pick the reader that reads such a table, so a
        // table packed one way and read the other, which this separates.
        byte[] dump = Files.readAllBytes(Path.of("ym/test/DBA 5.ym"));
        byte[] plain = YmToYmxr.convert(dump, List.of(), new Report()).written().file();
        byte[] copies = YmToYmxr.convert(dump, List.of("-copies"), new Report())
                .written().file();
        assertEquals(0, TuneFile.read(plain).dtx2()[Dtx.HEADER + 3] & 1,
                "the default packs no copies");
        assertEquals(1, TuneFile.read(copies).dtx2()[Dtx.HEADER + 3] & 1,
                "-copies records it in the table");
        assertTrue(copies.length < plain.length, () -> "DBA 5 packs to " + copies.length
                + " bytes with copies and " + plain.length + " without");
    }

    @Test
    void aSecondsOfSearchIsTakenAndAnythingElseIsNot() throws IOException {
        // -copiesS searches S seconds beyond the opening passes. The flag is
        // the packer's, so the tool reads the number and rejects what is not
        // one rather than packing at a default nobody asked for.
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Big - Samantha Fox Strip Poker 6.ym"));
        byte[] searched = YmToYmxr.convert(dump, List.of("-copies0"), new Report())
                .written().file();
        assertEquals(1, TuneFile.read(searched).dtx2()[Dtx.HEADER + 3] & 1,
                "-copies0 packs copies, the opening passes alone");
        assertThrows(NumberFormatException.class,
                () -> YmToYmxr.convert(dump, List.of("-copiesnow"), new Report()),
                "a search of what is not a number is rejected");
    }

    @Test
    void aSquareChangedUnderARunningTimerMovesNoPlace() throws IOException {
        // SPEC.md 6 rule 5: a row that sets the source column sets bit 6 of
        // the control column where the timer is stopped, and bit 5 unless
        // the source it starts is a square replacing a square on the same
        // target. Effect 1 of Synergy Credits starts on row 12, where
        // no source runs on its timer, and starts another source at another
        // count on row 36, where the timer has run since. The count row 36
        // writes loads when the running count reaches zero (1.9), so the
        // pitch moves and the phase stands; the row moves no place with it,
        // so its two ticks fall a whole period apart.
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Synergy Credits.ym"));
        Table table = TuneFile.read(YmToYmxr.convert(dump, List.of(), new Report())
                .written().file()).table();
        int t = Columns.EFFECT + 4;
        int t2 = Columns.EFFECT;
        assertEquals(0x80 | 10, table.column(t)[12] & 0xFF, "row 12 sets effect 1 to R10");
        assertEquals(0x80 | 3, table.column(t + 1)[12] & 0xFF, "row 12 starts source 3");
        assertEquals(0x80 | Columns.TIMER_RESET | Columns.PLACE_RESET | 5,
                table.column(t + 2)[12] & 0xFF, "row 12 starts the stopped timer");
        assertEquals(0xE7, table.column(t + 3)[12] & 0xFF, "row 12's count");
        assertEquals(0x80 | 1, table.column(t + 1)[36] & 0xFF, "row 36 starts source 1");
        assertEquals(0x80 | 5, table.column(t + 2)[36] & 0xFF,
                "row 36 starts the source without stopping the timer or moving the place");
        assertEquals(0xEB, table.column(t + 3)[36] & 0xFF, "row 36's count");
        // Effect 0's source column goes to 0 on row 1944 and row 1950 starts
        // a square on the target it last ran one on. The timer stopped, so
        // the row programs it; the rows between them moved no place, so the
        // row leaves bit 5 clear and the tick reads the row the square left
        // off at (1.9).
        assertEquals(0x80, table.column(t2 + 1)[1944] & 0xFF, "row 1944 stops effect 0");
        assertEquals(0x80 | Columns.TIMER_RESET | 2, table.column(t2 + 2)[1950] & 0xFF,
                "row 1950 programs the timer and moves no place");
        // Every start on this tune's two effects is a square, and one that
        // follows a square on the same target moves no place, whether or not
        // the effect ran between them.
        for (int i = 0; i < 2; i++) {
            byte[] target = table.column(Columns.EFFECT + 4 * i);
            byte[] source = table.column(Columns.EFFECT + 4 * i + 1);
            byte[] control = table.column(Columns.EFFECT + 4 * i + 2);
            int target0 = -1;
            int last = -2;
            int unmoved = 0;
            for (int f = 0; f < table.rows(); f++) {
                if ((target[f] & 0x80) != 0) {
                    target0 = target[f] & 0x7F;
                }
                if ((source[f] & 0xFF) > 0x80) {
                    int want = target0 == last ? 0 : Columns.PLACE_RESET;
                    assertEquals(want, control[f] & Columns.PLACE_RESET,
                            "row " + f + " starts effect " + i + " on the wrong place bit");
                    unmoved += want == 0 ? 1 : 0;
                    last = target0;
                }
            }
            assertTrue(unmoved > 100, "effect " + i + " moves no place on "
                    + unmoved + " starts");
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
                    assertEquals(1, sources.get(number).rows());
                    assertEquals(0, sources.get(number).repeat());
                    assertEquals(Sources.MARK | (slot.data() & 15), sources.get(number).columns()[0][0] & 0xFF);
                }
            }
        }
        assertTrue(number > 0, "the tune has a sync buzzer");
        Sources.Source sid = sources.get(sources.number(
                new Effects.Slot(Effects.SID, 0, 8, 9, 1, 100), report));
        assertEquals(2, sid.rows());
        assertEquals(9, sid.columns()[0][0], "the loud half, which the first tick writes");
        assertEquals(Sources.MARK, sid.columns()[0][1] & 0xFF);
        assertEquals(0, sid.repeat());
    }

    /**
     * A count of 0 is the 256 the MFP counts down, so the frames a source
     * that plays once runs for are reckoned from 256 (SPEC.md 1.9.1, 6.4;
     * YMXS, SPEC.md 6.4, which reads counted(C)). The reckoning read the
     * column as it stands, so a drum started at a count of 0 ended a frame
     * after it began.
     */
    @Test
    void theReckoningReadsACountOf0AsTheCountTheTimerCounts() {
        assertEquals(Columns.duration(1000, 5, 256, 50), Columns.duration(1000, 5, 0, 50),
                "a count of 0 runs the frames a count of 256 runs");
        assertTrue(Columns.duration(1000, 5, 0, 50) > Columns.duration(1000, 5, 1, 50),
                "a count of 0 runs longer than a count of 1");
    }
}
