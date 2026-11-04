package sndh

import (
	"fmt"
	"sort"

	"github.com/odipar/ymxr/go/ymxr"
)

// The forms of a page (SPEC.md 3.4.4): entry k at k; a marked column's,
// entry k at k and at k + 128 with bit 7 set; and setR7's, entry k at
// k + 192 with bits 7 and 6 set.
const (
	PagePlain  = 0
	PageMarked = 1
	PagePorts  = 2
)

// MostPages is the most pages a list counts, its count being a byte.
const MostPages = 255

// PagesTag is the two bytes a list begins with, which mark it after the
// block.
var PagesTag = []byte{'Y', 'P'}

// The first target that maps its first value, setNoiseA, the target of
// three columns that maps its last, setBuzzer (SPEC.md 1.10.1, 2.1), and
// setR7, whose counted source writes the ports' bits (2.1.5).
const (
	pagesNoise  = 22
	pagesBuzzer = 21
	pagesMixer  = 7
)

// pagesEffect is one effect as the rows leave it.
type pagesEffect struct {
	target  int
	mapping int
	running bool
	forms   map[int]bool
}

func (one *pagesEffect) state() string {
	var forms []int
	for form := range one.forms {
		forms = append(forms, form)
	}
	sort.Ints(forms)
	return fmt.Sprint(one.target, one.mapping, one.running, forms)
}

// Pages is the list of the pages a version 10 tune maps through (SPEC.md
// 3.4.4), as the bound tune lists them after its transformation block
// (doc/BINARIES.md 1.2): the tag YP, a count, then for each page its
// effect, its form and its transformation, a byte each; nil below version
// 10. A page is one transformation in one form on one effect, the 256
// bytes a lookup handler reads its key through.
//
// The rows are read in the order a player reads them, through the repeat
// row until each effect's state there is one it has had before. An
// effect's state is its kept target, its kept mapping, whether a start
// runs it, and the forms of the handlers its timer may run: a deferred
// start adds its form to the forms before it, since the tick that
// performs it is outside what the rows record.
func Pages(tune ymxr.File) []byte {
	if tune.Version != ymxr.Version {
		return nil
	}
	pages := make([][]int, 4)
	listed := make([]map[int]bool, 4)
	effects := make([]*pagesEffect, 4)
	for i := range effects {
		listed[i] = map[int]bool{}
		effects[i] = &pagesEffect{forms: map[int]bool{}}
	}
	add := func(i, key int) {
		if !listed[i][key] {
			listed[i][key] = true
			pages[i] = append(pages[i], key)
		}
	}
	table := tune.Table
	columns := make([][]byte, ymxr.Mapped)
	for c := ymxr.Effect; c < ymxr.Mapped; c++ {
		columns[c] = table.Column(c)
	}
	for row := 0; row < table.Rows(); row++ {
		pagesPlay(tune, columns, row, effects, add)
	}
	// the loop, played again until each effect is at the repeat row as a
	// pass before left it
	seen := map[string]bool{}
	for table.Repeat() < table.Rows() {
		state := ""
		for _, one := range effects {
			state += one.state() + ";"
		}
		if seen[state] {
			break
		}
		seen[state] = true
		for row := table.Repeat(); row < table.Rows(); row++ {
			pagesPlay(tune, columns, row, effects, add)
		}
	}
	list := append(append([]byte{}, PagesTag...), 0)
	n := len(PagesTag)
	for i := 0; i < 4; i++ {
		for _, key := range pages[i] {
			if int(list[n]) < MostPages {
				list = append(list, byte(i), byte(key&0xFF), byte(key>>8))
				list[n]++
			}
		}
	}
	return list
}

// pagesPlay reads one row's effect columns, each effect the tune runs
// (SPEC.md 4.3, 1.10.2).
func pagesPlay(tune ymxr.File, columns [][]byte, row int, effects []*pagesEffect,
	add func(i, key int)) {
	for i := 0; i < 4; i++ {
		if tune.Effects&(1<<i) == 0 {
			continue
		}
		one := effects[i]
		t := ymxr.Effect + 4*i
		target := int(columns[t][row])
		source := int(columns[t+1][row])
		control := int(columns[t+2][row])
		mapping := int(columns[ymxr.MappingColumn+i][row])
		if target&0x80 != 0 {
			one.target = target & 0x7F
		}
		if source&0x80 != 0 {
			s := source & 0x7F
			if s == 0 {
				// a stop, its mapping column left unread
				one.running = false
				one.forms = map[int]bool{}
				continue
			}
			if mapping&0x80 != 0 {
				one.mapping = mapping & 0x7F
			}
			form := PageForm(tune, s, one.target)
			if one.mapping != 0 && form >= 0 {
				add(i, one.mapping<<8|form)
			}
			deferred := control&0x80 != 0 && control&ymxr.Deferred != 0
			if !deferred || !one.running {
				one.forms = map[int]bool{}
			}
			if form >= 0 {
				one.forms[form] = true
			}
			one.running = true
		} else if mapping&0x80 != 0 {
			one.mapping = mapping & 0x7F
			if one.running && one.mapping != 0 {
				var forms []int
				for form := range one.forms {
					forms = append(forms, form)
				}
				sort.Ints(forms)
				for _, form := range forms {
					add(i, one.mapping<<8|form)
				}
			}
		}
	}
}

