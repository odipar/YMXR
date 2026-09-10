package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.tool.Tool;

/**
 * {@code ym-to-ymxs}: a YM5!/YM6! register dump on standard input, the
 * YMXS structure as JSON on standard output.
 *
 * <p>The first stage of every conversion here (doc/ymxs.md). What comes
 * out is what {@code ym-to-ymxr} converts through, so
 * {@code ym-to-ymxs | ymxs-to-ymxr} writes the tune file that tool
 * writes.
 *
 * <p>{@code -r} produces a tune that plays once, and {@code -rROW} one
 * that repeats to that row; without either, a tune repeats to the frame
 * the dump marks.
 */
public final class YmToYmxs {

    private YmToYmxs() {
    }

    public static void main(String[] args) {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ym-to-ymxs", flags, "-r");
        Report report = new Report(tool.reports());
        YmDump.Song song;
        try {
            song = YmDump.read(tool.bytes());
        } catch (IllegalArgumentException no) {
            throw tool.wrong(Tool.WRONG, String.valueOf(no.getMessage()));
        }
        int repeat = repeat(tool, flags, song, report);
        Sources sources = new Sources(song);
        String text = Text.write(Tunes.multi(Ym.read(song, sources, repeat, report)));
        tool.report(song.format() + " \"" + song.name().strip() + "\", " + song.frames()
                + " rows at " + song.playerHz() + " Hz, " + sources.count() + " sources");
        for (String note : report.unsaid()) {
            System.err.println("  " + note);
        }
        tool.write(text);
    }

    /** The row the tune repeats to: {@code -rROW}, the dump's loop frame,
     *  or its frame count where {@code -r} makes it play once. */
    static int repeat(Tool tool, List<String> flags, YmDump.Song song, Report report) {
        for (String flag : flags) {
            if (flag.equals("-r")) {
                return song.frames();
            }
            if (flag.startsWith("-r")) {
                try {
                    int at = Integer.parseInt(flag.substring(2));
                    if (at > song.frames()) {
                        throw tool.wrong(Tool.WRONG, "the repeat row " + at
                                + " is past the dump's " + song.frames() + " frames");
                    }
                    return at;
                } catch (NumberFormatException no) {
                    throw tool.usage("not a row number: " + flag);
                }
            }
            throw tool.usage("not a flag of the tool: " + flag);
        }
        if (song.loopFrame() >= song.frames()) {
            report.note("the dump's loop frame " + song.loopFrame()
                    + " is past its last frame: the tune plays once");
        }
        return (int) Math.min(song.loopFrame(), song.frames());
    }
}
