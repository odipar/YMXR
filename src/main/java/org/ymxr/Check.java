package org.ymxr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.dtx.Table;

/**
 * A dump converted and replayed against itself: the tune file's table
 * stepped frame by frame by {@link Replay}, every frame's registers held to
 * the dump's but those an effect owns, and every effect's source, target,
 * rate and count held to what the dump flags. {@code ConversionTest} runs
 * it on the tunes under {@code ym/test}, and {@code bin/ymxr-check} on any
 * dumps, the corpus among them.
 */
final class Check {

    /** The wrong frames listed for one tune, at most. */
    static final int MOST = 20;

    private Check() {
    }

    /** What is wrong with the dump's conversion at the tool's defaults, or
     *  nothing where every frame replays to the dump. */
    static List<String> of(YmDump.Song song) {
        List<String> wrong = new ArrayList<>();
        Report report = new Report();
        int repeat = (int) Math.min(song.loopFrame(), song.frames());
        Sources sources = new Sources(song);
        Columns columns = new Columns(song, sources, repeat, report);
        Tune.Written written = Tune.write(columns, sources, song.playerHz(), YmToYmxr.UNIT,
                Tune.RING, report);
        TuneFile tune = TuneFile.read(written.file());
        if (tune.frameRate() != song.playerHz()) {
            wrong.add("the frame rate is " + tune.frameRate() + ", not " + song.playerHz());
        }
        int rows = song.frames() + written.before() + written.after();
        if (tune.table().rows() != rows) {
            wrong.add("the table has " + tune.table().rows() + " rows, not " + rows);
        }
        if (tune.table().repeat() != written.repeat()) {
            wrong.add("the table repeats at " + tune.table().repeat() + ", not "
                    + written.repeat());
        }
        if (tune.sources().size() != sources.count()) {
            wrong.add("the file holds " + tune.sources().size() + " sources, not "
                    + sources.count());
        }
        for (int i = 0; i < Math.min(sources.count(), tune.sources().size()); i++) {
            Table s = tune.sources().get(i);
            byte[] held = s.column(0);
            int want = sources.get(i + 1).rows().length;
            if (s.columns() != 1 || held.length != want) {
                wrong.add("source " + (i + 1) + " is " + s.columns() + " columns of "
                        + held.length + " rows, not one of " + want);
                continue;
            }
            for (int r = 0; r < held.length; r++) {
                boolean marker = (held[r] & 0x80) != 0;
                if (marker != (r == held.length - 1)) {
                    wrong.add("source " + (i + 1) + " row " + r + (marker ? " is a marker"
                            : " is not the marker"));
                }
            }
        }
        if (!wrong.isEmpty()) {
            return wrong;
        }
        Replay model = new Replay(tune.table());
        int[] drumEnd = {-1, -1};
        for (int f = 0; f < song.frames() && wrong.size() < MOST; f++) {
            if (f == repeat && repeat > 0) {
                for (int pad = 0; pad < written.before(); pad++) {
                    model.step();
                }
            }
            model.step();
            int[] dump = Columns.registers(song, f);
            Effects.Slot[] slots = Effects.of(song, f);
            int owned = 0;
            int mixer = 0;
            for (int i = 0; i < 2; i++) {
                Replay.Effect e = model.effect[i];
                Effects.Slot slot = slots[i];
                if (slot.on() && sources.number(slot, new Report()) != 0) {
                    if (e.source() == 0) {
                        wrong.add(f + ": effect " + i + " runs nothing where the dump flags kind "
                                + slot.kind());
                    } else {
                        Sources.Source s = sources.get(e.source());
                        if (s.kind() != slot.kind() || e.target() != slot.target()
                                || e.select() != slot.select() || e.count() != slot.count()) {
                            wrong.add(f + ": effect " + i + " runs kind " + s.kind() + " on R"
                                    + e.target() + " at " + e.select() + "/" + e.count()
                                    + ", not kind " + slot.kind() + " on R" + slot.target()
                                    + " at " + slot.select() + "/" + slot.count());
                        }
                        if (slot.kind() == Effects.DRUM) {
                            if (!e.started()) {
                                wrong.add(f + ": the drum is not started");
                            }
                            drumEnd[i] = f + Columns.duration(s.rows().length, slot.select(),
                                    slot.count(), song.playerHz());
                        }
                    }
                } else if (e.source() != 0) {
                    Sources.Source s = sources.get(e.source());
                    boolean drum = s.kind() == Effects.DRUM;
                    if (!drum || f >= drumEnd[i]) {
                        wrong.add(f + ": effect " + i + " runs source " + e.source()
                                + " where the dump flags nothing");
                    }
                }
                if (e.source() != 0 && e.target() < 13) {
                    owned |= 1 << e.target();
                    if (sources.get(e.source()).kind() == Effects.DRUM) {
                        mixer |= 0x09 << (e.target() - 8);
                    }
                }
            }
            for (int c = 0; c < 13; c++) {
                int want = c == 7 ? dump[7] | mixer : dump[c];
                if ((owned & 1 << c) == 0 && model.registers[c] != want) {
                    wrong.add(f + ": R" + c + " holds " + model.registers[c] + ", not " + want);
                }
            }
            if (model.envelopeWritten != (dump[13] >= 0)
                    || (dump[13] >= 0 && model.registers[13] != dump[13])) {
                wrong.add(f + ": R13 " + (model.envelopeWritten ? "written " + model.registers[13]
                        : "not written") + ", the dump " + (dump[13] >= 0 ? "writes " + dump[13]
                        : "does not write"));
            }
        }
        return wrong;
    }

