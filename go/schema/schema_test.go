package schema_test

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"regexp"
	"strconv"
	"strings"
	"testing"

	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/schema"
	"github.com/odipar/ymxr/go/trace"
	"github.com/odipar/ymxr/go/ymxr"
	"github.com/odipar/ymxs/go/check"
	"github.com/odipar/ymxs/go/ymxs"
)

var square = ymxs.RepeatingSource("square 12", []int{12, 0}, 0)

// other is a second source of one column, for a start of another source.
var other = ymxs.RepeatingSource("square 9", []int{9, 0}, 0)

// volumes is Vn of YMXS, SPEC.md 3.6.5: entry k of the sixteen keys of a
// volume is k times n divided by 15.
func volumes(n int) ymxs.Transformation {
	entries := make([]int, 16)
	for k := range entries {
		entries[k] = k * n / 15
	}
	return ymxs.Transformation{Name: fmt.Sprintf("V%d", n), Entries: entries}
}

// whole is a transformation of 256 entries, one for each key of a byte:
// entry k is k divided by 16.
var whole = func() ymxs.Transformation {
	entries := make([]int, 256)
	for k := range entries {
		entries[k] = k / 16
	}
	return ymxs.Transformation{Name: "8 bits to 4", Entries: entries}
}()

// timing is a timer from a whole period at the divisor 4 and count 100,
// the place at row 0.
func timing(t *testing.T) ymxs.Timing {
	t.Helper()
	prescaler, err := ymxs.PrescalerBy(4)
	if err != nil {
		t.Fatal(err)
	}
	return ymxs.Timing{Prescaler: prescaler, Count: 100, TimerReset: true, PlaceReset: true}
}

// struck is a struck note of the source on the register, through the
// mapping.
func struck(t *testing.T, register ymxs.Register, source ymxs.Single,
	mapping ymxs.Mapping) ymxs.Start {
	t.Helper()
	return started(t, ymxs.Setting(register), source, false, mapping)
}

// started is a start of the source on the target through the mapping,
// deferred or at its row.
func started(t *testing.T, target ymxs.Target, source ymxs.Source, deferred bool,
	mapping ymxs.Mapping) ymxs.Start {
	t.Helper()
	start, err := ymxs.Starting(target, source, timing(t), deferred, mapping)
	if err != nil {
		t.Fatal(err)
	}
	return start
}

// on is a row of one operation on the timer.
func on(timer ymxs.Timer, effect ymxs.Effect) ymxs.Row {
	return ymxs.Row{Effects: map[ymxs.Timer]ymxs.Effect{timer: effect}}
}

// remap is a remap through the mapping.
func remap(mapping ymxs.Mapping) ymxs.Remap {
	return ymxs.Remap{Mapping: mapping}
}

// tune is the rows at 50 Hz, repeating to the row.
func tune(rows []ymxs.Row, repeat int) ymxs.Tune {
	return ymxs.Tune{Rate: 50, Table: ymxs.Repeating(rows, repeat)}
}

// made is the tune mapped, and a structure YMXS's check passes.
func made(t *testing.T, tune ymxs.Tune) schema.Made {
	t.Helper()
	if faults := check.Tune(tune); len(faults) > 0 {
		t.Fatalf("the structure is wrong: %v", faults)
	}
	out, err := schema.Of(tune)
	if err != nil {
		t.Fatal(err)
	}
	return out
}

// reported is the line the writer reports for the tune.
func reported(t *testing.T, tune ymxs.Tune) string {
	t.Helper()
	_, err := schema.Of(tune)
	if err == nil {
		t.Fatal("the tune is mapped onto the columns, and it is an error of the writer")
	}
	return err.Error()
}

// file is the tune file of the columns.
func file(t *testing.T, one schema.Made) []byte {
	t.Helper()
	out, err := ymxr.Pack(one.Columns, one.Sources, one.Rate, 1, ymxr.Choose, ymxr.Plain(),
		report.Quiet(), "")
	if err != nil {
		t.Fatal(err)
	}
	return out.File
}

// mapping is column 30 + i, the mapping column of effect i.
func mapping(one schema.Made, i int) []byte {
	return one.Columns.Column[ymxr.MappingColumn+i]
}

// expect fails where the bytes read differ from the bytes the rules write.
func expect(t *testing.T, what string, got, want []byte) {
	t.Helper()
	if !bytes.Equal(got, want) {
		t.Fatalf("%s reads %v in place of %v", what, got, want)
	}
}

// A mapping of identity parts is the identity, so the start through it
// writes the columns of a start through the mapping of no parts.
func TestAStartThroughIdentityPartsWritesTheColumnsOfNoParts(t *testing.T) {
	parts := made(t, tune([]ymxs.Row{on(ymxs.TimerA, struck(t, ymxs.R8, square,
		ymxs.Mapped(ymxs.Identity{})))}, 0))
	none := made(t, tune([]ymxs.Row{on(ymxs.TimerA, struck(t, ymxs.R8, square,
		ymxs.Mapping{}))}, 0))
	for c := range none.Columns.Column {
		expect(t, fmt.Sprintf("column %d through an identity part", c),
			parts.Columns.Column[c], none.Columns.Column[c])
	}
	if parts.Columns.Effects != none.Columns.Effects {
		t.Fatalf("the effects word reads %d through an identity part, and %d through"+
			" the mapping of no parts", parts.Columns.Effects, none.Columns.Effects)
	}
}

