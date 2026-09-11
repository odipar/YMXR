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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * The calls {@code 68k/test/emu/test_ymxr.py} makes, and a short run of it.
 *
 * <p>The rig plays every tune under {@code ym/test} row by row on an
 * emulated 68000 against a model of SPEC.md 4 and 5. A tune of ten thousand
 * rows is minutes by itself, so no build runs the rig whole, and a rig no
 * build runs is one whose calls go unread: DTX's named files for weeks
 * after its tools became filters, and the check inside it reached no
 * table.
 *
 * <p>So this runs the rig capped. {@code -framesN} plays each tune for N
 * frames, which reads those frames against the model as a whole run reads
 * them: the chip writes of every frame, the timers' programming, each
 * handler's place and every tick's write. Two built fixtures at 24 frames
 * are under a second, against minutes for the ten whole.
 *
 * <p>What a capped run leaves is the wrap and the end: a tune is cut before
 * the row it repeats to comes round. The whole run reaches those, and it is
 * the run to make before a release.
 *
 * <p>Skipped where the tools the rig runs are absent, which the build is
 * not made to install.
 */
final class RigCallsTest {

    private static final Path RIG = Path.of("68k", "test", "emu", "test_ymxr.py");

    /** The frames a tune is played for here. Enough to reach a start, a
     *  retune and a stop on the two built tunes, and short enough that the
     *  emulator keeps up with a build. */
    private static final String FRAMES = "-frames24";

    /** The two tunes built for the shapes an effect takes: a drum
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
        for (String named : List.of("ym-to-ymxr", "ymxr-bind", "ymxr-sndh", "ymxr-prg")) {
            assertTrue(said.contains(named), RIG + " does not run " + named);
        }
    }

    /** The flags in the rig that belong to another program: the
     *  assembler's, the emulator's, and DTX's dtx-write, which the rig runs
     *  to read a table back. */
    private static final List<String> ANOTHER_PROGRAMS =
            List.of("-m68000", "-fr", "-i", "-o", "-l", "-d", "-s", "-c", "-D",
                    "-text");

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

    @Test
    void theRigPlaysTheBuiltTunesAgainstTheModel() throws Exception {
        Assumptions.assumeTrue(runs("python3", "--version"), "no python3");
        Assumptions.assumeTrue(runs("python3", "-c", "import unicorn"),
                "no unicorn for python3");
        Assumptions.assumeTrue(runs(named("RMAC", "rmac"), "-v"), "no rmac");
        Assumptions.assumeTrue(runs(named("DTX_WRITE", "dtx-write"), "-help"),
                "no dtx-write: DTX_WRITE names it");
        Assumptions.assumeTrue(Files.exists(Path.of("target/classes/org/ymxr/Tune.class")),
                "the tools are not built");
        List<String> argv = new ArrayList<>(List.of("python3", RIG.toString(), FRAMES));
        argv.addAll(TUNES);
        Process ran = new ProcessBuilder(argv).redirectErrorStream(true).start();
        String said = new String(ran.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(ran.waitFor() == 0
                        && said.contains("2 tunes play as the specification reads"),
                () -> RIG + " does not play the built tunes:\n" + said);
    }

    /** A tool the environment names, or the plain name. */
    private static String named(String variable, String plain) {
        String said = System.getenv(variable);
        return said == null || said.isEmpty() ? plain : said;
    }
}
