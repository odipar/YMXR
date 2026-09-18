package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The converter writes the conformance kit: every tune under
 * {@code doc/conformance/tunes} is the dump its SOURCES.md row names,
 * converted with the row's options, or the tune its builder builds, and
 * every reference is the reader's report of it (SPEC.md 7). This
 * makes each again and compares, so a change to the converter or the
 * reader that moved a byte of the kit fails here, and writes what is
 * missing beside the kit.
 */
final class ConformanceTest {

    static final Path KIT = Path.of("doc", "conformance");
    static final Path TUNES = KIT.resolve("tunes");

    /** One tune of the kit: the dump under {@code ym/test} it comes from
     *  and the options, or the builder that builds it. */
    record Fixture(String name, @Nullable String dump, List<String> options,
                   @Nullable Supplier<Tune.Written> built, String exercises) {

        static Fixture of(String name, String dump, String options, String exercises) {
            return new Fixture(name, dump, options.isEmpty() ? List.of() : List.of(options.split(" ")),
                    null, exercises);
        }

        static Fixture built(String name, String builder, Supplier<Tune.Written> built,
                             String exercises) {
            return new Fixture(name, builder, List.of(), built, exercises);
        }
    }

    /** The version word in {@code wrong-version}: one past the highest
     *  version the reader reads (SPEC.md 3.3.5). */
    static final int WRONG_VERSION = Tune.VERSION_COUNTED + 1;

    /** SOURCES.md's rows, one a tune. */
    static final List<Fixture> FIXTURES = List.of(
            Fixture.of("chambers", "Chambers of Shaolin 5 - you blew it!.ym", "",
                    "no effect; R13 once, with both envelope-period-0 bits beside it; a YM6 dump"),
            Fixture.of("circus", "Circus Attractions  2.ym", "",
                    "four frames, fewer than a period; a YM5 dump"),
            Fixture.of("plays-once", "Circus Attractions  2.ym", "-r",
                    "four frames whose RR is R: the frame after the last row reports -1"),
            Fixture.of("turrican", "Turrican - world 4-3.ym", "",
                    "three drums on Timer D, each ending by its marker; RR at 160, a loop longer than the ring replayed at its exact rows; R13 rewritten"),
            Fixture.of("turrican-2", "Turrican 2 - world completed 1.ym", "",
                    "a loop of one row, RR at 177, odd, so a row that sets no column goes in before it and the loop's row is written twice, and the table packs at unit 2; six drums before it, the last stopped by a row"),
            Fixture.of("synergy", "Synergy Credits.ym", "",
                    "nine SIDs on Timers A and D at once, six of them named by both; a square replacing a square on the target of its effect, its place unmoved; the select changed without the source, and the count alone; a running source stopped by a row; a tone fine byte 0 with the coarse bit beside it; 5,377 rows, odd, so a row that sets no column goes in at the end and the table packs at unit 2"),
            Fixture.of("preempt", "Digidrum preempt, built.ym", "",
                    "a drum starting on the voice a SID runs on stops the SID first, and the SID starts again when the drum ends; R8 passed between them with its column unset"),
            Fixture.of("retune", "Retrigger retune, built.ym", "",
                    "a one-row buzzer source on R13, restarted over a running timer with a new rate; select 7; stopped at the wrap alone"),
            Fixture.of("fine-zero", "Big - Samantha Fox Strip Poker 6.ym", "",
                    "a tone fine byte moving to 0: on voice A without the coarse set bit, on B and C with it;"
                    + " 215 rows, odd, so a row that sets no column goes in at the end and the table packs at unit 2"),
            Fixture.built("four-timers", "`BuiltTunes.fourTimers`", BuiltTunes::fourTimers,
                    "all four effects on Timers A, D, B and C at 60 Hz; the rows section 4 allows that no dump produces: a count alone, a select alone with the count kept, bit 5 alone, bit 5 with a new source on a running timer, bit 6 alone, a stop with the volume set, the same source again, a target set while running and read at the next start, a target that is not a volume register, a drum closing on 5, R13 set beside a buzzer, a source repeating to its row 2, a stop with no source running, values under a clear set bit, a fine byte and an envelope period byte that are not 0 with the bit beside them"),
            Fixture.built("voices", "`BuiltTunes.voices`", BuiltTunes::voices,
                    "version 4: the four kinds of target that write several registers, one an"
                    + " effect, and the marker in a different column under each - the coarse"
                    + " nibble of a voice on Timer A and of a tone on Timer C, the noise period"
                    + " on Timer D, the envelope shape of a buzzer on Timer B; a source of"
                    + " several columns repeating to row 0, one repeating to a row above it, one"
                    + " that plays once and stops its timer at its marker, and a start over a"
                    + " running source of the same row count that leaves the place where it"
                    + " stands"),
            Fixture.built("envelope", "`BuiltTunes.envelope`", BuiltTunes::envelope,
                    "version 4: `setEnvelope`, the one target whose marked register reads"
                    + " eight bits, so the marker's column is the envelope period's high"
                    + " byte, 0 to 127, and the column beside it a whole byte; a source of"
                    + " two columns repeating to a row above 0, one that plays once and"
                    + " stops its timer at its marker with 127 in a row of its marked"
                    + " column, a start that changes the source on a running timer, and the"
                    + " envelope shape set from column 13 while the period ticks"),
            Fixture.built("wrong-version", "`ConformanceTest.wrongVersion`", () -> wrongVersion(),
                    String.format(Locale.ROOT, "chambers with the version word $%04X: a reader"
                            + " produces no report of it", WRONG_VERSION)));

