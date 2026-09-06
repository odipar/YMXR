# performance

What a play call costs, in cycles, measured on the eight tunes under
`ym/test` by the rig's cycle counter (tools.md, the rigs): the 68000's own
cycles, with no wait state, over every frame of a tune played through its
wrap once. A tick handler's cost is its own instructions, from its vector
to its `rte`; the 68000 spends 44 cycles more entering an interrupt and
20 on the `rte`.

## The play call

The call, and the share of it spent in DTX's advance: one column
refilled a row out of its ST4 data set, a period's bytes of it at once.

| tune | frames | on average | at most | the advance on average | in the costliest frame |
|---|---|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | 480 | 1504 | 3400 | 950 | 2814 |
| Chambers of Shaolin 5 - you blew it! | 1020 | 1550 | 3470 | 941 | 2842 |
| Circus Attractions 2 | 180 | 1696 | 3424 | 1149 | 2838 |
| Digidrum preempt, built | 840 | 1773 | 3582 | 903 | 2868 |
| Retrigger retune, built | 1200 | 1623 | 3680 | 867 | 2888 |
| Synergy Credits | 10800 | 2048 | 4746 | 939 | 3080 |
| Turrican - world 4-3 | 3720 | 1624 | 4794 | 880 | 4132 |
| Turrican 2 - world completed 1 | 360 | 1749 | 4434 | 1098 | 3716 |

A table packs at unit 2 and a period of thirty rows, the column count,
so a refill is fifteen units of two bytes and one comes every row
(tools.md, Convert). What those units cost depends on how the column
packed: a run of long matches costs 12 cycles a unit to copy and a run
of short operations, each a length and an offset read bit by bit, 175
to 220 an operation to parse, which is the spread between the tunes'
averages. Unit 1
packs the corpus to 0.69 bytes a frame against 0.81 (experiments.md) and
costs more to decode: on Turrican - world 4-3 the advance 1,130 on
average and 4,448 at most against 880 and 4,132, and the play call 1,874
and 5,110 against 1,624 and 4,794; `-k1` packs at it. A larger period
would make the refill larger and rarer: packed at unit 1 and 283 rows,
the only period dividing its 5,377-row loop before the converter padded
it, Synergy Credits refilled 283 bytes at a time, from 4,792 to 19,742
cycles, and its costliest frame was 20,936.

The frame procedure is the rest, from 550 to 1,110 cycles on average:
the fourteen register columns' tests and the writes they admit, the
effects' columns and the call's own entry and exit. An effect the tune
does not run is jumped over, two nops standing at its columns' head
where the tune runs it; an effect it runs tests its three set-bit
columns in one pass and reads its count alone where none is set; a tone
period and the envelope period branch on a zero byte before the bit
beside it; a column's select is formed only where the column is set;
and the frame stands inline in the call.

The costliest frame of a tune is its heaviest refill, or a frame
within a few hundred cycles of it: the column and the period whose
fifteen units packed as the most operations. A table
that repeats puts each column's decoder back to what it was at the
loop's first row at that column's own refill, one column a row over the
period after the pass's end, so no row takes more than one decoder's
copy.

R4.5 budgets 6,656 cycles a frame. Every frame of every tune is under
it: the averages by more than two thirds, and the costliest frame by
1,862 cycles or more.

## Against YMX

YMX's performance.md measures its player by painting the background red
while a call runs and reading the palette writes back from a cycle-exact
Hatari (`ymx/test/cost.py` there). The stub here paints the same way, so
the two players read by one method, on the same dumps, over 2,000 calls:

| tune | player | on average | the 99th call in a hundred | at most |
|---|---|---|---|---|
| Synergy Credits | YMX 0.10.1 | 2317 | 3424 | 3880 |
| Synergy Credits | YMXR | 2144 | 3604 | 4648 |
| Turrican - world 4-3 | YMX 0.10.1 | 1912 | 3232 | 4252 |
| Turrican - world 4-3 | YMXR | 1777 | 2976 | 5116 |

YMXR costs a fourteenth less on average and a fifth more at its worst,
and the two figures have two causes. The frame procedure is 550 to
1,110 cycles: the fourteen register columns' tests and the writes they
admit, the effects' columns and the call's own entry and exit, where
YMX writes its fourteen registers unconditionally, one `movep` each,
and its whole call with nothing to decode is 908, the writes included.
Fourteen tests and a few writes cost what fourteen writes cost, which
YMX's own measurement found and its design took; the effects' columns
and the entry are what the schema adds. DTX's advance spends about 450
cycles a refill outside the decoder, loading and storing the decoder's
eight registers and stepping to the next, and about 430 inside it for
fifteen units on Turrican - world 4-3: 880 a row, where YMX refills one
stream of sixty-four bytes at the same unit on nineteen to twenty-five
rows in thirty-two, about 950 each, 560 to 740 a row. So YMXR's refill
costs more a row, and its frame procedure less on the rows that set few
columns, 744 on average on that tune against YMX's 908, and the average
lands a fourteenth under. The worst frame is the heaviest fifteen-unit
refill, 4,132 of the 4,794 on Turrican - world 4-3, against YMX's
heaviest sixty-four-byte group, and that is the decoder's spread and
not either player's.

## What can be done

Where an average call went before the design, Turrican - world 4-3 at
2,500, by the rig's labels:

