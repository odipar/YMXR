package sndh_test

import (
	"bytes"
	"fmt"
	"strings"
	"testing"

	"github.com/odipar/ymxr/go/binaries"
	"github.com/odipar/ymxr/go/internal/built"
	"github.com/odipar/ymxr/go/sndh"
	"github.com/odipar/ymxr/go/ymxr"
)

func files(t *testing.T) (nine, ten []byte) {
	t.Helper()
	nine, err := built.File(built.Unmapped(), "")
	if err != nil {
		t.Fatal(err)
	}
	ten, err = built.File(built.Mapped(), "")
	if err != nil {
		t.Fatal(err)
	}
	return nine, ten
}

// block is the tune file's transformation index and transformations, from
// the long at 16 to the end of transformation T, the DTX3 table on the
// next long.
func block(t *testing.T, ten []byte) []byte {
	t.Helper()
	at := ymxr.GetLong(ten, ymxr.TransformationsAt)
	got := sndh.Block(ten)
	want := ten[at:ymxr.GetLong(ten, ymxr.TableAt)]
	if len(got) > len(want) || !bytes.Equal(want[:len(got)], got) ||
		ymxr.Align(at+len(got)) != at+len(want) {
		t.Fatalf("the block is %d bytes at %d, and the file has %d to the table", len(got),
			at, len(want))
	}
	return got
}

func TestABoundTuneOfVersion10HasTheBlockAfterTheImage(t *testing.T) {
	_, ten := files(t)
	bound, err := sndh.Bound(ten)
	if err != nil {
		t.Fatal(err)
	}
	if v := ymxr.GetWord(bound, 4); v != ymxr.Version {
		t.Fatalf("the bound tune is version %d", v)
	}
	sources := int(bound[ymxr.CountAt])
	first := ymxr.GetLong(bound, 28) &^ ymxr.Counted
	if want := ymxr.Align(28 + 4*sources); first != want {
		t.Fatalf("DTX1 table 1 is at %d, and align(28 + 4S) is %d", first, want)
	}
	image := ymxr.GetLong(bound, sndh.ImageAt)
	at := ymxr.GetLong(bound, sndh.BoundTransformationsAt)
	if image <= first || at <= image || at != ymxr.Align(at) {
		t.Fatalf("the block is at %d, the image at %d and source 1 at %d", at, image,
			first)
	}
	got := block(t, ten)
	if !bytes.Equal(bound[at:at+len(got)], got) || len(bound) != ymxr.Align(at+len(got)) {
		t.Fatalf("the block at %d of %d bytes is other than the tune file's", at,
			len(bound))
	}
	if ymxr.GetWord(bound, at) != 2 {
		t.Fatalf("the block counts %d transformations", ymxr.GetWord(bound, at))
	}
}

func TestABoundTuneOfVersion9HasTheIndexAt24(t *testing.T) {
	nine, _ := files(t)
	bound, err := sndh.Bound(nine)
	if err != nil {
		t.Fatal(err)
	}
	first := ymxr.GetLong(bound, sndh.BoundIndexAt) &^ ymxr.Counted
	if first != ymxr.Align(24+4*int(bound[ymxr.CountAt])) {
		t.Fatalf("DTX1 table 1 of version 9 is at %d", first)
	}
	if sndh.Block(nine) != nil {
		t.Fatal("a tune file of version 9 has a block")
	}
	if image := ymxr.GetLong(bound, sndh.ImageAt); image <= first {
		t.Fatalf("the image of version 9 is at %d, and source 1 at %d", image, first)
	}
}

func TestASetOf30And34ColumnsBindsIntoTwoImages(t *testing.T) {
	nine, ten := files(t)
	set, err := sndh.Bind([][]byte{nine, ten, nine})
	if err != nil {
		t.Fatal(err)
	}
	want := []string{"DTX3 at unit 1, values of 1",
		"DTX3 at unit 1, 34 columns, values of 1"}
	if fmt.Sprintf("%q", set.Shapes) != fmt.Sprintf("%q", want) {
		t.Fatalf("the shapes are %q, and the key names 34 columns alone: %q", set.Shapes,
			want)
	}
	if fmt.Sprint(set.Image) != "[0 1 0]" {
		t.Fatalf("the images are %v", set.Image)
	}
	if set.Blocks[0] != nil || set.Blocks[2] != nil ||
		!bytes.Equal(set.Blocks[1], block(t, ten)) {
		t.Fatal("the set's blocks are other than the tune files'")
	}
	one := set.Tunes[1]
	if ymxr.GetLong(one, sndh.ImageAt) != 0 ||
		ymxr.GetLong(one, sndh.BoundTransformationsAt) != 0 {
		t.Fatal("a bound tune of a set has the field at 16 or at 24 set")
	}
	source := ymxr.GetLong(ten, ymxr.IndexAt(ymxr.Version)) &^ ymxr.Counted
	if len(one) != ymxr.Align(28+4*int(one[ymxr.CountAt]))+ymxr.Align(len(ten)-source) {
		t.Fatalf("a bound tune of a set of version 10 is %d bytes", len(one))
	}
}

