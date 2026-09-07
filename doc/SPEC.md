# The YMXR format

A working draft. A player plays it under emulation and on an emulated
machine, and the numbers move as the corpus says.

A tune is a DTX table and the values outside it. Every value of a DTX
table takes the one width the table gives (R1.1), and this table's is a
byte, what one register takes. It holds 30 columns of the 32 R3.4 allows,
each one byte, so a row is 30 bytes.

---

## 1. The columns

Columns 0 to 13 reach R0 to R13, one a register, in the chip's numbering.
Columns 14 to 29 are the four effects, four columns each. A bit an entry
does not name is written as zero and read as nothing (R6.2).

| column | holds |
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

An effect is the schema's and a timer is the machine's. Section 2.3 says
which runs which. A tune running one effect sets columns 14 to 17 and
leaves 18 to 29 unset for its whole length.

### 1.1 The set bit

Bit 7 of a column is its set bit, the flag for the column's value (R3.6).
At 1 a player takes the value; at 0 it does not interpret the value's
bits, and they may hold anything.

Nine columns have no bit to spare, because what they reach takes the whole
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

The set bit flags the value, not the column. The five bits above are
beside another column's value rather than in their own, and are read on
every row, set bit or no (R3.6). Every other bit a column holds is part of
its value, read only where the row sets it.

One column gathering all twenty-one set bits would gather twenty-one
reasons to move, and the sum of them moves on nearly every row. A bit held
beside its own value moves only when that value's use does, and compresses
with it. A reserved 0 does the same for a column that fills its byte: the
column is 0 where the row leaves it, and the bit beside it moves only
where a tune sets the register to 0.

### 1.2 Tone period

Two columns a voice, one a register. The fine column is the divider's bits
7 to 0, the byte R0, R2 or R4 takes. It fills its byte, so 0 says the row
does not set it, and bit 6 of the coarse column says the row sets it to 0
(1.1).

The coarse column: bit 7 is the set bit, bit 6 says the fine column's 0 is
a value, and bits 3 to 0 are the divider's bits 11 to 8, which reach R1,
R3 or R5. Bits 5 and 4 are zero.

A period whose low byte is 0 sets the fine column to 0 and bit 6 of the
coarse column to 1 on the same row. Bit 6 is beside the coarse column's
value, not part of it (R3.5), so a player reads it on every row, and a row
may say the fine byte is 0 without setting the coarse. Across the 543
tunes the fine byte moves to 0 in 3,541, 2,697 and 13,970 frames of
3,789,212 for voices A, B and C, which `ym/measure.py` reads back.

### 1.3 Volume

Bit 7 is the set bit. Bits 3 to 0 give the level and bit 4 takes the level
from the envelope generator instead. The low five bits reach R8, R9 or R10.
Bits 6 and 5 are zero.

While an effect writes this voice's volume register, a row leaves this
column unset (section 6), so the frame's one write a row does not undo
what the effect's ticks write. One row sets it: the row that stops the
effect, whose write restores the voice's level. The row that starts a
square wave sets no level of its own, so the voice holds what the last
row left it at for the timer's first period and the first tick writes
the loud half. A row that starts a square where one runs on the voice
already does not move the place (1.9), so the tick after it falls a
whole period after the tick before it, and the level it writes is the
new source's.

### 1.4 Mixing

Bit 7 is the set bit. Bits 5 to 0 reach R7: bits 2 to 0 silence the tone of
voices A, B and C, and bits 5 to 3 silence the noise. Bit 6 is zero. R7's
own bits 7 and 6 are never written from this column (section 4).

### 1.5 Noise period

Bit 7 is the set bit. Bits 4 to 0 reach R6. Bits 6 and 5 are zero.

### 1.6 Envelope shape

| bits | gives |
|---|---|
| 7 | the set bit of this column |
| 6 | column 11's 0 is a value: the envelope period's fine byte is 0 (1.7) |
| 5 | column 12's 0 is a value: its coarse byte is 0 (1.7) |
| 3 to 0 | the shape, which reaches R13 |

Bit 4 is zero.

