package ymxr

import (
	"errors"
	"fmt"
	"sort"
	"strings"
	"sync/atomic"

	"github.com/odipar/dtx/go/dtx"
	"github.com/odipar/dtx/go/pack"
	"github.com/odipar/dtx/go/st4"

	"github.com/odipar/ymxr/go/report"
)

// A tune file (SPEC.md 3.3): a tune's fixed values, the tune's table as a
// DTX3 file, and one DTX1 table a source. The file contains the tune's
// tables and no code; a tool binds them with DTX's reader into what the
// player reads (doc/BINARIES.md).
//
//	offset  bytes  what it is
//	0       4      YMXR
//	4       2      the version, $0007
//	6       2      the frame rate, in Hz
//	8       1      effects used, bits 3 to 0
//	9       1      S, the source count, 0 to 127
//	10      2      where the name begins, or zero where the tune has none
//	12      4      where the DTX3 table begins
//	16      4S     the source index: where source 1 to S's DTX1 table begins
//	        ..     the DTX3 table, on a long
//	        ..     the DTX1 tables, each on a long
//
// Every offset counts from the file's first byte.

// Magic is the four bytes a tune file opens with.
var Magic = []byte{'Y', 'M', 'X', 'R'}

// Version is the version of every tune file (SPEC.md 3.3.5): its table is
// DTX3, which a player of an earlier version reads wrongly.
const Version = 0x0007

// VersionDtx2 and VersionDtx2Last are the first and the last version whose
// table is DTX2, which a reader reports with the tool that writes the tune
// again (SPEC.md 3.3.4).
const (
	VersionDtx2     = 0x0003
	VersionDtx2Last = 0x0006
)

// Counted is bit 31 of a source's index entry: the source's rows are
// whole bytes and a player counts them (SPEC.md 3.1).
const Counted = 1 << 31

// Where each header field is.
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
	// MostName is the most bytes a name reaches, its zero aside.
	MostName = 255
	TableAt  = 12
	IndexAt  = 16
)

// Choose, as a writer's ring, has the writer choose one of Rings (SPEC.md
// 3.3.3): no ring is named.
const Choose = 0

// Rings are the rings a writer chooses among: two periods of thirty rows
// and each double of that, up to what the player reaches.
var Rings = []int{60, 120, 240, 480, 960, 1920}

// Reach is the furthest the player reads a column from the row pointer: a
// 16-bit displacement.
const Reach = 32767

// Plain is the packer of a live column by default: ST4 without copies
// from the literal stream (SPEC.md 3.3.3).
func Plain() []dtx.Packer {
	return []dtx.Packer{st4.Packer{}}
}

// Packers are the packers a live column packs with under -copies: ST4 with
// copies and without, the smaller kept, the copies searched for seconds
// beyond ST4's opening passes.
func Packers(seconds float64) []dtx.Packer {
	return []dtx.Packer{st4.Packer{}, st4.Packer{CopiesFlag: true, Seconds: seconds}}
}

// Written is a tune file written: the file, and the row it repeats to, R
// where it plays once.
type Written struct {
	File   []byte
	Repeat int
}

// Write is the file: its table packed at unit through a ring of that many
// bytes, or the ring the writer chooses where ring is Choose, as a DTX3
// file.
//
// The table is the tune's rows, the row it repeats to the tune's, and
// this adds no row. A column's bytes and its loop begin on a unit (DTX's
// R8.9), which PadToUnit provides before the columns are made (SPEC.md 6,
// rule 6); a table that does not divide packs at unit 1. A constant
// column is its value, and each live column is packed by every packer
// named, the smallest kept: with copies from the literal stream and
// without, the smaller kept, where -copies names both. The table packs at a
// period of C rows, the smallest DTX allows, since a refill decodes a
// period's rows of one column at once and a refill costs a period; the
// ring is a multiple of C for that. The writer packs the table with the
// first packer through each of Rings the player reaches and keeps the one
// whose bytes and state block come to the least; with a second packer, it
// packs with every packer through the two that came to the least and keeps
// the one of those two that comes to the less. A ring named is the multiple
// of C nearest it within that reach. A loop longer than the ring is
// replayed at its exact rows by DTX's reader.
func Write(columns Columns, sources *Sources, frameRate, unit, ring int,
	said *report.Report) (Written, error) {
	return WriteNamed(columns, sources, frameRate, unit, ring, Plain(), said, "")
}

