# task

Implement a YMXR reader from the specification.

`SPEC.md` in this directory is the format specification. Implement a
reader for the `.ymxr` files under `tunes/`: what SPEC.md 7 calls one.

## What to produce

`decode.py` in this directory. Run as:

    python3 decode.py <file.ymxr> <file.rows> <lines>

It prints the record SPEC.md 7 defines: one line of JSON a frame, at most
`<lines>` of them, the first line what the tune states once. A frame
reporting -1 is one line, and the record ends with it: print no line for
any frame after. Of a file whose version is not the one the specification
gives, print nothing.

The bytes matter: no space in a line, integers in decimal, names in the
order SPEC.md 7 gives, register keys in ascending numeric order, and a
line feed ending each line. In Python that is `json.dumps(entry,
separators=(",", ":"))` over dicts filled in that order, and not
`sort_keys`. Your output is compared with the reference byte for byte.

`<file.rows>` holds the tune's table as DTX0 lays it out: row 0 to `R`
minus one, each row its thirty columns in order, one byte a column, and
nothing between them. Take the rows from it. Everything else, the frame
rate, the effects used, the source index and the sources' tables, is in
the tune file as SPEC.md 3 lays it out. The image in the tune file packs
the same rows in a form another format defines, and you do not read
it.

## The tunes

Each is a `.ymxr` beside its `.rows`, and no two have the same shape.
The specification tells you how to read each one. No tune here breaks
section 6, names a target above 13, a select of 0 or a source past `S`,
and what a reader does with one that does is checked by nothing here.

| file | lines to produce |
|---|---:|
| `chambers.ymxr` | 1,021 |
| `circus.ymxr` | 181 |
| `plays-once.ymxr` | 32 |
| `turrican.ymxr` | 3,721 |
| `turrican-2.ymxr` | 361 |
| `synergy.ymxr` | 10,801 |
| `preempt.ymxr` | 841 |
| `retune.ymxr` | 1,201 |
| `fine-zero.ymxr` | 481 |
| `four-timers.ymxr` | 211 |
| `wrong-version.ymxr` | 0 |

The count is the first line and `R` plus `R` minus `RR` frames for a tune
that repeats, or `R` plus 1 for one that does not, with `R` and `RR` the
table's (SPEC.md 7).

## The rules

- Work **only** from `SPEC.md`.
- **Do not read the YMXR repository, or the DTX repository,** and do not
  read any implementation of this format, of DTX or of the compression
  under them, anywhere: not in those repositories, not on the web. This
  is a test of whether the specification alone is enough. Whoever sets
  the exercise says which directories hold those repositories, so you can
  keep out of them.
- You have no reference output. You cannot check your answer.

## Also produce

`SOURCES.md`: every file and page you read, listed. If you read an
implementation of anything, say so plainly. An honest list is worth more
here than a clean one.

`NOTES.md`: every place the specification left you guessing. For each,
the section, what it does not say, what you assumed, and how you would
word it. Mark each entry **decides output** or **costs nothing**,
depending on whether your assumption changed a byte you emitted.
