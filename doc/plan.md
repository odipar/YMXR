# plan

What a play call could cost, and what each step is worth. performance.md
holds what it costs today; every figure below is against those, and each
one says whether it was measured on the rig or counted from the 68000's
manual.

Two figures matter and they are not the same. A call is 1,382 to 2,362
cycles on average by tune, and the costliest frame of a tune is 1,660 to
6,342. R4.5 budgets 6,656 a frame, and what it binds is the costliest
frame. Most of what follows moves the average; the steps that move the
costliest frame are named where they are.

---

## Where the time is

DTX's advance is 50 to 69 per cent of an average call and 79 to 86 per
cent of the costliest frame: 4,166 of Turrican - world 4-3's 4,848 and
4,984 of Synergy Credits' 6,342. A refill parses at most one ST4
operation a unit at about 225 to 240 cycles each, and its unit count is
the column count, so fifteen operations is a tune's costliest frame and
the schema's thirty columns set that bound.

The frame procedure is the rest, 500 to 1,119. A column the row leaves
unset costs 22 cycles and a tone pair 46, and most columns are unset:
a row sets 0.6 to 13.0 of the fourteen register columns, 3.2 on the
median tune. Whole groups go unset, which is what a gate can skip,
measured over 40,000 rows a tune:

| tune | sets no tone | no volume | no envelope | columns a row |
|---|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | 58% | 93% | 100% | 0.6 |
| Chambers of Shaolin 5 - you blew it! | 21% | 86% | 100% | 2.4 |
| Circus Attractions 2 | 0% | 0% | 75% | 6.5 |
| DBA 2 | 23% | 56% | 98% | 2.2 |
| DBA 5 | 31% | 51% | 95% | 2.8 |
| Digidrum preempt, built | 0% | 100% | 0% | 5.1 |
| Retrigger retune, built | 0% | 98% | 0% | 5.0 |
| Synergy Credits | 26% | 82% | 62% | 3.2 |
| Turrican - world 4-3 | 0% | 40% | 94% | 3.7 |
| Turrican 2 - world completed 1 | 0% | 0% | 0% | 13.0 |

The volume group is unset on 40 per cent of the rows or more on eight
of the ten and the envelope group on 62 per cent or more on seven; on
the rest a gate reads the bit and finds the group set.

---

## What a sample's tick costs

A square's tick is taken: 88 cycles of its own instructions against 108
and 130, and 152 with the 68000's entry and the `rte` against 172 and
194. Measured on the rig, and performance.md holds it.


A digidrum's source is many rows played once, so its tick is the row
path every time, 172 cycles, and its place must step. Of the 108 its
instructions cost, 40 are the chip writes, 28 the step, 16 the end of
interrupt, 16 the level dropped and 8 the marker test. Nothing but the
16 is removable without a register the player does not have: reading and
stepping through an address register, `move.b (a0)+,YM_SELECT+2.w`, is
16 against the 52 the two absolute longs cost, but a library cannot hold
four registers of its host's.

A sample's timer is near its floor. A square's is not.

---

## What was measured and left

**Two bits in columns the specification states zero.** A bit read before
a group of register columns, saying whether the row sets any of them,
skips the group on one test. Measured at 62 cycles a frame weighted over
nine tunes against the player of five steps ago, where it was the
largest step in this document.

Against the player as it stands it is worth 17, and it costs two tunes.
The five steps above took the same work from another side: the row that
sets no envelope column already skips step 8, and the four before that
took the entry, the call, the effects' branches and their first reads.
What is left to gate is the volume group's three columns, 66 cycles.

The gate's bit has one place to sit. Only `d2` reaches step 6 holding
anything the row set, and `d2` is column 13, so the bit is column 13's.
A row that writes a volume then sets column 13, and a column 13 that is
not 0 is the row that takes the longer of the two paths the step above
gave it. Measured over 40,000 rows a tune, and counted at 20 cycles for
a gate that skips and 18 for one that does not:

| tune | rows that set no volume | net a frame |
|---|---|---|
| Digidrum preempt, built | 100% | -46 |
| Retrigger retune, built | 98% | -44 |
| Big - Samantha Fox Strip Poker 6 | 93% | -41 |
| Chambers of Shaolin 5 - you blew it! | 86% | -35 |
| Synergy Credits | 81% | -33 |
| DBA 2 | 56% | -11 |
| DBA 5 | 53% | -8 |
| Turrican - world 4-3 | 40% | 0 |
| Turrican 2 - world completed 1 | 0% | **+18** |
| Circus Attractions 2 | 0% | **+29** |
| the mean of the ten | | -17 |

Putting the bit in column 6 instead, which costs a read of 12 on every
row and leaves column 13 alone, reads -9 as the mean and costs Turrican
- world 4-3 and the two above. Gating the tone group rather than the
volumes reads -4: its six columns are idle on 0 to 58 per cent of rows,
and 138 cycles saved on a quarter of them does not pay 18 on the rest.

17 cycles a frame is the tune file's version at $0003, a bit's meaning
in SPEC.md 1.5 and 1.7, the converter, the conformance kit's tunes, and
two tunes that read slower. It is not taken.

**A thirty-first column holding the same bits.** It saves more a row,
216 where the row sets none of the three groups. The ring is 960 bytes
and 960 does not divide by 31, so the period goes from 30 to 32: a
refill grows one unit at unit 2 and two at unit 1, the state block
grows 1,056 to 1,088 bytes, and the packed table grows 2.7 to 12.4 per
cent. Every refill window is redrawn, and DBA 2's heaviest refill goes
from 18 operations to 21, its costliest call to about 6,150 to 6,330
against the budget's 6,656, where the model fitted to read it has
residuals of ±367. SPEC.md R3.5 also has it that a column holds one
value, and a mask column holds none.

**DTX's state block passed in a6.** The park is 16 + 4 + 16, 36 cycles
a frame. It changes DTX's abi.md sections 2 and 3, the `DTX_PARK`
field three packagers print, a test, and the one ABI DTX0 and DTX1
share with DTX2.

**Three fusions in ST4's decoder**: a one-unit literal run, a one-unit
rep match, and a two-unit new-offset match, each fused at the gamma's
exit. All three were counted and all three were wrong as written. ST4
is where the costliest frame is - fifteen operations at 225 to 240 -
and it is the only place with room to move that frame far, so it wants
a pass of its own rather than these three repaired.

---

## The order

Every step this document held is taken, each measured on the rig:

| step | counted | measured |
|---|---|---|
| the frame on `a0` | 44 | 44 on every tune |
| the advance called at its own address | 24 | 24 on every tune |
| one branch over a run of effects | 19 | 30, 20 or 10 by the effects a tune runs |
| an effect's head is its first read | 8 an effect | 16, 8 or 0 |
| the shape's test dropped | 14 | 12 to 14, no tune more |
| a square's own tick | 31 a tick | 88 against 108 and 130 |
| every source resolved at init | 52 a start | 21 to 38 a frame |

The first four were counted before they were built and each measured at
its count. Synergy Credits reads 2,356 cycles a call against the 2,469
this document opened at and 4,469 cycles of ticks against 5,380, so
6,825 a frame against 7,849. performance.md holds both.

What is left is where the costliest frame is. A refill parses at most
one ST4 operation a unit and there are fifteen units, so the frame R4.5
binds is fifteen operations at 225 to 240 apiece, and nothing above
touches it. ST4 wants a pass of its own.
