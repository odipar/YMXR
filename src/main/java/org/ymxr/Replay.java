package org.ymxr;

import java.util.Arrays;
import org.dtx.Table;

/**
 * The frame procedure of SPEC.md section 4, as a model: what the fourteen
 * registers are after each row, and what each effect runs. A reader in
 * the sense of R2.4, writing to no chip.
 *
 * <p>A register the model has not seen set is -1, and so is a register an
 * effect runs on, from the row after the one that starts it. The effects'
 * ticks are not modelled: a running effect's source, target and rate are
 * what the rows gave, and its place is not followed.
 *
 * <p>What the last step wrote stands beside the state: {@code written}
 * reports each register's value where the row wrote it and -1 where not,
 * and each effect says whether the row touched it and which control bits
 * the row set. That is what a reader reports (SPEC.md 7).
 */
final class Replay {

    /** What one effect runs after a row: source 0 where it runs no source.
     *  {@code touched} says the row set one of its columns, {@code timer}
     *  that the row's control column had bit 6 and {@code place} bit 5. */
    record Effect(int target, int source, int select, int count, boolean started,
                  boolean touched, boolean timer, boolean place) {
        static final Effect NONE = new Effect(0, 0, 0, 0, false, false, false, false);
    }

    final int[] registers = new int[14];
    final int[] written = new int[14];
    final Effect[] effect = new Effect[4];
    boolean envelopeWritten;
    private final Table table;
    private int row;

    Replay(Table table) {
        this.table = table;
        Arrays.fill(registers, -1);
        Arrays.fill(effect, Effect.NONE);
    }

    /** The row number the next step reads, following the table's repeat. */
    int row() {
        return row;
    }

    /** One frame: the next row, as section 4 writes it. */
    void step() {
        byte[] r = new byte[Columns.C];
        for (int c = 0; c < Columns.C; c++) {
            r[c] = table.column(c)[row];
        }
        row++;
        if (row == table.rows()) {
            row = table.repeat();
        }
        for (int i = 0; i < 4; i++) {
            int t = Columns.EFFECT + 4 * i;
            Effect was = effect[i];
            // A row leaves the column of a volume register an effect runs
            // on unset (SPEC.md 6 rule 1), so the model has no value for
            // it: the row that stops the effect sets the register again.
            if (was.source() != 0 && was.target() < 13) {
                registers[was.target()] = -1;
            }
            int target = (r[t] & 0x80) != 0 ? r[t] & 0x7F : was.target();
            int source = was.source();
            boolean started = false;
            if ((r[t + 1] & 0x80) != 0) {
                source = r[t + 1] & 0x7F;
                started = source != 0;
            }
            int select = was.select();
            int count = was.count();
            if ((r[t + 3] & 0xFF) != 0) {
                count = r[t + 3] & 0xFF;
            }
            boolean timer = false;
            boolean place = false;
            if ((r[t + 2] & 0x80) != 0) {
                select = r[t + 2] & 7;
                timer = (r[t + 2] & 0x40) != 0;
                place = (r[t + 2] & 0x20) != 0;
            }
            boolean touched = ((r[t] | r[t + 1] | r[t + 2]) & 0x80) != 0 || r[t + 3] != 0;
            effect[i] = new Effect(target, source, select, count, started, touched, timer, place);
        }
        envelopeWritten = false;
        Arrays.fill(written, -1);
        for (int c = 0; c < 13; c++) {
            if (Columns.BESIDE_COLUMN[c] >= 0) {
                boolean zero = (r[Columns.BESIDE_COLUMN[c]] & Columns.BESIDE_BIT[c]) != 0;
                if (r[c] != 0 || zero) {
                    registers[c] = r[c] & 0xFF;
                    written[c] = registers[c];
                }
            } else if ((r[c] & 0x80) != 0) {
                registers[c] = r[c] & Columns.MASK[c];
                written[c] = registers[c];
            }
        }
        if ((r[13] & 0x80) != 0) {
            registers[13] = r[13] & 0x0F;
            written[13] = registers[13];
            envelopeWritten = true;
        }
    }
}
