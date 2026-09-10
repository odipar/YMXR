package ym

import (
	"strconv"
	"strings"

	"github.com/odipar/ymxs/ymxs"

	"github.com/odipar/ymxr/report"
	"github.com/odipar/ymxr/schema"
	"github.com/odipar/ymxr/ymxr"
)

// A YM5!/YM6! dump read into a YMXS tune: one row a frame, and a source
// for each distinct sound the dump's effect slots produce. The schema maps
// what this produces onto the columns.
//
// A dump contains every register of every frame, so a row here sets a
// register where the dump's value changed. A register an effect is running
// on belongs to that effect, and no row sets it before the row that stops
// it, as rule 1 of SPEC.md 6 requires.
//
// The two slots run on Timers A and D (SPEC.md 2.3). A slot sounding a
// square wave becomes a source of a level and a silence; one restarting
// the envelope, a source of the shape; one playing a recording, a source
// of the sample's levels and a closing row at mid-scale. A recording owns
// the voice's volume for its duration at its rate, and silences that
// voice's tone and noise meanwhile.
//
// A recording on a voice excludes a square wave on it: the dump's player
// runs one effect a voice, and the recording is that effect.
//
// The row a tune repeats to sets every register except those an effect is
// running on, and starts every effect that ran up to it or runs into the
// wrap, so the wrap resumes from the chip as the rows left it.

// Slot is one of the two effect slots in a dump's frame, read as the
// schema's effects: a kind, the target its ticks write, the value its
// source is built from, and the timer's select and count (SPEC.md 1.8,
// 1.9).
type Slot struct {
	Kind   int
	Voice  int
	Target int
	Data   int
	Select int
	Count  int
}

// On is whether a player runs this slot.
func (s Slot) On() bool {
	return s.Kind != 0
}

// where is the registers a slot reads: its code, its select and its count.
var where = [2][3]int{{1, 6, 14}, {3, 8, 15}}

// Slots is the two slots of one frame. YM6 files each slot's kind in its
// code's bits 7 and 6; YM5 has no kind bits, its first slot being a SID
// voice and its second a digidrum. A slot whose select or count is 0 is
// empty, as the reference player reads it.
func Slots(song Song, frame int) [2]Slot {
	ym6 := song.Format == "YM6!"
	r := song.Values
	var out [2]Slot
	for slot := 0; slot < 2; slot++ {
		code := int(r[where[slot][0]][frame]) & 0xF0
		voice := ((code >> 4) & 3) - 1
		selects := int(r[where[slot][1]][frame]) >> 5
		count := int(r[where[slot][2]][frame])
		if voice < 0 || selects == 0 || count == 0 {
			continue
		}
		kind := ymxr.Drum
		if ym6 {
			kind = (code >> 6) + 1
		} else if slot == 0 {
			kind = ymxr.SID
		}
		target := 13
		if kind != ymxr.Buzzer {
			target = 8 + voice
		}
		out[slot] = Slot{Kind: kind, Voice: voice, Target: target,
			Data: int(r[8+voice][frame]) & 0x1F, Select: selects, Count: count}
	}
	return out
}

// registers is R0 to R13 of one frame with the dump's flag bits masked
// off, and -1 for R13 where the dump does not write it.
func registers(song Song, frame int) [14]int {
	r := song.Values
	var out [14]int
	for i := 0; i < 14; i++ {
		out[i] = int(r[i][frame]) & ymxr.Mask[i]
	}
	if r[13][frame] == 0xFF {
		out[13] = -1
	}
	return out
}

