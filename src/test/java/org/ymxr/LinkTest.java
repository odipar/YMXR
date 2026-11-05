package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The linker (doc/LINKER.md): the tables the build cuts, the link, and
 *  the figures the document reads. */
final class LinkTest {

    private static final Path KIT = Path.of("doc/conformance/tunes");

    /** The cores the build cuts a table from: every SNDH core. */
    private static List<Binaries.Binary> cut() {
        List<Binaries.Binary> cut = new ArrayList<>();
        for (Binaries.Binary binary : Binaries.all()) {
            if (Pieces.cuts(binary)) {
                cut.add(binary);
            }
        }
        return cut;
    }

    @Test
    void theBuildCutsATableFromEachCore() {
        List<String> names = new ArrayList<>();
        for (Binaries.Binary binary : cut()) {
            names.add(binary.name());
        }
        List<String> all = new ArrayList<>();
        for (String map : List.of("", "-map")) {
            for (String defer : List.of("", "-defer")) {
                for (String core : List.of("", "-perf", "-lean", "-perf-lean", "-abs",
                        "-perf-abs", "-lean-abs", "-perf-lean-abs")) {
                    all.add("YMXR_sndh" + core + defer + map + ".bin");
                }
            }
        }
        assertEquals(all, names);
    }

    /** A table reads back as the text it was written as, its pieces in the
     *  core's order and apart, each of a block the linker names and of one
     *  byte or more, a gap aside, and every field inside the code past the
     *  descriptor, the offset of the first handler aside. */
    @Test
    void eachTableIsInTheCoresOrder() {
        for (Binaries.Binary binary : cut()) {
            byte[] core = Binaries.carried(binary.name());
            Pieces table = Pieces.carried(binary);
            assertEquals(table, Pieces.read(table.text()), binary.name());
            Set<String> gaps = new LinkedHashSet<>();
            for (Pieces.Align align : table.aligns()) {
                gaps.add(align.gap());
            }
            int after = Sndh.CORE_DESCRIPTOR;
            Set<String> blocks = new LinkedHashSet<>();
            for (Pieces.Piece piece : table.pieces()) {
                assertTrue(piece.from() >= after && piece.to() <= core.length
                        && (piece.to() > piece.from() || gaps.contains(piece.name())),
                        binary.name() + ": " + piece);
                assertTrue(Link.BLOCKS.contains(piece.block()), binary.name() + ": " + piece);
                blocks.add(piece.block());
                after = piece.to();
            }
            // a lean core leaves the level drop out, and the loop over the
            // one-row handlers that writes it; a core without the lookup has
            // no lookup instances, and the gap before the second bank is in
            // a core with the deferred start and the lookup
            Set<String> all = new LinkedHashSet<>(Link.BLOCKS);
            if (binary.defines().contains("-dYMXR_NEST=0")) {
                all.remove("one");
            }
            boolean map = binary.name().contains("-map");
            if (!map) {
                for (String kind : Link.LOOKUPS) {
                    all.remove(kind);
                    for (int i = 0; i < 4; i++) {
                        all.remove(kind + i);
                    }
                }
            }
            if (!map || !binary.name().contains("-defer")) {
                all.remove("align");
            }
            assertEquals(all, blocks, binary.name());
            // the descriptor's offset of the first handler, in a core with
            // the lookup, is the one field before the code
            for (Pieces.Field field : table.fields()) {
                assertTrue((field.at() >= Sndh.CORE_DESCRIPTOR
                        || field.at() == Sndh.CORE_HANDLERS_AT && map)
                        && field.at() + field.width() <= core.length, binary.name() + ": "
                        + field);
                assertEquals(2, field.width(), binary.name() + ": " + field);
            }
        }
    }

