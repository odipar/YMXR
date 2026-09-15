package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every document against the house style's ban list.
 *
 * <p>{@code AGENTS.md} defines the rules - a program does not intend, and no
 * flourish - and this test holds the phrases struck in review under them.
 * Each entry is one struck phrase or the stem of one; a hit names the file
 * and line. A phrase that is legitimate in a new context comes off the list
 * in the same change that uses it.
 *
 * <p>The documents are found rather than listed. A list is a place a new
 * document is not, and the one that reached review unchecked was the one
 * nobody had added.
 */
final class HouseStyleTest {

    /** The two documents that define the rules, and so quote what they
     * strike. Every other Markdown file in the tree is read. */
    private static final List<String> DEFINES_THE_RULES =
            List.of("AGENTS.md", "CLAUDE.md");

    /** Struck in review, lowercase; matched as substrings. */
    private static final List<String> STRUCK = List.of(
            // roles and abstractions acting: a writer promising, a source
            // implying, a player being told, roles standing
            "promise",
            "guarantee",
            "implies",
            "imply ",
            "can be told",
            "roles stand",
            // a format does not rule, and does not measure: a measurement
            // is made of it, and its specification defines it
            "it ruled",
            "it measured",
            // a format does not answer a constraint: a choice is what
            // it was, and the constraint is what bound it
            "answered",
            // a specification defines; a tune and a build carry, and the
            // verb belongs to what a thing does
            "carries",
            // a column is one value a row; a thing does not sit anywhere
            "sits in",
            "stand apart",
            // a place is a row number, and bit 5 moves it: a thing that
            // has not moved needs no sentence
            "keeps its place",
            "keeps the place",
            "stands where it",
            // a document defines and cites; it rests on no other and
            // props none up. The trailing space keeps "stands once"
            // out of the match, which the substring alone would catch
            " stands on ",
            " stand on ",
            "is the base",
            // a period is counted, not flown
            "in flight",
            // a rule justified by quoting a speaking thing
            "spells out",
            "spell out",
            "because it says",
            "says it",
            "says so",
            "says what to take",
            // a thing does no human act: it requires, it provides
            "asks of",
            "asks for",
            "set-ness",
            "takes the machine with it",
            // "consumer" is a role the specification defines, as "caller"
            // and "owner" are roles: only the verb is struck
            "consume ",
            "consumes",
            "consumed",
            "consuming",
            "stand as they were",
            // a consumer does not understand a stream, it implements it
            "understand",
            "refuse",
            // the sweep: a trailing clause generalising the sentence
            "whatever",
            "whichever way",
            "where it sits",
            "stood still",
            // the metaphor in place of the operation
            " a tail ",
            "sliver",
            "literally",
            "smear",
            "bears it out",
            "pressure point",
            "door left open",
            "cover version",
            "smuggl",
            "catastroph",
            // shape: no em dash construct anywhere - a dash that must stay
            // is a single '-'; the list strikes the en dash and the minus
            // sign too
            "—",
            "–",
            "−",
            // a noun pressed into service as a verb
            "vendor",
            // the verdict: the sentence grading itself or its subject
            "is deliberate",
            "by design",
            "on purpose",
            "asked properly",
            "not a shrug",
            "most of the point",
            "the answer to that",
            "worth reading",
            "the ones that matter",
            "the whole point",
            // filler: cut unless the word carries the meaning
            "actually",
            // the five stand-ins for the action, struck in YMXS and here:
            // what a tune holds is the tune data structure, the rate a
            // tune states is the tune's rate, what the two chips give is
            // the figures of the two chips, a value a register takes is a
            // value that fits it, and a negation stands where the sentence
            // belongs. This vocabulary defines the noun "state" (a state
            // block, a register of YM2149 state), so only its verb forms
            // are struck. YMX's opcode `HOLD` is a name, so the entries
            // here are the verb forms rather than the bare stem.
            " hold ",
            "holds",
            "holding",
            "held",
            "states",
            "stated",
            "stating",
            // The verb forms rather than the stem: "giv" would match no
            // word here, but "tak" stands inside "mistake" and "stands
            // on" inside "stands once", so an entry that would read a
            // word apart carries the spaces that keep it out
            "gives",
            "giving",
            "given",
            " take ",
            " takes ",
            " taking ",
            " taken ",
            "nothing",
            // possessive decoration: a table of its own is a table
            " own ",
            " own.",
            // a person's viewpoint in a sentence about a file
            // the metaphor: a form is an encoding of the structure, and a
            // tune is encoded rather than written down
            "written down",
            "write down",
            "writes down",
            "writing down",
            "a tune down",
            "the structure down",
            "puts down",
            // a "no X" where the operation has a name: a reader skips a
            // blank line, the player leaves a register as it is
            "belongs to no",
            "performs no ",
            "writes no ",
            "reaches no ",
            "and no other",
            "for none",
            "on no chip",
            "with no error",
            "is not defined",
            "not defined by",
            "no other step",
            "would rather");

