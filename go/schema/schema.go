// Package schema maps a YMXS tune onto this format's columns
// (doc/ymxs.md): the structure YMXS defines, encoded as SPEC.md 1 encodes
// it.
//
// YMXS defines what is true of a row; a column is written where its value
// differs from the value the player keeps (R3.6, R4.6). So this keeps the
// target, the select and the count of each effect as a player does, and a
// retune whose select did not move leaves the control column unset. The
// registers a row sets are the row's, since a YMXS row lists what it sets
// and leaves the rest alone.
//
// Every conversion here passes through this: a dump is read into a YMXS
// tune and mapped by this package, so the columns follow from the
// structure alone.
package schema

import (
	"fmt"

	"github.com/odipar/ymxr/go/ymxr"
	"github.com/odipar/ymxs/go/ymxs"
)

// timers is the effect each timer runs, SPEC.md 2.3: A, D, B, C.
var timers = [4]ymxs.Timer{ymxs.TimerA, ymxs.TimerD, ymxs.TimerB, ymxs.TimerC}

// Timer is the timer effect i runs (SPEC.md 2.3).
func Timer(i int) ymxs.Timer {
	return timers[i]
}

// Made is a tune mapped: its columns, its sources numbered as SPEC.md 3.1
// numbers them, and the rate its header records.
type Made struct {
	Columns ymxr.Columns
	Sources *ymxr.Sources
	Rate    int
}

// Of is the tune as columns and sources.
//
// The error names a structure this format cannot encode: a count of 256, a
// source value past seven bits, more sources than the source column
// numbers, or a source of more rows than a table carries.
func Of(tune ymxs.Tune) (Made, error) {
	rows := ymxs.Rows(tune)
	frames := len(rows)
	repeat := frames
	if at, repeats := tune.Table.Repeat(); repeats {
		repeat = at
	}
	sources := ymxs.Sources(tune)
	if len(sources) > ymxr.Most {
		return Made{}, fmt.Errorf("the tune runs %d sources, and a source column numbers"+
			" %d", len(sources), ymxr.Most)
	}
	column := make([][]byte, ymxr.C)
	for c := range column {
		column[c] = make([]byte, frames)
	}
	var target, selects, count [4]int
	for i := range target {
		target[i] = -1
	}
	used := 0
	for f := 0; f < frames; f++ {
		// After the wrap the player keeps what the last row left, so the
		// row the tune repeats to writes its targets again.
		if f == repeat {
			for i := range target {
				target[i] = -1
			}
		}
		out := make([]byte, ymxr.C)
		registers(rows[f], out)
		for _, one := range ymxs.Effects(rows[f]) {
			i, err := effectOf(one.Timer)
			if err != nil {
				return Made{}, err
			}
			runs, err := effect(out, i, one.Effect, sources, &target, &selects, &count, f)
			if err != nil {
				return Made{}, err
			}
			used |= runs
		}
		for c := 0; c < ymxr.C; c++ {
			column[c][f] = out[c]
		}
	}
	built, err := sourceTables(sources)
	if err != nil {
		return Made{}, err
	}
	return Made{Columns: ymxr.Columns{Column: column, Repeat: repeat, Effects: used},
		Sources: ymxr.Passed(built), Rate: tune.Rate}, nil
}

// registers writes the registers the row sets, each in its column: the set
// bit where the column has one, and the bit beside it where a column that
// fills its byte is 0 (SPEC.md 1.1).
func registers(row ymxs.Row, out []byte) {
	for _, one := range ymxs.Registered(row) {
		c := ymxs.Number(one.Register)
		value := one.Value
		if c < len(ymxr.BesideColumn) && ymxr.BesideColumn[c] >= 0 {
			out[c] = byte(value)
			if value == 0 {
				out[ymxr.BesideColumn[c]] |= byte(ymxr.BesideBit[c])
			}
		} else {
			out[c] |= byte(0x80 | value)
		}
	}
}

