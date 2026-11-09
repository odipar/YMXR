package org.ymxr;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.dtx.Table;
import org.jspecify.annotations.Nullable;
import org.ymxs.tool.Tool;

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
 *  offset  bytes  what it is
 *  0       12     three bra.w, to init, exit and play
 *  12      4      YMXS
 *  16      2      the descriptor's version, 1
 *  18      2      the bound tune's version the core reads
 *  20      2      YMXR_FIXED, the workspace's bytes before the state block
 *  22      2      flags: bits 0 to 4, the five switches below
 *  24      2      where the core's state byte is
 *  26      2      where the handlers begin, the reach measured from there
 *  28      4      the subtune table's offset, patched here
 *  32      4      the workspace's offset, patched here
 *  36      4      with the lookup in, the page area's offset, patched here
 * </pre>
 */
final class Sndh {

    /** The most the '##' tag's two digits reach. */
    static final int MAX_SUBTUNES = 99;

    /** The most seconds a TIME word reaches. */
    static final int MAX_SECONDS = 65535;

    static final byte[] CORE_MAGIC = {'Y', 'M', 'X', 'S'};
    static final int CORE_MAGIC_AT = 12;
    static final int CORE_VERSION = 1;
    static final int CORE_VERSION_AT = 16;
    static final int CORE_READS_AT = 18;
    static final int CORE_FIXED_AT = 20;
    static final int CORE_FLAGS_AT = 22;
    static final int CORE_STATE_AT = 24;
    static final int CORE_HANDLERS_AT = 26;
    static final int CORE_TABLE_AT = 28;
    static final int CORE_WORK_AT = 32;
    static final int CORE_DESCRIPTOR = 36;

    /** The page area's offset in a core with the lookup in (BINARIES.md
     *  2.2, 3.1), 0 where the file has none. */
    static final int CORE_PAGES_AT = 36;

    /** The page area's bytes for a list of P pages besides its tables:
     *  256 bytes a page, and the 255 bytes over which each effect's pages
     *  begin at its offset modulo 256 (BINARIES.md 3.1). */
    static final int PAGE_BYTES = 256;
    static final int PAGE_SPAN = 255;

    /** What a page read reaches: its displacement is a signed word from
     *  the instruction that reads it (68k/player/lookup_ticks.S, LKEY). */
    static final int PAGE_REACH = 32767;

    /** The core's flag bit 0: the player's raster monitor assembled in
     *  (doc/performance.md). */
    static final int CORE_MONITOR = 1;

    /** The core's flag bit 1: a tick neither drops the interrupt level
     *  nor writes an end of interrupt (the player's YMXR_NEST=0 and
     *  YMXR_AEOI=1, doc/performance.md). */
    static final int CORE_LEAN = 2;

    /** The core's flag bit 2: a tick reads its row through a displacement
     *  from the instruction that reads it (the player's YMXR_PCREL=1,
     *  doc/performance.md), which reaches 32,767 bytes, so the file's
     *  tunes are within that of the core (BINARIES.md 5.5). */
    static final int CORE_PCREL = 4;

    /** The core's flag bit 3: the deferred start assembled in (the
     *  player's YMXR_DEFER=1, doc/performance.md), which a tune whose
     *  effects byte sets bit 4 requires (SPEC.md 3.3). */
    static final int CORE_DEFER = 8;

    /**
     * core flag bit 4, transformation support (YMXR_MAP=1). Effects
     * byte bit 5 requires this core, which reads bound versions 7 to 10
     * (SPEC.md 1.10, 3.3).
     */
    static final int CORE_MAP = 16;

    /** What a displacement reaches, the bytes from the core's first byte
     *  plus its field at 26 a bound tune of a file built on the core above
     *  ends within. */
    static final int PCREL_REACH = 32767;

    /** The word of a bra.w, before its displacement. */
    static final int BRA_W = 0x6000;

    /** The workspace's bytes past what the state needs: the player uses
     *  its workspace on a long, an SNDH host loads the file on an even
     *  address, and init rounds the workspace's address up to a long. */
    static final int WORK_ROUNDING = 2;

    /** The CONV tag's text where the caller names no converter: the
     *  player, and the converter that writes its tune files. */
    static final String CONVERTER = "YMXR (ym-to-ymxr)";

    /** The timer each of the four effects runs, a bit a timer, A to D:
     *  effects 0 to 3 run Timers A, D, B and C. */
    private static final int[] TIMER_OF_EFFECT = {0, 3, 1, 2};

    /** Timer C's bit in a claims byte, the third of A to D. */
    static final int TIMER_C = 1 << 2;

    /** The clock tag's two names: Timer C, and the VBL. */
    static final String TIMER_C_CLOCK = "TC";
    static final String VBL_CLOCK = "!V";

