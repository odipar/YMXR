# performance

What a play call costs, in cycles, measured on the ten tunes under
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
| Big - Samantha Fox Strip Poker 6 | 430 | 1660 | 2254 | 1072 | 1654 |
| Chambers of Shaolin 5 - you blew it! | 1000 | 1494 | 3520 | 852 | 2860 |
| Circus Attractions 2 | 8 | 1568 | 1764 | 792 | 792 |
| DBA 2 | 19442 | 2085 | 5644 | 1185 | 4920 |
| DBA 5 | 22262 | 2172 | 5790 | 1257 | 4554 |
| Digidrum preempt, built | 800 | 1745 | 2928 | 822 | 2052 |
| Retrigger retune, built | 1200 | 1616 | 2412 | 828 | 1098 |
| Synergy Credits | 10754 | 2468 | 6450 | 1266 | 4994 |
| Turrican - world 4-3 | 3680 | 1720 | 4948 | 933 | 4176 |
| Turrican 2 - world completed 1 | 179 | 2020 | 5756 | 1284 | 5062 |

A table packs at unit 2 and a period of thirty rows, the column count, so a
refill is fifteen units of two bytes and one comes every row (tools.md,
Convert); a tune whose row count or repeat row is odd packs at unit 1, Synergy
Credits and Turrican 2 - world completed 1 among these, so a refill of theirs
is thirty units of a byte. What those units cost depends on how the column
packed: a run of long matches costs 12 cycles a unit to copy, and a short
operation, a length and an offset read bit by bit, about 225 to 240 an
operation to parse. The advance spends 496 cycles a refill outside the
decoder, loading and storing the decoder's eight registers, testing its mark
and stepping to the next, and a refill that parses nothing new adds 334 on
Turrican - world 4-3 at unit 2 and 578 on Synergy Credits at unit 1. The range
comes from the endpoints on Turrican - world 4-3: the 334 above and 3,680 at
its heaviest are 3,346 over fourteen operations, about 239 each, and YMX's own
slope is about 225 an operation. A least-squares fit over every refill reads
higher, 285 to 342 an operation, since a refill that parses few operations is
mostly the fixed part. A refill of 496 outside and the heaviest 3,680 inside
is the 4,176 the table above gives as Turrican's advance in its costliest
frame. Unit 1 packs the corpus to 0.69 bytes a frame against 0.81
(experiments.md) and costs more to decode: measured on Turrican - world 4-3
with the same rows, the advance 1,130 on average and 4,448 at most against 880
and 4,132, and the play call 1,874 and 5,110 against 1,624 and 4,794; `-k1`
packs at it. A larger period would make the refill larger and rarer: packed at
unit 1 and 283 rows, the only period dividing its 5,377-row loop while DTX
asked that the loop divide by the period, Synergy Credits refilled 283 bytes
at a time, from 4,792 to 19,742 cycles, and its costliest frame was 20,936.

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

R4.5 budgets 6,656 cycles a frame. Every frame of every tune is within it: the
averages by more than three fifths of it, and the costliest frame of every
tune but two by 900 cycles or more. DBA 5's costliest frame is 866 cycles
under the budget and Synergy Credits', at unit 1, 206.

## The raster monitor

The figures above are the rig's, counted instruction by instruction
under an emulated 68000. A second measure runs on a cycle-exact machine,
and the player is built with it: `rmac -dYMXR_PERF=1` puts the raster
monitor in (`68k/YMXR.S`), and `bin/ymxr-sndh -perf` puts that core in
an SNDH file. The call paints the background red while its work runs,
one scanline to 512 cycles, and each tick handler paints its own colour
where the beam stands: effect 0 green, effect 1 blue, effect 2 magenta,
effect 3 cyan, four colours the call's red and the bar's yellow are not,
so a reader tells every band apart. A tick's band is a few hundred
cycles wide, so each handler also adds a count of what its own
instructions cost, in turns of ten cycles, and the next call burns the
total off as a yellow bar after its own work, the register writes among
them: no chip write moves for the bar, and the bar shows what the
handlers cost the frame. The call waits for the display to start before
it paints, since the VBL fires far above the screen and an unsynced bar
lands in the top border where nothing shows; the wait stands before the
red mark, so it costs the figures nothing, and it is bounded, so a call
from anywhere runs on. With the switch off, the default, the player is
byte for byte the player without it.

`ym/cost.sh` builds a program with that core, runs it under Hatari
tracing the writes to the background, and `ym/cost.py` reads every
call's span back: the red mark to the yellow one is the call's own work,
less each tick band inside it, and the yellow to the write that puts the
desktop's colour back is the bar. What the method leaves out: the ticks'
counts are estimates, each within a twentieth of what A tick measures
below, which PlayerTest holds them to; what a tick costs beyond its
handler, the 44 cycles of the interrupt's entry and the 20 of its `rte`,
stands where the tick landed, inside the call's own work where it landed
there and counted nowhere where it did not; a tick that lands inside the
bar is counted in the next call's; and the wait moves the call's writes
in time, which is why the monitor is a build for reading a run and not
one to play a tune with.

