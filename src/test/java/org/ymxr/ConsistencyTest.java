package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.ymxr.doc.Documents;
import org.junit.jupiter.api.Test;

/**
 * The documents against themselves: every reference that can be followed,
 * every figure that can be recomputed.
 *
 * <p>{@code HouseStyleTest} checks the prose against {@code AGENTS.md} and
 * {@code GlossaryTest} the terms against the glossary. This checks the
 * numbers and the pointers, which drift as a document is
 * edited: a requirement renumbered, a section renamed, a column added, a
 * ratio left over from the figures before it.
 *
 * <p>Every check here found something on the day it was written.
 */
final class ConsistencyTest {

    private static final Path SPEC = Path.of("doc/SPEC.md");
    private static final Path REQ = Path.of("doc/requirements.md");
    private static final Path GLO = Path.of("doc/glossary.md");
    private static final Path TERM = Path.of("doc/terminology.md");
    private static final Path EXP = Path.of("doc/experiments.md");
    private static final Path PERF = Path.of("doc/performance.md");
    private static final Path PLAN = Path.of("doc/plan.md");
    private static final Path WRITING = Path.of("doc/writing.md");

    /** Every document of the repository, which every check here reads. */
    private static final List<Path> DOCUMENTS = documents();

    private static List<Path> documents() {
        try (java.util.stream.Stream<Path> tree = Files.walk(Path.of("."))) {
            return tree.filter(Files::isRegularFile)
                    .filter(at -> at.toString().endsWith(".md"))
                    .filter(at -> !at.toString().contains("/target/"))
                    .filter(at -> !at.toString().contains("/.git"))
                    .sorted()
                    .toList();
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p);
    }

    /** A claim in prose, where any space may be the line wrap. */
    private static Pattern wrapped(String claim) {
        return Pattern.compile(claim.replace(" ", "\\s+"));
    }

    /** The rows of the column table: first index, last index, its text. */
    private static List<Object[]> columnTable(String spec) {
        int at = spec.indexOf("| column | what it reaches |");
        assertTrue(at >= 0, "SPEC.md has no column table");
        String block = spec.substring(at, spec.indexOf("\n\n", at));
        Matcher m = Pattern.compile("^\\| (\\d+)(?: to (\\d+))? \\| ([^|]+) \\|$",
                Pattern.MULTILINE).matcher(block);
        List<Object[]> rows = new ArrayList<>();
        while (m.find()) {
            int first = Integer.parseInt(m.group(1));
            int last = m.group(2) == null ? first : Integer.parseInt(m.group(2));
            rows.add(new Object[] {first, last, m.group(3).trim()});
        }
        return rows;
    }

    @Test
    void everyColumnIsNamedAsTheSpecificationNamesIt() throws IOException {
        // The report names a column on every row, in the player's
        // vocabulary for that column (AGENTS.md, one vocabulary), so
        // SPEC.md's table is the reference for it.
        List<Object[]> rows = columnTable(read(SPEC));
        List<String> astray = new ArrayList<>();
        for (Object[] r : rows) {
            int first = (Integer) r[0];
            int last = (Integer) r[1];
            String reaches = (String) r[2];
            for (int c = first; c <= last; c++) {
                String name = Tune.name(c);
                // A row is the column's name, and anything further
                // follows a comma: "R0, voice A tone period, fine",
                // "effect 0 timer control, Timer A's control register".
                boolean named = reaches.equals(name) || reaches.startsWith(name + ",");
                if (first == last && !named) {
                    astray.add("column " + c + " is named " + name
                            + " and the table reads " + reaches);
                } else if (first != last && (c - Columns.EFFECT) / 4
                        != (first - Columns.EFFECT) / 4) {
                    astray.add("column " + c + " falls outside the effect of its row");
                }
            }
        }
        assertEquals(List.of(), astray, "a column's name is not the specification's");
        assertEquals("R0", Tune.name(0));
        assertEquals("effect 3 timer count", Tune.name(Columns.C - 1));
    }

    @Test
    void theColumnTableAddsUpToWhatTheProseClaims() throws IOException {
        String spec = read(SPEC);
        List<Object[]> rows = columnTable(spec);
        int columns = 0;
        int next = 0;
        List<String> gaps = new ArrayList<>();
        List<String> astray = new ArrayList<>();
        for (Object[] r : rows) {
            int first = (Integer) r[0];
            int last = (Integer) r[1];
            if (first != next) {
                gaps.add("column " + next + " is where " + first + " stands");
            }
            // columns 0 to 13 reach R0 to R13, one a register
            if (first <= 13 && !((String) r[2]).startsWith("R" + first + ",")) {
                astray.add("column " + first + " reads " + r[2]);
            }
            next = last + 1;
            columns += last - first + 1;
        }
        assertTrue(gaps.isEmpty(), () -> "the column indices break: " + gaps);
        assertTrue(astray.isEmpty(), () -> "a register column is not its"
                + " register: " + astray);

        Matcher m = wrapped("(\\d+) columns of the 32 R\\d+\\.\\d+ allows, each"
                + " one byte, so a row is (\\d+) bytes").matcher(spec);
        assertTrue(m.find(), "SPEC.md has no column and byte count");
        int saidColumns = Integer.parseInt(m.group(1));
        int saidBytes = Integer.parseInt(m.group(2));
        int c = columns;
        assertTrue(saidColumns == c && saidBytes == c,
                () -> "the table has " + c + " columns of one byte; the prose"
                        + " reads " + saidColumns + " and " + saidBytes);
    }

    @Test
    void everyRequirementCitedIsDefined() throws IOException {
        Set<String> defined = new TreeSet<>();
        Matcher d = Pattern.compile("^- \\*\\*(R\\d+\\.\\d+)\\*\\*",
                Pattern.MULTILINE).matcher(read(REQ));
        while (d.find()) {
            defined.add(d.group(1));
        }
        List<String> dangling = new ArrayList<>();
        // A citation a document qualifies with DTX is DTX's requirements and
        // not this repository's: tools.md cites DTX's R5.6 and R5.11 for
        // what a unit requires of a column's bytes. Those are read out first.
        Pattern theirs = Pattern.compile("DTX'?s?\\s+R\\d+\\.\\d+(\\s+and\\s+R\\d+\\.\\d+)*");
        for (Path p : DOCUMENTS) {
            Matcher c = Pattern.compile("\\bR\\d+\\.\\d+\\b")
                    .matcher(theirs.matcher(read(p)).replaceAll(""));
            while (c.find()) {
                if (!defined.contains(c.group())) {
                    dangling.add(p + " cites " + c.group());
                }
            }
        }
        assertTrue(dangling.isEmpty(), () -> String.join("\n", dangling)
                + "\nrequirements.md defines " + defined);
    }

    @Test
    void everySectionCitedExists() throws IOException {
        String spec = read(SPEC);
        Set<String> headings = new TreeSet<>();
        Matcher h = Pattern.compile("^#{2,3} (\\d+(?:\\.\\d+)?)\\.? ",
                Pattern.MULTILINE).matcher(spec);
        while (h.find()) {
            headings.add(h.group(1));
        }
        List<String> missing = new ArrayList<>();
        Matcher r = Pattern.compile("[Ss]ection (\\d+(?:\\.\\d+)?)|\\((\\d\\.\\d+)"
                + "(?:, (\\d\\.\\d+))?(?:, (\\d\\.\\d+))?\\)").matcher(spec);
        while (r.find()) {
            for (int g = 1; g <= r.groupCount(); g++) {
                String ref = r.group(g);
                if (ref != null && !headings.contains(ref)) {
                    missing.add("SPEC.md points at " + ref);
                }
            }
        }
        assertTrue(missing.isEmpty(), () -> String.join("\n", missing)
                + "\nits headings are " + headings);
    }

