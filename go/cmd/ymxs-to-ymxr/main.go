// Command ymxs-to-ymxr reads a YMXS structure as JSON on standard input
// and writes a tune file (SPEC.md 3.3) on standard output.
//
// The second stage of every conversion here (doc/ymxs.md). A tune file has
// one tune in it, so a multi of several writes a multi file
// (doc/BINARIES.md 0) instead: ymxr-sndh reads that as a set of subtunes,
// each named by its tune's title.
package main

import (
	"fmt"
	"os"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/ymxr"
)

func main() {
	t, args := tool.Of("ymxs-to-ymxr", os.Args[1:], flags.PackingFlags...)
	flags.Only(t, args, flags.PackingFlags)
	said := report.Of(t.Reports())
	multi := flags.Read(t)
	tunes := flags.TuneFiles(t, multi, flags.PackingOf(t, args), said)
	file := tunes[0]
	if len(tunes) > 1 {
		var names []string
		for _, tune := range multi.Tunes {
			names = append(names, flags.Title(tune))
		}
		made, err := ymxr.Multi(tunes, names)
		if err != nil {
			t.Wrong(tool.Wrong, err.Error())
		}
		file = made
		t.Report(fmt.Sprintf("%d tunes in a multi file, %d bytes", len(tunes), len(file)))
	}
	for _, note := range said.Unsaid() {
		t.Note(note)
	}
	t.WriteBytes(file)
}
