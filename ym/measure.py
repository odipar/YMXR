#!/usr/bin/env python3
"""How a dump's registers move from frame to frame, over the corpus.

Reads every dump under YM_CORPUS, a YM3!, YM3b, YM5! or YM6! file, packed
or plain, and prints over its frame steps: each voice's coarse tone byte
moving while its fine byte stays, its level moving, and its envelope bit
moving alone; the envelope shape's writes, and those that write the shape
standing; the tunes whose envelope period and whose noise period stay
throughout; the frames of envelope period 0, and those with a voice on the
envelope; the shape column's changes under three encodings of the period
bytes' bits; and each voice's fine tone byte moving to 0. doc/tools.md
18.7 has the list; ym/convert.py reads the corpus through this file's
reader, payload and regs.

    ym/measure.py
"""
import os, struct, subprocess, sys, tempfile, shutil

CORPUS = os.environ.get("YM_CORPUS",
                        os.path.expanduser("~/git/jatari/data/ym_format"))

def payload(path, tmp):
    for f in os.listdir(tmp):
        os.remove(os.path.join(tmp, f))
    r = subprocess.run(["7z", "x", "-y", f"-o{tmp}", path],
                       capture_output=True)
    files = os.listdir(tmp)
    if not files:                       # not an archive: some are raw dumps
        return open(path, "rb").read()
    return open(os.path.join(tmp, files[0]), "rb").read()

def regs(d):
    """(nframes, get(reg, frame)) for a YM3!/YM3b/YM5!/YM6! dump, or None.

    A YM3 dump is fourteen vectors of one register each after the four
    bytes of the format, and under YM3b a long after them, the frame it
    repeats to (the converter's reading, ymxs.md): R14 and R15, where a
    YM5 dump files its effects' counts, read 0."""
    if d[:4] in (b"YM3!", b"YM3b"):
        rest = len(d) - 4 - (4 if d[:4] == b"YM3b" else 0)
        if rest <= 0 or rest % 14:
            return None
        nf = rest // 14
        block = d[4:4 + 14 * nf]
        return nf, lambda r, f: block[r * nf + f] if r < 14 else 0
    if d[:4] not in (b"YM5!", b"YM6!") or d[4:12] != b"LeOnArD!":
        return None
    nf   = struct.unpack(">I", d[12:16])[0]
    attr = struct.unpack(">I", d[16:20])[0]
    ndd  = struct.unpack(">H", d[20:22])[0]
    at = 34 + struct.unpack(">H", d[32:34])[0]
    for _ in range(ndd):                      # digidrum samples
        at += 4 + struct.unpack(">I", d[at:at + 4])[0]
    for _ in range(3):                        # name, author, comment
        at = d.index(b"\0", at) + 1
    block = d[at:at + 16 * nf]
    if len(block) < 16 * nf:
        return None
    inter = bool(attr & 1)
    if inter:
        return nf, lambda r, f: block[r * nf + f]
    return nf, lambda r, f: block[f * 16 + r]

# YM5/YM6 keep effect flags in the spare bits of these registers.
MASK = {1: 0x0F, 3: 0x0F, 5: 0x0F, 6: 0x1F,
        8: 0x1F, 9: 0x1F, 10: 0x1F, 13: 0xFF}
def m(r, v):
    return v & MASK.get(r, 0xFF)

