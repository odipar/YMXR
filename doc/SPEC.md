# The YMXR format

Version 3 encodes YMXS tune data (YMXS, SPEC.md 1) as DTX tables for
playback on the Atari ST's YM2149 and MC68901. This document defines
columns (1), target, source and timer maps (2), file layout (3), frames
(4), ticks (5), writer rules and checks (6), reader output (7), and later
versions (8).

A tune's table is a DTX table (DTX, SPEC.md 1) of one width (R1.1), a
byte, the width of a register: 30 columns of the 32 R3.4 allows, each one
byte, so a row is 30 bytes. YMXS, SPEC.md 2 to 6 define what each
register reaches, what a target, a source and a rate are, what a frame
and a tick do with them, and the rules of the structure; this document
defines the encoding of each and cites the rest.

**Conventions.** A clause is cited by number, 4.3 or 1.1.2, and a step
within one as 4.3 step 2; a rule of section 6 as rule 1 and a condition
of one as rule 1(a); a clause of YMXS's SPEC.md as (YMXS, SPEC.md 3.4)
and a section of DTX's as (DTX, SPEC.md 1). `Note:` begins an
informative sentence. A range includes both ends. `$` prefixes a
hexadecimal figure. The bits of a byte are numbered 7 to 0, bit 7 the
most significant, and a bit is 1 or 0. A field of more than one byte is
most significant byte first. An offset counts bytes from the first byte
of the file, and an offset *on a long* is a multiple of 4. A row *sets*
a column where the column is set as 1.1 defines, and otherwise leaves it
*unset*. In a reported text, `Rn` is a register and each other capital
letter a decimal figure defined beside the text.

**Roles.** A player performs sections 4 and 5 on the two chips. A reader
reads a tune file (3.3) and performs section 4 with every register write
and every effect column recorded as an entry of section 7 in place of the
chips (R2.4). A writer produces a tune file, satisfying the rules of
section 6, from a structure free of the errors of YMXS, SPEC.md 1.11. A
check reads a tune file beside the recording it encodes and reports the
lines of 6.4. The host calls the player once a frame at the frame rate
(3.3), and owns every timer outside the tune's and every register outside
R0 to R13. [BINARIES.md](BINARIES.md) 1 defines the bound tune a player
reads, and [tools.md](tools.md) 15 the player's three calls.

**Terms.** A *frame* is one call of the player (4.2); a *tick* one
interrupt of a timer (5.1); a *row* one entry of a table. The *MFP* is
the MC68901. An *effect* is one of the four groups of columns 14 to 29
(1.8), and *effect i* runs on the timer 2.3 assigns it. A *start* is a
row setting an effect's source column to 1 to 127, a *stop* a row setting
it to 0 (1.8.3). The *kept* target, select and count of an effect are the
values the player keeps of the last row that set each (R4.6). The *place*
of a timer is the row of its source the next tick reads (YMXS, SPEC.md
3.4.2). A *recording* is the frames a writer produces a tune from: each
frame the values of R0 to R12, R13 present or absent, and a flag on
effect 0 and on effect 1, present or absent (6.4). Section 3.2 defines
the *marker*, 3.1 the *source index* and an *index entry*, 6.3 an *unset
row*, and 2.3 *stopping*, *claiming* and *releasing* a timer.

---

## 1. The columns

Columns 0 to 13 reach R0 to R13, one a register, in the chip's numbering;
columns 14 to 29 are the four effects, four columns each, effect i at
columns 14 + 4i to 17 + 4i. A writer writes 0 to a bit this section
leaves unassigned, a player leaves it unread, and a later version assigns
it (R6.2, section 8).

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

### 1.1 The set bit

**1.1.1** Every column other than the nine of 1.1.2 has a set bit, bit 7
(R3.6). Where it is 1 the row sets the column, and a player reads bits 6
to 0 as the column's clause defines. Where it is 0 the row leaves the
column unset, and a player leaves bits 6 to 0 unread, other than a bit
1.1.2 assigns, which 1.1.4 reads; a writer may write any value to those
bits, other than a bit 1.1.2 assigns and a bit this section leaves
unassigned. A row may set a column to the value the register already
has.

**1.1.2** Nine columns fill their byte with their value: 0, 2, 4, 11, 12,
17, 21, 25 and 29. Each reserves the value 0 for a row that leaves it
unset (R3.7), and a bit of a column beside it marks its 0 as a value:

| column | its 0 is a value where | a player reads that bit |
|---|---|---|
| 0 | bit 6 of column 1 is 1 | on every row |
| 2 | bit 6 of column 3 is 1 | on every row |
| 4 | bit 6 of column 5 is 1 | on every row |
| 11 | bit 6 of column 13 is 1 | on every row |
| 12 | bit 5 of column 13 is 1 | on every row |
| 17 | bit 4 of column 16 is 1 | where the row sets column 16 |
| 21 | bit 4 of column 20 is 1 | where the row sets column 20 |
| 25 | bit 4 of column 24 is 1 | where the row sets column 24 |
| 29 | bit 4 of column 28 is 1 | where the row sets column 28 |

The bit marks a 0 alone: a byte other than 0 is the column's value with
that bit at 1 and at 0 (1.1.3).

**1.1.3** A player reads such a column and the bit beside it together,
and writes the column's register where the row sets the column (4.3,
4.4):

| the column | the bit | the row |
|---|---|---|
| other than 0 | 0 or 1 | sets the column, whose value is the byte |
| 0 | 0 | leaves the column unset |
| 0 | 1 | sets the column to 0 |

**1.1.4** A player reads a marking bit where 1.1.2's table has it, the
set bit of the column it stands in 1 or 0: a row may set a fine column
to 0 and leave the coarse column unset (1.2), or mark a period byte 0
and leave R13 as it is (1.6). Where a row leaves a control column unset,
a count column of 0 is unset (1.9). Every other bit of a column is part
of its value, read where the row sets the column.

### 1.2 Tone period

**1.2.1** Two columns a voice: a fine column, 0, 2 or 4, and a coarse
column, 1, 3 or 5. The fine column is bits 7 to 0 of the twelve-bit
period, the byte written to R0, R2 or R4; it fills its byte, and bit 6
of the coarse column marks its 0 as a value (1.1.2).

**1.2.2** The coarse column:

| bit | meaning |
|---|---|
| 7 | the set bit |
| 6 | the fine column's 0 is a value (1.1.2) |
| 5, 4 | unassigned, 0 |
| 3 to 0 | bits 11 to 8 of the period, which reach R1, R3 or R5 |

**1.2.3** A row sets either column of a pair alone, or both (YMXS,
SPEC.md 2.2). A player reads bit 6 on every row (1.1.4).

### 1.3 Volume

**1.3.1** Columns 8, 9 and 10, one a voice:

| bit | meaning |
|---|---|
| 7 | the set bit |
| 6, 5 | unassigned, 0 |
| 4 to 0 | the value, which reaches R8, R9 or R10 (YMXS, SPEC.md 2.3) |

