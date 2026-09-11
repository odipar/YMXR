// Package trace is a reader's report of a tune (SPEC.md 7): one line a
// play call, the call's result, the registers the frame writes and the
// effects the row touched. The kit's references are the output of this,
// and the rig checks the 68000 player to it.
package trace

import (
	"github.com/odipar/dtx/go/dtx"

	"github.com/odipar/ymxr/go/ymxr"
)

// The frame procedure of SPEC.md section 4, as a model: what the fourteen
// registers are after each row, and what each effect runs. A reader in the
// sense of R2.4, writing to no chip.
//
// A register the model has not seen set is -1, and so is a register an
// effect runs on, from the row after the one that starts it. The effects'
// ticks are not modelled: a running effect's source, target and rate are
// what the rows gave, and its place is not followed.
//
// What the last step wrote stands beside the state: Written reports each
// register's value where the row wrote it and -1 where not, and each
// effect reports whether the row touched it and which control bits the row
// set.

// Effect is what one effect runs after a row: source 0 where it runs no
// source. Touched says the row set one of its columns, Timer that the
// row's control column had bit 6 and Place bit 5.
type Effect struct {
	Target  int
	Source  int
	Select  int
	Count   int
	Started bool
	Touched bool
	Timer   bool
	Place   bool
}

// Replay is the model over one table.
type Replay struct {
	Registers       [14]int
	Written         [14]int
	Effect          [4]Effect
	EnvelopeWritten bool

	table *dtx.Table
	row   int
}

// Of is a model at the table's first row.
func Of(table *dtx.Table) *Replay {
	out := &Replay{table: table}
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
	var r [ymxr.C]byte
	for c := 0; c < ymxr.C; c++ {
		r[c] = m.table.Column(c)[m.row]
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
		}
		touched := (r[t]|r[t+1]|r[t+2])&0x80 != 0 || r[t+3] != 0
		m.Effect[i] = Effect{Target: target, Source: source, Select: selects,
			Count: count, Started: started, Touched: touched, Timer: timer, Place: place}
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