// effect writes one row's operation on one effect, as its four columns,
// and gives the bit of the effects word where the row starts one.
func effect(out []byte, i int, one ymxs.Effect, sources []ymxs.Source,
	target, selects, count *[4]int, at int) (int, error) {
	t := ymxr.Effect + 4*i
	switch e := one.(type) {
	case ymxs.Stop:
		// The set bit with no source under it: the timer stops, and the
		// row leaves the rate columns unset (SPEC.md 1.8).
		out[t+1] = byte(0x80)
		return 0, nil
	case ymxs.Start:
		reaches := ymxs.TargetNumber(e.Target)
		if reaches != target[i] {
			out[t] = byte(0x80 | reaches)
			target[i] = reaches
		}
		out[t+1] = byte(0x80 | (indexOf(sources, e.Source) + 1))
		by, err := selectOf(e.Prescaler)
		if err != nil {
			return 0, err
		}
		selects[i] = by
		counted, err := counted(e.Count, at)
		if err != nil {
			return 0, err
		}
		count[i] = counted
		out[t+2] = byte(0x80 | resets(e.TimerReset, e.PlaceReset) | selects[i])
		out[t+3] = byte(count[i])
		return 1 << i, nil
	case ymxs.Retune:
		now, err := selectOf(e.Prescaler)
		if err != nil {
			return 0, err
		}
		reset := resets(e.TimerReset, e.PlaceReset)
		if now != selects[i] || reset != 0 {
			out[t+2] = byte(0x80 | reset | now)
		}
		rate, err := counted(e.Count, at)
		if err != nil {
			return 0, err
		}
		if rate != count[i] {
			out[t+3] = byte(rate)
		}
		selects[i] = now
		count[i] = rate
		return 0, nil
	}
	return 0, fmt.Errorf("row %d: an effect this version does not read", at)
}

// counted is the count column, which fills its byte, so the 256 the MFP
// reads a 0 as is out of reach (SPEC.md 1.9).
func counted(count, at int) (int, error) {
	if count < 1 || count >= ymxs.MostCount {
		return 0, fmt.Errorf("row %d: a count of %d, and the count column reaches 1 to %d",
			at, count, ymxs.MostCount-1)
	}
	return count, nil
}

func resets(timer, place bool) int {
	out := 0
	if timer {
		out |= ymxr.TimerReset
	}
	if place {
		out |= ymxr.PlaceReset
	}
	return out
}

// selectOf is the prescaler select the control register reads (SPEC.md
// 1.9).
func selectOf(prescaler ymxs.Prescaler) (int, error) {
	by := ymxs.Divides(prescaler)
	for i := 1; i < len(ymxr.Prescaler); i++ {
		if ymxr.Prescaler[i] == by {
			return i, nil
		}
	}
	return 0, fmt.Errorf("no select divides by %d", by)
}

// effectOf is the effect a timer runs (SPEC.md 2.3).
func effectOf(timer ymxs.Timer) (int, error) {
	for i, one := range timers {
		if one == timer {
			return i, nil
		}
	}
	return 0, fmt.Errorf("no effect runs on Timer %s", timer)
}

// sourceTables is the sources as tables of this format: the values, bit 7
// set on the last row as the marker (SPEC.md 3.2), and the row the source
// repeats to, its row count where it plays once.
func sourceTables(sources []ymxs.Source) ([]ymxr.Source, error) {
	var out []ymxr.Source
	for _, source := range sources {
		values := ymxs.Values(source)
		rows := make([]byte, len(values))
		for r, value := range values {
			if value < 0 || value >= ymxr.Mark {
				return nil, fmt.Errorf("the source %s has the value %d in row %d, and"+
					" bit 7 of a source's row is the marker", ymxs.SourceName(source),
					value, r)
			}
			rows[r] = byte(value)
		}
		rows[len(rows)-1] |= byte(ymxr.Mark)
		repeat := len(rows)
		if at, repeats := ymxs.SourceTable(source).Repeat(); repeats {
			repeat = at
		}
		out = append(out, ymxr.Source{Kind: kind(source), Rows: rows, Repeat: repeat})
	}
	return out, nil
}

// kind is what a source of this shape sounds, for a report: the format
// names no kind, and the shape is what a tune uses it for (SPEC.md 2.2).
func kind(source ymxs.Source) int {
	table := ymxs.SourceTable(source)
	_, repeats := table.Repeat()
	if !repeats {
		return ymxr.Drum
	}
	if len(table.Rows) == 1 {
		return ymxr.Buzzer
	}
	return ymxr.SID
}

func indexOf(sources []ymxs.Source, source ymxs.Source) int {
	for i, one := range sources {
		if ymxs.SourceEqual(one, source) {
			return i
		}
	}
	return -1
}
