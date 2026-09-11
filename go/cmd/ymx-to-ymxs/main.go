// Command ymx-to-ymxs reads a YMX file on standard input and writes the
// YMXS structure as JSON on standard output.
//
// The first stage of a YMX conversion (doc/ymxs.md), so
// ymx-to-ymxs | ymxs-to-ymxr writes the tune file ymx-to-ymxr writes.
package main

import (
	"errors"
	"fmt"
	"os"
	"strconv"

	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/tool"
	"github.com/odipar/ymxs/go/ymxs"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/ymx"
)

func main() {
	t, args := tool.Of("ymx-to-ymxs", os.Args[1:], "-r")
	// The call is read before the input is, as ym-to-ymxs reads it.
	at, asked := Asked(t, args)
	said := report.Of(t.Reports())
	read, err := ymx.StandardInput()
	if err != nil {
		var wrong *ymx.FormatException
		if errors.As(err, &wrong) {
			t.Wrong(tool.Wrong, err.Error())
		}
		t.Wrong(tool.Failed, err.Error())
	}
	frames := ymx.Frames(read)
	if frames != read.Frames {
		said.Note(fmt.Sprintf("%d frame of YMX's padding comes off the end: a dump's rows"+
			" are the tune's rows", read.Frames-frames))
	}
	read.Frames = frames
	repeat := Repeat(at, asked, read)
	song := ymx.Song(read, "")
	t.Report(fmt.Sprintf("YMX!: %d rows at %d Hz", read.Frames, read.Rate))
	tune := ymx.Of(read, song, repeat, said)
	for _, note := range said.Unsaid() {
		t.Note(note)
	}
	t.Write(text.Write(ymxs.NewMulti(tune)))
}

// Once is -r, a tune that plays once, in the place of a row.
const Once = -1

// Asked is the row the call asks the tune to repeat to.
func Asked(t *tool.Tool, args []string) (int, bool) {
	flags.Only(t, args, []string{"-r"})
	for _, flag := range args {
		if flag == "-r" {
			return Once, true
		}
		at, err := strconv.Atoi(flag[2:])
		if err != nil {
			t.Usage("not a row number: " + flag)
		}
		return at, true
	}
	return 0, false
}

// Repeat is the row the tune repeats to: the row the call names, the
// file's loop frame, or its frame count where the tune plays once.
func Repeat(at int, asked bool, read ymx.Dumped) int {
	if asked {
		if at == Once {
			return read.Frames
		}
		return at
	}
	return ymx.Repeat(read)
}
