package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a tool reports while it works: the account a report prints, the
 * progress lines of a long run, and the conversion's
 * account against the file it wrote. {@code -silent} turns the account
 * off and leaves the notes, and no account reaches standard output,
 * which the tool writes its file to.
 */
final class ReportTest {

    /** A report writing into a buffer. */
    private record Caught(Report report, ByteArrayOutputStream buffer) {
        String said() {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    private static Caught caught(boolean says) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        return new Caught(new Report(says, new PrintStream(buffer, true,
                StandardCharsets.UTF_8)), buffer);
    }

    @Test
    void aSilentReportPrintsNothingAndKeepsItsNotes() {
        Caught c = caught(false);
        c.report().say("a line");
        c.report().step("a step");
        c.report().row("a name", "its value");
        c.report().progress("read", 1, 2);
        c.report().note("a note");
        assertEquals("", c.said(), "a silent report prints no line");
        assertEquals(List.of("a note"), c.report().notes(), "and keeps the note for its caller");
        assertFalse(c.report().says());
    }

    @Test
    void aReportPrintsTheAccountAndSaysANoteWhereItHappened() {
        Caught c = caught(true);
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
    void aRunThatEndsWithinASecondSaysNothingOfItsProgress() {
        Caught c = caught(true);
        for (int done = 1; done <= 400; done++) {
            c.report().progress("read", done, 400);
        }
        assertEquals("", c.said(), "400 steps in no time at all print no line");
    }

    @Test
    void aLongRunSaysHowFarThroughItIsOnceATenth() throws Exception {
        Caught c = caught(true);
        // A tenth of the run and a second of the clock both have to pass,
        // so the four steps below produce three lines and not four: the
        // first is at the report's start.
        for (int done = 1; done <= 4; done++) {
            Thread.sleep(1100);
            c.report().progress("read", done, 4);
        }
        assertEquals("""
                  read 1 of 4 (25%)
                  read 2 of 4 (50%)
                  read 3 of 4 (75%)
                  read 4 of 4 (100%)
                """, c.said());
    }

    @Test
    void theConversionSaysWhatItReadTookAndPacked() throws Exception {
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Chambers of Shaolin 5 -"
                + " you blew it!.ym"));
        Caught c = caught(true);
        YmToYmxr.Converted converted = YmToYmxr.convert(dump, List.of(), c.report());
        String said = c.said();
        // Every figure in the account comes from the conversion, so a
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
    void theConverterReadsTheSilentFlagAndWritesTheSameFileEitherWay() throws Exception {
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Chambers of Shaolin 5 -"
                + " you blew it!.ym"));
        Caught silent = caught(false);
        Caught saying = caught(true);
        // Every tool reads -silent, and the tools that pass the converter's
        // flags on pass it too, so the converter reads it; and a report
        // says what a conversion did without changing what it wrote.
        YmToYmxr.Converted with = YmToYmxr.convert(dump, List.of(YmToYmxr.SILENT),
                silent.report());
        YmToYmxr.Converted said = YmToYmxr.convert(dump, List.of(), saying.report());
        assertEquals(said.said(), with.said());
        assertArrayEquals(said.written().file(), with.written().file(),
                "a reporting conversion writes the file a silent one writes");
        assertEquals("", silent.said(), "the flag it names leaves the output empty");
        assertFalse(saying.said().isEmpty(), "and without it the account stands");
        assertFalse(new Report().says(), "the report a library caller passes is silent");
    }

    @Test
    void theSourcesAndTheFlagsAskedForAreSaid() throws Exception {
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Turrican 2 -"
                + " world completed 1.ym"));
        Caught c = caught(true);
        YmToYmxr.convert(dump, List.of("-k1", "-m1920", "-r10"), c.report());
        String said = c.said();
        // A flag passed reads as requested, and a source of a kind is
        // counted with its rows.
        assertTrue(said.contains("the flags: -k1 -m1920 -r10"), said);
        assertTrue(said.contains("-k, the unit           1, asked for"), said);
        assertTrue(said.contains("-m, the ring           1920 bytes, asked for"), said);
        assertTrue(said.contains("-r, the repeat row     10, asked for"), said);
        assertTrue(said.contains("digidrums"), "the dump's digidrums are said: " + said);
        assertTrue(said.contains("the sources: "), said);
    }

    @Test
    void everyColumnsPackedBytesAreTheBytesInTheFile(@TempDir Path work) throws Exception {
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Chambers of Shaolin 5 -"
                + " you blew it!.ym"));
        Caught c = caught(true);
        byte[] file = YmToYmxr.convert(dump, List.of(), c.report()).written().file();
        // The account has a row a column; the file has each column's
        // data set at a separate offset, so the two are compared.
        int table = Tune.getLong(file, Tune.TABLE_AT);
        // A DTX2 file is its header, then a payload whose offsets count
        // from the payload's first byte (DTX abi.md 2.3).
        int payload = table + org.dtx.Dtx.HEADER;
        int[] at = new int[Columns.C];
        for (int i = 0; i < Columns.C; i++) {
            at[i] = Tune.getLong(file, payload + 4 + 4 * i);
        }
        // The table runs to the first source's table, or to the file's
        // end where the tune has none.
        int sources = file[Tune.COUNT_AT] & 0xFF;
        int ends = sources > 0 ? Tune.getLong(file, Tune.INDEX_AT) : file.length;
        String said = c.said();
        for (int i = 0; i < Columns.C; i++) {
            // A set runs to the next set's offset, less the padding that
            // puts that one on a long; the last runs to the table's end.
            int to = i + 1 < Columns.C ? at[i + 1] : ends - payload;
            int most = to - at[i];
            int least = Math.max(0, most - 3);
            boolean found = false;
            for (int bytes = least; bytes <= most && !found; bytes++) {
                found = said.contains(String.format("  %-22s %7d -> %6d bytes",
                        Tune.name(i), 500, bytes));
            }
            assertTrue(found, "no row for column " + i + " reading " + least + " to " + most
                    + " bytes in " + said);
        }
        Files.write(work.resolve("t.ymxr"), file);
    }

    @Test
    void aToolSaysItsAccountOnStandardErrorAndWritesItsFileOnStandardOutput()
            throws Exception {
        // The account, the progress and the notes go to standard error,
        // and the file the tool is for goes to standard output, so a run
        // read through a pipe reads the same with the report on as with it
        // off (doc/tools.md, What a tool reports).
        byte[] dump = Files.readAllBytes(Path.of("ym/test/Turrican 2 - world completed 1.ym"));
        byte[][] out = new byte[2][];
        String[] err = new String[2];
        for (int i = 0; i < 2; i++) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            ByteArrayOutputStream e = new ByteArrayOutputStream();
            PrintStream wasOut = System.out;
            PrintStream wasErr = System.err;
            InputStream wasIn = System.in;
            try {
                System.setOut(new PrintStream(o, true, StandardCharsets.UTF_8));
                System.setErr(new PrintStream(e, true, StandardCharsets.UTF_8));
                System.setIn(new ByteArrayInputStream(dump));
                YmToYmxr.main(i == 0 ? new String[0] : new String[] {YmToYmxr.SILENT});
            } finally {
                System.setOut(wasOut);
                System.setErr(wasErr);
                System.setIn(wasIn);
            }
            out[i] = o.toByteArray();
            err[i] = e.toString(StandardCharsets.UTF_8);
        }
        assertArrayEquals(out[1], out[0], "standard output reads the same with the report on");
        assertEquals("YMXR", new String(out[0], 0, 4, StandardCharsets.UTF_8),
                "and is the tune file alone");
        assertTrue(err[0].contains("ym-to-ymxr: 178 frames at 50 Hz"),
                "the summary names the tool, on standard error: " + err[0]);
        assertTrue(err[0].contains("the flags:") && err[0].contains("the table: "),
                "the account is on standard error: " + err[0]);
        // Turrican 2 has 178 frames repeating to 177: a row goes in before
        // the repeat, the count is then odd, and the loop of one row is
        // written twice (SPEC.md 6, rule 6), which is two notes
        String before = "  padded: 1 unset row at row 177, before the repeat row, so the table"
                + " packs at unit 2" + System.lineSeparator();
        String after = "  padded: the loop's 1 row written twice, so the table packs at unit 2"
                + System.lineSeparator();
        assertEquals("", err[1].replace(before, "").replace(after, ""),
                "a silent run prints its notes alone: " + err[1]);
        assertTrue(err[0].contains("note: padded: 1 unset row at row 177"),
                "the note stands either way: " + err[0]);
        assertEquals(2, err[0].lines().filter(l -> l.contains("note: padded:")).count(),
                "and each stands once: " + err[0]);
    }
}
