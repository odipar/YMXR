# Player sizes

A player linked for a song is the floor, the bytes every song keeps, and
the blocks of what the song runs (LINKER.md). This document lists where
those bytes are and what makes them fewer: at the cycles every call and
every tick costs today, and for a few cycles more a call.

## The corpus

The player of each of the 544 dumps links from the plain core. The
floor, 2,752 bytes, is 97% of the 1,543,122 bytes of the 544 players. The
link keeps one of four sets of blocks, and the last column is the link
with the pieces below:

| blocks kept | dumps | bytes | with the pieces |
|---|---|---|---|
| none | 461 | 2,752 | 1,466 |
| effect1 | 69 | 3,262 | 2,816 |
| square, square0, effect0 | 12 | 3,432 | 2,986 |
| square, square0, square1, effect0, effect1 | 2 | 4,094 | 3,798 |

A smaller player for the corpus is a smaller floor. The corpus is the one
of experiments.md, and `YM_CORPUS` is its folder.

## The floor

The floor of the plain core is the SNDH wrapper (BINARIES.md 4), init,
stop and the play call's frame procedure. Init and stop run once a song,
outside the play call R4.5 budgets and outside the ticks.

Init and stop do work for the effects: each effect's timer claimed at
init, kept and put back by the wrapper and released at stop, each source
resolved and measured against the handlers' reach, the handlers linked,
the effects' records cleared, and the shape tests and the effects' heads
written. A song that runs no effect runs this work all the same, and a
song that runs one effect runs the timer code of the three others.

## At the same cycles

`68k/test/sizes/pieces.patch` makes that work pieces: an effect's timer
code is a piece of the effect's block, `effect0` to `effect3`, and the
rest are pieces of `effects`, a block the link keeps for a song that runs
an effect:

| work | piece | bytes |
|---|---|---|
| the wrapper's claim of the timer | `effectN_10` | 10 |
| the wrapper's put-back of the timer | `effectN_11` | 68 |
| init's claim of the timer | `effectN_12` | 40 |
| stop's release of the timer | `effectN_13` | 32 |
| the loop that resolves the sources | `effects_1` | 166 |
| the check of a source's reach | `effects_2` | 76 |
| the level's drop or its nops, and the links of the general handler | `effects_3` | 64 |
| the second bank's pass and the effects' records cleared | `effects_4` | 42 |
| the shape tests and the effects' heads | `effects_5` | 166 |
| the routine that links a general handler | `effects_6` | 48 |
| the tables of the heads and the resets | `effects_7` | 32 |
| `ymxr_rem` | `effects_8` | 64 |
| the timers' table | `effects_9` | 32 |

With the pieces the timer code of an effect is 150 bytes, and the block of
the effects 690 bytes. Two short branches past a piece are word branches,
2 bytes more each, so a song that runs all four effects links 4 bytes
more than today.

The patch marks pieces and widens two branches of init and of the
wrapper, and leaves every instruction of the play call and the ticks as
it is. A link leaves pieces out and keeps those instructions, so a linked
player costs the same cycles. Over the first 1,000 frames of each of the
eleven dumps, the rig counts every call and every tick on the player
assembled with the pieces at the cycles of the player as it is.

With the pieces the 544 players are 913,558 bytes, 41% fewer.

## For a few cycles more

The frame writes registers 11, 12, 8 to 10 and 7 in two copies: the first
for a row that sets the shape column, the second, `ymxr_plain`, for a row
that leaves it 0, without the tests of the shape's bits.
`68k/test/sizes/one-copy.patch` keeps the first copy alone. One copy of
steps 6 and 7 is 130 bytes fewer in every player.

Over the same frames the average call of a dump costs 24 to 40 cycles
more and a single call 22 fewer to 40 more, and every tick costs what it
costs today. A row that leaves the shape column 0 runs the tests the
second copy leaves out, and a row that sets it runs without the branch to
that copy. plan.md has the cost of an average call.

Two places have more bytes, each at a cost in cycles the rig has yet to
count:

- Each effect's frame code and general handler are 492 to 512 bytes of
  the plain core, a copy an effect. One copy for the four, its addresses
  read through a register, would save those bytes for each effect past
  the first, and cost each step of an effect the read through the
  register.
- `several`, the start of a source of several columns, is 1,416 bytes. A
  start runs it at the frame, and a loop over the columns would cost each
  such start.

A tick handler runs thousands of times a second at a digidrum's rate, so
a cycle there costs the most: the handlers stay as they are.

## The dumps of ym/test

The blocks the link keeps for each dump, its bytes today, with the pieces
and with one copy of steps 6 and 7 as well:

| dump | blocks kept | bytes | with the pieces | with one copy as well |
|---|---|---|---|---|
| Big - Samantha Fox Strip Poker 6 | none | 2,752 | 1,466 | 1,336 |
| Chambers of Shaolin 5 - you blew it! | none | 2,752 | 1,466 | 1,336 |
| Circus Attractions  2 | none | 2,752 | 1,466 | 1,336 |
| DBA 2 | square, square0, effect0 | 3,432 | 2,986 | 2,856 |
| DBA 5 | square, square0, effect0 | 3,432 | 2,986 | 2,856 |
| Digidrum preempt, built | square, square0, effect0, effect1 | 3,942 | 3,646 | 3,516 |
| Retrigger retune, built | one, one0, effect0 | 3,336 | 2,890 | 2,760 |
| Synergy Credits | square, square0, square1, effect0, effect1 | 4,094 | 3,798 | 3,668 |
| Turrican - world 4-3 | effect1 | 3,262 | 2,816 | 2,686 |
| Turrican 2 - world completed 1 | effect1 | 3,262 | 2,816 | 2,686 |
| capture | none | 2,752 | 1,466 | 1,336 |

The floor of the plain core goes from 2,752 bytes to 1,466 with the
pieces and to 1,336 with one copy of steps 6 and 7 as well. With the
pieces the floor of the core with the deferred start in goes from 3,668
to 2,256, of the core with the lookup in from 6,826 to 5,350, and of the
core with the lookup and the deferred start in from 8,742 to 7,090.

## The cores with the lookup

A song that cuts a source and maps no value links from a core with the
lookup in (LINKER.md), and that core's floor has the lookup's routines
and tables: `ymxr_prefill`, the window tables, `ymxr_rmove` and the page
routines. A block of what a song runs where it maps a value would leave
those out of the player of a song that cuts a source alone. That block is
left to a later change, and the pieces patch leaves them in the floor.

## The check

- SizesTest applies the patches to a copy of `68k`, cuts each core's
  table as the build does, and reads the tables and the bytes above. A
  row of the corpus's table is read against the dump of `ym/test` that
  keeps the same blocks, and its count of dumps against the corpus where
  `YM_CORPUS` is set.
- The rig's `-onlysizes` plays the eleven dumps on the player as it is
  and on the player of each patch, each against the model, and reads the
  cycles above.
