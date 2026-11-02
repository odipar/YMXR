package schema_test

import (
	"bytes"
	"testing"

	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/schema"
	"github.com/odipar/ymxr/go/ymxr"
	"github.com/odipar/ymxs/go/ymxs"
)

// unread is the line of SPEC.md 6.5 for row 1, the row of the effect tune
// builds after a start at row 0.
const unread = "row 1: an effect this version does not read"

var square = ymxs.RepeatingSource("square 12", []int{12, 0}, 0)

// tune is a start of the square on R8 at row 0, then the effect at row 1.
func tune(t *testing.T, then ymxs.Effect) ymxs.Tune {
	t.Helper()
	rows := []ymxs.Row{
		{Effects: map[ymxs.Timer]ymxs.Effect{ymxs.TimerA: struck(t, ymxs.Mapping{})}},
		{Effects: map[ymxs.Timer]ymxs.Effect{ymxs.TimerA: then}},
	}
	return ymxs.Tune{Rate: 50, Table: ymxs.Repeating(rows, 0)}
}

// struck is a struck note of the square on R8, through the mapping.
func struck(t *testing.T, mapping ymxs.Mapping) ymxs.Start {
	t.Helper()
	prescaler, err := ymxs.PrescalerBy(4)
	if err != nil {
		t.Fatal(err)
	}
	start := ymxs.Struck(ymxs.Setting(ymxs.R8), square, prescaler, 100)
	start.Mapping = mapping
	return start
}

func reported(t *testing.T, tune ymxs.Tune) string {
	t.Helper()
	_, err := schema.Of(tune)
	if err == nil {
		t.Fatal("the tune is mapped onto the columns, and its effect at row 1 is an error")
	}
	return err.Error()
}

func TestAStartThroughATransformationIsAnUnreadEffect(t *testing.T) {
	half := ymxs.Transformation{Name: "half", Entries: []int{0, 0, 1, 1, 2, 2, 3, 3, 4,
		4, 5, 5, 6, 6, 7, 7}}
	said := reported(t, tune(t, struck(t, ymxs.Mapped(half))))
	if said != unread {
		t.Fatalf("a start through a transformation reports %q, and the line is %q",
			said, unread)
	}
}

func TestARemapIsAnUnreadEffect(t *testing.T) {
	said := reported(t, tune(t, ymxs.Remap{}))
	if said != unread {
		t.Fatalf("a remap to the identity reports %q, and the line is %q", said, unread)
	}
}

// A mapping of identity parts is the identity, so the start through it
// writes the columns of a start through the mapping of no parts.
func TestAStartThroughIdentityPartsWritesTheColumnsOfNoParts(t *testing.T) {
	parts, err := schema.Of(tune(t, struck(t, ymxs.Mapped(ymxs.Identity{}))))
	if err != nil {
		t.Fatal(err)
	}
	none, err := schema.Of(tune(t, struck(t, ymxs.Mapping{})))
	if err != nil {
		t.Fatal(err)
	}
	for c := range none.Columns.Column {
		if !bytes.Equal(parts.Columns.Column[c], none.Columns.Column[c]) {
			t.Fatalf("column %d reads %v through an identity part, and %v through"+
				" the mapping of no parts", c, parts.Columns.Column[c], none.Columns.Column[c])
		}
	}
	if parts.Columns.Effects != none.Columns.Effects {
		t.Fatalf("the effects word reads %d through an identity part, and %d through"+
			" the mapping of no parts", parts.Columns.Effects, none.Columns.Effects)
	}
}

// A start through a transformation at row 3 of four rows that repeat to
// row 1: padding to a unit of 2 adds a row before row 1, and the line
// numbers the row of the structure.
func TestTheRowOfAnErrorIsTheRowOfTheStructure(t *testing.T) {
	half := ymxs.Transformation{Name: "half", Entries: []int{0, 0, 1, 1, 2, 2, 3, 3, 4,
		4, 5, 5, 6, 6, 7, 7}}
	rows := []ymxs.Row{
		{Effects: map[ymxs.Timer]ymxs.Effect{ymxs.TimerA: struck(t, ymxs.Mapping{})}},
		{},
		{},
		{Effects: map[ymxs.Timer]ymxs.Effect{ymxs.TimerA: struck(t, ymxs.Mapped(half))}},
	}
	padded, frames := ymxr.PadToUnit(ymxs.Tune{Rate: 50, Table: ymxs.Repeating(rows, 1)},
		2, report.Quiet())
	if len(ymxs.Rows(padded)) == len(rows) {
		t.Fatal("the tune is padded to the unit")
	}
	_, err := schema.Padded(padded, frames)
	if err == nil || err.Error() != "row 3: an effect this version does not read" {
		t.Fatalf("the start at row 3 reports %v", err)
	}
}
