// Package binaries contains the 68000 binaries the tools combine with
// bound tunes (doc/BINARIES.md): the SNDH core, which goes under an SNDH
// file's entries, and the program stub, which goes in front of an SNDH
// file.
//
// Five switches of the player's are in the core, the raster monitor, the
// lean tick, the row read through an absolute address, the deferred start
// and the lookup, and each of their thirty-two settings is a separate core,
// so a file that requests two uses the core that is both. A tick reads its
// row through the program counter where YMXR_PCREL is at 1, its value
// unasked, a file uses a core with the deferred start in where a subtune
// defers one (doc/performance.md), and a core with the lookup in where a
// subtune maps a value (SPEC.md 1.10).
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

// The core with no switch set, and the stub, by the file each is carried
// as.
const (
	Core = "YMXR_sndh.bin"
	Stub = "YMXR_prg.bin"
)

// all is the thirty-three by name, so release/publish.sh reads the list
// off this file.
var all = []string{
	Core, "YMXR_sndh-perf.bin", "YMXR_sndh-lean.bin", "YMXR_sndh-perf-lean.bin",
	"YMXR_sndh-abs.bin", "YMXR_sndh-perf-abs.bin", "YMXR_sndh-lean-abs.bin",
	"YMXR_sndh-perf-lean-abs.bin",
	"YMXR_sndh-defer.bin", "YMXR_sndh-perf-defer.bin", "YMXR_sndh-lean-defer.bin",
	"YMXR_sndh-perf-lean-defer.bin", "YMXR_sndh-abs-defer.bin",
	"YMXR_sndh-perf-abs-defer.bin", "YMXR_sndh-lean-abs-defer.bin",
	"YMXR_sndh-perf-lean-abs-defer.bin",
	"YMXR_sndh-map.bin", "YMXR_sndh-perf-map.bin", "YMXR_sndh-lean-map.bin",
	"YMXR_sndh-perf-lean-map.bin", "YMXR_sndh-abs-map.bin", "YMXR_sndh-perf-abs-map.bin",
	"YMXR_sndh-lean-abs-map.bin", "YMXR_sndh-perf-lean-abs-map.bin",
	"YMXR_sndh-defer-map.bin", "YMXR_sndh-perf-defer-map.bin",
	"YMXR_sndh-lean-defer-map.bin", "YMXR_sndh-perf-lean-defer-map.bin",
	"YMXR_sndh-abs-defer-map.bin", "YMXR_sndh-perf-abs-defer-map.bin",
	"YMXR_sndh-lean-abs-defer-map.bin", "YMXR_sndh-perf-lean-abs-defer-map.bin",
	Stub,
}

// All is the thirty-three, in the order the build writes them: the
// thirty-two cores, the sixteen without the lookup first, then the stub.
func All() []string {
	return append([]string(nil), all...)
}

// Named is the core of the five switches (doc/performance.md), each a
// suffix of its name in this order: -perf where monitor, the raster
// monitor in; -lean where lean, ticks that neither drop the interrupt
// level nor write their end of interrupt; -abs where pcrel is false, a row
// read through an absolute address, where every other core reads it
// through the program counter; -defer where deferred, the deferred start
// in; and -map where mapped, the lookup in, which reads bound tunes to
// version 10 where every other core reads them to version 9.
func Named(monitor, lean, pcrel, deferred, mapped bool) string {
	name := "YMXR_sndh"
	if monitor {
		name += "-perf"
	}
	if lean {
		name += "-lean"
	}
	if !pcrel {
		name += "-abs"
	}
	if deferred {
		name += "-defer"
	}
	if mapped {
		name += "-map"
	}
	return name + ".bin"
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
