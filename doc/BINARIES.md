# The YMXR binaries

Binary layouts and host calls: multi files (0), bound tunes (1), SNDH
cores (2), SNDH files (3), program stubs and programs (4), and host
procedures (5).

**Conventions.** A clause is cited by number, 2.7. `Note:` begins an
informative sentence. A range includes both ends. A field of more than
one byte is most significant byte first. An offset counts in bytes from
the first byte of the file or block whose layout the table defines; `$`
prefixes a hexadecimal figure. An offset is *even* where it divides by 2
and *on a long* where it divides by 4; even(A) is the least even number
at or above A, and align(A) the least multiple of 4 at or above A. In a
reported text, each capital letter is a decimal figure defined beside the
text, and i an ordinal.

**Roles.** The player is the program of SPEC.md 4 and 5, called as 5.1
of this document defines. A tool writes a multi file, a bound tune, an
SNDH file or a program from tune files and the assembled binaries of 2.1
and 4.1, and reports an error of this document as its line and exits 1.
An SNDH host loads an SNDH file on an even address and calls its three
entries (5.4). The core (2) is a host of the player and provides what 5.2
lists; the stub (4) is an SNDH host of the core (5.4).

**Terms.** A *subtune* is one tune of an SNDH file, numbered from 1 in the
order of the subtune table (2.5); a *set* is the tunes of one SNDH file.
An *image* is the output of DTX's packager for one DTX2 table or several:
the reader's code, then a column table and the table for each (DTX,
abi.md 1); its *format block* is the 28 bytes at its offset 16, and a
*state block* is the bytes DTX's reader requires of a host for one table,
counted in the format block's field at +4. The *claims* of a tune are the
timers its effects run: effects 0 to 3 run Timers A, D, B and C (SPEC.md
2.3), and the *claims byte* has bit 0 for A, 1 for B, 2 for C and 3 for D.
The four timers on the MC68901:

| timer | vector | control register, its nibble | data register | enable, pending, in-service, mask registers | bit |
|---|---|---|---|---|---|
| A | $134 | TACR $FFFA19, bits 3 to 0 | $FFFA1F | IERA $FFFA07, IPRA $FFFA0B, ISRA $FFFA0F, IMRA $FFFA13 | 5 |
| B | $120 | TBCR $FFFA1B, bits 3 to 0 | $FFFA21 | IERA, IPRA, ISRA, IMRA | 0 |
| C | $114 | TCDCR $FFFA1D, bits 7 to 4 | $FFFA23 | IERB $FFFA09, IPRB $FFFA0D, ISRB $FFFA11, IMRB $FFFA15 | 5 |
| D | $110 | TCDCR $FFFA1D, bits 3 to 0 | $FFFA25 | IERB, IPRB, ISRB, IMRB | 4 |

---

## 0. The multi file

**0.1** A multi file is `N` tune files (SPEC.md 3.3) and a name each:
the input of the SNDH tool (3.3), whose subtunes are its tunes in order.
The player reads a bound tune (1) and a reader a tune file (SPEC.md 7); a
tool reads a multi file (0.4, 3.3).

**0.2 Layout.**

| offset | bytes | what it is |
|---|---|---|
| 0 | 4 | `YMXM` |
| 4 | 2 | the version, $0003 to $0006: the highest of the tune files in it (SPEC.md 3.3.5) |
| 6 | 2 | `N`, the tune count, 1 to 99 |
| 8 | 8`N` | the entries, tune 1 to `N`: a long where its tune file begins, signed, then a long its bytes, signed |
| 8 + 8`N` | | the names, tune 1 to `N`, each UTF-8 text ended by a zero byte |
| | | the tune files, tune 1 to `N`, each on a long, each byte for byte the tune file |

**0.3** A name is the text a subtune is called by (3.2); the empty text
is a name. A name is UTF-8 text free of zero bytes; a tool writes name i
as its bytes then a zero byte, and a reader reads a name to its first
zero byte (0.4). Tune file 1 begins at align(8 + 8`N` + the bytes of the
names with their zero bytes), and each next one at align(the end of the
one before); an entry is that offset and the tune file's bytes.

**0.4 Reading.** A reader reads the fields in the order of the table
below and reports the first condition met. Tune file i is bytes A to
A + B - 1 of the multi file, A and B of entry i; name 1 begins at
8 + 8`N`, name i + 1 at the byte after the zero byte of name i, and a
name runs to its zero byte or the file's end.

