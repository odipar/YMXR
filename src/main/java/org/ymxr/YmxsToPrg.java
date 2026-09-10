package org.ymxr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.ymxs.YMXS.Multi;
import org.ymxs.tool.Tool;

/**
 * {@code ymxs-to-prg}: a YMXS structure as JSON on standard input, a TOS
 * program on standard output.
 *
 * <p>The SNDH file {@code ymxs-to-sndh} makes, with the program stub in
 * front of it (doc/BINARIES.md 4): a program that claims the machine under
 * Supexec, plays the tune from the VBL or Timer C, switches subtunes on
 * the keys 1 to 9, and releases the machine on SPACE or ESC.
 *
 * <p>{@code -rROWS} stops the run after that many rows, 0 for the tune's
 * row count; every other flag is {@code ymxs-to-sndh}'s.
 */
public final class YmxsToPrg {

    private YmxsToPrg() {
    }

    public static void main(String[] args) {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxs-to-prg", flags, Ymxs.PACKING);
        Ymxs.only(tool, flags, Ymxs.PACKING, Ymxs.TAGS, Ymxs.ROWS);
        Report report = new Report(tool.reports());
        long rows = Ymxs.rows(tool, flags, 0);
        Multi multi = Ymxs.read(tool);
        byte[] sndh = YmxsToSndh.of(tool, multi, flags, report);
        byte[] program;
        try {
            program = Prg.of(sndh, rows);
        } catch (IllegalArgumentException wrong) {
            throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
        }
        report.row("the program", program.length + " bytes, "
                + (rows == 0 ? "the tune's row count" : rows + " rows"));
        Out.write(tool, program);
        for (String note : report.unsaid()) {
            System.err.println("  " + note);
        }
    }
}
