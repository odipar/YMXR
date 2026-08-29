# experiments

One experiment: the corpus through the specification, and what it packs
to.

The corpus is the 544 YM files YMX 0.8.3 is tested against, 543 of which
read. `ymx/parity.sh` in that repository names the path, and `YM_CORPUS`
names it here. `ym/convert.py` runs everything below and reads the figures
back; the percentages are sums and ratios of its rows. The packer is ST4,
built from YMX's `go/cmd/st4`, with
back-references held to 960 bytes (`ST4_RING=960`): a streaming player
decodes into a ring, and 960 bytes a stream is YMX 0.7's default.

The figures measure the schema as it stood before an effect's source and
target took columns of their own (SPEC 1.8, 1.9). `ym/convert.py` follows
that earlier revision, and the figures move when it is brought forward.

---

## Everything fits

Every readable tune converts whole: 411 YM5! files and 132 YM6!, whose
effect slots encode differently. The fourteen sound registers cross at
their own widths, the two effect slots land in two of the schema's four
effects, and the largest sample number a tune names is 31, against the 127
a source number spans (SPEC 1.8). A row that does not set a value
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
three rows.

---

## The envelope period's reserved 0

The period fills its word, so 0 says the row does not set it, and bit 6
of the shape keeps period 0 itself reachable (SPEC 1.6, 1.7). Packed both
ways at the same ring, the two envelope columns cost 72,071 bytes as
SPEC.md has them, and 124,151 under the design they displaced - a shape
column without the bit, and a 4-byte period column with an ordinary set
bit. The reservation saves 52,080 bytes, 2% of the 2,572,854 the corpus
packs to.
