package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** doc/sizes.md: the links of the dumps of ym/test and of the corpus, and
 *  the cores of the patches of 68k/test/sizes, each applied to a copy of
 *  68k, with each table cut as the build cuts it. The rig's -onlysizes
 *  reads the document's cycles. */
class SizesTest {

    private static final Path DOC = Path.of("doc/sizes.md");
    private static final Path PATCHES = Path.of("68k/test/sizes");
    private static final Path DUMPS = Path.of("ym/test");
    private static final Path RMAC = Path.of("rmac");
    /** The block every song that runs an effect keeps, which the pieces
     *  patch adds. */
    private static final String EFFECTS = "effects";

    /** A core of a patched copy of 68k: its bytes and its table. */
    private record Cut(byte[] core, Pieces table) {
    }

    private static final Map<String, Cut> CUTS = new HashMap<>();

    /** The core of those switches assembled from a copy of 68k with the
     *  patches named applied in order, and its table, once a run. */
    private static synchronized Cut cut(boolean defers, boolean maps, String... patches)
            throws IOException, InterruptedException {
        String key = defers + " " + maps + " " + String.join(" ", patches);
        Cut done = CUTS.get(key);
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
        for (String patch : patches) {
            Process run = new ProcessBuilder("patch", "-s", "-p1", "-d", work.toString(), "-i",
                    PATCHES.resolve(patch).toAbsolutePath().toString())
                    .redirectErrorStream(true).start();
            String said = new String(run.getInputStream().readAllBytes());
            assertEquals(0, run.waitFor(), patch + " does not apply: " + said);
        }
        Binaries.Binary binary = Binaries.binary(false, false, true, defers, maps);
        Cut made = new Cut(Binaries.assemble(RMAC, sources, binary),
                Pieces.cut(RMAC, sources, binary));
        CUTS.put(key, made);
        return made;
    }

