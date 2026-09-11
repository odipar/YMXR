package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The player's equates against the bound tune's constants: the bound
 * tune's offsets, its version and the column count appear in both, and
 * this fails where the two differ. The tune file's constants are checked
 * against SPEC.md 3.3, and the bound tune against the file it is bound
 * from.
 */
final class PlayerTest {

    private static final Path PLAYER = Path.of("68k/YMXR.S");

    private static final Path BINARIES = Path.of("doc/BINARIES.md");

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

    /**
     * The offset rows of one section of BINARIES.md, keyed by the row's
     * text: the first table under that heading, which opens
     * {@code | offset | bytes | what it is |}. Three sections define a
     * layout
     * and each names its fields the same way, so one reader serves all
     * three.
     */
    private static Map<String, Integer> layout(String said, String section) {
        int at = said.indexOf("## " + section);
        assertTrue(at >= 0, "BINARIES.md has no section " + section);
        int table = said.indexOf("| offset | bytes | what it is |", at);
        assertTrue(table >= 0, section + " has no layout");
        Map<String, Integer> out = new LinkedHashMap<>();
        Pattern cells = Pattern.compile("^\\| (\\d+) \\| [^|]+ \\| ([^|]+) \\|$");
        for (String line : said.substring(table).split("\n")) {
            if (!line.startsWith("|")) {
                break;
            }
            Matcher row = cells.matcher(line);
            if (row.matches()) {
                out.put(row.group(2).trim(), Integer.parseInt(row.group(1)));
            }
        }
        assertTrue(out.size() >= 8, () -> section + " read as " + out.size() + " rows");
        return out;
    }

    /** The bit numbers of the flags table under one section, by what sets them. */
    private static Map<String, Integer> flags(String said, String section) {
        int at = said.indexOf("## " + section);
        assertTrue(at >= 0, "BINARIES.md has no section " + section);
        int table = said.indexOf("The flags word:", at);
        assertTrue(table >= 0, section + " has no flags word");
        Map<String, Integer> out = new LinkedHashMap<>();
        Pattern cells = Pattern.compile("^\\| (\\d+) \\| ([^|]+) \\| [^|]+ \\|$");
        for (String line : said.substring(table).split("\n")) {
            Matcher row = cells.matcher(line);
            if (row.matches()) {
                out.put(row.group(2).trim(), Integer.parseInt(row.group(1)));
            } else if (!out.isEmpty() && !line.startsWith("|")) {
                break;
            }
        }
        assertTrue(out.size() == 2, () -> section + "'s flags word read as " + out.size());
        return out;
    }

    /** The one row of a table whose text opens with those words. */
    private static Map.Entry<String, Integer> row(Map<String, Integer> table, String opens) {
        for (Map.Entry<String, Integer> one : table.entrySet()) {
            if (one.getKey().startsWith(opens)) {
                return one;
            }
        }
        throw new AssertionError("no row opens \"" + opens + "\", of " + table.keySet());
    }

    /**
     * The bound tune's layout as BINARIES.md 1 defines it, against the
     * player's equates and the binder's constants. A host reads a bound
     * tune by that table, so a field that moves in one of the three moves
     * in all three or the three disagree.
     */
    @Test
    void theBoundTuneIsLaidOutAsBinariesDefines() throws IOException {
        Map<String, Integer> said = layout(Files.readString(BINARIES), "1. The bound tune");
        Map<String, Integer> e = equates();
        assertEquals(0, row(said, "`YMXB`").getValue(), "the magic stands first");
        assertEquals(e.get("TF_VERSION"), row(said, "the version").getValue());
        assertEquals(e.get("TF_RATE"), row(said, "the frame rate").getValue());
        assertEquals(e.get("TF_EFFECTS"), row(said, "effects used").getValue());
        assertEquals(e.get("TF_SOURCES"), row(said, "`S`, the source count").getValue());
        assertEquals(Bound.STATE_AT, row(said, "the state block's bytes").getValue());
        assertEquals(Bound.IMAGE_AT, row(said, "where the image begins").getValue());
        assertEquals(Bound.TABLE_AT, row(said, "where this tune's table stands").getValue());
        assertEquals(Bound.INDEX_AT, row(said, "the source index").getValue());
        Matcher version = Pattern.compile("\\$([0-9A-Fa-f]+)")
                .matcher(row(said, "the version").getKey());
        assertTrue(version.find(), "the version row has no version");
        assertEquals(Bound.VERSION, Integer.parseInt(version.group(1), 16),
                "BINARIES.md 1 has another version than the binder writes");
    }

