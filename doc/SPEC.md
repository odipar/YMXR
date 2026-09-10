# The YMXR format

A working draft. A player plays it under emulation and on an emulated
machine, and the numbers move as the corpus says.

A tune is a DTX table and the values outside it. Every value of a DTX
table is the one width the table defines (R1.1), and this table's width is
a byte, the width of one register. It defines 30 columns of the 32 R3.4
allows, each one byte, so a row is 30 bytes.

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
not set it (R3.7). For five of them a bit of the column beside it, read on
every row, keeps 0 reachable as a value:

| column | its 0 is a value where |
|---|---|
| 0 | bit 6 of column 1 is 1 |
| 2 | bit 6 of column 3 is 1 |
| 4 | bit 6 of column 5 is 1 |
| 11 | bit 6 of column 13 is 1 |
| 12 | bit 5 of column 13 is 1 |

A player reads such a column and its bit together:

| the column | the bit | the row |
|---|---|---|
| not 0 | 0 or 1 | sets the register to the column's value |
| 0 | 0 | does not set it, and the register is not written |
| 0 | 1 | sets it to 0 |

The four timer counts have no such bit, and a count of 0, which the MFP
reads as 256, is not reachable (1.9).

The set bit flags the value, not the column. The five bits above sit
beside another column's value rather than inside their own, and are read on
every row, set bit or no (R3.6). Every other bit of a column is part of its
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

Bit 7 is the set bit. Bits 3 to 0 are the level and bit 4 reads the level
from the envelope generator instead. The low five bits reach R8, R9 or R10.
Bits 6 and 5 are zero.

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

Bit 7 is the set bit. Bits 5 to 0 reach R7: bits 2 to 0 silence the tone of
voices A, B and C, and bits 5 to 3 silence the noise. Bit 6 is zero. Bits 7
and 6 of R7 are never written from this column (section 4).

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

A row sets this column to write R13, and any write to R13 restarts the
envelope. A row that leaves it unset writes no register, and the envelope
runs on.

A tune restarts a shape it is already sounding more often than it changes
to another. Such a row sets the column to the four bits already in R13,
which R3.6 allows: the set bit marks a value to write, not a value that
changed.

"Do not write" is the clear set bit, so no value of this column is reserved
for it. A row may set this column while an effect writes R13: the row's
write restarts the envelope alongside the restarts from the ticks, as the
dumps in the corpus do.

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

**What 0 sounds like.** The envelope's rate is 2,000,000 divided by 256
times the period, so period 1 sounds at 7,812.5 Hz, the fastest sweep the
generator has. The divider reads 0 as 1, so 0 sounds at 7,812.5 Hz as well.
The two values are one pitch, which makes 0 the value to reserve: a tune
that needs the pitch writes 1 at no cost.

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
set it. The MFP reads a count of 0 as 256, and the schema does not reach
that count; a later version may assign a bit for it (R6.2).

The control column:

| bits | what they are |
|---|---|
| 7 | the set bit |
| 6 | the timer's reset: the player stops it, writes the count and starts it |
| 5 | the place's reset: the timer's place in its source returns to the first row, which the next tick reads |
| 4, 3 | zero: the register's bits, which select an output's reset and modes no tune uses |
| 2 to 0 | the prescaler select as the register reads it: 1 for 4, 2 for 10, 3 for 16, 4 for 50, 5 for 64, 6 for 100, 7 for 200 |

Bits 4 to 0 belong to the register, and a player writes them as the column
has them; bits 7 to 5 belong to the player, part of the column's value and
read where the row sets it. Select 0 stops a timer, which a row does
through the source column (1.8), so 0 is unassigned here (R6.2).

Timers C and D share a control register, C in bits 6 to 4 and D in bits 2
to 0. The player moves Timer C's select up by four, and writes the three
bits of its timer, leaving the other timer's as they are, as the frame's
write to R7 leaves the host's two (section 4).

The rate is 2,457,600 divided by the prescaler times the count.

Each rate column belongs to one effect, and 2.3 fixes the timer that
effect runs on, so the two registers a rate reaches are settled before a
tune plays (R3.5).

A player writes each column where the row sets it (section 4): the count
to the data register, the select to the control register, and the timer
runs on. A count written while the timer runs loads when the running
count reaches zero, which moves the pitch without a break; a select
written while it runs neither stops nor reloads the running count, and
the new prescaler divides the 2,457,600 clock from the write on, so the
period the timer is counting is part at the old prescaler and part at
the new.

Bit 6 puts a stop before those writes and a start after them: the player
writes select 0, then the count, then the select. A count written to a
stopped timer loads at once, and the select that follows starts the timer
from it, so the timer begins a whole period at the count. The count and
the select it writes come from the row, or where the row leaves a column
unset, from what the player keeps (R4.6).

