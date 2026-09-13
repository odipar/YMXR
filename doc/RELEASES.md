# RELEASES

What a release contains stands here, and each one published is listed
below it.

## What a release contains

`release/publish.sh` writes `dist/release` (tools.md), and
`release/manifest.sh` the manifest in it:

- one zip a platform, over six: Windows, macOS and Linux, each on x64 and
  arm64. A zip contains the thirteen tools as executables, and each
  executable contains the five 68000 binaries of BINARIES.md and DTX's
  twenty-two images, so one converts a dump and writes a program on a
  machine where neither this repository nor a toolchain is installed
- `MANIFEST.txt`: every zip's size and sha256, what it contains, and the
  source commit the release was built from

The version names every file. It is read out of `pom.xml`, or stands as
the script's one argument.

The five binaries are assembled from `68k/` by rmac on the machine that
cuts the release, so the caller's machine has an assembler to install or
not as it pleases. They are committed under `go/binaries/data`: a Go module
fetched by its import path contains the files a commit has in it, and an
executable built from one embeds these. `BinariesTest` reads them against
the assembly the build makes, so one that does not match what rmac writes
today fails the build.

A player pins a version of this format: the tune file's is 3 (SPEC.md 3.3)
and the bound tune's is 3 (BINARIES.md 1), and a release's number names
the tools rather than either.

## Published

### 0.3.8, 2026-09-13

<https://github.com/odipar/YMXR/releases/tag/v0.3.8>, built from the commit
tagged `v0.3.8`.

The tune data structure is YMXS 0.3.2 and the table format is DTX 0.10.1,
as 0.3.7 had them. The tune file is version 3 and the bound tune is version
3. A dump does not convert to the file 0.3.7 wrote: the tune file records
what the tune is called now, and the name and its zero are bytes 0.3.7 did
not write. A reader of 0.3.7 passes over the word that records it and reads
such a file as it always did.

- **A subtune played differently by what was selected ahead of it.**
  `YMXR_stop` silenced the three voices and left every other register where
  the tune had written it, and a row sets the registers it names and no
  others, so a tune whose first row leaves one unset read the value the
  tune before it wrote. The envelope shape reaches furthest, since writing
  R13 retriggers the generator. Two tunes of the corpus set it late:
  Turrican - world 4-3 at row 161 and DBA 5 at row 774. Under Hatari,
  Turrican - world 4-3 after Deeper read R13 as 0 for its first 161 frames
  and after Synergy Credits as 8. The stop writes every register zero and
  the mixer with each channel off now, the voices first so the rest is
  written into silence, and the two read alike on all fourteen registers.
  The SNDH core is 4,710 bytes against 4,686.
- The timers were not the fault, and are not changed. A release of a
  claimed timer stops it, clears its enable and its mask and drops its
  pending, and init clears the four effect records and re-seeds the rings.
  A tune using no timer reads alike after a tune using two and after one
  using three.
- **The tune file records what the tune is called.** Bytes 10 and 11 were
  zero and no reader read them; they are the name's offset now, and the
  name lies between the source index and the DTX2 table, in UTF-8 ended by
  a zero, at most 255 bytes. `ym-to-ymxr` writes the dump's song name and
  `ymxs-to-ymxr` the tune's title; `ymx-to-ymxr` writes none, since a
  `.ymx` dump has none to read. `ymxr-multi` names a subtune by `-nNAME`
  where one names it, else by the name the file records, else by the file,
  so a set built from a directory of dumps lists tunes rather than
  filenames.
- The conformance kit is written again for that: every fixture from a dump
  grows by its name and the zero, chambers by 36 bytes for a title of 33
  characters, and `four-timers`, which is built without a dump, is the
  bytes it was.
- **The program clears the screen every run.** It cleared it where the
  SNDH file's core had the raster monitor in and left the desktop's pixels
  otherwise, so a plain program drew its banner and its list of subtunes
  over them. Bit 0 of the stub descriptor's flags word is zero from here
  and a caller never chose it. The stub is 1,674 bytes against 1,682.
- The ring a small program packs at, and where it stops paying for a set,
  are measured in experiments.md: a ring of 120 bytes with copies cuts
  what one tune occupies by 36 to 43 percent, and a set of about six
  tunes or more packs larger, since a host allocates one workspace for a
  set and the bytes are paid a tune. The defaults are unchanged.

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
a bound tune are what 0.3.5 wrote, both version 3, and the program stub
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
that flag is about what a tool reports of its work, and a warning is what
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
  descriptor is what it always was, and 0 in it means what the stub always
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
`$60`. This release is what the fault is measured against.

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
what moved.

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