    /**
     * The SNDH core's descriptor and its flags word as BINARIES.md 2
     * defines them, against the packager that writes them.
     */
    @Test
    void theSndhCoreIsLaidOutAsBinariesDefines() throws IOException {
        String binaries = Files.readString(BINARIES);
        Map<String, Integer> said = layout(binaries, "2. The SNDH core");
        assertEquals(Sndh.CORE_MAGIC_AT, row(said, "`YMXS`").getValue());
        assertEquals(Sndh.CORE_VERSION_AT, row(said, "the descriptor's version").getValue());
        assertEquals(Sndh.CORE_READS_AT, row(said, "the bound tune's version").getValue());
        assertEquals(Sndh.CORE_FIXED_AT, row(said, "`YMXR_FIXED`").getValue());
        assertEquals(Sndh.CORE_FLAGS_AT, row(said, "flags").getValue());
        assertEquals(Sndh.CORE_STATE_AT, row(said, "where the core's state byte").getValue());
        assertEquals(Sndh.CORE_TABLE_AT, row(said, "the subtune table").getValue());
        assertEquals(Sndh.CORE_WORK_AT, row(said, "the workspace").getValue());
        Map<String, Integer> bits = flags(binaries, "2. The SNDH core");
        assertEquals(Sndh.CORE_MONITOR, 1 << row(bits, "the player's raster monitor").getValue());
        assertEquals(Sndh.CORE_LEAN, 1 << row(bits, "the lean tick").getValue());
    }

    /**
     * The program stub's descriptor and its flags word as BINARIES.md 4
     * defines them, against the tool that patches them.
     */
    @Test
    void theProgramStubIsLaidOutAsBinariesDefines() throws IOException {
        String binaries = Files.readString(BINARIES);
        Map<String, Integer> said = layout(binaries, "4. The program stub");
        assertEquals(Prg.STUB_MAGIC_AT, row(said, "`YMXT`").getValue());
        assertEquals(Prg.STUB_VERSION_AT, row(said, "the descriptor's version").getValue());
        assertEquals(Prg.STUB_SUBTUNES_AT, row(said, "the subtunes").getValue());
        assertEquals(Prg.STUB_FLAGS_AT, row(said, "flags").getValue());
        assertEquals(Prg.STUB_RATE_AT, row(said, "the rate, rows a second").getValue());
        assertEquals(Prg.STUB_ROWS_AT, row(said, "the rows to play").getValue());
        assertEquals(Prg.STUB_CORE_AT, row(said, "the core's offset").getValue());
        Map<String, Integer> bits = flags(binaries, "4. The program stub");
        assertEquals(Prg.FLAG_CLEAR, 1 << row(bits, "the core has the raster monitor").getValue());
        assertEquals(Prg.FLAG_VBL, 1 << row(bits, "the set claims Timer C").getValue());
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
        assertEquals(Bound.TABLE_AT, e.get("TF_TABLE"));
        assertEquals(Bound.INDEX_AT, e.get("TF_INDEX"));
        assertEquals(Bound.FORMAT_AT, e.get("IM_FORMAT"));
        assertEquals(Columns.EFFECT, 14);
        assertEquals(Tune.MAX_RING, 32767 / (Columns.C - 1));
    }