// mappingCore is the plain core with the lookup in, whose field at 18 is
// 10.
func mappingCore(t *testing.T) []byte {
	t.Helper()
	core, err := binaries.Read(binaries.Named(false, false, true, false, true))
	if err != nil {
		t.Fatal(err)
	}
	if v := ymxr.GetWord(core, 18); v != ymxr.Version {
		t.Fatalf("the core with the lookup reads bound tunes to version %d", v)
	}
	return core
}

func TestAnSndhFilePlacesEachBlockAfterTheImages(t *testing.T) {
	nine, ten := files(t)
	tunes := [][]byte{ten, nine, ten}
	file, err := sndh.With(mappingCore(t), tunes, sndh.Options{Title: "set"})
	if err != nil {
		t.Fatal(err)
	}
	record, err := sndh.Layout(file)
	if err != nil {
		t.Fatal(err)
	}
	set, err := sndh.Bind(tunes)
	if err != nil {
		t.Fatal(err)
	}
	key := "\"part\":\"core\",\"at\":"
	var core int
	fmt.Sscanf(record[strings.Index(record, key)+len(key):], "%d", &core)
	table := core + ymxr.GetLong(file, core+28)
	tune := func(i int) int { return core + ymxr.GetLong(file, table+2+4*i) }
	// the images end before the first block
	last := 0
	for i := range tunes {
		at := tune(i) + ymxr.GetLong(file, tune(i)+sndh.ImageAt)
		if end := at + len(set.Images[set.Image[i]]); end > last {
			last = end
		}
	}
	want := block(t, ten)
	end := last
	for _, i := range []int{0, 2} {
		at := tune(i) + ymxr.GetLong(file, tune(i)+sndh.BoundTransformationsAt)
		if at != core+ymxr.Align(end-core) || !bytes.Equal(file[at:at+len(want)], want) {
			t.Fatalf("subtune %d's block is at %d, and the part before it ends at %d", i+1,
				at, end)
		}
		end = at + len(want)
		if !strings.Contains(record, fmt.Sprintf("\"transformations\":%d}", at)) {
			t.Fatalf("the record of subtune %d reads no block at %d:\n%s", i+1, at, record)
		}
	}
	if work := core + ymxr.GetLong(file, core+32); work != end+end%2 {
		t.Fatalf("the workspace is at %d, and the last block ends at %d", work, end)
	}
	if ymxr.GetWord(file, tune(1)+4) != ymxr.Unmapped {
		t.Fatal("subtune 2 is other than version 9")
	}
}

// flagsOf is the flags word of the core under an SNDH file.
func flagsOf(file []byte) int {
	return ymxr.GetWord(file, (ymxr.GetWord(file, 2)+3)&^1+sndh.CoreFlagsAt)
}

func TestASetThatMapsAValueIsOnTheCoreWithTheLookupIn(t *testing.T) {
	nine, ten := files(t)
	file, err := sndh.Of([][]byte{nine, ten}, sndh.Options{Title: "set"})
	if err != nil {
		t.Fatalf("an SNDH file with version 10 reads %v", err)
	}
	if flags := flagsOf(file); flags != 4|16 {
		t.Fatalf("the core under a set that maps a value reads flags %d", flags)
	}
	file, err = sndh.Of([][]byte{nine}, sndh.Options{Title: "set"})
	if err != nil {
		t.Fatalf("an SNDH file of version 9 reads %v", err)
	}
	if flags := flagsOf(file); flags != 4 {
		t.Fatalf("the core under a set of version 9 reads flags %d", flags)
	}
}

func TestACoreThatReadsTo9ReportsTheVersionLine(t *testing.T) {
	nine, ten := files(t)
	plain, err := binaries.Read(binaries.Core)
	if err != nil {
		t.Fatal(err)
	}
	line := "the core reads bound tunes to version 9, and this binds at 10"
	_, err = sndh.With(plain, [][]byte{nine, ten}, sndh.Options{Title: "set"})
	if err == nil || err.Error() != line {
		t.Fatalf("an SNDH file with version 10 on the plain core reads %v, and the line"+
			" is %q", err, line)
	}
}

func TestACoreWithoutBit4ReportsTheLineOfTheLookup(t *testing.T) {
	_, ten := files(t)
	core := append([]byte(nil), mappingCore(t)...)
	ymxr.PutWord(core, sndh.CoreFlagsAt, 4)
	line := "the core's flags at 22 read 4, and a subtune that maps a value needs bit" +
		" 4 set"
	if _, err := sndh.With(core, [][]byte{ten}, sndh.Options{Title: "set"}); err == nil ||
		err.Error() != line {
		t.Fatalf("a core with bit 4 clear reads %v, and the line is %q", err, line)
	}
}

