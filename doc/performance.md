# performance

What a play call costs, in cycles, measured on the eleven tunes under
`ym/test` by the rig's cycle counter (tools.md 17): 68000 cycles,
with no wait state, over every frame of a tune played through its wrap
once. A tick handler's cost is its instructions, from its vector to its
`rte`; the 68000 spends 44 cycles more entering an interrupt and 20 on the
`rte`.

## The play call

The call, and the share of it spent in DTX's advance: a turn a row, which
refills one live column a period's rows out of its ST4 data set, or runs
idle past the last.

| tune | frames | on average | at most | the advance on average | in the costliest frame |
|---|---|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | 432 | 1088 | 2794 | 547 | 2270 |
| Chambers of Shaolin 5 - you blew it! | 1000 | 1171 | 3820 | 575 | 3128 |
| Circus Attractions 2 | 8 | 1485 | 1648 | 766 | 766 |
| DBA 2 | 19444 | 1496 | 4106 | 664 | 3386 |
| DBA 5 | 22264 | 1590 | 3824 | 740 | 2734 |
| Digidrum preempt, built | 800 | 1648 | 4022 | 777 | 2424 |
| Retrigger retune, built | 1200 | 1402 | 2820 | 668 | 2096 |
| Synergy Credits | 10756 | 2021 | 5074 | 903 | 2924 |
| Turrican - world 4-3 | 3680 | 1534 | 4402 | 779 | 3716 |
| Turrican 2 - world completed 1 | 182 | 1401 | 4112 | 697 | 3456 |
| capture | 1844 | 1286 | 3576 | 663 | 3048 |

A table packs at unit 2 and a period of thirty rows, the column count, so
a refill is fifteen units of two bytes, and a period's thirty turns are one
a live column and the rest idle (tools.md 4). A column whose rows are one
value is constant: the payload has its value, the player reads it as an
immediate, and it has neither ring nor refill. On these tunes 12 to 22 of
the thirty columns are live. A turn past the last slot costs the advance
144 cycles.

The cost of a refill depends on how the column packed: a run of long
matches costs 12 cycles a unit to copy, and a short operation, a length
and an offset read bit by bit, about 190 to 260 an operation to parse.
The advance spends 454 cycles a refill outside the decoder, loading and
storing the decoder's eight registers, moving its mark and stepping to
the next; 416 where a column fits the ring, since the mark the advance
moves is a replayed loop and ST4 replays a loop only past the reach of a
back reference (DTX, abi.md 4). Two of the eleven tunes fit: Circus
Attractions 2, whose loop is four rows, and Turrican 2 - world completed
1, whose loop is two. A refill that ends at the ring's end costs 16 more,
which puts the pointer back by N, and at a ring of sixty bytes, two
periods, every other refill ends there. Circus Attractions 2 plays eight
frames, each a slot's first refill after the seed and each ending at the
ring's end, and reads 432.

