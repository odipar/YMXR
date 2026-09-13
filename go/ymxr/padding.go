package ymxr

import (
	"fmt"

	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxs/go/ymxs"
)

// Short is the loop length under which a loop of odd length is written
// again rather than padded: a frame added to a loop of a few rows
// lengthens every pass by a sixty-fourth or more, which is heard in a
// sweep or an arpeggio (experiments.md), and the rows written again cost
// a match a column.
const Short = 64

// Unset is a row that sets no register and leaves every timer running.
var Unset = ymxs.Row{}

// PadToUnit is tune with rows added so that its table packs at unit,
// each addition noted on said (SPEC.md 6, rule 6), and the frame of the
// dump each row of the table answers to: -1 for a row that sets no
// column, and the rows of a loop written again answer to its frames
// again. Row for row where the tune divided as it was.
//
// DTX packs a column in units of k bytes, so the row count divides by k
// and so does the repeat row (DTX R5.6, R5.11). A tune whose row count or
// repeat row does not divide packed at unit 1 instead, and a refill at
// unit 1 spans twice the rows of one at unit 2 and parses up to twice the
// blocks (experiments.md). So the table is lengthened. At the repeat row,
// rows that set no column go in until it divides, which moves the loop's
// first row later and lengthens what plays before it. Then, where the
// count does not divide, a loop of fewer than Short rows is written
// again until it does, and a longer loop, or a tune that plays once, gets
// rows that set no column at the end. A row that sets no column writes
// no register and leaves every timer running, so the tune plays one more
// frame there with its effects running through it; a loop written again
// plays as it did, since a pass plays the same rows. At most k minus one
// rows that set no column go in each place, and a tune that already
// divides is returned as it is.
func PadToUnit(tune ymxs.Tune, unit int, said *report.Report) (ymxs.Tune, []int) {
	rows := append([]ymxs.Row(nil), tune.Table.Rows...)
	frames := make([]int, len(rows))
	for i := range frames {
		frames[i] = i
	}
	at, repeats := tune.Table.Repeat()
	if unit > 1 && repeats && at%unit != 0 {
		added := unit - at%unit
		rows = append(rows[:at], append(unsetRows(added), rows[at:]...)...)
		frames = append(frames[:at], append(unsetFrames(added), frames[at:]...)...)
		said.Note(noted(added, at, true, unit))
		at += added
	}
	if unit > 1 && len(rows)%unit != 0 {
		loop := 0
		if repeats {
			loop = len(rows) - at
		}
		if loop > 0 && loop < Short {
			once := append([]ymxs.Row(nil), rows[at:]...)
			onceFrames := append([]int(nil), frames[at:]...)
			times := 1
			for len(rows)%unit != 0 {
				rows = append(rows, once...)
				frames = append(frames, onceFrames...)
				times++
			}
			said.Note(written(loop, times, unit))
		} else {
			added := unit - len(rows)%unit
			said.Note(noted(added, len(rows), false, unit))
			rows = append(rows, unsetRows(added)...)
			frames = append(frames, unsetFrames(added)...)
		}
	}
	if len(rows) == len(tune.Table.Rows) {
		return tune, frames
	}
	var table ymxs.Table[ymxs.Row]
	if repeats {
		table = ymxs.Repeating(rows, at)
	} else {
		table = ymxs.Once(rows)
	}
	return ymxs.Tune{Title: tune.Title, Composer: tune.Composer, Writer: tune.Writer,
		Rate: tune.Rate, Table: table}, frames
}

func unsetRows(n int) []ymxs.Row {
	out := make([]ymxs.Row, n)
	for i := range out {
		out[i] = Unset
	}
	return out
}

func unsetFrames(n int) []int {
	out := make([]int, n)
	for i := range out {
		out[i] = -1
	}
	return out
}

// noted is the note for rows that set no column, which the Java tree
// writes word for word.
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

// written is the note for a loop written again, which the Java tree
// writes word for word.
func written(loop, times, unit int) string {
	rows := " row"
	if loop != 1 {
		rows = " rows"
	}
	how := "twice"
	if times != 2 {
		how = fmt.Sprintf("%d times", times)
	}
	return fmt.Sprintf("padded: the loop's %d%s written %s, so the table packs at unit %d",
		loop, rows, how, unit)
}
