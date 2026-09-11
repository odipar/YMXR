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
// interrupt (doc/performance.md); the packer's flags are ymxs-to-ymxr's.
package main

import (
	"os"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
)

func main() {
	t, args := tool.Of("ymxs-to-sndh", os.Args[1:], flags.PackingFlags...)
	flags.Only(t, args, flags.PackingFlags, flags.Tags)
	said := report.Of(t.Reports())
	multi := flags.Read(t)
	t.WriteBytes(flags.SndhOf(t, multi, args, said))
	for _, note := range said.Unsaid() {
		t.Note(note)
	}
}
