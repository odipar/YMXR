// Package ym reads a YM5!/YM6! register dump into the tune data
// structure: one row a frame, and a source for each distinct sound the
// dump's effect slots produce.
//
// The dump is read as the YM format page describes it. The layout is a
// fixed header, optional extra data, the digidrum samples, three
// NUL-terminated strings, and then the frames: either 16 vectors of one
// register each (the interleaved option) or one 16-byte record a frame.
// Both come out as 16 register vectors.
//
// Distributed .ym files are usually LHA archives containing this data; the
// reader unpacks them itself, through the copy of that unpacker in YMXS.
package ym

import (
	"fmt"
	"os"

	"github.com/odipar/ymxs/ym"
)

// Registers is the register count in the file: R0 to R15, the last two
// being the I/O ports.
const Registers = 16

// Drums4Bit is attribute bit 2: drum samples are 4-bit values, one a byte.
const Drums4Bit = 4

// Song is one parsed tune, in the terms of the file alone: what the header
// said, the frames as read, and the samples as stored.
//
// Values[r][frame] is R(r)'s raw value, all sixteen of them - the two I/O
// ports included, because in this format they are where an effect's timer
// count is filed. Only fourteen reach the columns.
type Song struct {
	Format      string
	Frames      int
	PlayerHz    int
	MasterClock int64
	LoopFrame   int64
	Interleaved bool
	Attributes  int64
	Drums       [][]byte
	Name        string
	Author      string
	Comment     string
	Values      [][]byte
}

// FormatException is anything this reader will not accept, with a usable
// message.
type FormatException struct {
	Said string
}

func (f *FormatException) Error() string {
	return f.Said
}

func wrong(said string, args ...any) error {
	return &FormatException{Said: fmt.Sprintf(said, args...)}
}

type dump struct {
	data []byte
	at   int
}

// IsDump is whether the data, unpacked, opens as a YM5! or YM6! dump.
func IsDump(data []byte) bool {
	if len(data) < 4 {
		return false
	}
	format := string(data[:4])
	return format == "YM5!" || format == "YM6!"
}

// Read is the song in the data.
func Read(data []byte) (Song, error) {
	if ym.IsArchive(data) {
		out, err := ym.Unpack(data)
		if err != nil {
			return Song{}, wrong("cannot unpack this .ym's LHA wrapper: %s", err.Error())
		}
		data = out
	}
	d := &dump{data: data}
	return d.run()
}

func (d *dump) run() (Song, error) {
	format, err := d.ascii(4)
	if err != nil {
		return Song{}, err
	}
	if format != "YM6!" && format != "YM5!" {
		return Song{}, wrong("not a YM5!/YM6! file (starts with %q); YM2/YM3/YM4 and"+
			" packed .ym files are not supported", format)
	}
	check, err := d.ascii(8)
	if err != nil {
		return Song{}, err
	}
	if check != "LeOnArD!" {
		return Song{}, wrong("missing the LeOnArD! check string after %s", format)
	}
	frames, err := d.u32()
	if err != nil {
		return Song{}, err
	}
	attributes, err := d.u32()
	if err != nil {
		return Song{}, err
	}
	digidrums, err := d.u16()
	if err != nil {
		return Song{}, err
	}
	masterClock, err := d.u32()
	if err != nil {
		return Song{}, err
	}
	playerHz, err := d.u16()
	if err != nil {
		return Song{}, err
	}
	loopFrame, err := d.u32()
	if err != nil {
		return Song{}, err
	}
	additional, err := d.u16()
	if err != nil {
		return Song{}, err
	}
	if err := d.skip(additional, "additional data"); err != nil {
		return Song{}, err
	}
	drums := make([][]byte, digidrums)
	for i := 0; i < digidrums; i++ {
		size, err := d.u32()
		if err != nil {
			return Song{}, err
		}
		if size < 0 || size > int64(len(d.data)-d.at) {
			return Song{}, wrong("truncated file: digidrum %d claims %d bytes", i, size)
		}
		drums[i] = make([]byte, size)
		copy(drums[i], d.data[d.at:])
		d.at += int(size)
	}
	name, err := d.text()
	if err != nil {
		return Song{}, err
	}
	author, err := d.text()
	if err != nil {
		return Song{}, err
	}
	comment, err := d.text()
	if err != nil {
		return Song{}, err
	}
	if frames <= 0 || frames > 0x7FFFFFFF {
		return Song{}, wrong("unusable frame count %d", frames)
	}
	if playerHz <= 0 {
		return Song{}, wrong("unusable player frequency %d Hz", playerHz)
	}
	count := int(frames)
	interleaved := attributes&1 != 0
	var values [][]byte
	if interleaved {
		values, err = d.interleaved(count)
	} else {
		values, err = d.perFrame(count)
	}
	if err != nil {
		return Song{}, err
	}
	// 'End!' closes the file. Some tools omit it; the frames are all read
	// by now, so this only reports, it does not reject.
	if d.at+4 <= len(d.data) {
		if end, err := d.ascii(4); err == nil && end != "End!" {
			fmt.Fprintln(os.Stderr, "Warning: no End! marker after the frames")
		}
	}
	return Song{Format: format, Frames: count, PlayerHz: playerHz,
		MasterClock: masterClock, LoopFrame: loopFrame, Interleaved: interleaved,
		Attributes: attributes, Drums: drums, Name: name, Author: author,
		Comment: comment, Values: values}, nil
}

