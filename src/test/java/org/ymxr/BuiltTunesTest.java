package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** The two built dumps under {@code ym/test}, which {@link BuiltTunes}
 *  builds, so a file and the account of how it is made stay one thing. */
final class BuiltTunesTest {

    @Test
    void thePreemptTuneIsTheOneItsSourceBuilds() throws IOException {
        assertBuilt("Digidrum preempt, built.ym", BuiltTunes.preempt());
    }

    @Test
    void theRetriggerRetuneIsTheOneItsSourceBuilds() throws IOException {
        assertBuilt("Retrigger retune, built.ym", BuiltTunes.retriggerRetune());
    }

    private static void assertBuilt(String name, byte[] built) throws IOException {
        assertArrayEquals(Files.readAllBytes(Path.of("ym", "test", name)), built,
                "ym/test/" + name + " is not what BuiltTunes builds");
    }
}
