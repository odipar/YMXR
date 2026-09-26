# terminology

The terms of the two chips and of the format, one word for each thing
(requirements.md R0.6 to R0.9). [glossary.md](glossary.md) lists every
term with the section that defines it. YMXS, SPEC.md 2 defines the range
of a register, what a volume bit selects and what a write to R13 does,
and this document cites it.

**Conventions.** A clause is cited by number, 2.3. A bold term is defined
in the clause that bolds it. A range includes both ends. `Note:` begins
an informative sentence.

---

## 1. The sound chip

**1.1 The chip.** The **YM2149** is Yamaha's AY-3-8910, clocked at 2 MHz
on an Atari ST. A **register** is one byte of its settings: a write is the
one way software changes a register, and a register keeps its value until
written again. Sixteen registers: fourteen are the sound, and R14 and R15
are the two I/O ports, which belong to the host.

| register | bits | what it is |
|---|---|---|
| R0, R1 | 8 + 4 | voice A **tone period**, fine and coarse: one 12-bit number |
| R2, R3 | 8 + 4 | voice B tone period |
| R4, R5 | 8 + 4 | voice C tone period |
| R6 | 5 | **noise period** |
| R7 | 8 | **mixing** in bits 5 to 0; the I/O port directions in bits 7 and 6 |
| R8, R9, R10 | 5 each | voice A, B, C **volume**: bits 3 to 0 the level, bit 4 the envelope's level instead |
| R11, R12 | 8 + 8 | **envelope period**, fine and coarse: a 16-bit divider |
| R13 | 4 | **envelope shape** |
| R14, R15 | 8 each | the two I/O ports |

**1.2 Bits 7 and 6 of R7.** Bits 7 and 6 of R7 select the direction of
the two I/O ports. A write of R7 by a player writes bits 5 to 0 from the
tune and bits 7 and 6 as 1, the port directions an ST runs with; a reader
reports bits 5 to 0 (SPEC.md 7). Note: YMXS, SPEC.md 8.8 leaves the two
bits to a later version; this format fixes them.

**1.3 Generators.** A **signal** is a series of values at a rate: a square
wave, noise, a sample. Five **generators** produce them: three
**tone generators**, each a counter that flips its output at the end of
each period, producing a square wave; one **noise generator**, a shift
register producing a pseudo-random bit pattern, one for the three voices;
and one **envelope generator**, a counter walking one of sixteen shapes.

**1.4 Period.** The **period** of a generator is the length of one cycle
in steps of its clock; a larger period is a lower pitch.

    tone frequency     = 2,000,000 / (16 x tone period)
    noise clock        = 2,000,000 / (16 x noise period)
    envelope frequency = 2,000,000 / (256 x envelope period)

**1.5 Noise period.** R6, five bits, 0 to 31: the divider of the noise
generator (1.4). One generator feeds the three voices, which differ in
volume and mixing alone.

**1.6 The envelope.** The envelope shape is four bits. Four of the sixteen
shapes repeat, two sawtooths and two triangles; the other twelve run once
and stop. Every write to R13 restarts the envelope, the value changed or
the same (YMXS, SPEC.md 2.5), so a row that leaves the shape as it is
leaves R13 unwritten, and the set bit of column 13 encodes that (SPEC.md
1.6).

Note: the divisor of the envelope period is 256, so periods 18 and 17 are
434 and 460 Hz, about a semitone apart; a sync buzzer (5.4) sets the pitch
from a timer instead.

**1.7 Voices.** Three **voices**, A, B and C. Each has a **volume**, R8, R9
or R10, and a **mixing** setting in R7: which of the voice's tone and the
noise reach it. In a volume register bits 3 to 0 are the level, and bit 4
set selects the level of the envelope generator instead, the level bits
then unread (YMXS, SPEC.md 2.3). A set bit among bits 5 to 0 of R7
silences what it selects (YMXS, SPEC.md 2.4).

