package org.ymxr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.dtx.Table;
import org.ymxs.tool.Tool;

/**
 * A dump converted and replayed against itself: the tune file's table
 * stepped frame by frame by {@link Replay}, every frame's registers against
 * the dump's, a volume register an effect owns checked against an unset column
 * instead, and every effect's source, target, rate and count checked against what
 * the dump flags. {@code ConversionTest} runs it on the tunes under
 * {@code ym/test}, and {@code bin/ymxr-check} on any dumps, the corpus
 * among them.
 */
final class Check {

    /** The wrong frames listed for one tune, at most. */
    static final int MOST = 20;

    private Check() {
    }

    /** What is wrong with the dump's conversion at the tool's defaults, or
     *  an empty list where every frame replays to the dump. */
    static List<String> of(YmDump.Song song) {
        return of(song, List.of());
    }

    /**
     * What is wrong with the dump's conversion at the tool's flags, or
     * an empty list where every frame replays to the dump: the tune's rows are
     * stepped through one pass and the loop once, as the kit's record runs
     * (SPEC.md 7), each row checked against its frame of the dump.
     */
    static List<String> of(YmDump.Song song, List<String> flags) {
        List<String> wrong = new ArrayList<>();
        Report report = new Report();
        YmToYmxr.Converted converted = YmToYmxr.convert(song, flags, report);
        Sources sources = converted.sources();
        Tune.Written written = converted.written();
        int repeat = converted.repeat();
        TuneFile tune = TuneFile.read(written.file());
        if (tune.frameRate() != song.playerHz()) {
            wrong.add("the frame rate is " + tune.frameRate() + ", not " + song.playerHz());
        }
        // the frame of the dump each row of the table answers to (SPEC.md
        // 6, rule 6): -1 for a row that sets no column, and the rows of a
        // loop written again answer to its frames again
        int[] frames = converted.frames();
        int rows = frames.length;
        if (tune.table().rows() != rows) {
            wrong.add("the table has " + tune.table().rows() + " rows, not " + rows);
        }
        if (tune.table().repeat() != written.repeat()) {
            wrong.add("the table repeats at " + tune.table().repeat() + ", not "
                    + written.repeat());
        }
        if (tune.sources().size() != sources.count()) {
            wrong.add("the file has " + tune.sources().size() + " sources, not "
                    + sources.count());
        }
        for (int i = 0; i < Math.min(sources.count(), tune.sources().size()); i++) {
            Table s = tune.sources().get(i);
            byte[] values = s.column(0);
            int want = sources.get(i + 1).rows();
            if (s.columns() != 1 || values.length != want) {
                wrong.add("source " + (i + 1) + " is " + s.columns() + " columns of "
                        + values.length + " rows, not one of " + want);
                continue;
            }
            for (int r = 0; r < values.length; r++) {
                boolean marker = (values[r] & 0x80) != 0;
                if (marker != (r == values.length - 1)) {
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
        int calls = Trace.calls(tune.table());
        for (int call = 0; call < calls && wrong.size() < MOST; call++) {
            int r = model.row();
            if (r == rows) {
                break;                              // a tune that plays once has played
            }
            model.step();
            int f = frames[r];
            if (f < 0) {
                // a row that sets no column: every register keeps its value, and the
                // effects run on through it
                for (int c = 0; c < 13; c++) {
                    if (model.written[c] >= 0) {
                        wrong.add(r + ": a row that sets no column wrote R" + c);
                    }
                }
                if (model.envelopeWritten) {
                    wrong.add(r + ": a row that sets no column wrote R13");
                }
                continue;
            }
            int[] dump = Columns.registers(song, f);
            Effects.Slot[] slots = Effects.of(song, f);
            int owned = 0;
            int mixer = 0;
            for (int i = 0; i < 2; i++) {
                Replay.Effect e = model.effect[i];
                Effects.Slot slot = slots[i];
                // A drum on a voice preempts a SID there: while the other
                // effect runs a drum on this slot's voice, the SID the dump
                // flags runs no source.
                Replay.Effect o = model.effect[1 - i];
                boolean preempted = slot.on() && slot.kind() == Effects.SID && o.source() != 0
                        && sources.get(o.source()).kind() == Effects.DRUM
                        && o.target() == slot.target();
                if (preempted) {
                    if (e.source() != 0) {
                        wrong.add(f + ": effect " + i + " runs source " + e.source()
                                + " under a drum on its voice");
                    }
                } else if (slot.on() && sources.number(slot, new Report()) != 0) {
                    int number = sources.number(slot, new Report());
                    if (e.source() == 0) {
                        wrong.add(f + ": effect " + i + " runs no source where the dump flags kind "
                                + slot.kind());
                    } else {
                        Sources.Source s = sources.get(e.source());
                        if (e.source() != number || e.target() != slot.target()
                                || e.select() != slot.select() || e.count() != slot.count()) {
                            Sources.Source flagged = sources.get(number);
                            wrong.add(f + ": effect " + i + " runs source " + e.source()
                                    + " (kind " + s.kind() + " value " + s.data() + ") on R"
                                    + e.target() + " at " + e.select() + "/" + e.count()
                                    + ", not source " + number + " (kind " + flagged.kind()
                                    + " value " + flagged.data() + ") on R" + slot.target()
                                    + " at " + slot.select() + "/" + slot.count());
                        }
                        if (slot.kind() == Effects.DRUM) {
                            if (!e.started()) {
                                wrong.add(f + ": the drum is not started");
                            }
                            drumEnd[i] = r + Columns.duration(s.rows(), slot.select(),
                                    slot.count(), song.playerHz());
                        }
                    }
                } else if (e.source() != 0) {
                    Sources.Source s = sources.get(e.source());
                    boolean drum = s.kind() == Effects.DRUM;
                    if (!drum || r >= drumEnd[i]) {
                        wrong.add(f + ": effect " + i + " runs source " + e.source()
                                + " where the dump flags no effect");
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
                if ((owned & 1 << c) != 0) {
                    // While an effect runs on a volume register no row sets
                    // that column (SPEC.md 1.3, section 6 rule 1).
                    if (c >= 8 && c <= 10 && model.written[c] >= 0) {
                        wrong.add(f + ": R" + c + "'s column is set to " + model.written[c]
                                + " while an effect runs on it");
                    }
                } else if (model.registers[c] != want) {
                    wrong.add(f + ": R" + c + " is " + model.registers[c] + ", not " + want);
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

    /** What one line of the tool reports of a file: no dump where the file is
     *  not a YM5!/YM6! dump, and otherwise an empty list, or the faults. */
    record Result(Path file, boolean dump, List<String> wrong) {
    }

    /** The file at {@code path}, checked at the tool's flags; a dump the
     *  converter rejects is one line saying why. */
    static Result of(Path path, List<String> flags) {
        byte[] data;
        try {
            data = Files.readAllBytes(path);
        } catch (IOException failed) {
            return new Result(path, true, List.of("unreadable: " + failed.getMessage()));
        }
        return of(data, path, flags);
    }

    /** The dump in {@code data}, under the name it is reported by. */
    static Result of(byte[] data, Path path, List<String> flags) {
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
            return new Result(path, true, of(YmDump.read(data), flags));
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
     * {@code ymxr-check}: a YM5!/YM6! dump on standard input, one line on
     * standard output saying whether the tune it converts to replays to
     * that dump, and the wrong frames under it where it does not. The
     * flags are the converter's, and an exit of 1 marks a dump that does not
     * replay.
     *
     * <p>A corpus is read by naming files and directories instead:
     * {@code ymxr-check corpus/} reads every {@code .ym} under it, in
     * parallel, one line a file and a count at the end. The tool reports how
     * far through it is on standard error, which a run of thousands runs
     * for minutes.
     */
    public static void main(String[] args) {
        List<String> args2 = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxr-check", args2, "-k", "-m", "-r", "-copies");
        List<String> flags = new ArrayList<>();
        List<String> named = new ArrayList<>();
        for (String arg : args2) {
            (arg.startsWith("-") ? flags : named).add(arg);
        }
        Ymxs.numbers(tool, flags);
        Report report = new Report(tool.reports());
        if (named.isEmpty()) {
            Result result = of(tool.bytes(), Path.of("standard input"), flags);
            said(result);
            System.out.flush();
            System.exit(result.dump() && result.wrong().isEmpty() ? Tool.DONE : Tool.WRONG);
            return;
        }
        List<Path> files;
        try {
            files = dumps(named.toArray(new String[0]));
        } catch (IOException failed) {
            throw tool.wrong(Tool.FAILED, String.valueOf(failed.getMessage()));
        }
        report.say(files.size() + (files.size() == 1 ? " file" : " files") + " to read"
                + (flags.isEmpty() ? "" : ", at " + String.join(" ", flags)));
        AtomicInteger read = new AtomicInteger();
        List<Result> results = files.parallelStream()
                .map(path -> {
                    Result result = of(path, flags);
                    report.progress("read", read.incrementAndGet(), files.size());
                    return result;
                }).toList();
        int dumps = 0;
        int failed = 0;
        for (Result result : results) {
            if (result.dump()) {
                dumps++;
                failed += result.wrong().isEmpty() ? 0 : 1;
            }
            said(result);
        }
        int others = results.size() - dumps;
        System.out.println(dumps + (dumps == 1 ? " dump, " : " dumps, ") + failed + " wrong"
                + (others == 0 ? "" : ", " + others + (others == 1 ? " file" : " files")
                + " not a dump"));
        System.out.flush();
        System.exit(failed == 0 ? Tool.DONE : Tool.WRONG);
    }

    /** One file's verdict, on standard output, which the tool is for. */
    private static void said(Result result) {
        String name = result.file().getFileName().toString();
        if (!result.dump()) {
            System.out.println(name + ": not a YM5!/YM6! dump");
        } else if (result.wrong().isEmpty()) {
            System.out.println(name + ": replays to its dump");
        } else {
            System.out.println(name + ":");
            for (String line : result.wrong()) {
                System.out.println("  " + line);
            }
        }
    }
}
