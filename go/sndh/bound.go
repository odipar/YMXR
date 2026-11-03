// Package sndh binds a tune file with DTX's reader and puts it behind the
// SNDH core and the program stub (doc/BINARIES.md).
package sndh

import (
	"fmt"

	"github.com/odipar/dtx/go/dtx"
	"github.com/odipar/dtx/go/pack"

	"github.com/odipar/ymxr/go/ymxr"
)

// A tune bound with DTX's reader for playback (doc/BINARIES.md 1).
// Sources precede the reader image; optional transformation tables follow
// it. Frames prepare lookups from those tables; ticks read source rows
// and prepared lookup pages.
//
//	offset  bytes  what it is
//	0       4      YMXB
//	4       2      the version, the tune file's, $0007 to $000A
//	6       2      the frame rate, in Hz
//	8       1      effects used, the tune file's
//	9       1      S, the source count, 0 to 127
//	10      2      bytes 10 and 11 of the tune file, which a player skips
//	12      4      the state block's bytes the image's reader needs
//	16      4      where the image begins, signed
//	20      4      where this tune's table is, from the image's first byte
//	24      4S     versions 7 to 9: the source index, where source 1 to
//	               S's DTX1 table begins
//	24      4      version 10: where the transformation block begins,
//	               signed
//	28      4S     version 10: the source index
//	        ..     the DTX1 tables, each on a long
//	        ..     the image, on a long, in a bound tune written alone
//	        ..     version 10: the transformation block, on a long, in a
//	               bound tune written alone: the tune file's transformation
//	               index and transformations, byte for byte
//
// Every offset counts from the first byte.

// BoundMagic is the four bytes a bound tune opens with.
var BoundMagic = []byte{'Y', 'M', 'X', 'B'}

// Bound field offsets: BoundIndexAt is the source index in versions 7
// to 9; version 10 replaces that position with BoundTransformationsAt,
// the transformation-block pointer.
const (
	StateAt                = 12
	ImageAt                = 16
	BoundTableAt           = 20
	BoundIndexAt           = 24
	BoundTransformationsAt = 24
)

// BoundIndex returns the source-index offset: 24 in versions 7 to 9; 28
// in version 10, after the transformation-block pointer.
func BoundIndex(version int) int {
	if version == ymxr.Version {
		return BoundTransformationsAt + 4
	}
	return BoundIndexAt
}

// The image's format block, and where the state block's bytes and the
// first table are in it (DTX, abi.md 1).
const (
	FormatAt      = 16
	FormatStateAt = 4
	FormatTableAt = 8
)

// Set contains shared images and bound tunes in input order. Tunes with
// the same image parameters share reader code (DTX abi.md 1). Each
// bound tune omits its image and transformation block; ImageAt and
// BoundTransformationsAt are 0 until the caller places and patches
// them.
type Set struct {
	Images []([]byte)
	Shapes []string
	Tunes  [][]byte

	// Image is which image each tune's table is in, and Table where it
	// is in that image.
	Image []int
	Table []int

	// Blocks contains each tune's transformation block, nil below version
	// 10.
	Blocks [][]byte
}

// Bind is the set of tunes bound with as few images as the figures allow.
func Bind(tuneFiles [][]byte) (Set, error) {
	// A tune joins the first group whose image would carry its table: the
	// packager reads a table that does not fit, so the key names what it
	// reads it against.
	var groups [][]int
	var keys []string
	image := make([]int, len(tuneFiles))
	table := make([]int, len(tuneFiles))
	for i, file := range tuneFiles {
		read, err := ymxr.Read(file)
		if err != nil {
			return Set{}, err
		}
		key, err := shape(read.Dtx3)
		if err != nil {
			return Set{}, err
		}
		at := -1
		for k, one := range keys {
			if one == key {
				at = k
				break
			}
		}
		if at < 0 {
			at = len(keys)
			keys = append(keys, key)
			groups = append(groups, nil)
		}
		groups[at] = append(groups[at], i)
		image[i] = at
	}
	var images [][]byte
	for _, group := range groups {
		var tables [][]byte
		for _, i := range group {
			read, err := ymxr.Read(tuneFiles[i])
			if err != nil {
				return Set{}, err
			}
			tables = append(tables, read.Dtx3)
		}
		made, at, err := pack.Images(tables)
		if err != nil {
			return Set{}, err
		}
		images = append(images, made)
		for j, i := range group {
			table[i] = at[j]
		}
	}
	var tunes [][]byte
	blocks := make([][]byte, len(tuneFiles))
	for i, file := range tuneFiles {
		built, err := build(file, nil, table[i],
			ymxr.GetLong(images[image[i]], FormatAt+FormatStateAt))
		if err != nil {
			return Set{}, err
		}
		tunes = append(tunes, built)
		blocks[i] = Block(file)
	}
	return Set{Images: images, Shapes: keys, Tunes: tunes, Image: image, Table: table,
		Blocks: blocks}, nil
}