A refill that parses no new operation copies inside a match the ring
already has, at one of three costs by where the match's source lies: 334
cycles inside the decoder where the match's source is after the ring's
start, 378 where it is before that start, and 516 where it crosses that
start. The 44 and the 182 above the first are the ring's size added to the
source (ST4's `source_wrapped`) and, at the crossing, the copy cut into two
segments. A refill at unit 1 is 576.

The 190 to 260 is a least-squares fit of the decoder's cycles against the
operations parsed, over every refill of a tune: 190 on Turrican 2 - world
completed 1 and 259 on Big - Samantha Fox Strip Poker 6. The endpoints of
a tune whose heaviest refill parses ten operations or more read lower, 179
to 213: the fit counts every refill, and a refill that parses few
operations is mostly the fixed part. On Turrican - world 4-3 the 334 above
and 3,246 at its heaviest are 2,912 over fourteen operations, about 208
each. A refill of 454 outside, 16 more at the ring's end, and the heaviest
3,246 inside are the 3,716 the table above reads as Turrican's advance in
its costliest frame.

Unit 1 packs a table smaller and costs more to decode: Turrican - world 4-3
packs to 3,332 bytes at `-k1` against 3,600, its advance reads 929 on
average and 4,136 in the costliest frame against 779 and 3,716, and the
play call 1,684 on average and 4,878 at most against 1,534 and 4,402.
`-k1` packs at unit 1. Five of the eleven tunes of Scavenger's in the
corpus pass R4.5's budget at unit 1, DBA 4's costliest frame the furthest
at 7,214, and none of them at unit 2.

The frame procedure is the rest, from 541 to 1,118 cycles on average: the
fourteen register columns' tests and the writes they admit, the effects'
columns and the call's entry and exit. The call jumps over an effect the
tune leaves unused, and two nops replace the jump at the head of its columns
where the tune runs it; an effect it runs tests its three set-bit columns in
one pass and reads its count alone where none is set; a tone period and the
envelope period branch on a zero byte before the bit beside it; a column's
select is formed only where the column is set; init writes a constant
column's value into each read of it, 8 cycles where a read through the row
pointer is 12; and the frame procedure is inline in the call.

The costliest frame of a tune is its heaviest refill, or a frame
within a few hundred cycles of it: the column and the period whose fifteen
units packed as the most operations. A table that repeats puts each live
column's decoder back to what it was at the loop's first row at that
column's next refill, one column a turn over the period after the pass's
end, so each row pays for one decoder's copy at most.

R4.5 budgets 6,656 cycles a frame. Every frame of every tune is within it:
the averages by more than two thirds of it, and the costliest frame of
every tune by 1,582 cycles or more, Synergy Credits' 5,074 the nearest.
The ticks of a frame come on top of the call, and A tick below counts them.

Release 0.4.22 packed every column at unit 2 through a ring of 960 bytes.
Its play call, counted the same way on its player, against the table above:

| tune | on average, 0.4.22 | on average | at most, 0.4.22 | at most |
|---|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | 1292 | 1088 | 1924 | 2794 |
| Chambers of Shaolin 5 - you blew it! | 1367 | 1171 | 3258 | 3820 |
| Circus Attractions 2 | 1446 | 1485 | 1648 | 1648 |
| DBA 2 | 1674 | 1496 | 4272 | 4106 |
| DBA 5 | 1754 | 1590 | 3606 | 3824 |
| Digidrum preempt, built | 1627 | 1648 | 2762 | 4022 |
| Retrigger retune, built | 1490 | 1402 | 2220 | 2820 |
| Synergy Credits | 2030 | 2021 | 5094 | 5074 |
| Turrican - world 4-3 | 1606 | 1534 | 4532 | 4402 |
| Turrican 2 - world completed 1 | 1606 | 1401 | 4060 | 4112 |
| capture | 1414 | 1286 | 3422 | 3576 |

The call costs less on average on nine of the eleven, and 21 and 39
cycles more on Digidrum preempt, built and Circus Attractions 2. Its
costliest frame is higher on seven, by 52 to 1,260 cycles, lower on
three, by 20 to 166, and the same on Circus Attractions 2. Under 0.4.22
the nearest frame to the budget was 1,562 cycles under it, and is 1,582
under it now.

A tune needs its bound tune (BINARIES.md 1) and a state block in the
workspace, the rings among them. The tools of 0.4.22 and of this release
write these, in bytes:

| tune | bound tune, 0.4.22 | state block, 0.4.22 | bound tune | state block | the two against 0.4.22 |
|---|---|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | 3448 | 30312 | 2004 | 1800 | -89% |
| Chambers of Shaolin 5 - you blew it! | 3904 | 30312 | 2564 | 1800 | -87% |
| Circus Attractions 2 | 3292 | 30312 | 1892 | 1416 | -90% |
| DBA 2 | 9740 | 31272 | 12548 | 5560 | -56% |
| DBA 5 | 14932 | 31272 | 19340 | 5560 | -46% |
| Digidrum preempt, built | 4096 | 30312 | 3344 | 3060 | -81% |
| Retrigger retune, built | 3952 | 30312 | 3584 | 2640 | -82% |
| Synergy Credits | 9600 | 31272 | 12956 | 4520 | -57% |
| Turrican - world 4-3 | 5084 | 31272 | 5772 | 2500 | -77% |
| Turrican 2 - world completed 1 | 4084 | 30312 | 2792 | 1848 | -87% |
| capture | 4328 | 30312 | 3368 | 1940 | -85% |

The state block was a ring of 960 bytes for each of the thirty columns,
and is now a ring of 60 to 240 for each live column, the ring of tools.md
4.2 whose state block and table come to the least. A smaller ring reaches
fewer rows back to copy from, and four bound tunes grow by up to 4,408
bytes: DBA 5's is 13,720 bytes at `-m960` and 19,340 at the 240 the writer
chooses, against a state block of 17,800 and 5,560. The two together are
46 to 90 per cent smaller on every tune.

## The raster monitor

The figures above are the rig's, counted instruction by instruction under an
emulated 68000. A second measure runs on a cycle-exact machine, and the
player is built with it: `rmac -dYMXR_PERF=1` puts the raster monitor in
(`68k/YMXR.S`), and `bin/ymxr-sndh -perf` puts that core in an SNDH file.
The call paints the background red while its work runs, one scanline to 512
cycles, and each tick handler paints a separate colour where the beam
is: effect 0 green, effect 1 blue, effect 2 magenta, effect 3 cyan, four
colours distinct from the call's red and the bar's yellow, so a reader
separates every band. A tick's band is a few hundred cycles wide, so each
handler also adds a count of what its instructions cost, in turns of ten
cycles, and the next call burns the total off as a yellow bar after its
work, the register writes among them: the bar leaves every chip write where
it was and shows what the handlers cost the frame. The call waits for the
display to start before it paints, since the VBL fires far above the screen
and an unsynced bar lands in the top border, out of sight; the wait is
before the red mark, so it is outside the figures, and it is bounded, so a
call from anywhere runs on. With the switch off, the default, the player is
byte for byte the player without it.

`ym/cost.sh` builds a program with that core, runs it under Hatari tracing
the writes to the background, and `ym/cost.py` reads every call's span back:
the red mark to the yellow one is the call's work, less each tick band
inside it, and the yellow to the write that puts the desktop's colour back
is the bar. What the method leaves out: the ticks' counts are estimates,
each a path's cycles rounded to a turn of ten and so within 4 cycles of what
A tick measures below; what a tick costs beyond its handler, the 44 cycles
of the interrupt's entry and the 20 of its `rte`, falls where the tick
landed, inside the call's work where it landed there and left out where it
did not; a tick that lands inside the bar is counted in the next call's; and
the wait moves the call's writes in time, which is why the monitor is a
build for reading a run rather than one to play a tune with.

A call writes the row the call before it read, and reads the next once its
writes are made (68k/YMXR.S), so a row's refill comes after that row's
writes. Measured on DBA 2 over 3,000 frames,
the call's first register write is at least 772 cycles after the VBL.
YMX writes its fourteen registers before it decodes for the same reason.

Measure from the VBL, at the tune's rate. Where the program plays from Timer
C, a call runs inside the tick handler the timer interrupted, since a handler
drops the level once its write is made (SPEC.md 5), and the wait then keeps
that handler open for as long as it runs: that timer's next ticks wait with
it, and the tune's sound changes. A file whose clock tag names the VBL, which
`-perf` and `-vbl` write (BINARIES.md 3.2), plays from it, and every call then
runs outside the tick handlers.

## The program's clock

The tick a program plays from costs the CPU what its handler runs, and
the stub arms Timer C at the lowest multiple of the tune's rate the MFP
counts exactly (BINARIES.md 4.10). Read by Hatari's profiler on its
cycle-exact CPU over a few thousand ticks of a tune at each rate: at 50
Hz, where a row lands every third tick, the handler runs 192 cycles a
tick on average, its `rte` among them, and at 60 Hz, a row every fourth,
181. The routine around the play call, which saves every register before
the call and restores them after, runs 416 cycles a row, the call apart.
The profiler reads instructions, and the 44 cycles of the interrupt's
entry come on top of each tick. `test_ymxr.py -clock` reads the three
figures back.

A 50 Hz tune ticks 150 times a second, so the clock costs 150 x 236 +
50 x 416 = 56,200 cycles a second, 0.70 per cent of the 8,021,247 the
CPU runs. A 60 Hz tune ticks 240 times, 240 x 225 + 60 x 416 = 78,960
cycles a second, 0.98 per cent.

The two averages put a tick without a row at 148 or 149 cycles of the
handler, by the operating system under the program, whose code the `rte`
returns into. The 200 Hz clock the stub armed before 0.4.11 ran 50 such
ticks a second more for a 50 Hz tune, about 9,600 cycles with their
entries, and 40 fewer for a 60 Hz tune than the 240 of the even spacing
(4.10), about 7,700 cycles; a rate the MFP counts no multiple of under
400 keeps the 200 Hz clock.

The VBL costs the handler of BINARIES.md 4.8 once a frame, 50 ticks a second
at the screen's rate, and the row lands where the frame procedure runs.

## Against YMX

YMX 0.10.1 measured its player the same way, painting the background red
while a call runs and reading the palette writes back from a cycle-exact
Hatari. So the two players were read by one method, on the same dumps, each
over a `VBLS=2300` run: 2,019 calls of YMX and 2,020 of YMXR. YMX is a
design document now, and its rows are in this document alone, measured on
the player of its 0.10.1 release; YMXR's are this release's, `ym/cost.sh`
over the two tunes under TOS 2.06. `test_ymxr.py -cost` measures them again
over the first 2,020 calls, under the operating system `TOS` names: a tick's
entry and `rte` fall inside a call or outside it by where the tick lands,
and where it lands moves with the operating system and the build of Hatari,
so an average there reads within 2 cycles of the table and a single call
within 64.

| tune | player | on average | the 99th call in a hundred | at most |
|---|---|---|---|---|
| Synergy Credits | YMX 0.10.1 | 2330 | 3680 | 4716 |
| Synergy Credits | YMXR | 1932 | 3528 | 4528 |
| Turrican - world 4-3 | YMX 0.10.1 | 1897 | 3360 | 4284 |
| Turrican - world 4-3 | YMXR | 1573 | 3584 | 4560 |

These figures and the rig's are two samples: the rig counts every frame
of the tune in 68000 cycles with no wait state, and these are the first
2,020 calls on a machine that stalls the processor while the shifter
fetches. Synergy Credits' costliest frame is its 5,347th, past the end of
this run.

YMXR costs a sixth less on average on Turrican - world 4-3 and a sixth less
on Synergy Credits, and at their worst 6 per cent more on Turrican - world
4-3 and 4 per cent less on Synergy Credits, and the figures have two
causes. The frame procedure here tests the fourteen register columns and
writes the ones they admit, reads the effects' columns and enters and leaves
the call, where YMX writes its fourteen registers unconditionally, one
`movep` each, and its whole call with no decode in it is 908, the writes
included. Fourteen tests and a few writes cost what fourteen writes cost,
which YMX's measurement found and its design follows; the schema adds the
effects' columns and the entry. So YMXR's frame procedure costs less on the
rows that set few columns, 755 on average on that tune, the rig's call there
less its advance, against YMX's 908, and its average is the lower.

The refill is the second cause. YMXR refills fifteen units of two bytes on
the turns that refill a live column, seventeen in thirty on Turrican -
world 4-3, and runs an idle turn on the rest; YMX serves a round-robin of
twenty-four slots, twenty-one of them a live stream, so its group is
twenty-four bytes, twelve units at unit 2, and it refills on twenty-one
rows in twenty-four: the three slots without a stream refill no column and
cost 1,012 cycles a call over 252 of the 2,019 calls on Turrican - world
4-3, against 2,023 on the 1,767 calls that refill. YMX's refill is the
smaller of the two, twelve units against fifteen. A refill parses at most
one operation a unit, so its unit count bounds the parse: YMX's worst frame
parses twelve operations of twelve, and Turrican - world 4-3's heaviest
refill here fourteen of fifteen. On Synergy Credits the run ends before
YMXR's costliest frame, so its 4,528 at most is not that frame, which the
rig counts at 5,074. Both players pad this tune to unit 2, YMX by
duplicating a frame and YMXR by an unset row (SPEC.md 6, rule 6). At `-k1`
YMXR packs it at unit 1: the refill is thirty units, and a run of the same
length reads 5,560 at most.

YMX alone writes its register columns unconditionally, dense, with the
effects behind one bit. Counted by the rig against the player as it is,
each register written as the player writes a set column, in 36 cycles, and
the effects' steps behind a test of 22, that costs 22 to 141 cycles a
frame on seven of the eleven fixtures and saves 2 to 56 on the other four,
for rewriting 1.1 to 1.7 and redefining five bits against R6.2. YMX's
finding that a test costs what a write costs applies to a 30-cycle test
and not to the 22 of a test that forms the select only where it writes.

---

## A tick

| tick | cycles |
|---|---|
| a row written, the place stepped | 96 |
| the marker, the place to row `RR` | 114 |
| the marker, the timer stopped | 124 |
| a square's two rows, no place stepped | 80 |
| a source of one row, no place stepped | 56 |
| a row written, the tune running one effect | 88 |
| the marker to row `RR`, one effect | 114 |
| the marker and the stop, one effect | 124 |
| a square's two rows, one effect | 72 |
| a source of one row, one effect | 48 |

A tick reads its row through the program counter by default; the section
below measures an absolute address instead.
With the interrupt's entry and its `rte`, a tick is 160 cycles: 12% of an
8 MHz 68000 at a digidrum's 6,000 ticks a second, and 51% at 25,600.

A source of two rows repeating to row 0 runs a separate handler
(68k/player/ticks.S, SQUARE), which encodes both rows as immediates and
moves between them by their difference: 80 cycles, 144 with the entry and
the `rte`, against the 160 and 178 the two paths of the general handler
cost. A tune whose effects are all such sources ticks 29.4 times a frame on
Synergy Credits, 24.8 on DBA 2 and 20.2 on DBA 5, so 735, 620 and 505
cycles a frame come off those tunes, against a play call of 2,021, 1,496
and 1,590 (the table above).

A tick drops the interrupt level and writes an end of interrupt because the
MFP runs in software end-of-interrupt mode, as TOS leaves it, and because a
faster timer may nest inside a slower one. A host that requires neither
uses the core assembled with `YMXR_NEST=0` and `YMXR_AEOI=1` (BINARIES.md
2.1), where a tick writes its two chip registers and returns:

| tick | as it is | lean |
|---|---|---|
| a row written, the place stepped | 96 | 64 |
| the marker, the place to row `RR` | 114 | 98 |
| the marker, the timer stopped | 124 | 108 |
| a square's two rows, no place stepped | 80 | 48 |
| a source of one row, no place stepped | 56 | 24 |

The level is dropped on the three paths that write a row's value and the end
of interrupt is written on all five, so those three lose 32 cycles and the
two that end a source lose 16, and 24 where the tune runs one effect and the
nops below are already in place. A source ends once a pass and its rows are
written many times, so 32 a tick bounds what comes off a frame: 941 cycles
on Synergy Credits, 595 on DBA 2, 485 on DBA 5 and 295 on Turrican - world
4-3. The two switches go together, since automatic end of interrupt leaves
the in-service bit clear and the level a tick sets is then all that keeps a
lower timer out, and the player claims the MFP's vector register at init and
restores it at stop.

The four cores write one tune the same. Traced under Hatari over 900 frames
of Synergy Credits, counted from the frame the player first writes in,
every register receives the same values in the same order from all four,
15,022 writes in all, the two registers a timer drives among them; 10 to 18
writes fall in another order between registers, a tick's write landing at
another point among a row's. A square's toggle can also land the other side
of a frame's edge, a tick of another length returning inside the call at
another point, which lengthens one half of the wave and shortens the next;
`ym/writes.py` reads two traces back this way, and reports such a toggle as
a register parting at one write.

A tune that runs one effect has no second timer to nest inside a tick, so
init writes two nops where that effect's handlers drop the interrupt level:
8 cycles a tick, the drop costing 16 and the nops 8. Only a path that
writes a row's value drops the level, so the two marker rows of the second
five equal those of the first five. A tune
running two or more effects keeps the drop, since a faster timer waits
behind a slower one without it.

A tune whose sources are of other shapes pays 2 to 4 cycles a frame for the
vector each start now writes, and a tune of squares 3 to 22 for the two
values and the difference each start patches, against the 343 to 500 its
ticks no longer cost.

A source of one row repeating runs a separate handler the same way
(68k/player/ticks.S, ONEROW). A source of one row is the marker alone
(SPEC.md 3.2) and its place is at row 0, so the handler encodes that row as
an immediate, writes it, and moves no place: 48 cycles against the 114 the
general handler's marker path costs, and 56 against 114 where the tune runs
more than one effect. The kit's `retune` ticks 7.7 times a frame, a 383 Hz
buzzer, so 506 cycles a frame come off it against a play call of 1,379.
`retune` is a built tune: every source the corpus names has another shape,
83 tunes of it naming any source (experiments.md), so what the handler
saves is measured on this tune and the conformance kit alone. The target
belongs to the effect on all three handlers, patched at a start out of the
effect's record, so the handler follows the source's shape alone.

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
cell, eight bytes each in the workspace, so a start reads two longs where it
walked the tune's index and read the source's header: 48 cycles against 100.
That is 38 a frame on Synergy Credits, 22 on DBA 2 and 21 on DBA 5, and 60
to 94 off their costliest frames. The source column's seven bits reach 127,
so the room is sized for 127 sources. A second table of the same room is
beside it, where a source of several columns has its loop cell and the
stride from one column to the next, and an effect's record is 20 bytes where
it was 8, the last 4 its source's row count, so YMXR_FIXED is 2,136 bytes
against 56. With the deferred start in, the record is 64 bytes, the last 44
a pending start's, and YMXR_FIXED 2,312 (A deferred start).

## A tick through a transformation

`YMXR_MAP=1` enables optional lookup support and version 10 playback
(BINARIES.md 2.1). Identity uses the ordinary handlers measured in A tick;
a transformation uses the matching lookup instance
(68k/player/lookup_ticks.S, TICKL and TICKWL). A tick writes its source key
into the low displacement byte of `move.b page(pc),$FFFF8802.w`. The high
byte selects a page of 256 bytes, which init filled in the page area after
the core (BINARIES.md 5.6); the read writes the selected entry.

The general handler's source read and marker test, with and without a
lookup:

| the row's read and the marker's branch | cycles | bytes | the handler |
|---|---|---|---|
| through the identity: `move.b place(pc),port`, `bmi.s` | 28 | 8 | 96 |
| through a transformation: `move.b place(pc),key.l`, then `move.b page(pc),port`, `bmi.s` | 52 | 16 | 120 |
| for comparison: `lea` the table, an indexed read, d0 and a0 saved | 94 | 26 | 162 |
| for comparison: an indexed read through the program counter, d0 saved | 62 | 18 | 130 |

The handler is the tick that writes a row and steps the place on a tune of
several effects, and each row for comparison is that handler with its read
in place of the identity's. The key's write costs 24 cycles a tick and uses
no register. An indexed read through the program counter reaches one page,
the one beside the read, so it has no second page for a remap to switch to.
A source of 8,192 rows a second through a transformation costs 3,932 cycles
a frame more at 50 Hz.

Every lookup kind, on the three builds the rig counts: as it is, lean (A
tick) and with the row read through an absolute address (A tick through an
absolute address):

| tick through a transformation | as it is | lean | absolute |
|---|---|---|---|
| a row written, the place stepped | 120 | 88 | 132 |
| the marker, the place to row `RR` | 142 | 126 | 158 |
| the marker, the timer stopped | 148 | 132 | 156 |
| a row written, the tune running one effect | 112 | 88 | 124 |
| counted, a row written | 140 | 108 | 152 |
| counted, the end, the place to row `RR` | 182 | 166 | 198 |
| counted, the end, the timer stopped | 198 | 182 | 206 |
| two columns, a row written | 176 | 144 | 200 |
| two columns, the marker, the places to row `RR` | 206 | 190 | 234 |
| two columns, the marker, the timer stopped | 184 | 168 | 196 |
| three columns, a row written | 232 | 200 | 268 |
| three columns, the marker, the places to row `RR` | 270 | 254 | 310 |
| three columns, the marker, the timer stopped | 220 | 204 | 236 |
| two counted columns, a row written | 196 | 164 | 220 |
| two counted columns, the end, the places to row `RR` | 246 | 230 | 274 |
| two counted columns, the end, the timer stopped | 234 | 218 | 246 |

Each figure is the tick of the identity's handler of that shape and 24
cycles, 28 on the path that loops, and 34 when a counted source stops.
The lookup handler uses a word branch to the halt, and a counted handler
branches there after restoring its counter (68k/player/lookup_ticks.S,
TICKL). As it is and lean, the 24 is the
key's write; on the absolute build the key's write is 28 cycles, and the
page read through the program counter, 20, is 4 less than the identity's
read of an absolute address, 24. Init writes nops in place of the level's
drop in the general instance on a tune that runs one effect, as it does in
the general handler (A tick), and in no other lookup kind. The counted
instance of one column costs 20 cycles a row more than the general one on a
tune of several effects. The two kinds of two columns, the value mapped
written last (`setToneX`, `setEnvelope`) or first (`setNoiseX`), cost the
same, and so do the two of three, the value mapped last (`setBuzzer`) or in
the middle (`setVoiceX`), and the two counted kinds of two columns. A
square and a source of one row through a transformation run their handler
of the identity, with entries in its immediates, and tick at the cost A
tick counts.

## A remap at the frame

Starts through a transformation and remaps prepare lookups during the
frame's effect step (SPEC.md 1.10.2). Init fills each page of the bound
tune's list in the page area (BINARIES.md 5.6), with a table for each
effect and fill kind of the list, a byte a transformation, so a start
and a remap read their page in one byte and write its page byte; a page
outside the list is copied into the fallback, the page not in use, with
interrupts enabled. Patch handler operands and the vector at level 7, as
for identity starts (68k/player/lookup_start.S to lookup_kinds.S).

A start of a source that steps a place caches its lookup instance, and
init writes the addresses of the effect's three tables and the page of
its fallback, in the last 28 bytes of the effect's record, which is 56
bytes, and 100 with the deferred start in: the address of the instance's
page byte, the source's transformation and fill kind, the page byte of
the effect's reference for the instance, a byte set while the cache
names the running handler and no start is pending, the address of the
table of its fill kind, the three tables' addresses, the page byte a
start writes, the page of the effect's first fallback page, and two
bytes to a long; with the deferred start in, the bank of the cache is a
byte before them. A start of any other handler and the arming of a
deferred start clear that byte. A remap between two transformations, in
each effect's frame code, tests the byte and the timer, reads the new
transformation's page in the table, or else compares the fallback page
the instance does not read with it, and writes the page byte. Every
other remap runs the routine that resolves the running kind and its
instance, and writes the cache where it moves the source into a lookup
instance.

A start of a marked source of one column, the general kind, reads the
effect's instance, the page byte of its first fallback page and its
vector as constants of the effect's frame code, and writes its operands
at the offsets of that kind. Every other kind reads its descriptor, its
instance and its page byte by index from two tables and the table of
page bytes.

The following costs run from the remap's first instruction in the frame
code to the branch it ends at, or from the start's first instruction to
its return, excluding the routine that selects or fills a page and the
copy, for rows that start or remap one effect:

| the row | one column | a square | a source of one row |
|---|---|---|---|
| a start at row 0 | 414 to 494 | 574 | 462 |
| a remap between two transformations | 202 to 236 | 766 to 786 | 588 |
| a remap from the identity | 1408 | 766 | 588 |
| a remap to the identity | 1186 | 548 to 566 | 440 |

The ranges are the effect, a row that resets the timer, a source that
plays once, the half a square is at and the cache the start finds. A
start at the running source's row, with bit 5 of the control column
clear, reads that row as well, and on one column its remainder by R of
the source (SPEC.md 1.8.4): 606 to 808 cycles on one column, and 736 to
750 on a square. A remap on a stopped timer sets the
kept mapping alone, 86 to 130 cycles. The counted kinds and the kinds of
several columns write a register, a place and a loop cell a column: a
start at row 0 is 1,068 to 1,654 cycles on them, a remap between two
transformations 202, from the identity 1,536 to 2,006 and to the
identity 1,254 to 1,750.

Through the identity, a start at row 0 of a marked source of one column
costs 396 cycles from the effect's start in the frame code to its rate,
and through a transformation, the page found, 552 to 650. The lookup
adds the page's read in the table; the cache, written where it names
another instance; the call; and the page byte.

A start and a remap between two transformations read the page in the
table of the source's fill kind, and where it has 0 they compare the
transformation and the fill kind of the effect's two fallback pages with
the source's and fill the page not in use. A remap from the identity
selects its page in a separate routine, 140 cycles. Switching between
two transformations changes one page byte atomically, 8 cycles.
Switching between identity and a lookup also moves registers, loop cells
and places between handlers: 426 cycles on one column to 946 on three.
Init copies each page of the list, at these costs:

| the copy | the transformation's entries | cycles |
|---|---|---|
| entry k at k | 256 | 1582 |
| the same | 32 | 392 |
| the same | 16 | 214 |
| a marked column's: entry k at k, and at k + 128 with bit 7 set | 256 | 2228 |
| the same | 32 | 638 |
| the same | 16 | 374 |
| the same | 10 | 308 |
| a counted source on `setR7`: entry k at k + 192, bits 7 and 6 set | 64 | 916 |

So the routines of a volume's remap between two transformations, a marked
source of one column, cost 202 to 236 cycles, its page filled at init.
Without the lookup a source at several volumes is a separate copy at
each, and a change of volume is a start of another copy at the running
source's row. On a marked source of one column, the copy as long as the
running one, with the timer running and bits 6 and 5 of the control
column clear, that start costs 412 cycles from the effect's start in the
frame code to its rate, and 420 with the deferred start in, where the
start reads the running handler through the vector. On one span, from
effect 0's branch on the set bit of its source column to its rate, the
copy's start costs 444 cycles, and 452 with the deferred start in, and a
remap between two transformations, its page found and the timer running,
254 on the core with the lookup: a change of volume through a
transformation costs 190 cycles less there than a change of copy. Beyond
that span the core with the lookup costs more at the head of each effect,
in the table below, and on each tick (A tick through a transformation).

A page outside the area is copied at the frame, into the effect's
fallback page not in use (BINARIES.md 5.6). With that copy, the routines
of a start and a remap of a marked source of one column cost, from the
remap's first instruction in the frame code to the branch it ends at, or
from the start's first instruction to its return:

| the row | the transformation's entries | the routines | the copy | the rest |
|---|---|---|---|---|
| a remap between two transformations | 16 | 1062 | 374 | 688 |
| the same | 32 | 1326 | 638 | 688 |
| a start at row 0 | 16 | 1342 | 374 | 968 |
| the same | 32 | 1606 | 638 | 968 |

The copy costs what init's copy of as many entries in the same form
costs in the table above, and the rest is the routines less the copy.

Identity-only tunes still run ordinary tick handlers on a lookup core.
Frame processing costs more than on the core without lookup support.
These differences are measured on the default build with the rig's three
version 9 fixtures, covering each source shape and place reset:

| a path of the frame | cycles more |
|---|---|
| the head of each effect the tune runs, a frame | 8 |
| a start at row 0 | 10 |
| a start at the running source's row, after a source that steps a place | 18 |
| a start of a counted source or of one of several columns | 12 |
| a square's start at row 0 | 22 |
| a square's start at the running source's row, after a source that steps a place | 30 |
| a place's reset | 8 |
| a square's place reset | -12 |
| a counted source's place reset | 224 |
| a place's reset of a source of several columns | 352 |

The head reads the constant 0 of the effect's mapping column, and a
start runs the branch init aims at the start through the identity, 10
cycles. A start at the running source's row and a place's reset read the
running handler through the vector, 8 cycles, where the core without the
lookup reads its address. A start of a counted source or of one of
several columns multiplies by the size of the effect's record, 56 bytes
where it is 20, 2 cycles more: the multiply costs 2 cycles a bit set in
the size, and 56 has three where 20 has two. A square's start writes row
0's level into the effect's record, 12 cycles, and its place's reset
then writes it in one move, 12 cycles less. The place's reset of a
counted source or of one of several columns runs one routine for every
kind, through the kind the source runs (ymxr_widereset in
68k/player/start.S, ymxr_kindof in lookup_kinds.S), where the core without
the lookup runs a routine for each shape. Over the eleven fixtures, the
play call counts 0 to 40 cycles more on average on this core than the table
of The play call, and 0 to 92 more at most.

## The cost of 34 columns

Version 10 adds four mapping columns to version 9's thirty (SPEC.md 3.3).
DTX's period is at least the column count (DTX, abi.md 4). The rig's lookup
tunes pack at unit 2 with a period of thirty-four rows: seventeen units
per refill, against fifteen for thirty columns (The play call). The
eleven dump fixtures use identity and retain thirty columns.

The routines of A remap at the frame run inside the play call. The
costliest call of each tune the rig plays through a transformation, and its
costliest with the routines of its starts and its remaps counted apart, as
R4.5 counts them:

| tune | the costliest call | the costliest, the starts and remaps apart |
|---|---|---|
| a fade on setR8 | 2388 | 1894 |
| a fade of setVoiceA's volume | 3482 | 1894 |
| a mapped square and its remap | 2502 | 1894 |
| a mapped source of one row and its remap | 2388 | 1894 |
| a source remapped from the identity to T, to T' and back | 3094 | 2134 |
| a remap on an idle timer and on a stopped one, each kept by a start | 2384 | 1928 |
| an 8-bit sample on setR10 through 256 entries | 3012 | 1904 |
| the coarse nibble of setToneA | 3270 | 2450 |
| the shape of setBuzzer | 3580 | 1982 |
| the volume of setNoiseA | 3500 | 1978 |
| the high byte of setEnvelope | 3400 | 2510 |
| a table of 10 entries on setR9 | 2420 | 1894 |
| setR7 through a transformation | 2952 | 2354 |
| a start through T on setToneA, the target alone to setNoiseA, a place's reset and a remap | 3470 | 1964 |
| a start through T on setVoiceA, the target alone to setBuzzer, a place's reset and a remap | 3726 | 1964 |
| a counted source on setR7 remapped from the identity after the target alone | 3292 | 2662 |
| starts through a new mapping with placeReset clear while a source ticks | 2738 | 1894 |
| a remap and a retune with timerReset on a running timer | 2420 | 1894 |
| a remap and a retune with timerReset on a stopped timer, kept by a start | 2416 | 1938 |
| a place's reset on a mapped square, loop and counted source, three effects | 5216 | 3104 |
| one transformation through the mirror and through the plain fill | 2442 | 1894 |
| a transformation of 32 entries through the mirror and through the plain fill | 2442 | 1894 |
| squares and a loop started through a new mapping with placeReset clear | 2502 | 1894 |
| starts with placeReset clear while a source of several columns runs | 3482 | 1894 |
| remaps on Timers C and D, which share a control register, each idle while the other runs | 2928 | 2090 |
| a remap and a retune with timerReset on a running square | 2502 | 1894 |
| a stop row with the mapping column set, then a start with it unset | 2420 | 1928 |
| a remap in the row that sets the target to setNoiseA | 3452 | 1946 |
| a marked source on setEnvelope through T, a remap, a place's reset and a remap from the identity | 3280 | 2490 |
| counted sources of two columns on setToneA and setNoiseA through T, a remap, a place's reset and a remap from the identity | 5764 | 3644 |
| sources that play once on seven lookup kinds and loop on five, two effects | 5596 | 2472 |
| sources that play once on seven lookup kinds, one effect | 3540 | 1964 |
| four sources through 256 entries remapped in one row | 8182 | 3750 |

Of the 33, one passes R4.5's 6,656, at its first row, which starts
sources on two or more effects through a transformation, and with the
routines counted apart every call is within it. The costliest starts four
counted sources, and its frame that remaps the four to another
transformation costs 2,930.

## A counted tick of two columns

`setEnvelope` writes R11 and R12, and both read every bit of their byte,
so a source on that target is counted: its rows are whole bytes and a
tick counts them (SPEC.md 2.1.3). The wide handler runs that source with
a word counter where the marked one tests bit 7, 134 bytes an instance
against 112, and four instances of it are beside the two marked shapes.

Both tunes of the kit that sweep the envelope period run one effect. Init
writes the nops in the general handler, the square's and the one-row
source's alone (A tick), so these ticks drop the level as on a tune of
several effects, and the figures are of the player as it is. The last two
columns are the player whose ticks read a row through an absolute address
(A tick through an absolute address):

| tick | marked | counted | marked, absolute | counted, absolute |
|---|---|---|---|---|
| a row written, the places stepped | 152 | 172 | 176 | 196 |
| the end, the places to row `RR` | 178 | 218 | 206 | 246 |
| the end, the timer stopped | 160 | 200 | 172 | 212 |

So 20 cycles a tick that writes a row, as the counted handler of one
column costs against the general one, and 40 on the path that loops,
where the counter is reloaded too. The counted handler reaches
the half of the envelope period above a marked source's range, and plays
the last row as written: the tick that reads the last row of a marked
source on this target
writes the marker's bit into R12 with the value, a period 32,768 more than
the row's, which the kit's `envelope` tune does on its first source's last
two rows, 800 and then 33,696, its 928 with the marker.

The player is 11,066 bytes, 1,592 of them the counted shapes' twelve
instances, four each of one, two and three columns. Where a tick reads
an absolute address, 10,764 bytes and 1,696, instances of 144 bytes
against the marked shape's 122 for two columns.

## A tick through an absolute address

`YMXR_PCREL=0` assembles a player whose tick reads its row through an
absolute address rather than through a signed word displacement from the
instruction that reads it (68k/player/switches.S; BINARIES.md 5.5), for a
host that places a tune further off than a displacement reaches. The read
costs 20 cycles where a displacement costs 16; the step that moves the
place is an `addq.l` on a long address, 28 against the 20 an `addq.w` on a
displacement costs; and the loop's test and its reload move the same way,
20 against 16 and 36 against 28. A square's handler and a one-row source's
write a value the start patched into them, so both cost the same in either
build:

| tick | cycles |
|---|---|
| a row written, the place stepped | 108 |
| the marker, the place to row `RR` | 130 |
| the marker, the timer stopped | 132 |
| a square's two rows, no place stepped | 80 |
| a source of one row, no place stepped | 56 |
| a row written, the tune running one effect | 100 |
| the marker to row `RR`, one effect | 130 |
| the marker and the stop, one effect | 132 |
| a square's two rows, one effect | 72 |
| a source of one row, one effect | 48 |

The lean tick of the table above leaves the same two writes out of these
paths, the level's drop on the three that write a row's value and the end
of interrupt on all five:

| tick | as it is | lean |
|---|---|---|
| a row written, the place stepped | 108 | 76 |
| the marker, the place to row `RR` | 130 | 114 |
| the marker, the timer stopped | 132 | 116 |
| a square's two rows, no place stepped | 80 | 48 |
| a source of one row, no place stepped | 56 | 24 |

So the player as it is saves 12 cycles a tick that writes a row and
steps its place, 16 on the loop and 8 on the stop: at a digidrum's 6,000
ticks a second, 1,440 cycles a frame. A handler of two columns saves
twice that a tick and one of three three times, since each column is a
read and a step; the counted handler saves the same 12 as the general
one.

The raster monitor reads this build as it reads the others, and the bar
it burns for the ticks is the figure that moves. Under Hatari over 420
calls of Turrican - world 4-3 on a `-perf-abs` core, against the same run
on the core the tools choose: the call costs 1,483 cycles on average
against 1,484, and a tick 156 against 144, so the bar is 2.47 lines on
average and 32.00 at most against 2.24 and 28.87. `ym/cost.py` reads the
colour writes back.

A start writes the place it patches behind level 7, so that a tick reads
a handler once it is made, and in the player as it is the arithmetic
that turns a row's address into a displacement is in that window as
well: a dozen instructions this build leaves out, which a tick of a timer
due inside it waits for.

The displacement is a signed word, so every row of every source is within
32,767 bytes of the handlers. Init measures each source against them and
reports -1 for one further off (BINARIES.md 1.5), and the layouts put a
tune's DTX1 tables before its image and a set's subtunes before the
images, so the rows of the tune a host loads are beside the player at any
length of image. A host that places them further off assembles this build,
which is 10,764 bytes against 11,066. Two figures move against each other:
a place is a word where it was a long, which shortens a timer's handlers
by 52 bytes, and a start does the arithmetic that turns a row's address
into a displacement, 38 bytes more of the timer's frame code. The init and
the routines the four timers share, the reach check among them, are 358
bytes more.

What the two cores do to a tune, under Hatari over 900 frames of Turrican -
world 4-3, counted from the frame the player first writes in: both write
12,354 values, the same value to the same register in the same order, and
the two cores of 0.4.22 write the same. A write lands at another cycle: the
arithmetic a start does to turn a row's address into a displacement, and a
tick of another length returning inside the call at another point, move it,
and the median write of the absolute core's run is 1,184 cycles earlier
against the VBL. `ym/writes.py` reads the two traces back.

The rig reads these figures back: `-cycles` and `-cycles -abs` its counts,
`-clock` the program's clock and `-cost` the comparison with YMX under
Hatari; `-hatari` plays the same tunes on a cycle-exact machine, where the
MFP fires the ticks.

## A deferred start

`YMXR_DEFER=1` assembles a player with the deferred start (SPEC.md 4.8)
in, and the tools put a core of that build under a file only where a
subtune's effects byte sets bit 4 (BINARIES.md 2.1). Init of a player
without it reports -1 for such a tune. The deferred start leaves the
five paths of A tick as they are: the handlers keep their instructions,
each gains a stub after its last one, and the stub runs on one tick
alone. The figures below are counted from the 68000's instruction
timings, on that build with the row read through the program counter,
and start at the head of the path that ends a source.

At the frame of a deferred start on a running timer the player patches the
handler of the start's shape in the bank its vector does not point into,
writes the rate into the effect's installer, and writes one word into the
running handler, at the head of its last row's path: a `bra.s` to its
stub, or a `nop` where the stub follows the word. The tick that reads the
last row runs the stub and the installer, which writes the word back,
stops the timer where the start sets the timer's reset, writes the count,
the vector and the select, and returns; the next tick runs the armed
handler. A stop with a start pending writes the word back and drops the
start, since a set inits one loaded player for each subtune: a word left
in place sends the next subtune's tick at its last row into the installer
of the start armed before.

| the tick that reads the last row | cycles |
|---|---|
| today's, to row `RR` | 88 |
| today's, the timer stopped | 98 |
| the switch on Timer A or B | 142 |
| the same, with `timerReset` | 172 |
| the switch on Timer C or D, whose control register is shared | 202 |
| the same, with `timerReset` | 232 |

On Timer C or D the installer reads, masks and writes the control
register at level 7, as the frame procedure does: a tick of the other
timer of the pair nests inside the installer and writes the register too.
The rig counts the tick whole, 26 cycles more than the table on the
general handler's path and 44 on a square's: 168 and 258 on the
conformance kit's `deferred`, and 186, 216 and 246 on Synergy Credits with
every start deferred.

A square's stub tests the row it wrote: the tick that writes row 0 after
the start is armed costs 48 cycles more than a square's tick, and the
tick that writes row 1 runs the installer.

A tune whose rows defer no start has bit 4 of its effects used at 0
(SPEC.md 3.3), and init of this build leaves its frame the branches the
assembler wrote. A start with bit 5 at 0 and a row that resets the place
read the running handler through the vector, 8 or 16 cycles more than
the build without the deferred start, which reads it at its address as
version 7 did. On that build the play call of each of the eleven
fixtures counts the cycles of version 7, and on this build the play call
of Synergy Credits counts 2,031 cycles on average and 5,106 at most,
against 2,021 and 5,074. On either build a start with bit 5 at 0, after
a source whose handler steps a place, compares the row number with R, 32
cycles. A tune with bit 4 set runs each row that sets an effect's
target, source or control column through a test of the pending start,
about 50 cycles a start and 22 a stop or a retune.

The second bank, the installers, the stubs and the stop's write-back make
the player 16,476 bytes against 11,066, and 16,370 against 10,764 where a
tick reads an absolute address. Each handler gains a stub: an instance of
the wide handler is 138 bytes against 134, and of the marked shape of two
columns 116 against 112.

The lookup in, `YMXR_MAP=1`, makes the player 29,944 bytes against 11,066,
and 30,978 against 10,764 where a tick reads an absolute address. With the
deferred start in as well, 44,786 against 16,476, and 46,832 against
16,370.

## A cut

`YMXR_MAP=1` plays a tune that cuts a source as well, a tune of version 11
(SPEC.md 1.11; BINARIES.md 2.1). A start or a recut under a cut 1 to 127
runs the counted handler of the source's columns at every shape of source
(68k/player/cut.S), and a start under cut 0 runs the handler of the
source's shape, as in version 10. The rig counts the figures below on the
nine tunes of its check of cuts that defer no start, on the player as it
is.

| tick | a marked source under cut 0 | under a cut |
|---|---|---|
| one column, a row written | 96 | 116 |
| one column, the end of a pass, the place to its loop row | 114 | 154 |
| one column, the end of a pass, the timer stopped | 124 | 164 |
| two columns, a row written | 152 | 172 |
| two columns, the end of a pass, the places to its loop row | 178 | 218 |
| three columns, a row written | 208 | 228 |
| three columns, the end of a pass, the places to its loop row | 242 | 282 |

The counter of rows left costs 20 cycles a row written on each of the
three, and 40 at the end of a pass, where the handler reloads it (A
counted tick of two columns). A square that a start or a recut puts under
a cut of one of its rows runs the counted handler, and ticks at 116 and
154 in turn under its two rows after that, against 80 on its handler. A
recut to its two rows of a square on its handler, and every recut of a
source of one row, leave that handler running. A counted source runs the
counted handler under cut 0 as well, and ticks at the same figures under a
cut. Through a transformation a tick under a cut costs 24 cycles more a
row written and 28 more at the end of a pass, 140 and 182 on one column.
The column under cut 0 is of a tune of several effects; the counted
handler drops the level on a tune of one effect as well, so a tick under a
cut costs the same on either (A tick).

A start under a cut and a recut each run a routine of the cut
(68k/player/cut.S). A source of one column through the identity starts and
recuts on a separate path (ymxr_kone, and the first path of ymxr_recut),
which reads the cut's rows and the source's loop row and writes the place,
the loop cell, the counter and its reload of the counted handler of one
column. A source of two or three columns through the identity starts on a
second path of ymxr_kone, which reads the same and writes each column's
register, place and loop cell, and the counter and its reload, of the
counted handler of its columns, at the offsets the kind's descriptor
lists. A recut of a source the counted handler already runs writes the
places, the loop cells, the counter and its reload of that handler, and
leaves its registers, its page byte and the vector as they are. Every
other start, and a recut that moves a source of several columns or through
a transformation to the counted handler, runs the general routine, which
reads the source's shape and its kind first. A start is counted from the
effect's start in the frame code to its rate, and a recut from the
effect's branch on the set bit of its source column to its rate:

| the source | a start under a cut | a recut on the counted handler |
|---|---|---|
| one column | 840 to 916 | 1090 to 1120 |
| two columns | 1534 to 1540 | 1670 to 1710 |
| three columns | 1752 | 1810 |
| one column, through a transformation | 2138 to 2204 | 1404 |

The ranges are the effect, the source's shape and the cut. The table's
start puts the place at row F of the cut; a start with the place kept
reads the running row and its remainder by R as well, 1,206 to 1,226
cycles on one column and 1,872 on two. A recut that moves a source from
another handler to the counted handler runs the path of one column through
the identity on such a source, 1,082 to 1,160 cycles, the path of several
columns through the identity on a marked source of two or three columns,
1,842 cycles on two and 2,086 on three, and the general routine on every
other, 2,992 cycles on a square through a transformation. A recut that
leaves a square's or a one-row source's handler running sets the running
cut alone: 426 cycles on a square and 314 on a source of one row. A recut
on a stopped timer sets the kept cut alone, 116 cycles. On the same span a
start under cut 0 at row 0 of a marked source of one column costs 464
cycles.

The routines run inside the play call, and R4.5 counts them apart, as it
counts those of a start or a remap through a transformation. Each tune's
costliest call, whole and as R4.5 counts it:

| tune | the costliest call | as R4.5 counts it |
|---|---|---|
| a cut past row 0 that excludes the repeat row, its place reset, one after a recut to the whole table, and one of a source of one row under cut 0 | 4020 | 2628 |
| starts at the running row, inside a cut and outside it | 2922 | 2232 |
| a source that plays once under a cut, started again by a select | 2608 | 1960 |
| a square, one row and a counted source under a cut, recut to the whole table | 4928 | 3508 |
| a recut on a stopped timer, read by the start after it | 2656 | 1960 |
| starts under the whole table after a cut, each place reset after, and recuts of a loop and a square started under it | 5874 | 4454 |
| sources of two and three columns under a cut and under the whole table, recut, and a place's reset | 9486 | 4968 |
| starts through a transformation under a cut, remaps under it, and recuts, beside a timer that maps a source under the whole table | 9722 | 4124 |
| recuts on the timers that share a control register | 4118 | 2726 |

Every call of the nine is within R4.5's 6,656 as it counts them, the
costliest at 4,968. Counted whole, two pass it, each at its first row,
which starts four sources: 9,722 cycles, four through a transformation,
two of them under a cut, and 9,486, four of two and three columns under a
cut.

The player with the lookup in is 29,944 bytes against 21,966 in release
0.15.0, before the cut, and 30,978 against 23,256 where a tick reads an
absolute address; with the deferred start in as well, 44,786 against
35,908, and 46,832 against 37,954. Of the 7,978 bytes it is longer than in
that release, 3,208 are the cut's blocks, which a link leaves out of the
player of a song that cuts no source (LINKER.md): 2,688 of routines and
130 of each effect's frame code. With the deferred start in, the blocks
are 3,682 of the 8,878. The blocks of copy from frame, which a link keeps
only for a song that copies from frame, are 2,002 bytes of the rest, and
2,104 with the deferred start in (Copy from frame). The blocks of the sync
and of the handler of its targets, which a link keeps only for a song that
syncs a voice, are 2,526 bytes, and 2,802 with the deferred start in
(LINKER.md).

## Copy from frame

`YMXR_MAP=1` plays a tune that copies from frame as well, a tune of
version 12 (SPEC.md 3.1.7; BINARIES.md 2.1). A start of copy from frame
aims the counted handler of its target's columns at their frame values, a
source of one row with one row a pass, so every tick reads the last row of
the source and runs the counted handler's path at the end of a pass (A
tick, A counted tick of two columns), and through a transformation the
counted lookup instance's. A frame of such a tune runs the register steps
of the block `copy` in place of the frame's (LINKER.md): they find the
registers copy from frame runs on (below), keep each value they read,
masked to its register's width, as its frame value, and write the chip
where the register is outside those. Init aims the frame, the starts and
the head of each effect's paths at the block only for a tune that copies
from frame, so every other tune runs the frame and the ticks it ran in the
release before, at the same cycles, and a song of such tunes links the
same player as in that release.

