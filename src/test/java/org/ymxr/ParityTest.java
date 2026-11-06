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
     * Java/Go parity for mapping conversion: fades, identity
     * restoration, padding, counted byte keys and identity-only
     * effects. Compare file, record, check and report; lookup use
     * requires version 10, otherwise version 9.
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
        // The identity-only effect has a constant 0 mapping column in
        // both encoders.
        byte[] beside = both("ymxs-to-ymxr", json(YmxsTest.identityBeside()), "-silent");
        byte[] column = TuneFile.read(beside).table().column(Columns.MAPPING + 1);
        assertArrayEquals(new byte[column.length], column, "effect 1's mapping column");
    }

    /**
     * Both encoders reproduce the mapped conformance file and reference
     * record byte for byte. SNDH conversion selects deferred-start and
     * lookup support (BINARIES.md 2.1).
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
     * The list of the pages a tune maps through, after the bound tune's
     * transformation block (BINARIES.md 1.1, SPEC.md 3.4.4): the kit's
     * mapped tune, the fade and a mapped tune with deferred starts bound
     * the same in both trees, the kit's eleven pages six of them on effect
     * 0, and its SNDH file with the page area after the core, the field
     * at 36 its offset and the subtune table past it (3.1).
     */
    @Test
    void theListOfPagesAndTheAreaAreTheSameInBothTrees() throws Exception {
        byte[] kit = Files.readAllBytes(ConformanceTest.TUNES.resolve("mapped.ymxr"));
        byte[] bound = both("ymxr-bind", kit, "-silent");
        int block = Tune.getLong(bound, Bound.TRANSFORMATIONS_AT);
        int pages = Bound.pages(bound, block);
        assertEquals(11, pages, "the kit's pages");
        byte[] list = Pages.of(TuneFile.read(kit));
        int first = 0;
        for (int k = 0; k < pages; k++) {
            first += list[Pages.TAG.length + 1 + 3 * k] == 0 ? 1 : 0;
        }
        assertEquals(6, first, "the kit's pages on effect 0");
        for (byte[] tune : List.of(both("ymxs-to-ymxr", json(YmxsTest.fade()), "-silent"),
                BuiltTunes.mapped().file())) {
            both("ymxr-bind", tune, "-silent");
        }
        byte[] sndh = both("ymxr-sndh", kit, "-silent", "-carried");
        int core = Sndh.even(Tune.getWord(sndh, 2) + 2);
        byte[] carried = Binaries.core(false, false, true, true, true);
        int length = carried.length;
        int area = Sndh.area(carried, Bound.of(List.of(kit)));
        assertEquals(Sndh.even(length), Tune.getLong(sndh, core + Sndh.CORE_PAGES_AT),
                "the page area right after the core");
        // a table of 11 bytes, the transformations, for each of the 4
        // pairs of an effect and a form, and 256 bytes a page
        assertEquals(4 * 11 + 256 * pages + Sndh.PAGE_SPAN, area, "the kit's page area");
        assertEquals(Sndh.even(Sndh.even(length) + area),
                Tune.getLong(sndh, core + Sndh.CORE_TABLE_AT), "the subtune table past it");
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

    /**
     * Compare Java/Go output for the fade through binding, layout,
     * multi, SNDH and PRG tools, including reports. A mixed fade/dump
     * set has separate thirty- and thirty-four-column images and
     * requires lookup support (BINARIES.md 1.4, 2.1).
     */
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
     * Both tools measure source reach from descriptor field 26. The
     * shorter mapped source fits program-counter reads; the longer
     * requires absolute reads, and explicit -pcrel reports the same
     * error (BINARIES.md 2.1).
     */
    @Test
    void aMappedFileIsMeasuredFromTheFirstHandlerInBothTrees() throws Exception {
        byte[] within = both("ymxr-sndh", BinariesTest.longMapped(20000), "-carried", "-silent");
        assertEquals(Sndh.CORE_PCREL | Sndh.CORE_MAP, Tune.getWord(within,
                Sndh.even(Tune.getWord(within, 2) + 2) + Sndh.CORE_FLAGS_AT));
        byte[] far = BinariesTest.longMapped(30000);
        Ran chosen = same("ymxr-sndh", far, "-carried");
        assertTrue(steady(chosen.said()).contains("ticks that read a row through an absolute"
                + " address: the tunes end past the 32767 bytes a displacement reaches; the"
                + " lookup in: a subtune maps a value\n"), chosen.said());
        assertEquals(Sndh.CORE_MAP, Tune.getWord(chosen.out(),
                Sndh.even(Tune.getWord(chosen.out(), 2) + 2) + Sndh.CORE_FLAGS_AT));
        Ran asked = same("ymxr-sndh", far, "-carried", "-pcrel", "-silent");
        assertEquals(1, asked.exit(), asked.said());
        assertTrue(asked.said().matches("ymxr-sndh: the tunes end \\d+ bytes past the core's"
                + " first handler, at \\d+, and a tick that reads a row through the program"
                + " counter reaches 32767\n"), asked.said());
    }

    /**
     * Offsets inside the transformation index are accepted and copied
     * through the index end. Layout also reads a negative bound block
     * offset as signed. Compare Java and Go files and records (SPEC.md
     * 3.3.4; BINARIES.md 1.1, 1.2).
     */
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
     * Compare every YMXS conformance tune through both encoders: file
     * or error, exit status, record and check. YMXR rejects the
     * transformation fixture's lookups before the last setVoiceB value
     * (SPEC.md 6.5).
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

    /**
     * Unsupported mappings report the original structure row, the same
     * line and exit 1 in both encoders (SPEC.md 6.5).
     */
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
     * Compare version 10 files, records, malformed-file errors and
     * mapping checks in Java and Go. Exercise multi repacking and
     * layout, then deferred/lookup SNDH and PRG output, including the
     * raster-monitor core and handler-relative source reach
     * (BINARIES.md 2.1).
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

    /**
     * A core linked for the set (doc/LINKER.md) is the same in both trees,
     * and so is the report of its blocks: each tune of the conformance
     * kit, a multi file of two that need other blocks, and each switch on
     * one tune. The three tools link by default, and -carried writes
     * the full core.
     */
    @Test
    void aLinkedCoreIsTheSameInBothTrees() throws Exception {
        Path kit = Path.of("doc/conformance/tunes");
        List<Path> tunes;
        try (Stream<Path> at = Files.list(kit)) {
            tunes = at.filter(one -> one.toString().endsWith(".ymxr")
                    && !one.getFileName().toString().equals("wrong-version.ymxr")).sorted()
                    .toList();
        }
        for (Path tune : tunes) {
            same("ymxr-sndh", Files.readAllBytes(tune));
        }
        byte[] voices = Files.readAllBytes(kit.resolve("voices.ymxr"));
        for (String[] flags : new String[][] {{"-perf"}, {"-lean"}, {"-abs"},
                {"-perf", "-lean", "-abs"}}) {
            same("ymxr-sndh", voices, flags);
        }
        Ran set = ran(Path.of("bin"), "ymxr-multi", new byte[0], "-silent",
                kit.resolve("counted.ymxr").toString(), kit.resolve("voices.ymxr").toString());
        assertEquals(0, set.exit(), set.said());
        same("ymxr-sndh", set.out());
        byte[] mapped = json(ConformanceTest.mapped());
        for (String tool : List.of("ymxr-sndh", "ymxs-to-sndh", "ymxs-to-prg")) {
            byte[] input = tool.equals("ymxr-sndh") ? voices : mapped;
            byte[] linked = both(tool, input, "-silent");
            byte[] carried = both(tool, input, "-silent", "-carried");
            assertTrue(linked.length < carried.length, tool + " links by default");
            assertArrayEquals(linked, both(tool, input, "-silent", "-link"),
                    tool + " accepts the former flag");
        }
        byte[] linked = both("ymxs-to-sndh", mapped, "-silent");
        byte[] direct = both("ymxr-sndh", Files.readAllBytes(kit.resolve("mapped.ymxr")),
                "-silent");
        int at = Sndh.even(Tune.getWord(linked, 2) + 2);
        int from = Sndh.even(Tune.getWord(direct, 2) + 2);
        // the core and the page area, the patched longs at 28 to 40 aside
        int table = Tune.getLong(linked, at + Sndh.CORE_TABLE_AT);
        assertEquals(table, Tune.getLong(direct, from + Sndh.CORE_TABLE_AT),
                "the subtune table follows the same linked core");
        assertArrayEquals(Arrays.copyOfRange(direct, from, from + Sndh.CORE_TABLE_AT),
                Arrays.copyOfRange(linked, at, at + Sndh.CORE_TABLE_AT));
        assertArrayEquals(Arrays.copyOfRange(direct, from + 40, from + table),
                Arrays.copyOfRange(linked, at + 40, at + table),
                "ymxs-to-sndh writes the core ymxr-sndh writes");
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

    @Test
    void theClockFlagsForMultipleSubtunesReadTheSameInBothTrees() throws Exception {
        byte[] fifty = Files.readAllBytes(Path.of("doc/conformance/tunes/plays-once.ymxr"));
        byte[] sixty = fifty.clone();
        Tune.putWord(sixty, Tune.FRAME_RATE_AT, 60);
        byte[] allFifty = multiOf(List.of(fifty, fifty), "-silent");
        byte[] vbl = both("ymxr-sndh", allFifty, "-vbl");
        byte[] timer = both("ymxr-sndh", allFifty, "-tc");
        assertEquals("!V", Prg.tags(vbl).clock());
        assertEquals("TC", Prg.tags(timer).clock());
        assertArrayEquals(timer, both("ymxr-sndh", allFifty));
        byte[] watched = both("ymxr-sndh", allFifty, "-perf");
        assertEquals("!V", Prg.tags(watched).clock());
        assertEquals(Prg.FLAG_VBL, Tune.getWord(both("ymxr-prg", watched),
                Prg.HEADER + Prg.STUB_FLAGS_AT));
        assertEquals("TC", Prg.tags(both("ymxr-sndh", allFifty, "-perf", "-tc")).clock());
        assertEquals(Prg.FLAG_VBL, Tune.getWord(both("ymxr-prg", timer, "-vbl"),
                Prg.HEADER + Prg.STUB_FLAGS_AT));
        assertEquals(0, Tune.getWord(both("ymxr-prg", vbl, "-tc"),
                Prg.HEADER + Prg.STUB_FLAGS_AT));

        for (List<byte[]> tunes : List.of(List.of(fifty, sixty), List.of(sixty, sixty))) {
            byte[] multi = multiOf(tunes, "-silent");
            byte[] sndh = both("ymxr-sndh", multi, "-tc");
            assertEquals("TC", Prg.tags(sndh).clock());
            assertArrayEquals(sndh, both("ymxr-sndh", multi));
            for (String[] flags : List.of(new String[] {"-perf"}, new String[] {"-perf", "-tc"})) {
                Ran perf = same("ymxr-sndh", multi, flags);
                assertEquals(1, perf.exit(), perf.said());
                assertTrue(perf.said().contains("-perf requires every subtune in a set"
                        + " to play at 50 Hz"), perf.said());
            }
            Ran writer = same("ymxr-sndh", multi, "-vbl");
            Ran wrapper = same("ymxr-prg", sndh, "-vbl");
            for (Ran run : List.of(writer, wrapper)) {
                assertEquals(1, run.exit(), run.said());
                assertTrue(run.said().contains("-vbl requires every subtune in a set"
                        + " to play at 50 Hz"), run.said());
            }
        }
    }

    @Test
    void aMixedRateStructurePlaysAtBothRatesInBothTrees() throws Exception {
        org.ymxs.YMXS.Tune fifty = YmxsTest.fade();
        org.ymxs.YMXS.Tune sixty = new org.ymxs.YMXS.Tune("Sixty", fifty.composer(),
                fifty.writer(), 60, fifty.table());
        byte[] json = org.ymxs.Text.write(new org.ymxs.YMXS.Multi(List.of(fifty, sixty)))
                .getBytes(StandardCharsets.UTF_8);
        byte[] packed = both("ymxs-to-ymxr", json, "-silent");
        byte[] sndh = both("ymxr-sndh", packed, "-silent");
        assertEquals(300, Prg.tags(sndh).rate());
        byte[] direct = both("ymxs-to-sndh", json, "-silent");
        assertEquals(300, Prg.tags(direct).rate());
        byte[] program = both("ymxr-prg", sndh, "-r20");
        assertEquals(Prg.MIXED_STUB_VERSION, Tune.getWord(program, Prg.HEADER + Prg.STUB_VERSION_AT));
        assertArrayEquals(both("ymxr-prg", direct, "-silent", "-r20"),
                both("ymxs-to-prg", json, "-silent", "-r20"));
        both("ymxr-layout", sndh);
        both("ymxr-layout", program);
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
        List<byte[]> tunes = new ArrayList<>();
        for (Path dump : two) {
            tunes.add(both("ym-to-ymxr", Files.readAllBytes(dump), "-silent"));
        }
        byte[] multi = multiOf(tunes, "-silent");
        for (List<String> flags : List.of(List.of("-silent"), List.of("-silent", "-carried"))) {
            List<String> args = new ArrayList<>(flags);
            two.forEach(dump -> args.add(dump.toString()));
            Ran script = ran(Path.of("bin"), "ymxr-set", new byte[0], args.toArray(String[]::new));
            assertEquals(0, script.exit(), "the script runs: " + script.said());
            byte[] sndh = both("ymxr-sndh", multi, flags.toArray(String[]::new));
            assertArrayEquals(both("ymxr-prg", sndh, "-silent"), script.out(),
                    "the script writes the program the four calls write");
        }
    }
}
