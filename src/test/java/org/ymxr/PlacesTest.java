package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A tune file read against rule 3 of SPEC.md 6 ({@link Check#places}): a
 * start that leaves bit 5 of the control column at 0 leaves the place at
 * its number modulo the row count of the source it starts (1.8.4), and a
 * start that names a source past the file's is a line.
 */
final class PlacesTest {

    /**
     * The starts of {@link YmxsTest#outsideRule3a} with bit 5 at 0, on
     * another target, across row counts and across the wrap, and one on a
     * timer that has run none.
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
                BuiltTunes.RING, new Report()).file();
    }

    @Test
    void aStartWithBit5At0ReadsARowOfItsSource() {
        assertEquals(List.of(), Check.places(TuneFile.read(written())),
                "the place is a row of the source modulo its R");
    }

    @Test
    void aStartPastTheFilesSourcesIsALine() {
        Schema.Made made = Schema.of(YmxsTest.outsideRule3a());
        made.columns().column[Columns.EFFECT + 1][2] = (byte) (0x80 | 9);
        byte[] file = Tune.write(made.columns(), made.sources(), made.rate(), YmToYmxr.UNIT,
                BuiltTunes.RING, new Report()).file();
        assertEquals(List.of("2: effect 0 starts source 9, and the file has 3 sources"),
                Check.places(TuneFile.read(file)));
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
                Schema.of(YmxsTest.outsideRule3a()).sources(), 50, YmToYmxr.UNIT, BuiltTunes.RING,
                new Report()).file();
        assertEquals(List.of(), Check.places(TuneFile.read(ours)),
                "the writer's bit 5 puts every start inside rule 3");
    }

    /** The tool's verdict on such a file, in both trees ({@code ParityTest}). */
    static byte[] outsideRule3a() {
        return written();
    }
}
