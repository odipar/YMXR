package ymxr

import (
	"errors"
	"fmt"
	"strings"

	"github.com/odipar/dtx/go/dtx"
	"github.com/odipar/dtx/go/st4"

	"github.com/odipar/ymxr/go/report"
)

// A tune file (SPEC.md 3.3): a tune's fixed values, the tune's table as a
// DTX2 file, and one DTX1 table a source. The file contains the tune's
// tables and no code; a tool binds them with DTX's reader into what the
// player reads (doc/BINARIES.md).
//
//	offset  bytes  what it is
//	0       4      YMXR
//	4       2      the version, $0003
//	6       2      the frame rate, in Hz
//	8       1      effects used, bits 3 to 0
//	9       1      S, the source count, 0 to 127
//	10      2      where the name begins, or zero where the tune has none
//	12      4      where the DTX2 table begins
//	16      4S     the source index: where source 1 to S's DTX1 table begins
//	        ..     the DTX2 table, on a long
//	        ..     the DTX1 tables, each on a long
//
// Every offset counts from the file's first byte.

// Magic is the four bytes a tune file opens with.
var Magic = []byte{'Y', 'M', 'X', 'R'}

// Version is the version of this format.
// Version is the version of a tune whose sources are one column and whose
// target columns are 0 to 13 (SPEC.md 3.3.5).
const Version = 0x0003

// VersionColumns is the version of a tune with a source of several
// columns (SPEC.md 3.3.5), which a target of 14 upward runs.
const VersionColumns = 0x0004

// VersionOf is the version a tune of these sources is written at: the
// lower of the two it reads under, so a tune both versions encode is one
// file and a player of version 3 reads it (SPEC.md 3.3.5).
func VersionOf(sources []Source) int {
	for _, one := range sources {
		if one.Width() > 1 {
			return VersionColumns
		}
	}
	return Version
}

// Where each header field stands.
const (
	FrameRateAt = 6

	// MostRate is the largest frame rate the word at FrameRateAt reads. A
	// YMXS rate reaches 2,147,483,647 (YMXS, SPEC.md 1.3), so a writer
	// reads this bound rather than writing the low sixteen bits of a rate
	// above it.
	MostRate  = 65535
	EffectsAt = 8
	CountAt   = 9
	NameAt    = 10
	// MostName is the most bytes a name takes, its zero aside.
	MostName = 255
	TableAt  = 12
	IndexAt  = 16
)

// Ring is the ring a column unpacks through, dtx-write's default.
const Ring = 960

// MaxRing is the widest ring the player reads: column 29's value stands 29
// rings past column 0's, and the player reaches it through a 16-bit
// displacement.
const MaxRing = 32767 / (C - 1)

// Written is a tune file written: the file, and the row it repeats to, R
// where it plays once.
type Written struct {
	File   []byte
	Repeat int
}

// Write is the file: its table packed at unit through a ring of that many
// bytes, as a DTX2 file.
//
// The table is the tune's rows, the row it repeats to the tune's, and
// this adds no row. A column's bytes and its loop begin on a unit (DTX's
// R5.6 and R5.11), which PadToUnit provides before the columns are made
// (SPEC.md 6, rule 6); a table that does not divide packs at unit 1. The
// table packs at a period of C rows, the smallest DTX allows, since
// a refill decodes a period's rows of one column at once and a refill
// costs a period; the ring is a multiple of C for that, the one nearest
// what was asked for within what the player reaches. A loop longer
// than the ring is replayed at its exact rows by DTX's reader.
func Write(columns Columns, sources *Sources, frameRate, unit, ring int,
	said *report.Report) (Written, error) {
	return WriteNamed(columns, sources, frameRate, unit, ring, st4.Packer{}, said, "")
}

// WriteWith is the same, packed by the packer the tool's flags asked for.
func WriteWith(columns Columns, sources *Sources, frameRate, unit, ring int,
	packer dtx.Packer, said *report.Report) (Written, error) {
	return WriteNamed(columns, sources, frameRate, unit, ring, packer, said, "")
}

// namedBytes is what a name comes to in the file: its UTF-8 and a zero,
// cut to MostName bytes. A name of no printable characters writes none,
// and the file records zero for it. Bytes under a space are dropped, since
// a name reaches an ST screen and an SNDH tag.
func namedBytes(name string) []byte {
	kept := make([]rune, 0, len(name))
	for _, one := range name {
		if one >= ' ' {
			kept = append(kept, one)
		}
	}
	said := []byte(strings.TrimSpace(string(kept)))
	if len(said) == 0 {
		return said
	}
	cut := len(said)
	if cut > MostName {
		cut = MostName
	}
	// a cut lands on a whole character, so the name stays UTF-8
	for cut > 0 && cut < len(said) && said[cut]&0xC0 == 0x80 {
		cut--
	}
	out := make([]byte, cut+1)
	copy(out, said[:cut])
	return out
}

// TuneName is the name a tune file records, or an empty string where it
// records none. A file written before the name reads as none, since the
// word was zero there.
func TuneName(file []byte) string {
	if len(file) < IndexAt {
		return ""
	}
	at := GetWord(file, NameAt)
	if at == 0 || at >= len(file) {
		return ""
	}
	end := at
	for end < len(file) && file[end] != 0 {
		end++
	}
	return string(file[at:end])
}