The rig counts the figures below on the five tunes of its check of copy
from frame, on the player as it is with the lookup in, and with the
deferred start in as well on the two that defer a start. A tick:

| tick | cycles |
|---|---|
| one column | 154 |
| two columns | 218 |
| three columns | 282 |
| one column, through a transformation | 182 |
| three columns, through a transformation | 310 |

Through the identity and on one column through a transformation, each
figure is the counted handler's at the end of a pass under a cut (A cut).

A frame runs the block's register steps, and the steps a register cost 36
to 172 cycles more than the frame's steps on the same rows, which the rig
counts by playing each tune again with the frame's steps and without a
tick. Each keeps the value it reads as the frame value and tests whether a
tick writes the register. Before the steps, the frame finds the registers
the ticks write. It reads the effects' records for them again on two
kinds of frame: after a row that sets a column of an effect, which can
start, stop, aim or settle the effect's handler, and from the row that
arms a deferred start to the row that settles it, since a tick performs
the start in between (A deferred start). Such a read costs 618 to 1,618
cycles, and 888 to 1,466 with the deferred start in, by the effects whose
timers count and the columns of their handlers. Every other frame tests
the mark of such a row and uses the mask the last read found: 62 cycles,
and 142 with the deferred start in, where it tests each effect's pending
start as well. A row marks the frame in a stub of each effect whose
columns it sets (ymxr_csome), 44 cycles, which init reaches from a
`bsr.w` over the read at the head of the effect's paths.

