package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * the core and the stub, so a caller who takes either has the same file at
 * every step.
 *
 * <p>The tools are run as a caller runs them: the input on standard input,
 * the output on standard output. What is compared is standard output and
 * the exit. A report is prose, and the progress lines a long run prints
 * are spaced by the clock, so a report is compared with those lines taken
 * out.
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

    /** A report with the lines the clock spaces taken out, which two trees
     *  reach at different rows. */
    private static String steady(String said) {
        return said.lines().filter(one -> !one.contains("packing the columns")
                && !one.contains("read ")).reduce("", (a, b) -> a + b + "\n");
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