Bit 6 is that stop, so it affects a running timer and not a stopped one. A
stopped timer starts on the select whether the row sets bit 6 or leaves it
clear: the count reaches it while it is stopped and the select starts it
from that count, the same start either way. So a writer that sets the bit
where the timer runs gets a whole period at the new count, and one that
leaves it clear gets the new count at the running count's next zero; where
the timer is stopped the two are identical, and where a writer cannot
determine which (section 6 rule 5), either bit is correct.

The place is a row number into the source's rows. Bit 5 moves it to 0, and
the next tick reads row 0; without the bit the row leaves it where it is,
through a change of rate and through a start. So a row that starts a source
without bit 5 leaves the number as it is, and it counts into the new
source's rows; a row that stopped the effect in between makes no
difference, since only bit 5 and a tick move the place. Two ticks of a
square are then a whole period apart across a start, and the level the
second writes belongs to the new source.

A bend sets the count column; a struck note sets bits 6 and 5 with it; a
drum struck again at the rate already running sets bit 5 with the select
already there.

A row that leaves a rate column unset leaves the timer running at its
current rate.

---

## 2. The maps

The format defines these once, and a tune repeats none of them. They make a
column's numbers the schema's rather than the machine's: a tracker maps its
terms onto these (R3.2).

### 2.1 The targets

A target is a procedure. It reads a source's row and writes it, and the
schema names fourteen, one a YM2149 register. Each writes one register from
a row of one byte, the typical source (2.2); a procedure reading a row of
more columns, or of a wider one, belongs to a later version (R6.2).

| target | the procedure |
|---|---|
| 0 to 13 | `setR0` to `setR13`, which write the row to that register |
| 14 to 127 | unassigned |

`setR7` writes bits 5 to 0 and leaves R7's bits 7 and 6 as the host set
them, as the frame's write to R7 does (section 4). Those two bits are the
I/O port directions and belong to the host.

A source's last row has a marker in its bit 7 (3.2), so a source names a
target whose register reads seven bits or fewer and ignores that bit:
`setR1`, `setR3`, `setR5`, `setR6`, `setR8` to `setR10` and `setR13`. The
targets that read the whole byte, and `setR7` with its two host bits,
belong to a later version for a source (R6.2).

A later version may name procedures that reach the MFP registers (R6.2).

### 2.2 The sources

A source is a table: `R` rows and `C` columns, every value `W` bytes, 1, 2
or 4, and a row `RR` it repeats to once the last row is done. That is the
shape of DTX's table (R1.1), one layer down. At every tick the source
advances one row, and its target reads that row.

A source is one column of one byte at this version, the row shape 2.1's
procedures read. A source of two byte values would serve a procedure
reading a word, a tone period being twelve bits, and more columns a source
driving more than one target; the table already records `C` and `W` in its
header (3.1), and the procedure that reads such a row belongs to a later
version (2.1, R6.2).

A tune keeps its sources and an index of them (3.1), and an effect column
names one. Source 0 names none.

Two effects may name one source. Each timer advancing it keeps a separate
place, so one starting or stopping leaves the other where it was.

The sound follows from the shape of the source, and the format defines no
kind. One row repeating writes its byte on every tick, which is what a sync
buzzer requires of R13. Two rows alternating a level and 0 are a square
wave at the timer's rate, a SID voice on a volume register. Many rows are a
recording or a cycle, and the index entry determines whether they repeat.

Since the values belong to the source, two SID voices at two levels are two
sources, and an effect column names one of them rather than encoding the
level.

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

A source's rows are values that fit the register its target writes (R5.3).
Bound for a volume register, a recording's linear amplitudes convert to the
logarithmic levels of that register, and the conversion is the writer's
work.

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
| 4 | 2 | the version, $0002 |
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

One method serves both rates: a clock advances a table one row and calls a
procedure with that row. The frame clock advances the DTX table and calls
the procedure this section defines, which writes the columns of section 1.
A timer advances a source and calls its target, which writes one register
(section 5).

They differ in the table, the clock and the procedure, and in no other
respect. A source has the DTX table's shape - `R` rows, `C` columns, a
repeat at `RR` (2.2, R1.1) - and a target does what the procedure below
does, for one register rather than thirty columns.

A player advances the tune's table one row a frame, for every table the
tune runs, and writes the columns that row sets (1.1). A value the row does
not set is not interpreted, and a player that requires one on a later row
keeps it (R4.6) rather than reading it back from the buffer. The five bits
1.1 lists are beside their columns' values, and read on every row.

The effects go first, then the registers.

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
   where the row sets it or bit 6 is set; then the select, where the row
   sets it or bit 6 is set; and the timer's place to its source's first
   row where bit 5 is set. Bit 6's stop is step 2's write, and the select
   here is the start after it. A row that sets the source column and
   leaves bit 5 clear moves no place: the row number in the place counts
   into the new source's rows (1.9).
