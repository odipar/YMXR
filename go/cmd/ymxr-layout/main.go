// Command ymxr-layout reads a file this repository writes on standard
// input and writes the record of doc/BINARIES.md 6 on standard output,
// one line of JSON a part.
//
// The four kinds are the multi file (BINARIES.md 0), the bound tune (1),
// the SNDH file (3) and the TOS program (4), and the record reports the
// parts of each: the tags and their values, the core's descriptor, the
// subtune table, each bound tune with its sources, the images and the
// workspace, and of a program the PRG header, the stub's descriptor and
// where the SNDH file begins.
package main

import (
	"fmt"
	"os"
	"strings"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/sndh"
)

func main() {
	t, _ := tool.Of("ymxr-layout", os.Args[1:])
	file := t.Bytes()
	said := report.Of(t.Reports())
	record, err := sndh.Layout(file)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	lines := strings.Count(record, "\n")
	if said.Says() {
		kind := record[strings.Index(record, ":\"")+2 : strings.Index(record, "\",\"bytes")]
		said.Say(fmt.Sprintf("the file: %d bytes, kind %s", len(file), kind))
		for _, part := range []string{"tag", "tune", "source", "image"} {
			of := strings.Count(record, "\"part\":\""+part+"\"")
			if of > 0 {
				name := "the " + part + "s"
				if of == 1 {
					name = "the " + part
				}
				said.Row(name, fmt.Sprintf("%d", of))
			}
		}
	}
	line := " lines"
	if lines == 1 {
		line = " line"
	}
	t.Report(fmt.Sprintf("%d%s", lines, line))
	t.WriteBytes([]byte(record))
}
