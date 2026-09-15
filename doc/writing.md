# Writing a tune file

For a writer: a tracker, a converter, or any program that produces a
tune file for a player to play. [SPEC.md](SPEC.md) defines the format;
this document fixes the order to read it in, what the format leaves to a
writer, and the checks on the file. A reader is written against
the conformance kit ([conformance/README.md](conformance/README.md)), a
player against SPEC.md 4 and 5.

---

## 1. Two routes

**1.1** [YMXS](https://github.com/odipar/YMXS)'s SPEC.md defines what a
tune is: rows of registers and effects, what each register reaches, how
a rate is reckoned, and the rules a writer satisfies. SPEC.md here
defines one encoding of that structure.

**1.2** A writer whose tune is in the structure emits it as YMXS JSON
(YMXS, json.md) and runs `bin/ymxs-to-ymxr`, which encodes the columns
([ymxs.md](ymxs.md) 5) and packs the table; that writer reads sections 4
and 5 of this document alone.

**1.3** A writer that produces the columns itself reads the whole of this
document and SPEC.md, and produces the file of section 2.

---

## 2. What a writer produces

**2.1** A tune file (SPEC.md 3.3): the header, the tune's table as a
DTX2 file, the source index, and one DTX1 table a source. The file has
tables alone; a tool binds it with DTX's reader into the layout a player
reads (BINARIES.md 1).

---

## 3. The table

**3.1** One row a frame, at the rate in the header (SPEC.md 3.3). Thirty
columns (SPEC.md 1): 0 to 13 reach R0 to R13 in the chip's numbering, 14
to 29 are four effects of four columns each.

**3.2** Bit 7 of a column is its set bit: at 1 the row sets the column,
at 0 the value's bits are arbitrary (SPEC.md 1.1). Nine columns fill
their byte and reserve 0 for a row that leaves them unset; each has a bit
beside it in another column that marks 0 as a value, listed in SPEC.md
1.1. SPEC.md 1.2 to 1.7 define each register column.

**3.3** A row with every column unset leaves every register as it is and
every timer running, so a writer sets a value on the row it changes and
leaves the rows between unset.

---

## 4. The effects

**4.1** Four columns an effect: the target, the source, the timer's
control and its count (SPEC.md 1.8, 1.9). A row starts an effect by
setting the source column to the source's number, 1 to 127, and stops it
by setting the column to `$80`: the set bit with source 0 under it. A
source column of 0 is unset (SPEC.md 1.1).

**4.2** The control column has the prescaler select in bits 2 to 0, the
timer's reset in bit 6, the place's reset in bit 5, and in bit 4 the
mark that a count column of 0 is the value 256 (SPEC.md 1.9).

**4.3** SPEC.md 2.3 assigns Timers A, D, B and C to effects 0 to 3; a
writer selects an effect, and its timer is that of the assignment. A
tune running one effect sets columns 14 to 17 and leaves 18 to 29 unset
for its whole length.

**4.4 SPEC.md 6 binds the writer.** Its rules fix which columns a row
leaves unset while an effect runs, which effects a row may name, and
which bits a row that starts a source sets with it. A player assumes the
rules and writes each set column as the row has it (YMXS, SPEC.md 6.1);
a file that breaks one plays, and a check of the structure reports the
breach as a warning (tools.md 8.2). Each rule names the YMXS rule it
encodes; YMXS, SPEC.md 6 defines those in the structure.

---

## 5. The sources

**5.1** A source is a DTX1 table of `R` rows and one column of one-byte
values (SPEC.md 3.1): `C` is 1 and `W` is 1 at this version, and a reader
rejects a source of another shape. `RR` is the row it repeats to; where
`RR` equals `R` the source plays once.

**5.2** The last row has bit 7 set, alone among the rows (SPEC.md 3.2).
That bit is the marker, and the register reads the rest of the byte, so
a source names a target whose register ignores bit 7: `setR1`, `setR3`,
`setR5`, `setR6`, `setR8` to `setR10` and `setR13` (SPEC.md 2.1). A source
on `setR0`, `setR7` or any other target of the fourteen belongs to a later
version (SPEC.md 2.1, R6.2).

**5.3** The format leaves the kind of a source to its shape (SPEC.md
2.2); the sound follows from the shape:

| the source | the sound |
|---|---|
| one row repeating | R13 rewritten at the timer's rate, restarting the envelope: a sync buzzer |
| two rows repeating to row 0 | a volume moving between a level and 0 at the timer's rate: a SID voice |
| many rows played once | a recording through a volume register: a digidrum |

**5.4** The values belong to the source: two SID voices at two levels are
two sources, and the effect column names one of them.

---

## 6. Packing the table

**6.1** The table goes into the file as a DTX2 file, which DTX's writer
packs (DTX, SPEC.md 2.3). Three of its settings reach a player:

| setting | what it requires |
|---|---|
| the unit K | `R` and `RR` divide by K, DTX's `k` (DTX R5.6 and R5.11); rule 6 of SPEC.md 6 lengthens a tune until they do |
| the ring | the bytes a column unpacks through: a multiple of thirty, at least 60 and at most 1,110 |
| the period | thirty rows, the column count |

**6.2** The ring sets the size of the state block a host allocates,
thirty rings and a fixed part; a smaller ring packs to more bytes
(experiments.md).

---

## 7. What a reader rejects

| the file | clause |
|---|---|
| shorter than 16 bytes, or other than `YMXR` at offset 0 | SPEC.md 3.3 |
| a version other than `$0003` | SPEC.md 3.3, R6.1 |
| a source whose `C` or `W` is other than 1 | SPEC.md 3.1 |
| a table or a source whose offsets lie outside the file | SPEC.md 3.3; the Go reader, where the Java reader leaves this unchecked (tools.md 20.5) |

A reader reports a rejected file (SPEC.md 7) and produces zero entries.

---

## 8. Checking a file

**8.1** Four checks:

```bash
bin/ymxr-trace -r4 < tune.ymxr        # the reader's record (SPEC.md 7)
bin/ymxr-bind < tune.ymxr > tune.bin  # bound with DTX's reader, or rejected
python3 68k/test/emu/test_ymxr.py tune.ymxr
ym/play.sh tune.ymxr                  # under Hatari, with its sound on
```

**8.2** The third plays the file on an emulated 68000 and checks every
frame against a model of SPEC.md 4 and 5 built from the tune's tables,
and names the frame and the register that differ; `-hatari` plays it on
a real MFP (tools.md 18.1).

**8.3** `doc/conformance/tunes/` has eleven tune files: ten a reader
reads, and `wrong-version.ymxr`, version `$0004`, which a reader rejects
(7); `MANIFEST.txt` names the record of each by its sha256 and size, the
rejected one's empty.

---

## 9. The order to read

1. requirements.md R3, what a tune's data is.
2. SPEC.md 1, the columns, and 1.1 before the rest of them.
3. SPEC.md 2, the targets, the sources and the timers.
4. SPEC.md 3, the bytes of the tune file.
5. SPEC.md 6, the rules that bind a writer.
6. SPEC.md 4 and 5, what a player does with all of it.

[glossary.md](glossary.md) lists every term this uses, and
[terminology.md](terminology.md) describes the machine the terms name.
