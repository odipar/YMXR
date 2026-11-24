# Player sizes

A player linked for a song is the floor, the bytes every song keeps, and
the blocks of what the song runs (LINKER.md). This document lists where
those bytes are, what they cost in cycles, and what makes them fewer for
a few cycles more a call.

## The corpus

The player of each of the 544 dumps links from the plain core. The
floor, 1,336 bytes, is 86% of the 842,506 bytes of the 544 players. The
link keeps one of four sets of blocks:

| blocks kept | dumps | bytes |
|---|---|---|
| none | 461 | 1,336 |
| effect1, effects | 69 | 2,682 |
| square, square0, effect0, effects | 12 | 2,852 |
| square, square0, square1, effect0, effect1, effects | 2 | 3,664 |

Before the block of the effects (LINKER.md, plan item 14) and one copy
of steps 6 and 7, the floor was 2,752 bytes and the 544 players were
1,543,122 bytes: they are 45% fewer now. The corpus is the one of
experiments.md, and `YM_CORPUS` is its folder.

## The floor

The floor of the plain core is the SNDH core's entries, init and stop
but for their work for the effects, and the play call's frame procedure.
Init and stop run once a song, outside the play call R4.5 budgets and
outside the ticks.

## The work for the effects

Each effect's timer code is pieces of the effect's block, `effect0` to
`effect3`, and the rest of init's and stop's work for the effects is
pieces of `effects`, the block a link keeps for a song that runs an
effect:

| work | piece | bytes |
|---|---|---|
| the SNDH core's claim of the timer | `effectN_6` | 10 |
| the SNDH core's put-back of the timer | `effectN_7` | 68 |
| init's claim of the timer | `effectN_8` | 40 |
| stop's release of the timer | `effectN_9` | 32 |
| the loop that resolves the sources | `effects_1` | 162 |
| the check of a source's reach | `effects_2` | 76 |
| the level's drop or its nops, and the links of the general handler | `effects_3` | 64 |
| the second bank's pass and the effects' records cleared | `effects_4` | 42 |
| the shape tests and the effects' heads | `effects_5` | 166 |
| the routine that links a general handler | `effects_6` | 48 |
| the tables of the heads and the resets | `effects_7` | 32 |
| `ymxr_rem` | `effects_8` | 64 |
| the timers' table | `effects_9` | 32 |

The timer code of an effect is 150 bytes, and the block of the effects
686 bytes. The pieces leave every instruction of the play call and the
ticks as it is.

## One copy of steps 6 and 7

The frame writes registers 11, 12, 8 to 10 and 7 in one copy, for a row
that sets the shape column and for a row that leaves it 0. The player
had a second copy for the row that leaves it 0, without the tests of the
shape's bits, which `68k/test/sizes/two-copies.patch` restores. The
second copy of steps 6 and 7 is 130 bytes more in every player.

Over the first 1,000 frames of each of the eleven dumps the average call
of a dump costs 24 to 40 cycles more than with the second copy and a
single call 22 fewer to 40 more, and every tick costs the same. A row that
leaves the shape column 0 runs the tests the second copy leaves out, and
a row that sets it runs without the branch to that copy. plan.md has the
cost of an average call.

## A few cycles more

Two places have more bytes, each at a cost in cycles the rig has yet to
count:

- Each effect's block, its frame code, general handler and timer code,
  is 642 to 662 bytes of the plain core, a copy an effect. One copy for
  the four, its addresses read through a register, would save those
  bytes for each effect past the first, and cost each step of an effect
  the read through the register.
- `several`, the start of a source of several columns, is 1,416 bytes. A
  start runs it at the frame, and a loop over the columns would cost each
  such start.

A tick handler runs thousands of times a second at a digidrum's rate, so
a cycle there costs the most: the handlers stay as they are.

## The dumps of ym/test

The blocks the link keeps for each dump, its bytes, and its bytes with
the second copy of steps 6 and 7:

| dump | blocks kept | bytes | with the second copy |
|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | none | 1,336 | 1,466 |
| Chambers of Shaolin 5 - you blew it! | none | 1,336 | 1,466 |
| Circus Attractions  2 | none | 1,336 | 1,466 |
| DBA 2 | square, square0, effect0, effects | 2,852 | 2,982 |
| DBA 5 | square, square0, effect0, effects | 2,852 | 2,982 |
| Digidrum preempt, built | square, square0, effect0, effect1, effects | 3,512 | 3,642 |
| Retrigger retune, built | one, one0, effect0, effects | 2,756 | 2,886 |
| Synergy Credits | square, square0, square1, effect0, effect1, effects | 3,664 | 3,794 |
| Turrican - world 4-3 | effect1, effects | 2,682 | 2,812 |
| Turrican 2 - world completed 1 | effect1, effects | 2,682 | 2,812 |
| capture | none | 1,336 | 1,466 |

The floor of the plain core is 1,336 bytes, and 1,466 with the second
copy of steps 6 and 7. The floor of the core with the deferred start in
is 2,126 bytes, of the core with the lookup in 2,548, and of the core
with the lookup and the deferred start in 3,968.

## The cores with the lookup

A song that cuts a source and maps no value links from a core with the
lookup in (LINKER.md). The lookup's routines and tables are the block
`lookup`, which a link keeps where a tune is of version 10 or maps a
value: the start through a transformation, the remap, the page routines,
init's fill of the pages and the routines of the lookup kinds' loops. The
block is 2,672 bytes of the core with the lookup in and 2,992 of the core
with the deferred start in as well, and a song that cuts a source alone
leaves it out: `cut.ymxr` links 7,336 bytes, against 10,008 with the
block. The table of the kinds' descriptors, `ymxr_kindof` and
`ymxr_runrow` stay in the floor, as a start and a recut under a cut read
them.

## The check

- SizesTest reads the bytes above off the cores and tables under
  `go/binaries/data`, and off the patch applied to a copy of `68k`, with
  its table cut as the build cuts it. A row of the corpus's table is read
  against the dump of `ym/test` that keeps the same blocks, and its count
  of dumps against the corpus where `YM_CORPUS` is set.
- The rig's `-onlysizes` plays the eleven dumps on the player as it is
  and on the player of the patch, each against the model, and reads the
  cycles above.
