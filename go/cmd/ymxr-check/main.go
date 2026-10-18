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

import (
	"bytes"
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
	"github.com/odipar/ymxr/go/ymxr"
)

// kind marks a file as a dump, a tune file, or neither.
type kind int

const (
	dump kind = iota
	tune
	neither
)

// result is one line of the tool's report on a file: its kind, and an
// empty list, or the faults.
type result struct {
	file  string
	kind  kind
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
		if one.kind != neither && len(one.wrong) == 0 {
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
	said.Say(report.Count(len(files), "file", "files") + " to read" + at)
	dumped, failed, tunes, placed := 0, 0, 0, 0
	for i, name := range files {
		data, err := os.ReadFile(name)
		one := result{file: name, kind: dump, wrong: []string{"unreadable: " + name}}
		if err == nil {
			one = of(data, name, reads)
		}
		said.Progress("read", i+1, len(files))
		switch one.kind {
		case dump:
			dumped++
			if len(one.wrong) > 0 {
				failed++
			}
		case tune:
			tunes++
			if len(one.wrong) > 0 {
				placed++
			}
		}
		says(one)
	}
	read := ""
	if tunes > 0 {
		read = fmt.Sprintf(", %s, %d wrong", report.Count(tunes, "tune file", "tune files"),
			placed)
	}
	if others := len(files) - dumped - tunes; others > 0 {
		read += ", " + report.Count(others, "file", "files") + " neither a dump nor a tune file"
	}
	fmt.Printf("%s, %d wrong%s\n", report.Count(dumped, "dump", "dumps"), failed, read)
	if failed == 0 && placed == 0 {
		os.Exit(tool.Done)
	}
	os.Exit(tool.Wrong)
}

// of is the dump or the tune file in the data, under the name it is
// reported by.
func of(data []byte, name string, reads []string) result {
	if bytes.HasPrefix(data, ymxr.Magic) {
		file, err := ymxr.Read(data)
		if err != nil {
			return result{file: name, kind: tune,
				wrong: []string{"the tune file does not read: " + err.Error()}}
		}
		return result{file: name, kind: tune, wrong: check.Places(file)}
	}
	song, err := ym.Read(data)
	if err != nil {
		if !ym.IsDump(data) {
			return result{file: name, kind: neither}
		}
		return result{file: name, kind: dump,
			wrong: []string{"the converter fails on it: " + err.Error()}}
	}
	return result{file: name, kind: dump, wrong: check.Of(song, reads)}
}

// says puts one file's verdict on standard output, which the tool
// is for.
func says(one result) {
	name := filepath.Base(one.file)
	switch {
	case one.kind == neither:
		fmt.Println(name + ": neither a YM3!/YM3b/YM5!/YM6! dump nor a tune file")
	case len(one.wrong) == 0 && one.kind == dump:
		fmt.Println(name + ": replays to its dump")
	case len(one.wrong) == 0:
		fmt.Println(name + ": every start follows rule 3")
	default:
		fmt.Println(name + ":")
		for _, line := range one.wrong {
			fmt.Println("  " + line)
		}
	}
}

// dumps is the files named, and every .ym and .ymxr under a directory
// named.
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
			lower := strings.ToLower(one.Name())
			if strings.HasSuffix(lower, ".ym") || strings.HasSuffix(lower, ".ymxr") {
				under = append(under, filepath.Join(name, one.Name()))
			}
		}
		sort.Strings(under)
		out = append(out, under...)
	}
	return out, nil
}
