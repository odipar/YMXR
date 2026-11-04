package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Writer regressions: reject frame rates above the header word, align
 * the first bound subtune even, and preserve version 10 transformation
 * blocks.
 *
 * <p>A bound version 10 tune points to its block at offset 24 and
 * starts its source index at 28. The unchanged block follows images in
 * SNDH files. Thirty- and thirty-four-column tables require separate
 * images (BINARIES.md 1.2 to 1.4).
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

    /**
     * Tune-file transformation block, from the header pointer at 16 to
     * the aligned DTX3 table.
     */
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
        // The unchanged transformation block follows the image, then the
        // list of its pages (BINARIES.md 1.1), padded to a four-byte
        // boundary.
        byte[] block = Bound.block(file);
        byte[] list = Pages.of(TuneFile.read(file));
        int own = block.length - list.length;
        assertEquals(blockOf(file).length, Tune.align(own));
        assertArrayEquals(Arrays.copyOf(blockOf(file), own), Arrays.copyOf(block, own),
                "the block from the long at 16");
        assertArrayEquals(list, Arrays.copyOfRange(block, own, block.length),
                "the list after the block");
        int at = Tune.getLong(bound, Bound.TRANSFORMATIONS_AT);
        assertTrue(at > image, "the block is after the image");
        assertEquals(0, at % 4, "the block begins on a long");
        assertEquals(Tune.align(at + block.length), bound.length, "the block is last");
        assertArrayEquals(block, Arrays.copyOfRange(bound, at, at + block.length),
                "the block is the tune file's and its list");
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