    /** Each tick kind as a triple: the text of its row in
     *  performance.md, the player's equate for what that path
     *  costs, and the cycles the level drop adds to it, 16 on the
     *  three paths that write a row's value and none on the two that
     *  end a source. */
    private static final List<String> TICKS = List.of(
            "a row written, the place stepped", "PERF_ON", "16",
            "the marker, the place to row `RR`", "PERF_LOOP", "0",
            "the marker, the timer stopped", "PERF_STOP", "0",
            "a square's two rows, no place stepped", "PERF_SQ", "16",
            "a source of one row, no place stepped", "PERF_ONEROW", "16");

    /** The cycles an end of interrupt written by hand adds to every
     *  path (68k/YMXR.S, PERF_END). */
    private static final int END = 16;

    @Test
    void theMonitorCountsATickAtWhatItCosts() throws IOException {
        // The raster monitor burns a bar for the ticks' counted cost
        // (68k/YMXR.S, YMXR_PERF), and a count is the path's cycles with
        // what the two switches add, rounded to the nearest turn of ten.
        // The player's figure for a path is what it costs with neither
        // the level dropped nor an end of interrupt written, which is
        // performance.md's lean column, and the two switches add back
        // what its other column measures.
        Map<String, Integer> e = equates();
        String said = Files.readString(Path.of("doc/performance.md"));
        for (int i = 0; i < TICKS.size(); i += 3) {
            String row = TICKS.get(i);
            int equate = Objects.requireNonNull(e.get(TICKS.get(i + 1)),
                    TICKS.get(i + 1) + " is not an equate of the player");
            int drop = Integer.parseInt(TICKS.get(i + 2));
            Matcher m = Pattern.compile("^\\| " + Pattern.quote(row)
                    + " \\| (\\d+) \\| (\\d+) \\|$", Pattern.MULTILINE).matcher(said);
            assertTrue(m.find(), "performance.md's lean table has no row for " + row);
            int full = Integer.parseInt(m.group(1));
            int lean = Integer.parseInt(m.group(2));
            assertEquals(lean, equate, row + " is measured at " + lean
                    + " cycles with neither switch, and the player has " + equate);
            assertEquals(full, lean + drop + END, row + " is measured at " + full
                    + " cycles as it stands, and the lean path plus the level and the end"
                    + " of interrupt is " + (lean + drop + END));
        }
    }

    @Test
    void theTuneFileIsLaidOutAsTheSpecificationDefines() {
        // SPEC.md 3.3: the version, where the DTX2 table's offset stands
        // and where the source index begins
        assertEquals(0x0003, Tune.VERSION);
        assertEquals(12, Tune.TABLE_AT);
        assertEquals(16, Tune.INDEX_AT);
        // the bound tune keeps the header to offset 12 and puts the state
        // block's bytes, the image's place and this tune's table in it
        // before the index
        assertEquals(24, Bound.INDEX_AT);
    }

    @Test
    void theBoundTuneIsTheFileBoundWithTheReader() throws IOException {
        byte[] file = Files.readAllBytes(Path.of("doc/conformance/tunes/four-timers.ymxr"));
        TuneFile tune = TuneFile.read(file);
        byte[] bound = Bound.of(file);
        assertArrayEquals(Bound.MAGIC, Arrays.copyOf(bound, 4));
        assertEquals(Bound.VERSION, Tune.getWord(bound, 4));
        // the header in the file, from the frame rate to the count
        assertArrayEquals(Arrays.copyOfRange(file, Tune.FRAME_RATE_AT, Tune.TABLE_AT),
                Arrays.copyOfRange(bound, Tune.FRAME_RATE_AT, Tune.TABLE_AT));
        int count = tune.sources().size();
        int imageAt = Tune.getLong(bound, Bound.IMAGE_AT);
        assertEquals(Tune.align(Bound.INDEX_AT + 4 * count), imageAt);
        // the state block's bytes are the image's format block's
        assertEquals(Tune.getLong(bound, imageAt + Bound.FORMAT_AT + Bound.FORMAT_STATE_AT),
                Tune.getLong(bound, Bound.STATE_AT));
        // the DTX1 tables stand past the image, each on a long, as the file
        // has them
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
