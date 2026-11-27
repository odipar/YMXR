// Package trace is a reader's report of a tune (SPEC.md 7): one line a
// play call, the call's result, the registers the frame writes and the
// effects the row set. The kit's references are the output of this,
// and the rig checks the 68000 player to it.
package trace

import (
	"github.com/odipar/dtx/go/dtx"

	"github.com/odipar/ymxr/go/ymxr"
)

// The frame model of SPEC.md 4 records registers and effects after each
// row, without chip writes. Unset registers are -1, as are registers
// used by an effect from the frame after its start. Ticks, source
// places and pending starts are outside the model.
//
// Written records the current row's register writes, with -1 for
// omitted registers.
//
// Effect records one effect after a frame: Source 0 means idle. Set
// marks an effect-column write; Timer, Place and Deferred record
// control bits 6, 5 and 3. Mapping is the kept mapping, initially 0 for
// identity, and Cut the kept cut, initially 0 for the whole table. A
// deferred start can run through a different captured mapping and cut
// (SPEC.md 1.10.3, 1.11.3, 7.3).
type Effect struct {
	Target   int
	Source   int
	Select   int
	Count    int
	Started  bool
	Set      bool
	Timer    bool
	Place    bool
	Deferred bool
	Mapping  int
	Cut      int
}

// Replay is the model over one table.
type Replay struct {
	Registers       [14]int
	Written         [14]int
	Effect          [4]Effect
	EnvelopeWritten bool
	// Syncs is the voices the last row syncs, bit 0 voice A to bit 2 voice
	// C: bit 5 of each coarse column in versions 13 to 15, read on every row
	// (SPEC.md 1.12).
	Syncs int
	// Evens is the voices whose bit 4 the last row sets, bit 0 voice A to
	// bit 2 voice C, in version 14, a sync length of 2, read on every row
	// (SPEC.md 1.12.2).
	Evens int
	// Lengths is the sync length of each voice in the last row, A, B and C,
	// 1 to 8: the sync length column and 1 in version 15, 2 where bit 4 is
	// set in version 14, and 1 otherwise (SPEC.md 1.12.2).
	Lengths [3]int

	table *dtx.Table
	row   int

	// effects is the effects used, whose bits 5 and 6 lay out the columns
	// after 29 (SPEC.md 3.3.5).
	effects int
}

// Of is a model at the first row of a table of that effects byte.
func Of(table *dtx.Table, effects int) *Replay {
	out := &Replay{table: table, effects: effects}
	for i := range out.Registers {
		out.Registers[i] = -1
	}
	return out
}

// Row is the row number the next step reads, following the table's repeat.
func (m *Replay) Row() int {
	return m.row
}

// Rows is how many rows the table has.
func (m *Replay) Rows() int {
	return m.table.Rows()
}

// Step reads one frame: the next row, as section 4 writes it.
func (m *Replay) Step() {
	columns := m.table.Columns()
	r := make([]byte, columns)
	for c := 0; c < columns; c++ {
		r[c] = m.table.Column(c)[m.row]
	}
	m.Syncs = 0
	m.Evens = 0
	for v := 0; ymxr.HasSyncs(m.effects) && v < 3; v++ {
		if r[1+2*v]&ymxr.Sync != 0 {
			m.Syncs |= 1 << v
		}
		if ymxr.HasEvens(m.effects) && r[1+2*v]&ymxr.Even != 0 {
			m.Evens |= 1 << v
		}
		// bits 2 to 0 of the sync length column, the length less 1; bits 7
		// to 3 are unassigned and left unread (SPEC.md 1.12.2)
		switch {
		case ymxr.HasLengths(m.effects):
			m.Lengths[v] = int(r[ymxr.LengthColumnOf(m.effects, v)]&7) + 1
		case m.Evens&(1<<v) != 0:
			m.Lengths[v] = 2
		default:
			m.Lengths[v] = 1
		}
	}
	m.row++
	if m.row == m.table.Rows() {
		m.row = m.table.Repeat()
	}
	for i := 0; i < 4; i++ {
		t := ymxr.Effect + 4*i
		was := m.Effect[i]
		// A row leaves the column of a volume register an effect runs on
		// unset (SPEC.md 6 rule 1), so the model has no value for it: the
		// row that stops the effect sets the register again.
		if was.Source != 0 && was.Target < 13 {
			m.Registers[was.Target] = -1
		}
		target := was.Target
		if r[t]&0x80 != 0 {
			target = int(r[t]) & 0x7F
		}
		source := was.Source
		started := false
		if r[t+1]&0x80 != 0 {
			source = int(r[t+1]) & 0x7F
			started = source != 0
		}
		selects := was.Select
		count := was.Count
		timer := false
		place := false
		deferred := false
		// Bit 4 marks the count column's 0 as the value the MFP counts 256
		// for, and a player reads it where the row sets the control column
		// (SPEC.md 1.1, 1.9).
		value := r[t+2]&0x80 != 0 && r[t+2]&ymxr.CountValue != 0
		if r[t+3] != 0 || value {
			count = int(r[t+3])
		}
		if r[t+2]&0x80 != 0 {
			selects = int(r[t+2]) & 7
			timer = r[t+2]&0x40 != 0
			place = r[t+2]&0x20 != 0
			deferred = r[t+2]&ymxr.Deferred != 0
		}
		// A start or remap can replace the kept mapping, and a start or
		// recut the kept cut. A remap and a recut preserve the source. A
		// stop ignores its mapping and cut columns; the check reports that
		// invalid combination (SPEC.md 1.10, 1.11, 6.4).
		mc := ymxr.MappingColumnOf(m.effects, i)
		xc := ymxr.CutColumnOf(m.effects, i)
		mapped := mc >= 0 && r[mc]&0x80 != 0
		cut := xc >= 0 && r[xc]&0x80 != 0
		stop := r[t+1] == 0x80
		mapping := was.Mapping
		if mapped && !stop {
			mapping = int(r[mc]) & 0x7F
		}
		kept := was.Cut
		if cut && !stop {
			kept = int(r[xc]) & 0x7F
		}
		set := (r[t]|r[t+1]|r[t+2])&0x80 != 0 || r[t+3] != 0 || mapped || cut
		m.Effect[i] = Effect{Target: target, Source: source, Select: selects,
			Count: count, Started: started, Set: set, Timer: timer, Place: place,
			Deferred: deferred, Mapping: mapping, Cut: kept}
	}
	m.EnvelopeWritten = false
	for i := range m.Written {
		m.Written[i] = -1
	}
	for c := 0; c < 13; c++ {
		if ymxr.BesideColumn[c] >= 0 {
			zero := r[ymxr.BesideColumn[c]]&byte(ymxr.BesideBit[c]) != 0
			if r[c] != 0 || zero {
				m.Registers[c] = int(r[c])
				m.Written[c] = m.Registers[c]
			}
		} else if r[c]&0x80 != 0 {
			m.Registers[c] = int(r[c]) & ymxr.Mask[c]
			m.Written[c] = m.Registers[c]
		}
	}
	if r[13]&0x80 != 0 {
		m.Registers[13] = int(r[13]) & 0x0F
		m.Written[13] = m.Registers[13]
		m.EnvelopeWritten = true
	}
}
