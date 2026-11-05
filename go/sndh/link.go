package sndh

import (
	"fmt"
	"regexp"
	"sort"
	"strconv"
	"strings"

	"github.com/odipar/dtx/go/dtx"

	"github.com/odipar/ymxr/go/binaries"
	"github.com/odipar/ymxr/go/ymxr"
)

// Kinds are the handler kinds that are blocks: the general handler is in
// each effect's block.
var Kinds = []string{"square", "one", "counted", "two", "three", "envelope"}

// Lookups are the lookup kinds, each a block on each effect: the general,
// the counted, two columns, noise, buzzer, voice, the counted of two
// columns and the counted on noise (68k/player/lookup_kinds.S).
var Lookups = []string{"lk", "lc", "lt", "ln", "lb", "lv", "le", "lf"}

// Blocks are the blocks a core's pieces belong to, in the order
// doc/LINKER.md lists them: each handler kind, which init's loop over it
// is, and its instance on each effect; the start of several columns; each
// effect's frame code and general handler; each lookup kind and its
// instance on each effect; and align, the padding before the second bank.
var Blocks = func() []string {
	var all []string
	for _, kind := range Kinds {
		all = append(all, kind)
		for i := 0; i < 4; i++ {
			all = append(all, fmt.Sprintf("%s%d", kind, i))
		}
	}
	all = append(all, "several")
	for i := 0; i < 4; i++ {
		all = append(all, fmt.Sprintf("effect%d", i))
	}
	for _, kind := range Lookups {
		all = append(all, kind)
		for i := 0; i < 4; i++ {
			all = append(all, fmt.Sprintf("%s%d", kind, i))
		}
	}
	return append(all, "align")
}()

// Piece is a run of bytes of a core a player linked for one song leaves
// out: its name, <block>_<n>, its first byte and the byte past its last.
type Piece struct {
	Name     string
	From, To int
}

// Block is the block the piece is of.
func (p Piece) Block() string {
	return p.Name[:strings.LastIndex(p.Name, "_")]
}

// Field is a value of the core that moves where a piece is left out: its
// offset, its width in bytes, and for each piece whose end it refers
// across, the coefficient c (doc/LINKER.md, The library).
type Field struct {
	At, Width int
	Moves     map[string]int
}

// Mask is a word of the core, at the label ymxr_k_<prefix>, that a link
// writes with bit i set where it keeps block <prefix>i.
type Mask struct {
	Prefix string
	At     int
}

// Twin is a piece of the second bank, Copy, and the piece of the first it
// copies, First: a link leaves both out or keeps both, at one length, and
// a field's coefficient for the two is First's.
type Twin struct {
	Copy, First string
}

// Align is a piece of padding, Gap, whose length a link sets: the byte
// Base past its end, linked, is the byte Add past Anchor, linked, modulo
// 256. A lookup instance's page read is Base past the gap before it, and
// its effect's reference is Add past ymxr_ref (68k/player/lookup_ref.S),
// the word at Word, which a link writes with the reference it picks; -1
// where Add is a constant.
type Align struct {
	Gap                     string
	Base, Anchor, Add, Word int
}

// Pieces is the table of a core: its pieces, its masks, its twins, its
// aligns and its fields, in the core's order.
type Pieces struct {
	Pieces []Piece
	Masks  []Mask
	Twins  []Twin
	Aligns []Align
	Fields []Field
}

var move = regexp.MustCompile(`^([a-z][a-z0-9]*_[0-9]+)([+-][0-9]+)$`)

