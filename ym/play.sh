#!/bin/sh
# A tune played, or recorded: a dump or a tune file into an SNDH file and
# a program around it, and the program run under Hatari with its sound on.
# tools.md has the tools this drives and BINARIES.md the files they write.
#
#   ym/play.sh tune.ym                  play it, SPACE stops
#   ym/play.sh tune.ymxr                a tune file plays as it stands
#   ym/play.sh tune.ym out.wav          record it instead, sound to a WAV
#   VBLS=1500 ym/play.sh tune.ym        stop after that many frames
#   PERF=1 ym/play.sh tune.ym out.wav   the raster monitor's core in
#
# A recording is a Hatari AVI, video and sound; ym/avi.py takes the sound
# out of it as a WAV and writes the run's last frame beside it as a PNG.
set -e
here=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
HATARI=${HATARI:-hatari}
TOS=${TOS:-$HOME/hatari-2.6.1_macos/tos-2.06.rom}
tune=$1
out=$2
if [ -z "$tune" ]; then
    sed -n '2,12p' "$0" | cut -c3-
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
    *.ymxr) file=$tune ;;
    *) file=$work/tune.ymxr; "$here/bin/ym-to-ymxr" "$tune" "$file" >/dev/null ;;
esac
"$here/bin/ymxr-sndh" "$file" "$work/TUNE.SND" ${PERF:+-perf} \
    -t"${name%.*}" >/dev/null
"$here/bin/ymxr-prg" "$work/TUNE.SND" "$work/TUNE.PRG" >/dev/null
set -- --tos "$TOS" --machine st --cpuclock 8 --cpu-exact on \
    --compatible on --memsize 4 --sound 44100 --log-level fatal
if [ -n "$VBLS" ]; then
    set -- "$@" --run-vbls "$VBLS"
fi
if [ -n "$out" ]; then
    set -- "$@" --fast-forward on --avirecord --avi-vcodec png \
        --png-level 1 --avi-file "$work/run.avi"
fi
(cd "$work" && "$HATARI" "$@" TUNE.PRG >/dev/null 2>&1) || true
if [ -n "$out" ]; then
    python3 "$here/ym/avi.py" "$work/run.avi" "$out" "${out%.*}.png"
fi
