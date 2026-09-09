# tools

Every tool's usage, flags and environment. The Java tree is the source of
truth; the Go and C# trees are to follow it.

## What a tool says

A tool reports what it did, and `-silent` leaves it saying what it
wrote and its notes. The report goes to standard error and what the tool
is for goes to standard output, so a run read through a pipe or into a
file reads the same either way and the report stands on the terminal
beside it.

Every tool takes `-silent`, `ym/play.sh` and `ym/cost.sh` among them,
which pass it to the tools they drive.

| the tool says | what it holds |
|---|---|
| what it read | the dump's format, title and composer, its frames, rate and length, its loop frame, and its digidrums |
| the flags it took | each flag's value and whether it was asked for or is the default |
| what it found | the effects that run, and the sources by kind with their rows |
| what it packed | a row a column: the rows in, the bytes out and the two as a percentage |
| what it wrote | the file's bytes, and what the parts of it came to |
| a note | a warning, which stands whether the report is on or off |

A run of many files says how far through them it is on lines of its
own, held apart by a tenth of the run and by a second of the clock: a
run that ends within a second says nothing of its progress, and one of
minutes gives about ten such lines. They are ordinary lines, so a run read
into a file holds them as it holds the rest.

## Convert

A YM5!/YM6! register dump into a tune file (SPEC.md 3.3):

```
bin/ym-to-ymxr in.ym out.ymxr [-kK] [-mN] [-rRR | -r] [-copies[S]] [-silent]
```

| flag | gives |
|---|---|
| `-kK` | the unit the table packs at, 2 by default, and 1 where the repeat row or the row count does not divide by it |
| `-mN` | the ring a column unpacks through, in bytes, 960 by default and at most 1129: the player reaches column 29 through a 16-bit displacement |
| `-rRR` | the row the tune repeats to. The default is the dump's loop frame, and `-r` alone a tune that plays once |
| `-copies[S]` | a match beyond the ring packs as a copy from the column's own literal stream, which packs a small ring far smaller. `-copiesS` searches `S` seconds for a better parse, and a search of some seconds packs another parse every run |

The first file is unpacked where it is an LHA archive, as distributed
`.ym` files are. Standard output is one line: the frames, the sources,
the effects run, the repeat row and the bytes written. The report beside
it holds the dump as it was read, the flags as they were taken, the
sources by kind, a row a column of what it packed to, and the notes:
effects dropped, a unit other than the one asked for, and a ring other
than the one asked for.

