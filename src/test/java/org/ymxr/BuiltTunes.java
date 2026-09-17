package org.ymxr;

import java.util.List;

/**
 * The two YM dumps under {@code ym/test} that are built rather than
 * recorded, and the effects they carry that no recorded file does. The
 * files were written by the rig YMX had, and {@code BuiltTunesTest} checks
 * them against this one.
 *
 * <p>A YM6 frame files an effect in two slots, each three fields spread
 * across spare register bits: slot 1's code is R1 bits 7-4, its prescaler
 * R6 bits 7-5 and its count R14; slot 2's are R3, R8 and R15. A code's
 * bits 7-6 select the kind, 00 SID, 01 DigiDrum, 11 Sync-Buzzer, and its
 * bits 5-4 the voice plus one, so 00 leaves the slot idle. The parameter
 * stands in that voice's volume register: a SID's maximum volume, a
 * drum's sample number, a buzzer's envelope shape.
 */
final class BuiltTunes {

    /** The frames the preempt tune runs for. */
    static final int PREEMPT_FRAMES = 400;
    /** The frames the retrigger-retune tune runs for. */
    static final int RETRIGGER_RETUNE_FRAMES = 600;

    private BuiltTunes() {
    }

    /**
     * A sync buzzer whose shape and rate move on the same frame, running
     * unbroken from frame 8 on voice C: both step every fiftieth frame
     * together, so the row that sets them restarts a running source
     * with a new rate and a new shape, and the buzzer is never stopped.
     */
    static byte[] retriggerRetune() {
        int frames = RETRIGGER_RETUNE_FRAMES;
        byte[][] v = bed(frames);
        for (int f = 0; f < frames; f++) {
            v[8][f] = 12;
            v[9][f] = 12;
            if (f < 8) {
                v[10][f] = 11;
                continue;
            }
            int step = (f - 8) / 50;
            v[1][f] |= (byte) 0xF0;                 // sync-buzzer, voice C
            v[6][f] |= (byte) ((5 + step % 3) << 5);
            v[14][f] = (byte) (60 + step % 40);
            v[10][f] = (byte) (step % 2 == 0 ? 0x0A : 0x0C);
        }
        return GenYm.ym6File(frames, v);
    }

    /**
     * A drum arriving on the voice a SID runs on, which no recorded file
     * does: both slots address voice A, and the SID stays flagged on the
     * frame the drum lands, so it is running when the drum claims the
     * voice. The drum's start stops the SID first, and the SID starts
     * again when the drum ends.
     */
    static byte[] preempt() {
        int frames = PREEMPT_FRAMES;
        byte[][] v = bed(frames);
        byte[] drum = new byte[150];
        for (int i = 0; i < drum.length; i++) {
            drum[i] = (byte) (128 + 100 * Math.sin(i * 0.2));
        }
        for (int f = 0; f < frames; f++) {
            v[8][f] = 13;
            v[1][f] |= (byte) 0x10;                 // SID, voice A
            v[6][f] |= (byte) ((4 + (f / 20) % 4) << 5);
            v[14][f] = (byte) (60 + f % 40);
            if (f % 20 == 0 && f > 0) {             // DigiDrum, voice A
                v[3][f] |= (byte) 0x50;
                v[8][f] = (byte) (5 << 5);          // prescaler, sample 0
                v[15][f] = (byte) 80;
            }
        }
        return GenYm.ym6File(frames, v, drum);
    }

