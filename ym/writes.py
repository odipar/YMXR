#!/usr/bin/env python3
"""Two runs' chip writes, register by register, out of Hatari traces.

  hatari --trace psg_write,video_vbl --trace-file a.txt A.PRG
  hatari --trace psg_write,video_vbl --trace-file b.txt B.PRG
  python3 ym/writes.py a.txt b.txt

The cores of BINARIES.md play one tune through the same player, so what
each writes to the chip is the measure of whether a switch changed the
tune. This reads both traces and reports, for every register, whether
the values come in the same order and where they part.

A run is counted from the frame the player first writes in, since a
program that clears the screen starts a frame later than one that does
not, and 900 frames from the first VBL are then 900 different rows. The
count is FRAMES frames from there, and a register whose sequences agree
up to the shorter one's end is the window's edge, not a difference: the
longer run played one more row.

A tick of another length returns inside the call at another point, so a
square's toggle can land on the other side of a frame's edge. Such a
toggle reads here as one register parting at one write, and the ear
hears one half a fraction longer; a player that writes the wrong values,
or writes them to the wrong register, reads as a register parting early
and staying parted.
"""
import collections
import re
import sys

WRITE = re.compile(r"ym write data reg=0x([0-9a-f]+) val=0x([0-9a-f]+).*pc=([0-9a-f]+)")
ROM = 0xE00000
FRAMES = 900


def writes(path, frames=FRAMES):
    """Every write the program made, the frame it first wrote in, and the
    frames counted: a register to its values in order, and the whole run
    in order."""
    by_frame = collections.defaultdict(list)
    frame = -1
    for line in open(path, errors="replace"):
        if line.startswith("VBL="):
            frame += 1
            continue
        m = WRITE.search(line)
        if not m or frame < 0 or int(m.group(3), 16) >= ROM:
            continue
        by_frame[frame].append((int(m.group(1), 16), int(m.group(2), 16)))
    if not by_frame:
        return -1, {}, []
    first = min(by_frame)
    flat = [w for f in range(first, first + frames) for w in by_frame.get(f, [])]
    per = collections.defaultdict(list)
    for reg, value in flat:
        per[reg].append(value)
    return first, per, flat


def parts(a, b):
    """Where two value sequences part: the index, or None where the
    shorter is the longer's head, which the window's edge cuts."""
    n = min(len(a), len(b))
    for i in range(n):
        if a[i] != b[i]:
            return i
    return None


def main(argv):
    if len(argv) != 3:
        print(__doc__.split("\n\n")[1].strip(), file=sys.stderr)
        return 2
    runs = [writes(path) for path in argv[1:]]
    for path, (first, _, flat) in zip(argv[1:], runs):
        if first < 0:
            print("no write of the program in " + path)
            return 1
        print("%s: %d writes over %d frames from frame %d"
              % (path, len(flat), FRAMES, first))
    (_, one, flat_one), (_, two, flat_two) = runs
    apart = 0
    edge = 0
    for reg in sorted(set(one) | set(two)):
        a, b = one.get(reg, []), two.get(reg, [])
        at = parts(a, b)
        if at is not None:
            apart += 1
            print("  R%-2d parts at write %d of %d and %d" % (reg, at, len(a), len(b)))
        elif len(a) != len(b):
            edge += 1
            print("  R%-2d receives the same %d values, and %d more in the longer run"
                  % (reg, min(len(a), len(b)), abs(len(a) - len(b))))
    if apart == 0:
        moved = sum(1 for x, y in zip(flat_one, flat_two) if x != y)
        print("  every register receives the same values in the same order"
              + (", %d at the window's edge" % edge if edge else "")
              + "; %d writes fall in another order between registers" % moved)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
