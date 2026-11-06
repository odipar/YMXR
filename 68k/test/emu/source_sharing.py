"""Generate a mixed-rate YMXS set with shared sources and transformations."""

import argparse
import json
import sys


def source_sharing(sample_rows=4096, rows=96):
    sample = []
    for i in range(sample_rows):
        phase = i % 63
        level = phase - 16 if phase < 32 else 47 - phase
        sample.append(128 + int(level * 7 * (sample_rows - i) / sample_rows))
    wave = [min(i % 128, 127 - i % 128) // 4 for i in range(512)]
    entries = [i >> 4 for i in range(256)]

    def columns(**values):
        return {name: [value] + [-1] * (rows - 1) for name, value in values.items()}

    def timer(target, source, count, mapping):
        return columns(shape=0, target=target, source=source, prescaler=4,
                       count=count, timerReset=1, placeReset=1, deferred=0,
                       mapping=mapping)

    tunes = []
    for i in range(6):
        registers = columns(r4=66 + i * 13, r5=1 + i % 2, r7=59, r10=9 + i % 4)
        registers["r4"][32] = 80 + i * 9
        registers["r4"][64] = 98 + i * 11
        # The last tune changes both the wavetable loop and the lookup page list.
        last = i == 5
        tunes.append({
            "title": f"Shared sources {i + 1}",
            "composer": "",
            "writer": "synthetic source sharing fixture",
            "rate": 50 if i % 2 == 0 else 60,
            "rows": rows,
            "repeat": None,
            "sources": [
                {"name": "shared sample", "repeat": None, "values": sample},
                {"name": "shared wavetable", "repeat": 16 if last else 0, "values": wave},
            ],
            "transformations": [{"name": "sample to volume", "entries": entries}],
            "registers": registers,
            "timerD" if last else "timerA": timer(8, 1, 153, [1]),
            "timerB": timer(9, 2, 48, 0),
        })
    return {"format": "ymxs", "version": 7, "tunes": tunes}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sample-rows", type=int, default=4096)
    args = parser.parse_args()
    json.dump(source_sharing(args.sample_rows), sys.stdout)
    print()
