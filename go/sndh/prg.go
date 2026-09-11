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
//	14      2      the rate, rows a second, patched here from the TC tag
//	16      4      the rows to play, patched here; 0 plays the tune's row count
//	20      4      the core's offset from the SNDH file's first byte, patched here

var stubMagic = []byte{'Y', 'M', 'X', 'T'}

const (
	stubMagicAt    = 4
	stubVersion    = 1
	stubVersionAt  = 8
	stubSubtunesAt = 10
	StubFlagsAt    = 12
	stubRateAt     = 14
	stubRowsAt     = 16
	stubCoreAt     = 20
	stubLength     = 24
)

// FlagClear is flag bit 0: the screen cleared before the banner. Set where
// the SNDH file's core has the raster monitor in, so that the monitor's
// bars stand where the desktop's pixels were. It follows the core, and a
// caller does not choose it.
const FlagClear = 1

// FlagVBL is flag bit 1: play from the VBL, a 50 Hz clock. Set where the
// set claims Timer C, since the stub then has no timer to play from; such
// a set is at 50 Hz, or there is no program.
const FlagVBL = 2

// The PRG header's bytes, and its magic.
const (
	Header   = 28
	PrgMagic = 0x601A
)

// tagsAt is where the tag block begins in an SNDH file, past the entry
// triple: its SNDH, then the tags.
const tagsAt = 12

// Tagged is what the tag block says: the subtunes, the rate, the FLAG
// letters and where the block ends.
type Tagged struct {
	Subtunes int
	Rate     int
	Flag     string
	End      int
}

// Program is the program around an SNDH file, playing that many rows, or
// the tune's row count where rows is 0.
func Program(sndh []byte, rows int64) ([]byte, error) {
	stub, err := binaries.Read(binaries.Stub)
	if err != nil {
		return nil, err
	}
	return ProgramWith(stub, sndh, rows)
}

// ProgramWith is the same, from the stub named.
func ProgramWith(stub, sndh []byte, rows int64) ([]byte, error) {
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
	timerC := strings.ContainsRune(tags.Flag, 'c')
	if timerC && tags.Rate != 50 {
		return nil, fmt.Errorf("the set claims Timer C and plays at %d Hz: the stub then"+
			" plays from the VBL, a 50 Hz clock, so this set needs a host of its own",
			tags.Rate)
	}
	core, err := Core(sndh, tags.End+4)
	if err != nil {
		return nil, err
	}
	monitor := ymxr.GetWord(sndh, core+coreFlagsAt)&coreMonitor != 0
	prg := make([]byte, Header+len(stub)+len(sndh)+4)
	ymxr.PutWord(prg, 0, PrgMagic)
	ymxr.PutLong(prg, 2, len(stub)+len(sndh))
	copy(prg[Header:], stub)
	ymxr.PutWord(prg, Header+stubSubtunesAt, tags.Subtunes)
	flags := 0
	if monitor {
		flags |= FlagClear
	}
	if timerC {
		flags |= FlagVBL
	}
	ymxr.PutWord(prg, Header+StubFlagsAt, flags)
	ymxr.PutWord(prg, Header+stubRateAt, tags.Rate)
	ymxr.PutLong(prg, Header+stubRowsAt, int(rows))
	ymxr.PutLong(prg, Header+stubCoreAt, core)
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
// two digits the subtunes; TC and each text tag, TITL, COMM, CONV and
// FLAG, run to their zero byte and one past; FRMS is 4 + 4 bytes a
// subtune, and '!#SN' 4 + 2 bytes a subtune, then a name a subtune, each
// to its zero byte and one past. The subtunes, the rate and the FLAG
// letters come from those tags alone, so a title or a composer that reads
// like a tag patches no field.
func ReadTags(sndh []byte) (Tagged, error) {
	if len(sndh) < tagsAt+4 || string(sndh[tagsAt:tagsAt+4]) != "SNDH" {
		return Tagged{}, fmt.Errorf("not an SNDH file: no SNDH at %d", tagsAt)
	}
	subtunes := -1
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
		case strings.HasPrefix(name, "TC"):
			to, err := zero(sndh, at+2)
			if err != nil {
				return Tagged{}, err
			}
			rate = 0
			for i := at + 2; i < to && digit(sndh[i]); i++ {
				rate = rate*10 + int(sndh[i]-'0')
			}
			if rate == 0 {
				return Tagged{}, fmt.Errorf("the SNDH file's tags have no TC rate")
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
		return Tagged{}, fmt.Errorf("the SNDH file's tags have no TC rate")
	}
	return Tagged{Subtunes: subtunes, Rate: rate, Flag: flag, End: at}, nil
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