    /**
     * Which ticks the file's core reads a row with (BINARIES.md 2.1).
     * A tool writes {@code CHOSEN} unasked: it puts the core that reads
     * a row through the program counter under a file whose tunes end
     * within the reach (5.5), and the one that reads an absolute address
     * under a file whose tunes end further off. {@code PCREL} reports
     * such a file rather than putting the other core under it, and
     * {@code ABSOLUTE} writes the core that reads an address at any
     * length of file.
     */
    enum Ticks { CHOSEN, PCREL, ABSOLUTE }

    /** The clock the caller asks the tag block to name: the one the set
     *  and the core select, the VBL, or Timer C (tools.md 12.1). */
    enum Asked { CHOSEN, VBL, TIMER_C }

    /** The tag block's text: the title, the composer where there is one,
     *  and a name a subtune where the caller names them; and the core the
     *  file uses, which {@code monitor} and {@code lean} select a switch
     *  of and {@code ticks} the third; and with {@code link}, the core
     *  linked for the set, its blocks the subtunes need (doc/LINKER.md);
     *  and the converter, {@link #CONVERTER} where it is null or blank. */
    record Options(String title, @Nullable String composer, @Nullable List<String> names,
            boolean monitor, boolean lean, Ticks ticks, Asked asked, boolean link,
            @Nullable String converter) {

        /** The CONV tag's text, {@link #CONVERTER} by default. */
        Options(String title, @Nullable String composer, @Nullable List<String> names,
                boolean monitor, boolean lean, Ticks ticks, Asked asked, boolean link) {
            this(title, composer, names, monitor, lean, ticks, asked, link, null);
        }

        /** The core as carried, not linked for the set. */
        Options(String title, @Nullable String composer, @Nullable List<String> names,
                boolean monitor, boolean lean, Ticks ticks, Asked asked) {
            this(title, composer, names, monitor, lean, ticks, asked, false);
        }

        /** The two switches the tag block reads, the third chosen, and the
         *  clock tag left to the set. */
        Options(String title, @Nullable String composer, @Nullable List<String> names,
                boolean monitor, boolean lean) {
            this(title, composer, names, monitor, lean, Ticks.CHOSEN, Asked.CHOSEN);
        }

        /** The same, with the ticks named. */
        Options(String title, @Nullable String composer, @Nullable List<String> names,
                boolean monitor, boolean lean, Ticks ticks) {
            this(title, composer, names, monitor, lean, ticks, Asked.CHOSEN);
        }

        /** The CONV tag's text before cleaning: the converter named, or
         *  {@link #CONVERTER} where it is null or blank. */
        String conv() {
            String named = converter();
            return named == null || named.isBlank() ? CONVERTER : named;
        }
    }

    private Sndh() {
    }

    /**
     * Builds an SNDH file from tune files in subtune order. Options
     * select raster monitoring, lean ticks and source addressing. Any
     * subtune with deferred starts enables that support; any subtune
     * using a transformation enables lookups.
     *
     * @throws IllegalArgumentException for unreadable tune files,
     * incompatible core versions, clock conflicts or too many
     * subtunes for the '##' tag
     */
    static byte[] of(List<byte[]> tuneFiles, Options options) {
        return build(null, tuneFiles, options);
    }

    /** The same, around the core named. */
    static byte[] of(byte[] core, List<byte[]> tuneFiles, Options options) {
        return build(core, tuneFiles, options);
    }

