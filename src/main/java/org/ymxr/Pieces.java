package org.ymxr;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The library of a core (doc/LINKER.md): its pieces, the runs of bytes a
 * player linked for one song leaves out, and its fields, the values that
 * move where a piece is left out. The build cuts a table from the
 * assembly of each core, and writes it beside the core as
 * {@code <core>.pieces}.
 *
 * @param pieces the pieces in the core's order
 * @param masks the words a link writes the blocks it keeps into
 * @param twins the pieces of the second bank, each with the piece of the
 *        first it copies
 * @param aligns the pieces whose length a link sets, each before a lookup
 *        instance or the second bank
 * @param fields the fields in the core's order
 */
record Pieces(List<Piece> pieces, List<Mask> masks, List<Twin> twins, List<Align> aligns,
              List<Field> fields) {

    /** The suffix of a table beside its core. */
    static final String SUFFIX = ".pieces";

    /** The pads the cut assembles each piece with: one that moves every
     *  byte of a field, and one that moves the high byte of a word and
     *  a byte field by 0. */
    static final int PAD = 2;
    static final int WIDE_PAD = 256;

    /**
     * A piece: its name, {@code <block>_<n>}, its first byte and the byte
     * past its last, offsets from the core's first byte.
     */
    record Piece(String name, int from, int to) {

        /** The block the piece is of. */
        String block() {
            return name.substring(0, name.lastIndexOf('_'));
        }

        int size() {
            return to - from;
        }
    }

    /**
     * A mask: a word of the core, at the label {@code ymxr_k_<prefix>}, that
     * a link writes with bit i set where it keeps block {@code <prefix>i}.
     * The core as carried has every bit of its blocks set.
     */
    record Mask(String prefix, int at) {
    }

    /**
     * A piece of the second bank, {@code copy}, and the piece of the first it
     * copies, {@code first}: a link leaves both out or keeps both, at one
     * length, and a field's coefficient for the two is {@code first}'s.
     */
    record Twin(String copy, String first) {
    }

    /**
     * A piece of padding, {@code gap}, whose length a link sets: the byte
     * {@code base} past its end, linked, is the byte {@code add} past
     * {@code anchor}, linked, modulo 256. A lookup instance's page read is
     * {@code base} past the gap before it, and its effect's reference is
     * {@code add} past {@code ymxr_ref} (68k/player/lookup_ref.S), the word
     * at {@code word}, which a link writes with the reference it picks; -1
     * where {@code add} is a constant.
     */
    record Align(String gap, int base, int anchor, int add, int word) {
    }

    /**
     * A field: its offset, its width in bytes, and for each piece whose end
     * it refers across, the coefficient c: a link that leaves the piece out
     * moves the value by -c times the piece's bytes.
     */
    record Field(int at, int width, Map<String, Integer> moves) {
    }

    /** Whether the build cuts a table from this core: every core. */
    static boolean cuts(Binaries.Binary binary) {
        return binary.source().equals(Binaries.CORE.source());
    }

    /** The name a core's table is carried as. */
    static String name(Binaries.Binary binary) {
        return binary.name().replace(".bin", SUFFIX);
    }

    /**
     * The table carried beside a core.
     *
     * @throws IllegalStateException where the classes carry none for it
     */
    static Pieces carried(Binaries.Binary binary) {
        String name = name(binary);
        try (InputStream in = Pieces.class.getResourceAsStream(Binaries.CARRIED + name)) {
            if (in == null) {
                throw new IllegalStateException("no table carried at " + Binaries.CARRIED + name
                        + ": the build's binaries step writes it");
            }
            return read(new String(in.readAllBytes(), StandardCharsets.US_ASCII));
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /** The table as text: a line a piece, a mask, a twin, an align and a
     *  field. */
    String text() {
        StringBuilder out = new StringBuilder();
        for (Piece piece : pieces) {
            out.append("piece ").append(piece.name()).append(' ').append(piece.from())
                    .append(' ').append(piece.to()).append('\n');
        }
        for (Mask mask : masks) {
            out.append("mask ").append(mask.at()).append(' ').append(mask.prefix())
                    .append('\n');
        }
        for (Twin twin : twins) {
            out.append("twin ").append(twin.copy()).append(' ').append(twin.first())
                    .append('\n');
        }
        for (Align align : aligns) {
            out.append("align ").append(align.gap()).append(' ').append(align.base())
                    .append(' ').append(align.anchor()).append(' ').append(align.add());
            if (align.word() >= 0) {
                out.append(' ').append(align.word());
            }
            out.append('\n');
        }
        for (Field field : fields) {
            out.append("field ").append(field.at()).append(' ').append(field.width());
            for (Map.Entry<String, Integer> move : field.moves().entrySet()) {
                out.append(' ').append(move.getKey()).append(move.getValue() < 0 ? "" : "+")
                        .append(move.getValue());
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static final Pattern MOVE = Pattern.compile("([a-z][a-z0-9]*_[0-9]+)([+-][0-9]+)");

    /**
     * A table out of its text.
     *
     * @throws IllegalArgumentException for a line of another shape
     */
    static Pieces read(String text) {
        List<Piece> pieces = new ArrayList<>();
        List<Mask> masks = new ArrayList<>();
        List<Twin> twins = new ArrayList<>();
        List<Align> aligns = new ArrayList<>();
        List<Field> fields = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] word = line.split(" ");
            if (word[0].equals("piece") && word.length == 4) {
                pieces.add(new Piece(word[1], Integer.parseInt(word[2]),
                        Integer.parseInt(word[3])));
            } else if (word[0].equals("mask") && word.length == 3) {
                masks.add(new Mask(word[2], Integer.parseInt(word[1])));
            } else if (word[0].equals("twin") && word.length == 3) {
                twins.add(new Twin(word[1], word[2]));
            } else if (word[0].equals("align") && (word.length == 5 || word.length == 6)) {
                aligns.add(new Align(word[1], Integer.parseInt(word[2]),
                        Integer.parseInt(word[3]), Integer.parseInt(word[4]),
                        word.length == 6 ? Integer.parseInt(word[5]) : -1));
            } else if (word[0].equals("field") && word.length >= 4) {
                Map<String, Integer> moves = new TreeMap<>();
                for (int i = 3; i < word.length; i++) {
                    Matcher move = MOVE.matcher(word[i]);
                    if (!move.matches()) {
                        throw new IllegalArgumentException("a field's move reads " + word[i]);
                    }
                    moves.put(move.group(1), Integer.parseInt(move.group(2)));
                }
                fields.add(new Field(Integer.parseInt(word[1]), Integer.parseInt(word[2]), moves));
            } else if (!line.isEmpty()) {
                throw new IllegalArgumentException("a line of a table reads " + line);
            }
        }
        return new Pieces(List.copyOf(pieces), List.copyOf(masks), List.copyOf(twins),
                List.copyOf(aligns), List.copyOf(fields));
    }

    /** One assembly: the bytes, and the labels of the text section with
     *  their offsets, out of rmac's listing. */
    record Assembly(byte[] code, Map<String, Integer> labels) {
    }

    /**
     * The table of one core, cut from its assembly with a pad past each
     * piece (doc/LINKER.md, The library).
     *
     * @throws IllegalStateException where rmac fails, or a pad moves the
     *         core in a way a field does not explain
     */
    static Pieces cut(Path rmac, Path sources, Binaries.Binary binary) throws IOException {
        Assembly base = assemble(rmac, sources, binary, List.of(), true);
        byte[] core = base.code();
        List<Piece> pieces = new ArrayList<>();
        for (Map.Entry<String, Integer> label : base.labels().entrySet()) {
            if (label.getKey().startsWith("ymxr_p_")) {
                String name = label.getKey().substring("ymxr_p_".length());
                Integer to = base.labels().get("ymxr_e_" + name);
                if (to == null) {
                    throw new IllegalStateException("piece " + name + " has no end");
                }
                pieces.add(new Piece(name, label.getValue(), to));
            }
        }
        // a gap of 0 bytes before the piece at its end
        pieces.sort(Comparator.comparingInt(Piece::from).thenComparingInt(Piece::to));
        List<Mask> masks = new ArrayList<>();
        for (Map.Entry<String, Integer> label : base.labels().entrySet()) {
            if (label.getKey().startsWith("ymxr_k_")) {
                masks.add(new Mask(label.getKey().substring("ymxr_k_".length()),
                        label.getValue()));
            }
        }
        masks.sort(Comparator.comparingInt(Mask::at));
        Map<Integer, Integer> widths = new TreeMap<>();
        Map<Integer, Map<String, Integer>> moves = new TreeMap<>();
        // A piece of the second bank and the piece of the first it copies
        // keep one layout: the cut pads both at once, and the first one's
        // coefficient is the sum of the two, which a link that leaves both
        // out moves a field by with the one length.
        Map<String, List<Piece>> twins = new LinkedHashMap<>();
        Set<String> copies = new java.util.HashSet<>();
        for (Piece piece : pieces) {
            twins.put(piece.name(), new ArrayList<>(List.of(piece)));
        }
        Map<String, Piece> named = new LinkedHashMap<>();
        for (Piece piece : pieces) {
            named.put(piece.name(), piece);
        }
        for (String label : base.labels().keySet()) {
            if (!label.startsWith("ymxr_t_")) {
                continue;
            }
            String[] two = label.substring("ymxr_t_".length()).split("__");
            Piece piece = named.get(two[0]);
            Piece first = two.length == 2 ? named.get(two[1]) : null;
            if (piece == null || first == null) {
                throw new IllegalStateException(binary.name() + ": the twin label " + label
                        + " names no two pieces");
            }
            if (first.size() != piece.size()) {
                throw new IllegalStateException(binary.name() + ": piece " + piece.name()
                        + " is " + piece.size() + " bytes, and " + first.name() + ", which it"
                        + " copies, " + first.size());
            }
            twins.computeIfAbsent(first.name(), k -> new ArrayList<>()).add(piece);
            copies.add(piece.name());
        }
        // A core whose padding keeps an offset modulo 256, the lookup's, is
        // padded 256 and 512 bytes, which leave that padding as it is: a
        // field is then a word, its high byte the byte that moves.
        List<Align> aligns = new ArrayList<>();
        for (Piece piece : pieces) {
            Integer at = base.labels().get("ymxr_a_" + piece.name());
            Integer anchor = base.labels().get("ymxr_r_" + piece.name());
            Integer add = base.labels().get("ymxr_n_" + piece.name());
            Integer word = base.labels().get("ymxr_w_" + piece.name());
            if (at != null && anchor != null && add != null) {
                aligns.add(new Align(piece.name(), at - piece.to(), anchor, add,
                        word == null ? -1 : word));
            }
        }
        boolean aligned = !aligns.isEmpty();
        int smallPad = aligned ? WIDE_PAD : PAD;
        int widePad = aligned ? 2 * WIDE_PAD : WIDE_PAD;
        for (Piece piece : pieces) {
            if (copies.contains(piece.name())) {
                continue;
            }
            List<Piece> padding = twins.getOrDefault(piece.name(), List.of(piece));
            byte[] small = padded(rmac, sources, binary, padding, smallPad, core.length);
            byte[] wide = padded(rmac, sources, binary, padding, widePad, core.length);
            boolean[] taken = new boolean[core.length];
            for (int i = 0; i < core.length; i++) {
                if (small[i] == core[i] || taken[i]) {
                    continue;
                }
                // a word where the byte before it, or this byte, moves with
                // the wide pad; a byte otherwise; in an aligned core, a word
                // at an even offset, whose high byte moves
                int at;
                int width;
                if (aligned) {
                    if ((i & 1) != 0) {
                        throw new IllegalStateException(binary.name() + ": the byte at " + i
                                + " moves with a pad of " + smallPad + " past " + piece.name()
                                + ", and a field of an aligned core is a word");
                    }
                    at = i;
                    width = 2;
                } else if (i > 0 && wide[i - 1] != core[i - 1] && !taken[i - 1]) {
                    at = i - 1;
                    width = 2;
                } else if (wide[i] != core[i]) {
                    at = i;
                    width = 2;
                } else {
                    at = i;
                    width = 1;
                }
                // a move read modulo the field's width: in a core past 32 KB
                // a pad moves a field past the range of a signed word, and a
                // link that leaves pieces out moves it back
                int was = value(core, at, width);
                int c = wrapped(value(small, at, width) - was, width);
                int far = wrapped(value(wide, at, width) - was, width);
                if (c % smallPad != 0 || far != c / smallPad * widePad) {
                    throw new IllegalStateException(binary.name() + ": the field at " + at
                            + " moves " + c + " with a pad of " + smallPad + " past "
                            + piece.name() + " and " + far + " with " + widePad);
                }
                Integer known = widths.putIfAbsent(at, width);
                if (known != null && known != width) {
                    throw new IllegalStateException(binary.name() + ": the field at " + at
                            + " is " + known + " bytes past one piece and " + width
                            + " past " + piece.name());
                }
                moves.computeIfAbsent(at, k -> new TreeMap<>()).put(piece.name(), c / smallPad);
                for (int j = at; j < at + width; j++) {
                    taken[j] = true;
                }
            }
            for (int i = 0; i < core.length; i++) {
                if (wide[i] != core[i] && !taken[i]) {
                    throw new IllegalStateException(binary.name() + ": the byte at " + i
                            + " moves with a pad of " + widePad + " past " + piece.name()
                            + " and with " + smallPad + " not");
                }
            }
        }
        List<Field> fields = new ArrayList<>();
        for (Map.Entry<Integer, Integer> width : widths.entrySet()) {
            fields.add(new Field(width.getKey(), width.getValue(),
                    new LinkedHashMap<>(moves.get(width.getKey()))));
        }
        List<Twin> twinned = new ArrayList<>();
        for (Map.Entry<String, List<Piece>> twin : twins.entrySet()) {
            for (Piece copy : twin.getValue()) {
                if (!copy.name().equals(twin.getKey())) {
                    twinned.add(new Twin(copy.name(), twin.getKey()));
                }
            }
        }
        return new Pieces(List.copyOf(pieces), List.copyOf(masks), List.copyOf(twinned),
                List.copyOf(aligns), List.copyOf(fields));
    }

    /** The core assembled with a pad of {@code pad} bytes past each of the
     *  pieces, the pads cut out again, so each byte is at its offset in the
     *  core. */
    private static byte[] padded(Path rmac, Path sources, Binaries.Binary binary,
                                 List<Piece> padding, int pad, int length) throws IOException {
        List<String> defines = new ArrayList<>(List.of("-dYMXR_PADSIZE=" + pad));
        for (Piece piece : padding) {
            defines.add("-dYMXR_PAD_" + piece.name() + "=1");
        }
        byte[] code = assemble(rmac, sources, binary, defines, false).code();
        String name = padding.get(0).name();
        if (code.length != length + pad * padding.size()) {
            throw new IllegalStateException(binary.name() + " is "
                    + (code.length - length) + " bytes longer with a pad of " + pad
                    + " past " + name + " and its copies: a branch past it has the size rmac"
                    + " picks by its distance");
        }
        List<Piece> order = new ArrayList<>(padding);
        order.sort(Comparator.comparingInt(Piece::to));
        byte[] out = new byte[length];
        int from = 0;
        int to = 0;
        for (Piece piece : order) {
            System.arraycopy(code, from, out, to, piece.to() - to);
            from += piece.to() - to + pad;
            to = piece.to();
        }
        System.arraycopy(code, from, out, to, length - to);
        return out;
    }

    /** A difference of two values of a field, read as a signed value of the
     *  field's width. */
    private static int wrapped(int difference, int width) {
        return width == 1 ? (byte) difference : (short) difference;
    }

    /** The signed value of a field. */
    static int value(byte[] code, int at, int width) {
        return width == 1 ? code[at] : (short) Tune.getWord(code, at);
    }

    /**
     * rmac's assembly of a binary with more defines, and with its listing
     * where {@code listed}, read for the labels of the text section.
     */
    static Assembly assemble(Path rmac, Path sources, Binaries.Binary binary, List<String> more,
                             boolean listed) throws IOException {
        Path work = Files.createTempDirectory("ymxr68k");
        try {
            Path out = work.resolve(binary.name());
            Path list = work.resolve("listing");
            List<String> command = new ArrayList<>(
                    List.of(rmac.toString(), "-m68000", "-fr", "-i" + sources));
            command.addAll(binary.defines());
            command.addAll(more);
            if (listed) {
                command.add("-l" + list);
            }
            command.add("-o");
            command.add(out.toString());
            command.add(sources.resolve(binary.source()).toString());
            Process run = new ProcessBuilder(command).redirectErrorStream(true).start();
            byte[] said = run.getInputStream().readAllBytes();
            if (run.waitFor() != 0 || !Files.exists(out)) {
                throw new IllegalStateException(rmac + " gave "
                        + new String(said, StandardCharsets.UTF_8).trim());
            }
            Map<String, Integer> labels = new LinkedHashMap<>();
            if (listed) {
                // the symbol table at the listing's end: a name, its value in
                // sixteen hex digits, and t for a label of the text section
                for (String line : Files.readAllLines(list, StandardCharsets.ISO_8859_1)) {
                    String[] part = line.trim().split("\\s+");
                    for (int i = 0; i + 2 < part.length; i += 3) {
                        boolean text = part[i + 2].equals("t");
                        boolean added = part[i + 2].equals("a") && part[i].startsWith("ymxr_n_");
                        if ((text || added) && part[i + 1].length() == 16) {
                            try {
                                labels.putIfAbsent(part[i], (int) Long.parseLong(part[i + 1], 16));
                            } catch (NumberFormatException notOne) {
                                // a line of code, not of the symbol table
                            }
                        }
                    }
                }
            }
            return new Assembly(Files.readAllBytes(out), labels);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stopped);
        } finally {
            try (Stream<Path> tree = Files.walk(work)) {
                for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
