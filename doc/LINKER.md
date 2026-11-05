# The linker

A tool links a player for one song out of blocks, the blocks the song
runs, where a release ships 32 cores today and a tool picks one by the
song's effects byte (issue #329). This document defines the blocks, the
library they come in, the link, and how a linked player is checked.

Status: in stages. A player linked from any of the 32 cores leaves out
the frame code and the handlers of each effect the song does not run,
each handler of an effect whose rows start no source of the handler's
kind, and each lookup instance of an effect that maps no source of the
instance's kind, in both banks of a core with the deferred start. The
plan at the end lists what is done and what follows.

## The player today

The player is one assembly, `68k/YMXR.S`, and each core has every handler
kind for all four effects. Its regions, from rmac's listing of the plain
player and of the player with the lookup in:

| region | plain | with the lookup |
|---|---|---|
| the jump table | 12 | 12 |
| init | 1,462 | 2,426 |
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
| the start of several columns | 772 | 772 |
| `ymxr_rem` | 64 | 64 |
| the mask words | 14 | 30 |
| the tables of the instances of the general handler, the square and the one-row source | | 24 |
| the timers' table, the tables of instances and the parked place | 154 | 154 |
| the lookup's routines | | 3,308 |
| the lookup instances and the gaps before them, eight kinds | | 4,188 |
| the player | 8,258 | 17,634 |

The handlers are 2,416 bytes of the plain player. A song whose sources are
general and square sources runs 432 of them.

## Blocks

A block is the code and data a player runs for one part of a song:

- an effect's block, `effect0` to `effect3`, is its frame code, its
  general handler and, in a core with the deferred start, its
  installer, which a song needs where its effects byte sets the
  effect's bit, bits 0 to 3, in a tune of the song;
- a handler's block, such as `square1`, is one handler of one kind on
  one effect, which a song needs where the effect's rows start a source
  of that kind: its source column with bit 7 set and the source's number
  (SPEC.md 1.8.3);
- a kind's block, such as `square`, is init's loop over the kind's
  handlers, which a song needs where it needs a handler of the kind;
- `several` is the start of a source of several columns and the routines
  it calls, which a song needs where a start is of a counted source or of
  one of several columns;
- a lookup instance's block, such as `lt1`, is one instance of one lookup
  kind on one effect and the gap before it, which a song needs where the
  effect maps a source of that kind through a transformation: a start of
  the source with the effect's mapping not 0, or a remap of the running
  source to a mapping not 0 (SPEC.md 1.10);
- a lookup kind's block, such as `lt`, is init's loop over the kind's
  instances, which a song needs where it needs an instance of the kind;
- `align`, in a core with the deferred start and the lookup, is the gap
  before the second bank, which every song needs.

| kind | a handler | a source of the kind |
|---|---|---|
| `square` | 28 | one column, two rows, repeating to row 0 |
| `one` | 20 | one column, one row, repeating to row 0 |
| `counted` | 96 | counted, one column |
| `two` | 112 | marked, two columns |
| `three` | 140 | marked, three columns |
| `envelope` | 128 | counted, two or three columns |

Sizes are in bytes, of the plain core. An effect's block is its frame
code, 578 to 598 bytes, and its general handler, 80; `several` is 988
bytes of routines and 120 of tables.

| lookup kind | an instance | init's loop | a source of the kind, mapped to a target |
|---|---|---|---|
| `lk` | 70 | 82 | one column, of the general handler |
| `lc` | 86 | 96 | counted, one column |
| `lt` | 102 | 96 | marked, two columns, below `setNoiseA` |
| `ln` | 102 | 96 | marked, two columns, `setNoiseA` or past it |
| `lb` | 130 | 114 | marked, three columns, `setBuzzer` |
| `lv` | 130 | 114 | marked, three columns, another target |
| `le` | 118 | 114 | counted, two or three columns, below `setNoiseA` |
| `lf` | 118 | 114 | counted, two or three columns, `setNoiseA` or past it |

Sizes are in bytes, of the core with the lookup in and without the
deferred start. A gap is 0 to 255 bytes. A square and a source of one
row keep their entries in their handler, and run no lookup instance.

A song's lookup instances are read from its rows as a player reads
them: each effect's target, its mapping and the sources it runs, row by
row, and past the last row from the repeat row again until the four
effects' state at the repeat row is one they have had there before. A
deferred start on a running timer adds its source to the ones a remap
reaches.