    /** Every Markdown file in the tree but the two that define the rules. */
    private static List<Path> documents() throws IOException {
        try (Stream<Path> tree = Files.walk(Path.of("."))) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !DEFINES_THE_RULES
                            .contains(path.getFileName().toString()))
                    .sorted()
                    .toList();
        }
    }

    /** The five languages this repository writes comments in. */
    private static final List<String> SOURCES =
            List.of(".java", ".go", ".S", ".py", ".sh");

    /** Every source in the tree but this one, which quotes the struck
     *  phrases to ban them, and the built trees, which are output. */
    private static List<Path> sources() throws IOException {
        try (Stream<Path> tree = Files.walk(Path.of("."))) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> SOURCES.stream()
                            .anyMatch(one -> path.toString().endsWith(one)))
                    .filter(path -> !path.toString().contains("/target/")
                            && !path.toString().contains("/dist/"))
                    .filter(path -> !path.getFileName().toString()
                            .equals("HouseStyleTest.java"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * The comment lines of a source, each with the line it stands on.
     *
     * <p>R0.1 binds a code comment as it binds a document, and the code
     * around a comment does not: a field named {@code holds} or a call to
     * {@code getState} is a name and not prose. So this reads the comment
     * text and leaves the rest of the line out.
     */
    private static List<String[]> commentsOf(Path source) throws IOException {
        String named = source.toString();
        if (named.endsWith(".java")) {
            return java(Files.readAllLines(source));
        }
        if (named.endsWith(".go")) {
            return marked(Files.readAllLines(source), "//");
        }
        if (named.endsWith(".S")) {
            return assembly(Files.readAllLines(source));
        }
        // A shell comment and a Python one open the same way, and a
        // Python docstring is read as the prose it is.
        return script(Files.readAllLines(source));
    }

    /** A javadoc block and the line comments beside it. */
    private static List<String[]> java(List<String> lines) {
        List<String[]> out = new ArrayList<>();
        boolean inside = false;
        for (int at = 0; at < lines.size(); at++) {
            String said = lines.get(at).strip();
            if (said.startsWith("/*")) {
                inside = true;
            }
            if (inside) {
                out.add(new String[] {String.valueOf(at + 1),
                        said.replaceFirst("^/?\\*+/?", "").strip()});
                if (said.endsWith("*/")) {
                    inside = false;
                }
                continue;
            }
            if (said.startsWith("//")) {
                out.add(new String[] {String.valueOf(at + 1),
                        said.substring(2).strip()});
            }
        }
        return out;
    }

    /** Every line opening with {@code mark}, that mark off. */
    private static List<String[]> marked(List<String> lines, String mark) {
        List<String[]> out = new ArrayList<>();
        for (int at = 0; at < lines.size(); at++) {
            String said = lines.get(at).strip();
            if (said.startsWith(mark)) {
                out.add(new String[] {String.valueOf(at + 1),
                        said.substring(mark.length()).strip()});
            }
        }
        return out;
    }

    /**
     * A 68000 source: rmac reads a line opening with {@code *} or
     * {@code ;} as a comment, and a {@code ;} after an instruction as the
     * rest of that line.
     */
    private static List<String[]> assembly(List<String> lines) {
        List<String[]> out = new ArrayList<>();
        for (int at = 0; at < lines.size(); at++) {
            String line = lines.get(at);
            String said = line.strip();
            if (said.startsWith("*") || said.startsWith(";")) {
                out.add(new String[] {String.valueOf(at + 1),
                        said.replaceFirst("^[*;\\s]+", "").strip()});
                continue;
            }
            int mark = line.indexOf(';');
            if (mark >= 0) {
                out.add(new String[] {String.valueOf(at + 1),
                        line.substring(mark + 1).strip()});
            }
        }
        return out;
    }

    /** A shell or Python source: a {@code #} line, and a Python docstring,
     *  which is prose and not a string a program prints. */
    private static List<String[]> script(List<String> lines) {
        List<String[]> out = new ArrayList<>();
        String open = null;
        for (int at = 0; at < lines.size(); at++) {
            String said = lines.get(at).strip();
            if (open != null) {
                out.add(new String[] {String.valueOf(at + 1),
                        said.replace(open, "").strip()});
                if (said.contains(open)) {
                    open = null;
                }
                continue;
            }
            String head = said.startsWith("r") ? said.substring(1) : said;
            String mark = head.startsWith("\"\"\"") ? "\"\"\""
                    : head.startsWith("'''") ? "'''" : null;
            if (mark != null) {
                String body = head.substring(mark.length());
                int end = body.indexOf(mark);
                out.add(new String[] {String.valueOf(at + 1),
                        (end < 0 ? body : body.substring(0, end)).strip()});
                if (end < 0) {
                    open = mark;
                }
                continue;
            }
            if (said.startsWith("#") && !said.startsWith("#!")) {
                out.add(new String[] {String.valueOf(at + 1),
                        said.replaceFirst("^#+", "").strip()});
            }
        }
        return out;
    }

    /**
     * The comments of the Java and the Go tree, against the same list.
     *
     * <p>R0.1 makes the rules bind every document, code comment and commit
     * message, and R0.2's test read the documents alone. A comment drifted
     * where no test read it, so the comments are read here: the same
     * phrases, the same list, and the paragraph joined as a document's is,
     * since a comment wraps at the same width.
     *
     * <p>Five languages, and a comment opens differently in each: javadoc
     * and {@code //} in Java, {@code //} in Go, {@code *} and {@code ;} in
     * a 68000 source, and {@code #} with a docstring in Python and in a
     * shell script.
     */
    @Test
    void noCommentHasAStruckPhrase() throws IOException {
        List<Path> sources = sources();
        assertTrue(sources.size() > 50, () -> "only " + sources.size()
                + " sources read; the walk is asleep");
        for (String kind : SOURCES) {
            assertTrue(sources.stream().anyMatch(one -> one.toString().endsWith(kind)),
                    () -> "no " + kind + " source was read");
        }
        List<String> hits = new ArrayList<>();
        for (Path source : sources) {
            List<String[]> comments = commentsOf(source);
            for (String[] one : comments) {
                String line = " " + one[1].toLowerCase();
                for (String struck : STRUCK) {
                    if (line.contains(struck)) {
                        hits.add(source + ":" + one[0] + " has \"" + struck + '"');
                    }
                }
            }
            hits.addAll(wrappedHits(source,
                    comments.stream().map(one -> one[1]).toList()));
        }
        assertTrue(hits.isEmpty(), () -> String.join("\n", hits)
                + "\nAGENTS.md binds a code comment as it binds a document"
                + " (R0.1); reword the comment, or drop the entry from this"
                + " list in the same change.");
    }

    @Test
    void noDocumentHasAStruckPhrase() throws IOException {
        List<Path> documents = documents();
        assertTrue(!documents.isEmpty(), "no document was found to hold");
        List<String> hits = new ArrayList<>();
        for (Path document : documents) {
            List<String> lines = Files.readAllLines(document);
            boolean fenced = false;
            boolean span = false;
            for (int at = 0; at < lines.size(); at++) {
                if (lines.get(at).isBlank()) {
                    // a paragraph ends, and a span with it
                    span = false;
                }
                if (lines.get(at).startsWith("```")) {
                    fenced = !fenced;
                    continue;
                }
                // A fence and an indented block are quoted: a message a
                // tool writes, a file's bytes, a command. The rules are
                // for prose, and a quotation keeps the words it quotes.
                if (fenced || lines.get(at).startsWith("    ")) {
                    continue;
                }
                // a space in front, so an entry that leads
                // with one matches a word at the start of a
                // line as well as inside one
                String line = " " + quoted(lines.get(at), span).toLowerCase();
                span = open(lines.get(at), span);
                for (String struck : STRUCK) {
                    if (line.contains(struck)) {
                        hits.add(document + ":" + (at + 1)
                                + " has \"" + struck + '"');
                    }
                }
            }
            hits.addAll(wrappedHits(document, lines));
        }
        assertTrue(hits.isEmpty(), () -> String.join("\n", hits)
                + "\nAGENTS.md has the rule each phrase was struck under;"
                + " reword the line, or drop the entry from this list in the"
                + " same change.");
    }

    /**
     * The hits a line wrap hides. A phrase broken across two lines stands in
     * neither of them, so every paragraph is read joined as well, and what
     * the joined text has beyond the hits in its lines is reported at
     * the line the paragraph begins on. A table row, an indented block and a
     * fence break a paragraph: joining those would put words side by side
     * that no sentence puts there.
     */
    private static List<String> wrappedHits(Path document, List<String> lines) {
        List<String> hits = new ArrayList<>();
        int from = 0;
        for (int at = 0; at <= lines.size(); at++) {
            boolean breaks = at == lines.size() || lines.get(at).isBlank()
                    || lines.get(at).startsWith("|")
                    || lines.get(at).startsWith("    ")
                    || lines.get(at).startsWith("```");
            if (!breaks) {
                continue;
            }
            if (at > from) {
                List<String> paragraph = lines.subList(from, at);
                String joined = " " + quoted(String.join(" ", paragraph), false).toLowerCase();
                for (String struck : STRUCK) {
                    int whole = occurrences(joined, struck);
                    int apart = 0;
                    boolean span = false;
                    for (String line : paragraph) {
                        apart += occurrences(" " + quoted(line, span).toLowerCase(), struck);
                        span = open(line, span);
                    }
                    for (int n = apart; n < whole; n++) {
                        hits.add(document + ":" + (from + 1) + " has \""
                                + struck + "\", broken by a line wrap");
                    }
                }
            }
            from = at + 1;
        }
        return hits;
    }

    /**
     * {@code said} with every inline code span blanked, the backticks
     * included, where {@code open} says a span from an earlier line of
     * the paragraph is still open.
     *
     * <p>A span between backticks is a quotation: a message a tool
     * writes, a column's name, a flag, a field of a file. The rules are
     * for prose, and a quotation keeps the words it quotes, so a struck
     * phrase inside one is the quoted program's and not this document's.
     * Each character of a span becomes a space, so a hit that straddles
     * a span's edge still stands out.
     */
    private static String quoted(String said, boolean open) {
        StringBuilder out = new StringBuilder(said);
        boolean inside = open;
        for (int at = 0; at < out.length(); at++) {
            if (out.charAt(at) == '`') {
                inside = !inside;
                out.setCharAt(at, ' ');
            } else if (inside) {
                out.setCharAt(at, ' ');
            }
        }
        return out.toString();
    }

    /** Whether a span open at the start of {@code said} is open at its
     *  end: a span wraps with the paragraph it stands in. */
    private static boolean open(String said, boolean open) {
        boolean inside = open;
        for (int at = 0; at < said.length(); at++) {
            if (said.charAt(at) == '`') {
                inside = !inside;
            }
        }
        return inside;
    }

    /** How many times a struck phrase stands in a run of text. */
    private static int occurrences(String text, String struck) {
        int found = 0;
        for (int at = text.indexOf(struck); at >= 0;
                at = text.indexOf(struck, at + 1)) {
            found++;
        }
        return found;
    }
}
