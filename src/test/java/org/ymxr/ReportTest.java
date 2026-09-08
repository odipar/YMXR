package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a tool says while it works: the account a report prints, the
 * progress line it draws on a terminal and takes off again, and the
 * conversion's own account against the file it wrote. {@code -silent}
 * turns the account off and leaves the notes, and no account reaches
 * standard output, which is what a tool is for.
 */
final class ReportTest {

    /** A report writing into a buffer, told whether it is a terminal. */
    private record Caught(Report report, ByteArrayOutputStream buffer) {
        String said() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    private static Caught caught(boolean says, boolean terminal) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        return new Caught(new Report(says, new PrintStream(buffer, true,
                StandardCharsets.UTF_8), terminal), buffer);
    }

    @Test
    void aSilentReportPrintsNothingAndKeepsItsNotes() {
        Caught c = caught(false, true);
        c.report().say("a line");
        c.report().step("a step");
        c.report().row("a name", "what it holds");
        c.report().progress("read", 1, 2);
        c.report().note("a note");
        assertEquals("", c.said(), "a silent report prints nothing");
        assertEquals(List.of("a note"), c.report().notes(), "and holds the note for its caller");
        assertFalse(c.report().says());
    }

    @Test
    void aReportPrintsTheAccountAndSaysANoteWhereItHappened() {
        Caught c = caught(true, false);
        c.report().say("the tune file: 12 bytes");
        c.report().row("the table", "500 rows");
        c.report().note("packed at unit 1");
        assertEquals("""
                the tune file: 12 bytes
                  the table              500 rows
                  note: packed at unit 1
                """, c.said());
        assertEquals(List.of("packed at unit 1"), c.report().notes(),
                "a note said where it happened is a note still");
    }

    @Test
    void theProgressLineIsDrawnOnATerminalAndNowhereElse() {
        Caught off = caught(true, false);
        off.report().progress("read", 1, 4);
        assertEquals("", off.said(), "a redirected run holds no carriage returns");

        Caught on = caught(true, true);
        on.report().progress("read", 1, 4);
        assertTrue(on.said().startsWith("\r  read 1 of 4 (25%)"), on.said());
        assertFalse(on.said().contains("\n"), "the line is redrawn, not added to");
    }

    @Test
    void aLineAfterTheProgressLineTakesItOffFirst() {
        Caught c = caught(true, true);
        c.report().progress("read", 1, 4);
        c.report().say("done");
        String said = c.said();
        assertTrue(said.endsWith("done" + System.lineSeparator()), said);
        assertTrue(said.contains("\r" + " ".repeat(46) + "\r"),
                "the progress line is written over with spaces before the next line");
    }

    @Test
    void theConversionSaysWhatItReadTookAndPacked() throws Exception {
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Chambers of Shaolin 5 -"
                + " you blew it!.ym"));
        Caught c = caught(true, false);
        YmToYmxr.Converted converted = YmToYmxr.convert(dump, List.of(), c.report());
        String said = c.said();
        // Every figure the account gives is the conversion's own, so a
        // change in either shows here.
        assertTrue(said.contains("YM6!: Chambers of Shaolin"), said);
        assertTrue(said.contains("500 at 50 Hz (0:10)"), said);
        assertTrue(said.contains("-k, the unit           " + YmToYmxr.UNIT + ", the default"),
                said);
        assertTrue(said.contains("-m, the ring           " + Tune.RING + " bytes, the default"),
                said);
        assertTrue(said.contains("the table: " + Columns.C + " columns of 500 rows, "
                + Columns.C * 500 + " bytes"), said);
        assertTrue(said.contains("the file " + converted.written().file().length + " bytes"),
                said);
        for (int c1 = 0; c1 < Columns.C; c1++) {
            assertTrue(said.contains("  " + Tune.name(c1) + " "),
                    "no row for column " + c1 + " in " + said);
        }
    }

    @Test
    void theConverterTakesTheSilentFlagAndDoesNotRefuseIt() throws Exception {
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Chambers of Shaolin 5 -"
                + " you blew it!.ym"));
        Caught c = caught(false, false);
        // Every tool reads -silent, and the tools that pass the converter's
        // flags on pass it too, so the converter takes it and packs the same.
        YmToYmxr.Converted with = YmToYmxr.convert(dump, List.of(YmToYmxr.SILENT), c.report());
        YmToYmxr.Converted without = YmToYmxr.convert(dump, List.of(), new Report());
        assertEquals(without.said(), with.said());
        assertEquals("", c.said(), "the flag it names leaves nothing printed");
    }
}
