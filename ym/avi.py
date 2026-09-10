#!/usr/bin/env python3
"""The sound out of a Hatari recording, as a WAV file.

Hatari records to AVI with `--avirecord`, video and sound together
(ym/play.sh). This reads the sound chunks out of one and writes them as
a WAV, so that a run needs no converter beside the emulator. It reads
the chunks in order rather than the index, and reads them unpadded,
which is how Hatari writes them.

    ym/avi.py in.avi out.wav [frame.png]

A third name writes one video frame, the last of the recording, as the
PNG Hatari stored it.
"""
import re
import struct
import sys

RATE = 44100                    # what ym/play.sh asks the emulator for
CHUNK = re.compile(rb"(\d\d(dc|wb)|LIST|idx1|JUNK)")


def chunks(data):
    """Every chunk of the recording's data section: its name and bytes."""
    at = data.find(b"movi") + 4
    while at + 8 <= len(data):
        name = data[at:at + 4]
        if not CHUNK.fullmatch(name):
            at += 1
            continue
        size = struct.unpack("<I", data[at + 4:at + 8])[0]
        yield name, data[at + 8:at + 8 + size]
        at += 8 + size


def wav(sound):
    """The sound as a WAV file's bytes: two channels, sixteen bits."""
    return (b"RIFF" + struct.pack("<I", 36 + len(sound)) + b"WAVEfmt "
            + struct.pack("<IHHIIHH", 16, 1, 2, RATE, RATE * 4, 4, 16)
            + b"data" + struct.pack("<I", len(sound)) + sound)


def main():
    if len(sys.argv) not in (3, 4):
        print(__doc__.strip())
        return 2
    data = open(sys.argv[1], "rb").read()
    sound, frames = bytearray(), []
    for name, body in chunks(data):
        if name[2:] == b"wb":
            sound += body
        elif body[:4] == b"\x89PNG":
            frames.append(body)
    if not sound:
        print("the recording has no sound")
        return 1
    open(sys.argv[2], "wb").write(wav(bytes(sound)))
    print("%s: %.2f seconds, %d frames" % (sys.argv[2], len(sound) / 4 / RATE, len(frames)))
    if len(sys.argv) == 4 and frames:
        open(sys.argv[3], "wb").write(frames[-1])
    return 0


if __name__ == "__main__":
    sys.exit(main())
