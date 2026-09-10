// Command ymxr-prg reads an SNDH file on standard input and writes the
// program around it on standard output, playing -rROWS rows, or the tune's
// row count without. The stub's flag bit 0 follows the file's core: the
// screen is cleared where that core has the raster monitor in.
package main

import (
	"fmt"
	"os"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/sndh"
)

func main() {
	t, args := tool.Of("ymxr-prg", os.Args[1:], flags.Rows...)
	flags.Only(t, args, flags.Rows)
	rows := flags.RowsOf(t, args, 0)
	said := report.Of(t.Reports())
	file := t.Bytes()
	prg, err := sndh.Program(file, rows)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	made(said, file, prg, rows)
	rowed := "the tune's row count"
	if rows != 0 {
		rowed = fmt.Sprintf("%d rows", rows)
	}
	t.Report(fmt.Sprintf("%d bytes, %s", len(prg), rowed))
	t.WriteBytes(prg)
}

// made says what the program was made of: the file under it, and what the
// stub was patched with.
func made(said *report.Report, file, prg []byte, rows int64) {
	if !said.Says() {
		return
	}
	tags, err := sndh.ReadTags(file)
	if err != nil {
		return
	}
	subtunes := " subtunes at "
	if tags.Subtunes == 1 {
		subtunes = " subtune at "
	}
	said.Say(fmt.Sprintf("the SNDH file: %d bytes, %d%s%d Hz, FLAG %s", len(file),
		tags.Subtunes, subtunes, tags.Rate, tags.Flag))
	said.Row("the subtunes", fmt.Sprintf("%d", tags.Subtunes))
	if rows == 0 {
		said.Row("the rows to play", "0, the tune's row count")
	} else {
		said.Row("the rows to play", fmt.Sprintf("%d", rows))
	}
	said.Say(fmt.Sprintf("the program: %d bytes", len(prg)))
}
