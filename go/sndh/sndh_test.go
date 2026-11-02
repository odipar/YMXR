package sndh_test

import (
	"testing"

	"github.com/odipar/ymxr/go/internal/built"
	"github.com/odipar/ymxr/go/sndh"
	"github.com/odipar/ymxr/go/ymxr"
)

func files(t *testing.T) (nine, ten []byte) {
	t.Helper()
	nine, err := built.File(built.Unmapped(), "")
	if err != nil {
		t.Fatal(err)
	}
	ten, err = built.File(built.Mapped(), "")
	if err != nil {
		t.Fatal(err)
	}
	return nine, ten
}

const unbound = "the binder writes bound tunes to version 9, and this tune file is" +
	" version 10"

func TestAVersion10TuneFileIsUnbound(t *testing.T) {
	nine, ten := files(t)
	if _, err := sndh.Bound(nine); err != nil {
		t.Fatalf("version 9 binds, and reads %q", err)
	}
	if _, err := sndh.Bound(ten); err == nil || err.Error() != unbound {
		t.Fatalf("a bound tune of version 10 reads %v, and the line is %q", err, unbound)
	}
	if _, err := sndh.Bind([][]byte{nine, ten}); err == nil || err.Error() != unbound {
		t.Fatalf("a set with version 10 reads %v, and the line is %q", err, unbound)
	}
	_, err := sndh.Of([][]byte{nine, ten}, sndh.Options{Title: "set"})
	if want := "subtune 2: " + unbound; err == nil || err.Error() != want {
		t.Fatalf("an SNDH file with version 10 reads %v, and the line is %q", err, want)
	}
}

func TestASetBindsAtVersion10WhereATuneFileIs(t *testing.T) {
	nine, ten := files(t)
	if v := sndh.Binds([][]byte{nine, nine}); v != ymxr.Unmapped {
		t.Fatalf("a set of version 9 binds at %d", v)
	}
	if v := sndh.Binds([][]byte{nine, ten}); v != ymxr.Version {
		t.Fatalf("a set with version 10 binds at %d", v)
	}
}
