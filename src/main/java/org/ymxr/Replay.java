package org.ymxr;

import java.util.Arrays;
import org.dtx.Table;

/**
 * The frame procedure of SPEC.md section 4, as a model: what the fourteen
 * registers hold after each row, and what each effect runs. A reader in
 * the sense of R2.4, writing to no chip.
 *
 * <p>A register the model has not seen set is -1. The effects' ticks are
 * not modelled: a running effect's source, target and rate are what the
 * rows gave, and its place is not followed.
 */
final class Replay {

    /** What one effect runs after a row: source 0 where it runs nothing. */
    record Effect(int target, int source, int select, int count, boolean started) {
        static final Effect NONE = new Effect(0, 0, 0, 0, false);
    }

    final int[] registers = new int[14];
    final Effect[] effect = new Effect[4];
    boolean envelopeWritten;
    private final Table table;
    private int row;

    Replay(Table table) {
        this.table = table;
        Arrays.fill(registers, -1);
        Arrays.fill(effect, Effect.NONE);
    }

    /** The row number the next step takes, following the table's repeat. */
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
            if ((r[t + 2] & 0x80) != 0) {
                select = r[t + 2] & 7;
            }
            effect[i] = new Effect(target, source, select, count, started);
        }
        envelopeWritten = false;
        for (int c = 0; c < 13; c++) {
            if (Columns.BESIDE_COLUMN[c] >= 0) {
                boolean zero = (r[Columns.BESIDE_COLUMN[c]] & Columns.BESIDE_BIT[c]) != 0;
                if (r[c] != 0 || zero) {
                    registers[c] = r[c] & 0xFF;
                }
            } else if ((r[c] & 0x80) != 0) {
                registers[c] = r[c] & Columns.MASK[c];
            }
        }
        if ((r[13] & 0x80) != 0) {
            registers[13] = r[13] & 0x0F;
            envelopeWritten = true;
        }
    }
}
