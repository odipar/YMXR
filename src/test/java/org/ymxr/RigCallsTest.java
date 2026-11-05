package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * Checks rig tool calls and capped emulation runs against SPEC.md 4 and 5.
 * The model compares frame writes, MFP programming, handler places and
 * tick writes. Short runs cover starts, retunes and stops; complete runs
 * also reach tune wraps and endings.
 *
 * <p>Run each combination of source addressing, lean ticks, monitoring,
 * deferred starts and optional lookups. Conformance tunes add multi-column
 * targets, counted sources and mapped deferred starts absent from dumps.
 * A complete refill run on one source tune checks reader-image bounds
 * against performance.md.
 *
 * <p>Hatari modes require a TOS image and run separately. Missing local
 * tools skip these tests; bin/suite requires them.
 */
final class RigCallsTest {

    private static final Path RIG = Path.of("68k", "test", "emu", "test_ymxr.py");

    /** The frames a tune is played for here. Enough to reach a start, a
     *  retune and a stop on the two built tunes, and short enough that the
     *  emulator keeps up with a build. */
    private static final String FRAMES = "-frames24";

    /** The two tunes built for the shapes an effect runs: a drum
     *  preempting a square on its voice, and a note struck again at the
     *  rate already running. */
    private static final List<String> TUNES =
            List.of("ym/test/Digidrum preempt, built.ym",
                    "ym/test/Retrigger retune, built.ym");

    private static boolean runs(String... argv) {
        try {
            return new ProcessBuilder(argv).redirectErrorStream(true)
                    .start().waitFor() == 0;
        } catch (IOException | InterruptedException no) {
            return false;
        }
    }

    private static String rig() throws IOException {
        return Files.readString(RIG);
    }

    @Test
    void theRigNamesTheToolsThisRepositoryBuilds() throws IOException {
        String said = rig();
        for (String named : List.of("ym-to-ymxr", "ymxr-bind", "ymxr-multi", "ymxr-sndh",
                "ymxr-prg")) {
            assertTrue(said.contains(named), RIG + " does not run " + named);
        }
    }

    /** The flags in the rig that belong to another program: the
     *  assembler's, the emulator's, and DTX's dtx-write, which the rig runs
     *  to read a table back and to pack one again with a row set by hand,
     *  at a unit, a ring and its copies. */
    private static final List<String> ANOTHER_PROGRAMS =
            List.of("-m68000", "-fr", "-i", "-o", "-l", "-d", "-s", "-c", "-D",
                    "-text", "-k", "-m", "-copies");

    /** The flags the rig reads itself, out of the usage it prints: a line
     *  of that block opens with the rig's name and the flag. A flag the rig
     *  documents for itself is no tool's. */
    private static List<String> itsOwn(String said) {
        List<String> out = new ArrayList<>();
        Matcher flag = Pattern.compile("test_ymxr\\.py (-[a-z]+)").matcher(said);
        while (flag.find()) {
            out.add(flag.group(1));
        }
        return out;
    }

    /**
     * Every flag the rig passes to a tool is one that tool reads.
     *
     * <p>A flag the rig builds with {@code "-r%d"} reaches the tool as
     * {@code -r}, so the stems are compared. A tool that drops a flag the
     * rig passes fails here, which is how a rig rots between runs.
     */
    @Test
    void everyFlagTheRigPassesIsOneTheToolReads() throws IOException {
        StringBuilder tools = new StringBuilder();
        try (var at = Files.walk(Path.of("src/main/java/org/ymxr"))) {
            for (Path one : at.filter(Files::isRegularFile).toList()) {
                tools.append(Files.readString(one));
            }
        }
        String said = rig();
        List<String> mine = itsOwn(said);
        assertTrue(mine.contains("-frames") && mine.contains("-cycles"),
                () -> RIG + " prints no usage its flags are read from: " + mine);
        Matcher flag = Pattern.compile("\"(-[a-z]+)(?:%[ds])?\"").matcher(said);
        List<String> unread = new ArrayList<>();
        while (flag.find()) {
            String stem = flag.group(1);
            if (ANOTHER_PROGRAMS.contains(stem) || mine.contains(stem)
                    || unread.contains(stem)) {
                continue;
            }
            if (!tools.toString().contains('"' + stem)) {
                unread.add(stem);
            }
        }
        assertTrue(unread.isEmpty(), () -> RIG + " passes " + unread
                + ", which no tool reads");
    }

