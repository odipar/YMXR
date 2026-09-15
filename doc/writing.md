# writing

For a tracker, or any tool that makes a tune file rather than plays one.
SPEC.md defines the format; this fixes the order to read it in, what the
format leaves to a writer, and the checks on what comes out. A reader is
written against the conformance kit instead (conformance/README.md), and a
player against SPEC.md 4 and 5.

**Read YMXS first.** [YMXS](https://github.com/odipar/YMXS)'s SPEC.md is
what a tune is: rows of registers and effects, what each register reaches,
how a rate is reckoned, and the rules a writer satisfies. SPEC.md here is
the encoding of that, and repeats none of it. A writer whose tune is
already in the structure needs none of this document: write the tune as
YMXS JSON and run `bin/ymxs-to-ymxr`, which encodes the columns (ymxs.md).

## What a writer makes

A tune file (SPEC.md 3.3): a header, the tune's table as a DTX2 file, an
index of the sources, and one DTX1 table a source. It contains no code. A
tool binds it with DTX's reader into the layout a player reads
(BINARIES.md), so that file is the only route from a writer to a
player.

`src/main/java/org/ymxr/` is one writer, from a YM dump. It reads as a
worked example of everything below.

## The table

One row a frame, at the rate in the header. Thirty columns (SPEC.md 1): 0
to 13 reach R0 to R13 in the chip's numbering, and 14 to 29 are four
effects of four columns each.

Bit 7 of a column is its set bit: at 1 the row sets that column, and at 0
it does not, so the value's bits are arbitrary (1.1). Nine columns have no
bit to spare, since the register fills the whole byte, and reserve 0 for
the row that does not set them. Five of those have a bit beside them that
keeps 0 reachable as a value, and 1.1 lists which. 1.2 to 1.7 define each
column separately.

A row that sets no column leaves every register as it is, so a writer
writes a value on the row it changes and leaves the rows between alone.

## The effects

Four columns an effect: the target, the source, the timer's control and
its count (1.8, 1.9). A row starts an effect by setting its source
column to the source's number, and stops it by setting that column to 0:
the set bit at 1 with no source under it, which is `$80` and not a byte of
0. A column left at 0 is a column the row does not set (1.1), and the two
are the difference between stopping an effect and leaving it alone.

The control column defines the prescaler in bits 2 to 0, the timer's reset
in bit 6 and the place's reset in bit 5.

Which timer an effect runs belongs to the machine rather than the writer:
2.3 assigns Timers A, D, B and C to effects 0 to 3. A tune running one
effect sets columns 14 to 17 and leaves 18 to 29 unset for its whole
length.

**SPEC.md 6, the rules a writer satisfies, is this document's other half.**
Those rules bind the writer rather than the player: which columns a row
leaves unset while an effect runs, which effects a row may name, and which
bit a row that starts a source sets with it. A player writes a marked
column once and assumes those rules, which a writer satisfies, so a file
that breaks one plays as something else rather than failing.

## The sources

A source is a DTX1 table of `R` rows and one column of one-byte values
(3.1). `C` is 1 and `W` is 1 at this version, and a reader rejects a
source of another shape. `RR` is the row it repeats to, and where `RR`
equals `R` the source plays once.

The last row has bit 7 set, alone among the rows (3.2). That bit is the
marker, and the register reads the rest of the byte, so a source names a
target whose register ignores bit 7: `setR1`, `setR3`, `setR5`, `setR6`,
`setR8` to `setR10` and `setR13` (2.1). A source on `setR0`, `setR7` or any
other target of the fourteen belongs to a later version.

The sound follows from the shape of the source, and the format defines no
kind (2.2):

| the source | what it sounds |
|---|---|
| one row repeating | R13 rewritten at the timer's rate, restarting the envelope: a sync buzzer |
| two rows repeating to row 0 | a volume flipped between a level and 0 at the timer's rate: a SID voice |
| many rows played once | a recording through a volume register: a digidrum |

The values belong to the source, so two SID voices at two levels are two
sources, and the effect column names one of them.

## Packing the table

The table goes into the file as a DTX2 file, which DTX's writer makes
(DTX, SPEC.md 2.3). Three of its settings reach a player:

| setting | what it requires of a writer |
|---|---|
| the unit `k` | a unit of 2 requires that `R` and `RR` both divide by 2 |
| the ring | the bytes a column unpacks through, a multiple of the thirty in a period and at most 1129 |
| the period | thirty rows, the column count, so a refill reads one period of one column |

The ring sets the size of the state block a host allocates, thirty rings
and a fixed part, and a smaller ring costs packed bytes. experiments.md
measures the two against each other.

## What a reader rejects

| the file | why |
|---|---|
| a version that is not `$0002` | 3.3, R6.1 |
| a source whose `C` or `W` is not 1 | 3.1 |

## Checking what you wrote

Four checks, each reading more of the file than the one above it:

```bash
bin/ymxr-trace -r4 < tune.ymxr      # what a reader reports of it (SPEC.md 7)
bin/ymxr-bind < tune.ymxr > tune.bin   # bound with DTX's reader, or rejected
python3 68k/test/emu/test_ymxr.py tune.ymxr
ym/play.sh tune.ymxr                # under Hatari, with its sound on
```

The third is the one that reads the tune as a player does: it plays the
file on an emulated 68000 and checks every frame against a model of
SPEC.md 4 and 5, built from the tune's tables, so a row that encodes one
thing and sounds another fails there by name. `-hatari` plays it on a real
MFP (tools.md).

`doc/conformance/tunes/` contains eleven tune files and what a reader
reports of each, so a writer has both a valid file to read and the record
its rows produce.

## The order to read

1. requirements.md R3, what a tune's data is.
2. SPEC.md 1, the columns, and 1.1 before the rest of them.
3. SPEC.md 2, the targets, the sources and the timers.
4. SPEC.md 3, the bytes of the tune file.
5. SPEC.md 6, the rules that bind a writer and no one else.
6. SPEC.md 4 and 5, what a player does with all of it.

glossary.md lists every term this uses, and terminology.md describes the
machine under them.
