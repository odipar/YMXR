// Package binaries contains the assembled 68000 SNDH cores and program
// stub (doc/BINARIES.md). Five switches select thirty-two cores: raster
// monitor, lean ticks, absolute source reads, deferred starts and
// transformation lookups. Tools select lookup support only when a
// subtune requires it, and deferred support only when a subtune defers
// a start. Source reads use the program counter by default.
//
// The Maven build copies these files into data/ for go:embed. Read
// returns nil for absent resources; callers then use the directory
// named by YMXR_68K.
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
	Core      = "YMXR_sndh.bin"
	Stub      = "YMXR_prg.bin"
	MixedStub = "YMXR_prg-mixed.bin"
	Clock     = "YMXR_clock.bin"
)

// all lists the binaries by name, so release/publish.sh reads the list
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
	Stub, MixedStub, Clock,
}

// All returns the thirty-two cores in build order, then the two
// program stubs and the mixed-rate clock.
func All() []string {
	return append([]string(nil), all...)
}

// Named selects a core by five switches. Suffixes occur in this order:
// -perf for the raster monitor; -lean for ticks without interrupt
// nesting or an end-of-interrupt write; -abs when pcrel is false;
// -defer for deferred starts; -map for transformation lookups.
//
// Without -abs, ticks use program-counter displacements. A subtune with
// effects bit 4 requires -defer; bit 5 requires -map. Lookup cores read
// bound versions 7 to 10; other cores read 7 to 9 (doc/BINARIES.md 2).
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
