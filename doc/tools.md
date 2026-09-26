# The tools

Commands, flags, reports and exit codes (1 to 13), pipes (14), player
calls (15), scripts (16), rigs (17), measurements (18), Java and Go
implementations (19), releases (20), and environment (21).

Terms follow [SPEC.md](SPEC.md) and [BINARIES.md](BINARIES.md). A
*structure* is a YMXS multi as JSON ([ymxs.md](ymxs.md)); a *dump* is
YM3!, YM3b, YM5! or YM6!, bare or in an LHA archive.

**Conventions.** A clause is cited by number, 8.3. In a quoted line a
capital letter is a decimal figure defined beside the line or in the
clause, and `<x>` a text the clause names; a format is that of C's
`printf`. `Note:` begins an informative sentence.

---

## 1. Definitions

**1.1** A *tool* reads one input from standard input, writes one output
to standard output, and writes lines to standard error. `ymxr-multi`
reads the files its arguments name (11), and `ymxr-check` reads the files
and directories its arguments name where it has any (8).

**1.2** An *invocation* is one execution of a tool with a list of
arguments and an input; it ends with an exit code (3.6).

**1.3** A *summary line* is `<tool>: ` and a text, written on standard
error where the invocation completes; each tool's section defines it.

**1.4** The *report* is every line a tool writes on standard error other
than the summary line, the notes and the warnings: a *heading* at the
left margin, and under it *rows*, each two spaces, a name padded to 22
characters, a space and a value, the format `  %-22s %s`.

**1.5** A *note* is a line about the input that the invocation continues
past: in the report, `  note: ` and the text where the condition arises;
with the report off (3.3), `  ` and the text after the output, the
summary line absent. A *counted note* is a note of 5.6, written after
the summary line in every case.

**1.6** A *warning* is a line of YMXS's check (YMXS, doc/tools.md 5),
written by a tool that reads a structure (7.2).

**1.7** An *error* is a fault of the input, of the call, or of a read or
a write; it is written as `<tool>: ` and its text, ends the invocation,
and leaves standard output empty. An error of several lines (7.1) has the
tool's name before its first line alone.

**1.8** A *progress line* is `  <what> N of M (P%)`, N the steps done of
M and P the percentage, written where the tenth of the run has moved
since the last progress line and a second has passed since the start of
the run or that line; `ymxr-check` writes them as `read` (8.5), and
every tool that writes a tune file as `packing the columns`, N of 30,
within the packing report (4.5).

---

## 2. The twelve tools

| tool | reads | writes | flags other than `-silent` |
|---|---|---|---|
| `ym-to-ymxr` | a dump | a tune file (SPEC.md 3.3) | `-kK`, `-mN`, `-copies[S]`, `-rRR`, `-r` |
| `ym-to-ymxs` | a dump | a structure of one tune | `-rRR`, `-r` |
| `ymxs-to-ymxr` | a structure | a tune file, or a multi file (BINARIES.md 0) of several | `-kK`, `-mN`, `-copies[S]` |
| `ymxs-to-sndh` | a structure | an SNDH file (BINARIES.md 3) | `-kK`, `-mN`, `-copies[S]`, `-tTITLE`, `-cCOMPOSER`, `-perf`, `-lean`, `-pcrel`, `-abs`, `-vbl` |
| `ymxs-to-prg` | a structure | a TOS program (BINARIES.md 4) | the flags of `ymxs-to-sndh` and `-rROWS` |
| `ymxr-check` | a dump, or the files named | one verdict a dump, on standard output | `-kK`, `-mN`, `-copies[S]`, `-rRR`, `-r` |
| `ymxr-trace` | a tune file | the record of SPEC.md 7 | `-rROWS` |
| `ymxr-layout` | a multi file, a bound tune, an SNDH file or a program | the record of BINARIES.md 6 | - |
| `ymxr-bind` | a tune file | a bound tune (BINARIES.md 1) | - |
| `ymxr-multi` | the tune files named | a multi file | `-nNAME` |
| `ymxr-sndh` | a tune file or a multi file | an SNDH file | `-tTITLE`, `-cCOMPOSER`, `-perf`, `-lean`, `-pcrel`, `-abs`, `-vbl` |
| `ymxr-prg` | an SNDH file | a TOS program | `-rROWS`, `-vbl` |

**2.1** Each tool is written twice, under one name, in the two trees of
section 19: in Java as the script `bin/<tool>`, one line running
`bin/run` with the class below; in Go as the executable built from the
command directory `go/cmd/<tool>`.

| tool | Java class |
|---|---|
| `ym-to-ymxr` | `org.ymxr.YmToYmxr` |
| `ym-to-ymxs` | `org.ymxr.YmToYmxs` |
| `ymxs-to-ymxr` | `org.ymxr.YmxsToYmxr` |
| `ymxs-to-sndh` | `org.ymxr.YmxsToSndh` |
| `ymxs-to-prg` | `org.ymxr.YmxsToPrg` |
| `ymxr-check` | `org.ymxr.Check` |
| `ymxr-trace` | `org.ymxr.Trace` |
| `ymxr-layout` | `org.ymxr.Layout` |
| `ymxr-bind` | `org.ymxr.Bind` |
| `ymxr-multi` | `org.ymxr.Multi` |
| `ymxr-sndh` | `org.ymxr.Sndh` |
| `ymxr-prg` | `org.ymxr.Prg` |

---

## 3. An invocation

**3.1 The arguments.** `-silent` is a flag of every tool and is removed
from the arguments wherever it stands. Every other argument is a flag of
the tool, listed in section 2, or makes the call wrong (3.2); for
`ymxr-multi` and `ymxr-check` an argument that begins with a character
other than `-` names a file (9, 12).

**3.2 A wrong call** writes one line and exits with 2. A tool reads a
flag outside its flags before standard input, `ymxr-check` excepted
(8.4), and `ym-to-ymxr`, `ymxr-check`, `ymxr-trace`, `ymxr-prg` and
`ymxs-to-prg` the numbers of their flags before it, standard input then
unread; `ymxs-to-ymxr`, `ymxs-to-sndh` and `ymxs-to-prg` read the numbers
of `-kK`, `-mN` and `-copies[S]` after reading and checking the structure
(7.1), so an error of the structure is reported first, exit 1. X is the
argument.

| tool | condition | line |
|---|---|---|
| `ymxr-bind` | any argument other than `-silent` | `ymxr-bind reads its input on standard input and writes it on standard output. Its one flag is -silent.` |
| every other tool but `ymxr-check` (8.4) | X is outside the flags of the tool | `not a flag of the tool: X` |
| `ymxr-sndh` | X begins `-copies` | `not a flag of the tool: X; a tune file is packed already` |
| a tool with `-kK`, `-mN` or `-copies[S]` | the rest of X after `-k` or `-m` is other than a decimal integer, or after `-copies` other than a decimal number | `not a number: X` |
| `ym-to-ymxr`, `ymxr-check` | the rest of X after `-r` is other than a decimal integer, `-r` alone excepted | `not a row count: X` |
| `ymxr-trace`, `ymxr-prg`, `ymxs-to-prg` | the rest of X after `-r` is other than a decimal integer, `-r` alone included | `not a row count: X` |
| `ym-to-ymxs` | the rest of X after `-r` is other than a decimal integer, `-r` alone excepted | `not a row number: X` |
| `ymxr-multi` | X names zero files | `ymxr-multi tune.ymxr [more.ymxr ...] [-nNAME]...` |
| `ymxr-multi` | more `-nNAME` flags than files | `N names for M tune files` |

