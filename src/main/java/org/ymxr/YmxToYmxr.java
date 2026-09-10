package org.ymxr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A YMX file into a tune file.
 *
 * <p>ymx-dump reads a .ymx out (YMX, SPEC.md 2): its header, and every
 * stream decoded to one byte a frame. Streams 0 to 13 are the sound
 * registers as the chip receives them, the effect bits stripped, so
 * they are a frame's fourteen values and go through the same conversion a
 * YM dump does.
 *
 * <p>Streams 14 to 24 are the script that drives YMX's four timer
 * channels. A tune whose script starts no effect converts whole here. One
 * that starts something converts to its frame values without it, which
 * this says on standard error rather than leaving to a listener.
 */
final class YmxToYmxr {

    /** The YMX tool this reads, which YMX_DUMP names. */
    private static final String DUMP =
            System.getenv().getOrDefault("YMX_DUMP", "ymx-dump");

    /** The name this process's standard input opens under. */
    private static final String STANDARD_INPUT = "/dev/stdin";

    /** The streams a frame's registers stand in (YMX, SPEC.md 2). */
    private static final int REGISTERS = 14;

    /** Stream 14, M, the master byte (YMX, SPEC.md 2.1): bits 0 to 3 mark
     *  the timer channels that act this frame, and bits 4 to 7 name which
     *  voices the frame leaves its volume register unwritten for, which is
     *  what SPEC.md 6 rule 1 asks of a row here. */
    private static final int STREAM_M = 14;

    /** The bits of M that mark a channel acting. */
    private static final int ACTS = 0x0F;

    private YmxToYmxr() {
    }

    /** One .ymx read out: the header's frames and rate, the streams, and
     *  each sample's level bytes with the end marker after them and the
     *  position it loops to, $FFFF where it plays once (YMX, SPEC.md 6). */
    record Dumped(int frames, int rate, int loopFrame, byte[][] streams,
                  byte[][] samples, int[] loops) {
    }

    /** The file read out through ymx-dump. */
    static Dumped dumped(Path file) throws IOException {
        return dumped(file.toString(), false);
    }

    /** Standard input read out through ymx-dump. The tool opens a file
     *  name, so it is called with the name of this process's standard
     *  input, on that input, and no copy of the file is written. */
    static Dumped standardInput() throws IOException {
        return dumped(STANDARD_INPUT, true);
    }

    /** The input is not a file ymx-dump reads: its exit of 1, and the
     *  fault itself on standard error, from ymx-dump. */
    static final class FormatException extends IOException {

        private static final long serialVersionUID = 1L;

        FormatException(String said) {
            super(said);
        }
    }

