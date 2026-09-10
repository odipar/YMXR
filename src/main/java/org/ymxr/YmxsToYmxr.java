package org.ymxr;

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
 * has one tune in it, so a multi of several writes a multi file
 * (doc/BINARIES.md 0) instead: {@code ymxr-sndh} reads that as a set of
 * subtunes, each named by its tune's title.
 */
public final class YmxsToYmxr {

    private YmxsToYmxr() {
    }

    public static void main(String[] args) {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxs-to-ymxr", flags, Ymxs.PACKING);
        Ymxs.only(tool, flags, Ymxs.PACKING);
        Report report = new Report(tool.reports());
        Multi multi = Ymxs.read(tool);
        List<byte[]> tunes = Ymxs.tuneFiles(tool, multi, Ymxs.Packing.of(tool, flags), report);
        byte[] file;
        if (tunes.size() == 1) {
            file = tunes.get(0);
        } else {
            List<String> names = new ArrayList<>();
            for (org.ymxs.YMXS.Tune tune : multi.tunes()) {
                names.add(Ymxs.title(tune));
            }
            try {
                file = org.ymxr.Multi.of(tunes, names);
            } catch (IllegalArgumentException wrong) {
                throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
            }
            tool.report(tunes.size() + " tunes in a multi file, " + file.length + " bytes");
        }
        for (String note : report.unsaid()) {
            System.err.println("  " + note);
        }
        Out.write(tool, file);
    }
}
