package sndh

import (
	"bytes"
	"fmt"
	"strings"

	"github.com/odipar/ymxr/go/binaries"
	"github.com/odipar/ymxr/go/ymxr"
)

// A TOS program around an SNDH file (doc/BINARIES.md 4): the PRG header,
// the stub with its descriptor patched, the SNDH file, and the relocation
// table, one zero long.
//
// The stub's descriptor, from the stub's first byte:
//
//	offset  bytes  what it is
//	0       4      bra.w to the program
//	4       4      YMXT
//	8       2      the descriptor's version, 1
//	10      2      the subtunes, patched here from the '##' tag
//	12      2      flags, patched here
//	14      2      the rate, rows a second, patched here from the clock tag
//	16      4      the rows to play, patched here; 0 plays on until a key stops it
//	20      4      the core's offset from the SNDH file's first byte, patched here

var stubMagic = []byte{'Y', 'M', 'X', 'T'}

const (
	stubMagicAt     = 4
	stubVersion     = 2
	stubVersionAt   = 8
	stubSubtunesAt  = 10
	StubFlagsAt     = 12
	stubRateAt      = 14
	stubRowsAt      = 16
	stubCoreAt      = 20
	stubPrescalerAt = 24
	stubCountAt     = 26
	stubTicksAt     = 28
	stubLength      = 30
)

// Timer is the timer the stub arms for a rate (BINARIES.md 4.10): the
// prescaler, TCDCR's nibble; the count, 1 to 255 or 0 for 256; and the
// ticks a second the two make, which a row is counted against.
type Timer struct {
	Prescaler int
	Count     int
	Ticks     int
}

// MfpClock is the MFP's clock, and divisors are the seven the nibbles
// select (SPEC.md 1.9.2).
const MfpClock = 2457600

var divisors = [8]int{0, 4, 10, 16, 50, 64, 100, 200}

// mostTicks is the ticks a second the timer is armed at, at most: twice
// the operating system's clock.
const mostTicks = 400

// OSClock is the operating system's clock, which the stub arms where the
// MFP counts no multiple of the rate: 2,457,600 / 64 / 192.
var OSClock = Timer{Prescaler: 5, Count: 192, Ticks: 200}

// TimerFor is the timer for a rate: the lowest multiple of the rate the
// MFP counts exactly, so a row lands every few ticks and the count
// returns to zero, and the operating system's clock where it counts
// none. A tune at
// 50 Hz is 150 ticks a second and a row every third, one at 60 Hz is 240
// and a row every fourth.
func TimerFor(rate int) Timer {
	for k := 1; rate > 0 && k*rate <= mostTicks; k++ {
		ticks := k * rate
		if MfpClock%ticks != 0 {
			continue
		}
		of := MfpClock / ticks
		for nibble := 1; nibble <= 7; nibble++ {
			count := of / divisors[nibble]
			if count*divisors[nibble] == of && count >= 1 && count <= 256 {
				return Timer{Prescaler: nibble, Count: count, Ticks: ticks}
			}
		}
	}
	return OSClock
}

// FlagVBL is flag bit 1: play from the VBL. Set where the clock tag names
// the VBL, where the FLAG letters claim Timer C, since the stub then has
// no timer to play from, or where the VBL is asked for; a file that
// names the VBL itself is at 50 Hz, or there is no program. Clear, the
// stub plays from Timer C.
const FlagVBL = 2

// The PRG header's bytes, and its magic.
const (
	Header   = 28
	PrgMagic = 0x601A
)

// tagsAt is where the tag block begins in an SNDH file, past the entry
// triple: its SNDH, then the tags.
const tagsAt = 12

// Tagged is the tag block read out: the subtunes, the clock tag and its
// rate, the FLAG letters and where the block ends.
type Tagged struct {
	Subtunes int
	Clock    string
	Rate     int
	Flag     string
	End      int
}

// Program is the program around an SNDH file, playing that many rows, or
// playing on where rows is 0, from the clock the file names. A clock
// asked for stands over that one, at the file's rate.
func Program(sndh []byte, rows int64, asked Asked) ([]byte, error) {
	stub, err := binaries.Read(binaries.Stub)
	if err != nil {
		return nil, err
	}
	return ProgramWith(stub, sndh, rows, asked)
}

