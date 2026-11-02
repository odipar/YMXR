// Package schema maps a YMXS tune onto this format's columns
// (doc/ymxs.md): the structure YMXS defines, encoded as SPEC.md 1 encodes
// it.
//
// YMXS defines what is true of a row; a column is written where its value
// differs from the value the player keeps (R3.6, R4.6). So this keeps the
// target, the select, the count and the mapping of each effect as a player
// does, and a retune whose select did not move leaves the control column
// unset. The registers a row sets are the row's, since a YMXS row lists
// what it sets and leaves the rest alone.
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

// Of is the tune as columns and sources, the line of an error numbering a
// row of the tune.
func Of(tune ymxs.Tune) (Made, error) {
	frames := make([]int, len(ymxs.Rows(tune)))
	for i := range frames {
		frames[i] = i
	}
	return Padded(tune, frames)
}

// Padded is a tune that ymxr.PadToUnit padded, as columns and sources:
// frames is, for each row of the tune, the row of the structure it comes
// from, or -1 for a row the padding added, and the line of an error
// numbers that row (SPEC.md 6.5).
//
// The tune file is version 10 where a row sets a mapping column to a
// transformation, 1 to 127, and version 9 otherwise, with 30 columns and
// no transformation index (SPEC.md 3.3.5).
//
// The error names a structure this format cannot encode: more sources than
// the source column numbers, more transformations than the mapping column
// numbers in a tune that maps a value, a count outside 0 to 255, a mapping
// through a transformation in a part other than the last, a target past
// 24, a source on two targets that mark different columns, a source on
// setR7 and on another target, a source value past seven bits in its
// marker's column or past eight bits in another column, or a key past 63
// on setR7.
func Padded(tune ymxs.Tune, frames []int) (Made, error) {
	rows := ymxs.Rows(tune)
	size := len(rows)
	repeat := size
	at, repeats := tune.Table.Repeat()
	if repeats {
		repeat = at
	}
	sources := ymxs.Sources(tune)
	if len(sources) > ymxr.Most {
		return Made{}, fmt.Errorf("the tune runs %d sources, and a source column numbers"+
			" %d", len(sources), ymxr.Most)
	}
	// Transformation n is the one a mapping column numbers n, in the order
	// of YMXS, SPEC.md 1.9 (SPEC.md 3.4.1). A remap on a timer outside the
	// tune leaves every column as it is (rule 2(a)), so more than 127
	// transformations is an error only where a start, or a remap on a timer
	// of the tune, maps through one.
	maps := mappingsOf(tune)
	if maps.transforms() && len(maps.list) > ymxr.MostTransformations {
		return Made{}, fmt.Errorf("the tune maps through %d transformations, and a mapping"+
			" column numbers %d", len(maps.list), ymxr.MostTransformations)
	}
	column := make([][]byte, ymxr.Mapped)
	for c := range column {
		column[c] = make([]byte, size)
	}
	var target [4]int
	for i := range target {
		target[i] = -1
	}
	// kept is the rate the rows read so far leave each effect, and last the
	// rate after the last row, which the repeat row reads (rule 4(e)).
	var kept rates
	last := ratesAfter(rows)
	used := 0
	for f := 0; f < size; f++ {
		// After the wrap the player keeps what the last row left, so the
		// row the tune repeats to writes its targets again, the mapping of
		// each effect a row maps through a transformation, and each value
		// of the rate whose value after the last row differs from its value
		// before the repeat row (rule 4(e)).
		if f == repeat {
			for i := range target {
				target[i] = -1
			}
			kept.wrap(last)
			maps.wrap()
		}
		out := make([]byte, ymxr.Mapped)
		registers(rows[f], out)
		for _, one := range ymxs.Effects(rows[f]) {
			i, err := effectOf(one.Timer)
			if err != nil {
				return Made{}, err
			}
			runs, err := effect(out, i, one.Effect, sources, &target, &kept, &maps,
				frames[f])
			if err != nil {
				return Made{}, err
			}
			used |= runs
		}
		for c := 0; c < ymxr.Mapped; c++ {
			column[c][f] = out[c]
		}
	}
	marks, err := markers(rows, sources)
	if err != nil {
		return Made{}, err
	}
	built, err := sourceTables(sources, marks)
	if err != nil {
		return Made{}, err
	}
	columns := ymxr.Columns{Column: column[:ymxr.C], Repeat: repeat, Effects: used}
	if mapsAValue(column) {
		columns.Column = column
		columns.Effects |= ymxr.Maps
		for _, one := range maps.list {
			entries := make([]byte, len(one.Entries))
			for k, entry := range one.Entries {
				entries[k] = byte(entry)
			}
			columns.Transformations = append(columns.Transformations, entries)
		}
	}
	return Made{Columns: columns, Sources: ymxr.Passed(built), Rate: tune.Rate}, nil
}

