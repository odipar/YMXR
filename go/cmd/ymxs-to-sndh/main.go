// Command ymxs-to-sndh reads a YMXS structure as JSON on standard input
// and writes an SNDH file on standard output.
//
// Every tune of the multi is a subtune, 1 upward in its order, behind one
// core (doc/BINARIES.md 2). The title and the composer are the first
// tune's unless -t and -c name others, and a tune's title names its
// subtune where the multi has several.
//
// -perf selects the core with the raster monitor in and -lean the core
// whose ticks neither drop the interrupt level nor write an end of
// interrupt (doc/performance.md); -vbl names the VBL as the clock a host
// plays from, and -perf names it too (BINARIES.md 3.2); the packer's
// flags are ymxs-to-ymxr's.
package main

import "github.com/odipar/ymxr/go/internal/cli/ymxstosndh"

func main() {
	ymxstosndh.Main()
}
