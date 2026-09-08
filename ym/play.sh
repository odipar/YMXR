#!/bin/sh
# A tune played, or recorded: a dump or a tune file into an SNDH file and
# a program around it, and the program run under Hatari with its sound
# on. tools.md has the tools this drives and BINARIES.md the files they
# write.
#
#   ym/play.sh [options] tune.ym [out.wav]
#   ym/play.sh [options] tune.ymxr [out.wav]
#
# The first name is a YM dump or a tune file. A second name records the
# run instead of playing it: Hatari writes an AVI, video and sound, which
# ym/avi.py reads back as a WAV, with the run's last frame beside it as a
# PNG of the same name.
#
# The converter's options, which a tune file is packed already and takes
# none of:
#
#   -kK        the unit the table packs at, 1 or 2; 2 by default, and a
#              tune whose row count or repeat row is odd packs at 1
#   -mN        the ring in bytes, 960 by default
#   -rRR       the row the tune repeats to; -r alone plays it once
#
# The tags:
#
#   -tTITLE    the title, the dump's own or the file's name by default
#   -cCOMPOSER the composer
#
# The core the file takes, the plain one by default:
#
#   -perf      the core with the raster monitor in, so the run paints
#              what each call costs and the program clears the screen
#              for it (performance.md, Measure)
#   -lean      the core whose ticks neither drop the interrupt level nor
#              write their own end of interrupt, 32 cycles a tick
#              cheaper, which asks that no MFP interrupt of the host's
#              nest inside another and that the MFP's vector register be
#              the player's (performance.md, BINARIES.md)
#
# The two are a switch each: both together take the core that is both,
# and the bars a run paints are then the lean ticks' own.
#
# The run:
#
#   -vN        stop after N frames; the tune plays on without it
#   -silent    the tools say only what they wrote; without it each says
#              what it read, the flags it took and what it made, on
#              standard error (tools.md)
#   -h         this text
#
# HATARI and TOS name the emulator and a TOS image. The emulator is asked
# for its modelled YM mixing, which is what a voice whose volume a timer
# moves is heard through.
#
# Examples:
#
#   ym/play.sh "ym/test/Turrican - world 4-3.ym"
#       the dump converted at the defaults and played until SPACE
#
#   ym/play.sh -k1 -tTurrican -cHippel tune.ym
#       packed a byte a unit, and the two tags set
#
#   ym/play.sh -v3000 tune.ym run.wav
#       sixty seconds recorded to run.wav, with run.png beside it
#
#   ym/play.sh -perf -v300 tune.ym bars.wav
#       the same on the monitor's core, so bars.png shows what the call
#       and the timers cost
#
#   ym/play.sh -silent tune.ym
#       the same run with the tools saying only what they wrote
#
#   ym/play.sh -lean tune.ymxr
#       a tune file already packed, on the core whose ticks cost less
#
#   ym/play.sh -perf -lean -v300 tune.ym lean.wav
#       the lean core read on the monitor, against the bars the same
#       run paints without -lean
#
#   TOS=~/tos206.rom HATARI=~/bin/hatari ym/play.sh tune.ym
#       another emulator, and another TOS image
#
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
help=
perf=
lean=
silent=
vbls=
for arg do
    case $arg in
        -h|-help|--help) help=1 ;;
        -perf) perf=-perf ;;
        -lean) lean=-lean ;;
        -silent) silent=-silent ;;
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
if [ -n "$help" ] || [ -z "$tune" ]; then
    # the head of this file, to the first line that is not a comment
    sed -n '2,/^[^#]/p' "$0" | sed '$d' | cut -c3-
    if [ -n "$help" ]; then
        exit 0
    fi
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
            ${repeat:+"$repeat"} $silent >/dev/null
        ;;
esac
"$here/bin/ymxr-sndh" "$file" "$work/TUNE.SND" $perf $lean $silent \
    "-t${title:-${name%.*}}" ${composer:+"-c$composer"} >/dev/null
"$here/bin/ymxr-prg" "$work/TUNE.SND" "$work/TUNE.PRG" $silent >/dev/null
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
