#!/usr/bin/env python3
"""The play call's cost, out of a raster monitor run's palette writes.

A player with the monitor in (68k/YMXR.S, YMXR_PERF) paints the
background red while a call's work runs and yellow while it burns the
timers' counted cost, and each tick handler paints its own colour and
puts back what stood before it. This reads a Hatari trace of the writes
to the background back: the red mark to the yellow one is the call's own
work, a tick's colour and the write that puts the red back are a tick
inside it, and the yellow to the write that puts the desktop's colour
back is the bar. doc/performance.md carries the figures and the method.

    ym/cost.py trace.txt
"""
import re
import sys

FRAME = 160256                  # a PAL frame of an ST, in cycles
LINE = 512                      # a scanline
WORK = "700"                    # the call's work
BAR = "770"                     # the timers' bar
TICKS = ("070", "007", "707", "077")
WRITE = re.compile(r"write col addr=ff8240 col=(\w+) video_cyc_w=(\d+).*pc=([0-9a-f]+)")
ROM = 0xE00000


def read(path):
    """Every palette write the program made, in order: its colour and the
    cycle it landed on. A write from the operating system's own code is
    not the monitor's and is left out."""
    out = []
    for line in open(path, errors="replace"):
        m = WRITE.search(line)
        if m and int(m.group(3), 16) < ROM:
            out.append((m.group(1).upper().lstrip("0") or "0", int(m.group(2))))
    return out


def span(at, to):
    """The cycles between two marks, a frame's wrap taken."""
    return to - at + (FRAME if to < at else 0)


def spans(writes):
    """(the calls' work, the ticks, the bars), each a list of cycles."""
    work, ticks, bars = [], [], []
    at = 0
    while at < len(writes):
        colour, opened = writes[at]
        if colour != WORK:
            at += 1
            continue
        inside = 0
        at += 1
        while at < len(writes) and writes[at][0] != BAR:
            if writes[at][0] in TICKS and at + 1 < len(writes):
                took = span(writes[at][1], writes[at + 1][1])
                inside += took
                ticks.append(took)
                at += 2
                continue
            at += 1
        if at == len(writes):
            break
        whole = span(opened, writes[at][1])
        if whole < FRAME // 2:  # a span over a stop is no call
            work.append(whole - inside)
            burnt = at + 1
            while burnt < len(writes) and writes[burnt][0] in TICKS:
                burnt += 1
            if burnt < len(writes):
                bars.append(span(writes[at][1], writes[burnt][1]))
        at += 1
    return work, ticks, bars


def main():
    if len(sys.argv) != 2:
        print(__doc__.strip())
        return 2
    work, ticks, bars = spans(read(sys.argv[1]))
    if not work:
        print("the trace holds no call")
        return 1
    work.sort()
    n = len(work)
    line = "calls %d, %d cycles on average, %d at the 99th in a hundred, %d at most (%.2f lines)" % (
        n, sum(work) / n, work[int(n * 0.99)], work[-1], work[-1] / LINE)
    if ticks:
        line += "; ticks %.2f a call, %d cycles each on average" % (
            len(ticks) / n, sum(ticks) / len(ticks))
    if bars:
        line += "; the bar %.2f lines on average, %.2f at most" % (
            sum(bars) / len(bars) / LINE, max(bars) / LINE)
    print(line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
