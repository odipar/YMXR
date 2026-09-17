// Package ymxr is the format: the thirty columns of a tune, the sources
// its effects run, the tune file those are written to, and the multi file
// several tune files travel in.
package ymxr

// C is the column count, and a row's bytes (SPEC.md 1).
const C = 30

// Effect is the first effect's target column; four columns an effect.
const Effect = 14

// The two resets in a control column (SPEC.md 1.9).
const (
	TimerReset = 0x40
	PlaceReset = 0x20
)

// CountValue is bit 4 of a control column: the count column beside it is
// 0, and that 0 is the value the MFP counts 256 for (SPEC.md 1.1, 1.9).
const CountValue = 0x10

// Mask is the register bits a YM dump uses for its flags, masked off.
var Mask = [14]int{0xFF, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0x1F, 0x3F,
	0x1F, 0x1F, 0x1F, 0xFF, 0xFF, 0x0F}

// Marker is the column of a source's row the marker stands in, a target
// (SPEC.md 2.1): the column whose register reads seven bits or fewer, and
// -1 for a target this version does not encode. Targets 0 to 13 write one
// register, so the marker stands in their one column where the register
// leaves bit 7; 14 to 19 mark the coarse nibble, 21 the envelope shape,
// and 22 to 24 the noise period.
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

// Prescaler is what each select divides that clock by (SPEC.md 1.9).
var Prescaler = [8]int{0, 4, 10, 16, 50, 64, 100, 200}

// Columns is a tune's thirty columns, one byte a column a frame: the
// columns themselves, the row the tune repeats to, and the effects the
// tune runs.
//
// Schema writes them from a YMXS structure, which the ym package reads a
// dump into (doc/ymxs.md).
type Columns struct {
	// Column[c][frame].
	Column [][]byte

	// Repeat is the row the tune repeats to, R where it does not.
	Repeat int

	// Effects is bits 3 to 0: the effects the tune ever runs.
	Effects int
}

// Duration is the frames a source of that many rows runs for at a rate,
// rounded up, with a sixteenth of a frame added for a start that falls
// inside the frame it begins in.
func Duration(rows, selects, count, frameRate int) int {
	divisor := int64(Prescaler[selects]) * int64(count)
	scaled := int64(rows)*divisor*int64(frameRate) + MFP/16
	return int((scaled + MFP - 1) / MFP)
}

// Name is a column's name in a report: a register by its number,
// and an effect column by its effect and its part of it.
func Name(c int) string {
	if c < Effect {
		return "R" + itoa(c)
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
