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
// marks the envelope shape, and 22 to 24 the noise period.
var Marker = []int{-1, 0, -1, 0, -1, 0, 0, -1, 0, 0, 0, -1, -1, 0,
	1, 1, 1, 1, 1, 1, -1, 2, 0, 0, 0}

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

// Columns contains a tune's columns, repeat row, effects used and
// optional transformations. Version 9 has thirty columns; version 10
// adds four mapping columns (SPEC.md 1.10, 3.4). Schema encodes these
// from a YMXS tune; the ym package imports a dump into that structure.
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

// Name is a column's name in a report: a register by its number, and an
// effect column by its effect and its part of it.
func Name(c int) string {
	if c < Effect {
		return "R" + itoa(c)
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
