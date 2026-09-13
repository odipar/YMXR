// Package convert reads a YM dump into a tune file: the one call
// ym-to-ymxr makes, and what the check replays against the dump.
//
// Every conversion passes through the structure (doc/ymxs.md): the
// register streams become a YMXS tune, and the schema maps that onto the
// columns.
package convert

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/odipar/dtx/go/st4"

	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/schema"
	"github.com/odipar/ymxr/go/ym"
	"github.com/odipar/ymxr/go/ymxr"
)

// Unit is the unit a table packs at by default.
const Unit = 2

// Converted is a conversion: the tune file written, the dump's frame the
// tune repeats to, or its frame count where it plays once, the sources it
// named, and what the tool prints of it.
type Converted struct {
	Written ymxr.Written
	Repeat  int
	Sources *ymxr.Sources
	Said    string
	Effects int
}

// Of is the dump converted with the tool's flags, -kK, -mN, -rRR or -r,
// -copies[S], as the tool does it.
func Of(song ym.Song, args []string, said *report.Report) (Converted, error) {
	unit := Unit
	ring := ymxr.Ring
	repeat := -1
	once := false
	copies := false
	seconds := 0.0
	for _, flag := range args {
		var err error
		switch {
		case strings.HasPrefix(flag, "-k"):
			unit, err = strconv.Atoi(flag[2:])
		case strings.HasPrefix(flag, "-m"):
			ring, err = strconv.Atoi(flag[2:])
		case strings.HasPrefix(flag, "-copies"):
			// the packer's, spelled as DTX's dtx-write spells it: a
			// match beyond the ring copies from the column's literal
			// stream, which packs a small ring far smaller, and -copiesS
			// searches S seconds for a better parse
			copies = true
			if len(flag) > 7 {
				seconds, err = strconv.ParseFloat(flag[7:], 64)
			}
		case flag == "-r":
			once = true
		case strings.HasPrefix(flag, "-r"):
			repeat, err = strconv.Atoi(flag[2:])
		case flag == "-silent":
		default:
			return Converted{}, fmt.Errorf("not a flag of the tool: %s", flag)
		}
		if err != nil {
			return Converted{}, fmt.Errorf("not a number: %s", flag)
		}
	}
	switch {
	case once:
		repeat = song.Frames
	case repeat > song.Frames:
		return Converted{}, fmt.Errorf("the repeat row %d is past the dump's %d frames",
			repeat, song.Frames)
	case repeat < 0:
		repeat = song.Frames
		if song.LoopFrame < int64(song.Frames) {
			repeat = int(song.LoopFrame)
		} else {
			said.Note(fmt.Sprintf("the dump's loop frame %d is past its last frame: the"+
				" tune plays once", song.LoopFrame))
		}
	}
	flagsRead(said, args, unit, ring, repeat, once, copies, seconds)
	sources := ymxr.Drums(song.Drums, song.Attributes&ym.Drums4Bit != 0)
	made, err := schema.Of(ym.Of(song, sources, repeat, said))
	if err != nil {
		return Converted{}, err
	}
	found(said, sources, made.Columns.Effects)
	written, err := ymxr.WriteNamed(made.Columns, made.Sources, song.PlayerHz, unit, ring,
		st4.Packer{CopiesFlag: copies, Seconds: seconds}, said, song.Name)
	if err != nil {
		return Converted{}, err
	}
	repeats := "no row"
	if repeat < song.Frames {
		repeats = fmt.Sprintf("row %d", written.Repeat)
	}
	return Converted{Written: written, Repeat: repeat, Sources: sources,
		Effects: made.Columns.Effects,
		Said: fmt.Sprintf("%d frames at %d Hz, %d sources, effects %b, repeats at %s:"+
			" %d bytes", song.Frames, song.PlayerHz, sources.Count(),
			made.Columns.Effects, repeats, len(written.File))}, nil
}

// flagsRead says which flags the tool read and what each came to.
func flagsRead(said *report.Report, args []string, unit, ring, repeat int,
	once, copies bool, seconds float64) {
	if !said.Says() {
		return
	}
	read := "none, so the defaults below"
	if len(args) > 0 {
		read = strings.Join(args, " ")
	}
	said.Say("the flags: " + read)
	asked := ", asked for"
	if unit == Unit {
		asked = ", the default"
	}
	said.Row("-k, the unit", fmt.Sprintf("%d%s", unit, asked))
	asked = ", asked for"
	if ring == ymxr.Ring {
		asked = ", the default"
	}
	said.Row("-m, the ring", fmt.Sprintf("%d bytes%s", ring, asked))
	if once {
		said.Row("-r, the repeat row", "none, the tune plays once")
	} else {
		named := ", the dump's loop frame"
		for _, flag := range args {
			if strings.HasPrefix(flag, "-r") {
				named = ", asked for"
			}
		}
		said.Row("-r, the repeat row", fmt.Sprintf("%d%s", repeat, named))
	}
	switch {
	case !copies:
		said.Row("-copies", "no, the default: a match beyond the ring is not packed")
	case seconds == 0:
		said.Row("-copies", "yes, the opening passes alone")
	default:
		said.Row("-copies", fmt.Sprintf("yes, %s seconds of search, which packs another"+
			" parse a run", said2(seconds)))
	}
}

// said2 writes a count of seconds as the Java tree writes it.
func said2(seconds float64) string {
	return strconv.FormatFloat(seconds, 'f', -1, 64)
}

// found says what the sources and the effects the dump's frames came to.
func found(said *report.Report, sources *ymxr.Sources, effects int) {
	if !said.Says() {
		return
	}
	var rows, kinds [ymxr.Buzzer + 1]int
	for _, source := range sources.All() {
		kinds[source.Kind]++
		rows[source.Kind] += len(source.Rows)
	}
	named := " sources"
	if sources.Count() == 1 {
		named = " source"
	}
	said.Say(fmt.Sprintf("the effects: %d of 4 run, %d%s, at most %d",
		bits(effects), sources.Count(), named, ymxr.Most))
	for kind := 0; kind <= ymxr.Buzzer; kind++ {
		if kinds[kind] > 0 {
			row := " rows in all"
			if rows[kind] == 1 {
				row = " row in all"
			}
			said.Row(kindName(kind), fmt.Sprintf("%d, %d%s", kinds[kind], rows[kind], row))
		}
	}
}

// kindName names a source of a kind.
func kindName(kind int) string {
	switch kind {
	case ymxr.SID:
		return "square waves"
	case ymxr.Drum:
		return "digidrums"
	case ymxr.Sinus:
		return "sinus SIDs"
	case ymxr.Buzzer:
		return "buzzers"
	}
	return fmt.Sprintf("kind %d", kind)
}

func bits(of int) int {
	count := 0
	for of != 0 {
		count += of & 1
		of >>= 1
	}
	return count
}
