#!/usr/bin/env python3
"""A tune a timer drives a whole byte with, for hearing what versions 5
and 6 encode (SPEC.md 3.1.6).

  python3 ym/whole-byte.py | bin/ymxs-to-prg -r4800 > dist/whole/TUNE.PRG
  ym/hatari.sh dist/whole

The marker is in bit 7 of a row, so until version 5 a source drove
only a register that reads seven bits or fewer: a volume, a coarse
nibble, the envelope shape. A counted source has no marker and its rows
are whole bytes, which opens the six registers that read all eight -
the three tone fine bytes, the mixer and the envelope period's two bytes
(SPEC.md 2.1.2) - and version 6 counts such a source of several columns,
so one timer drives the envelope period's two bytes together (SPEC.md
2.1.3). No dump converts to one, since a YM file records the registers a
frame writes and names no effect, so a tune that spends this is written
through the structure, as this one is.

Six sections of 800 rows at 50 Hz, 16 seconds each, with one melody on
voice B under all six:

  1. voice A at 440 Hz, written by rows alone: the reference
  2. setR0: the tone's fine byte swept 24 times a second, then 96
  3. setR4: voice C brought in and swept 96 times a second, then 384
  4. setR11: voice A on the envelope, its period's low byte swept 5
     times a second, 1,953 Hz down to 30
  5. setR7: the mixer gating voice A's tone 192 times a second
  6. setEnvelope: both period bytes from one timer, 122 Hz down to one
     cycle in 8.39 seconds

The note a sweep drives is at a period of 256 to 511, a coarse byte
of 1, so a fine byte over its whole range moves the pitch by an octave:
at a lower note the coarse byte would swamp the sweep.

experiments.md reads what the six sections come to.
"""
import json

ROWS_A_SECTION = 800
SECTIONS = 6
ROWS = ROWS_A_SECTION * SECTIONS
RATE = 50


def period(hz):
    """The period the chip counts for a frequency: 2,000,000 over sixteen
    times it (terminology.md 1.4)."""
    return int(round(2000000.0 / (16.0 * hz)))


# a minor pentatonic over two octaves, for the melody on voice B
NOTES = [220.0 * r for r in (1, 1.2, 1.335, 1.5, 1.78, 2, 2.4, 2.67, 3, 3.56)]
MELODY = [0, 2, 4, 3, 5, 4, 2, 1, 0, 2, 4, 6, 5, 4, 3, 2]

# The mixer: voice A's tone and voice B's, no noise, voice C off; the
# third section brings voice C in, and the fifth gates voice A.
MIXER = 0b111100
MIXER_C = 0b111000

DRONE = period(440.0)
VOICE_C = period(440.0)

r = {n: [-1] * ROWS for n in range(14)}
timer = {k: [-1] * ROWS for k in
         ("shape", "target", "source", "prescaler", "count", "timerReset", "placeReset")}


def at(row, **registers):
    for name, value in registers.items():
        r[int(name[1:])][row] = value


def start(row, target, source, prescaler, count):
    """A row that starts a source on Timer A (json.md 6.2)."""
    timer["shape"][row] = 0
    timer["target"][row] = target
    timer["source"][row] = source
    timer["prescaler"][row] = prescaler
    timer["count"][row] = count
    timer["timerReset"][row] = 1
    timer["placeReset"][row] = 1


def retune(row, prescaler, count):
    timer["shape"][row] = 1
    timer["prescaler"][row] = prescaler
    timer["count"][row] = count
    timer["timerReset"][row] = 1
    timer["placeReset"][row] = 0


def stop(row):
    timer["shape"][row] = 2


