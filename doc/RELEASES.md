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
cuts the release, so the caller's machine has an assembler to install or
not as it pleases. They are committed under `go/binaries/data`: a Go module
fetched by its import path contains the files a commit has in it, and an
executable built from one embeds these. `BinariesTest` reads them against
the assembly the build makes, so one that does not match what rmac writes
today fails the build.

A player pins a version of this format: the tune file's is 2 (SPEC.md 3.3)
and the bound tune's is 2 (BINARIES.md 1), and a release's number names
the tools rather than either.

## Published

### 0.1.2, 2026-09-11

<https://github.com/odipar/YMXR/releases/tag/v0.1.2>, built from the commit
tagged `v0.1.2`.

The report three of the tools print where they are not silenced.

- `ymxr-sndh` left out the core's bytes, the `!#SN` tag, what each image
  fixes with the tunes that share it, and the file's parts. The image
  rows are the figure that reports whether a set of subtunes shares DTX's
  reader, and a release before this one had no row to read it from.
- `ymxr-bind` printed one line where five belong: the image's place and
  bytes, the source tables, the state block the host finds the workspace
  for, and what the binding came to over the tune file.
- `ymxr-prg` left out the stub's bytes, the clock it plays from and what
  becomes of the screen, both of which follow the core.

The three printed less than the Java tools they are read against, which
`ParityTest` missed because it ran every tool with `-silent`. It runs
them without the flag now, and reads a set of subtunes that shares one
image against a set split over two, so a report that drifts from the Java
tree fails the build.

Every file a tool writes is unchanged: a tune file, an SNDH file or a
program from 0.1.1 is byte for byte what 0.1.2 writes, and the five
68000 binaries an executable contains are the same bytes. The report is
what moved.

### 0.1.1, 2026-09-11

<https://github.com/odipar/YMXR/releases/tag/v0.1.1>, built from the commit
tagged `v0.1.1`.

A released executable reads a `.ymx` with no other program installed.

- `ymx-to-ymxs` and `ymx-to-ymxr` ran YMX's `ymx-dump` and read the values
  it printed, so a released executable stopped with `cannot run ymx-dump`
  unless `YMX_DUMP` named a built copy of that tool. The two decode the
  file with YMX's reader now, `github.com/odipar/ymx/go`, which an
  executable contains: all thirteen run as they stand.
- The Java tools go on running `ymx-dump`, which `YMX_DUMP` names, since
  YMX publishes no Java artifact for the reader. `ParityTest` reads the
  four files under `ymx/test` through both trees, so the two routes write
  one tune file, one JSON and one report.
- `release/publish.sh` runs the host's executables with an empty
  environment and puts a `.ymx` through them beside the dump, so a release
  that reads one only where `ymx-dump` stands beside it is caught before it
  is published.

The 68000 sources are unchanged since 0.1.0, so the five binaries an
executable contains are the same bytes and the format versions stand:
this release changes the tools only.

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
