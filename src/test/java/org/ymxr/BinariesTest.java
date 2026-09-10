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
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The five binaries as the build assembled them, and what the tools make
 * of them: each of the four cores' descriptors and the stub's as
 * BINARIES.md defines them, an SNDH file from the kit's tunes read back
 * tag by tag and part by part, and a program around it.
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
     *  the flags word at 22, a zero word at 26, the state byte in
     *  the core, the two offsets it is written with unpatched, and three
     *  entries that reach even addresses in the core. */
    private static void assertCore(byte[] core, int flags) throws IOException {
        assertEquals(0, core.length & 1, "the core is even-sized");
        assertArrayEquals(Sndh.CORE_MAGIC, Arrays.copyOfRange(core, 12, 16));
        assertEquals(1, Tune.getWord(core, 16));
        assertEquals(Bound.VERSION, Tune.getWord(core, 18));
        assertEquals(PlayerTest.equates().get("YMXR_FIXED"), Tune.getWord(core, 20),
                "the workspace's fixed bytes are the player's own");
        assertEquals(flags, Tune.getWord(core, 22), "the flags word");
        assertEquals(0, Tune.getWord(core, 26));
        int state = Tune.getWord(core, 24);
        assertTrue(state >= Sndh.CORE_DESCRIPTOR && state < core.length,
                "the state byte stands at " + state);
        assertEquals(0, Tune.getLong(core, 28));
        assertEquals(0, Tune.getLong(core, 32));
        for (int entry = 0; entry < 12; entry += 4) {
            int to = reaches(core, entry);
            assertTrue(to >= Sndh.CORE_DESCRIPTOR && to < core.length && (to & 1) == 0,
                    "the entry at " + entry + " reaches " + to);
        }
    }

    @Test
    void theCoreIsWhatItsDescriptorStates() throws IOException {
        assertCore(Binaries.core(), 0);
    }

    @Test
    void theMonitorCoreIsTheSameCoreWithTheMonitorIn() throws IOException {
        byte[] core = Binaries.core();
        byte[] monitor = Binaries.core(true, false);
        assertCore(monitor, Sndh.CORE_MONITOR);
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
    void eachOfTheTwoSwitchesFourSettingsIsACoreWhoseFlagsSayWhichItIs() throws IOException {
        // The tool selects a core by the switches passed and checks it against
        // the flags word (Sndh.checkCore), so each of the four settings
        // needs a separate core whose word reads the setting back.
        Set<String> named = new LinkedHashSet<>();
        for (int setting = 0; setting < 4; setting++) {
            boolean monitor = (setting & Sndh.CORE_MONITOR) != 0;
            boolean lean = (setting & Sndh.CORE_LEAN) != 0;
            byte[] core = Binaries.core(monitor, lean);
            assertCore(core, setting);
            Sndh.checkCore(core, monitor, lean);
            named.add(Binaries.binary(monitor, lean).name());
        }
        assertEquals(4, named.size(), "the four settings are four binaries: " + named);
    }

    @Test
    void theLeanTickIsTheSameBytesOffEitherCore() throws IOException {
        // Both switches belong to the player, so the lean tick uses the
        // same code off the core with the monitor in as off the plain one.
        int off = Binaries.core().length - Binaries.core(false, true).length;
        assertEquals(off, Binaries.core(true, false).length
                - Binaries.core(true, true).length,
                "the lean tick is " + off + " bytes off the plain core");
        assertTrue(off > 0, "the lean core is smaller by " + off + " bytes");
    }

    @Test
    void theStubIsWhatItsDescriptorStates() {
        byte[] stub = Binaries.stub();
        assertEquals(0, stub.length & 1, "the stub is even-sized");
        int to = reaches(stub, 0);
        assertTrue(to >= Prg.STUB_DESCRIPTOR && to < stub.length, "the stub's bra.w reaches " + to);
        assertArrayEquals(Prg.STUB_MAGIC, Arrays.copyOfRange(stub, 4, 8));
        assertEquals(1, Tune.getWord(stub, 8));
    }

    /**
     * The five under {@code go/binaries/data} are the five the classes
     * carry. A Go module fetched by its import path contains the files a
     * commit has in it, so those are what a Go executable embeds, and a
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

    @Test
    void aBinaryNotCarriedIsSaidSo() {
        IllegalStateException none = assertThrows(IllegalStateException.class,
                () -> Binaries.carried("none.bin"));
        assertTrue(said(none).contains("/org/ymxr/68k/none.bin"), said(none));
    }

    /**
     * The tag block walked from SNDH to HDNS as the tool writes it: the
     * tags in order, each text tag's text, the '##' count, the TC rate,
     * the FLAG letters after its '~', FRMS's longs, the names, and where
     * the block ends, past HDNS. A zero byte where a tag would begin is a
     * pad, and stands on an odd position.
     */
    record Tags(List<String> order, Map<String, String> text, int subtunes, int rate,
                String flag, int[] frames, List<String> names, int end) {
    }

    static Tags tags(byte[] sndh) {
        assertEquals("SNDH", ascii(sndh, 12, 4));
        List<String> order = new ArrayList<>();
        Map<String, String> text = new HashMap<>();
        int subtunes = -1;
        int rate = -1;
        String flag = "";
        int[] frames = new int[0];
        List<String> names = new ArrayList<>();
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
            } else if (name.startsWith("TC")) {
                order.add("TC");
                int to = at + 2;
                while (sndh[to] != 0) {
                    to++;
                }
                rate = Integer.parseInt(ascii(sndh, at + 2, to - at - 2));
                at = to + 1;
            } else if (name.equals("FRMS")) {
                order.add(name);
                assertTrue(subtunes >= 0, "'##' stands before FRMS");
                at += 4;
                frames = new int[subtunes];
                for (int i = 0; i < subtunes; i++) {
                    frames[i] = Tune.getLong(sndh, at + 4 * i);
                }
                at += 4 * subtunes;
            } else if (name.equals("!#SN")) {
                order.add(name);
                assertTrue(subtunes >= 0, "'##' stands before !#SN");
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
        return new Tags(order, text, subtunes, rate, flag, frames, names, at);
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
        // The images stand between the subtune table and the tunes: those
        // that agree on what an image fixes once share one, so the reader's
        // code stands once for them (DTX abi.md 1, BINARIES.md 2).
        Bound.Set set = Bound.of(files);
        int state = 0;
        int next = tableAt + 2 + 4 * files.size();
        int[] imageAt = new int[set.images().size()];
        for (int i = 0; i < imageAt.length; i++) {
            next = Tune.align(next);
            imageAt[i] = next;
            next += set.images().get(i).length;
        }
        for (int i = 0; i < files.size(); i++) {
            int at = Tune.getLong(sndh, header + tableAt + 2 + 4 * i);
            assertEquals(0, at & 1, "subtune " + (i + 1) + " on an even address");
            assertEquals(next, at, "subtune " + (i + 1) + " follows what stands before it");
            byte[] bound = set.tunes().get(i).clone();
            // The tune reaches its image from its first byte, which the
            // combine put in and the set left at zero.
            Tune.putLong(bound, Bound.IMAGE_AT, imageAt[set.image()[i]] - at);
            assertArrayEquals(bound, Arrays.copyOfRange(sndh, header + at,
                    header + at + bound.length), "subtune " + (i + 1) + " is its bound tune");
            assertEquals("YMXB", ascii(sndh, header + at, 4));
            state = Math.max(state, Tune.getLong(bound, Bound.STATE_AT));
            next = Sndh.even(at + bound.length);
        }
        assertEquals(next, workAt, "the workspace follows the last tune");
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
        assertEquals(List.of("TITL", "COMM", "CONV", "##", "TC", "FLAG", "FRMS", "!#SN", "HDNS"),
                tags.order());
        assertEquals("Two of the kit", tags.text().get("TITL"));
        assertEquals("Jochen Hippel", tags.text().get("COMM"));
        assertEquals(Sndh.CONVERTER, tags.text().get("CONV"));
        assertEquals(2, tags.subtunes());
        assertEquals(50, tags.rate());
        assertEquals("y", tags.flag(), "neither tune runs an effect");
        assertArrayEquals(new int[] {0, 0}, tags.frames(), "both tunes repeat");
        assertEquals(List.of("Chambers", "Circus"), tags.names());
        assertCombined(Binaries.core(), sndh, files, tags);
    }

    @Test
    void aFileWithoutNamesOrComposerHasNeitherTag() throws IOException {
        List<byte[]> files = List.of(tune("plays-once"));
        byte[] sndh = Sndh.of(files, new Sndh.Options("Once", null, null, false, false));
        Tags tags = tags(sndh);
        assertEquals(List.of("TITL", "CONV", "##", "TC", "FLAG", "FRMS", "HDNS"), tags.order());
        assertEquals(1, tags.subtunes());
        assertCombined(Binaries.core(), sndh, files, tags);
    }

    @Test
    void theFramesTagGivesTheRowsOfATuneThatPlaysOnce() throws IOException {
        byte[] file = tune("plays-once");
        org.dtx.Table table = TuneFile.read(file).table();
        assertEquals(table.rows(), table.repeat(), "plays-once has RR at R");
        Tags tags = tags(Sndh.of(List.of(file), new Sndh.Options("Once", null, null, false, false)));
        assertArrayEquals(new int[] {table.rows()}, tags.frames());
        assertEquals(4, tags.frames()[0]);
    }

    @Test
    void theFlagTagListsTheTimersTheSetClaims() throws IOException {
        Tags tags = tags(Sndh.of(List.of(tune("four-timers"), tune("four-timers")),
                new Sndh.Options("Four", null, null, false, false)));
        assertEquals("abcdy", tags.flag());
        assertEquals(60, tags.rate());
        // effects 0 to 3 run Timers A, D, B and C
        assertEquals("~ay", Sndh.flag(Sndh.claims(1)));
        assertEquals("~dy", Sndh.flag(Sndh.claims(2)));
        assertEquals("~by", Sndh.flag(Sndh.claims(4)));
        assertEquals("~cy", Sndh.flag(Sndh.claims(8)));
        assertEquals("~y", Sndh.flag(Sndh.claims(0)));
    }

    @Test
    void twoRatesInOneSetAreRejected() throws IOException {
        List<byte[]> files = List.of(tune("four-timers"), tune("chambers"));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(files, new Sndh.Options("Mixed", null, null, false, false)));
        assertTrue(said(wrong).contains("50 Hz") && said(wrong).contains("60"), said(wrong));
    }

    @Test
    void moreSubtunesThanTheCountHoldsAreRejected() throws IOException {
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
        assertEquals("subtune 2: version 3 is not 2", wrong.getMessage());
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
    void theMonitorInTheCoreSetsTheStubsClearBit() throws IOException {
        List<byte[]> files = List.of(tune("chambers"));
        byte[] plain = Sndh.of(files, new Sndh.Options("Plain", null, null, false, false));
        byte[] watched = Sndh.of(files, new Sndh.Options("Watched", null, null, true, false));
        assertCombined(Binaries.core(), plain, files, tags(plain));
        assertCombined(Binaries.core(true, false), watched, files, tags(watched));
        assertEquals(0, Tune.getWord(Prg.of(plain, 0), 28 + Prg.STUB_FLAGS_AT),
                "the plain core leaves the desktop's pixels where they are");
        assertEquals(Prg.FLAG_CLEAR, Tune.getWord(Prg.of(watched, 0), 28 + Prg.STUB_FLAGS_AT),
                "the monitor core has the program clear the screen");
    }

    @Test
    void aFileTakesTheCoreOfTheTwoSwitchesItIsAskedFor() throws IOException {
        // The failure this covers: a file asked for the monitor and the
        // lean tick used the monitor's core, whose flags record no
        // the lean tick, and checkCore refused it.
        List<byte[]> files = List.of(tune("chambers"));
        for (int setting = 0; setting < 4; setting++) {
            boolean monitor = (setting & Sndh.CORE_MONITOR) != 0;
            boolean lean = (setting & Sndh.CORE_LEAN) != 0;
            byte[] sndh = Sndh.of(files, new Sndh.Options("Both", null, null, monitor, lean));
            assertEquals(setting, Tune.getWord(sndh,
                    Sndh.even(tags(sndh).end()) + Sndh.CORE_FLAGS_AT),
                    "the file's core reads back the switches asked for");
            assertCombined(Binaries.core(monitor, lean), sndh, files, tags(sndh));
            assertEquals(monitor ? Prg.FLAG_CLEAR : 0,
                    Tune.getWord(Prg.of(sndh, 0), 28 + Prg.STUB_FLAGS_AT),
                    "the program clears the screen for the monitor's bars and not otherwise");
        }
    }

    @Test
    void aCoreWithoutTheLeanTickIsRejectedWhereTheLeanTickWasAskedFor() throws IOException {
        List<byte[]> files = List.of(tune("chambers"));
        Sndh.Options lean = new Sndh.Options("Lean", null, null, false, true);
        Sndh.Options both = new Sndh.Options("Both", null, null, true, true);
        for (Sndh.Options options : List.of(lean, both)) {
            byte[] without = Binaries.core(options.monitor(), false);
            IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                    () -> Sndh.of(without, files, options));
            assertTrue(said(wrong).contains("flags at " + Sndh.CORE_FLAGS_AT)
                    && said(wrong).contains("needs bit 1 set"), said(wrong));
            Sndh.checkCore(Binaries.core(options.monitor(), true), options.monitor(), true);
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
                && said(wrong).contains("read 0"), said(wrong));
        byte[] sndh = Sndh.of(Binaries.core(true, false), files, options);
        assertEquals(Sndh.CORE_MONITOR, Tune.getWord(sndh,
                Sndh.even(tags(sndh).end()) + Sndh.CORE_FLAGS_AT),
                "the monitor core passes the same check");
    }

    @Test
    void aSetClaimingTimerCAtAnotherRateMakesNoProgram() throws IOException {
        byte[] sndh = Sndh.of(List.of(tune("four-timers")),
                new Sndh.Options("Four", null, null, false, false));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Prg.of(sndh, 0));
        assertEquals("the set claims Timer C and plays at 60 Hz: the stub then plays from the"
                + " VBL, a 50 Hz clock, so this set needs a host of its own", wrong.getMessage());
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
