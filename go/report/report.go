// Package report is a conversion's account beside the file it
// writes: the notes and warnings a caller reads back, and, where a tool
// requires it, a running account of what the conversion did.
//
// A tool reports by default and -silent turns it off. The library's
// callers pass a report that emits no line, so a test or a corpus run over
// thousands of dumps prints only what it prints itself.
//
// Lines go to standard error, and what a tool is for goes to standard
// output. A run whose output is read through a pipe or a file reads the
// same with the report on as with it off, and the report is on the
// terminal beside it.
//
// A long run reports its progress on separate lines, spaced apart by a
// tenth of the run and by a second of the clock: a run that ends within a
// second reports no progress, and one of minutes produces about ten such
// lines.
package report

import (
	"fmt"
	"io"
	"os"
	"sync"
	"time"
)

// name is how wide a name stands in a reported row.
const name = 22

// apart is how long a run stays quiet about its progress.
const apart = time.Second

// Report is one conversion's account.
type Report struct {
	mutex sync.Mutex
	notes []string
	says  bool
	to    io.Writer

	// The tenth of a run the last progress line gave, and the clock it
	// stood at.
	tenth int
	last  time.Time

	// Preempted is the frames on which a drum on a voice kept a SID there
	// from running.
	Preempted    int
	Sinus        int
	MissingDrums int
	Overflow     int
	CutAtRepeat  int

	// Most is the sources a tune names, which the overflow note reports.
	Most int
}

// Quiet is a report that prints no line.
func Quiet() *Report {
	return Of(false)
}

// Of is a report that prints what the conversion does where says.
func Of(says bool) *Report {
	return To(says, os.Stderr)
}

// To is the same, into a writer of the caller's.
func To(says bool, to io.Writer) *Report {
	return &Report{says: says, to: to, tenth: -1, last: time.Now()}
}

// Says is whether anything printed here is read: a caller that builds a
// line at some cost asks first.
func (r *Report) Says() bool {
	return r.says
}

// Say puts one line down, at the left margin.
func (r *Report) Say(line string) {
	r.mutex.Lock()
	defer r.mutex.Unlock()
	r.said(line)
}

func (r *Report) said(line string) {
	if r.says {
		fmt.Fprintln(r.to, line)
	}
}

// Step puts one line under the line above it.
func (r *Report) Step(line string) {
	r.Say("  " + line)
}

// Row puts one row of a reported table down: a name, then its value.
func (r *Report) Row(named, what string) {
	if r.says {
		r.Say(fmt.Sprintf("  %-*s %s", name, named, what))
	}
}

// Progress says how far through a run of that many steps this is. A line
// is said where the tenth of the run it stands in has moved and a second
// has passed since the last.
func (r *Report) Progress(what string, done, of int) {
	r.mutex.Lock()
	defer r.mutex.Unlock()
	if !r.says || of <= 0 {
		return
	}
	now := done * 10 / of
	clock := time.Now()
	if now == r.tenth || clock.Sub(r.last) < apart {
		return
	}
	r.tenth = now
	r.last = clock
	r.said(fmt.Sprintf("  %s %d of %d (%d%%)", what, done, of, done*100/of))
}

// Note records a warning, which stands whether the report is on or off.
func (r *Report) Note(text string) {
	r.mutex.Lock()
	defer r.mutex.Unlock()
	r.notes = append(r.notes, text)
	r.said("  note: " + text)
}

// Unsaid is the notes a tool has still to print: the counted ones, which
// are reached only here, and, where the report is off, the ones it would
// have said where they happened.
func (r *Report) Unsaid() []string {
	r.mutex.Lock()
	defer r.mutex.Unlock()
	all := r.all()
	if r.says {
		return all[len(r.notes):]
	}
	return all
}

// Notes is every note of the conversion.
func (r *Report) Notes() []string {
	r.mutex.Lock()
	defer r.mutex.Unlock()
	return r.all()
}

func (r *Report) all() []string {
	out := append([]string{}, r.notes...)
	if r.Sinus > 0 {
		out = append(out, fmt.Sprintf("%d sinus SID frames dropped: the reference"+
			" player runs an empty handler for them", r.Sinus))
	}
	if r.MissingDrums > 0 {
		out = append(out, fmt.Sprintf("%d digidrum triggers dropped: the file has"+
			" no sample at that number", r.MissingDrums))
	}
	if r.Overflow > 0 {
		out = append(out, fmt.Sprintf("%d effect frames dropped: a tune names at"+
			" most %d sources", r.Overflow, r.Most))
	}
	if r.CutAtRepeat > 0 {
		out = append(out, fmt.Sprintf("%d digidrums stopped at the row the tune"+
			" repeats to", r.CutAtRepeat))
	}
	if r.Preempted > 0 {
		out = append(out, fmt.Sprintf("%d frames on which a drum kept a SID from"+
			" running on its voice", r.Preempted))
	}
	return out
}