// A tune through the identity alone, its remaps to the identity among its
// rows, is the tune file of version 9 byte for byte: each remap leaves
// every column as it is (SPEC.md 3.3.5, rule 2(g)).
func TestAnIdentityTuneWithIdentityRemapsWritesVersion9(t *testing.T) {
	identity := ymxs.Mapped(ymxs.Identity{})
	remapped := made(t, tune([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, ymxs.Mapping{})),
		on(ymxs.TimerA, remap(identity)),
		on(ymxs.TimerA, remap(ymxs.Mapping{})),
		on(ymxs.TimerA, struck(t, ymxs.R8, other, identity)),
	}, 1))
	plain := made(t, tune([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, ymxs.Mapping{})),
		{},
		{},
		on(ymxs.TimerA, struck(t, ymxs.R8, other, ymxs.Mapping{})),
	}, 1))
	if len(remapped.Columns.Column) != ymxr.C || remapped.Columns.Effects&ymxr.Maps != 0 ||
		remapped.Columns.Transformations != nil {
		t.Fatalf("the tune is %d columns, the effects used %b, and version 9 is %d"+
			" columns with bit 5 clear", len(remapped.Columns.Column),
			remapped.Columns.Effects, ymxr.C)
	}
	written := file(t, remapped)
	if ymxr.GetWord(written, 4) != ymxr.Unmapped {
		t.Fatalf("the tune file is version %d", ymxr.GetWord(written, 4))
	}
	expect(t, "the tune file", written, file(t, plain))
}

// A remap on a timer outside the tune leaves every column as it is (rule
// 2(a)), so a tune whose only transformation is on such a remap writes
// version 9, as the YMXS kit's transformations.json has at Timer D row 0.
func TestARemapOutsideTheTuneWritesVersion9(t *testing.T) {
	one := made(t, tune([]ymxs.Row{{Effects: map[ymxs.Timer]ymxs.Effect{
		ymxs.TimerA: struck(t, ymxs.R8, square, ymxs.Mapping{}),
		ymxs.TimerD: remap(ymxs.Mapped(volumes(7))),
	}}}, 0))
	if len(one.Columns.Column) != ymxr.C || one.Columns.Effects != 1 {
		t.Fatalf("the tune is %d columns and the effects used %b", len(one.Columns.Column),
			one.Columns.Effects)
	}
}

// fade is a fade of voice A, YMXS's SPEC.md 3.6.5: a start of a voice on
// setVoiceA through the identity, the identity and V15, then a remap a
// row through V10, V5 and V0, the tune repeating to row 0.
func fade(t *testing.T) ymxs.Tune {
	t.Helper()
	voice := ymxs.Triple{Name: "voice", Table: ymxs.Repeating([]ymxs.Three{
		{First: 0x60, Second: 1, Third: 15}, {First: 0x60, Second: 1, Third: 12}}, 0)}
	through := func(n int) ymxs.Mapping {
		return ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{}, volumes(n))
	}
	return tune([]ymxs.Row{
		on(ymxs.TimerA, started(t, ymxs.SetVoice{Voice: ymxs.VoiceA}, voice, false,
			through(15))),
		on(ymxs.TimerA, remap(through(10))),
		on(ymxs.TimerA, remap(through(5))),
		on(ymxs.TimerA, remap(through(0))),
	}, 0)
}

// The fade writes version 10: the four mapping columns, column 30 a number
// a row, bit 5 of the effects used, the long at 16 the transformation
// index, the source index at 20 and the transformations in the order YMXS
// numbers them (SPEC.md 1.10, 3.3, 3.4).
func TestTheFadeWritesVersion10(t *testing.T) {
	one := made(t, fade(t))
	if len(one.Columns.Column) != ymxr.Mapped {
		t.Fatalf("the fade is %d columns", len(one.Columns.Column))
	}
	expect(t, "column 30", mapping(one, 0), []byte{0x81, 0x82, 0x83, 0x84})
	for i := 1; i < 4; i++ {
		expect(t, fmt.Sprintf("column %d", ymxr.MappingColumn+i), mapping(one, i),
			[]byte{0, 0, 0, 0})
	}
	expect(t, "the source column", one.Columns.Column[ymxr.Effect+1], []byte{0x81, 0, 0, 0})
	if one.Columns.Effects != 1|ymxr.Maps {
		t.Fatalf("the effects used read %b", one.Columns.Effects)
	}
	written := file(t, one)
	if ymxr.GetWord(written, 4) != ymxr.Version {
		t.Fatalf("the tune file is version %d", ymxr.GetWord(written, 4))
	}
	index := ymxr.GetLong(written, ymxr.TransformationsAt)
	if ymxr.GetWord(written, index) != 4 {
		t.Fatalf("the transformation index at %d has %d transformations", index,
			ymxr.GetWord(written, index))
	}
	source := ymxr.GetLong(written, ymxr.IndexAt(ymxr.Version))
	if source&^ymxr.Counted <= index || ymxr.IndexAt(ymxr.Version) != 20 {
		t.Fatalf("the source index at %d has source 1 at %d, and the transformations at %d",
			ymxr.IndexAt(ymxr.Version), source, index)
	}
	read, err := ymxr.Read(written)
	if err != nil {
		t.Fatal(err)
	}
	if read.Effects != 1|ymxr.Maps || len(read.Transformations) != 4 {
		t.Fatalf("the file reads back the effects used %b and %d transformations",
			read.Effects, len(read.Transformations))
	}
	for n, volume := range []int{15, 10, 5, 0} {
		want := make([]byte, 16)
		for k, entry := range volumes(volume).Entries {
			want[k] = byte(entry)
		}
		expect(t, fmt.Sprintf("transformation %d", n+1), read.Transformations[n], want)
	}
}

