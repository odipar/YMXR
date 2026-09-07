# BINARIES

The prebuilt binaries, and how a tool combines them with a tune file
without an assembler: the SNDH core, the player under SNDH's three
entries, and the program stub, a block that drives an SNDH file as a TOS
program, each assembled once by the build and kept in the jar; DTX's
reader images, in the dtx jar; and the tune file, which holds the tune's
tables and no code. Combined they make a bound tune, an SNDH
file any SNDH host plays, and a program around it. Any tool that follows
this document writes files of the same layout; what stays each tool's
own is the tag text, and the workspace above the floor of section 2.
Big-endian throughout; every offset and size in bytes.

| file | contents |
|---|---|
| `YMXR_sndh.bin` | the SNDH core: the player and its SNDH glue, assembled from `68k/YMXR_sndh.S` |
| `YMXR_prg.bin` | the program stub, assembled from `68k/YMXR_prg.S` |
| `dtx2-w1-k1.bin` and the rest | DTX's reader images, one a variant, in the dtx jar (DTX, abi.md) |

The build assembles the core and the stub into `org/ymxr/68k/` on the
classpath (pom.xml, the `binaries` step; `-Drmac=PATH` names the
assembler), and the tools of section 5 read them there. A release
attaches them with a manifest of their sizes and digests.

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

## 1. The bound tune

What the player takes: the tune file's tables, with DTX's image for the
DTX2 table in place of the table. The tune file (SPEC.md 3.3) has the
tables and no code; the image is the table with DTX's reader code and
its column table in front, which DTX's packager makes for the table's
unit and copies flag (DTX, abi.md 1). Binding is the packager's combine,
and nothing in the rows moves.

| offset | bytes | gives |
|---|---|---|
| 0 | 4 | `YMXB` |
| 4 | 2 | the version, $0001 |
| 6 | 2 | the frame rate, in Hz, from the tune file |
| 8 | 1 | effects used, from the tune file |
| 9 | 1 | `S`, the source count, from the tune file |
| 10 | 2 | zero |
| 12 | 4 | the state block's bytes: the image's format block gives them |
| 16 | 4 | where the image begins |
| 20 | 4`S` | the source index: where the table of source 1 to `S` begins |
| | | the image, on a long |
| | | the DTX1 tables, each on a long, as the tune file has them |

The player reads the magic and the version at init and rejects another
of either (its source gives both), reads the effects byte, the image and
the index, and never reads the state block's bytes: a host reads them,
to give the player a workspace of `YMXR_FIXED` plus that many bytes,
on a long.

## 2. The SNDH core

Position-independent. Its layout from its first byte:

| offset | bytes | gives |
|---|---|---|
| 0 | 4 | `bra.w` to init: the entry an SNDH file's own triple reaches |
| 4 | 4 | `bra.w` to exit |
| 8 | 4 | `bra.w` to play |
| 12 | 4 | `YMXS` |
| 16 | 2 | the descriptor's version, 1 |
| 18 | 2 | the bound tune's version this core reads |
| 20 | 2 | `YMXR_FIXED`, the workspace's bytes before the state block |
| 22 | 2 | where the core's state byte is |
| 24 | 4 | the subtune table: written 0, patched by the tool |
| 28 | 4 | the workspace: written 0, patched by the tool |

Both patched offsets count from the core's first byte and are even. The
state byte has bit 0 set while a tune plays and bit 1 set once the tune
has played its last row and does not repeat; a host that has to know
when a tune that plays once is over reads it, since play returns
nothing.

The subtune table: a word count `N`, then `N` longs, each a bound tune's
offset from the core's first byte, each even. Init with the subtune `s`
in `d0.w`, 1 up, plays the tune at entry `s`; one out of range plays the
first.

The workspace: `YMXR_FIXED` plus the largest state block over the set's
bound tunes, rounded up to a long, zero bytes, last in the file.

Init keeps the four timers' vectors, control, data, enable and mask bits
as it finds them, calls the player's init on the subtune's bound tune
and the workspace, and records which timers the tune claims from its
effects byte: effects 0 to 3 run Timers A, D, B and C. Exit stops the
player and puts the claimed timers back as they were kept, the data
before the control; a timer the tune does not run is never touched. Play
calls the player's play. Each entry keeps every register.

## 3. The SNDH file

In order:

1. **The entry triple**: three `bra.w`, at 0, 4 and 8, to the core's
   three entries, each the word `$6000` and a displacement, which is the
   core's offset less 2 for all three.
