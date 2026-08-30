# The YMXR format

A working draft. It fixes a column set so the rest can be written against
something. Every number here moves until DTX exists and a reader reads a
tune back.

A tune is a DTX table and the values that sit outside it. The table holds
18 columns of the 32 R3.4 allows, and 30 bytes a row.

---

## 1. The columns

A column of 2 or 4 bytes is most significant byte first. A bit an entry
does not name is written as zero and read as nothing (R6.2).

| column | bytes | holds |
|---|---|---|
| 0 | 2 | voice A tone period |
| 1 | 1 | voice A volume |
| 2 | 2 | voice B tone period |
| 3 | 1 | voice B volume |
| 4 | 2 | voice C tone period |
| 5 | 1 | voice C volume |
| 6 | 1 | mixing |
| 7 | 1 | noise period |
| 8 | 1 | envelope shape |
| 9 | 2 | envelope period |
| 10 | 2 | effect 0 |
| 11 | 2 | effect 0 rate |
| 12 to 13 | 4 | effect 1, the two above in the same order |
| 14 to 15 | 4 | effect 2, the same |
| 16 to 17 | 4 | effect 3, the same |

An effect is the schema's and a timer is the machine's. Section 2.3 says
which runs which. A tune running one effect sets columns 10 and 11 and
leaves 12 to 17 unset for its whole length.

### 1.1 The set bit

Bit 7 of a column's first byte is its set bit, the flag for the column's
value (R3.6). A column is most significant byte first, so that one rule is
the top bit at every width: bit 7 of a byte and bit 15 of a word. At 1 a
player takes the value; at 0 it does not interpret the value's bits, and
they may hold anything.

The set bit flags the value, not the column. One bit in the schema sits
beside a value rather than in it, and is read on every row, set bit or no:
bit 6 of the envelope shape, which settles what a zero in the envelope
period means (1.6, 1.7). Every other bit a column holds is part of its
value, read only where the row sets it.

One column has no bit to spare, and reserves a value instead. The envelope
period fills its word, so 0 says the row does not set it (1.7).

One column gathering all seventeen set bits would gather seventeen
reasons to move, and the sum of them moves on nearly every row. A bit held
beside its own value moves only when that value's use does, and compresses
with it.

### 1.2 Tone period

Bit 15 is the set bit. Bits 11 to 0 give the voice's divider: bits 7 to 0
reach R0, R2 or R4, and bits 11 to 8 reach R1, R3 or R5. Bits 14 to 12 are
zero.

### 1.3 Volume

Bit 7 is the set bit. Bits 3 to 0 give the level and bit 4 takes the level
from the envelope generator instead. The low five bits reach R8, R9 or R10.
Bits 6 and 5 are zero.

While an effect writes this voice's volume register, a row leaves this
column unset (section 6), so the frame's one write a row does not undo what
the effect's ticks write. The row that stops the effect sets it again, and
that write restores the voice's level.

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
| 6 | the envelope period is 0 (1.7) |
| 3 to 0 | the shape, which reaches R13 |

Bits 5 and 4 are zero.

A row sets this column to write R13, and any write to R13 restarts the
envelope. A row that leaves it unset writes nothing, and the envelope runs
on.

A tune restarts a shape it is already sounding more often than it changes
to another. Such a row sets the column to the four bits it already held,
which is what R3.6 allows: the set bit says take this value, and says
nothing about the value having changed.

"Do not write" is the clear set bit, so no value of this column is
reserved for it. While an effect writes R13, a row leaves this column unset.

Bit 6 is beside this column's value, not part of it (R3.5), so a player
reads it on every row. A row may say the period is 0 without writing R13.

### 1.7 Envelope period

Bits 15 to 0 give the envelope generator's divider: bits 7 to 0 reach R11
and bits 15 to 8 reach R12.

A zero in this column does not set the period, or sets it to 0. Bit 6 of
column 8 settles which.

| column 9 | column 8 bit 6 | the row |
|---|---|---|
| not 0 | 0 or 1 | sets the period to the column's value |
| 0 | 0 | does not set the period, and R11 and R12 are not written |
| 0 | 1 | sets the period to 0 |

This column has no set bit. The divider fills the word and leaves no bit
beside it, so a reserved value does that work: a player reads the word and
writes where it is not zero, which costs the same test as a bit would.