// The mapping of each remap row of the fade's record is the number YMXS,
// SPEC.md 7.4 records for the last part of the remap's mapping.
func TestTheRecordOfARemapRowIsTheNumberOfItsLastPart(t *testing.T) {
	fade := fade(t)
	list := ymxs.Transformations(fade)
	lines := strings.Split(string(trace.Record(file(t, made(t, fade)), -1)), "\n")
	kept := regexp.MustCompile(`"0":\{[^}]*"mapping":(\d+)\}`)
	read := 0
	for r, row := range ymxs.Rows(fade) {
		remap, is := row.Effects[ymxs.TimerA].(ymxs.Remap)
		if !is {
			continue
		}
		found := kept.FindStringSubmatch(lines[r+1])
		if found == nil {
			t.Fatalf("row %d records %s", r, lines[r+1])
		}
		parts := remap.Mapping.Parts
		numbers, err := ymxs.PartNumbers(ymxs.Mapped(parts[len(parts)-1]), list)
		if err != nil {
			t.Fatal(err)
		}
		if got, _ := strconv.Atoi(found[1]); got != numbers[0] {
			t.Fatalf("row %d records the mapping %d, and its last part is %d", r, got,
				numbers[0])
		}
		read++
	}
	if read != 3 {
		t.Fatalf("the record has %d remaps of the fade's three", read)
	}
}

// The transformations are numbered in the order of YMXS's SPEC.md 1.9: the
// rows from row 0 and within a row the timers A to D, the order of the
// effects A, D, B and C aside.
func TestTheTransformationsAreNumberedInTimerOrder(t *testing.T) {
	one := made(t, tune([]ymxs.Row{{Effects: map[ymxs.Timer]ymxs.Effect{
		ymxs.TimerA: struck(t, ymxs.R8, square, ymxs.Mapped(volumes(15))),
		ymxs.TimerB: struck(t, ymxs.R9, square, ymxs.Mapped(volumes(10))),
		ymxs.TimerD: struck(t, ymxs.R10, square, ymxs.Mapped(volumes(5))),
	}}}, 0))
	// effects 0, 1 and 2 are Timers A, D and B
	for i, number := range []byte{1, 3, 2} {
		expect(t, fmt.Sprintf("the mapping column of effect %d", i), mapping(one, i),
			[]byte{0x80 | number})
	}
}

// A start that leaves the mapping column unset runs through the kept
// mapping, so a start through the kept one writes it unset; a remap sets
// it with the source column unset (SPEC.md 1.10.2), and a stop leaves it
// unset and the kept mapping as it is (rule 3(c)), so a remap to the kept
// mapping after the stop is left out.
func TestAStartThroughTheKeptMappingLeavesTheColumnUnset(t *testing.T) {
	v10 := ymxs.Mapped(volumes(10))
	v5 := ymxs.Mapped(volumes(5))
	one := made(t, ymxs.Tune{Rate: 50, Table: ymxs.Once([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, v10)),
		on(ymxs.TimerA, struck(t, ymxs.R8, other, v10)),
		on(ymxs.TimerA, remap(v5)),
		on(ymxs.TimerA, ymxs.Stop{}),
		on(ymxs.TimerA, remap(v5)),
		on(ymxs.TimerA, struck(t, ymxs.R8, square, v5)),
	})})
	expect(t, "column 30", mapping(one, 0), []byte{0x81, 0, 0x82, 0, 0, 0})
	expect(t, "the source column", one.Columns.Column[ymxr.Effect+1],
		[]byte{0x81, 0x82, 0, 0x80, 0, 0x81})
}

// A remap on an idle timer of the tune is written, the mapping the next
// start keeps (SPEC.md 1.10.2), and a start through it leaves the column
// unset.
func TestARemapOnAnIdleTimerOfTheTuneIsWritten(t *testing.T) {
	one := made(t, ymxs.Tune{Rate: 50, Table: ymxs.Once([]ymxs.Row{
		on(ymxs.TimerC, remap(ymxs.Mapped(volumes(5)))),
		on(ymxs.TimerC, struck(t, ymxs.R8, square, ymxs.Mapped(volumes(5)))),
	})})
	expect(t, "column 33", mapping(one, 3), []byte{0x81, 0})
}

// After a deferred start the kept mapping is the pending start's, and the
// running source may run through another until a tick performs it: a
// remap equal to the kept mapping is written (YMXS, SPEC.md 3.5.5). Once
// the remap and the pending start agree, a second such remap leaves the
// column unset.
func TestARemapEqualToTheKeptMappingAfterADeferredStartIsWritten(t *testing.T) {
	v5 := ymxs.Mapped(volumes(5))
	one := made(t, ymxs.Tune{Rate: 50, Table: ymxs.Once([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, ymxs.Mapped(volumes(10)))),
		on(ymxs.TimerA, started(t, ymxs.Setting(ymxs.R8), other, true, v5)),
		on(ymxs.TimerA, remap(v5)),
		on(ymxs.TimerA, remap(v5)),
	})})
	expect(t, "column 30", mapping(one, 0), []byte{0x81, 0x82, 0x82, 0})
}

// While a deferred start through another mapping may be pending, a tick
// may have performed it, so a remap to the mapping the effect ran through
// before is written, and so is the same remap again: the pending start
// keeps the mapping of its row (YMXS, SPEC.md 3.5.5).
func TestARemapWhileAStartIsPendingThroughAnotherIsWritten(t *testing.T) {
	v10 := ymxs.Mapped(volumes(10))
	one := made(t, ymxs.Tune{Rate: 50, Table: ymxs.Once([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, v10)),
		on(ymxs.TimerA, started(t, ymxs.Setting(ymxs.R8), other, true,
			ymxs.Mapped(volumes(5)))),
		on(ymxs.TimerA, remap(v10)),
		on(ymxs.TimerA, remap(v10)),
	})})
	expect(t, "column 30", mapping(one, 0), []byte{0x81, 0x82, 0x81, 0x81})
}