    /** What one line of the tool says of a file: no dump where the file is
     *  not a YM5!/YM6! dump, and otherwise nothing wrong, or what is. */
    record Result(Path file, boolean dump, List<String> wrong) {
    }

    /** The file at {@code path}, checked; a dump the converter refuses is
     *  one line saying why. */
    static Result of(Path path) {
        byte[] data;
        try {
            data = Files.readAllBytes(path);
        } catch (IOException failed) {
            return new Result(path, true, List.of("unreadable: " + failed.getMessage()));
        }
        if (Lha.isArchive(data)) {
            try {
                data = Lha.unpack(data);
            } catch (RuntimeException failed) {
                return new Result(path, true, List.of("the archive does not unpack: "
                        + failed.getMessage()));
            }
        }
        if (!YmDump.isDump(data)) {
            return new Result(path, false, List.of());
        }
        try {
            return new Result(path, true, of(YmDump.read(data)));
        } catch (RuntimeException failed) {
            return new Result(path, true, List.of("the converter refuses it: "
                    + failed.getMessage()));
        }
    }

    /** The dumps named, and every {@code .ym} under a directory named. */
    static List<Path> dumps(String[] args) throws IOException {
        List<Path> out = new ArrayList<>();
        for (String arg : args) {
            Path path = Path.of(arg);
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.list(path)) {
                    out.addAll(files.filter(p -> p.toString().toLowerCase().endsWith(".ym"))
                            .sorted().toList());
                }
            } else {
                out.add(path);
            }
        }
        return out;
    }

    /**
     * {@code ymxr-check DUMP|DIR ...}: one line a file, the wrong frames
     * under a tune that fails, and an exit of 1 where any does. A file that
     * is not a YM5!/YM6! dump is said and not counted.
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("ymxr-check DUMP|DIR ...");
            System.exit(2);
        }
        List<Result> results = dumps(args).parallelStream().map(Check::of).toList();
        int dumps = 0;
        int failed = 0;
        for (Result result : results) {
            String name = result.file().getFileName().toString();
            if (!result.dump()) {
                System.out.println(name + ": not a YM5!/YM6! dump");
            } else if (result.wrong().isEmpty()) {
                dumps++;
                System.out.println(name + ": replays to its dump");
            } else {
                dumps++;
                failed++;
                System.out.println(name + ":");
                for (String line : result.wrong()) {
                    System.out.println("  " + line);
                }
            }
        }
        int others = results.size() - dumps;
        System.out.println(dumps + (dumps == 1 ? " dump, " : " dumps, ") + failed + " wrong"
                + (others == 0 ? "" : ", " + others + (others == 1 ? " file" : " files")
                + " not a dump"));
        System.exit(failed == 0 ? 0 : 1);
    }
}
