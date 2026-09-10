package ymxr

// The sources a tune names, built from its effects (SPEC.md 2.2, 3.1): one
// a distinct kind and value, numbered from 1 as first met. A SID voice is
// two rows, its level and 0; a sync buzzer one row, its shape; a digidrum
// the recording's levels and a closing row at mid-scale. The last row of
// every source has bit 7 set, the marker a tick ends on (SPEC.md 3.2).

// Most is the most sources a tune names: the source column's seven bits.
const Most = 127

// Mark is bit 7 of a source's last row.
const Mark = 0x80

// Park is the level a digidrum's last row leaves its register at:
// mid-scale, so the frame write that sets the register back does not
// click.
const Park = 13

// The kinds a slot sounds, as the schema's effects read them (SPEC.md
// 1.8): a SID voice is a square wave on a volume register, a digidrum a
// recording through one, a sinus SID a shape the reference player runs an
// empty handler for, and a sync buzzer the envelope restarted at the
// timer's rate.
const (
	SID    = 1
	Drum   = 2
	Sinus  = 3
	Buzzer = 4
)

// Source is one source: its rows, and the row it repeats to, R where it
// does not repeat.
type Source struct {
	Kind   int
	Data   int
	Rows   []byte
	Repeat int
}

// Sources is the sources a tune names, in the order its rows first start
// them.
type Sources struct {
	list    []Source
	numbers map[int]int
	drums   [][]byte
}

// Passed is sources passed whole, numbered 1 upward in that order: what a
// tune built rather than converted uses.
func Passed(passed []Source) *Sources {
	return &Sources{list: append([]Source{}, passed...), numbers: map[int]int{}}
}

// Drums is a sources list built on a song's digidrums, as 4-bit levels:
// the high nibble of an 8-bit sample, or the byte as it stands where the
// file has 4-bit values.
func Drums(drums [][]byte, fourBit bool) *Sources {
	out := &Sources{numbers: map[int]int{}, drums: make([][]byte, len(drums))}
	for i, one := range drums {
		out.drums[i] = make([]byte, len(one))
		for j, level := range one {
			if fourBit {
				out.drums[i][j] = level & 15
			} else {
				out.drums[i][j] = level >> 4
			}
		}
	}
	return out
}

// Number is the number of the source a slot names, built on first use, or
// 0 where the slot names none: a dropped kind, a digidrum the file does
// not hold, or one past the ceiling. The three counts are the caller's
// report.
func (s *Sources) Number(kind, given int, sinus, missing, overflow *int) int {
	data := given & 15
	if kind == Drum {
		data = given & 31
	}
	if kind == Sinus {
		*sinus++
		return 0
	}
	if kind == Drum && data >= len(s.drums) {
		*missing++
		return 0
	}
	key := kind<<8 | data
	if known, met := s.numbers[key]; met {
		return known
	}
	if len(s.list) == Most {
		*overflow++
		return 0
	}
	s.list = append(s.list, s.build(kind, data))
	s.numbers[key] = len(s.list)
	return len(s.list)
}

func (s *Sources) build(kind, data int) Source {
	switch kind {
	case SID:
		// The level then the silence. The row that starts the square
		// writes no level, so the voice keeps the value the last row set
		// for a timer's period and the first tick opens the loud half.
		return Source{Kind: kind, Data: data, Rows: []byte{byte(data), byte(Mark)}}
	case Buzzer:
		return Source{Kind: kind, Data: data, Rows: []byte{byte(Mark | data)}}
	default:
		rows := make([]byte, len(s.drums[data])+1)
		copy(rows, s.drums[data])
		rows[len(rows)-1] = byte(Mark | Park)
		return Source{Kind: kind, Data: data, Rows: rows, Repeat: len(rows)}
	}
}

// At is source number, 1 upward.
func (s *Sources) At(number int) Source {
	return s.list[number-1]
}

// Count is how many sources the tune names.
func (s *Sources) Count() int {
	return len(s.list)
}

// All is every source, in order.
func (s *Sources) All() []Source {
	return append([]Source{}, s.list...)
}

// MaxSubtunes is the subtunes an SNDH file numbers: the '##' tag's two
// digits.
const MaxSubtunes = 99
