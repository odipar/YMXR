// Command ymxr-multi writes the tune files named as one multi file on
// standard output. Each tune is named by its file unless -nNAME names it,
// the names in the order the files are named.
package main

import (
	"fmt"
	"os"
	"strings"

	"github.com/odipar/ymxs/tool"

	"github.com/odipar/ymxr/ymxr"
)

func main() {
	t, args := tool.Of("ymxr-multi", os.Args[1:], "-n")
	var named, files []string
	for _, flag := range args {
		switch {
		case strings.HasPrefix(flag, "-n"):
			named = append(named, flag[2:])
		case strings.HasPrefix(flag, "-"):
			t.Usage("not a flag of the tool: " + flag)
		default:
			files = append(files, flag)
		}
	}
	if len(files) == 0 {
		t.Usage("ymxr-multi tune.ymxr [more.ymxr ...] [-nNAME]...")
	}
	if len(named) > len(files) {
		t.Usage(fmt.Sprintf("%d names for %d tune files", len(named), len(files)))
	}
	var tunes [][]byte
	var names []string
	for i, name := range files {
		file, err := os.ReadFile(name)
		if err != nil {
			t.Wrong(tool.Failed, "cannot read "+name+": "+err.Error())
		}
		tunes = append(tunes, file)
		if i < len(named) {
			names = append(names, named[i])
		} else {
			names = append(names, ymxr.Stem(name))
		}
	}
	file, err := ymxr.Multi(tunes, names)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	for i, tune := range tunes {
		t.Report(fmt.Sprintf("%s: %d bytes", names[i], len(tune)))
	}
	tuned := " tunes, "
	if len(tunes) == 1 {
		tuned = " tune, "
	}
	t.Report(fmt.Sprintf("%d%s%d bytes", len(tunes), tuned, len(file)))
	t.WriteBytes(file)
}
