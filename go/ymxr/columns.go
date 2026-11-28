// Package ymxr defines tune columns, effect sources, optional
// transformations, tune files and multi files (SPEC.md 1 to 3).
package ymxr

// C is the column count of versions 7 to 9, and a row's bytes (SPEC.md 1).
const C = 30

// MappingColumn is the first mapping column. Effect i uses column 30 +
// i in version 10 (SPEC.md 1.10).
const MappingColumn = 30

// Mapped is the version 10 column count, including four mapping columns
// (SPEC.md 3.3.5).
const Mapped = 34

// Maps is effects-used bit 5: version 10 mapping columns and
// transformation block are present (SPEC.md 3.3).
const Maps = 0x20

// Cuts is effects-used bit 6: version 11, its cut columns and cut index
// (SPEC.md 3.3).
const Cuts = 0x40

// Copies is effects-used bit 7: version 12, a row starts copy from frame,
// the last entry of the source index (SPEC.md 3.1.7, 3.3).
const Copies = 0x80

// Syncs is a bit above the effects byte, which the file leaves out: a row
// syncs a voice, at its frame or through a target of a sync, which version
// 13 marks (SPEC.md 1.12, 3.3.5).
const Syncs = 0x100

// Sync is bit 5 of a coarse column, 1, 3 or 5, in version 13: the row syncs
// the voice, read on every row (SPEC.md 1.2.2, 1.12).
const Sync = 0x20

// Evens is a bit above the effects byte, which the file leaves out: a tune
// file of version 14, whose bit 4 of a coarse column is a sync length of 2,
// read on every row (SPEC.md 1.12.2, 3.3.5).
const Evens = 0x200

// Even is bit 4 of a coarse column, 1, 3 or 5, in version 14: the voice's
// sync length in the row is 2, and 1 where the bit is 0 (SPEC.md 1.2.2,
// 1.12.2).
const Even = 0x10

// Lengths is a bit above the effects byte, which the file leaves out: a row
// has a sync length above 1, and the table has the three sync length
// columns, which version 15 marks (SPEC.md 1.12.2, 3.3.5).
const Lengths = 0x400

// SyncTarget is the first target of a sync, syncToneA; 26 and 27 are
// syncToneB and syncToneC (SPEC.md 2.1).
const SyncTarget = 25

// CutColumn is the first cut column where the tune maps a value, and All
// the column count of a table that maps a value and cuts a source
// (SPEC.md 3.3.5). Where the tune maps none, the cut columns are 30 to 33.
const (
	CutColumn = 34
	All       = 38
)

// LengthColumn is the first sync length column in the layout of 41 a writer
// writes the rows in, voice A's, before the groups a tune leaves unused are
// dropped; and Whole is that layout's column count (SPEC.md 1.12.2).
const (
	LengthColumn = 38
	Whole        = 41
)

// MostSyncLength is the most steps a sync keeps a voice's period at 0: a
// sync length column is the length less 1 in four bits (SPEC.md 1.12.2).
const MostSyncLength = 16

// MostCuts is the cut limit: cut numbers 1 to 127 select a cut of the
// index; 0 is the whole table (SPEC.md 1.11).
const MostCuts = 127

// MostCountedRows is the rows the counted handler counts, in a word: of a
// cut, and of a source that fills its byte (SPEC.md section 8).
const MostCountedRows = 65535

// MostWideRows is the rows of a source of two or three columns: a player
// keeps the stride of its columns, align(R), in a word (SPEC.md 3.1.3,
// section 8).
const MostWideRows = 65534

// Cut is a cut of the index: its first row and its last row (SPEC.md
// 3.5).
type Cut struct {
	First, Last int
}

// HasMapping is whether the effects byte marks the mapping columns, bit 5.
func HasMapping(effects int) bool {
	return effects&Maps != 0
}

// HasCuts is whether the effects byte marks the cut columns, bit 6.
func HasCuts(effects int) bool {
	return effects&Cuts != 0
}

// HasCopies is whether the effects byte marks copy from frame, bit 7.
func HasCopies(effects int) bool {
	return effects&Copies != 0
}

