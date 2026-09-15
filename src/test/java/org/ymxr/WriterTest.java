package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Two faults a writer reported by writing the wrong bytes.
 *
 * <p>The frame rate is a word (BINARIES.md 1, SPEC.md 3.3) and a YMXS rate
 * reaches 2,147,483,647 (YMXS, SPEC.md 1.3), so a rate above 65,535 wrote
 * its low sixteen bits: a tune at 70,000 Hz became one at 4,464 Hz, and
 * the tool reported 0.
 *
 * <p>Every bound tune of a set begins on an even address (BINARIES.md
 * 5.1), which {@code Sndh.combine} evened for every subtune after the
 * first; the first stood at the byte after the last image, odd where that
 * image's length is odd.
 */
final class WriterTest {

    /** A table of one row, the columns a tune of one frame needs. */
    private static Columns oneRow() {
        byte[][] column = new byte[Columns.C][1];
        column[0][0] = (byte) 0x80;
        return new Columns(column, 0, 0);
    }

    private static Tune.Written at(int rate) {
        return Tune.write(oneRow(), new Sources(List.of()), rate, 1, Tune.RING,
                new Report());
    }

    @Test
    void aFrameRatePastTheWordIsAFaultOfTheWriter() {
        String said = String.valueOf(assertThrows(IllegalArgumentException.class,
                () -> at(Tune.MOST_RATE + 1)).getMessage());
        assertEquals("a frame rate of 65536, and the frame rate is a word, 1 to 65535",
                said, "the writer names the rate and the bound");
        assertThrows(IllegalArgumentException.class, () -> at(70000),
                "a YMXS rate a word cannot carry is a fault rather than its low bits");
        assertThrows(IllegalArgumentException.class, () -> at(0),
                "a player is called at least once a second");
    }

    @Test
    void theRateTheWordCarriesIsWritten() {
        for (int rate : new int[] {1, 50, 60, Tune.MOST_RATE}) {
            byte[] file = at(rate).file();
            assertEquals(rate, Tune.getWord(file, Tune.FRAME_RATE_AT),
                    "the file reads the rate it was written at");
        }
    }

    @Test
    void everySubtuneOfASetBeginsOnAnEvenAddress() {
        // The set as the binder makes it, with its image replaced by one of
        // an odd length, so that the byte after the last image is odd:
        // where subtune 1 stood before this fault was fixed.
        Bound.Set bound = Bound.of(List.of(at(50).file(), at(60).file()));
        assertEquals(1, bound.images().size(), "the two tunes share one image");
        Bound.Set odd = new Bound.Set(List.of(new byte[15]), bound.shapes(),
                bound.tunes(), bound.image(), bound.table());
        byte[] file = Sndh.combine(new byte[64], odd, new byte[] {0}, 16);
        int found = 0;
        for (int at = 0; at + Bound.MAGIC.length <= file.length; at++) {
            if (file[at] == 'Y' && file[at + 1] == 'M' && file[at + 2] == 'X'
                    && file[at + 3] == 'B') {
                found++;
                assertEquals(0, at % 2, "a bound tune begins at " + at
                        + ", an odd address (BINARIES.md 5.1)");
            }
        }
        assertEquals(2, found, "the file has the set's two bound tunes in it");
    }
}
