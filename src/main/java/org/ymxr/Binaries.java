package org.ymxr;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * The two 68000 binaries the tools combine with bound tunes
 * (doc/BINARIES.md): the SNDH core, which {@link Sndh} puts under an SNDH
 * file's entries, and the program stub, which {@link Prg} puts in front of
 * an SNDH file. The build assembles each once from its source under
 * {@code 68k} and writes it into the classes, the one step rmac is needed
 * for; a tool reads them there and runs no assembler.
 */
final class Binaries {

    /** Where the classes carry them. */
    static final String CARRIED = "/org/ymxr/68k/";

    /** One binary: the file it is carried as, and the source it is
     *  assembled from. */
    record Binary(String name, String source) {
    }

    static final Binary CORE = new Binary("YMXR_sndh.bin", "YMXR_sndh.S");
    static final Binary STUB = new Binary("YMXR_prg.bin", "YMXR_prg.S");

    private Binaries() {
    }

    /** Both, in the order the build writes them. */
    static List<Binary> all() {
        return List.of(CORE, STUB);
    }

    /** The SNDH core as carried. */
    static byte[] core() {
        return carried(CORE.name());
    }

    /** The program stub as carried. */
    static byte[] stub() {
        return carried(STUB.name());
    }

    /**
     * A binary as the classes carry it.
     *
     * @throws IllegalStateException where none of that name is carried
     */
    static byte[] carried(String name) {
        try (InputStream in = Binaries.class.getResourceAsStream(CARRIED + name)) {
            if (in == null) {
                throw new IllegalStateException("no binary carried at " + CARRIED + name
                        + ": the build's binaries step writes it");
            }
            return in.readAllBytes();
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /**
     * rmac's assembly of one source under {@code sources}, raw, for the
     * 68000, with {@code sources} on the include path for the player the
     * core includes.
     *
     * @throws IllegalStateException where rmac does not run or fails
     */
    static byte[] assemble(Path rmac, Path sources, Binary binary) throws IOException {
        Path work = Files.createTempDirectory("ymxr68k");
        try {
            Path out = work.resolve(binary.name());
            Process run = new ProcessBuilder(rmac.toString(), "-m68000", "-fr",
                    "-i" + sources, "-o", out.toString(),
                    sources.resolve(binary.source()).toString())
                    .redirectErrorStream(true).start();
            byte[] said = run.getInputStream().readAllBytes();
            if (run.waitFor() != 0 || !Files.exists(out)) {
                throw new IllegalStateException(rmac + " gave "
                        + new String(said, StandardCharsets.UTF_8).trim());
            }
            return Files.readAllBytes(out);
        } catch (IOException failed) {
            throw new IllegalStateException(rmac + " did not run: " + failed.getMessage(),
                    failed);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(stopped);
        } finally {
            try (Stream<Path> tree = Files.walk(work)) {
                for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    /**
     * {@code Binaries DIR... [-aRMAC] [-sSOURCES]}: the core and the stub
     * assembled and written into each directory named, one line each
     * with its bytes. The assembler is {@code rmac} on the path unless
     * {@code -a} names another, and the sources are under {@code 68k}
     * unless {@code -s} names another directory. A failure to assemble
     * stops the build with the message.
     */
    public static void main(String[] args) throws IOException {
        List<Path> into = new ArrayList<>();
        Path rmac = Path.of("rmac");
        Path sources = Path.of("68k");
        for (String arg : args) {
            if (arg.startsWith("-a")) {
                rmac = Path.of(arg.substring(2));
            } else if (arg.startsWith("-s")) {
                sources = Path.of(arg.substring(2));
            } else if (arg.startsWith("-")) {
                usage();
                return;
            } else {
                into.add(Path.of(arg));
            }
        }
        if (into.isEmpty()) {
            usage();
            return;
        }
        for (Path at : into) {
            Files.createDirectories(at);
        }
        for (Binary binary : all()) {
            byte[] code;
            try {
                code = assemble(rmac, sources, binary);
            } catch (IllegalStateException failed) {
                System.err.println("binaries: no " + binary.name() + " assembled from "
                        + sources.resolve(binary.source()) + " with an assembler at " + rmac
                        + ": " + failed.getMessage() + ". The build needs rmac, and"
                        + " -Drmac=PATH names another.");
                System.exit(1);
                return;
            }
            for (Path at : into) {
                Files.write(at.resolve(binary.name()), code);
            }
            System.out.printf("%-14s %5d bytes%n", binary.name(), code.length);
        }
    }

    private static void usage() {
        System.err.println("Binaries DIR... [-aRMAC] [-sSOURCES]");
        System.exit(2);
    }
}
