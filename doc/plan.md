# plan

What a play call could cost, and what each step is worth. performance.md
has what it costs today; every figure below is against those, and each one
records whether it was measured on the rig or counted from the 68000's
manual.

Two figures matter and they are not the same. A call is 1,292 to 2,036
cycles on average by tune, and the costliest frame of a tune is 1,648 to
5,144. R4.5 budgets 6,656 a frame, and what it binds is the costliest
frame. Most of what follows moves the average; the steps that move the
costliest frame are named where they are.

---

## Where the time is

DTX's advance is 46 to 61 per cent of an average call and 58 to 85 per
cent of the costliest frame: 4,130 of Turrican - world 4-3's 4,836 and
2,982 of Synergy Credits' 5,144. A refill parses at most one ST4
operation a unit at about 225 to 240 cycles each, and its unit count is
the column count, so fifteen operations is a tune's costliest frame and
the schema's thirty columns set that bound.

The frame procedure is the rest, 510 to 1,092. A column the row leaves
unset costs 22 cycles and a tone pair 46, and most columns are unset:
a row sets 0.6 to 13.0 of the fourteen register columns, 3.2 on the
median tune. Whole groups go unset, which a gate can skip,
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

A square's tick is in place: 88 cycles of instructions against 108 and
130, and 152 with the 68000's entry and the `rte` against 172 and 194.
Measured on the rig, and performance.md has the figure.

A digidrum's source is many rows played once, so its tick is the row path
every time, 172 cycles, and its place must step. Of the 108 its
instructions cost, 40 are the chip writes, 28 the step, 16 the end of
interrupt, 16 the level dropped and 8 the marker test. Only the
16 is removable without a register the player does not have: reading and
stepping through an address register, `move.b (a0)+,YM_SELECT+2.w`, is 16
against the 52 the two absolute longs cost, but a library cannot claim four
of its host's registers.

A sample's timer is near its floor. A square's is not.

---

## What was measured and left

**Two bits in columns the specification fixes at zero.** A bit read before
a group of register columns, saying whether the row sets any of them,
skips the group on one test. Measured at 62 cycles a frame weighted over
nine tunes against the player of five steps ago, where it was the
largest step in this document.

Against the player as it stands it is worth 17, and it costs two tunes.
The five steps above removed the same work from another side: the row that
sets no envelope column already skips step 8, and the four before that cut
the entry, the call, the effects' branches and their first reads. What is
left to gate is the volume group's three columns, 66 cycles.

The gate's bit has one place to sit. Only `d2` reaches step 6 with anything
the row set, and `d2` is column 13, so the bit belongs to column 13. A row
that writes a volume then sets column 13, and a column 13 that is not 0 is
the row that follows the longer of the two paths the step above left it.
Measured over 40,000 rows a tune, and counted at 20 cycles for a gate that
skips and 18 for one that does not:

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
two tunes that read slower. It is not adopted.

**A thirty-first column of the same bits.** It saves more a row,
216 where the row sets none of the three groups. The ring is 960 bytes
and 960 does not divide by 31, so the period goes from 30 to 32: a
refill grows one unit at unit 2 and two at unit 1, the state block
grows 1,056 to 1,088 bytes, and the packed table grows 2.7 to 12.4 per
cent. Every refill window is redrawn, and DBA 2's heaviest refill goes
from 18 operations to 21, its costliest call to about 6,150 to 6,330
against the budget's 6,656, where the model fitted to read it has
residuals of ±367. SPEC.md R3.5 also requires that a column be one value,
and a mask column is not one.

**Three fusions in ST4's decoder**: a one-unit literal run, a one-unit
rep match, and a two-unit new-offset match, each fused at the gamma's
exit. All three were counted and all three were wrong as written. ST4
is where the costliest frame is - fifteen operations at 225 to 240 -
and it is the only place with room to move that frame far, so it needs
a separate pass rather than these three repaired.

---

## The order

Every step this document listed is in place, each measured on the rig:

| step | counted | measured |
|---|---|---|
| the frame on `a0` | 44 | 44 on every tune |
| the advance called at its address | 24 | 24 on every tune |
| one branch over a run of effects | 19 | 30, 20 or 10 by the effects a tune runs |
| an effect's head is its first read | 8 an effect | 16, 8 or 0 |
| the shape's test dropped | 14 | 12 to 14, no tune more |
| a separate tick for a square | 31 a tick | 88 against 108 and 130 |
| every source resolved at init | 52 a start | 21 to 38 a frame |
| DTX's state block in a6 | 36 | 36 off the advance, 12 off the call |
| a separate tick for a one-row source | 74 a tick | 568 a frame on the kit's retune |

The first four were counted before they were built and each measured at
its count. Synergy Credits reads 2,036 cycles a call against the 2,469
this document opened at and 2,585 cycles of ticks against 5,380, so
4,621 a frame against 7,849. performance.md has the call, and `-cycles`
reads all three back.

What was left was the costliest frame. A refill parses at most one ST4
operation per unit of its window, so the frame R4.5 binds is one operation
per unit at 225 to 240 cycles each, and no step above touches it. That
pass has now been made, in ST4 (research.md, "A penalty a block, against
the costliest frame"), and it moved the question.

The window is 30 units at unit 1 and 15 at unit 2, and the worst refill
measured is 15 operations at unit 1 against 9 to 11 at unit 2: 52 per cent
of R4.5's 6,656-cycle budget against 31. A tune packed at unit 1 only
because its row count or its repeat row was odd. So the lever was the row
count, not the packer: low with one row fewer packs at unit 2, and its
worst refill falls from 15 to 9. A penalty a block in ST4 (`st4 -p8`) also
helps at unit 1, from 15 to 12 for two per cent more bytes, and does
little
at unit 2; it stays a flag of ST4, since a default in DTX means changing
three packers.

SPEC.md 6, rule 6 is the rule that follows: a writer whose tune does not
divide by the unit adds rows that set no column at the repeat row until it
divides, and then writes a loop of fewer than 64 rows again or adds rows
that set no column at the end; a row that sets no column leaves every
register as it is and every timer running, and a loop written again plays
as it did. The converter applies it (tools.md 4.4), so every tune packs
at unit 2 and the window of every tune is 15 units. Five of the ten tunes
packed at unit 1 before it; measured again at unit 2, their calls fell by a
seventh to a sixth on average and by a tenth to more than a third in the
costliest frame, Synergy Credits' from 6,358 to 5,144 (performance.md). The
costliest frame of any tune is now that one, 1,512 cycles under the budget.
