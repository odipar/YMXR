# YMXR

## Read this first

**AI wrote most of YMXR.** Claude (Anthropic's Claude Code) wrote the two
tool trees, the 68000 player and the two files combined around it, the
tests, the emulation rig and most of what is written here, under Robbert
van Dalen's direction: he requested, read and merged every change.
[LICENSE](LICENSE) is the terms, and its attribution records who did what.
Whether to use software written that way is the reader's decision, and
this section is here so that the decision is informed.

What it is built on is older than it. DTX defines the table and the 68000
readers a tool binds a tune with, and the ST4 compressor beneath DTX
derives from Einar Saukas's ZX1. The YM5 and YM6 register-dump formats are
Arnaud Carré's, and SNDH is the Atari ST scene's shared music container.

## What YMXR is

A chiptune format for the Atari ST, and a player of it. YMXR replaces
[YMX](https://github.com/odipar/YMX). Three repositories stand behind a
tune file, each defining one layer:

**[DTX](https://github.com/odipar/DTX)** is the table format: rows and
columns, every value one width of 1, 2 or 4 bytes, repeating at a row
the table selects. It defines the layout of a column and leaves the
meaning to the format above it, and its readers, the 68000 code a tool
binds a tune with, are there.

**[YMXS](https://github.com/odipar/YMXS)** is the tune data structure:
rows of registers and effects, the sources those effects run, and one
rate a tune, encoded as JSON. Its SPEC.md defines what a tune is and
what a player does with a row and a tick. Every conversion here passes
through it, so a tune is read, edited or written as a structure, from a
dump or from a tracker.

**YMXR**, this repository, encodes that structure as a thirty-column DTX
table and defines the meaning of each column: the fourteen registers of
the YM2149 and four effects, each a source on a target at a timer of the
MC68901. A clock advances a table one row and a procedure writes the row
to the chips: the host's clock advances the tune's table, a timer a
source's, at the effect's rate.

## The documents

| | what it defines | where to begin |
|---|---|---|
| [requirements.md](doc/requirements.md) | what the encoding has to do | a player, first |
| [SPEC.md](doc/SPEC.md) | the format: every column, the frame, the tick, the rules a writer satisfies, the record a reader reports | a player, 1 to 5, with [YMXS's SPEC.md](https://github.com/odipar/YMXS/blob/main/doc/SPEC.md) beside it; a reader, 1 to 3 and 7 |
| [BINARIES.md](doc/BINARIES.md) | the prebuilt binaries, how a tool combines them, and the host's side | a player, after SPEC.md |
| [doc/conformance/](doc/conformance) | the kit an independent reader is written against | a reader, after SPEC.md |
| [writing.md](doc/writing.md) | writing a tune file, for a tracker or a converter | a writer |
| [tools.md](doc/tools.md) | every tool: its flags, its lines, its exits | a tool |
| [ymxs.md](doc/ymxs.md) | the structure every conversion passes through, and the clauses of YMXS this format encodes | |
| [glossary.md](doc/glossary.md) | every term, one line each | |
| [terminology.md](doc/terminology.md) | the machine, and the terms for it | |
| [performance.md](doc/performance.md) | what a play call costs, in cycles | |
| [experiments.md](doc/experiments.md) | ideas measured, and the measurements | |
| [plan.md](doc/plan.md) | what a call could cost, and the cost of each step | |
| [RELEASES.md](doc/RELEASES.md) | what changed in each published set | |

## What is here

| | |
|---|---|
| [`src/main/java/org/ymxr/`](src/main/java/org/ymxr) | the thirteen tools in Java, the reference: the converters, the check, the trace, the binder and the combiners |
| [`go/`](go) | the same thirteen in Go, the executables a release ships |
| [`bin/`](bin) | the thirteen as scripts, each writing standard output; eleven read standard input, `ymxr-multi` and `ymxr-check` the files named ([tools.md](doc/tools.md) 1.1) |
| [`68k/YMXR.S`](68k/YMXR.S) | the player; `YMXR_PERF` builds the raster monitor in |
| [`68k/YMXR_sndh.S`](68k/YMXR_sndh.S), [`68k/YMXR_prg.S`](68k/YMXR_prg.S) | the SNDH core around the player and the program stub, assembled once and combined with a tune by a tool |
| [`ym/`](ym) | the measurement and play scripts ([tools.md](doc/tools.md) 19), and [`ym/test`](ym/test), ten dumps the tests run on |
| [`ymx/`](ymx) | a YMX file converted and played, and [`ymx/test`](ymx/test), four tunes read from YMX files alone |
| [`release/`](release) | the scripts that build and list a release |

## Converting and playing

```bash
bin/ym-to-ymxr < tune.ym > tune.ymxr
bin/ymxr-sndh -t"The title" < tune.ymxr > tune.sndh
bin/ymxr-prg < tune.sndh > TUNE.PRG

bin/ym-to-ymxs < tune.ym | bin/ymxs-to-prg > TUNE.PRG
bin/ymxr-multi one.ymxr two.ymxr | bin/ymxr-sndh | bin/ymxr-prg > SET.PRG
bin/ymxr-trace -r4 < tune.ymxr
ym/play.sh tune.ym
```

The tune file has the tune's tables; the SNDH file is those tables bound
with DTX's reader and combined with the player, which any SNDH host
plays; the program plays the SNDH file on a bare machine. `ym/play.sh`
runs the three tools and Hatari; `bin/ymxr-check` replays a dump against
the tune it converts to, and `bin/ymxr-trace` prints the record a reader
reports of a tune file ([SPEC.md](doc/SPEC.md) 7), the conformance kit's
reference. Each tool reports on standard error and writes its output on
standard output; `-silent` omits the report and the summary line and
keeps the output and the notes ([tools.md](doc/tools.md) 3.3).

## Building and testing

Java 23, Maven and rmac, with DTX and YMXS installed in the local Maven
repository by `mvn install` in each checkout. The Go tree builds with
`go build ./cmd/...` under [`go/`](go) and fetches its modules
([tools.md](doc/tools.md) 20).

| what runs | what it reads |
|---|---|
| `mvn test` | the documents against themselves and the house style; every dump under [`ym/test`](ym/test) converted through the structure to the file the converter writes, read back and replayed against its dump; the conformance kit made again and compared byte for byte; the four cores and the stub assembled and their descriptors, an SNDH file and a program read back; the two trees against each other |
| [`68k/test/emu/test_ymxr.py`](68k/test/emu/test_ymxr.py) | the player on an emulated 68000: every frame's writes, the timers' programming and every tick against the specification's model; `-corpus`, `-hatari`, `-kit`, `-perf` and `-lean` select what it plays and which build ([tools.md](doc/tools.md) 18) |
| [`ym/parity.py`](ym/parity.py) | one tune packed by YMX and by this repository, both played under Hatari, each frame's registers read against the other run's |
