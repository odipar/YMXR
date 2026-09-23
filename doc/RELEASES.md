# RELEASES

What a release contains stands here, and each one published is listed
below it.

## What a release contains

`release/publish.sh` writes `dist/release` (tools.md), and
`release/manifest.sh` the manifest in it:

- one zip a platform, over six: Windows, macOS and Linux, each on x64 and
  arm64. A zip contains the twelve tools as executables, and each
  executable contains the nine 68000 binaries of BINARIES.md and DTX's
  twenty-two images, so one converts a dump and writes a program on a
  machine where neither this repository nor a toolchain is installed
- `MANIFEST.txt`: every zip's size and sha256, what it contains, and the
  source commit the release was built from

The version names every file. It is read out of `pom.xml`, or stands as
the script's one argument.

The nine binaries are assembled from `68k/` by rmac on the machine that
cuts the release, so the caller's machine has an assembler to install or
not as it pleases. They are committed under `go/binaries/data`: a Go module
fetched by its import path contains the files a commit has in it, and an
executable built from one embeds these. `BinariesTest` reads them against
the assembly the build makes, so one that does not match what rmac writes
today fails the build.

A player pins a version of this format: a tune file is 3, 4, 5 or 6
(SPEC.md 3.3.5) and a bound tune the version of the tune file bound
(BINARIES.md 1), a writer writes the lowest version a tune reads under,
and a release's number names the tools rather than either.

## Published

### 0.4.15, 2026-09-23

<https://github.com/odipar/YMXR/releases/tag/v0.4.15>, built from the commit
tagged `v0.4.15`.

The nine 68000 binaries are 0.4.11's bytes: a comment in `YMXR_prg.S` is
the one line under `68k/` that has moved. An SNDH file records how long
each subtune plays, and a file cut short records the same in both trees.

- **The SNDH file records each subtune's seconds.** The tag block ends
  with `TIME` before `HDNS`: a word a subtune, the frames of a tune that
  plays once divided by the rate of the set, rounded up and at most
  65,535, and 0 for one that repeats, as `FRMS` records it (BINARIES.md
  3.2). In SNDH, 0 in either tag marks a tune that loops without end, and
  rounding up keeps a tune under a second at 1. `ymxr-prg` and
  `ymxr-layout` read the tag, and `ymxr-layout` records it as `seconds`
  (BINARIES.md 6.4).
- **A rate of 0 is an error.** A tune file whose rate word is 0 made
  `ymxr-sndh` write an SNDH file naming `TC0`; it now reports `subtune 1
  plays at 0 Hz: an SNDH file records a rate of 1 Hz or more`, exit 1
  (tools.md 12.2).
- **A file cut short records the same in both trees.** The Go tree's
  `ymxr-layout` panicked, exit 2, where the Java tree reports `the record
  runs past the file's B bytes`, exit 1 (tools.md 9.7), and a program cut
  inside its stub ran the search for its SNDH file past the end in both
  trees. Every cut of the eleven files of the binaries kit, 377,463 cuts,
  records the same in both.
- **What an earlier release reads.** `ymxr-prg` and `ymxr-layout` of
  0.4.14 and earlier report `the SNDH file's tag TIME at A is not one this
  reads` on a file this release writes. An SNDH file or a program is 4 +
  2`N` bytes longer, `N` its subtunes.

Checks: `mvn -o clean test` green, 177 tests and no skip; the rig green
under its default, `-abs`, `-lean`, `-kit`, `-stub` and `-hatari`, 10, 10,
10, 14, 10 and 13 tunes. The corpus run was left out: it reads the
converter, the binder and the player, and none of them has moved since
0.4.14, when it read 544 of 544.

### 0.4.14, 2026-09-23

<https://github.com/odipar/YMXR/releases/tag/v0.4.14>, built from the commit
tagged `v0.4.14`.

The nine 68000 binaries are 0.4.11's bytes. The tools read a format they
could not read before, and performance.md records what the program's
clock costs.

- **A YM3 dump reads.** `YM3!` and `YM3b` are fourteen vectors of one
  register each and no header at all: the rate is 50 Hz, the clock
  2,000,000, and R14 and R15, which YM5 runs its effects through, are
  zero, so a YM3 dump runs no effect; `YM3b` names the frame it repeats
  to in a long after the vectors. A byte count that is no whole frame
  and a loop frame past the end are two lines of this reader, and the
  format
  gate `ymxr-check` reads opens on them too.
- **The corpus is 544 of 544.** The rig played every file of the corpus
  at 200 frames and each one plays as the specification reads, where
  `capture.ym`, the corpus's one YM3 dump, stood outside a run before.
- **What the program's clock costs.** Read off Hatari's profiler over
  the handler's instructions: a tick that plays no row is 147 cycles,
  the interrupt's entry and the `rte` among them, and one that plays a
  row 283 before the play call it makes. A 50 Hz tune's 150 ticks a
  second cost 28,850 cycles, 0.36 per cent of the CPU, against the
  36,200 of the 200 Hz clock before 0.4.11; a 60 Hz tune's 240 ticks
  cost 43,440 against 37,560, the price of its rows landing evenly.

Checks: `mvn -o clean test` green, 172 tests and no skip; the rig green
under its default, `-abs`, `-lean`, `-kit`, `-stub` and `-hatari`, 10,
10, 10, 14, 10 and 13 tunes, and over the whole corpus, 544 of 544.

### 0.4.13, 2026-09-22

<https://github.com/odipar/YMXR/releases/tag/v0.4.13>, built from the commit
tagged `v0.4.13`.

The nine 68000 binaries are 0.4.11's bytes: no source under `68k/` has
moved since. What moves is the record `ymxr-layout` writes and the
clauses two more rounds of readers marked.

- **One key, one meaning.** `state` was the state byte's offset on the
  core line and the state block's bytes on a tune line, and `subtunes`
  was the subtune table's offset on the core line and the count of the
  `##` tag. A tune line reads `stateblock`, the core line
  `subtunetable` and the tag line `count`, so `ymxr-layout` writes
  different keys than 0.4.12 for those three values.
- **6.5 lists the offsets.** 6.1 reads every `at` and every other offset
  of the record from the file's first byte and 6.5 counts them from the
  program's first byte, and neither said which values those are. The
  clause names them: the entry line's `to`, every `at`, the core line's
  `state`, `subtunetable` and `work`, the subtunes line's `tunes` and a
  tune line's `image`.
- **The letters are carried over.** The core line reports `U` and `W`
  with `H` among them, and the subtunes line and the workspace line said
  `at` in those letters alone; each tag's length stood in 4.5 step 2
  unnamed, which fixes the `at` of `!#SN` and `HDNS`; and the `##` count
  and a clock tag's rate read as digits against 6.1's integers.
- **Four readers read the document cold**, runs 6 and 7 of the binaries
  kit, two implementers at a time. Every record came back byte for byte
  from each, forty-four against forty-four, and one reader of run 6
  marked every note *leaves output as it is*, the first of the nine to
  do so. Over the seven runs the document moved in 26 places.

Checks: `mvn -o clean test` green, 168 tests and no skip; the rig green
under its default, `-abs`, `-lean`, `-kit`, `-stub` and `-hatari`, 10,
10, 10, 14, 10 and 13 tunes.

### 0.4.12, 2026-09-22

<https://github.com/odipar/YMXR/releases/tag/v0.4.12>, built from the commit
tagged `v0.4.12`.

The nine 68000 binaries are 0.4.11's bytes: no source under `68k/` moved.
What moves is `ymxr-layout`, the record it writes, and the checks the
documents are read under.

- **A record reads the timer a program is armed with.** BINARIES.md 6.5
  reported the stub's descriptor as far as the core's offset, which was
  the whole descriptor until 0.4.11 put the prescaler, the count and the
  ticks behind it. The stub line ends with those three where the version
  is 2 or above, and a program of an earlier release ends at `core`.
- **A text above $7E escapes the same in both trees.** The Java tool
  replaced a byte outside UTF-8 with U+FFFD and then wrote the record in
  US-ASCII, which put a `?` where the Go tool wrote `\ufffd`. 6.1
  defines the escape and the replacement character, and the Java tool
  escapes every character above $7E.
- **Five readers read BINARIES.md cold**, runs 3 to 5 of the binaries
  kit, the fifth two implementers at once. Every record came back byte
  for byte from each, and twenty places of the document moved for what
  they marked: 4.5 reported the stub's version as 1 where 4.2 reads 2,
  the entry line read `entry i at 4i` where an ordinal counts from 1, an
  image line read as one an offset or one a subtune, and the text of
  `FLAG` read two ways under a citation of 4.5.
- **The lines a table reports are read against both trees**, 52 of them:
  the longest run of words between the figures a tool writes into a line
  must stand in the Java tree and the Go tree. The versions the
  documents report are driven through the tools rather than read, which
  is the check 4.5 lacked.
- **The rig reads the timer on Hatari's MFP.** `-stub` reads its tunes
  at 50 Hz, which divides 200, so the arming 0.4.11 changed made no
  difference to it; it reads the same tune at 60 Hz now, the period
  between the in-service bits the stub's handler clears, 4.164 to
  4.166 ms against the 4.167 of 240 ticks a second.

The kit under `doc/conformance-binaries` is eleven files and 153 record
lines, with a program of each descriptor version and a multi file whose
names stand above $7E.

Checks: `mvn -o clean test` green, 168 tests and no skip; the rig green
under its default, `-abs`, `-lean`, `-kit`, `-stub` and `-hatari`, 10,
10, 10, 14, 10 and 13 tunes.

### 0.4.11, 2026-09-21

<https://github.com/odipar/YMXR/releases/tag/v0.4.11>, built from the commit
tagged `v0.4.11`.

A twelfth tool, and a program stub of 1,770 bytes whose descriptor is
version 2; the eight cores are 0.4.10's bytes, no source of the player
having moved.

- **Timer C ticks at a multiple of the tune's rate.** The stub armed the
  operating system's 200 Hz and counted the rate against it, so a 60 Hz
  tune's rows landed 3, 3, 4 ticks apart, 15 or 20 ms where 16.67 was
  wanted. A tool picks the timer now, the lowest multiple of the rate the
  MFP counts exactly, and writes the prescaler, the count and the ticks
  into three fields of the descriptor (BINARIES.md 4.10): a 50 Hz tune is
  150 ticks a second and a row every third, a 60 Hz tune 240 and a row
  every fourth, and a rate the MFP counts no multiple of under 400 falls
  back to the 200 Hz clock, whose accumulator spreads the rows over the
  second. Over
  rates 1 to 400 the two fields multiply to 2,457,600 and the ticks are a
  multiple of the rate.
