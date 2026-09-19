# The conformance kit

**1.** The kit is 15 tunes under `tunes/`, each a `.ymxr` (SPEC.md 3.3)
beside its `.rows` (3), and a reference record of each (SPEC.md 7):
18,783 entries over the 15 tunes, one line each, the first line of each
record among them. The kit tests the two documents,
[SPEC.md](../SPEC.md) and YMXS's `doc/SPEC.md`: an implementer who has
read them alone writes a reader (2), and a record equal to the reference
byte for byte shows that the documents define it.

**2. What the kit tests.** The reader of SPEC.md 7 and requirements.md
R2.4 reads a tune file and reports one entry a frame, each write
recorded in place of the chip; the ticks of section 5 are outside the
record (YMXS, SPEC.md 7.1), so a reader runs on any machine. The kit
tests a reader; the rig (7) tests the player against the same record.

**3. The files.**

| file | what it is |
|---|---|
| `TASK.md` | the task an implementer receives, with the line count of each tune |
| `tunes/X.ymxr` | the tune file of tune `X` |
| `tunes/X.rows` | the tune's table as DTX0 (DTX, SPEC.md 2.1): a 16-byte header, `R` at bytes 4 to 7, `C` = 30 at 8 and 9, `RR` at 10 to 13 and `W` = 1 at 14, then `R` rows of 30 bytes; empty for `wrong-version` |
| `X.jsonl` | the reference record of tune `X`, outside the kit: `MANIFEST.txt` records its sha256 and bytes |
| `MANIFEST.txt` | `# sha256  bytes  file`, then a line each for `tunes/X.ymxr`, `tunes/X.rows` and `X.jsonl` of every tune |
| `SOURCES.md` | a row a tune: its name, the dump or builder it comes from, the dump's title and author, the converter's flags, its bytes, the first 16 hexadecimal digits of its sha256, and what it reaches |

Nine tunes are converted from eight dumps under `ym/test`: six of
published music, by Jochen Hippel, Mad Max and Scavenger as their
headers record, one of them converted twice (`circus`, `plays-once`),
and two built (`preempt`, `retune`); `four-timers`, `voices`, `envelope`
and `counted` are built by code and `wrong-version` is `chambers` with
the version word $0007. The music is its composers', and the
repository's LICENSE covers the code alone: a copy of the kit has the
tunes in it for the exercise alone.

**4. The exercise.** Initial condition: an implementer whose reading
excludes the three repositories and every implementation of the format,
of YMXS, of DTX and of ST4.

1. Copy `TASK.md`, `tunes/`, `../SPEC.md` and YMXS's `doc/SPEC.md` into a
   fresh directory, the YMXS copy named `YMXS-SPEC.md`, the name `TASK.md`
   cites. `MANIFEST.txt`, `SOURCES.md` and the references stay behind:
   an implementer who can check an answer is outside the exercise.
2. Name to the implementer the directories of the YMXR, YMXS and DTX
   repositories, which `TASK.md` rule 2 excludes. Note: a checkout is at
   a separate path on every machine. An implementer that reads a
   project's files as it starts - a house style, a memory, a status of
   the tree - has read something of the family before rule 1 reaches it;
   the first run listed that in `READ.md` rather than ruling it out, and
   none of it defines the format.
3. Where several implementers run at once, each has a directory the
   others leave alone.
4. The implementer produces `decode.py`, `READ.md` and `NOTES.md` as
   `TASK.md` defines.
5. For each tune `X` with the line count `L` of `TASK.md`, run `python3
   decode.py tunes/X.ymxr tunes/X.rows L` and compare its output byte for
   byte with the reference: `bin/ymxr-trace -silent < tunes/X.ymxr`, or
   the sha256 and bytes `MANIFEST.txt` records for `X.jsonl`. For
   `wrong-version` the tool's output is empty and its exit is 1; the
   reference is the empty output.

**5. What passes.** All three:

1. Output: every record byte for byte the reference, the empty record of
   `wrong-version` included.
2. Sources: `READ.md` names documents alone; an implementation named
   there, in this repository, in YMXS, in DTX or on the web, fails the
   run.
3. Notes: every entry of `NOTES.md` is marked *leaves output as it is*.
   An entry marked *decides output* is a sentence the documents lack, a
   guess that matches the reference included.

**5.1 The first run**, 2026-09-18, against the kit at 13 tunes. The
implementer wrote a `decode.py` of 177 lines and produced every record
byte for byte, the empty record of `wrong-version` included, and
`READ.md` named documents alone. `NOTES.md` had 18 entries and marked 7
*decides output*, so the run failed rule 3. Those 7 carry the worth of
the run: six clauses of SPEC.md were reworded for them.

- 4.1 step 1 read "a version other than 3" where 3.3.5 reads 3 and 4, so
  a reader following it wrote an empty record for both version 4 tunes,
  290 lines. That clause was the one defect of the run.
- 3.1.3 named DTX's SPEC.md for the stride of a source's columns, a
  document the kit leaves out, so the implementer recovered the stride
  from the files: at a stride of R the table of one source leaves a byte
  3.3.1 requires to be 0. 3.1.3 defines the stride now.
- 3.1.2's header table read C as 1 and its payload as R bytes, the
  layout of a source of one column, against 3.1.3.
- 7.2's `repeat` is the RR of the header, and the record of the
  structure writes `null` for the same key on a source that plays once
  (YMXS, SPEC.md 7.3).
- 7.3's `source` read "the last start or stop", where a stop is a source
  column set to 0 in the Terms and stopping the timer in 4.3 step 1, and
  4.3 step 4's "write it and keep it" read as keeping a count of 0. Row
  42 of `four-timers` is the one row of the kit under both.

The seventh entry read 7.3's mask of a register's width as
underspecified, where the clause defines it; that clause stands.

**5.2 The second run**, the same day, against the kit with the six
clauses of 5.1 written into it and a fresh implementer. Every record byte
for byte again, `READ.md` documents alone, and `NOTES.md` 24 entries with
9 marked *decides output* - of a different kind from the first run's: 2
are choices the documents leave open and 7 are readings the documents
settle where two clauses are read together. None of the six clauses of
5.1 was read wrong, which the rewording was for.

Three clauses changed for it.

- 3.1.2's payload row, which 5.1 had written as C times align(R), is a
  byte too long for an odd R: `dtx-write` reports a DTX1 table of 21, 27
  and 33 bytes for C of 1, 2 and 3 at R = 5, so the last column of a
  table has no padding after it and the payload is (C - 1) times
  align(R) plus R. The run caught a defect the fix of the first run had
  put there.
- 4.3 step 4 said what the kept count becomes on a row that writes one
  and left the rest to the reader, while 7.3 reports the kept count on
  every row. The step covers every row now, in one condition rather than
  two branches.
- `TASK.md` 2 asked for "at most `<lines>` lines" without saying whether
  the figure counts the lines or the frames of 7.4, which differ by the
  first line. It says the lines.

**5.3 The third run**, the same day, against the kit with the clauses of
5.1 and 5.2 in it and a third implementer. Every record byte for byte
again, and `NOTES.md` 17 entries with 8 marked *decides output*. Every
clause either earlier run wrote was read as it stands. Two changed for
it, both of them places where a second reading stands in the document
itself.

- 7.2 fixed the rows of a source by row and left the order inside a row
  to inference, where 2.1.1 defines another order for the same bytes: a
  tick writes the columns with the marker's column last. A reader
  following the tick reports the columns of `voices` source 1 as 0, 2, 1.
  7.2 names column order and says which of the two it is.
- 7.4 read that the record of a file with an error of 3.3.4 is empty,
  while 3.3.4 has a reader report a line for that error, and where the
  line stands was left open. It stands outside the record.

