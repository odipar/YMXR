# YMXR

YMXR is a redesign of the [YMX](https://github.com/odipar/YMX) encoding,
before 1.0 freezes it. YMXR states the format; YMX does not. YMX is a
prior encoding: what was measured of it remains measured, and what its
specification states, it states for YMX.

ST4 and the streaming model carry over. What this repository works on is
Layer 1: what the streams hold, how a consumer reads them, and the line
between that and the container, which YMX 0.8.3 states together
(doc/requirements.md, R3).

[doc/requirements.md](doc/requirements.md) comes first. Nothing else is
written until it says what the encoding has to do.

The shape mirrors YMX: Java is the source of truth, Go and C# follow it byte
for byte, the 68000 player is under `68k/`, and the harnesses under `ymx/`
hold the three trees to each other.

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
