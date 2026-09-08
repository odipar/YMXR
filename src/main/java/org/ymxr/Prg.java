package org.ymxr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * A TOS program around an SNDH file (doc/BINARIES.md 4): the PRG header,
 * the stub with its descriptor patched, the SNDH file as {@link Sndh}
 * writes it, and the relocation table, one zero long.
 *
 * <p>The stub's descriptor, from the stub's first byte:
 *
 * <pre>
 *  offset  bytes  gives
 *  0       4      bra.w to the program
 *  4       4      YMXT
 *  8       2      the descriptor's version, 1
 *  10      2      the subtunes, patched here from the '##' tag
 *  12      2      flags, patched here
 *  14      2      the rate, rows a second, patched here from the TC tag
 *  16      4      the rows to play, patched here; 0 plays as many as the tune gives
 *  20      4      the core's offset from the SNDH file's first byte, patched here
 * </pre>
 */
final class Prg {

    static final byte[] STUB_MAGIC = {'Y', 'M', 'X', 'T'};
    static final int STUB_MAGIC_AT = 4;
    static final int STUB_VERSION = 1;
    static final int STUB_VERSION_AT = 8;
    static final int STUB_SUBTUNES_AT = 10;
    static final int STUB_FLAGS_AT = 12;
    static final int STUB_RATE_AT = 14;
    static final int STUB_ROWS_AT = 16;
    static final int STUB_CORE_AT = 20;
    static final int STUB_DESCRIPTOR = 24;

    /** Flag bit 0: the screen cleared before the banner. Set where the
     *  SNDH file's core has the raster monitor in, so that the monitor's
     *  bars stand where the desktop's pixels were. It follows the core,
     *  and a caller does not choose it. */
    static final int FLAG_CLEAR = 1;

    /** Flag bit 1: play from the VBL, a 50 Hz clock. Set where the set
     *  claims Timer C, since the stub then has no timer to play from;
     *  such a set is at 50 Hz, or there is no program. Clear, the stub
     *  reads the screen's rate, and plays from the VBL where that equals
     *  the descriptor's rate and from Timer C where not. */
    static final int FLAG_VBL = 2;

    /** The PRG header's bytes, and its magic. */
    static final int HEADER = 28;
    static final int PRG_MAGIC = 0x601A;

    /** Where the tag block begins in an SNDH file, past the entry triple:
     *  its SNDH, then the tags. */
    private static final int TAGS_AT = 12;

    /** What the tag block gives the stub: the '##' count, the TC rate, the
     *  FLAG letters after its '~', and where HDNS stands. */
    record Tags(int subtunes, int rate, String flag, int end) {
    }

    private Prg() {
    }

    /**
     * The program around an SNDH file, from the stub carried.
     *
     * @param rows the rows to play, 0 for as many as the tune gives
     * @throws IllegalArgumentException where the file is not an SNDH file
     *     around this player's core, or the set claims Timer C at a rate
     *     other than 50
     */
    static byte[] of(byte[] sndh, long rows) {
        return of(Binaries.stub(), sndh, rows);
    }

    /** The same, from the stub given. */
    static byte[] of(byte[] stub, byte[] sndh, long rows) {
        checkStub(stub);
        if (rows < 0 || rows > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("rows " + rows + " does not fit a long");
        }
        Tags tags = tags(sndh);
        boolean timerC = tags.flag().indexOf('c') >= 0;
        if (timerC && tags.rate() != 50) {
            throw new IllegalArgumentException("the set claims Timer C and plays at " + tags.rate()
                    + " Hz: the stub then plays from the VBL, a 50 Hz clock, so this set needs"
                    + " a host of its own");
        }
        int core = core(sndh, tags.end() + 4);
        boolean monitor = (Tune.getWord(sndh, core + Sndh.CORE_FLAGS_AT)
                & Sndh.CORE_MONITOR) != 0;
        byte[] prg = new byte[HEADER + stub.length + sndh.length + 4];
        Tune.putWord(prg, 0, PRG_MAGIC);
        Tune.putLong(prg, 2, stub.length + sndh.length);
        System.arraycopy(stub, 0, prg, HEADER, stub.length);
        Tune.putWord(prg, HEADER + STUB_SUBTUNES_AT, tags.subtunes());
        Tune.putWord(prg, HEADER + STUB_FLAGS_AT, (monitor ? FLAG_CLEAR : 0)
                | (timerC ? FLAG_VBL : 0));
        Tune.putWord(prg, HEADER + STUB_RATE_AT, tags.rate());
        Tune.putLong(prg, HEADER + STUB_ROWS_AT, (int) rows);
        Tune.putLong(prg, HEADER + STUB_CORE_AT, core);
        System.arraycopy(sndh, 0, prg, HEADER + stub.length, sndh.length);
        return prg;
    }

