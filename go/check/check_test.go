package check_test

import (
	"strings"
	"testing"

	"github.com/odipar/ymxr/go/check"
	"github.com/odipar/ymxr/go/internal/built"
	"github.com/odipar/ymxr/go/ymxr"
)

func places(t *testing.T, columns ymxr.Columns) string {
	t.Helper()
	file, err := built.File(columns, "")
	if err != nil {
		t.Fatal(err)
	}
	read, err := ymxr.Read(file)
	if err != nil {
		t.Fatal(err)
	}
	return strings.Join(check.Places(read), "\n")
}

func TestAStopThatSetsTheMappingColumnIsALine(t *testing.T) {
	want := "3: effect 0 stops and sets its mapping column, and a stop leaves the" +
		" mapping as it is"
	if got := places(t, built.Mapped()); got != want {
		t.Fatalf("the check reads %q, and the line is %q", got, want)
	}
}

func TestAMappingPastTheTransformationsIsALine(t *testing.T) {
	columns := built.Mapped()
	columns.Transformations = columns.Transformations[:1]
	columns.Column[ymxr.MappingColumn+1][2] = 0x80 | 5
	want := "1: effect 0 maps through transformation 2, and the file has 1" +
		" transformation\n" +
		"2: effect 1 maps through transformation 5, and the file has 1" +
		" transformation\n" +
		"3: effect 0 stops and sets its mapping column, and a stop leaves the" +
		" mapping as it is"
	if got := places(t, columns); got != want {
		t.Fatalf("the check reads\n%s\nand the lines are\n%s", got, want)
	}
}

func TestAVersion9FileHasNoMappingLine(t *testing.T) {
	if got := places(t, built.Unmapped()); got != "" {
		t.Fatalf("the check of version 9 reads %q", got)
	}
}
