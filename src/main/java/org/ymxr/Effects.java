package org.ymxr;

/**
 * The two effect slots of a YM frame, read as the schema's effects: a kind,
 * the target its ticks write, the value its source is built from, and the
 * timer's select and count (SPEC.md 1.8, 1.9).
 *
 * <p>YM6 gives each slot a kind in its code's bits 7 and 6; YM5 has no kind
 * bits, its first slot being a SID voice and its second a digidrum. A slot's
 * select is the MFP's own, 1 to 7, which is what the control column takes; a
 * slot whose select or count is 0 is empty, as the reference player reads it.
 */
final class Effects {

    /** A SID voice: a square wave on a volume register. */
    static final int SID = 1;

    /** A digidrum: a recording through a volume register. */
    static final int DRUM = 2;

    /** A sinus SID, for which the reference player runs an empty handler.
     *  The converter drops it. */
    static final int SINUS = 3;

    /** A sync buzzer: the envelope restarted at the timer's rate. */
    static final int BUZZER = 4;

    /** One slot of one frame. Kind 0 is an empty slot. */
    record Slot(int kind, int voice, int target, int data, int select, int count) {

        static final Slot EMPTY = new Slot(0, 0, 0, 0, 0, 0);

        boolean on() {
            return kind != 0;
        }
    }

    /** The registers a slot reads: its code, its select and its count. */
    private static final int[][] WHERE = {{1, 6, 14}, {3, 8, 15}};

    private Effects() {
    }

    /** The two slots of one frame. */
    static Slot[] of(YmDump.Song song, int frame) {
        boolean ym6 = song.format().equals("YM6!");
        byte[][] r = song.registers();
        Slot[] out = new Slot[2];
        for (int slot = 0; slot < 2; slot++) {
            int code = r[WHERE[slot][0]][frame] & 0xF0;
            int voice = ((code >> 4) & 3) - 1;
            int select = (r[WHERE[slot][1]][frame] & 0xFF) >> 5;
            int count = r[WHERE[slot][2]][frame] & 0xFF;
            if (voice < 0 || select == 0 || count == 0) {
                out[slot] = Slot.EMPTY;
                continue;
            }
            int kind = ym6 ? (code >> 6) + 1 : slot == 0 ? SID : DRUM;
            int target = kind == BUZZER ? 13 : 8 + voice;
            out[slot] = new Slot(kind, voice, target, r[8 + voice][frame] & 0x1F,
                    select, count);
        }
        return out;
    }
}
