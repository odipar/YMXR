package org.ymxr;

import java.util.ArrayList;
import java.util.List;
import org.dtx.St4;
import org.ymxs.Check;
import org.ymxs.Text;
import org.ymxs.YMXS.Multi;
import org.ymxs.tool.Tool;

/**
 * What the tools that read a YMXS file share: the flags they take, the
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

        static Packing of(List<String> flags) {
            int unit = YmToYmxr.UNIT;
            int ring = Tune.RING;
            boolean copies = false;
            double seconds = 0;
            for (String flag : flags) {
                if (flag.startsWith("-k")) {
                    unit = number(flag.substring(2), flag);
                } else if (flag.startsWith("-m")) {
                    ring = number(flag.substring(2), flag);
                } else if (flag.startsWith("-copies")) {
                    copies = true;
                    seconds = flag.length() > 7 ? decimal(flag.substring(7), flag) : 0;
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

    /** The multi standard input carries, read and checked.
     *
     * @throws RuntimeException where the text is not this form, or is a
     *     structure no player plays: the tool exits 1 and says what
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
        try {
            made = Schema.of(tune);
        } catch (IllegalArgumentException wrong) {
            throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
        }
        Tune.Written written = Tune.write(made.columns(), made.sources(), made.rate(),
                packing.unit(), packing.ring(), packing.packer(), report);
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

    private static int number(String said, String flag) {
        try {
            return Integer.parseInt(said);
        } catch (NumberFormatException no) {
            throw new IllegalArgumentException("not a number: " + flag);
        }
    }

    private static double decimal(String said, String flag) {
        try {
            return Double.parseDouble(said);
        } catch (NumberFormatException no) {
            throw new IllegalArgumentException("not a number: " + flag);
        }
    }
}
