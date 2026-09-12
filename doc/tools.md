# tools

Every tool's usage, flags and environment.

## The two trees

The tools are written twice: in Java under `src/`, and in Go under `go/`.
Java is the reference, and `ParityTest` runs the two against each other on
the dumps under `ym/test`, so one input has one output in both.

A Java tool is a shell script naming a class, run through `bin/run`, which
builds where a source, the pom or a 68000 source is newer than the last
build. A Go tool is an executable and runs as it stands: it contains the
five 68000 binaries and DTX's twenty-two images, so it needs neither this
repository nor a Java runtime beside it.

```bash
cd go && go build ./cmd/...           # the thirteen, for this machine
release/publish.sh                    # win, osx and linux, x64 and arm64
```

DTX, YMXS and YMX are module dependencies of the Go tree:
`github.com/odipar/dtx/go` and `github.com/odipar/ymxs/go` at the releases
the pom names for their Java artifacts, and `github.com/odipar/ymx/go` for
the `.ymx` reader, which the pom names no artifact for. A build fetches
all three, so the Go tree builds without the three checkouts beside it.
This tree is a separate module, `github.com/odipar/ymxr/go`, and a
version of it is a tag of that directory: `go/v0.1.0` beside `v0.1.0`.

A `.ymx` is where the two trees differ in what they need installed. A Go
tool decodes the file with YMX's reader, which it contains. A Java tool
runs YMX's `ymx-dump` and reads the values it prints: `YMX_DUMP` names
that program, and the Java tools are its only callers here.

## What a tool reports

A tool reports what it did, and `-silent` reduces that to what it wrote
and its notes. The report goes to standard error and the output of the tool
to standard output, so a run read through a pipe or into a file is the same
either way, with the report on the terminal beside it.

Every tool accepts `-silent`, `ym/play.sh` and `ym/cost.sh` among them,
which pass it to the tools they drive.

| the report | what it covers |
|---|---|
| what it read | the dump's format, title and composer, its frames, rate and length, its loop frame, and its digidrums |
| the flags it read | each flag's value and whether it was passed or is the default |
| what it found | the effects that run, and the sources by kind with their rows |
| what it packed | a row a column: the rows in, the bytes out and the two as a percentage |
| what it wrote | the file's bytes, and what the parts of it came to |
| a note | a warning, which stands whether the report is on or off |

A run of many files reports its progress on separate lines, spaced by a
tenth of the run and by a second of the clock: a run that ends within a
second reports no progress, and one of minutes produces about ten such
lines. They are ordinary lines, so a run read into a file contains them
with the rest.

Every script in `bin/` is one line through `bin/run`, which builds where a
source, the pom or a 68000 source is newer than the last build, or a core
is not assembled, and then runs the class it is handed. All tool behaviour
is Java.

## Convert

A YM5!/YM6! register dump into a tune file (SPEC.md 3.3):

```
bin/ym-to-ymxr [-kK] [-mN] [-rRR | -r] [-copies[S]] [-silent]
               < in.ym > out.ymxr
```

| flag | what it sets |
|---|---|
| `-kK` | the unit the table packs at, 2 by default, and 1 where the repeat row or the row count does not divide by it |
| `-mN` | the ring a column unpacks through, in bytes, 960 by default and at most 1129: the player reaches column 29 through a 16-bit displacement |
| `-rRR` | the row the tune repeats to. The default is the dump's loop frame, and `-r` alone a tune that plays once |
| `-copies[S]` | a match beyond the ring packs as a copy from the column's separate literal stream, which packs a small ring far smaller. `-copiesS` searches `S` seconds for a better parse, and a search of some seconds packs another parse every run |

The dump is unpacked where it is an LHA archive, as distributed `.ym`
files are. Standard output is the tune file, and the report on standard
error covers the dump as it was read, the flags as they were read, the
sources by kind, a row a column of what it packed to, one closing line of
the frames, the sources, the effects run, the repeat row and the bytes
written, and the notes: effects dropped, a unit other than the one
requested, and a ring other than the one requested.

