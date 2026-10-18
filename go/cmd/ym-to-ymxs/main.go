// Command ym-to-ymxs reads a YM5!/YM6! register dump on standard input and
// writes the YMXS structure as JSON on standard output.
//
// The first stage of every conversion here (doc/ymxs.md). What comes out
// is the structure ym-to-ymxr converts through, so ym-to-ymxs | ymxs-to-ymxr writes
// the tune file that tool writes.
//
// -r produces a tune that plays once, and -rROW one that repeats to that
// row; without either, a tune repeats to the frame the dump marks.
package main

import "github.com/odipar/ymxr/go/internal/cli/ymtoymxs"

func main() {
	ymtoymxs.Main()
}
