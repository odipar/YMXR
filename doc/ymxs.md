# Conversion through YMXS

[YMXS](https://github.com/odipar/YMXS) is the tune data structure: rows of
registers and effects, the sources those effects run, and one rate a tune.
It is defined once, in records, and encoded as JSON. Every conversion
here passes through it.

    ym ──► ym-to-ymxs ──► YMXS ──► ymxs-to-ymxr ──► a tune file, or a
                                    │              multi file of several
                                    ├──► ymxs-to-sndh ──► an SNDH file
                                    └──► ymxs-to-prg  ──► a TOS program

`ym-to-ymxr` runs both stages in one call and writes the bytes the
pipeline writes, which `YmxsTest` reads back on every tune under
`ym/test`.

A tracker can emit YMXS JSON for conversion to YMXR.

## The two stages

**Reading.** `Ym` converts a YM5!/YM6! dump into register rows, effects,
sources and a repeat row. Import rules cover
drums preempting square waves, drum duration from source rows and rate,
effect volume registers, and repeat rows that restore registers and
restart effects running through the wrap.

**Encoding.** `Schema` converts the structure into the columns of SPEC.md
1: set bits, bits marking zero values in full-byte columns, effect
columns, source markers and timer assignments.

## What each column is written from

| the structure | the columns |
|---|---|
| a row's registers | each in its column, with the set bit, and the bit beside it where a column that fills its byte is 0 (1.1, 1.2, 1.7) |
| a count of 0 | the count column at 0, and bit 4 of the control column beside it, which marks that 0 as the value the MFP counts 256 for (1.1, 1.9) |
| `Start` | source; target if changed; control and count if a reset is set, the rate changes or the timer may be stopped |
| `Retune` | control if the select changes, a reset is set or the count changes to 0; count if changed |
| `Stop` | the source column at `$80`, the rate columns left unset (1.8) |
| a source | its values, bit 7 set on the last row as the marker (3.2), repeating at its repeat row |
| Timer A, D, B, C | effects 0, 1, 2, 3 (2.3) |
| the tune's sources | numbered 1 upward in the order a row first starts each (3.1) |

## The rate a row leaves alone

The schema tracks each effect's target, select and count as the player
does (R3.6, R4.6). It omits unchanged columns where the operation permits:
a retune with both resets clear that changes only a nonzero count leaves
the control column unset. For a start, the timer is treated as running
only when its previous source repeats and has not been stopped.

A wrap preserves the player's kept values, so the repeat row writes its
targets again.

## What is an error

For a structure this format cannot encode, the tool reports the row
and exits 1:

| the structure | why |
|---|---|
| a count past 255 | the count column is the timer's data register, a byte (1.9) |
| a source value past 127 | bit 7 of a source's row is the marker (3.2) |
| more than 127 sources | the source column numbers 1 to 127 (1.8) |

## The tools

The tools read standard input, write standard output and report on
standard error (tools.md).

A YMXS multi of several tunes is a set of subtunes, one tune file each,
which `ymxs-to-sndh` puts behind one core. `ymxs-to-ymxr` writes those
tune files as one multi file (BINARIES.md 0), which `ymxr-sndh` reads.

```bash
bin/ym-to-ymxs < tune.ym | bin/ymxs-to-prg > TUNE.PRG
bin/ym-to-ymxs < tune.ym > tune.ymxs
bin/ymxs-to-sndh -tTitle < tune.ymxs > tune.sndh
```

`ym/play-ymxs.sh` runs the first of those under Hatari, from a file or
from standard input, and records the run where it is named a WAV
(tools.md, Playing a YMXS file):

```bash
ym/play-ymxs.sh tune.ymxs
bin/ym-to-ymxs < tune.ym | ym/play-ymxs.sh -v3000 run.wav
```

## Building

YMXS is a Maven dependency, `org.ymx:ymxs`, so a build needs that
repository beside this one: `mvn install` in the YMXS checkout, which DTX
already requires too.
