package org.ymxr;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.dtx.Table;

/**
 * What a reader reports of a tune (SPEC.md 7): one line a play call, the
 * call's result, the registers the frame writes and the effects the row
 * touched. The kit's references are what this gives, and the rig holds
 * the 68000 player to it.
 */
final class Trace {

    private Trace() {
    }

    /** The calls the kit takes of a tune: one pass and the loop once, or
     *  the pass and the call that reports its end. */
    static int calls(Table table) {
        int rows = table.rows();
        int repeat = table.repeat();
        return repeat < rows ? rows + rows - repeat : rows + 1;
    }

    /** The record's first line: what the tune states once. */
    static String header(TuneFile tune) {
        StringBuilder line = new StringBuilder("{\"rate\":").append(tune.frameRate())
                .append(",\"effects\":").append(tune.effects()).append(",\"sources\":[");
        for (int i = 0; i < tune.sources().size(); i++) {
            Table s = tune.sources().get(i);
            byte[] rows = s.column(0);
            line.append(i == 0 ? "" : ",").append("{\"rows\":[");
            for (int r = 0; r < rows.length; r++) {
                line.append(r == 0 ? "" : ",").append(rows[r] & 0xFF);
            }
            line.append("],\"repeat\":").append(s.repeat()).append('}');
        }
        return line.append("]}\n").toString();
    }

    /** The record: the first line, then {@code calls} entries of the tune,
     *  one a line, to {@code out}; a frame reporting -1 ends it. */
    static void trace(TuneFile tune, int calls, PrintStream out) {
        out.print(header(tune));
        Table table = tune.table();
        Replay model = new Replay(table);
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
            line.append("},\"e\":{");
            first = true;
            for (int i = 0; i < 4; i++) {
                Replay.Effect e = model.effect[i];
                if (!e.touched()) {
                    continue;
                }
                line.append(first ? "" : ",").append('"').append(i).append("\":{\"target\":")
                        .append(e.target()).append(",\"source\":").append(e.source())
                        .append(",\"select\":").append(e.select()).append(",\"count\":")
                        .append(e.count()).append(",\"timer\":").append(e.timer())
                        .append(",\"place\":").append(e.place()).append('}');
                first = false;
            }
            line.append("}}\n");
            out.print(line);
        }
    }

    /** The record of a tune file as bytes, and nothing of a file whose
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

    /** {@code ymxr-trace TUNE [FRAMES]}: the tune's record on standard
     *  output, its first line and one line a frame, the kit's count of
     *  frames unless one is given. */
    public static void main(String[] args) throws IOException {
        List<String> named = new ArrayList<>();
        boolean silent = false;
        for (String arg : args) {
            if (arg.equals(YmToYmxr.SILENT)) {
                silent = true;
            } else {
                named.add(arg);
            }
        }
        if (named.isEmpty() || named.size() > 2) {
            System.err.println("ymxr-trace TUNE [FRAMES] [-silent]");
            System.exit(2);
        }
        Report report = new Report(!silent);
        int calls = named.size() == 2 ? Integer.parseInt(named.get(1)) : -1;
        byte[] tune = Files.readAllBytes(Path.of(named.get(0)));
        // A file this reader does not read records nothing and says so
        // (SPEC.md 6, R6.1), so the report reads the header under the
        // same guard rather than throwing where the record would not.
        try {
            TuneFile file = TuneFile.read(tune);
            report.say("the tune file: " + named.get(0) + ", " + tune.length + " bytes");
            report.row("the table", file.table().rows() + " rows of " + file.table().columns()
                    + " columns, repeating at row " + file.table().repeat());
            report.row("the frame rate", file.frameRate() + " Hz");
            report.row("the sources", String.valueOf(file.sources().size()));
            report.row("the rows to record", calls < 0 ? "one pass and the loop once"
                    : String.valueOf(calls));
        } catch (IllegalArgumentException wrong) {
            report.say("the tune file: " + named.get(0) + ", " + tune.length
                    + " bytes, which this reader does not read: " + wrong.getMessage());
        }
        byte[] rows = record(tune, calls);
        report.say("recorded: " + rows.length + " bytes of rows on standard output");
        System.out.write(rows);
        System.out.flush();
    }
}
