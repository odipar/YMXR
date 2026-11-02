package trace_test

import (
	"strings"
	"testing"

	"github.com/odipar/ymxr/go/internal/built"
	"github.com/odipar/ymxr/go/trace"
	"github.com/odipar/ymxr/go/ymxr"
)

func record(t *testing.T, columns ymxr.Columns) []string {
	t.Helper()
	file, err := built.File(columns, "")
	if err != nil {
		t.Fatal(err)
	}
	rows := trace.Record(file, -1)
	if len(rows) == 0 {
		t.Fatal("no record")
	}
	return strings.Split(strings.TrimSuffix(string(rows), "\n"), "\n")
}

// effect is the entry of effect 0 in a record of built.Mapped's rows.
func effect(source, selects int, reset bool, mapping string) string {
	b := "false"
	if reset {
		b = "true"
	}
	return `{"result":0,"w":{},"e":{"0":{"target":8,"source":` + string(rune('0'+source)) +
		`,"select":` + string(rune('0'+selects)) + `,"count":100,"timer":` + b +
		`,"place":` + b + `,"deferred":false` + mapping + `}}}`
}

func TestAVersion10RecordHasTheTransformationsAndTheMappings(t *testing.T) {
	lines := record(t, built.Mapped())
	want := []string{
		`{"rate":50,"effects":33,"sources":[{"rows":[12,128],"repeat":0}],` +
			`"transformations":[[15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,0],[0,0,1]]}`,
		effect(1, 1, true, `,"mapping":1`),
		// a remap: the source as it is, the mapping 2
		effect(1, 1, false, `,"mapping":2`),
		// a start that leaves the mapping column unset keeps the mapping
		effect(1, 1, false, `,"mapping":2`),
		// a stop that sets the mapping column leaves the mapping as it is
		effect(0, 1, false, `,"mapping":2`),
		// a remap on the idle timer
		effect(0, 1, false, `,"mapping":0`),
		`{"result":0,"w":{},"e":{}}`,
		`{"result":-1}`,
	}
	if len(lines) != len(want) {
		t.Fatalf("the record is %d lines, not %d:\n%s", len(lines), len(want),
			strings.Join(lines, "\n"))
	}
	for i := range want {
		if lines[i] != want[i] {
			t.Errorf("line %d is\n%s\nnot\n%s", i, lines[i], want[i])
		}
	}
}

func TestAVersion9RecordIsAsBefore(t *testing.T) {
	lines := record(t, built.Unmapped())
	want := []string{
		`{"rate":50,"effects":1,"sources":[{"rows":[12,128],"repeat":0}]}`,
		effect(1, 1, true, ""),
		`{"result":0,"w":{},"e":{}}`,
		effect(1, 1, false, ""),
		effect(0, 1, false, ""),
		`{"result":0,"w":{},"e":{}}`,
		`{"result":0,"w":{},"e":{}}`,
		`{"result":-1}`,
	}
	if strings.Join(lines, "\n") != strings.Join(want, "\n") {
		t.Fatalf("the record is\n%s\nnot\n%s", strings.Join(lines, "\n"),
			strings.Join(want, "\n"))
	}
}
