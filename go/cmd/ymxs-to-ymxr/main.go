// Command ymxs-to-ymxr reads a YMXS structure as JSON on standard input
// and writes a tune file (SPEC.md 3.3) on standard output.
//
// The second stage of every conversion here (doc/ymxs.md). A tune file has
// one tune in it, so a multi of several writes a multi file
// (doc/BINARIES.md 0) instead: ymxr-sndh reads that as a set of subtunes,
// each named by its tune's title.
package main

import "github.com/odipar/ymxr/go/internal/cli/ymxstoymxr"

func main() {
	ymxstoymxr.Main()
}
