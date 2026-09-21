# The conformance kit of the binaries

**1.** The kit is ten files under `files/`, of the four kinds
[BINARIES.md](../BINARIES.md) defines, and the record of each under
`records/`: 149 lines over the ten. The kit tests one document,
BINARIES.md: an implementer who has read it alone writes a reader (2),
and a record equal to the reference byte for byte shows that the
document defines the layouts.

**2. What the kit tests.** The reader of BINARIES.md 6 reads a multi
file (0), a bound tune (1), an SNDH file (3) or a TOS program (4) and
reports its parts, one line of JSON a part: the tags and their values,
the core's descriptor, the subtune table, each bound tune's fields and
sources, the images and the workspace, and for a program the PRG
header, the stub's descriptor and where the SNDH file begins. The
runtime of those files, the core's three entries (2.7 to 2.9) and the
program's run (4.6 to 4.9), is outside the record: the rig
(tools.md) tests the player and the program against a machine.

The tune file inside a multi file and the image inside an SNDH file are
bytes the record names the place and the size of: a reader of this kit
reads neither the tune format nor DTX.

**3. The files.**

| file | what it is |
|---|---|
| `TASK.md` | the task an implementer receives, with the line count of each file |
| `files/X` | the file `X`, written by the tools of the repository |
| `records/X.jsonl` | the reference record of `X` (BINARIES.md 6) |
| `MANIFEST.txt` | `# sha256  bytes  file`, then a line each for every file and every record |
| `SOURCES.md` | a row a file: its name, the tool call that wrote it, its input, its bytes, the first 16 hexadecimal digits of its sha256, and what it reaches |

The ten are written from three tunes of the other kit
(`../conformance/tunes`), `circus`, `plays-once` and `four-timers`, by
the calls `SOURCES.md` records, with the binaries of release 0.4.10
under nine of them and 0.4.11's under `armed.prg`, whose stub is of the
later descriptor version: the kit has a program of each. The music in
those tunes is its composers', and the repository's LICENSE covers the
code alone: a copy of the kit has the files in it for the exercise
alone.

**4. The exercise.** Initial condition: an implementer whose reading
excludes the three repositories and every implementation of these
files, of the tune format, of DTX and of ST4.

1. Copy `TASK.md`, `files/` and `../BINARIES.md` into a fresh
   directory. `MANIFEST.txt`, `SOURCES.md` and `records/` stay behind:
   an implementer who can check an answer is outside the exercise.
2. Name to the implementer the directories of the YMXR, YMXS and DTX
   repositories, which `TASK.md` rule 4 excludes.
3. Where several implementers run at once, each has a directory the
   others leave alone.
4. The implementer produces `layout.py`, `READ.md` and `NOTES.md` as
   `TASK.md` defines.
5. For each file `X`, run `python3 layout.py files/X` and compare its
   output byte for byte with `records/X.jsonl`, which
   `bin/ymxr-layout -silent < files/X` writes (tools.md 9.5).

**5. What passes.** All three:

1. Output: every record byte for byte the reference.
2. Sources: `READ.md` names documents alone; an implementation named
   there, in this repository, in YMXS, in DTX or on the web, fails the
   run.
3. Notes: every entry of `NOTES.md` is marked *leaves output as it is*.
   An entry marked *decides output* is a sentence the document lacks, a
   guess that matches the reference included.

**6. The runs.** The log stands here, one entry a run.

**6.1 The first run**, 2026-09-20, against the kit as it stands. The
implementer wrote a `layout.py` of 291 lines and produced the line count
of every one of the nine files; seven records came back byte for byte
and two differed in one value. `READ.md` named documents alone, and
`NOTES.md` had 13 entries with 2 marked *decides output*, so the run
failed rule 3 of 5.

- 6.4's workspace read `B the file's bytes less W`, which inside a
  program is the program's bytes: the relocation table of 4.4 step 4
  stands after the SNDH file, so the reference this repository wrote
  counted those 4 bytes into the workspace and the implementer's 32,434
  was right against 2.6's `align(2120 + 30312) + 2`. The clause reads
  `the bytes from W to the end of the SNDH file` now, 6.5 says where
  that end is, and the records of `two.prg` and `timers.prg` carry
  32,434. That was the one defect of the run.
- 6.1 read that every offset of the record counts from the file's first
  byte, `the fields of 1.2 among them`, where `table` is an offset from
  the image's first byte (1.2) and 6.3 converted the field at 16 alone.
  The implementer read the silence the way this tree writes it, so the
  output stands; 6.1 excepts `table` now and 6.3 defines it.

Eleven entries marked *leaves output as it is* carried eleven sentences
into the document: where the core's first byte is (6.4), the `~` of the
`FLAG` text against 4.5 step 2, the leading digits of a rate, the names
of `!#SN` read as 4.5 reads them, the order of 6.1's four tests, a file
that breaks a rule of 0.4 or 4.5, an image's number and the image at the
lowest offset, the image of a bound tune of a set, a text above $7E, and
in 4.5 step 2 that `##` is 4 bytes and a zero byte, which 3.2 writes and
that step read as four. The signed fields of 0.2 and the unsigned index
of 1.2 are marked in their tables.