**1.8 The DAC.** The **DAC** is the ladder of output levels, 16 for a
volume register and 32 for the envelope, close to logarithmic. A sample
is amplitudes and a volume register is an index into the ladder; a writer
converts each amplitude to an index (R5.3).

---

## 2. The timers

**2.1 The MFP.** The **MFP**, the MC68901, has four **timers**, A to D,
clocked at 2,457,600 a second, unrelated to the YM2149's clock. Effect 0
runs on Timer A, effect 1 on Timer D, effect 2 on Timer B and effect 3 on
Timer C (SPEC.md 2.3); a player claims the timers of the effects used at
init, before the first row is written (2.5). Timer C is the operating
system's 200 Hz clock: a player that claims it stops that clock, and the
host calls the player from another interrupt (BINARIES.md 5).

**2.2 The rate.** A timer divides the clock twice: by a **prescaler**, 4,
10, 16, 50, 64, 100 or 200, then by a **timer count**, 1 to 255, or 0,
which counts 256. The timer counts the divided clock down from its count
and raises an interrupt at zero. That interrupt is a tick (4.2), and the
**rate** of the timer is its ticks a second:

    rate = 2,457,600 / (prescaler x timer count)

The slowest rate, a count of 0 at a prescaler of 200, is 48 a second; the
fastest, a count of 1 at a prescaler of 4, is 614,400. A rate above
125,000 is an error of the structure (YMXS, SPEC.md 3.3.4).

**2.3 The two registers.** The **timer control register** of a timer has
its prescaler select in three bits: 1 for 4, 2 for 10, 3 for 16, 4 for
50, 5 for 64, 6 for 100, 7 for 200; 0 stops the timer. Timers A and B
have one each, the select in bits 2 to 0; a player writes bit 3, the mode
bit, and bits 7 to 4 as 0. Timers C and D share one, C's select in bits 6
to 4 and D's in bits 2 to 0, and a write for one timer leaves the bits of
the other as they are. The **timer data register** of a timer is its
timer count.

| timer | control register | data register | vector | enable and mask bit |
|---|---|---|---|---|
| A | $FFFA19, bits 3 to 0 | $FFFA1F | $134 | bit 5 of IERA and IMRA |
| B | $FFFA1B, bits 3 to 0 | $FFFA21 | $120 | bit 0 of IERA and IMRA |
| C | $FFFA1D, bits 7 to 4 | $FFFA23 | $114 | bit 5 of IERB and IMRB |
| D | $FFFA1D, bits 3 to 0 | $FFFA25 | $110 | bit 4 of IERB and IMRB |

IERA is $FFFA07 and IERB $FFFA09; the pending register of each is 4
bytes on, the in-service register 8 and the mask register 12.

**2.4 A write to a running timer.** A count written while the timer runs
loads when the running count reaches zero; one written while the timer
is stopped loads at once, and the select written after it starts the
timer from that count (YMXS, SPEC.md 3.3.5, 3.4.1). A player stops a
timer by writing select 0 and clearing its pending bit, so a tick latched
while the stop runs is dropped; the timer's reset, bit 6 of the control
column, stops the timer that way before the count and the select are
written (SPEC.md 1.9).

**2.5 Claim and release.** A player **claims** a timer at init, for each
effect the effects used byte marks: it stops the timer, writes its
vector, clears its pending bit, and sets its enable bit and its mask bit.
It **releases** it at stop, the reverse: it stops the timer and clears
its enable bit, its mask bit and its pending bit. A player leaves a timer
outside the tune's as it is. Note: YMXS, SPEC.md 8.5 leaves what a claim
comprises to a later version; this format fixes it.

---

## 3. Tables, rows and procedures

**3.1 A table.** A **table** is `R` rows of `C` columns, every value `W`
bytes, 1, 2 or 4, and a row `RR` it repeats to after its last row, `RR`
equal to `R` where it plays once (DTX, SPEC.md 1). `R`, `C`, `RR` and `W`
are its **metadata**; the layout of the rows is one of DTX's variants
(R1.2).