A start of copy from frame at the frame, counted from the effect's start
in the frame code to its rate as A cut counts one, by its target's
columns:

| columns | the player as it is | with the deferred start in |
|---|---|---|
| one | 730 to 810 | 896 to 954 |
| two | 1040 to 1080 | 1266 to 1284 |
| three | 1296 | 1462 |

On the same span a start under cut 0 at row 0 of a marked source of one
column costs 464 cycles (A cut). Through a transformation, on the player
with the deferred start in, a start of one column costs 1,380 to 1,624
cycles.

A start of copy from frame runs a routine inside the play call,
ymxr_copystart through the identity and ymxr_copylook through a
transformation, and R4.5 counts both apart, as it counts those of a cut (A
cut). Each tune's costliest call, whole and as R4.5 counts it:

| tune | the costliest call | as R4.5 counts it |
|---|---|---|
| copy from frame on a register of each kind of handler | 8736 | 5612 |
| copy from frame on targets of two and three registers | 9178 | 5684 |
| copy from frame replaced and replacing, at the frame and deferred, and recut | 7720 | 5950 |
| copy from frame beside a mapped source, its timer remapped to mapping 0 | 4528 | 3420 |
| copy from frame through transformations, remapped, deferred and beside a mapped source | 10266 | 6060 |

Every call of the five is within R4.5's 6,656 as it counts them, the
costliest at 6,060. Counted whole, four pass it, each at its first row,
which starts copy from frame on three or four effects.