**3.3 `-silent`.** With `-silent` a tool omits the report and the summary
line, and writes each note after the output as 1.5 defines; the flag
leaves an error and a warning as they are.

**3.4 Reading.** A tool reads standard input to its end before writing
to standard output or standard error, a wrong call excepted; where the
read fails it writes `cannot read standard input: ` and the message of
the failure, and exits with 2.

**3.5 Writing.** The output is one write. Where it fails, the tool writes
`cannot write standard output` and exits with 2.

**3.6 The exit code.**

| exit | the invocation |
|---|---|
| 0 | completed: the output is written, and every warning found is reported |
| 1 | ended with an error of the input: a file outside the format the tool reads, a structure this format leaves unencoded (7.1), or a dump that fails its check (8) |
| 2 | ended with a wrong call, or a failed read, write or run of another program |

**3.7 The order** of the lines on standard error: the warnings (7.2),
the report's lines in the order of the work, the summary line, the notes
of 1.5 that remain, the counted notes.

---

## 4. The packer's flags and the padding

The tools that write a tune file share the packer's flags and the
padding: `ym-to-ymxr`, `ymxs-to-ymxr`, `ymxs-to-sndh`,
`ymxs-to-prg`, and `ymxr-check` for the conversion it checks.

**4.1 `-kK`.** K is the unit the table packs at, DTX's `k`, 1, 2 or 4
(DTX, SPEC.md 2.3), 2 by default.

**4.2 `-mN`.** N is the ring in bytes, 960 by default. The table packs
through the multiple of 30 nearest N, at least 60 and at most 1,110; where
that differs from N the tool notes `the ring is A bytes: a multiple of the
period within the player's reach`, A the ring used.

Note: the player reaches column 29 of the ring through a 16-bit
displacement, and 32,767 divided by 29 is 1,129, the largest ring; 1,110
is the largest multiple of 30 within it.

**4.3 `-copies[S]`.** With `-copies` every column packs with copies from
its literal stream, which sets bit 0 of the payload's flags byte (DTX,
SPEC.md 2.3). S, a decimal number of seconds, is the time the packer
searches for a better parse; with S above 0 the parse differs from run
to run. With S absent the packer reads its opening passes alone.

**4.4 The padding.** Before a tune is encoded, the tool performs rule 6
of SPEC.md 6 on it, R its row count, RR its repeat row where it repeats,
K the unit, an *unset row* a row with every column unset:

1. Where K is above 1, the tune repeats and RR fails to divide by K:
   insert K minus RR modulo K unset rows at RR, and move RR past
   them. Note `padded: N unset row(s) at row RR, before the repeat row,
   so the table packs at unit K`, N the rows inserted, `row` for 1 and
   `rows` otherwise.
2. Where K is above 1 and R fails to divide by K, L = R minus RR for a
   tune that repeats and 0 for one that plays once:
   - where L is 1 to 63: append the loop's rows again until R divides by
     K. Note `padded: the loop's L row(s) written T`, T `twice` or `N
     times`, then `, so the table packs at unit K`;
   - otherwise: append K minus R modulo K unset rows. Note
     `padded: N unset row(s) at row R, so the table packs at unit K`.

The period of the DTX2 table is 30 rows.

**4.5 The packing report.** A tool that writes a tune file reports, per
tune: the heading `the table: 30 columns of R rows, B bytes, packed at
unit K through a ring of A`, B = 30 R; a row a column, named `R0` to
`R13` and `effect i target`, `effect i source`, `effect i timer control`,
`effect i timer count` for i 0 to 3, the value
`%7d -> %6d bytes  (%5.1f%%)`, two spaces before the parenthesis, of the
rows in, the bytes out and their ratio; the heading `the sources: N
table(s) of R row(s), B bytes`; and the heading `packed X bytes into Y
(Z%), the file F bytes`, X = 30 R, Y the DTX2 table, F the tune file.

**4.6 The name** of a tune file (SPEC.md 3.3) is the text the tool's
section names, its characters below space dropped, stripped of
surrounding white space, and cut to 255 bytes of UTF-8 on a character
boundary; where the text is then empty the name word is 0.

---

## 5. ym-to-ymxr

**5.1** The input is a dump; the output the tune file of the tune the two
stages of ymxs.md read and encode from it, padded (4.4) between them and
named by the dump's song name (4.6).

**5.2 The repeat row.** `-r` writes a tune that plays once. `-rRR`
writes one that repeats to row RR; RR above the dump's frame count F is
the error `the repeat row RR is past the dump's F frames`, exit 1, RR
equal to F a tune that plays once, and RR below 0 is read as both flags
absent. With both flags absent the tune repeats to the dump's loop frame;
where that is at or above F the tune plays once and the tool notes `the
dump's loop frame L is past its last frame: the tune plays once`.

**5.3 The report**, in order:

1. The heading `<format>: <title>`, `<format>` the dump's `YM5!` or
   `YM6!`, `<title>` its song name stripped of surrounding white space, or
   `(untitled)` where blank, then ` by <author>` where the author is
   present. Rows: `frames`, `F at H Hz (m:ss)` and `, interleaved` or `,
   a record a frame`; `the loop frame`, `L of the dump's header`;
   `digidrums`, `N of B bytes in all`, where the dump has any.
2. The heading `the flags: <flags>`, the arguments as passed, or `none, so
   the defaults below`. Rows: `-k, the unit`, `K, the default` or `K,
   asked for`; `-m, the ring`, `N bytes, the default` or `N bytes, asked
   for`;
   `-r, the repeat row`, `none, the tune plays once`, `RR, asked for` or
   `RR, the dump's loop frame`; `-copies`, `no, the default: a match beyond
   the ring is not packed`, `yes, the opening passes alone` or `yes, S
   seconds of search, which packs another parse a run`.
3. The heading `the effects: E of 4 run, S source(s), at most 127`. Rows
   for each kind present: `square waves`, `digidrums`, `sinus SIDs`,
   `buzzers`, the value `N, R row(s) in all`.
4. The packing report (4.5).

**5.4 The summary line** is `ym-to-ymxr: F frames at H Hz, S sources,
effects <bits>, repeats at row RR: B bytes`, F the dump's frames, S the
sources, `<bits>` the effects-used byte in binary, RR the repeat row after
padding, and `no row` in place of `row RR` for a tune that plays once.

**5.5 Errors.** A dump the reader rejects is its message, exit 1; the
errors of the encoding are those of 7.1.

**5.6 The counted notes**, each where its count is above 0:

| note | counts |
|---|---|
| `N sinus SID frames dropped: the reference player runs an empty handler for them` | frames whose slot flags a sinus SID |
| `N digidrum triggers dropped: the file has no sample at that number` | frames whose slot flags a digidrum past the dump's samples |
| `N effect frames dropped: a tune names at most 127 sources` | frames whose slot names a 128th source |
| `N digidrums stopped at the row the tune repeats to` | drums running when the repeat row is read |
| `N frames on which a drum kept a SID from running on its voice` | frames of YMXS, ym.md 7.3 |

---

## 6. ym-to-ymxs

**6.1** The input is a dump; the output a structure of one tune, its
title the dump's song name, its composer the author, both stripped of
surrounding white space, its writer `ym-to-ymxs`, its rate the dump's,
its rows the dump's frames as ymxs.md reads them, unpadded. `-r` and
`-rRR` are those of 5.2, with the same error, except that RR below 0 is
written as the repeat row, which a tool reading the structure reports as
an error (YMXS, SPEC.md 1.11).

