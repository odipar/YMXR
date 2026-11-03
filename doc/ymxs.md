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
columns, mapping columns, source markers or counts, and timer
assignments.

## What each column is written from

| the structure | the columns |
|---|---|
| a row's registers | each in its column, with the set bit, and the bit beside it where a column that fills its byte is 0 (1.1, 1.2, 1.7) |
| a count of 0 | the count column at 0, and bit 4 of the control column beside it, which marks that 0 as the value the MFP counts 256 for (1.1, 1.9) |
| `Start` | source; target if changed; control and count if a reset is set, the start is deferred, the rate changes or the timer may be stopped |
| `deferred` of a `Start` | bit 3 of the control column, which the start sets (1.9, 4.8) |
| `Retune` | control if the select changes, a reset is set or the count changes to 0; count if changed |
| `Stop` | the source column at `$80`, the rate columns left unset (1.8) |
| the `mapping` of a `Start` | the mapping column, the number of its last part, where it differs from the kept mapping (1.10; SPEC.md 6, rule 2(g)) |
| `Remap` on a timer of the tune | the mapping column with the source column unset, where the effect may run through another mapping (1.10; SPEC.md 6, rule 2(g)) |
| `Remap` on a timer outside the tune | every column left unset (SPEC.md 6, rule 2(a)) |
| the tune's transformations | numbered 1 upward in the order of YMXS, SPEC.md 1.9, in a tune file of version 10 where a row sets a mapping column to one (3.3.5, 3.4) |
| a source | a column a register of its target (3.1.3), repeating at its repeat row: bit 7 of the marker's column set on the last row (3.2), or, where that register reads its whole byte, rows a player counts and bit 31 of the index entry (3.1.6) |
| a source of one column every `Start` maps through a transformation, with a key of 128 or more | rows a player counts and bit 31 of the index entry (3.1.6) |
| a source a `Start` on `setR7` maps through a transformation | each key k as k + 192, the two port directions set (SPEC.md 6, rule 2(f)) |
| Timer A, D, B, C | effects 0, 1, 2, 3 (2.3) |
| the tune's sources | numbered 1 upward in the order a row first starts each (3.1) |

## Kept values

The schema tracks each effect's target, select, count and mapping, and
omits columns whose values can be reused. A retune that changes only a
nonzero count, with both resets clear, leaves the control column unset.

For mappings, it tracks the kept value for later starts, the running
value for ticks, and a pending deferred start's snapshot. After deferring
a change, the running mapping may be unknown: the rows do not determine
which tick reaches the source boundary. The next remap then writes its
column even if its mapping equals the kept value (SPEC.md 6, rule 2(g)).

Kept values survive the wrap. At the repeat row the schema marks targets
and, for each effect that uses a transformation, all three mapping values
as unknown. The first subsequent start or remap writes the required
mapping. For select, count and timer-running state, it compares the
values before the repeat row with those after the last row. Equal values
can be reused; differing values require later rate-column writes
(SPEC.md 6, rule 4(e)).

## The place's reset

A start sets bit 5 of its control column where the structure sets
`placeReset`, and leaves it at 0 where the structure clears it: the
place keeps its row number modulo the row count of the source started,
on any target and across the wrap (SPEC.md 1.8.4; YMXS, SPEC.md 3.4.3).
A tune file of version 7 set bit 5 on every start outside the old rule
3(a), where the place it left was undefined; every such file reads as it
played, since its starts with bit 5 at 0 name a source of the row count
of the one before.

## A deferred start

A start with `deferred` set sets the control column with bit 3 (SPEC.md
4.8): a player arms it on a running timer and performs it at the tick
that reads the last row of the running source. Until that tick the timer
counts at the rate of the effect before it, so the schema counts the
rate as open after a deferred start, and the next row that sets a rate
column of the effect sets both. With `placeReset` clear the source
started continues the count of the rows the running source played, at
its row count modulo R of the source started (YMXS, SPEC.md 3.5.3,
version 6). A structure of version 5 reads as version 6, and a tune file
of version 8 as version 9.

A deferred start captures its row's mapping. A remap while it is pending
changes the running effect and the kept mapping; the pending start
retains its snapshot. At the source boundary it installs that snapshot,
leaving the kept mapping for subsequent starts (SPEC.md 1.10.3).

## Optional transformations

An empty YMXS mapping uses identity: source values are written unchanged.
A transformation is an optional lookup table, selected by a mapping part.
YMXR permits it only on the target's last value; every earlier part must
be identity. For `setVoiceA`, `[0, 0, Vn]` changes volume while preserving
pitch. For `setR8`, `[Vn]` changes the sole value.

Use volume tables to play a sample at different levels without copying
its source. Successive `Remap` rows produce a fade without restarting it;
independent timers can run the same sample at separate levels. A table
can also convert 8-bit sample keys to YM volume values, change buzzer
shapes, or scale a noise effect's volume. The table changes values alone;
source timing, length and repetition remain unchanged.

In YMXS JSON, 0 denotes identity and a positive number selects a
transformation. In YMXR columns, the set bit distinguishes an explicit
identity selection (`$80`) from an unset mapping (`$00`, preserve the
kept value). The schema emits version 10 only when a row selects a
transformation; identity-only tunes remain version 9. [writing.md](writing.md)
5.7 covers key ranges and [SPEC.md](SPEC.md) 6, rule 2(g) defines the
column-write algorithm.

## What is an error

A structure this format cannot encode is an error: the tool reports the
row or the source and exits 1.

| the structure | why |
|---|---|
| more than 127 sources | the source column numbers 1 to 127 (1.8) |
| more than 127 transformations, and a start, or a remap on a timer of the tune, through one | the mapping column numbers 1 to 127 (1.10) |
| a count past 255 | the count column is the timer's data register, a byte (1.9) |
| a start, or a remap on a timer of the tune, through a transformation for a value other than the last of its target | the mapping column maps the last value alone (1.10) |
| a target past 24 | 25 to 127 are left to a later version (2.1, section 8) |
| a source on two targets whose markers are in different columns | a source has one marker column (SPEC.md 6, rule 2(d)) |
| a source on `setR7` and on `setR0`, `setR2`, `setR4`, `setR11` or `setR12` | every row of a source on `setR7` has the two port directions, bits 7 and 6 (SPEC.md 6, rule 2(f)) |
| a value past 127 in the marker's column of a marked source | bit 7 of that column is the marker (3.2) |
| a key past 63 of a source a start on `setR7` maps through a transformation | bits 7 and 6 of each row are the two port directions (SPEC.md 6, rule 2(f)) |
| a value past 255 | a column of a source is one byte (3.1.3) |

## The tools

The tools read standard input, write standard output and report on
standard error (tools.md).

A YMXS multi of several tunes is a set of subtunes, one tune file each,
their rings chosen for the set (tools.md 4.7), which `ymxs-to-sndh` puts
behind one core. `ymxs-to-ymxr` writes those tune files as one multi file
(BINARIES.md 0), which `ymxr-sndh` reads.

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
