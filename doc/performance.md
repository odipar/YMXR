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
| Chambers of Shaolin 5 - you blew it! | 1020 | 2857 | 8298 | 1717 | 7166 |
| Circus Attractions 2 | 180 | 3040 | 8328 | 1898 | 7190 |
| Digidrum preempt, built | 840 | 2888 | 8342 | 1656 | 7166 |
| Retrigger retune, built | 1200 | 2776 | 8312 | 1623 | 7168 |
| Synergy Credits | 10800 | 3164 | 8520 | 1718 | 6972 |
| Turrican - world 4-3 | 3720 | 2842 | 8328 | 1638 | 7166 |
| Turrican 2 - world completed 1 | 360 | 3012 | 8304 | 1860 | 7166 |

A table packs at a period of thirty rows, the column count, so a refill
is thirty bytes and one comes every row (tools.md, Convert). What those
bytes cost depends on how the column packed: a run of long matches
decodes at 17 cycles a byte and a run of short operations, each a length
and an offset read bit by bit, at 70 or more, which is the spread between
the tunes' averages. A larger period would make the refill larger and
rarer: packed at 283 rows, the only period dividing its 5,377-row loop
before the converter padded it, Synergy Credits refilled 283 bytes at a
time, from 4,792 to 19,742 cycles, and its costliest frame was 20,936.

The frame procedure is the rest: its thirty tests and the writes they
admit, from 1,140 to 1,450 cycles on average, and a start resolving its
source through the index.

The costliest frame of every tune is the same row: the one before a
pass's last period, where a table that repeats puts every column's
decoder back to what it was at the loop's first row, thirty decoders at
170 cycles each, 5,104 in one call, on top of that row's refill.

R4.5 budgets 6,656 cycles a frame. The average is under it by more than
half; that one frame is over it on every tune, by 1,642 to 1,864 cycles,
and what it costs is DTX's.

## Against YMX

YMX's performance.md measures its player by painting the background red
while a call runs and reading the palette writes back from a cycle-exact
Hatari (`ymx/test/cost.py` there). The stub here paints the same way, so
the two players read by one method, on the same dumps, over 2,000 calls:

| tune | player | on average | the 99th call in a hundred | at most |
|---|---|---|---|---|
| Synergy Credits | YMX 0.10.1 | 2317 | 3424 | 3880 |
| Synergy Credits | YMXR | 3388 | 5096 | 7124 |
| Turrican - world 4-3 | YMX 0.10.1 | 1912 | 3232 | 4252 |
| Turrican - world 4-3 | YMXR | 3081 | 4480 | 8584 |

YMXR costs half as much again on average and twice as much at its worst,
and the difference has three parts. The frame procedure is 1,140 to
1,450 cycles: thirty tests, the writes they admit and four effects'
columns, where YMX writes its fourteen registers unconditionally, one
`movep` each, and its whole call with nothing to decode is 908, the
writes included. Thirty tests cost more than fourteen writes, which
YMX's own measurement found and its design took. DTX's advance spends
954 cycles a refill outside the decoder, loading and storing the
decoder's eight registers and keeping its ring, its turn and its
budget, and 700 to 950 inside it for thirty bytes, where a byte costs 12
cycles to copy and each operation the stream holds 175 to 220 to parse;
YMX's refill decodes sixty-four bytes at unit 2 for about 950 in all.
Packed at unit 2 the advance falls by a sixth, no more. And YMX reopens
its streams' sections one a turn across the frames before its loop,
where DTX puts all thirty decoders back in one call, 5,104 cycles.

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
