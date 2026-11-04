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

    /** The blocks a core's pieces belong to, in the order the document
     *  lists them. */
    static final List<String> BLOCKS = List.of("square", "one", "counted", "two", "three",
            "envelope", "several");

    private Link() {
    }

    /** Whether a song with these effects links: one that needs neither the
     *  deferred start nor the lookup. */
    static boolean links(boolean defers, boolean maps) {
        return !defers && !maps;
    }

    /**
     * The blocks a set of tunes needs: for each source of each tune, the
     * block of its handler kind, and {@code several} for a counted source
     * or one of several columns. Init resolves every source of the tune,
     * so a source the rows never start counts.
     */
    static Set<String> blocks(List<TuneFile> tunes) {
        Set<String> needed = new LinkedHashSet<>();
        for (TuneFile tune : tunes) {
            for (int s = 0; s < tune.sources().size(); s++) {
                Table source = tune.sources().get(s);
                boolean counted = tune.counted().get(s);
                needed.add(kind(source, counted));
                if (counted || source.columns() > 1) {
                    needed.add("several");
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
     * left out, and every field in the bytes kept moved by -c times the
     * bytes of each piece left out it refers across.
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
