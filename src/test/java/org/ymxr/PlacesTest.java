package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A tune file read against rule 3 of SPEC.md 6 ({@link Check#places}): each
 * start that leaves bit 5 of the control column at 0 outside rule 3(a) is a
 * line, and a file this repository writes has none.
 */
final class PlacesTest {

    /**
     * The starts of {@link YmxsTest#outsideRule3a} with bit 5 at 0, as a
     * writer other than this one can leave them: this writer sets the bit
     * on each start outside rule 3(a) (doc/ymxs.md), and the test clears it
     * again before the file is written.
     */
    private static byte[] written() {
        Schema.Made made = Schema.of(YmxsTest.outsideRule3a());
        byte[][] column = made.columns().column;
        int a = Columns.EFFECT + 2;
        int d = Columns.EFFECT + 4 + 2;
        for (int[] at : new int[][] {{d, 0}, {a, 1}, {a, 2}, {a, 3}}) {
            column[at[0]][at[1]] &= (byte) ~Columns.PLACE_RESET;
        }
        return Tune.write(made.columns(), made.sources(), made.rate(), YmToYmxr.UNIT,
                Tune.RING, new Report()).file();
    }

    @Test
    void aStartOutsideRule3aIsALine() {
        List<String> lines = Check.places(TuneFile.read(written()));
        List<String> heads = lines.stream()
                .map(line -> line.substring(0, line.indexOf(" starts"))).toList();
        assertEquals(List.of("0: effect 1", "1: effect 0", "2: effect 0", "3: effect 0"), heads,
                lines.toString());
        List<String> why = lines.stream()
                .map(line -> line.substring(line.indexOf("with bit 5 at 0, ") + 17)).toList();
        assertEquals(List.of("and no source has started on the effect",
                "and after the wrap the source last started ran on target 9",
                "and the source last started had 2 rows",
                "and the source last started ran on target 8"), why, lines.toString());
    }

    @Test
    void aTuneFileThisRepositoryWritesFollowsRule3() throws IOException {
        try (Stream<Path> at = Files.list(Path.of("ym/test"))) {
            for (Path dump : at.filter(one -> one.toString().endsWith(".ym")).sorted().toList()) {
                byte[] file = YmToYmxr.convert(Files.readAllBytes(dump), List.of(), new Report())
                        .written().file();
                assertEquals(List.of(), Check.places(TuneFile.read(file)), dump.toString());
            }
        }
        byte[] ours = Tune.write(Schema.of(YmxsTest.outsideRule3a()).columns(),
                Schema.of(YmxsTest.outsideRule3a()).sources(), 50, YmToYmxr.UNIT, Tune.RING,
                new Report()).file();
        assertEquals(List.of(), Check.places(TuneFile.read(ours)),
                "the writer's bit 5 puts every start inside rule 3");
    }

    /** The tool's verdict on such a file, in both trees ({@code ParityTest}). */
    static byte[] outsideRule3a() {
        return written();
    }
}