// WriteNamed is the same, with the tune named.
func WriteNamed(columns Columns, sources *Sources, frameRate, unit, ring int,
	packer dtx.Packer, said *report.Report, name string) (Written, error) {
	if frameRate < 1 || frameRate > MostRate {
		return Written{}, fmt.Errorf("a frame rate of %d, and the frame rate is a"+
			" word, 1 to %d", frameRate, MostRate)
	}
	frames := len(columns.Column[0])
	repeat := columns.Repeat
	// PadToUnit runs before this in every tool (SPEC.md 6, rule 6), so a
	// table reaching here divides; one from a caller that skipped it packs
	// at unit 1, which the note reports.
	if unit > 1 && (frames%unit != 0 || (repeat < frames && repeat%unit != 0)) {
		what := fmt.Sprintf("the repeat row %d", repeat)
		if frames%unit != 0 {
			what = fmt.Sprintf("the row count %d", frames)
		}
		said.Note(fmt.Sprintf("packed at unit 1: %s does not divide by %d", what, unit))
		unit = 1
	}
	at := RingOf(ring)
	if at != ring {
		said.Note(fmt.Sprintf("the ring is %d bytes: a multiple of the period within"+
			" the player's reach", at))
	}
	watched := &watched{said: said, bytes: make([]int, C), inner: packer}
	table, err := packTable(columns.Column, frames, repeat, unit, at, watched)
	if err != nil {
		return Written{}, err
	}
	all := sources.All()
	tables := make([][]byte, len(all))
	sourceRows := 0
	sourceBytes := 0
	for i, one := range all {
		built, err := dtx.NewTable(one.Rows(), one.Repeat, 1, one.Columns)
		if err != nil {
			return Written{}, err
		}
		tables[i] = dtx.WriteDtx1(built)
		sourceRows += one.Rows()
		sourceBytes += len(tables[i])
	}
	named := namedBytes(name)
	after := IndexAt + 4*len(tables)
	nameAt := 0
	if len(named) != 0 {
		nameAt = after
	}
	here := Align(after + len(named))
	tableAt := here
	here = Align(here + len(table))
	sourceAt := make([]int, len(tables))
	for i := range tables {
		sourceAt[i] = here
		here = Align(here + len(tables[i]))
	}
	file := make([]byte, here)
	copy(file, Magic)
	PutWord(file, 4, VersionOf(all))
	PutWord(file, FrameRateAt, frameRate)
	file[EffectsAt] = byte(columns.Effects)
	file[CountAt] = byte(len(tables))
	PutWord(file, NameAt, nameAt)
	PutLong(file, TableAt, tableAt)
	copy(file[nameAt:], named)
	for i := range tables {
		PutLong(file, IndexAt+4*i, sourceAt[i])
	}
	copy(file[tableAt:], table)
	for i := range tables {
		copy(file[sourceAt[i]:], tables[i])
	}
	packed(said, watched, frames, len(table), len(tables), sourceRows, sourceBytes,
		at, unit, len(file))
	if repeat > frames {
		repeat = frames
	}
	return Written{File: file, Repeat: repeat}, nil
}

// packTable is the columns as a DTX2 file, packed.
func packTable(column [][]byte, frames, repeat, unit, ring int,
	packer dtx.Packer) ([]byte, error) {
	rr := repeat
	if rr > frames {
		rr = frames
	}
	table, err := dtx.NewTable(frames, rr, 1, column)
	if err != nil {
		return nil, err
	}
	return dtx.WriteDtx2(table, packer, unit, ring)
}

// watched is a packer that reports: it packs a column through the one the
// file reads, records what each column came to, and reports how far
// through the thirty it is.
type watched struct {
	inner dtx.Packer
	said  *report.Report
	bytes []int
	done  int
}

func (w *watched) Pack(column []byte, unit, ring, loop int) ([]byte, error) {
	w.said.Progress("packing the columns", w.done, len(w.bytes))
	out, err := w.inner.Pack(column, unit, ring, loop)
	if err != nil {
		return nil, err
	}
	if w.done < len(w.bytes) {
		w.bytes[w.done] = len(out)
	}
	w.done++
	return out, nil
}

func (w *watched) Copies() bool {
	return w.inner.Copies()
}

// packed says what the packing came to, a column at a time and in all.
func packed(said *report.Report, packer *watched, frames, table, sources,
	sourceRows, sourceBytes, ring, unit, file int) {
	if !said.Says() {
		return
	}
	said.Say(fmt.Sprintf("the table: %d columns of %d rows, %d bytes, packed at unit %d"+
		" through a ring of %d", C, frames, C*frames, unit, ring))
	for c := 0; c < C; c++ {
		bytes := packer.bytes[c]
		said.Row(Name(c), fmt.Sprintf("%7d -> %6d bytes  (%5.1f%%)", frames, bytes,
			100.0*float64(bytes)/float64(frames)))
	}
	rows, tables := " rows, ", " tables of "
	if sourceRows == 1 {
		rows = " row, "
	}
	if sources == 1 {
		tables = " table of "
	}
	said.Say(fmt.Sprintf("the sources: %d%s%d%s%d bytes", sources, tables, sourceRows,
		rows, sourceBytes))
	raw := C * frames
	said.Say(fmt.Sprintf("packed %d bytes into %d (%.1f%%), the file %d bytes",
		raw, table, 100.0*float64(table)/float64(raw), file))
}

