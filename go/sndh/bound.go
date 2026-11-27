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
//	               index and transformations, byte for byte, then the list
//	               of the pages the tune maps through (Pages)
//
// Every offset counts from the first byte.

// BoundMagic is the four bytes a bound tune opens with.
var BoundMagic = []byte{'Y', 'M', 'X', 'B'}

// Bound field offsets: BoundIndexAt is the source index in versions 7
// to 9; versions 10 and 11 replace that position with
// BoundTransformationsAt, the transformation-block pointer, and version
// 11 the next with BoundCutsAt, the cut-index pointer.
const (
	StateAt                = 12
	ImageAt                = 16
	BoundTableAt           = 20
	BoundIndexAt           = 24
	BoundTransformationsAt = 24
	BoundCutsAt            = 28
)

// SyncAt is the byte of the bound tune whose bit 7, SyncMark, marks a tune
// file of version 13, 14 or 15, a tune that syncs a voice, and bit 6,
// LengthMark, one of version 15, whose table has the sync length columns:
// byte 10, the high byte of the name's offset, whose bits 7 and 6 are 0 in
// a tune file, the name being within 16,384 bytes of the start (BINARIES.md
// 1.2).
const (
	SyncAt     = 10
	SyncMark   = 0x80
	LengthMark = 0x40
)

// BoundIndex returns the source-index offset: 24 in versions 7 to 9; 28
// in version 10, after the transformation-block pointer; 32 in version 11,
// after the cut-index pointer.
func BoundIndex(version int) int {
	switch version {
	case ymxr.VersionCut:
		return BoundCutsAt + 4
	case ymxr.Version:
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

	sources sourcePool
}

type sourcePool struct {
	tables  [][]byte
	offsets [][]int
	bytes   int
}

// TuneBytes is the number of bytes placed for tune i before any shared
// source tables.
func (set Set) TuneBytes(i int) int {
	tune := set.Tunes[i]
	if set.sources.bytes == 0 {
		return len(tune)
	}
	// with the sources pooled a subtune keeps its header, its index and,
	// in version 11, the cut index after the index (BINARIES.md 1.3)
	if ymxr.GetWord(tune, 4) == ymxr.VersionCut {
		cuts := ymxr.GetLong(tune, BoundCutsAt)
		return cuts + 2 + 8*ymxr.GetWord(tune, cuts)
	}
	return BoundIndex(ymxr.GetWord(tune, 4)) + 4*len(set.sources.offsets[i])
}

// SharedSourceBytes is the size of the shared source tables, including
// padding between tables.
func (set Set) SharedSourceBytes() int {
	return set.sources.bytes
}

// poolSources places whole DTX1 tables in first-use order when at least
// two tunes share a table. The counted bit belongs to the index entry.
func poolSources(tunes [][]byte) sourcePool {
	if len(tunes) < 2 {
		return sourcePool{}
	}
	type location struct {
		tune, offset int
	}
	seen := make(map[string]location)
	pool := sourcePool{offsets: make([][]int, len(tunes))}
	shared := false
	for i, tune := range tunes {
		index := BoundIndex(ymxr.GetWord(tune, 4))
		count := int(tune[ymxr.CountAt])
		pool.offsets[i] = make([]int, count)
		for j := 0; j < count; j++ {
			at := ymxr.GetLong(tune, index+4*j) &^ ymxr.Counted
			end := len(tune)
			if j+1 < count {
				end = ymxr.GetLong(tune, index+4*(j+1)) &^ ymxr.Counted
			}
			table := tune[at:end]
			where, found := seen[string(table)]
			if !found {
				where = location{tune: i, offset: pool.bytes}
				seen[string(table)] = where
				pool.tables = append(pool.tables, table)
				pool.bytes = ymxr.Align(pool.bytes + len(table))
			}
			shared = shared || where.tune != i
			pool.offsets[i][j] = where.offset
		}
	}
	if !shared {
		return sourcePool{}
	}
	return pool
}

// readable is the tune file read, as every binder reads it.
func readable(tuneFile []byte) (ymxr.File, error) {
	return ymxr.Read(tuneFile)
}

// BoundVersion is the version a bound tune of the tune file records, the layout of
// its header and index (BINARIES.md 1.2): the tune file's, and for a tune
// file of version 12 to 15 the version of its columns, 11 where it cuts a
// source, 10 where it maps a value and cuts none, and 9 otherwise. Bit 7 of
// its effects byte marks copy from frame, which the index leaves out: copy
// from frame is source S, the tables plus 1 (SPEC.md 3.1.7). Bits 7 and 6
// of byte 10 mark a tune file of versions 13 to 15 and of version 15
// (SyncAt).
func BoundVersion(tune ymxr.File) int {
	switch {
	case tune.Version < ymxr.VersionCopy:
		return tune.Version
	case ymxr.HasCuts(tune.Effects):
		return ymxr.VersionCut
	case ymxr.HasMapping(tune.Effects):
		return ymxr.Version
	}
	return ymxr.Unmapped
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
		read, err := readable(file)
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
		Blocks: blocks, sources: poolSources(tunes)}, nil
}

