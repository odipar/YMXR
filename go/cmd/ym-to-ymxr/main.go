// Command ym-to-ymxr reads a YM5!/YM6! dump on standard input and writes a
// tune file on standard output.
//
// Every conversion passes through the structure (doc/ymxs.md): the dump's
// frames become a YMXS tune, and the schema maps that onto the columns, so
// this writes the bytes ym-to-ymxs | ymxs-to-ymxr writes.
package main

import (
	"fmt"
	"os"
	"strings"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/convert"
	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/ym"
)

func main() {
	t, args := tool.Of("ym-to-ymxr", os.Args[1:], "-k", "-m", "-copies", "-r")
	flags.Only(t, args, flags.PackingFlags, flags.Rows)
	// The call is read before the input is, so a flag that is not a number
	// is an exit of 2 and standard input is left unread.
	flags.Numbers(t, args)
	said := report.Of(t.Reports())
	song, err := ym.Read(t.Bytes())
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	read(said, song)
	made, err := convert.Of(song, args, said)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	t.Report(made.Said)
	for _, note := range said.Unsaid() {
		t.Note(note)
	}
	t.WriteBytes(made.Written.File)
}

// read says what the dump was.
func read(said *report.Report, song ym.Song) {
	if !said.Says() {
		return
	}
	title := "(untitled)"
	if strings.TrimSpace(song.Name) != "" {
		title = strings.TrimSpace(song.Name)
	}
	by := ""
	if strings.TrimSpace(song.Author) != "" {
		by = " by " + strings.TrimSpace(song.Author)
	}
	said.Say(song.Format + ": " + title + by)
	rate := song.PlayerHz
	if rate < 1 {
		rate = 1
	}
	seconds := song.Frames / rate
	how := ", a record a frame"
	if song.Interleaved {
		how = ", interleaved"
	}
	said.Row("frames", fmt.Sprintf("%d at %d Hz (%d:%02d)%s", song.Frames, song.PlayerHz,
		seconds/60, seconds%60, how))
	said.Row("the loop frame", fmt.Sprintf("%d of the dump's header", song.LoopFrame))
	if len(song.Drums) > 0 {
		bytes := 0
		for _, drum := range song.Drums {
			bytes += len(drum)
		}
		said.Row("digidrums", fmt.Sprintf("%d of %d bytes in all", len(song.Drums), bytes))
	}
}
