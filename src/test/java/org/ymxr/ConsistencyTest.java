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
     * document, and the qualifier stands anywhere from the parenthesis it
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
     * DTX 0.10.1 and YMXS 0.3.2 while the pom stood at 0.11.5 and 0.3.4,
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
     *  number standing for itself and for the clauses under it. */
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
     * builds a line from stand outside the comparison, and this reads the
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
                // stands between two such runs and moves with the file
                String part = parts.stream().max(Comparator.comparingInt(String::length))
                        .orElseThrow();
                if (!java.contains(part)) {
                    // a line of the shared tool or of a script, which
                    // stands outside these two trees
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
