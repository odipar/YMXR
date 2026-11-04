# The linker

A tool links a player for one song out of blocks, the blocks the song
runs, where a release ships 32 cores today and a tool picks one by the
song's effects byte (issue #329). This document defines the blocks, the
library they come in, the link, and how a linked player is checked.

Status: a design in progress. The sections below are written as each part
is decided; the plan at the end lists what is decided and what is open.

## The player today

The player is one assembly, `68k/YMXR.S`, and each core has every handler
kind for all four effects. Its regions, from rmac's listing of the plain
player and of the player with the lookup in:

| region | plain | with the lookup |
|---|---|---|
| the jump table | 12 | 12 |
| init | 1,384 | 2,198 |
| `YMXR_stop` and `YMXR_play` | 208 | 208 |
| the frame | 2,776 | 3,712 |
| the frame's read-site tables | 164 | 204 |
| the general handler, four effects | 320 | 320 |
| the square's | 112 | 112 |
| the one-row source's | 80 | 80 |
| the counted handler of one column | 384 | 384 |
| the handler of two columns | 448 | 448 |
| the handler of three columns | 560 | 560 |
| the counted handler of two columns | 512 | 512 |
| the place's reset of several columns | 216 | 116 |
| the start of several columns | 790 | 790 |
| `ymxr_rem` | 64 | 64 |
| the timers' table and the parked place | 122 | 122 |
| the lookup's routines | | 3,222 |
| the lookup instances, eight kinds | | 4,188 |
| the player | 8,152 | 17,252 |

The handlers are 2,416 bytes of the plain player. A song whose starts are
general and square sources runs 432 of them.

## Plan

1. The design: the blocks, their interface, the library's format and the
   link, with a decision on each open point of issue #329.
2. The library: the blocks cut out of one assembly of the player, each with
   its references to other blocks and its patch sites.
3. The linker, in the Java tree: the blocks a song runs, read from its
   rows, laid out, their references resolved, and a report of the bytes of
   each song against the core a tool picks today.
4. The check: the rig plays the player linked for each fixture against the
   model.
