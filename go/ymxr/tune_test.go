package ymxr_test

import (
	"bytes"
	"strconv"
	"strings"
	"testing"

	"github.com/odipar/ymxr/go/internal/built"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/ymxr"
)

var fade, half = built.Fade(), built.Half()

func written(t *testing.T, columns ymxr.Columns, name string) []byte {
	t.Helper()
	file, err := built.File(columns, name)
	if err != nil {
		t.Fatal(err)
	}
	return file
}

func TestAMappedTuneWritesVersion10(t *testing.T) {
	file := written(t, built.Mapped(), "fade")
	if v := ymxr.GetWord(file, 4); v != 10 {
		t.Fatalf("the version is %d, and a table of 34 columns writes 10", v)
	}
	if file[ymxr.EffectsAt] != 0x21 {
		t.Fatalf("the effects used are %#x, and the tune sets bits 5 and 0",
			file[ymxr.EffectsAt])
	}
	// the source index at 20, the name at 24, the transformation index on
	// the long after the name's five bytes
	if at := ymxr.GetWord(file, ymxr.NameAt); at != 24 {
		t.Fatalf("the name is at %d, and it follows the index at 20", at)
	}
	index := ymxr.GetLong(file, ymxr.TransformationsAt)
	if index != 32 {
		t.Fatalf("the transformation index is at %d, and it is the long after the name",
			index)
	}
	// T and two offsets, then each transformation on a long: E and its
	// entries
	want := []byte{0, 2, 0, 0, 0, 12, 0, 0, 0, 32, 0, 0, 0, 16}
	want = append(want, fade...)
	want = append(want, 0, 0, 0, 3, 0, 0, 1, 0, 0, 0)
	if got := file[index : index+len(want)]; !bytes.Equal(got, want) {
		t.Fatalf("the transformation index and the transformations are % x, not % x", got, want)
	}
	if table := ymxr.GetLong(file, ymxr.TableAt); table != index+40 {
		t.Fatalf("the DTX3 table is at %d, and it follows the transformations at %d", table,
			index+40)
	}
	if first := ymxr.GetLong(file, ymxr.IndexAt(10)) &^ ymxr.Counted; first%4 != 0 {
		t.Fatalf("source 1 is at %d, and a DTX1 table begins on a long", first)
	}
}

func TestAMappedTuneReadsBack(t *testing.T) {
	columns := built.Mapped()
	read, err := ymxr.Read(written(t, columns, "fade"))
	if err != nil {
		t.Fatal(err)
	}
	if read.Version != 10 || read.Effects != 0x21 || read.FrameRate != 50 {
		t.Fatalf("read as version %d, effects %d at %d Hz", read.Version, read.Effects,
			read.FrameRate)
	}
	if read.Table.Columns() != ymxr.Mapped {
		t.Fatalf("the table has %d columns, and version 10 has 34", read.Table.Columns())
	}
	for c := 0; c < ymxr.Mapped; c++ {
		if !bytes.Equal(read.Table.Column(c), columns.Column[c]) {
			t.Fatalf("column %d reads % x, not % x", c, read.Table.Column(c),
				columns.Column[c])
		}
	}
	if len(read.Transformations) != 2 || !bytes.Equal(read.Transformations[0], fade) ||
		!bytes.Equal(read.Transformations[1], half) {
		t.Fatalf("the transformations read % x", read.Transformations)
	}
	if len(read.Sources) != 1 || !bytes.Equal(read.Sources[0].Column(0), []byte{12, 0x80}) {
		t.Fatalf("the sources read %v", read.Sources)
	}
	if ymxr.TuneName(written(t, columns, "fade")) != "fade" {
		t.Fatal("the name reads otherwise")
	}
}

func TestAnUnmappedTuneWritesVersion9(t *testing.T) {
	file := written(t, built.Unmapped(), "")
	if v := ymxr.GetWord(file, 4); v != 9 {
		t.Fatalf("the version is %d, and a table of 30 columns writes 9", v)
	}
	// the source index at 16 and the table after it, as before version 10
	if at := ymxr.GetLong(file, ymxr.TableAt); at != 20 {
		t.Fatalf("the DTX3 table is at %d, and it follows the index of one source", at)
	}
	read, err := ymxr.Read(file)
	if err != nil {
		t.Fatal(err)
	}
	if len(read.Transformations) != 0 || read.Table.Columns() != ymxr.C {
		t.Fatalf("version 9 reads %d transformations and %d columns",
			len(read.Transformations), read.Table.Columns())
	}
}

