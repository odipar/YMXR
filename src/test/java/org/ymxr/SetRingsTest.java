package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.dtx.Dtx3;
import org.junit.jupiter.api.Test;

/**
 * The rings of a set's tables, chosen for the set (tools.md 4.7): the rule
 * on options made up for it, and the tools' tune files packed through the
 * rings it chooses.
 */
final class SetRingsTest {

    private static SortedMap<Integer, int[]> option(int... ringBytesState) {
        SortedMap<Integer, int[]> one = new TreeMap<>();
        for (int i = 0; i < ringBytesState.length; i += 3) {
            one.put(ringBytesState[i], new int[] {ringBytesState[i + 1], ringBytesState[i + 2]});
        }
        return one;
    }

    /** A tune whose state block is within the largest at a wider ring
     *  packs through it: the first tune comes to 1,500 bytes through 60 and
     *  1,700 through 120 alone, and the set of both comes to 7,800 with it
     *  at 120 against 8,000 with each tune at the ring of the tune alone. */
    @Test
    void aTuneTakesAWiderRingWhereTheSetsStateBlockHasRoomForIt() {
        List<SortedMap<Integer, int[]>> options = List.of(
                option(60, 1000, 500, 120, 800, 900),
                option(60, 5000, 2000, 120, 4000, 3000));
        assertArrayEquals(new int[] {120, 60}, SetRings.choose(options),
                "the B of 2,000 and the B of 3,000 both come to 7,800, and the smaller is"
                        + " kept");
    }

    @Test
    void ofTwoRingsOfTheSameBytesTheSmallerIsTaken() {
        List<SortedMap<Integer, int[]>> options = List.of(
                option(60, 700, 400, 120, 700, 600, 240, 900, 1000),
                option(60, 3000, 1500));
        assertArrayEquals(new int[] {60, 60}, SetRings.choose(options),
                "the first tune's 60 and 120 are both 700 bytes under the B of 1,500");
    }

    /** A set of one tune comes to the ring a tune alone is written at: the
     *  least of its table and state block, the smaller of two that come to
     *  the same. */
    @Test
    void aSetOfOneComesToTheRingOfTheTuneAlone() {
        SortedMap<Integer, int[]> one = option(60, 4000, 1000, 120, 3500, 1500,
                240, 2600, 2400, 480, 2300, 4800);
        int least = Integer.MAX_VALUE;
        int ring = 0;
        for (var at : one.entrySet()) {
            if (at.getValue()[0] + at.getValue()[1] < least) {
                least = at.getValue()[0] + at.getValue()[1];
                ring = at.getKey();
            }
        }
        assertEquals(ring, SetRings.choose(List.of(one))[0]);
    }

    @Test
    void aTuneWithNoRingIsAnError() {
        assertEquals("tune 2 has no ring to choose", assertThrows(
                IllegalArgumentException.class,
                () -> SetRings.choose(List.of(option(60, 1, 1), option()))).getMessage());
    }

    private static Tune.Written converted(String dump) throws IOException {
        return YmToYmxr.convert(Files.readAllBytes(Path.of("ym/test", dump)), List.of(),
                new Report()).written();
    }

    private static final List<String> SET = List.of("DBA 2.ym", "DBA 5.ym",
            "Synergy Credits.ym");

    /** The bytes of a set: its tables, and the largest state block among
     *  them. */
    private static long footprint(List<Tune.Written> set) {
        long tables = 0;
        int state = 0;
        for (Tune.Written tune : set) {
            tables += tune.table().length;
            state = Math.max(state, Tune.state(tune.table()));
        }
        return tables + state;
    }

