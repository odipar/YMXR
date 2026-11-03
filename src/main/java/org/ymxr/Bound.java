package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dtx.Dtx;
import org.dtx.Packager;
import org.jspecify.annotations.Nullable;

/**
 * A tune bound with DTX's reader for playback (doc/BINARIES.md 1).
 * Sources precede the reader image; optional transformation tables follow
 * it. Frames prepare lookups from those tables; ticks read source rows
 * and prepared lookup pages.
 *
 * <pre>
 *  offset  bytes  what it is
 *  0       4      YMXB
 *  4       2      the version, the tune file's, $0007 to $000A
 *  6       2      the frame rate, in Hz
 *  8       1      effects used, the tune file's
 *  9       1      S, the source count, 0 to 127
 *  10      2      bytes 10 and 11 of the tune file, which a player skips
 *  12      4      the state block's bytes the image's reader needs
 *  16      4      where the image begins, signed
 *  20      4      where this tune's table is, from the image's first byte
 *  24      4      version 10: where the transformation block begins, signed
 *  24, 28  4S     the source index, at 24 and at 28 in version 10: where
 *                 source 1 to S's DTX1 table begins, bit 31 the mark of a
 *                 counted source
 *          ..     the DTX1 tables, each on a long
 *          ..     the image, on a long
 *          ..     version 10: the transformation block, on a long
 * </pre>
 *
 * Every offset counts from the first byte. The transformation block is
 * the tune file's transformation index and transformations byte for byte
 * (SPEC.md 3.4), each offset of its index counted from its first byte.
 */
final class Bound {

    static final byte[] MAGIC = {'Y', 'M', 'X', 'B'};

    static final int STATE_AT = 12;
    static final int IMAGE_AT = 16;
    static final int TABLE_AT = 20;
    static final int INDEX_AT = 24;

    /**
     * Offset of the version 10 transformation-block pointer
     * (BINARIES.md 1.2).
     */
    static final int TRANSFORMATIONS_AT = 24;

    /** The image's format block, and where the state block's bytes and the
     *  first table are in it (DTX, abi.md 1). */
    static final int FORMAT_AT = 16;
    static final int FORMAT_STATE_AT = 4;
    static final int FORMAT_TABLE_AT = 8;

    private Bound() {
    }

    /**
     * Source-index offset: 24 in versions 7 to 9; 28 in version 10,
     * after the transformation-block pointer (BINARIES.md 1.2).
     */
    static int indexAt(int version) {
        return version == Tune.VERSION ? TRANSFORMATIONS_AT + 4 : INDEX_AT;
    }

    /**
     * Shared images and bound tunes in input order. Tunes with the same
     * image parameters share reader code (DTX abi.md 1). Each bound
     * tune omits its image and transformation block; their offsets
     * remain 0 until the caller places and patches them.
     *
     * @param image image number for each tune
     *
     * @param table table offset within each tune's image
     *
     * @param blocks transformation block per tune, empty below version
     * 10
     */
    record Set(List<byte[]> images, List<String> shapes, List<byte[]> tunes,
            int[] image, int[] table, List<byte[]> blocks) {
    }