// Of is the dump as a tune, repeating to that row, its row count where it
// plays once. sources numbers the sources as it meets them, and the report
// counts what was dropped.
func Of(song Song, sources *ymxr.Sources, repeat int, said *report.Report) ymxs.Tune {
	frames := song.Frames
	var rows []ymxs.Row
	made := map[int]ymxs.Source{}
	var wrote [14]int
	for i := range wrote {
		wrote[i] = -1
	}
	var running [2]Slot
	runningNumber := [2]int{0, 0}
	selectHeld := [2]int{0, 0}
	countHeld := [2]int{0, 0}
	drumEnd := [2]int{-1, -1}
	stopAtRepeat := [2]bool{false, false}
	// What each effect last ran, whether or not it runs now: a row that
	// stops an effect moves no place, so a square that starts again reads
	// the row it left off at.
	lastKind := [2]int{0, 0}
	lastTarget := [2]int{-1, -1}
	for f := 0; f < frames; f++ {
		// The row the tune repeats to sets every register but R13 and the
		// ones an effect owns there, and every effect, so the wrap resumes
		// from a known setting: row 0 too, where the tune repeats to it.
		keyframe := f == repeat
		if keyframe {
			for i := range wrote {
				wrote[i] = -1
			}
			lastKind = [2]int{0, 0}
			lastTarget = [2]int{-1, -1}
			for i := 0; i < 2; i++ {
				stopAtRepeat[i] = running[i].On()
			}
		}
		reg := registers(song, f)
		slot := Slots(song, f)
		var number [2]int
		// The drums' numbers first: a drum on a voice preempts a SID
		// there, so a SID the dump flags on a voice where the other slot's
		// drum runs, or starts in this frame, is not started. At the
		// repeat row a running drum is cut and the SID starts.
		for i := 0; i < 2; i++ {
			if slot[i].On() && slot[i].Kind == ymxr.Drum {
				number[i] = sources.Number(slot[i].Kind, slot[i].Data,
					&said.Sinus, &said.MissingDrums, &said.Overflow)
				if number[i] == 0 {
					slot[i] = Slot{}
				}
			}
		}
		for i := 0; i < 2; i++ {
			if !slot[i].On() || slot[i].Kind == ymxr.Drum {
				continue
			}
			other := 1 - i
			drumStarts := slot[other].On() && slot[other].Kind == ymxr.Drum &&
				slot[other].Voice == slot[i].Voice
			// a drum the other slot replaces in this frame, by another
			// kind or another voice, runs no longer
			replaced := slot[other].On() && !drumStarts
			drumRuns := running[other].Kind == ymxr.Drum &&
				running[other].Voice == slot[i].Voice && f < drumEnd[other] &&
				!keyframe && !replaced
			if slot[i].Kind == ymxr.SID && (drumRuns || drumStarts) {
				slot[i] = Slot{}
				said.Preempted++
				continue
			}
			number[i] = sources.Number(slot[i].Kind, slot[i].Data,
				&said.Sinus, &said.MissingDrums, &said.Overflow)
			if number[i] == 0 {
				slot[i] = Slot{}
			}
		}
		effects := map[ymxs.Timer]ymxs.Effect{}
		owned := 0
		for i := 0; i < 2; i++ {
			timer := schema.Timer(i)
			drum := running[i].Kind == ymxr.Drum
			if !slot[i].On() {
				if keyframe && drum && f < drumEnd[i] {
					said.CutAtRepeat++
				}
				if drum && f < drumEnd[i] && !keyframe {
					// the drum plays on: the dump flags only its trigger
				} else if running[i].On() || keyframe {
					effects[timer] = ymxs.Stop{}
					running[i] = Slot{}
				}
			} else {
				starting := keyframe || slot[i].Kind == ymxr.Drum ||
					running[i].Kind != slot[i].Kind ||
					running[i].Target != slot[i].Target ||
					runningNumber[i] != number[i]
				if starting {
					// Bit 6 stops the timer, writes the count and starts
					// it, so the timer runs a whole period and loses what
					// it had run of the last one. Section 6 rule 5 sets the
					// bit where the timer is stopped; where the timer runs,
					// the count this row writes loads when the running
					// count reaches zero, which moves the pitch without a
					// break (1.9). A timer is stopped where no effect runs
					// on it, the row that stopped the effect having written
					// select 0, and where a digidrum's source has run out:
					// that source does not repeat, so its last tick stops
					// the timer (section 5). A SID voice's source and a
					// sync buzzer's repeat, and run until a row stops them.
					// The keyframe sets the bit in either case.
					stopped := keyframe || !running[i].On() ||
						running[i].Kind == ymxr.Drum && f >= drumEnd[i]
					// Where a square replaces a square on the same target
					// the row leaves the place alone: the row number in the
					// place counts into the new source's rows (1.9). Every
					// level is a separate source, so a square whose level
					// moves starts one each time, and its two ticks either
					// side of the start fall a whole period apart. A drum
					// struck again reads its first row, so it moves the
					// place as any other start does.
					unmoved := !keyframe && slot[i].Kind == ymxr.SID &&
						lastKind[i] == ymxr.SID && lastTarget[i] == slot[i].Target
					register, err := ymxs.RegisterAt(slot[i].Target)
					if err != nil {
						panic(err)
					}
					prescaler, err := ymxs.PrescalerBy(ymxr.Prescaler[slot[i].Select])
					if err != nil {
						panic(err)
					}
					effects[timer] = ymxs.Start{Target: ymxs.Setting(register),
						Source:     source(made, sources, number[i]),
						Prescaler:  prescaler,
						Count:      slot[i].Count,
						TimerReset: stopped, PlaceReset: !unmoved}
					running[i] = slot[i]
					runningNumber[i] = number[i]
					lastKind[i] = slot[i].Kind
					lastTarget[i] = slot[i].Target
					if slot[i].Kind == ymxr.Drum {
						drumEnd[i] = f + ymxr.Duration(len(sources.At(number[i]).Rows),
							slot[i].Select, slot[i].Count, song.PlayerHz)
					}
				} else if slot[i].Select != selectHeld[i] ||
					slot[i].Count != countHeld[i] {
					prescaler, err := ymxs.PrescalerBy(ymxr.Prescaler[slot[i].Select])
					if err != nil {
						panic(err)
					}
					effects[timer] = ymxs.Retune{Prescaler: prescaler,
						Count: slot[i].Count}
				}
				selectHeld[i] = slot[i].Select
				countHeld[i] = slot[i].Count
			}
			if running[i].Kind == ymxr.SID || running[i].Kind == ymxr.Drum {
				owned |= 1 << running[i].Target
			}
			if running[i].Kind == ymxr.Drum {
				reg[7] |= 0x09 << running[i].Voice
			}
		}
		values := map[ymxs.Register]int{}
		for c := 0; c < 13; c++ {
			register := ymxs.Registers[c]
			if ymxr.BesideColumn[c] >= 0 {
				if reg[c] != wrote[c] {
					values[register] = reg[c]
					wrote[c] = reg[c]
				}
			} else if owned&(1<<c) != 0 {
				wrote[c] = -1 // the effect's register,
			} else if reg[c] != wrote[c] { // and no row's
				values[register] = reg[c]
				wrote[c] = reg[c]
			}
		}
		if reg[13] >= 0 {
			values[ymxs.R13] = reg[13]
		}
		rows = append(rows, ymxs.Row{Registers: values, Effects: effects})
	}
	// The keyframe's stop is for an effect that ran up to the repeat row,
	// or runs into the wrap, so that the wrap resumes from a known
	// setting; the row leaves an effect that did neither, or that the tune
	// never runs, alone.
	if repeat < frames {
		for i := 0; i < 2; i++ {
			timer := schema.Timer(i)
			if _, stops := rows[repeat].Effects[timer].(ymxs.Stop); stops &&
				!stopAtRepeat[i] && !running[i].On() {
				delete(rows[repeat].Effects, timer)
			}
		}
	}
	table := ymxs.Once(rows)
	if repeat < frames {
		table = ymxs.Repeating(rows, repeat)
	}
	return ymxs.Tune{Title: strings.TrimSpace(song.Name),
		Composer: strings.TrimSpace(song.Author), Writer: "ym-to-ymxs",
		Rate: song.PlayerHz, Table: table}
}

// source is the source of that number, built on first use: its rows
// without the marker bit 7, which is this format's and not the
// structure's (SPEC.md 3.2).
func source(made map[int]ymxs.Source, sources *ymxr.Sources, number int) ymxs.Source {
	if known, met := made[number]; met {
		return known
	}
	of := sources.At(number)
	values := make([]int, len(of.Rows))
	for i, row := range of.Rows {
		values[i] = int(row) &^ ymxr.Mark
	}
	named := name(of.Kind) + " " + strconv.Itoa(of.Data)
	built := ymxs.OnceSource(named, values)
	if of.Repeat < len(of.Rows) {
		built = ymxs.RepeatingSource(named, values, of.Repeat)
	}
	made[number] = built
	return built
}

// name is what a source of that kind is called, so that two sources of one
// shape and two values stay two sources.
func name(kind int) string {
	switch kind {
	case ymxr.SID:
		return "square"
	case ymxr.Drum:
		return "drum"
	case ymxr.Buzzer:
		return "buzzer"
	}
	return "source"
}
