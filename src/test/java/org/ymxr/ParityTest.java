package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * been built.
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
        Ran java = ran(Path.of("bin"), tool, in, flags);
        Ran go = ran(built(), tool, in, flags);
        assertEquals(java.exit(), go.exit(), tool + " exits the same: " + java.said()
                + " | " + go.said());
        assertArrayEquals(java.out(), go.out(), tool + " writes the same bytes");
        assertEquals(steady(java.said()), steady(go.said()), tool + " reports the same");
        return java.out();
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
        both("ymxr-prg", both("ymxr-sndh", tune, "-silent", "-tOne"), "-silent", "-r2000");
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
    @Test
    void theVerboseReportIsTheSameInBothTrees() throws Exception {
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Turrican - world 4-3.ym"));
        byte[] structure = both("ym-to-ymxs", dump);
        byte[] tune = both("ym-to-ymxr", dump);
        both("ymxs-to-ymxr", structure);
        both("ymxs-to-sndh", structure);
        both("ymxs-to-prg", structure);
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
     * once, so a set that agrees on those shares one image and one that
     * does not is split. Both cases are here: two tune files packed
     * through one ring, and two packed through two.
     */
    @Test
    void aSetOfSubtunesIsReportedTheSameInBothTrees() throws Exception {
        byte[] dump = Files.readAllBytes(dumps().get(0));
        byte[] wide = both("ym-to-ymxr", dump, "-silent", "-m900");
        byte[] narrow = both("ym-to-ymxr", dump, "-silent", "-m300");
        byte[] shared = multiOf(List.of(wide, wide));
        byte[] split = multiOf(List.of(wide, narrow));
        // One image for the two that agree, and two for the two that do
        // not: the rows that say so are in the report alone.
        both("ymxr-prg", both("ymxr-sndh", shared, "-perf"));
        both("ymxr-prg", both("ymxr-sndh", split));
    }

    /** Every tool of the eleven (tools.md 2), for a check that runs them
     *  all. `ymxr-check` stood outside this list while the comment over it
     *  read eleven. */
    private static final List<String> EVERY_TOOL = List.of("ym-to-ymxs",
            "ym-to-ymxr", "ymxs-to-ymxr", "ymxs-to-sndh", "ymxs-to-prg",
            "ymxr-bind", "ymxr-check", "ymxr-multi", "ymxr-sndh", "ymxr-prg",
            "ymxr-trace");

    /**
     * The tools this runs against the tools tools.md 2 lists. The list
     * here named ten while the comment over it read eleven, and the tool
     * left out, {@code ymxr-check}, stood outside the two checks below.
     */
    @Test
    void everyToolTheDocumentListsIsRunHere() throws Exception {
        String tools = Files.readString(Path.of("doc", "tools.md"));
        int at = tools.indexOf("## 2. The eleven tools");
        assertTrue(at >= 0, "tools.md lists no tools");
        String table = tools.substring(at, tools.indexOf("\n\n", tools.indexOf("|", at)));
        List<String> listed = new ArrayList<>();
        Matcher row = Pattern.compile("^\\| `([a-z0-9-]+)` \\|", Pattern.MULTILINE)
                .matcher(table);
        while (row.find()) {
            listed.add(row.group(1));
        }
        assertEquals(11, listed.size(), "tools.md 2 lists " + listed);
        assertEquals(new TreeSet<>(listed), new TreeSet<>(EVERY_TOOL),
                "a tool of tools.md 2 is run here, and one run here is listed there");
    }

    @Test
    void anEmptyInputIsOneFaultInBothTrees() throws Exception {
        // The three tools whose input is YMXS's JSON form stood outside
        // this check: an empty text parsed to an absent format in that
        // tree's Java reader and to a text that is not JSON in its Go one,
        // and odipar/YMXS#58 reports the second in both. This build reads
        // YMXS 0.4.4, where the second stands, so the three read one here now.
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

    @Test
    void aWrongInputIsWrongInBothTrees() throws Exception {
        byte[] nonsense = "not a file of any of these".getBytes();
        for (String tool : List.of("ym-to-ymxs", "ym-to-ymxr", "ymxs-to-ymxr",
                "ymxs-to-sndh", "ymxs-to-prg", "ymxr-bind", "ymxr-sndh", "ymxr-prg",
                "ymxr-trace")) {
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
        assertEquals(0, java.out().length, "a fault writes no bytes");
    }

    /**
     * A tune file whose table stands outside it, and one whose table is
     * another variant, read in both trees. SPEC.md 3.3.4 has a line for
     * each; the Java tree took the bytes through the offset before any
     * check, so a file written wrong ended in the exception the copy threw
     * where the Go tree reported the line.
     */
    @Test
    void aFileWrittenWrongIsOneLineInBothTrees() throws Exception {
        byte[] dump = Files.readAllBytes(dumps().get(0));
        byte[] file = ran(Path.of("bin"), "ym-to-ymxr", dump, "-silent").out();
        int tableAt = Tune.getLong(file, Tune.TABLE_AT);
        byte[] far = file.clone();
        Tune.putLong(far, Tune.TABLE_AT, file.length + 8);
        byte[] variant = file.clone();
        variant[tableAt + 3] = 1;
        for (byte[] wrong : List.of(far, variant)) {
            Ran java = ran(Path.of("bin"), "ymxr-trace", wrong, "-silent");
            Ran go = ran(built(), "ymxr-trace", wrong, "-silent");
            assertEquals(1, java.exit(), "the reader reports it: " + java.said());
            assertEquals(java.exit(), go.exit(), "both trees exit the same: " + go.said());
            assertEquals(steady(java.said()), steady(go.said()), "both write one line");
            assertEquals(0, java.out().length, "a file written wrong has no record");
        }
    }

    /**
     * {@code bin/ymxr-set} against the calls it stands for. The script
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
