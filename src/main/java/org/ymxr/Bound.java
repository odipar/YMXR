package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dtx.Dtx;
import org.dtx.Packager;
import org.jspecify.annotations.Nullable;

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
 *  4       2      the version, $0002
 *  6       2      the frame rate, in Hz
 *  8       1      effects used, bits 3 to 0
 *  9       1      S, the source count, 0 to 127
 *  10      2      zero
 *  12      4      the state block's bytes the image's reader needs
 *  16      4      where the image begins, signed
 *  20      4      where this tune's table stands, from the image's first byte
 *  24      4S     the source index: where source 1 to S's DTX1 table begins
 *          ..     the image, on a long
 *          ..     the DTX1 tables, each on a long
 * </pre>
 *
 * Every offset counts from the first byte.
 */
final class Bound {

    static final byte[] MAGIC = {'Y', 'M', 'X', 'B'};
    static final int VERSION = 0x0002;
    static final int STATE_AT = 12;
    static final int IMAGE_AT = 16;
    static final int TABLE_AT = 20;
    static final int INDEX_AT = 24;

    /** The image's format block, and where the state block's bytes and the
     *  first table stand in it (DTX, abi.md 1). */
    static final int FORMAT_AT = 16;
    static final int FORMAT_STATE_AT = 4;
    static final int FORMAT_TABLE_AT = 8;

    private Bound() {
    }

    /**
     * A set of tunes bound together: the images their tables were packaged
     * into, and a bound tune for each, in the order given. A bound tune
     * here carries no image of its own, and its {@code IMAGE_AT} stands at
     * 0 for the caller that lays them out to patch (SPEC.md's files are
     * laid out by {@link Sndh}).
     *
     * <p>The tunes are grouped by what an image gives once (DTX abi.md 1),
     * so tunes that agree on those share one image and the reader's code
     * stands once for the group.
     *
     * @param image which image each tune's table stands in
     * @param table where each tune's table stands in it
     */
    record Set(List<byte[]> images, List<String> shapes, List<byte[]> tunes,
            int[] image, int[] table) {
    }

    /** The set of tunes bound with as few images as the figures allow. */
    static Set of(List<byte[]> tuneFiles) {
        // A tune joins the first group whose image would take its table:
        // the packager reads a table that does not fit, so the key is what
        // it reads it against.
        List<List<Integer>> groups = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        int[] image = new int[tuneFiles.size()];
        int[] table = new int[tuneFiles.size()];
        for (int i = 0; i < tuneFiles.size(); i++) {
            String key = shape(TuneFile.read(tuneFiles.get(i)).dtx2());
            int at = keys.indexOf(key);
            if (at < 0) {
                at = keys.size();
                keys.add(key);
                groups.add(new ArrayList<>());
            }
            groups.get(at).add(i);
            image[i] = at;
        }
        List<byte[]> images = new ArrayList<>();
        for (List<Integer> group : groups) {
            List<byte[]> tables = new ArrayList<>();
            for (int i : group) {
                tables.add(TuneFile.read(tuneFiles.get(i)).dtx2());
            }
            Packager.Packaged made = Packager.packaged(tables);
            images.add(made.image());
            for (int j = 0; j < group.size(); j++) {
                table[group.get(j)] = made.headers()[j];
            }
        }
        List<byte[]> tunes = new ArrayList<>();
        for (int i = 0; i < tuneFiles.size(); i++) {
            tunes.add(head(tuneFiles.get(i), table[i],
                    Tune.getLong(images.get(image[i]), FORMAT_AT + FORMAT_STATE_AT)));
        }
        return new Set(images, keys, tunes, image, table);
    }

    /** What an image gives once, as a key two tables are grouped by and as
     *  a reader of the report sees it: the variant, the width, and under
     *  DTX2 the unit, the copies flag and the ring (DTX abi.md 1). The
     *  period follows the ring and C, which the schema fixes. */
    private static String shape(byte[] dtx2) {
        Dtx.Header header = Dtx.header(dtx2);
        int payload = header.length();
        return "DTX" + header.variant() + " at unit " + (dtx2[payload + 2] & 0xFF)
                + ", a ring of " + Tune.getWord(dtx2, payload)
                + ", values of " + header.width()
                + ((dtx2[payload + 3] & 1) != 0 ? ", with copies" : "");
    }

    /** A bound tune with no image in it: the header, the state block's
     *  bytes off the image the table went into, the table's place, and the
     *  file's DTX1 tables. The image's own place is the caller's to put in. */
    private static byte[] head(byte[] tuneFile, int table, int state) {
        return build(tuneFile, null, table, state);
    }

    /** The bound tune of a tune file: the header with the magic and the
     *  version replaced and the state block's bytes and the image's place
     *  put in, the index recomputed, then the image, then the file's DTX1
     *  tables as they stand. */
    static byte[] of(byte[] tuneFile) {
        byte[] image = Packager.image(TuneFile.read(tuneFile).dtx2());
        return build(tuneFile, image,
                Tune.getLong(image, FORMAT_AT + FORMAT_TABLE_AT),
                Tune.getLong(image, FORMAT_AT + FORMAT_STATE_AT));
    }

    /**
     * The bound tune: the header, the state block's bytes, the table's
     * place in the image that holds it, the source index, then the image
     * where one is given and the file's DTX1 tables.
     *
     * @param image the image to carry, or null where the tunes share one
     *     the caller lays out and patches {@code IMAGE_AT} for
     */
    private static byte[] build(byte[] tuneFile, byte @Nullable [] image, int table,
                                int state) {
        TuneFile tune = TuneFile.read(tuneFile);
        int count = tune.sources().size();
        byte[][] tables = new byte[count][];
        for (int i = 0; i < count; i++) {
            int at = Tune.getLong(tuneFile, Tune.INDEX_AT + 4 * i);
            int to = i + 1 < count ? Tune.getLong(tuneFile, Tune.INDEX_AT + 4 * (i + 1))
                    : tuneFile.length;
            tables[i] = Arrays.copyOfRange(tuneFile, at, to);
        }
        int here = Tune.align(INDEX_AT + 4 * count);
        int imageAt = image == null ? 0 : here;
        if (image != null) {
            here = Tune.align(here + image.length);
        }
        int[] sourceAt = new int[count];
        for (int i = 0; i < count; i++) {
            sourceAt[i] = here;
            here = Tune.align(here + tables[i].length);
        }
        byte[] bound = new byte[here];
        System.arraycopy(tuneFile, 0, bound, 0, Tune.TABLE_AT);
        System.arraycopy(MAGIC, 0, bound, 0, 4);
        Tune.putWord(bound, 4, VERSION);
        Tune.putLong(bound, STATE_AT, state);
        Tune.putLong(bound, IMAGE_AT, imageAt);
        // Where this tune's table stands in the image that holds it. An
        // image of one names it in its own format block; one of several
        // names the first, so a tune past the first carries its own.
        Tune.putLong(bound, TABLE_AT, table);
        for (int i = 0; i < count; i++) {
            Tune.putLong(bound, INDEX_AT + 4 * i, sourceAt[i]);
        }
        if (image != null) {
            System.arraycopy(image, 0, bound, imageAt, image.length);
        }
        for (int i = 0; i < count; i++) {
            System.arraycopy(tables[i], 0, bound, sourceAt[i], tables[i].length);
        }
        return bound;
    }
}
