# experiments

One experiment: the corpus through the specification, what it packs to,
and what a frame costs.

The corpus is the 544 YM files YMX 0.8.3 is tested against, 543 of which
read. `ymx/parity.sh` in that repository names the path, and `YM_CORPUS`
names it here. `ym/convert.py` runs everything below and reads the figures
back; the percentages are sums and ratios of its rows. The packer is ST4,
built from YMX's `go/cmd/st4`, with
back-references held to 960 bytes (`ST4_RING=960`): a streaming player
decodes into a ring, and 960 bytes a stream is YMX 0.7's default.

Every figure below was measured over the column set the specification
held then: eighteen columns, twelve of two bytes and six of one, 30 bytes
a row, in DTX2 files as DTX laid them out then, a header of 14 bytes plus
`C` rounded up to a long. The specification now holds thirty columns of
one byte (SPEC 1), the one width DTX 0.4.0 gives a whole table, and the
converter has not run over them.

---

## Everything fits

Every readable tune converts whole: 411 YM5! files and 132 YM6!, whose
effect slots encode differently. The fourteen sound registers cross at
their own widths, and the two effect slots land in two of the schema's
four effects.

Two ceilings the schema sets, against what the corpus asks of them. A
source number is a byte, so 255 sources, and the most any tune needs is
16. A source holds at most 32,768 rows, and the longest the corpus plays
is a digidrum of a few thousand. A row that does not set a value
zero-fills it (R3.6).

---

## What it packs to

A DTX2 payload states one unit `k`, and every column of it packs at that
one (DTX, R5.2), so a reader holds one decoder. The rows below are whole
DTX2 files: the header, the payload's `N`, `k` and its offsets, an ST4
data set a column, and the pad that puts each data set on a long.

| | bytes | a frame | against raw |
|---|---|---|---|
| raw rows, 543 tunes, 3,789,212 frames | 113,676,360 | 30.00 | |
| DTX2 files at `k` = 1 | 2,717,440 | 0.72 | 41.8x |
| DTX2 files at `k` = 2 | 2,927,944 | 0.77 | 38.8x |
| DTX2 files at `k` = 4 | 4,259,388 | 1.12 | 26.7x |

`k` = 1 packs smallest, and it is the unit every `R` divides by (DTX,
R5.6). At `k` = 2, 148 of the 543 tunes need one frame added to divide; at
`k` = 4, 250 tunes need 508 frames between them, and the packing is worse
besides.

The gain over YMX 0.7, on the 41 tunes it ships with both files and their
390,593 frames, under one packer. The columns pack at the 960-byte ring
throughout; so do 39 of the 41 `.ymx` files, and the other two hold the
rings YMX's packer raised them to, 1,776 and 2,400 bytes, which is help
for the `.ymx` side.

| | bytes | a frame | of YMX 0.7 |
|---|---|---|---|
| YMX 0.7, the `.ymx` files | 410,852 | 1.05 | 1.00x |
| YMXR, DTX2 files at `k` = 1 | 343,952 | 0.88 | 0.84x |
| the same at a unit a column | 352,436 | 0.90 | 0.86x |

YMXR holds the same music in 16% fewer bytes. The last row is what a
column packed at its own width comes to, which DTX2 does not admit. One
unit for the payload costs nothing: it packs 2% smaller than a unit a
column does.

Both sides are whole files, but a `.ymx` holds its sample tables and a
DTX2 file holds no sources yet, since where a tune places them is not
written (SPEC 7). That margin flatters the DTX2 side.

The three tone periods take 28.1%, 22.7% and 24.6% of the packed bytes,
75.4% between them. The four effects and their rates take 4.5%, and the
envelope shape 0.3%. No corpus tune runs more than two effects at once,
so columns 14 to 17 hold zeros throughout and pack to 3,929 bytes each:
the floor a column of zeros costs.

---

## What a frame costs

R4.5 budgets the worst frame at what 13 scanlines cover, and until now
that was the one figure here nobody had measured. `ym/convert.py frame`
counts, for every frame of every tune, the columns a row sets and the
register writes the frame procedure of section 4 makes for them.

| | columns set, of 18 | YM writes | MFP writes |
|---|---|---|---|
| the average frame | 3.41 | 5.40 | 0.07 |
| the 99th frame in a hundred | | 11 together | |
| the worst frame in the corpus | 18 | 14 | 8 |

The worst frame is `5th Gear 1 title`, which sets every column: all
fourteen sound registers, all four rates, all four effects. Fourteen is
the ceiling for any format at all, since the chip holds fourteen sound
registers, so no encoding writes more.

One PAL frame is 160,256 cycles and one scanline 512, so R4.5's 13
scanlines are 6,656. Two byte writes reach a YM register, and at a
generous 32 cycles for the pair the worst frame's 22 register writes are
about 700 cycles, with another 220 to test eighteen set bits: near 920
cycles, under two scanlines.

That is the frame procedure's own work and not the whole frame. What
reading a row out of the table costs is DTX's, and no 68000 reader is
written, so it is unmeasured. Seven eighths of R4.5's budget is still to
play for.

---

## The envelope period's reserved 0

The period fills its word, so 0 says the row does not set it, and bit 6
of the shape keeps period 0 itself reachable (SPEC 1.6, 1.7). Packed both
ways at the same ring and the same unit, the two envelope columns cost
69,440 bytes as SPEC.md has them, and 96,802 under the design they
displaced - a shape column without the bit, and a 4-byte period column
with an ordinary set bit. The reservation saves 27,362 bytes, 1% of the
2,717,440 the corpus packs to.

The first measurement of this put the saving at 52,080. It packed each
column at its own width, so the 4-byte period column went to ST4 at
`k` = 4, and the design under test paid for a unit no DTX2 payload gives
it (DTX, R5.2). At one unit that design costs 22% less, and the saving
halves.
