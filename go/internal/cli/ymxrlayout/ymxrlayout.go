// Package ymxrlayout is the tool ymxr-layout: cmd/ymxr-layout runs it as an executable,
// and the WebAssembly module (wasm/) runs it by name.
package ymxrlayout

import (
	"fmt"
	"os"
	"strings"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/sndh"
)

func Main() {
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
		said.Say(fmt.Sprintf("the file: %s, kind %s", report.Count(len(file), "byte", "bytes"),
			kind))
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
	t.Report(report.Count(lines, "line", "lines"))
	t.WriteBytes([]byte(record))
}
