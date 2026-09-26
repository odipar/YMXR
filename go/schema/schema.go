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
// numbers, or a source of more rows than a table has.
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
	// Whether the timer is known to be counting: a start sets it where the
	// source it runs repeats, and a stop clears it.
	var counting [4]bool
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
			runs, err := effect(out, i, one.Effect, sources, &target, &selects, &count,
				&counting, f)
			if err != nil {
				return Made{}, err
			}
			used |= runs
		}
		for c := 0; c < ymxr.C; c++ {
			column[c][f] = out[c]
		}
	}
	marker, runsOn, err := markers(rows, sources)
	if err != nil {
		return Made{}, err
	}
	built, err := sourceTables(sources, marker, runsOn)
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
// and the bit of the effects word where the row starts one.
func effect(out []byte, i int, one ymxs.Effect, sources []ymxs.Source,
	target, selects, count *[4]int, counting *[4]bool, at int) (int, error) {
	t := ymxr.Effect + 4*i
	switch e := one.(type) {
	case ymxs.Stop:
		// The set bit with no source under it: the timer stops, and the
		// row leaves the rate columns unset (SPEC.md 1.8).
		out[t+1] = byte(0x80)
		counting[i] = false
		return 0, nil
	case ymxs.Start:
		reaches := ymxs.TargetNumber(ymxs.StartTarget(e))
		if reaches != target[i] {
			out[t] = byte(0x80 | reaches)
			target[i] = reaches
		}
		out[t+1] = byte(0x80 | (indexOf(sources, ymxs.StartSource(e)) + 1))
		timing := ymxs.StartTiming(e)
		now, err := selectOf(timing.Prescaler)
		if err != nil {
			return 0, err
		}
		rate, err := counted(timing.Count, at)
		if err != nil {
			return 0, err
		}
		reset := resets(timing.TimerReset, timing.PlaceReset)
		// A start on a timer already counting, at the rate it counts,
		// sets no rate column: step 2 resolves the source and the ticks
		// read it from here on, at the rate the control register already
		// has (SPEC.md 4). The timer is known to be counting only where
		// the source it runs repeats, since a source that plays once
		// stops it at its marker (section 5) and no row says when.
		if reset != 0 || now != selects[i] || rate != count[i] || !counting[i] {
			out[t+2] = byte(0x80 | reset | marked(rate) | now)
			out[t+3] = byte(rate)
		}
		selects[i] = now
		count[i] = rate
		_, counting[i] = ymxs.SourceRows(ymxs.StartSource(e)).Repeat()
		return 1 << i, nil
	case ymxs.Retune:
		now, err := selectOf(e.Timing.Prescaler)
		if err != nil {
			return 0, err
		}
		reset := resets(e.Timing.TimerReset, e.Timing.PlaceReset)
		rate, err := counted(e.Timing.Count, at)
		if err != nil {
			return 0, err
		}
		// A count of 0 is the value the MFP counts 256 for, and the count
		// column reserves 0 for the row that does not set it, so bit 4 of
		// the control column marks it (SPEC.md 1.9). The row then sets the
		// control column whether the select moved or not.
		mark := 0
		if rate != count[i] {
			mark = marked(rate)
		}
		if now != selects[i] || reset != 0 || mark != 0 {
			out[t+2] = byte(0x80 | reset | mark | now)
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

// counted is the count column, which is the timer's data register
// (SPEC.md 1.9). Every value of that register is a count, 0 among them.
func counted(count, at int) (int, error) {
	if count < 0 || count > ymxs.MostCount {
		return 0, fmt.Errorf("row %d: a count of %d, and the count column reaches 0 to %d",
			at, count, ymxs.MostCount)
	}
	return count, nil
}

// marked is bit 4 of the control column, which marks the count column's 0
// as the value the MFP counts 256 for (SPEC.md 1.1, 1.9).
func marked(count int) int {
	if count == 0 {
		return ymxr.CountValue
	}
	return 0
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

// markers is the column of each source's row the marker is in, off the
// targets the tune starts that source on (SPEC.md 2.1, 3.2.1).
//
// The error names a target this version does not encode, or two targets of
// one source that name different columns (rule 2(d)).
func markers(rows []ymxs.Row, sources []ymxs.Source) ([]int, []int, error) {
	marker := make([]int, len(sources))
	target := make([]int, len(sources))
	// unstarted: no row starts the source, so no target names a column and
	// the marker is in column 0, as every version before this one
	// wrote every source.
	for i := range marker {
		marker[i] = unstarted
		target[i] = unstarted
	}
	for _, row := range rows {
		for _, one := range ymxs.Effects(row) {
			start, is := one.Effect.(ymxs.Start)
			if !is {
				continue
			}
			source := ymxs.StartSource(start)
			runs := ymxs.StartTarget(start)
			n := indexOf(sources, source)
			number := ymxs.TargetNumber(runs)
			if number >= len(ymxr.Marker) {
				return nil, nil, fmt.Errorf("the source %s runs on %s, a target this"+
					" version leaves to a later one (SPEC.md section 8)",
					ymxs.SourceName(source), ymxs.TargetName(runs))
			}
			// -1: the target's register fills its byte, so the rows carry no
			// marker and a player counts them (SPEC.md 3.1).
			at := ymxr.Marker[number]
			if marker[n] != unstarted && marker[n] != at {
				return nil, nil, fmt.Errorf("the source %s runs on targets that mark"+
					" column %d and column %d, and a source has one marker column"+
					" (SPEC.md rule 2(d))", ymxs.SourceName(source), marker[n], at)
			}
			marker[n] = at
			target[n] = number
		}
	}
	return marker, target, nil
}

// unstarted marks a source no row starts, which no target names a marker
// column for.
const unstarted = -1 << 30

// sourceTables is the sources as tables of this format: a column a value of
// the row, bit 7 set on the last row of the marker's column (SPEC.md 3.2),
// and the row the source repeats to, its row count where it plays once.
func sourceTables(sources []ymxs.Source, marker, target []int) ([]ymxr.Source, error) {
	var out []ymxr.Source
	for n, source := range sources {
		values := ymxs.SourceRows(source).Rows
		counted := marker[n] == -1
		// A tick of a counted source on setR7 writes the row whole, so the
		// two port directions are in the source rather than in the
		// player (SPEC.md rule 2(f)).
		ports := 0
		if counted && target[n] == ymxr.MixerTarget {
			ports = ymxr.Ports
		}
		at := marker[n]
		if counted || at == unstarted {
			at = 0
		}
		columns := make([][]byte, ymxs.SourceColumns(source))
		for c := range columns {
			columns[c] = make([]byte, len(values))
		}
		for r, row := range values {
			for c := range columns {
				value := row[c]
				most := 0xFF
				if !counted && c == at {
					most = ymxr.Mark - 1
				}
				if value < 0 || value > most {
					said := "a column is one byte"
					if !counted && c == at {
						said = "bit 7 of the marker's column is the marker"
					}
					return nil, fmt.Errorf("the source %s has the value %d in row %d of"+
						" column %d, and %s", ymxs.SourceName(source), value, r, c, said)
				}
				columns[c][r] = byte(value | ports)
			}
		}
		if !counted {
			columns[at][len(values)-1] |= byte(ymxr.Mark)
		}
		repeat := len(values)
		if to, repeats := ymxs.SourceRows(source).Repeat(); repeats {
			repeat = to
		}
		out = append(out, ymxr.Source{Kind: kind(source), Columns: columns,
			Repeat: repeat, Counted: counted})
	}
	return out, nil
}

// kind names what a source of this shape sounds, for a report: the format
// names no kind, and a tune's use of the shape settles it (SPEC.md 2.2).
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
