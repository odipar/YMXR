#!/bin/sh
# A YMXS tune played, or recorded: the structure into a program through
# bin/ymxs-to-prg, and the program run under Hatari with its sound on.
# doc/ymxs.md has the pipeline and doc/tools.md the tools it drives.
#
#   ym/play-ymxs.sh [options] tune.ymxs [out.wav]
#   ym/play-ymxs.sh [options] < tune.ymxs
#
# With no name the structure comes from standard input, so a dump plays
# without a file between:
#
#   bin/ym-to-ymxs < tune.ym | ym/play-ymxs.sh
#
# A name that is not a .ymxs records the run instead of playing it: Hatari
# writes an AVI, video and sound, which ym/avi.py reads back as a WAV, with
# the run's last frame beside it as a PNG of the same name.
#
# A multi of several tunes is a set of subtunes, one a tune in the multi's
# order, each named by its title, which the program picks between with the
# arrow keys or a number typed, one digit or two (BINARIES.md 4.6). An SNDH
# file records one rate, so a multi whose tunes do not share one produces a
# line on stderr and no program.
#
# The packer's options:
#
#   -kK        the unit the table packs at, 1 or 2; 2 by default, and a
#              tune whose row count or repeat row is odd is padded until
#              both divide (SPEC.md 6, rule 6)
#   -mN        the ring in bytes, 960 by default
#   -copies[S] a match beyond the ring packs as a copy from the column's
#              separate literal stream, which packs a small ring far
#              smaller; -copiesS searches S seconds for a better parse
#
# The tags, the first tune's title and composer by default:
#
#   -tTITLE    the title
#   -cCOMPOSER the composer
#
# The core the file uses, the plain one by default:
#
#   -perf      the core with the raster monitor in, so the run paints
#              what each call costs (performance.md, Measure)
#   -lean      the core whose ticks neither drop the interrupt level nor
#              write an end of interrupt (performance.md, BINARIES.md)
#   -pcrel     the core whose ticks read a row through the program
#              counter, which the tool writes unasked where the file's
#              tunes stand within the reach (BINARIES.md 5.5)
#   -abs       the core whose ticks read a row through an absolute
#              address
#
# The run:
#
#   -rROWS     the program stops after ROWS rows; it plays on without it
#   -vN        stop the emulator after N frames
#   -silent    the tools report their notes alone
#   -h         this text
#
# The program stays under a directory this says on stderr, so it can be
# read back or run again. HATARI and TOS name the emulator and a TOS image
# (ym/hatari.sh).
#
# Examples:
#   ym/play-ymxs.sh tune.ymxs
#   bin/ym-to-ymxs < tune.ym | ym/play-ymxs.sh -perf
#   ym/play-ymxs.sh -v2000 tune.ymxs run.wav
#
set -e
here=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
out=
rows=
vbls=
help=
silent=
# The flags read off, and the names left in the positional parameters, as
# ym/play.sh reads them.
left=$#
while [ "$left" -gt 0 ]; do
    arg=$1
    shift
    left=$((left - 1))
    case $arg in
        -h|-help|--help) help=1 ;;
        -v*) vbls=${arg#-v} ;;
        -r*) rows=$arg ;;
        -silent) silent=$arg ;;
        -k*|-m*|-copies*|-t*|-c*|-perf|-lean|-pcrel|-abs) set -- "$@" "$arg" ;;
        -*) echo "ym/play-ymxs.sh does not read $arg" >&2; exit 2 ;;
        *) set -- "$@" "$arg" ;;
    esac
done
if [ -n "$help" ]; then
    # the head of this file, to the first line that is not a comment
    sed -n '2,/^[^#]/p' "$0" | sed '$d' | cut -c3-
    exit 0
fi
# The flags stay in the parameters and the names come out of them: one
# .ymxs to play, or none where standard input has the structure, and
# any other name to record to.
tune=
left=$#
while [ "$left" -gt 0 ]; do
    arg=$1
    shift
    left=$((left - 1))
    case $arg in
        -*) set -- "$@" "$arg" ;;
        *.ymxs|*.YMXS|*.json)
            if [ -n "$tune" ]; then
                echo "ym/play-ymxs.sh: $tune and $arg both name a structure;" \
                    "a multi's tunes are its subtunes" >&2
                exit 2
            fi
            tune=$arg
            ;;
        *)
            if [ -n "$out" ]; then
                echo "ym/play-ymxs.sh: $out and $arg both name a file to record to" >&2
                exit 2
            fi
            out=$arg
            ;;
    esac
done
if [ -n "$out" ]; then
    case $out in /*) ;; *) out=$(pwd)/$out ;; esac
fi
# The program is kept, and where it stands is said on stderr: a run is
# often the start of reading it, under a debugger or another host.
work=$(mktemp -d)
echo "ym/play-ymxs.sh: TUNE.PRG is under $work" >&2
if [ -n "$tune" ]; then
    "$here/bin/ymxs-to-prg" "$@" ${rows:+"$rows"} $silent \
        < "$tune" > "$work/TUNE.PRG"
else
    "$here/bin/ymxs-to-prg" "$@" ${rows:+"$rows"} $silent > "$work/TUNE.PRG"
fi
"$here/ym/hatari.sh" "$work" "$vbls" "$out"