    /**
     * A tune built row by row rather than from a dump, so that it reaches
     * what no dump can: all four effects at once, on Timers A, D, B and C,
     * a frame rate of 60 Hz, and the rows section 4 allows that the
     * converter never writes. 120 rows repeating at row 30, every register
     * and every effect set on that row, so the wrap lands on a known state.
     *
     * <p>Effect 0, Timer A, a two-row SID source on R8 from row 2: a count
     * change alone, a select change alone with the count column 0, bit 5
     * alone with the select it has, bit 5 alone with a new source on the
     * running timer, bit 6 alone with the count column 0, a stop with the
     * volume set, the same source started again, its target set to R1
     * while it runs and read at the next start. Effect 1, Timer D, a
     * drum of 40 rows on R10 at select 7 from row 5, ending by its
     * marker, its closing row 5. Effect 2, Timer B, a one-row buzzer on
     * R13 from row 3, a row setting R13 beside it, restarted with a new
     * rate. Effect 3, Timer C, a five-row source repeating to its row 2 on
     * R9 from row 30 while effect 1 runs at another select, a count
     * change, a stop, and a stop with no source running. Rows 12 and 13
     * write a tone fine byte 0 and one that is not 0 with the coarse
     * column's bit 6, with and without the coarse set bit; rows 14 and 16
     * an envelope period byte 0 and one that is not 0 by the bit beside it
     * with R13 unset. Unset columns carry the arbitrary bits 1.1 allows: values
     * under a clear bit 7.
     */
    static Tune.Written fourTimers() {
        int frames = 120;
        int repeat = 30;
        byte[][] c = new byte[Columns.C][frames];
        int[] bed = {0x40, 1, 0x30, 2, 0x20, 3, 5, 0x38, 12, 12, 11, 60, 1, 8};
        for (int f = 0; f < frames; f++) {
            boolean all = f == 0 || f == repeat;
            for (int r = 0; r < 14; r++) {
                if (all) {
                    int v = bed[r] + (f == repeat ? 1 : 0);
                    c[r][f] = (byte) (r == 13 || Columns.BESIDE_COLUMN[r] < 0 ? 0x80 | v : v);
                }
            }
            if (f > 0 && f % 5 == 1) {
                c[0][f] = (byte) (0x40 + f % 90);           // tone A fine, a value
            }
            if (f % 7 == 3) {
                c[2][f] = (byte) (0x30 + f % 40);
                c[3][f] = (byte) (0x80 | (2 + f / 40 % 2));
            }
            if (f % 11 == 4) {
                c[6][f] = (byte) (0x80 | f % 32);
            }
            // what an unset column may hold: values under a clear bit 7,
            // off bits 6 and 5 of the columns beside a fine byte and R13
            if (f % 9 == 8) {
                c[8][f] = 0x0F;
                c[Columns.EFFECT + 1][f] = 0x05;
                c[Columns.EFFECT + 2][f] = 0x07;
                c[13][f] = 0x0A;
            }
        }
        c[0][12] = 0;                                    // fine 0 without the coarse set bit
        c[1][12] = 0x40;
        c[0][13] = 0x55;                                 // fine not 0 with the coarse's bit 6
        c[1][13] = 0x40;
        c[0][17] = 0x56;                                 // the same with the coarse set
        c[1][17] = (byte) (0x80 | 0x40 | 2);
        c[11][14] = 0;                                   // envelope fine 0 by the bit beside it
        c[13][14] = 0x40;
        c[11][18] = 0x33;                                // envelope fine not 0 with the bit
        c[13][18] = 0x40;
        c[12][16] = 0;                                   // envelope coarse 0 the same way
        c[13][16] = 0x20;
        int start = 0x80 | Columns.TIMER_RESET | Columns.PLACE_RESET;
        int e0 = Columns.EFFECT;
        int e1 = Columns.EFFECT + 4;
        int e2 = Columns.EFFECT + 8;
        int e3 = Columns.EFFECT + 12;
        // effect 0, Timer A: the SID on R8
        c[e0][2] = (byte) (0x80 | 8);
        c[e0 + 1][2] = (byte) (0x80 | 1);
        c[e0 + 2][2] = (byte) (start | 5);
        c[e0 + 3][2] = 100;
        c[e0 + 3][10] = 90;                              // the count alone
        c[e0 + 2][20] = (byte) (0x80 | 4);               // the select alone, the count kept
        c[e0 + 2][34] = (byte) (0x80 | Columns.PLACE_RESET | 4);   // bit 5 alone
        c[e0 + 1][38] = (byte) (0x80 | 5);               // a new source, bit 5 alone
        c[e0 + 2][38] = (byte) (0x80 | Columns.PLACE_RESET | 4);
        c[e0 + 2][42] = (byte) (0x80 | Columns.TIMER_RESET | 4);   // bit 6 alone, the count kept
        c[e0 + 1][50] = (byte) 0x80;                     // a stop, the volume set
        c[8][50] = (byte) (0x80 | 12);
        c[e0 + 1][60] = (byte) (0x80 | 1);               // the same source again
        c[e0 + 2][60] = (byte) (start | 5);
        c[e0 + 3][60] = 100;
        c[e0][70] = (byte) (0x80 | 1);                   // the target, for the next start
        c[e0 + 1][80] = (byte) (0x80 | 1);               // started on R1, R8 set again
        c[e0 + 2][80] = (byte) (start | 5);
        c[e0 + 3][80] = 100;
        c[8][80] = (byte) (0x80 | 12);
        c[e0][repeat] = (byte) (0x80 | 8);               // the keyframe restarts it on R8
        c[e0 + 1][repeat] = (byte) (0x80 | 1);
        c[e0 + 2][repeat] = (byte) (start | 5);
        c[e0 + 3][repeat] = 100;
        // effect 1, Timer D: the drum on R10, ending by its marker
        c[e1][5] = (byte) (0x80 | 10);
        c[e1 + 1][5] = (byte) (0x80 | 2);
        c[e1 + 2][5] = (byte) (start | 7);
        c[e1 + 3][5] = (byte) 200;
        c[e1 + 1][repeat] = (byte) (0x80 | 2);           // the keyframe strikes it again
        c[e1 + 2][repeat] = (byte) (start | 6);
        c[e1 + 3][repeat] = (byte) 100;
        c[e1 + 1][95] = (byte) (0x80 | 2);               // again, running into the wrap
        c[e1 + 2][95] = (byte) (start | 7);
        c[e1 + 3][95] = (byte) 200;
        // effect 2, Timer B: the buzzer on R13
        c[e2][3] = (byte) (0x80 | 13);
        c[e2 + 1][3] = (byte) (0x80 | 3);
        c[e2 + 2][3] = (byte) (start | 6);
        c[e2 + 3][3] = 80;
        c[13][15] = (byte) (0x80 | 0x0C);                // R13 set beside the buzzer
        c[e2 + 1][25] = (byte) (0x80 | 3);               // restarted with a new rate
        c[e2 + 2][25] = (byte) (start | 7);
        c[e2 + 3][25] = 60;
        c[e2][repeat] = (byte) (0x80 | 13);              // the keyframe restarts it
        c[e2 + 1][repeat] = (byte) (0x80 | 3);
        c[e2 + 2][repeat] = (byte) (start | 6);
        c[e2 + 3][repeat] = 80;
        // effect 3, Timer C: the looping source on R9, beside Timer D
        c[e3][repeat] = (byte) (0x80 | 9);
        c[e3 + 1][repeat] = (byte) (0x80 | 4);
        c[e3 + 2][repeat] = (byte) (start | 3);
        c[e3 + 3][repeat] = (byte) 150;
        c[e3 + 3][50] = (byte) 120;                      // the count alone
        c[e3 + 1][100] = (byte) 0x80;                    // a stop, R9 set
        c[9][100] = (byte) (0x80 | 12);
        c[e3 + 1][105] = (byte) 0x80;                    // a stop with no source running
        byte[] sid = {12, (byte) 0x80};
        byte[] drum = new byte[40];
        for (int i = 0; i < 39; i++) {
            drum[i] = (byte) (8 + (i % 3 == 0 ? 7 : -8) * ((39 - i) / 13));
        }
        drum[39] = (byte) (0x80 | 5);
        byte[] buzzer = {(byte) (0x80 | 0x0A)};
        byte[] looping = {3, 6, 9, 12, (byte) (0x80 | 15)};
        byte[] fifth = {10, 5, (byte) (0x80 | 1)};
        Sources sources = new Sources(List.of(
                Sources.Source.of(Effects.SID, 12, sid, 0),
                Sources.Source.of(Effects.DRUM, 0, drum, drum.length),
                Sources.Source.of(Effects.BUZZER, 0x0A, buzzer, 0),
                Sources.Source.of(Effects.SID, 15, looping, 2),
                Sources.Source.of(Effects.SID, 10, fifth, 1)));
        Columns columns = new Columns(c, repeat, 0b1111);
        return Tune.write(columns, sources, 60, YmToYmxr.UNIT, Tune.RING, new Report());
    }

