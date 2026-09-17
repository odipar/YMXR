package trace

import (
	"bytes"
	"fmt"

	"github.com/odipar/dtx/go/dtx"

	"github.com/odipar/ymxr/go/ymxr"
)

// Calls is the calls the kit uses for a tune: one pass and the loop once,
// or the pass and the call that reports its end.
func Calls(table *dtx.Table) int {
	rows := table.Rows()
	repeat := table.Repeat()
	if repeat < rows {
		return rows + rows - repeat
	}
	return rows + 1
}

// Header is the record's first line: the tune's fixed values.
func Header(tune ymxr.File) string {
	var line bytes.Buffer
	fmt.Fprintf(&line, "{\"rate\":%d,\"effects\":%d,\"sources\":[", tune.FrameRate,
		tune.Effects)
	for i, s := range tune.Sources {
		if i > 0 {
			line.WriteByte(',')
		}
		line.WriteString("{\"rows\":[")
		// a byte a column of the row, row 0's columns first (SPEC.md 7.2)
		for r := 0; r < s.Rows(); r++ {
			for c := 0; c < s.Columns(); c++ {
				if r > 0 || c > 0 {
					line.WriteByte(',')
				}
				fmt.Fprintf(&line, "%d", s.Column(c)[r])
			}
		}
		fmt.Fprintf(&line, "],\"repeat\":%d}", s.Repeat())
	}
	line.WriteString("]}\n")
	return line.String()
}

// Trace is the record: the first line, then that many entries of the tune,
// one a line; a frame reporting -1 ends it.
func Trace(tune ymxr.File, calls int) []byte {
	var out bytes.Buffer
	out.WriteString(Header(tune))
	table := tune.Table
	model := Of(table)
	for call := 0; call < calls; call++ {
		if model.Row() == table.Rows() {
			out.WriteString("{\"result\":-1}\n")
			return out.Bytes()
		}
		model.Step()
		out.WriteString("{\"result\":0,\"w\":{")
		first := true
		for c := 0; c < 14; c++ {
			if model.Written[c] >= 0 {
				if !first {
					out.WriteByte(',')
				}
				fmt.Fprintf(&out, "\"%d\":%d", c, model.Written[c])
				first = false
			}
		}
		out.WriteString("},\"e\":{")
		first = true
		for i := 0; i < 4; i++ {
			e := model.Effect[i]
			if !e.Touched {
				continue
			}
			if !first {
				out.WriteByte(',')
			}
			fmt.Fprintf(&out, "\"%d\":{\"target\":%d,\"source\":%d,\"select\":%d,"+
				"\"count\":%d,\"timer\":%t,\"place\":%t}", i, e.Target, e.Source,
				e.Select, e.Count, e.Timer, e.Place)
			first = false
		}
		out.WriteString("}}\n")
	}
	return out.Bytes()
}

// Record is the record of a tune file as bytes, and no byte for a file
// whose version is not the one this reads (R6.1).
func Record(file []byte, calls int) []byte {
	tune, err := ymxr.Read(file)
	if err != nil {
		return nil
	}
	if calls < 0 {
		calls = Calls(tune.Table)
	}
	return Trace(tune, calls)
}
