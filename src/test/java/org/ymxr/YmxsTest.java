package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.ymxs.Chip;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.StartOne;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Timing;
import org.ymxs.YMXS.Tune;

/**
 * The structure in the middle of a conversion (doc/ymxs.md): every tune
 * under {@code ym/test} read into a YMXS tune, written as JSON, read back
 * and mapped onto the columns.
 *
 * <p>The claim under test is that the structure loses no part of a tune: a
 * tune mapped from the structure it was read into is the file the
 * converter writes, byte for byte, and one that passes through the JSON
 * form on the way is that file again. {@code ConformanceTest} pins
 * eleven of those files by their sha256, so the two together say the
 * conversion did not move.
 */
final class YmxsTest {

    private static List<Path> dumps() throws IOException {
        try (Stream<Path> at = Files.list(Path.of("ym/test"))) {
            return at.filter(one -> one.toString().endsWith(".ym")).sorted().toList();
        }
    }

    /** The tune file the converter writes, from the dump's bytes. */
    private static byte[] converted(Path dump) throws IOException {
        return YmToYmxr.convert(Files.readAllBytes(dump), List.of(), new Report()).written().file();
    }

    /** The same dump through the structure and its JSON form. */
    private static byte[] throughTheForm(Path dump) throws IOException {
        YmDump.Song song = YmDump.read(Files.readAllBytes(dump));
        int repeat = (int) Math.min(song.loopFrame(), song.frames());
        Tune tune = Ym.read(song, new Sources(song), repeat, new Report());
        Tune back = Text.read(Text.write(Tunes.multi(tune))).tunes().get(0);
        Schema.Made made = Schema.of(Padding.toUnit(back, YmToYmxr.UNIT, new Report()).tune());
        // the tune file records the tune's name, as ymxs-to-ymxr writes it
        return org.ymxr.Tune.write(made.columns(), made.sources(), made.rate(),
                YmToYmxr.UNIT, org.ymxr.Tune.RING, new Report(), back.title()).file();
    }

    @Test
    void everyTuneConvertsThroughTheStructureToTheSameFile() throws IOException {
        List<Path> dumps = dumps();
        assertTrue(dumps.size() >= 10, () -> "only " + dumps.size() + " dumps read");
        for (Path dump : dumps) {
            assertArrayEquals(converted(dump), throughTheForm(dump),
                    dump + " converts to another file through the structure");
        }
    }

    @Test
    void theFormReadsBackAsTheStructureItWas() throws IOException {
        Path dump = dumps().stream().filter(at -> at.toString().contains("Turrican - world"))
                .findFirst().orElseThrow();
        YmDump.Song song = YmDump.read(Files.readAllBytes(dump));
        Tune tune = Ym.read(song, new Sources(song), (int) song.loopFrame(), new Report());
        Tune back = Text.read(Text.write(Tunes.multi(tune))).tunes().get(0);
        assertEquals(Tunes.rows(tune).size(), Tunes.rows(back).size(), "the rows");
        assertEquals(Tunes.sources(tune).size(), Tunes.sources(back).size(), "the sources");
        assertEquals(tune.table().repeat(), back.table().repeat(), "the row it repeats to");
        for (int r = 0; r < Tunes.rows(tune).size(); r++) {
            assertEquals(Tunes.registers(Tunes.rows(tune).get(r)),
                    Tunes.registers(Tunes.rows(back).get(r)), "row " + r + "'s registers");
            assertEquals(Tunes.effects(Tunes.rows(tune).get(r)).toString(),
                    Tunes.effects(Tunes.rows(back).get(r)).toString(),
                    "row " + r + "'s effects");
        }
    }

    /** A structure written by hand, for what a dump does not produce. */
    private static Tune built(List<Row> rows, int repeat) {
        return new Tune("built", "", "YmxsTest", 50, Tunes.repeating(rows, repeat));
    }

    private static Row row(Map<Register, Integer> registers, Timer timer, Effect effect) {
        Map<Timer, Effect> effects = new EnumMap<>(Timer.class);
        effects.put(timer, effect);
        return new Row(registers, effects);
    }

    @Test
    void aColumnIsWrittenWhereItDiffersFromWhatThePlayerKeeps() {
        org.ymxs.YMXS.Single square = Tunes.repeating("square 12", List.of(12, 0), 0);
        List<Row> rows = new ArrayList<>();
        rows.add(row(Map.of(), Timer.A, new StartOne(Tunes.setting(Register.R8), square, new Timing(
                Chip.prescaler(4), 100, true, true))));
        // the rate does not move: neither rate column is written
        rows.add(row(Map.of(), Timer.A, new Retune(new Timing(Chip.prescaler(4), 100, false, false))));
        // the count alone
        rows.add(row(Map.of(), Timer.A, new Retune(new Timing(Chip.prescaler(4), 90, false, false))));
        // the select alone
        rows.add(row(Map.of(), Timer.A, new Retune(new Timing(Chip.prescaler(10), 90, false, false))));
        byte[][] column = Schema.of(built(rows, 0)).columns().column;
        int control = Columns.EFFECT + 2;
        int count = Columns.EFFECT + 3;
        assertEquals(0x80 | Columns.TIMER_RESET | Columns.PLACE_RESET | 1,
                column[control][0] & 0xFF, "the start writes the control column");
        assertEquals(100, column[count][0] & 0xFF, "and the count");
        assertEquals(0, column[control][1] & 0xFF, "an unmoved rate writes no control column");
        assertEquals(0, column[count][1] & 0xFF, "and no count");
        assertEquals(0, column[control][2] & 0xFF, "a count that moved writes no control column");
        assertEquals(90, column[count][2] & 0xFF, "and the count");
        assertEquals(0x80 | 2, column[control][3] & 0xFF, "a select that moved writes it");
        assertEquals(0, column[count][3] & 0xFF, "and leaves the count unwritten");
    }

