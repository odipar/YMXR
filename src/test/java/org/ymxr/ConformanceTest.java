package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.ymxs.Chip;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Mapping;
import org.ymxs.YMXS.OneConnection;
import org.ymxs.YMXS.Pair;
import org.ymxs.YMXS.Part;
import org.ymxs.YMXS.Register;
import org.ymxs.YMXS.Remap;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.SetTone;
import org.ymxs.YMXS.Single;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Timing;
import org.ymxs.YMXS.Transformation;
import org.ymxs.YMXS.Two;
import org.ymxs.YMXS.TwoConnection;
import org.ymxs.YMXS.Voice;

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
    static final int WRONG_VERSION = Tune.VERSION + 1;

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
                    "a loop of one row, RR at 177, odd, so an unset row goes in before it and the loop's row is written twice, and the table packs at unit 2; six drums before it, the last stopped by a row"),
            Fixture.of("synergy", "Synergy Credits.ym", "",
                    "nine SIDs on Timers A and D at once, six of them named by both; a square replacing a square on the target of its effect, its place unmoved; the select changed without the source, and the count alone; a running source stopped by a row; a tone fine byte 0 with the coarse bit beside it; 5,377 rows, odd, so an unset row goes in at the end and the table packs at unit 2"),
            Fixture.of("preempt", "Digidrum preempt, built.ym", "",
                    "a drum starting on the voice a SID runs on stops the SID first, and the SID starts again when the drum ends; R8 passed between them with its column unset"),
            Fixture.of("retune", "Retrigger retune, built.ym", "",
                    "a one-row buzzer source on R13, restarted over a running timer with a new rate; select 7; stopped at the wrap alone"),
            Fixture.of("fine-zero", "Big - Samantha Fox Strip Poker 6.ym", "",
                    "a tone fine byte moving to 0: on voice A without the coarse set bit, on B and C with it;"
                    + " 215 rows, odd, so an unset row goes in at the end and the table packs at unit 2"),
            Fixture.built("four-timers", "`BuiltTunes.fourTimers`", BuiltTunes::fourTimers,
                    "all four effects on Timers A, D, B and C at 60 Hz; the rows section 4 allows and the dumps leave out: a count alone, a select alone with the count kept, bit 5 alone, bit 5 with a new source on a running timer, bit 6 alone, a stop with the volume set, the same source again, a target set while running and read at the next start, a target other than a volume register, a drum closing on 5, R13 set beside a buzzer, a source repeating to its row 2, a stop with no source running, values under a clear set bit, a fine byte and an envelope period byte other than 0 with the bit beside them"),
            Fixture.built("voices", "`BuiltTunes.voices`", BuiltTunes::voices,
                    "the four kinds of target that write several registers, one an"
                    + " effect, and the marker in a different column under each - the coarse"
                    + " nibble of a voice on Timer A and of a tone on Timer C, the noise period"
                    + " on Timer D, the envelope shape of a buzzer on Timer B; a source of"
                    + " several columns repeating to row 0, one repeating to a row above it, one"
                    + " that plays once and stops its timer at its marker, and a start over a"
                    + " running source of the same row count that leaves the place where it"
                    + " is"),
            Fixture.built("envelope", "`BuiltTunes.envelope`", BuiltTunes::envelope,
                    "`setEnvelope`, the one target whose marked register reads"
                    + " eight bits, so the marker's column is the envelope period's high"
                    + " byte, 0 to 127, and the column beside it a whole byte; a source of"
                    + " two columns repeating to a row above 0, one that plays once and"
                    + " stops its timer at its marker with 127 in a row of its marked"
                    + " column, a start that changes the source on a running timer, and the"
                    + " envelope shape set from column 13 while the period ticks"),
            Fixture.built("counted", "`BuiltTunes.counted`", BuiltTunes::counted,
                    "a counted source on each of the six targets whose"
                    + " register reads every bit of its byte - `setR0` and `setR4` on"
                    + " Timer A, `setR7` on Timer D, `setR11` on Timer B, `setR12` and"
                    + " `setR2` on Timer C - with rows of bit 7 set in every one of"
                    + " them, and index entries whose bit 31 is 1 and whose bits 30"
                    + " to 0 are the offset; a square on R8 in the same file, which the"
                    + " marker ends; a counted source of one row, one that plays once"
                    + " and stops its timer at its count, one repeating to a row above"
                    + " 0; a target set while an effect runs and read at the next"
                    + " start; a row that sets R12 as it stops the effect running on"
                    + " it"),
            Fixture.built("envelope-counted", "`BuiltTunes.envelopeCounted`",
                    BuiltTunes::envelopeCounted,
                    "a counted source of two columns on `setEnvelope`, whose"
                    + " two registers read every bit of their byte, so its rows are whole"
                    + " bytes and a tick counts them; the high byte runs past 127, where"
                    + " a marked source of this target stops, and reaches 255, a period"
                    + " of 65,323; one source repeating to a row above 0 and one that"
                    + " plays once and stops its timer at its count, a start that changes"
                    + " the source on a running timer, and the envelope shape set from"
                    + " column 13 while the period ticks"),
            Fixture.built("deferred", "`BuiltTunes.deferred`", BuiltTunes::deferred,
                    "deferred starts, bit 3 of the control column, on Timer A and on"
                    + " Timer C: a fall after a loop, a source with the place at its number,"
                    + " bit 5 at 0, a deferred start a stop drops, and one on a stopped"
                    + " timer"),
            Fixture.built("mapped", "`ConformanceTest.mapped`", () -> schema(mapped()),
                    "version 10 at 50 Hz; optional lookups, fades, deferred snapshots"
                    + " and kept mappings. See [Mapped transformations](#mapped-transformations)."),
            Fixture.built("wrong-version", "`ConformanceTest.wrongVersion`", () -> wrongVersion(),
                    String.format(Locale.ROOT, "chambers with the version word $%04X: a reader"
                            + " produces an empty record of it", WRONG_VERSION)));

    /** chambers with another version in its header: what a reader reports
     *  no report of (R6.1). */
    static Tune.Written wrongVersion() {
        try {
            byte[] dump = Files.readAllBytes(Path.of("ym", "test", "Chambers of Shaolin 5 - you blew it!.ym"));
            Tune.Written it = YmToYmxr.convert(dump, List.of(), new Report()).written();
            byte[] file = it.file().clone();
            Tune.putWord(file, 4, WRONG_VERSION);
            return it.withFile(file);
        } catch (IOException failed) {
            throw new IllegalStateException(failed);
        }
    }

    /** A YMXS structure as {@code ymxs-to-ymxr} writes it: padded to the
     *  writer's unit, its columns made by the schema (doc/ymxs.md), and
     *  packed through the ring the writer chooses, the tune's title its
     *  name (tools.md 7). */
    static Tune.Written schema(org.ymxs.YMXS.Tune tune) {
        Report report = new Report();
        Schema.Made made = Schema.of(Padding.toUnit(tune, YmToYmxr.UNIT, report));
        return Tune.write(made.columns(), made.sources(), made.rate(), YmToYmxr.UNIT,
                Tune.CHOOSE, Tune.packers(), report, tune.title());
    }

    /**
     * Volume transformation Vn: sixteen entries with entry k = floor(k
     * * n / 15) (YMXS, SPEC.md 3.6.5).
     */
    private static Transformation volume(int n) {
        List<Integer> entries = new ArrayList<>();
        for (int k = 0; k < 16; k++) {
            entries.add(k * n / 15);
        }
        return new Transformation("V" + n, entries);
    }

    /**
     * Converts byte keys to volumes: entry k = floor(k / 17), inverted
     * around 15 when {@code down} is set.
     */
    private static Transformation steps(boolean down) {
        List<Integer> entries = new ArrayList<>();
        for (int k = 0; k < 256; k++) {
            entries.add(down ? 15 - k / 17 : k / 17);
        }
        return new Transformation(down ? "steps down" : "steps", entries);
    }

    /** A start of {@code source} on {@code register} with both resets. */
    private static Start start(Register register, Single source, int by, int count,
                               Mapping mapping) {
        return new Start(new OneConnection(Tunes.setting(register), source),
                new Timing(Chip.prescaler(by), count, true, true), false, mapping);
    }

    /**
     * Key rows of {@link #mapped}: fall start, remap while its
     * replacement is pending, later start reusing kept mapping, and
     * repeat row.
     */
    static final int FALL = 6;
    static final int PENDING = 10;
    static final int UNSET = 20;
    static final int REPEAT = 32;

    /**
     * The version 10 conformance tune: four effects exercise fades,
     * kept mappings, deferred snapshots, remaps before first start and
     * byte-sized keys (SPEC.md 1.10, 3.4). Encoded through the schema
     * as the tools encode YMXS.
     *
     * <p>Timer A fades a setR8 loop through decreasing volume tables.
     * Timer B starts a fall, defers a square, then remaps the fall
     * while the square retains its captured mapping. A later start
     * reuses the remap's kept value. Timer C remaps before starting
     * setToneC and later restores identity. Timer D runs a counted
     * byte-key ramp through two volume tables. The repeat row restarts
     * A, B and C and retunes D, preserving its kept mapping.
     */
    static org.ymxs.YMXS.Tune mapped() {
        Single pulse = Tunes.repeating("pulse", List.of(15, 12, 9, 6), 0);
        Single square = Tunes.repeating("square 15", List.of(15, 0), 0);
        List<Integer> falling = new ArrayList<>();
        List<Integer> keys = new ArrayList<>();
        for (int r = 0; r < 16; r++) {
            falling.add(15 - r);
            keys.add(r * 17);
        }
        Single fall = Tunes.once("fall", falling);
        Single ramp = Tunes.repeating("ramp", keys, 0);
        Pair arpeggio = new Pair("arpeggio", Tunes.repeating(
                List.of(new Two(0x00, 2), new Two(0x80, 1), new Two(0x40, 1)), 0));
        List<Integer> raised = new ArrayList<>();
        for (int k = 0; k < 16; k++) {
            raised.add(Math.min(k + 1, 15));
        }
        Mapping up = Tunes.mapping(Tunes.IDENTITY, new Transformation("coarse up", raised));
        Start tone = new Start(new TwoConnection(new SetTone(Voice.C), arpeggio),
                new Timing(Chip.prescaler(200), 100, true, true), false, up);
        Start fading = start(Register.R8, pulse, 50, 100, Tunes.mapping(volume(15)));
        Start loud = start(Register.R9, square, 50, 140, Tunes.mapping(volume(10)));
        Start sweep = start(Register.R10, ramp, 200, 192, Tunes.mapping(steps(false)));
        List<Map<Timer, Effect>> effects = new ArrayList<>();
        for (int r = 0; r < 48; r++) {
            effects.add(new EnumMap<>(Timer.class));
        }
        int[] fade = {12, 9, 6, 3, 0};
        for (int pass : new int[] {0, 30}) {
            for (int s = 0; s < fade.length; s++) {
                effects.get(pass + 4 + 2 * s).put(Timer.A,
                        new Remap(Tunes.mapping(volume(fade[s]))));
            }
            effects.get(pass + 14).put(Timer.A, new Remap(Tunes.NO_PARTS));
        }
        for (int r : new int[] {0, REPEAT}) {
            effects.get(r).put(Timer.A, fading);
            effects.get(r).put(Timer.B, loud);
        }
        effects.get(0).put(Timer.D, sweep);
        effects.get(2).put(Timer.C, new Remap(up));
        effects.get(4).put(Timer.C, tone);
        effects.get(FALL).put(Timer.B, start(Register.R9, fall, 200, 150,
                Tunes.mapping(volume(5))));
        effects.get(8).put(Timer.B, new Start(new OneConnection(Tunes.setting(Register.R9),
                square), new Timing(Chip.prescaler(50), 140, false, true), true,
                Tunes.mapping(volume(10))));
        effects.get(PENDING).put(Timer.B, new Remap(Tunes.mapping(volume(12))));
        effects.get(UNSET).put(Timer.B, start(Register.R9, square, 50, 140,
                Tunes.mapping(volume(12))));
        effects.get(16).put(Timer.D, new Remap(Tunes.mapping(steps(true))));
        effects.get(20).put(Timer.C, Tunes.STOP);
        effects.get(REPEAT).put(Timer.C, tone);
        effects.get(REPEAT).put(Timer.D,
                new Retune(new Timing(Chip.prescaler(200), 160, false, false)));
        effects.get(36).put(Timer.B, new Remap(Tunes.mapping(volume(12))));
        effects.get(38).put(Timer.C, new Remap(Tunes.NO_PARTS));
        effects.get(40).put(Timer.D, new Remap(Tunes.mapping(steps(false))));
        effects.get(44).put(Timer.B, new Remap(Tunes.mapping(volume(5))));
        effects.get(44).put(Timer.C, Tunes.STOP);
        // the tones of the three voices on, voice C's period the one the
        // arpeggio's first row writes
        Map<Register, Integer> first = Map.of(Register.R0, 0x1C, Register.R1, 1,
                Register.R2, 0x7D, Register.R3, 1, Register.R4, 0x00, Register.R5, 2,
                Register.R7, 0x38);
        List<Row> rows = new ArrayList<>();
        for (int r = 0; r < effects.size(); r++) {
            rows.add(new Row(r == 0 ? first : Map.of(), effects.get(r)));
        }
        return new org.ymxs.YMXS.Tune("mapped", "", "ConformanceTest", 50,
                Tunes.repeating(rows, REPEAT));
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

    /** The tune's table as a DTX0 file: the sixteen-byte header with R, C
     *  and RR (SPEC.md 3.1), then row 0 to R minus one, each its C columns
     *  in order, 30 or 34 by version (DTX, SPEC.md 2.1); what a reader
     *  reads the table from, since the DTX3 table's packing is DTX's and
     *  not this specification's. Empty for a file of another version. */
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
        assertTrue(task.contains(String.format(Locale.ROOT, "$%04X", Tune.VERSION)),
                "TASK.md leaves out version " + Tune.VERSION + ", which a reader of"
                + " this kit reads");
        int most = 0;
        for (Fixture f : FIXTURES) {
            byte[] file = Files.readAllBytes(TUNES.resolve(f.name() + ".ymxr"));
            if (!Tune.reads(Tune.getWord(file, 4))) {
                continue;
            }
            for (List<Integer> row : targets(file)) {
                most = Math.max(most, row.get(0));
            }
        }
        assertTrue(most > 13, "no tune of the kit names a target above 13,"
                + " so this check is empty");
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

    /** README.md 1 against the kit, each count of its tunes and the count
     *  of the entries; TASK.md's lines of each tune; and README.md's
     *  version word of {@code wrong-version}. */
    @Test
    void theReadmeCountsTheKit() throws IOException {
        String text = Files.readString(KIT.resolve("README.md"));
        String readme = text.substring(0, text.indexOf("**2."));
        Matcher tunes = Pattern.compile("(\\d+) tunes").matcher(readme);
        int counts = 0;
        while (tunes.find()) {
            assertEquals(FIXTURES.size(), Integer.parseInt(tunes.group(1)),
                    "README.md 1's tune count");
            counts++;
        }
        assertTrue(counts > 0, "README.md 1 does not count the tunes");
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
        Matcher version = Pattern.compile("version word \\$([0-9A-Fa-f]{4})").matcher(text);
        assertTrue(version.find(), "README.md has no version word for wrong-version");
        assertEquals(Tune.getWord(Files.readAllBytes(TUNES.resolve("wrong-version.ymxr")), 4),
                Integer.parseInt(version.group(1), 16), "README.md's version word for wrong-version");
    }

    /**
     * The rows file as TASK.md 3 and README.md 3 lay it out, the offset
     * and the bytes of R, C and RR, the bytes of the header, W and the
     * column count by version, against every rows file of the kit; the
     * columns of a row and of an effect in SPEC.md 4.2 and 4.3; and the
     * kit's tune of version 10 as README.md 3 and 6, SOURCES.md and SPEC.md
     * 7.6 name it, its version and its rate read from the file.
     */
    @Test
    void theDocumentsReadTheColumnsAndTheVersionOfEachTune() throws IOException {
        String text = Files.readString(KIT.resolve("TASK.md"));
        String task = text.replaceAll("\\s+", " ");
        String readme = flat(KIT.resolve("README.md"));
        String sources = Files.readString(KIT.resolve("SOURCES.md"));
        String spec = flat(KIT.resolve("../SPEC.md"));
        assertEquals(List.of(Columns.C, Tune.OLDEST, Tune.UNMAPPED, Columns.MAPPED, Tune.VERSION),
                numbers(task, "(\\d+) columns in a tune file of versions (\\d+) to (\\d+), and"
                        + " (\\d+) in one of version (\\d+)"), "TASK.md 3's columns by version");
        List<Integer> layout = numbers(readme, "a (\\d+)-byte header, `R` at bytes (\\d+) to"
                + " (\\d+), `C` at (\\d+) and (\\d+), (\\d+) for a tune of version (\\d+) and"
                + " (\\d+) for one of version (\\d+), `RR` at (\\d+) to (\\d+) and `W` ="
                + " (\\d+) at (\\d+), then `R` rows of `C` bytes");
        assertEquals(13, layout.size(), "README.md 3's rows file");
        assertEquals(List.of(Columns.C, Tune.UNMAPPED, Columns.MAPPED, Tune.VERSION),
                layout.subList(5, 9), "README.md 3's columns by version");
        int header = layout.get(0);
        // a field's name, then its offset and its bytes
        Map<String, List<Integer>> fields = new TreeMap<>(Map.of(
                "R", List.of(layout.get(1), layout.get(2) - layout.get(1) + 1),
                "C", List.of(layout.get(3), layout.get(4) - layout.get(3) + 1),
                "RR", List.of(layout.get(9), layout.get(10) - layout.get(9) + 1)));
        Map<String, List<Integer>> listed = new TreeMap<>();
        Matcher field = Pattern.compile("^\\| (\\d+) \\| (\\d+) \\| `(R|C|RR)`,",
                Pattern.MULTILINE).matcher(text);
        while (field.find()) {
            listed.put(field.group(3), List.of(Integer.parseInt(field.group(1)),
                    Integer.parseInt(field.group(2))));
        }
        assertEquals(fields, listed, "TASK.md 3's fields against README.md 3's");
        assertEquals(List.of(header, header), List.of(
                numbers(task, "is the tune's table: a (\\d+)-byte header").get(0),
                numbers(text, "(?m)^\\| (\\d+) \\| `C` times `R` \\| the rows \\|$").get(0)),
                "TASK.md 3's bytes of the header against README.md 3's");
        int effects = Columns.MAPPED - Columns.MAPPING;
        int each = (Columns.MAPPING - Columns.EFFECT) / effects;
        assertEquals(List.of(Columns.C - 1, Columns.MAPPING, Columns.MAPPED - 1, Tune.VERSION),
                numbers(spec, "Read the current row, columns 0 to (\\d+), and (\\d+) to (\\d+)"
                        + " in version (\\d+)"), "SPEC.md 4.2 step 2's columns of a row");
        Matcher step = Pattern.compile("perform 4\\.3 on its (\\w+) columns, and (\\w+) in"
                + " version (\\d+)").matcher(spec);
        assertTrue(step.find(), "SPEC.md 4.2 step 3 counts no columns of an effect");
        assertEquals(List.of(each, each + 1, Tune.VERSION),
                List.of(ConsistencyTest.spelled(step.group(1)),
                        ConsistencyTest.spelled(step.group(2)), Integer.parseInt(step.group(3))),
                "SPEC.md 4.2 step 3's columns of an effect");
        assertEquals(List.of(Columns.EFFECT, each, Columns.EFFECT + each - 1, each,
                Columns.MAPPING, Tune.VERSION), numbers(spec, "columns (\\d+) \\+ (\\d+)i to"
                + " (\\d+) \\+ (\\d+)i, its mapping column M, column (\\d+) \\+ i in version"
                + " (\\d+)"), "SPEC.md 4.3's columns of effect i");
        List<String> mapped = new ArrayList<>();
        for (Fixture f : FIXTURES) {
            byte[] file = Files.readAllBytes(TUNES.resolve(f.name() + ".ymxr"));
            int version = Tune.getWord(file, 4);
            if (!Tune.reads(version)) {
                continue;
            }
            byte[] rows = Files.readAllBytes(TUNES.resolve(f.name() + ".rows"));
            org.dtx.Table table = TuneFile.read(file).table();
            Map<String, Integer> values = Map.of("R", table.rows(), "C", table.columns(),
                    "RR", table.repeat());
            for (Map.Entry<String, List<Integer>> one : fields.entrySet()) {
                assertEquals((long) values.getOrDefault(one.getKey(), -1), big(rows,
                        one.getValue().get(0), one.getValue().get(1)), f.name() + ".rows' "
                        + one.getKey());
            }
            assertEquals(layout.get(11), rows[layout.get(12)] & 0xFF, f.name() + ".rows' W");
            assertEquals(header + table.columns() * table.rows(), rows.length,
                    f.name() + ".rows' bytes");
            assertEquals(version == Tune.VERSION ? Columns.MAPPED : Columns.C, table.columns(),
                    f.name() + "'s column count");
            if (version != Tune.VERSION) {
                continue;
            }
            mapped.add(f.name());
            String name = Pattern.quote(f.name());
            List<Integer> written = List.of(version, Tune.getWord(file, Tune.FRAME_RATE_AT));
            assertEquals(List.of(version), numbers(readme, "`" + name + "` as a YMXS structure"
                    + " that the schema of `ymxs-to-ymxr` writes at version (\\d+)"),
                    "README.md 3's version of " + f.name());
            assertEquals(written, numbers(readme, "\\| `" + name + "` \\| version (\\d+) at"
                    + " (\\d+) Hz"), "README.md 6's version and rate of " + f.name());
            assertEquals(written, numbers(sources, "\\| `" + name + "` \\|[^\\n]*\\| version"
                    + " (\\d+) at (\\d+) Hz"), "SOURCES.md's version and rate of " + f.name());
            assertEquals(List.of(version), numbers(spec, "`" + name + "\\.ymxr`, a tune of"
                    + " version (\\d+)"), "SPEC.md 7.6's version of " + f.name());
        }
        assertEquals(1, mapped.size(), "the kit's tunes of version " + Tune.VERSION + ": " + mapped);
    }

    /**
     * Checks mapped-fixture documentation against the structure:
     * transformation order on A and B, fade intervals, largest ramp key
     * and table length.
     */
    @Test
    void theDocumentsReadTheFiguresOfTheMappedTune() throws IOException {
        List<Row> rows = Tunes.rows(mapped());
        List<String> through = new ArrayList<>();
        List<Integer> fade = new ArrayList<>();
        for (Timer timer : List.of(Timer.A, Timer.B)) {
            for (int r = 0; r < REPEAT; r++) {
                Effect e = rows.get(r).effects().get(timer);
                Mapping mapping = e == null ? null : Tunes.mapping(e);
                if (mapping == null) {
                    continue;
                }
                if (timer == Timer.A && e instanceof Remap) {
                    fade.add(r);
                }
                for (Part part : mapping.parts()) {
                    if (part instanceof Transformation t && t.name().matches("V\\d+")) {
                        through.add(t.name());
                    }
                }
            }
        }
        Set<Integer> gaps = new TreeSet<>();
        for (int i = 1; i < fade.size(); i++) {
            gaps.add(fade.get(i) - fade.get(i - 1));
        }
        assertEquals(1, gaps.size(), "the rows between the remaps of the fade: " + fade);
        String every = List.of("first", "second", "third", "fourth")
                .get(gaps.iterator().next() - 1);
        if (!(rows.get(0).effects().get(Timer.D) instanceof Start ramp)
                || !(ramp.mapping().parts().get(0) instanceof Transformation steps)) {
            throw new AssertionError("row 0 starts no source on Timer D through a transformation");
        }
        int keys = Collections.max(Tunes.values(Tunes.source(ramp.connection())));
        for (Path at : List.of(KIT.resolve("README.md"), KIT.resolve("SOURCES.md"))) {
            String text = Files.readString(at);
            int start = text.indexOf("### Mapped transformations");
            assertTrue(start >= 0, at + " has no mapped transformation cases");
            int end = text.indexOf("\n**7.", start);
            String description = text.substring(start, end < 0 ? text.length() : end);
            List<String> named = new ArrayList<>();
            Matcher name = Pattern.compile("\\bV\\d+\\b").matcher(description);
            while (name.find()) {
                named.add(name.group());
            }
            assertEquals(through, named, at + "'s transformations of Timers A and B");
            assertTrue(description.contains("remapped every " + every + " row"),
                    at + " lacks \"remapped every " + every + " row\"");
            assertEquals(List.of(keys), numbers(description, "keys reach (\\d+)"),
                    at + "'s largest key of the ramp");
            assertEquals(List.of(steps.entries().size()), numbers(description,
                    "a transformation of (\\d+) entries"), at + "'s entries of the ramp's mapping");
        }
    }

    /** The whole number of {@code bytes} bytes at {@code at}, most
     *  significant byte first. */
    private static long big(byte[] file, int at, int bytes) {
        long out = 0;
        for (int i = 0; i < bytes; i++) {
            out = out << 8 | file[at + i] & 0xFF;
        }
        return out;
    }

    /** A document's text with each run of white space one space, so a
     *  pattern matches a sentence across a line break. */
    private static String flat(Path at) throws IOException {
        return Files.readString(at).replaceAll("\\s+", " ");
    }

    /** The groups of the first match of {@code pattern} in {@code text},
     *  each a whole number, or an empty list where the pattern does not
     *  match. */
    private static List<Integer> numbers(String text, String pattern) {
        Matcher m = Pattern.compile(pattern).matcher(text);
        List<Integer> out = new ArrayList<>();
        if (m.find()) {
            for (int g = 1; g <= m.groupCount(); g++) {
                out.add(Integer.parseInt(m.group(g)));
            }
        }
        return out;
    }

    /**
     * Verifies the fall ends after the pending-start remap and before
     * the later start. Thus the remap affects the fall, installation
     * uses the captured square mapping, and the later start reuses the
     * kept mapping (SPEC.md 1.10.3).
     */
    @Test
    void theMappedTuneRemapsWhileAStartIsPending() {
        List<Row> rows = Tunes.rows(mapped());
        if (!(rows.get(FALL).effects().get(Timer.B) instanceof Start fall)) {
            throw new AssertionError("row " + FALL + " starts no source on Timer B");
        }
        Timing rate = fall.timing();
        long ticks = (long) Tunes.size(Tunes.rows(Tunes.source(fall.connection())))
                * Chip.divides(rate.prescaler()) * Chip.ticks(rate.count());
        assertTrue(rate.timerReset() && ticks * mapped().rate()
                > (long) (PENDING - FALL) * Chip.CLOCK, "the fall ends before row " + PENDING);
        assertTrue(ticks * mapped().rate() < (long) (UNSET - FALL) * Chip.CLOCK,
                "the fall ends after row " + UNSET);
        List<Effect> between = new ArrayList<>();
        for (int r = FALL + 1; r < PENDING; r++) {
            Effect e = rows.get(r).effects().get(Timer.B);
            if (e != null) {
                between.add(e);
            }
        }
        assertTrue(between.size() == 1 && between.get(0) instanceof Start s && s.deferred(),
                "Timer B between rows " + FALL + " and " + PENDING + ": " + between);
        assertTrue(rows.get(PENDING).effects().get(Timer.B) instanceof Remap,
                "row " + PENDING + " remaps Timer B");
        for (int r = PENDING + 1; r < UNSET; r++) {
            assertNull(rows.get(r).effects().get(Timer.B), "row " + r + " on Timer B");
        }
        assertTrue(rows.get(UNSET).effects().get(Timer.B) instanceof Start start
                && !start.deferred(), "row " + UNSET + " starts Timer B at its frame");
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
