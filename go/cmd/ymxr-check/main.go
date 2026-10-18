// Command ymxr-check reads a YM5!/YM6! dump on standard input and writes
// one line on standard output saying whether the tune it converts to
// replays to that dump, and the wrong frames under it where it does not.
// The flags are the converter's, and an exit of 1 says a dump does not
// replay. A tune file on standard input is read against rule 3 of SPEC.md
// 6 (check.Places), and exit 1 marks one with a start outside it.
//
// A corpus is read by naming files and directories instead: ymxr-check
// corpus/ reads every .ym and .ymxr under it, one line a file and a count
// at the end.
package main

import "github.com/odipar/ymxr/go/internal/cli/ymxrcheck"

func main() {
	ymxrcheck.Main()
}
