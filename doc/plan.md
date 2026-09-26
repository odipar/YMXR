# plan

What a play call could cost, and what each step saves. performance.md has
the cost today; every figure below compares with it, and each is marked as
measured on the rig or counted from the 68000's manual.

Two figures matter. A call is 1,292 to 2,030
cycles on average by tune, and the costliest frame of a tune is 1,648 to
5,094. R4.5 budgets 6,656 a frame and binds the costliest frame. Most of
what follows moves the average, and the text points out each step that
moves the costliest frame.

---

## Where the time is

DTX's advance is 46 to 61 per cent of an average call and 58 to 84 per
cent of the costliest frame: 3,826 of Turrican - world 4-3's 4,532 and
2,932 of Synergy Credits' 5,094. A refill parses at most one ST4
operation a unit at about 200 to 270 cycles each, and its window is the
schema's thirty columns, fifteen units at unit 2, so fifteen operations
bound a tune's costliest frame.

The frame procedure is the rest, 510 to 1,092. A column the row leaves
unset costs 22 cycles and a tone pair 46, and most columns are unset: a
row sets 0.6 to 13.0 of the fourteen register columns, 3.3 on the median
tune. Whole groups go unset, and a gate can skip them; over 40,000 rows a
tune:

| tune | tone unset | volume unset | envelope unset | columns a row |
|---|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | 58% | 94% | 100% | 0.6 |
| Chambers of Shaolin 5 - you blew it! | 21% | 86% | 100% | 2.4 |
| Circus Attractions 2 | 0% | 0% | 75% | 6.5 |
| DBA 2 | 23% | 56% | 98% | 2.2 |
| DBA 5 | 31% | 51% | 95% | 2.8 |
| Digidrum preempt, built | 0% | 100% | 0% | 5.1 |
| Retrigger retune, built | 0% | 98% | 0% | 5.0 |
| Synergy Credits | 26% | 82% | 62% | 3.2 |
| Turrican - world 4-3 | 0% | 40% | 94% | 3.7 |
| Turrican 2 - world completed 1 | 0% | 0% | 0% | 13.0 |
| capture | 27% | 42% | 88% | 3.3 |

The volume group is unset on 40 per cent of the rows or more on nine of
the eleven and the envelope group on 62 per cent or more on eight; on the
rest a gate reads the bit and finds the group set.

---

## What a sample's tick costs

A square's tick is in place: 88 cycles of instructions against 96 and
114, and 152 with the 68000's entry and the `rte` against 160 and 178.
The rig measures it, and performance.md has the figure.

A digidrum's source is many rows played once, so its tick is the row path
every time, 160 cycles, and its place must step. Of the 96 its instructions
cost, 36 are the chip writes, 20 the step, 16 the end of interrupt, 16 the
level dropped and 8 the marker test. The lean core removes the end of
interrupt and the level dropped, 32 (performance.md). A further cut needs a
register the player lacks: reading and stepping through an address
register, `move.b (a0)+,YM_SELECT+2.w`, is 16 against the 40 the read
through the program counter and its step cost, but a library cannot claim
four of its host's registers.

---

## What was measured and left

