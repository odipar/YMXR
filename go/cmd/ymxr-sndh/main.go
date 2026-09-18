// Command ymxr-sndh reads a tune file or a multi file (doc/BINARIES.md 0)
// on standard input and writes an SNDH file on standard output.
//
// A multi file's tunes are subtunes 1 up in its order, each named by the
// name the multi file records for it. The title is the first tune's name
// unless -tTITLE names another. -lean puts the core whose ticks neither
// drop the interrupt level nor write an end of interrupt under the tunes,
// -perf puts the core with the raster monitor in there, for reading a run,
// and -pcrel the core whose ticks read a row through the program counter
// (BINARIES.md 5.5). The three are one switch each, and any two together
// select the core that is both, so -perf -lean reads what a lean run
// costs.
package main

import (
	"fmt"
	"os"
	"strings"

	"github.com/odipar/ymxs/go/tool"

	"github.com/odipar/ymxr/go/binaries"
	"github.com/odipar/ymxr/go/flags"
	"github.com/odipar/ymxr/go/report"
	"github.com/odipar/ymxr/go/sndh"
	"github.com/odipar/ymxr/go/ymxr"
)

func main() {
	t, args := tool.Of("ymxr-sndh", os.Args[1:], flags.Tags...)
	flags.Only(t, args, flags.Tags)
	options := sndh.Options{}
	for _, flag := range args {
		switch {
		case flag == "-perf":
			options.Monitor = true
		case flag == "-lean":
			options.Lean = true
		case flag == "-pcrel":
			options.Pcrel = true
		case strings.HasPrefix(flag, "-copies"):
			t.Usage("not a flag of the tool: " + flag + "; a tune file is packed already")
		case strings.HasPrefix(flag, "-t"):
			options.Title = flag[2:]
		case strings.HasPrefix(flag, "-c"):
			options.Composer = flag[2:]
		}
	}
	said := report.Of(t.Reports())
	file := t.Bytes()
	var tunes [][]byte
	var names []string
	if ymxr.IsMulti(file) {
		read, err := ymxr.ReadMulti(file)
		if err != nil {
			t.Wrong(tool.Wrong, err.Error())
		}
		tunes, names = read.Tunes, read.Names
	} else {
		tunes, names = [][]byte{file}, []string{""}
	}
	if options.Title == "" {
		options.Title = "(untitled)"
		if strings.TrimSpace(names[0]) != "" {
			options.Title = names[0]
		}
	}
	if len(tunes) > 1 {
		options.Names = names
	}
	out, err := sndh.Of(tunes, options)
	if err != nil {
		t.Wrong(tool.Wrong, err.Error())
	}
	made(said, options, names, tunes, out)
	subtunes := " subtunes"
	if len(tunes) == 1 {
		subtunes = " subtune"
	}
	t.Report(fmt.Sprintf("%d bytes, %d%s", len(out), len(tunes), subtunes))
	t.WriteBytes(out)
}

// made says what the file was made of: the core the switches picked, the
// tags written, and each subtune's bound tune.
func made(said *report.Report, options sndh.Options, names []string, tunes [][]byte,
	file []byte) {
	if !said.Says() {
		return
	}
	core, err := binaries.Read(binaries.Named(options.Monitor, options.Lean, options.Pcrel))
	if err != nil {
		return
	}
	said.Say(fmt.Sprintf("the core: %s, %d bytes", binaryName(options), len(core)))
	var switches []string
	if options.Monitor {
		switches = append(switches, "-perf, the raster monitor in")
	}
	if options.Lean {
		switches = append(switches, "-lean, ticks that neither drop the interrupt level"+
			" nor write an end of interrupt")
	}
	if options.Pcrel {
		switches = append(switches, "-pcrel, ticks that read a row through the program"+
			" counter")
	}
	if len(switches) == 0 {
		said.Row("the switches", "none, the plain core")
	} else {
		said.Row("the switches", strings.Join(switches, "; "))
	}
	composer := ""
	if options.Composer != "" {
		composer = ", COMM " + options.Composer
	}
	named := ""
	if options.Names != nil {
		name := " names"
		if len(options.Names) == 1 {
			name = " name"
		}
		named = fmt.Sprintf(", !#SN with %d%s", len(options.Names), name)
	}
	said.Say("the tags: TITL " + options.Title + composer + named)
	set, err := sndh.Bind(tunes)
	if err != nil {
		return
	}
	bound := 0
	images := 0
	for _, image := range set.Images {
		images += len(image)
	}
	for i, one := range set.Tunes {
		bound += len(one)
		named := names[i]
		if strings.TrimSpace(named) == "" {
			named = "the tune"
		}
		said.Row(named, fmt.Sprintf("%d bytes bound to %d, its table in image %d",
			len(tunes[i]), len(one), set.Image[i]+1))
	}
	image := " images of "
	if len(set.Images) == 1 {
		image = " image of "
	}
	said.Say(fmt.Sprintf("the images: %d%s%d bytes, DTX's reader once a set of tunes"+
		" that share one", len(set.Images), image, images))
	// What an image fixes once splits a set into more than one,
	// and only a flag moves the unit, -k on one dump and not another:
	// a tune whose row count or repeat row is odd is padded to the unit
	// named (SPEC.md 6, rule 6; tools.md, experiments.md).
	for i := range set.Images {
		of := 0
		for _, which := range set.Image {
			if which == i {
				of++
			}
		}
		tune := " tunes"
		if of == 1 {
			tune = " tune"
		}
		said.Row(fmt.Sprintf("image %d", i+1), fmt.Sprintf("%s, %d%s", set.Shapes[i],
			of, tune))
	}
	said.Say(fmt.Sprintf("the file: %d bytes, the core %d, the images %d, the tunes %d,"+
		" the workspace and the rest %d", len(file), len(core), images, bound,
		len(file)-len(core)-images-bound))
}

func binaryName(options sndh.Options) string {
	return binaries.Named(options.Monitor, options.Lean, options.Pcrel)
}
