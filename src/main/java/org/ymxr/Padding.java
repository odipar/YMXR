package org.ymxr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.ymxs.YMXS.Row;
import org.ymxs.YMXS.Table;
import org.ymxs.YMXS.Tune;

/**
 * Rows added so that a tune's table packs at its unit (SPEC.md 6, rule
 * 6).
 *
 * <p>DTX packs a column in units of {@code k} bytes, so the row count
 * divides by {@code k} and so does the repeat row (DTX R5.6, R5.11). A
 * tune whose row count or repeat row does not divide packed at unit 1
 * instead, and a refill at unit 1 spans twice the rows of one at unit 2
 * and parses up to twice the blocks (experiments.md). So rows that set no
 * column go in: at the repeat row until it divides, which moves the loop's
 * first row later and lengthens what plays before it, then at the end
 * until the count divides. A row that sets no column writes no register
 * and leaves every timer running, so the tune plays one more frame there
 * with its effects running through it. At most {@code k} minus one rows
 * go in each place, and a tune that already divides is returned as it is.
 */
final class Padding {

    /** A row that sets no register and leaves every timer running. */
    static final Row UNSET = new Row(Map.of(), Map.of());

    private Padding() {
    }

    /** A tune padded, and the rows of its table that were added, in
     *  order: rows that set no column, which no frame of a dump answers
     *  to. Empty where the tune divided as it was. */
    record Padded(Tune tune, int[] added) {
    }

    /**
     * {@code tune} with rows added so that its table packs at {@code unit},
     * each addition noted on {@code report}.
     */
    static Padded toUnit(Tune tune, int unit, Report report) {
        if (unit <= 1) {
            return new Padded(tune, new int[0]);
        }
        Table<Row> table = tune.table();
        List<Row> rows = new ArrayList<>(table.rows());
        OptionalInt repeat = table.repeat();
        int at = repeat.isPresent() ? repeat.getAsInt() : -1;
        List<Integer> where = new ArrayList<>();
        if (at >= 0 && at % unit != 0) {
            int added = unit - at % unit;
            rows.addAll(at, java.util.Collections.nCopies(added, UNSET));
            for (int i = 0; i < added; i++) {
                where.add(at + i);
            }
            report.note(noted(added, at, true, unit));
            at += added;
        }
        if (rows.size() % unit != 0) {
            int added = unit - rows.size() % unit;
            report.note(noted(added, rows.size(), false, unit));
            for (int i = 0; i < added; i++) {
                where.add(rows.size() + i);
            }
            rows.addAll(java.util.Collections.nCopies(added, UNSET));
        }
        int[] added = where.stream().mapToInt(Integer::intValue).toArray();
        if (added.length == 0) {
            return new Padded(tune, added);
        }
        Table<Row> padded = new Table<>(List.copyOf(rows),
                at >= 0 ? OptionalInt.of(at) : OptionalInt.empty());
        return new Padded(new Tune(tune.title(), tune.composer(), tune.writer(), tune.rate(),
                padded), added);
    }

    /** The note, which the Go tree writes word for word. */
    static String noted(int added, int at, boolean beforeRepeat, int unit) {
        return "padded: " + added + (added == 1 ? " unset row" : " unset rows") + " at row "
                + at + (beforeRepeat ? ", before the repeat row" : "")
                + ", so the table packs at unit " + unit;
    }
}