**1.3.2** While an effect runs on the register, every row leaves the
column unset, and the row that stops the effect may set it (rule 1).

### 1.4 Mixing

**1.4.1** Column 7:

| bit | meaning |
|---|---|
| 7 | the set bit |
| 6 | unassigned, 0 |
| 5 to 0 | the value, bits 5 to 0 of R7 (YMXS, SPEC.md 2.4) |

**1.4.2** Bits 7 and 6 of R7 are the directions of the two I/O ports
(YMXS, SPEC.md 2.4, 8.8). A player writes both as 1 on every write of R7
a row makes (4.4 step 5), the directions of an Atari ST; a reader reports
bits 5 to 0 (7.3). A tick of a counted source on `setR7` writes the row
whole (5.1), so the two bits stand in the source, where a writer sets
them (rule 2(f)).

### 1.5 Noise period

**1.5.1** Column 6:

| bit | meaning |
|---|---|
| 7 | the set bit |
| 6, 5 | unassigned, 0 |
| 4 to 0 | the value, which reaches R6 |

### 1.6 Envelope shape

**1.6.1** Column 13:

| bit | meaning |
|---|---|
| 7 | the set bit |
| 6 | column 11's 0 is a value (1.7) |
| 5 | column 12's 0 is a value (1.7) |
| 4 | unassigned, 0 |
| 3 to 0 | the shape, which reaches R13 |

**1.6.2** A row that sets this column writes R13, and every write of R13
restarts the envelope, the value changed or the same (YMXS, SPEC.md 2.5);
a row that leaves it unset leaves R13 and the envelope as they are. A
player reads bits 6 and 5 on every row (1.1.4). A row may set this column
while an effect runs on R13 (rule 1).

### 1.7 Envelope period

**1.7.1** Column 11 is bits 7 to 0 of the sixteen-bit period, the byte
written to R11; column 12 is bits 15 to 8, the byte written to R12. Each
fills its byte (1.1.2): bit 6 of column 13 marks column 11's 0 as a value
and bit 5 marks column 12's, and a player reads each pair by 1.1.3. A row
sets either column alone, or both.

### 1.8 Effect

**1.8.1** Four columns an effect, effect i at column 14 + 4i: the target
column, the source column, the control column and the count column (1.9).
Effect i runs on the timer of 2.3.

**1.8.2** The target column: bit 7 the set bit, bits 6 to 0 the target
number, 0 to 127 (2.1). A row that sets it makes its value the kept
target; the kept target is 0 until a row sets it. A row that sets the
target column alone changes the kept target and leaves the effect
running as it is: a running effect writes the registers of the kept target
at its start (4.3 step 3).

**1.8.3** The source column: bit 7 the set bit, bits 6 to 0 the source
number, 0 to 127. A row that sets it to 1 to 127 is a start: from 4.3
step 3 the timer runs that source of the source index (3.1) on the kept
target, and each tick writes the registers of that target (5.1). A row
that sets it to 0, the byte `$80`, is a stop: the timer stops and is
idle, and the place is left as it is.

**1.8.4** A start places the source: where the row sets the control
column with bit 5 at 1, the place is row 0; otherwise the place keeps its
row number, which is then a row number of the source started (YMXS,
SPEC.md 3.4.3); before the first start on the timer that number is 0 (4.1
step 4). A start sets the rate where the row sets the control column or
the count column (1.9), and leaves the timer counting at the kept select
and count where it leaves both unset: a row that connects a second source
to a running timer at the rate it counts sets the source column alone.

**1.8.5** A stopped timer starts at the write of a select (1.9.4), bit 6
of the control column 1 or 0; rule 3 fixes the value a writer sets.

**1.8.6** A row may set the source column to the number the timer runs:
the source starts again, placed by 1.8.4. Two effects may run one source,
each with a separate place (YMXS, SPEC.md 3.2.3).

### 1.9 Effect rate

**1.9.1** The count column is the count, 0 to 255, the byte written to
the timer's data register (YMXS, SPEC.md 3.3.3). It fills its byte
(1.1.2): 0 marks a row that leaves it unset, and bit 4 of the control
column marks 0 as the value, which the timer counts as 256. A player
reads bit 4 where the row sets the control column (1.1.4).

| the count column | bit 4 of the control column | the row |
|---|---|---|
| other than 0 | 0 or 1, or the control column unset | sets the count to its byte |
| 0 | 0, or the control column unset | leaves the count unset |
| 0 | 1 | sets the count to 0, which counts 256 |

**1.9.2** The control column:

| bit | meaning |
|---|---|
| 7 | the set bit |
| 6 | `timerReset` (YMXS, SPEC.md 3.4.1): the player stops the timer, writes the count, then writes the select (4.3) |
| 5 | `placeReset` (YMXS, SPEC.md 3.4.3): the place is row 0 |
| 4 | the count column's 0 is a value (1.9.1) |
| 3 | unassigned, 0 |
| 2 to 0 | the select: 1 to 7 for the divisors 4, 10, 16, 50, 64, 100 and 200 (YMXS, SPEC.md 3.3.2); 0 unassigned (section 8) |

**1.9.3** A row that sets the control column sets the select, the two
resets and bit 4 at once. A row that leaves it unset sets a count of 1 to
255 or leaves the count unset, and leaves the timer running as it is.
Rule 4 fixes on which rows a writer sets the two columns.

**1.9.4** The select reaches the timer's control register (2.3). Timers A
and B each have a register: the player writes one byte, the select in
bits 2 to 0 and 0 in bits 7 to 3. Timers C and D share one, C's select in
bits 6 to 4 and D's in bits 2 to 0: at interrupt level 7 the player reads
the register, keeps the four bits of the other timer, writes the select
to its three bits and 0 to the fourth bit of its four, and writes the
byte back. A select of 0 stops a timer; a row stops one through the
source column (1.8.3). YMXS, SPEC.md 3.3.5 and 3.4.1 define what a select
or a count written to a running or a stopped timer does; 4.3 fixes the
order of the two writes.

---

## 2. The maps

The format fixes these three maps once, and a tune's columns are numbers
under them (R3.2).

### 2.1 The targets

| target | procedure | columns | the marker's column |
|---|---|---|---|
| 0 to 13 | `setR0` to `setR13` (YMXS, SPEC.md 3.1.2): `setRn` writes the row to Rn | 1 | 0 |
| 14, 15, 16 | `setToneA`, `setToneB`, `setToneC`: R0 R1, R2 R3, R4 R5 | 2 | 1, the coarse nibble |
| 17, 18, 19 | `setVoiceA`, `setVoiceB`, `setVoiceC`: R0 R1 R8, R2 R3 R9, R4 R5 R10 | 3 | 1, the coarse nibble |
| 20 | `setEnvelope`: R11 R12 | 2 | 1, the period's high byte |
| 21 | `setBuzzer`: R11 R12 R13 | 3 | 2, the shape |
| 22, 23, 24 | `setNoiseA`, `setNoiseB`, `setNoiseC`: R6 R8, R6 R9, R6 R10 | 2 | 0, the noise period |
| 25 to 127 | unassigned; a later version assigns them (section 8) |  |  |