def main():
    tmp = tempfile.mkdtemp()
    files = sorted(f for f in os.listdir(CORPUS) if f.lower().endswith(".ym"))
    read = unread = 0
    frames = 0
    coarse_alone = [0, 0, 0]        # per voice: coarse moved, fine did not
    follow_alone = [0, 0, 0]        # bit 4 moved, level did not
    level_moved  = [0, 0, 0]
    env_writes = env_restates = 0   # R13 != $FF ; and same shape as the last write
    tunes_env_fixed = tunes_noise_fixed = 0
    p0_frames = p0_live = 0         # envelope period 0; and with a voice following
    p0_tunes = set()
    churn_plain = churn_beside = churn_host = 0  # shape-column value changes:
        # the column alone; with the bits beside the period bytes (SPEC
        # 1.7); and with a set bit for each period byte in it instead
    fine_zero = [0, 0, 0]           # tone fine byte moved to 0: the coarse column's bit 6

    for name in files:
        d = payload(os.path.join(CORPUS, name), tmp)
        got = regs(d) if d else None
        if not got:
            unread += 1
            continue
        read += 1
        nf, g = got
        frames += nf - 1
        env_period_moved = noise_moved = False
        last_shape = None
        shape = 0
        prev_plain = prev_beside = prev_host = None
        for f in range(nf):
            if f:
                for v, (lo, hi) in enumerate(((0, 1), (2, 3), (4, 5))):
                    fine_d   = m(lo, g(lo, f)) != m(lo, g(lo, f - 1))
                    coarse_d = m(hi, g(hi, f)) != m(hi, g(hi, f - 1))
                    if coarse_d and not fine_d:
                        coarse_alone[v] += 1
                for v, r in enumerate((8, 9, 10)):
                    now, was = m(r, g(r, f)), m(r, g(r, f - 1))
                    if (now & 0x0F) != (was & 0x0F):
                        level_moved[v] += 1
                    elif (now & 0x10) != (was & 0x10):
                        follow_alone[v] += 1
                if g(11, f) != g(11, f - 1) or g(12, f) != g(12, f - 1):
                    env_period_moved = True
                if m(6, g(6, f)) != m(6, g(6, f - 1)):
                    noise_moved = True
            period = (g(12, f) << 8) | g(11, f)
            if period == 0:
                p0_frames += 1
                if any(m(r, g(r, f)) & 0x10 for r in (8, 9, 10)):
                    p0_live += 1
                    p0_tunes.add(name)
            if f:
                fine_moved = g(11, f) != g(11, f - 1)
                coarse_moved = g(12, f) != g(12, f - 1)
                if g(13, f) != 0xFF:
                    shape = g(13, f) & 0x0F
                plain = (0x80 if g(13, f) != 0xFF else 0) | shape
                beside = plain | (0x40 if fine_moved and g(11, f) == 0 else 0) \
                             | (0x20 if coarse_moved and g(12, f) == 0 else 0)
                host = plain | (0x40 if fine_moved else 0) | (0x20 if coarse_moved else 0)
                if prev_plain is not None and plain != prev_plain:
                    churn_plain += 1
                if prev_beside is not None and beside != prev_beside:
                    churn_beside += 1
                if prev_host is not None and host != prev_host:
                    churn_host += 1
                prev_plain, prev_beside, prev_host = plain, beside, host
                for v, lo in enumerate((0, 2, 4)):
                    if g(lo, f) == 0 and g(lo, f - 1) != 0:
                        fine_zero[v] += 1
            shape = g(13, f)
            if shape != 0xFF:
                env_writes += 1
                if shape == last_shape:
                    env_restates += 1
                last_shape = shape
        tunes_env_fixed   += not env_period_moved
        tunes_noise_fixed += not noise_moved

    shutil.rmtree(tmp, ignore_errors=True)
    pc = lambda n: f"{100.0 * n / frames:.2f}%"
    print(f"read {read} of {read + unread} files, {frames:,} frame steps")
    print(f"tone coarse moved alone   A {pc(coarse_alone[0])}  B {pc(coarse_alone[1])}  C {pc(coarse_alone[2])}")
    print(f"volume level moved        A {pc(level_moved[0])}  B {pc(level_moved[1])}  C {pc(level_moved[2])}")
    print(f"follow bit moved alone    A {pc(follow_alone[0])}  B {pc(follow_alone[1])}  C {pc(follow_alone[2])}")
    print(f"envelope shape writes     {env_writes:,}; of those already standing {env_restates:,}"
          f" ({100.0 * env_restates / env_writes:.1f}%)")
    print(f"tunes with one envelope period throughout  {tunes_env_fixed} of {read}")
    print(f"tunes with one noise period throughout     {tunes_noise_fixed} of {read}")
    print(f"envelope period 0 frames  {p0_frames:,}; with a voice following"
          f" {p0_live} ({len(p0_tunes)} tunes)")
    print(f"shape column value changes  the column {churn_plain:,}; with the bits beside"
          f" the period bytes {churn_beside:,}; hosting their set bits {churn_host:,}")
    print(f"tone fine byte moved to 0  A {fine_zero[0]:,}  B {fine_zero[1]:,}"
          f"  C {fine_zero[2]:,} frames")


if __name__ == "__main__":
    main()