    private static Dumped dumped(String file, boolean input) throws IOException {
        String named = input ? "standard input" : file;
        ProcessBuilder run = new ProcessBuilder(DUMP, file);
        run.redirectError(ProcessBuilder.Redirect.INHERIT);
        if (input) {
            run.redirectInput(ProcessBuilder.Redirect.INHERIT);
        }
        Process ymx;
        try {
            ymx = run.start();
        } catch (IOException notThere) {
            throw new IOException("cannot run " + DUMP
                    + ": YMX_DUMP names YMX's ymx-dump", notThere);
        }
        int frames = 0;
        int rate = 50;
        int loop = 0;
        int streams = 0;
        byte[][] value = new byte[0][];
        byte[][] samples = new byte[0][];
        int[] loops = new int[0];
        try (BufferedReader said = new BufferedReader(
                new InputStreamReader(ymx.getInputStream()))) {
            String line;
            while ((line = said.readLine()) != null) {
                String[] word = line.split(" ");
                if (word[0].equals("frames")) {
                    frames = Integer.parseInt(word[1]);
                } else if (word[0].equals("rate")) {
                    rate = Integer.parseInt(word[1]);
                } else if (word[0].equals("loop")) {
                    loop = Integer.parseInt(word[1]);
                } else if (word[0].equals("streams")) {
                    streams = Integer.parseInt(word[1]);
                    value = new byte[streams][frames];
                } else if (word[0].equals("samples")) {
                    samples = new byte[Integer.parseInt(word[1])][];
                    loops = new int[samples.length];
                } else if (word[0].equals("sample")) {
                    int at = Integer.parseInt(word[1]);
                    loops[at] = Integer.parseInt(word[3]);
                    byte[] level = new byte[word.length - 4];
                    for (int i = 0; i < level.length; i++) {
                        level[i] = (byte) Integer.parseInt(word[i + 4]);
                    }
                    samples[at] = level;
                } else if (word.length == streams + 1) {
                    int frame = Integer.parseInt(word[0]);
                    for (int s = 0; s < streams; s++) {
                        value[s][frame] = (byte) Integer.parseInt(word[s + 1]);
                    }
                }
            }
        }
        try {
            int exit = ymx.waitFor();
            if (exit == 1) {
                throw new FormatException(named + " is not a file " + DUMP + " reads");
            }
            if (exit != 0) {
                throw new IOException(DUMP + " did not read " + named);
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted reading " + named, stopped);
        }
        return new Dumped(frames, rate, loop, value, samples, loops);
    }

    /**
     * The frames of a dumped file, its packing's padding off the end.
     *
     * <p>YMX fits a tune to its unit by padding it (YMX, ymx), where the
     * conversion here drops to a unit of 1 instead, so a dump of an odd
     * frame count is one frame longer through YMX than through the dump.
     * The dump is what a tune's rows are, so the padding comes off and
     * this format reckons the unit separately.
     *
     * <p>A pad at YMX's unit of 2 is one frame, it repeats the frame
     * before it and it acts on no channel, and a padded count is even.
     * One frame comes off where all three apply. A tune whose last
     * frame reads that way loses it, which is a frame writing what the
     * frame before it wrote; a file packed at a wider unit keeps the pad
     * past the first.
     */
    static int frames(Dumped read) {
        int last = read.frames() - 1;
        if (read.frames() % 2 != 0 || last <= read.loopFrame()) {
            return read.frames();
        }
        if ((read.streams()[STREAM_M][last] & 0xFF) != 0) {
            return read.frames();
        }
        for (int r = 0; r < REGISTERS; r++) {
            if (read.streams()[r][last] != read.streams()[r][last - 1]) {
                return read.frames();
            }
        }
        return last;
    }

    /** The frames a dumped file's script acts on, which this version
     *  leaves behind: M is 0 on a frame that starts no effect (YMX,
     *  SPEC.md 2.1). */
    static int acting(Dumped read) {
        int frames = 0;
        for (int frame = 0; frame < read.frames(); frame++) {
            if ((read.streams()[STREAM_M][frame] & ACTS) != 0) {
                frames++;
            }
        }
        return frames;
    }

    /** A dumped file as a YM song of its fourteen register streams, which
     *  the converter reads as it reads a dump's. */
    static YmDump.Song song(Dumped read, String name) {
        byte[][] registers = new byte[YmDump.Song.YM_REGISTERS][read.frames()];
        for (int r = 0; r < YmDump.Song.YM_REGISTERS; r++) {
            registers[r] = new byte[read.frames()];
        }
        for (int r = 0; r < REGISTERS; r++) {
            System.arraycopy(read.streams()[r], 0, registers[r], 0, read.frames());
        }
        return new YmDump.Song("YMX!", read.frames(), read.rate(), 2000000L,
                read.loopFrame(), false, 0L, new byte[0][], name, "", "", registers);
    }

    public static void main(String[] args) throws IOException {
        List<String> flags = new ArrayList<>();
        String in = null;
        String out = null;
        for (String arg : args) {
            if (arg.startsWith("-")) {
                flags.add(arg);
            } else if (in == null) {
                in = arg;
            } else if (out == null) {
                out = arg;
            }
        }
        if (in == null || out == null) {
            System.err.println("ymx-to-ymxr in.ymx out.ymxr [-kK] [-mN]"
                    + " [-copies[S]] [-silent]");
            System.exit(2);
            return;
        }
        Report report = new Report(!flags.contains(YmToYmxr.SILENT));
        Dumped read = dumped(Path.of(in));
        report.say("YMX!: " + read.frames() + " frames at " + read.rate() + " Hz, "
                + acting(read) + " of them acting on a channel");
        int unit = YmToYmxr.UNIT;
        int ring = Tune.RING;
        for (String flag : flags) {
            if (flag.startsWith("-k")) {
                unit = Integer.parseInt(flag.substring(2));
            } else if (flag.startsWith("-m")) {
                ring = Integer.parseInt(flag.substring(2));
            }
        }
        int frames = frames(read);
        if (frames != read.frames()) {
            report.note((read.frames() - frames) + " frame of YMX's padding"
                    + " comes off the end: a dump's rows are what a tune has,"
                    + " and this conversion selects its unit separately");
        }
        read = new Dumped(frames, read.rate(), read.loopFrame(), read.streams(),
                read.samples(), read.loops());
        int repeat = Math.min(read.loopFrame(), read.frames());
        String stem = Path.of(in).getFileName().toString();
        YmDump.Song song = song(read, stem.endsWith(".ymx")
                ? stem.substring(0, stem.length() - 4) : stem);

        // Every conversion passes through the structure (doc/ymxs.md):
        // the register streams and the script become a YMXS tune, and the
        // schema maps that onto the columns.
        Schema.Made made = Schema.of(Ymx.read(read, song, repeat, report));
        Columns columns = made.columns();
        Sources sources = made.sources();
        report.row("the effects", Integer.bitCount(columns.effects) + " of 4 run, "
                + sources.count() + " sources");
        Tune.Written written = Tune.write(columns, sources, read.rate(), unit, ring, report);
        Files.write(Path.of(out), written.file());
        System.out.println(read.frames() + " frames at " + read.rate() + " Hz, "
                + sources.count() + " sources, effects "
                + Integer.toBinaryString(columns.effects) + ": "
                + written.file().length + " bytes");
        System.out.println("written: " + out);
    }
}