- **`-tc` names Timer C**, over the VBL `-perf` names, in `ymxr-sndh`,
  `ymxs-to-sndh`, `ymxr-prg` and `ymxs-to-prg`, and in `bin/ymxr-set`,
  `ym/play.sh` and `ym/play-ymxs.sh`. Both flags together are `-vbl and
  -tc name two clocks`, exit 2, and a set that claims Timer C keeps the
  VBL, `-tc` with such a set reported by name.
- **`ymxr-layout` writes the record of a file**, the twelfth tool: a
  multi file, a bound tune, an SNDH file or a program on standard input,
  one line of JSON a part on standard output. BINARIES.md 6 defines that
  record, of the tags and their values, the core's descriptor, the
  subtune table, each bound tune with its sources, the images and the
  workspace, and of a program the PRG header, the stub's descriptor and
  where the SNDH file begins.
- **The binaries kit reads BINARIES.md cold**, nine files of the four
  kinds under `doc/conformance-binaries` with the record of each, 127
  lines over the nine. Two implementers read the document and wrote a
  reader from it: the first returned seven records of nine byte for byte
  and the two that differed were right, the workspace inside a program
  counting the relocation table in; the second returned all nine. The
  two runs moved 23 places of the document, `##` being 4 bytes and a zero
  byte in 4.5 step 2 among them.
- **The rig reads the stub's two clocks**, `-stub`: one SNDH file, two
  programs, one from Timer C and one from the VBL, and the writes the
  frame procedure makes are one stream in one order under either, over
  the same frames. `-hatari` names the VBL as the clock it cuts its
  frames at, where the clock tag of 0.4.10 had left it reading Timer C
  against a model of the VBL.

Checks: `mvn -o clean test` green, 166 tests and no skip; the rig green
under its default, `-abs`, `-lean`, `-kit`, `-stub` and `-hatari`, 10,
10, 10, 14, 10 and 13 tunes.

### 0.4.10, 2026-09-20

<https://github.com/odipar/YMXR/releases/tag/v0.4.10>, built from the commit
tagged `v0.4.10`.

The program stub is 1,760 bytes, 34 fewer than 0.4.9's, and the eight
cores are 0.4.9's bytes. What moves is the clock a host and a program
play from, which the SNDH header now names.

- **A set that claims Timer C names the VBL.** The tag block wrote `TC`
  and the rate for every set, so one whose tunes run an effect on Timer
  C asked its host for the timer the player's handler claims at init.
  SNDH v2.2 names a tag for each clock, `TA` to `TD` and `!V`, and the
  block's clock tag is now `!V` and the rate where the claims byte has
  Timer C, and `TC` and the rate where it leaves that timer free
  (BINARIES.md 3.2). `four-timers.ymxr` reads `!V60` and `chambers.ymxr`
  `TC50`. The `FLAG` letters are as they were, and a host that reads
  them sees the same claims.
- **A program plays from the clock its file names.** The stub read the
  screen's rate and played from the VBL where it matched the tune's rate,
  from Timer C where it did not, so a file naming Timer C was played from
  the VBL on a 50 Hz screen. It reads flag bit 1 alone now: the VBL where
  the clock tag names the VBL, where the `FLAG` letters claim Timer C, or
  where the VBL is asked for, and Timer C otherwise (BINARIES.md 4.3,
  4.7). The code that read the screen's rate is gone with it, and the
  stub loses 34 bytes.
- **`-vbl` is the VBL asked for** in `ymxr-sndh`, `ymxs-to-sndh`,
  `ymxr-prg` and `ymxs-to-prg`, and in `bin/ymxr-set`, `ym/play.sh` and
  `ym/play-ymxs.sh`, where it is read before `-vN`. The two writers put
  `!V` in the tag block and the two program tools set bit 1. `-perf`
  names the VBL too: the raster monitor paints one frame of calls, which
  reads against the raster where the tick comes from the VBL
  (performance.md).
- **The stub's reader reads either clock tag**, and the tags without one
  are `the SNDH file's tags have no TC or !V rate`. A file that names the
  VBL at a rate other than 50 is `the file plays from the VBL at H Hz:
  the stub's VBL is a 50 Hz clock, so this set needs a separate host or
  the VBL asked for`, and `-vbl` writes that program at the caller's
  asking. `bin/ymxr-set` reads `-pcrel` and `-abs`, which tools.md
  already listed among the flags reaching `ymxr-sndh` through it.

Checks: `mvn -o clean test` green, 156 tests and no skip; the rig green
under its default, `-abs`, `-lean` and `-kit`; `chambers.ymxr` played 300
frames from each clock under Hatari, and the report of every case read
back.

### 0.4.9, 2026-09-19

<https://github.com/odipar/YMXR/releases/tag/v0.4.9>, built from the commit
tagged `v0.4.9`.

The 68000 binaries are 0.4.8's bytes, every one of the nine: no source
under `68k/` moved. What moves is one figure a check reckons, and the
documents the tools are read against.

- **A digidrum's end row is reckoned from the count the timer counts.**
  6.4 read the count column where YMXS's 6.4 reads counted(C), and a
  count of 0 is the 256 the MFP counts down (1.9.1), so a drum started at
  0 was reckoned to end a frame after it began. `Columns.duration` reads
  counted(C) in both trees. Over the corpus's 543 readable dumps every
  start has a count of 1 to 255, measured through `ym-to-ymxs`, so the
  fix moves what a structure written by hand reckons and leaves every
  converted byte and every verdict of the corpus as it was.
- **Four readers read the specification cold**, runs 5.6 to 5.9 of the
  conformance kit, and every record of the kit came back byte for byte
  from each. Twenty-two places changed for what they marked: rule 2(e)
  forbade a tune the kit ships, 1.9.2 left the select's condition to two
  other clauses where 8,322 entries turn on it, 7.2 gave `rows` an order
  and no shape, and 7.4 never said which stream has the record on it.
  conformance/README.md records the four runs.
- **Every shape a tick has is read on a real MFP.** The rig told a chip
  write from the handler that wrote it by five shapes of seven, so the
  two counted shapes stood outside the comparison since the counted
  handler arrived: `counted.ymxr` now plays 70,239 ticks on Hatari's MFP
  against the model, and `envelope-counted.ymxr` 9,722.
- **A run over the corpus plays 542 of 543 tunes** as the specification
  reads, the one failure being a YM3 dump, which the converter reads as
  another format and names.
  The rig took a directory for a dump before this and converted every
  dump of the corpus to build a set of four.
- **A citation of a specification lands on a clause**, which
  `ConsistencyTest` reads; `release/publish.sh` requires the nine
  binaries `go/binaries/binaries.go` embeds rather than the five it named;
  and tools.md 20.3 reads that a release is tagged twice, `v0.4.9` and
  `go/v0.4.9`.

### 0.4.8, 2026-09-19

<https://github.com/odipar/YMXR/releases/tag/v0.4.8>, built from the commit
tagged `v0.4.8`.

The player reads a row through the program counter unasked. The eight
cores move their bytes, four of them change name, and a fifth reader of
the specification moved seven of its clauses.

- **`YMXR_PCREL` stands at 1.** A player assembled by hand reads each row
  through a signed word displacement from the instruction that reads it,
  where it read an absolute address: 96 cycles a tick that writes a row
  against 108, 114 on the loop against 130 and 124 on the stop against
  132, and 160 with the interrupt's entry and its `rte`. Every row of
  every source stands within 32,767 bytes of the handlers, and a host
  that places a tune further off assembles the player with
  `-dYMXR_PCREL=0` (68k/YMXR.S; BINARIES.md 5.5). The player is 8,160
  bytes against 7,804.
- **The four cores that read an absolute address are named for it**:
  `YMXR_sndh-abs.bin`, `-perf-abs`, `-lean-abs` and `-perf-lean-abs`,
  where `-pcrel` named the other four before, and the four the tools
  choose keep the plain names, so a flags word of 4 to 7 is the ordinary
  case (BINARIES.md 2.1). A tool passed neither switch reads the
  displacement core and stands the absolute one under a file whose tunes
  end past the reach, as it did.
- **performance.md is the player as it stands**, and an absolute address
  has a section with the figures it costs. Three of the ten fixture tunes
  read a cycle or two more a call, where a start turns a row's address
  into a displacement, and what a square's tick and a one-row source's
  save comes down with the general handler: 343 to 500 cycles a frame,
  and 445 on the kit's `retune`.
- **A fifth reader read the specification cold** and produced all fifteen
  records of the kit byte for byte. Seven places moved for what it marked
  as deciding output: the Conventions' offset counts bytes and one on a
  long is a multiple of 4; 3.1.2 has the offset of row n of column i, so
  the stride stands without DTX's SPEC.md; 1.1.2's table records which
  marking bits a player reads on every row; 1.9.1 reads the count as 0 to
  255; 7.3 defines the effects of `e` once and names its four numbers as
  the kept values; 7.4 says where the line of an error of the file goes;
  and 8.3 names a tune's table whose RR is above R (conformance/README.md
  5.5).
- **The envelope's whole range, heard.** `ym/whole-byte.py` gains a sixth
  section, a counted source of two columns on `setEnvelope` whose 512
  rows step the period from 64 to 65,535 over eight seconds and then
  stand: under Hatari the player writes 52 periods above 32,767, the half
  a marked source leaves unreachable, and the last is one cycle in 8.39
  seconds (experiments.md).

### 0.4.7, 2026-09-19

<https://github.com/odipar/YMXR/releases/tag/v0.4.7>, built from the commit
tagged `v0.4.7`.

The envelope period reaches its whole range. Version 6 of the tune file
counts a source of several columns, the player runs it on a sixth handler
shape, and a tune whose envelope a timer drives sounds the half it could
not reach.

- **A source on `setEnvelope` is counted.** Both registers of that target
  read every bit of their byte, so bit 7 of a row is a value and the
  marker took it: a source on it reached a period of 0 to 32,767, half
  what a row writes,
  and the tick that read its last row wrote the marker's bit into R12
  with the value, a period 32,768 above the rows before it. The kit's
  `envelope` tune jumps from 800 to 33,696 there. Such a source is
  counted now: its rows are whole bytes, a tick counts them, and it
  reaches 0 to 65,535, an envelope cycle of 8.39 seconds where it reached
  4.19 (SPEC.md 2.1.3, 3.1.6). The marked form stands for the files that
  have it, which 2.1.4 defines.
