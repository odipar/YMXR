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
