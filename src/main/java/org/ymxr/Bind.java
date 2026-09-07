package org.ymxr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code ymxr-bind in.ymxr out.bin}: the bound tune of a tune file
 * ({@link Bound}), what the player takes, as a file.
 */
final class Bind {

    private Bind() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("ymxr-bind in.ymxr out.bin");
            System.exit(2);
            return;
        }
        byte[] bound;
        try {
            bound = Bound.of(Files.readAllBytes(Path.of(args[0])));
        } catch (IllegalArgumentException wrong) {
            System.err.println("ymxr-bind: " + wrong.getMessage());
            System.exit(1);
            return;
        }
        Files.write(Path.of(args[1]), bound);
        System.out.println(args[1] + ": " + bound.length + " bytes");
    }
}
