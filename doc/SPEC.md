# The YMXR format

A working draft. A player plays it under emulation and on an emulated
machine, and the numbers move as the corpus says.

A tune is a DTX table and the values outside it. Every value of a DTX
table is the one width the table defines (R1.1), and this table's width is
a byte, the width of one register. It defines 30 columns of the 32 R3.4
allows, each one byte, so a row is 30 bytes.

## What this defines, and what YMXS does

[YMXS](https://github.com/odipar/YMXS) is the tune data structure. Its
SPEC.md defines what a tune is and what a player does with it: what each
register reaches on the two chips, what a target and a source are, how a
rate is reckoned from a prescaler and a count, what the two resets do, what
a frame does with a row and a tick with a source's row, and the rules a
writer satisfies.

This document defines one encoding of that structure and repeats none of
it. A rule that belongs to YMXS is cited as `(YMXS, SPEC.md 3.4)` and
written down there alone, as a rule that belongs to DTX is cited as `(DTX,
SPEC.md 2.2)`.

| here | there |
|---|---|
| the thirty columns and the bits of each (section 1) | the value in a column, and what it reaches on the chips |
| a column's number to a target, a source and a timer (section 2) | what a target, a source and a rate are |
| the DTX tables, the source index and the tune file (section 3) | - |
| the order and the tests a player performs on a row (section 4, 5) | what a frame and a tick do |
| what a writer satisfies in columns (section 6) | what a writer satisfies in the structure |
| what a reader of a tune file reports (section 7) | what a reader of the structure reports |

DTX and ST4 are this document's throughout. The table, its packing, its
reader and the file they stand in belong here, and YMXS defines none of
them.

---

## 1. The columns

Columns 0 to 13 reach R0 to R13, one a register, in the chip's numbering.
Columns 14 to 29 are the four effects, four columns each. A bit an entry
does not name is written as zero and has no meaning (R6.2).

| column | what it reaches |
|---|---|
| 0 | R0, voice A tone period, fine |
| 1 | R1, voice A tone period, coarse |
| 2 | R2, voice B tone period, fine |
| 3 | R3, voice B tone period, coarse |
| 4 | R4, voice C tone period, fine |
| 5 | R5, voice C tone period, coarse |
| 6 | R6, noise period |
| 7 | R7, mixing |
| 8 | R8, voice A volume |
| 9 | R9, voice B volume |
| 10 | R10, voice C volume |
| 11 | R11, envelope period, fine |
| 12 | R12, envelope period, coarse |
| 13 | R13, envelope shape |
| 14 | effect 0 target |
| 15 | effect 0 source |
| 16 | effect 0 timer control, Timer A's control register |
| 17 | effect 0 timer count, Timer A's data register |
| 18 to 21 | effect 1, the four above in the same order, on Timer D |
| 22 to 25 | effect 2, the same, on Timer B |
| 26 to 29 | effect 3, the same, on Timer C |

An effect belongs to the schema and a timer to the machine. Section 2.3
assigns one to the other. A tune running one effect sets columns 14 to 17 and
leaves 18 to 29 unset for its whole length.

### 1.1 The set bit

Bit 7 of a column is its set bit, the flag for the column's value (R3.6).
At 1 a player reads the value; at 0 it does not interpret the value's bits,
which are arbitrary.

Nine columns have no bit to spare, because the register fills the whole
byte: the three tone periods' fine columns, the two envelope period
columns and the four timer counts. Each reserves 0 for the row that does
not set it (R3.7), and for each a bit of the column beside it keeps 0
reachable as a value:

| column | its 0 is a value where |
|---|---|
| 0 | bit 6 of column 1 is 1 |
| 2 | bit 6 of column 3 is 1 |
| 4 | bit 6 of column 5 is 1 |
| 11 | bit 6 of column 13 is 1 |
| 12 | bit 5 of column 13 is 1 |
| 17 | bit 4 of column 16 is 1 |
| 21 | bit 4 of column 20 is 1 |
| 25 | bit 4 of column 24 is 1 |
| 29 | bit 4 of column 28 is 1 |

A player reads such a column and its bit together:

| the column | the bit | the row |
|---|---|---|
| not 0 | 0 or 1 | sets the register to the column's value |
| 0 | 0 | does not set it, and the register is not written |
| 0 | 1 | sets it to 0 |

The set bit flags the value, not the column. The nine bits above sit
beside another column's value rather than inside the value they mark, and
where each is read follows the column it belongs to. The five in a register
column are read on every row, set bit or no (R3.6). The four in a control
column are read where the row sets that column (1.9): every count a bend
moves through is 1 to 255, and a row reaching 0 is one that programs the
timer and sets both columns. Every other bit of a column is part of its
value, read only where the row sets it.

One column gathering all twenty-one set bits would gather twenty-one
reasons to move, and the sum of them moves on nearly every row. A bit
beside its value moves only when that value's use does, and compresses with
it. A reserved 0 does the same for a column that fills its byte: the column
is 0 where the row leaves it, and the bit beside it moves only where a tune
sets the register to 0.

### 1.2 Tone period

Two columns a voice, one a register. The fine column is the divider's bits
7 to 0, the byte written to R0, R2 or R4. It fills its byte, so 0 marks the
row that does not set it, and bit 6 of the coarse column marks the row that
sets it to 0 (1.1).

The coarse column: bit 7 is the set bit, bit 6 marks the fine column's 0 as
a value, and bits 3 to 0 are the divider's bits 11 to 8, which reach R1, R3
or R5. Bits 5 and 4 are zero.

A period whose low byte is 0 sets the fine column to 0 and bit 6 of the
coarse column to 1 on the same row. Bit 6 sits beside the coarse column's
value rather than inside it (R3.5), so a player reads it on every row, and
a row may mark the fine byte as 0 without setting the coarse. Across the 543
tunes the fine byte moves to 0 in 3,541, 2,697 and 13,970 frames of
3,789,212 for voices A, B and C, which `ym/measure.py` reads back.

### 1.3 Volume

Bit 7 is the set bit. Bits 4 to 0 are the value that reaches R8, R9 or R10,
which YMXS defines (YMXS, SPEC.md 2). Bits 6 and 5 are zero.

While an effect writes this voice's volume register, a row leaves this
column unset (section 6), so the frame's write does not undo what the
effect's ticks write. One row sets it: the row that stops the effect, whose
write restores the voice's level. The row that starts a square wave sets no
level, so the voice keeps the value the last row left for the timer's first
period, and the first tick writes the loud half. A row that starts a square
where one already runs on the voice does not move the place (1.9), so the
tick after it falls a whole period after the tick before it, and the level
it writes belongs to the new source.

### 1.4 Mixing

Bit 7 is the set bit. Bits 5 to 0 reach R7, whose bits YMXS defines (YMXS,
SPEC.md 2). Bit 6 is zero. Bits 7 and 6 of R7 are never written from this
column (section 4): they are the host's I/O port directions.

### 1.5 Noise period

Bit 7 is the set bit. Bits 4 to 0 reach R6. Bits 6 and 5 are zero.

### 1.6 Envelope shape

| bits | what they are |
|---|---|
| 7 | the set bit of this column |
| 6 | column 11's 0 is a value: the envelope period's fine byte is 0 (1.7) |
| 5 | column 12's 0 is a value: its coarse byte is 0 (1.7) |
| 3 to 0 | the shape, which reaches R13 |

Bit 4 is zero.

A row sets this column to write R13, and a write to R13 restarts the
envelope (YMXS, SPEC.md 2). A row that leaves it unset writes no register,
and the envelope runs on.

A row restarting the shape already sounding is more frequent than one
changing it (YMXS, SPEC.md 2), and such a row sets this column to the four
bits already in R13, which R3.6 allows: the set bit marks a value to write,
not a value that changed.

"Do not write" is the clear set bit, so no value of this column is reserved
for it. A row may set this column while an effect writes R13: the row's
write restarts the envelope alongside the restarts from the ticks, as the
dumps in the corpus do (section 6).

Bits 6 and 5 sit beside this column's value rather than inside it (R3.5),
so a player reads them on every row. A row may mark a period byte as 0
without writing R13.

### 1.7 Envelope period

Two columns, one a register. Column 11 is the divider's bits 7 to 0, the
byte written to R11, and column 12 its bits 15 to 8, the byte written to
R12. Each fills its byte, so 0 marks the row that does not set it, and a
bit of column 13 marks the row that sets it to 0: bit 6 for column 11 and
bit 5 for column 12 (1.1).

| column 11 | column 13 bit 6 | the row |
|---|---|---|
| not 0 | 0 or 1 | sets R11 to the column's value |
| 0 | 0 | does not set it, and R11 is not written |
| 0 | 1 | sets R11 to 0 |

Column 12 and bit 5 read the same way for R12.

Neither column has a set bit. The divider fills both bytes and leaves no
bit beside them, so a reserved value does that work: a player reads the
byte and writes where it is not zero, which costs the same test as a bit
would.

**Why 0 is the value reserved.** The divider reads 0 as 1, so the two
values are one pitch (YMXS, SPEC.md 2). A tune that needs that pitch writes
1 at no cost, and 0 is free to mark the row that sets neither byte.

**Why the bits exist anyway.** Period 0 is a setting the chip accepts, and
a tune converted from a register dump should convert without
approximation. The two bits keep it reachable, and with it every period
that has a zero byte: 1 to 255 have a zero coarse byte, 256 and its
multiples a zero fine byte. Period 0 itself sounds in almost no row: across
the 543 tunes, 211 frames of 3,789,212 have it with a voice reading its
level from the envelope, in 8 tunes.

**What the corpus says about the reserved value.** 2,312,208 of the
corpus's frames have period 0, and all but the 211 above have it with no
voice following the envelope. There the registers are untouched, which is
the meaning of "the row does not set these columns", so the reserved value
and the common case agree.

**Why the bits are not the whole answer.** Set bits for the two period
bytes, marking not set against set, would move whenever a byte moved
between set and unset: 101,444 changes on a column that changes 24,099
times. The two bits beside them move only where a row sets a byte to 0:
55,799 changes. `ym/measure.py` reads the three figures back, and
experiments.md has what the difference packs to.

### 1.8 Effect

Two columns: the target, and the source.

The target column: bit 7 is the set bit, and bits 6 to 0 are the target, 0
to 127, an index into 2.1's procedures.

The source column: bit 7 is the set bit, and bits 6 to 0 are the source, 0
to 127, an index into the tune's source index (3.1). Source 0 names no
source.

The two columns and the timer this effect runs on (2.3) are the
connection. A row setting the source column connects the three: the timer
runs the source it names on the target the player keeps for this effect,
at the rate of the two columns beside them (1.9). The row moves the place
to the source's first row where bit 5 of the control column is set. A
stopped timer starts on the select the row writes, bit 6 set or clear,
since a select runs an MFP timer; bit 6 is a stop before the writes, which
a running timer requires and a stopped one already has (1.9, section 6).

A row setting source 0 stops the timer. It moves no place: the place stays
at the row number the last tick read, and a source that starts on that
timer again reads from that number where the row leaves bit 5 clear.

The target the player keeps is the one in the row's target column, or where
the row leaves that column unset, the last one it read (R4.6). A row
setting the target column alone changes no effect until a source starts.

These columns define no other value. A source's values are its rows (2.2),
so a level or a shape is a source rather than an operand, and two levels
are two sources.

A row may set the source column to the value already there, which R3.6
allows: the source is resolved again, and bit 5 places it at its first
row.

A player resolves the source and the target when the source column is
set, and not again while the source runs.

A timer runs one effect at a time. The schema defines four effects and the
MFP has four timers, so four sound at once and there is no fifth. A tune
that requires another stops one of the four on the row that starts it, the
writer's work rather than a player's (R3.3).

A register accepts writes from as many timers as name it, and each of those
timers runs a separate effect.

### 1.9 Effect rate

Two columns, one a register of the timer this effect runs on (2.3): the
control column and the count column. Each is laid out as its register is,
so a player masks the player's bits off and writes the rest.

The count column is the timer count, bits 7 to 0, the byte written to the
timer's data register. It fills its byte, so 0 marks the row that does not
set it, and bit 4 of the control column beside it marks that 0 as a value
(1.1). The MFP counts 256 at a count of 0, so a row reaches that count by
setting both columns.

| the count column | control bit 4 | the row |
|---|---|---|
| not 0 | 0 or 1 | sets the count to the column's value |
| 0 | 0 | does not set it, and the timer counts on |
| 0 | 1 | sets it to 0, which the MFP counts 256 |

A player reads bit 4 where the row sets the control column, as it reads
bits 6 and 5. A row that leaves the control column unset sets a count of 1
to 255 or none, which is every count a bend moves through (1.9).

The control column:

| bits | what they are |
|---|---|
| 7 | the set bit |
| 6 | the timer's reset: the player stops it, writes the count and starts it |
| 5 | the place's reset: the timer's place in its source returns to the first row, which the next tick reads |
| 4 | the count column's 0 is a value: the count beside this one is 0, which counts 256 (1.1) |
| 3 | zero |
| 2 to 0 | the prescaler select as the register reads it: 1 for 4, 2 for 10, 3 for 16, 4 for 50, 5 for 64, 6 for 100, 7 for 200 |

Bits 2 to 0 belong to the register, and a player writes those three; bits
7 to 4 belong to the player, part of the column's value and read where the
row sets it. Bit 3 is the register's mode select, which reaches no tune,
and a player writes 0 to it. Select 0 stops a timer, which a row does
through the source column (1.8), so 0 is unassigned here (R6.2).

Timers C and D share a control register, C in bits 6 to 4 and D in bits 2
to 0. The player moves Timer C's select up by four, and writes the three
bits of its timer, leaving the other timer's as they are, as the frame's
write to R7 leaves the host's two (section 4).

The rate the select and the count come to, and what a write to either
does to a running timer, are YMXS's (YMXS, SPEC.md 3.3). Bit 6 is
`timerReset` and bit 5 is `placeReset`, which that document defines (YMXS,
SPEC.md 3.4). What follows is how a player reaches those from these two
columns.

Each rate column belongs to one effect, and 2.3 fixes the timer that
effect runs on, so the two registers a rate reaches are settled before a
tune plays (R3.5).

A player writes each column where the row sets it (section 4): the count
to the data register, the select to the control register, and the timer
runs on.

Bit 6 puts a stop before those writes and a start after them: the player
writes select 0, then the count, then the select. The count and the select
it writes come from the row, or where the row leaves a column unset, from
what the player keeps (R4.6). That sequence is what `timerReset` requires of a
running timer, and a stopped timer starts on the select either way, so
where a writer cannot determine which (section 6 rule 3) either bit is
correct.

Bit 5 moves the place to row 0, which the next tick reads. Without the bit
the row leaves the place where it is, through a change of rate and through
a start, so the row number counts into the rows of the source that runs
next; a row that stopped the effect in between makes no difference, since
only bit 5 and a tick move the place.

A row that leaves a rate column unset leaves the timer running at its
current rate.

---

## 2. The maps

The format defines these once, and a tune repeats none of them. They make a
column's numbers the schema's rather than the machine's: a tracker maps its
terms onto these (R3.2).

### 2.1 The targets

What a target is, and the fourteen this version names, are YMXS's (YMXS,
SPEC.md 3.1). This section numbers them for the target column (1.8).

