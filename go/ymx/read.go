package ymx

import (
	"fmt"
	"strconv"

	"github.com/odipar/ymxs/ymxs"

	"github.com/odipar/ymxr/report"
	"github.com/odipar/ymxr/schema"
	"github.com/odipar/ymxr/ym"
	"github.com/odipar/ymxr/ymxr"
)

// noVoice is voice 3, which is no voice: three opcodes read it as a second
// form.
const noVoice = 3

// The opcodes an action byte's top three bits select.
const (
	resume         = 0
	hold           = 1
	release        = 2
	startToggle    = 3
	retune         = 4
	startRetrigger = 5
	startPCM       = 6
	startPCMEmpt   = 7
)

// Frames is the frames of a dumped file, its packing's padding off the
// end.
//
// YMX fits a tune to its unit by padding it, where the conversion here
// drops to a unit of 1 instead, so a dump of an odd frame count is one
// frame longer through YMX than through the dump. The dump is what a
// tune's rows are, so the padding comes off and this format reckons the
// unit separately.
//
// A pad at YMX's unit of 2 is one frame, it repeats the frame before it
// and it acts on no channel, and a padded count is even. One frame comes
// off where all three apply.
func Frames(read Dumped) int {
	last := read.Frames - 1
	if read.Frames%2 != 0 || last <= read.LoopFrame {
		return read.Frames
	}
	if read.Streams[StreamM][last] != 0 {
		return read.Frames
	}
	for r := 0; r < 14; r++ {
		if read.Streams[r][last] != read.Streams[r][last-1] {
			return read.Frames
		}
	}
	return last
}

// built is the sources a script names, one for each distinct table: two
// channels running one shape run one source (SPEC.md 2.2).
type built struct {
	made map[string]ymxs.Source
}

func (b *built) of(name string, values []int, repeat int) ymxs.Source {
	key := fmt.Sprintf("%v/%d", values, repeat)
	if known, met := b.made[key]; met {
		return known
	}
	source := ymxs.OnceSource(name, values)
	if repeat < len(values) {
		source = ymxs.RepeatingSource(name, values, repeat)
	}
	b.made[key] = source
	return source
}

// Of is the file as a tune, repeating to that row.
func Of(read Dumped, song ym.Song, repeat int, said *report.Report) ymxs.Tune {
	registers := ymxs.Rows(ym.Of(song, ymxr.Passed(nil), repeat, said))
	frames := read.Frames
	owned := ownedBy(read)
	made := &built{made: map[string]ymxs.Source{}}
	effects := make([]map[ymxs.Timer]ymxs.Effect, frames)
	for f := range effects {
		effects[f] = map[ymxs.Timer]ymxs.Effect{}
	}
	used := script(read, made, effects, said)
	rows := make([]ymxs.Row, frames)
	for f := 0; f < frames; f++ {
		sets := map[ymxs.Register]int{}
		for register, value := range registers[f].Registers {
			sets[register] = value
		}
		// A voice a timer owns is one the frame leaves unwritten (YMX,
		// SPEC.md 2.1), which is what SPEC.md 6 rule 1 requires of a row
		// here. M's bits 7 to 5 are read only where bit 4 is set.
		for voice := 0; voice < 3; voice++ {
			if owned[f]&(1<<voice) != 0 {
				delete(sets, ymxs.Registers[8+voice])
			}
		}
		rows[f] = ymxs.Row{Registers: sets, Effects: effects[f]}
	}
	// The row a tune repeats to stops every effect it does not start, so
	// the wrap resumes from a known setting, as a dump's conversion does
	// it. Only the effects the tune runs: a row sets no column of one it
	// does not run (section 6 rule 2), so this waits until the walk says
	// which run.
	if repeat < frames {
		for c := 0; c < 4; c++ {
			timer := schema.Timer(c)
			if _, on := rows[repeat].Effects[timer]; used&(1<<c) != 0 && !on {
				rows[repeat].Effects[timer] = ymxs.Stop{}
			}
		}
	}
	table := ymxs.Once(rows)
	if repeat < frames {
		table = ymxs.Repeating(rows, repeat)
	}
	return ymxs.Tune{Title: song.Name, Writer: "ymx-to-ymxs", Rate: read.Rate, Table: table}
}