# The melody, a note every twelve rows through the whole piece.
for row in range(0, ROWS, 12):
    p = period(NOTES[MELODY[(row // 12) % len(MELODY)]])
    at(row, r2=p & 255, r3=p >> 8)

# 1. The mixer, the two volumes, and voice A's note, by rows alone.
at(0, r7=MIXER, r8=13, r9=11, r0=DRONE & 255, r1=DRONE >> 8, r12=0)

# 2. The tone's fine byte swept: 32 rows of a ramp at 768 ticks a second,
#    so the pitch travels the octave 24 times a second, and four times as
#    fast from the halfway retune.
sweep = [(i * 8) & 255 for i in range(32)]
start(ROWS_A_SECTION, 0, 1, 64, 50)
retune(ROWS_A_SECTION + 400, 16, 50)
stop(2 * ROWS_A_SECTION - 1)

# 3. Voice C's fine byte swept, four times as fast as the section above,
#    with voice A at its note under it.
lead = [(i * 16) & 255 for i in range(16)]
at(2 * ROWS_A_SECTION, r0=DRONE & 255, r1=DRONE >> 8, r8=13,
   r7=MIXER_C, r10=12, r5=VOICE_C >> 8)
start(2 * ROWS_A_SECTION + 2, 4, 2, 16, 100)
retune(2 * ROWS_A_SECTION + 400, 16, 25)
stop(3 * ROWS_A_SECTION - 1)

# 4. The envelope period swept: voice A's tone silenced and its volume on
#    the envelope, whose period's low byte is ramped and whose shape is
#    struck every 50 rows. The envelope's frequency is 2,000,000 over 256
#    times the period, so a low byte of 4 to 255 sweeps 1,953 Hz to 30.
at(3 * ROWS_A_SECTION, r7=0b111101, r8=16, r10=0, r12=0, r13=10)
start(3 * ROWS_A_SECTION + 2, 11, 3, 64, 120)
for row in range(3 * ROWS_A_SECTION + 50, 4 * ROWS_A_SECTION, 50):
    at(row, r13=10)
stop(4 * ROWS_A_SECTION - 1)

# 5. The mixer gated: two rows, voice A's tone on and off, at 384 ticks a
#    second. The structure's rows are the mixer's six bits and the writer
#    sets the two port directions (SPEC.md rule 2(f)).
gate = [MIXER, MIXER | 1]
at(4 * ROWS_A_SECTION, r7=MIXER, r8=13, r0=DRONE & 255, r1=DRONE >> 8, r11=0)
start(4 * ROWS_A_SECTION + 2, 7, 4, 64, 100)
stop(5 * ROWS_A_SECTION - 1)

# 6. The envelope period over its whole range, both bytes from one timer:
#    a counted source of two columns on setEnvelope, whose 512 rows step
#    the period from 64 to 65,535 at 64 ticks a second, ten octaves in
#    eight seconds under voice A's tone. The source plays once, so the
#    last row's period remains for the eight seconds after it: one cycle
#    in 8.39 seconds, and the half above 32,767 is the half a marked
#    source leaves unreachable (SPEC.md 2.1.3, 2.1.4).
DESCENT_ROWS = 512
descent = []
for i in range(DESCENT_ROWS):
    p = int(round(64 * (65535.0 / 64) ** (i / float(DESCENT_ROWS - 1))))
    descent.append([p & 255, p >> 8])
at(5 * ROWS_A_SECTION, r7=MIXER, r8=16, r0=DRONE & 255, r1=DRONE >> 8, r13=10)
start(5 * ROWS_A_SECTION + 2, 20, 5, 200, 192)

print(json.dumps({"format": "ymxs", "version": 4, "tunes": [{
    "title": "A timer on a whole byte", "composer": "",
    "writer": "ym/whole-byte.py", "rate": RATE, "rows": ROWS, "repeat": 0,
    "sources": [
        {"name": "the fine byte swept", "repeat": 0, "values": sweep},
        {"name": "voice C's fine byte swept", "repeat": 0, "values": lead},
        {"name": "the envelope period swept", "repeat": 0,
         "values": [(i * 4 + 4) & 255 for i in range(64)]},
        {"name": "the mixer gating voice A", "repeat": 0, "values": gate},
        {"name": "the envelope period over its whole range", "repeat": None,
         "values": descent}],
    "registers": {"r%d" % n: r[n] for n in range(14)},
    "timerA": timer}]}))
