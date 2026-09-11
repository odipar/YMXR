#!/usr/bin/env python3
"""The two players' output compared, one tune packed both ways.

A tune goes through YMX's tools into a program and through this
repository's into another, both run under Hatari, and the chip writes of
each are cut into frames at the VBL. What the frame procedure writes must
read alike on both; a register an effect drives is sampled at the frame's
edge, where a toggle lands one side or the other, so those are counted
and not compared.

  ym/parity.py [-whole] [tune.ym ...]

A pass of the music is what this compares: the frames from the first the
music writes to the tune's last row. Past that a tune starts over, and
which row it starts at is each tree's reading of the dump's loop
frame rather than a thing a player does, so `-whole` reads the run out to
its end and the default stops at the wrap.

YMX_REPO names the YMX checkout, `../YMX` by default, and YMX_BIN its
built Go tools, `$YMX_REPO/go/bin`. HATARI, TOS and VBLS are the rig's
(68k/test/emu/test_ymxr.py).
"""

import contextlib
import json
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
YMX_REPO = os.environ.get("YMX_REPO", os.path.join(os.path.dirname(ROOT), "YMX"))
YMX_BIN = os.environ.get("YMX_BIN", os.path.join(YMX_REPO, "go", "bin"))
HATARI = os.environ.get("HATARI", "hatari")
TOS = os.environ.get("TOS", os.path.expanduser("~/hatari-2.6.1_macos/tos-2.06.rom"))
VBLS = int(os.environ.get("VBLS", "700"))
WHOLE = "-whole" in sys.argv
CORPUS = os.environ.get("YM_CORPUS", os.path.expanduser("~/git/jatari/data/ym_format"))

# The tunes under ym/test are one of each shape, which the rig
# requires of them. A comparison against another player needs the shapes an
# ST tune is driven with besides: these come from the corpus, six tunes
# whose effects are square waves on a volume register and five whose
# sources are recordings played once (experiments.md counts both).
SID = ["Sid Music #1", "Sid Music #2", "Synergy Odyssey",
       "Synergy Wicked Polygons 1", "DBA 4", "A Prehistoric Tale 16 - intro"]
SAMPLES = ["Chambers of Shaolin - Mega Pock Olipse", "Lethal Xcess 3 - level 2",
           "Ooh Crikey - main menu", "Turrican 2 - world 1-1 The Desert rocks",
           "Seven Gates of Jambala  - level 11 digidrums"]

# The bits each register reads of the byte written to it, so that a value the
# chip drops is not a difference (68k/test/emu/test_ymxr.py, MASK).
MASK = [0xFF, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0x1F, 0xFF, 0x1F, 0x1F, 0x1F, 0xFF, 0xFF, 0x0F]

WRITE = re.compile(r"ym write data reg=0x([0-9a-f]+) val=0x([0-9a-f]+)")
VBL = re.compile(r"^VBL=(\d+)")


def run(args, where=None, stdin=None, out=None):
    """One tool, which reads standard input and writes standard output;
    `stdin` names the file it reads and `out` the file its output goes
    to."""
    with open(stdin, "rb") if stdin else contextlib.nullcontext() as source:
        r = subprocess.run(args, cwd=where, stdin=source, capture_output=True)
    if r.returncode:
        raise SystemExit(" ".join(args) + " failed:\n" + r.stderr.decode()[:400])
    if out:
        open(out, "wb").write(r.stdout)
    return r


def frames(path):
    """Every frame's chip state, and the frame the music starts at: the
    first write to a sound register other than R7, which TOS writes at
    boot before any program runs."""
    state = [0] * 14
    out = []
    first = None
    for line in open(path, errors="replace"):
        if VBL.match(line):
            out.append(tuple(state))
            continue
        m = WRITE.search(line)
        if m:
            reg, value = int(m.group(1), 16), int(m.group(2), 16)
            if reg <= 13:
                if first is None and reg != 7:
                    first = len(out)
                state[reg] = value & MASK[reg]
    return out, first


def rows(tune):
    """The tune's row count, out of its table's header (SPEC.md 3.3,
    and DTX SPEC.md 1: `R` at bytes 4 to 7 of the table)."""
    file = open(tune, "rb").read()
    at = int.from_bytes(file[12:16], "big")
    return int.from_bytes(file[at + 4:at + 8], "big")


def driven(tune):
    """The registers an effect of the tune ever runs on, read out of what
    a reader reports of it (SPEC.md 7). Those are the registers a timer
    writes between frames."""
    r = run([os.path.join(ROOT, "bin", "ymxr-trace"), "-silent"], stdin=tune)
    on = set()
    for line in r.stdout.decode().splitlines():
        if not line.startswith('{"result"'):
            continue
        for effect in json.loads(line).get("e", {}).values():
            if effect.get("source"):
                on.add(effect["target"])
    return on


def trace(prg, work, name):
    """The program run under Hatari, its chip and VBL writes kept."""
    at = os.path.join(work, name)
    os.makedirs(at, exist_ok=True)
    run(["cp", prg, at])
    out = os.path.join(work, name + ".txt")
    subprocess.run([HATARI, "--tos", TOS, "--machine", "st", "--cpuclock", "8",
                    "--cpu-exact", "on", "--compatible", "on", "--memsize", "4",
                    "--sound", "off", "--conout", "2", "--fast-forward", "on",
                    "--disable-video", "1", "--run-vbls", str(VBLS),
                    "--log-level", "fatal", "--trace", "psg_write,video_vbl",
                    "--trace-file", out, os.path.basename(prg)],
                   cwd=at, capture_output=True)
    return out