A row sets this column to write R13, and any write to R13 restarts the
envelope. A row that leaves it unset writes nothing, and the envelope runs
on.

A tune restarts a shape it is already sounding more often than it changes
to another. Such a row sets the column to the four bits it already held,
which is what R3.6 allows: the set bit says take this value, and says
nothing about the value having changed.

"Do not write" is the clear set bit, so no value of this column is
reserved for it. A row may set this column while an effect writes R13: the
row's write restarts the envelope beside the ticks' restarts, which is how
the dumps the corpus holds have it.

Bits 6 and 5 are beside this column's value, not part of it (R3.5), so a
player reads them on every row. A row may say a period byte is 0 without
writing R13.

### 1.7 Envelope period

Two columns, one a register. Column 11 is the divider's bits 7 to 0, the
byte R11 takes, and column 12 its bits 15 to 8, the byte R12 takes. Each
fills its byte, so 0 says the row does not set it, and a bit of column 13
says the row sets it to 0: bit 6 for column 11 and bit 5 for column 12
(1.1).

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
generator has. The divider treats 0 as 1, so 0 sounds at 7,812.5 Hz as
well. The two values are one pitch, which makes 0 the value to reserve: a
tune that needs the pitch writes 1 and loses nothing.

**Why the bits exist anyway.** Period 0 is a state the chip can hold, and
a tune converted from a register dump should convert without
approximation. The two bits keep it reachable, and every period with a
zero byte with it: 1 to 255 have a zero coarse byte, 256 and its multiples
a zero fine byte. Period 0 itself is sounded in almost no row: across the
543 tunes, 211 frames of 3,789,212 hold it with a voice taking its level
from the envelope, in 8 tunes.

**What the corpus says about the reserved value.** 2,312,208 of the
corpus's frames hold period 0, and all but the 211 above hold it with no
voice following the envelope. There the registers are untouched, which is
what "the row does not set these columns" says, so the reserved value and
the common case agree.

**Why the bits are not the whole answer.** Set bits for the two period
bytes held here, saying not set against set, would move whenever a byte
moved between set and unset: 101,444 changes on a column that has 24,099
of its own. The two bits beside move only where a row sets a byte to 0:
55,799 changes. `ym/measure.py` reads the three figures back, and
experiments.md has what the difference packs to.

### 1.8 Effect

Two columns: the target, and the source.

The target column: bit 7 is the set bit, and bits 6 to 0 give the target,
0 to 127, an index into 2.1's procedures.

The source column: bit 7 is the set bit, and bits 6 to 0 give the source,
0 to 127, an index into the tune's source index (3.1). Source 0 names no
source.

The two columns and the timer this effect runs on (2.3) are the
connection. A row setting the source column connects the three: the timer
runs the source it names on the target the player holds for this effect,
at the rate the two columns beside them give (1.9). The row moves the
place to the source's first row where bit 5 of the control column is set,
and starts a stopped timer through bit 6 (1.9, section 6).

A row setting source 0 stops the timer. It moves no place: the place
holds the row number the last tick read, and a source that starts on that
timer again reads from that number where the row leaves bit 5 clear.

The target the player holds is the one the row's target column gives, or
where the row leaves that column unset, the last one it took (R4.6). A
row setting the target column alone changes nothing until a source
starts.

Nothing else is in these columns. A source's values are its own rows
(2.2), so a level or a shape is a source rather than an operand, and two
levels are two sources.

A row may set the source column to the value it already holds, which is
what R3.6 allows: the source is resolved again, and bit 5 places it at
its first row.

A player resolves the source and the target when the source column is
set, and not again while the source runs.

A timer runs one effect, and holds one at a time. The schema has four
effects and the MFP has four timers, so four sound at once and there is no
fifth. A tune that needs another stops one of the four on the row that
starts it, which is the writer's work and not a player's (R3.3).

A register takes writes from as many timers as name it, and each of those
timers runs its own effect.

### 1.9 Effect rate

Two columns, one a register of the timer this effect runs on (2.3): the
control column and the count column. Each is laid out as its register is,
so a player masks its own bits off and writes the rest.

