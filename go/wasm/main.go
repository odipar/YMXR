// Command wasm is the twelve tools in one program, for WebAssembly: its
// first argument names the tool, and the arguments after it are that
// tool's. Built with GOOS=wasip1 GOARCH=wasm it is ymxr.wasm, a WASI
// command module that reads standard input, writes standard output and
// standard error, and exits as the tool does (doc/tools.md 20).
//
//	ymxr.wasm ymxs-to-sndh -silent < tune.json > tune.sndh
package main

import (
	"fmt"
	"os"
	"sort"
	"strings"

	"github.com/odipar/ymxr/go/internal/cli/ymtoymxr"
	"github.com/odipar/ymxr/go/internal/cli/ymtoymxs"
	"github.com/odipar/ymxr/go/internal/cli/ymxrbind"
	"github.com/odipar/ymxr/go/internal/cli/ymxrcheck"
	"github.com/odipar/ymxr/go/internal/cli/ymxrlayout"
	"github.com/odipar/ymxr/go/internal/cli/ymxrmulti"
	"github.com/odipar/ymxr/go/internal/cli/ymxrprg"
	"github.com/odipar/ymxr/go/internal/cli/ymxrsndh"
	"github.com/odipar/ymxr/go/internal/cli/ymxrtrace"
	"github.com/odipar/ymxr/go/internal/cli/ymxstoprg"
	"github.com/odipar/ymxr/go/internal/cli/ymxstosndh"
	"github.com/odipar/ymxr/go/internal/cli/ymxstoymxr"
)

// tools is each tool by its name.
var tools = map[string]func(){
	"ym-to-ymxr":   ymtoymxr.Main,
	"ym-to-ymxs":   ymtoymxs.Main,
	"ymxr-bind":    ymxrbind.Main,
	"ymxr-check":   ymxrcheck.Main,
	"ymxr-layout":  ymxrlayout.Main,
	"ymxr-multi":   ymxrmulti.Main,
	"ymxr-prg":     ymxrprg.Main,
	"ymxr-sndh":    ymxrsndh.Main,
	"ymxr-trace":   ymxrtrace.Main,
	"ymxs-to-prg":  ymxstoprg.Main,
	"ymxs-to-sndh": ymxstosndh.Main,
	"ymxs-to-ymxr": ymxstoymxr.Main,
}

func main() {
	if len(os.Args) < 2 || tools[os.Args[1]] == nil {
		names := make([]string, 0, len(tools))
		for name := range tools {
			names = append(names, name)
		}
		sort.Strings(names)
		fmt.Fprintln(os.Stderr, "ymxr: the first argument names a tool: "+
			strings.Join(names, ", "))
		os.Exit(2)
	}
	// each tool reads its flags from os.Args[1:], so the tool's name is
	// its os.Args[0], as it is for the executable
	os.Args = os.Args[1:]
	tools[os.Args[0]]()
}
