package org.ymxr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    public static void main(String[] args) throws IOException {
        int unit = UNIT;
        int ring = Tune.RING;
        int repeat = -1;
        boolean once = false;
        String in = null;
        String out = null;
        for (String arg : args) {
            if (arg.startsWith("-k")) {
                unit = Integer.parseInt(arg.substring(2));
            } else if (arg.startsWith("-m")) {
                ring = Integer.parseInt(arg.substring(2));
            } else if (arg.equals("-r")) {
                once = true;
            } else if (arg.startsWith("-r")) {
                repeat = Integer.parseInt(arg.substring(2));
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
        YmDump.Song song = YmDump.read(Files.readAllBytes(Path.of(in)));
        Report report = new Report();
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
        Files.write(Path.of(out), written.file());
        System.out.println(song.frames() + " frames at " + song.playerHz() + " Hz, "
                + sources.count() + " sources, effects " + Integer.toBinaryString(columns.effects)
                + ", repeats at " + (repeat < song.frames() ? "row " + written.repeat() : "no row")
                + ": " + written.file().length + " bytes");
        for (String note : report.notes()) {
            System.out.println("  " + note);
        }
    }

    private static void usage() {
        System.err.println("ym-to-ymxr in.ym out.ymxr [-kK] [-mN] [-rRR | -r]");
    }
}