The rest is in every linked player: the jump table, init, `YMXR_stop`,
`YMXR_play`, the frame's register steps and its tables, `ymxr_rem`, the
mask words, the timers' table and the parked place, and in a core with
the lookup the lookup's routines and the effects' references. A source
of one column that is none of the shapes above runs the general handler.
The frame reaches an effect's handler of a kind on a start on that
effect of a source of that kind alone. Init writes the branch that picks
a start's shape from the shapes of every source of the tune, and a start
of a source of several columns or a counted source calls the routines of
`several`. A path to a block the song does not need is a path no row of
the song runs.

### Pieces

A block is one or more pieces: runs of bytes of the core, each between
the labels `ymxr_p_<piece>` and `ymxr_e_<piece>`, which the macros
`PIECE` and `ENDPIECE` of `68k/player/pieces.S` define. A piece is named
for its block and numbered within it: `square_1` is init's loop over the
squares, `square_2` the four squares. Neither macro writes a byte, so the
core a release ships is the same with the pieces marked.

Init links each handler kind in a loop over its four handlers, and each
loop is a piece of the kind's block. The handlers of one kind are one
after the other, all of one size, so the handlers a player keeps are too:
the loop walks the four effects, and links a handler and steps past it
where the effect's bit in the kind's mask, `ymxr_k_square` for the
square, is set. The start of several columns and the deferred start read
an effect's handler out of a table of the kind's four, `ymxr_at_two` for
two columns, each entry the handler's distance from the table, which the
link moves as it leaves handlers out. The level drop of a tick that nests
is written in the loop of each kind it is in: the general handler, the
square and the one-row source. The word is written into all four
effects' handlers, since a timer the tune does not run is left
unclaimed and its handlers do not run.

Each effect's frame code and its general handler are pieces of the
effect's block, and an effect's frame code falls through to the next one
the player has.

A core with the deferred start has a second bank of handlers, BANK bytes
past the first, the same handlers in the same order (deferred.S). Each
handler of the second bank is a piece of its twin's block, marked by
`PIECE2`, which names the twin: a link leaves both out or keeps both,
so the banks keep one layout, and BANK, a difference of two labels,
moves by the bytes the first bank loses. Each effect's installer is a
piece of the effect's block. Init writes each effect's
read sites, the branch at its head and its place's reset out of three
tables, which list all four effects. The word `ymxr_k_effect` has a bit
an effect the player has, all four in a core as carried, and init
leaves the entries of an effect whose bit is clear as they are
(`ymxr_kept` in `68k/player/init.S`). The branch at the head of an
effect a tune does not run reaches the next effect the tune runs, or
the register steps past the last.

A lookup instance reads its pages at an offset modulo 256 that its
effect's reference sets (BINARIES.md 5.6). Each instance is two pieces
of its block: the gap before it, `lt0_1`, zero bytes that put the
instance's page read on that offset, and the instance, `lt0_2`. A link
keeps the gap of an instance it keeps at the length that puts the page
read on the offset in the linked player, and can pick each effect's
reference: init and the page routines read the four out of `ymxr_refat`,
a word an effect past `ymxr_ref`. Init links each lookup kind in a loop
over its four instances, a piece of the kind's block: it reads each
instance's distance from the first handler out of the kind's descriptor
in `ymxr_kinds`, a word an effect, which the link moves, links the
instance where the effect's bit in the kind's mask, `ymxr_k_lt` for two
columns, is set, and computes the page byte of the instance's page 0
from the instance's address and its effect's reference. A start and a
remap read the instance out of the same descriptor, and a square's or a
one-row source's out of `ymxr_at_square` and `ymxr_at_one`. In a core
with the deferred start, each gap and each instance of the second bank
is a twin of the first bank's, and `align_1`, the gap before the second
bank, puts its first handler on the offset modulo 256 of the first
bank's: BANK is a multiple of 256, and the two banks read their pages at
one offset.

## The library

The library of a core is the core as carried and a table of its pieces
and fields, `<core>.pieces` beside it in `go/binaries/data`. The build
cuts it from the assembly of each of the 32 cores as it assembles them.

The table is text, a line each:

