// Command ymxr-trace reads a tune file on standard input and writes its
// record on standard output, the first line and one line a row, -rROWS
// rows or one pass and the loop once without.
package main

import "github.com/odipar/ymxr/go/internal/cli/ymxrtrace"

func main() {
	ymxrtrace.Main()
}
