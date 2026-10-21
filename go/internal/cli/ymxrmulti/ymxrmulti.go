// Package ymxrmulti is the tool ymxr-multi: cmd/ymxr-multi runs it as an executable,
// and the WebAssembly module (wasm/) runs it by name.
package ymxrmulti

import (
	"fmt"
	"os"
	"strings"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/ymxr"
)

func Main() {
	t, args := tool.Of("ymxr-multi", os.Args[1:], "-n", "-m", "-copies")
	var named, files, asked []string
	for _, flag := range args {
		switch {
		case strings.HasPrefix(flag, "-n"):
			named = append(named, flag[2:])
		case strings.HasPrefix(flag, "-m") || strings.HasPrefix(flag, "-copies"):
			asked = append(asked, flag)
		case strings.HasPrefix(flag, "-"):
			t.Usage("not a flag of the tool: " + flag)
		default:
			files = append(files, flag)
		}
	}
	// The call is read before a file is, so a flag that is not a number
	// is an exit of 2 and every file is left unread.
	packing := flags.PackingOf(t, asked)
	if len(files) == 0 {
		t.Usage("ymxr-multi tune.ymxr [more.ymxr ...] [-nNAME]... [-mN] [-copies[S]]")
	}
	if len(named) > len(files) {
		t.Usage(fmt.Sprintf("%d names for %d tune files", len(named), len(files)))
	}
	var tunes [][]byte
	var names []string
	for i, name := range files {
		file, err := os.ReadFile(name)
		if err != nil {
			t.Wrong(tool.Failed, "cannot read "+name+": "+err.Error())
		}
		tunes = append(tunes, file)
		// -n names a tune, then the name the tune file records, then
		// the file it was read from
		switch recorded := ymxr.TuneName(file); {
		case i < len(named):
			names = append(names, named[i])
		case recorded != "":
			names = append(names, recorded)
		default:
			names = append(names, ymxr.Stem(name))
		}
	}
	if len(tunes) > ymxr.MostTunes {
		// before the packing, which would run for every tune first
		t.Wrong(tool.Wrong, fmt.Sprintf("%d tunes, and a multi file has %d at most",
			len(tunes), ymxr.MostTunes))
	}
	said := report.Of(t.Reports())
	set, err := ymxr.OfFiles(tunes, packing.Packers(), packing.Ring, said)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	chose := ""
	if packing.Ring == ymxr.Choose {
		chose = ymxr.ChoseForSet
	}
	for i, tune := range set {
		if err := ymxr.Report(said, tune, chose); err != nil {
			t.Wrong(tool.Wrong, err.Error())
		}
		tunes[i] = tune.File
	}
	if err := ymxr.SaySet(said, set); err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	file, err := ymxr.Multi(tunes, names)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	for i, tune := range tunes {
		t.Report(names[i] + ": " + report.Count(len(tune), "byte", "bytes"))
	}
	t.Report(report.Count(len(tunes), "tune", "tunes") + ", " +
		report.Count(len(file), "byte", "bytes"))
	t.WriteBytes(file)
}
