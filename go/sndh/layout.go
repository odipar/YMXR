package sndh

import (
	"bytes"
	"encoding/json"
	"fmt"
	"runtime"
	"sort"
	"strconv"
	"strings"

	"github.com/odipar/ymxr/go/ymxr"
)

// Layout is the record of BINARIES.md 6: a file this repository writes
// read back and reported part by part, one line of JSON a part. The
// reader here reads the file alone, as an implementer of the document
// does, so a record that differs from the reference is the document read
// two ways. A read past the file's end is the error of tools.md 9.7,
// where the Java tree catches the same read.
func Layout(file []byte) (record string, err error) {
	defer func() {
		if r := recover(); r != nil {
			wrong, ok := r.(runtime.Error)
			if !ok || !(strings.Contains(wrong.Error(), "index out of range") ||
				strings.Contains(wrong.Error(), "slice bounds out of range")) {
				panic(r)
			}
			record, err = "", fmt.Errorf("the record runs past the file's %d bytes",
				len(file))
		}
	}()
	kind, err := kindOf(file)
	if err != nil {
		return "", err
	}
	var out bytes.Buffer
	fmt.Fprintf(&out, "{\"kind\":\"%s\",\"bytes\":%d}\n", kind, len(file))
	switch kind {
	case "multi":
		layoutMulti(&out, file)
	case "bound":
		layoutBound(&out, file)
	case "program":
		layoutProgram(&out, file)
	default:
		layoutSndh(&out, file, 0, len(file))
	}
	return out.String(), nil
}

// kindOf is the kind of 6.1, read off the file's first bytes, the four in
// the order the clause lists them.
func kindOf(file []byte) (string, error) {
	if ascii(file, 0, 4) == "YMXM" {
		return "multi", nil
	}
	if ascii(file, 0, 4) == "YMXB" {
		return "bound", nil
	}
	if len(file) >= 2 && ymxr.GetWord(file, 0) == PrgMagic {
		return "program", nil
	}
	if ascii(file, 12, 4) == "SNDH" {
		return "sndh", nil
	}
	return "", fmt.Errorf("not a file BINARIES.md defines: no YMXM, YMXB, $601A or SNDH")
}

// layoutMulti is 6.2: the header, then a line a tune.
func layoutMulti(out *bytes.Buffer, file []byte) {
	tunes := ymxr.GetWord(file, 6)
	fmt.Fprintf(out, "{\"part\":\"header\",\"version\":%d,\"tunes\":%d}\n",
		ymxr.GetWord(file, 4), tunes)
	name := 8 + 8*tunes
	for i := 0; i < tunes; i++ {
		to := zeroFrom(file, name)
		fmt.Fprintf(out, "{\"part\":\"tune\",\"number\":%d,\"at\":%d,\"bytes\":%d,"+
			"\"name\":%s}\n", i+1, ymxr.GetLong(file, 8+8*i),
			ymxr.GetLong(file, 12+8*i), jsonText(string(file[name:to])))
		name = to + 1
	}
}

// layoutBound reports the bound header, sources and image, plus the
// transformation block in version 10 (BINARIES.md 6.3).
func layoutBound(out *bytes.Buffer, file []byte) {
	fmt.Fprintf(out, "{\"part\":\"header\"%s}\n", boundFields(file, 0))
	layoutSources(out, file, 0)
	block := 0
	if ymxr.GetWord(file, 4) == ymxr.Version {
		block = signedLong(file, BoundTransformationsAt)
	}
	if image := signedLong(file, 16); image > 0 {
		// The image ends at the transformation block when offset 24 is set;
		// otherwise it ends at the file's end.
		ends := len(file)
		if block > 0 {
			ends = block
		}
		fmt.Fprintf(out, "{\"part\":\"image\",\"at\":%d,\"bytes\":%d}\n",
			image, ends-image)
	}
	if block > 0 {
		fmt.Fprintf(out, "{\"part\":\"transformations\",\"at\":%d,\"count\":%d,"+
			"\"bytes\":%d}\n", block, ymxr.GetWord(file, block), len(file)-block)
	}
}