| target | the procedure |
|---|---|
| 0 to 13 | `setR0` to `setR13`, which write the row to that register |
| 14 to 127 | unassigned |

A source's last row has a marker in its bit 7 (3.2), which is this
format's and not the structure's. So a source names a target whose register
reads seven bits or fewer and ignores that bit: `setR1`, `setR3`, `setR5`,
`setR6`, `setR8` to `setR10` and `setR13`. The targets that read the whole
byte, and `setR7` with its two host bits, belong to a later version for a
source (R6.2).

A later version may number procedures that reach the MFP registers (R6.2).

### 2.2 The sources

What a source is, what a source of each shape sounds, and that two effects
may run one source with a separate place each, are YMXS's (YMXS, SPEC.md
3.2). This section defines the table a source is written in.



A source is a DTX table: `R` rows and `C` columns, every value `W` bytes,
1, 2 or 4, and a row `RR` it repeats to once the last row is done (R1.1).
The index entry records `RR`, which determines whether the rows repeat.

A source is one column of one byte at this version, the row shape 2.1's
procedures read. A source of two byte values would serve a procedure
reading a word, a tone period being twelve bits, and more columns a source
driving more than one target; the table already records `C` and `W` in its
header (3.1), and the procedure that reads such a row belongs to a later
version (2.1, R6.2).

