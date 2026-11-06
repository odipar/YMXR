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
//	22      2      flags: bit 0 the raster monitor, bit 1 the lean tick,
//	               bit 2 the row read through the program counter, bit 3
//	               the deferred start, bit 4 the lookup
//	24      2      where the core's state byte is
//	26      2      where the handlers begin, the reach measured from there
//	28      4      the subtune table's offset, patched here
//	32      4      the workspace's offset, patched here
//	36      4      with the lookup in, the page area's offset, patched
//	               here

// MaxSubtunes is the most the '##' tag's two digits number.
const MaxSubtunes = ymxr.MaxSubtunes

// MaxSeconds is the most seconds a TIME word reaches.
const MaxSeconds = 65535

// The core's descriptor.
var coreMagic = []byte{'Y', 'M', 'X', 'S'}

const (
	coreMagicAt   = 12
	coreVersion   = 1
	coreVersionAt = 16
	coreReadsAt   = 18
	CoreFixedAt   = 20
	CoreFlagsAt   = 22
	coreStateAt   = 24
	coreHandlers  = 26
	coreTableAt   = 28
	coreWorkAt    = 32
	coreLength    = 36
	// CorePagesAt is the page area's offset in a core with the lookup in
	// (BINARIES.md 2.2, 3.1), 0 where the file has none.
	CorePagesAt = 36
)

// coreMonitor is the core's flag bit 0: the player's raster monitor
// assembled in (doc/performance.md).
const coreMonitor = 1

// coreLean is the core's flag bit 1: a tick neither drops the interrupt
// level nor writes an end of interrupt (doc/performance.md).
const coreLean = 2

// corePcrel is the core's flag bit 2: a tick reads its row through a
// displacement from the instruction that reads it (the player's
// YMXR_PCREL=1, doc/performance.md), which reaches 32,767 bytes, so the
// file's tunes are within that of the core (BINARIES.md 5.5).
const corePcrel = 4

// coreDefer is the core's flag bit 3: the deferred start assembled in (the
// player's YMXR_DEFER=1, doc/performance.md), which a tune whose effects
// byte sets bit 4 requires (SPEC.md 3.3).
const coreDefer = 8

// coreMap is core flag bit 4, transformation support (YMXR_MAP=1).
// Effects byte bit 5 requires this core, which reads bound versions 7
// to 10 (SPEC.md 1.10, 3.3).
const coreMap = 16

// pcrelReach is the bytes from the core's first byte plus its field at 26
// a bound tune of a file built on the core above ends within, which a
// displacement reaches.
const pcrelReach = 32767

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

// TimerC is Timer C's bit in a claims byte, the third of A to D.
const TimerC = 1 << 2

// ClockTimerC and ClockVBL are the clock tag's two names: Timer C, and
// the VBL.
const (
	ClockTimerC = "TC"
	ClockVBL    = "!V"
)

// Ticks names the ticks the file's core reads a row with (BINARIES.md
// 2.1). A tool writes Chosen unasked: it puts the core that reads a row
// through the program counter under a file whose tunes end within the
// reach (5.5), and the one that reads an absolute address under a file
// whose tunes end further off. Pcrel reports such a file rather than putting the other
// core under it, and Absolute writes the core that reads an address at
// any length of file.
type Ticks int

// The three.
const (
	Chosen Ticks = iota
	Pcrel
	Absolute
)

// Asked is the clock the caller asks the tag block to name: the one the
// set and the core select, the VBL, or Timer C (tools.md 12.1).
type Asked int

// The three.
const (
	AskedChosen Asked = iota
	AskedVBL
	AskedTimerC
)

// Options is the tag block's text: the title, the composer where there is
// one, and a name a subtune where the caller names them; and the core the
// file uses, which Monitor and Lean select a switch of and Ticks the
// third.
type Options struct {
	Title    string
	Composer string
	Names    []string
	Monitor  bool
	Lean     bool
	Ticks    Ticks
	Asked    Asked
	// Link links the core for the set, its blocks the subtunes need
	// (doc/LINKER.md).
	Link bool
}

