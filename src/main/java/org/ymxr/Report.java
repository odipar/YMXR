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
 * library's own callers take a report that says nothing, so a test or a
 * corpus run over thousands of dumps prints only what it prints itself.
 *
 * <p>Lines go to standard error, and what a tool is for goes to standard
 * output: the summary line, and the trace tool's own rows. A run whose
 * output is read through a pipe or a file reads the same with the report
 * on as with it off, and the report is on the terminal beside it.
 *
 * <p>The progress line is redrawn over itself with a carriage return,
 * which a terminal overwrites and a file keeps, so it is drawn only
 * where standard error is a terminal.
 */
final class Report {

    /** How wide a name stands in a reported row. */
    private static final int NAME = 22;

    /** How wide the progress line is drawn, so that a shorter one written
     *  over a longer one leaves none of it behind. */
    private static final int WIDE = 44;

    private final List<String> notes = new ArrayList<>();

    /** Whether this report prints what the conversion does. */
    private final boolean says;

    private final PrintStream to;

    /** Whether the stream is read on a terminal, which the progress line
     *  asks before it draws. */
    private final boolean terminal;

    /** Whether a progress line stands on the terminal, to be cleared.
     *  A run over many files counts them from several threads, so the
     *  three methods that draw and clear the line take this object's
     *  lock and this field is read under it. */
    private boolean drawn;

    /** Frames on which a drum on a voice kept a SID there from running. */
    int preempted;

    int sinus;
    int missingDrums;
    int overflow;
    int cutAtRepeat;

    /** A report that prints nothing. */
    Report() {
        this(false, System.err);
    }

    /** A report that prints what the conversion does where {@code says}. */
    Report(boolean says) {
        this(says, System.err);
    }

    Report(boolean says, PrintStream to) {
        this(says, to, atTerminal());
    }

    /** The same, told whether the stream is a terminal, which is what
     *  decides the progress line and what a test names for itself. */
    Report(boolean says, PrintStream to, boolean terminal) {
        this.says = says;
        this.to = to;
        this.terminal = terminal;
    }

    /** Whether anything printed here is read: a caller that builds a line
     *  at some cost asks first. */
    boolean says() {
        return says;
    }

    /** One line, at the left margin. */
    synchronized void say(String line) {
        if (says) {
            clear();
            to.println(line);
        }
    }

    /** One line under the line above it. */
    void step(String line) {
        say("  " + line);
    }

    /** One row of a reported table: a name, then what it holds. */
    void row(String name, String what) {
        if (says) {
            say(String.format(Locale.ROOT, "  %-" + NAME + "s %s", name, what));
        }
    }

    /**
     * How far through a run of {@code of} steps this is, redrawn over the
     * line before it. Nothing is drawn where the report is silent or the
     * terminal is not where this is read, so a redirected run holds no
     * carriage returns.
     */
    synchronized void progress(String what, int done, int of) {
        if (!says || of <= 0 || !terminal) {
            return;
        }
        to.printf(Locale.ROOT, "\r  %-" + WIDE + "s",
                String.format(Locale.ROOT, "%s %d of %d (%d%%)",
                        what, done, of, done * 100 / of));
        to.flush();
        drawn = true;
    }

    /** The progress line taken off, so the next line stands on its own. */
    synchronized void clear() {
        if (drawn) {
            to.print("\r" + " ".repeat(WIDE + 2) + "\r");
            to.flush();
            drawn = false;
        }
    }

    /**
     * Whether the terminal is where this is read. Since JDK 22 {@code
     * System.console()} is an object whether or not the streams are a
     * terminal, and {@code isTerminal()} is what says so.
     */
    static boolean atTerminal() {
        java.io.Console console = System.console();
        return console != null && console.isTerminal();
    }

    synchronized void note(String text) {
        notes.add(text);
        say("  note: " + text);
    }

    synchronized List<String> notes() {
        List<String> out = new ArrayList<>(notes);
        if (sinus > 0) {
            out.add(sinus + " sinus SID frames dropped: the reference player runs"
                    + " an empty handler for them");
        }
        if (missingDrums > 0) {
            out.add(missingDrums + " digidrum triggers dropped: the file holds no"
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
