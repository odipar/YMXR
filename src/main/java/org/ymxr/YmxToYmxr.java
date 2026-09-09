package org.ymxr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A YMX file into a tune file.
 *
 * <p>ymx-dump reads a .ymx out (YMX, SPEC.md 2): its header, and every
 * stream decoded to one byte a frame. Streams 0 to 13 are the sound
 * registers holding what the chip receives, the effect bits stripped, so
 * they are a frame's fourteen values and go through the same conversion a
 * YM dump does.
 *
 * <p>Streams 14 to 24 are the script that drives YMX's four timer
 * channels. A tune whose script starts nothing converts whole here. One
 * that starts something converts to its frame values without it, which
 * this says on standard error rather than leaving to a listener.
 */
final class YmxToYmxr {

    /** The YMX tool this reads, which YMX_DUMP names. */
    private static final String DUMP =
            System.getenv().getOrDefault("YMX_DUMP", "ymx-dump");

    /** The streams a frame's registers stand in (YMX, SPEC.md 2). */
    private static final int REGISTERS = 14;

    /** Stream 14, M, the master byte (YMX, SPEC.md 2.1): bits 0 to 3 mark
     *  the timer channels that act this frame, and bits 4 to 7 hold which
     *  voices the frame leaves its volume register unwritten for, which is
     *  what SPEC.md 6 rule 1 asks of a row here. */
    private static final int STREAM_M = 14;

    /** The bits of M that mark a channel acting. */
    private static final int ACTS = 0x0F;

    private YmxToYmxr() {
    }

    /** One .ymx read out: the header's frames and rate, and the streams. */
    record Dumped(int frames, int rate, int loopFrame, byte[][] streams) {
    }

    /** The file read out through ymx-dump. */
    static Dumped dumped(Path file) throws IOException {
        ProcessBuilder run = new ProcessBuilder(DUMP, file.toString());
        run.redirectError(ProcessBuilder.Redirect.INHERIT);
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
                } else if (word[0].equals("samples") || word[0].equals("sample")) {
                    continue;
                } else if (word.length == streams + 1) {
                    int frame = Integer.parseInt(word[0]);
                    for (int s = 0; s < streams; s++) {
                        value[s][frame] = (byte) Integer.parseInt(word[s + 1]);
                    }
                }
            }
        }
        try {
            if (ymx.waitFor() != 0) {
                throw new IOException(DUMP + " did not read " + file);
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted reading " + file, stopped);
        }
        return new Dumped(frames, rate, loop, value);
    }

    /** The frames a dumped file's script acts on, which this version
     *  leaves behind: M is 0 on a frame that starts nothing (YMX,
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
     *  the converter takes as it takes a dump's. */
    static YmDump.Song song(Dumped read, String name) {
        byte[][] registers = new byte[YmDump.Song.YM_REGISTERS][read.frames()];
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
        report.say("YMX!: " + read.frames() + " frames at " + read.rate() + " Hz");
        int acts = acting(read);
        if (acts > 0) {
            report.note("this version reads the fourteen register streams and not"
                    + " the script: " + acts + " frames of it start or stop an"
                    + " effect, and the tune converts without them");
        }
        String stem = Path.of(in).getFileName().toString();
        YmToYmxr.Converted made = YmToYmxr.convert(
                song(read, stem.endsWith(".ymx") ? stem.substring(0, stem.length() - 4) : stem),
                flags, report);
        Files.write(Path.of(out), made.written().file());
        System.out.println(made.said());
        System.out.println("written: " + out);
    }
}