// Of builds an SNDH file from tune files in subtune order. Options
// select raster monitoring, lean ticks and source addressing. Any
// subtune with deferred starts enables that support; any subtune using
// a transformation enables lookups.
func Of(tuneFiles [][]byte, options Options) ([]byte, error) {
	return around(nil, tuneFiles, options)
}

// With is the same, around the core named.
func With(core []byte, tuneFiles [][]byte, options Options) ([]byte, error) {
	return around(core, tuneFiles, options)
}

func around(given []byte, tuneFiles [][]byte, options Options) ([]byte, error) {
	n := len(tuneFiles)
	if n == 0 {
		return nil, errors.New("no tune files: an SNDH file has one subtune at least")
	}
	if n > MaxSubtunes {
		return nil, fmt.Errorf("%d tune files: the '##' tag's two digits count at most %d"+
			" subtunes", n, MaxSubtunes)
	}
	if options.Names != nil && len(options.Names) != n {
		return nil, fmt.Errorf("%d names for %d subtunes", len(options.Names), n)
	}
	frames := make([]int, n)
	rates := make([]int, n)
	mixed := false
	claimed := 0
	defers := false
	maps := false
	var read []ymxr.File
	for i, file := range tuneFiles {
		tune, err := ymxr.Read(file)
		if err != nil {
			return nil, fmt.Errorf("subtune %d: %w", i+1, err)
		}
		read = append(read, tune)
		rates[i] = tune.FrameRate
		if rates[i] == 0 {
			return nil, fmt.Errorf("subtune %d plays at 0 Hz: an SNDH file records a rate of 1 Hz or more", i+1)
		}
		mixed = mixed || rates[i] != rates[0]
		if tune.Table.Repeat() >= tune.Table.Rows() {
			frames[i] = tune.Table.Rows()
		}
		claimed |= Claims(tune.Effects)
		defers = defers || tune.Effects&ymxr.Defers != 0
		maps = maps || tune.Effects&ymxr.Maps != 0
	}
	if err := checkVbl(options.Asked, n, rates[0], mixed); err != nil {
		return nil, err
	}
	if n > 1 && (mixed || rates[0] != 50) {
		if options.Monitor {
			return nil, errors.New("-perf requires every subtune in a set to play at 50 Hz")
		}
		if claimed&TimerC != 0 {
			return nil, errors.New("a set with a non-50 Hz subtune needs Timer C" +
				" for playback and a subtune claims it for an effect")
		}
	}
	// The tunes are bound as a set, so those that agree on what an image
	// fixes once share one and the reader's code appears once for them
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
	rate := rates[0]
	var nativeRates []int
	if mixed {
		rate, err = mixedRate(rates)
		if err != nil {
			return nil, err
		}
		nativeRates = rates
	}
	tags, err := tagsOf(options, rate, n, frames, claimed, nativeRates)
	if err != nil {
		return nil, err
	}
	// Which core is under the tunes: the one the caller named, or the
	// one the switches select, which reads a row through the program
	// counter unless the file's tunes end past the reach (BINARIES.md
	// 5.5).
	pcrel := options.Ticks != Absolute
	core := given
	if core != nil && options.Ticks == Chosen {
		// the caller chose by handing a core over: its flags word says
		// which ticks it has
		pcrel = ymxr.GetWord(core, CoreFlagsAt)&corePcrel != 0
	}
	if core == nil {
		core, err = chosen(options, pcrel, defers, maps, read)
		if err != nil {
			return nil, err
		}
		if pcrel && options.Ticks == Chosen && TunesEnd(core, set, tags) > pcrelReach {
			pcrel = false
			core, err = chosen(options, false, defers, maps, read)
			if err != nil {
				return nil, err
			}
		}
	}
	if err := checkCore(core, options.Monitor, options.Lean, pcrel, defers, maps,
		Binds(tuneFiles)); err != nil {
		return nil, err
	}
	if pcrel {
		// A tick of this core reads its row through a displacement from
		// the instruction that reads it, which reaches pcrelReach bytes,
		// so every row of every subtune is within that of the
		// handlers (BINARIES.md 5.5).
		last := TunesEnd(core, set, tags)
		handlers := ymxr.GetWord(core, coreHandlers)
		if last > pcrelReach && handlers == 0 {
			return nil, fmt.Errorf("the tunes end %d bytes past the core's first byte,"+
				" and a tick that reads a row through the program counter reaches %d",
				last, pcrelReach)
		}
		if last > pcrelReach {
			return nil, fmt.Errorf("the tunes end %d bytes past the core's first handler,"+
				" at %d, and a tick that reads a row through the program counter"+
				" reaches %d", last, handlers, pcrelReach)
		}
	}
	if Area(core, set) < 0 {
		handlers := ymxr.GetWord(core, coreHandlers)
		least := 0
		for _, block := range set.Blocks {
			if len(block) > 0 {
				if n := LeastOf(block, Fallback(core)); n > least {
					least = n
				}
			}
		}
		return nil, fmt.Errorf("the page area ends %d bytes past the core's first handler,"+
			" at %d, and a tick's page read reaches %d", even(len(core))+least-handlers,
			handlers, PageReach)
	}
	workspace := ymxr.Align(ymxr.GetWord(core, CoreFixedAt)+state) + WorkRounding
	var clock []byte
	if mixed {
		clock, err = binaries.Read(binaries.Clock)
		if err != nil {
			return nil, err
		}
		ymxr.PutWord(clock, clockRateAt, rate)
	}
	return combine(core, set, tags, workspace, clock)
}

