#!/bin/sh
# TUNE.PRG under Hatari, with its sound on. ym/play.sh and
# ym/play-ymxs.sh both run a program this way, so the emulator's flags and
# the recording stand here once. doc/tools.md, Play.
#
#   ym/hatari.sh WORK [VBLS] [out.wav]
#
# WORK is the directory TUNE.PRG stands in. VBLS stops the run after that
# many frames, and empty plays on. A third name records the run instead of
# playing it: Hatari writes an AVI, video and sound, which ym/avi.py reads
# back as a WAV, with the run's last frame beside it as a PNG of the same
# name.
#
# HATARI and TOS name the emulator and a TOS image. The emulator is asked
# for its modelled YM mixing, which is what a voice whose volume a timer
# moves is heard through.
set -e
here=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
HATARI=${HATARI:-hatari}
TOS=${TOS:-$HOME/hatari-2.6.1_macos/tos-2.06.rom}
work=$1
vbls=$2
out=$3
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
    # The name's stem, not the path's: a name with no dot in a directory
    # whose path has one would otherwise put the PNG above the run's
    # directory.
    said=${out##*/}
    python3 "$here/ym/avi.py" "$work/run.avi" "$out" "${out%/*}/${said%.*}.png"
fi
