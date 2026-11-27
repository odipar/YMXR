package org.ymxr;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dtx.Table;
import org.ymxs.tool.Tool;

/**
 * What a reader reports of a tune (SPEC.md 7): one line a play call, the
 * call's result, the registers the frame writes and the effects the row
 * set. The kit's references are the output of this tool, and the rig checks
 * the 68000 player to it.
 */
final class Trace {

    private Trace() {
    }

    /** The calls the kit uses for a tune: one pass and the loop once, or
     *  the pass and the call that reports its end. */
    static int calls(Table table) {
        int rows = table.rows();
        int repeat = table.repeat();
        return repeat < rows ? rows + rows - repeat : rows + 1;
    }

    /**
     * The record's header, including transformation entries after
     * sources where the file has mapping columns, and the cuts where it has
     * cut columns; copy from frame has no table and is left out (SPEC.md
     * 7.2).
     */
    static String header(TuneFile tune) {
        StringBuilder line = new StringBuilder("{\"rate\":").append(tune.frameRate())
                .append(",\"effects\":").append(tune.effects() & 0xFF).append(",\"sources\":[");
        for (int i = 0; i < tune.sources().size(); i++) {
            Table s = tune.sources().get(i);
            line.append(i == 0 ? "" : ",").append("{\"rows\":[");
            // a byte a column of the row, row 0's columns first (SPEC.md 7.2)
            for (int r = 0; r < s.rows(); r++) {
                for (int c = 0; c < s.columns(); c++) {
                    line.append(r == 0 && c == 0 ? "" : ",").append(s.column(c)[r] & 0xFF);
                }
            }
            line.append("],\"repeat\":").append(s.repeat()).append('}');
        }
        line.append(']');
        if (Columns.maps(tune.effects())) {
            line.append(",\"transformations\":[");
            for (int n = 0; n < tune.transformations().size(); n++) {
                byte[] entries = tune.transformations().get(n);
                line.append(n == 0 ? "" : ",").append('[');
                for (int k = 0; k < entries.length; k++) {
                    line.append(k == 0 ? "" : ",").append(entries[k] & 0xFF);
                }
                line.append(']');
            }
            line.append(']');
        }
        if (Columns.cuts(tune.effects())) {
            line.append(",\"cuts\":[");
            for (int n = 0; n < tune.cuts().size(); n++) {
                TuneFile.Cut one = tune.cuts().get(n);
                line.append(n == 0 ? "" : ",").append("{\"first\":").append(one.first())
                        .append(",\"last\":").append(one.last()).append('}');
            }
            line.append(']');
        }
        return line.append("}\n").toString();
    }

    /**
     * Writes the header and up to {@code calls} frame entries to {@code
     * out}, one per line. A frame reporting -1 ends the record. Effect
     * entries of versions 10 to 12 report the kept mapping, and of versions
     * 11 and 12 the kept cut; ticks and a pending start's mapping and cut
     * are outside the record (SPEC.md 7.3).
     */
    static void trace(TuneFile tune, int calls, PrintStream out) {
        out.print(header(tune));
        boolean mapped = tune.version() >= Tune.VERSION;
        boolean cuts = tune.version() >= Tune.CUT;
        Table table = tune.table();
        Replay model = new Replay(table, tune.effects());
        for (int call = 0; call < calls; call++) {
            if (model.row() == table.rows()) {
                out.print("{\"result\":-1}\n");
                return;
            }
            model.step();
            StringBuilder line = new StringBuilder("{\"result\":0,\"w\":{");
            boolean first = true;
            for (int c = 0; c < 14; c++) {
                if (model.written[c] >= 0) {
                    line.append(first ? "" : ",").append('"').append(c).append("\":")
                            .append(model.written[c]);
                    first = false;
                }
            }
            line.append('}');
            // SPEC.md 7.3: in versions 13 to 15 the voices the row syncs,
            // after w, where it syncs one; after them, in version 14 the
            // voices whose bit 4 the row sets, and in version 15 the sync
            // length of each voice whose length is above 1.
            voices(line, "s", model.syncs);
            voices(line, "even", model.evens);
            if (Columns.lengths(tune.effects())) {
                lengths(line, model.lengths);
            }
            line.append(",\"e\":{");
            first = true;
            for (int i = 0; i < 4; i++) {
                Replay.Effect e = model.effect[i];
                if (!e.set()) {
                    continue;
                }
                line.append(first ? "" : ",").append('"').append(i).append("\":{\"target\":")
                        .append(e.target()).append(",\"source\":").append(e.source())
                        .append(",\"select\":").append(e.select()).append(",\"count\":")
                        .append(e.count()).append(",\"timer\":").append(e.timer())
                        .append(",\"place\":").append(e.place())
                        .append(",\"deferred\":").append(e.deferred());
                if (mapped) {
                    line.append(",\"mapping\":").append(e.mapping());
                }
                if (cuts) {
                    line.append(",\"cut\":").append(e.cut());
                }
                line.append('}');
                first = false;
            }
            line.append("}}\n");
            out.print(line);
        }
    }

