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
| 12 | 2 | effect 1 |
| 13 | 2 | effect 1 rate |
| 14 | 2 | effect 2 |
| 15 | 2 | effect 2 rate |
| 16 | 2 | effect 3 |
| 17 | 2 | effect 3 rate |

An effect is the schema's and a timer is the machine's. Section 2.3 says
which runs which. A tune running one effect sets columns 10 and 11 and
leaves 12 to 17 unset for its whole length.

### 1.1 The set bit

Bit 7 of a column's first byte is its set bit, the flag for the column's
value (R3.6). A column is most significant byte first, so that one rule is
the top bit at every width: bit 7 of a byte and bit 15 of a word. At 1 a
player takes the value; at 0 it does not interpret the value's bits, and
they may hold anything.

The set bit flags the value, not the column. A bit a column holds beside
its value is read on every row, set bit or no: bit 6 of the envelope
shape, which settles what a zero in the envelope period means (1.6, 1.7).

One column has no bit to spare, and reserves a value instead. The envelope
period fills its word, so 0 says the row does not set it (1.7).

One column gathering all eighteen set bits would gather eighteen reasons
to move, and the sum of them moves on nearly every row. A bit held beside
its own value moves only when that value's use does, and compresses with
it.

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
| not 0 | 0 | sets the period to the column's value |
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
| 14 to 12 | the source, 0 to 7. Source 0 is none, and the timer runs nothing |
| 11 to 8 | the target, 0 to 15 |
| 7 to 0 | the data, eight bits the source reads |

The first byte holds the set bit, the source and the target, so one read
gives a player the whole routing. The second holds the data whole.

The source and the target are the schema's numbers, not the machine's, and
section 2 says what each is. A player resolves both when a source starts,
and not again while it runs.

A row sets this column to start what it names. A row that starts the same
effect a second time sets it to the value it already held, which is what
R3.6 allows.

A timer runs one effect, and holds one at a time. The schema has four
effect columns and the MFP has four timers, so four effects sound at once
and there is no fifth. A tune that needs another stops one of the four on
the row that starts it, which is the writer's work and not a player's
(R3.3).

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

A row restarts an effect by setting the effect column, not this one. A row
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

| target | is |
|---|---|
| 0 to 13 | R0 to R13 |
| 14, 15 | unassigned |

### 2.2 The sources

| source | supplies, one value a tick | its data byte |
|---|---|---|
| 0 | nothing, and the timer runs no effect | unused |
| 1 | a toggle: the level, then zero, alternating | bits 3 to 0, the level |
| 2 | a sample's bytes, one a tick; its index entry ends it (3.1) | the sample number |
| 3 | a wave's bytes, the same, through the wave index | the wave number |
| 4 | a retrigger: one shape written again | bits 3 to 0, the shape |
| 5 to 7 | unassigned | |

### 2.3 The timers

| effect column | runs on |
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
| effects used | 1 | bits 3 to 0: which of the four effect columns the tune ever sets, so a player claims those timers before the first row |
| the sample index | 6 an entry | one entry a sample, in sample-number order |
| the wave index | 6 an entry | one entry a wave, in wave-number order |
| the samples | | the bytes the sample index addresses |
| the waves | | the bytes the wave index addresses |

A player reads one row at a time (R1.3), so it cannot find which timers to
claim by reading ahead. That is why the effects a tune runs are stated here.

### 3.1 The indexes

An effect's data byte gives a sample or wave number, and the entry at that
number gives where the bytes are and what the last one does:

| offset | bytes | gives |
|---|---|---|
| 0 | 4 | the offset of the first byte, from the start of the samples or of the waves |
| 4 | 2 | bit 15 the repeat; bits 14 to 0 the length minus one |

A start resolves the number through the index once, and the ticks read
bytes from there on; nothing is looked up while the effect runs (R3.3).

A sample or a wave holds at most 32,768 bytes. The length field spans
exactly that, and a signed 16-bit offset on a 68000 reaches every byte of
one from its start.

The repeat is what the last byte does. At 1 the next tick starts over at
the first byte; at 0 the supply ends. A repeating wave is a timbre and one
played once is a run - a decay drawn by hand, say - and a sample divides
the same way: a drum plays once, a loop starts over.

### 3.2 The bytes

A sample and a wave hold what a volume register takes, which is a
logarithmic index and not a recording's linear amplitude (R5.3).

---

## 4. The frame

A player calls `nextRow` once a frame for every table the tune runs, and
writes the columns the row sets (1.1). A value the row does not set is not
interpreted, and a player that needs one on a later row keeps it (R4.6)
rather than looking for it in the buffer. Bit 6 of column 8 is beside its
column's value, and read on every row (1.1).

The writes happen in this order.

1. Columns 0, 2 and 4, the tone periods, to R0 to R5.
2. Column 7 to R6, and column 9 to R11 and R12, as 1.7's table reads it.
3. Columns 1, 3 and 5, the volumes, to R8, R9 and R10.
4. Column 6 to R7. The player writes bits 5 to 0 of the column with R7's
   bits 7 and 6 as the host holds them, which it does not change.
5. Column 8 to R13. Any write to R13 restarts the envelope.
6. Columns 11, 13, 15 and 17, the rates, each to the timer its effect runs
   on.
7. Columns 10, 12, 14 and 16, the effects. A source of 0 stops what the
   timer ran. Any other source stops it and starts what the column gives.

Steps 1 to 6 write where the row sets, each to registers the column itself
fixes, and none of them reads a map. One test crosses columns: a zero in
column 9 sends the player to bit 6 of column 8 (1.7). Step 7 is the only
step that
reads a map, and it reads one to start an effect, not to place a value.

---

## 5. What a tick does

A tick of a timer running an effect writes one value to the register its
target names. Section 2.2 says what the value is and how the effect's eight
data bits are read.

A player does not read the table between ticks.

---

## 6. What a writer does not do

These rules bind the writer. They are why a player writes a marked column
once and tests nothing.

1. While an effect owns a register, a row leaves that register's column
   unset. A player writing steps 1 to 5 then needs no test: the column's
   set bit is clear.
2. An effect a column runs is one the tune states in section 3.
3. A source and a target a column names are ones section 2 defines.
4. Where two timers name one register, their writes are the writer's to
   order. A player writes what each tick gives it.

---

## 7. Not yet written

How many samples and waves a tune may hold, and where in a tune the
indexes and their bytes are placed.

Where a tune states the version it was written for, and what a player does
with a version it was not built for (R6.1).

What follows the last byte of a sample or wave that does not repeat:
whether the timer stops, and what the register then holds. The entry ends
the supply (3.1), and no rule ends the effect but a row.

What a reader reports.