- **Version 6**, since a player of 5 would read a counted source of
  several columns as a counted source of one (3.3.5). It runs through the
  multi file, the bound tune, the core's descriptor and the player's init
  (BINARIES.md).
- **A sixth handler shape**: the wide handler with a word counter where
  the marked one tests bit 7, four instances of 138 bytes against the
  marked shape's 122. A tick that writes a row costs 196 cycles against
  176 and one that loops 246 against 206, measured on the kit's two
  envelope tunes; the player is 7,804 bytes against 6,934.
- **The kit has a fifteenth tune**, `envelope-counted`, whose high byte
  reaches 255 and whose two sources are counted.

### 0.4.6, 2026-09-19

<https://github.com/odipar/YMXR/releases/tag/v0.4.6>, built from the commit
tagged `v0.4.6`.

A structure reaches the mixer. The 68000 binaries are 0.4.5's bytes, and
the tools write one file differently: a counted source on `setR7` now has
the two port directions in its rows, which no structure could express
before.

- **A writer sets the port directions of a counted source on `setR7`.**
  2.1.2 names that target one whose register reads every bit of its byte,
  so a counted source may drive the mixer, and the structure's value for
  R7 is the mixer's six bits, 0 to 63: the converter reported a row above
  that, and such a tune file was writable by hand alone. Bits 7 and 6 are
  the directions of the two I/O ports, which a player writes as 1 on
  every write a row makes (1.4.2); a tick of a counted source writes the
  row whole, so rule 2(f) has the writer set them. A structure whose
  source reads 56 and 57 writes `$F8` and `$F9`.
- **A tune that spends version 5, in the tree.** `ym/whole-byte.py`
  writes five sections of 800 rows at 50 Hz, a timer driving `setR0`,
  `setR4`, `setR11` and `setR7` in turn under one melody, and
  experiments.md reads back what the sections come to. tools.md 18.6
  names it.
- **What the two cores do to a tune, measured.** Over 900 frames of
  Turrican under Hatari both write 9,589 values and 9,583 of them are the
  same value to the same register in the same order; a write lands at
  another cycle inside the frame, 24 later at the median. performance.md
  records that, the lean column of the new build, and what the raster
  monitor makes of it.
- **A relative work directory records its run.** `ym/hatari.sh` named its
  AVI under the directory it then ran inside, so a relative WORK left the
  run unrecorded and the read of the AVI reported a file that was never
  there.

### 0.4.5, 2026-09-18

<https://github.com/odipar/YMXR/releases/tag/v0.4.5>, built from the commit
tagged `v0.4.5`.

A source whose column fills its byte, and a tick that reads its row
through the program counter. Version 5 of the tune file encodes the
first; the second is a build of the player, and the tools stand it under
a file unasked, so an SNDH file written by this release ticks 12 cycles
cheaper on every row it writes.

- **A source on a register that reads every bit of its byte.** `setR0`,
  `setR2`, `setR4`, `setR7`, `setR11` and `setR12` were left to a later
  version: the marker stands in bit 7 of a row and those registers read
  that bit.
  A source on one of them is counted instead - bit 31 of its index entry
  marks it, every byte of a row is a value, and a tick reads R rows -
  and a file with one is version 5 (SPEC.md 3.1.6, 3.3.5). The player
  runs a fifth handler shape for it, 100 bytes an instance, whose word
  counter a start patches beside the place.
- **The kit has a fourteenth tune**, `counted`: a counted source on each
  of the six targets, rows with bit 7 set in every one of them, and a
  square on R8 beside them which the marker ends, so a player that reads
  the end of a source from the version word rather than from the index
  entry fails on it. Eighty frames and 154 ticks.
- **A tick reads its row through the program counter.** `YMXR_PCREL=1`
  builds a player whose place is a signed word displacement in the
  instruction that reads it: 96 cycles where a tick that writes a row and
  steps its place cost 108, 114 where the marker's loop cost 130 and 124
  where its stop cost 132. A square's handler and a one-row source's
  write a value the start patched into them and cost what they cost.
- **A file reads its rows that way unasked.** The tools read a file's
  last bound tune against the core's first byte: within the 32,767 bytes
  a displacement reaches they write the core that reads through the
  program counter, and further off the one that reads an address.
  `-pcrel` requires it and `-abs` stands the other core under the tunes.
  The cores are eight, one a setting of the three switches.
- **Two layouts moved** so the rows stand beside the player: a bound
  tune's DTX1 tables now stand before its image, and an SNDH file's
  subtunes before the images. Both are offsets the player follows rather
  than a fixed order, so no format moved.

### 0.4.4, 2026-09-18

<https://github.com/odipar/YMXR/releases/tag/v0.4.4>, built from the commit
tagged `v0.4.4`.

`setEnvelope` is a target this version encodes, and the targets of several
registers are heard as one form. The player is the size 0.4.3 has: the row
for target 20 stood in `ymxr_wide` as zeros and this release fills it, so
a version 3 tune plays as it did and performance.md stands as it was.

- **The envelope period is a target this version encodes.** Target 20,
  `setEnvelope`, was the one target of 14 to 24 left out: both its
  registers read eight bits, so no column of its rows had a bit to spare
  for the marker. The marker stands in bit 7 of R12's column now, the
  envelope period's high byte, which leaves a source on this target a
  period of 0 to 32,767. The envelope frequency is 2,000,000 / (256 x
  envelope period), so 32,767 is one cycle in 4.19 seconds and 65,535 one
  in 8.39 seconds: the half this target leaves out is the envelope slower
  than 4.19 seconds a cycle, which a frame's R11 and R12 columns write.
  SPEC.md 2.1.3 defines the encoding and rule 2(e) the range, and
  `ConsistencyTest` recomputes the two cycles from terminology.md 1.4.
- **The kit has a thirteenth tune**, `envelope`, which reaches that target
  and the whole byte beside the marker's column: a source of two columns
  repeating to a row above 0, one that plays once and stops its timer at
  its marker with 127 in a row of its marked column, a start that changes
  the source on a running timer, and the envelope shape set from column 13
  while the period ticks. It plays 128 frames and 201 ticks on the player,
  the player's frames the reader's entries.
- **The targets of several registers are heard as one form.**
  `dist/ab/AB.PRG` plays one piece three ways, 1,536 rows at 50 Hz a
  subtune - two timers under version 3, one `setVoiceA`, one `setToneA` -
  and under Hatari the three sound the same. Of the 3,072 frames each
  record has, 3,024 are equal byte for byte across the three, and the 48
  that differ are the 48 that start a timer, where R0 moves from the
  frame's column to the first tick of the effect starting there.
  experiments.md records the run and what a record cannot reach: the ticks
  are outside it, and the ticks are where the three forms differ most.
- **A record reports the six bits of R7 alone.** SPEC.md 7.3 read "six for
  R7, whose bits 7 and 6 the player writes as 1" after "the value the
  register reads", which reads as an instruction to report those two bits
  with the six: 248 where the reference reports 56. A reader written from
  that section alone made that reading, and its first comparison failed on
  R7. The clause says which of the two a record reports, and names the
  range.
- **The task names the versions and the targets the kit reaches**, which
  it read as version 3 and targets 0 to 13 while the kit has carried a
  version 4 tune since 0.4.2.
- **One build runs at a time.** `bin/ym-to-ymxs < tune.ym | bin/ymxs-to-prg`
  starts both tools at once, and with a source newer than the last build
  both found a build owed and both ran Maven into the same
  `target/classes`. `bin/run` builds under the lock `target/.building`
  now, and an absent `target/classpath` owes a build as well.
- **A code span that wraps, and a fenced block, are quoted whole.** The
  style check read the words of a quoted message as prose: doc/tools.md
  19.2 carried the build lock's message and fired on a word inside it
  until the span was moved onto a separate line. 19.2 is rewrapped at
  the width of the document, so the document reads the check back.
- **DTX 0.11.10 and YMXS 0.4.4.** The three tools here that read YMXS JSON
  report `this is not JSON: EOF` for an empty input now, as the Go tree
  does; the parity tests read the error paths.

### 0.4.3, 2026-09-18

<https://github.com/odipar/YMXR/releases/tag/v0.4.3>, built from the commit
tagged `v0.4.3`.

The document checks are one package. Every file a tool is built from stands
as 0.4.2 has it - `go/`, `68k/`, `bin/` and every document - so the player
is that release's, the eleven tools are its executables, and the ten dumps
under `ym/test` convert to the tune files it wrote.

- **`org.ymxr.doc.Documents`** reads a link that resolves, one wrap width, a
  glossary in order and the rows it is read from. Those four were written
  in each of the four repositories of the family, and the copies had
  drifted in both directions: this tree read fenced blocks and anchors
  where ST4 skipped them, and reported the line of no broken link, which
  YMXS did. The count of documents read, which says the check is awake,
  came from here. The package is carried from DTX, where it is kept, as
  `org.ymxr.style` is.
- **One drift the package exposed.** The row parser here returned two
  cells, the term and the third column, where the other three return all
  three; its two callers read `row[2]` now. The check that caught the shift
  is the one asserting it had opened more than twenty rows.
- The tools read DTX 0.11.9 and YMXS 0.4.3.

### 0.4.2, 2026-09-18

<https://github.com/odipar/YMXR/releases/tag/v0.4.2>, built from the commit
tagged `v0.4.2`.

The style check, and the LHA decoder read from YMXS rather than copied.
Every file under `68k/` stands as v0.4.1 has it, so the player is that
release's, and the ten dumps under `ym/test` convert to the tune files a
build of the tag before writes, measured.

- **The struck list is a document.** The check was a list of 104 phrases in
  a test class, matched as substrings. `org.ymxr.style` reads `STRUCK.md` -
  a section a rule of AGENTS.md, an entry a name, a pattern and the samples
  the pattern is and is not in - and runs over every document and every
  code comment. The package is carried from DTX, which wrote it, and the
  four repositories of the family run the same 370 lines. The cleft, struck
  in AGENTS.md since it was written and encoded in no list, found 30 of the
  50 lines reworded.
- **A code span is quoted material.** The check read what stands between
  backticks as prose, so tools.md reported a hit on two of the messages the
  tools write, which it quotes. That line stands in every copy now.
