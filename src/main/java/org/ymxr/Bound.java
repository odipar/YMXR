package org.ymxr;

import java.util.Arrays;
import org.dtx.Packager;

/**
 * A bound tune: a tune file's tables bound with DTX's reader into what the
 * player takes. doc/BINARIES.md is the contract. The layout is the tune
 * file's ({@link Tune}) under its own magic and version, with the image DTX
 * packages the DTX2 table with where the file has the table, and the bytes
 * of the state block the image's reader needs stated in the header, so
 * that a host allocates them without reading the image.
 *
 * <pre>
 *  offset  bytes  gives
 *  0       4      YMXB
 *  4       2      the version, $0001
 *  6       2      the frame rate, in Hz
 *  8       1      effects used, bits 3 to 0
 *  9       1      S, the source count, 0 to 127
 *  10      2      zero
 *  12      4      the state block's bytes the image's reader needs
 *  16      4      where the image begins
 *  20      4S     the source index: where source 1 to S's DTX1 table begins
 *          ..     the image, on a long
 *          ..     the DTX1 tables, each on a long
 * </pre>
 *
 * Every offset counts from the first byte.
 */
final class Bound {

    static final byte[] MAGIC = {'Y', 'M', 'X', 'B'};
    static final int VERSION = 0x0001;
    static final int STATE_AT = 12;
    static final int IMAGE_AT = 16;
    static final int INDEX_AT = 20;

    /** The image's format block, and where the state block's bytes stand in
     *  it (DTX, abi.md 1). */
    static final int FORMAT_AT = 16;
    static final int FORMAT_STATE_AT = 4;

    private Bound() {
    }

    /** The bound tune of a tune file: the header with the magic and the
     *  version replaced and the state block's bytes and the image's place
     *  put in, the index recomputed, then the image, then the file's DTX1
     *  tables as they stand. */
    static byte[] of(byte[] tuneFile) {
        TuneFile tune = TuneFile.read(tuneFile);
        byte[] image = Packager.image(tune.dtx2());
        int count = tune.sources().size();
        byte[][] tables = new byte[count][];
        for (int i = 0; i < count; i++) {
            int at = Tune.getLong(tuneFile, Tune.INDEX_AT + 4 * i);
            int to = i + 1 < count ? Tune.getLong(tuneFile, Tune.INDEX_AT + 4 * (i + 1))
                    : tuneFile.length;
            tables[i] = Arrays.copyOfRange(tuneFile, at, to);
        }
        int here = Tune.align(INDEX_AT + 4 * count);
        int imageAt = here;
        here = Tune.align(here + image.length);
        int[] sourceAt = new int[count];
        for (int i = 0; i < count; i++) {
            sourceAt[i] = here;
            here = Tune.align(here + tables[i].length);
        }
        byte[] bound = new byte[here];
        System.arraycopy(tuneFile, 0, bound, 0, Tune.TABLE_AT);
        System.arraycopy(MAGIC, 0, bound, 0, 4);
        Tune.putWord(bound, 4, VERSION);
        Tune.putLong(bound, STATE_AT, Tune.getLong(image, FORMAT_AT + FORMAT_STATE_AT));
        Tune.putLong(bound, IMAGE_AT, imageAt);
        for (int i = 0; i < count; i++) {
            Tune.putLong(bound, INDEX_AT + 4 * i, sourceAt[i]);
        }
        System.arraycopy(image, 0, bound, imageAt, image.length);
        for (int i = 0; i < count; i++) {
            System.arraycopy(tables[i], 0, bound, sourceAt[i], tables[i].length);
        }
        return bound;
    }
}
