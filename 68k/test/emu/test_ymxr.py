"""The player under emulation, against a model of SPEC.md 4 and 5.

Every tune under ym/test is converted, then played row by row on an
emulated 68000: the chip writes of every frame are held to the frame
procedure's, the timers' programming to the rate columns', the place each
handler holds to the source's rows, and every tick's write to the row the
place stands on. The model is written here from the tune's own tables, so
the player is checked against the specification and not against the
converter.

Usage: test_ymxr.py [tune.ym ...]      the fixtures under ym/test by default
       test_ymxr.py -cycles [tunes]    the play call's cost as well
       test_ymxr.py -hatari [tunes]    the same tunes on a real MFP, under
                                       Hatari
       test_ymxr.py -perf [tunes]      the player built with the raster
                                       monitor in, held to the same model:
                                       the monitor moves no chip write

The player takes a bound tune (doc/BINARIES.md 1): each tune file is
bound through bin/ymxr-bind before it is played, and a tune file of
another version is held to be rejected by the binder, the reader and, its
bound form's version moved, the player.

Under unicorn the timers are modelled here, since it raises no interrupt:
every tick is fired by hand at the time the model gives. Under Hatari the
MFP fires them: the tune goes into an SNDH file and a program around it
through bin/ymxr-sndh and bin/ymxr-prg (BINARIES.md 3 and 4), the program
takes the machine over and plays the tune on the VBL, since the screen's
rate is the tune's 50 Hz, and the trace of every chip write is read
against the same model, the frames cut at the VBL and the ticks counted
against the rates the trace shows the timers programmed at. So -hatari
takes tunes at 50 Hz.

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
TUNE_VERSION = 2
BOUND_VERSION = 1
# The video address counter's low byte, which the raster monitor waits on,
# and the background it paints.
VIDEO = 0xFFFF8209
PALETTE = 0xFFFF8240
# What the monitor's build paints: the call's work, and the timers' bar.
PERF_FRAME = 0x0700
PERF_BAR = 0x0770

# The memory map: the player, the tune two bytes past a long so nothing
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
C = 30
EFFECT = 14
# What each register takes of a byte written to it: the rest the chip
# ignores, and the player leaves the set bit and the bits beside in.
TAKES = [0xFF, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0x1F, 0xFF, 0x1F, 0x1F, 0x1F, 0xFF, 0xFF, 0x0F]


def taken(writes):
    return [(reg, value & TAKES[reg]) for reg, value in writes]


# What the player is assembled with: the raster monitor's switch, which
# an equate of the source reads as the assembly defines it.
PERF = "-perf" in sys.argv
DEFINED = {"YMXR_PERF": 1 if PERF else 0}


def equate(name, symbols=None):
    """An equate out of the player's source, so the rig reads what the
    player reads. A term that names another equate is read through to its
    figures, one that names a label is the address the assembly gave it,
    and a name the assembly defines takes the value it was defined with.
    A tick's offsets are measured off the handler's own labels, so they
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
# reads. Both offsets are measured off the handler's own labels, so they
# stand once the player is assembled (main).
TICK_SEL = TICK_PTR = 0


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


def convert(ym, work):
    """A tune file out of a YM dump, through the converter; a tune file
    named as it stands."""
    if ym.endswith(".ymxr"):
        return open(ym, "rb").read(), ""
    out = os.path.join(work, "tune.ymxr")
    flags = os.environ.get("YMXR_FLAGS", "").split()
    r = subprocess.run([os.path.join(ROOT, "bin", "ym-to-ymxr"), ym, out] + flags,
                       capture_output=True)
    assert r.returncode == 0, r.stdout.decode() + r.stderr.decode()
    return open(out, "rb").read(), r.stdout.decode().strip()


def bind(file, work):
    """The bound tune of a tune file, through bin/ymxr-bind; None where the
    binder rejects the file, which it says on its own line."""
    path, out = os.path.join(work, "bound.ymxr"), os.path.join(work, "bound.bin")
    open(path, "wb").write(file)
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-bind"), path, out], capture_output=True)
    if r.returncode == 1 and r.stderr.startswith(b"ymxr-bind: "):
        return None
    assert r.returncode == 0, r.stdout.decode() + r.stderr.decode()
    return open(out, "rb").read()


