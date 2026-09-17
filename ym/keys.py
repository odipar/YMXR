#!/usr/bin/env python3
"""The program's keys, pressed under Hatari.

BINARIES.md 4.6 step 4 defines how a program picks a subtune: LEFT and UP
step back, RIGHT and DOWN step on, both wrapping, and a typed number reaches
any subtune of the set, one digit or two. Until this rig every check read
those keys out of the stub's source rather than pressing them.

It builds a program of twelve subtunes, each writing the number it is to
R0 - tune i writes 16i and 16i + 1 - so the register trace says which
subtune is playing at any moment. Then it starts Hatari with a command fifo, presses
keys through it, and reads the trace back.

    python3 ym/keys.py [--keep]

HATARI and TOS name the emulator and a TOS image, as ym/hatari.sh reads
them. --keep leaves the work directory behind.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
HATARI = os.environ.get('HATARI', 'hatari')
# Hatari ends the run itself at this many VBLs, so no dialog is opened and
# no key of the host's is pressed: 50 a second, where the keys below run
# for ten.
VBLS = 900
TOS = os.environ.get('TOS', str(Path.home() / 'hatari-2.6.1_macos/tos-2.06.rom'))
TUNES = 12

# Hatari reads a one-character argument as that character and a longer one
# as an ST scancode, so a digit goes in as itself and a key with no
# character goes in as the scancode BINARIES.md 4.6 names:
# LEFT $4B, RIGHT $4D, UP $48, DOWN $50, SPACE $39.
KEY = {'LEFT': '75', 'RIGHT': '77', 'UP': '72', 'DOWN': '80', 'SPACE': '57'}


def program(work: Path) -> Path:
    """A program of twelve subtunes, each known by what it writes to R0."""
    tunes = [{'title': f'tune {i:02d}', 'composer': '', 'writer': 'ym/keys.py',
              'rate': 50, 'rows': 2, 'repeat': 0, 'sources': [],
              'registers': {'r0': [16 * i, 16 * i + 1], 'r7': [63, 63]}}
             for i in range(1, TUNES + 1)]
    multi = {'format': 'ymxs', 'version': 3, 'tunes': tunes}
    at = work / 'TUNE.PRG'
    made = subprocess.run([REPO / 'bin' / 'ymxs-to-prg', '-silent', '-tKEYS'],
                          input=json.dumps(multi).encode(), capture_output=True)
    if made.returncode:
        raise SystemExit('the twelve subtunes did not build: '
                         + made.stderr.decode())
    at.write_bytes(made.stdout)
    return at


def playing(trace: Path) -> list:
    """The subtunes the trace shows, in the order they played."""
    seen = []
    if not trace.exists():
        return seen
    for value in re.findall(r'ym write data reg=0x0 val=0x([0-9a-f]+)',
                            trace.read_text(errors='replace')):
        subtune = int(value, 16) >> 4
        if 1 <= subtune <= TUNES and (not seen or seen[-1] != subtune):
            seen.append(subtune)
    return seen


def main() -> int:
    if not shutil.which(HATARI):
        print(f'ym/keys.py: no {HATARI} on the path')
        return 2
    if not Path(TOS).exists():
        print(f'ym/keys.py: no TOS image at {TOS}')
        return 2
    work = Path(tempfile.mkdtemp(prefix='ymxr-keys-'))
    program(work)
    fifo, trace = work / 'cmd', work / 'trace.txt'
    hatari = subprocess.Popen(
        [HATARI, '--tos', TOS, '--machine', 'st', '--cpuclock', '8',
         '--cpu-exact', 'on', '--compatible', 'on', '--memsize', '4',
         '--sound', 'off', '--log-level', 'fatal', '--alert-level', 'fatal',
         '--confirm-quit', 'off', '--run-vbls', str(VBLS),
         '--cmd-fifo', str(fifo),
         '--trace', 'psg_write', '--trace-file', str(trace),
         str(work / 'TUNE.PRG')],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def press(*keys):
        with fifo.open('w') as to:
            for key in keys:
                to.write(f'hatari-event keypress {key}\n')
                to.flush()
                time.sleep(0.1)

    def waits_for(subtune, seconds=6.0):
        """Whether that subtune is the one playing before the time runs out.
        The trace is the answer, so a rig reads what the chip was written
        rather than what a sleep hoped for."""
        until = time.time() + seconds
        while time.time() < until:
            seen = playing(trace)
            if seen and seen[-1] == subtune:
                return True
            time.sleep(0.1)
        return False

    # The steps, each a claim of BINARIES.md 4.6 step 4 and the subtune it
    # leaves playing.
    steps = [('the program starts on subtune 1', (), 1),
             ('RIGHT steps on', (KEY['RIGHT'],), 2),
             ('DOWN steps on', (KEY['DOWN'],), 3),
             ('LEFT steps back', (KEY['LEFT'],), 2),
             ('UP steps back', (KEY['UP'],), 1),
             ('LEFT wraps to the last subtune', (KEY['LEFT'],), TUNES),
             ('RIGHT wraps to the first', (KEY['RIGHT'],), 1),
             ('two digits reach subtune 12', ('1', '2'), 12),
             ('a digit no second can grow starts at once', ('9',), 9)]
    wrong = []
    if not waits_for(1, seconds=40.0):      # TOS, then the program's banner
        print('ym/keys.py: the program did not reach subtune 1 under Hatari')
        hatari.kill()
        return 2
    for what, keys, expect in steps:
        if keys:
            press(*keys)
        if not waits_for(expect):
            seen = playing(trace)
            wrong.append(f'{what}: subtune {seen[-1] if seen else "none"}'
                         f' plays, not {expect}')
    last = playing(trace)[-1]
    press(KEY['SPACE'])
    time.sleep(2.0)
    ended = playing(trace)
    # Hatari ends the run at its VBL count. A run that outlives it by a
    # margin is killed rather than left behind.
    try:
        hatari.wait(timeout=VBLS / 50.0 + 15)
    except subprocess.TimeoutExpired:
        hatari.kill()
        hatari.wait(timeout=10)
        print('ym/keys.py: Hatari was killed rather than ending at its VBLs')
    if ended and ended[-1] != last:
        wrong.append(f'SPACE left subtune {ended[-1]} playing')
    print(f'the subtunes played, in order: {playing(trace)}')
    for line in wrong:
        print('FAIL', line)
    if not wrong:
        print(f'OK  every key of BINARIES.md 4.6 step 4, over {TUNES} subtunes')
    if '--keep' in sys.argv:
        print(f'the work stands under {work}')
    else:
        shutil.rmtree(work, ignore_errors=True)
    return 1 if wrong else 0


if __name__ == '__main__':
    sys.exit(main())
