# plan

What a play call could cost, and what each step is worth. performance.md
holds what it costs today; every figure below is against those, and each
one says whether it was measured on the rig or counted from the 68000's
manual.

Two figures matter and they are not the same. A call is 1,494 to 2,469
cycles on average by tune, and the costliest frame of a tune is 1,764 to
6,450. R4.5 budgets 6,656 a frame, and what it binds is the costliest
frame. Most of what follows moves the average; the steps that move the
costliest frame are named where they are.

---

## Where the time is

DTX's advance is 47 to 65 per cent of an average call and 77 to 84 per
cent of the costliest frame: 4,176 of Turrican - world 4-3's 4,948 and
4,994 of Synergy Credits' 6,450. A refill parses at most one ST4
operation a unit at about 225 to 240 cycles each, and its unit count is
the column count, so fifteen operations is a tune's costliest frame and
the schema's thirty columns set that bound.

The frame procedure is the rest, 588 to 1,203. A column the row leaves
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

## 1. The shape's test dropped on the row that sets no envelope column

`68k/YMXR.S`. Step 5 reads column 13 into `d2` and step 8 tests the
same `d2` again, `tst.b` and `bpl`, 14 cycles. Where step 5 found the
column 0 the answer at step 8 is known, and 62 to 100 per cent of the
rows of seven of the ten tunes are that row.

Steps 6 and 7 stand between the two, so the test goes only where the
paths part after them, and they part two ways:

| | saves a frame | asks for |
|---|---|---|
| a second copy of steps 6 and 7 on the plain path | 14 | about 58 bytes, two more entries in `ymxr_sites`, a macro whose reads carry their own labels |
| columns 11 and 12 written after steps 6 and 7 | 14 | SPEC.md section 4's step order, the reader's record, the rig's model and the kit's pinned rows |

The second was built and measured on the rig over 59,755 frames of ten
tunes: 14 cycles a frame on every tune, 8 to 14 off every tune's
costliest frame, and no frame costs more, with Synergy Credits' at
6,436 against 6,450. The first is counted and not yet built.

Reversing the branch alone, with neither, moves 2 cycles: the plain
path's `beq` taken at 10 becomes a `bne` not taken at 8, and the row
that sets the column pays the 2 back.

## 3. The advance is called at its own address

`movea.l WS_IMAGE(a6),a2` and `jsr IM_ADVANCE(a2)` reach a `bra.w` in
the image's slot: 16 + 18 + 10, 44 cycles to arrive. Init resolves the
slot once and writes the address into a `jsr (abs).l`, 20 cycles.
Counted, 24 a frame; patching the slot's own address rather than the
body's leaves the `bra.w` and saves 14.

## 4. One branch over a run of effects the tune does not run

Each effect the tune does not run costs its own `bra.w` to its own end,
10 cycles. Init walks the effects from 3 down to 0 keeping the head of
the next one it runs, so a run of skipped effects costs one branch
rather than one each. Counted, 30 a frame where the tune runs none, 20
or 10 where it runs one or two, and 19 as the mean of the corpus.

## 5. The effect's patched skip merged into its first read

The two nops init writes over the branch of an effect the tune runs
cost 8 cycles a frame each. Merging the skip into the effect's first
read removes them. Counted, 8 a frame an effect run, and `RUNS` must
write the non-skip case as well: a second init on one loaded copy finds
the site as the first init left it.

---

## 6. Two bits in columns the specification states zero

Steps 4 to 8 test each register column in turn. Two spare bits, read
before the groups they stand for, skip the volumes and the envelope
whole: bits SPEC.md 1.5 and 1.7 state as zero in columns 6 and 13,
which the frame already reads on every row.

Measured on the rig, on a player built with the gate:

| tune | saved a frame |
|---|---|
| Big - Samantha Fox Strip Poker 6 | 97 |
| Chambers of Shaolin 5 - you blew it! | 92 |
| Circus Attractions 2 | 27 |
| DBA 2 | 67 |
| DBA 5 | 60 |
| Retrigger retune, built | 16 |
| Synergy Credits | 62 |
| Turrican - world 4-3 | 58 |
| Turrican 2 - world completed 1 | 49 |
| the nine, weighted by frame | 62 |

The bits ride in columns that stand already, so the column count, the
period, the ring and a refill's unit count are what they were, and so
is the bound of one operation a unit. The two columns pack to 1,368
bytes more over the ten tunes, 3.36 per cent. Synergy Credits'
costliest frame falls, 6,464 to 6,424 on the same measurement.

What it asks for: the tune version goes from $0002 to $0003. A player
holding the gate drops every write it gates when it reads a table
without the bits, so an old player takes a new table and a new player
does not take an old one. The reader of section 7 needs no change,
since `Columns.MASK[6]` masks the bits away and the record is byte for
byte what it was, and the rig's expected write order is unchanged.

The figures above were measured against a baseline repacked from
`ym/convert.py`'s columns rather than the converter's own tables, which
differ by 0 to 7 cycles a frame; the saving is the difference of two
runs on the same tables.

---

## What was measured and left

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

Taken in the order they cost the least to take:

| step | saves a frame | the costliest frame | asks for |
|---|---|---|---|
| 3, the advance called direct | 24, counted | the same | nothing |
| 4, one branch over a run | 19, counted | the same | nothing |
| 5, the merged skip | 8 an effect, counted | the same | nothing |
| 1, the shape's test dropped | 14 | 8 to 14 less | 58 bytes, or section 4's order |
| 6, the two bits | 62, measured | 40 less | version $0003 |

Steps 3 to 5 stand in `68k/YMXR.S` alone and take neither bytes nor
rule with them. Step 1 takes one of the two costs its own section
gives. Step 6 takes the tune file's version. Together they read about
125 cycles off a call of about 2,050, and about 50 off the frame the
budget binds.

Step 2, the frame on `a0`, is taken: 44 cycles a frame and 44 off every
costliest frame, on every one of the ten tunes, which is the figure it
was counted at. performance.md holds what a call costs with it in.
