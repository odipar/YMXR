package ymxr

import (
	"fmt"
	"math"
	"sort"

	"github.com/odipar/dtx/go/dtx"

	"github.com/odipar/ymxr/go/report"
)

// The rings of a set's tables, chosen for the set (tools.md 4.7). A set's
// subtunes share one workspace, sized for the largest state block among
// them (BINARIES.md 3.1), so the workspace grows with the largest alone: a
// tune whose state block stays within it packs through the ring of the
// fewest table bytes there.
//
// Each tune's table is packed without copies through each ring the writer
// chooses among, as one tune's is (tools.md 4.2). For each state block B one
// of those packings needs, each tune packs through the ring whose table is
// the fewest bytes among those whose state block is B or less, the smaller
// ring of two of the same bytes, and the set keeps the B whose tables and B
// come to the least, the smaller B of two that come to the same. Under
// -copies each tune then packs with copies through its ring where they pay.

// ChooseRings is each tune's ring for the set, in the order of options: an
// option of a tune maps a ring to the table's bytes and the state block its
// reader needs, through that ring. A tune with no option is an error.
func ChooseRings(options []map[int][2]int) ([]int, error) {
	seen := map[int]bool{}
	var budgets []int
	for i, one := range options {
		if len(one) == 0 {
			return nil, fmt.Errorf("tune %d has no ring to choose", i+1)
		}
		for _, cost := range one {
			if !seen[cost[1]] {
				seen[cost[1]] = true
				budgets = append(budgets, cost[1])
			}
		}
	}
	sort.Ints(budgets)
	var best []int
	least := int64(math.MaxInt64)
	for _, budget := range budgets {
		rings := make([]int, len(options))
		var tables int64
		fits := true
		for i := 0; i < len(options) && fits; i++ {
			ring, bytes := -1, math.MaxInt
			// in ring order, so of two of the same bytes the smaller ring
			for _, one := range ascending(options[i]) {
				if cost := options[i][one]; cost[1] <= budget && cost[0] < bytes {
					ring, bytes = one, cost[0]
				}
			}
			fits = ring >= 0
			rings[i] = ring
			tables += int64(bytes)
		}
		if fits && tables+int64(budget) < least {
			least, best = tables+int64(budget), rings
		}
	}
	return best, nil
}

// ascending is the rings an option names, in ascending order.
func ascending(option map[int][2]int) []int {
	rings := make([]int, 0, len(option))
	for one := range option {
		rings = append(rings, one)
	}
	sort.Ints(rings)
	return rings
}

// ForSet is the set's tunes with each table packed through the ring chosen
// for the set among the plain packings each was written with, and with
// every packer through it where there are several. A tune written through a
// ring named has no plain packings to choose among, and is an error.
func ForSet(tunes []Written, packers []dtx.Packer, said *report.Report) ([]Written, error) {
	options := make([]map[int][2]int, len(tunes))
	for i, tune := range tunes {
		options[i] = map[int][2]int{}
		for ring, packed := range tune.Plain {
			options[i][ring] = [2]int{len(packed.Table), packed.State}
		}
	}
	rings, err := ChooseRings(options)
	if err != nil {
		return nil, err
	}
	live := 0
	if len(packers) > 1 {
		for i, tune := range tunes {
			payload, err := payloadOf(tune.Plain[rings[i]].Table)
			if err != nil {
				return nil, err
			}
			live += payload.Live()
		}
	}
	count := &watched{said: said, total: live * len(packers)}
	every := make([]dtx.Packer, len(packers))
	for i, packer := range packers {
		every[i] = counted{inner: packer, count: count}
	}
	out := make([]Written, len(tunes))
	for i, tune := range tunes {
		packed, ok := tune.Plain[rings[i]]
		if !ok {
			return nil, fmt.Errorf("no table through a ring of %d", rings[i])
		}
		table := packed.Table
		if len(packers) > 1 {
			read, err := dtx.ReadDtx3(table)
			if err != nil {
				return nil, err
			}
			if table, err = dtx.WriteDtx3(read, every, tune.Unit, rings[i]); err != nil {
				return nil, err
			}
		}
		tune.File, tune.Table = WithTable(tune.File, table), table
		out[i] = tune
	}
	return out, nil
}