def trace(file, work, calls):
    """What the Java reader reports of a tune file, one entry a call
    (SPEC.md 7), through bin/ymxr-trace."""
    path = os.path.join(work, "traced.ymxr")
    open(path, "wb").write(file)
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-trace"), path, str(calls)],
                       capture_output=True)
    assert r.returncode == 0, r.stdout.decode() + r.stderr.decode()
    return [json.loads(line) for line in r.stdout.decode().splitlines() if line]


def entry(model, writes):
    """The reader's entry for a frame the model just stepped: the writes
    the chip takes, and the effects the row touched."""
    w = {}
    for reg, value in taken(writes):
        # R7's two host bits are no tune's: the reader gives bits 5 to 0
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
        index = [long_at(file, 20 + 4 * i) for i in range(count)]
        end = index[0] if count else len(file)
        image = file[self.image_at:end]
        table_at = long_at(image, 16 + 8)
        dtx = os.path.join(work, "table.dtx")
        csv = os.path.join(work, "table.csv")
        open(dtx, "wb").write(image[table_at:])
        r = subprocess.run([DTX_WRITE, dtx, csv], capture_output=True)
        assert r.returncode == 0, r.stderr.decode()
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
        # target is what the player holds after step 1, and using the
        # target the running effect writes: the one held at its start
        self.fx = [dict(target=0, using=0, source=0, select=0, count=0, place=None,
                        running=False) for _ in range(4)]

    def frame(self):
        """(the chip writes in order, the effects' state after the row)."""
        r = self.tune.rows[self.row]
        self.row += 1
        if self.row == self.tune.R:
            self.row = self.tune.RR
        writes = []
        for i in range(4):
            t = EFFECT + 4 * i
            fx = self.fx[i]
            if r[t] & 0x80:
                fx["target"] = r[t] & 0x7F
            fx["restart"] = False
            fx["reset_place"] = False
            fx["touched"] = bool((r[t] | r[t + 1] | r[t + 2]) & 0x80) or r[t + 3] != 0
            if r[t + 1] & 0x80:
                source = r[t + 1] & 0x7F
                fx["source"] = source
                if source == 0:
                    fx["running"] = False
                    fx["place"] = None
                else:
                    at, R, RR, rows = self.tune.sources[source]
                    fx["place"] = 0
                    fx["using"] = fx["target"]
            if r[t + 2] & 0x80:
                if r[t + 2] & 0x40:
                    fx["restart"] = True
                    fx["running"] = True
                if r[t + 3]:
                    fx["count"] = r[t + 3]
                fx["select"] = r[t + 2] & 7
                if r[t + 2] & 0x20:
                    fx["reset_place"] = True
                    fx["place"] = 0
            elif r[t + 3]:
                fx["count"] = r[t + 3]
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

    def place_address(self, i):
        fx = self.fx[i]
        if fx["place"] is None:
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
        # moved would hold the wait to its ceiling
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
        """The MFP writes of a call, in order."""
        for reg, value in writes:
            if reg in self.ier:
                self.ier[reg] = value
            elif reg in self.imr:
                self.imr[reg] = value
            for i, t in enumerate(TIMER):
                if reg == t["ctrl"]:
                    mode = (value >> t["shift"]) & 0x0F
                    if mode != self.mode[i]:
                        if self.mode[i] == 0 and mode:
                            self.restarts[i] += 1
                            self.phase[i] = self.period(i)
                        self.mode[i] = mode
                elif reg == t["data"]:
                    if self.mode[i] == 0:
                        self.count[i] = value or 256
                        self.pending[i] = None
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
        advanced: the count the real MFP fires, give or take one."""
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
    """The tune on the player, held to the model frame by frame and tick
    by tick; with kit, each frame is held to the reader's entry as well,
    and a tune that plays once to the call that reports its end."""
    work = tempfile.mkdtemp()
    file, report = convert(ym, work)
    workspace = WORK + 0x100
    version = struct.unpack(">H", file[4:6])[0]
    if version != TUNE_VERSION:
        # the binder and the reader reject the file; the player, which never
        # sees it, rejects a bound tune whose own version word is moved
        assert bind(file, work) is None, "the binder took a tune file of version %d" % version
        if kit:
            assert trace(file, work, 1) == [], "the reader reports something of version %d" % version
        bound = bind(file[:4] + struct.pack(">H", TUNE_VERSION) + file[6:], work)
        assert bound is not None, "the file is not a tune of the version it states"
        assert Machine(code, symbols, bound).call("init", a0=FILE, a1=workspace) == 0, \
            "init rejected the bound tune before its version was moved"
        moved = bound[:4] + struct.pack(">H", BOUND_VERSION + 1) + bound[6:]
        m = Machine(code, symbols, moved)
        assert m.call("init", a0=FILE, a1=workspace) & 0xFFFFFFFF == 0xFFFFFFFF, \
            "init took a bound tune of version %d" % (BOUND_VERSION + 1)
        return 0, 0, [], {}, (0, 0, 0, [])
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
    costliest = [0]
    advance = []
    clocks = MFP_CLOCK / tune.rate
    once = tune.RR == tune.R
    frames = tune.R + 1 if once else min(tune.R + tune.R - tune.RR, 4 * tune.R)
    entries = trace(file, work, frames) if kit else None
    if kit:
        assert len(entries) == frames + 1, "the reader gives %d lines, not %d" % (len(entries), frames + 1)
        first = {"rate": tune.rate, "effects": tune.effects,
                 "sources": [{"rows": rows, "repeat": rr} for _, r_, rr, rows in tune.sources[1:]]}
        assert entries[0] == first, "the reader's first line is %s, the tune states %s" % (entries[0], first)
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
            # the call after the last row: the end reported, nothing written
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
        assert taken(m.psg) == taken(want), "frame %d writes %s, not %s" % (f, m.psg, want)
        if kit:
            assert entries[f] == entry(model, want), "frame %d: the reader reports %s, the player %s" % (
                f, entries[f], entry(model, want))
        restarts = list(timers.restarts)
        timers.apply(m.mfp)
        for i in range(4):
            fx = model.fx[i]
            if fx["restart"]:
                assert timers.restarts[i] == restarts[i] + 1, \
                    "frame %d: effect %d's timer was not restarted" % (f, i)
            if fx["running"]:
                assert timers.mode[i] == fx["select"], "frame %d: effect %d runs at select %d, not %d" % (f, i, timers.mode[i], fx["select"])
                assert (timers.pending[i] or timers.count[i]) == fx["count"], \
                    "frame %d: effect %d's count is %d, not %d" % (f, i, timers.pending[i] or timers.count[i], fx["count"])
            elif fx["source"] == 0 and tune.effects & 1 << i:
                assert timers.mode[i] == 0, "frame %d: effect %d's timer runs with no source" % (f, i)
            place = model.place_address(i)
            if place is not None and fx["running"]:
                at = CODE + symbols["ymxr_tick%d" % i]
                assert m.long(at + TICK_PTR) == place, "frame %d: effect %d's place is %x, not %x" % (f, i, m.long(at + TICK_PTR), place)
                assert m.byte(at + TICK_SEL) == fx["using"], "frame %d: effect %d's handler selects R%d, not R%d" % (f, i, m.byte(at + TICK_SEL), fx["using"])
        for i in timers.due(clocks):
            fx = model.fx[i]
            assert fx["running"], "frame %d: a tick of effect %d with nothing running" % (f, i)
            want, then = model.tick(i)
            if cycles:
                cycles._settle(None)
            before = cycles.cycles if cycles else 0
            m.interrupt(TIMER[i]["vector"])
            if cycles:
                cycles._settle(None)
                tick_cost.setdefault(then, set()).add(cycles.cycles - before)
            ticks += 1
            assert taken(m.psg) == taken([want]), "frame %d: tick of effect %d wrote %s, not %s" % (f, i, m.psg, [want])
            timers.apply(m.mfp)
            at = CODE + symbols["ymxr_tick%d" % i]
            if then == "stop":
                assert timers.mode[i] == 0, "frame %d: effect %d ran out and its timer runs on" % (f, i)
            else:
                assert m.long(at + TICK_PTR) == model.place_address(i), "frame %d: after a tick effect %d's place is off" % (f, i)
    m.call("stop", a0=workspace)
    timers.apply(m.mfp)
    for i in range(4):
        if tune.effects & 1 << i:
            assert timers.mode[i] == 0 or i == 3, "stop left effect %d's timer running" % i
    assert m.byte(MFP + 0x1D) & 0x70 == nibble, "stop moved Timer C's nibble"
    return frames, ticks, cost, tick_cost, (costliest[0], tune.R, tune.RR, advance)


WRITE = re.compile(r"ym write data reg=0x([0-9a-f]+) val=0x([0-9a-f]+) .* pc=([0-9a-f]+)")
MFPW = re.compile(r"mfp write \S+ ([0-9a-f]+)=0x([0-9a-f]+) video_cyc=(\d+) .* pc=([0-9a-f]+)")
VBLA = re.compile(r"^VBL=(\d+) clock=(\d+)")
ROM = 0xE00000


def hatari(ym, code, symbols, perf=False):
    """The tune in an SNDH file in a program under Hatari: the frames the
    trace holds, checked against the model, and the ticks counted. code
    and symbols are the SNDH core's, the player's symbols among them; with
    perf the core with the raster monitor in, which the SNDH file takes."""
    work = tempfile.mkdtemp()
    file, report = convert(ym, work)
    bound = bind(file, work)
    assert bound is not None, "the binder rejected the tune"
    tune = Tune(bound, work)
    # the frames are cut at the VBL, which the program plays from where the
    # screen's rate is the tune's: Hatari's ST here refreshes at 50 Hz
    assert tune.rate == 50, "-hatari takes tunes at 50 Hz, and this one plays at %d" % tune.rate
    path = os.path.join(work, "TUNE.YMXR")
    open(path, "wb").write(file)
    sndh = os.path.join(work, "TUNE.SND")
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-sndh"), path, sndh,
                        "-t" + os.path.basename(ym)] + (["-perf"] if perf else []),
                       capture_output=True)
    assert r.returncode == 0, r.stdout.decode() + r.stderr.decode()
    prg = os.path.join(work, "YMXR.PRG")
    r = subprocess.run([os.path.join(ROOT, "bin", "ymxr-prg"), sndh, prg,
                        "-r%d" % STUB_FRAMES], capture_output=True)
    assert r.returncode == 0, r.stdout.decode() + r.stderr.decode()
    sndh_bytes = open(sndh, "rb").read()
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

    def where(pc):
        if frame[0] <= pc < frame[1]:
            return "frame"
        if stop[0] <= pc < stop[1]:
            return "stop"
        for i in range(4):
            if ticks[i][0] <= pc < ticks[i][1]:
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
                # comes after the frame's ticks; the frame's own come before
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
    # The frame procedure runs early in a VBL, its effect steps before its
    # first chip write, and a tick may land anywhere: before the frame,
    # inside its effect steps, or after. So the events go in their own
    # order, the model's frame applied at the first frame write, or at a
    # tick that only reads right in the state the frame leaves.
    for f, (events, clocks, stops) in enumerate(frames[first:first + STUB_FRAMES]):
        writes = []
        want = None
        for kind, reg, value in events:
            if kind == "frame":
                if want is None:
                    want = model.frame()
                writes.append((reg, value))
            elif kind in (0, 1, 2, 3):
                fx = model.fx[kind]
                if want is None and not fx["running"]:
                    want = model.frame()
                    fx = model.fx[kind]
                assert fx["running"], "frame %d: a tick of effect %d with nothing running" % (f, kind)
                at = dict(fx)
                w, then = model.tick(kind)
                if taken([(reg, value)]) != taken([w]) and want is None:
                    model.fx[kind] = at        # the tick came after the frame's effect step
                    want = model.frame()
                    assert model.fx[kind]["running"], "frame %d: a tick of effect %d with nothing running" % (f, kind)
                    w, then = model.tick(kind)
                assert taken([(reg, value)]) == taken([w]), \
                    "frame %d: a tick of effect %d wrote %s, not %s; the frame's events %s; the row %s" % (
                        f, kind, (reg, value), w, events, tune.rows[(model.row - 1) % tune.R])
                counted[kind] += 1
        if want is None:
            want = model.frame()
        assert taken(writes) == taken(want), "frame %d writes %s, not %s" % (f, writes, want)
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
    # The count holds the rates, not the phase: a timer stopped or restarted
    # inside a frame is counted for the frame whole, so five in a hundred
    # are allowed, where a wrong prescaler would be off by half or more.
    # Every tick's value is held to its row above. YMXR_TICKS=1 prints the
    # frames where the two part. The monitor's build costs time, a mark at
    # each end of a handler and the bar burnt in the call, so a real MFP
    # drops ticks of a fast timer: with perf the count is held under the
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
    assert taken(stopped) == [(8, 0), (9, 0), (10, 0)], "the stop wrote %s" % stopped
    return played, sum(counted)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    count = "-cycles" in sys.argv
    real = "-hatari" in sys.argv
    kit = "-kit" in sys.argv
    perf = PERF
    if kit:
        where = os.path.join(ROOT, "doc", "conformance", "tunes")
        tunes = args or sorted(os.path.join(where, f) for f in os.listdir(where)
                               if f.endswith(".ymxr"))
    else:
        tunes = args or sorted(os.path.join(ROOT, "ym", "test", f)
                               for f in os.listdir(os.path.join(ROOT, "ym", "test"))
                               if f.endswith(".ym"))
    code, symbols = assemble(defines=["-dYMXR_PERF=1"] if perf else [])
    global TICK_SEL, TICK_PTR
    TICK_SEL = equate("TICK_SEL", symbols)
    TICK_PTR = equate("TICK_PTR", symbols)
    print("the player: %d bytes%s" % (len(code), ", the raster monitor in" if perf else ""))
    if real:
        code, symbols = assemble("YMXR_sndh.S", defines=["-dYMXR_PERF=1"] if perf else [])
        print("the SNDH core: %d bytes%s" % (len(code),
                                             ", the raster monitor in" if perf else ""))
    cycles_of = None
    if count:
        sys.path.insert(0, os.path.join(DTX_REPO, "68k", "test", "emu"))
        import test_dtx
        with_movep(test_dtx)
        cycles_of = test_dtx
    stale = []
    for ym in tunes:
        if real:
            frames, ticks = hatari(ym, code, symbols, perf)
            print("%-45s %6d frames, %6d ticks on Hatari's MFP" % (os.path.basename(ym), frames, ticks))
            continue
        frames, ticks, cost, tick_cost, where = check(ym, code, symbols,
                                                      cycles_of and CyclesOn(cycles_of), kit, perf)
        line = "%-45s %6d frames, %6d ticks" % (os.path.basename(ym), frames, ticks)
        if kit:
            line += ", the player's frames are the reader's entries"
        if cost and perf:
            line += ", no cost figures: the monitor's own cycles run inside the call"
        if cost and not perf:
            average = int(sum(cost) / len(cost))
            adv = where[3]
            line += ", play %5d cycles on average, %5d at most, at frame %d of R %d RR %d" % (
                average, max(cost), where[0], where[1], where[2])
            line += "; the advance %5d on average, %5d at most, %5d in the costliest frame" % (
                sum(adv) / len(adv), max(adv), adv[where[0]])
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
            for then, name in (("on", "a row written, the place stepped"),
                               ("loop", "the marker, the place to row `RR`"),
                               ("stop", "the marker, the timer stopped")):
                if then in tick_cost:
                    tick = re.search(r"^\| %s \| (\d+) \|$" % re.escape(name),
                                     open(os.path.join(ROOT, "doc", "performance.md")).read(), re.M)
                    if not tick or {int(tick.group(1))} != tick_cost[then]:
                        stale.append("performance.md's tick %s is not %s" % (then, tick_cost[then]))
        print(line)
    assert not stale, "\n".join(sorted(set(stale)))
    print("every tune plays as the specification reads")


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
