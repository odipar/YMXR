package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The player's equates against the converter's constants: the tune file's
 * offsets, the version and the column count are stated in both, and this
 * fails where the two differ.
 */
final class PlayerTest {

    private static final Path PLAYER = Path.of("68k/YMXR.S");

    /** Every {@code NAME equ VALUE} in the player, decimal or hex. */
    static Map<String, Integer> equates() throws IOException {
        Map<String, Integer> out = new HashMap<>();
        Matcher m = Pattern.compile("^(\\w+)\\s+equ\\s+(\\$?[0-9A-Fa-f]+)", Pattern.MULTILINE)
                .matcher(Files.readString(PLAYER));
        while (m.find()) {
            String value = m.group(2);
            out.put(m.group(1), value.startsWith("$") ? (int) Long.parseLong(value.substring(1), 16)
                    : Integer.parseInt(value));
        }
        return out;
    }

    @Test
    void thePlayerReadsTheFileTheConverterWrites() throws IOException {
        Map<String, Integer> e = equates();
        assertTrue(e.size() > 20, "the player's equates read as " + e.size());
        assertEquals(Tune.VERSION, e.get("YMXR_VERSION"));
        assertEquals(Columns.C, e.get("YMXR_COLUMNS"));
        assertEquals(Tune.FRAME_RATE_AT, e.get("TF_RATE"));
        assertEquals(Tune.EFFECTS_AT, e.get("TF_EFFECTS"));
        assertEquals(Tune.COUNT_AT, e.get("TF_SOURCES"));
        assertEquals(Tune.STATE_AT, e.get("TF_STATE"));
        assertEquals(Tune.IMAGE_AT, e.get("TF_IMAGE"));
        assertEquals(Tune.INDEX_AT, e.get("TF_INDEX"));
        assertEquals(Tune.FORMAT_AT, e.get("IM_FORMAT"));
        assertEquals(Columns.EFFECT, 14);
        assertEquals(Tune.MAX_RING, 32767 / (Columns.C - 1));
    }

    @Test
    void theRingIsAMultipleOfThePeriodWithinReach() {
        // the ring asked for, a multiple of 30 already
        assertEquals(960, Tune.ringOf(960));
        // the nearest multiple of 30
        assertEquals(90, Tune.ringOf(100));
        // two periods at least
        assertEquals(60, Tune.ringOf(30));
        // the widest ring the player reads
        assertEquals(1110, Tune.ringOf(4000));
    }
}