**What 0 sounds like.** The envelope's rate is 2,000,000 divided by 256
times the period, so period 1 sounds at 7,812.5 Hz, the fastest sweep the
generator has. The divider treats 0 as 1, so 0 sounds at 7,812.5 Hz as
well. The two values are one pitch, which makes 0 the value to reserve: a
tune that needs the pitch writes 1 and loses nothing.

**Why bit 6 exists anyway.** Period 0 is a state the chip can hold, and a
tune converted from a register dump should convert without approximation.
Bit 6 keeps the case reachable at the cost of one bit that is 1 in almost
no row: across the 543 tunes, 211 frames of 3,789,212 hold period 0 with a
voice taking its level from the envelope, in 8 tunes.

**What the corpus says about the reserved value.** 2,312,208 of the
corpus's frames hold period 0, and all but the 211 above hold it with no
voice following the envelope. There the registers are untouched, which is
what "the row does not set this column" says, so the reserved value and the
common case agree.

**Why the bit is not the whole answer.** Two bits qualifying the period
here, saying not set against set against zero, would move whenever the
period moved between set and unset: 60,973 changes on a column that has
24,099 of its own. Bit 6 moves only when a tune sounds period 0, which is
211 frames. `ym/measure.py` reads all four figures back.

### 1.8 Effect

| bits | gives |
|---|---|
| 15 | the set bit |
| 14 to 8 | the target, 0 to 127, an index into 2.1's procedures |
| 7 to 0 | the source, 0 to 255, an index into the tune's source index (3.1) |

The column is the connection: a source, a target, and the timer this
effect runs on (2.3). A row setting it connects the three, and the rate
column beside it says how fast the timer turns them (1.9). Source 0 names
no source, and a row setting it stops what the timer ran.

The first byte holds the set bit and the target, the second the source
whole.

Nothing else is in this column. A source's values are its own rows (2.2),
so a level or a shape is a source rather than an operand, and two levels
are two sources.

A row sets this column to start what it names. A row that starts the same
effect a second time sets the value it already held, which is what R3.6
allows.

A player resolves both bytes when a source starts, and not again while it
runs.

A timer runs one effect, and holds one at a time. The schema has four
effects and the MFP has four timers, so four sound at once and there is no
fifth. A tune that needs another stops one of the four on the row that
starts it, which is the writer's work and not a player's (R3.3).

A register takes writes from as many timers as name it, and each of those
timers runs its own effect.

### 1.9 Effect rate

| bits | gives |
|---|---|
| 15 | the set bit |
| 14 | how the timer takes the rate |
| 10 to 8 | the prescaler: 0 for 4, 1 for 10, 2 for 16, 3 for 50, 4 for 64, 5 for 100, 6 for 200 |
| 7 to 0 | the count, 0 read as 256 |

The first byte holds the set bit, the rate's manner and the prescaler; the
second holds the count whole.

The rate is 2,457,600 divided by the prescaler times the count.

Each rate column belongs to one effect, and 2.3 fixes the timer that effect
runs on, so the control and data registers a rate reaches are settled
before a tune plays (R3.5).

Bit 14 says what a player does with a rate this column gives while an
effect already runs. At 0 the player writes the count, and the running
period ends at the length it began with, which moves the pitch without a
break. At 1 the player stops the timer, writes the rate and starts it, so
the period begins again and the source begins its cycle. A note that bends
takes 0 and a note that is struck takes 1.

A row restarts an effect by setting its effect column, not this one. A row
that leaves this column unset leaves the timer running at the rate it has.

A start reads the rate the player last took for that timer, which the
player keeps (R4.6).

Bits 13, 12 and 11, and prescaler code 7, are unassigned (R6.2).

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

A later version may name procedures that reach the MFP's own registers
(R6.2).

### 2.2 The sources

A source is a table: `R` rows and `C` columns, a column 1, 2 or 4 bytes
wide, and a row `RR` it repeats to once the last row is done. That is the
shape DTX's own table has (R1.1), one layer down. At every tick the source
advances one row, and its target takes that row.

Typically a source is one column, one byte wide, which is what 2.1's
procedures take. A wider column serves a procedure taking more than a byte,
a tone period being twelve bits, and more columns serve a source driving
more than one target. How an entry describes either is not yet written
(section 7).

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

| value | bytes | holds |
|---|---|---|
| frame rate | 2 | how often the player is called, in Hz |
| effects used | 1 | bits 3 to 0: which of the four effects the tune ever runs, so a player claims those timers before the first row |
| the source index | 8 an entry | one entry a source, in source-number order |
| the sources | | the rows the index addresses |

