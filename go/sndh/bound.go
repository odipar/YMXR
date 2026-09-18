// Package sndh binds a tune file with DTX's reader and puts it behind the
// SNDH core and the program stub (doc/BINARIES.md).
package sndh

import (
	"fmt"

	"github.com/odipar/dtx/go/dtx"
	"github.com/odipar/dtx/go/pack"

	"github.com/odipar/ymxr/go/ymxr"
)

// A bound tune: a tune file's tables bound with DTX's reader into what the
// player reads. doc/BINARIES.md is the contract. The layout is the tune
// file's under its magic and version, with the image DTX packages the DTX2
// table with where the file has the table, and the bytes of the state
// block the image's reader needs recorded in the header, so that a host
// allocates them without reading the image.
//
//	offset  bytes  what it is
//	0       4      YMXB
//	4       2      the version, $0003
//	6       2      the frame rate, in Hz
//	8       1      effects used, bits 3 to 0
//	9       1      S, the source count, 0 to 127
//	10      2      zero
//	12      4      the state block's bytes the image's reader needs
//	16      4      where the image begins, signed
//	20      4      where this tune's table stands, from the image's first byte
//	24      4S     the source index: where source 1 to S's DTX1 table begins
//	        ..     the DTX1 tables, each on a long
//	        ..     the image, on a long
//
// Every offset counts from the first byte.

// BoundMagic is the four bytes a bound tune opens with.
var BoundMagic = []byte{'Y', 'M', 'X', 'B'}

// Where a bound tune's fields stand.
const (
	// BoundVersion is the version of a bound tune whose sources are one
	// column (SPEC.md 3.3.5).
	BoundVersion = ymxr.Version

	// BoundVersionColumns is the version of one with a source of several
	// columns.
	BoundVersionColumns = ymxr.VersionColumns

	// BoundVersionCounted is the version of one with a source whose column
	// fills its byte, which a player counts the rows of.
	BoundVersionCounted = ymxr.VersionCounted
	StateAt      = 12
	ImageAt      = 16
	BoundTableAt = 20
	BoundIndexAt = 24
)

// The image's format block, and where the state block's bytes and the
// first table stand in it (DTX, abi.md 1).
const (
	FormatAt      = 16
	FormatStateAt = 4
	FormatTableAt = 8
)

// Set is a set of tunes bound together: the images their tables were
// packaged into, and a bound tune for each, in the order named. A bound
// tune here has no image in it, and its ImageAt stands at 0 for
// the caller that lays them out to patch.
//
// The tunes are grouped by what an image fixes once (DTX abi.md 1), so
// tunes that agree on those share one image and the reader's code stands
// once for the group.
type Set struct {
	Images []([]byte)
	Shapes []string
	Tunes  [][]byte

	// Image is which image each tune's table stands in, and Table where it
	// stands in that image.
	Image []int
	Table []int
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
		key, err := shape(read.Dtx2)
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
			tables = append(tables, read.Dtx2)
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
	for i, file := range tuneFiles {
		built, err := build(file, nil, table[i],
			ymxr.GetLong(images[image[i]], FormatAt+FormatStateAt))
		if err != nil {
			return Set{}, err
		}
		tunes = append(tunes, built)
	}
	return Set{Images: images, Shapes: keys, Tunes: tunes, Image: image, Table: table}, nil
}

// shape is the values an image fixes once, as a key two tables are
// grouped by
// and as a reader of the report sees it: the variant, the width, and under
// DTX2 the unit, the copies flag and the ring (DTX abi.md 1). The period
// follows the ring and C, which the schema fixes.
func shape(dtx2 []byte) (string, error) {
	header, err := dtx.ReadHeader(dtx2)
	if err != nil {
		return "", err
	}
	payload := header.Length()
	copies := ""
	if dtx2[payload+3]&1 != 0 {
		copies = ", with copies"
	}
	return fmt.Sprintf("DTX%d at unit %d, a ring of %d, values of %d%s", header.Variant,
		int(dtx2[payload+2]), ymxr.GetWord(dtx2, payload), header.Width, copies), nil
}

// Bound is the bound tune of a tune file: the header with the magic and
// the version replaced and the state block's bytes and the image's place
// put in, the index recomputed, then the image, then the file's DTX1
// tables as they stand.
func Bound(tuneFile []byte) ([]byte, error) {
	read, err := ymxr.Read(tuneFile)
	if err != nil {
		return nil, err
	}
	image, err := pack.Image(read.Dtx2)
	if err != nil {
		return nil, err
	}
	return build(tuneFile, image, ymxr.GetLong(image, FormatAt+FormatTableAt),
		ymxr.GetLong(image, FormatAt+FormatStateAt))
}

// build is the bound tune: the header, the state block's bytes, the
// table's place in the image it stands in, the source index, then the
// image where the tune has one and the file's DTX1 tables. A nil image is
// a tune of a set, whose ImageAt the caller patches.
func build(tuneFile, image []byte, table, state int) ([]byte, error) {
	read, err := ymxr.Read(tuneFile)
	if err != nil {
		return nil, err
	}
	count := len(read.Sources)
	tables := make([][]byte, count)
	for i := 0; i < count; i++ {
		at := ymxr.GetLong(tuneFile, ymxr.IndexAt+4*i) &^ ymxr.Counted
		to := len(tuneFile)
		if i+1 < count {
			to = ymxr.GetLong(tuneFile, ymxr.IndexAt+4*(i+1)) &^ ymxr.Counted
		}
		tables[i] = tuneFile[at:to]
	}
	// The DTX1 tables first and the image after them, so that a source's
	// rows stand beside the header however long the image is: a player
	// whose ticks read a row through a displacement reaches 32,767 bytes
	// (68k/YMXR.S, YMXR_PCREL).
	here := ymxr.Align(BoundIndexAt + 4*count)
	sourceAt := make([]int, count)
	for i := 0; i < count; i++ {
		sourceAt[i] = here
		here = ymxr.Align(here + len(tables[i]))
	}
	imageAt := 0
	if image != nil {
		imageAt = here
		here = ymxr.Align(here + len(image))
	}
	bound := make([]byte, here)
	copy(bound, tuneFile[:ymxr.TableAt])
	copy(bound, BoundMagic)
	ymxr.PutWord(bound, 4, ymxr.GetWord(tuneFile, 4))
	ymxr.PutLong(bound, StateAt, state)
	ymxr.PutLong(bound, ImageAt, imageAt)
	// Where this tune's table stands in the image it is packaged into. An
	// image of one names it in its format block; one of several names the
	// first, so a tune past the first records a separate offset.
	ymxr.PutLong(bound, BoundTableAt, table)
	for i := 0; i < count; i++ {
		// A counted source's mark stands in the index a player reads, so
		// bit 31 stands in the binding beside the offset (SPEC.md 3.1).
		entry := sourceAt[i]
		if read.Counted[i] {
			entry |= ymxr.Counted
		}
		ymxr.PutLong(bound, BoundIndexAt+4*i, entry)
	}
	if image != nil {
		copy(bound[imageAt:], image)
	}
	for i := 0; i < count; i++ {
		copy(bound[sourceAt[i]:], tables[i])
	}
	return bound, nil
}
