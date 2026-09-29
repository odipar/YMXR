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
	flags.Only(t, args, flags.Rows, flags.Clocks)
	rows := flags.RowsOf(t, args, 0)
	asked := flags.AskedOf(t, args)
	said := report.Of(t.Reports())
	file := t.Bytes()
	prg, err := sndh.Program(file, rows, asked)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	made(said, file, prg, rows, asked)
	rowed := "until a key stops it"
	if rows != 0 {
		rowed = report.Count(rows, "row", "rows")
	}
	t.Report(report.Count(len(prg), "byte", "bytes") + ", " + rowed)
	t.WriteBytes(prg)
}

// made says what the program was made of: the file under it, and what the
// stub was patched with.
func made(said *report.Report, file, prg []byte, rows int64, asked sndh.Asked) {
	if !said.Says() {
		return
	}
	tags, err := sndh.ReadTags(file)
	if err != nil {
		return
	}
	said.Say(fmt.Sprintf("the SNDH file: %s, %s at %d Hz, FLAG %s",
		report.Count(len(file), "byte", "bytes"), report.Count(tags.Subtunes, "subtune",
			"subtunes"), tags.Rate, tags.Flag))
	stub, err := binaries.Read(binaries.Stub)
	if err != nil {
		return
	}
	flags := ymxr.GetWord(prg, sndh.Header+sndh.StubFlagsAt)
	said.Say("the stub: " + report.Count(len(stub), "byte", "bytes") + ", patched")
	said.Row("the subtunes", fmt.Sprintf("%d", tags.Subtunes))
	if rows == 0 {
		said.Row("the rows to play", "0, until a key stops it")
	} else {
		said.Row("the rows to play", fmt.Sprintf("%d", rows))
	}
	said.Row("it plays from", from(tags, flags, asked))
	said.Row("the screen", "cleared before the banner")
	said.Say("the program: " + report.Count(len(prg), "byte", "bytes"))
}

// from is the clock the program plays from, and what named it: the
// caller, the file's clock tag, or its claims.
func from(tags sndh.Tagged, flags int, asked sndh.Asked) string {
	if flags&sndh.FlagVBL == 0 {
		timer := sndh.TimerFor(tags.Rate)
		at := fmt.Sprintf("Timer C, %s a second and a row every %d",
			report.Count(timer.Ticks, "tick", "ticks"), timer.Ticks/tags.Rate)
		if asked == sndh.AskedTimerC {
			return at + ", asked for"
		}
		return at
	}
	if asked == sndh.AskedVBL {
		return "the VBL, asked for"
	}
	if tags.Clock == sndh.ClockVBL {
		return "the VBL, the file's clock tag"
	}
	return "the VBL, the set claims Timer C"
}