The count column is the timer count, bits 7 to 0, the byte the timer's
data register takes. It fills its byte, so 0 says the row does not set
it. The MFP reads a count of 0 as 256, and the schema does not reach that
count; a later version may assign a bit for it (R6.2).

The control column:

| bits | gives |
|---|---|
| 7 | the set bit |
| 6 | the timer's reset: the player stops it, writes the count and starts it |
| 5 | the place's reset: the timer's place in its source returns to the first row, which the next tick takes |
| 4, 3 | zero: the register's own bits, which pick an output's reset and modes no tune uses |
| 2 to 0 | the prescaler select as the register takes it: 1 for 4, 2 for 10, 3 for 16, 4 for 50, 5 for 64, 6 for 100, 7 for 200 |

Bits 4 to 0 are the register's, and a player writes them as the column
holds them; bits 7 to 5 are the player's, part of the column's value and
read where the row sets it. Select 0 stops a timer, which a row does
through the source column (1.8), so 0 is unassigned here (R6.2).

Timers C and D share a control register, C in bits 6 to 4 and D in bits 2
to 0. The player moves Timer C's select up by four, and writes its
timer's three bits leaving the other timer's as they are, as the frame's
write to R7 leaves the host's two (section 4).

The rate is 2,457,600 divided by the prescaler times the count.

Each rate column belongs to one effect, and 2.3 fixes the timer that
effect runs on, so the two registers a rate reaches are settled before a
tune plays (R3.5).

A player writes each column where the row sets it (section 4): the count
to the data register, the select to the control register, and the timer
runs on. A count written while the timer runs is taken when the running
count reaches zero, which moves the pitch without a break; a select
written while it runs neither stops nor reloads the running count, and
the new prescaler divides the 2,457,600 clock from the write on, so the
period the timer is counting is part at the old prescaler and part at
the new.

Bit 6 puts a stop before those writes and a start after them: the player
writes select 0, then the count, then the select. A count written to a
stopped timer is taken at once, and the select that follows starts the
timer from it, so the timer begins a whole period at the count. The
count and the select it writes are the row's, or where the row leaves a
column unset, the ones the player keeps (R4.6). A stopped timer is
started this way, on the row that starts its effect (section 6).

The place is a row number into the source's rows. Bit 5 moves it to 0,
and the next tick reads row 0; without the bit the row moves it nowhere,
through a change of rate and through a start. So a row that starts a
source without bit 5 leaves the number as it is, and it counts into the
new source's rows; a row that stopped the effect in between makes no
difference, since nothing but bit 5 and a tick moves the place. Two ticks
of a square are then a whole period apart across a start, and the level
the second writes is the new source's.

A note that bends sets the count column; a note that is struck sets bits
6 and 5 with it; a drum struck again at the rate it has sets bit 5 with
the select it has.

A row that leaves a rate column unset leaves the timer running at the
rate it has.

---

## 2. The maps

The format defines these once, and a tune states none of them. They make a
column's numbers the schema's rather than the machine's: a tracker maps its
own onto these (R3.2).

### 2.1 The targets

A target is a procedure. It takes a source's row and writes it, and the
schema names fourteen, one a YM2149 register. Each writes one register
from a row of one byte, which is the typical source (2.2); a procedure
taking a row of more columns, or of a wider one, is a later version's
(R6.2).

| target | the procedure |
|---|---|
| 0 to 13 | `setR0` to `setR13`, which write the row to that register |
| 14 to 127 | unassigned |

`setR7` writes bits 5 to 0 with R7's bits 7 and 6 as the host holds them,
which it does not change, as the frame's own write to R7 does (section 4).
Those two bits are the I/O port directions and are no tune's.

A source's last row has a marker in its bit 7 (3.2), so a source names
a target whose register takes seven bits or fewer and ignores that bit:
`setR1`, `setR3`, `setR5`, `setR6`, `setR8` to `setR10` and `setR13`. The
targets that take the whole byte, and `setR7` with its two host bits, are a
later version's for a source (R6.2).

A later version may name procedures that reach the MFP's own registers
(R6.2).

### 2.2 The sources

