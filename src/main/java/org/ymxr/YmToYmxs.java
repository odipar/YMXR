package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
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
        // The call is read before the input is, so a wrong call is an
        // exit of 2 and standard input is left unread.
        OptionalInt asked = asked(tool, flags);
        Report report = new Report(tool.reports());
        YmDump.Song song;
        String text;
        Sources sources;
        try {
            song = YmDump.read(tool.bytes());
            int repeat = repeat(tool, asked, song, report);
            sources = new Sources(song);
            text = Text.write(Tunes.multi(Ym.read(song, sources, repeat, report)));
        } catch (YmDump.FormatException | IllegalArgumentException | IllegalStateException no) {
            throw tool.wrong(Tool.WRONG, String.valueOf(no.getMessage()));
        }
        tool.report(song.format() + " \"" + song.name().strip() + "\", " + song.frames()
                + " rows at " + song.playerHz() + " Hz, " + sources.count() + " sources");
        for (String note : report.unsaid()) {
            System.err.println("  " + note);
        }
        tool.write(text);
    }

    /** {@code -r}, a tune that plays once, in the place of a row. */
    static final int ONCE = -1;

    /** The row the call asks the tune to repeat to: {@code -rROW},
     *  {@link #ONCE} for {@code -r}, and empty where the call names
     *  neither. Read before the input, so a flag this does not read is an
     *  exit of 2 and the input is left unread. */
    static OptionalInt asked(Tool tool, List<String> flags) {
        Ymxs.only(tool, flags, Ymxs.ROWS);
        for (String flag : flags) {
            if (flag.equals("-r")) {
                return OptionalInt.of(ONCE);
            }
            try {
                return OptionalInt.of(Integer.parseInt(flag.substring(2)));
            } catch (NumberFormatException no) {
                throw tool.usage("not a row number: " + flag);
            }
        }
        return OptionalInt.empty();
    }

    /** The row the tune repeats to: the row the call names, the dump's
     *  loop frame, or its frame count where the tune plays once. */
    static int repeat(Tool tool, OptionalInt asked, YmDump.Song song, Report report) {
        if (asked.isPresent()) {
            int at = asked.getAsInt();
            if (at == ONCE) {
                return (int) song.frames();
            }
            if (at > song.frames()) {
                throw tool.wrong(Tool.WRONG, "the repeat row " + at
                        + " is past the dump's " + song.frames() + " frames");
            }
            return at;
        }
        if (song.loopFrame() >= song.frames()) {
            report.note("the dump's loop frame " + song.loopFrame()
                    + " is past its last frame: the tune plays once");
        }
        return (int) Math.min(song.loopFrame(), song.frames());
    }
}
