"""The player under emulation, against a model of SPEC.md 4 and 5.

Every tune under ym/test is converted, then played row by row on an
emulated 68000: the chip writes of every frame against the frame
procedure's, the timers' programming against the rate columns', the place
each handler keeps against the source's rows, and every tick's write
against the row its place stands on. The model is built here from the
tune's tables, so the player is checked against the specification and not
against the converter.

Usage: test_ymxr.py [tune.ym ...]      the fixtures under ym/test by default
       test_ymxr.py -corpus [N]        a spread of the corpus in place of the
                                       fixtures, 40 tunes unless N sets it
       test_ymxr.py -framesN [tunes]   each tune played for N frames at most,
                                       for a short run over every shape
       test_ymxr.py -cycles [tunes]    the play call's cost as well
       test_ymxr.py -hatari [tunes]    the same tunes on a real MFP, under
                                       Hatari
       test_ymxr.py -perf [tunes]      the player built with the raster
                                       monitor in, against the same model:
                                       the monitor moves no chip write
       test_ymxr.py -kit [tunes]       the conformance kit's tune files, the
                                       player's frames against the reader's
                                       entries a frame at a time

The fixtures under ym/test are chosen for the shapes a tune has, one of
each; -corpus reads the corpus instead, which no fixture was chosen for. A
tune that fails is named and the rest are read, so one run reports every
tune that fails rather than the first.

The player reads a bound tune (doc/BINARIES.md 1): each tune file is bound
through bin/ymxr-bind before it is played, and a tune file of another
version is checked to be rejected by the binder, the reader and, its bound
form's version moved, the player.

Under unicorn the timers are modelled here, since it raises no interrupt:
every tick is fired here at the time the model computes. Under Hatari the
MFP fires them: the tune goes into an SNDH file and a program around it
through bin/ymxr-sndh and bin/ymxr-prg (BINARIES.md 3 and 4), the program
claims the machine and plays the tune on the VBL, since the screen's rate
is the tune's 50 Hz, and the trace of every chip write is read against the
same model, the frames cut at the VBL and the ticks counted against the
rates the trace shows the timers programmed at. So -hatari requires tunes
at 50 Hz.

Needs rmac (RMAC, or on the path), DTX's dtx-write (DTX_WRITE, or on the
path) to read the table back, unicorn (pip install unicorn), and the
converter, the binder and the two combiners under bin/. DTX's rig, at
DTX_REPO/68k/test/emu, counts the cycles where it is found; hatari
(HATARI) with a TOS image (TOS) plays the tune on a real MFP.
"""
import json, os
import re
import struct
import subprocess
import sys
import tempfile

from unicorn import (Uc, UcError, UC_ARCH_M68K, UC_MODE_BIG_ENDIAN,
                     UC_HOOK_MEM_WRITE, UC_HOOK_MEM_READ, UC_HOOK_CODE)
from unicorn.m68k_const import (UC_CPU_M68K_M68000, UC_M68K_REG_D0,
                                UC_M68K_REG_D1, UC_M68K_REG_D2, UC_M68K_REG_D3,
                                UC_M68K_REG_D6, UC_M68K_REG_D7, UC_M68K_REG_A0,
                                UC_M68K_REG_A1, UC_M68K_REG_A6, UC_M68K_REG_A7,
                                UC_M68K_REG_PC, UC_M68K_REG_SR)

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", "..", ".."))
RMAC = os.environ.get("RMAC", "rmac")
DTX_WRITE = os.environ.get("DTX_WRITE", "dtx-write")
DTX_REPO = os.environ.get("DTX_REPO", os.path.join(ROOT, "..", "DTX"))
HATARI = os.environ.get("HATARI", "hatari")
TOS = os.environ.get("TOS", os.path.expanduser("~/hatari-2.6.1_macos/tos-2.06.rom"))
# The rows the program is asked to play before it stops (bin/ymxr-prg -r).
STUB_FRAMES = 2000
# The tune file's version (SPEC.md 3.3) and the bound tune's (BINARIES.md 1).
TUNE_VERSION = 3
BOUND_VERSION = 3
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
FILE = 0x10000 + 2
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
# The bits each register reads of a byte written to it: the rest the chip
# ignores, and the player leaves the set bit and the bits beside in.
FITS = [0xFF, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0x1F, 0xFF, 0x1F, 0x1F, 0x1F, 0xFF, 0xFF, 0x0F]


def masked(writes):
    return [(reg, value & FITS[reg]) for reg, value in writes]


# What the player is assembled with: the raster monitor's switch, which
# an equate of the source reads as the assembly defines it.
# The 68000's interrupt entry and its rte, which the cycle counter here
# does not reach: 44 and 20 from the manual. performance.md's 172 for a
# tick is its 108 and these.
ENTRY = 64

