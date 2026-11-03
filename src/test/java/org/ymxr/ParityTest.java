package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The two trees against each other, byte for byte.
 *
 * <p>One input has one output. Java and Go read the same dumps, write the
 * same tune files, bind them the same way and put the same bytes behind
 * the core and the stub, so a caller who runs either has the same file at
 * every step.
 *
 * <p>The tools are run as a caller runs them: the input on standard input,
 * the output on standard output. Standard output and the exit are
 * compared. A report is prose, and the progress lines a long run prints
 * are spaced by the clock, so a report is compared with those lines
 * dropped.
 *
 * <p>Skipped where Go is not installed, or where the Java tools have not
 * been built; the case of the YMXS kit where the YMXS checkout is absent.
 */
final class ParityTest {

    /** Where the Go tools are built, once for every test here. */
    private static @Nullable Path built;

    @BeforeAll
    static void buildTheGoTree() throws IOException, InterruptedException {
        Assumptions.assumeTrue(onThePath("go"), "no go on the path");
        Assumptions.assumeTrue(Files.exists(Path.of("target/classes/org/ymxr/Tune.class")),
                "the Java tools are not built");
        Path into = Files.createTempDirectory("ymxr-go");
        Process ran = new ProcessBuilder("go", "build", "-o", into + "/", "./cmd/...")
                .directory(Path.of("go").toFile())
                .redirectErrorStream(true)
                .start();
        String said = new String(ran.getInputStream().readAllBytes());
        assertEquals(0, ran.waitFor(), "the Go tree builds: " + said);
        built = into;
    }

    private static boolean onThePath(String tool) {
        try {
            return new ProcessBuilder(tool, "version").redirectErrorStream(true)
                    .start().waitFor() == 0;
        } catch (IOException | InterruptedException no) {
            return false;
        }
    }

    /** What one run came to. */
    private record Ran(int exit, byte[] out, String said) {
    }

    /** {@code tool} run on {@code in}, out of the tree {@code where} names. */
    private static Ran ran(Path where, String tool, byte[] in, String... flags)
            throws IOException, InterruptedException {
        Path input = Files.createTempFile("in", ".bin");
        Files.write(input, in);
        List<String> command = new ArrayList<>(List.of(where.resolve(tool).toString()));
        command.addAll(List.of(flags));
        Process ran = new ProcessBuilder(command).redirectInput(input.toFile()).start();
        byte[] out = ran.getInputStream().readAllBytes();
        String said = new String(ran.getErrorStream().readAllBytes());
        return new Ran(ran.waitFor(), out, said);
    }

    /** A progress line, which the clock spaces: the two labels a tool
     *  reports progress under, the count, and the percentage. Matched by
     *  the whole line, so a report's line is never read as one. */
    private static final Pattern PROGRESS = Pattern.compile(
            "^ {2}(packing the columns|read) \\d+ of \\d+ \\(\\d+%\\)$");

    /** A report with the lines the clock spaces dropped, which two trees
     *  reach at different rows. */
    private static String steady(String said) {
        return said.lines().filter(one -> !PROGRESS.matcher(one).matches())
                .reduce("", (a, b) -> a + b + "\n");
    }

    /** The same call in both trees, which must come to the same bytes. */
    private static byte[] both(String tool, byte[] in, String... flags) throws Exception {
        return same(tool, in, flags).out();
    }

    /** The same call in both trees, and the run of the Java tree. */
    private static Ran same(String tool, byte[] in, String... flags) throws Exception {
        Ran java = ran(Path.of("bin"), tool, in, flags);
        Ran go = ran(built(), tool, in, flags);
        assertEquals(java.exit(), go.exit(), tool + " exits the same: " + java.said()
                + " | " + go.said());
        assertArrayEquals(java.out(), go.out(), tool + " writes the same bytes");
        assertEquals(steady(java.said()), steady(go.said()), tool + " reports the same");
        return java;
    }

    private static Path built() {
        Path where = built;
        if (where == null) {
            throw new IllegalStateException("the Go tools are not built");
        }
        return where;
    }

    private static List<Path> dumps() throws IOException {
        try (Stream<Path> at = Files.list(Path.of("ym/test"))) {
            return at.filter(one -> one.toString().endsWith(".ym")).sorted().toList();
        }
    }

    @Test
    void everyDumpConvertsTheSameInBothTrees() throws Exception {
        for (Path dump : dumps()) {
            byte[] bytes = Files.readAllBytes(dump);
            byte[] structure = both("ym-to-ymxs", bytes, "-silent");
            assertTrue(structure.length > 0, dump + " converts");
            byte[] tune = both("ym-to-ymxr", bytes, "-silent");
            assertArrayEquals(tune, both("ymxs-to-ymxr", structure, "-silent"),
                    dump + ": the one call and the two stages write one file");
        }
    }

    /** The starts outside rule 3(a) that the writer sets the place's reset
     *  on (SPEC.md 6), the one only the wrap makes a breach among them: one
     *  file and one report, the check's warnings in it. */
    @Test
    void aStartOutsideRule3aConvertsTheSameInBothTrees() throws Exception {
        byte[] structure = org.ymxs.Text.write(org.ymxs.Tunes.multi(YmxsTest.outsideRule3a()))
                .getBytes(StandardCharsets.UTF_8);
        assertTrue(both("ymxs-to-ymxr", structure).length > 0, "the structure converts");
    }

    /** A deferred start (SPEC.md 4.8): one tune file in both trees, and one
     *  record of it, bit 3 of the control column as `deferred`. */
    @Test
    void aDeferredStartConvertsTheSameInBothTrees() throws Exception {
        byte[] structure = org.ymxs.Text.write(org.ymxs.Tunes.multi(YmxsTest.deferred()))
                .getBytes(StandardCharsets.UTF_8);
        byte[] tune = both("ymxs-to-ymxr", structure, "-silent");
        String record = new String(both("ymxr-trace", tune), StandardCharsets.UTF_8);
        assertTrue(record.contains("\"deferred\":true"), record);
        // the core with the deferred start in, and the report that names it
        byte[] sndh = both("ymxr-sndh", tune, "-tDeferred");
        assertEquals(Sndh.CORE_PCREL | Sndh.CORE_DEFER, Tune.getWord(sndh,
                Sndh.even(Tune.getWord(sndh, 2) + 2) + Sndh.CORE_FLAGS_AT));
        both("ymxr-sndh", tune, "-perf", "-lean", "-abs");
        both("ymxr-prg", sndh, "-silent");
    }