    /** What the program was made of: the file under it, the stub's own
     *  bytes, and what the stub was patched with. */
    private static void made(Report report, byte[] sndh, byte[] prg, long rows) {
        if (!report.says()) {
            return;
        }
        Tags tags = tags(sndh);
        int flags = Tune.getWord(prg, HEADER + STUB_FLAGS_AT);
        report.say("the SNDH file: " + sndh.length + " bytes, " + tags.subtunes()
                + (tags.subtunes() == 1 ? " subtune at " : " subtunes at ") + tags.rate()
                + " Hz, FLAG " + tags.flag());
        report.say("the stub: " + Binaries.stub().length + " bytes, patched");
        report.row("the subtunes", String.valueOf(tags.subtunes()));
        report.row("the rows to play", rows == 0 ? "0, as many as the tune gives"
                : String.valueOf(rows));
        report.row("it plays from", (flags & FLAG_VBL) != 0 ? "the VBL, the set claims Timer C"
                : "the VBL where the screen's rate is the tune's, and Timer C where it is not");
        report.row("the screen", (flags & FLAG_CLEAR) != 0
                ? "cleared, the core has the raster monitor in"
                : "left as the desktop drew it");
        report.say("the program: " + prg.length + " bytes");
    }

    /**
     * The stub's descriptor held to what this patches.
     *
     * @throws IllegalArgumentException where the stub is not one, is of
     *     another descriptor version, or is odd-sized, since the SNDH
     *     file after it would then load on an odd address
     */
    static void checkStub(byte[] stub) {
        if (stub.length < STUB_DESCRIPTOR || !Arrays.equals(STUB_MAGIC,
                Arrays.copyOfRange(stub, STUB_MAGIC_AT, STUB_MAGIC_AT + 4))) {
            throw new IllegalArgumentException("not a program stub: no YMXT at " + STUB_MAGIC_AT);
        }
        int version = Tune.getWord(stub, STUB_VERSION_AT);
        if (version != STUB_VERSION) {
            throw new IllegalArgumentException("the stub's descriptor is version " + version
                    + ", and this writes " + STUB_VERSION);
        }
        if ((stub.length & 1) != 0) {
            throw new IllegalArgumentException("the stub is " + stub.length + " bytes, odd:"
                    + " the SNDH file after it would load on an odd address");
        }
    }

    /**
     * The tag block walked from its first tag to HDNS, as {@link Sndh}
     * writes it. A zero byte where a tag name would begin is a pad, one
     * byte. '##' is four bytes, its two digits the subtunes; TC and each
     * text tag, TITL, COMM, CONV and FLAG, run to their zero byte and one
     * past; FRMS is 4 + 4 bytes a subtune, and '!#SN' 4 + 2 bytes a
     * subtune, then a name a subtune, each to its zero byte and one past.
     * The subtunes, the rate and the FLAG letters come from those tags
     * alone, so a title or a composer that reads like a tag patches
     * nothing.
     *
     * @throws IllegalArgumentException where the file has no SNDH at 12,
     *     no HDNS ends its tags, a tag is not one {@link Sndh} writes,
     *     FRMS or '!#SN' stands before '##', or '##' or TC is missing
     */
    static Tags tags(byte[] sndh) {
        if (sndh.length < TAGS_AT + 4 || !ascii(sndh, TAGS_AT, 4).equals("SNDH")) {
            throw new IllegalArgumentException("not an SNDH file: no SNDH at " + TAGS_AT);
        }
        int subtunes = -1;
        int rate = -1;
        String flag = "";
        int at = TAGS_AT + 4;
        while (true) {
            if (at < sndh.length && sndh[at] == 0) {
                at++;
                continue;
            }
            String name = name(sndh, at);
            if (name.equals("HDNS")) {
                break;
            }
            if (name.startsWith("##")) {
                if (!digit(sndh[at + 2]) || !digit(sndh[at + 3])) {
                    throw new IllegalArgumentException("the SNDH file's tags give no '##'"
                            + " subtune count");
                }
                subtunes = (sndh[at + 2] - '0') * 10 + sndh[at + 3] - '0';
                at += 4;
            } else if (name.startsWith("TC")) {
                int to = zero(sndh, at + 2);
                rate = 0;
                for (int i = at + 2; i < to && digit(sndh[i]); i++) {
                    rate = rate * 10 + sndh[i] - '0';
                }
                if (rate == 0) {
                    throw new IllegalArgumentException("the SNDH file's tags give no TC rate");
                }
                at = to + 1;
            } else if (name.equals("FRMS")) {
                at += 4 + 4 * sized(subtunes, name, at);
            } else if (name.equals("!#SN")) {
                int names = sized(subtunes, name, at);
                at += 4 + 2 * names;
                for (int i = 0; i < names; i++) {
                    at = zero(sndh, at) + 1;
                }
            } else if (name.equals("TITL") || name.equals("COMM") || name.equals("CONV")
                    || name.equals("FLAG")) {
                int to = zero(sndh, at + 4);
                if (name.equals("FLAG")) {
                    String text = ascii(sndh, at + 4, to - at - 4);
                    flag = text.substring(text.indexOf('~') + 1);
                }
                at = to + 1;
            } else {
                throw new IllegalArgumentException("the SNDH file's tag " + name + " at " + at
                        + " is not one this reads");
            }
        }
        if (subtunes < 0) {
            throw new IllegalArgumentException("the SNDH file's tags give no '##' subtune count");
        }
        if (rate < 0) {
            throw new IllegalArgumentException("the SNDH file's tags give no TC rate");
        }
        return new Tags(subtunes, rate, flag, at);
    }