**3.2 A row.** A **row** is one step of a table: `C` values, one a
**column**, each `W` bytes. A table has values alone, and the format
defines what a column means (R2.1). An **unset row** is a row of the tune's
table that leaves every column unset: a frame that reads it leaves every
register as it is and every timer running (SPEC.md 6, rule 6).

**3.3 Yielding.** **Yielding** is reading the rows of a table one at a
time, in order. A clock keeps a row number in the table: the first
advance yields row 0, each advance the row after, and the advance after
row `R` - 1 yields row `RR`, or ends the table where `RR` is `R`. Two
clocks on one table keep two row numbers, and each moves its number
alone. A table that repeats has a row after every row. A timer's row
number in its source is its place (YMXS, SPEC.md 3.4.2). Note: YMXS,
SPEC.md 8.4 leaves the place before the first start that resets it to a
later version; this format fixes it at row 0 (SPEC.md 4.1 step 4).

**3.4 A procedure.** A **procedure** writes a row to the chips. A clock
advances a table one row and calls a procedure with that row. A frame
(4.1) and a tick (4.2) differ in the table, the clock and the procedure.

---

## 4. Frames and ticks

**4.1 A frame.** The clock of the tune is the host, which calls the
player at the frame rate, fixed for the tune (SPEC.md 3.3). Its table is
the tune's, its procedure writes the columns the row sets to the two
chips (SPEC.md 4), and one advance and call is a **frame**. After the end
of a tune that plays once, a call leaves every register as it is and
reports -1 (SPEC.md 4).

**4.2 A tick.** The clock of a timer is its interrupt (2.2). Its table is
a source, its procedure a target, which writes one, two or three registers
(SPEC.md 2.1, 5), and one advance and call is a **tick**. Note: YMXS,
SPEC.md 8.6 leaves where a tick falls in a frame, and what a tick of a
timer with no source connected performs, to a later version; this format
fixes both (SPEC.md 4.2.1, 5.2.1).

**4.3 Row and frame.** A document about storing or packing values uses
row (3.2); a document about playing uses frame (4.1).

**4.4 What each writes.** A frame writes the registers its row sets; a
tick writes the registers of its target. Under rule 1 of SPEC.md 6 a row
leaves a register an effect runs on unset, R13 excepted.

---

## 5. Effects

**5.1 An effect.** An **effect** is a source connected to a target on one
timer, at the rate of its prescaler and count (YMXS, SPEC.md 1.7). Four
columns encode it: the target column, the source column, the control
column and the count column (SPEC.md 1.8, 1.9). The **effect rate** is
the rate of the timer (2.2), set by the control column and the count
column of the row that starts or retunes the effect, and unchanged by a
row that leaves those columns unset.

**5.2 Square wave and SID voice.** A **square wave** is a source of two
rows, a level and 0, repeating to row 0. A **SID voice** is a square wave
run on a voice's volume register at a rate in ratio to the note the voice
plays, so a row sets the rate where the note changes.

**5.3 Sample and digidrum.** A **sample** is a recording, one value a
tick, converted to the levels of the DAC (1.8). A **digidrum** is a sample
run on a voice's volume register: a source of many rows that plays once,
at the rate of the recording. Note: the converter of this repository
closes a drum with a row of level 13 and the marker, so the voice is left
at 13 when the timer stops.

**5.4 Sync buzzer.** A **sync buzzer** is a source of one row run on
`setR13`: each tick restarts the envelope (1.6), so the rate of the timer
is the pitch and the row is the shape.

**5.5 The kinds.** The tools report a source as a square, a drum or a
buzzer (tools.md), the kinds of 5.2 to 5.4, and the converter drops a
sinus SID, a fourth kind of the dumps, with a note (tools.md). The format
encodes a source as a table (SPEC.md 2.2), and the kind is outside the
format: a player reads the table alone.
