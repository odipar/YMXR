package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.dtx.St4;
import org.ymxs.tool.Tool;

/**
 * A YM5!/YM6! dump into a tune file.
 *
 * <pre>
 * ym-to-ymxr in.ym out.ymxr [-kK] [-mN] [-rRR] [-copies[S]] [-silent]
 * </pre>
 *
 * {@code -k} is the unit the table packs at, 2 by default; {@code -m} the
 * ring in bytes, 960; {@code -r} the row the tune repeats to, the dump's
 * loop frame by default, and {@code -r} alone a tune that plays once.
 * The tool reports what it read, the flags it took, the sources it found
 * and what each column packed to; {@code -silent} leaves the summary
 * line and the notes.
 */
public final class YmToYmxr {

    /** The unit a table packs at unless {@code -k} names another. */
    static final int UNIT = 2;

    /** The flag that turns a tool's report off. Every tool reads it, and
     *  the tools that accept the converter's flags pass it through. */
    static final String SILENT = "-silent";

    private YmToYmxr() {
    }

    /** A conversion: the tune file written, the dump's frame the tune
     *  repeats to, or its frame count where it plays once, and what the
     *  tool prints of it. */
    /** {@code frames} is the frame of the dump each row of the table
     *  answers to, once the table is padded to its unit (SPEC.md 6, rule
     *  6): -1 for a row that sets no column, and the rows of a loop
     *  written again answer to its frames again. */
    record Converted(Tune.Written written, int repeat, Sources sources, String said,
                     int[] frames) {
    }

    /** The dump converted with the tool's flags, {@code -kK}, {@code -mN},
     *  {@code -rRR} or {@code -r}, {@code -copies[S]}, as the tool does
     *  it. */
    static Converted convert(byte[] dump, List<String> flags, Report report) {
        return convert(YmDump.read(dump), flags, report);
    }

    static Converted convert(YmDump.Song song, List<String> flags, Report report) {
        int unit = UNIT;
        int ring = Tune.RING;
        int repeat = -1;
        boolean once = false;
        boolean copies = false;
        double seconds = 0;
        for (String flag : flags) {
            if (flag.startsWith("-k")) {
                unit = Integer.parseInt(flag.substring(2));
            } else if (flag.startsWith("-m")) {
                ring = Integer.parseInt(flag.substring(2));
            } else if (flag.startsWith("-copies")) {
                // the packer's, spelled as DTX's dtx-write spells it:
                // a match beyond the ring copies from the column's literal
                // stream, which packs a small ring far smaller,
                // and -copiesS searches S seconds for a better parse
                copies = true;
                seconds = flag.length() > 7 ? Double.parseDouble(flag.substring(7)) : 0;
            } else if (flag.equals("-r")) {
                once = true;
            } else if (flag.startsWith("-r")) {
                repeat = Integer.parseInt(flag.substring(2));
            } else if (!flag.equals(SILENT)) {
                throw new IllegalArgumentException("not a flag of the tool: " + flag);
            }
        }
        if (once) {
            repeat = song.frames();
        } else if (repeat > song.frames()) {
            throw new IllegalArgumentException("the repeat row " + repeat + " is past the dump's "
                    + song.frames() + " frames");
        } else if (repeat < 0) {
            repeat = (int) Math.min(song.loopFrame(), song.frames());
            if (song.loopFrame() >= song.frames()) {
                report.note("the dump's loop frame " + song.loopFrame()
                        + " is past its last frame: the tune plays once");
            }
        }
        read(report, song);
        flagsRead(report, flags, unit, ring, repeat, once, copies, seconds);
        Sources sources = new Sources(song);
        Padding.Padded padded = Padding.toUnit(Ym.read(song, sources, repeat, report), unit,
                report);
        Schema.Made made = Schema.of(padded.tune());
        Columns columns = made.columns();
        found(report, sources, columns);
        Tune.Written written = Tune.write(columns, made.sources(), song.playerHz(), unit, ring,
                copies ? new St4(true, seconds) : new St4(), report, song.name());
        String said = song.frames() + " frames at " + song.playerHz() + " Hz, "
                + sources.count() + " sources, effects " + Integer.toBinaryString(columns.effects)
                + ", repeats at " + (repeat < song.frames() ? "row " + written.repeat() : "no row")
                + ": " + written.file().length + " bytes";
        return new Converted(written, repeat, sources, said, padded.frames());
    }