**6.2 The summary line** is `ym-to-ymxs: <format> "<name>", F rows at H
Hz, S sources`, `<name>` the song name stripped. The counted notes of 5.6
follow it. The tool writes the structure before any check of it.

---

## 7. ymxs-to-ymxr, ymxs-to-sndh and ymxs-to-prg

**7.1 Reading a structure.** The tool reads the input as JSON (YMXS,
json.md) and checks the structure: an error of the form or of the
structure (YMXS, doc/tools.md 4) is written as the lines of the check
joined, exit 1. Each tune is then padded (4.4) and encoded (ymxs.md);
a structure this format leaves unencoded is an error, exit 1, with the
row where it arises:

| condition | line |
|---|---|
| the tune starts more than 127 sources | `the tune runs N sources, and a source column numbers 127` |
| row N has a count outside 0 to 255 | `row N: a count of C, and the count column reaches 0 to 255` |
| row N performs an operation outside `Start`, `Retune` and `Stop` | `row N: an effect this version does not read` |
| a source runs on a target past 24 | `the source <source> runs on <target>, a target this version leaves to a later one (SPEC.md section 8)` |
| a source runs on two targets whose markers are in different columns | `the source <source> runs on targets that mark column A and column B, and a source has one marker column (SPEC.md rule 2(d))` |
| a marked source has a value past 127 in its marker's column | `the source <source> has the value V in row R of column C, and bit 7 of the marker's column is the marker` |
| a source has a value past 255 | `the source <source> has the value V in row R of column C, and a column is one byte` |

**7.2 The warnings.** After the check and before the report, each
finding of YMXS's check of the writing rules (YMXS, SPEC.md 6.3) is a
line `<tool>: warning: <finding>`, with `tune N: ` between `warning: `
and the finding where the multi has more than one tune. `-silent` leaves
them.

**7.3 The tune file** of a tune is named by the tune's title (4.6). The
report per tune: the packing report (4.5), then the row
`<title>`, `(untitled)` where blank, with the value `R rows at H Hz, S
sources: B bytes`, R the row count before padding.

**7.4 `ymxs-to-ymxr`.** The flags are `-kK`, `-mN` and `-copies[S]`. A
multi of one tune writes that tune file and omits the summary line. A
multi of several writes a multi file (BINARIES.md 0) of the tune files
in the multi's order, each named by its title, `(untitled)` where blank;
the summary line is `ymxs-to-ymxr: N tunes in a multi file, B bytes`,
and a multi of 100 tunes or more is the error of 11.2, exit 1.

**7.5 `ymxs-to-sndh`.** The flags are those of 7.4 and `-tTITLE`,
`-cCOMPOSER`, `-perf`, `-lean`, `-pcrel`, `-abs`, `-vbl`, `-tc`. The
output is the SNDH file of section 12 around the tune files of 7.3, one
subtune a tune in the multi's order, with: the title `-tTITLE`, or the
first tune's title, `(untitled)` where blank; the composer `-cCOMPOSER`,
or the first tune's where present after stripping; the subtune names the
titles, `(untitled)` where blank, where the multi has more than one
tune. The errors of 12.2 are exit 1. The report is that of 7.3 alone;
the tool omits a summary line.

**7.6 `ymxs-to-prg`.** The flags are those of 7.5 and `-rROWS`, ROWS the
rows the program plays, 0 by default. The output is the program of
section 13 around the SNDH file of 7.5; the errors of 13.2 are exit 1.
The report is that of 7.3 and the row `the program`, `B bytes, until a
key stops it` for ROWS 0 and `B bytes, ROWS rows` otherwise; the tool
omits a summary line.

---

## 8. ymxr-check

**8.1** The input is a dump on standard input, or the files the
arguments name: a file named is read as a dump, and for a directory
named every file directly under it whose name ends `.ym` in either case,
in name order. The flags are those of `ym-to-ymxr`; the dump is
converted at them and the conversion replayed against the dump (8.3).

**8.2 The verdict** of a dump is written on standard output: `<name>:
replays to its dump`, or `<name>:` and one line of 8.3 under it, each
two spaces in; `<name>` is `standard input` or the file's name. A file
that is other than a dump after unpacking is `<name>: not a
YM3!/YM3b/YM5!/YM6! dump`.

**8.3 The check** converts the dump as `ym-to-ymxr` does, reads the tune
file back, and steps a model of SPEC.md 4 through one pass and one loop
of its table, R + R - RR frames, or R + 1 where the tune plays once,
each row against the dump's frame it was read from; a row of the
padding (4.4) is checked by the two `a row that sets no column` lines
alone. Where any of the first six lines arises the check ends with them;
otherwise the per-frame lines follow, the check ending at the first
frame after which the lines number 20 or more. The lines, in order:

| line | condition |
|---|---|
| `the frame rate is H, not P` | the file's rate H differs from the dump's P |
| `the table has R rows, not N` | the file's row count differs from the padded row count N |
| `the table repeats at RR, not X` | the file's repeat row differs from the padded one X |
| `the file has N sources, not M` | the file's source count differs from the conversion's M |
| `source N is C columns of R rows, not one of W` | source N's table is another shape than one column of W rows |
| `source N row R is a marker`, `source N row R is not the marker` | bit 7 of row R differs from the marker's place, the last row |
| `r: a row that sets no column wrote Rc`, `r: a row that sets no column wrote R13` | row r of the padding writes a register |
| `f: effect i runs source S under a drum on its voice` | at frame f the model runs a SID where the dump's other slot runs a drum on that voice (YMXS, ym.md 7.3) |
| `f: effect i runs no source where the dump flags kind K` | the dump flags an effect on slot i and the model's effect i is idle |
| `f: effect i runs source S (kind K value V) on Rt at sel/cnt, not source N (kind K2 value V2) on Rt2 at sel2/cnt2` | the model's effect differs from the dump's slot in source, target, select or count |
| `f: the drum is not started` | the dump flags a drum on slot i and row f leaves the source column of effect i unset |
| `f: effect i runs source S where the dump flags no effect` | the model's effect i runs where the dump's slot i is empty, a drum within its F frames (below) excepted |
| `f: Rc's column is set to V while an effect runs on it` | row f sets a volume register an effect runs on (SPEC.md 6, rule 1) |
| `f: Rc is V, not W` | the model's register c differs from the dump's, R7 with `$09` shifted by the voice ORed in while a drum runs there |
| `f: R13 written V, the dump writes W`, with `not written` and `does not write` for either side absent | the model's write of R13 differs from the dump's |

A drum runs from its start frame for the F frames YMXS, ym.md 7.4 step 4
reckons.

