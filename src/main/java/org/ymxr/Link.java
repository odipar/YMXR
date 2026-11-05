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

    /** The lookup kinds, each a block on each effect: the general, the
     *  counted, two columns, noise, buzzer, voice, the counted of two
     *  columns and the counted on noise (68k/player/lookup_kinds.S). */
    static final List<String> LOOKUPS = List.of("lk", "lc", "lt", "ln", "lb", "lv", "le", "lf");

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
        for (String kind : LOOKUPS) {
            all.add(kind);
            for (int i = 0; i < 4; i++) {
                all.add(kind + i);
            }
        }
        all.add("align");
        return List.copyOf(all);
    }

    private Link() {
    }

    /** Whether a song with these effects links: every song does, on the
     *  core its switches and its effects select. */
    static boolean links(boolean defers, boolean maps) {
        return true;
    }

    /**
     * The blocks a set of tunes needs: the block of each effect a tune runs,
     * bits 0 to 3 of its effects byte; for each source an effect's rows
     * start, the instance of its handler kind on that effect and the
     * kind's loop of init; {@code several} where a start is of a counted
     * source or one of several columns; the lookup instance of each kind
     * an effect maps a source through, and the kind's loop of init; and
     * {@code align}, the padding before the second bank.
     */
    static Set<String> blocks(List<TuneFile> tunes) {
        Set<String> needed = new LinkedHashSet<>();
        needed.add("align");
        for (TuneFile tune : tunes) {
            for (String lookup : lookups(tune)) {
                needed.add(lookup);
                needed.add(lookup.substring(0, 2));
            }
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

    /**
     * The lookup instances a tune runs, as blocks such as {@code lt1}: for
     * each start of a source through a transformation, the kept mapping
     * not 0, and each remap of a running source to a transformation, the
     * lookup kind of the source on the target it started on. A square and
     * a source of one row keep their entries in the identity handler. The
     * rows are read as a player reads them, through the repeat row until
     * each effect's state there is one it has had before, as Pages reads
     * them; a deferred start on a running timer adds its source to the
     * ones a remap reaches.
     */
    static Set<String> lookups(TuneFile tune) {
        Set<String> out = new LinkedHashSet<>();
        if (tune.version() != Tune.VERSION) {
            return out;
        }
        Table table = tune.table();
        int[] target = new int[4];
        int[] mapping = new int[4];
        List<Set<Integer>> running = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            running.add(new java.util.TreeSet<>());
        }
        for (int row = 0; row < table.rows(); row++) {
            lookupRow(tune, table, row, target, mapping, running, out);
        }
        Set<List<Integer>> seen = new java.util.HashSet<>();
        while (table.repeat() < table.rows()) {
            List<Integer> state = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                state.add(target[i]);
                state.add(mapping[i]);
                state.add(-1);
                state.addAll(running.get(i));
            }
            if (!seen.add(state)) {
                break;
            }
            for (int row = table.repeat(); row < table.rows(); row++) {
                lookupRow(tune, table, row, target, mapping, running, out);
            }
        }
        return out;
    }

    /** One row's effect columns for {@link #lookups}: a running source is
     *  kept with its target, its number times 64 plus the target. */
    private static void lookupRow(TuneFile tune, Table table, int row, int[] target,
                                  int[] mapping, List<Set<Integer>> running, Set<String> out) {
        for (int i = 0; i < 4; i++) {
            if ((tune.effects() & 1 << i) == 0) {
                continue;
            }
            int t = Columns.EFFECT + 4 * i;
            int tg = table.column(t)[row] & 0xFF;
            int src = table.column(t + 1)[row] & 0xFF;
            int control = table.column(t + 2)[row] & 0xFF;
            int map = table.column(Columns.MAPPING + i)[row] & 0xFF;
            if ((tg & 0x80) != 0) {
                target[i] = tg & 0x7F;
            }
            if ((src & 0x80) != 0) {
                int s = src & 0x7F;
                if (s == 0) {
                    running.get(i).clear();
                    continue;
                }
                if ((map & 0x80) != 0) {
                    mapping[i] = map & 0x7F;
                }
                String kind = lookup(tune, s, target[i]);
                if (mapping[i] != 0 && kind != null) {
                    out.add(kind + i);
                }
                boolean deferred = (control & 0x80) != 0 && (control & Columns.DEFERRED) != 0;
                if (!deferred || running.get(i).isEmpty()) {
                    running.get(i).clear();
                }
                running.get(i).add(s * 64 + target[i]);
            } else if ((map & 0x80) != 0) {
                mapping[i] = map & 0x7F;
                if (mapping[i] != 0) {
                    for (int one : running.get(i)) {
                        String kind = lookup(tune, one / 64, one % 64);
                        if (kind != null) {
                            out.add(kind + i);
                        }
                    }
                }
            }
        }
    }

    /** The lookup kind of source {@code s} on {@code target}, or null for a
     *  square or a source of one row (68k/player/lookup_start.S, .kind). */
    static @org.jspecify.annotations.Nullable String lookup(TuneFile tune, int s, int target) {
        if (s < 1 || s > tune.sources().size()) {
            return null;
        }
        Table source = tune.sources().get(s - 1);
        switch (kind(source, tune.counted().get(s - 1))) {
            case "square", "one":
                return null;
            case "general":
                return "lk";
            case "counted":
                return "lc";
            case "two":
                return target >= NOISE ? "ln" : "lt";
            case "three":
                return target == BUZZER ? "lb" : "lv";
            default:
                return target >= NOISE ? "lf" : "le";
        }
    }

    /** The first target that maps its first value, setNoiseA, and the target
     *  of three columns that maps its last, setBuzzer (SPEC.md 1.10.1). */
    private static final int NOISE = 22;
    private static final int BUZZER = 21;

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
        // the bytes each piece loses: all of a piece left out, and a kept
        // gap's length less the length the alignment sets
        Map<String, Integer> lost = new java.util.HashMap<>();
        for (Pieces.Piece piece : table.pieces()) {
            if (!blocks.contains(piece.block())) {
                lost.put(piece.name(), piece.size());
            }
        }
        Map<String, Pieces.Piece> byName = new java.util.HashMap<>();
        for (Pieces.Piece piece : table.pieces()) {
            byName.put(piece.name(), piece);
        }
        // the gaps on the references as carried, and on the references
        // picked; the link keeps the shorter, and the carried on a tie
        Map<Integer, Integer> carried = new java.util.LinkedHashMap<>();
        for (Pieces.Align align : table.aligns()) {
            if (align.word() >= 0) {
                carried.put(align.word(), Pieces.value(core, align.word(), 2));
            }
        }
        Map<String, Integer> asCarried = new java.util.HashMap<>(lost);
        gaps(table, byName, asCarried, carried, false);
        Map<Integer, Integer> references = new java.util.LinkedHashMap<>();
        Map<String, Integer> picked = new java.util.HashMap<>(lost);
        gaps(table, byName, picked, references, true);
        for (Pieces.Align align : table.aligns()) {
            if (align.word() >= 0 && !references.containsKey(align.word())) {
                references.put(align.word(), free(references,
                        carried.getOrDefault(align.word(), 0)));
            }
        }
        if (sum(picked) <= sum(asCarried)) {
            references = carried;
            lost.putAll(asCarried);
        } else {
            lost.putAll(picked);
        }
        for (Pieces.Twin twin : table.twins()) {
            int copy = lost.getOrDefault(twin.copy(), 0);
            int first = lost.getOrDefault(twin.first(), 0);
            if (copy != first) {
                throw new IllegalArgumentException("piece " + twin.copy() + " loses " + copy
                        + " bytes and " + twin.first() + ", which it copies, " + first);
            }
        }
        byte[] moved = Arrays.copyOf(core, core.length);
        for (Pieces.Field field : table.fields()) {
            int by = 0;
            for (Map.Entry<String, Integer> move : field.moves().entrySet()) {
                by += move.getValue() * lost.getOrDefault(move.getKey(), 0);
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
        for (Map.Entry<Integer, Integer> reference : references.entrySet()) {
            Tune.putWord(moved, reference.getKey(), reference.getValue());
        }
        java.io.ByteArrayOutputStream linked = new java.io.ByteArrayOutputStream();
        int from = 0;
        for (Pieces.Piece piece : table.pieces()) {
            int loses = lost.getOrDefault(piece.name(), 0);
            if (loses == 0) {
                continue;
            }
            linked.write(moved, from, piece.from() - from);
            // a gap kept at its new length is zeros, as the assembler pads
            linked.write(new byte[piece.size() - loses], 0, piece.size() - loses);
            from = piece.to();
        }
        linked.write(moved, from, core.length - from);
        return linked.toByteArray();
    }

    /**
     * The bytes each gap the link keeps loses, into {@code lost}: a gap's
     * length puts the byte its align names on the offset modulo 256 of its
     * anchor and add, the add of an instance's gap its effect's reference.
     * Where {@code pick}, the link picks an effect's reference on its first
     * gap it keeps: the reference that gap is 0 bytes for, or 2 bytes on
     * from it where another effect's reference is on that offset.
     */
    private static void gaps(Pieces table, Map<String, Pieces.Piece> byName,
                             Map<String, Integer> lost, Map<Integer, Integer> references,
                             boolean pick) {
        for (Pieces.Align align : table.aligns()) {
            Pieces.Piece gap = byName.get(align.gap());
            if (gap == null || lost.containsKey(gap.name())) {
                continue;
            }
            int from = linked(table, lost, gap.from());
            int anchor = linked(table, lost, align.anchor());
            int add = align.add();
            if (align.word() >= 0) {
                if (pick && !references.containsKey(align.word())) {
                    int least = Math.floorMod(from + align.base() - anchor, 256);
                    references.put(align.word(), free(references, least));
                }
                add = references.getOrDefault(align.word(), align.add());
            }
            int length = Math.floorMod(anchor + add - from - align.base(), 256);
            lost.put(gap.name(), gap.size() - length);
        }
    }

    private static int sum(Map<String, Integer> lost) {
        int sum = 0;
        for (int one : lost.values()) {
            sum += one;
        }
        return sum;
    }

    /** The first offset modulo 256 from {@code at} on, 2 bytes a step, that
     *  no reference picked is on. */
    private static int free(Map<Integer, Integer> references, int at) {
        int offset = at;
        while (references.containsValue(offset)) {
            offset = (offset + 2) & 255;
        }
        return offset;
    }

    /** Where a byte of the core is in the linked core: its offset less the
     *  bytes each piece that ends at or before it loses. */
    private static int linked(Pieces table, Map<String, Integer> lost, int at) {
        int by = 0;
        for (Pieces.Piece piece : table.pieces()) {
            if (piece.to() <= at) {
                by += lost.getOrDefault(piece.name(), 0);
            }
        }
        return at - by;
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
