// Command ymxr-trace reads a tune file on standard input and writes its
// record on standard output, the first line and one line a row, -rROWS
// rows or one pass and the loop once without.
package main

import (
	"fmt"
	"os"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/trace"
	"github.com/odipar/ymxr/go/ymxr"
)

func main() {
	t, args := tool.Of("ymxr-trace", os.Args[1:], flags.Rows...)
	flags.Only(t, args, flags.Rows)
	calls := int(flags.RowsOf(t, args, -1))
	said := report.Of(t.Reports())
	tune := t.Bytes()
	if ymxr.IsMulti(tune) {
		t.Wrong(tool.Wrong, "this is a multi file of several tunes, and a record is of"+
			" one tune")
	}
	// A file this reader does not read produces no record, and reports why
	// (SPEC.md 6, R6.1), so the report reads the header under the same
	// guard rather than failing where the record would not.
	if file, err := ymxr.Read(tune); err == nil {
		said.Say(fmt.Sprintf("the tune file: %d bytes", len(tune)))
		said.Row("the table", fmt.Sprintf("%d rows of %d columns, repeating at row %d",
			file.Table.Rows(), file.Table.Columns(), file.Table.Repeat()))
		said.Row("the frame rate", fmt.Sprintf("%d Hz", file.FrameRate))
		said.Row("the sources", fmt.Sprintf("%d", len(file.Sources)))
		if calls < 0 {
			said.Row("the rows to record", "one pass and the loop once")
		} else {
			said.Row("the rows to record", fmt.Sprintf("%d", calls))
		}
	} else {
		said.Say(fmt.Sprintf("the tune file: %d bytes, which this reader does not read: %s",
			len(tune), err.Error()))
	}
	rows := trace.Record(tune, calls)
	if len(rows) == 0 {
		// A file this reader does not read produces no record (R6.1), and
		// a caller reading the exit is told so.
		t.Wrong(tool.Wrong, "no record: this reader does not read the file")
	}
	t.Report(fmt.Sprintf("%d bytes of rows", len(rows)))
	t.WriteBytes(rows)
}
