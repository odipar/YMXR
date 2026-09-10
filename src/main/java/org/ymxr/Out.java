package org.ymxr;

import java.io.IOException;
import org.ymxs.tool.Tool;

/**
 * A file on standard output. {@link Tool} writes text; a tune file, an
 * SNDH file and a program are bytes, so they go out through here.
 */
final class Out {

    private Out() {
    }

    /** {@code file} on standard output, which is what the tool is for. */
    static void write(Tool tool, byte[] file) {
        try {
            System.out.write(file);
            System.out.flush();
        } catch (IOException failed) {
            throw tool.wrong(Tool.FAILED, "cannot write standard output: "
                    + failed.getMessage());
        }
    }
}
