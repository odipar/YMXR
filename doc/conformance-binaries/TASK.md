# The task

Implement a reader of the files YMXR's tools write, from the
specification.

**1. The document.** `BINARIES.md` in this directory defines four files:
the multi file (0), the bound tune (1), the SNDH file (3) and the TOS
program (4). Section 6 defines the record a reader reports of each.
Read it and implement that reader for the files under `files/`.

`BINARIES.md` cites `SPEC.md`, the tune format, and DTX's documents.
Both are outside this task: a tune file inside a multi file and the
image inside an SNDH file are bytes this reader reports the place and
the size of. Where a clause of section 6 needs one of those documents to
be read, that is an entry of `NOTES.md` (6).

**2. What to produce.** `layout.py` in this directory, run as

    python3 layout.py <file>

It prints the record `BINARIES.md` 6 defines, one line a part. The
output is compared with the reference byte for byte: each line free of
spaces, integers in decimal, the keys in the order section 6 defines,
and a line feed ending each line. In Python that is
`json.dumps(part, separators=(",", ":"))` over dicts filled in that
order, `sort_keys` left at its default.

**3. The files.** Eleven, of the four kinds. Each was written by the tools
of the repository from the tunes of the other kit.

| file | what it is | lines to produce |
|---|---|---:|
| `set.ymxr` | a multi file of two tunes, named | 4 |
| `named.ymxr` | the same, its names above $7E | 4 |
| `one.ymxb` | a bound tune written alone | 3 |
| `one.snd` | an SNDH file of one subtune that plays once | 14 |
| `two.snd` | two subtunes, a composer and their names | 17 |
| `timers.snd` | one subtune claiming four timers, at 60 Hz | 19 |
| `perf.snd` | one subtune behind the core with the raster monitor in | 14 |
| `abs.snd` | one subtune behind the core whose ticks read an address | 14 |
| `two.prg` | a program around `two.snd`, 2,000 rows | 20 |
| `timers.prg` | a program around `timers.snd`, the VBL asked for | 22 |
| `armed.prg` | the same, its stub of the later descriptor version | 22 |

**4. The rules.**

1. Work from `BINARIES.md` alone.
2. The YMXR, YMXS and DTX repositories are outside the task, as is every
   implementation of these files, of the tune format, of DTX and of the
   compression under them, in those repositories or on the web. The task
   tests whether the document alone defines the record. The *setter*
   names the directories of the three repositories.
3. The reference output is outside the task: the setter checks an
   answer.

**5. Also produce.** `READ.md`: every file and page read, listed; where
an implementation of anything was read, the list names it.

`NOTES.md`: every place the specification left a choice. For each, the
section, what it omits, what was assumed, and the wording proposed. Mark
each entry **decides output** or **leaves output as it is**, by whether
the assumption changed a byte emitted.
