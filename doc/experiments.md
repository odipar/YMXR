# experiments

Ideas measured against a corpus, and what the measurements said.

The corpus is the 544 YM files YMX 0.8.3 is tested against, 543 of which
read. `ymx/parity.sh` in that repository names the path.

---

## Measured

### The schema fits in 32 columns

R3.4 caps a table at 32 columns and R3.1 has the schema cover the YM2149's
and the MFP's registers and the effects a tune drives them with. Whether
the second fits inside the first was open, and R6.4's second table turns on
it.

A column set covering all fourteen sound registers, all four timers, and
every technique the corpus plays fits with room to spare. SPEC.md holds the
set as it now is, at 18 columns and 30 bytes a row against a cap of 32
columns, so a second table is what an extension costs rather than what the
first version needs.

The shape that makes it fit is the hardware's. Four registers are ones a
timer can own: the three volume registers and the envelope shape register.
The MFP has four timers, so the schema has four effects, each costing two
columns, one for what it runs and one for how fast.

The corpus settled three widths that were otherwise a matter of opinion.
`ym/measure.py` reads the figures back out. It takes the corpus from
`YM_CORPUS`, as `ymx/parity.sh` does in YMX, reads 543 of the 544 files,
and counts over the 3,788,669 frame steps they hold. The file it does not
read is a YM3!, which is the same one YMX 0.8.3 does not read. The script
masks every register to its own width before comparing, which also strips
the effect flags YM5 and YM6 keep in the spare bits of R1, R3, R6, R8, R9
and R10.

| what was asked | the figure, voice A, B and C | what it settled |
|---|---|---|
| does a tone period need one column or two | the coarse nibble moves without the fine byte on 0.03%, 0.03% and 0.06% of frame steps | one 2-byte column, R3.5's first case |
| does the follow-the-envelope bit need a column of its own | it moves without the level on 0.24%, 0.01% and 0.11%, against the level's own 29.30%, 34.90% and 29.69% | it stays in the level's byte |
| may a set bit be read from what changed | 15,010 of the 18,949 shape writes rewrite a shape already sounding | it may not: a bit set from differences drops four envelope restarts in five |

Two figures bound how little of the table moves. 448 of the 543 tunes hold
one envelope period from start to end, and 117 hold one noise period
throughout. R4.4 spends the average, and the average is small.

### What the columns pack to

The schema is only worth its 30 bytes a row if the columns pack well, so
every corpus tune was converted into them and every column packed with ST4
at its own width. `ym/convert.py` runs the conversion and the packing, and
reads these figures back; the packer is built from YMX's `go/cmd/st4`.

| | bytes | a frame | against raw |
|---|---|---|---|
| raw rows, 543 tunes, 3,789,212 frames | 113,676,360 | 30.00 | |
| ST4, the 18 columns each their own stream | 1,602,170 | 0.42 | 71.0x |
| the same with one ST4 container a stream | 1,819,618 | 0.48 | 62.5x |

The containers cost 217,448 bytes over 9,774 streams, so a build wrapping
the whole table in one container keeps most of that.

Against the format it replaces, on the 41 tunes YMX 0.8.3 ships with both
files: the `.ymx` files hold 410,852 bytes and the packed columns 223,829,
which is 0.54x, or 0.57 bytes a frame against 1.05. The `.ymx` side counts
its headers and sample tables and the column side does not, so the ratio
flatters the columns by that margin.

zlib over the same columns gives 0.57x, so the halving is the schema's
rather than one packer's. ST4's payload beats zlib's on every run, which a
packer a 68000 can decode had no need to do.

The three tone periods take 70% of the packed bytes. The four effect
columns and their rates take 5.5%, and the envelope shape 0.5%.

One writer choice was measured rather than argued: a row that does not set
a column may hold anything there (R3.6), and filling with zero packs
smaller than repeating the last value, 23,589 against 24,518 bytes of zlib
over twelve tunes. `ym/convert.py` zero-fills.

### What the count does not settle

This measured whether a covering set fits, not which set. Which column
holds what, and in what order, is SPEC.md's.

---

## Changed

Three requirements come from the check over that column set. Each names a
way a column set can fit in 32 and still be wrong.

- **R3.5**, a column holding one thing, which two findings reached. The
  first read that a column's meaning may not depend on another's, and would
  have split each voice's effect data into three columns. That was wrong:
  the data is read at a start, and a start has already dispatched on the
  kind, so the byte costs a player no reading. What survives is the rule the
  mixing column rests on, that a write is not assembled from two columns.
  The second is that a column names its own target. The set keyed its four
  rate columns by voice, where the MFP's control registers are per timer
  with Timers C and D sharing one, so every rate move ended in a lookup for
  its own register. Keying by timer costs nothing, there being four of
  each.
- **R5.6** and **R5.7**, what is fixed for a whole tune. Nothing held how
  often a player is called, or which timers it claims before the first
  row. A player reads one row at a time (R1.3), so it can find neither by
  reading ahead.
