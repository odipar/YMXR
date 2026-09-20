package org.ymxr;

import java.util.ArrayList;
import java.util.List;
import org.dtx.St4;
import org.ymxs.Check;
import org.ymxs.Text;
import org.ymxs.YMXS.Multi;
import org.ymxs.tool.Tool;

/**
 * What the tools that read a YMXS file share: the flags they read, the
 * structure read off standard input, and the tune files it maps to
 * (doc/ymxs.md).
 *
 * <p>A YMXS multi of several tunes is a set of subtunes: one tune file
 * each, which {@link Sndh} puts behind one core.
 */
final class Ymxs {

    /** What the packer reads: the unit, the ring, and the search for a
     *  better parse. */
    record Packing(int unit, int ring, boolean copies, double seconds) {

        static Packing of(Tool tool, List<String> flags) {
            int unit = YmToYmxr.UNIT;
            int ring = Tune.RING;
            boolean copies = false;
            double seconds = 0;
            for (String flag : flags) {
                if (flag.startsWith("-copies")) {
                    copies = true;
                    seconds = flag.length() > 7 ? decimal(tool, flag.substring(7), flag) : 0;
                } else if (flag.startsWith("-k")) {
                    unit = number(tool, flag.substring(2), flag);
                } else if (flag.startsWith("-m")) {
                    ring = number(tool, flag.substring(2), flag);
                }
            }
            return new Packing(unit, ring, copies, seconds);
        }

        org.dtx.Packer packer() {
            return copies ? new St4(true, seconds) : new St4();
        }
    }

    private Ymxs() {
    }

    /** The multi on standard input, read and checked.
     *
     * <p>A structure no player plays exits 1. A structure that plays, but
     * not as written, produces the warnings of YMXS, SPEC.md 6 on standard
     * error and passes: every tool that reads a tune reports those, so a
     * fault a writer left in is named where the tune is used rather than
     * only where it is checked.
     *
     * @throws RuntimeException where the text is not this form, or is a
     *     structure no player plays: the tool exits 1 and reports what
     */
    static Multi read(Tool tool) {
        Multi multi;
        try {
            multi = Text.read(tool.text());
        } catch (IllegalArgumentException wrong) {
            throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
        }
        List<String> faults = Check.of(multi);
        if (!faults.isEmpty()) {
            throw tool.wrong(Tool.WRONG, String.join("\n", faults));
        }
        tool.warnings(multi);
        return multi;
    }

    /** One tune file a tune, in the multi's order. */
    static List<byte[]> tuneFiles(Tool tool, Multi multi, Packing packing, Report report) {
        List<byte[]> out = new ArrayList<>();
        for (org.ymxs.YMXS.Tune tune : multi.tunes()) {
            out.add(tuneFile(tool, tune, packing, report));
        }
        return out;
    }

    /** One tune file: the structure mapped onto the columns and packed. */
    static byte[] tuneFile(Tool tool, org.ymxs.YMXS.Tune tune, Packing packing, Report report) {
        Schema.Made made;
        Tune.Written written;
        try {
            made = Schema.of(Padding.toUnit(tune, packing.unit(), report).tune());
            // The writer's faults of the encoding are reported here as the
            // schema's are: a structure the columns or the header cannot
            // carry is the tune's, and the tool names it and exits 1.
            written = Tune.write(made.columns(), made.sources(), made.rate(),
                    packing.unit(), packing.ring(), packing.packer(), report, tune.title());
        } catch (IllegalArgumentException wrong) {
            throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
        }
        report.row(title(tune), org.ymxs.Tunes.size(tune.table()) + " rows at " + made.rate()
                + " Hz, " + made.sources().count() + " sources: "
                + written.file().length + " bytes");
        return written.file();
    }

    /** What a tune is called, its writer where it has no title. */
    static String title(org.ymxs.YMXS.Tune tune) {
        return tune.title().isBlank() ? "(untitled)" : tune.title().strip();
    }

    /** The flags a tool reads beyond {@code -silent}, so that
     *  {@link Tool#of} leaves them for the tool. */
    static final String[] PACKING = {"-k", "-m", "-copies"};

    /** The tags {@code ymxs-to-sndh} reads, the switches that select the
     *  core among the eight (BINARIES.md 2.1), and the clock asked for. */
    static final String[] TAGS = {"-t", "-c", "-perf", "-lean", "-pcrel", "-abs", "-vbl"};

    /** The row count {@code ymxs-to-prg} reads. */
    static final String[] ROWS = {"-r"};

    /** The clock {@code ymxr-prg} reads: the VBL over the clock the file
     *  names. */
    static final String[] VBL = {"-vbl"};

    /** Every argument checked against the flags the tool reads: a call
     *  that passes another, or a file name, is wrong (exit 2). */
    static void only(Tool tool, List<String> flags, String[]... reads) {
        for (String flag : flags) {
            boolean read = false;
            for (String[] set : reads) {
                for (String one : set) {
                    read |= flag.startsWith(one);
                }
            }
            if (!read) {
                throw tool.usage("not a flag of the tool: " + flag);
            }
        }
    }

    /** Every flag with a number in it read, so a call this cannot read is
     *  an exit of 2 before standard input is read. */
    static void numbers(Tool tool, List<String> flags) {
        Packing.of(tool, flags);
        for (String flag : flags) {
            if (flag.startsWith("-r") && !flag.equals("-r")) {
                rows(tool, List.of(flag), 0);
            }
        }
    }

    /** The row count {@code -rROWS} names, {@code none} where the call
     *  names none. */
    static long rows(Tool tool, List<String> flags, long none) {
        for (String flag : flags) {
            if (flag.startsWith("-r")) {
                try {
                    return Long.parseLong(flag.substring(2));
                } catch (NumberFormatException no) {
                    throw tool.usage("not a row count: " + flag);
                }
            }
        }
        return none;
    }

    private static int number(Tool tool, String said, String flag) {
        try {
            return Integer.parseInt(said);
        } catch (NumberFormatException no) {
            throw tool.usage("not a number: " + flag);
        }
    }

    private static double decimal(Tool tool, String said, String flag) {
        try {
            return Double.parseDouble(said);
        } catch (NumberFormatException no) {
            throw tool.usage("not a number: " + flag);
        }
    }
}
