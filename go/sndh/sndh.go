package sndh

import (
	"bytes"
	"errors"
	"fmt"

	"github.com/odipar/ymxr/go/binaries"
	"github.com/odipar/ymxr/go/ymxr"
)

// An SNDH file from tune files (doc/BINARIES.md 3): the entry triple, the
// tag block, the core with its two offsets patched, the subtune table,
// each tune bound on an even address, and the workspace, two bytes more
// than the state needs, since init rounds its address up to a long. Any
// SNDH host plays it, and the prg package puts a program around it.
//
// The core's descriptor, from the core's first byte:
//
//	offset  bytes  what it is
//	0       12     three bra.w, to init, exit and play
//	12      4      YMXS
//	16      2      the descriptor's version, 1
//	18      2      the bound tune's version the core reads
//	20      2      YMXR_FIXED, the workspace's bytes before the state block
//	22      2      flags: bit 0 the raster monitor, bit 1 the lean tick
//	24      2      where the core's state byte is
//	26      2      zero
//	28      4      the subtune table's offset, patched here
//	32      4      the workspace's offset, patched here

// MaxSubtunes is the most the '##' tag's two digits number.
const MaxSubtunes = ymxr.MaxSubtunes

// The core's descriptor.
var coreMagic = []byte{'Y', 'M', 'X', 'S'}

const (
	coreMagicAt   = 12
	coreVersion   = 1
	coreVersionAt = 16
	coreReadsAt   = 18
	CoreFixedAt   = 20
	coreFlagsAt   = 22
	coreStateAt   = 24
	coreTableAt   = 28
	coreWorkAt    = 32
	coreLength    = 36
)

// coreMonitor is the core's flag bit 0: the player's raster monitor
// assembled in (doc/performance.md).
const coreMonitor = 1

// coreLean is the core's flag bit 1: a tick neither drops the interrupt
// level nor writes an end of interrupt (doc/performance.md).
const coreLean = 2

// braW is the word of a bra.w, before its displacement.
const braW = 0x6000

// WorkRounding is the workspace's bytes past what the state needs: the
// player uses its workspace on a long, an SNDH host loads the file on an
// even address, and init rounds the workspace's address up to a long.
const WorkRounding = 2

// Converter is the CONV tag's text: the player, and the converter that
// writes its tune files.
const Converter = "YMXR (ym-to-ymxr)"

// timerOfEffect is the timer each of the four effects runs, a bit a timer,
// A to D: effects 0 to 3 run Timers A, D, B and C.
var timerOfEffect = [4]int{0, 3, 1, 2}

// Options is the tag block's text: the title, the composer where there is
// one, and a name a subtune where the caller names them; and the core the
// file uses, which monitor and lean select a switch each.
type Options struct {
	Title    string
	Composer string
	Names    []string
	Monitor  bool
	Lean     bool
}

// Of is the file, from the tune files as subtunes 1 up, around the core
// the options' two switches select: the raster monitor in where they ask
// to read the run, the lean tick where the options select it, both where
// they select both, and the plain core where neither.
func Of(tuneFiles [][]byte, options Options) ([]byte, error) {
	core, err := binaries.Read(binaries.Named(options.Monitor, options.Lean))
	if err != nil {
		return nil, err
	}
	return With(core, tuneFiles, options)
}

