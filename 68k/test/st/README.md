# The patched displacement read on a real ST

`XPATCH.PRG` counts which displacement a 68000 reads through where the
instruction just before the read has stored a new one. The 68000 fetches
an instruction's extension word ahead of the instruction, and a store
into the displacement of a `move.b d16(pc),d0` by the instruction before
it reaches the read where that fetch comes after the store. Hatari's
cycle-exact 68000 reads the new displacement after the stores of cases
A, H, I and P below; the program counts the same reads on an ST, an STE
or a Mega STE, and the rig runs it under Hatari against the counts at
the end of this file.

## The cases

Each case but Z stores a key into the low byte of the displacement of
the read that follows the store, and Z stores it onto a spare byte. The
byte at the new displacement is `N` and the byte at the old one `O`. A
register is set to the old displacement from an immediate before the
loop. An iteration first writes the old displacement back from that
register, then stores the key, then reads, and a case counts the reads
of `N` (N), of `O` (O) and of any other byte (X) over 50,000 iterations.

| case | the store | where it stores | between it and the read |
|---|---|---|---|
| Z | `move.b d16(pc),abs.l` | a spare byte | adjacent |
| A | `move.b d16(pc),abs.l` | the read's displacement | adjacent |
| H | `move.b d16(pc),-(An)` | the read's displacement | a `nop` |
| E | `move.b d16(pc),-(An)` | the read's displacement | adjacent |
| I | `move.b abs.l,abs.l` | the read's displacement | adjacent |
| P | `move.b d16(pc),abs.l` | the read's displacement | adjacent |

I stores from an absolute address to an absolute address, the addressing
of the core built with `YMXR_PCREL=0`. P is A's pair as a tick handler
writes the chip: register 0 of the PSG selected through `$FFFF8800`
before the store, the read writing the byte to `$FFFF8802`, and the byte
read back through `$FFFF8800`.

Each case runs at the interrupt level TOS leaves, 3, and then at 4, 5, 6
and 7. At 3 the VBL and the MFP's interrupts reach the loop, at 4 and 5
the MFP's alone, and at 6 and 7 neither.

On a Mega STE the program runs every case twice: at 8 MHz with the cache
off, and at 16 MHz with the cache on, writing 0 and then 3 to
`$FFFF8E21`. It writes the register's first value back at the end.

## XPATCH.TXT

The program writes its lines to the screen as each case finishes a
level, and at the end to `XPATCH.TXT` in the folder it runs from. Where
the file exists, the program appends its lines to it, so the runs on
several machines from one disk come one after another in the file. The
lines of a run are, in order:

- `XPATCH, 50000 iterations a case`;
- the machine, from the `_MCH` and `_CPU` cookies, such as `_MCH
  $00010000 STE, _CPU 0`, with `absent` for a cookie the jar lacks, or
  for both where the TOS sets up no jar;
- the TOS, from the version in the ROM's header, such as `TOS 1.62`;
  under EmuTOS, such as `EmuTOS, TOS 1.04 in the header`;
- on a Mega STE, `8 MHz, the cache off` before the first set of lines
  and `16 MHz, the cache on` before the second;
- a line a case and level, such as

      E4 N=00021 O=49979 X=00000 pc,-(An)

  the case's letter and the level, the three counts, and the form of the
  store.

The program shows the mouse again before it writes the file. On a disk
that is write protected, TOS then shows an alert: remove the protection
and choose Retry, and the program writes the file, or choose Cancel. The
screen then shows the file's path and `written`, or `not written:` and
either the GEMDOS error, -13 after a Cancel on a disk that is write
protected, or `N of M bytes` where Fwrite wrote N of the run's M bytes
to the file before the disk filled. Then it shows `Press a key.`

## Building it

    python3 68k/test/st/build.py

writes `68k/test/st/XPATCH.PRG` from [XPATCH.S](XPATCH.S) and prints its
path and size; a path after the command names another file. The builder
needs rmac: `RMAC` names it, or it is on the path. It assembles with
every optimisation of rmac off, so each instruction keeps the form the
source writes.

## Running it on an ST

1. Build `XPATCH.PRG` (Building it, above), and copy it to a floppy disk
   or to a GEMDOS drive, such as a hard disk partition. Leave the disk
   writable.
2. Run it from the desktop of an STF or an STE, and of a Mega STE where
   one is at hand, from the same disk.
3. Leave the mouse and the keyboard alone until `Press a key.`, or until
   TOS shows an alert on a disk that is write protected: at 6 and 7 the
   keyboard ACIA's interrupt is masked, and a second byte it receives
   then overruns the first.
4. Press a key; the program returns to the desktop.
5. Return `XPATCH.TXT` from the folder `XPATCH.PRG` ran from, with the
   lines of every run in it.

## What the counts mean

Z is the control: the store lands on a spare byte, so the read is
through the old displacement and Z reads O in every iteration at every
level. A count of N or X in Z is a fault of the run.

A, H, I and P read N on a 68000 that fetches the read's extension word
after the store, and O where it fetches the word before it, in every
iteration but those in which P reads X.

E is the second control: the 68000 fetches the read's extension word
before the write of a `-(An)` store, so E reads O in every iteration at
6 and 7. At 3 to 5, an interrupt that lands between the store and the
read refills the prefetch from the read's address on its return, and
each such interrupt is one N.

P reads X where an interrupt handler selects another register of the PSG
between the select and the read-back: the byte read back is then that
register's. TOS's VBL handler selects one, and at 3 a VBL can land
there.

## The counts under Hatari

`test_ymxr.py -xpatch` ([tools.md 17.1](../../../doc/tools.md)) builds
the program with the builder above, runs it twice in one folder under
Hatari 2.6.1 on an ST at 8 MHz, cycle-exact, under the TOS image `TOS`
names, and reads each case's line of `XPATCH.TXT` against this table.
The first run creates the file and the second appends to it. A line
outside the table fails the run, and so do a console whose lines differ
from the run's and a second run that leaves the first run's lines out of
the file. The rest is the iterations less the other two counts.

| case | levels | N | O | X |
|---|---|---|---|---|
| Z | 3 to 7 | 0 | 50,000 | 0 |
| A | 3 to 7 | 50,000 | 0 | 0 |
| H | 3 to 7 | 50,000 | 0 | 0 |
| E | 3 to 5 | 1 to 99 | the rest | 0 |
| E | 6 and 7 | 0 | 50,000 | 0 |
| I | 3 to 7 | 50,000 | 0 | 0 |
| P | 3 | the rest | 0 | 0 to 99 |
| P | 4 to 7 | 50,000 | 0 | 0 |

An interrupt lands against the loop at a phase that moves with the
program's layout, the TOS and its boot, and between runs of one program,
so E at 3 to 5 and P at 3 read a range.