// The page area's bytes for a list of P pages besides its tables: 256
// bytes a page, and the 255 bytes over which each effect's pages begin at
// its offset modulo 256 (BINARIES.md 3.1). PageReach is the reach of a
// page read, whose displacement is a signed word from the instruction
// that reads it (68k/player/lookup_ticks.S, LKEY).
const (
	PageBytes = 256
	PageSpan  = 255
	PageReach = 32767
)

// Area is the page area's bytes (BINARIES.md 3.1, 5.6): for a core with
// the lookup in, the largest area a subtune's list needs within the reach
// of the core's first handler, from the area's offset right after the
// core (AreaOf); -1 where a list's fallback pages do not fit, and 0 for a
// core without the lookup.
func Area(core []byte, set Set) int {
	if len(core) < CorePagesAt+4 || ymxr.GetWord(core, CoreFlagsAt)&coreMap == 0 {
		return 0
	}
	most := 0
	for _, block := range set.Blocks {
		if len(block) > 0 {
			n := AreaOf(block, Reach(core), Fallback(core))
			if n < 0 {
				return -1
			}
			if n > most {
				most = n
			}
		}
	}
	return most
}

// Reach is the bytes past the page area's offset that the core's first
// handler reaches (BINARIES.md 5.6).
func Reach(core []byte) int {
	return PageReach - (even(len(core)) - ymxr.GetWord(core, coreHandlers))
}

// Fallback is the fallback pages of an effect, two a bank of handlers
// (BINARIES.md 5.6).
func Fallback(core []byte) int {
	if ymxr.GetWord(core, CoreFlagsAt)&coreDefer != 0 {
		return 4
	}
	return 2
}

// TunesEnd returns the end of the bound tunes and their sources relative
// to the first handler, at the core's field 26 (BINARIES.md 2.1, 3.1).
// Program-counter displacements are measured from there. Cores without
// lookups have field 26 set to 0, so their reach is measured from the
// core's first byte.
func TunesEnd(core []byte, set Set, tags []byte) int {
	at := even(even(len(core))+max(0, Area(core, set))) + 2 + 4*len(set.Tunes)
	end := at
	for i := range set.Tunes {
		end = at + set.TuneBytes(i)
		at = even(end)
	}
	if set.sources.bytes > 0 {
		end = ymxr.Align(at) + set.sources.bytes
	}
	return end - ymxr.GetWord(core, coreHandlers)
}

// Binds is the version the tune files bind at: 10 where one of them is
// version 10, and 9 otherwise (SPEC.md 3.3.5).
func Binds(tuneFiles [][]byte) int {
	return ymxr.BindsAt(tuneFiles)
}

