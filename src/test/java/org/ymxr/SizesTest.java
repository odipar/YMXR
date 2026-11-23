package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** doc/sizes.md: the links of the dumps of ym/test and of the corpus, the
 *  pieces of each effect's timer code and of the block of the effects, and
 *  the core of the patch of 68k/test/sizes, applied to a copy of 68k, with
 *  its table cut as the build cuts it. The rig's -onlysizes reads the
 *  document's cycles. */
class SizesTest {

    private static final Path DOC = Path.of("doc/sizes.md");
    private static final Path PATCH = Path.of("68k/test/sizes/one-copy.patch");
    private static final Path DUMPS = Path.of("ym/test");
    private static final Path RMAC = Path.of("rmac");
    private static final String EFFECTS = "effects";

    /** The pieces of an effect's timer code, effect 0's for each. */
    private static final List<String> TIMER =
            List.of("effectN_6", "effectN_7", "effectN_8", "effectN_9");

    /** The plain core of the patch: its bytes and its table. */
    private record Cut(byte[] core, Pieces table) {
    }

    private static @Nullable Cut patched;

    /** The plain core assembled from a copy of 68k with the patch applied,
     *  and its table, once a run. */
    private static synchronized Cut patched() throws IOException, InterruptedException {
        Cut done = patched;
        if (done != null) {
            return done;
        }
        // a short path: rmac stops on an include path past about 130
        // characters
        Path work = Files.createTempDirectory(Path.of("/tmp"), "ymxr");
        Path sources = work.resolve("68k");
        try (Stream<Path> all = Files.walk(Path.of("68k"))) {
            for (Path from : all.filter(p -> !p.startsWith(Path.of("68k", "test"))).toList()) {
                Path to = work.resolve(from.toString());
                if (Files.isDirectory(from)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(from, to);
                }
            }
        }
        Process run = new ProcessBuilder("patch", "-s", "-p1", "-d", work.toString(), "-i",
                PATCH.toAbsolutePath().toString()).redirectErrorStream(true).start();
        String said = new String(run.getInputStream().readAllBytes());
        assertEquals(0, run.waitFor(), PATCH + " does not apply: " + said);
        Cut made = new Cut(Binaries.assemble(RMAC, sources, Binaries.CORE),
                Pieces.cut(RMAC, sources, Binaries.CORE));
        patched = made;
        return made;
    }

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String prose() throws IOException {
        return Files.readString(DOC).replaceAll("\\s+", " ");
    }

    /** The bytes a link of the core keeps for the tune. */
    private static int linked(byte[] core, Pieces table, TuneFile tune) {
        return Link.link(core, table, Link.blocks(List.of(tune))).length;
    }

    private static int today(TuneFile tune) {
        return linked(Binaries.core(), Pieces.carried(Binaries.CORE), tune);
    }

    private static TuneFile converted(Path dump) throws IOException {
        return TuneFile.read(YmToYmxr.convert(Files.readAllBytes(dump), List.of("-silent"),
                new Report(false)).written().file());
    }

    private static List<Path> dumps() throws IOException {
        try (Stream<Path> all = Files.list(DUMPS)) {
            return all.filter(p -> p.toString().endsWith(".ym")).sorted().toList();
        }
    }

    /** The blocks of the plain core a link keeps for the tune, in the
     *  order of Link.blocks, as LINKER.md's table writes them. */
    private static String kept(TuneFile tune) {
        Set<String> has = new LinkedHashSet<>();
        for (Pieces.Piece piece : Pieces.carried(Binaries.CORE).pieces()) {
            has.add(piece.block());
        }
        List<String> kept = new ArrayList<>(Link.blocks(List.of(tune)));
        kept.retainAll(has);
        return kept.isEmpty() ? "none" : String.join(", ", kept);
    }

