package org.ymxr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.dtx.Table;
import org.jspecify.annotations.Nullable;

/**
 * An SNDH file from tune files (doc/BINARIES.md 3): the entry triple, the
 * tag block, the core with its two offsets patched, the subtune table,
 * each tune bound ({@link Bound}) on an even address, and the workspace,
 * two bytes more than the state needs, since init rounds its address up
 * to a long. Any SNDH host plays it, and {@link Prg} puts a program
 * around it.
 *
 * <p>The core's descriptor, from the core's first byte:
 *
 * <pre>
 *  offset  bytes  gives
 *  0       12     three bra.w, to init, exit and play
 *  12      4      YMXS
 *  16      2      the descriptor's version, 1
 *  18      2      the bound tune's version the core reads
 *  20      2      YMXR_FIXED, the workspace's bytes before the state block
 *  22      2      flags: bit 0 where the raster monitor is assembled in
 *  24      2      where the core's state byte is
 *  26      2      zero
 *  28      4      the subtune table's offset, patched here
 *  32      4      the workspace's offset, patched here
 * </pre>
 */
final class Sndh {

    /** What the '##' tag's two digits hold. */
    static final int MAX_SUBTUNES = 99;

    static final byte[] CORE_MAGIC = {'Y', 'M', 'X', 'S'};
    static final int CORE_MAGIC_AT = 12;
    static final int CORE_VERSION = 1;
    static final int CORE_VERSION_AT = 16;
    static final int CORE_READS_AT = 18;
    static final int CORE_FIXED_AT = 20;
    static final int CORE_FLAGS_AT = 22;
    static final int CORE_STATE_AT = 24;
    static final int CORE_TABLE_AT = 28;
    static final int CORE_WORK_AT = 32;
    static final int CORE_DESCRIPTOR = 36;

    /** The core's flag bit 0: the player's raster monitor assembled in
     *  (doc/performance.md). */
    static final int CORE_MONITOR = 1;

    /** The core's flag bit 1: a tick neither drops the interrupt level
     *  nor writes its own end of interrupt (the player's YMXR_NEST=0 and
     *  YMXR_AEOI=1, doc/performance.md). */
    static final int CORE_LEAN = 2;

    /** The word of a bra.w, before its displacement. */
    static final int BRA_W = 0x6000;

    /** The workspace's bytes past what the state needs: the player takes
     *  its workspace on a long, an SNDH host loads the file on an even
     *  address, and init rounds the workspace's address up to a long. */
    static final int WORK_ROUNDING = 2;

    /** The CONV tag's text: the player, and the converter that writes its
     *  tune files. */
    static final String CONVERTER = "YMXR (ym-to-ymxr)";

    /** The timer each of the four effects runs, a bit a timer, A to D:
     *  effects 0 to 3 run Timers A, D, B and C. */
    private static final int[] TIMER_OF_EFFECT = {0, 3, 1, 2};

    /** The tag block's text: the title, the composer where there is one,
     *  and a name a subtune where names are given; and the core the file
     *  takes, the one with the raster monitor in where {@code monitor} is
     *  set. */
    record Options(String title, @Nullable String composer, @Nullable List<String> names,
            boolean monitor, boolean lean) {
    }

    private Sndh() {
    }

    /**
     * The file, from the tune files as subtunes 1 up, around the core
     * carried: the one with the raster monitor in where the options ask
     * for it, the plain one where not.
     *
     * @throws IllegalArgumentException where a tune file is not one this
     *     reads, the bound tunes are not of the version the core reads,
     *     two tunes' rates differ, or there are more tunes than '##'
     *     holds
     */
    static byte[] of(List<byte[]> tuneFiles, Options options) {
        return of(options.monitor() ? Binaries.monitorCore()
                : options.lean() ? Binaries.leanCore() : Binaries.core(), tuneFiles, options);
    }

    /** The same, around the core given. */
    static byte[] of(byte[] core, List<byte[]> tuneFiles, Options options) {
        checkCore(core, options.monitor(), options.lean());
        int n = tuneFiles.size();
        if (n == 0) {
            throw new IllegalArgumentException("no tune files: an SNDH file holds one subtune"
                    + " at least");
        }
        if (n > MAX_SUBTUNES) {
            throw new IllegalArgumentException(n + " tune files: the '##' tag's two digits hold"
                    + " at most " + MAX_SUBTUNES + " subtunes");
        }
        List<String> names = options.names();
        if (names != null && names.size() != n) {
            throw new IllegalArgumentException(names.size() + " names for " + n + " subtunes");
        }
        List<byte[]> bound = new ArrayList<>();
        int[] frames = new int[n];
        int rate = 0;
        int claimed = 0;
        int state = 0;
        for (int i = 0; i < n; i++) {
            TuneFile tune;
            try {
                tune = TuneFile.read(tuneFiles.get(i));
            } catch (IllegalArgumentException wrong) {
                throw new IllegalArgumentException("subtune " + (i + 1) + ": "
                        + wrong.getMessage(), wrong);
            }
            if (i == 0) {
                rate = tune.frameRate();
            } else if (tune.frameRate() != rate) {
                throw new IllegalArgumentException("subtune " + (i + 1) + " plays at "
                        + tune.frameRate() + " Hz and subtune 1 at " + rate + ": an SNDH file"
                        + " states one rate");
            }
            Table table = tune.table();
            frames[i] = table.repeat() < table.rows() ? 0 : table.rows();
            claimed |= claims(tune.effects());
            byte[] b = Bound.of(tuneFiles.get(i));
            state = Math.max(state, Tune.getLong(b, Bound.STATE_AT));
            bound.add(b);
        }
        byte[] tags = tags(options, rate, n, frames, claimed);
        int workspace = Tune.align(Tune.getWord(core, CORE_FIXED_AT) + state) + WORK_ROUNDING;
        return combine(core, bound, tags, workspace);
    }

