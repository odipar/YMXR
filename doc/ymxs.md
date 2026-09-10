# YMXS in the middle

[YMXS](https://github.com/odipar/YMXS) is the tune data structure: rows of
registers and effects, the sources those effects run, and one rate a tune.
It is defined once, in records, and written down as JSON. Every conversion
here passes through it.

    ym  ──► ym-to-ymxs  ──┐
                          ├──► YMXS ──► ymxs-to-ymxr ──► a tune file, or a
    ymx ──► ymx-to-ymxs ──┘                    │         multi file of several
                                               ├──► ymxs-to-sndh ──► an SNDH file
                                               └──► ymxs-to-prg  ──► a TOS program

`ym-to-ymxr` and `ymx-to-ymxr` run both stages in one call and write the
bytes the pipeline writes, which `YmxsTest` reads back on every tune under
`ym/test`.

A YMXS file is where a tune is read, edited or written: a tracker that
emits YMXS reaches this player with no dump behind it.

## The two stages

**Reading** decides what a tune is. `Ym` reads a YM5!/YM6! dump and `Ymx` a
YMX file: which register each row sets, which effect starts, moves or
stops, what each source sounds, and which row the tune repeats to. Every
rule about a dump lives here: a drum preempting a square on its voice, a
drum's duration reckoned from its rows and its rate, the volume register
an effect owns, and the row the tune repeats to setting every register and
starting every effect that runs into the wrap.

**The schema** (`Schema`) encodes that structure as the thirty columns of
SPEC.md 1. Every rule about the encoding lives here: the set bit, the bit
beside a column that fills its byte, the four columns an effect, the
marker on a source's last row, and the effect each timer runs.

No other class reads a dump, and no other class writes a column.

## What each column is written from

| the structure | the columns |
|---|---|
| a row's registers | each in its column, with the set bit, and the bit beside it where a column that fills its byte is 0 (1.1, 1.2, 1.7) |
| `Start` | the source column; the target column where the target differs from the one the player keeps; the control column with the two resets; the count column |
| `Retune` | the control column where the select or a reset moved, and the count column where the count moved |
| `Stop` | the source column at `$80`, the rate columns left unset (1.8) |
| a source | its values, bit 7 set on the last row as the marker (3.2), repeating at its repeat row |
| Timer A, D, B, C | effects 0, 1, 2, 3 (2.3) |
| the tune's sources | numbered 1 upward in the order a row first starts each (3.1) |

## The rate a row leaves alone

A YMXS effect is the whole of what an effect is: its target, its source
and its rate. A column is written where its value differs from the value
the player keeps (R3.6, R4.6), so the schema keeps the target, the select
and the count of each effect as a player does, and a retune whose select
did not move leaves the control column unset.

The delta is not part of the structure, and it is not lost by leaving it
out: a row that moves a count alone and a row that moves both are two
different structures, and each encodes to the columns it needs.

After the wrap a player keeps what the last row left, so the row the tune
repeats to writes its targets again.

## What is an error

A structure this format cannot encode is a fault of the file, and the tool
reading it exits 1 and says which row:

| the structure | why |
|---|---|
| a count of 256 | the count column fills its byte, and 0 is the row that does not set it (1.9) |
| a source value past 127 | bit 7 of a source's row is the marker (3.2) |
| more than 127 sources | the source column numbers 1 to 127 (1.8) |

## The tools

Each is a filter: standard input, standard output, and the report on
standard error (tools.md). No stage writes a file between them: a tune
passes from one to the next as bytes on a pipe. YMX's `ymx-dump` opens a
file name rather than a stream, so `ymx-to-ymxs` calls it with
`/dev/stdin` and reads its input through it.

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
repository beside this one: `mvn install` in the YMXS checkout, as DTX
already asks for.