    /** The table of the eleven dumps: the blocks kept, the bytes linked,
     *  and the bytes with one copy of steps 6 and 7. */
    @Test
    void theDocumentReadsTheDumps() throws Exception {
        String doc = Files.readString(DOC);
        Cut copy = patched();
        List<Path> dumps = dumps();
        assertEquals(11, dumps.size());
        List<String> missing = new ArrayList<>();
        for (Path dump : dumps) {
            TuneFile tune = converted(dump);
            String name = dump.getFileName().toString();
            String row = "| " + name.substring(0, name.length() - 3) + " | " + kept(tune) + " | "
                    + grouped(today(tune)) + " | "
                    + grouped(linked(copy.core(), copy.table(), tune)) + " |";
            if (!doc.contains("\n" + row + "\n")) {
                missing.add(row);
            }
        }
        assertTrue(missing.isEmpty(), "sizes.md has no rows\n" + String.join("\n", missing));
    }

    /** The pieces of each effect's timer code and of the block of the
     *  effects, a row each with its bytes, each effect's alike; the floor
     *  of each core, and the sizes of the places of a few cycles more. */
    @Test
    void theDocumentReadsThePieces() throws Exception {
        String doc = Files.readString(DOC);
        String prose = prose();
        Pieces plain = Pieces.carried(Binaries.CORE);
        Map<String, Integer> pieces = new TreeMap<>();
        Map<String, Integer> blocks = new TreeMap<>();
        for (Pieces.Piece piece : plain.pieces()) {
            pieces.put(piece.name(), piece.size());
            blocks.merge(piece.block(), piece.size(), Integer::sum);
        }
        List<String> listed = new ArrayList<>();
        for (String name : pieces.keySet()) {
            if (name.startsWith(EFFECTS + "_")
                    || TIMER.contains(name.replace("effect0", "effectN"))) {
                listed.add(name);
            }
        }
        listed.sort(Comparator.comparingInt(name -> Integer.parseInt(name.replaceAll(".*_", ""))));
        int each = 0;
        for (String name : listed) {
            if (name.startsWith("effect0_")) {
                for (int e = 1; e < 4; e++) {
                    assertEquals(size(pieces, name),
                            size(pieces, name.replace("effect0", "effect" + e)),
                            name + " is another size in effect " + e);
                }
                each += size(pieces, name);
            }
            String row = " | `" + name.replace("effect0", "effectN") + "` | "
                    + grouped(size(pieces, name)) + " |\n";
            assertTrue(doc.contains(row), "sizes.md has no row ending" + row);
        }
        assertTrue(prose.contains("The timer code of an effect is " + grouped(each)
                        + " bytes, and the block of the effects " + grouped(size(blocks, EFFECTS))
                        + " bytes."),
                "sizes.md reads other sizes of the timer code and the block of the effects");
        Cut copy = patched();
        int floor = floor(Binaries.core(), plain);
        int withCopy = floor(copy.core(), copy.table());
        assertTrue(prose.contains("The floor of the plain core is " + grouped(floor)
                        + " bytes, and " + grouped(withCopy)
                        + " with one copy of steps 6 and 7."),
                "sizes.md reads other floors of the plain core");
        // the second copy is in the floor, so every player loses its bytes
        assertEquals(Binaries.core().length - copy.core().length, floor - withCopy);
        assertTrue(prose.contains("One copy of steps 6 and 7 is " + grouped(floor - withCopy)
                + " bytes fewer in every player."), "sizes.md reads another size of the copy");
        StringBuilder others = new StringBuilder("The floor of the core with the deferred start"
                + " in is ");
        String[] names = {"the deferred start", "the lookup", "the lookup and the deferred start"};
        boolean[][] switches = {{true, false}, {false, true}, {true, true}};
        for (int i = 0; i < 3; i++) {
            Binaries.Binary binary = Binaries.binary(false, false, true, switches[i][0],
                    switches[i][1]);
            if (i > 0) {
                others.append(i == 2 ? ", and of the core with " : ", of the core with ")
                        .append(names[i]).append(" in ");
            }
            others.append(grouped(floor(Binaries.carried(binary.name()),
                    Pieces.carried(binary))));
            if (i == 0) {
                others.append(" bytes");
            }
        }
        others.append(".");
        assertTrue(prose.contains(others.toString()), "sizes.md does not read: " + others);
        int least = Integer.MAX_VALUE;
        int most = 0;
        for (int e = 0; e < 4; e++) {
            least = Math.min(least, size(blocks, "effect" + e));
            most = Math.max(most, size(blocks, "effect" + e));
        }
        assertTrue(prose.contains("Each effect's block, its frame code, general handler and"
                        + " timer code, is " + grouped(least) + " to " + grouped(most)
                        + " bytes of the plain core"),
                "sizes.md reads other sizes of an effect's block");
        assertTrue(prose.contains("`several`, the start of a source of several columns, is "
                        + grouped(size(blocks, "several")) + " bytes."),
                "sizes.md reads another size of several");
    }

