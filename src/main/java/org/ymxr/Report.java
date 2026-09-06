package org.ymxr;

import java.util.ArrayList;
import java.util.List;

/** What a conversion has to say beside the file it writes. */
final class Report {

    private final List<String> notes = new ArrayList<>();

    /** Frames on which a drum on a voice kept a SID there from running. */
    int preempted;

    int sinus;
    int missingDrums;
    int overflow;
    int cutAtRepeat;

    void note(String text) {
        notes.add(text);
    }

    List<String> notes() {
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
