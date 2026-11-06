// Package ymxstoprg is the tool ymxs-to-prg: cmd/ymxs-to-prg runs it as an executable,
// and the WebAssembly module (wasm/) runs it by name.
package ymxstoprg

import (
	"os"

	"github.com/odipar/dtx/go/dtx"
	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/sndh"
	"github.com/odipar/ymxr/go/ymxr"
)

func Main() {
	Run(nil)
}

func invocation() (*tool.Tool, []string) {
	t, args := tool.Of("ymxs-to-prg", os.Args[1:], flags.PackingFlags...)
	flags.Only(t, args, flags.PackingFlags, flags.Tags, flags.Rows, flags.Links)
	return t, args
}

// Run writes the program with the supplied plain packer, or the default.
func Run(plain dtx.Packer) {
	t, args := invocation()
	rows := flags.RowsOf(t, args, 0)
	said := report.Of(t.Reports())
	multi := flags.Read(t)
	file := flags.SndhWithPacker(t, multi, args, said, plain)
	prg, err := sndh.Program(file, rows, flags.AskedOf(t, args))
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	rowed := "until a key stops it"
	if rows != 0 {
		rowed = report.Count(rows, "row", "rows")
	}
	said.Row("the program", report.Count(len(prg), "byte", "bytes")+", "+rowed)
	t.WriteBytes(prg)
	for _, note := range said.Unsaid() {
		t.Note(note)
	}
}

// Plan visits the jobs for the plain packer of the same command.
func Plan(visit func([]byte, int, int, int) error) error {
	t, args := invocation()
	flags.RowsOf(t, args, 0)
	multi := flags.Read(t)
	flags.AskedOf(t, args)
	packing := flags.PackingOf(t, args)
	said := report.Of(false)
	for _, tune := range multi.Tunes {
		made := flags.PreparedTune(t, tune, packing.Unit, said)
		if err := ymxr.PlainJobs(made.Columns, packing.Unit, packing.Ring, visit); err != nil {
			return err
		}
	}
	return nil
}