4. Columns 0 to 5, the tone periods, to R0 to R5.
5. Column 6 to R6, and columns 11 and 12 to R11 and R12, as 1.7's table
   reads them.
6. Columns 8, 9 and 10, the volumes, to R8, R9 and R10.
7. Column 7 to R7. The player writes bits 5 to 0 of the column with R7's
   bits 7 and 6 left as the host set them.
8. Column 13 to R13. Any write to R13 restarts the envelope.

The effects stop before the registers are written, and that order
preserves a restore. The row that stops an effect sets its register's
column too (1.3); if the register were written first, a tick of the
still-running effect would overwrite it. A start requires no such order,
since the row that starts an effect leaves that register's column unset
(section 6).

A table whose `RR` is `R` has no row after its last (3.1). The frame after
the one that read the last row advances no row, writes no register and
reports -1, as does every frame after it; every other frame reports 0,
which a player returns to its caller (R2.4).

Steps 4 to 8 write where the row sets, each to registers the column itself
fixes, and none of them reads an index. Five tests cross columns: a zero in
a column 1.1 lists sends the player to the bit beside it. Step 2 is the
only step that reads the source index, and it reads it to start an effect
rather than to place a value.

---

## 5. What a tick does

A tick is section 4's method at a timer's rate: it advances its source one
row and calls its target with that row (2.1, 2.2).

The last row is the marker (3.2), and the tick that writes it is the last
of the source's cycle. Where the source repeats, the next tick reads row
`RR`. Where it does not, the timer stops, and the register keeps the last
row's value until a row of the table sets it (1.3).

A player does not read the DTX table between ticks.

---

## 6. The rules a writer satisfies

These rules bind the writer. They are why a player writes a marked column
once and performs no test.

1. While an effect runs on a volume register, a row leaves that register's
   column unset. A player writing steps 4 to 8 then requires no test: the
   row does not set the column. An effect on R13 leaves the row free
   (1.6). Where the row that stops one effect starts another on the same
   register, the column stays unset: the register is the second effect's
   from that row.
2. An effect a row starts is one the tune records in section 3, and a row
   sets no column of an effect outside that record.
3. A source and a target a column names are ones section 2 defines, and a
   row that starts an effect for the first time has set its target column,
   on that row or before.
4. Where two timers name one register, the writer fixes the order of their
   writes. A player writes each tick's value.
5. A row that sets the source column to a source sets bit 5 of the control
   column with it, unless the source it starts has the row count of the
   last source this effect ran on the same target, where the row may leave
   the bit clear and move no place (1.9); a row between them setting
   source 0 makes no difference. Bit 5 puts the place at the first row. It
   sets bit 6 where the timer is stopped, which a row setting source 0
   leaves it; where a writer cannot determine whether a source that plays
   once has run out by this row, either bit is correct, since a stopped
   timer starts on the select with the bit or without it and the bit
   affects a running timer alone (1.9). A row that sets the source column
   to 0 leaves the control and count columns unset.
6. A row sets a rate column (1.9) on the row that starts its effect, or
   while the effect runs, and not before its first start. The row that
   starts an effect for the first time sets its count column, because the
   count the player keeps is 0 until a row sets it, bit 6 writes the kept
   count where the row leaves the column unset, and the MFP reads a count
   of 0 as 256 (1.9). A select written to a timer with no effect on it
   starts the timer with no source to run.

---

## 7. What a reader reports

A reader is the role R2.4 defines beside the player: it reads a tune and
reports the result, and writes to no chip. It reports the tune's fixed
values once, then what the frame procedure does, one entry a frame in order
and one for the frame after the last row. What a timer writes between
frames is omitted (section 5): a tick's rate is a property of the machine,
and a reader runs on none. A tune whose version is not $0002 is rejected
without a report (3.3, R6.1).

The report is lines of JSON, one entry a line, without spaces, its integers
in decimal, its names in the order below, and `true` and `false` as JSON
defines them; each line ends with a line feed. The first line is the tune's
fixed values, and each line after it one frame:

    {"rate":50,"effects":2,"sources":[{"rows":[13,128],"repeat":0}]}
    {"result":0,"w":{"0":251,"1":4,"7":49},"e":{"1":{"target":10,"source":1,"select":1,"count":122,"timer":true,"place":true}}}
    {"result":0,"w":{},"e":{}}
    {"result":-1}

- `rate` is the frame rate and `effects` the effects used, as the file
  records them (3.3), and `sources` the sources in the index's order, 1
  upward, each with its rows as the table has them, the marker in the last
  row's bit 7 (3.2), and the row it repeats to, `R` where it does not
  (3.1).
- `result` is what the frame reports (section 4): 0, or -1 for the frame
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
