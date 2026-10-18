// Command ymxr-prg reads an SNDH file on standard input and writes the
// program around it on standard output, playing -rROWS rows, or the tune's
// row count without. The program plays from the clock the file's tag
// names, Timer C or the VBL, and from the VBL where -vbl is passed
// (BINARIES.md 4.3). The stub clears the screen before its banner every
// run.
package main

import "github.com/odipar/ymxr/go/internal/cli/ymxrprg"

func main() {
	ymxrprg.Main()
}
