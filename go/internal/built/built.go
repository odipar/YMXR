// Package built is a tune of version 10 built as columns, which the tests
// of the Go tree read, as the Java tree's tests read BuiltTunes.
package built

import (
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/ymxr"
)

// Fade is transformation 1 of Mapped: sixteen entries with entry k = 15
// - k.
func Fade() []byte {
	out := make([]byte, 16)
	for k := range out {
		out[k] = byte(15 - k)
	}
	return out
}

// Half is transformation 2 of Mapped: three entries with entry k =
// floor(k / 2).
func Half() []byte {
	return []byte{0, 0, 1}
}

// Mapped is a six-row version 10 fixture played once. Effect 0 starts
// source 1 on setR8 through Fade, remaps to Half, starts with its
// mapping column unset, stops with that column incorrectly set, then
// remaps to identity. The invalid stop checks that the player ignores
// its mapping column.
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

// Unmapped removes the mapping columns and transformations from Mapped,
// producing a version 9 fixture.
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