// ReadPieces is the table in that text: a line a piece, a mask, a twin,
// an align and a field.
func ReadPieces(text string) (Pieces, error) {
	var table Pieces
	for _, line := range strings.Split(text, "\n") {
		word := strings.Split(line, " ")
		switch {
		case word[0] == "piece" && len(word) == 4:
			from, err1 := strconv.Atoi(word[2])
			to, err2 := strconv.Atoi(word[3])
			if err1 != nil || err2 != nil {
				return Pieces{}, fmt.Errorf("a line of a table reads %s", line)
			}
			table.Pieces = append(table.Pieces, Piece{word[1], from, to})
		case word[0] == "mask" && len(word) == 3:
			at, err := strconv.Atoi(word[1])
			if err != nil {
				return Pieces{}, fmt.Errorf("a line of a table reads %s", line)
			}
			table.Masks = append(table.Masks, Mask{word[2], at})
		case word[0] == "twin" && len(word) == 3:
			table.Twins = append(table.Twins, Twin{word[1], word[2]})
		case word[0] == "align" && (len(word) == 5 || len(word) == 6):
			base, err1 := strconv.Atoi(word[2])
			anchor, err2 := strconv.Atoi(word[3])
			add, err3 := strconv.Atoi(word[4])
			at, err4 := -1, error(nil)
			if len(word) == 6 {
				at, err4 = strconv.Atoi(word[5])
			}
			if err1 != nil || err2 != nil || err3 != nil || err4 != nil {
				return Pieces{}, fmt.Errorf("a line of a table reads %s", line)
			}
			table.Aligns = append(table.Aligns, Align{word[1], base, anchor, add, at})
		case word[0] == "field" && len(word) >= 4:
			at, err1 := strconv.Atoi(word[1])
			width, err2 := strconv.Atoi(word[2])
			if err1 != nil || err2 != nil {
				return Pieces{}, fmt.Errorf("a line of a table reads %s", line)
			}
			moves := map[string]int{}
			for _, one := range word[3:] {
				m := move.FindStringSubmatch(one)
				if m == nil {
					return Pieces{}, fmt.Errorf("a field's move reads %s", one)
				}
				c, _ := strconv.Atoi(m[2])
				moves[m[1]] = c
			}
			table.Fields = append(table.Fields, Field{at, width, moves})
		case line != "":
			return Pieces{}, fmt.Errorf("a line of a table reads %s", line)
		}
	}
	return table, nil
}

// Text is the table as the build writes it.
func (t Pieces) Text() string {
	var out strings.Builder
	for _, p := range t.Pieces {
		fmt.Fprintf(&out, "piece %s %d %d\n", p.Name, p.From, p.To)
	}
	for _, m := range t.Masks {
		fmt.Fprintf(&out, "mask %d %s\n", m.At, m.Prefix)
	}
	for _, w := range t.Twins {
		fmt.Fprintf(&out, "twin %s %s\n", w.Copy, w.First)
	}
	for _, a := range t.Aligns {
		fmt.Fprintf(&out, "align %s %d %d %d", a.Gap, a.Base, a.Anchor, a.Add)
		if a.Word >= 0 {
			fmt.Fprintf(&out, " %d", a.Word)
		}
		out.WriteString("\n")
	}
	for _, f := range t.Fields {
		fmt.Fprintf(&out, "field %d %d", f.At, f.Width)
		names := make([]string, 0, len(f.Moves))
		for name := range f.Moves {
			names = append(names, name)
		}
		sort.Strings(names)
		for _, name := range names {
			fmt.Fprintf(&out, " %s%+d", name, f.Moves[name])
		}
		out.WriteString("\n")
	}
	return out.String()
}

// Cuts is whether the build cuts a table from the core of that name: every
// SNDH core.
func Cuts(core string) bool {
	return strings.HasPrefix(core, "YMXR_sndh")
}

// CarriedPieces is the table carried beside the core of that name.
func CarriedPieces(core string) (Pieces, error) {
	text, err := binaries.Read(strings.TrimSuffix(core, ".bin") + ".pieces")
	if err != nil {
		return Pieces{}, err
	}
	return ReadPieces(string(text))
}

// Links is whether a set of these effects links: every set does, on the
// core its switches and its effects select.
func Links(defers, maps bool) bool {
	return true
}

// Kind is the block of a source's handler kind, or general for the
// general handler, which every player has (68k/player/init.S resolves
// the same shapes).
func Kind(source *dtx.Table, counted bool) string {
	switch {
	case counted && source.Columns() == 1:
		return "counted"
	case counted:
		return "envelope"
	case source.Columns() == 2:
		return "two"
	case source.Columns() == 3:
		return "three"
	case source.Repeat() == 0 && source.Rows() == 2:
		return "square"
	case source.Repeat() == 0 && source.Rows() == 1:
		return "one"
	}
	return "general"
}