A tune keeps its sources and an index of them (3.1), and an effect column
names one, 1 to 127. Source 0 names none.

A source's rows are separate from the tune's. The DTX table's rows advance
once a frame (R1.3); a source's advance once a tick.

### 2.3 The timers

| effect | runs on |
|---|---|
| 0 | Timer A |
| 1 | Timer D |
| 2 | Timer B |
| 3 | Timer C |

The order is the order a tune claims them. Timer C is the operating
system's
200 Hz clock, so a tune that runs effect 3 stops that clock and cannot be
hosted from a Timer C hook. A tune that runs three effects or fewer leaves
Timer C to the host.

---

## 3. Outside the table

A tune records these once. None is a column (R5.6).

| value | what it is |
|---|---|
| the version | the version of this specification the tune was written for (R6.1) |
| the frame rate | how often the player is called, in Hz |
| effects used | bits 3 to 0: which of the four effects the tune ever runs, so a player claims those timers before the first row |
| the source index | where each source's table stands, in source-number order |
| the sources | one DTX1 table a source |

A player reads one row at a time (R1.3), so it cannot find which timers to
claim by reading ahead. That is why the effects a tune runs are recorded
here. Section 3.3 defines the bytes.

### 3.1 The source index

An effect's source column is a source number, 1 to 127, and the index
entry at that number is where the source's table stands. A source is a
DTX1 table (DTX, SPEC.md 2.2), its rows from byte 16 of the table, and its
header carrying `R` at bytes 4 to 7, `C` at bytes 8 and 9, `RR` at bytes 10
to 13 and `W` at byte 14, each most significant byte first (R1.1), `RR`
equal to `R` where the source does not repeat, as DTX defines it.

