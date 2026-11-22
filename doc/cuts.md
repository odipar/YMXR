# Cuts in YMXR

YMXS 0.8.0 adds a cut to each `Start` and a fifth operation, `Recut`,
that sets the cut of a running effect (YMXS, SPEC.md 3.7; YMX issue 180).
This document is the plan of encoding B of YMXS's doc/cuts.md in YMXR: a
column an effect selects a cut of an index in the tune file, and the
counted tick handler ends the cut at its last row. SPEC.md and
BINARIES.md replace this document once they define what landed.

## What stays

A tune whose every `Start` is under the whole table of its source, and
which has no `Recut`, converts to the tune file it converts to today, at
version 9 or 10, and a structure of version 7 or before reads that way
(YMXS, SPEC.md 3.7.1). The conformance files and the eleven tunes of
`ym/test` are such tunes; milestone 1 verifies that the bump of the
dependency leaves every file the tests compare as it is.

## The column

Effect i has a cut column, the column i of the four cut columns, which
follow the mapping columns where the tune has them (The tune file): bit
7 the set bit and bits 6 to 0 a cut number, as in a mapping column
(SPEC.md 1.10).

| byte | the row |
|---|---|
| `$00` | leaves the column unset |
| `$80` | sets cut 0, the whole table of the source started |
| `$81` to `$FF` | sets cut 1 to 127 of the cut index |

Each effect has a *kept cut*, 0 from 4.1. The column is read with the
source column:

| source column | cut column set | cut column unset |
|---|---|---|
| 1 to 127, a start | the kept cut is bits 6 to 0, and the start runs under it | the start runs under the kept cut |
| 0, a stop | unread, and the kept cut is left as it is | the kept cut is left as it is |
| unset | a recut | the effect runs under its cut |

**A recut.** The kept cut is bits 6 to 0 of the column. On a timer
running a source, the running cut is the kept cut, and where the place
is outside it, the place is its first row; the source, the target, the
rate and the mapping are left as they are (YMXS, SPEC.md 4.3). On an
idle timer the timer is left as it is (YMXS rule 5). A start pending on
the timer keeps the cut of its row, and the running source ends at the
last row of the new cut (YMXS, SPEC.md 3.5.5). A row that sets the
mapping column and the cut column, the source column unset, performs the
remap of 4.3.1 and then the recut.

## The cut index

A tune file of version 11 has U cuts, 1 to 127, at Q, the offset in the
long at 20:

| offset | bytes | meaning |
|---|---|---|
| Q | 2 | U, the cut count, 1 to 127 |
| Q + 2 | 8U | cuts 1 to U, each a long, its first row, then a long, its last row |

The index begins on a long: after the transformations where bit 5 of
the effects used is 1, and otherwise after the name, or after the source
index where the name is empty. The DTX3 table follows it on a long. A
writer numbers the cuts as it numbers the transformations (SPEC.md
3.4.1): first use in row order, then timer order A to D. Two cuts of the
same first and last rows are one cut, and cut 0 has no entry. A cut
number is free of any source, so section 6 gains a rule: every cut a
start or a recut selects lies in the rows of the source it runs on,
which YMXS's check reads of the structure (YMXS, SPEC.md 1.11).

## The tune file

A tune *maps a value* where a row sets a mapping column to 1 to 127, and
*cuts a source* where a row sets a cut column to 1 to 127. A table has a
group of four columns where the tune uses it:

| the tune | version | columns | the mapping columns | the cut columns |
|---|---|---|---|---|
| maps no value and cuts no source | 9 | 30 | absent | absent |
| maps a value and cuts no source | 10 | 34 | 30 to 33 | absent |
| cuts a source and maps no value | 11 | 34 | absent | 30 to 33 |
| maps a value and cuts a source | 11 | 38 | 30 to 33 | 34 to 37 |

The player reads each column at a displacement init patches for the
tune (68k/player/columns.S), so init patches the reads of a cut column at
30 + i or 34 + i by bit 5, and a frame reads it in the same cycles at
either. K of 3.3.4, the column count of the version, is 34 or 38 in
version 11, by bit 5.

The header of version 11 against version 10:

| | version 10 | version 11 |
|---|---|---|
| bit 5 of the effects used | 1 | 1 where the tune maps a value, 0 otherwise |
| bit 6 of the effects used | 0 | 1 |
| the long at 16 | where the transformation index begins | the same where bit 5 is 1, and 0 where it is 0 |
| the long at 20 | index entry 1 | where the cut index begins |
| the source index | at 20 | at 24 |

The offset of the source index follows the version alone, as it does
today: I of 3.1.1 is 16, 20 in version 10 and 24 in version 11. A reader
reads versions 7 to 11. The lookup cores, `YMXR_MAP=1`, read version 11
too, and init of every other core reports -1 for a tune whose effects byte
sets bit 6, as it does for bit 5 (BINARIES.md 1.5, 2.1).

The errors of the file (3.3.4) gain these, U the cut count, Q where the
cut index is, and G and L the first and the last row of cut N:

| condition | reported as |
|---|---|
| the version is 11 and bit 6 of the effects used is 0 | `version V with bit 6 of the effects used clear` |
| the version is 7 to 10 and bit 6 of the effects used is 1 | `version V with bit 6 of the effects used set` |
| the word U at Q, or the 8U bytes after it, end outside the file | `the cut index at Q is outside the file` |
| U is 0 or above 127 | `the file has U cuts, and a version 11 file has 1 to 127` |
| L of cut N is below its G | `cut N is rows G to L, and a cut ends at or after the row it begins at` |

## The tick under a cut

A start under cut 0 runs the handler it runs today, which the shape of
its source selects (68k/player/ticks.S). A start or a recut under cut 1
to 127 runs the counted handler of the source's column count at every
shape of source: TICKC, TICKW counted, or TICKL or TICKWL counted
through a transformation. A square or a source of one row under a cut
reads the rows of the cut, which the immediates of SQUARE and ONEROW
encode for the whole table alone.

The counted handler ends a pass where its counter of rows left reaches
0. Under a cut of rows F to L, with the place at row P, a start, a recut
and a place's reset write:

| operand | under the cut F to L |
|---|---|
| the place | row P, and row F where P is outside F to L |
| the rows left | L - P + 1 |
| the loop cell | row RR where F to L includes it, row F where it excludes it, and 0 where the source plays once |
| the rows of a pass | L + 1 less the row of the loop cell |

Step 3 of SPEC.md 5.1 then reads: under a cut 1 to 127 the row is the
last where the counter reaches 0, and the marker is unread; step 5 puts
the place at the row of the loop cell. The hook of a pending start
(4.8) is on the counted handler's end path, so the tick that reads row L
performs the start, as YMXS, SPEC.md 3.5.2 defines.

A marked source under a cut writes each byte whole, as the marked
handler does, so row R - 1, in a cut that includes it, writes its marker
bit to the register as it does today (3.2.2).

**The counter, not a compare.** YMXS's doc/cuts.md has encoding B
compare the place with the last row. The place is the operand of the
instruction that reads the row, an address a start patches, so a compare
reads that operand out of the instruction on every tick. The counted
handler decrements a word of the handler, its end path reloads it, and
the hook of a pending start is on that path. It costs 20 cycles more
than the marked handler on a tick that writes a row and 40 on the path
that loops, measured on the instances of two columns (performance.md, A
counted tick of two columns); TICKC's comment has the same 20 for one
column.

**Through a transformation.** In TICKL and TICKWL the end test, their
`counted` argument, is separate from the page, and the page follows the
encoding of the source: the key of a marked source is bits 6 to 0 of the
marker's column. A cut over a marked source through a transformation
runs a counted instance on the page of a marked source; milestone 5
settles whether the lookup lays one out today or gains it.

## The frame

What SPEC.md 4 gains:

- 4.1 step 4: the kept cut of each effect is 0.
- 4.2 step 2: the cut columns in version 11, 30 to 33 or 34 to 37.
- 4.3 step 3, a start: with bit 5 at 1 the place is the first row of the
  cut, row 0 under cut 0; with bit 5 at 0 it is its row number modulo R
  of the source started, and the first row of the cut where that row is
  outside the cut (YMXS, SPEC.md 3.4.3).