// BlocksOf is the blocks a set of tunes needs: the block of each effect
// a tune runs, bits 0 to 3 of its effects byte; for each source an
// effect's rows start, the instance of its handler kind on that effect
// and the kind's loop of init; several where a start is of a counted
// source or one of several columns; the lookup instance of each kind an
// effect maps a source through, and the kind's loop of init; and align,
// the padding before the second bank; in the order of Blocks.
func BlocksOf(tunes []ymxr.File) []string {
	needed := map[string]bool{"align": true}
	for _, tune := range tunes {
		for _, lookup := range LookupsOf(tune) {
			needed[lookup] = true
			needed[lookup[:2]] = true
		}
		for i := 0; i < 4; i++ {
			if tune.Effects&(1<<i) == 0 {
				continue
			}
			needed[fmt.Sprintf("effect%d", i)] = true
			for _, s := range Started(tune, i) {
				source := tune.Sources[s-1]
				kind := Kind(source, tune.Counted[s-1])
				if kind != "general" {
					needed[kind] = true
					needed[fmt.Sprintf("%s%d", kind, i)] = true
				}
				if tune.Counted[s-1] || source.Columns() > 1 {
					needed["several"] = true
				}
			}
		}
	}
	var ordered []string
	for _, block := range Blocks {
		if needed[block] {
			ordered = append(ordered, block)
		}
	}
	return ordered
}

// LookupsOf is the lookup instances a tune runs, as blocks such as lt1:
// for each start of a source through a transformation, the kept mapping
// not 0, and each remap of a running source to a transformation, the
// lookup kind of the source on the target it started on. A square and a
// source of one row keep their entries in the identity handler. The rows
// are read as a player reads them, through the repeat row until each
// effect's state there is one it has had before, as the pages are read;
// a deferred start on a running timer adds its source to the ones a
// remap reaches.
func LookupsOf(tune ymxr.File) []string {
	var out []string
	if tune.Version != ymxr.Version {
		return out
	}
	seen := map[string]bool{}
	add := func(block string) {
		if !seen[block] {
			seen[block] = true
			out = append(out, block)
		}
	}
	var target, mapping [4]int
	running := [4]map[int]bool{{}, {}, {}, {}}
	table := tune.Table
	for row := 0; row < table.Rows(); row++ {
		lookupRow(tune, row, &target, &mapping, &running, add)
	}
	states := map[string]bool{}
	for table.Repeat() < table.Rows() {
		var state strings.Builder
		for i := 0; i < 4; i++ {
			fmt.Fprintf(&state, "%d %d -1", target[i], mapping[i])
			for _, one := range sortedKeys(running[i]) {
				fmt.Fprintf(&state, " %d", one)
			}
			state.WriteString(";")
		}
		if states[state.String()] {
			break
		}
		states[state.String()] = true
		for row := table.Repeat(); row < table.Rows(); row++ {
			lookupRow(tune, row, &target, &mapping, &running, add)
		}
	}
	return out
}

func sortedKeys(set map[int]bool) []int {
	keys := make([]int, 0, len(set))
	for k := range set {
		keys = append(keys, k)
	}
	sort.Ints(keys)
	return keys
}