The table is the dump's frames row for row: the row it repeats to is
the dump's loop frame, its rows are the dump's, and no row is added
anywhere. A table packs at unit 2: unit 1 packs the corpus to 0.69 bytes
a frame against 0.81, and costs the play call about a seventh more on
average (performance.md), and `-k1` asks for it. A column's bytes and
its loop begin on a unit (DTX's R5.6 and R5.11), so a tune whose row
count or repeat row is odd packs at unit 1, which the tool notes. The
table packs at a period of thirty rows, the column count and the
smallest DTX allows, since a refill decodes a period's rows of one
column at once and a refill costs the period (performance.md);
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
the four cores and the stub once (`-Drmac=PATH` names another), and DTX
0.7.0 in the local Maven repository: `mvn install` at DTX's `v0.7.0` tag,
whose reader this player's frame figures (performance.md) are measured
against. The player names the table to read at that reader's init, and a
set of subtunes shares one image (BINARIES.md 1), neither of which 0.6.0
reads.

## Check

```
bin/ymxr-check [-kK] [-mN] [-rRR | -r] [-copies[S]] [-silent] DUMP|DIR ...
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
bin/ymxr-trace TUNE [FRAMES] [-silent]
```

What a reader reports of a tune file (SPEC.md 7), on standard output:
the first line what the tune states once, then one line a frame,
`FRAMES` of them or the count the kit takes of the tune, one pass and
the loop once, or the pass and the frame that reports its end. Of a file of
another version it prints nothing. The conformance kit's references are
what this gives (doc/conformance/README.md).

## The player

`68k/YMXR.S`, assembled with `rmac -m68000 -fr`, is 2,564 bytes. Its
first three longs are the calls:

| call | takes | gives |
|---|---|---|
| `YMXR_init` | `a0` the bound tune (BINARIES.md), on an even address; `a1` the workspace, on a long | `d0` 0, or -1 for one the player does not read |
| `YMXR_play` | `a0` the workspace | `d0` 0, or -1 where the tune has played its last row and does not repeat |
| `YMXR_stop` | `a0` the workspace | the claimed timers stopped, disabled and masked, the three volumes silenced |

Every call clobbers `d0` to `d5` and `a0` to `a5`, and leaves `d6`, `d7`
and `a6` as they were. The workspace is `YMXR_FIXED`, 60 bytes, then the
state block the bound tune states at offset 12 (BINARIES.md). The host
owns the machine: the player saves and restores no vector, timer control
or interrupt enable, and touches no timer the tune does not run. The
header of the source gives the contract in full.

## Bind, SNDH file, program

```
bin/ymxr-bind tune.ymxr tune.bin [-silent]
bin/ymxr-sndh tune.ymxr [more.ymxr ...] tune.sndh [-tTITLE] [-cCOMPOSER]
              [-nNAME ...] [-perf] [-lean] [-silent]
bin/ymxr-prg tune.sndh TUNE.PRG [-rROWS] [-silent]
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
clears the screen so that its bars show; `-lean` puts the core whose
ticks neither drop the interrupt level nor write their own end of
interrupt, which asks two things of the host (performance.md). The two
are a switch each, and both together put the core that is both, whose
bars are the lean ticks' own. The four cores and the stub are assembled
by the build with rmac, once, into the classpath; BINARIES.md is the
contract for every byte of them, and no assembler runs at combine time.

## Play

```
ym/play.sh [-kK] [-mN] [-rRR | -r] [-copies[S]] [-tTITLE] [-cCOMPOSER]
           [-perf] [-lean] [-vN] [-silent] tune.ym [more.ym ...] [out.wav]
ym/play.sh -h
```

`ym/play.sh` runs the three tools above and hands the program to Hatari
with its sound on. SPACE stops the tune, and `-vN` stops the run after
`N` frames. The first name is a tune; after it a name ending in `.ym` or
`.ymxr`, in either case, is another tune and any other name records the
run instead:
Hatari writes an AVI, video and sound, which `ym/avi.py` reads back as a
WAV, with the run's last frame beside it as a PNG.

Several tunes go into one file, a subtune each in the order named and
each named by its file, which the program picks between on the keys 1 to
9. An SNDH file states one rate, so a set whose tunes do not share one
gets a line on stderr and no file, and a tenth tune and past it play
only under a host that asks for a subtune by number.

| option | gives |
|---|---|
| `-kK` | the unit the table packs at, 2 by default |
| `-mN` | the ring in bytes, 960 |
| `-rRR`, `-r` | the row the tune repeats to, or a tune that plays once |
| `-copies[S]` | a match beyond the ring packs as a copy from the column's own literal stream, which packs a small ring far smaller. `-copiesS` searches `S` seconds for a better parse, and a search of some seconds packs another parse every run |
| `-tTITLE`, `-cCOMPOSER` | the tags; the title is the file's name by default |
| `-perf` | the core with the raster monitor in, so the recorded frame shows the bars (Measure) |
| `-lean` | the core whose ticks neither drop the interrupt level nor write their own end of interrupt (performance.md) |
| `-perf -lean` | the core that is both, so the bars are the lean ticks' own |
| `-vN` | the frames to run |
| `-silent` | the tools it drives say nothing but their notes, since this script takes their standard output |
| `-h` | the options and examples, which the script's own head holds |

`-k`, `-m` and `-r` are the converter's, and a tune file, which is
packed already, takes none of them. A set whose tunes do not share a unit
takes an image a unit and pays for DTX's reader twice, which the report
names; `-k1` on every dump takes one image and packs the tables smaller,
at about a fifth of the play call, and experiments.md has what that came
to on twenty tunes. `HATARI` and `TOS` name the emulator
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
python3 68k/test/emu/test_ymxr.py -corpus [N]
python3 68k/test/emu/test_ymxr.py -cycles [tunes]
python3 68k/test/emu/test_ymxr.py -hatari [tunes]
python3 68k/test/emu/test_ymxr.py -kit [tune.ymxr ...]
python3 68k/test/emu/test_ymxr.py -lean [tunes]
```

The tunes under `ym/test` are chosen for the shapes a tune takes, one of
each, and `-corpus` reads what the corpus holds instead: every Nth file
of it by name, forty tunes unless `-corpusN` gives another count, so a
sample covers the corpus rather than one composer's run of it. Forty is
about eleven minutes. A tune that fails is named and the rest are read,
so one run says every tune that fails and not the first alone.

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
frame writes, and none of the order it writes them in. `-lean` builds it with
the two switches a tick reads, `YMXR_NEST=0` and `YMXR_AEOI=1`
(performance.md), and holds that build to the same model. `-kit` plays the
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

## Against YMX

```
ym/parity.py [-whole] [tune.ym ...]
```

One tune packed both ways and played twice: through YMX's `ymx` and
`mkprg` into a program, and through this repository's three tools into
another. Both run under Hatari with their chip writes traced and cut into
frames at the VBL, and each frame's fourteen registers are read against
the other run's, each masked to what the register takes.

The frames are aligned on the first write to a sound register other than
R7, which TOS writes at boot before a program runs. A pass of the music is
what it compares, the tune's own row count from that frame; past the wrap
a tune starts over at the row each tree read out of the dump, which is the
converters' reading and not a thing a player does. `-whole` reads the run
to its end.

A register an effect drives is sampled at the frame's edge, where a
toggle lands one side or the other and two players that both play the
wave right still part (performance.md). Those partings are counted and
named. A frame differing on a register no effect drives is what fails the
run.

| variable | gives |
|---|---|
| `YMX_REPO` | the YMX checkout, `../YMX` by default |
| `YMX_BIN` | its built Go tools, `$YMX_REPO/go/bin` by default |
| `HATARI`, `TOS`, `VBLS` | the emulator, a TOS image and the frames to run |

## Measure

`ym/convert.py` runs the corpus through the specification and prints the
figures experiments.md holds, and `ym/measure.py` the register-level
figures SPEC.md 1.2 and 1.7 give. `YM_CORPUS` names the corpus,
`DTX_WRITE` the DTX writer, `DTX_RING` the ring, `DTX_COPIES` the copies
flag, `JOBS` how many tunes
convert at once, and `YMX_PAIRS` the tunes with a `.ymx` beside them.

```
ym/cost.sh [-lean] [-silent] tune.ymxr [more.ymxr ...]
VBLS=3000 ym/cost.sh tune.ymxr
```

`ym/cost.sh` measures the play call on a cycle-exact machine: it builds
a program with the raster monitor's core for each tune, runs it under
Hatari tracing the writes to the background, and reads every call's
span back through `ym/cost.py`. The monitor is a build of the player
(`68k/YMXR.S`, `YMXR_PERF`): the call paints the background red while
its work runs, each tick handler paints its own colour, and the call
burns a yellow bar for what the ticks it counted cost, after its own
writes, so none of them moves for it. `-lean` builds the core whose
ticks neither drop the interrupt level nor write their own end of
interrupt, so what comes back is that core's cost against the plain
one's. `HATARI`, `TOS` and `VBLS` name the emulator, a TOS image and the
frames to run. performance.md has the figures and what the method leaves
out.

```
hatari --trace psg_write,video_vbl --trace-file trace.txt TUNE.PRG
python3 ym/halves.py trace.txt
```

`ym/halves.py` measures a square wave: for every volume register a run
writes, the edges its writes make and how far each half stands from the
median. A square is a voice moving between a level and 0 at a timer's
rate, so what the ear hears is the length of each half, and a comparison
of values, of counts, or of the register at each frame boundary passes a
player that writes the right values at the wrong times. Run it on the
player and on another player's trace of the same tune before believing a
square is right; experiments.md, What a square does when it starts, has
what it caught and the figures it read.

```
hatari --trace psg_write,video_vbl --trace-file a.txt A.PRG
python3 ym/writes.py a.txt b.txt
```

`ym/writes.py` holds two runs' chip writes against each other: for every
register, whether the values come in the same order and where they part.
The cores of BINARIES.md play one tune through the same player, so this
is the measure of whether a switch changed the tune. It counts from the
frame the player first writes in, since a program that clears the screen
starts a frame later and 900 frames from the first VBL are then 900
different rows, and a register whose values agree to the shorter run's
end is the window's edge, not a difference. performance.md, A tick, has
what it read off the four cores.