| part | cycles |
|---|---|
| DTX's advance outside the decoder: the slot, `DTX_advance`, `DTX_step`, the budget, `DTX_state` twice, the two `movem` of eight registers, the ring's wrap with its `mulu`, the turn | 964 |
| DTX's advance inside `ST4_resume`: thirty bytes at 12 a byte copied and 175 to 220 an operation parsed | 674 |
| the fourteen register columns' tests and the writes they admit | 497 |
| the effects' columns, one effect run and three jumped | 174 |
| the call's entry and exit | 178 |

And the worst frame, row 1,919, at 7,968: `DTX_back` with its entry and
exit 5,218, the period's end 164, the advance outside the decoder 954,
the refill inside 830, and the frame procedure 802. Two other rows
follow it: row 149, where `DTX_away` copies every decoder out, at 7,584,
and row 969, the heaviest refill, at 5,748, of which 4,008 is inside the
decoder.

Four steps, in the order to take them, each counted against the player
as it stands. What the player can do alone comes first; what needs DTX
follows; a rule in SPEC.md is last and optional. The figures of steps 2
to 4 are counted from the 68000's manual and not yet run.

1. **The player alone, taken.** An effect the tune runs reads its three
   set-bit columns into one register and branches on the sign, reading
   the count alone where none is set: 66 a quiet effect against 118. A
   tone period and the envelope period branch on the fine byte's own zero
   before the bit beside it. The frame procedure stands inline in the
   call. Counted at 147 a frame, measured at 118: 2,500 to 2,382 on
   average, 7,968 to 7,824 at worst. No packed byte, no rule, 270 bytes
   of code.
2. **A proposal to DTX, taken.** Of the 954 a refilling row spent
   outside the decoder, a walker over the decoders' states removes the
   two `bsr` and `rts` pairs, the recomputed ring end, the
   five-instruction budget, the `mulu` and the turn's compare, keeping
   the two `movem`, the call and one compare: counted at 438 a row, 516
   saved; measured, the advance fell by 508. And the copies `DTX_away`
   and `DTX_back` made of all thirty decoders in one call each are part
   of each column's own refill, since what the copy loads is what the
   refill loads next anyway. The worst frame is the heaviest refill:
   5,110 on Turrican against the 5,100 counted, under R4.5 by 1,546;
   6,600 on Synergy Credits against 6,540, under by 56. The cost was
   DTX's: a 48-byte decoder state in its abi, its three packagers'
   state formula, 184 bytes of code, a release.
3. **Unit 2 in the converter, taken as the default.** Measured after
   step 2: on Turrican the advance 1,130 to 880 on average, the play
   call 1,874 to 1,624 on average and 5,110 to 4,794 at most; on
   Synergy Credits 2,320 to 2,048 and 6,600 to 4,746. It costs 18% of
   the corpus's packed bytes, 0.69 to 0.81 a frame, and no padding
   frame on a table that repeats, since the period of thirty rows
   divides by 2. The converter packs at unit 2 unless `-k1` asks for the
   bytes.
4. **A rule, optional.** A row that sets any of an effect's columns sets
   its count column too, so a count of 0 says the effect sets nothing: a
   quiet effect then costs 22. About 40 a frame on a tune running two
   effects, nothing on the 458 corpus tunes running none, for 488 packed
   bytes on the corpus, one data register write on a stop row, and a
   version. Not now.

Not taken: dense register columns written unconditionally, as YMX has
them, with the effects behind one bit. Against the player as it stands
that saves 70 to 80 a frame on a tune running one effect, nothing on
Synergy Credits, and costs about 70 a frame on the tunes running none,
for rewriting 1.1 to 1.7 and redefining five bits against R6.2. YMX's
finding that a test costs what a write costs held for a 30-cycle test
and does not for the 22 of a test that forms the select only where it
writes.

What each step leaves, Turrican - world 4-3, in the rig's count, with
the Hatari figure as the count plus the offset measured on the player,
about 150 on average and 320 at worst after step 3; step 4 is counted
from step 3 as measured:

| after | average | worst | Hatari, average | Hatari, worst |
|---|---|---|---|---|
| the two savings before the design, measured | 2500 | 7968 | 2712 | 8200 |
| step 1, measured | 2382 | 7824 | 2583 | 8052 |
| step 2, measured | 1874 | 5110 | 2037 | 5424 |
| step 3, measured | 1624 | 4794 | 1777 | 5116 |
| step 4 | 1584 | 4754 | 1737 | 5076 |
| YMX 0.10.1, measured | | | 1912 | 4252 |
| R4.5 | | 6656 | | |

The average passed YMX's at step 3, by a fourteenth. The worst frame
came under R4.5 at step 2 and stays about 860 over YMX's: a fifteen-unit
refill's heaviest case against YMX's sixty-four-byte group, which is the
decoder's and not either player's.

## A tick

| tick | cycles |
|---|---|
| a row written, the place stepped | 108 |
| the marker, the place to row `RR` | 130 |
| the marker, the timer stopped | 116 |

With the interrupt's entry and its `rte`, a tick is 172 cycles: at a
digidrum's 6,000 a second, 13% of an 8 MHz 68000, and at the 25,600 a
second terminology.md gives as the practical ceiling, 55%.

The rig reads every figure here back with `-cycles`, and `-hatari` plays
the same tunes on a cycle-exact machine, where the MFP fires the ticks.
