"""XPATCH.PRG, assembled from XPATCH.S beside this file (README.md).

Usage: build.py [out.prg]       XPATCH.PRG beside this file by default

rmac writes the program, with every optimisation off so that each
instruction is assembled in the form the source writes: a case is the
order and the form of its instructions. Needs rmac (RMAC, or on the
path). Prints the program's path and its size in bytes."""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RMAC = os.environ.get("RMAC", "rmac")
SOURCE = "XPATCH.S"


def build(out):
    """The program assembled to `out`, and its size in bytes. rmac runs in
    this folder on the source's name: rmac 2.4.3 stops with SIGTRAP on a
    source path of 128 bytes or more."""
    out = os.path.abspath(out)
    r = subprocess.run([RMAC, "-m68000", "-p", "~oall", "-o", out, SOURCE],
                       cwd=HERE, capture_output=True)
    if r.returncode or not os.path.isfile(out):
        raise SystemExit("rmac fails on %s, exit %d:\n%s%s" % (
            os.path.join(HERE, SOURCE), r.returncode, r.stdout.decode(),
            r.stderr.decode()))
    return os.path.getsize(out)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "XPATCH.PRG")
    print("%s, %d bytes" % (out, build(out)))