    /** Every row of the glossary's table, its term and where it points. */
    @Test
    void theGlossaryIsInOrder() throws IOException {
        List<String[]> rows = Documents.glossaryRows(read(GLO));
        assertTrue(rows.size() > 40, () -> "the glossary read as " + rows.size()
                + " rows");
        List<String> wrong = Documents.outOfOrder(rows);
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong));
    }

    @Test
    void everyGlossaryRowPointsSomewhereReal() throws IOException {
        Set<String> sections = new TreeSet<>();
        Matcher h = Pattern.compile("^## (.+)$", Pattern.MULTILINE)
                .matcher(read(TERM));
        while (h.find()) {
            sections.add(h.group(1).trim().toLowerCase());
        }
        List<String> bad = new ArrayList<>();
        for (String[] row : Documents.glossaryRows(read(GLO))) {
            String where = row[2];
            if (where.startsWith("terminology.md,")) {
                String named = where.substring("terminology.md,".length())
                        .trim().toLowerCase();
                if (!sections.contains(named)) {
                    bad.add(row[0] + " points at terminology.md's \"" + named
                            + '"');
                }
            } else {
                String file = where.split("[ ,;]")[0];
                if (file.endsWith(".md")
                        && !Files.exists(Path.of("doc", file))
                        && !Files.exists(Path.of(file))) {
                    bad.add(row[0] + " points at " + file);
                }
            }
        }
        assertTrue(bad.isEmpty(), () -> String.join("\n", bad)
                + "\nterminology.md has " + sections);
    }

    @Test
    void everyLinkResolves() throws IOException {
        List<String> broken = Documents.links(DOCUMENTS);
        assertTrue(broken.isEmpty(), () -> String.join("\n", broken));
    }

    /**
     * The tone periods' share against the three in the same sentence.
     * Prose again, so the table check above does not reach it.
     */
    @Test
    void theToneShareIsTheSumOfTheThreeParts() throws IOException {
        Matcher share = wrapped("are (\\d+\\.\\d)%, (\\d+\\.\\d)% and"
                + " (\\d+\\.\\d)% of the packed bytes, (\\d+\\.\\d)% between"
                + " them").matcher(read(EXP));
        assertTrue(share.find(), "experiments.md has no tone shares");
        double sum = 0;
        for (int i = 1; i <= 3; i++) {
            sum += Double.parseDouble(share.group(i));
        }
        double said = Double.parseDouble(share.group(4));
        double got = Math.round(sum * 10.0) / 10.0;
        assertTrue(Math.abs(got - said) < 0.05, () -> "the three shares make "
                + got + "%, and the sentence reads " + said + '%');
    }

    /**
     * The envelope saving against the two figures in the same sentence,
     * and against what the corpus packs to. The figures are prose rather
     * than a table row, so the check above does not reach them.
     */
    @Test
    void theEnvelopeSavingIsTheDifferenceItReports() throws IOException {
        String experiments = read(EXP);
        Matcher both = wrapped("cost ([\\d,]+) bytes as SPEC\\.md has them, and"
                + " ([\\d,]+) under").matcher(experiments);
        assertTrue(both.find(), "experiments.md has no envelope pair");
        Matcher saved = wrapped("saves ([\\d,]+) bytes, (\\d+\\.\\d)% of the"
                + " ([\\d,]+) the corpus").matcher(experiments);
        assertTrue(saved.find(), "experiments.md has no envelope saving");
        long mine = number(both.group(1));
        long other = number(both.group(2));
        long says = number(saved.group(1));
        assertTrue(other - mine == says, () -> "the saving is written as "
                + says + ", and " + other + " less " + mine + " is "
                + (other - mine));
        long whole = number(saved.group(3));
        double percent = Math.round(says * 1000.0 / whole) / 10.0;
        assertTrue(Double.parseDouble(saved.group(2)) == percent,
                () -> "the saving is written as " + saved.group(2) + "% and is "
                        + percent + "% of " + whole);
        Matcher row = Pattern.compile("^\\| DTX2 files at `k` = 1 \\| ([\\d,]+) \\|",
                Pattern.MULTILINE).matcher(experiments);
        assertTrue(row.find(), "experiments.md has no row for k = 1");
        assertTrue(number(row.group(1)) == whole, () -> string(whole)
                + " is not the packing table's figure at k = 1, "
                + row.group(1));
    }

    /**
     * The two envelope cycles of SPEC.md 2.1.3 against the formula of
     * terminology.md 1.4 at the periods the clause names, and the high
     * period against the marker's bit.
     */
    @Test
    void theEnvelopeCyclesAreTheFormulaAtThosePeriods() throws IOException {
        Matcher formula = wrapped("envelope frequency = ([\\d,]+) / \\("
                + "(\\d+) x envelope period\\)").matcher(read(TERM));
        assertTrue(formula.find(), "terminology.md has no envelope formula");
        long clock = number(formula.group(1));
        long steps = number(formula.group(2));
        Matcher said = wrapped("period ([\\d,]+) is one cycle in (\\d+\\.\\d+)"
                + " seconds and period ([\\d,]+) one in (\\d+\\.\\d+)"
                + " seconds").matcher(read(SPEC));
        assertTrue(said.find(), "SPEC.md 2.1.3 has no envelope cycles");
        for (int pair = 1; pair <= 3; pair += 2) {
            long period = number(said.group(pair));
            double seconds = Math.round(steps * period * 100.0 / clock) / 100.0;
            double reads = Double.parseDouble(said.group(pair + 1));
            assertTrue(seconds == reads, () -> "period " + string(period)
                    + " is one cycle in " + seconds + " seconds, and the"
                    + " sentence reads " + reads);
        }
        assertEquals(0x7F * 256L + 0xFF, number(said.group(1)),
                "the period 2.1.3 names for 4.19 seconds is another");
        assertEquals(0xFFFF, number(said.group(3)),
                "the period a source of this target reaches is not the one 2.1.3 names");
        assertEquals(-1, Columns.MARKER[20],
                "a source on target 20 is counted, so no column of it marks (2.1.3)");
    }

    /**
     * The payload of a DTX1 table against the row it has in SPEC.md 3.1.2.
     * That row read "C times align(R)" for a release, which is a byte too
     * long for an odd R: the last column of a table has no padding after
     * it, as the writer shows at every R and C below.
     */
    @Test
    void theDtx1PayloadIsTheRowOfTheTable() throws IOException {
        assertTrue(read(SPEC).contains(
                "| 16 | (C - 1) times align(R) + R |"),
                "SPEC.md 3.1.2 has another length for the payload");
        for (int columns = 1; columns <= 3; columns++) {
            for (int rows = 1; rows <= 7; rows++) {
                byte[][] values = new byte[columns][rows];
                for (int c = 0; c < columns; c++) {
                    for (int r = 0; r < rows; r++) {
                        values[c][r] = (byte) (c * 16 + r + 1);
                    }
                }
                byte[] table = org.dtx.Dtx1.write(
                        org.dtx.Table.of(rows, 0, 1, values));
                int align = rows + (rows & 1);
                int said = 16 + (columns - 1) * align + rows;
                final int c = columns;
                final int r = rows;
                assertEquals(said, table.length, () -> "a table of " + r
                        + " rows and " + c + " columns is " + said
                        + " bytes under 3.1.2");
            }
        }
    }

    private static long number(String said) {
        return Long.parseLong(said.replace(",", ""));
    }

    private static String string(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    @Test
    void everyFigureInExperimentsRecomputes() throws IOException {
        Pattern frames = Pattern.compile("([\\d,]+) frames");
        Pattern rowOf = Pattern.compile(
                "^\\| ([^|]+) \\| ([\\d,]+) \\| (\\d+\\.\\d\\d) \\|([^|]*)\\|$");
        Pattern heading = Pattern.compile("^\\| \\| bytes \\| a frame \\| (.+) \\|$");
        Pattern againstRaw = Pattern.compile("(\\d+\\.\\d)x");
        Pattern againstFirst = Pattern.compile("(\\d+\\.\\d\\d)x");

        // A table's per-frame column is read against the frame count nearest
        // above it: the corpus tables count all 543 tunes, the gain table the
        // 41 with a .ymx beside them.
        long over = 0;
        long raw = 0;
        long first = 0;
        String against = "";
        int checked = 0;
        int ratios = 0;
        List<String> wrong = new ArrayList<>();
        List<String> lines = Files.readAllLines(EXP);
        for (int at = 0; at < lines.size(); at++) {
            String line = lines.get(at);
            Matcher f = frames.matcher(line);
            if (f.find()) {
                over = Long.parseLong(f.group(1).replace(",", ""));
                if (!line.startsWith("|")) {
                    raw = 0;
                }
            }
            Matcher head = heading.matcher(line);
            if (head.find()) {
                against = head.group(1).trim();
                first = 0;
                continue;
            }
            Matcher row = rowOf.matcher(line);
            if (!row.find() || over == 0) {
                continue;
            }
            long bytes = Long.parseLong(row.group(2).replace(",", ""));
            double said = Double.parseDouble(row.group(3));
            String label = row.group(1).trim();
            if (label.startsWith("raw rows")) {
                raw = bytes;
            }
            if (first == 0) {
                first = bytes;
            }
            checked++;
            double got = Math.round(bytes * 100.0 / over) / 100.0;
            if (Math.abs(got - said) > 0.005) {
                wrong.add(EXP + ":" + (at + 1) + " " + label + ": " + bytes
                        + " over " + over + " frames is " + got
                        + " a frame, not " + said);
            }
            // "against raw" divides the raw rows by the row; every other
            // ratio column divides the row by the table's first row
            if (against.equals("against raw")) {
                Matcher a = againstRaw.matcher(row.group(4));
                if (a.find() && raw > 0) {
                    ratios++;
                    double saidRatio = Double.parseDouble(a.group(1));
                    double gotRatio = Math.round(raw * 10.0 / bytes) / 10.0;
                    if (Math.abs(gotRatio - saidRatio) > 0.05) {
                        wrong.add(EXP + ":" + (at + 1) + " " + label + ": "
                                + raw + " over " + bytes + " is " + gotRatio
                                + "x, not " + saidRatio + 'x');
                    }
                }
            } else {
                Matcher a = againstFirst.matcher(row.group(4));
                if (a.find() && first > 0) {
                    ratios++;
                    double saidRatio = Double.parseDouble(a.group(1));
                    double gotRatio = Math.round(bytes * 100.0 / first) / 100.0;
                    if (Math.abs(gotRatio - saidRatio) > 0.005) {
                        wrong.add(EXP + ":" + (at + 1) + " " + label + ": "
                                + bytes + " over " + first + " is " + gotRatio
                                + "x, not " + saidRatio + 'x');
                    }
                }
            }
        }
        int seen = checked;
        int found = ratios;
        assertTrue(seen >= 5, () -> "only " + seen
                + " figures parsed; the check is asleep");
        assertTrue(found >= 4, () -> "only " + found
                + " ratios parsed; the check is half asleep");
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong));
    }

    /**
     * performance.md's play-call table by tune: the call on average, the
     * call at most, the advance on average, and the advance in the
     * costliest frame. The rig writes these figures and fails where the
     * table is not what it counts, so the table is the measurement and
     * the four checks below read the prose against it.
     */
    private static Map<String, long[]> playCalls(String perf) {
        Map<String, long[]> calls = new LinkedHashMap<>();
        Pattern row = Pattern.compile("^\\| ([^|]+?) \\| \\d+"
                + " \\| (\\d+) \\| (\\d+) \\| (\\d+) \\| (\\d+) \\|$");
        boolean inside = false;
        for (String line : perf.split("\n")) {
            if (line.startsWith("| tune | frames | on average |")) {
                inside = true;
            } else if (inside && !line.startsWith("|---")) {
                Matcher of = row.matcher(line);
                if (!of.matches()) {
                    break;
                }
                calls.put(of.group(1), new long[] {
                    Long.parseLong(of.group(2)), Long.parseLong(of.group(3)),
                    Long.parseLong(of.group(4)), Long.parseLong(of.group(5))});
            }
        }
        return calls;
    }

    /** The least and the greatest of one figure over that table. */
    private static long[] range(Map<String, long[]> calls,
            ToLongFunction<long[]> of) {
        long least = Long.MAX_VALUE;
        long most = Long.MIN_VALUE;
        for (long[] call : calls.values()) {
            least = Math.min(least, of.applyAsLong(call));
            most = Math.max(most, of.applyAsLong(call));
        }
        return new long[] {least, most};
    }

    /**
     * The program's clock in performance.md against itself: the handler's
     * cycles a tick at 50 Hz and at 60 Hz, the routine around the play call,
     * and the 44 cycles of the interrupt's entry, and the section's sums,
     * percentages and rounded savings read against those.
     * {@code test_ymxr.py -clock} reads the three figures against Hatari;
     * this reads what the section builds on them.
     */
    @Test
    void theClockCostsAreTheSumsTheSectionReports() throws IOException {
        String perf = read(PERF);
        String section = perf.substring(perf.indexOf("## The program's clock"),
                perf.indexOf("## Against YMX"));
        Matcher ticks = wrapped("the handler runs (\\d+) cycles a tick on average, its `rte`"
                + " among them, and at 60 Hz, a row every fourth, (\\d+)\\.").matcher(section);
        assertTrue(ticks.find(), "performance.md has no ticks of the program's clock");
        Matcher around = wrapped("runs (\\d+) cycles a row, the call apart").matcher(section);
        assertTrue(around.find(), "performance.md has no routine around the play call");
        Matcher entry = wrapped("the (\\d+) cycles of the interrupt's entry come on top")
                .matcher(section);
        assertTrue(entry.find(), "performance.md adds no entry to the clock's ticks");
        long enter = number(entry.group(1));
        long row = number(around.group(1));
        long[] tick = {number(ticks.group(1)) + enter, number(ticks.group(2)) + enter};
        long[] per = {150, 240};
        long[] rows = {50, 60};
        Matcher sum = wrapped("(\\d+) x (\\d+) \\+ (\\d+) x (\\d+) = ([\\d,]+)"
                + " cycles a second, (\\d+\\.\\d+) per cent").matcher(section);
        int read = 0;
        while (sum.find()) {
            String said = sum.group();
            assertTrue(read < 2, "the section has more sums than its two rates: " + said);
            assertEquals(per[read], number(sum.group(1)), said);
            assertEquals(tick[read], number(sum.group(2)), said);
            assertEquals(rows[read], number(sum.group(3)), said);
            assertEquals(row, number(sum.group(4)), said);
            long total = per[read] * tick[read] + rows[read] * row;
            assertEquals(total, number(sum.group(5)), said);
            assertEquals(Math.round(total * 10000.0 / 8021247) / 100.0,
                    Double.parseDouble(sum.group(6)), said);
            read++;
        }
        assertEquals(2, read, "the section's sums of its two rates");
        Matcher plain = wrapped("a tick without a row at (\\d+) or (\\d+) cycles of the"
                + " handler").matcher(section);
        assertTrue(plain.find(), "performance.md has no tick without a row");
        double mid = (number(plain.group(1)) + number(plain.group(2))) / 2.0 + enter;
        Matcher saved = wrapped("ran 50 such ticks a second more for a 50 Hz tune, about"
                + " ([\\d,]+) cycles").matcher(section);
        assertTrue(saved.find(), "performance.md has no saving of the 50 Hz clock");
        assertEquals(Math.round(50 * mid / 100.0) * 100, number(saved.group(1)), saved.group());
        Matcher spacing = wrapped("\\(4\\.10\\), about ([\\d,]+) cycles").matcher(section);
        assertTrue(spacing.find(), "performance.md has no cost of the even spacing");
        assertEquals(Math.round(40 * mid / 100.0) * 100, number(spacing.group(1)),
                spacing.group());
    }

    /**
     * plan.md's two opening ranges against performance.md's table. plan.md
     * records that every figure in it is against those, and no check read
     * the one document against the other: both ranges sat three commits
     * behind the player.
     */
    @Test
    void thePlanReadsTheCallRangesMeasured() throws IOException {
        Map<String, long[]> calls = playCalls(read(PERF));
        assertTrue(calls.size() >= 10, () -> "performance.md's play-call table"
                + " read as " + calls.size() + " rows");
        Matcher said = wrapped("A call is ([\\d,]+) to ([\\d,]+) cycles on"
                + " average by tune, and the costliest frame of a tune is"
                + " ([\\d,]+) to ([\\d,]+)").matcher(read(PLAN));
        assertTrue(said.find(), "plan.md has no call range");
        long[] average = range(calls, call -> call[0]);
        long[] most = range(calls, call -> call[1]);
        assertEquals(string(average[0]), said.group(1), "the least call on average");
        assertEquals(string(average[1]), said.group(2), "the greatest call on average");
        assertEquals(string(most[0]), said.group(3), "the least costliest frame");
        assertEquals(string(most[1]), said.group(4), "the greatest costliest frame");
    }

    /**
     * The frame procedure is the call less the advance, in both documents.
     * DTX reading its state block from a6 cut 36 cycles from the advance and
     * 12 off the call, so this figure rose 24 where the others fell.
     */
    @Test
    void theFrameProcedureIsTheCallLessTheAdvance() throws IOException {
        Map<String, long[]> calls = playCalls(read(PERF));
        long[] rest = range(calls, call -> call[0] - call[2]);
        Matcher perf = wrapped("The frame procedure is the rest, from"
                + " ([\\d,]+) to ([\\d,]+) cycles on average").matcher(read(PERF));
        assertTrue(perf.find(), "performance.md has no frame procedure range");
        assertEquals(string(rest[0]), perf.group(1), "performance.md's least");
        assertEquals(string(rest[1]), perf.group(2), "performance.md's greatest");
        Matcher plan = wrapped("The frame procedure is the rest,"
                + " ([\\d,]+) to ([\\d,]+)\\.").matcher(read(PLAN));
        assertTrue(plan.find(), "plan.md has no frame procedure range");
        assertEquals(string(rest[0]), plan.group(1), "plan.md's least");
        assertEquals(string(rest[1]), plan.group(2), "plan.md's greatest");
    }

    /**
     * The play call the ticks section reads its savings against, one
     * figure a tune, is the table's average call for that tune: the
     * sentence read 2,380, 1,985 and 2,073 from a player three releases
     * older than the table beside it.
     */
    @Test
    void theTickSavingsReadTheTablesCalls() throws IOException {
        Map<String, long[]> calls = playCalls(read(PERF));
        Matcher said = wrapped("cycles a frame come off those tunes, against a play"
                + " call of ([\\d,]+), ([\\d,]+) and ([\\d,]+)").matcher(read(PERF));
        assertTrue(said.find(), "performance.md has no play call beside the tick savings");
        for (int i = 0; i < 3; i++) {
            String tune = List.of("Synergy Credits", "DBA 2", "DBA 5").get(i);
            long[] call = Objects.requireNonNull(calls.get(tune),
                    "performance.md's table names no " + tune);
            assertEquals(string(call[0]), said.group(i + 1), tune + "'s call");
        }
    }

    /**
     * The share plan.md reads for the advance, and the two tunes it names,
     * against the table. The percentages round the table's figures, so a figure
     * that moves without its percentage moving is caught here.
     */
    @Test
    void thePlanReadsTheAdvanceShareMeasured() throws IOException {
        Map<String, long[]> calls = playCalls(read(PERF));
        Matcher said = wrapped("DTX's advance is (\\d+) to (\\d+) per cent of"
                + " an average call and (\\d+) to (\\d+) per cent of the"
                + " costliest frame: ([\\d,]+) of Turrican - world 4-3's"
                + " ([\\d,]+) and ([\\d,]+) of Synergy Credits' ([\\d,]+)")
                .matcher(read(PLAN));
        assertTrue(said.find(), "plan.md has no advance share");
        long[] share = range(calls, call -> Math.round(100.0 * call[2] / call[0]));
        assertEquals(share[0], Long.parseLong(said.group(1)), "the least share");
        assertEquals(share[1], Long.parseLong(said.group(2)), "the greatest share");
        long[] turrican = Objects.requireNonNull(calls.get("Turrican - world 4-3"),
                "performance.md's table names no Turrican - world 4-3");
        long[] synergy = Objects.requireNonNull(calls.get("Synergy Credits"),
                "performance.md's table names no Synergy Credits");
        assertEquals(string(turrican[3]), said.group(5), "Turrican's advance");
        assertEquals(string(turrican[1]), said.group(6), "Turrican's costliest frame");
        assertEquals(string(synergy[3]), said.group(7), "Synergy Credits' advance");
        assertEquals(string(synergy[1]), said.group(8),
                "Synergy Credits' costliest frame");
        long one = Math.round(100.0 * turrican[3] / turrican[1]);
        long two = Math.round(100.0 * synergy[3] / synergy[1]);
        assertEquals(Math.min(one, two), Long.parseLong(said.group(3)),
                "the least share of a costliest frame");
        assertEquals(Math.max(one, two), Long.parseLong(said.group(4)),
                "the greatest share of a costliest frame");
    }

    /**
     * performance.md's refill parts against its table: the fixed part
     * outside the decoder and the heaviest parse inside make the advance in
     * Turrican's costliest frame. The sentence gave the sum from before DTX
     * took its state block in a6, and the table gave the figure after it.
     */
    @Test
    void theRefillPartsAddUpToTheAdvanceMeasured() throws IOException {
        String perf = read(PERF);
        Matcher parts = wrapped("A refill of ([\\d,]+) outside and the heaviest"
                + " ([\\d,]+) inside is the ([\\d,]+) the table above reads")
                .matcher(perf);
        assertTrue(parts.find(), "performance.md has no refill parts");
        long outside = number(parts.group(1));
        long inside = number(parts.group(2));
        long whole = number(parts.group(3));
        assertEquals(whole, outside + inside, () -> outside + " outside and "
                + inside + " inside make " + (outside + inside)
                + ", and the sentence reads " + whole);
        long[] turrican = Objects.requireNonNull(
                playCalls(perf).get("Turrican - world 4-3"),
                "performance.md's table names no Turrican - world 4-3");
        assertEquals(string(turrican[3]), string(whole),
                "the table reads another advance in the costliest frame");
        Matcher spends = wrapped("The advance spends ([\\d,]+) cycles a refill"
                + " outside the decoder").matcher(perf);
        assertTrue(spends.find(), "performance.md has no fixed part");
        assertEquals(string(outside), string(number(spends.group(1))),
                "the two sentences read the fixed part differently");
    }

    /** The words the section against YMX spells a count with. */
    private static final Map<String, Integer> COUNT = Map.of(
            "three", 3, "twelve", 12, "fifteen", 15, "twenty-one", 21,
            "twenty-four", 24, "thirty", 30);

    /** A ratio spelled as a part: an eighth less is one part in eight. */
    private static final Map<String, Integer> PART = Map.of(
            "a half", 2, "a third", 3, "a quarter", 4, "a fifth", 5, "a sixth", 6,
            "a seventh", 7, "an eighth", 8, "a ninth", 9, "a tenth", 10);

    private static long part(String said) {
        String words = said.replaceAll("\\s+", " ");
        return Objects.requireNonNull(PART.get(words), () -> "no part spelled " + words);
    }

    private static int count(String word) {
        return Objects.requireNonNull(COUNT.get(word), () -> "no count spelled " + word);
    }

    /** The table against YMX by tune and player: the call on average, at
     *  the 99th call in a hundred and at most. */
    private static Map<String, long[]> againstYmx(String section) {
        Map<String, long[]> rows = new LinkedHashMap<>();
        Matcher m = Pattern.compile("^\\| ([^|]+?) \\| (YMX 0\\.10\\.1|YMXR) \\| (\\d+)"
                + " \\| (\\d+) \\| (\\d+) \\|$", Pattern.MULTILINE).matcher(section);
        while (m.find()) {
            rows.put(m.group(1) + ", " + m.group(2), new long[] {Long.parseLong(m.group(3)),
                Long.parseLong(m.group(4)), Long.parseLong(m.group(5))});
        }
        return rows;
    }

    /**
     * The section against YMX read against its table, the play-call table
     * and ym/cost.sh. YMX's rows are in this document alone, measured once
     * on that player, so its figures are read against each other:
     * the refill's two kinds of call make its calls and its average. The
     * sentences read 721 where the play-call table makes 722 and placed a
     * frame counted from 0 as the 5,346th.
     */
    @Test
    void theComparisonWithYmxReadsItsTables() throws IOException {
        String perf = read(PERF);
        String section = perf.substring(perf.indexOf("## Against YMX"), perf.indexOf("## A tick"));
        Map<String, long[]> t = againstYmx(section);
        assertEquals(4, t.size(), () -> "the table against YMX read as " + t.keySet());
        long[] sy = Objects.requireNonNull(t.get("Synergy Credits, YMX 0.10.1"), "no YMX row, Synergy");
        long[] sr = Objects.requireNonNull(t.get("Synergy Credits, YMXR"), "no YMXR row, Synergy");
        long[] ty = Objects.requireNonNull(t.get("Turrican - world 4-3, YMX 0.10.1"),
                "no YMX row, Turrican");
        long[] tr = Objects.requireNonNull(t.get("Turrican - world 4-3, YMXR"),
                "no YMXR row, Turrican");
        Matcher ratio = wrapped("YMXR costs (an? \\w+) less on average on Turrican - world 4-3"
                + " and (an? \\w+) less on Synergy Credits, and at their worst (\\d+) per cent"
                + " more on Turrican - world 4-3 and (\\d+) per cent less on Synergy Credits")
                .matcher(section);
        assertTrue(ratio.find(), "performance.md has no ratio against YMX");
        assertEquals(Math.round(ty[0] / (double) (ty[0] - tr[0])), part(ratio.group(1)),
                ratio.group(1));
        assertEquals(Math.round(sy[0] / (double) (sy[0] - sr[0])), part(ratio.group(2)),
                ratio.group(2));
        assertEquals(Math.round(100.0 * (tr[2] - ty[2]) / ty[2]), Long.parseLong(ratio.group(3)),
                "the worst call more on Turrican - world 4-3");
        assertEquals(Math.round(100.0 * (sy[2] - sr[2]) / sy[2]), Long.parseLong(ratio.group(4)),
                "the worst call less on Synergy Credits");

        Map<String, long[]> calls = playCalls(perf);
        long[] turrican = Objects.requireNonNull(calls.get("Turrican - world 4-3"),
                "performance.md's table names no Turrican - world 4-3");
        long[] synergy = Objects.requireNonNull(calls.get("Synergy Credits"),
                "performance.md's table names no Synergy Credits");
        Matcher frame = wrapped("columns, (\\d+) on average on that tune, the rig's call there"
                + " less its advance, against YMX's (\\d+)").matcher(section);
        assertTrue(frame.find(), "performance.md has no frame procedure against YMX's");
        assertEquals(turrican[0] - turrican[2], Long.parseLong(frame.group(1)),
                "the frame procedure on Turrican - world 4-3");
        Matcher whole = wrapped("its whole call with no decode in it is (\\d+)").matcher(section);
        assertTrue(whole.find(), "performance.md has no call of YMX without a decode");
        assertEquals(whole.group(1), frame.group(2), "YMX's call without a decode, read twice");
        Matcher most = wrapped("so its ([\\d,]+) at most is not that frame, which the rig counts"
                + " at ([\\d,]+)").matcher(section);
        assertTrue(most.find(), "performance.md has no costliest frame past the run");
        assertEquals(sr[2], number(most.group(1)), "Synergy Credits' call at most in the run");
        assertEquals(synergy[1], number(most.group(2)), "Synergy Credits' costliest frame");

        Matcher run = wrapped("each over a `VBLS=(\\d+)` run: ([\\d,]+) calls of YMX and"
                + " ([\\d,]+) of YMXR").matcher(section);
        assertTrue(run.find(), "performance.md names no run of the two players");
        Matcher script = wrapped("performance.md's figures are a VBLS=(\\d+) run, which plays"
                + " ([\\d,]+) calls").matcher(read(Path.of("ym/cost.sh")));
        assertTrue(script.find(), "ym/cost.sh names no run of performance.md's");
        assertEquals(run.group(1), script.group(1), "the run's VBLs in the two files");
        assertEquals(run.group(3), script.group(2), "YMXR's calls in the two files");
        Matcher split = wrapped("cost ([\\d,]+) cycles a call over (\\d+) of the ([\\d,]+) calls"
                + " on Turrican - world 4-3, against ([\\d,]+) on the ([\\d,]+) calls that refill")
                .matcher(section);
        assertTrue(split.find(), "performance.md has no split of YMX's calls");
        long idle = number(split.group(2));
        long busy = number(split.group(5));
        assertEquals(number(split.group(3)), idle + busy, "YMX's calls, the two kinds together");
        assertEquals(number(run.group(2)), idle + busy, "YMX's calls in the run and in the split");
        assertEquals(ty[0], Math.round((number(split.group(1)) * idle
                + number(split.group(4)) * busy) / (double) (idle + busy)),
                "YMX's two kinds of call average to its row on Turrican - world 4-3");

        Matcher ours = wrapped("YMXR refills (\\S+) units every row; YMX serves a round-robin of"
                + " (\\S+) slots, (\\S+) of them a live stream, so its group is (\\S+) bytes,"
                + " (\\S+) units at the same unit 2, and it refills on (\\S+) rows in (\\S+):"
                + " the (\\S+) slots without a stream").matcher(section);
        assertTrue(ours.find(), "performance.md has no refill of the two players");
        int units = count(ours.group(1));
        int slots = count(ours.group(2));
        int live = count(ours.group(3));
        assertEquals(Columns.C / 2, units, "YMXR's units at unit 2");
        assertEquals(slots, count(ours.group(4)), "YMX's group, a byte a slot");
        assertEquals(slots / 2, count(ours.group(5)), "YMX's units at unit 2");
        assertEquals(live, count(ours.group(6)), "the rows YMX refills on");
        assertEquals(slots, count(ours.group(7)), "the rows YMX refills over");
        assertEquals(slots - live, count(ours.group(8)), "YMX's slots without a stream");
        Matcher against = wrapped("(\\S+) units against (\\S+), which is why").matcher(section);
        assertTrue(against.find(), "performance.md has no refills set against each other");
        assertEquals(slots / 2, count(against.group(1)), "YMX's refill");
        assertEquals(units, count(against.group(2)), "YMXR's refill");
        Matcher columns = wrapped("(\\S+) columns here against (\\S+) live streams there")
                .matcher(section);
        assertTrue(columns.find(), "performance.md has no columns against streams");
        assertEquals(Columns.C, count(columns.group(1)), "YMXR's columns");
        assertEquals(live, count(columns.group(2)), "YMX's live streams");
        Matcher worst = wrapped("(\\S+) of (\\S+) here and (\\S+) of (\\S+) there").matcher(section);
        assertTrue(worst.find(), "performance.md has no worst frame's parse");
        assertEquals(List.of(units, units, slots / 2, slots / 2), List.of(count(worst.group(1)),
                count(worst.group(2)), count(worst.group(3)), count(worst.group(4))),
                "the worst frames parse an operation a unit");
        Matcher one = wrapped("the refill was (\\S+) units").matcher(section);
        assertTrue(one.find(), "performance.md has no refill at unit 1");
        assertEquals(Columns.C, count(one.group(1)), "YMXR's units at unit 1");
    }

    /**
     * The raster monitor's paragraph against what it names: a scanline
     * against ym/cost.py's, the entry and the rte against the section on
     * the program's clock and the rig's ENTRY, the count's turn against the
     * player's COUNT macro, and the count's error against A tick's table.
     * The paragraph read each count as within a twentieth, and a tick of
     * one row, 64 cycles, counts as 60.
     */
    @Test
    void theRasterMonitorReadsTheFiguresItNames() throws IOException {
        String perf = read(PERF);
        String section = perf.substring(perf.indexOf("## The raster monitor"),
                perf.indexOf("## The program's clock"));
        Matcher line = wrapped("one scanline to (\\d+) cycles").matcher(section);
        assertTrue(line.find(), "performance.md has no scanline");
        Matcher tool = Pattern.compile("^LINE = (\\d+)", Pattern.MULTILINE)
                .matcher(read(Path.of("ym/cost.py")));
        assertTrue(tool.find(), "ym/cost.py has no scanline");
        assertEquals(tool.group(1), line.group(1), "the scanline in ym/cost.py");
        Matcher tick = wrapped("the (\\d+) cycles of the interrupt's entry and the (\\d+) of its"
                + " `rte`").matcher(section);
        assertTrue(tick.find(), "performance.md has no entry and rte");
        Matcher clock = wrapped("the (\\d+) cycles of the interrupt's entry come on top")
                .matcher(perf);
        assertTrue(clock.find(), "the program's clock has no entry");
        assertEquals(clock.group(1), tick.group(1), "the entry in the two sections");
        Matcher rig = Pattern.compile("^ENTRY = (\\d+)", Pattern.MULTILINE)
                .matcher(read(Path.of("68k/test/emu/test_ymxr.py")));
        assertTrue(rig.find(), "the rig has no ENTRY");
        assertEquals(Long.parseLong(rig.group(1)),
                Long.parseLong(tick.group(1)) + Long.parseLong(tick.group(2)),
                "the rig's entry and rte together");
        Matcher turn = wrapped("in turns of (\\w+) cycles").matcher(section);
        assertTrue(turn.find(), "performance.md has no turn of the count");
        Matcher macro = Pattern.compile("add\\.w\\s+#\\(\\\\cycles\\+\\\\drop\\+PERF_END\\+(\\d+)\\)"
                + "/(\\d+),\\(a0\\)").matcher(read(Path.of("68k/YMXR.S")));
        assertTrue(macro.find(), "the player's COUNT macro has no turn");
        int ten = Integer.parseInt(macro.group(2));
        assertEquals("ten", turn.group(1), "the turn the player counts in");
        assertEquals(10, ten, "the turn the player counts in");
        assertEquals(ten / 2, Integer.parseInt(macro.group(1)), "the count rounds to the nearest turn");
        Matcher within = wrapped("rounded to a turn of (\\w+) and so within (\\d+) cycles of what"
                + " A tick measures below").matcher(section);
        assertTrue(within.find(), "performance.md has no bound on a tick's count");
        String ticks = perf.substring(perf.indexOf("## A tick"));
        Matcher row = Pattern.compile("^\\| ((?:a|the) [^|]+?) \\| (\\d+) \\| (\\d+) \\|$",
                Pattern.MULTILINE).matcher(ticks.substring(0, ticks.indexOf("\n## ", 5)));
        long off = 0;
        int read = 0;
        while (row.find()) {
            long cost = Long.parseLong(row.group(2));
            off = Math.max(off, Math.abs((cost + ten / 2) / ten * ten - cost));
            read++;
        }
        assertEquals(5, read, "A tick's table of five paths");
        assertEquals(off, Long.parseLong(within.group(2)), "the most a tick's count is off");
    }

    /**
     * The section on a counted tick of two columns against its table and
     * the marker's arithmetic: the rig reads each build's two columns
     * against its count, a run a build, and this reads the differences the
     * section draws against both builds at once. The section's table had
     * the absolute build's figures under the plain build's name.
     */
    @Test
    void theCountedTickReadsItsTable() throws IOException {
        String perf = read(PERF);
        String section = perf.substring(perf.indexOf("## A counted tick of two columns"),
                perf.indexOf("## A tick through an absolute address"));
        Matcher row = Pattern.compile("^\\| (a row written, the places stepped|the end, the places"
                + " to row `RR`|the end, the timer stopped) \\| (\\d+) \\| (\\d+) \\| (\\d+)"
                + " \\| (\\d+) \\|$", Pattern.MULTILINE).matcher(section);
        Map<String, long[]> rows = new LinkedHashMap<>();
        while (row.find()) {
            rows.put(row.group(1), new long[] {Long.parseLong(row.group(2)),
                Long.parseLong(row.group(3)), Long.parseLong(row.group(4)),
                Long.parseLong(row.group(5))});
        }
        assertEquals(3, rows.size(), () -> "the counted ticks' table read as " + rows.keySet());
        Matcher more = wrapped("So (\\d+) cycles a tick that writes a row, as the counted handler"
                + " of one column costs against the general one, and (\\d+) on the path that"
                + " loops").matcher(section);
        assertTrue(more.find(), "performance.md has no difference of a counted tick");
        long[] written = Objects.requireNonNull(rows.get("a row written, the places stepped"));
        long[] loops = Objects.requireNonNull(rows.get("the end, the places to row `RR`"));
        for (int build = 0; build < 4; build += 2) {
            assertEquals(Long.parseLong(more.group(1)), written[build + 1] - written[build],
                    "a counted tick that writes a row, columns " + (build + 1) + " and " + (build + 2));
            assertEquals(Long.parseLong(more.group(2)), loops[build + 1] - loops[build],
                    "a counted tick on the loop, columns " + (build + 1) + " and " + (build + 2));
        }
        Matcher marker = wrapped("a period ([\\d,]+) more than the row's, which the kit's"
                + " `envelope` tune does on its first source's last two rows, ([\\d,]+) and then"
                + " ([\\d,]+), its ([\\d,]+) with the marker").matcher(section);
        assertTrue(marker.find(), "performance.md has no marker of the envelope tune");
        assertEquals(1L << 15, number(marker.group(1)), "bit 7 of R12, the period's high byte");
        assertEquals(number(marker.group(3)) - number(marker.group(1)), number(marker.group(4)),
                "the last row's period less the marker");
    }

    /** A count spelled out, one to twenty. */
    private static final List<String> SPELLED = List.of("zero", "one", "two", "three", "four",
            "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen",
            "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty");

    private static int spelled(String word) {
        int at = SPELLED.indexOf(word);
        assertTrue(at >= 0, () -> "no count spelled " + word);
        return at;
    }

    /**
     * writing.md, the guide for a writer, against the reader, the packer
     * and the conformance kit: the versions a reader reads and the one a
     * writer writes for each shape of tune, the ring the packer clamps to,
     * and the kit's files, their versions and their lines in MANIFEST.txt.
     * The guide read version 3 while the format reached 6: it read a
     * version other than $0003 as rejected, a source as one column, and
     * eleven tune files where the kit had fifteen.
     */
    @Test
    void theWritersGuideReadsTheFormatAndTheKit() throws IOException {
        String guide = read(WRITING);
        Matcher reads = wrapped("a version other than (\\d) to (\\d)").matcher(guide);
        assertTrue(reads.find(), "writing.md names no versions a reader reads");
        assertEquals(Tune.VERSION, Integer.parseInt(reads.group(1)), "the first version read");
        assertEquals(Tune.VERSION_WIDE_COUNTED, Integer.parseInt(reads.group(2)),
                "the last version read");
        Matcher lowest = wrapped("\\(SPEC\\.md 3\\.3\\.5\\): (\\d) where every source is one"
                + " column and every target 0 to 13, (\\d) with a source of several columns or a"
                + " target of 14 upward, (\\d) with a counted source of one column, and (\\d) with"
                + " a counted source of several columns").matcher(guide);
        assertTrue(lowest.find(), "writing.md names no version a writer writes");
        assertEquals(List.of(Tune.VERSION, Tune.VERSION_COLUMNS, Tune.VERSION_COUNTED,
                Tune.VERSION_WIDE_COUNTED), List.of(Integer.parseInt(lowest.group(1)),
                Integer.parseInt(lowest.group(2)), Integer.parseInt(lowest.group(3)),
                Integer.parseInt(lowest.group(4))), "the version of each shape");

        Matcher ring = wrapped("the bytes a column unpacks through: a multiple of (\\w+), at"
                + " least (\\d+) and at most ([\\d,]+)").matcher(guide);
        assertTrue(ring.find(), "writing.md has no ring");
        assertEquals(Columns.C, count(ring.group(1)), "the ring's multiple, the period");
        assertEquals(Tune.ringOf(0), Integer.parseInt(ring.group(2)), "the least ring");
        assertEquals(Tune.ringOf(Integer.MAX_VALUE / 2), number(ring.group(3)), "the most ring");

        Path kit = Path.of("doc/conformance/tunes");
        List<Path> files;
        try (Stream<Path> at = Files.list(kit)) {
            files = at.filter(one -> one.toString().endsWith(".ymxr")).sorted().toList();
        }
        Matcher tunes = wrapped("`doc/conformance/tunes/` has (\\w+) tune files: (\\w+) a reader"
                + " reads, of versions (\\d) to (\\d), and `wrong-version\\.ymxr`, version"
                + " `\\$([0-9A-F]{4})`").matcher(guide);
        assertTrue(tunes.find(), "writing.md has no count of the kit's tune files");
        assertEquals(files.size(), spelled(tunes.group(1)), "the kit's tune files");
        TreeSet<Integer> versions = new TreeSet<>();
        int read = 0;
        for (Path one : files) {
            byte[] file = Files.readAllBytes(one);
            int version = (file[4] & 0xFF) << 8 | file[5] & 0xFF;
            if (one.getFileName().toString().equals("wrong-version.ymxr")) {
                assertEquals(Integer.parseInt(tunes.group(5), 16), version,
                        "wrong-version.ymxr's version");
                assertTrue(version > Tune.VERSION_WIDE_COUNTED, "wrong-version.ymxr is one a"
                        + " reader reads");
            } else {
                versions.add(version);
                read++;
            }
        }
        assertEquals(read, spelled(tunes.group(2)), "the kit's tune files a reader reads");
        assertEquals(List.of(Integer.parseInt(tunes.group(3)), Integer.parseInt(tunes.group(4))),
                List.of(versions.first(), versions.last()),
                "the versions of the kit's tune files");
        String manifest = read(Path.of("doc/conformance/MANIFEST.txt"));
        for (Path one : files) {
            assertTrue(manifest.contains(" tunes/" + one.getFileName() + "\n"),
                    () -> "MANIFEST.txt names no " + one.getFileName());
        }
    }

    /** A ratio spelled as a part, to a fifteenth. */
    private static final List<String> PARTS = List.of("", "a whole", "a half", "a third",
            "a quarter", "a fifth", "a sixth", "a seventh", "an eighth", "a ninth", "a tenth",
            "an eleventh", "a twelfth", "a thirteenth", "a fourteenth", "a fifteenth");

    /** The part a fall from one figure to another nears: a fall of an eighth is 1 in 8. */
    private static String fall(long from, long to) {
        return PARTS.get((int) Math.round(from / (double) (from - to)));
    }

    /**
     * plan.md against requirements.md and performance.md: the budget, the
     * heaviest refill and its shares, the tunes that packed at unit 1 and
     * what padding them saved, the costliest frame against the budget, an
     * operation's cost, the square's tick, the window, and the arithmetic of
     * the thirty-first column. The rig reads the shares and gates the rows
     * give; this reads what the other documents give. The document read ten
     * tunes where the fixtures are eleven and five that packed at unit 1
     * where performance.md counts six.
     */
    @Test
    void thePlanReadsTheBudgetAndTheTables() throws IOException {
        String plan = read(PLAN);
        String perf = read(PERF);
        Matcher budget = wrapped("A play call costs at most ([\\d,]+) cycles").matcher(read(REQ));
        assertTrue(budget.find(), "requirements.md has no budget in R4.5");
        long most = number(budget.group(1));
        Matcher names = Pattern.compile("(?:R4\\.5 budgets ([\\d,]+) a frame|R4\\.5's ([\\d,]+)-cycle"
                + " budget|the budget's ([\\d,]+))").matcher(plan.replaceAll("\\s+", " "));
        int named = 0;
        while (names.find()) {
            String said = names.group(1) != null ? names.group(1)
                    : names.group(2) != null ? names.group(2) : names.group(3);
            assertEquals(most, number(said), "plan.md's budget: " + names.group());
            named++;
        }
        assertTrue(named >= 3, "plan.md names R4.5's budget " + named + " times");

        Map<String, long[]> calls = playCalls(perf);
        long heaviest = 0;
        long costliest = 0;
        String worst = "";
        for (Map.Entry<String, long[]> one : calls.entrySet()) {
            heaviest = Math.max(heaviest, one.getValue()[3]);
            if (one.getValue()[1] > costliest) {
                costliest = one.getValue()[1];
                worst = one.getKey();
            }
        }
        Matcher refill = wrapped("The heaviest refill of the (\\w+) tunes costs ([\\d,]+) cycles at"
                + " unit 2, (\\d+) per cent of R4\\.5's [\\d,]+-cycle budget, against ([\\d,]+) at"
                + " unit 1 and (\\d+) per cent").matcher(plan);
        assertTrue(refill.find(), "plan.md has no heaviest refill");
        assertEquals(calls.size(), spelled(refill.group(1)), "the tunes the table measures");
        assertEquals(heaviest, number(refill.group(2)), "the heaviest refill the table reads");
        assertEquals(Math.round(100.0 * heaviest / most), Long.parseLong(refill.group(3)),
                "the heaviest refill's share of the budget");
        assertEquals(Math.round(100.0 * number(refill.group(4)) / most),
                Long.parseLong(refill.group(5)), "the share at unit 1");

        int table = perf.indexOf("| tune | on average at unit 1 |");
        assertTrue(table >= 0, "performance.md has no table at unit 1");
        Matcher units = Pattern.compile("^\\| ([^|]+?) \\| (\\d+) \\| (\\d+) \\| (\\d+) \\| (\\d+) \\|$",
                Pattern.MULTILINE).matcher(perf.substring(table, perf.indexOf("\n\n", table)));
        Map<String, long[]> byTune = new LinkedHashMap<>();
        while (units.find()) {
            byTune.put(units.group(1), new long[] {Long.parseLong(units.group(2)),
                Long.parseLong(units.group(3)), Long.parseLong(units.group(4)),
                Long.parseLong(units.group(5))});
        }
        Matcher before = wrapped("(\\w+) of the (\\w+) tunes packed at unit 1 before it; measured again"
                + " at unit 2, their calls fell by (an? \\w+) to (an? \\w+) on average and by (an? \\w+)"
                + " to more than a quarter in the costliest frame, Synergy Credits' from ([\\d,]+) to"
                + " ([\\d,]+)").matcher(plan);
        assertTrue(before.find(), "plan.md has no tunes that packed at unit 1");
        assertEquals(byTune.size(), spelled(before.group(1).toLowerCase()), "the tunes padded");
        assertEquals(calls.size(), spelled(before.group(2)), "the tunes measured");
        Comparator<long[]> onAverage = Comparator.comparingDouble(row -> (row[0] - row[1]) / (double) row[0]);
        Comparator<long[]> atMost = Comparator.comparingDouble(row -> (row[2] - row[3]) / (double) row[2]);
        long[] leastAverage = byTune.values().stream().min(onAverage).orElseThrow();
        long[] mostAverage = byTune.values().stream().max(onAverage).orElseThrow();
        long[] leastFrame = byTune.values().stream().min(atMost).orElseThrow();
        long[] mostFrame = byTune.values().stream().max(atMost).orElseThrow();
        assertEquals(fall(leastAverage[0], leastAverage[1]), before.group(3).replaceAll("\\s+", " "),
                "the least fall on average");
        assertEquals(fall(mostAverage[0], mostAverage[1]), before.group(4).replaceAll("\\s+", " "),
                "the greatest fall on average");
        assertEquals(fall(leastFrame[2], leastFrame[3]), before.group(5).replaceAll("\\s+", " "),
                "the least fall in the costliest frame");
        assertTrue((mostFrame[2] - mostFrame[3]) / (double) mostFrame[2] > 0.25,
                "the greatest fall in the costliest frame is a quarter or less");
        long[] synergy = Objects.requireNonNull(byTune.get("Synergy Credits"),
                "the unit-1 table has no Synergy Credits");
        assertEquals(synergy[2], number(before.group(6)), "Synergy Credits at unit 1");
        assertEquals(synergy[3], number(before.group(7)), "Synergy Credits at unit 2");
        Matcher under = wrapped("The costliest frame of any tune is now that one, ([\\d,]+) cycles"
                + " under the budget").matcher(plan);
        assertTrue(under.find(), "plan.md has no costliest frame against the budget");
        assertEquals("Synergy Credits", worst, "the tune of the costliest frame");
        assertEquals(most - costliest, number(under.group(1)), "the costliest frame under the budget");

        Matcher parse = wrapped("about (\\d+) to (\\d+) an operation to parse").matcher(perf);
        assertTrue(parse.find(), "performance.md has no operation's cost");
        Matcher each = Pattern.compile("(\\d+) to (\\d+) cycles each|operations at (\\d+) to (\\d+)")
                .matcher(plan.replaceAll("\\s+", " "));
        int costs = 0;
        while (each.find()) {
            String low = each.group(1) != null ? each.group(1) : each.group(3);
            String high = each.group(2) != null ? each.group(2) : each.group(4);
            assertEquals(parse.group(1) + " to " + parse.group(2), low + " to " + high,
                    "an operation's cost: " + each.group());
            costs++;
        }
        assertEquals(3, costs, "plan.md names an operation's cost three times");

        Matcher square = wrapped("A square's tick is in place: (\\d+) cycles of instructions against"
                + " (\\d+) and (\\d+), and (\\d+) with the 68000's entry and the `rte` against (\\d+)"
                + " and (\\d+)").matcher(plan);
        assertTrue(square.find(), "plan.md has no square's tick");
        String ticks = perf.substring(perf.indexOf("## A tick"));
        long[] paths = new long[3];
        String[] rows = {"a square's two rows, no place stepped", "a row written, the place stepped",
            "the marker, the place to row `RR`"};
        for (int i = 0; i < 3; i++) {
            Matcher row = Pattern.compile("^\\| " + Pattern.quote(rows[i]) + " \\| (\\d+) \\|",
                    Pattern.MULTILINE).matcher(ticks);
            assertTrue(row.find(), "performance.md's tick table has no " + rows[i]);
            paths[i] = Long.parseLong(row.group(1));
            assertEquals(paths[i], Long.parseLong(square.group(i + 1)), rows[i]);
        }
        Matcher rig = Pattern.compile("^ENTRY = (\\d+)", Pattern.MULTILINE)
                .matcher(read(Path.of("68k/test/emu/test_ymxr.py")));
        assertTrue(rig.find(), "the rig has no ENTRY");
        long entry = Long.parseLong(rig.group(1));
        for (int i = 0; i < 3; i++) {
            assertEquals(paths[i] + entry, Long.parseLong(square.group(i + 4)),
                    rows[i] + " with the entry and the rte");
        }
        Matcher drum = wrapped("its tick is the row path every time, (\\d+) cycles").matcher(plan);
        assertTrue(drum.find(), "plan.md has no digidrum's tick");
        assertEquals(paths[1] + entry, Long.parseLong(drum.group(1)), "a digidrum's tick");
        Matcher order = Pattern.compile("^\\| a separate tick for a square \\| \\d+ a tick \\| (\\d+)"
                + " against (\\d+) and (\\d+) \\|$", Pattern.MULTILINE).matcher(plan);
        assertTrue(order.find(), "plan.md's order has no square");
        for (int i = 0; i < 3; i++) {
            assertEquals(paths[i], Long.parseLong(order.group(i + 1)), "the order's " + rows[i]);
        }

        Matcher window = wrapped("The window is (\\d+) units at unit 1 and (\\d+) at unit 2")
                .matcher(plan);
        assertTrue(window.find(), "plan.md has no window");
        assertEquals(Columns.C, Integer.parseInt(window.group(1)), "the window at unit 1");
        assertEquals(Columns.C / 2, Integer.parseInt(window.group(2)), "the window at unit 2");
        Matcher wider = wrapped("The ring is (\\d+) bytes and (\\d+) does not divide by 31, so the"
                + " period goes from (\\d+) to (\\d+): a refill grows (\\w+) unit at unit 2 and"
                + " (\\w+) at unit 1").matcher(plan);
        assertTrue(wider.find(), "plan.md has no thirty-first column's ring");
        assertEquals(Tune.RING, Integer.parseInt(wider.group(1)), "the ring");
        assertEquals(Tune.RING, Integer.parseInt(wider.group(2)), "the ring, again");
        assertTrue(Tune.RING % 31 != 0, "the ring divides by 31");
        int period = 31;
        while (Tune.RING % period != 0) {
            period++;
        }
        assertEquals(Columns.C, Integer.parseInt(wider.group(3)), "the period today");
        assertEquals(period, Integer.parseInt(wider.group(4)), "the period of 31 columns");
        assertEquals((period - Columns.C) / 2, spelled(wider.group(5)), "a refill's growth at unit 2");
        assertEquals(period - Columns.C, spelled(wider.group(6)), "a refill's growth at unit 1");
    }

    /**
     * Every glossary row names the document that explains its term, and
     * and no check opened that document. requirements.md R0.7 allows no second
     * word for a thing that has one, so the document explaining a term
     * names it: SPEC.md 3.1 wrote "the index's entry" where the glossary
     * lists "index entry".
     *
     * <p>A term of a letter or two is found in any prose, so those rows
     * pass on anything; the check reaches the rest.
     */
    @Test
    void everyGlossaryTermIsNamedWhereItIsExplained() throws IOException {
        List<String> quiet = new ArrayList<>();
        int opened = 0;
        for (String[] row : Documents.glossaryRows(read(GLO))) {
            String file = row[2].split("[ ,;]")[0];
            if (!file.endsWith(".md")) {
                continue;
            }
            Path at = Files.exists(Path.of("doc", file))
                    ? Path.of("doc", file) : Path.of(file);
            if (!Files.exists(at)) {
                continue;
            }
            opened++;
            String said = read(at).toLowerCase();
            String term = row[0].replace("`", "").trim().toLowerCase();
            String one = term.endsWith("s")
                    ? term.substring(0, term.length() - 1) : term;
            if (!said.contains(term) && !said.contains(one)) {
                quiet.add(row[0] + " is explained in " + file
                        + ", which never names it");
            }
        }
        final int all = opened;
        assertTrue(all > 20, () -> "only " + all + " rows opened; the check is asleep");
        assertTrue(quiet.isEmpty(), () -> String.join("\n", quiet)
                + "\nR0.7 allows no second word for a thing that has one.");
    }

    @Test
    void everyDocumentKeepsOneWrapWidth() throws IOException {
        assertTrue(DOCUMENTS.size() > 12, () -> "only " + DOCUMENTS.size()
                + " documents read; the check is asleep");
        List<String> wide = Documents.wide(DOCUMENTS, 78);
        assertTrue(wide.isEmpty(), () -> String.join("\n", wide)
                + "\nAGENTS.md requires one width.");
    }

    private static final Path RIG = Path.of("68k/test/emu/test_ymxr.py");

    /** The register numbers a phrase names, in the order it names them. */
    private static Set<Integer> registers(String said) {
        Set<Integer> out = new TreeSet<>();
        Matcher one = Pattern.compile("R(\\d+)").matcher(said);
        while (one.find()) {
            out.add(Integer.parseInt(one.group(1)));
        }
        return out;
    }

    /** The registers a "four for ..., five for ..." phrase puts at that
     *  width, one entry a width, read from the {@code at}th such phrase. */
    private static Map<Integer, Set<Integer>> widths(String said, int at) {
        Matcher phrase = Pattern.compile(
                "four\\s+for\\s+([^.]*?),\\s+five\\s+for\\s+([^.]*?)"
                        + "(?:,\\s+and\\s+six|\\.)")
                .matcher(said);
        for (int i = 0; i <= at; i++) {
            assertTrue(phrase.find(), "SPEC.md names no " + (at + 1)
                    + " widths phrase; the check is asleep");
        }
        Map<Integer, Set<Integer>> out = new LinkedHashMap<>();
        out.put(4, registers(phrase.group(1)));
        out.put(5, registers(phrase.group(2)));
        return out;
    }

    /** A Python list of fourteen bytes under that name. */
    private static List<Integer> table(Path at, String named) throws IOException {
        Matcher said = Pattern.compile(named + "\\s*=\\s*\\[([^\\]]*)\\]")
                .matcher(read(at));
        assertTrue(said.find(), at + " records no " + named);
        List<Integer> out = new ArrayList<>();
        for (String one : said.group(1).split(",")) {
            out.add(Integer.decode(one.trim()));
        }
        return out;
    }

    /** The bits each register reads of the byte written to it: SPEC.md 4
     *  and section 7 name the same widths, and the rig masks by them
     *  (AGENTS.md, Measure). */
    @Test
    void theRegisterWidthsReadTheSameInTheSpecificationAndTheMaskTable()
            throws IOException {
        String spec = read(SPEC);
        Map<Integer, Set<Integer>> writes = widths(spec, 0);
        Map<Integer, Set<Integer>> reports = widths(spec, 1);
        assertEquals(writes, reports,
                "SPEC.md 4 and section 7 read the same register at two widths");
        assertEquals(Set.of(1, 3, 5, 13), writes.get(4), "the registers of four bits");
        assertEquals(Set.of(6, 8, 9, 10), writes.get(5), "the registers of five bits");

        List<Integer> fits = table(RIG, "FITS");
        assertEquals(14, fits.size(), "a mask a sound register");
        for (int r = 0; r < fits.size(); r++) {
            int bits = Integer.bitCount(fits.get(r));
            Set<Integer> named = writes.getOrDefault(bits, Set.of());
            assertEquals(named.contains(r), bits == 4 || bits == 5,
                    "R" + r + " masks to " + bits + " bits, which SPEC.md 4"
                            + " reads as " + (named.contains(r) ? "that" : "another")
                            + " width");
        }
        assertEquals(0x0E, 0xAE & fits.get(13),
                "SPEC.md 4: a row whose R13 column is $AE runs shape $E");
    }

    /**
     * The crossover table of a set: every row's share against the two
     * figures beside it, and the three sentences below it against the
     * table's first and last rows.
     */
    @Test
    void theSetCrossoverRecomputes() throws IOException {
        String experiments = read(EXP);
        int at = experiments.indexOf("| tunes | ring 960, no copies |");
        assertTrue(at >= 0, "experiments.md has no crossover table");
        String block = experiments.substring(at, experiments.indexOf("\n\n", at));
        Matcher row = Pattern.compile(
                "^\\| (\\d+) \\| ([\\d,]+) \\| ([\\d,]+) \\| (-?\\d+\\.\\d)% \\|$",
                Pattern.MULTILINE).matcher(block);
        List<long[]> table = new ArrayList<>();
        while (row.find()) {
            long tunes = Long.parseLong(row.group(1));
            long wide = number(row.group(2));
            long small = number(row.group(3));
            double said = Double.parseDouble(row.group(4));
            double got = Math.round(1000.0 * (wide - small) / wide) / 10.0;
            assertTrue(Math.abs(got - said) < 0.05, () -> "at " + tunes
                    + " tunes " + wide + " against " + small + " is " + got
                    + "%, and the row reads " + said + "%");
            table.add(new long[] {tunes, wide, small});
        }
        assertTrue(table.size() >= 5, "the crossover table has " + table.size() + " rows");

        long[] first = table.get(0);
        long[] last = table.get(table.size() - 1);
        long span = last[0] - first[0];

        // the workspace, which the ring alone decides
        Matcher space = wrapped("The workspace saved is ([\\d,]+) bytes, once:"
                + " ([\\d,]+) bytes at a ring of 960 against ([\\d,]+) at 120")
                .matcher(experiments);
        assertTrue(space.find(), "experiments.md has no workspace sentence");
        long saved = number(space.group(1));
        assertEquals(number(space.group(2)) - number(space.group(3)), saved,
                "the workspace saved is the difference of the two state blocks");

        // what a tune adds, out of the first and last rows of the table
        Matcher each = wrapped("The bytes a tune adds are ([\\d,]+) at the defaults"
                + " and ([\\d,]+) at the small ring, ([\\d,]+) more a tune")
                .matcher(experiments);
        assertTrue(each.find(), "experiments.md has no per-tune sentence");
        long wideEach = Math.round((double) (last[1] - first[1]) / span);
        long smallEach = Math.round((double) (last[2] - first[2]) / span);
        assertEquals(wideEach, number(each.group(1)),
                "what a tune adds at the defaults, over the table's span");
        assertEquals(smallEach, number(each.group(2)),
                "what a tune adds at the small ring, over the table's span");
        assertEquals(smallEach - wideEach, number(each.group(3)),
                "the difference of the two the sentence reports");

        // where the two meet, and that the table turns negative there
        Matcher meet = wrapped("The two meet at ([\\d,]+) over ([\\d,]+),"
                + " near six tunes").matcher(experiments);
        assertTrue(meet.find(), "experiments.md has no crossover sentence");
        assertEquals(saved, number(meet.group(1)), "the saving the sentence divides");
        assertEquals(smallEach - wideEach, number(meet.group(2)),
                "the cost a tune the sentence divides by");
        assertEquals(6, Math.round((double) saved / (smallEach - wideEach)),
                "the crossover the two figures make");
        for (long[] one : table) {
            if (one[0] <= 4) {
                assertTrue(one[2] < one[1], "at " + one[0] + " tunes the small ring is larger");
            }
            if (one[0] >= 6) {
                assertTrue(one[2] > one[1], "at " + one[0] + " tunes the small ring is smaller");
            }
        }
    }

    private static final Path TOOLS = Path.of("doc/tools.md");
    private static final Path POM = Path.of("pom.xml");
    private static final Path GO_MOD = Path.of("go/go.mod");

    /** The clause numbers a document defines: its numbered headings and
     *  the bold number that opens a clause. */
    private static Set<String> clauses(Path at) throws IOException {
        Set<String> out = new TreeSet<>();
        String said = read(at);
        Matcher heading = Pattern.compile("^#{1,4} (\\d+(?:\\.\\d+)*)\\.?\\s",
                Pattern.MULTILINE).matcher(said);
        while (heading.find()) {
            out.add(heading.group(1));
        }
        Matcher bold = Pattern.compile("^\\*\\*(\\d+(?:\\.\\d+)*)\\b",
                Pattern.MULTILINE).matcher(said);
        while (bold.find()) {
            out.add(bold.group(1));
        }
        return out;
    }

    /**
     * Every clause a document cites in another document is one that
     * document defines. tools.md cited ymxs.md 1, 3, 3.4, 3.6 and 5 of a
     * numbering ymxs.md never had, written the day the tools became a
     * specification, and no check opened ymxs.md against them.
     *
     * <p>A citation qualified with YMXS or DTX names that repository's
     * document, and the qualifier is anywhere from the parenthesis it
     * opens; a document this repository does not have is that repository's
     * too. RELEASES.md records what was true at each release, so a clause
     * renumbered after one leaves its entry as it was.
     */
    @Test
    void everyClauseCitedInAnotherDocumentIsDefined() throws IOException {
        Map<Path, Set<String>> defined = new LinkedHashMap<>();
        List<String> dangling = new ArrayList<>();
        int read = 0;
        for (Path p : DOCUMENTS) {
            if (p.getFileName().toString().equals("RELEASES.md")) {
                continue;
            }
            String said = read(p);
            Matcher cited = Pattern.compile("([A-Za-z_]+)\\.md\\)? (\\d+(?:\\.\\d+)*)")
                    .matcher(said);
            while (cited.find()) {
                int open = said.lastIndexOf('(',
                        Math.max(0, cited.start() - 1));
                int from = open >= 0 && cited.start() - open <= 120
                        ? open : Math.max(0, cited.start() - 20);
                String before = said.substring(from, cited.start());
                if (before.contains("YMXS") || before.contains("DTX")) {
                    continue;
                }
                Path at = Path.of("doc", cited.group(1) + ".md");
                if (!Files.exists(at)) {
                    at = Path.of(cited.group(1) + ".md");
                }
                if (!Files.exists(at)) {
                    continue;
                }
                if (!defined.containsKey(at)) {
                    defined.put(at, clauses(at));
                }
                read++;
                if (!defined.get(at).contains(cited.group(2))) {
                    dangling.add(p + " cites " + cited.group());
                }
            }
        }
        final int opened = read;
        assertTrue(opened > 100, () -> "only " + opened
                + " citations read; the check is asleep");
        assertTrue(dangling.isEmpty(), () -> String.join("\n", dangling));
    }

    /** The version of one Maven dependency of the pom. */
    private static String artifact(String pom, String named) {
        Matcher said = Pattern.compile("<artifactId>" + named
                + "</artifactId>\\s*<version>([^<]+)</version>").matcher(pom);
        assertTrue(said.find(), "pom.xml requires no " + named);
        return said.group(1);
    }

    /** The version of one module of go.mod. */
    private static String module(String mod, String named) {
        Matcher said = Pattern.compile("github\\.com/odipar/" + named
                + "/go (v[^\\s]+)").matcher(mod);
        assertTrue(said.find(), "go.mod requires no " + named);
        return said.group(1);
    }

    /**
     * The releases of DTX and YMXS the documents name against the ones the
     * two trees require. tools.md 19.2 and 19.3 and requirements.md R1 read
     * DTX 0.10.1 and YMXS 0.3.2 while the pom required 0.11.5 and 0.3.4,
     * five releases of one and two of the other later: the versions move
     * with every release of either repository and the prose moved with
     * none of them.
     */
    @Test
    void everyReleaseTheDocumentsNameIsTheOneTheBuildRequires()
            throws IOException {
        String pom = read(POM);
        String dtx = artifact(pom, "dtx");
        String ymxs = artifact(pom, "ymxs");
        String mod = read(GO_MOD);
        assertEquals("v" + dtx, module(mod, "dtx"),
                "go.mod and pom.xml require two releases of DTX");
        assertEquals("v" + ymxs, module(mod, "ymxs"),
                "go.mod and pom.xml require two releases of YMXS");
        String tools = read(TOOLS);
        Matcher maven = wrapped("DTX `" + dtx + "` and YMXS `" + ymxs
                + "` in the local Maven repository").matcher(tools);
        assertTrue(maven.find(), "tools.md 19.2 names another pair of"
                + " releases than the pom requires, " + dtx + " and " + ymxs);
        Matcher modules = wrapped("`github.com/odipar/dtx/go v" + dtx
                + "` and `github.com/odipar/ymxs/go v" + ymxs + "`")
                .matcher(tools);
        assertTrue(modules.find(), "tools.md 19.3 names another pair of"
                + " modules than go.mod requires, " + dtx + " and " + ymxs);
        Matcher required = wrapped(dtx + " is the release the Java tree and"
                + " the Go tree read").matcher(read(REQ));
        assertTrue(required.find(), "requirements.md R1 names another"
                + " release of DTX than the build requires, " + dtx);
    }

    /** The clauses one document defines: `**N.N**` and `## N.N`, a section
     *  number marking itself and the clauses under it. */
    private static Set<String> clausesOf(String said) {
        Set<String> out = new HashSet<>();
        Matcher m = Pattern.compile("(?m)^(?:\\*\\*|#+ )R?(\\d+(?:\\.\\d+)*)").matcher(said);
        while (m.find()) {
            String clause = m.group(1);
            out.add(clause);
            for (int dot = clause.indexOf('.'); dot > 0; dot = clause.indexOf('.', dot + 1)) {
                out.add(clause.substring(0, dot));
            }
        }
        return out;
    }

    /**
     * Every citation of a specification lands on a clause of it.
     *
     * <p>A citation in these documents is the clause in brackets, `(3.1.2)`,
     * and a reader follows it. ST4's 3.4 pointed at a value no clause set
     * and YMXS's 3.1.1 at a 2.6 no document has, both found by a reader
     * with the documents alone; this reads every one of them at once. The
     * documents outside the list cite SPEC.md by the same brackets, which
     * the Conventions allow and this leaves alone.
     */
    /**
     * Every line a table of BINARIES.md or tools.md reports reads the
     * same in the two trees: a message reworded in one tree and the
     * document, or in the document alone, fails here. The letters a
     * table writes for a figure, V or N or B or i, and the figures a tool
     * builds a line from are outside the comparison, and this reads the
     * words around them.
     *
     * <p>The check that a figure reads the same is
     * {@code BinariesTest.theVersionsTheDocumentsReportAreTheOnesWritten}:
     * 0.4.11 took the stub's descriptor to version 2 in BINARIES.md 4.2
     * and left 4.5 reading 1, which a reader of the kit found.
     */
    @Test
    void everyLineATableReportsReadsTheSameInBothTrees() throws IOException {
        String java = tree(Path.of("src/main/java/org/ymxr"), ".java");
        String go = tree(Path.of("go"), ".go");
        List<String> read = new ArrayList<>();
        for (Path at : List.of(Path.of("doc/BINARIES.md"), Path.of("doc/tools.md"))) {
            for (String said : reported(read(at))) {
                List<String> parts = new ArrayList<>();
                for (String part : said.split("\\b[A-Zi]\\b|[0-9][0-9,]*")) {
                    String one = part.strip();
                    if (one.length() >= 12) {
                        parts.add(one);
                    }
                }
                if (parts.isEmpty()) {
                    continue;
                }
                // the longest run of words between the figures: a name
                // the tool writes into a line, a tag's or a tool's,
                // is between two such runs and moves with the file
                String part = parts.stream().max(Comparator.comparingInt(String::length))
                        .orElseThrow();
                if (!java.contains(part)) {
                    // a line of the shared tool or of a script, which
                    // is outside these two trees
                    continue;
                }
                read.add(said);
                assertTrue(go.contains(part),
                        at + " reports \"" + said + "\" and the Go tree lacks \""
                        + part + "\"");
            }
        }
        assertTrue(read.size() >= 30, "the tables report " + read.size() + " lines of the tools");
    }

    /** The lines the tables of a document report: the last cell of a row,
     *  each code span in it of three words or more that opens in lower
     *  case. */
    private static List<String> reported(String document) {
        List<String> out = new ArrayList<>();
        Matcher row = Pattern.compile("^\\|(.*)\\|\\s*$", Pattern.MULTILINE)
                .matcher(document);
        while (row.find()) {
            String[] cells = row.group(1).split("\\|");
            if (cells.length < 2) {
                continue;
            }
            Matcher said = Pattern.compile("`([^`]+)`").matcher(cells[cells.length - 1]);
            while (said.find()) {
                String one = said.group(1);
                if (one.split("\\s+").length >= 3 && Character.isLowerCase(one.charAt(0))) {
                    out.add(one);
                }
            }
        }
        return out;
    }

    /** Every source of a tree, read as one text. */
    private static String tree(Path at, String ending) throws IOException {
        StringBuilder out = new StringBuilder();
        try (Stream<Path> found = Files.walk(at)) {
            for (Path one : found.filter(p -> p.toString().endsWith(ending)).toList()) {
                out.append(Files.readString(one)).append('\n');
            }
        }
        // a line a tool builds from two strings reads as one here
        return out.toString().replaceAll("\"\\s*\\+\\s*\"", "")
                .replaceAll("\"\\s*\\+\\n?\\s*\"", "");
    }

    @Test
    void everyCitationLandsOnAClause() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Path at : List.of(SPEC, REQ, Path.of("doc/BINARIES.md"), TERM)) {
            String said = read(at);
            Set<String> clauses = clausesOf(said);
            Matcher m = Pattern.compile("\\((\\d+(?:\\.\\d+){1,3})\\)").matcher(said);
            while (m.find()) {
                if (!clauses.contains(m.group(1))) {
                    wrong.add(at + " cites (" + m.group(1) + "), which is no clause of it");
                }
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }
}