// HasSyncs is whether a row syncs a voice, at its frame or through a target
// of a sync: the bit above the effects byte (SPEC.md 3.3.5).
func HasSyncs(effects int) bool {
	return effects&Syncs != 0
}

// HasEvens is whether the file is of version 14, whose bit 4 of a coarse
// column is a sync length: the bit above the effects byte that version 14
// marks (SPEC.md 3.3.5).
func HasEvens(effects int) bool {
	return effects&Evens != 0
}

// HasLengths is whether the table has the sync length columns: the bit
// above the effects byte that version 15 marks (SPEC.md 1.12.2, 3.3.5).
func HasLengths(effects int) bool {
	return effects&Lengths != 0
}

// ColumnCount is the column count of a table of that effects byte: 30,
// with four mapping columns where bit 5 is 1, four cut columns where bit 6
// is 1 and three sync length columns in version 15 (SPEC.md 3.3.5).
func ColumnCount(effects int) int {
	count := C
	if HasMapping(effects) {
		count += 4
	}
	if HasCuts(effects) {
		count += 4
	}
	if HasLengths(effects) {
		count += 3
	}
	return count
}

// LengthColumnOf is voice v's sync length column, after the mapping and
// the cut columns, or -1 where the table has none (SPEC.md 1.12.2).
func LengthColumnOf(effects, v int) int {
	if !HasLengths(effects) {
		return -1
	}
	return ColumnCount(effects) - 3 + v
}

// MappingColumnOf is effect i's mapping column, 30 + i, or -1 where the
// table has no mapping columns (SPEC.md 1.10).
func MappingColumnOf(effects, i int) int {
	if !HasMapping(effects) {
		return -1
	}
	return MappingColumn + i
}

// CutColumnOf is effect i's cut column, 30 + i where the tune maps no value
// and 34 + i where it maps one, or -1 where the table has no cut columns
// (SPEC.md 1.11.1).
func CutColumnOf(effects, i int) int {
	if !HasCuts(effects) {
		return -1
	}
	if HasMapping(effects) {
		return CutColumn + i
	}
	return MappingColumn + i
}

// MostTransformations is the transformation limit: numbers 1 to 127
// select lookups; 0 selects identity (SPEC.md 1.10).
const MostTransformations = 127

// MostEntries is the entry-count limit for a transformation: one entry
// per byte key (SPEC.md 3.4).
const MostEntries = 256

// Effect is the first effect's target column; four columns an effect.
const Effect = 14

// The two resets in a control column (SPEC.md 1.9).
const (
	TimerReset = 0x40
	PlaceReset = 0x20
)

// Deferred is bit 3 of a control column, a deferred start (SPEC.md 1.9,
// 4.8).
const Deferred = 0x08

// Defers is bit 4 of the effects used: a row defers a start (SPEC.md 3.3).
const Defers = 0x10

// CountValue is bit 4 of a control column: the count column beside it is
// 0, and that 0 is the value the MFP counts 256 for (SPEC.md 1.1, 1.9).
const CountValue = 0x10

// Mask is the register bits a YM dump uses for its flags, masked off.
var Mask = [14]int{0xFF, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0x1F, 0x3F,
	0x1F, 0x1F, 0x1F, 0xFF, 0xFF, 0x0F}

// Ports is bits 7 and 6 of R7, the directions of the two I/O ports, which
// an Atari ST writes as 1 (SPEC.md 1.4.2). A row's write reads them from
// the player; a tick of a counted source on setR7 writes the row whole,
// so a writer sets them in every row of such a source (rule 2(f)).
const Ports = 0xC0

// MixerTarget is the target whose register is R7, the mixer (SPEC.md 2.1).
const MixerTarget = 7

// MostMixerKey is the largest setR7 lookup key. Keys use bits 5 to 0;
// stored key k is k + 192, with both port-direction bits set (SPEC.md
// 6, rule 2(f)).
const MostMixerKey = 0xFF &^ Ports