func TestTheReachCountsFromTheFirstHandler(t *testing.T) {
	_, ten := files(t)
	set, err := sndh.Bind([][]byte{ten})
	if err != nil {
		t.Fatal(err)
	}
	core := mappingCore(t)
	handlers := ymxr.GetWord(core, 26)
	end := (len(core)+1)&^1 + 2 + 4 + len(set.Tunes[0])
	if handlers == 0 || sndh.TunesEnd(core, set, nil) != end-handlers {
		t.Fatalf("the tunes end %d from the core's first byte and %d from the field at"+
			" 26, %d", end, sndh.TunesEnd(core, set, nil), handlers)
	}
	// the core padded so that the tune ends a byte past the reach from
	// its first handler
	padded := append(append([]byte(nil), core...),
		make([]byte, 32767-sndh.TunesEnd(core, set, nil)+2)...)
	line := fmt.Sprintf("the tunes end %d bytes past the core's first handler, at %d,"+
		" and a tick that reads a row through the program counter reaches 32767",
		sndh.TunesEnd(padded, set, nil), handlers)
	if _, err := sndh.With(padded, [][]byte{ten}, sndh.Options{Title: "far",
		Ticks: sndh.Pcrel}); err == nil || err.Error() != line {
		t.Fatalf("a set past the reach reads %v, and the line is %q", err, line)
	}
}

func TestTheCoresAreNamedByTheFiveSwitches(t *testing.T) {
	all := binaries.All()
	if len(all) != 33 || all[32] != binaries.Stub {
		t.Fatalf("the binaries are %d, the last %s", len(all), all[len(all)-1])
	}
	i := 0
	for _, mapped := range []bool{false, true} {
		for _, deferred := range []bool{false, true} {
			for _, pcrel := range []bool{true, false} {
				for _, lean := range []bool{false, true} {
					for _, monitor := range []bool{false, true} {
						if name := binaries.Named(monitor, lean, pcrel, deferred,
							mapped); name != all[i] {
							t.Fatalf("binary %d is %s, and the switches name %s", i,
								all[i], name)
						}
						i++
					}
				}
			}
		}
	}
	name := binaries.Named(true, true, false, true, true)
	if name != "YMXR_sndh-perf-lean-abs-defer-map.bin" {
		t.Fatalf("the five switches name %s", name)
	}
}

func TestTheRecordOfABoundTuneOfVersion10EndsTheImageAtTheBlock(t *testing.T) {
	_, ten := files(t)
	bound, err := sndh.Bound(ten)
	if err != nil {
		t.Fatal(err)
	}
	record, err := sndh.Layout(bound)
	if err != nil {
		t.Fatal(err)
	}
	image := ymxr.GetLong(bound, sndh.ImageAt)
	at := ymxr.GetLong(bound, sndh.BoundTransformationsAt)
	want := strings.Join([]string{
		fmt.Sprintf("{\"kind\":\"bound\",\"bytes\":%d}", len(bound)),
		fmt.Sprintf("{\"part\":\"header\",\"version\":10,\"rate\":50,\"effects\":%d,"+
			"\"sources\":1,\"stateblock\":%d,\"image\":%d,\"table\":%d,"+
			"\"transformations\":%d}", bound[ymxr.EffectsAt],
			ymxr.GetLong(bound, sndh.StateAt), image,
			ymxr.GetLong(bound, sndh.BoundTableAt), at),
		"{\"part\":\"source\",\"number\":1,\"at\":32}",
		fmt.Sprintf("{\"part\":\"image\",\"at\":%d,\"bytes\":%d}", image, at-image),
		fmt.Sprintf("{\"part\":\"transformations\",\"at\":%d,\"count\":2,"+
			"\"bytes\":%d}", at, len(bound)-at),
	}, "\n") + "\n"
	if record != want {
		t.Fatalf("the record is\n%sand reads\n%s", record, want)
	}
}

func TestTheRecordMasksBit31OfASourceEntry(t *testing.T) {
	_, ten := files(t)
	bound, err := sndh.Bound(ten)
	if err != nil {
		t.Fatal(err)
	}
	ymxr.PutLong(bound, 28, ymxr.GetLong(bound, 28)|ymxr.Counted)
	record, err := sndh.Layout(bound)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(record, "{\"part\":\"source\",\"number\":1,\"at\":32}\n") {
		t.Fatalf("a counted source reads its mark in the record:\n%s", record)
	}
}

func TestASetBindsAtVersion10WhereATuneFileIs(t *testing.T) {
	nine, ten := files(t)
	if v := sndh.Binds([][]byte{nine, nine}); v != ymxr.Unmapped {
		t.Fatalf("a set of version 9 binds at %d", v)
	}
	if v := sndh.Binds([][]byte{nine, ten}); v != ymxr.Version {
		t.Fatalf("a set with version 10 binds at %d", v)
	}
}