    /** What the rig runs: python3 with unicorn, rmac, DTX's dtx-write and
     *  the tools this repository builds. */
    private static void theRigRuns() {
        Assumptions.assumeTrue(runs("python3", "--version"), "no python3");
        Assumptions.assumeTrue(runs("python3", "-c", "import unicorn"),
                "no unicorn for python3");
        Assumptions.assumeTrue(runs(named("RMAC", "rmac"), "-v"), "no rmac");
        Assumptions.assumeTrue(runs(named("DTX_WRITE", "dtx-write"), "-help"),
                "no dtx-write: DTX_WRITE names it");
        Assumptions.assumeTrue(Files.exists(Path.of("target/classes/org/ymxr/Tune.class")),
                "the tools are not built");
    }

    /** The rig run with {@code argv} after its name, and what it printed. */
    private static String rigWith(List<String> argv) throws Exception {
        List<String> all = new ArrayList<>(List.of("python3", RIG.toString()));
        all.addAll(argv);
        Process ran = new ProcessBuilder(all).redirectErrorStream(true).start();
        String said = new String(ran.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        return ran.waitFor() + "\n" + said;
    }

    /** The thirty-two cores a release ships, as the rig's switches select
     *  them: the plain core, and {@code -abs}, {@code -lean}, {@code -perf},
     *  {@code -defer} and {@code -map} alone and together. */
    private static final List<List<String>> CORES = cores();

    private static List<List<String>> cores() {
        List<String> switches = List.of("-perf", "-lean", "-abs", "-defer", "-map");
        List<List<String>> out = new ArrayList<>();
        for (int setting = 0; setting < 1 << switches.size(); setting++) {
            List<String> core = new ArrayList<>();
            for (int i = 0; i < switches.size(); i++) {
                if ((setting & 1 << i) != 0) {
                    core.add(switches.get(i));
                }
            }
            out.add(List.copyOf(core));
        }
        return List.copyOf(out);
    }

    /** The property that selects a shard of the thirty-two cores. */
    static final String SHARD = "rig.shard";

    /**
     * The cores of a shard. Absent or empty, the shard is every core; {@code
     * none} is no core; {@code k/n} is each core whose index in {@code all}
     * leaves k after a division by n, so the shards 0 to n - 1 of n are every
     * core once. The workflow runs the cores in four jobs of eight, and its
     * suite job none (.github/workflows/test.yml).
     */
    static List<List<String>> shard(List<List<String>> all, @Nullable String named) {
        if (named == null || named.isEmpty()) {
            return all;
        }
        if (named.equals("none")) {
            return List.of();
        }
        Matcher parts = Pattern.compile("(\\d+)/(\\d+)").matcher(named);
        if (!parts.matches()) {
            throw new IllegalArgumentException(SHARD + " reads none or k/n, and not " + named);
        }
        int k = Integer.parseInt(parts.group(1));
        int n = Integer.parseInt(parts.group(2));
        if (n < 1 || k >= n) {
            throw new IllegalArgumentException(SHARD + " reads k/n with k from 0 to n - 1,"
                    + " and not " + named);
        }
        List<List<String>> out = new ArrayList<>();
        for (int i = k; i < all.size(); i += n) {
            out.add(all.get(i));
        }
        return List.copyOf(out);
    }

    @Test
    void theFourShardsAreEveryCoreOnce() {
        List<List<String>> joined = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            List<List<String>> one = shard(CORES, k + "/4");
            assertTrue(one.size() == 8, () -> "shard has " + one.size() + " cores, not 8");
            joined.addAll(one);
        }
        assertTrue(joined.size() == CORES.size() && joined.containsAll(CORES),
                () -> "the four shards are " + joined + ", not " + CORES);
        assertTrue(shard(CORES, "none").isEmpty() && shard(CORES, null).equals(CORES),
                "none is no core, and an absent shard every core");
    }

    /** The switches a linked player is linked under, the eight sets of
     *  {@code -perf}, {@code -lean} and {@code -abs}: ymxr-sndh -link picks
     *  the deferred start and the lookup by the tunes' effects. */
    private static final List<List<String>> LINKED = linked();

    private static List<List<String>> linked() {
        List<List<String>> out = new ArrayList<>();
        for (List<String> core : CORES) {
            if (!core.contains("-defer") && !core.contains("-map")) {
                out.add(core);
            }
        }
        return List.copyOf(out);
    }