- 4.3 step 6, a place's reset with no start: the first row of the
  running cut, and the rows left of the table above.
- 4.3: the recut, after the remap of 4.3.1 and before step 4.
- 4.8, a deferred start: the arming reads the kept cut after the row,
  and the pending start keeps it. With bit 5 at 0, the tick that
  performs it puts the place at L + 1, L the last row of the cut that
  ended, modulo R of the source started, and at the first row of its cut
  where that row is outside it (YMXS, SPEC.md 3.5.3). Under cut 0, L + 1
  is R, the count of today.
- 4.2.1: a recut writes the place, the rows left, the loop cell, the rows
  of a pass and, where the handler changes, the vector as one operation
  against a tick, at level 7, as a start writes its operands.

## The record and the check

- 7.3: in version 11 an effect's entry ends `"cut":u`, the kept cut after
  the row, 0 to 127, and `e` includes each effect whose cut column the
  row sets.
- 6.4, a tune file read alone: a cut column above U; a stop that sets its
  cut column; a start or a recut under a cut that ends past the last row
  of the source it runs on.

## The converter

The table of doc/ymxs.md, What each column is written from, gains:

| the structure | the columns |
|---|---|
| the `cut` of a `Start` | the cut column where its value differs from the kept cut: 0 where the cut is the whole table of the source, and the number of the cut otherwise |
| `Recut` on a timer of the tune | the cut column at the number of its cut, the source and the mapping columns unset |
| `Recut` on a timer outside the tune | every column left unset (SPEC.md 6, rule 2(a)) |
| the tune's cuts | numbered 1 upward in first-use order, in a tune file of version 11 where a row sets a cut column to one |

A `Recut` writes the number of its cut where the cut is the whole table
of the running source too: after a deferred start the rows leave the
running source open (doc/ymxs.md, Kept values), and the counted handler
reads the same rows.

The schema tracks the kept, the running and the pending cut of each
effect as it tracks the mapping. At the repeat row the kept cut of an
effect that runs under a cut is unknown, and the first start or recut
after it writes the column, as rule 2(g) has for the mapping column.

What is an error gains a tune of more than 127 cuts with a start or a
recut on a timer of the tune under one: `the tune cuts through N cuts,
and a cut column numbers 127`.

## The files

Java is the reference and Go builds the release executables; ParityTest
compares the two byte for byte. The cut changes the files the mapping
of version 10 changed:

| part | Java | Go |
|---|---|---|
| the columns | `Columns` | `ymxr/columns.go` |
| the converter | `Schema` | `schema/schema.go` |
| the tune file | `Tune`, `TuneFile` | `ymxr/tune.go` |
| the record and the model of a frame | `Trace`, `Replay` | `trace/trace.go`, `trace/replay.go` |
| the check | `Check` | `check/check.go` |
| the bound tune, the core, the link and the multi file | `Binaries`, `Sndh`, `Link`, `Multi` | `sndh/sndh.go`, `sndh/link.go`, `internal/built/built.go` |

The player, under `68k/player/`: `equates.S`, the record's fields;
`effect.S`, the cut column read beside the mapping column in a piece
`cut<e>_1`, which a link leaves out of a tune that cuts no source
(doc/LINKER.md); `start.S`, the start, the place's reset and the recut
under a cut; `deferred.S`, the pending start's cut; `init.S` and
`switches.S`, the branches init aims in a tune of version 11. The cut is
assembled where `YMXR_MAP` is 1, and the handlers are the counted ones of
today.

The rig's model (`68k/test/emu/test_ymxr.py`, `Model`) gains the kept,
the running and the pending cut of each effect; `counted(i)` is true
under a cut 1 to 127, `left(i)` is L - P + 1, and `tick(i)` ends the
pass at L and loops to the row of the cut.

The documents: SPEC.md, the clauses above, and 8.5 gains a cut column
above U; BINARIES.md, a field of the bound tune where the cut index
begins, the source index 4 bytes on as version 10 moved it, the lookup
cores reading version 11 (2.1 to 2.3), and the multi file of version 11;
glossary.md, the terms of a cut; doc/ymxs.md and tools.md, with the tools
that write and read a cut; performance.md; RELEASES.md.