// layoutProgram is 6.5: the PRG header, the stub, where the SNDH file
// begins, and that file's lines. The SNDH file ends at 28 plus the long
// at 2, the relocation table after it.
func layoutProgram(out *bytes.Buffer, file []byte) {
	at := 28
	// the first SNDH on an even offset; a file cut before it ends the
	// search at its end, and the tags below read past it (tools.md 9.7)
	for at+16 <= len(file) && ascii(file, at+12, 4) != "SNDH" {
		at += 2
	}
	fmt.Fprintf(out, "{\"part\":\"prg\",\"text\":%d}\n", ymxr.GetLong(file, 2))
	version := ymxr.GetWord(file, 28+8)
	fmt.Fprintf(out, "{\"part\":\"stub\",\"at\":28,\"bytes\":%d,\"version\":%d,"+
		"\"subtunes\":%d,\"flags\":%d,\"rate\":%d,\"rows\":%d,\"core\":%d",
		at-28, version, ymxr.GetWord(file, 28+10),
		ymxr.GetWord(file, 28+12), ymxr.GetWord(file, 28+14),
		ymxr.GetLong(file, 28+16), at+ymxr.GetLong(file, 28+20))
	if version >= 2 {
		// the timer the stub arms (4.10), which a program of an earlier
		// version has no field for
		fmt.Fprintf(out, ",\"prescaler\":%d,\"count\":%d,\"ticks\":%d",
			ymxr.GetWord(file, 28+stubPrescalerAt), ymxr.GetWord(file, 28+stubCountAt),
			ymxr.GetWord(file, 28+stubTicksAt))
	}
	out.WriteString("}\n")
	fmt.Fprintf(out, "{\"part\":\"sndh\",\"at\":%d}\n", at)
	layoutSndh(out, file, at, 28+ymxr.GetLong(file, 2))
}

