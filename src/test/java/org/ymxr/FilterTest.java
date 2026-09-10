package org.ymxr;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The filters run as programs (doc/tools.md, The structure): the file on
 * standard output, the report on standard error, and the exit the call
 * ends in.
 *
 * <p>The exits are {@code org.ymxs.tool.Tool}'s: 0 the tool completed, 1
 * the input is wrong, 2 the call is wrong. A fault printed as a stack
 * trace is one a caller cannot read, so no run here prints one, and
 * {@code -silent} leaves standard output as it was.
 */
final class FilterTest {

    /** The smallest dump under {@code ym/test}, so a run is short. */
    private static final Path DUMP = Path.of("ym/test/Circus Attractions  2.ym");

    /** One run: what each stream carried, and the exit. */
    private record Ran(byte[] out, String err, int exit) {
    }

    /** {@code tool} run on {@code in}, in this JVM's class path. Each
     *  stream is drained as it fills, so neither buffer stops the run. */
    private Ran ran(String tool, byte[] in, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElse("java"),
                "-cp", System.getProperty("java.class.path"), "org.ymxr." + tool));
        command.addAll(List.of(args));
        Process ran = new ProcessBuilder(command).start();
        byte[][] said = new byte[2][];
        Thread reading = Thread.ofVirtual().start(() -> said[0] = drained(ran.getErrorStream()));
        try (OutputStream to = ran.getOutputStream()) {
            to.write(in);
        } catch (IOException ended) {
            // A tool that ends before it reads its input closes the pipe,
            // which is the run this call is here to read.
        }
        said[1] = drained(ran.getInputStream());
        int exit = ran.waitFor();
        reading.join();
        return new Ran(said[1], new String(said[0], UTF_8), exit);
    }

    private static byte[] drained(InputStream from) {
        try (InputStream in = from) {
            return in.readAllBytes();
        } catch (IOException failed) {
            return new byte[0];
        }
    }

    private byte[] structure() throws Exception {
        return ran("YmToYmxs", Files.readAllBytes(DUMP), "-silent").out();
    }

    @Test
    void theFileGoesOutAndTheReportGoesToStandardError() throws Exception {
        Ran loud = ran("YmToYmxs", Files.readAllBytes(DUMP));
        assertEquals(0, loud.exit(), () -> "the dump converts: " + loud.err());
        assertEquals('{', loud.out()[0], "the structure is JSON on standard output");
        assertTrue(loud.err().contains("ym-to-ymxs: YM5!"),
                () -> "the report names the tool and what it read: " + loud.err());
        Ran quiet = ran("YmToYmxs", Files.readAllBytes(DUMP), "-silent");
        assertArrayEquals(loud.out(), quiet.out(), "-silent leaves standard output as it was");
        assertFalse(quiet.err().contains("ym-to-ymxs:"), () -> "-silent reports no progress: "
                + quiet.err());
    }

    @Test
    void eachStageWritesItsOwnFileAndNothingElse() throws Exception {
        byte[] structure = structure();
        for (String tool : List.of("YmxsToYmxr", "YmxsToSndh", "YmxsToPrg")) {
            Ran loud = ran(tool, structure);
            assertEquals(0, loud.exit(), () -> tool + " converts: " + loud.err());
            assertTrue(loud.out().length > 0, () -> tool + " writes its file");
            assertTrue(loud.err().contains("the table:"),
                    () -> tool + " reports the packing: " + loud.err());
            Ran quiet = ran(tool, structure, "-silent");
            assertArrayEquals(loud.out(), quiet.out(), tool + " under -silent writes the same");
            assertFalse(quiet.err().contains("the table:"),
                    () -> tool + " under -silent reports no progress: " + quiet.err());
        }
    }

    /** Every tool that reads one file on standard input, and the name it
     *  reports under. */
    private static final Map<String, String> FILTERS = new LinkedHashMap<>(Map.of(
            "YmToYmxs", "ym-to-ymxs", "YmxsToYmxr", "ymxs-to-ymxr",
            "YmxsToSndh", "ymxs-to-sndh", "YmxsToPrg", "ymxs-to-prg",
            "YmToYmxr", "ym-to-ymxr", "Bind", "ymxr-bind", "Sndh", "ymxr-sndh",
            "Prg", "ymxr-prg", "Trace", "ymxr-trace"));

    @Test
    void anInputThisCannotReadExitsOne() throws Exception {
        byte[] nonsense = "not a file of any of these".getBytes(UTF_8);
        for (String tool : FILTERS.keySet()) {
            for (byte[] in : List.of(nonsense, new byte[0])) {
                Ran fault = ran(tool, in);
                assertEquals(1, fault.exit(), () -> tool + " on a wrong input exits 1: "
                        + fault.err());
                assertEquals(0, fault.out().length, () -> tool + " writes no file");
            }
        }
    }

    @Test
    void theChainOfFiltersRunsFromTheDumpToTheProgram() throws Exception {
        byte[] dump = Files.readAllBytes(DUMP);
        Ran tune = ran("YmToYmxr", dump, "-silent");
        assertEquals(0, tune.exit(), () -> "the dump converts: " + tune.err());
        assertEquals('Y', tune.out()[0], "a tune file opens with YMXR");
        Ran bound = ran("Bind", tune.out(), "-silent");
        assertEquals(0, bound.exit(), () -> "the tune file binds: " + bound.err());
        Ran sndh = ran("Sndh", tune.out(), "-silent", "-tThe title");
        assertEquals(0, sndh.exit(), () -> "the tune file goes behind a core: " + sndh.err());
        Ran prg = ran("Prg", sndh.out(), "-silent");
        assertEquals(0, prg.exit(), () -> "the SNDH file goes into a program: " + prg.err());
        assertTrue(prg.out().length > sndh.out().length, "a program is the SNDH file and more");
        Ran trace = ran("Trace", tune.out(), "-silent", "-r4");
        assertEquals(0, trace.exit(), () -> "the tune file records: " + trace.err());
        assertEquals('{', trace.out()[0], "a record is one JSON line a row");
        Ran check = ran("Check", dump, "-silent");
        assertEquals(0, check.exit(), () -> "the dump replays: " + check.err());
        assertTrue(new String(check.out(), UTF_8).contains("replays to its dump"),
                () -> "the verdict is on standard output: " + new String(check.out(), UTF_8));
    }

    @Test
    void aMultiOfSeveralTunesIsAMultiFileAndItsSubtunes() throws Exception {
        Ran made = ran("YmxsToYmxr", multi(structure()), "-silent");
        assertEquals(0, made.exit(), () -> "a multi of two converts: " + made.err());
        assertEquals("YMXM", new String(made.out(), 0, 4, UTF_8),
                "several tunes are one multi file");
        Ran sndh = ran("Sndh", made.out());
        assertEquals(0, sndh.exit(), () -> "the multi file goes behind a core: " + sndh.err());
        assertTrue(sndh.err().contains("2 subtunes"),
                () -> "its tunes are the subtunes: " + sndh.err());
    }

    @Test
    void aCallThisCannotReadExitsTwo() throws Exception {
        byte[] structure = structure();
        List<Ran> calls = List.of(
                ran("YmToYmxs", structure, "-zz"),
                ran("YmToYmxs", structure, "tune.ym"),
                ran("YmxsToYmxr", structure, "-zz"),
                ran("YmxsToYmxr", structure, "tune.ymxs"),
                ran("YmxsToYmxr", structure, "-kx"),
                ran("YmxsToYmxr", structure, "-tTitle"),
                ran("YmxsToSndh", structure, "-r0"),
                ran("YmxsToSndh", structure, "-copiesx"),
                ran("YmxsToPrg", structure, "-rx"));
        for (Ran call : calls) {
            assertEquals(2, call.exit(), () -> "a wrong call exits 2: " + call.err());
            assertEquals(0, call.out().length, "and writes no file");
        }
    }

    @Test
    void noFaultReachesTheCallerAsAStackTrace() throws Exception {
        byte[] nonsense = "not a file of any of these".getBytes(UTF_8);
        for (Map.Entry<String, String> tool : FILTERS.entrySet()) {
            for (Ran fault : List.of(ran(tool.getKey(), nonsense),
                    ran(tool.getKey(), new byte[0], "-zz"))) {
                assertFalse(fault.err().contains("Exception in thread"),
                        () -> tool.getValue() + " reports its fault as a line: " + fault.err());
                assertFalse(fault.err().contains("\n\tat "),
                        () -> tool.getValue() + " reports its fault as a line: " + fault.err());
                assertTrue(fault.err().contains(tool.getValue() + ": "),
                        () -> tool.getValue() + " names itself in its fault: " + fault.err());
            }
        }
    }

    /** The structure with its tune in it twice, which is a multi of two. */
    private static byte[] multi(byte[] structure) {
        String said = new String(structure, UTF_8);
        int tunes = said.indexOf("\"tunes\": [");
        int first = said.indexOf('{', tunes + 1);
        int last = said.lastIndexOf('}', said.lastIndexOf(']'));
        String tune = said.substring(first, last + 1);
        return (said.substring(0, last + 1) + "," + tune + said.substring(last + 1))
                .getBytes(UTF_8);
    }
}