// ownedBy is the voices YMX's master stream reserves for a timer, a frame
// each: bits 7 to 5 of M, read where its bit 4 is set.
func ownedBy(read Dumped) []int {
	out := make([]int, read.Frames)
	skip := 0
	for f := 0; f < read.Frames; f++ {
		master := int(read.Streams[StreamM][f])
		if master&0x10 != 0 {
			skip = (master >> 5) & 7
		}
		out[f] = skip
	}
	return out
}

// script is the script's four channels as the four effects, and the
// effects the tune ever starts.
func script(read Dumped, made *built, effects []map[ymxs.Timer]ymxs.Effect,
	said *report.Report) int {
	used := 0
	var target [4]ymxs.Target
	// The select and the count each channel runs at: an opcode that
	// repatches a parameter leaves the rest of the rate as it is, and a
	// start writes the whole rate (SPEC.md 1.9).
	var selects, count [4]int
	// Whether each channel's timer runs. Bit 6 affects a running timer and
	// a stopped one starts on the select with it or without (1.9), so a
	// start sets the reset where the timer is stopped, as section 6 rule 5
	// requires, and a start over a running stream leaves it clear rather
	// than restarting the period.
	var running [4]bool
	var left []string
	kept := 0
	for f := 0; f < read.Frames; f++ {
		master := int(read.Streams[StreamM][f])
		here := effects[f]
		preempted := 0
		for c := 0; c < 4; c++ {
			if master&(1<<c) == 0 {
				continue
			}
			timer := schema.Timer(c)
			action := int(read.Streams[StreamA0+2*c][f])
			written := int(read.Streams[StreamA0+2*c+1][f])
			opcode := action >> 5
			voice := (action >> 3) & 3
			low := action & 7
			// A count byte of 0 is the MFP's 256, which the count column
			// does not reach (SPEC.md 1.9), and a select of 0 stops a
			// timer. Either way the rate the effect runs at does not move,
			// which is what the columns encoded before this conversion
			// read the structure.
			if written == 0 && count[c] != 0 {
				kept++
			}
			rate := written
			if written == 0 {
				rate = count[c]
			}
			if low == 0 && selects[c] != 0 {
				kept++
			}
			at := low
			if low == 0 {
				at = selects[c]
			}
			volume := 0
			if voice != noVoice {
				volume = int(read.Streams[8+voice][f]) & 0x1F
			}
			switch opcode {
			case startToggle:
				on := ymxs.Setting(ymxs.Registers[8+voice])
				target[c] = on
				here[timer] = ymxs.Start{Target: on,
					Source:     made.of("square "+strconv.Itoa(volume), toggle(volume), 0),
					Prescaler:  prescaler(at),
					Count:      rate,
					TimerReset: !running[c], PlaceReset: true}
				selects[c], count[c], running[c] = at, rate, true
				used |= 1 << c
			case startRetrigger:
				shape := (int(read.Streams[StreamX][f]) >> 4) & 0x0F
				on := ymxs.Setting(ymxs.R13)
				target[c] = on
				here[timer] = ymxs.Start{Target: on,
					Source:     made.of("buzzer "+strconv.Itoa(shape), retrigger(shape), 0),
					Prescaler:  prescaler(at),
					Count:      rate,
					TimerReset: !running[c], PlaceReset: true}
				selects[c], count[c], running[c] = at, rate, true
				used |= 1 << c
			case startPCM, startPCMEmpt:
				if volume >= len(read.Samples) {
					left = append(left, fmt.Sprintf("frame %d starts sample %d, which the"+
						" file does not carry", f, volume))
					break
				}
				loop := read.Loops[volume]
				rows := values(read.Samples[volume])
				repeat := loop
				if loop == 0xFFFF {
					repeat = len(rows)
				}
				on := ymxs.Setting(ymxs.Registers[8+voice])
				target[c] = on
				here[timer] = ymxs.Start{Target: on,
					Source:     made.of("drum "+strconv.Itoa(volume), rows, repeat),
					Prescaler:  prescaler(at),
					Count:      rate,
					TimerReset: !running[c], PlaceReset: true}
				selects[c], count[c], running[c] = at, rate, true
				used |= 1 << c
				if opcode == startPCMEmpt {
					preempted |= (int(read.Streams[StreamX][f]) & 0x0F) &^ (1 << c)
				}
			case release:
				here[timer] = ymxs.Stop{}
				running[c] = false
			case retune:
				// A new rate on a running stream, its place kept.
				// Addressed to a voice it repatches the volume from the
				// voice's byte first (YMX, SPEC.md 3.1), which is a source
				// of the row count the effect already runs, so rule 5 lets
				// the row leave the place alone: the toggle keeps its
				// phase and the half it stands in.
				on := target[c]
				if voice != noVoice && on != nil && isVolume(on) {
					here[timer] = ymxs.Start{Target: on,
						Source:    made.of("square "+strconv.Itoa(volume), toggle(volume), 0),
						Prescaler: prescaler(at), Count: rate}
				} else {
					here[timer] = ymxs.Retune{Prescaler: prescaler(at), Count: rate}
				}
				selects[c], count[c] = at, rate
			case hold:
				// HOLD's low bits are flags and not a prescaler (YMX,
				// SPEC.md 2.4): 1 reloads the count, 2 the toggle's
				// volume, 4 the retrigger's shape.
				if low&1 != 0 && written != 0 {
					count[c] = written
				}
				on := target[c]
				var source ymxs.Source
				if low&2 != 0 && on != nil && isVolume(on) {
					source = made.of("square "+strconv.Itoa(volume), toggle(volume), 0)
				} else if low&4 != 0 {
					shape := (int(read.Streams[StreamX][f]) >> 4) & 0x0F
					source = made.of("buzzer "+strconv.Itoa(shape), retrigger(shape), 0)
				}
				if source != nil && on != nil {
					// The parameter is repatched and the stream runs on,
					// so the place stands: the source has the row count
					// the effect already runs and rule 5 lets the row
					// leave the place alone.
					here[timer] = ymxs.Start{Target: on, Source: source,
						Prescaler: prescaler(selects[c]), Count: count[c]}
				} else if low&1 != 0 {
					here[timer] = ymxs.Retune{Prescaler: prescaler(selects[c]),
						Count: count[c]}
				}
			case resume:
				left = append(left, fmt.Sprintf("frame %d resumes channel %d, which this"+
					" version does not carry", f, c))
			default:
				left = append(left, fmt.Sprintf("frame %d runs opcode %d", f, opcode))
			}
		}
		// A preempt stops other channels (YMX, SPEC.md 3, opcode 7), after
		// the frame's channels have acted.
		for other := 0; other < 4; other++ {
			if preempted&(1<<other) != 0 {
				here[schema.Timer(other)] = ymxs.Stop{}
				running[other] = false
			}
		}
	}
	if kept > 0 {
		said.Note(fmt.Sprintf("%d rows write a count or a select of 0, which the columns"+
			" read as the rate the effect already runs at", kept))
	}
	most := len(left)
	if most > 3 {
		most = 3
	}
	for _, one := range left[:most] {
		said.Note(one)
	}
	if len(left) > 3 {
		said.Note(fmt.Sprintf("%d more the script does that this version leaves behind",
			len(left)-3))
	}
	return used
}

func prescaler(at int) ymxs.Prescaler {
	by, err := ymxs.PrescalerBy(ymxr.Prescaler[at])
	if err != nil {
		panic(err)
	}
	return by
}

// isVolume is whether a target writes a voice's volume register.
func isVolume(target ymxs.Target) bool {
	at := ymxs.TargetNumber(target)
	return at >= 8 && at <= 10
}

// toggle is a toggle stream's two rows: the loud half, then the silent
// one.
func toggle(level int) []int {
	return []int{level & 0x1F, 0}
}

// retrigger is a retrigger stream's one row, the shape.
func retrigger(shape int) []int {
	return []int{shape & 0x0F}
}

// values is a sample's bytes as source values: YMX writes its end marker
// in bit 7, which is this format's marker (SPEC.md 3.2), so the sample
// ends at the first byte that carries one and the values are the seven
// bits under it.
func values(rows []byte) []int {
	out := make([]int, 0, len(rows))
	for _, row := range rows {
		out = append(out, int(row)&0x7F)
		if row&0x80 != 0 {
			break
		}
	}
	return out
}
