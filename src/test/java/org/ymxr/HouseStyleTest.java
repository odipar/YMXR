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
 * <p>{@code AGENTS.md} states the rules - nothing acts on its own, and no
 * flourish - and this test holds the phrases struck in review under them.
 * Each entry is one struck phrase or the stem of one; a hit names the file
 * and line. A phrase that is legitimate in a new context comes off the list
 * in the same change that uses it, so the exception is deliberate.
 *
 * <p>The documents are found rather than listed. A list is a place a new
 * document is not, and the one that reached review unchecked was the one
 * nobody had added.
 */
final class HouseStyleTest {

    /** The two documents that state the rules, and so quote what they
     * strike. Every other Markdown file in the tree is held. */
    private static final List<String> STATES_THE_RULES =
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
            // is taken of it, and its specification states what it states
            "it ruled",
            "it measured",
            // a format does not answer a constraint: a choice is what
            // it was, and the constraint is what bound it
            "answered",
            // a specification defines; a tune and a build carry, and keep
            // the verb for what a thing holds
            "specification carries",
            // a column holds a value; nothing sits anywhere
            "sits in",
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
            "actually");

    /** Every Markdown file in the tree but the two that state the rules. */
    private static List<Path> documents() throws IOException {
        try (Stream<Path> tree = Files.walk(Path.of("."))) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !STATES_THE_RULES
                            .contains(path.getFileName().toString()))
                    .sorted()
                    .toList();
        }
    }

    @Test
    void everyDocumentCarriesNoStruckPhrase() throws IOException {
        List<Path> documents = documents();
        assertTrue(!documents.isEmpty(), "no document was found to hold");
        List<String> hits = new ArrayList<>();
        for (Path document : documents) {
            List<String> lines = Files.readAllLines(document);
            for (int at = 0; at < lines.size(); at++) {
                String line = lines.get(at).toLowerCase();
                for (String struck : STRUCK) {
                    if (line.contains(struck)) {
                        hits.add(document + ":" + (at + 1)
                                + " carries \"" + struck + '"');
                    }
                }
            }
        }
        assertTrue(hits.isEmpty(), () -> String.join("\n", hits)
                + "\nAGENTS.md has the rule each phrase was struck under;"
                + " reword the line, or take the entry off this list in the"
                + " same change.");
    }
}