    private static String grouped(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String prose() throws IOException {
        return Files.readString(DOC).replaceAll("\\s+", " ");
    }

    /** The bytes a link of the core keeps for the tune, with the block of
     *  the effects where the tune runs one and the core has it. */
    private static int linked(byte[] core, Pieces table, TuneFile tune) {
        Set<String> blocks = new LinkedHashSet<>(Link.blocks(List.of(tune)));
        if ((tune.effects() & 0x0F) != 0) {
            blocks.add(EFFECTS);
        }
        return Link.link(core, table, blocks).length;
    }

    private static TuneFile converted(Path dump) throws IOException {
        return TuneFile.read(YmToYmxr.convert(Files.readAllBytes(dump), List.of("-silent"),
                new Report(false)).written().file());
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

    /** The table of the eleven dumps: the blocks kept, the bytes linked
     *  today, with the pieces and with one copy of steps 6 and 7 as well. */
    @Test
    void theDocumentReadsTheDumps() throws Exception {
        String doc = Files.readString(DOC);
        Cut pieces = cut(false, false, "pieces.patch");
        Cut copy = cut(false, false, "pieces.patch", "one-copy.patch");
        List<Path> dumps;
        try (Stream<Path> all = Files.list(DUMPS)) {
            dumps = all.filter(p -> p.toString().endsWith(".ym")).sorted().toList();
        }
        assertEquals(11, dumps.size());
        List<String> missing = new ArrayList<>();
        for (Path dump : dumps) {
            TuneFile tune = converted(dump);
            String name = dump.getFileName().toString();
            String row = "| " + name.substring(0, name.length() - 3) + " | " + kept(tune) + " | "
                    + grouped(Link.link(Binaries.core(), Pieces.carried(Binaries.CORE),
                            Link.blocks(List.of(tune))).length) + " | "
                    + grouped(linked(pieces.core(), pieces.table(), tune)) + " | "
                    + grouped(linked(copy.core(), copy.table(), tune)) + " |";
            if (!doc.contains("\n" + row + "\n")) {
                missing.add(row);
            }
        }
        assertTrue(missing.isEmpty(), "sizes.md has no rows\n" + String.join("\n", missing));
    }

    /** The pieces of the patch, a row each with its block and its bytes,
     *  each effect's alike, and the floor of each core before and after. */
    @Test
    void theDocumentReadsThePieces() throws Exception {
        String doc = Files.readString(DOC);
        String prose = prose();
        Cut plain = cut(false, false, "pieces.patch");
        Map<String, Integer> pieces = new TreeMap<>();
        Map<String, Integer> blocks = new TreeMap<>();
        for (Pieces.Piece piece : plain.table().pieces()) {
            pieces.put(piece.name(), piece.size());
            blocks.merge(piece.block(), piece.size(), Integer::sum);
        }
        List<String> added = new ArrayList<>();
        for (String name : pieces.keySet()) {
            if (name.startsWith(EFFECTS + "_") || name.matches("effect0_1[0-9]")) {
                added.add(name);
            }
        }
        added.sort(Comparator.comparingInt(name -> Integer.parseInt(name.replaceAll(".*_", ""))));
        int each = 0;
        for (String name : added) {
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
        assertTrue(prose.contains("the timer code of an effect is " + grouped(each)
                        + " bytes, and the block of the effects " + grouped(size(blocks, EFFECTS))
                        + " bytes."),
                "sizes.md reads other sizes of the timer code and the block of the effects");
        Cut copy = cut(false, false, "pieces.patch", "one-copy.patch");
        int today = floor(Binaries.core(), Pieces.carried(Binaries.CORE));
        int withPieces = floor(plain.core(), plain.table());
        int withCopy = floor(copy.core(), copy.table());
        String floors = "The floor of the plain core goes from %s bytes to %s with the pieces"
                + " and to %s with one copy of steps 6 and 7 as well.";
        assertTrue(prose.contains(String.format(Locale.ROOT, floors, grouped(today),
                        grouped(withPieces), grouped(withCopy))),
                "sizes.md reads other floors of the plain core");
        // the second copy is in the floor, so every player loses its bytes
        assertEquals(plain.core().length - copy.core().length, withPieces - withCopy);
        assertTrue(prose.contains("One copy of steps 6 and 7 is " + grouped(withPieces - withCopy)
                + " bytes fewer in every player."), "sizes.md reads another size of the copy");
        Map<String, Integer> carried = new TreeMap<>();
        for (Pieces.Piece piece : Pieces.carried(Binaries.CORE).pieces()) {
            carried.merge(piece.block(), piece.size(), Integer::sum);
        }
        int least = Integer.MAX_VALUE;
        int most = 0;
        for (int e = 0; e < 4; e++) {
            least = Math.min(least, size(carried, "effect" + e));
            most = Math.max(most, size(carried, "effect" + e));
        }
        assertTrue(prose.contains("Each effect's frame code and general handler are "
                        + grouped(least) + " to " + grouped(most) + " bytes of the plain core"),
                "sizes.md reads other sizes of an effect's block");
        assertTrue(prose.contains("`several`, the start of a source of several columns, is "
                        + grouped(size(carried, "several")) + " bytes."),
                "sizes.md reads another size of several");
        StringBuilder others = new StringBuilder("With the pieces the floor of the core with"
                + " the deferred start in goes from ");
        String[] names = {"the deferred start", "the lookup", "the lookup and the deferred start"};
        boolean[][] switches = {{true, false}, {false, true}, {true, true}};
        for (int i = 0; i < 3; i++) {
            Binaries.Binary binary = Binaries.binary(false, false, true, switches[i][0],
                    switches[i][1]);
            Cut patched = cut(switches[i][0], switches[i][1], "pieces.patch");
            if (i > 0) {
                others.append(i == 2 ? ", and of the core with " : ", of the core with ")
                        .append(names[i]).append(" in from ");
            }
            others.append(grouped(floor(Binaries.carried(binary.name()), Pieces.carried(binary))))
                    .append(" to ").append(grouped(floor(patched.core(), patched.table())));
        }
        others.append(".");
        assertTrue(prose.contains(others.toString()), "sizes.md does not read: " + others);
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
     *  ym/test that keeps the same blocks, today and with the pieces, and
     *  the sentences of the corpus are the sums of the rows. Where
     *  YM_CORPUS is the corpus's folder, the rows are the corpus's: the
     *  dumps of each set of blocks kept. */
    @Test
    void theDocumentReadsTheCorpus() throws Exception {
        String doc = Files.readString(DOC);
        String prose = prose();
        Cut pieces = cut(false, false, "pieces.patch");
        byte[] core = Binaries.core();
        Pieces table = Pieces.carried(Binaries.CORE);
        // the links of the dumps of ym/test, by the blocks each keeps
        Map<String, int[]> links = new TreeMap<>();
        try (Stream<Path> all = Files.list(DUMPS)) {
            for (Path dump : all.filter(p -> p.toString().endsWith(".ym")).sorted().toList()) {
                TuneFile tune = converted(dump);
                links.put(kept(tune), new int[] {
                        Link.link(core, table, Link.blocks(List.of(tune))).length,
                        linked(pieces.core(), pieces.table(), tune)});
            }
        }
        String section = doc.substring(doc.indexOf("\n## The corpus\n"),
                doc.indexOf("\n## The floor\n"));
        Matcher row = Pattern.compile("^\\| ([a-z0-9, ]+) \\| ([\\d,]+) \\| ([\\d,]+)"
                + " \\| ([\\d,]+) \\|$", Pattern.MULTILINE).matcher(section);
        Map<String, Integer> counts = new TreeMap<>();
        long dumps = 0;
        long today = 0;
        long now = 0;
        while (row.find()) {
            int[] link = links.get(row.group(1));
            assertTrue(link != null, "no dump of ym/test keeps " + row.group(1));
            int count = number(row.group(2));
            assertEquals(link == null ? 0 : link[0], number(row.group(3)),
                    "the bytes of " + row.group(1));
            assertEquals(link == null ? 0 : link[1], number(row.group(4)),
                    "the bytes with the pieces of " + row.group(1));
            counts.put(row.group(1), count);
            dumps += count;
            today += (long) count * number(row.group(3));
            now += (long) count * number(row.group(4));
        }
        assertTrue(counts.size() > 1, "sizes.md has no table of the corpus");
        int floor = floor(core, table);
        String share = "The player of each of the " + grouped(dumps)
                + " dumps links from the plain core. The floor, " + grouped(floor) + " bytes, is "
                + Math.round(100.0 * floor * dumps / today) + "% of the "
                + grouped(today) + " bytes of the " + grouped(dumps) + " players.";
        assertTrue(prose.contains(share), "sizes.md does not read: " + share);
        String saved = "With the pieces the " + grouped(dumps) + " players are "
                + grouped(now) + " bytes, " + Math.round(100.0 * (today - now) / today)
                + "% fewer.";
        assertTrue(prose.contains(saved), "sizes.md does not read: " + saved);
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