2. **The tag block**, `SNDH` through `HDNS`: `TITL` and the title, `COMM`
   and the composer where one is given, `CONV` and the converter's name,
   `##` and two digits, the subtunes, `TC` and the rate in decimal, `FLAG`
   and `~`, a letter for each timer the set claims, `a` to `d`, and `y`,
   each text ended by a zero byte and the block padded to an even length,
   `FRMS` and a long a subtune: the rows of a tune that plays once, 0 for
   one that repeats, `!#SN` and a word a subtune, where a name is given,
   each the offset of that subtune's name from the tag's own first byte,
   the names each ended by a zero byte, a pad to an even length, and
   `HDNS`.
3. **The core**, with its two offsets patched.
4. **The subtune table** (2).
5. **The bound tunes**, each on an even address.
6. **The workspace** (2), last.

Rules the tool keeps: every bound tune is of the version the core reads,
one rate across the set for the `TC` tag, and at most 99 subtunes, the
two digits of `##`.

## 4. The program stub

Position-independent, raw and even-sized; the SNDH file begins at the
stub's last byte. Its layout from its first byte:

| offset | bytes | gives |
|---|---|---|
| 0 | 4 | `bra.w` to the program |
| 4 | 4 | `YMXT` |
| 8 | 2 | the descriptor's version, 1 |
| 10 | 2 | the subtunes: patched by the tool |
| 12 | 2 | flags: patched; the bits are below |
| 14 | 2 | the rate, rows a second: patched from the `TC` tag |
| 16 | 4 | the rows to play: patched; 0 plays as many as the tune gives |
| 20 | 4 | the core's offset from the SNDH file's first byte: patched |

The flags word:

| bit | set by the tool where | the stub then |
|---|---|---|
| 0 | the caller asked for it | paints the background around each play call, red while it runs and yellow at its end: a trace of the palette writes gives the call's cycles |
| 1 | the rate is 50, or the set claims Timer C | plays from the VBL, and from Timer C with the bit clear |

Bit 1 takes two cases because the stub does one thing for both: a set
that claims Timer C leaves the stub no timer to play from, and the VBL
is a 50 Hz clock either way, so a set that claims Timer C at another
rate stops the combine. With the bit clear the stub runs Timer C at
200 Hz, counts the rate against 200 and plays when the count crosses,
the remainder carried, so a rate that does not divide 200 lands its
rows over the second without drift.

A program from the stub, in order:

1. **The PRG header**, 28 bytes: `$601A`, the text size, the stub and the
   SNDH file together, then the data, bss, symbol, reserved and flags
   longs, all 0, and a zero absflag word.
2. **The stub**, with its descriptor patched: the subtunes and the rate
   out of the SNDH file's own `##` and `TC` tags, the core's offset
   found by its `YMXS`.
3. **The SNDH file.**
4. **The relocation table**: one zero long, since nothing in the stub or
   the SNDH file is relocated.

The program prints the SNDH file's address, takes the machine over under
Supexec, keeping the VBL vector, the four timers' vectors, the enable,
mask and control registers and Timer C's count, and turns every MFP
interrupt off; calls init with subtune 1 and plays from the VBL or
Timer C; stops on SPACE or ESC, or once the rows patched in have played,
or once the core's state byte says the tune is over; switches subtunes
on 1 to 9; and hands the machine back. The keyboard is read at its ACIA,
every IKBD report taken whole, so that the mouse works when TOS has the
keyboard back.

## 5. Driving play, the host's side

An SNDH file is passive: init sets the tune up and claims the timers its
effects run, and something outside the file calls play at the tune's
rate. The `TC` tag addresses that caller: it gives the rate, and names
Timer C as the interrupt a desktop host makes the calls from, by the
convention that a host chains the operating system's 200 Hz Timer C and
counts the rate against 200. The `FLAG` letters list the timers the set
claims, so a host that ticks from a timer picks one the tunes do not
run.

The tools: `bin/ymxr-bind tune.ymxr out.bin` writes the bound tune;
`bin/ymxr-sndh tune.ymxr ... out.sndh` the SNDH file, with `-tTITLE`,
`-cCOMPOSER` and `-nNAME` a subtune; `bin/ymxr-prg in.sndh out.prg` the
program, with `-paint` for flag bit 0 and `-rROWS` for the rows.
tools.md has them.
