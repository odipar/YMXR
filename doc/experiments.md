# experiments

One experiment: the corpus through the specification, and what it packs
to.

The corpus is the 544 YM files YMX 0.8.3 is tested against, 543 of which
read. `ymx/parity.sh` in that repository names the path, and `YM_CORPUS`
names it here. `ym/convert.py` runs everything below and reads every
figure back. The packer is ST4, built from YMX's `go/cmd/st4`, with
back-references held to 960 bytes (`ST4_RING=960`): a streaming player
decodes into a ring, and 960 bytes a stream is YMX 0.7's default.

---

## Everything fits

Every readable tune converts whole. The fourteen sound registers cross at
their own widths, YM6's two effect slots land in two of the four effect
columns, and the largest sample number a tune names is 29, against the
255 the effect column's data byte allows. A row that does not set a value
zero-fills it (R3.6).

---

## What it packs to

| | bytes | a frame | against raw |
|---|---|---|---|
| raw rows, 543 tunes, 3,789,212 frames | 113,676,360 | 30.00 | |
| ST4, each column its own stream | 2,572,859 | 0.68 | 44.2x |
| the same with one ST4 container a stream | 2,790,188 | 0.74 | 40.7x |

Against the format it replaces, on the 41 tunes YMX 0.8.3 ships with both
files: the `.ymx` files hold 410,852 bytes and the packed columns
331,315, which is 0.81x, or 0.85 bytes a frame against 1.05. The `.ymx`
side counts its headers and sample tables and the column side does not,
so the ratio flatters the columns by that margin.

The three tone periods take 76% of the packed bytes.

---

## The envelope period's reserved 0

The period fills its word, so 0 says the row does not set it, and bit 6
of the shape keeps period 0 itself reachable (SPEC 1.6, 1.7). Packed both
ways at the same ring, the two envelope columns cost 72,071 bytes as
SPEC.md has them, and 124,151 under the design they displaced - a shape
column without the bit, and a 4-byte period column with an ordinary set
bit. The reservation saves 52,080 bytes, 2% of everything the corpus
packs to.
