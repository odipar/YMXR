package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.ymxs.Chip;
import org.ymxs.Tunes;
import org.ymxs.YMXS.Effect;
import org.ymxs.YMXS.Retune;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Start;
import org.ymxs.YMXS.Timer;
import org.ymxs.YMXS.Tune;

/**
 * The YMX conversion against the file it reads (doc/ymxs.md): every tune
 * under {@code ymx/test} converted, and the structure compared with the
 * header and the script bytes ymx-dump printed.
 *
 * <p>Two claims are under test. A tune that plays once through converts to
 * a tune that plays once: bit 0 of the header's flags names that, and the
 * loop frame is 0 both where a tune plays once and where it starts over
 * from its first frame (YMX, SPEC.md 1.2), so the flag is the one place
 * the two part. And an opcode that programs a timer converts to an effect
 * at the count and the prescaler index its action byte names, a count of 0
 * among them: the MFP counts 256 at a count of 0, and the count column
 * reaches it with bit 4 of the control column beside it (SPEC.md 1.9).
 */
final class YmxTest {

    private static final int STREAM_M = 14;
    private static final int STREAM_A0 = 17;

    /** The first opcode that programs or retunes a timer, whose low bits
     *  are the prescaler index and whose P byte is the count (YMX,
     *  SPEC.md 2.4). */
    private static final int PROGRAMS = 3;

    /** HOLD, whose low bits are flags: flag 1 reloads the count from P,
     *  and the prescaler index stands (YMX, SPEC.md 3). */
    private static final int RELOADS = 1;

    private static List<Path> files() throws IOException {
        try (Stream<Path> at = Files.list(Path.of("ymx/test"))) {
            return at.filter(one -> one.toString().endsWith(".ymx")).sorted().toList();
        }
    }

    /** A .ymx read out, which needs YMX's ymx-dump: the Java tools run it,
     *  and the Go tools decode the file themselves. */
    private static YmxToYmxr.Dumped read(Path file) throws IOException {
        YmxToYmxr.Dumped read = YmxToYmxr.dumped(file);
        return new YmxToYmxr.Dumped(YmxToYmxr.frames(read), read.rate(), read.loopFrame(),
                read.flags(), read.streams(), read.samples(), read.loops());
    }

    private static Tune converted(YmxToYmxr.Dumped read) {
        return Ymx.read(read, YmxToYmxr.song(read, ""), YmxToYmxr.repeat(read),
                new Report());
    }

    private static void dumpIsThere() {
        String named = System.getenv("YMX_DUMP");
        Assumptions.assumeTrue(named != null && Files.isExecutable(Path.of(named)),
                "YMX_DUMP names no ymx-dump");
    }

    @Test
    void aTuneThatPlaysOnceConvertsToATuneThatPlaysOnce() throws IOException {
        dumpIsThere();
        List<String> once = new ArrayList<>();
        List<String> over = new ArrayList<>();
        for (Path file : files()) {
            YmxToYmxr.Dumped read = read(file);
            Tune tune = converted(read);
            int rows = Tunes.rows(tune).size();
            OptionalInt repeat = tune.table().repeat();
            if (YmxToYmxr.startsOver(read)) {
                over.add(file.getFileName().toString());
                assertEquals(OptionalInt.of(Math.min(read.loopFrame(), rows)), repeat,
                        file + " starts over at its loop frame");
            } else {
                once.add(file.getFileName().toString());
                assertEquals(OptionalInt.empty(), repeat,
                        file + " plays once, so it repeats to no row");
            }
        }
        assertTrue(!once.isEmpty(), "a tune that plays once is read: " + once);
        assertTrue(!over.isEmpty(), "a tune that starts over is read: " + over);
    }

    @Test
    void everyProgrammingOpcodeConvertsToTheCountAndTheSelectItNames() throws IOException {
        dumpIsThere();
        int checked = 0;
        int zero = 0;
        for (Path file : files()) {
            YmxToYmxr.Dumped read = read(file);
            List<Row> rows = Tunes.rows(converted(read));
            for (int f = 0; f < read.frames(); f++) {
                int master = read.streams()[STREAM_M][f] & 0xFF;
                for (int c = 0; c < 4; c++) {
                    if ((master & (1 << c)) == 0) {
                        continue;
                    }
                    int action = read.streams()[STREAM_A0 + 2 * c][f] & 0xFF;
                    int count = read.streams()[STREAM_A0 + 2 * c + 1][f] & 0xFF;
                    int opcode = action >> 5;
                    boolean reloads = opcode == RELOADS && (action & 1) != 0;
                    if (opcode < PROGRAMS && !reloads) {
                        continue;
                    }
                    Effect effect = Tunes.effects(rows.get(f)).get(Schema.timer(c));
                    if (effect == null) {
                        continue;       // a start the conversion left behind, with a note
                    }
                    String at = file + " frame " + f + " channel " + c;
                    assertEquals(count, count(effect, at), at + ": the count");
                    if (!reloads) {
                        assertEquals(Columns.PRESCALER[action & 7], divides(effect, at),
                                at + ": the prescaler");
                    }
                    checked++;
                    if (count == 0) {
                        zero++;
                    }
                }
            }
        }
        // 7,477 across the four files, and one of them names a count of 0.
        assertTrue(checked > 7000, "only " + checked + " opcodes set a timer's rate");
        assertTrue(zero > 0, "no opcode names a count of 0, the MFP's " + Chip.ZERO_COUNTS);
    }

    private static int count(Effect effect, String at) {
        if (effect instanceof Start start) {
            return start.count();
        }
        if (effect instanceof Retune retune) {
            return retune.count();
        }
        throw new IllegalStateException(at + " converts to " + effect);
    }

    private static int divides(Effect effect, String at) {
        if (effect instanceof Start start) {
            return Chip.divides(start.prescaler());
        }
        if (effect instanceof Retune retune) {
            return Chip.divides(retune.prescaler());
        }
        throw new IllegalStateException(at + " converts to " + effect);
    }

    /** The one row of the corpus whose action names a count of 0, which
     *  the conversion read as the count already running before 0.3.2:
     *  DitherDance frame 2894, where a HOLD's flag 1 reloads Timer B. */
    @Test
    void theOneRowNamingACountOf0ReachesTheStructure() throws IOException {
        dumpIsThere();
        Path file = Path.of("ymx/test/DitherDance.ymx");
        List<Row> rows = Tunes.rows(converted(read(file)));
        assertEquals(0, count(timerB(rows, 2894), "DitherDance frame 2894"),
                "the MFP counts " + Chip.ZERO_COUNTS + " at a count of 0");
        assertEquals(239, count(timerB(rows, 2893), "DitherDance frame 2893"),
                "the count the row before it runs, which 0 replaced");
    }

    private static Effect timerB(List<Row> rows, int at) {
        Effect effect = Tunes.effects(rows.get(at)).get(Timer.B);
        if (effect == null) {
            throw new IllegalStateException("row " + at + " sets no effect on Timer B");
        }
        return effect;
    }
}
