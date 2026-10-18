// Command ym-to-ymxr reads a YM5!/YM6! dump on standard input and writes a
// tune file on standard output.
//
// Every conversion passes through the structure (doc/ymxs.md): the dump's
// frames become a YMXS tune, and the schema maps that onto the columns, so
// this writes the bytes ym-to-ymxs | ymxs-to-ymxr writes.
package main

import "github.com/odipar/ymxr/go/internal/cli/ymtoymxr"

func main() {
	ymtoymxr.Main()
}
