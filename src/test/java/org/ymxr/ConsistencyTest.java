package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The documents against themselves: every reference that can be followed,
 * every figure that can be recomputed.
 *
 * <p>{@code HouseStyleTest} holds the prose to {@code AGENTS.md} and
 * {@code GlossaryTest} holds the terms to the glossary. This holds the
 * numbers and the pointers, which drift on their own as a document is
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

    private static final List<Path> DOCUMENTS =
            List.of(Path.of("README.md"), SPEC, REQ, GLO, TERM, EXP);

    private static String read(Path p) throws IOException {
        return Files.readString(p);
    }

    /** A claim in prose, where any space may be the line wrap. */
    private static Pattern wrapped(String claim) {
        return Pattern.compile(claim.replace(" ", "\\s+"));
    }

    /** The rows of the column table: first index, last index, what it holds. */
    private static List<Object[]> columnTable(String spec) {
        int at = spec.indexOf("| column | holds |");
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
        // The report gives a row a column, and the name on it is the
        // player's vocabulary for that column (AGENTS.md, one
        // vocabulary), so SPEC.md's table is what it is held to.
        List<Object[]> rows = columnTable(read(SPEC));
        List<String> astray = new ArrayList<>();
        for (Object[] r : rows) {
            int first = (Integer) r[0];
            int last = (Integer) r[1];
            String holds = (String) r[2];
            for (int c = first; c <= last; c++) {
                String name = Tune.name(c);
                // A row is the column's name, and where it says more it
                // says it after a comma: "R0, voice A tone period, fine",
                // "effect 0 timer control, Timer A's control register".
                boolean named = holds.equals(name) || holds.startsWith(name + ",");
                if (first == last && !named) {
                    astray.add("column " + c + " is named " + name + " and holds " + holds);
                } else if (first != last && (c - Columns.EFFECT) / 4
                        != (first - Columns.EFFECT) / 4) {
                    astray.add("column " + c + " falls outside the effect its row gives");
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
                astray.add("column " + first + " holds " + r[2]);
            }
            next = last + 1;
            columns += last - first + 1;
        }
        assertTrue(gaps.isEmpty(), () -> "the column indices break: " + gaps);
        assertTrue(astray.isEmpty(), () -> "a register column is not its"
                + " register: " + astray);

        Matcher m = wrapped("(\\d+) columns of the 32 R\\d+\\.\\d+ allows, each"
                + " one byte, so a row is (\\d+) bytes").matcher(spec);
        assertTrue(m.find(), "SPEC.md does not state its column and byte count");
        int saidColumns = Integer.parseInt(m.group(1));
        int saidBytes = Integer.parseInt(m.group(2));
        int c = columns;
        assertTrue(saidColumns == c && saidBytes == c,
                () -> "the table holds " + c + " columns of one byte; the prose"
                        + " says " + saidColumns + " and " + saidBytes);
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
        for (Path p : DOCUMENTS) {
            Matcher c = Pattern.compile("\\bR\\d+\\.\\d+\\b").matcher(read(p));
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
    private static List<String[]> glossaryRows(String glo) {
        List<String[]> out = new ArrayList<>();
        for (String line : glo.split("\n")) {
            if (!line.startsWith("| ") || line.startsWith("| term")) {
                continue;
            }
            String[] cells = line.split("\\|");
            if (cells.length >= 4) {
                out.add(new String[] {cells[1].trim(), cells[3].trim()});
            }
        }
        return out;
    }

    @Test
    void theGlossaryIsInOrder() throws IOException {
        List<String[]> rows = glossaryRows(read(GLO));
        assertTrue(rows.size() > 40, () -> "the glossary read as " + rows.size()
                + " rows");
        List<String> wrong = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            String before = rows.get(i - 1)[0].replace("`", "").toLowerCase();
            String after = rows.get(i)[0].replace("`", "").toLowerCase();
            if (before.compareTo(after) > 0) {
                wrong.add('"' + before + "\" stands before \"" + after + '"');
            }
        }
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
        for (String[] row : glossaryRows(read(GLO))) {
            String where = row[1];
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
                + "\nterminology.md holds " + sections);
    }

    @Test
    void everyLinkResolves() throws IOException {
        List<String> broken = new ArrayList<>();
        for (Path p : DOCUMENTS) {
            Matcher m = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)")
                    .matcher(read(p));
            while (m.find()) {
                String target = m.group(2);
                if (target.startsWith("http")) {
                    continue;
                }
                Path base = p.getParent() == null ? Path.of(".") : p.getParent();
                Path at = base.resolve(target.split("#")[0]).normalize();
                if (!Files.exists(at)) {
                    broken.add(p + ": [" + m.group(1) + "](" + target + ')');
                }
            }
        }
        assertTrue(broken.isEmpty(), () -> String.join("\n", broken));
    }

    /**
     * The tone periods' share against the three the same sentence gives.
     * Prose again, so the table check above does not reach it.
     */
    @Test
    void theToneShareIsTheSumOfTheThreeItStates() throws IOException {
        Matcher share = wrapped("take (\\d+\\.\\d)%, (\\d+\\.\\d)% and"
                + " (\\d+\\.\\d)% of the packed bytes, (\\d+\\.\\d)% between"
                + " them").matcher(read(EXP));
        assertTrue(share.find(), "experiments.md gives no tone shares");
        double sum = 0;
        for (int i = 1; i <= 3; i++) {
            sum += Double.parseDouble(share.group(i));
        }
        double said = Double.parseDouble(share.group(4));
        double got = Math.round(sum * 10.0) / 10.0;
        assertTrue(Math.abs(got - said) < 0.05, () -> "the three shares make "
                + got + "%, and the sentence says " + said + '%');
    }

    /**
     * The envelope saving against the two figures the same sentence gives,
     * and against what the corpus packs to. The figures are prose rather
     * than a table row, so the check above does not reach them.
     */
    @Test
    void theEnvelopeSavingIsTheDifferenceItStates() throws IOException {
        String experiments = read(EXP);
        Matcher both = wrapped("cost ([\\d,]+) bytes as SPEC\\.md has them, and"
                + " ([\\d,]+) under").matcher(experiments);
        assertTrue(both.find(), "experiments.md gives no envelope pair");
        Matcher saved = wrapped("saves ([\\d,]+) bytes, (\\d+\\.\\d)% of the"
                + " ([\\d,]+) the corpus").matcher(experiments);
        assertTrue(saved.find(), "experiments.md gives no envelope saving");
        long mine = number(both.group(1));
        long other = number(both.group(2));
        long says = number(saved.group(1));
        assertTrue(other - mine == says, () -> "the saving is stated as "
                + says + ", and " + other + " less " + mine + " is "
                + (other - mine));
        long whole = number(saved.group(3));
        double percent = Math.round(says * 1000.0 / whole) / 10.0;
        assertTrue(Double.parseDouble(saved.group(2)) == percent,
                () -> "the saving is stated as " + saved.group(2) + "% and is "
                        + percent + "% of " + whole);
        Matcher row = Pattern.compile("^\\| DTX2 files at `k` = 1 \\| ([\\d,]+) \\|",
                Pattern.MULTILINE).matcher(experiments);
        assertTrue(row.find(), "experiments.md has no row for k = 1");
        assertTrue(number(row.group(1)) == whole, () -> string(whole)
                + " is not what the packing table gives at k = 1, "
                + row.group(1));
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
        int held = ratios;
        assertTrue(seen >= 5, () -> "only " + seen
                + " figures parsed; the check is asleep");
        assertTrue(held >= 4, () -> "only " + held
                + " ratios parsed; the check is half asleep");
        assertTrue(wrong.isEmpty(), () -> String.join("\n", wrong));
    }

    @Test
    void everyDocumentHoldsOneWrapWidth() throws IOException {
        List<String> wide = new ArrayList<>();
        for (Path p : DOCUMENTS) {
            List<String> lines = Files.readAllLines(p);
            for (int at = 0; at < lines.size(); at++) {
                String line = lines.get(at);
                if (line.startsWith("|") || line.startsWith("    ")
                        || line.contains("](")) {
                    continue;
                }
                if (line.length() > 78) {
                    wide.add(p + ":" + (at + 1) + " runs to " + line.length());
                }
            }
        }
        assertTrue(wide.isEmpty(), () -> String.join("\n", wide)
                + "\nAGENTS.md asks one width, held.");
    }
}
