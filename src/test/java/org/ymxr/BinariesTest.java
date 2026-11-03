package org.ymxr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The thirty-three binaries as the build assembled them, and what the
 * tools make of them: each of the thirty-two cores' descriptors and the
 * stub's as BINARIES.md defines them, an SNDH file from the kit's tunes
 * read back tag by tag and part by part, and a program around it.
 */
final class BinariesTest {

    private static final Path TUNES = ConformanceTest.TUNES;

    static byte[] tune(String name) throws IOException {
        return Files.readAllBytes(TUNES.resolve(name + ".ymxr"));
    }

    static String ascii(byte[] bytes, int at, int length) {
        return new String(bytes, at, length, StandardCharsets.ISO_8859_1);
    }

    /** What an exception says, as text. */
    static String said(Throwable wrong) {
        return String.valueOf(wrong.getMessage());
    }

    /** Where a bra.w at {@code at} lands. */
    static int reaches(byte[] bytes, int at) {
        assertEquals(Sndh.BRA_W, Tune.getWord(bytes, at), "a bra.w at " + at);
        return at + 2 + (short) Tune.getWord(bytes, at + 2);
    }

    /** One core's descriptor: YMXS at 12, the two versions, YMXR_FIXED,
     *  the flags word at 22, where the handlers begin at 26, the state
     *  byte in the core, the two offsets it is written with unpatched, and
     *  three entries that reach even addresses in the core. The switches
     *  are those of {@code flags}. */
    private static void assertCore(byte[] core, int flags) throws IOException {
        boolean defer = (flags & Sndh.CORE_DEFER) != 0;
        boolean map = (flags & Sndh.CORE_MAP) != 0;
        assertEquals(0, core.length & 1, "the core is even-sized");
        assertArrayEquals(Sndh.CORE_MAGIC, Arrays.copyOfRange(core, 12, 16));
        assertEquals(1, Tune.getWord(core, 16));
        // a core without the lookup reads to the version of a tune that
        // maps no value, and a core with it to the version of one that
        // does (SPEC.md 3.3.5)
        assertEquals(map ? Tune.VERSION : Tune.UNMAPPED, Tune.getWord(core, 18),
                "the bound tune version the core reads");
        assertEquals(PlayerTest.equates(defer, map).get("YMXR_VERSION"), Tune.getWord(core, 18),
                "the version the player reads");
        assertEquals(PlayerTest.equates(defer, map).get("YMXR_FIXED"),
                Tune.getWord(core, 20),
                "the workspace's fixed bytes are the player's");
        assertEquals(flags, Tune.getWord(core, 22), "the flags word");
        // where the handlers begin: the first handler of a core with the
        // lookup in, past the descriptor and the init it reaches, and 0 in
        // a core without it, whose reach counts from its first byte
        int handlers = Tune.getWord(core, Sndh.CORE_HANDLERS_AT);
        if (map) {
            assertTrue(handlers > reaches(core, 0) && handlers < core.length
                    && (handlers & 1) == 0, "the handlers begin at " + handlers);
        } else {
            assertEquals(0, handlers, "the field at 26 of a core without the lookup");
        }
        int state = Tune.getWord(core, 24);
        assertTrue(state >= Sndh.CORE_DESCRIPTOR && state < core.length,
                "the state byte is at " + state);
        assertEquals(0, Tune.getLong(core, 28));
        assertEquals(0, Tune.getLong(core, 32));
        for (int entry = 0; entry < 12; entry += 4) {
            int to = reaches(core, entry);
            assertTrue(to >= Sndh.CORE_DESCRIPTOR && to < core.length && (to & 1) == 0,
                    "the entry at " + entry + " reaches " + to);
        }
    }

    @Test
    void theCoreMatchesItsDescriptor() throws IOException {
        assertCore(Binaries.core(), Sndh.CORE_PCREL);
    }

    @Test
    void theMonitorCoreIsTheSameCoreWithTheMonitorIn() throws IOException {
        byte[] core = Binaries.core();
        byte[] monitor = Binaries.core(true, false, true);
        assertCore(monitor, Sndh.CORE_MONITOR | Sndh.CORE_PCREL);
        assertTrue(monitor.length > core.length, "the monitor core is " + monitor.length
                + " bytes and the plain core " + core.length);
        assertEquals(Tune.getWord(core, 16), Tune.getWord(monitor, 16),
                "the descriptor's version");
        assertEquals(Tune.getWord(core, 18), Tune.getWord(monitor, 18),
                "the bound tune's version");
        for (int entry = 0; entry < 12; entry += 4) {
            assertEquals(reaches(core, entry), reaches(monitor, entry),
                    "the entry at " + entry);
        }
    }

    @Test
    void eachOfTheFiveSwitchesThirtyTwoSettingsIsACoreWhoseFlagsReadItBack()
            throws IOException {
        // The tool selects a core by the switches passed and checks it against
        // the flags word (Sndh.checkCore), so each of the thirty-two settings
        // needs a separate core whose word reads the setting back.
        Set<String> named = new LinkedHashSet<>();
        for (int setting = 0; setting < 32; setting++) {
            boolean monitor = (setting & Sndh.CORE_MONITOR) != 0;
            boolean lean = (setting & Sndh.CORE_LEAN) != 0;
            boolean pcrel = (setting & Sndh.CORE_PCREL) != 0;
            boolean defer = (setting & Sndh.CORE_DEFER) != 0;
            boolean map = (setting & Sndh.CORE_MAP) != 0;
            byte[] core = Binaries.core(monitor, lean, pcrel, defer, map);
            assertCore(core, setting);
            Sndh.checkCore(core, monitor, lean, pcrel, defer, map,
                    map ? Tune.VERSION : Sndh.binds(List.of()));
            named.add(Binaries.binary(monitor, lean, pcrel, defer, map).name());
            if (!map) {
                assertArrayEquals(core, Binaries.core(monitor, lean, pcrel, defer),
                        "the four switches name the core without the lookup");
                // a core without the lookup reads bound tunes to version 9,
                // which the version line reports before its flags
                IllegalArgumentException old = assertThrows(IllegalArgumentException.class,
                        () -> Sndh.checkCore(core, monitor, lean, pcrel, defer, true,
                                Tune.VERSION));
                assertEquals("the core reads bound tunes to version 9, and this binds at 10",
                        old.getMessage());
            }
        }
        assertEquals(32, named.size(), "the thirty-two settings are thirty-two binaries: "
                + named);
        assertEquals(33, Binaries.all().size(), "the thirty-two cores and the stub");
        List<String> order = new ArrayList<>();
        for (Binaries.Binary binary : Binaries.all()) {
            order.add(binary.name());
        }
        assertEquals(named, new LinkedHashSet<>(order.subList(0, 32)), "the cores");
        for (int i = 0; i < 32; i++) {
            assertEquals(i >= 16, order.get(i).endsWith("-map.bin"),
                    "the sixteen without the lookup first, then the sixteen with it: " + order);
        }
        assertEquals(List.of("-dYMXR_PERF=1", "-dYMXR_NEST=0", "-dYMXR_AEOI=1",
                "-dYMXR_PCREL=0", "-dYMXR_DEFER=1", "-dYMXR_MAP=1"),
                Binaries.binary(true, true, false, true, true).defines());
        assertEquals("YMXR_sndh-perf-lean-abs-defer-map.bin",
                Binaries.binary(true, true, false, true, true).name());
    }