PERF = "-perf" in sys.argv
LEAN = "-lean" in sys.argv

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
    source = open(os.path.join(ROOT, "68k", "YMXR.S")).read()
    m = re.search(r"^%s\s+equ\s+([-$\w+*]+)" % name, source, re.M)
    assert m, name + " is not an equate of YMXR.S"
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
# stands at; and a one-row source handler's, the same two. Every offset
# is measured off its handler's labels, so they stand once the
# player is assembled (main).
TICK_SEL = TICK_PTR = SQ_SEL = SQ_VAL = ONE_SEL = ONE_VAL = 0


def assemble(source="YMXR.S", defines=()):
    """The bytes a source under 68k/ assembles to, and its symbols: the
    player's, or the SNDH core's, which includes the player."""
    work = tempfile.mkdtemp()
    out, lst = os.path.join(work, "code.bin"), os.path.join(work, "code.lst")
    r = subprocess.run([RMAC, "-m68000", "-fr", "-l*" + lst, "-i" + os.path.join(ROOT, "68k")]
                       + list(defines) + ["-o", out, os.path.join(ROOT, "68k", source)],
                       capture_output=True)
    assert r.returncode == 0, r.stdout.decode() + r.stderr.decode()
    symbols = {}
    for line in open(lst):
        parts = line.split()
        for i in range(0, len(parts) - 2, 3):
            if parts[i + 2] == "t" and len(parts[i + 1]) == 16:
                try:
                    symbols[parts[i]] = int(parts[i + 1], 16)
                except ValueError:
                    pass
    return open(out, "rb").read(), symbols


CORPUS = os.environ.get("YM_CORPUS",
                        os.path.expanduser("~/git/jatari/data/ym_format"))

# How many tunes -corpus reads where no count follows it. Forty is about
# eleven minutes, which is a net a reader runs and waits for.
CORPUS_TUNES = 40

# The frames a tune is played for at most, which -frames sets. None plays
# every tune whole, which is what a full run does.
MOST_FRAMES = None