A source is a table: `R` rows and `C` columns, every value `W` bytes, 1, 2
or 4, and a row `RR` it repeats to once the last row is done. That is the
shape DTX's own table has (R1.1), one layer down. At every tick the source
advances one row, and its target takes that row.

Typically a source is one column of one byte, which is what 2.1's
procedures take. A source of two byte values serves a procedure taking a
word, a tone period being twelve bits, and more columns serve a source
driving more than one target. How an entry describes either is not yet
written (section 8).

A tune holds its sources and an index of them (3.1), and an effect column
names one. Source 0 names none.

Two effects may name one source. Each timer advancing it holds its own
place, so one starting or stopping leaves the other where it was.

The shapes a tune uses are shapes, not kinds the format names. One row
repeating writes its byte on every tick, which is what a sync buzzer asks
of R13. Two rows alternating a level and 0 are a square wave at the
timer's rate, which is a SID voice on a volume register. Many rows are a
recording or a cycle, and whether they repeat is the entry's to give.

Because a source holds its own values, two SID voices at two levels are
two sources, and an effect column names one of them rather than holding
the level.

A source's rows are its own. The DTX table's rows advance once a frame
(R1.3); a source's advance once a tick.

### 2.3 The timers

| effect | runs on |
|---|---|
| 0 | Timer A |
| 1 | Timer D |
| 2 | Timer B |
| 3 | Timer C |

The order is the order a tune takes them. Timer C is the operating system's
200 Hz clock, so a tune that runs effect 3 stops that clock and cannot be
hosted from a Timer C hook. A tune that runs three effects or fewer leaves
Timer C to the host.

---

## 3. Outside the table

A tune states these once. None is a column (R5.6).

| value | holds |
|---|---|
| the version | the version of this specification the tune was written for (R6.1) |
| the frame rate | how often the player is called, in Hz |
| effects used | bits 3 to 0: which of the four effects the tune ever runs, so a player claims those timers before the first row |
| the source index | where each source's table stands, in source-number order |
| the sources | one DTX1 table a source |

A player reads one row at a time (R1.3), so it cannot find which timers to
claim by reading ahead. That is why the effects a tune runs are stated here.
Section 3.3 gives the bytes.

### 3.1 The source index

An effect's source column gives a source number, 1 to 127, and the index's
entry at that number gives where the source's table stands. A source is a
DTX1 table (DTX, SPEC.md 2.2): one column of one-byte values, its rows
from byte 16 of the table, and its header giving `R` at bytes 4 to
7 and `RR` at bytes 10 to 13, each most significant byte first (R1.1),
`RR` equal to `R` where the source does not repeat, as DTX has it.

Source 0 has no entry. The index runs from source 1.

A start resolves the number through the index once, and the ticks advance
rows from there on; nothing is looked up while the effect runs (R3.3).

The repeat is what the last row does. Where `RR` is below `R` the next tick
takes row `RR`; where it is `R` the source has no next row (section 5). A
source of two rows repeating is a square wave, and one of many rows
playing once is a drum; a loop returns to `RR`, which is where the loop
begins and need not be the first row.

### 3.2 The rows

A source's rows hold what the register its target writes takes (R5.3).
Bound for a volume register, a recording's linear amplitudes convert to
the logarithmic levels the register takes, and the conversion is the
writer's work.

The last row of a source has bit 7 set, and no other row has. That bit
is the marker: a tick tests it after the write, so it costs the tick
nothing before, and the register takes the rest of the byte (2.1). What
the rest holds is the writer's: a square wave's is its silent half, its
first row being the loud one, so the first tick a timer's period after
the start writes the loud half; a drum's is a level the register is
left at until a row sets it again (1.3); and a source of one row is the
marker alone.

### 3.3 The tune file

| offset | bytes | gives |
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
holds the tune's tables and no code: a tool outside this specification
binds them with that reader into what a player takes, and the player's
own documents give that form. A reader reads a tune of its own version
and rejects another (R6.1).

---

## 4. The frame

One method serves both rates: a clock advances a table one row and calls a
procedure with that row. The frame clock advances the DTX table and calls
the procedure this section gives, which writes the columns of section 1. A
timer advances a source and calls its target, which writes one register
(section 5).