    /** A core with the lookup in whose flags word has bit 4 cleared, which
     *  the check reports after the version, which it reads (BINARIES.md
     *  2.10). */
    @Test
    void aCoreWhoseFlagsLackTheLookupIsRejectedForATuneThatMapsAValue() throws IOException {
        byte[] core = Binaries.core(false, false, true, false, true).clone();
        Tune.putWord(core, Sndh.CORE_FLAGS_AT, Sndh.CORE_PCREL);
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.checkCore(core, false, false, true, false, true, Tune.VERSION));
        assertEquals("the core's flags at 22 read 4, and a subtune that maps a value needs bit"
                + " 4 set", wrong.getMessage());
        IllegalArgumentException of = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(core, List.of(fade()), new Sndh.Options("Fade", null, null, false,
                        false)));
        assertEquals(wrong.getMessage(), of.getMessage());
        assertEquals(reported(Files.readString(Path.of("doc", "BINARIES.md")),
                "the core's flags at 22 read F, and a subtune that maps").replace("F", "4"),
                wrong.getMessage());
    }

    @Test
    void theLeanTickIsTheSameBytesOffEitherCore() throws IOException {
        // Both switches belong to the player, so the lean tick uses the
        // same code off the core with the monitor in as off the plain one.
        int off = Binaries.core().length - Binaries.core(false, true, true).length;
        assertEquals(off, Binaries.core(true, false, true).length
                - Binaries.core(true, true, true).length,
                "the lean tick is " + off + " bytes off the plain core");
        assertTrue(off > 0, "the lean core is smaller by " + off + " bytes");
    }

    @Test
    void theRowReadThroughTheProgramCounterIsTheSameBytesOnEitherCore() throws IOException {
        // The third switch belongs to the player as the other two do, so
        // it puts the same code into the core with the monitor in as into
        // the plain one: a place is a word where it was a long and a start
        // does the arithmetic that turns an address into a displacement.
        int on = Binaries.core().length - Binaries.core(false, false, false).length;
        assertEquals(on, Binaries.core(true, false, true).length
                - Binaries.core(true, false, false).length,
                "the row read through the program counter is " + on + " bytes on the core");
        assertTrue(on > 0, "that core is larger by " + on + " bytes");
    }

    @Test
    void theStubMatchesItsDescriptor() {
        byte[] stub = Binaries.stub();
        assertEquals(0, stub.length & 1, "the stub is even-sized");
        int to = reaches(stub, 0);
        assertTrue(to >= Prg.STUB_DESCRIPTOR && to < stub.length, "the stub's bra.w reaches " + to);
        assertArrayEquals(Prg.STUB_MAGIC, Arrays.copyOfRange(stub, 4, 8));
        assertEquals(Prg.STUB_VERSION, Tune.getWord(stub, 8));
    }

    /**
     * The binaries under {@code go/binaries/data} are the ones the classes
     * carry. A Go module fetched by its import path contains the files a
     * commit has in it, so a Go executable embeds those, and a
     * stale one would put another core under a tune.
     */
    @Test
    void theBinariesTheGoTreeCarriesAreTheOnesTheBuildAssembled() throws IOException {
        Path data = Path.of("go/binaries/data");
        for (Binaries.Binary binary : Binaries.all()) {
            Path at = data.resolve(binary.name());
            assertTrue(Files.exists(at), at + " is not there: run mvn process-classes");
            assertArrayEquals(Binaries.carried(binary.name()), Files.readAllBytes(at),
                    binary.name() + " under go/binaries/data is not the one the build"
                            + " assembled");
        }
    }

    /** The Go tree lists the thirty-three by name, which release/publish.sh
     *  reads, and its list is the build's, in the build's order. */
    @Test
    void theGoTreeListsTheBinariesTheBuildWrites() throws IOException {
        String go = Files.readString(Path.of("go/binaries/binaries.go"));
        Set<String> listed = new LinkedHashSet<>();
        Matcher name = Pattern.compile("YMXR_[A-Za-z0-9_-]*\\.bin").matcher(go);
        while (name.find()) {
            listed.add(name.group());
        }
        List<String> written = new ArrayList<>();
        for (Binaries.Binary binary : Binaries.all()) {
            written.add(binary.name());
        }
        assertEquals(new LinkedHashSet<>(written), listed,
                "go/binaries/binaries.go lists another set of binaries");
        // the list All returns, its two constants by the names they are
        int at = go.indexOf("var all = []string{");
        assertTrue(at >= 0, "go/binaries/binaries.go has no list of all");
        String all = go.substring(at, go.indexOf('}', at))
                .replace("Core,", "\"" + Binaries.CORE.name() + "\",")
                .replace("Stub,", "\"" + Binaries.STUB.name() + "\",");
        List<String> order = new ArrayList<>();
        Matcher one = Pattern.compile("\"(YMXR_[A-Za-z0-9_-]*\\.bin)\"").matcher(all);
        while (one.find()) {
            order.add(one.group(1));
        }
        assertEquals(written, order, "go/binaries/binaries.go lists them in another order");
    }

    @Test
    void aBinaryNotCarriedIsSaidSo() {
        IllegalStateException none = assertThrows(IllegalStateException.class,
                () -> Binaries.carried("none.bin"));
        assertTrue(said(none).contains("/org/ymxr/68k/none.bin"), said(none));
    }

    /**
     * The tag block walked from SNDH to HDNS as the tool writes it: the
     * tags in order, each text tag's text, the '##' count, the clock tag
     * and its rate, the FLAG letters after its '~', FRMS's longs, the
     * names, TIME's words, and where the block ends, past HDNS. A zero
     * byte where a tag would begin is a pad, and is at an odd
     * position.
     */
    record Tags(List<String> order, Map<String, String> text, int subtunes, String clock,
                int rate, String flag, int[] frames, List<String> names, int[] seconds,
                int end) {
    }

    static Tags tags(byte[] sndh) {
        assertEquals("SNDH", ascii(sndh, 12, 4));
        List<String> order = new ArrayList<>();
        Map<String, String> text = new HashMap<>();
        int subtunes = -1;
        String clock = "";
        int rate = -1;
        String flag = "";
        int[] frames = new int[0];
        List<String> names = new ArrayList<>();
        int[] seconds = new int[0];
        int at = 16;
        while (true) {
            if (sndh[at] == 0) {
                assertEquals(1, at & 1, "a pad at " + at);
                at++;
                continue;
            }
            String name = ascii(sndh, at, 4);
            if (name.equals("HDNS")) {
                order.add(name);
                at += 4;
                break;
            }
            if (name.startsWith("##")) {
                order.add("##");
                subtunes = Integer.parseInt(name.substring(2));
                at += 4;
                assertEquals(0, sndh[at], "'##' ends in a zero byte");
                at++;
            } else if (name.startsWith("TC") || name.startsWith("!V")) {
                clock = name.substring(0, 2);
                order.add(clock);
                int to = at + 2;
                while (sndh[to] != 0) {
                    to++;
                }
                rate = Integer.parseInt(ascii(sndh, at + 2, to - at - 2));
                at = to + 1;
            } else if (name.equals("FRMS")) {
                order.add(name);
                assertTrue(subtunes >= 0, "'##' comes before FRMS");
                at += 4;
                frames = new int[subtunes];
                for (int i = 0; i < subtunes; i++) {
                    frames[i] = Tune.getLong(sndh, at + 4 * i);
                }
                at += 4 * subtunes;
            } else if (name.equals("TIME")) {
                order.add(name);
                assertTrue(subtunes >= 0, "'##' comes before TIME");
                at += 4;
                seconds = new int[subtunes];
                for (int i = 0; i < subtunes; i++) {
                    seconds[i] = Tune.getWord(sndh, at + 2 * i);
                }
                at += 2 * subtunes;
            } else if (name.equals("!#SN")) {
                order.add(name);
                assertTrue(subtunes >= 0, "'##' comes before !#SN");
                int base = at;
                at += 4 + 2 * subtunes;
                for (int i = 0; i < subtunes; i++) {
                    assertEquals(at, base + Tune.getWord(sndh, base + 4 + 2 * i),
                            "name " + (i + 1) + "'s offset");
                    int to = at;
                    while (sndh[to] != 0) {
                        to++;
                    }
                    names.add(ascii(sndh, at, to - at));
                    at = to + 1;
                }
            } else {
                order.add(name);
                at += 4;
                int to = at;
                while (sndh[to] != 0) {
                    to++;
                }
                String value = ascii(sndh, at, to - at);
                if (name.equals("FLAG")) {
                    assertTrue(value.startsWith("~"), "FLAG reads " + value);
                    flag = value.substring(1);
                } else {
                    text.put(name, value);
                }
                at = to + 1;
            }
        }
        return new Tags(order, text, subtunes, clock, rate, flag, frames, names, seconds, at);
    }

    /** The file's parts past the tags: the core named, the subtune table,
     *  the bound tunes and the workspace, each checked against its place. */
    private static void assertCombined(byte[] core, byte[] sndh, List<byte[]> files, Tags tags)
            throws IOException {
        int header = Sndh.even(tags.end());
        for (int entry = 0; entry < 12; entry += 4) {
            assertEquals(header + entry, reaches(sndh, entry), "the entry at " + entry);
        }
        assertEquals("YMXS", ascii(sndh, header + 12, 4));
        byte[] patched = Arrays.copyOfRange(sndh, header, header + core.length);
        int tableAt = Tune.getLong(patched, Sndh.CORE_TABLE_AT);
        int workAt = Tune.getLong(patched, Sndh.CORE_WORK_AT);
        Tune.putLong(patched, Sndh.CORE_TABLE_AT, 0);
        Tune.putLong(patched, Sndh.CORE_WORK_AT, 0);
        assertArrayEquals(core, patched, "the core as assembled, its two offsets aside");
        assertEquals(Sndh.even(core.length), tableAt);
        assertEquals(files.size(), Tune.getWord(sndh, header + tableAt));
        // The tunes are between the subtune table and the images, so
        // that a tune's sources are beside the core (68k/YMXR.S,
        // YMXR_PCREL); the tunes that agree on what an image fixes once
        // share one image, so the reader's code appears once for them (DTX
        // abi.md 1, BINARIES.md 2).
        Bound.Set set = Bound.of(files);
        int state = 0;
        int next = tableAt + 2 + 4 * files.size();
        int[] tuneAt = new int[files.size()];
        for (int i = 0; i < files.size(); i++) {
            tuneAt[i] = Tune.getLong(sndh, header + tableAt + 2 + 4 * i);
            assertEquals(0, tuneAt[i] & 1, "subtune " + (i + 1) + " on an even address");
            assertEquals(next, tuneAt[i],
                    "subtune " + (i + 1) + " follows what comes before it");
            next = Sndh.even(tuneAt[i] + set.tunes().get(i).length);
        }
        int[] imageAt = new int[set.images().size()];
        for (int i = 0; i < imageAt.length; i++) {
            next = Tune.align(next);
            imageAt[i] = next;
            next += set.images().get(i).length;
        }
        // the transformation blocks of the subtunes of version 10 after
        // the images, each on a long, in subtune order (BINARIES.md 3.1)
        int[] blockAt = new int[files.size()];
        for (int i = 0; i < files.size(); i++) {
            byte[] block = set.blocks().get(i);
            if (block.length > 0) {
                next = Tune.align(next);
                blockAt[i] = next;
                assertArrayEquals(block, Arrays.copyOfRange(sndh, header + next,
                        header + next + block.length),
                        "subtune " + (i + 1) + "'s transformation block");
                next += block.length;
            }
        }
        next = Sndh.even(next);
        for (int i = 0; i < files.size(); i++) {
            byte[] bound = set.tunes().get(i).clone();
            // The tune reaches its image from its first byte, which the
            // combine put in and the set left at zero, and in version 10
            // its transformation block the same way.
            Tune.putLong(bound, Bound.IMAGE_AT, imageAt[set.image()[i]] - tuneAt[i]);
            if (blockAt[i] > 0) {
                Tune.putLong(bound, Bound.TRANSFORMATIONS_AT, blockAt[i] - tuneAt[i]);
            }
            assertArrayEquals(bound, Arrays.copyOfRange(sndh, header + tuneAt[i],
                    header + tuneAt[i] + bound.length),
                    "subtune " + (i + 1) + " is its bound tune");
            assertEquals("YMXB", ascii(sndh, header + tuneAt[i], 4));
            state = Math.max(state, Tune.getLong(bound, Bound.STATE_AT));
        }
        assertEquals(next, workAt, "the workspace follows the last image or block");
        int workspace = Tune.align(Tune.getWord(core, Sndh.CORE_FIXED_AT) + state) + 2;
        assertEquals(header + workAt + workspace, sndh.length,
                "the workspace is last, two bytes more than the state needs");
        for (int at = header + workAt; at < sndh.length; at++) {
            assertEquals(0, sndh[at], "a workspace byte at " + at);
        }
    }

    @Test
    void anSndhFileFromTwoTunesReadsBack() throws IOException {
        List<byte[]> files = List.of(tune("chambers"), tune("circus"));
        byte[] sndh = Sndh.of(files, new Sndh.Options("Two of the kit", "Jochen Hippel",
                List.of("Chambers", "Circus"), false, false));
        Tags tags = tags(sndh);
        assertEquals(List.of("TITL", "COMM", "CONV", "##", "TC", "FLAG", "FRMS", "!#SN", "TIME",
                "HDNS"), tags.order());
        assertEquals("TC", tags.clock(), "neither tune claims Timer C");
        assertEquals("Two of the kit", tags.text().get("TITL"));
        assertEquals("Jochen Hippel", tags.text().get("COMM"));
        assertEquals(Sndh.CONVERTER, tags.text().get("CONV"));
        assertEquals(2, tags.subtunes());
        assertEquals(50, tags.rate());
        assertEquals("y", tags.flag(), "neither tune runs an effect");
        assertArrayEquals(new int[] {0, 0}, tags.frames(), "both tunes repeat");
        assertArrayEquals(new int[] {0, 0}, tags.seconds(), "both tunes repeat");
        assertEquals(List.of("Chambers", "Circus"), tags.names());
        assertCombined(Binaries.core(false, false, true), sndh, files, tags);
    }

    @Test
    void aFileWithoutNamesOrComposerHasNeitherTag() throws IOException {
        List<byte[]> files = List.of(tune("plays-once"));
        byte[] sndh = Sndh.of(files, new Sndh.Options("Once", null, null, false, false));
        Tags tags = tags(sndh);
        assertEquals(List.of("TITL", "CONV", "##", "TC", "FLAG", "FRMS", "TIME", "HDNS"),
                tags.order());
        assertEquals(1, tags.subtunes());
        assertCombined(Binaries.core(false, false, true), sndh, files, tags);
    }

    @Test
    void theFramesTagIsTheRowsOfATuneThatPlaysOnce() throws IOException {
        byte[] file = tune("plays-once");
        org.dtx.Table table = TuneFile.read(file).table();
        assertEquals(table.rows(), table.repeat(), "plays-once has RR at R");
        Tags tags = tags(Sndh.of(List.of(file), new Sndh.Options("Once", null, null, false, false)));
        assertArrayEquals(new int[] {table.rows()}, tags.frames());
        assertEquals(4, tags.frames()[0]);
    }

    @Test
    void theTimeTagIsTheSecondsOfATuneThatPlaysOnceRoundedUp() throws IOException {
        byte[] file = tune("plays-once");
        assertEquals(50, TuneFile.read(file).frameRate());
        Tags tags = tags(Sndh.of(List.of(file), new Sndh.Options("Once", null, null, false, false)));
        assertArrayEquals(new int[] {1}, tags.seconds(),
                "4 frames at 50 Hz last under a second, and 0 marks a tune that repeats");
    }

    @Test
    void theSecondsAreRoundedUpAndAtMostAWord() {
        assertEquals(0, Sndh.seconds(0, 50), "a tune that repeats");
        assertEquals(1, Sndh.seconds(1, 50));
        assertEquals(1, Sndh.seconds(50, 50));
        assertEquals(2, Sndh.seconds(51, 50));
        assertEquals(225, Sndh.seconds(225 * 50, 50), "3:45 at 50 Hz");
        assertEquals(1, Sndh.seconds(65535, 65535));
        assertEquals(65535, Sndh.seconds(65535 * 50, 50));
        assertEquals(65535, Sndh.seconds(65535 * 50 + 1, 50), "65,536 seconds, past a word");
        assertEquals(65535, Sndh.seconds(Integer.MAX_VALUE, 1));
        assertEquals(32769, Sndh.seconds(Integer.MAX_VALUE, 65535));
    }

    /** The kit's mapped tune defers a start and maps a value, which init of
     *  every core reports -1 for (BINARIES.md 1.5). */
    @Test
    void aSubtuneThatDefersAndMapsIsRejected() throws IOException {
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(List.of(tune("mapped")),
                        new Sndh.Options("Both", null, null, false, false)));
        assertEquals("subtune 1 defers a start and maps a value, and init of every core"
                + " reports -1 for such a tune", wrong.getMessage());
    }

    @Test
    void aRateOfZeroIsRejected() throws IOException {
        byte[] file = tune("plays-once");
        file[Tune.FRAME_RATE_AT] = 0;
        file[Tune.FRAME_RATE_AT + 1] = 0;
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(List.of(file), new Sndh.Options("Zero", null, null, false, false)));
        assertEquals("subtune 1 plays at 0 Hz: an SNDH file records a rate of 1 Hz or more",
                wrong.getMessage());
    }

    @Test
    void theFlagTagListsTheTimersTheSetClaims() throws IOException {
        Tags tags = tags(Sndh.of(List.of(tune("four-timers"), tune("four-timers")),
                new Sndh.Options("Four", null, null, false, false)));
        assertEquals("abcdy", tags.flag());
        assertEquals(60, tags.rate());
        assertEquals(List.of("TITL", "CONV", "##", "!V", "FLAG", "FRMS", "TIME", "HDNS"),
                tags.order(), "the clock tag is where TC would be");
        // effects 0 to 3 run Timers A, D, B and C
        assertEquals("~ay", Sndh.flag(Sndh.claims(1)));
        assertEquals("~dy", Sndh.flag(Sndh.claims(2)));
        assertEquals("~by", Sndh.flag(Sndh.claims(4)));
        assertEquals("~cy", Sndh.flag(Sndh.claims(8)));
        assertEquals("~y", Sndh.flag(Sndh.claims(0)));
    }

    @Test
    void theClockTagIsTheVblWhereTheSetClaimsTimerC() throws IOException {
        Sndh.Options options = new Sndh.Options("Clock", null, null, false, false);
        Tags free = tags(Sndh.of(List.of(tune("chambers")), options));
        assertEquals("TC", free.clock(), "a set that leaves Timer C free plays from it");
        assertEquals("y", free.flag());
        Tags claimed = tags(Sndh.of(List.of(tune("four-timers")), options));
        assertEquals("!V", claimed.clock(), "a host cannot call play from a claimed timer");
        assertEquals("abcdy", claimed.flag());
        assertEquals(60, claimed.rate(), "the clock tag has the rate either way");
        assertEquals("TC50", Sndh.clock(Sndh.claims(0), 50, Sndh.Asked.CHOSEN));
        assertEquals("TC50", Sndh.clock(Sndh.claims(1 | 2 | 4), 50, Sndh.Asked.CHOSEN),
                "Timers A, D and B");
        assertEquals("!V50", Sndh.clock(Sndh.claims(8), 50, Sndh.Asked.CHOSEN),
                "effect 3 runs Timer C");
        assertEquals("!V50", Sndh.clock(Sndh.claims(0), 50, Sndh.Asked.VBL),
                "the VBL asked for");
        assertEquals("TC50", Sndh.clock(Sndh.claims(0), 50, Sndh.Asked.TIMER_C),
                "Timer C asked for");
        IllegalArgumentException both = assertThrows(IllegalArgumentException.class,
                () -> Sndh.clock(Sndh.claims(8), 50, Sndh.Asked.TIMER_C));
        assertEquals("the set claims Timer C and the clock asked for is Timer C: the"
                + " player's handler has that timer", both.getMessage());
    }

    /**
     * The version a document reports is the version a tool writes: the
     * stub's descriptor (BINARIES.md 4.5) and the core's (2.10), each
     * driven with a binary of another version and read against the row
     * of its table. 0.4.11 took the stub to version 2 in 4.2 and left
     * 4.5 reading 1, which a reader of the kit found and this reads.
     */
    @Test
    void theVersionsTheDocumentsReportAreTheOnesWritten() throws IOException {
        String binaries = Files.readString(Path.of("doc", "BINARIES.md"));
        byte[] files = tune("plays-once");
        byte[] stub = Binaries.stub().clone();
        Tune.putWord(stub, Prg.STUB_VERSION_AT, 9);
        byte[] sndh = Sndh.of(List.of(files), new Sndh.Options("Once", null, null, false, false));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Prg.of(stub, sndh, 0));
        assertEquals(reported(binaries, "the stub's descriptor is version").replace("V", "9"),
                wrong.getMessage());
        byte[] core = Binaries.core(false, false, true).clone();
        Tune.putWord(core, 16, 9);
        IllegalArgumentException old = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(core, List.of(files),
                        new Sndh.Options("Once", null, null, false, false)));
        assertEquals(reported(binaries, "the core's descriptor is version").replace("V", "9"),
                old.getMessage());
    }

    /** The line a table of the document reports, opening with those
     *  words: the one code span of a row that begins so. */
    private static String reported(String document, String opens) {
        Matcher said = Pattern.compile("`(" + Pattern.quote(opens) + "[^`]*)`").matcher(document);
        assertTrue(said.find(), "the document reports no line opening \"" + opens + "\"");
        return said.group(1);
    }

    @Test
    void theTimerIsTheLowestMultipleOfTheRateTheMfpCounts() {
        // 2,457,600 / 64 / 256 is 150, a row every third tick at 50 Hz,
        // and / 64 / 160 is 240, a row every fourth at 60
        assertEquals(new Prg.Timer(5, 256, 150), Prg.timer(50));
        assertEquals(new Prg.Timer(5, 160, 240), Prg.timer(60));
        assertEquals(new Prg.Timer(5, 192, 200), Prg.timer(200), "a row a tick");
        assertEquals(new Prg.Timer(5, 256, 150), Prg.timer(25), "a row every sixth");
        // 7 and 11 divide no multiple of the MFP's clock under 400
        assertEquals(Prg.OS_CLOCK, Prg.timer(77));
        for (int rate = 1; rate <= 400; rate++) {
            Prg.Timer timer = Prg.timer(rate);
            assertTrue(timer.count() >= 1 && timer.count() <= 256, rate + ": " + timer);
            assertTrue(timer.prescaler() >= 1 && timer.prescaler() <= 7, rate + ": " + timer);
            int divisor = Prg.MFP_CLOCK / (timer.count() * timer.ticks());
            assertEquals(Prg.MFP_CLOCK, divisor * timer.count() * timer.ticks(),
                    rate + ": the two fields make " + timer.ticks() + " ticks");
            assertTrue(List.of(4, 10, 16, 50, 64, 100, 200).contains(divisor),
                    rate + ": the divisor is " + divisor);
            assertTrue(timer.ticks() % rate == 0 || timer.equals(Prg.OS_CLOCK),
                    rate + ": " + timer.ticks() + " ticks is no multiple of the rate");
        }
    }

    @Test
    void theProgramCarriesTheTimerItsRateAsksFor() throws IOException {
        byte[] sndh = Sndh.of(List.of(tune("plays-once")),
                new Sndh.Options("Once", null, null, false, false));
        byte[] prg = Prg.of(sndh, 0);
        assertEquals(5, Tune.getWord(prg, 28 + Prg.STUB_PRESCALER_AT), "the divisor 64");
        assertEquals(0, Tune.getWord(prg, 28 + Prg.STUB_COUNT_AT), "256, as the MFP reads it");
        assertEquals(150, Tune.getWord(prg, 28 + Prg.STUB_TICKS_AT), "a row every third");
        assertEquals(50, Tune.getWord(prg, 28 + Prg.STUB_RATE_AT));
    }

    @Test
    void theMonitorsVblIsAskedAwayByTimerC() throws IOException {
        List<byte[]> files = List.of(tune("chambers"));
        Sndh.Options watched = new Sndh.Options("Watched", null, null, true, false);
        assertEquals("!V", tags(Sndh.of(files, watched)).clock(), "-perf names the VBL");
        Sndh.Options asked = new Sndh.Options("Watched", null, null, true, false,
                Sndh.Ticks.CHOSEN, Sndh.Asked.TIMER_C);
        assertEquals("TC", tags(Sndh.of(files, asked)).clock(), "-tc names Timer C over it");
        assertEquals(0, Tune.getWord(Prg.of(Sndh.of(files, asked), 0), 28 + Prg.STUB_FLAGS_AT),
                "and the program plays from Timer C");
    }

    @Test
    void twoRatesInOneSetAreRejected() throws IOException {
        List<byte[]> files = List.of(tune("four-timers"), tune("chambers"));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(files, new Sndh.Options("Mixed", null, null, false, false)));
        assertTrue(said(wrong).contains("50 Hz") && said(wrong).contains("60"), said(wrong));
    }

    @Test
    void moreSubtunesThanTheCountNumbersAreRejected() throws IOException {
        List<byte[]> files = Collections.nCopies(Sndh.MAX_SUBTUNES + 1, tune("circus"));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(files, new Sndh.Options("Many", null, null, false, false)));
        assertTrue(said(wrong).contains("99"), said(wrong));
        assertEquals(99, tags(Sndh.of(files.subList(0, Sndh.MAX_SUBTUNES),
                new Sndh.Options("Many", null, null, false, false))).subtunes());
    }

    @Test
    void aTuneOfAnotherVersionIsRejected() throws IOException {
        List<byte[]> files = List.of(tune("chambers"), tune("wrong-version"));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(files, new Sndh.Options("Wrong", null, null, false, false)));
        assertEquals("subtune 2: version " + ConformanceTest.WRONG_VERSION
                + " is not " + Tune.READS,
                wrong.getMessage());
    }

    @Test
    void aProgramIsTheHeaderTheStubTheFileAndAZeroLong() throws IOException {
        List<byte[]> files = List.of(tune("chambers"), tune("circus"));
        byte[] sndh = Sndh.of(files, new Sndh.Options("Two of the kit", null, null, false, false));
        byte[] stub = Binaries.stub();
        byte[] prg = Prg.of(sndh, 0);
        assertEquals(28 + stub.length + sndh.length + 4, prg.length);
        assertEquals(0x601A, Tune.getWord(prg, 0));
        assertEquals(stub.length + sndh.length, Tune.getLong(prg, 2), "the text");
        for (int at = 6; at < 26; at += 4) {
            assertEquals(0, Tune.getLong(prg, at), "the long at " + at);
        }
        assertEquals(0, Tune.getWord(prg, 26), "absflag");
        byte[] patched = Arrays.copyOfRange(prg, 28, 28 + stub.length);
        assertEquals("YMXT", ascii(patched, 4, 4));
        assertEquals(2, Tune.getWord(patched, Prg.STUB_SUBTUNES_AT));
        assertEquals(0, Tune.getWord(patched, Prg.STUB_FLAGS_AT),
                "no Timer C claimed: the stub plays at the screen's rate");
        assertEquals(50, Tune.getWord(patched, Prg.STUB_RATE_AT));
        assertEquals(0, Tune.getLong(patched, Prg.STUB_ROWS_AT));
        int core = Tune.getLong(patched, Prg.STUB_CORE_AT);
        assertEquals(Sndh.even(tags(sndh).end()), core, "the core's offset");
        assertEquals("YMXS", ascii(sndh, core + 12, 4));
        // the stub as assembled, its descriptor aside
        Arrays.fill(patched, Prg.STUB_SUBTUNES_AT, Prg.STUB_DESCRIPTOR, (byte) 0);
        byte[] plain = stub.clone();
        Arrays.fill(plain, Prg.STUB_SUBTUNES_AT, Prg.STUB_DESCRIPTOR, (byte) 0);
        assertArrayEquals(plain, patched);
        assertArrayEquals(sndh, Arrays.copyOfRange(prg, 28 + stub.length,
                28 + stub.length + sndh.length), "the SNDH file follows the stub");
        assertEquals(0, Tune.getLong(prg, prg.length - 4), "the relocation table");

        byte[] counted = Prg.of(sndh, 2000);
        assertEquals(2000, Tune.getLong(counted, 28 + Prg.STUB_ROWS_AT));
        assertEquals(0, Tune.getWord(counted, 28 + Prg.STUB_FLAGS_AT), "the rows are no flag");
    }

    @Test
    void theMonitorInTheCoreNamesTheVbl() throws IOException {
        // The monitor paints one frame of calls, which reads against the
        // raster only where the tick comes from the VBL.
        List<byte[]> files = List.of(tune("chambers"));
        byte[] plain = Sndh.of(files, new Sndh.Options("Plain", null, null, false, false));
        byte[] watched = Sndh.of(files, new Sndh.Options("Watched", null, null, true, false));
        // the core under a file written unasked reads a row through the
        // program counter (Sndh.Ticks)
        assertCombined(Binaries.core(false, false, true), plain, files, tags(plain));
        assertCombined(Binaries.core(true, false, true), watched, files, tags(watched));
        assertEquals("TC", tags(plain).clock(), "a set that leaves Timer C free plays from it");
        assertEquals("!V", tags(watched).clock(), "-perf names the VBL");
        assertEquals(0, Tune.getWord(Prg.of(plain, 0), 28 + Prg.STUB_FLAGS_AT),
                "a file naming Timer C sets no flag");
        assertEquals(Prg.FLAG_VBL, Tune.getWord(Prg.of(watched, 0), 28 + Prg.STUB_FLAGS_AT),
                "the program follows the clock the file names");
    }

    @Test
    void aFileUsesTheCoreTheThreeSwitchesSelect() throws IOException {
        // The failure this covers: a file asked for the monitor and the
        // lean tick used the monitor's core, whose flags record no
        // the lean tick, and checkCore rejected it.
        List<byte[]> files = List.of(tune("chambers"));
        for (int setting = 0; setting < 8; setting++) {
            boolean monitor = (setting & Sndh.CORE_MONITOR) != 0;
            boolean lean = (setting & Sndh.CORE_LEAN) != 0;
            boolean pcrel = (setting & Sndh.CORE_PCREL) != 0;
            byte[] sndh = Sndh.of(files, new Sndh.Options("Both", null, null, monitor, lean,
                    pcrel ? Sndh.Ticks.PCREL : Sndh.Ticks.ABSOLUTE));
            assertEquals(setting, Tune.getWord(sndh,
                    Sndh.even(tags(sndh).end()) + Sndh.CORE_FLAGS_AT),
                    "the file's core reads back the switches asked for");
            assertCombined(Binaries.core(monitor, lean, pcrel), sndh, files, tags(sndh));
            assertEquals(monitor ? Prg.FLAG_VBL : 0,
                    Tune.getWord(Prg.of(sndh, 0), 28 + Prg.STUB_FLAGS_AT),
                    "-perf alone of the three sets a stub flag, the VBL it names");
        }
    }

    @Test
    void aFileWithASubtuneThatDefersAStartUsesTheCoreWithTheDeferredStartIn()
            throws IOException {
        // A core without the deferred start is smaller and init returns -1
        // for a tune that defers a start (BINARIES.md 2.1), so a file uses
        // the larger core only where a subtune's effects byte sets bit 4.
        List<byte[]> plain = List.of(tune("chambers"));
        List<byte[]> deferring = List.of(tune("chambers"), tune("deferred"));
        assertEquals(0, tune("chambers")[Tune.EFFECTS_AT] & Columns.DEFERS);
        assertEquals(Columns.DEFERS, tune("deferred")[Tune.EFFECTS_AT] & Columns.DEFERS);
        for (int setting = 0; setting < 8; setting++) {
            boolean monitor = (setting & Sndh.CORE_MONITOR) != 0;
            boolean lean = (setting & Sndh.CORE_LEAN) != 0;
            boolean pcrel = (setting & Sndh.CORE_PCREL) != 0;
            Sndh.Options options = new Sndh.Options("Deferred", null, null, monitor, lean,
                    pcrel ? Sndh.Ticks.PCREL : Sndh.Ticks.ABSOLUTE);
            for (List<byte[]> files : List.of(plain, deferring)) {
                boolean defer = files == deferring;
                byte[] sndh = Sndh.of(files, options);
                assertEquals(setting | (defer ? Sndh.CORE_DEFER : 0), Tune.getWord(sndh,
                        Sndh.even(tags(sndh).end()) + Sndh.CORE_FLAGS_AT),
                        "bit 3 of the file's flags where a subtune defers a start");
                assertCombined(Binaries.core(monitor, lean, pcrel, defer), sndh, files,
                        tags(sndh));
            }
            byte[] without = Binaries.core(monitor, lean, pcrel);
            IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                    () -> Sndh.of(without, deferring, options));
            assertTrue(said(wrong).contains("flags at " + Sndh.CORE_FLAGS_AT)
                    && said(wrong).contains("needs bit 3 set"), said(wrong));
        }
        Matcher larger = Pattern.compile("a core with it is ([\\d,]+) bytes larger")
                .matcher(String.join(" ", Files.readString(Path.of("doc/BINARIES.md"))
                        .split("\\s+")));
        assertTrue(larger.find(), "BINARIES.md 2.1 has no size of the deferred start");
        assertEquals(Integer.parseInt(larger.group(1).replace(",", "")),
                Binaries.core(false, false, true, true).length
                        - Binaries.core(false, false, true).length,
                "the bytes the deferred start adds to the plain core, as BINARIES.md 2.1"
                        + " reads them");
    }

    /**
     * The core under a file whose subtunes map a value has the lookup in
     * (BINARIES.md 2.1): a core without it reads bound tunes to version 9,
     * and its init reports -1 for a tune of version 10. A file uses the
     * larger core only where a subtune's effects byte sets bit 5, and the
     * core with the deferred start and the lookup in where a subtune sets
     * bit 4 and one sets bit 5. A core without the lookup named by the
     * caller reports the version it reads before its flags (2.10).
     */
    @Test
    void aFileWithASubtuneThatMapsAValueUsesTheCoreWithTheLookupIn() throws IOException {
        byte[] fade = fade();
        int switches = Columns.MAPS | Columns.DEFERS;
        assertEquals(Columns.MAPS, fade[Tune.EFFECTS_AT] & switches);
        assertEquals(Columns.DEFERS, tune("deferred")[Tune.EFFECTS_AT] & switches);
        List<List<byte[]>> sets = List.of(List.of(tune("chambers")),
                List.of(tune("chambers"), fade), List.of(tune("deferred"), fade));
        for (int setting = 0; setting < 8; setting++) {
            boolean monitor = (setting & Sndh.CORE_MONITOR) != 0;
            boolean lean = (setting & Sndh.CORE_LEAN) != 0;
            boolean pcrel = (setting & Sndh.CORE_PCREL) != 0;
            Sndh.Options options = new Sndh.Options("Mapped", null, null, monitor, lean,
                    pcrel ? Sndh.Ticks.PCREL : Sndh.Ticks.ABSOLUTE);
            for (List<byte[]> files : sets) {
                int effects = 0;
                for (byte[] file : files) {
                    effects |= file[Tune.EFFECTS_AT];
                }
                boolean defer = (effects & Columns.DEFERS) != 0;
                boolean map = (effects & Columns.MAPS) != 0;
                byte[] sndh = Sndh.of(files, options);
                assertEquals(setting | (defer ? Sndh.CORE_DEFER : 0) | (map ? Sndh.CORE_MAP : 0),
                        Tune.getWord(sndh, Sndh.even(tags(sndh).end()) + Sndh.CORE_FLAGS_AT),
                        "bit 4 of the file's flags where a subtune maps a value");
                assertCombined(Binaries.core(monitor, lean, pcrel, defer, map), sndh, files,
                        tags(sndh));
            }
            for (boolean defer : new boolean[] {false, true}) {
                byte[] without = Binaries.core(monitor, lean, pcrel, defer);
                IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                        () -> Sndh.of(without, List.of(fade), options));
                assertEquals("the core reads bound tunes to version 9, and this binds at 10",
                        wrong.getMessage());
            }
        }
        String binaries = String.join(" ", Files.readString(Path.of("doc/BINARIES.md"))
                .split("\\s+"));
        Matcher larger = Pattern.compile("`YMXR_sndh-map\\.bin` is ([\\d,]+) bytes larger than"
                + " `YMXR_sndh\\.bin`").matcher(binaries);
        assertTrue(larger.find(), "BINARIES.md 2.1 has no size of the lookup");
        assertEquals(Integer.parseInt(larger.group(1).replace(",", "")),
                Binaries.core(false, false, true, false, true).length
                        - Binaries.core(false, false, true).length,
                "the bytes the lookup adds to the plain core, as BINARIES.md 2.1 reads them");
        Matcher words = Pattern.compile("`YMXR_sndh-map\\.bin` reads (\\d+), and"
                + " `YMXR_sndh-defer-map\\.bin` (\\d+)").matcher(binaries);
        assertTrue(words.find(), "BINARIES.md 2.1 has no flags word of the lookup");
        assertEquals(Integer.parseInt(words.group(1)), Tune.getWord(
                Binaries.core(false, false, true, false, true), Sndh.CORE_FLAGS_AT));
        assertEquals(Integer.parseInt(words.group(2)), Tune.getWord(
                Binaries.core(false, false, true, true, true), Sndh.CORE_FLAGS_AT));
    }

    /**
     * A tick of a core with the lookup in reads its row through a
     * displacement from its handler, and the handlers begin at the field at
     * 26, so the tool measures the reach from there (BINARIES.md 2.1): a
     * mapped source of 20,000 rows ends within it from there and past it
     * from the core's first byte, and one of 30,000 rows ends past it, so
     * that file is on the core that reads an address, and -pcrel reports
     * the line that names the first handler.
     */
    @Test
    void aFileOnTheCoreWithTheLookupIsMeasuredFromItsFirstHandler() throws IOException {
        byte[] core = Binaries.core(false, false, true, false, true);
        int handlers = Tune.getWord(core, Sndh.CORE_HANDLERS_AT);
        Bound.Set set = Bound.of(List.of(fade()));
        int end = Sndh.even(core.length) + 2 + 4 + set.tunes().get(0).length;
        assertEquals(end - handlers, Sndh.tunesEnd(core, set, new byte[0]),
                "the tunes end counted from the first handler");
        byte[] plain = Binaries.core();
        assertEquals(Sndh.even(plain.length) + 2 + 4 + set.tunes().get(0).length,
                Sndh.tunesEnd(plain, set, new byte[0]), "and from the first byte, at 0");
        Sndh.Options chosen = new Sndh.Options("Long", null, null, false, false);
        Sndh.Options asked = new Sndh.Options("Long", null, null, false, false,
                Sndh.Ticks.PCREL);
        List<byte[]> within = List.of(longMapped(20000));
        Bound.Set near = Bound.of(within);
        assertTrue(Sndh.tunesEnd(core, near, new byte[0]) + handlers > Sndh.PCREL_REACH
                && Sndh.tunesEnd(core, near, new byte[0]) <= Sndh.PCREL_REACH,
                "20,000 rows end past the reach from the first byte and within it from"
                        + " the first handler");
        byte[] sndh = Sndh.of(within, chosen);
        assertEquals(Sndh.CORE_PCREL | Sndh.CORE_MAP, Tune.getWord(sndh,
                Sndh.even(tags(sndh).end()) + Sndh.CORE_FLAGS_AT),
                "the core that reads a row through the program counter");
        assertCombined(core, sndh, within, tags(sndh));
        List<byte[]> past = List.of(longMapped(30000));
        sndh = Sndh.of(past, chosen);
        assertEquals(Sndh.CORE_MAP, Tune.getWord(sndh,
                Sndh.even(tags(sndh).end()) + Sndh.CORE_FLAGS_AT),
                "the core that reads an address");
        assertCombined(Binaries.core(false, false, false, false, true), sndh, past, tags(sndh));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(past, asked));
        int last = Sndh.tunesEnd(core, Bound.of(past), new byte[0]);
        assertEquals("the tunes end " + last + " bytes past the core's first handler, at "
                + handlers + ", and a tick that reads a row through the program counter"
                + " reaches " + Sndh.PCREL_REACH, wrong.getMessage());
        assertEquals(reported(Files.readString(Path.of("doc", "BINARIES.md")),
                "the tunes end B bytes past the core's first handler")
                .replace("B", String.valueOf(last)).replace("F", String.valueOf(handlers)),
                wrong.getMessage());
    }

    /** The fade of YMXS (YmxsTest), its tune file as ymxs-to-ymxr writes
     *  it: a square on `setR8` through four transformations, version 10. */
    static byte[] fade() {
        org.ymxs.YMXS.Tune tune = YmxsTest.fade();
        Schema.Made made = Schema.of(Padding.toUnit(tune, YmToYmxr.UNIT, new Report()));
        return Tune.write(made.columns(), made.sources(), made.rate(), YmToYmxr.UNIT,
                BuiltTunes.RING, new Report(), tune.title()).file();
    }

    @Test
    void aFileWhoseTunesEndPastTheReachIsRefusedWhereTheDisplacementWasAskedFor()
            throws IOException {
        // A tick of that core reads its row through a signed word
        // displacement from the instruction that reads it, so the tool
        // reads the file's last bound tune against the core's first byte
        // (BINARIES.md 5.5): the tool reports a file whose tunes end
        // further off, where init would report -1 on the machine. The tunes
        // of a set are a few hundred bytes each, so the core is padded to
        // the reach rather than the file filled with subtunes.
        List<byte[]> files = List.of(tune("turrican"));
        byte[] core = Arrays.copyOf(Binaries.core(false, false, true), Sndh.PCREL_REACH);
        Sndh.Options options = new Sndh.Options("Far", null, null, false, false,
                Sndh.Ticks.PCREL);
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(core, files, options));
        assertTrue(said(wrong).contains("past the core's first byte")
                && said(wrong).contains(String.valueOf(Sndh.PCREL_REACH)), said(wrong));
        // the same tunes on the core as it is, whose tunes end within
        // the reach
        assertTrue(Sndh.of(files, options).length > 0,
                "the core as assembled packs the tunes that end within its reach");
    }

    @Test
    void aFileWhoseTunesEndPastTheReachStandsOnTheCoreThatReadsAnAddress() throws IOException {
        // A source of 30,000 rows puts the file's one bound tune past the
        // reach by itself, so the core chosen for it reads a row through
        // an absolute address (Sndh.Ticks), and -pcrel reports such a file
        // rather than putting that core under it.
        List<byte[]> files = List.of(longSource(30000));
        byte[] sndh = Sndh.of(files, new Sndh.Options("Long", null, null, false, false));
        int flags = Tune.getWord(sndh, Sndh.even(tags(sndh).end()) + Sndh.CORE_FLAGS_AT);
        assertEquals(0, flags & Sndh.CORE_PCREL,
                "the core under a file past the reach reads an address");
        assertCombined(Binaries.core(false, false, false), sndh, files, tags(sndh));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(files, new Sndh.Options("Long", null, null, false, false,
                        Sndh.Ticks.PCREL)));
        assertTrue(said(wrong).contains("past the core's first byte")
                && said(wrong).contains(String.valueOf(Sndh.PCREL_REACH)), said(wrong));
    }

    /** A tune file of one source of that many rows, on `setR8`, so that
     *  its bound tune runs past {@link Sndh#PCREL_REACH} by itself. */
    private static byte[] longSource(int rows) {
        byte[][] c = new byte[Columns.C][8];
        c[7][0] = (byte) (0x80 | 0x3E);                  // voice A's tone alone
        c[0][0] = 0x40;
        c[1][0] = (byte) (0x80 | 1);
        int e0 = Columns.EFFECT;
        c[e0][0] = (byte) (0x80 | 8);                    // setR8
        c[e0 + 1][0] = (byte) (0x80 | 1);
        c[e0 + 2][0] = (byte) (0x80 | Columns.TIMER_RESET | Columns.PLACE_RESET | 5);
        c[e0 + 3][0] = 100;
        byte[] source = new byte[rows];
        for (int i = 0; i < rows; i++) {
            source[i] = (byte) (i % 16);
        }
        source[rows - 1] = (byte) (0x80 | 12);           // the marker
        Sources sources = new Sources(List.of(Sources.Source.of(Effects.SID, 0, source, rows)));
        return Tune.write(new Columns(c, 0, 0b0001), sources, 50, YmToYmxr.UNIT,
                BuiltTunes.RING, new Report()).file();
    }

    /** The same source through transformation 1 of the sixteen of
     *  {@link BuiltTunes#fade}, a tune file of version 10. */
    static byte[] longMapped(int rows) {
        byte[][] c = new byte[Columns.MAPPED][8];
        c[7][0] = (byte) (0x80 | 0x3E);                  // voice A's tone alone
        c[0][0] = 0x40;
        c[1][0] = (byte) (0x80 | 1);
        int e0 = Columns.EFFECT;
        c[e0][0] = (byte) (0x80 | 8);                    // setR8
        c[e0 + 1][0] = (byte) (0x80 | 1);
        c[e0 + 2][0] = (byte) (0x80 | Columns.TIMER_RESET | Columns.PLACE_RESET | 5);
        c[e0 + 3][0] = 100;
        c[Columns.MAPPING][0] = (byte) (0x80 | 1);       // through transformation 1
        byte[] source = new byte[rows];
        for (int i = 0; i < rows; i++) {
            source[i] = (byte) (i % 16);
        }
        source[rows - 1] = (byte) (0x80 | 12);           // the marker
        Sources sources = new Sources(List.of(Sources.Source.of(Effects.SID, 0, source, rows)));
        return Tune.write(new Columns(c, 0, Columns.MAPS | 0b0001, BuiltTunes.fade()), sources,
                50, YmToYmxr.UNIT, BuiltTunes.RING, new Report()).file();
    }

    @Test
    void aCoreWithoutTheRowReadThroughTheProgramCounterIsRejectedWhereItWasAskedFor()
            throws IOException {
        List<byte[]> files = List.of(tune("chambers"));
        Sndh.Options options = new Sndh.Options("Pcrel", null, null, false, false,
                Sndh.Ticks.PCREL);
        byte[] address = Binaries.core(false, false, false);
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(address, files, options));
        assertTrue(said(wrong).contains("flags at " + Sndh.CORE_FLAGS_AT)
                && said(wrong).contains("needs bit 2 set"), said(wrong));
    }

    @Test
    void aCoreWithoutTheLeanTickIsRejectedWhereTheLeanTickWasAskedFor() throws IOException {
        List<byte[]> files = List.of(tune("chambers"));
        Sndh.Options lean = new Sndh.Options("Lean", null, null, false, true);
        Sndh.Options both = new Sndh.Options("Both", null, null, true, true);
        for (Sndh.Options options : List.of(lean, both)) {
            byte[] without = Binaries.core(options.monitor(), false, false);
            IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                    () -> Sndh.of(without, files, options));
            assertTrue(said(wrong).contains("flags at " + Sndh.CORE_FLAGS_AT)
                    && said(wrong).contains("needs bit 1 set"), said(wrong));
            Sndh.checkCore(Binaries.core(options.monitor(), true, false), options.monitor(),
                    true, Sndh.binds(files));
        }
    }

    @Test
    void aCoreWithoutTheMonitorIsRejectedWhereTheMonitorWasAskedFor() throws IOException {
        List<byte[]> files = List.of(tune("chambers"));
        Sndh.Options options = new Sndh.Options("Watched", null, null, true, false);
        byte[] core = Binaries.core();
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(core, files, options));
        assertTrue(said(wrong).contains("flags at " + Sndh.CORE_FLAGS_AT)
                && said(wrong).contains("needs bit 0 set"), said(wrong));
        byte[] sndh = Sndh.of(Binaries.core(true, false, true), files, options);
        assertEquals(Sndh.CORE_MONITOR | Sndh.CORE_PCREL, Tune.getWord(sndh,
                Sndh.even(tags(sndh).end()) + Sndh.CORE_FLAGS_AT),
                "the monitor core passes the same check");
    }

    @Test
    void aSetClaimingTimerCAtAnotherRateMakesNoProgram() throws IOException {
        byte[] sndh = Sndh.of(List.of(tune("four-timers")),
                new Sndh.Options("Four", null, null, false, false));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Prg.of(sndh, 0));
        assertEquals("the file plays from the VBL at 60 Hz: the stub's VBL is a 50 Hz clock,"
                + " so this set needs a separate host or the VBL asked for", wrong.getMessage());
        // the VBL asked for is that separate host named
        assertEquals(Prg.FLAG_VBL, Tune.getWord(Prg.of(sndh, 0, Sndh.Asked.VBL),
                28 + Prg.STUB_FLAGS_AT));
        IllegalArgumentException timer = assertThrows(IllegalArgumentException.class,
                () -> Prg.of(sndh, 0, Sndh.Asked.TIMER_C));
        assertEquals("the set claims Timer C and the clock asked for is Timer C: the"
                + " player's handler has that timer", timer.getMessage());
    }

    @Test
    void aSetClaimingTimerCAtFiftyHertzPlaysFromTheVbl() throws IOException {
        byte[] file = tune("four-timers").clone();
        Tune.putWord(file, Tune.FRAME_RATE_AT, 50);
        byte[] sndh = Sndh.of(List.of(file), new Sndh.Options("Four at fifty", null, null, false, false));
        assertEquals("abcdy", tags(sndh).flag());
        byte[] prg = Prg.of(sndh, 0);
        assertEquals(Prg.FLAG_VBL, Tune.getWord(prg, 28 + Prg.STUB_FLAGS_AT), "Timer C claimed");
        assertEquals(50, Tune.getWord(prg, 28 + Prg.STUB_RATE_AT));
    }

    @Test
    void aSetWithoutTimerCLeavesBitOneClear() throws IOException {
        byte[] file = tune("chambers").clone();
        Tune.putWord(file, Tune.FRAME_RATE_AT, 60);
        byte[] prg = Prg.of(Sndh.of(List.of(file), new Sndh.Options("Sixty", null, null, false, false)),
                0);
        assertEquals(0, Tune.getWord(prg, 28 + Prg.STUB_FLAGS_AT), "neither flag");
        assertEquals(60, Tune.getWord(prg, 28 + Prg.STUB_RATE_AT));
    }

    @Test
    void aTitleThatReadsLikeATagPatchesNothing() throws IOException {
        byte[] sndh = Sndh.of(List.of(tune("chambers")),
                new Sndh.Options("TC##HDNS FLAG~c", "##99", null, false, false));
        Tags tags = tags(sndh);
        assertEquals("TC##HDNS FLAG~c", tags.text().get("TITL"));
        assertEquals("##99", tags.text().get("COMM"));
        byte[] prg = Prg.of(sndh, 0);
        assertEquals(1, Tune.getWord(prg, 28 + Prg.STUB_SUBTUNES_AT));
        assertEquals(50, Tune.getWord(prg, 28 + Prg.STUB_RATE_AT));
        assertEquals(0, Tune.getWord(prg, 28 + Prg.STUB_FLAGS_AT), "the FLAG tag reads ~y");
        assertEquals(Sndh.even(tags.end()), Tune.getLong(prg, 28 + Prg.STUB_CORE_AT),
                "the core's offset");
    }

    @Test
    void aFileThatIsNotAnSndhFileMakesNoProgram() throws IOException {
        byte[] core = Binaries.core();
        for (byte[] file : List.of(core, new byte[0])) {
            IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                    () -> Prg.of(file, 0));
            assertTrue(said(wrong).contains("no SNDH at 12"), said(wrong));
        }
        byte[] sndh = Sndh.of(List.of(tune("circus")), new Sndh.Options("Cut", null, null, false, false));
        byte[] cut = Arrays.copyOf(sndh, tags(sndh).end() - 4);
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Prg.of(cut, 0));
        assertTrue(said(wrong).contains("no HDNS"), said(wrong));
    }
}
