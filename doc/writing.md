# writing

For a tracker, or any tool that makes a tune file rather than plays one.
SPEC.md states the format; this gives the order to read it in, what a
writer settles that the format leaves to it, and how to check what comes
out. A reader is written against the conformance kit instead
(conformance/README.md), and a player against SPEC.md 4 and 5.

## What a writer makes

A tune file (SPEC.md 3.3): a header, the tune's table as a DTX2 file, an
index of the sources, and one DTX1 table a source. It holds no code. A
tool binds it with DTX's reader into what a player takes (BINARIES.md),
so nothing a writer makes reaches a player except through that file.

`src/main/java/org/ymxr/` is one writer, from a YM dump. It reads as a
worked example of everything below.

## The table

One row a frame, at the rate the header states. Thirty columns (SPEC.md
1): 0 to 13 reach R0 to R13 in the chip's own numbering, and 14 to 29 are
four effects of four columns each.

Bit 7 of a column is its set bit: at 1 the row sets that column, and at 0
it does not, so the value's bits may hold anything (1.1). Nine columns
have no bit to spare, since what they reach takes the whole byte, and
reserve 0 for the row that does not set them. Five of those have a bit
beside them that keeps 0 reachable as a value, and 1.1 gives which. 1.2
to 1.7 give each column on its own.

A row that sets no column writes no register, so a writer states a value
on the row it changes and leaves the rows between alone.

## The effects

Four columns an effect: the target, the source, the timer's control and
its count (1.8, 1.9). A row starts an effect by setting its source
column to the source's number, and stops it by setting that column to 0:
the set bit at 1 with no source under it, which is `$80` and not a byte
of 0. A column left at 0 is a column the row does not set (1.1), and the
two are the difference between stopping an effect and saying nothing
about it.

The control column gives the prescaler in bits 2 to 0, the timer's reset
in bit 6 and the place's reset in bit 5.

Which timer an effect runs is the machine's and not the writer's: 2.3
gives Timers A, D, B and C to effects 0 to 3. A tune running one effect
sets columns 14 to 17 and leaves 18 to 29 unset for its whole length.

**SPEC.md 6, What a writer does not do, is this document's other half.**
Its rules bind the writer rather than the player: which columns a row
leaves unset while an effect runs, which effects a row may name, and
which bit a row that starts a source sets with it. A player writes a
marked column once and tests nothing because a writer keeps those rules,
so a file that breaks one plays as something else rather than failing.

## The sources

A source is a DTX1 table of `R` rows and one column of one-byte values
(3.1). `C` is 1 and `W` is 1 at this version, and a reader rejects a
source of another shape. `RR` is the row it repeats to, and where `RR`
equals `R` the source plays once.

The last row has bit 7 set and no other row has (3.2). That bit is the
marker, and the register takes the rest of the byte, so a source names a
target whose register ignores bit 7: `setR1`, `setR3`, `setR5`, `setR6`,
`setR8` to `setR10` and `setR13` (2.1). A source on `setR0`, `setR7` or
any other target of the fourteen is a later version's.

The shapes a tune uses are shapes and not kinds the format names (2.2):

| the source | what it sounds |
|---|---|
| one row repeating | R13 rewritten at the timer's rate, restarting the envelope: a sync buzzer |
| two rows repeating to row 0 | a volume flipped between a level and 0 at the timer's rate: a SID voice |
| many rows played once | a recording through a volume register: a digidrum |

A source holds its own values, so two SID voices at two levels are two
sources and the effect column names one of them.

## Packing the table

The table goes into the file as a DTX2 file, which DTX's writer makes
(DTX, SPEC.md 2.3). Three of its settings reach a player:

| setting | what it asks of a writer |
|---|---|
| the unit `k` | a unit of 2 asks that `R` and `RR` both divide by 2 |
| the ring | the bytes a column unpacks through, a multiple of the thirty a period holds and at most 1129 |
| the period | thirty rows, the column count, so a refill reads one period of one column |

The ring sets the state block a host finds room for, thirty rings and a
fixed part, and a smaller ring costs packed bytes. experiments.md
measures the two against each other.

## What a reader rejects

| the file | why |
|---|---|
| a version that is not `$0002` | 3.3, R6.1 |
| a source whose `C` or `W` is not 1 | 3.1 |

## Checking what you wrote

Four checks, each reading more of the file than the one above it:

```bash
bin/ymxr-trace tune.ymxr 4          # what a reader reports of it (SPEC.md 7)
bin/ymxr-bind tune.ymxr tune.bin    # bound with DTX's reader, or rejected
python3 68k/test/emu/test_ymxr.py tune.ymxr
ym/play.sh tune.ymxr                # under Hatari, with its sound on
```

The third is the one that reads the tune as a player does: it plays the
file on an emulated 68000 and holds every frame to a model of SPEC.md 4
and 5, written from the tune's own tables, so a row that states one thing
and sounds another fails there by name. `-hatari` plays it on a real MFP
(tools.md).

`doc/conformance/tunes/` holds eleven tune files and what a reader reports
of each, so a writer has both a valid file to read and the record its
rows make.

## The order to read

1. requirements.md R3, what a tune's data is.
2. SPEC.md 1, the columns, and 1.1 before the rest of them.
3. SPEC.md 2, the targets, the sources and the timers.
4. SPEC.md 3, the tune file's own bytes.
5. SPEC.md 6, the rules that bind a writer and no one else.
6. SPEC.md 4 and 5, what a player does with all of it.

glossary.md holds every term this uses, and terminology.md the machine
under them.