```
piece square_1 1172 1208
piece square0_1 5626 5654
mask 8776 square
twin square0_2 square0_1
align lt0_1 28 13850 0
field 5194 2 effect0_1+1 effect1_1+1 effect2_1+1
```

A `piece` line is the piece's name, its first byte and the byte past its
last, offsets from the core's first byte, in the core's order. A `mask`
line is the offset of a word at the label `ymxr_k_<prefix>` and the
prefix: a link writes the word with bit i set where it keeps the block
`<prefix>i`. A `twin` line is a piece of the second bank and the piece
of the first it copies. An `align` line is a gap, a piece whose length a
link sets, and three numbers B, A and N: the byte B past the gap's end
is N bytes past the byte at offset A, modulo 256, in the core and in a
linked core. For a lookup instance's gap B is the distance from the
gap's end to the base of the instance's page read, A the offset of
`ymxr_ref` and N its effect's reference past it, and a fifth number W is
the offset of the word of `ymxr_refat` that is N; for `align_1` B is 0,
A the offset of the first handler and N 0. A `field` line is a value of
the core that moves where a piece is left out: its offset, its width in
bytes, a word or a byte, and for each piece whose end it refers across,
the piece's name and the coefficient c. The value is signed, and a link
that leaves out a piece of L bytes moves it by -c times L: a
displacement through the program counter, a branch, or a difference of
two labels in a table.

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

A value the source means as a size, such as `TW2_SIZE`, the bytes of a
handler of two columns, is a difference of two labels in one piece: one
measured from the first handler to the second would move when the first
is left out, as a distance does. The sizes in `68k/player/handlers.S` are
measured to the end of the first handler's piece.

A piece of the second bank is padded with its twin, both at once, since
a pad past one alone breaks the layout the assembly checks: the field
moves by c times 2 for the two together, and the table records c under
the twin of the first bank. A link leaves the two out together, and
the two are one length, so a field moves by c times that length.

A core with the lookup is padded 256 and 512 bytes, which leave each
gap's length as it is: a field moves by c times 256 and c times 512, and
is a word at an even offset, its high byte the byte that moves. A field
of a core past 32 KB is read modulo 2^16: a pad moves its value past the
range of a signed word, and a link that leaves pieces out moves it back.
A byte field across a pad of 256 would move by 0: the source has no byte
that is a difference of two labels, and a branch of a byte's
displacement across the pad is out of its reach, an error of the
assembly or of the cut.

The cut fails where a padded core is not P bytes longer, which a branch
whose size rmac picks by its distance makes, or where a byte moves that
no field of the two pads explains. The source then names the branch's
size. The 32 tables of this version have word fields alone.

## The link

The link reads the core of the song's switches, its table and the blocks
the song needs. It writes the core's bytes in order with every piece of a
block the song does not need left out and each gap it keeps at the
length its align line sets, moves each field in the bytes it keeps by -c
times the bytes each piece it refers across loses, and writes each mask
with the blocks it keeps.

The link sets the gaps in the core's order: a gap's length is the
distance, modulo 256, from the gap's first byte in the linked core to
the byte its align line names there, less B, where the linked offset of
a byte is its offset less the bytes each piece before it loses. A gap
loses its length in the core less its length linked, which can be less
than 0. A piece of the second bank loses what its twin loses, and the
link fails where it does not.

The link sets the gaps twice, and keeps the setting with fewer bytes,
the first on a tie:

1. on each effect's reference as the core has it, N of each align line;
2. on each effect's reference picked at the first gap of a W the link
   keeps, in the core's order: the N that gap is 0 bytes for, or the
   first 2 bytes on from it, modulo 256, that no reference picked
   before is on. A W of no gap the link keeps is the core's N, or the
   first 2 bytes on from it that no reference picked is on.

The link writes each W with the N of the setting it keeps. The four
references are on four offsets modulo 256, as the page area places the
effects' pages by them (BINARIES.md 5.6).

Pieces leave and none moves, so a field refers to the same label it did,
or for a label in a piece left out, to the byte that follows the piece.
The link fails where a value no longer fits its field. A field in a piece
left out is left out with it.

The descriptor of BINARIES.md 2.2 is in the first 40 bytes, before every
piece, and a linked core is read as the core it was linked from: the
flags word is the core's, and init measures the reach from the general
handler to the last handler kept. In a core with the lookup the word at
26, the first handler's offset, is a field, which the link moves. The
page area follows the linked core (BINARIES.md 3.1), and a writer
measures it against the linked core.

