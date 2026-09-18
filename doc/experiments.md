# experiments

One experiment: the corpus through the specification, what it packs to,
and what a frame costs.

The corpus is the 544 YM files YMX 0.8.3 was tested against, 543 of which
read. `YM_CORPUS` names the directory here. `ym/convert.py` runs everything
below and reads the figures back; the percentages are sums and ratios of
its rows. The DTX2 files are
written by DTX 0.4.0's `dtx-write`, built from that repository's
`go/cmd/dtx-write`, from the columns as text, at the ring it defaults to,
960 bytes: a streaming player decodes into a ring, and 960 bytes a stream
is the ring in every `.ymx` file below.

---

## Everything fits

Every readable tune converts whole: 411 YM5! files and 132 YM6!, whose
effect slots encode differently. The fourteen sound registers cross one a
column, and the two effect slots land in two of the schema's four
effects.

Two ceilings the schema sets, against what the corpus requires of them. A
source number is seven bits, so 127 sources, and the most any tune requires
is 16. A source is at most 32,768 rows, and the longest the corpus plays is
a digidrum of a few thousand. A row that does not set a value zero-fills it
(R3.6).

83 tunes name any source: 14 a square wave and 69 a digidrum, and none
a sinus SID or a sync buzzer. Their shapes are two rows repeating to row 0
and many rows played once (SPEC.md 1.9), so the third shape the format
defines, one row repeating, is named by no file here.
`ym/convert.py` counts them, a tune once a kind.

---

## What it packs to

A DTX2 payload records one unit `k`, and every column of it packs at that
one (DTX, R5.2), so a reader runs one decoder. The rows below are whole
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
play call about a seventh more on average (performance.md). A tune whose
row count or repeat row is odd is padded until it packs at `k` = 2
(SPEC.md 6, rule 6): rows that set no column at the repeat row, then a
loop of fewer than 64 rows written again or rows that set no column at
the end ("Where a padded tune's added frame lands" below); before that
rule it packed at `k` = 1 instead.

At `k` = 1, the three tone periods, fine and coarse together, are 21.9%,
18.0% and 19.3% of the packed bytes, 59.2% between them. The sixteen effect
columns are 15.7%, and the envelope shape 1.4%. No corpus tune runs more
than two effects at once, so columns 22 to 29 are zero throughout and pack
to 20,284 bytes each: the floor a column costs, mostly the 28-byte ST4
header its data set opens with, one a tune.

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

YMXR packs the same music into 8% fewer bytes at `k` = 2, the unit the
converter packs at (tools.md), and 21% fewer at `k` = 1. Both sides are
whole files, but a `.ymx` includes its sample tables and a DTX2 file is the
tune's table alone: a tune file keeps its sources beside it (SPEC.md 3.3)
and the figures here leave them out, so both margins read wider than a tune
file against a `.ymx` would.

## Copies from the literal stream

DTX2 packs a match beyond the ring as a copy from the column's separate
literal stream where the packer is run with `-copies` (tools.md), and the
format block records which way the table was packed, so the binder selects
the reader for it. It is off by default.

Copies save more as the ring shrinks, since a smaller ring puts more
matches beyond it. DBA 5 at `k` = 1, the tune file's bytes, against the
state block the ring requires of a host:

| ring | packed | with copies | saved | the state block |
|---|---|---|---|---|
| 960 | 13,908 | 13,484 | 424 | 31,272 |
| 480 | 16,856 | 16,352 | 504 | 16,872 |
| 240 | 19,272 | 18,420 | 852 | 9,672 |
| 120 | 21,596 | 20,580 | 1,016 | 6,072 |

So it pays most where a host has the least to spare. The reader that reads
copies is 32 bytes larger, 1,512 against 1,480, which the table recovers at
every ring above.