    /** A tag's four-byte name at {@code at}. */
    private static String name(byte[] sndh, int at) {
        if (at + 4 > sndh.length) {
            throw noEnd();
        }
        return ascii(sndh, at, 4);
    }

    /** Where the next zero byte from {@code from} stands. */
    private static int zero(byte[] sndh, int from) {
        for (int at = from; at < sndh.length; at++) {
            if (sndh[at] == 0) {
                return at;
            }
        }
        throw noEnd();
    }

    /** The subtunes a tag sized by '##' runs over: '##' stands before it. */
    private static int sized(int subtunes, String name, int at) {
        if (subtunes < 0) {
            throw new IllegalArgumentException("the SNDH file's " + name + " tag at " + at
                    + " stands before the '##' count that sizes it");
        }
        return subtunes;
    }

    private static IllegalArgumentException noEnd() {
        return new IllegalArgumentException("not an SNDH file: no HDNS ends its tags");
    }

    /**
     * Where the core begins: its YMXS, past the tags, less the magic's
     * offset. The entry triple's first bra.w reaches the core's first
     * byte, or the file is not one {@link Sndh} wrote, and the core's
     * descriptor stands whole in the file, since this reads its flags.
     */
    static int core(byte[] sndh, int from) {
        int at = find(sndh, new String(Sndh.CORE_MAGIC, StandardCharsets.ISO_8859_1), from,
                sndh.length);
        if (at < 0) {
            throw new IllegalArgumentException("the SNDH file holds no core: no YMXS past"
                    + " its tags");
        }
        int core = at - Sndh.CORE_MAGIC_AT;
        int reached = Tune.getWord(sndh, 0) == Sndh.BRA_W
                ? 2 + (short) Tune.getWord(sndh, 2) : -1;
        if (core < from || reached != core) {
            throw new IllegalArgumentException("the core begins at " + core + ", and the"
                    + " entry triple reaches " + reached);
        }
        if (core + Sndh.CORE_DESCRIPTOR > sndh.length) {
            throw new IllegalArgumentException("the core begins at " + core + " and the file ends "
                    + (sndh.length - core) + " bytes on, short of the core's descriptor, "
                    + Sndh.CORE_DESCRIPTOR + " bytes");
        }
        return core;
    }

    private static boolean digit(byte b) {
        return b >= '0' && b <= '9';
    }

    private static String ascii(byte[] bytes, int at, int length) {
        return new String(bytes, at, length, StandardCharsets.ISO_8859_1);
    }

    /** Where a text first stands in {@code [from, end)}, or -1. */
    static int find(byte[] bytes, String text, int from, int end) {
        byte[] wanted = text.getBytes(StandardCharsets.ISO_8859_1);
        for (int at = from; at + wanted.length <= end; at++) {
            if (Arrays.equals(wanted, 0, wanted.length, bytes, at, at + wanted.length)) {
                return at;
            }
        }
        return -1;
    }

    /**
     * {@code ymxr-prg in.sndh out.prg [-rROWS]}: the program around an
     * SNDH file, playing {@code ROWS} rows, as many as the tune gives
     * without. The stub's flag bit 0 follows the file's core: the screen
     * is cleared where that core has the raster monitor in.
     */
    public static void main(String[] args) throws IOException {
        long rows = 0;
        boolean silent = false;
        String in = null;
        String out = null;
        for (String arg : args) {
            if (arg.startsWith("-r") && arg.substring(2).matches("[0-9]+")) {
                rows = Long.parseLong(arg.substring(2));
            } else if (arg.equals(YmToYmxr.SILENT)) {
                silent = true;
            } else if (arg.startsWith("-") || out != null) {
                usage();
                return;
            } else if (in == null) {
                in = arg;
            } else {
                out = arg;
            }
        }
        if (in == null || out == null) {
            usage();
            return;
        }
        Report report = new Report(!silent);
        byte[] sndh = Files.readAllBytes(Path.of(in));
        byte[] prg;
        try {
            prg = of(sndh, rows);
        } catch (IllegalArgumentException wrong) {
            System.err.println("ymxr-prg: " + wrong.getMessage());
            System.exit(1);
            return;
        }
        Files.write(Path.of(out), prg);
        made(report, sndh, prg, rows);
        System.out.println(out + ": " + prg.length + " bytes");
    }

    private static void usage() {
        System.err.println("ymxr-prg in.sndh out.prg [-rROWS] [-silent]");
        System.exit(2);
    }
}
