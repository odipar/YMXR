package org.ymxr;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import org.ymxs.tool.Tool;

/**
 * The record of BINARIES.md 6: a file this repository writes read back
 * and reported part by part, one line of JSON a part. The reader here
 * reads the file alone, as an implementer of the document does, so a
 * record that differs from the reference is the document read two ways.
 *
 * <p>{@code ymxr-layout} (tools.md 9.5) writes it.
 */
final class Layout {

    private Layout() {
    }

    /** The tool: a file on standard input, its record on standard
     *  output. */
    public static void main(String[] args) {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxr-layout", flags);
        byte[] file = tool.bytes();
        Report report = new Report(tool.reports());
        String record;
        try {
            record = of(file);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException wrong) {
            throw tool.wrong(Tool.WRONG, wrong instanceof IllegalArgumentException
                    ? String.valueOf(wrong.getMessage())
                    : "the record runs past the file's " + file.length + " bytes");
        }
        List<String> lines = record.lines().toList();
        report.say("the file: " + file.length + " bytes, kind " + kind(file));
        for (String part : List.of("tag", "tune", "source", "image")) {
            long of = lines.stream().filter(line -> line.contains("\"part\":\"" + part + "\""))
                    .count();
            if (of > 0) {
                report.row("the " + part + (of == 1 ? "" : "s"), String.valueOf(of));
            }
        }
        tool.report(lines.size() + (lines.size() == 1 ? " line" : " lines"));
        Out.write(tool, record.getBytes(StandardCharsets.US_ASCII));
    }

    /** The record of the file: 6.1's first line, then the lines of 6.2
     *  to 6.5 by kind, each ended by a line feed. */
    static String of(byte[] file) {
        StringBuilder out = new StringBuilder();
        String kind = kind(file);
        out.append("{\"kind\":\"").append(kind).append("\",\"bytes\":").append(file.length)
                .append("}\n");
        switch (kind) {
            case "multi" -> multi(out, file);
            case "bound" -> bound(out, file);
            case "program" -> program(out, file);
            default -> sndh(out, file, 0, file.length);
        }
        return out.toString();
    }

    /** The kind of 6.1, read off the file's first bytes. */
    private static String kind(byte[] file) {
        if (ascii(file, 0, 4).equals("YMXM")) {
            return "multi";
        }
        if (ascii(file, 0, 4).equals("YMXB")) {
            return "bound";
        }
        if (file.length >= 2 && word(file, 0) == Prg.PRG_MAGIC) {
            return "program";
        }
        if (ascii(file, 12, 4).equals("SNDH")) {
            return "sndh";
        }
        throw new IllegalArgumentException("not a file BINARIES.md defines: no YMXM, YMXB,"
                + " $601A or SNDH");
    }

    /** 6.2: the header, then a line a tune. */
    private static void multi(StringBuilder out, byte[] file) {
        int tunes = word(file, 6);
        out.append("{\"part\":\"header\",\"version\":").append(word(file, 4))
                .append(",\"tunes\":").append(tunes).append("}\n");
        int name = 8 + 8 * tunes;
        for (int i = 0; i < tunes; i++) {
            int to = zero(file, name);
            out.append("{\"part\":\"tune\",\"number\":").append(i + 1)
                    .append(",\"at\":").append(Tune.getLong(file, 8 + 8 * i))
                    .append(",\"bytes\":").append(Tune.getLong(file, 12 + 8 * i))
                    .append(",\"name\":").append(text(new String(file, name, to - name,
                            StandardCharsets.UTF_8)))
                    .append("}\n");
            name = to + 1;
        }
    }

    /** 6.3: the header, a line a source, and the image of a bound tune
     *  written alone. */
    private static void bound(StringBuilder out, byte[] file) {
        out.append("{\"part\":\"header\"").append(fields(file, 0)).append("}\n");
        sources(out, file, 0);
        int image = Tune.getLong(file, 16);
        if (image > 0) {
            out.append("{\"part\":\"image\",\"at\":").append(image)
                    .append(",\"bytes\":").append(file.length - image).append("}\n");
        }
    }