`C` is 1 and `W` is 1 at this version: one column of one-byte values, the
row shape 2.1's procedures read. A reader reads a source of that shape and
rejects one of another, as it rejects a tune of another version (R6.1). A
row of more columns or of another width belongs to a later version (2.1).

Source 0 has no index entry. The index runs from source 1.

A start resolves the number through the index once, and the ticks advance
rows from there on; no lookup happens while the effect runs (R3.3).

`RR` determines what the last row does. Where `RR` is below `R` the next
tick reads row `RR`; where it is `R` the source has no next row (section
5). A source of two rows repeating is a square wave, and one of many rows
playing once is a drum; a loop returns to `RR`, which is where the loop
begins and need not be the first row.

### 3.2 The rows

A source's rows are values that fit the register its target writes (R5.3,
YMXS, SPEC.md 3.2).

The last row of a source has bit 7 set, and no other row has. That bit is
the marker: a tick tests it after the write, so the tick pays for it only
then, and the register reads the rest of the byte (2.1). The rest belongs
to the writer: a square wave's is its silent half, its first row being the
loud one, so the first tick a timer's period after the start writes the
loud half; a drum's is the level the register keeps until a row sets it
again (1.3); and a source of one row is the marker alone.

### 3.3 The tune file

| offset | bytes | what it is |
|---|---|---|
| 0 | 4 | `YMXR` |
| 4 | 2 | the version, $0003 |
| 6 | 2 | the frame rate, in Hz |
| 8 | 1 | effects used |
| 9 | 1 | `S`, the source count, 0 to 127 |
| 10 | 2 | zero |
| 12 | 4 | where the DTX2 table begins |
| 16 | 4`S` | the source index: where the table of source 1 to `S` begins |
| | | the DTX2 table, on a long |
| | | the DTX1 tables, each on a long |