**Two bits in columns the specification fixes at zero.** A bit read before
a group of register columns, 1 where the row sets any of them, skips the
group on one test. Weighed when this document opened (#48), at
62 cycles a frame over nine tunes, it was the largest step in it.

Against the player as it stands it saves 6 cycles a frame on the mean of
the eleven fixtures, and it costs six of them. Five steps of the order
below removed part of that work first: a row with the envelope columns
unset already skips step 8, and the four before that cut the entry, the
call, the effects' branches and their first reads. What is left to gate is
the volume group's three columns, 66 cycles.

In one place the player reads the gate's bit already: only `d2` reaches
step 6 with a value the row set, and `d2` is column 13. A row that writes a
volume then sets column 13, and a column 13 other than 0 is the row that
follows the longer of the two paths the step above left it. The rig walks
each row through the player's instructions over 40,000 rows a tune, a row
that moves to the longer path along both, and counts a gate that skips at
20 cycles and one that does not at 18 (`-cycles`):

| tune | rows with the volume unset | net a frame |
|---|---|---|
| Digidrum preempt, built | 100% | -46 |
| Retrigger retune, built | 98% | -44 |
| Big - Samantha Fox Strip Poker 6 | 94% | -39 |
| Synergy Credits | 82% | -32 |
| Chambers of Shaolin 5 - you blew it! | 86% | -30 |
| DBA 2 | 56% | **+5** |
| DBA 5 | 51% | **+9** |
| Turrican 2 - world completed 1 | 0% | **+18** |
| capture | 42% | **+19** |
| Turrican - world 4-3 | 40% | **+21** |
| Circus Attractions 2 | 0% | **+57** |
| the mean of the eleven | | -6 |

Putting the bit in column 6 instead, which costs a read of 12 on every row
and leaves column 13 alone, reads -8 as the mean, the better of the two,
and costs four tunes: Circus Attractions 2, Turrican - world 4-3, Turrican
2 - world completed 1 and capture. Column 13 pays for the row that moves
to the longer path. Gating the tone group rather than the volumes, its bit
read the same way, reads +7: its six columns are idle on 0 to 58 per cent
of rows, and the 106 it saves on a sixth of them does not pay the 30 it
costs on the rest.

8 cycles a frame, the better gate, is a version of the tune file, a bit's
meaning in SPEC.md 1.5 and 1.7, the converter, the conformance kit's
tunes, and four tunes that read slower. The gate is left out.

**A thirty-first column of the same bits.** Weighed when this document
opened (#48), against the player of then. It saves more a row, 216 where
the row leaves all three groups unset. The ring is 960 bytes and 960 does
not divide by 31, so the period goes from 30 to 32: a refill grows one
unit at unit 2 and two at unit 1, the state block grows 1,056 to 1,088
bytes, and the packed table grows 2.7 to 12.4 per cent. Every refill
window is redrawn, and DBA 2's heaviest refill goes from 18 operations to
21, its costliest call to about 6,150 to 6,330 against the budget's 6,656,
where the model fitted to read it has residuals of ±367. requirements.md
R3.5 also requires a column to be one value, and a mask column is several.

**Three fusions in ST4's decoder**: a one-unit literal run, a one-unit
rep match, and a two-unit new-offset match, each fused at the gamma's
exit. Each was counted, and each was wrong as written. Most of the
costliest frame is ST4's refill - fifteen operations at 200 to 270 - and
ST4 alone has room to move that frame far, so it needs a separate pass
rather than these three repaired.

---

## The order

Every step this document listed is in place, each measured on the rig when
it landed; the player has moved since, and performance.md has the cost of a
call now:

| step | counted | measured |
|---|---|---|
| the frame on `a0` | 44 | 44 on every tune |
| the advance called at its address | 24 | 24 on every tune |
| one branch over a run of effects | 19 | 30, 20 or 10 by the effects a tune runs |
| an effect's head is its first read | 8 an effect | 16, 8 or 0 |
| the shape's test dropped | 14 | 12 to 14, no tune more |
| a separate tick for a square | 31 a tick | 88 against 96 and 114 |
| every source resolved at init | 52 a start | 21 to 38 a frame |
| DTX's state block in a6 | 36 | 36 off the advance, 12 off the call |
| a separate tick for a one-row source | 74 a tick | 445 a frame on the kit's retune |

The first four were counted before they were built and each measured at
its count. Synergy Credits reads 2,030 cycles a call against the 2,469
this document opened at and 2,585 cycles of ticks against 5,380, so
4,615 a frame against 7,849. performance.md has the call, and `-cycles`
reads all three back.

What was left was the costliest frame. A refill parses at most one ST4
operation per unit of its window, so the frame R4.5 binds is one operation
per unit at 200 to 270 cycles each, and every step above leaves it as it
was. ST4 has since made that pass (research.md, "A penalty a block,
against the costliest frame").

The window is 30 units at unit 1 and 15 at unit 2, and a refill of Turrican
2 - world completed 1 parses fifteen operations, the bound at unit 2. The
heaviest refill of the eleven tunes costs 3,826 cycles at unit 2, 57 per
cent of R4.5's 6,656-cycle budget, against 4,754 at unit 1 and 71 per cent
when the play call was measured again (#171). A tune packed at unit 1 only
because its row count or its repeat row was odd, so the row count sets the
unit: `low`, a tune of ST4's research, packs at unit 2 with one row fewer,
and its worst refill falls from 15 to 9. A penalty a block in ST4 (`st4
-p8`) also helps at unit 1, from 15 to 12 for two per cent more bytes, and
does little at unit 2; it stays a flag of ST4, since a default in DTX means
changing three packers.

SPEC.md 6, rule 6 follows: a writer adds unset rows at the repeat row until
the tune divides by the unit, and then writes a loop of fewer than 64 rows
again or adds unset rows at the end; an unset row leaves every register as
it is and every timer running, and a loop written again plays as it did.
The converter applies it (tools.md 4.4), so every tune packs at unit 2 and
the window of every tune is 15 units. Six of the eleven tunes packed at
unit 1 before it; measured again at unit 2, their calls fell by a ninth to
a sixth on average and by a fifteenth to more than a quarter in the
costliest frame, Synergy Credits' from 5,778 to 5,094 (performance.md). The
costliest frame of any tune is now that one, 1,562 cycles under the budget.