    /** 6.5: the PRG header, the stub, where the SNDH file begins, and
     *  that file's lines. The SNDH file ends at 28 plus the long at 2,
     *  the relocation table standing after it. */
    private static void program(StringBuilder out, byte[] file) {
        int at = 28;
        while (!ascii(file, at + 12, 4).equals("SNDH")) {
            at += 2;
        }
        out.append("{\"part\":\"prg\",\"text\":").append(Tune.getLong(file, 2)).append("}\n");
        int version = word(file, 28 + 8);
        out.append("{\"part\":\"stub\",\"at\":28,\"bytes\":").append(at - 28)
                .append(",\"version\":").append(version)
                .append(",\"subtunes\":").append(word(file, 28 + 10))
                .append(",\"flags\":").append(word(file, 28 + 12))
                .append(",\"rate\":").append(word(file, 28 + 14))
                .append(",\"rows\":").append(Tune.getLong(file, 28 + 16))
                .append(",\"core\":").append(at + Tune.getLong(file, 28 + 20));
        if (version >= 2) {
            // the timer the stub arms (4.10), which a program of an
            // earlier version has no field for
            out.append(",\"prescaler\":").append(word(file, 28 + Prg.STUB_PRESCALER_AT))
                    .append(",\"count\":").append(word(file, 28 + Prg.STUB_COUNT_AT))
                    .append(",\"ticks\":").append(word(file, 28 + Prg.STUB_TICKS_AT));
        }
        out.append("}\n");
        out.append("{\"part\":\"sndh\",\"at\":").append(at).append("}\n");
        sndh(out, file, at, 28 + Tune.getLong(file, 2));
    }