**2.1.1** A tick writes a source's row to the registers of its target,
column i of the row to register i, each byte whole, and the register
reads the bits it has (4.4). The marker of 3.2 stands in bit 7 of the
column above, whose register reads seven bits or fewer, and the other
columns are whole bytes: a source on `setToneA` writes R0 the eight bits
it reads, where a source on `setR0` has no bit to spare for the marker.

A tick writes the columns in column order and the marker's column last
(5.1), so a player reads the marker off the last byte it wrote. The order
of the writes is the player's (YMXS, SPEC.md 3.1.1), with two of them
fixed there: a tone and a voice name their coarse nibble for the marker,
so the fine byte stands before it, and a buzzer names its shape, so the
envelope period stands before it.

**2.1.2** A target of one register runs a source of one column, and one
of several a source of that many columns (3.1.3; YMXS, SPEC.md 3.2.1).
The targets of one register whose register reads seven bits or fewer are
`setR1`, `setR3`, `setR5`, `setR6`, `setR8`, `setR9`, `setR10` and
`setR13`, and a source on one of these writes its rows with the marker
in bit 7 (3.2.1). `setR0`, `setR2`, `setR4`, `setR7`, `setR11` and
`setR12` write a register that reads every bit of its byte, so a source
on one of these spends every bit of a row on the value and a player
counts its rows (3.1.6). R7 reads every bit of its byte with bits 7 and
6 the directions of the two ports, which a player writes as 1 (1.4.2)
and a writer sets in the rows of a counted source on `setR7` (rule
2(f)); a register column of R7 is the six bits below them (1.4.1).

**2.1.3** `setEnvelope` writes R11 and R12, the low and the high byte of
the envelope period (YMXS, SPEC.md 2.5), and both registers read eight
bits. A source on this target is counted (3.1.6): its rows are whole
bytes, so it writes R12 a value of 0 to 255 and reaches an envelope
period of 0 to 65,535, the whole range a frame's R11 and R12 columns
reach (1.1). The envelope frequency is 2,000,000 / (256 x envelope
period) (terminology.md 1.4): period 32,767 is one cycle in 4.19 seconds
and period 65,535 one in 8.39 seconds.

**2.1.4** A file of version 4 or 5 writes a marked source on that target
(3.3.5): bits 6 to 0 of R12's column are the value, the marker stands in
bit 7, and the tick that reads the last row writes that bit into R12 with
the value, a period 32,768 above the one the rows before it write. A
player reads such a source as this clause defines it, and a writer of
this version writes a counted source in its place.

### 2.2 The sources

**2.2.1** A source is a table of one, two or three values a row (YMXS,
SPEC.md 3.2.1), written as a DTX1 table of R rows, a column a value and
one byte a value, repeating to RR or playing once (3.1). A source has as
many columns as the target of every effect that starts it reads (2.1.2).

**2.2.2** A source is numbered by its index entry, 1 to 127 (3.1), and a
source column names one by that number (1.8.3). A tick advances a source
one row (5.1); a frame advances the tune's table one row (4.2).

### 2.3 The timers

| effect | timer | control register | select bits | data register | vector | bit | enable, pending, in-service, mask |
|---|---|---|---|---|---|---|---|
| 0 | A | `$FFFA19` | 2 to 0 | `$FFFA1F` | `$134` | 5 | `$FFFA07`, `$FFFA0B`, `$FFFA0F`, `$FFFA13` |
| 1 | D | `$FFFA1D` | 2 to 0 | `$FFFA25` | `$110` | 4 | `$FFFA09`, `$FFFA0D`, `$FFFA11`, `$FFFA15` |
| 2 | B | `$FFFA1B` | 2 to 0 | `$FFFA21` | `$120` | 0 | `$FFFA07`, `$FFFA0B`, `$FFFA0F`, `$FFFA13` |
| 3 | C | `$FFFA1D` | 6 to 4 | `$FFFA23` | `$114` | 5 | `$FFFA09`, `$FFFA0D`, `$FFFA11`, `$FFFA15` |

**2.3.1** Effect i runs on the timer of its row; the bit column is the
timer's bit in each of the four interrupt registers of its row, and the
vector is the address of its exception vector, the vector table at
address 0. A tune claims timers in effect order (4.1). Timer C is the 200
Hz clock of the operating system: a tune running effect 3 stops that
clock, and one running effects 0 to 2 alone leaves Timer C to the host.

**2.3.2** A pending bit and an in-service bit clear on a written 0 and
keep their value on a written 1: a player clears one by one write of the
byte with that bit 0 and every other bit 1. A player sets or clears an
enable bit or a mask bit by a read, a change and a write back.

Note: a read and a write back of the pending register drops a tick of
another timer latched between the two bus cycles.

**2.3.3** Three operations on a timer:

- *Stopping* it: write 0 to its four bits of its control register, the
  select and the bit above it, the other four bits as they are; then
  clear its pending bit.
- *Claiming* it (4.1): write 0 to its four bits of the control register;
  write the address of its tick (5.1) to its vector; clear its pending
  bit; set its enable bit; set its mask bit.
- *Releasing* it (4.7): write 0 to its four bits of the control register;
  clear its enable bit; clear its mask bit; clear its pending bit.

**2.3.4** A write that clears a pending bit or an enable bit between the
MFP raising an interrupt and the 68000 acknowledging it leaves the 68000
at the spurious interrupt exception, vector `$60`; a host provides an
`rte` there while a tune plays, and keeps and restores its handler.

---

## 3. Outside the table

A tune records these once (R5.6): the version, the frame rate, the
effects used, the name, the source index and the sources. Section 3.3
defines the bytes of each.

### 3.1 The source index

**3.1.1** The source index is S index entries, S the source count, 0 to
127: index entry n, 1 to S, is 4 bytes at 16 + 4(n - 1), bits 30 to 0
the offset of the DTX1 table of source n, whichever the version, and bit
31 the mark of a counted source (3.1.6). The index begins at source 1:
source 0 is the stop (1.8.3).

**3.1.2** A source's table is a DTX1 file (DTX, SPEC.md 1 and 2.2):

| offset | bytes | meaning |
|---|---|---|
| 0 | 3 | `DTX` |
| 3 | 1 | the variant, 1 |
| 4 | 4 | R, the row count, 1 upward |
| 8 | 2 | C, the column count, 1, 2 or 3 (3.1.3) |
| 10 | 4 | RR, the row the source repeats to, 0 to R - 1, or R for a source that plays once |
| 14 | 1 | W, the bytes a value, 1 |
| 15 | 1 | 0 |
| 16 | (C - 1) times align(R) + R | the rows, a column at a time (3.1.3): row n of column i at 16 + i times align(R) + n, align(R) the row count rounded up to a multiple of 2; the last column is R bytes and the table ends; for C = 1 that is R bytes, row n at 16 + n |

