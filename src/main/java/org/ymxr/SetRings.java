package org.ymxr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeSet;
import org.dtx.Dtx1;
import org.dtx.Dtx3;
import org.dtx.Packer;
import org.dtx.Table;

/**
 * The rings of a set's tables, chosen for the set (tools.md 4.7). A set's
 * subtunes share one workspace, sized for the largest state block among
 * them (BINARIES.md 3.1), so the workspace grows with the largest alone: a
 * tune whose state block stays within it packs through the ring of the
 * fewest table bytes there.
 *
 * <p>Each tune's table is packed without copies through each ring the writer
 * chooses among, as one tune's is (tools.md 4.2). For each state block B one
 * of those packings needs, each tune packs through the ring whose table is
 * the fewest bytes among those whose state block is B or less, the smaller
 * ring of two of the same bytes, and the set keeps the B whose tables and B
 * come to the least, the smaller B of two that come to the same. Under
 * {@code -copies} each tune then packs with copies through its ring where
 * they pay.
 */
final class SetRings {

    private SetRings() {
    }

    /**
     * Each tune's ring for the set, in the order of {@code options}: an
     * option of a tune maps a ring to the table's bytes and the state
     * block its reader needs, through that ring.
     *
     * @throws IllegalArgumentException where a tune has no option
     */
    static int[] choose(List<SortedMap<Integer, int[]>> options) {
        TreeSet<Integer> budgets = new TreeSet<>();
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).isEmpty()) {
                throw new IllegalArgumentException("tune " + (i + 1) + " has no ring to"
                        + " choose");
            }
            for (int[] one : options.get(i).values()) {
                budgets.add(one[1]);
            }
        }
        int[] best = new int[0];
        long least = Long.MAX_VALUE;
        for (int budget : budgets) {
            int[] rings = new int[options.size()];
            long tables = 0;
            boolean fits = true;
            for (int i = 0; i < options.size() && fits; i++) {
                int ring = -1;
                int bytes = Integer.MAX_VALUE;
                // in ring order, so of two of the same bytes the smaller ring
                for (Map.Entry<Integer, int[]> one : options.get(i).entrySet()) {
                    if (one.getValue()[1] <= budget && one.getValue()[0] < bytes) {
                        ring = one.getKey();
                        bytes = one.getValue()[0];
                    }
                }
                fits = ring >= 0;
                rings[i] = ring;
                tables += bytes;
            }
            if (fits && tables + budget < least) {
                least = tables + budget;
                best = rings;
            }
        }
        return best;
    }

    /**
     * The set's tunes with each table packed through the ring chosen for
     * the set among the plain packings each was written with, and with
     * every packer through it where there are several.
     *
     * @throws IllegalArgumentException where a tune was written through a
     *     ring named, and so has no plain packings to choose among
     */
    static List<Tune.Written> forSet(List<Tune.Written> tunes, List<Packer> packers,
                                     Report report) {
        List<SortedMap<Integer, int[]>> options = new ArrayList<>();
        for (Tune.Written tune : tunes) {
            SortedMap<Integer, int[]> one = new java.util.TreeMap<>();
            for (Map.Entry<Integer, Tune.Packed> packed : tune.plain().entrySet()) {
                one.put(packed.getKey(), new int[] {packed.getValue().table().length,
                    packed.getValue().state()});
            }
            options.add(one);
        }
        int[] rings = choose(options);
        int live = 0;
        if (packers.size() > 1) {
            for (int i = 0; i < tunes.size(); i++) {
                live += Dtx3.payload(plain(tunes.get(i), rings[i]).table()).live();
            }
        }
        Tune.Watched watched = new Tune.Watched(report, live * packers.size());
        List<Packer> every = new ArrayList<>();
        for (Packer packer : packers) {
            every.add(watched.around(packer));
        }
        List<Tune.Written> out = new ArrayList<>();
        for (int i = 0; i < tunes.size(); i++) {
            Tune.Written tune = tunes.get(i);
            byte[] table = plain(tune, rings[i]).table();
            if (packers.size() > 1) {
                table = Dtx3.write(Dtx3.read(table), every, tune.unit(), rings[i]);
            }
            out.add(new Tune.Written(Tune.withTable(tune.file(), table), tune.repeat(), table,
                    tune.frames(), tune.unit(), tune.sources(), tune.sourceRows(),
                    tune.sourceBytes(), tune.plain()));
        }
        return out;
    }

    private static Tune.Packed plain(Tune.Written tune, int ring) {
        Tune.Packed packed = tune.plain().get(ring);
        if (packed == null) {
            throw new IllegalArgumentException("no table through a ring of " + ring);
        }
        return packed;
    }

    /**
     * The tune files of a set, read back and each table packed again:
     * through the rings chosen for the set where {@code ring} is
     * {@link Tune#CHOOSE}, and otherwise each through the multiple of the
     * period nearest {@code ring} within its reach, with every packer.
     *
     * @throws IllegalArgumentException where a file is not a tune file of
     *     this version, with the reader's line (writing.md 7)
     */
    static List<Tune.Written> ofFiles(List<byte[]> files, List<Packer> packers, int ring,
                                      Report report) {
        List<TuneFile> read = new ArrayList<>();
        int packs = 0;
        for (byte[] file : files) {
            TuneFile one = TuneFile.read(file);
            read.add(one);
            int live = Dtx3.payload(one.dtx3()).live();
            packs += live * (ring == Tune.CHOOSE ? Tune.rings(live).size() : packers.size());
        }
        Tune.Watched watched = new Tune.Watched(report, packs);
        Packer first = watched.around(packers.get(0));
        List<Packer> every = new ArrayList<>();
        for (Packer packer : packers) {
            every.add(watched.around(packer));
        }
        List<Tune.Written> tunes = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            TuneFile one = read.get(i);
            Table table = one.table();
            int unit = Dtx3.payload(one.dtx3()).unit();
            byte[][] column = new byte[table.columns()][];
            for (int c = 0; c < column.length; c++) {
                column[c] = table.column(c);
            }
            int sourceRows = 0;
            int sourceBytes = 0;
            for (Table source : one.sources()) {
                sourceRows += source.rows();
                sourceBytes += Dtx1.write(source).length;
            }
            int tableLive = Tune.live(column);
            SortedMap<Integer, Tune.Packed> plain = new java.util.TreeMap<>();
            byte[] packed;
            if (ring == Tune.CHOOSE) {
                plain = Tune.candidates(column, table.rows(), table.repeat(), unit,
                        Tune.rings(tableLive), first, report);
                // the table through the set's ring is put in its place below
                packed = plain.values().iterator().next().table();
            } else {
                int at = Tune.ringOf(ring, tableLive);
                if (at != ring) {
                    report.note("the ring is " + Report.count(at, "byte", "bytes")
                            + ": a multiple of the period within the player's reach");
                }
                packed = Tune.table(column, table.rows(), table.repeat(), unit, at, every);
            }
            tunes.add(new Tune.Written(Tune.withTable(files.get(i), packed), table.repeat(),
                    packed, table.rows(), unit, one.sources().size(), sourceRows, sourceBytes,
                    plain));
        }
        return ring == Tune.CHOOSE ? forSet(tunes, packers, report) : tunes;
    }

    /** The set's heading in the report: its tunes, their tables' bytes and
     *  the largest state block among them (tools.md 4.7). */
    static void say(Report report, List<Tune.Written> set) {
        long tables = 0;
        int state = 0;
        for (Tune.Written tune : set) {
            tables += tune.table().length;
            state = Math.max(state, Tune.state(tune.table()));
        }
        report.say("the set: " + Report.count(set.size(), "tune", "tunes") + ", the tables "
                + Report.count(tables, "byte", "bytes") + ", the state block "
                + Report.count(state, "byte", "bytes"));
    }
}
