package org.ymxr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ymxs.YMXS.Multi;
import org.ymxs.tool.Tool;

/**
 * {@code ymxs-to-ymxr}: a YMXS structure as JSON on standard input, a tune
 * file (SPEC.md 3.3) on standard output.
 *
 * <p>The second stage of every conversion here (doc/ymxs.md). A tune file
 * carries one tune, so a multi of several is an error: those are subtunes,
 * and {@code ymxs-to-sndh} puts them behind one core.
 */
public final class YmxsToYmxr {

    private YmxsToYmxr() {
    }

    public static void main(String[] args) throws IOException {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxs-to-ymxr", flags, Ymxs.PACKING);
        Report report = new Report(tool.reports());
        Multi multi = Ymxs.read(tool);
        if (multi.tunes().size() != 1) {
            throw tool.wrong(Tool.WRONG, "the multi has " + multi.tunes().size()
                    + " tunes, and a tune file carries one: ymxs-to-sndh reads several"
                    + " as subtunes");
        }
        byte[] file = Ymxs.tuneFile(tool, multi.tunes().get(0), Ymxs.Packing.of(flags), report);
        for (String note : report.unsaid()) {
            System.err.println("  " + note);
        }
        Out.write(tool, file);
    }
}