**3.1.3** A reader reads a source whose C is the columns of the target
of every effect that starts it, 1, 2 or 3 (2.1), and whose W is 1;
another C or W is an error of the file (3.3.4). DTX1 lays a table out
column by column, so column i of a source of C columns stands at 16 + i
times align(R) (3.1.2), which is the stride DTX1 lays its columns at
(DTX, SPEC.md 2.2), and a tick reads its columns at that stride. Where R
is odd, the byte between one column and the next is 0. A source of
values wider than a byte is left to a later version (section 8).

**3.1.4** RR below R marks a source that repeats: the tick that reads row
R - 1 places the next at row RR (5.1). RR equal to R marks a source that
plays once: the tick that reads row R - 1 stops the timer (5.1).

**3.1.5** A writer numbers the sources in first-start order (YMXS,
SPEC.md 1.9).

**3.1.6** A counted source is one whose rows carry no marker: each of the
eight bits of a row is a value, where the marker of 3.2.1 requires bit 7.
Bit 31 of its index entry is 1, and a tick reads R rows from the row it
starts at (5.1). Bit 31 of the index entry of every other source is 0.

A writer writes a source counted where the column the marker would stand
in writes a register that reads every bit of its byte: a source of one
column on a target of 2.1.2, and a source of two columns on `setEnvelope`
(2.1.3). A file with a counted source of one column is version 5, and one
with a counted source of several columns version 6 (3.3.5).

### 3.2 The rows

**3.2.1** A row of a source is one byte a column, each fitting the
register its column writes (R5.3; YMXS, SPEC.md 3.2.2). The marker
stands in bit 7 of the column 2.1 names for the target, bits 6 to 0 of
that byte the value; every other column is a whole byte. A writer writes
the marker as 1 in the last row of the source and as 0 in every other
row. Every byte of every row of a counted source is a whole value, and
R ends the source (3.1.6).

**3.2.2** A tick writes each byte whole and, for a source the marker
ends, tests the marker after the writes (5.1); each register reads the
bits it has (2.1.1). A source of one row is the marker and its values in
one row. A player tests bit 7 of the marker's column in every row it
writes, so a row other than the last with bit 7 at 1 there ends the pass
through the source at that row; a check reports such a row (6.4). A
counted source ends on the count of 3.1.6, and every byte of its rows is
a value.

### 3.3 The tune file

| offset | bytes | meaning |
|---|---|---|
| 0 | 4 | `YMXR` |
| 4 | 2 | the version, `$0003`, `$0004`, `$0005` or `$0006` (3.3.5) |
| 6 | 2 | the frame rate, frames a second, 1 to 65,535 |
| 8 | 1 | the effects used: bit i, 0 to 3, is 1 where a row starts effect i; bits 7 to 4 are 0 |
| 9 | 1 | S, the source count, 0 to 127 |
| 10 | 2 | the offset of the name, or 0 for a tune with an empty name |
| 12 | 4 | the offset of the DTX2 table |
| 16 | 4S | the source index: index entry 1 to S, bit 31 of each the mark of a counted source (3.1.1) |
| 16 + 4S | 2 to 256 | the name, where the word at 10 is other than 0: its UTF-8 bytes, 1 to 255, and a zero byte |
| on a long | | the DTX2 table (3.3.3) |
| on a long each | | the DTX1 tables of sources 1 to S, in that order (3.1.2) |

**3.3.1** Each table begins on a long: the DTX2 table at the first offset
on a long at or after the end of the name, or of the index where the name
is empty, and each DTX1 table at the first on a long at or after the end
of the table before it. Each byte between the end of one of those and the
start of the next is 0. The DTX2 table ends where the table of source 1
begins, or at the file's end where S is 0. A tune file is data alone; a
tool binds it with a reader of DTX2 into the bound tune a player reads
(BINARIES.md 1).

**3.3.2** The name is for display. A writer drops each character below
space, drops leading and trailing spaces, cuts the UTF-8 at a character
boundary to at most 255 bytes, and writes the word at 10 as 0 for a name
left empty. A reader reads an empty name where the word at 10 is 0 or at
or past the file's end, and otherwise the bytes from the word's offset to
the first zero byte or the file's end.

**3.3.3** The DTX2 table is the tune's table as a complete DTX file (DTX,
SPEC.md 1 and 2.3), header and payload: R rows, 1 upward, C = 30, W = 1,
RR the repeat row or R for a tune that plays once. A reader unpacks each
column at the payload's unit k, 1, 2 or 4, with copies where bit 0 of the
payload's flags byte is 1 (DTX, SPEC.md 2.3); R and RR divide by k (rule
6). The ring N is at most 1,129 bytes and one DTX's packager binds (DTX,
abi.md 4).

Note: a player reaching column 29's ring through a 16-bit displacement
from column 0's reads a ring of at most 32,767 divided by 29. The
reference writer packs through a ring that is a multiple of 30 bytes, 60
to 1,110, 960 by default.

**3.3.4** Errors of the file. A reader reads a file free of the
conditions below and reports the first one present as its line, in the
order of the table: V the version, X a DTX variant, N a source number, A
and B where a table stands, F the file's bytes, C a column count and W a
width. The record of such a file is empty (7.4), and a reader that
reports a condition reads no further field. A name offset other than 16 +
4S is a field a reader follows (3.3.2).

| condition | reported as |
|---|---|
| the file's bytes 0 to 3 are `YMXM`, a multi file (BINARIES.md 0) | `this is a multi file of several tunes, and a record is of one tune` |
| the file is shorter than 16 bytes, or its bytes 0 to 3 are other than `YMXR` | `not a YMXR file` |
| the version is other than 3, 4, 5 or 6 (3.3.5) | `version V is not 3, 4, 5 or 6` |
| the table begins or ends outside the file | `the table stands at A to B, and the file has F bytes` |
| the table is a DTX variant other than 2 (3.3.3) | `the table is DTX X, and a tune's table is DTX2 (SPEC.md 3.3.3)` |
| the table is other than 30 columns of one byte (3.3.3) | `the table is C columns of W bytes, and a tune's table is 30 of one (SPEC.md 3.3.3)` |
| source N begins or ends outside the file | `source N stands at A to B, and the file has F bytes` |
| source N has C other than 1, 2 or 3, or W other than 1 (3.1.3) | `source N is C columns of W bytes, and a source is one, two or three columns of one (SPEC.md 3.1)` |
| the version is 3 and source N has several columns (3.3.5) | `source N is C columns, and version 3 writes one` |
| the version is below 5 and bit 31 of source N's index entry is 1 (3.1.6) | `source N is counted, and version V writes the marker` |
| the version is below 6 and source N is counted with several columns (3.1.6) | `source N is counted and C columns, and version V counts one` |

