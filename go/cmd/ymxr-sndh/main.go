// Command ymxr-sndh reads a tune file or a multi file (doc/BINARIES.md 0)
// on standard input and writes an SNDH file on standard output.
//
// A multi file's tunes are subtunes 1 up in its order, each named by the
// name the multi file records for it. The title is the first tune's name
// unless -tTITLE names another. -lean puts the core whose ticks neither
// drop the interrupt level nor write an end of interrupt under the tunes,
// -perf puts the core with the raster monitor in there, for reading a run,
// and -pcrel the core whose ticks read a row through the program counter
// (BINARIES.md 5.5). The three are one switch each, and any two together
// select the core that is both, so -perf -lean reads what a lean run
// costs. The clock tag names Timer C, or the VBL where -vbl or -perf is
// passed, and a host plays from the clock it names (BINARIES.md 3.2).
package main

import "github.com/odipar/ymxr/go/internal/cli/ymxrsndh"

func main() {
	ymxrsndh.Main()
}
