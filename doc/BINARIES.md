# BINARIES

The prebuilt binaries, and how a tool combines them with a tune file
without an assembler: the SNDH core, the player under SNDH's three
entries, and the program stub, a block that drives an SNDH file as a TOS
program, each assembled once by the build and kept in the jar; DTX's
reader code, in the dtx jar; and the tune file, which contains the tune's
tables and no code. Combined they make a bound tune, an SNDH file any
SNDH host plays, and a program around it. Any tool that follows this
document writes files of the same layout; what remains particular to each
tool is
the tag text, and the workspace above the floor of section 2.
Big-endian throughout; every offset and size in bytes.

| file | contents |
|---|---|
| `YMXR_sndh.bin` | the SNDH core: the player and its SNDH glue, assembled from `68k/YMXR_sndh.S` |
| `YMXR_sndh-perf.bin` | the same core with the player's raster monitor assembled in (`YMXR_PERF`, performance.md), for reading a run |
| `YMXR_sndh-lean.bin` | the same core whose ticks neither drop the interrupt level nor write an end of interrupt (`YMXR_NEST=0` and `YMXR_AEOI=1`, performance.md), 32 cycles cheaper on a tick that writes a row and 16 on one that ends a source, for a host where no MFP interrupt of the host nests and the MFP's vector register belongs to the player |
| `YMXR_sndh-perf-lean.bin` | the same core with both switches set: the raster monitor reads what a run costs, and the ticks it reads are the lean ones |
| `YMXR_prg.bin` | the program stub, assembled from `68k/YMXR_prg.S` |
| `DTX0.bin`, `DTX1-w1.bin`, `DTX2-w1-k1.bin` and the rest | DTX's reader code, twenty-two files: one for DTX0, one a width for DTX1, one a width, a unit and the copies flag for DTX2, at `org/dtx/68k/` in the dtx jar (DTX, doc/abi.md) |

The build assembles the four cores and the stub into `org/ymxr/68k/` on
the classpath (pom.xml, the `binaries` step; `-Drmac=PATH` names the
assembler), and the tools of section 5 read them there.

## The stack

```
+----------------------------------------------+
| PRG header, 28 bytes                         |
| program stub, patched (4)                    |
|  +--------------------------------------+    |
|  | entry triple and tags (3)            |    |
|  | SNDH core, patched (2)               |    |
|  | subtune table                        |    |
|  | bound tune 1 (1)                     |    |
|  | ...                                  |    |
|  | bound tune n                         |    |
|  | workspace, zero bytes                |    |
|  +--------------------------------------+    |
| relocation table, one zero long              |
+----------------------------------------------+
```

The inner box is the SNDH file of section 3, which any SNDH host plays
as it stands; section 4 adds the outer box. The assembler produced the
core and the stub; every other byte is the tool's or the tune's.

## 0. The multi file

Several tune files in one, a name each. A set of subtunes reaches a tool
as one file rather than as several file names, so every tool reads one
input and writes one output. A player reads a tune file (SPEC.md 3.3) or
the bound tune of section 1, and never this.

| offset | bytes | what it is |
|---|---|---|
| 0 | 4 | `YMXM` |
| 4 | 2 | the version, $0003, which is the version of the tune files in it |
| 6 | 2 | `N`, the tune count, 1 to 99 |
| 8 | 8`N` | one entry a tune: 4 where its tune file begins, 4 the tune file's bytes |
| | | the names, in the entries' order, each ended by a zero byte |
| | | the tune files, each on a long, in the entries' order |

A tune's name is the text a subtune is called by, and empty where the
file records none for it. An entry records where a tune file begins and
what it measures, so each is read out as it stands and binds as one
written by itself: a multi file adds no byte to a tune and moves none.

## 1. The bound tune

What the player reads: the tune file's tables, with DTX's image for the
DTX2 table in place of the table. The tune file (SPEC.md 3.3) has the
tables and no code; the image is the table with DTX's reader code and
its column table in front, which DTX's packager makes for the table's
unit and copies flag (DTX, abi.md 1). Binding is the packager's combine,
and no row moves.

| offset | bytes | what it is |
|---|---|---|
| 0 | 4 | `YMXB` |
| 4 | 2 | the version, $0003 |
| 6 | 2 | the frame rate, in Hz, from the tune file |
| 8 | 1 | effects used, from the tune file |
| 9 | 1 | `S`, the source count, from the tune file |
| 10 | 2 | zero |
| 12 | 4 | the state block's bytes, from the image's format block |
| 16 | 4 | where the image begins, signed |
| 20 | 4 | where this tune's table stands, from the image's first byte |
| 24 | 4`S` | the source index: where the table of source 1 to `S` begins |
| | | the image, on a long, where this tune has a separate one |
| | | the DTX1 tables, each on a long, as the tune file has them |