**8.4 A file that fails to read** is a verdict of one line: `unreadable:
<message>`, `the archive does not unpack: <message>`, or `the converter
refuses it: <message>` for a dump the converter rejects. A flag
outside the converter's is that verdict with the message `not a flag of
the tool: X`, exit 1, in place of the wrong call of 3.2 (19.5).

**8.5 Several files.** The report is the heading `N file(s) to read`,
then `, at <flags>` where flags were passed; the progress lines `read`
(1.8); the verdicts in the order the arguments name the files, a
directory's `.ym` files sorted by name within it; and on standard output
after them `N dump(s), M wrong`, then `, K file(s) not a dump` where K is
above 0. The Java tree reads the files in parallel, the Go tree in
order; the verdicts are in that order in both.

**8.6 The exit** is 0 where every dump replays, 1 where any dump fails or
standard input is other than a dump, and 2 where a directory fails to
list or, in the Go tree, a named file fails to stat (19.5).

---

## 9. ymxr-trace and ymxr-layout

**9.1** The input is a tune file; the output its record (SPEC.md 7):
the first line, then one entry a frame for ROWS frames, `-rROWS`, or by
default R + R - RR frames for a tune that repeats and R + 1 for one that
plays once, the entry `{"result":-1}` ending the record where the tune
ends before ROWS frames (SPEC.md 7).

**9.2 The report** is the heading `the tune file: B bytes` and the rows
`the table`, `R rows of 30 columns, repeating at row RR`; `the frame
rate`, `H Hz`; `the sources`, `S`; `the rows to record`, `one pass and
the loop once` or ROWS. For a file the reader rejects the heading is `the
tune file: B bytes, which this reader does not read: <message>`. The
summary line is `ymxr-trace: B bytes of rows`.

**9.3 Errors.** A multi file is `this is a multi file of several tunes,
and a record is of one tune`, exit 1. A file the reader rejects (SPEC.md
3.3, R6.1) is `no record: this reader does not read the file`, exit 1,
after the report.

**9.4** The conformance kit's references are this tool's output
([conformance/README.md](conformance/README.md)).

**9.5 `ymxr-layout`.** The input is a file of BINARIES.md 0, 1, 3 or 4,
a multi file, a bound tune, an SNDH file or a TOS program; the output is
its record (BINARIES.md 6), one line of JSON a part: the tags and their
values, the core's descriptor, the subtune table, each bound tune with
its sources, the images and the workspace, and of a program the PRG
header, the stub's descriptor and where the SNDH file begins. The tool
reads a file, so `-silent` is its one flag.

**9.6 The report** is the heading `the file: B bytes, kind K`, K one of
`multi`, `bound`, `sndh` and `program`, and a row for each part the
record has one or more of: `the tag(s)`, `the tune(s)`, `the
source(s)` and `the image(s)`, each the lines of that part. The summary
line is `ymxr-layout: N line(s)`.

**9.7 Errors.** A file of no kind of 6.1 is `not a file BINARIES.md
defines: no YMXM, YMXB, $601A or SNDH`, exit 1; a file whose record runs
past its end is `the record runs past the file's B bytes`, exit 1.

**9.8** The binaries kit's references are this tool's output
([conformance-binaries/README.md](conformance-binaries/README.md)).

---

## 10. ymxr-bind

**10.1** The input is a tune file; the output its bound tune (BINARIES.md
1). `-silent` is the tool's one flag.

**10.2 The report:** the heading `the tune file: B bytes` with the rows
`the table`, `the frame rate` and `the sources` of 9.2; the heading
`bound: DTX's reader for the table in place of the table` with the rows
`the reader's image`, `at A, B bytes`, A the image's offset in the bound
tune; `the source tables`, `N of B bytes`, where the tune has sources;
`the state block`, `B bytes, which the host finds the workspace for`;
`in all`, `B bytes, D over the tune file`, D the bound tune's bytes less
the tune file's. The summary line is `ymxr-bind: B bytes`.

**10.3 Errors.** A multi file is `this is a multi file of several tunes,
and a bound tune is one tune: ymxr-sndh reads a multi file`, exit 1; a
file the reader rejects is its message, exit 1.

---

## 11. ymxr-multi

**11.1** The arguments name tune files, one subtune each in the order
named, and `-nNAME` flags, the i-th naming the i-th file; the output is
the multi file (BINARIES.md 0). A tune is named by its `-nNAME`, else by
the name its tune file records (SPEC.md 3.3), else by the stem of its
file name: the name after the last `/`, up to its last `.`.

**11.2 Errors.** A file that fails to read is `cannot read F: <message>`,
exit 2; a file the reader rejects (writing.md 7) is its message, exit 1;
a hundred tune files or more is `N tunes, and a multi file has 99 at
most`, exit 1.

**11.3 The summary lines** are `ymxr-multi: <name>: B bytes` for each
tune, then `ymxr-multi: N tune(s), B bytes`.

**11.4 Reading a multi file.** A file of 8 bytes or more beginning `YMXM` is
read as a multi file (BINARIES.md 0), and a tool that reads one rejects,
with the line, exit 1: a version word other than 3 to 6, `version V is not
3, 4, 5 or 6`; a count outside 1 to 99, `N tunes, and a multi file has 1 to
99`; an index past the file's end, `the entries of N tunes stand past the
file's B bytes`; an entry outside the file, `tune N stands at A for B bytes,
and the file has F`, A its offset.

---

## 12. ymxr-sndh

**12.1** The input is a tune file, one subtune, or a multi file, its
tunes the subtunes in order, each named by the name the multi file
records. The output is the SNDH file of BINARIES.md 3 around the core
the switches select: `-perf` the core with the raster monitor in;
`-lean` the core whose ticks omit the interrupt-level drop and the
end-of-interrupt write (performance.md); `-pcrel` the core whose ticks
read a row through the program counter and `-abs` the core whose ticks
read an absolute address (BINARIES.md 5.5); and two or three together
the core that is those. Where neither `-pcrel` nor `-abs` is passed the
tool reads the file's last bound tune against the core's first byte:
within 32,767 bytes it writes the core that reads through the program
counter, and further off the core that reads an address. The clock tag
names Timer C, or the VBL where `-vbl` or `-perf` is passed: the raster
monitor paints one frame of calls, which reads against the raster where
the tick comes from the VBL (BINARIES.md 3.2, performance.md). `-tc`
names Timer C over `-perf`, and `-vbl` and `-tc` together are `-vbl and
-tc name two clocks`, exit 2. A set that claims Timer C names the VBL,
and `-tc` with such a set is the error of 12.2. The title is `-tTITLE`,
else the first name, `(untitled)` where a tune file was read or the name
is blank; the composer `-cCOMPOSER`, or absent. In the tags (BINARIES.md
3) each text is reduced to its characters $20 to $7E; the report of 12.4
prints the text as passed.

**12.2 Errors**, each exit 1:

| condition | line |
|---|---|
| the multi file fails to read | the line of 11.4 |
| zero tune files | `no tune files: an SNDH file has one subtune at least` |
| more than 99 | `N tune files: the '##' tag's two digits hold at most 99 subtunes` |
| subtune N fails to read | `subtune N: <message>` |
| subtune 1 has a rate of 0 | `subtune 1 plays at 0 Hz: an SNDH file records a rate of 1 Hz or more` |
| subtune N has a rate other than subtune 1's | `subtune N plays at H Hz and subtune 1 at R: an SNDH file records one rate` |
| the tag block exceeds a `bra.w` | `the tag block is B bytes, and a bra.w reaches 32767` |
| Timer C is asked for and the claims byte of the set has it | `the set claims Timer C and the clock asked for is Timer C: the player's handler has that timer` |
| the core's descriptor fails its check (12.3) | the line of 12.3 |
| `-pcrel` was passed and the last bound tune ends B bytes past the core's first byte, B above 32,767; the tool that was passed neither switch writes the other core instead (12.1) | `the tunes end B bytes past the core's first byte, and a tick that reads a row through the program counter reaches 32767` |

**12.3 The core's descriptor** (BINARIES.md 2) is checked before the
combine: `not an SNDH core: no YMXS at 12`; `the core's descriptor is
version V, and this writes 1`; `the core reads bound tunes to version V, and
this binds at W`, W the version the tune binds at; `the core's flags at 22
read F, and the raster monitor asked for needs bit 0 set`; `... the lean
tick asked for needs bit 1 set`, or `... the row read through the program
counter asked for needs bit 2 set`.

**12.4 The report:** the heading `the core: <file>, B bytes` with the
row `the switches`, of `-perf, the raster monitor in` and `-lean, ticks
that neither drop the interrupt level nor write an end of interrupt`
where those were passed, and then one of `ticks that read a row through
the program counter`, `-abs, ticks that read a row through an absolute
address` where that was passed, and `ticks that read a row through an
absolute address: the tunes end past the 32767 bytes a displacement
reaches` where the tool chose it (12.1), joined by `; `; the heading
`the tags: TITL <title>`, then `, COMM <composer>` where present, `,
<clock><rate>` and `, FLAG ~<letters>`, then `, !#SN with N name(s)` for
several subtunes; a row a subtune, its name or `the tune`, `B bytes
bound to B2, its table in image I`, B the tune file's bytes, B2 the
bound tune's, I the number of its image from 1; the heading `the images:
N image(s) of B bytes, DTX's reader once a set of tunes that share one`
with a row an image, `image I`, `<shape>, N tune(s)`, the shape `DTX2 at
unit K, a ring of A, values of W`, W the width of a value in bytes, then
`, with copies` where packed so; the heading `the file: B bytes, the
core C, the images I, the tunes T, the workspace and the rest W`, C, I,
T and W the bytes of the core, the images, the bound tunes, and the
remainder. The summary line is `ymxr-sndh: B bytes, N subtune(s)`.

