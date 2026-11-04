# The linker

A tool links a player for one song out of blocks, the blocks the song
runs, where a release ships 32 cores today and a tool picks one by the
song's effects byte (issue #329). This document defines the blocks, the
library they come in, the link, and how a linked player is checked.

Status: the first version. It links a player from the eight cores without
the deferred start and the lookup, and leaves out each handler kind the
song runs no source of. The plan at the end lists what is done and what
follows.

## The player today

The player is one assembly, `68k/YMXR.S`, and each core has every handler
kind for all four effects. Its regions, from rmac's listing of the plain
player and of the player with the lookup in:

| region | plain | with the lookup |
|---|---|---|
| the jump table | 12 | 12 |
| init | 1,346 | 2,166 |
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
| the player | 8,114 | 17,220 |

The handlers are 2,416 bytes of the plain player. A song whose sources are
general and square sources runs 432 of them.

## Blocks

A block is the code and data a player runs for the sources of one kind:
a handler kind's four handlers, one an effect, the loops of init that
link them, and the routines a start of that kind calls. A song needs a
block where one of its sources is of the block's kind, and init resolves
every source of the tune at its first call, so a source the rows never
start counts as well.

| block | its bytes | a source that needs it |
|---|---|---|
| `square` | the square's handlers, and init's loop over them | one column, two rows, repeating to row 0 |
| `one` | the one-row source's handlers, and init's loop over their level drop | one column, one row, repeating to row 0 |
| `counted` | the counted handlers of one column, init's loop over them and its link routine | counted, one column |
| `two` | the handlers of two columns, and init's loop over them | marked, two columns |
| `three` | the handlers of three columns, and init's loop over them | marked, three columns |
| `envelope` | the counted handlers of two columns, and init's loop over them | counted, two or three columns |
| `several` | the start of a source of several columns, the place's reset on its handler, and the table of the targets that write several registers | counted, or two or three columns |

The rest is in every linked player: the jump table, init, `YMXR_stop`,
`YMXR_play`, the frame and its tables, the general handler, `ymxr_rem`,
the timers' table and the parked place. A source of one column that is
none of the shapes above runs the general handler.

The frame reaches a handler kind on the start of a source of that kind
alone. Init writes the branch that picks a start's shape from the shapes
of every source of the tune, and a start of a source of several columns
or a counted source calls the routines of `several`. A path to a block
the song does not need is a path no row of the song runs.

### Pieces

A block is one or more pieces: runs of bytes of the core, each between
the labels `ymxr_p_<piece>` and `ymxr_e_<piece>`, which the macros
`PIECE` and `ENDPIECE` of `68k/player/pieces.S` define. A piece is named
for its block and numbered within it: `square_1` is init's loop over the
squares, `square_2` the four squares. Neither macro writes a byte, so the
core a release ships is the same with the pieces marked.

Init links each handler kind in a loop over its four handlers, and each
loop is a piece of the kind's block. The level drop of a tick that nests
is written in the loop of each kind it is in: the general handler, the
square and the one-row source. The word is written into all four
effects' handlers, since a timer the tune does not run is left
unclaimed and its handlers do not run.

## The library

The library of a core is the core as carried and a table of its pieces
and fields, `<core>.pieces` beside it in `go/binaries/data`. The build
cuts it from the assembly of each core without the deferred start and the
lookup, eight tables, as it assembles the cores.

The table is text, a line each:

```
piece square_1 1130 1158
field 1160 2 counted_2+1 counted_3+1 envelope_1+1 one_2+1 square_2+1 three_1+1 two_1+1
```

A `piece` line is the piece's name, its first byte and the byte past its
last, offsets from the core's first byte, in the core's order. A `field`
line is a value of the core that moves where a piece is left out: its
offset, its width in bytes, a word or a byte, and for each piece whose
end it refers across, the piece's name and the coefficient c. The value
is signed, and a link that leaves out a piece of L bytes moves it by
-c times L: a displacement through the program counter, a branch, or a
difference of two labels in a table.