**One image, one table or several.** An image contains the reader's code
once, and DTX's init reads the header of the table to read (DTX, abi.md
2), so tunes that agree on what an image fixes once - the variant, the
width, the unit, the copies flag and the ring - go into one image and the
code stands once for them. A bound tune written by itself has its image
in it and reaches it forwards; one of a set reaches the set's image,
which stands before it, so the offset at +16 is negative there. The field
at +20 names this tune's table either way.

The player reads the magic and the version at init and rejects another
of either, reads the effects byte, the image, the table and the index,
and never reads the state block's bytes: a host reads them, to size the
player a workspace of `YMXR_FIXED` plus that many bytes, on a long. `R`
and `RR` it reads out of the table's header (DTX, SPEC.md 1), since
`DTX_metadata` reports the image's first table and a set shares one.

## 2. The SNDH core

Position-independent. Its layout from its first byte:

| offset | bytes | what it is |
|---|---|---|
| 0 | 4 | `bra.w` to init: the entry an SNDH file's triple reaches |
| 4 | 4 | `bra.w` to exit |
| 8 | 4 | `bra.w` to play |
| 12 | 4 | `YMXS` |
| 16 | 2 | the descriptor's version, 1 |
| 18 | 2 | the bound tune's version this core reads |
| 20 | 2 | `YMXR_FIXED`, the workspace's bytes before the state block |
| 22 | 2 | flags: the bits are below |
| 24 | 2 | where the core's state byte is |
| 26 | 2 | zero |
| 28 | 4 | the subtune table: written 0, patched by the tool |
| 32 | 4 | the workspace: written 0, patched by the tool |

The flags word:

| bit | set where the core was assembled with | which the host then provides |
|---|---|---|
| 0 | the player's raster monitor (`YMXR_PERF`, performance.md) | that it read the palette writes to have what the run cost, and clear the screen for the bars |
| 1 | the lean tick (`YMXR_NEST=0` and `YMXR_AEOI=1`, performance.md) | that no MFP interrupt of the host nest inside another, and that the MFP's vector register be the player's to set |

The two bits are a switch each: a core is assembled with either, both or
neither.

Both patched offsets count from the core's first byte and are even. A
tool selects the core by name and checks it against the flags word, and
stops
where the two part. The state byte has bit 0 set while a tune plays and
bit 1 set once the tune has played its last row and does not repeat; a
host that has to know when a tune that plays once is over reads it,
since play returns no value.

The subtune table: a word count `N`, then `N` longs, each a bound tune's
offset from the core's first byte, each even. Init with the subtune `s`
in `d0.w`, 1 up, plays the tune at entry `s`; one out of range plays the
first.

The workspace: `YMXR_FIXED` plus the largest state block over the set's
bound tunes, rounded up to a long, plus two bytes, zero bytes, last in
the file. The player requires its workspace on a long and an SNDH host
loads the file on an even address, so the core rounds the workspace's
address up to a long, and the two bytes leave room for that.

Init keeps the four timers' vectors, control, enable and mask bits as it
finds them, calls the player's init on the subtune's bound tune and the
workspace, and records which timers the tune claims from its effects
byte: effects 0 to 3 run Timers A, D, B and C. Exit stops the player and
puts the claimed timers back as they were kept; a timer the tune does
not run is never touched. No data register is kept: it reads as the live
count and writes as the reload, so a count written back sets a rate no
one asked for, and a host that needs a timer's rate back writes the
value it knows. Play calls the player's play. Each entry keeps every
register.

## 3. The SNDH file

In order:

1. **The entry triple**: three `bra.w`, at 0, 4 and 8, to the core's
   three entries, each the word `$6000` and a displacement, which is the
   core's offset less 2 for all three.
2. **The tag block**, `SNDH` through `HDNS`: `TITL` and the title, `COMM`
   and the composer where the file has one, `CONV` and the converter's
   name,
   `##` and two digits, the subtunes, `TC` and the rate in decimal, `FLAG`
   and `~`, a letter for each timer the set claims, `a` to `d`, and `y`,
   each text ended by a zero byte and the block padded to an even length,
   `FRMS` and a long a subtune: the rows of a tune that plays once, 0 for
   one that repeats, `!#SN` and a word a subtune, where the file names
   them, each the offset of that subtune's name from the tag's first
   byte,
   the names each ended by a zero byte, a pad to an even length, and
   `HDNS`.