// reads is the reader's line for the file, or the empty string.
func reads(file []byte) string {
	if _, err := ymxr.Read(file); err != nil {
		return err.Error()
	}
	return ""
}

func TestTheReaderReportsAVersion10FileInTheOrderOfSpec(t *testing.T) {
	good := written(t, built.Mapped(), "fade")
	index := ymxr.GetLong(good, ymxr.TransformationsAt)
	edit := func(change func(file []byte) []byte) []byte {
		return change(append([]byte(nil), good...))
	}
	nine := written(t, built.Unmapped(), "")
	cases := []struct {
		file []byte
		line string
	}{
		{good, ""},
		{edit(func(f []byte) []byte { f[ymxr.EffectsAt] = 0x01; return f }),
			"version 10 with bit 5 of the effects used clear"},
		{func() []byte {
			f := append([]byte(nil), nine...)
			f[ymxr.EffectsAt] |= ymxr.Maps
			return f
		}(), "version 9 with bit 5 of the effects used set"},
		{edit(func(f []byte) []byte { return f[:18] }), "not a YMXR file"},
		{edit(func(f []byte) []byte {
			ymxr.PutLong(f, ymxr.TransformationsAt, len(f)-1)
			return f
		}), "the transformation index at " + strconv.Itoa(len(good)-1) + " is outside the file"},
		{edit(func(f []byte) []byte { ymxr.PutWord(f, index, 0); return f }),
			"the file has 0 transformations, and a version 10 file has 1 to 127"},
		{edit(func(f []byte) []byte { ymxr.PutWord(f, index, 128); return f }),
			"the file has 128 transformations, and a version 10 file has 1 to 127"},
		{edit(func(f []byte) []byte {
			ymxr.PutLong(f, ymxr.TransformationsAt, len(f)-8)
			ymxr.PutWord(f, len(f)-8, 2)
			return f
		}), "the transformation index at " + strconv.Itoa(len(good)-8) + " is outside the file"},
		{edit(func(f []byte) []byte { ymxr.PutLong(f, index+6, len(f)); return f }),
			"transformation 2 at " + strconv.Itoa(index+len(good)) + " is outside the file"},
		{edit(func(f []byte) []byte { ymxr.PutWord(f, index+32, 0); return f }),
			"transformation 2 has 0 entries, and a transformation has 1 to 256"},
		{edit(func(f []byte) []byte { ymxr.PutWord(f, index+12, 257); return f }),
			"transformation 1 has 257 entries, and a transformation has 1 to 256"},
		{edit(func(f []byte) []byte {
			ymxr.PutLong(f, index+6, len(f)-index-4)
			ymxr.PutWord(f, len(f)-4, 3)
			return f
		}), "transformation 2 at " + strconv.Itoa(len(good)-4) + " is outside the file"},
		// 127 sources, an index of 508 bytes at 20, in a file of 527 bytes
		{func() []byte {
			f := make([]byte, ymxr.IndexAt(10)+4*127-1)
			copy(f, good)
			f[ymxr.CountAt] = 127
			return f
		}(), "the source index at 20 is outside the file"},
		{append([]byte(nil), nine[:ymxr.HeaderLength+1]...),
			"the source index at 16 is outside the file"},
	}
	for i, one := range cases {
		if got := reads(one.file); got != one.line {
			t.Errorf("case %d reads %q, and the line is %q", i, got, one.line)
		}
	}
}

func TestAVersion10TableOf30ColumnsIsAnError(t *testing.T) {
	nine, err := ymxr.Read(written(t, built.Unmapped(), ""))
	if err != nil {
		t.Fatal(err)
	}
	file := ymxr.WithTable(written(t, built.Mapped(), "fade"), nine.Dtx3)
	line := "the table is 30 columns of 1 bytes, and a tune's table is 34 of one" +
		" (SPEC.md 3.3.3)"
	if got := reads(file); got != line {
		t.Fatalf("a version 10 file of a 30-column table reads %q, and the line is %q",
			got, line)
	}
}