    /** The set of tunes bound with as few images as the figures allow. */
    static Set of(List<byte[]> tuneFiles) {
        // A tune joins the first group whose image would carry its table:
        // the packager reads a table that does not fit, so the key names what
        // it reads it against.
        List<List<Integer>> groups = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        int[] image = new int[tuneFiles.size()];
        int[] table = new int[tuneFiles.size()];
        for (int i = 0; i < tuneFiles.size(); i++) {
            String key = shape(TuneFile.read(tuneFiles.get(i)).dtx3());
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
                tables.add(TuneFile.read(tuneFiles.get(i)).dtx3());
            }
            Packager.Packaged made = Packager.packaged(tables);
            images.add(made.image());
            for (int j = 0; j < group.size(); j++) {
                table[group.get(j)] = made.headers()[j];
            }
        }
        List<byte[]> tunes = new ArrayList<>();
        List<byte[]> blocks = new ArrayList<>();
        for (int i = 0; i < tuneFiles.size(); i++) {
            tunes.add(head(tuneFiles.get(i), table[i],
                    Tune.getLong(images.get(image[i]), FORMAT_AT + FORMAT_STATE_AT)));
            blocks.add(block(tuneFiles.get(i)));
        }
        return new Set(images, keys, tunes, image, table, blocks);
    }

    /** What an image fixes once, as a key two tables are grouped by and as
     *  a reader of the report sees it: the variant, the width, the unit,
     *  and the column count of a table of 34 (DTX abi.md 1). Under DTX3
     *  each table defines its ring, and the image has the copy code where
     *  a table of it has copies, so neither separates two tables. The
     *  period follows C at the writer's unit, so a table of 30 columns and
     *  one of 34 go into separate images, and the key of a table of 30 is
     *  the key of version 9. */
    private static String shape(byte[] dtx3) {
        Dtx.Header header = Dtx.header(dtx3);
        int payload = header.length();
        return "DTX" + header.variant() + " at unit " + (dtx3[payload + 2] & 0xFF)
                + (header.columns() == Columns.MAPPED ? ", " + Columns.MAPPED + " columns" : "")
                + ", values of " + header.width();
    }

    /**
     * Builds a bound header, source index and DTX1 tables without an
     * image or transformation block. The caller places the shared image
     * and optional block and patches their offsets.
     */
    private static byte[] head(byte[] tuneFile, int table, int state) {
        return build(tuneFile, null, table, state);
    }

    /**
     * Binds a tune file: header, recomputed source index, unchanged
     * DTX1 tables, packaged DTX3 image and, in version 10,
     * transformation block (doc/BINARIES.md 1).
     */
    static byte[] of(byte[] tuneFile) {
        byte[] image = Packager.image(TuneFile.read(tuneFile).dtx3());
        return build(tuneFile, image,
                Tune.getLong(image, FORMAT_AT + FORMAT_TABLE_AT),
                Tune.getLong(image, FORMAT_AT + FORMAT_STATE_AT));
    }

    /**
     * Copies a version 10 transformation block unchanged, or returns an
     * empty array for versions 7 to 9. The block begins at the header's
     * long at 16 and ends after its index or last entry, whichever is
     * later. Index offsets are relative to the block, so relocation
     * preserves them. The reader has already checked bounds (SPEC.md
     * 3.4.2).
     */
    static byte[] block(byte[] tuneFile) {
        if (Tune.getWord(tuneFile, 4) != Tune.VERSION) {
            return new byte[0];
        }
        int at = Tune.getLong(tuneFile, Tune.TRANSFORMATIONS_AT);
        int count = Tune.getWord(tuneFile, at);
        int end = at + 2 + 4 * count;
        for (int n = 0; n < count; n++) {
            int table = at + Tune.getLong(tuneFile, at + 2 + 4 * n);
            end = Math.max(end, table + 2 + Tune.getWord(tuneFile, table));
        }
        return Arrays.copyOfRange(tuneFile, at, end);
    }

    /**
     * Builds the bound header, source index and DTX1 tables. If an
     * image is supplied, append it and the version 10 transformation
     * block.
     *
     * @param image the image to append, or null for a shared image
     * placed by the caller, which patches {@code IMAGE_AT} and version
     * 10 {@code TRANSFORMATIONS_AT}
     */
    private static byte[] build(byte[] tuneFile, byte @Nullable [] image, int table,
                                int state) {
        TuneFile tune = TuneFile.read(tuneFile);
        int version = tune.version();
        int from = Tune.indexAt(version);
        int index = indexAt(version);
        int count = tune.sources().size();
        byte[][] tables = new byte[count][];
        for (int i = 0; i < count; i++) {
            int at = Tune.getLong(tuneFile, from + 4 * i) & ~Tune.COUNTED;
            int to = i + 1 < count
                    ? Tune.getLong(tuneFile, from + 4 * (i + 1)) & ~Tune.COUNTED
                    : tuneFile.length;
            tables[i] = Arrays.copyOfRange(tuneFile, at, to);
        }
        // Place sources before the image to preserve their reach
        // through the player's signed word displacements. Put the
        // transformation block last: frames read it, while ticks read
        // sources and prepared lookup pages (BINARIES.md 1.3).
        int here = Tune.align(index + 4 * count);
        int[] sourceAt = new int[count];
        for (int i = 0; i < count; i++) {
            sourceAt[i] = here;
            here = Tune.align(here + tables[i].length);
        }
        int imageAt = image == null ? 0 : here;
        if (image != null) {
            here = Tune.align(here + image.length);
        }
        byte[] block = image == null ? new byte[0] : block(tuneFile);
        int blockAt = block.length == 0 ? 0 : here;
        here = Tune.align(here + block.length);
        byte[] bound = new byte[here];
        System.arraycopy(tuneFile, 0, bound, 0, Tune.TABLE_AT);
        System.arraycopy(MAGIC, 0, bound, 0, 4);
        Tune.putWord(bound, 4, version);
        Tune.putLong(bound, STATE_AT, state);
        Tune.putLong(bound, IMAGE_AT, imageAt);
        // Where this tune's table is in the image it is packaged into. An
        // image of one names it in its format block; one of several
        // names the first, so a tune past the first records a separate offset.
        Tune.putLong(bound, TABLE_AT, table);
        if (version == Tune.VERSION) {
            Tune.putLong(bound, TRANSFORMATIONS_AT, blockAt);
        }
        // A counted source's mark is in the index a player reads, so
        // bit 31 is in the binding beside the offset (SPEC.md 3.1).
        for (int i = 0; i < count; i++) {
            Tune.putLong(bound, index + 4 * i,
                    sourceAt[i] | (tune.counted().get(i) ? Tune.COUNTED : 0));
        }
        if (image != null) {
            System.arraycopy(image, 0, bound, imageAt, image.length);
        }
        System.arraycopy(block, 0, bound, blockAt, block.length);
        for (int i = 0; i < count; i++) {
            System.arraycopy(tables[i], 0, bound, sourceAt[i], tables[i].length);
        }
        return bound;
    }
}