// lookupRow is one row's effect columns for LookupsOf: a running source is
// kept with its target, its number times 64 plus the target.
func lookupRow(tune ymxr.File, row int, target, mapping *[4]int, running *[4]map[int]bool,
	add func(string)) {
	for i := 0; i < 4; i++ {
		if tune.Effects&(1<<i) == 0 {
			continue
		}
		t := ymxr.Effect + 4*i
		tg := int(tune.Table.Column(t)[row])
		src := int(tune.Table.Column(t + 1)[row])
		control := int(tune.Table.Column(t + 2)[row])
		m := int(tune.Table.Column(ymxr.MappingColumn + i)[row])
		if tg&0x80 != 0 {
			target[i] = tg & 0x7F
		}
		if src&0x80 != 0 {
			s := src & 0x7F
			if s == 0 {
				running[i] = map[int]bool{}
				continue
			}
			if m&0x80 != 0 {
				mapping[i] = m & 0x7F
			}
			if kind := Lookup(tune, s, target[i]); mapping[i] != 0 && kind != "" {
				add(fmt.Sprintf("%s%d", kind, i))
			}
			deferred := control&0x80 != 0 && control&ymxr.Deferred != 0
			if !deferred || len(running[i]) == 0 {
				running[i] = map[int]bool{}
			}
			running[i][s*64+target[i]] = true
		} else if m&0x80 != 0 {
			mapping[i] = m & 0x7F
			if mapping[i] != 0 {
				for _, one := range sortedKeys(running[i]) {
					if kind := Lookup(tune, one/64, one%64); kind != "" {
						add(fmt.Sprintf("%s%d", kind, i))
					}
				}
			}
		}
	}
}

// The first target that maps its first value, setNoiseA, and the target
// of three columns that maps its last, setBuzzer (SPEC.md 1.10.1).
const (
	noise  = 22
	buzzer = 21
)

// Lookup is the lookup kind of source s on that target, or "" for a square
// or a source of one row (68k/player/lookup_start.S, .kind).
func Lookup(tune ymxr.File, s, target int) string {
	if s < 1 || s > len(tune.Sources) {
		return ""
	}
	switch Kind(tune.Sources[s-1], tune.Counted[s-1]) {
	case "square", "one":
		return ""
	case "general":
		return "lk"
	case "counted":
		return "lc"
	case "two":
		if target >= noise {
			return "ln"
		}
		return "lt"
	case "three":
		if target == buzzer {
			return "lb"
		}
		return "lv"
	}
	if target >= noise {
		return "lf"
	}
	return "le"
}

// gaps writes into lost the bytes each gap the link keeps loses: a gap's
// length puts the byte its align names on the offset modulo 256 of its
// anchor and add, the add of an instance's gap its effect's reference.
// Where pick, the link picks an effect's reference on its first gap it
// keeps: the reference that gap is 0 bytes for, or 2 bytes on from it
// where another effect's reference is on that offset.
func gaps(table Pieces, byName map[string]Piece, lost map[string]int, references map[int]int,
	pick bool) {
	linked := func(at int) int {
		by := 0
		for _, p := range table.Pieces {
			if p.To <= at {
				by += lost[p.Name]
			}
		}
		return at - by
	}
	for _, a := range table.Aligns {
		gap, ok := byName[a.Gap]
		if _, out := lost[a.Gap]; !ok || out {
			continue
		}
		from, anchor, add := linked(gap.From), linked(a.Anchor), a.Add
		if a.Word >= 0 {
			if _, chosen := references[a.Word]; pick && !chosen {
				references[a.Word] = free(references, (from+a.Base-anchor)&255)
			}
			add = references[a.Word]
		}
		length := (anchor + add - from - a.Base) & 255
		lost[a.Gap] = (gap.To - gap.From) - length
	}
}

// free is the first offset modulo 256 from at on, 2 bytes a step, that no
// reference picked is on.
func free(references map[int]int, at int) int {
	for {
		taken := false
		for _, n := range references {
			taken = taken || n == at
		}
		if !taken {
			return at
		}
		at = (at + 2) & 255
	}
}

func copyLost(lost map[string]int) map[string]int {
	out := make(map[string]int, len(lost))
	for k, v := range lost {
		out[k] = v
	}
	return out
}

func sum(lost map[string]int) int {
	total := 0
	for _, v := range lost {
		total += v
	}
	return total
}

// Started is the sources effect i's rows start: a source column with its
// bit 7 set and a number from 1 up (SPEC.md 1.8.3), a number of a source
// the tune has, in order.
func Started(tune ymxr.File, i int) []int {
	seen := map[int]bool{}
	for _, b := range tune.Table.Column(ymxr.Effect + 4*i + 1) {
		s := int(b & 0x7F)
		if b&0x80 != 0 && s > 0 && s <= len(tune.Sources) {
			seen[s] = true
		}
	}
	var out []int
	for s := 1; s <= len(tune.Sources); s++ {
		if seen[s] {
			out = append(out, s)
		}
	}
	return out
}

