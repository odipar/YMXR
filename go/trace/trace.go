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

// Header writes the record's header, including transformation entries
// after sources in version 10 (SPEC.md 7.2).
func Header(tune ymxr.File) string {
	var line bytes.Buffer
	fmt.Fprintf(&line, "{\"rate\":%d,\"effects\":%d,\"sources\":[", tune.FrameRate,
		tune.Effects&0xFF)
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
	line.WriteByte(']')
	if ymxr.HasMapping(tune.Effects) {
		line.WriteString(",\"transformations\":[")
		for n, entries := range tune.Transformations {
			if n > 0 {
				line.WriteByte(',')
			}
			line.WriteByte('[')
			for k, entry := range entries {
				if k > 0 {
					line.WriteByte(',')
				}
				fmt.Fprintf(&line, "%d", entry)
			}
			line.WriteByte(']')
		}
		line.WriteByte(']')
	}
	if ymxr.HasCuts(tune.Effects) {
		line.WriteString(",\"cuts\":[")
		for n, one := range tune.Cuts {
			if n > 0 {
				line.WriteByte(',')
			}
			fmt.Fprintf(&line, "{\"first\":%d,\"last\":%d}", one.First, one.Last)
		}
		line.WriteByte(']')
	}
	line.WriteString("}\n")
	return line.String()
}

// Trace writes the header and up to calls frame entries, one per line.
// A frame reporting -1 ends the record. Effect entries of versions 10 and
// 11 report the kept mapping, and of version 11 the kept cut; ticks and a
// pending start's mapping and cut are outside the record (SPEC.md 7.3).
func Trace(tune ymxr.File, calls int) []byte {
	var out bytes.Buffer
	out.WriteString(Header(tune))
	table := tune.Table
	model := Of(table, tune.Effects)
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
		out.WriteByte('}')
		// SPEC.md 7.3: in version 13 the voices the row syncs, after w,
		// where it syncs one.
		if model.Syncs != 0 {
			out.WriteString(",\"s\":[")
			first = true
			for v := 0; v < 3; v++ {
				if model.Syncs&(1<<v) != 0 {
					if !first {
						out.WriteByte(',')
					}
					fmt.Fprintf(&out, "\"%c\"", 'A'+v)
					first = false
				}
			}
			out.WriteByte(']')
		}
		out.WriteString(",\"e\":{")
		first = true
		for i := 0; i < 4; i++ {
			e := model.Effect[i]
			if !e.Set {
				continue
			}
			if !first {
				out.WriteByte(',')
			}
			fmt.Fprintf(&out, "\"%d\":{\"target\":%d,\"source\":%d,\"select\":%d,"+
				"\"count\":%d,\"timer\":%t,\"place\":%t,\"deferred\":%t", i, e.Target,
				e.Source, e.Select, e.Count, e.Timer, e.Place, e.Deferred)
			if tune.Version >= ymxr.Version {
				fmt.Fprintf(&out, ",\"mapping\":%d", e.Mapping)
			}
			if tune.Version >= ymxr.VersionCut {
				fmt.Fprintf(&out, ",\"cut\":%d", e.Cut)
			}
			out.WriteByte('}')
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