// PageForm is the form source s runs on target through a
// transformation, or -1 for a square or a source of one row, whose
// entries are in the handler (SPEC.md 3.4.4): a counted source plain,
// and the ports' form on setR7; a marked source of one column marked; of
// two, plain on the noise targets and marked on the others; of three,
// marked on setBuzzer and plain on the voices.
func PageForm(tune ymxr.File, s, target int) int {
	if s > len(tune.Sources) {
		return -1
	}
	source := tune.Sources[s-1]
	if tune.Counted[s-1] {
		if target == pagesMixer {
			return PagePorts
		}
		return PagePlain
	}
	switch source.Columns() {
	case 1:
		if source.Repeat() == 0 && (source.Rows() == 1 || source.Rows() == 2) {
			return -1
		}
		return PageMarked
	case 2:
		if target >= pagesNoise {
			return PagePlain
		}
		return PageMarked
	}
	if target == pagesBuzzer {
		return PageMarked
	}
	return PagePlain
}

// PagesOf is the count of the list after a bound transformation block that
// begins at at in bytes, 0 where no list follows it (BINARIES.md 1.1).
func PagesOf(bytes []byte, at int) int {
	end := listAt(bytes, at)
	if !tagged(bytes, end) {
		return 0
	}
	return int(bytes[end+len(PagesTag)])
}

// tagged is whether the list's tag is at at.
func tagged(bytes []byte, at int) bool {
	return at+len(PagesTag) < len(bytes) && bytes[at] == PagesTag[0] &&
		bytes[at+1] == PagesTag[1]
}

// listAt is where the list is after the transformation block at at: past
// its index and its last transformation.
func listAt(bytes []byte, at int) int {
	count := ymxr.GetWord(bytes, at)
	end := at + 2 + 4*count
	for n := 0; n < count; n++ {
		table := at + ymxr.GetLong(bytes, at+2+4*n)
		if last := table + 2 + ymxr.GetWord(bytes, table); last > end {
			end = last
		}
	}
	return end
}

// AreaOf is the page area a bound transformation block needs within reach
// bytes, its pages placed by the rule of BINARIES.md 5.6, fallback pages
// for each effect a page of the list falls back on; 0 where the list
// counts no page, and -1 where even the fallback pages do not fit.
func AreaOf(block []byte, reach, fallback int) int {
	pages := PagesOf(block, 0)
	if pages == 0 {
		return 0
	}
	first := listAt(block, 0) + len(PagesTag) + 1
	pairs := map[int]bool{}
	last := make([]int, 4)
	for j := 0; j < pages; j++ {
		effect := int(block[first+3*j])
		pairs[effect<<8|int(block[first+3*j+1])] = true
		last[effect&3] = j + 1
	}
	tables := len(pairs)*ymxr.GetWord(block, 0) + PageSpan
	most := floorDiv(reach-tables, PageBytes)
	placed, dropped := pages, 0
	for placed+fallback*dropped > most {
		placed--
		if placed < 0 {
			return -1
		}
		dropped = 0
		for _, one := range last {
			if one > placed {
				dropped++
			}
		}
	}
	return tables + PageBytes*(placed+fallback*dropped)
}

// LeastOf is the least page area a block needs: its tables and a fallback
// for each effect of its list (BINARIES.md 5.6).
func LeastOf(block []byte, fallback int) int {
	pages := PagesOf(block, 0)
	first := listAt(block, 0) + len(PagesTag) + 1
	pairs := map[int]bool{}
	effects := map[int]bool{}
	for j := 0; j < pages; j++ {
		effects[int(block[first+3*j])] = true
		pairs[int(block[first+3*j])<<8|int(block[first+3*j+1])] = true
	}
	return len(pairs)*ymxr.GetWord(block, 0) + PageSpan + PageBytes*fallback*len(effects)
}

func floorDiv(a, b int) int {
	q := a / b
	if a%b != 0 && (a < 0) != (b < 0) {
		q--
	}
	return q
}