    private static int size(Map<String, Integer> sizes, String name) {
        Integer size = sizes.get(name);
        assertTrue(size != null, "no piece or block " + name);
        return size == null ? 0 : size;
    }

    /** The floor: the bytes a link keeps for a song of no effect. */
    private static int floor(byte[] core, Pieces table) {
        return Link.link(core, table, Set.of("align")).length;
    }

    /** The corpus's table. Each row's bytes are the link of a dump of
     *  ym/test that keeps the same blocks, and the sentence of the corpus
     *  is the sums of the rows. Where YM_CORPUS is the corpus's folder,
     *  the rows are the corpus's: the dumps of each set of blocks kept. */
    @Test
    void theDocumentReadsTheCorpus() throws Exception {
        String doc = Files.readString(DOC);
        String prose = prose();
        // the links of the dumps of ym/test, by the blocks each keeps
        Map<String, Integer> links = new TreeMap<>();
        for (Path dump : dumps()) {
            TuneFile tune = converted(dump);
            links.put(kept(tune), today(tune));
        }
        String section = doc.substring(doc.indexOf("\n## The corpus\n"),
                doc.indexOf("\n## The floor\n"));
        Matcher row = Pattern.compile("^\\| ([a-z0-9, ]+) \\| ([\\d,]+) \\| ([\\d,]+) \\|$",
                Pattern.MULTILINE).matcher(section);
        Map<String, Integer> counts = new TreeMap<>();
        long dumps = 0;
        long bytes = 0;
        while (row.find()) {
            Integer link = links.get(row.group(1));
            assertTrue(link != null, "no dump of ym/test keeps " + row.group(1));
            assertEquals(link == null ? 0 : link, number(row.group(3)),
                    "the bytes of " + row.group(1));
            int count = number(row.group(2));
            counts.put(row.group(1), count);
            dumps += count;
            bytes += (long) count * number(row.group(3));
        }
        assertTrue(counts.size() > 1, "sizes.md has no table of the corpus");
        int floor = floor(Binaries.core(), Pieces.carried(Binaries.CORE));
        String share = "The player of each of the " + grouped(dumps)
                + " dumps links from the plain core. The floor, " + grouped(floor) + " bytes, is "
                + Math.round(100.0 * floor * dumps / bytes) + "% of the " + grouped(bytes)
                + " bytes of the " + grouped(dumps) + " players.";
        assertTrue(prose.contains(share), "sizes.md does not read: " + share);
        String folder = System.getenv("YM_CORPUS");
        if (folder != null && Files.isDirectory(Path.of(folder))) {
            assertEquals(counts, corpus(Path.of(folder)), "the dumps of each set of blocks");
        }
    }

    private static int number(String grouped) {
        return Integer.parseInt(grouped.replace(",", ""));
    }

    /** The dumps of the corpus in the folder, by the blocks of the plain
     *  core each keeps. */
    private static Map<String, Integer> corpus(Path folder) throws IOException {
        List<Path> dumps;
        try (Stream<Path> all = Files.list(folder)) {
            // a dump is a file: the corpus has directories named like one
            dumps = all.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ym"))
                    .sorted().toList();
        }
        List<String> kept = dumps.parallelStream().map(dump -> {
            try {
                TuneFile tune = converted(dump);
                assertEquals(0, tune.effects() & (Columns.DEFERS | Columns.MAPS | Columns.CUTS),
                        dump + " links from a core other than the plain one");
                return kept(tune);
            } catch (IOException wrong) {
                throw new IllegalStateException(wrong);
            }
        }).toList();
        Map<String, Integer> counts = new TreeMap<>();
        for (String one : kept) {
            counts.merge(one, 1, Integer::sum);
        }
        return counts;
    }
}
