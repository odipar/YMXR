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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The library of a core (doc/LINKER.md): its pieces, the runs of bytes a
 * player linked for one song leaves out, and its fields, the values that
 * move where a piece is left out. The build cuts a table from the
 * assembly of each core without the deferred start and the lookup, and
 * writes it beside the core as {@code <core>.pieces}.
 *
 * @param pieces the pieces in the core's order
 * @param masks the words a link writes the blocks it keeps into
 * @param fields the fields in the core's order
 */
record Pieces(List<Piece> pieces, List<Mask> masks, List<Field> fields) {

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
     * A field: its offset, its width in bytes, and for each piece whose end
     * it refers across, the coefficient c: a link that leaves the piece out
     * moves the value by -c times the piece's bytes.
     */
    record Field(int at, int width, Map<String, Integer> moves) {
    }

    /** Whether the build cuts a table from this core: one without the
     *  deferred start and the lookup. */
    static boolean cuts(Binaries.Binary binary) {
        return binary.source().equals(Binaries.CORE.source())
                && !binary.defines().contains("-dYMXR_DEFER=1")
                && !binary.defines().contains("-dYMXR_MAP=1");
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

    /** The table as text, a line a piece and then a line a field. */
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
        List<Field> fields = new ArrayList<>();
        for (String line : text.split("\n")) {
            String[] word = line.split(" ");
            if (word[0].equals("piece") && word.length == 4) {
                pieces.add(new Piece(word[1], Integer.parseInt(word[2]),
                        Integer.parseInt(word[3])));
            } else if (word[0].equals("mask") && word.length == 3) {
                masks.add(new Mask(word[2], Integer.parseInt(word[1])));
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
        return new Pieces(List.copyOf(pieces), List.copyOf(masks), List.copyOf(fields));
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
        pieces.sort(Comparator.comparingInt(Piece::from));
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
        for (Piece piece : pieces) {
            byte[] small = padded(rmac, sources, binary, piece, PAD, core.length);
            byte[] wide = padded(rmac, sources, binary, piece, WIDE_PAD, core.length);
            boolean[] taken = new boolean[core.length];
            for (int i = 0; i < core.length; i++) {
                if (small[i] == core[i] || taken[i]) {
                    continue;
                }
                // a word where the byte before it, or this byte, moves with
                // the wide pad; a byte otherwise
                int at;
                int width;
                if (i > 0 && wide[i - 1] != core[i - 1] && !taken[i - 1]) {
                    at = i - 1;
                    width = 2;
                } else if (wide[i] != core[i]) {
                    at = i;
                    width = 2;
                } else {
                    at = i;
                    width = 1;
                }
                int was = value(core, at, width);
                int c = value(small, at, width) - was;
                if (c % PAD != 0 || value(wide, at, width) - was != c / PAD * WIDE_PAD) {
                    throw new IllegalStateException(binary.name() + ": the field at " + at
                            + " moves " + c + " with a pad of " + PAD + " past " + piece.name()
                            + " and " + (value(wide, at, width) - was) + " with " + WIDE_PAD);
                }
                Integer known = widths.putIfAbsent(at, width);
                if (known != null && known != width) {
                    throw new IllegalStateException(binary.name() + ": the field at " + at
                            + " is " + known + " bytes past one piece and " + width
                            + " past " + piece.name());
                }
                moves.computeIfAbsent(at, k -> new TreeMap<>()).put(piece.name(), c / PAD);
                for (int j = at; j < at + width; j++) {
                    taken[j] = true;
                }
            }
            for (int i = 0; i < core.length; i++) {
                if (wide[i] != core[i] && !taken[i]) {
                    throw new IllegalStateException(binary.name() + ": the byte at " + i
                            + " moves with a pad of " + WIDE_PAD + " past " + piece.name()
                            + " and with " + PAD + " not");
                }
            }
        }
        List<Field> fields = new ArrayList<>();
        for (Map.Entry<Integer, Integer> width : widths.entrySet()) {
            fields.add(new Field(width.getKey(), width.getValue(),
                    new LinkedHashMap<>(moves.get(width.getKey()))));
        }
        return new Pieces(List.copyOf(pieces), List.copyOf(masks), List.copyOf(fields));
    }

    /** The core assembled with a pad of {@code pad} bytes past the piece,
     *  the pad cut out again, so each byte is at its offset in the core. */
    private static byte[] padded(Path rmac, Path sources, Binaries.Binary binary, Piece piece,
                                 int pad, int length) throws IOException {
        byte[] code = assemble(rmac, sources, binary,
                List.of("-dYMXR_PAD_" + piece.name() + "=1", "-dYMXR_PADSIZE=" + pad), false)
                .code();
        if (code.length != length + pad) {
            throw new IllegalStateException(binary.name() + " is " + (code.length - length)
                    + " bytes longer with a pad of " + pad + " past " + piece.name()
                    + ": a branch past it has the size rmac picks by its distance");
        }
        byte[] out = new byte[length];
        System.arraycopy(code, 0, out, 0, piece.to());
        System.arraycopy(code, piece.to() + pad, out, piece.to(), length - piece.to());
        return out;
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
                        if (part[i + 2].equals("t") && part[i + 1].length() == 16) {
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
