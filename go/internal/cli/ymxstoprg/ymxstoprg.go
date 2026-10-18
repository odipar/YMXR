// Package ymxstoprg is the tool ymxs-to-prg: cmd/ymxs-to-prg runs it as an executable,
// and the WebAssembly module (wasm/) runs it by name.
package ymxstoprg

import (
	"os"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/sndh"
)

func Main() {
	t, args := tool.Of("ymxs-to-prg", os.Args[1:], flags.PackingFlags...)
	flags.Only(t, args, flags.PackingFlags, flags.Tags, flags.Rows)
	rows := flags.RowsOf(t, args, 0)
	said := report.Of(t.Reports())
	multi := flags.Read(t)
	file := flags.SndhOf(t, multi, args, said)
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
