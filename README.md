# YMXR

YMXR states what sits in the columns of a table, and how those columns reach
the YM2149 and the MFP of an Atari ST.

The table is not YMXR's. Its rows, its packing, and the engine that hands a
player one row at a time are DTX's, in a repository of its own. YMXR is
written against one function of DTX's ABI, `nextRow`, and states nothing
about how a row is stored or unpacked.

What is left is the part a chiptune format is for: which column drives which
register, which drives a timer, what a sample table holds, and how little a
player has to do between a row arriving and the chip hearing it.

[doc/requirements.md](doc/requirements.md) comes first. Nothing else is
written until it says what YMXR has to do.

The shape follows [YMX](https://github.com/odipar/YMX), which stated the
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
