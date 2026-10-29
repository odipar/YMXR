# A deferred start

YMXS version 5 adds a third flag to a start beside its two resets,
`deferred`, and defines the place across a start with `placeReset` clear
as its row number modulo the row count of the source started (YMXS,
SPEC.md 3.4.3 and 3.5). This document is the plan for YMXR to encode and
play both: what changes in the format, in the player, in the two tool
trees and in the rig, in the order the steps land. A figure marked
*counted* is from the 68000's instruction timings; one marked *measured*
is off the rig. When the last step lands, SPEC.md, BINARIES.md and
performance.md define what this plans, and this document is removed.

---

## 1. What YMXS defines

A deferred start on a timer running a source is *pending*: the source
plays to its last row, a source that repeats included, and the tick that
reads that row performs the start as a start with `deferred` clear. On a
stopped timer it is performed at its frame. A stop and a start performed
at a frame discard the pending start, a second deferred start replaces
it, and a retune sets the rate of the running effect and leaves it
pending (YMXS, SPEC.md 3.5.5).

With `placeReset` clear, a start sets the place to P modulo R, P its
number and R the row count of the source started. At the tick that
performs a pending start, P is the last row of the source before it.

---

## 2. The format

**2.1 The version.** A tune file is version 8. A player of version 7
reads bit 3 of a control column as 0 and performs a deferred start at its
frame, so the version changes; a version-8 player reads 7 and 8, since a
file of version 7 has bit 3 at 0 everywhere.

**2.2 Bit 3 of the control column** is `deferred` (SPEC.md 1.9.2, where
it is unassigned today). A writer sets the control column on every
deferred start, so a player reads the bit wherever a start is deferred.

**2.3 The place.** SPEC.md 1.8.4 reads: where a start leaves bit 5 at 0,
the place keeps its row number modulo the row count of the source
started. Rule 3(a) and clause 8.4 go: every start with bit 5 at 0 is
defined.

**2.4 The record.** A frame's entry of SPEC.md 7.3 has `"deferred":b`
after `"place":b`, bit 3 of a set control column and `false` where the
row leaves it unset. Every record of the conformance kit with an effect
changes, and the kit gains a tune with a deferred start.

---

## 3. The player

**3.1 What stays.** The five tick paths of performance.md keep their
instructions and their cycles: a row written and the place stepped, a
square, a source of one row, the marker with the place to row RR, and the
marker with the timer stopped. Every cost below is at a frame or at the
one tick that performs a pending start.

**3.2 Two banks of handlers.** A tick handler is patched for the source
it runs, one instance a shape an effect. A pending start needs a handler
patched for its source while the running one still runs, and the two may
be one shape, so each effect has each handler twice: bank 0 and bank 1,
bank 1 the same code at a fixed distance after bank 0. The effect's
record names the bank its vector points into. Bank 1 adds about 2,400
bytes, the handlers' size today (counted off the listing), and moves the
reach of a tick that reads its row through the program counter by as
much.

**3.3 Arming.** At the frame of a deferred start on a running timer, the
player patches the shape's handler in the other bank exactly as a start
does, its target, its place and its loop cell: the pending start is
*armed*, and every operand its first tick reads is in place before the
switch. The place is the last row of the running source modulo the row
count of the source armed, or row 0 with bit 5 set. The rate the start
sets, its count, its select and its timer reset, goes into the effect's
installer (3.5). The vector and the running handler are left as they
are.

**3.4 The hook.** Each handler has a stub after its last instruction, a
`jmp` to its effect's installer. Arming ends with one word written into
the running handler, at level 7 beside the test that the timer still
runs: the instruction that begins its end path becomes a `bra.s` to the
stub. One word is one bus cycle, so a tick reads the handler whole on
either side of it.

| shape | the word the hook replaces | what the stub runs |
|---|---|---|
| a row, counted or marked, one to three columns | the `tst` of the loop cell that begins the last row's path | the installer |
| a square | the `eori` that moves to the other row | the installer where the row written is the marker, and otherwise the `eori` and the end of interrupt |
| a source of one row | the instruction after the row's write | the installer |

The hook makes a source that repeats end at its last row, as YMXS 3.5.2
defines, and leaves every row before the last on the path it has today.

**3.5 The switch.** The installer performs the armed start at the tick
that reads the last row: it writes the hooked word back, stops the timer
where the start sets `timerReset`, writes the count and the select,
writes the armed handler's address into the vector, ends the interrupt
and returns. The next tick runs the armed handler on the new source's
first row to play.

| the switch | counted |
|---|---|
| Timer A or B, each with a separate control register, no reset | 134 cycles |
| the same, with `timerReset` | 170 cycles |
| Timer C or D, whose control register is shared, no reset | 182 cycles |
| the same, with `timerReset` | 218 cycles |
| today's last row, to row RR or stopped, for comparison | 88 or 98 cycles |