At the default ring it is a per-tune switch rather than a default. Six
tunes read -4.08% on 5th Gear 1 title and -3.33% on Last Ninja, both of
them long; 0.00% on Big - Delta 6 and Spaceball 1, both short; -0.48% on
DBA 2; and **+0.26%** on Crapman level 3, which packs larger with copies
than without. Packing costs 4 to 38 times as long, and `-copiesS` searches
S seconds beyond the opening passes, which packs another parse every
run.

### A set shares one workspace

A multi-tune program allocates a single workspace, sized for the largest
of its tunes: `YMXR_FIXED` plus the largest state block over the set
(BINARIES.md). So a smaller ring saves those bytes once for a set of any
size, while the larger file it packs to is paid for every tune, and at
some size the two cross. Measured as programs, using Deeper (9,984 rows),
DitherDance (4,044) and low (9,903), repeated in that order to fill the
set:

| tunes | ring 960, no copies | ring 120 with copies | smaller by |
|---|---|---|---|
| 1 | 53,320 | 33,992 | 36.2% |
| 2 | 69,480 | 51,208 | 26.3% |
| 3 | 82,826 | 70,816 | 14.5% |
| 4 | 95,878 | 89,710 | 6.4% |
| 6 | 123,918 | 125,034 | -0.9% |
| 9 | 165,012 | 179,254 | -8.6% |
| 12 | 206,104 | 233,472 | -13.3% |

The workspace saved is 25,200 bytes, once: 31,272 bytes at a ring of 960
against 6,072 at 120. The bytes a tune adds are 13,889 at the defaults and
18,135 at the small ring, 4,246 more a tune. The two meet at 25,200 over
4,246, near six tunes, where the table turns negative.

Where they meet follows the length of the tunes: the ring sets the
workspace and the rows set the file, so a set of short tunes adds less a
tune and crosses later than these three, which are long. `-m120 -copies`
is for a program of one tune or a few, and the defaults are for a set.

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
starts both its effects, each a source, a control and a count. Fourteen is
the ceiling for register writes in any format, since the chip has fourteen
sound registers, and a start adds the three writes a
timer's reset costs.

One PAL frame is 160,256 cycles and one scanline 512, so R4.5's 13
scanlines are 6,656. Two byte writes reach a YM register, and at a
generous 32 cycles for the pair the worst frame's 18 writes are about 580
cycles, with another 370 to test thirty columns: near 950 cycles, under
two scanlines.

That is the frame procedure alone and not the whole frame. What reading a
row out of the table costs belongs to DTX, measured there for its reader
and not for a table of this shape, so it is not counted here. R4.5's budget
is seven times that.

---

## The envelope period's reserved 0

The period's two bytes fill their columns, so 0 marks the row that does not
set one, and bits 6 and 5 of the shape keep a zero byte reachable (SPEC
1.1, 1.7). The other way to keep it is a set bit for each byte, placed in
the shape column, which then moves whenever a byte moves between set and
unset (1.7). Packed both ways at the same ring and at `k` = 1, the three
envelope columns cost 123,646 bytes as SPEC.md has them, and 131,622 under
set bits in the shape column. The reservation saves 7,976 bytes, 0.3% of
the 2,600,640 the corpus packs to at that unit.

The saving is smaller than the 27,362 of an earlier measurement. That one
set the period as one column of two bytes against a plain column of four
with a set bit beside it, and a column of four bytes is outside a table of
one-byte values (R1.1).

---

## One unit across a set of subtunes

A set of subtunes shares an image with every other subtune that agrees on
what an image fixes once, the unit among them (BINARIES.md 1). Before
SPEC.md 6, rule 6, a tune whose row count or repeat row was odd packed at
unit 1 whichever unit the flags requested, so a set as it came needed two
images, one a unit, and `-k1` on every dump needed one. Under rule 6 every
tune packs at the unit the flags request and a set needs one image either
way; the measurement below is from before it.

Twenty tunes off the corpus, every ninth, converted both ways:

| | images | the file |
|---|---|---|
| as they come | 2 | 150,816 |
| every dump at `-k1` | 1 | 136,668 |

14,148 bytes, 9.4 per cent. The image the set stops paying for is 1,492 of
that and the tables are the rest, since unit 1 packs smaller: twenty eight
tunes the defaults pack at unit 2, spread across the corpus, read 14.4 per
cent smaller at unit 1, 133,340 bytes against 155,748. One of them, Union
Demo - Megadist 2, packs 43.6 per cent smaller and another, Masterblazer 6,
0.8.

The play call pays for it. Four of those tunes under Hatari, at unit 2 and
then at unit 1:

| tune | on average | at most |
|---|---|---|
| 5th Gear 1 title | 1,410 to 1,669 | 3,484 to 4,244 |
| A Prehistoric Tale 5 | 1,439 to 1,695 | 2,556 to 5,156 |
| A Prehistoric Tale 14 | 1,468 to 1,726 | 4,080 to 4,424 |
| Ancient Zone | 1,446 to 1,707 | 3,168 to 4,600 |

About a fifth on the average call, against the seventh performance.md
measures on Turrican - world 4-3, and the costliest frame moves further
and less evenly: one of the four doubles. Twelve more of them at unit 1
read 3,016 to 5,496 in their costliest frame, and R4.5 budgets 6,656.

So the trade is a tenth of the file for a fifth of the call, and the frame
the budget binds moves toward it. The image is 1,492 bytes of the 14,148,
so what a set gains from one unit is mostly what any tune gains from it,
and `-k1` stays a flag rather than the default for a set. Under rule 6 the
image is no part of the trade, and the tables alone are.

## Where a padded tune's added frame lands

Rule 6 pads a loop of odd length by one frame a pass. Of the 543 dumps of
the corpus, converted under 0.3.11, 161 are padded, and in 137 that frame
lands inside the loop: thirteen loops are one row, an ending that
sustains, which a row that sets no column leaves as it was; 121 are 95
rows or more, where one frame is under one per cent of a pass; and three
are short and move every row. Masterblazer 7 slides all three tones over
7 rows, A Prehistoric Tale 6 runs a vibrato on voice B over 9, and
Crapman game over an arpeggio over 17, which one frame a pass slows by
14, 11 and 6 per cent. Played side by side under Hatari, each of the
three with the frame and written again, neither version was heard as
wrong: the difference is measured, not heard.

A loop of fewer than 64 rows is written again all the same (SPEC.md 6,
rule 6), since it keeps the period the dump had for a few bytes: the
sixteen short loops play as they did, and the 121 long ones keep the
frame. The second copy packs as a match a column, so it costs under four
per cent against the frame it replaces: on the three above and on
Turrican 2 - world completed 1, 1,768 bytes to 1,816, 1,452 to 1,508,
2,448 to 2,508 and 2,164 to 2,180. A one-row loop written twice costs 0
to 16 bytes on four of the thirteen.

---

## What a square does when it starts

A SID voice is a volume register moving between a level and 0 at a timer's
rate. The YM format leaves that movement to the player across the row
where one note ends and another begins, so the players of the era each do
something else there and each composer heard one of them. Two are on record.

The **ym2149-rs model**, which the reference player ships: a start writes
0 to the voice at once and sets the alternation to its loud half, and the
first tick, one timer period later, writes that half.

**maxYMiser's model**, the tracker most of these sections were written in:
the start leaves the level and the half as they are. Its replayer traced
under Hatari on *Chipping for Ca$h* writes R0 to R10 every frame and leaves
the volume register the square runs on unwritten for all 218 frames it
runs; on the row the square starts it leaves the level as it is too, and
the first tick writes the wave's first value. Its timer's count and control
are rewritten every frame with the timer running, and no row stops it. Its
release clears the mask and not the control register, so the count runs on
and the tick after the next note falls a whole period after the tick before
the gap.

