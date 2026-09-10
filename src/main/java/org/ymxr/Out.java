package org.ymxr;

import java.io.PrintStream;
import org.ymxs.tool.Tool;

/**
 * A file on standard output. {@link Tool} writes text; a tune file, an
 * SNDH file and a program are bytes, so they go out through here.
 */
final class Out {

    private Out() {
    }

    /** {@code file} on standard output, which is what the tool is for. A
     *  write that fails, a closed pipe among them, is an exit of 2: the
     *  stream records the fault in a flag rather than throwing it, so the
     *  flag is read, as {@code Tool.write} reads it after text. */
    static void write(Tool tool, byte[] file) {
        PrintStream out = System.out;
        out.write(file, 0, file.length);
        out.flush();
        if (out.checkError()) {
            throw tool.wrong(Tool.FAILED, "cannot write standard output");
        }
    }
}