---

## 13. ymxr-prg

**13.1** The input is an SNDH file of section 12; the output the program
of BINARIES.md 4: the stub patched with the subtune count, the rate of
the clock tag and the FLAG letters read from the tags, ROWS from
`-rROWS`, 0 by default, and the core's offset. The program plays from
the clock the file's tag names, Timer C or the VBL, and from the clock
passed where `-vbl` or `-tc` names one (BINARIES.md 4.3); `-vbl` and
`-tc` together are `-vbl and -tc name two clocks`, exit 2. It plays ROWS
rows and stops, or plays on until SPACE or ESC for ROWS 0; lists the
subtunes; selects one on LEFT, RIGHT, UP, DOWN or a typed number; and
starts the next subtune where a subtune that plays once has ended.

**13.2 Errors**, each exit 1: the lines of BINARIES.md 4's tag reader
for a file outside the layout (`not an SNDH file: no SNDH at 12`, `the
SNDH file's tags have no '##' subtune count`, `the SNDH file's tags have
no TC or !V rate`, `the SNDH file's FRMS tag at A stands before the '##'
count that sizes it`, the same with `TIME` and with `!#SN`, `the SNDH
file's tag X at A is not one this reads`, `not an SNDH file: no HDNS
ends its tags`, `the SNDH file has no core: no YMXS past its tags`, `the
core begins at C, and the entry triple reaches R`, `the core begins at C
and the file ends B bytes on, short of the core's descriptor, 36
bytes`); `rows N does not fit a long` for ROWS outside 0 to
4,294,967,295; and `the file plays from the VBL at H Hz: the stub's VBL
is a 50 Hz clock, so this set needs a separate host or the VBL asked
for` where the clock tag is `!V` or the FLAG letters have `c`, the rate
is other than 50, and the clock is left to the file; and `the set claims
Timer C and the clock asked for is Timer C: the player's handler has
that timer` where `-tc` is passed and the FLAG letters have `c`.

**13.3 The report:** the heading `the SNDH file: B bytes, N subtune(s)
at H Hz, FLAG <letters>`; the heading `the stub: B bytes, patched` with
the rows `the subtunes`, N; `the rows to play`, `0, until a key stops
it` or ROWS; `it plays from`, one of `Timer C, T ticks a second and a
row every K`, T the stub's field at 28 and K T divided by the rate
(BINARIES.md 4.10), the same with `, asked for` after it where `-tc`
named the clock, `the VBL, asked for`, `the VBL, the file's clock tag`
and `the VBL, the set claims Timer C`; `the screen`, `cleared before the
banner`; the heading `the program: B bytes`. The summary line is
`ymxr-prg: B bytes, until a key stops it` or `ymxr-prg: B bytes, ROWS
rows`.

---

## 14. The pipe

**14.1** The output of one tool is the input of the next; README.md,
Converting and playing, lists the calls. Every conversion passes through
the structure (ymxs.md), and `ym-to-ymxr` writes the bytes `ym-to-ymxs
| ymxs-to-ymxr` writes.

**14.2** A tool that ends with an error leaves standard output empty, so
the next tool reads an empty input and reports the error of its format.
The exit code of a pipe is the shell's.

**14.3** `bin/ymxr-set` runs these calls over a set of dumps and writes
the program on standard output (16.6).

---

## 15. The player

**15.1** `68k/YMXR.S`, assembled with `rmac -m68000 -fr`, is the player;
its first three longs are `bra.w` to the calls, and the SNDH core
(BINARIES.md 2) and the rigs call them.

| offset | call | in | out |
|---|---|---|---|
| 0 | `YMXR_init` | `a0` the bound tune (BINARIES.md 1), on an even address; `a1` the workspace, on a long | `d0` 0, or -1 for a bound tune of another magic or version, whose image's variant byte is other than 2, or with a source past the handlers' reach (BINARIES.md 1.5) |
| 4 | `YMXR_play` | `a0` the workspace | `d0` 0, or -1 where the tune has played its last row and plays once; that call leaves every register as it is |
| 8 | `YMXR_stop` | `a0` the workspace | - |

**15.2** Every call clobbers `d0` to `d5` and `a0` to `a5`, and leaves
`d6`, `d7` and `a6` as they were. `YMXR_play` runs in supervisor mode at
any interrupt level. `YMXR_init` reads the first row and claims each timer
of the effects-used byte at level 7; a call writes the row the call
before it read, then reads the next.

**15.3 The workspace** is `YMXR_FIXED`, 2,120 bytes, then the state
block of the bound tune's image, whose length the bound tune records at
offset 12 (BINARIES.md 1); a host allocates the sum, on a long.

**15.4 `YMXR_stop`** releases each claimed timer: stopped, its interrupt
disabled and masked, its pending bit cleared; then writes R8, R9 and R10
to 0, R13 down to R0 to 0, and R7 to `$FF`; and restores the MFP's
vector register where the build defines `YMXR_AEOI` as 1.

**15.5** The player leaves every vector, timer control register and
interrupt enable for the host to keep and restore, and leaves a timer the
tune leaves unused alone; BINARIES.md 5 defines the host's side, and the
SNDH core performs it.

---

## 16. The scripts

