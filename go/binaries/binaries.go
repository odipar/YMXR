// Package binaries contains the 68000 binaries the tools combine with
// bound tunes (doc/BINARIES.md): the SNDH core, which goes under an SNDH
// file's entries, and the program stub, which goes in front of an SNDH
// file.
//
// Three switches of the player's are in the core, the raster monitor,
// the lean tick and the row read through an absolute address, and each of
// their eight settings is a separate core, so a file that requests two
// uses the core that is both. A tick reads its row through the program
// counter where YMXR_PCREL is at 1, its value unasked
// (doc/performance.md).
//
// The files are build output. The Maven build writes them into data/,
// which go:embed reads only inside its module. A tree built without
// them does not contain one: Read then returns nil and the caller reads
// them out of the directory YMXR_68K names.
package binaries

import (
	"embed"
	"fmt"
	"os"
	"path/filepath"
)

// The directory rather than the files in it: a tree whose build has not
// run does not have a .bin here, and a pattern that did not match a file
// would not compile.
//
//go:embed data
var data embed.FS

// The nine, by the file each is carried as. The four with abs in the name
// are the four above with the player's YMXR_PCREL=0: a tick reads its row
// through an absolute address rather than a displacement from the
// instruction that reads it (doc/performance.md).
const (
	Core                = "YMXR_sndh.bin"
	Monitor             = "YMXR_sndh-perf.bin"
	Lean                = "YMXR_sndh-lean.bin"
	MonitorLean         = "YMXR_sndh-perf-lean.bin"
	Absolute            = "YMXR_sndh-abs.bin"
	MonitorAbsolute     = "YMXR_sndh-perf-abs.bin"
	LeanAbsolute        = "YMXR_sndh-lean-abs.bin"
	MonitorLeanAbsolute = "YMXR_sndh-perf-lean-abs.bin"
	Stub                = "YMXR_prg.bin"
)

// All is the nine, in the order the build writes them.
func All() []string {
	return []string{Core, Monitor, Lean, MonitorLean,
		Absolute, MonitorAbsolute, LeanAbsolute, MonitorLeanAbsolute, Stub}
}

// Named is the core of the three switches: the raster monitor in where
// monitor, ticks that neither drop the interrupt level nor write their end
// of interrupt where lean, and a row read through the program counter
// where pcrel, which every core but the four named for an absolute
// address does.
func Named(monitor, lean, pcrel bool) string {
	if pcrel {
		if monitor {
			if lean {
				return MonitorLean
			}
			return Monitor
		}
		if lean {
			return Lean
		}
		return Core
	}
	if monitor {
		if lean {
			return MonitorLeanAbsolute
		}
		return MonitorAbsolute
	}
	if lean {
		return LeanAbsolute
	}
	return Absolute
}

// Read is a binary as this executable contains it, or as YMXR_68K names a
// directory to read it from.
func Read(name string) ([]byte, error) {
	if where := os.Getenv("YMXR_68K"); where != "" {
		return os.ReadFile(filepath.Join(where, name))
	}
	file, err := data.ReadFile("data/" + name)
	if err != nil {
		return nil, fmt.Errorf("no binary carried at data/%s, and YMXR_68K names no"+
			" directory to read it from: the build's binaries step writes it", name)
	}
	return file, nil
}

// Embedded is how many binaries this executable contains, which a test
// reads to skip where the build has not run.
func Embedded() int {
	said, err := data.ReadDir("data")
	if err != nil {
		return 0
	}
	count := 0
	for _, one := range said {
		if filepath.Ext(one.Name()) == ".bin" {
			count++
		}
	}
	return count
}