| condition | reported as |
|---|---|
| the file is under 8 bytes, or bytes 0 to 3 are other than `YMXM` | `not a YMXM file` |
| the version is V, other than 3, 4, 5 or 6 | `version V is not 3, 4, 5 or 6` |
| `N` is outside 1 to 99 | `N tunes, and a multi file has 1 to 99` |
| 8 + 8`N` is past the file's F bytes | `the entries of N tunes stand past the file's F bytes` |
| entry i has A or B below 0, or A + B past the file's F bytes | `tune i stands at A for B bytes, and the file has F` |

**0.5 Writing.** A tool with tune files and names reports the first
condition met of the table below; then reads each tune file as SPEC.md
3.3 defines and reports that reader's line; then writes the layout of
0.2.

| condition | reported as |
|---|---|
| M names for N tune files, M other than N | `M names for N tunes` |
| zero tune files | `no tunes: a multi file has one at least` |
| N tune files, N above 99 | `N tunes, and a multi file has 99 at most` |

---

## 1. The bound tune

**1.1** A bound tune combines tune metadata, a source index and DTX1
tables with an image in place of the tune file's DTX2 table. The packager
builds that image using the table's unit and copies flag (DTX, SPEC.md
2.3; abi.md 1). Every unpacked row matches the tune file byte for byte.

**1.2 Layout.**

| offset | bytes | what it is |
|---|---|---|
| 0 | 4 | `YMXB` |
| 4 | 2 | the version, $0003 to $0006: the version of the tune file bound (SPEC.md 3.3.5) |
| 6 | 2 | the frame rate, in Hz, from the tune file |
| 8 | 1 | effects used, from the tune file |
| 9 | 1 | `S`, the source count, from the tune file |
| 10 | 2 | bytes 10 and 11 of the tune file, copied; the player skips them |
| 12 | 4 | the state block's bytes, unsigned: the format block's field at +4 of the image the table is in |
| 16 | 4 | where the image begins, signed: past the DTX1 tables in a bound tune written alone (1.3), and past the subtunes in an SNDH file (3.1) |
| 20 | 4 | where this tune's table stands, from the image's first byte, unsigned: the format block's field at +8 for an image of one table, and the offset the packager reports for the table in an image of several (1.4) |
| 24 | 4`S` | the source index: for source 1 to `S`, a long, unsigned, where its DTX1 table begins |
| | | the DTX1 tables, source 1 to `S`, each on a long, byte for byte the tune file's |
| | | the image, on a long, in a bound tune written alone |

The tables stand before the image so that a source's rows stand beside
the header however long the image is: a player whose ticks read a row
through a displacement reaches 32,767 bytes (5.5).

**1.3 A bound tune written alone.** A tool writes it from one tune file:
bytes 6 to 11 are the tune file's; the field at 12 is the field at +4 of
the format block of the image the packager makes of the tune's DTX2 table
alone; the field at 20 is that format block's field at +8; DTX1 table 1
is at align(24 + 4`S`) and each next at align(the end of the one before);
the index is those offsets; the field at 16 is align(the last table's
end), where the image stands.

**1.4 A set.** A set shares images. The *shape* of a DTX2 table is its
variant, its unit `k`, its ring `N`, its width `W` and its copies flag
(DTX, SPEC.md 1, 2.3). A tool groups the tunes by shape in tune order,
the first tune of a shape opening its group, and packages the tables of
each group, in tune order, into one image; the field at 20 of each tune
is the offset the packager reports for its table's header in that image.
A bound tune of a set has the layout of 1.2 with the image absent and the
field at 16 written 0; 3.1 places the images and patches the field.

**1.5 What the player reads.** At init (5.1), in order:

1. Bytes 0 to 5; where they are other than `YMXB` and a version $0003
   to $0006 (SPEC.md 3.3.5), report -1 and stop.
2. The field at 16 and the field at 8.
3. Byte 19 of the image, the variant; where it is other than 2, report
   -1 and stop.
4. The field at 20, then bytes 4 to 7 and 10 to 13 of the table's
   header, `R` and `RR` (DTX, SPEC.md 1).
5. The field at 9 and the index, then of each source's table its `R`,
   its `RR` and its rows from byte 16. A player of 5.5 measures each
   source against its handlers here and reports -1 for one past their
   reach.
6. Report 0.

The player skips fields 6, 10 and 12. The host supplies the frame clock
(5.4) and allocates the workspace sized by field 12 (5.2).

---

## 2. The SNDH core

**2.1** The core is position-independent code: the player under three
entries, with the procedures of 2.7 to 2.9. Eight cores are assembled
from one source, one for each setting of the player's three switches; a
tool selects one by name and checks its flags word (2.3, 2.10).

| core | switches | flags word |
|---|---|---|
| `YMXR_sndh.bin` | the row read through the program counter, which the player reads by default | 4 |
| `YMXR_sndh-perf.bin` | that row and the raster monitor, `YMXR_PERF=1` | 5 |
| `YMXR_sndh-lean.bin` | that row and the lean tick, `YMXR_NEST=0` and `YMXR_AEOI=1` | 6 |
| `YMXR_sndh-perf-lean.bin` | all three | 7 |
| `YMXR_sndh-abs.bin` | the row read through an absolute address, `YMXR_PCREL=0` | 0 |
| `YMXR_sndh-perf-abs.bin` | that row and the raster monitor | 1 |
| `YMXR_sndh-lean-abs.bin` | that row and the lean tick | 2 |
| `YMXR_sndh-perf-lean-abs.bin` | that row and the two above | 3 |

A tool asked for a switch selects a core that has it. Where the caller
names none of the third, the tool selects the core that reads a row
through the program counter for a file whose last bound tune ends within
32,767 bytes of the core's first byte, and the core that reads an
absolute address for a file whose tunes end further off (5.5). A caller
that names the third reads back that core, or the line of 2.10 where the
file's tunes end past the reach.

**2.2 Layout**, from the core's first byte:

| offset | bytes | what it is |
|---|---|---|
| 0 | 4 | `bra.w` to init (2.7): the entry an SNDH file's triple reaches |
| 4 | 4 | `bra.w` to exit (2.8) |
| 8 | 4 | `bra.w` to play (2.9) |
| 12 | 4 | `YMXS` |
| 16 | 2 | the descriptor's version, 1 |
| 18 | 2 | the highest bound tune version this core reads, 6 |
| 20 | 2 | `YMXR_FIXED`, the workspace's bytes before the state block: 2,120 |
| 22 | 2 | flags, the word of 2.3 |
| 24 | 2 | where the core's state byte is (2.4) |
| 26 | 2 | zero |
| 28 | 4 | the subtune table's offset (2.5): assembled 0, patched by the tool |
| 32 | 4 | the workspace's offset (2.6): assembled 0, patched by the tool |

The descriptor is bytes 12 to 35. The two patched offsets count from the
core's first byte and are even.

**2.3 The flags word:**

| bit | set where the core was assembled with | which the host then provides |
|---|---|---|
| 0 | the player's raster monitor (`YMXR_PERF=1`): play and each tick handler write the background colour register as performance.md defines | a screen on which those writes are read |
| 1 | the lean tick (`YMXR_AEOI=1`, which the player's source requires `YMXR_NEST=0` with; performance.md): a tick keeps the interrupt level it entered at, and the MFP runs in automatic end-of-interrupt mode from init to stop, bit 3 of its vector register cleared at init and restored at stop | handlers of the host's MFP interrupts that run whole at the level they enter at, with the in-service bit clear; and the vector register left as the player set it between init and stop (5.2) |
| 2 | the row read through the program counter (performance.md), which the player reads where `YMXR_PCREL` stands at 1, its value unasked: a tick reads its row through a signed word displacement from the instruction that reads it, 12 cycles less a tick that writes a row | a file whose last bound tune ends within 32,767 bytes of the core's first byte, which the tool that writes it reads (2.10, 5.5) |

**2.4 The state byte**, at the offset the field at 24 names: bit 0 is
set from init's step 8 to exit's step 1, while a tune plays; bit 1 is set
by play (2.9) once the player has reported -1 (SPEC.md 4); bits 7 to 2
are 0. Note: play keeps every register, so the state byte is where an
SNDH host reads the end of a tune that plays once.

**2.5 The subtune table**, at the offset the field at 28 names: a word
`N`, then `N` longs, unsigned, subtune 1 to `N`, each the offset of its
bound tune from the core's first byte (3.1).

**2.6 The workspace**, at the offset the field at 32 names: the core's
address plus that offset, rounded up to a long (2.7 step 4), then
`YMXR_FIXED` bytes for the player and the state block of the subtune's
image. A tool writes align(the core's field at 20 + the largest state
block of the set) + 2 zero bytes, last in the file (3.1). Note: the file
loads on an even address, and the two bytes cover the rounding of 2.7
step 4.

**2.7 Init**, entered with `d0.w` the subtune `s`, 1 to `N`; every
register is kept.

1. Where the state byte has bit 0 set, perform steps 1 to 4 of exit
   (2.8).
2. At interrupt level 7, keep the four vectors, TACR, TBCR, TCDCR, IERA,
   IERB, IMRA and IMRB (Terms).
3. Where `s` is outside 1 to `N`, `s` is 1. The bound tune is the core's
   address plus entry `s` of the subtune table.
4. The workspace is at align(the core's address + the field at 32).
5. Call the player's init with `a0` the bound tune and `a1` the
   workspace (5.1). Where it reports -1, return: the state byte is 0.
6. Compute the claims byte (Terms) from the field at 8 of the bound tune
   and keep it.
7. At level 7, keep the vector at $60 and write the address of an `rte`
   there.
8. Write 1 to the state byte.

Note: a write of the player's that clears a pending or an enable bit
after the MFP raises an interrupt and before the 68000 acknowledges it
leaves the 68000 an empty vector at the acknowledge, so the 68000 runs
exception 24, vector $60, in place of the timer's handler; the write
cancelled that tick, so its handler is an `rte`. The write is made at
level 7 and the exception runs the same: an acknowledge the 68000 has
begun runs to its end at any level.

**2.8 Exit**; every register is kept. Where the state byte has bit 0
clear, return; otherwise:

1. Write 0 to the state byte.
2. Call the player's stop with `a0` the workspace of 2.7 step 4 (5.1).
3. At level 7, restore the vector at $60 kept in 2.7 step 7.
4. At level 7, for each timer of the claims byte in the order A, B, C,
   D: restore its vector; write its nibble of its control register from
   the kept byte, the other nibble as the register has it; clear its
   pending bit; set its enable bit where the kept IER has it set, and its
   mask bit where the kept IMR has it set.

A timer outside the claims byte is left as it is throughout. Exit leaves
each data register as it is. Note: the player's stop clears the enable
and the mask bit of each timer of the claims byte (5.3), and step 4 sets
them back. Note: a data register reads as the running count and a write
to it is the reload value, so a count kept at init and written at exit
sets a rate other than the host's; a host that requires a timer's rate
back writes the count it knows.

**2.9 Play**; every register is kept. Where the state byte has bit 0
clear, return; otherwise call the player's play with `a0` the workspace
(5.1), and where it reports -1, set bit 1 of the state byte.

**2.10 Errors of a core.** A tool reads a core before it writes an SNDH
file (3) and reports the first condition met:

| condition | reported as |
|---|---|
| the core is under 36 bytes, or bytes 12 to 15 are other than `YMXS` | `not an SNDH core: no YMXS at 12` |
| the field at 16 is V, other than 1 | `the core's descriptor is version V, and this writes 1` |
| the field at 18 is V, below the version W the tune binds at | `the core reads bound tunes to version V, and this binds at W` |
| the raster monitor is selected and bit 0 of the flags word F is clear | `the core's flags at 22 read F, and the raster monitor asked for needs bit 0 set` |
| the lean tick is selected and bit 1 of the flags word F is clear | `the core's flags at 22 read F, and the lean tick asked for needs bit 1 set` |
| the row read through the program counter is selected and bit 2 of the flags word F is clear | `the core's flags at 22 read F, and the row read through the program counter asked for needs bit 2 set` |
| that row is selected and the file's last bound tune ends B bytes past the core's first byte, B above 32,767 (5.5) | `the tunes end B bytes past the core's first byte, and a tick that reads a row through the program counter reaches 32767` |