    /** 6.4: the entry triple, the tags, the core, the subtune table, a
     *  subtune and its sources a line each, a line an image, and the
     *  workspace. The SNDH file runs from {@code from} to {@code ends},
     *  and every offset counts from the file's first byte. */
    private static void sndh(StringBuilder out, byte[] file, int from, int ends) {
        out.append("{\"part\":\"entry\",\"to\":[");
        for (int i = 0; i < 3; i++) {
            out.append(i == 0 ? "" : ",").append(from + 4 * i + 2
                    + (short) word(file, from + 4 * i + 2));
        }
        out.append("]}\n");
        int hdns = tags(out, file, from);
        int core = Sndh.even(hdns + 4);
        int table = core + Tune.getLong(file, core + 28);
        int work = core + Tune.getLong(file, core + 32);
        out.append("{\"part\":\"core\",\"at\":").append(core)
                .append(",\"version\":").append(word(file, core + 16))
                .append(",\"binds\":").append(word(file, core + 18))
                .append(",\"fixed\":").append(word(file, core + 20))
                .append(",\"flags\":").append(word(file, core + 22))
                .append(",\"state\":").append(core + word(file, core + 24))
                .append(",\"subtunes\":").append(table)
                .append(",\"work\":").append(work)
                .append("}\n");
        int n = word(file, table);
        List<Integer> tunes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            tunes.add(core + Tune.getLong(file, table + 2 + 4 * i));
        }
        out.append("{\"part\":\"subtunes\",\"at\":").append(table).append(",\"tunes\":[");
        for (int i = 0; i < n; i++) {
            out.append(i == 0 ? "" : ",").append(tunes.get(i));
        }
        out.append("]}\n");
        TreeSet<Integer> images = new TreeSet<>();
        for (int at : tunes) {
            images.add(at + Tune.getLong(file, at + 16));
        }
        for (int i = 0; i < n; i++) {
            int at = tunes.get(i);
            int upto = i + 1 < n ? tunes.get(i + 1) : images.first();
            out.append("{\"part\":\"tune\",\"number\":").append(i + 1)
                    .append(",\"at\":").append(at)
                    .append(",\"bytes\":").append(upto - at)
                    .append(fields(file, at)).append("}\n");
            sources(out, file, at);
        }
        int number = 1;
        for (int image : images) {
            out.append("{\"part\":\"image\",\"number\":").append(number++)
                    .append(",\"at\":").append(image).append("}\n");
        }
        out.append("{\"part\":\"workspace\",\"at\":").append(work)
                .append(",\"bytes\":").append(ends - work).append("}\n");
    }

    /** The tags of 3.2, a line each from 16 to HDNS, and where HDNS
     *  stands. */
    private static int tags(StringBuilder out, byte[] file, int from) {
        int at = from + 16;
        int subtunes = 0;
        while (true) {
            if (file[at] == 0) {
                at++;
                continue;
            }
            String name = ascii(file, at, 4);
            if (name.equals("HDNS")) {
                out.append("{\"part\":\"tag\",\"name\":\"HDNS\",\"at\":").append(at)
                        .append("}\n");
                return at;
            }
            if (name.startsWith("##")) {
                subtunes = Integer.parseInt(name.substring(2));
                out.append("{\"part\":\"tag\",\"name\":\"##\",\"at\":").append(at)
                        .append(",\"subtunes\":").append(subtunes).append("}\n");
                at += 5;
            } else if (name.startsWith(Sndh.TIMER_C_CLOCK) || name.startsWith(Sndh.VBL_CLOCK)) {
                int to = zero(file, at + 2);
                out.append("{\"part\":\"tag\",\"name\":\"").append(name, 0, 2)
                        .append("\",\"at\":").append(at).append(",\"rate\":")
                        .append(Integer.parseInt(ascii(file, at + 2, to - at - 2)))
                        .append("}\n");
                at = to + 1;
            } else if (name.equals("FRMS")) {
                out.append("{\"part\":\"tag\",\"name\":\"FRMS\",\"at\":").append(at)
                        .append(",\"frames\":[");
                for (int i = 0; i < subtunes; i++) {
                    out.append(i == 0 ? "" : ",").append(Tune.getLong(file, at + 4 + 4 * i));
                }
                out.append("]}\n");
                at += 4 + 4 * subtunes;
            } else if (name.equals("!#SN")) {
                out.append("{\"part\":\"tag\",\"name\":\"!#SN\",\"at\":").append(at)
                        .append(",\"names\":[");
                int named = at + 4 + 2 * subtunes;
                for (int i = 0; i < subtunes; i++) {
                    int to = zero(file, named);
                    out.append(i == 0 ? "" : ",").append(text(new String(file, named,
                            to - named, StandardCharsets.UTF_8)));
                    named = to + 1;
                }
                out.append("]}\n");
                at = named;
            } else {
                int to = zero(file, at + 4);
                out.append("{\"part\":\"tag\",\"name\":\"").append(name).append("\",\"at\":")
                        .append(at).append(",\"text\":")
                        .append(text(ascii(file, at + 4, to - at - 4))).append("}\n");
                at = to + 1;
            }
        }
    }

    /** A line a source of the bound tune at {@code at}, in index
     *  order. */
    private static void sources(StringBuilder out, byte[] file, int at) {
        int sources = file[at + 9] & 0xFF;
        for (int i = 0; i < sources; i++) {
            out.append("{\"part\":\"source\",\"number\":").append(i + 1)
                    .append(",\"at\":").append(at + Tune.getLong(file, at + 24 + 4 * i))
                    .append("}\n");
        }
    }

    /** The fields of 1.2 a record reports of the bound tune at
     *  {@code at}, its image resolved against the file. */
    private static String fields(byte[] file, int at) {
        return ",\"version\":" + word(file, at + 4)
                + ",\"rate\":" + word(file, at + 6)
                + ",\"effects\":" + (file[at + 8] & 0xFF)
                + ",\"sources\":" + (file[at + 9] & 0xFF)
                + ",\"state\":" + Tune.getLong(file, at + 12)
                + ",\"image\":" + (at + Tune.getLong(file, at + 16))
                + ",\"table\":" + Tune.getLong(file, at + 20);
    }

    /** Where the next zero byte from {@code from} stands. */
    private static int zero(byte[] file, int from) {
        int at = from;
        while (file[at] != 0) {
            at++;
        }
        return at;
    }

    /** JSON text: the quotes, a backslash before a quote and a
     *  backslash, and every character above $7E escaped, so the record
     *  reads US-ASCII (6.1). A byte sequence outside UTF-8 reads as
     *  U+FFFD, the replacement character, and escapes as $FFFD. */
    private static String text(String said) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < said.length(); i++) {
            char c = said.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c > 0x7E) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static String ascii(byte[] file, int at, int bytes) {
        return at + bytes > file.length ? ""
                : new String(file, at, bytes, StandardCharsets.US_ASCII);
    }

    private static int word(byte[] file, int at) {
        return Tune.getWord(file, at);
    }
}
