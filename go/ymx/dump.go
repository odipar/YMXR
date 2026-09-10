// Package ymx reads a YMX file into a YMXS tune: its fourteen register
// streams as the rows a dump's registers make, and its script's four
// channels as the four effects.
//
// A channel of YMX and an effect here are the same thing: a source on a
// target at a timer's rate. What each opcode does to one is YMX's SPEC.md
// 3; what an effect is here is SPEC.md 1.8 and 1.9, so the walk in read.go
// reads one and writes the other.
//
// The file is read through YMX's own ymx-dump, which YMX_DUMP names, and
// that opens a file name rather than a stream, so it is called with the
// name of this tool's standard input, on that input. No copy of the file
// is written.
package ymx

import (
	"bufio"
	"errors"
	"fmt"
	"os"
	"os/exec"
	"strconv"
	"strings"

	"github.com/odipar/ymxr/go/ym"
)

// dump is the YMX tool this reads, which YMX_DUMP names.
func dump() string {
	if named := os.Getenv("YMX_DUMP"); named != "" {
		return named
	}
	return "ymx-dump"
}

// standardInput is the name this process's standard input opens under.
const standardInput = "/dev/stdin"

// Streams the script stands in (YMX, SPEC.md 2).
const (
	StreamM  = 14
	StreamX  = 15
	StreamA0 = 17
)

// acts is the bits of M that mark a channel acting.
const acts = 0x0F

// Dumped is one .ymx read out: the header's frames and rate, the streams,
// and each sample's level bytes with the end marker after them and the
// position it loops to, $FFFF where it plays once (YMX, SPEC.md 6).
type Dumped struct {
	Frames    int
	Rate      int
	LoopFrame int
	Streams   [][]byte
	Samples   [][]byte
	Loops     []int
}

// FormatException is an input ymx-dump does not read: its exit of 1, and
// the fault itself on standard error, from ymx-dump.
type FormatException struct {
	Said string
}

func (f *FormatException) Error() string {
	return f.Said
}

// StandardInput is standard input read out through ymx-dump.
func StandardInput() (Dumped, error) {
	return read(standardInput, true)
}

// File is the file at that path read out through ymx-dump.
func File(named string) (Dumped, error) {
	return read(named, false)
}

func read(file string, input bool) (Dumped, error) {
	named := file
	if input {
		named = "standard input"
	}
	run := exec.Command(dump(), file)
	run.Stderr = os.Stderr
	if input {
		run.Stdin = os.Stdin
	}
	out, err := run.StdoutPipe()
	if err != nil {
		return Dumped{}, err
	}
	if err := run.Start(); err != nil {
		return Dumped{}, fmt.Errorf("cannot run %s: YMX_DUMP names YMX's ymx-dump", dump())
	}
	read := Dumped{Rate: 50}
	streams := 0
	said := bufio.NewScanner(out)
	said.Buffer(make([]byte, 0, 1<<20), 1<<24)
	for said.Scan() {
		word := strings.Split(said.Text(), " ")
		switch {
		case word[0] == "frames":
			read.Frames, _ = strconv.Atoi(word[1])
		case word[0] == "rate":
			read.Rate, _ = strconv.Atoi(word[1])
		case word[0] == "loop":
			read.LoopFrame, _ = strconv.Atoi(word[1])
		case word[0] == "streams":
			streams, _ = strconv.Atoi(word[1])
			read.Streams = make([][]byte, streams)
			for s := range read.Streams {
				read.Streams[s] = make([]byte, read.Frames)
			}
		case word[0] == "samples":
			count, _ := strconv.Atoi(word[1])
			read.Samples = make([][]byte, count)
			read.Loops = make([]int, count)
		case word[0] == "sample":
			at, _ := strconv.Atoi(word[1])
			read.Loops[at], _ = strconv.Atoi(word[3])
			level := make([]byte, len(word)-4)
			for i := range level {
				value, _ := strconv.Atoi(word[i+4])
				level[i] = byte(value)
			}
			read.Samples[at] = level
		case len(word) == streams+1:
			frame, _ := strconv.Atoi(word[0])
			for s := 0; s < streams; s++ {
				value, _ := strconv.Atoi(word[s+1])
				read.Streams[s][frame] = byte(value)
			}
		}
	}
	if err := run.Wait(); err != nil {
		var exit *exec.ExitError
		if errors.As(err, &exit) && exit.ExitCode() == 1 {
			return Dumped{}, &FormatException{Said: fmt.Sprintf("%s is not a file %s reads",
				named, dump())}
		}
		return Dumped{}, fmt.Errorf("%s did not read %s", dump(), named)
	}
	return read, nil
}

// Acting is how many of the file's frames act on a channel.
func Acting(read Dumped) int {
	frames := 0
	for frame := 0; frame < read.Frames; frame++ {
		if read.Streams[StreamM][frame]&acts != 0 {
			frames++
		}
	}
	return frames
}

// Song is a dumped file as a YM song of its fourteen register streams,
// which the converter reads as it reads a dump's.
func Song(read Dumped, name string) ym.Song {
	values := make([][]byte, ym.Registers)
	for r := range values {
		values[r] = make([]byte, read.Frames)
	}
	for r := 0; r < 14; r++ {
		copy(values[r], read.Streams[r][:read.Frames])
	}
	return ym.Song{Format: "YMX!", Frames: read.Frames, PlayerHz: read.Rate,
		MasterClock: 2000000, LoopFrame: int64(read.LoopFrame), Name: name,
		Values: values}
}