**16.1 `ym/play.sh [options] tune [more ...] [out.wav]`** converts,
combines and runs under Hatari with the emulator's sound on. The first
name is a dump or a tune file; a later name ending `.ym` or `.ymxr` in
either case is another, one subtune each in the order named, each named
by its file; any other name records the run instead: Hatari writes an
AVI, `ym/avi.py` reads its sound out as that WAV and its last frame as a
PNG of the same stem. The options `-kK`, `-mN`, `-rRR`, `-r`,
`-copies[S]` reach `ym-to-ymxr`, and with a tune file among the names
are the error `ym/play.sh: <name> is a tune file, and -k, -m and -r pack
one`, exit 2; `-tTITLE` and `-cCOMPOSER`, `-perf`, `-lean`, `-vbl` and
`-tc` reach `ymxr-sndh`, the title the first name's stem by default;
`-vN` stops the run after N frames, and `-vbl` is read as the clock
before it; `-silent` reaches every tool; `-h` prints the head of the
script, exit 0, and a call with zero names prints it, exit 2. Several
tunes go through `ymxr-multi`, each named by its stem, and the program
picks between them as 13.1 defines. The script writes `TUNE.SND` and
`TUNE.PRG` under a temporary directory: `ym/play.sh: TUNE.SND and
TUNE.PRG are under <dir>` on standard error. A second name to record to
is the error `ym/play.sh: <a> and <b> both name a file to record to`,
exit 2; an option outside these is `ym/play.sh does not read <option>`,
exit 2.

**16.2 `ym/play-ymxs.sh [options] [tune.ymxs] [out.wav]`** runs
`ymxs-to-prg` on the file named, or on standard input otherwise, and the
program under Hatari as 16.1 runs one; `-rROWS`, `-vN`, `-silent`, `-h`
are the script's, and `-kK`, `-mN`, `-copies[S]`, `-tTITLE`,
`-cCOMPOSER`, `-perf`, `-lean`, `-pcrel`, `-abs`, `-vbl`, `-tc` reach
`ymxs-to-prg`, and `-vbl` is read as the clock before `-vN`. A name
ending `.ymxs` or `.json` is the structure; a second is the error `<a>
and <b> both name a structure; a multi's tunes are its subtunes`, exit
2.

**16.3 `ym/hatari.sh WORK [VBLS] [out.wav]`** runs `TUNE.PRG` under
`WORK` with `--tos $TOS --machine st --cpuclock 8 --cpu-exact on
--compatible on --memsize 4 --sound 44100 --ym-mixing model --log-level
fatal`, `--run-vbls VBLS` where VBLS is present, and with a third name
`--fast-forward on --avirecord --avi-vcodec png --png-level 1 --avi-file
WORK/run.avi`, the AVI then read out as 16.1 defines. Both play scripts
end in it.

**16.4 `ym/cost.sh [-lean] [-silent] tune.ymxr [more ...]`** measures the
play call (performance.md): for each tune file an SNDH file on the
`-perf` core, `-lean` with it where passed, a program around it, a run
under Hatari for `VBLS` frames, 1,500 by default, tracing the writes to
the background colour, and `ym/cost.py` on the trace, one line a tune. An
option outside the two is `ym/cost.sh does not read <option>`, exit 2.

**16.5 `Binaries DIR... [-aRMAC] [-sSOURCES]`**, class
`org.ymxr.Binaries`, assembles the eight cores and the stub with `rmac
-m68000 -fr -i68k <defines> -o` into each directory named, one line each
`%-24s %5d bytes`; the assembler is `rmac` on the path or `-aRMAC`, the
sources `68k` or `-sSOURCES`. A failure is `binaries: no <file>
assembled from <source> with an assembler at <rmac>: <message>. The
build needs rmac, and -Drmac=PATH names another.`, exit 1; a call with
another flag or zero directories prints the usage, exit 2. The Maven
build runs it into `target/classes/org/ymxr/68k` and `go/binaries/data`.

**16.6 `bin/ymxr-set [options] tune.ym [more.ym ...]`** writes a TOS
program of the dumps named on standard output, a subtune each in the
order named, each under the name its tune file records (11.1): the calls
of 14.1 run over one set. The tools are the Go tree's (19.3), built by
`go build` into `target/go` on the first call and again where a Go
source, the module files or an assembled binary is newer than that
build, which writes on standard error.

Every option of the four tools is the script's, under the tool's name
but for the program's row count, which `-rRR` already spells for the
converter:

| option | reaches |
|---|---|
| `-kK`, `-mN`, `-rRR`, `-r`, `-copies[S]` | `ym-to-ymxr` (4, 5) |
| `-nNAME`, the i-th naming the i-th dump | `ymxr-multi` (11.1) |
| `-tTITLE`, `-cCOMPOSER`, `-perf`, `-lean`, `-pcrel`, `-abs`, `-vbl`, `-tc` | `ymxr-sndh` (12) |
| `-rowsN`, the rows the program plays | `ymxr-prg` as `-rN` (13.1) |
| `-silent` | every tool (3.3) |

`-h` prints the head of the script, exit 0, and a call that names zero
dumps prints it on standard error, exit 2. An option outside these is
`bin/ymxr-set does not read <option>`, exit 2; a hundred dumps or more
is `bin/ymxr-set: N dumps, and an SNDH file has 99 subtunes at most`,
exit 1; `go` off the path is `bin/ymxr-set: the Go tools need go on the
path`, exit 2. A tool that ends with an error ends the script at that
tool's exit code, standard output empty.

---

## 17. The rigs

**17.1 `68k/test/emu/test_ymxr.py [mode] [tune ...]`** converts each dump
named, or every dump under `ym/test`, and a dump the converter reads as
another format (5.1) stands outside the run, named on a line and counted
apart from the tunes that played wrong. The rig binds each tune through
`ymxr-bind` and plays it row by row on an emulated 68000 (unicorn),
against a model of
SPEC.md 4 and 5 built from the tune's tables: every frame's chip writes
in order, each timer's programming, each handler's place, and every
tick's write. The rig fires each tick at the time the model computes, in
place of the interrupt the emulator lacks. It fires two ticks the tune's
timers do not: a tick of a timer no row has started, which writes `$80`
to R0 and stops the timer (SPEC.md 5.2.1), and a tick inside a frame,
which the rig stops at the head of each effect's step and after the last
of them (SPEC.md 4.2.1). The frame it stops in is one whose row moves
what the effect's tick writes, so the reading before that effect's step
and the reading after it differ, and the run reports how many of the
boundaries a tick was fired at. A name ending `.ymxr` is played as it
stands, and a run writes one such tune itself: a start that moves no
place with no start on that timer before it, which no conversion of a
dump writes, to read the place against SPEC.md 4.1 step 4. A tune whose
RR equals R is played one frame past its last row, where the call reports
-1 and leaves every register as it is. A tune that fails is named and the
run continues.

