// Package built is a tune of version 10 built as columns, which the tests
// of the Go tree read, as the Java tree's tests read BuiltTunes.
package built

import (
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/ymxr"
)

// Fade is transformation 1 of Mapped: sixteen entries, entry k 15 - k.
func Fade() []byte {
	out := make([]byte, 16)
	for k := range out {
		out[k] = byte(15 - k)
	}
	return out
}

// Half is transformation 2 of Mapped: three entries, each key halved.
func Half() []byte {
	return []byte{0, 0, 1}
}

// Mapped is the columns of a tune of version 10: on effect 0, a start of
// source 1 on setR8 through the fade at row 0, a remap to half at row 1,
// a start at row 2 that leaves the mapping column unset, a stop at row 3
// that sets it, and a remap to the identity at row 4; six rows, played
// once.
func Mapped() ymxr.Columns {
	frames := 6
	c := make([][]byte, ymxr.Mapped)
	for i := range c {
		c[i] = make([]byte, frames)
	}
	e0 := ymxr.Effect
	m0 := ymxr.MappingColumn
	c[e0][0] = 0x80 | 8
	c[e0+1][0] = 0x80 | 1
	c[e0+2][0] = 0x80 | ymxr.TimerReset | ymxr.PlaceReset | 1
	c[e0+3][0] = 100
	c[m0][0] = 0x80 | 1
	c[m0][1] = 0x80 | 2
	c[e0+1][2] = 0x80 | 1
	c[e0+1][3] = 0x80
	c[m0][3] = 0x80 | 1
	c[m0][4] = 0x80
	return ymxr.Columns{Column: c, Repeat: frames, Effects: 0x01 | ymxr.Maps,
		Transformations: [][]byte{Fade(), Half()}}
}

// Unmapped is the columns of Mapped without the mapping columns, version
// 9.
func Unmapped() ymxr.Columns {
	columns := Mapped()
	columns.Column = columns.Column[:ymxr.C]
	columns.Effects = 0x01
	columns.Transformations = nil
	return columns
}

// Sources is the one source of either: two rows of R8, the marker on the
// second.
func Sources() *ymxr.Sources {
	return ymxr.Passed([]ymxr.Source{ymxr.SourceOf(ymxr.SID, 0, []byte{12, 0x80}, 0)})
}

// File is the tune file of the columns at 50 Hz, unit 1 and the ring the
// writer chooses, named.
func File(columns ymxr.Columns, name string) ([]byte, error) {
	out, err := ymxr.Pack(columns, Sources(), 50, 1, ymxr.Choose, ymxr.Plain(),
		report.Quiet(), name)
	if err != nil {
		return nil, err
	}
	return out.File, nil
}
