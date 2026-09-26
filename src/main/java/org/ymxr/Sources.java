package org.ymxr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The sources a tune names, built from its effects (SPEC.md 2.2, 3.1): one
 * a distinct kind and value, numbered from 1 as first met. What the shape
 * of a source sounds is YMXS's (YMXS, SPEC.md 3.2): a SID voice is two
 * rows, its level and 0; a sync buzzer one row, its shape; a digidrum the
 * recording's levels and a closing row at mid-scale. The last row of every
 * source has bit 7 set, the marker a tick ends on, which is this format's
 * and not the structure's (SPEC.md 3.2).
 */
final class Sources {

    /** The most sources a tune names: the source column's seven bits. */
    static final int MOST = 127;

    /** Bit 7 of a source's last row. */
    static final int MARK = 0x80;

    /** The level a digidrum's last row leaves its register at: mid-scale,
     *  so the frame write that sets the register back does not click. */
    static final int PARK = 13;

    /** One source: a column a value of the row (SPEC.md 2.2.1), each
     *  column the R values of that column in row order, and the row it
     *  repeats to, `R` where it plays once. The marker is in bit 7 of
     *  the last row of the column the target names (SPEC.md 3.2.1), other
     *  than on a source whose column fills its byte, where every row is a
     *  whole byte and the end is in the count a player keeps. */
    record Source(int kind, int data, byte[][] columns, int repeat, boolean counted) {

        /** A source the marker ends. */
        Source(int kind, int data, byte[][] columns, int repeat) {
            this(kind, data, columns, repeat, false);
        }

        /** One column, the shape a YM dump converts to. */
        static Source of(int kind, int data, byte[] rows, int repeat) {
            return new Source(kind, data, new byte[][] {rows}, repeat);
        }

        /** The rows of every column. */
        int rows() {
            return columns[0].length;
        }

        /** The values a row: C, the source's columns (SPEC.md 3.1.3). */
        int width() {
            return columns.length;
        }
    }

    private final List<Source> list = new ArrayList<>();
    private final Map<Integer, Integer> numbers = new HashMap<>();
    private final byte[][] drums;

    /** Sources passed whole, numbered 1 upward in that order: what a tune
     *  built rather than converted uses. */
    Sources(List<Source> passed) {
        list.addAll(passed);
        drums = new byte[0][];
    }

    /** The song's digidrums as 4-bit levels: the high nibble of an 8-bit
     *  sample, or the byte as it is where the file has 4-bit values. */
    Sources(YmDump.Song song) {
        boolean fourBit = (song.attributes() & YmDump.Song.A_DRUM4BITS) != 0;
        byte[][] source = song.drums();
        drums = new byte[source.length][];
        for (int i = 0; i < source.length; i++) {
            drums[i] = new byte[source[i].length];
            for (int j = 0; j < source[i].length; j++) {
                drums[i][j] = (byte) (fourBit ? source[i][j] & 15 : (source[i][j] & 0xFF) >> 4);
            }
        }
    }

    /** The number of the source a slot names, built on first use, or 0
     *  where the slot names none: a dropped kind, a digidrum the file does
     *  not have, or one past the ceiling. */
    int number(Effects.Slot slot, Report report) {
        int data = slot.kind() == Effects.DRUM ? slot.data() & 31 : slot.data() & 15;
        if (slot.kind() == Effects.SINUS) {
            report.sinus++;
            return 0;
        }
        if (slot.kind() == Effects.DRUM && data >= drums.length) {
            report.missingDrums++;
            return 0;
        }
        int key = slot.kind() << 8 | data;
        Integer known = numbers.get(key);
        if (known != null) {
            return known;
        }
        if (list.size() == MOST) {
            report.overflow++;
            return 0;
        }
        list.add(build(slot.kind(), data));
        numbers.put(key, list.size());
        return list.size();
    }

    private Source build(int kind, int data) {
        switch (kind) {
            case Effects.SID:
                // The level then the silence. The row that starts the square
                // leaves the level as it is, so the voice keeps the                // value the last row set for a timer's period, and the first tick opens
                // the loud half.
                return Source.of(kind, data, new byte[] {(byte) data, (byte) MARK}, 0);
            case Effects.BUZZER:
                return Source.of(kind, data, new byte[] {(byte) (MARK | data)}, 0);
            default:
                byte[] rows = new byte[drums[data].length + 1];
                System.arraycopy(drums[data], 0, rows, 0, drums[data].length);
                rows[rows.length - 1] = (byte) (MARK | PARK);
                return Source.of(kind, data, rows, rows.length);
        }
    }

    /** Source `number`, 1 upward. */
    Source get(int number) {
        return list.get(number - 1);
    }

    int count() {
        return list.size();
    }

    List<Source> all() {
        return List.copyOf(list);
    }
}