    /** chambers with another version in its header: what a reader reports
     *  no report of (R6.1). */
    static Tune.Written wrongVersion() {
        try {
            byte[] dump = Files.readAllBytes(Path.of("ym", "test", "Chambers of Shaolin 5 - you blew it!.ym"));
            Tune.Written it = YmToYmxr.convert(dump, List.of(), new Report()).written();
            byte[] file = it.file().clone();
            Tune.putWord(file, 4, WRONG_VERSION);
            return new Tune.Written(file, it.repeat());
        } catch (IOException failed) {
            throw new IllegalStateException(failed);
        }
    }

    /** The tune of a fixture: converted from its dump, or built. */
    static Tune.Written make(Fixture f) throws IOException {
        if (f.built() != null) {
            return f.built().get();
        }
        byte[] dump = Files.readAllBytes(Path.of("ym", "test", f.dump()));
        return YmToYmxr.convert(dump, f.options(), new Report()).written();
    }

    /** The name and author from the dump's header, the only attribution in
     *  the file, as two cells; none for a tune built without a dump. */
    static String credit(Fixture f) throws IOException {
        if (f.built() != null) {
            return "none | none";
        }
        YmDump.Song song = YmDump.read(Files.readAllBytes(Path.of("ym", "test", f.dump())));
        return song.name() + " | " + song.author();
    }

    /** The reference: the reader's record of the tune, the kit's count of frames. */
    static byte[] reference(byte[] tune) {
        return Trace.record(tune, -1);
    }