// WriteWith is the same, packed by the packers the tool's flags asked for.
func WriteWith(columns Columns, sources *Sources, frameRate, unit, ring int,
	packers []dtx.Packer, said *report.Report) (Written, error) {
	return WriteNamed(columns, sources, frameRate, unit, ring, packers, said, "")
}

// namedBytes is a name as the file has it: its UTF-8 and a zero,
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
	packers []dtx.Packer, said *report.Report, name string) (Written, error) {
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
	live := Live(columns.Column)
	var rings []int
	if ring == Choose {
		for _, one := range Rings {
			if one <= ReachOf(live) {
				rings = append(rings, one)
			}
		}
	} else {
		at := RingOf(ring, live)
		if at != ring {
			said.Note("the ring is " + report.Count(at, "byte", "bytes") +
				": a multiple of the period within the player's reach")
		}
		rings = append(rings, at)
	}
	// The copies search is the most of a packing's time, so every ring is
	// tried with the first packer alone, and the two that come to the least
	// with every packer, where there is a second.
	tried := 1
	if len(packers) > 1 && len(rings) > 1 {
		tried = 2
	}
	total := 0
	if len(packers) > 1 || len(rings) == 1 {
		total = live * len(packers) * tried
	}
	if len(rings) > 1 {
		total += live * len(rings)
	}
	count := &watched{said: said, total: total}
	first := []dtx.Packer{counted{inner: packers[0], count: count}}
	every := make([]dtx.Packer, len(packers))
	for i, packer := range packers {
		every[i] = counted{inner: packer, count: count}
	}
	var table []byte
	at := 0
	least := -1
	if len(rings) > 1 {
		// A ring the packager finds no period for at this unit is left out
		// (DTX abi.md 4): at a unit of 4 a period divides by 4.
		plain := map[int]int{}
		var cheapest []int
		for _, one := range rings {
			made, err := packTable(columns.Column, frames, repeat, unit, one, first)
			if err != nil {
				return Written{}, err
			}
			state, err := State(made)
			if err != nil {
				said.Note(fmt.Sprintf("a ring of %d has no period at unit %d", one, unit))
				continue
			}
			plain[one] = len(made) + state
			cheapest = append(cheapest, one)
			if cost := len(made) + state; least < 0 || cost < least {
				least, table, at = cost, made, one
			}
		}
		if len(cheapest) == 0 {
			return Written{}, fmt.Errorf("no ring the writer chooses among has a period"+
				" at unit %d (DTX abi.md 4)", unit)
		}
		sort.SliceStable(cheapest, func(a, b int) bool {
			return plain[cheapest[a]] < plain[cheapest[b]]
		})
		if tried > len(cheapest) {
			tried = len(cheapest)
		}
		// With one packer the pass above is the packing: its least is the
		// table.
		if len(packers) > 1 {
			rings, least = cheapest[:tried], -1
		} else {
			rings = nil
		}
	}
	for _, one := range rings {
		made, err := packTable(columns.Column, frames, repeat, unit, one, every)
		if err != nil {
			return Written{}, err
		}
		state, err := State(made)
		if err != nil {
			return Written{}, err
		}
		if cost := len(made) + state; least < 0 || cost < least ||
			cost == least && one < at {
			least, table, at = cost, made, one
		}
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
	PutWord(file, 4, Version)
	PutWord(file, FrameRateAt, frameRate)
	file[EffectsAt] = byte(columns.Effects)
	file[CountAt] = byte(len(tables))
	PutWord(file, NameAt, nameAt)
	PutLong(file, TableAt, tableAt)
	copy(file[nameAt:], named)
	for i := range tables {
		entry := sourceAt[i]
		if all[i].Counted {
			entry |= Counted
		}
		PutLong(file, IndexAt+4*i, entry)
	}
	copy(file[tableAt:], table)
	for i := range tables {
		copy(file[sourceAt[i]:], tables[i])
	}
	if err := packed(said, table, frames, len(tables), sourceRows, sourceBytes, at, unit,
		ring == Choose, len(file)); err != nil {
		return Written{}, err
	}
	if repeat > frames {
		repeat = frames
	}
	return Written{File: file, Repeat: repeat}, nil
}

// packTable is the columns as a DTX3 file, packed.
func packTable(column [][]byte, frames, repeat, unit, ring int,
	packers []dtx.Packer) ([]byte, error) {
	rr := repeat
	if rr > frames {
		rr = frames
	}
	table, err := dtx.NewTable(frames, rr, 1, column)
	if err != nil {
		return nil, err
	}
	return dtx.WriteDtx3(table, packers, unit, ring)
}

// State is the state block a reader of this DTX3 table needs, in bytes, as
// the format block of its image defines it (DTX abi.md 1).
func State(table []byte) (int, error) {
	header, err := dtx.ReadHeader(table)
	if err != nil {
		return 0, err
	}
	return pack.AnyStateBytes(table, header)
}

// Live is the columns whose values change, which DTX3 packs: every other
// is constant and is its value.
func Live(column [][]byte) int {
	live := 0
	for _, c := range column {
		for _, b := range c {
			if b != c[0] {
				live++
				break
			}
		}
	}
	return live
}

// ReachOf is the widest ring the player reaches with live live columns: the
// last slot is that many less one rings past the first, through a 16-bit
// displacement, and DTX3 bounds a ring at 32,512.
func ReachOf(live int) int {
	if live < 2 || Reach/(live-1) > dtx.MaxRingDtx3 {
		return dtx.MaxRingDtx3
	}
	return Reach / (live - 1)
}

// watched counts how far through the packing a write is: the count runs
// over each pack of a live column. DTX packs the columns at once, so the
// count is one every pack shares.
type watched struct {
	said  *report.Report
	total int
	done  atomic.Int64
}

// counted is a packer that reports through a watched count.
type counted struct {
	inner dtx.Packer
	count *watched
}

func (c counted) Pack(column []byte, unit, ring, loop int) ([]byte, error) {
	c.count.said.Progress("packing the columns", int(c.count.done.Add(1)-1), c.count.total)
	return c.inner.Pack(column, unit, ring, loop)
}

func (c counted) Copies() bool {
	return c.inner.Copies()
}

// packed says what the packing came to, a column at a time and in all.
func packed(said *report.Report, table []byte, frames, sources, sourceRows, sourceBytes,
	ring, unit int, chose bool, file int) error {
	if !said.Says() {
		return nil
	}
	header, err := dtx.ReadHeader(table)
	if err != nil {
		return err
	}
	payload, err := dtx.ReadPayload3(table, header)
	if err != nil {
		return err
	}
	state, err := State(table)
	if err != nil {
		return err
	}
	chosen := ""
	if chose {
		chosen = ", the one the writer chose"
	}
	said.Say(fmt.Sprintf("the table: %s of %s, %s, packed at unit %d through a ring of %d%s, %s",
		report.Count(C, "column", "columns"), report.Count(frames, "row", "rows"),
		report.Count(C*frames, "byte", "bytes"), unit, ring, chosen,
		report.Count(payload.Live(), "live column", "live columns")))
	for c := 0; c < C; c++ {
		if !payload.IsLive(c) {
			said.Row(Name(c), fmt.Sprintf("%7d -> constant %d", frames, payload.Constants[c]))
			continue
		}
		bytes := payload.Bytes(payload.Map[c], len(table)-dtx.HeaderLength)
		said.Row(Name(c), fmt.Sprintf("%7d -> %6d bytes  (%5.1f%%)", frames, bytes,
			100.0*float64(bytes)/float64(frames)))
	}
	said.Say(fmt.Sprintf("the sources: %s of %s, %s", report.Count(sources, "table", "tables"),
		report.Count(sourceRows, "row", "rows"), report.Count(sourceBytes, "byte", "bytes")))
	raw := C * frames
	said.Say(fmt.Sprintf("packed %s into %d (%.1f%%), the state block %s, the file %s",
		report.Count(raw, "byte", "bytes"), len(table),
		100.0*float64(len(table))/float64(raw), report.Count(state, "byte", "bytes"),
		report.Count(file, "byte", "bytes")))
	return nil
}

// RingOf is the ring the table packs through: the multiple of C nearest
// the one asked for, at least two periods and at most what the player
// reaches with live live columns.
func RingOf(ring, live int) int {
	nearest := int((float64(ring)/C)+0.5) * C
	if most := ReachOf(live) / C * C; nearest > most {
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

// File is a tune file read back: its header fields, its DTX3 table as the
// bytes in the file and as the table unpacked out of them, and its
// sources. What a reader reports (R2.4).
type File struct {
	Version   int
	FrameRate int
	Effects   int
	Dtx3      []byte
	Table     *dtx.Table
	Sources   []*dtx.Table
	Counted   []bool
}

// Read is the tune file in those bytes.
func Read(file []byte) (File, error) {
	if len(file) < IndexAt || string(file[:4]) != string(Magic) {
		return File{}, errors.New("not a YMXR file")
	}
	version := GetWord(file, 4)
	// SPEC.md 3.3.5: versions 3 to 6 have a DTX2 table, which a player of
	// this version reads wrongly, and the tools write the tune again from
	// what it was converted from.
	if version >= VersionDtx2 && version <= VersionDtx2Last {
		return File{}, fmt.Errorf("version %d has a DTX2 table, and this reads %d: write"+
			" the tune again with ym-to-ymxr or ymxs-to-ymxr", version, Version)
	}
	if version != Version {
		return File{}, fmt.Errorf("version %d is not %d", version, Version)
	}
	count := int(file[CountAt])
	tableAt := GetLong(file, TableAt)
	end := len(file)
	if count != 0 {
		end = GetLong(file, IndexAt) &^ Counted
	}
	if tableAt < 0 || end > len(file) || tableAt > end {
		return File{}, fmt.Errorf("the table is at %d to %d, and the file has %d"+
			" bytes", tableAt, end, len(file))
	}
	dtx3 := file[tableAt:end]
	variant := 0
	if len(dtx3) > 3 {
		variant = int(dtx3[3])
	}
	if variant != dtx.DTX3 {
		return File{}, fmt.Errorf("the table is DTX%d, and a tune's table is DTX3"+
			" (SPEC.md 3.3.3)", variant)
	}
	// The shape is read off the header before the payload: a DTX3 payload's
	// map is a byte a column, so a table of another C reads as no payload
	// at all (DTX, SPEC.md 2.4).
	shape, err := dtx.ReadHeader(dtx3)
	if err != nil {
		return File{}, err
	}
	if shape.Columns != C || shape.Width != 1 {
		return File{}, fmt.Errorf("the table is %d columns of %d bytes, and a tune's"+
			" table is %d of one (SPEC.md 3.3.3)", shape.Columns, shape.Width, C)
	}
	table, err := dtx.Read(dtx3)
	if err != nil {
		return File{}, err
	}
	var sources []*dtx.Table
	var counted []bool
	for i := 0; i < count; i++ {
		entry := GetLong(file, IndexAt+4*i)
		at := entry &^ Counted
		counted = append(counted, entry&Counted != 0)
		to := len(file)
		if i+1 < count {
			to = GetLong(file, IndexAt+4*(i+1)) &^ Counted
		}
		if at < 0 || to > len(file) || at > to {
			return File{}, fmt.Errorf("source %d is at %d to %d, and the file has"+
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
		sources = append(sources, source)
	}
	return File{Version: version, FrameRate: GetWord(file, FrameRateAt),
		Effects: int(file[EffectsAt]), Dtx3: dtx3, Table: table, Sources: sources,
		Counted: counted}, nil
}