// payloadOf is the payload of a DTX3 table.
func payloadOf(table []byte) (dtx.Payload3, error) {
	header, err := dtx.ReadHeader(table)
	if err != nil {
		return dtx.Payload3{}, err
	}
	return dtx.ReadPayload3(table, header)
}

// OfFiles is the tune files of a set, read back and each table packed
// again: through the rings chosen for the set where ring is Choose, and
// otherwise each through the multiple of the period nearest ring within its
// reach, with every packer. A file that is not a tune file of this version
// is the reader's error (writing.md 7).
func OfFiles(files [][]byte, packers []dtx.Packer, ring int,
	said *report.Report) ([]Written, error) {
	read := make([]File, len(files))
	payloads := make([]dtx.Payload3, len(files))
	packs := 0
	for i, file := range files {
		one, err := Read(file)
		if err != nil {
			return nil, err
		}
		payload, err := payloadOf(one.Dtx3)
		if err != nil {
			return nil, err
		}
		read[i], payloads[i] = one, payload
		if ring == Choose {
			packs += payload.Live() * len(RingsOf(payload.Live()))
		} else {
			packs += payload.Live() * len(packers)
		}
	}
	count := &watched{said: said, total: packs}
	first := counted{inner: packers[0], count: count}
	every := make([]dtx.Packer, len(packers))
	for i, packer := range packers {
		every[i] = counted{inner: packer, count: count}
	}
	tunes := make([]Written, len(files))
	for i, one := range read {
		table := one.Table
		column := make([][]byte, table.Columns())
		for c := range column {
			column[c] = table.Column(c)
		}
		sourceRows, sourceBytes := 0, 0
		for _, source := range one.Sources {
			sourceRows += source.Rows()
			sourceBytes += len(dtx.WriteDtx1(source))
		}
		unit, live := payloads[i].Unit, Live(column)
		plain := map[int]Packed{}
		var packed []byte
		if ring == Choose {
			var err error
			plain, err = Candidates(column, table.Rows(), table.Repeat(), unit, RingsOf(live),
				first, said)
			if err != nil {
				return nil, err
			}
			// the table through the set's ring is put in its place below
			packed = plain[sortedRings(plain)[0]].Table
		} else {
			at := RingOf(ring, live)
			if at != ring {
				said.Note("the ring is " + report.Count(at, "byte", "bytes") +
					": a multiple of the period within the player's reach")
			}
			var err error
			if packed, err = packTable(column, table.Rows(), table.Repeat(), unit, at,
				every); err != nil {
				return nil, err
			}
		}
		tunes[i] = Written{File: WithTable(files[i], packed), Repeat: table.Repeat(),
			Table: packed, Frames: table.Rows(), Unit: unit, Sources: len(one.Sources),
			SourceRows: sourceRows, SourceBytes: sourceBytes, Plain: plain}
	}
	if ring == Choose {
		return ForSet(tunes, packers, said)
	}
	return tunes, nil
}

// SaySet is the set's heading in the report: its tunes, their tables' bytes
// and the largest state block among them (tools.md 4.7).
func SaySet(said *report.Report, set []Written) error {
	tables, state := 0, 0
	for _, tune := range set {
		tables += len(tune.Table)
		one, err := State(tune.Table)
		if err != nil {
			return err
		}
		if one > state {
			state = one
		}
	}
	said.Say(fmt.Sprintf("the set: %s, the tables %s, the state block %s",
		report.Count(len(set), "tune", "tunes"), report.Count(tables, "byte", "bytes"),
		report.Count(state, "byte", "bytes")))
	return nil
}

// Report is the packing report of a tune written, with the heading's words
// for how its ring was chosen (tools.md 4.5).
func Report(said *report.Report, written Written, chose string) error {
	return packed(said, written, chose)
}
