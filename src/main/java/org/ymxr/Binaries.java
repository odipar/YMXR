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
 * The assembled 68000 SNDH cores and program stub (doc/BINARIES.md).
 * Five switches select thirty-two cores: raster monitor, lean ticks,
 * absolute source reads, deferred starts and transformation lookups.
 * The tools select lookup support only when a subtune requires it, and
 * deferred support only when a subtune defers a start. Source reads use
 * the program counter by default.
 *
 * <p>The build assembles the binaries under {@code 68k} and copies them
 * into the classes. Tools read those resources without running an
 * assembler.
 */
final class Binaries {

    /** Where the classes carry them. */
    static final String CARRIED = "/org/ymxr/68k/";

    /** One binary: the file it is carried as, the source it is assembled
     *  from, and the switches rmac assembles it with. */
    record Binary(String name, String source, List<String> defines) {
    }

    static final Binary CORE = binary(false, false, true, false, false);

    static final Binary STUB = new Binary("YMXR_prg.bin", "YMXR_prg.S", List.of());

    private Binaries() {
    }

    /**
     * All thirty-three binaries in build order: sixteen cores without
     * lookup support, sixteen with it, then the program stub.
     */
    static List<Binary> all() {
        List<Binary> all = new ArrayList<>();
        for (boolean map : new boolean[] {false, true}) {
            for (boolean defer : new boolean[] {false, true}) {
                for (boolean pcrel : new boolean[] {true, false}) {
                    for (boolean lean : new boolean[] {false, true}) {
                        for (boolean monitor : new boolean[] {false, true}) {
                            all.add(binary(monitor, lean, pcrel, defer, map));
                        }
                    }
                }
            }
        }
        all.add(STUB);
        return all;
    }

    /** The core with no switch set, as carried. */
    static byte[] core() {
        return carried(CORE.name());
    }

    /** The core of the three switches without the deferred start, as
     *  carried. */
    static byte[] core(boolean monitor, boolean lean, boolean pcrel) {
        return core(monitor, lean, pcrel, false);
    }

    /**
     * The carried core selected by four switches, with transformation
     * support disabled.
     */
    static byte[] core(boolean monitor, boolean lean, boolean pcrel, boolean defer) {
        return core(monitor, lean, pcrel, defer, false);
    }

    /** The core of the five switches, as carried. */
    static byte[] core(boolean monitor, boolean lean, boolean pcrel, boolean defer,
                       boolean map) {
        return carried(binary(monitor, lean, pcrel, defer, map).name());
    }

    /** The binary of the three switches without the deferred start. */
    static Binary binary(boolean monitor, boolean lean, boolean pcrel) {
        return binary(monitor, lean, pcrel, false);
    }

    /**
     * The binary selected by four switches, with transformation support
     * disabled.
     */
    static Binary binary(boolean monitor, boolean lean, boolean pcrel, boolean defer) {
        return binary(monitor, lean, pcrel, defer, false);
    }

    /**
     * Selects the core by five switches. Suffixes occur in this order:
     * {@code -perf} for the raster monitor; {@code -lean} for ticks
     * without interrupt nesting or an end-of-interrupt write; {@code
     * -abs} for absolute source reads; {@code -defer} for deferred
     * starts; {@code -map} for transformation lookups.
     *
     * <p>Without {@code -abs}, ticks use program-counter displacements.
     * A subtune with effects bit 4 requires {@code -defer}; bit 5
     * requires {@code -map}. Lookup cores read bound versions 7 to 10;
     * other cores read 7 to 9 (doc/BINARIES.md 2).
     */
    static Binary binary(boolean monitor, boolean lean, boolean pcrel, boolean defer,
                         boolean map) {
        StringBuilder name = new StringBuilder("YMXR_sndh");
        List<String> defines = new ArrayList<>();
        if (monitor) {
            name.append("-perf");
            defines.add("-dYMXR_PERF=1");
        }
        if (lean) {
            name.append("-lean");
            defines.add("-dYMXR_NEST=0");
            defines.add("-dYMXR_AEOI=1");
        }
        if (!pcrel) {
            name.append("-abs");
            defines.add("-dYMXR_PCREL=0");
        }
        if (defer) {
            name.append("-defer");
            defines.add("-dYMXR_DEFER=1");
        }
        if (map) {
            name.append("-map");
            defines.add("-dYMXR_MAP=1");
        }
        return new Binary(name + ".bin", "YMXR_sndh.S", List.copyOf(defines));
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
     * core includes and the binary's switches after it.
     *
     * @throws IllegalStateException where rmac does not run or fails
     */
    static byte[] assemble(Path rmac, Path sources, Binary binary) throws IOException {
        Path work = Files.createTempDirectory("ymxr68k");
        try {
            Path out = work.resolve(binary.name());
            List<String> command = new ArrayList<>(
                    List.of(rmac.toString(), "-m68000", "-fr", "-i" + sources));
            command.addAll(binary.defines());
            command.add("-o");
            command.add(out.toString());
            command.add(sources.resolve(binary.source()).toString());
            Process run = new ProcessBuilder(command).redirectErrorStream(true).start();
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
     * {@code Binaries DIR... [-aRMAC] [-sSOURCES]}: the thirty-two cores
     * and the stub assembled and written into each directory named, one line
     * each with its bytes, and beside each core without the deferred start
     * and the lookup, the table of its pieces the build cuts from it
     * (doc/LINKER.md). The assembler is {@code rmac} on the path unless
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
            System.out.printf("%-38s %5d bytes%n", binary.name(), code.length);
            if (Pieces.cuts(binary)) {
                Pieces table;
                try {
                    table = Pieces.cut(rmac, sources, binary);
                } catch (IllegalStateException failed) {
                    System.err.println("binaries: no table cut from " + binary.name() + ": "
                            + failed.getMessage());
                    System.exit(1);
                    return;
                }
                for (Path at : into) {
                    Files.writeString(at.resolve(Pieces.name(binary)), table.text(),
                            StandardCharsets.US_ASCII);
                }
                System.out.printf("%-38s %5d pieces, %d fields%n", Pieces.name(binary),
                        table.pieces().size(), table.fields().size());
            }
        }
    }

    private static void usage() {
        System.err.println("Binaries DIR... [-aRMAC] [-sSOURCES]");
        System.exit(2);
    }
}
