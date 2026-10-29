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
 * The 68000 binaries the tools combine with bound tunes
 * (doc/BINARIES.md): the SNDH core, which {@link Sndh} puts under an SNDH
 * file's entries, and the program stub, which {@link Prg} puts in front
 * of an SNDH file. Four switches of the player's are in the core, the
 * raster monitor, the lean tick, the row read through an absolute address
 * and the deferred start, and each of their sixteen settings is a separate
 * core, so a file that requests two uses the core that is both. A tick
 * reads its row through the program counter where {@code YMXR_PCREL} is at
 * 1, its value unasked, and a file uses a core with the deferred start in
 * where a subtune defers one (doc/performance.md).
 * The build assembles each once from its source under {@code 68k} and
 * writes it into the classes, the one step rmac is needed for; a tool
 * reads them there and runs no assembler.
 */
final class Binaries {

    /** Where the classes carry them. */
    static final String CARRIED = "/org/ymxr/68k/";

    /** One binary: the file it is carried as, the source it is assembled
     *  from, and the switches rmac assembles it with. */
    record Binary(String name, String source, List<String> defines) {
    }

    static final Binary CORE = binary(false, false, true, false);

    static final Binary STUB = new Binary("YMXR_prg.bin", "YMXR_prg.S", List.of());

    private Binaries() {
    }

    /** All seventeen, in the order the build writes them: the sixteen
     *  cores, then the stub. */
    static List<Binary> all() {
        List<Binary> all = new ArrayList<>();
        for (boolean defer : new boolean[] {false, true}) {
            for (boolean pcrel : new boolean[] {true, false}) {
                for (boolean lean : new boolean[] {false, true}) {
                    for (boolean monitor : new boolean[] {false, true}) {
                        all.add(binary(monitor, lean, pcrel, defer));
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

    /** The core of the four switches, as carried. */
    static byte[] core(boolean monitor, boolean lean, boolean pcrel, boolean defer) {
        return carried(binary(monitor, lean, pcrel, defer).name());
    }

    /** The binary of the three switches without the deferred start. */
    static Binary binary(boolean monitor, boolean lean, boolean pcrel) {
        return binary(monitor, lean, pcrel, false);
    }

    /**
     * The binary of the four switches (doc/performance.md), each a suffix of
     * its name in this order:
     *
     * <ul>
     * <li>{@code -perf} where {@code monitor}: the player's raster monitor
     *     ({@code YMXR_PERF}). The play call paints the background red
     *     while its work runs and burns a yellow bar for the timers'
     *     counted cost, and each tick handler paints a separate colour. A
     *     trace of the palette writes reports what the run cost, and a file
     *     made for reading a run uses this core in place of the plain one.
     * <li>{@code -lean} where {@code lean}: the player's two tick switches
     *     the other way ({@code YMXR_NEST} and {@code YMXR_AEOI}). A tick
     *     neither drops the interrupt level nor writes its end of
     *     interrupt, so one that writes a row costs 32 cycles less and one
     *     that ends a source 16. A host uses this core where no MFP
     *     interrupt of the host nests inside another and the MFP's vector
     *     register is the player's to set.
     * <li>{@code -abs} where {@code pcrel} is false: a tick reads its row
     *     through an absolute address ({@code YMXR_PCREL=0}), which a host
     *     that places a tune further off than a displacement reaches uses.
     *     Every other core reads it through the program counter.
     * <li>{@code -defer} where {@code defer}: the deferred start
     *     ({@code YMXR_DEFER=1}), which a file uses where a subtune's
     *     effects byte sets bit 4 (SPEC.md 3.3).
     * </ul>
     */
    static Binary binary(boolean monitor, boolean lean, boolean pcrel, boolean defer) {
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
     * {@code Binaries DIR... [-aRMAC] [-sSOURCES]}: the sixteen cores and
     * the stub assembled and written into each directory named, one line each
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
            System.out.printf("%-34s %5d bytes%n", binary.name(), code.length);
        }
    }

    private static void usage() {
        System.err.println("Binaries DIR... [-aRMAC] [-sSOURCES]");
        System.exit(2);
    }
}