// A remap to the mapping the effect runs through, with no start pending
// through another, leaves the column unset; a deferred start through the
// mapping the effect runs through leaves the running mapping as it is
// (rule 2(g)).
func TestARemapToTheRunningMappingLeavesTheColumnUnset(t *testing.T) {
	v5 := ymxs.Mapped(volumes(5))
	one := made(t, ymxs.Tune{Rate: 50, Table: ymxs.Once([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, v5)),
		on(ymxs.TimerA, remap(v5)),
		on(ymxs.TimerA, started(t, ymxs.Setting(ymxs.R8), other, true, v5)),
		on(ymxs.TimerA, remap(v5)),
	})})
	expect(t, "column 30", mapping(one, 0), []byte{0x81, 0, 0, 0})
}

// The player keeps the mapping through the wrap, so the first start at or
// after the repeat row writes the mapping column of an effect a row maps
// (SPEC.md 1.10.3), and a remap there is written.
func TestTheRepeatRowWritesTheMappingColumn(t *testing.T) {
	v10 := ymxs.Mapped(volumes(10))
	one := made(t, tune([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, v10)),
		on(ymxs.TimerA, struck(t, ymxs.R8, other, v10)),
		on(ymxs.TimerA, remap(ymxs.Mapped(volumes(5)))),
	}, 1))
	expect(t, "column 30", mapping(one, 0), []byte{0x81, 0x81, 0x82})
	again := made(t, tune([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, v10)),
		on(ymxs.TimerA, remap(v10)),
		on(ymxs.TimerA, remap(ymxs.Mapped(volumes(5)))),
	}, 1))
	expect(t, "column 30 after a remap at the repeat row", mapping(again, 0),
		[]byte{0x81, 0x81, 0x82})
}

// An effect through the identity alone keeps a constant 0 mapping column in
// a repeating tune of version 10: its remaps to the identity and its starts
// after the repeat row leave the column unset (SPEC.md 1.10.3).
func TestAnIdentityEffectKeepsAConstantMappingColumn(t *testing.T) {
	identity := ymxs.Mapped(ymxs.Identity{})
	one := made(t, tune([]ymxs.Row{
		{Effects: map[ymxs.Timer]ymxs.Effect{
			ymxs.TimerA: struck(t, ymxs.R8, square, ymxs.Mapped(volumes(10))),
			ymxs.TimerB: struck(t, ymxs.R9, square, ymxs.Mapping{}),
		}},
		{Effects: map[ymxs.Timer]ymxs.Effect{
			ymxs.TimerA: remap(ymxs.Mapped(volumes(5))),
			ymxs.TimerB: remap(identity),
		}},
		on(ymxs.TimerB, struck(t, ymxs.R9, other, identity)),
		on(ymxs.TimerB, remap(ymxs.Mapping{})),
		on(ymxs.TimerB, started(t, ymxs.Setting(ymxs.R9), square, true, ymxs.Mapping{})),
		on(ymxs.TimerB, remap(identity)),
	}, 1))
	if one.Columns.Effects&ymxr.Maps == 0 {
		t.Fatal("the tune maps a value on Timer A, and bit 5 is clear")
	}
	// Timer B is effect 2
	expect(t, "column 32", mapping(one, 2), make([]byte, 6))
	expect(t, "column 30", mapping(one, 0), []byte{0x81, 0x82, 0, 0, 0, 0})
}

// remappedThrough is a square started, then a remap a row through each of
// count transformations.
func remappedThrough(t *testing.T, count int) ymxs.Tune {
	t.Helper()
	rows := []ymxs.Row{on(ymxs.TimerA, struck(t, ymxs.R8, square, ymxs.Mapping{}))}
	for n := 0; n < count; n++ {
		rows = append(rows, on(ymxs.TimerA, remap(ymxs.Mapped(
			ymxs.Transformation{Name: fmt.Sprint(n), Entries: make([]int, 16)}))))
	}
	return tune(rows, 0)
}

// More transformations than the mapping column numbers is a line of the
// writer (SPEC.md 6.5), and 127 are 127 tables.
func TestMoreThan127TransformationsIsALine(t *testing.T) {
	said := reported(t, remappedThrough(t, ymxr.MostTransformations+1))
	want := "the tune maps through 128 transformations, and a mapping column numbers 127"
	if said != want {
		t.Fatalf("the tune reports %q, and the line is %q", said, want)
	}
	one := made(t, remappedThrough(t, ymxr.MostTransformations))
	if len(one.Columns.Transformations) != ymxr.MostTransformations {
		t.Fatalf("127 transformations are %d tables", len(one.Columns.Transformations))
	}
}

// outsideThrough is a square started, then a remap a row on Timer D, a
// timer outside the tune, through each of count transformations, and where
// last is set a remap of the square through one more.
func outsideThrough(t *testing.T, count int, last bool) ymxs.Tune {
	t.Helper()
	rows := []ymxs.Row{on(ymxs.TimerA, struck(t, ymxs.R8, square, ymxs.Mapping{}))}
	for n := 0; n < count; n++ {
		rows = append(rows, on(ymxs.TimerD, remap(ymxs.Mapped(
			ymxs.Transformation{Name: fmt.Sprint(n), Entries: make([]int, 16)}))))
	}
	if last {
		rows = append(rows, on(ymxs.TimerA, remap(ymxs.Mapped(
			ymxs.Transformation{Name: "last", Entries: make([]int, 16)}))))
	}
	return tune(rows, 0)
}

