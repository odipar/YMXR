# YMXR

YMXR is a chiptune format for the Atari ST, and one use of a data engine
called DTX.

It takes the name YMX when it is done. Until then,
[YMX](https://github.com/odipar/YMX) is what plays today, and YMXR is what
replaces it.

**DTX** takes a table of rows and columns, compiles it into a binary, and
gives a caller one row at a time through a single function, `nextRow`. It
says nothing about what a column holds. DTX is a repository of its own.

**YMXR** says what a column holds, and what a player does with it. One
method serves the whole of that: a clock advances a table one row, and a
procedure writes that row to the chips. The tune's clock advances the tune's
table, and a timer advances a table of its own at an effect's rate.
Nothing here says how a row is stored or unpacked, and nothing in DTX knows
a sound chip exists.

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
| [doc/glossary.md](doc/glossary.md) | every term, one line each |
| [doc/terminology.md](doc/terminology.md) | the machine, and the terms for it |
| [doc/tools.md](doc/tools.md) | every tool's usage, flags and environment |
| [doc/performance.md](doc/performance.md) | what a play call costs, in cycles |
| [doc/experiments.md](doc/experiments.md) | ideas measured, and what the measurements said |
| [doc/BINARIES.md](doc/BINARIES.md) | the prebuilt binaries, and how a tool combines them |
| [doc/RELEASES.md](doc/RELEASES.md) | what changed in each published set |
| [doc/conformance/](doc/conformance) | the kit an independent reader is written against |