// layoutSndh is 6.4: the entry triple, the tags, the core, the subtune
// table, subtunes and their sources, the shared source tables, images,
// and the workspace. The SNDH file runs from `from` to `ends`, and every offset
// counts from the file's first byte.
func layoutSndh(out *bytes.Buffer, file []byte, from, ends int) {
	out.WriteString("{\"part\":\"entry\",\"to\":[")
	for i := 0; i < 3; i++ {
		if i > 0 {
			out.WriteString(",")
		}
		fmt.Fprintf(out, "%d", from+4*i+2+int(int16(ymxr.GetWord(file, from+4*i+2))))
	}
	out.WriteString("]}\n")
	hdns := layoutTags(out, file, from)
	core := CoreAt(file, even(hdns+4))
	table := core + ymxr.GetLong(file, core+28)
	work := core + ymxr.GetLong(file, core+32)
	fmt.Fprintf(out, "{\"part\":\"core\",\"at\":%d,\"version\":%d,\"binds\":%d,"+
		"\"fixed\":%d,\"flags\":%d,\"state\":%d,\"subtunetable\":%d,\"work\":%d",
		core, ymxr.GetWord(file, core+16), ymxr.GetWord(file, core+18),
		ymxr.GetWord(file, core+20), ymxr.GetWord(file, core+22),
		core+ymxr.GetWord(file, core+24), table, work)
	// with the lookup in, the page area's offset at 36, and the area to the
	// subtune table (BINARIES.md 2.2, 3.1)
	mapped := ymxr.GetWord(file, core+22)&coreMap != 0
	pages := 0
	if mapped {
		pages = ymxr.GetLong(file, core+CorePagesAt)
		at := 0
		if pages != 0 {
			at = core + pages
		}
		fmt.Fprintf(out, ",\"pages\":%d", at)
	}
	out.WriteString("}\n")
	if pages != 0 {
		fmt.Fprintf(out, "{\"part\":\"pages\",\"at\":%d,\"bytes\":%d}\n", core+pages,
			table-core-pages)
	}
	n := ymxr.GetWord(file, table)
	tunes := make([]int, n)
	for i := 0; i < n; i++ {
		tunes[i] = core + ymxr.GetLong(file, table+2+4*i)
	}
	fmt.Fprintf(out, "{\"part\":\"subtunes\",\"at\":%d,\"tunes\":[", table)
	for i, at := range tunes {
		if i > 0 {
			out.WriteString(",")
		}
		fmt.Fprintf(out, "%d", at)
	}
	out.WriteString("]}\n")
	var images []int
	for _, at := range tunes {
		image := at + signedLong(file, at+16)
		if !has(images, image) {
			images = append(images, image)
		}
	}
	sort.Ints(images)
	poolAt := images[0]
	for i := 0; i+1 < n; i++ {
		at := tunes[i]
		index := at + BoundIndex(ymxr.GetWord(file, at+4))
		for j := 0; j < int(file[at+9]); j++ {
			source := at + (ymxr.GetLong(file, index+4*j) &^ ymxr.Counted)
			if source >= tunes[i+1] && source < poolAt {
				poolAt = source
			}
		}
	}
	for i, at := range tunes {
		upto := poolAt
		if i+1 < n {
			upto = tunes[i+1]
		}
		fmt.Fprintf(out, "{\"part\":\"tune\",\"number\":%d,\"at\":%d,\"bytes\":%d%s}\n",
			i+1, at, upto-at, boundFields(file, at))
		layoutSources(out, file, at)
	}
	if poolAt < images[0] {
		fmt.Fprintf(out, "{\"part\":\"sourcepool\",\"at\":%d,\"bytes\":%d}\n",
			poolAt, images[0]-poolAt)
	}
	for i, at := range images {
		fmt.Fprintf(out, "{\"part\":\"image\",\"number\":%d,\"at\":%d}\n", i+1, at)
	}
	fmt.Fprintf(out, "{\"part\":\"workspace\",\"at\":%d,\"bytes\":%d}\n", work, ends-work)
}

// layoutTags is the tags of 3.2, a line each from 16 to HDNS, and where
// HDNS is.
func layoutTags(out *bytes.Buffer, file []byte, from int) int {
	at := from + 16
	subtunes := 0
	for {
		if file[at] == 0 {
			at++
			continue
		}
		name := ascii(file, at, 4)
		switch {
		case name == "HDNS":
			fmt.Fprintf(out, "{\"part\":\"tag\",\"name\":\"HDNS\",\"at\":%d}\n", at)
			return at
		case name[:2] == "##":
			subtunes, _ = strconv.Atoi(name[2:])
			fmt.Fprintf(out, "{\"part\":\"tag\",\"name\":\"##\",\"at\":%d,"+
				"\"count\":%d}\n", at, subtunes)
			at += 5
		case name[:2] == ClockTimerC || name[:2] == ClockVBL:
			to := zeroFrom(file, at+2)
			rate := 0
			for i := at + 2; i < to && file[i] >= '0' && file[i] <= '9'; i++ {
				rate = rate*10 + int(file[i]-'0')
			}
			fmt.Fprintf(out, "{\"part\":\"tag\",\"name\":\"%s\",\"at\":%d,"+
				"\"rate\":%d}\n", name[:2], at, rate)
			at = to + 1
		case name == "FRMS":
			fmt.Fprintf(out, "{\"part\":\"tag\",\"name\":\"FRMS\",\"at\":%d,"+
				"\"frames\":[", at)
			for i := 0; i < subtunes; i++ {
				if i > 0 {
					out.WriteString(",")
				}
				fmt.Fprintf(out, "%d", uint32(ymxr.GetLong(file, at+4+4*i)))
			}
			out.WriteString("]}\n")
			at += 4 + 4*subtunes
		case name == "TIME":
			fmt.Fprintf(out, "{\"part\":\"tag\",\"name\":\"TIME\",\"at\":%d,"+
				"\"seconds\":[", at)
			for i := 0; i < subtunes; i++ {
				if i > 0 {
					out.WriteString(",")
				}
				fmt.Fprintf(out, "%d", ymxr.GetWord(file, at+4+2*i))
			}
			out.WriteString("]}\n")
			at += 4 + 2*subtunes
		case name == "!#SN":
			fmt.Fprintf(out, "{\"part\":\"tag\",\"name\":\"!#SN\",\"at\":%d,"+
				"\"names\":[", at)
			named := at + 4 + 2*subtunes
			for i := 0; i < subtunes; i++ {
				if i > 0 {
					out.WriteString(",")
				}
				to := zeroFrom(file, named)
				out.WriteString(jsonText(string(file[named:to])))
				named = to + 1
			}
			out.WriteString("]}\n")
			at = named
		default:
			to := zeroFrom(file, at+4)
			fmt.Fprintf(out, "{\"part\":\"tag\",\"name\":\"%s\",\"at\":%d,"+
				"\"text\":%s}\n", name, at, jsonText(string(file[at+4:to])))
			at = to + 1
		}
	}
}