// A writer leaves out a remap on a timer outside the tune, and its
// transformations with it: 128 of them write version 9 (SPEC.md 3.3.5,
// rule 2(a)). A remap on a timer of the tune through one more makes a file
// of 128 transformations, the line of SPEC.md 6.5.
func TestTransformationsOutsideTheTuneAreLeftOut(t *testing.T) {
	one := made(t, outsideThrough(t, ymxr.MostTransformations+1, false))
	if len(one.Columns.Column) != ymxr.C || one.Columns.Effects&ymxr.Maps != 0 ||
		len(one.Columns.Transformations) != 0 {
		t.Fatalf("the tune is %d columns, the effects used %b and %d tables",
			len(one.Columns.Column), one.Columns.Effects, len(one.Columns.Transformations))
	}
	without := ymxs.Rows(outsideThrough(t, 0, false))
	for n := 0; n <= ymxr.MostTransformations; n++ {
		without = append(without, ymxs.Row{})
	}
	expect(t, "the file", file(t, one), file(t, made(t, tune(without, 0))))
	said := reported(t, outsideThrough(t, ymxr.MostTransformations, true))
	want := "the tune maps through 128 transformations, and a mapping column numbers 127"
	if said != want {
		t.Fatalf("the tune reports %q, and the line is %q", said, want)
	}
}

// A mapping column maps the last value of its target, so a mapping of
// another value is a line of the writer with the row of the structure
// (SPEC.md 1.10.1, 6.5).
func TestAMappingOfAnotherValueIsALine(t *testing.T) {
	voice := ymxs.Triple{Name: "voice", Table: ymxs.Repeating([]ymxs.Three{
		{First: 0x60, Second: 1, Third: 15}}, 0)}
	said := reported(t, tune([]ymxs.Row{on(ymxs.TimerA, started(t,
		ymxs.SetVoice{Voice: ymxs.VoiceA}, voice, false,
		ymxs.Mapped(ymxs.Identity{}, volumes(10), volumes(5))))}, 0))
	want := "row 0: the mapping maps value 1 of setVoiceA, and a mapping column maps its" +
		" value 2 alone"
	if said != want {
		t.Fatalf("the start reports %q, and the line is %q", said, want)
	}
}

// A remap of three values on setVoiceB, as the YMXS kit's
// transformations.json has at Timer B row 2, maps the fine period first:
// the line names the target of the start the remap maps.
func TestARemapOfThreeValuesOnSetVoiceBIsALine(t *testing.T) {
	chord := ymxs.Triple{Name: "chord", Table: ymxs.Repeating([]ymxs.Three{
		{First: 28, Second: 1, Third: 15}, {First: 214, Second: 0, Third: 12}}, 0)}
	half := ymxs.Transformation{Name: "half", Entries: make([]int, 256)}
	for k := range half.Entries {
		half.Entries[k] = k / 2
	}
	said := reported(t, tune([]ymxs.Row{
		{},
		on(ymxs.TimerB, started(t, ymxs.SetVoice{Voice: ymxs.VoiceB}, chord, false,
			ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{}, volumes(10)))),
		on(ymxs.TimerB, remap(ymxs.Mapped(half, ymxs.Transformation{Name: "half",
			Entries: volumes(8).Entries}, volumes(15)))),
	}, 1))
	want := "row 2: the mapping maps value 0 of setVoiceB, and a mapping column maps its" +
		" value 2 alone"
	if said != want {
		t.Fatalf("the remap reports %q, and the line is %q", said, want)
	}
}

// A start through a transformation at row 3 of four rows that repeat to
// row 1: padding to a unit of 2 adds a row before row 1, and the line
// numbers the row of the structure.
func TestTheRowOfAnErrorIsTheRowOfTheStructure(t *testing.T) {
	tone := ymxs.Pair{Name: "tone", Table: ymxs.Repeating([]ymxs.Two{
		{First: 0x60, Second: 1}}, 0)}
	rows := []ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, ymxs.Mapping{})),
		{},
		{},
		on(ymxs.TimerB, started(t, ymxs.SetTone{Voice: ymxs.VoiceB}, tone, false,
			ymxs.Mapped(volumes(10), ymxs.Identity{}))),
	}
	padded, frames := ymxr.PadToUnit(tune(rows, 1), 2, report.Quiet())
	if len(ymxs.Rows(padded)) == len(rows) {
		t.Fatal("the tune is padded to the unit")
	}
	_, err := schema.Padded(padded, frames)
	want := "row 3: the mapping maps value 0 of setToneB, and a mapping column maps its" +
		" value 1 alone"
	if err == nil || err.Error() != want {
		t.Fatalf("the start at row 3 reports %v", err)
	}
}

// The source of start, on setR10 through a table of 256 entries, has a key
// of 128 or more: every start of it is through a transformation, so it is
// written counted, every row a whole byte and no marker (SPEC.md 3.1.6).
func TestAKeyOf128OnSetR10ThroughATransformationIsCounted(t *testing.T) {
	sample := ymxs.RepeatingSource("sample", []int{128, 200, 255, 56, 0}, 0)
	one := made(t, tune([]ymxs.Row{on(ymxs.TimerC, struck(t, ymxs.R10, sample,
		ymxs.Mapped(whole)))}, 0))
	source := one.Sources.At(1)
	if !source.Counted {
		t.Fatal("the source of keys of 128 or more is marked")
	}
	expect(t, "the source's rows", source.Columns[0], []byte{128, 200, 255, 56, 0})
	file := file(t, one)
	if ymxr.GetLong(file, ymxr.IndexAt(ymxr.Version))&ymxr.Counted == 0 {
		t.Fatal("bit 31 of the source's index entry is clear")
	}
	// keys below 128 keep the marker
	low := made(t, tune([]ymxs.Row{on(ymxs.TimerC, struck(t, ymxs.R10,
		ymxs.RepeatingSource("low", []int{100, 50}, 0), ymxs.Mapped(whole)))}, 0))
	if low.Sources.At(1).Counted {
		t.Fatal("the source of keys below 128 is counted")
	}
	expect(t, "the marked source's rows", low.Sources.At(1).Columns[0], []byte{100, 0x80 | 50})
}

