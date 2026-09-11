package org.ymxr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.tool.Tool;

/**
 * {@code ymx-to-ymxs}: a YMX file on standard input, the YMXS structure as
 * JSON on standard output.
 *
 * <p>The first stage of a YMX conversion (doc/ymxs.md), so
 * {@code ymx-to-ymxs | ymxs-to-ymxr} writes the tune file
 * {@code ymx-to-ymxr} writes. The file is read through YMX's
 * {@code ymx-dump}, which {@code YMX_DUMP} names, and that opens a file
 * name rather than a stream, so it is called with the name of this tool's
 * standard input, on that input. No copy of the file is written.
 */
public final class YmxToYmxs {

    private YmxToYmxs() {
    }

    public static void main(String[] args) {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymx-to-ymxs", flags, "-r");
        // The call is read before the input is, as ym-to-ymxs reads it.
        OptionalInt asked = YmToYmxs.asked(tool, flags);
        Report report = new Report(tool.reports());
        String text;
        try {
            text = convert(tool, asked, report);
        } catch (IllegalArgumentException | IllegalStateException wrong) {
            throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
        } catch (YmxToYmxr.FormatException wrong) {
            throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
        } catch (IOException failed) {
            // Running ymx-dump failed, which is the call and not the file.
            throw tool.wrong(Tool.FAILED, String.valueOf(failed.getMessage()));
        }
        for (String note : report.unsaid()) {
            System.err.println("  " + note);
        }
        tool.write(text);
    }

    private static String convert(Tool tool, OptionalInt asked, Report report)
            throws IOException {
        YmxToYmxr.Dumped read = YmxToYmxr.standardInput();
        int frames = YmxToYmxr.frames(read);
        if (frames != read.frames()) {
            report.note((read.frames() - frames) + " frame of YMX's padding comes off the"
                    + " end: a dump's rows are the tune's rows");
        }
        read = new YmxToYmxr.Dumped(frames, read.rate(), read.loopFrame(), read.flags(),
                read.streams(), read.samples(), read.loops());
        int repeat = repeat(asked, read);
        YmDump.Song song = YmxToYmxr.song(read, "");
        tool.report("YMX!: " + read.frames() + " rows at " + read.rate() + " Hz");
        return Text.write(Tunes.multi(Ymx.read(read, song, repeat, report)));
    }

    /** The row the tune repeats to: the row the call names, the file's
     *  loop frame, or its frame count where the tune plays once. */
    private static int repeat(OptionalInt asked, YmxToYmxr.Dumped read) {
        if (asked.isPresent()) {
            int at = asked.getAsInt();
            return at == YmToYmxs.ONCE ? read.frames() : at;
        }
        return YmxToYmxr.repeat(read);
    }
}
