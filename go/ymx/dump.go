// Package ymx reads a YMX file into a YMXS tune: its fourteen register
// streams as the rows a dump's registers make, and its script's four
// channels as the four effects.
//
// A channel of YMX and an effect here are the same thing: a source on a
// target at a timer's rate. What each opcode does to one is YMX's SPEC.md
// 3; what an effect is here is SPEC.md 1.8 and 1.9, so the walk in read.go
// reads one and writes the other.
//
// The file is decoded by YMX's reader, which this imports: a tool of this
// repository reads a .ymx with no other program installed. YMX's ymx-dump
// prints the same values as text, and the Java tools here read it that
// way.
package ymx

import (
	"fmt"
	"io"
	"os"

	"github.com/odipar/ymx/go/check"

	"github.com/odipar/ymxr/go/ym"
)

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

// FormatException is an input the reader does not read: the fault it
// returned, under the name the input was read by.
type FormatException struct {
	Said string
}

func (f *FormatException) Error() string {
	return f.Said
}

// StandardInput is standard input read out.
func StandardInput() (Dumped, error) {
	file, err := io.ReadAll(os.Stdin)
	if err != nil {
		return Dumped{}, err
	}
	return read(file, "standard input")
}

// File is the file at that path read out.
func File(named string) (Dumped, error) {
	file, err := os.ReadFile(named)
	if err != nil {
		return Dumped{}, err
	}
	return read(file, named)
}

// read is those bytes decoded, under the name the fault reports them by.
func read(file []byte, named string) (Dumped, error) {
	out, err := check.ReadFile(file)
	if err != nil {
		return Dumped{}, &FormatException{
			Said: fmt.Sprintf("%s is not a YMX file: %s", named, err)}
	}
	// A stream decodes to at least the file's frames, and a section that
	// ends on a unit boundary decodes to more. A frame past the count is
	// no frame of the tune, so every stream is cut to the count here and
	// the walk indexes without a separate bound.
	streams := make([][]byte, len(out.Streams))
	for s, stream := range out.Streams {
		streams[s] = stream[:out.Frames]
	}
	return Dumped{Frames: out.Frames, Rate: out.Rate, LoopFrame: out.LoopFrame,
		Streams: streams, Samples: out.Samples, Loops: out.Loops}, nil
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