    /**
     * The four kinds of target that write several registers (SPEC.md
     * 2.1), one an effect, so the kit has a tune of version 4: a voice on
     * Timer A, a noise on Timer D, a buzzer on Timer B and a tone on Timer
     * C. The marker stands in a different column under each kind - the
     * coarse nibble of a voice and a tone, the noise period of a noise,
     * the envelope shape of a buzzer - so a reader that reads the marker
     * in column 0 alone reports the wrong rows for three of the four.
     *
     * <p>The shapes beside that: a source of several columns repeating to
     * row 0, one repeating to a row above it, one that plays once and
     * stops its timer at its marker, and a start over a running source of
     * the same row count on the same kept target, which leaves the place
     * where it stands (rule 3(a)).
     */
    static Tune.Written voices() {
        int frames = 96;
        int repeat = 32;
        byte[][] c = new byte[Columns.C][frames];
        // The registers no effect runs: the mixer with all three voices
        // heard and the noise on voice C, voice B's period, and voice C's
        // fine byte. An effect owns R0, R1 and R8 on Timer A, R6 and R10
        // on Timer D, R11 to R13 on Timer B and R2 and R3 on Timer C, and
        // rule 1 has every row leave those columns unset.
        for (int f : new int[] {0, repeat}) {
            c[4][f] = (byte) 0x60;
            c[5][f] = (byte) (0x80 | 1);
            c[7][f] = (byte) (0x80 | 0x20);
            c[9][f] = (byte) (0x80 | 10);
        }
        int start = 0x80 | Columns.TIMER_RESET | Columns.PLACE_RESET;
        int e0 = Columns.EFFECT;
        int e1 = Columns.EFFECT + 4;
        int e2 = Columns.EFFECT + 8;
        int e3 = Columns.EFFECT + 12;
        // effect 0, Timer A: setVoiceA, three registers, the marker in the
        // coarse nibble; started again at the repeat row on the same target
        c[e0][0] = (byte) (0x80 | 17);
        c[e0 + 1][0] = (byte) (0x80 | 1);
        c[e0 + 2][0] = (byte) (start | 5);
        c[e0 + 3][0] = 100;
        c[e0 + 1][repeat] = (byte) (0x80 | 1);
        c[e0 + 2][repeat] = (byte) (start | 5);
        c[e0 + 3][repeat] = 100;
        c[e0 + 3][40] = 80;                              // the count alone
        // effect 1, Timer D: setNoiseC, two registers, the marker in the
        // noise period, which the handler writes last of the two
        c[e1][1] = (byte) (0x80 | 24);
        c[e1 + 1][1] = (byte) (0x80 | 2);
        c[e1 + 2][1] = (byte) (start | 6);
        c[e1 + 3][1] = (byte) 200;
        c[e1 + 1][repeat] = (byte) (0x80 | 2);
        c[e1 + 2][repeat] = (byte) (start | 6);
        c[e1 + 3][repeat] = (byte) 200;
        // effect 2, Timer B: setBuzzer, three registers, the marker in the
        // envelope shape; the source plays once, so a tick stops the timer
        // at its last row, and R13 stands outside rule 1 (1(c))
        c[e2][2] = (byte) (0x80 | 21);
        c[e2 + 1][2] = (byte) (0x80 | 3);
        c[e2 + 2][2] = (byte) (start | 7);
        c[e2 + 3][2] = (byte) 250;
        c[13][20] = (byte) (0x80 | 8);                   // R13 set while it runs
        c[e2 + 1][repeat] = (byte) (0x80 | 3);
        c[e2 + 2][repeat] = (byte) (start | 7);
        c[e2 + 3][repeat] = (byte) 250;
        c[e2 + 1][70] = (byte) 0x80;                     // a stop, R11 to R13 set
        c[11][70] = 0;
        c[12][70] = 0;
        c[13][70] = (byte) 0x80;
        // effect 3, Timer C: setToneB, two registers, the marker in the
        // coarse nibble; the second start has the row count of the first on
        // the kept target and leaves bit 5 at 0, so the place stands where
        // the ticks left it (rule 3(a))
        c[e3][4] = (byte) (0x80 | 15);
        c[e3 + 1][4] = (byte) (0x80 | 4);
        c[e3 + 2][4] = (byte) (start | 4);
        c[e3 + 3][4] = (byte) 150;
        c[e3 + 1][48] = (byte) (0x80 | 5);               // the place kept
        c[e3 + 2][48] = (byte) (0x80 | 4);
        c[e3 + 1][repeat] = (byte) (0x80 | 4);
        c[e3 + 2][repeat] = (byte) (start | 4);
        c[e3 + 3][repeat] = (byte) 150;
        // A source of C columns: column i is the value register i of its
        // target reads, and the marker stands in bit 7 of the column the
        // target names (SPEC.md 2.1, 3.2.1).
        byte[][] voice = {{0, (byte) 0x80, 0, (byte) 0x40, 0, (byte) 0xC0},
                          {1, 1, 2, 2, 3, (byte) (0x80 | 3)},
                          {15, 13, 11, 9, 7, 5}};
        byte[][] noise = {{4, 9, 14, 19, (byte) (0x80 | 24)},
                          {15, 12, 10, 8, 6}};
        byte[][] buzzer = {{0, 0x40, (byte) 0x80, (byte) 0xC0},
                           {1, 1, 2, 2},
                           {8, 10, 12, (byte) (0x80 | 14)}};
        byte[][] tone = {{(byte) 0x30, (byte) 0x60, (byte) 0x90, (byte) 0xC0},
                         {1, 1, 2, (byte) (0x80 | 2)}};
        byte[][] fifth = {{(byte) 0x20, (byte) 0x50, (byte) 0x80, (byte) 0xB0},
                          {2, 2, 3, (byte) (0x80 | 3)}};
        Sources sources = new Sources(List.of(
                new Sources.Source(Effects.SID, 0, voice, 0),
                new Sources.Source(Effects.SID, 0, noise, 2),
                new Sources.Source(Effects.BUZZER, 0, buzzer, 4),
                new Sources.Source(Effects.SID, 0, tone, 0),
                new Sources.Source(Effects.SID, 0, fifth, 0)));
        Columns columns = new Columns(c, repeat, 0b1111);
        return Tune.write(columns, sources, 50, YmToYmxr.UNIT, Tune.RING, new Report());
    }

    /** Three tones and no noise, the same under both tunes' effects. */
    private static byte[][] bed(int frames) {
        byte[][] v = new byte[16][frames];
        for (int f = 0; f < frames; f++) {
            v[0][f] = (byte) (0x40 + f % 90);
            v[1][f] = 1;
            v[2][f] = (byte) (0x30 + f % 40);
            v[3][f] = 2;
            v[4][f] = (byte) (0x20 + f % 30);
            v[5][f] = 3;
            v[6][f] = (byte) (f % 32);
            v[7][f] = (byte) 0x38;
            v[9][f] = 12;
            v[10][f] = 11;
            v[11][f] = (byte) (f * 3);
            v[12][f] = (byte) (f / 64);
            v[13][f] = (byte) GenYm.NO_ENVELOPE_CHANGE;
        }
        return v;
    }
}