// shape is the values an image fixes once, as a key two tables are
// grouped by and as a reader of the report sees it: the variant, the width,
// the unit, and the column count of a table of 34 (DTX abi.md 1). Under
// DTX3 each table defines its ring, and the image has the copy code where a
// table of it has copies, so neither separates two tables. The period
// follows C at the writer's unit, so a table of 30 columns and one of 34 go
// into separate images, and the key of a table of 30 is the key of version
// 9.
func shape(dtx3 []byte) (string, error) {
	header, err := dtx.ReadHeader(dtx3)
	if err != nil {
		return "", err
	}
	payload := header.Length()
	columns := ""
	if header.Columns == ymxr.Mapped {
		columns = fmt.Sprintf(", %d columns", header.Columns)
	}
	return fmt.Sprintf("DTX%d at unit %d%s, values of %d", header.Variant,
		int(dtx3[payload+2]), columns, header.Width), nil
}

// Bound binds a tune file: header, recomputed source index, unchanged
// DTX1 tables, packaged DTX3 image and, in version 10, transformation
// block (doc/BINARIES.md 1).
func Bound(tuneFile []byte) ([]byte, error) {
	read, err := ymxr.Read(tuneFile)
	if err != nil {
		return nil, err
	}
	image, err := pack.Image(read.Dtx3)
	if err != nil {
		return nil, err
	}
	return build(tuneFile, image, ymxr.GetLong(image, FormatAt+FormatTableAt),
		ymxr.GetLong(image, FormatAt+FormatStateAt))
}

// Block copies a version 10 transformation block unchanged, or returns
// nil for versions 7 to 9. The block begins at the header's long at 16
// and ends after its index or last entry, whichever is later. Index
// offsets are relative to the block, so relocation preserves them. The
// reader has already checked bounds (SPEC.md 3.4.2).
func Block(tuneFile []byte) []byte {
	if ymxr.GetWord(tuneFile, 4) != ymxr.Version {
		return nil
	}
	at := ymxr.GetLong(tuneFile, ymxr.TransformationsAt)
	count := ymxr.GetWord(tuneFile, at)
	end := at + 2 + 4*count
	for n := 0; n < count; n++ {
		table := at + ymxr.GetLong(tuneFile, at+2+4*n)
		if last := table + 2 + ymxr.GetWord(tuneFile, table); last > end {
			end = last
		}
	}
	return tuneFile[at:end]
}

// build writes the bound header, source index and DTX1 tables. If image
// is supplied, append it and the version 10 transformation block. With
// nil image, the caller places the shared image and block and patches
// ImageAt and BoundTransformationsAt.
func build(tuneFile, image []byte, table, state int) ([]byte, error) {
	read, err := ymxr.Read(tuneFile)
	if err != nil {
		return nil, err
	}
	count := len(read.Sources)
	from := ymxr.IndexAt(read.Version)
	index := BoundIndex(read.Version)
	tables := make([][]byte, count)
	for i := 0; i < count; i++ {
		at := ymxr.GetLong(tuneFile, from+4*i) &^ ymxr.Counted
		to := len(tuneFile)
		if i+1 < count {
			to = ymxr.GetLong(tuneFile, from+4*(i+1)) &^ ymxr.Counted
		}
		tables[i] = tuneFile[at:to]
	}
	// Place sources before the image to preserve their reach through the
	// player's signed word displacements. Put the transformation block
	// last: frames read it, while ticks read sources and prepared lookup
	// pages (BINARIES.md 1.3).
	here := ymxr.Align(index + 4*count)
	sourceAt := make([]int, count)
	for i := 0; i < count; i++ {
		sourceAt[i] = here
		here = ymxr.Align(here + len(tables[i]))
	}
	imageAt := 0
	blockAt := 0
	block := Block(tuneFile)
	if image != nil {
		imageAt = here
		here = ymxr.Align(here + len(image))
		if block != nil {
			blockAt = here
			here = ymxr.Align(here + len(block))
		}
	}
	bound := make([]byte, here)
	copy(bound, tuneFile[:ymxr.TableAt])
	copy(bound, BoundMagic)
	ymxr.PutWord(bound, 4, ymxr.GetWord(tuneFile, 4))
	ymxr.PutLong(bound, StateAt, state)
	ymxr.PutLong(bound, ImageAt, imageAt)
	// Where this tune's table is in the image it is packaged into. An
	// image of one names it in its format block; one of several names the
	// first, so a tune past the first records a separate offset.
	ymxr.PutLong(bound, BoundTableAt, table)
	if block != nil {
		ymxr.PutLong(bound, BoundTransformationsAt, blockAt)
	}
	for i := 0; i < count; i++ {
		// A counted source's mark is in the index a player reads, so
		// bit 31 is in the binding beside the offset (SPEC.md 3.1).
		entry := sourceAt[i]
		if read.Counted[i] {
			entry |= ymxr.Counted
		}
		ymxr.PutLong(bound, index+4*i, entry)
	}
	if image != nil {
		copy(bound[imageAt:], image)
		copy(bound[blockAt:], block)
	}
	for i := 0; i < count; i++ {
		copy(bound[sourceAt[i]:], tables[i])
	}
	return bound, nil
}
