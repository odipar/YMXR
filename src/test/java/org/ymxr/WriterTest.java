package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Two faults a writer reported by writing the wrong bytes, and the bound
 * tune of version 10.
 *
 * <p>The frame rate is a word (BINARIES.md 1, SPEC.md 3.3) and a YMXS rate
 * reaches 2,147,483,647 (YMXS, SPEC.md 1.3), so a rate above 65,535 wrote
 * its low sixteen bits: a tune at 70,000 Hz became one at 4,464 Hz, and
 * the tool reported 0.
 *
 * <p>Every bound tune of a set begins on an even address (BINARIES.md
 * 5.1), which {@code Sndh.combine} evened for every subtune after the
 * first; the first was at the byte after the last image, odd where that
 * image's length is odd.
 *
 * <p>A bound tune of version 10 has the transformation block's offset at
 * 24 and its source index at 28, and the block after the image, byte for
 * byte the tune file's (BINARIES.md 1.2, 1.3); in an SNDH file the field
 * at 24 is patched to reach the block after the images (3.1). A table of
 * 34 columns has a separate image from one of 30, whose period differs
 * at the writer's unit (1.4).
 */
final class WriterTest {

    /** A table of one row, the columns a tune of one frame needs. */
    private static Columns oneRow() {
        byte[][] column = new byte[Columns.C][1];
        column[0][0] = (byte) 0x80;
        return new Columns(column, 0, 0);
    }