- **`Lha.java` goes.** It was 351 lines of code identical to YMXS's but for
  the class modifier, carried because the two calls it needs were
  package-private there, beside a Go tree that already imported the module
  and called the exported pair. YMXS 0.4.2 makes them public and this reads
  them.
- The tools read DTX 0.11.8 and YMXS 0.4.2.

### 0.4.1, 2026-09-17

<https://github.com/odipar/YMXR/releases/tag/v0.4.1>, built from the commit
tagged `v0.4.1`.

What a tick of a target of several registers writes, and the rig reading
one on a real MFP. Every file under `68k/` but the rig stands as v0.4.0 has
it, so the player is that release's, and the thirty files the ten dumps
under `ym/test` convert to - the tune file, the structure and the program
of each - are byte for byte 0.4.0's, measured against a build of its tag.

- **Section 8 names what 0.4.0 defines.** 8.2 left targets 14 to 127 to a
  later version and 8.3 a source of more than one column, and 0.4.0
  defined targets 14 to 24 and sources of two and three columns. 8.2 is
  targets 25 to 127, `setEnvelope` and a source of one column on the six
  registers that read every bit of their value; 8.3 is more than three
  columns, values wider than a byte, and RR above R.
- **5.1 was the hole that made**: its steps wrote the register of the
  target, and a target of several has no one register. A tick writes a
  column at a time in column order, the column the marker stands in last,
  so the marker is the last byte written and a player reads it there. 1.8.2,
  1.8.3 and 4.3 step 3 read the registers of the kept target.
- **The order is the player's**, which YMXS 0.4.1 defines: 3.1.1's table
  read "registers, in the order it writes them", and the sound depends on
  two of those orderings alone. The marker-last rule satisfies both, since
  a tone and a voice name their coarse nibble for the marker and a buzzer
  names its shape. The tools read YMXS 0.4.1.
- **The rig reads a tick of several registers on a real MFP.** Its trace
  path placed a chip write by the handler whose range its PC fell in, and
  the two handlers of several registers stood in none of them, so their
  writes were a tick of no effect: a run reported effect 0 ticking 0 times
  where the rates said 20,483. A tick of C registers is C writes of the
  trace besides, and the loop read one write a tick. A run that names no
  tune plays the conformance kit's `voices` after the fixtures, since no
  dump converts to a target of several registers: 2,000 frames and 36,675
  ticks on Hatari's MFP.

### 0.4.0, 2026-09-17

<https://github.com/odipar/YMXR/releases/tag/v0.4.0>, built from the commit
tagged `v0.4.0`.

A target that writes several registers, and the source of several columns
it runs. Every tune the tools converted before this is the file it was:
the version a writer writes is the lower of the two the tune reads under,
so the tune file of each of the ten dumps under `ym/test` is byte for byte
0.3.27's, measured against a build of its tag, and a player of version 3
reads it. The structure of each differs in its version word alone, which
YMXS 0.4.0 writes as 4. The SNDH file of each is 3,260 bytes longer: 2,212
of core, 4,736 bytes going to 6,948, and 1,048 of workspace. The program
stub stands at 1,794 bytes.

- **Targets 14 to 24** (SPEC.md 2.1): `setToneA/B/C` writes R0 R1, R2 R3,
  R4 R5; `setVoiceA/B/C` those and the voice's volume; `setBuzzer` R11 R12
  R13; `setNoiseA/B/C` the noise period and one voice's volume. An effect
  runs a voice's period and its volume on one timer where it needed two.
  `setEnvelope` writes two registers of eight bits, so a source for it has
  no bit to spare for the marker and this version encodes none (2.1.3).
- **The marker stands in the column the target names** (3.2.1), whose
  register reads seven bits or fewer: the coarse nibble of a tone or a
  voice, the envelope shape of a buzzer, the noise period of a noise. So a
  source runs on R0, which no source of version 3 could.
- **A tune file is version 3 or 4, and a writer writes the lower one**
  (3.3.5). The rule runs through the multi file and the bound tune, and
  the core's descriptor field at 18 is the highest bound tune version it
  reads (BINARIES.md 2), which the binder reads against the version it
  binds at.
- **A tune of version 3 costs the player what it cost before.** Two
  handlers an effect beside the three it had, and three branches that
  stand elsewhere in a tune with a source of several columns, each a byte
  init writes. `-cycles` reads every figure of performance.md back
  unchanged. A tick of two registers is 244 cycles with the interrupt's
  entry and its rte and one of three is 328, against 176 for one. The
  player is 6,264 bytes where it was 4,052, and the workspace 2,120 where
  it was 1,072.
- **The conformance kit has a twelfth tune**, `voices`: one kind of target
  an effect, with the marker in a different column under each, so a reader
  that reads column 0 alone reports the wrong rows for three of the four.
  The kit reads 12 tunes and 18,444 entries, and the exercise plays 11.
- The tools read YMXS 0.4.0, whose structure this encodes: a start is a
  shape a width, pairing a target with the source of the values a row it
  reads.

### 0.3.27, 2026-09-17

<https://github.com/odipar/YMXR/releases/tag/v0.3.27>, built from the commit
tagged `v0.3.27`.

What a reader reports of a file written wrong. Every file under `68k/`
stands as v0.3.26 has it, so the player is that release's and the forty
files the ten dumps under `ym/test` convert to are byte for byte 0.3.26's,
measured against a build of its tag.

- **SPEC.md 8.1 is defined at 3.3.4 and goes.** The table of conditions
  gains a row for a table or a source that begins or ends outside the file,
  one for a table of a DTX variant other than 2, and one for a table other
  than 30 columns of one byte. A reader reports the first condition
  present, in the order of the table, and reads no further field; a name
  offset other than 16 + 4S is a field a reader follows (3.3.2).
- **The two trees read a file written wrong the same way.** The Java tree
  copied the bytes through the offsets before any check, so a file cut
  short ended in the exception the copy threw where the Go tree reported
  its line, which tools.md 19.5 carried as a difference between them; the
  row goes with the clause. Both trees read the table's variant and shape
  besides, which neither read before: a DTX1 table or a table of 29 columns
  was read as a tune.
- Section 8 says what a missing number means, since the numbers of the
  rest stand: 8.1 is defined at 3.3.4 and 8.6 at 4.2.1 and 5.2.1.

### 0.3.26, 2026-09-17

<https://github.com/odipar/YMXR/releases/tag/v0.3.26>, built from the commit
tagged `v0.3.26`.

The place before the first start on a timer, and two measurements read
again. The player grew six bytes at init, so the SNDH file and the program
of every dump are six bytes longer than 0.3.25's and the tune file and the
structure of every dump are byte for byte that release's, measured against
a build of its tag.

- **The place of a timer before its first start is row 0 (SPEC.md 4.1
  step 4)**, which closes half of 8.4. The player computed a wild pointer
  there: the place a start that moves no place writes is the new source's
  first row plus the place less the source's first row, and before any
  start those two are the parked marker and 0. Init writes the parked
  marker into the kept first row of every effect, the six bytes, and the
  two cancel. 8.4 keeps its other half, the row a tick reads where the
  place is outside the rows of the source connected, which rule 3(a)
  keeps a tune clear of.
- **The rig writes that tune itself**, through YMXS's form and
  `ymxs-to-ymxr`: no conversion of a dump writes a start that moves no
  place with no start before it, and the tune fails on the player as
  0.3.25 has it.
- **`-refill` reads the advance's parts off the pass `-cycles` counts**,
  where the two walked every frame of every fixture separately: 2:30 for
  both against 2:26 for the call's figures alone (tools.md 17.1).
- **performance.md's Against YMX table is measured again** by the method
  the section describes, `ym/cost.sh` at `VBLS=2300`: YMXR reads 1,990 and
  1,651 cycles a call on average against 1,995 and 1,655, and 4,296 and
  4,692 at most against 4,464 and 5,056. At their worst YMXR is 10 per cent
  over YMX on Turrican - world 4-3 where the sentence read 18, and the
  section says which release each row is.

### 0.3.25, 2026-09-17

<https://github.com/odipar/YMXR/releases/tag/v0.3.25>, built from the commit
tagged `v0.3.25`.

SPEC.md 8.6 closed, and the play call's figures measured again. The player
grew four bytes at init, so the SNDH file and the program of every dump are
four bytes longer than 0.3.24's and the tune file and the structure of every
dump are byte for byte that release's, measured against a build of its tag.

- **Where a tick falls in a frame is defined (SPEC.md 4.2.1)**: between any
  two operations of a frame, with three that run whole against a tick - the
  two bytes of a register write, the write of the target, the place and the
  loop row at a start, and the write of a select. The rig asserts it rather
  than assuming it: it stops a play call at the head of each effect's step
  and after the last of them, on a row that moves what that effect's tick
  writes, and reads the tick against the model stepped that far.
- **A tick of a timer no row has started is defined (SPEC.md 5.2.1)**: it
  writes 0 to `$FFFF8800`, `$80` to `$FFFF8802` and stops the timer. The
  player wrote R8 there, the register the assembler left in the handler's
  immediate, which silenced voice A; init parks it at R0, the register of
  the target 4.1 step 4 keeps, for the four bytes.
- **performance.md's two tables are measured again.** Three DTX releases
  have gone under them since they were last read, each carrying the ST4
  decoder the advance calls: the call is 0 to 6 cycles cheaper on average
  and its costliest frame up to 304, and Turrican - world 4-3's heaviest
  refill fell from 4,130 to 3,826. plan.md's ranges, the advance's share,
  the tick savings' calls and the budget's margin follow the tables.
- **The advance's parts are measured rather than carried.** A refill spends
  450 cycles outside the decoder, and 412 where a column fits the ring; one
  that parses no operation adds 334 inside it on every tune; an operation
  costs 199 to 271 to parse by a fit over every refill, where the paragraph
  read 285 to 342. `test_ymxr.py -refill` measures these and reads the
  sentences carrying them back, as `-cycles` reads the tables back
  (tools.md 17.1).

### 0.3.24, 2026-09-17

<https://github.com/odipar/YMXR/releases/tag/v0.3.24>, built from the commit
tagged `v0.3.24`.

A rig that presses the program's keys. Every file under `src/main`, `go/`
and `68k/` stands as v0.3.23 has it, so the tools and the player are that
release's and the forty files the ten dumps under `ym/test` convert to are
byte for byte 0.3.23's.

