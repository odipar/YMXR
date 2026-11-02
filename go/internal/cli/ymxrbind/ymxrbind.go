// Package ymxrbind is the tool ymxr-bind: cmd/ymxr-bind runs it as an executable,
// and the WebAssembly module (wasm/) runs it by name.
package ymxrbind

import (
	"fmt"
	"os"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/sndh"
	"github.com/odipar/ymxr/go/ymxr"
)

func Main() {
	t, _ := tool.Of("ymxr-bind", os.Args[1:])
	said := report.Of(t.Reports())
	tune := t.Bytes()
	if ymxr.IsMulti(tune) {
		t.Wrong(tool.Wrong, "this is a multi file of several tunes, and a bound tune is"+
			" one tune: ymxr-sndh reads a multi file")
	}
	read(said, tune)
	bound, err := sndh.Bound(tune)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	bound_(said, tune, bound)
	t.Report(report.Count(len(bound), "byte", "bytes"))
	t.WriteBytes(bound)
}

// read says what the tune file was.
func read(said *report.Report, tune []byte) {
	if !said.Says() {
		return
	}
	file, err := ymxr.Read(tune)
	if err != nil {
		return
	}
	said.Say("the tune file: " + report.Count(len(tune), "byte", "bytes"))
	said.Row("the table", fmt.Sprintf("%s of %s, repeating at row %d",
		report.Count(file.Table.Rows(), "row", "rows"),
		report.Count(file.Table.Columns(), "column", "columns"), file.Table.Repeat()))
	said.Row("the frame rate", fmt.Sprintf("%d Hz", file.FrameRate))
	said.Row("the sources", fmt.Sprintf("%d", len(file.Sources)))
}

// bound_ says what the binding came to, and what the player needs of a
// host.
func bound_(said *report.Report, tune, bound []byte) {
	if !said.Says() {
		return
	}
	file, err := ymxr.Read(tune)
	if err != nil {
		return
	}
	state := ymxr.GetLong(bound, sndh.StateAt)
	image := ymxr.GetLong(bound, sndh.ImageAt)
	// The DTX1 source tables follow the index and the image follows them,
	// so the sources run from the first one's offset to the image, and the
	// image ends at the transformation block where the tune has one and at
	// the file's end otherwise (BINARIES.md 1.3).
	sources := len(file.Sources)
	block := 0
	if file.Version == ymxr.Version {
		block = ymxr.GetLong(bound, sndh.BoundTransformationsAt)
	}
	ends := len(bound)
	if block > 0 {
		ends = block
	}
	said.Say("bound: DTX's reader for the table in place of the table")
	said.Row("the reader's image", fmt.Sprintf("at %d, %s", image,
		report.Count(ends-image, "byte", "bytes")))
	if sources > 0 {
		first := ymxr.GetLong(bound, sndh.BoundIndex(file.Version)) &^ ymxr.Counted
		said.Row("the source tables", fmt.Sprintf("%d of %s", sources,
			report.Count(image-first, "byte", "bytes")))
	}
	if block > 0 {
		said.Row("the transformations", fmt.Sprintf("at %d, %d of %s", block,
			len(file.Transformations), report.Count(len(bound)-block, "byte", "bytes")))
	}
	said.Row("the state block", report.Count(state, "byte", "bytes")+
		", which the host finds the workspace for")
	said.Row("in all", fmt.Sprintf("%s, %d over the tune file",
		report.Count(len(bound), "byte", "bytes"), len(bound)-len(tune)))
}
