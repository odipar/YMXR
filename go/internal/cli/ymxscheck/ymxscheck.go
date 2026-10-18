// Package ymxscheck is YMXS's tool ymxs-check, for the WebAssembly module
// (wasm/), which runs it by name in a page, where YMXS's executable cannot
// run. Main is the main of YMXS's cmd/ymxs-check at the version go.mod
// requires, line for line and over the same packages, so the module
// writes what that executable writes; WasmTest reads the two against one
// another. YMXS's releases ship the executable, and YMXR's ship it in the
// module alone.
package ymxscheck

import (
	"os"

	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/tool"
	"github.com/odipar/ymxs/go/ymxs"
)

func Main() {
	t, _ := tool.Of("ymxs-check", os.Args[1:])
	said := t.Text()
	multi, err := text.Read(said)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	t.Write(said)
	rows := 0
	sources := 0
	for _, tune := range multi.Tunes {
		rows += len(ymxs.Rows(tune))
		sources += len(ymxs.Sources(tune))
	}
	t.Report(tool.Count(len(multi.Tunes), "tune", "tunes") + ", " +
		tool.Count(rows, "row", "rows") + ", " + tool.Count(sources, "source", "sources"))
	warnings := t.Warnings(multi)
	if warnings == 0 {
		t.Report("every rule of SPEC.md 6 is satisfied")
	} else {
		t.Report(tool.Count(warnings, "warning", "warnings"))
	}
}