This format adopted the first model and applied it to every row that
starts a source. In this schema the values written belong to the source
(2.2), so a square whose level moves starts a new source on most rows: the
lead of DBA 5 starts one on 957 of its first 1,575 rows and Synergy
Credits on 3,396 of 5,377. Every one of those rows wrote 0 to the voice
between two ticks and moved the place to the source's loud row, so the
half those two ticks bound was cut in two and the one after it began
early. Read off the
voice's edges over 1,575 frames of DBA 5:

| the row that starts a square | edges | halves short | halves long | off |
|---|---|---|---|---|
| moves the place and writes 0 to the voice | 16,906 | 351 | 52 | 2.4% |
| moves the place alone | 16,266 | 7 | 313 | 2.0% |
| leaves the place and the level as they are | 16,625 | 5 | 29 | 0.2% |
| the reference player | 16,625 | 4 | 28 | 0.2% |

The middle row stood before the write of 0 was added: a start whose place
moved to the loud row while the voice was loud wrote that level again, so
no edge fell between the two ticks and the half ran to twice its length.
Adding the write of 0 turned 313 long halves into 351 short ones and left
every other figure as it was. Both follow from one reading: a row that
starts a source read as a row that starts a wave.

The third row is the rule now. A row that starts a square where this effect
last ran one on the same target leaves bit 5 clear and sets no volume
column, so the voice is left as it is between the two ticks either side of
it and they fall a whole period apart (1.3, 1.9, section 6 rule 3). The
level the second writes belongs to the new source.

### The place needs no code

The player was changed for this and then changed back. A row that stops an
effect writes select 0 and drops the latched tick, and leaves the handler's
place and the source's first row as they are; a start whose row leaves bit
5 clear reads the place and counts that number into the new source's rows.
So a square that stops and starts again resumes at the row it left off at
while the player stands as it is, and the converter encodes it by keeping
the last kind and target each effect ran rather than the one it runs.

The other half of maxYMiser's model, a timer that counts through the gap,
was built and measured: the source column's 0 clears the enable bit instead
of writing select 0, the tick that runs a source out does the same, and a
start after a gap programs the timer only where the prescaler moved. It
costs 64 bytes of player and the effect step's enable write, 2,502 cycles a
call on Synergy Credits against the 2,469 of the player then, and 2,457
through the raster monitor against its 2,421. It buys the timer's phase
over a gap in which the voice is silent. Measured against it on the drum
preempt tune, which stops and starts a square on one voice 191 times, the
two produce 2,873 and 2,874 edges, and the one whose timer counts on shows
three short halves where the other's are whole: its first half after a gap
runs for what the counter had left of a period, where a timer started again
counts a whole one. It costs less and breaks fewer halves, so the timer is
stopped and bit 5 alone moves the place.

### Four rules this left behind

1. **A rule written into the specification, the converter, the player and
   the rig's model agrees with every check that reads it.** Every rig here
   runs the player against a model built from the tune's tables, so it
   proves the player writes what the table encodes. It cannot see that the
   table encodes the wrong thing. The conformance kit pins what the
   converter writes, so it pinned the defect as the reference too, and
   `synergy.ymxr` shrank by 244 bytes when the defect was removed. A rule
   about sound needs a check that reads sound.
2. **Compare event timing, not event values.** Every comparison made
   against the reference player before this one passed: which registers a
   frame writes, the values, the order, the counts, each register at each
   frame boundary, the timers' prescaler and count at each boundary. The
   write that broke the square is a legal value at a legal moment, and it
   changes the spacing between the writes to one register alone.
   `ym/halves.py` reads that spacing out of a trace; run it before reading
   a square as right.
3. **A loudness metric over a second measures the wrong thing.** The
   metric used for two days was a per-second correlation of level against
   the reference, and two builds whose output was identical byte for byte
   scored 0.70 and 0.28 on it, differing only in where the program landed
   in the frame. A measure that moves with the phase cannot report on the
   phase.