// Through the identity, a value of 128 or more on setR10 is an error of the
// structure (YMXS, SPEC.md 3.2.2), and so is a source that one start runs
// through the identity and another through a transformation: YMXS's check
// reports it before the writer reads it.
func TestAKeyOf128ThroughTheIdentityIsAnErrorOfTheStructure(t *testing.T) {
	sample := ymxs.RepeatingSource("sample", []int{128, 0}, 0)
	for _, rows := range [][]ymxs.Row{
		{on(ymxs.TimerC, struck(t, ymxs.R10, sample, ymxs.Mapping{}))},
		{
			on(ymxs.TimerC, struck(t, ymxs.R10, sample, ymxs.Mapped(whole))),
			on(ymxs.TimerA, struck(t, ymxs.R8, sample, ymxs.Mapping{})),
		},
	} {
		if faults := check.Tune(tune(rows, 0)); len(faults) == 0 {
			t.Fatal("YMXS's check passes a value of 128 through the identity")
		}
	}
}

// On setToneX the mapped value is in the marker's column, so a key of 128
// or more there is the marker's line; on setVoiceX the mapped volume is a
// whole byte, and a key of 255 passes.
func TestAKeyOf128OnAWiderTarget(t *testing.T) {
	tone := ymxs.Pair{Name: "tone", Table: ymxs.Repeating([]ymxs.Two{
		{First: 0x60, Second: 200}}, 0)}
	said := reported(t, tune([]ymxs.Row{on(ymxs.TimerA, started(t,
		ymxs.SetTone{Voice: ymxs.VoiceA}, tone, false,
		ymxs.Mapped(ymxs.Identity{}, whole)))}, 0))
	want := "the source tone has the value 200 in row 0 of column 1, and bit 7 of the" +
		" marker's column is the marker"
	if said != want {
		t.Fatalf("the source reports %q, and the line is %q", said, want)
	}
	voice := ymxs.Triple{Name: "voice", Table: ymxs.Repeating([]ymxs.Three{
		{First: 0x60, Second: 1, Third: 255}}, 0)}
	one := made(t, tune([]ymxs.Row{on(ymxs.TimerA, started(t,
		ymxs.SetVoice{Voice: ymxs.VoiceA}, voice, false,
		ymxs.Mapped(ymxs.Identity{}, ymxs.Identity{}, whole)))}, 0))
	if one.Sources.At(1).Counted {
		t.Fatal("the source on setVoiceA is counted")
	}
	expect(t, "the volume column", one.Sources.At(1).Columns[2], []byte{255})
}

// Through a transformation on setR7 a source's rows keep the two port
// directions of rule 2(f), so key k reads as k + 192; a key of 64 or more
// is a line of the writer (SPEC.md 6.5).
func TestSetR7ThroughATransformation(t *testing.T) {
	ports := ymxs.Transformation{Name: "ports", Entries: make([]int, 64)}
	for k := range ports.Entries {
		ports.Entries[k] = 63 - k
	}
	mixer := ymxs.RepeatingSource("mixer", []int{0x38, 0, 63}, 0)
	one := made(t, tune([]ymxs.Row{on(ymxs.TimerA, struck(t, ymxs.R7, mixer,
		ymxs.Mapped(ports)))}, 0))
	source := one.Sources.At(1)
	if !source.Counted {
		t.Fatal("the source on setR7 is marked")
	}
	expect(t, "the source's rows", source.Columns[0], []byte{0xF8, 0xC0, 0xFF})
	expect(t, "column 30", mapping(one, 0), []byte{0x81})
	wide := ymxs.Transformation{Name: "wide", Entries: make([]int, 256)}
	keys := ymxs.RepeatingSource("keys", []int{0, 64}, 0)
	said := reported(t, tune([]ymxs.Row{on(ymxs.TimerA, struck(t, ymxs.R7, keys,
		ymxs.Mapped(wide)))}, 0))
	want := "the source keys has the key 64 in row 1 on setR7, and a key on setR7 is 0 to 63"
	if said != want {
		t.Fatalf("the source reports %q, and the line is %q", said, want)
	}
}

// Every row of a source on setR7 has bits 7 and 6 set, and a tick on setR0
// writes the row whole to R0: a source on both is an error of the writer,
// through a transformation or the identity, in either order (SPEC.md rule
// 2(f), 6.5).
func TestASourceOnSetR7AndOnAnotherTargetIsALine(t *testing.T) {
	gate := ymxs.RepeatingSource("gate", []int{56, 57}, 0)
	gated := ymxs.Transformation{Name: "gated", Entries: make([]int, 64)}
	for k := range gated.Entries {
		gated.Entries[k] = k
	}
	want := "the source gate runs on setR7 and on setR0, and every row of a source on" +
		" setR7 has bits 7 and 6 set (SPEC.md rule 2(f))"
	for _, mapped := range []ymxs.Mapping{ymxs.Mapped(gated), {}} {
		mixer := struck(t, ymxs.R7, gate, mapped)
		tone := struck(t, ymxs.R0, gate, ymxs.Mapping{})
		for _, rows := range [][]ymxs.Row{
			{on(ymxs.TimerA, mixer), on(ymxs.TimerB, tone)},
			{on(ymxs.TimerA, tone), on(ymxs.TimerB, mixer)},
		} {
			one := tune(rows, 0)
			if faults := check.Tune(one); len(faults) > 0 {
				t.Fatalf("the structure is wrong: %v", faults)
			}
			if said := reported(t, one); said != want {
				t.Fatalf("the source reports %q, and the line is %q", said, want)
			}
		}
	}
}

