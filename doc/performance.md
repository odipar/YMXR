# performance

What a play call costs, in cycles, measured on the ten tunes under
`ym/test` by the rig's cycle counter (tools.md 17): 68000 cycles,
with no wait state, over every frame of a tune played through its wrap
once. A tick handler's cost is its instructions, from its vector to its
`rte`; the 68000 spends 44 cycles more entering an interrupt and 20 on the
`rte`.

## The play call

The call, and the share of it spent in DTX's advance: one column
refilled a row out of its ST4 data set, a period's bytes of it at once.

| tune | frames | on average | at most | the advance on average | in the costliest frame |
|---|---|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | 432 | 1292 | 1924 | 782 | 1402 |
| Chambers of Shaolin 5 - you blew it! | 1000 | 1367 | 3258 | 803 | 2676 |
| Circus Attractions 2 | 8 | 1446 | 1648 | 746 | 746 |
| DBA 2 | 19444 | 1674 | 4272 | 877 | 2972 |
| DBA 5 | 22264 | 1754 | 3606 | 938 | 2410 |
| Digidrum preempt, built | 800 | 1626 | 2750 | 775 | 1948 |
| Retrigger retune, built | 1200 | 1490 | 2220 | 779 | 1042 |
| Synergy Credits | 10756 | 2030 | 5094 | 938 | 2932 |
| Turrican - world 4-3 | 3680 | 1605 | 4532 | 884 | 3826 |
| Turrican 2 - world completed 1 | 182 | 1605 | 4060 | 932 | 3436 |

Five of the ten packed at unit 1 before SPEC.md 6, rule 6, since an odd
row count or repeat row does not divide by 2, and a refill of theirs was
thirty units of a byte. The call on each, at unit 1 and padded to unit 2:

| tune | on average at unit 1 | at unit 2 | at most at unit 1 | at unit 2 |
|---|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | 1533 | 1292 | 2104 | 1924 |
| DBA 2 | 1923 | 1674 | 5258 | 4272 |
| DBA 5 | 2009 | 1754 | 4986 | 3606 |
| Synergy Credits | 2294 | 2030 | 5778 | 5094 |
| Turrican 2 - world completed 1 | 1896 | 1605 | 5382 | 4060 |

A table packs at unit 2 and a period of thirty rows, the column count, so
a refill is fifteen units of two bytes and one comes every row (tools.md
4). What those units cost follows how the column packed: a run of long
matches costs 12 cycles a unit to copy, and a short operation, a length
and an offset read bit by bit, about 200 to 270 an operation to parse.
The advance spends 450 cycles a refill outside the decoder, loading and
storing the decoder's eight registers, moving its mark and stepping to
the next; 412 where a column fits the ring, since the mark the advance
moves is a replayed loop and ST4 replays a loop only past the reach of a
back reference (DTX, abi.md 4). Six of the ten tunes fit. A refill that
parses no new operation adds 334 inside the decoder on every tune, and
576 at unit 1.

The 200 to 270 is a least-squares fit of the decoder's cycles against the
operations parsed, over every refill of a tune: 199 on Turrican 2 - world
completed 1 and 271 on Big - Samantha Fox Strip Poker 6. The endpoints of
a tune whose heaviest refill parses ten operations or more read lower,
179 to 217: the fit counts every refill, and a refill that parses few
operations is mostly the fixed part. On Turrican - world 4-3 the 334
above and 3,376 at its heaviest are 3,042 over fourteen operations, about
217 each. A refill of 450 outside and the heaviest 3,376 inside is the
3,826 the table above reads as Turrican's advance in its costliest frame.

Unit 1 packs the corpus to 0.69 bytes a frame against 0.81
(experiments.md) and costs more to decode: on Turrican - world 4-3 the
advance reads 1,132 on average and 4,298 at most against 884 and 3,826,
and the play call 1,852 and 4,952 against 1,605 and 4,532. `-k1` packs at
it.

The frame procedure is the rest, from 510 to 1,092 cycles on average:
the fourteen register columns' tests and the writes they admit, the
effects' columns and the call's entry and exit. An effect the tune
does not run is jumped over, two nops standing at its columns' head
where the tune runs it; an effect it runs tests its three set-bit
columns in one pass and reads its count alone where none is set; a tone
period and the envelope period branch on a zero byte before the bit
beside it; a column's select is formed only where the column is set;
and the frame stands inline in the call.

