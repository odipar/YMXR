// Command ymxr-multi writes the tune files named as one multi file on
// standard output. Each tune is named by its file unless -nNAME names it,
// the names in the order the files are named.
package main

import "github.com/odipar/ymxr/go/internal/cli/ymxrmulti"

func main() {
	ymxrmulti.Main()
}