The table is the dump's frames row for row: the row it repeats to is
the dump's loop frame, its rows are the dump's, and no row is added
anywhere. A table packs at unit 2: unit 1 packs the corpus to 0.69 bytes
a frame against 0.81, and costs the play call about a seventh more on
average (performance.md); `-k1` selects it. A column's bytes and
its loop begin on a unit (DTX's R5.6 and R5.11), so a tune whose row
count or repeat row is odd packs at unit 1, which the tool notes. The
table packs at a period of thirty rows, the column count and the
smallest DTX allows, since a refill decodes a period's rows of one
column at once and a refill costs the period (performance.md);
the ring is the multiple of thirty nearest the one asked for, at least
sixty and at most 1,110. A loop longer than the ring is replayed at its
exact rows by DTX's reader, at any period.

What the converter does with a dump's effects: a SID voice is a source of
two rows, its level and 0; a sync buzzer one row, its shape; a digidrum the
recording's 4-bit levels and a closing row at mid-scale, which owns the
voice's volume for the frames its rows run at its rate and sets the mixer's
bits for the voice meanwhile. A sinus SID is dropped, since the reference
player runs an empty handler for it. The row the tune repeats to sets every
register except R13 and the ones an effect owns there, and every effect
that ran up to it or runs into the wrap, so the wrap resumes from a known
setting. `ConversionTest` replays every tune under `ym/test` against its
dump.

The tune file contains the tune's tables and no code: BINARIES.md defines
how a tool binds them with DTX's reader into the layout the player
reads.

The tool runs out of `target/classes`, and builds first where a source,
the pom or a 68000 source is newer than the last build, or the core is
not assembled. Java 23, Maven and rmac, with which the build assembles
the four cores and the stub once (`-Drmac=PATH` names another), and DTX
and YMXS in the local Maven repository: `mvn install` at DTX's `v0.7.0`
tag, whose reader this player's frame figures (performance.md) are
measured against, and `mvn install` in the YMXS checkout, whose records
every conversion passes through (doc/ymxs.md). The player names the table
to read at that reader's init, and a set of subtunes shares one image
(BINARIES.md 1), neither of which DTX 0.6.0 reads.

## Check

```
bin/ymxr-check [-kK] [-mN] [-rRR | -r] [-copies[S]] [-silent] < in.ym
bin/ymxr-check [flags] DUMP|DIR ...
```

The dump on standard input, or every dump named and every `.ym` under a
directory named, converted at
the tool's defaults and replayed against itself: the tune file's table
stepped frame by frame by the reader in `Replay`, every frame's registers
checked against the dump's except those an effect owns, and every effect's
source, target, rate and count checked against the dump's flags. One line a
tune, the first twenty wrong frames under a tune that fails, and an exit of
1 where any does; a file that is not a YM5!/YM6! dump is reported and not
counted.
`ConversionTest` runs the same check on the tunes under `ym/test`, and
the corpus (Measure, `YM_CORPUS`) runs through it whole in under two
minutes: 543 dumps replay to their dumps, and one file is not a dump.
The check steps one pass and the loop once, as a reader's record runs, and
accepts the converter's flags: `bin/ymxr-check -r DIR` replays every dump
as a tune that plays once.

## Trace

```
bin/ymxr-trace [-rROWS] [-silent] < tune.ymxr
```

What a reader reports of a tune file (SPEC.md 7), on standard output: the
first line the tune's fixed values, then one line a frame, `ROWS` of them
or the count the kit uses for the tune, one pass and the loop once, or the
pass and the frame that reports its end. A file of another version reports
none, which is an exit of 1. The conformance kit's references are the
output of this tool (doc/conformance/README.md).

## The structure

Every conversion passes through YMXS, the tune data structure
(doc/ymxs.md). These five tools are the stages of it, each a filter:
standard input, standard output, the report on standard error, and no file
between them.

```
bin/ym-to-ymxs   [-rRR | -r] [-silent]              < in.ym   > out.ymxs
bin/ymx-to-ymxs  [-rRR | -r] [-silent]              < in.ymx  > out.ymxs
bin/ymxs-to-ymxr [-kK] [-mN] [-copies[S]] [-silent] < in.ymxs > out.ymxr
bin/ymxs-to-sndh [-tTITLE] [-cCOMPOSER] [-perf] [-lean] ...   > out.sndh
bin/ymxs-to-prg  [-rROWS] ...                                 > OUT.PRG
```

| tool | reads | writes |
|---|---|---|
| `ym-to-ymxs` | a YM register dump, packed or not | the structure as JSON |
| `ymx-to-ymxs` | a YMX file | the structure as JSON |
| `ymxs-to-ymxr` | the structure | a tune file (SPEC.md 3.3) |
| `ymxs-to-sndh` | the structure | an SNDH file any SNDH host plays |
| `ymxs-to-prg` | the structure | a TOS program |

`ym-to-ymxs | ymxs-to-ymxr` writes the file `ym-to-ymxr` writes, byte for
byte, and `ymxs-to-sndh` and `ymxs-to-prg` write what `ymxr-sndh` and
`ymxr-prg` write of it. `YmxsTest` reads the first back on every tune under
`ym/test`.

A multi of several tunes is a set of subtunes: `ymxs-to-sndh` and
`ymxs-to-prg` put one tune file each behind one core, in the multi's
order, and a tune's title names its subtune. The title and the composer
are the first tune's unless `-t` and `-c` name others. `ymxs-to-ymxr`
writes a tune file of one tune and a multi file (BINARIES.md 0) of
several, which `ymxr-sndh` reads as those subtunes.

The packer's flags are the converter's: `-kK` the unit, `-mN` the ring,
`-copies[S]` the copies from a column's separate literal stream.

A run ends in one of three exits:

| exit | what it means |
|---|---|
| 0 | the tool completed, and standard output has the file |
| 1 | the input is wrong: not this format, or a structure this format cannot encode (ymxs.md, What is an error) |
| 2 | the call is wrong, or a stream failed |

The call is read before the input, so a flag a tool does not read is an
exit of 2 and the input is left unread. A fault is one line on standard
error, named by the tool that found it, and never a stack trace.
`FilterTest` runs the tools and reads their exits, their two streams and
`-silent` back.

## The player

`68k/YMXR.S`, assembled with `rmac -m68000 -fr`, is 2,564 bytes. Its
first three longs are the calls:

| call | in | out |
|---|---|---|
| `YMXR_init` | `a0` the bound tune (BINARIES.md), on an even address; `a1` the workspace, on a long | `d0` 0, or -1 for one the player does not read |
| `YMXR_play` | `a0` the workspace | `d0` 0, or -1 where the tune has played its last row and does not repeat |
| `YMXR_stop` | `a0` the workspace | the claimed timers stopped, disabled and masked, the three volumes silenced |

Every call clobbers `d0` to `d5` and `a0` to `a5`, and leaves `d6`, `d7`
and `a6` as they were. The workspace is `YMXR_FIXED`, 60 bytes, then the
state block the bound tune records at offset 12 (BINARIES.md). The host
owns the machine: the player saves and restores no vector, timer control
or interrupt enable, and touches no timer the tune does not run. The header
of the source defines the contract in full.

## Bind, SNDH file, program

```
bin/ymxr-multi tune.ymxr [more.ymxr ...] [-nNAME ...] [-silent] > tunes.ymxr
bin/ymxr-bind  [-silent] < tune.ymxr > tune.bin
bin/ymxr-sndh  [-tTITLE] [-cCOMPOSER] [-perf] [-lean] [-silent]
               < tune.ymxr > tune.sndh
bin/ymxr-prg   [-rROWS] [-silent] < tune.sndh > TUNE.PRG
```

A tune file contains tables and no code. The player reads the bound tune,
the file with DTX's image for its table in place of the table, which
`bin/ymxr-bind` writes; `bin/ymxr-sndh` puts one bound tune or more behind
the SNDH core, the player under SNDH's three entries, into an SNDH file any
SNDH host plays, with the tags from the flags; and `bin/ymxr-prg` puts the
program stub in front of an SNDH file, making a TOS program that claims the
machine under Supexec, plays the file from the VBL or Timer C, stops on
SPACE or ESC or after `ROWS` rows, switches subtunes on 1 to 9, and
releases the machine.

Subtunes come from a multi file (BINARIES.md 0): several tune files in
one, a name each, which `bin/ymxr-multi` writes from the tune files named
and `bin/ymxs-to-ymxr` from a structure of several tunes. `bin/ymxr-sndh`
reads a tune file as one subtune and a multi file as its tunes in order,
each named by the name the multi file records for it. The title is the
first tune's name unless `-tTITLE` names another.

`-perf` selects the core with the raster monitor in
(Measure), and the program then clears the screen so that its bars show;
`-lean` selects the core whose ticks neither drop the interrupt level nor
write an end of interrupt, which requires two things of the host
(performance.md). The two are a switch each, and both together select the
core that is both, whose bars are those of the lean ticks. The four cores
and the stub are assembled by the build with rmac, once, into the
classpath; BINARIES.md is the contract for every byte of them, and no
assembler runs at combine time.

## Play

```
ym/play.sh [-kK] [-mN] [-rRR | -r] [-copies[S]] [-tTITLE] [-cCOMPOSER]
           [-perf] [-lean] [-vN] [-silent] tune.ym [more.ym ...] [out.wav]
ym/play.sh -h
```

`ym/play.sh` runs the tools above and passes the program to Hatari
with its sound on. SPACE or ESC ends the program, and `-vN` stops the run
after `N` frames. The first name is a tune; after it a name ending in `.ym` or
`.ymxr`, in either case, is another tune and any other name records the
run instead:
Hatari writes an AVI, video and sound, which `ym/avi.py` reads back as a
WAV, with the run's last frame beside it as a PNG.

Several tunes go into one file, a subtune each in the order named and each
named by its file, which the program selects on the keys 1 to 9. An SNDH
file records one rate, so a set whose tunes do not share one produces a
line on stderr and no file, and a tenth tune and past it play only under a
host that selects a subtune by number.

| option | what it sets |
|---|---|
| `-kK` | the unit the table packs at, 2 by default |
| `-mN` | the ring in bytes, 960 |
| `-rRR`, `-r` | the row the tune repeats to, or a tune that plays once |
| `-copies[S]` | a match beyond the ring packs as a copy from the column's separate literal stream, which packs a small ring far smaller. `-copiesS` searches `S` seconds for a better parse, and a search of some seconds packs another parse every run |
| `-tTITLE`, `-cCOMPOSER` | the tags; the title is the file's name by default |
| `-perf` | the core with the raster monitor in, so the recorded frame shows the bars (Measure) |
| `-lean` | the core whose ticks neither drop the interrupt level nor write an end of interrupt (performance.md) |
| `-perf -lean` | the core that is both, so the bars are those of the lean ticks |
| `-vN` | the frames to run |
| `-silent` | the tools it drives report their notes alone, since this script reads their standard output |
| `-h` | the options and examples, from the head of the script |

`-k`, `-m` and `-r` belong to the converter, and a tune file, which is
packed already, accepts none of them. A set whose tunes do not share a unit
needs an image a unit and pays for DTX's reader twice, which the report
names; `-k1` on every dump needs one image and packs the tables smaller, at
about a fifth of the play call, and experiments.md has the result on twenty
tunes. `HATARI` and `TOS` name the emulator
and a TOS image, as they do for the rigs.

`ym/hatari.sh WORK [VBLS] [out.wav]` runs the `TUNE.PRG` under `WORK`, and
both play scripts end in it, so the emulator's flags and the recording
stand in one place.

## Playing a YMX file

```
ymx/play.sh [options] tune.ymx [more.ymx ...] [out.wav]
```

A `.ymx` converted and played: `bin/ymx-to-ymxr` makes a tune file of
each, and `ym/play.sh` reads those, so a name that is not a tune records
the run and several tunes go into one file as subtunes, as they do there.

`-kK`, `-mN` and `-copies[S]` reach the converter and every other option
belongs to `ym/play.sh`. The script runs the Java tools, which read a
`.ymx` through YMX's `ymx-dump`: `YMX_DUMP` names it, and with neither it
nor `YMX_REPO` set the default is `../YMX/go/bin/ymx-dump`.

The tune file each `.ymx` converts to is kept, and the directory
containing them is reported on stderr, so a conversion can be read back
with `bin/ymxr-trace` or played by itself. `ym/play.sh` reports where it
left the SNDH file and the program the same way.

## Playing a YMXS file

```
ym/play-ymxs.sh [-kK] [-mN] [-copies[S]] [-tTITLE] [-cCOMPOSER] [-perf]
                [-lean] [-rROWS] [-vN] [-silent] [tune.ymxs] [out.wav]
ym/play-ymxs.sh -h
```

The structure played: `bin/ymxs-to-prg` writes the program and Hatari
runs it, as `ym/play.sh` does with a dump. With no `.ymxs` name the
structure comes from standard input, so a dump plays with no file
between:

```bash
bin/ym-to-ymxs < tune.ym | ym/play-ymxs.sh
```

A name that is not a `.ymxs` records the run instead, as `ym/play.sh`
does, and the program stays under a directory reported on stderr.

One name is what it reads, since a multi of several tunes is a set of
subtunes, each named by its title, which the program selects on the keys 1
to 9. An SNDH file records one rate, so a multi whose tunes do not share
one produces a line on stderr and no program, and a tenth tune and past it
play only under a host that selects a subtune by number.

`-rROWS` stops the program after that many rows; the packer's flags and the
tags are `bin/ymxs-to-prg`'s, and `-vN`, `-silent` and `-h` are
`ym/play.sh`'s.

## The rigs

`68k/test/emu/test_ymxr.py` plays every tune under `ym/test`, or the tunes
named, on an emulated 68000 and checks the player against a model of
SPEC.md 4 and 5 built from the tune's tables: every frame's chip writes in
order, the timers' programming against the rate columns, each handler's
place against the source's rows, and every tick's write against the row its
place names.

```
python3 68k/test/emu/test_ymxr.py [tune.ym ...]
python3 68k/test/emu/test_ymxr.py -corpus [N]
python3 68k/test/emu/test_ymxr.py -framesN [tunes]
python3 68k/test/emu/test_ymxr.py -cycles [tunes]
python3 68k/test/emu/test_ymxr.py -hatari [tunes]
python3 68k/test/emu/test_ymxr.py -kit [tune.ymxr ...]
python3 68k/test/emu/test_ymxr.py -lean [tunes]
```

`-framesN` plays each tune for N frames at most. Every frame it plays is
read against the model as a whole run reads it; what a capped run leaves is
the wrap and the end, which a tune reaches only once its rows are played.
The ten fixtures whole are a minute, and at `-frames40` eight seconds, so a
build runs a capped one and a release the whole: `RigCallsTest` runs the two
built tunes at `-frames24` on every build.

The tunes under `ym/test` are chosen for the shapes a tune has, one of
each, and `-corpus` reads the corpus instead: every Nth file of it by name,
forty tunes unless `-corpusN` sets another count, so a sample covers the
corpus rather than one composer's run of it. Forty is about eleven minutes.
A tune that fails is named and the rest are read, so one run reports every
tune that fails rather than the first alone.

A tune named as a `.ymxr` file plays as it stands, without the converter. A
tune whose `RR` is `R` is played one frame past its last row, where the
call reports -1 and writes no register. `-hatari` requires tunes at 50 Hz:
it cuts the trace into frames at the VBL, which the program plays from
where the screen's rate is the tune's, and Hatari's ST refreshes at 50
Hz.

Under unicorn, which raises no interrupt, the rig models the four timers
and fires every tick itself at the time the model computes. `-cycles`
counts the play call, the share of it spent in DTX's advance, and the tick
handlers, with DTX's cycle counter, whose tables this rig adds `movep` to,
and fails where performance.md's figures differ from what it counts.
`-hatari` puts the tune into an SNDH file and a program around it through
`bin/ymxr-sndh` and `bin/ymxr-prg`, with 2,000 rows to play, runs it under
a cycle-exact Hatari,
and reads the trace of every chip write against the same model, so the
ticks come from the MFP itself: the frames are separated by the VBL, and
the ticks counted against the rates the trace shows the timers programmed
at. `ym/cost.sh` measures the same program's cycles instead, through the
raster monitor (Measure). `-perf` builds the player with that monitor in
and checks it against the model under unicorn, so a band painted or a cost
counted changes no register a frame writes, and none of the order it writes
them in. `-lean` builds it with the two switches a tick reads,
`YMXR_NEST=0` and `YMXR_AEOI=1` (performance.md), and checks that build
against the same model. `-kit` plays the conformance kit's tunes, or the
tune files named, and checks each frame the player produces against the
reader's record of it through `bin/ymxr-trace`, and the record's first line
against the tune's header, so the player, the rig's model and the reader
agree line by line. Every tune is bound through `bin/ymxr-bind` before the
player reads it; a tune file of another version is rejected by the binder
and the reader, and the player rejects a bound tune of another version.

| variable | what it names |
|---|---|
| `RMAC` | the assembler, `rmac` on the path by default |
| `DTX_WRITE` | DTX's `dtx-write`, which reads the table back out of the bound tune's image; built from DTX's `go/cmd/dtx-write` |
| `DTX_REPO` | the DTX checkout, `../DTX` by default, for the cycle counter under `68k/test/emu` |
| `HATARI`, `TOS` | the emulator and a TOS image, `hatari` and `~/hatari-2.6.1_macos/tos-2.06.rom` by default |

## From a YMX file

```
bin/ymx-to-ymxr [-kK] [-mN] [-copies[S]] [-silent] < in.ymx > out.ymxr
```

A `.ymx` into a tune file, for moving a library of them across, through
the structure (doc/ymxs.md): `ymx-to-ymxs | ymxs-to-ymxr` writes the same
file. Neither tree reads the container here: the Go tool decodes it with
YMX's reader, which it imports as a module, and the Java tool runs YMX's
`ymx-dump`. The contents of a `.ymx` are read by the tree that writes them
either way.

YMX's streams 0 to 13 are the sound registers as the chip receives them,
its effect bits stripped (YMX, SPEC.md 2), so a frame's fourteen values go
through the conversion a YM dump's do and accept the same flags.

Streams 14 to 24 are the script that drives YMX's four timer channels,
and a channel there is an effect here: a source on a target at a timer's
rate. Six of YMX's eight opcodes convert. `START_TOGGLE` is two rows on a
volume register, `START_RETRIGGER` one row on R13, `START_PCM` the sample's
bytes with the end marker YMX writes as this format's marker, `RETUNE` a
rate with the voice's volume repatched, `HOLD` a reloaded count or
source, and `RELEASE` source 0.

A `RETUNE` at a voice and a `HOLD` that reloads a parameter change the
source without moving the place: the stream keeps its phase and the half it
stands in (YMX, SPEC.md 3.1). Section 6 rule 3 allows exactly that, since
the source they start has the row count the effect already runs, so the row
leaves bit 5 clear. The row the tune repeats to stops every effect it does
not start, so a wrap resumes from a known setting. `START_PCM_PREEMPT`
stops the channels its operand names, filling their whole row: a select
left standing there would start the timer the stop just stopped (SPEC.md
1.9).

YMX fits a tune to its unit by padding it, where this conversion drops to a
unit of 1 instead, so a dump of an odd frame count is one frame longer
through YMX. A dump's rows are the tune's rows (`ym-to-ymxr` prevails where
the two readings differ), so a pad comes off: one frame, repeating the
frame before it, acting on no channel, on an even count. A tune whose last
frame reads that way loses it, and a file packed at a wider unit keeps the
pad past the first.

A start sets bit 6 where the channel's timer is stopped and leaves it clear
over a running stream, since bit 6 affects a running timer and a stopped
one starts on the select either way (1.9, section 6 rule 3). The shape a
retrigger start restarts stands in X's bits 7 to 4, and the channels a
preempt stops in its bits 3 to 0.

`RESUME` is not read, and a frame that runs it produces a note on stderr.
Which timer a channel runs on is YMX's `T` stream and this schema's 2.3, so
the map does not cross: channel 0 becomes effect 0.

Whether the tune starts over is bit 0 of the header's flags (YMX, SPEC.md
1.2). The loop frame `L` is 0 both in a tune that plays once and in one
that starts over from its first frame, so the flag is the one place the
two part: with it clear the tune converts to a table whose `RR` is `R`
(SPEC.md 3.1), and the frame after the last row reports -1 rather than
wrapping. `-rRR` and `-r` on `ymx-to-ymxs` name the row instead.