// With is the same, around the core named.
func With(core []byte, tuneFiles [][]byte, options Options) ([]byte, error) {
	if err := CheckCore(core, options.Monitor, options.Lean); err != nil {
		return nil, err
	}
	n := len(tuneFiles)
	if n == 0 {
		return nil, errors.New("no tune files: an SNDH file has one subtune at least")
	}
	if n > MaxSubtunes {
		return nil, fmt.Errorf("%d tune files: the '##' tag's two digits hold at most %d"+
			" subtunes", n, MaxSubtunes)
	}
	if options.Names != nil && len(options.Names) != n {
		return nil, fmt.Errorf("%d names for %d subtunes", len(options.Names), n)
	}
	frames := make([]int, n)
	rate := 0
	claimed := 0
	for i, file := range tuneFiles {
		tune, err := ymxr.Read(file)
		if err != nil {
			return nil, fmt.Errorf("subtune %d: %w", i+1, err)
		}
		if i == 0 {
			rate = tune.FrameRate
		} else if tune.FrameRate != rate {
			return nil, fmt.Errorf("subtune %d plays at %d Hz and subtune 1 at %d: an"+
				" SNDH file records one rate", i+1, tune.FrameRate, rate)
		}
		if tune.Table.Repeat() >= tune.Table.Rows() {
			frames[i] = tune.Table.Rows()
		}
		claimed |= Claims(tune.Effects)
	}
	// The tunes are bound as a set, so those that agree on what an image
	// fixes once share one and the reader's code stands once for them
	// (DTX abi.md 1, doc/BINARIES.md 2).
	set, err := Bind(tuneFiles)
	if err != nil {
		return nil, err
	}
	state := 0
	for _, one := range set.Tunes {
		if at := ymxr.GetLong(one, StateAt); at > state {
			state = at
		}
	}
	tags, err := Tags(options, rate, n, frames, claimed)
	if err != nil {
		return nil, err
	}
	workspace := ymxr.Align(ymxr.GetWord(core, CoreFixedAt)+state) + WorkRounding
	return Combine(core, set, tags, workspace)
}

// CheckCore reads the core's descriptor against what this writes, and its
// flags against the switches requested: the flags word records whether the
// raster monitor is in and whether the ticks are the lean ones, and the
// file the core was read from does not.
func CheckCore(core []byte, monitor, lean bool) error {
	if len(core) < coreLength ||
		!bytes.Equal(coreMagic, core[coreMagicAt:coreMagicAt+4]) {
		return fmt.Errorf("not an SNDH core: no YMXS at %d", coreMagicAt)
	}
	version := ymxr.GetWord(core, coreVersionAt)
	if version != coreVersion {
		return fmt.Errorf("the core's descriptor is version %d, and this writes %d",
			version, coreVersion)
	}
	reads := ymxr.GetWord(core, coreReadsAt)
	if reads != BoundVersion {
		return fmt.Errorf("the core reads bound tunes of version %d, and this binds at %d",
			reads, BoundVersion)
	}
	flags := ymxr.GetWord(core, coreFlagsAt)
	if monitor && flags&coreMonitor == 0 {
		return fmt.Errorf("the core's flags at %d read %d, and the raster monitor asked"+
			" for needs bit 0 set", coreFlagsAt, flags)
	}
	if lean && flags&coreLean == 0 {
		return fmt.Errorf("the core's flags at %d read %d, and the lean tick asked for"+
			" needs bit 1 set", coreFlagsAt, flags)
	}
	return nil
}

// Claims is the timers a tune's effects claim, a bit a timer, A to D.
func Claims(effects int) int {
	claimed := 0
	for i, timer := range timerOfEffect {
		if effects&(1<<i) != 0 {
			claimed |= 1 << timer
		}
	}
	return claimed
}

// Flag is the FLAG tag's text: '~', a letter for each timer claimed, a to
// d, and y for the YM2149.
func Flag(claimed int) string {
	text := "~"
	for timer := 0; timer < 4; timer++ {
		if claimed&(1<<timer) != 0 {
			text += string(rune('a' + timer))
		}
	}
	return text + "y"
}

// Tags is the tag block, 'SNDH' through 'HDNS': TITL, COMM where there is
// a composer, CONV, '##' and two digits, TC and the rate, FLAG, each text
// ended by a zero byte, a pad to an even length, FRMS with a long a
// subtune, '!#SN' where the caller names them with a word a subtune, the
// name's offset from the tag's first byte, then the names each ended by a
// zero byte, a pad to an even length, and HDNS. The '##' count stands
// before FRMS and the names, since a reader sizes both by it.
func Tags(options Options, rate, n int, frames []int, claimed int) ([]byte, error) {
	var out bytes.Buffer
	text(&out, "SNDH")
	tag(&out, "TITL", Clean(options.Title))
	if options.Composer != "" {
		tag(&out, "COMM", Clean(options.Composer))
	}
	tag(&out, "CONV", Converter)
	tag(&out, fmt.Sprintf("##%02d", n), "")
	tag(&out, fmt.Sprintf("TC%d", rate), "")
	tag(&out, "FLAG", Flag(claimed))
	pad(&out)
	text(&out, "FRMS")
	for _, f := range frames {
		out.Write([]byte{byte(f >> 24), byte(f >> 16), byte(f >> 8), byte(f)})
	}
	if options.Names != nil {
		text(&out, "!#SN")
		at := 4 + 2*n
		for _, name := range options.Names {
			out.Write([]byte{byte(at >> 8), byte(at)})
			at += len(Clean(name)) + 1
		}
		for _, name := range options.Names {
			text(&out, Clean(name))
			out.WriteByte(0)
		}
	}
	pad(&out)
	text(&out, "HDNS")
	return out.Bytes(), nil
}