// ProgramWith is the same, from the stub named.
func ProgramWith(stub, sndh []byte, rows int64, asked Asked) ([]byte, error) {
	if err := CheckStub(stub); err != nil {
		return nil, err
	}
	if rows < 0 || rows > 0xFFFFFFFF {
		return nil, fmt.Errorf("rows %d does not fit a long", rows)
	}
	tags, err := ReadTags(sndh)
	if err != nil {
		return nil, err
	}
	// The file leaves the stub the VBL where its clock tag names the VBL
	// and where its set claims Timer C; a clock asked for stands over
	// either (BINARIES.md 4.3).
	claimed := strings.ContainsRune(tags.Flag, 'c')
	named := tags.Clock == ClockVBL || claimed
	if claimed && asked == AskedTimerC {
		return nil, fmt.Errorf("the set claims Timer C and the clock asked for is Timer" +
			" C: the player's handler has that timer")
	}
	vbl := asked == AskedVBL || (asked == AskedChosen && named)
	if named && asked == AskedChosen && tags.Rate != 50 {
		return nil, fmt.Errorf("the file plays from the VBL at %d Hz: the stub's VBL is"+
			" a 50 Hz clock, so this set needs a separate host or the VBL asked for",
			tags.Rate)
	}
	core, err := Core(sndh, tags.End+4)
	if err != nil {
		return nil, err
	}
	prg := make([]byte, Header+len(stub)+len(sndh)+4)
	ymxr.PutWord(prg, 0, PrgMagic)
	ymxr.PutLong(prg, 2, len(stub)+len(sndh))
	copy(prg[Header:], stub)
	ymxr.PutWord(prg, Header+stubSubtunesAt, tags.Subtunes)
	flags := 0
	if vbl {
		flags |= FlagVBL
	}
	ymxr.PutWord(prg, Header+StubFlagsAt, flags)
	ymxr.PutWord(prg, Header+stubRateAt, tags.Rate)
	ymxr.PutLong(prg, Header+stubRowsAt, int(rows))
	ymxr.PutLong(prg, Header+stubCoreAt, core)
	timer := TimerFor(tags.Rate)
	ymxr.PutWord(prg, Header+stubPrescalerAt, timer.Prescaler)
	ymxr.PutWord(prg, Header+stubCountAt, timer.Count&0xFF)
	ymxr.PutWord(prg, Header+stubTicksAt, timer.Ticks)
	copy(prg[Header+len(stub):], sndh)
	return prg, nil
}

// CheckStub reads the stub's descriptor against what this patches.
func CheckStub(stub []byte) error {
	if len(stub) < stubLength ||
		!bytes.Equal(stubMagic, stub[stubMagicAt:stubMagicAt+4]) {
		return fmt.Errorf("not a program stub: no YMXT at %d", stubMagicAt)
	}
	version := ymxr.GetWord(stub, stubVersionAt)
	if version != stubVersion {
		return fmt.Errorf("the stub's descriptor is version %d, and this writes %d",
			version, stubVersion)
	}
	if len(stub)&1 != 0 {
		return fmt.Errorf("the stub is %d bytes, odd: the SNDH file after it would load"+
			" on an odd address", len(stub))
	}
	return nil
}

