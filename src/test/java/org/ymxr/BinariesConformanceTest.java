package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The kit of the binaries (doc/conformance-binaries): every file under
 * {@code files} read back as BINARIES.md 6 defines and compared with
 * the record beside it, the manifest read against both, and TASK.md's
 * line counts read against the records. A reader of the document
 * produces those records; this reads them against the one in this tree.
 */
final class BinariesConformanceTest {

    private static final Path KIT = Path.of("doc", "conformance-binaries");
    private static final Path FILES = KIT.resolve("files");
    private static final Path RECORDS = KIT.resolve("records");

    /** The kit's files, in the order MANIFEST.txt lists them. */
    private static List<String> named() throws IOException {
        List<String> names = new ArrayList<>();
        for (String line : Files.readAllLines(KIT.resolve("MANIFEST.txt"))) {
            if (line.startsWith("#")) {
                continue;
            }
            String file = line.substring(line.lastIndexOf(' ') + 1);
            if (file.startsWith("files/")) {
                names.add(file.substring("files/".length()));
            }
        }
        return names;
    }

    @Test
    void everyFileOfTheKitRecordsAsTheReferenceReads() throws IOException {
        List<String> names = named();
        assertEquals(9, names.size(), "the kit is nine files");
        for (String name : names) {
            String record = Layout.of(Files.readAllBytes(FILES.resolve(name)));
            assertEquals(Files.readString(RECORDS.resolve(name + ".jsonl")), record,
                    "the record of " + name);
        }
    }

    @Test
    void theManifestReadsEveryFileAndEveryRecord() throws IOException {
        List<String> lines = Files.readAllLines(KIT.resolve("MANIFEST.txt"));
        assertEquals("# sha256  bytes  file", lines.get(0));
        List<String> listed = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] said = line.split("  ");
            assertEquals(3, said.length, line);
            Path at = KIT.resolve(said[2]);
            byte[] file = Files.readAllBytes(at);
            assertEquals(said[0], sha256(file), said[2] + "'s sha256");
            assertEquals(Integer.parseInt(said[1]), file.length, said[2] + "'s bytes");
            listed.add(said[2]);
        }
        List<String> under = new ArrayList<>();
        for (Path at : List.of(FILES, RECORDS)) {
            try (Stream<Path> found = Files.list(at)) {
                found.forEach(p -> under.add(KIT.relativize(p).toString()));
            }
        }
        under.sort(null);
        List<String> sorted = new ArrayList<>(listed);
        sorted.sort(null);
        assertEquals(sorted, under, "the manifest lists what stands under the kit");
    }

    @Test
    void theTaskReadsTheLinesOfEveryRecord() throws IOException {
        String task = Files.readString(KIT.resolve("TASK.md"));
        for (String name : named()) {
            long lines = Files.readString(RECORDS.resolve(name + ".jsonl")).lines().count();
            String row = "| `" + name + "` |";
            int at = task.indexOf(row);
            assertTrue(at >= 0, "TASK.md has a row for " + name);
            String said = task.substring(at, task.indexOf('\n', at));
            assertTrue(said.endsWith("| " + lines + " |"),
                    name + " records " + lines + " lines, and TASK.md reads " + said);
        }
    }

    /** The record's shape: US-ASCII, every line an object free of
     *  spaces, and a line feed ending each. */
    @Test
    void everyRecordIsTheShapeTheDocumentDefines() throws IOException {
        for (String name : named()) {
            String record = Files.readString(RECORDS.resolve(name + ".jsonl"));
            assertTrue(record.endsWith("}\n"), name + " ends in a line feed");
            for (String line : record.lines().toList()) {
                assertTrue(line.startsWith("{") && line.endsWith("}"), name + ": " + line);
                assertEquals(-1, outsideText(line), name + ": " + line);
                for (int i = 0; i < line.length(); i++) {
                    assertTrue(line.charAt(i) < 128, name + " reads US-ASCII: " + line);
                }
            }
        }
    }

    /** Where the line has a space outside a text, or -1. */
    private static int outsideText(String line) {
        boolean inside = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                inside = !inside;
            } else if (c == ' ' && !inside) {
                return i;
            }
        }
        return -1;
    }

    private static String sha256(byte[] file) {
        try {
            StringBuilder out = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(file)) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException none) {
            throw new IllegalStateException(none);
        }
    }

    /** The first line of every record names the kind of its file, and
     *  the kinds of the kit are the four the document defines. */
    @Test
    void theKitHasAFileOfEveryKind() throws IOException {
        List<String> kinds = new ArrayList<>();
        for (String name : named()) {
            String first = Files.readString(RECORDS.resolve(name + ".jsonl")).lines()
                    .findFirst().orElseThrow();
            String kind = first.substring(first.indexOf(':') + 2, first.indexOf("\",\"bytes"));
            if (!kinds.contains(kind)) {
                kinds.add(kind);
            }
        }
        kinds.sort(null);
        assertEquals(List.of("bound", "multi", "program", "sndh"), kinds);
    }

    /** A name of a multi file is UTF-8 (0.3), and the names of the kit
     *  are ASCII, so every record is. */
    @Test
    void theKitsNamesAreAscii() throws IOException {
        byte[] multi = Files.readAllBytes(FILES.resolve("set.ymxr"));
        String record = Layout.of(multi);
        assertTrue(record.contains("\"name\":\"Circus\"") && record.contains("\"name\":\"Once\""),
                record);
        assertEquals(record, new String(record.getBytes(StandardCharsets.US_ASCII),
                StandardCharsets.US_ASCII));
    }
}