// Combine is the file: the entry triple, the tag block padded even, the
// core with its offsets patched, the subtune table, the bound tunes each
// on an even address, and that many zero bytes of workspace. Each entry is
// a bra.w to the same entry of the core's triple, so all three
// displacements are the header's bytes less 2.
//
// The images of the set stand behind the subtune table and every bound
// tune's ImageAt is patched to reach the one with its table in it, from
// its first byte.
func Combine(core []byte, set Set, tags []byte, workspace int) ([]byte, error) {
	tunes := set.Tunes
	header := even(12 + len(tags))
	if header-2 > 32767 {
		return nil, fmt.Errorf("the tag block is %d bytes, and a bra.w reaches %d",
			len(tags), 32767)
	}
	n := len(tunes)
	tableAt := even(len(core))
	at := tableAt + 2 + 4*n
	// The images first, each on a long: the reader's code stands once a
	// set of tunes that agree on what an image fixes once (DTX abi.md 1),
	// and every bound tune of that set reaches it.
	imageAt := make([]int, len(set.Images))
	for i := range imageAt {
		at = ymxr.Align(at)
		imageAt[i] = at
		at += len(set.Images[i])
	}
	offsets := make([]int, n)
	for i := 0; i < n; i++ {
		offsets[i] = at
		at = even(at + len(tunes[i]))
	}
	workAt := at
	file := make([]byte, header+workAt+workspace)
	for entry := 0; entry < 12; entry += 4 {
		ymxr.PutWord(file, entry, braW)
		ymxr.PutWord(file, entry+2, header-2)
	}
	copy(file[12:], tags)
	copy(file[header:], core)
	ymxr.PutLong(file, header+coreTableAt, tableAt)
	ymxr.PutLong(file, header+coreWorkAt, workAt)
	ymxr.PutWord(file, header+tableAt, n)
	for i := range imageAt {
		copy(file[header+imageAt[i]:], set.Images[i])
	}
	for i := 0; i < n; i++ {
		ymxr.PutLong(file, header+tableAt+2+4*i, offsets[i])
		copy(file[header+offsets[i]:], tunes[i])
		if len(imageAt) > 0 {
			// The bound tune reaches its image from its first byte, and
			// the images stand before it, so the reach is negative.
			ymxr.PutLong(file, header+offsets[i]+ImageAt,
				imageAt[set.Image[i]]-offsets[i])
		}
	}
	return file, nil
}

func even(at int) int {
	return at + (at & 1)
}

// tag writes one text tag: the name, the text, a zero byte.
func tag(out *bytes.Buffer, name, said string) {
	text(out, name)
	text(out, said)
	out.WriteByte(0)
}

// text writes a text one byte a character, as the tags have them.
func text(out *bytes.Buffer, said string) {
	for _, r := range said {
		out.WriteByte(byte(r))
	}
}

// pad writes a zero byte where the block's length is odd.
func pad(out *bytes.Buffer) {
	if out.Len()&1 != 0 {
		out.WriteByte(0)
	}
}

// Clean is the printable ASCII of a text: a title comes out of a dump's
// header, which accepts any bytes.
func Clean(said string) string {
	out := make([]rune, 0, len(said))
	for _, c := range said {
		if c >= 0x20 && c < 0x7F {
			out = append(out, c)
		}
	}
	return string(out)
}