    /** Sources of two and of three values a row: one tune file, SNDH file
     *  and program in both trees. */
    @Test
    void aSourceOfSeveralValuesARowConvertsTheSameInBothTrees() throws Exception {
        byte[] structure = org.ymxs.Text.write(org.ymxs.Tunes.multi(YmxsTest.several()))
                .getBytes(StandardCharsets.UTF_8);
        byte[] tune = both("ymxs-to-ymxr", structure, "-silent");
        assertTrue(tune.length > 0, "the structure converts");
        both("ymxr-trace", tune);
        both("ymxs-to-sndh", structure, "-silent");
        both("ymxs-to-prg", structure, "-silent");
    }

    /** {@code tune} as JSON, the input of the tools that read a structure. */
    private static byte[] json(org.ymxs.YMXS.Tune tune) {
        return org.ymxs.Text.write(org.ymxs.Tunes.multi(tune)).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Mappings through the structure (SPEC.md 1.10, rule 2(g)): the fade,
     * a remap to the identity, a start through a transformation after the
     * rows padding adds, the counted source on `setR10` of keys of 128 or
     * more, and an effect through the identity alone beside one that maps.
     * One tune file, version 10 where a row maps a value and 9 otherwise,
     * one record and one check in both trees, and one report.
     */
    @Test
    void aMappedTuneConvertsTheSameInBothTrees() throws Exception {
        java.util.Map<org.ymxs.YMXS.Tune, Integer> tunes = new java.util.LinkedHashMap<>();
        tunes.put(YmxsTest.fade(), Tune.VERSION);
        tunes.put(YmxsTest.remapped(), Tune.UNMAPPED);
        tunes.put(YmxsTest.mappedAfterPadding(), Tune.VERSION);
        tunes.put(YmxsTest.sampled(), Tune.VERSION);
        tunes.put(YmxsTest.identityBeside(), Tune.VERSION);
        tunes.put(YmxsTest.outsideThrough(128, false), Tune.UNMAPPED);
        for (java.util.Map.Entry<org.ymxs.YMXS.Tune, Integer> one : tunes.entrySet()) {
            byte[] tune = both("ymxs-to-ymxr", json(one.getKey()));
            assertEquals((int) one.getValue(), Tune.getWord(tune, 4), "the version");
            both("ymxr-trace", tune);
            assertEquals("standard input: every start follows rule 3\n",
                    new String(both("ymxr-check", tune), StandardCharsets.UTF_8));
        }
        byte[] sampled = both("ymxs-to-ymxr", json(YmxsTest.sampled()), "-silent");
        assertTrue(TuneFile.read(sampled).counted().get(0), "the sample is counted");
        // DECISIONS: the mapping column of the effect through the identity
        // is 0 in every row of the file both trees write
        byte[] beside = both("ymxs-to-ymxr", json(YmxsTest.identityBeside()), "-silent");
        byte[] column = TuneFile.read(beside).table().column(Columns.MAPPING + 1);
        assertArrayEquals(new byte[column.length], column, "effect 1's mapping column");
    }

    /**
     * The kit's tune of version 10 (doc/conformance/README.md 6): its
     * structure through {@code ymxs-to-ymxr} in both trees is the kit's
     * file byte for byte, and the record of both trees is the reference
     * MANIFEST.txt lists. The tune defers a start and maps a value, so
     * {@code ymxr-sndh} and {@code ymxs-to-sndh} put it in both trees on the
     * core with the deferred start and the lookup in (BINARIES.md 2.1).
     */
    @Test
    void theKitsMappedTuneConvertsAndReadsTheSameInBothTrees() throws Exception {
        byte[] kit = Files.readAllBytes(ConformanceTest.TUNES.resolve("mapped.ymxr"));
        assertArrayEquals(kit, both("ymxs-to-ymxr", json(ConformanceTest.mapped()), "-silent"),
                "the kit's mapped.ymxr");
        assertEquals(Tune.VERSION, Tune.getWord(kit, 4), "the version of mapped.ymxr");
        byte[] record = both("ymxr-trace", kit, "-silent");
        String line = ConformanceTest.sha256(record) + "  " + record.length + "  mapped.jsonl\n";
        assertTrue(Files.readString(ConformanceTest.KIT.resolve("MANIFEST.txt")).contains(line),
                "the record of mapped.ymxr is the reference");
        for (byte[] sndh : List.of(both("ymxr-sndh", kit),
                both("ymxs-to-sndh", json(ConformanceTest.mapped())))) {
            assertEquals(Sndh.CORE_PCREL | Sndh.CORE_DEFER | Sndh.CORE_MAP, Tune.getWord(sndh,
                    Sndh.even(Tune.getWord(sndh, 2) + 2) + Sndh.CORE_FLAGS_AT));
        }
    }

    /**
     * A rate at the repeat row that the last row leaves otherwise (SPEC.md
     * rule 4(e)): the retune at the repeat row at count 100 with the last
     * row at 90, then the same with the select, a timer the last row stops,
     * a start pending across the wrap, a deferred start on the last row,
     * and rates that agree across it. One tune file and one record in both
     * trees; in the record of the first, the frame that reads the repeat
     * row a second time records count 100.
     */
    @Test
    void aRateAtTheRepeatRowConvertsTheSameInBothTrees() throws Exception {
        byte[] tune = both("ymxs-to-ymxr", json(YmxsTest.repeatCount()), "-silent");
        String[] record = new String(both("ymxr-trace", tune), StandardCharsets.US_ASCII)
                .split("\n");
        // padded to unit 2: an unset row before row 1, so the repeat row
        // is row 2 of four, read again at frame 4, line 5 of the record
        assertEquals(7, record.length, String.join("\n", record));
        assertTrue(record[5].contains("\"select\":1,\"count\":100,"), record[5]);
        for (org.ymxs.YMXS.Tune one : List.of(YmxsTest.repeatSelect(),
                YmxsTest.repeatCounting(), YmxsTest.repeatPending(),
                YmxsTest.repeatDeferred(), YmxsTest.repeatAgrees())) {
            both("ymxr-trace", both("ymxs-to-ymxr", json(one)));
        }
    }

    /** The fade through the binder, which writes a bound tune of version
     *  10 (BINARIES.md 1.2): ymxr-bind with its report, ymxr-layout of the
     *  bound tune, and a set of the fade and a dump through ymxr-multi and
     *  ymxr-layout, the same in both trees; and the SNDH writer, which puts
     *  the core with the lookup in under the fade and under the set, whose
     *  two tables are in two images (BINARIES.md 2.1, 1.4): ymxr-sndh with
     *  its report, ymxr-prg and ymxr-layout of each file, and ymxs-to-sndh
     *  and ymxs-to-prg on the structure, the same in both trees. */
    @Test
    void theFadeBindsTheSameInBothTrees() throws Exception {
        byte[] structure = json(YmxsTest.fade());
        byte[] tune = both("ymxs-to-ymxr", structure, "-silent");
        Ran bind = same("ymxr-bind", tune);
        byte[] bound = bind.out();
        assertEquals(Tune.VERSION, Tune.getWord(bound, 4), "the bound tune's version");
        int block = Tune.getLong(bound, Bound.TRANSFORMATIONS_AT);
        assertTrue(steady(bind.said()).contains("  the transformations    at " + block + ", "
                + Tune.getWord(bound, block) + " of "
                + Report.count(bound.length - block, "byte", "bytes") + "\n"), bind.said());
        assertEquals(block, Tune.getLong(bound, Bound.IMAGE_AT) + imageBytes(bind.said()),
                "the image ends at the transformation block");
        String record = new String(both("ymxr-layout", bound), StandardCharsets.US_ASCII);
        assertTrue(record.contains(",\"transformations\":" + block + "}\n")
                && record.contains("{\"part\":\"transformations\",\"at\":" + block + ","),
                record);
        byte[] dump = both("ym-to-ymxr", Files.readAllBytes(dumps().get(0)), "-silent");
        byte[] multi = multiOf(List.of(tune, dump));
        assertEquals(Tune.VERSION, Tune.getWord(multi, 4), "a set with the fade");
        both("ymxr-layout", multi);
        for (byte[] in : List.of(tune, multi)) {
            Ran made = same("ymxr-sndh", in);
            assertEquals(0, made.exit(), made.said());
            byte[] sndh = made.out();
            assertEquals(Sndh.CORE_PCREL | Sndh.CORE_MAP, Tune.getWord(sndh,
                    Sndh.even(Tune.getWord(sndh, 2) + 2) + Sndh.CORE_FLAGS_AT));
            String said = steady(made.said());
            assertTrue(said.contains("; the lookup in: a subtune maps a value\n")
                    && said.contains("the images: " + (in == tune ? "1 image" : "2 images")
                    + " of "), said);
            both("ymxr-prg", sndh);
            String layout = new String(both("ymxr-layout", sndh), StandardCharsets.US_ASCII);
            assertTrue(layout.contains("\"version\":10,") && layout.contains("\"binds\":10,"),
                    layout);
        }
        for (String tool : List.of("ymxs-to-sndh", "ymxs-to-prg")) {
            Ran ran = same(tool, structure);
            assertEquals(0, ran.exit(), ran.said());
        }
    }

    /**
     * A tick of a core with the lookup in reads its row from its handlers,
     * which begin at the core's field at 26 (BINARIES.md 2.1): a mapped
     * source of 20,000 rows is on the core that reads a row through the
     * program counter, and one of 30,000 on the core that reads an address,
     * and -pcrel reports the line that names the first handler, the same in
     * both trees.
     */
    @Test
    void aMappedFileIsMeasuredFromTheFirstHandlerInBothTrees() throws Exception {
        byte[] within = both("ymxr-sndh", BinariesTest.longMapped(20000), "-silent");
        assertEquals(Sndh.CORE_PCREL | Sndh.CORE_MAP, Tune.getWord(within,
                Sndh.even(Tune.getWord(within, 2) + 2) + Sndh.CORE_FLAGS_AT));
        byte[] far = BinariesTest.longMapped(30000);
        Ran chosen = same("ymxr-sndh", far);
        assertTrue(steady(chosen.said()).contains("ticks that read a row through an absolute"
                + " address: the tunes end past the 32767 bytes a displacement reaches; the"
                + " lookup in: a subtune maps a value\n"), chosen.said());
        assertEquals(Sndh.CORE_MAP, Tune.getWord(chosen.out(),
                Sndh.even(Tune.getWord(chosen.out(), 2) + 2) + Sndh.CORE_FLAGS_AT));
        Ran asked = same("ymxr-sndh", far, "-pcrel", "-silent");
        assertEquals(1, asked.exit(), asked.said());
        assertTrue(asked.said().matches("ymxr-sndh: the tunes end \\d+ bytes past the core's"
                + " first handler, at \\d+, and a tick that reads a row through the program"
                + " counter reaches 32767\n"), asked.said());
    }

    /** The fade with every offset of its transformation index 0, inside
     *  the index, which the reader reads (SPEC.md 3.3.4): the binder
     *  copies the block to the index's end (BINARIES.md 1.1), the same
     *  bound tune in both trees; and that bound tune with -8 in the field
     *  at 24, which ymxr-layout reads as a signed number (BINARIES.md
     *  1.2), the same record in both trees. */
    @Test
    void aBlockWhoseTransformationsOverlapItsIndexBindsTheSameInBothTrees()
            throws Exception {
        byte[] tune = both("ymxs-to-ymxr", json(YmxsTest.fade()), "-silent");
        int at = Tune.getLong(tune, Tune.TRANSFORMATIONS_AT);
        int index = 2 + 4 * Tune.getWord(tune, at);
        for (int i = 2; i < index; i += 4) {
            Tune.putLong(tune, at + i, 0);
        }
        Ran bind = same("ymxr-bind", tune);
        assertEquals(0, bind.exit(), bind.said());
        byte[] bound = bind.out();
        int block = Tune.getLong(bound, Bound.TRANSFORMATIONS_AT);
        assertArrayEquals(Arrays.copyOfRange(tune, at, at + index),
                Arrays.copyOfRange(bound, block, block + index), "the index in the block");
        Tune.putLong(bound, Bound.TRANSFORMATIONS_AT, -8);
        String record = new String(both("ymxr-layout", bound), StandardCharsets.US_ASCII);
        assertTrue(record.contains(",\"transformations\":-8}\n")
                && !record.contains("{\"part\":\"transformations\""), record);
    }

    /** The bytes of {@code the reader's image} in a report of ymxr-bind. */
    private static int imageBytes(String said) {
        Matcher row = Pattern.compile("(?m)^  the reader's image +at \\d+, (\\d+) bytes$")
                .matcher(said);
        assertTrue(row.find(), said);
        return Integer.parseInt(row.group(1));
    }

    /** ymxr-bind's report on two tunes with sources, four-timers and DBA
     *  2, the image from its offset to the bound tune's end and the
     *  sources from source 1 to the image, and ymxr-layout of the bound
     *  tune of the kit's counted tune, whose source 1 has bit 31 set in
     *  its index entry: the same in both trees. */
    @Test
    void theBindersReportAndTheCountedSourcesReadTheSameInBothTrees() throws Exception {
        byte[] fourTimers = Files.readAllBytes(Path.of("doc/conformance/tunes/four-timers.ymxr"));
        String said = steady(same("ymxr-bind", fourTimers).said());
        assertTrue(said.contains("  the reader's image     at 184, 2876 bytes\n")
                && said.contains("  the source tables      5 of 140 bytes\n"), said);
        byte[] dba = both("ym-to-ymxr", Files.readAllBytes(Path.of("ym/test/DBA 2.ym")),
                "-silent");
        for (byte[] tune : List.of(fourTimers, dba)) {
            Ran bind = same("ymxr-bind", tune);
            byte[] bound = bind.out();
            int image = Tune.getLong(bound, Bound.IMAGE_AT);
            assertEquals(bound.length - image, imageBytes(bind.said()), bind.said());
            int sources = TuneFile.read(tune).sources().size();
            assertTrue(sources > 0, "a tune with sources");
            assertTrue(steady(bind.said()).contains("  the source tables      " + sources
                    + " of " + Report.count(image - Tune.getLong(bound, Bound.INDEX_AT),
                            "byte", "bytes") + "\n"), bind.said());
        }
        byte[] counted = both("ymxr-bind",
                Files.readAllBytes(Path.of("doc/conformance/tunes/counted.ymxr")), "-silent");
        int first = Tune.getLong(counted, Bound.INDEX_AT);
        assertTrue(first < 0, "source 1 of counted.ymxr is counted");
        String record = new String(both("ymxr-layout", counted), StandardCharsets.US_ASCII);
        assertTrue(record.contains("{\"part\":\"source\",\"number\":1,\"at\":"
                + (first & ~Tune.COUNTED) + "}\n") && !record.contains("\"at\":-"), record);
    }

    /** The YMXS checkout whose conformance kit the case below reads, at
     *  the release pom.xml requires: YMXS_REPO, ../YMXS by default. */
    private static Path ymxsRepo() {
        String named = System.getenv("YMXS_REPO");
        return Path.of(named == null || named.isEmpty() ? "../YMXS" : named);
    }

    /**
     * Every tune of the YMXS conformance kit (YMXS, doc/conformance/tunes)
     * through ymxs-to-ymxr in both trees: one tune file, or one line and
     * one exit, and of each tune file one record and one check. The kit's
     * transformations.json maps three values on `setVoiceB`, the line of
     * SPEC.md 6.5.
     */
    @Test
    void everyTuneOfTheYmxsKitConvertsTheSameInBothTrees() throws Exception {
        Path kit = ymxsRepo().resolve("doc/conformance/tunes");
        Assumptions.assumeTrue(Files.isDirectory(kit),
                "no YMXS kit at " + kit + ": YMXS_REPO names the checkout");
        List<Path> tunes;
        try (Stream<Path> at = Files.list(kit)) {
            tunes = at.filter(one -> one.toString().endsWith(".json")).sorted().toList();
        }
        assertTrue(!tunes.isEmpty(), "the kit has tunes");
        int written = 0;
        for (Path one : tunes) {
            Ran java = same("ymxs-to-ymxr", Files.readAllBytes(one), "-silent");
            if (java.exit() == 0) {
                both("ymxr-trace", java.out());
                both("ymxr-check", java.out());
                written++;
            }
        }
        assertTrue(written > 0, "a tune of the kit converts");
        Ran mapped = same("ymxs-to-ymxr", Files.readAllBytes(kit.resolve("transformations.json")),
                "-silent");
        assertEquals(1, mapped.exit(), mapped.said());
        assertTrue(mapped.said().endsWith("ymxs-to-ymxr: row 2: the mapping maps value 0 of"
                + " setVoiceB, and a mapping column maps its value 2 alone\n"), mapped.said());
    }

    /** A mapping this version leaves unencoded: the writer reports its
     *  line, with the row of the structure, exit 1, in both trees (SPEC.md
     *  6.5). */
    @Test
    void aMappingThisFormatCannotEncodeIsTheSameErrorInBothTrees() throws Exception {
        assertWrong(YmxsTest.remappedThrough(128), "the tune maps through 128"
                + " transformations, and a mapping column numbers 127");
        assertWrong(YmxsTest.outsideThrough(127, true), "the tune maps through 128"
                + " transformations, and a mapping column numbers 127");
        assertWrong(YmxsTest.chordRemapped(), "row 2: the mapping maps value 0 of setVoiceB,"
                + " and a mapping column maps its value 2 alone");
        assertWrong(YmxsTest.wrongAfterPadding(), "row 3: the mapping maps value 0 of"
                + " setNoiseA, and a mapping column maps its value 1 alone");
        assertWrong(YmxsTest.wideMixer(), "the source wide has the key 64 in row 1 on setR7,"
                + " and a key on setR7 is 0 to 63");
        for (boolean[] one : new boolean[][] {{true, true}, {false, true}, {true, false}}) {
            assertWrong(YmxsTest.mixerAndTone(one[0], one[1]), "the source gate runs on setR7"
                    + " and on setR0, and every row of a source on setR7 has bits 7 and 6 set"
                    + " (SPEC.md rule 2(f))");
        }
    }

    private static void assertWrong(org.ymxs.YMXS.Tune tune, String line) throws Exception {
        byte[] structure = org.ymxs.Text.write(org.ymxs.Tunes.multi(tune))
                .getBytes(StandardCharsets.UTF_8);
        Ran java = same("ymxs-to-ymxr", structure, "-silent");
        assertEquals(1, java.exit(), java.said());
        assertTrue(java.said().endsWith("ymxs-to-ymxr: " + line + "\n"), java.said());
    }

    /** A tune file with starts outside rule 3(a): one verdict and one exit
     *  in both trees, and one for the file this writer writes. */
    @Test
    void aTuneFileChecksTheSameInBothTrees() throws Exception {
        both("ymxr-check", PlacesTest.outsideRule3a());
        byte[] tune = both("ym-to-ymxr", Files.readAllBytes(Path.of("ym/test/DBA 2.ym")),
                "-silent");
        assertEquals("standard input: every start follows rule 3\n",
                new String(both("ymxr-check", tune), StandardCharsets.UTF_8));
    }

    /**
     * A tune file of version 10 (SPEC.md 1.10, 3.4): one record and one
     * check in both trees; each file a reader reports written wrong one
     * line, and each check of the mapping columns the same lines; a multi
     * file of it, and of it and a dump, version 10, its tables packed
     * again with the same report, and read by the record, the check and
     * the layout as one line or one record; and an SNDH file of it and of
     * the set on the core with the deferred start and the lookup in, since
     * the tune defers a start and maps a value, also with the raster
     * monitor, whose core ends past 32,767 bytes and whose handlers begin
     * within them (BINARIES.md 2.1), and a program of each.
     */
    @Test
    void aMappedTuneFileReadsTheSameInBothTrees() throws Exception {
        byte[] mapped = BuiltTunes.mapped().file();
        String record = new String(both("ymxr-trace", mapped), StandardCharsets.US_ASCII);
        assertTrue(record.contains("\"transformations\":[[") && record.contains("\"mapping\":"),
                record);
        both("ymxr-trace", mapped, "-r200");
        assertEquals("standard input: every start follows rule 3\n",
                new String(both("ymxr-check", mapped), StandardCharsets.UTF_8));
        for (java.util.Map.Entry<String, byte[]> one : MappedTest.wrong().entrySet()) {
            Ran java = same("ymxr-trace", one.getValue());
            assertEquals(1, java.exit(), java.said());
            assertTrue(java.said().contains("does not read: " + one.getKey() + "\n"),
                    java.said());
            assertEquals("standard input:\n  the tune file does not read: " + one.getKey()
                    + "\n", new String(both("ymxr-check", one.getValue()),
                    StandardCharsets.UTF_8));
        }
        for (java.util.Map.Entry<List<String>, byte[]> one
                : MappedTest.unchecked().entrySet()) {
            assertEquals("standard input:\n  " + String.join("\n  ", one.getKey()) + "\n",
                    new String(both("ymxr-check", one.getValue()), StandardCharsets.UTF_8));
            both("ymxr-trace", one.getValue());
        }
        byte[] dump = both("ym-to-ymxr", Files.readAllBytes(dumps().get(0)), "-silent");
        assertEquals(Tune.VERSION, Tune.getWord(multiOf(List.of(mapped)), 4), "a set of one");
        byte[] multi = multiOf(List.of(dump, mapped));
        assertEquals(Tune.VERSION, Tune.getWord(multi, 4), "a set with a mapped tune");
        multiOf(List.of(dump, mapped), "-m480");
        assertEquals(Tune.UNMAPPED, Tune.getWord(multiOf(List.of(dump, dump)), 4));
        assertEquals(1, same("ymxr-trace", multi).exit(), "a record is of one tune file");
        assertEquals(1, same("ymxr-check", multi).exit(),
                "a multi file is neither a dump nor a tune file");
        both("ymxr-layout", multi);
        both("ymxr-bind", mapped);
        for (byte[] in : List.of(mapped, multi)) {
            byte[] sndh = both("ymxr-sndh", in);
            assertEquals(Sndh.CORE_PCREL | Sndh.CORE_DEFER | Sndh.CORE_MAP, Tune.getWord(sndh,
                    Sndh.even(Tune.getWord(sndh, 2) + 2) + Sndh.CORE_FLAGS_AT));
            both("ymxr-prg", sndh, "-silent");
            byte[] watched = both("ymxr-sndh", in, "-perf");
            assertEquals(Sndh.CORE_MONITOR | Sndh.CORE_PCREL | Sndh.CORE_DEFER | Sndh.CORE_MAP,
                    Tune.getWord(watched,
                            Sndh.even(Tune.getWord(watched, 2) + 2) + Sndh.CORE_FLAGS_AT));
            both("ymxr-prg", watched, "-silent");
        }
    }

    @Test
    void everyDumpBindsAndCombinesTheSameInBothTrees() throws Exception {
        for (Path dump : dumps()) {
            byte[] tune = both("ym-to-ymxr", Files.readAllBytes(dump), "-silent");
            both("ymxr-bind", tune, "-silent");
            byte[] sndh = both("ymxr-sndh", tune, "-silent", "-tThe title");
            both("ymxr-prg", sndh, "-silent");
            both("ymxr-trace", tune, "-silent", "-r200");
            both("ymxr-check", Files.readAllBytes(dump), "-silent");
        }
    }

    @Test
    void theFlagsReadTheSameInBothTrees() throws Exception {
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Turrican - world 4-3.ym"));
        for (String[] flags : new String[][] {{"-k1"}, {"-m480"}, {"-r"}, {"-r100"},
                {"-copies"}, {"-k1", "-m1129", "-copies"}}) {
            List<String> argv = new ArrayList<>(List.of("-silent"));
            argv.addAll(List.of(flags));
            both("ym-to-ymxr", dump, argv.toArray(new String[0]));
        }
        byte[] tune = both("ym-to-ymxr", dump, "-silent");
        both("ymxr-sndh", tune, "-silent", "-tOne", "-cTwo", "-perf");
        both("ymxr-sndh", tune, "-silent", "-lean");
        both("ymxr-sndh", tune, "-silent", "-pcrel");
        both("ymxr-sndh", tune, "-silent", "-lean", "-pcrel");
        both("ymxr-sndh", tune, "-silent", "-vbl");
        both("ymxr-sndh", tune, "-silent", "-perf", "-tc");
        both("ymxr-prg", both("ymxr-sndh", tune, "-silent", "-tOne"), "-silent", "-r2000");
        // the clock the file names, the VBL asked for over it, and the
        // VBL the monitor names
        both("ymxr-prg", both("ymxr-sndh", tune, "-silent"), "-silent", "-vbl");
        both("ymxr-prg", both("ymxr-sndh", tune, "-silent", "-vbl"), "-silent");
        both("ymxr-prg", both("ymxr-sndh", tune, "-silent", "-perf"), "-silent");
        both("ymxr-prg", both("ymxr-sndh", tune, "-silent", "-perf"), "-silent", "-tc");
    }

    /**
     * The TIME tag of a tune that plays once, in both trees. Every dump
     * under ym/test repeats, so the cases above write 0 alone: the kit's
     * set has a subtune that repeats and one of 4 frames at 50 Hz, rounded
     * up to 1, and a dump read with -r plays once.
     */
    @Test
    void theTimeTagIsTheSameInBothTrees() throws Exception {
        byte[] set = Files.readAllBytes(Path.of("doc", "conformance-binaries", "files",
                "set.ymxr"));
        byte[] sndh = both("ymxr-sndh", set, "-silent");
        String record = new String(both("ymxr-layout", sndh), StandardCharsets.UTF_8);
        assertTrue(record.contains("\"name\":\"TIME\"") && record.contains("\"seconds\":[0,1]"),
                record);
        both("ymxr-prg", sndh, "-silent");
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Turrican - world 4-3.ym"));
        byte[] once = both("ym-to-ymxr", dump, "-silent", "-r");
        both("ymxr-layout", both("ymxr-sndh", once, "-silent"));
        byte[] zero = once.clone();
        zero[Tune.FRAME_RATE_AT] = 0;
        zero[Tune.FRAME_RATE_AT + 1] = 0;
        Ran java = ran(Path.of("bin"), "ymxr-sndh", zero, "-silent");
        Ran go = ran(built(), "ymxr-sndh", zero, "-silent");
        assertEquals(1, java.exit(), "a rate of 0 is an error: " + java.said());
        assertEquals(java.exit(), go.exit(), "ymxr-sndh exits the same: " + go.said());
        assertEquals(java.said(), go.said(), "ymxr-sndh reports the same");
    }

    @Test
    void aSetOfSubtunesIsTheSameInBothTrees() throws Exception {
        List<Path> dumps = dumps();
        byte[] one = both("ym-to-ymxr", Files.readAllBytes(dumps.get(0)), "-silent");
        byte[] two = both("ym-to-ymxr", Files.readAllBytes(dumps.get(1)), "-silent");
        Path work = Files.createTempDirectory("ymxr-parity");
        Path first = work.resolve("one.ymxr");
        Path second = work.resolve("two.ymxr");
        Files.write(first, one);
        Files.write(second, two);
        Ran java = ran(Path.of("bin"), "ymxr-multi", new byte[0], first.toString(),
                second.toString(), "-silent");
        Ran go = ran(built(), "ymxr-multi", new byte[0], first.toString(),
                second.toString(), "-silent");
        assertEquals(java.exit(), go.exit(), "ymxr-multi exits the same: " + go.said());
        assertArrayEquals(java.out(), go.out(), "ymxr-multi writes the same bytes");
        byte[] sndh = both("ymxr-sndh", java.out(), "-silent");
        both("ymxr-prg", sndh, "-silent");
    }

    /** The multi file of those tune files, written by both trees, which
     *  must be the same bytes. ymxr-multi reads file names rather than
     *  standard input, so it is run here rather than through both. */
    private static byte[] multiOf(List<byte[]> tunes, String... flags) throws Exception {
        Path work = Files.createTempDirectory("ymxr-parity");
        List<String> argv = new ArrayList<>();
        for (int i = 0; i < tunes.size(); i++) {
            Path at = work.resolve("tune" + (i + 1) + ".ymxr");
            Files.write(at, tunes.get(i));
            argv.add(at.toString());
        }
        argv.addAll(List.of(flags));
        String[] named = argv.toArray(new String[0]);
        Ran java = ran(Path.of("bin"), "ymxr-multi", new byte[0], named);
        Ran go = ran(built(), "ymxr-multi", new byte[0], named);
        assertEquals(java.exit(), go.exit(), "ymxr-multi exits the same: " + go.said());
        assertArrayEquals(java.out(), go.out(), "ymxr-multi writes the same bytes");
        assertEquals(steady(java.said()), steady(go.said()), "ymxr-multi reports the same");
        return java.out();
    }

    /**
     * The report a tool prints where it is not silenced.
     *
     * <p>Every other test here runs the tools with -silent, which reduces
     * the report to what the tool wrote and its notes. The rest of the
     * report is where a reader of a run finds its figures: the core's
     * bytes and the file's parts, what an image fixes and how many tunes
     * share it, the stub's patches, and what a binding came to. Those
     * drifted between the trees while every file they name matched byte
     * for byte, so this runs the same tools without the flag.
     *
     * <p>The progress lines are spaced by the clock and come out at
     * different rows, and steady drops those from both.
     */
    /**
     * A count of 1 reads in the singular, the same in both trees (tools.md
     * 1.9): a YM3! dump of one frame, packed at a unit of 1 so that its table
     * is one row, through the converters and the tools that read its tune
     * file, a set of that tune alone, and a check of that one dump.
     */
    @Test
    void aCountOfOneIsSingularInBothTrees() throws Exception {
        byte[] dump = new byte[4 + 14];
        System.arraycopy("YM3!".getBytes(StandardCharsets.US_ASCII), 0, dump, 0, 4);
        dump[4 + 7] = 0x3F;             // R7: every voice off
        dump[4 + 13] = (byte) 0xFF;     // R13 left as it is
        byte[] tune = both("ym-to-ymxr", dump, "-k1");
        both("ym-to-ymxs", dump);
        both("ymxr-bind", tune);
        both("ymxr-trace", tune);
        both("ymxr-sndh", tune);
        byte[] set = multiOf(List.of(tune));
        assertTrue(set.length > 0, "a set of one tune");
        List<String> said = new ArrayList<>();
        for (String[] call : new String[][] {{"ym-to-ymxr", "-k1"}, {"ym-to-ymxs"},
                {"ymxr-bind"}, {"ymxr-sndh"}}) {
            byte[] in = call[0].startsWith("ym-to") ? dump : tune;
            said.add(ran(Path.of("bin"), call[0], in,
                    Arrays.copyOfRange(call, 1, call.length)).said());
        }
        Path work = Files.createTempDirectory("ymxr-parity");
        Path one = work.resolve("one.ym");
        Files.write(one, dump);
        Ran java = ran(Path.of("bin"), "ymxr-check", new byte[0], one.toString());
        Ran go = ran(built(), "ymxr-check", new byte[0], one.toString());
        assertEquals(java.exit(), go.exit(), "ymxr-check exits the same: " + go.said());
        assertArrayEquals(java.out(), go.out(), "ymxr-check writes the same bytes");
        assertEquals(steady(java.said()), steady(go.said()), "ymxr-check reports the same");
        said.add(java.said() + new String(java.out(), StandardCharsets.UTF_8));
        String all = String.join("\n", said);
        for (String line : List.of("30 columns of 1 row, 30 bytes",
                "ym-to-ymxr: 1 frame at 50 Hz, 0 sources", "ym-to-ymxs: YM3! \"\", 1 row at",
                "1 row of 30 columns", "1 image of", "1 tune", "ymxr-sndh:", "1 subtune",
                "1 file to read", "1 dump, 0 wrong")) {
            assertTrue(all.contains(line), () -> "the reports read \"" + line + "\": " + all);
        }
    }

    @Test
    void theVerboseReportIsTheSameInBothTrees() throws Exception {
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Turrican - world 4-3.ym"));
        byte[] structure = both("ym-to-ymxs", dump);
        byte[] tune = both("ym-to-ymxr", dump);
        both("ymxs-to-ymxr", structure);
        both("ymxs-to-sndh", structure);
        both("ymxs-to-sndh", structure, "-vbl");
        both("ymxs-to-prg", structure);
        both("ymxs-to-prg", structure, "-vbl");
        both("ymxs-to-prg", structure, "-perf", "-tc");
        both("ymxr-bind", tune);
        both("ymxr-trace", tune, "-r200");
        both("ymxr-check", dump);
        // The stub's two flag rows follow the core, so the plain core and
        // the one with the raster monitor in report different rows.
        both("ymxr-prg", both("ymxr-sndh", tune));
        both("ymxr-prg", both("ymxr-sndh", tune, "-perf", "-tOne", "-cTwo"), "-r2000");
    }

    /**
     * A set of subtunes reported, where the report has the most to say.
     *
     * <p>The tunes of one multi file are grouped by what an image fixes
     * once, the unit among it, so a set that agrees on those shares one
     * image and one that does not is split. Both cases are here: two tune
     * files packed at one unit, and two packed at two. The two packed
     * through rings of 900 and 300 bytes split until DTX3, whose image
     * leaves the ring to each table.
     */
    @Test
    void aSetOfSubtunesIsReportedTheSameInBothTrees() throws Exception {
        byte[] dump = Files.readAllBytes(dumps().get(0));
        byte[] one = both("ym-to-ymxr", dump, "-silent");
        byte[] two = both("ym-to-ymxr", dump, "-silent", "-k1");
        byte[] shared = multiOf(List.of(one, one));
        byte[] split = multiOf(List.of(one, two));
        // One image for the two that agree, and two for the two that do
        // not: the rows that say so are in the report alone.
        both("ymxr-prg", both("ymxr-sndh", shared, "-perf"));
        both("ymxr-prg", both("ymxr-sndh", split));
    }

    /**
     * A set's rings chosen for it (tools.md 4.7): through ymxr-multi under
     * each of its packing flags, and through ymxs-to-ymxr and ymxs-to-sndh
     * on a structure of two tunes. One file and one report in both trees.
     */
    @Test
    void aSetsRingsAreChosenTheSameInBothTrees() throws Exception {
        List<byte[]> tunes = new ArrayList<>();
        List<org.ymxs.YMXS.Tune> structures = new ArrayList<>();
        for (String name : List.of("Big - Samantha Fox Strip Poker 6.ym",
                "Turrican 2 - world completed 1.ym")) {
            byte[] dump = Files.readAllBytes(Path.of("ym", "test", name));
            tunes.add(both("ym-to-ymxr", dump, "-silent"));
            structures.add(org.ymxs.Text.read(new String(both("ym-to-ymxs", dump, "-silent"),
                    StandardCharsets.UTF_8)).tunes().get(0));
        }
        multiOf(tunes);
        multiOf(tunes, "-m480");
        multiOf(tunes, "-copies");
        byte[] structure = org.ymxs.Text.write(new org.ymxs.YMXS.Multi(structures))
                .getBytes(StandardCharsets.UTF_8);
        both("ymxs-to-ymxr", structure);
        both("ymxs-to-sndh", structure, "-copies");
    }

    /** Every tool of the twelve (tools.md 2), for a check that runs them
     *  all. `ymxr-check` was left out of this list while the comment over it
     *  read eleven. */
    private static final List<String> EVERY_TOOL = List.of("ym-to-ymxs",
            "ym-to-ymxr", "ymxs-to-ymxr", "ymxs-to-sndh", "ymxs-to-prg",
            "ymxr-bind", "ymxr-check", "ymxr-multi", "ymxr-sndh", "ymxr-prg",
            "ymxr-trace", "ymxr-layout");

    /**
     * The tools this runs against the tools tools.md 2 lists. The list
     * here named ten while the comment over it read eleven, and the tool
     * left out, {@code ymxr-check}, was left out of the two checks below.
     */
    @Test
    void everyToolTheDocumentListsIsRunHere() throws Exception {
        String tools = Files.readString(Path.of("doc", "tools.md"));
        int at = tools.indexOf("## 2. The twelve tools");
        assertTrue(at >= 0, "tools.md lists no tools");
        String table = tools.substring(at, tools.indexOf("\n\n", tools.indexOf("|", at)));
        List<String> listed = new ArrayList<>();
        Matcher row = Pattern.compile("^\\| `([a-z0-9-]+)` \\|", Pattern.MULTILINE)
                .matcher(table);
        while (row.find()) {
            listed.add(row.group(1));
        }
        assertEquals(12, listed.size(), "tools.md 2 lists " + listed);
        assertEquals(new TreeSet<>(listed), new TreeSet<>(EVERY_TOOL),
                "a tool of tools.md 2 is run here, and one run here is listed there");
    }

    @Test
    void anEmptyInputIsOneFaultInBothTrees() throws Exception {
        // The three tools whose input is YMXS's JSON form were left out of
        // this check: an empty text parsed to an absent format in that
        // tree's Java reader and to a text that is not JSON in its Go one,
        // and odipar/YMXS#58 reports the second in both. YMXS reads the
        // second from its 0.4.4 on, so the three read one here now.
        for (String tool : EVERY_TOOL) {
            Ran java = ran(Path.of("bin"), tool, new byte[0], "-silent");
            Ran go = ran(built(), tool, new byte[0], "-silent");
            assertTrue(java.exit() != 0, tool + " reads an empty input: "
                    + java.said());
            assertEquals(java.exit(), go.exit(), tool + " exits the same: "
                    + go.said());
            assertEquals(java.said(), go.said(), tool + " reports the same");
        }
    }

    @Test
    void anUnknownFlagIsOneFaultInBothTrees() throws Exception {
        for (String tool : EVERY_TOOL) {
            Ran java = ran(Path.of("bin"), tool, new byte[0], "-zz");
            Ran go = ran(built(), tool, new byte[0], "-zz");
            assertTrue(java.exit() != 0, tool + " reads -zz: " + java.said());
            assertEquals(java.exit(), go.exit(), tool + " exits the same: "
                    + go.said());
            assertEquals(java.said(), go.said(), tool + " reports the same");
        }
    }

    /** The record of every file of the binaries kit, from both trees:
     *  the four kinds of BINARIES.md 6 read the same. */
    @Test
    void everyFileOfTheBinariesKitRecordsTheSameInBothTrees() throws Exception {
        Path files = Path.of("doc", "conformance-binaries", "files");
        try (Stream<Path> found = Files.list(files)) {
            for (Path file : found.sorted().toList()) {
                byte[] record = both("ymxr-layout", Files.readAllBytes(file));
                assertArrayEquals(Files.readAllBytes(Path.of("doc", "conformance-binaries",
                        "records", file.getFileName() + ".jsonl")), record,
                        file + "'s record is the kit's");
            }
        }
    }

    /**
     * A file of the kit cut short, in both trees: cut one byte into each
     * part its record lists, and of the program inside its stub, before
     * any SNDH. Each cut records the same in both, the whole record or
     * the line of tools.md 9.7 where the record reads past the end.
     */
    @Test
    void aFileCutShortReadsTheSameInBothTrees() throws Exception {
        Path kit = Path.of("doc", "conformance-binaries");
        Pattern at = Pattern.compile("\"at\":(\\d+)");
        for (String name : List.of("two.snd", "two.prg")) {
            byte[] file = Files.readAllBytes(kit.resolve("files").resolve(name));
            TreeSet<Integer> cuts = new TreeSet<>();
            for (String line : Files.readAllLines(kit.resolve("records")
                    .resolve(name + ".jsonl"))) {
                Matcher part = at.matcher(line);
                if (part.find()) {
                    cuts.add(Integer.parseInt(part.group(1)) + 1);
                }
            }
            if (name.endsWith(".prg")) {
                cuts.add(1000);
            }
            for (int cut : cuts) {
                byte[] in = Arrays.copyOf(file, cut);
                Ran java = ran(Path.of("bin"), "ymxr-layout", in);
                Ran go = ran(built(), "ymxr-layout", in);
                String where = name + " cut at " + cut;
                assertTrue(java.exit() == 0 || java.said().contains(
                        "the record runs past the file's " + cut + " bytes"),
                        where + ": " + java.said());
                assertEquals(java.exit(), go.exit(), where + " exits the same: " + go.said());
                assertArrayEquals(java.out(), go.out(), where + " writes the same bytes");
                assertEquals(java.said(), go.said(), where + " reports the same");
            }
        }
    }

    /** A YM3 dump, which has no header: the two trees read the same
     *  fourteen vectors out of it. */
    @Test
    void aYm3DumpConvertsTheSameInBothTrees() throws Exception {
        byte[] data = new byte[4 + 14 * 40];
        System.arraycopy("YM3!".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 4);
        for (int r = 0; r < 14; r++) {
            for (int frame = 0; frame < 40; frame++) {
                data[4 + r * 40 + frame] = (byte) ((r * 7 + frame * 3) & 0x0F);
            }
        }
        byte[] tune = both("ym-to-ymxr", data, "-silent");
        assertTrue(tune.length > 0, "a YM3 dump converts");
        both("ym-to-ymxs", data, "-silent");
        both("ymxr-check", data, "-silent");
    }

    @Test
    void aWrongInputIsWrongInBothTrees() throws Exception {
        byte[] nonsense = "not a file of any of these".getBytes();
        for (String tool : List.of("ym-to-ymxs", "ym-to-ymxr", "ymxs-to-ymxr",
                "ymxs-to-sndh", "ymxs-to-prg", "ymxr-bind", "ymxr-sndh", "ymxr-prg",
                "ymxr-trace", "ymxr-layout")) {
            Ran java = ran(Path.of("bin"), tool, nonsense, "-silent");
            Ran go = ran(built(), tool, nonsense, "-silent");
            assertEquals(1, java.exit(), tool + " reads no such input: " + java.said());
            assertEquals(java.exit(), go.exit(), tool + " exits the same: " + go.said());
            assertArrayEquals(java.out(), go.out(), tool + " writes the same bytes");
        }
    }

    /**
     * A frame rate the word at 6 cannot carry, in both trees. A YMXS rate
     * reaches 2,147,483,647 (YMXS, SPEC.md 1.3) and the word reads 1 to
     * 65,535, so a writer reports the rate rather than writing its low
     * sixteen bits; {@code WriterTest} reads the bound, and this reads the
     * two trees against each other, which no dump reaches, a dump's rate
     * being a word.
     */
    @Test
    void aRateThePlayerCannotReadIsOneFaultInBothTrees() throws Exception {
        byte[] fast = ("{\"format\":\"ymxs\",\"version\":3,\"tunes\":[{\"title\":\"fast\","
                + "\"composer\":\"\",\"writer\":\"t\",\"rate\":70000,\"rows\":2,"
                + "\"repeat\":0,\"sources\":[],\"registers\":{\"r0\":[1,2]}}]}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Ran java = ran(Path.of("bin"), "ymxs-to-ymxr", fast, "-silent");
        assertEquals(1, java.exit(), "the writer reports the rate: " + java.said());
        assertTrue(java.said().contains("a frame rate of 70000"),
                "the line names the rate: " + java.said());
        Ran go = ran(built(), "ymxs-to-ymxr", fast, "-silent");
        assertEquals(java.exit(), go.exit(), "both trees exit 1: " + go.said());
        assertEquals(steady(java.said()), steady(go.said()), "both write one line");
        assertEquals(0, java.out().length, "a fault leaves standard output empty");
    }

    /**
     * A tune file whose table is outside it, one whose table offset has
     * bit 31 set, and one whose table is another variant, read in both
     * trees. SPEC.md 3.3.4 has a line for each; the Java tree took the
     * bytes through the offset before any check, so a file written wrong
     * ended in the exception the copy threw where the Go tree reported the
     * line, and it read the offset at 12 signed, so its line wrote the
     * offset negative. {@link #aMappedTuneFileReadsTheSameInBothTrees}
     * reads a file cut inside its source index.
     */
    @Test
    void aFileWrittenWrongIsOneLineInBothTrees() throws Exception {
        byte[] dump = Files.readAllBytes(dumps().get(0));
        byte[] file = ran(Path.of("bin"), "ym-to-ymxr", dump, "-silent").out();
        int tableAt = Tune.getLong(file, Tune.TABLE_AT);
        byte[] far = file.clone();
        Tune.putLong(far, Tune.TABLE_AT, file.length + 8);
        byte[] high = file.clone();
        Tune.putLong(high, Tune.TABLE_AT, 0x80000000 | tableAt);
        byte[] variant = file.clone();
        variant[tableAt + 3] = 1;
        for (byte[] wrong : List.of(far, high, variant)) {
            Ran java = ran(Path.of("bin"), "ymxr-trace", wrong, "-silent");
            Ran go = ran(built(), "ymxr-trace", wrong, "-silent");
            assertEquals(1, java.exit(), "the reader reports it: " + java.said());
            assertEquals(java.exit(), go.exit(), "both trees exit the same: " + go.said());
            assertEquals(steady(java.said()), steady(go.said()), "both write one line");
            assertEquals(0, java.out().length, "a file written wrong has no record");
        }
    }

    /**
     * {@code bin/ymxr-set} against the calls it replaces. The script
     * converts each dump, puts the tune files in one multi file, makes an
     * SNDH file around them and writes the program of the stub in front
     * of that, running the Go tools it builds (tools.md 16.6). The same
     * four calls through the Java tools write the same program, so the
     * set the script writes is the set README.md's four calls write.
     */
    @Test
    void theSetScriptWritesWhatItsFourCallsWrite() throws Exception {
        List<Path> two = dumps().subList(0, 2);
        Ran script = ran(Path.of("bin"), "ymxr-set", new byte[0], "-silent",
                two.get(0).toString(), two.get(1).toString());
        assertEquals(0, script.exit(), "the script runs: " + script.said());

        List<byte[]> tunes = new ArrayList<>();
        for (Path dump : two) {
            tunes.add(both("ym-to-ymxr", Files.readAllBytes(dump), "-silent"));
        }
        byte[] sndh = both("ymxr-sndh", multiOf(tunes, "-silent"), "-silent");
        byte[] program = both("ymxr-prg", sndh, "-silent");
        assertArrayEquals(program, script.out(),
                "the script writes the program the four calls write");
    }
}