---

## 3. The SNDH file

**3.1 Layout.** T is the tag block's bytes, from `SNDH` at 12 through
the last byte of `HDNS`; H = even(12 + T), L the
core's bytes, `N` the subtunes. Offsets under *from the core* count from
the core's first byte, at H.

| part | at | bytes |
|---|---|---|
| the entry triple | 0, 4, 8 | three `bra.w`: the word $6000, then the displacement H - 2, to the same entry of the core's triple (2.2) |
| the tag block | 12 | 3.2 |
| a pad | 12 + T | a zero byte where 12 + T is odd |
| the core | H | the core (2), its field at 28 patched with even(L) and its field at 32 with W below |
| the subtune table | even(L) from the core | the word `N`, then `N` longs, each a bound tune's offset from the core (2.5) |
| the bound tunes | subtune 1 at even(L) + 2 + 4`N`, subtune i + 1 at even(the end of subtune i), from the core | subtune 1 to `N`, each of 1.4, its field at 16 patched with its image's offset less its offset |
| the images | from even(the end of the last bound tune), each on a long, from the core | the images of the set (1.4), in group order |
| the workspace | W = even(the end of the last image), from the core | align(the core's field at 20 + the largest state block of the set) + 2 zero bytes (2.6), last |

The subtunes stand before the images so that a subtune's sources stand
beside the core: a player whose ticks read a row through a displacement
reaches 32,767 bytes (5.5), and an image between the two would be in the
way.

A pad byte is zero. Every bound tune begins on an even address, as 5.1
requires.

**3.2 The tag block**, in this order; each text is cleaned to the bytes
$20 to $7E, the others dropped.

| bytes | what they are |
|---|---|
| `SNDH` | |
| `TITL`, the title, a zero byte | the title |
| `COMM`, the composer, a zero byte | where the composer is other than the empty text |
| `CONV`, `YMXR (ym-to-ymxr)`, a zero byte | the converter |
| `##`, two decimal digits, a zero byte | `N`, 01 to 99 |
| `TC`, the rate in decimal, a zero byte | the rate of the set, in Hz, where the block names Timer C |
| `!V`, the rate in decimal, a zero byte | the rate of the set, in Hz, where the block names the VBL |
| `FLAG`, `~`, letters, `y`, a zero byte | the letters `a` to `d` of the timers in the claims byte of the set, in that order |
| a zero byte | where the bytes so far are odd |
| `FRMS`, `N` longs | subtune 1 to `N`: `R` for a tune that plays once, 0 for one that repeats |
| `!#SN`, `N` words, `N` names each with a zero byte | where `N` is above 1: word i is the offset of name i from the tag's first byte, word 1 being 4 + 2`N`; the names of the set: the multi file's (0.3), or of a YMXS multi its tunes' titles, `(untitled)` for a blank one |
| a zero byte | where the bytes so far are odd |
| `HDNS` | |

The claims byte of the set is the claims bytes of its tunes ORed. One of
`TC` and `!V` stands in the block, the clock tag: it names the clock a
host calls play from (5.4). The title and the composer are the tool's
(tools.md); where the tool leaves the title to the file, of a multi file
it is name 1, and `(untitled)` where name 1 is the empty text, and of a
YMXS multi it is the first tune's title, and the composer the first
tune's composer where that is other than blank. A tune file read alone
has the empty name; its recorded name (SPEC.md 3.3) is outside what the
tool reads.

**3.3 Writing.** A tool with a core and `N` tune files, in order:

1. Check the core (2.10).
2. Report the first condition met of the table's first three rows.
3. For subtune i from 1 to `N`: read tune file i as SPEC.md 3.3 defines,
   an error reported as `subtune i: ` and that reader's line; then
   report the table's fourth condition where it is met.
4. Bind the set (1.4).
5. Write the tag block (3.2), its clock tag `!V` where the claims byte of
   the set has Timer C, whose handler is then the player's, or where the
   VBL is asked for, and `TC` otherwise. Where Timer C is asked for and
   the claims byte has it, report `the set claims Timer C and the clock
   asked for is Timer C: the player's handler has that timer`.
6. Lay the file out as 3.1, reporting the table's last condition where
   it is met.

| condition | reported as |
|---|---|
| zero tune files | `no tune files: an SNDH file has one subtune at least` |
| N tune files, N above 99 | an error naming N and the 99 subtunes the two digits of `##` number |
| N names for M tune files, N other than M | `N names for M subtunes` |
| subtune i, i above 1, is at H Hz and subtune 1 at R, H other than R | `subtune i plays at H Hz and subtune 1 at R: an SNDH file records one rate` |
| H - 2 is above 32,767, the tag block being B bytes | `the tag block is B bytes, and a bra.w reaches 32767` |

---

## 4. The program stub

**4.1** The stub is position-independent code of an even length; a tool
puts it in front of an SNDH file and patches its descriptor (4.4), and
the SNDH file begins at the byte after the stub's last.

**4.2 Layout**, from the stub's first byte:

| offset | bytes | what it is |
|---|---|---|
| 0 | 4 | `bra.w` to the program (4.6) |
| 4 | 4 | `YMXT` |
| 8 | 2 | the descriptor's version, 2 |
| 10 | 2 | the subtunes: patched from the `##` tag |
| 12 | 2 | flags: patched; the word of 4.3 |
| 14 | 2 | the rate, rows a second: patched from the clock tag |
| 16 | 4 | the rows to play: patched; 0 plays until a key stops it |
| 20 | 4 | the core's offset from the SNDH file's first byte: patched |
| 24 | 2 | the prescaler: patched; TCDCR's nibble, 1 to 7, the divisors 4, 10, 16, 50, 64, 100 and 200 (SPEC.md 1.9.2) |
| 26 | 2 | the timer's count: patched; 1 to 255, or 0 for the 256 the MFP counts |
| 28 | 2 | the timer's rate, `ticks`: patched; the ticks a second the prescaler and the count make, which a row is counted against (4.8) |

**4.3 The flags word:**

| bit | set by the tool where | the stub then |
|---|---|---|
| 0 | this version writes 0 | reads it as 0: the screen is cleared on every run (4.6 step 1) |
| 1 | the clock tag is `!V`, the `FLAG` letters contain `c`, or the VBL is asked for | plays from the VBL; with the bit clear, from Timer C (4.7) |

**4.4 The program.** A tool with the stub, an SNDH file of F bytes and
a row count `rows` writes, in order:

1. The PRG header, 28 bytes: the word $601A; a long, the stub's bytes
   plus F; five zero longs; a zero word.
2. The stub, its fields at 10, 12, 14, 16, 20, 24, 26 and 28 patched:
   `N` of the `##` tag; the flags of 4.3; the rate of the clock tag;
   `rows`; the core's offset, H of 3.1; and the prescaler, the count and
   the ticks of the timer 4.10 picks for the rate.
3. The SNDH file, byte for byte.
4. One zero long, the relocation table.

Note: a program is 28 + the stub's bytes + F + 4 bytes; the relocation
table is empty since every address in the stub and the file is relative.

**4.5 Reading the SNDH file.** In order:

1. Check the stub and `rows`, the table's first four conditions.
2. Read `SNDH` at 12, then the tags from 16 to `HDNS`: a zero byte where
   a tag name would begin is a pad of one byte; `##` is 4 bytes and a
   zero byte, its two digits `N`; the clock tag, `TC` or `!V`, runs to
   its zero byte, its leading decimal digits the rate; `TITL`, `COMM`,
   `CONV` and `FLAG` run to their zero byte, of `FLAG` the letters after
   `~` kept, or the whole text where `~` is absent; `FRMS` is 4 + 4`N`
   bytes; `!#SN` is 4 + 2`N` bytes then `N` texts each to its zero byte.
3. Where the clock tag is `!V` or the `FLAG` letters contain `c`, check
   the rate.
4. C is where `YMXS` first stands from the byte after `HDNS`, less 12;
   R is 2 + the word at 2 where the word at 0 is $6000, else -1.

The tool reports the first condition met, those of a tag in the order the
tags stand:

| condition | reported as |
|---|---|
| the stub is under 30 bytes, or its bytes 4 to 7 are other than `YMXT` | `not a program stub: no YMXT at 4` |
| the stub's field at 8 is V, other than 2 | `the stub's descriptor is version V, and this writes 2` |
| the stub's length B is odd | `the stub is B bytes, odd: the SNDH file after it would load on an odd address` |
| `rows` N is outside 0 to 4,294,967,295 | `rows N does not fit a long` |
| bytes 12 to 15 of the file are other than `SNDH` | `not an SNDH file: no SNDH at 12` |
| either of the two bytes after `##` is other than a digit | `the SNDH file's tags have no '##' subtune count` |
| the clock tag's text reads as 0 | `the SNDH file's tags have no TC or !V rate` |
| `FRMS` or `!#SN` at A precedes `##` | `the SNDH file's FRMS tag at A stands before the '##' count that sizes it`, or `!#SN` in place of `FRMS` |
| tag X at A is other than the tags of 3.2 | `the SNDH file's tag X at A is not one this reads` |
| a tag name or a zero byte is read past the file's end | `not an SNDH file: no HDNS ends its tags` |
| the tags end and `##` is absent | `the SNDH file's tags have no '##' subtune count` |
| the tags end and the clock tag is absent | `the SNDH file's tags have no TC or !V rate` |
| the clock tag is `!V` or the `FLAG` letters contain `c`, the rate H is other than 50, and the caller leaves the clock to the file | `the file plays from the VBL at H Hz: the stub's VBL is a 50 Hz clock, so this set needs a separate host or the VBL asked for` |
| `YMXS` is absent past `HDNS` | `the SNDH file has no core: no YMXS past its tags` |
| C is before the byte after `HDNS`, or R is other than C | `the core begins at C, and the entry triple reaches R` |
| C plus 36 is past the file, B bytes remaining from C | `the core begins at C and the file ends B bytes on, short of the core's descriptor, 36 bytes` |

A reader of 6.1 reports a condition of this table that reads the SNDH
file. The four that read the stub a tool is handed and its `rows`
argument are the tool's, and so is the one that reads the caller's
clock: a reader of a program whose stub is of an earlier version reports
the record of 6.5, and one of a file whose clock tag is `!V` at a rate
other than 50 the record of 6.4.

**4.6 The program**, as the stub runs it under TOS. Scan codes are the
IKBD's; `N` is the field at 10, `rate` the field at 14, `rows` the field
at 16.

1. Read the resolution (Getrez): the screen is 40 columns in low
   resolution and 80 in the others. Clear the screen (`ESC E`) and print
   `YMXR at $` and the SNDH file's address in eight hexadecimal digits,
   then `SPACE or ESC ends the program`.
2. Where `N` is above 1: print `LEFT and RIGHT pick a subtune`, `or type
   its number` and an empty line, then the list: subtune 1 to `N` in
   columns of at most 18 rows, the columns (`N` - 1) / 18 + 1, the rows
   (`N` - 1) / the columns + 1, the entries running down a column before
   the next, the column's width the screen's divided by the columns,
   each cell two spaces, the number in two digits, two spaces, the name
   cut to the column's width less 7, then spaces to the column's width;
   a row's trailing spaces are dropped, and a carriage return and a line
   feed end it. The name of subtune i is name i of the `!#SN` tag, found
   by scanning the file from byte 12 in steps of 2 to `HDNS`, over at
   most 2,049 positions; else the `TITL` text where `TITL` is the first
   tag; else `subtune`.
3. Under Supexec, at interrupt level 7: keep the VBL vector at $70, the
   four timer vectors, IERA, IERB, IMRA, IMRB, TACR, TBCR and TCDCR, then
   write 0 to those seven registers; send $12 to the IKBD, the mouse off;
   start subtune 1 (4.7); set level 3; print the playing line (4.9).
4. Read the keyboard at its ACIA ($FFFFFC00, $FFFFFC02). A byte $F6 to
   $FF opens a report of 7, 5, 2, 2, 2, 2, 6, 2, 1 or 1 bytes more, which
   are skipped. A byte with bit 7 set is a key released and is skipped.
   SPACE ($39) or ESC ($01): step 6. LEFT ($4B) or UP ($48): start the
   subtune before the one playing, `N` after 1. RIGHT ($4D) or DOWN
   ($50): start the one after, 1 after `N`. A digit key ($02 to $0B, the
   figures 1 to 9 and 0): the number typed becomes ten times itself plus
   the figure where that is at most `N`, else the figure; the pause is 25
   ticks (4.8), or 0 where ten times the number is above `N`. Once the
   pause has run out with a number typed, start that subtune where it is
   1 to `N`, and forget the number otherwise.
5. Where a tick (4.8) has set *over*, start the subtune after the one
   playing, 1 after `N`. Where a tick has set *done*, step 6; otherwise
   step 4.
6. At level 7: call the core's exit; read the ACIA until it is empty;
   send $08 to the IKBD, the mouse reporting; restore the vectors and
   registers kept in step 3, writing 192 to Timer C's data register
   before TCDCR; leave Supexec and end the program (Pterm0).

**4.7 Start**, with the subtune `s`, at level 7, in order:

1. The subtune playing is `s`; the number typed, its pause and *over*
   (4.8) are 0.
2. Call the core's exit, then its init with `d0.w` = `s`; the program
   leaves init's result unread.
3. *Done* (4.8) is 0, and the rows left are `rows`.
4. Where bit 1 of the flags is set: write the VBL vector with the handler
   of 4.8, and stop.
5. Otherwise: write the Timer C vector with the handler of 4.8; write 0
   to the accumulator (4.8); write the field at 26 to Timer C's data
   register; write bits 7 to 4 of TCDCR as the field at 24, bits 3 to 0
   as they are; clear bit 5 of IPRB; set bit 5 of IERB and of IMRB.

Where init reports -1 (2.7 step 5), the clock is armed as above, each
tick's play returns at once (2.9), and the program runs until a key or
`rows`.

Note: the two fields make the ticks the field at 28 reads, 2,457,600
divided by the prescaler's divisor and by the count; the operating
system's clock, which 4.10 falls back to, is $5 and 192, the divisor 64
and 200 ticks a second.

**4.8 A tick.** Terms: the *accumulator* is a word; the *busy flag* is a
byte, 0 at load; *over* and *done* are two marks, 0 at start (4.7);
`ticks` is the field at 28. From the VBL, one tick a frame. From Timer
C, in order:

1. Clear bit 5 of ISRB.
2. Add `rate` to the accumulator.
3. Where the accumulator is below `ticks`, or the busy flag is set,
   return.
4. Set the busy flag; set level 5.
5. While the accumulator is at least `ticks`: subtract `ticks` and
   perform the tick.
6. Clear the busy flag and return.

The tick, in order:

1. Where the pause of 4.6 step 4 is above 0, subtract 1.
2. Where *done* is set, return.
3. Call the core's play.
4. Where the rows left are above 0, subtract 1; where they reach 0, set
   *done* and return.
5. Where `N` is above 1, read the core's state byte through its field at
   24; where bit 1 is set, set *over*.

Every register is kept.

**4.9 The playing line**: a carriage return, `playing `, the subtune in
two digits, `/`, `N` in two digits, two spaces, and the name of 4.6 step
2 cut or padded with spaces to the screen's width less 16. Note: the
padding covers the line before it.

**4.10 The timer for a rate.** A tool writes the fields at 24, 26 and 28
from the rate R: for `ticks` = R, 2R, 3R and up to 400, the first for
which 2,457,600 / `ticks` is a whole number and equals a prescaler's
divisor times a count of 1 to 256, the prescalers read in the order
SPEC.md 1.9.2 numbers them; the prescaler's nibble goes in the field at
24, the count in the field at 26 as the MFP reads it, 0 for 256, and
`ticks` in the field at 28. Where no `ticks` up to 400 meets that, the
fields are $5, 192 and 200, the operating system's clock, and the
accumulator of 4.8 spreads the rows over the second.

A row then lands every `ticks` / R ticks, and the count returns to zero:
R = 50 is 150 ticks a second and a row every third, R = 60 is 240 and a
row every fourth, and R = 200 is 200 and a row a tick.

---

## 5. Driving play, the host's side

**5.1 The player's calls**, three `bra.w` at the player's first bytes,
called in supervisor mode:

| call | in | out |
|---|---|---|
| init, at 0 | `a0` the bound tune (1), even; `a1` the workspace, on a long | `d0` 0, or -1 for a bound tune of 1.5's two conditions |
| play, at 4 | `a0` the workspace | `d0` 0, or -1 where the tune has ended (SPEC.md 4): every register of the chip and the MFP is left as it is |
| stop, at 8 | `a0` the workspace | the timers of the claims byte released and the registers written as 5.3 lists |

A call clobbers `d0` to `d5` and `a0` to `a5` and keeps `d6`, `d7` and
`a6`. Init reads row 0; each play writes the row the call before it read
and then reads the next.

**5.2 What a host provides.** The workspace: `YMXR_FIXED` bytes, 2,120,
then the bound tune's field at 12 bytes, on a long. Before init the host
keeps, and after stop restores, what the player writes (5.3): of each
timer in the claims byte, the vector, the nibble of the control register,
the enable bit and the mask bit (Terms). While a tune plays the host has
the address of an `rte` in the vector at $60 (2.7 Note). With the lean
tick the player keeps the MFP's vector register at init and restores it
at stop, and the host leaves it alone between. The host calls init with
bit 3 of the MFP's vector register set, software end-of-interrupt mode.
Note: TOS leaves it so. A host calls play once a frame at the tune's
rate, the field at 6 of the bound tune, and calls stop before a second
init on one workspace.

**5.3 What the player writes outside the tune.** At init, at level 7,
for each timer in the claims byte: the nibble of its control register
written 0; its vector written with the player's handler; its pending bit
cleared; its enable bit and its mask bit set. At stop, at level 7, for
each: the nibble written 0, the enable bit and the mask bit cleared, the
pending bit cleared; then R8, R9 and R10 written 0, R13 down to R0
written 0, and R7 written $FF. A timer outside the claims byte is left as
it is by both.

**5.4 An SNDH host.** It loads the file on an even address, calls init
with `d0.w` the subtune, 1 to `N`, calls play at the rate of the clock
tag, and calls exit at the end; it reads the end of a tune that plays
once in the state byte (2.4). The clock tag names the clock an SNDH host
under TOS calls play from: `TC` Timer C, and `!V` the VBL, written where
the set claims Timer C or the VBL is asked for (3.2, 3.3 step 5). The
`FLAG` letters list the timers the set claims, and a host that ticks
from a timer selects one outside them. Under `TC` the program of 4
writes Timer C's vector with a separate handler, leaves the timer at the
operating system's 200 Hz, adds the rate to an accumulator on each tick
and plays a row for each 200 the accumulator reaches (4.7, 4.8), and
restores the vector at the end (4.6 step 6); under `!V` it plays from
the VBL (4.3).

**5.5 A player that reads a row through a displacement.** A tick reads
its row through a signed word displacement from the instruction that
reads it, 12 cycles less a tick that writes a row than an absolute
address costs ([performance.md](performance.md)). Every row of every
source stands within 32,767 bytes of the player's handlers: init measures
each source against them and reports -1 for one further off (1.5 step 5).
The layouts of 1.3 and 3.1 put a tune's DTX1 tables beside its header and
a set's subtunes beside the core, so a host that loads one file and
passes the player a tune inside it meets the requirement. A host that
loads the player and the tune separately places them within that reach,
or assembles the player with `YMXR_PCREL=0`, whose ticks read an absolute
address and reach any offset.

Note: the tools that write the files of this document are
`bin/ymxr-multi`, `bin/ymxr-bind`, `bin/ymxr-sndh` and `bin/ymxr-prg`,
and `bin/ymxr-layout` writes the record of 6 ([tools.md](tools.md)).

---

## 6. What a reader reports

**6.1 The record.** A reader reads a file this document defines and
reports its parts, one a line: lines of JSON in US-ASCII, one part a
line, each line free of spaces but for those inside a text, integers in
decimal, the keys of each object in the order this section lists them,
and a line feed, byte 10, ending every line. An `at` is an offset from
the file's first byte, and so is every other offset the record reports
but for `table`, which 6.3 defines. A text of the record is UTF-8 read
out, each character above $7E escaped `\uXXXX`, its code point in four
hexadecimal digits, lower case; a byte sequence outside UTF-8 reads as
U+FFFD, the replacement character, and escapes as `\ufffd`.

The first line is `{"kind":"K","bytes":F}`, F the file's bytes and K the
first kind of the four the file meets, read in this order: `multi` where
bytes 0 to 3 are `YMXM` (0.2), `bound` where they are `YMXB` (1.2),
`program` where the word at 0 is $601A (4.4), and `sndh` where bytes 12
to 15 are `SNDH` (3.1). The lines after it are 6.2 to 6.5, by kind.
Where the file meets none of the four, report `not a file BINARIES.md
defines: no YMXM, YMXB, $601A or SNDH` and stop; where it breaks a rule
of 0.4 or 4.5, report that line alone and stop.

**6.2 A multi file** (0.2), after the first line:

- `{"part":"header","version":V,"tunes":N}`, the fields at 4 and 6.
- a line a tune, 1 to `N`, in order: `{"part":"tune","number":i,"at":A,
  "bytes":B,"name":"T"}`, A and B of entry i and T its name (0.3, 0.4).
  A name is JSON text, its UTF-8 read out.

**6.3 A bound tune** (1.2), after the first line:

- `{"part":"header","version":V,"rate":H,"effects":E,"sources":S,
  "state":B,"image":I,"table":C}`, the fields at 4, 6, 8, 9, 12, 16 and
  20, I the field at 16 plus the tune's first byte, which 3.1 patches
  for a subtune of an SNDH file, so that I is the image's offset there,
  and C the field at 20 as the file has it, an offset from the image's
  first byte.
- a line a source, 1 to `S`, in index order: `{"part":"source",
  "number":i,"at":A}`, A the index entry plus the tune's first byte.
- `{"part":"image","at":I,"bytes":B}` where the field at 16 is above 0,
  B the file's bytes less I: the image of a bound tune written alone
  (1.3). A bound tune of a set has that field written 0 (1.4), and the
  line stands in the record of the SNDH file instead (6.4).

**6.4 An SNDH file** (3.1), after the first line:

- `{"part":"entry","to":[a,b,c]}`, the three `bra.w` of 3.1 at 0, 4
  and 8 from the SNDH file's first byte, and a, b and c their targets:
  for the one at E, E + 2 + the word at E + 2, read signed, an offset
  like every other the record reports and counted from the file's first
  byte.
- a line a tag, in the order they stand, from 16 to `HDNS` (3.2), a
  zero byte where a tag name would begin skipped, A its first byte:
  `{"part":"tag","name":"TITL","at":A,"text":"T"}` for `TITL`, `COMM`,
  `CONV` and `FLAG`, T the bytes after the four of the name to its zero
  byte; of `FLAG` that is the whole text 3.2 writes, its `~` and its
  `y` among it, and the letters 4.5 step 2 keeps are the tool's reading,
  outside the record; `{"part":"tag","name":"##","at":A,"subtunes":N}`, N
  its two digits; `{"part":"tag","name":"TC","at":A,"rate":H}`, or `!V`
  in place of `TC`, H the leading decimal digits after the two of the
  name;
  `{"part":"tag","name":"FRMS","at":A,"frames":[...]}`, the `N` longs,
  unsigned;
  `{"part":"tag","name":"!#SN","at":A,"names":[...]}`, the `N` names in
  subtune order, each read to its zero byte from the byte after the
  offset words (4.5 step 2); and
  `{"part":"tag","name":"HDNS","at":A}`, last.
- `{"part":"core","at":H,"version":V,"binds":R,"fixed":F,"flags":G,
  "state":S,"subtunes":T,"work":W}`, H the core's first byte,
  even(12 + T) of 3.1, the fields at 16, 18, 20, 22, 24, 28 and 32
  (2.2), S, T and W each plus H.
- `{"part":"subtunes","at":T,"tunes":[...]}`, the word `N` at T and the
  `N` longs after it, each plus H (2.5).
- a line a subtune, 1 to `N`, in order: `{"part":"tune","number":i,
  "at":A,"bytes":B,"version":V,"rate":R,"effects":E,"sources":S,
  "state":D,"image":I,"table":C}`, A the offset subtune i has in the
  subtune table, plus H, the fields of 1.2 read at A as 6.3 reads them,
  its image line left out, and B the bytes to the next subtune, or, for
  the last, to the image at the lowest offset; then a line a source of
  that subtune, as 6.3 defines them, each `at` the index entry plus A.
- a line an image: `{"part":"image","number":i,"at":I}`, one line for
  each distinct offset the subtunes name, in increasing order of offset,
  i counting from 1.
- `{"part":"workspace","at":W,"bytes":B}`, B the bytes from W to the end
  of the SNDH file, which the workspace stands last in (3.1).

**6.5 A program** (4.4), after the first line:

- `{"part":"prg","text":B}`, the long at 2.
- `{"part":"stub","at":28,"bytes":S,"version":V,"subtunes":N,
  "flags":G,"rate":H,"rows":R,"core":C}`, the fields at 8, 10, 12, 14,
  16 and 20 counted from 28 (4.2), S the stub's bytes, the SNDH file's
  first byte less 28, and C the core's offset plus the SNDH file's first
  byte. V is the field as the file has it, and
  `"prescaler":P,"count":K,"ticks":T`, the fields at 24, 26 and 28, end
  the line where V is 2 or above; a program of a release before those
  fields reads 1 and ends its line at `core`.
- `{"part":"sndh","at":A}`, A the SNDH file's first byte: the lowest
  even offset, 28 or above, where bytes A + 12 to A + 15 are `SNDH`.
- the lines 6.4 defines for the SNDH file at A, the first line of 6.1
  left out, every offset counted from the program's first byte, the
  `at` of a tag line among them, where 4.5 counts from the SNDH file's
  first byte. The SNDH
  file ends at 28 plus the long at 2, the relocation table standing
  after it (4.4), and the workspace's `bytes` counts to that end.
