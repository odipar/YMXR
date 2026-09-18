# The conformance kit

**1.** The kit is 13 tunes under `tunes/`, each a `.ymxr` (SPEC.md 3.3)
beside its `.rows` (3), and a reference record of each (SPEC.md 7):
18,573 entries over the 13 tunes, one line each, the first line of each
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
and two built (`preempt`, `retune`); `four-timers`, `voices` and
`envelope` are built by code and `wrong-version` is `chambers` with the
version word $0005. The music is its composers', and the repository's
LICENSE covers the code alone: a copy of the kit has the tunes in it for
the exercise alone.

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
| `wrong-version` | the version word $0005: a reader produces an empty record |

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
