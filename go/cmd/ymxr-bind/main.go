// Command ymxr-bind reads a tune file on standard input and writes its
// bound tune on standard output, which is the layout the player reads. A
// tune file this does not bind, one of another version for one, is a line
// on standard error and an exit of 1.
package main

import (
	"fmt"
	"os"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/sndh"
	"github.com/odipar/ymxr/go/ymxr"
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
	// The image stands before the DTX1 source tables, so where there is a
	// source the first one's offset ends the image, and where there is
	// none the file does.
	sources := len(file.Sources)
	ends := len(bound)
	if sources > 0 {
		ends = ymxr.GetLong(bound, sndh.BoundIndexAt)
	}
	said.Say("bound: DTX's reader for the table in place of the table")
	said.Row("the reader's image", fmt.Sprintf("at %d, %d bytes", image, ends-image))
	if sources > 0 {
		said.Row("the source tables", fmt.Sprintf("%d of %d bytes", sources,
			len(bound)-ends))
	}
	said.Row("the state block", fmt.Sprintf("%d bytes, which the host finds the"+
		" workspace for", state))
	said.Row("in all", fmt.Sprintf("%d bytes, %d over the tune file", len(bound),
		len(bound)-len(tune)))
}