3. **The core**, with its two offsets patched.
4. **The subtune table** (2).
5. **The images**, each on a long: one a group of subtunes that agree on
   what an image fixes once (1), so DTX's reader code stands once for
   the
   group rather than once a subtune.
6. **The bound tunes**, each on an even address, each reaching its image
   backwards from its first byte.
7. **The workspace** (2), last.

Rules the tool keeps: every bound tune is of the version the core reads,
one rate across the set for the `TC` tag, and at most 99 subtunes, the
two digits of `##`.

The images are what a set of subtunes saves. Ten of the tunes under
`ym/test` fall into two groups, one a unit, so eight copies of the reader
come off the file: 88,288 bytes against the 100,160 a copy a subtune took.

## 4. The program stub

Position-independent, raw and even-sized; the SNDH file begins after
the stub's last byte. Its layout from its first byte:

| offset | bytes | what it is |
|---|---|---|
| 0 | 4 | `bra.w` to the program |
| 4 | 4 | `YMXT` |
| 8 | 2 | the descriptor's version, 1 |
| 10 | 2 | the subtunes: patched by the tool |
| 12 | 2 | flags: patched; the bits are below |
| 14 | 2 | the rate, rows a second: patched from the `TC` tag |
| 16 | 4 | the rows to play: patched; 0 plays the tune's row count |
| 20 | 4 | the core's offset from the SNDH file's first byte: patched |

The flags word:

| bit | set by the tool where | the stub then |
|---|---|---|
| 0 | the core has the raster monitor in | clears the screen before the banner, so that the monitor's bars stand where the desktop's pixels were |
| 1 | the set claims Timer C | plays from the VBL; with the bit clear, from the VBL where the screen's rate is the tune's and from Timer C where it is not |

The VBL is the screen's clock: 50 or 60 Hz by the sync bit, 71 in
high resolution. A set that claims Timer C leaves the stub no timer to
play from, so it plays from the VBL, and the tool checks such a set
against
50 Hz, the rate of the screen the tune is written for; on another
screen it plays at that screen's rate. With the bit clear the stub reads
the screen's rate, uses the VBL where it is the tune's, and otherwise
runs Timer C at 200 Hz, counts the rate against 200 and plays a row for
every 200 the count reaches, the remainder carried, so a rate that does
not divide 200 lands its rows over the second without drift, and a rate
above 200 lands more than one row on a tick.

A program from the stub, in order:

1. **The PRG header**, 28 bytes: `$601A`, the text size, the stub and the
   SNDH file together, then the data, bss, symbol, reserved and flags
   longs, all 0, and a zero absflag word.
2. **The stub**, with its descriptor patched: the subtunes and the rate
   out of the SNDH file's `##` and `TC` tags, the core's offset
   found by its `YMXS`.
3. **The SNDH file.**
4. **The relocation table**: one zero long, since no address in the stub
   or
   the SNDH file is relocated.

The program prints the SNDH file's address, claims the machine under
Supexec, keeping the VBL vector, the four timers' vectors and the
enable, mask and control registers, and turns every MFP interrupt off
and stops the four timers; calls init with subtune 1 and plays from the
VBL or Timer C; stops on SPACE or ESC, or once the rows patched in have
played, or once the core's state byte says the tune is over; switches
subtunes on 1 to 9, offering in its banner the keys the set has; and
hands the machine back with the mouse reporting again, Timer C's count
written as the 192 of the system's 200 Hz. The keyboard is read at its
ACIA, every IKBD report read whole, and the mouse turned off at the
chip while the program runs, so that TOS finds no packet half read when
it has the keyboard back.

## 5. Driving play, the host's side

An SNDH file is passive: init sets the tune up and claims the timers its
effects run, and something outside the file calls play at the tune's
rate. The `TC` tag addresses that caller: it fixes the rate, and names
Timer C as the interrupt a desktop host makes the calls from, by the
convention that a host chains the operating system's 200 Hz Timer C and
counts the rate against 200. The `FLAG` letters list the timers the set
claims, so a host that ticks from a timer picks one the tunes do not
run.

The tools, each reading standard input and writing standard output:
`bin/ymxr-multi` writes the multi file of section 0 from the tune files
named, with `-nNAME` a tune; `bin/ymxr-bind` the bound tune;
`bin/ymxr-sndh` the SNDH file, with `-tTITLE`, `-cCOMPOSER`, `-perf` for
the monitor's core and `-lean` for the lean one, which are a switch each;
`bin/ymxr-prg` the program, with `-rROWS` for the rows. Each says what it
made, on standard error, and `-silent` turns that off. tools.md has them,
and `ym/cost.sh` reads a monitor run back.