The switch costs 36 to 130 cycles more than the tick it replaces, once
for each deferred start, on that tick alone.

**3.6 The frame after a switch.** The effect's record describes the
running handler, which the frame reads at a start with bit 5 at 0 and
at a place's reset. Where a start is pending, the frame reads the vector
first, at level 7: where it points at the armed handler, the switch has
run, and the armed shape, bank, first rows and row count become the
record's. A row that sets an effect's target, source or control column
tests the pending flag, about 12 cycles (counted).

**3.7 Discarding and replacing.** A stop or a start performed at a frame,
on an effect with a pending start the switch has yet to run, writes the
hooked word back at level 7 before it acts. A second deferred start arms
a handler that is neither the running one nor the armed one where the
two banks have one; where both instances of its shape are in use, it arms
the armed one again at level 7, a window of the start's patching, about
500 cycles (counted), on that row alone. A retune writes the running
timer as it does today, and the pending start stays armed.

**3.8 A stopped timer.** A deferred start on a timer that is stopped,
or that stops while the start is armed, is performed at its frame as a
start with bit 3 at 0. The test reads the select bits of the timer's
control register at level 7, together with the hook's write.

**3.9 The place modulo a row count.** A start with bit 5 at 0 reads the
row number the place is at, from the running handler as today, and
reduces it modulo R of the source started where it is R or above: a
compare at every such start, and two `divu` (counted 280 cycles at most)
where the number is above R, at the frame alone. R comes from the DTX1
header of the source, and the effect's record keeps the row count of the
running source for the armed place of 3.3.

**3.10 The workspace.** The effect's record grows from 16 bytes to 64,
for the bank, the pending flag, the row count, and the armed handler,
shape, bank, first rows, row count and hooked word. `YMXR_FIXED` grows
from 2,120 to 2,312 bytes, field 20 of the core (BINARIES.md 2.2) with
it, and the core reads bound tunes of version 8.

---

## 4. The two tool trees

**4.1 The writer.** `Schema` sets bit 3 and the control column on a
deferred start, and bit 5 where the structure sets `placeReset`: the rule
that sets bit 5 on a start outside rule 3(a), and its exception at the
wrap, goes (doc/ymxs.md, The place's reset). A start after a deferred
start counts the timer as running where the pending start's source
repeats.

**4.2 The reader.** `Replay` and the Go reader record bit 3 as
`deferred` (2.4), and read versions 7 and 8.

**4.3 YMXS.** Both trees read the deferral through YMXS's structure, so
they need a YMXS release of version 5 of the structure: Maven `org.ymx:ymxs`
and the Go module `github.com/odipar/ymxs/go` at 0.5.0. Until it is
tagged, the branch builds against YMXS's main: a local install under
0.5.0-SNAPSHOT for Maven, and a pseudo-version of the main commit for Go.
The workflow clones YMXS at the tag pom.xml names, so it fails on the
branch until the release.

---

## 5. The rig and the tests

**5.1 The model.** `test_ymxr.py` models SPEC.md 4 and 5; it gains the
pending start, the hook's end of a source that repeats, the switch at the
last row and the place modulo R. It reads handler addresses in both
banks.

**5.2 Built tunes.** Each case is a tune written through YMXS's form and
played against the model, every tick's write and every place compared:

| case | what it reaches |
|---|---|
| a marked source after a marked one, both one column | the armed handler in the other bank of the running shape |
| a drum after a square, and a square after a drum | the square's hook, and the general handler armed from a square |
| a source of one row after a source of one row | the one-row hook |
| two and three columns, counted and marked | the hooks and the arming of 3.4 in every shape |
| a stop, and a start, before the switch | the hook written back |
| a retune before the switch | the start still pending, at the retuned rate until the switch |
| two deferred starts on one source | the replace of 3.7 |
| a deferred start on a stopped timer | the start at its frame |
| each on Timer C or D | the shared control register at the switch |
| a start with bit 5 at 0 across row counts 2, 3 and 16 | the place modulo R |

Each runs under the default core and the lean, absolute and monitor
switches.

**5.3 The figures.** PlayerTest reads the five tick paths against
performance.md, unchanged, and the switch against 3.5 once measured.

---

## 6. The order

1. YMXS 0.5.0 as a dependency (4.3).
2. The format: version 8, bit 3, the place modulo R, the record, the
   kit (section 2), the writer and the reader (4.1, 4.2).
3. The place modulo R in the player and the model (3.9).
4. The banks, the hook, arming and the switch, in the player and the
   model (3.2 to 3.8), with the built tunes of 5.2.
5. SPEC.md, BINARIES.md, performance.md and tools.md define what landed,
   and this document goes.