They differ in the table, the clock and the procedure, and in nothing
else. A source has the DTX table's shape - `R` rows, `C` columns, a repeat
at `RR` (2.2, R1.1) - and a target does what the procedure below does, for
one register rather than thirty columns.

A player advances the tune's table one row a frame, for every table the
tune runs, and writes the columns that row sets (1.1). A value the row does
not set is not interpreted, and a player that needs one on a later row
keeps it (R4.6) rather than looking for it in the buffer. The five bits
1.1 lists are beside their columns' values, and read on every row.

The effects go first, then the registers.

1. Columns 14, 18, 22 and 26, the targets. The player keeps the value for
   the effect's next start, and writes nothing.
2. Columns 15, 19, 23 and 27, the sources, each on its timer (2.3).
   Where the row sets that effect's control column with bit 6 (1.9), the
   player writes select 0 to the timer's control register first, whether
   or not the row sets the source column, so that no tick of the old
   rate takes the new source. A source of 0 stops the timer: select 0 to
   its control register. Any other is resolved through the index (3.1)
   on the target the player keeps, and is what the timer's ticks advance
   from here on.
3. Columns 16, 20, 24 and 28, the controls, each with the count column
   beside it, to its timer's two registers as 1.9 gives: the count,
   where the row sets it or bit 6 is set; then the select, where the row
   sets it or bit 6 is set; and the timer's place to its source's first
   row where bit 5 is set. Bit 6's stop is step 2's write, and the
   select here is the start after it. A row that sets the source column
   and leaves bit 5 clear moves no place: the row number the place holds
   counts into the new source's rows (1.9).
4. Columns 0 to 5, the tone periods, to R0 to R5.
5. Column 6 to R6, and columns 11 and 12 to R11 and R12, as 1.7's table
   reads them.
6. Columns 8, 9 and 10, the volumes, to R8, R9 and R10.
7. Column 7 to R7. The player writes bits 5 to 0 of the column with R7's
   bits 7 and 6 as the host holds them, which it does not change.
8. Column 13 to R13. Any write to R13 restarts the envelope.

The effects stop before the registers are written, and that order is what
makes a restore hold. The row that stops an effect sets its register's
column too (1.3); were the register written first, a tick of the effect
still running would write over it. A start needs no such order, because
the row that starts an effect leaves that register's column unset
(section 6).

A table whose `RR` is `R` has no row after its last (3.1). The frame
after the one that took the last row takes no row, writes nothing and
reports -1, and so does every frame after it; every other frame reports
0, which a player gives its caller (R2.4).

Steps 4 to 8 write where the row sets, each to registers the column itself
fixes, and none of them reads an index. Five tests cross columns: a zero
in a column 1.1 lists sends the player to the bit beside it. Step 2 is the
only step that reads the source index, and it reads it to start an effect,
not to place a value.

---

## 5. What a tick does

A tick is section 4's method at a timer's rate: it advances its source one
row and calls its target with that row (2.1, 2.2).

The last row is the marker (3.2), and the tick that writes it is the last
of the source's cycle. Where the source repeats, the next tick takes row
`RR`. Where it does not, the timer stops, and the register holds the last
row's value until a row of the table sets it (1.3).

A player does not read the DTX table between ticks.

---

## 6. What a writer does not do

These rules bind the writer. They are why a player writes a marked column
once and tests nothing.

1. While an effect runs on a volume register, a row leaves that
   register's column unset. A player writing steps 4 to 8 then needs no
   test: the row does not set the column. An effect on R13 holds nothing
   against the row (1.6). Where the row that stops one effect starts
   another on the same register, the column stays unset: the register is
   the second effect's from that row.
2. An effect a row starts is one the tune states in section 3, and a row
   sets no column of an effect the tune does not state.
3. A source and a target a column names are ones section 2 defines, and
   a row that starts an effect for the first time has set its target
   column, on that row or before.
4. Where two timers name one register, their writes are the writer's to
   order. A player writes what each tick gives it.
