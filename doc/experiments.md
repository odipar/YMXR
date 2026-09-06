# experiments

One experiment: the corpus through the specification, what it packs to,
and what a frame costs.

The corpus is the 544 YM files YMX 0.8.3 is tested against, 543 of which
read. `ymx/parity.sh` in that repository names the path, and `YM_CORPUS`
names it here. `ym/convert.py` runs everything below and reads the figures
back; the percentages are sums and ratios of its rows. The DTX2 files are
written by DTX 0.4.0's `dtx-write`, built from that repository's
`go/cmd/dtx-write`, from the columns as text, at the ring it defaults to,
960 bytes: a streaming player decodes into a ring, and 960 bytes a stream
is the ring every `.ymx` file below holds.

---

## Everything fits

Every readable tune converts whole: 411 YM5! files and 132 YM6!, whose
effect slots encode differently. The fourteen sound registers cross one a
column, and the two effect slots land in two of the schema's four
effects.

Two ceilings the schema sets, against what the corpus asks of them. A
source number is seven bits, so 127 sources, and the most any tune needs
is 16. A source holds at most 32,768 rows, and the longest the corpus
plays is a digidrum of a few thousand. A row that does not set a value
zero-fills it (R3.6).

---

## What it packs to

A DTX2 payload states one unit `k`, and every column of it packs at that
one (DTX, R5.2), so a reader holds one decoder. The rows below are whole
DTX2 files as `dtx-write` writes them: the header, the payload's `N`, `k`,
its flags and its offsets, an ST4 data set a column, and the pad that puts
each data set on a long.

| | bytes | a frame | against raw |
|---|---|---|---|
| raw rows, 543 tunes, 3,789,212 frames | 113,676,360 | 30.00 | |
| DTX2 files at `k` = 1 | 2,600,640 | 0.69 | 43.7x |
| DTX2 files at `k` = 2 | 3,078,520 | 0.81 | 36.9x |
| DTX2 files at `k` = 4 | 4,190,340 | 1.11 | 27.1x |

`k` = 1 packs smallest, and it is the unit every `R` divides by (DTX,
R5.6). At `k` = 2, 148 of the 543 tunes need one frame added to divide; at
`k` = 4, 250 tunes need 508 frames between them, and the packing is worse
besides. The converter packs at `k` = 2 (tools.md): `k` = 1 costs the
play call about a seventh more on average (performance.md). No frame is
added: a tune whose row count or repeat row is odd packs at `k` = 1
instead.

The gain over YMX, on the 42 tunes it ships with both files and their
391,193 frames. The `.ymx` files are YMX 0.10.1's, format 0.9, every one
packed at a 960-byte ring and none with copies from its literal stream, so
the two sides pack alike. Eight of them start over at a frame other than
the first, where a DTX2 file here does not repeat.

| | bytes | a frame | of YMX |
|---|---|---|---|
| YMX 0.10.1, the `.ymx` files | 404,752 | 1.03 | 1.00x |
| YMXR, DTX2 files at `k` = 1 | 319,024 | 0.82 | 0.79x |
| YMXR, DTX2 files at `k` = 2 | 373,300 | 0.95 | 0.92x |
| YMXR, DTX2 files at `k` = 4 | 540,668 | 1.38 | 1.34x |

YMXR holds the same music in 8% fewer bytes at `k` = 2, the unit the
converter packs at (tools.md), and in 21% fewer at `k` = 1. Both sides
are whole files, but a `.ymx` holds its sample tables and a DTX2 file
holds no sources yet, since where a tune places them is not written
(SPEC 8). That margin flatters the DTX2 side.

At `k` = 1, the three tone periods, fine and coarse together, take 21.9%,
18.0% and 19.3% of the packed bytes, 59.2% between them. The sixteen
effect columns take 15.7%, and the envelope shape 1.4%. No corpus tune
runs more than two effects at once, so columns 22 to 29 hold zeros
throughout and pack to 20,284 bytes each: the floor a column costs,
mostly the 28-byte ST4 header its data set opens with, one a tune.

---

## What a frame costs

R4.5 budgets the worst frame at what 13 scanlines cover. `ym/convert.py
frame` counts, for every frame of every tune, the columns a row sets and
the register writes the frame procedure of section 4 makes for them: one
a column for the fourteen registers, and for an effect one for a stop,
three for a start or a timer's reset, and one each for a count or a
select written to a running timer (SPEC 1.9).

| | columns set, of 30 | YM writes | MFP writes |
|---|---|---|---|
| the average frame | 3.91 | 3.81 | 0.10 |
| the 99th frame in a hundred | | 10 together | |
| the worst frame in the corpus | 18 | 12 | 6 |

The worst frame is `Synergy Odyssey`, which sets twelve registers and
starts both its effects, each a source, a control and a count. Fourteen
is the ceiling for register writes in any format at all, since the chip
holds fourteen sound registers, and a start adds the three writes a
timer's reset costs.

One PAL frame is 160,256 cycles and one scanline 512, so R4.5's 13
scanlines are 6,656. Two byte writes reach a YM register, and at a
generous 32 cycles for the pair the worst frame's 18 writes are about 580
cycles, with another 370 to test thirty columns: near 950 cycles, under
two scanlines.

That is the frame procedure's own work and not the whole frame. What
reading a row out of the table costs is DTX's, measured there for its
reader and not for a table of this shape, so it is not counted here.
R4.5's budget is seven times that.

---

## The envelope period's reserved 0

The period's two bytes fill their columns, so 0 says the row does not set
one, and bits 6 and 5 of the shape keep a zero byte reachable (SPEC 1.1,
1.7). The other way to keep it is a set bit for each byte, held in the
shape column, which then moves whenever a byte moves between set and
unset (1.7). Packed both ways at the same ring and at `k` = 1, the three
envelope columns cost 123,646 bytes as SPEC.md has them, and 131,622
under set bits held in the shape. The reservation saves 7,976 bytes,
0.3% of the 2,600,640 the corpus packs to at that unit.

The saving is smaller than the 27,362 an earlier measurement gave. That
one set the period as one column of two bytes against a plain column of
four with a set bit of its own, and a column of four bytes is one no
table of one-byte values holds (R1.1).