    private static byte[] build(byte @Nullable [] given, List<byte[]> tuneFiles,
                                Options options) {
        int n = tuneFiles.size();
        if (n == 0) {
            throw new IllegalArgumentException("no tune files: an SNDH file has one subtune"
                    + " at least");
        }
        if (n > MAX_SUBTUNES) {
            throw new IllegalArgumentException(n + " tune files: the '##' tag's two digits count"
                    + " at most " + MAX_SUBTUNES + " subtunes");
        }
        List<String> names = options.names();
        if (names != null && names.size() != n) {
            throw new IllegalArgumentException(names.size() + " names for " + n + " subtunes");
        }
        int[] frames = new int[n];
        int[] rates = new int[n];
        boolean mixed = false;
        int claimed = 0;
        boolean defers = false;
        boolean maps = false;
        List<TuneFile> read = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            TuneFile tune;
            try {
                tune = TuneFile.read(tuneFiles.get(i));
            } catch (IllegalArgumentException wrong) {
                throw new IllegalArgumentException("subtune " + (i + 1) + ": "
                        + wrong.getMessage(), wrong);
            }
            rates[i] = tune.frameRate();
            if (rates[i] == 0) {
                throw new IllegalArgumentException("subtune " + (i + 1) + " plays at 0"
                        + " Hz: an SNDH file records a rate of 1 Hz or more");
            }
            mixed |= rates[i] != rates[0];
            read.add(tune);
            Table table = tune.table();
            frames[i] = table.repeat() < table.rows() ? 0 : table.rows();
            claimed |= claims(tune.effects());
            defers |= (tune.effects() & Columns.DEFERS) != 0;
            maps |= (tune.effects() & Columns.MAPS) != 0;
        }
        checkVbl(options.asked(), n, rates[0], mixed);
        if (n > 1 && (mixed || rates[0] != 50)) {
            if (options.monitor()) {
                throw new IllegalArgumentException("-perf requires every subtune in a set"
                        + " to play at 50 Hz");
            }
            if ((claimed & TIMER_C) != 0) {
                throw new IllegalArgumentException("a set with a non-50 Hz subtune needs Timer C"
                        + " for playback and a subtune claims it for an effect");
            }
        }
        // The tunes are bound as a set, so those that agree on what an
        // image fixes once share one and the reader's code appears once for
        // them (DTX abi.md 1, doc/BINARIES.md 2).
        Bound.Set set = Bound.of(tuneFiles);
        int state = 0;
        for (byte[] b : set.tunes()) {
            state = Math.max(state, Tune.getLong(b, Bound.STATE_AT));
        }
        int rate = mixed ? mixedRate(rates) : rates[0];
        byte[] tags = tags(options, rate, n, frames, claimed, mixed ? rates : null);
        // Which core is under the tunes: the one the caller named, or
        // the one the switches select, which reads a row through the
        // program counter unless the file's tunes end past the reach
        // (BINARIES.md 5.5).
        boolean pcrel = options.ticks() != Ticks.ABSOLUTE;
        byte[] core = given;
        if (core != null && options.ticks() == Ticks.CHOSEN) {
            // the caller chose by handing a core over: its flags word says
            // which ticks it has
            pcrel = (Tune.getWord(core, CORE_FLAGS_AT) & CORE_PCREL) != 0;
        }
        if (core == null) {
            core = chosen(options, pcrel, defers, maps, read);
            if (pcrel && options.ticks() == Ticks.CHOSEN
                    && tunesEnd(core, set, tags) > PCREL_REACH) {
                pcrel = false;
                core = chosen(options, false, defers, maps, read);
            }
        }
        checkCore(core, options.monitor(), options.lean(), pcrel, defers, maps,
                binds(tuneFiles));
        if (pcrel) {
            // A tick of this core reads its row through a displacement
            // from the instruction that reads it, which reaches
            // PCREL_REACH bytes, so every row of every subtune is
            // within that of the handlers (BINARIES.md 5.5).
            int last = tunesEnd(core, set, tags);
            int handlers = Tune.getWord(core, CORE_HANDLERS_AT);
            if (last > PCREL_REACH && handlers == 0) {
                throw new IllegalArgumentException("the tunes end " + last + " bytes past the"
                        + " core's first byte, and a tick that reads a row through the program"
                        + " counter reaches " + PCREL_REACH);
            }
            if (last > PCREL_REACH) {
                throw new IllegalArgumentException("the tunes end " + last + " bytes past the"
                        + " core's first handler, at " + handlers + ", and a tick that reads a"
                        + " row through the program counter reaches " + PCREL_REACH);
            }
        }
        if (area(core, set) < 0) {
            int handlers = Tune.getWord(core, CORE_HANDLERS_AT);
            int least = 0;
            for (byte[] block : set.blocks()) {
                if (block.length > 0) {
                    least = Math.max(least, Bound.least(block, fallback(core)));
                }
            }
            throw new IllegalArgumentException("the page area ends "
                    + (even(core.length) + least - handlers) + " bytes past the core's first"
                    + " handler, at " + handlers + ", and a tick's page read reaches "
                    + PAGE_REACH);
        }
        int workspace = Tune.align(Tune.getWord(core, CORE_FIXED_AT) + state) + WORK_ROUNDING;
        byte[] clock = mixed ? Binaries.carried(Binaries.CLOCK.name()) : new byte[0];
        if (mixed) {
            Tune.putWord(clock, CLOCK_RATE_AT, rate);
        }
        return combine(core, set, tags, workspace, clock);
    }

    private static final int CLOCK_MAGIC = 0x594D5843; // YMXC
    private static final int CLOCK_MAGIC_AT = 12;
    private static final int CLOCK_CORE_AT = 16;
    static final int CLOCK_RATE_AT = 18;

    static boolean mixed(byte[] file, int at) {
        return at >= 0 && at <= file.length - CLOCK_RATE_AT - 2
                && Tune.getLong(file, at + CLOCK_MAGIC_AT) == CLOCK_MAGIC;
    }

    static int coreAt(byte[] file, int at) {
        return mixed(file, at) ? at + Tune.getWord(file, at + CLOCK_CORE_AT) : at;
    }

    static void checkVbl(Asked asked, int subtunes, int rate, boolean mixed) {
        if (asked == Asked.VBL && (mixed || (subtunes > 1 && rate != 50))) {
            throw new IllegalArgumentException("-vbl requires every subtune in a set"
                    + " to play at 50 Hz");
        }
    }

    /** A common Timer C callback rate. Use an exact multiple of each
     *  subtune's rate where the timer can generate it, or count each
     *  subtune's phase against 200 or 400 callbacks a second. */
    private static int mixedRate(int[] rates) {
        int fastest = Arrays.stream(rates).max().orElseThrow();
        if (fastest > Prg.MOST_TICKS) {
            throw new IllegalArgumentException("mixed-rate subtunes need rates at or below "
                    + Prg.MOST_TICKS + " Hz");
        }
        for (int rate = fastest; rate <= Prg.MOST_TICKS; rate++) {
            int candidate = rate;
            if (Arrays.stream(rates).allMatch(one -> candidate % one == 0)
                    && Prg.timer(rate).ticks() == rate) {
                return rate;
            }
        }
        return fastest <= Prg.OS_CLOCK.ticks() ? Prg.OS_CLOCK.ticks() : Prg.MOST_TICKS;
    }

    /** The core the switches select, linked for the blocks the set needs
     *  unless the caller selects the carried core (doc/LINKER.md). */
    private static byte[] chosen(Options options, boolean pcrel, boolean defers, boolean maps,
                                 List<TuneFile> tunes) {
        Binaries.Binary binary = Binaries.binary(options.monitor(), options.lean(), pcrel,
                defers, maps);
        byte[] core = Binaries.carried(binary.name());
        if (options.link() && Link.links(defers, maps)) {
            core = Link.link(core, Pieces.carried(binary), Link.blocks(tunes));
        }
        return core;
    }

    /**
     * The page area's bytes (BINARIES.md 3.1, 5.6): for a core with the
     * lookup in, the largest area a subtune's list needs within the reach
     * of the core's first handler, from the area's offset right after the
     * core ({@link Bound#area}); -1 where a list's fallback pages do not
     * fit, and 0 for a core without the lookup.
     */
    static int area(byte[] core, Bound.Set set) {
        if (core.length < CORE_PAGES_AT + 4
                || (Tune.getWord(core, CORE_FLAGS_AT) & CORE_MAP) == 0) {
            return 0;
        }
        int reach = reach(core);
        int most = 0;
        for (byte[] block : set.blocks()) {
            if (block.length > 0) {
                int one = Bound.area(block, reach, fallback(core));
                if (one < 0) {
                    return -1;
                }
                most = Math.max(most, one);
            }
        }
        return most;
    }

    /** The bytes past the page area's offset that the core's first handler
     *  reaches (BINARIES.md 5.6). */
    static int reach(byte[] core) {
        return PAGE_REACH - (even(core.length) - Tune.getWord(core, CORE_HANDLERS_AT));
    }

    /** The fallback pages of an effect, two a bank of handlers (BINARIES.md
     *  5.6). */
    static int fallback(byte[] core) {
        return (Tune.getWord(core, CORE_FLAGS_AT) & CORE_DEFER) != 0 ? 4 : 2;
    }

    /**
     * The end of the bound tunes and their sources relative to the first
     * handler, at the core's field 26 (BINARIES.md 2.1, 3.1). Program-counter
     * displacements are measured from there. Cores without lookups have
     * field 26 set to 0, so their reach is measured from the core's
     * first byte.
     */
    static int tunesEnd(byte[] core, Bound.Set set, byte[] tags) {
        int at = even(even(core.length) + Math.max(0, area(core, set))) + 2
                + 4 * set.tunes().size();
        int end = at;
        for (int i = 0; i < set.tunes().size(); i++) {
            end = at + set.tuneBytes(i);
            at = even(end);
        }
        if (set.sources().bytes() > 0) {
            end = Tune.align(at) + set.sources().bytes();
        }
        return end - Tune.getWord(core, CORE_HANDLERS_AT);
    }

    /** The version a set of these tune files binds at, and the version of
     *  a multi file of them: 10 where one of them is version 10, and 9
     *  otherwise (tools.md 11.1). */
    static int binds(List<byte[]> tuneFiles) {
        for (byte[] tune : tuneFiles) {
            if (tune.length >= Tune.INDEX_AT && Tune.getWord(tune, 4) == Tune.VERSION) {
                return Tune.VERSION;
            }
        }
        return Tune.UNMAPPED;
    }

    /**
     * Checks the core descriptor, supported bound version and required
     * switches. Descriptor flags determine capabilities, independently
     * of the filename.
     *
     * @throws IllegalArgumentException for an invalid descriptor,
     * unsupported bound version or missing raster, lean-tick,
     * deferred-start or lookup support requested by the arguments
     */
    static void checkCore(byte[] core, boolean monitor, boolean lean, int binds) {
        checkCore(core, monitor, lean, false, binds);
    }

    static void checkCore(byte[] core, boolean monitor, boolean lean, boolean pcrel,
                          int binds) {
        checkCore(core, monitor, lean, pcrel, false, binds);
    }

    static void checkCore(byte[] core, boolean monitor, boolean lean, boolean pcrel,
                          boolean defers, int binds) {
        checkCore(core, monitor, lean, pcrel, defers, false, binds);
    }

    static void checkCore(byte[] core, boolean monitor, boolean lean, boolean pcrel,
                          boolean defers, boolean maps, int binds) {
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
        if (reads < binds) {
            throw new IllegalArgumentException("the core reads bound tunes to version " + reads
                    + ", and this binds at " + binds);
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
        if (pcrel && (flags & CORE_PCREL) == 0) {
            throw new IllegalArgumentException("the core's flags at " + CORE_FLAGS_AT + " read "
                    + flags + ", and the row read through the program counter asked for needs"
                    + " bit 2 set");
        }
        if (defers && (flags & CORE_DEFER) == 0) {
            throw new IllegalArgumentException("the core's flags at " + CORE_FLAGS_AT + " read "
                    + flags + ", and a subtune that defers a start needs bit 3 set");
        }
        if (maps && (flags & CORE_MAP) == 0) {
            throw new IllegalArgumentException("the core's flags at " + CORE_FLAGS_AT + " read "
                    + flags + ", and a subtune that maps a value needs bit 4 set");
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

    /** The clock the tag block names: the one asked for, and the VBL
     *  where the raster monitor is in and the caller leaves the clock to
     *  the tool, since the monitor paints one frame of calls and reads
     *  against the raster where the tick comes from the VBL. */
    static Asked asked(Options options) {
        return options.asked() == Asked.CHOSEN && options.monitor()
                ? Asked.VBL : options.asked();
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

    /** The clock tag: '!V' and the rate, the VBL, where the claims byte
     *  has Timer C, since the player's handler then has that timer, or
     *  where the VBL is asked for; 'TC' and the rate, Timer C,
     *  otherwise. A host calls play from that clock (BINARIES.md 5.4).
     *
     *  @throws IllegalArgumentException where Timer C is asked for and
     *      the claims byte has it
     */
    static String clock(int claimed, int rate, Asked asked) {
        if ((claimed & TIMER_C) != 0) {
            if (asked == Asked.TIMER_C) {
                throw new IllegalArgumentException("the set claims Timer C and the clock"
                        + " asked for is Timer C: the player's handler has that timer");
            }
            return VBL_CLOCK + rate;
        }
        return (asked == Asked.VBL ? VBL_CLOCK : TIMER_C_CLOCK) + rate;
    }

    /**
     * The tag block, 'SNDH' through 'HDNS': TITL, COMM where there is a
     * composer, CONV, '##' and two digits, the clock tag and the rate,
     * FLAG, each text ended by a zero byte, a pad to an even length, FRMS
     * with a long a subtune, '!#SN' where the caller names them with a
     * word a subtune, the name's offset from the tag's first byte, then
     * the names each ended by a zero byte, a pad to an even length, TIME
     * with a word a subtune, and HDNS. The '##' count is before FRMS,
     * the names and TIME, since a reader sizes each by it. The raster
     * monitor paints one frame of calls (performance.md), so a core with
     * it in names the VBL as the clock.
     */
    static byte[] tags(Options options, int rate, int n, int[] frames, int claimed) {
        return tags(options, rate, n, frames, claimed, null);
    }

    private static byte[] tags(Options options, int rate, int n, int[] frames, int claimed,
                               int @Nullable [] rates) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        text(out, "SNDH");
        tag(out, "TITL", clean(options.title()));
        String composer = options.composer();
        if (composer != null && !composer.isEmpty()) {
            tag(out, "COMM", clean(composer));
        }
        tag(out, "CONV", clean(options.conv()));
        tag(out, String.format(Locale.ROOT, "##%02d", n), "");
        Asked clock = rates == null ? asked(options) : Asked.TIMER_C;
        tag(out, clock(claimed, rate, clock), "");
        tag(out, "FLAG", flag(claimed));
        pad(out);
        text(out, "FRMS");
        for (int i = 0; i < n; i++) {
            long calls = rates == null ? frames[i]
                    : ((long) frames[i] * rate + rates[i] - 1) / rates[i];
            if (calls > 0xFFFFFFFFL) {
                throw new IllegalArgumentException("subtune " + (i + 1)
                        + " exceeds the FRMS callback count");
            }
            int f = (int) calls;
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
        text(out, "TIME");
        for (int i = 0; i < n; i++) {
            int s = seconds(frames[i], rates == null ? rate : rates[i]);
            out.write(s >>> 8);
            out.write(s);
        }
        text(out, "HDNS");
        return out.toByteArray();
    }

    /**
     * The seconds {@code frames} frames last at {@code rate} frames a
     * second, rounded up and at most {@link #MAX_SECONDS}: 0 frames, a
     * tune that repeats, is 0 seconds, and a tune that plays once for
     * under a second is 1.
     */
    static int seconds(int frames, int rate) {
        return (int) Math.min(((long) frames + rate - 1) / rate, MAX_SECONDS);
    }

    /**
     * The file: the entry triple, the tag block padded even, the core with
     * its offsets patched, the subtune table, the bound tunes each on an
     * even address, and {@code workspace} zero bytes. Each entry is a
     * bra.w to the same entry of the core's triple, so all three
     * displacements are the header's bytes less 2.
     */
    static byte[] combine(byte[] core, List<byte[]> tunes, byte[] tags, int workspace) {
        return combine(core, new Bound.Set(List.of(), List.of(), tunes,
                new int[tunes.size()], new int[tunes.size()],
                Collections.nCopies(tunes.size(), new byte[0])), tags, workspace);
    }

    /**
     * Combines a set with shared images: the page area follows the core,
     * bound tunes follow the subtune table, then shared source tables,
     * images and transformation blocks. Patch each tune's offsets
     * relative to that tune.
     * A tune without a shared image is packaged separately with its
     * image and block.
     */
    static byte[] combine(byte[] core, Bound.Set set, byte[] tags, int workspace) {
        return combine(core, set, tags, workspace, new byte[0]);
    }

    private static byte[] combine(byte[] core, Bound.Set set, byte[] tags, int workspace,
                                  byte[] clock) {
        List<byte[]> tunes = set.tunes();
        int entries = even(12 + tags.length);
        int header = entries + clock.length;
        if (entries - 2 > Short.MAX_VALUE) {
            throw new IllegalArgumentException("the tag block is " + tags.length
                    + " bytes, and a bra.w reaches " + Short.MAX_VALUE);
        }
        int n = tunes.size();
        // The page area right after the core, within reach of the lookup
        // handlers, the core's last bytes (BINARIES.md 3.1).
        int pagesAt = even(core.length);
        int area = Math.max(0, area(core, set));
        int tableAt = even(pagesAt + area);
        int at = tableAt + 2 + 4 * n;
        // The bound tunes first, each on an even address (5.1), so that a
        // tune's sources are beside the core: a player whose ticks read a
        // row through a displacement reaches 32,767 bytes
        // (68k/player/switches.S, YMXR_PCREL), and an image between the
        // two would be in the way.
        int[] offsets = new int[n];
        for (int i = 0; i < n; i++) {
            offsets[i] = at;
            at = even(at + set.tuneBytes(i));
        }
        int poolAt = 0;
        if (set.sources().bytes() > 0) {
            poolAt = Tune.align(at);
            at = poolAt + set.sources().bytes();
        }
        // Then the images, each on a long: the reader's code appears once a
        // set of tunes that agree on what an image fixes once (DTX abi.md
        // 1), and every bound tune of that set reaches it.
        int[] imageAt = new int[set.images().size()];
        for (int i = 0; i < imageAt.length; i++) {
            at = Tune.align(at);
            imageAt[i] = at;
            at += set.images().get(i).length;
        }
        // Frames read the transformation blocks, including each lookup
        // page list. Identical blocks share an offset past the images.
        int[] blockAt = new int[n];
        Map<ByteBuffer, Integer> blocks = new HashMap<>();
        for (int i = 0; i < n; i++) {
            byte[] block = set.blocks().get(i);
            if (block.length == 0) {
                continue;
            }
            ByteBuffer key = ByteBuffer.wrap(block);
            Integer where = blocks.get(key);
            if (where == null) {
                where = Tune.align(at);
                blocks.put(key, where);
                at = where + block.length;
            }
            blockAt[i] = where;
        }
        at = even(at);
        int workAt = at;
        byte[] file = new byte[header + workAt + workspace];
        for (int entry = 0; entry < 12; entry += 4) {
            Tune.putWord(file, entry, BRA_W);
            Tune.putWord(file, entry + 2, entries - 2);
        }
        System.arraycopy(tags, 0, file, 12, tags.length);
        System.arraycopy(clock, 0, file, entries, clock.length);
        System.arraycopy(core, 0, file, header, core.length);
        Tune.putLong(file, header + CORE_TABLE_AT, tableAt);
        Tune.putLong(file, header + CORE_WORK_AT, workAt);
        if (area > 0) {
            Tune.putLong(file, header + CORE_PAGES_AT, pagesAt);
        }
        Tune.putWord(file, header + tableAt, n);
        at = header + poolAt;
        for (ByteBuffer source : set.sources().tables()) {
            source.get(source.position(), file, at, source.remaining());
            at += Tune.align(source.remaining());
        }
        for (Map.Entry<ByteBuffer, Integer> entry : blocks.entrySet()) {
            ByteBuffer block = entry.getKey();
            block.get(0, file, header + entry.getValue(), block.remaining());
        }
        for (int i = 0; i < imageAt.length; i++) {
            System.arraycopy(set.images().get(i), 0, file, header + imageAt[i],
                    set.images().get(i).length);
        }
        for (int i = 0; i < n; i++) {
            Tune.putLong(file, header + tableAt + 2 + 4 * i, offsets[i]);
            int tune = header + offsets[i];
            System.arraycopy(tunes.get(i), 0, file, tune, set.tuneBytes(i));
            if (poolAt > 0) {
                int index = tune + Bound.indexAt(Tune.getWord(file, tune + 4));
                int[] offsetsInPool = set.sources().offsets()[i];
                for (int j = 0; j < offsetsInPool.length; j++) {
                    int entry = index + 4 * j;
                    int counted = Tune.getLong(file, entry) & Tune.COUNTED;
                    Tune.putLong(file, entry, (poolAt + offsetsInPool[j] - offsets[i]) | counted);
                }
            }
            if (imageAt.length > 0) {
                // The bound tune reaches its image from its first byte,
                // and the images are after it.
                Tune.putLong(file, header + offsets[i] + Bound.IMAGE_AT,
                        imageAt[set.image()[i]] - offsets[i]);
            }
            if (blockAt[i] > 0) {
                Tune.putLong(file, header + offsets[i] + Bound.TRANSFORMATIONS_AT,
                        blockAt[i] - offsets[i]);
            }
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
     *  header, which accepts any bytes. */
    static String clean(String text) {
        StringBuilder out = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= 0x20 && c < 0x7F) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** The core selection flags of the three tools that write SNDH files.
     *  {@code -link} is accepted as the default for existing calls. */
    static final String[] LINKS = {"-carried", "-link"};

    /** Two lists of flags as one. */
    private static String[] read(String[] some, String[] more) {
        String[] all = Arrays.copyOf(some, some.length + more.length);
        System.arraycopy(more, 0, all, some.length, more.length);
        return all;
    }

    /**
     * {@code ymxr-sndh}: a tune file or a multi file (doc/BINARIES.md 0)
     * on standard input, an SNDH file on standard output. A multi file's
     * tunes are subtunes 1 up in its order, each named by the name the
     * multi file records for it. The title is the first tune's name unless
     * {@code -tTITLE} names another, and the CONV tag's text is
     * {@link #CONVERTER} unless {@code -wWRITER} names another.
     * {@code -lean} puts the core whose ticks neither drop the interrupt
     * level nor write an end of interrupt under the tunes, {@code -perf}
     * puts the core with the raster monitor in there, for reading a run,
     * and {@code -pcrel} the core whose ticks read a row through the
     * program counter (BINARIES.md 5.5). The three are one switch each,
     * and any two together select the core that is both, so
     * {@code -perf -lean} reads what a lean run costs. The core is linked
     * for the subtunes by default (doc/LINKER.md). {@code -carried} writes
     * the full core selected by the switches.
     */
    public static void main(String[] args) {
        List<String> flags = new ArrayList<>(Arrays.asList(args));
        Tool tool = Tool.of("ymxr-sndh", flags, read(Ymxs.TAGS, LINKS));
        Ymxs.only(tool, flags, Ymxs.TAGS, LINKS);
        @Nullable String title = null;
        @Nullable String composer = null;
        @Nullable String writer = null;
        boolean monitor = false;
        boolean lean = false;
        boolean link = true;
        Asked asked = Ymxs.asked(tool, flags);
        Ticks ticks = Ticks.CHOSEN;
        for (String flag : flags) {
            if (flag.equals("-perf")) {
                monitor = true;
            } else if (flag.equals("-lean")) {
                lean = true;
            } else if (flag.equals("-pcrel")) {
                ticks = Ticks.PCREL;
            } else if (flag.equals("-abs")) {
                ticks = Ticks.ABSOLUTE;
            } else if (flag.equals("-carried")) {
                link = false;
            } else if (flag.startsWith("-copies")) {
                throw tool.usage("not a flag of the tool: " + flag
                        + "; a tune file is packed already");
            } else if (flag.equals("-vbl") || flag.equals("-tc")) {
                // the clock asked for, read by Ymxs.asked above
                continue;
            } else if (flag.startsWith("-t")) {
                title = flag.substring(2);
            } else if (flag.startsWith("-c")) {
                composer = flag.substring(2);
            } else if (flag.startsWith("-w")) {
                writer = flag.substring(2);
            }
        }
        Report report = new Report(tool.reports());
        byte[] file = tool.bytes();
        List<byte[]> tunes;
        List<String> names;
        if (Multi.is(file)) {
            Multi.Read read;
            try {
                read = Multi.read(file);
            } catch (IllegalArgumentException wrong) {
                throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
            }
            tunes = read.tunes();
            names = read.names();
        } else {
            tunes = List.of(file);
            names = List.of("");
        }
        if (title == null) {
            title = names.get(0).isBlank() ? "(untitled)" : names.get(0);
        }
        Options options = new Options(title, composer,
                tunes.size() > 1 ? names : null, monitor, lean, ticks, asked, link, writer);
        byte[] sndh;
        try {
            sndh = of(tunes, options);
        } catch (IllegalArgumentException wrong) {
            throw tool.wrong(Tool.WRONG, String.valueOf(wrong.getMessage()));
        }
        made(report, options, names, tunes, sndh);
        tool.report(Report.count(sndh.length, "byte", "bytes") + ", "
                + Report.count(tunes.size(), "subtune", "subtunes"));
        Out.write(tool, sndh);
    }

    /** What the file was made of: the core that went under the tunes,
     *  read back off the file's flags word, the tags written, each
     *  subtune's bound tune, and the workspace under them. */
    private static void made(Report report, Options options, List<String> names,
                             List<byte[]> tunes, byte[] sndh) {
        if (!report.says()) {
            return;
        }
        int at = coreAt(sndh, even(Tune.getWord(sndh, 2) + 2));
        int flags = Tune.getWord(sndh, at + CORE_FLAGS_AT);
        boolean monitor = (flags & CORE_MONITOR) != 0;
        boolean lean = (flags & CORE_LEAN) != 0;
        boolean pcrel = (flags & CORE_PCREL) != 0;
        boolean defer = (flags & CORE_DEFER) != 0;
        boolean map = (flags & CORE_MAP) != 0;
        Binaries.Binary binary = Binaries.binary(monitor, lean, pcrel, defer, map);
        int carried = Binaries.core(monitor, lean, pcrel, defer, map).length;
        int core = carried;
        if (options.link() && Link.links(defer, map)) {
            List<TuneFile> read = new ArrayList<>();
            for (byte[] tune : tunes) {
                read.add(TuneFile.read(tune));
            }
            Pieces table = Pieces.carried(binary);
            core = Link.link(Binaries.carried(binary.name()), table, Link.blocks(read)).length;
            List<String> out = Link.leftOut(table, Link.blocks(read));
            report.say("the core: " + binary.name() + " linked for the set, "
                    + Report.count(core, "byte", "bytes") + " of " + carried
                    + (out.isEmpty() ? ", every block kept"
                    : ", the blocks left out: " + String.join(", ", out)));
        } else {
            report.say("the core: " + binary.name() + ", "
                    + Report.count(core, "byte", "bytes"));
        }
        List<String> switches = new ArrayList<>();
        if (monitor) {
            switches.add("-perf, the raster monitor in");
        }
        if (lean) {
            switches.add("-lean, ticks that neither drop the interrupt level nor write"
                    + " an end of interrupt");
        }
        if (pcrel) {
            switches.add("ticks that read a row through the program counter");
        } else if (options.ticks() == Ticks.CHOSEN) {
            switches.add("ticks that read a row through an absolute address: the tunes end"
                    + " past the " + PCREL_REACH + " bytes a displacement reaches");
        } else {
            switches.add("-abs, ticks that read a row through an absolute address");
        }
        if (defer) {
            switches.add("the deferred start in: a subtune defers a start");
        }
        if (map) {
            switches.add("the lookup in: a subtune maps a value");
        }
        report.row("the switches", switches.isEmpty() ? "none, the plain core"
                : String.join("; ", switches));
        Prg.Tags tags = Prg.tags(sndh);
        report.say("the tags: TITL " + options.title()
                + (options.composer() == null ? "" : ", COMM " + options.composer())
                + ", CONV " + options.conv() + ", " + tags.clock() + tags.rate() + ", FLAG ~" + tags.flag()
                + (options.names() == null ? ""
                : ", !#SN with " + Report.count(options.names().size(), "name", "names")));
        Bound.Set set = Bound.of(tunes);
        int bound = set.sources().bytes();
        int images = 0;
        for (byte[] image : set.images()) {
            images += image.length;
        }
        for (int i = 0; i < tunes.size(); i++) {
            int bytes = set.tuneBytes(i);
            bound += bytes;
            // A subtune is called by the name the multi file records for
            // it; a tune file records none, and one tune is one subtune.
            report.row(names.get(i).isBlank() ? "the tune" : names.get(i),
                    Report.count(tunes.get(i).length, "byte", "bytes") + " bound to " + bytes
                    + ", its table in image " + (set.image()[i] + 1));
        }
        if (set.sources().bytes() > 0) {
            report.row("the shared sources", Report.count(set.sources().bytes(), "byte", "bytes"));
        }
        report.say("the images: " + Report.count(set.images().size(), "image", "images")
                + " of " + Report.count(images, "byte", "bytes")
                + ", DTX's reader once a set of tunes that share one");
        // What an image fixes once splits a set into more than one,
        // and only a flag moves the unit, -k on one dump and not another:
        // a tune whose row count or repeat row is odd is padded to the
        // unit named (SPEC.md 6, rule 6; tools.md, experiments.md).
        for (int i = 0; i < set.images().size(); i++) {
            int of = 0;
            for (int which : set.image()) {
                of += which == i ? 1 : 0;
            }
            report.row("image " + (i + 1), set.shapes().get(i) + ", "
                    + Report.count(of, "tune", "tunes"));
        }
        report.say("the file: " + Report.count(sndh.length, "byte", "bytes") + ", the core "
                + core + ", the images "
                + images + ", the tunes " + bound + ", the workspace and the rest "
                + (sndh.length - core - images - bound));
    }

}
