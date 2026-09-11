# task

Implement a YMXR reader from the specification.

`SPEC.md` in this directory is the format specification, and
`YMXS-SPEC.md` beside it is the specification SPEC.md stands on: what a
tune is, what each register reaches, how a rate is reckoned, and what a
frame and a tick do with a row. SPEC.md cites it as `(YMXS, SPEC.md 3.4)`,
which means section 3.4 of `YMXS-SPEC.md`. Read both. Implement a reader
for the `.ymxr` files under `tunes/`: what SPEC.md 7 calls one.

The record you print is SPEC.md 7's, not `YMXS-SPEC.md` 7's. Those are two
readers of two things, and the one asked for here reports the columns a
tune file has.

## What to produce

`decode.py` in this directory. Run as:

    python3 decode.py <file.ymxr> <file.rows> <lines>

It prints the record SPEC.md 7 defines: one line of JSON a frame, at most
`<lines>` of them, the first line the tune's fixed values. A frame
reporting -1 is one line, and the record ends with it: print no line for
any frame after. For a file whose version is not the one the specification
defines, print no line at all.

Your output is compared with the reference byte for byte: no space in a
line, integers in decimal, names in the order SPEC.md 7 defines, register
keys in ascending numeric order, and a line feed ending each line. In
Python that is `json.dumps(entry, separators=(",", ":"))` over dicts
filled in that order, and not `sort_keys`.

`<file.rows>` is the tune's table: a header of sixteen bytes carrying `R`
at bytes 4 to 7 and `RR` at bytes 10 to 13, most significant byte first, as
SPEC.md 3.1 defines a source's, then row 0 to `R` minus one, each row its
thirty columns in order, one byte a column, with no padding between them.
Read the table from it. Everything else, the frame rate, the effects used,
the source index and the sources' tables, is in the tune file as SPEC.md 3
lays it out. The DTX2 table in the tune file packs the same rows in a form
another format defines, and you do not read it.

## The tunes

Each is a `.ymxr` beside its `.rows`, and no two have the same shape. The
specification defines how each one is read. No tune here breaks section 6,
names a target above 13, a select of 0 or a source past `S`, and no test
here covers what a reader does with one that does.

| file | lines to produce |
|---|---:|
| `chambers.ymxr` | 1,001 |
| `circus.ymxr` | 9 |
| `plays-once.ymxr` | 6 |
| `turrican.ymxr` | 3,681 |
| `turrican-2.ymxr` | 180 |
| `synergy.ymxr` | 10,755 |
| `preempt.ymxr` | 801 |
| `retune.ymxr` | 1,201 |
| `fine-zero.ymxr` | 431 |
| `four-timers.ymxr` | 211 |
| `wrong-version.ymxr` | 0 |

The count is the first line and `R` plus `R` minus `RR` frames for a tune
that repeats, or `R` plus 1 for one that does not, with `R` and `RR` the
table's (SPEC.md 7).

## The rules

- Work **only** from `SPEC.md` and `YMXS-SPEC.md`.
- **Do not read the YMXR repository, the YMXS repository, or the DTX
  repository,** and do not read any implementation of this format, of the
  structure under it, of DTX or of the compression under them, anywhere:
  not in those repositories, not on the web. This is a test of whether the
  two documents alone are enough. Whoever sets the exercise names the
  directories those repositories are in, so you can keep out of them.
- You have no reference output. You cannot check your answer.

## Also produce

`READ.md`: every file and page you read, listed. If you read an
implementation of anything, say so plainly: a list that names one is
worth more here than one that leaves it out.

`NOTES.md`: every place the specification left you guessing. For each, the
section, what it omits, what you assumed, and how you would word it. Mark
each entry **decides output** or **costs no byte**, depending on whether
your assumption changed a byte you emitted.