The third entry that changed bytes was whether a row setting a column to
the value the register keeps is reported, which 1.1.1 allows and 7.3
reports through 4.4; the reading that passes is the one the clauses have,
and they stand.

**5.4 The fourth run**, the same day, against the kit at 14 tunes, with
`counted` in it and a fourth implementer. Every record byte for byte,
`wrong-version` included; `READ.md` named documents alone; and `NOTES.md`
had 10 entries with 1 marked *decides output*, so the run failed rule 3
on that one. Version 5, bit 31 of an index entry and the layout that puts
a tune's DTX1 tables before its image were read as they stand, and the
reader reached the counted source of each of the six targets. Two clauses
changed:

- 7.3 defined `source` as the number the last row set the column to and
  then named two events that leave it as it is, neither of them a stop
  through the source column. The rule covers that stop, since the number
  the row sets is 0, and the two exceptions invite the reading that a
  stop keeps the number of the source it stopped: 228 rows of these tunes
  set the source column to 0 over an earlier 1 to 127. The clause names
  the stop now.
- 3.1.1 reads an index entry as bits 30 to 0 the offset and bit 31 the
  mark of a counted source, where a reader of a version 3 or 4 file could
  read the four bytes as one offset, since 3.1.6 keeps bit 31 at 0 there.
  The clause reads "whichever the version" now.

**5.5 The fifth run**, 2026-09-19, against the kit at 15 tunes, with
`envelope-counted` in it and a fifth implementer. Every record byte for
byte, `wrong-version` included; `READ.md` named documents alone; and
`NOTES.md` had 20 entries with 7 marked *decides output*, so the run
failed rule 3 on those. Version 6, the counted source of several columns,
and rule 2(f)'s bits 7 and 6 on a counted source of `setR7` were read as
they stand. Seven places changed:

- The Conventions read "an offset counts from the first byte of the file,
  and an offset *on a long* divides by 4", where "divides by 4" reads as
  a unit as well as an alignment and no clause fixes the unit of a source
  index entry. A unit of longs puts the first source of `counted.ymxr` at
  byte 6,352 of a 1,756-byte file. An offset counts bytes now, and one on
  a long is a multiple of 4.
- 3.1.2's row-layout cell gave the size of a source's rows and left where
  a column begins to 3.1.3, which cites DTX's SPEC.md for the stride, a
  document outside the exercise. The cell has the offset of row n of
  column i now. Read row by row, source 2 of `voices.ymxr` reports
  `[4,9,14,19,152,0,15,12,10,8]` for `[4,15,9,12,14,10,19,8,152,6]`.
- 1.1.2's table assigned the nine marking bits and 1.1.4 alone said that
  five of them are read on every row and four where the row sets the
  control column. The table has the condition a row now, and 1.1.4 reads
  it off the table. 872 rows of nine tunes leave column 13 unset with bit
  6 or bit 5 at 1.
- 1.9.1 read "the count column is the count, the byte written to the
  timer's data register", where a reader carrying the set bit of 1.1.1
  across reports bits 6 to 0. The clause reads 0 to 255 now, as 3,368
  count columns of six tunes are above 127.
- 7.3 defined the effects of `e` twice, as "the effects the row sets a
  column of" and as those whose target, source or control column is set
  or whose count column is other than 0. The second stands alone now.
- 7.3's four numbers read as the row's four columns, each being the kept
  value: 3,574 starts leave the target column unset and 1,685 starts in
  `synergy.ymxr` leave the control column unset. The clause defines the
  four as kept values before it reads them one by one, and a Note records
  that the width of a register column drops the marking bits of 1.1.2.
- 7.4 put the line of an error of the file "outside the record" without
  saying where it goes, which decides the output of `wrong-version.ymxr`
  on one stream. A reader that writes the record to a stream writes that
  line to another.

8.3 changed beside them: it left a source whose RR is above R to a later
version and left a tune's table with RR above R unnamed, where 3.3.3
defines that RR as the repeat row or R. It names both now.

