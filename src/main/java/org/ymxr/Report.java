package org.ymxr;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a conversion has to say beside the file it writes: the notes and
 * warnings a caller reads back, and, where a tool asks for it, a running
 * account of what the conversion did.
 *
 * <p>A tool reports by default and {@code -silent} turns it off. The
 * library's callers pass a report that emits no line, so a test or a
 * corpus run over thousands of dumps prints only what it prints itself.
 *
 * <p>Lines go to standard error, and what a tool is for goes to standard
 * output: the summary line, and the trace tool's rows. A run whose
 * output is read through a pipe or a file reads the same with the report
 * on as with it off, and the report is on the terminal beside it.
 *
 * <p>A long run reports its progress on separate lines, spaced
 * apart by a tenth of the run and by a second of the clock: a run that
 * ends within a second reports no progress, and one of minutes produces
 * about ten such lines. They are ordinary lines, so a run read into a file
 * contains them with the rest, and no run needs to know whether a
 * terminal is reading it.
 */
final class Report {

    /** How wide a name stands in a reported row. */
    private static final int NAME = 22;

    /** How long a run stays quiet about its progress, in nanoseconds. */
    private static final long APART = 1_000_000_000L;

    private final List<String> notes = new ArrayList<>();

    /** Whether this report prints what the conversion does. */
    private final boolean says;

    private final PrintStream to;

    /** The tenth of a run the last progress line gave, and the clock it
     *  stood at. A run over many files counts them from several threads,
     *  so progress claims this object's lock and both are read under it. */
    private int tenth = -1;

    private long last;

    /** Frames on which a drum on a voice kept a SID there from running. */
    int preempted;

    int sinus;
    int missingDrums;
    int overflow;
    int cutAtRepeat;

    /** A report that prints no line. */
    Report() {
        this(false, System.err);
    }

    /** A report that prints what the conversion does where {@code says}. */
    Report(boolean says) {
        this(says, System.err);
    }

    Report(boolean says, PrintStream to) {
        this.says = says;
        this.to = to;
        this.last = System.nanoTime();
    }

    /** Whether anything printed here is read: a caller that builds a line
     *  at some cost asks first. */
    boolean says() {
        return says;
    }

    /** One line, at the left margin. */
    synchronized void say(String line) {
        if (says) {
            to.println(line);
        }
    }

    /** One line under the line above it. */
    void step(String line) {
        say("  " + line);
    }

    /** One row of a reported table: a name, then its value. */
    void row(String name, String what) {
        if (says) {
            say(String.format(Locale.ROOT, "  %-" + NAME + "s %s", name, what));
        }
    }

    /**
     * How far through a run of {@code of} steps this is. A line is said
     * where the tenth of the run it stands in has moved and a second has
     * passed since the last, so a run that ends within a second says
     * no progress and one of minutes produces about ten such
     * lines.
     */
    synchronized void progress(String what, int done, int of) {
        if (!says || of <= 0) {
            return;
        }
        int now = done * 10 / of;
        long clock = System.nanoTime();
        if (now == tenth || clock - last < APART) {
            return;
        }
        tenth = now;
        last = clock;
        say(String.format(Locale.ROOT, "  %s %d of %d (%d%%)", what, done, of,
                done * 100 / of));
    }

    synchronized void note(String text) {
        notes.add(text);
        say("  note: " + text);
    }

    /** The notes a tool has still to print: the counted ones, which are
     *  reached only here, and, where the report is off, the ones it
     *  would have said where they happened. */
    synchronized List<String> unsaid() {
        List<String> all = notes();
        return says ? all.subList(notes.size(), all.size()) : all;
    }

    synchronized List<String> notes() {
        List<String> out = new ArrayList<>(notes);
        if (sinus > 0) {
            out.add(sinus + " sinus SID frames dropped: the reference player runs"
                    + " an empty handler for them");
        }
        if (missingDrums > 0) {
            out.add(missingDrums + " digidrum triggers dropped: the file has no"
                    + " sample at that number");
        }
        if (overflow > 0) {
            out.add(overflow + " effect frames dropped: a tune names at most "
                    + Sources.MOST + " sources");
        }
        if (cutAtRepeat > 0) {
            out.add(cutAtRepeat + " digidrums stopped at the row the tune repeats to");
        }
        if (preempted > 0) {
            out.add(preempted + " frames on which a drum kept a SID from running on its voice");
        }
        return out;
    }
}
