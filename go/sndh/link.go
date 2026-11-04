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

// CoreTableAt is the offset of the core's subtune table offset, which a
// linked core's report reads its length off (BINARIES.md 2.2, 3.1).
const CoreTableAt = coreTableAt

// Kinds are the handler kinds that are blocks: the general handler is in
// each effect's block.
var Kinds = []string{"square", "one", "counted", "two", "three", "envelope"}

// Blocks are the blocks a core's pieces belong to, in the order
// doc/LINKER.md lists them: each handler kind, which init's loop over it
// is, and its instance on each effect; the start of several columns; and
// each effect's frame code and general handler.
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
	return all
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

// Pieces is the table of a core: its pieces, its masks and its fields,
// in the core's order.
type Pieces struct {
	Pieces []Piece
	Masks  []Mask
	Fields []Field
}

var move = regexp.MustCompile(`^([a-z][a-z0-9]*_[0-9]+)([+-][0-9]+)$`)

// ReadPieces is the table in that text, a line a piece and then a line a
// field.
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

// Cuts is whether the build cuts a table from the core of that name: one
// without the deferred start and the lookup.
func Cuts(core string) bool {
	return strings.HasPrefix(core, "YMXR_sndh") && !strings.Contains(core, "-defer") &&
		!strings.Contains(core, "-map")
}

// CarriedPieces is the table carried beside the core of that name.
func CarriedPieces(core string) (Pieces, error) {
	text, err := binaries.Read(strings.TrimSuffix(core, ".bin") + ".pieces")
	if err != nil {
		return Pieces{}, err
	}
	return ReadPieces(string(text))
}

// Links is whether a set of these effects links: one that needs neither
// the deferred start nor the lookup.
func Links(defers, maps bool) bool {
	return !defers && !maps
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
// and the kind's loop of init; and several where a start is of a counted
// source or one of several columns; in the order of Blocks.
func BlocksOf(tunes []ymxr.File) []string {
	needed := map[string]bool{}
	for _, tune := range tunes {
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
// block left out, every field in the bytes kept moved by -c times the
// bytes of each piece left out it refers across, and each mask written
// with the blocks kept (doc/LINKER.md, The link). A value that no longer
// fits its field is an error.
func Link(core []byte, table Pieces, blocks []string) ([]byte, error) {
	kept := map[string]bool{}
	for _, block := range blocks {
		kept[block] = true
	}
	size := map[string]int{}
	left := 0
	for _, p := range table.Pieces {
		if !kept[p.Block()] {
			size[p.Name] = p.To - p.From
			left += p.To - p.From
		}
	}
	moved := append([]byte(nil), core...)
	for _, f := range table.Fields {
		by := 0
		for name, c := range f.Moves {
			by += c * size[name]
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
	linked := make([]byte, 0, len(core)-left)
	from := 0
	for _, p := range table.Pieces {
		if _, out := size[p.Name]; out {
			linked = append(linked, moved[from:p.From]...)
			from = p.To
		}
	}
	return append(linked, moved[from:]...), nil
}
