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
 * The WebAssembly module against the executables (doc/tools.md 20.5): the
 * twelve tools built for WASI as ymxr.wasm, run under Node through the host
 * the module ships with ({@code wasm/ymxr-node.mjs}), write the bytes, the
 * report and the exit code the Go executables write for the same call.
 *
 * <p>Skipped where go or node is off the path; bin/suite requires both.
 */
final class WasmTest {

    /** The Go executables and the module, built once for every test here. */
    private static @Nullable Path built;

    @BeforeAll
    static void buildTheModule() throws IOException, InterruptedException {
        Assumptions.assumeTrue(onThePath("go", "version"), "no go on the path");
        Assumptions.assumeTrue(onThePath("node", "--version"), "no node on the path");
        Path into = Files.createTempDirectory("ymxr-wasm");
        build(into, List.of("go", "build", "-o", into + "/", "./cmd/..."), List.of());
        build(into, List.of("go", "build", "-o", into.resolve("ymxr.wasm").toString(), "./wasm"),
                List.of("GOOS=wasip1", "GOARCH=wasm"));
        built = into;
    }

    private static void build(Path into, List<String> command, List<String> env)
            throws IOException, InterruptedException {
        ProcessBuilder go = new ProcessBuilder(command).directory(Path.of("go").toFile())
                .redirectErrorStream(true);
        for (String one : env) {
            go.environment().put(one.substring(0, one.indexOf('=')),
                    one.substring(one.indexOf('=') + 1));
        }
        Process ran = go.start();
        String said = new String(ran.getInputStream().readAllBytes());
        assertEquals(0, ran.waitFor(), String.join(" ", command) + ": " + said);
    }

    private static boolean onThePath(String tool, String flag) {
        try {
            return new ProcessBuilder(tool, flag).redirectErrorStream(true)
                    .start().waitFor() == 0;
        } catch (IOException | InterruptedException no) {
            return false;
        }
    }

    private static Path built() {
        Path where = built;
        if (where == null) {
            throw new IllegalStateException("the module is not built");
        }
        return where;
    }

    /** What one run came to. */
    private record Ran(int exit, byte[] out, String said) {
    }

    /** One call, standard input from {@code in}. */
    private static Ran ran(List<String> command, byte[] in) throws IOException, InterruptedException {
        Path input = Files.createTempFile("in", ".bin");
        Files.write(input, in);
        ProcessBuilder call = new ProcessBuilder(command).redirectInput(input.toFile());
        call.environment().put("YMXR_WASM", built().resolve("ymxr.wasm").toString());
        Process ran = call.start();
        byte[] out = ran.getInputStream().readAllBytes();
        String said = new String(ran.getErrorStream().readAllBytes());
        return new Ran(ran.waitFor(), out, said);
    }