// LeftOut is the blocks the table has that the set named leaves out, in
// the order of Blocks.
func LeftOut(table Pieces, blocks []string) []string {
	kept := map[string]bool{}
	for _, block := range blocks {
		kept[block] = true
	}
	has := map[string]bool{}
	for _, p := range table.Pieces {
		has[p.Block()] = true
	}
	var out []string
	for _, block := range Blocks {
		if has[block] && !kept[block] {
			out = append(out, block)
		}
	}
	return out
}

// Link is the core linked for the blocks named: every piece of another
// block left out, each gap kept at the length its align sets, every field
// in the bytes kept moved by -c times the bytes each piece it refers
// across loses, and each mask written with the blocks kept
// (doc/LINKER.md, The link). A value that no longer fits its field is an
// error.
func Link(core []byte, table Pieces, blocks []string) ([]byte, error) {
	kept := map[string]bool{}
	for _, block := range blocks {
		kept[block] = true
	}
	// the bytes each piece loses: all of a piece left out, and a kept
	// gap's length less the length the alignment sets
	lost := map[string]int{}
	byName := map[string]Piece{}
	for _, p := range table.Pieces {
		byName[p.Name] = p
		if !kept[p.Block()] {
			lost[p.Name] = p.To - p.From
		}
	}
	// the gaps on the references as carried, and on the references
	// picked; the link keeps the shorter, and the carried on a tie
	carried := map[int]int{}
	var words []int
	for _, a := range table.Aligns {
		if _, seen := carried[a.Word]; a.Word >= 0 && !seen {
			carried[a.Word] = int(int16(ymxr.GetWord(core, a.Word)))
			words = append(words, a.Word)
		}
	}
	asCarried := copyLost(lost)
	gaps(table, byName, asCarried, carried, false)
	references := map[int]int{}
	picked := copyLost(lost)
	gaps(table, byName, picked, references, true)
	for _, at := range words {
		if _, chosen := references[at]; !chosen {
			references[at] = free(references, carried[at])
		}
	}
	if sum(picked) <= sum(asCarried) {
		references = carried
		lost = asCarried
	} else {
		lost = picked
	}
	for _, w := range table.Twins {
		if lost[w.Copy] != lost[w.First] {
			return nil, fmt.Errorf("piece %s loses %d bytes and %s, which it copies, %d",
				w.Copy, lost[w.Copy], w.First, lost[w.First])
		}
	}
	moved := append([]byte(nil), core...)
	for _, f := range table.Fields {
		by := 0
		for name, c := range f.Moves {
			by += c * lost[name]
		}
		if by == 0 {
			continue
		}
		if f.Width == 1 {
			value := int(int8(core[f.At])) - by
			// a short branch of 0 is a word branch on the 68000
			if value < -128 || value > 127 || value == 0 {
				return nil, fmt.Errorf("the byte field at %d is %d linked", f.At, value)
			}
			moved[f.At] = byte(value)
		} else {
			value := int(int16(ymxr.GetWord(core, f.At))) - by
			if value < -32768 || value > 32767 {
				return nil, fmt.Errorf("the word field at %d is %d linked", f.At, value)
			}
			ymxr.PutWord(moved, f.At, value)
		}
	}
	for _, m := range table.Masks {
		bits := 0
		for i := 0; i < 16; i++ {
			if kept[fmt.Sprintf("%s%d", m.Prefix, i)] {
				bits |= 1 << i
			}
		}
		ymxr.PutWord(moved, m.At, bits)
	}
	for _, at := range words {
		ymxr.PutWord(moved, at, references[at])
	}
	out := make([]byte, 0, len(core))
	from := 0
	for _, p := range table.Pieces {
		loses := lost[p.Name]
		if loses == 0 {
			continue
		}
		out = append(out, moved[from:p.From]...)
		// a gap kept at its new length is zeros, as the assembler pads
		out = append(out, make([]byte, (p.To-p.From)-loses)...)
		from = p.To
	}
	return append(out, moved[from:]...), nil
}