An action byte's P is the timer count, and 0 there is the MFP's slowest
count, 256 (YMX, SPEC.md 5). It converts as it stands: the count column
reaches 0 with bit 4 of the control column beside it (SPEC.md 1.9).

**Two readings the structure corrected.** Reading a `.ymx` as a structure
rather than as columns exposed two places where the columns this tool wrote
broke SPEC.md. A sample whose bytes run past YMX's end marker put a second
marker inside a source, where 3.2 allows one and a tick ends on the first:
the source now ends at that marker. An action byte whose low bits are 0
wrote select 0 to the control column, which 1.9 defines as a stop: an
opcode that programs a timer names an index of 1 to 7 (YMX, SPEC.md 2.4),
so a file naming 0 there produces a note and the row is left behind. Both
change what a `.ymx` converts to, by a few bytes either way;
`ym-to-ymxr` is untouched by them.

## Against YMX

```
ym/parity.py [-whole] [tune.ym ...]
```

One tune packed both ways and played twice: through YMX's `ymx` and
`mkprg` into a program, and through this repository's three tools into
another. Both run under Hatari with their chip writes traced and cut into
frames at the VBL, and each frame's fourteen registers are read against
the other run's, each masked to the register's bits.

The frames are aligned on the first write to a sound register other than
R7, which TOS writes at boot before a program runs. It compares one pass of
the music, the tune's row count from that frame; past the wrap a tune
starts over at the row each tree read out of the dump, which is the
converters' reading rather than a player's behaviour. `-whole` reads the run
to its end.