**3.3.5** The version. A tune whose sources are one column and whose
target columns are 0 to 13 is version 3; one with a source of several
columns or a target column of 14 upward is version 4 (2.1); one with a
counted source of one column is version 5 (3.1.6), whichever its targets
are, since a player of 3 or 4 would read bit 31 of that source's index
entry as part of an offset; and one with a counted source of several
columns is version 6, which a player of 5 would read as a counted source
of one column. A writer writes the lowest of the four a tune reads under,
so a tune two of them encode is one file and the older player reads it. A
player of this version reads 3, 4, 5 and 6.

Note: a tune at 50 Hz with the effects used 0, S 0 and the name `Circus
Attractions #2` begins `594D5852 0003 0032 00 00 0010 00000028`: the 22
bytes of the name at 16, and the DTX2 table at 40.

---

## 4. The frame

The player initializes once (4.1), processes a row on each host call
(4.2), and stops once (4.7). Each frame applies the current tune row,
reading only set columns and keeping values needed by later rows (R4.6).

### 4.1 Before the first frame

The host calls the player once with the tune, then once a frame at the
frame rate (3.3). The player, in order:

1. Read the version; for a version other than 3, 4, 5 or 6 (3.3.5),
   report -1 and leave every register and every timer as it is. A reader
   reports the line of 3.3.4 in place of that, and its record is empty
   (7.4).
2. Read R and RR of the tune's table and the effects used.
3. Resolve each source of the source index: its first row, and its loop
   row, row RR where it repeats and the end where it plays once (3.1.4).
4. Set the kept target, source, select and count of each effect to 0,
   and the place of each timer to 0, the row number a start that moves
   no place keeps (1.8.4); a tick before the first start on a timer
   writes the register of the target this leaves (5.2.1).
5. Read row 0, the current row.
6. At interrupt level 7, for each effect i the effects used names, 0 to
   3 in order, claim its timer (2.3.3).

Every other timer, and every register of the YM2149, is left as it is
until a row sets it (YMXS, SPEC.md 4.1). The host keeps, before 4.1, and
restores, after 4.7, the vector, the control register and the enable and
mask bits of each timer the tune claims.

Note: a player reads the bound tune (BINARIES.md 1), whose first four
bytes are `YMXB`; it reports -1 at step 1 for other first four bytes, and
for an image of a variant other than 2.

### 4.2 A frame

One call of the player, in order:

1. Where the tune has ended (4.6): report -1 and end, every register and
   timer left as it is.
2. Read the current row, columns 0 to 29.
3. For each effect 0 to 3 in order, perform 4.3 on its four columns.
4. Write the register columns (4.4).
5. Where the row is row R - 1 of a tune whose RR is R, the tune has
   ended (4.6); otherwise the current row is the row after it: n + 1 for
   row n below R - 1, and row RR after row R - 1 (4.5).
6. Report 0.

At step 3, a player processes only the effects marked in effects used;
a reader processes all four (7.3). Register writes follow effect
operations.

**4.2.1** A tick falls between any two operations of a frame: between
two steps of 4.2, between two steps of 4.3, between 4.3 and 4.4, and
between two register writes of 4.4. A tick between two operations uses
the updated effects before it and the previous values of those after it.
Three operations run whole against a tick: the two bytes of a register
write (4.4), the write of the target, the place and the loop row at a
start (4.3 step 3), and the write of a select (4.3 step 5), which on a
control register two timers share is a read, a change and a write back.
A stop is two writes (2.3.3), and a tick latched before the stop falls
between them.

Note: a player that reads the row of the next frame at the end of a
call, after step 4, leaves the writes of every frame at one offset from
the host's clock; row 0 is then read at 4.1 step 5.

### 4.3 An effect's columns

For effect i, its target column T, source column S, control column K and
count column N, columns 14 + 4i to 17 + 4i, and its timer (2.3), in
order:

1. Where K is set with bit 6 at 1: stop the timer (2.3.3).
2. Where T is set: the kept target is bits 6 to 0 of T.
3. Where S is set and bits 6 to 0 are 0: stop the timer (2.3.3). Where S
   is set and bits 6 to 0 are s, 1 to 127: start source s. At interrupt
   level 7: the registers the timer's tick writes are the registers of
   the kept target; the place is row 0 of source s where K is set with bit 5
   at 1, and otherwise the row number the place has, in source s; the
   loop row of the tick is the loop row of source s (3.1.4). From this
   step a tick of the timer reads source s.
4. Where N is other than 0, or K is set with bit 4 at 1: the count is N,
   and the step writes it to the timer's data register and keeps it.
   Otherwise the count keeps the value it has, and where K is set with
   bit 6 at 1 the step writes that kept count to the data register.
5. Where K is set: write bits 2 to 0 of K, the select, to the timer's
   control register (1.9.4) and keep it. A stopped timer starts at this
   write on a whole period at the count its data register has.
6. Where K is set with bit 5 at 1, and S is unset or set to 0: the place
   is row 0 of the source last started on the timer.

A start writes the tick's target, place and loop row as one operation
against a tick (4.2.1). With T, S and K unset and N at 0, the effect
continues unchanged.

Note: step 4 before step 5 loads the count before the select starts a
stopped timer (YMXS, SPEC.md 3.4.1 and 8.5).

### 4.4 The register columns

The player writes the registers below in this order, each where the row
sets its column (1.1), one write a register: the register's number to
`$FFFF8800`, then the column's byte whole to `$FFFF8802`; the two bytes
are one operation against a tick (4.2.1). The register reads the bits it
has: eight for R0, R2, R4, R11 and R12, four for R1, R3, R5 and R13, five
for R6, R8, R9 and R10, and six for R7, whose bits 7 and 6 the player
writes as 1 (1.4.2). A row whose column 13 is `$AE` writes shape `$E`.

1. R0, then R1; R2, then R3; R4, then R5 (1.2).
2. R6 (1.5).
3. R11, then R12 (1.7).
4. R8, R9, R10 (1.3).
5. R7 (1.4).
6. R13 (1.6), which restarts the envelope.

A register the row leaves alone keeps its value (YMXS, SPEC.md 4.4). A
register an effect runs on is written as any other where the row sets
its column; rule 1 leaves such a column unset.

Note: on a 68000 the two bytes of a write are one `movep.w`.

### 4.5 The wrap

When RR is below R, the frame after row R - 1 reads row RR and performs
it as any row. The wrap preserves timers, places and kept values (YMXS,
SPEC.md 4.5). YMXS, SPEC.md 6 rules 1(d) and 6(a) define the repeat row's
operations on a running timer.

### 4.6 The end

A tune whose RR is R ends with row R - 1: the frame that reads it
reports 0, and every frame after it leaves the current row, every
register and every timer as it is and reports -1 (YMXS, SPEC.md 4.6).
Frames 0 to R - 1 read the R rows, and frame R on reports -1. A timer
running at the end runs on until the stop (4.7).

### 4.7 The stop

The host calls the stop once, after the last frame it requires. The
player, in order:

1. At interrupt level 7, for each effect the effects used names, 0 to 3
   in order, release its timer (2.3.3).
