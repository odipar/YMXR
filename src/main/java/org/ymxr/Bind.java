package org.ymxr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code ymxr-bind in.ymxr out.bin}: the bound tune of a tune file
 * ({@link Bound}), what the player takes, as a file. A tune file this
 * does not bind, one of another version for one, gets a line on stderr
 * beginning {@code ymxr-bind: } and an exit of 1; a file that does not
 * read or write gets the same line and an exit of 2, as does a wrong
 * call.
 */
final class Bind {

    private Bind() {
    }

    public static void main(String[] args) {
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
        } catch (IOException failed) {
            System.err.println("ymxr-bind: " + failed);
            System.exit(2);
            return;
        }
        try {
            Files.write(Path.of(args[1]), bound);
        } catch (IOException failed) {
            System.err.println("ymxr-bind: " + failed);
            System.exit(2);
            return;
        }
        System.out.println(args[1] + ": " + bound.length + " bytes");
    }
}
