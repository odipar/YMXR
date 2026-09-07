#!/bin/sh
# A tune played, or recorded: a dump or a tune file into an SNDH file and
# a program around it, and the program run under Hatari with its sound on.
# tools.md has the tools this drives and BINARIES.md the files they write.
#
#   ym/play.sh [options] tune.ym [out.wav]
#
#   -kK        the unit the table packs at, 2 by default
#   -mN        the ring in bytes, 960
#   -rRR       the row the tune repeats to; -r alone plays it once
#   -tTITLE    the title in the tags, the file's name by default
#   -cCOMPOSER the composer in the tags
#   -perf      the core with the raster monitor in, so the run paints
#              what each call costs (performance.md)
#   -vN        stop after N frames; the tune plays on without it
#
# -k, -m and -r are the converter's and a tune file takes none of them.
# A second name records the run instead of playing it: Hatari writes an
# AVI, video and sound, which ym/avi.py reads back as a WAV, with the
# run's last frame beside it as a PNG. The emulator is asked for its
# modelled YM mixing, which is what a voice whose volume a timer moves is
# heard through. HATARI and TOS name the emulator and a TOS image.
set -e
here=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
HATARI=${HATARI:-hatari}
TOS=${TOS:-$HOME/hatari-2.6.1_macos/tos-2.06.rom}
tune=
out=
unit=
ring=
repeat=
title=
composer=
perf=
vbls=
for arg do
    case $arg in
        -perf) perf=-perf ;;
        -k*) unit=$arg ;;
        -m*) ring=$arg ;;
        -r*) repeat=$arg ;;
        -t*) title=${arg#-t} ;;
        -c*) composer=${arg#-c} ;;
        -v*) vbls=${arg#-v} ;;
        -*) echo "ym/play.sh does not read $arg" >&2; exit 2 ;;
        *) if [ -z "$tune" ]; then tune=$arg; else out=$arg; fi ;;
    esac
done
if [ -z "$tune" ]; then
    sed -n '2,22p' "$0" | cut -c3-
    exit 2
fi
case $tune in /*) ;; *) tune=$(pwd)/$tune ;; esac
if [ -n "$out" ]; then
    case $out in /*) ;; *) out=$(pwd)/$out ;; esac
fi
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT HUP INT TERM
name=$(basename "$tune")
case $tune in
    *.ymxr)
        file=$tune
        if [ -n "$unit$ring$repeat" ]; then
            echo "ym/play.sh: $name is a tune file, and -k, -m and -r pack one" >&2
            exit 2
        fi
        ;;
    *)
        file=$work/tune.ymxr
        "$here/bin/ym-to-ymxr" "$tune" "$file" ${unit:+"$unit"} ${ring:+"$ring"} \
            ${repeat:+"$repeat"} >/dev/null
        ;;
esac
"$here/bin/ymxr-sndh" "$file" "$work/TUNE.SND" $perf \
    "-t${title:-${name%.*}}" ${composer:+"-c$composer"} >/dev/null
"$here/bin/ymxr-prg" "$work/TUNE.SND" "$work/TUNE.PRG" >/dev/null
set -- --tos "$TOS" --machine st --cpuclock 8 --cpu-exact on \
    --compatible on --memsize 4 --sound 44100 --ym-mixing model \
    --log-level fatal
if [ -n "$vbls" ]; then
    set -- "$@" --run-vbls "$vbls"
fi
if [ -n "$out" ]; then
    set -- "$@" --fast-forward on --avirecord --avi-vcodec png \
        --png-level 1 --avi-file "$work/run.avi"
fi
(cd "$work" && "$HATARI" "$@" TUNE.PRG >/dev/null 2>&1) || true
if [ -n "$out" ]; then
    python3 "$here/ym/avi.py" "$work/run.avi" "$out" "${out%.*}.png"
fi