def spread(most):
    """A spread of `most` tunes off the corpus: every Nth file by name, so
    a sample covers the corpus rather than one composer's run of it."""
    if not os.path.isdir(CORPUS):
        raise SystemExit("no corpus at " + CORPUS + ": YM_CORPUS names it")
    every = sorted(os.path.join(CORPUS, f) for f in os.listdir(CORPUS)
                   if f.lower().endswith(".ym"))
    if not every:
        raise SystemExit("no .ym file under " + CORPUS)
    return every[::max(1, len(every) // most)][:most]


def convert(ym, work):
    """A tune file out of a YM dump, through the converter; a tune file
    named as it stands."""
    if ym.endswith(".ymxr"):
        return open(ym, "rb").read(), ""
    flags = os.environ.get("YMXR_FLAGS", "").split()
    r = subprocess.run([os.path.join(ROOT, "bin", "ym-to-ymxr")] + flags,
                       stdin=open(ym, "rb"), capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    # The tool reports on standard error, the last line being what it wrote.
    said = [line for line in r.stderr.decode().splitlines() if line.startswith("ym-to-ymxr: ")]
    return r.stdout, said[-1][len("ym-to-ymxr: "):] if said else ""


def bind(file, work):
    """The bound tune of a tune file, through bin/ymxr-bind; None where the
    binder rejects the file, which it reports on a separate line."""
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-bind"), "-silent"],
                       input=file, capture_output=True)
    if r.returncode == 1 and r.stderr.startswith(b"ymxr-bind: "):
        return None
    assert r.returncode == 0, r.stderr.decode()
    return r.stdout


def trace(file, work, calls):
    """What the Java reader reports of a tune file, one entry a call
    (SPEC.md 7), through bin/ymxr-trace."""
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-trace"), "-r%d" % calls, "-silent"],
                       input=file, capture_output=True)
    assert r.returncode == 0, r.stderr.decode()
    return [json.loads(line) for line in r.stdout.decode().splitlines() if line]


def entry(model, writes):
    """The reader's entry for a frame the model just stepped: the writes
    the chip reads, and the effects the row touched."""
    w = {}
    for reg, value in masked(writes):
        # R7's two host bits belong to the host: the reader reports bits 5 to 0
        w[str(reg)] = value & 0x3F if reg == 7 else value
    e = {}
    for i in range(4):
        fx = model.fx[i]
        if not fx["touched"]:
            continue
        one = {"target": fx["target"], "source": fx["source"]}
        one["select"] = fx["select"]
        one["count"] = fx["count"]
        one["timer"] = fx["restart"]
        one["place"] = fx["reset_place"]
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
        # Where this tune's table stands in the image it is packaged
        # into: an image of one names it in its format block, and one
        # shared by several names the first, so a bound tune records a
        # separate offset
        # (BINARIES.md 2, DTX abi.md 1).
        self.table_at = long_at(file, 20)
        index = [long_at(file, 24 + 4 * i) for i in range(count)]
        end = index[0] if count else len(file)
        image = file[self.image_at:end]
        table_at = self.table_at
        csv = os.path.join(work, "table.csv")
        # dtx-write reads its input on standard input and writes its
        # output on standard output (DTX, doc/tools.md).
        r = subprocess.run([DTX_WRITE, "-text"], input=image[table_at:],
                           capture_output=True)
        assert r.returncode == 0, r.stderr.decode()
        open(csv, "wb").write(r.stdout)
        self.rows = []
        for line in open(csv):
            if line.startswith("#") or line.startswith("c0") or not line.strip():
                continue
            self.rows.append([int(x) for x in line.split(",")])
        header = image[table_at:table_at + 16]
        self.R = long_at(header, 4)
        self.RR = long_at(header, 10)
        assert len(self.rows) == self.R
        # a source: (the offset of its first row in the file, R, RR, rows)
        self.sources = [None]
        for at in index:
            r_, rr = long_at(file, at + 4), long_at(file, at + 10)
            self.sources.append((at + 16, r_, rr, list(file[at + 16:at + 16 + r_])))


class Model:
    """SPEC.md 4 and 5 over the tune's rows: the chip writes a frame makes,
    the timers' state after it, and each tick's write."""

    def __init__(self, tune):
        self.tune = tune
        self.row = 0
        # the row the frame plays, read by begin and used by the steps
        # and the writes below
        self.playing = None
        # target is the one the player keeps after step 1, and using the
        # target the running effect writes: the one kept at its start
        self.fx = [dict(target=0, using=0, source=0, select=0, count=0, place=None,
                        running=False) for _ in range(4)]

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
        them and the ones after it as they were."""
        r = self.playing
        t = EFFECT + 4 * i
        fx = self.fx[i]
        if r[t] & 0x80:
            fx["target"] = r[t] & 0x7F
        fx["restart"] = False
        # Whether this row writes the timer a select. A stopped timer starts
        # on one, bit 6 set or clear (SPEC.md 1.9), so a restart is due for
        # a stopped timer on the select alone.
        fx["selected"] = False
        fx["reset_place"] = False
        fx["touched"] = bool((r[t] | r[t + 1] | r[t + 2]) & 0x80) or r[t + 3] != 0
        if r[t + 1] & 0x80:
            source = r[t + 1] & 0x7F
            fx["source"] = source
            if source == 0:
                # a row that stops an effect moves no place, so a source
                # that starts again reads from it (SPEC.md 1.9)
                fx["running"] = False
            else:
                at, R, RR, rows = self.tune.sources[source]
                # row 0 where the row sets bit 5 of the control column, and
                # where it does not, the row number already in the place
                # (SPEC.md 1.9)
                if r[t + 2] & 0x20 or fx["place"] is None:
                    fx["place"] = 0
                fx["using"] = fx["target"]
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
        """The write a tick of effect i makes, and what follows: 'on',
        'loop' or 'stop'."""
        fx = self.fx[i]
        at, R, RR, rows = self.tune.sources[fx["source"]]
        value = rows[fx["place"]]
        write = (fx["using"], value)
        if value & 0x80:
            if RR < R:
                fx["place"] = RR
                return write, "loop"
            fx["running"] = False
            return write, "stop"
        fx["place"] += 1
        return write, "on"

    def square(self, i):
        """Whether effect i's source is two rows repeating to row 0, which
        the player's square handler uses (68k/YMXR.S, SQUARE)."""
        fx = self.fx[i]
        if fx["source"] == 0:
            return False
        at, R, RR, rows = self.tune.sources[fx["source"]]
        return R == 2 and RR == 0

    def onerow(self, i):
        """Whether effect i's source is one row repeating, which the
        player's one-row handler uses (68k/YMXR.S, ONEROW)."""
        fx = self.fx[i]
        if fx["source"] == 0:
            return False
        at, R, RR, rows = self.tune.sources[fx["source"]]
        return R == 1 and RR == 0

    def value(self, i):
        """The row a handler with its place as an immediate stands at,
        which is its place."""
        fx = self.fx[i]
        at, R, RR, rows = self.tune.sources[fx["source"]]
        return rows[fx["place"]]

    def place_address(self, i):
        fx = self.fx[i]
        if fx["place"] is None or fx["source"] == 0:
            return None
        return FILE + self.tune.sources[fx["source"]][0] + fx["place"]


class Machine:
    """The player on an emulated 68000, with the chip writes and the MFP
    writes of every call kept."""

    def __init__(self, code, symbols, tune_bytes):
        self.symbols = symbols
        mu = Uc(UC_ARCH_M68K, UC_MODE_BIG_ENDIAN)
        mu.ctl_set_cpu_model(UC_CPU_M68K_M68000)
        for at, size in ((0, 0x1000), (CODE, 0xF000), (FILE & ~0xFFF, 0x30000),
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
        mu.hook_add(UC_HOOK_MEM_WRITE, self._write)
        self.rte_at = None
        self.beam = 0
        mu.hook_add(UC_HOOK_CODE, self._code)
        # the video address counter, which moves while the chip fetches
        # pixels: the raster monitor waits for it, and a value that never
        # moved would run the wait to its ceiling
        mu.hook_add(UC_HOOK_MEM_READ, self._beam, begin=VIDEO, end=VIDEO + 1)

    def _beam(self, mu, access, address, size, value, data):
        self.beam = (self.beam + 1) & 0xFF
        mu.mem_write(VIDEO, bytes([self.beam]))

    def _code(self, mu, address, size, data):
        if self.rte_at is not None and bytes(mu.mem_read(address, 2)) == b"\x4e\x73":
            self.rte_at = address
            mu.emu_stop()

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
            # writes of the call stand before it
            self.palette.append((value & 0xFFFF, len(self.psg)))
        elif address < 0x1000 or CODE <= address < CODE + 0x10000:
            pass                        # a vector, or the player patching itself
        elif not (WORK <= address < WORK + 0x40000 or STACK <= address < STACK + 0x10000):
            self.stray.append((address, size, value))

    def call(self, name, a0=0, a1=0):
        """One call through the jump table, back at the sentinel."""
        mu = self.mu
        slot = {"init": 0, "play": 4, "stop": 8}[name]
        mu.reg_write(UC_M68K_REG_D6, 0x6D6D6D6D)
        mu.reg_write(UC_M68K_REG_D7, 0x7D7D7D7D)
        mu.reg_write(UC_M68K_REG_A6, 0x00046000)
        mu.reg_write(UC_M68K_REG_A0, a0)
        mu.reg_write(UC_M68K_REG_A1, a1)
        mu.reg_write(UC_M68K_REG_SR, 0x2000)
        sp = STACK + 0x8000
        mu.mem_write(sp - 4, struct.pack(">I", DONE))
        mu.reg_write(UC_M68K_REG_A7, sp - 4)
        self.psg, self.mfp, self.stray, self.palette = [], [], [], []
        try:
            mu.emu_start(CODE + slot, DONE, count=50_000_000)
        except UcError as bad:
            raise AssertionError("%s stopped: %s at pc %x" % (name, bad, mu.reg_read(UC_M68K_REG_PC)))
        assert mu.reg_read(UC_M68K_REG_PC) == DONE, name + " did not return"
        assert not self.stray, name + " wrote outside its memory: " + repr(self.stray[:4])
        assert mu.reg_read(UC_M68K_REG_D6) == 0x6D6D6D6D, name + " moved d6"
        assert mu.reg_read(UC_M68K_REG_D7) == 0x7D7D7D7D, name + " moved d7"
        assert mu.reg_read(UC_M68K_REG_A6) == 0x00046000, name + " moved a6"
        return mu.reg_read(UC_M68K_REG_D0) & 0xFFFFFFFF

    def interrupt(self, vector):
        """One tick through its vector, run to the handler's rte."""
        mu = self.mu
        handler = long_at(bytes(mu.mem_read(vector, 4)), 0)
        sp = STACK + 0x8000
        mu.mem_write(sp - 6, struct.pack(">HI", 0x2000, DONE))
        mu.reg_write(UC_M68K_REG_A7, sp - 6)
        mu.reg_write(UC_M68K_REG_SR, 0x2600)
        self.psg, self.mfp, self.stray = [], [], []
        self.rte_at = 0
        try:
            mu.emu_start(handler, DONE, count=1000)
        except UcError as bad:
            raise AssertionError("a tick stopped: %s at pc %x" % (bad, mu.reg_read(UC_M68K_REG_PC)))
        assert self.rte_at, "the tick did not reach its rte"
        self.rte_at = None
        assert not self.stray, "a tick wrote outside its memory: " + repr(self.stray[:4])

    def byte(self, at):
        return bytes(self.mu.mem_read(at, 1))[0]

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
        self.ier = dict(ier)            # by register address
        self.imr = dict(imr)

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
        leaves it where it stands and a select written back resumes from
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
                    self.mode[i] = mode
                    if started:
                        self.restarts[i] += 1
                        self.phase[i] = self.period(i)
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
            self.phase[i] += self.period(i)
            yield i
        for i in range(4):
            if self.mode[i]:
                self.phase[i] -= clocks

    def expected(self, i, clocks):
        """How many ticks of effect i the next `clocks` hold, the phase
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


def check(ym, code, symbols, cycles=None, kit=False, perf=False):
    """The tune on the player, against the model frame by frame and tick
    by tick; with kit, each frame against the reader's entry as well,
    and a tune that plays once to the call that reports its end."""
    work = tempfile.mkdtemp()
    file, report = convert(ym, work)
    workspace = WORK + 0x100
    version = struct.unpack(">H", file[4:6])[0]
    if version != TUNE_VERSION:
        # the binder and the reader reject the file; the player, which never
        # sees it, rejects a bound tune whose version word is moved
        assert bind(file, work) is None, "the binder took a tune file of version %d" % version
        if kit:
            assert trace(file, work, 1) == [], "the reader reports something of version %d" % version
        bound = bind(file[:4] + struct.pack(">H", TUNE_VERSION) + file[6:], work)
        assert bound is not None, "the file is not a tune of the version it records"
        assert Machine(code, symbols, bound).call("init", a0=FILE, a1=workspace) == 0, \
            "init rejected the bound tune before its version was moved"
        moved = bound[:4] + struct.pack(">H", BOUND_VERSION + 1) + bound[6:]
        m = Machine(code, symbols, moved)
        assert m.call("init", a0=FILE, a1=workspace) & 0xFFFFFFFF == 0xFFFFFFFF, \
            "init took a bound tune of version %d" % (BOUND_VERSION + 1)
        return 0, 0, [], {}, 0, (0, 0, 0, [])
    bound = bind(file, work)
    assert bound is not None, "the binder rejected the tune"
    tune = Tune(bound, work)
    assert tune.version == BOUND_VERSION, "the binder wrote version %d" % tune.version
    m = Machine(code, symbols, bound)
    if cycles:
        end = FILE + (long_at(bound, 20) if bound[9] else len(bound))
        cycles.attach(m, (FILE + tune.image_at, end))
    model = Model(tune)
    timers = Timers.of_machine(m, tune.effects)
    assert m.call("init", a0=FILE, a1=workspace) == 0, "init rejected the tune"
    # Timer C's nibble is the host's 200 Hz clock, kept unless effect 3 runs
    nibble = 0 if tune.effects & 8 else 0x50
    assert m.byte(MFP + 0x1D) & 0x70 == nibble, "init moved Timer C's nibble"
    timers.apply(m.mfp)
    for i in range(4):
        if tune.effects & 1 << i:
            assert timers.enabled(i), "effect %d's timer is not enabled" % i
            assert m.long(TIMER[i]["vector"]) == CODE + symbols["ymxr_tick%d" % i], \
                "effect %d's vector" % i
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
                 "sources": [{"rows": rows, "repeat": rr} for _, r_, rr, rows in tune.sources[1:]]}
        assert entries[0] == first, "the reader's first line is %s, the tune header is %s" % (entries[0], first)
        entries = entries[1:]
    cost = []
    for f in range(frames):
        if cycles:
            cycles._settle(None)
        before = cycles.cycles if cycles else 0
        image_before = cycles.image if cycles else 0
        d0 = m.call("play", a0=workspace)
        if cycles:
            cycles._settle(None)
            cost.append(cycles.cycles - before)
            advance.append(cycles.image - image_before)
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
            # after it, so the two marks stand around every chip write
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
        want = model.frame()
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
            # stood stopped and the row writes it a select: a select is what
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
            place = model.place_address(i)
            if place is not None and fx["running"]:
                if model.square(i):
                    at = CODE + symbols["ymxr_sq%d" % i]
                    assert m.byte(at + SQ_VAL) == model.value(i), \
                        "frame %d: effect %d's square stands at %02x, not %02x" % (
                            f, i, m.byte(at + SQ_VAL), model.value(i))
                    assert m.byte(at + SQ_SEL) == fx["using"], \
                        "frame %d: effect %d's square selects R%d, not R%d" % (
                            f, i, m.byte(at + SQ_SEL), fx["using"])
                    assert m.long(TIMER[i]["vector"]) == at, \
                        "frame %d: effect %d's vector is not its square's" % (f, i)
                elif model.onerow(i):
                    at = CODE + symbols["ymxr_one%d" % i]
                    assert m.byte(at + ONE_VAL) == model.value(i), \
                        "frame %d: effect %d's one row is %02x, not %02x" % (
                            f, i, m.byte(at + ONE_VAL), model.value(i))
                    assert m.byte(at + ONE_SEL) == fx["using"], \
                        "frame %d: effect %d's one row selects R%d, not R%d" % (
                            f, i, m.byte(at + ONE_SEL), fx["using"])
                    assert m.long(TIMER[i]["vector"]) == at, \
                        "frame %d: effect %d's vector is not its one row's" % (f, i)
                else:
                    at = CODE + symbols["ymxr_tick%d" % i]
                    assert m.long(at + TICK_PTR) == place, "frame %d: effect %d's place is %x, not %x" % (f, i, m.long(at + TICK_PTR), place)
                    assert m.byte(at + TICK_SEL) == fx["using"], "frame %d: effect %d's handler selects R%d, not R%d" % (f, i, m.byte(at + TICK_SEL), fx["using"])
        for i in timers.due(clocks):
            fx = model.fx[i]
            assert fx["running"], "frame %d: a tick of effect %d with no source running" % (f, i)
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
                if bin(tune.effects).count("1") == 1:
                    kind += " alone"        # init took the level's drop out
                tick_cost.setdefault(kind, set()).add(cycles.cycles - before)
                tick_cycles += cycles.cycles - before
            ticks += 1
            assert masked(m.psg) == masked([want]), "frame %d: tick of effect %d wrote %s, not %s" % (f, i, m.psg, [want])
            timers.apply(m.mfp)
            if model.square(i):
                at = CODE + symbols["ymxr_sq%d" % i]
                assert m.byte(at + SQ_VAL) == model.value(i), \
                    "frame %d: after a tick effect %d's square stands at %02x, not %02x" % (
                        f, i, m.byte(at + SQ_VAL), model.value(i))
            elif model.onerow(i):
                at = CODE + symbols["ymxr_one%d" % i]
                assert m.byte(at + ONE_VAL) == model.value(i), \
                    "frame %d: after a tick effect %d's one row is %02x, not %02x" % (
                        f, i, m.byte(at + ONE_VAL), model.value(i))
            elif then == "stop":
                assert timers.mode[i] == 0, "frame %d: effect %d ran out and its timer runs on" % (f, i)
            else:
                at = CODE + symbols["ymxr_tick%d" % i]
                assert m.long(at + TICK_PTR) == model.place_address(i), "frame %d: after a tick effect %d's place is off" % (f, i)
    m.call("stop", a0=workspace)
    timers.apply(m.mfp)
    for i in range(4):
        if tune.effects & 1 << i:
            assert timers.mode[i] == 0 or i == 3, "stop left effect %d's timer running" % i
    assert m.byte(MFP + 0x1D) & 0x70 == nibble, "stop moved Timer C's nibble"
    return (frames, ticks, cost, tick_cost, tick_cycles,
            (costliest[0], tune.R, tune.RR, advance))


WRITE = re.compile(r"ym write data reg=0x([0-9a-f]+) val=0x([0-9a-f]+) .* pc=([0-9a-f]+)")
MFPW = re.compile(r"mfp write \S+ ([0-9a-f]+)=0x([0-9a-f]+) video_cyc=(\d+) .* pc=([0-9a-f]+)")
VBLA = re.compile(r"^VBL=(\d+) clock=(\d+)")
ROM = 0xE00000


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
    # the frames are cut at the VBL, which the program plays from where the
    # screen's rate is the tune's: Hatari's ST here refreshes at 50 Hz
    assert tune.rate == 50, "-hatari requires tunes at 50 Hz, and this one plays at %d" % tune.rate
    sndh = os.path.join(work, "TUNE.SND")
    # These switches select the core in the file; the rig assembled the same
    # core, and the comparison below fails where the two differ. A switch
    # missed here fails there rather than running the plain core.
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-sndh"), "-silent",
                        "-t" + os.path.basename(ym)] + (["-perf"] if perf else [])
                       + (["-lean"] if LEAN else []),
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
                        "--sound", "off", "--conout", "2", "--fast-forward", "on",
                        "--disable-video", "1", "--run-vbls", str(STUB_FRAMES + 400),
                        "--log-level", "fatal", "--trace", "psg_write,video_vbl,mfp_write",
                        "--trace-file", trace, "YMXR.PRG"],
                       cwd=work, capture_output=True)
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
    # a square's handler stands beside the four, one an effect, and a
    # one-row source's beside those
    square = symbols["ymxr_sq1"] - symbols["ymxr_sq0"]
    squares = [(base + symbols["ymxr_sq%d" % i], base + symbols["ymxr_sq%d" % i] + square)
               for i in range(4)]
    one = symbols["ymxr_one1"] - symbols["ymxr_one0"]
    ones = [(base + symbols["ymxr_one%d" % i], base + symbols["ymxr_one%d" % i] + one)
            for i in range(4)]

    def where(pc):
        if frame[0] <= pc < frame[1]:
            return "frame"
        if stop[0] <= pc < stop[1]:
            return "stop"
        for i in range(4):
            if (ticks[i][0] <= pc < ticks[i][1]
                    or squares[i][0] <= pc < squares[i][1]
                    or ones[i][0] <= pc < ones[i][1]):
                return i
        return None

    # the trace as frames: each a list of (kind, reg, value) and its
    # length in MFP clocks
    frames = []
    clock = None
    current = []
    stops = {}
    for line in open(trace, errors="replace"):
        v = VBLA.match(line)
        if v:
            now = int(v.group(2))
            if clock is not None:
                frames.append((current, (now - clock) * MFP_PER_CPU, stops))
            clock = now
            current = []
            stops = {}
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
                if kind == "mfp_tick" and reg == TIMER[at]["ctrl"] \
                        and (value >> TIMER[at]["shift"]) & 0x0F == 0:
                    stops[at] = int(w.group(3)) * MFP_PER_CPU
                current.append((kind, reg, value))
    first = next(i for i, (events, _, _) in enumerate(frames)
                 if any(kind == "frame" for kind, _, _ in events))
    model = Model(tune)
    timers = Timers(tune.effects, [0, 0, 0, 0], [256] * 4,
                    {0xFFFFFA07: 0, 0xFFFFFA09: 0}, {0xFFFFFA13: 0, 0xFFFFFA15: 0})
    for events, _, _ in frames[:first]:
        timers.apply([(reg, value) for kind, reg, value in events if kind in ("mfp", "mfp_tick")])
    played = 0
    counted = [0, 0, 0, 0]
    expected = [0, 0, 0, 0]
    # The frame procedure runs early in a VBL: the four effect steps in
    # order, then every chip write of the row. A tick may land anywhere
    # between, and reads the effects stepped before it as the row leaves
    # them and the ones after it as they were, so the model is stepped one
    # effect at a time and the trace says how far the procedure has got.
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
            # the first write is effect 1's, and the writes after it stand
            # for either, so they place no source
            return of[0] if of and not stepped[of[0]] else None

        for kind, reg, value in events:
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
                fx = model.fx[kind]
                assert fx["running"], "frame %d: a tick of effect %d with no source running" % (f, kind)
                before = dict(fx)
                w, then = model.tick(kind)
                if not stepped[kind] and masked([(reg, value)]) != masked([w]):
                    model.fx[kind] = before    # the tick came after the effect step
                    upto(kind)
                    assert model.fx[kind]["running"], \
                        "frame %d: a tick of effect %d with no source running" % (f, kind)
                    w, then = model.tick(kind)
                assert masked([(reg, value)]) == masked([w]), \
                    "frame %d: a tick of effect %d wrote %s, not %s; the frame's events %s; the row %s" % (
                        f, kind, (reg, value), w, events, model.playing)
                counted[kind] += 1
        upto(3)
        if want is None:
            want = model.writes()
        assert masked(writes) == masked(want), "frame %d writes %s, not %s" % (f, writes, want)

    for f, (events, clocks, stops) in enumerate(frames[first:first + STUB_FRAMES]):
        replay(events, f)
        played += 1
        timers.apply([(reg, value) for kind, reg, value in events if kind == "mfp"])
        for i in range(4):
            got = sum(1 for kind, _, _ in events if kind == i)
            # a timer its handler stopped ticks only until that write
            want_ticks = timers.expected(i, stops.get(i, clocks))
            expected[i] += want_ticks
            if os.environ.get("YMXR_TICKS") and abs(got - want_ticks) > 1:
                print("frame %d: effect %d ticked %d, the rates say %d; mode %s count %s" % (
                    f, i, got, want_ticks, timers.mode, timers.count))
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
    stopped = [(reg, value) for events, _, _ in frames[first + STUB_FRAMES - 1:first + STUB_FRAMES + 3]
               for kind, reg, value in events if kind == "stop"]
    assert masked(stopped) == [(8, 0), (9, 0), (10, 0)], "the stop wrote %s" % stopped
    return played, sum(counted)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    count = "-cycles" in sys.argv
    real = "-hatari" in sys.argv
    kit = "-kit" in sys.argv
    perf = PERF
    wide = next((a for a in sys.argv[1:] if a.startswith("-corpus")), None)
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
        # binder and the reader both reject it, which is what the kit reads
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
    defines = ["-dYMXR_PERF=1"] if perf else []
    if LEAN:
        defines += ["-dYMXR_NEST=0", "-dYMXR_AEOI=1"]
    code, symbols = assemble(defines=defines)
    global TICK_SEL, TICK_PTR, SQ_SEL, SQ_VAL, ONE_SEL, ONE_VAL
    TICK_SEL = equate("TICK_SEL", symbols)
    TICK_PTR = equate("TICK_PTR", symbols)
    SQ_SEL = equate("SQ_SEL", symbols)
    SQ_VAL = equate("SQ_VAL", symbols)
    ONE_SEL = equate("ONE_SEL", symbols)
    ONE_VAL = equate("ONE_VAL", symbols)
    print("the player: %d bytes%s%s" % (len(code),
                                       ", the raster monitor in" if perf else "",
                                       ", the lean tick" if LEAN else ""))
    if real:
        code, symbols = assemble("YMXR_sndh.S", defines=defines)
        print("the SNDH core: %d bytes%s" % (len(code),
                                             ", the raster monitor in" if perf else ""))
    cycles_of = None
    if count:
        sys.path.insert(0, os.path.join(DTX_REPO, "68k", "test", "emu"))
        import test_dtx
        with_movep(test_dtx)
        cycles_of = test_dtx
    stale = []
    wrong = []
    for ym in tunes:
        try:
            if real:
                frames, ticks = hatari(ym, code, symbols, perf)
                print("%-45s %6d frames, %6d ticks on Hatari's MFP" % (os.path.basename(ym), frames, ticks))
                continue
            frames, ticks, cost, tick_cost, tick_cycles, where = check(
                ym, code, symbols, cycles_of and CyclesOn(cycles_of), kit, perf)
            line = "%-45s %6d frames, %6d ticks" % (os.path.basename(ym), frames, ticks)
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
                # separates performance.md's 108 from its 172.
                ticked = int(round((tick_cycles + ENTRY * ticks) / float(frames)))
                if ticks:
                    line += "; the ticks %5d cycles a frame, %5d with the call" % (
                        ticked, average + ticked)
                for then in sorted(tick_cost):
                    line += ", a tick %s %s" % (then, "/".join(str(c) for c in sorted(tick_cost[then])))
                stem = os.path.basename(ym)[:-3].replace("  ", " ")
                row = re.search(r"^\| %s \| (\d+) \| (\d+) \| (\d+) \| (\d+) \| (\d+) \|$"
                                % re.escape(stem),
                                open(os.path.join(ROOT, "doc", "performance.md")).read(), re.M)
                said = row and tuple(int(x) for x in row.groups())
                counted = (frames, average, max(cost), int(sum(adv) / len(adv)), adv[where[0]])
                if said != counted:
                    stale.append("performance.md says %s for %s, and the rig counts %s" % (
                        said, stem, counted))
                # plan.md's closing figures, on the tune it names. No
                # figure reached them: its call had moved twice and its
                # ticks once,
                # while performance.md's table beside them stayed fixed. The
                # figures are the core the document reckons against, whose
                # ticks drop the level and write an end of interrupt, so
                # the lean core reads its ticks and not these.
                if stem == "Synergy Credits" and not LEAN:
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
                        if was != got:
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
                    tick = re.search(row,
                                     open(os.path.join(ROOT, "doc", "performance.md")).read(), re.M)
                    if not tick or {int(tick.group(1))} != tick_cost[then]:
                        stale.append("performance.md's tick %s is not %s" % (then, tick_cost[then]))
            print(line)
        except AssertionError as failed:
            # A tune that fails is named and the rest are read, so one run
            # says every tune that fails and not the first alone. One line
            # a tune, as bin/ymxr-check reports: a reader reads the tune by
            # itself to see the whole of it.
            wrong.append(os.path.basename(ym))
            said = str(failed).strip().split("\n")[0]
            print("%-45s FAILED: %s" % (os.path.basename(ym), said[:120]))
    assert not stale, "\n".join(sorted(set(stale)))
    if wrong:
        raise SystemExit("%d of %d tunes failed: %s"
                         % (len(wrong), len(tunes), ", ".join(wrong)))
    print("%d tunes play as the specification reads" % len(tunes))


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
    the cycles spent in the image, DTX's advance, told from the rest."""

    def __init__(self, module):
        self.module = module
        self.counter = None
        self.cycles = 0
        self.image = 0
        self.range = (0, 0)

    def attach(self, m, image=(0, 0)):
        self.counter = self.module.Cycles(m)
        self.range = image
        inner = self.counter._settle
        counter = self.counter
        rig = self

        def settle(next_pc):
            pending = counter.pending
            before = counter.cycles
            inner(next_pc)
            if pending is not None and rig.range[0] <= pending[0] < rig.range[1]:
                rig.image += counter.cycles - before
        self.counter._settle = settle

    def _settle(self, at):
        if self.counter:
            self.counter._settle(at)
            self.cycles = self.counter.cycles


if __name__ == "__main__":
    main()
