# YMXR

YMXR is a chiptune format for the Atari ST: one encoding of the YMXS
tune data structure in a DTX table, and a player of it. YMXR replaces
[YMX](https://github.com/odipar/YMX).

**DTX** is a data format: a table of rows and columns, every value one
width of 1, 2 or 4 bytes, repeating at a row the table selects. It
defines the layout of a column and leaves its meaning to the format
using it.
[DTX](https://github.com/odipar/DTX) is a separate repository, and its
readers, the 68000 code a tool binds a tune with, are there.

**YMXS** is the tune data structure: rows of registers and effects, the
sources those effects run, and one rate a tune, encoded as JSON. Its
SPEC.md defines what a tune is and what a player does with a row and a
tick. [YMXS](https://github.com/odipar/YMXS) is a separate repository;
every conversion here passes through it ([doc/ymxs.md](doc/ymxs.md)), so
a tune is read, edited or written as a structure, from a dump or from a
tracker.

**YMXR** defines the meaning of each column of a thirty-column DTX
table: the fourteen registers of the YM2149 and four effects, each a
source on a target at a timer of the MC68901, and the columns a player
reads to perform a frame. A clock advances a table one row and a
procedure writes the row to the chips: the host's clock advances the
tune's table, a timer a source's, at the effect's rate.

## Reading order

| for | read |
|---|---|
| a player | [doc/requirements.md](doc/requirements.md); [doc/SPEC.md](doc/SPEC.md) sections 1 to 5 with YMXS's SPEC.md beside it; [doc/BINARIES.md](doc/BINARIES.md) for the layout a player is handed and the host's side |
| a reader | SPEC.md sections 1 to 3 and 7, then the kit under [doc/conformance/](doc/conformance) |
| a writer | [doc/writing.md](doc/writing.md), which fixes the order |
| the tools | [doc/tools.md](doc/tools.md) |

## What is here

| | |
|---|---|
| `doc/` | the specification, the requirements it is written against, and the documents below |
| `src/main/java/org/ymxr/` | the thirteen tools in Java, the reference: the converters, the check, the trace, the binder and the combiners |
| `go/` | the same thirteen in Go, the executables a release ships |
| `68k/YMXR.S` | the player; `YMXR_PERF` builds the raster monitor in (doc/performance.md) |
| `68k/YMXR_sndh.S`, `68k/YMXR_prg.S` | the SNDH core around the player and the program stub, assembled once and combined with a tune by a tool (doc/BINARIES.md) |
| `68k/test/emu/` | the rig: the player under emulation against a model of the specification |
| `bin/` | the thirteen tools as scripts, each writing standard output; eleven read standard input, `ymxr-multi` and `ymxr-check` the files named (doc/tools.md 1.1) |
| `ym/` | the measurement scripts (doc/tools.md 19), the play scripts, and `ym/test`, ten dumps the tests run on |
| `ymx/` | a YMX file converted and played, and `ymx/test`, four tunes read from YMX files alone |
| `release/` | the scripts that build and list a release |

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

The tune file has the tune's tables; the SNDH file is those tables
bound with DTX's reader and combined with the player, which any SNDH
host plays; the program plays the SNDH file on a bare machine
(doc/BINARIES.md). `ym/play.sh` runs the three tools and Hatari;
`bin/ymxr-check` replays a dump against the tune it converts to, and
`bin/ymxr-trace` prints the record a reader reports of a tune file
(SPEC.md 7), the conformance kit's reference. Each tool reports on
standard error and writes its output on standard output; `-silent`
omits the report and the summary line and keeps the output and the
notes (doc/tools.md 3.3).

## Building

Java 23, Maven and rmac, with DTX and YMXS installed in the local Maven
repository by `mvn install` in each checkout; `mvn test` builds the
tools, assembles the four cores and the stub, and runs the tests. The Go
tree builds with `go build ./cmd/...` under `go/` and fetches its
modules (doc/tools.md 20).

## Tests

| what runs | what it checks |
|---|---|
| `mvn test` | the documents against themselves and the house style; every dump under `ym/test` converted through the structure to the file the converter writes, read back and replayed against its dump; the conformance kit made again and compared byte for byte; the four cores and the stub assembled and their descriptors, an SNDH file and a program read back; the two trees against each other |
| `68k/test/emu/test_ymxr.py` | the player on an emulated 68000: every frame's writes, the timers' programming and every tick against the specification's model; `-corpus`, `-hatari`, `-kit`, `-perf` and `-lean` select what it plays and which build (doc/tools.md 18) |
| `ym/parity.py` | one tune packed by YMX and by this repository, both played under Hatari, each frame's registers read against the other run's |

## The documents

| | |
|---|---|
| [doc/requirements.md](doc/requirements.md) | what the encoding has to do |
| [doc/SPEC.md](doc/SPEC.md) | the format specification |
| [doc/writing.md](doc/writing.md) | writing a tune file, for a tracker or a converter |
| [doc/glossary.md](doc/glossary.md) | every term, one line each |
| [doc/terminology.md](doc/terminology.md) | the machine, and the terms for it |
| [doc/tools.md](doc/tools.md) | every tool: its flags, its lines, its exits |
| [doc/ymxs.md](doc/ymxs.md) | the structure every conversion passes through, and the clauses of YMXS this format encodes |
| [doc/BINARIES.md](doc/BINARIES.md) | the prebuilt binaries, and how a tool combines them |
| [doc/performance.md](doc/performance.md) | what a play call costs, in cycles |
| [doc/experiments.md](doc/experiments.md) | ideas measured, and the measurements |
| [doc/plan.md](doc/plan.md) | what a call could cost, and the cost of each step |
| [doc/RELEASES.md](doc/RELEASES.md) | what changed in each published set |
| [doc/conformance/](doc/conformance) | the kit an independent reader is written against |