Every offset counts from the file's first byte, and a field of more than
one byte is most significant byte first.

The DTX2 table is the tune's table as a complete DTX file (DTX, SPEC.md 1
and 2.3), its header and its payload, which DTX's reader reads. The file
contains the tune's tables and no code: a tool outside this specification
binds them with that reader into the layout a player reads, and that
layout is defined in the player's documents. A reader reads a tune of this
version and rejects another (R6.1).

---

## 4. The frame

What a frame does is YMXS's: the effects first and then the registers, in
the register order step 4 to step 8 below follow, and why that order
preserves a restore (YMXS, SPEC.md 4). This section is the columns a player
reads to do it, and the tests it performs on them.

One method serves both rates: a clock advances a table one row and calls a
procedure with that row. The frame clock advances the DTX table and calls
the procedure this section defines, which writes the columns of section 1.
A timer advances a source and calls its target, which writes one register
(section 5). They differ in the table, the clock and the procedure, and in
no other respect: a source has the DTX table's shape - `R` rows, `C`
columns, a repeat at `RR` (2.2, R1.1) - and a target does what the
procedure below does, for one register rather than thirty columns.

A player advances the tune's table one row a frame, for every table the
tune runs, and writes the columns that row sets (1.1). A value the row does
not set is not interpreted, and a player that requires one on a later row
keeps it (R4.6) rather than reading it back from the buffer. The five bits
1.1 lists are beside their columns' values, and read on every row.

