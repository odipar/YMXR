// Package check replays a dump against the tune it converts to: the tune
// file's table stepped frame by frame by the reader model, every frame's
// registers against the dump's, a volume register an effect owns checked
// against an unset column instead, and every effect's source, target, rate
// and count checked against what the dump flags.
package check

import (
	"fmt"

	"github.com/odipar/ymxr/go/convert"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/trace"
	"github.com/odipar/ymxr/go/ym"
	"github.com/odipar/ymxr/go/ymxr"
)

// Most is the wrong frames listed for one tune, at most.
const Most = 20

// Of is what is wrong with the dump's conversion at the tool's flags, or
// an empty list where every frame replays to the dump: the tune's rows are
// stepped through one pass and the loop once, as the kit's record runs
// (SPEC.md 7), each row checked against its frame of the dump.
func Of(song ym.Song, args []string) []string {
	var wrong []string
	made, err := convert.Of(song, args, report.Quiet())
	if err != nil {
		return []string{err.Error()}
	}
	sources := made.Sources
	tune, err := ymxr.Read(made.Written.File)
	if err != nil {
		return []string{err.Error()}
	}
	if tune.FrameRate != song.PlayerHz {
		wrong = append(wrong, fmt.Sprintf("the frame rate is %d, not %d", tune.FrameRate,
			song.PlayerHz))
	}
	// the frame of the dump each row of the table answers to (SPEC.md 6,
	// rule 6): -1 for a row that sets no column, and the rows of a loop
	// written again answer to its frames again
	frames := made.Frames
	rows := len(frames)
	if tune.Table.Rows() != rows {
		wrong = append(wrong, fmt.Sprintf("the table has %d rows, not %d",
			tune.Table.Rows(), rows))
	}
	if tune.Table.Repeat() != made.Written.Repeat {
		wrong = append(wrong, fmt.Sprintf("the table repeats at %d, not %d",
			tune.Table.Repeat(), made.Written.Repeat))
	}
	if len(tune.Sources) != sources.Count() {
		wrong = append(wrong, fmt.Sprintf("the file has %d sources, not %d",
			len(tune.Sources), sources.Count()))
	}
	for i := 0; i < min(sources.Count(), len(tune.Sources)); i++ {
		s := tune.Sources[i]
		values := s.Column(0)
		want := sources.At(i + 1).Rows()
		if s.Columns() != 1 || len(values) != want {
			wrong = append(wrong, fmt.Sprintf("source %d is %d columns of %d rows, not"+
				" one of %d", i+1, s.Columns(), len(values), want))
			continue
		}
		for r, value := range values {
			marker := value&0x80 != 0
			if marker != (r == len(values)-1) {
				said := " is not the marker"
				if marker {
					said = " is a marker"
				}
				wrong = append(wrong, fmt.Sprintf("source %d row %d%s", i+1, r, said))
			}
		}
	}
	if len(wrong) > 0 {
		return wrong
	}
	model := trace.Of(tune.Table)
	drumEnd := [2]int{-1, -1}
	calls := trace.Calls(tune.Table)
	quiet := report.Quiet()
	for call := 0; call < calls && len(wrong) < Most; call++ {
		r := model.Row()
		if r == rows {
			break // a tune that plays once has played
		}
		model.Step()
		f := frames[r]
		if f < 0 {
			// a row that sets no column: every register keeps its value, and the
			// effects run on through it
			for c := 0; c < 13; c++ {
				if model.Written[c] >= 0 {
					wrong = append(wrong, fmt.Sprintf("%d: a row that sets no column wrote R%d",
						r, c))
				}
			}
			if model.EnvelopeWritten {
				wrong = append(wrong, fmt.Sprintf("%d: a row that sets no column wrote R13", r))
			}
			continue
		}
		dump := registers(song, f)
		slots := ym.Slots(song, f)
		owned := 0
		mixer := 0
		for i := 0; i < 2; i++ {
			e := model.Effect[i]
			slot := slots[i]
			// A drum on a voice preempts a SID there: while the other
			// effect runs a drum on this slot's voice, the SID the dump
			// flags runs no source.
			o := model.Effect[1-i]
			preempted := slot.On() && slot.Kind == ymxr.SID && o.Source != 0 &&
				sources.At(o.Source).Kind == ymxr.Drum && o.Target == slot.Target
			number := 0
			if slot.On() {
				number = sources.Number(slot.Kind, slot.Data, &quiet.Sinus,
					&quiet.MissingDrums, &quiet.Overflow)
			}
			switch {
			case preempted:
				if e.Source != 0 {
					wrong = append(wrong, fmt.Sprintf("%d: effect %d runs source %d under"+
						" a drum on its voice", f, i, e.Source))
				}
			case slot.On() && number != 0:
				if e.Source == 0 {
					wrong = append(wrong, fmt.Sprintf("%d: effect %d runs no source where"+
						" the dump flags kind %d", f, i, slot.Kind))
				} else {
					s := sources.At(e.Source)
					if e.Source != number || e.Target != slot.Target ||
						e.Select != slot.Select || e.Count != slot.Count {
						flagged := sources.At(number)
						wrong = append(wrong, fmt.Sprintf("%d: effect %d runs source %d"+
							" (kind %d value %d) on R%d at %d/%d, not source %d (kind %d"+
							" value %d) on R%d at %d/%d", f, i, e.Source, s.Kind, s.Data,
							e.Target, e.Select, e.Count, number, flagged.Kind,
							flagged.Data, slot.Target, slot.Select, slot.Count))
					}
					if slot.Kind == ymxr.Drum {
						if !e.Started {
							wrong = append(wrong, fmt.Sprintf("%d: the drum is not started",
								f))
						}
						drumEnd[i] = r + ymxr.Duration(s.Rows(), slot.Select,
							slot.Count, song.PlayerHz)
					}
				}
			case e.Source != 0:
				s := sources.At(e.Source)
				drum := s.Kind == ymxr.Drum
				if !drum || r >= drumEnd[i] {
					wrong = append(wrong, fmt.Sprintf("%d: effect %d runs source %d where"+
						" the dump flags no effect", f, i, e.Source))
				}
			}
			if e.Source != 0 && e.Target < 13 {
				owned |= 1 << e.Target
				if sources.At(e.Source).Kind == ymxr.Drum {
					mixer |= 0x09 << (e.Target - 8)
				}
			}
		}
		for c := 0; c < 13; c++ {
			want := dump[c]
			if c == 7 {
				want = dump[7] | mixer
			}
			if owned&(1<<c) != 0 {
				// While an effect runs on a volume register no row sets
				// that column (SPEC.md 1.3, section 6 rule 1).
				if c >= 8 && c <= 10 && model.Written[c] >= 0 {
					wrong = append(wrong, fmt.Sprintf("%d: R%d's column is set to %d while"+
						" an effect runs on it", f, c, model.Written[c]))
				}
			} else if model.Registers[c] != want {
				wrong = append(wrong, fmt.Sprintf("%d: R%d is %d, not %d", f, c,
					model.Registers[c], want))
			}
		}
		if model.EnvelopeWritten != (dump[13] >= 0) ||
			(dump[13] >= 0 && model.Registers[13] != dump[13]) {
			was := "not written"
			if model.EnvelopeWritten {
				was = fmt.Sprintf("written %d", model.Registers[13])
			}
			does := "does not write"
			if dump[13] >= 0 {
				does = fmt.Sprintf("writes %d", dump[13])
			}
			wrong = append(wrong, fmt.Sprintf("%d: R13 %s, the dump %s", f, was, does))
		}
	}
	return wrong
}

// registers is R0 to R13 of one frame with the dump's flag bits masked
// off, and -1 for R13 where the dump does not write it.
func registers(song ym.Song, frame int) [14]int {
	var out [14]int
	for i := 0; i < 14; i++ {
		out[i] = int(song.Values[i][frame]) & ymxr.Mask[i]
	}
	if song.Values[13][frame] == 0xFF {
		out[13] = -1
	}
	return out
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
