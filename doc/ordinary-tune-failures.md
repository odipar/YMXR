# Ordinary tune failures

Tested with YMXS 0.7.1 and YMXR 0.14.0. The inputs are valid tunes and
ordinary file conversions. The proposed changes below are for follow-up.

## A counted sample stops after one tick

A 50 Hz tune plays a mapped 8-bit sample on R8 at 25,600 ticks per second.
The YMXS checker accepts it. A 65,536-row sample starts with volume
writes 8, 9, 10, 11. Add one sample row and the first four calls to the
timer vector write 8, 8, 8, 8. The sample stops after the first write
and the vector serves the parked value on later calls. Hatari also
recorded one R8 write for the failing size using the linked PRG at
9,600 ticks per second. The control played consecutive sample rows.

The absolute player stores the number of rows left as a 16-bit value.
The count of 65,536 rolls through zero for a full pass, but the count of
65,537 becomes 1. The lookup handler decrements that word on each tick.
See the counted source setup in [init.S](../68k/player/init.S) and the
counted lookup handlers in [lookup_ticks.S](../68k/player/lookup_ticks.S).

**Proposed fix.** Check the remaining row count and repeat span before
emitting a file. Report a source length the player cannot encode.
For playback of longer samples, select a long counter only for sources
that need it. Keep the existing timer code for shorter sources and test
65,535, 65,536 and 65,537 rows for one-shot and repeating samples.

## A two-column source reads its first column twice

The source of a `setToneA` effect has a low and a high byte per row.
The YMXS checker and YMXR exporter accept both sizes below. These are
the first four register pairs written by the absolute player.

| Source rows | R0 and R1 pairs |
|---:|---|
| 65,534 | (200, 4), (201, 5), (202, 6), (203, 7) |
| 65,535 | (200, 200), (200, 200), (200, 200), (200, 200) |

Each DTX1 column is padded to a word. The stride of a 65,535-row column
is 65,536, which becomes zero when written to the 16-bit stride field
in [init.S](../68k/player/init.S). The slot setup uses the zero stride
for the second column in [start.S](../68k/player/start.S). It also reads
the marker from the wrong column. Hatari recorded one R1 write of
200 in the linked PRG. The shorter control started with 4, 5, 6, 7.

**Proposed fix.** Check the padded stride before exporting a wide
source. To support longer sources, keep the full column offsets through
init and the start routines. The absolute handlers already read from
per-column addresses, so the wider offset can be resolved before ticks.

## Chunked playback for long sources

As another way to support long sources, YMXR could split any sample or
wavetable of one, two, or three columns at whole-row boundaries. Size
each chunk to fit the handler's row counter and padded column stride.
Keep one logical source so the columns advance together and loop
points, position resets, and remaps use the original row numbers.

At a chunk boundary, the timer handler would switch the row addresses
so the next tick reads the next row at the same rate. The timer keeps
running through the switch. Sources that fit in one chunk could use the
existing handler. Check playback across chunk boundaries, including
loops, position resets, and remaps.

## A UTF-8 signature blocks ordinary file imports

An unchanged tune is accepted by the Go and Java tools. Add a UTF-8
byte order mark at the start of its CSV and both versions of
`ymxs-csv-to-json` exit 1 with `a row before any table opens`. The
same prefix on JSON causes `ymxs-check` and `ymxs-to-prg` to exit 1
with a JSON syntax error. This is a file interoperability case.

**Proposed fix.** Strip one leading U+FEFF when decoding UTF-8 CSV or
JSON input. Keep U+FEFF inside fields and cover both forms in the
Go and Java command tests.

## A PRG row limit restarts at each subtune

Export two four-row tunes that each play once with `ymxs-to-prg -r8`.
Hatari plays both tunes and then repeats them past the limit. Over the
250-VBL run, the 50/50 Hz set wrote 120 tune rows, and the 50/60 Hz
set wrote 126. With `-r3` or `-r4`, both sets stopped after the requested
number of rows in the first subtune. Go and Java emitted identical
programs, and 0.13.0 reproduced the same output bytes.

