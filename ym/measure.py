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
    """(nframes, get(reg, frame)) for a YM5!/YM6! dump, or None."""
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
    churn_host = churn_own = 0      # shape-column value changes, hosting the
                                    # period's set bit against not

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
        held = 0
        prev_host = prev_own = None
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
                moved = (g(11, f) != g(11, f - 1)) or (g(12, f) != g(12, f - 1))
                if g(13, f) != 0xFF:
                    held = g(13, f) & 0x0F
                wrote = g(13, f) != 0xFF
                host = (0x80 if wrote else 0) | (0x40 if moved else 0) | held
                own = (0x80 if wrote else 0) | held
                if prev_host is not None and host != prev_host:
                    churn_host += 1
                if prev_own is not None and own != prev_own:
                    churn_own += 1
                prev_host, prev_own = host, own
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
    print(f"shape column value changes  hosting the period's set bit {churn_host:,},"
          f" its own {churn_own:,}, extra {churn_host - churn_own:,}")


if __name__ == "__main__":
    main()
