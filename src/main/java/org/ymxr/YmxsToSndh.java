package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.ymxs.YMXS.Multi;
import org.ymxs.tool.Tool;

/**
 * {@code ymxs-to-sndh}: a YMXS structure as JSON on standard input, an
 * SNDH file on standard output.
 *
 * <p>Every tune of the multi is a subtune, 1 upward in its order, behind
 * one core (doc/BINARIES.md 2). The title and the composer are the first
 * tune's unless {@code -t} and {@code -c} name others, and a tune's title
 * names its subtune where the multi has several.
 *
 * <p>{@code -perf} selects the core with the raster monitor in and
 * {@code -lean} the core whose ticks neither drop the interrupt level nor
 * write an end of interrupt (doc/performance.md); the packer's flags are
 * {@code ymxs-to-ymxr}'s.
 */
public final class YmxsToSndh {

    private YmxsToSndh() {
    }

    public static void main(String[] args) {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxs-to-sndh", flags, Ymxs.PACKING);
        Ymxs.only(tool, flags, Ymxs.PACKING, Ymxs.TAGS);
        Report report = new Report(tool.reports());
        Multi multi = Ymxs.read(tool);
        Out.write(tool, of(tool, multi, flags, report));
        for (String note : report.unsaid()) {
            System.err.println("  " + note);
        }
    }

    /** The SNDH file of the multi, as the flags ask for it. */
    static byte[] of(Tool tool, Multi multi, List<String> flags, Report report) {
        @Nullable String title = null;
        @Nullable String composer = null;
        boolean monitor = false;
        boolean lean = false;
        Sndh.Ticks ticks = Sndh.Ticks.CHOSEN;
        for (String flag : flags) {
            if (flag.startsWith("-t")) {
                title = flag.substring(2);
            } else if (flag.startsWith("-c") && !flag.startsWith("-copies")) {
                composer = flag.substring(2);
            } else if (flag.equals("-perf")) {
                monitor = true;
            } else if (flag.equals("-lean")) {
                lean = true;
            } else if (flag.equals("-pcrel")) {
                ticks = Sndh.Ticks.PCREL;
            } else if (flag.equals("-abs")) {
                ticks = Sndh.Ticks.ABSOLUTE;
            }
        }
        org.ymxs.YMXS.Tune first = multi.tunes().get(0);
        if (title == null) {
            title = Ymxs.title(first);
        }
        if (composer == null && !first.composer().isBlank()) {
            composer = first.composer().strip();
        }
        List<String> names = new ArrayList<>();
        for (org.ymxs.YMXS.Tune tune : multi.tunes()) {
            names.add(Ymxs.title(tune));
        }
        List<byte[]> tunes = Ymxs.tuneFiles(tool, multi, Ymxs.Packing.of(tool, flags), report);
        try {
            return Sndh.of(tunes, new Sndh.Options(title, composer,
                    tunes.size() > 1 ? names : null, monitor, lean, ticks));
        } catch (IllegalArgumentException wrong) {
            throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
        }
    }
}
