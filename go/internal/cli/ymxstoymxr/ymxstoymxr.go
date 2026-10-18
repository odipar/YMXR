// Package ymxstoymxr is the tool ymxs-to-ymxr: cmd/ymxs-to-ymxr runs it as an executable,
// and the WebAssembly module (wasm/) runs it by name.
package ymxstoymxr

import (
	"os"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/ymxr"
)

func Main() {
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
		t.Report(report.Count(len(tunes), "tune", "tunes") + " in a multi file, " +
			report.Count(len(file), "byte", "bytes"))
	}
	for _, note := range said.Unsaid() {
		t.Note(note)
	}
	t.WriteBytes(file)
}
