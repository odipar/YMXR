# YMXR

YMXR is a chiptune format for the Atari ST, and one use of a data format
called DTX.

It takes the name YMX when it is done. Until then,
[YMX](https://github.com/odipar/YMX) is what plays today, and YMXR is what
replaces it.

**DTX** is a data format: a table of rows and columns, where every value
takes one width, 1, 2 or 4 bytes, and the rows repeat at a row of the
table's choosing. It says nothing about what a column holds.
[DTX](https://github.com/odipar/DTX) is a repository of its own, and holds
the readers of the format as this one holds a player.

**YMXR** says what a column holds, and what a player does with it. One
method serves the whole of that: a clock advances a table one row, and a
procedure writes that row to the chips. The tune's clock advances the tune's
table, and a timer advances a table of its own at an effect's rate.
Nothing here says how a row is stored or unpacked, and nothing in DTX knows
a sound chip exists.

[doc/requirements.md](doc/requirements.md) comes first. Nothing else is
written until it says what YMXR has to do.

## What's here

| | |
|---|---|
| `doc/` | the specification, the requirements it is written against, and the rest |
| `src/main/java/org/ymxr/` | the converter: a YM5!/YM6! dump into a tune file, on DTX's Java library |
| `68k/YMXR.S` | the player, with the raster monitor as a build (doc/performance.md) |
| `68k/YMXR_sndh.S`, `68k/YMXR_prg.S` | the SNDH core around the player and the program stub, assembled once and combined with a tune by a tool (doc/BINARIES.md) |
| `68k/test/emu/` | the rig: the player under emulation against a model of the specification |
| `ym/` | the measurements behind the figures, and `ym/test` ten tunes the tests run on |
| `bin/` | the converter, the check, the trace, the binder and the SNDH and program combiners, run out of a build |

A tune converts and plays like this:

```bash
ym/play.sh tune.ym
```

That converts, combines and plays it under Hatari; named a second file it
records the run to a WAV instead, and its options reach the tools it
drives, `-perf` among them for the raster monitor. The three are these:

```bash
bin/ym-to-ymxr tune.ym tune.ymxr
bin/ymxr-sndh tune.ymxr tune.sndh -t"The title"
bin/ymxr-prg tune.sndh TUNE.PRG
```

The tune file holds the tune's tables and no code; the SNDH file is the
tables bound with DTX's reader behind the player, which any SNDH host
plays, and the program plays the SNDH file on a bare machine
(doc/BINARIES.md). `bin/ymxr-check` replays a dump against the tune it
converts to, and `bin/ymxr-trace` prints what a reader reports of a tune
file (SPEC.md 7), which the conformance kit's references are.

Each tool reports what it read, the flags it took and what it made, on
standard error, and how far through a long run it is; what the tool is
for goes to standard output. `-silent` leaves standard output and the
notes (doc/tools.md).

The shape follows [YMX](https://github.com/odipar/YMX): Java is the
source of truth, and the Go and C# trees are to follow it byte for byte.

## Tests

| what runs | what it checks |
|---|---|
| `mvn test` | the documents against themselves and the house style, every tune under `ym/test` converted, read back and replayed against its dump, the conformance kit converted again and compared byte for byte, and the four cores and the stub assembled and their descriptors, an SNDH file and a program read back |
| `68k/test/emu/test_ymxr.py` | the player on an emulated 68000: every frame's writes, the timers' programming and every tick against the specification's model |
| the same, `-hatari` | the SNDH file's program on a real MFP under Hatari, the trace of the player's writes against that model |
| the same, `-kit` | the conformance kit's tunes on the player, each frame held to the reader's record |
| the same, `-perf` | the player built with the raster monitor in, held to the same model: every frame's writes are the ones the specification gives |

| | |
|---|---|
| [doc/requirements.md](doc/requirements.md) | what the encoding has to do |
| [doc/SPEC.md](doc/SPEC.md) | the format specification |
| [doc/glossary.md](doc/glossary.md) | every term, one line each |
| [doc/terminology.md](doc/terminology.md) | the machine, and the terms for it |
| [doc/tools.md](doc/tools.md) | every tool's usage, flags and environment |
| [doc/performance.md](doc/performance.md) | what a play call costs, in cycles |
| [doc/experiments.md](doc/experiments.md) | ideas measured, and what the measurements said |
| [doc/plan.md](doc/plan.md) | what a call could cost, and what each step is worth |
| [doc/BINARIES.md](doc/BINARIES.md) | the prebuilt binaries, and how a tool combines them |
| [doc/RELEASES.md](doc/RELEASES.md) | what changed in each published set |
| [doc/conformance/](doc/conformance) | the kit an independent reader is written against |