func (d *dump) interleaved(frames int) ([][]byte, error) {
	if err := d.need(int64(frames)*Registers, "interleaved frame data"); err != nil {
		return nil, err
	}
	values := make([][]byte, Registers)
	for r := 0; r < Registers; r++ {
		values[r] = make([]byte, frames)
		copy(values[r], d.data[d.at:])
		d.at += frames
	}
	return values, nil
}

func (d *dump) perFrame(frames int) ([][]byte, error) {
	if err := d.need(int64(frames)*Registers, "frame data"); err != nil {
		return nil, err
	}
	values := make([][]byte, Registers)
	for r := range values {
		values[r] = make([]byte, frames)
	}
	for frame := 0; frame < frames; frame++ {
		for r := 0; r < Registers; r++ {
			values[r][frame] = d.data[d.at]
			d.at++
		}
	}
	return values, nil
}

func (d *dump) need(bytes int64, what string) error {
	if bytes > int64(len(d.data)-d.at) {
		return wrong("truncated file: %s needs %d bytes but only %d are left",
			what, bytes, len(d.data)-d.at)
	}
	return nil
}

func (d *dump) skip(bytes int, what string) error {
	if bytes < 0 {
		return wrong("negative size for %s", what)
	}
	if err := d.need(int64(bytes), what); err != nil {
		return err
	}
	d.at += bytes
	return nil
}

func (d *dump) ascii(bytes int) (string, error) {
	if err := d.need(int64(bytes), "header field"); err != nil {
		return "", err
	}
	said := string(d.data[d.at : d.at+bytes])
	d.at += bytes
	return said, nil
}

// text reads a header string, which a zero byte ends. Its bytes are one
// character each, as the dumps have them.
func (d *dump) text() (string, error) {
	end := d.at
	for end < len(d.data) && d.data[end] != 0 {
		end++
	}
	if end == len(d.data) {
		return "", wrong("unterminated header string")
	}
	runes := make([]rune, end-d.at)
	for i, b := range d.data[d.at:end] {
		runes[i] = rune(b)
	}
	d.at = end + 1
	return string(runes), nil
}

func (d *dump) u16() (int, error) {
	if err := d.need(2, "header field"); err != nil {
		return 0, err
	}
	value := int(d.data[d.at])<<8 | int(d.data[d.at+1])
	d.at += 2
	return value, nil
}

func (d *dump) u32() (int64, error) {
	high, err := d.u16()
	if err != nil {
		return 0, err
	}
	low, err := d.u16()
	if err != nil {
		return 0, err
	}
	return int64(high)<<16 | int64(low), nil
}