4. **Read the distinction the reference draws, not one of its rules.** The
   reference player has two routines here, and the comment on the first
   says which is which: "a fresh square restarts at phase zero … Retunes
   and resumes never come here". Reading the fresh-start routine and
   applying its rule to every start put the defect here.

---

## The targets of several registers, heard

A target of several registers (SPEC.md 2.1) writes a voice from one
effect where version 3 needs one effect a register. Whether the tune that
comes out is the same music is a question about sound, so the three forms
were heard side by side. `dist/ab/AB.PRG` plays one piece three ways, a
subtune each, 1,536 rows at 50 Hz: two timers under version 3, one
`setVoiceA`, and one `setToneA` with the fine byte beside the coarse
nibble. Under Hatari the three sound the same.

The records agree as far as they reach. Each has 3,072 frames (SPEC.md 7),
and 3,024 of them are equal byte for byte across the three. The 48 that
differ are the 48 that start a timer, and in each the difference is R0
alone: under version 3 the frame writes the tone's fine byte at a section's
first row, and under a target of several registers the first tick of the
effect starting there writes it. At select 4 and count 96, the rate these
subtunes run their effects at, a tick is every 1.95 ms.

A record leaves the ticks out (YMXS, SPEC.md 7.1), and the ticks are where
the three forms differ most: one tick writing R0, R1 and R8 stands where
two ticks on two timers wrote R1 and R8 and a frame wrote R0. That
difference stands below the record, and the listening reaches it.

The three tune files stand beside the program: 3,356 bytes under version 3,
3,108 for `setVoiceA` and 3,116 for `setToneA`, 24 sources against 48, and
one timer claimed against two, which leaves a timer for the tune to use.
`dist/` is outside the repository, as `YM_CORPUS` is.

---

## A timer on a whole byte, heard

The marker stands in bit 7 of a row, so a source drove a register of seven
bits or fewer until version 5: a volume, a coarse nibble, the envelope
shape. A counted source has no marker and its rows are whole bytes
(SPEC.md 3.1.6), which opens the six registers that read all eight. What
that sounds like is a question for the ear, so `ym/whole-byte.py` writes
a tune that spends it and `bin/ymxs-to-prg` makes a program of it:

    python3 ym/whole-byte.py | bin/ymxs-to-prg -r4000 > dist/whole/TUNE.PRG
    ym/hatari.sh dist/whole

Five sections of 800 rows at 50 Hz, 16 seconds each, one melody on voice
B under all five, and the note a sweep drives at a period of 256 to 511,
so a fine byte over its whole range moves the pitch by an octave. Under
Hatari, over the middle twelve seconds of each section, with the forty
strongest bins of a spectrum to 12 kHz as the measure of how far a
section spreads its energy:

| section | what a timer drives | the ticks | the forty strongest bins |
|---|---|---|---|
| 1 | the rows alone, voice A at 440 Hz | none | 9.4% |
| 2 | `setR0`, the tone's fine byte | 768 a second, then 3,072 | 7.3% |
| 3 | `setR4`, voice C's fine byte | 1,536 a second, then 6,144 | 8.9% |
| 4 | `setR11`, the envelope period's low byte | 320 a second | 2.8% |
| 5 | `setR7`, the mixer gating voice A | 384 a second | 6.1% |

A steady tone keeps its energy in a few bins and a swept one spreads it,
so the figure falls as a section moves its register faster over a wider
range: the envelope sweeping 1,953 Hz down to 30 five times a second
spreads it furthest. The five sections stand at one loudness, 3,309 to
4,274 rms, so the spread is the pitch moving rather than a section
playing louder.

The tune file is 1,732 bytes at version 5, with four counted sources of
32, 16, 64 and 2 rows. The two rows of the last are `$FC` and `$FD`: the
mixer's 60 and 61 with the two port directions the writer sets (rule
2(f)). `dist/` is outside the repository.