## Figures

The blocks of its core each tune of the conformance kit keeps, the core
it links, and the bytes of that core linked for it: the plain core, of
8,942 bytes, the core with the deferred start in, of 13,322 bytes, or
the core with the lookup and the deferred start in, of 28,418 bytes.

| tune | the core | the blocks kept | bytes |
|---|---|---|---|
| `chambers.ymxr` | plain | none | 2,676 |
| `circus.ymxr` | plain | none | 2,676 |
| `counted.ymxr` | plain | square, square1, counted, counted0, counted1, counted2, counted3, several, effect0, effect1, effect2, effect3 | 6,996 |
| `deferred.ymxr` | the deferred start | effect0, effect3 | 5,396 |
| `envelope-counted.ymxr` | plain | envelope, envelope0, several, effect0 | 4,654 |
| `envelope.ymxr` | plain | two, two0, several, effect0 | 4,620 |
| `fine-zero.ymxr` | plain | none | 2,676 |
| `four-timers.ymxr` | plain | square, square0, one, one2, effect0, effect1, effect2, effect3 | 5,458 |
| `mapped.ymxr` | the lookup and the deferred start | square, square2, counted, counted1, two, two3, several, effect0, effect1, effect2, effect3, lk, lk0, lk2, lc, lc1, lt, lt3, align | 15,370 |
| `plays-once.ymxr` | plain | none | 2,676 |
| `preempt.ymxr` | plain | square, square0, effect0, effect1 | 4,074 |
| `retune.ymxr` | plain | one, one0, effect0 | 3,382 |
| `synergy.ymxr` | plain | square, square0, square1, effect0, effect1 | 4,102 |
| `turrican-2.ymxr` | plain | effect1 | 3,352 |
| `turrican.ymxr` | plain | effect1 | 3,352 |
| `voices.ymxr` | plain | two, two1, two3, three, three0, three2, several, effect0, effect1, effect2, effect3 | 7,108 |

A tune of general sources alone, with no effect, links 2,676 bytes, 30%
of the core: the blocks are 6,266 bytes of it. The kit's largest,
`voices.ymxr`, runs all four effects, keeps a handler of two or three
columns on each and the start of several columns, and links 7,108.
`mapped.ymxr` keeps 4 lookup instances of 3 kinds and links 15,370
bytes of 28,418: the 28 instances and the 5 loops of init it leaves out
are 8,128 bytes, and the gaps it keeps are 740 bytes shorter than in the
core.

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
  assembled alone. The 16 tunes of the kit play their whole length as
  the specification reads on linked players of the plain core, `-lean`,
  `-abs -perf`, `-lean -abs` and `-perf -lean`, and the 11 dumps of
  `ym/test` on the plain core. Under `-defer -map` the 20 tunes of the
  check of deferred starts, and under `-map` the 33 tunes of the check of
  tunes that map a value, play every tick against the model on the
  players linked for them, with each of those switches.

## Plan

1. The design: the blocks, the library's format and the link. Done.
2. The library: the pieces marked in the source and the tables cut by
   the build. Done for each effect's frame code, each handler and each
   lookup instance, in the 32 cores.
3. The linker, in the Java and the Go trees: the blocks a set needs,
   read from its sources and its effects, the link, and `ymxr-sndh
   -link`. Done.
4. The check: the rig plays the player linked for each fixture against
   the model. Done.
5. A register's steps as a block, for the registers no tune of a song
   sets, is left out. A dump writes every register on its first frame,
   R11 and R12 through bits 6 and 5 of the shape's column, and of the
   eleven fixtures of `ym/test` one, built by hand, leaves a register
   unset. The test of a register's bit that init needs costs every player
   more bytes than it saves a song that sets every register.
6. The cores with the lookup, whose instances keep their page offset
   modulo 256 through a gap the link sets. Done. The descriptors' words
   an effect, the masks and init's loops make `YMXR_sndh-map.bin` as
   carried 270 bytes larger than with the instances LOOK_STRIDE apart,
   and the instances and their gaps 4,188 bytes, as before.
7. Each effect's reference picked by the link, so the first instance
   of each effect it keeps needs no gap. Done.
8. The stage that follows: `-link` as the tools' default, with
   RigCallsTest's matrix on linked cores.