A call writes the row the call before it took, and takes the next once
its writes are made (68k/YMXR.S). What that buys is where the writes
land: measured on DBA 2 over 3,000 frames, the first register write of
a frame stood 2,363 cycles after the VBL before the row was taken ahead
and stands 1,125 after it, and the energy at the frame rate fell from
8.48 per cent of the whole to 6.38, against YMX's 6.56. YMX writes its
fourteen registers before it decodes for the same reason.

Measure from the VBL, at the tune's own rate. Where the program plays
from Timer C, a call runs inside the tick handler the timer
interrupted, since a handler drops the level once its write is made
(SPEC.md 5), and the wait then holds that handler open for as long as it
runs: the timer's own next ticks wait with it, and the tune's sound
changes. At the screen's rate the program plays from the VBL and no call
nests inside a tick.

## Against YMX

YMX's performance.md measures its player the same way, painting the
background red while a call runs and reading the palette writes back
from a cycle-exact Hatari (`ymx/test/cost.py` there). So the two players
read by one method, on the same dumps, over the 2,019 calls of a
`VBLS=2300` run:

| tune | player | on average | the 99th call in a hundred | at most |
|---|---|---|---|---|
| Synergy Credits | YMX 0.10.1 | 2330 | 3680 | 4716 |
| Synergy Credits | YMXR | 2421 | 4220 | 5408 |
| Turrican - world 4-3 | YMX 0.10.1 | 1897 | 3360 | 4284 |
| Turrican - world 4-3 | YMXR | 1749 | 3032 | 5148 |

These figures and the rig's are not one sample: the rig counts every frame
of the tune and the 68000's own cycles with no wait state, and these are the
first 2,019 calls on a machine that stalls the processor while the shifter
fetches. Synergy Credits' costliest frame is its 4,517th, past the end of
this run.

YMXR costs a thirteenth less on average on Turrican - world 4-3 and a
twenty-fifth more on Synergy Credits, whose odd row count puts it at unit 1,
and a fifth
and a seventh more at their worst, and the figures have two causes.
The frame procedure is 550 to 1,110 cycles: the fourteen register columns'
tests and the writes they admit, the effects' columns and the call's own entry
and exit, where YMX writes its fourteen registers unconditionally, one `movep`
each, and its whole call with nothing to decode is 908, the writes included.
Fourteen tests and a few writes cost what fourteen writes cost, which YMX's
own measurement found and its design took; the effects' columns and the entry
are what the schema adds. So YMXR's frame procedure costs less on the rows
that set few columns, 746 on average on that tune against YMX's 908, and the
average lands a tenth under.

The refill is the second cause. YMXR refills fifteen units every row; YMX
serves a round-robin of twenty-four slots, twenty-one of them holding a live
stream, so its group is twenty-four bytes, twelve units at the same unit 2,
and it refills on twenty-one rows in twenty-four: the three slots without a
stream refill nothing and cost 1,012 cycles a call over 252 of the 2,019
calls, against 2,023 on the 1,767 calls that refill. YMX's refill is the
smaller of the two, twelve units against fifteen, which is why its worst call
is the lower. A refill parses at most one operation a unit, so its unit count
bounds the parse, and that count follows the streams the schedule serves:
thirty columns here against twenty-one live streams there. The worst frame of
each player parses one operation for every unit, fifteen of fifteen here and
twelve of twelve there, so each worst frame is that bound. On Synergy Credits
the gap is wider: unit 1 makes the refill thirty units, where YMX duplicates a
frame to raise its own row count and stays at unit 2.

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
3. **Unit 2 in the converter, taken as the default.** Measured after step 2:
   on Turrican the advance 1,130 to 880 on average, the play call 1,874 to
   1,624 on average and 5,110 to 4,794 at most; on Synergy Credits 2,320 to
   2,048 and 6,600 to 4,746. It costs 18% of the corpus's packed bytes, 0.69
   to 0.81 a frame. The converter packs at unit 2 unless `-k1` asks for the
   bytes, or the row count or the repeat row is odd, which a unit of two bytes
   cannot land on: Synergy Credits and Turrican 2 - world completed 1 pack at
   unit 1.
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

What each step leaves, Turrican - world 4-3, in the rig's count, with the
Hatari figure as the count plus the offset measured on the player, about 26
on average and 190 at worst; step 4 is counted from the tune's own rows as
measured:

| after | average | worst | Hatari, average | Hatari, worst |
|---|---|---|---|---|
| the two savings before the design, measured | 2500 | 7968 | 2526 | 8158 |
| step 1, measured | 2382 | 7824 | 2408 | 8014 |
| step 2, measured | 1874 | 5110 | 1900 | 5300 |
| step 3, measured | 1624 | 4794 | 1650 | 4984 |
| the tune's own rows, no row added, measured | 1718 | 4948 | 1748 | 5148 |
| step 4 | 1639 | 4794 | 1665 | 4984 |
| YMX 0.10.1, measured | | | 1897 | 4284 |
| R4.5 | | 6656 | | |

The average passed YMX's at step 3. The rows after it are the dump's own, the
padding that kept every loop on a period gone with DTX's rule that asked for
it, and the repeat replayed at its exact row costs the advance a mark's test a
row. The worst frame came under R4.5 at step 2 and stays about 740 over YMX's:
a fifteen-unit refill's heaviest case against YMX's twelve-unit group, and the
two sizes follow the streams each schedule serves.

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