    /** The tunes of the set packed again: each the same table read back,
     *  the set's tables and state block no more than the tunes' at their
     *  rings alone, and less on these three. */
    @Test
    void aSetsRingsPackItsTablesInNoMoreBytes() throws IOException {
        List<Tune.Written> alone = new ArrayList<>();
        for (String dump : SET) {
            alone.add(converted(dump));
        }
        List<Tune.Written> set = SetRings.forSet(alone, Tune.packers(), new Report());
        for (int i = 0; i < alone.size(); i++) {
            assertEquals(TuneFile.read(alone.get(i).file()).table(),
                    TuneFile.read(set.get(i).file()).table(),
                    SET.get(i) + ": the table reads back as it did");
            assertEquals(TuneFile.read(alone.get(i).file()).sources(),
                    TuneFile.read(set.get(i).file()).sources(),
                    SET.get(i) + ": and its sources");
            assertEquals(Tune.name(alone.get(i).file()), Tune.name(set.get(i).file()),
                    SET.get(i) + ": and its name");
        }
        assertTrue(footprint(set) < footprint(alone), () -> "the set comes to "
                + footprint(set) + " bytes, and its tunes at their rings alone to "
                + footprint(alone));
    }

    /** ymxr-multi's route, through the tune files, and the route of a tool
     *  of tools.md 7, through the tunes as they are written, come to one set
     *  of files. */
    @Test
    void theTwoRoutesToASetWriteTheSameFiles() throws IOException {
        List<Tune.Written> alone = new ArrayList<>();
        List<byte[]> files = new ArrayList<>();
        for (String dump : SET) {
            alone.add(converted(dump));
            files.add(alone.get(alone.size() - 1).file());
        }
        List<Tune.Written> written = SetRings.forSet(alone, Tune.packers(), new Report());
        List<Tune.Written> read = SetRings.ofFiles(files, Tune.packers(), Tune.CHOOSE,
                new Report());
        for (int i = 0; i < SET.size(); i++) {
            assertArrayEquals(written.get(i).file(), read.get(i).file(), SET.get(i));
        }
    }

    @Test
    void aSetOfOneTuneIsTheTuneFileAsItWas() throws IOException {
        byte[] file = converted("Turrican - world 4-3.ym").file();
        assertArrayEquals(file, SetRings.ofFiles(List.of(file), Tune.packers(), Tune.CHOOSE,
                new Report()).get(0).file());
    }

    @Test
    void aRingNamedPacksEveryTableThroughIt() throws IOException {
        List<byte[]> files = new ArrayList<>();
        for (String dump : SET) {
            files.add(converted(dump).file());
        }
        List<Tune.Written> set = SetRings.ofFiles(files, Tune.packers(), 480, new Report());
        for (int i = 0; i < SET.size(); i++) {
            assertEquals(480, Dtx3.payload(set.get(i).table()).ring(), SET.get(i));
            assertEquals(TuneFile.read(files.get(i)).table(),
                    TuneFile.read(set.get(i).file()).table(), SET.get(i) + " reads back");
        }
    }

    /** A table put in place of another of another length: the sources
     *  after it move, and every other part of the file is as it was. */
    @Test
    void aTableInPlaceOfAnotherMovesTheSourcesAfterIt() throws IOException {
        Tune.Written tune = converted("DBA 2.ym");
        assertArrayEquals(tune.file(), Tune.withTable(tune.file(), tune.table()),
                "the same table leaves the file as it is");
        List<Tune.Packed> plain = new ArrayList<>(tune.plain().values());
        byte[] wider = plain.get(plain.size() - 1).table();
        assertTrue(wider.length != tune.table().length, "the two tables differ in length");
        byte[] moved = Tune.withTable(tune.file(), wider);
        TuneFile before = TuneFile.read(tune.file());
        TuneFile after = TuneFile.read(moved);
        assertEquals(before.sources(), after.sources(), "the sources read back as they were");
        assertEquals(before.counted(), after.counted(), "and which of them are counted");
        assertEquals(before.table(), after.table(), "the table reads back as the same rows");
        assertEquals(Tune.name(tune.file()), Tune.name(moved), "the name is as it was");
        assertEquals(Tune.getLong(tune.file(), Tune.TABLE_AT), Tune.getLong(moved, Tune.TABLE_AT),
                "the table is where it was");
    }
}