func TestTheRingsFollowTheColumnCount(t *testing.T) {
	for _, one := range []struct {
		columns int
		rings   []int
	}{
		{ymxr.C, []int{60, 120, 240, 480, 960, 1920}},
		{ymxr.Mapped, []int{68, 136, 272, 544, 1088, 2176}},
	} {
		got := ymxr.Rings(one.columns)
		for i := range got {
			if got[i] != one.rings[i] {
				t.Fatalf("the rings of %d columns are %v, not %v", one.columns, got,
					one.rings)
			}
		}
	}
	if at := ymxr.RingOf(100, 2, ymxr.Mapped); at != 102 {
		t.Fatalf("a ring of 100 at 34 columns is %d, and the multiple of 34 nearest is"+
			" 102", at)
	}
	if at := ymxr.RingOf(100, 2, ymxr.C); at != 90 {
		t.Fatalf("a ring of 100 at 30 columns is %d, not 90", at)
	}
}

func TestAMappingColumnIsNamedByItsEffect(t *testing.T) {
	for c, name := range map[int]string{29: "effect 3 timer count",
		30: "effect 0 mapping", 33: "effect 3 mapping"} {
		if got := ymxr.Name(c); got != name {
			t.Errorf("column %d is %q, not %q", c, got, name)
		}
	}
}

func TestWithTableKeepsTheTransformations(t *testing.T) {
	file := written(t, built.Mapped(), "fade")
	read, err := ymxr.Read(file)
	if err != nil {
		t.Fatal(err)
	}
	longer := append(append([]byte(nil), read.Dtx3...), 0, 0, 0, 0, 0, 0, 0, 0)
	moved := ymxr.WithTable(file, longer)
	if got, want := ymxr.GetLong(moved, ymxr.IndexAt(10)),
		ymxr.GetLong(file, ymxr.IndexAt(10))+8; got != want {
		t.Fatalf("source 1 is at %d after a table 8 bytes longer, not %d", got, want)
	}
	index := ymxr.GetLong(file, ymxr.TransformationsAt)
	if !bytes.Equal(moved[:20], file[:20]) ||
		!bytes.Equal(moved[24:index+40], file[24:index+40]) {
		t.Fatal("the header, the name and the transformations moved")
	}
}

func TestAMultiFileIsVersion10WhereATuneIs(t *testing.T) {
	ten := written(t, built.Mapped(), "fade")
	nine := written(t, built.Unmapped(), "")
	for _, one := range []struct {
		tunes   [][]byte
		version int
	}{
		{[][]byte{nine, nine}, 9},
		{[][]byte{nine, ten}, 10},
	} {
		names := make([]string, len(one.tunes))
		multi, err := ymxr.Multi(one.tunes, names)
		if err != nil {
			t.Fatal(err)
		}
		if v := ymxr.GetWord(multi, 4); v != one.version {
			t.Fatalf("the multi file is version %d, not %d", v, one.version)
		}
		read, err := ymxr.ReadMulti(multi)
		if err != nil {
			t.Fatal(err)
		}
		if !bytes.Equal(read.Tunes[len(one.tunes)-1], one.tunes[len(one.tunes)-1]) {
			t.Fatal("the last tune reads otherwise")
		}
	}
}

func TestThePackingReportReadsTheColumnCount(t *testing.T) {
	var out bytes.Buffer
	_, err := ymxr.WriteNamed(built.Mapped(), built.Sources(), 50, 1, ymxr.Choose, ymxr.Plain(),
		report.To(true, &out), "fade")
	if err != nil {
		t.Fatal(err)
	}
	said := out.String()
	for _, line := range []string{"the table: 34 columns of 6 rows, 204 bytes,",
		"effect 0 mapping", "effect 3 mapping", "packed 204 bytes into"} {
		if !strings.Contains(said, line) {
			t.Errorf("the report has no %q:\n%s", line, said)
		}
	}
}