| mode | reads |
|---|---|
| absent | the dumps named, or the eleven under `ym/test` |
| `-corpus[N]` | N tunes spread over the corpus, `YM_CORPUS`: of its F files by name, every (F divided by N)-th, the first N of those; 40 with N absent |
| `-framesN` | each tune for N frames at most |
| `-cycles` | the play call, DTX's advance and the tick handlers counted with DTX's cycle counter under `DTX_REPO/68k/test/emu`, against performance.md's figures; the frame procedure's effect steps and register steps counted apart, frame by frame, against dense register columns: fourteen writes of a set column's form, and the effects' steps behind the test of a column's set bit on the rows whose effect columns are all clear. A run of the eleven fixtures reads what the columns cost and save a frame, the write, the test and the test the player had before, against the section on YMX; and it plays the kit's `envelope`, `envelope-counted` and `counted` tunes, counting the marked and the counted ticks, against the section on a counted tick of two columns, the columns of the build assembled, with the handlers' sizes and the player's. `-cycles -abs` reads the absolute build's tables the same way. A run of the fixtures also walks each row through the frame procedure's register steps, the player's instructions counted with the same table and equal to the rig's count on every frame, and reads plan.md against the walk over 40,000 rows a tune: the groups a row leaves unset, the three gates, and a sample's tick in parts |
| `-refill` | `-cycles` and the advance's parts off one pass: what a refill spends outside ST4's decoder and inside it, and the operations it parses, against the figures of performance.md's play-call section that its table does not carry. A run of the eleven fixtures reads the claims over the set; a run of other tunes reads each tune alone |
| `-hatari` | each tune, at 50 Hz alone, through `ymxr-sndh -vbl` and `ymxr-prg` with 2,000 rows, run under Hatari, the trace of every chip write cut into frames at the VBL and read against the model; `-vbl` names the VBL as the clock the program plays from (BINARIES.md 4.3), which the frames are cut at; a run that names no tune plays the conformance kit's `voices` after the fixtures, since no dump converts to a target of several registers |
| `-stub` | each tune, at 50 Hz alone, through `ymxr-sndh` and then `ymxr-prg` twice, 600 rows each: one program playing from Timer C, which the file's clock tag names, and one from the VBL, which `-vbl` names. Both run under Hatari, and the writes the frame procedure makes are one stream in one order under either clock, over the same frames but for the phase of the first row and the last. A tick's writes are counted apart: an effect's handler writes the chip from its timer, which the two clocks interleave among the rows differently. Then the same tune at 60 Hz, a rate no multiple of the operating system's clock: the tool arms 240 ticks a second (BINARIES.md 4.10), and the gaps between the in-service bit the stub's handler clears read that period on Hatari's MFP, 4.167 ms, against the 5 ms of the 200 Hz clock |
| `-clock` | the stub's Timer C handler under Hatari's profiler: `Circus Attractions 2` in a program at 50 Hz and at 60 Hz, a row every third tick and every fourth, 1,000 VBLs each; each run measures the handler's cycles a tick and the routine around the play call's a row, which performance.md's section on the program's clock reads within a cycle, and the two runs together solve for a tick without a row, which it reads as one of two figures |
| `-cost` | the raster monitor's runs of performance.md, as `ym/cost.sh` makes them: `Synergy Credits` and `Turrican - world 4-3` on the monitor's core over the VBLs the section against YMX names, the first calls, as many as the section counts, on average, at the 99th in a hundred and at most against the table's YMXR rows, and `Synergy Credits` packed at unit 1 over the same run against its figure at most, an average within 2 cycles and a single call within 64: a tick's entry and `rte` fall inside a call or outside it by where the tick lands, which moves with the operating system `TOS` names; then `DBA 2` on the plain core, played from the VBL, the least cycles from the VBL to the frame procedure's first chip write over the frames the raster monitor's paragraph names, which that paragraph reads within one of the shifter's bus slots, 4 cycles: the least moves with the phase the VBL lands at |
| `-perf` | the player assembled with the raster monitor, against the model |
| `-lean` | the player assembled with `YMXR_NEST=0` and `YMXR_AEOI=1`, against the model |
| `-kit` | the tune files named, or the conformance kit's, each frame the player produces against the reader's record from `ymxr-trace`, and the record's first line against the tune's header; `wrong-version.ymxr` is left out of the tunes played, and the rig requires `ymxr-trace` to exit other than 0 with an empty output on it and `ymxr-bind` to reject it |

**17.2 `ym/keys.py [--keep]`** presses the program's keys. It writes a
program of twelve subtunes, each writing the number it is to R0, starts
Hatari with a command fifo and a trace of the chip writes, and presses the
keys of BINARIES.md 4.6 step 4 through the fifo: RIGHT and DOWN step on,
LEFT and UP step back, both wrap, two digits typed inside the pause reach
subtune 12, and a digit no second can grow starts at once. The trace records
which subtune plays, and each check reads the chip's writes. Hatari ends the
run itself at a VBL count, with no dialog to answer, and a run that outlives
it is killed. `--keep` leaves the work directory, and `HATARI` and `TOS`
name the emulator and a TOS image as 16.3 reads them; a missing one is exit
2, a key that lands on another subtune exit 1.

`YMXR_FLAGS` adds flags to the conversion. `RigCallsTest` runs three things
on every build: the two built tunes at `-frames24` on each of the eight
cores of 12.1, `-abs`, `-lean` and `-perf` alone and together and the plain
core; the conformance kit at `-frames24` under `-kit`; and `-refill` over
`Turrican 2 - world completed 1` whole, which reads the tune's row of
performance.md and a refill of a tune with a source. The workflow runs
`-refill` over the eleven fixtures whole as a step after the suite, which
reads the sentences over the set, and `-cycles -abs` over them, which reads
the absolute build's tables. The workflow then runs `-stub`, `-hatari`,
`-clock` and `-cost` under Hatari 2.6.1, built from its tag, and the PAL
image of EmuTOS 1.4, which boots the ST at the 50 Hz the two modes require;
a caller runs them under TOS 2.06, which `TOS` names.

---

## 18. The measurements

**18.1 `ym/convert.py corpus|envelope|frame`** converts the corpus and
prints the figures of experiments.md. `JOBS` is the tunes converted at
once, `DTX_WRITE` DTX's writer, `DTX_RING` the ring, and `DTX_COPIES` the
copies flag.

**18.2 `ym/cost.py trace.txt`** reads a Hatari trace of the background
colour's writes, `--trace video_color`, from a program on a `-perf`
core, and prints each call's span (performance.md).

**18.3 `ym/halves.py trace.txt`** reads a trace of `psg_write,video_vbl`
and prints, for every volume register written, the edges among the
writes, the median half and the halves far from it: the length of each
half of a square wave.

**18.4 `ym/writes.py a.txt b.txt`** reads two such traces and prints, for
every register, whether the values come in the same order and where they
part, counted from the frame the player first writes in.

**18.5 `ym/avi.py in.avi out.wav [frame.png]`** writes the sound of a
Hatari recording as a WAV and its last frame as a PNG.

**18.6 `ym/whole-byte.py`** writes the structure of a tune a timer drives
a whole byte with, six sections of 800 rows at 50 Hz: four on a register
version 5 opened (SPEC.md 3.1.6), one on the envelope period's two bytes
from one timer, which version 6 counts (SPEC.md 2.1.3), and one written
by rows alone. `python3 ym/whole-byte.py | bin/ymxs-to-prg -r4800 >
dist/whole/TUNE.PRG` makes a program of it, and experiments.md reads what
the sections come to.

**18.7 `ym/measure.py`** reads every dump of the corpus under `YM_CORPUS`
and prints, over its frame steps, how the registers move: each voice's
coarse tone byte moving while its fine byte stays, its level moving, and its
envelope bit moving alone; the envelope shape's writes, and those that write
the shape standing; the tunes whose envelope period and whose noise period
stay throughout; the frames of envelope period 0, and those among them with
a voice on the envelope; the shape column's changes, alone, with the bits
beside the period bytes, and with a set bit for each period byte; and each
voice's fine tone byte moving to 0. SPEC.md 1.2 to 1.7 define the columns
these movements are encoded in, and `ym/convert.py` reads the corpus through
this script's reader.

---

## 19. The two trees

**19.1** The tools are written in Java under `src/main/java/org/ymxr/`,
the reference, and in Go under `go/`, module `github.com/odipar/ymxr/go`,
a version of it a tag of that directory, `go/v0.1.0` beside `v0.1.0`.

