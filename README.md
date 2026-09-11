# YMXR

YMXR is a chiptune format for the Atari ST, and one use of a data format
called DTX.

It assumes the name YMX when it is done. Until then,
[YMX](https://github.com/odipar/YMX) plays today, and YMXR replaces it.

**DTX** is a data format: a table of rows and columns, where every value is
one width, 1, 2 or 4 bytes, and the rows repeat at a row the table selects.
It defines no meaning for a column. [DTX](https://github.com/odipar/DTX) is
a separate repository, where the readers of the format live; the player
lives here.

**YMXS** is the tune data structure: rows of registers and effects, the
sources those effects run, and one rate a tune, written down as JSON.
[YMXS](https://github.com/odipar/YMXS) is a separate repository, and every
conversion here passes through it ([doc/ymxs.md](doc/ymxs.md)), so a tune
is read, edited or written without a dump. Its SPEC.md defines what a tune
is and what a player does with a row, and the specification here is the
base it stands on.

**YMXR** defines one encoding of that structure: the meaning of each
column, and the columns a player reads to do what YMXS says a frame does.
One method serves the whole of it: a clock advances a table one row, and a
procedure writes that row to the chips. The tune's clock advances the
tune's table, and a timer advances a separate table at an effect's rate.
How a row is stored or unpacked is outside this repository, and DTX names
no sound chip.

[doc/requirements.md](doc/requirements.md) comes first, and defines what
YMXR has to do before anything else is written.

A tracker or any other tool that makes tune files starts at
[doc/writing.md](doc/writing.md), which fixes the order to read the
specification in and the checks on what comes out.

## What's here

| | |
|---|---|
| `doc/` | the specification, the requirements it is written against, and the rest |
| `src/main/java/org/ymxr/` | the converter: a YM5!/YM6! dump into a tune file, on DTX's Java library |
| `68k/YMXR.S` | the player, with the raster monitor as a build (doc/performance.md) |
| `68k/YMXR_sndh.S`, `68k/YMXR_prg.S` | the SNDH core around the player and the program stub, assembled once and combined with a tune by a tool (doc/BINARIES.md) |
| `68k/test/emu/` | the rig: the player under emulation against a model of the specification |
| `ym/` | the measurements behind the figures, and `ym/test` ten tunes the tests run on |
| `ymx/` | a YMX file converted and played, and `ymx/test` three tunes that have no dump |
| `bin/` | the tools, each reading standard input and writing standard output: the converters, the structure's five filters, the check, the trace, the binder, the multi file's maker and the SNDH and program combiners, run out of a build |
| `go/` | the same thirteen tools in Go, executables a release ships ([tools.md](doc/tools.md)) |

A tune converts and plays like this:

```bash
ym/play.sh tune.ym
```

That converts, combines and plays it under Hatari. Several tunes go into
one file as subtunes the program selects on the keys 1 to 9, and a name
that is not a tune records the run to a WAV instead. Its options reach the
tools it drives, `-perf` among them for the raster monitor. The three are
these:

```bash
bin/ym-to-ymxr < tune.ym > tune.ymxr
bin/ymxr-sndh -t"The title" < tune.ymxr > tune.sndh
bin/ymxr-prg < tune.sndh > TUNE.PRG
```

The same, through the structure and standard input, and the same played:

```bash
bin/ym-to-ymxs < tune.ym | bin/ymxs-to-prg > TUNE.PRG
bin/ym-to-ymxs < tune.ym | ym/play-ymxs.sh
```

The tune file contains the tune's tables and no code; the SNDH file is the
tables bound with DTX's reader behind the player, which any SNDH host
plays, and the program plays the SNDH file on a bare machine
(doc/BINARIES.md). `bin/ymxr-check` replays a dump against the tune it
converts to, and `bin/ymxr-trace` prints what a reader reports of a tune
file (SPEC.md 7), which are the conformance kit's references.

Each tool reports what it read, the flags it accepted and what it produced,
on standard error, along with its progress through a long run; the output
of the tool goes to standard output. `-silent` leaves standard output and
the notes (doc/tools.md).

The shape follows [YMX](https://github.com/odipar/YMX): Java is the
source of truth, and the Go and C# trees are to follow it byte for byte.

## Tests

| what runs | what it checks |
|---|---|
| `mvn test` | the documents against themselves and the house style, every dump converted through the structure to the file the converter writes, every tune under `ym/test` converted, read back and replayed against its dump, the conformance kit converted again and compared byte for byte, and the four cores and the stub assembled and their descriptors, an SNDH file and a program read back |
| `68k/test/emu/test_ymxr.py` | the player on an emulated 68000: every frame's writes, the timers' programming and every tick against the specification's model |
| the same, `-corpus` | the same, over a spread of the corpus rather than the tunes under `ym/test`, which are one of each shape |
| the same, `-hatari` | the SNDH file's program on a real MFP under Hatari, the trace of the player's writes against that model |
| the same, `-kit` | the conformance kit's tunes on the player, each frame against the reader's record |
| the same, `-perf` | the player built with the raster monitor in, against the same model: every frame's writes are the ones the specification defines |
| `ym/parity.py` | one tune packed by YMX and by this repository, both played under Hatari, and every frame's registers read against the other run's |

| | |
|---|---|
| [doc/requirements.md](doc/requirements.md) | what the encoding has to do |
| [doc/SPEC.md](doc/SPEC.md) | the format specification |
| [doc/writing.md](doc/writing.md) | writing a tune file: for a tracker targeting the format |
| [doc/glossary.md](doc/glossary.md) | every term, one line each |
| [doc/terminology.md](doc/terminology.md) | the machine, and the terms for it |
| [doc/tools.md](doc/tools.md) | every tool's usage, flags and environment |
| [doc/performance.md](doc/performance.md) | what a play call costs, in cycles |
| [doc/experiments.md](doc/experiments.md) | ideas measured, and what the measurements said |
| [doc/plan.md](doc/plan.md) | what a call could cost, and what each step is worth |
| [doc/ymxs.md](doc/ymxs.md) | the structure every conversion passes through |
| [doc/BINARIES.md](doc/BINARIES.md) | the prebuilt binaries, and how a tool combines them |
| [doc/RELEASES.md](doc/RELEASES.md) | what changed in each published set |
| [doc/conformance/](doc/conformance) | the kit an independent reader is written against |