// rate is a rate of the divisor by and count, both resets clear.
func rate(t *testing.T, by, count int) ymxs.Timing {
	t.Helper()
	prescaler, err := ymxs.PrescalerBy(by)
	if err != nil {
		t.Fatal(err)
	}
	return ymxs.Timing{Prescaler: prescaler, Count: count}
}

// startAt is a start of the source on the register at the timing, through
// the identity, deferred or at its row.
func startAt(t *testing.T, register ymxs.Register, source ymxs.Single, timing ymxs.Timing,
	deferred bool) ymxs.Start {
	t.Helper()
	start, err := ymxs.Starting(ymxs.Setting(register), source, timing, deferred,
		ymxs.Mapping{})
	if err != nil {
		t.Fatal(err)
	}
	return start
}

// retunedAtTheRepeatRow is three rows that repeat to row 1: a start at the
// divisor 4 and count 100, a retune to the same rate, then a retune to the
// divisor by and count, the rate after the last row.
func retunedAtTheRepeatRow(t *testing.T, by, count int) ymxs.Tune {
	return tune([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, ymxs.Mapping{})),
		on(ymxs.TimerA, ymxs.Retune{Timing: rate(t, 4, 100)}),
		on(ymxs.TimerA, ymxs.Retune{Timing: rate(t, by, count)}),
	}, 1)
}

// both is a row of one operation on each of Timers A and D.
func both(a, d ymxs.Effect) ymxs.Row {
	return ymxs.Row{Effects: map[ymxs.Timer]ymxs.Effect{ymxs.TimerA: a, ymxs.TimerD: d}}
}

// ratesOfTheStructure reads the record of the tune over one pass and one
// loop (SPEC.md 7.4) against the rates of its rows, and its table against
// rules 4(b) and 4(d). At the frame of each start and retune of the
// structure, the kept select and count of its effect are the operation's
// (YMXS, SPEC.md 7.4); a start that leaves the control column unset follows
// a start of a repeating source with bit 3 at 0 and no stop between; and
// the first start or retune after a deferred start sets both rate columns.
// It returns the starts and retunes of the repeat row read a second time.
func ratesOfTheStructure(t *testing.T, one ymxs.Tune) int {
	t.Helper()
	tuneFile := file(t, made(t, one))
	read, err := ymxr.Read(tuneFile)
	if err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSuffix(string(trace.Record(tuneFile, -1)), "\n"),
		"\n")
	entry := regexp.MustCompile(`"(\d)":\{"target":\d+,"source":\d+,"select":(\d+),` +
		`"count":(\d+)`)
	rows := ymxs.Rows(one)
	repeat, _ := one.Table.Repeat()
	sources := ymxs.Sources(one)
	var selects, count [4]int
	var counting, deferred [4]bool
	again := 0
	for k := 0; k+1 < len(lines); k++ {
		r := k
		if k >= len(rows) {
			r = repeat + (k-len(rows))%(len(rows)-repeat)
		}
		for _, found := range entry.FindAllStringSubmatch(lines[k+1], -1) {
			i, _ := strconv.Atoi(found[1])
			selects[i], _ = strconv.Atoi(found[2])
			count[i], _ = strconv.Atoi(found[3])
		}
		for i := 0; i < 4; i++ {
			source := int(read.Table.Column(ymxr.Effect + 4*i + 1)[r])
			control := int(read.Table.Column(ymxr.Effect + 4*i + 2)[r])
			if source == 0x80 {
				counting[i] = false
			} else if source > 0x80 {
				_, repeats := ymxs.SourceRows(sources[source-0x81]).Repeat()
				if control&0x80 == 0 {
					if !counting[i] {
						t.Fatalf("frame %d: effect %d starts with the control column unset"+
							" on a stopped timer", k, i)
					}
					counting[i] = counting[i] && repeats
				} else {
					counting[i] = control&ymxr.Deferred == 0 && repeats
				}
			}
		}
		for _, op := range ymxs.Effects(rows[r]) {
			var timing ymxs.Timing
			starts := false
			switch e := op.Effect.(type) {
			case ymxs.Start:
				timing = e.Timing
				starts = e.Deferred
			case ymxs.Retune:
				timing = e.Timing
			default:
				continue
			}
			i := 0
			for schema.Timer(i) != op.Timer {
				i++
			}
			if deferred[i] {
				control := read.Table.Column(ymxr.Effect + 4*i + 2)[r]
				written := read.Table.Column(ymxr.Effect + 4*i + 3)[r]
				if control&0x80 == 0 || written == 0 && control&ymxr.CountValue == 0 {
					t.Fatalf("frame %d, row %d, effect %d: the first start or retune after a"+
						" deferred start sets the rate columns %#x and %#x", k, r, i, control,
						written)
				}
			}
			deferred[i] = starts
			if ymxr.Prescaler[selects[i]] != ymxs.Divides(timing.Prescaler) ||
				count[i] != timing.Count {
				t.Fatalf("frame %d, row %d, effect %d: the kept select %d and count %d, and"+
					" the structure's divisor %d and count %d", k, r, i, selects[i], count[i],
					ymxs.Divides(timing.Prescaler), timing.Count)
			}
			if k >= len(rows) && r == repeat {
				again++
			}
		}
	}
	return again
}

// rateColumns is the control and count columns of effect i at row r.
func rateColumns(one schema.Made, i, r int) []byte {
	return []byte{one.Columns.Column[ymxr.Effect+4*i+2][r],
		one.Columns.Column[ymxr.Effect+4*i+3][r]}
}

// The retune at the repeat row is at the count the timer counts at in the
// first pass, and the last row leaves another: the repeat row writes the
// count column, and the second pass records the count of the structure.
// The select agrees across the wrap, and the control column stays unset
// (SPEC.md rule 4(e)).
func TestTheRepeatRowWritesACountTheLastRowLeavesOtherwise(t *testing.T) {
	one := retunedAtTheRepeatRow(t, 4, 90)
	expect(t, "the rate columns of the repeat row", rateColumns(made(t, one), 0, 1),
		[]byte{0, 100})
	if again := ratesOfTheStructure(t, one); again != 1 {
		t.Fatalf("the repeat row read a second time has %d retunes", again)
	}
}

