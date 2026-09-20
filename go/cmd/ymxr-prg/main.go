// Command ymxr-prg reads an SNDH file on standard input and writes the
// program around it on standard output, playing -rROWS rows, or the tune's
// row count without. The program plays from the clock the file's tag
// names, Timer C or the VBL, and from the VBL where -vbl is passed
// (BINARIES.md 4.3). The stub clears the screen before its banner every
// run.
package main

import (
	"fmt"
	"os"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/binaries"
	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/sndh"
	"github.com/odipar/ymxr/go/ymxr"
)

func main() {
	t, args := tool.Of("ymxr-prg", os.Args[1:], flags.Rows...)
	flags.Only(t, args, flags.Rows, flags.VBL)
	rows := flags.RowsOf(t, args, 0)
	vbl := false
	for _, flag := range args {
		if flag == "-vbl" {
			vbl = true
		}
	}
	said := report.Of(t.Reports())
	file := t.Bytes()
	prg, err := sndh.Program(file, rows, vbl)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	made(said, file, prg, rows, vbl)
	rowed := "until a key stops it"
	if rows != 0 {
		rowed = fmt.Sprintf("%d rows", rows)
	}
	t.Report(fmt.Sprintf("%d bytes, %s", len(prg), rowed))
	t.WriteBytes(prg)
}

// made says what the program was made of: the file under it, and what the
// stub was patched with.
func made(said *report.Report, file, prg []byte, rows int64, vbl bool) {
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
	stub, err := binaries.Read(binaries.Stub)
	if err != nil {
		return
	}
	flags := ymxr.GetWord(prg, sndh.Header+sndh.StubFlagsAt)
	said.Say(fmt.Sprintf("the stub: %d bytes, patched", len(stub)))
	said.Row("the subtunes", fmt.Sprintf("%d", tags.Subtunes))
	if rows == 0 {
		said.Row("the rows to play", "0, until a key stops it")
	} else {
		said.Row("the rows to play", fmt.Sprintf("%d", rows))
	}
	said.Row("it plays from", from(tags, flags, vbl))
	said.Row("the screen", "cleared before the banner")
	said.Say(fmt.Sprintf("the program: %d bytes", len(prg)))
}

// from is the clock the program plays from, and what named it: the
// caller, the file's clock tag, or its claims.
func from(tags sndh.Tagged, flags int, vbl bool) string {
	if flags&sndh.FlagVBL == 0 {
		return "Timer C, 200 ticks a second and the rate's share of them"
	}
	if vbl {
		return "the VBL, asked for"
	}
	if tags.Clock == sndh.ClockVBL {
		return "the VBL, the file's clock tag"
	}
	return "the VBL, the set claims Timer C"
}
