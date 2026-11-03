# Writing a tune file

How to produce and check a tune file from a tracker or converter.
[SPEC.md](SPEC.md) defines the format; this document covers the writer's
choices and reading order. For a reader, use the
[conformance kit](conformance/README.md); for a player, SPEC.md 4 and 5.

---

## 1. Two routes

**1.1** [YMXS](https://github.com/odipar/YMXS)'s SPEC.md defines tune
data, chip values, rates and writer rules. SPEC.md here defines an
encoding of that structure.

**1.2** To convert a tune in the structure, emit YMXS JSON and run
`bin/ymxs-to-ymxr` to encode and pack it ([ymxs.md](ymxs.md)). Read
sections 4 and 5 for effects and sources.

**1.3** To write columns directly, read this document and SPEC.md in
full, then produce the file of section 2.

---

## 2. What a writer produces

**2.1** A tune file (SPEC.md 3.3): the header, the tune's table as a
DTX3 file, the source index, one DTX1 table a source and, in version 10,
the transformation index and the transformations (SPEC.md 3.4). The file
has tables alone; a tool binds it with DTX's reader into the layout a
player reads (BINARIES.md 1).

---

## 3. The table

**3.1** One row a frame, at the rate in the header (SPEC.md 3.3). Thirty
columns, and thirty-four in version 10 (SPEC.md 1): 0 to 13 reach R0 to
R13 in the chip's numbering, 14 to 29 are four effects of four columns
each, and 30 to 33 are the mapping columns of the four effects (SPEC.md
1.10).

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
control and its count (SPEC.md 1.8, 1.9); in version 10 a fifth, the
mapping column, column 30 + i of effect i (SPEC.md 1.10). The target
column selects a target of SPEC.md 2.1: a register, 0 to 13, or a target
of two or three registers, 14 to 24. A row starts an effect by setting
the source column to the source's number, 1 to 127, and stops it by
setting the column to `$80`: the set bit with source 0 under it. A
source column of 0 is unset (SPEC.md 1.1).

**4.2** The control column has the prescaler select in bits 2 to 0, the
timer's reset in bit 6, the place's reset in bit 5, in bit 4 the mark
that a count column of 0 is the value 256, and in bit 3 a deferred start
(SPEC.md 1.9, 4.8).

**4.3** SPEC.md 2.3 assigns Timers A, D, B and C to effects 0 to 3, so
a writer selects a timer by selecting an effect. A tune running effect 0
alone sets columns 14 to 17 and leaves 18 to 29 unset for its whole
length; in version 10 it leaves 31 to 33 unset as well, and sets column
30 on the rows SPEC.md 6, rule 2(g) lists.

**4.4 SPEC.md 6 binds the writer.** Its rules fix which columns a row
leaves unset while an effect runs, which effects and numbers a row sets,
and which bits a row that starts a source sets with it. A player assumes
the rules and writes each set column as the row has it (SPEC.md 6.1), so
a file that breaks one plays as its columns read. Rules 1, 3 and 4
encode rules of YMXS, SPEC.md 6, and a converter reports a breach of a
YMXS rule in the structure as a warning (tools.md 7.2); rules 2, 5 and 6
are this format's.

---

## 5. The sources

**5.1** A source is a DTX1 table of `R` rows, a column for each register
its target writes, and a byte a value (SPEC.md 2.2, 3.1.3): `C` is 1, 2
or 3, the registers its target writes, and `W` is 1. `RR` is the row it
repeats to; where `RR` equals `R` the source plays once.

**5.2** A source ends on its marker or on its count. A marked source
sets bit 7, the marker, on its last row and clears it on every other row
(SPEC.md 3.2), in the column SPEC.md 2.1 assigns its target: a column of
a register that reads seven bits or fewer. A target of one register
that runs a marked source is `setR1`, `setR3`, `setR5`, `setR6`, `setR8`
to `setR10` or `setR13`.

**5.3** A target of one register that reads eight bits, `setR0`,
`setR2`, `setR4`, `setR7`, `setR11` or `setR12`, runs a counted source:
every bit of a row is a value, bit 31 of its index entry is 1, and a
tick counts `R` rows (SPEC.md 3.1.6). A counted source on `setR7` sets
bits 7 and 6 of every row, the directions of the two ports (SPEC.md 6,
rule 2(f)). A source of two columns on `setEnvelope` is counted as well;
versions 4 and 5 wrote the marked form in its place, the marker in bit 7
of R12's column (SPEC.md 2.1.3, 2.1.4). Targets 25 upward are left
to a later version (SPEC.md 2.1, R6.2).

**5.4** A writer writes version 9, or 10 where a row maps a value
(SPEC.md 3.3.5): its table is DTX3, which a player of version 6 or before
reads wrongly, bit 3 of a control column is a deferred start, which a
player of version 7 reads as 0, and version 9 encodes every source of 5.1
to 5.3. Version 10 adds a mapping column an effect and the
transformations they number (SPEC.md 1.10, 3.4). Versions 3 to 6 had a
DTX2 table.

**5.5** The shape of a source sets its sound (SPEC.md 2.2):

| the source | the sound |
|---|---|
| one row repeating | R13 rewritten at the timer's rate, restarting the envelope: a sync buzzer |
| two rows repeating to row 0 | a volume moving between a level and 0 at the timer's rate: a SID voice |
| many rows played once | a recording through a volume register: a digidrum |

**5.6** Without a lookup, source values are written unchanged. Different
sample levels then require different sources. An optional transformation
can change those levels while reusing the source.

**5.7 Transformations.** A source value becomes a lookup key: key k
selects entry k of the chosen transformation. A mapping selects the
lookup; mapping 0 is identity. YMXR can transform only the target's last
value: volume on `setVoiceX` or `setNoiseX`, shape on `setBuzzer`, the coarse
nibble on `setToneX`, the high byte on `setEnvelope`, or the sole value on
`setRn`. Values follow source-column order; earlier mapping parts must
be identity.

For sample volume, define `Vn[k] = floor(k * n / 15)` for keys 0 to 15,
with n from 0 to 15. This scales register codes; the chip's output
levels are close to logarithmic (terminology.md 1). Start a sample on
`setR8` through `Vn`, or a `setVoiceA` source through `[identity,
identity, Vn]`. A fade remaps through successively lower levels while
the sample continues from its current place. The same source can play on
separate timers at different levels. For 8-bit sample keys, use a table
of 256 entries that converts each key to a YM volume. Transformations
can also change envelope shapes or noise-effect volume; they do not
change timing, source length or repeat position.

Write the mapping column with its set bit when a start changes the kept
mapping, or a remap changes the running mapping. A remap leaves the source
column unset. Byte `$00` preserves the kept mapping; `$80` explicitly
selects identity. Stops leave the mapping column unset. A deferred start
captures its mapping; subsequent remaps affect current ticks, and the
pending start retains its snapshot (SPEC.md 6, rule 2(g)).

Keys must fit both the transformation and the source encoding:

| source column | key range | encoding |
|---|---|---|
| marker column of a marked source | 0 to 127 | key in bits 6 to 0, marker in bit 7 |
| `setR7` | 0 to 63 | key k stored as k + 192, port directions set |
| any other column | 0 to 255 | whole byte |

A one-column source used only through transformations is counted when a
key reaches 128; otherwise that bit would conflict with its marker
(SPEC.md 3.1.6). A writer checks every key against the selected table's
last entry and preserves marker and port bits in the output
(SPEC.md 2.1.5).
A row that sets a mapping column to a transformation, 1 to 127, makes the
file version 10 (5.4).

---

## 6. Packing the table

**6.1** The table goes into the file as a DTX3 file, which DTX's writer
packs (DTX, SPEC.md 2.4): a constant column is its value, and each live
column is packed with ST4. Three of its settings reach a player:

| setting | what it requires |
|---|---|
| the unit K | `R` and `RR` divide by K, DTX's `k` (DTX R8.9); rule 6 of SPEC.md 6 lengthens a tune until they do |
| the ring | the bytes a live column unpacks through: a multiple of thirty, at least 60, and at most 32,767 divided by the live columns less one; in version 10 a multiple of thirty-four, at least 68 |
| the period | thirty rows, the column count; thirty-four in version 10 |

**6.2** The ring sets the size of the state block a host allocates, a ring a
live column and a fixed part; a smaller ring packs to more bytes
(experiments.md), so the reference writer chooses the ring whose table and
state block come to the least (SPEC.md 3.3.3). The subtunes of a set share
one workspace, sized for the largest state block among them, so the writer
chooses their rings for the set: a tune packs through a wider ring where its
state block stays within the largest (tools.md 4.7).

---

## 7. What a reader rejects

A reader rejects a file with a condition of SPEC.md 3.3.4, reports the
first (SPEC.md 7) and produces an empty record. Among them: a version
other than 7, 8, 9 and 10, versions 3 to 6 among them, whose table is
DTX2, and a source other than one, two or three columns of one byte.

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
and reports the frame and the register that differ; `-hatari` plays it on
a real MFP (tools.md 17.1).

**8.3** `doc/conformance/tunes/` has seventeen tune files: sixteen a
reader reads, fifteen of version 9 and one of version 10, `mapped.ymxr`,
and `wrong-version.ymxr`, version `$000B`, which a reader rejects (7);
`MANIFEST.txt` lists the sha256 and the size of the record of each, the
rejected one's empty.

---

## 9. The order to read

1. requirements.md R3, what a tune's data is.
2. SPEC.md 1, the columns, and 1.1 before the rest of them.
3. SPEC.md 2, the targets, the sources and the timers.
4. SPEC.md 3, the bytes of the tune file.
5. SPEC.md 6, the rules that bind a writer.
6. SPEC.md 4 and 5, how a player plays a tune.

[glossary.md](glossary.md) lists every term this uses, and
[terminology.md](terminology.md) describes the machine the terms name.