5. A row that sets the source column to a source sets bit 5 of the
   control column with it, unless the source it starts has the row count
   of the last source this effect ran on the target it holds, where the
   row may leave the bit clear and move no place (1.9); a row between
   them setting source 0 makes no difference. It sets bit 6
   where the timer is stopped, which a row setting source 0 leaves it.
   The place goes to the first row and the timer starts for those bits
   alone. A row that sets the source column to 0 leaves the control and
   count columns unset.
6. A row sets a rate column (1.9) on the row that starts its effect, or
   while the effect runs, and not before its first start. The row that
   starts an effect for the first time sets its count column, because
   the count the player keeps is 0 until a row sets it, bit 6 writes the
   kept count where the row leaves the column unset, and the MFP reads a
   count of 0 as 256 (1.9). A select written to a timer with no effect
   on it starts the timer with nothing to run.

---

## 7. What a reader reports

A reader is the role R2.4 gives beside the player: it reads a tune and
reports what it holds, and writes to no chip. It reports what the tune
states once, then what the frame procedure does, one entry a frame in
order and one for the frame after the last row, and nothing of what a
timer writes between frames (section 5): a tick's rate is the machine's,
and a reader has no machine. Of a tune whose version is not $0002 it
reports nothing (3.3, R6.1).

The report is lines of JSON, one entry a line. A line has no space in
it, its integers in decimal, its names in the order given here, and
`true` and `false` as JSON has them; the line ends with a line feed. The
first line gives what the tune states once, and each line after it one
frame:

    {"rate":50,"effects":2,"sources":[{"rows":[13,128],"repeat":0}]}
    {"result":0,"w":{"0":251,"1":4,"7":49},"e":{"1":{"target":10,"source":1,"select":1,"count":122,"timer":true,"place":true}}}
    {"result":0,"w":{},"e":{}}
    {"result":-1}

- `rate` is the frame rate and `effects` the effects used, as the file
  states them (3.3), and `sources` the sources in the index's order, 1
  upward, each its rows as the table holds them, the marker in the last
  row's bit 7 (3.2), and the row it repeats to, `R` where it does not
  (3.1).
- `result` is what the frame reports (section 4): 0, or -1 for the frame
  after the last row of a tune whose `RR` is `R`. That entry holds
  `result` alone, and the record ends with it.
- `w` holds the YM2149 registers steps 4 to 8 write, by number in
  ascending numeric order, `"2"` before `"10"`, and only those: a
  register the row does not set is absent, and a row that sets nothing
  gives `{}`. For a column with a set bit the value is bits 6 to 0
  masked to the register's bits: four for R1, R3, R5 and R13, five for
  R6, R8, R9 and R10, and six for R7, whose bits 7 and 6 are the host's
  (step 7) and are not reported. For a column that fills its byte the
  value is the byte where it is not 0, and 0 where the byte is 0 and the
  bit beside it is set (1.1); a fine byte 0 with the coarse column's bit
  6 set and its set bit clear writes R0 and not R1 (1.2).
- `e` holds the effects the row set a column of, by number 0 to 3 in
  ascending order: an effect whose target, source or control column has
  bit 7, or whose count column is not 0 (1.1, 1.9). A row that sets none
  gives `{}`. Each effect gives its state after steps 1 to 3, in this
  order: `target` as the player holds it after step 1, 0 to 127 as the
  column holds it, and 0 before a row sets it; `source`, the number the
  last row set, 0 to 127, whether or not its ticks have reached the
  marker (section 5); `select`, the column's three bits, and `count`,
  as the player keeps them (1.9): 0 before a row sets them, and kept
  through a stop after; `timer`, bit 6 of the row's control column where
  that column is set, and `place`, bit 5 the same, false where the
  column is not set.

The record of a tune is its first line and the entries of its frames from
the first, as many as are asked of the reader: one pass and the loop
once is `R` plus `R` minus `RR` frames for a tune that repeats, with `R`
and `RR` the table's, and the pass and the frame after it `R` plus 1 for
one that does not.

---

## 8. Not yet written

What a player does with a tune of a version it was not built for beyond
rejecting it (R6.1).

How a target takes a row of a DTX1 table of more than one column, or of
another width (2.2).