The costliest frame of a tune is its heaviest refill, or a frame
within a few hundred cycles of it: the column and the period whose fifteen
units packed as the most operations. A table that repeats puts each
column's decoder back to what it was at the loop's first row at that
column's next refill, one column a row over the period after the pass's
end, so no row pays for more than one decoder's copy.

R4.5 budgets 6,656 cycles a frame. Every frame of every tune is within it:
the averages by more than two thirds of it, and the costliest frame of
every tune by 1,562 cycles or more, Synergy Credits' 5,094 the nearest.
The ticks of a frame stand beside the call and are counted in A tick below.

## The raster monitor

The figures above are the rig's, counted instruction by instruction
under an emulated 68000. A second measure runs on a cycle-exact machine,
and the player is built with it: `rmac -dYMXR_PERF=1` puts the raster
monitor in (`68k/YMXR.S`), and `bin/ymxr-sndh -perf` puts that core in
an SNDH file. The call paints the background red while its work runs,
one scanline to 512 cycles, and each tick handler paints a separate colour
where the beam stands: effect 0 green, effect 1 blue, effect 2 magenta,
effect 3 cyan, four colours distinct from the call's red and the bar's
yellow, so a reader separates every band. A tick's band is a few
hundred cycles wide, so each handler also adds a count of what its
instructions cost, in turns of ten cycles, and the next call burns the
total off as a yellow bar after its work, the register writes among them:
no chip write moves for the bar, and the bar shows what the handlers cost
the frame. The call waits for the display to start before it paints, since
the VBL fires far above the screen and an unsynced bar lands in the top
border, out of sight; the wait stands before the red mark, so it is outside
the figures, and it is bounded, so a call from anywhere runs on. With the
switch off, the default, the player is byte for byte the player without
it.

`ym/cost.sh` builds a program with that core, runs it under Hatari tracing
the writes to the background, and `ym/cost.py` reads every call's span
back: the red mark to the yellow one is the call's work, less each tick
band inside it, and the yellow to the write that puts the desktop's colour
back is the bar. What the method leaves out: the ticks' counts are
estimates, each within a twentieth of what A tick measures below, which
PlayerTest checks; what a tick costs beyond its handler, the 44 cycles of
the interrupt's entry and the 20 of its `rte`, falls where the tick landed,
inside the call's work where it landed there and left out where it did not;
a tick that lands inside the bar is counted in the next call's; and the
wait moves the call's writes in time, which is why the monitor is a build
for reading a run rather than one to play a tune with.

A call writes the row the call before it read, and reads the next once its
writes are made (68k/YMXR.S), so a row's refill stands behind that row's
writes rather than in front of them. Measured on DBA 2 over 3,000
frames, the first register write of a frame stands 1,125 cycles after
the VBL and the energy at the frame rate is 6.38 per cent of the whole,
against YMX's 6.56. YMX writes its fourteen registers before it decodes
for the same reason.

Measure from the VBL, at the tune's rate. Where the program plays from Timer
C, a call runs inside the tick handler the timer interrupted, since a handler
drops the level once its write is made (SPEC.md 5), and the wait then keeps
that handler open for as long as it runs: that timer's next ticks wait with
it, and the tune's sound changes. At the screen's rate the program plays from
the VBL and no call nests inside a tick.

## Against YMX

YMX 0.10.1 measured its player the same way, painting the background red
while a call runs and reading the palette writes back from a cycle-exact
Hatari. So the two players were read by one method, on the same dumps,
each over a `VBLS=2300` run: 2,019 calls of YMX and 2,020 of YMXR. YMX is
a design document now, so its row is that release's, in that repository's
history, and YMXR's is this release's, `ym/cost.sh` over the two tunes.

| tune | player | on average | the 99th call in a hundred | at most |
|---|---|---|---|---|
| Synergy Credits | YMX 0.10.1 | 2330 | 3680 | 4716 |
| Synergy Credits | YMXR | 1990 | 3464 | 4296 |
| Turrican - world 4-3 | YMX 0.10.1 | 1897 | 3360 | 4284 |
| Turrican - world 4-3 | YMXR | 1651 | 2936 | 4692 |

