# YMXR

YMXR is a chiptune format for the Atari ST, and one use of a data format
called DTX.

It takes the name YMX when it is done. Until then,
[YMX](https://github.com/odipar/YMX) is what plays today, and YMXR is what
replaces it.

**DTX** is a data format: a table of rows and columns, where every value
takes one width, 1, 2 or 4 bytes, and the rows repeat at a row of the
table's choosing. It says nothing about what a column holds.
[DTX](https://github.com/odipar/DTX) is a repository of its own, and holds
the readers of the format as this one holds a player.

**YMXR** says what a column holds, and what a player does with it. One
method serves the whole of that: a clock advances a table one row, and a
procedure writes that row to the chips. The tune's clock advances the tune's
table, and a timer advances a table of its own at an effect's rate.
Nothing here says how a row is stored or unpacked, and nothing in DTX knows
a sound chip exists.

[doc/requirements.md](doc/requirements.md) comes first. Nothing else is
written until it says what YMXR has to do.

## What's here

| | |
|---|---|
| `doc/` | the specification, the requirements it is written against, and the rest |
| `src/main/java/org/ymxr/` | the converter: a YM5!/YM6! dump into a tune file, on DTX's Java library |
| `68k/YMXR.S` | the player, and `68k/YMXR_prg.S` a program around it for Hatari |
| `68k/test/emu/` | the rig: the player under emulation against a model of the specification |
| `ym/` | the measurements behind the figures, and `ym/test` seven tunes the tests run on |
| `bin/` | the converter, run out of a build |

A tune converts and plays like this:

```bash
bin/ym-to-ymxr tune.ym tune.ymxr
python3 68k/test/emu/test_ymxr.py tune.ym
```

The shape follows [YMX](https://github.com/odipar/YMX): Java is the
source of truth, and the Go and C# trees are to follow it byte for byte.

## Tests

| what runs | what it checks |
|---|---|
| `mvn test` | the documents against themselves and the house style, and every tune under `ym/test` converted, read back and replayed against its dump |
| `68k/test/emu/test_ymxr.py` | the player on an emulated 68000: every frame's writes, the timers' programming and every tick against the specification's model |
| the same, `-hatari` | the player on a real MFP under Hatari, the trace of its writes against that model |

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
