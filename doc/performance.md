# performance

What a play call costs, in cycles, measured on the seven tunes under
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
| Chambers of Shaolin 5 - you blew it! | 1020 | 2405 | 7854 | 1717 | 7166 |
| Circus Attractions 2 | 180 | 2563 | 7848 | 1898 | 7190 |
| Digidrum preempt, built | 840 | 2683 | 8138 | 1656 | 7166 |
| Retrigger retune, built | 1200 | 2464 | 8000 | 1623 | 7168 |
| Synergy Credits | 10800 | 2932 | 8256 | 1718 | 6972 |
| Turrican - world 4-3 | 3720 | 2500 | 7968 | 1638 | 7166 |
| Turrican 2 - world completed 1 | 360 | 2654 | 7932 | 1860 | 7166 |

A table packs at a period of thirty rows, the column count, so a refill
is thirty bytes and one comes every row (tools.md, Convert). What those
bytes cost depends on how the column packed: a run of long matches
decodes at 17 cycles a byte and a run of short operations, each a length
and an offset read bit by bit, at 70 or more, which is the spread between
the tunes' averages. A larger period would make the refill larger and
rarer: packed at 283 rows, the only period dividing its 5,377-row loop
before the converter padded it, Synergy Credits refilled 283 bytes at a
time, from 4,792 to 19,742 cycles, and its costliest frame was 20,936.

The frame procedure is the rest, from 660 to 1,210 cycles on average.
On Turrican - world 4-3 that is 497 for the fourteen register columns'
tests and the writes they admit, 174 for the effects' columns and 178
for the call's own entry and exit; on Synergy Credits, whose two effects
start and bend often, the effects' columns are 529. An effect the tune
does not run is jumped over, two nops standing at its columns' head where
the tune runs it, and a column's select is loaded only where the column
is set.

The costliest frame of every tune is the same row: the one before a
pass's last period, where a table that repeats puts every column's
decoder back to what it was at the loop's first row, thirty decoders at
170 cycles each, 5,104 in one call, on top of that row's refill.

R4.5 budgets 6,656 cycles a frame. The average is under it by more than
half; that one frame is over it on every tune, by 1,192 to 1,600 cycles,
and what it costs is DTX's.

## Against YMX

YMX's performance.md measures its player by painting the background red
while a call runs and reading the palette writes back from a cycle-exact
Hatari (`ymx/test/cost.py` there). The stub here paints the same way, so
the two players read by one method, on the same dumps, over 2,000 calls:

| tune | player | on average | the 99th call in a hundred | at most |
|---|---|---|---|---|
| Synergy Credits | YMX 0.10.1 | 2317 | 3424 | 3880 |
| Synergy Credits | YMXR | 3123 | 4852 | 6844 |
| Turrican - world 4-3 | YMX 0.10.1 | 1912 | 3232 | 4252 |
| Turrican - world 4-3 | YMXR | 2712 | 4128 | 8200 |

YMXR costs a third to two fifths more on average and near twice as much
at its worst, and the difference has three parts. The frame procedure is
660 to 1,210 cycles: the fourteen register columns' tests and the writes
they admit, the effects' columns and the call's own entry and exit,
where YMX writes its fourteen registers unconditionally, one `movep`
each, and its whole call with nothing to decode is 908, the writes
included. Fourteen tests and a few writes cost what fourteen writes cost,
which YMX's own measurement found and its design took; the effects'
columns and the entry are what the schema adds. DTX's advance spends
954 cycles a refill outside the decoder, loading and storing the
decoder's eight registers and keeping its ring, its turn and its
budget, and 700 to 950 inside it for thirty bytes, where a byte costs 12
cycles to copy and each operation the stream holds 175 to 220 to parse;
YMX's refill decodes sixty-four bytes at unit 2 for about 950 in all.
Packed at unit 2 the advance falls by a sixth, no more. And YMX reopens
its streams' sections one a turn across the frames before its loop,
where DTX puts all thirty decoders back in one call, 5,104 cycles.

## What can be done

Where an average call goes, Turrican - world 4-3 at 2,500, by the rig's
labels:

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

1. **The player alone.** An effect the tune runs reads its three set-bit
   columns into one register and branches on the sign, reading the count
   alone where none is set: 66 a quiet effect against 118. A tone period
   and the envelope period branch on the fine byte's own zero before the
   bit beside it. The frame procedure stands inline behind one entry branch
   init aims at the highest effect the tune runs. Together 147 a frame:
   2,500 to 2,353 on average, 7,968 to 7,800 at worst. No packed byte, no
   rule, about 200 bytes of code.
2. **A proposal to DTX.** Of the 954 a refilling row spends outside the
   decoder, a walker over the decoders' states removes the two `bsr` and
   `rts` pairs, the recomputed ring end, the five-instruction budget, the
   `mulu` and the turn's compare, keeping the two `movem`, the call and
   one compare: 438 a row, 516 saved. And the copies `DTX_away` and
   `DTX_back` make of all thirty decoders in one call each become part of
   each column's own refill, 122 or 134 more on thirty rows a pass, since
   what the copy loads is what the refill loads next anyway. The worst
   frame becomes the heaviest refill: about 5,070 on Turrican, under
   R4.5 by about 1,590; about 6,540 on Synergy Credits, under by about
   110. The cost is DTX's: some sixty instructions, a 40-byte decoder
   state in its abi, its three packagers' state formula, a release.
3. **Unit 2 in the converter.** Measured today: the advance 1,638 to
   1,398 on Turrican, the heaviest refill inside the decoder 4,008 to
   3,692 there and 4,886 to 2,624 on Synergy Credits. It costs 18% of the
   corpus's packed bytes, 0.69 to 0.81 a frame, and one padding frame on
   148 of 543 tunes. A converter choosing unit 2 only where a tune's
   heaviest refill at unit 1 passes the budget keeps most of the corpus
   at 0.69.
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
the Hatari figure as the count plus the offset measured on the player
as it stands, 212 on average and 232 at worst:

| after | average | worst | Hatari, average | Hatari, worst |
|---|---|---|---|---|
| the player as it stands, measured | 2500 | 7968 | 2712 | 8200 |
| step 1 | 2353 | 7800 | 2565 | 8030 |
| step 2 | 1840 | 5070 | 2050 | 5300 |
| step 3 | 1590 | 4750 | 1800 | 4980 |
| step 4 | 1550 | 4700 | 1760 | 4940 |
| YMX 0.10.1, measured | | | 1912 | 4252 |
| R4.5 | | 6656 | | |

The average reaches YMX's at step 3. The worst frame comes under R4.5 at
step 2 and stays about 700 over YMX's: a thirty-byte refill's heaviest
case against YMX's sixty-four-byte group, which is the decoder's and not
either player's.

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
