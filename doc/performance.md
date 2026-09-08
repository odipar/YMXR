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
| Big - Samantha Fox Strip Poker 6 | 430 | 1536 | 2130 | 1026 | 1608 |
| Chambers of Shaolin 5 - you blew it! | 1000 | 1370 | 3396 | 806 | 2814 |
| Circus Attractions 2 | 8 | 1446 | 1648 | 746 | 746 |
| DBA 2 | 19442 | 1966 | 5522 | 1139 | 4874 |
| DBA 5 | 22262 | 2054 | 5686 | 1211 | 4508 |
| Digidrum preempt, built | 800 | 1627 | 2808 | 776 | 2006 |
| Retrigger retune, built | 1200 | 1494 | 2270 | 782 | 1052 |
| Synergy Credits | 10754 | 2356 | 6348 | 1220 | 4948 |
| Turrican - world 4-3 | 3680 | 1608 | 4836 | 887 | 4130 |
| Turrican 2 - world completed 1 | 179 | 1908 | 5644 | 1238 | 5016 |

A table packs at unit 2 and a period of thirty rows, the column count, so a
refill is fifteen units of two bytes and one comes every row (tools.md,
Convert); a tune whose row count or repeat row is odd packs at unit 1, Synergy
Credits and Turrican 2 - world completed 1 among these, so a refill of theirs
is thirty units of a byte. What those units cost depends on how the column
packed: a run of long matches costs 12 cycles a unit to copy, and a short
operation, a length and an offset read bit by bit, about 225 to 240 an
operation to parse. The advance spends 486 cycles a refill outside the
decoder, loading and storing the decoder's eight registers, testing its mark
and stepping to the next, and a refill that parses nothing new adds 334 on
Turrican - world 4-3 at unit 2 and 578 on Synergy Credits at unit 1. The range
comes from the endpoints on Turrican - world 4-3: the 334 above and 3,680 at
its heaviest are 3,346 over fourteen operations, about 239 each, and YMX's own
slope is about 225 an operation. A least-squares fit over every refill reads
higher, 285 to 342 an operation, since a refill that parses few operations is
mostly the fixed part. A refill of 486 outside and the heaviest 3,680 inside
is the 4,166 the table above gives as Turrican's advance in its costliest
frame. Unit 1 packs the corpus to 0.69 bytes a frame against 0.81
(experiments.md) and costs more to decode: measured on Turrican - world 4-3,
the advance 1,138 on average and 4,586 at most against 887 and 4,130, and the
play call 1,859 and 5,240 against 1,608 and 4,836; `-k1` packs at it.

The frame procedure is the rest, from 486 to 1,112 cycles on average:
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
tune but one by 900 cycles or more. Synergy Credits' costliest frame, at
unit 1, is 296 cycles under the budget. The ticks a frame takes stand
beside the call and are counted in A tick below.

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
its writes are made (68k/YMXR.S), so a row's refill stands behind that
row's writes and not in front of them. Measured on DBA 2 over 3,000
frames, the first register write of a frame stands 1,125 cycles after
the VBL and the energy at the frame rate is 6.38 per cent of the whole,
against YMX's 6.56. YMX writes its fourteen registers before it decodes
for the same reason.

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
and a fifth and a seventh more at their worst, and the figures have two
causes. The frame procedure is 588 to 1,203 cycles: the fourteen register
columns' tests and the writes they admit, the effects' columns and the call's
own entry and exit, where YMX writes its fourteen registers unconditionally,
one `movep` each, and its whole call with nothing to decode is 908, the writes
included. Fourteen tests and a few writes cost what fourteen writes cost,
which YMX's own measurement found and its design took; the effects' columns
and the entry are what the schema adds. So YMXR's frame procedure costs less
on the rows that set few columns, 787 on average on that tune against YMX's
908, and the average lands a thirteenth under.

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

The one thing YMX does here that this player does not is write its
register columns unconditionally, dense, with the effects behind one
bit. Measured against the player as it stands that saves 70 to 80 a
frame on a tune running one effect, nothing on Synergy Credits, and
costs about 70 a frame on the tunes running none, for rewriting 1.1 to
1.7 and redefining five bits against R6.2. YMX's finding that a test
costs what a write costs holds for a 30-cycle test and not for the 22 of
a test that forms the select only where it writes.