2. Write R8, R9 and R10 as 0; then R13, R12 and so on down to R0 as 0;
   then R7 as `$FF`, every channel off and both port directions 1.
3. Where the player cleared bit 3 of the vector register at 4.1 (5.1.1),
   write the register back as 4.1 read it.

Every other timer is left as it is.

---

## 5. The tick

### 5.1 A tick

A tick of a timer running an effect, in order (YMXS, SPEC.md 5.2). The
target is the effect's kept target at its start (4.3 step 3), and 2.1
names its registers and the column of the source each is written from
(2.1.1):

1. For each column of the source other than the column 2.1 names for the
   marker, in column order: write that column's register number to
   `$FFFF8800`, then the byte at the place in that column to
   `$FFFF8802`, whole (3.2.2). A source of one column has that column
   alone, and step 2 writes it.
2. Write the register number of the marker's column to `$FFFF8800`, then
   the byte at the place in that column to `$FFFF8802`, whole.
3. Test whether the row is the last of the source: where the source is
   counted (3.1.6), row R - 1 is the last; where it is any other, a row
   whose byte written at step 2 has bit 7 set, the marker, is the last.
4. Where the row is before the last: the place is the row after it, in
   every column.
5. Where the row is the last: for a source that repeats, the place is
   row RR; for a source that plays once, stop the timer (2.3.3). The
   timer is idle, the place stays at row R - 1, and each register keeps
   the value written until a row sets it.
6. Where bit 3 of the vector register `$FFFA17` is 1, software end of
   interrupt: clear the timer's in-service bit (2.3.2).

**5.1.1** A player runs the MFP in the mode the host leaves it, bit 3 of
`$FFFA17` at 1, and performs step 5. A player that clears bit 3 at 4.1,
automatic end of interrupt, omits step 5, keeps the interrupt level
through every tick, and writes the register back at 4.7 (BINARIES.md 2,
the flags word).

**5.1.2** A player performing step 5 may lower the interrupt level to 5
after step 2, so that a tick of a timer of higher priority nests in this
one; a tick of the same timer or of a lower one is served after it.

### 5.2 The timer at a tick

A tick leaves its timer's data register unchanged and writes the control
register only to stop the timer (5.1 step 5). The next period uses the
current count; a count written by a row loads as YMXS, SPEC.md 3.3.5
defines. Ticks read source rows; frames read tune rows (4.2). A select
written before the first start breaks rule 4 and starts a timer with no
source connected, whose ticks 5.2.1 defines.

**5.2.1** Until the first start on it, a timer has no source connected
(1.8.3). A tick of such a timer, in order:

1. Write 0 to `$FFFF8800`, the register of the target 4.1 step 4 keeps
   (2.1).
2. Write `$80` to `$FFFF8802`.
3. Stop the timer (2.3.3).

A row that sets the target column moves the kept target and leaves the
register a tick writes as it is: a start writes that register (4.3 step
3). Rule 4(c) keeps a tune clear of such a tick.

---

## 6. The rules a writer satisfies

### 6.1 The rules and the player

A player assumes the six rules below and writes each column as the row
has it, so a tune that breaks a rule plays as its columns read. A check
reads rules 1 and 6 (6.4); rules 2 to 5 bind the writer alone. Rule 1
encodes rule 1 of the structure, rule 3 its rules 3 and 4, and rule 4 its
rule 5 (YMXS, SPEC.md 6); its rules 2 and 6 are satisfied in the
structure a writer encodes; rules 2, 5 and 6 here are this format's. A
structure outside what this version encodes is an error of the writer
(6.5).

Terms: an effect *runs* from 4.3 step 3 of its start row until the first
of a stop on the effect, a later start on it, and, for a source that
plays once, the tick that reads its last row (YMXS, SPEC.md 6.2); its
timer is *stopped* from then until the next start. An effect runs on the
register of the kept target at its start.

### 6.2 The rules

**Rule 1: while an effect runs on a register R0 to R12, every row leaves
that register's column unset** (YMXS rule 1).

- 1(a) The start row leaves the column unset; the row that stops the
  effect may set it, and the register then reads the row's value.
- 1(b) Where one row stops an effect on a register and starts another
  on it, the row leaves the column unset.
- 1(c) R13 is outside the rule: a row may set column 13 while an effect
  runs on R13 (1.6.2).
- 1(d) An effect running when row R - 1 is read runs through the wrap
  (4.5), and the rows of the next pass are bound by 1(a).

**Rule 2: a row sets columns of the effects the tune records, with
numbers the maps assign.**

- 2(a) A row leaves the target, control and count columns of an effect
  whose bit of the effects used (3.3) is 0 unset, and its source column
  unset or set to 0, a stop (YMXS rule 6(a)); a player leaves the
  columns of such an effect unread (4.2), and a reader records the stop
  (7.3).
- 2(b) A source column is 0 or 1 to S, S the source count (3.3); a
  target column is a target this version encodes, 0 to 24 (2.1). A source
  of C columns runs on a target of C registers (2.1.2).
- 2(c) A start whose target column is unset runs on the kept target, the
  last target column set on the effect in frame order, through the wrap
  (4.5). A writer sets the target column on the first start of an
  effect in the first pass, and on the first start at or after the
  repeat row.
- 2(d) A target of two registers names one column for the marker (2.1),
  so a source of two columns runs on the tone targets, on the noise
  targets or on `setEnvelope`, and on one of the three alone.
- 2(e) The marker's column of a target other than `setEnvelope` writes a
  register that reads seven bits or fewer, and every column beside it is
  a whole byte, as is every column of a counted source (3.1.6). A source
  on `setEnvelope` is counted, so both its columns are whole bytes
  (2.1.3).
- 2(f) A counted source on `setR7` has bits 7 and 6 of every row set, the
  directions of the two I/O ports (1.4.2): a tick writes the row whole,
  so a writer sets the two bits where a row's write reads them from the
  player. The structure's value for such a row is the mixer's six bits,
  0 to 63 (YMXS, SPEC.md 5.1).

**Rule 3: a start sets bit 5 of the control column with it, with one
exception, and sets bit 6 where the timer is stopped** (YMXS rules 3
and 4).

- 3(a) The exception: the source started has the row count of the source
  last started on the effect, on the same kept target. The row may then
  leave bit 5 at 0, and the place is left at its row number (1.8.4); a
  stop between the two starts leaves the place as it is (YMXS, SPEC.md
  3.4.3).
- 3(b) A timer is stopped before its first start, after a stop, and
  after its source that plays once has run out (5.1 step 5). Where the
  rows leave open whether a source has run out at the start row, bit 6
  at either value is correct: a stopped timer starts on the select
  either way (4.3 step 5).
- 3(c) A stop leaves the control column and the count column unset.

**Rule 4: a row sets a rate column on the start row of its effect or
while the effect runs** (YMXS rule 5).