    /** The tune's table as a DTX0 file: the sixteen-byte header carrying R
     *  and RR (SPEC.md 3.1), then row 0 to R minus one, each its thirty
     *  columns in order (DTX, SPEC.md 2.1); what a reader reads the table
     *  from, since the DTX2 table's packing is DTX's and not this
     *  specification's. Empty for a file of another version. */
    static byte[] rows(byte[] tune) {
        try {
            return org.dtx.Dtx0.write(TuneFile.read(tune).table());
        } catch (IllegalArgumentException another) {
            return new byte[0];
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** SOURCES.md's row for a fixture. */
    static String row(Fixture f, byte[] tune) throws IOException {
        String from = f.built() != null ? f.dump() : "`" + f.dump() + "`";
        String options = f.options().isEmpty() ? "none" : "`" + String.join(" ", f.options()) + "`";
        return "| `" + f.name() + "` | " + from + " | " + credit(f) + " | " + options + " | "
                + tune.length + " | " + sha256(tune).substring(0, 16) + " | " + f.exercises() + " |";
    }

    /** MANIFEST.txt's three lines for a fixture: the tune, its rows, and
     *  the reference, which is not in the kit. */
    static String manifest(Fixture f, byte[] tune, byte[] rows, byte[] reference) {
        return sha256(tune) + "  " + tune.length + "  tunes/" + f.name() + ".ymxr\n"
                + sha256(rows) + "  " + rows.length + "  tunes/" + f.name() + ".rows\n"
                + sha256(reference) + "  " + reference.length + "  " + f.name() + ".jsonl";
    }

    /** The lines of a record. */
    static long lines(byte[] record) {
        return record.length == 0 ? 0 : new String(record, StandardCharsets.US_ASCII).lines().count();
    }

    @Test
    void everyTuneMatchesItsRowsFile() throws IOException {
        List<String> missing = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        String sources = Files.readString(KIT.resolve("SOURCES.md"));
        for (Fixture f : FIXTURES) {
            byte[] tune = make(f).file();
            Path at = TUNES.resolve(f.name() + ".ymxr");
            if (!Files.exists(at)) {
                Files.write(at, tune);
                missing.add(at + " written");
            } else {
                assertArrayEquals(Files.readAllBytes(at), tune,
                        at + " does not match its SOURCES.md row");
            }
            Path rowsAt = TUNES.resolve(f.name() + ".rows");
            if (!Files.exists(rowsAt)) {
                Files.write(rowsAt, rows(tune));
                missing.add(rowsAt + " written");
            } else {
                assertArrayEquals(Files.readAllBytes(rowsAt), rows(tune),
                        rowsAt + " is not the table's rows");
            }
            String row = row(f, tune);
            rows.add(row);
            if (!sources.contains(row)) {
                missing.add("SOURCES.md lacks the row for " + f.name());
            }
        }
        if (!missing.isEmpty()) {
            Files.writeString(KIT.resolve("SOURCES.generated.md"), String.join("\n", rows) + "\n");
        }
        assertTrue(missing.isEmpty(), () -> String.join("\n", missing)
                + "\nSOURCES.generated.md has every row; copy the rows into SOURCES.md");
    }

    @Test
    void everyReferenceIsInTheManifest() throws IOException {
        StringBuilder want = new StringBuilder("# sha256  bytes  file\n");
        for (Fixture f : FIXTURES) {
            byte[] tune = Files.readAllBytes(TUNES.resolve(f.name() + ".ymxr"));
            want.append(manifest(f, tune, rows(tune), reference(tune))).append('\n');
        }
        Path at = KIT.resolve("MANIFEST.txt");
        String have = Files.exists(at) ? Files.readString(at) : "";
        if (!have.equals(want.toString())) {
            Files.writeString(KIT.resolve("MANIFEST.generated.txt"), want.toString());
        }
        assertEquals(want.toString(), have,
                "MANIFEST.txt does not match the tunes and the reader; MANIFEST.generated.txt does");
    }

    /**
     * The task names the versions a reader of the kit reads, and the
     * targets its tunes name.
     *
     * <p>An implementer of the kit works from TASK.md, and no check read it
     * back: it said a version word other than $0003 leaves the record empty
     * and that every tune names targets 0 to 13, where the kit has carried
     * a tune of version 4 on targets 14 to 24 since 0.4.2. An implementer
     * following it wrote an empty record for that tune.
     */
    @Test
    void theTaskNamesTheVersionsAndTheTargetsTheKitReaches() throws IOException {
        String task = Files.readString(KIT.resolve("TASK.md"));
        for (int version : new int[] {Tune.VERSION, Tune.VERSION_COLUMNS}) {
            assertTrue(task.contains(String.format(Locale.ROOT, "$%04X", version)),
                    "TASK.md leaves out version " + version + ", which a reader"
                    + " of this kit reads");
        }
        int most = 0;
        for (Fixture f : FIXTURES) {
            byte[] file = Files.readAllBytes(TUNES.resolve(f.name() + ".ymxr"));
            if (Tune.getWord(file, 4) != Tune.VERSION
                    && Tune.getWord(file, 4) != Tune.VERSION_COLUMNS) {
                continue;
            }
            for (List<Integer> row : targets(file)) {
                most = Math.max(most, row.get(0));
            }
        }
        assertTrue(most > 13, "no tune of the kit names a target above 13,"
                + " so this check reads nothing");
        assertTrue(task.contains("0 to " + most), "TASK.md names targets up to"
                + " another number than " + most + ", which the kit reaches");
    }

    /** The targets the rows of {@code file} name, each as a one-element row
     *  so the caller reads the number alone. */
    private static List<List<Integer>> targets(byte[] file) {
        List<List<Integer>> out = new ArrayList<>();
        for (String line : new String(reference(file),
                java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
            Matcher m = Pattern.compile("\"target\":(\\d+)").matcher(line);
            while (m.find()) {
                out.add(List.of(Integer.parseInt(m.group(1))));
            }
        }
        return out;
    }

    @Test
    void theReadmeCountsTheKit() throws IOException {
        String readme = Files.readString(KIT.resolve("README.md"));
        Matcher tunes = Pattern.compile("(\\d+) tunes").matcher(readme);
        assertTrue(tunes.find(), "README.md does not count the tunes");
        assertEquals(FIXTURES.size(), Integer.parseInt(tunes.group(1)), "README.md's tune count");
        long entries = 0;
        String task = Files.readString(KIT.resolve("TASK.md"));
        for (Fixture f : FIXTURES) {
            long lines = lines(reference(Files.readAllBytes(TUNES.resolve(f.name() + ".ymxr"))));
            entries += lines;
            Matcher row = Pattern.compile("^\\| `" + Pattern.quote(f.name()) + "\\.ymxr` \\| ([\\d,]+) \\|$",
                    Pattern.MULTILINE).matcher(task);
            assertTrue(row.find(), "TASK.md has no row for " + f.name());
            assertEquals(lines, Long.parseLong(row.group(1).replace(",", "")),
                    "TASK.md's lines for " + f.name());
        }
        Matcher count = Pattern.compile("([\\d,]+) entries").matcher(readme);
        assertTrue(count.find(), "README.md does not count the entries");
        assertEquals(entries, Long.parseLong(count.group(1).replace(",", "")), "README.md's entry count");
        Matcher version = Pattern.compile("version word \\$([0-9A-Fa-f]{4})").matcher(readme);
        assertTrue(version.find(), "README.md has no version word for wrong-version");
        assertEquals(Tune.getWord(Files.readAllBytes(TUNES.resolve("wrong-version.ymxr")), 4),
                Integer.parseInt(version.group(1), 16), "README.md's version word for wrong-version");
    }

    @Test
    void everyDumpOfTheKitReplaysToItsDumpAtItsOptions() throws IOException {
        for (Fixture f : FIXTURES) {
            if (f.built() != null) {
                continue;
            }
            YmDump.Song song = YmDump.read(Files.readAllBytes(Path.of("ym", "test", f.dump())));
            List<String> wrong = Check.of(song, f.options());
            assertTrue(wrong.isEmpty(), () -> f.name() + ":\n" + String.join("\n", wrong));
        }
    }
}
