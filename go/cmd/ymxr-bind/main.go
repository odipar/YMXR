// Command ymxr-bind reads a tune file on standard input and writes its
// bound tune on standard output, which is the layout the player reads. A
// tune file this does not bind, one of another version for one, is a line
// on standard error and an exit of 1.
package main

import "github.com/odipar/ymxr/go/internal/cli/ymxrbind"

func main() {
	ymxrbind.Main()
}
