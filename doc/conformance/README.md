# conformance

The kit is 11 tunes, and what a reader of SPEC.md reports of each:
18,276 entries between them, a line of record each. It exists to test
[SPEC.md](../SPEC.md) rather than the code: hand it to someone who has
never seen this repository, and see whether the document alone is enough
to read the tunes.

A writer is a third role again, and writing.md defines its path; the tunes
here are valid files a writer reads as examples.

It tests the **reader** of SPEC.md 7 and requirements.md R2.4: what reads a
tune and reports the result, writing to no chip. A reader requires neither
a machine nor section 5's ticks, since it reports one entry a frame, so the
claim this kit measures is "a reader can be written from the document", not
"a player can be ported from it". The rig checks a player against the same
record, below.

The tunes are converted from the dumps under `ym/test`, six of them
recordings of published music, by Jochen Hippel, Mad Max and Scavenger
as their headers say, and two built. The music is its composers': the
repository's LICENSE covers neither the dumps nor these.

## Running the exercise

Copy `TASK.md`, `tunes/`, `../SPEC.md` and YMXS's `doc/SPEC.md` into a
fresh directory and hand that directory to an implementer with no access to
this repository, to YMXS's, to DTX's, or to any implementation of the
format or the compression under it. Both specifications go over: SPEC.md
here defines the columns and cites YMXS's for what a frame and a tick do
with them, so an implementer handed one of the two is reading half a
document. Name the YMXS copy `YMXS-SPEC.md`, which is what `TASK.md` calls
it. `TASK.md` tells the implementer to keep out of both repositories and
leaves the paths to you, since a checkout is somewhere different on every
machine: name them when you hand the directory over. Keep `MANIFEST.txt`
and the references back: an implementer who can check an answer is not
reading the document, and the exercise measures the document.

Where several implementers run at once, each needs a directory no other
run writes to.

The implementer produces `decode.py`, `READ.md` and `NOTES.md`,
described in `TASK.md`. Compare the output with the reference, which
`bin/ymxr-trace` produces for each tune and `MANIFEST.txt` names by sha256
and bytes.

## What passes

Three tests, all three of which must hold:

1. **Output.** Every record byte-identical to the reference on every
   tune, the empty record of `wrong-version` included.
2. **Sources.** No implementer names an implementation: not a source
   file here, in YMXS or in DTX, not one on the web.
3. **Notes.** No entry is marked "decides output". A choice that settles
   a byte is a sentence the document lacks, whether or not the guess
   matched the reference.

No run has been measured yet.

## What the tunes cover

| tune | what it reaches |
|---|---|
| `chambers` | no effect; R13 written once, with both envelope-period-0 bits beside it |
| `circus` | four frames, fewer than a period |
| `plays-once` | four frames whose `RR` is `R`: the frame after the last row reports -1 |
| `turrican` | three drums on Timer D, each ending by its marker; `RR` at 160, a loop longer than the ring replayed at its exact rows; R13 rewritten |
| `turrican-2` | a loop of one row from an odd `RR`, so the table packs at unit 1; six drums before it, the last stopped by a row |
| `synergy` | nine SIDs on two timers at once, six of them named by both; the select changed without the source, and the count alone; a running source stopped by a row; a tone fine byte 0 with the coarse bit beside it; an odd row count, so the table packs at unit 1 |
| `preempt` | a drum starting on the voice a SID runs on stops the SID first; R8 passed between them with its column unset, written by ticks alone |
| `retune` | a one-row buzzer source on R13, restarted over a running timer with a new rate; select 7 |
| `fine-zero` | a tone fine byte moving to 0 on each voice, with and without the coarse set bit |
| `four-timers` | all four effects on Timers A, D, B and C at 60 Hz, and the rows section 4 allows that no dump produces: a count or a select alone, bit 5 alone, bit 6 alone, a stop with the volume set, the same source again, a target set while running and read at the next start, a target that is not a volume register, a drum closing on 5, R13 set beside a buzzer, a source repeating to its row 2, values under a clear set bit |
| `wrong-version` | the version word $0003: a reader produces no report |

SOURCES.md records where each tune comes from and what it exercises in
full.

## How the kit is kept true

Every tune is converted or built by this repository's code from the row
SOURCES.md records, and `ConformanceTest` makes each again under `mvn test`
and compares the file, its rows and `MANIFEST.txt` with it, so a change to
the converter or the reader that moved a byte of the kit fails there and
writes what is missing beside the kit. The reference is the reader's report
(SPEC.md 7), which `bin/ymxr-trace` produces; `68k/test/emu/test_ymxr.py
-kit` plays every tune but `wrong-version`, which the binder and the reader
reject, on the 68000 player and compares the player's frames, the rig's
model of section 4 and the reader's record with one another, so a
disagreement between the three is a failing test here and no kit is
published on it. Every tune converted from a dump replays to the dump
through `ymxr-check` at its options, in `ConformanceTest`.
