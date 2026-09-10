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
import java.util.List;
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

    @Test
    void anInputThisCannotReadExitsOne() throws Exception {
        byte[] structure = structure();
        byte[] nonsense = "not a file of any of these".getBytes(UTF_8);
        List<Ran> faults = List.of(
                ran("YmToYmxs", nonsense),
                ran("YmxsToYmxr", nonsense),
                ran("YmxsToSndh", nonsense),
                ran("YmxsToPrg", nonsense),
                ran("YmToYmxs", new byte[0]),
                ran("YmxsToYmxr", multi(structure)));
        for (Ran fault : faults) {
            assertEquals(1, fault.exit(), () -> "a wrong input exits 1: " + fault.err());
            assertEquals(0, fault.out().length, "and writes no file");
        }
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
        for (String tool : List.of("YmToYmxs", "YmxsToYmxr", "YmxsToSndh", "YmxsToPrg")) {
            for (Ran fault : List.of(ran(tool, nonsense), ran(tool, new byte[0], "-zz"))) {
                assertFalse(fault.err().contains("Exception in thread"),
                        () -> tool + " reports its fault as a line: " + fault.err());
                assertFalse(fault.err().contains("\n\tat "),
                        () -> tool + " reports its fault as a line: " + fault.err());
                assertEquals(1, fault.err().lines().filter(one -> !one.isBlank()).count(),
                        () -> tool + " reports one line: " + fault.err());
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