- **`ym/keys.py` presses the keys BINARIES.md 4.6 step 4 defines**, which
  no check pressed before: the correction to `ym/play.sh` in 0.3.21 was
  read out of `68k/YMXR_prg.S` rather than run. The rig writes a program of
  twelve subtunes, each writing the number it is to R0, starts Hatari with
  a command fifo and a trace of the chip writes, and presses keys through
  it. RIGHT and DOWN step on, LEFT and UP step back, both wrap, two digits
  typed inside the pause reach subtune 12, and a digit no second can grow
  starts at once.
- The trace says which subtune plays, so each check waits for the chip
  rather than for a sleep, and Hatari ends the run itself at a VBL count
  with `--fast-boot` on: eleven seconds a run, with no dialog to answer and
  no emulator left behind. tools.md 17.2 defines it.

### 0.3.23, 2026-09-17

<https://github.com/odipar/YMXR/releases/tag/v0.3.23>, built from the commit
tagged `v0.3.23`.

YMXS 0.3.6 in the pom and in `go.mod`, so both libraries under this one are
their newest release: DTX 0.11.7 and YMXS 0.3.6, and ST4 go/v0.1.7 beneath
DTX.

**No byte of this release differs from 0.3.22's.** YMXS 0.3.6 moved a check
and no code, so the forty files the ten dumps under `ym/test` convert to, a
tune file, a structure, an SNDH file and a program each, are byte for byte
0.3.22's, measured against a build of that tag.

### 0.3.22, 2026-09-17

<https://github.com/odipar/YMXR/releases/tag/v0.3.22>, built from the commit
tagged `v0.3.22`.

DTX 0.11.7 in the pom and in `go.mod`, and a check that reads a citation
written through a link.

**No byte of this release differs from 0.3.21's.** DTX 0.11.7 has ST4 0.1.7
in it, whose packer is 0.1.6's, so the forty files the ten dumps under
`ym/test` convert to are byte for byte 0.3.21's, measured against a build
of that tag.

- `everyClauseCitedInAnotherDocumentIsDefined` read `tools.md 15` and passed
  over `[tools.md](tools.md) 15`, the form SPEC.md's Roles paragraph writes,
  so the clause a reader follows from there was read by no check. The
  closing bracket is part of the pattern now: 153 citations are read where
  140 were.
- The chain names a release at every link: ST4 go/v0.1.7, DTX 0.11.7, this.

### 0.3.21, 2026-09-17

<https://github.com/odipar/YMXR/releases/tag/v0.3.21>, built from the commit
tagged `v0.3.21`.

DTX 0.11.6 in the pom and in `go.mod`, for the ST4 packer that writes a
copy the offsets cannot reach as literals, and two play scripts that say
what the program does with a set of more than nine tunes.

**No byte of this release differs from 0.3.20's.** The forty files the ten
dumps under `ym/test` convert to, a tune file, a structure, an SNDH file
and a program each, are byte for byte 0.3.20's, measured against a build
of that tag.

- **The chain each link names a release again**: ST4 go/v0.1.6, DTX
  0.11.6, this. ST4 0.1.6 fixes a packer that read one call three ways: a
  copy whose source lay further back than an offset reaches ended the Go
  and C# packers and passed the Java one, which writes a container the
  format cannot express. It needs a literal stream of more than 32,512
  bytes behind the copy, which no tune of this repository reaches, so no
  table here packs differently.
- **The program picks a subtune past nine, and the scripts say so.**
  `ym/play.sh` wrote `N tunes, and the program's keys reach subtune 9`
  past nine tunes, and both play scripts said a tenth tune and past it
  play only under a host that selects a subtune by number. The stub
  selects them itself: a typed number is ten times itself plus the figure
  (BINARIES.md 4.6), and the arrow keys walk the set. The warning is gone
  and tools.md 16.1 cites 13.1 for what the program does.

### 0.3.20, 2026-09-16

<https://github.com/odipar/YMXR/releases/tag/v0.3.20>, built from the commit
tagged `v0.3.20`.

YMXS 0.3.5 in the pom and in `go.mod`, a script that writes a set of dumps
as one program, and the documents read back against the tree.

**No byte of this release differs from 0.3.19's.** YMXS 0.3.5 is 0.3.4's
code, and every file under `src/main`, `go/` and `68k/` stands as v0.3.19
has it: the forty files the ten dumps under `ym/test` convert to, a tune
file, a structure, an SNDH file and a program each, are byte for byte
0.3.19's.

- **`bin/ymxr-set tune.ym [more.ym ...]` writes a set of dumps as one
  program on standard output** (tools.md 16.6): each dump converted, the
  tune files into one multi file, an SNDH file around them, and the program
  stub in front of that. It runs the Go tools, built with `go build` into
  `target/go` where one is older than a source of the tree, and every option
  of the four tools reaches the tool it belongs to, the program's row count
  under `-rowsN` since `-rRR` spells the converter's repeat row.
  `ParityTest` requires the program the same four calls write through the
  Java tools, byte for byte.
- **Two checks read a pointer and a figure back.** Every clause one document
  cites in another is one that document defines, and the releases the
  documents name are the ones the two trees require. Both failed on the text
  as it stood: tools.md cited ymxs.md 1, 3, 3.4, 3.6 and 5 of a numbering
  that document never had, and tools.md 19.2 and 19.3 and requirements.md R1
  named DTX 0.10.1 and YMXS 0.3.2 while the build required 0.11.5 and 0.3.4.
- **experiments.md carried its crossover section twice**, the older copy
  left behind by the rewrite that replaced it, its table header broken.
- **performance.md read a frame procedure of 588 to 1,203 cycles**, a player
  of nine steps ago, beside the tested 510 to 1,092 above it, and put a
  practical ceiling of 25,600 ticks a second in terminology.md, which the
  specification rewrite dropped from it.
- **The house style, where no test reads it**: the cleft in terminology.md
  2.5 and two documents besides, one verdict, one sweep, and six negations
  where the operation has a name. AGENTS.md logs each strike, and
  `flatters`, `however many` and `nowhere` join the ban list.

### 0.3.19, 2026-09-16

<https://github.com/odipar/YMXR/releases/tag/v0.3.19>, built from the commit
tagged `v0.3.19`.

DTX 0.11.5, in the pom and in `go.mod`, so the ST4 under this tune comes
from ST4 go/v0.1.5 rather than from a commit no release had reached.

**No byte of this release differs from 0.3.18's.** DTX 0.11.5 is 0.11.4's
bytes, so a tune file, an SNDH and a program read as 0.3.18 wrote them. The
chain reads end to end now, each link naming a release: ST4 go/v0.1.5, DTX
0.11.5, this.

### 0.3.18, 2026-09-16

<https://github.com/odipar/YMXR/releases/tag/v0.3.18>, built from the commit
tagged `v0.3.18`.

DTX 0.11.4, in the pom and in `go.mod`, for the ST4 decoder that enters on
an instruction rather than on a branch. A tune file is the bytes 0.3.17
wrote; a bound tune is twelve bytes smaller.

- Over the ten dumps under `ym/test`, `ym-to-ymxr` and `ym-to-ymxs` write
  every one of their twenty files byte for byte as 0.3.17 did. The format
  is unchanged: the tune file's version is 3 and the bound tune's is 3.
- `ymxs-to-sndh` and `ymxs-to-prg` bind DTX's reader into the program, and
  that reader lost twelve bytes, so all twenty of their files move: 428,200
  bytes of SNDH over the ten becomes 428,080, and 446,664 of program
  446,544.
- The reader also runs a shorter init: three columns at `k` of 1 cost 7,700
  cycles where they cost 7,844. A frame is unchanged, an advance landing
  within a few cycles either way.

### 0.3.17, 2026-09-16

<https://github.com/odipar/YMXR/releases/tag/v0.3.17>, built from the commit
tagged `v0.3.17`.

DTX 0.11.3, in the pom and in `go.mod`, for the ST4 whose search aims two
moves. A dump converts to the same bytes as under 0.3.16: over the ten
dumps under `ym/test` through `ym-to-ymxr`, `ym-to-ymxs`, `ymxs-to-sndh`
and `ymxs-to-prg`, every one of the forty files is byte for byte 0.3.16's.
The format is unchanged.

- ST4's copies search gained a move that grows the dictionary where a copy
  reads from, and one that fills the gap between a literal run and the one
  after it. A column packed with a search of a second writes 1.20 per cent
  fewer bytes, and of three seconds 2.77.
- The converters here pack without seconds, so a tune weighs what
  it weighed. The gain is for a caller that spends the seconds.

### 0.3.16, 2026-09-16

<https://github.com/odipar/YMXR/releases/tag/v0.3.16>, built from the commit
tagged `v0.3.16`.

DTX 0.11.2, in the pom and in `go.mod`, for the ST4 whose search reads what
its moves save. A dump converts to the same bytes as under 0.3.15: over the
ten dumps under `ym/test` through `ym-to-ymxr`, `ym-to-ymxs`,
`ymxs-to-sndh` and `ymxs-to-prg`, every one of the forty files is byte for
byte 0.3.15's. The format is unchanged.

- ST4 weighted its search's odds by what each move saved: extending a
  literal run saves bits where freeing one at random is the walk the
  annealing makes. A column packed with a search of a second writes 0.51
  per cent fewer bytes, and of three seconds 1.3 per cent.
- A column packed without a search is the bytes it was, which is why every
  file of the ten dumps reads the same.
- `github.com/odipar/st4/go` stands at v0.1.3 in `go.mod`, which is DTX's
  requirement reaching this module through it.

### 0.3.15, 2026-09-16

<https://github.com/odipar/YMXR/releases/tag/v0.3.15>, built from the commit
tagged `v0.3.15`.

DTX 0.11.1, in the pom and in `go.mod`, for the ST4 that packs faster. A
dump converts to the same bytes as under 0.3.14: over the ten dumps under
`ym/test` through `ym-to-ymxr`, `ym-to-ymxs`, `ymxs-to-sndh` and
`ymxs-to-prg`, every one of the forty files is byte for byte 0.3.14's. The
format is unchanged.

- ST4's literal channel reads its least in one step where it read a
  min-tree in a logarithm, so a search of a packed column fits a fifth more
  steps in a second at a small ring.
- `github.com/odipar/st4/go` stands at v0.1.2 in `go.mod`, which is DTX's
  requirement reaching this module through it.

### 0.3.14, 2026-09-15

<https://github.com/odipar/YMXR/releases/tag/v0.3.14>, built from the commit
tagged `v0.3.14`.

