# The task

Implement a YMXR reader from the specification.

**1. The documents.** `SPEC.md` in this directory defines the format;
`YMXS-SPEC.md` beside it defines what a tune is, what each register
reaches, how a rate is reckoned, and what a frame and a tick do with a
row. A citation `(YMXS, SPEC.md 3.4)` in `SPEC.md` is clause 3.4 of
`YMXS-SPEC.md`. Read both. Implement the reader of `SPEC.md` 7 for the
`.ymxr` files under `tunes/`.

The record is `SPEC.md` 7's, which reports the columns of a tune file;
`YMXS-SPEC.md` 7 defines the record of a separate reader, of the
structure, and is outside this task.

**2. What to produce.** `decode.py` in this directory, run as

    python3 decode.py <file.ymxr> <file.rows> <lines>

It prints at most `<lines>` lines of the record `SPEC.md` 7 defines: the
first line, the tune's fixed values, then one line of JSON a frame. A
frame reporting -1 is one line, and the record ends with it. `<lines>`
counts the lines, the first line among them, so the program reads at
most `<lines>` - 1 frames, and fewer where the record ends first; it is
not the F of `SPEC.md` 7.4, which counts frames alone. For a file
whose version word is other than $0003, $0004 or $0005 (`SPEC.md`
3.3.5), the
record is empty and the program's output is empty.

The output is compared with the reference byte for byte: each line free
of spaces, integers in decimal, names in the order `SPEC.md` 7 defines,
the register names in ascending numeric order, and a line feed ending
each line. In Python that is `json.dumps(entry, separators=(",", ":"))`
over dicts filled in that order, `sort_keys` left at its default.

**3. The rows file.** `<file.rows>` is the tune's table: a 16-byte
header, then row 0 to `R` - 1, each its thirty columns in order, one
byte a column, the rows adjoining.

| offset | bytes | what it is |
|---|---|---|
| 4 | 4 | `R`, the row count, most significant byte first |
| 10 | 4 | `RR`, the row the table repeats to, most significant byte first; `R` where the tune plays once |
| 16 | 30`R` | the rows |

Read the table from it. The frame rate, the effects used, the source
index and the sources' tables are in the tune file as `SPEC.md` 3 lays
it out. The DTX2 table in the tune file packs the same rows in a form
another format defines; the rows file stands in for it.

**4. The tunes.** Each is a `.ymxr` beside its `.rows`; the tunes differ
in what they reach. Every tune satisfies `SPEC.md` 6, names the targets
`SPEC.md` 2.1 assigns, 0 to 24, selects 1 to 7 and sources 0 to `S`, 0 the
stop (`SPEC.md` 1.8); a reader records a source's rows as the table has
them, a byte a column and a set bit 7 before the last row included
(`SPEC.md` 7.2), and bit 31 of an index entry marks a counted source
with bits 30 to 0 the offset (`SPEC.md` 3.1.1); what a reader does with
a file outside those is outside this task.

| file | lines to produce |
|---|---:|
| `chambers.ymxr` | 1,001 |
| `circus.ymxr` | 9 |
| `plays-once.ymxr` | 6 |
| `turrican.ymxr` | 3,681 |
| `turrican-2.ymxr` | 183 |
| `synergy.ymxr` | 10,757 |
| `preempt.ymxr` | 801 |
| `retune.ymxr` | 1,201 |
| `fine-zero.ymxr` | 433 |
| `four-timers.ymxr` | 211 |
| `voices.ymxr` | 161 |
| `envelope.ymxr` | 129 |
| `counted.ymxr` | 81 |
| `wrong-version.ymxr` | 0 |

The count is the first line and `R` + `R` - `RR` frames for a tune that
repeats, or `R` + 1 for one that plays once, `R` and `RR` the table's
(`SPEC.md` 7).

**5. The rules.**

1. Work from `SPEC.md` and `YMXS-SPEC.md` alone.
2. The YMXR, YMXS and DTX repositories are outside the task, as is every
   implementation of this format, of the structure under it, of DTX and
   of the compression under them, in those repositories or on the web.
   The task tests whether the two documents alone define the record. The
   *setter* names the directories of the three repositories.
3. The reference output is outside the task: the setter checks an
   answer.

**6. Also produce.** `READ.md`: every file and page read, listed; where
an implementation of anything was read, the list names it.

`NOTES.md`: every place the specification left a choice. For each, the
section, what it omits, what was assumed, and the wording proposed. Mark
each entry **decides output** or **leaves output as it is**, by whether
the assumption changed a byte emitted.
