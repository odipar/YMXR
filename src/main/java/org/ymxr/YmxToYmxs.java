package org.ymxr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ymxs.Text;
import org.ymxs.Tunes;
import org.ymxs.tool.Tool;

/**
 * {@code ymx-to-ymxs}: a YMX file on standard input, the YMXS structure as
 * JSON on standard output.
 *
 * <p>The first stage of a YMX conversion (doc/ymxs.md), so
 * {@code ymx-to-ymxs | ymxs-to-ymxr} writes the tune file
 * {@code ymx-to-ymxr} writes. The file is read through YMX's own
 * {@code ymx-dump}, which {@code YMX_DUMP} names, and that reads a file
 * rather than a stream, so standard input is written to a temporary file
 * first.
 */
public final class YmxToYmxs {

    private YmxToYmxs() {
    }

    public static void main(String[] args) throws IOException {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymx-to-ymxs", flags, "-r");
        Report report = new Report(tool.reports());
        Path at = Files.createTempFile("ymx-to-ymxs", ".ymx");
        String text;
        try {
            Files.write(at, tool.bytes());
            text = convert(tool, at, flags, report);
        } catch (IllegalArgumentException | IllegalStateException wrong) {
            throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
        } finally {
            Files.deleteIfExists(at);
        }
        for (String note : report.unsaid()) {
            System.err.println("  " + note);
        }
        tool.write(text);
    }

    private static String convert(Tool tool, Path at, List<String> flags, Report report)
            throws IOException {
        YmxToYmxr.Dumped read = YmxToYmxr.dumped(at);
        int frames = YmxToYmxr.frames(read);
        if (frames != read.frames()) {
            report.note((read.frames() - frames) + " frame of YMX's padding comes off the"
                    + " end: a dump's rows are the tune's rows");
        }
        read = new YmxToYmxr.Dumped(frames, read.rate(), read.loopFrame(), read.streams(),
                read.samples(), read.loops());
        int repeat = repeat(tool, flags, read);
        YmDump.Song song = YmxToYmxr.song(read, "");
        tool.report("YMX!: " + read.frames() + " rows at " + read.rate() + " Hz");
        return Text.write(Tunes.multi(Ymx.read(read, song, repeat, report)));
    }

    /** The row the tune repeats to: {@code -rROW}, the file's loop frame,
     *  or its frame count where {@code -r} makes it play once. */
    private static int repeat(Tool tool, List<String> flags, YmxToYmxr.Dumped read) {
        for (String flag : flags) {
            if (flag.equals("-r")) {
                return read.frames();
            }
            if (flag.startsWith("-r")) {
                try {
                    return Integer.parseInt(flag.substring(2));
                } catch (NumberFormatException no) {
                    throw tool.usage("not a row number: " + flag);
                }
            }
            throw tool.usage("not a flag of the tool: " + flag);
        }
        return Math.min(read.loopFrame(), read.frames());
    }
}
