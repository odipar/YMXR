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
 * of an SNDH file. Three switches of the player's stand in the core, the
 * raster monitor, the lean tick and the row read through an absolute
 * address, and each of their eight settings is a separate core, so a file
 * that requests two uses the core that is both. A tick reads its row
 * through the program counter where {@code YMXR_PCREL} stands at 1, its
 * value unasked (doc/performance.md).
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

    static final Binary CORE = new Binary("YMXR_sndh.bin", "YMXR_sndh.S", List.of());

    /** The core whose ticks read a row through an absolute address (the
     *  player's {@code YMXR_PCREL=0}, doc/performance.md), which a host
     *  that places a tune further off than a displacement reaches uses. */
    static final Binary ABSOLUTE = new Binary("YMXR_sndh-abs.bin", "YMXR_sndh.S",
            List.of("-dYMXR_PCREL=0"));

    /**
     * The core with the player's raster monitor assembled in (the
     * player's {@code YMXR_PERF}, doc/performance.md): the play call
     * paints the background red while its work runs and burns a yellow
     * bar for the timers' counted cost, and each tick handler paints a
     * separate colour. A trace of the palette writes reports what the run
     * cost, and a file made for reading a run uses this core in place of the
     * plain one.
     */
    static final Binary MONITOR = new Binary("YMXR_sndh-perf.bin", "YMXR_sndh.S",
            List.of("-dYMXR_PERF=1"));

    static final Binary MONITOR_ABSOLUTE = new Binary("YMXR_sndh-perf-abs.bin",
            "YMXR_sndh.S", List.of("-dYMXR_PERF=1", "-dYMXR_PCREL=0"));

    /**
     * The core with the player's two tick switches the other way (the
     * player's {@code YMXR_NEST} and {@code YMXR_AEOI},
     * doc/performance.md): a tick neither drops the interrupt level nor
     * writes its end of interrupt, so one that writes a row costs 32
     * cycles less and one that ends a source 16. A host uses this core
     * where no MFP interrupt of the host nests inside another and the
     * MFP's vector register is the player's to set.
     */
    static final Binary LEAN = new Binary("YMXR_sndh-lean.bin", "YMXR_sndh.S",
            List.of("-dYMXR_NEST=0", "-dYMXR_AEOI=1"));

    static final Binary LEAN_ABSOLUTE = new Binary("YMXR_sndh-lean-abs.bin", "YMXR_sndh.S",
            List.of("-dYMXR_NEST=0", "-dYMXR_AEOI=1", "-dYMXR_PCREL=0"));

    /**
     * The core with both switches set: the raster monitor reads what a
     * run costs, and the ticks it reads are the lean ones. A file made
     * for reading a lean run uses this core.
     */
    static final Binary MONITOR_LEAN = new Binary("YMXR_sndh-perf-lean.bin", "YMXR_sndh.S",
            List.of("-dYMXR_PERF=1", "-dYMXR_NEST=0", "-dYMXR_AEOI=1"));

    static final Binary MONITOR_LEAN_ABSOLUTE = new Binary("YMXR_sndh-perf-lean-abs.bin",
            "YMXR_sndh.S",
            List.of("-dYMXR_PERF=1", "-dYMXR_NEST=0", "-dYMXR_AEOI=1", "-dYMXR_PCREL=0"));

    static final Binary STUB = new Binary("YMXR_prg.bin", "YMXR_prg.S", List.of());

    private Binaries() {
    }

    /** All nine, in the order the build writes them. */
    static List<Binary> all() {
        return List.of(CORE, MONITOR, LEAN, MONITOR_LEAN,
                ABSOLUTE, MONITOR_ABSOLUTE, LEAN_ABSOLUTE, MONITOR_LEAN_ABSOLUTE, STUB);
    }

    /** The core with neither switch set, as carried. */
    static byte[] core() {
        return core(false, false, true);
    }

    /** The core of the three switches, as carried: the raster monitor in
     *  where {@code monitor}, ticks that neither drop the interrupt level
     *  nor write their end of interrupt where {@code lean}, and a row read
     *  through the program counter where {@code pcrel}, which every core
     *  but the four named for an absolute address does. */
    static byte[] core(boolean monitor, boolean lean, boolean pcrel) {
        return carried(binary(monitor, lean, pcrel).name());
    }

    /** The binary of the three switches. */
    static Binary binary(boolean monitor, boolean lean, boolean pcrel) {
        if (pcrel) {
            if (monitor) {
                return lean ? MONITOR_LEAN : MONITOR;
            }
            return lean ? LEAN : CORE;
        }
        if (monitor) {
            return lean ? MONITOR_LEAN_ABSOLUTE : MONITOR_ABSOLUTE;
        }
        return lean ? LEAN_ABSOLUTE : ABSOLUTE;
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
     * {@code Binaries DIR... [-aRMAC] [-sSOURCES]}: the eight cores and the
     * stub assembled and written into each directory named, one line each
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
            System.out.printf("%-24s %5d bytes%n", binary.name(), code.length);
        }
    }

    private static void usage() {
        System.err.println("Binaries DIR... [-aRMAC] [-sSOURCES]");
        System.exit(2);
    }
}