    /**
     * The core's descriptor held to what this writes, and its flags to
     * the core asked for: the flags word gives whether the raster
     * monitor is in, and the file the core was read from does not.
     *
     * @throws IllegalArgumentException where the core is not one, is of
     *     another descriptor version, reads bound tunes of another
     *     version than {@link Bound} writes, or has no raster monitor in
     *     where {@code monitor} asks for one
     */
    static void checkCore(byte[] core, boolean monitor, boolean lean) {
        if (core.length < CORE_DESCRIPTOR || !Arrays.equals(CORE_MAGIC,
                Arrays.copyOfRange(core, CORE_MAGIC_AT, CORE_MAGIC_AT + 4))) {
            throw new IllegalArgumentException("not an SNDH core: no YMXS at " + CORE_MAGIC_AT);
        }
        int version = Tune.getWord(core, CORE_VERSION_AT);
        if (version != CORE_VERSION) {
            throw new IllegalArgumentException("the core's descriptor is version " + version
                    + ", and this writes " + CORE_VERSION);
        }
        int reads = Tune.getWord(core, CORE_READS_AT);
        if (reads != Bound.VERSION) {
            throw new IllegalArgumentException("the core reads bound tunes of version " + reads
                    + ", and this binds at " + Bound.VERSION);
        }
        int flags = Tune.getWord(core, CORE_FLAGS_AT);
        if (monitor && (flags & CORE_MONITOR) == 0) {
            throw new IllegalArgumentException("the core's flags at " + CORE_FLAGS_AT + " read "
                    + flags + ", and the raster monitor asked for needs bit 0 set");
        }
        if (lean && (flags & CORE_LEAN) == 0) {
            throw new IllegalArgumentException("the core's flags at " + CORE_FLAGS_AT + " read "
                    + flags + ", and the lean tick asked for needs bit 1 set");
        }
    }

    /** The timers a tune's effects claim, a bit a timer, A to D. */
    static int claims(int effects) {
        int claimed = 0;
        for (int i = 0; i < TIMER_OF_EFFECT.length; i++) {
            if ((effects & 1 << i) != 0) {
                claimed |= 1 << TIMER_OF_EFFECT[i];
            }
        }
        return claimed;
    }

    /** The FLAG tag's text: '~', a letter for each timer claimed, a to d,
     *  and y for the YM2149. */
    static String flag(int claimed) {
        StringBuilder text = new StringBuilder("~");
        for (int timer = 0; timer < 4; timer++) {
            if ((claimed & 1 << timer) != 0) {
                text.append((char) ('a' + timer));
            }
        }
        return text.append('y').toString();
    }

