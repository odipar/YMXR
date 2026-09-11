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
            "giv",
            "tak",
            "nothing",
            // possessive decoration: a table of its own is a table
            " own ",
            " own.",
            // a person's viewpoint in a sentence about a file
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

    @Test
    void noDocumentHasAStruckPhrase() throws IOException {
        List<Path> documents = documents();
        assertTrue(!documents.isEmpty(), "no document was found to hold");
        List<String> hits = new ArrayList<>();
        for (Path document : documents) {
            List<String> lines = Files.readAllLines(document);
            for (int at = 0; at < lines.size(); at++) {
                // a space in front, so an entry that leads
                // with one matches a word at the start of a
                // line as well as inside one
                String line = " " + lines.get(at).toLowerCase();
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
                String joined = " " + String.join(" ", paragraph).toLowerCase();
                for (String struck : STRUCK) {
                    int whole = occurrences(joined, struck);
                    int apart = 0;
                    for (String line : paragraph) {
                        apart += occurrences(" " + line.toLowerCase(), struck);
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