// Marker is the column of a source's row the marker is in, a target
// (SPEC.md 2.1): the column whose register reads seven bits or fewer, and
// -1 where every column writes a register that reads all eight, which is
// a counted source (3.1.6). Targets 0 to 13 write one register, so the
// marker is in their one column where the register leaves bit 7 and
// the source is counted where it does not; 14 to 19 mark the coarse
// nibble, 20, setEnvelope, writes two whole bytes and is counted, 21
// marks the envelope shape, 22 to 24 the noise period, and 25 to 27, the
// targets of a sync, the coarse nibble.
var Marker = []int{-1, 0, -1, 0, -1, 0, 0, -1, 0, 0, 0, -1, -1, 0,
	1, 1, 1, 1, 1, 1, -1, 2, 0, 0, 0, 1, 1, 1}

// BesideColumn and BesideBit are, for a column that fills its byte, the
// column and bit beside it that keep its 0 a value (SPEC.md 1.1).
var (
	BesideColumn = [13]int{1, -1, 3, -1, 5, -1, -1, -1, -1, -1, -1, 13, 13}
	BesideBit    = [13]int{0x40, 0, 0x40, 0, 0x40, 0, 0, 0, 0, 0, 0, 0x40, 0x20}
)

// MFP is the timer chip's clock, in ticks a second.
const MFP = 2457600

// Prescaler is the number each select divides that clock by (SPEC.md 1.9).
var Prescaler = [8]int{0, 4, 10, 16, 50, 64, 100, 200}

// Columns contains a tune's columns, repeat row, effects used, optional
// transformations and cuts. Version 9 has thirty columns; version 10 adds
// four mapping columns, and version 11 four cut columns after the mapping
// columns where the tune has them (SPEC.md 1.10, 1.11, 3.4, 3.5). Schema
// encodes these from a YMXS tune; the ym package imports a dump into that
// structure.
type Columns struct {
	// Column[c][frame].
	Column [][]byte

	// Repeat is the row the tune repeats to, R where it does not.
	Repeat int

	// Effects records effects used: bits 3 to 0 select effects, bit 4
	// marks deferred starts, and bit 5 marks version 10 mapping columns
	// (SPEC.md 3.3).
	Effects int

	// Transformations contains optional lookup tables: transformation n is
	// at index n - 1 and key k selects entry k. Empty with C columns
	// (SPEC.md 3.4).
	Transformations [][]byte

	// Cuts is the cut index: cut n is at index n - 1, and empty below
	// version 11 (SPEC.md 3.5).
	Cuts []Cut
}

// Duration is the frames a source of that many rows runs for at a rate,
// rounded up, with a sixteenth of a frame added for a start that falls
// inside the frame it begins in. A count of 0 is the 256 the MFP counts
// (SPEC.md 1.9.1, 6.4).
func Duration(rows, selects, count, frameRate int) int {
	counted := count
	if counted == 0 {
		counted = 256
	}
	divisor := int64(Prescaler[selects]) * int64(counted)
	scaled := int64(rows)*divisor*int64(frameRate) + MFP/16
	return int((scaled + MFP - 1) / MFP)
}

// NameIn is a column's name in the table of that effects byte, whose cut
// columns are 30 to 33 where it has no mapping columns (SPEC.md 3.3.5).
func NameIn(c, effects int) string {
	if c >= MappingColumn && HasCuts(effects) && !HasMapping(effects) {
		return "effect " + itoa(c-MappingColumn) + " cut"
	}
	return Name(c)
}

// Name is a column's name in a report, in the layout of a table that maps a
// value and cuts a source: a register by its number, and an effect column
// by its effect and its part of it.
func Name(c int) string {
	if c < Effect {
		return "R" + itoa(c)
	}
	if c >= CutColumn {
		return "effect " + itoa(c-CutColumn) + " cut"
	}
	if c >= MappingColumn {
		return "effect " + itoa(c-MappingColumn) + " mapping"
	}
	effect := (c - Effect) / 4
	part := ""
	switch (c - Effect) % 4 {
	case 0:
		part = "target"
	case 1:
		part = "source"
	case 2:
		part = "timer control"
	default:
		part = "timer count"
	}
	return "effect " + itoa(effect) + " " + part
}

func itoa(n int) string {
	if n < 10 {
		return string(rune('0' + n))
	}
	return string(rune('0'+n/10)) + string(rune('0'+n%10))
}