// shape is the values an image fixes once, as a key two tables are
// grouped by and as a reader of the report sees it: the variant, the width,
// the unit, and the column count of a table of 34 (DTX abi.md 1). Under
// DTX3 each table defines its ring, and the image has the copy code where a
// table of it has copies, so neither separates two tables. The period
// follows C at the writer's unit, so tables of 30, 34 and 38 columns go into
// separate images, and the key of a table of 30 is the key of version 9.
func shape(dtx3 []byte) (string, error) {
	header, err := dtx.ReadHeader(dtx3)
	if err != nil {
		return "", err
	}
	payload := header.Length()
	columns := ""
	if header.Columns != ymxr.C {
		columns = fmt.Sprintf(", %d columns", header.Columns)
	}
	return fmt.Sprintf("DTX%d at unit %d%s, values of %d", header.Variant,
		int(dtx3[payload+2]), columns, header.Width), nil
}

// Bound binds a tune file: header, recomputed source index, unchanged
// DTX1 tables, packaged DTX3 image and, in version 10, transformation
// block (doc/BINARIES.md 1).
func Bound(tuneFile []byte) ([]byte, error) {
	read, err := readable(tuneFile)
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

// Block is the bound tune's transformation block of a version 10 tune
// file, or nil for versions 7 to 9: the tune file's block, copied
// unchanged, then the list of the pages the tune maps through (Pages,
// BINARIES.md 1.2). The tune file's block begins at the header's long at
// 16 and ends after its index or last entry, whichever is later. Index
// offsets are relative to the block, so relocation preserves them. The
// reader has already checked bounds (SPEC.md 3.4.2).
func Block(tuneFile []byte) []byte {
	if !ymxr.HasMapping(int(tuneFile[ymxr.EffectsAt])) {
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
	block := append([]byte{}, tuneFile[at:end]...)
	if read, err := ymxr.Read(tuneFile); err == nil {
		block = append(block, Pages(read)...)
	}
	return block
}

// Transformations is the transformation blocks of a set laid out from an
// offset on a long (BINARIES.md 3.1): the index of each distinct block, then
// each distinct transformation once, and each block's list right after the
// last transformation the block indexes first, or after a copy of its
// shortest where an earlier block indexes each of its transformations. A
// block's list is then past the furthest end of its index and of each
// transformation it indexes, where init reads it (1.1, 1.5), and a frame
// reads a transformation through its index entry at any offset. Returns the
// bytes and the offset of each tune's block in them, -1 for a tune without
// one.
func Transformations(blocks [][]byte) ([]byte, []int) {
	type parsed struct {
		entries [][]byte
		list    []byte
	}
	var distinct []parsed
	known := make(map[string]int)
	which := make([]int, len(blocks))
	for i, block := range blocks {
		which[i] = -1
		if len(block) == 0 {
			continue
		}
		d, found := known[string(block)]
		if !found {
			count := ymxr.GetWord(block, 0)
			one := parsed{entries: make([][]byte, count)}
			end := 2 + 4*count
			for n := 0; n < count; n++ {
				p := ymxr.GetLong(block, 2+4*n)
				one.entries[n] = block[p : p+2+ymxr.GetWord(block, p)]
				end = max(end, p+len(one.entries[n]))
			}
			one.list = block[end:]
			d = len(distinct)
			known[string(block)] = d
			distinct = append(distinct, one)
		}
		which[i] = d
	}
	size := 0
	index := make([]int, len(distinct))
	for d, one := range distinct {
		size = ymxr.Align(size)
		index[d] = size
		size += 2 + 4*len(one.entries)
	}
	placed := make(map[string]int)
	where := make([][]int, len(distinct))
	lists := make([]int, len(distinct))
	for d, one := range distinct {
		where[d] = make([]int, len(one.entries))
		added := false
		for n, entry := range one.entries {
			if p, found := placed[string(entry)]; found {
				where[d][n] = p
				continue
			}
			size = ymxr.Align(size)
			placed[string(entry)] = size
			where[d][n] = size
			size += len(entry)
			added = true
		}
		if !added {
			shortest := 0
			for n, entry := range one.entries {
				if len(entry) < len(one.entries[shortest]) {
					shortest = n
				}
			}
			size = ymxr.Align(size)
			where[d][shortest] = size
			size += len(one.entries[shortest])
		}
		lists[d] = size
		size += len(one.list)
	}
	out := make([]byte, size)
	for d, one := range distinct {
		ymxr.PutWord(out, index[d], len(one.entries))
		for n, entry := range one.entries {
			ymxr.PutLong(out, index[d]+2+4*n, where[d][n]-index[d])
			copy(out[where[d][n]:], entry)
		}
		copy(out[lists[d]:], one.list)
	}
	at := make([]int, len(blocks))
	for i, d := range which {
		at[i] = -1
		if d >= 0 {
			at[i] = index[d]
		}
	}
	return out, at
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
	version := BoundVersion(read)
	// the tables; copy from frame, source S, is the count plus 1
	count := len(read.Sources)
	from := ymxr.IndexAt(read.Version)
	index := BoundIndex(version)
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
	// pages. The cut index follows the source index, where a subtune whose
	// sources an SNDH file pools keeps it (BINARIES.md 1.3).
	here := ymxr.Align(index + 4*count)
	var cuts []byte
	cutsAt := 0
	if version == ymxr.VersionCut {
		at := ymxr.GetLong(tuneFile, ymxr.CutsAt)
		cuts = tuneFile[at : at+2+8*ymxr.GetWord(tuneFile, at)]
		cutsAt = here
		here = ymxr.Align(here + len(cuts))
	}
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
	ymxr.PutWord(bound, 4, version)
	if read.Version >= ymxr.VersionSync {
		bound[SyncAt] |= SyncMark
	}
	if read.Version == ymxr.VersionLength {
		bound[SyncAt] |= LengthMark
	}
	bound[ymxr.CountAt] = byte(count)
	ymxr.PutLong(bound, StateAt, state)
	ymxr.PutLong(bound, ImageAt, imageAt)
	// Where this tune's table is in the image it is packaged into. An
	// image of one names it in its format block; one of several names the
	// first, so a tune past the first records a separate offset.
	ymxr.PutLong(bound, BoundTableAt, table)
	if block != nil {
		ymxr.PutLong(bound, BoundTransformationsAt, blockAt)
	}
	if version == ymxr.VersionCut {
		ymxr.PutLong(bound, BoundCutsAt, cutsAt)
		copy(bound[cutsAt:], cuts)
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
