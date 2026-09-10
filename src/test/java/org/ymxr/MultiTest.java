package org.ymxr;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The multi file (doc/BINARIES.md 0): several tune files in one, a name
 * each.
 *
 * <p>What is under test is that a multi file adds nothing to the tune
 * files in it: a tune read back out of one is the file that went in, byte
 * for byte, so an SNDH file of subtunes is the file the tune files make.
 */
final class MultiTest {

    private static byte[] tune(String dump) throws IOException {
        return YmToYmxr.convert(Files.readAllBytes(Path.of("ym/test", dump)),
                List.of(), new Report()).written().file();
    }

    private static List<byte[]> two() throws IOException {
        return List.of(tune("Turrican - world 4-3.ym"), tune("Circus Attractions  2.ym"));
    }

    @Test
    void aTuneReadBackIsTheFileThatWentIn() throws IOException {
        List<byte[]> tunes = two();
        List<String> names = List.of("Turrican", "Circus");
        Multi.Read read = Multi.read(Multi.of(tunes, names));
        assertEquals(names, read.names(), "the names come back in order");
        assertEquals(2, read.tunes().size(), "and both tunes");
        for (int i = 0; i < tunes.size(); i++) {
            assertArrayEquals(tunes.get(i), read.tunes().get(i),
                    "tune " + (i + 1) + " is the file that went in");
        }
    }

    @Test
    void theHeaderIsTheOneTheDocumentDefines() throws IOException {
        List<byte[]> tunes = two();
        byte[] file = Multi.of(tunes, List.of("one", "two"));
        assertEquals("YMXM", new String(file, 0, 4, UTF_8), "the four bytes it opens with");
        assertEquals(Tune.VERSION, Tune.getWord(file, 4), "the version of the tune files in it");
        assertEquals(2, Tune.getWord(file, Multi.COUNT_AT), "the tune count");
        for (int i = 0; i < 2; i++) {
            int at = Tune.getLong(file, Multi.INDEX_AT + 8 * i);
            int bytes = Tune.getLong(file, Multi.INDEX_AT + 8 * i + 4);
            assertEquals(tunes.get(i).length, bytes, "entry " + i + " measures its tune file");
            assertEquals(0, at % 2, "and stands on a long");
            assertArrayEquals(tunes.get(i),
                    Arrays.copyOfRange(file, at, at + bytes),
                    "the tune file stands where the entry says");
        }
    }

    @Test
    void anEmptyNameIsATuneTheFileNamesNone() throws IOException {
        Multi.Read read = Multi.read(Multi.of(List.of(tune("Circus Attractions  2.ym")),
                List.of("")));
        assertEquals(1, read.tunes().size(), "one tune");
        assertEquals("", read.names().get(0), "and no name for it");
    }

    @Test
    void aFileThisDoesNotReadIsAnError() throws IOException {
        byte[] tune = tune("Circus Attractions  2.ym");
        assertThrows(IllegalArgumentException.class, () -> Multi.read(tune),
                "a tune file is not a multi file");
        assertThrows(IllegalArgumentException.class,
                () -> Multi.of(List.of(tune), List.of("one", "two")),
                "two names for one tune");
        assertThrows(IllegalArgumentException.class,
                () -> Multi.of(List.of(), List.of()),
                "no tunes at all");
        List<byte[]> many = new ArrayList<>(Collections.nCopies(Multi.MOST + 1, tune));
        List<String> names = new ArrayList<>(Collections.nCopies(Multi.MOST + 1, "one"));
        String said = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> Multi.of(many, names)).getMessage());
        assertTrue(said.contains(String.valueOf(Multi.MOST)),
                "the most in one is the subtunes an SNDH file numbers: " + said);
        byte[] file = Multi.of(List.of(tune), List.of("one"));
        byte[] cut = Arrays.copyOf(file, Multi.INDEX_AT + 4);
        assertThrows(IllegalArgumentException.class, () -> Multi.read(cut),
                "a file its entries stand past");
    }
}
