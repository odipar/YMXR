package ymxr

import (
	"fmt"

	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxs/go/ymxs"
)

// Unset is a row that sets no register and leaves every timer running.
var Unset = ymxs.Row{}

// PadToUnit is tune with rows added so that its table packs at unit,
// each addition noted on said (SPEC.md 6, rule 6), and the rows of the
// table that were added, in order: no frame of a dump answers to one.
//
// DTX packs a column in units of k bytes, so the row count divides by k
// and so does the repeat row (DTX R5.6, R5.11). A tune whose row count or
// repeat row does not divide packed at unit 1 instead, and a refill at
// unit 1 spans twice the rows of one at unit 2 and parses up to twice the
// blocks (experiments.md). So rows that set no column go in: at the
// repeat row until it divides, which moves the loop's first row later and
// lengthens what plays before it, then at the end until the count
// divides. A row that sets no column writes no register and leaves every
// timer running, so the tune plays one more frame there with its effects
// running through it. At most k minus one rows go in each place, and a
// tune that already divides is returned as it is.
func PadToUnit(tune ymxs.Tune, unit int, said *report.Report) (ymxs.Tune, []int) {
	if unit <= 1 {
		return tune, nil
	}
	rows := append([]ymxs.Row(nil), tune.Table.Rows...)
	at, repeats := tune.Table.Repeat()
	var where []int
	if repeats && at%unit != 0 {
		added := unit - at%unit
		rows = append(rows[:at], append(unsetRows(added), rows[at:]...)...)
		for i := 0; i < added; i++ {
			where = append(where, at+i)
		}
		said.Note(noted(added, at, true, unit))
		at += added
	}
	if len(rows)%unit != 0 {
		added := unit - len(rows)%unit
		said.Note(noted(added, len(rows), false, unit))
		for i := 0; i < added; i++ {
			where = append(where, len(rows)+i)
		}
		rows = append(rows, unsetRows(added)...)
	}
	if len(where) == 0 {
		return tune, nil
	}
	var table ymxs.Table[ymxs.Row]
	if repeats {
		table = ymxs.Repeating(rows, at)
	} else {
		table = ymxs.Once(rows)
	}
	return ymxs.Tune{Title: tune.Title, Composer: tune.Composer, Writer: tune.Writer,
		Rate: tune.Rate, Table: table}, where
}

func unsetRows(n int) []ymxs.Row {
	out := make([]ymxs.Row, n)
	for i := range out {
		out[i] = Unset
	}
	return out
}

// noted is the note, which the Java tree writes word for word.
func noted(added, at int, beforeRepeat bool, unit int) string {
	rows := " unset row"
	if added != 1 {
		rows = " unset rows"
	}
	before := ""
	if beforeRepeat {
		before = ", before the repeat row"
	}
	return fmt.Sprintf("padded: %d%s at row %d%s, so the table packs at unit %d",
		added, rows, at, before, unit)
}
