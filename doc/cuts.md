# Cuts in YMXR

YMXS 0.8.0 defines a cut on a start and a recut that sets it (YMXS,
SPEC.md 3.7; YMX issue 180). This document is the plan of encoding B
(YMXS, doc/cuts.md): the columns, the cut index, the tick handler under
a cut, the tune file of version 11, and the milestones in each tree.

## The column

Column 34 + i is the cut of effect i, as column 30 + i is its mapping.
Bit 7 is the set bit, bits 6 to 0 the cut number:

| byte | meaning |
|---|---|
| `$00` | unset: the kept cut is preserved |
| `$80` | set to 0, the whole table, rows 0 to R - 1 |
| `$81` to `$FF` | set to 1 to 127, a cut of the index |

A start or a recut that sets the column makes its value the *kept cut*,
initially 0 (the whole table). A stop preserves the kept cut, as it
preserves the kept mapping.

Read the cut column with the source column:

| source column | cut column | what happens |
|---|---|---|
| set to 1-127, a start | set | replace the kept cut with bits 6 to 0 |
| set to 1-127, a start | unset | reuse the kept cut |
| set to 0, a stop | set or unset | the kept cut is preserved |
| unset, and mapping set, a remap | set or unset | the cut column is unset (a remap leaves the cut) |
| unset, and mapping unset | set | a recut: replace the kept cut with bits 6 to 0 |

A recut is a row that sets the cut column and leaves the source
column and the mapping column unset. It replaces the kept cut of the
effect the timer runs, and the tick reads the new cut from the next
row of the source it reads. Where the place is outside the new cut,
the place is the cut's first row.

## The cut index

A version 11 tune file has a cut index of C entries, 1 to 127. Cut n
has a first row and a last row:

| offset | bytes | meaning |
|---|---|---|
| Q | 2 | C, the cut count, 1 to 127 |
| Q + 2 | 8C | cuts 1 to C: a long first, then a long last |

Each entry is 8 bytes. A writer numbers cuts in first-use order: the row
and the effect determine the order, as YMXS, SPEC.md 1.9 defines for
sources and transformations. Two starts or recuts under the same first
and last rows share one cut number. Cut 0 is the whole table and has no
index entry.

Start the index on a long boundary. The index follows the
transformation index where bit 5 of the effects used is set, and
otherwise the name, or the source index where the name is empty.

## The tune file

Version 11, `$000B`, is version 10 with four cut columns and a cut
index.

| what | version 10 | version 11 |
|---|---|---|
| columns | 34 | 38 |
| effects used bit 6 | 0 | 1 |
| bit 5, the mapping columns | 0 or 1 | 0 or 1 |
| the cut index | absent | present |

The header offset of the cut index pointer:

| bit 5 | bit 6 | transformation index pointer | cut index pointer | source index |
|---|---|---|---|---|
| 0 | 0 | absent | absent | 16 |
| 1 | 0 | 16 | absent | 20 |
| 0 | 1 | absent | 16 | 20 |
| 1 | 1 | 16 | 20 | 24 |

A writer writes version 11 where a row sets a cut column to 1 to 127:
where every start has the whole-table cut and there is no recut, the
tune is version 9 or 10 as before. A reader reads versions 7 to 11. A
player with cut support reads the same; a player without it reads 7 to
10. Version 11 without bit 6 set, or versions 7 to 10 with bit 6 set,
are errors of the file.

Mapping columns 30 to 33 are present in version 11 whether or not the
tune maps a value. Where no row selects a transformation, bit 5 is
clear, the transformation index is absent, and the mapping columns are
unset on every row: DTX3 packs each as a constant column of zero bytes.

## The tick under a cut

A tick of a timer running a source under a cut, in order (the changes
against SPEC.md 5.1):

1. Write each column of the source to its register, as 5.1 defines.
2. Write the marker's column, as 5.1 defines.
3. Test whether the row is the last of the cut: the place equals the
   cut's last row. The marker of step 2 is not read for this test.
4. Where the row is before the last: the place is the row after it.
5. Where the row is the last:
   - Where the cut includes the source's repeat row RR (first <= RR <=
     last): the place is RR.
   - Where the cut excludes RR: the place is the cut's first row.
   - For a source that plays once: stop the timer.
   - Where a start is pending: perform it.

Under the whole-table cut (0 to R - 1) the tick reads R rows, as a
tick of version 10 reads them: the marker test of a marked source and
the counter of a counted source both end at the same row as the compare.
The player uses the existing handler for the whole-table cut and a
cut-aware handler for a cut from the index.

### The handler

A handler of a source under a cut (`TICKCUT`) replaces the marker test
(`bmi.s`) with a compare of the place against the cut's last row. The
last-row address is in the handler's data, written by the frame code at
a start or a recut.

For a counted source (whole-byte columns): the counter of the existing
handler (`TICKC`) is replaced with the compare, since the marker is
absent and the counter is not needed under the cut.