**5.6 The sixth run**, the same day, against the kit at 15 tunes with the
clauses of 5.5 in it and a sixth implementer. Every record byte for byte,
`wrong-version` included, with the line of 3.3.4 on a separate stream;
`READ.md` named documents alone; and `NOTES.md` had 17 entries with 7
marked *decides output*, so the run failed rule 3 on those. The seven
stand where a rule is spread over clauses rather than where a clause is
wrong: five of the seven cite section 7, and the reader read every one of
them as the kit has it. Seven places changed:

- 1.1.2's table reads as though the marking bit were the set bit of the
  column beside it, which 1.1.3 defines otherwise: three rows of
  `four-timers` mark a byte other than 0. The clause reads that the bit
  marks a 0 alone.
- 7.3 read "bits 6 to 0 masked to the register's width", where the two
  readings part on the twelve rows of five tunes that set a column with a
  marking bit in it: `chambers` row 0 has column 13 at `$EA`, which the
  width reports as 10 and bits 6 to 0 as 106. The rule reads the byte
  masked to the width.
- 7.3 read the keys of `w` off 4.4 and left a column the row leaves unset
  to 1.1.1 by way of that clause. Twelve rows of `four-timers` write a
  value into an unset column, and row 8 reports `{}` where a reader keyed
  on the byte reports two registers and an effect. The clause reads that
  such a register is absent.
- 7.2 gave `rows` an order and left its shape to the count in the sentence
  after it, so a list of rows read as well as one flat list: three records
  of the kit differ between the two. `rows` is one list of C times R
  integers now.
- 3.1.2's offset of row n of column i reads align(R), which 3.1.3 defined
  a clause later. The definition stands beside the offset now and 3.1.3
  cites it.
- 7.2 left what a reader does with a source whose bit 7 stands before its
  last row, which a reader following 3.2.2 cuts at the first marker:
  source 2 of `four-timers` reads 2 values in place of 40. 7.2 reads
  every row of the table.
- 7.2 left whether the mark of a counted source reaches the record. A
  counted source and a marked one report alike now, and bit 31 of an
  index entry is absent from it.

Three clauses changed beside them. 2.1.2 named R7 among the registers
that read every bit of their byte while 4.4 reads R7 as six, which 1.4.2
and rule 2(f) reconcile: the clause reads the two ports' directions in
bits 7 and 6. 4.1 step 4 set the kept target, select and count of an
effect and left the kept source, which 7.3 reports from the first frame.
7.4 read that the line of an error of the file goes to another stream and
left which to the reader; the host names it.

`TASK.md` 4 read that every tune satisfies SPEC.md 6, where source 2 of
`four-timers` is the condition 6.4 reports, and it repeated two rules of
SPEC.md that the clauses above now define: it describes the tunes and
leaves the rules to the documents.

**6. What each tune reaches.**