// CheckCore reads the core's descriptor against what this writes, and its
// flags against the switches requested: the flags word records whether the
// raster monitor is in and whether the ticks are the lean ones, and the
// file the core was read from does not.
func CheckCore(core []byte, monitor, lean bool, binds int) error {
	return checkCore(core, monitor, lean, false, false, false, binds)
}

func checkCore(core []byte, monitor, lean, pcrel, defers, maps bool, binds int) error {
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
	if reads < binds {
		return fmt.Errorf("the core reads bound tunes to version %d, and this binds at %d",
			reads, binds)
	}
	flags := ymxr.GetWord(core, CoreFlagsAt)
	if monitor && flags&coreMonitor == 0 {
		return fmt.Errorf("the core's flags at %d read %d, and the raster monitor asked"+
			" for needs bit 0 set", CoreFlagsAt, flags)
	}
	if lean && flags&coreLean == 0 {
		return fmt.Errorf("the core's flags at %d read %d, and the lean tick asked for"+
			" needs bit 1 set", CoreFlagsAt, flags)
	}
	if pcrel && flags&corePcrel == 0 {
		return fmt.Errorf("the core's flags at %d read %d, and the row read through the"+
			" program counter asked for needs bit 2 set", CoreFlagsAt, flags)
	}
	if defers && flags&coreDefer == 0 {
		return fmt.Errorf("the core's flags at %d read %d, and a subtune that defers a"+
			" start needs bit 3 set", CoreFlagsAt, flags)
	}
	if maps && flags&coreMap == 0 {
		return fmt.Errorf("the core's flags at %d read %d, and a subtune that maps a"+
			" value needs bit 4 set", CoreFlagsAt, flags)
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

// Clock is the clock tag: '!V' and the rate, the VBL, where the claims
// byte has Timer C, since the player's handler then has that timer, or
// where the VBL is asked for; 'TC' and the rate, Timer C, otherwise. A
// host calls play from that clock (BINARIES.md 5.4).
func Clock(claimed, rate int, asked Asked) (string, error) {
	if claimed&TimerC != 0 {
		if asked == AskedTimerC {
			return "", fmt.Errorf("the set claims Timer C and the clock asked for is" +
				" Timer C: the player's handler has that timer")
		}
		return ClockVBL + fmt.Sprintf("%d", rate), nil
	}
	if asked == AskedVBL {
		return ClockVBL + fmt.Sprintf("%d", rate), nil
	}
	return ClockTimerC + fmt.Sprintf("%d", rate), nil
}

// AskedOf is the clock the tag block names: the one asked for, and the
// VBL where the raster monitor is in and the caller leaves the clock to
// the tool, since the monitor paints one frame of calls and reads
// against the raster where the tick comes from the VBL.
func AskedOf(options Options) Asked {
	if options.Asked == AskedChosen && options.Monitor {
		return AskedVBL
	}
	return options.Asked
}

const (
	clockMagic   = 0x594D5843 // YMXC
	clockMagicAt = 12
	clockCoreAt  = 16
	clockRateAt  = 18
)

// Mixed reports whether the file has mixed-rate entries at at.
func Mixed(file []byte, at int) bool {
	return at >= 0 && at <= len(file)-clockRateAt-2 && ymxr.GetLong(file, at+clockMagicAt) == clockMagic
}

// CoreAt is the core's start after the entries at at.
func CoreAt(file []byte, at int) int {
	if Mixed(file, at) {
		return at + ymxr.GetWord(file, at+clockCoreAt)
	}
	return at
}

func checkVbl(asked Asked, subtunes, rate int, mixed bool) error {
	if asked == AskedVBL && (mixed || (subtunes > 1 && rate != 50)) {
		return errors.New("-vbl requires every subtune in a set to play at 50 Hz")
	}
	return nil
}

// mixedRate is the Timer C callback rate shared by the subtunes.
func mixedRate(rates []int) (int, error) {
	fastest := 0
	for _, rate := range rates {
		fastest = max(fastest, rate)
	}
	if fastest > mostTicks {
		return 0, fmt.Errorf("mixed-rate subtunes need rates at or below %d Hz", mostTicks)
	}
	for rate := fastest; rate <= mostTicks; rate++ {
		common := true
		for _, one := range rates {
			common = common && rate%one == 0
		}
		if common && TimerFor(rate).Ticks == rate {
			return rate, nil
		}
	}
	if fastest <= OSClock.Ticks {
		return OSClock.Ticks, nil
	}
	return mostTicks, nil
}

// Tags is the tag block, 'SNDH' through 'HDNS': TITL, COMM where there is
// a composer, CONV, '##' and two digits, the clock tag and the rate, FLAG,
// each text ended by a zero byte, a pad to an even length, FRMS with a
// long a subtune, '!#SN' where the caller names them with a word a
// subtune, the name's offset from the tag's first byte, then the names
// each ended by a zero byte, a pad to an even length, TIME with a word a
// subtune, and HDNS. The '##' count is before FRMS, the names and
// TIME, since a reader sizes each by it. The raster monitor paints one
// frame of calls (performance.md), so a core with it in names the VBL as
// the clock.
func Tags(options Options, rate, n int, frames []int, claimed int) ([]byte, error) {
	return tagsOf(options, rate, n, frames, claimed, nil)
}

func tagsOf(options Options, rate, n int, frames []int, claimed int, rates []int) ([]byte, error) {
	var out bytes.Buffer
	text(&out, "SNDH")
	tag(&out, "TITL", Clean(options.Title))
	if options.Composer != "" {
		tag(&out, "COMM", Clean(options.Composer))
	}
	tag(&out, "CONV", Converter)
	tag(&out, fmt.Sprintf("##%02d", n), "")
	asked := AskedOf(options)
	if rates != nil {
		asked = AskedTimerC
	}
	clock, err := Clock(claimed, rate, asked)
	if err != nil {
		return nil, err
	}
	tag(&out, clock, "")
	tag(&out, "FLAG", Flag(claimed))
	pad(&out)
	text(&out, "FRMS")
	for i, f := range frames {
		calls := int64(f)
		if rates != nil {
			calls = (calls*int64(rate) + int64(rates[i]) - 1) / int64(rates[i])
		}
		if calls > 0xFFFFFFFF {
			return nil, fmt.Errorf("subtune %d exceeds the FRMS callback count", i+1)
		}
		out.Write([]byte{byte(calls >> 24), byte(calls >> 16), byte(calls >> 8), byte(calls)})
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
	text(&out, "TIME")
	for i, f := range frames {
		native := rate
		if rates != nil {
			native = rates[i]
		}
		s := seconds(f, native)
		out.Write([]byte{byte(s >> 8), byte(s)})
	}
	text(&out, "HDNS")
	return out.Bytes(), nil
}

// seconds is the seconds that many frames last at rate frames a second,
// rounded up and at most MaxSeconds: 0 frames, a tune that repeats, is 0
// seconds, and a tune that plays once for under a second is 1.
func seconds(frames, rate int) int {
	s := (int64(frames) + int64(rate) - 1) / int64(rate)
	if s > MaxSeconds {
		return MaxSeconds
	}
	return int(s)
}

// Combine writes entries, tags, patched core, page area, subtune table,
// bound tunes and workspace. Each entry branches to the corresponding core
// entry. Bound tunes start at even addresses.
//
// Shared source tables follow the tunes, then the images. Transformation blocks
// follow the images. Patch each tune's ImageAt and
// BoundTransformationsAt relative to that tune (doc/BINARIES.md 3.1).
func Combine(core []byte, set Set, tags []byte, workspace int) ([]byte, error) {
	return combine(core, set, tags, workspace, nil)
}

func combine(core []byte, set Set, tags []byte, workspace int, clock []byte) ([]byte, error) {
	tunes := set.Tunes
	entries := even(12 + len(tags))
	header := entries + len(clock)
	if entries-2 > 32767 {
		return nil, fmt.Errorf("the tag block is %d bytes, and a bra.w reaches %d",
			len(tags), 32767)
	}
	n := len(tunes)
	// The page area right after the core, within reach of the lookup
	// handlers, the core's last bytes (BINARIES.md 3.1).
	pagesAt := even(len(core))
	area := max(0, Area(core, set))
	tableAt := even(pagesAt + area)
	at := tableAt + 2 + 4*n
	// The bound tunes first, each on an even address (5.1), so that a
	// tune's sources are beside the core: a player whose ticks read a
	// row through a displacement reaches 32,767 bytes
	// (68k/player/switches.S, YMXR_PCREL), and an image between the
	// two would be in the way.
	offsets := make([]int, n)
	for i := 0; i < n; i++ {
		offsets[i] = at
		at = even(at + set.TuneBytes(i))
	}
	poolAt := 0
	if set.sources.bytes > 0 {
		poolAt = ymxr.Align(at)
		at = poolAt + set.sources.bytes
	}
	// Then the images, each on a long: the reader's code appears once a
	// set of tunes that agree on what an image fixes once (DTX abi.md 1),
	// and every bound tune of that set reaches it.
	imageAt := make([]int, len(set.Images))
	for i := range imageAt {
		at = ymxr.Align(at)
		imageAt[i] = at
		at += len(set.Images[i])
	}
	// Frames read the transformation blocks, including each lookup page
	// list. Identical blocks share an offset past the images.
	blockAt := make([]int, n)
	blocks := make(map[string]int)
	for i, block := range set.Blocks {
		if len(block) == 0 {
			continue
		}
		where, found := blocks[string(block)]
		if !found {
			where = ymxr.Align(at)
			blocks[string(block)] = where
			at = where + len(block)
		}
		blockAt[i] = where
	}
	at = even(at)
	workAt := at
	file := make([]byte, header+workAt+workspace)
	for entry := 0; entry < 12; entry += 4 {
		ymxr.PutWord(file, entry, braW)
		ymxr.PutWord(file, entry+2, entries-2)
	}
	copy(file[12:], tags)
	copy(file[entries:], clock)
	copy(file[header:], core)
	ymxr.PutLong(file, header+coreTableAt, tableAt)
	ymxr.PutLong(file, header+coreWorkAt, workAt)
	if area > 0 {
		ymxr.PutLong(file, header+CorePagesAt, pagesAt)
	}
	ymxr.PutWord(file, header+tableAt, n)
	at = header + poolAt
	for _, table := range set.sources.tables {
		copy(file[at:], table)
		at += ymxr.Align(len(table))
	}
	for block, where := range blocks {
		copy(file[header+where:], block)
	}
	for i := range imageAt {
		copy(file[header+imageAt[i]:], set.Images[i])
	}
	for i := 0; i < n; i++ {
		ymxr.PutLong(file, header+tableAt+2+4*i, offsets[i])
		tune := file[header+offsets[i]:]
		copy(tune, tunes[i][:set.TuneBytes(i)])
		if poolAt > 0 {
			index := BoundIndex(ymxr.GetWord(tune, 4))
			for j, offset := range set.sources.offsets[i] {
				entry := index + 4*j
				counted := ymxr.GetLong(tune, entry) & ymxr.Counted
				ymxr.PutLong(tune, entry, (poolAt+offset-offsets[i])|counted)
			}
		}
		if len(imageAt) > 0 {
			// The bound tune reaches its image from its first byte, and
			// the images are after it.
			ymxr.PutLong(file, header+offsets[i]+ImageAt,
				imageAt[set.Image[i]]-offsets[i])
		}
		if blockAt[i] > 0 {
			ymxr.PutLong(file, header+offsets[i]+BoundTransformationsAt,
				blockAt[i]-offsets[i])
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

// chosen is the core the switches select, and with Link that core linked
// for the blocks the set needs (doc/LINKER.md).
func chosen(options Options, pcrel, defers, maps bool, tunes []ymxr.File) ([]byte, error) {
	name := binaries.Named(options.Monitor, options.Lean, pcrel, defers, maps)
	core, err := binaries.Read(name)
	if err != nil || !options.Link || !Links(defers, maps) {
		return core, err
	}
	table, err := CarriedPieces(name)
	if err != nil {
		return nil, err
	}
	return Link(core, table, BlocksOf(tunes))
}