    /** The dump as it was read. */
    private static void read(Report report, YmDump.Song song) {
        String title = song.name().isBlank() ? "(untitled)" : song.name().strip();
        report.say(song.format() + ": " + title
                + (song.author().isBlank() ? "" : " by " + song.author().strip()));
        int seconds = song.frames() / Math.max(1, song.playerHz());
        report.row("frames", String.format(Locale.ROOT, "%d at %d Hz (%d:%02d)%s",
                song.frames(),
                song.playerHz(), seconds / 60, seconds % 60,
                song.interleaved() ? ", interleaved" : ", a record a frame"));
        report.row("the loop frame", song.loopFrame() + " of the dump's header");
        if (song.digidrums() > 0) {
            int bytes = 0;
            for (byte[] drum : song.drums()) {
                bytes += drum.length;
            }
            report.row("digidrums", song.digidrums() + " of " + bytes + " bytes in all");
        }
    }

    /** The flags the tool read and what each came to. */
    private static void flagsRead(Report report, List<String> flags, int unit, int ring, int repeat,
                              boolean once, boolean copies, double seconds) {
        report.say("the flags: " + (flags.isEmpty() ? "none, so the defaults below"
                : String.join(" ", flags)));
        report.row("-k, the unit", unit + (unit == UNIT ? ", the default" : ", asked for"));
        report.row("-m, the ring", ring + " bytes"
                + (ring == Tune.RING ? ", the default" : ", asked for"));
        report.row("-r, the repeat row", once ? "none, the tune plays once"
                : repeat + (flags.stream().anyMatch(f -> f.startsWith("-r")) ? ", asked for"
                : ", the dump's loop frame"));
        report.row("-copies", !copies ? "no, the default: a match beyond the ring is not packed"
                : seconds == 0 ? "yes, the opening passes alone"
                : "yes, " + seconds + " seconds of search, which packs another parse a run");
    }

    /** The sources and the effects the dump's frames came to. */
    private static void found(Report report, Sources sources, Columns columns) {
        if (!report.says()) {
            return;
        }
        int[] rows = new int[Effects.BUZZER + 1];
        int[] kinds = new int[Effects.BUZZER + 1];
        for (Sources.Source source : sources.all()) {
            kinds[source.kind()]++;
            rows[source.kind()] += source.rows();
        }
        report.say("the effects: " + Integer.bitCount(columns.effects) + " of 4 run, "
                + sources.count() + (sources.count() == 1 ? " source" : " sources")
                + ", at most " + Sources.MOST);
        for (int kind = 0; kind <= Effects.BUZZER; kind++) {
            if (kinds[kind] > 0) {
                report.row(kindName(kind), kinds[kind] + ", " + rows[kind]
                        + (rows[kind] == 1 ? " row in all" : " rows in all"));
            }
        }
    }

    /** What a source of a kind is called. */
    private static String kindName(int kind) {
        return switch (kind) {
            case Effects.SID -> "square waves";
            case Effects.DRUM -> "digidrums";
            case Effects.SINUS -> "sinus SIDs";
            case Effects.BUZZER -> "buzzers";
            default -> "kind " + kind;
        };
    }

    public static void main(String[] args) {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ym-to-ymxr", flags, "-k", "-m", "-copies", "-r");
        Ymxs.only(tool, flags, Ymxs.PACKING, Ymxs.ROWS);
        // The call is read before the input is, so a flag that is not a
        // number is an exit of 2 and standard input is left unread.
        Ymxs.numbers(tool, flags);
        Report report = new Report(tool.reports());
        Converted converted;
        try {
            converted = convert(tool.bytes(), flags, report);
        } catch (YmDump.FormatException | IllegalArgumentException | IllegalStateException no) {
            throw tool.wrong(Tool.WRONG, String.valueOf(no.getMessage()));
        }
        tool.report(converted.said());
        // A note is a warning and stands whether the report is on or off.
        // Where the report is on, a note it said where it happened is not
        // said twice, and the counted ones are reached only here.
        for (String note : report.unsaid()) {
            System.err.println("  " + note);
        }
        Out.write(tool, converted.written().file());
    }

}