- 4(a) The first start on an effect sets the control column and the
  count column: the kept select and count are 0 until a row sets them,
  and a start with bit 6 at 1 and the count column unset writes the kept
  count (4.3 step 4).
- 4(b) A start on a stopped timer sets the control column, with bit 6 as
  rule 3 fixes; a start on a timer counting at the kept select and count
  may leave both columns unset (1.8.4).
- 4(c) A row before the first start of an effect leaves both columns
  unset: a select written to a timer with its source disconnected starts
  it (5.2).

**Rule 5: a row that sets a count of 0 sets bit 4 of the control column
with it.** A row that leaves the control column unset sets a count of 1
to 255, or leaves the count unset (1.9.1).

**Rule 6: the row count and the repeat row divide by the unit the table
packs at.** R divides by k, and RR where below R divides by k (DTX's
R5.6 and R5.11). A writer whose table divides by k packs it as it is;
otherwise it lengthens the table by 6.3.

### 6.3 The padding

An *unset row* leaves every column unset: the frame that reads it leaves
every register and every timer as it is, one frame long.

Initial condition: the table has R rows and a repeat row RR, present, or
absent for a tune that plays once; the unit k is 2 or 4 (k = 1 divides
every table).

1. Where RR is present and RR mod k is other than 0: insert k - (RR mod
   k) unset rows before row RR, and RR increases by that count. Reported
   as `padded: N unset rows at row RR, before the repeat row, so the
   table packs at unit k`, N the rows inserted and RR the repeat row
   before the insert; `N unset row` for N = 1.
2. Where R, after step 1, mod k is other than 0: L is R - RR where RR is
   present, and 0 where absent.
   - Where L is 1 to 63: append the L rows of the loop, rows RR to
     R - 1 in order, again until R divides by k; the loop is then
     written T times in all, 2 or 4. Reported as `padded: the loop's L
     rows written T times, so the table packs at unit k`, `the loop's 1
     row` for L = 1 and `written twice` for T = 2.
   - Otherwise: append k - (R mod k) unset rows. Reported as `padded: N
     unset rows at row R, so the table packs at unit k`, R the row count
     before the append; `N unset row` for N = 1.

End: R and RR divide by k. The rows of the tune keep their order, with
the additions between and after them; a loop written again repeats its
rows, and an unset row adds one frame at its place with every effect
running through it.

Note: a frame added to a loop of fewer than 64 rows lengthens every pass
of the loop by a sixty-fourth or more, where the loop written again keeps
its period.

### 6.4 The check

A check reads a tune file beside the recording it encodes, one frame of
the recording a row, each row 6.3 added beside the frame it encodes. It
reports each condition below as its line: the first seven in table order
and, where one of them is reported, the rows left unread; the rest in row
order over the rows of the frames 7.4 names for an unnamed F, a row read
while fewer than 20 lines have been reported. Decimal figures are H, P,
R, N, X, M, C, W, V, F and J; Rn is a register; the model is the
registers and effects of section 4 after the row; F is the frame of the
recording the row encodes, and r the row's number.

A flag on effect i, 0 or 1, is a kind, 1 a SID voice, 2 a digidrum, 3 a
sinus SID and 4 a sync buzzer, a value, a target register, a select and a
count; it names source N, the source the writer produced from that kind and
value, or 0 for a kind 3, a digidrum outside the recording, or a source
past 127. A source produced from a digidrum plays once; its end row, for a
start at row r of a source of J rows at the flag's select, divisor D, and
count C, at H frames a second, is r + (J × D × C × H + 153,600 + 2,457,599)
divided by 2,457,600, the arithmetic exact.

| condition | reported as |
|---|---|
| the frame rate of the file is H and the recording's is P, H other than P | `the frame rate is H, not P` |
| the table has R rows, and the recording's frames with the rows 6.3 added are N, R other than N | `the table has R rows, not N` |
| the table repeats at RR and the recording at X, RR other than X | `the table repeats at RR, not X` |
| the file has N sources and the writer produced M from the recording, N other than M | `the file has N sources, not M` |
| source N's table has C columns of R rows, and the writer's source N has W rows, C other than 1 or R other than W | `source N is C columns of R rows, not one of W` |
| bit 7 of row R of source N is 1, R other than the last row | `source N row R is a marker` |
| bit 7 of the last row, R, of source N is 0 | `source N row R is not the marker` |
| row N is an unset row 6.3 added, and its frame writes Rn, n 0 to 12 (rule 6) | `N: a row that sets no column wrote Rn` |
| row N is an unset row 6.3 added, and its frame writes R13 (rule 6) | `N: a row that sets no column wrote R13` |
| for i 0 and 1 in order: the flag on effect i is a SID voice on Rt, the model's effect 1 - i runs on Rt a source produced from a digidrum, and the model's effect i runs source S, S other than 0 | `F: effect i runs source S under a drum on its voice` |
| otherwise, the flag on effect i names source N, N other than 0, and the model's effect i runs source 0; K the flag's kind | `F: effect i runs no source where the dump flags kind K` |
| otherwise, the flag on effect i names source N and the model's effect i runs source S on Rt at select P and count C, one of S, t, P and C other than the flag's N, t2, P2 and C2; K and V are the kind and value S was produced from, K2 and V2 those of N | `F: effect i runs source S (kind K value V) on Rt at P/C, not source N (kind K2 value V2) on Rt2 at P2/C2` |
| the flag on effect i is a digidrum naming source N, N other than 0, the model's effect i runs a source, and the row is other than a start on effect i | `F: the drum is not started` |
| the flag on effect i is absent or names source 0, and the model's effect i runs source S, S other than 0: a source produced from other than a digidrum, or from a digidrum with r at or past its end row | `F: effect i runs source S where the dump flags no effect` |
| the row sets the column of R8, R9 or R10, Rn, to V while the model's effect runs on Rn (rule 1) | `F: Rn's column is set to V while an effect runs on it` |
| after the row, Rn is V in the model and W in the recording's frame, n 0 to 12, V other than W, Rn outside the registers the model's effects run on; for R7, W has bits v and v + 3 set for each voice v, 0 to 2, whose volume register the model's effects run a source produced from a digidrum on | `F: Rn is V, not W` |
| the row writes R13 as V and the recording's frame has R13 as W, V other than W; or one of the two has R13 written and the other leaves it | `F: R13 written V, the dump writes W`, with `not written` for a row that leaves R13 and `does not write` for a frame that leaves it |

### 6.5 Errors of a writer

A writer reports a structure outside what this version encodes as one
line, in place of a tune file: N a count, NAME a source's name, V a value
and R a row number.

| condition | reported as |
|---|---|
| the structure has N sources, above 127 (2.2.2) | `the tune runs N sources, and a source column numbers 127` |
| source NAME has the value V, above 127, in row R (3.2.1) | `the source NAME has the value V in row R, and bit 7 of a source's row is the marker` |
| the rate is H, outside 1 to 65,535, the word at 6 (3.3) | `a frame rate of H, and the frame rate is a word, 1 to 65535` |

---

## 7. What a reader reports

