// Command ymxr-bind reads a tune file on standard input and writes its
// bound tune on standard output, which is the layout the player reads. A
// tune file this does not bind, one of another version for one, is a line
// on standard error and an exit of 1.
package main

import (
	"fmt"
	"os"

	"github.com/odipar/ymxs/tool"

	"github.com/odipar/ymxr/report"
	"github.com/odipar/ymxr/sndh"
	"github.com/odipar/ymxr/ymxr"
)

func main() {
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
	t.Report(fmt.Sprintf("%d bytes", len(bound)))
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
	said.Say(fmt.Sprintf("the tune file: %d bytes", len(tune)))
	said.Row("the table", fmt.Sprintf("%d rows of %d columns, repeating at row %d",
		file.Table.Rows(), file.Table.Columns(), file.Table.Repeat()))
	said.Row("the frame rate", fmt.Sprintf("%d Hz", file.FrameRate))
	said.Row("the sources", fmt.Sprintf("%d", len(file.Sources)))
}

// bound_ says what the binding came to.
func bound_(said *report.Report, tune, bound []byte) {
	if !said.Says() {
		return
	}
	said.Say(fmt.Sprintf("the bound tune: %d bytes, the state block %d",
		len(bound), ymxr.GetLong(bound, sndh.StateAt)))
}