**6.2 The second run**, the same day, against the kit with the thirteen
places of 6.1 written into it and a fresh implementer. Every record
byte for byte, all nine, and `READ.md` named documents alone.
`NOTES.md` had 10 entries with 4 marked *decides output*, so the run
failed rule 3 again; the four are readings the document settles once two
clauses are read together, and each was settled in one place.

- 6.1 sent a reader of an SNDH file to the conditions of 4.5, five of
  which read the stub, the caller's `rows` or the caller's clock rather
  than the file. Reporting the clock row would have replaced the records
  of `timers.snd` and `timers.prg` with one error line. 4.5 says which
  conditions a reader reports now.
- 6.4 gave a subtune's `at` as `its offset from the subtune table`,
  which reads as the offset the table has or as the file offset it
  names. It reads `the offset subtune i has in the subtune table, plus
  H` now.
- 6.4 put the `~` in a tag's text and named 4.5 step 2, which keeps the
  letters after it, so the two clauses read as one instruction to drop
  it.
- 6.5's `its first line left out` named either 6.1's kind line or 6.4's
  `entry` line. It names 6.1's now.

Six entries marked *leaves output as it is* carried six sentences: what
T counts in 3.1, the unsigned fields of 1.2 and 2.5, the `!#SN` names
read from the byte after the offset words, a file that meets none of the
four kinds, the line of a file outside the rules standing alone, and
the scan for the SNDH file inside a program stepping by 2
from 28.

**6.3 The third run**, 2026-09-21, against the document release 0.4.11
ships, the ten places of 6.2 written into it and a fresh implementer.
Every record byte for byte again, all nine, and `READ.md` documents
alone. `NOTES.md` had 11 entries with 3 marked *decides output*, so the
run failed rule 3; the three are two clauses of 6 pulling against the
layout they report, and one clause of 4.5 left behind by a release.

- 4.2 reads the stub's descriptor version 2, with the fields at 24, 26
  and 28 the timer is armed from, where 4.5 reported an error for a
  field at 8 other than 1 and a stub under 24 bytes. The kit's two
  programs are 0.4.10's, their stubs 24 bytes reading 1, so a reader
  that followed 4.5 as it stood reported an error line for what 4.2
  defines. The row reads
  30 bytes and version 2 now, those four conditions are the tool's
  alone, and 6.5 reads V as the file has it: a program of an earlier
  release reads 1. That was the one defect of the run.
- 6.3 read `I` as the tune's first byte for a bound tune of a set, which
  1.4 writes the field 0 for, where 3.1 patches that field in an SNDH
  file and every subtune of the kit has it patched.
- 6.3's image line stands where the field at 16 is above 0, which every
  subtune of a written file meets, so a reader that applied 6.3 whole
  inside 6.4 wrote an image line a subtune beside the images. 6.4 leaves
  that line out now.

Eight entries marked *leaves output as it is* read as the document
defines them.

**6.4 The fourth run**, 2026-09-21, against the kit at ten files, the
tenth a program of the later descriptor version, and the three places of
6.3 written into the document. Every record byte for byte, all ten, and
`READ.md` documents alone. `NOTES.md` had 11 entries with 3 marked
*decides output*, each a clause of 6.4 that reads two ways, and each
reads one way now.

- `to` reports three targets of a `bra.w`, and 6.1's rule covers an
  offset: a reader of a program that left the SNDH file's first byte off
  reported 118, 122 and 126 where the reference reads 1,906, 1,910 and
  1,914. The clause names the base now.
- The image lines read as one an offset or one a subtune naming it; the
  kit's two files of two subtunes share an image, so the two readings
  differ by a line. The clause reads one line a distinct offset.
- The text of `FLAG` was raised by the second, the third and this run:
  6.4 put the `~` in the text and cited 4.5 step 2, which keeps the
  letters after it, so the two read as one instruction. 4.5's reading is
  marked the tool's, outside the record.

Two figures the run checked against the document and reported: the
prescaler, the count and the ticks of `armed.prg` are 4.10's for 60 Hz,
2,457,600 / 240 = 10,240 = 64 x 160, and every workspace of 32,434 bytes
is 2.6's align(2120 + 30312) + 2.
