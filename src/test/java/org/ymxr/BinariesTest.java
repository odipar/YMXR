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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The two binaries as the build assembled them, and what the tools make
 * of them: the core's and the stub's descriptors as BINARIES.md states
 * them, an SNDH file from the kit's tunes read back tag by tag and part
 * by part, and a program around it.
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

    @Test
    void theCoreIsWhatItsDescriptorStates() throws IOException {
        byte[] core = Binaries.core();
        assertEquals(0, core.length & 1, "the core is even-sized");
        assertArrayEquals(Sndh.CORE_MAGIC, Arrays.copyOfRange(core, 12, 16));
        assertEquals(1, Tune.getWord(core, 16));
        assertEquals(Bound.VERSION, Tune.getWord(core, 18));
        assertEquals(56, Tune.getWord(core, 20));
        assertEquals(PlayerTest.equates().get("YMXR_FIXED"), Tune.getWord(core, 20));
        int state = Tune.getWord(core, 22);
        assertTrue(state >= Sndh.CORE_DESCRIPTOR && state < core.length,
                "the state byte stands at " + state);
        assertEquals(0, Tune.getLong(core, 24));
        assertEquals(0, Tune.getLong(core, 28));
        for (int entry = 0; entry < 12; entry += 4) {
            int to = reaches(core, entry);
            assertTrue(to >= Sndh.CORE_DESCRIPTOR && to < core.length && (to & 1) == 0,
                    "the entry at " + entry + " reaches " + to);
        }
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

    /** The file's parts past the tags: the core, the subtune table, the
     *  bound tunes and the workspace, each held to its place. */
    private static void assertCombined(byte[] sndh, List<byte[]> files, Tags tags)
            throws IOException {
        byte[] core = Binaries.core();
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
        int state = 0;
        int next = tableAt + 2 + 4 * files.size();
        for (int i = 0; i < files.size(); i++) {
            int at = Tune.getLong(sndh, header + tableAt + 2 + 4 * i);
            assertEquals(0, at & 1, "subtune " + (i + 1) + " on an even address");
            assertEquals(next, at, "subtune " + (i + 1) + " follows what stands before it");
            byte[] bound = Bound.of(files.get(i));
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
                List.of("Chambers", "Circus")));
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
        assertCombined(sndh, files, tags);
    }

    @Test
    void aFileWithoutNamesOrComposerHasNeitherTag() throws IOException {
        List<byte[]> files = List.of(tune("plays-once"));
        byte[] sndh = Sndh.of(files, new Sndh.Options("Once", null, null));
        Tags tags = tags(sndh);
        assertEquals(List.of("TITL", "CONV", "##", "TC", "FLAG", "FRMS", "HDNS"), tags.order());
        assertEquals(1, tags.subtunes());
        assertCombined(sndh, files, tags);
    }

    @Test
    void theFramesTagGivesTheRowsOfATuneThatPlaysOnce() throws IOException {
        byte[] file = tune("plays-once");
        org.dtx.Table table = TuneFile.read(file).table();
        assertEquals(table.rows(), table.repeat(), "plays-once has RR at R");
        Tags tags = tags(Sndh.of(List.of(file), new Sndh.Options("Once", null, null)));
        assertArrayEquals(new int[] {table.rows()}, tags.frames());
        assertEquals(4, tags.frames()[0]);
    }

    @Test
    void theFlagTagListsTheTimersTheSetClaims() throws IOException {
        Tags tags = tags(Sndh.of(List.of(tune("four-timers"), tune("four-timers")),
                new Sndh.Options("Four", null, null)));
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
                () -> Sndh.of(files, new Sndh.Options("Mixed", null, null)));
        assertTrue(said(wrong).contains("50 Hz") && said(wrong).contains("60"), said(wrong));
    }

    @Test
    void moreSubtunesThanTheCountHoldsAreRejected() throws IOException {
        List<byte[]> files = Collections.nCopies(Sndh.MAX_SUBTUNES + 1, tune("circus"));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(files, new Sndh.Options("Many", null, null)));
        assertTrue(said(wrong).contains("99"), said(wrong));
        assertEquals(99, tags(Sndh.of(files.subList(0, Sndh.MAX_SUBTUNES),
                new Sndh.Options("Many", null, null))).subtunes());
    }

    @Test
    void aTuneOfAnotherVersionIsRejected() throws IOException {
        List<byte[]> files = List.of(tune("chambers"), tune("wrong-version"));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Sndh.of(files, new Sndh.Options("Wrong", null, null)));
        assertEquals("subtune 2: version 3 is not 2", wrong.getMessage());
    }

    @Test
    void aProgramIsTheHeaderTheStubTheFileAndAZeroLong() throws IOException {
        List<byte[]> files = List.of(tune("chambers"), tune("circus"));
        byte[] sndh = Sndh.of(files, new Sndh.Options("Two of the kit", null, null));
        byte[] stub = Binaries.stub();
        byte[] prg = Prg.of(sndh, false, 0);
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
                "no Timer C claimed: the stub takes the screen's rate");
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

        byte[] painted = Prg.of(sndh, true, 2000);
        assertEquals(Prg.FLAG_PAINT, Tune.getWord(painted, 28 + Prg.STUB_FLAGS_AT));
        assertEquals(2000, Tune.getLong(painted, 28 + Prg.STUB_ROWS_AT));
    }

    @Test
    void aSetClaimingTimerCAtAnotherRateMakesNoProgram() throws IOException {
        byte[] sndh = Sndh.of(List.of(tune("four-timers")), new Sndh.Options("Four", null, null));
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Prg.of(sndh, false, 0));
        assertEquals("the set claims Timer C and plays at 60 Hz: the stub then plays from the"
                + " VBL, a 50 Hz clock, so this set needs a host of its own", wrong.getMessage());
    }

    @Test
    void aSetClaimingTimerCAtFiftyHertzPlaysFromTheVbl() throws IOException {
        byte[] file = tune("four-timers").clone();
        Tune.putWord(file, Tune.FRAME_RATE_AT, 50);
        byte[] sndh = Sndh.of(List.of(file), new Sndh.Options("Four at fifty", null, null));
        assertEquals("abcdy", tags(sndh).flag());
        byte[] prg = Prg.of(sndh, false, 0);
        assertEquals(Prg.FLAG_VBL, Tune.getWord(prg, 28 + Prg.STUB_FLAGS_AT), "Timer C claimed");
        assertEquals(50, Tune.getWord(prg, 28 + Prg.STUB_RATE_AT));
    }

    @Test
    void aSetWithoutTimerCLeavesBitOneClear() throws IOException {
        byte[] file = tune("chambers").clone();
        Tune.putWord(file, Tune.FRAME_RATE_AT, 60);
        byte[] prg = Prg.of(Sndh.of(List.of(file), new Sndh.Options("Sixty", null, null)),
                false, 0);
        assertEquals(0, Tune.getWord(prg, 28 + Prg.STUB_FLAGS_AT), "neither flag");
        assertEquals(60, Tune.getWord(prg, 28 + Prg.STUB_RATE_AT));
    }

    @Test
    void aTitleThatReadsLikeATagPatchesNothing() throws IOException {
        byte[] sndh = Sndh.of(List.of(tune("chambers")),
                new Sndh.Options("TC##HDNS FLAG~c", "##99", null));
        Tags tags = tags(sndh);
        assertEquals("TC##HDNS FLAG~c", tags.text().get("TITL"));
        assertEquals("##99", tags.text().get("COMM"));
        byte[] prg = Prg.of(sndh, false, 0);
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
                    () -> Prg.of(file, false, 0));
            assertTrue(said(wrong).contains("no SNDH at 12"), said(wrong));
        }
        byte[] sndh = Sndh.of(List.of(tune("circus")), new Sndh.Options("Cut", null, null));
        byte[] cut = Arrays.copyOf(sndh, tags(sndh).end() - 4);
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class,
                () -> Prg.of(cut, false, 0));
        assertTrue(said(wrong).contains("no HDNS"), said(wrong));
    }
}
