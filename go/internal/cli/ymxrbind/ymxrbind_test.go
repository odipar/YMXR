package ymxrbind

import (
	"bytes"
	"os"
	"strings"
	"testing"

	"github.com/odipar/ymxr/go/internal/built"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/sndh"
)

// said is the lines bound_ reports for the tune file, from the heading
// on.
func said(t *testing.T, tune []byte) string {
	t.Helper()
	bound, err := sndh.Bound(tune)
	if err != nil {
		t.Fatal(err)
	}
	var out bytes.Buffer
	bound_(report.To(true, &out), tune, bound)
	return out.String()
}

func TestTheImageOfFourTimersIsItsBytesToTheEnd(t *testing.T) {
	tune, err := os.ReadFile("../../../../doc/conformance/tunes/four-timers.ymxr")
	if err != nil {
		t.Fatal(err)
	}
	want := strings.Join([]string{
		"bound: DTX's reader for the table in place of the table",
		"  the reader's image     at 184, 2876 bytes",
		"  the source tables      5 of 140 bytes",
		"  the state block        30360 bytes, which the host finds the workspace for",
		"  in all                 3060 bytes, 1468 over the tune file",
	}, "\n") + "\n"
	if got := said(t, tune); got != want {
		t.Fatalf("ymxr-bind reports\n%sand the image follows the sources:\n%s", got, want)
	}
}

func TestTheImageOfVersion10EndsAtTheTransformationBlock(t *testing.T) {
	tune, err := built.File(built.Mapped(), "")
	if err != nil {
		t.Fatal(err)
	}
	want := strings.Join([]string{
		"bound: DTX's reader for the table in place of the table",
		"  the reader's image     at 52, 1664 bytes",
		"  the source tables      1 of 20 bytes",
		"  the transformations    at 1716, 2 of 40 bytes",
		"  the state block        700 bytes, which the host finds the workspace for",
		"  in all                 1756 bytes, 1456 over the tune file",
	}, "\n") + "\n"
	if got := said(t, tune); got != want {
		t.Fatalf("ymxr-bind reports\n%sand reads\n%s", got, want)
	}
}
