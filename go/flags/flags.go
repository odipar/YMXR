// Package flags is shared by the tools that read a YMXS file: the flags
// they read, the structure read off standard input, and the tune files it
// maps to (doc/ymxs.md).
//
// A YMXS multi of several tunes is a set of subtunes: one tune file each,
// which the sndh package puts behind one core.
package flags

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/odipar/dtx/go/dtx"
	"github.com/odipar/dtx/go/st4"
	"github.com/odipar/ymxs/go/check"
	"github.com/odipar/ymxs/go/text"
	"github.com/odipar/ymxs/go/tool"
	"github.com/odipar/ymxs/go/ymxs"

	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/schema"
	"github.com/odipar/ymxr/go/sndh"
	"github.com/odipar/ymxr/go/ymxr"
)

// Packing is the packer's three settings: the unit, the ring, and the
// search for
// a better parse.
type Packing struct {
	Unit    int
	Ring    int
	Copies  bool
	Seconds float64
}

// Unit is the unit the table packs at by default.
const Unit = 2

// PackingFlags is the packer's flags.
var PackingFlags = []string{"-k", "-m", "-copies"}

// Tags is the tags ymxs-to-sndh reads, the two cores it selects between,
// and the clock asked for.
var Tags = []string{"-t", "-c", "-perf", "-lean", "-pcrel", "-abs", "-vbl", "-tc"}

// Rows is the row count a program stops after.
var Rows = []string{"-r"}

// Clocks are the clocks ymxr-prg reads, each over the clock the file
// names.
var Clocks = []string{"-vbl", "-tc"}

// AskedOf is the clock the flags ask for, where the two name one; a call
// that names both is wrong (exit 2).
func AskedOf(t *tool.Tool, args []string) sndh.Asked {
	asked := sndh.AskedChosen
	for _, flag := range args {
		if flag != "-vbl" && flag != "-tc" {
			continue
		}
		one := sndh.AskedVBL
		if flag == "-tc" {
			one = sndh.AskedTimerC
		}
		if asked != sndh.AskedChosen && asked != one {
			t.Usage("-vbl and -tc name two clocks")
		}
		asked = one
	}
	return asked
}

// PackingOf is the packing the flags ask for.
func PackingOf(t *tool.Tool, args []string) Packing {
	out := Packing{Unit: Unit, Ring: ymxr.Ring}
	for _, flag := range args {
		switch {
		case strings.HasPrefix(flag, "-copies"):
			out.Copies = true
			if len(flag) > 7 {
				out.Seconds = decimal(t, flag[7:], flag)
			}
		case strings.HasPrefix(flag, "-k"):
			out.Unit = number(t, flag[2:], flag)
		case strings.HasPrefix(flag, "-m"):
			out.Ring = number(t, flag[2:], flag)
		}
	}
	return out
}

// Packer packs a column under this packing.
func (p Packing) Packer() dtx.Packer {
	return st4.Packer{CopiesFlag: p.Copies, Seconds: p.Seconds}
}

// Only reads every argument against the flags the tool reads: a call that
// passes another, or a file name, is wrong (exit 2).
func Only(t *tool.Tool, args []string, reads ...[]string) {
	for _, flag := range args {
		read := false
		for _, set := range reads {
			for _, one := range set {
				if strings.HasPrefix(flag, one) {
					read = true
				}
			}
		}
		if !read {
			t.Usage("not a flag of the tool: " + flag)
		}
	}
}

// Numbers reads every flag with a number in it, so a call this cannot read
// is an exit of 2 before standard input is read.
func Numbers(t *tool.Tool, args []string) {
	PackingOf(t, args)
	for _, flag := range args {
		if strings.HasPrefix(flag, "-r") && flag != "-r" {
			RowsOf(t, []string{flag}, 0)
		}
	}
}

// RowsOf is the row count -rROWS names, or none where the call names none.
func RowsOf(t *tool.Tool, args []string, none int64) int64 {
	for _, flag := range args {
		if strings.HasPrefix(flag, "-r") {
			at, err := strconv.ParseInt(flag[2:], 10, 64)
			if err != nil {
				t.Usage("not a row count: " + flag)
			}
			return at
		}
	}
	return none
}

