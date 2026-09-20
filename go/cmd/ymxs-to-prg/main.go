// Command ymxs-to-prg reads a YMXS structure as JSON on standard input and
// writes a TOS program on standard output.
//
// The SNDH file ymxs-to-sndh makes, with the program stub in front of it
// (doc/BINARIES.md 4): a program that claims the machine under Supexec,
// plays the tune from the clock the file's tag names, Timer C or the VBL,
// switches subtunes on the keys 1 to 9, and releases the machine on SPACE
// or ESC.
//
// -rROWS stops the run after that many rows, 0 to play on until a key
// stops it; every other flag is ymxs-to-sndh's.
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
		rowed = fmt.Sprintf("%d rows", rows)
	}
	said.Row("the program", fmt.Sprintf("%d bytes, %s", len(prg), rowed))
	t.WriteBytes(prg)
	for _, note := range said.Unsaid() {
		t.Note(note)
	}
}
