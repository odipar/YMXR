#!/bin/sh
# A tune played, or recorded: a dump or a tune file, or several of
# either, into one SNDH file and a program around it, and the program run
# under Hatari with its sound on. tools.md has the tools this drives and
# BINARIES.md the files they write.
#
#   ym/play.sh [options] tune.ym [more.ym ...] [out.wav]
#   ym/play.sh [options] tune.ymxr [more.ymxr ...] [out.wav]
#
# The first name is a YM dump or a tune file. After it, a name ending in
# .ym or .ymxr, in either case, is another tune, and any other name
# records the run instead of playing it: Hatari writes an AVI, video and
# sound, which ym/avi.py reads back as a WAV, with the run's last frame
# beside it as a PNG of the same name.
#
# Several tunes go into one file, a subtune each in the order named, and
# the program picks between them on the keys 1 to 9. Each is named by its
# file, and an SNDH file records one rate, so a set whose tunes do not
# share one gets a line on stderr and no file. A tenth tune and past it
# play only under a host that asks for a subtune by number.
#
# The converter's options, which a tune file is packed already and needs
# none of:
#
#   -kK        the unit the table packs at, 1 or 2; 2 by default, and a
#              tune whose row count or repeat row is odd packs at 1
#   -mN        the ring in bytes, 960 by default
#   -rRR       the row the tune repeats to; -r alone plays it once
#   -copies[S] a match beyond the ring packs as a copy from the column's
#              separate literal stream, which packs a small ring far smaller;
#              -copiesS searches S seconds for a better parse, and a
#              search of some seconds packs another parse every run
#
# The tags:
#
#   -tTITLE    the title, the dump's or the file's name by default
#   -cCOMPOSER the composer
#
# The core the file uses, the plain one by default:
#
#   -perf      the core with the raster monitor in, so the run paints
#              what each call costs and the program clears the screen
#              for it (performance.md, Measure)
#   -lean      the core whose ticks neither drop the interrupt level nor
#              write an end of interrupt, 32 cycles cheaper on a
#              tick that writes a row and 16 on one that ends a source,
#              which asks that no MFP interrupt of the host's nest inside
#              another and that the MFP's vector register be the player's
#              (performance.md, BINARIES.md)
#
# The two are a switch each: both together select the core that is both,
# and the bars a run paints are then the lean ticks' own.
#
# The run:
#
#   -vN        stop after N frames; the tune plays on without it
#   The SNDH file and the program stay under a directory this says on
#   stderr, so either can be read back or run again.
#
#   -silent    the tools report their notes alone: this script reads
#              their standard output, and without the flag each says
#              what it read, the flags it took and what it made, on
#              standard error (tools.md)
#   -h         this text
#
# HATARI and TOS name the emulator and a TOS image (ym/hatari.sh). The
# emulator is asked for its modelled YM mixing, which is what a voice whose
# volume a timer moves is heard through.
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
#   ym/play.sh a.ym b.ym c.ym
#       the three as subtunes 1, 2 and 3 of one file, played from the
#       first, the keys 1 to 3 picking between them
#
#   ym/play.sh -perf -v300 tune.ym bars.wav
#       the same on the monitor's core, so bars.png shows what the call
#       and the timers cost
#
#   ym/play.sh -silent tune.ym
#       the same run with the tools reporting their notes alone
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
out=
unit=
ring=
repeat=
copies=
title=
composer=
help=
perf=
lean=
silent=
vbls=
# The flags read off, and the names left in the positional parameters:
# each is shifted off and a name put back at the end, so the loop reaches
# every argument once and what remains is the names in that order.
left=$#
while [ "$left" -gt 0 ]; do
    arg=$1
    shift
    left=$((left - 1))
    case $arg in
        -h|-help|--help) help=1 ;;
        -perf) perf=-perf ;;
        -lean) lean=-lean ;;
        -silent) silent=-silent ;;
        -k*) unit=$arg ;;
        -m*) ring=$arg ;;
        -r*) repeat=$arg ;;
        -t*) title=${arg#-t} ;;
        -copies*) copies=$arg ;;
        -c*) composer=${arg#-c} ;;
        -v*) vbls=${arg#-v} ;;
        -*) echo "ym/play.sh does not read $arg" >&2; exit 2 ;;
        *) set -- "$@" "$arg" ;;
    esac