1. Columns 14, 18, 22 and 26, the targets. The player keeps the value for
   the effect's next start, and writes no register.
2. Columns 15, 19, 23 and 27, the sources, each on its timer (2.3).
   Where the row sets that effect's control column with bit 6 (1.9), the
   player writes select 0 to the timer's control register first, whether
   or not the row sets the source column, so that no tick of the old rate
   reads the new source. A source of 0 stops the timer: select 0 to its
   control register. Any other is resolved through the index (3.1) on the
   target the player keeps, and the timer's ticks advance it from here
   on.
3. Columns 16, 20, 24 and 28, the controls, each with the count column
   beside it, to its timer's two registers as 1.9 defines: the count,
   where the row sets it, where bit 4 marks its 0 as a value, or where
   bit 6 is set; then the select, where the row sets it or bit 6 is set;
   and the timer's place to its source's first row where bit 5 is set.
   Bit 6's stop is step 2's write, and the select here is the start after
   it. A row that sets the source column and
   leaves bit 5 clear moves no place: the row number in the place counts
   into the new source's rows (1.9).
4. Columns 0 to 5, the tone periods, to R0 to R5.
5. Column 6 to R6, and columns 11 and 12 to R11 and R12, as 1.7's table
   reads them.
6. Columns 8, 9 and 10, the volumes, to R8, R9 and R10.
7. Column 7 to R7. The player writes bits 5 to 0 of the column with R7's
   bits 7 and 6 left as the host set them.
8. Column 13 to R13, which restarts the envelope (YMXS, SPEC.md 2).

A table whose `RR` is `R` has no row after its last (3.1). The frame after
the one that read the last row advances no row, writes no register and
reports -1, as does every frame after it; every other frame reports 0,
which a player returns to its caller (R2.4, YMXS, SPEC.md 4).

Steps 4 to 8 write where the row sets, each to registers the column itself
fixes, and none of them reads an index. Five tests cross columns: a zero in
a column 1.1 lists sends the player to the bit beside it. Step 2 is the
only step that reads the source index, and it reads it to start an effect
rather than to place a value.

---

## 5. What a tick does

What a tick does is YMXS's (YMXS, SPEC.md 5). Here it is section 4's method
at a timer's rate: it advances its source one row and calls its target with
that row (2.1, 2.2).

This format marks the end of a cycle in the row itself. The last row is
the marker (3.2), so the tick that writes it tests bit 7 after the write
rather than counting rows against `R`.
Where the source repeats, the next tick reads row `RR` (3.1); where it does
not, the timer stops.

A player does not read the DTX table between ticks.

---

## 6. The rules a writer satisfies

The rules themselves are YMXS's five (YMXS, SPEC.md 6). Below are those
rules in the columns that encode them, which is why a player writes a
marked column once and performs no test. Each entry names the YMXS rule it
encodes.

1. **The volume column of a register an effect writes stays unset** (YMXS
   rule 1). A player writing steps 4 to 8 then requires no test: the row
   does not set the column. An effect on R13 leaves the row free (1.6).
   Where the row that stops one effect starts another on the same register,
   the column stays unset: the register is the second effect's from that
   row.
2. **An effect a row sets a column of is one the tune records** in section
   3, and a row sets no column of an effect outside that record. A source
   and a target a column names are ones section 2 numbers, and a row that
   starts an effect for the first time has set its target column, on that
   row or before. The structure settles these where a row is built, so
   YMXS lists neither and they bind a writer of columns alone.
