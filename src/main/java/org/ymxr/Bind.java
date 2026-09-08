package org.ymxr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code ymxr-bind in.ymxr out.bin}: the bound tune of a tune file
 * ({@link Bound}), what the player takes, as a file. A tune file this
 * does not bind, one of another version for one, gets a line on stderr
 * beginning {@code ymxr-bind: } and an exit of 1; a file that does not
 * read or write gets the same line and an exit of 2, as does a wrong
 * call.
 */
final class Bind {

    private Bind() {
    }

    public static void main(String[] args) {
        List<String> named = new ArrayList<>();
        boolean silent = false;
        for (String arg : args) {
            if (arg.equals(YmToYmxr.SILENT)) {
                silent = true;
            } else {
                named.add(arg);
            }
        }
        if (named.size() != 2) {
            System.err.println("ymxr-bind in.ymxr out.bin [-silent]");
            System.exit(2);
            return;
        }
        Report report = new Report(!silent);
        byte[] tune;
        byte[] bound;
        try {
            tune = Files.readAllBytes(Path.of(named.get(0)));
            read(report, tune);
            bound = Bound.of(tune);
        } catch (IllegalArgumentException wrong) {
            System.err.println("ymxr-bind: " + wrong.getMessage());
            System.exit(1);
            return;
        } catch (IOException failed) {
            System.err.println("ymxr-bind: " + failed);
            System.exit(2);
            return;
        }
        try {
            Files.write(Path.of(named.get(1)), bound);
        } catch (IOException failed) {
            System.err.println("ymxr-bind: " + failed);
            System.exit(2);
            return;
        }
        bound(report, tune, bound);
        System.out.println(named.get(1) + ": " + bound.length + " bytes");
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
        report.say("bound: DTX's reader for the table in place of the table");
        report.row("the reader's image", "at " + image + ", " + (bound.length - image)
                + " bytes");
        report.row("the state block", state + " bytes, which the host finds the workspace for");
        report.row("in all", bound.length + " bytes, " + (bound.length - tune.length)
                + " over the tune file");
    }
}
