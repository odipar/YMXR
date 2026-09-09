#!/bin/sh
# A YMX file played: converted to a tune file and handed to ym/play.sh,
# which combines it with the player and runs it under Hatari. tools.md
# has the tools this drives, and BINARIES.md the files they write.
#
#   ymx/play.sh [options] tune.ymx [more.ymx ...] [out.wav]
#
# The first name is a YMX file. After it, a name ending in .ymx, in
# either case, is another tune, and any other name records the run
# instead of playing it, as ym/play.sh has it. Several tunes go into one
# file, a subtune each in the order named.
#
# The converter's options, which reach bin/ymx-to-ymxr:
#
#   -kK        the unit the table packs at, 1 or 2; 2 by default
#   -mN        the ring in bytes, 960 by default
#   -copies[S] a match beyond the ring packs as a copy from the column's
#              own literal stream (tools.md)
#
# Every other option is ym/play.sh's and is passed to it: -tTITLE,
# -cCOMPOSER, -perf, -lean, -vN, -silent and -h among them.
#
# YMX_DUMP names YMX's ymx-dump, which reads a .ymx out. With neither it
# nor YMX_REPO set, ../YMX/go/bin/ymx-dump is taken.
#
# The tune file each .ymx converts to is kept, and the directory holding
# them is said on stderr, so a conversion can be read back with
# bin/ymxr-trace or played on its own.
#
#   ymx/play.sh ymx/test/Deeper.ymx
#   ymx/play.sh -v600 ymx/test/*.ymx run.wav
set -e
here=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
YMX_REPO=${YMX_REPO:-$(CDPATH= cd -- "$here/.." && pwd)/YMX}
YMX_DUMP=${YMX_DUMP:-$YMX_REPO/go/bin/ymx-dump}
export YMX_DUMP
if [ ! -x "$YMX_DUMP" ]; then
    echo "ymx/play.sh: no ymx-dump at $YMX_DUMP: YMX_DUMP names it, or" \
         "YMX_REPO names the checkout it is built in" >&2
    exit 2
fi
# The converter's flags read off, and ym/play.sh's kept for it. A name
# may hold a space, so the names stay in the positional parameters and no
# string of them is built: each is shifted off and the file it converts
# to is put back at the end, so what remains is the tunes in the order
# given.
mine=
theirs=
recording=
for arg do
    case $arg in
        -k*|-m*|-copies*) mine="$mine $arg" ;;
        -*) theirs="$theirs $arg" ;;
    esac
done
tunes=0
for arg do
    case $arg in
        *.ymx|*.YMX) tunes=$((tunes + 1)) ;;
    esac
done
if [ "$tunes" -eq 0 ]; then
    sed -n '2,/^[^#]/p' "$0" | sed '$d' | cut -c3-
    exit 2
fi
# The tune files stay: the last thing this does is exec ym/play.sh,
# which replaces the shell, so nothing here runs afterwards to clear
# them. Said on stderr rather than left to be found.
work=$(mktemp -d)
echo "ymx/play.sh: the tune files are under $work" >&2
# A directory a tune, so the file a conversion writes carries the tune's
# own name: ym/play.sh names a subtune by the file it is given, and two
# tunes may share a name.
at=0
left=$#
while [ "$left" -gt 0 ]; do
    arg=$1
    shift
    left=$((left - 1))
    case $arg in
        -*) ;;
        *.ymx|*.YMX)
            at=$((at + 1))
            name=$(basename "$arg")
            mkdir -p "$work/$at"
            file=$work/$at/${name%.*}.ymxr
            # shellcheck disable=SC2086
            "$here/bin/ymx-to-ymxr" "$arg" "$file" $mine -silent >/dev/null
            set -- "$@" "$file"
            ;;
        *)
            # any other name records the run, and ym/play.sh reads it
            # after the tunes
            recording=$arg
            ;;
    esac
done
if [ -n "$recording" ]; then
    set -- "$@" "$recording"
fi
# shellcheck disable=SC2086
exec "$here/ym/play.sh" $theirs "$@"
