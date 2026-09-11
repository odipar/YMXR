// Command ymx-to-ymxr reads a YMX file on standard input and writes a tune
// file on standard output. The file is decoded by YMX's reader, which the
// ymx package imports, as ymx-to-ymxs decodes it.
package main

import (
	"errors"
	"fmt"
	"os"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/schema"
	"github.com/odipar/ymxr/go/ymx"
	"github.com/odipar/ymxr/go/ymxr"
)

func main() {
	t, args := tool.Of("ymx-to-ymxr", os.Args[1:], "-k", "-m", "-copies")
	flags.Only(t, args, flags.PackingFlags)
	said := report.Of(t.Reports())
	packing := flags.PackingOf(t, args)
	read, err := ymx.StandardInput()
	if err != nil {
		var wrong *ymx.FormatException
		if errors.As(err, &wrong) {
			t.Wrong(tool.Wrong, err.Error())
		}
		t.Wrong(tool.Failed, err.Error())
	}
	said.Say(fmt.Sprintf("YMX!: %d frames at %d Hz, %d of them acting on a channel",
		read.Frames, read.Rate, ymx.Acting(read)))
	frames := ymx.Frames(read)
	if frames != read.Frames {
		said.Note(fmt.Sprintf("%d frame of YMX's padding comes off the end: a dump's rows"+
			" are what a tune has, and this conversion selects its unit separately",
			read.Frames-frames))
	}
	read.Frames = frames
	repeat := ymx.Repeat(read)
	song := ymx.Song(read, "")

	// Every conversion passes through the structure (doc/ymxs.md): the
	// register streams and the script become a YMXS tune, and the schema
	// maps that onto the columns.
	made, err := schema.Of(ymx.Of(read, song, repeat, said))
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	said.Row("the effects", fmt.Sprintf("%d of 4 run, %d sources",
		bits(made.Columns.Effects), made.Sources.Count()))
	written, err := ymxr.WriteWith(made.Columns, made.Sources, read.Rate,
		packing.Unit, packing.Ring, packing.Packer(), said)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	t.Report(fmt.Sprintf("%d rows at %d Hz, %d sources, effects %b: %d bytes",
		read.Frames, read.Rate, made.Sources.Count(), made.Columns.Effects,
		len(written.File)))
	for _, note := range said.Unsaid() {
		t.Note(note)
	}
	t.WriteBytes(written.File)
}

func bits(of int) int {
	count := 0
	for of != 0 {
		count += of & 1
		of >>= 1
	}
	return count
}