**19.2 A Java tool** is the script `bin/<tool>`, one line running `bin/run`
with the class of 2.1 and the arguments. `bin/run` builds where
`target/classes/.built` or `target/classpath` is absent, a core of
BINARIES.md is absent from `target/classes/org/ymxr/68k`, or a file under
`src/main/java`, `pom.xml` or a `68k/*.S` source is newer than `.built`:
`mvn -q process-classes dependency:build-classpath` with standard input
closed, under the lock `target/.building`, a directory one process creates
and the others wait for, one second at a time, up to 180 seconds, after
which a waiting process writes `run: a build has held <lock> for three
minutes` and exits with 2. Then it runs the class with `java -ea`. The build
needs Java 23, Maven, rmac (`-Drmac=PATH` names another), and DTX `0.11.11`
and YMXS `0.4.7` in the local Maven repository, `mvn install` in each
checkout. `.github/workflows/test.yml` does that on a GitHub runner and then
runs `bin/suite` (19.6), with Go, rmac, unicorn and DTX's `dtx-write` on it
so that no check skips, then `test_ymxr.py -refill` over the eleven fixtures
whole, which reads performance.md's sentences over the set, `-cycles -abs`
over the fixtures, which reads the absolute build's tables, and then
`-stub`, `-hatari`, `-clock` and `-cost` under Hatari and EmuTOS (17). No
push starts it: a caller starts it from the Actions tab or by `gh workflow
run test.yml`.

**19.3 A Go tool** is one executable, built from `go/` by `go build
./cmd/...`, with the nine 68000 binaries and DTX's twenty-two images
embedded; it runs by itself. The Go tree requires the modules
`github.com/odipar/dtx/go v0.11.11` and `github.com/odipar/ymxs/go v0.4.7`,
which a build fetches.

**19.4 Parity.** `ParityTest` runs the two trees on one input and
requires the same exit code, the same bytes on standard output and, with
the progress lines dropped, the same text on standard error: every dump
under `ym/test` through `ym-to-ymxs`, `ym-to-ymxr`, `ymxs-to-ymxr`,
`ymxr-bind`, `ymxr-sndh`, `ymxr-prg`, `ymxr-trace -r200` and
`ymxr-check`; the flags `-k1`, `-m480`, `-r`, `-r100`, `-copies`, `-k1
-m1129 -copies`, `-tOne -cTwo -perf`, `-lean`, `-r2000`; two tune files
through `ymxr-multi`, `ymxr-sndh` and `ymxr-prg`. Skipped where `go` is
off the path or the Java tree is unbuilt.

**19.5 Where the trees differ.**

| input | the Java tree | the Go tree |
|---|---|---|
| a dump whose slots name a 128th source | the counted note `N effect frames dropped: a tune names at most 127 sources` | `... at most 0 sources` |
| `ymxr-check` on a named file that is absent | the verdict `unreadable: <message>`, counted as a dump, exit 1 | the error `<stat message>`, exit 2, before any verdict |
| `ymxr-check` on a named file that is present and fails to read | `unreadable: <message>` | `unreadable: <file name>` |
| `ymxr-check` with a flag the converter rejects | the verdict `the converter refuses it: not a flag of the tool: X` (8.4) | the verdict `not a flag of the tool: X` |
| `ymxr-check` on an LHA archive that fails to unpack | `the archive does not unpack: <message>`, counted as a dump | `not a YM3!/YM3b/YM5!/YM6! dump`, uncounted |
| `ymxr-check` on several files | read in parallel | read in order |
| the ring of `-mN` | `Math.round` of a float | a float64 plus 0.5, equal for N of 0 upward |

**19.6 The whole suite.** `bin/suite [maven argument ...]` runs the suite of
19.2 on the caller's machine. A skipped test is a check that did not run, so
the script requires go on the path, python3 with unicorn in it, rmac on the
path or at `RMAC`, DTX's packer on the path or at `DTX_WRITE`, and a built
DTX checkout at `DTX_REPO`, exit 2 where one of them is absent; it reads
`gofmt -l go` beside them and ends with 2 where a file would be rewritten.
After the run it reads the count of skipped tests, exit 1 and the lines that
report it where the count is above 0. `pom.xml` requires one release of DTX,
and the message names it: another release of that packer is another set of
bytes.

---

## 20. A release

**20.1** `release/publish.sh [version]` builds the twelve tools from the
Go tree for `win-x64`, `win-arm64`, `osx-x64`, `osx-arm64`, `linux-x64`
and `linux-arm64`, one zip a platform in `dist/release`, named
`ymxr-tools-<platform>-v<version>.zip`, the Windows executables with
`.exe`. The version is the argument, or the first `<version>` of
`pom.xml`. `TARGETS`, a space-separated list, limits the platforms;
`OUT` replaces `dist`. A platform outside the six is `publish: <target>
is not a platform this builds`, exit 1. The nine binaries it requires
under `go/binaries/data` are the ones `go/binaries/binaries.go` names,
so the two lists agree: a missing one is `publish:
<path> is not built: run mvn process-classes` and a Go file naming none
is `publish: go/binaries/binaries.go names no binary`, exit 1 either
way. After the zips the script runs `manifest.sh` (20.2); then, where
the host is macOS or Linux on x64 or arm64 and its platform was built,
it runs the host's executables from a directory outside the repository
with an empty environment, `ym/test/Turrican - world 4-3.ym` through
`ym-to-ymxs` and `ymxs-to-prg`, and prints the byte count of the
program.

**20.2** `release/manifest.sh VERSION DIR` writes `DIR/MANIFEST.txt`:
each zip's name, size and sha256, its contents, and the source commit,
`COMMIT` or `HEAD`.

**20.3** A release is the commit tagged `v<version>`, the tag's message
`YMXR <version>`, and the Go tree of that commit is tagged
`go/v<version>` beside it (19.1), which is the version a caller reaches
the module at. The published release has the six zips and the
`MANIFEST.txt` of 20.1 under it, and its text is the newest entry of
[RELEASES.md](RELEASES.md).

**20.4** [RELEASES.md](RELEASES.md) lists the releases.

---

## 21. The environment

| variable | names | read by |
|---|---|---|
| `YMXR_68K` | a directory of the nine binaries in place of the embedded ones | the Go tools |
| `YMXR_FLAGS` | flags added to the rig's conversions | `test_ymxr.py` |
| `YMXR_TICKS` | set, each frame whose tick count differs from the rates' by more than 1 is printed | `test_ymxr.py -hatari` |
| `COMMIT` | the source commit recorded, `HEAD` by default | `release/manifest.sh` |
| `TARGETS`, `OUT` | 20.1 | `release/publish.sh` |
| `YM_CORPUS` | the corpus directory | `test_ymxr.py -corpus`, `ym/convert.py`, `ym/measure.py` |
| `RMAC` | the assembler, `rmac` by default | `test_ymxr.py` |
| `DTX_WRITE` | DTX's `dtx-write`, `dtx-write` by default | `test_ymxr.py`, `ym/convert.py` |
| `DTX_REPO` | the DTX checkout, `../DTX` by default | `test_ymxr.py -cycles`, `RigCallsTest`, `bin/suite` |
| `DTX_RING`, `DTX_COPIES`, `JOBS` | 18.1 | `ym/convert.py` |
| `HATARI`, `TOS` | the emulator, `hatari`, and a TOS image, `~/hatari-2.6.1_macos/tos-2.06.rom` | `ym/hatari.sh`, `ym/cost.sh`, `test_ymxr.py` |
| `VBLS` | the frames a run plays | `ym/cost.sh` |
