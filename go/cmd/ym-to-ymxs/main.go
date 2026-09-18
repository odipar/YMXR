// Command ym-to-ymxs reads a YM5!/YM6! register dump on standard input and
// writes the YMXS structure as JSON on standard output.
//
// The first stage of every conversion here (doc/ymxs.md). What comes out
// is the structure ym-to-ymxr converts through, so ym-to-ymxs | ymxs-to-ymxr writes
// the tune file that tool writes.
//
// -r produces a tune that plays once, and -rROW one that repeats to that
// row; without either, a tune repeats to the frame the dump marks.
package main

import (
	"fmt"
	"os"
	"strconv"
	"strings"

	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/tool"
	"github.com/odipar/ymxs/go/ymxs"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/ym"
	"github.com/odipar/ymxr/go/ymxr"
)

func main() {
	t, args := tool.Of("ym-to-ymxs", os.Args[1:], "-r")
	// The call is read before the input is, so a wrong call is an exit of
	// 2 and standard input is left unread.
	at, asked := Asked(t, args)
	said := report.Of(t.Reports())
	song, err := ym.Read(t.Bytes())
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	repeat := Repeat(t, at, asked, song, said)
	sources := ymxr.Drums(song.Drums, song.Attributes&ym.Drums4Bit != 0)
	tune := ym.Of(song, sources, repeat, said)
	t.Report(fmt.Sprintf("%s %q, %d rows at %d Hz, %d sources", song.Format,
		strings.TrimSpace(song.Name), song.Frames, song.PlayerHz, sources.Count()))
	for _, note := range said.Unsaid() {
		t.Note(note)
	}
	t.Write(text.Write(ymxs.NewMulti(tune)))
}

// Once is -r, a tune that plays once, in the place of a row.
const Once = -1

// Asked is the row the call asks the tune to repeat to: -rROW, Once for
// -r, and none where the call names neither. Read before the input, so a
// flag this does not read is an exit of 2 and the input is left unread.
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
// dump's loop frame, or its frame count where the tune plays once.
func Repeat(t *tool.Tool, at int, asked bool, song ym.Song, said *report.Report) int {
	if asked {
		if at == Once {
			return song.Frames
		}
		if at > song.Frames {
			t.Wrong(tool.Wrong, fmt.Sprintf("the repeat row %d is past the dump's %d"+
				" frames", at, song.Frames))
		}
		return at
	}
	if song.LoopFrame >= int64(song.Frames) {
		said.Note(fmt.Sprintf("the dump's loop frame %d is past its last frame: the tune"+
			" plays once", song.LoopFrame))
	}
	if song.LoopFrame < int64(song.Frames) {
		return int(song.LoopFrame)
	}
	return song.Frames
}
