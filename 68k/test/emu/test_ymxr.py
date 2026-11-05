"""The player under emulation, against a model of SPEC.md 4 and 5.

Every tune under ym/test is converted, then played row by row on an
emulated 68000: the chip writes of every frame against the frame
procedure's, the timers' programming against the rate columns', the place
each handler keeps against the source's rows, and every tick's write
against the row its place names. The model is built here from the
tune's tables, so the player is checked against the specification and not
against the converter.

Usage: test_ymxr.py [tune.ym ...]      the fixtures under ym/test by default
       test_ymxr.py -corpus [N]        a spread of the corpus in place of the
                                       fixtures, 40 tunes unless N sets it
       test_ymxr.py -framesN [tunes]   each tune played for N frames at most,
                                       for a short run over every shape
       test_ymxr.py -onlyNAMES         the checks NAMES names alone, a comma
                                       between two, after one build of the
                                       player and with no tune played: of
                                       the CHECKS below, in their order
       test_ymxr.py -cycles [tunes]    the play call's cost as well
       test_ymxr.py -refill [tunes]    -cycles and the advance's parts off
                                       one pass: what a refill spends
                                       outside ST4's decoder and inside it
       test_ymxr.py -hatari [tunes]    the same tunes on a real MFP, under
                                       Hatari, and the kit's voices beside
                                       them: no dump converts to a target
                                       of several registers
       test_ymxr.py -stub [tunes]      the program stub's two clocks under
                                       Hatari: one SNDH file played from
                                       Timer C and from the VBL, the rows
                                       one stream either way, and the same
                                       tune at 60 Hz, whose rows land on
                                       the period the timer is armed at
       test_ymxr.py -clock             the stub's Timer C handler under
                                       Hatari's profiler, one tune at 50 Hz
                                       and at 60 Hz, against performance.md
       test_ymxr.py -cost              the raster monitor's runs under
                                       Hatari, as ym/cost.sh makes them:
                                       the two tunes against YMX, one at
                                       unit 1, and DBA 2's first write,
                                       against performance.md
       test_ymxr.py -perf [tunes]      the player built with the raster
                                       monitor in, against the same model:
                                       the monitor moves no chip write
       test_ymxr.py -lean [tunes]      the player built with YMXR_NEST=0 and
                                       YMXR_AEOI=1, whose ticks omit the
                                       interrupt-level drop, against the
                                       same model
       test_ymxr.py -kit [tunes]       the conformance kit's tune files, the
                                       player's frames against the reader's
                                       entries a frame at a time
       test_ymxr.py -abs [tunes]       the player built with YMXR_PCREL=0,
                                       whose ticks read a row through an
                                       absolute address
                                       (68k/player/switches.S): the same
                                       model, and it joins the switches
                                       above
       test_ymxr.py -map [tunes]       optional lookup support, YMXR_MAP=1,
                                       checked against the same model;
                                       combines with the switches above
       test_ymxr.py -linked [tunes]    each tune on the player ymxr-sndh
                                       -link links for it (doc/LINKER.md),
                                       the tool's core against the rig's
                                       link; combines with the switches
                                       above and -kit
       test_ymxr.py -xpatch           the real-ST program of 68k/test/st
                                       under Hatari: each case's line of
                                       the XPATCH.TXT it writes against
                                       the table of its README.md

The fixtures under ym/test are chosen for the shapes a tune has, one of
each; -corpus reads the corpus instead, which no fixture was chosen for. A
tune that fails is named and the rest are read, so one run reports every
tune that fails rather than the first.

The player reads a bound tune (doc/BINARIES.md 1): each tune file is bound
through bin/ymxr-bind before it is played, and a tune file of another
version is checked to be rejected by the binder, the reader and, its bound
form's version moved, the player.

Under unicorn the timers are modelled here, since it raises no interrupt:
every tick is fired here at the time the model computes. Two ticks the timers
do not produce are fired besides: one of a timer no row has started (SPEC.md
5.2.1), and one inside a frame, at each boundary SPEC.md 4.2.1 has a tick
falling on (inside). Under Hatari the MFP fires them: the tune goes into an
SNDH file and a program around it through bin/ymxr-sndh and bin/ymxr-prg
(BINARIES.md 3 and 4), the program claims the machine and plays the tune on
the VBL, since the screen's rate is the tune's 50 Hz, and the trace of every
chip write is read against the same model, the frames cut at the VBL and the
ticks counted against the rates the trace shows the timers programmed at. So
-hatari requires tunes at 50 Hz.

Needs rmac (RMAC, or on the path), DTX's dtx-write (DTX_WRITE, or on the
path) to read the table back and to pack one again with a row set by hand,
unicorn (pip install unicorn), and the converter, the binder, the multi
file's maker and the two combiners under bin/. DTX's rig, at
DTX_REPO/68k/test/emu, counts the cycles where it is found; hatari
(HATARI) with a TOS image (TOS) plays the tune on a real MFP, and runs the
real-ST program under -xpatch.
"""
import copy
import json, os
from collections import Counter
import re
import struct
import subprocess
import sys
import tempfile

from unicorn import (Uc, UcError, UC_ARCH_M68K, UC_MODE_BIG_ENDIAN,
                     UC_HOOK_MEM_WRITE, UC_HOOK_MEM_READ, UC_HOOK_CODE)
from unicorn.m68k_const import (UC_CPU_M68K_M68000, UC_M68K_REG_D0,
                                UC_M68K_REG_D1, UC_M68K_REG_D2, UC_M68K_REG_D3,
                                UC_M68K_REG_D5, UC_M68K_REG_D6, UC_M68K_REG_D7,
                                UC_M68K_REG_A0, UC_M68K_REG_A1, UC_M68K_REG_A2,
                                UC_M68K_REG_A3, UC_M68K_REG_A6,
                                UC_M68K_REG_A7,
                                UC_M68K_REG_PC, UC_M68K_REG_SR)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", ".."))
RMAC = os.environ.get("RMAC", "rmac")
DTX_WRITE = os.environ.get("DTX_WRITE", "dtx-write")
DTX_REPO = os.environ.get("DTX_REPO", os.path.join(ROOT, "..", "DTX"))
HATARI = os.environ.get("HATARI", "hatari")
TOS = os.environ.get("TOS", os.path.expanduser("~/hatari-2.6.1_macos/tos-2.06.rom"))
# Each Hatari run passes HATARI_FLAGS. Fast boot: TOS 2.06 skips its test
# of the 4 MB of memory, which with fast boot off in the emulator's
# configuration outlasts the VBLs a run allows before the program starts.
# Both floppy drives off: TOS addresses no disk. Quit with no confirmation,
# and a dialog only for a fatal message, the lowest level Hatari reads.
# Where SDL_VIDEODRIVER and SDL_AUDIODRIVER are unset, a run is headless
# and silent, with SDL's dummy video and audio drivers; its input is
# closed, and a time limit ends a run that waits on a dialog.
HATARI_FLAGS = ["--fast-boot", "on", "--drive-a", "off", "--drive-b", "off",
                "--confirm-quit", "off", "--alert-level", "fatal"]
HATARI_ENV = dict(os.environ)
HATARI_ENV.setdefault("SDL_VIDEODRIVER", "dummy")
HATARI_ENV.setdefault("SDL_AUDIODRIVER", "dummy")
# The rows the program is asked to play before it stops (bin/ymxr-prg -r).
STUB_FRAMES = 2000

# The frames a run of -stub plays from each clock, where the row stream is
# read rather than the tune.
STUB_CLOCKS = 600
# The versions of a tune file (SPEC.md 3.3.5) and of a bound tune
# (BINARIES.md 1): 10, which a writer writes where a row maps a value, 9,
# which it writes otherwise, 8, read as 9, and 7, whose control columns
# have bit 3 at 0, each a tune whose table is DTX3, which a reader reads.
TUNE_VERSIONS = (10, 9, 8, 7)
WRITTEN = 9
MAPPED = 10

# Bit 31 of a source's index entry: the source's rows are whole bytes and
# a player counts them (SPEC.md 3.1).
COUNTED = 1 << 31


def bound_versions(symbols):
    """Supported bound versions: 7 to 10 with lookup support, 7 to 9
    without it (BINARIES.md 1.5; YMXR_MAP).
    """
    return (7, 8, 9, 10) if "ymxr_look" in symbols else (7, 8, 9)


# The video address counter's low byte, which the raster monitor waits on,
# and the background it paints.
VIDEO = 0xFFFF8209
PALETTE = 0xFFFF8240
# What the monitor's build paints: the call's work, and the timers' bar.
PERF_FRAME = 0x0700
PERF_BAR = 0x0770

# The memory map: the player, the tune two bytes past a long so no field
# assumes one, the workspace on a long, a stack, and a sentinel a call
# returns to.
CODE = 0x1000
# Place each tune within signed-word reach of the assembled handlers, as
# SNDH and PRG layouts require (doc/BINARIES.md 2, 3). Builds differ
# with deferred and lookup support, so placed computes an address per
# Machine; using one address for all builds can exceed a smaller core's
# reach.
FILE = None


def placed(length):
    """Where a tune is loaded after a build of `length` bytes at CODE: the
    first 256-byte boundary past it, two bytes on so no field assumes a
    long. Under -abs, whose ticks read an absolute address and reach any
    offset, 32,768 bytes further, past a displacement's reach from the
    build's end."""
    end = CODE + length + (0 if PCREL else 0x8000)
    return ((end + 0xFF) & ~0xFF) + 2


# The page area of a list of P pages besides its tables: 256 bytes a
# page, and the 255 bytes over which each effect's pages begin at its
# offset modulo 256 (BINARIES.md 3.1, 5.6), and the list's tag.
PAGE_BYTES = 256
PAGE_SPAN = 255
LIST_TAG = b"YP"


def list_of(tune_bytes):
    """A bound tune's transformation count and its list's entries, (effect,
    form, transformation) each; None for a tune of no block, and entries of
    None where no list follows the block (BINARIES.md 1.1)."""
    if tune_bytes[:4] != b"YMXB" or struct.unpack(">H", tune_bytes[4:6])[0] != MAPPED:
        return None
    block = struct.unpack(">i", tune_bytes[24:28])[0]
    if block <= 0:
        return None
    count = struct.unpack(">H", tune_bytes[block:block + 2])[0]
    last = block + 2 + 4 * count
    for n in range(count):
        at = block + long_at(tune_bytes, block + 2 + 4 * n)
        last = max(last, at + 2 + struct.unpack(">H", tune_bytes[at:at + 2])[0])
    if tune_bytes[last:last + 2] != LIST_TAG:
        return count, None
    return count, [tuple(tune_bytes[last + 3 + 3 * k:last + 6 + 3 * k])
                   for k in range(tune_bytes[last + 2])]


def plan_of(count, entries, reach, fallback):
    """The pages init fills, k, and the area's bytes, by the rule of
    BINARIES.md 5.6: QT + 255 + 256 X within reach, X the first k pages and
    the fallback pages of each effect of an entry at or past k; None where
    even the fallbacks do not fit."""
    if not entries:
        return 0, 0
    tables = len({(e, f) for e, f, _ in entries}) * count + PAGE_SPAN
    most = (reach - tables) // PAGE_BYTES
    last = {e: j + 1 for j, (e, _, _) in enumerate(entries)}
    k, dropped = len(entries), 0
    while k + fallback * dropped > most:
        k -= 1
        if k < 0:
            return None
        dropped = sum(1 for one in last.values() if one > k)
    return k, tables + PAGE_BYTES * (k + fallback * dropped)


def reach_of(symbols, area):
    """The bytes past an area at area the first handler reaches."""
    return 32767 - (area - (CODE + symbols["ymxr_tick0"]))


def fallback_of(symbols):
    """An effect's fallback pages, two a bank of handlers."""
    return 4 if "ymxr_bank1" in symbols else 2


def area_of(tune_bytes, symbols, area=None, reach=None):
    """The page area's bytes a bound tune needs on a player with the
    lookup, its area at area, or reach bytes before the first handler's
    reach; 0 for a player without it or a tune of no page, and None where
    it does not fit."""
    listed = list_of(tune_bytes)
    if "ymxr_look" not in symbols or not listed or not listed[1]:
        return 0
    if reach is None:
        reach = reach_of(symbols, area)
    plan = plan_of(listed[0], listed[1], reach, fallback_of(symbols))
    return plan and plan[1]


def page_at(symbols, area, tune, effect, form, run, reach):
    """Where init puts the page of transformation run in form on an effect:
    the tables first, then each effect's pages, and its fallback where it
    has one, at its offset modulo 256 past them, in the order of those
    offsets (68k/player/lookup_prefill.S, ymxr_prefill). The page's address,
    or the effect's fallback pages for a page init leaves out."""
    entries = tune.pages
    count = len(tune.transformations)
    fallback = fallback_of(symbols)
    k = plan_of(count, entries, reach, fallback)[0]
    base = area + count * len({(e, f) for e, f, _ in entries})
    ref = [CODE + symbols["ymxr_ref%d" % e] for e in range(4)]
    off = [(ref[e] - base) & 0xFF for e in range(4)]
    placed = [sum(1 for e_, _, _ in entries[:k] if e_ == e) for e in range(4)]
    falls = [any(e_ == e for e_, _, _ in entries[k:]) for e in range(4)]
    size = [placed[e] + (fallback if falls[e] else 0) for e in range(4)]
    region = base + off[effect] + 256 * sum(size[f] for f in range(4) if off[f] < off[effect])
    mine = [p for p in entries[:k] if p[0] == effect]
    if (effect, form, run) in mine:
        return region + 256 * mine.index((effect, form, run))
    return [region + 256 * (placed[effect] + n) for n in range(fallback if falls[effect] else 0)]


WORK = 0x40000
STACK = 0x80000
DONE = 0x90000
PSG = 0xFFFF8800
MFP = 0xFFFFFA00

MFP_CLOCK = 2457600
# The MFP's clock against the 68000's.
MFP_PER_CPU = MFP_CLOCK / 8000000.0
PRESCALER = [0, 4, 10, 16, 50, 64, 100, 200]
# The timers by effect (SPEC.md 2.3): control register, its shift in a
# shared one, data register, enable register and bit, vector.
TIMER = [dict(ctrl=0xFFFFFA19, shift=0, data=0xFFFFFA1F, ier=0xFFFFFA07, bit=5, vector=0x134),
         dict(ctrl=0xFFFFFA1D, shift=0, data=0xFFFFFA25, ier=0xFFFFFA09, bit=4, vector=0x110),
         dict(ctrl=0xFFFFFA1B, shift=0, data=0xFFFFFA21, ier=0xFFFFFA07, bit=0, vector=0x120),
         dict(ctrl=0xFFFFFA1D, shift=4, data=0xFFFFFA23, ier=0xFFFFFA09, bit=5, vector=0x114)]
# The effects each of those registers belongs to, in effect order, so a
# trace of the MFP reports which effect the frame procedure has stepped.
# Timers C and D share a control register, and that one has two.
OWNS = {reg: [i for i in range(4) if reg in (TIMER[i]["ctrl"], TIMER[i]["data"])]
        for t in TIMER for reg in (t["ctrl"], t["data"])}
C = 30
EFFECT = 14
# The bits each register reads of a byte written to it (SPEC.md 4): the
# player writes the column byte whole, set bit and bits beside included,
# and the chip drops the rest.
FITS = [0xFF, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0x1F, 0xFF, 0x1F, 0x1F, 0x1F, 0xFF, 0xFF, 0x0F]


# What YMXR_stop leaves on the chip: the three voices silenced first, then
# every register zero, then the mixer with each channel off. A row sets the
# registers it names alone, so a tune that follows one which set a register
# it leaves unset would read that value (68k/player/stop.S, YMXR_stop).
HUSHED = ([(8, 0), (9, 0), (10, 0)] + [(r, 0) for r in range(13, -1, -1)]
          + [(7, 0xFF)])

def masked(writes):
    return [(reg, value & FITS[reg]) for reg, value in writes]


# What the player is assembled with: the raster monitor's switch, which
# an equate of the source reads as the assembly defines it.
# The 68000's interrupt entry and its rte, which the cycle counter here
# does not reach: 44 and 20 from the manual. performance.md's 160 for a
# tick is its 96 and these.
ENTRY = 64

PERF = "-perf" in sys.argv
LEAN = "-lean" in sys.argv
# -defer runs every tune on the player with the deferred start in
# (YMXR_DEFER=1); without it, check plays a tune whose effects byte sets
# bit 4 on that player and every other tune on the player without it.
DEFER = "-defer" in sys.argv

# The row of performance.md's lean table each kind of tick reads, the
# five kinds being what a lean tick has: it drops no interrupt level, so
# the path a tune of one effect runs is the path every other tune runs.
LEAN_ROW = {"on": "a row written, the place stepped",
            "loop": "the marker, the place to row `RR`",
            "stop": "the marker, the timer stopped",
            "square": "a square's two rows, no place stepped",
            "one": "a source of one row, no place stepped"}
DEFINED = {"YMXR_PERF": 1 if PERF else 0,
           "YMXR_NEST": 0 if LEAN else 1, "YMXR_AEOI": 1 if LEAN else 0}

# -map enables YMXR_MAP=1 for every tune. Otherwise check enables it
# only for tunes with effects bit 5 set.
MAP = "-map" in sys.argv
# Each tune played on the player ymxr-sndh -link links for it
# (doc/LINKER.md), where the run's other checks are left out.
LINKED = "-linked" in sys.argv

# Build switches and assembled variants for this run. Deferred tunes
# require YMXR_DEFER; version 10 tunes require YMXR_MAP (check).
DEFINES = []
DEFER_ON = "-dYMXR_DEFER=1"
MAP_ON = "-dYMXR_MAP=1"
_BUILT = {}


def build(defines, source="YMXR.S"):
    """The bytes a source assembles to with a define set, and its symbols,
    assembled once a run."""
    key = (source, tuple(sorted(set(defines))))
    if key not in _BUILT:
        _BUILT[key] = assemble(source, key[1])
    return _BUILT[key]


def switched(defines, defer=False, lookup=False):
    """Add requested deferred-start and lookup defines, preserving switches
    already present.
    """
    out = [d for d in defines if d not in (DEFER_ON, MAP_ON)]
    if defer or DEFER_ON in defines:
        out.append(DEFER_ON)
    if lookup or MAP_ON in defines:
        out.append(MAP_ON)
    return out


def deferring(defines):
    """The player assembled with those switches and the deferred start in
    (YMXR_DEFER=1), and its symbols."""
    return build(switched(defines, defer=True))


def needs(effects):
    """Required switches from the effects byte: bit 4 for deferred starts,
    bit 5 for lookups (BINARIES.md 2.1).
    """
    return bool(effects & 0x10), bool(effects & 0x20)


# A cycle figure the documents read is true where it is within this share
# of the cycles the rig counts (tools.md 17.1). Sizes, counts of frames,
# ticks or pages, and words are read exactly, and are compared without
# near.
CYCLES_WITHIN = 0.05


def near(said, counted):
    """Whether the cycles a document reads are the rig's count within
    CYCLES_WITHIN: a number against a number, a tuple or a list figure by
    figure, a text by the figures in it with the rest of the text equal,
    and a set of counts where it has one count."""
    if isinstance(counted, set):
        return len(counted) == 1 and near(said, next(iter(counted)))
    if isinstance(said, (tuple, list)) or isinstance(counted, (tuple, list)):
        return (isinstance(said, (tuple, list)) and isinstance(counted, (tuple, list))
                and len(said) == len(counted)
                and all(near(a, b) for a, b in zip(said, counted)))
    if isinstance(said, (int, float)) and isinstance(counted, (int, float)):
        return abs(said - counted) <= CYCLES_WITHIN * abs(counted)
    if not isinstance(said, str) or not isinstance(counted, str):
        return said == counted
    figure = r"\d[\d,]*"
    if re.sub(figure, "#", said) != re.sub(figure, "#", counted):
        return False
    return near([int(f.replace(",", "")) for f in re.findall(figure, said)],
                [int(f.replace(",", "")) for f in re.findall(figure, counted)])


_SOURCE = {}


def player_source(name="YMXR.S"):
    """The player's source as rmac reads it: each .include line replaced
    by the lines of the file it includes, under 68k/. Read once a run."""
    if name not in _SOURCE:
        lines = []
        for line in open(os.path.join(ROOT, "68k", name)):
            m = re.match(r'\s+\.include\s+"([^"]+)"', line)
            lines.append(player_source(m.group(1)) if m else line)
        _SOURCE[name] = "".join(lines)
    return _SOURCE[name]


def equate(name, symbols=None):
    """An equate out of the player's source, so the rig reads what the
    player reads. A term that names another equate is read through to its
    figures, one that names a label is the address the assembly gave it,
    and a name the assembly defines reads as the value it was defined
    with. A tick's offsets are measured off the handler's labels, so they
    are read with the symbols the assembly gave."""
    if name in DEFINED:
        return DEFINED[name]
    if symbols and name in symbols:
        return symbols[name]
    m = re.search(r"^%s\s+equ\s+([-$\w+*]+)" % name, player_source(), re.M)
    assert m, name + " is not an equate of the player's source"
    whole = 0
    for signed in re.finditer(r"([+-]?)([$\w*]+)", m.group(1)):
        product = 1
        for factor in signed.group(2).split("*"):
            if factor.startswith("$"):
                product *= int(factor[1:], 16)
            elif factor.isdigit():
                product *= int(factor)
            else:
                product *= equate(factor, symbols)
        whole += -product if signed.group(1) == "-" else product
    return whole


# A tick handler's operands: the register it selects, and the place it
# reads; a square handler's, the register it selects and the row it
# is at; and a one-row source handler's, the same two. Every offset
# is measured off its handler's labels, so they are fixed once the
# player is assembled (main).
# The player as it is assembled: a handler reads its row through a
# displacement from the instruction that reads it, and the rig reads the
# place the same way. -abs assembles the player that reads an absolute
# address, and clears this.
PCREL = True

TICK_SEL = TICK_PTR = SQ_SEL = SQ_VAL = SQ_MASK = ONE_SEL = ONE_VAL = 0
TICKC_SEL = TICKC_PTR = TICKC_LEFT = 0
TWC_SEL1 = TWC_PTR1 = TWC_LEFT = 0
TW_SEL1 = TW_PTR1 = (0, 0)

# The registers a target writes and the column of the row each writes,
# in the order a tick writes them (SPEC.md 2.1): the column the marker
# is in comes last, so the move that writes it leaves the marker in N
# and the handler tests it there (3.2.1).
TARGETS = {n: [(n, 0)] for n in range(14)}
TARGETS.update({14: [(0, 0), (1, 1)], 15: [(2, 0), (3, 1)], 16: [(4, 0), (5, 1)],
                17: [(0, 0), (8, 2), (1, 1)], 18: [(2, 0), (9, 2), (3, 1)],
                19: [(4, 0), (10, 2), (5, 1)],
                20: [(11, 0), (12, 1)], 21: [(11, 0), (12, 1), (13, 2)],
                22: [(8, 1), (6, 0)], 23: [(9, 1), (6, 0)], 24: [(10, 1), (6, 0)]})

# Source column eligible for the optional lookup: the target's last
# value. Sole value for setRn, the coarse nibble for setToneX, volume for
# voice/noise, high envelope byte for setEnvelope, or shape for
# setBuzzer (SPEC.md 1.10.1, 2.1).
LAST_VALUE = {n: 0 for n in range(14)}
LAST_VALUE.update({14: 1, 15: 1, 16: 1, 17: 2, 18: 2, 19: 2, 20: 1, 21: 2,
                   22: 1, 23: 1, 24: 1})


def assemble(source="YMXR.S", defines=()):
    """The bytes a source under 68k/ assembles to, and its symbols: the
    player's, or the SNDH core's, which includes the player."""
    work = tempfile.mkdtemp()
    out, lst = os.path.join(work, "code.bin"), os.path.join(work, "code.lst")
    r = subprocess.run([RMAC, "-m68000", "-fr", "-l*" + lst, "-i" + os.path.join(ROOT, "68k")]
                       + list(defines) + ["-o", out, os.path.join(ROOT, "68k", source)],
                       capture_output=True)
    assert r.returncode == 0, r.stdout.decode() + r.stderr.decode()
    symbols = Symbols()
    text = set()
    for line in open(lst):
        parts = line.split()
        for i in range(0, len(parts) - 2, 3):
            # a label of the text section, or an equate, which rmac lists
            # as an absolute: an offset a macro writes is here alone,
            # since the source has no line to read it off
            if parts[i + 2] in ("t", "a") and len(parts[i + 1]) == 16:
                try:
                    symbols.setdefault(parts[i], int(parts[i + 1], 16))
                except ValueError:
                    continue
                if parts[i + 2] == "t":
                    text.add(parts[i])
    symbols.text = frozenset(text)
    return open(out, "rb").read(), symbols


class Symbols(dict):
    """An assembly's symbols, each name's value, and in text the names
    that are labels of the text section: an offset of the code, which a
    link moves (doc/LINKER.md), where every other symbol is an equate."""
    text = frozenset()


CORPUS = os.environ.get("YM_CORPUS",
                        os.path.expanduser("~/git/jatari/data/ym_format"))

# How many tunes -corpus reads where no count follows it. Forty is about
# eleven minutes, which is a net a reader runs and waits for.
CORPUS_TUNES = 40

# The tunes the check of init's patching reads to build one set, and how
# many of those it is in the set: it inits every ordered pair, so the
# second bounds the work, and the first bounds what a run converts for it.
SET_READS = 24
SET_TUNES = 10

# The frames a tune is played for at most, which -frames sets. None plays
# every tune whole, as a full run does.
MOST_FRAMES = None

# The checks a run makes after it builds the player and before it plays the
# tunes, in their order, by the names -only reads: the SNDH cores, the two
# sets of subtunes, and one each of the functions below.
CHECKS = ("cores", "sets", "unplaced", "deferred", "mapped", "fallbacks",
          "deferred_sizes", "mapped_sizes", "absolute_sizes", "halves", "voices", "widekept",
          "envelope", "wholebyte", "outofreach")


def spread(most):
    """A spread of `most` tunes off the corpus: every Nth file by name, so
    a sample covers the corpus rather than one composer's run of it."""
    if not os.path.isdir(CORPUS):
        raise SystemExit("no corpus at " + CORPUS + ": YM_CORPUS names it")
    # a dump is a file: the corpus has a directory named like one in it,
    # and a converter reading it stops the run
    every = sorted(os.path.join(CORPUS, f) for f in os.listdir(CORPUS)
                   if f.lower().endswith(".ym")
                   and os.path.isfile(os.path.join(CORPUS, f)))
    if not every:
        raise SystemExit("no .ym file under " + CORPUS)
    return every[::max(1, len(every) // most)][:most]


class AnotherFormat(Exception):
    """A dump the converter reads as another format: it reads a YM3!, a
    YM3b, a YM5! or a YM6! dump, packed or plain (doc/tools.md 5.1), and a
    corpus may have a file of another in it. Such a file is outside a
    run rather than among the tunes that played wrong."""


def convert(ym, work):
    """A tune file out of a YM dump, through the converter; a tune file
    named as it is."""
    if ym.endswith(".ymxr"):
        return open(ym, "rb").read(), ""
    flags = os.environ.get("YMXR_FLAGS", "").split()
    r = subprocess.run([os.path.join(ROOT, "bin", "ym-to-ymxr")] + flags,
                       stdin=open(ym, "rb"), capture_output=True)
    if r.returncode != 0 and "not a YM3!/YM3b/YM5!/YM6! file" in r.stderr.decode():
        raise AnotherFormat(r.stderr.decode().strip().splitlines()[-1])
    assert r.returncode == 0, r.stderr.decode()
    # The tool reports on standard error, the last line being what it wrote.
    said = [line for line in r.stderr.decode().splitlines() if line.startswith("ym-to-ymxr: ")]
    return r.stdout, said[-1][len("ym-to-ymxr: "):] if said else ""


# The bound tune of each tune file a run binds, and the text of each table
# it reads back: a tool writes the same bytes for the same input, so each
# is run once a run.
_BOUND = {}
_TEXT = {}


def bind(file, work):
    """The bound tune of a tune file, through bin/ymxr-bind; None where the
    binder rejects the file, which it reports on a separate line."""
    if file not in _BOUND:
        r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-bind"), "-silent"],
                           input=file, capture_output=True)
        if r.returncode == 1 and r.stderr.startswith(b"ymxr-bind: "):
            _BOUND[file] = None
        else:
            assert r.returncode == 0, r.stderr.decode()
            _BOUND[file] = r.stdout
    return _BOUND[file]


def trace(file, work, calls):
    """What the Java reader reports of a tune file, one entry a call
    (SPEC.md 7), through bin/ymxr-trace."""
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-trace"), "-r%d" % calls, "-silent"],
                       input=file, capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    return [json.loads(line) for line in r.stdout.decode().splitlines() if line]


def entry(model, writes):
    """The reader's entry for a frame the model just stepped: the writes
    the chip reads, and the effects the row set."""
    w = {}
    for reg, value in masked(writes):
        # R7's two host bits belong to the host: the reader reports bits 5 to 0
        w[str(reg)] = value & 0x3F if reg == 7 else value
    e = {}
    for i in range(4):
        fx = model.fx[i]
        if not fx["set"]:
            continue
        kept = fx["kept"]
        one = {"target": fx["target"], "source": kept["source"]}
        one["select"] = kept["select"]
        one["count"] = kept["count"]
        one["timer"] = bool(fx["bits"] & 0x40)
        one["place"] = bool(fx["bits"] & 0x20)
        one["deferred"] = bool(fx["bits"] & 0x08)
        if model.tune.version == MAPPED:
            # the kept mapping after the row (SPEC.md 7.3)
            one["mapping"] = fx["mapping"]
        e[str(i)] = one
    return {"result": 0, "w": w, "e": e}


def long_at(d, at):
    return struct.unpack(">I", d[at:at + 4])[0]


class Tune:
    """A bound tune read back: the header, the rows out of the image's
    table through dtx-write, and the sources' rows."""

    def __init__(self, file, work):
        assert file[:4] == b"YMXB", "not a bound tune"
        self.version = struct.unpack(">H", file[4:6])[0]
        self.rate = struct.unpack(">H", file[6:8])[0]
        self.effects = file[8]
        count = file[9]
        self.state = long_at(file, 12)
        self.image_at = long_at(file, 16)
        # Where this tune's table is in the image it is packaged
        # into: an image of one names it in its format block, and one
        # shared by several names the first, so a bound tune records a
        # separate offset
        # (BINARIES.md 2, DTX abi.md 1).
        self.table_at = long_at(file, 20)
        # Version 10 points to its transformation block with a signed
        # long at 24; its source index starts at 28 (BINARIES.md 1.2).
        # The block has word T, T relative offsets, then word E and E
        # entries per table. Transformation n is transformations[n - 1]
        # (SPEC.md 3.4.2).
        self.transformations = []
        # the pages the tune maps through, after the block's index and its
        # last transformation: (effect, form, transformation) a page, the
        # forms 0 plain, 1 marked and 2 setR7's ports (BINARIES.md 1.2)
        self.pages = []
        index_at, end = 24, len(file)
        if self.version == MAPPED:
            block = struct.unpack(">i", file[24:28])[0]
            index_at, end = 28, block
            t = struct.unpack(">H", file[block:block + 2])[0]
            last = block + 2 + 4 * t
            for n in range(t):
                at = block + long_at(file, block + 2 + 4 * n)
                e = struct.unpack(">H", file[at:at + 2])[0]
                self.transformations.append(list(file[at + 2:at + 2 + e]))
                last = max(last, at + 2 + e)
            # the list's tag, and a tune without one has no list
            if file[last:last + 2] == LIST_TAG:
                self.pages = [tuple(file[last + 3 + 3 * k:last + 6 + 3 * k])
                              for k in range(file[last + 2])]
            else:
                self.pages = None
        # bit 31 of an index entry marks a source whose column fills its
        # byte: its rows carry no marker and a player counts them
        # (SPEC.md 3.1).
        entries = [long_at(file, index_at + 4 * i) for i in range(count)]
        index = [at & ~COUNTED for at in entries]
        self.counted = [False] + [bool(at & COUNTED) for at in entries]
        # The reader image follows sources and ends at the optional
        # transformation block, or at the bound file's end (BINARIES.md
        # 1.2).
        self.image_end = end
        image = file[self.image_at:end]
        table_at = self.table_at
        csv = os.path.join(work, "table.csv")
        # dtx-write reads its input on standard input and writes its
        # output on standard output (DTX, doc/tools.md).
        if image[table_at:] not in _TEXT:
            r = subprocess.run([DTX_WRITE, "-text"], input=image[table_at:],
                               capture_output=True)
            assert r.returncode == 0, r.stderr.decode()
            _TEXT[image[table_at:]] = r.stdout
        open(csv, "wb").write(_TEXT[image[table_at:]])
        self.rows = []
        for line in open(csv):
            if line.startswith("#") or line.startswith("c0") or not line.strip():
                continue
            self.rows.append([int(x) for x in line.split(",")])
        header = image[table_at:table_at + 16]
        self.R = long_at(header, 4)
        self.RR = long_at(header, 10)
        assert len(self.rows) == self.R
        # a source: (the offset of its first row in the file, R, RR, a
        # column a value of the row). DTX1 lays a table out column by
        # column and pads each column to a word, so column i is a
        # stride of (R + 1) & ~1 from the one before it (SPEC.md 3.1.3).
        self.sources = [None]
        for at in index:
            r_, rr = long_at(file, at + 4), long_at(file, at + 10)
            c = struct.unpack(">H", file[at + 8:at + 10])[0]
            stride = (r_ + 1) & ~1
            columns = [list(file[at + 16 + i * stride:at + 16 + i * stride + r_])
                       for i in range(c)]
            self.sources.append((at + 16, r_, rr, columns))


class Model:
    """SPEC.md 4 and 5 over the tune's rows: the chip writes a frame makes,
    the timers' state after it, and each tick's write."""

    def __init__(self, tune):
        self.tune = tune
        self.row = 0
        # the row the frame plays, read by begin and used by the steps
        # and the writes below
        self.playing = None
        # target is kept for later starts; using is the running target.
        # pending is an armed deferred start or None. mapping is kept
        # for later starts; run is used by current ticks. Mapping 0 is
        # identity; n selects transformation n (SPEC.md 1.10, 4.8).
        self.fx = [dict(target=0, using=0, source=0, select=0, count=0, place=None,
                        running=False, pending=None, mapping=0, run=0)
                   for _ in range(4)]

    def frame(self):
        """(the chip writes in order, the effects' state after the row)."""
        self.begin()
        for i in range(4):
            self.step(i)
        return self.writes()

    def begin(self):
        """The row this frame plays, read and the next one placed."""
        self.playing = self.tune.rows[self.row]
        self.row += 1
        if self.row == self.tune.R:
            self.row = self.tune.RR

    def step(self, i):
        """Effect i's four columns. The four effects are stepped in order,
        and every chip write of the row comes after all four, so a tick
        between two steps reads the effects before it as the row leaves
        them and the ones after it as they were (SPEC.md 4.2.1)."""
        r = self.playing
        t = EFFECT + 4 * i
        fx = self.fx[i]
        # Version 10 has 34 columns, including mapping columns.
        # Thirty-column tables use identity throughout (SPEC.md 1.10.1).
        m = r[C + i] if len(r) > C else 0
        if r[t] & 0x80:
            fx["target"] = r[t] & 0x7F
        fx["restart"] = False
        # Whether this row writes the timer a select. A stopped timer starts
        # on one, bit 6 set or clear (SPEC.md 1.9), so a restart is due for
        # a stopped timer on the select alone.
        fx["selected"] = False
        fx["reset_place"] = False
        fx["set"] = bool((r[t] | r[t + 1] | r[t + 2] | m) & 0x80) or r[t + 3] != 0
        if not r[t + 1] & 0x80 and m & 0x80:
            # Remap changes kept and running mappings, preserving
            # source, place and rate. Use timer state before any reset
            # in this row. A stopped timer remains stopped; a pending
            # start retains its captured mapping (SPEC.md 1.10.2,
            # 1.10.3).
            fx["mapping"] = m & 0x7F
            if fx["running"]:
                fx["run"] = fx["mapping"]
        elif r[t + 1] & 0x7F and m & 0x80:
            # A start can replace kept mapping. A stop ignores its
            # mapping column (SPEC.md 1.10.3).
            fx["mapping"] = m & 0x7F
        # What the reader records of the row (SPEC.md 7.3): the source the
        # last row that set the column named, the kept select and count,
        # and the control bits the row sets. A deferred start's are the
        # row's, where the timer runs on the effect before it until the
        # switch, which the fields below follow.
        kept = fx.setdefault("kept", dict(source=0, select=0, count=0))
        fx["bits"] = r[t + 2] if r[t + 2] & 0x80 else 0
        if r[t + 1] & 0x80:
            kept["source"] = r[t + 1] & 0x7F
        if r[t + 2] & 0x80:
            kept["select"] = r[t + 2] & 7
            if r[t + 3] or r[t + 2] & 0x10:
                kept["count"] = r[t + 3]
        elif r[t + 3]:
            kept["count"] = r[t + 3]
        if r[t + 1] & 0x80:
            source = r[t + 1] & 0x7F
            if r[t + 2] & 0x80 and r[t + 2] & 0x08 and source and fx["running"]:
                # A deferred start on a running timer is armed: the row
                # leaves the timer, its source, its rate and its place as
                # they are, and the tick that reads the running source's
                # last row performs it, at row 0 with bit 5 and otherwise
                # at the running source's R modulo R of this source, the
                # count of the rows the running source played (SPEC.md 4.8).
                was = self.tune.sources[fx["source"]][1]
                R = self.tune.sources[source][1]
                fx["pending"] = dict(
                    source=source, using=fx["target"],
                    place=0 if r[t + 2] & 0x20 else was % R,
                    select=r[t + 2] & 7,
                    count=r[t + 3] if r[t + 3] or r[t + 2] & 0x10 else fx["count"],
                    mapping=fx["mapping"])
                return
            # a stop and a start at the frame drop a pending start (4.8)
            fx["pending"] = None
            fx["source"] = source
            if source == 0:
                # a row that stops an effect moves no place, so a source
                # that starts again reads from it (SPEC.md 1.9)
                fx["running"] = False
            else:
                at, R, RR, columns = self.tune.sources[source]
                # row 0 where the row sets bit 5 of the control column, and
                # where it does not, the row number already in the place
                # modulo R of this source (SPEC.md 1.8.4)
                if r[t + 2] & 0x20 or fx["place"] is None:
                    fx["place"] = 0
                else:
                    fx["place"] %= R
                fx["using"] = fx["target"]
                # Use the explicit mapping or reuse kept when its column
                # is unset (SPEC.md 1.10.2).
                fx["run"] = fx["mapping"]
                # The effect runs from here: a stopped timer starts on the
                # select the row writes, bit 6 set or clear, and select 0 is
                # unassigned in that column, so a row naming a source writes
                # its timer a select that runs it (SPEC.md 1.9).
                fx["running"] = True
        if r[t + 2] & 0x80:
            if r[t + 2] & 0x40:
                fx["restart"] = True
                fx["running"] = True
            # bit 4 marks the count column's 0 as the value the MFP counts
            # 256 for, and a player reads it where the row sets this column
            # (SPEC.md 1.1, 1.9)
            if r[t + 3] or r[t + 2] & 0x10:
                fx["count"] = r[t + 3]
            fx["select"] = r[t + 2] & 7
            fx["selected"] = bool(r[t + 2] & 7)
            if r[t + 2] & 0x20:
                fx["reset_place"] = True
                fx["place"] = 0
        elif r[t + 3]:
            fx["count"] = r[t + 3]

    def sets_select(self, i):
        """Whether effect i's step writes the timer's control register:
        the row writes the select, or the source column stops the effect.
        Timers C and D share the register, and a write to it is read
        against this."""
        r = self.playing
        t = EFFECT + 4 * i
        return bool(r[t + 2] & 0x80) or (r[t + 1] & 0x80 and not r[t + 1] & 0x7F)

    def writes(self):
        """The chip writes the row makes, in order. They come after all
        four effect steps."""
        r = self.playing
        writes = []
        for fine, coarse in ((0, 1), (2, 3), (4, 5)):
            if r[fine] != 0 or r[coarse] & 0x40:
                writes.append((fine, r[fine]))
            if r[coarse] & 0x80:
                writes.append((coarse, r[coarse] & 0x7F))
        if r[6] & 0x80:
            writes.append((6, r[6] & 0x7F))
        if r[11] != 0 or r[13] & 0x40:
            writes.append((11, r[11]))
        if r[12] != 0 or r[13] & 0x20:
            writes.append((12, r[12]))
        for v in (8, 9, 10):
            if r[v] & 0x80:
                writes.append((v, r[v] & 0x7F))
        if r[7] & 0x80:
            writes.append((7, (r[7] & 0x3F) | 0xC0))
        if r[13] & 0x80:
            writes.append((13, r[13] & 0x7F))
        return writes

    def tick(self, i):
        """The writes a tick of effect i makes, one a register of its
        target, and what follows: 'on', 'loop' or 'stop'."""
        fx = self.fx[i]
        at, R, RR, columns = self.tune.sources[fx["source"]]
        write = self.ticking(i)
        # A source whose column fills its byte ends on the row its count
        # runs out at; every other ends on the marker, bit 7 of the
        # column written last, the marker's column of the row (SPEC.md
        # 3.2, 5.1).
        if self.tune.counted[fx["source"]]:
            last = fx["place"] == R - 1
        else:
            last = bool(columns[TARGETS[fx["using"]][-1][1]][fx["place"]] & 0x80)
        if last:
            pending = fx["pending"]
            if pending:
                # At the last source row, install a pending start even
                # for a repeating source. The next tick reads the new
                # source through its captured mapping; kept mapping
                # remains unchanged (SPEC.md 4.8, 1.10.3).
                fx.update(source=pending["source"], using=pending["using"],
                          place=pending["place"], select=pending["select"],
                          count=pending["count"], running=True, pending=None,
                          run=pending["mapping"])
                return write, "switch"
            if RR < R:
                fx["place"] = RR
                return write, "loop"
            fx["running"] = False
            return write, "stop"
        fx["place"] += 1
        return write, "on"

    def ticking(self, i):
        """The writes a tick of effect i would make, the place left where
        it is."""
        fx = self.fx[i]
        if not fx["running"]:
            return None
        at, R, RR, columns = self.tune.sources[fx["source"]]
        return [(register, self.through(i, c, columns[c][fx["place"]]))
                for register, c in TARGETS[fx["using"]]]

    def through(self, i, c, byte):
        """Apply the running mapping to source byte in column c. Identity
        and earlier target columns preserve the byte. For the target's
        last value, select an entry by key, preserving marker bit 7 or
        setR7 port bits 7 and 6 as applicable (SPEC.md 2.1.5). Other
        columns use the full byte key.
        """
        fx = self.fx[i]
        if not fx["run"] or c != LAST_VALUE[fx["using"]]:
            return byte
        entries = self.tune.transformations[fx["run"] - 1]
        counted = self.tune.counted[fx["source"]]
        if counted and fx["using"] == 7:
            assert byte & 0xC0 == 0xC0, \
                "a row of %02x on setR7 through a transformation, its port bits clear" % byte
            key, keep = byte & 0x3F, 0xC0
        elif not counted and c == TARGETS[fx["using"]][-1][1]:
            key, keep = byte & 0x7F, byte & 0x80
        else:
            key, keep = byte, 0
        assert key < len(entries), "key %d of transformation %d of %d entries" % (
            key, fx["run"], len(entries))
        return entries[key] | keep

    def fill(self, i):
        """Lookup page encoding for effect i: ports for counted setR7,
        mirror for a mapped marker column, plain otherwise
        (68k/player/lookup_pages.S, ymxr_fill).
        """
        fx = self.fx[i]
        counted = self.tune.counted[fx["source"]]
        if counted and fx["using"] == 7:
            return "ports"
        if not counted and LAST_VALUE[fx["using"]] == TARGETS[fx["using"]][-1][1]:
            return "mirror"
        return "plain"

    def moved(self, i):
        """The write a tick of effect i makes before the row's step and
        the write it makes after it, where the row moves one to the
        other, and None where the row leaves it as it is. Read before the
        frame, so the row is the one the next frame plays: a step moves
        one effect alone, and the steps before it leave this one as
        the frame before left it."""
        was = self.ticking(i)
        if was is None:
            return None
        keep, playing = copy.deepcopy(self.fx), self.playing
        self.playing = self.tune.rows[self.row]
        self.step(i)
        then = self.ticking(i)
        self.fx, self.playing = keep, playing
        return (was, then) if then is not None and then != was else None

    def counted(self, i):
        """Whether effect i's source has its column filling its byte, which
        the player's counted handler runs (68k/player/ticks.S, TICKC)."""
        fx = self.fx[i]
        return fx["source"] != 0 and self.tune.counted[fx["source"]]

    def square(self, i):
        """Whether effect i's source is two rows repeating to row 0, which
        the player's square handler uses (68k/player/ticks.S, SQUARE). A
        counted source runs the counted handler at any shape."""
        fx = self.fx[i]
        if fx["source"] == 0 or self.counted(i):
            return False
        at, R, RR, columns = self.tune.sources[fx["source"]]
        return len(columns) == 1 and R == 2 and RR == 0

    def onerow(self, i):
        """Whether effect i's source is one row repeating, which the
        player's one-row handler uses (68k/player/ticks.S, ONEROW)."""
        fx = self.fx[i]
        if fx["source"] == 0 or self.counted(i):
            return False
        at, R, RR, columns = self.tune.sources[fx["source"]]
        return len(columns) == 1 and R == 1 and RR == 0

    def value(self, i, row=None):
        """Value at the handler's immediate place, or explicit row, through
        its mapping. Square and one-row handlers both use one-column
        sources.
        """
        fx = self.fx[i]
        at, R, RR, columns = self.tune.sources[fx["source"]]
        return self.through(i, 0, columns[0][fx["place"] if row is None else row])

    def wide(self, i):
        """The columns of effect i's source where it has several, which
        the player's handlers of several registers run (68k/player/ticks.S,
        TICKW), and 0 where it is one column."""
        fx = self.fx[i]
        if fx["source"] == 0:
            return 0
        columns = len(self.tune.sources[fx["source"]][3])
        return columns if columns > 1 else 0

    def left(self, i):
        """The rows a counted handler has left: from the row its place
        is at to the end of the source (68k/player/ticks.S, TICKC)."""
        fx = self.fx[i]
        at, R, RR, columns = self.tune.sources[fx["source"]]
        return R - fx["place"]

    def place_address(self, i):
        """The address the running handler's place is at: the row it
        reads in the column it writes first (68k/player/ticks.S, TICKW)."""
        fx = self.fx[i]
        if fx["place"] is None or fx["source"] == 0:
            return None
        at, R, RR, columns = self.tune.sources[fx["source"]]
        stride = (R + 1) & ~1
        return FILE + at + TARGETS[fx["using"]][0][1] * stride + fx["place"]


class Machine:
    """Emulated 68000 with chip and MFP writes recorded per call.

    A lookup tick modifies the next instruction's operand. Unicorn
    translates blocks in advance, so cached code would use the previous
    key. Mark key-byte writes, invalidate that instruction's translation
    and restart at its address. The cycle counter records it once
    (68k/player/lookup_ticks.S, LKEY).

    Load the bound tune at this machine's FILE address, computed beyond
    its code by placed. Callers read FILE after construction.
    """

    def __init__(self, code, symbols, tune_bytes):
        global FILE
        # the page area right after the player, as the tools place it after
        # the core (BINARIES.md 3.1), and the tune past it; with AREA_AFTER
        # the area past the tune, within the reach that is left
        if AREA_AFTER:
            FILE = placed(len(code))
            at = (FILE + len(tune_bytes) + 1) & ~1
        else:
            at = CODE + ((len(code) + 1) & ~1)
        pages = area_of(tune_bytes, symbols, at) or 0
        self.area = at if pages else 0
        self.reach = reach_of(symbols, at) if pages else 0
        self.pages = (self.area, self.area + pages)
        self.filling = False
        # the fallback pages a frame fills (ymxr_lfill), (first, past) each
        self.falls = []
        if pages:
            count, entries = list_of(tune_bytes)
            listed = type("Listed", (), {"pages": entries, "transformations": [0] * count})
            for effect in range(4):
                one = page_at(symbols, self.area, listed, effect, 9, 0, self.reach)
                if one:
                    self.falls.append((min(one), max(one) + 256))
        if not AREA_AFTER:
            FILE = placed(((len(code) + 1) & ~1) + pages)
        self.symbols = symbols
        self.size = len(code)
        # the key byte of every lookup instance: its page read's
        # displacement, low byte
        self.keys = {CODE + at + 3 for name, at in symbols.items()
                     if re.match(r"ymxr_l[kctnbvef][0-3]b?_key$", name)}
        self.stale = False
        self.restart = None
        self.counter = None
        self.until = None
        self.ending = None
        mu = Uc(UC_ARCH_M68K, UC_MODE_BIG_ENDIAN)
        mu.ctl_set_cpu_model(UC_CPU_M68K_M68000)
        # The code and the tune after it, to the workspace: -abs loads the
        # tune further off (placed).
        for at, size in ((0, 0x1000), (CODE, WORK - CODE),
                         (WORK, 0x40000), (STACK, 0x10000), (DONE, 0x1000),
                         (0xFFFF8000, 0x1000), (0xFFFFF000, 0x1000)):
            mu.mem_map(at, size)
        mu.mem_write(CODE, code)
        mu.mem_write(FILE, tune_bytes)
        # the host's MFP: Timer C running for the system at 200 Hz, D
        # stopped, both enabled and unmasked as TOS leaves them
        mu.mem_write(MFP + 0x1D, bytes([0x50]))
        mu.mem_write(MFP + 0x23, bytes([192]))
        mu.mem_write(MFP + 0x09, bytes([0x30]))
        mu.mem_write(MFP + 0x15, bytes([0x30]))
        self.mu = mu
        self.psg = []
        self.mfp = []
        self.select = None
        self.stray = []
        self.palette = []
        # The image of a bound tune: DTX's init writes the ring's size into
        # the decoder's code where the table has copies (DTX, abi.md 2), so
        # a write there is the reader's, wherever the tune is loaded.
        self.images = []
        self.image(FILE, tune_bytes)
        mu.hook_add(UC_HOOK_MEM_WRITE, self._write)
        self.rte_at = None
        self.stopped = None
        self.beam = 0
        mu.hook_add(UC_HOOK_CODE, self._code)
        # the video address counter, which moves while the chip fetches
        # pixels: the raster monitor waits for it, and a value that never
        # moved would run the wait to its ceiling
        mu.hook_add(UC_HOOK_MEM_READ, self._beam, begin=VIDEO, end=VIDEO + 1)

    def image(self, at, tune_bytes):
        """Reader image in the bound tune at at, ending at the optional
        version 10 transformation block or file end (BINARIES.md 1.2).
        """
        if tune_bytes[:4] == b"YMXB":
            image_at = struct.unpack(">i", tune_bytes[16:20])[0]
            end = len(tune_bytes)
            if struct.unpack(">H", tune_bytes[4:6])[0] == MAPPED:
                end = struct.unpack(">i", tune_bytes[24:28])[0]
            if image_at > 0:
                self.images.append((at + image_at, at + end))

    def _beam(self, mu, access, address, size, value, data):
        self.beam = (self.beam + 1) & 0xFF
        mu.mem_write(VIDEO, bytes([self.beam]))

    def _code(self, mu, address, size, data):
        if self.stale:
            # the instruction after a key's write: its block translated
            # again before it runs
            self.stale = False
            mu.ctl_remove_cache(address, address + size)
            self.restart = address
            mu.emu_stop()
        elif self.rte_at is not None and bytes(mu.mem_read(address, 2)) == b"\x4e\x73":
            self.rte_at = address
            mu.emu_stop()
        elif address == self.stopped and (self.until is None or self.until(mu)):
            mu.emu_stop()               # the call stopped at a boundary
        elif self.ending == "next":
            self.ending = None          # the window ended (window_end)
            mu.emu_stop()
        elif self.ending and bytes(mu.mem_read(address, 2)) == b"\x46\xdf":
            self.ending = "next"

    def _write(self, mu, access, address, size, value, data):
        if PSG <= address < PSG + 4:
            for lane in range(size):
                byte = (value >> (8 * (size - 1 - lane))) & 0xFF
                at = address + lane
                if at == PSG:
                    self.select = byte
                elif at == PSG + 2:
                    self.psg.append((self.select, byte))
        elif MFP <= address < MFP + 0x40:
            for lane in range(size):
                byte = (value >> (8 * (size - 1 - lane))) & 0xFF
                self.mfp.append((address + lane, byte))
        elif PERF and PALETTE <= address < PALETTE + 2:
            # a mark of the raster monitor: the colour, and how many chip
            # writes of the call are before it
            self.palette.append((value & 0xFFFF, len(self.psg)))
        elif address < 0x1000 or CODE <= address < CODE + self.size:
            # a vector, or the player patching itself
            if any(at in self.keys for at in range(address, address + size)):
                self.stale = True
        elif any(lo <= address < hi for lo, hi in self.images):
            pass                        # DTX's init writing its decoder
        elif self.pages[0] <= address < self.pages[1] and self.filling:
            pass                        # init writing the page area
        elif any(lo <= address < hi for lo, hi in self.falls):
            pass                        # a fill of a fallback page
        elif not (WORK <= address < WORK + 0x40000 or STACK <= address < STACK + 0x10000):
            self.stray.append((address, size, value))

    def call(self, name, a0=0, a1=0, d0=0, at=None, until=None):
        """One call through the jump table, back at the sentinel. The
        core's three entries are in another order than the player's
        (BINARIES.md 2), so each set has a name a slot.

        With at, the call stops at that address and resume runs it on:
        a tick fired between the two falls there (SPEC.md 4.2.1), and the
        call's chip writes keep the order the tick fell in. With until as
        well, it stops there where until(mu) is true, so a routine every
        effect calls stops for one effect."""
        mu = self.mu
        slot = {"init": 0, "play": 4, "stop": 8,
                "core-init": 0, "core-exit": 4, "core-play": 8}[name]
        mu.reg_write(UC_M68K_REG_D0, d0)
        mu.reg_write(UC_M68K_REG_D6, 0x6D6D6D6D)
        mu.reg_write(UC_M68K_REG_D7, 0x7D7D7D7D)
        mu.reg_write(UC_M68K_REG_A6, 0x00046000)
        mu.reg_write(UC_M68K_REG_A0, a0)
        mu.reg_write(UC_M68K_REG_A1, a1)
        # the page area init fills, 0 where the tune needs none (5.1)
        mu.reg_write(UC_M68K_REG_A2, self.area if name == "init" else 0)
        self.filling = name == "init"
        mu.reg_write(UC_M68K_REG_SR, 0x2000)
        sp = STACK + 0x8000
        mu.mem_write(sp - 4, struct.pack(">I", DONE))
        mu.reg_write(UC_M68K_REG_A7, sp - 4)
        self.psg, self.mfp, self.stray, self.palette = [], [], [], []
        self.stopped, self.until = at, until
        self._run(CODE + slot, name)
        if at is not None:
            assert mu.reg_read(UC_M68K_REG_PC) == at, \
                "%s ran past %x" % (name, at)
            return None
        return self._returned(name)

    def resume(self, name="play"):
        """The call stopped at an address, run on to the sentinel."""
        at, self.stopped, self.until = self.stopped, None, None
        assert at is not None, "no call is at an address"
        self._run(self.mu.reg_read(UC_M68K_REG_PC), name)
        return self._returned(name)

    def window_end(self):
        """The stopped call run on to the instruction after the move.w
        (sp)+,sr that ends its window at level 7, where a tick due inside
        the window is served. The run is one emu_start: the status register
        read between two single steps leaves unicorn's flags of the
        instruction before it wrong for the branch after it."""
        mu = self.mu
        self.stopped, self.until, self.ending = None, None, True
        self._go(mu.reg_read(UC_M68K_REG_PC), DONE, 50_000_000)
        assert self.ending is None, "the call ran past its window's end"
        self.stopped = mu.reg_read(UC_M68K_REG_PC)

    def _go(self, at, until, count):
        """emu_start from `at`, started again where a key's write stopped
        it: the instruction there is translated afresh, and the cycle
        counter settles it once."""
        while True:
            self.mu.emu_start(at, until, count=count)
            if self.restart is None:
                return
            at, self.restart = self.restart, None
            counter = self.counter
            if counter is not None and counter.pending is not None \
                    and counter.pending[0] == at:
                # the counter read the instruction before the stop, and
                # reads it again where the run starts
                counter.pending = None

    def _run(self, at, name):
        try:
            self._go(at, DONE, 50_000_000)
        except UcError as bad:
            raise AssertionError("%s stopped: %s at pc %x"
                                 % (name, bad, self.mu.reg_read(UC_M68K_REG_PC)))

    def _returned(self, name):
        """The call back at the sentinel, with the registers it keeps."""
        mu = self.mu
        assert mu.reg_read(UC_M68K_REG_PC) == DONE, name + " did not return"
        assert not self.stray, name + " wrote outside its memory: " + repr(self.stray[:4])
        assert mu.reg_read(UC_M68K_REG_D6) == 0x6D6D6D6D, name + " moved d6"
        assert mu.reg_read(UC_M68K_REG_D7) == 0x7D7D7D7D, name + " moved d7"
        assert mu.reg_read(UC_M68K_REG_A6) == 0x00046000, name + " moved a6"
        return mu.reg_read(UC_M68K_REG_D0) & 0xFFFFFFFF

    def fire(self, vector):
        """One tick through its vector, run to the handler's rte, on the
        stack and the registers as they are: the frame the handler
        returns through is pushed below the stack pointer it finds, so a
        tick inside a stopped call leaves the call as it was."""
        mu = self.mu
        handler = long_at(bytes(mu.mem_read(vector, 4)), 0)
        pc, sr, sp = (mu.reg_read(UC_M68K_REG_PC), mu.reg_read(UC_M68K_REG_SR),
                      mu.reg_read(UC_M68K_REG_A7))
        mu.mem_write(sp - 6, struct.pack(">HI", sr, pc))
        mu.reg_write(UC_M68K_REG_A7, sp - 6)
        mu.reg_write(UC_M68K_REG_SR, 0x2600)
        self.rte_at = 0
        try:
            self._go(handler, DONE, 1000)
        except UcError as bad:
            raise AssertionError("a tick stopped: %s at pc %x" % (bad, mu.reg_read(UC_M68K_REG_PC)))
        assert self.rte_at, "the tick did not reach its rte"
        self.rte_at = None
        mu.reg_write(UC_M68K_REG_A7, sp)
        mu.reg_write(UC_M68K_REG_SR, sr)
        mu.reg_write(UC_M68K_REG_PC, pc)
        assert not self.stray, "a tick wrote outside its memory: " + repr(self.stray[:4])

    def interrupt(self, vector):
        """One tick through its vector, outside any call."""
        mu = self.mu
        mu.reg_write(UC_M68K_REG_A7, STACK + 0x8000)
        mu.reg_write(UC_M68K_REG_SR, 0x2000)
        mu.reg_write(UC_M68K_REG_PC, DONE)
        self.psg, self.mfp, self.stray = [], [], []
        self.fire(vector)

    def byte(self, at):
        return bytes(self.mu.mem_read(at, 1))[0]

    def place(self, at, off):
        """The row a handler reads: the field is the row's address, and
        under the PC-relative build its displacement from the field."""
        if not PCREL:
            return self.long(at + off)
        one = self.word(at + off)
        return at + off + (one - 0x10000 if one >= 0x8000 else one)

    def word(self, at):
        return struct.unpack(">H", self.mu.mem_read(at, 2))[0]

    def long(self, at):
        return long_at(bytes(self.mu.mem_read(at, 4)), 0)


class Timers:
    """The MFP's four timers as the player programs them, in MFP clocks:
    which run, at what period, and when each next fires. Fed the MFP
    writes as they come, in order."""

    def __init__(self, effects, mode, count, ier, imr):
        self.effects = effects          # the effects the tune runs: only
                                        # their timers are the player's
        self.mode = list(mode)          # the control field, 0 stopped
        self.count = list(count)
        self.pending = [None] * 4
        self.loaded = [False] * 4       # the data register written while
                                        # the control register is 0
        self.phase = [float(PRESCALER[self.mode[i] & 7] * self.count[i]) for i in range(4)]
        self.restarts = [0, 0, 0, 0]
        self.now = 0.0                  # where the writes fall: 0 at a
                                        # frame, a tick's time inside one
        self.ier = dict(ier)            # by register address
        self.imr = dict(imr)
        self.scaled = False             # a select written to a running
                                        # timer scales the time to its next
                                        # fire, as a real MFP's counter
                                        # runs on at the new prescaler

    @staticmethod
    def of_machine(m, effects):
        return Timers(effects,
                      [(m.byte(t["ctrl"]) >> t["shift"]) & 0x0F for t in TIMER],
                      [m.byte(t["data"]) or 256 for t in TIMER],
                      {0xFFFFFA07: m.byte(0xFFFFFA07), 0xFFFFFA09: m.byte(0xFFFFFA09)},
                      {0xFFFFFA13: m.byte(0xFFFFFA13), 0xFFFFFA15: m.byte(0xFFFFFA15)})

    def apply(self, writes):
        """The MFP writes of a call, in order.

        The main counter runs on through a stop: a control register of 0
        leaves it where it is and a select written back resumes from
        there, so a select alone moves the prescaler and no more. The
        data register written while the control register is 0 loads the
        counter, and the select after that is the start this counts."""
        for reg, value in writes:
            if reg in self.ier:
                self.ier[reg] = value
            elif reg in self.imr:
                self.imr[reg] = value
            for i, t in enumerate(TIMER):
                if reg == t["ctrl"]:
                    mode = (value >> t["shift"]) & 0x0F
                    started = mode and self.mode[i] == 0 and self.loaded[i]
                    if self.scaled and mode and self.mode[i] and mode != self.mode[i]:
                        self.phase[i] = self.now + (self.phase[i] - self.now) \
                            * PRESCALER[mode & 7] / PRESCALER[self.mode[i] & 7]
                    self.mode[i] = mode
                    if started:
                        self.restarts[i] += 1
                        self.phase[i] = self.now + self.period(i)
                        self.loaded[i] = False
                elif reg == t["data"]:
                    if self.mode[i] == 0:
                        self.count[i] = value or 256
                        self.pending[i] = None
                        self.loaded[i] = True
                    else:
                        self.pending[i] = value or 256

    def period(self, i):
        return PRESCALER[self.mode[i] & 7] * self.count[i]

    def enabled(self, i):
        t = TIMER[i]
        return bool(self.ier[t["ier"]] & 1 << t["bit"]) and bool(self.imr[t["ier"] + 12] & 1 << t["bit"])

    def live(self, i):
        return bool(self.effects & 1 << i) and self.mode[i] != 0 and self.enabled(i)

    def due(self, clocks):
        """The ticks due in the next `clocks`, each effect in turn as its
        time comes; a handler run in between may reprogram a timer."""
        while True:
            soonest = None
            for i in range(4):
                if self.live(i) and self.phase[i] < clocks:
                    if soonest is None or self.phase[i] < self.phase[soonest]:
                        soonest = i
            if soonest is None:
                break
            i = soonest
            if self.pending[i] is not None:
                self.count[i] = self.pending[i]
                self.pending[i] = None
            self.now = self.phase[i]
            self.phase[i] += self.period(i)
            yield i
            self.now = 0.0
        for i in range(4):
            if self.mode[i]:
                self.phase[i] -= clocks

    def expected(self, i, clocks):
        """How many ticks of effect i the next `clocks` have room for, the phase
        advanced: the count the real MFP fires, within one."""
        if not self.live(i):
            return 0
        n = 0
        while self.phase[i] < clocks:
            if self.pending[i] is not None:
                self.count[i] = self.pending[i]
                self.pending[i] = None
            self.phase[i] += self.period(i)
            n += 1
        self.phase[i] -= clocks
        return n


# The frames a boundary run plays at most. A tick is fired at a boundary
# on the first row that moves what the effect's tick writes, so a tune
# whose rows move few of them is read at the boundaries its first
# INSIDE_FRAMES rows reach.
INSIDE_FRAMES = 2000


def widens(tune, r, i):
    """Whether row r starts on effect i, through ymxr_widestart, a source
    of several columns or a counted source, of more than one row, with the
    timer running on, and leaves the source column of each effect before
    it unset."""
    t = EFFECT + 4 * i
    s = r[t + 1] & 0x7F if r[t + 1] & 0x80 else 0
    return bool(s) and tune.sources[s][1] > 1 \
        and (len(tune.sources[s][3]) > 1 or tune.counted[s]) \
        and r[t + 2] & 0xC0 != 0xC0 \
        and not any(r[EFFECT + 4 * j + 1] & 0x80 for j in range(i))


def aims(tune, r, i):
    """Whether row r starts on effect i, at the frame, a source the
    general handler runs, a column of more than two rows or of a loop,
    with the timer running on."""
    t = EFFECT + 4 * i
    s = r[t + 1] & 0x7F if r[t + 1] & 0x80 else 0
    if not s or tune.counted[s] or len(tune.sources[s][3]) != 1:
        return False
    _, R, RR, _ = tune.sources[s]
    return (R > 2 or RR != 0) and r[t + 2] & 0xC0 != 0xC0 and r[t + 2] & 0x88 != 0x88


def reaches(model, i, kind):
    """Whether the next frame reaches boundary kind from inside. Lookup
    cases are a page switch between transformations, a lookup-start
    window and a square-level remap. Each requires a running timer
    without reset. Effect heads and the end of effect processing occur
    on every row.

    Identity starts also expose windows in ymxr_widestart for
    multi-column or counted sources, and in the general handler start.
    Earlier effects must leave their source columns unset so the
    selected effect reaches the shared routine first.
    """
    if kind is None:
        return True
    r = model.tune.rows[model.row]
    t = EFFECT + 4 * i
    if kind in ("wide", "wide end", "aim", "aim end"):
        # a start through a transformation runs ymxr_lstart in its place
        if model.tune.version == MAPPED:
            m = r[C + i]
            if m & 0x7F if m & 0x80 else model.fx[i]["mapping"]:
                return False
        if kind in ("aim", "aim end"):
            return aims(model.tune, r, i)
        return widens(model.tune, r, i)
    fx = model.fx[i]
    m = r[C + i]
    if r[t + 2] & 0x80 and r[t + 2] & 0x40:
        return False
    if kind in ("page", "square"):
        if r[t + 1] & 0x80 or not m & 0x80 or not fx["running"] or fx["pending"]:
            return False
        n = m & 0x7F
        if kind == "square":
            return model.square(i) and n != fx["run"]
        return (not model.square(i) and not model.onerow(i)
                and n != 0 and fx["run"] != 0 and n != fx["run"])
    source = r[t + 1] & 0x7F
    if not r[t + 1] & 0x80 or not source:
        return False
    if r[t + 2] & 0x80 and r[t + 2] & 0x08 and fx["running"]:
        return False
    if not (m & 0x7F if m & 0x80 else fx["mapping"]):
        return False
    at, R, RR, columns = model.tune.sources[source]
    shaped = not model.tune.counted[source] and len(columns) == 1 and R <= 2 and RR == 0
    # a marked source of one column starts in the window of the general
    # kind (ymxr_lkwindow), every other in the window of every kind
    one = not model.tune.counted[source] and len(columns) == 1
    return not shaped and one == (kind == "window one")


def inside(code, symbols, bound, tune, workspace):
    """Inject a tick at a frame boundary and compare with the model
    (SPEC.md 4.2.1). Before an effect step, ticks use its previous
    values; afterward they use the updated values. Continue playback
    around the boundary so source places advance normally. Return
    boundary kinds, with None for effect heads and the end of effect
    processing.

    Start windows in ymxr_widestart and the general handler cover place
    reads and writes. A tick before the window advances the old place
    before the start reads it. A tick due inside the window runs
    afterward on the new source.

    Lookup cases add the atomic page-byte switch, the lookup-start
    window and the square-level remap window. Before a page switch, a
    tick uses the previous mapping; ticks delayed by a level-7 window
    use the updated effect.
    """
    if not tune.effects:
        return []
    at = []
    for i in range(4):
        if tune.effects & 1 << i:
            # The boundaries inside a step first, since a few rows reach
            # them. The head of an effect the tune does not run is branched
            # over; the point after effect 3 is reached on every row.
            if "ymxr_look" in symbols and tune.version == MAPPED:
                # the remap's piece of the effect's frame code, which a
                # linked player keeps where the effect maps a value
                if "ymxr_tick%d_rpage" % i in symbols:
                    at.append((i, i, CODE + symbols["ymxr_tick%d_rpage" % i], "page"))
                at.append((i, i + 1, CODE + symbols["ymxr_lwindow"], "window"))
                at.append((i, i + 1, CODE + symbols["ymxr_lkwindow"], "window one"))
                at.append((i, i + 1, CODE + symbols["ymxr_rlevel"], "square"))
            if any(widens(tune, r, i) for r in tune.rows):
                # the window of the start of several columns, in a player
                # that has the routine: one linked for a tune without such
                # a source leaves it out (doc/LINKER.md)
                wide = CODE + next(a for a in range(symbols["ymxr_widestart"], len(code), 2)
                                   if code[a:a + 6] == b"\x40\xe7\x00\x7c\x07\x00")
                at.append((i, i, wide, "wide"))
                at.append((i, i + 1, wide + 6, "wide end"))
            if any(aims(tune, r, i) for r in tune.rows):
                lo = symbols["ymxr_tick%d_aim" % i]
                aim = CODE + next(a for a in range(lo, len(code), 2)
                                  if code[a:a + 6] == b"\x40\xe7\x00\x7c\x07\x00")
                at.append((i, i, aim, "aim"))
                at.append((i, i + 1, aim + 6, "aim end"))
            at.append((i, i, CODE + symbols["ymxr_tick%d_over" % i], None))
            at.append((i, 4, CODE + symbols["ymxr_tick3_done"], None))
    m = Machine(code, symbols, bound)
    model = Model(tune)
    timers = Timers.of_machine(m, tune.effects)
    assert m.call("init", a0=FILE, a1=workspace) == 0, "init rejected the tune"
    timers.apply(m.mfp)
    clocks = MFP_CLOCK / tune.rate
    left = list(at)
    fired = []
    for f in range(min(INSIDE_FRAMES, MOST_FRAMES or INSIDE_FRAMES)):
        if f == tune.R + tune.R - tune.RR:
            # a boundary inside a step is reached in a pass and a loop or
            # not at all
            left = [one for one in left if one[3] is None]
        if not left:
            break
        # the first boundary left whose effect this row moves: where the
        # row leaves every one of them as it is, the two readings at
        # each boundary are one and the row plays as a frame of a run
        pick = next(((one, moves) for one in left
                     for moves in [model.moved(one[0])]
                     if moves and reaches(model, one[0], one[3])), None)
        if pick is None:
            d0 = m.call("play", a0=workspace)
            want = model.frame()
        else:
            (i, step, address, kind), (was, then) = pick
            # a routine every effect calls stops for effect i, whose record
            # it reads through a3
            record = workspace + equate("WS_FX", symbols) + i * equate("FX_SIZE", symbols)
            m.call("play", a0=workspace, at=address,
                   until=None if kind in (None, "aim", "aim end") else
                   (lambda mu: mu.reg_read(UC_M68K_REG_A3) == record))
            level = (m.mu.reg_read(UC_M68K_REG_SR) >> 8) & 7
            if kind in ("window", "window one", "square", "wide end", "aim end"):
                assert level == 7, \
                    "frame %d: effect %d's %s boundary is at level %d, not inside its window" % (
                        f, i, kind, level)
                m.window_end()
                level = (m.mu.reg_read(UC_M68K_REG_SR) >> 8) & 7
            assert level < 6, "frame %d: effect %d's boundary at %x is at level %d" % (
                f, i, address, level)
            model.begin()
            for j in range(step):
                model.step(j)
            write, _ = model.tick(i)    # the place stepped, as a tick steps it
            assert write == (was if step <= i else then), \
                "frame %d: the model reads effect %d at boundary %d as %s, not %s" % (
                    f, i, step, write, (was, then))
            before = len(m.psg)
            m.fire(TIMER[i]["vector"])
            assert masked(m.psg[before:]) == masked(write), \
                "frame %d: a tick of effect %d at boundary %d%s wrote %s, not %s" % (
                    f, i, step, "" if kind is None else " (%s)" % kind, m.psg[before:], write)
            left.remove((i, step, address, kind))
            fired.append(kind)
            d0 = m.resume()
            for j in range(step, 4):
                model.step(j)
            want = write + model.writes()
        assert masked(m.psg) == masked(want), \
            "frame %d: the frame writes %s, not %s" % (f, m.psg, want)
        if d0 != 0:
            break                       # the tune has ended (4.6)
        timers.apply(m.mfp)
        for k in timers.due(clocks):
            write, _ = model.tick(k)
            m.interrupt(TIMER[k]["vector"])
            assert masked(m.psg) == masked(write), \
                "frame %d: a tick of effect %d wrote %s, not %s" % (f, k, m.psg, write)
            timers.apply(m.mfp)
    return fired


def built(name, tune):
    """A tune written here through YMXS's form rather than converted from a
    dump, as a file to play: ymxs-to-ymxr converts it, and the version word
    of the file comes back beside its path (SPEC.md 3.3.5)."""
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxs-to-ymxr"), "-silent"],
                       input=json.dumps(tune).encode(), capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    at = os.path.join(tempfile.mkdtemp(), name + ".ymxr")
    with open(at, "wb") as f:
        f.write(r.stdout)
    return at, struct.unpack(">H", r.stdout[4:6])[0]


def unplaced(code, symbols):
    """A start that moves no place, and no start on that timer before it:
    the place is row 0 of the source the row names (SPEC.md 4.1 step 4).

    The tune is written through YMXS's form with placeReset clear, which
    ymxs-to-ymxr writes as bit 5 at 0 (doc/ymxs.md). The model reads the
    place as row 0 and check reads the player against it.
    """
    rows = 8
    def column(at, value):
        return [value if r == at else -1 for r in range(rows)]
    tune = {"format": "ymxs", "version": 3, "tunes": [{
        "title": "A start that moves no place", "composer": "",
        "writer": "68k/test/emu/test_ymxr.py", "rate": 50, "rows": rows,
        "repeat": 0,
        "sources": [{"name": "four rows", "repeat": 0, "values": [9, 10, 11, 12]}],
        "registers": {"r7": column(0, 56), "r0": column(0, 100)},
        "timerA": {"shape": column(0, 0), "target": column(0, 8),
                   "source": column(0, 1), "prescaler": column(0, 50),
                   "count": column(0, 200), "timerReset": column(0, 1),
                   "placeReset": column(0, 0)}}]}
    at = built("unplaced", tune)[0]
    frames = check(at, code, symbols)[0]
    return "a start that moves no place is at row 0: %d frames" % frames


def effects_tune(title, rows, sources, timers, registers=None, repeat=0,
                 transformations=None):
    """Build YMXS JSON from timer operations. Each timer maps row numbers
    to start, retune or stop tuples; sources are numbered from 1 and
    contain name, values and repeat position.

    Without transformations, emit version 5. With (name, entries)
    tables, emit version 7: starts append a mapping after deferred, and
    ("remap", mapping) changes it. Mapping 0 is identity; a nonempty
    mapping lists one part per target value, 0 for identity or a table
    number (YMXS, json.md 6.7).
    """
    keys = ("shape", "target", "source", "prescaler", "count", "timerReset",
            "placeReset", "deferred") + (("mapping",) if transformations else ())

    def timer(ops):
        columns = {key: [-1] * rows for key in keys}
        for at, op in ops.items():
            if op[0] == "start":
                _, source, target, by, count, tr, pr, deferred = op[:8]
                for key, value in (("shape", 0), ("target", target), ("source", source),
                                   ("prescaler", by), ("count", count), ("timerReset", tr),
                                   ("placeReset", pr), ("deferred", deferred)):
                    columns[key][at] = value
                if transformations:
                    columns["mapping"][at] = op[8] if len(op) > 8 else 0
            elif op[0] == "retune":
                _, by, count, tr, pr = op
                for key, value in (("shape", 1), ("prescaler", by), ("count", count),
                                   ("timerReset", tr), ("placeReset", pr)):
                    columns[key][at] = value
            elif op[0] == "remap":
                columns["shape"][at] = 3
                columns["mapping"][at] = op[1]
            else:
                columns["shape"][at] = 2
        return columns
    tune = {"title": title, "composer": "", "writer": "68k/test/emu/test_ymxr.py",
            "rate": 50, "rows": rows, "repeat": repeat,
            "sources": [{"name": name, "repeat": rr, "values": values}
                        for name, values, rr in sources],
            "registers": registers or {"r7": [56] + [-1] * (rows - 1)}}
    if transformations:
        tune["transformations"] = [{"name": name, "entries": list(entries)}
                                   for name, entries in transformations]
    for letter, ops in timers.items():
        tune["timer" + letter] = timer(ops)
    return {"format": "ymxs", "version": 7 if transformations else 5, "tunes": [tune]}


def deferred(code, symbols):
    """Check deferred starts tick by tick (SPEC.md 4.8): the last row ends
    even a repeating source, installs the pending start, and the next
    tick reads the new source. Cover handler shapes in both banks,
    cancellation/replacement, stopped timers, place modulo and shared
    timer control.

    With lookups, also compare frame records. Check captured mappings in
    both banks for square, one-row and multi-column sources; remaps
    while pending; later reuse of kept; identity/lookup transitions; and
    replacement by another mapped start. Pending retains its mapping
    while remaps change current ticks (SPEC.md 1.10.3).
    """
    loop = ("loop of four", [15, 12, 9, 6], 0)
    fall = ("fall", list(range(15, -1, -1)), None)
    square = ("square", [15, 0], 0)
    other = ("square two", [13, 2], 0)
    held = ("one row", [11], 0)
    three = ("three rows", [10, 5, 1], 0)
    voice = ("voice A", [[0, 1, 15], [128, 1, 12], [0, 2, 9], [128, 2, 6]], 0)
    slow, quick = (200, 200), (50, 100)
    V10, V5, V0 = ([k * n // 15 for k in range(16)] for n in (10, 5, 0))
    two_maps, three_maps = [("V10", V10), ("V5", V5)], [("V10", V10), ("V5", V5), ("V0", V0)]
    def start(source, target=8, rate=slow, tr=1, pr=1, deferred=0, mapping=None):
        return ("start", source, target, rate[0], rate[1], tr, pr, deferred) + (
            () if mapping is None else (mapping,))
    def remap(mapping):
        return ("remap", mapping)
    cases = [
        ("a fall after a loop", [loop, fall],
         {"A": {0: start(1), 3: start(2, rate=quick, tr=0, deferred=1)}}),
        ("a fall after a loop, the place kept modulo R", [loop, fall],
         {"A": {0: start(1), 3: start(2, rate=quick, pr=0, deferred=1)}}),
        ("a loop after a loop, the general handler in both banks", [loop, three],
         {"A": {0: start(1), 2: start(2, pr=0, deferred=1), 12: start(1, pr=0, deferred=1)}}),
        ("a fall after a square", [square, fall],
         {"A": {0: start(1), 5: start(2, rate=quick, deferred=1)}}),
        ("a square after a square, both banks", [square, other],
         {"A": {0: start(1), 4: start(2, pr=0, deferred=1), 9: start(1, pr=0, deferred=1)}}),
        ("a square after a fall", [fall, square],
         {"A": {0: start(1), 1: start(2, deferred=1)}}),
        ("a fall after one row", [held, fall],
         {"A": {0: start(1), 3: start(2, rate=quick, deferred=1)}}),
        ("a stop before the switch", [loop, fall],
         {"A": {0: start(1), 2: start(2, deferred=1), 3: ("stop",), 6: start(1)}}),
        ("a start before the switch", [loop, fall, square],
         {"A": {0: start(1), 2: start(2, deferred=1), 3: start(3)}}),
        ("a retune before the switch", [loop, fall],
         {"A": {0: start(1), 2: start(2, rate=quick, deferred=1),
                3: ("retune", 200, 150, 0, 0)}}),
        ("a second deferred start before the switch", [loop, fall, three],
         {"A": {0: start(1), 2: start(2, deferred=1), 3: start(3, deferred=1)}}),
        ("a deferred start on a stopped timer", [fall, loop],
         {"A": {0: start(1, rate=quick), 8: start(2, deferred=1)}}),
        ("a fall after a loop on Timer C", [loop, fall],
         {"C": {0: start(1, target=9), 3: start(2, target=9, rate=quick, deferred=1)}}),
        ("two timers, one deferred start each", [loop, fall, square],
         {"A": {0: start(1), 3: start(2, rate=quick, deferred=1)},
          "D": {0: start(3, target=9), 2: start(2, target=9, tr=0, deferred=1)}}),
        ("a deferred start through a mapping, in both banks", [loop, three],
         {"A": {0: start(1, rate=quick, mapping=[1]),
                4: start(2, rate=quick, deferred=1, mapping=[2]),
                12: start(1, rate=quick, deferred=1, mapping=[1])}}, two_maps),
        ("deferred starts through a mapping of a square, one row and three columns",
         [loop, square, held, voice],
         {"A": {0: start(1, target=10, mapping=[1]),
                3: start(2, target=10, deferred=1, mapping=[2]),
                10: start(3, target=10, deferred=1, mapping=[1]), 11: remap([1]),
                16: start(1, target=10, deferred=1, mapping=[2])},
          "B": {0: start(1, target=9, mapping=[1]),
                3: start(4, target=17, deferred=1, mapping=[0, 0, 2])}}, two_maps),
        ("a remap of a square while a start is pending, the frames at 200 Hz", [square, held],
         {"A": {0: start(1, target=10, mapping=[2]),
                2: start(2, target=10, deferred=1, mapping=[1]), 3: remap([1])}},
         two_maps, 200),
        ("a remap before the switch, a retune after it, a start that keeps the mapping",
         [fall, loop],
         {"A": {0: start(1, mapping=[1]), 2: start(2, deferred=1, mapping=[2]),
                4: remap([3]), 15: ("retune", 200, 200, 0, 1),
                18: start(2, mapping=[3])}}, three_maps),
        ("remaps between the identity and a transformation while a start is pending",
         [fall, loop],
         {"A": {0: start(1, mapping=0), 2: start(2, deferred=1, mapping=[1]),
                4: remap([2])},
          "D": {0: start(1, target=9, mapping=[1]),
                2: start(2, target=9, deferred=1, mapping=[2]), 4: remap(0),
                18: remap(0)}}, two_maps),
        ("a pending start replaced by one through a mapping", [fall, loop, three],
         {"A": {0: start(1, mapping=[1]), 2: start(2, deferred=1, mapping=0),
                3: start(3, deferred=1, mapping=[2])},
          "D": {0: start(1, target=9, mapping=0),
                2: start(2, target=9, deferred=1, mapping=[1]),
                3: start(3, target=9, deferred=1, mapping=[2])}}, two_maps),
    ]
    played = mapped = 0
    for title, sources, timers, *through in cases:
        tune = effects_tune(title, 24, sources, timers,
                            transformations=through[0] if through else None)
        if len(through) > 1:
            tune["tunes"][0]["rate"] = through[1]
        at, version = built(re.sub(r"[^a-z]+", "-", title), tune)
        assert version == MAPPED or not through, \
            "%s: ymxs-to-ymxr wrote version %d" % (title, version)
        ticked = []
        frames, ticks = check(at, *played_on(at, code, symbols), kit=bool(through),
                              ticked=ticked)[:2]
        assert ticks, "%s: no tick" % title
        played += 1
        mapped += bool(through)
    return ("deferred starts: %d tunes, every tick against the model, and the %d that map"
            " a value every frame against the reader's record" % (played, mapped))


def tune_file(file):
    """Tune-file header fields: version, source count, DTX3 offset,
    optional version 10 transformation-index offset and source-index
    offset (SPEC.md 3.3).
    """
    version = struct.unpack(">H", file[4:6])[0]
    return (version, file[9], long_at(file, 12),
            long_at(file, 16) if version == MAPPED else None,
            20 if version == MAPPED else 16)


def edited(file, edits):
    """A tune file with columns of its rows set by hand: `edits` maps a row
    and a column to the byte of the column there. The DTX3 table is read
    out and packed again through dtx-write at its unit, its ring and its
    copies (DTX3 header fields at 16, 18 and 19), and the sources follow it,
    each on a long and their index entries moved, every other byte as it
    was (SPEC.md 3.3.1)."""
    _, count, table_at, _, index_at = tune_file(file)
    entries = [long_at(file, index_at + 4 * n) for n in range(count)]
    starts = [entry & ~COUNTED for entry in entries]
    table = file[table_at:starts[0] if count else len(file)]
    r = subprocess.run([DTX_WRITE, "-text"], input=table, capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    lines = r.stdout.decode().splitlines()
    rows = [line.split(",") for line in lines if line and line[0].isdigit()]
    for (row, column), byte in edits.items():
        rows[row][column] = str(byte)
    text = "\n".join([line for line in lines if not line or not line[0].isdigit()]
                     + [",".join(row) for row in rows]) + "\n"
    ring = struct.unpack(">H", table[16:18])[0]
    r = subprocess.run([DTX_WRITE, "-v3", "-k%d" % table[18], "-m%d" % ring]
                       + (["-copies"] if table[19] & 1 else []),
                       input=text.encode(), capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    out = bytearray(file[:table_at]) + r.stdout
    for n, at in enumerate(starts):
        out += b"\0" * (-len(out) % 4)
        rows_, columns = long_at(file, at + 4), struct.unpack(">H", file[at + 8:at + 10])[0]
        struct.pack_into(">I", out, index_at + 4 * n, len(out) | (entries[n] & COUNTED))
        out += file[at:at + 16 + (columns - 1) * ((rows_ + 1) & ~1) + rows_]
    return bytes(out)


def reformed(file, forms):
    """A tune file with sources in the other form of SPEC.md 3.1.6: `forms`
    maps a source's number to the column of its marker (2.1) and whether
    it is counted. A counted source has bit 31 of its index entry set and
    the marker cleared from the last row of that column; a marked one has
    bit 31 clear and the marker set there, the form of 2.1.4 on
    setEnvelope. Every other byte as it was. YMXS's writer encodes each
    such source in the other form (3.1.6)."""
    out = bytearray(file)
    index_at = tune_file(file)[4]
    for n, (column, counted) in forms.items():
        entry = long_at(out, index_at + 4 * (n - 1))
        at = entry & ~COUNTED
        rows = long_at(out, at + 4)
        last = at + 16 + column * ((rows + 1) & ~1) + rows - 1
        assert bool(entry & COUNTED) != counted, "source %d is in that form" % n
        assert bool(out[last] & 0x80) == counted, \
            "source %d has %02x in the last row of column %d" % (n, out[last], column)
        struct.pack_into(">I", out, index_at + 4 * (n - 1), at | (COUNTED if counted else 0))
        out[last] ^= 0x80
    return bytes(out)


# Version 9 fixtures used to measure frame overhead with lookup support
# against the core without it (performance.md, A remap at the frame).
# Build once per run. Each table row identifies a measured path,
# starting with an effect's head.
_V9 = []
V9_PATHS = (("the head of each effect the tune runs, a frame", None),
            ("a start at row 0", "start"),
            ("a start at the running source's row, after a source that steps a place",
             "start kept"),
            ("a start of a counted source or of one of several columns", "wide start"),
            ("a square's start at row 0", "square start"),
            ("a square's start at the running source's row, after a source that steps a"
             " place", "square start kept"),
            ("a place's reset", "reset"),
            ("a square's place reset", "square reset"),
            ("a counted source's place reset", "counted reset"),
            ("a place's reset of a source of several columns", "wide reset"))


def v9_tunes():
    """Tunes that map no value, each (title, tune file, the path of each
    row that starts a source or resets its place, by row), on effect A
    alone: a source of one column and a square, each started at row 0
    and at the running source's row, after a source that steps a place,
    and its place reset; and a source of several columns and a counted
    source, each started and its place reset (V9_PATHS)."""
    if _V9:
        return _V9
    loop = ("loop of four", [15, 12, 9, 6], 0)
    square = ("square", [15, 0], 0)
    toned = ("tone A", [[0, 1], [128, 2], [64, 3], [32, 4]], 0)
    whole = ("whole bytes", [200, 150, 100, 50, 25], 0)
    reset = ("retune", 50, 100, 0, 1)

    def start(source, target, reset=1):
        return ("start", source, target, 50, 100, reset, reset, 0)
    cases = [
        ("a loop and a square, started and their places reset", [loop, square],
         {0: (start(1, 8), "start"), 4: (reset, "reset"), 8: (start(1, 8, 0), "start kept"),
          12: (start(2, 8, 0), "square start kept"), 16: (reset, "square reset"),
          20: (start(2, 8), "square start")}),
        ("a source of several columns, started and its place reset", [toned],
         {0: (start(1, 14), "wide start"), 9: (reset, "wide reset"),
          15: (reset, "wide reset")}),
        ("a counted source, started and its place reset", [whole],
         {0: (start(1, 0), "wide start"), 6: (reset, "counted reset"),
          15: (reset, "counted reset")})]
    for title, sources, rows in cases:
        tune = effects_tune(title, 24, sources, {"A": {r: op for r, (op, _) in rows.items()}})
        at, version = built(re.sub(r"[^a-z0-9]+", "-", title.lower()), tune)
        assert version == WRITTEN, "%s: ymxs-to-ymxr wrote version %d" % (title, version)
        _V9.append((title, at, {r: path for r, (_, path) in rows.items()}))
    return _V9


# A volume's change through copies of a source, on the cores without the
# lookup (performance.md, A remap at the frame): the rows of COPY_ROWS
# each start the other of two sources at the running source's row.
_COPIES = []
COPY_ROWS = (5, 11, 17)


def copies_tune():
    """A tune that maps no value, on effect A alone: two marked sources of
    one column and four rows, the second the first at a lower volume,
    the first started at row 0 and then each row of COPY_ROWS a start of
    the other with timerReset and placeReset clear, so the timer runs on
    and the place is the running source's row."""
    if _COPIES:
        return _COPIES[0]
    loud, soft = ("loop of four", [15, 12, 9, 6], 0), ("loop of four, softer", [10, 8, 6, 4], 0)
    rows = {0: ("start", 1, 8, 50, 100, 1, 1, 0)}
    rows.update({r: ("start", 2 - n % 2, 8, 50, 100, 0, 0, 0) for n, r in enumerate(COPY_ROWS)})
    at, version = built("copies", effects_tune("copies of a loop at two volumes", 24,
                                               [loud, soft], {"A": rows}))
    assert version == WRITTEN, "the copies' tune is of version %d" % version
    _COPIES.append(at)
    return at


# The tunes that map a value (SPEC.md 1.10), built once a run (mapped_tunes).
_MAPPED = []

# Handler names used by mapped sources: lookup kind, square or one-row.
# Preserve these after run or mapped clears the active field (running).
RAN = set()

# The effects whose running page is in their fallback (pages).
FELL = set()

# Where Machine puts the page area: right after the player, as the tools
# put it after the core, or past the tune (fallbacks).
AREA_AFTER = False

# Each copy init makes into the page area, (its fill kind, E): the cycles
# from ymxr_fill to the return into ymxr_prefill, over the tunes a run of
# -cycles plays (check).
INIT_COPIES = {}

# Number of lookup tunes before the ordinary fixtures in
# patched_code_follows_the_subtune.
SET_MAPPED = 6


def mapped_tunes():
    """Lookup fixtures as (title, tune file, handler kinds). Encode YMXS
    version 7 through ymxs-to-ymxr. edited adds raw-column cases;
    reformed adds source encodings omitted by the writer.

    Coverage includes sample-volume fades; square and one-row
    immediates; identity/lookup transitions; remaps on idle and stopped
    timers; byte keys; all eligible last-value columns; short tables;
    a table of 32 entries in the mirror and the plain form; and setR7
    port bits.

    State cases cover target-only rows, place preservation/reset, remap
    with retune/reset, shared C/D control, ignored mapping on stop, and
    remap with a target-column change. Encoding cases cover marker
    mirrors, plain pages, legacy marked envelopes and counted
    multi-column sources.

    Playback reaches loop and stop paths across handler kinds and
    effects, including four counted sources remapped through full
    byte-key tables in one frame (performance.md, The cost of 34
    columns). deferred checks mapped deferred starts separately.
    """
    if _MAPPED:
        return _MAPPED
    def volume(n):
        return [k * n // 15 for k in range(16)]
    V15, V10, V5, V0 = volume(15), volume(10), volume(5), volume(0)
    V32 = [k * 15 // 31 for k in range(32)]
    tone, tone2 = [(k + 3) & 15 for k in range(16)], [15 - k for k in range(16)]
    shape, shape2 = [8 + (k & 7) for k in range(16)], [15 - (k & 7) for k in range(16)]
    sample, sample2 = [k >> 3 for k in range(256)], [31 - (k >> 3) for k in range(256)]
    period, period2 = [(16 * k) & 255 for k in range(16)], [255 - 16 * k for k in range(16)]
    high, high2 = [(5 * k) & 127 for k in range(16)], [(7 * k + 3) & 127 for k in range(16)]
    ten, ten2 = [9 - k for k in range(10)], [3 * k for k in range(10)]
    mixer, mixer2 = [k ^ 0x3F for k in range(64)], [(k + 7) & 63 for k in range(64)]
    loop = ("loop of four", [15, 12, 9, 6], 0)
    fall = ("fall", list(range(15, -1, -1)), None)
    square = ("square", [15, 0], 0)
    held = ("one row", [11], 0)
    voice = ("voice A", [[0, 1, 15], [128, 1, 12], [0, 2, 9], [128, 2, 6]], 0)
    toned = ("tone A", [[0, 1], [128, 2], [64, 3], [32, 4]], 0)
    buzz = ("buzzer", [[0, 2, 8], [128, 3, 10], [0, 5, 12], [64, 8, 14]], 0)
    noise = ("noise A", [[3, 15], [7, 11], [11, 8], [15, 4]], 0)
    envelope = ("envelope", [[0, 2], [128, 3], [0, 5], [64, 8]], 0)
    wave = ("sample", [0, 40, 90, 140, 200, 255, 180, 110, 60, 20], 0)
    tens = ("ten keys", [9, 5, 2, 7, 0], 0)
    mix = ("mixer", [56, 48, 62, 7], 0)
    whole = ("whole bytes", [200, 150, 100, 50, 25], 0)
    quick = (50, 100)

    def once(source):
        return (source[0] + " once", source[1], None)

    def start(source, target, mapping, rate=quick, tr=1, pr=1, deferred=0):
        return ("start", source, target, rate[0], rate[1], tr, pr, deferred, mapping)

    def remap(mapping):
        return ("remap", mapping)

    cases = [
        ("a fade on setR8", [loop], [("V15", V15), ("V10", V10), ("V5", V5), ("V0", V0)],
         {"A": {0: start(1, 8, [1]), 4: remap([2]), 8: remap([3]), 12: remap([4]),
                16: remap(0), 20: remap([1])}}, None, {"lk"}),
        ("a fade of setVoiceA's volume", [voice], [("V15", V15), ("V10", V10), ("V5", V5)],
         {"A": {0: start(1, 17, [0, 0, 1]), 6: remap([0, 0, 2]), 12: remap([0, 0, 3]),
                18: remap(0), 20: remap([0, 0, 1])}}, None, {"lv"}),
        ("a mapped square and its remap", [square], [("V10", V10), ("V5", V5)],
         {"A": {0: start(1, 8, [1]), 6: remap([2]), 12: remap(0), 18: remap([1])}},
         None, {"sq"}),
        ("a mapped source of one row and its remap", [held], [("V10", V10), ("V5", V5)],
         {"A": {0: start(1, 9, [1]), 6: remap([2]), 12: remap(0), 18: remap([1])}},
         None, {"one"}),
        ("a source remapped from the identity to T, to T' and back", [loop],
         [("V10", V10), ("V5", V5)],
         {"A": {0: start(1, 10, 0), 4: remap([1]), 8: remap([2]), 12: remap(0),
                16: remap([1])}}, None, {"lk"}),
        ("a remap on an idle timer and on a stopped one, each kept by a start",
         [loop], [("V5", V5), ("V10", V10)],
         {"A": {0: remap([1]), 4: start(1, 8, [1]), 10: ("stop",), 12: remap([2]),
                14: start(1, 8, [2])}}, None, {"lk"}),
        ("an 8-bit sample on setR10 through 256 entries", [wave],
         [("sample", sample), ("sample again", sample2)],
         {"B": {0: start(1, 10, [1]), 8: remap([2]), 16: remap([1])}}, None, {"lc"}),
        ("the coarse nibble of setToneA", [toned], [("tone", tone), ("tone again", tone2)],
         {"A": {0: start(1, 14, [0, 1]), 6: remap([0, 2]), 9: ("retune", 50, 100, 0, 1),
                12: remap(0), 18: remap([0, 1])}}, None, {"lt"}),
        ("the shape of setBuzzer", [buzz], [("shape", shape), ("shape again", shape2)],
         {"C": {0: start(1, 21, [0, 0, 1]), 6: remap([0, 0, 2]), 12: remap(0),
                18: remap([0, 0, 1])}}, None, {"lb"}),
        ("the volume of setNoiseA", [noise], [("V10", V10), ("V5", V5)],
         {"D": {0: start(1, 22, [0, 1]), 6: remap([0, 2]), 12: remap(0),
                18: remap([0, 1])}}, None, {"ln"}),
        ("the high byte of setEnvelope", [envelope],
         [("period", period), ("period again", period2)],
         {"B": {0: start(1, 20, [0, 1]), 6: remap([0, 2]), 9: ("retune", 64, 120, 0, 1),
                12: remap(0), 18: remap([0, 1]), 21: ("retune", 64, 120, 0, 1)}},
         None, {"le"}),
        ("a table of 10 entries on setR9", [tens], [("ten", ten), ("ten again", ten2)],
         {"A": {0: start(1, 9, [1]), 8: remap([2]), 16: remap([1])}}, None, {"lk"}),
        ("setR7 through a transformation", [mix], [("mixer", mixer), ("mixer again", mixer2)],
         {"A": {0: start(1, 7, [1]), 8: remap([2]), 11: ("retune", 50, 100, 0, 1),
                14: remap(0), 18: remap([1])}}, None, {"lc"}),
        ("a start through T on setToneA, the target alone to setNoiseA, a place's"
         " reset and a remap", [toned, noise], [("tone", tone), ("tone again", tone2),
                                                 ("V10", V10)],
         {"A": {0: start(1, 14, [0, 1]), 6: ("retune", 50, 100, 0, 1), 10: remap([0, 2]),
                14: start(2, 22, [0, 3])}}, {(3, 14): 0x80 | 22}, {"lt", "ln"}),
        ("a start through T on setVoiceA, the target alone to setBuzzer, a place's"
         " reset and a remap", [voice, buzz], [("V15", V15), ("V5", V5), ("shape", shape)],
         {"A": {0: start(1, 17, [0, 0, 1]), 6: ("retune", 50, 100, 0, 1),
                10: remap([0, 0, 2]), 14: start(2, 21, [0, 0, 3])}},
         {(3, 14): 0x80 | 21}, {"lv", "lb"}),
        ("a counted source on setR7 remapped from the identity after the target alone",
         [mix], [("mixer", mixer)],
         {"A": {0: start(1, 7, 0), 6: remap([1]), 10: remap(0), 14: remap([1])}},
         {(3, 14): 0x80 | 0}, {"lc"}),
        ("starts through a new mapping with placeReset clear while a source ticks",
         [loop, fall], [("V15", V15), ("V5", V5), ("V10", V10)],
         {"A": {0: start(1, 8, [1]), 5: start(2, 8, [2], tr=0, pr=0),
                12: start(1, 8, [3], tr=0, pr=0), 18: start(2, 8, [1], tr=0, pr=0)}},
         None, {"lk"}),
        ("a remap and a retune with timerReset on a running timer", [loop],
         [("V15", V15), ("V10", V10), ("V5", V5)],
         {"A": {0: start(1, 8, [1]), 6: remap([2]), 12: remap([3])}},
         {(6, 16): 0xC4, (6, 17): 100, (12, 16): 0xC4, (12, 17): 120}, {"lk"}),
        ("a remap and a retune with timerReset on a stopped timer, kept by a start",
         [fall], [("V15", V15), ("V10", V10)],
         {"B": {0: start(1, 9, [1]), 8: remap([2]), 14: start(1, 9, [2])}},
         {(8, 24): 0xC4, (8, 25): 100}, {"lk"}),
        ("a place's reset on a mapped square, loop and counted source, three effects",
         [square, loop, ("whole bytes", [200, 150, 100, 50, 25], 0)],
         [("V10", V10), ("V5", V5), ("sample", sample)],
         {"A": {0: start(1, 8, [1]), 4: ("retune", 50, 100, 0, 1), 9: remap([2]),
                13: ("retune", 50, 90, 0, 1)},
          "B": {0: start(2, 9, [2]), 6: ("retune", 50, 110, 0, 1), 12: remap([1]),
                18: ("retune", 50, 110, 0, 1)},
          "D": {0: start(3, 0, [3]), 6: ("retune", 64, 100, 0, 1),
                15: ("retune", 64, 100, 0, 1)}}, None, {"sq", "lk", "lc"}),
        ("one transformation through the mirror and through the plain fill",
         [loop, noise], [("V10", V10)],
         {"A": {0: start(1, 8, [1]), 6: start(2, 22, [0, 1], tr=0), 12: start(1, 8, [1], tr=0),
                18: start(2, 22, [0, 1], tr=0)}}, None, {"lk", "ln"}),
        ("a transformation of 32 entries through the mirror and through the plain fill",
         [loop, noise], [("V32", V32)],
         {"A": {0: start(1, 8, [1]), 6: start(2, 22, [0, 1], tr=0), 12: start(1, 8, [1], tr=0),
                18: start(2, 22, [0, 1], tr=0)}}, None, {"lk", "ln"}),
        ("squares and a loop started through a new mapping with placeReset clear",
         [square, ("square two", [13, 2], 0), loop], [("V15", V15), ("V10", V10), ("V5", V5)],
         {"A": {0: start(1, 8, [1]), 5: start(2, 8, [2], tr=0, pr=0),
                11: start(3, 8, [3], tr=0, pr=0), 17: start(1, 8, [2], tr=0, pr=0)}},
         None, {"sq", "lk"}),
        ("starts with placeReset clear while a source of several columns runs",
         [voice, buzz, toned], [("V15", V15), ("shape", shape), ("tone", tone)],
         {"A": {0: start(1, 17, [0, 0, 1]), 6: start(2, 21, [0, 0, 2], tr=0, pr=0),
                12: start(3, 14, [0, 3], tr=0, pr=0), 18: start(1, 17, [0, 0, 1], tr=0, pr=0)}},
         None, {"lv", "lb", "lt"}),
        ("remaps on Timers C and D, which share a control register, each idle while"
         " the other runs", [loop], [("V10", V10), ("V5", V5)],
         {"D": {0: start(1, 9, [1]), 8: ("stop",), 10: remap([2]), 12: start(1, 9, [2]),
                16: remap([1])},
          "C": {2: remap([2]), 6: start(1, 10, [2]), 14: remap(0)}}, None, {"lk"}),
        ("a remap and a retune with timerReset on a running square", [square],
         [("V10", V10), ("V5", V5)],
         {"A": {0: start(1, 8, [1]), 6: remap([2]), 12: remap(0)}},
         {(6, 16): 0xC4, (6, 17): 100, (12, 16): 0xC4, (12, 17): 90}, {"sq"}),
        ("a stop row with the mapping column set, then a start with it unset", [loop],
         [("V10", V10), ("V5", V5)],
         {"A": {0: start(1, 8, [1]), 8: ("stop",), 12: start(1, 8, [1]), 20: remap([2])}},
         {(8, 30): 0x80 | 2}, {"lk"}),
        ("a remap in the row that sets the target to setNoiseA", [toned, noise],
         [("tone", tone), ("tone again", tone2)],
         {"A": {0: start(1, 14, [0, 1]), 6: remap([0, 2]), 12: start(2, 22, [0, 2])}},
         {(6, 14): 0x80 | 22}, {"lt", "ln"}),
        ("a marked source on setEnvelope through T, a remap, a place's reset and a remap"
         " from the identity", [envelope], [("high", high), ("high again", high2)],
         {"B": {0: start(1, 20, [0, 1]), 6: remap([0, 2]), 9: ("retune", 64, 120, 0, 1),
                12: remap(0), 15: ("retune", 64, 120, 0, 1), 18: remap([0, 1]),
                21: ("retune", 64, 120, 0, 1)}}, None, {"lt"}, {1: (1, False)}),
        ("counted sources of two columns on setToneA and setNoiseA through T, a remap, a"
         " place's reset and a remap from the identity", [toned, noise],
         [("tone", tone), ("tone again", tone2), ("V10", V10), ("V5", V5)],
         {"A": {0: start(1, 14, [0, 1]), 6: remap([0, 2]), 9: ("retune", 50, 100, 0, 1),
                12: remap(0), 18: remap([0, 1])},
          "D": {0: start(2, 22, [0, 3]), 6: remap([0, 4]), 9: ("retune", 50, 100, 0, 1),
                12: remap(0), 15: ("retune", 50, 100, 0, 1), 18: remap([0, 3]),
                21: ("retune", 50, 100, 0, 1)}}, None, {"le", "lf"}, {1: (1, True), 2: (0, True)}),
        ("sources that play once on seven lookup kinds and loop on five, two effects",
         [fall, once(voice), once(buzz), once(toned), once(noise), once(envelope),
          once(whole), voice, buzz, toned, noise, wave],
         [("sample", sample), ("V15", V15), ("shape", shape), ("tone", tone), ("V10", V10),
          ("period", period)],
         {"A": {0: start(1, 8, [1]), 4: start(2, 17, [0, 0, 2]), 8: start(3, 21, [0, 0, 3]),
                12: start(4, 14, [0, 4]), 16: start(5, 22, [0, 5]), 20: start(6, 20, [0, 6])},
          "B": {0: start(7, 0, [1]), 4: start(8, 18, [0, 0, 2]), 9: start(9, 21, [0, 0, 3]),
                13: start(10, 15, [0, 4]), 17: start(11, 23, [0, 5]), 21: start(12, 10, [1])}},
         None, {"lk", "lv", "lb", "lt", "ln", "le", "lc"}),
        ("sources that play once on seven lookup kinds, one effect",
         [fall, once(voice), once(buzz), once(toned), once(noise), once(envelope), once(whole)],
         [("V15", V15), ("shape", shape), ("tone", tone), ("V10", V10), ("period", period),
          ("sample", sample)],
         {"A": {0: start(1, 8, [1]), 3: start(2, 17, [0, 0, 1]), 6: start(3, 21, [0, 0, 2]),
                9: start(4, 14, [0, 3]), 12: start(5, 22, [0, 4]), 15: start(6, 20, [0, 5]),
                18: start(7, 0, [6])}},
         None, {"lk", "lv", "lb", "lt", "ln", "le", "lc"}),
        ("four sources through 256 entries remapped in one row", [whole],
         [("sample", sample), ("sample again", sample2)],
         {letter: {0: start(1, target, [1]), 8: remap([2]), 16: remap([1])}
          for letter, target in (("A", 0), ("B", 2), ("C", 4), ("D", 11))}, None, {"lc"}),
    ]
    for title, sources, transformations, timers, edits, kinds, *forms in cases:
        tune = effects_tune(title, 24, sources, timers, transformations=transformations)
        at, version = built(re.sub(r"[^a-z0-9]+", "-", title.lower()), tune)
        assert version == MAPPED, "%s: ymxs-to-ymxr wrote version %d" % (title, version)
        if edits or forms:
            with open(at, "rb") as f:
                file = f.read()
            if edits:
                file = edited(file, edits)
            if forms:
                file = reformed(file, forms[0])
            with open(at, "wb") as f:
                f.write(file)
        _MAPPED.append((title, at, kinds))
    return _MAPPED


def mapped(code, symbols):
    """Play mapped_tunes tick by tick against the model and frame by frame
    against the reader record (SPEC.md 7). Check all lookup shapes and
    inject ticks at the three lookup boundaries (inside). Without lookup
    support, require init to reject each fixture, then play the first on
    a supported build; -map plays all of them (BINARIES.md 1.5).
    """
    tunes = mapped_tunes()
    if "ymxr_look" not in symbols:
        work = tempfile.mkdtemp()
        for title, at, _ in tunes:
            with open(at, "rb") as f:
                bound = bind(f.read(), work)
            assert Machine(code, symbols, bound).call("init", a0=FILE, a1=WORK + 0x100) \
                & 0xFFFFFFFF == 0xFFFFFFFF, "%s: init of a player without the lookup took it" % title
        tunes = tunes[:1]
    played = 0
    inner = {"page": 0, "window": 0, "window one": 0, "square": 0}
    for title, at, kinds in tunes:
        ticked = []
        RAN.clear()
        result = check(at, *played_on(at, code, symbols), kit=True, perf=PERF, ticked=ticked)
        assert ticked, "%s: no tick" % title
        assert kinds <= RAN, "%s: a source ran through a transformation in %s, and in none of %s" % (
            title, sorted(RAN), sorted(kinds - RAN))
        for kind in result[6]:
            if kind in inner:
                inner[kind] += 1
        played += 1
    if "ymxr_look" not in symbols:
        return ("tunes that map a value: init reports -1 for each of %d, and the first plays"
                " on the player with the lookup in, every tick against the model and every"
                " frame against the reader's record" % len(mapped_tunes()))
    assert all(inner.values()), "a tick fell inside the boundaries of the lookup %s times" % inner
    return ("tunes that map a value: %d tunes, every tick against the model and every"
            " frame against the reader's record, a tick inside a remap's page switch at %d"
            " rows, in a start's window at %d, %d of them the general kind's, and between a"
            " square's mask and level at %d"
            % (played, inner["page"], inner["window"] + inner["window one"],
               inner["window one"], inner["square"]))


# The tune whose pages pass the reach (fallback_tune), built once a run,
# and the rows from FALLBACK_FROM on, every second one, that fill a page
# of effect 0's fallback at the frame, each the one start or remap of its
# row: the start or the remap, and the entries of its transformation
# (performance.md, A remap at the frame).
_FALLBACK = []
FALLBACK_FROM = 100
FALLBACK_FILLS = (("remap", 16), ("remap", 32), ("start", 16), ("start", 32))


def fallback_tune():
    """A tune whose pages pass the reach of the first handler: effect 0
    remaps through 100 transformations of 16 entries, one a row, and
    effect 1 through 20 in rows 81 to 99, so init fills the first pages of
    the list and each effect maps the rest through its fallback, both in
    the same rows. From row FALLBACK_FROM, in every second row, effect 0
    alone runs a row of FALLBACK_FILLS, a start at row 0 or a remap,
    through a transformation of 16 or 32 entries that each row before it
    leaves unused, and fills a page of its fallback at the frame."""
    if _FALLBACK:
        return _FALLBACK[0]
    tables = [("T%d" % n, [(k * (2 * (n % 8) + 1) + n // 8 + 1) % 16 for k in range(16)])
              for n in range(120)]
    ramp = ("ramp", list(range(16)), 0)
    a = {0: ("start", 1, 8, 50, 100, 1, 1, 0, [1])}
    a.update({r: ("remap", [1 + r]) for r in range(1, FALLBACK_FROM)})
    d = {0: ("start", 1, 9, 50, 100, 1, 1, 0, [101])}
    d.update({r: ("remap", [21 + r]) for r in range(81, FALLBACK_FROM)})
    rows = FALLBACK_FROM
    for what, entries in FALLBACK_FILLS:
        # an even step, so each table differs from the 120 above
        n = len(tables) + 1
        tables.append(("W%d" % n, [(2 * k + n) % 16 for k in range(entries)]))
        a[rows] = ("remap", [n]) if what == "remap" else ("start", 1, 8, 50, 100, 1, 1, 0, [n])
        rows += 2
    tune = effects_tune("two effects through their fallback", rows, [ramp],
                        {"A": a, "D": d}, transformations=tables)
    at, version = built("fallbacks", tune)
    assert version == MAPPED, "the fallback's tune is of version %d" % version
    _FALLBACK.append(at)
    return at


def fallbacks(code, symbols):
    """The tune of fallback_tune, every tick against the model and every
    frame against the reader's record (BINARIES.md 5.6). Then init
    reports -1 for a tune that maps a value with a2 at 0, with an area
    below the player and without a list, and leaves the area below as it
    was; and 0 for one whose list has no page, with a2 at 0 (5.1)."""
    global AREA_AFTER
    if "ymxr_look" not in symbols:
        code, symbols = build(switched(DEFINES, lookup=True))
    at = fallback_tune()
    work = tempfile.mkdtemp()
    with open(at, "rb") as f:
        bound = bind(f.read(), work)
    count, entries = list_of(bound)
    # on the build whose ticks read through a displacement, the area past
    # the tune, so that the tune's sources are within the reach the area
    # leaves of the first handler (BINARIES.md 5.5, 5.6); under -abs,
    # whose ticks read an absolute address, the area right after the
    # player and the tune past the reach
    global MOST_FRAMES
    # every frame of the tune played under -frames too: effect 0 reaches
    # its fallback at its row k
    AREA_AFTER, most = PCREL, MOST_FRAMES
    MOST_FRAMES = None
    try:
        return refused(code, symbols, at, bound, count, entries, work)
    finally:
        AREA_AFTER, MOST_FRAMES = False, most


def refused(code, symbols, at, bound, count, entries, work):
    """The rest of fallbacks, with the area past the tune."""
    m = Machine(code, symbols, bound)
    k = plan_of(count, entries, m.reach, fallback_of(symbols))[0]
    dropped = {e for e, _, _ in entries[k:]}
    assert dropped == {0, 1}, "init fills %d of %d pages, and effects %s fall back" % (
        k, len(entries), sorted(dropped))
    FELL.clear()
    frames, ticks = check(at, code, symbols, kit=True)[:2]
    assert FELL == {0, 1}, "effects %s ran a page of their fallback" % sorted(FELL)
    # init reports -1, and leaves the bytes it was handed as they were
    mapped_bound = bound
    workspace = WORK + 0x100
    m = Machine(code, symbols, mapped_bound)
    m.area = 0
    assert m.call("init", a0=FILE, a1=workspace) == 0xFFFFFFFF, \
        "init took a tune that maps a value with no page area"
    m = Machine(code, symbols, mapped_bound)
    below = CODE - 0x400
    m.area = below
    was = bytes(m.mu.mem_read(below, 0x400))
    assert m.call("init", a0=FILE, a1=workspace) == 0xFFFFFFFF, \
        "init took a page area below the player"
    assert bytes(m.mu.mem_read(below, 0x400)) == was, "init wrote below the player"
    block = struct.unpack(">i", mapped_bound[24:28])[0]
    count = struct.unpack(">H", mapped_bound[block:block + 2])[0]
    tag = block + 2 + 4 * count
    for n in range(count):
        at = block + long_at(mapped_bound, block + 2 + 4 * n)
        tag = max(tag, at + 2 + struct.unpack(">H", mapped_bound[at:at + 2])[0])
    assert mapped_bound[tag:tag + 2] == LIST_TAG, "the kit's list is untagged"
    untagged = mapped_bound[:tag] + bytes(2) + mapped_bound[tag + 2:]
    m = Machine(code, symbols, untagged)
    m.area = (FILE + len(untagged) + 1) & ~1
    assert m.call("init", a0=FILE, a1=workspace) == 0xFFFFFFFF, \
        "init took a tune that maps a value without a list"
    square = built("a-mapped-square", effects_tune(
        "a mapped square", 8, [("square", [15, 0], 0)],
        {"A": {0: ("start", 1, 8, 50, 100, 1, 1, 0, [1])}},
        transformations=[("V", [15 - k for k in range(16)])]))[0]
    with open(square, "rb") as f:
        none = bind(f.read(), work)
    assert list_of(none)[1] == [], "the mapped square lists pages"
    m = Machine(code, symbols, none)
    assert m.area == 0 and m.call("init", a0=FILE, a1=workspace) == 0, \
        "init rejected a tune that lists no page, with no area"
    return ("the fallback: %d of %d pages in the area and effects 0 and 1 through their"
            " fallback, %d frames, %d ticks; init reports -1 with no area, with one below the"
            " player and without a list, and 0 for a list of no page with no area"
            % (k, len(entries), frames, ticks))


def fallback_fills(code, symbols, cycles_of):
    """The tune of fallback_tune counted on the player with the lookup in,
    the area past the tune as fallbacks places it: each row of
    FALLBACK_FILLS, on every pass, required to start or remap effect 0
    alone and to fill one page in the mirror form at the frame, of the
    entries the row names. By the start or the remap, the shape and the
    page's (fill kind, E), the set of (the routines from the call to the
    return, the copy) it counts (check, lookup_regions)."""
    global AREA_AFTER, MOST_FRAMES
    AREA_AFTER, most = PCREL, MOST_FRAMES
    MOST_FRAMES = None
    try:
        where = check(fallback_tune(), code, symbols, CyclesOn(cycles_of))[5]
    finally:
        AREA_AFTER, MOST_FRAMES = False, most
    R, fills = where[1], {}
    for f, (events, spent, filled) in enumerate(where[8]):
        row = f % R - FALLBACK_FROM
        if row < 0:
            continue
        if row % 2:
            assert not events and not filled, \
                "frame %d: row %d starts or remaps %s and fills %s" % (f, f % R, events, filled)
            continue
        what, entries = FALLBACK_FILLS[row // 2]
        want = [(0, {"remap": "switch", "start": "start"}[what], "lk")]
        assert events == want and filled == [(1, entries)], \
            "frame %d: row %d runs %s and fills %s, not %s and a page of %d entries" % (
                f, f % R, events, filled, want, entries)
        fills.setdefault((events[0][1], events[0][2], filled[0]), set()).add(
            (spent["routine"], spent["copy"]))
    return fills


def deferred_sizes():
    """performance.md's sizes of the player with the deferred start in
    (A deferred start), against the four builds of the two switches: the
    player with it and without it, each with the row read through the
    program counter and through an absolute address, and an instance of
    the wide handler and of the marked shape of two columns in each."""
    built = {(pcrel, defer): assemble(defines=([] if pcrel else ["-dYMXR_PCREL=0"])
                                      + (["-dYMXR_DEFER=1"] if defer else []))
             for pcrel in (True, False) for defer in (True, False)}
    said = " ".join(open(os.path.join(ROOT, "doc", "performance.md")).read()
                    .split("## A deferred start")[1].split())
    sizes = re.search(r"make the player ([\d,]+) bytes against ([\d,]+), and ([\d,]+) against"
                      r" ([\d,]+) where a tick reads an absolute address", said)
    counted = tuple(len(built[k][0]) for k in ((True, True), (True, False),
                                               (False, True), (False, False)))
    assert sizes and tuple(int(x.replace(",", "")) for x in sizes.groups()) == counted, \
        "performance.md reads the two families' players as %s, and they assemble to %s" % (
            sizes and sizes.groups(), counted)
    shapes = re.search(r"an instance of the wide handler is (\d+) bytes against (\d+), and of"
                       r" the marked shape of two columns (\d+) against (\d+)", said)
    def instance(defer, shape):
        symbols = built[(True, defer)][1]
        return symbols["ymxr_%s1" % shape] - symbols["ymxr_%s0" % shape]
    counted = (instance(True, "env"), instance(False, "env"),
               instance(True, "two"), instance(False, "two"))
    assert shapes and tuple(int(x) for x in shapes.groups()) == counted, \
        "performance.md reads the counted instances as %s, and they assemble to %s" % (
            shapes and shapes.groups(), counted)
    return ("the deferred start: the player %d bytes against %d, every size performance.md"
            " reads" % (len(built[(True, True)][0]), len(built[(True, False)][0])))


def mapped_sizes():
    """Verify performance.md player sizes across combinations of source
    addressing, deferred starts and lookup support (A deferred start).
    """
    sizes = {(pcrel, defer, look): len(build(([] if pcrel else ["-dYMXR_PCREL=0"])
                                             + ([DEFER_ON] if defer else [])
                                             + ([MAP_ON] if look else []))[0])
             for pcrel in (True, False) for defer in (False, True) for look in (True, False)}
    said = " ".join(open(os.path.join(ROOT, "doc", "performance.md")).read()
                    .split("## A deferred start")[1].split())
    m = re.search(r"The lookup in, `YMXR_MAP=1`, makes the player ([\d,]+) bytes against"
                  r" ([\d,]+), and ([\d,]+) against ([\d,]+) where a tick reads an absolute"
                  r" address; with the deferred start in as well, ([\d,]+) against ([\d,]+),"
                  r" and ([\d,]+) against ([\d,]+)\.", said)
    counted = tuple(sizes[(pcrel, defer, look)] for defer in (False, True)
                    for pcrel in (True, False) for look in (True, False))
    assert m and tuple(int(x.replace(",", "")) for x in m.groups()) == counted, \
        "performance.md reads the players with the lookup as %s, and they assemble to %s" % (
            m and m.groups(), counted)
    return ("the lookup: the player %d bytes against %d, every size performance.md reads"
            % (sizes[(True, False, True)], sizes[(True, False, False)]))


def absolute_sizes():
    """performance.md's sizes of the player whose ticks read a row through
    an absolute address (A tick through an absolute address), against the
    two builds without the deferred start: the players, a timer's handlers
    and frame code, and the rest. The frame code of the four timers is from
    ymxr_frame to ymxr_regs, and their handlers from ymxr_tick0 to
    ymxr_reach."""
    near, near_symbols = assemble()
    far, far_symbols = assemble(defines=["-dYMXR_PCREL=0"])
    def span(symbols, first, last):
        return symbols[last] - symbols[first]
    handlers = (span(far_symbols, "ymxr_tick0", "ymxr_reach")
                - span(near_symbols, "ymxr_tick0", "ymxr_reach")) // 4
    frame = (span(near_symbols, "ymxr_frame", "ymxr_regs")
             - span(far_symbols, "ymxr_frame", "ymxr_regs")) // 4
    rest = len(near) - len(far) - 4 * (frame - handlers)
    said = " ".join(open(os.path.join(ROOT, "doc", "performance.md")).read()
                    .split("## A tick through an absolute address")[1].split())
    m = re.search(r"assembles this build, which is ([\d,]+) bytes against ([\d,]+)\..*?"
                  r"shortens a timer's handlers by (\d+) bytes, .*? (\d+) bytes more of the"
                  r" timer's frame code\. The init and the routines the four timers share,"
                  r" the reach check among them, are (\d+) bytes more", said)
    counted = (len(far), len(near), handlers, frame, rest)
    assert m and tuple(int(x.replace(",", "")) for x in m.groups()) == counted, \
        "performance.md reads the absolute build as %s, and it assembles to %s" % (
            m and m.groups(), counted)
    return "the absolute build: %d bytes against %d, every size performance.md reads" % (
        len(far), len(near))


def halves(code, symbols):
    """A square started again with bits 5 and 6 of the control column at
    0, on the target of the square before it and at its row count, with
    the timer running (rule 3(a) of SPEC.md 6): the place keeps its row
    number (1.8.4) and the timer its period (1.9), so each tick reads the
    row after the one the tick before it read, across both starts, and no
    half of the wave is written twice. The second start names a square of
    the first's two levels swapped: the row moves on and the level is the
    new source's, so the level of the tick before the start is written
    again.

    check reads every tick against the model; this reads the rows the
    ticks read, in order, and the levels they write.
    """
    rows = 24
    starts = {0: 1, 8: 2, 16: 3}
    def column(value, first=None):
        return [(first if r == 0 and first is not None else value)
                if r in starts else -1 for r in range(rows)]
    tune = {"format": "ymxs", "version": 3, "tunes": [{
        "title": "A square that keeps its half", "composer": "",
        "writer": "68k/test/emu/test_ymxr.py", "rate": 50, "rows": rows,
        "repeat": 0,
        "sources": [{"name": "fifteen", "repeat": 0, "values": [15, 0]},
                    {"name": "twelve", "repeat": 0, "values": [12, 3]},
                    {"name": "twelve swapped", "repeat": 0, "values": [3, 12]}],
        "registers": {"r7": [0x3E if r == 0 else -1 for r in range(rows)]},
        "timerA": {"shape": column(0), "target": column(8),
                   "source": [starts.get(r, -1) for r in range(rows)],
                   "prescaler": column(200), "count": column(60),
                   "timerReset": column(0, first=1),
                   "placeReset": column(0, first=1)}}]}
    at = built("halves", tune)[0]
    ticked = []
    frames, ticks = check(at, code, symbols, ticked=ticked)[:2]
    mine = [(f, source, row, writes[0][1] & 0x7F)
            for f, i, source, row, writes in ticked if i == 0]
    # a pass is the table's rows once: row 0's start sets bit 5, so each
    # pass begins at row 0 of its first source
    passes = [[one for one in mine if one[0] // rows == k]
              for k in range(frames // rows)]
    for ran in passes:
        assert [row for _, _, row, _ in ran] == [k % 2 for k in range(len(ran))], \
            "a tick reads the row the tick before it read: %s" % ran
        sources = [source for _, source, _, _ in ran]
        assert sources == sorted(sources) and set(sources) == {1, 2, 3}, \
            "the three squares play in turn: %s" % sources
        one, two = sources.index(2), sources.index(3)
        assert ran[one][3] != ran[one - 1][3], \
            "the first start writes the new square's level: %s" % ran[one - 1:one + 1]
        assert ran[two][3] == ran[two - 1][3], \
            "the second start writes the level again, the row moved on: %s" % (
                ran[two - 1:two + 1],)
    return ("a square started again with bits 5 and 6 at 0 keeps its half:"
            " %d frames, %d ticks" % (frames, len(mine)))


def voices(code, symbols):
    """A target that writes several registers (SPEC.md 2.1): a source of
    three columns on setVoiceA, whose rows move the voice's period and its
    volume at once, and one of two columns on setNoiseB.

    No dump converts to such a tune - a YM dump names a register a slot,
    not a voice - so the tune is written here through YMXS's form. The
    model reads a write a column and check reads the player against it,
    handler, place and vector.
    """
    rows = 32
    def column(ats, value):
        return [value if r in ats else -1 for r in range(rows)]
    starts = (0, 8, 16, 24)
    tune = {"format": "ymxs", "version": 4, "tunes": [{
        "title": "A target of several registers", "composer": "",
        "writer": "68k/test/emu/test_ymxr.py", "rate": 50, "rows": rows,
        "repeat": 0,
        "sources": [
            {"name": "voice A", "repeat": 0,
             "values": [[0, 1, 15], [128, 1, 12], [0, 2, 9], [128, 2, 6],
                        [0, 1, 11], [64, 1, 14], [0, 3, 7], [64, 3, 10]]},
            {"name": "noise B", "repeat": 0,
             "values": [[3, 15], [7, 11], [11, 8], [15, 4]]}],
        "registers": {"r7": column((0,), 0x34), "r10": column((0,), 0),
                      "r11": column((0,), 0), "r12": column((0,), 0),
                      "r13": column((0,), 0)},
        "timerA": {"shape": column(starts, 0), "target": column(starts, 17),
                   "source": column(starts, 1), "prescaler": column(starts, 50),
                   "count": column(starts, 80), "timerReset": column(starts, 1),
                   "placeReset": column(starts, 1)},
        "timerD": {"shape": column(starts, 0), "target": column(starts, 23),
                   "source": column(starts, 2), "prescaler": column(starts, 100),
                   "count": column(starts, 90), "timerReset": column(starts, 1),
                   "placeReset": column(starts, 1)}}]}
    at, version = built("voices", tune)
    assert version == WRITTEN, \
        "a tune with a source of several columns is version 7 (SPEC.md 3.3.5)"
    frames, ticks = check(at, code, symbols)[:2]
    return ("a target of three registers and one of two: %d frames, %d ticks"
            % (frames, ticks))


def widekept(code, symbols):
    """A start through ymxr_widestart, of a source of several columns or of
    a counted source, with bits 5 and 6 of the control column at 0 and the
    timer running on: the place keeps its row number modulo R of the new
    source (SPEC.md 1.8.4). inside() fires a tick where the routine opens
    its level-7 window and one where the window closes. The first reads
    the source that runs before the start, and the start reads the place
    that tick stepped; a start that reads the place before the window
    starts the new source a row behind. The second reads the new source at
    the place the start read; where the vector is aimed after the window,
    that tick runs the handler before the start, and the new source plays
    a row behind again.

    Five tunes on Timer A, each starting two sources in turn at rows 0, 6,
    12, 18 and 24. In the first both are of two columns on setToneA; in
    the other four the first is a source of one column on R10, which the
    general handler runs, and the second is of a kind the routine starts:
    two columns on setToneA, three on setVoiceA, a counted column on R0
    and a counted two on setEnvelope. Each tune fires a tick at four
    boundaries: the two of the window, the head of effect 0's step and the
    point after the last step. The four with the general handler fire two
    more, the two of its start's window at rows 12 and 24, where the
    source of R10 starts after the other: a start that aims the vector
    after its window has a tick due inside it run the other's handler.
    """
    rows = 30
    starts = (0, 6, 12, 18, 24)
    def column(at):
        return [at(starts.index(r)) if r in starts else -1 for r in range(rows)]
    tones = [[16 * k + 2, 2] for k in range(6)]
    kinds = (
        ("Two sources of two columns", ("eight tones", [[16 * k + 1, 1] for k in range(8)]),
         ("six tones", tones), (14, 14), {"r7": 0x3E, "r8": 15}),
        ("A source of two columns after the general handler", None,
         ("six tones", tones), (10, 14), {"r7": 0x3E, "r8": 15}),
        ("A source of three columns after the general handler", None,
         ("voice A", [[0, 1, 15], [128, 1, 12], [0, 2, 9], [128, 2, 6], [0, 1, 11],
                      [64, 1, 14]]), (10, 17), {"r7": 0x3E}),
        ("A counted source after the general handler", None,
         ("sweep", [200, 220, 240, 255, 240, 220]), (10, 0), {"r7": 0x3E, "r1": 1, "r8": 12}),
        ("A counted source of two columns after the general handler", None,
         ("sweep", [[0, 2], [128, 3], [0, 5], [64, 8], [0, 13], [192, 21]]), (10, 20),
         {"r7": 0x3E, "r8": 0x10, "r13": 10}))
    global MOST_FRAMES
    # the five tunes are 300 frames in all, read whole under -frames as
    # well, since the boundaries come at rows 6 to 24
    most, MOST_FRAMES = MOST_FRAMES, None
    frames = ticks = 0
    try:
        for title, first, then, targets, registers in kinds:
            want = 4 if first else 6
            first = first or ("eight volumes", [k + 4 for k in range(8)])
            tune = {"format": "ymxs", "version": 4, "tunes": [{
                "title": title, "composer": "", "writer": "68k/test/emu/test_ymxr.py",
                "rate": 50, "rows": rows, "repeat": 0,
                "sources": [{"name": name, "repeat": 0, "values": values}
                            for name, values in (first, then)],
                "registers": {r: [v] + [-1] * (rows - 1) for r, v in registers.items()},
                "timerA": {"shape": column(lambda k: 0),
                           "target": column(lambda k: targets[k % 2]),
                           "source": column(lambda k: 1 + k % 2),
                           "prescaler": column(lambda k: 50), "count": column(lambda k: 80),
                           "timerReset": column(lambda k: 1 if k == 0 else 0),
                           "placeReset": column(lambda k: 1 if k == 0 else 0)}}]}
            at = built("widekept", tune)[0]
            played = check(at, code, symbols)
            assert len(played[6]) == want, "%s: a tick at %d boundaries, not %d" % (
                title, len(played[6]), want)
            frames, ticks = frames + played[0], ticks + played[1]
    finally:
        MOST_FRAMES = most
    return ("a start of several columns or of a counted source at the running row, and one"
            " of the general handler after it, a tick where each window opens and one where"
            " it closes: %d tunes, %d frames, %d ticks" % (len(kinds), frames, ticks))


def envelope(code, symbols):
    """setEnvelope (SPEC.md 2.1.3): a source of two columns on target 20,
    the marker in bit 7 of the envelope period's high byte, so that column
    is a value of 0 to 127. The rows sweep the period under a repeating
    shape, with voice A on the envelope (R8 bit 4) and its tone open.

    The two registers of this target are R11 and R12, both of eight bits,
    where every other wide target marks a register of seven bits or fewer;
    the model reads the marker off the high byte and check reads the
    player against it.
    """
    rows = 24
    def column(ats, value):
        return [value if r in ats else -1 for r in range(rows)]
    starts = (0, 12)
    tune = {"format": "ymxs", "version": 4, "tunes": [{
        "title": "The envelope period a source", "composer": "",
        "writer": "68k/test/emu/test_ymxr.py", "rate": 50, "rows": rows,
        "repeat": 0,
        "sources": [
            {"name": "sweep", "repeat": 0,
             "values": [[0, 2], [128, 3], [0, 5], [64, 8], [0, 13], [192, 21]]}],
        "registers": {"r7": column((0,), 0x3E), "r0": column((0,), 0x80),
                      "r1": column((0,), 1), "r8": column((0,), 0x10),
                      "r13": column(starts, 10)},
        "timerB": {"shape": column(starts, 0), "target": column(starts, 20),
                   "source": column(starts, 1), "prescaler": column(starts, 64),
                   "count": column(starts, 120), "timerReset": column(starts, 1),
                   "placeReset": column(starts, 1)}}]}
    at, version = built("envelope", tune)
    assert version == WRITTEN, \
        "a source on setEnvelope is counted, in a tune of version 7 (SPEC.md 3.3.5)"
    frames, ticks = check(at, code, symbols)[:2]
    return ("the envelope period a counted source of two columns: %d frames, %d ticks"
            % (frames, ticks))


def outofreach(code, symbols):
    """Load sources beyond signed-word reach and require init to report -1
    (BINARIES.md 5.5). Other tests place sources within reach of their
    assembled handlers.
    """
    work = tempfile.mkdtemp()
    with open(os.path.join(ROOT, "doc", "conformance", "tunes",
                           "turrican.ymxr"), "rb") as f:
        bound = bind(f.read(), work)
    assert bound is not None, "the binder rejected the tune"
    workspace = WORK + 0x100
    assert Machine(code, symbols, bound).call("init", a0=FILE, a1=workspace) == 0, \
        "init rejected a tune the handlers reach"
    far = WORK - 0x10000
    m = Machine(code, symbols, bound)
    m.mu.mem_write(far, bound)
    m.image(far, bound)
    assert m.call("init", a0=far, a1=workspace) & 0xFFFFFFFF == 0xFFFFFFFF, \
        "init took a tune %d bytes from the handlers" % (far - CODE)
    return "a tune %d bytes off: init reports -1" % (far - CODE)


def wholebyte(code, symbols):
    """A source whose one column fills its byte (SPEC.md 3.1): setR0 is
    the tone's fine byte, which reads every bit, so its rows carry no
    marker and the player counts them. Bit 31 of the source's index entry
    marks it, in a tune of version 7.

    The rows here run to 255, which the marker's 0 to 127 could not carry,
    so this tune is one no version before wrote. The model counts the rows
    as the handler does and check reads the player against it, handler,
    place, counter and vector.
    """
    rows = 24
    def column(ats, value):
        return [value if r in ats else -1 for r in range(rows)]
    starts = (0, 12)
    tune = {"format": "ymxs", "version": 4, "tunes": [{
        "title": "A column that fills its byte", "composer": "",
        "writer": "68k/test/emu/test_ymxr.py", "rate": 50, "rows": rows,
        "repeat": 0,
        "sources": [
            {"name": "sweep", "repeat": 0,
             "values": [200, 220, 240, 255, 240, 220]},
            {"name": "fall", "repeat": None,
             "values": [128, 160, 192, 224]}],
        "registers": {"r7": column((0,), 0x3E), "r1": column((0,), 1),
                      "r8": column((0,), 12)},
        "timerA": {"shape": column(starts, 0), "target": column(starts, 0),
                   "source": [1 if r == 0 else (2 if r == 12 else -1)
                              for r in range(rows)],
                   "prescaler": column(starts, 50),
                   "count": column(starts, 100), "timerReset": column(starts, 1),
                   "placeReset": column(starts, 1)}}]}
    at, version = built("wholebyte", tune)
    assert version == WRITTEN, \
        "a tune with a counted source is version 7 (SPEC.md 3.3.5)"
    frames, ticks = check(at, code, symbols)[:2]
    return ("a column that fills its byte: %d frames, %d ticks"
            % (frames, ticks))


def handler_at(m, symbols, name, i):
    """Resolve effect i's handler by shape name: tick, sq, one, cnt, two,
    three or env; lookup kinds use lk, lc, lt, ln, lb, lv, le or lf.
    Select the bank containing the vector, or bank 0 when neither
    matches (68k/player/handlers.S, BANK).
    """
    at = CODE + symbols["ymxr_%s%d" % (name, i)]
    if "ymxr_bank1" not in symbols:
        # a player without the deferred start has bank 0 alone
        return at
    bank = symbols["ymxr_bank1"] - symbols["ymxr_tick0"]
    return at + bank if m.long(TIMER[i]["vector"]) == at + bank else at


def lookup_kind(model, i):
    """Lookup kind for the running source, using the target captured at its
    start (68k/player/lookup_kinds.S, ymxr_kinds). One-column sources use lc
    when counted, otherwise lk. Two-column tone/envelope sources map the
    last write, lt marked or le counted; noise maps the first write, ln
    marked or lf counted. Three-column buzzer maps the last write, lb; voice
    maps the middle write, lv.
    """
    using = model.fx[i]["using"]
    columns = model.wide(i)
    first = using in (22, 23, 24)
    if model.counted(i):
        return ("lf" if first else "le") if columns else "lc"
    if columns == 2:
        return "ln" if first else "lt"
    if columns == 3:
        return "lb" if using == 21 else "lv"
    return "lk"


def lookup_regions(symbols):
    """Cycle-counter regions for lookup preparation: page selection
    (ymxr_page), the fill a start or a remap between two transformations
    makes of the page not in use (ymxr_lfill), table/entry reads
    (ymxr_tab, ymxr_entry), filling (ymxr_fill), identity/lookup
    movement (ymxr_move), atomic page switch (ymxr_tickN_rpage), and
    multi-column place reset (ymxr_widereset). Count complete start/remap
    routines separately from call to return (check), since ymxr_rem is
    shared with identity starts.
    """
    at = {name: CODE + symbols[name] for name in (
        "ymxr_page", "ymxr_lfill", "ymxr_tab", "ymxr_fill", "ymxr_runrow",
        "ymxr_move", "ymxr_widereset", "ymxr_widestart")}
    # the page byte's write of each effect's remap, in its frame code: in
    # the remap's piece, which a linked player keeps where the effect maps
    # a value (doc/LINKER.md)
    switches = {"switch%d" % i: (CODE + symbols["ymxr_tick%d_rpage" % i],
                                 CODE + symbols["ymxr_tick%d_remapped" % i])
                for i in range(4) if "ymxr_tick%d_rpage" % i in symbols}
    return dict(switches, **{
        "page": (at["ymxr_page"], at["ymxr_tab"]),
        "fill": (at["ymxr_lfill"], at["ymxr_page"]),
        "table": (at["ymxr_tab"], at["ymxr_fill"]),
        "copy": (at["ymxr_fill"], at["ymxr_runrow"]),
        "move": (at["ymxr_move"], at["ymxr_lfill"]),
        "reset": (at["ymxr_widereset"], at["ymxr_widestart"])})


# The routines of a start or a remap through a transformation, each
# counted from its call to its return: the start, at the frame and armed,
# and the remap, from its head or past its tests; and the remap in each
# effect's frame code, counted from its first instruction to the branch it
# ends at, or to the return of the routine it calls.
ROUTINES = ("ymxr_lstart", "ymxr_remap", "ymxr_rmove")
REMAPS = ("ymxr_tick%d_remap", "ymxr_tick%d_remapped")


def routine_calls(code, symbols):
    """Return addresses after each bsr.w calling a routine of ROUTINES,
    and the end of each effect's remap (REMAPS).
    """
    called = {symbols[name] for name in ROUTINES if name in symbols}
    return [at + 4 for at in range(0, len(code) - 3, 2)
            if code[at:at + 2] == b"\x61\x00"
            and at + 2 + struct.unpack(">h", code[at + 2:at + 4])[0] in called] + [
        symbols[REMAPS[1] % i] for i in range(4) if REMAPS[1] % i in symbols]


def shape_of(model, i):
    """Running handler shape for effect i: square, one, or a stepping
    lookup kind from lookup_kind. Return None when idle.
    """
    fx = model.fx[i]
    if not fx["running"] or fx["source"] == 0:
        return None
    if model.square(i):
        return "square"
    if model.onerow(i):
        return "one"
    return lookup_kind(model, i)


def lookup_events(model, before, row):
    """Classify row lookup events from the model before and after it.
    Return (effect, operation, shape) entries. Starts distinguish
    reset-to-zero from preserved place. Remaps distinguish stopped,
    unchanged, table-to-table switch, from identity and to identity
    (SPEC.md 1.10.2).
    """
    events = []
    for i in range(4):
        t = EFFECT + 4 * i
        mapping = row[C + i] if len(row) > C else 0
        fx = model.fx[i]
        shape, ran = before[i]
        if row[t + 1] & 0x80:
            if row[t + 1] & 0x7F and fx["running"] and fx["run"] and not fx["pending"]:
                kept = row[t + 2] & 0xA0 != 0xA0
                events.append((i, "start, the place kept" if kept else "start",
                               shape_of(model, i)))
        elif mapping & 0x80:
            if shape is None:
                what = "stopped"
            elif fx["run"] == ran:
                what = "same"
            elif not fx["run"]:
                what = "to the identity"
            elif not ran:
                what = "from the identity"
            else:
                what = "switch"
            events.append((i, what, shape))
    return events


def page_of(m, symbols, at, name):
    """Resolve the page read by lookup instance at, kind name, from its
    displacement word with key 0 (68k/player/lookup_ticks.S, LKEY).
    """
    word = at + symbols[name.upper() + "_KEY"] - 1
    high = m.byte(word)
    return word + ((high << 8) - (0x10000 if high & 0x80 else 0))


def pairs(m, symbols):
    """Count and validate each lookup instance's key write followed by its
    page read. The key write targets the read's low displacement byte;
    source addressing is program-counter or absolute according to the
    build. Unicorn checks instruction order; the real-ST program checks
    prefetch behavior (68k/test/st).
    """
    count = 0
    for name, at in symbols.items():
        one = re.match(r"ymxr_(l[kctnbvef])[0-3]b?$", name)
        if not one:
            continue
        key = CODE + at + symbols[one.group(1).upper() + "_KEY"]
        read = key - 3
        write = read - (8 if PCREL else 10)
        assert m.word(write) == (0x13FA if PCREL else 0x13F9), \
            "%s writes its key with %04x" % (name, m.word(write))
        assert (m.word(read), m.word(read + 4)) == (0x11FA, 0x8802), \
            "%s reads its page with %04x %04x" % (name, m.word(read), m.word(read + 4))
        assert m.long(read - 4) == key, \
            "%s writes its key at %x, and the page read's key is at %x" % (
                name, m.long(read - 4), key)
        count += 1
    return count


def running(m, symbols, model, i, said, ticked=False):
    """Compare the running handler with the model: immediate levels for
    square/one-row sources; place and remaining count for stepping
    sources; selected lookup instance and page; first target register
    and vector. After a tick, omit register/vector checks because those
    remain unchanged. Prefix diagnostics with said and record lookup
    handlers in RAN.
    """
    fx = model.fx[i]
    if model.square(i):
        name = "sq"
        at = handler_at(m, symbols, "sq", i)
        assert m.byte(at + SQ_VAL) == model.value(i), \
            "%seffect %d's square is at %02x, not %02x" % (
                said, i, m.byte(at + SQ_VAL), model.value(i))
        # the two rows' difference, which moves the immediate between them
        mask = model.value(i, 0) ^ model.value(i, 1)
        assert m.byte(at + SQ_MASK) == mask, \
            "%seffect %d's square moves by %02x, not %02x" % (
                said, i, m.byte(at + SQ_MASK), mask)
        sel = SQ_SEL
    elif model.onerow(i):
        name = "one"
        at = handler_at(m, symbols, "one", i)
        assert m.byte(at + ONE_VAL) == model.value(i), \
            "%seffect %d's one row is %02x, not %02x" % (
                said, i, m.byte(at + ONE_VAL), model.value(i))
        sel = ONE_SEL
    else:
        # every handler that steps a place has it at TICK_PTR and its first
        # register at TICK_SEL (68k/player/handlers.S, HANDLEQU, and
        # lookup_checks.S, the assertions after it)
        if fx["run"]:
            name = lookup_kind(model, i)
        elif model.counted(i):
            name = "env" if model.wide(i) else "cnt"
        else:
            name = {0: "tick", 2: "two", 3: "three"}[model.wide(i)]
        at = handler_at(m, symbols, name, i)
        place = model.place_address(i)
        assert m.place(at, TICK_PTR) == place, \
            "%seffect %d's place is %x, not %x" % (said, i, m.place(at, TICK_PTR), place)
        if model.counted(i):
            left = symbols[name.upper() + "_LEFT"] if fx["run"] else (
                TWC_LEFT if model.wide(i) else TICKC_LEFT)
            assert m.word(at + left) == model.left(i), \
                "%seffect %d has %d rows left, not %d" % (
                    said, i, m.word(at + left), model.left(i))
        if fx["run"]:
            pages(m, symbols, model, i, at, name, said)
        sel = TICK_SEL
    if fx["run"]:
        RAN.add(name)
    if ticked:
        return
    first = TARGETS[fx["using"]][0][0]
    assert m.byte(at + sel) == first, \
        "%seffect %d's handler selects R%d first, not R%d" % (said, i, m.byte(at + sel), first)
    assert m.long(TIMER[i]["vector"]) == at, \
        "%seffect %d's vector is at %x, not at its handler at %x" % (
            said, i, m.long(TIMER[i]["vector"]), at)


def pages(m, symbols, model, i, at, name, said):
    """Verify the running instance's page against the model transformation.
    Plain pages copy entries from 0. Marker pages duplicate up to 128
    entries at offset 128 with bit 7 set. setR7 pages place keys 0 to 63
    at offset 192 with both port bits set (SPEC.md 2.1.5; rule 2(f)).
    """
    fx = model.fx[i]
    base = page_of(m, symbols, at, name)
    fill = model.fill(i)
    form = {"plain": 0, "mirror": 1, "ports": 2}[fill]
    # a start through a transformation finds its page in the list the
    # binder wrote, which init put in the area (BINARIES.md 1.2, 3.1)
    assert (i, form, fx["run"]) in model.tune.pages, \
        "%seffect %d runs transformation %d in form %d, outside the list %s" % (
            said, i, fx["run"], form, model.tune.pages)
    want = page_at(symbols, m.area, model.tune, i, form, fx["run"], m.reach)
    if isinstance(want, list):
        # a page init left out, in the effect's fallback (BINARIES.md 5.6)
        assert base in want, "%seffect %d's %s instance reads %x, outside its fallback at %s" % (
            said, i, name, base, [hex(one) for one in want])
        FELL.add(i)
    else:
        assert base == want, \
            "%seffect %d's %s instance reads %x, not its page at %x" % (said, i, name, base, want)
    entries = model.tune.transformations[fx["run"] - 1]
    page = bytes(m.mu.mem_read(base, 256))
    if fill == "plain":
        want = dict(enumerate(entries))
    elif fill == "mirror":
        half = entries[:128]
        want = dict(enumerate(half))
        want.update({128 + k: e | 0x80 for k, e in enumerate(half)})
    else:
        want = {192 + k: e | 0xC0 for k, e in enumerate(entries[:64])}
    wrong = [k for k, e in sorted(want.items()) if page[k] != e]
    assert not wrong, "%seffect %d's page has %02x at %d, not %02x of transformation %d (%s)" % (
        said, i, page[wrong[0]], wrong[0], want[wrong[0]], fx["run"], fill)


def check(ym, code, symbols, cycles=None, kit=False, perf=False, parts=False,
          ticked=None, spans=None):
    """The tune on the player, against the model frame by frame and tick
    by tick; with kit, each frame against the reader's entry as well,
    and a tune that plays once to the call that reports its end. With
    ticked, a list, each tick is added to it as (frame, effect, source,
    row, writes): the row the tick read, the writes the player made.
    With cycles and spans, a list, each start and each remap at the
    frame is added to it as (frame, effect, label, cycles): the cycles to
    the effect's rate from its start in the frame code (EFFECT, _now),
    label "now", or from its branch on the set bit of its source column
    (_unset), label "unset".

    With parts, the counter splits ST4's decoder out of the advance and
    counts the operations each refill parses, so one pass over the frames
    reads the table's figures and the parts below it (refills)."""
    work = tempfile.mkdtemp()
    file, report = convert(ym, work)
    workspace = WORK + 0x100
    version = struct.unpack(">H", file[4:6])[0]
    if version not in TUNE_VERSIONS:
        # the binder and the reader reject the file; the player, which never
        # sees it, rejects a bound tune whose version word is moved past the
        # versions it reads
        assert bind(file, work) is None, "the binder took a tune file of version %d" % version
        if kit:
            assert trace(file, work, 1) == [], "the reader reports something of version %d" % version
        # bound at 9, the version of a table of 30 columns
        bound = bind(file[:4] + struct.pack(">H", WRITTEN) + file[6:], work)
        assert bound is not None, "the file is not a tune of the version it records"
        assert Machine(code, symbols, bound).call("init", a0=FILE, a1=workspace) == 0, \
            "init rejected the bound tune before its version was moved"
        past = max(bound_versions(symbols)) + 1
        moved = bound[:4] + struct.pack(">H", past) + bound[6:]
        m = Machine(code, symbols, moved)
        assert m.call("init", a0=FILE, a1=workspace) & 0xFFFFFFFF == 0xFFFFFFFF, \
            "init took a bound tune of version %d" % past
        return 0, 0, [], {}, 0, (0, 0, 0, [], [], 0, b"", (0, 0), [], 0), [], None
    bound = bind(file, work)
    assert bound is not None, "the binder rejected the tune"
    tune = Tune(bound, work)
    assert tune.version == version, "the binder wrote version %d of a tune file of %d" % (
        tune.version, version)
    defer, lookup = needs(tune.effects)
    have = ("ymxr_bank1" in symbols, "ymxr_look" in symbols)
    if (defer and not have[0]) or (lookup and not have[1]):
        # First verify that init rejects a tune when its required
        # deferred or lookup support is absent. Then run it on the build
        # with that support (BINARIES.md 1.5).
        assert Machine(code, symbols, bound).call("init", a0=FILE, a1=workspace) \
            & 0xFFFFFFFF == 0xFFFFFFFF, \
            "init of a player without the switch a tune of effects %s needs took it" % (
                format(tune.effects, "06b"))
        code, symbols = build(switched(DEFINES, defer or have[0], lookup or have[1]))
    m = Machine(code, symbols, bound)
    if cycles:
        # The reader image starts at header offset 16 and ends at the
        # transformation block or bound file end. Count cycles there as
        # reader work. Count effect steps from ymxr_frame to ymxr_regs
        # and register steps from there to ymxr_played; dense replaces
        # those two regions.
        regions = None if perf else {
            "effects": (CODE + symbols["ymxr_frame"], CODE + symbols["ymxr_regs"]),
            "registers": (CODE + symbols["ymxr_regs"], CODE + symbols["ymxr_played"])}
        if regions and "ymxr_look" in symbols:
            regions.update(lookup_regions(symbols))
        cycles.attach(m, (FILE + tune.image_at, FILE + tune.image_end),
                      *(decoder_of(file, bound, tune, cycles.module)
                        if parts else ()), regions=regions)
    # each copy into a page, as (its fill kind, E), a frame's copies kept
    # beside its starts and remaps (lookup_events)
    copies = []
    if cycles and not perf and "ymxr_look" in symbols:
        def copied(mu, address, size, data):
            table = mu.reg_read(UC_M68K_REG_A2)
            copies.append((mu.reg_read(UC_M68K_REG_D5) & 0xFF,
                           struct.unpack(">H", bytes(mu.mem_read(table, 2)))[0]))
        m.mu.hook_add(UC_HOOK_CODE, copied, begin=CODE + symbols["ymxr_fill"],
                      end=CODE + symbols["ymxr_fill"])
        # init's copies, each from its call to its return into the prefill
        filling = {}

        def fill_from(mu, address, size, data):
            filling.update(at=cycles.counter.cycles, key=(
                mu.reg_read(UC_M68K_REG_D5) & 0xFF,
                struct.unpack(">H", bytes(mu.mem_read(mu.reg_read(UC_M68K_REG_A2), 2)))[0]))

        def filled(mu, address, size, data):
            if filling:
                INIT_COPIES.setdefault(filling["key"], set()).add(
                    cycles.counter.cycles - filling["at"])
        m.mu.hook_add(UC_HOOK_CODE, fill_from, begin=CODE + symbols["ymxr_fill"],
                      end=CODE + symbols["ymxr_fill"])
        m.mu.hook_add(UC_HOOK_CODE, filled, begin=CODE + symbols["ymxr_filled"],
                      end=CODE + symbols["ymxr_filled"])
    # Measure each call of a routine of ROUTINES from entry after bsr
    # to the instruction after its rts (routine_calls).
    routine = {"cycles": 0, "at": None}
    looking = ()
    if cycles and "copy" in cycles.regions:
        looking = tuple(lookup_regions(symbols)) + ("routine",)
        def entered(mu, address, size, data):
            # the remap of the frame code counts the routine it calls
            if routine["at"] is None:
                routine["at"] = cycles.counter.cycles

        def returned(mu, address, size, data):
            if routine["at"] is not None:
                routine["cycles"] += cycles.counter.cycles - routine["at"]
                routine["at"] = None
        for name in ROUTINES + tuple(REMAPS[0] % i for i in range(4)):
            if name in symbols:
                m.mu.hook_add(UC_HOOK_CODE, entered, begin=CODE + symbols[name],
                              end=CODE + symbols[name])
        for at in routine_calls(code, symbols):
            m.mu.hook_add(UC_HOOK_CODE, returned, begin=CODE + at, end=CODE + at)
    lookups = []
    # a start of a marked source of one column at row 0, through the
    # identity or a transformation, counted from the frame's start path
    # (EFFECT, _mapped) to the rate it ends at: (path, cycles) a frame
    begun = {"at": None, "path": None, "starts": []}
    if looking:
        sources = equate("WS_SOURCES", symbols)

        def mapped_start(mu, address, size, data):
            cell = struct.unpack(">i", bytes(mu.mem_read(
                mu.reg_read(UC_M68K_REG_A0) + sources - 4
                + 8 * (mu.reg_read(UC_M68K_REG_D1) & 0x7F), 4)))[0]
            row0 = mu.reg_read(UC_M68K_REG_D2) & 0x20
            begun.update(at=cycles.counter.cycles if cell >= 0 and row0 else None, path=None)

        def through(path):
            def mark(mu, address, size, data):
                if begun["at"] is not None and begun["path"] is None:
                    begun["path"] = path
            return mark

        def started(mu, address, size, data):
            if begun["at"] is not None and begun["path"]:
                begun["starts"].append((begun["path"], cycles.counter.cycles - begun["at"]))
            begun["at"] = None
        for i in range(4):
            for name, hook in (("ymxr_tick%d_mapped", mapped_start),
                               ("ymxr_tick%d_start", through("identity")),
                               ("ymxr_tick%d_rate", started)):
                m.mu.hook_add(UC_HOOK_CODE, hook, begin=CODE + symbols[name % i],
                              end=CODE + symbols[name % i])
        m.mu.hook_add(UC_HOOK_CODE, through("lookup"), begin=CODE + symbols["ymxr_lstart"],
                      end=CODE + symbols["ymxr_lstart"])
    # each start and each remap at the frame, from _now and from _unset to
    # _rate, by the frame it is in
    playing = {"frame": None, "at": {}}
    if cycles and spans is not None:
        def opened(i, label):
            def hook(mu, address, size, data):
                playing["at"][(i, label)] = cycles.counter.cycles
            return hook

        def rate(i):
            def hook(mu, address, size, data):
                for label in ("now", "unset"):
                    if (i, label) in playing["at"]:
                        spans.append((playing["frame"], i, label, cycles.counter.cycles
                                      - playing["at"].pop((i, label))))
            return hook
        for i in range(4):
            for name, hook in (("ymxr_tick%d_now", opened(i, "now")),
                               ("ymxr_tick%d_unset", opened(i, "unset")),
                               ("ymxr_tick%d_rate", rate(i))):
                m.mu.hook_add(UC_HOOK_CODE, hook, begin=CODE + symbols[name % i],
                              end=CODE + symbols[name % i])
    model = Model(tune)
    timers = Timers.of_machine(m, tune.effects)
    assert m.call("init", a0=FILE, a1=workspace) == 0, "init rejected the tune"
    # the frame procedure as init patched it: a constant column's read is
    # an immediate (68k/player/init.S, ymxr_put)
    patched = bytes(m.mu.mem_read(CODE, len(code)))
    if "ymxr_look" in symbols:
        pairs(m, symbols)
    # Timer C's nibble is the host's 200 Hz clock, kept unless effect 3 runs
    nibble = 0 if tune.effects & 8 else 0x50
    assert m.byte(MFP + 0x1D) & 0x70 == nibble, "init moved Timer C's nibble"
    timers.apply(m.mfp)
    for i in range(4):
        if tune.effects & 1 << i:
            assert timers.enabled(i), "effect %d's timer is not enabled" % i
            assert m.long(TIMER[i]["vector"]) == CODE + symbols["ymxr_tick%d" % i], \
                "effect %d's vector" % i
            # A tick of a timer no row has started (SPEC.md 5.2.1): init
            # leaves the place at a marker and the register at R0, so such
            # a tick writes $80 to R0 and stops the timer. Rule 4(c) keeps
            # a tune clear of one, and one here leaves the player as init
            # left it: the place remains and the timer was stopped already.
            m.interrupt(TIMER[i]["vector"])
            assert m.psg == [(0, 0x80)], \
                "effect %d: a tick with no source connected wrote %s" % (i, m.psg)
            timers.apply(m.mfp)
            assert timers.mode[i] == 0, \
                "effect %d: a tick with no source connected left its timer running" % i
    ticks = 0
    tick_cost = {}
    tick_cycles = 0                     # every tick's instructions
    costliest = [0]
    advance = []
    clocks = MFP_CLOCK / tune.rate
    once = tune.RR == tune.R
    frames = tune.R + 1 if once else min(tune.R + tune.R - tune.RR, 4 * tune.R)
    # -frames caps a long tune: every frame is emulated, so a tune of ten
    # thousand rows is minutes by itself. A capped run reads the frames it
    # plays against the model as a whole run does, and reaches neither the
    # wrap nor the end.
    if MOST_FRAMES and frames > MOST_FRAMES:
        frames = MOST_FRAMES
        once = False
    entries = trace(file, work, frames) if kit else None
    if kit:
        assert len(entries) == frames + 1, "the reader writes %d lines, not %d" % (len(entries), frames + 1)
        first = {"rate": tune.rate, "effects": tune.effects,
                 # a byte a column of the row, row 0's columns first
                 # (SPEC.md 7.2)
                 "sources": [{"rows": [c[r] for r in range(r_) for c in columns],
                              "repeat": rr}
                             for _, r_, rr, columns in tune.sources[1:]]}
        if tune.version == MAPPED:
            # each transformation's entries, transformation 1 first (7.2)
            first["transformations"] = tune.transformations
        assert entries[0] == first, "the reader's first line is %s, the tune header is %s" % (entries[0], first)
        entries = entries[1:]
    cost = []
    refills_of = []
    # each frame's cycles in the effects' steps and in the register
    # columns', and whether its row sets a column of any effect
    steps = []
    for f in range(frames):
        playing["frame"] = f
        if cycles:
            cycles._settle(None)
        spent_before = dict(cycles.spent) if cycles else {}
        spent_before["routine"] = routine["cycles"]
        copies.clear()
        before = cycles.cycles if cycles else 0
        image_before = cycles.image if cycles else 0
        decoder_before = cycles.decoder if cycles else 0
        parsed_before = sum(cycles.hits.values()) if cycles else 0
        d0 = m.call("play", a0=workspace)
        if cycles:
            cycles._settle(None)
            cost.append(cycles.cycles - before)
            advance.append(cycles.image - image_before)
            if parts and d0 == 0:
                # the call after the last row of a tune that plays once
                # leaves every decoder as it is, so it is left out here
                within = cycles.decoder - decoder_before
                refills_of.append((cycles.image - image_before - within, within,
                                   sum(cycles.hits.values()) - parsed_before))
            if cost[-1] == max(cost):
                costliest[0] = f
        if once and f == tune.R:
            # the call after the last row: the end reported, no register written
            assert d0 & 0xFFFFFFFF == 0xFFFFFFFF, \
                "play gave %d after the last row of a tune that plays once" % d0
            assert not m.psg, "the call after the last row wrote %s" % m.psg
            if kit:
                assert entries[f] == {"result": -1}, "the reader's last entry is %s" % entries[f]
            break
        assert d0 == 0, "play gave %d at frame %d" % (d0, f)
        if perf:
            # the monitor marks the call's work and burns the timers' bar
            # after it, so the two marks are around every chip write
            colours = [colour for colour, _ in m.palette]
            assert colours[:1] == [PERF_FRAME] and PERF_BAR in colours, \
                "frame %d: the monitor's marks are %s" % (f, [hex(x) for x in colours])
            red = m.palette[0][1]
            assert red == 0, \
                "frame %d: the red mark falls on chip write %d, not before the first" % (f, red)
            yellow = next(before for colour, before in m.palette if colour == PERF_BAR)
            assert yellow == len(m.psg), \
                "frame %d: the yellow mark falls on chip write %d of %d, not after the last" % (
                    f, yellow, len(m.psg))
        if looking:
            shapes = [(shape_of(model, i), model.fx[i]["run"]) for i in range(4)]
            row = model.tune.rows[model.row]
        want = model.frame()
        if looking:
            spent = dict(cycles.spent, routine=routine["cycles"])
            delta = {name: spent[name] - spent_before[name] for name in looking}
            delta["starts"] = tuple(begun["starts"])
            begun["starts"].clear()
            lookups.append((lookup_events(model, shapes, row), delta, list(copies)))
        if cycles and cycles.regions:
            steps.append((cycles.spent["effects"] - spent_before["effects"],
                          cycles.spent["registers"] - spent_before["registers"],
                          any(model.fx[i]["set"] for i in range(4)), model.playing))
        assert masked(m.psg) == masked(want), "frame %d writes %s, not %s" % (f, m.psg, want)
        if kit:
            assert entries[f] == entry(model, want), "frame %d: the reader reports %s, the player %s" % (
                f, entries[f], entry(model, want))
        restarts = list(timers.restarts)
        stopped = [mode == 0 for mode in timers.mode]
        timers.apply(m.mfp)
        for i in range(4):
            fx = model.fx[i]
            # A restart is due where the row sets bit 6, and where the timer
            # was stopped and the row writes it a select: a select
            # runs an MFP timer, so a stopped one starts on it either way
            # (SPEC.md 1.9). A source that plays once stops its timer at
            # its last row, so a writer cannot always determine which
            # (section 6 rule 5).
            if fx["restart"] or (stopped[i] and fx["selected"]):
                assert timers.restarts[i] == restarts[i] + 1, \
                    "frame %d: effect %d's timer was not restarted" % (f, i)
            else:
                # bit 6 clear and the timer running: a row that retunes or
                # reaims a running effect leaves the count it is running (1.9)
                assert timers.restarts[i] == restarts[i], \
                    "frame %d: effect %d's timer restarted where the row sets no bit 6" % (f, i)
            if fx["running"]:
                assert timers.mode[i] == fx["select"], "frame %d: effect %d runs at select %d, not %d" % (f, i, timers.mode[i], fx["select"])
                # The timer counts the ticks its data register names, and a
                # register of 0 counts 256 (SPEC.md 1.9), so the column's
                # count is read as ticks to compare with what the MFP counts.
                ticks = fx["count"] or 256
                assert (timers.pending[i] or timers.count[i]) == ticks, \
                    "frame %d: effect %d counts %d ticks, not %d" % (
                        f, i, timers.pending[i] or timers.count[i], ticks)
            elif fx["source"] == 0 and tune.effects & 1 << i:
                assert timers.mode[i] == 0, "frame %d: effect %d's timer runs with no source" % (f, i)
            if model.place_address(i) is not None and fx["running"]:
                running(m, symbols, model, i, "frame %d: " % f)
        for i in timers.due(clocks):
            fx = model.fx[i]
            assert fx["running"], "frame %d: a tick of effect %d with no source running" % (f, i)
            read = (fx["source"], fx["place"])
            # a tick through a transformation, whose kind the counts keep
            # apart from the identity's
            mapping = fx["run"]
            want, then = model.tick(i)
            if cycles:
                cycles._settle(None)
            before = cycles.cycles if cycles else 0
            m.interrupt(TIMER[i]["vector"])
            if cycles:
                cycles._settle(None)
                kind = then
                if model.square(i):
                    kind = "square"
                elif model.onerow(i):
                    kind = "one"
                elif model.counted(i):
                    # a counted handler, of one column or of several
                    kind = ("counted wide " if model.wide(i) else "counted ") + then
                elif model.wide(i) == 3:
                    kind = "three " + then
                elif model.wide(i):
                    kind = "wide " + then
                if mapping:
                    kind = "mapped " + kind
                if bin(tune.effects & 0x0F).count("1") == 1:
                    kind += " alone"        # init took the level's drop out
                tick_cost.setdefault(kind, set()).add(cycles.cycles - before)
                tick_cycles += cycles.cycles - before
            ticks += 1
            assert masked(m.psg) == masked(want), "frame %d: tick of effect %d wrote %s, not %s" % (f, i, m.psg, want)
            if ticked is not None:
                ticked.append((f, i) + read + (list(m.psg),))
            timers.apply(m.mfp)
            if then == "stop" and not model.square(i) and not model.onerow(i):
                assert timers.mode[i] == 0, \
                    "frame %d: effect %d ran out and its timer runs on" % (f, i)
            else:
                running(m, symbols, model, i, "frame %d: after a tick " % f, ticked=True)
    boundaries = inside(code, symbols, bound, tune, workspace)
    m.call("stop", a0=workspace)
    timers.apply(m.mfp)
    for i in range(4):
        if tune.effects & 1 << i:
            assert timers.mode[i] == 0 or i == 3, "stop left effect %d's timer running" % i
    assert m.byte(MFP + 0x1D) & 0x70 == nibble, "stop moved Timer C's nibble"
    return (frames, ticks, cost, tick_cost, tick_cycles,
            (costliest[0], tune.R, tune.RR, advance, steps, tune.effects, patched,
             (len(bound), long_at(bound, 12)), lookups, len(tune.rows[0])),
            boundaries, refill_parts(refills_of, file, bound, tune))


def refill_parts(rows, file, bound, tune):
    """parts_of a run's frames, with what the table's payload marks: a
    DTX3 table at the tune file's field at 12, its flags at 19 past it
    (DTX's SPEC.md 2.4), bit 1 a loop longer than the ring, replayed, and
    L, the live columns, at 20; the loop's rows; and the period, P, the
    turns a refill comes round in, and the unit, k, which the bound tune's
    image has in its format block at 16, at +14 and +18 (DTX, abi.md 1)."""
    if not rows:
        return None
    parts = parts_of(rows)
    table = long_at(file, 12)
    parts["replayed"] = bool(file[table + 19] & 2)
    parts["live"] = struct.unpack(">H", file[table + 20:table + 22])[0]
    parts["loop"] = tune.R - tune.RR
    parts["period"], parts["unit"] = image_period(bound)
    return parts


def image_period(bound):
    """P and k of a bound tune's image, out of its format block, 16 bytes
    into the image (DTX, abi.md 1): the period the packager set, the
    column count or above, and the unit. The image is at the field at 16
    (BINARIES.md 1.1)."""
    block = long_at(bound, 16) + 16
    return struct.unpack(">H", bound[block + 14:block + 16])[0], bound[block + 18]


WRITE = re.compile(r"ym write data reg=0x([0-9a-f]+) val=0x([0-9a-f]+) .* pc=([0-9a-f]+)")
MFPW = re.compile(r"mfp write \S+ ([0-9a-f]+)=0x([0-9a-f]+) video_cyc=(\d+) .* pc=([0-9a-f]+)")
VBLA = re.compile(r"^VBL=(\d+) clock=(\d+)")
# the video cycle the VBL comes at, and the cycles it is pending: a
# write's video_cyc counts from the video frame's first cycle, a few dozen
# before the VBL, and the exception, the line above, waits for a handler
# of a higher level to return
VBLV = re.compile(r"^VBL \d+ video_cyc=(\d+) pending_cyc=(\d+)")
VIDEO_CYC = re.compile(r"video_cyc=(\d+)")
CYCLE = re.compile(r"video_cyc=(\d+)")

# The ST's CPU, cycles a second: the clock a trace counts in.
CPU_CLOCK = 8021247.0
ROM = 0xE00000


# The words performance.md spells a small count with, so the rig reads the
# sentence back as it is.
WORDS = {1: "one", 2: "two", 3: "three", 4: "four", 5: "five", 6: "six",
         7: "seven", 8: "eight", 9: "nine", 10: "ten", 11: "eleven",
         12: "twelve", 13: "thirteen", 14: "fourteen", 15: "fifteen",
         16: "sixteen", 17: "seventeen", 18: "eighteen", 19: "nineteen",
         20: "twenty", 21: "twenty-one", 22: "twenty-two", 23: "twenty-three",
         24: "twenty-four", 25: "twenty-five", 26: "twenty-six",
         27: "twenty-seven", 28: "twenty-eight", 29: "twenty-nine",
         30: "thirty", 31: "thirty-one", 32: "thirty-two", 33: "thirty-three",
         34: "thirty-four"}


def footprint_read(footprints):
    """performance.md's bound tunes and state blocks, a fixture a row,
    against the binder's and the field at 12 of what it bound (BINARIES.md
    1.1); the release's columns beside them are that release's, and the
    last column is the two together against them."""
    text = open(os.path.join(ROOT, "doc", "performance.md")).read()
    stale = []
    for stem, (bound, state) in sorted(footprints.items()):
        row = re.search(r"^\| %s \| (\d+) \| (\d+) \| (\d+) \| (\d+) \| (-?\d+)%% \|$"
                        % re.escape(stem), text, re.M)
        if not row:
            stale.append("performance.md has no footprint of " + stem)
            continue
        was_bound, was_state, said_bound, said_state, share = (int(x) for x in row.groups())
        got = round(100.0 * (bound + state) / (was_bound + was_state) - 100)
        if (said_bound, said_state, share) != (bound, state, got):
            stale.append("performance.md reads %d, %d and %d%% for %s's footprint, and the rig"
                         " counts %d, %d and %d%%" % (said_bound, said_state, share, stem,
                                                      bound, state, got))
    return stale


def dense_read(savings, costs):
    """performance.md's dense register columns against the rig: the write
    and the test the section names, the old test, and what the columns
    cost or save a frame over the eleven fixtures, a cost the least to the
    most on the tunes they cost and a saving the least to the most on the
    rest, each figure rounded to a cycle."""
    said = " ".join(open(os.path.join(ROOT, "doc", "performance.md")).read().split())
    stale = []
    m = re.search(r"each register written as the player writes a set column, in (\d+) cycles,"
                  r" and the effects' steps behind a test of (\d+), that costs (\d+) to (\d+)"
                  r" cycles a frame on (\w+) of the (\w+) fixtures and saves (\d+) to (\d+) on"
                  r" the other (\w+),", said)
    if not m:
        return ["performance.md has no count of dense register columns"]
    rounded = [int(round(saved)) for saved, _ in savings.values()]
    cost = sorted(-r for r in rounded if r < 0)
    save = sorted(r for r in rounded if r >= 0)
    counted = (str(costs["write"]), str(costs["skip"]), str(cost[0]), str(cost[-1]),
               WORDS[len(cost)], WORDS[len(rounded)], str(save[0]), str(save[-1]),
               WORDS[len(save)]) if cost and save else None
    if not near(m.groups(), counted):
        stale.append("performance.md reads dense register columns as %s, and the rig counts %s"
                     % (m.groups(), counted))
    t = re.search(r"applies to a (\d+)-cycle test and not to the (\d+) of a test that forms the"
                  r" select only where it writes", said)
    if not t or not near((int(t.group(1)), int(t.group(2))), (costs["then"], costs["skip"])):
        stale.append("performance.md reads the two tests as %s, and the rig counts %d and %d"
                     % (t and t.groups(), costs["then"], costs["skip"]))
    return stale


# The kit's tunes the section on a counted tick of two columns reads: a
# marked and a counted source on setEnvelope, and a counted source of one
# column on each target whose register reads a whole byte.
ENVELOPE_TUNES = ("envelope.ymxr", "envelope-counted.ymxr")
COUNTED_TUNE = "counted.ymxr"
# The table's rows, each the path its tick runs.
COUNTED_ROWS = {"a row written, the places stepped": "on",
                "the end, the places to row `RR`": "loop",
                "the end, the timer stopped": "stop"}


def counted_read(code, symbols, cycles_of, general):
    """performance.md's section on a counted tick of two columns against the
    rig, for the build assembled: its table's two columns of that build, the
    marked and the counted ticks of the kit's two envelope tunes; a counted
    tick of one column on the kit's counted tune against the general tick
    the fixtures measured (general, by kind); the differences the section
    draws; the handlers' sizes and the player's; and the envelope tune's
    last two periods, the marker added to the last."""
    text = open(os.path.join(ROOT, "doc", "performance.md")).read()
    section = text[text.index("## A counted tick of two columns"):
                   text.index("## A tick through an absolute address")]
    said = " ".join(section.split())
    stale = []
    kit = os.path.join(ROOT, "doc", "conformance", "tunes")
    got = {name: check(os.path.join(kit, name), code, symbols, CyclesOn(cycles_of))[3]
           for name in ENVELOPE_TUNES + (COUNTED_TUNE,)}

    def one(name, kind):
        costs = got[name].get(kind, set())
        if len(costs) != 1:
            stale.append("%s ticks %s at %s, not one figure" % (name, kind, sorted(costs)))
            return None
        return next(iter(costs))

    column = 0 if PCREL else 2
    ticked = {}
    for row, then in COUNTED_ROWS.items():
        m = re.search(r"^\| %s \| (\d+) \| (\d+) \| (\d+) \| (\d+) \|$" % re.escape(row), section, re.M)
        if not m:
            stale.append("performance.md's counted ticks have no row for " + row)
            continue
        counted = (one(ENVELOPE_TUNES[0], "wide %s alone" % then),
                   one(ENVELOPE_TUNES[1], "counted wide %s alone" % then))
        ticked[then] = counted
        read = (int(m.group(column + 1)), int(m.group(column + 2)))
        if not near(read, counted):
            stale.append("performance.md reads the ticks %s as %s, and the rig counts %s"
                         % (row, read, counted))
    d = re.search(r"So (\d+) cycles a tick that writes a row, as the counted handler of one"
                  r" column costs against the general one, and (\d+) on the path that loops", said)
    one_column = one(COUNTED_TUNE, "counted on")
    general_on = general.get("on", set())
    if not d or len(general_on) != 1 or "on" not in ticked or "loop" not in ticked:
        stale.append("performance.md has no difference of a counted tick the rig reads")
    else:
        row_more, loop_more = int(d.group(1)), int(d.group(2))
        if not near(row_more, ticked["on"][1] - ticked["on"][0]) or \
                not near(row_more, one_column - next(iter(general_on))):
            stale.append("performance.md reads %d cycles more a counted tick that writes a row,"
                         " and the rig counts %d on two columns and %d on one"
                         % (row_more, ticked["on"][1] - ticked["on"][0],
                            one_column - next(iter(general_on))))
        if not near(loop_more, ticked["loop"][1] - ticked["loop"][0]):
            stale.append("performance.md reads %d cycles more on the loop, and the rig counts %d"
                         % (loop_more, ticked["loop"][1] - ticked["loop"][0]))
    wide_size = symbols["ymxr_env1"] - symbols["ymxr_env0"]
    marked_size = symbols["ymxr_two1"] - symbols["ymxr_two0"]
    shapes = 4 * (symbols["ymxr_cnt1"] - symbols["ymxr_cnt0"]) + 4 * wide_size
    if DEFER or MAP:
        # the section's sizes are the player without the deferred start
        # and the lookup, and deferred_sizes and mapped_sizes read the
        # other builds'
        pass
    elif PCREL:
        sizes = re.search(r"(\d+) bytes an instance against (\d+)", said)
        player = re.search(r"The player is ([\d,]+) bytes, ([\d,]+) of them the counted shapes'"
                           r" eight instances", said)
    else:
        sizes = re.search(r"instances of (\d+) bytes against the marked shape's (\d+)", said)
        player = re.search(r"where a tick reads an absolute address, ([\d,]+) bytes and ([\d,]+),", said)
    if DEFER or MAP:
        pass
    elif not sizes or (int(sizes.group(1)), int(sizes.group(2))) != (wide_size, marked_size):
        stale.append("performance.md reads the handlers' instances as %s, and the player"
                     " assembles %d and %d" % (sizes and sizes.groups(), wide_size, marked_size))
    elif not player or tuple(int(x.replace(",", "")) for x in player.groups()) != (len(code), shapes):
        stale.append("performance.md reads the player as %s, and it assembles to %d bytes, %d"
                     " of them the counted shapes" % (player and player.groups(), len(code), shapes))
    marker = re.search(r"a period ([\d,]+) more than the row's, which the kit's `envelope` tune"
                       r" does on its first source's last two rows, ([\d,]+) and then ([\d,]+),"
                       r" its ([\d,]+) with the marker", said)
    work = tempfile.mkdtemp(prefix="ymxr68")
    with open(os.path.join(kit, ENVELOPE_TUNES[0]), "rb") as f:
        tune = Tune(bind(f.read(), work), work)
    at, R, RR, columns = tune.sources[1]
    periods = [columns[0][r] | columns[1][r] << 8 for r in range(R)]
    # the marker is bit 7 of R12, the high byte: 1 << 15 of the period
    counted = (1 << 15, periods[-2], periods[-1], periods[-1] - (1 << 15))
    if not marker or tuple(int(x.replace(",", "")) for x in marker.groups()) != counted:
        stale.append("performance.md reads the envelope tune's marker as %s, and the tune has %s"
                     % (marker and marker.groups(), counted))
    return stale


# Lookup tick paths measured against performance.md. The general
# instance alone replaces interrupt-level lowering with two nops for a
# one-effect tune; other lookup kinds use one row for both cases.
def _mapped_rows():
    rows = [("a row written, the place stepped", ("mapped on",)),
            ("the marker, the place to row `RR`", ("mapped loop", "mapped loop alone")),
            ("the marker, the timer stopped", ("mapped stop", "mapped stop alone")),
            ("a row written, the tune running one effect", ("mapped on alone",))]
    for shape, kind, paths in (
            ("counted", "counted", ("a row written", "the end, the place to row `RR`",
                                    "the end, the timer stopped")),
            ("two columns", "wide", ("a row written", "the marker, the places to row `RR`",
                                     "the marker, the timer stopped")),
            ("three columns", "three", ("a row written", "the marker, the places to row `RR`",
                                        "the marker, the timer stopped")),
            ("two counted columns", "counted wide", ("a row written",
                                                     "the end, the places to row `RR`",
                                                     "the end, the timer stopped"))):
        for path, then in zip(paths, ("on", "loop", "stop")):
            rows.append(("%s, %s" % (shape, path),
                         ("mapped %s %s" % (kind, then), "mapped %s %s alone" % (kind, then))))
    return tuple(rows)


MAPPED_TICKS = _mapped_rows()

# Measured remap-frame paths and source shapes, using lookup_events
# labels.
MAPPED_STEPS = (("a start at row 0", "start"),
                ("a remap between two transformations", "switch"),
                ("a remap from the identity", "from the identity"),
                ("a remap to the identity", "to the identity"))
MAPPED_SHAPES = ("lk", "square", "one")

# The two reads the table of a row's read compares with the key's write,
# each with the marker's branch: the table's address into a0 and an
# indexed read, and an indexed read through the program counter, the
# registers each uses saved and put back. The table is the word before
# the code, and the branch falls through to the next instruction.
LEA_READ = ("t:      dc.w    0\n"
            "        move.w  d0,-(sp)\n        move.l  a0,-(sp)\n        clr.w   d0\n"
            "        move.b  t(pc),d0\n        lea     t(pc),a0\n"
            "        move.b  (a0,d0.w),$FFFF8802.w\n        bmi.s   e\n"
            "        movea.l (sp)+,a0\n        move.w  (sp)+,d0\ne:\n")
PC_READ = ("t:      dc.w    0\n"
           "        move.w  d0,-(sp)\n        clr.w   d0\n        move.b  t(pc),d0\n"
           "        move.b  t(pc,d0.w),$FFFF8802.w\n        bmi.s   e\n"
           "        move.w  (sp)+,d0\ne:\n")

# Identity-tick fixtures for comparison with lookup ticks: multi-column
# and counted shapes, covering loops and stops, plus ordinary fixtures
# and identity paths in mapped tunes.
IDENTITY_TUNES = ENVELOPE_TUNES + (COUNTED_TUNE, "voices.ymxr")

# The tune of mapped_tunes whose costliest call the cost of 34 columns
# names.
FOUR_REMAPS = "four sources through 256 entries remapped in one row"

# The tune of mapped_tunes whose remaps a remap at the frame reads
# against a change of copy: effect 0's, each between two
# transformations, the page found and the timer running.
FADE = "a fade on setR8"


def instructions(module, code, at, n, to=None):
    """The cycles and the bytes of n instructions of code from at, counted
    as the rig counts the player's, a branch among them falling through to
    the next instruction, or branching to `to`."""
    start, total = at, 0
    for _ in range(n):
        words = struct.unpack(">5H", (code[at:at + 10] + bytes(10))[:10])
        after = (to or at + 2) if words[0] >> 12 == 6 else None
        cycles, size = module.cycles_of(words, 0, None, at, after, None)
        total += cycles
        at += 2 * size
    return total, at - start


def span(values, comma=","):
    """A set of cycle counts as performance.md writes it: one figure, or
    the least to the most, a figure of 1,000 or more with its comma in
    prose and without it in a table (comma "")."""
    low, high = format(min(values), comma), format(max(values), comma)
    return low if low == high else "%s to %s" % (low, high)


def mapped_read(code, symbols, cycles_of, general, plays):
    """Verify the lookup sections of performance.md on the selected build:
    tick costs by kind against identity equivalents, frame start/remap
    routines, page selection, copying and switching. Also check each
    tune's worst frame with and without those routines, period and unit,
    plus ordinary-fixture overhead on the lookup core. general contains
    ordinary-fixture tick counts by kind.
    """
    text = open(os.path.join(ROOT, "doc", "performance.md")).read()
    stale = []

    def section(heading, then):
        if heading not in text or then not in text:
            stale.append("performance.md has no section %s before %s" % (heading, then))
            return ""
        return text[text.index(heading):text.index(then)]
    ticks_at = section("## A tick through a transformation", "## A remap at the frame")
    frame_at = section("## A remap at the frame", "## The cost of 34 columns")
    cost_at = section("## The cost of 34 columns", "## A counted tick of two columns")

    def reads(said, claim, *want):
        """One sentence against the figures measured, a figure of 1,000
        or more with its comma."""
        m = re.search(claim, " ".join(said.split()))
        if not m:
            stale.append("performance.md has no \"%s\"" % claim)
            return
        if not near(tuple(g.replace(",", "") for g in m.groups()),
                    tuple(str(w).replace(",", "") for w in want)):
            stale.append("performance.md reads %s where the rig counts %s"
                         % (m.groups(), tuple(str(w) for w in want)))

    played, widths, spans_of = [], set(), {}
    for title, at, kinds in mapped_tunes():
        result = check(at, code, symbols, CyclesOn(cycles_of), spans=spans_of.setdefault(title, []))
        with open(at, "rb") as f:
            bound = bind(f.read(), None)
        played.append((title, result[2], result[3], result[5][8], image_period(bound)))
        widths.add(result[5][9])
    ticked = {}
    for _, _, tick_cost, _, _ in played:
        for kind, spent in tick_cost.items():
            ticked.setdefault(kind, set()).update(spent)
    identity = {kind: set(spent) for kind, spent in general.items()}
    kit = os.path.join(ROOT, "doc", "conformance", "tunes")
    for name in IDENTITY_TUNES:
        for kind, spent in check(os.path.join(kit, name), code, symbols,
                                 CyclesOn(cycles_of))[3].items():
            identity.setdefault(kind, set()).update(spent)
    for kind, spent in ticked.items():
        if not kind.startswith("mapped "):
            identity.setdefault(kind, set()).update(spent)

    # the ticks, a column a build: as it is, lean, and absolute
    column = {(True, False): 1, (True, True): 2, (False, False): 3}.get((PCREL, LEAN))
    read = set()
    for row, kinds in MAPPED_TICKS:
        m = re.search(r"^\| %s \| (\d+) \| (\d+) \| (\d+) \|$" % re.escape(row), ticks_at, re.M)
        spent = set()
        for kind in kinds:
            spent |= ticked.get(kind, set())
            read.add(kind)
        if not m or not spent or column is None:
            stale.append("performance.md's ticks through a transformation read %s for %s, and"
                         " the rig counts %s" % (m and m.groups(), row, sorted(spent)))
        elif not near(int(m.group(column)), spent):
            stale.append("performance.md reads the tick %s through a transformation as %s,"
                         " and the rig counts %s" % (row, m.group(column), sorted(spent)))
    unread = sorted(kind for kind in ticked if kind.startswith("mapped ") and kind not in read
                    and not kind.startswith(("mapped square", "mapped one")))
    if unread:
        stale.append("performance.md has no row for the ticks %s" % unread)
    # each against the identity's tick of its shape
    more = re.search(r"Each figure is the tick of the identity's handler of that shape and (\d+)"
                     r" cycles, and (\d+) on the path that loops",
                     " ".join(ticks_at.split()))
    paired = Counter()
    for kind, spent in sorted(ticked.items()):
        base = kind[len("mapped "):]
        if not kind.startswith("mapped ") or base not in identity \
                or base.startswith(("square", "one")):
            continue
        loops = " loop" in kind
        want = more and int(more.group(2 if loops else 1))
        if len(spent) != 1 or len(identity[base]) != 1 or \
                next(iter(spent)) - next(iter(identity[base])) != want:
            stale.append("the tick %s is %s through a transformation and %s through the identity,"
                         " not %s more" % (base, sorted(spent), sorted(identity[base]), want))
        paired["loop" if loops else "stop" if " stop" in kind else "on"] += 1
    if not more or set(paired) != {"on", "loop", "stop"}:
        stale.append("performance.md's ticks through a transformation are read against the"
                     " identity's on %s" % dict(paired))
    # the 24 in its parts, on the builds that read the row through the
    # program counter and through an absolute address: the key's write,
    # the page read, and the identity's read of the row it replaces
    parts = {}
    for pcrel in (True, False):
        built_ = build([d for d in DEFINES if d != "-dYMXR_PCREL=0"]
                       + ([] if pcrel else ["-dYMXR_PCREL=0"]))
        key = instructions(cycles_of, built_[0], built_[1]["ymxr_lk0_ptr"], 1)
        page = instructions(cycles_of, built_[0], built_[1]["ymxr_lk0_ptr"] + key[1], 1)[0]
        parts[pcrel] = (key[0], page, instructions(cycles_of, built_[0],
                                                   built_[1]["ymxr_tick0_row"] + 6, 1)[0])
    if more and any(key + page - read != int(more.group(1)) for key, page, read in parts.values()):
        stale.append("the key's write, the page read and the identity's read are %s, not %s more"
                     % (parts, more.group(1)))
    key, page, read = parts[False]
    reads(ticks_at, r"on the absolute build the key's write is (\d+) cycles, and the page read"
                    r" through the program counter, (\d+), is (\d+) less than the identity's"
                    r" read of an absolute address, (\d+)\.", key, page, read - page, read)
    reads(ticks_at, r"As it is and lean, the (\d+) is the key's write", parts[True][0])
    if parts[True][1] != parts[True][2]:
        stale.append("the page read and the identity's read through the program counter are %s"
                     % (parts[True],))
    reads(ticks_at, r"Every lookup kind, on the (\w+) builds the rig counts",
          WORDS[len({(True, False), (True, True), (False, False)})])
    for kind in ("square", "square alone", "one", "one alone"):
        if "mapped " + kind in ticked and ticked["mapped " + kind] != identity.get(kind):
            stale.append("a %s through a transformation ticks at %s, and through the identity at"
                         " %s" % (kind, sorted(ticked["mapped " + kind]), identity.get(kind)))
    def one(kind, within=ticked):
        spent = within.get(kind, set())
        return next(iter(spent)) if len(spent) == 1 else None
    reads(ticks_at, r"The counted instance of one column costs (\d+) cycles a row more than the"
                    r" general one", (one("mapped counted on") or 0) - (one("mapped on") or 0))
    rate = re.search(r"A source of ([\d,]+) rows a second through a transformation costs ([\d,]+)"
                     r" cycles a frame more at (\d+) Hz", " ".join(ticks_at.split()))
    if not rate or one("on", identity) is None or one("mapped on") is None:
        stale.append("performance.md has no cost of a source a second through a transformation")
    else:
        rows, hz = int(rate.group(1).replace(",", "")), int(rate.group(3))
        reads(ticks_at, r"A source of ([\d,]+) rows a second through a transformation costs"
                        r" ([\d,]+) cycles a frame more at (\d+) Hz",
              rows, int(rows / float(hz) * (one("mapped on") - one("on", identity))), hz)
    if not PCREL or LEAN or DEFER:
        # the row's read and the frame are the player as it is
        return stale

    # a page: the 256 bytes a key reaches, since the key is a byte
    reads(ticks_at, r"The high byte selects a page of (\d+)\s+bytes", 256)
    # the row's read: the general handler's and its lookup instance's,
    # and the two for comparison, each with the marker's branch
    module = cycles_of
    through = instructions(module, code, symbols["ymxr_tick0_row"] + 6, 2)
    looked = instructions(module, code, symbols["ymxr_lk0_ptr"], 3)
    handler = one("on", identity)
    reads(ticks_at, r"The key's write costs (\d+) cycles a tick", looked[0] - through[0])
    for row, read, whole in (
            ("through the identity", through, handler),
            ("through a transformation", looked, one("mapped on")),
            ("for comparison: `lea` the table", LEA_READ, None),
            ("for comparison: an indexed read through the program counter", PC_READ, None)):
        if whole is None:
            snippet = assemble_text(read)[2:]
            cycles, size = counted(module, snippet, False), len(snippet)
            whole = handler - through[0] + cycles
        else:
            cycles, size = read
        if whole != handler - through[0] + cycles:
            stale.append("the tick %s is %s, and its read and the rest %d" % (
                row, whole, handler - through[0] + cycles))
        m = re.search(r"^\| %s[^|]* \| (\d+) \| (\d+) \| (\d+) \|$" % re.escape(row), ticks_at, re.M)
        if not m or int(m.group(2)) != size or not near(
                (int(m.group(1)), int(m.group(3))), (cycles, whole)):
            stale.append("performance.md reads the row's read %s as %s, and the rig counts %s"
                         % (row, m and m.groups(), (cycles, size, whole)))

    # the frame: each row with one start or one remap of one effect, its
    # routine from the call to the return
    steps, pages, moves, copies, totals, switched = {}, {}, {}, {}, {}, set()
    for _, _, _, lookups, _ in played:
        for events, spent, filled in lookups:
            if len(filled) == 1:
                copies.setdefault(filled[0], set()).add(spent["copy"])
            switches = sum(1 for _, what, shape in events
                           if what == "switch" and shape not in ("square", "one"))
            if switches:
                switched.add(sum(spent["switch%d" % i] for i in range(4)) // switches)
            if len(events) != 1:
                continue
            _, what, shape = events[0]
            # the page's selection in its routine, or the fill of the
            # page not in use, each with the table's address
            page = (spent["page"] + spent["fill"] + spent["table"]
                    if spent["page"] or spent["fill"] else 0)
            steps.setdefault((what, shape), set()).add(spent["routine"] - page - spent["copy"])
            if spent["page"]:
                pages.setdefault("filled" if filled else "found", set()).add(page)
            if spent["fill"]:
                pages.setdefault("fill", set()).add(page)
            if spent["move"]:
                moves.setdefault(shape, set()).add(spent["move"])
            totals.setdefault((what, shape, tuple(filled)), set()).add(spent["routine"])
    for row, what in MAPPED_STEPS:
        m = re.search(r"^\| %s \| ([^|]+?) \| ([^|]+?) \| ([^|]+?) \|$" % re.escape(row),
                      frame_at, re.M)
        counted_ = tuple(span(steps[(what, shape)], "") if (what, shape) in steps else None
                         for shape in MAPPED_SHAPES)
        if not m or not near(m.groups(), counted_):
            stale.append("performance.md reads %s at the frame as %s, and the rig counts %s"
                         % (row, m and m.groups(), counted_))
    def spans(*keys):
        values = set()
        for key in keys:
            values |= steps.get(key, set())
        return span(values) if values else None
    others = sorted({shape for _, shape in steps if shape not in MAPPED_SHAPES + (None,)})
    reads(frame_at, r"A start at the running source's row, with bit 5 of the control column clear,"
                    r" reads that row as well, and on one column its remainder by R of the source"
                    r" \(SPEC.md 1.8.4\): ([\d,]+(?: to [\d,]+)?) cycles on one column, and"
                    r" ([\d,]+(?: to [\d,]+)?) on a square",
          spans(("start, the place kept", "lk")), spans(("start, the place kept", "square")))
    reads(frame_at, r"A remap on a stopped timer sets the kept mapping alone, ([\d,]+(?: to"
                    r" [\d,]+)?) cycles", spans(("stopped", None)))
    reads(frame_at, r"a start at row 0 is ([\d,]+(?: to [\d,]+)?) cycles on them, a remap between"
                    r" two transformations ([\d,]+(?: to [\d,]+)?), from the identity ([\d,]+(?:"
                    r" to [\d,]+)?) and to the identity ([\d,]+(?: to [\d,]+)?)\.",
          *(spans(*[(what, shape) for shape in others])
            for what in ("start", "switch", "from the identity", "to the identity")))
    # every page in the list: no remap fills a page of the fallback
    if pages.get("fill") or pages.get("filled"):
        stale.append("a remap fills a page at the frame: %s" % sorted(pages))
    reads(frame_at, r"A remap from the identity selects its page in a separate routine,"
                    r" ([\d,]+(?: to [\d,]+)?) cycles", span(pages.get("found", {0})))
    # the cache, the last bytes of the record (68k/player/equates.S,
    # FX_LBYTE)
    deferred = build([d for d in DEFINES if d != DEFER_ON] + [DEFER_ON])[1]
    reads(frame_at, r"in the last (\d+) bytes of the effect's record, which is (\d+) bytes, and"
                    r" (\d+) with the deferred start in",
          symbols["FX_SIZE"] - symbols["FX_LBYTE"], symbols["FX_SIZE"], deferred["FX_SIZE"])
    if deferred["FX_SIZE"] - deferred["FX_LBYTE"] != symbols["FX_SIZE"] - symbols["FX_LBYTE"]:
        stale.append("the cache is %d bytes of the record with the deferred start in"
                     % (deferred["FX_SIZE"] - deferred["FX_LBYTE"]))
    reads(frame_at, r"changes one page byte atomically, (\d+) cycles", span(switched or {0}))
    # a start at row 0 of a marked source of one column from the effect's
    # start in the frame code to its rate: through the identity, and
    # through a transformation in a frame that starts it alone, the page
    # found
    ident, looked = set(), set()
    for _, _, _, lookups, _ in played:
        for events, spent, filled in lookups:
            for path, n in spent.get("starts", ()):
                if path == "identity":
                    ident.add(n)
                elif [what for _, what, shape in events] == ["start"] \
                        and events[0][2] == "lk" and not filled:
                    looked.add(n)
    reads(frame_at, r"Through the identity, a start at row 0 of a marked source of one column"
                    r" costs ([\d,]+(?: to [\d,]+)?) cycles from the effect's start in the frame"
                    r" code to its rate, and through a transformation, the page found,"
                    r" ([\d,]+(?: to [\d,]+)?)\.", span(ident or {0}), span(looked or {0}))
    three = moves.get("lb", set()) | moves.get("lv", set())
    every = set().union(*moves.values()) if moves else {0}
    if moves.get("lk") != {min(every)} or three != {max(every)}:
        stale.append("the moves between the identity and a transformation are %s" % moves)
    reads(frame_at, r"between handlers: ([\d,]+) cycles on one column to ([\d,]+) on three",
          span(moves.get("lk", {0})), span(three or {0}))
    # the copy table: a row a fill kind and a count of entries, the copies
    # init makes (INIT_COPIES)
    copies = {key: set(v) for key, v in INIT_COPIES.items()}
    kind, listed = None, set()
    for m in re.finditer(r"^\| ([^|]+?) \| (\d+) \| ([\d,]+) \|$", frame_at, re.M):
        kind = {"entry k at k": 0, "the same": kind}.get(
            m.group(1), 1 if m.group(1).startswith("a marked column") else
            2 if m.group(1).startswith("a counted source on `setR7`") else None)
        key = (kind, int(m.group(2)))
        listed.add(key)
        # the mirror's second write and the ports' first, out of the copy
        # (ymxr_fill): move.l d1,d16(a4) after a move.l to (a4)+, and lea
        # d16(a4),a4
        fill = code[symbols["ymxr_fill"]:symbols["ymxr_runrow"]]
        at = re.search(r"at k \+ (\d+)", m.group(1))
        if m.group(1) != "the same" and kind in (1, 2) and (not at or int(at.group(1)) != (
                struct.unpack(">h", fill[fill.index(b"\x29\x41") + 2:][:2])[0] + 4 if kind == 1
                else struct.unpack(">h", fill[fill.index(b"\x49\xec") + 2:][:2])[0])):
            stale.append("performance.md reads the copy %s at an offset ymxr_fill does not write"
                         % m.group(1))
        if not near(int(m.group(3).replace(",", "")), copies.get(key, set())):
            stale.append("performance.md reads the copy %s of %s entries as %s, and the rig counts"
                         " %s" % (m.group(1), m.group(2), m.group(3), copies.get(key)))
    # init copies every page of a list, so the rig counts more sizes than
    # the table has rows, and each row is a size it counts
    if not listed <= set(copies):
        stale.append("performance.md's copies are %s, and the rig counts %s"
                     % (sorted(listed, key=str), sorted(copies)))
    reads(frame_at, r"So the routines of a volume's remap between two transformations, a marked"
                    r" source of one column, cost ([\d,]+(?: to [\d,]+)?) cycles",
          span(totals.get(("switch", "lk", ()), {0})))
    # a volume's change through copies, on the cores without the lookup:
    # each start of COPY_ROWS from _now and from _unset to _rate, without
    # the deferred start and with it; and through a transformation on this
    # core, each remap of FADE between two transformations, from _unset to
    # _rate
    now_, unset_ = [], []
    for defer in (False, True):
        core_ = build([d for d in DEFINES if d not in (MAP_ON, DEFER_ON)]
                      + ([DEFER_ON] if defer else []))
        spans_ = []
        R = check(copies_tune(), core_[0], core_[1], CyclesOn(cycles_of), spans=spans_)[5][1]
        for label, into in (("now", now_), ("unset", unset_)):
            into.append({n for f, _, at, n in spans_ if f % R in COPY_ROWS and at == label})
    fade = next((lookups for title, _, _, lookups, _ in played if title == FADE), [])
    remapped = {n for f, i, at, n in spans_of.get(FADE, ())
                if at == "unset" and fade[f][0] == [(0, "switch", "lk")] and not fade[f][2]}
    reads(frame_at, r"Without the lookup a source at several volumes is a separate copy at each, and"
                    r" a change of volume is a start of another copy at the running source's row\. On"
                    r" a marked source of one column, the copy as long as the running one, with the"
                    r" timer running and bits 6 and 5 of the control column clear, that start costs"
                    r" ([\d,]+(?: to [\d,]+)?) cycles from the effect's start in the frame code to its"
                    r" rate, and ([\d,]+(?: to [\d,]+)?) with the deferred start in, where the start"
                    r" reads the running handler through the vector\.",
          *(span(values or {0}) for values in now_))
    less = (next(iter(unset_[0])) - next(iter(remapped))
            if len(unset_[0]) == 1 and len(remapped) == 1 else None)
    reads(frame_at, r"On one span, from effect 0's branch on the set bit of its source column to its"
                    r" rate, the copy's start costs ([\d,]+(?: to [\d,]+)?) cycles, and ([\d,]+(?: to"
                    r" [\d,]+)?) with the deferred start in, and a remap between two transformations,"
                    r" its page found and the timer running, ([\d,]+(?: to [\d,]+)?) on the core with"
                    r" the lookup: a change of volume through a transformation costs ([\d,]+) cycles"
                    r" less there than a change of copy\.",
          span(unset_[0] or {0}), span(unset_[1] or {0}), span(remapped or {0}), less)
    reads(frame_at, r"Beyond that span the core with the lookup costs more at the head of each effect,"
                    r" in the table below, and on each tick \(A tick through a transformation\)\.")
    # a page outside the area, copied at the frame (fallback_fills): a row
    # a start or a remap and a count of entries, the only table of five
    # columns in the section
    fills = fallback_fills(code, symbols, cycles_of)
    what, listed = None, set()
    for m in re.finditer(r"^\| ([^|]+?) \| (\d+) \| ([^|]+?) \| ([^|]+?) \| ([^|]+?) \|$",
                         frame_at, re.M):
        what = what if m.group(1) == "the same" else dict(MAPPED_STEPS).get(m.group(1))
        key = (what, "lk", (1, int(m.group(2))))
        listed.add(key)
        got = fills.get(key, set())
        counted_ = tuple(span(values, "") for values in (
            {r for r, _ in got}, {c for _, c in got}, {r - c for r, c in got})) if got else None
        if m.groups()[2:] != counted_:
            stale.append("performance.md reads the fill of %s at the frame as %s, and the rig"
                         " counts %s" % (key, m.groups()[2:], counted_))
    if listed != set(fills):
        stale.append("performance.md's fills at the frame are %s, and the rig counts %s"
                     % (sorted(listed, key=str), sorted(fills, key=str)))
    for key, got in fills.items():
        if {c for _, c in got} != INIT_COPIES.get(key[2]):
            stale.append("the copy of %s at the frame is %s, and init's %s"
                         % (key, sorted(c for _, c in got), INIT_COPIES.get(key[2])))
    reads(frame_at, r"The copy costs what init's copy of as many entries in the same form costs in the"
                    r" table above, and the rest is the routines less the copy\.")
    # a marked source of one column: the mirror form, and the general
    # lookup kind
    reads(frame_at, r"With that copy, the routines of a start and a remap of a (\w+) source of one"
                    r" (\w+) cost, from the remap's first instruction in the frame code to the branch"
                    r" it ends at, or from the start's first instruction to its return:",
          "marked" if {kind for _, _, (kind, _) in fills} == {1} else None,
          "column" if {shape for _, shape, _ in fills} == {"lk"} else None)
    # Measure version 9 frame overhead with and without lookup support.
    # Rows without a start or place reset use the effect-head path;
    # other rows use their V9_PATHS label.
    plain = build([d for d in DEFINES if d != MAP_ON])
    heads, paths, narrow = set(), {}, set()
    for title, at, rows in v9_tunes():
        looked = check(at, code, symbols, CyclesOn(cycles_of))
        without = check(at, plain[0], plain[1], CyclesOn(cycles_of))
        effects, R = bin(looked[5][5] & 0x0F).count("1"), looked[5][1]
        narrow.add(looked[5][9])
        gained = [a - b for a, b in zip(looked[2], without[2])]
        head = {d // effects for f, d in enumerate(gained) if f % R not in rows}
        heads |= head
        for f, d in enumerate(gained):
            if f % R in rows and len(head) == 1:
                paths.setdefault(rows[f % R], set()).add(d - effects * next(iter(head)))
    paths[None] = heads
    for row, path in V9_PATHS:
        m = re.search(r"^\| %s \| (-?\d+) \|$" % re.escape(row), frame_at, re.M)
        if not m or not near(int(m.group(1)), paths.get(path, set())):
            stale.append("performance.md reads the path %s of a tune of version 9 as %s, and the"
                         " rig counts %s" % (row, m and m.group(1), paths.get(path)))
    def path(name):
        return next(iter(paths[name])) if len(paths.get(name, ())) == 1 else None
    fixture = next(((patched, effects) for _, _, patched, effects, _ in plays.values()
                    if effects & 0x0F), None)
    if fixture is None:
        stale.append("no fixture runs an effect on the core with the lookup in")
    else:
        # the head's read of each mapping column, as init patched it, and
        # the branch at the start
        patched, effects = fixture
        if {instructions(module, patched, symbols["q_%d" % (C + i)], 1)[0]
                for i in range(4) if effects & 1 << i} != {path(None)}:
            stale.append("the heads of the mapping columns of a fixture are not %s" % path(None))
        now = symbols["ymxr_tick0_now"]
        aimed = now + 2 + struct.unpack(">h", patched[now + 2:now + 4])[0]
        reads(frame_at, r"a start runs the branch init aims at the start through the identity,"
                        r" (\d+) cycles", instructions(module, patched, now, 1, to=aimed)[0])
        if aimed != symbols["ymxr_tick0_start"] or path("start") != \
                instructions(module, patched, now, 1, to=aimed)[0]:
            stale.append("init aims a start of a tune that maps no value at %x, not at _start"
                         % aimed)
    vector = (path("start kept") or 0) - (path("start") or 0)
    reads(frame_at, r"read the running handler through the vector, (\d+) cycles", vector)
    if path("reset") != vector:
        stale.append("a place's reset is %s more, and a start at the running row %d"
                     % (path("reset"), vector))
    record = (symbols["FX_SIZE"], plain[1]["FX_SIZE"])
    times = (path("wide start") or 0) - (path("start") or 0)
    reads(frame_at, r"multiplies by the size of the effect's record, (\d+) bytes where it is"
                    r" (\d+), (\d+) cycles more", record[0], record[1], times)
    # mulu is 38 cycles and 2 a bit set of its source (the 68000's manual)
    if times != 2 * (bin(record[0]).count("1") - bin(record[1]).count("1")):
        stale.append("a start of several columns is %d more than a start, not its multiply's" % times)
    level = (path("square start") or 0) - (path("start") or 0)
    reads(frame_at, r"A square's start writes row 0's level into the effect's record, (\d+)"
                    r" cycles, and its place's reset then writes it in one move, (\d+) cycles less",
          level, -(path("square reset") or 0))
    if path("square start kept") != level + path("start kept"):
        stale.append("a square's start at the running row is %s, not %d"
                     % (path("square start kept"), level + path("start kept")))
    reads(frame_at, r"on the default build with the rig's (\w+) version 9 fixtures",
          WORDS[len(v9_tunes())])
    more_on = [got[1] - said[1] for said, got, _, _, _ in plays.values()]
    more_at = [got[2] - said[2] for said, got, _, _, _ in plays.values()]
    reads(frame_at, r"Over the (\w+) fixtures, the play call counts ([\d,]+) to ([\d,]+) cycles more"
                    r" on average on this core than the table of The play call, and ([\d,]+) to"
                    r" ([\d,]+) more at most",
          WORDS.get(len(plays), len(plays)), min(more_on or [0]), max(more_on or [0]),
          min(more_at or [0]), max(more_at or [0]))

    # Verify version 10 column count, period, unit and worst frame cost,
    # including and excluding lookup routines.
    if len(widths) != 1 or len(narrow) != 1:
        stale.append("the tunes are of %s columns and of %s" % (sorted(widths), sorted(narrow)))
    else:
        wide_, narrow_ = next(iter(widths)), next(iter(narrow))
        reads(cost_at, r"Version 10 adds (\w+) mapping columns to version 9's (\w+)",
              WORDS[wide_ - narrow_], WORDS[narrow_])
    fixtures = {width for _, _, _, effects, width in plays.values() if not effects & 0x20}
    reads(cost_at, r"The (\w+) dump fixtures use identity and retain (\w+) columns",
          WORDS.get(len(plays), len(plays)) if len(fixtures) == 1 else None,
          WORDS.get(next(iter(fixtures)), fixtures) if len(fixtures) == 1 else None)
    periods = {period for _, _, _, _, period in played}
    if len(periods) != 1:
        stale.append("the tunes through a transformation pack at %s" % sorted(periods))
    else:
        P, k = next(iter(periods))
        reads(cost_at, r"pack at unit (\d+) with a period of ([\w-]+) rows: ([\w-]+) units"
                       r" per refill, against (\w+) for thirty columns",
              k, WORDS.get(P, P), WORDS.get(P // k, P // k), WORDS[30 // k])
    budget = re.search(r"A play call costs at most ([\d,]+) cycles",
                       " ".join(open(os.path.join(ROOT, "doc", "requirements.md")).read().split()))
    budget = int(budget.group(1).replace(",", "")) if budget else 0
    rows = re.findall(r"^\| ([^|]+?) \| (\d+) \| (\d+) \|$", cost_at, re.M)
    if [title for title, _, _ in rows] != [title for title, _, _, _, _ in played]:
        stale.append("performance.md's costliest calls are of %s, and the rig plays %s"
                     % ([title for title, _, _ in rows], [title for title, _, _, _, _ in played]))
    over = []
    for title, cost, _, lookups, _ in played:
        apart = max(c - spent["routine"] for c, (_, spent, _) in zip(cost, lookups))
        said = next(((int(most), int(less)) for named, most, less in rows if named == title), None)
        if not near(said, (max(cost), apart)):
            stale.append("performance.md reads the costliest call of %s as %s, and the rig counts"
                         " %s" % (title, said, (max(cost), apart)))
        if apart > budget:
            stale.append("%s passes R4.5 with the routines of its starts and remaps apart, at %d"
                         % (title, apart))
        if max(cost) > budget:
            over.append(title)
    reads(cost_at, r"Of the (\d+), (\w+) pass(?:es)? R4\.5's ([\d,]+)", len(played),
          WORDS.get(len(over), len(over)), budget)
    costliest = sorted((max(cost), title) for title, cost, _, _, _ in played)
    for title, cost, _, lookups, _ in played:
        if title not in over:
            continue
        f = cost.index(max(cost))
        events, _, filled = lookups[f]
        starts = [shape for _, what, shape in events if what.startswith("start")]
        if f or len(starts) < 2 or filled:
            stale.append("%s: its costliest frame, %d, starts %s and fills %d pages"
                         % (title, f, starts, len(filled)))
    if costliest[-1][1] != FOUR_REMAPS:
        stale.append("the costliest tune through a transformation is %s" % costliest[-1][1])
    four = next((cost[f] for title, cost, _, lookups, _ in played if title == FOUR_REMAPS
                 for f, (events, _, filled) in enumerate(lookups)
                 if len(events) == 4 and all(what == "switch" for _, what, _ in events)), None)
    # its first row's starts and copies, and its row of four remaps'
    frames = next((lookups for title, _, _, lookups, _ in played if title == FOUR_REMAPS), [])
    begun = frames[0] if frames else ([], {}, [])
    remapped = next((frame for frame in frames if len(frame[0]) == 4
                     and all(what == "switch" for _, what, _ in frame[0])), ([], {}, []))
    shapes = {shape for _, what, shape in begun[0] if what == "start"}
    reads(cost_at, r"The costliest starts (\w+) counted sources, and its frame that remaps the"
                   r" (\w+) to another transformation costs ([\d,]+)",
          WORDS.get(len(begun[0])) if shapes == {"lc"} else shapes,
          WORDS.get(len(remapped[0])), four)
    return stale


def decoder_of(file, bound, tune, dtx):
    """Where ST4's decoder is in a bound tune's image, and the two
    parse heads inside it.

    The image is DTX's packager's output, and DTX's rig assembles the same
    template for its labels, so ST4_resume is the decoder's first byte and
    ST4_init the byte after its last: a cycle of the image is inside the
    decoder or outside it by its pc. An operation is an entry to one of the
    two heads, a new offset or a run of literals.
    """
    image, labels = dtx.package(file[long_at(file, 12):])
    at = FILE + tune.image_at
    # The packager reads the tune-file tail, including sources; the
    # binder packages the tune table separately. Compare their
    # reader-image regions only, ending at the transformation block in
    # version 10 or the bound file's end otherwise (BINARIES.md 1.2).
    have = bound[tune.image_at:tune.image_end]
    over = min(len(image), len(have))
    assert image[:over] == have[:over], \
        "the packager's image is not the image the binder wrote"
    return ((at + labels["ST4_resume"], at + labels["ST4_init"]),
            (at + labels["new_offset"], at + labels["begin_literals"]))


def parts_of(every):
    """The refill figures of performance.md over a run's frames, each frame
    (the cycles outside the decoder, the cycles inside it, the operations
    parsed). A turn past the last slot runs no decoder (DTX, abi.md 4): its
    cycles are the idle turn's, and the refill figures read the rest.

    A refill that parses no operation copies inside a match, at one of a
    few costs: `idles` counts each, and `idle` is the least of the ones a
    twentieth of those refills or more read. A refill that ends at
    the ring's end puts the pointer back by N, so `outsides` lists the
    costs outside the decoder a twentieth of the refills or more read."""
    turns = Counter(out for out, dec, _ in every if dec == 0)
    rows = [row for row in every if row[1]] or every
    idles = Counter(dec for _, dec, ops in rows if ops == 0)
    common = sorted(c for c, n in idles.items() if 20 * n >= sum(idles.values()))
    outs = Counter(out for out, _, _ in rows)
    heaviest = max(rows, key=lambda row: row[1])
    # The fit of the decoder's cycles on the operations parsed, over every
    # refill: its slope is an operation's cost to parse. A tune whose
    # refills all parse the same count has no slope to read.
    mean_o = sum(ops for _, _, ops in rows) / len(rows)
    mean_d = sum(dec for _, dec, _ in rows) / len(rows)
    var = sum((ops - mean_o) ** 2 for _, _, ops in rows)
    cov = sum((ops - mean_o) * (dec - mean_d) for _, dec, ops in rows)
    return {"frames": len(rows),
            "turn": turns.most_common(1)[0][0] if turns else None,
            "outside": min(outs),
            "outsides": sorted(c for c, n in outs.items() if 20 * n >= len(rows)),
            "idle": common[0] if common else None, "idles": idles,
            "heaviest": heaviest[1], "operations": heaviest[2],
            "slope": cov / var if var else None}


def refill(ym, code, symbols, dtx, unit):
    """The advance's parts over every frame of one tune converted at
    another unit, which check reads at the unit the converter picks."""
    kept = os.environ.get("YMXR_FLAGS")
    os.environ["YMXR_FLAGS"] = (kept + " " + unit) if kept else unit
    work = tempfile.mkdtemp()
    try:
        file, report = convert(ym, work)
    finally:
        if kept is None:
            del os.environ["YMXR_FLAGS"]
        else:
            os.environ["YMXR_FLAGS"] = kept
    bound = bind(file, work)
    assert bound is not None, "the binder rejected the tune"
    tune = Tune(bound, work)
    cycles = CyclesOn(dtx)
    m = Machine(code, symbols, bound)
    end = FILE + (long_at(bound, 20) if bound[9] else len(bound))
    cycles.attach(m, (FILE + tune.image_at, end),
                  *decoder_of(file, bound, tune, dtx))
    workspace = WORK + 0x100
    assert m.call("init", a0=FILE, a1=workspace) == 0, "init rejected the tune"
    once = tune.RR == tune.R
    frames = tune.R + 1 if once else min(tune.R + tune.R - tune.RR, 4 * tune.R)
    if MOST_FRAMES and frames > MOST_FRAMES:
        frames = MOST_FRAMES
    rows = []
    for f in range(frames):
        cycles._settle(None)
        was = (cycles.image, cycles.decoder, sum(cycles.hits.values()))
        d0 = m.call("play", a0=workspace)
        cycles._settle(None)
        if d0 != 0:
            break                       # the tune has ended (SPEC.md 4.6)
        rows.append((cycles.image - was[0] - (cycles.decoder - was[1]),
                     cycles.decoder - was[1],
                     sum(cycles.hits.values()) - was[2]))
    return parts_of(rows)


def refills(parts, tunes, code, symbols, dtx, whole=True):
    """performance.md read against the refill parts a run measured, one
    tune a key (check).

    The document's figures are of the eleven fixtures, so the claims over
    the set are read back on a run of the set: `whole` says this is one.
    """
    said = " ".join(open(os.path.join(ROOT, "doc", "performance.md")).read().split())
    stale = []

    def reads(claim, *want):
        """One sentence of performance.md against the figures measured."""
        m = re.search(claim, said)
        if not m:
            stale.append("performance.md has no \"%s\"" % claim)
            return
        got = tuple(str(w).replace(",", "").lower() for w in want)
        if not near(tuple(g.replace(",", "").lower() for g in m.groups()), got):
            stale.append("performance.md reads %s where the rig counts %s"
                         % (str(m.groups()), str(got)))

    if whole:
        # A run shorter than a period refills each slot once, after the
        # seed, so its floor is read apart from the others. The period is
        # the tune's (refill_parts): 30 turns at 30 columns, and 34 at 34.
        full = {stem: one for stem, one in parts.items() if one["frames"] >= one["period"]}
        replayed = sorted({one["outside"] for one in full.values() if one["replayed"]})
        fitting = sorted({one["outside"] for one in full.values() if not one["replayed"]})
        reads(r"The advance spends ([\d,]+) cycles a refill outside the"
              r" decoder,.*?; ([\d,]+) where a column fits the ring",
              replayed[0] if len(replayed) == 1 else replayed,
              fitting[0] if len(fitting) == 1 else fitting)
        fits = sorted(stem for stem, one in parts.items() if not one["replayed"])
        reads(r"(\w+) of the eleven tunes fits?:", WORDS.get(len(fits), len(fits)))
        for stem in fits:
            reads(r"%s, whose loop is (\w+)" % re.escape(stem),
                  WORDS.get(parts[stem]["loop"], parts[stem]["loop"]))
        steps = sorted({one["outsides"][1] - one["outsides"][0]
                        for one in full.values() if len(one["outsides"]) > 1})
        reads(r"A refill that ends at the ring's end costs ([\d,]+) more",
              steps[0] if len(steps) == 1 else steps)
        for stem, one in sorted(parts.items()):
            if stem not in full:
                reads(r"%s plays (\w+) frames,.*? and reads ([\d,]+)\." % re.escape(stem),
                      WORDS.get(one["frames"], one["frames"]), one["outside"])
        lives = sorted(one["live"] for one in parts.values())
        reads(r"([\d,]+) to ([\d,]+) of the thirty columns are live", lives[0], lives[-1])
        turns = sorted({one["turn"] for one in parts.values() if one["turn"]})
        reads(r"A turn past the last slot costs the advance ([\d,]+) cycles",
              turns[0] if len(turns) == 1 else turns)
        # the three costs of a refill that parses no operation, over the set
        idles = Counter()
        for one in parts.values():
            idles.update(one["idles"])
        three = sorted(c for c, _ in idles.most_common(3))
        at_one = refill(tunes[0], code, symbols, dtx, "-k1")["idle"]
        reads(r"([\d,]+) cycles inside the decoder where the match's source is"
              r" after the ring's start, ([\d,]+) where it is before that"
              r" start, and ([\d,]+) where it crosses that start.*?The ([\d,]+)"
              r" and the ([\d,]+) above the first.*?A refill at unit 1 is"
              r" ([\d,]+)\.", three[0], three[1], three[2], three[1] - three[0],
              three[2] - three[0], at_one)
        slopes = sorted((one["slope"], stem) for stem, one in parts.items()
                        if one["slope"])
        reads(r"about ([\d,]+) to ([\d,]+) an operation to parse",
              round(slopes[0][0] / 10) * 10, round(slopes[-1][0] / 10) * 10)
        reads(r"over every refill of a tune: ([\d,]+) on (.+?) and ([\d,]+)"
              r" on (.+?)\. The endpoints",
              round(slopes[0][0]), slopes[0][1],
              round(slopes[-1][0]), slopes[-1][1])
        ends = sorted((one["heaviest"] - one["idle"]) / one["operations"]
                      for one in parts.values() if one["operations"] >= 10)
        reads(r"read lower, ([\d,]+) to ([\d,]+):",
              int(ends[0]), int(ends[-1]))
    one = parts.get("Turrican - world 4-3")
    if one:
        reads(r"refill a live column, (\w+) in thirty on Turrican - world 4-3",
              WORDS.get(one["live"], one["live"]))
        reads(r"the ([\d,]+) above and ([\d,]+) at its heaviest are ([\d,]+)"
              r" over (\w+) operations, about ([\d,]+) each",
              one["idle"], one["heaviest"], one["heaviest"] - one["idle"],
              WORDS.get(one["operations"], one["operations"]),
              int((one["heaviest"] - one["idle"]) / one["operations"]))
    return stale


def under_hatari(work, name, vbls, trace_kinds="psg_write,video_vbl"):
    """One program under Hatari for that many VBLs: the chip writes in
    order, each with the VBL it lands in, the cycle of the run it lands
    at and the address that wrote it, and where the SNDH file loaded."""
    trace = os.path.join(work, name + ".trace")
    r = subprocess.run([HATARI, "--tos", TOS, "--machine", "st", "--cpuclock", "8",
                        "--cpu-exact", "on", "--compatible", "on", "--memsize", "4",
                        *HATARI_FLAGS,
                        "--sound", "off", "--conout", "2", "--fast-forward", "on",
                        "--disable-video", "1", "--run-vbls", str(vbls),
                        "--log-level", "fatal", "--trace", trace_kinds,
                        "--trace-file", trace, name], cwd=work, env=HATARI_ENV,
                       stdin=subprocess.DEVNULL, capture_output=True, timeout=900)
    said = re.search(r"YMXR at \$([0-9A-F]{8})", r.stdout.decode(errors="replace"))
    assert said, "%s printed no address: %s" % (
        name, r.stdout.decode(errors="replace")[-300:])
    at = int(said.group(1), 16)
    writes = []
    frame = 0
    clock = 0
    for line in open(trace, errors="replace"):
        v = VBLA.match(line)
        if v:
            frame += 1
            clock = int(v.group(2))
            continue
        w = WRITE.search(line)
        if w and int(w.group(3), 16) < ROM:
            # video_cyc counts the cycles into the frame, and the VBL
            # line the cycle the frame opened at
            cyc = CYCLE.search(line)
            writes.append((frame, int(w.group(1), 16), int(w.group(2), 16),
                           int(w.group(3), 16),
                           clock + (int(cyc.group(1)) if cyc else 0)))
            continue
        w = MFPW.search(line)
        if w and int(w.group(4), 16) < ROM:
            # an effect's step writes its timer before the row's chip
            # writes, so a run traced with mfp_write reads where the
            # frame procedure opened
            writes.append((frame, 0xFF000000 | int(w.group(1), 16),
                           int(w.group(2), 16), int(w.group(4), 16),
                           clock + int(w.group(3))))
    return writes, at


def clocks(ym, code, symbols):
    """The stub's two clocks over one SNDH file (BINARIES.md 4.3, 4.7):
    the program plays from Timer C, which the file's clock tag names, and
    from the VBL where -vbl names it. The rows the frame procedure
    writes are one stream either way, and each run plays one row a frame.
    An effect's handler writes the chip from its timer, which runs at its
    period, so the two clocks interleave those writes among the rows
    differently: they are counted here and left out of the comparison."""
    work = tempfile.mkdtemp()
    file, report = convert(ym, work)
    bound = bind(file, work)
    assert bound is not None, "the binder rejected the tune"
    rate = Tune(bound, work).rate
    assert rate == 50, "-stub requires tunes at 50 Hz, and this one plays at %d" % rate
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-sndh"), "-silent",
                        "-t" + os.path.basename(ym)] + ([] if PCREL else ["-abs"]),
                       input=file, capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    sndh_bytes = r.stdout
    tags = sndh_bytes[12:sndh_bytes.find(b"HDNS")]
    assert b"TC" in tags, "the file names Timer C: " + repr(tags)
    played = {}
    for name, asked in (("TIMERC.PRG", False), ("VBL.PRG", True)):
        r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-prg"), "-silent",
                            "-r%d" % STUB_CLOCKS] + (["-vbl"] if asked else []),
                           input=sndh_bytes, capture_output=True)
        assert r.returncode == 0, r.stderr.decode()
        open(os.path.join(work, name), "wb").write(r.stdout)
        flags = r.stdout[28 + 12] << 8 | r.stdout[28 + 13]
        assert flags == (2 if asked else 0), "%s reads flags %d" % (name, flags)
        writes, at = under_hatari(work, name, STUB_CLOCKS + 400)
        core = sndh_bytes.find(b"YMXS") - 12
        assert core >= 12, "the core is not in the SNDH file"
        assert sndh_bytes[core:core + 28] == code[:28] \
            and sndh_bytes[core + 36:core + len(code)] == code[36:], \
            "the core in the file is not the one assembled, its two patched longs aside"
        base = at + core
        frame = (base + symbols["ymxr_frame"], base + symbols["ymxr_reads"])
        played[name] = [(f, reg, value, frame[0] <= pc < frame[1], cycle)
                        for f, reg, value, pc, cycle in writes]
    timer, vbl = played["TIMERC.PRG"], played["VBL.PRG"]
    rows = [[(reg, value) for _, reg, value, row, _ in w if row] for w in (timer, vbl)]
    assert rows[0] == rows[1], \
        "the two clocks played %d writes of a row and %d, or wrote different values" % (
            len(rows[0]), len(rows[1]))
    assert rows[0], "the frame procedure wrote no register"
    spans = []
    for w in (timer, vbl):
        of = [f for f, _, _, row, _ in w if row]
        spans.append(of[-1] - of[0])
    # A row whose columns are unset leaves every register as it is, so
    # the span runs short of the rows played, and a tune ends its writes
    # where it ends them; the two clocks run those writes over the same
    # frames, one row a frame under either, the phase of the first row
    # and the last aside.
    assert abs(spans[0] - spans[1]) <= 2 and max(spans) <= STUB_CLOCKS + 1, \
        "%d rows took %d frames from Timer C and %d from the VBL" % (
            STUB_CLOCKS, spans[0], spans[1])
    ticked = len(timer) - len(rows[0])
    return len(rows[0]), ticked, spans[0], spans[1], evenness(ym, work, code, symbols)


def evenness(ym, work, code, symbols):
    """The timer the tool arms for a rate no multiple of the operating
    system's 200 Hz clock, read on Hatari's MFP: the same tune at 60 Hz,
    whose fields are the divisor 64 and the count 160, 240 ticks a second
    (BINARIES.md 4.10). The stub's handler clears its in-service bit on
    every tick, so the gaps between those writes are the timer's period;
    a row runs at level 5 while it plays, which delays a tick and the
    next one makes up, so the run's mean reads the arming. The
    figure returned is the mean tick in milliseconds, 4.167 for 240 ticks
    a second against 5.000 for the 200 the clock this replaced arms."""
    file, report = convert(ym, work)
    at = bytearray(file)
    at[6], at[7] = 0, 60                # the frame rate, a word at 6
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-sndh"), "-silent", "-tSixty"]
                       + ([] if PCREL else ["-abs"]), input=bytes(at), capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-prg"), "-silent",
                        "-r%d" % STUB_CLOCKS], input=r.stdout, capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    prg = r.stdout
    ticks = prg[28 + 28] << 8 | prg[28 + 29]
    assert ticks == 240, "the tool armed %d ticks a second for 60 Hz" % ticks
    sndh_at = 28
    while prg[sndh_at + 12:sndh_at + 16] != b"SNDH":
        sndh_at += 2
    open(os.path.join(work, "SIXTY.PRG"), "wb").write(prg)
    writes, loaded = under_hatari(work, "SIXTY.PRG", STUB_CLOCKS + 400,
                                  "psg_write,mfp_write,video_vbl")
    stub = (loaded - (sndh_at - 28), loaded)
    # ISRB, whose bit 5 the stub's handler clears as it enters
    of = [cycle for _, reg, _, pc, cycle in writes
          if reg == 0xFFFFFA11 and stub[0] <= pc < stub[1]]
    assert len(of) > 100, "the stub's handler wrote its in-service bit %d times" % len(of)
    mean = (of[-1] - of[0]) / (len(of) - 1) / CPU_CLOCK * 1000
    assert abs(mean - 1000.0 / ticks) <= 0.02, \
        "the timer ticked every %.3f ms, and %d ticks a second is %.3f" % (
            mean, ticks, 1000.0 / ticks)
    return mean


# The tune the program's clock is read on, and the VBLs a run of it lasts:
# past the operating system's boot, a few thousand ticks.
CLOCK_TUNE = os.path.join(ROOT, "ym", "test", "Circus Attractions  2.ym")
CLOCK_VBLS = 1000
PROFILED = re.compile(r"^([0-9a-f]{8}) (.*?)\s[\d.]+% \((\d+), (\d+), ", re.M)


def clock_program(work, rate):
    """The clock's tune at `rate` in CLOCK.PRG, and where the SNDH file
    is in the program file: the stub is its text, and the SNDH file
    follows the stub."""
    file, report = convert(CLOCK_TUNE, work)
    at = bytearray(file)
    at[6], at[7] = 0, rate              # the frame rate, a word at 6
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-sndh"), "-silent", "-tClock"],
                       input=bytes(at), capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-prg"), "-silent"],
                       input=r.stdout, capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    prg = r.stdout
    sndh_at = 28
    while prg[sndh_at + 12:sndh_at + 16] != b"SNDH":
        sndh_at += 2
    open(os.path.join(work, "CLOCK.PRG"), "wb").write(prg)
    return sndh_at


def clock_run(work, vbls, script=None):
    """What a run of CLOCK.PRG writes to the console: the program's lines,
    and the debugger's where a script of its commands is named. Its
    input is empty, so the debugger reads the script alone."""
    out = os.path.join(work, "out.txt")
    subprocess.run([HATARI, "--tos", TOS, "--machine", "st", "--cpuclock", "8",
                    "--cpu-exact", "on", "--compatible", "on", "--memsize", "4",
                    *HATARI_FLAGS,
                    "--sound", "off", "--conout", "2", "--fast-forward", "on",
                    "--disable-video", "1", "--run-vbls", str(vbls),
                    "--log-level", "fatal"] + (["--parse", script] if script else [])
                   + ["CLOCK.PRG"], cwd=work, env=HATARI_ENV, stdin=subprocess.DEVNULL,
                   stdout=open(out, "wb"), stderr=subprocess.STDOUT, timeout=600)
    return open(out, errors="replace").read()


def clock_profile(rate, symbols):
    """The stub's Timer C handler over one run at `rate`, read by Hatari's
    profiler: the ticks, the rows among them, the cycles of the handler's
    instructions, and those of the routine around the play call. The
    profile lists from an address, so a first run reads where the
    program loads; the debugger is entered once, at a VBL past the boot,
    and lists the profile. A listing ends at a page, which is a few lines
    on some hosts, so a listing starts every 16 bytes across the two
    routines, eight instructions at most, and each routine is read to
    its last instruction: the rts of the routine and the rte of the
    handler."""
    work = tempfile.mkdtemp(prefix="ymxr68")
    sndh_at = clock_program(work, rate)
    said = re.search(r"YMXR at \$([0-9A-F]{8})", clock_run(work, 400))
    assert said, "the program printed no address"
    stub = int(said.group(1), 16) - (sndh_at - 28)
    lists = os.path.join(work, "lists.txt")
    open(lists, "w").write("".join(
        "profile addresses $%x\n" % (stub + at)
        for at in range(symbols["tick"], symbols["clear"], 16)) + "cont\n")
    script = os.path.join(work, "profile.txt")
    open(script, "w").write("profile on\nb VBL > %d :once :quiet :file %s\n"
                            % (CLOCK_VBLS - 20, lists))
    runs, cycles, said = {}, {}, {}
    for m in PROFILED.finditer(clock_run(work, CLOCK_VBLS, script)):
        at = int(m.group(1), 16)
        said[at], runs[at], cycles[at] = m.group(2), int(m.group(3)), int(m.group(4))

    def spent(first, last, ends):
        inside = [at for at in cycles if stub + symbols[first] <= at < stub + symbols[last]]
        assert any(re.search(r"\b%s\b" % ends, said[at]) for at in inside), \
            "the profile of %s at %d Hz stops before its %s" % (first, rate, ends)
        return sum(cycles[at] for at in inside)

    ticks = runs.get(stub + symbols["timer_c"], 0)
    rows = runs.get(stub + symbols["tick"], 0)
    assert ticks > 1000 and rows > 200, \
        "the profile at %d Hz read %d ticks and %d rows" % (rate, ticks, rows)
    return ticks, rows, spent("timer_c", "clear", "rte"), spent("tick", "timer_c", "rts")


def clock():
    """performance.md's figures of the program's clock against Hatari's
    profiler (-clock): the same tune at 50 Hz and at 60 Hz, a row every
    third tick and every fourth. Each run measures the handler's cycles
    a tick on average and the routine around the play call's a row, and
    those read within a cycle of the section's. The two runs together
    solve for a tick without a row, which the section reads as one of two
    figures: the rte runs longer on some returns, by the code it returns
    into, and so by the operating system under the program."""
    code, symbols = assemble("YMXR_prg.S")
    got = {rate: clock_profile(rate, symbols) for rate in (50, 60)}
    (t5, r5, c5, w5), (t6, r6, c6, w6) = got[50], got[60]
    fifty, sixty = c5 / float(t5), c6 / float(t6)
    around = (w5 + w6) / float(r5 + r6)
    across = (t5 - r5) * r6 - (t6 - r6) * r5
    plain = (c5 * r6 - c6 * r5) / float(across)
    runs = "at 50 Hz %d ticks, %d rows, %d and %d cycles; at 60 Hz %d, %d, %d and %d" % (
        t5, r5, c5, w5, t6, r6, c6, w6)
    said = " ".join(open(os.path.join(ROOT, "doc", "performance.md")).read().split())
    m = re.search(r"the handler runs (\d+) cycles a tick on average, its `rte` among them,"
                  r" and at 60 Hz, a row every fourth, (\d+)\. The routine around the play"
                  r" call, .*? runs (\d+) cycles a row", said)
    assert m, "performance.md has no figures of the program's clock"
    for what, reads, counted in (("a tick at 50 Hz", m.group(1), fifty),
                                 ("a tick at 60 Hz", m.group(2), sixty),
                                 ("the routine around the play call", m.group(3), around)):
        assert abs(int(reads) - counted) < 1, \
            "performance.md reads %s cycles for %s, and the profiler counts %.2f (%s)" % (
                reads, what, counted, runs)
    n = re.search(r"a tick without a row at (\d+) or (\d+) cycles of the handler", said)
    assert n, "performance.md has no tick without a row"
    assert int(round(plain)) in (int(n.group(1)), int(n.group(2))), \
        "performance.md reads %s or %s cycles for a tick without a row, and the two runs" \
        " give %.2f (%s)" % (n.group(1), n.group(2), plain, runs)
    return ("the program's clock over %d ticks at 50 Hz and %d at 60 Hz: %.2f cycles a tick"
            " and %.2f, %.2f around the play call, a tick without a row %.2f, as"
            " performance.md reads" % (t5, t6, fifty, sixty, around, plain))


# performance.md's two tunes against YMX, and the tune of the raster
# monitor's paragraph on where a row's writes land.
COST_TUNES = ("Synergy Credits", "Turrican - world 4-3")
FIRST_TUNE = "DBA 2"



def cost_program(file, work, name, flags):
    """A tune file in COST.PRG, as ym/cost.sh builds it: bin/ymxr-sndh with
    the flags named, then bin/ymxr-prg."""
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-sndh"), "-silent", "-t" + name] + flags,
                       input=file, capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    sndh = r.stdout
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-prg"), "-silent"],
                       input=sndh, capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    open(os.path.join(work, "COST.PRG"), "wb").write(r.stdout)
    return sndh


def cost_run(work, vbls, kinds):
    """COST.PRG under Hatari for `vbls` VBLs, as ym/cost.sh runs it but with
    both floppy drives off, which starts the program sooner and fits more
    calls into the run: what the program printed, and the trace of
    `kinds`."""
    r = subprocess.run([HATARI, "--tos", TOS, "--machine", "st", "--cpuclock", "8",
                        "--cpu-exact", "on", "--compatible", "on", "--memsize", "4",
                        *HATARI_FLAGS,
                        "--sound", "off", "--conout", "2", "--fast-forward", "on",
                        "--disable-video", "1", "--run-vbls", str(vbls),
                        "--log-level", "fatal", "--trace", kinds,
                        "--trace-file", "trace.txt", "COST.PRG"],
                       cwd=work, env=HATARI_ENV, stdin=subprocess.DEVNULL, capture_output=True,
                       timeout=900)
    return r.stdout.decode(errors="replace"), os.path.join(work, "trace.txt")


def calls(ym, vbls, first, unit=None):
    """The first `first` play calls of a raster monitor run (ym/cost.py):
    the calls the run made, and those first calls on average, at the 99th
    in a hundred and at most. An operating system that boots sooner starts
    the tune sooner and fits more calls into the run, so the figures are
    of the first calls and not of the run."""
    sys.path.insert(0, os.path.join(ROOT, "ym"))
    import cost as raster
    work = tempfile.mkdtemp(prefix="ymxr68")
    flags = ["-k%d" % unit] if unit else []
    r = subprocess.run([os.path.join(ROOT, "bin", "ym-to-ymxr")] + flags,
                       stdin=open(ym, "rb"), capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    cost_program(r.stdout, work, os.path.basename(ym)[:-3], ["-perf"])
    _, trace = cost_run(work, vbls, "video_color")
    work_cycles, _, _ = raster.spans(raster.read(trace))
    made = len(work_cycles)
    assert made >= first, "the run of %s makes %d calls, not %d" % (
        os.path.basename(ym), made, first)
    work_cycles = sorted(work_cycles[:first])
    return made, int(sum(work_cycles) / first), work_cycles[int(first * 0.99)], work_cycles[-1]


def first_writes(ym, frames):
    """The cycles from the VBL to the frame procedure's first chip write,
    in each of the first `frames` frames whose call writes: the plain core,
    played from the VBL, its writes told from the ticks' by the player's
    labels."""
    code, symbols = assemble("YMXR_sndh.S")
    work = tempfile.mkdtemp(prefix="ymxr68")
    file, _ = convert(ym, work)
    sndh = cost_program(file, work, os.path.basename(ym)[:-3], ["-vbl"])
    said, trace = cost_run(work, frames + 700, "psg_write,video_vbl")
    at = re.search(r"YMXR at \$([0-9A-F]{8})", said)
    assert at, "the program printed no address"
    base = int(at.group(1), 16) + sndh.find(b"YMXS") - 12
    low, high = base + symbols["ymxr_frame"], base + symbols["ymxr_reads"]
    vbl = re.compile(r"^VBL \d+ video_cyc=(\d+)")
    write = re.compile(r"ym write data reg=0x[0-9a-f]+ val=0x[0-9a-f]+ video_cyc=(\d+) .*pc=([0-9a-f]+)")
    out, since, seen = [], None, True
    for line in open(trace, errors="replace"):
        v = vbl.match(line)
        if v:
            since, seen = int(v.group(1)), False
            continue
        w = write.search(line)
        if w and not seen and since is not None and low <= int(w.group(2), 16) < high:
            out.append(int(w.group(1)) - since)
            seen = True
    assert len(out) >= frames, "the run reads %d frames of %s's calls, not %d" % (
        len(out), os.path.basename(ym), frames)
    return out[:frames]


def monitor_runs():
    """performance.md's figures of a raster monitor run against Hatari
    (-cost): the two tunes of the section against YMX, each over the run
    the section names, their first calls, as many as the section counts,
    on average, at the 99th in a hundred and at most against the table's
    YMXR rows; Synergy Credits packed at unit 1 over a run of the same
    length against its figure at most; and on DBA 2, the least cycles from
    the VBL to the frame procedure's first chip write, against the raster
    monitor's paragraph. A run reads the table within the bounds the
    section names, since a tick's entry and rte fall inside a call or
    outside it by where the tick lands."""
    text = open(os.path.join(ROOT, "doc", "performance.md")).read()
    said = " ".join(text.split())
    run = re.search(r"each over a `VBLS=(\d+)` run: ([\d,]+) calls of YMX and ([\d,]+) of YMXR", said)
    assert run, "performance.md names no run of YMXR's calls"
    vbls, counted = int(run.group(1)), int(run.group(3).replace(",", ""))
    first = re.search(r"these are the first ([\d,]+) calls", said)
    assert first and int(first.group(1).replace(",", "")) == counted, \
        "performance.md counts YMXR's calls two ways"
    # How near a run under another operating system, or another build of
    # Hatari, reads the table, measured under TOS 2.06: a single call within
    # a tick's entry and rte, which fall inside a call or outside it by
    # where the tick lands, and an average within the figure the section
    # names.
    near_by = re.search(r"an average there reads within (\d+) cycles of the table and a single"
                        r" call within (\d+)", said)
    assert near_by, "performance.md names no bound on a run of another machine"
    average_within, call_within = int(near_by.group(1)), int(near_by.group(2))
    assert call_within == ENTRY, "performance.md bounds a call by %d, and a tick's entry and" \
        " rte are %d" % (call_within, ENTRY)
    stale, read = [], []
    for tune in COST_TUNES:
        row = re.search(r"^\| %s \| YMXR \| (\d+) \| (\d+) \| (\d+) \|$" % re.escape(tune), text, re.M)
        assert row, "performance.md's table has no YMXR row for " + tune
        made, average, near, most = calls(os.path.join(ROOT, "ym", "test", tune + ".ym"), vbls, counted)
        said_row = tuple(int(x) for x in row.groups())
        if not (abs(average - said_row[0]) <= average_within and abs(near - said_row[1]) <= call_within
                and abs(most - said_row[2]) <= call_within):
            stale.append("performance.md reads %s for %s, and the run measures %s" % (
                "/".join(row.groups()), tune, "/".join(str(x) for x in (average, near, most))))
        read.append("%s %d/%d/%d over the first %d of %d calls" % (tune, average, near, most, counted, made))
    unit = re.search(r"the refill is thirty units, and a run of the same length reads ([\d,]+)"
                     r" at most", said)
    assert unit, "performance.md has no run of Synergy Credits at unit 1"
    _, _, _, most = calls(os.path.join(ROOT, "ym", "test", "Synergy Credits.ym"), vbls, counted, unit=1)
    if abs(most - int(unit.group(1).replace(",", ""))) > call_within:
        stale.append("performance.md reads %s at most for Synergy Credits at unit 1, and the run"
                     " measures %d" % (unit.group(1), most))
    read.append("Synergy Credits at unit 1 %d at most" % most)
    lands = re.search(r"Measured on %s over ([\d,]+) frames, the call's first register write"
                      r" is at least ([\d,]+) cycles after the VBL" % FIRST_TUNE, said)
    assert lands, "performance.md has no first write on " + FIRST_TUNE
    frames = int(lands.group(1).replace(",", ""))
    got = first_writes(os.path.join(ROOT, "ym", "test", FIRST_TUNE + ".ym"), frames)
    # The least moves by the phase the VBL lands at against the instruction
    # it interrupts: a title one byte longer moved it 2 cycles. It reads
    # within one of the shifter's bus slots, 4 cycles.
    if abs(min(got) - int(lands.group(2).replace(",", ""))) > 4:
        stale.append("performance.md reads %s cycles to %s's first write at least, and the run"
                     " measures %d" % (lands.group(2), FIRST_TUNE, min(got)))
    read.append("%s's first write at least %d cycles after the VBL over %d frames, %.0f on average"
                % (FIRST_TUNE, min(got), frames, sum(got) / float(frames)))
    assert not stale, "\n".join(stale)
    return "the raster monitor's runs as performance.md reads them: " + "; ".join(read)


# The real-ST program for the patched displacement read: its source, its
# builder and the README.md whose table -xpatch reads a run against.
XPATCH_DIR = os.path.join(ROOT, "68k", "test", "st")
# The VBLs a run of it plays under Hatari: TOS boots and its desktop starts
# the program, and the program's six cases, at five levels each, and its
# key wait come after that.
XPATCH_VBLS = 8000
XPATCH_CASES = "ZAHEIP"
# A case's line: its letter and level, the counts N, O and X, and the form
# of its store.
XPATCH_LINE = re.compile(r"([ZAHEIP])([0-7]) N=(\d{5}) O=(\d{5}) X=(\d{5}) \S.*")


def xpatch_levels(said):
    """The levels a cell of README.md's table names: 3, 6 and 7, 3 to 7."""
    m = re.fullmatch(r"(\d) to (\d)", said)
    if m:
        return list(range(int(m.group(1)), int(m.group(2)) + 1))
    m = re.fullmatch(r"(\d) and (\d)", said)
    if m:
        return [int(m.group(1)), int(m.group(2))]
    assert re.fullmatch(r"\d", said), "README.md's table names the levels %r" % said
    return [int(said)]


def xpatch_count(said):
    """A count of README.md's table as its least and its most, or None for
    the rest: 0, 50,000, 1 to 99."""
    if said == "the rest":
        return None
    m = re.fullmatch(r"([\d,]+)(?: to ([\d,]+))?", said)
    assert m, "README.md's table counts %r" % said
    least = int(m.group(1).replace(",", ""))
    return least, int((m.group(2) or m.group(1)).replace(",", ""))


def xpatch_table():
    """README.md's iterations of a case, and its counts under Hatari: a
    case and a level to the least and the most of N, O and X, and which of
    the three is the rest, the iterations less the other two."""
    text = open(os.path.join(XPATCH_DIR, "README.md")).read()
    m = re.search(r"of any other byte \(X\) over ([\d,]+) iterations", " ".join(text.split()))
    assert m, "README.md names no iterations of a case"
    iterations = int(m.group(1).replace(",", ""))
    at = text.find("## The counts under Hatari")
    assert at >= 0, "README.md has no counts under Hatari"
    table = {}
    for row in re.finditer(r"^\| ([A-Z]) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|$",
                           text[at:], re.M):
        counts = [xpatch_count(cell.strip()) for cell in row.groups()[2:]]
        rest = [i for i, count in enumerate(counts) if count is None]
        assert len(rest) <= 1, "README.md's %s row has two rests" % row.group(1)
        if rest:
            least = sum(count[0] for count in counts if count is not None)
            most = sum(count[1] for count in counts if count is not None)
            counts[rest[0]] = (iterations - most, iterations - least)
        for level in xpatch_levels(row.group(2).strip()):
            assert (row.group(1), level) not in table, \
                "README.md's table counts %s%d twice" % (row.group(1), level)
            table[row.group(1), level] = (counts, rest[0] if rest else None)
    every = [(case, level) for case in XPATCH_CASES for level in range(3, 8)]
    assert sorted(table) == sorted(every), \
        "README.md's table counts %s" % " ".join("%s%d" % key for key in sorted(table))
    return text, iterations, table


def xpatch():
    """The real-ST program of 68k/test/st under Hatari (-xpatch): built by
    its builder, run twice in one folder cycle-exact on an ST at 8 MHz
    under the TOS image TOS names, and each case's line of the XPATCH.TXT
    it writes read against the table of its README.md. The first run
    creates the file and the second appends its lines to it. The lines of
    a run open with the iterations, the machine and the TOS, and a line of
    each case follows at the level TOS leaves, 3, and at 4 to 7, Z to P;
    the console shows the run's lines, in the same order. README.md quotes
    the first line and a case's line as the program writes them."""
    text, iterations, table = xpatch_table()
    work = tempfile.mkdtemp(prefix="xpatch")
    program = os.path.join(work, "XPATCH.PRG")
    built = subprocess.run([sys.executable, os.path.join(XPATCH_DIR, "build.py"), program],
                           capture_output=True)
    assert built.returncode == 0, built.stdout.decode() + built.stderr.decode()
    before = b""
    for run in (1, 2):
        console = os.path.join(work, "console%d.txt" % run)
        subprocess.run([HATARI, "--tos", TOS, "--machine", "st", "--cpuclock", "8",
                        "--cpu-exact", "on", "--compatible", "on", "--memsize", "4",
                        *HATARI_FLAGS,
                        "--sound", "off", "--conout", "2", "--fast-forward", "on",
                        "--disable-video", "1", "--run-vbls", str(XPATCH_VBLS),
                        "--log-level", "fatal", "XPATCH.PRG"], cwd=work, env=HATARI_ENV,
                       stdin=subprocess.DEVNULL, stdout=open(console, "wb"),
                       stderr=subprocess.STDOUT, timeout=600)
        shown = open(console, "rb").read()
        written = os.path.join(work, "XPATCH.TXT")
        assert os.path.isfile(written), \
            "XPATCH.TXT is absent after XPATCH.PRG ran under %s for %d VBLs; the console ends:\n%s" % (
                TOS, XPATCH_VBLS, shown[-600:].decode("latin-1"))
        whole = open(written, "rb").read()
        assert whole.startswith(before), \
            "run %d of XPATCH.PRG left XPATCH.TXT without the lines of run 1 at its head" % run
        assert len(whole) > len(before), "run %d of XPATCH.PRG added 0 bytes to XPATCH.TXT" % run
        kept, before = whole[len(before):], whole
        assert kept in shown, "the console's lines differ from XPATCH.TXT's under %s:\n%s" % (
            TOS, kept.decode("latin-1"))
        lines = kept.decode("latin-1").split("\r\n")
        assert lines[-1] == "", "XPATCH.TXT ends in a line without CR and LF"
        lines = lines[:-1]
        opens = "XPATCH, %d iterations a case" % iterations
        assert lines[0] == opens, "run %d's lines in XPATCH.TXT open with %r, and README.md" \
            " reads %r" % (run, lines[0], opens)
        assert lines[1] == "_MCH $00000000 ST, _CPU 0", \
            "XPATCH.TXT reads Hatari's ST as %r" % lines[1]
        assert re.fullmatch(r"(EmuTOS, )?TOS \d\.\d\d( in the header)?", lines[2]), \
            "XPATCH.TXT reads the TOS as %r" % lines[2]
        cases = [XPATCH_LINE.fullmatch(line) for line in lines[3:]]
        assert all(cases), "XPATCH.TXT has the lines %r" % lines[3:]
        ran = [(m.group(1), int(m.group(2))) for m in cases]
        every = [(case, level) for case in XPATCH_CASES for level in range(3, 8)]
        assert ran == every, "XPATCH.TXT has the cases %s" % " ".join("%s%d" % key for key in ran)
        assert "`%s`" % lines[0] in text, "README.md quotes another first line than %r" % lines[0]
        forms = {m.group(1): m.group(0).split(" ", 4)[4] for m in cases}
        quoted = [XPATCH_LINE.fullmatch(line.strip()) for line in text.split("\n")
                  if re.fullmatch(r" +[ZAHEIP][0-7] N=.*", line)]
        assert quoted and all(m and m.group(0).split(" ", 4)[4] == forms[m.group(1)]
                              for m in quoted), \
            "README.md quotes a case's line in another form than %r" % forms
        departs, ranged = [], []
        for m in cases:
            key = (m.group(1), int(m.group(2)))
            counts, rest = table[key]
            got = [int(m.group(i)) for i in (3, 4, 5)]
            if sum(got) != iterations or not all(least <= n <= most
                                                 for n, (least, most) in zip(got, counts)):
                departs.append("%s, and README.md's table reads %s" % (m.group(0), ", ".join(
                    "%s=%d" % (what, least) if least == most else
                    "%s=%d to %d" % (what, least, most)
                    for what, (least, most) in zip("NOX", counts))))
            ranged += ["%s%d %s=%d" % (key + ("NOX"[i], got[i])) for i, (least, most)
                       in enumerate(counts) if least != most and i != rest]
        assert not departs, "under %s:\n%s" % (TOS, "\n".join(departs))
    return ("XPATCH.PRG of %d bytes under %s, %s: the %d lines read as README.md's table, %s"
            % (os.path.getsize(program), os.path.basename(TOS), lines[2], len(cases),
               ", ".join(ranged)))


def hatari(ym, code, symbols, perf=False):
    """The tune in an SNDH file in a program under Hatari: the frames the
    trace records, checked against the model, and the ticks counted. code
    and symbols are the SNDH core's, the player's symbols among them; with
    perf the core with the raster monitor in: the switches that chose the
    core here choose the SNDH file's core too."""
    work = tempfile.mkdtemp()
    file, report = convert(ym, work)
    bound = bind(file, work)
    assert bound is not None, "the binder rejected the tune"
    tune = Tune(bound, work)
    # the frames are cut at the VBL, and -vbl below names the VBL as the
    # clock the program plays from (BINARIES.md 4.3): Hatari's ST here
    # refreshes at 50 Hz, the rate the tune plays at
    assert tune.rate == 50, "-hatari requires tunes at 50 Hz, and this one plays at %d" % tune.rate
    if tune.effects & 0x10 and "ymxr_bank1" not in symbols:
        # a row defers a start, so the tool writes the core with the
        # deferred start in (BINARIES.md 2.1), and the rig assembles it too
        code, symbols = assemble("YMXR_sndh.S", defines=[
            d for d in DEFINES if d != "-dYMXR_DEFER=1"] + ["-dYMXR_DEFER=1"])
    sndh = os.path.join(work, "TUNE.SND")
    # These switches select the core in the file; the rig assembled the same
    # core, and the comparison below fails where the two differ. A switch
    # missed here fails there rather than running the plain core. The tool
    # reads a row through the program counter unasked, so a run under
    # -abs passes -abs for the core the rig assembled.
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-sndh"), "-silent", "-vbl",
                        "-t" + os.path.basename(ym)] + (["-perf"] if perf else [])
                       + (["-lean"] if LEAN else [])
                       + ([] if PCREL else ["-abs"]),
                       input=file, capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    sndh_bytes = r.stdout
    open(sndh, "wb").write(sndh_bytes)
    prg = os.path.join(work, "YMXR.PRG")
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-prg"), "-silent",
                        "-r%d" % STUB_FRAMES], input=sndh_bytes, capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    open(prg, "wb").write(r.stdout)
    core = sndh_bytes.find(b"YMXS") - 12
    assert core >= 12, "the core is not in the SNDH file"
    assert sndh_bytes[core:core + 28] == code[:28] \
        and sndh_bytes[core + 36:core + len(code)] == code[36:], \
        "the core in the file is not the one assembled, its two patched longs aside"
    trace = os.path.join(work, "trace.txt")
    r = subprocess.run([HATARI, "--tos", TOS, "--machine", "st", "--cpuclock", "8",
                        "--cpu-exact", "on", "--compatible", "on", "--memsize", "4",
                        *HATARI_FLAGS,
                        "--sound", "off", "--conout", "2", "--fast-forward", "on",
                        "--disable-video", "1", "--run-vbls", str(STUB_FRAMES + 400),
                        "--log-level", "fatal", "--trace", "psg_write,video_vbl,mfp_write",
                        "--trace-file", trace, "YMXR.PRG"],
                       cwd=work, env=HATARI_ENV, stdin=subprocess.DEVNULL,
                       capture_output=True, timeout=900)
    said = re.search(r"YMXR at \$([0-9A-F]{8})", r.stdout.decode(errors="replace"))
    assert said, "the program did not print its address: " + r.stdout.decode(errors="replace")[-300:]
    sndh_at = int(said.group(1), 16)
    base = sndh_at + core
    frame = (base + symbols["ymxr_frame"], base + symbols["ymxr_reads"])
    stop = (base + symbols["YMXR_stop"], base + symbols["YMXR_play"])
    # one macro lays out all four handlers, so the distance between the
    # first two is every handler's length; the monitor's build is longer
    handler = symbols["ymxr_tick1"] - symbols["ymxr_tick0"]
    ticks = [(base + symbols["ymxr_tick%d" % i], base + symbols["ymxr_tick%d" % i] + handler)
             for i in range(4)]
    # a square's handler is beside the four, one an effect, and a
    # one-row source's beside those
    square = symbols["ymxr_sq1"] - symbols["ymxr_sq0"]
    squares = [(base + symbols["ymxr_sq%d" % i], base + symbols["ymxr_sq%d" % i] + square)
               for i in range(4)]
    one = symbols["ymxr_one1"] - symbols["ymxr_one0"]
    ones = [(base + symbols["ymxr_one%d" % i], base + symbols["ymxr_one%d" % i] + one)
            for i in range(4)]
    # and the two handlers of a target that writes several registers, one
    # an effect the same way (68k/player/ticks.S, TICKW)
    two = symbols["ymxr_two1"] - symbols["ymxr_two0"]
    twos = [(base + symbols["ymxr_two%d" % i], base + symbols["ymxr_two%d" % i] + two)
            for i in range(4)]
    three = symbols["ymxr_three1"] - symbols["ymxr_three0"]
    threes = [(base + symbols["ymxr_three%d" % i],
               base + symbols["ymxr_three%d" % i] + three) for i in range(4)]
    # and the two counted shapes, of one column and of several
    # (68k/player/ticks.S, TICKC and TICKW with a counter): a write of a
    # shape this misses is a write of no effect, so its tick is outside the
    # count below and its registers outside the comparison with the model.
    cnt = symbols["ymxr_cnt1"] - symbols["ymxr_cnt0"]
    cnts = [(base + symbols["ymxr_cnt%d" % i], base + symbols["ymxr_cnt%d" % i] + cnt)
            for i in range(4)]
    env = symbols["ymxr_env1"] - symbols["ymxr_env0"]
    envs = [(base + symbols["ymxr_env%d" % i], base + symbols["ymxr_env%d" % i] + env)
            for i in range(4)]
    # With the deferred start in, every handler has a copy in bank 1, BANK
    # bytes on, and each effect an installer after the banks, which the
    # tick that performs a start runs: its writes are that tick's.
    bank = (symbols["ymxr_bank1"] - symbols["ymxr_tick0"]) if "ymxr_bank1" in symbols else None
    installers = []
    if bank is not None:
        ends = ["ymxr_inst1", "ymxr_inst2", "ymxr_inst3", "ymxr_reach"]
        installers = [(base + symbols["ymxr_inst%d" % i], base + symbols[ends[i]])
                      for i in range(4)]

    def where(pc):
        if frame[0] <= pc < frame[1]:
            return "frame"
        if stop[0] <= pc < stop[1]:
            return "stop"
        for i in range(4):
            if installers and installers[i][0] <= pc < installers[i][1]:
                return i
        if bank is not None and base + symbols["ymxr_bank1"] <= pc < installers[0][0]:
            pc -= bank
        for i in range(4):
            if (ticks[i][0] <= pc < ticks[i][1]
                    or squares[i][0] <= pc < squares[i][1]
                    or ones[i][0] <= pc < ones[i][1]
                    or twos[i][0] <= pc < twos[i][1]
                    or threes[i][0] <= pc < threes[i][1]
                    or cnts[i][0] <= pc < cnts[i][1]
                    or envs[i][0] <= pc < envs[i][1]):
                return i
        return None

    # the trace as frames: each a list of (kind, reg, value), its length
    # in MFP clocks, when a handler stopped its timer, and when an
    # installer reprogrammed it, by effect
    frames = []
    clock = None
    current = []
    stops = {}
    installed = {}
    start = 0                           # the video cycle the frame's VBL
                                        # exception came at
    last = 0                            # the video cycle of the latest line
    wrapped = False                     # a video frame began in this frame
    frame_cycles = 0

    def since(video_cyc):
        """A write's MFP clocks from the frame's VBL exception. A tick the
        exception waits for, after the next video frame's first cycle,
        counts on from the video frame before."""
        return (video_cyc - start + (frame_cycles if wrapped else 0)) * MFP_PER_CPU

    for line in open(trace, errors="replace"):
        v = VBLV.match(line)
        if v:
            last = int(v.group(1)) + int(v.group(2))
            wrapped = True
            continue
        v = VIDEO_CYC.search(line)
        if v:
            last = int(v.group(1))
        v = VBLA.match(line)
        if v:
            now = int(v.group(2))
            if clock is not None:
                frame_cycles = now - clock
                frames.append((current, (now - clock) * MFP_PER_CPU, stops, installed))
            clock = now
            start = last
            wrapped = False
            current = []
            stops = {}
            installed = {}
            continue
        w = WRITE.search(line)
        if w:
            pc = int(w.group(3), 16)
            if pc < ROM:
                current.append((where(pc), int(w.group(1), 16), int(w.group(2), 16)))
            continue
        w = MFPW.search(line)
        if w:
            pc = int(w.group(4), 16)
            if pc < ROM:
                # a handler's write, a timer stopping itself on the marker,
                # comes after the frame's ticks; the frame's ticks come before
                reg, value = 0xFF000000 | int(w.group(1), 16), int(w.group(2), 16)
                at = where(pc)
                kind = "mfp_tick" if at in (0, 1, 2, 3) else "mfp"
                if kind == "mfp_tick" and installers \
                        and installers[at][0] <= pc < installers[at][1]:
                    # an installer's write, at the tick that performs a
                    # deferred start: the timer's new rate from that time
                    kind = "mfp_install"
                    installed.setdefault(at, (since(int(w.group(3))), []))[1].append(
                        (reg, value))
                elif kind == "mfp_tick" and reg == TIMER[at]["ctrl"] \
                        and (value >> TIMER[at]["shift"]) & 0x0F == 0:
                    stops[at] = since(int(w.group(3)))
                current.append((kind, reg, value))
    first = next(i for i, (events, _, _, _) in enumerate(frames)
                 if any(kind == "frame" for kind, _, _ in events))
    model = Model(tune)
    timers = Timers(tune.effects, [0, 0, 0, 0], [256] * 4,
                    {0xFFFFFA07: 0, 0xFFFFFA09: 0}, {0xFFFFFA13: 0, 0xFFFFFA15: 0})
    timers.scaled = True
    for events, _, _, _ in frames[:first]:
        timers.apply([(reg, value) for kind, reg, value in events if kind in ("mfp", "mfp_tick")])
    played = 0
    counted = [0, 0, 0, 0]
    expected = [0, 0, 0, 0]
    # The frame procedure runs early in a VBL: the four effect steps in
    # order, then every chip write of the row. A tick falls between any
    # two of those operations (SPEC.md 4.2.1), and reads the effects
    # stepped before it as the row leaves them and the ones after it as
    # they were, so the model is stepped one effect at a time and the
    # trace says how far the procedure has got.
    # Each step programs one timer, so a write to an effect's control
    # or data register places that step, and the frame's first chip write
    # places all four.
    # Two ticks the trace does not place: one on a row that programs no
    # timer for its effect, before the frame's first chip write, and one
    # inside that effect's step, since the step aims the handler at the
    # new source before it writes the timer. Both are read against the
    # source the effect was running and, on a mismatch, against the source
    # the step writes.
    def replay(events, f):
        """The frame's events against the model. The model is left as the
        frame leaves it."""
        model.begin()
        stepped = [False, False, False, False]
        writes = []
        want = None

        def upto(i):
            """Effects 0 to i stepped, in the order the frame runs them."""
            for j in range(i + 1):
                if not stepped[j]:
                    model.step(j)
                    stepped[j] = True

        def owner(reg):
            """The effect whose step wrote a timer register, or None where
            the trace does not say."""
            of = [i for i in OWNS.get(reg, ()) if tune.effects & 1 << i]
            if len(of) == 1:
                return of[0]
            of = [i for i in of if model.sets_select(i)]
            if len(of) == 1:
                return of[0]
            # the register Timers C and D share, written by both effects:
            # the first write is effect 1's, and the writes after it are
            # for either, so they place no source
            return of[0] if of and not stepped[of[0]] else None

        def wrote(at, kind, n):
            """The n writes of one tick of effect `kind`, from event `at`,
            or None where the trace has other events among them. A handler
            writes its registers at the interrupt's level, so no tick of
            another timer falls between two of them."""
            if at + n > len(events):
                return None
            got = []
            for one in events[at:at + n]:
                if one[0] != kind:
                    return None
                got.append((one[1], one[2]))
            return got

        def installs(at, kind):
            """Whether the tick of effect `kind` at event `at` writes its
            timer's data register before the effect's next tick: the
            installer of a deferred start writes the count there, and a
            handler writes the chip and its end of interrupt alone. A tick
            of a timer of a higher priority nests inside the installer, so
            the ticks of other effects are read past."""
            for one in events[at + 1:]:
                if one[0] == kind:
                    return False
                if one[0] == "mfp_install" and one[1] == TIMER[kind]["data"]:
                    return True
            return False

        at = 0
        while at < len(events):
            kind, reg, value = events[at]
            if kind == "mfp":
                i = owner(reg)
                if i is not None:
                    upto(i)
            elif kind == "frame":
                upto(3)
                if want is None:
                    want = model.writes()
                writes.append((reg, value))
            elif kind in (0, 1, 2, 3):
                if not stepped[kind] and not model.fx[kind]["running"]:
                    # no source ran before the step, so the tick is after it
                    upto(kind)
                if not stepped[kind] and installs(at, kind):
                    # the tick runs the installer of a start the step armed
                    # (SPEC.md 4.8), so it is after the step: a frame that
                    # arms a start writes neither the chip nor the timer
                    upto(kind)
                fx = model.fx[kind]
                assert fx["running"], "frame %d: a tick of effect %d with no source running" % (f, kind)
                before = dict(fx)
                w, then = model.tick(kind)
                got = wrote(at, kind, len(w))
                if not stepped[kind] and (got is None or masked(got) != masked(w)):
                    model.fx[kind] = before    # the tick came after the effect step
                    upto(kind)
                    assert model.fx[kind]["running"], \
                        "frame %d: a tick of effect %d with no source running" % (f, kind)
                    w, then = model.tick(kind)
                    got = wrote(at, kind, len(w))
                assert got is not None, \
                    "frame %d: a tick of effect %d writes %d registers and the trace has %s" % (
                        f, kind, len(w), events[at:at + len(w)])
                assert masked(got) == masked(w), \
                    "frame %d: a tick of effect %d wrote %s, not %s; the frame's events %s; the row %s" % (
                        f, kind, got, w, events, model.playing)
                counted[kind] += 1
                at += len(w) - 1
            at += 1
        upto(3)
        if want is None:
            want = model.writes()
        assert masked(writes) == masked(want), "frame %d writes %s, not %s" % (f, writes, want)

    for f, (events, clocks, stops, installed) in enumerate(frames[first:first + STUB_FRAMES]):
        was = list(counted)
        replay(events, f)
        played += 1
        timers.apply([(reg, value) for kind, reg, value in events if kind == "mfp"])
        for i in range(4):
            # the ticks replay read, since a tick of a target of several
            # registers is several writes of the trace
            got = counted[i] - was[i]
            # a timer its handler stopped ticks only until that write, and
            # one an installer reprogrammed at its rate before the write
            # and at the new one after it
            end = stops.get(i, clocks)
            if i in installed:
                at, writes = installed[i]
                want_ticks = timers.expected(i, at)
                timers.apply(writes)
                want_ticks += timers.expected(i, end - at)
            else:
                want_ticks = timers.expected(i, end)
            expected[i] += want_ticks
            if os.environ.get("YMXR_TICKS") and abs(got - want_ticks) > 1:
                print("frame %d: effect %d ticked %d, the rates say %d; mode %s count %s;"
                      " stopped at %s, installed at %s, of %d clocks" % (
                          f, i, got, want_ticks, timers.mode, timers.count, stops.get(i),
                          installed[i][0] if i in installed else None, clocks))
        timers.apply([(reg, value) for kind, reg, value in events if kind == "mfp_tick"])
    # The count fixes the rates, not the phase: a timer stopped or restarted
    # inside a frame is counted for the frame whole, so five in a hundred
    # are allowed, where a wrong prescaler would be off by half or more.
    # Every tick's value is checked against its row above. YMXR_TICKS=1 prints the
    # frames where the two part. The monitor's build costs time, a mark at
    # each end of a handler and the bar burnt in the call, so a real MFP
    # drops ticks of a fast timer: with perf the count stays under the
    # rates, not to them.
    for i in range(4):
        if expected[i] or counted[i]:
            allowed = 1 + expected[i] // 20
            assert counted[i] - expected[i] <= allowed, \
                "effect %d ticked %d times, the rates allow %d" % (i, counted[i], expected[i])
            assert perf or expected[i] - counted[i] <= allowed, \
                "effect %d ticked %d times, the rates say %d" % (i, counted[i], expected[i])
    stopped = [(reg, value) for events, _, _, _ in frames[first + STUB_FRAMES - 1:first + STUB_FRAMES + 3]
               for kind, reg, value in events if kind == "stop"]
    assert masked(stopped) == masked(HUSHED), "the stop wrote %s" % stopped
    return played, sum(counted)


# The vector the 68000 runs where an interrupt acknowledge finds no
# vector on the bus: exception 24, the spurious interrupt.
SPURIOUS = 0x60


# The head of an effect: a bra.w over its columns where a tune does not run
# it, and the read's opcode where it does (68k/player/effect.S, the EFFECT
# macro): move.b d16(a1),d1 where the target column is live, and move.b
# #v,d1 where it is constant (DTX, SPEC.md 2.4). Init writes one or the
# other, so a set where one subtune runs an effect and the next does not
# reads the head back as the branch it began as.
HEAD_BRANCH = 0x6000
HEAD_READ = (0x1229, 0x123C)


def heads(m, symbols):
    """The four effect heads as init left them."""
    return [int.from_bytes(bytes(m.mu.mem_read(
        CODE + symbols["ymxr_tick%d_over" % i], 2)), "big") for i in range(4)]


def want_heads(effects):
    return [HEAD_READ if effects & (1 << i) else (HEAD_BRANCH,) for i in range(4)]


def heads_are(got, effects):
    """Whether each head is the one init writes for these effects."""
    return all(one in want for one, want in zip(got, want_heads(effects)))


# Mapping-column sites per effect (EFFECT in 68k/player/effect.S): the
# head's or, then the start and remap reads. Live columns use a displacement
# from a1; constants use immediates. Versions 7 to 9 patch all three to
# constant 0 (ymxr_col).
MAP_SITES = (("q", (0x8229, 0x0001)), ("c", (0x1229, 0x123C)), ("m", (0x1229, 0x123C)))


def mapping_sites(m, symbols):
    """Each effect's three patched mapping sites, opcode and operand, in
    effect order. Return an empty list without lookup support.
    """
    if "ymxr_look" not in symbols:
        return []
    return [[(m.word(CODE + symbols["%s_%d" % (site, 30 + i)]),
              m.word(CODE + symbols["%s_%d" % (site, 30 + i)] + 2))
             for site, _ in MAP_SITES] for i in range(4)]


def sites_are(got, version):
    """Whether each site matches the table version: a live-column read or
    constant operand in version 10; constant 0 for thirty-column tables.
    """
    for sites in got:
        for (site, ops), (op, operand) in zip(MAP_SITES, sites):
            if op not in ops or (version != MAPPED and (op, operand) != (ops[1], 0)):
                return False
    return True


# The kit's tune that defers a start, which opens a set and an SNDH file
# of a run with -defer (opening).
DEFERRING = os.path.join(ROOT, "doc", "conformance", "tunes", "deferred.ymxr")


def opening(defines):
    """Prepend a fixture requiring each enabled deferred/lookup capability,
    so automatic SNDH core selection matches this run's switches
    (BINARIES.md 2.1).
    """
    out = []
    if DEFER_ON in defines:
        out.append(DEFERRING)
    if MAP_ON in defines:
        out.append(mapped_tunes()[0][1])
    return out


def asked(defines):
    """The bits of the flags word (BINARIES.md 2.3) the run's switches set:
    0 the raster monitor, 1 the lean tick, 2 the row read through the
    program counter."""
    return ((1 if "-dYMXR_PERF=1" in defines else 0)
            | (2 if "-dYMXR_AEOI=1" in defines else 0)
            | (0 if "-dYMXR_PCREL=0" in defines else 4))


def flagged(flags):
    """The defines of the core whose flags word reads `flags` (BINARIES.md
    2.3), as main assembles the player with them."""
    return ((["-dYMXR_PERF=1"] if flags & 1 else [])
            + ([] if flags & 4 else ["-dYMXR_PCREL=0"])
            + (["-dYMXR_NEST=0", "-dYMXR_AEOI=1"] if flags & 2 else [])
            + ([DEFER_ON] if flags & 8 else []) + ([MAP_ON] if flags & 16 else []))


def pieces_of(name):
    """The table of a core's pieces, masks, twins, aligns and fields, as the
    build cut it beside the core in go/binaries/data (doc/LINKER.md, The
    library): (name, first byte, byte past the last) a piece, (offset,
    prefix) a mask, (copy, first) a twin, (gap, base, anchor, add, word)
    an align, word -1 where add is a constant, and (offset, width, {piece:
    c}) a field."""
    pieces, masks, twins, aligns, fields = [], [], [], [], []
    with open(os.path.join(ROOT, "go", "binaries", "data",
                           name.replace(".bin", ".pieces"))) as f:
        for line in f:
            word = line.split()
            if word and word[0] == "piece":
                pieces.append((word[1], int(word[2]), int(word[3])))
            elif word and word[0] == "mask":
                masks.append((int(word[1]), word[2]))
            elif word and word[0] == "twin":
                twins.append((word[1], word[2]))
            elif word and word[0] == "align":
                aligns.append((word[1], int(word[2]), int(word[3]), int(word[4]),
                               int(word[5]) if len(word) == 6 else -1))
            elif word and word[0] == "field":
                moves = {}
                for one in word[3:]:
                    m = re.match(r"([a-z][a-z0-9]*_[0-9]+)([+-][0-9]+)$", one)
                    moves[m.group(1)] = int(m.group(2))
                fields.append((int(word[1]), int(word[2]), moves))
    return pieces, masks, twins, aligns, fields


def link(core, table, blocks_out):
    """The core linked with every piece of the blocks named left out, each
    gap kept at the length its align sets, every field moved by -c times
    the bytes each piece it refers across loses, and each mask written
    with the blocks kept (doc/LINKER.md, The link): the rig's link, which
    the tool's is read against. Returns the bytes and the bytes each
    piece loses."""
    pieces, masks, twins, aligns, fields = table
    lost = {name: to - at for name, at, to in pieces
            if name.rsplit("_", 1)[0] in blocks_out}
    where = {name: (at, to) for name, at, to in pieces}

    def free(references, offset):
        while offset in references.values():
            offset = (offset + 2) & 255
        return offset

    def gaps(lost, references, pick):
        # each gap's length on its effect's reference; where pick, an
        # effect's reference on the first gap the link keeps of it: the one
        # that gap is 0 bytes for, or 2 bytes on where another's is there
        def linked(x):
            return x - sum(lost.get(name, 0) for name, _, to in pieces if to <= x)
        for gap, base, anchor, add, word in aligns:
            if gap in lost:
                continue
            at, to = where[gap]
            if word >= 0:
                if pick and word not in references:
                    references[word] = free(references,
                                            (linked(at) + base - linked(anchor)) & 255)
                add = references[word]
            length = (linked(anchor) + add - linked(at) - base) % 256
            lost[gap] = (to - at) - length
    # the gaps on the references as carried and on the references picked:
    # the link keeps the shorter, and the carried on a tie
    carried = {}
    for one in aligns:
        if one[4] >= 0:
            carried.setdefault(one[4], struct.unpack(">h", core[one[4]:one[4] + 2])[0])
    as_carried, picked, references = dict(lost), dict(lost), {}
    gaps(as_carried, carried, False)
    gaps(picked, references, True)
    for word in carried:
        if word not in references:
            references[word] = free(references, carried[word])
    if sum(picked.values()) <= sum(as_carried.values()):
        lost, references = as_carried, carried
    else:
        lost = picked
    for copy, first in twins:
        assert lost.get(copy, 0) == lost.get(first, 0), \
            "piece %s loses %d bytes and %s, which it copies, %d" % (
                copy, lost.get(copy, 0), first, lost.get(first, 0))
    moved = bytearray(core)
    for at, width, moves in fields:
        by = sum(c * lost.get(name, 0) for name, c in moves.items())
        if by:
            form = ">b" if width == 1 else ">h"
            moved[at:at + width] = struct.pack(form, struct.unpack(form, core[at:at + width])[0] - by)
    kept = set(one[0].rsplit("_", 1)[0] for one in pieces) - set(blocks_out)
    for at, prefix in masks:
        moved[at:at + 2] = struct.pack(">H", sum(1 << i for i in range(16)
                                                 if "%s%d" % (prefix, i) in kept))
    for at, offset in references.items():
        moved[at:at + 2] = struct.pack(">H", offset)
    out, start = bytearray(), 0
    for name, at, to in pieces:
        if lost.get(name, 0):
            out += moved[start:at] + bytes((to - at) - lost[name])
            start = to
    out += moved[start:]
    return bytes(out), lost


def linked_player(ym, defines):
    """The player ymxr-sndh -link links for one tune, cut out of the SNDH
    file it writes, and its symbols as the link moved them; and the line
    the run prints of it. The rig links the assembled core with the
    blocks the tool reports left out and requires the same bytes, its
    patched longs aside, and the player in the core to be the player
    assembled alone (doc/LINKER.md, The check)."""
    work = tempfile.mkdtemp()
    file, _ = convert(ym, work)
    bits = asked(defines)
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-sndh"), "-link"]
                       + (["-perf"] if bits & 1 else []) + (["-lean"] if bits & 2 else [])
                       + (["-pcrel"] if bits & 4 else ["-abs"]),
                       input=file, capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    said = re.search(r"the core: (\S+) linked for the set, (\d+) bytes? of (\d+)"
                     r"(?:, the blocks left out: ([a-z0-9, ]+))?", r.stderr.decode())
    assert said, "ymxr-sndh -link reports no linked core: " + r.stderr.decode()
    name, blocks_out = said.group(1), (said.group(4) or "").split(", ")
    made = r.stdout
    at = struct.unpack(">h", made[2:4])[0] + 2
    body = made[at:]
    flags = struct.unpack(">H", body[22:24])[0]
    assert core_name(flags) == name, "the file's flags read %s, and the report %s" % (
        core_name(flags), name)
    core_code, core_symbols = build(flagged(flags), "YMXR_sndh.S")
    table = pieces_of(name)
    ours, lost = link(core_code, table, blocks_out)
    assert len(ours) == int(said.group(2)), "the rig links %d bytes, and the tool %s" % (
        len(ours), said.group(2))
    # the longs at 28 and 32, and in a core with the lookup the long at
    # 36, are the file's offsets (BINARIES.md 2.2)
    patched = 40 if "map" in name else 36
    assert body[:28] == ours[:28] and body[patched:len(ours)] == ours[patched:], \
        "the core ymxr-sndh linked is not the rig's link of %s, its patched longs aside" % name
    code, symbols = build(flagged(flags))
    first = core_symbols["YMXR_init"] - symbols["YMXR_init"]
    assert core_code[first:] == code, "the player in %s is not the player assembled" % name
    # the pieces of the blocks left out; a gap kept is kept at 0 bytes too
    out = [(name, a, to) for name, a, to in table[0] if name.rsplit("_", 1)[0] in blocks_out]
    moved = Symbols()
    for sym, value in symbols.items():
        if sym not in symbols.text:
            moved[sym] = value
            continue
        # a label inside a piece left out goes with it; one of the code
        # between pieces at a piece's first byte names the byte the link
        # keeps there
        if any(a < first + value < to or (a == first + value and sym not in BETWEEN)
               for _, a, to in out):
            continue
        moved[sym] = value - sum(lost.get(name, 0) for name, _, to in table[0]
                                 if to <= first + value)
    # each effect's reference, which the link picks, out of ymxr_refat
    if "ymxr_refat" in moved:
        for e in range(4):
            at = moved["ymxr_refat"] + 2 * e
            moved["ymxr_ref%d" % e] = moved["ymxr_ref"] + struct.unpack(
                ">H", ours[first + at:first + at + 2])[0]
    moved.text = frozenset(sym for sym in symbols.text if sym in moved)
    return ours[first:], moved, "%s linked, %d bytes of %d, %s left out" % (
        name, len(ours), len(core_code), ", ".join(blocks_out) or "no block")


# The labels at a piece's first byte that name the first byte the link
# keeps there (doc/LINKER.md): the frame's, before effect 0's frame code;
# the first handler's, which the reach and the second bank are measured
# from; the second bank's, before its first handler; and the lookup
# instances', which marks a core with the lookup in.
BETWEEN = ("ymxr_frame", "ymxr_tick0", "ymxr_bank1", "ymxr_look")


def played_on(at, code, symbols):
    """The player a tune file plays on: under -linked, the one ymxr-sndh
    -link links for it (doc/LINKER.md), and the player assembled
    otherwise."""
    if LINKED:
        linked = linked_player(at, DEFINES)
        return linked[0], linked[1]
    return code, symbols


# The checks before the tunes that play their tunes through played_on, so
# a run under -linked plays them on linked players.
LINKABLE = ("deferred", "mapped")


def core_name(flags):
    """The name of the core whose flags word reads `flags` (BINARIES.md
    2.1)."""
    return "YMXR_sndh%s%s%s%s%s.bin" % (
        "-perf" if flags & 1 else "", "-lean" if flags & 2 else "",
        "" if flags & 4 else "-abs", "-defer" if flags & 8 else "",
        "-map" if flags & 16 else "")


# The end of the line of BINARIES.md 2.10 for tunes past the reach of a
# displacement.
PAST_THE_REACH = b"a tick that reads a row through the program counter reaches 32767"


def sndh_of(files, defines, work):
    """Build an SNDH from tune files in subtune order through ymxr-multi
    and ymxr-sndh. Explicit flags select monitoring, lean ticks and
    addressing; subtune effects bytes select deferred and lookup support
    (BINARIES.md 2.1, 3). Return None when sources exceed displacement
    reach.
    """
    if len(files) > 1:
        paths = []
        for n, file in enumerate(files):
            paths.append(os.path.join(work, "%d.ymxr" % (n + 1)))
            with open(paths[-1], "wb") as f:
                f.write(file)
        r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-multi"), "-silent"] + paths,
                           capture_output=True)
        assert r.returncode == 0, r.stderr.decode()
        file = r.stdout
    else:
        file = files[0]
    bits = asked(defines)
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-sndh"), "-silent"]
                       + (["-perf"] if bits & 1 else []) + (["-lean"] if bits & 2 else [])
                       + (["-pcrel"] if bits & 4 else ["-abs"]),
                       input=file, capture_output=True)
    if r.returncode == 1 and PAST_THE_REACH in r.stderr:
        return None
    assert r.returncode == 0, r.stderr.decode()
    return r.stdout


def from_core(made, defines):
    """Load an SNDH with its core at CODE. Return bytes from that origin,
    matching assembled core and symbols, flags, subtune-table end and
    bound-tune spans. Compare assembly byte for byte except patched
    fields 28 and 32. Feature flags follow subtune effects bits 4 and 5;
    the table follows the even-aligned core end (BINARIES.md 2.1, 3.1).
    """
    at = struct.unpack(">h", made[2:4])[0] + 2
    body = made[at:]
    assert len(body) <= WORK - CODE, \
        "the SNDH file is %d bytes from its core, and the code's memory %d" % (
            len(body), WORK - CODE)
    flags = struct.unpack(">H", body[22:24])[0]
    table = long_at(body, 28)
    count = struct.unpack(">H", body[table:table + 2])[0]
    starts = [long_at(body, table + 2 + 4 * i) for i in range(count)]
    images = min(one + struct.unpack(">i", body[one + 16:one + 20])[0] for one in starts)
    effects = 0
    for one in starts:
        effects |= body[one + 8]
    want = asked(defines) | (effects & 0x30) >> 1
    assert flags == want, \
        "ymxr-sndh wrote %s, flags %d, and the run and the subtunes select %s" % (
            core_name(flags), flags, core_name(want))
    code, symbols = build(flagged(flags), "YMXR_sndh.S")
    # with the lookup in, the page area's offset at 36 is patched too, and
    # the area is between the core and the table (BINARIES.md 2.2, 3.1)
    patched = 40 if flags & 16 else 36
    assert body[:28] == code[:28] and body[patched:len(code)] == code[patched:], \
        "%s in the SNDH file is not the one assembled, its patched longs aside" % (
            core_name(flags))
    pages = long_at(body, 36) if flags & 16 else 0
    # the area's reach from the core's first handler, its field at 26
    reach = 32767 - (((len(code) + 1) & ~1) - struct.unpack(">H", body[26:28])[0])
    most = max([area_of(body[one:], symbols, reach=reach) or 0 for one in starts
                if body[one + 8] & 0x20] + [0]) if pages else 0
    assert pages in (0, (len(code) + 1) & ~1), \
        "the page area is at %d, and the core is %d bytes" % (pages, len(code))
    assert table == (((len(code) + 1) & ~1) + most + 1) & ~1, \
        "the subtune table is at %d, and the core is %d bytes and its page area %d" % (
            table, len(code), most)
    return (body, code, symbols, flags, table + 2 + 4 * count,
            list(zip(starts, starts[1:] + [images])))


def that_map(count):
    """The clause of a set's line that counts its subtunes that map a
    value."""
    return ", %d that map%s a value" % (count, "s" if count == 1 else "")


def patched_code_follows_the_subtune(defines, tunes):
    """Verify init output depends only on the selected subtune. Compare a
    fresh initialization with initialization after each other subtune in
    the same loaded SNDH. Check player code and the selected bound tune.
    Exclude shared images, workspace and other tunes: DTX init can
    modify an image shared by tables of the same shape.

    opening ensures the set requires this run's deferred and lookup
    capabilities. Omit tunes with a different frame rate from the first
    because an SNDH records one rate (BINARIES.md 1.4, 2.1, 3.3).
    """
    work = tempfile.mkdtemp()
    first = opening(defines)
    files = []
    rate, others = None, 0
    # The set is the tunes at the head of the list, and a few are enough:
    # the pairs below are the square of the set, and a run over the corpus
    # would otherwise convert every dump of it for a set of four.
    for ym in (first + [one for one in tunes if one not in first])[:SET_READS]:
        try:
            file, _ = convert(ym, work)
        except AnotherFormat:
            continue
        bound = bind(file, work)
        if bound is None:
            continue
        rate = rate or file[6:8]
        if file[6:8] != rate:
            others += 1
            continue
        files.append((os.path.basename(ym), file))
        if len(files) == SET_TUNES:
            break
    made = sndh_of([file for _, file in files], defines, work)
    # A tick of the core that reads a row through the program counter
    # reaches 32,767 bytes, which the tool measures from the core's first
    # byte plus its field at 26, and the tool reports a set whose bound
    # tunes end further off (BINARIES.md 2.1, 2.10): each subtune then
    # joins the set where the set with it is within the reach.
    left = 0
    if made is None:
        fits = []
        for one in files:
            tried = sndh_of([file for _, file in fits + [one]], defines, work)
            if tried is not None:
                fits.append(one)
                made = tried
        left = len(files) - len(fits)
        files = fits
    if len(files) < 2:
        return "one subtune: no pair to init one after the other"
    body, code, symbols, flags, shared, spans = from_core(made, defines)

    def image(m):
        return bytes(m.mu.mem_read(CODE, len(body)))

    alone = []
    for i in range(len(files)):
        m = Machine(body, symbols, b"")
        m.call("core-init", d0=i + 1)
        alone.append(image(m))
    pairs = 0
    for a in range(len(files)):
        for b in range(len(files)):
            if a == b:
                continue
            m = Machine(body, symbols, b"")
            m.call("core-init", d0=a + 1)
            m.call("core-init", d0=b + 1)
            got = image(m)
            # the page area between the core and the subtune table, where
            # a subtune's pages past the next one's are left as they were,
            # which no tick of the next one reads (BINARIES.md 3.1)
            area = long_at(body, 36) if flags & 16 else 0
            table = long_at(body, 28)
            for name, lo, hi in (("the code", 0, area or table), ("the code", table, shared),
                                 ("the tune", spans[b][0], spans[b][1])):
                if got[lo:hi] != alone[b][lo:hi]:
                    where = [i for i in range(lo, hi) if got[i] != alone[b][i]]
                    near = {}
                    for sym, at in symbols.items():
                        if not sym.startswith(".") and at <= where[0]:
                            near[at] = sym
                    label = near[max(near)] if near and lo == 0 else "?"
                    raise AssertionError(
                        "%s after %s then %s differs from %s alone at %d byte(s), "
                        "the first at %s+%d ($%X): %02X where %02X" % (
                            name, files[a][0], files[b][0], files[b][0], len(where),
                            label, where[0] - (max(near) if near and lo == 0 else lo),
                            CODE + where[0], got[where[0]], alone[b][where[0]]))
            pairs += 1
    return "%d subtunes in one SNDH file of ymxr-sndh on %s%s: the code after any one" \
        " then another is the code after the other alone, %d ordered pairs%s%s" % (
            len(files), core_name(flags),
            that_map(sum(1 for _, file in files if file[8] & 0x20)) if flags & 16 else "",
            pairs,
            "" if left == 0 else "; %d past the displacement's reach left out" % left,
            "" if others == 0 else "; %d at a rate other than the first's left out" % others)


def core(defines, ym):
    """Check the SNDH core's vector $60: init saves the host vector and
    installs rte; exit restores it. Clearing an MFP pending/enable bit
    before acknowledgement can cause this spurious exception instead of
    a timer interrupt (BINARIES.md 2).

    Use the last subtune after opening's capability fixtures so core
    selection includes required deferred/lookup support. Verify patched
    mapping sites and key-write/page-read order (pairs). Return core
    name, size and instance count.
    """
    work = tempfile.mkdtemp()
    files = [convert(one, work)[0] for one in opening(defines) if one != ym]
    files.append(convert(ym, work)[0])
    made = sndh_of(files, defines, work)
    assert made is not None, \
        "ymxr-sndh reports %s past the reach of a displacement" % os.path.basename(ym)
    body, code, symbols, flags, _, spans = from_core(made, defines)
    bound = body[spans[-1][0]:spans[-1][1]]
    m = Machine(body, symbols, b"")
    host = 0x00ABCDE0
    m.mu.mem_write(SPURIOUS, struct.pack(">I", host))
    # The core keeps every register, so its entries report through the
    # state byte rather than through d0: bit 0 is while a tune plays.
    m.call("core-init", d0=len(files))
    assert m.byte(CODE + symbols["sndh_state"]) & 1, \
        "the core's init rejected " + os.path.basename(ym)
    at = m.long(SPURIOUS)
    assert at == CODE + symbols["sndh_spurious"], \
        "init left $60 at %08x, not the core's handler at %08x" % (
            at, CODE + symbols["sndh_spurious"])
    assert bytes(m.mu.mem_read(at, 2)) == b"\x4e\x73", "the handler at $60 is not an rte"
    assert heads_are(heads(m, symbols), bound[8]), \
        "init left the effect heads at %s, not %s for effects %s" % (
            ["%04X" % h for h in heads(m, symbols)],
            ["/".join("%04X" % o for o in h) for h in want_heads(bound[8])],
            format(bound[8], "04b"))
    version = struct.unpack(">H", bound[4:6])[0]
    assert sites_are(mapping_sites(m, symbols), version), \
        "init left the mapping sites at %s for a tune of version %d" % (
            [["%04X %04X" % one for one in sites] for sites in mapping_sites(m, symbols)],
            version)
    instances = pairs(m, symbols) if "ymxr_look" in symbols else 0
    m.call("core-exit")
    assert not m.byte(CODE + symbols["sndh_state"]) & 1, "the core's exit left the tune playing"
    assert masked(m.psg) == masked(HUSHED), \
        "exit wrote %s, not the chip state a tune starts from" % masked(m.psg)
    assert m.long(SPURIOUS) == host, "exit left $60 at %08x, not the host's %08x" % (
        m.long(SPURIOUS), host)
    return core_name(flags), len(code), instances


def main():
    if "-clock" in sys.argv:
        print(clock())
        return
    if "-cost" in sys.argv:
        print(monitor_runs())
        return
    if "-xpatch" in sys.argv:
        print(xpatch())
        return
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    count = "-cycles" in sys.argv
    parts = "-refill" in sys.argv
    real = "-hatari" in sys.argv
    stub = "-stub" in sys.argv
    kit = "-kit" in sys.argv
    global PCREL, FILE
    PCREL = "-abs" not in sys.argv
    perf = PERF
    wide = next((a for a in sys.argv[1:] if a.startswith("-corpus")), None)
    picked = next((a for a in sys.argv[1:] if a.startswith("-only")), None)
    only = None
    if picked is not None:
        only = picked[len("-only"):].split(",")
        unknown = [name for name in only if name not in CHECKS]
        if unknown:
            raise SystemExit("-only reads the names %s and not %s"
                             % (", ".join(CHECKS), ", ".join(unknown)))
    capped = next((a for a in sys.argv[1:] if a.startswith("-frames")), None)
    if capped is not None:
        said = capped[len("-frames"):]
        if not said.isdigit() or int(said) < 1:
            raise SystemExit("-frames reads a count of 1 or more and not " + said)
        global MOST_FRAMES
        MOST_FRAMES = int(said)
    if kit:
        where = os.path.join(ROOT, "doc", "conformance", "tunes")
        tunes = args or sorted(os.path.join(where, f) for f in os.listdir(where)
                               if f.endswith(".ymxr"))
        # The kit's wrong-version tune has no record to play against: the
        # binder and the reader both reject it, as the kit reads
        # of it (doc/conformance/README.md). So it is checked to be rejected
        # and left out of the tunes played.
        left = [one for one in tunes
                if os.path.basename(one) == "wrong-version.ymxr"]
        tunes = [one for one in tunes if one not in left]
        for one in left:
            with open(one, "rb") as f:
                file = f.read()
            said = subprocess.run(
                [os.path.join(ROOT, "bin", "ymxr-trace"), "-silent"],
                input=file, capture_output=True)
            assert said.returncode != 0 and not said.stdout, \
                "%s: the reader read a tune of another version" % os.path.basename(one)
            assert bind(file, tempfile.mkdtemp(prefix="ymxr68")) is None, \
                "%s: the binder bound a tune of another version" % os.path.basename(one)
            print("%-45s the binder and the reader reject it"
                  % os.path.basename(one))
    elif wide is not None:
        said = wide[len("-corpus"):]
        if said and not said.isdigit():
            raise SystemExit("-corpus reads a count and not " + said)
        tunes = args or spread(int(said or CORPUS_TUNES))
    else:
        tunes = args or sorted(os.path.join(ROOT, "ym", "test", f)
                               for f in os.listdir(os.path.join(ROOT, "ym", "test"))
                               if f.endswith(".ym"))
        # No dump converts to a target of several registers, to a counted
        # source or to a deferred start, so the kit's tunes of versions 4
        # to 6 and its deferred tune go on the end of a run on a real MFP:
        # with the dumps before them every handler shape of the player is
        # read there against the same model as the rest, the counted
        # shapes of one column and of several among them, and the switch
        # of the core with the deferred start in.
        if real and not args:
            tunes = tunes + [os.path.join(ROOT, "doc", "conformance", "tunes", one)
                             for one in ("voices.ymxr", "counted.ymxr",
                                         "envelope-counted.ymxr", "deferred.ymxr")]
    defines = ["-dYMXR_PERF=1"] if perf else []
    if not PCREL:
        defines += ["-dYMXR_PCREL=0"]
    if LEAN:
        defines += ["-dYMXR_NEST=0", "-dYMXR_AEOI=1"]
    if DEFER:
        defines += [DEFER_ON]
    if MAP:
        defines += [MAP_ON]
    DEFINES[:] = defines
    code, symbols = build(defines)
    FILE = placed(len(code))
    global TICK_SEL, TICK_PTR, SQ_SEL, SQ_VAL, SQ_MASK, ONE_SEL, ONE_VAL, TW_SEL1, TW_PTR1
    global TICKC_SEL, TICKC_PTR, TICKC_LEFT
    global TWC_SEL1, TWC_PTR1, TWC_LEFT
    TICK_SEL = equate("TICK_SEL", symbols)
    TICK_PTR = equate("TICK_PTR", symbols)
    SQ_SEL = equate("SQ_SEL", symbols)
    SQ_VAL = equate("SQ_VAL", symbols)
    SQ_MASK = equate("SQ_MASK", symbols)
    ONE_SEL = equate("ONE_SEL", symbols)
    ONE_VAL = equate("ONE_VAL", symbols)
    TW_SEL1 = (equate("TW2_SEL1", symbols), equate("TW3_SEL1", symbols))
    TW_PTR1 = (equate("TW2_PTR1", symbols), equate("TW3_PTR1", symbols))
    TICKC_SEL = equate("TICKC_SEL", symbols)
    TICKC_PTR = equate("TICKC_PTR", symbols)
    TWC_SEL1 = equate("TWC_SEL1", symbols)
    TWC_PTR1 = equate("TWC_PTR1", symbols)
    TWC_LEFT = equate("TWC_LEFT", symbols)
    TICKC_LEFT = equate("TICKC_LEFT", symbols)
    print("the player: %d bytes%s%s%s%s, the tune at $%X" % (
        len(code), ", the raster monitor in" if perf else "",
        ", the lean tick" if LEAN else "",
        ", the deferred start in" if DEFER else "",
        ", the lookup in" if MAP else "", FILE))
    # The checks below read one tune of the list, and a dump of another
    # format is outside a run (convert): the first the converter reads
    # represents them, and the run reports the rest as it reaches them.
    opens = tempfile.mkdtemp()
    first = None
    for one in tunes:
        try:
            convert(one, opens)
        except AnotherFormat:
            continue
        first = one
        break
    if first is not None:
        def wanted(name):
            return (only is None or name in only) and (not LINKED or name in LINKABLE)
        lookup = mapped_tunes() if wanted("cores") or wanted("sets") else None
        if wanted("cores"):
            print("the SNDH core of ymxr-sndh, %s: %d bytes, $60 kept and put back"
                  % core(defines, first)[:2])
            print("the SNDH core with the lookup in, %s: %d bytes, $60 kept and put back, the"
                  " mapping sites as the table has them, %d lookup instances each with its"
                  " key's write before its page read" % core(defines, lookup[0][1]))
        if wanted("sets"):
            print("    %s" % patched_code_follows_the_subtune(defines, tunes))
            # the fixtures after a mapped tune of each lookup kind, on the
            # core with the lookup in
            print("    %s" % patched_code_follows_the_subtune(
                defines, [at for _, at, kinds in lookup][:SET_MAPPED] + tunes))
        for name, run in (("unplaced", lambda: unplaced(code, symbols)),
                          ("deferred", lambda: deferred(*deferring(defines))),
                          ("mapped", lambda: mapped(code, symbols)),
                          ("fallbacks", lambda: fallbacks(code, symbols)),
                          ("deferred_sizes", deferred_sizes),
                          ("mapped_sizes", mapped_sizes),
                          ("absolute_sizes", absolute_sizes),
                          ("halves", lambda: halves(code, symbols)),
                          ("voices", lambda: voices(code, symbols)),
                          ("widekept", lambda: widekept(code, symbols)),
                          ("envelope", lambda: envelope(code, symbols)),
                          ("wholebyte", lambda: wholebyte(code, symbols)),
                          ("outofreach", lambda: outofreach(code, symbols))):
            if wanted(name) and (PCREL or name != "outofreach"):
                print("    %s" % run())
    if only is not None:
        print("%d %s whole: %s" % (len(only), "check runs" if len(only) == 1
                                   else "checks run", ", ".join(only)))
        return
    if real or stub:
        code, symbols = build(defines, "YMXR_sndh.S")
    cycles_of = None
    if count or parts:
        sys.path.insert(0, os.path.join(DTX_REPO, "68k", "test", "emu"))
        import test_dtx
        with_movep(test_dtx)
        cycles_of = test_dtx
    costs = dense(cycles_of, code, symbols) if cycles_of and not perf else None
    savings = {}                        # dense columns' saving by tune, and its effects
    footprints = {}                     # the bound tune's bytes and its state block's, by tune
    general = {}                        # every tick's cost the fixtures counted, by kind
    walker = None                       # the register steps walked, once a count runs
    stale = []
    wrong = []
    measured = {}                       # the refill parts by tune, with -refill
    plays = {}                          # the table's play call and the lookup core's, by tune
    another = []
    for ym in tunes:
        try:
            if stub:
                writes, ticked, timed, framed, worst = clocks(ym, code, symbols)
                line = ("%-45s %6d writes of a row, one stream from both clocks, over %d"
                        " frames from Timer C and %d from the VBL"
                        % (os.path.basename(ym), writes, timed, framed))
                if ticked:
                    line += ", %d writes of a tick beside them" % ticked
                line += ", the same tune at 60 Hz ticking every %.3f ms" % worst
                print(line)
                continue
            if real:
                frames, ticks = hatari(ym, code, symbols, perf)
                print("%-45s %6d frames, %6d ticks on Hatari's MFP" % (os.path.basename(ym), frames, ticks))
                continue
            playing = (code, symbols)
            if LINKED:
                # a tune that needs the deferred start or the lookup plays on
                # the core with it, linked
                playing = linked_player(ym, defines)
                print("%-45s %s" % (os.path.basename(ym), playing[2]))
            frames, ticks, cost, tick_cost, tick_cycles, where, boundaries, refills_of = \
                check(ym, playing[0], playing[1], cycles_of and CyclesOn(cycles_of),
                      kit, perf, parts)
            for kind, spent in tick_cost.items():
                general.setdefault(kind, set()).update(spent)
            line = "%-45s %6d frames, %6d ticks" % (os.path.basename(ym), frames, ticks)
            if boundaries:
                line += ", a tick at %d boundaries of the frame" % len(boundaries)
            if refills_of:
                measured[os.path.basename(ym)[:-3].replace("  ", " ")] = refills_of
            if kit:
                line += ", the player's frames are the reader's entries"
            if cost and perf:
                line += ", no cost figures: the monitor's cycles run inside the call"
            if cost and not perf:
                average = int(sum(cost) / len(cost))
                adv = where[3]
                line += ", play %5d cycles on average, %5d at most, at frame %d of R %d RR %d" % (
                    average, max(cost), where[0], where[1], where[2])
                line += "; the advance %5d on average, %5d at most, %5d in the costliest frame" % (
                    sum(adv) / len(adv), max(adv), adv[where[0]])
                # A tick costs the frame its instructions and the
                # 68000's entry and rte besides, which no emulated cycle
                # here counts: 44 and 20 from the manual, the 64 that
                # separates performance.md's 96 from its 160.
                ticked = int(round((tick_cycles + ENTRY * ticks) / float(frames)))
                if costs and where[4] and PCREL and not LEAN:
                    # the walk of the frame procedure, frame by frame, is the rig's count
                    walker = walker or FrameWalk(cycles_of, code, symbols)
                    tune_walk = FrameWalk(cycles_of, where[6], symbols)
                    for f, (_, counted, _, row) in enumerate(where[4]):
                        if tune_walk.registers(row) != counted:
                            stale.append("%s frame %d: the walk of the register steps reads %d,"
                                         " and the rig counts %d" % (os.path.basename(ym), f,
                                                                     tune_walk.registers(row), counted))
                            break
                if costs and where[4]:
                    savings[os.path.basename(ym)[:-3].replace("  ", " ")] = (
                        saving(where[4], where[5], costs), bin(where[5]).count("1"))
                    line += "; dense columns %+4.0f a frame" % savings[
                        os.path.basename(ym)[:-3].replace("  ", " ")][0]
                if ticks:
                    line += "; the ticks %5d cycles a frame, %5d with the call" % (
                        ticked, average + ticked)
                for then in sorted(tick_cost):
                    line += ", a tick %s %s" % (then, "/".join(str(c) for c in sorted(tick_cost[then])))
                stem = os.path.basename(ym)[:-3].replace("  ", " ")
                footprints[stem] = where[7]
                row = re.search(r"^\| %s \| (\d+) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \|$"
                                % re.escape(stem),
                                open(os.path.join(ROOT, "doc", "performance.md")).read(), re.M)
                said = row and tuple(int(x) for x in row.groups())
                counted = (frames, average, max(cost), int(sum(adv) / len(adv)), adv[where[0]])
                # Measure each build against its matching play-call and
                # tick tables. Absolute reads change start arithmetic
                # and tick costs. Ordinary tables omit deferred and
                # lookup support; -defer uses its deferred section,
                # while -map reads fixture overhead in A remap at the
                # frame (plays).
                if PCREL and MAP and not DEFER and not LEAN and said:
                    plays[stem] = (said, counted, where[6], where[5], where[9])
                if PCREL and not DEFER and not MAP and not where[5] & 0x20 \
                        and (not said or said[0] != counted[0] or not near(said[1:], counted[1:])):
                    stale.append("performance.md says %s for %s, and the rig counts %s" % (
                        said, stem, counted))
                # the table against 0.4.22: that release's call and this
                # one's, on average and at most
                against = re.search(r"^\| %s \| (\d+) \| (\d+) \| (\d+) \| (\d+) \|$"
                                    % re.escape(stem),
                                    open(os.path.join(ROOT, "doc", "performance.md")).read(), re.M)
                if PCREL and not DEFER and not MAP and not where[5] & 0x20 and (
                        not against or not near((int(against.group(2)), int(against.group(4))),
                                                (average, max(cost)))):
                    stale.append("performance.md's table against 0.4.22 reads %s for %s, and the"
                                 " rig counts %d and %d" % (against and against.groups(), stem,
                                                            average, max(cost)))
                # plan.md's closing figures, on the tune it names. No
                # figure reached them: its call had moved twice and its
                # ticks once,
                # while performance.md's table beside them stayed fixed. The
                # figures are the core the document reckons against, whose
                # ticks drop the level and write an end of interrupt, so
                # the lean core reads its ticks and not these. Every effect
                # of this tune is a square, whose tick costs the same under
                # both builds, so the three figures are under -abs too.
                if stem == "Synergy Credits" and not LEAN and PCREL and DEFER and not MAP:
                    # the play call of the build with the deferred start,
                    # against the build without it (A deferred start)
                    perf_said = " ".join(open(os.path.join(ROOT, "doc", "performance.md"))
                                         .read().split("## A deferred start")[1].split())
                    with_it = re.search(
                        r"the play call of Synergy Credits counts ([\d,]+) cycles on"
                        r" average and ([\d,]+) at most", perf_said)
                    if not with_it or not near(tuple(int(x.replace(",", ""))
                                                     for x in with_it.groups()),
                                               (average, max(cost))):
                        stale.append("performance.md reads the play call of Synergy Credits"
                                     " with the deferred start in as %s, and the rig counts"
                                     " %d and %d" % (with_it and with_it.groups(), average,
                                                    max(cost)))
                if stem == "Synergy Credits" and not LEAN and PCREL and not DEFER and not MAP:
                    # the costliest frame, counted from the first as 1,
                    # which the section against YMX places past its run
                    perf_said = " ".join(open(os.path.join(ROOT, "doc", "performance.md")).read().split())
                    nth = re.search(r"Synergy Credits' costliest frame is its ([\d,]+)(?:st|nd|rd|th)",
                                    perf_said)
                    if not nth or int(nth.group(1).replace(",", "")) != where[0] + 1:
                        stale.append("performance.md places Synergy Credits' costliest frame at %s,"
                                     " and the rig counts frame %d" % (nth and nth.group(1), where[0] + 1))
                    plan = " ".join(open(os.path.join(ROOT, "doc", "plan.md")).read().split())
                    closing = re.search(
                        r"Synergy Credits reads ([\d,]+) cycles a call against the"
                        r" [\d,]+ this document opened at and ([\d,]+) cycles of"
                        r" ticks against [\d,]+, so ([\d,]+) a frame", plan)
                    if not closing:
                        stale.append("plan.md has no closing figures for Synergy Credits")
                    else:
                        was = tuple(int(x.replace(",", "")) for x in closing.groups())
                        got = (average, ticked, average + ticked)
                        if not near(was, got):
                            stale.append("plan.md says %s for Synergy Credits, and the"
                                         " rig counts %s" % (was, got))
                # A lean tick drops no interrupt level, so the row a tune of
                # one effect runs is the row every other tune runs, and the
                # five kinds read the lean table's second figure.
                for then, name in (("on", "a row written, the place stepped"),
                                   ("loop", "the marker, the place to row `RR`"),
                                   ("stop", "the marker, the timer stopped"),
                                   ("square", "a square's two rows, no place stepped"),
                                   ("one", "a source of one row, no place stepped"),
                                   ("on alone", "a row written, the tune running one effect"),
                                   ("loop alone", "the marker to row `RR`, one effect"),
                                   ("stop alone", "the marker and the stop, one effect"),
                                   ("square alone", "a square's two rows, one effect"),
                                   ("one alone", "a source of one row, one effect")):
                    if then not in tick_cost:
                        continue
                    if LEAN:
                        name = LEAN_ROW[then.replace(" alone", "")]
                        row = r"^\| %s \| \d+ \| (\d+) \|$" % re.escape(name)
                    else:
                        row = r"^\| %s \| (\d+) \|$" % re.escape(name)
                    said_doc = open(os.path.join(ROOT, "doc", "performance.md")).read()
                    if not PCREL:
                        # the table of that build, under its heading
                        said_doc = said_doc.split(
                            "## A tick through an absolute address")[1]
                    tick = re.search(row, said_doc, re.M)
                    if not tick or not near(int(tick.group(1)), tick_cost[then]):
                        stale.append("performance.md's tick %s is not %s" % (then, tick_cost[then]))
            if refills_of:
                # the parts of the same pass, after the call's figures
                line += ("; a refill %4d cycles outside the decoder and %4d"
                         " inside it at no operation, %5d at its heaviest,"
                         " %2d operations" % (
                             refills_of["outside"], refills_of["idle"],
                             refills_of["heaviest"], refills_of["operations"]))
                if refills_of["slope"]:
                    line += ", %3.0f an operation fitted" % refills_of["slope"]
                line += ", a period of %d rows at unit %d" % (refills_of["period"],
                                                             refills_of["unit"])
            print(line)
        except AnotherFormat as said:
            another.append(os.path.basename(ym))
            print("%-45s %s" % (os.path.basename(ym), said))
        except AssertionError as failed:
            # A tune that fails is named and the rest are read, so one run
            # says every tune that fails and not the first alone. One line
            # a tune, as bin/ymxr-check reports: a reader reads the tune by
            # itself to see the whole of it.
            wrong.append(os.path.basename(ym))
            said = str(failed).strip().split("\n")[0]
            print("%-45s FAILED: %s" % (os.path.basename(ym), said[:120]))
    if costs and not args and wide is None and not kit and not LEAN:
        # a run over the eleven fixtures reads the section on a counted
        # tick of two columns against the kit's tunes of that shape
        stale += counted_read(code, symbols, cycles_of, general)
    if costs and MAP and not args and wide is None and not kit and not DEFER \
            and (PCREL or not LEAN):
        # For eleven fixtures with lookup support, verify A tick through
        # a transformation, A remap at the frame and The cost of 34
        # columns against mapped tunes.
        stale += mapped_read(code, symbols, cycles_of, general, plays)
    if costs and not args and wide is None and not kit and PCREL and not LEAN:
        # plan.md's shares, gates and parts over the eleven fixtures
        stale += plan_read(walker or FrameWalk(cycles_of, code, symbols), tunes, measured)
    if footprints and not args and wide is None and not kit and PCREL and not LEAN:
        # the bound tunes and their state blocks against performance.md's
        # table of them
        stale += footprint_read(footprints)
    if savings and not args and wide is None and not kit and PCREL and not LEAN and not MAP:
        # a run over the eleven fixtures reads the section against YMX's
        # dense columns as the rig counts them
        stale += dense_read(savings, costs)
    if measured:
        # The advance's parts of the same pass, and the sentences of
        # performance.md that read them back: a run over the eleven fixtures
        # reads every figure of The play call the table does not carry.
        stale += refills(measured, tunes, code, symbols, cycles_of,
                         not args and wide is None and not kit)
    assert not stale, "\n".join(sorted(set(stale)))
    played = len(tunes) - len(another)
    if wrong:
        raise SystemExit("%d of %d tunes failed: %s"
                         % (len(wrong), played, ", ".join(wrong)))
    print("%d tunes play as the specification reads%s" % (
        played, ", and %d of another format is outside the run: %s"
        % (len(another), ", ".join(another)) if another else ""))


def assemble_text(text):
    """The bytes rmac assembles a few lines of 68000 to, at 0."""
    work = tempfile.mkdtemp()
    source, out = os.path.join(work, "part.S"), os.path.join(work, "part.bin")
    open(source, "w").write(text)
    r = subprocess.run([RMAC, "-m68000", "-fr", "-o", out, source], capture_output=True)
    assert r.returncode == 0, r.stdout.decode() + r.stderr.decode()
    return open(out, "rb").read()


def counted(module, code, jumps):
    """The cycles of code's instructions in order, as the rig counts the
    player's: a short branch among them jumping where `jumps` is true."""
    at, total = 0, 0
    while at < len(code):
        words = struct.unpack(">5H", (code[at:at + 10] + bytes(10))[:10])
        op = words[0]
        after = None
        if op >> 12 == 6 and op & 0xFF:
            after = at + 2 + ((op & 0xFF) ^ 0x80) - 0x80 if jumps else at + 2
        cycles, size = module.cycles_of(words, 0, None, at, after, None)
        total += cycles
        at += 2 * size
    return total


def dense(module, code, symbols):
    """What register columns written unconditionally would cost this
    player, counted as the rig counts it (performance.md, Against YMX):
    a register's write in the form the player writes a set column in,
    the select and the column into d0 and one movep, fourteen of them;
    the effects behind one bit, tested as the player tests a column's set
    bit, the test at WRITE _6's site, its branch jumping over the effects'
    steps on a row whose effect columns are all clear; and the test the
    player had when it formed the select before it, one move ahead of the
    same two instructions."""
    one = assemble_text("        move.w  #$0600,d0\n        move.b  $7FFF(a1),d0\n"
                        "        movep.w d0,(a2)\n")
    block = assemble_text("".join("        move.w  #$%02X00,d0\n        move.b  $7FFF(a1),d0\n"
                                  "        movep.w d0,(a2)\n" % r for r in range(14)))
    test = code[symbols["c_6"]:symbols["c_6"] + 6]
    assert struct.unpack(">H", test[4:6])[0] >> 8 == 0x6A, "WRITE _6 is not a read and a bpl.s"
    select = assemble_text("        move.w  #$0600,d1\n")
    return {"write": counted(module, one, False), "block": counted(module, block, False),
            "skip": counted(module, test, True), "run": counted(module, test, False),
            "then": counted(module, select, False) + counted(module, test, True)}


class FrameWalk:
    """The frame procedure's register steps, 4 to 8, walked for a row
    through the player's instructions and counted with DTX's table as
    the rig counts the player: each macro's branches go as its tests read
    the row's bytes. A run over the fixtures reads the walk against the
    rig's count of every frame, so the walk of a row the player never
    plays, a gate's path, counts what the player would."""

    def __init__(self, module, code, symbols):
        self.module, self.code, self.at = module, code, symbols

    def walk(self, start, stop, decide, code=None):
        """The cycles from start to stop, the n-th conditional branch jumping
        where decide(n) is true and bra always."""
        code = self.code if code is None else code
        at, total, n = start, 0, 0
        while at != stop:
            words = struct.unpack(">5H", (code[at:at + 10] + bytes(10))[:10])
            op = words[0]
            after = None
            if op >> 12 == 6:
                disp = op & 0xFF
                size = 2 if disp else 4
                target = at + 2 + ((disp ^ 0x80) - 0x80 if disp else (words[1] ^ 0x8000) - 0x8000)
                jump = True if op >> 8 & 0xF == 0 else decide(n)
                if op >> 8 & 0xF:
                    n += 1
                after = target if jump else at + size
            cycles, size = self.module.cycles_of(words, 0, None, at, after, None)
            total += cycles
            at = after if after is not None else at + 2 * size
            assert 0 <= at <= len(code) and total < 5000, "the walk left the code at %x" % at
        return total

    def tones(self, r):
        """Step 4: the three pairs, TONE fine,coarse."""
        total = 0
        for fine, coarse, stop in ((0, 1, "c_3"), (2, 3, "c_5"), (4, 5, "c_6")):
            f, c = r[fine], r[coarse]
            if f != 0:
                went = [True, not c & 0x80]
            elif c == 0:
                went = [False, True]
            else:
                went = [False, False, not c & 0x40, not c & 0x80]
            total += self.walk(self.at["c_%d" % coarse], self.at[stop], lambda n, w=went: w[n])
        return total

    def volumes(self, r):
        """Step 6's three columns on the path the row runs: WRITE after a
        column 13 other than 0, RWRITE in the copy after one of 0."""
        kind = "r" if r[13] == 0 else "c"
        return sum(self.walk(self.at["%s_%d" % (kind, v)], self.at["%s_%d" % (kind, stop)],
                             lambda n, v=v: not r[v] & 0x80)
                   for v, stop in ((8, 9), (9, 10), (10, 7)))

    def registers(self, r):
        """Steps 4 to 8 of the row, ymxr_regs to ymxr_played."""
        at = self.at
        total = self.tones(r) + self.walk(at["c_6"], at["c_13"], lambda n: not r[6] & 0x80)
        if r[13] == 0:
            total += self.walk(at["c_13"], at["q_11"], lambda n: True)
            total += self.walk(at["q_11"], at["q_12"], lambda n: r[11] == 0)
            total += self.walk(at["q_12"], at["r_8"], lambda n: r[12] == 0)
            total += self.volumes(r)
            return total + self.walk(at["r_7"], at["ymxr_played"], lambda n: not r[7] & 0x80)
        total += self.walk(at["c_13"], at["c_11"], lambda n: False)
        for column, bit, stop in ((11, 6, "c_12"), (12, 5, "c_8")):
            went = [True] if r[column] else [False, not r[13] >> bit & 1]
            total += self.walk(at["c_%d" % column], at[stop], lambda n, w=went: w[n])
        total += self.volumes(r)
        return total + self.walk(at["c_7"], at["ymxr_played"],
                                 lambda n: not r[7] & 0x80 if n == 0 else not r[13] & 0x80)

    def snippet(self, text, decide):
        part = assemble_text(text)
        return self.walk(0, len(part), decide, part)


# The rows plan.md's shares and gates are measured over, a tune.
PLAN_ROWS = 40000


def plan_figures(walker, tunes):
    """plan.md's figures of the frame procedure, off the rows of `tunes`,
    PLAN_ROWS of each played through its loop: a tune's share of rows that
    set no tone, no volume and no envelope column, its register columns a
    row, and what three gates would cost or save it a frame, each a mean
    over the rows. A gate is a bit a row sets where it sets a column of a
    group, tested before the group: 20 cycles where it skips and 18 where it
    does not, as walked. The volume gate in column 13 moves a row that sets
    a volume where column 13 was 0 from the copy of steps 6 and 7 to the
    longer path; in column 6, and the tone gate, each read their bit on
    every row, a move of 12."""
    gate = "        btst    #4,d2\n        beq.s   over\n        nop\nover:\n"
    skip = walker.snippet(gate, lambda n: True)
    part = assemble_text(gate)
    run = walker.walk(0, len(part) - 2, lambda n: False, part)
    read = walker.snippet("        move.b  $7FFF(a1),d3\n", lambda n: False)
    unset = [0] * 30
    figures = {"skip": skip, "run": run, "read": read,
               "column": walker.walk(walker.at["c_6"], walker.at["c_13"], lambda n: True),
               "pair": walker.tones(unset) // 3, "volumes": walker.volumes([0] * 13 + [1] + [0] * 16),
               "tones": walker.tones(unset), "tunes": {}}
    base = walker
    for ym in tunes:
        work = tempfile.mkdtemp(prefix="ymxr68")
        file, _ = convert(ym, work)
        bound = bind(file, work)
        tune = Tune(bound, work)
        model = Model(tune)
        # the tune's frame procedure as init patched it
        m = Machine(base.code, base.at, bound)
        assert m.call("init", a0=FILE, a1=WORK + 0x100) == 0, "init rejected the tune"
        walker = FrameWalk(base.module, bytes(m.mu.mem_read(CODE, len(base.code))), base.at)
        none = {"tone": 0, "volume": 0, "envelope": 0}
        columns = 0
        net = {"13": 0, "6": 0, "tone": 0}
        for n in range(PLAN_ROWS):
            model.begin()
            r = model.playing
            wrote = {register for register, _ in model.writes()}
            columns += len(wrote)
            tone = bool(wrote & {0, 1, 2, 3, 4, 5})
            volume = bool(wrote & {8, 9, 10})
            none["tone"] += not tone
            none["volume"] += not volume
            none["envelope"] += not wrote & {11, 12, 13}
            if volume:
                moved = 0
                if r[13] == 0:
                    gated = list(r)
                    gated[13] = 0x10            # bit 4, the gate: the longer path
                    moved = walker.registers(gated) - walker.registers(r)
                net["13"] += run + moved
                net["6"] += read + run
            else:
                net["13"] += skip - walker.volumes(r)
                net["6"] += read + skip - walker.volumes(r)
            net["tone"] += read + (run if tone else skip - walker.tones(r))
        stem = os.path.basename(ym)[:-3].replace("  ", " ")
        figures["tunes"][stem] = {
            "no " + k: 100.0 * v / PLAN_ROWS for k, v in none.items()}
        figures["tunes"][stem].update({"columns": columns / float(PLAN_ROWS),
                                       "net 13": net["13"] / float(PLAN_ROWS),
                                       "net 6": net["6"] / float(PLAN_ROWS),
                                       "net tone": net["tone"] / float(PLAN_ROWS)})
    return figures


def sample_tick(walker):
    """A row tick of the general handler, instruction by instruction, on the
    path that writes a row and steps the place: the two chip writes, the
    marker's test, the level dropped, the step and the end of interrupt,
    and the read and the step an address register would replace."""
    code, at = walker.code, walker.at["ymxr_tick0"]
    parts = []
    while struct.unpack(">H", code[at:at + 2])[0] != 0x4E73:          # rte
        words = struct.unpack(">5H", (code[at:at + 10] + bytes(10))[:10])
        after = at + 2 if words[0] >> 12 == 6 else None
        cycles, size = walker.module.cycles_of(words, 0, None, at, after, None)
        parts.append((words[0], cycles))
        at += 2 * size
    kinds = {"writes": 0, "test": 0, "level": 0, "step": 0, "end": 0}
    writes = 0
    for op, cycles in parts:
        if op >> 12 == 6:
            kinds["test"] += cycles
        elif op == 0x46FC:
            kinds["level"] += cycles
        elif op >> 12 == 5:
            kinds["step"] += cycles
            step = cycles
        elif op >> 12 == 1 and writes < 2:
            kinds["writes"] += cycles
            writes += 1
            read = cycles
        else:
            kinds["end"] += cycles
    kinds["whole"] = sum(c for _, c in parts)
    kinds["through"] = read + step
    kinds["register"] = walker.snippet("        move.b  (a0)+,$FFFF8802.w\n", lambda n: False)
    return kinds


# A share spelled as a part: a sixth is one in six.
PARTS = {2: "a half", 3: "a third", 4: "a quarter", 5: "a fifth", 6: "a sixth",
         7: "a seventh", 8: "an eighth", 9: "a ninth", 10: "a tenth"}


def plan_read(walker, tunes, parts):
    """plan.md against the rig: the groups a row leaves unset and the
    columns it sets, the costs of a column, a pair and the volume group,
    the three gates, the sample tick's parts, and, where the run counted a
    refill's parts, the operations of Turrican 2 - world completed 1's."""
    text = open(os.path.join(ROOT, "doc", "plan.md")).read()
    said = " ".join(text.split())
    fig = plan_figures(walker, tunes)
    tune = fig["tunes"]
    stale = []

    def read(pattern, counted, what):
        m = re.search(pattern, said)
        got = m and tuple(g.replace(",", "") if re.fullmatch(r"[\d,]+", g) else g
                          for g in m.groups())
        want = tuple(str(x) for x in counted)
        if not near(got, want):
            stale.append("plan.md reads %s as %s, and the rig counts %s" % (what, got, want))

    for name, v in tune.items():
        read(r"\| %s \| (\d+)%% \| (\d+)%% \| (\d+)%% \| (\d+\.\d) \|" % re.escape(name),
             ("%.0f" % v["no tone"], "%.0f" % v["no volume"], "%.0f" % v["no envelope"],
              "%.1f" % v["columns"]), "the groups %s leaves unset" % name)
    columns = sorted(v["columns"] for v in tune.values())
    median = columns[len(columns) // 2]
    read(r"a row sets (\d+\.\d) to (\d+\.\d) of the fourteen register columns, (\d+\.\d) on the"
         r" median tune", ("%.1f" % columns[0], "%.1f" % columns[-1], "%.1f" % median),
         "the columns a row sets")
    read(r"unset on 40 per cent of the rows or more on (\w+) of the (\w+) and the envelope group"
         r" on 62 per cent or more on (\w+);",
         (WORDS[sum(1 for v in tune.values() if round(v["no volume"]) >= 40)], WORDS[len(tune)],
          WORDS[sum(1 for v in tune.values() if round(v["no envelope"]) >= 62)]),
         "the tunes whose groups go unset")
    read(r"A column the row leaves unset costs (\d+) cycles and a tone pair (\d+)",
         (fig["column"], fig["pair"]), "an unset column and pair")
    read(r"What is left to gate is the volume group's three columns, (\d+) cycles",
         (fig["volumes"],), "the volume group")
    read(r"counts a gate that skips at (\d+) cycles and one that does not at (\d+)",
         (fig["skip"], fig["run"]), "a gate")
    for name, v in tune.items():
        net = int(round(v["net 13"]))
        read(r"\| %s \| (\d+)%% \| \**([-+]?\d+)\** \|" % re.escape(name),
             ("%.0f" % v["no volume"], ("+%d" % net) if net > 0 else "%d" % net),
             "the gate in column 13 on " + name)
    means = {k: sum(v[k] for v in tune.values()) / len(tune) for k in ("net 13", "net 6", "net tone")}
    costs = {k: [n for n, v in tune.items() if round(v[k]) > 0] for k in means}
    read(r"\| the mean of the (\w+) \| \| (-?\d+) \|", (WORDS[len(tune)], int(round(means["net 13"]))),
         "the gate in column 13's mean")
    read(r"it saves (\d+) cycles a frame on the mean of the (\w+) fixtures, and it costs (\w+) of"
         r" them", (-int(round(means["net 13"])), WORDS[len(tune)], WORDS[len(costs["net 13"])]),
         "the gate in column 13")
    six = costs["net 6"]
    read(r"costs a read of (\d+) on every row and leaves column 13 alone, reads (-?\d+) as the mean,"
         r" the better of the two, and costs (\w+) tunes: (.+?)\. Column 13 pays",
         (fig["read"], int(round(means["net 6"])), WORDS[len(six)],
          ", ".join(six[:-1]) + " and " + six[-1]), "the gate in column 6")
    if means["net 6"] >= means["net 13"]:
        stale.append("plan.md reads column 6 as the better gate, and the rig counts column 13's"
                     " mean lower")
    idle = [v["no tone"] for v in tune.values()]
    share = sum(idle) / len(idle) / 100.0
    read(r"reads \+(\d+): its six columns are idle on (\d+) to (\d+) per cent of rows, and the"
         r" (\d+) it saves on (an? \w+) of them does not pay the (\d+) it costs on the rest",
         (int(round(means["net tone"])), "%.0f" % min(idle), "%.0f" % max(idle),
          fig["tones"] - fig["skip"] - fig["read"], PARTS[int(round(1 / share))],
          fig["read"] + fig["run"]), "the tone gate")
    read(r"(\d+) cycles a frame, the better gate, is a version of the tune file, .*? and (\w+)"
         r" tunes that read slower", (-int(round(means["net 6"])), WORDS[len(six)]),
         "the better gate")
    tick = sample_tick(walker)
    read(r"Of the (\d+) its instructions cost, (\d+) are the chip writes, (\d+) the step, (\d+) the"
         r" end of interrupt, (\d+) the level dropped and (\d+) the marker test",
         (tick["whole"], tick["writes"], tick["step"], tick["end"], tick["level"], tick["test"]),
         "the parts of a sample's tick")
    read(r"`move\.b \(a0\)\+,YM_SELECT\+2\.w`, is (\d+) against the (\d+) the read through the"
         r" program counter and its step cost", (tick["register"], tick["through"]),
         "the read and the step")
    if parts and "Turrican 2 - world completed 1" in parts:
        read(r"a refill of Turrican 2 - world completed 1 parses (\w+) operations at its"
             r" heaviest", (WORDS[parts["Turrican 2 - world completed 1"]["operations"]],),
             "Turrican 2 - world completed 1's heaviest refill")
    return stale


def saving(steps, effects, costs):
    """The cycles a frame dense register columns would save this tune on
    average, a cost where negative. Where the tune's effect bits are all
    clear, both procedures jump over the effects' steps; otherwise the
    dense one tests the bit and runs the steps on the rows that set an
    effect column."""
    saved = 0
    for effect_cycles, register_cycles, touched, _ in steps:
        now = effect_cycles + register_cycles
        if not effects:
            then = costs["block"] + effect_cycles
        elif touched:
            then = costs["block"] + costs["run"] + effect_cycles
        else:
            then = costs["block"] + costs["skip"]
        saved += now - then
    return saved / float(len(steps))


def with_movep(module):
    """DTX's tables with movep added, which its reader never uses: the
    manual's 16 cycles for a word and 24 for a long, either way."""
    inner = module.cycles_of

    def cycles_of(words, sr, dn, pc, next_pc, source):
        op = words[0]
        if op & 0xF138 == 0x0108:
            return (24 if op & 0x0040 else 16), 2
        return inner(words, sr, dn, pc, next_pc, source)
    module.cycles_of = cycles_of


class CyclesOn:
    """DTX's cycle counter over this rig's machine, once it exists, with
    the cycles spent in the image, DTX's advance, told from the rest, and
    within those the cycles spent inside ST4's decoder and the entries to
    the heads a caller counts (refill)."""

    def __init__(self, module):
        self.module = module
        self.counter = None
        self.cycles = 0
        self.image = 0
        self.decoder = 0
        self.range = (0, 0)
        self.inner = (0, 0)
        self.hits = {}
        # named address ranges of the player, and the cycles spent in each
        self.regions = {}
        self.spent = {}

    def attach(self, m, image=(0, 0), decoder=(0, 0), heads=(), regions=None):
        self.counter = self.module.Cycles(m)
        m.counter = self.counter
        self.range = image
        self.inner = decoder
        self.hits = {at: 0 for at in heads}
        self.regions = dict(regions or {})
        self.spent = {name: 0 for name in self.regions}
        inner = self.counter._settle
        counter = self.counter
        rig = self

        def settle(next_pc):
            pending = counter.pending
            before = counter.cycles
            inner(next_pc)
            if pending is None:
                return
            if rig.range[0] <= pending[0] < rig.range[1]:
                rig.image += counter.cycles - before
                if rig.inner[0] <= pending[0] < rig.inner[1]:
                    rig.decoder += counter.cycles - before
            for name, (low, high) in rig.regions.items():
                if low <= pending[0] < high:
                    rig.spent[name] += counter.cycles - before
            if pending[0] in rig.hits:
                rig.hits[pending[0]] += 1
        self.counter._settle = settle

    def _settle(self, at):
        if self.counter:
            self.counter._settle(at)
            self.cycles = self.counter.cycles


if __name__ == "__main__":
    main()
