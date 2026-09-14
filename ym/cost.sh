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
    (cd "$work" && "$HATARI" --tos "$TOS" --machine st --cpuclock 8 \
        --cpu-exact on --compatible on --memsize 4 --sound off --conout 2 \
        --fast-forward on --disable-video 1 --run-vbls "$VBLS" \
        --log-level fatal --trace video_color --trace-file trace.txt \
        COST.PRG >/dev/null 2>&1)
    printf '%-40s ' "$(basename "$tune" .ymxr)"
    python3 "$here/ym/cost.py" "$work/trace.txt"
done