### 7.1 The record

A reader reads a tune file (3.3) and performs the frames of section 4
with every register write and each row's effect columns recorded in
place of the chips (R2.4): the figures of the tune once, then one entry
a frame in order; the ticks are outside the record. The record is lines
of JSON in US-ASCII, one entry a line, each line free of spaces,
integers in decimal, the keys of each object in the order this section
lists them, `true` and `false` as JSON defines them, and a line feed,
byte 10, ending every line (YMXS, SPEC.md 7.2).

### 7.2 The first line

`{"rate":H,"effects":E,"sources":[...]}`: `rate` the frame rate H (3.3);
`effects` the effects used E as a decimal integer, 0 to 15; `sources`
the sources in index order, source 1 first, each
`{"rows":[...],"repeat":RR}` with `rows` one list of C times R integers,
0 to 255: the C columns of row 0, then those of row 1, and so on, column
0 of a row first; and `repeat` its RR as its DTX1 header has it, an
integer, RR equal to R included (3.1.4), where the record of the
structure writes `null` for a source that plays once (YMXS, SPEC.md
7.3); `[]` where S is 0. `rows` is every row of the table as the table
has it: the marker of a source that has one, a row with bit 7 set
before the last (3.2.1, 6.4), and the bytes of a counted source and of a
marked one alike, bit 31 of an index entry absent from the record
(3.1.1). A row reads in the order of its columns rather than the order a
tick writes them, which puts the marker's column last (2.1.1).

### 7.3 A frame's entry

For a frame that reads a row, `{"result":0,"w":{...},"e":{...}}`:

- `w` is the registers 4.4 writes, keyed by number as text, `"0"` to
  `"13"`, in ascending numeric order, `"2"` before `"10"`, each with the
  value the register reads: for a column with a set bit, the byte masked
  to the register's width, four for R1, R3, R5 and R13, five for R6, R8,
  R9 and R10, and six for R7; for a column that fills its byte, the byte
  (1.1.3). Note: the width drops the marking bits of 1.1.2 with the bits
  1.1 leaves unassigned. A record reports the six bits of R7 alone: a
  player writes bits 7 and 6 of that register as 1 (1.4.2), and a record
  leaves the two out, so R7's value here is 0 to 63. A register whose
  column the row leaves unset is absent, its bits 6 to 0 unread (1.1.1),
  and `{}` is a row that leaves every register column unset. A register an
  effect runs on is included where the row sets its column (6.1).
- `e` is the effects whose target, source or control column the row sets,
  or whose count column is other than 0, regardless of the effects used
  byte (4.2); keyed by number as text, `"0"` to `"3"`, in ascending order;
  `{}` for a row that leaves every effect column unset. Each is
  `{"target":t,"source":s,"select":p,"count":c,"timer":b,"place":b}`,
  where each of `target`, `source`, `select` and `count` is the value kept
  for the effect (4.3), which a row that leaves its column unset leaves as
  it is: `target` the kept target after 4.3 step 2, 0 to 127; `source` the
  number the last row that set the effect's source column set it to, 0 to
  127, a row that sets a stop setting it to 0 (1.8.3), and 0 until a row
  sets it; a row that stops the timer through its control column (4.3 step
  1) leaves it as it is, and so does the tick that ends a source that
  plays once; `select`, 0 to 7, and `count`, 0 to 255, the kept select and
  count after 4.3 steps 4 and 5, 0 until a row sets them and kept through
  a stop; `timer` bit 6 of the row's control column and `place` bit 5,
  each `true` or `false`, both `false` where the row leaves the control
  column unset.

For the first frame after the end (4.6), `{"result":-1}`, where the
record ends.

### 7.4 The length

The record is the first line and the entries of frames 0 to F - 1, F the
frames the host requires, except that the record of a tune that plays
once ends with its `{"result":-1}` entry. Where the host leaves F
unnamed, F is R + (R - RR) for a tune that repeats, one pass and one
loop, and R + 1 for one that plays once, R and RR the file's (3.3). The
record of a file with an error of 3.3.4 is empty, 0 bytes, and the line a
reader reports of that error stands outside the record: a reader that
writes the record to a stream writes that line to another, which the
host names. Two readers of
one tune file over one F produce one record, byte for byte.

### 7.5 The example

The record of `doc/conformance/tunes/plays-once.ymxr`: four rows at 50
Hz, RR equal to R, S 0, over the unnamed F of 7.4, five frames:

    {"rate":50,"effects":0,"sources":[]}
    {"result":0,"w":{"0":163,"1":2,"2":238,"3":14,"4":238,"5":14,"6":12,"7":56,"8":15,"9":0,"10":0,"11":0,"12":0},"e":{}}
    {"result":0,"w":{"0":142,"1":12,"6":28,"7":49,"8":12},"e":{}}
    {"result":0,"w":{"0":251,"1":4,"6":12,"8":13},"e":{}}
    {"result":0,"w":{"0":89,"1":2,"7":56,"8":15},"e":{}}
    {"result":-1}

Row 0 sets R11 and R12 to 0 with bits 6 and 5 of column 13 at 1 and its
set bit at 0 (1.6), so the entry has `"11":0,"12":0` and R13 is absent.

The entry of frame 8 of `doc/conformance/tunes/retune.ymxr`, whose row
sets columns 14 to 17 to `$8D`, `$81`, `$E5` and `$3C`, a start of source
1 on `setR13` at select 5 and count 60 with both resets, beside columns
0, 2, 4, 6, 10 and 11:

    {"result":0,"w":{"0":72,"2":56,"4":40,"6":8,"10":10,"11":24},"e":{"0":{"target":13,"source":1,"select":5,"count":60,"timer":true,"place":true}}}

### 7.6 The conformance kit

[conformance/README.md](conformance/README.md) defines the test of a
reader: the record of each tune of the kit over the unnamed F of 7.4,
the empty record of `wrong-version.ymxr` included, each with the sha256
and the length `MANIFEST.txt` lists.

---

## 8. What a later version defines

A player of this version is unconstrained in each item below. A number
missing from the list is a clause a version has defined, and the numbers
of the rest stand: 8.1 is defined at 3.3.4 and 8.6 at 4.2.1 and 5.2.1.

**8.2** Targets 25 to 127 (2.1).

**8.3** A source of more than three columns or of values wider than a byte
(3.1.3), and a source (3.1.4) or a tune's table (3.3.3) whose RR is above
R.

**8.4** The row a tick reads where the place is outside the rows of the
source connected (1.8.4; YMXS, SPEC.md 8.4). A start that leaves bit 5 at
0 names a source of the row count of the one last started (rule 3(a)), so
a tune under the rules leaves the place inside them.

**8.5** The unassigned bits and values: select 0 and bit 3 of a control
column (1.9.2), bits 5 and 4 of a coarse column (1.2.2), bits 6 and 5 of
columns 6, 8, 9 and 10, bit 6 of column 7, bit 4 of column 13, and a
source column above S (3.3).
