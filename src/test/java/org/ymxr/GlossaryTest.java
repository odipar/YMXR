package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The glossary against the document that explains the terms.
 *
 * <p>{@code AGENTS.md} allows one vocabulary, and requirements.md R0.6 puts
 * it in two files: the glossary lists a term, terminology.md explains it.
 * Two files are two places to change, so this test fails when a term is
 * explained and not listed.
 *
 * <p>Bold does two jobs in terminology.md. A run that ends in a full stop
 * leads a paragraph; every other run is a term.
 */
final class GlossaryTest {

    private static final Path GLOSSARY = Path.of("doc/glossary.md");

    private static final Path TERMINOLOGY = Path.of("doc/terminology.md");

    private static final Pattern BOLD = Pattern.compile("\\*\\*([^*]+)\\*\\*");

    /** Lowercase, without a leading article or the markup around a term. */
    private static String plain(String term) {
        String bare = term.replace("`", "").trim().toLowerCase();
        return bare.startsWith("the ") ? bare.substring(4) : bare;
    }

    /** The first cell of every row of the glossary's table. */
    private static Set<String> listed() throws IOException {
        Set<String> terms = new LinkedHashSet<>();
        for (String line : Files.readAllLines(GLOSSARY)) {
            if (!line.startsWith("| ") || line.startsWith("| term")
                    || line.startsWith("|---")) {
                continue;
            }
            terms.add(plain(line.split("\\|")[1]));
        }
        return terms;
    }

    @Test
    void everyTermExplainedIsListed() throws IOException {
        Set<String> listed = listed();
        assertTrue(listed.size() > 20,
                () -> "the glossary table read as " + listed.size() + " rows");
        List<String> missing = new ArrayList<>();
        List<String> lines = Files.readAllLines(TERMINOLOGY);
        for (int at = 0; at < lines.size(); at++) {
            Matcher bold = BOLD.matcher(lines.get(at));
            while (bold.find()) {
                String term = bold.group(1);
                if (term.endsWith(".")) {
                    continue;
                }
                String plain = plain(term);
                String singular = plain.endsWith("s")
                        ? plain.substring(0, plain.length() - 1) : plain;
                if (!listed.contains(plain) && !listed.contains(singular)) {
                    missing.add(TERMINOLOGY + ":" + (at + 1) + " \"" + term
                            + "\" is explained and not listed");
                }
            }
        }
        assertTrue(missing.isEmpty(), () -> String.join("\n", missing)
                + "\nAdd the term to " + GLOSSARY + ", or drop the bold if"
                + " the run is not a term.");
    }
}