def both(ym, work):
    """One tune through each tree, into two programs.

    A .ym is packed by each tree from the dump. A .ymx is a tune that has
    no dump: YMX plays the file itself and this tree plays what
    ymx-to-ymxr makes of it, which is what moving a library across
    comes to."""
    stem = os.path.basename(ym).rsplit(".", 1)[0]
    ymx = os.path.join(work, "t.ymx")
    if ym.lower().endswith(".ymx"):
        run(["cp", ym, ymx])
    else:
        run([os.path.join(YMX_BIN, "ymx"), "-f", ym, ymx])
    theirs = os.path.join(work, "THEIRS.PRG")
    # mkprg finds YMX's prebuilt cores through YMX_REPO
    made = subprocess.run([os.path.join(YMX_BIN, "mkprg"), theirs, ymx],
                          capture_output=True,
                          env=dict(os.environ, YMX_REPO=YMX_REPO))
    if made.returncode:
        raise SystemExit("mkprg failed:\n" + made.stderr.decode()[:400])
    tune = os.path.join(work, "t.ymxr")
    run([os.path.join(ROOT, "bin", "ymx-to-ymxr" if ym.lower().endswith(".ymx")
                      else "ym-to-ymxr"), "-silent"],
        stdin=ymx if ym.lower().endswith(".ymx") else ym, out=tune)
    sndh = os.path.join(work, "t.snd")
    run([os.path.join(ROOT, "bin", "ymxr-sndh"), "-t" + stem, "-silent"],
        stdin=tune, out=sndh)
    ours = os.path.join(work, "OURS.PRG")
    run([os.path.join(ROOT, "bin", "ymxr-prg"), "-silent"], stdin=sndh, out=ours)
    return tune, theirs, ours


def compare(ym):
    """One tune's two runs, read against each other."""
    work = os.environ.get("PARITY_KEEP") and os.path.join(
        os.environ["PARITY_KEEP"], os.path.basename(ym)) or tempfile.mkdtemp()
    os.makedirs(work, exist_ok=True)
    tune, theirs, ours = both(ym, work)
    on = driven(tune)
    a, first_a = frames(trace(theirs, work, "ymx"))
    b, first_b = frames(trace(ours, work, "ymxr"))
    if first_a is None or first_b is None:
        return os.path.basename(ym), None, "one of the two wrote no sound register"
    off = first_b - first_a
    last = len(a) if WHOLE else min(len(a), first_a + rows(tune))
    same = read = parted = 0
    # the registers a frame parted on, which the run
    # names: the set an effect drives is what it is judged against
    where = set()
    for i in range(last):
        j = i + off
        if not 0 <= j < len(b):
            continue
        read += 1
        if a[i] == b[j]:
            same += 1
            continue
        differ = {r for r in range(14) if a[i][r] != b[j][r]}
        where |= differ
        if differ <= on:
            parted += 1
    plain = sorted(where - on)
    return os.path.basename(ym), (read, same, parted, plain, sorted(where & on)), None


def main():
    tunes = [a for a in sys.argv[1:] if not a.startswith("-")]
    if not tunes:
        tunes = sorted(os.path.join(ROOT, "ym", "test", f)
                       for f in os.listdir(os.path.join(ROOT, "ym", "test"))
                       if f.endswith(".ym"))
        # the tunes that have no dump: YMX plays the file and this tree
        # plays what the converter makes of it, which is the migration
        at = os.path.join(ROOT, "ymx", "test")
        if os.path.isdir(at):
            tunes += sorted(os.path.join(at, f) for f in os.listdir(at)
                            if f.lower().endswith(".ymx"))
        missing = []
        for named in SID + SAMPLES:
            at = os.path.join(CORPUS, named + ".ym")
            (tunes if os.path.exists(at) else missing).append(at)
        if missing:
            print("%d corpus tunes are not under %s, so the shapes they carry"
                  " go unread" % (len(missing), CORPUS))
    if not os.path.isdir(YMX_BIN):
        raise SystemExit("no YMX tools at " + YMX_BIN + ": YMX_BIN names them")
    wrong = []
    for ym in tunes:
        name, got, why = compare(ym)
        if why:
            print("%-40s %s" % (name[:40], why))
            wrong.append(name)
            continue
        read, same, parted, plain, parting = got
        line = "%-40s %4d/%4d frames alike" % (name[:40], same, read)
        if parted:
            line += ", %d parted on R%s" % (
                parted, ",R".join(str(r) for r in parting))
        if plain:
            line += ", and %d frames differ on R%s no effect drives" % (
                read - same - parted, ",R".join(str(r) for r in plain))
            wrong.append(name)
        print(line)
    if wrong:
        raise SystemExit("%d of %d tunes differ outside their effects: %s"
                         % (len(wrong), len(tunes), ", ".join(wrong)))
    print("%d tunes read alike outside the registers their effects drive" % len(tunes))


if __name__ == "__main__":
    main()