The build cuts the table with pads. It assembles the core once with the
pieces marked, reads each piece's labels from rmac's listing, and then
assembles the core once for each piece with `-dYMXR_PAD_<piece>=1`, where
`ENDPIECE` writes YMXR_PADSIZE zero bytes past the piece's last byte. A
pad of P moves every label past the piece's end P bytes on, so a value
that refers across the end moves by c times P. Two pads define each field:

- with a pad of 2, every byte that moves belongs to a field;
- with a pad of 256, the high byte of a word field moves, and a byte
  field moves by 0;
- the field moves by c times 2 and by c times 256 exactly.

The cut fails where a padded core is not P bytes longer, which a branch
whose size rmac picks by its distance makes, or where a byte moves that
no field of the two pads explains. The source then names the branch's
size. The eight tables of this version have word fields alone.

## The link

The link reads the core of the song's switches, its table and the blocks
the song needs. It writes the core's bytes in order with every piece of a
block the song does not need left out, and moves each field in the bytes
it keeps by -c times the length of each piece left out whose end the
field refers across.

Pieces leave and none moves, so a field refers to the same label it did,
or for a label in a piece left out, to the byte that follows the piece.
The link fails where a value no longer fits its field. A field in a piece
left out is left out with it.

The descriptor of BINARIES.md 2.2 is in the first 40 bytes, before every
piece, and a linked core is read as the core it was linked from: the
flags word is the core's, and init measures the reach from the general
handler to the last handler kept.

## Figures

The blocks each tune of the conformance kit needs, and the bytes of the
plain core of 8,798 bytes linked for it:

| tune | the blocks kept | bytes |
|---|---|---|
| `chambers.ymxr` | none | 5,264 |
| `circus.ymxr` | none | 5,264 |
| `counted.ymxr` | square, counted, several | 6,968 |
| `deferred.ymxr` | the deferred start: the core as carried | |
| `envelope-counted.ymxr` | envelope, several | 6,946 |
| `envelope.ymxr` | two, several | 6,864 |
| `fine-zero.ymxr` | none | 5,264 |
| `four-timers.ymxr` | square, one | 5,504 |
| `mapped.ymxr` | the deferred start and the lookup: the core as carried | |
| `plays-once.ymxr` | none | 5,264 |
| `preempt.ymxr` | square | 5,404 |
| `retune.ymxr` | one | 5,364 |
| `synergy.ymxr` | square | 5,404 |
| `turrican-2.ymxr` | none | 5,264 |
| `turrican.ymxr` | none | 5,264 |
| `voices.ymxr` | two, three, several | 7,500 |

A tune of general sources alone links 5,264 bytes, 60% of the core: the
blocks are 3,534 bytes of it. The kit's largest, `voices.ymxr`, keeps
the handlers of two and three columns and the start of several columns,
and links 7,500.

## The check

- The build cuts each table in every build, and LinkTest requires the
  table under `go/binaries/data` to be the one the classes carry.
- LinkTest requires a link that keeps every block to be the core byte
  for byte, and reads the figures above.
- ParityTest requires `ymxr-sndh -link` to write the same file and
  report in the Java and the Go trees, for each tune of the kit, each
  switch and a set of two.
- The rig's `-linked` plays each tune on the player `ymxr-sndh -link`
  links for it, cut out of the SNDH file, against the model. It first
  requires the tool's core to be the rig's link of the assembled core,
  its patched longs aside, and the player in the core to be the player
  assembled alone. The 14 tunes of the kit that link play their whole
  length as the specification reads on each of the eight cores the
  library has, and the 11 dumps of `ym/test` their first 24 frames on
  the plain core.

## Plan

1. The design: the blocks, the library's format and the link. Done for
   the handler kinds; the frame is one block.
2. The library: the pieces marked in the source and the tables cut by
   the build. Done.
3. The linker, in the Java and the Go trees: the blocks a set needs,
   read from its sources, the link, and `ymxr-sndh -link`. Done.
4. The check: the rig plays the player linked for each fixture against
   the model. Done.
5. Later: the frame cut into a block an effect, which needs init to read
   the read sites through a table the link writes; the lookup instances
   as blocks, laid out at their page residues; the deferred start's
   second bank; `-link` as the tools' default, with RigCallsTest's matrix
   on linked cores.