These figures and the rig's are not one sample: the rig counts every frame
of the tune in 68000 cycles with no wait state, and these are the first
2,020 calls on a machine that stalls the processor while the shifter
fetches. Synergy Credits' costliest frame is its 5,346th, past the end of
this run.

YMXR costs an eighth less on average on Turrican - world 4-3 and a
seventh less on Synergy Credits, and at their worst 10 per cent more on
Turrican - world 4-3 and 9 per cent less on Synergy Credits, and the
figures have two causes. The frame procedure here tests the fourteen
register columns and writes the ones they admit, reads the effects'
columns and enters and leaves the call, where YMX writes its fourteen
registers unconditionally, one `movep` each, and its whole call with no
decode in it is 908, the writes included. Fourteen tests and a few writes
cost what fourteen writes cost, which YMX's measurement found and its
design follows; the schema adds the effects' columns and the entry. So
YMXR's frame procedure costs less on the rows that set few columns, 721
on average on that tune, the rig's call there less its advance, against
YMX's 908, and the average lands under.

The refill is the second cause. YMXR refills fifteen units every row; YMX
serves a round-robin of twenty-four slots, twenty-one of them a live
stream, so its group is twenty-four bytes, twelve units at the same unit
2, and it refills on twenty-one rows in twenty-four: the three slots
without a stream refill no column and cost 1,012 cycles a call over 252
of the 2,019 calls, against 2,023 on the 1,767 calls that refill. YMX's
refill is the smaller of the two, twelve units against fifteen, which is
why its worst call is the lower. A refill parses at most one operation a
unit, so its unit count bounds the parse, and that count follows the
streams the schedule serves: thirty columns here against twenty-one live
streams there. The worst frame of each player parses one operation for
every unit, fifteen of fifteen here and twelve of twelve there, so each
worst frame is that bound. On Synergy Credits the run ends before YMXR's
costliest frame, so its 4,296 at most is not that frame, which the rig
counts at 5,094. Both players pad this tune to unit 2, YMX by duplicating
a frame and YMXR by a row that sets no column (SPEC.md 6, rule 6); before
that rule the tune packed at unit 1, the refill was thirty units, and a
run of the same length reads 5,168 at most.

The one thing YMX does here that this player does not is write its register
columns unconditionally, dense, with the effects behind one bit. Measured
against the player as it stands that saves 70 to 80 a frame on a tune
running one effect, zero on Synergy Credits, and costs about 70 a frame on
a tune that runs no effect, for rewriting 1.1 to 1.7 and redefining five
bits against R6.2. YMX's finding that a test costs what a write costs
applies to a 30-cycle test and not to the 22 of a test that forms the
select only where it writes.

---

## A tick

| tick | cycles |
|---|---|
| a row written, the place stepped | 108 |
| the marker, the place to row `RR` | 130 |
| the marker, the timer stopped | 132 |
| a square's two rows, no place stepped | 88 |
| a source of one row, no place stepped | 64 |
| a row written, the tune running one effect | 100 |
| the marker to row `RR`, one effect | 130 |
| the marker and the stop, one effect | 132 |
| a square's two rows, one effect | 80 |
| a source of one row, one effect | 56 |

With the interrupt's entry and its `rte`, a tick is 172 cycles: 13% of an
8 MHz 68000 at a digidrum's 6,000 ticks a second, and 55% at 25,600.

A source of two rows repeating to row 0 runs a separate handler
(68k/YMXR.S, SQUARE), which encodes both rows as immediates and moves
between them by their difference: 88 cycles, 152 with the entry and the
`rte`, against the 172 and 194 the two paths of the general handler cost. A
tune whose effects are all such sources ticks 29.4 times a frame on Synergy
Credits, 24.8 on DBA 2 and 20.2 on DBA 5, so 911, 769 and 626 cycles a
frame come off those tunes, against a play call of 2,030, 1,674 and 1,754
(the table above).

A tick drops the interrupt level and writes an end of interrupt because the
MFP runs in software end-of-interrupt mode, as TOS leaves it, and because a
faster timer may nest inside a slower one. A host that requires neither
uses the core assembled with `YMXR_NEST=0` and `YMXR_AEOI=1` (BINARIES.md
2.1), where a tick writes its two chip registers and returns:

| tick | as it stands | lean |
|---|---|---|
| a row written, the place stepped | 108 | 76 |
| the marker, the place to row `RR` | 130 | 114 |
| the marker, the timer stopped | 132 | 116 |
| a square's two rows, no place stepped | 88 | 56 |
| a source of one row, no place stepped | 64 | 32 |

The level is dropped on the three paths that write a row's value and the
end of interrupt is written on all five, so those three lose 32 cycles and
the two that end a source lose 16, and 24 where the tune runs one effect
and the nops below already stand. A source ends once a pass and its rows
are written many times, so 32 a tick bounds what comes off a frame: 941
cycles on Synergy Credits, 595 on DBA 2, 485 on DBA 5 and 295 on Turrican -
world 4-3. The two switches go together, since automatic end of interrupt
leaves the in-service bit clear and the level a tick sets is then all that
keeps a lower timer out, and the player claims the MFP's vector register at
init and restores it at stop.

The four cores write one tune the same but for a square's edge. Traced under
Hatari over 900 frames of Synergy Credits, counted from the frame the player
first writes in, every register a row writes receives the same values in the
same order from all four, to the row the window ends on. The two a timer
drives part at a few of their writes: the plain and lean cores share 2,036 of
R9's values, of the 2,037 and 2,039 they write, and the lean core and the one
with both switches 6,621 of R10's, of 6,626 and 6,630. A parting is one toggle
landing the other side of a frame's edge, a tick of another length returning
inside the call at another point, which lengthens one half of the wave and
shortens the next. `ym/writes.py` reads two traces back this way.

A tune that runs one effect has no second timer to nest inside a tick, so
init writes two nops where that effect's handlers drop the interrupt level:
8 cycles a tick, the drop costing 16 and the nops 8. Only a path that
writes a row's value drops it and the marker's two never did, so the two
marker rows of the second five stand at what the first five's do. A tune
running two or more effects keeps the drop, since a faster timer waits
behind a slower one without it.

A tune whose sources are of other shapes pays 2 to 4 cycles a frame for the
vector each start now writes, and a tune of squares 3 to 22 for the two
values and the difference each start patches, against the 626 to 911 its
ticks no longer cost.

A source of one row repeating runs a separate handler the same way
(68k/YMXR.S, ONEROW). A source of one row is the marker alone (SPEC.md 3.2)
and its place stands at row 0, so the handler encodes that row as an
immediate, writes it, and moves no place: 56 cycles against the 130 the
general handler's marker path cost, and 64 against 130 where the tune runs
more than one effect. The kit's `retune` ticks 7.7 times a frame, a 383 Hz
buzzer, so 568 cycles a frame come off it against a play call of 1,493.
`retune` is a built tune: every source the corpus names has another shape,
83 tunes of it naming any source (experiments.md), so what the handler
saves is measured on this tune and the conformance kit alone. The target
belongs to the effect on all three handlers, patched at a start out of the
effect's record, so a source's shape selects the handler and the register
it drives does not.

A start separates three shapes off one cell, which would cost every start
16 cycles. Init already walks every source to resolve it, so it reads there
which of the two shapes the tune's sources have and settles the branch that
leaves the general handler's path: a tune using one of them and not the
other jumps straight to that shape's block. The two tests left are the ones
that separate a square's kept place from a one-row source's, 3 cycles a
frame on DBA 2 and DBA 5 and 5 on Synergy Credits, and 8 on the costliest
frame of DBA 5 and Synergy Credits. The handler, its start and init's
reading of the shapes are 400 bytes, the SNDH core going to 4,600 from
4,200.

Init resolves every source the tune names into its first row and its loop
cell, eight bytes each in the workspace, so a start reads two longs where
it walked the tune's index and read the source's header: 48 cycles against
100. That is 38 a frame on Synergy Credits, 22 on DBA 2 and 21 on DBA 5, and
60 to 94 off their costliest frames. The source column's seven bits reach
127, so the room is sized for 127 sources, and YMXR_FIXED is 1,072 bytes
against 56.

The rig reads every figure here back with `-cycles`, and `-hatari` plays
the same tunes on a cycle-exact machine, where the MFP fires the ticks.
