# RELEASES

What a release contains stands here, and each one published is listed
below it.

## What a release contains

`release/publish.sh` writes `dist/release` (tools.md), and
`release/manifest.sh` the manifest in it:

- one zip a platform, over six: Windows, macOS and Linux, each on x64 and
  arm64. A zip contains the thirteen tools as executables, and each
  executable contains the five 68000 binaries of BINARIES.md and DTX's
  twenty-two images, so one converts a dump and writes a program on a
  machine where neither this repository nor a toolchain is installed
- `MANIFEST.txt`: every zip's size and sha256, what it contains, and the
  source commit the release was built from

The version names every file. It is read out of `pom.xml`, or stands as
the script's one argument.

The five binaries are assembled from `68k/` by rmac on the machine that
cuts the release, and nowhere else: a release runs no assembler on the
caller's machine, and no binary is tracked in the tree.

A player pins a version of this format: the tune file's is 2 (SPEC.md 3.3)
and the bound tune's is 2 (BINARIES.md 1), and a release's number names
the tools rather than either.

## Published

### 0.1.0, 2026-09-11

<https://github.com/odipar/YMXR/releases/tag/v0.1.0>, built from the commit
tagged `v0.1.0`.

The first release: the player, the format, the binaries and the thirteen
tools.

- The player of SPEC.md, its four effects on the MC68901's timers, and
  the four cores of BINARIES.md: the plain one, the one with the raster
  monitor in, the lean one and the one that is both.
- The tune file (SPEC.md 3.3), the bound tune, the SNDH file and the
  program stub, defined byte for byte in BINARIES.md.
- Every conversion passes through YMXS, the tune data structure
  (doc/ymxs.md): a dump or a YMX file is read into it and a schema maps
  that onto the columns.
- Thirteen tools, each reading standard input and writing standard
  output: the two converters, the structure's five filters, the multi
  file's maker, the binder, the two combiners, the trace and the check.
- The same thirteen in Go under `go/`, which `ParityTest` runs against
  the Java tree byte for byte. A release ships those, one zip a platform.
- The conformance kit (doc/conformance), the emulation rig
  (68k/test/emu) and the parity rig against YMX (ym/parity.py).
