// Command ymxr-layout reads a file this repository writes on standard
// input and writes the record of doc/BINARIES.md 6 on standard output,
// one line of JSON a part.
//
// The four kinds are the multi file (BINARIES.md 0), the bound tune (1),
// the SNDH file (3) and the TOS program (4), and the record reports the
// parts of each: the tags and their values, the core's descriptor, the
// subtune table, each bound tune with its sources, the images and the
// workspace, and of a program the PRG header, the stub's descriptor and
// where the SNDH file begins.
package main

import "github.com/odipar/ymxr/go/internal/cli/ymxrlayout"

func main() {
	ymxrlayout.Main()
}