The conformance kit gains the tune files of YMXS's `doc/tunes/cut.json`
and of the YMXS kit's `cuts.json`, with their records.

## The dependency

`pom.xml` reads `org.ymx:ymxs` 0.8.0 and `go/go.mod`
`github.com/odipar/ymxs/go` v0.8.0. Until the columns encode a cut, the
converter reports a `Start` under a cut other than the whole table of its
source, and a `Recut` on a timer of the tune, as an error (doc/ymxs.md),
and leaves out a `Recut` on a timer outside the tune, as it leaves out a
`Remap` there. ParityTest's case of the YMXS kit,
`everyTuneOfTheYmxsKitConvertsTheSameInBothTrees`, reads the kit of the
YMXS checkout beside this one; that kit is version 8 since YMXS 0.8.0,
and the 0.7.1 this repository read before converts none of it, so the
case failed on main until the bump.

## Open for review

- The counted handler at every shape under a cut 1 to 127, where a
  square or a source of one row under a cut that equals its whole table
  could keep its handler. The counted handler is one path to verify on
  the rig, and a cut of the whole table of a running source is a
  `Recut` back to it.
- A cut of rows whose count is above 65,535: the counter is a word, as
  for a counted source today, so a writer reports such a cut, or the
  counter widens.

## Milestones

- [x] The dependency at YMXS 0.8.0 in both trees: the build, `mvn test`
      and the rig, and every file the tests compare as it is
- [x] SPEC.md, BINARIES.md and glossary.md define the column, the
      index, version 11, the tick under a cut and the record
- [x] Java: the converter, the tune file, the record and the check;
      doc/ymxs.md and tools.md; the rings of a table of 38 columns in
      SPEC.md 3.3.3; and the kit's tunes under a cut, SPEC.md 7.6
- [x] Go, byte for byte with Java
- [ ] The player, in the lookup cores, in four steps, each green on the
      rig and the suite:
  - [x] a source of one column through the identity: init reads version
        11, the cut column's read sites, the kept and the running cut in
        the effect's record, and the start, the recut and the place's
        reset under a cut on the counted handler of one column; the
        rig's model of the tick under a cut, and the kit's `cut` on the
        lookup cores
  - [x] the deferred start under a cut: the arming captures the cut, the
        installer places at L + 1 of the cut that ended, and a recut while
        a start is pending moves the running handler and the armed place;
        the kit's `cuts`
  - [x] sources of two and three columns, and a source through a
        transformation, on the counted instances; the rig's generator
        draws starts under a cut and recuts
  - [ ] the link and the tools: the cut's pieces, which a link keeps for
        a song that cuts a source alone, the SNDH tools' core for bit 6,
        BINARIES.md 1.5 and 2, and LINKER.md's table of the kit's tunes of
        version 11
- [ ] performance.md: a tick under a cut and a frame of a recut,
      measured on the rig, and the bytes of the core with the cut in
- [ ] This document goes, and YMXS's doc/cuts.md ticks its two YMXR
      items

## Settled

- A table has a group of four columns where the tune uses it: the cut
  columns at 30 to 33 where the tune maps no value, and at 34 to 37 after
  the mapping columns where it maps one.
- The lookup cores, `YMXR_MAP=1`, read version 11, and the release keeps
  32 cores; a separate switch would make 64.
- A tune of versions 7 to 10 runs the frame code of today on the lookup
  cores: init aims the branches at the paths that read the cut column
  where the effects byte sets bit 6. The head ors the column at 30 + i,
  the cut column where the tune maps no value; where it maps one, init
  aims the head's branch on the set bits at a fifth or. The record's
  update after a deferred start's switch copies the armed cut in every
  tune, two instructions (ymxr_take).
- A recut from cut 0 to cut 0 leaves the handler as it is; every other
  recut aims the counted handler, and a recut to cut 0 keeps it there
  until the next start.
- The core's descriptor records version 10 until the last step, so the
  SNDH tools bind a tune of version 11 once a link keeps the cut's code.