    @Test
    void theFourShardsAreEverySetOfLinkedSwitchesOnce() {
        List<List<String>> joined = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            List<List<String>> one = shard(LINKED, k + "/4");
            assertTrue(one.size() == 2, () -> "shard has " + one.size() + " sets, not 2");
            joined.addAll(one);
        }
        assertTrue(LINKED.size() == 8 && joined.size() == 8 && joined.containsAll(LINKED),
                () -> "the four shards are " + joined + ", not " + LINKED);
    }

    /** The conformance kit's tunes, the 20 of the check of deferred starts
     *  and the 33 of the check of tunes that map a value, each on the player
     *  ymxr-sndh -link links for it, under each set of switches: the tool's
     *  core against the rig's link of the assembled core, and every tick
     *  against the model (doc/LINKER.md, The check). The workflow runs the
     *  sets in the four jobs of cores. */
    @TestFactory
    Stream<DynamicTest> theRigPlaysOnLinkedPlayersOfEverySetOfSwitches() {
        return shard(LINKED, System.getProperty(SHARD)).stream().map(core -> DynamicTest.dynamicTest(
                core.isEmpty() ? "the plain switches" : String.join(" ", core), () -> {
                    theRigRuns();
                    List<String> argv = new ArrayList<>(List.of("-linked", "-kit", "-defer",
                            "-map", FRAMES));
                    argv.addAll(core);
                    String said = rigWith(argv);
                    assertTrue(said.startsWith("0\n")
                                    && said.contains("16 tunes play as the specification reads")
                                    && said.contains("deferred starts: 20 tunes")
                                    && said.contains("tunes that map a value: 33 tunes")
                                    && said.contains("linked, "),
                            () -> RIG + " " + String.join(" ", argv) + " does not play on"
                                    + " linked players:\n" + said);
                }));
    }

    @TestFactory
    Stream<DynamicTest> theRigPlaysTheBuiltTunesOnEveryCore() {
        return shard(CORES, System.getProperty(SHARD)).stream().map(core -> DynamicTest.dynamicTest(
                core.isEmpty() ? "the plain core" : String.join(" ", core), () -> {
                    theRigRuns();
                    List<String> argv = new ArrayList<>(core);
                    argv.add(FRAMES);
                    argv.addAll(TUNES);
                    String said = rigWith(argv);
                    assertTrue(said.startsWith("0\n")
                                    && said.contains("2 tunes play as the specification reads"),
                            () -> RIG + " " + String.join(" ", core)
                                    + " does not play the built tunes:\n" + said);
                }));
    }

    /** The conformance kit's tunes, capped: the player's frames against
     *  the reader's entries, and the tune of another version rejected by
     *  the binder and the reader. */
    @Test
    void theRigPlaysTheKitAgainstTheReader() throws Exception {
        theRigRuns();
        String said = rigWith(List.of("-kit", FRAMES));
        assertTrue(said.startsWith("0\n")
                        && said.contains("tunes play as the specification reads")
                        && said.contains("the binder and the reader reject it"),
                () -> RIG + " -kit does not play the kit:\n" + said);
    }

    /** The tune a counted run reads: 182 frames, so the whole tune plays in
     *  seconds, and a source on Timer D, so the bound image has the table of
     *  a source beside the tune's. performance.md has its row. */
    private static final String COUNTED = "ym/test/Turrican 2 - world completed 1.ym";

    /** The DTX checkout whose rig the counted modes run on: its cycle
     *  counter, and the packager its classes build. */
    private static Path dtxRepo() {
        String named = System.getenv("DTX_REPO");
        return Path.of(named == null || named.isEmpty() ? "../DTX" : named);
    }

    /**
     * The rig's {@code -refill} over one tune, whole: the play call, DTX's
     * advance and the parts of a refill counted, and the tune's row of
     * performance.md read against the count. A run that reads the image of
     * the binder wrong fails here in seconds.
     */
    @Test
    void theRigCountsAWholeTuneAgainstTheDocument() throws Exception {
        theRigRuns();
        Path rig = dtxRepo().resolve("68k/test/emu/test_dtx.py");
        Assumptions.assumeTrue(Files.isRegularFile(rig),
                "no DTX rig at " + rig + ": DTX_REPO names the checkout");
        Path packager = dtxRepo().resolve("target/classes/org/dtx/Packager.class");
        Assumptions.assumeTrue(Files.isRegularFile(packager),
                "DTX's classes are not built at " + packager);
        String said = rigWith(List.of("-refill", COUNTED));
        assertTrue(said.startsWith("0\n")
                        && said.contains("1 tunes play as the specification reads")
                        && said.contains("cycles outside the decoder"),
                () -> RIG + " -refill does not count " + COUNTED + ":\n" + said);
    }

    /** A tool the environment names, or the plain name. */
    private static String named(String variable, String plain) {
        String said = System.getenv(variable);
        return said == null || said.isEmpty() ? plain : said;
    }
}