// layoutSources is a line a source of the bound tune at `at`, in index
// order: the index at 24, and at 28 in version 10, each entry's bit 31
// the mark of a counted source and outside the offset.
func layoutSources(out *bytes.Buffer, file []byte, at int) {
	index := at + BoundIndex(ymxr.GetWord(file, at+4))
	for i := 0; i < int(file[at+9]); i++ {
		fmt.Fprintf(out, "{\"part\":\"source\",\"number\":%d,\"at\":%d}\n",
			i+1, at+ymxr.GetLong(file, index+4*i)&^ymxr.Counted)
	}
}

// boundFields reports the bound fields at the supplied offset,
// resolving the image and optional version 10 transformation block
// (BINARIES.md 1.2).
func boundFields(file []byte, at int) string {
	fields := fmt.Sprintf(",\"version\":%d,\"rate\":%d,\"effects\":%d,\"sources\":%d,"+
		"\"stateblock\":%d,\"image\":%d,\"table\":%d",
		ymxr.GetWord(file, at+4), ymxr.GetWord(file, at+6), file[at+8], file[at+9],
		ymxr.GetLong(file, at+12), at+signedLong(file, at+16),
		ymxr.GetLong(file, at+20))
	if ymxr.GetWord(file, at+4) == ymxr.Version {
		fields += fmt.Sprintf(",\"transformations\":%d",
			at+signedLong(file, at+BoundTransformationsAt))
	}
	return fields
}

// signedLong is the long at `at` as a signed number, as BINARIES.md 1.2
// defines the fields at 16 and 24.
func signedLong(b []byte, at int) int {
	return int(int32(uint32(ymxr.GetLong(b, at))))
}

// zeroFrom is where the next zero byte from `from` is.
func zeroFrom(file []byte, from int) int {
	at := from
	for file[at] != 0 {
		at++
	}
	return at
}

// jsonText is JSON text: the quotes, the escapes JSON defines, and a
// character above $7E escaped as \uXXXX.
func jsonText(said string) string {
	out, err := json.Marshal(said)
	if err != nil {
		return "\"\""
	}
	var escaped bytes.Buffer
	for _, r := range string(out) {
		if r > 0x7E {
			fmt.Fprintf(&escaped, "\\u%04x", r)
		} else {
			escaped.WriteRune(r)
		}
	}
	return escaped.String()
}

func ascii(file []byte, at, bytes int) string {
	if at < 0 || at+bytes > len(file) {
		return ""
	}
	return string(file[at : at+bytes])
}

func has(of []int, one int) bool {
	for _, each := range of {
		if each == one {
			return true
		}
	}
	return false
}
