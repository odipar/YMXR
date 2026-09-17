package ymxr

import (
	"errors"
	"fmt"
	"path/filepath"
	"strings"
)

// A multi file: several tune files in one, a name each, which ymxr-sndh
// reads as a set of subtunes (doc/BINARIES.md 0). A player reads a tune
// file (SPEC.md 3.3) and never this: a multi file passes tune files
// between tools, so that a tool reads one input and writes one output.

// MultiMagic is the four bytes a multi file opens with.
var MultiMagic = []byte{'Y', 'M', 'X', 'M'}

// Where a multi file's header fields stand.
const (
	MultiCountAt = 6
	MultiIndexAt = 8
)

// entry is where a tune file begins, and its bytes.
const entry = 8

// MostTunes is the most tunes in one, the subtunes an SNDH file numbers.
const MostTunes = MaxSubtunes

// MultiRead is the tune files a multi file has in it, and the name of
// each, both in the file's order.
type MultiRead struct {
	Tunes [][]byte
	Names []string
}

// IsMulti is whether the file opens as a multi file.
// multiVersion is the version of a multi file: the highest of the tune
// files in it (SPEC.md 3.3.5).
func multiVersion(tunes [][]byte) int {
	for _, tune := range tunes {
		if len(tune) >= 6 && GetWord(tune, 4) == VersionColumns {
			return VersionColumns
		}
	}
	return Version
}

func IsMulti(file []byte) bool {
	return len(file) >= MultiIndexAt && string(file[:4]) == string(MultiMagic)
}

// Multi is the multi file of these tune files, named one for one.
func Multi(tunes [][]byte, names []string) ([]byte, error) {
	if len(tunes) != len(names) {
		return nil, fmt.Errorf("%d names for %d tunes", len(names), len(tunes))
	}
	if len(tunes) == 0 {
		return nil, errors.New("no tunes: a multi file has one at least")
	}
	if len(tunes) > MostTunes {
		return nil, fmt.Errorf("%d tunes, and a multi file has %d at most",
			len(tunes), MostTunes)
	}
	said := make([][]byte, len(tunes))
	text := 0
	for i, tune := range tunes {
		if _, err := Read(tune); err != nil {
			return nil, err
		}
		said[i] = []byte(names[i])
		text += len(said[i]) + 1
	}
	at := Align(MultiIndexAt + entry*len(tunes) + text)
	bytes := at
	for _, tune := range tunes {
		bytes = Align(bytes + len(tune))
	}
	file := make([]byte, bytes)
	copy(file, MultiMagic)
	PutWord(file, 4, multiVersion(tunes))
	PutWord(file, MultiCountAt, len(tunes))
	name := MultiIndexAt + entry*len(tunes)
	for i, tune := range tunes {
		PutLong(file, MultiIndexAt+entry*i, at)
		PutLong(file, MultiIndexAt+entry*i+4, len(tune))
		copy(file[name:], said[i])
		name += len(said[i]) + 1
		copy(file[at:], tune)
		at = Align(at + len(tune))
	}
	return file, nil
}

// ReadMulti is the tune files and names the file has in it.
func ReadMulti(file []byte) (MultiRead, error) {
	if !IsMulti(file) {
		return MultiRead{}, errors.New("not a YMXM file")
	}
	version := GetWord(file, 4)
	if version != Version && version != VersionColumns {
		return MultiRead{}, fmt.Errorf("version %d is not %d or %d", version, Version,
			VersionColumns)
	}
	count := GetWord(file, MultiCountAt)
	if count < 1 || count > MostTunes {
		return MultiRead{}, fmt.Errorf("%d tunes, and a multi file has 1 to %d",
			count, MostTunes)
	}
	name := MultiIndexAt + entry*count
	if name > len(file) {
		return MultiRead{}, fmt.Errorf("the entries of %d tunes stand past the file's"+
			" %d bytes", count, len(file))
	}
	out := MultiRead{}
	for i := 0; i < count; i++ {
		at := GetLong(file, MultiIndexAt+entry*i)
		bytes := GetLong(file, MultiIndexAt+entry*i+4)
		if at < 0 || bytes < 0 || at+bytes > len(file) {
			return MultiRead{}, fmt.Errorf("tune %d stands at %d for %d bytes, and the"+
				" file has %d", i+1, at, bytes, len(file))
		}
		tune := make([]byte, bytes)
		copy(tune, file[at:at+bytes])
		out.Tunes = append(out.Tunes, tune)
		end := name
		for end < len(file) && file[end] != 0 {
			end++
		}
		out.Names = append(out.Names, string(file[name:end]))
		name = end + 1
	}
	return out, nil
}

// Stem is the stem of a file's name, which names its tune.
func Stem(file string) string {
	name := filepath.Base(file)
	if dot := strings.LastIndex(name, "."); dot > 0 {
		return name[:dot]
	}
	return name
}