// mapsAValue is whether a row sets a mapping column to a transformation, 1
// to 127, which makes the tune file version 10 (SPEC.md 3.3.5).
func mapsAValue(column [][]byte) bool {
	for c := ymxr.MappingColumn; c < ymxr.Mapped; c++ {
		for _, value := range column[c] {
			if value&0x80 != 0 && value&0x7F != 0 {
				return true
			}
		}
	}
	return false
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

// effect writes one row's operation on one effect, as its four columns and
// its mapping column, and the bit of the effects word where the row starts
// one. at is the row of the structure, for the line of an error.
func effect(out []byte, i int, one ymxs.Effect, sources []ymxs.Source,
	target *[4]int, kept *rates, maps *mappings, at int) (int, error) {
	t := ymxr.Effect + 4*i
	switch e := one.(type) {
	case ymxs.Stop:
		// The set bit with no source under it: the timer stops, and the
		// row leaves the rate columns and the mapping column unset (SPEC.md
		// 1.8, rule 3(c)).
		out[t+1] = byte(0x80)
		kept.keep(i, one)
		maps.stop(i)
		return 0, nil
	case ymxs.Start:
		runs := ymxs.ConnectionTarget(e.Connection)
		mapping, err := maps.number(e.Mapping, runs, at)
		if err != nil {
			return 0, err
		}
		reaches := ymxs.TargetNumber(runs)
		if reaches != target[i] {
			out[t] = byte(0x80 | reaches)
			target[i] = reaches
		}
		out[t+1] = byte(0x80 | (indexOf(sources, ymxs.ConnectionSource(e.Connection)) + 1))
		maps.start(out, i, mapping, runs, e.Deferred)
		timing := e.Timing
		now, err := selectOf(timing.Prescaler)
		if err != nil {
			return 0, err
		}
		rate, err := counted(timing.Count, at)
		if err != nil {
			return 0, err
		}
		reset := resets(timing.TimerReset, timing.PlaceReset)
		deferred := e.Deferred
		// A start on a timer already counting, at the rate it counts,
		// sets no rate column: step 2 resolves the source and the ticks
		// read it from here on, at the rate the control register already
		// has (SPEC.md 4). The timer is known to be counting only where
		// the source it runs repeats, since a source that plays once
		// stops it at its marker (section 5) and no row says when. A
		// deferred start sets the control column, whose bit 3 marks it
		// (1.9).
		if reset != 0 || now != kept.selects[i] || rate != kept.count[i] ||
			!kept.counting[i] || deferred {
			mark := 0
			if deferred {
				mark = ymxr.Deferred
			}
			out[t+2] = byte(0x80 | reset | mark | marked(rate) | now)
			out[t+3] = byte(rate)
		}
		kept.keep(i, one)
		if deferred {
			return 1<<i | ymxr.Defers, nil
		}
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
		if rate != kept.count[i] {
			mark = marked(rate)
		}
		if now != kept.selects[i] || reset != 0 || mark != 0 {
			out[t+2] = byte(0x80 | reset | mark | now)
		}
		if rate != kept.count[i] {
			out[t+3] = byte(rate)
		}
		kept.keep(i, one)
		return 0, nil
	case ymxs.Remap:
		return 0, maps.remap(out, i, e.Mapping, at)
	}
	panic(fmt.Sprintf("no effect %T", one))
}

// rates is the rate the rows leave each effect (SPEC.md 6, rule 4(e)): the
// kept select and count, each open where the rows leave it open, after a
// deferred start or at the repeat row, and whether the timer is known to be
// counting.
type rates struct {
	selects, count [4]int

	// counting is whether the timer is known to be counting: a start sets
	// it where the source it runs repeats, and a stop clears it.
	counting [4]bool
}

// keep is the operation on effect i. A start or a retune sets the select
// and the count to its rate, and a start sets the flag where its source
// repeats. Until the tick that performs a deferred start the timer counts
// at the rate of the effect before it, apart from the rate a row keeps, so
// a deferred start opens the select and the count and clears the flag: the
// next row that sets a rate sets both columns (4.8). A stop clears the
// flag. Each value after the operation follows from the operation alone,
// so the values after the last row are the same whether the repeat row
// opens them or not.
func (r *rates) keep(i int, one ymxs.Effect) {
	switch e := one.(type) {
	case ymxs.Start:
		r.selects[i], r.count[i], r.counting[i] = open, open, false
		if !e.Deferred {
			r.selects[i], _ = selectOf(e.Timing.Prescaler)
			r.count[i] = e.Timing.Count
			_, r.counting[i] = ymxs.SourceRows(ymxs.ConnectionSource(e.Connection)).Repeat()
		}
	case ymxs.Retune:
		r.selects[i], _ = selectOf(e.Timing.Prescaler)
		r.count[i] = e.Timing.Count
	case ymxs.Stop:
		r.counting[i] = false
	}
}

// wrap is the repeat row: the select and the count, each open where its
// value before the repeat row differs from its value after the last row,
// last, and the flag cleared where it differs from the flag there. A value
// equal to it keeps its value: the second pass starts from that value, as
// the first does.
func (r *rates) wrap(last rates) {
	for i := range r.selects {
		if r.selects[i] != last.selects[i] {
			r.selects[i] = open
		}
		if r.count[i] != last.count[i] {
			r.count[i] = open
		}
		if r.counting[i] != last.counting[i] {
			r.counting[i] = false
		}
	}
}

// ratesAfter is the rate the rows leave each effect after the last row.
func ratesAfter(rows []ymxs.Row) rates {
	var out rates
	for _, row := range rows {
		for _, one := range ymxs.Effects(row) {
			if i, err := effectOf(one.Timer); err == nil {
				out.keep(i, one.Effect)
			}
		}
	}
	return out
}

// mappings is the mapping of each effect as the rows set it (SPEC.md 6,
// rule 2(g)): kept is the kept mapping (1.10.3), 0 at the first row;
// running the mapping the running effect writes through, where the rows
// fix it, and open where a deferred start through another may be pending;
// and pending the mapping of a deferred start that may be pending, none,
// or open after the wrap.
type mappings struct {
	kept, running, pending [4]int

	// list is the tune's transformations, transformation n at n - 1
	// (YMXS, SPEC.md 1.9).
	list []ymxs.Transformation

	// claimed is whether the effect's timer is a timer of the tune, which a
	// row starts a source on (YMXS, SPEC.md 1.10); maps whether a start of
	// the effect, or a remap on a timer of the tune, maps through a
	// transformation.
	claimed, maps [4]bool

	// runs is the target of the last start on the effect, and before its
	// first start the target of that start: the target a line reports for
	// a remap.
	runs [4]ymxs.Target
}

// transforms is whether a start, or a remap on a timer of the tune, maps
// through a transformation: the first such row on an effect sets its mapping
// column to 1 to T, so the file is version 10 (SPEC.md 3.3.5).
func (m *mappings) transforms() bool {
	for _, one := range m.maps {
		if one {
			return true
		}
	}
	return false
}

// none marks a timer with no deferred start pending, and open a value the
// rows leave open: the player may keep one of several.
const (
	none = -2
	open = -1
)

// mappingsOf is the mappings of the tune at its first row.
func mappingsOf(tune ymxs.Tune) mappings {
	out := mappings{list: ymxs.Transformations(tune)}
	for i := range out.pending {
		out.pending[i] = none
	}
	for _, timer := range ymxs.Claimed(tune) {
		i, err := effectOf(timer)
		if err == nil {
			out.claimed[i] = true
		}
	}
	for _, row := range ymxs.Rows(tune) {
		for _, one := range ymxs.Effects(row) {
			i, err := effectOf(one.Timer)
			if err != nil {
				continue
			}
			switch e := one.Effect.(type) {
			case ymxs.Start:
				if out.runs[i] == nil {
					out.runs[i] = ymxs.ConnectionTarget(e.Connection)
				}
				out.maps[i] = out.maps[i] || !ymxs.IsIdentity(e.Mapping)
			case ymxs.Remap:
				out.maps[i] = out.maps[i] || out.claimed[i] && !ymxs.IsIdentity(e.Mapping)
			}
		}
	}
	return out
}

// number is the number a mapping column is written with for the mapping
// on the target (SPEC.md 1.10.1): 0 for a mapping of no parts, and
// otherwise the number of its last part, 0 for the identity and n for
// transformation n of the list. The last part maps the one value of setR0
// to setR13, the volume of setVoiceX and setNoiseX, the shape of setBuzzer,
// the coarse nibble of setToneX and the high byte of setEnvelope.
//
// The error is the line of a part before the last that is a
// transformation, with the row at.
func (m *mappings) number(mapping ymxs.Mapping, target ymxs.Target, at int) (int, error) {
	parts := mapping.Parts
	if len(parts) == 0 {
		return 0, nil
	}
	last := len(parts) - 1
	for v, part := range parts[:last] {
		if _, identity := part.(ymxs.Identity); !identity {
			return 0, fmt.Errorf("row %d: the mapping maps value %d of %s, and a mapping"+
				" column maps its value %d alone", at, v, ymxs.TargetName(target), last)
		}
	}
	numbers, err := ymxs.PartNumbers(ymxs.Mapped(parts[last]), m.list)
	if err != nil {
		return 0, err
	}
	return numbers[0], nil
}

// start writes the mapping column of a start on effect i through that
// mapping number, where it differs from the kept mapping: a start whose
// mapping column is unset runs through the kept mapping, and a deferred
// one keeps it from its row (SPEC.md 1.10.2, 1.10.3). The effect before a
// deferred start runs through its mapping until the tick that performs
// it, which the rows leave open.
func (m *mappings) start(out []byte, i, mapping int, target ymxs.Target, deferred bool) {
	m.runs[i] = target
	if mapping != m.kept[i] {
		out[ymxr.MappingColumn+i] = byte(0x80 | mapping)
		m.kept[i] = mapping
	}
	if deferred {
		if m.running[i] != mapping {
			m.running[i] = open
		}
		m.pending[i] = mapping
		return
	}
	m.running[i] = mapping
	m.pending[i] = none
}

// remap writes the mapping column of a remap on effect i. A remap on a
// timer outside the tune leaves every column as it is (rule 2(a)). On a
// timer of the tune, idle or running, it sets the mapping column with the
// source column unset (SPEC.md 1.10.2), and is left out where the effect
// runs through its mapping already. Equal to the kept mapping it is
// written all the same where a deferred start through another mapping may
// be pending or may have been performed (rule 2(g)).
func (m *mappings) remap(out []byte, i int, mapping ymxs.Mapping, at int) error {
	if !m.claimed[i] {
		return nil
	}
	number, err := m.number(mapping, m.runs[i], at)
	if err != nil {
		return err
	}
	if m.running[i] == number {
		return nil
	}
	out[ymxr.MappingColumn+i] = byte(0x80 | number)
	m.kept[i] = number
	m.running[i] = open
	if m.pending[i] == none || m.pending[i] == number {
		m.running[i] = number
	}
	return nil
}

// stop is a stop on effect i, which leaves the mapping column unset (rule
// 3(c)): it drops a pending start, and the next start keeps the kept
// mapping.
func (m *mappings) stop(i int) {
	m.pending[i] = none
	m.running[i] = m.kept[i]
}

// wrap is the repeat row: the player keeps what the last row left, so the
// first pass leaves open the mapping of each effect a row maps through a
// transformation. An effect through the identity alone keeps 0 through the
// wrap, and its mapping column is 0 in every row.
func (m *mappings) wrap() {
	for i := range m.maps {
		if m.maps[i] {
			m.kept[i] = open
			m.running[i] = open
			m.pending[i] = open
		}
	}
}

// unstarted marks a source no row starts, which no target names a marker
// column for.
const unstarted = -1 << 30

// marks is where each source's marker is and which target it runs on: the
// column bit 7 marks, -1 where the target's register fills its byte; the
// target's number, which a counted source on setR7 reads the port
// directions in for (rule 2(f)); whether every start of the source maps it
// through a transformation, so its rows are keys (3.1.6); and whether a
// start on setR7 maps it through one (rule 2(f)).
type marks struct {
	column, target []int
	keyed, mixer   []bool
}

// markers is the column of each source's row the marker is in, off the
// targets the tune starts that source on (SPEC.md 2.1, 3.2.1).
//
// The error names a target this version does not encode, two targets of one
// source that name different columns (rule 2(d)), or a source on setR7 and
// on another target (rule 2(f)).
func markers(rows []ymxs.Row, sources []ymxs.Source) (marks, error) {
	out := marks{column: make([]int, len(sources)), target: make([]int, len(sources)),
		keyed: make([]bool, len(sources)), mixer: make([]bool, len(sources))}
	identity := make([]bool, len(sources))
	// last is the target of the last start of each source, for the line of
	// rule 2(f).
	last := make([]ymxs.Target, len(sources))
	// unstarted: no row starts the source, so no target names a column and
	// the marker is in column 0, as every version before this one
	// wrote every source.
	for i := range out.column {
		out.column[i] = unstarted
		out.target[i] = unstarted
	}
	for _, row := range rows {
		for _, one := range ymxs.Effects(row) {
			start, is := one.Effect.(ymxs.Start)
			if !is {
				continue
			}
			source := ymxs.ConnectionSource(start.Connection)
			runs := ymxs.ConnectionTarget(start.Connection)
			n := indexOf(sources, source)
			number := ymxs.TargetNumber(runs)
			through := !ymxs.IsIdentity(start.Mapping)
			identity[n] = identity[n] || !through
			out.mixer[n] = out.mixer[n] || through && number == ymxr.MixerTarget
			if number >= len(ymxr.Marker) {
				return marks{}, fmt.Errorf("the source %s runs on %s, a target this"+
					" version leaves to a later one (SPEC.md section 8)",
					ymxs.SourceName(source), ymxs.TargetName(runs))
			}
			// -1: the target's register fills its byte, so the rows carry no
			// marker and a player counts them (SPEC.md 3.1).
			at := ymxr.Marker[number]
			if out.column[n] != unstarted && out.column[n] != at {
				return marks{}, fmt.Errorf("the source %s runs on targets that mark"+
					" column %d and column %d, and a source has one marker column"+
					" (SPEC.md rule 2(d))", ymxs.SourceName(source), out.column[n], at)
			}
			// Every row of a source on setR7 has the two port directions, and
			// a tick on another target writes the row whole to its register
			// (rule 2(f)).
			if out.target[n] != unstarted && out.target[n] != number &&
				(out.target[n] == ymxr.MixerTarget || number == ymxr.MixerTarget) {
				other := runs
				if number == ymxr.MixerTarget {
					other = last[n]
				}
				return marks{}, fmt.Errorf("the source %s runs on setR7 and on %s, and every"+
					" row of a source on setR7 has bits 7 and 6 set (SPEC.md rule 2(f))",
					ymxs.SourceName(source), ymxs.TargetName(other))
			}
			out.column[n] = at
			out.target[n] = number
			last[n] = runs
		}
	}
	for n := range out.keyed {
		out.keyed[n] = out.column[n] != unstarted && !identity[n]
	}
	return out, nil
}

// sourceTables is the sources as tables of this format: a column a value of
// the row, bit 7 set on the last row of the marker's column (SPEC.md 3.2),
// and the row the source repeats to, its row count where it plays once.
//
// A source whose target's register fills its byte has no bit for the
// marker: every row is a whole byte and a player counts them (3.1). So is a
// source of one column whose every start maps it through a transformation,
// where a key of 128 or more is in a row (3.1.6).
func sourceTables(sources []ymxs.Source, marks marks) ([]ymxr.Source, error) {
	var out []ymxr.Source
	for n, source := range sources {
		values := ymxs.SourceRows(source).Rows
		marker := marks.column[n]
		if marker == 0 && ymxs.SourceColumns(source) == 1 && marks.keyed[n] &&
			pastMarker(values) {
			marker = -1
		}
		counted := marker == -1
		// A tick of a counted source on setR7 writes the row whole, so the
		// two port directions are in the source rather than in the
		// player (SPEC.md rule 2(f)).
		ports := 0
		if counted && marks.target[n] == ymxr.MixerTarget {
			ports = ymxr.Ports
		}
		at := marker
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
				// Through a transformation on setR7 a key k is read as k +
				// 192, the two port directions set (rule 2(f)).
				if marks.mixer[n] && value > ymxr.MostMixerKey {
					return nil, fmt.Errorf("the source %s has the key %d in row %d on"+
						" setR7, and a key on setR7 is 0 to %d", ymxs.SourceName(source),
						value, r, ymxr.MostMixerKey)
				}
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

// pastMarker is whether a row of a source of one column is 128 or more,
// where bit 7 of a marked source is the marker (3.2.1).
func pastMarker(values [][]int) bool {
	for _, row := range values {
		if row[0] >= ymxr.Mark {
			return true
		}
	}
	return false
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

// kind names what a source of this shape sounds, for a report: the format
// names no kind, and a tune's use of the shape settles it (SPEC.md 2.2).
func kind(source ymxs.Source) int {
	table := ymxs.SourceRows(source)
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