// The select the last row leaves differs from the select before the repeat
// row, and the count agrees: the repeat row writes the control column alone
// (SPEC.md rule 4(e)).
func TestTheRepeatRowWritesASelectTheLastRowLeavesOtherwise(t *testing.T) {
	one := retunedAtTheRepeatRow(t, 10, 100)
	expect(t, "the rate columns of the repeat row", rateColumns(made(t, one), 0, 1),
		[]byte{0x81, 0})
	if again := ratesOfTheStructure(t, one); again != 1 {
		t.Fatalf("the repeat row read a second time has %d retunes", again)
	}
}

// The timer counts before the repeat row and is stopped after the last row:
// the start at the repeat row writes both rate columns, so the select it
// writes starts the timer in the second pass (SPEC.md 4.3 step 5, rule
// 4(e)).
func TestTheRepeatRowStartsATimerTheLastRowStops(t *testing.T) {
	one := tune([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, ymxs.Mapping{})),
		on(ymxs.TimerA, startAt(t, ymxs.R8, other, rate(t, 4, 100), false)),
		on(ymxs.TimerA, ymxs.Stop{}),
	}, 1)
	expect(t, "the rate columns of the repeat row", rateColumns(made(t, one), 0, 1),
		[]byte{0x81, 100})
	if again := ratesOfTheStructure(t, one); again != 1 {
		t.Fatalf("the repeat row read a second time has %d starts", again)
	}
}

// A deferred start on the last row leaves the rate open across the wrap, so
// the start at the repeat row writes both rate columns (SPEC.md rule 4(d),
// 4(e)).
func TestAStartPendingAcrossTheWrapOpensTheRate(t *testing.T) {
	eight := ymxs.RepeatingSource("eight", []int{9, 8, 7, 6, 5, 4, 3, 2}, 0)
	one := tune([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, ymxs.Mapping{})),
		on(ymxs.TimerA, startAt(t, ymxs.R8, eight, rate(t, 4, 100), false)),
		on(ymxs.TimerA, startAt(t, ymxs.R8, other, rate(t, 4, 90), true)),
	}, 1)
	expect(t, "the rate columns of the repeat row", rateColumns(made(t, one), 0, 1),
		[]byte{0x81, 100})
	if again := ratesOfTheStructure(t, one); again != 1 {
		t.Fatalf("the repeat row read a second time has %d starts", again)
	}
}

// A deferred start on the last row leaves the rate open across the wrap, so
// the retune at the repeat row sets both rate columns (SPEC.md rule 4(d),
// 4(e)). The record keeps 100 at the repeat row before the fix and after
// it; 9f66c42 left both columns unset there, and the timer counted at 90 in
// the second pass until the tick that performs the start.
func TestTheFirstRetuneAfterADeferredStartAcrossTheWrapSetsBothColumns(t *testing.T) {
	one := tune([]ymxs.Row{
		on(ymxs.TimerA, struck(t, ymxs.R8, square, ymxs.Mapping{})),
		on(ymxs.TimerA, ymxs.Retune{Timing: rate(t, 4, 100)}),
		on(ymxs.TimerA, ymxs.Retune{Timing: rate(t, 4, 90)}),
		on(ymxs.TimerA, startAt(t, ymxs.R8, other, rate(t, 4, 100), true)),
	}, 1)
	expect(t, "the rate columns of the repeat row", rateColumns(made(t, one), 0, 1),
		[]byte{0x81, 100})
	if again := ratesOfTheStructure(t, one); again != 1 {
		t.Fatalf("the repeat row read a second time has %d retunes", again)
	}
}

// Where the last row leaves the select, the count and the counting flag of
// each effect as they are before the repeat row, the repeat row leaves its
// rate columns unset as in the first pass, and the tune file is byte for
// byte the one 9f66c42 wrote.
func TestARateThatAgreesAcrossTheWrapIsLeftUnset(t *testing.T) {
	first, err := ymxs.PrescalerBy(4)
	if err != nil {
		t.Fatal(err)
	}
	one := tune([]ymxs.Row{
		both(struck(t, ymxs.R8, square, ymxs.Mapping{}), startAt(t, ymxs.R9, other,
			ymxs.Timing{Prescaler: first, Count: 60, TimerReset: true, PlaceReset: true},
			false)),
		both(ymxs.Retune{Timing: rate(t, 4, 100)}, startAt(t, ymxs.R9, square,
			rate(t, 4, 60), false)),
		both(ymxs.Retune{Timing: rate(t, 10, 90)}, startAt(t, ymxs.R9, other,
			rate(t, 4, 100), false)),
		both(ymxs.Retune{Timing: rate(t, 4, 100)}, startAt(t, ymxs.R9, square,
			rate(t, 4, 60), false)),
	}, 1)
	columns := made(t, one)
	for i := 0; i < 2; i++ {
		expect(t, fmt.Sprintf("the rate columns of effect %d at the repeat row", i),
			rateColumns(columns, i, 1), []byte{0, 0})
	}
	if again := ratesOfTheStructure(t, one); again != 2 {
		t.Fatalf("the repeat row read a second time has %d operations", again)
	}
	sum := sha256.Sum256(file(t, columns))
	if got := hex.EncodeToString(sum[:]); got !=
		"229eebd5b3a0e8040525fc4d79cef42044ed9fc0993eae5375d19ef6548fb197" {
		t.Fatalf("the tune file has the sha256 %s", got)
	}
}
