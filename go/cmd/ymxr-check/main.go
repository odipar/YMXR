// Command ymxr-check reads a YM5!/YM6! dump on standard input and writes
// one line on standard output saying whether the tune it converts to
// replays to that dump, and the wrong frames under it where it does not.
// The flags are the converter's, and an exit of 1 says a dump does not
// replay.
//
// A corpus is read by naming files and directories instead: ymxr-check
// corpus/ reads every .ym under it, one line a file and a count at the
// end.
package main

import (
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/check"
	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/ym"
)

// result is one line of the tool's report on a file: no dump where the
// file is not a YM3!/YM3b/YM5!/YM6! dump, and otherwise an empty list, or the
// faults.
type result struct {
	file  string
	dump  bool
	wrong []string
}

func main() {
	t, args := tool.Of("ymxr-check", os.Args[1:], "-k", "-m", "-r", "-copies")
	var reads, named []string
	for _, arg := range args {
		if strings.HasPrefix(arg, "-") {
			reads = append(reads, arg)
		} else {
			named = append(named, arg)
		}
	}
	flags.Numbers(t, reads)
	said := report.Of(t.Reports())
	if len(named) == 0 {
		one := of(t.Bytes(), "standard input", reads)
		says(one)
		if one.dump && len(one.wrong) == 0 {
			os.Exit(tool.Done)
		}
		os.Exit(tool.Wrong)
	}
	files, err := dumps(named)
	if err != nil {
		t.Wrong(tool.Failed, err.Error())
	}
	at := ""
	if len(reads) > 0 {
		at = ", at " + strings.Join(reads, " ")
	}
	file := " files"
	if len(files) == 1 {
		file = " file"
	}
	said.Say(fmt.Sprintf("%d%s to read%s", len(files), file, at))
	dumped := 0
	failed := 0
	for i, name := range files {
		data, err := os.ReadFile(name)
		one := result{file: name, dump: true, wrong: []string{"unreadable: " + name}}
		if err == nil {
			one = of(data, name, reads)
		}
		said.Progress("read", i+1, len(files))
		if one.dump {
			dumped++
			if len(one.wrong) > 0 {
				failed++
			}
		}
		says(one)
	}
	others := len(files) - dumped
	dumps := " dumps, "
	if dumped == 1 {
		dumps = " dump, "
	}
	not := ""
	if others > 0 {
		file := " files"
		if others == 1 {
			file = " file"
		}
		not = fmt.Sprintf(", %d%s not a dump", others, file)
	}
	fmt.Printf("%d%s%d wrong%s\n", dumped, dumps, failed, not)
	if failed == 0 {
		os.Exit(tool.Done)
	}
	os.Exit(tool.Wrong)
}

// of is the dump in the data, under the name it is reported by.
func of(data []byte, name string, reads []string) result {
	song, err := ym.Read(data)
	if err != nil {
		if !ym.IsDump(data) {
			return result{file: name, dump: false}
		}
		return result{file: name, dump: true,
			wrong: []string{"the converter refuses it: " + err.Error()}}
	}
	return result{file: name, dump: true, wrong: check.Of(song, reads)}
}

// says puts one file's verdict on standard output, which the tool
// is for.
func says(one result) {
	name := filepath.Base(one.file)
	switch {
	case !one.dump:
		fmt.Println(name + ": not a YM3!/YM3b/YM5!/YM6! dump")
	case len(one.wrong) == 0:
		fmt.Println(name + ": replays to its dump")
	default:
		fmt.Println(name + ":")
		for _, line := range one.wrong {
			fmt.Println("  " + line)
		}
	}
}

// dumps is the dumps named, and every .ym under a directory named.
func dumps(named []string) ([]string, error) {
	var out []string
	for _, name := range named {
		at, err := os.Stat(name)
		if err != nil {
			return nil, err
		}
		if !at.IsDir() {
			out = append(out, name)
			continue
		}
		read, err := os.ReadDir(name)
		if err != nil {
			return nil, err
		}
		var under []string
		for _, one := range read {
			if strings.HasSuffix(strings.ToLower(one.Name()), ".ym") {
				under = append(under, filepath.Join(name, one.Name()))
			}
		}
		sort.Strings(under)
		out = append(out, under...)
	}
	return out, nil
}
