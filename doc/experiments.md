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

| | bytes | a frame | against raw |
|---|---|---|---|
| raw rows, 543 tunes, 3,789,212 frames | 113,676,360 | 30.00 | |
| ST4, each column its own stream | 2,572,854 | 0.68 | 44.2x |
| the same with one ST4 container a stream | 2,790,180 | 0.74 | 40.7x |

The gain over YMX 0.7, on the 41 tunes it ships with both files and their
390,593 frames, under one packer. The columns pack at the 960-byte ring
throughout; so do 39 of the 41 `.ymx` files, and the other two hold the
rings YMX's packer raised them to, 1,776 and 2,400 bytes, which is help
for the `.ymx` side.

| | bytes | a frame | of YMX 0.7 |
|---|---|---|---|
| YMX 0.7, the `.ymx` files | 410,852 | 1.05 | 1.00x |
| YMXR, the packed columns | 331,315 | 0.85 | 0.81x |

YMXR holds the same music in 19% fewer bytes. The `.ymx` side counts its
headers and sample tables and the column side does not, so the ratio
flatters the columns by that margin.

The three tone periods take 76% of the packed bytes, the sum of their
three rows. The four effects and their rates take 4.2%, and the envelope
shape 0.3%. Where a tune runs no effect at all, its four effect columns
and four rate columns pack to 4,708 bytes each over the corpus, which is
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
reading a row out of the table costs is DTX's, and unmeasured until DTX
exists, so what this settles is which side of R4.5 the budget will be
spent on. Seven eighths of it is still to play for.

---

## The envelope period's reserved 0

The period fills its word, so 0 says the row does not set it, and bit 6
of the shape keeps period 0 itself reachable (SPEC 1.6, 1.7). Packed both
ways at the same ring, the two envelope columns cost 72,071 bytes as
SPEC.md has them, and 124,151 under the design they displaced - a shape
column without the bit, and a 4-byte period column with an ordinary set
bit. The reservation saves 52,080 bytes, 2% of the 2,572,854 the corpus
packs to.
