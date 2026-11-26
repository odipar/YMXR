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
 * ticks are not modelled: a running effect's source, target and rate read
 * what the rows gave, and its place is not followed.
 *
 * <p>What the last step wrote is beside the state: {@code written}
 * reports each register's value where the row wrote it and -1 where not,
 * and each effect reports whether the row set it and which control bits
 * the row set. A reader reports that (SPEC.md 7).
 */
final class Replay {

    /**
     * One effect after a frame: source 0 means idle. {@code set} marks
     * an effect-column write; {@code timer}, {@code place} and {@code
     * deferred} record control bits 6, 5 and 3. {@code mapping} is the
     * kept mapping, initially 0 for identity, and {@code cut} the kept cut,
     * initially 0 for the whole table. A deferred start can run through a
     * different captured mapping and cut; this model omits ticks and
     * pending starts (SPEC.md 1.10.3, 1.11.3, 7.3).
     */
    record Effect(int target, int source, int select, int count, boolean started,
                  boolean set, boolean timer, boolean place, boolean deferred,
                  int mapping, int cut) {
        static final Effect NONE = new Effect(0, 0, 0, 0, false, false, false, false,
                false, 0, 0);
    }

    final int[] registers = new int[14];
    final int[] written = new int[14];
    /** The voices the last row syncs, bit 0 voice A to bit 2 voice C: bit
     *  5 of each coarse column in version 13, read on every row (SPEC.md
     *  1.12). */
    int syncs;
    final Effect[] effect = new Effect[4];
    boolean envelopeWritten;
    private final Table table;
    /** The effects used, whose bits 5 and 6 lay out the columns after 29
     *  (SPEC.md 3.3.5). */
    private final int effects;
    private int row;

    Replay(Table table, int effects) {
        this.table = table;
        this.effects = effects;
        Arrays.fill(registers, -1);
        Arrays.fill(effect, Effect.NONE);
    }

    /** The row number the next step reads, following the table's repeat. */
    int row() {
        return row;
    }

    /** One frame: the next row, as section 4 writes it. */
    void step() {
        byte[] r = new byte[table.columns()];
        for (int c = 0; c < r.length; c++) {
            r[c] = table.column(c)[row];
        }
        syncs = 0;
        for (int v = 0; Columns.syncs(effects) && v < 3; v++) {
            syncs |= (r[1 + 2 * v] & Columns.SYNC) != 0 ? 1 << v : 0;
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
            boolean timer = false;
            boolean place = false;
            boolean deferred = false;
            // Bit 4 marks the count column's 0 as the value the MFP counts
            // 256 for, and a player reads it where the row sets the control
            // column (SPEC.md 1.1, 1.9).
            boolean value = (r[t + 2] & 0x80) != 0
                    && (r[t + 2] & Columns.COUNT_VALUE) != 0;
            if ((r[t + 3] & 0xFF) != 0 || value) {
                count = r[t + 3] & 0xFF;
            }
            if ((r[t + 2] & 0x80) != 0) {
                select = r[t + 2] & 7;
                timer = (r[t + 2] & 0x40) != 0;
                place = (r[t + 2] & 0x20) != 0;
                deferred = (r[t + 2] & Columns.DEFERRED) != 0;
            }
            // A start or remap can replace the kept mapping, and a start or
            // recut the kept cut. A remap and a recut preserve the source.
            // A stop ignores its mapping and cut columns; the check reports
            // that invalid combination (SPEC.md 1.10, 1.11, 6.4).
            int m = Columns.mapping(effects, i);
            int x = Columns.cut(effects, i);
            boolean mapped = m >= 0 && (r[m] & 0x80) != 0;
            boolean cut = x >= 0 && (r[x] & 0x80) != 0;
            boolean stop = (r[t + 1] & 0xFF) == 0x80;
            int mapping = mapped && !stop ? r[m] & 0x7F : was.mapping();
            int kept = cut && !stop ? r[x] & 0x7F : was.cut();
            boolean set = ((r[t] | r[t + 1] | r[t + 2]) & 0x80) != 0 || r[t + 3] != 0 || mapped
                    || cut;
            effect[i] = new Effect(target, source, select, count, started, set, timer, place,
                    deferred, mapping, kept);
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
