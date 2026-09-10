#!/usr/bin/env python3
"""The halves a square wave is made of, out of a Hatari trace.

  hatari --trace psg_write,video_vbl --trace-file trace.txt TUNE.PRG
  python3 ym/halves.py trace.txt

A square wave on a voice is that voice's volume register moving between
a level and 0 at the timer's rate, and the ear hears the length of each
half. A comparison of values, of counts, or of the register at each
frame boundary passes a player that writes the right values at the wrong
times; this reads the times.

For every volume register the trace writes, it reports the writes, the
edges among them, the median half and how many halves stand far from the
median. A player that breaks a square's phase reads high there: the row
that started a square used to write 0 to the voice between two ticks, and
DBA 5 came out at 2.4 per cent against the reference player's 0.2
(SPEC.md 1.9, experiments.md).

The register a digidrum owns moves through a recording's levels and its
halves mean little. The tool marks the registers only a row writes,
which no timer drives, and reports the halves of the rest.
"""
import re
import statistics
import sys

WRITE = re.compile(r"ym write data reg=0x([0-9a-f]+) val=0x([0-9a-f]+) "
                   r"video_cyc=(\d+) .* pc=([0-9a-f]+)")
ROM = 0xE00000
FRAME = 160256                  # a 50 Hz frame in 8 MHz cycles


def writes(path):
    """Every write a program made to R8, R9 and R10: the cycle it stood
    at, the register, the value, and the address it was written from."""
    out = []
    frame = -1
    for line in open(path, errors="replace"):
        if line.startswith("VBL="):
            frame += 1
            continue
        m = WRITE.search(line)
        if not m or frame < 0:
            continue
        pc = int(m.group(4), 16)
        reg = int(m.group(1), 16)
        if pc >= ROM or reg < 8 or reg > 10:
            continue
        out.append((frame * FRAME + int(m.group(3)), reg,
                    int(m.group(2), 16) & 0x1F, pc))
    return out


def halves(all, reg):
    """The times the voice moved between silent and loud, and the spans
    between them."""
    edges = []
    on = None
    for at, r, value, _ in all:
        if r != reg:
            continue
        now = value != 0
        if now != on:
            edges.append(at)
            on = now
    spans = [b - a for a, b in zip(edges, edges[1:]) if b > a]
    return edges, spans


def main(argv):
    if len(argv) != 2:
        print(__doc__.split("\n\n")[1].strip(), file=sys.stderr)
        return 2
    all = writes(argv[1])
    if not all:
        print("no write to a volume register in " + argv[1])
        return 1
    frames = all[-1][0] // FRAME + 1
    print("%d frames" % frames)
    for reg in (8, 9, 10):
        of = [w for w in all if w[1] == reg]
        if not of:
            continue
        places = sorted({w[3] for w in of})
        edges, spans = halves(all, reg)
        # a voice a timer drives receives writes far faster than a frame;
        # one a row writes receives one a frame at most, and its spans are notes
        driven = len(of) > 4 * frames
        head = "R%d: %d writes from %s, %d edges" % (
            reg, len(of), " ".join("%06x" % p for p in places), len(edges))
        if not driven or len(spans) < 8:
            print(head + ", a row's writes" if not driven else head)
            continue
        median = statistics.median(spans)
        short = sum(1 for d in spans if d < median * 0.6)
        long = sum(1 for d in spans if d > median * 1.6)
        print(head + ", the half %d cycles, %d under 60 per cent of it and "
              "%d over 160, %.1f per cent off"
              % (median, short, long, 100.0 * (short + long) / len(spans)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