| tune | what it reaches |
|---|---|
| `chambers` | zero effects; R13 written once, with both envelope-period-0 bits beside it |
| `circus` | four frames, fewer than the DTX2 table's period `P` of 30 rows (DTX, abi.md 1) |
| `plays-once` | four frames whose `RR` is `R`: the frame after the last row reports -1 |
| `turrican` | three drums on Timer D, each ending by its marker; `RR` at 160, a loop longer than the ring replayed at its exact rows; R13 rewritten |
| `turrican-2` | a loop of one row from an odd `RR`, so a row of unset columns (SPEC.md 6, rule 6) is inserted before it and the loop's row is written twice, and the table packs at unit 2; six drums before it, the last stopped by a row |
| `synergy` | nine SIDs on two timers at once, six of them named by both; the select changed with the source kept, and the count alone; a running source stopped by a row; a tone fine byte 0 with the coarse bit beside it; an odd row count, so a row of unset columns is appended at the end and the table packs at unit 2 |
| `preempt` | a drum starting on the voice a SID runs on stops the SID first; R8 passed between them with its column unset, written by ticks alone |
| `retune` | a one-row buzzer source on R13, restarted over a running timer with a new rate; select 7 |
| `fine-zero` | a tone fine byte moving to 0 on each voice, with the coarse set bit set and with it clear; an odd row count, so a row of unset columns is appended at the end and the table packs at unit 2 |
| `four-timers` | all four effects on Timers A, D, B and C at 60 Hz, and rows section 4 allows beyond those a conversion of a dump produces: a count or a select alone, bit 5 alone, bit 6 alone, a stop with the volume set, the same source again, a target set while running and read at the next start, a target other than a volume register, a drum closing on 5, its source with bit 7 set in nine rows before its last, outside SPEC.md 3.2 and reported as the table has it (SPEC.md 7), R13 set beside a buzzer, a source repeating to its row 2, values under a clear set bit |
| `voices` | version 4 (SPEC.md 3.3.5): the four kinds of target that write several registers, one an effect at 50 Hz, and the marker in a different column under each - the coarse nibble of a voice on Timer A and of a tone on Timer C, the noise period on Timer D, the envelope shape of a buzzer on Timer B; a source of several columns repeating to row 0, one repeating to a row above it, one that plays once and stops its timer at its marker, and a start over a running source of the same row count that leaves the place where it stands |
| `envelope` | version 4 (SPEC.md 3.3.5): `setEnvelope`, the one target whose marked register reads eight bits, so the marker's column is the envelope period's high byte, 0 to 127, and the column beside it a whole byte; a source of two columns repeating to a row above 0, one that plays once and stops its timer at its marker with 127 in a row of its marked column, a start that changes the source on a running timer, and the envelope shape set from column 13 while the period ticks |
| `counted` | version 5 (SPEC.md 3.3.5): a counted source on each of the six targets whose register reads every bit of its byte - `setR0` and `setR4` on Timer A, `setR7` on Timer D, `setR11` on Timer B, `setR12` and `setR2` on Timer C - each with rows whose bit 7 is set, where a source the marker ends reads that bit as its end; an index entry whose bit 31 is 1 and whose bits 30 to 0 are the offset (SPEC.md 3.1.1); a square on R8 in the same file, which the marker ends, so a player reads the end of a source from its index entry rather than from the version word; a counted source of one row, one that plays once and stops its timer at its count, one repeating to a row above 0; a target set while an effect runs and read at the next start; a row that sets R12 as it stops the effect running on it |
| `envelope-counted` | version 6 (SPEC.md 3.3.5): a counted source of two columns on `setEnvelope`, whose two registers read every bit of their byte, so its rows are whole bytes and a tick counts them; the high byte runs past 127, where a marked source of that target stops, and reaches 255, an envelope period of 65,323; one source repeating to a row above 0 and one that plays once and stops its timer at its count, a start that changes the source on a running timer, and the envelope shape set from column 13 while the period ticks |
| `wrong-version` | the version word $0007: a reader produces an empty record |

`SOURCES.md` records where each tune comes from and what it reaches in
full.

**7. How the kit is kept true.**

1. `ConformanceTest` builds every tune from its `SOURCES.md` row under
   `mvn test` and requires the `.ymxr`, the `.rows` and `MANIFEST.txt`
   byte for byte; a difference fails the test. A tune or rows file
   absent from the kit is written; a `SOURCES.md` row or a
   `MANIFEST.txt` that differs is written beside the kit as
   `SOURCES.generated.md` or `MANIFEST.generated.txt`.
2. The reference is the record of `bin/ymxr-trace` (SPEC.md 7).
3. `68k/test/emu/test_ymxr.py -kit` plays every tune but `wrong-version`
   on the 68000 player and requires the player's frames, the rig's model
   of section 4 and the reference to agree.
4. `ConformanceTest` replays every tune converted from a dump against the
   dump through `ymxr-check` at its options.
