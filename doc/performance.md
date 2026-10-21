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
| Big - Samantha Fox Strip Poker 6 | 430 | 1154 | 3292 | 652 | 2808 |
| Chambers of Shaolin 5 - you blew it! | 1000 | 1254 | 4314 | 698 | 3830 |
| Circus Attractions 2 | 8 | 1700 | 1902 | 1008 | 1008 |
| DBA 2 | 19442 | 1605 | 5484 | 812 | 4804 |
| DBA 5 | 22262 | 1707 | 5190 | 896 | 3862 |
| Digidrum preempt, built | 800 | 1787 | 4216 | 940 | 2642 |
| Retrigger retune, built | 1200 | 1522 | 2904 | 812 | 2204 |
| Synergy Credits | 10754 | 2201 | 5898 | 1109 | 4516 |
| Turrican - world 4-3 | 3680 | 1647 | 4838 | 929 | 4136 |
| Turrican 2 - world completed 1 | 179 | 1527 | 5394 | 864 | 4774 |
| capture | 1842 | 1384 | 6062 | 799 | 5462 |

A table packs at unit 1 and a period of thirty rows, the column count, so
a refill is thirty units of a byte, and a period's thirty turns are one a
live column and the rest idle (tools.md 4). A column whose rows are one
value is constant: the payload has its value, the player reads it as an
immediate, and it has neither ring nor refill. On these tunes 12 to 22 of
the thirty columns are live. A turn past the last slot costs the advance
144 cycles.

The cost of a refill depends on how the column packed: a run of long
matches costs 12 cycles a unit to copy, and a short operation, a length
and an offset read bit by bit, about 200 to 280 an operation to parse.
The advance spends 454 cycles a refill outside the decoder, loading and
storing the decoder's eight registers, moving its mark and stepping to
the next; 416 where a column fits the ring, since the mark the advance
moves is a replayed loop and ST4 replays a loop only past the reach of a
back reference (DTX, abi.md 4). Two of the eleven tunes fit: Circus
Attractions 2, whose loop is four rows, and Turrican 2 - world completed
1, whose loop is one. A refill that ends at the ring's end costs 16 more,
which puts the pointer back by N, and at a ring of sixty bytes, two
periods, every other refill ends there. Circus Attractions 2 plays eight
frames, each a slot's first refill after the seed and each ending at the
ring's end, and reads 432.