// ReadTags walks the tag block from its first tag to HDNS. A zero byte
// where a tag name would begin is a pad, one byte. '##' is four bytes, its
// two digits the subtunes; the clock tag, 'TC' or '!V', and each text tag,
// TITL, COMM, CONV and FLAG, run to their zero byte and one past; FRMS is
// 4 + 4 bytes a subtune, and '!#SN' 4 + 2 bytes a subtune, then a name a
// subtune, each to its zero byte and one past. The subtunes, the rate and
// the FLAG letters come from those tags alone, so a title or a composer
// that reads like a tag patches no field.
func ReadTags(sndh []byte) (Tagged, error) {
	if len(sndh) < tagsAt+4 || string(sndh[tagsAt:tagsAt+4]) != "SNDH" {
		return Tagged{}, fmt.Errorf("not an SNDH file: no SNDH at %d", tagsAt)
	}
	subtunes := -1
	clock := ""
	rate := -1
	flag := ""
	at := tagsAt + 4
	for {
		if at < len(sndh) && sndh[at] == 0 {
			at++
			continue
		}
		if at+4 > len(sndh) {
			return Tagged{}, noEnd()
		}
		name := string(sndh[at : at+4])
		if name == "HDNS" {
			break
		}
		switch {
		case strings.HasPrefix(name, "##"):
			if !digit(sndh[at+2]) || !digit(sndh[at+3]) {
				return Tagged{}, fmt.Errorf("the SNDH file's tags have no '##' subtune" +
					" count")
			}
			subtunes = int(sndh[at+2]-'0')*10 + int(sndh[at+3]-'0')
			at += 4
		case strings.HasPrefix(name, ClockTimerC) || strings.HasPrefix(name, ClockVBL):
			clock = name[:2]
			to, err := zero(sndh, at+2)
			if err != nil {
				return Tagged{}, err
			}
			rate = 0
			for i := at + 2; i < to && digit(sndh[i]); i++ {
				rate = rate*10 + int(sndh[i]-'0')
			}
			if rate == 0 {
				return Tagged{}, fmt.Errorf("the SNDH file's tags have no TC or" +
					" !V rate")
			}
			at = to + 1
		case name == "FRMS":
			n, err := sized(subtunes, name, at)
			if err != nil {
				return Tagged{}, err
			}
			at += 4 + 4*n
		case name == "!#SN":
			names, err := sized(subtunes, name, at)
			if err != nil {
				return Tagged{}, err
			}
			at += 4 + 2*names
			for i := 0; i < names; i++ {
				to, err := zero(sndh, at)
				if err != nil {
					return Tagged{}, err
				}
				at = to + 1
			}
		case name == "TITL" || name == "COMM" || name == "CONV" || name == "FLAG":
			to, err := zero(sndh, at+4)
			if err != nil {
				return Tagged{}, err
			}
			if name == "FLAG" {
				said := string(sndh[at+4 : to])
				flag = said[strings.Index(said, "~")+1:]
			}
			at = to + 1
		default:
			return Tagged{}, fmt.Errorf("the SNDH file's tag %s at %d is not one this"+
				" reads", name, at)
		}
	}
	if subtunes < 0 {
		return Tagged{}, fmt.Errorf("the SNDH file's tags have no '##' subtune count")
	}
	if rate < 0 {
		return Tagged{}, fmt.Errorf("the SNDH file's tags have no TC or !V rate")
	}
	return Tagged{Subtunes: subtunes, Clock: clock, Rate: rate, Flag: flag,
		End: at}, nil
}

// zero is where the next zero byte from there stands.
func zero(sndh []byte, from int) (int, error) {
	for at := from; at < len(sndh); at++ {
		if sndh[at] == 0 {
			return at, nil
		}
	}
	return 0, noEnd()
}

// sized is the subtunes a tag sized by '##' runs over: '##' stands before
// it.
func sized(subtunes int, name string, at int) (int, error) {
	if subtunes < 0 {
		return 0, fmt.Errorf("the SNDH file's %s tag at %d stands before the '##' count"+
			" that sizes it", name, at)
	}
	return subtunes, nil
}

func noEnd() error {
	return fmt.Errorf("not an SNDH file: no HDNS ends its tags")
}

// Core is where the core begins: its YMXS, past the tags, less the magic's
// offset. The entry triple's first bra.w reaches the core's first byte, or
// the file is not one this wrote, and the core's descriptor stands whole
// in the file, since this reads its flags.
func Core(sndh []byte, from int) (int, error) {
	at := find(sndh, string(coreMagic), from, len(sndh))
	if at < 0 {
		return 0, fmt.Errorf("the SNDH file has no core: no YMXS past its tags")
	}
	core := at - coreMagicAt
	reached := -1
	if ymxr.GetWord(sndh, 0) == braW {
		reached = 2 + int(int16(ymxr.GetWord(sndh, 2)))
	}
	if core < from || reached != core {
		return 0, fmt.Errorf("the core begins at %d, and the entry triple reaches %d",
			core, reached)
	}
	if core+coreLength > len(sndh) {
		return 0, fmt.Errorf("the core begins at %d and the file ends %d bytes on, short"+
			" of the core's descriptor, %d bytes", core, len(sndh)-core, coreLength)
	}
	return core, nil
}

func digit(b byte) bool {
	return b >= '0' && b <= '9'
}

// find is where a text first stands in [from, end), or -1.
func find(said []byte, text string, from, end int) int {
	wanted := []byte(text)
	for at := from; at+len(wanted) <= end; at++ {
		if bytes.Equal(wanted, said[at:at+len(wanted)]) {
			return at
		}
	}
	return -1
}