The libraries under this one move: DTX 0.11.0 and YMXS 0.3.4, and the Go
module with them. A dump converts to the same bytes as under 0.3.13: over
the ten dumps under `ym/test` through `ym-to-ymxr`, `ym-to-ymxs`,
`ymxs-to-sndh` and `ymxs-to-prg`, every one of the forty files is byte for
byte 0.3.13's, and the conformance kit and the parity tests read the same.
The format is unchanged.

- **DTX 0.11.0** reads ST4 as a module in its Go tree, and its other two
  copy the ST4 that collects its node pool. Packing a column with
  copies costs a fifth of the memory it did, and the Java tools pack a
  48 KB file at `-Xmx1g` where they needed 12 GB.
- **YMXS 0.3.4** is its documents alone, so a tune file reads as it did.
- `github.com/odipar/st4/go` stands in `go.mod` as an indirect requirement
  now, which is DTX's dependency reaching this module through it.

### 0.3.13, 2026-09-15

<https://github.com/odipar/YMXR/releases/tag/v0.3.13>, built from the commit
tagged `v0.3.13`.

Two tools come out, and one fault is reported rather than written. A dump
converts to the same bytes as under 0.3.12: over the ten dumps under
`ym/test` through `ym-to-ymxr`, `ym-to-ymxs`, `ymxs-to-sndh`, `ymxs-to-prg`,
`ymxr-bind`, `ymxr-sndh` and `ymxr-prg`, over the eleven tunes of the
conformance kit through `ymxr-trace`, and over sets of 2, 3, 5 and 10
through `ymxr-multi` and `ymxr-sndh`, every file is byte for byte 0.3.12's,
and every report on standard error reads the same. The format is unchanged,
YMXS 0.3.2 and DTX 0.10.1 with it.

- **`ymx-to-ymxr` and `ymx-to-ymxs` come out, leaving eleven tools.** YMX is
  a design document now, and the format, the player and the tools it had are
  in that repository's history. A tune packed by those tools reaches this
  player through 0.3.12, the last release that reads a `.ymx` file. These go
  with the two: the `.ymx` reader under each, the Go module
  `github.com/odipar/ymx/go`, `ym/parity.py`, `ymx/` and its four `.ymx`
  files, `ym/convert.py pairs`, and `YMX_DUMP`, `YMX_REPO`, `YMX_BIN` and
  `YMX_PAIRS`. tools.md loses section 7, so every section after it moves
  down one and every citation moves with it.
- **A frame rate above 65,535 is an error.** The frame rate is a word at
  offset 6 (BINARIES.md 1) and a YMXS rate reaches 2,147,483,647 (YMXS,
  SPEC.md 1.3), and the write was unbounded: a tune at 70,000 Hz was written
  at 4,464 Hz and the tool exited 0. Both trees name the rate and the bound
  and exit 1. The Java tree ended in a stack trace where the Go tree wrote
  one line; both write one line now.
- **Subtune 1 of a set begins on an even address.** Every bound tune of a set
  does (BINARIES.md 5.1), and the first was left at the byte after the last
  image, odd where that image's length is odd. The sets measured above are
  unchanged; a set whose first image is an odd length differs from 0.3.12's.
- README.md, LICENSE and the documents under `doc/` were rewritten against
  the code, and README.md, LICENSE and glossary.md name YMX as the family
  this repository belongs to. Documentation only.

### 0.3.12, 2026-09-13

<https://github.com/odipar/YMXR/releases/tag/v0.3.12>, built from the commit
tagged `v0.3.12`.

The converters changed; the player did not. A dump converts to the same
bytes as under 0.3.11 unless its loop is an odd number of frames and
shorter than 64; such a dump converts to a slightly larger file that plays
as the dump does. Every file reads under 0.3.11, since the format is
unchanged. YMXS 0.3.2 and DTX 0.10.1 are unchanged.

- **A short loop of odd length is written twice instead of padded.**
  0.3.11 made every table an even number of rows by adding a silent row,
  and where the loop was an odd number of rows that row landed inside the
  loop: a silent frame on every pass. Over the 543 dumps of the corpus
  that is 137 tunes. For 13 of them the loop is one row, a sustained
  ending, where the frame changes what is heard by no amount; for 121 the
  loop is 95 rows or more, where one frame is under one per cent of a
  pass; but for three the loop is short and moves every row (Masterblazer
  7, a tone slide over 7 rows; A Prehistoric Tale 6, a vibrato over 9;
  Crapman game over, an arpeggio over 17), and the frame slowed the figure
  by 14, 11 and 6 per cent on every pass. Now a loop of fewer than 64 rows
  is written again in full instead, so the tune plays as the dump does; a
  loop of 64 rows or more, and a tune that plays once, still gets the
  silent row at the end. Sixteen corpus tunes change, and the second copy
  packs as a match: 0 to 60 bytes more on the tunes measured. The rule is
  SPEC.md 6, rule 6, with the bound and its reason; the converter's report
  names what it did, as in `padded: the loop's 7 rows written twice, so
  the table packs at unit 2`.
- **The conformance kit changed in one tune.** `turrican-2` has a one-row
  loop, now written twice, so its bytes, its `.rows` file and the manifest
  differ. The other ten tunes are as in 0.3.11.
- experiments.md has the corpus scan behind the change, "Where a padded
  tune's added frame lands". Documentation only.

### 0.3.11, 2026-09-13

<https://github.com/odipar/YMXR/releases/tag/v0.3.11>, built from the commit
tagged `v0.3.11`.

The converters changed; the player did not. A dump with an even number of
frames and an even loop frame converts to the same bytes as under 0.3.10.
A dump where either is odd converts to a slightly larger file that costs
the player less to decode. Every file reads under 0.3.10, since the format
is unchanged. YMXS 0.3.2 and DTX 0.10.1 are unchanged.

- **Tunes with an odd frame count or an odd loop frame are padded.** DTX
  packs a tune in units of two bytes, which needs an even row count and an
  even loop row. A tune without them was packed in units of one byte
  instead, and the player then decoded twice as many units a frame. Now
  the converter adds a silent row: at the loop row where that is odd, then
  at the end where the count is odd. A silent row sets no register and
  leaves the timer effects running, so the tune plays one extra frame
  there; the converter reports each row it adds. This is rule 6 in SPEC.md
  section 6, and `-k1` still packs in units of one byte and adds no row.
  148 of the 543 dumps in the corpus have an odd frame count.
- **What it saves.** Five of the ten test tunes were affected. Measured on
  the rig, their play call costs a seventh to a sixth less on average and
  a tenth to more than a third less in the costliest frame. The worst
  frame over all ten tunes is now 5,144 cycles, down from 6,358, against
  the budget of 6,656. The files grow by the alignment: the three affected
  tunes in the conformance kit grew 0.9, 2.4 and 5.3 per cent.
- **Multi-tune programs with mixed tunes shrink.** A set of subtunes shares
  one DTX image per unit, so a set with both kinds of tune needed two
  images; now every tune packs at unit 2 and a set needs one. On the
  twenty-tune set measured in experiments.md the second image was 1,492
  bytes.
- **The conformance kit is regenerated.** Three of its eleven tunes,
  `turrican-2`, `synergy` and `fine-zero`, had an odd row count or loop
  row, so their bytes, their `.rows` files and the manifest differ. A
  reader that passed the 0.3.10 kit passes this one, since the format did
  not change.
- **performance.md is measured again.** Five of its ten tunes had been
  measured at units of one byte. The tables now show both, and the
  comparison with YMX 0.10.1 is a run of today's player: an eighth and a
  seventh less on average than YMX on the two tunes compared.
- The release notes for 0.3.8 to 0.3.10, experiments.md and plan.md were
  rewritten in plain language. Documentation only.

### 0.3.10, 2026-09-13

<https://github.com/odipar/YMXR/releases/tag/v0.3.10>, built from the commit
tagged `v0.3.10`.

The player changed; the tools around it did not. A tune file converts to
the same bytes as under 0.3.9 and reads under 0.3.9. A bound tune and a
program contain the new player, so those differ. YMXS 0.3.2 and DTX 0.10.1
are unchanged.

- **Switching subtunes could change how the next one sounded.** In a
  multi-tune program every subtune shares one copy of the player, and
  starting a subtune patches that copy for the effects it uses. Three of
  those patches were applied in one direction only: set for a subtune that
  needed them, never cleared for one that did not. So a subtune could
  inherit code from the one played before it. The most audible case: after
  a subtune that used one timer effect alone, a subtune using that same
  effect together with another lost the interrupt-level drop that lets the
  two timers interleave, which changes the timing of SID voices. Every one
  of these patches is now rewritten at every start, whether the subtune
  needs it or not.
- The four player cores grew by 8 to 16 bytes each: plain 4,726, monitor
  5,216, lean 4,492, lean monitor 4,982.
- The test rig now checks this directly. It starts every test tune on a
  fresh player, and again after every other tune, and requires the
  player's code to be identical both ways. Ninety ordered pairs pass on
  both cores, and removing one of the fixes makes the check fail at the
  exact byte.
- `ym/convert.py pairs` printed "YMX 0.7" for files that are 0.9; it now
  reads the version from the files. This is a script in the repository,
  not part of the release zips.

### 0.3.9, 2026-09-13

<https://github.com/odipar/YMXR/releases/tag/v0.3.9>, built from the commit
tagged `v0.3.9`.

Only the program stub changed. The player cores are byte-identical to
0.3.8, so a tune file and a bound tune convert to the same bytes as
before. YMXS 0.3.2 and DTX 0.10.1 are unchanged.

- **A program with more than eighteen subtunes scrolled its list off the
  top of the screen.** The list printed one line per subtune under a
  five-line header, so twenty-five subtunes needed thirty rows on a
  twenty-five-row screen. The list is now laid out in columns: at most
  eighteen entries per column, as many columns as needed (twenty-five
  subtunes in two, ninety-nine in six), numbered down each column.
  Eighteen or fewer subtunes print exactly as before.
- On a 40-column screen, names are cut to thirteen characters so that
  everything fits; on 80 columns they fit in full. Every subtune stays
  numbered and selectable either way.
- The stub is 1,794 bytes, up from 1,674.

### 0.3.8, 2026-09-13

<https://github.com/odipar/YMXR/releases/tag/v0.3.8>, built from the commit
tagged `v0.3.8`.

Two changes to what the tools write, and one to the player. YMXS 0.3.2 and
DTX 0.10.1 are unchanged. A tune file written by 0.3.8 is not
byte-identical to one from 0.3.7, because it now records the tune's name;
0.3.7 tools read such a file without trouble, since the name is in a
field they skip.

