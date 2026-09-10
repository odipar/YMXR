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
            if (ymx.waitFor() != 0) {
                throw new IOException(DUMP + " did not read " + file);
            }
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted reading " + file, stopped);
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

    /** The opcodes an action byte's top three bits select (YMX, SPEC.md 3). */
    private static final int RESUME = 0;
    private static final int HOLD = 1;
    private static final int RELEASE = 2;
    private static final int START_TOGGLE = 3;
    private static final int RETUNE = 4;
    private static final int START_RETRIGGER = 5;
    private static final int START_PCM = 6;
    private static final int START_PCM_PREEMPT = 7;

    /** Voice 3 is no voice: three opcodes read it as a second form. */
    private static final int NO_VOICE = 3;

    /** The streams the script stands in (YMX, SPEC.md 2). */
    private static final int STREAM_X = 15;
    private static final int STREAM_A0 = 17;

    /** A source built for a channel, and the number it took. */
    private record Built(List<Sources.Source> list) {

        /** The number of a source of these rows, made where it is new: two
         *  channels running one shape run one source, as SPEC.md 2.2 has
         *  it. */
        int number(int kind, int data, byte[] rows, int repeat) {
            for (int at = 0; at < list.size(); at++) {
                Sources.Source have = list.get(at);
                if (have.repeat() == repeat && Arrays.equals(have.rows(), rows)) {
                    return at + 1;
                }
            }
            list.add(new Sources.Source(kind, data, rows, repeat));
            return list.size();
        }
    }

    /** A toggle stream's two rows: the loud half, then the silent one the
     *  marker stands in (SPEC.md 3.2). */
    private static byte[] toggle(int level) {
        return new byte[] {(byte) (level & 0x1F), (byte) 0x80};
    }

    /** A retrigger stream's one row, the shape under the marker. */
    private static byte[] retrigger(int shape) {
        return new byte[] {(byte) (0x80 | (shape & 0x0F))};
    }

    /**
     * The script's four channels as this schema's four effects.
     *
     * <p>A channel of YMX and an effect here are the same thing: a source
     * on a target at a timer's rate. What each opcode does to one is
     * SPEC.md 3's, and what a row of effect columns says is SPEC.md 1.8
     * and 1.9 here, so the walk below reads one and writes the other.
     */
    private static int script(Dumped read, byte[][] column, Built built,
                              byte[][] samples, int[] loops, int repeat,
                              Report report) {
        int used = 0;
        int[] target = {-1, -1, -1, -1};
        // The select each channel runs at. A row that sets the source
        // column sets bit 5 with it (section 6 rule 5) and writes the
        // control column, and select 0 there would stop the timer, so a
        // row that only reloads a source writes the select it is running.
        int[] select = {0, 0, 0, 0};
        // Whether each channel's timer runs. Bit 6 moves a running timer
        // and a stopped one starts on the select with it or without
        // (1.9), so a start sets the bit where the timer is stopped, as
        // section 6 rule 5 has it, and a start over a running stream
        // leaves it clear rather than restarting the period.
        boolean[] running = {false, false, false, false};
        List<String> left = new ArrayList<>();
        for (int f = 0; f < read.frames(); f++) {
            int master = read.streams()[STREAM_M][f] & 0xFF;
            // A preempt stops other channels (SPEC.md 3, opcode 7). The
            // channels act in channel order, so one that acts before the
            // preempt has written its row already: the stops are applied
            // after the frame's channels, and a stop fills the whole row.
            // A select left standing there would start the timer the stop
            // just stopped, since a stopped timer starts on the select
            // whether the row sets bit 6 or not (1.9).
            int preempted = 0;
            for (int c = 0; c < 4; c++) {
                if ((master & (1 << c)) == 0) {
                    continue;
                }
                int action = read.streams()[STREAM_A0 + 2 * c][f] & 0xFF;
                int count = read.streams()[STREAM_A0 + 2 * c + 1][f] & 0xFF;
                int opcode = action >> 5;
                int voice = (action >> 3) & 3;
                int low = action & 7;
                int at = Columns.EFFECT + 4 * c;
                int volume = voice == NO_VOICE ? 0
                        : read.streams()[8 + voice][f] & 0x1F;
                switch (opcode) {
                    case START_TOGGLE -> {
                        target[c] = 8 + voice;
                        column[at][f] = (byte) (0x80 | target[c]);
                        column[at + 1][f] = (byte) (0x80 | built.number(
                                Effects.SID, volume, toggle(volume), 0));
                        column[at + 2][f] = (byte) (0x80
                                | (running[c] ? 0 : Columns.TIMER_RESET)
                                | Columns.PLACE_RESET | low);
                        column[at + 3][f] = (byte) count;
                        select[c] = low;
                        running[c] = true;
                        used |= 1 << c;
                    }
                    case START_RETRIGGER -> {
                        // X bits 7 to 4 are the shape and 3 to 0 the
                        // channels a preempt stops (YMX, SPEC.md 2.2)
                        int shape = (read.streams()[STREAM_X][f] >> 4) & 0x0F;
                        target[c] = 13;
                        column[at][f] = (byte) (0x80 | 13);
                        column[at + 1][f] = (byte) (0x80 | built.number(
                                Effects.BUZZER, shape, retrigger(shape), 0));
                        column[at + 2][f] = (byte) (0x80
                                | (running[c] ? 0 : Columns.TIMER_RESET)
                                | Columns.PLACE_RESET | low);
                        column[at + 3][f] = (byte) count;
                        select[c] = low;
                        running[c] = true;
                        used |= 1 << c;
                    }
                    case START_PCM, START_PCM_PREEMPT -> {
                        if (volume >= samples.length) {
                            left.add("frame " + f + " starts sample " + volume
                                    + ", which the file does not carry");
                            break;
                        }
                        byte[] rows = samples[volume];
                        int loop = loops[volume];
                        target[c] = 8 + voice;
                        column[at][f] = (byte) (0x80 | target[c]);
                        column[at + 1][f] = (byte) (0x80 | built.number(
                                Effects.DRUM, volume, rows,
                                loop == 0xFFFF ? rows.length : loop));
                        column[at + 2][f] = (byte) (0x80
                                | (running[c] ? 0 : Columns.TIMER_RESET)
                                | Columns.PLACE_RESET | low);
                        column[at + 3][f] = (byte) count;
                        select[c] = low;
                        running[c] = true;
                        used |= 1 << c;
                        if (opcode == START_PCM_PREEMPT) {
                            preempted |= (read.streams()[STREAM_X][f] & 0x0F)
                                    & ~(1 << c);
                        }
                    }
                    case RELEASE -> {
                        column[at + 1][f] = (byte) 0x80;
                        running[c] = false;
                    }
                    case RETUNE -> {
                        // A new rate on a running stream, its place kept.
                        // Addressed to a voice it repatches the volume from
                        // the voice's byte first (YMX, SPEC.md 3.1), which
                        // is a source of the row count the effect already
                        // runs, so rule 5 lets the row leave bit 5 clear
                        // and move no place: the toggle keeps its phase and
                        // the half it stands in.
                        if (voice != NO_VOICE && target[c] >= 8 && target[c] <= 10) {
                            column[at + 1][f] = (byte) (0x80 | built.number(
                                    Effects.SID, volume, toggle(volume), 0));
                        }
                        column[at + 2][f] = (byte) (0x80 | low);
                        column[at + 3][f] = (byte) count;
                        select[c] = low;
                    }
                    case HOLD -> {
                        // HOLD's low bits are flags and not a prescaler
                        // (SPEC.md 2.4): 1 reloads the count, 2 the
                        // toggle's volume, 4 the retrigger's shape.
                        if ((low & 1) != 0) {
                            column[at + 3][f] = (byte) count;
                        }
                        int source = 0;
                        if ((low & 2) != 0 && target[c] >= 8 && target[c] <= 10) {
                            source = built.number(Effects.SID, volume,
                                    toggle(volume), 0);
                        } else if ((low & 4) != 0) {
                            int shape = (read.streams()[STREAM_X][f] >> 4) & 0x0F;
                            source = built.number(Effects.BUZZER, shape,
                                    retrigger(shape), 0);
                        }
                        if (source != 0) {
                            // The parameter is repatched and the stream runs
                            // on, so the place stands: the source has the row
                            // count the effect already runs and rule 5 lets
                            // the row leave bit 5 clear. The select it is
                            // running goes back, since select 0 there would
                            // stop the timer.
                            column[at + 1][f] = (byte) (0x80 | source);
                            column[at + 2][f] = (byte) (0x80 | select[c]);
                        }
                    }
                    case RESUME -> left.add("frame " + f + " resumes channel " + c
                            + ", which this version does not carry");
                    default -> left.add("frame " + f + " runs opcode " + opcode);
                }
            }
            for (int other = 0; other < 4; other++) {
                if ((preempted & (1 << other)) != 0) {
                    int at = Columns.EFFECT + 4 * other;
                    column[at][f] = 0;
                    column[at + 1][f] = (byte) 0x80;
                    column[at + 2][f] = 0;
                    column[at + 3][f] = 0;
                    running[other] = false;
                }
            }
        }
        // The row a tune repeats to stops every effect it does not start,
        // so the wrap lands on a known state whatever ran into it, as a
        // dump's conversion does it (Columns). Only the effects the tune
        // runs: a row sets no column of one it does not state (section 6
        // rule 2), so this waits until the walk says which run.
        if (repeat < read.frames()) {
            for (int c = 0; c < 4; c++) {
                int at = Columns.EFFECT + 4 * c;
                if ((used & (1 << c)) != 0 && column[at + 1][repeat] == 0) {
                    column[at + 1][repeat] = (byte) 0x80;
                }
            }
        }
        for (String said : left.subList(0, Math.min(left.size(), 3))) {
            report.note(said);
        }
        if (left.size() > 3) {
            report.note((left.size() - 3) + " more the script does that this"
                    + " version leaves behind");
        }
        return used;
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

        // The fourteen register streams first, through the columns a YM
        // dump's registers make: the streams are the chip's registers
        // without the effect bits, so no part of the script reaches them here.
        Columns registers = new Columns(song, new Sources(List.of()), repeat, report);
        byte[][] column = registers.column;

        // A voice a timer owns is one the frame leaves unwritten (YMX,
        // SPEC.md 2.1), which is what SPEC.md 6 rule 1 asks of a row here.
        // M's bits 7 to 5 are state and read only where bit 4 is set.
        int skip = 0;
        for (int f = 0; f < read.frames(); f++) {
            int master = read.streams()[STREAM_M][f] & 0xFF;
            if ((master & 0x10) != 0) {
                skip = (master >> 5) & 7;
            }
            for (int voice = 0; voice < 3; voice++) {
                if ((skip & (1 << voice)) != 0) {
                    column[8 + voice][f] = 0;
                }
            }
        }

        Built built = new Built(new ArrayList<>());
        int used = script(read, column, built, read.samples(), read.loops(),
                repeat, report);
        Sources sources = new Sources(List.copyOf(built.list()));
        Columns columns = new Columns(column, repeat, used);
        report.row("the effects", Integer.bitCount(used) + " of 4 run, "
                + sources.count() + " sources");
        Tune.Written written = Tune.write(columns, sources, read.rate(), unit, ring, report);
        Files.write(Path.of(out), written.file());
        System.out.println(read.frames() + " frames at " + read.rate() + " Hz, "
                + sources.count() + " sources, effects "
                + Integer.toBinaryString(used) + ": " + written.file().length + " bytes");
        System.out.println("written: " + out);
    }
}
