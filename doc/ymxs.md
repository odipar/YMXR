# Conversion through YMXS

[YMXS](https://github.com/odipar/YMXS) is the tune data structure: rows of
registers and effects, the sources those effects run, and one rate a tune.
YMXS defines it once, in records, and encodes it as JSON, and every
conversion here passes through it.

    ym ──► ym-to-ymxs ──► YMXS ──► ymxs-to-ymxr ──► a tune file, or a
                                    │              multi file of several
                                    ├──► ymxs-to-sndh ──► an SNDH file
                                    └──► ymxs-to-prg  ──► a TOS program

`ym-to-ymxr` runs both stages in one call and writes the pipeline's
bytes, which `YmxsTest` reads back on every tune under `ym/test`.

A tracker can emit YMXS JSON for conversion to YMXR.

## The two stages

**Reading.** `Ym` converts a YM3!, YM3b, YM5! or YM6! dump into register
rows, effects, sources and a repeat row. Import rules cover drums preempting
square waves, drum duration from source rows and rate, effect volume
registers, and repeat rows that restore registers and restart effects
running through the wrap. A YM3 dump is fourteen vectors of one register
each and no header: the rate is 50 Hz, the clock 2,000,000, and R14 and R15
are zero, so it runs no effect; YM3b names the frame it repeats to in a long
after the vectors.

**Encoding.** `Schema` converts the structure into the columns of SPEC.md
1: set bits, bits marking zero values in full-byte columns, effect
columns, source markers or counts, and timer assignments.

## What each column is written from

| the structure | the columns |
|---|---|
| a row's registers | each in its column, with the set bit, and the bit beside it where a column that fills its byte is 0 (1.1, 1.2, 1.7) |
| a count of 0 | the count column at 0, and bit 4 of the control column beside it, which marks that 0 as the value the MFP counts 256 for (1.1, 1.9) |
| `Start` | source; target if changed; control and count if a reset is set, the rate changes or the timer may be stopped |
| `Retune` | control if the select changes, a reset is set or the count changes to 0; count if changed |
| `Stop` | the source column at `$80`, the rate columns left unset (1.8) |
| a source | a column a register of its target (3.1.3), repeating at its repeat row: bit 7 of the marker's column set on the last row (3.2), or, where that register reads its whole byte, rows a player counts and bit 31 of the index entry (3.1.6) |
| Timer A, D, B, C | effects 0, 1, 2, 3 (2.3) |
| the tune's sources | numbered 1 upward in the order a row first starts each (3.1) |

## The rate a row leaves alone

The schema tracks each effect's target, select and count as the player
does (R3.6, R4.6), and leaves a column unset where the operation allows:
a retune with both resets clear that changes only a count other than 0
leaves the control column unset. For a start, the schema counts the timer
as running only where its previous source repeats and no row stopped it.

The player's kept values run through the wrap, so the repeat row writes
its targets again.

## What is an error

A structure this format cannot encode is an error: the tool reports the
row or the source and exits 1.

| the structure | why |
|---|---|
| more than 127 sources | the source column numbers 1 to 127 (1.8) |
| a count past 255 | the count column is the timer's data register, a byte (1.9) |
| a target past 24 | 25 to 127 are left to a later version (2.1, section 8) |
| a source on two targets whose markers are in different columns | a source has one marker column (SPEC.md 6, rule 2(d)) |
| a value past 127 in the marker's column of a marked source | bit 7 of that column is the marker (3.2) |
| a value past 255 | a column of a source is one byte (3.1.3) |

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
from standard input, and records the run to a WAV it is named
(tools.md 16.2):

```bash
ym/play-ymxs.sh tune.ymxs
bin/ym-to-ymxs < tune.ym | ym/play-ymxs.sh -v3000 run.wav
```

## Building

YMXS is a Maven dependency, `org.ymx:ymxs`, so a build needs `mvn
install` in the YMXS checkout, as it does in DTX's.
