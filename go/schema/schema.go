// Package schema encodes YMXS tune data as YMXR columns (doc/ymxs.md).
// Every converter uses this stage after constructing a YMXS tune.
// Register writes follow the row; effect columns are omitted when the
// player can reuse kept values.
//
// Transformation lookups are optional. Identity-only tunes use version
// 9. Version 10 permits a lookup on the target's last value alone.
// Kept, running and pending mappings are tracked separately because a
// deferred start can change the running mapping between frames (SPEC.md
// 6, rule 2(g)). Version 11 adds the cut columns and the cut index, whose
// kept, running and pending cuts follow the same rules (rule 2(i)).
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

// Made contains encoded columns, sources in first-start order and frame
// rate. Columns also includes optional transformation tables (SPEC.md
// 3.1, 3.4).
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

// Padded encodes a padded tune as columns and sources. frames maps each
// output row to its original structure row, or -1 for an added row;
// diagnostics use original row numbers (SPEC.md 6.5).
//
// A row selecting a transformation produces version 10. Identity-only
// rows produce version 9, without mapping columns or tables.
//
// An error reports an unencodable structure: excess sources or
// transformations, count outside byte range, lookup before the last
// target value, unsupported target, incompatible shared-source markers
// or setR7 use, or a source value or lookup key outside the encoded
// range.
func Padded(tune ymxs.Tune, frames []int) (Made, error) {
	rows := ymxs.Rows(tune)
	size := len(rows)
	repeat := size
	at, repeats := tune.Table.Repeat()
	if repeats {
		repeat = at
	}
	sources := ymxs.Sources(tune)
	// Copy from frame is source S, after the tables (SPEC.md 3.1.7).
	count := len(sources)
	if copiesFrom(rows) {
		count++
	}
	if count > ymxr.Most {
		return Made{}, fmt.Errorf("the tune runs %d sources, and a source column numbers"+
			" %d", count, ymxr.Most)
	}
	// Transformation numbers follow YMXS first-use order. Ignore remaps on
	// timers outside the tune; the count limit applies only when an
	// encoded start or remap uses a lookup (SPEC.md 6, rule 2(a)).
	maps := mappingsOf(tune)
	if maps.transforms() && len(maps.list) > ymxr.MostTransformations {
		return Made{}, fmt.Errorf("the tune maps through %d transformations, and a mapping"+
			" column numbers %d", len(maps.list), ymxr.MostTransformations)
	}
	// Cuts are numbered in first-use order, row order then timer order A to
	// D; a start under the whole table of its source selects cut 0 (SPEC.md
	// 3.5.1).
	cuts := cutsOf(rows, maps.claimed)
	if err := syncCuts(rows); err != nil {
		return Made{}, err
	}
	if len(cuts.list) > ymxr.MostCuts {
		return Made{}, fmt.Errorf("the tune cuts through %d cuts, and a cut column numbers"+
			" %d", len(cuts.list), ymxr.MostCuts)
	}
	// The counted handler counts a cut's rows in a word (SPEC.md section 8).
	for _, cut := range cuts.list {
		if length := cut.Last - cut.First + 1; length > ymxr.MostCountedRows {
			return Made{}, fmt.Errorf("the tune cuts rows %d to %d, %d rows, and a cut is %d"+
				" rows at most", cut.First, cut.Last, length, ymxr.MostCountedRows)
		}
	}
	column := make([][]byte, ymxr.Whole)
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
		// The wrap preserves player state. Write targets again after the
		// repeat row, restore mappings for effects using lookups, and restore
		// rate values that differ from the end of the tune (SPEC.md 6, rules
		// 2(g), 4(e)).
		if f == repeat {
			for i := range target {
				target[i] = -1
			}
			kept.wrap(last)
			maps.wrap()
			cuts.wrap()
		}
		out := make([]byte, ymxr.Whole)
		registers(rows[f], out)
		syncs(rows[f], out)
		lengths(rows[f], out)
		for _, one := range ymxs.Effects(rows[f]) {
			i, err := effectOf(one.Timer)
			if err != nil {
				return Made{}, err
			}
			runs, err := effect(out, i, one.Effect, sources, &target, &kept, &maps, &cuts,
				frames[f])
			if err != nil {
				return Made{}, err
			}
			used |= runs
		}
		for c := 0; c < ymxr.Whole; c++ {
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
	// A table has a group of four columns where the tune uses it: the
	// mapping columns where a row selects a transformation, and the cut
	// columns where a row selects a cut 1 to 127, after the mapping columns;
	// and the three sync length columns after both where a row has a sync
	// length above 1 (SPEC.md 1.12.2, 3.3.5).
	long := lengthy(column)
	if syncsFrom(rows) || long {
		used |= ymxr.Syncs
	}
	columns := ymxr.Columns{Column: append([][]byte(nil), column[:ymxr.C]...),
		Repeat: repeat, Effects: used}
	if selects(column, ymxr.MappingColumn) {
		columns.Column = append(columns.Column, column[ymxr.MappingColumn:ymxr.CutColumn]...)
		columns.Effects |= ymxr.Maps
		for _, one := range maps.list {
			entries := make([]byte, len(one.Entries))
			for k, entry := range one.Entries {
				entries[k] = byte(entry)
			}
			columns.Transformations = append(columns.Transformations, entries)
		}
	}
	if selects(column, ymxr.CutColumn) {
		columns.Column = append(columns.Column, column[ymxr.CutColumn:ymxr.All]...)
		columns.Effects |= ymxr.Cuts
		for _, one := range cuts.list {
			columns.Cuts = append(columns.Cuts, ymxr.Cut{First: one.First, Last: one.Last})
		}
	}
	if long {
		columns.Column = append(columns.Column, column[ymxr.LengthColumn:ymxr.Whole]...)
		columns.Effects |= ymxr.Lengths
	}
	return Made{Columns: columns, Sources: ymxr.Passed(built), Rate: tune.Rate}, nil
}

// copiesFrom reports whether a row starts copy from frame (SPEC.md 3.1.7).
func copiesFrom(rows []ymxs.Row) bool {
	for _, row := range rows {
		for _, one := range ymxs.Effects(row) {
			if start, ok := one.Effect.(ymxs.Start); ok {
				if _, copies := start.Connection.(ymxs.CopyFromFrame); copies {
					return true
				}
			}
		}
	}
	return false
}

// syncCuts reports a target of a sync under a cut or deferred, which is a
// later version's (SPEC.md rule 2(k), section 8): a start on one under a cut
// other than the whole table of its source, a recut of a timer whose last
// start is on one, and a deferred start on one.
func syncCuts(rows []ymxs.Row) error {
	runs := map[ymxs.Timer]ymxs.Target{}
	for at, row := range rows {
		for _, one := range ymxs.Effects(row) {
			on := runs[one.Timer]
			start, starts := one.Effect.(ymxs.Start)
			if starts {
				on = ymxs.ConnectionTarget(start.Connection)
			}
			_, sync := on.(ymxs.SyncTone)
			_, recut := one.Effect.(ymxs.Recut)
			if sync && (starts && start.Cut != ymxs.ConnectionWhole(start.Connection) || recut) {
				return fmt.Errorf("row %d: %s runs under a cut, which this version leaves to a"+
					" later one (SPEC.md section 8)", at, ymxs.TargetName(on))
			}
			if sync && starts && start.Deferred {
				return fmt.Errorf("row %d: %s starts deferred, which this version leaves to a"+
					" later one (SPEC.md section 8)", at, ymxs.TargetName(on))
			}
			if starts {
				runs[one.Timer] = on
			}
		}
	}
	return nil
}

// syncsFrom reports whether a row syncs a voice, at its frame or through a
// start on a target of a sync (SPEC.md 1.12, 3.3.5).
func syncsFrom(rows []ymxs.Row) bool {
	for _, row := range rows {
		if row.Syncs != [3]bool{} {
			return true
		}
		for _, one := range ymxs.Effects(row) {
			if start, ok := one.Effect.(ymxs.Start); ok {
				if _, sync := ymxs.ConnectionTarget(start.Connection).(ymxs.SyncTone); sync {
					return true
				}
			}
		}
	}
	return false
}

// selects reports whether any row sets one of the four columns from first
// to a number 1 to 127, a transformation or a cut (SPEC.md 3.3.5).
func selects(column [][]byte, first int) bool {
	for c := first; c < first+4; c++ {
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

// syncs writes bit 5 of the coarse column of each voice the row syncs,
// which the row sets or leaves unset as the structure has it (SPEC.md
// 1.2.2, 1.12).
func syncs(row ymxs.Row, out []byte) {
	for v, voice := range ymxs.Voices {
		if row.Syncs[v] {
			out[ymxs.Number(ymxs.Coarse(voice))] |= ymxr.Sync
		}
	}
}

// lengths writes the sync length less 1 of each voice, the row's syncSteps
// of the voice and 1 where it has none, in the sync length columns of the
// layout of 41 (SPEC.md 1.12.2; YMXS, SPEC.md 1.6).
func lengths(row ymxs.Row, out []byte) {
	for v, voice := range ymxs.Voices {
		out[ymxr.LengthColumn+v] = byte(ymxs.Steps(row, voice) - 1)
	}
}

// lengthy reports whether a row has a sync length above 1, a sync length
// column other than 0 (SPEC.md 1.12.2, 3.3.5).
func lengthy(column [][]byte) bool {
	for c := ymxr.LengthColumn; c < ymxr.Whole; c++ {
		for _, value := range column[c] {
			if value != 0 {
				return true
			}
		}
	}
	return false
}

// effect encodes one effect operation into its four columns and
// optional mapping column, updating effects-used bits. at is the
// original structure row for diagnostics.
func effect(out []byte, i int, one ymxs.Effect, sources []ymxs.Source,
	target *[4]int, kept *rates, maps *mappings, cuts *cutting, at int) (int, error) {
	t := ymxr.Effect + 4*i
	switch e := one.(type) {
	case ymxs.Stop:
		// A stop sets source 0 and leaves rate and mapping columns unset. It
		// cancels a pending start but preserves the kept mapping for later
		// starts (SPEC.md 6, rule 3(c)).
		out[t+1] = byte(0x80)
		kept.keep(i, one)
		maps.stop(i)
		cuts.stop(i)
		return 0, nil
	case ymxs.Recut:
		// A recut on a timer outside the tune leaves every column unset, as
		// a remap there does (SPEC.md 6, rule 2(a)). One on a timer of the
		// tune writes the cut column where its cut differs from the running
		// cut (rule 2(i)).
		cuts.recut(out, i, cuts.value(one, maps.claimed[i]))
		return 0, nil
	case ymxs.Start:
		// The start's cut column, where its cut differs from the kept cut; a
		// deferred start captures it (rule 2(i)).
		cuts.start(out, i, cuts.value(one, true), e.Deferred)
		runs := ymxs.ConnectionTarget(e.Connection)
		mapping, err := maps.number(e.Mapping, runs, at)
		if err != nil {
			return 0, err
		}
		_, copies := e.Connection.(ymxs.CopyFromFrame)
		reaches := ymxs.TargetNumber(runs)
		if reaches != target[i] {
			out[t] = byte(0x80 | reaches)
			target[i] = reaches
		}
		// Copy from frame is source S, after the tables (3.1.7).
		number := indexOf(sources, ymxs.ConnectionSource(e.Connection)) + 1
		if copies {
			number = len(sources) + 1
		}
		out[t+1] = byte(0x80 | number)
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
		used := 1 << i
		if deferred {
			used |= ymxr.Defers
		}
		if copies {
			used |= ymxr.Copies
		}
		return used, nil
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
			r.counting[i] = ymxs.ConnectionRepeats(e.Connection)
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

// cutting tracks the cut of each effect as mappings tracks the mapping:
// kept for later starts, running for current ticks and pending for a
// deferred start's captured cut, each a cut number, open or, for pending,
// none (SPEC.md 6, rule 2(i)).
type cutting struct {
	kept, running, pending [4]int

	// list contains the cut index, cut n at index n - 1 (SPEC.md 3.5.1).
	list []ymxs.Cut

	// uses marks the effects a start or a recut runs under a cut 1 to 127,
	// whose three cuts the repeat row opens.
	uses [4]bool
}

// cutsOf numbers the cuts the rows select in first-use order: row order,
// then timer order A to D. A start under a cut other than the whole table
// of its source selects one, and so does a recut on a timer of the tune;
// two cuts of the same rows are one (SPEC.md 3.5.1).
func cutsOf(rows []ymxs.Row, claimed [4]bool) cutting {
	out := cutting{}
	for i := range out.pending {
		out.pending[i] = none
	}
	for _, row := range rows {
		for _, one := range ymxs.Effects(row) {
			i, err := effectOf(one.Timer)
			if err != nil {
				continue
			}
			var cut ymxs.Cut
			switch e := one.Effect.(type) {
			case ymxs.Start:
				if e.Cut == ymxs.ConnectionWhole(e.Connection) {
					continue
				}
				cut = e.Cut
			case ymxs.Recut:
				if !claimed[i] {
					continue
				}
				cut = e.Cut
			default:
				continue
			}
			if !containsCut(out.list, cut) {
				out.list = append(out.list, cut)
			}
		}
	}
	for _, row := range rows {
		for _, one := range ymxs.Effects(row) {
			if i, err := effectOf(one.Timer); err == nil && out.value(one.Effect, claimed[i]) > 0 {
				out.uses[i] = true
			}
		}
	}
	return out
}

func containsCut(list []ymxs.Cut, cut ymxs.Cut) bool {
	return indexOfCut(list, cut) >= 0
}

func indexOfCut(list []ymxs.Cut, cut ymxs.Cut) int {
	for n, one := range list {
		if one == cut {
			return n
		}
	}
	return -1
}

// value is the cut number a start or a recut selects: 0 for a start under
// the whole table of its source, the number of its cut otherwise, and -1
// for every other operation and a recut outside the tune (rule 2(i)).
func (c *cutting) value(one ymxs.Effect, claimed bool) int {
	switch e := one.(type) {
	case ymxs.Start:
		if e.Cut == ymxs.ConnectionWhole(e.Connection) {
			return 0
		}
		return indexOfCut(c.list, e.Cut) + 1
	case ymxs.Recut:
		if !claimed {
			return -1
		}
		if at := indexOfCut(c.list, e.Cut); at >= 0 {
			return at + 1
		}
	}
	return -1
}

// start writes the cut column where the start's value differs from the
// kept cut; a deferred start makes it the pending cut (rule 2(i)).
func (c *cutting) start(out []byte, i, value int, deferred bool) {
	if value != c.kept[i] {
		out[ymxr.CutColumn+i] = byte(0x80 | value)
		c.kept[i] = value
	}
	if deferred {
		if c.running[i] != value {
			c.running[i] = open
		}
		c.pending[i] = value
		return
	}
	c.running[i] = value
	c.pending[i] = none
}

// recut writes the cut column where the recut's value differs from the
// running cut, and leaves out a recut outside the tune (rule 2(i)).
func (c *cutting) recut(out []byte, i, value int) {
	if value < 0 || value == c.running[i] {
		return
	}
	out[ymxr.CutColumn+i] = byte(0x80 | value)
	c.kept[i] = value
	c.running[i] = open
	if c.pending[i] == none || c.pending[i] == value {
		c.running[i] = value
	}
}

// stop leaves the cut column unset, drops the pending cut and makes the
// running cut the kept cut (rule 2(i)).
func (c *cutting) stop(i int) {
	c.pending[i] = none
	c.running[i] = c.kept[i]
}

// wrap opens the three cuts of each effect that runs under a cut 1 to 127,
// so the first later start or recut writes the column (rule 2(i)).
func (c *cutting) wrap() {
	for i := range c.uses {
		if c.uses[i] {
			c.kept[i] = open
			c.running[i] = open
			c.pending[i] = open
		}
	}
}

// mappings tracks three values per effect: kept for later starts
// (initially 0), running for current ticks, and pending for a deferred
// start's captured mapping. open marks values not determined by frame
// rows; none marks an absent pending start (SPEC.md 6, rule 2(g)).
type mappings struct {
	kept, running, pending [4]int

	// list contains transformations in first-use order, with number n at
	// index n - 1 (YMXS, SPEC.md 1.9).
	list []ymxs.Transformation

	// claimed marks timers used by a start anywhere in the tune. maps
	// marks effects with an encoded start or remap through a
	// transformation.
	claimed, maps [4]bool

	// runs records the latest start's target, or the first start's target
	// before one occurs. Used for remap diagnostics.
	runs [4]ymxs.Target
}

// transforms reports whether an encoded start or remap uses a
// transformation. Its first selection requires version 10 (SPEC.md
// 3.3.5).
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

// mappingsOf initializes identity mappings at row 0 and records each
// effect's targets and transformation use.
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

// number encodes the mapping's last part: 0 for identity or an empty
// mapping; otherwise its transformation number in list. Earlier parts
// must be Identity. The last value is volume for voice/noise targets,
// shape for buzzer, the coarse nibble for tone, envelope high byte for
// envelope, or the sole register value (SPEC.md 1.10.1). An earlier
// lookup returns an error with structure row at.
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
	// the lookup of a target of a sync is a later version's (SPEC.md
	// section 8)
	if _, sync := target.(ymxs.SyncTone); sync && numbers[0] != 0 {
		return 0, fmt.Errorf("row %d: the mapping maps %s through a transformation, which"+
			" this version leaves to a later one (SPEC.md section 8)", at,
			ymxs.TargetName(target))
	}
	return numbers[0], nil
}

// start writes a changed kept mapping and captures it for this start.
// An immediate start replaces running and clears pending. A deferred
// start preserves current ticks until the boundary; if its mapping
// differs, running becomes open because rows alone do not determine
// when installation occurs (SPEC.md 6, rule 2(g)).
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

// remap ignores timers outside the tune. Otherwise it writes the
// mapping column with the source column unset when the requested
// mapping differs from running. Equality with kept does not remove the
// write: a deferred start may have installed a different mapping. A
// written remap updates kept; pending retains its snapshot (SPEC.md 6,
// rule 2(g)).
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

// stop leaves the mapping column unset, cancels pending and preserves
// kept for later starts (SPEC.md 6, rule 3(c)).
func (m *mappings) stop(i int) {
	m.pending[i] = none
	m.running[i] = m.kept[i]
}

// wrap opens kept, running and pending for effects using
// transformations, so the first later start or remap writes the
// required mapping. Identity-only effects retain 0 and their mapping
// column remains constant (SPEC.md 6, rule 2(g)).
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

// marks records source encoding attributes: marker column (-1 for
// counted full-byte targets), target number, whether every start uses a
// lookup, and whether a setR7 start uses one. These select counted
// encoding for high keys and preserve mixer port bits (SPEC.md 3.1.6;
// rule 2(f)).
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
			number := ymxs.TargetNumber(runs)
			if source == nil {
				// copy from frame has no table, and its marker is the
				// player's (SPEC.md 3.1.7); its target is one this version
				// encodes
				if number >= len(ymxr.Marker) {
					return marks{}, fmt.Errorf("copy from frame runs on %s, a target this"+
						" version leaves to a later one (SPEC.md section 8)",
						ymxs.TargetName(runs))
				}
				continue
			}
			n := indexOf(sources, source)
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

// sourceTables encodes DTX1 sources with one byte column per target
// value. Marked sources set bit 7 of the marker column on the last row
// alone; counted sources preserve all byte bits. Full-byte targets
// require counting. A one-column source used only through lookups also
// requires counting when a key reaches 128 (SPEC.md 3.1.6, 3.2).
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
		// A player keeps the stride of the columns, align(R), in a word
		// (SPEC.md 3.1.3, section 8).
		if columns := ymxs.SourceColumns(source); columns > 1 &&
			len(values) > ymxr.MostWideRows {
			return nil, fmt.Errorf("the source %s has %d columns and %d rows, and such a"+
				" source is %d rows at most", ymxs.SourceName(source), columns, len(values),
				ymxr.MostWideRows)
		}
		// The counted handler counts the rows in a word (SPEC.md section 8).
		if counted && len(values) > ymxr.MostCountedRows {
			return nil, fmt.Errorf("the source %s fills its byte and has %d rows, and such a"+
				" source is %d rows at most", ymxs.SourceName(source), len(values),
				ymxr.MostCountedRows)
		}
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
				// Store setR7 key k as k + 192, preserving both port directions
				// (SPEC.md 6, rule 2(f)).
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