    /** {@code tool} as the executable runs it. */
    private static Ran executable(String tool, byte[] in, String... flags)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(built().resolve(tool).toString()));
        command.addAll(List.of(flags));
        return ran(command, in);
    }

    /** {@code tool} as the module runs it under Node. */
    private static Ran module(String tool, byte[] in, String... flags)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("node", "wasm/ymxr-node.mjs", tool));
        command.addAll(List.of(flags));
        return ran(command, in);
    }

    /** A progress line, which the clock spaces (ParityTest). */
    private static final Pattern PROGRESS = Pattern.compile(
            "^ {2}(packing the columns|read) \\d+ of \\d+ \\(\\d+%\\)$");

    private static String steady(String said) {
        return said.lines().filter(one -> !PROGRESS.matcher(one).matches())
                .reduce("", (a, b) -> a + b + "\n");
    }

    /** The same call through the executable and the module, which must
     *  come to the same bytes, report and exit. */
    private static byte[] both(String tool, byte[] in, String... flags) throws Exception {
        Ran native_ = executable(tool, in, flags);
        Ran wasm = module(tool, in, flags);
        assertEquals(native_.exit(), wasm.exit(), tool + " exits the same: " + native_.said()
                + " | " + wasm.said());
        assertArrayEquals(native_.out(), wasm.out(), tool + " writes the same bytes");
        assertEquals(steady(native_.said()), steady(wasm.said()), tool + " reports the same");
        return native_.out();
    }

    private static List<Path> dumps() throws IOException {
        try (Stream<Path> at = Files.list(Path.of("ym/test"))) {
            return at.filter(one -> one.toString().endsWith(".ym")).sorted().toList();
        }
    }

    /** The calls a page makes to write a file of a structure, every dump's
     *  structure through each. */
    @Test
    void everyDumpConvertsTheSameAsTheExecutables() throws Exception {
        for (Path dump : dumps()) {
            byte[] bytes = Files.readAllBytes(dump);
            byte[] structure = both("ym-to-ymxs", bytes, "-silent");
            assertTrue(structure.length > 0, dump + " converts");
            both("ym-to-ymxr", bytes, "-silent");
            both("ymxs-to-ymxr", structure, "-silent");
            both("ymxs-to-sndh", structure, "-silent");
            both("ymxs-to-prg", structure, "-silent");
        }
    }

    /** One dump through the rest of the twelve, with the report on. */
    @Test
    void theOtherToolsRunTheSameAsTheExecutables() throws Exception {
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Turrican - world 4-3.ym"));
        byte[] structure = both("ym-to-ymxs", dump);
        both("ymxs-to-sndh", structure, "-vbl", "-tA title");
        both("ymxs-to-prg", structure, "-r100", "-perf");
        byte[] tune = both("ym-to-ymxr", dump, "-k1", "-m480");
        both("ymxr-bind", tune);
        byte[] sndh = both("ymxr-sndh", tune);
        both("ymxr-prg", sndh);
        both("ymxr-layout", sndh);
        both("ymxr-trace", tune, "-r50");
        both("ymxr-check", dump);
    }

    /** The two tools that read the files their arguments name: the host
     *  reads them into the directory the module opens as "/". */
    @Test
    void theFilesAToolReadsByNameReadTheSame() throws Exception {
        Path at = Files.createTempDirectory("ymxr-named");
        Files.write(at.resolve("a.ymxr"), executable("ym-to-ymxr",
                Files.readAllBytes(Path.of("ym/test/DBA 2.ym")), "-silent").out());
        Files.write(at.resolve("b.ymxr"), executable("ym-to-ymxr",
                Files.readAllBytes(Path.of("ym/test/capture.ym")), "-silent").out());
        Files.copy(Path.of("ym/test/DBA 2.ym"), at.resolve("DBA 2.ym"));
        both("ymxr-multi", new byte[0], "-silent", at.resolve("a.ymxr").toString(),
                at.resolve("b.ymxr").toString());
        both("ymxr-check", new byte[0], at.toString());
        // a file that is absent is the system's message, which the WASI
        // runtime spells with a capital (doc/tools.md 20.5)
        String absent = at.resolve("absent.ym").toString();
        Ran native_ = executable("ymxr-check", new byte[0], absent);
        Ran wasm = module("ymxr-check", new byte[0], absent);
        assertEquals(native_.exit(), wasm.exit(), wasm.said());
        assertEquals(native_.said().toLowerCase(), wasm.said().toLowerCase());
    }

    /** A wrong call: a flag outside the tool's is the executable's line,
     *  and a first argument outside the twelve names them. */
    @Test
    void aWrongCallIsTheExecutablesLine() throws Exception {
        both("ymxs-to-sndh", new byte[0], "-z");
        Ran none = module("ymxs-to-wav", new byte[0]);
        assertEquals(2, none.exit(), none.said());
        assertEquals("ymxr: the first argument names a tool: ym-to-ymxr, ym-to-ymxs,"
                + " ymxr-bind, ymxr-check, ymxr-layout, ymxr-multi, ymxr-prg, ymxr-sndh,"
                + " ymxr-trace, ymxs-to-prg, ymxs-to-sndh, ymxs-to-ymxr\n", none.said());
    }
}