- **A subtune could sound different depending on which subtune played
  before it.** Stopping a tune silenced the three volume registers but
  left every other chip register - tone, noise, mixer, envelope - as the
  tune had set them. A tune that does not set one of those on its first
  row then inherited the previous tune's value. The envelope shape was the
  worst case, because writing it restarts the envelope: Turrican world 4-3
  does not set it until row 161, and DBA 5 not until row 774. Stopping a
  tune now resets every register and switches every channel off in the
  mixer. Verified under Hatari: Turrican world 4-3 plays identically after
  Deeper and after Synergy Credits.
- The timers were checked and were not at fault; they are unchanged.
- **The tune file now records the tune's name.** Two header bytes that
  were always zero, and that no reader looked at, now point at a name
  string in UTF-8 of at most 255 bytes. `ym-to-ymxr` fills it from the YM
  dump's song name and `ymxs-to-ymxr` from the YMXS title; `ymx-to-ymxr`
  leaves it empty, because a `.ymx` dump has no name. `ymxr-multi` now
  names each subtune from `-nNAME` where one names it, otherwise from this
  field, otherwise from the filename, so a multi-tune program built from a
  directory of dumps shows real titles instead of filenames.
- The conformance kit was regenerated for this: each fixture built from a
  dump grew by its name, and `four-timers`, built without a dump, is
  unchanged.
- **The program now clears the screen every time it starts.** It used to
  do so only when the raster monitor was included, so a normal program
  drew its banner over the desktop's leftover pixels. Flag bit 0 of the
  stub descriptor is now always zero. The stub shrank to 1,674 bytes from
  1,682.
- Measured and documented in experiments.md, with the defaults unchanged:
  packing one tune with a 120-byte ring and `-copies` cuts its memory use
  by 36 to 43 percent, but a set of about six or more tunes ends up
  larger, because a multi-tune program allocates one shared workspace and
  pays the larger file once per tune.

### 0.3.7, 2026-09-13

<https://github.com/odipar/YMXR/releases/tag/v0.3.7>, built from the commit
tagged `v0.3.7`.

The tune data structure is YMXS 0.3.2 and the table format is DTX 0.10.1.
The 68000 sources are unchanged since 0.3.6, so the five binaries are the
same bytes, the tune file is version 3 and the bound tune is version 3,
and a dump converts to the file 0.3.6 wrote, byte for byte.

- **A structure with two sources under one name reads here.** YMXS 0.3.1
  made them an error of the form and 0.3.2 took the rule off: an effect
  names its source by the number of the table it stands in, so a name
  tells one from another in no form. A tune the tools of 0.3.6 rejected
  converts here.
- DTX 0.10.1 is the table format: its module is fetched by its import
  path, its twenty-two images are committed with it, and its rig reaches
  its tools again. No class, image or packaged byte of it moves, which
  `ConformanceTest` reads back: the eleven tunes of the kit are the bytes
  they were.

### 0.3.6, 2026-09-13

<https://github.com/odipar/YMXR/releases/tag/v0.3.6>, built from the commit
tagged `v0.3.6`.

The program a tool writes lists its subtunes, walks them and plays on to
the next. The player and the four cores are unchanged, so a tune file and
a bound tune read as 0.3.5 wrote them, both version 3, and the program stub
is 1,682 bytes against 836.

- **The set is on the screen, one subtune a line**: its number and the
  name the SNDH file's `!#SN` tag names it by, which `ymxs-to-sndh` writes
  from the tunes' titles. A file of one subtune has no such tag and its
  title is the name, read at a fixed place since `TITL` opens the tag
  block. The list writes a name whole.
- **A line says which subtune plays**, written over the line before it, so
  the list stays where it is. `Getrez` sets what the screen leaves a name:
  23 columns in low resolution and 63 in the other two.
- **The arrows walk the set**, left or up back and right or down on,
  wrapping at each end, so a set of any size is reached.
- **A number typed reaches past the ninth.** A digit joins the number
  before it, and half a second after the last digit it reaches the set: 1
  then 3 is 13, and 1 alone is 1. A digit that would put the number past
  the set starts it again at that digit, and a number no second digit can
  grow starts its subtune as the key is read.
- **A tune that ends starts the next one.** Bit 1 of the core's state byte
  marks a tune that has played its last row and repeats to no row, and the
  last ends at the first. A tune that repeats plays on, and a set of one
  has no next, so its tune goes quiet where it ends as 0.3.5 has it.

Driven under Hatari through its command fifo: the arrows read 02, 03, 04,
03, 02, 01, 02 and wrap both ways; 1 then 3 reads 13 and 1 then 0 reads
10; 4 alone reads 4 with no wait and 1 alone reads 1 once the pause runs
out; and three tunes that each play once read 01, 02, 03 three times over
with no key pressed.

### 0.3.5, 2026-09-13

<https://github.com/odipar/YMXR/releases/tag/v0.3.5>, built from the commit
tagged `v0.3.5`.

A conversion writes fewer bytes, and a player writes fewer MFP registers.
The 68000 sources are unchanged since 0.3.3, so the five binaries are the
same bytes, and the tune file is version 3 and the bound tune is version
3, as 0.3.0 set them. A file this release writes plays under 0.3.0 to
0.3.4 and a file those wrote plays here: the reading does not move, and
`doc/conformance/tunes/synergy.ymxr` is 7,596 bytes against 7,920 with
its reference record the same bytes.

- **A start on a running timer keeps the rate it finds.** A start wrote
  the control and count columns on every row, where a retune writes them
  only where they moved, so a row handing a running timer a second source
  at the rate it counts wrote that rate again and the player wrote two MFP
  registers it need not. SPEC.md 1.8 defines the reading, and now in as
  many words: a running timer runs at the rate the two columns set, or at
  the rate it already counts where the row sets neither.
- The encoder leaves the two columns 0 where no reset is asked, the select
  and the count are the ones in force, and the timer is known to be
  counting. A source that plays once clears that last, since it stops the
  timer at its marker and no row says when.
- A `.ymx` conversion is where it tells: YMX's `HOLD` that repatches a
  toggle's volume becomes a start of exactly that shape, and 58 to 92 per
  cent of the starts across the files with a dump beside them are one.

Measured over three `.ymx` tunes, a minute of each under Hatari against
the program YMX writes of the same file:

| | the rate columns | the packed table | MFP writes |
|---|---|---|---|
| Deeper | -6.7% | -2.6% | 6,536 to 3,634 |
| DitherDance | -35.3% | -14.3% | 5,352 to 1,472 |
| low | -12.4% | -4.7% | 7,118 to 2,792 |

Where this tree wrote 1.4 to 3.0 times YMX's timer traffic it writes less:
3,634 against 4,778, 1,472 against 1,800, 2,792 against 3,760. No frame
differs on a register no effect drives, the envelope restarts the rows ask
for are the same count both ways, and the end-of-interrupt counts move by
less than a tenth of a per cent.

### 0.3.4, 2026-09-12

<https://github.com/odipar/YMXR/releases/tag/v0.3.4>, built from the commit
tagged `v0.3.4`.

Every tool that reads a structure reads it the way `ymxs-check` does. The
68000 sources are unchanged since 0.3.3, so the five binaries are the same
bytes, the tune file is version 3 and the bound tune is version 3, and
`ym-to-ymxr` writes the file 0.3.3 wrote, byte for byte.

**The tune data structure is YMXS 0.3.1**, which puts the warnings of its
SPEC.md 6 where its errors already stood. `Ymxs.read` and `flags.Read`
call them, which is the one place each tree reads a structure, so
`ymxs-to-ymxr`, `ymxs-to-sndh` and `ymxs-to-prg` name a fault a writer left
in rather than converting in silence. `-silent` does not quiet a warning:
that flag is about what a tool reports of its work, and a warning reports what
the tune gets wrong.

What YMXS 0.3.1 reads that 0.3.0 did not:

- rule 1's warnings as one line a run, where a run of two hundred rows was
  two hundred warnings
- rule 2, at the row a second timer starts on a register another runs on
- rule 1 at the wrap, where an effect running after the last row runs on
  into the row the tune repeats to
- a new rule 6: a `Stop` of a timer the tune has not started, with the row
  it repeats to excepted
- a rate no 68000 services, as an error: past 125,000 ticks a second a
  68000 at 8 MHz spends every cycle it has entering interrupts and leaving
  them
- a source no row starts, and two sources under one name, as errors of the
  form
- a JSON number past what an int reads, rejected in both trees

The rules are silent over `ym/test` through `ymxs-to-prg` and over the four
files under `ymx/test` through `ymxs-to-ymxr`, and over the 543 tunes of
the corpus in YMXS.

### 0.3.3, 2026-09-12

<https://github.com/odipar/YMXR/releases/tag/v0.3.3>, built from the commit
tagged `v0.3.3`.

The 68000 binaries move: the four cores are 38 bytes larger and the program
stub 8 bytes smaller. The tune file is version 3 and the bound tune is
version 3, as 0.3.0 set them, so a file of this release plays under 0.3.0
to 0.3.2 and the other way round, and `ym-to-ymxr` writes the file 0.3.2
wrote, byte for byte.

- **A handler at `$60` while a tune plays.** The MFP raises an interrupt
  and the 68000 acknowledges it an instruction or more later. A write of
  the player's that clears a pending bit or an enable bit inside that
  window leaves the MFP with no vector to place on the bus, and the 68000
  runs exception 24 rather than the timer's handler. The SNDH core keeps
  the host's vector at `$60` and puts an `rte` there at init, beside the
  four timer vectors it already keeps, and exit puts the host's back. The
  tick that acknowledge stood for is the one the write cancelled, so
  returning is the whole handler. Raising the interrupt level around the
  write is no substitute: an acknowledge the 68000 has begun runs to its
  end at any level, and the MFP sets a pending bit whether the 68000 masks
  or not.
- The player is untouched. It saves and restores no vector, and its
  header names `$60` among what a host keeps around it. No cycle of a play
  call or a tick moves: init and exit alone.
- **A tune that ends stops no program.** The program left the machine the
  moment the core's state byte marked the tune over, so a tune that plays
  once played once and the desktop came back, and a set of subtunes ended
  with the first one that ran out. SPACE and ESC end the program now, and
  the rows patched in where a caller names a count. A tune that plays
  its last row goes quiet and the keys stand, so another subtune is one
  keypress away.