A player reads one row at a time (R1.3), so it cannot find which timers to
claim by reading ahead. That is why the effects a tune runs are stated here.

### 3.1 The source index

An effect column's source byte gives a source number, and the entry at
that number gives where its rows are, how many, and what the last one
does:

| offset | bytes | gives |
|---|---|---|
| 0 | 4 | the offset of the first row, from the start of the sources |
| 4 | 2 | `R`, the row count, minus one |
| 6 | 2 | bit 15 the repeat; bits 14 to 0 `RR`, the row it repeats to |

An entry is a source's metadata: the `R` and `RR` a table's metadata gives
(R1.1), with `C` and the column width the typical ones until section 7
says how an entry gives others.

Source 0 has no entry. The index runs from source 1.

A start resolves the number through the index once, and the ticks advance
rows from there on; nothing is looked up while the effect runs (R3.3).

A source holds at most 32,768 rows. The row count spans exactly that, and
over the typical one-byte row a signed 16-bit offset on a 68000 reaches
every row of one from its start.

Bit 15 of the row count is unassigned, and is what an entry has nearest to
hand for describing a source of more than one column (R6.2).

The repeat is what the last row does. At 1 the next tick takes row `RR`;
at 0 the source has no next row. A source of two rows repeating is a
square wave, and one of many rows playing once is a drum; a loop returns
to `RR`, which is where the loop begins and need not be the first row.

### 3.2 The rows

A source's rows hold what the register its target writes takes (R5.3).
Bound for a volume register, a recording's linear amplitudes convert to
the logarithmic levels the register takes, and the conversion is the
writer's work.

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
one register rather than twenty-two columns.

A player advances the tune's table one row a frame, for every table the
tune runs, and writes the columns that row sets (1.1). A value the row does
not set is not interpreted, and a player that needs one on a later row
keeps it (R4.6) rather than looking for it in the buffer. Bit 6 of column 8
is beside its column's value, and read on every row (1.1).

The effects go first, then the registers.

1. Columns 11, 13, 15 and 17, the rates, each to the timer its effect runs
   on.
2. Columns 10, 12, 14 and 16, the effects. A source of 0 stops what the
   timer ran. Any other source stops it and starts what the column gives.
3. Columns 0, 2 and 4, the tone periods, to R0 to R5.
4. Column 7 to R6, and column 9 to R11 and R12, as 1.7's table reads it.
5. Columns 1, 3 and 5, the volumes, to R8, R9 and R10.
6. Column 6 to R7. The player writes bits 5 to 0 of the column with R7's
   bits 7 and 6 as the host holds them, which it does not change.
7. Column 8 to R13. Any write to R13 restarts the envelope.

The effects stop before the registers are written, and that order is what
makes a restore hold. The row that stops an effect sets its register's
column too (1.3); were the register written first, a tick of the effect
still running would write over it. A start needs no such order, because
the row that starts an effect leaves that register's column unset
(section 6).

Steps 3 to 7 write where the row sets, each to registers the column itself
fixes, and none of them reads an index. One test crosses columns: a zero
in column 9 sends the player to bit 6 of column 8 (1.7). Step 2 is the
only step that reads the source index, and it reads it to start an effect,
not to place a value.

---

## 5. What a tick does

A tick is section 4's method at a timer's rate: it advances its source one
row and calls its target with that row (2.1, 2.2).

The last row is where the source's `RR` applies. One that repeats takes row
`RR` on the next tick. One that does not has no next row, and section 7
holds what follows as unwritten.

A player does not read the DTX table between ticks.

---

## 6. What a writer does not do

These rules bind the writer. They are why a player writes a marked column
once and tests nothing.

1. While an effect owns a register, a row leaves that register's column
   unset. A player writing steps 3 to 7 then needs no test: the row does
   not set the column.
2. An effect a row starts is one the tune states in section 3.
3. A source and a target a column names are ones section 2 defines.
4. Where two timers name one register, their writes are the writer's to
   order. A player writes what each tick gives it.

---

## 7. Not yet written

How many sources a tune may hold, and where in a tune the index and the
rows are placed.

Where a tune states the version it was written for, and what a player does
with a version it was not built for (R6.1).

What follows the last row of a source that does not repeat: whether the
timer stops, and what the register then holds. The entry ends the rows
(3.1), and no rule ends the effect but a row of the DTX table.

How an entry describes a source of more than one column, or of a column
wider than a byte (2.2).

What a reader reports.
