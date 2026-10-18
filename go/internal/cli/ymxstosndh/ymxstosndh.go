// Package ymxstosndh is the tool ymxs-to-sndh: cmd/ymxs-to-sndh runs it as an executable,
// and the WebAssembly module (wasm/) runs it by name.
package ymxstosndh

import (
	"os"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
)

func Main() {
	t, args := tool.Of("ymxs-to-sndh", os.Args[1:], flags.PackingFlags...)
	flags.Only(t, args, flags.PackingFlags, flags.Tags)
	said := report.Of(t.Reports())
	multi := flags.Read(t)
	t.WriteBytes(flags.SndhOf(t, multi, args, said))
	for _, note := range said.Unsaid() {
		t.Note(note)
	}
}
