package org.ymxr;

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

    /** The rows of the column table, as (index count, bytes) pairs. */
    private static List<int[]> columnTable(String spec) {
        int at = spec.indexOf("| column | bytes | holds |");
        assertTrue(at >= 0, "SPEC.md has no column table");
        String block = spec.substring(at, spec.indexOf("\n\n", at));
        Matcher m = Pattern.compile("^\\| (\\d+)(?: to (\\d+))? \\| (\\d+) \\|",
                Pattern.MULTILINE).matcher(block);
        List<int[]> rows = new ArrayList<>();
        while (m.find()) {
            int first = Integer.parseInt(m.group(1));
            int last = m.group(2) == null ? first : Integer.parseInt(m.group(2));
            rows.add(new int[] {first, last, Integer.parseInt(m.group(3))});
        }
        return rows;
    }

    @Test
    void theColumnTableAddsUpToWhatTheProseClaims() throws IOException {
        String spec = read(SPEC);
        List<int[]> rows = columnTable(spec);
        int columns = 0;
        int bytes = 0;
        int next = 0;
        List<String> gaps = new ArrayList<>();
        for (int[] r : rows) {
            if (r[0] != next) {
                gaps.add("column " + next + " is where " + r[0] + " stands");
            }
            next = r[1] + 1;
            columns += r[1] - r[0] + 1;
            bytes += r[2];
        }
        assertTrue(gaps.isEmpty(), () -> "the column indices break: " + gaps);

        Matcher m = Pattern.compile(
                "(\\d+) columns of the 32 R\\d+\\.\\d+ allows, and (\\d+) bytes")
                .matcher(spec);
        assertTrue(m.find(), "SPEC.md does not state its column and byte count");
        int saidColumns = Integer.parseInt(m.group(1));
        int saidBytes = Integer.parseInt(m.group(2));
        int c = columns;
        int b = bytes;
        assertTrue(saidColumns == c && saidBytes == b,
                () -> "the table holds " + c + " columns and " + b
                        + " bytes; the prose says " + saidColumns + " and "
                        + saidBytes);
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
        Matcher share = Pattern.compile("take (\\d+\\.\\d)%, (\\d+\\.\\d)% and"
                + "\\s+(\\d+\\.\\d)% of the packed bytes,\\s+(\\d+\\.\\d)%"
                + " between them").matcher(read(EXP));
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
        Matcher both = Pattern.compile("cost\\s+([\\d,]+) bytes as\\s+SPEC\\.md"
                + " has them, and ([\\d,]+) under").matcher(experiments);
        assertTrue(both.find(), "experiments.md gives no envelope pair");
        Matcher saved = Pattern.compile("saves ([\\d,]+) bytes, (\\d+)% of the"
                + "\\s+([\\d,]+) the corpus").matcher(experiments);
        assertTrue(saved.find(), "experiments.md gives no envelope saving");
        long mine = number(both.group(1));
        long other = number(both.group(2));
        long says = number(saved.group(1));
        assertTrue(other - mine == says, () -> "the saving is stated as "
                + says + ", and " + other + " less " + mine + " is "
                + (other - mine));
        long whole = number(saved.group(3));
        long percent = Math.round(says * 100.0 / whole);
        assertTrue(Long.parseLong(saved.group(2)) == percent,
                () -> "the saving is stated as " + saved.group(2) + "% and is "
                        + percent + "% of " + whole);
        assertTrue(experiments.contains("| " + string(whole) + " | 0.72 |"),
                string(whole) + " is not what the packing table gives");
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