In [YMXR_prg.S](../68k/YMXR_prg.S), automatic advance calls `start`,
which resets `rows_left` to the original limit. The limit therefore
never reaches zero where every subtune ends before it. The tool
documents `-rROWS` as the number of rows the program plays in
[tools.md, section 13](tools.md#13-ymxr-prg).

**Proposed fix.** Initialize the row budget once per program run and
preserve it when changing subtunes. Count only rows that the player
writes. Add regression cases for two four-row subtunes at 50/50 and
50/60 Hz, with a limit before, at, and beyond the first subtune's end.

To reproduce, create the two versions of the set and open the PRGs in
Hatari. The first tune writes R0 values 64 through 67 and the second
writes 96 through 99. Both programs should end after one pass through
those eight values.

~~~sh
work=$(mktemp -d)
for rate in 50 60; do
    python3 -B - "$rate" <<'PY' >"$work/set.json"
import json
import sys

def tune(title, rate, first):
    return dict(title=title, composer="", writer="", rate=rate, rows=4,
                repeat=None, sources=[], transformations=[],
                registers=dict(r0=list(range(first, first + 4))))

print(json.dumps(dict(format="ymxs", version=7, tunes=[
    tune("First", 50, 64), tune("Second", int(sys.argv[1]), 96),
])))
PY
    ymxs-to-prg -silent -r8 <"$work/set.json" >"$work/RATE$rate.PRG"
done
printf '%s\n' "$work"
~~~

## A title can hide the subtune names

A set with the titles `HDNS Intro` and `Outro` exports with both names
in the SNDH `!#SN` table. The standalone program in Hatari lists
`HDNS Intro` for both subtunes. The same set with `Intro` or
`XHDNS Intro` as its first title lists `Outro` correctly.

The `names` routine in [YMXR_prg.S](../68k/YMXR_prg.S) checks every even
offset for `!#SN` and `HDNS`. The first title starts at offset 20, so the
letters `HDNS` there end the search. The fallback uses the file title for
every subtune.

**Proposed fix.** Read the tag block from one tag boundary to the next,
using each field's length or terminator. Bound the search by the start
of the core. Test titles containing `HDNS` and `!#SN` at both alignments.

## Automatic advance discards a digit

Start the second cue in a set of twelve 50 Hz cues and press `1` while
it plays. The player waits 25 rows for a second digit because the set
includes 10, 11, and 12. If the cue ends after 16 rows, the player
advances to cues 3 and 4. With the same cue set to repeat, the pause
ends and the player selects cue 1.

In [YMXR_prg.S](../68k/YMXR_prg.S), automatic advance calls `start`,
which clears both the digit and its timeout. The key loop therefore
loses the digit before it can select the subtune.

**Proposed fix.** Preserve the digit and the time left on its timeout
when a tune ends. Clear them when a manual selection starts a tune.
Test both a single digit and a two-digit selection that spans a change
of cue.

## The number entry timeout depends on the tune rate

In a set of twelve repeating cues, select cue 2 and type `1` and `2`
180 ms apart. With cue 2 at 50 Hz the player selects cue 12. With cue 2
at 200 Hz it selects cue 1 and then cue 2. Both cues repeat, so this
happens during one manual selection.

The key loop in [YMXR_prg.S](../68k/YMXR_prg.S) waits 25 played rows
after the first digit. This is 500 ms at 50 Hz and 125 ms at 200 Hz.

**Proposed fix.** Measure the pause in elapsed time. Use the playback
clock before it is divided down to the tune rate, with the timeout
scaled to the clock frequency. Preserve the time left across automatic
advance. Test digit pairs on both rates with the same interval.

## Reproduce the subtune menu failures

Run with the released YMXR tools on the path. The first group of
programs shows the titles on startup. For the second group, open each
program in Hatari, press Right to start cue 2, then immediately press
`1`. Wait about half a second. The repeating set returns to cue 1 and
the set of one-shot cues continues to advance.

For the timeout, open `LOOP.PRG` and then `FAST.PRG`. In each, use Right
to select cue 2, then type `1` and `2` about 180 ms apart. `LOOP.PRG`
selects cue 12. `FAST.PRG` selects cue 1 and then cue 2.

~~~sh
work=$(mktemp -d)
python3 -B - "$work" <<'PY'
import json
from pathlib import Path
import sys

root = Path(sys.argv[1])

def tune(title, rows, repeat, r0):
    return dict(title=title, composer="", writer="", rate=50, rows=rows,
                repeat=repeat, sources=[], transformations=[],
                registers=dict(r0=[r0] + [-1] * (rows - 1)))

def write(name, tunes):
    (root / (name + ".json")).write_text(
        json.dumps(dict(format="ymxs", version=7, tunes=tunes)))

for name, title in (("EVEN", "HDNS Intro"), ("ODD", "XHDNS Intro"),
                    ("PLAIN", "Intro")):
    write(name, [tune(title, 4, 0, 64), tune("Outro", 4, 0, 96)])

for name, repeat in (("SHORT", None), ("LOOP", 0)):
    write(name, [tune(f"Cue {i}", 180 if i == 1 else 16, repeat, 30 + i)
                 for i in range(1, 13)])
fast = [tune(f"Cue {i}", 180 if i == 1 else 16, 0, 30 + i)
        for i in range(1, 13)]
fast[1]["rate"] = 200
write("FAST", fast)
PY
for name in EVEN ODD PLAIN; do
    ymxs-to-prg -silent -r4 <"$work/$name.json" >"$work/$name.PRG"
done
for name in SHORT LOOP FAST; do
    ymxs-to-prg -silent <"$work/$name.json" >"$work/$name.PRG"
done
ymxr-layout -silent <"$work/EVEN.PRG"
printf '%s\n' "$work"
~~~

## A 50 Hz tune speeds up on a monochrome ST

A 50 Hz tune uses Timer C to play a four-row volume waveform at
2,560 ticks a second. Its standalone export uses the VBL for tune
rows because the effect claims Timer C. In Hatari the rows play at
about 50 Hz with the color monitor and 71.5 Hz with the monochrome
monitor. The Timer C effect still runs at 2,560 Hz in monochrome.
The same 50 Hz tune without the effect uses Timer C for its rows
and plays at 50 Hz on both monitors.

The VBL handler in [YMXR_prg.S](../68k/YMXR_prg.S) plays one row at
each display refresh. The 50 Hz assumption also affects an explicit
`-vbl` export and a 50 Hz export with `-perf`.

**Proposed fix.** Check the display refresh when the program selects
the VBL. For fixed-rate playback on a display with a different
refresh, use a free MFP timer when one is available. Report a rate
mismatch where VBL timing is required and the requested rate cannot
be met. Test 50 Hz rows with Timer C in use on both monitor types.

## Two fast tone effects can stall an 8 MHz ST

A 50 Hz tune starts two 16-row tone wavetables on Timers A and D.
Each timer runs at 19,200 Hz. YMXS accepts the tune and reports that
it satisfies the tune rules. YMXR exports a PRG. On an 8 MHz ST in
Hatari, the timer handlers write their tone periods throughout the
run and the first frame never reaches its R6 write. The same PRG at
16 MHz writes all 20 frame markers and exits. At 8 MHz, reducing
both timers to 15,360 Hz also lets the program finish.

The two-column handler spends 152 cycles on an ordinary row before
interrupt entry and return. With those 64 cycles, the two timers
already require about 8.29 million cycles a second, before loop
boundaries and frame processing. See [performance.md](performance.md).
The default PRG export has no check for that combined load.

**Proposed fix.** Warn during PRG export when simultaneous effect
rates exceed the stock ST's cycle budget. Estimate load from the
chosen handler, source shape and timer rate, including interrupt
overhead. Include the affected subtune and row in the warning and
allow export for faster machines. Test the two measured rates and
the same tune with only one timer.

## Reproduce the timer issues

Export the three tunes below. In Hatari, run `CLAIMC.PRG` on an 8 MHz
ST with `--monitor rgb` and `--monitor mono`. Trace
`psg_write,video_vbl` and compare the time spanned by the 40 R0 writes.
For `P32.PRG` and `P40.PRG`, use the RGB monitor and trace R6. The low
five bits should count from 1 to 20. At 8 MHz only `P40.PRG` writes
those values. At 16 MHz both programs write them.

~~~sh
work=$(mktemp -d)
python3 -B - "$work" <<'PY'
import json
from pathlib import Path
import sys

root = Path(sys.argv[1])

def tune(title, rows, registers):
    return dict(title=title, composer="", writer="", rate=50, rows=rows,
                repeat=0, sources=[], transformations=[],
                registers=registers)

def start(t, timer, target, count, values):
    n = len(t["sources"]) + 1
    name = "Steps" if target == 8 else f"Wave {n}"
    t["sources"].append(dict(name=name, repeat=0, values=values))
    op = dict(shape=0, source=n, target=target, prescaler=4, count=count,
              timerReset=1, placeReset=1, deferred=0, mapping=0)
    t["timer" + timer] = {k: [v] + [-1] * (t["rows"] - 1)
                          for k, v in op.items()}

def write(name, t):
    (root / (name + ".json")).write_text(
        json.dumps(dict(format="ymxs", version=7, tunes=[t])))

t = tune("Timer C effect", 48, dict(r0=list(range(30, 78))))
start(t, "C", 8, 240, [1, 2, 3, 4])
write("CLAIMC", t)

for count in (32, 40):
    t = tune("L32AD", 20, dict(r6=list(range(1, 21))))
    for i, timer in enumerate("AD"):
        start(t, timer, 14 + i, count, [[30 + i + j, 1 + i]
                                       for j in range(16)])
    write(f"P{count}", t)
PY
ymxs-to-prg -silent -r40 <"$work/CLAIMC.json" >"$work/CLAIMC.PRG"
for name in P32 P40; do
    ymxs-check <"$work/$name.json" >/dev/null
    ymxs-to-prg -silent -r20 <"$work/$name.json" >"$work/$name.PRG"
done
printf '%s\n' "$work"
~~~

## Reproduce the long sources

Run from the YMXR root with the released YMXS and YMXR tools on the path.
The probe uses the carried absolute core to compare the same timer
handlers at each size. It requires rmac and Python with Unicorn.

~~~sh
work=$(mktemp -d)
python3 -B - "$work" <<'PY'
import json
from pathlib import Path
import sys

root = Path(sys.argv[1])
for wide, sizes in ((False, (65536, 65537)), (True, (65534, 65535))):
    for size in sizes:
        start = dict(shape=0, source=1, target=14 if wide else 8,
                     prescaler=4, count=24, timerReset=1, placeReset=1,
                     deferred=0, mapping=0 if wide else [1])
        values = ([[200 + i % 8, 4 + i % 4] for i in range(size)] if wide
                  else [128 + 16 * (i % 8) for i in range(size)])
        source = dict(name="Sample", repeat=None, values=values)
        volume = dict(name="Volume", entries=[i >> 4 for i in range(256)])
        tune = dict(title="Sample", composer="", writer="", rate=50,
                    rows=4, repeat=0, registers={}, sources=[source],
                    transformations=[] if wide else [volume],
                    timerA={k: [v, -1, -1, -1] for k, v in start.items()})
        file = root / f"{'wide' if wide else 'counted'}-{size}.json"
        file.write_text(json.dumps(dict(format="ymxs", version=7,
                                        tunes=[tune])))
PY
for file in "$work"/*.json; do
    ymxs-check -silent <"$file" >/dev/null
    ymxs-to-sndh -silent -abs -carried <"$file" >"${file%.json}.sndh"
done

ulimit -c 0
python3 -B -X faulthandler - "$work" <<'PY'
from pathlib import Path
import sys

sys.path.insert(0, "68k/test/emu")
from test_ymxr import Machine, TIMER, assemble

for file in sorted(Path(sys.argv[1]).glob("*.sndh")):
    flags = ["-dYMXR_PCREL=0"]
    if file.name.startswith("counted"):
        flags.append("-dYMXR_MAP=1")
    code, symbols = assemble("YMXR_sndh.S", flags)
    data = file.read_bytes()
    core = data.index(b"YMXS", data.index(b"HDNS")) - 12
    assert data[core:core + 28] == code[:28]
    machine = Machine(data, {k: at + core for k, at in symbols.items()}, b"")
    machine.call("core-init", d0=1)
    machine.call("core-play")
    writes = []
    for _ in range(4):
        machine.interrupt(TIMER[0]["vector"])
        writes.append(machine.psg)
    print(file.name, writes)
PY
~~~

## Reproduce the UTF-8 signature

Set `YMXS_REPO` to the YMXS checkout.

~~~sh
work=$(mktemp -d)
input="$YMXS_REPO/doc/conformance/tunes/one-row.json"
ymxs-json-to-csv -silent <"$input" >"$work/plain.csv"
{ printf '\357\273\277'; cat "$work/plain.csv"; } >"$work/bom.csv"
{ printf '\357\273\277'; cat "$input"; } >"$work/bom.json"
ymxs-csv-to-json -silent <"$work/plain.csv" >/dev/null
ymxs-csv-to-json -silent <"$work/bom.csv" >/dev/null
ymxs-check -silent <"$work/bom.json" >/dev/null
ymxs-to-prg -silent <"$work/bom.json" >/dev/null
~~~

## Other coverage

- Both importers and the default YMXR PRG exporter completed on 544
  regular corpus files. The corpus sample is the `YM_CORPUS` collection
  described in [tools.md](tools.md).
- The linked emulator rig played 60 corpus tunes for up to 64 frames
  each, and generated seeds 20 to 119 covered 140 subtunes.
- The linked absolute player matched the reference on 120 generated
  tunes, covering 164 subtunes. A separate linked run matched through
  seed 167. At seed 168 the test generator stopped with a
  `KeyError: 'mapping'` before exporting the tune.
- For 11 YM fixtures, direct export to YMXR and export through YMXS
  produced identical bytes with each of `-k1`, `-k2`, and `-k4`.
  Three multitune or conformance fixtures produced identical native
  and WebAssembly PRGs with each of `-k1`, `-k4`, and `-m960`.
- A linked run from seeds 169 to 319 matched the reference on 214
  subtunes, including 555 retunes and 833 stops. The mixed-rate
  regression passed with SNDH callbacks and in Hatari. Hatari also
  wrote all 48 rows in order on each side of the 50/59 and 50/240 Hz
  test sets. Go and Java produced identical PRGs for the monochrome,
  timer load and 200 Hz number entry examples.