// RingOf is the ring the table packs through: the multiple of C nearest
// the one asked for, at least two periods and at most what the player
// reaches.
func RingOf(ring int) int {
	nearest := int((float64(ring)/C)+0.5) * C
	if most := MaxRing / C * C; nearest > most {
		nearest = most
	}
	if nearest < 2*C {
		nearest = 2 * C
	}
	return nearest
}

// Align rounds an offset up to a long.
func Align(at int) int {
	return (at + 3) &^ 3
}

// PutWord writes a 16-bit value, most significant byte first.
func PutWord(b []byte, at, value int) {
	b[at] = byte(value >> 8)
	b[at+1] = byte(value)
}

// PutLong writes a 32-bit value, most significant byte first.
func PutLong(b []byte, at, value int) {
	PutWord(b, at, int(uint32(value)>>16))
	PutWord(b, at+2, value)
}

// GetWord reads a 16-bit value.
func GetWord(b []byte, at int) int {
	return int(b[at])<<8 | int(b[at+1])
}

// GetLong reads a 32-bit value.
func GetLong(b []byte, at int) int {
	return GetWord(b, at)<<16 | GetWord(b, at+2)
}

// File is a tune file read back: its header fields, its DTX2 table as the
// bytes in the file and as the table unpacked out of them, and its
// sources. What a reader reports (R2.4).
type File struct {
	Version   int
	FrameRate int
	Effects   int
	Dtx2      []byte
	Table     *dtx.Table
	Sources   []*dtx.Table
}

// Read is the tune file in those bytes.
func Read(file []byte) (File, error) {
	if len(file) < IndexAt || string(file[:4]) != string(Magic) {
		return File{}, errors.New("not a YMXR file")
	}
	version := GetWord(file, 4)
	if version != Version && version != VersionColumns {
		return File{}, fmt.Errorf("version %d is not %d or %d", version, Version,
			VersionColumns)
	}
	count := int(file[CountAt])
	tableAt := GetLong(file, TableAt)
	end := len(file)
	if count != 0 {
		end = GetLong(file, IndexAt)
	}
	if tableAt < 0 || end > len(file) || tableAt > end {
		return File{}, fmt.Errorf("the table stands at %d to %d, and the file has %d"+
			" bytes", tableAt, end, len(file))
	}
	dtx2 := file[tableAt:end]
	variant := 0
	if len(dtx2) > 3 {
		variant = int(dtx2[3])
	}
	if variant != dtx.DTX2 {
		return File{}, fmt.Errorf("the table is DTX%d, and a tune's table is DTX2"+
			" (SPEC.md 3.3.3)", variant)
	}
	table, err := dtx.Read(dtx2)
	if err != nil {
		return File{}, err
	}
	if table.Columns() != C || table.Width() != 1 {
		return File{}, fmt.Errorf("the table is %d columns of %d bytes, and a tune's"+
			" table is %d of one (SPEC.md 3.3.3)", table.Columns(), table.Width(), C)
	}
	var sources []*dtx.Table
	for i := 0; i < count; i++ {
		at := GetLong(file, IndexAt+4*i)
		to := len(file)
		if i+1 < count {
			to = GetLong(file, IndexAt+4*(i+1))
		}
		if at < 0 || to > len(file) || at > to {
			return File{}, fmt.Errorf("source %d stands at %d to %d, and the file has"+
				" %d bytes", i+1, at, to, len(file))
		}
		source, err := dtx.Read(file[at:to])
		if err != nil {
			return File{}, err
		}
		// SPEC.md 3.1.3: a source is one, two or three columns of one byte,
		// the row shape the target that runs it reads (2.1). A wider value
		// is a later version's, and the player would read its rows a byte
		// at a time and play something else, so it is rejected here as a
		// tune of another version is.
		if source.Columns() < 1 || source.Columns() > 3 || source.Width() != 1 {
			return File{}, fmt.Errorf("source %d is %d columns of %d bytes, and a"+
				" source is one, two or three columns of one (SPEC.md 3.1)", i+1,
				source.Columns(), source.Width())
		}
		// SPEC.md 3.3.5: version 3 writes a source of one column, so a
		// wider one under that version is a file written wrong rather than
		// a tune of a version this reads.
		if version == Version && source.Columns() > 1 {
			return File{}, fmt.Errorf("source %d is %d columns, and version %d writes"+
				" one", i+1, source.Columns(), Version)
		}
		sources = append(sources, source)
	}
	return File{Version: version, FrameRate: GetWord(file, FrameRateAt),
		Effects: int(file[EffectsAt]), Dtx2: dtx2, Table: table, Sources: sources}, nil
}