3. **A row that sets the source column sets bit 5 with it** (YMXS rules 3
   and 4), unless the source it starts has the row count of the last source
   this effect ran on the same target, where the row may leave the bit
   clear and keep the phase (1.9); a row between them setting source 0
   makes no difference. It sets bit 6 where the timer is stopped, which a
   row setting source 0 leaves it. A row that sets the source column to 0
   leaves the control and count columns unset.
4. **A row sets a rate column on the row that starts its effect** (YMXS
   rule 5), or while the effect runs, and not before its first start. The
   row that starts an effect for the first time sets its count column,
   because the count the player keeps is 0 until a row sets it, and bit 6
   writes the kept count where the row leaves the column unset (1.9).
5. **A row that sets a count of 0 sets bit 4 of the control column with
   it.** The count column reserves 0 for the row that does not set it, and
   bit 4 is what marks that 0 as the value the MFP counts 256 for (1.1,
   1.9). A row that leaves the control column unset sets a count of 1 to
   255 or none.

---

## 7. What a reader reports

The role, the shape of the record and the conventions its lines follow are
YMXS's (YMXS, SPEC.md 7): a reader writes to no chip, reports the fixed
values on the first line and one entry a frame after it, omits what a timer
writes between frames, and writes lines of JSON without spaces, integers in
decimal, names in the order the document fixes, and a line feed ending each
line.

A reader there reports the structure and one here reports a tune file, so
the names and the values below are this document's, and a caller reads one
record or the other. A tune whose version is not $0003 is rejected without
a report (3.3, R6.1). The names, in order:

    {"rate":50,"effects":2,"sources":[{"rows":[13,128],"repeat":0}]}
    {"result":0,"w":{"0":251,"1":4,"7":49},"e":{"1":{"target":10,"source":1,"select":1,"count":122,"timer":true,"place":true}}}
    {"result":0,"w":{},"e":{}}
    {"result":-1}

- `rate` is the frame rate and `effects` the effects used, as the file
  records them (3.3), and `sources` the sources in the index's order, 1
  upward, each with its rows as the table has them, the marker in the last
  row's bit 7 (3.2), and the row it repeats to, `R` where it does not
  (3.1).
- `result` is the frame's report (section 4): 0, or -1 for the frame
  after the last row of a tune whose `RR` is `R`. That entry has `result`
  alone, and the record ends with it.
- `w` lists the YM2149 registers steps 4 to 8 write, by number in ascending
  numeric order, `"2"` before `"10"`, and only those: a register the row
  does not set is absent, and a row that sets none is `{}`. For a column
  with a set bit the value is bits 6 to 0 masked to the register's bits:
  four for R1, R3, R5 and R13, five for R6, R8, R9 and R10, and six for R7,
  whose bits 7 and 6 belong to the host (step 7) and are not reported. For
  a column that fills its byte the value is the byte where it is not 0, and
  0 where the byte is 0 and the bit beside it is set (1.1); a fine byte 0
  with the coarse column's bit 6 set and its set bit clear writes R0 and
  not R1 (1.2).
- `e` lists the effects the row set a column of, by number 0 to 3 in
  ascending order: an effect whose target, source or control column has bit
  7, or whose count column is not 0 (1.1, 1.9). A row that sets none is
  `{}`. Each effect reports its setting after steps 1 to 3, in this order:
  `target` as the player keeps it after step 1, 0 to 127 as the column has
  it, and 0 before a row sets it; `source`, the number the last row set, 0
  to 127, whether or not its ticks have reached the marker (section 5);
  `select`, the column's three bits, and `count`, as the player keeps them
  (1.9): 0 before a row sets them, and kept through a stop after; `timer`,
  bit 6 of the row's control column where that column is set, and `place`,
  bit 5 the same, false where the column is not set.

The record of a tune is its first line and the entries of its frames from
the first, as many as the caller requires: one pass plus one loop is `R`
plus `R` minus `RR` frames for a tune that repeats, with `R` and `RR` the
table's, and the pass and the frame after it, `R` plus 1, for one that does
not.

---

## 8. Not yet written

What a player does with a tune of a version it was not built for beyond
rejecting it (R6.1).
