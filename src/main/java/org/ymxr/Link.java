package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dtx.Table;

/**
 * A player linked for one song (doc/LINKER.md): the core of the song's
 * switches with the pieces of each block the song does not need left out,
 * and every field across a piece left out moved by the piece's bytes.
 */
final class Link {

    /** The handler kinds that are blocks: the general handler is in each
     *  effect's block. */
    static final List<String> KINDS = List.of("square", "one", "counted", "two", "three",
            "envelope");

    /** The blocks a core's pieces belong to, in the order the document
     *  lists them: each handler kind, which init's loop over it is, and
     *  its instance on each effect; the start of several columns; and
     *  each effect's frame code and general handler. */
    static final List<String> BLOCKS = blocks();

    private static List<String> blocks() {
        List<String> all = new ArrayList<>();
        for (String kind : KINDS) {
            all.add(kind);
            for (int i = 0; i < 4; i++) {
                all.add(kind + i);
            }
        }
        all.add("several");
        for (int i = 0; i < 4; i++) {
            all.add("effect" + i);
        }
        return List.copyOf(all);
    }

    private Link() {
    }

    /** Whether a song with these effects links: one that needs neither the
     *  deferred start nor the lookup. */
    static boolean links(boolean defers, boolean maps) {
        return !defers && !maps;
    }

    /**
     * The blocks a set of tunes needs: the block of each effect a tune runs,
     * bits 0 to 3 of its effects byte; for each source an effect's rows
     * start, the instance of its handler kind on that effect and the
     * kind's loop of init; and {@code several} where a start is of a
     * counted source or one of several columns.
     */
    static Set<String> blocks(List<TuneFile> tunes) {
        Set<String> needed = new LinkedHashSet<>();
        for (TuneFile tune : tunes) {
            for (int i = 0; i < 4; i++) {
                if ((tune.effects() & 1 << i) == 0) {
                    continue;
                }
                needed.add("effect" + i);
                for (int s : started(tune, i)) {
                    Table source = tune.sources().get(s - 1);
                    boolean counted = tune.counted().get(s - 1);
                    String kind = kind(source, counted);
                    if (KINDS.contains(kind)) {
                        needed.add(kind);
                        needed.add(kind + i);
                    }
                    if (counted || source.columns() > 1) {
                        needed.add("several");
                    }
                }
            }
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String block : BLOCKS) {
            if (needed.contains(block)) {
                ordered.add(block);
            }
        }
        return ordered;
    }

    /** The sources effect {@code i}'s rows start: a source column with
     *  its bit 7 set and a number from 1 up (SPEC.md 1.8.3), a number of
     *  a source the tune has. */
    static Set<Integer> started(TuneFile tune, int i) {
        byte[] column = tune.table().column(Columns.EFFECT + 4 * i + 1);
        Set<Integer> out = new java.util.TreeSet<>();
        for (byte b : column) {
            int s = b & 0x7F;
            if ((b & 0x80) != 0 && s > 0 && s <= tune.sources().size()) {
                out.add(s);
            }
        }
        return out;
    }

    /** The block of a source's handler kind, or {@code general} for the
     *  general handler, which every player has (68k/player/init.S
     *  resolves the same shapes). */
    static String kind(Table source, boolean counted) {
        if (counted) {
            return source.columns() == 1 ? "counted" : "envelope";
        }
        if (source.columns() == 2) {
            return "two";
        }
        if (source.columns() == 3) {
            return "three";
        }
        if (source.repeat() == 0 && source.rows() == 2) {
            return "square";
        }
        if (source.repeat() == 0 && source.rows() == 1) {
            return "one";
        }
        return "general";
    }

    /**
     * The core linked for the blocks named: every piece of another block
     * left out, every field in the bytes kept moved by -c times the bytes
     * of each piece left out it refers across, and each mask written with
     * the blocks kept.
     *
     * @throws IllegalArgumentException where a value no longer fits its
     *         field
     */
    static byte[] link(byte[] core, Pieces table, Set<String> blocks) {
        Set<String> out = new LinkedHashSet<>();
        int left = 0;
        for (Pieces.Piece piece : table.pieces()) {
            if (!blocks.contains(piece.block())) {
                out.add(piece.name());
                left += piece.size();
            }
        }
        byte[] moved = Arrays.copyOf(core, core.length);
        for (Pieces.Field field : table.fields()) {
            int by = 0;
            for (Map.Entry<String, Integer> move : field.moves().entrySet()) {
                if (out.contains(move.getKey())) {
                    by += move.getValue() * size(table, move.getKey());
                }
            }
            if (by == 0) {
                continue;
            }
            int value = Pieces.value(core, field.at(), field.width()) - by;
            if (field.width() == 1) {
                // a short branch of 0 is a word branch on the 68000
                if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE || value == 0) {
                    throw new IllegalArgumentException("the byte field at " + field.at()
                            + " is " + value + " linked");
                }
                moved[field.at()] = (byte) value;
            } else {
                if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
                    throw new IllegalArgumentException("the word field at " + field.at()
                            + " is " + value + " linked");
                }
                Tune.putWord(moved, field.at(), value);
            }
        }
        for (Pieces.Mask mask : table.masks()) {
            int bits = 0;
            for (int i = 0; i < 16; i++) {
                if (blocks.contains(mask.prefix() + i)) {
                    bits |= 1 << i;
                }
            }
            Tune.putWord(moved, mask.at(), bits);
        }
        byte[] linked = new byte[core.length - left];
        int from = 0;
        int to = 0;
        for (Pieces.Piece piece : table.pieces()) {
            if (out.contains(piece.name())) {
                System.arraycopy(moved, from, linked, to, piece.from() - from);
                to += piece.from() - from;
                from = piece.to();
            }
        }
        System.arraycopy(moved, from, linked, to, core.length - from);
        return linked;
    }

    private static int size(Pieces table, String name) {
        for (Pieces.Piece piece : table.pieces()) {
            if (piece.name().equals(name)) {
                return piece.size();
            }
        }
        throw new IllegalArgumentException("no piece " + name);
    }

    /** The blocks a table has that the set named leaves out, in the order
     *  of {@link #BLOCKS}. */
    static List<String> leftOut(Pieces table, Set<String> blocks) {
        Set<String> all = new LinkedHashSet<>();
        for (Pieces.Piece piece : table.pieces()) {
            all.add(piece.block());
        }
        List<String> out = new ArrayList<>();
        for (String block : BLOCKS) {
            if (all.contains(block) && !blocks.contains(block)) {
                out.add(block);
            }
        }
        return out;
    }
}
