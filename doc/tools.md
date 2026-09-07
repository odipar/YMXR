# tools

Every tool's usage, flags and environment. The Java tree is the source of
truth; the Go and C# trees are to follow it.

## Convert

A YM5!/YM6! register dump into a tune file (SPEC.md 3.3):

```
bin/ym-to-ymxr in.ym out.ymxr [-kK] [-mN] [-rRR | -r]
```

| flag | gives |
|---|---|
| `-kK` | the unit the table packs at, 2 by default, and 1 where the repeat row or the row count does not divide by it |
| `-mN` | the ring a column unpacks through, in bytes, 960 by default and at most 1129: the player reaches column 29 through a 16-bit displacement |
| `-rRR` | the row the tune repeats to. The default is the dump's loop frame, and `-r` alone a tune that plays once |

The first file is unpacked where it is an LHA archive, as distributed
`.ym` files are. The tool prints the frames, the sources, the effects
run, the repeat row and the bytes written, then its notes: effects
dropped, a unit other than the one asked for, and a ring other than the
one asked for.

The table is the dump's frames row for row: the row it repeats to is
the dump's loop frame, its rows are the dump's, and no row is added
anywhere. A table packs at unit 2: unit 1 packs the corpus to 0.69 bytes
a frame against 0.81, and costs the play call about a seventh more on
average (performance.md), and `-k1` asks for it. A column's bytes and
its loop begin on a unit (DTX's R5.6 and R5.11), so a tune whose row
count or repeat row is odd packs at unit 1, which the tool notes. The
table packs at a period of thirty rows, the column count and the
smallest DTX allows, since a refill decodes a period's rows of one
column at once and the period is what a refill costs (performance.md);
the ring is the multiple of thirty nearest the one asked for, at least
sixty and at most 1,110. A loop longer than the ring is replayed at its
exact rows by DTX's reader, at any period.

What the converter does with a dump's effects: a SID voice is a source of two
rows, its level and 0; a sync buzzer one row, its shape; a digidrum the
recording's 4-bit levels and a closing row at mid-scale, which owns the
voice's volume for the frames its rows take at its rate and sets the mixer's
bits for the voice meanwhile. A sinus SID is dropped, as the reference player
runs an empty handler for it. The row the tune repeats to sets every register
but R13 and the ones an effect owns there, and every effect that ran up to it
or runs into the wrap, so the wrap lands on a known state. `ConversionTest`
replays every tune under `ym/test` against its dump.

The tune file holds the tune's tables and no code: BINARIES.md says how a
tool binds them with DTX's reader into what the player takes.

The tool runs out of `target/classes`, and builds first where a source,
the pom or a 68000 source is newer than the last build, or the core is
not assembled. Java 23, Maven and rmac, with which the build assembles
both cores and the stub once (`-Drmac=PATH` names another), and DTX 0.6.0
in the local Maven repository: `mvn install` at DTX's `v0.6.0` tag,
whose reader this player's frame figures (performance.md) are measured
against.

## Check

```
bin/ymxr-check DUMP|DIR ...
```

Every dump named, and every `.ym` under a directory named, converted at
the tool's defaults and replayed against itself: the tune file's table
stepped frame by frame by the reader in `Replay`, every frame's registers
held to the dump's but those an effect owns, and every effect's source,
target, rate and count held to what the dump flags. One line a tune, the
first twenty wrong frames under a tune that fails, and an exit of 1 where
any does; a file that is not a YM5!/YM6! dump is said and not counted.
`ConversionTest` runs the same check on the tunes under `ym/test`, and
the corpus (Measure, `YM_CORPUS`) runs through it whole in under two
minutes: 543 dumps replay to their dumps, and one file is not a dump.
The check steps one pass and the loop once, as a reader's record runs,
and takes the converter's flags: `bin/ymxr-check -r DIR` replays every
dump as a tune that plays once.

## Trace

```
bin/ymxr-trace TUNE [FRAMES]
```

What a reader reports of a tune file (SPEC.md 7), on standard output:
the first line what the tune states once, then one line a frame,
`FRAMES` of them or the count the kit takes of the tune, one pass and
the loop once, or the pass and the frame that reports its end. Of a file of
another version it prints nothing. The conformance kit's references are
what this gives (doc/conformance/README.md).

## The player

`68k/YMXR.S`, assembled with `rmac -m68000 -fr`, is 2,428 bytes. Its
first three longs are the calls:

| call | takes | gives |
|---|---|---|
| `YMXR_init` | `a0` the bound tune (BINARIES.md), on an even address; `a1` the workspace, on a long | `d0` 0, or -1 for one the player does not read |
| `YMXR_play` | `a0` the workspace | `d0` 0, or -1 where the tune has played its last row and does not repeat |
| `YMXR_stop` | `a0` the workspace | the claimed timers stopped, disabled and masked, the three volumes silenced |

Every call clobbers `d0` to `d5` and `a0` to `a5`, and leaves `d6`, `d7`
and `a6` as they were. The workspace is `YMXR_FIXED`, 56 bytes, then the
state block the bound tune states at offset 12 (BINARIES.md). The host
owns the machine: the player saves and restores no vector, timer control
or interrupt enable, and touches no timer the tune does not run. The
header of the source gives the contract in full.

## Bind, SNDH file, program

```
bin/ymxr-bind tune.ymxr tune.bin
bin/ymxr-sndh tune.ymxr [more.ymxr ...] tune.sndh [-tTITLE] [-cCOMPOSER]
              [-nNAME ...] [-perf]
bin/ymxr-prg tune.sndh TUNE.PRG [-rROWS]
```

A tune file holds tables and no code. What the player takes is the bound
tune, the file with DTX's image for its table in place of the table,
which `bin/ymxr-bind` writes; `bin/ymxr-sndh` puts one bound tune or
more behind the SNDH core, the player under SNDH's three entries, into
an SNDH file any SNDH host plays, with the tags the flags give; and
`bin/ymxr-prg` puts the program stub in front of an SNDH file, making a
TOS program that takes the machine over under Supexec, plays the file
from the VBL or Timer C, stops on SPACE or ESC or after `ROWS` rows,
switches subtunes on 1 to 9, and hands the machine back. `-perf` puts
the core with the raster monitor in (Measure), and the program then
clears the screen so that its bars show. The core, that core and the
stub are assembled by the build with rmac, once, into the classpath;
BINARIES.md is the contract for every byte of them, and no assembler
runs at combine time.

## Play

```
ym/play.sh [-kK] [-mN] [-rRR | -r] [-tTITLE] [-cCOMPOSER] [-perf] [-vN]
           tune.ym [out.wav]
```

`ym/play.sh` runs the three tools above and hands the program to Hatari
with its sound on. SPACE stops the tune, and `-vN` stops the run after
`N` frames. Named a second file it records instead: Hatari writes an
AVI, video and sound, which `ym/avi.py` reads back as a WAV, with the
run's last frame beside it as a PNG.

| option | gives |
|---|---|
| `-kK` | the unit the table packs at, 2 by default |
| `-mN` | the ring in bytes, 960 |
| `-rRR`, `-r` | the row the tune repeats to, or a tune that plays once |
| `-tTITLE`, `-cCOMPOSER` | the tags; the title is the file's name by default |
| `-perf` | the core with the raster monitor in, so the recorded frame shows the bars (Measure) |
| `-vN` | the frames to run |

`-k`, `-m` and `-r` are the converter's, and a tune file, which is
packed already, takes none of them. `HATARI` and `TOS` name the emulator
and a TOS image, as they do for the rigs.

## The rigs

`68k/test/emu/test_ymxr.py` plays every tune under `ym/test`, or the
tunes named, on an emulated 68000 and holds the player to a model of
SPEC.md 4 and 5 written from the tune's own tables: every frame's chip
writes in order, the timers' programming against the rate columns, each
handler's place against the source's rows, and every tick's write against
the row its place stands on.

```
python3 68k/test/emu/test_ymxr.py [tune.ym ...]
python3 68k/test/emu/test_ymxr.py -cycles [tunes]
python3 68k/test/emu/test_ymxr.py -hatari [tunes]
python3 68k/test/emu/test_ymxr.py -kit [tune.ymxr ...]
```

A tune named as a `.ymxr` file plays as it stands, without the
converter. A tune whose `RR` is `R` is played one frame past its last
row, where the call reports -1 and writes nothing. `-hatari` takes tunes
at 50 Hz: it cuts the trace into frames at the VBL, which the program
plays from where the screen's rate is the tune's, and Hatari's ST
refreshes at 50 Hz.

Under unicorn, which raises no interrupt, the rig models the four timers and
fires every tick by hand at the time the model gives. `-cycles` counts the
play call, the share of it spent in DTX's advance, and the tick handlers, with
DTX's cycle counter, whose tables this rig adds `movep` to, and fails where
performance.md's figures are not what it counts. `-hatari` puts the tune into
an SNDH file and a program around it through `bin/ymxr-sndh` and
`bin/ymxr-prg`, with 2,000 rows to play, runs it under a cycle-exact Hatari,
and reads the trace of every chip write against the same model, so the ticks
are the MFP's own: the frames are told apart by the VBL, and the ticks counted
against the rates the trace shows the timers programmed at. `ym/cost.sh`
measures the same program's cycles instead, through the raster monitor
(Measure). `-perf` builds the player with that monitor in and holds it to the
model under unicorn, so a band painted or a cost counted changes no register a
frame writes, and none of the order it writes them in. `-kit` plays the
conformance kit's tunes, or the tune files named, and holds each frame the
player makes to the reader's record of it through `bin/ymxr-trace`, and the
record's first line to the tune's header, so the player, the rig's model and
the reader agree line by line. Every tune is bound through `bin/ymxr-bind`
before the player takes it; a tune file of another version is one the binder
and the reader reject, and the player rejects a bound tune whose version is
not its own.

| variable | gives |
|---|---|
| `RMAC` | the assembler, `rmac` on the path by default |
| `DTX_WRITE` | DTX's `dtx-write`, which reads the table back out of the bound tune's image; built from DTX's `go/cmd/dtx-write` |
| `DTX_REPO` | the DTX checkout, `../DTX` by default, for the cycle counter under `68k/test/emu` |
| `HATARI`, `TOS` | the emulator and a TOS image, `hatari` and `~/hatari-2.6.1_macos/tos-2.06.rom` by default |

## Measure

`ym/convert.py` runs the corpus through the specification and prints the
figures experiments.md holds, and `ym/measure.py` the register-level
figures SPEC.md 1.2 and 1.7 give. `YM_CORPUS` names the corpus,
`DTX_WRITE` the DTX writer, `DTX_RING` the ring, `JOBS` how many tunes
convert at once, and `YMX_PAIRS` the tunes with a `.ymx` beside them.

```
ym/cost.sh tune.ymxr [more.ymxr ...]
VBLS=3000 ym/cost.sh tune.ymxr
```

`ym/cost.sh` measures the play call on a cycle-exact machine: it builds
a program with the raster monitor's core for each tune, runs it under
Hatari tracing the writes to the background, and reads every call's
span back through `ym/cost.py`. The monitor is a build of the player
(`68k/YMXR.S`, `YMXR_PERF`): the call paints the background red while
its work runs, each tick handler paints its own colour, and the call
burns a yellow bar for what the ticks it counted cost, after its own
writes, so none of them moves for it. `HATARI`, `TOS` and `VBLS` name
the emulator, a TOS image and the frames to run. performance.md has the
figures and what the method leaves out.