    /** The tables under go/binaries/data are the ones the classes carry,
     *  and every table there is one of them. */
    @Test
    void theGoTreeCarriesTheTablesTheBuildCut() throws IOException {
        Path data = Path.of("go/binaries/data");
        Set<String> written = new LinkedHashSet<>();
        for (Binaries.Binary binary : cut()) {
            written.add(Pieces.name(binary));
            assertEquals(Pieces.carried(binary).text(),
                    Files.readString(data.resolve(Pieces.name(binary))),
                    Pieces.name(binary) + " under go/binaries/data is not the one the build"
                            + " cut");
        }
        Set<String> there = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(data)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(Pieces.SUFFIX)).sorted()
                    .forEach(there::add);
        }
        assertEquals(new java.util.TreeSet<>(written), there);
    }

    /** A link that keeps every block is the core byte for byte, and one
     *  that keeps none is the core less every piece. */
    @Test
    void aLinkOfEveryBlockIsTheCore() {
        for (Binaries.Binary binary : cut()) {
            byte[] core = Binaries.carried(binary.name());
            Pieces table = Pieces.carried(binary);
            assertArrayEquals(core, Link.link(core, table, Set.copyOf(Link.BLOCKS)),
                    binary.name());
            int pieces = 0;
            for (Pieces.Piece piece : table.pieces()) {
                pieces += piece.size();
            }
            assertEquals(core.length - pieces, Link.link(core, table, Set.of()).length,
                    binary.name());
        }
    }

    /** The blocks of a tune: each effect it runs, and the instance of each
     *  kind on the effect whose rows start a source of the kind, its shape
     *  as init resolves it. */
    @Test
    void theBlocksOfTheKitsTunes() throws IOException {
        assertEquals(List.of("align"), List.copyOf(blocks("chambers.ymxr")));
        assertEquals(List.of("square", "square0", "effect0", "effect1", "align"),
                List.copyOf(blocks("preempt.ymxr")));
        assertEquals(List.of("one", "one0", "effect0", "align"),
                List.copyOf(blocks("retune.ymxr")));
        assertEquals(List.of("effect1", "align"), List.copyOf(blocks("turrican.ymxr")));
        assertEquals(List.of("square", "square1", "counted", "counted0", "counted1", "counted2",
                "counted3", "several", "effect0", "effect1", "effect2", "effect3", "align"),
                List.copyOf(blocks("counted.ymxr")));
        assertEquals(List.of("envelope", "envelope0", "several", "effect0", "align"),
                List.copyOf(blocks("envelope-counted.ymxr")));
        assertEquals(List.of("two", "two1", "two3", "three", "three0", "three2", "several",
                "effect0", "effect1", "effect2", "effect3", "align"),
                List.copyOf(blocks("voices.ymxr")));
    }

    /** The lookup instances of a tune: the kind of each source an effect
     *  starts through a transformation, on the effect. */
    @Test
    void theLookupsOfTheMappedTune() throws IOException {
        Set<String> blocks = blocks("mapped.ymxr");
        for (String block : blocks) {
            if (Link.LOOKUPS.contains(block.replaceAll("[0-9]$", ""))) {
                assertTrue(blocks.contains(block.substring(0, 2)), block);
            }
        }
        assertTrue(blocks.stream().anyMatch(block -> block.matches("l[a-z][0-3]")),
                "mapped.ymxr runs no lookup instance: " + blocks);
    }

    private static Set<String> blocks(String name) throws IOException {
        return Link.blocks(List.of(TuneFile.read(Files.readAllBytes(KIT.resolve(name)))));
    }

    /** ymxr-sndh -link puts the linked core under the tunes: the file's
     *  core is the link of the carried one, its patched longs aside, and
     *  its subtune table follows it. */
    @Test
    void ymxrSndhLinksTheCoreForTheSet() throws IOException {
        byte[] file = Files.readAllBytes(KIT.resolve("voices.ymxr"));
        Sndh.Options options = new Sndh.Options("voices", null, null, false, false,
                Sndh.Ticks.CHOSEN, Sndh.Asked.CHOSEN, true);
        byte[] sndh = Sndh.of(List.of(file), options);
        int at = Tune.getWord(sndh, 2) + 2;
        byte[] linked = Link.link(Binaries.core(), Pieces.carried(Binaries.CORE),
                Link.blocks(List.of(TuneFile.read(file))));
        assertTrue(linked.length < Binaries.core().length);
        assertArrayEquals(Arrays.copyOf(linked, 28), Arrays.copyOfRange(sndh, at, at + 28));
        assertArrayEquals(Arrays.copyOfRange(linked, 36, linked.length),
                Arrays.copyOfRange(sndh, at + 36, at + linked.length));
        assertEquals(linked.length, Tune.getLong(sndh, at + Sndh.CORE_TABLE_AT));
    }

    /** A set that maps a value links the core with the lookup in: the
     *  file's core is that core linked, and shorter, its three patched
     *  longs aside. */
    @Test
    void aSetThatMapsAValueLinksTheCoreWithTheLookup() throws IOException {
        byte[] file = Files.readAllBytes(KIT.resolve("mapped.ymxr"));
        TuneFile tune = TuneFile.read(file);
        Sndh.Options options = new Sndh.Options("mapped", null, null, false, false,
                Sndh.Ticks.CHOSEN, Sndh.Asked.CHOSEN, true);
        byte[] sndh = Sndh.of(List.of(file), options);
        int at = Tune.getWord(sndh, 2) + 2;
        boolean defers = (tune.effects() & Columns.DEFERS) != 0;
        Binaries.Binary binary = Binaries.binary(false, false, true, defers, true);
        byte[] core = Binaries.carried(binary.name());
        byte[] linked = Link.link(core, Pieces.carried(binary), Link.blocks(List.of(tune)));
        assertTrue(linked.length < core.length);
        assertEquals(Sndh.CORE_MAP | Sndh.CORE_PCREL | (defers ? Sndh.CORE_DEFER : 0),
                Tune.getWord(sndh, at + 22));
        assertArrayEquals(Arrays.copyOf(linked, 28), Arrays.copyOfRange(sndh, at, at + 28));
        assertArrayEquals(Arrays.copyOfRange(linked, 40, linked.length),
                Arrays.copyOfRange(sndh, at + 40, at + linked.length));
    }

    /** A set that defers a start links the core with the deferred start in:
     *  the file's core is that core linked, and shorter. */
    @Test
    void aSetThatDefersAStartLinksTheCoreWithTheDeferredStart() throws IOException {
        byte[] file = Files.readAllBytes(KIT.resolve("deferred.ymxr"));
        Sndh.Options options = new Sndh.Options("deferred", null, null, false, false,
                Sndh.Ticks.CHOSEN, Sndh.Asked.CHOSEN, true);
        byte[] sndh = Sndh.of(List.of(file), options);
        int at = Tune.getWord(sndh, 2) + 2;
        Binaries.Binary binary = Binaries.binary(false, false, true, true, false);
        byte[] core = Binaries.carried(binary.name());
        byte[] linked = Link.link(core, Pieces.carried(binary),
                Link.blocks(List.of(TuneFile.read(file))));
        assertTrue(linked.length < core.length);
        assertEquals(Sndh.CORE_DEFER | Sndh.CORE_PCREL, Tune.getWord(sndh, at + 22));
        assertArrayEquals(Arrays.copyOfRange(linked, 36, linked.length),
                Arrays.copyOfRange(sndh, at + 36, at + linked.length));
    }

    /** A value that leaves its field is an error of the link. */
    @Test
    void aValuePastItsFieldIsAnError() {
        byte[] core = new byte[8];
        Tune.putWord(core, 4, 0x7FFF);
        Pieces table = new Pieces(List.of(new Pieces.Piece("square_1", 0, 2)), List.of(),
                List.of(), List.of(),
                List.of(new Pieces.Field(4, 2, java.util.Map.of("square_1", -1))));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Link.link(core, table, Set.of()));
        String said = String.valueOf(wrong.getMessage());
        assertTrue(said.contains("the word field at 4"), said);
    }

    /** LINKER.md's tables of the kinds: each handler's bytes in the plain
     *  core and its start's in each effect's frame code, and each lookup
     *  instance's and its loop of init's in the core with the lookup in. */
    @Test
    void theDocumentReadsTheSizesOfTheKinds() throws IOException {
        String doc = Files.readString(Path.of("doc/LINKER.md"));
        java.util.Map<String, Integer> plain = sizes(Pieces.carried(Binaries.CORE));
        for (String kind : Link.KINDS) {
            // a square's and a source of one row's start is a piece of the
            // handler's block in each effect's frame code
            Integer start = plain.get(kind + "0_3");
            String row = "| `" + kind + "` | " + plain.get(kind + "0_1") + " | "
                    + (start == null ? "in `several`" : start) + " | ";
            assertTrue(doc.contains("\n" + row), "LINKER.md has no row starting " + row);
        }
        java.util.Map<String, Integer> mapped =
                sizes(Pieces.carried(Binaries.binary(false, false, true, false, true)));
        for (String kind : Link.LOOKUPS) {
            String row = "| `" + kind + "` | " + mapped.get(kind + "0_2") + " | "
                    + mapped.get(kind + "_1") + " | ";
            assertTrue(doc.contains("\n" + row), "LINKER.md has no row starting " + row);
        }
    }

    private static java.util.Map<String, Integer> sizes(Pieces table) {
        java.util.Map<String, Integer> out = new java.util.HashMap<>();
        for (Pieces.Piece piece : table.pieces()) {
            out.put(piece.name(), piece.size());
        }
        return out;
    }

    /** LINKER.md's table of the kit: each tune's core, the blocks of the
     *  core it keeps, and the bytes of that core linked for it. */
    @Test
    void theDocumentReadsTheKitsLinkedCores() throws IOException {
        String doc = Files.readString(Path.of("doc/LINKER.md"));
        String prose = doc.replaceAll("\\s+", " ");
        byte[] core = Binaries.core();
        Pieces table = Pieces.carried(Binaries.CORE);
        assertTrue(prose.contains("the plain core, of " + String.format("%,d", core.length)
                + " bytes"), "LINKER.md names another size of the plain core than "
                + core.length);
        int least = Link.link(core, table, Set.of()).length;
        String share = "A tune of general sources alone, with no effect, links "
                + String.format("%,d", least)
                + " bytes, " + Math.round(100.0 * least / core.length)
                + "% of the core: the blocks are " + String.format("%,d", core.length - least)
                + " bytes of it.";
        assertTrue(prose.contains(share), "LINKER.md does not read: " + share);
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(KIT)) {
            files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".ymxr") && !name.equals("wrong-version.ymxr"))
                    .sorted().forEach(names::add);
        }
        Binaries.Binary deferring = Binaries.binary(false, false, true, true, false);
        byte[] deferred = Binaries.carried(deferring.name());
        assertTrue(prose.contains("the core with the deferred start in, of "
                + String.format("%,d", deferred.length) + " bytes"),
                "LINKER.md names another size of the core with the deferred start than "
                + deferred.length);
        for (String name : names) {
            TuneFile tune = TuneFile.read(Files.readAllBytes(KIT.resolve(name)));
            boolean defers = (tune.effects() & Columns.DEFERS) != 0;
            boolean maps = (tune.effects() & Columns.MAPS) != 0;
            assertTrue(Link.links(defers, maps), name);
            Binaries.Binary binary = Binaries.binary(false, false, true, defers, maps);
            byte[] carried = Binaries.carried(binary.name());
            Pieces pieces = Pieces.carried(binary);
            if (maps) {
                assertTrue(prose.contains("the core with the lookup" + (defers
                        ? " and the deferred start" : "") + " in, of "
                        + String.format("%,d", carried.length) + " bytes"),
                        "LINKER.md names another size of " + binary.name() + " than "
                        + carried.length);
            }
            Set<String> has = new LinkedHashSet<>();
            for (Pieces.Piece piece : pieces.pieces()) {
                has.add(piece.block());
            }
            List<String> kept = new ArrayList<>(Link.blocks(List.of(tune)));
            kept.retainAll(has);
            byte[] linked = Link.link(carried, pieces, Link.blocks(List.of(tune)));
            String which = maps ? (defers ? "the lookup and the deferred start" : "the lookup")
                    : defers ? "the deferred start" : "plain";
            String row = "| `" + name + "` | " + which + " | "
                    + (kept.isEmpty() ? "none" : String.join(", ", kept)) + " | "
                    + String.format("%,d", linked.length) + " |";
            assertTrue(doc.contains(row + "\n"), "LINKER.md has no row " + row);
            if (maps) {
                String said = lookups(name, carried, pieces, Link.blocks(List.of(tune)),
                        linked.length);
                assertTrue(prose.contains(said), "LINKER.md does not read: " + said);
            }
        }
    }

    /** The sentence of LINKER.md on a tune that maps a value: the lookup
     *  instances and kinds it keeps, the bytes of the lookup blocks it
     *  leaves out, and the bytes the gaps it keeps grow by. */
    private static String lookups(String name, byte[] core, Pieces table, Set<String> blocks,
                                  int linked) {
        Set<String> instances = new LinkedHashSet<>();
        Set<String> kinds = new LinkedHashSet<>();
        int out = 0;
        int left = 0;
        for (Pieces.Piece piece : table.pieces()) {
            String block = piece.block();
            boolean kind = Link.LOOKUPS.contains(block);
            boolean instance = !kind && Link.LOOKUPS.contains(block.replaceAll("[0-3]$", ""));
            if (!blocks.contains(block)) {
                left += piece.size();
                if (kind || instance) {
                    out += piece.size();
                }
            }
            if (kind || instance) {
                (kind ? kinds : instances).add(block);
            }
        }
        int keptInstances = 0;
        int keptKinds = 0;
        for (String block : instances) {
            keptInstances += blocks.contains(block) ? 1 : 0;
        }
        for (String block : kinds) {
            keptKinds += blocks.contains(block) ? 1 : 0;
        }
        return "`" + name + "` keeps " + keptInstances + " lookup instances of " + keptKinds
                + " kinds and links " + String.format("%,d", linked) + " bytes of "
                + String.format("%,d", core.length) + ": the "
                + (instances.size() - keptInstances) + " instances and the "
                + (kinds.size() - keptKinds) + " loops of init it leaves out are "
                + String.format("%,d", out) + " bytes, and the gaps it keeps are "
                + String.format("%,d", Math.abs(linked - (core.length - left))) + " bytes "
                + (linked >= core.length - left ? "longer" : "shorter") + " than in the core.";
    }
}
