# YMXR

YMXR is a chiptune format for the Atari ST, and one use of a data engine
called DTX.

**DTX** takes a table of rows and columns, compiles it into a binary, and
gives a caller one row at a time through a single function, `nextRow`. It
says nothing about what a column holds. DTX is a repository of its own.

**YMXR** says what sits in the columns: which one drives a sound register,
which drives a timer, and what a sample table holds. Its player calls
`nextRow` once a frame and turns the row it gets into writes to the YM2149
and the MFP. Nothing here says how a row is stored or unpacked, and
nothing in DTX knows a sound chip exists.

[doc/requirements.md](doc/requirements.md) comes first. Nothing else is
written until it says what YMXR has to do.

The shape follows [YMX](https://github.com/odipar/YMX), which held the
table and the columns together: Java is the source of truth, Go and C#
follow it byte for byte, the 68000 player is under `68k/`, and the harnesses
under `ymx/` hold the three trees to each other.

| | |
|---|---|
| [doc/requirements.md](doc/requirements.md) | what the encoding has to do |
| [doc/SPEC.md](doc/SPEC.md) | the format specification |
| [doc/terminology.md](doc/terminology.md) | the vocabulary the rest use |
| [doc/tools.md](doc/tools.md) | every tool's usage, flags and environment |
| [doc/performance.md](doc/performance.md) | what a play call costs, in cycles |
| [doc/experiments.md](doc/experiments.md) | ideas measured, and what the measurements said |
| [doc/BINARIES.md](doc/BINARIES.md) | the prebuilt binaries, and how a tool combines them |
| [doc/RELEASES.md](doc/RELEASES.md) | what changed in each published set |
| [doc/conformance/](doc/conformance) | the kit an independent reader is written against |
