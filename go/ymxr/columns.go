// Package ymxr is the format: the thirty columns of a tune, the sources
// its effects run, the tune file those are written to, and the multi file
// several tune files travel in.
package ymxr

// C is the column count, and a row's bytes (SPEC.md 1).
const C = 30

// Effect is the first effect's target column; four columns an effect.
const Effect = 14

// The two resets a control column carries (SPEC.md 1.9).
const (
	TimerReset = 0x40
	PlaceReset = 0x20
)

// Mask is the register bits a YM dump uses for its flags, masked off.
var Mask = [14]int{0xFF, 0x0F, 0xFF, 0x0F, 0xFF, 0x0F, 0x1F, 0x3F,
	0x1F, 0x1F, 0x1F, 0xFF, 0xFF, 0x0F}

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
// Schema writes them from a YMXS structure, which the ym and ymx packages
// read a dump into (doc/ymxs.md).
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
// inside the frame it begins in: the reckoning YMX's player was measured
// against.
func Duration(rows, selects, count, frameRate int) int {
	divisor := int64(Prescaler[selects]) * int64(count)
	scaled := int64(rows)*divisor*int64(frameRate) + MFP/16
	return int((scaled + MFP - 1) / MFP)
}

// Name is what a column is, for a reported row: a register by its number,
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
