package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.ymxs.YMXS.Mapping;
import org.ymxs.YMXS.OneConnection;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Remap;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Timing;
import org.ymxs.YMXS.Transformation;
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
                YmToYmxr.UNIT, org.ymxr.Tune.CHOOSE, new Report(), back.title()).file();
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

    /** A start of {@code source} on {@code register} through the identity. */
    private static Start start(Register register, Single source, Timing timing,
                               boolean deferred) {
        return start(register, source, timing, deferred, Tunes.NO_PARTS);
    }

    private static Start start(Register register, Single source, Timing timing,
                               boolean deferred, Mapping mapping) {
        return new Start(new OneConnection(Tunes.setting(register), source), timing, deferred,
                mapping);
    }

    @Test
    void aColumnIsWrittenWhereItDiffersFromWhatThePlayerKeeps() {
        org.ymxs.YMXS.Single square = Tunes.repeating("square 12", List.of(12, 0), 0);
        List<Row> rows = new ArrayList<>();
        rows.add(row(Map.of(), Timer.A, start(Register.R8, square, new Timing(
                Chip.prescaler(4), 100, true, true), false)));
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
        assertEquals(0, column[control][1] & 0xFF, "an unmoved rate leaves the control column at 0");
        assertEquals(0, column[count][1] & 0xFF, "and no count");
        assertEquals(0, column[control][2] & 0xFF, "a count that moved leaves the control column at 0");
        assertEquals(90, column[count][2] & 0xFF, "and the count");
        assertEquals(0x80 | 2, column[control][3] & 0xFF, "a select that moved writes it");
        assertEquals(0, column[count][3] & 0xFF, "and leaves the count unwritten");
    }

    /** A square, then a deferred start of a fall on its timer, then a
     *  retune before the switch (SPEC.md 4.8). */
    static Tune deferred() {
        org.ymxs.YMXS.Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        org.ymxs.YMXS.Single fall = Tunes.once("fall", List.of(15, 10, 5, 0));
        Timing stopped = new Timing(Chip.prescaler(4), 100, true, true);
        Timing same = new Timing(Chip.prescaler(4), 100, false, true);
        List<Row> rows = new ArrayList<>();
        rows.add(row(Map.of(), Timer.A, start(Register.R8, loud, stopped, false)));
        rows.add(row(Map.of(), Timer.A, start(Register.R8, fall, same, true)));
        rows.add(row(Map.of(), Timer.A, new org.ymxs.YMXS.Retune(same)));
        rows.add(new Row(Map.of(), Map.of()));
        return built(rows, 0);
    }

    /**
     * Starts on another target, across row counts and across the wrap, each
     * with placeReset clear, and one on a timer that has run none. The tune
     * repeats to row 1, so the start there follows the last start on its
     * timer after the wrap, where it follows the start of row 0 before.
     */
    static Tune outsideRule3a() {
        org.ymxs.YMXS.Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        org.ymxs.YMXS.Single soft = Tunes.repeating("square 6", List.of(6, 0), 0);
        org.ymxs.YMXS.Single four = Tunes.repeating("four rows", List.of(9, 10, 11, 12), 0);
        Timing stopped = new Timing(Chip.prescaler(4), 100, true, true);
        Timing kept = new Timing(Chip.prescaler(4), 100, false, false);
        List<Row> rows = new ArrayList<>();
        Map<Timer, Effect> first = new EnumMap<>(Timer.class);
        first.put(Timer.A, start(Register.R8, loud, stopped, false));
        first.put(Timer.D, start(Register.R10, soft,
                new Timing(Chip.prescaler(4), 100, true, false), false));
        rows.add(new Row(Map.of(), first));
        rows.add(row(Map.of(), Timer.A, start(Register.R8, soft, kept, false)));
        rows.add(row(Map.of(), Timer.A, start(Register.R8, four, kept, false)));
        rows.add(row(Map.of(), Timer.A, start(Register.R9, four, kept, false)));
        rows.add(row(Map.of(), Timer.D, start(Register.R10, loud, kept, false)));
        rows.add(new Row(Map.of(), Map.of()));
        return built(rows, 1);
    }

    /**
     * Volume lookup with entry k = floor(k * most / 15) for sixteen
     * keys.
     */
    private static Transformation volume(int most) {
        List<Integer> entries = new ArrayList<>();
        for (int k = 0; k < 16; k++) {
            entries.add(k * most / 15);
        }
        return new Transformation("V" + most, entries);
    }

    /**
     * Fade: start a square through V15, remap through V10, V5 and V0,
     * then stop (YMXS, SPEC.md 3.6).
     */
    static Tune fade() {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        List<Row> rows = new ArrayList<>();
        rows.add(row(Map.of(), Timer.A, start(Register.R8, loud,
                new Timing(Chip.prescaler(4), 100, true, true), false,
                Tunes.mapping(volume(15)))));
        for (int most : new int[] {10, 5, 0}) {
            rows.add(row(Map.of(), Timer.A, new Remap(Tunes.mapping(volume(most)))));
        }
        rows.add(new Row(Map.of(), Map.of(Timer.A, Tunes.STOP)));
        return built(rows, 0);
    }

    /** A start through V15 at row 3 of five rows that repeat to row 1, the
     *  last a stop: padding to a unit of 2 adds a row before row 1. */
    static Tune mappedAfterPadding() {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        Timing stopped = new Timing(Chip.prescaler(4), 100, true, true);
        List<Row> rows = new ArrayList<>();
        rows.add(row(Map.of(), Timer.A, start(Register.R8, loud, stopped, false)));
        rows.add(new Row(Map.of(), Map.of()));
        rows.add(new Row(Map.of(), Map.of()));
        rows.add(row(Map.of(), Timer.A, start(Register.R8, loud, stopped, false,
                Tunes.mapping(volume(15)))));
        rows.add(new Row(Map.of(), Map.of(Timer.A, Tunes.STOP)));
        return built(rows, 1);
    }

    /** A square, then a remap of its effect to the identity. */
    static Tune remapped() {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        List<Row> rows = new ArrayList<>();
        rows.add(row(Map.of(), Timer.A, start(Register.R8, loud,
                new Timing(Chip.prescaler(4), 100, true, true), false)));
        rows.add(row(Map.of(), Timer.A, new Remap(Tunes.NO_PARTS)));
        rows.add(new Row(Map.of(), Map.of()));
        return built(rows, 0);
    }

    /** A sweep of the envelope period on timer A, two values a row, and a
     *  voice on timer B, three. */
    static Tune several() {
        org.ymxs.YMXS.Pair sweep = new org.ymxs.YMXS.Pair("period",
                Tunes.repeating(List.of(new org.ymxs.YMXS.Two(0, 200),
                        new org.ymxs.YMXS.Two(255, 255)), 0));
        org.ymxs.YMXS.Triple voice = new org.ymxs.YMXS.Triple("a voice",
                Tunes.repeating(List.of(new org.ymxs.YMXS.Three(64, 2, 15),
                        new org.ymxs.YMXS.Three(128, 1, 12)), 0));
        Timing stopped = new Timing(Chip.prescaler(4), 100, true, true);
        Map<Timer, Effect> both = new EnumMap<>(Timer.class);
        both.put(Timer.A, new Start(new org.ymxs.YMXS.TwoConnection(
                new org.ymxs.YMXS.SetEnvelope(), sweep), stopped, false, Tunes.NO_PARTS));
        both.put(Timer.B, new Start(new org.ymxs.YMXS.ThreeConnection(
                new org.ymxs.YMXS.SetVoice(org.ymxs.YMXS.Voice.A), voice), stopped, false,
                Tunes.NO_PARTS));
        return built(List.of(new Row(Map.of(), both), new Row(Map.of(), Map.of())), 0);
    }

    /** The line the writer reports for {@code tune}. */
    private static String reported(Tune tune) {
        return String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Schema.of(tune)).getMessage());
    }

    /** The tune file of {@code tune}, padded and named as
     *  {@code ymxs-to-ymxr} pads and names it. */
    private static byte[] file(Tune tune) {
        Schema.Made made = Schema.of(Padding.toUnit(tune, YmToYmxr.UNIT, new Report()));
        return org.ymxr.Tune.write(made.columns(), made.sources(), made.rate(),
                YmToYmxr.UNIT, BuiltTunes.RING, new Report(), tune.title()).file();
    }

    /** The mapping column of effect {@code i}. */
    private static byte[] mapping(Schema.Made made, int i) {
        return made.columns().column[Columns.MAPPING + i];
    }

    /**
     * A named lookup with size entries, entry k = floor(k / by). It
     * remains distinct from identity.
     */
    private static Transformation divided(String name, int size, int by) {
        List<Integer> entries = new ArrayList<>();
        for (int k = 0; k < size; k++) {
            entries.add(k / by);
        }
        return new Transformation(name, entries);
    }

    /** A row of one operation on each of two timers. */
    private static Row row(Timer one, Effect first, Timer two, Effect second) {
        Map<Timer, Effect> effects = new EnumMap<>(Timer.class);
        effects.put(one, first);
        effects.put(two, second);
        return new Row(Map.of(), effects);
    }

    private static final Timing STOPPED = new Timing(Chip.prescaler(4), 100, true, true);

    /**
     * Identity remaps and remaps outside the tune produce no mapping
     * writes. The version 9 output matches the tune without those
     * operations (SPEC.md 3.3.5; rule 2(g)).
     */
    @Test
    void aTuneThroughTheIdentityWithRemapsWritesTheFileOfVersionNine() {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        Start start = start(Register.R8, loud, STOPPED, false);
        Tune with = built(List.of(row(Map.of(), Timer.A, start),
                row(Timer.A, new Remap(Tunes.NO_PARTS), Timer.D,
                        new Remap(Tunes.mapping(Tunes.IDENTITY))),
                row(Map.of(), Timer.A, new Remap(Tunes.mapping(Tunes.IDENTITY))),
                row(Map.of(), Timer.A, start(Register.R8, loud, STOPPED, true)),
                row(Map.of(), Timer.A, new Remap(Tunes.NO_PARTS))), 1);
        Tune without = built(List.of(row(Map.of(), Timer.A, start), Tunes.EMPTY, Tunes.EMPTY,
                row(Map.of(), Timer.A, start(Register.R8, loud, STOPPED, true)),
                Tunes.EMPTY), 1);
        byte[] file = file(with);
        assertEquals(org.ymxr.Tune.UNMAPPED, org.ymxr.Tune.getWord(file, 4));
        assertEquals(Columns.C, Schema.of(with).columns().count(), "thirty columns");
        assertArrayEquals(file(without), file, "the file of the tune without the remaps");
    }

    /**
     * A lookup used only by a remap outside the tune does not require
     * version 10 (SPEC.md 6, rule 2(a)).
     */
    @Test
    void aTransformationOnARemapOutsideTheTuneWritesVersionNine() {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        Tune tune = built(List.of(row(Timer.A, start(Register.R8, loud, STOPPED, false),
                Timer.D, new Remap(Tunes.mapping(volume(5)))), Tunes.EMPTY), 0);
        Schema.Made made = Schema.of(tune);
        assertEquals(Columns.C, made.columns().count());
        assertEquals(List.of(), made.columns().transformations);
        assertEquals(1, made.columns().effects, "effect 0 alone, and bit 5 clear");
        assertEquals(org.ymxr.Tune.UNMAPPED, org.ymxr.Tune.getWord(file(tune), 4));
    }

    /**
     * The fade encodes as version 10: four mapping columns, effects bit
     * 5, transformation-index pointer at 16, source index at 20 and
     * lookup tables in YMXS first-use order (SPEC.md 1.10, 3.3, 3.4).
     */
    @Test
    void theFadeWritesVersionTen() {
        Schema.Made made = Schema.of(fade());
        assertEquals(Columns.MAPPED, made.columns().count());
        assertArrayEquals(new byte[] {(byte) 0x81, (byte) 0x82, (byte) 0x83, (byte) 0x84, 0},
                mapping(made, 0), "the start through V15, then a remap a row");
        for (int i = 1; i < 4; i++) {
            assertArrayEquals(new byte[5], mapping(made, i),
                    "effect " + i + " runs through the identity");
        }
        // a remap sets the mapping column with the source column unset
        for (int r = 1; r < 4; r++) {
            assertEquals(0, made.columns().column[Columns.EFFECT + 1][r], "row " + r);
        }
        byte[] file = file(fade());
        assertEquals(org.ymxr.Tune.VERSION, org.ymxr.Tune.getWord(file, 4));
        assertEquals(Columns.MAPS | 1, file[org.ymxr.Tune.EFFECTS_AT] & 0xFF);
        int index = org.ymxr.Tune.getLong(file, org.ymxr.Tune.TRANSFORMATIONS_AT);
        assertEquals(org.ymxr.Tune.align(org.ymxr.Tune.indexAt(org.ymxr.Tune.VERSION) + 4
                + "built".length() + 1), index, "the index after the source index at 20 and"
                + " the name");
        assertEquals(org.ymxr.Tune.indexAt(org.ymxr.Tune.VERSION) + 4,
                org.ymxr.Tune.getWord(file, org.ymxr.Tune.NAME_AT), "one source at 20");
        TuneFile read = TuneFile.read(file);
        assertEquals(Columns.MAPPED, read.table().columns());
        assertEquals(4, read.transformations().size());
        int n = 0;
        for (int most : new int[] {15, 10, 5, 0}) {
            byte[] entries = new byte[16];
            for (int k = 0; k < 16; k++) {
                entries[k] = (byte) (k * most / 15);
            }
            assertArrayEquals(entries, read.transformations().get(n++), "V" + most);
        }
    }

    /**
     * Each fade remap's record reports the number of its mapping's last
     * part, matching YMXS (SPEC.md 7.4).
     */
    @Test
    void theRecordOfARemapRowIsTheNumberOfItsLastPart() {
        Tune fade = fade();
        List<Transformation> list = Tunes.transformations(fade);
        String[] lines = new String(Trace.record(file(fade), -1),
                java.nio.charset.StandardCharsets.US_ASCII).split("\n");
        java.util.regex.Pattern kept = java.util.regex.Pattern.compile(
                "\"0\":\\{[^}]*\"mapping\":(\\d+)\\}");
        int read = 0;
        for (int r = 0; r < Tunes.rows(fade).size(); r++) {
            if (!(Tunes.rows(fade).get(r).effects().get(Timer.A) instanceof Remap remap)) {
                continue;
            }
            java.util.regex.Matcher m = kept.matcher(lines[r + 1]);
            assertTrue(m.find(), lines[r + 1]);
            List<org.ymxs.YMXS.Part> parts = remap.mapping().parts();
            assertEquals(Tunes.number(list, parts.get(parts.size() - 1)),
                    Integer.parseInt(m.group(1)), "row " + r);
            read++;
        }
        assertEquals(3, read, "the three remaps of the fade");
    }

    /**
     * Starts reuse the kept mapping by leaving its column unset; a
     * changed mapping requires a write (SPEC.md 6, rule 2(g)).
     */
    @Test
    void aStartThatKeepsTheMappingLeavesTheColumnUnset() {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        Single soft = Tunes.repeating("square 6", List.of(6, 0), 0);
        Mapping v10 = Tunes.mapping(volume(10));
        Timing kept = new Timing(Chip.prescaler(4), 100, false, false);
        Tune tune = built(List.of(
                row(Map.of(), Timer.A, start(Register.R8, loud, STOPPED, false, v10)),
                row(Map.of(), Timer.A, start(Register.R8, soft, kept, false, v10)),
                new Row(Map.of(), Map.of(Timer.A, Tunes.STOP)),
                row(Map.of(), Timer.A, start(Register.R8, loud, STOPPED, false, v10)),
                row(Map.of(), Timer.A, start(Register.R8, soft, kept, false)),
                Tunes.EMPTY), 5);
        assertArrayEquals(new byte[] {(byte) 0x81, 0, 0, 0, (byte) 0x80, 0},
                mapping(Schema.of(tune), 0));
    }

    /**
     * A remap writes its mapping with the source column unset, unless
     * the running mapping already matches. On an idle timer of the
     * tune, the written mapping is reused by the next start (SPEC.md 6,
     * rule 2(g)).
     */
    @Test
    void aRemapSetsTheMappingColumnWithTheSourceColumnUnset() {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        Transformation v10 = volume(10);
        Tune tune = built(List.of(
                row(Map.of(), Timer.A, new Remap(Tunes.mapping(v10))),
                row(Map.of(), Timer.A, start(Register.R8, loud, STOPPED, false,
                        Tunes.mapping(v10))),
                row(Map.of(), Timer.A, new Remap(Tunes.mapping(v10))),
                row(Map.of(), Timer.A, new Remap(Tunes.mapping(volume(5)))),
                row(Map.of(), Timer.A, new Remap(Tunes.NO_PARTS)),
                Tunes.EMPTY), 5);
        Schema.Made made = Schema.of(tune);
        assertArrayEquals(new byte[] {(byte) 0x81, 0, 0, (byte) 0x82, (byte) 0x80, 0},
                mapping(made, 0), "the idle timer's remap, kept by the start; a remap"
                + " through the running mapping left out");
        int source = Columns.EFFECT + 1;
        assertEquals(0, made.columns().column[source][0], "the remap on the idle timer");
        assertEquals((byte) 0x81, made.columns().column[source][1], "the start");
        assertEquals(0, made.columns().column[source][3], "a remap");
    }

    /**
     * Deferring a different mapping makes the running mapping
     * uncertain. A remap equal to kept must still be written.
     * Installation preserves kept, so a later start can reuse the remap
     * value (SPEC.md 1.10.3).
     */
    @Test
    void aRemapEqualToTheKeptMappingAfterADeferredStartIsWritten() {
        Single loop = Tunes.repeating("loop", List.of(15, 12, 9, 6), 0);
        Single fall = Tunes.once("fall", List.of(15, 10, 5, 0));
        Transformation one = volume(10);
        Transformation two = volume(5);
        Timing defer = new Timing(Chip.prescaler(4), 100, false, true);
        Timing kept = new Timing(Chip.prescaler(4), 100, false, false);
        Tune tune = built(List.of(
                row(Map.of(), Timer.A, start(Register.R8, loop, STOPPED, false,
                        Tunes.mapping(one))),
                row(Map.of(), Timer.A, start(Register.R8, fall, defer, true, Tunes.mapping(two))),
                row(Map.of(), Timer.A, new Remap(Tunes.mapping(two))),
                row(Map.of(), Timer.A, start(Register.R8, loop, defer, true, Tunes.mapping(one))),
                row(Map.of(), Timer.A, new Remap(Tunes.mapping(two))),
                row(Map.of(), Timer.A, start(Register.R8, loop, kept, false,
                        Tunes.mapping(two))),
                row(Map.of(), Timer.A, new Remap(Tunes.mapping(two))),
                Tunes.EMPTY), 7);
        assertArrayEquals(new byte[] {(byte) 0x81, (byte) 0x82, (byte) 0x82, (byte) 0x81,
                (byte) 0x82, 0, 0, 0}, mapping(Schema.of(tune), 0));
    }

    /**
     * Mappings survive the wrap. For an effect using lookups, the first
     * start or remap after the repeat row restores its required mapping
     * (SPEC.md 6, rule 2(g)).
     */
    @Test
    void theRepeatRowWritesTheMappingColumn() {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        Mapping v10 = Tunes.mapping(volume(10));
        Tune tune = built(List.of(
                row(Map.of(), Timer.A, start(Register.R8, loud, STOPPED, false, v10)),
                row(Map.of(), Timer.A, start(Register.R8, loud, STOPPED, false, v10)),
                row(Map.of(), Timer.A, new Remap(Tunes.mapping(volume(5)))),
                Tunes.EMPTY), 1);
        assertArrayEquals(new byte[] {(byte) 0x81, (byte) 0x81, (byte) 0x82, 0},
                mapping(Schema.of(tune), 0));
    }

    /**
     * Repeating version 10 fixture: effect 0 changes volume tables,
     * while effect 1 uses identity before and after the repeat row.
     */
    static Tune identityBeside() {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        Single soft = Tunes.repeating("square 6", List.of(6, 0), 0);
        Timing kept = new Timing(Chip.prescaler(4), 100, false, false);
        return built(List.of(
                row(Timer.A, start(Register.R8, loud, STOPPED, false, Tunes.mapping(volume(10))),
                        Timer.D, start(Register.R9, soft, STOPPED, false)),
                row(Timer.A, new Remap(Tunes.mapping(volume(5))), Timer.D,
                        new Remap(Tunes.NO_PARTS)),
                row(Timer.A, start(Register.R8, loud, STOPPED, false, Tunes.mapping(volume(10))),
                        Timer.D, start(Register.R9, loud, kept, false)),
                row(Map.of(), Timer.D, new Remap(Tunes.mapping(Tunes.IDENTITY))),
                row(Map.of(), Timer.D, start(Register.R9, soft, kept, true)),
                row(Map.of(), Timer.D, new Remap(Tunes.NO_PARTS)),
                row(Map.of(), Timer.D, start(Register.R9, soft, STOPPED, false,
                        Tunes.mapping(Tunes.IDENTITY)))), 2);
    }

    /**
     * An identity-only effect needs no mapping writes, even beside an
     * effect using lookups in a repeating version 10 tune. Its mapping
     * column packs as constant 0 (SPEC.md 6, rule 2(g)).
     */
    @Test
    void anEffectThroughTheIdentityKeepsAConstantColumn() {
        Tune tune = identityBeside();
        Schema.Made made = Schema.of(tune);
        assertEquals(Columns.MAPPED, made.columns().count(), "effect 0 maps, version 10");
        assertArrayEquals(new byte[7], mapping(made, 1), "effect 1's column is 0");
        assertEquals((byte) 0x81, mapping(made, 0)[2], "effect 0's start at the repeat row");
        byte[] file = file(tune);
        org.dtx.Table table = TuneFile.read(file).table();
        byte[] column = table.column(Columns.MAPPING + 1);
        assertArrayEquals(new byte[column.length], column, "read back as 0 in every row");
    }

    /** A square started, then a remap a row through each of {@code count}
     *  transformations. */
    static Tune remappedThrough(int count) {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        List<Row> rows = new ArrayList<>();
        rows.add(row(Map.of(), Timer.A, start(Register.R8, loud, STOPPED, false)));
        for (int n = 0; n < count; n++) {
            rows.add(row(Map.of(), Timer.A, new Remap(Tunes.mapping(divided("t" + n, 16, 1)))));
        }
        return built(rows, 0);
    }

    /** More than 127 transformations: the mapping column numbers 127
     *  (SPEC.md 6.5). */
    @Test
    void moreTransformationsThanTheMappingColumnNumbersIsAnError() {
        assertEquals("the tune maps through 128 transformations, and a mapping column"
                + " numbers 127", reported(remappedThrough(128)));
        assertEquals(127, Schema.of(remappedThrough(127)).columns().transformations.size());
    }

    /**
     * Starts a square, remaps Timer D outside the tune through count
     * transformations, then optionally remaps the square through one
     * more.
     */
    static Tune outsideThrough(int count, boolean last) {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        List<Row> rows = new ArrayList<>();
        rows.add(row(Map.of(), Timer.A, start(Register.R8, loud, STOPPED, false)));
        for (int n = 0; n < count; n++) {
            rows.add(row(Map.of(), Timer.D, new Remap(Tunes.mapping(divided("t" + n, 16, 1)))));
        }
        if (last) {
            rows.add(row(Map.of(), Timer.A, new Remap(Tunes.mapping(divided("last", 16, 1)))));
        }
        return built(rows, 0);
    }

    /**
     * Remaps outside the tune are omitted and do not require version
     * 10. If another encoded operation uses a lookup, the full YMXS
     * list must fit the transformation-count limit (SPEC.md 3.3.5, 6.5;
     * rule 2(a)).
     */
    @Test
    void transformationsOutsideTheTuneAreLeftOut() {
        Schema.Made made = Schema.of(outsideThrough(128, false));
        assertEquals(Columns.C, made.columns().count());
        assertEquals(List.of(), made.columns().transformations);
        assertEquals(org.ymxr.Tune.UNMAPPED,
                org.ymxr.Tune.getWord(file(outsideThrough(128, false)), 4));
        List<Row> without = new ArrayList<>(Tunes.rows(outsideThrough(0, false)));
        for (int n = 0; n < 128; n++) {
            without.add(Tunes.EMPTY);
        }
        assertArrayEquals(file(built(without, 0)), file(outsideThrough(128, false)),
                "the file of the tune without the remaps");
        assertEquals("the tune maps through 128 transformations, and a mapping column"
                + " numbers 127", reported(outsideThrough(127, true)));
    }

    /** The chord of the YMXS kit's transformations.json on `setVoiceB`. */
    private static org.ymxs.YMXS.ThreeConnection chord() {
        org.ymxs.YMXS.Triple chord = new org.ymxs.YMXS.Triple("chord", Tunes.repeating(
                List.of(new org.ymxs.YMXS.Three(28, 1, 15),
                        new org.ymxs.YMXS.Three(214, 0, 12)), 0));
        return new org.ymxs.YMXS.ThreeConnection(
                new org.ymxs.YMXS.SetVoice(org.ymxs.YMXS.Voice.B), chord);
    }

    /**
     * YMXS transformation fixture: square volume on Timer A, chord
     * volume on Timer B, then an unsupported remap of all chord values.
     * The repeat row stops both timers.
     */
    static Tune chordRemapped() {
        Single loud = Tunes.repeating("square 15", List.of(15, 0), 0);
        Mapping all = Tunes.mapping(divided("half", 256, 2), divided("half", 16, 2),
                divided("ramp", 16, 1));
        return built(List.of(
                row(Map.of(), Timer.A, start(Register.R8, loud, STOPPED, false,
                        Tunes.mapping(volume(15)))),
                row(Map.of(), Timer.B, new Start(chord(), STOPPED, false,
                        Tunes.mapping(Tunes.IDENTITY, Tunes.IDENTITY, volume(10)))),
                row(Map.of(), Timer.B, new Remap(all)),
                row(Timer.A, Tunes.STOP, Timer.B, Tunes.STOP)), 3);
    }

    /**
     * Reject lookups before the target's last value for starts and
     * remaps, reporting the original structure row. The YMXS fixture's
     * three-part setVoiceB remap exceeds YMXR's restriction (SPEC.md
     * 1.10.1, 6.5).
     */
    @Test
    void aMappingOfAValueOtherThanTheLastIsAnError() {
        assertEquals("row 2: the mapping maps value 0 of setVoiceB, and a mapping column maps"
                + " its value 2 alone", reported(chordRemapped()));
        Tune started = built(List.of(row(Map.of(), Timer.B, new Start(chord(), STOPPED, false,
                Tunes.mapping(Tunes.IDENTITY, divided("half", 16, 2), volume(10))))), 0);
        assertEquals("row 0: the mapping maps value 1 of setVoiceB, and a mapping column maps"
                + " its value 2 alone", reported(started));
        // the volume alone is mapped
        Tune fine = built(List.of(row(Map.of(), Timer.B, new Start(chord(), STOPPED, false,
                Tunes.mapping(Tunes.IDENTITY, Tunes.IDENTITY, volume(10))))), 0);
        assertArrayEquals(new byte[] {(byte) 0x81}, mapping(Schema.of(fine), 2));
    }

    /** The row of a line is the row of the structure, numbered before the
     *  padding (SPEC.md 6.5). */
    @Test
    void theRowOfAnErrorIsTheRowOfTheStructure() {
        Padding.Padded padded = Padding.toUnit(wrongAfterPadding(), 2, new Report());
        assertTrue(Tunes.rows(padded.tune()).size() > 5, "the tune is padded to the unit");
        assertEquals("row 3: the mapping maps value 0 of setNoiseA, and a mapping column maps"
                + " its value 1 alone", String.valueOf(assertThrows(
                IllegalArgumentException.class, () -> Schema.of(padded)).getMessage()));
    }

    /** {@link #mappedAfterPadding} with the start at row 3 on `setNoiseA`
     *  through a transformation of the noise period, its first value. */
    static Tune wrongAfterPadding() {
        org.ymxs.YMXS.Pair hiss = new org.ymxs.YMXS.Pair("hiss", Tunes.repeating(
                List.of(new org.ymxs.YMXS.Two(31, 12), new org.ymxs.YMXS.Two(1, 0)), 0));
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        List<Row> rows = new ArrayList<>();
        rows.add(row(Map.of(), Timer.A, start(Register.R8, loud, STOPPED, false)));
        rows.add(Tunes.EMPTY);
        rows.add(Tunes.EMPTY);
        rows.add(row(Map.of(), Timer.A, new Start(new org.ymxs.YMXS.TwoConnection(
                new org.ymxs.YMXS.SetNoise(org.ymxs.YMXS.Voice.A), hiss), STOPPED, false,
                Tunes.mapping(divided("half", 32, 2), Tunes.IDENTITY))));
        rows.add(new Row(Map.of(), Map.of(Timer.A, Tunes.STOP)));
        return built(rows, 1);
    }

    /**
     * Byte-key sample on setR10 through a 256-entry table, entry k =
     * floor(k / 16), replacing the YMXS fixture's table.
     */
    static Tune sampled() {
        Single sample = Tunes.repeating("sample", List.of(128, 200, 255, 200, 128, 56, 0, 56),
                0);
        return built(List.of(row(Map.of(), Timer.C, start(Register.R10, sample,
                new Timing(Chip.prescaler(4), 192, true, true), false,
                Tunes.mapping(divided("8 bits to 4", 256, 16))))), 0);
    }

    /** A source on `setR7` through a transformation, with a key of 64. */
    static Tune wideMixer() {
        Single wide = Tunes.repeating("wide", List.of(63, 64), 0);
        return built(List.of(row(Map.of(), Timer.A, start(Register.R7, wide, STOPPED, false,
                Tunes.mapping(divided("wide", 128, 2))))), 0);
    }

    /**
     * High lookup keys on one-column setR10 require counted encoding
     * with index bit 31 set. Identity values there must fit the
     * register. Multi-column setToneA retains a marker, so its lookup
     * key must fit below bit 7 (SPEC.md 3.1.6, 6.5).
     */
    @Test
    void aKeyOf128OrMoreOnSetR10ThroughATransformationIsCounted() {
        Single sample = Tunes.repeating("sample", List.of(128, 200, 255, 200, 128, 56, 0, 56),
                0);
        Timing timing = new Timing(Chip.prescaler(4), 192, true, true);
        Mapping four = Tunes.mapping(divided("8 bits to 4", 256, 16));
        TuneFile read = TuneFile.read(file(sampled()));
        assertEquals(org.ymxr.Tune.VERSION, read.version());
        assertTrue(read.counted().get(0), "bit 31 of the index entry");
        assertArrayEquals(new byte[] {(byte) 128, (byte) 200, (byte) 255, (byte) 200,
                (byte) 128, 56, 0, 56}, read.sources().get(0).column(0), "the keys, no marker");
        // keys below 128 keep the marker
        Single low = Tunes.repeating("low", List.of(100, 50), 0);
        TuneFile marked = TuneFile.read(file(built(List.of(row(Map.of(), Timer.C,
                start(Register.R10, low, timing, false, four))), 0)));
        assertEquals(List.of(false), marked.counted());
        assertEquals((byte) (0x80 | 50), marked.sources().get(0).column(0)[1], "the marker");
        // a start of the source through the identity: an error of the
        // structure, which YMXS's check reports before the writer reads it
        Tune both = built(List.of(row(Map.of(), Timer.C, start(Register.R10, sample, timing,
                false, four)), row(Map.of(), Timer.A, start(Register.R10, sample, timing,
                false))), 0);
        assertFalse(org.ymxs.Check.of(both).isEmpty(), "the value 128 through the identity");
        org.ymxs.YMXS.Pair tone = new org.ymxs.YMXS.Pair("tone", Tunes.repeating(
                List.of(new org.ymxs.YMXS.Two(0, 200), new org.ymxs.YMXS.Two(0, 1)), 0));
        Tune toned = built(List.of(row(Map.of(), Timer.A, new Start(
                new org.ymxs.YMXS.TwoConnection(new org.ymxs.YMXS.SetTone(
                        org.ymxs.YMXS.Voice.A), tone), timing, false,
                Tunes.mapping(Tunes.IDENTITY, divided("high", 256, 16))))), 0);
        assertEquals("the source tone has the value 200 in row 0 of column 1, and bit 7 of"
                + " the marker's column is the marker", reported(toned));
    }

    /**
     * setR7 key k is encoded as k + 192 to preserve both port bits.
     * Reject keys above 63 (SPEC.md 6, rule 2(f)).
     */
    /**
     * Shares a source between setR7 on Timer A and setR0 on Timer B in
     * either order. The mixer start optionally uses a lookup.
     */
    static Tune mixerAndTone(boolean mixerFirst, boolean mapped) {
        Single gate = Tunes.repeating("gate", List.of(56, 57), 0);
        Start mixer = start(Register.R7, gate, STOPPED, false,
                mapped ? Tunes.mapping(divided("gated", 64, 1)) : Tunes.NO_PARTS);
        Start tone = start(Register.R0, gate, STOPPED, false);
        return built(List.of(row(Map.of(), Timer.A, mixerFirst ? mixer : tone),
                row(Map.of(), Timer.B, mixerFirst ? tone : mixer)), 0);
    }

    /**
     * Reject sharing a setR7 source with setR0: mandatory port bits
     * would become data bits on R0. Identity and lookup mappings have
     * the same restriction (SPEC.md 6, rule 2(f)).
     */
    @Test
    void aSourceOnTheMixerAndOnAnotherTargetIsAnError() {
        String line = "the source gate runs on setR7 and on setR0, and every row of a source"
                + " on setR7 has bits 7 and 6 set (SPEC.md rule 2(f))";
        for (boolean mixerFirst : new boolean[] {true, false}) {
            for (boolean mapped : new boolean[] {true, false}) {
                Tune tune = mixerAndTone(mixerFirst, mapped);
                assertEquals(List.of(), org.ymxs.Check.of(tune), "a structure YMXS passes");
                assertEquals(line, reported(tune), "setR7 first " + mixerFirst
                        + ", through a transformation " + mapped);
            }
        }
    }

    @Test
    void aSourceOnTheMixerThroughATransformationKeepsThePortDirections() {
        Single gate = Tunes.repeating("gate", List.of(56, 57), 0);
        Mapping gated = Tunes.mapping(divided("gated", 64, 1));
        TuneFile read = TuneFile.read(file(built(List.of(row(Map.of(), Timer.A,
                start(Register.R7, gate, STOPPED, false, gated))), 0)));
        assertEquals(org.ymxr.Tune.VERSION, read.version());
        assertTrue(read.counted().get(0), "a source on setR7 is counted");
        assertArrayEquals(new byte[] {(byte) (56 + 192), (byte) (57 + 192)},
                read.sources().get(0).column(0), "key k is k + 192");
        assertEquals("the source wide has the key 64 in row 1 on setR7, and a key on setR7 is"
                + " 0 to 63", reported(wideMixer()));
    }

    /**
     * Explicit identity parts and an empty mapping produce identical
     * start columns (YMXS, SPEC.md 3.6.3).
     */
    @Test
    void aStartThroughTheIdentityInEveryPartWritesTheSameColumns() {
        Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        Timing timing = new Timing(Chip.prescaler(4), 100, true, true);
        Tune none = built(List.of(row(Map.of(), Timer.A,
                start(Register.R8, loud, timing, false))), 0);
        Tune every = built(List.of(row(Map.of(), Timer.A,
                start(Register.R8, loud, timing, false, Tunes.mapping(Tunes.IDENTITY)))), 0);
        assertArrayEquals(Schema.of(none).columns().column, Schema.of(every).columns().column);
    }

    /**
     * Bit 5 of a start is the structure's placeReset (SPEC.md 1.8.4, rule
     * 3): a start with it clear leaves the place at its number modulo R of
     * the source it starts, on another target and across row counts, so the
     * writer sets the bit exactly where the structure sets it, and
     * YMXS's check reports the start on a timer that has run none alone.
     */
    @Test
    void aStartWritesBit5AsItsPlaceReset() {
        Tune tune = outsideRule3a();
        byte[][] column = Schema.of(tune).columns().column;
        int a = Columns.EFFECT + 2;
        int d = Columns.EFFECT + 4 + 2;
        assertEquals(Columns.PLACE_RESET, column[a][0] & Columns.PLACE_RESET,
                "row 0 sets placeReset on Timer A");
        assertEquals(0, column[d][0] & Columns.PLACE_RESET,
                "row 0 clears it on Timer D, which has run none");
        for (int[] at : new int[][] {{a, 1}, {a, 2}, {a, 3}, {d, 4}}) {
            assertEquals(0, column[at[0]][at[1]] & Columns.PLACE_RESET,
                    "row " + at[1] + " leaves the place at its number modulo R");
        }
        List<String> warned = org.ymxs.Check.writing(tune).stream()
                .filter(line -> line.contains("without the place's reset")).toList();
        assertEquals(List.of("row 0:"), warned.stream()
                .map(line -> line.substring(0, line.indexOf(':') + 1)).toList(),
                warned.toString());
    }

    /**
     * A deferred start sets the control column with bit 3 (SPEC.md 4.8),
     * the effects used have bit 4 set, and the next row that sets a rate
     * of the effect sets both columns, since the timer counts at the rate
     * of the effect before the start until the tick that performs it.
     */
    @Test
    void aDeferredStartSetsBit3AndTheEffectsUsedBit4() {
        Schema.Made made = Schema.of(deferred());
        byte[][] column = made.columns().column;
        int control = Columns.EFFECT + 2;
        int count = Columns.EFFECT + 3;
        assertEquals(0, column[control][0] & Columns.DEFERRED, "row 0 starts at once");
        assertEquals(0x80 | Columns.DEFERRED, column[control][1] & (0x80 | Columns.DEFERRED),
                "row 1 sets the control column with bit 3");
        assertEquals(0x80, column[control][2] & 0x80, "row 2 sets the select of a timer"
                + " whose rate the switch writes");
        assertEquals(100, column[count][2] & 0xFF, "and the count");
        assertEquals(Columns.DEFERS | 1, made.columns().effects,
                "effect 0 runs, and a row defers a start");
    }

    @Test
    void aStartOnATimerAtTheRateItCountsSetsNoRateColumn() {
        org.ymxs.YMXS.Single loud = Tunes.repeating("square 12", List.of(12, 0), 0);
        org.ymxs.YMXS.Single soft = Tunes.repeating("square 6", List.of(6, 0), 0);
        List<Row> rows = new ArrayList<>();
        rows.add(row(Map.of(), Timer.A, start(Register.R8, loud, new Timing(
                Chip.prescaler(4), 100, true, true), false)));
        // a second source on the running timer, at the rate it counts
        rows.add(row(Map.of(), Timer.A, start(Register.R8, soft, new Timing(
                Chip.prescaler(4), 100, false, false), false)));
        // the same, with the count moved
        rows.add(row(Map.of(), Timer.A, start(Register.R8, loud, new Timing(
                Chip.prescaler(4), 90, false, false), false)));
        // the timer stopped, then started again at the rate it last ran
        rows.add(new Row(Map.of(), Map.of(Timer.A, Tunes.STOP)));
        rows.add(row(Map.of(), Timer.A, start(Register.R8, loud, new Timing(
                Chip.prescaler(4), 90, false, false), false)));
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

    private static final Single LOUD = Tunes.repeating("square 12", List.of(12, 0), 0);
    private static final Single SOFT = Tunes.repeating("square 6", List.of(6, 0), 0);

    /** A rate of the divisor {@code by} and {@code count}, both resets
     *  clear. */
    private static Timing rate(int by, int count) {
        return new Timing(Chip.prescaler(by), count, false, false);
    }

    /** Three rows that repeat to row 1: a start at the divisor 4 and count
     *  100, a retune to the same rate, then a retune to {@code by} and
     *  {@code count}, the rate after the last row. */
    private static Tune retunedAtTheRepeatRow(int by, int count) {
        return built(List.of(
                row(Map.of(), Timer.A, start(Register.R8, LOUD, STOPPED, false)),
                row(Map.of(), Timer.A, new Retune(rate(4, 100))),
                row(Map.of(), Timer.A, new Retune(rate(by, count)))), 1);
    }

    /** The tune of the defect: the retune at the repeat row is at the
     *  count the first pass has there, 100, and the last row retunes to
     *  90, so the second pass reaches the repeat row at 90. */
    static Tune repeatCount() {
        return retunedAtTheRepeatRow(4, 90);
    }

    /** The same with the select: the last row retunes to the divisor 10
     *  at count 100. */
    static Tune repeatSelect() {
        return retunedAtTheRepeatRow(10, 100);
    }

    /** A start at the repeat row at the rate the timer counts at in the
     *  first pass, both resets clear, and a stop on the last row, so the
     *  timer is stopped where the second pass reaches the repeat row. */
    static Tune repeatCounting() {
        return built(List.of(
                row(Map.of(), Timer.A, start(Register.R8, LOUD, STOPPED, false)),
                row(Map.of(), Timer.A, start(Register.R8, SOFT, rate(4, 100), false)),
                new Row(Map.of(), Map.of(Timer.A, Tunes.STOP))), 1);
    }

    /** The same start at the repeat row, and on the last row a deferred
     *  start at count 90, pending across the wrap behind a source of
     *  eight rows: until the tick that performs it the rate is open. */
    static Tune repeatPending() {
        Single eight = Tunes.repeating("eight", List.of(9, 8, 7, 6, 5, 4, 3, 2), 0);
        return built(List.of(
                row(Map.of(), Timer.A, start(Register.R8, LOUD, STOPPED, false)),
                row(Map.of(), Timer.A, start(Register.R8, eight, rate(4, 100), false)),
                row(Map.of(), Timer.A, start(Register.R8, SOFT, rate(4, 90), true))), 1);
    }

    /** A retune at the repeat row at count 100, a retune to 90, and on the
     *  last row a deferred start at 100: the record keeps 100 across the
     *  wrap, and until the tick that performs the start the timer counts
     *  at 90. */
    static Tune repeatDeferred() {
        return built(List.of(
                row(Map.of(), Timer.A, start(Register.R8, LOUD, STOPPED, false)),
                row(Map.of(), Timer.A, new Retune(rate(4, 100))),
                row(Map.of(), Timer.A, new Retune(rate(4, 90))),
                row(Map.of(), Timer.A, start(Register.R8, SOFT, rate(4, 100), true))), 1);
    }

    /** A retune and a start at the repeat row at the rate the first pass
     *  has there, both resets clear, and a last row that leaves both
     *  timers at that rate, a source that repeats on each: the second pass
     *  reaches the repeat row as the first does. */
    static Tune repeatAgrees() {
        return built(List.of(
                row(Timer.A, start(Register.R8, LOUD, STOPPED, false),
                        Timer.D, start(Register.R9, SOFT,
                                new Timing(Chip.prescaler(4), 60, true, true), false)),
                row(Timer.A, new Retune(rate(4, 100)),
                        Timer.D, start(Register.R9, LOUD, rate(4, 60), false)),
                row(Timer.A, new Retune(rate(10, 90)),
                        Timer.D, start(Register.R9, SOFT, rate(4, 100), false)),
                row(Timer.A, new Retune(rate(4, 100)),
                        Timer.D, start(Register.R9, LOUD, rate(4, 60), false))), 1);
    }

    /**
     * The record of {@code tune} over one pass and one loop (SPEC.md 7.4)
     * against the rates of its rows, and its table against rules 4(b) and
     * 4(d). At the frame of each start and retune of the structure, the
     * kept select and count of its effect are the operation's (YMXS,
     * SPEC.md 7.4); a start that leaves the control column unset follows a
     * start of a repeating source with bit 3 at 0 and no stop between; and
     * the first start or retune after a deferred start sets both rate
     * columns. Returns the starts and retunes of the repeat row read a
     * second time.
     */
    private static int assertTheRatesOfTheStructure(Tune tune) {
        Padding.Padded padded = Padding.toUnit(tune, YmToYmxr.UNIT, new Report());
        byte[] file = file(tune);
        String[] lines = new String(Trace.record(file, -1),
                java.nio.charset.StandardCharsets.US_ASCII).split("\n");
        org.dtx.Table table = TuneFile.read(file).table();
        List<org.ymxs.YMXS.Source> sources = Tunes.sources(tune);
        java.util.regex.Pattern entry = java.util.regex.Pattern.compile(
                "\"(\\d)\":\\{\"target\":\\d+,\"source\":\\d+,\"select\":(\\d+),\"count\":(\\d+)");
        int rows = padded.frames().length;
        int repeat = padded.tune().table().repeat().orElseThrow();
        int[] select = new int[4];
        int[] count = new int[4];
        boolean[] counting = new boolean[4];
        boolean[] deferred = new boolean[4];
        int again = 0;
        for (int k = 0; k + 1 < lines.length; k++) {
            int t = k < rows ? k : repeat + (k - rows) % (rows - repeat);
            java.util.regex.Matcher m = entry.matcher(lines[k + 1]);
            while (m.find()) {
                int i = Integer.parseInt(m.group(1));
                select[i] = Integer.parseInt(m.group(2));
                count[i] = Integer.parseInt(m.group(3));
            }
            for (int i = 0; i < 4; i++) {
                int source = table.column(Columns.EFFECT + 4 * i + 1)[t] & 0xFF;
                int control = table.column(Columns.EFFECT + 4 * i + 2)[t] & 0xFF;
                if (source == 0x80) {
                    counting[i] = false;
                } else if (source > 0x80) {
                    boolean repeats = Tunes.rows(sources.get(source - 0x81)).repeat()
                            .isPresent();
                    if ((control & 0x80) == 0) {
                        assertTrue(counting[i], "frame " + k + ": effect " + i + " starts with"
                                + " the control column unset on a stopped timer");
                    }
                    counting[i] = (control & 0x80) == 0 ? counting[i] && repeats
                            : (control & Columns.DEFERRED) == 0 && repeats;
                }
            }
            int s = padded.frames()[t];
            if (s < 0) {
                continue;
            }
            for (Map.Entry<Timer, Effect> one : Tunes.effects(Tunes.rows(tune).get(s))
                    .entrySet()) {
                Timing timing = one.getValue() instanceof Start start ? start.timing()
                        : one.getValue() instanceof Retune retune ? retune.timing() : null;
                if (timing == null) {
                    continue;
                }
                int i = 0;
                while (Schema.timer(i) != one.getKey()) {
                    i++;
                }
                String at = "frame " + k + ", row " + s + " of the structure, effect " + i;
                if (deferred[i]) {
                    int control = table.column(Columns.EFFECT + 4 * i + 2)[t] & 0xFF;
                    int written = table.column(Columns.EFFECT + 4 * i + 3)[t] & 0xFF;
                    assertTrue((control & 0x80) != 0
                            && (written != 0 || (control & Columns.COUNT_VALUE) != 0),
                            at + ": the first start or retune after a deferred start sets"
                            + " both rate columns");
                }
                deferred[i] = one.getValue() instanceof Start start && start.deferred();
                assertEquals(Chip.divides(timing.prescaler()), Columns.PRESCALER[select[i]],
                        at + ": the kept select");
                assertEquals(timing.count(), count[i], at + ": the kept count");
                if (k >= rows && t == repeat) {
                    again++;
                }
            }
        }
        return again;
    }

    /** The repeat row of {@code tune}, a row of the columns
     *  {@code Schema.of} writes without the padding of SPEC.md 6.3. */
    private static int repeatRow(Tune tune) {
        return tune.table().repeat().orElseThrow();
    }

    /**
     * The retune at the repeat row is at the count the timer counts at in
     * the first pass, and the last row leaves another, so the repeat row
     * writes the count column, and the second pass records the count of
     * the structure (rule 4(e)). The select agrees across the wrap, and
     * the control column stays unset.
     */
    @Test
    void theRepeatRowWritesACountTheLastRowLeavesOtherwise() {
        Tune tune = repeatCount();
        byte[][] column = Schema.of(tune).columns().column;
        int r = repeatRow(tune);
        assertEquals(100, column[Columns.EFFECT + 3][r] & 0xFF, "the count column");
        assertEquals(0, column[Columns.EFFECT + 2][r], "the control column");
        assertEquals(1, assertTheRatesOfTheStructure(tune),
                "the retune at the repeat row, read a second time");
    }

    /** The select the last row leaves differs from the select before the
     *  repeat row, and the count agrees: the repeat row writes the control
     *  column alone (rule 4(e)). */
    @Test
    void theRepeatRowWritesASelectTheLastRowLeavesOtherwise() {
        Tune tune = repeatSelect();
        byte[][] column = Schema.of(tune).columns().column;
        int r = repeatRow(tune);
        assertEquals(0x80 | 1, column[Columns.EFFECT + 2][r] & 0xFF, "the control column");
        assertEquals(0, column[Columns.EFFECT + 3][r], "the count column");
        assertEquals(1, assertTheRatesOfTheStructure(tune));
    }

    /** The timer counts before the repeat row and is stopped after the
     *  last row: the start at the repeat row writes both rate columns, so
     *  the select it writes starts the timer in the second pass (SPEC.md
     *  4.3 step 5, rule 4(e)). */
    @Test
    void theRepeatRowStartsATimerTheLastRowStops() {
        Tune tune = repeatCounting();
        byte[][] column = Schema.of(tune).columns().column;
        int r = repeatRow(tune);
        assertEquals(0x80 | 1, column[Columns.EFFECT + 2][r] & 0xFF, "the control column");
        assertEquals(100, column[Columns.EFFECT + 3][r] & 0xFF, "the count column");
        assertEquals(0x82, column[Columns.EFFECT + 1][r] & 0xFF, "the source");
        assertEquals(1, assertTheRatesOfTheStructure(tune));
    }

    /** A deferred start on the last row leaves the rate open across the
     *  wrap, so the start at the repeat row writes both rate columns
     *  (rule 4(d), 4(e)). */
    @Test
    void aStartPendingAcrossTheWrapOpensTheRate() {
        Tune tune = repeatPending();
        byte[][] column = Schema.of(tune).columns().column;
        int r = repeatRow(tune);
        assertEquals(0x80 | 1, column[Columns.EFFECT + 2][r] & 0xFF, "the control column");
        assertEquals(100, column[Columns.EFFECT + 3][r] & 0xFF, "the count column");
        assertEquals(1, assertTheRatesOfTheStructure(tune));
    }

    /**
     * A deferred start on the last row leaves the rate open across the
     * wrap, so the retune at the repeat row sets both rate columns (rule
     * 4(d), 4(e)). The record keeps 100 at the repeat row before the fix
     * and after it; 9f66c42 left both columns unset there, and the timer
     * counted at 90 in the second pass until the tick that performs the
     * start.
     */
    @Test
    void theFirstRetuneAfterADeferredStartAcrossTheWrapSetsBothColumns() {
        Tune tune = repeatDeferred();
        byte[][] column = Schema.of(tune).columns().column;
        int r = repeatRow(tune);
        assertEquals(0x80 | 1, column[Columns.EFFECT + 2][r] & 0xFF, "the control column");
        assertEquals(100, column[Columns.EFFECT + 3][r] & 0xFF, "the count column");
        assertEquals(1, assertTheRatesOfTheStructure(tune));
    }

    /**
     * Where the last row leaves the select, the count and the counting
     * flag of each effect as they are before the repeat row, the repeat
     * row leaves its rate columns unset as in the first pass, and the
     * file is byte for byte the one 9f66c42 wrote.
     */
    @Test
    void aRateThatAgreesAcrossTheWrapIsLeftUnset() throws Exception {
        Tune tune = repeatAgrees();
        byte[][] column = Schema.of(tune).columns().column;
        int r = repeatRow(tune);
        for (int c : new int[] {Columns.EFFECT + 2, Columns.EFFECT + 3, Columns.EFFECT + 6,
                Columns.EFFECT + 7}) {
            assertEquals(0, column[c][r], "column " + c + " of the repeat row");
        }
        assertEquals(2, assertTheRatesOfTheStructure(tune));
        byte[] sha = java.security.MessageDigest.getInstance("SHA-256").digest(file(tune));
        assertEquals("4fa48464fa3a207452cd3b37b8845004ab5f68c645527357c2ea64fe46f1238b",
                java.util.HexFormat.of().formatHex(sha));
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
        List<Row> big = List.of(row(Map.of(), Timer.A, start(Register.R8,
                square, new Timing( Chip.prescaler(4), Chip.MOST_COUNT + 1, true, true), false)));
        String said = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Schema.of(built(big, 0))).getMessage());
        assertTrue(said.contains("count of 256"), "a count of 256 is past the timer's data"
                + " register, which the count column is: " + said);
        // A source on a register that fills its byte has no marker in it:
        // its rows are whole bytes and a player counts them (3.1), which
        // bit 31 of its index entry marks, in a file of version 7 (3.3.5).
        org.ymxs.YMXS.Single whole = Tunes.repeating("whole", List.of(200), 0);
        List<Row> counted = List.of(row(Map.of(), Timer.A, start(Register.R0,
                whole, new Timing(Chip.prescaler(4), 100, true, true), false)));
        Schema.Made made = Schema.of(built(counted, 0));
        byte[] file = org.ymxr.Tune.write(made.columns(), made.sources(), made.rate(),
                YmToYmxr.UNIT, BuiltTunes.RING, new Report()).file();
        assertEquals(org.ymxr.Tune.UNMAPPED, org.ymxr.Tune.getWord(file, 4),
                "a tune with a counted source is version " + org.ymxr.Tune.UNMAPPED);
        TuneFile read = TuneFile.read(file);
        assertEquals(200, read.sources().get(0).column(0)[0] & 0xFF,
                "the row is the value, with no marker in bit 7");
        assertTrue(read.counted().get(0),
                "bit 31 of the index entry marks the source counted");
    }

    @Test
    void aSourceOnTheEnvelopePeriodIsCountedAndReachesTheWholeRange() {
        // Both registers of `setEnvelope` read every bit of their byte, so
        // a source on it is counted: its two columns are whole bytes, it
        // reaches a period of 0 to 65,535, and the file is version 7
        // (SPEC.md 2.1.3, 3.3.5).
        org.ymxs.YMXS.Pair sweep = new org.ymxs.YMXS.Pair("period",
                Tunes.repeating(List.of(new org.ymxs.YMXS.Two(0, 200),
                        new org.ymxs.YMXS.Two(255, 255)), 0));
        List<Row> rows = List.of(row(Map.of(), Timer.A, new Start(
                new org.ymxs.YMXS.TwoConnection(new org.ymxs.YMXS.SetEnvelope(), sweep),
                new Timing(Chip.prescaler(4), 100, true, true), false, Tunes.NO_PARTS)));
        Schema.Made made = Schema.of(built(rows, 0));
        byte[] file = org.ymxr.Tune.write(made.columns(), made.sources(), made.rate(),
                YmToYmxr.UNIT, BuiltTunes.RING, new Report()).file();
        assertEquals(org.ymxr.Tune.UNMAPPED, org.ymxr.Tune.getWord(file, 4),
                "a tune with a counted source of several columns is version "
                        + org.ymxr.Tune.UNMAPPED);
        TuneFile read = TuneFile.read(file);
        assertTrue(read.counted().get(0), "bit 31 of the index entry marks it counted");
        assertEquals(200, read.sources().get(0).column(1)[0] & 0xFF,
                "row 0 of the high byte's column is the value");
        assertEquals(255, read.sources().get(0).column(1)[1] & 0xFF,
                "and its last row is 255, where a marker would leave 127");
    }

    @Test
    void aCountedSourceOnTheMixerCarriesTheTwoPortDirections() {
        // A tick of a counted source writes the row whole, so the two
        // port directions of R7 are in the source: the structure's
        // value is the mixer's six bits and the writer sets bits 7 and 6
        // (SPEC.md rule 2(f), 1.4.2).
        org.ymxs.YMXS.Single gate = Tunes.repeating("gate", List.of(56, 57), 0);
        List<Row> rows = List.of(row(Map.of(), Timer.A, start(Register.R7, gate,
                new Timing(Chip.prescaler(4), 100, true, true), false)));
        Schema.Made made = Schema.of(built(rows, 0));
        byte[] file = org.ymxr.Tune.write(made.columns(), made.sources(), made.rate(),
                YmToYmxr.UNIT, BuiltTunes.RING, new Report()).file();
        TuneFile read = TuneFile.read(file);
        assertTrue(read.counted().get(0), "a source on setR7 is counted");
        assertEquals(0xF8, read.sources().get(0).column(0)[0] & 0xFF,
                "row 0 is the mixer's 56 with both port directions");
        assertEquals(0xF9, read.sources().get(0).column(0)[1] & 0xFF,
                "and row 1 the mixer's 57 with them");
    }

    @Test
    void everyTimerRunsTheEffectTheSpecificationAssignsIt() {
        assertEquals(Timer.A, Schema.timer(0));
        assertEquals(Timer.D, Schema.timer(1));
        assertEquals(Timer.B, Schema.timer(2));
        assertEquals(Timer.C, Schema.timer(3));
    }
}