| tick | today (SPEC.md 5.1) | under a cut |
|---|---|---|
| a row written, not the last | marker test: bmi.s, 2 cycles on N set by the write | compare: `cmp` place against last, bne.s |
| the row is the last, loop | read loop cell, write to place | read cut loop, write to place |
| the row is the last, stop | as today | as today |

The compare adds 6 cycles to the path that writes a row (the `cmp` and
the branch instruction). The loop path reads the cut's loop cell, which
the frame code writes at a start or a recut: the address of the cut's
repeat row (RR where the cut includes it, first otherwise) or 0 for
plays-once.

### The workspace

Each effect's record gains:

| field | bytes | meaning |
|---|---|---|
| cut number | 1 | the kept cut, 0 to 127 |
| cut last | 4 | the address of the cut's last row in the source |
| cut loop | 4 | the address of the cut's loop row, or 0 for plays-once at the last |
| cut first | 4 | the address of the cut's first row |

These are set at a start or a recut: the frame code reads the cut number
from the column, looks up the cut's first and last rows in the index (the
init resolves them, as it resolves sources), and writes the handler's
data.

## The model

Replay gains a kept cut per effect, initially the whole table. At a
start the model reads the cut from the structure; at a recut it reads
the new cut. The check verifies that the place stays inside the cut and
that the loop row is in the cut.

## The converter

Schema reads Cut from each Start and Recut from each Recut. Cuts are
collected and numbered in first-use order:

- The whole-table cut (0 to R - 1) is cut 0 and has no index entry.
- Each distinct cut other than the whole table is numbered 1 upward.
- Two cuts are equal where their first and last rows are equal.
- 127 cuts at most.

The cut column of a row is set where the row's cut differs from the kept
cut of the effect, as the mapping column is set where the mapping differs
from the kept mapping. At a start the converter writes the cut column where
the start's cut differs from the kept cut; at a recut it writes the cut
column.

## Changes by tree

### Java

| file | what changes |
|---|---|
| `Columns.java` | constants `CUT = 34`, `CUTS = 38`, `MOST_CUTS = 127`, `HAS_CUTS = 0x40` |
| `Schema.java` | read Cut and Recut from the structure, number cuts, write cut columns 34 to 37, build the cut index entries |
| `Tune.java` | `VERSION = 0x000B`, the cut index pointer in the header, `reads` accepts 7 to 11, `columns(11) = 38`, `indexAt` reads all three pointers |
| `TuneFile.java` | read and write the cut index, decode version 11 |
| `Replay.java` | a kept cut per effect, the tick under a cut |
| `Report.java` | the cut in a start's report, the recut operation |
| `Check.java` | validate cuts against sources |

### Go

| file | what changes |
|---|---|
| `ymxr/columns.go` | cut constants |
| `schema/schema.go` | cut numbering, cut columns, cut index entries |
| `ymxr/tune.go` | version 11, cut index read and write |
| `trace/replay.go` | the tick under a cut |
| `report/report.go` | cut reporting |
| `check/check.go` | cut validation |

### C#

The same changes as Java and Go.

### 68000

| file | what changes |
|---|---|
| `equates.S` | workspace fields for cut data |
| `ticks.S` | `TICKCUT`, `TICKCUTC`, `TICKCUTW`: handlers that compare the place against the cut's last row |
| `effect.S` | read the cut column in `EFFECT`, call cut-aware start and recut |
| `start.S` | resolve the cut at a start, patch the handler's data |
| `init.S` | resolve cut index entries into addresses |
| `handlers.S` | lay out cut-aware handlers |

### Documents

| file | what changes |
|---|---|
| `SPEC.md` | section 1 gains 1.11 (the cut column), section 3 gains the cut index (3.5) and the version 11 layout, section 5 gains the tick under a cut, section 8 drops the items this version defines |
| `performance.md` | a tick under a cut: the cycles and the comparison against a tick of today |
| `BINARIES.md` | version 11 bound tune, the cut index in the image |
| `RELEASES.md` | the release that ships this |

## The dependency

YMXS 0.8.0 defines Cut and Recut. The pom gains `org.ymx:ymxs:0.8.0`
and go.mod `github.com/odipar/ymxs/go v0.8.0`. Build YMXS 0.8.0 and
install it in the local Maven repository before the YMXR build.

## Milestones

1. Bump YMXS to 0.8.0 in Java and Go, verify the build.
2. SPEC.md: define the cut column, the cut index, version 11, the tick
   under a cut, and the reader's errors.
3. Java Schema and Tune: encode cuts and write version 11.
4. Java TuneFile and Replay: read the cut index, the tick under a cut.
5. Java Check, Report: cut validation and reporting.
6. Go: the same converter, tune, replay, check and report.
7. C#: the same.
8. 68000 player: cut-aware tick handlers, the cut column in the frame,
   the start's and the recut's cut data, the init's resolution of the
   cut index.
9. Conformance: a tune under a cut and one that moves a window.
10. Performance: measure and document the cost of a tick under a cut.
