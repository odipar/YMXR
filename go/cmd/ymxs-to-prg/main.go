// Command ymxs-to-prg reads a YMXS structure as JSON on standard input and
// writes a TOS program on standard output.
//
// The SNDH file ymxs-to-sndh makes, with the program stub in front of it
// (doc/BINARIES.md 4): a program that claims the machine under Supexec,
// plays the tune from the clock the file's tag names, Timer C or the VBL,
// switches subtunes on the keys 1 to 9, and releases the machine on SPACE
// or ESC.
//
// -rROWS stops the run after that many rows, 0 to play on until a key
// stops it; every other flag is ymxs-to-sndh's.
package main

import "github.com/odipar/ymxr/go/internal/cli/ymxstoprg"

func main() {
	ymxstoprg.Main()
}