A refill that parses no new operation copies inside a match the ring
already has, at one of three costs by where the match's source lies: 576
cycles inside the decoder where the match's source is after the ring's
start, 612 where it is before that start, and 750 where it crosses that
start. The 36 and the 174 above the first are the ring's size added to the
source (ST4's `source_wrapped`) and, at the crossing, the copy cut into two
segments. At a ring of two periods a match's source lies on either side of
the ring's start about as often, so each tune reads all three. A refill
at unit 2 is 334.

The 200 to 280 is a least-squares fit of the decoder's cycles against the
operations parsed, over every refill of a tune: 203 on Turrican 2 - world
completed 1 and 281 on Retrigger retune, built. The endpoints of a tune
whose heaviest refill parses ten operations or more read lower, 209 to
236: the fit counts every refill, and a refill that parses few operations
is mostly the fixed part. On Turrican - world 4-3 the 576 above and 3,748
at its heaviest are 3,172 over fifteen operations, about 211 each. A
refill of 454 outside and the heaviest 3,748 inside are 4,202, the most
Turrican's advance costs a frame; its costliest call is another frame,
whose advance is the 4,136 of the table above.

Unit 2 packs a table larger and costs less to decode: Turrican - world 4-3
packs to 3,600 bytes at `-k2` against 3,332, its advance reads 779 on
average and 3,716 in the costliest frame against 929 and 4,136, and the
play call 1,497 on average and 4,362 at most against 1,647 and 4,838.
`-k2` packs at unit 2.

The frame procedure is the rest, from 502 to 1,092 cycles on average: the
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
within a few hundred cycles of it: the column and the period whose thirty
units packed as the most operations. A table that repeats puts each live
column's decoder back to what it was at the loop's first row at that
column's next refill, one column a turn over the period after the pass's
end, so each row pays for one decoder's copy at most.

R4.5 budgets 6,656 cycles a frame. Every frame of every tune is within it:
the averages by more than two thirds of it, and the costliest frame of
every tune by 594 cycles or more, capture's 6,062 the nearest.
The ticks of a frame come on top of the call, and A tick below counts them.

Release 0.4.22 packed every column at unit 2 through a ring of 960 bytes.
Its play call, counted the same way on its player, against the table above:

| tune | on average, 0.4.22 | on average | at most, 0.4.22 | at most |
|---|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | 1292 | 1154 | 1924 | 3292 |
| Chambers of Shaolin 5 - you blew it! | 1367 | 1254 | 3258 | 4314 |
| Circus Attractions 2 | 1446 | 1700 | 1648 | 1902 |
| DBA 2 | 1674 | 1605 | 4272 | 5484 |
| DBA 5 | 1754 | 1707 | 3606 | 5190 |
| Digidrum preempt, built | 1627 | 1787 | 2762 | 4216 |
| Retrigger retune, built | 1490 | 1522 | 2220 | 2904 |
| Synergy Credits | 2030 | 2201 | 5094 | 5898 |
| Turrican - world 4-3 | 1606 | 1647 | 4532 | 4838 |
| Turrican 2 - world completed 1 | 1606 | 1527 | 4060 | 5394 |
| capture | 1414 | 1384 | 3422 | 6062 |

The call costs less on average on six of the eleven and more on five, and
its costliest frame is higher on every one, by 254 to 2,640 cycles: a
refill is thirty units where it was fifteen, and parses at most an
operation a unit. Under 0.4.22 the nearest frame to the budget was 1,562
cycles under it.

A tune needs its bound tune (BINARIES.md 1) and a state block in the
workspace, the rings among them. The tools of 0.4.22 and of this release
write these, in bytes:

| tune | bound tune, 0.4.22 | state block, 0.4.22 | bound tune | state block | the two against 0.4.22 |
|---|---|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | 3448 | 30312 | 1952 | 1800 | -89% |
| Chambers of Shaolin 5 - you blew it! | 3904 | 30312 | 2328 | 1800 | -88% |
| Circus Attractions 2 | 3292 | 30312 | 1880 | 1416 | -90% |
| DBA 2 | 9740 | 31272 | 11920 | 5560 | -57% |
| DBA 5 | 14932 | 31272 | 19028 | 5560 | -47% |
| Digidrum preempt, built | 4096 | 30312 | 3300 | 3060 | -82% |
| Retrigger retune, built | 3952 | 30312 | 3516 | 2640 | -82% |
| Synergy Credits | 9600 | 31272 | 12716 | 4520 | -58% |
| Turrican - world 4-3 | 5084 | 31272 | 5492 | 2500 | -78% |
| Turrican 2 - world completed 1 | 4084 | 30312 | 2656 | 1848 | -87% |
| capture | 4328 | 30312 | 3036 | 1940 | -86% |

The state block was a ring of 960 bytes for each of the thirty columns,
and is now a ring of 60 to 240 for each live column, the ring of tools.md
4.2 whose state block and table come to the least. A smaller ring reaches
fewer rows back to copy from, and four bound tunes grow by up to 4,096
bytes: DBA 5's is 13,904 bytes at `-m960` and 19,028 at the 240 the writer
chooses, against a state block of 17,800 and 5,560. The two together are
47 to 90 per cent smaller on every tune.

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
the call's first register write is at least 752 cycles after the VBL.
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
| Synergy Credits | YMXR | 2098 | 3960 | 5560 |
| Turrican - world 4-3 | YMX 0.10.1 | 1897 | 3360 | 4284 |
| Turrican - world 4-3 | YMXR | 1696 | 3988 | 5024 |

These figures and the rig's are two samples: the rig counts every frame
of the tune in 68000 cycles with no wait state, and these are the first
2,020 calls on a machine that stalls the processor while the shifter
fetches. Synergy Credits' costliest frame is its 3,111th, past the end of
this run.

YMXR costs a ninth less on average on Turrican - world 4-3 and a tenth less
on Synergy Credits, and at their worst 17 per cent more on Turrican - world
4-3 and 18 per cent more on Synergy Credits, and the figures have two
causes. The frame procedure here tests the fourteen register columns and
writes the ones they admit, reads the effects' columns and enters and leaves
the call, where YMX writes its fourteen registers unconditionally, one
`movep` each, and its whole call with no decode in it is 908, the writes
included. Fourteen tests and a few writes cost what fourteen writes cost,
which YMX's measurement found and its design follows; the schema adds the
effects' columns and the entry. So YMXR's frame procedure costs less on the
rows that set few columns, 718 on average on that tune, the rig's call there
less its advance, against YMX's 908, and its average is the lower.

The refill is the second cause. YMXR refills thirty units of a byte on the
turns that refill a live column, seventeen in thirty on Turrican - world
4-3, and runs an idle turn on the rest; YMX serves a round-robin of
twenty-four slots, twenty-one of them a live stream, so its group is
twenty-four bytes, twelve units at unit 2, and it refills on twenty-one
rows in twenty-four: the three slots without a stream refill no column and
cost 1,012 cycles a call over 252 of the 2,019 calls on Turrican - world
4-3, against 2,023 on the 1,767 calls that refill. YMX's refill is the
smaller of the two, twelve units against thirty, which is why its worst
call is the lower. A refill parses at most one operation a unit, so its
unit count bounds the parse: YMX's worst frame parses twelve operations of
twelve, and Turrican - world 4-3's heaviest refill here fifteen of thirty.
On Synergy Credits the run ends before YMXR's costliest frame, so its 5,560
at most is not that frame, which the rig counts at 5,898. YMX pads this
tune to unit 2 by duplicating a frame. YMXR packs it at unit 1, and at
`-k2` pads it to unit 2 by an unset row (SPEC.md 6, rule 6): the refill is
fifteen units, and a run of the same length reads 4,512 at most.

YMX alone writes its register columns unconditionally, dense, with the
effects behind one bit. Counted by
the rig against the player as it is, each register written as the player
writes a set column, in 36 cycles, and the effects' steps behind a test of
22, that costs 4 to 181 cycles a frame on nine of the eleven fixtures and
saves 6 to 32 on the other two, for rewriting 1.1 to 1.7 and redefining
five bits against R6.2. YMX's finding that a test costs what a write costs
applies to a 30-cycle test and not to the 22 of a test that forms the select
only where it writes.

---

## A tick

| tick | cycles |
|---|---|
| a row written, the place stepped | 96 |
| the marker, the place to row `RR` | 114 |
| the marker, the timer stopped | 124 |
| a square's two rows, no place stepped | 88 |
| a source of one row, no place stepped | 64 |
| a row written, the tune running one effect | 88 |
| the marker to row `RR`, one effect | 114 |
| the marker and the stop, one effect | 124 |
| a square's two rows, one effect | 80 |
| a source of one row, one effect | 56 |

A tick reads its row through the program counter by default; the section
below measures an absolute address instead.
With the interrupt's entry and its `rte`, a tick is 160 cycles: 12% of an
8 MHz 68000 at a digidrum's 6,000 ticks a second, and 51% at 25,600.

A source of two rows repeating to row 0 runs a separate handler
(68k/YMXR.S, SQUARE), which encodes both rows as immediates and moves
between them by their difference: 88 cycles, 152 with the entry and the
`rte`, against the 160 and 178 the two paths of the general handler cost. A
tune whose effects are all such sources ticks 29.4 times a frame on Synergy
Credits, 24.8 on DBA 2 and 20.2 on DBA 5, so 500, 422 and 343 cycles a
frame come off those tunes, against a play call of 2,201, 1,605 and 1,707
(the table above).

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
| a square's two rows, no place stepped | 88 | 56 |
| a source of one row, no place stepped | 64 | 32 |

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
15,022 writes in all, the two registers a timer drives among them; 4 to 26
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
(68k/YMXR.S, ONEROW). A source of one row is the marker alone (SPEC.md 3.2)
and its place is at row 0, so the handler encodes that row as an
immediate, writes it, and moves no place: 56 cycles against the 114 the
general handler's marker path costs, and 64 against 114 where the tune runs
more than one effect. The kit's `retune` ticks 7.7 times a frame, a 383 Hz
buzzer, so 445 cycles a frame come off it against a play call of 1,522.
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
cell, eight bytes each in the workspace, so a start reads two longs where
it walked the tune's index and read the source's header: 48 cycles against
100. That is 38 a frame on Synergy Credits, 22 on DBA 2 and 21 on DBA 5, and
60 to 94 off their costliest frames. The source column's seven bits reach
127, so the room is sized for 127 sources. A second table of the same
room is beside it, where a source of several columns has its loop cell
and the stride from one column to the next, and an effect's record is 16
bytes where it was 8, so YMXR_FIXED is 2,120 bytes against 56.

## A counted tick of two columns

`setEnvelope` writes R11 and R12, and both read every bit of their byte,
so a source on that target is counted: its rows are whole bytes and a
tick counts them (SPEC.md 2.1.3). The wide handler runs that source with
a word counter where the marked one tests bit 7, 128 bytes an instance
against 112, and four instances of it are beside the two marked shapes.

Both tunes of the kit that sweep the envelope period run one effect, so
their ticks drop no level and the figures below are the second column's
of the lean table. The last two columns are the player whose ticks read a
row through an absolute address (A tick through an absolute address):

| tick | marked | counted | marked, absolute | counted, absolute |
|---|---|---|---|---|
| a row written, the places stepped | 152 | 172 | 176 | 196 |
| the end, the places to row `RR` | 178 | 218 | 206 | 246 |
| the end, the timer stopped | 160 | 180 | 172 | 192 |

So 20 cycles a tick that writes a row, as the counted handler of one
column costs against the general one, and 40 on the path that loops,
where the counter is reloaded too. The counted handler reaches
the half of the envelope period above a marked source's range, and plays
the last row as written: the tick that reads the last row of a marked
source on this target
writes the marker's bit into R12 with the value, a period 32,768 more than
the row's, which the kit's `envelope` tune does on its first source's last
two rows, 800 and then 33,696, its 928 with the marker.

The player is 8,318 bytes, 896 of them the counted shapes' eight
instances, four of one column and four of two; where a tick reads an
absolute address, 7,962 bytes and 952, instances of 138 bytes against the
marked shape's 122.

## A tick through an absolute address

`YMXR_PCREL=0` assembles a player whose tick reads its row through an
absolute address rather than through a signed word displacement from the
instruction that reads it (68k/YMXR.S; BINARIES.md 5.5), for a host that
places a tune further off than a displacement reaches. The read costs 20
cycles where a displacement costs 16; the step that moves the place is an
`addq.l` on a long address, 28 against the 20 an `addq.w` on a
displacement costs; and the loop's test and its reload move the same way,
20 against 16 and 36 against 28. A square's handler and a one-row
source's write a value the start patched into them, so both cost the same
in either build:

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

The lean tick of the table above leaves the same two writes out of these
paths, the level's drop on the three that write a row's value and the end
of interrupt on all five:

| tick | as it is | lean |
|---|---|---|
| a row written, the place stepped | 108 | 76 |
| the marker, the place to row `RR` | 130 | 114 |
| the marker, the timer stopped | 132 | 116 |
| a square's two rows, no place stepped | 88 | 56 |
| a source of one row, no place stepped | 64 | 32 |

So the player as it is saves 12 cycles a tick that writes a row and
steps its place, 16 on the loop and 8 on the stop: at a digidrum's 6,000
ticks a second, 1,440 cycles a frame. A handler of two columns saves
twice that a tick and one of three three times, since each column is a
read and a step; the counted handler saves the same 12 as the general
one.

The raster monitor reads this build as it reads the others, and the bar
it burns for the ticks is the figure that moves. Under Hatari over 420
calls of Turrican - world 4-3 on a `-perf-abs` core, against the same run
on the core the tools choose: the call costs 1,633 cycles on average
against 1,634, and a tick 156 against 144, so the bar is 2.47 lines on
average and 32.00 at most against 2.23 and 28.87. `ym/cost.py` reads the
colour writes back.

A start writes the place it patches behind level 7, so that a tick reads
a handler once it is made, and in the player as it is the arithmetic
that turns a row's address into a displacement is in that window as
well: a dozen instructions this build leaves out, which a tick of a timer
due inside it waits for.

The displacement is a signed word, so every row of every source is
within 32,767 bytes of the handlers. Init measures each source against
them and reports -1 for one further off (BINARIES.md 1.5), and the
layouts put a tune's DTX1 tables before its image and a set's subtunes
before the images, so the rows of the tune a host loads are beside the
player at any length of image. A host that places them further off
assembles this build, which is 7,962 bytes against 8,318. Two figures
move against each other: a place is a word where it was a long, which
shortens each shape a tick runs, and a start does the arithmetic that
turns a row's address into a displacement, which is longer than the
shapes save. Over a timer's block that is 46 bytes, and the init and the
routines the four timers share, the reach check among them, are 172 more.

What the two cores do to a tune, under Hatari over 900 frames of Turrican -
world 4-3, counted from the frame the player first writes in: both write
12,354 values, the same value to the same register in the same order, and
the two cores of 0.4.22 write the same. A write lands at another cycle: the
arithmetic a start does to turn a row's address into a displacement, and a
tick of another length returning inside the call at another point, move it,
and the median write of the absolute core's run is 1,188 cycles earlier
against the VBL. `ym/writes.py` reads the two traces back.

The rig reads these figures back: `-cycles` and `-cycles -abs` its counts,
`-clock` the program's clock and `-cost` the comparison with YMX under
Hatari; `-hatari` plays the same tunes on a cycle-exact machine, where the
MFP fires the ticks.
