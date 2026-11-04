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

    /** The cores the build cuts a table from: the eight without the
     *  deferred start and the lookup. */
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
    void theBuildCutsATableFromEachCoreWithoutTheDeferredStartAndTheLookup() {
        List<String> names = new ArrayList<>();
        for (Binaries.Binary binary : cut()) {
            names.add(binary.name());
        }
        assertEquals(List.of("YMXR_sndh.bin", "YMXR_sndh-perf.bin", "YMXR_sndh-lean.bin",
                "YMXR_sndh-perf-lean.bin", "YMXR_sndh-abs.bin", "YMXR_sndh-perf-abs.bin",
                "YMXR_sndh-lean-abs.bin", "YMXR_sndh-perf-lean-abs.bin"), names);
    }

    /** A table reads back as the text it was written as, its pieces in the
     *  core's order and apart, each of a block the linker names, and every
     *  field inside the core. */
    @Test
    void eachTableIsInTheCoresOrder() {
        for (Binaries.Binary binary : cut()) {
            byte[] core = Binaries.carried(binary.name());
            Pieces table = Pieces.carried(binary);
            assertEquals(table, Pieces.read(table.text()), binary.name());
            int after = Sndh.CORE_DESCRIPTOR;
            Set<String> blocks = new LinkedHashSet<>();
            for (Pieces.Piece piece : table.pieces()) {
                assertTrue(piece.from() >= after && piece.to() > piece.from()
                        && piece.to() <= core.length, binary.name() + ": " + piece);
                assertTrue(Link.BLOCKS.contains(piece.block()), binary.name() + ": " + piece);
                blocks.add(piece.block());
                after = piece.to();
            }
            assertEquals(Set.copyOf(Link.BLOCKS), blocks, binary.name());
            for (Pieces.Field field : table.fields()) {
                assertTrue(field.at() >= Sndh.CORE_DESCRIPTOR
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

    /** The blocks of a source follow its shape as init resolves it. */
    @Test
    void theBlocksOfTheKitsTunes() throws IOException {
        assertEquals(List.of(), List.copyOf(blocks("chambers.ymxr")));
        assertEquals(List.of("square"), List.copyOf(blocks("preempt.ymxr")));
        assertEquals(List.of("one"), List.copyOf(blocks("retune.ymxr")));
        assertEquals(List.of("square", "counted", "several"),
                List.copyOf(blocks("counted.ymxr")));
        assertEquals(List.of("envelope", "several"),
                List.copyOf(blocks("envelope-counted.ymxr")));
        assertEquals(List.of("two", "three", "several"), List.copyOf(blocks("voices.ymxr")));
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

    /** A set that needs the deferred start keeps the core as carried. */
    @Test
    void aSetThatDefersAStartKeepsTheCarriedCore() throws IOException {
        byte[] file = Files.readAllBytes(KIT.resolve("deferred.ymxr"));
        Sndh.Options linking = new Sndh.Options("deferred", null, null, false, false,
                Sndh.Ticks.CHOSEN, Sndh.Asked.CHOSEN, true);
        Sndh.Options carried = new Sndh.Options("deferred", null, null, false, false);
        assertArrayEquals(Sndh.of(List.of(file), carried), Sndh.of(List.of(file), linking));
    }

    /** A value that leaves its field is an error of the link. */
    @Test
    void aValuePastItsFieldIsAnError() {
        byte[] core = new byte[8];
        Tune.putWord(core, 4, 0x7FFF);
        Pieces table = new Pieces(List.of(new Pieces.Piece("square_1", 0, 2)),
                List.of(new Pieces.Field(4, 2, java.util.Map.of("square_1", -1))));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Link.link(core, table, Set.of()));
        String said = String.valueOf(wrong.getMessage());
        assertTrue(said.contains("the word field at 4"), said);
    }

    /** LINKER.md's table of the kit: each tune's blocks and the bytes of
     *  the plain core linked for it. */
    @Test
    void theDocumentReadsTheKitsLinkedCores() throws IOException {
        String doc = Files.readString(Path.of("doc/LINKER.md"));
        String prose = doc.replaceAll("\\s+", " ");
        byte[] core = Binaries.core();
        Pieces table = Pieces.carried(Binaries.CORE);
        assertTrue(prose.contains("the plain core of " + String.format("%,d", core.length)
                + " bytes"), "LINKER.md names another size of the plain core than "
                + core.length);
        int least = Link.link(core, table, Set.of()).length;
        String share = "A tune of general sources alone links " + String.format("%,d", least)
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
        for (String name : names) {
            TuneFile tune = TuneFile.read(Files.readAllBytes(KIT.resolve(name)));
            boolean defers = (tune.effects() & Columns.DEFERS) != 0;
            boolean maps = (tune.effects() & Columns.MAPS) != 0;
            String row;
            if (!Link.links(defers, maps)) {
                row = "| `" + name + "` | " + (defers && maps ? "the deferred start and the lookup"
                        : defers ? "the deferred start" : "the lookup")
                        + ": the core as carried | |";
            } else {
                Set<String> blocks = Link.blocks(List.of(tune));
                row = "| `" + name + "` | " + (blocks.isEmpty() ? "none"
                        : String.join(", ", blocks)) + " | "
                        + String.format("%,d", Link.link(core, table, blocks).length) + " |";
            }
            assertTrue(doc.contains(row + "\n"), "LINKER.md has no row " + row);
        }
    }
}