done
if [ -n "$help" ] || [ $# -eq 0 ]; then
    # the head of this file, to the first line that is not a comment
    sed -n '2,/^[^#]/p' "$0" | sed '$d' | cut -c3-
    if [ -n "$help" ]; then
        exit 0
    fi
    exit 2
fi
# The first name is a tune. After it a .ym or .ymxr is another tune, one
# subtune each, and any other name is the file the run is recorded to.
left=$#
at=0
while [ "$left" -gt 0 ]; do
    arg=$1
    shift
    left=$((left - 1))
    at=$((at + 1))
    if [ "$at" -eq 1 ]; then
        set -- "$@" "$arg"
        continue
    fi
    case $arg in
        *.ym|*.YM|*.ymxr|*.YMXR) set -- "$@" "$arg" ;;
        *)
            if [ -n "$out" ]; then
                echo "ym/play.sh: $out and $arg both name a file to record to" >&2
                exit 2
            fi
            out=$arg
            ;;
    esac
done
if [ -n "$out" ]; then
    case $out in /*) ;; *) out=$(pwd)/$out ;; esac
fi
# A tune file is packed already, so the converter's flags have no work to
# pack. Said over every name before any dump is converted, so the same
# mistake costs the same whichever name it stands under.
if [ -n "$unit$ring$repeat$copies" ]; then
    left=$#
    while [ "$left" -gt 0 ]; do
        arg=$1
        shift
        left=$((left - 1))
        case $arg in
            *.ymxr|*.YMXR)
                echo "ym/play.sh: $(basename "$arg") is a tune file, and -k, -m and -r" \
                    "pack one" >&2
                exit 2
                ;;
        esac
        set -- "$@" "$arg"
    done
fi
# The SNDH file and the program are kept, and where they stand is said
# on stderr: a run is often the start of reading one of them, with
# bin/ymxr-trace or a debugger or another host, and a path that has been
# cleared prints no line.
work=$(mktemp -d)
echo "ym/play.sh: TUNE.SND and TUNE.PRG are under $work" >&2
# Each tune converted where it is a dump, and the tune files left as they
# are, into the arguments ymxr-sndh reads: one subtune a name, in that
# order, each named by its file where there is more than one.
tunes=$#
stem=
at=0
left=$#
while [ "$left" -gt 0 ]; do
    tune=$1
    shift
    left=$((left - 1))
    case $tune in /*) ;; *) tune=$(pwd)/$tune ;; esac
    name=$(basename "$tune")
    at=$((at + 1))
    [ -n "$stem" ] || stem=${name%.*}
    case $tune in
        *.ymxr|*.YMXR)
            file=$tune
            ;;
        *)
            # A directory a tune, so the file a dump converts into carries
            # the tune's name: the tools name a subtune by the file they
            # were passed, and two tunes may share a name.
            mkdir -p "$work/$at"
            file=$work/$at/${name%.*}.ymxr
            "$here/bin/ym-to-ymxr" ${unit:+"$unit"} ${ring:+"$ring"} \
                ${repeat:+"$repeat"} ${copies:+"$copies"} $silent \
                < "$tune" > "$file"
            ;;
    esac
    if [ "$tunes" -gt 1 ]; then
        set -- "$@" "$file" "-n${name%.*}"
    else
        set -- "$@" "$file"
    fi
done
if [ "$tunes" -gt 9 ]; then
    echo "ym/play.sh: $tunes tunes, and the program's keys reach subtune 9" >&2
fi
# The tune files into one multi file, which is what an SNDH file of
# several subtunes is made from (BINARIES.md 0); one tune goes in as the
# tune file it is.
if [ "$tunes" -gt 1 ]; then
    "$here/bin/ymxr-multi" "$@" $silent > "$work/TUNE.YMXR"
else
    cp "$1" "$work/TUNE.YMXR"
fi
"$here/bin/ymxr-sndh" $perf $lean $silent "-t${title:-$stem}" \
    ${composer:+"-c$composer"} < "$work/TUNE.YMXR" > "$work/TUNE.SND"
"$here/bin/ymxr-prg" $silent < "$work/TUNE.SND" > "$work/TUNE.PRG"
"$here/ym/hatari.sh" "$work" "$vbls" "$out"
