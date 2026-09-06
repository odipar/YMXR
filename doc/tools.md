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
| `-kK` | the unit the table packs at, 1 by default |
| `-mN` | the ring a column unpacks through, in bytes, 960 by default and at most 1129: the player reaches column 29 through a 16-bit displacement |
| `-rRR` | the row the tune repeats to. The default is the dump's loop frame, and `-r` alone a tune that plays once |

The first file is unpacked where it is an LHA archive, as distributed
`.ym` files are. The tool prints the frames, the sources, the effects
run, the repeat row and the bytes written, then its notes: effects
dropped, rows padded, and a ring other than the one asked for.

A table that repeats packs at a period of thirty rows, the column count
and the smallest DTX allows, since a refill decodes a period's bytes of
one column at once and the period is what a refill costs (performance.md).
DTX asks that the repeat row and the loop's rows divide by the period, so
silent rows pad the repeat row up to a multiple of thirty and the loop
up to a multiple of thirty of three periods or more, and the tool says
how many. The ring is shorter than the loop, so that the loop is
replayed at the wrap: a loop the ring holds is read wrong past the wrap
by DTX 0.4.0's reader, which `68k/test/emu/test_ymxr.py` found.

What the converter does with a dump's effects: a SID voice is a source of
two rows, its level and 0; a sync buzzer one row, its shape; a digidrum
the recording's 4-bit levels and a closing row at mid-scale, which owns
the voice's volume for the frames its rows take at its rate and sets the
mixer's bits for the voice meanwhile. A sinus SID is dropped, as the
reference player runs an empty handler for it. The row the tune repeats
to sets every register and every effect, so the wrap lands on a known
state. `ConversionTest` replays every tune under `ym/test` against its
dump.

The tool runs out of `target/classes`, and builds first where a source or
the pom is newer than the last build. Java 23 and Maven, and DTX
0.5-SNAPSHOT in the local Maven repository: `mvn install` on DTX's main
at or past the reader that walks its decoder states, which this player's
frame figures (performance.md) are measured against.

## The player

`68k/YMXR.S`, assembled with `rmac -m68000 -fr`, is 2,428 bytes. Its
first three longs are the calls:

| call | takes | gives |
|---|---|---|
| `YMXR_init` | `a0` the tune file, on an even address; `a1` the workspace, on a long | `d0` 0, or -1 for a file the player does not read |
| `YMXR_play` | `a0` the workspace | `d0` 0, or -1 where the tune has played its last row and does not repeat |
| `YMXR_stop` | `a0` the workspace | the claimed timers stopped, disabled and masked, the three volumes silenced |

Every call clobbers `d0` to `d5` and `a0` to `a5`, and leaves `d6`, `d7`
and `a6` as they were. The workspace is `YMXR_FIXED`, 56 bytes, then the
state block the tune file states at offset 12. The host owns the machine:
the player saves and restores no vector, timer control or interrupt
enable, and touches no timer the tune does not run. The header of the
source gives the contract in full.

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
```

Under unicorn, which raises no interrupt, the rig models the four timers
and fires every tick by hand at the time the model gives. `-cycles` counts
the play call, the share of it spent in DTX's advance, and the tick
handlers, with DTX's cycle counter, whose tables this rig adds `movep` to,
and fails where performance.md's figures are not what it counts.
`-hatari` builds the stub, `68k/YMXR_prg.S`,
around the player and the tune, runs it under a cycle-exact Hatari, and
reads the trace of every chip write against the same model, so the ticks
are the MFP's own: the frames are told apart by the VBL, and the ticks
counted against the rates the trace shows the timers programmed at. The
stub paints the background red around each call, so a run traced with
`--trace video_color` reads back through YMX's `ymx/test/cost.py` as
the call's cycles on a cycle-exact machine (performance.md, Against
YMX).

| variable | gives |
|---|---|
| `RMAC` | the assembler, `rmac` on the path by default |
| `DTX_WRITE` | DTX's `dtx-write`, which reads the table back out of the image; built from DTX's `go/cmd/dtx-write` |
| `DTX_REPO` | the DTX checkout, `../DTX` by default, for the cycle counter under `68k/test/emu` |
| `HATARI`, `TOS` | the emulator and a TOS image, `hatari` and `~/hatari-2.6.1_macos/tos-2.06.rom` by default |

## Measure

`ym/convert.py` runs the corpus through the specification and prints the
figures experiments.md holds, and `ym/measure.py` the register-level
figures SPEC.md 1.2 and 1.7 give. `YM_CORPUS` names the corpus,
`DTX_WRITE` the DTX writer, `DTX_RING` the ring, `JOBS` how many tunes
convert at once, and `YMX_PAIRS` the tunes with a `.ymx` beside them.