// Read is the multi on standard input, read and checked. A text that is
// not this form, or a structure no player plays, exits 1.
//
// A structure that plays, but not as written, produces the warnings of
// YMXS, SPEC.md 6 on standard error and passes: every tool that reads a
// tune reports those, so a fault a writer left in is named where the tune
// is used rather than only where it is checked.
func Read(t *tool.Tool) ymxs.Multi {
	multi, err := text.Read(t.Text())
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	if faults := check.Multi(multi); len(faults) > 0 {
		t.Wrong(tool.Wrong, strings.Join(faults, "\n"))
	}
	t.Warnings(multi)
	return multi
}

// TuneFiles is one tune file a tune, in the multi's order.
func TuneFiles(t *tool.Tool, multi ymxs.Multi, packing Packing,
	said *report.Report) [][]byte {
	var out [][]byte
	for _, tune := range multi.Tunes {
		out = append(out, TuneFile(t, tune, packing, said))
	}
	return out
}

// TuneFile is one tune file: the structure mapped onto the columns and
// packed.
func TuneFile(t *tool.Tool, tune ymxs.Tune, packing Packing,
	said *report.Report) []byte {
	padded, _ := ymxr.PadToUnit(tune, packing.Unit, said)
	made, err := schema.Of(padded)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	written, err := ymxr.WriteNamed(made.Columns, made.Sources, made.Rate,
		packing.Unit, packing.Ring, packing.Packer(), said, tune.Title)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	said.Row(Title(tune), fmt.Sprintf("%d rows at %d Hz, %d sources: %d bytes",
		len(ymxs.Rows(tune)), made.Rate, made.Sources.Count(), len(written.File)))
	return written.File
}

// Title names a tune, and its writer where it has no title.
func Title(tune ymxs.Tune) string {
	if strings.TrimSpace(tune.Title) == "" {
		return "(untitled)"
	}
	return strings.TrimSpace(tune.Title)
}

func number(t *tool.Tool, said, flag string) int {
	at, err := strconv.Atoi(said)
	if err != nil {
		t.Usage("not a number: " + flag)
	}
	return at
}

func decimal(t *tool.Tool, said, flag string) float64 {
	at, err := strconv.ParseFloat(said, 64)
	if err != nil {
		t.Usage("not a number: " + flag)
	}
	return at
}

// SndhOf is the SNDH file of the multi, as the flags ask for it: every
// tune a subtune, 1 upward in the multi's order, behind one core. The
// title and the composer are the first tune's unless -t and -c name
// others, and a tune's title names its subtune where the multi has
// several.
func SndhOf(t *tool.Tool, multi ymxs.Multi, args []string,
	said *report.Report) []byte {
	options := sndh.Options{Asked: AskedOf(t, args)}
	title, composer := "", ""
	for _, flag := range args {
		switch {
		case flag == "-perf":
			options.Monitor = true
		case flag == "-lean":
			options.Lean = true
		case flag == "-pcrel":
			options.Ticks = sndh.Pcrel
		case flag == "-abs":
			options.Ticks = sndh.Absolute
		case flag == "-vbl" || flag == "-tc":
			// the clock asked for, read by AskedOf below
		case strings.HasPrefix(flag, "-copies"):
		case strings.HasPrefix(flag, "-t"):
			title = flag[2:]
		case strings.HasPrefix(flag, "-c"):
			composer = flag[2:]
		}
	}
	first := multi.Tunes[0]
	if title == "" {
		title = Title(first)
	}
	if composer == "" && strings.TrimSpace(first.Composer) != "" {
		composer = strings.TrimSpace(first.Composer)
	}
	options.Title = title
	options.Composer = composer
	var names []string
	for _, tune := range multi.Tunes {
		names = append(names, Title(tune))
	}
	tunes := TuneFiles(t, multi, PackingOf(t, args), said)
	if len(tunes) > 1 {
		options.Names = names
	}
	file, err := sndh.Of(tunes, options)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	return file
}
