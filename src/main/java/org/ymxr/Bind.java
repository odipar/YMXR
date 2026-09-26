package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ymxs.tool.Tool;

/**
 * {@code ymxr-bind}: a tune file on standard input, its bound tune
 * ({@link Bound}) on standard output, which is the layout the player
 * reads. A tune file this does not bind, one of another version for one,
 * is a line on standard error and an exit of 1.
 */
final class Bind {

    private Bind() {
    }

    public static void main(String[] args) {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxr-bind", flags);
        Report report = new Report(tool.reports());
        byte[] tune = tool.bytes();
        if (Multi.is(tune)) {
            throw tool.wrong(Tool.WRONG, "this is a multi file of several tunes, and a bound"
                    + " tune is one tune: ymxr-sndh reads a multi file");
        }
        byte[] bound;
        try {
            read(report, tune);
            bound = Bound.of(tune);
        } catch (IllegalArgumentException wrong) {
            throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
        }
        bound(report, tune, bound);
        tool.report(bound.length + " bytes");
        Out.write(tool, bound);
    }

    /** The tune file as it was read. */
    private static void read(Report report, byte[] tune) {
        if (!report.says()) {
            return;
        }
        TuneFile file = TuneFile.read(tune);
        report.say("the tune file: " + tune.length + " bytes");
        report.row("the table", file.table().rows() + " rows of " + file.table().columns()
                + " columns, repeating at row " + file.table().repeat());
        report.row("the frame rate", file.frameRate() + " Hz");
        report.row("the sources", String.valueOf(file.sources().size()));
    }

    /** What the binding came to, and what the player needs of a host. */
    private static void bound(Report report, byte[] tune, byte[] bound) {
        if (!report.says()) {
            return;
        }
        int state = Tune.getLong(bound, Bound.STATE_AT);
        int image = Tune.getLong(bound, Bound.IMAGE_AT);
        // The image is before the DTX1 source tables, so where there
        // is a source the first one's offset ends the image, and where
        // there is none the file does.
        int sources = TuneFile.read(tune).sources().size();
        int ends = sources > 0 ? Tune.getLong(bound, Bound.INDEX_AT) : bound.length;
        report.say("bound: DTX's reader for the table in place of the table");
        report.row("the reader's image", "at " + image + ", " + (ends - image) + " bytes");
        if (sources > 0) {
            report.row("the source tables", sources + " of " + (bound.length - ends) + " bytes");
        }
        report.row("the state block", state + " bytes, which the host finds the workspace for");
        report.row("in all", bound.length + " bytes, " + (bound.length - tune.length)
                + " over the tune file");
    }
}