- The banner named SPACE alone, where ESC has stopped the program since
  the stub was written. It names both. The rows field of the stub's
  descriptor reads as it always did, and 0 in it means what the stub always
  did with it: play on. Every document and both trees called that "the
  tune's row count", which was true only of a tune that plays once.
- SPEC.md 4 defines what reaches the chip, which section 7 defined for a
  reader's record alone: a player writes the column byte whole and each
  register drops the bits it does not have, so a row whose R13 column is
  `$AE` runs shape `$E`. The player has written that since it was written,
  and `ConsistencyTest` reads the widths back against the mask tables of
  the rig and `ym/parity.py`.

The rig drives the SNDH core's three entries under the emulator for the
first time and reads `$60` before init, after it and after exit. On
Hatari, a program of this release plays its rows and traces no line of ROM
after its last write, where the same program under 0.3.2 left 502,132
lines of desktop behind it.

**The spurious interrupt is gone on hardware.** 0.3.1 is the release the
fault was reported against, and it does not appear on an ST with this
release's handler in place.

### 0.3.2, 2026-09-11

<https://github.com/odipar/YMXR/releases/tag/v0.3.2>, built from the commit
tagged `v0.3.2`.

Three faults in the YMX conversion. The tune file is version 3 and the
bound tune is version 3, as 0.3.0 set them, so a file of this release plays
under 0.3.0 and 0.3.1 and the other way round. The 68000 sources are
unchanged since 0.3.1, so the five binaries are the same bytes.

- **A `.ymx` that plays once converted to one that starts over.** Bit 0 of
  the header's flags parts the two: `L` is 0 in a tune that plays once and
  0 in one that starts over from its first frame (YMX, SPEC.md 1.2), and
  neither reader read the flags word. A play-once file converted to a table
  repeating at row 0, and the SNDH its tools wrote named `FRMS 0` where
  YMX's named the tune's frame count.
- **A count of 0 was replaced by the count the channel already ran.** The
  MFP counts 256 at a count of 0, the slowest count the format reaches. The
  conversion dropped it under a comment citing the bound 0.3.0 removed when
  control bit 4 was assigned. One row of the four files under `ymx/test`
  reloads a timer to 0, and that stream ran 7% fast from there to its next
  reload.
- **A flag byte was read as a prescaler select.** An action byte's low bits
  are an index only for the five opcodes that program a timer; `HOLD`,
  `RELEASE` and a voiced `RESUME` read them as flags (YMX, SPEC.md 2.4).
  The note about a count or a select of 0 fired on 2,017 rows across the
  four files under `ymx/test`, and 2,016 of those were flag bytes, so the
  note is gone. An index of 0 on an opcode that programs is a malformed
  file, and that row produces a note and is left behind.

`ym/parity.py` reads a play-once `.ymx` against YMX's program 674 of 674
frames alike, where before the change 35 frames differed on R10 and R11,
registers no effect drives. `YmxTest` reads the corpus back on every build
with no emulator: the repeat each file's flags name, and the count and the
prescaler of all 7,477 rows that set a timer's rate.

`ym-to-ymxs`, `ym-to-ymxr` and every tool downstream of the structure are
untouched: a dump converts to the file 0.3.1 wrote, byte for byte, and
`ConformanceTest` pins that.

**A spurious interrupt on real hardware is not fixed here.** The remedy is
a dummy handler at vector `$60`, as 0.3.1 records.

### 0.3.1, 2026-09-11

<https://github.com/odipar/YMXR/releases/tag/v0.3.1>, built from the commit
tagged `v0.3.1`.

The pending register written whole, and three documents measured again.
The tune file is version 3 and the bound tune is version 3, as 0.3.0 set
them, so a file of this release plays under that one and the other way
round.

- **A tick the MFP latches during a clear is no longer dropped.** A
  pending bit clears on a written 0 and is unmoved by a written 1, and
  `bclr` read the byte and wrote it back: two bus cycles, with the MFP
  free to latch a tick of another timer between them, which the
  write-back then carried the read's 0 over. The five clears write the
  whole byte now. Raising the interrupt level is no substitute, since the
  MFP sets the bit whether the 68000 masks or not.
- The tick that ends a source costs 132 cycles rather than 136, and 116
  rather than 120 with the lean switch. No other tick reaches that path,
  and the five binaries are the size they were. `PERF_STOP` is 116, the
  figure the raster monitor bills a stop tick at.
- performance.md's play call and plan.md's closing figures are the ones
  the rig counts. The call moved where 0.3.0 assigned bit 4 and neither
  document was read again, and plan.md's ticks read 4,463 against 2,585.

**A spurious interrupt on real hardware is not fixed here.** A player that
changes a timer's parameters while an interrupt on that timer is live
races the 68000's acknowledge, and the remedy is a dummy handler at vector
`$60`. The fault is measured against this release.

### 0.3.0, 2026-09-11

<https://github.com/odipar/YMXR/releases/tag/v0.3.0>, built from the commit
tagged `v0.3.0`.

**The tune file is version 3 and the bound tune is version 3.** A file of
either older version is rejected by the tools and by the player here, and a
file this writes is rejected by 0.2.0's.

- **A count of 0 is a value.** The count column is the timer's data
  register, and every value of that register is a count: 1 counts one tick
  and 0 counts 256. The column reserves 0 for the row that does not set it,
  and bit 4 of the control column beside it marks that 0 as a value, as the
  bit beside a tone period byte marks its 0 (SPEC.md 1.1, 1.9). Bit 4 was
  unassigned, and R6.2 puts an assignment in a later version, which is why
  the version moves.
- The player masks the control column to bits 2 to 0 before it writes the
  select, so bit 4 never reached the MFP and is free to read. A bend, a row
  that moves the count alone, sets no control column and is untouched.
  The five binaries are 48 bytes larger, two instructions an effect.
- SPEC.md 1.9 said bits 4 to 0 belong to the register and a player writes
  them as the column has them. The player writes bits 2 to 0, and has since
  it was written.

**The tune data structure is YMXS 0.3.0**, where the count is the timer's
data register, 0 to 255. A structure of version 2 is read by no tool here.

The conformance kit is rewritten at the new version, and its wrong-version
tune at 4.

### 0.2.0, 2026-09-11

<https://github.com/odipar/YMXR/releases/tag/v0.2.0>, built from the commit
tagged `v0.2.0`.

**The tune data structure is YMXS 0.2.0, and `ym-to-ymxs` and
`ymx-to-ymxs` write version 2.** A row and a frame have one word each
there now: the tune key `frames` was the row count under a frame's name
and is `rows`, and the key `rows` was the columns a register and is
`registers`. The CSV form follows.

- A structure of version 1 is read by no tool here, and one this writes is
  read by no release before this. The tune file, the SNDH file and the
  program are untouched: their formats stand at the versions 0.1.0 set,
  and a `.ymxr` from 0.1.2 plays here.
- No file in this repository names those keys. Every conversion reads and
  writes the structure through the library, so the move is the dependency
  and what the library writes.

The five 68000 binaries are the same bytes as 0.1.0's, and the format
versions stand: the tune file's is 2 and the bound tune's is 2.

### 0.1.2, 2026-09-11

<https://github.com/odipar/YMXR/releases/tag/v0.1.2>, built from the commit
tagged `v0.1.2`.

The report three of the tools print where they are not silenced.

- `ymxr-sndh` left out the core's bytes, the `!#SN` tag, what each image
  fixes with the tunes that share it, and the file's parts. The image
  rows are the figure that reports whether a set of subtunes shares DTX's
  reader, and a release before this one had no row to read it from.
- `ymxr-bind` printed one line where five belong: the image's place and
  bytes, the source tables, the state block the host finds the workspace
  for, and what the binding came to over the tune file.
- `ymxr-prg` left out the stub's bytes, the clock it plays from and what
  becomes of the screen, both of which follow the core.

The three printed less than the Java tools they are read against, which
`ParityTest` missed because it ran every tool with `-silent`. It runs
them without the flag now, and reads a set of subtunes that shares one
image against a set split over two, so a report that drifts from the Java
tree fails the build.

Every file a tool writes is unchanged: a tune file, an SNDH file or a
program from 0.1.1 is byte for byte what 0.1.2 writes, and the five
68000 binaries an executable contains are the same bytes. The report is
the one field that moved.

### 0.1.1, 2026-09-11

<https://github.com/odipar/YMXR/releases/tag/v0.1.1>, built from the commit
tagged `v0.1.1`.

A released executable reads a `.ymx` with no other program installed.

- `ymx-to-ymxs` and `ymx-to-ymxr` ran YMX's `ymx-dump` and read the values
  it printed, so a released executable stopped with `cannot run ymx-dump`
  unless `YMX_DUMP` named a built copy of that tool. The two decode the
  file with YMX's reader now, `github.com/odipar/ymx/go`, which an
  executable contains: all thirteen run as they stand.
- The Java tools go on running `ymx-dump`, which `YMX_DUMP` names, since
  YMX publishes no Java artifact for the reader. `ParityTest` reads the
  four files under `ymx/test` through both trees, so the two routes write
  one tune file, one JSON and one report.
- `release/publish.sh` runs the host's executables with an empty
  environment and puts a `.ymx` through them beside the dump, so a release
  that reads one only where `ymx-dump` stands beside it is caught before it
  is published.

The 68000 sources are unchanged since 0.1.0, so the five binaries an
executable contains are the same bytes and the format versions stand:
this release changes the tools only.

### 0.1.0, 2026-09-11

<https://github.com/odipar/YMXR/releases/tag/v0.1.0>, built from the commit
tagged `v0.1.0`.

The first release: the player, the format, the binaries and the thirteen
tools.

- The player of SPEC.md, its four effects on the MC68901's timers, and
  the four cores of BINARIES.md: the plain one, the one with the raster
  monitor in, the lean one and the one that is both.
- The tune file (SPEC.md 3.3), the bound tune, the SNDH file and the
  program stub, defined byte for byte in BINARIES.md.
- Every conversion passes through YMXS, the tune data structure
  (doc/ymxs.md): a dump or a YMX file is read into it and a schema maps
  that onto the columns.
- Thirteen tools, each reading standard input and writing standard
  output: the two converters, the structure's five filters, the multi
  file's maker, the binder, the two combiners, the trace and the check.
- The same thirteen in Go under `go/`, which `ParityTest` runs against
  the Java tree byte for byte. A release ships those, one zip a platform.
- The conformance kit (doc/conformance), the emulation rig
  (68k/test/emu) and the parity rig against YMX (ym/parity.py).