    @Test
    void aStartOnATimerAtTheRateItCountsSetsNoRateColumn() {
        org.ymxs.YMXS.Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        org.ymxs.YMXS.Single soft = Tunes.repeating("square 6", List.of(6, 0), 0);
        List<Row> rows = new ArrayList<>();
        rows.add(row(Map.of(), Timer.A, new StartOne(Tunes.setting(Register.R8), loud, new Timing(
                Chip.prescaler(4), 100, true, true))));
        // a second source on the running timer, at the rate it counts
        rows.add(row(Map.of(), Timer.A, new StartOne(Tunes.setting(Register.R8), soft, new Timing(
                Chip.prescaler(4), 100, false, false))));
        // the same, with the count moved
        rows.add(row(Map.of(), Timer.A, new StartOne(Tunes.setting(Register.R8), loud, new Timing(
                Chip.prescaler(4), 90, false, false))));
        // the timer stopped, then started again at the rate it last ran
        rows.add(new Row(Map.of(), Map.of(Timer.A, Tunes.STOP)));
        rows.add(row(Map.of(), Timer.A, new StartOne(Tunes.setting(Register.R8), loud, new Timing(
                Chip.prescaler(4), 90, false, false))));
        byte[][] column = Schema.of(built(rows, 0)).columns().column;
        int source = Columns.EFFECT + 1;
        int control = Columns.EFFECT + 2;
        int count = Columns.EFFECT + 3;
        assertEquals(0, column[control][1] & 0xFF, "row 1 sets no control column");
        assertEquals(0, column[count][1] & 0xFF, "and no count");
        assertEquals(0x82, column[source][1] & 0xFF, "the source alone");
        assertEquals(0x80 | 1, column[control][2] & 0xFF, "a count that moved writes both");
        assertEquals(90, column[count][2] & 0xFF, "and the count");
        assertEquals(0x80, column[source][3] & 0xFF, "the stop");
        assertEquals(0x80 | 1, column[control][4] & 0xFF,
                "a stopped timer starts on the select the row writes (SPEC.md 1.8)");
        assertEquals(90, column[count][4] & 0xFF, "and its count");
    }

    @Test
    void aRegisterOfZeroSetsTheBitBesideIt() {
        List<Row> rows = List.of(new Row(Map.of(Register.R0, 0, Register.R11, 0), Map.of()),
                new Row(Map.of(Register.R0, 64), Map.of()));
        byte[][] column = Schema.of(built(rows, 0)).columns().column;
        assertEquals(0, column[0][0] & 0xFF, "a fine byte of 0 is the column's 0");
        assertEquals(0x40, column[1][0] & 0xFF, "and bit 6 of the column beside it (1.2)");
        assertEquals(0x40, column[13][0] & 0xFF, "the envelope period's the same (1.7)");
        assertEquals(64, column[0][1] & 0xFF, "a value is the value");
    }

    @Test
    void aStructureThisFormatCannotEncodeIsAnError() {
        org.ymxs.YMXS.Single square = Tunes.repeating("square 12", List.of(12, 0), 0);
        List<Row> big = List.of(row(Map.of(), Timer.A, new StartOne(Tunes.setting(Register.R8),
                square, new Timing( Chip.prescaler(4), Chip.MOST_COUNT + 1, true, true))));
        String said = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Schema.of(built(big, 0))).getMessage());
        assertTrue(said.contains("count of 256"), "a count of 256 is past the timer's data"
                + " register, which the count column is: " + said);
        // A source on a register that fills its byte has no marker in it:
        // its rows are whole bytes and a player counts them (3.1), which
        // the version word says (3.3.5).
        org.ymxs.YMXS.Single whole = Tunes.repeating("whole", List.of(200), 0);
        List<Row> counted = List.of(row(Map.of(), Timer.A, new StartOne(Tunes.setting(Register.R0),
                whole, new Timing(Chip.prescaler(4), 100, true, true))));
        Schema.Made made = Schema.of(built(counted, 0));
        byte[] file = org.ymxr.Tune.write(made.columns(), made.sources(), made.rate(),
                YmToYmxr.UNIT, org.ymxr.Tune.RING, new Report()).file();
        assertEquals(org.ymxr.Tune.VERSION_COUNTED, org.ymxr.Tune.getWord(file, 4),
                "a tune with a counted source is version "
                        + org.ymxr.Tune.VERSION_COUNTED);
        TuneFile read = TuneFile.read(file);
        assertEquals(200, read.sources().get(0).column(0)[0] & 0xFF,
                "the row is the value, with no marker in bit 7");
        assertTrue(read.counted().get(0),
                "bit 31 of the index entry marks the source counted");
    }

    @Test
    void everyTimerRunsTheEffectTheSpecificationAssignsIt() {
        assertEquals(Timer.A, Schema.timer(0));
        assertEquals(Timer.D, Schema.timer(1));
        assertEquals(Timer.B, Schema.timer(2));
        assertEquals(Timer.C, Schema.timer(3));
    }
}