    private static Tune.Written at(int rate) {
        return Tune.write(oneRow(), new Sources(List.of()), rate, 1, BuiltTunes.RING,
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
        // where subtune 1 was before this fault was fixed.
        Bound.Set bound = Bound.of(List.of(at(50).file(), at(60).file()));
        assertEquals(1, bound.images().size(), "the two tunes share one image");
        Bound.Set odd = new Bound.Set(List.of(new byte[15]), bound.shapes(),
                bound.tunes(), bound.image(), bound.table(), bound.blocks());
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

    /** The bytes of a tune file from the long at 16 to the DTX3 table,
     *  which the writer puts on a long after transformation T. */
    private static byte[] blockOf(byte[] file) {
        return Arrays.copyOfRange(file, Tune.getLong(file, Tune.TRANSFORMATIONS_AT),
                Tune.getLong(file, Tune.TABLE_AT));
    }

    @Test
    void aBoundTuneOfVersionTenHasItsBlockAfterTheImage() {
        byte[] file = BuiltTunes.mapped().file();
        byte[] bound = Bound.of(file);
        assertEquals(Tune.VERSION, Tune.getWord(bound, 4), "the tune file's version");
        int count = file[Tune.COUNT_AT] & 0xFF;
        assertEquals(28, Bound.indexAt(Tune.VERSION));
        assertEquals(24, Bound.indexAt(Tune.UNMAPPED));
        // DTX1 table 1 at align(28 + 4S), each as the tune file has it
        assertEquals(Tune.align(28 + 4 * count),
                Tune.getLong(bound, 28) & ~Tune.COUNTED, "source 1 follows the index");
        int image = Tune.getLong(bound, Bound.IMAGE_AT);
        for (int i = 0; i < count; i++) {
            int at = Tune.getLong(bound, 28 + 4 * i);
            int fileAt = Tune.getLong(file, Tune.indexAt(Tune.VERSION) + 4 * i);
            assertEquals(fileAt & Tune.COUNTED, at & Tune.COUNTED, "bit 31 of source " + (i + 1));
            int end = i + 1 < count ? Tune.getLong(bound, 28 + 4 * (i + 1)) & ~Tune.COUNTED
                    : image;
            int fileEnd = i + 1 < count
                    ? Tune.getLong(file, Tune.indexAt(Tune.VERSION) + 4 * (i + 1))
                    & ~Tune.COUNTED : file.length;
            assertArrayEquals(Arrays.copyOfRange(file, fileAt & ~Tune.COUNTED, fileEnd),
                    Arrays.copyOfRange(bound, at & ~Tune.COUNTED, end), "source " + (i + 1));
        }
        // the block on a long after the image, byte for byte, and last:
        // the bytes to the end of transformation 16, which the writer
        // pads to a long before the DTX3 table
        byte[] block = Bound.block(file);
        assertEquals(blockOf(file).length, Tune.align(block.length));
        assertArrayEquals(Arrays.copyOf(blockOf(file), block.length), block,
                "the block from the long at 16");
        int at = Tune.getLong(bound, Bound.TRANSFORMATIONS_AT);
        assertTrue(at > image, "the block is after the image");
        assertEquals(0, at % 4, "the block begins on a long");
        assertEquals(Tune.align(at + block.length), bound.length, "the block is last");
        assertArrayEquals(blockOf(file), Arrays.copyOfRange(bound, at, bound.length),
                "the block is the tune file's");
        assertArrayEquals(Tune.transformations(BuiltTunes.fade()), blockOf(file),
                "the block is the index and the sixteen transformations");
        assertEquals(0, Bound.block(BuiltTunes.deferred().file()).length,
                "a tune file of version 9 has no block");
    }

    @Test
    void aSetOfThirtyAndThirtyFourColumnsBindsIntoTwoImages() {
        byte[] nine = BuiltTunes.deferred().file();
        byte[] ten = BuiltTunes.mapped().file();
        Bound.Set set = Bound.of(List.of(nine, ten, nine));
        assertEquals(2, set.images().size(), "a table of 34 columns opens an image");
        assertEquals(List.of("DTX3 at unit 2, values of 1",
                "DTX3 at unit 2, 34 columns, values of 1"), set.shapes());
        assertArrayEquals(new int[] {0, 1, 0}, set.image());
        assertEquals(0, set.blocks().get(0).length);
        assertArrayEquals(Bound.block(ten), set.blocks().get(1));
        // a bound tune of a set: the image and the block absent, and the
        // fields at 16 and 24 written 0
        byte[] bound = set.tunes().get(1);
        assertEquals(0, Tune.getLong(bound, Bound.IMAGE_AT));
        assertEquals(0, Tune.getLong(bound, Bound.TRANSFORMATIONS_AT));
        int count = ten[Tune.COUNT_AT] & 0xFF;
        int last = Tune.getLong(bound, 28 + 4 * (count - 1)) & ~Tune.COUNTED;
        int lastFile = Tune.getLong(ten, Tune.indexAt(Tune.VERSION) + 4 * (count - 1))
                & ~Tune.COUNTED;
        assertEquals(Tune.align(last + ten.length - lastFile), bound.length,
                "the bound tune ends at its last source");
        // the SNDH file: the block after the images on a long, the field
        // at 24 patched to reach it, and the version 9 tunes as bound;
        // every offset here counts from the core's first byte
        byte[] core = new byte[64];
        byte[] file = Sndh.combine(core, set, new byte[] {0}, 16);
        int header = Sndh.even(12 + 1);
        int table = Sndh.even(core.length);
        int[] tune = new int[3];
        for (int i = 0; i < 3; i++) {
            tune[i] = Tune.getLong(file, header + table + 2 + 4 * i);
        }
        int image = Tune.align(Sndh.even(tune[2] + set.tunes().get(2).length));
        image = Tune.align(image + set.images().get(0).length);
        int at = Tune.align(image + set.images().get(1).length);
        assertEquals(at - tune[1], Tune.getLong(file, header + tune[1] + Bound.TRANSFORMATIONS_AT),
                "the field at 24 reaches the block, on a long after the last image");
        byte[] block = Bound.block(ten);
        assertArrayEquals(block, Arrays.copyOfRange(file, header + at, header + at + block.length));
        for (int i : new int[] {0, 2}) {
            byte[] one = set.tunes().get(i);
            assertArrayEquals(Arrays.copyOfRange(one, Bound.TABLE_AT, one.length),
                    Arrays.copyOfRange(file, header + tune[i] + Bound.TABLE_AT,
                            header + tune[i] + one.length),
                    "subtune " + (i + 1) + " from the field at 20");
        }
        assertEquals(Sndh.even(at + block.length), Tune.getLong(file, header + Sndh.CORE_WORK_AT),
                "the workspace follows the block");
    }
}
