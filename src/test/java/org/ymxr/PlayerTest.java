package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The player's equates against the bound tune's constants: the bound
 * tune's offsets, its version and the column count are stated in both,
 * and this fails where the two differ. The tune file's own constants are
 * held to what SPEC.md 3.3 states, and the bound tune to what the file it
 * is bound from states.
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
    void thePlayerReadsTheBoundTune() throws IOException {
        Map<String, Integer> e = equates();
        assertTrue(e.size() > 20, "the player's equates read as " + e.size());
        assertEquals(java.nio.ByteBuffer.wrap(Bound.MAGIC).getInt(), e.get("YMXR_MAGIC"),
                "the player's magic is the bound tune's");
        assertEquals(Bound.VERSION, e.get("YMXR_VERSION"));
        assertEquals(Columns.C, e.get("YMXR_COLUMNS"));
        assertEquals(Tune.FRAME_RATE_AT, e.get("TF_RATE"));
        assertEquals(Tune.EFFECTS_AT, e.get("TF_EFFECTS"));
        assertEquals(Tune.COUNT_AT, e.get("TF_SOURCES"));
        assertEquals(Bound.STATE_AT, e.get("TF_STATE"));
        assertEquals(Bound.IMAGE_AT, e.get("TF_IMAGE"));
        assertEquals(Bound.INDEX_AT, e.get("TF_INDEX"));
        assertEquals(Bound.FORMAT_AT, e.get("IM_FORMAT"));
        assertEquals(Columns.EFFECT, 14);
        assertEquals(Tune.MAX_RING, 32767 / (Columns.C - 1));
    }

    /** What each tick kind costs, as performance.md measures it: the row's
     *  text, then its cycles. */
    private static final List<String> TICKS = List.of(
            "a row written, the place stepped", "PERF_ON",
            "the marker, the place to row `RR`", "PERF_LOOP",
            "the marker, the timer stopped", "PERF_STOP");

    @Test
    void theMonitorCountsATickAtWhatItCosts() throws IOException {
        // The raster monitor burns a bar for the ticks' counted cost, in
        // turns of ten cycles (68k/YMXR.S, YMXR_PERF). Each count stands
        // within a twentieth of what performance.md measures that tick at,
        // so the bar reads as the timers' share of the frame.
        Map<String, Integer> e = equates();
        String said = Files.readString(Path.of("doc/performance.md"));
        for (int i = 0; i < TICKS.size(); i += 2) {
            String row = TICKS.get(i);
            int count = Objects.requireNonNull(e.get(TICKS.get(i + 1)),
                    TICKS.get(i + 1) + " is not an equate of the player");
            Matcher m = Pattern.compile("^\\| " + Pattern.quote(row) + " \\| (\\d+) \\|$",
                    Pattern.MULTILINE).matcher(said);
            assertTrue(m.find(), "performance.md has no row for " + row);
            int measured = Integer.parseInt(m.group(1));
            assertTrue(Math.abs(count * 10 - measured) * 20 <= measured,
                    row + " is measured at " + measured + " cycles, and the monitor counts "
                            + count + " turns of ten");
        }
    }

    @Test
    void theTuneFileIsWhatTheSpecificationStates() {
        // SPEC.md 3.3: the version, where the DTX2 table's offset stands
        // and where the source index begins
        assertEquals(0x0002, Tune.VERSION);
        assertEquals(12, Tune.TABLE_AT);
        assertEquals(16, Tune.INDEX_AT);
        // the bound tune keeps the header to offset 12 and puts the state
        // block's bytes and the image's place before the index
        assertEquals(20, Bound.INDEX_AT);
    }

    @Test
    void theBoundTuneIsTheFileBoundWithTheReader() throws IOException {
        byte[] file = Files.readAllBytes(Path.of("doc/conformance/tunes/four-timers.ymxr"));
        TuneFile tune = TuneFile.read(file);
        byte[] bound = Bound.of(file);
        assertArrayEquals(Bound.MAGIC, Arrays.copyOf(bound, 4));
        assertEquals(Bound.VERSION, Tune.getWord(bound, 4));
        // the header the file states, from the frame rate to the count
        assertArrayEquals(Arrays.copyOfRange(file, Tune.FRAME_RATE_AT, Tune.TABLE_AT),
                Arrays.copyOfRange(bound, Tune.FRAME_RATE_AT, Tune.TABLE_AT));
        int count = tune.sources().size();
        int imageAt = Tune.getLong(bound, Bound.IMAGE_AT);
        assertEquals(Tune.align(Bound.INDEX_AT + 4 * count), imageAt);
        // the state block's bytes are the image's format block's
        assertEquals(Tune.getLong(bound, imageAt + Bound.FORMAT_AT + Bound.FORMAT_STATE_AT),
                Tune.getLong(bound, Bound.STATE_AT));
        // the DTX1 tables stand past the image, each on a long, as the file
        // holds them
        int end = bound.length;
        for (int i = count - 1; i >= 0; i--) {
            int at = Tune.getLong(bound, Bound.INDEX_AT + 4 * i);
            assertEquals(0, at & 3, "source " + (i + 1) + " begins on a long");
            int fileAt = Tune.getLong(file, Tune.INDEX_AT + 4 * i);
            int fileEnd = i + 1 < count ? Tune.getLong(file, Tune.INDEX_AT + 4 * (i + 1))
                    : file.length;
            assertArrayEquals(Arrays.copyOfRange(file, fileAt, fileEnd),
                    Arrays.copyOfRange(bound, at, end), "source " + (i + 1));
            end = at;
        }
        assertTrue(count > 0, "four-timers runs sources");
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