    /**
     * The tag block, 'SNDH' through 'HDNS': TITL, COMM where there is a
     * composer, CONV, '##' and two digits, TC and the rate, FLAG, each
     * text ended by a zero byte, a pad to an even length, FRMS with a long
     * a subtune, '!#SN' where names are given with a word a subtune, the
     * name's offset from the tag's first byte, then the names each ended
     * by a zero byte, a pad to an even length, and HDNS. The '##' count
     * stands before FRMS and the names, since a reader sizes both by it.
     */
    static byte[] tags(Options options, int rate, int n, int[] frames, int claimed) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        text(out, "SNDH");
        tag(out, "TITL", clean(options.title()));
        String composer = options.composer();
        if (composer != null && !composer.isEmpty()) {
            tag(out, "COMM", clean(composer));
        }
        tag(out, "CONV", CONVERTER);
        tag(out, String.format(Locale.ROOT, "##%02d", n), "");
        tag(out, "TC" + rate, "");
        tag(out, "FLAG", flag(claimed));
        pad(out);
        text(out, "FRMS");
        for (int f : frames) {
            out.write(f >>> 24);
            out.write(f >>> 16);
            out.write(f >>> 8);
            out.write(f);
        }
        List<String> names = options.names();
        if (names != null) {
            text(out, "!#SN");
            int at = 4 + 2 * n;
            for (String name : names) {
                out.write(at >> 8);
                out.write(at);
                at += clean(name).length() + 1;
            }
            for (String name : names) {
                text(out, clean(name));
                out.write(0);
            }
        }
        pad(out);
        text(out, "HDNS");
        return out.toByteArray();
    }

    /**
     * The file: the entry triple, the tag block padded even, the core with
     * its offsets patched, the subtune table, the bound tunes each on an
     * even address, and {@code workspace} zero bytes. Each entry is a
     * bra.w to the same entry of the core's own triple, so all three
     * displacements are the header's bytes less 2.
     */
    static byte[] combine(byte[] core, List<byte[]> tunes, byte[] tags, int workspace) {
        int header = even(12 + tags.length);
        if (header - 2 > Short.MAX_VALUE) {
            throw new IllegalArgumentException("the tag block is " + tags.length
                    + " bytes, and a bra.w reaches " + Short.MAX_VALUE);
        }
        int n = tunes.size();
        int tableAt = even(core.length);
        int at = tableAt + 2 + 4 * n;
        int[] offsets = new int[n];
        for (int i = 0; i < n; i++) {
            offsets[i] = at;
            at = even(at + tunes.get(i).length);
        }
        int workAt = at;
        byte[] file = new byte[header + workAt + workspace];
        for (int entry = 0; entry < 12; entry += 4) {
            Tune.putWord(file, entry, BRA_W);
            Tune.putWord(file, entry + 2, header - 2);
        }
        System.arraycopy(tags, 0, file, 12, tags.length);
        System.arraycopy(core, 0, file, header, core.length);
        Tune.putLong(file, header + CORE_TABLE_AT, tableAt);
        Tune.putLong(file, header + CORE_WORK_AT, workAt);
        Tune.putWord(file, header + tableAt, n);
        for (int i = 0; i < n; i++) {
            Tune.putLong(file, header + tableAt + 2 + 4 * i, offsets[i]);
            System.arraycopy(tunes.get(i), 0, file, header + offsets[i], tunes.get(i).length);
        }
        return file;
    }

    static int even(int at) {
        return at + (at & 1);
    }

    /** One text tag: the name, the text, a zero byte. */
    private static void tag(ByteArrayOutputStream out, String name, String text) {
        text(out, name);
        text(out, text);
        out.write(0);
    }

    private static void text(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    /** A zero byte where the block's length is odd. */
    private static void pad(ByteArrayOutputStream out) {
        if ((out.size() & 1) != 0) {
            out.write(0);
        }
    }

    /** The printable ASCII of a text: a title comes out of a dump's
     *  header, which holds any bytes. */
    static String clean(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 0x20 && c < 0x7F) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * {@code ymxr-sndh in.ymxr... out.sndh [-tTITLE] [-cCOMPOSER]
     * [-nNAME]... [-perf]}: the SNDH file of the tune files, as subtunes
     * in the order named. The title is the output's stem unless one is
     * given. Where any name is given, each tune past the names given is
     * named by its file's stem; where none is, the file has no names.
     * {@code -lean} puts the core whose ticks neither drop the interrupt
     * level nor write their own end of interrupt under the tunes, and
     * {@code -perf} puts the core with the raster monitor in under the
     * entries, for reading a run.
     */
    public static void main(String[] args) throws IOException {
        @Nullable String title = null;
        @Nullable String composer = null;
        boolean monitor = false;
        boolean lean = false;
        List<String> names = new ArrayList<>();
        List<String> files = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("-perf")) {
                monitor = true;
            } else if (arg.equals("-lean")) {
                lean = true;
            } else if (arg.startsWith("-t")) {
                title = arg.substring(2);
            } else if (arg.startsWith("-c")) {
                composer = arg.substring(2);
            } else if (arg.startsWith("-n")) {
                names.add(arg.substring(2));
            } else if (arg.startsWith("-")) {
                usage();
                return;
            } else {
                files.add(arg);
            }
        }
        if (files.size() < 2) {
            usage();
            return;
        }
        String out = files.remove(files.size() - 1);
        if (names.size() > files.size()) {
            System.err.println("ymxr-sndh: " + names.size() + " names for " + files.size()
                    + " tune files");
            System.exit(1);
            return;
        }
        for (int i = names.size(); !names.isEmpty() && i < files.size(); i++) {
            names.add(stem(files.get(i)));
        }
        List<byte[]> tunes = new ArrayList<>();
        for (String file : files) {
            tunes.add(Files.readAllBytes(Path.of(file)));
        }
        byte[] sndh;
        try {
            sndh = of(tunes, new Options(title == null ? stem(out) : title, composer,
                    names.isEmpty() ? null : names, monitor, lean));
        } catch (IllegalArgumentException wrong) {
            System.err.println("ymxr-sndh: " + wrong.getMessage());
            System.exit(1);
            return;
        }
        Files.write(Path.of(out), sndh);
        System.out.println(out + ": " + sndh.length + " bytes, " + files.size()
                + (files.size() == 1 ? " subtune" : " subtunes"));
    }

    /** A file's name up to its last dot. */
    static String stem(String file) {
        String name = Path.of(file).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static void usage() {
        System.err.println("ymxr-sndh in.ymxr... out.sndh [-tTITLE] [-cCOMPOSER] [-nNAME]..."
                + " [-perf]");
        System.err.println("  -perf  the core with the raster monitor in, for reading a run");
        System.exit(2);
    }
}
