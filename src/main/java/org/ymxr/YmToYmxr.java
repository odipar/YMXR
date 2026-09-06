package org.ymxr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A YM5!/YM6! dump into a tune file.
 *
 * <pre>
 * ym-to-ymxr in.ym out.ymxr [-kK] [-mN] [-rRR]
 * </pre>
 *
 * {@code -k} is the unit the table packs at, 2 by default; {@code -m} the
 * ring in bytes, 960; {@code -r} the row the tune repeats to, the dump's
 * loop frame by default, and {@code -r} alone a tune that plays once.
 */
public final class YmToYmxr {

    /** The unit a table packs at unless {@code -k} names another. */
    static final int UNIT = 2;

    private YmToYmxr() {
    }

    /** A conversion: the tune file written, the dump's frame the tune
     *  repeats to, or its frame count where it plays once, and what the
     *  tool prints of it. */
    record Converted(Tune.Written written, int repeat, Sources sources, String said) {
    }

    /** The dump converted with the tool's flags, {@code -kK}, {@code -mN},
     *  {@code -rRR} or {@code -r}, as the tool does it. */
    static Converted convert(byte[] dump, List<String> flags, Report report) {
        return convert(YmDump.read(dump), flags, report);
    }

    static Converted convert(YmDump.Song song, List<String> flags, Report report) {
        int unit = UNIT;
        int ring = Tune.RING;
        int repeat = -1;
        boolean once = false;
        for (String flag : flags) {
            if (flag.startsWith("-k")) {
                unit = Integer.parseInt(flag.substring(2));
            } else if (flag.startsWith("-m")) {
                ring = Integer.parseInt(flag.substring(2));
            } else if (flag.equals("-r")) {
                once = true;
            } else if (flag.startsWith("-r")) {
                repeat = Integer.parseInt(flag.substring(2));
            } else {
                throw new IllegalArgumentException("not a flag of the tool: " + flag);
            }
        }
        if (once) {
            repeat = song.frames();
        } else if (repeat < 0) {
            repeat = (int) Math.min(song.loopFrame(), song.frames());
            if (song.loopFrame() >= song.frames()) {
                report.note("the dump's loop frame " + song.loopFrame()
                        + " is past its last frame: the tune plays once");
            }
        }
        Sources sources = new Sources(song);
        Columns columns = new Columns(song, sources, repeat, report);
        Tune.Written written = Tune.write(columns, sources, song.playerHz(), unit, ring, report);
        String said = song.frames() + " frames at " + song.playerHz() + " Hz, "
                + sources.count() + " sources, effects " + Integer.toBinaryString(columns.effects)
                + ", repeats at " + (repeat < song.frames() ? "row " + written.repeat() : "no row")
                + ": " + written.file().length + " bytes";
        return new Converted(written, repeat, sources, said);
    }

    public static void main(String[] args) throws IOException {
        List<String> flags = new ArrayList<>();
        String in = null;
        String out = null;
        for (String arg : args) {
            if (arg.startsWith("-")) {
                flags.add(arg);
            } else if (in == null) {
                in = arg;
            } else if (out == null) {
                out = arg;
            } else {
                usage();
                return;
            }
        }
        if (in == null || out == null) {
            usage();
            return;
        }
        Report report = new Report();
        Converted converted;
        try {
            converted = convert(Files.readAllBytes(Path.of(in)), flags, report);
        } catch (IllegalArgumentException wrong) {
            System.err.println(wrong.getMessage());
            usage();
            return;
        }
        Files.write(Path.of(out), converted.written().file());
        System.out.println(converted.said());
        for (String note : report.notes()) {
            System.out.println("  " + note);
        }
    }

    private static void usage() {
        System.err.println("ym-to-ymxr in.ym out.ymxr [-kK] [-mN] [-rRR | -r]");
    }
}