    /** The record of a tune file as bytes, and no byte for a file whose
     *  version is not the one this reads (R6.1). */
    static byte[] record(byte[] file, int calls) {
        TuneFile tune;
        try {
            tune = TuneFile.read(file);
        } catch (IllegalArgumentException another) {
            return new byte[0];
        }
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, false, java.nio.charset.StandardCharsets.US_ASCII);
        trace(tune, calls < 0 ? calls(tune.table()) : calls, out);
        out.flush();
        return bytes.toByteArray();
    }

    /** {@code ymxr-trace}: a tune file on standard input, its record on
     *  standard output, the first line and one line a row, {@code -rROWS}
     *  rows or one pass and the loop once without. */
    public static void main(String[] args) {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxr-trace", flags, Ymxs.ROWS);
        Ymxs.only(tool, flags, Ymxs.ROWS);
        int calls = (int) Ymxs.rows(tool, flags, -1);
        Report report = new Report(tool.reports());
        byte[] tune = tool.bytes();
        if (Multi.is(tune)) {
            throw tool.wrong(Tool.WRONG, "this is a multi file of several tunes, and a record"
                    + " is of one tune");
        }
        // A file this reader does not read produces no record, and reports why
        // (SPEC.md 6, R6.1), so the report reads the header under the
        // same guard rather than throwing where the record would not.
        try {
            TuneFile file = TuneFile.read(tune);
            report.say("the tune file: " + Report.count(tune.length, "byte", "bytes"));
            report.row("the table", Report.count(file.table().rows(), "row", "rows") + " of "
                    + Report.count(file.table().columns(), "column", "columns")
                    + ", repeating at row " + file.table().repeat());
            report.row("the frame rate", file.frameRate() + " Hz");
            report.row("the sources", String.valueOf(file.sources().size()));
            report.row("the rows to record", calls < 0 ? "one pass and the loop once"
                    : String.valueOf(calls));
        } catch (IllegalArgumentException wrong) {
            report.say("the tune file: " + Report.count(tune.length, "byte", "bytes")
                    + ", which this reader does not read: " + wrong.getMessage());
        }
        byte[] rows = record(tune, calls);
        if (rows.length == 0) {
            // A file this reader does not read produces no record (R6.1),
            // and a caller reading the exit is told so.
            throw tool.wrong(Tool.WRONG, "no record: this reader does not read the file");
        }
        tool.report(Report.count(rows.length, "byte", "bytes") + " of rows");
        Out.write(tool, rows);
    }

    /** The sync lengths above 1 of the three voices, as {@code lengths}
     *  and an object of each voice's letter and its length, where a voice
     *  has one (SPEC.md 7.3). */
    private static void lengths(StringBuilder line, int[] lengths) {
        boolean first = true;
        for (int v = 0; v < 3; v++) {
            if (lengths[v] > 1) {
                line.append(first ? ",\"lengths\":{" : ",").append('"')
                        .append((char) ('A' + v)).append("\":").append(lengths[v]);
                first = false;
            }
        }
        if (!first) {
            line.append('}');
        }
    }

    /** The voices of {@code mask}, bit 0 voice A to bit 2 voice C, as the
     *  key {@code key} and a list of letters, where the mask has one
     *  (SPEC.md 7.3). */
    private static void voices(StringBuilder line, String key, int mask) {
        if (mask == 0) {
            return;
        }
        line.append(",\"").append(key).append("\":[");
        for (int v = 0; v < 3; v++) {
            if ((mask & 1 << v) != 0) {
                line.append(Integer.bitCount(mask & (1 << v) - 1) == 0 ? "" : ",")
                        .append('"').append((char) ('A' + v)).append('"');
            }
        }
        line.append(']');
    }
}
