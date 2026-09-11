package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    private static List<Path> files() throws IOException {
        try (Stream<Path> at = Files.list(Path.of("ymx/test"))) {
            return at.filter(one -> one.toString().endsWith(".ymx")).sorted().toList();
        }
    }

    /** Whether YMX_DUMP names an executable, which the Java tools run to
     *  read a .ymx and the Go tools do not. */
    private static boolean dumpIsThere() {
        String named = System.getenv("YMX_DUMP");
        return named != null && Files.isExecutable(Path.of(named));
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

    /**
     * A .ymx converted in both trees.
     *
     * <p>The two read the file by separate routes: a Go tool decodes it
     * with YMX's reader, which the tree imports as a module, and a Java
     * tool runs YMX's ymx-dump and reads the values it prints. The routes
     * meet here, on the output and on the report.
     *
     * <p>Skipped where YMX_DUMP names no executable, which the Java tools
     * need.
     */
    @Test
    void everyYmxFileConvertsTheSameInBothTrees() throws Exception {
        Assumptions.assumeTrue(dumpIsThere(), "YMX_DUMP names no ymx-dump");
        for (Path file : files()) {
            byte[] bytes = Files.readAllBytes(file);
            byte[] structure = both("ymx-to-ymxs", bytes, "-silent");
            assertTrue(structure.length > 0, file + " converts");
            byte[] tune = both("ymx-to-ymxr", bytes, "-silent");
            assertArrayEquals(tune, both("ymxs-to-ymxr", structure, "-silent"),
                    file + ": the one call and the two stages write one file");
        }
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
     * the report to what the tool wrote and its notes. The figures a reader
     * of a run reads are in the rest of it and nowhere else: the core's
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
}