A register an effect drives is sampled at the frame's edge, where a
toggle lands one side or the other and two players that both play the
wave right still part (performance.md). Those partings are counted and
named. A frame differing on a register no effect drives is what fails the
run.

The tunes under `ym/test` are one of each shape, which the rig requires of
them. This reads eleven from the corpus besides, six whose
effects are square waves on a volume register and five whose sources are
recordings played once, so the shapes an ST tune is driven with are read
against the other player rather than assumed.

A name ending `.ymx` is a tune with no dump behind it. YMX plays the file
itself, and this tree plays what `bin/ymx-to-ymxr` makes of it, so the run
reads the move across rather than two packings of one dump.
`ymx/test` has four of them, and the default reads those.

| variable | what it names |
|---|---|
| `YM_CORPUS` | the corpus those eleven stand in |
| `YMX_REPO` | the YMX checkout, `../YMX` by default |
| `YMX_BIN` | its built Go tools, `$YMX_REPO/go/bin` by default |
| `HATARI`, `TOS`, `VBLS` | the emulator, a TOS image and the frames to run |

## Measure

`ym/convert.py` runs the corpus through the specification and prints the
figures in experiments.md, and `ym/measure.py` the register-level figures
in SPEC.md 1.2 and 1.7. `YM_CORPUS` names the corpus,
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
(`68k/YMXR.S`, `YMXR_PERF`): the call paints the background red while its
work runs, each tick handler paints a separate colour, and the call burns
a yellow bar for the cost of the ticks it counted, after its writes, so
none of them moves for it. `-lean` builds the core whose ticks neither drop
the interrupt level nor write an end of interrupt, so the result is that
core's cost against the plain one's. `HATARI`, `TOS` and `VBLS` name the
emulator, a TOS image and the frames to run. performance.md has the figures
and what the method leaves out.

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

`ym/writes.py` reads two runs' chip writes against each other: for every
register, whether the values come in the same order and where they part.
The cores of BINARIES.md play one tune through the same player, so this
is the measure of whether a switch changed the tune. It counts from the
frame the player first writes in, since a program that clears the screen
starts a frame later and 900 frames from the first VBL are then 900
different rows, and a register whose values agree to the shorter run's
end is the window's edge, not a difference. performance.md, A tick, has
what it read off the four cores.
