#!/bin/sh
# The play call's cost, measured: a program with the raster monitor's
# core for each tune, run under a cycle-exact Hatari tracing the
# background's palette writes, and every call's span read back.
# doc/performance.md has the figures this produced and the method.
#
#   ym/cost.sh [-lean] [-silent] tune.ymxr [more.ymxr ...]
#   VBLS=3000 ym/cost.sh tune.ymxr        # a longer run
#
# -lean reads the core whose ticks neither drop the interrupt level nor
# write an end of interrupt, so what comes back is that core's
# cost against the plain one's (BINARIES.md, performance.md).
#
# performance.md's figures are a VBLS=2300 run, which plays 2,020 calls.
# Hatari boots with fast boot: TOS 2.06 then skips its test of the memory,
# which with fast boot off in the emulator's configuration leaves no call
# in such a run. Both floppy drives are on and empty, so a disk the
# emulator's configuration lists is left out. Where SDL_VIDEODRIVER and
# SDL_AUDIODRIVER are unset or empty, it runs headless and silent, with
# SDL's dummy video and audio drivers; it quits with no confirmation and
# opens a dialog only for a fatal message.
set -e
here=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
HATARI=${HATARI:-hatari}
TOS=${TOS:-$HOME/hatari-2.6.1_macos/tos-2.06.rom}
VBLS=${VBLS:-1500}
lean=
silent=
for arg do
    case $arg in
        -lean) lean=-lean ;;
        -silent) silent=-silent ;;
        -*) echo "ym/cost.sh does not read $arg" >&2; exit 2 ;;
    esac
done
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT HUP INT TERM
for tune in "$@"; do
    case $tune in
        -*) continue ;;
        /*) path=$tune ;;
        *) path=$(pwd)/$tune ;;
    esac
    "$here/bin/ymxr-sndh" -perf $lean $silent -t"$(basename "$tune" .ymxr)" \
        < "$path" > "$work/COST.SND"
    "$here/bin/ymxr-prg" $silent < "$work/COST.SND" > "$work/COST.PRG"
    (cd "$work" && SDL_VIDEODRIVER=${SDL_VIDEODRIVER:-dummy} \
        SDL_AUDIODRIVER=${SDL_AUDIODRIVER:-dummy} "$HATARI" --tos "$TOS" \
        --machine st --cpuclock 8 --cpu-exact on --compatible on \
        --memsize 4 --sound off --conout 2 --fast-forward on \
        --disable-video 1 --run-vbls "$VBLS" --fast-boot on \
        --disk-a "" --disk-b "" --confirm-quit off --alert-level fatal \
        --log-level fatal \
        --trace video_color --trace-file trace.txt COST.PRG \
        </dev/null >/dev/null 2>&1)
    printf '%-40s ' "$(basename "$tune" .ymxr)"
    python3 "$here/ym/cost.py" "$work/trace.txt"
done