---

## A tick

| tick | cycles |
|---|---|
| a row written, the place stepped | 108 |
| the marker, the place to row `RR` | 130 |
| the marker, the timer stopped | 116 |
| a square's two rows, no place stepped | 88 |
| a row written, the tune running one effect | 100 |
| the marker to row `RR`, one effect | 130 |
| the marker and the stop, one effect | 116 |
| a square's two rows, one effect | 80 |

With the interrupt's entry and its `rte`, a tick is 172 cycles: at a
digidrum's 6,000 a second, 13% of an 8 MHz 68000, and at the 25,600 a
second terminology.md gives as the practical ceiling, 55%.

A source of two rows repeating to row 0 takes a handler of its own
(68k/YMXR.S, SQUARE), which holds both rows as its own immediate and
moves between them by their difference: 88 cycles, 152 with the entry
and the `rte`, against the 172 and 194 the two paths of the general
handler cost. A tune whose effects are all such sources ticks 29.4 times
a frame on Synergy Credits, 24.8 on DBA 2 and 20.2 on DBA 5, so 911, 769
and 626 cycles a frame come off those tunes, against a play call of
2,380, 1,985 and 2,073. A tick drops the interrupt level and writes its
own end of interrupt because the MFP is taken in software
end-of-interrupt mode, as TOS leaves it, and because a faster timer may
want to nest inside a slower one. A host that wants neither takes the
core assembled with `YMXR_NEST=0` and `YMXR_AEOI=1` (BINARIES.md,
`YMXR_sndh-lean.bin`), where a tick writes its two chip registers and
returns:

| tick | as it stands | lean |
|---|---|---|
| a row written, the place stepped | 108 | 76 |
| the marker, the place to row `RR` | 130 | 114 |
| the marker, the timer stopped | 116 | 100 |
| a square's two rows, no place stepped | 88 | 56 |

The level is dropped on the two paths that write a row's value and the
end of interrupt is written on all four, so those two lose 32 cycles and
the two that end a source lose 16, and 24 where the tune runs one effect
and the nops below already stand. A source ends once a pass and its rows
are written many times, so 32 a tick bounds what comes off a frame: 941
cycles on Synergy Credits, 595 on DBA 2, 485 on DBA 5 and 295 on
Turrican - world 4-3. The two switches go together, since automatic end
of interrupt sets no in-service bit and nothing but the level a tick
holds keeps a lower timer out, and the player takes the MFP's vector
register at init and puts it back at stop.

The four cores write one tune the same but for a square's edge. Traced
under Hatari over 900 frames of Synergy Credits, counted from the frame
the player first writes in, every register a row writes takes the same
values in the same order from all four, to the row the window ends on.
The two a timer drives part at a few of their writes: the plain and
lean cores hold 2,036 of R9's values in common, of the 2,037 and 2,039
they write, and the lean core and the one with both switches 6,621 of
R10's, of 6,626 and 6,630. A parting is one toggle landing the other
side of a frame's edge, a tick of another length returning inside the
call at another point, which lengthens one half of the wave and
shortens the next. `ym/writes.py` reads two traces back this way.

A tune that runs one effect has nothing to nest inside a tick, so init
writes two nops where that effect's handlers drop the interrupt level:
8 cycles a tick, the drop costing 16 and the nops 8. Only the row a
tick writes drops it and the marker's two paths never did, so the last
two rows above stand at what the four above them do. A tune running two
or more effects keeps the drop, since a faster timer waits behind a
slower one without it.

A tune whose sources are of other shapes pays 2
to 4 cycles a frame for the vector each start now writes, and a tune of
squares 3 to 22 for the two values and the difference each start
patches, against the 626 to 911 its ticks no longer cost.

Init resolves every source the tune names into its first row and its
loop cell, eight bytes each in the workspace, so a start reads two longs
where it walked the tune's index and read the source's header: 48 cycles
against 100. That is 38 a frame on Synergy Credits, 22 on DBA 2 and 21
on DBA 5, and 60 to 94 off their costliest frames. The source column's
seven bits reach 127, so the room stands for that many however few the
tune names, and YMXR_FIXED is 1,072 bytes against 56.

The rig reads every figure here back with `-cycles`, and `-hatari` plays
the same tunes on a cycle-exact machine, where the MFP fires the ticks.
