"""A YM dump into the schema's columns, and what each column packs to.

Usage: convert.py pairs    - the tunes with a .ymx beside them, against it
       convert.py corpus   - every corpus tune, per-column breakdown
       convert.py envelope - the envelope columns, the reserved-0 design
                             against a plain 4-byte period column

The corpus comes from YM_CORPUS (measure.py). The st4 packer comes from ST4,
or from the path, and is built from YMX's go/cmd/st4.
"""
import os, re, subprocess, sys, tempfile, shutil, zlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import measure as M

# doc/SPEC.md section 1: width in bytes, one entry a column
WIDTH = [2,1, 2,1, 2,1, 1, 1, 1, 2] + [2,2]*4
NAMES = ["toneA","volA","toneB","volB","toneC","volC","mix","noise",
         "envshape","envperiod"] + [f"{w}{i}" for i in range(4) for w in ("fx","rate")]
ROW = sum(WIDTH)

# YM6 code nibble, type in bits 7-6 (YMX YmEffects.java) -> source number
YM_KIND = {0x00: 1, 0x40: 2, 0x80: 3, 0xC0: 4}
ST4 = os.environ.get("ST4", "st4")
RING = int(os.environ.get("ST4_RING", "0"))   # bytes; 0 leaves st4's default
PACKED = re.compile(rb"Packed (\d+) bytes into (\d+)")

def load(path, tmp):
    """(nf, get, ym6) for one corpus file, or None."""
    d = M.payload(path, tmp)
    got = M.regs(d) if d else None
    if not got:
        return None
    nf, g = got
    return nf, g, d[:4] == b"YM6!"

def effect_slots(r, ym6):
    """The two YM effect slots of one frame. YM6 gives each slot a kind in
    the code's bits 7-6; YM5 has no kind bits, its first slot is a SID
    voice and its second a digidrum (YMX, YmEffects.java)."""
    out = []
    for slot, (code_r, pre_r, cnt_r) in enumerate(((1, 6, 14), (3, 8, 15))):
        code = r[code_r] & 0xF0
        voice = ((code >> 4) & 3) - 1
        pre, cnt = r[pre_r] >> 5, r[cnt_r]
        if voice < 0 or pre == 0 or cnt == 0:
            out.append((0, 0, 0, 0, 0)); continue
        kind = YM_KIND[code & 0xC0] if ym6 else (1 if slot == 0 else 2)
        target = 13 if kind == 4 else 8 + voice
        out.append((kind, target, r[8 + voice] & 0x1F, pre, cnt))
    return out

def rows(nf, g, ym6):
    """The 18 column streams for one tune. An unset value is zero-filled:
    R3.6 does not read it, and zero packs smallest of the fills tried."""
    cols = [bytearray() for _ in WIDTH]
    prev = [None] * len(WIDTH)
    for f in range(nf):
        r = [g(i, f) for i in range(16)]
        fx = effect_slots(r, ym6)
        owned = {t for k, t, _, _, _ in fx if k}

        v = [0] * len(WIDTH)
        v[0] = ((r[1] & 15) << 8) | r[0]
        v[2] = ((r[3] & 15) << 8) | r[2]
        v[4] = ((r[5] & 15) << 8) | r[4]
        v[1], v[3], v[5] = r[8] & 0x1F, r[9] & 0x1F, r[10] & 0x1F
        v[6], v[7] = r[7] & 0x3F, r[6] & 0x1F
        v[8] = (r[13] & 15) if r[13] != 0xFF else None
        v[9] = (r[12] << 8) | r[11]
        for i, (kind, target, data, pre, cnt) in enumerate(fx):
            v[10 + 2*i] = (kind << 12) | (target << 8) | data if kind else 0
            # the dump's prescaler select is 1 to 7; SPEC 1.9's code is 0 to 6
            v[11 + 2*i] = (((pre - 1) & 7) << 8) | (cnt & 0xFF) if kind else 0

        out = [0] * len(WIDTH)
        for c in range(len(WIDTH)):
            if c == 8:
                set_it = v[8] is not None
                val = (v[8] or 0) | (0x40 if v[9] == 0 and prev[9] != 0 else 0)
                out[c] = (0x80 if set_it else 0) | val
                prev[c] = v[8]
                continue
            if c == 9:
                out[c] = v[9] if v[9] != prev[9] else 0
                prev[c] = v[9]
                continue
            if c in (1, 3, 5) and (8 + (c - 1)//2) in owned:
                out[c] = 0; prev[c] = None; continue
            top = 0x80 if WIDTH[c] == 1 else 0x8000
            out[c] = (top | v[c]) if v[c] != prev[c] else 0
            prev[c] = v[c]
        for c, w in enumerate(WIDTH):
            cols[c] += out[c].to_bytes(w, "big")
    return cols

def rows_env_plain(nf, g):
    """The envelope columns under the plain design: the shape without the
    period's bit, and a 4-byte period whose bit 31 is its set bit."""
    shape_col, period_col = bytearray(), bytearray()
    prev_shape, prev_period = None, None
    for f in range(nf):
        r13 = g(13, f)
        shape = (r13 & 15) if r13 != 0xFF else None
        period = (g(12, f) << 8) | g(11, f)
        shape_col += ((0x80 | shape) if shape is not None else 0).to_bytes(1, "big")
        period_col += ((0x80000000 | period) if period != prev_period else 0).to_bytes(4, "big")
        prev_shape, prev_period = shape, period
    return shape_col, period_col

def st4(data, unit, work):
    src, dst = os.path.join(work, "s.bin"), os.path.join(work, "s.st4")
    open(src, "wb").write(bytes(data))
    args = [ST4, "-f", f"-k{unit}"]
    if RING:
        args.append(f"-m{RING // unit}")
    r = subprocess.run(args + [src, dst], capture_output=True)
    m = PACKED.search(r.stdout)
    if not m:
        raise SystemExit(f"st4 did not run: {r.stderr.decode()[:200]}")
    return int(m.group(2)), os.path.getsize(dst)

def tune(nf, g, ym6, work, per=None):
    pay = whole = 0
    for c, col in enumerate(rows(nf, g, ym6)):
        p, w = st4(col, WIDTH[c], work)
        if per is not None: per[c] += p
        pay += p; whole += w
    return pay, whole

def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "pairs"
    work, tmp = tempfile.mkdtemp(), tempfile.mkdtemp()
    try:
        if mode == "pairs":
            D = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             "..", "..", "YMX", "ym", "test")
            D = os.environ.get("YMX_PAIRS", os.path.normpath(D))
            tf = to = tp = tw = n = 0
            for f in sorted(os.listdir(D)):
                if not f.endswith(".ymx"): continue
                ym = os.path.join(D, f[:-4] + ".ym")
                if not os.path.exists(ym): continue
                got = load(ym, tmp)
                if not got: continue
                nf, g, ym6 = got
                pay, whole = tune(nf, g, ym6, work)
                n += 1; tf += nf
                to += os.path.getsize(os.path.join(D, f)); tp += pay; tw += whole
            print(f"{n} tunes, {tf:,} frames")
            print(f"  YMX 0.7 files      {to:>10,}   {to/tf:5.2f} bytes a frame")
            print(f"  columns, ST4       {tp:>10,}   {tp/tf:5.2f}   {tp/to:.2f}x")
            print(f"  with containers    {tw:>10,}   {tw/tf:5.2f}   {tw/to:.2f}x")
        elif mode == "envelope":
            reserved = plain = 0
            max_sample = tunes = frames = 0
            for name in sorted(f for f in os.listdir(M.CORPUS)
                               if f.lower().endswith(".ym")):
                got = load(os.path.join(M.CORPUS, name), tmp)
                if not got: continue
                nf, g, ym6 = got
                tunes += 1; frames += nf
                cols = rows(nf, g, ym6)
                for c in (8, 9):
                    reserved += st4(cols[c], WIDTH[c], work)[0]
                sh, pe = rows_env_plain(nf, g)
                plain += st4(sh, 1, work)[0] + st4(pe, 4, work)[0]
                for f in range(nf):
                    r = [g(i, f) for i in range(16)]
                    for kind, target, data, pre, cnt in effect_slots(r, ym6):
                        if kind == 2:
                            max_sample = max(max_sample, data)
            print(f"{tunes} tunes, {frames:,} frames, ring {RING or 'unlimited'}")
            print(f"  envelope columns, 0 reserved and the shape's bit  {reserved:>9,}")
            print(f"  envelope columns, a plain 4-byte period           {plain:>9,}")
            print(f"  the reserved value saves                          {plain - reserved:>9,}")
            print(f"  largest sample number a tune names: {max_sample}")
        else:
            per = [0] * len(WIDTH)
            tf = pay = whole = n = 0
            for name in sorted(f for f in os.listdir(M.CORPUS)
                               if f.lower().endswith(".ym")):
                got = load(os.path.join(M.CORPUS, name), tmp)
                if not got: continue
                nf, g, ym6 = got
                p, w = tune(nf, g, ym6, work, per)
                n += 1; tf += nf; pay += p; whole += w
            print(f"{n} tunes, {tf:,} frames")
            print(f"  raw rows        {ROW*tf:>12,}   {ROW:.2f} bytes a frame")
            print(f"  ST4 payload     {pay:>12,}   {pay/tf:5.2f}   {ROW*tf/pay:5.1f}x")
            print(f"  with containers {whole:>12,}   {whole/tf:5.2f}   {ROW*tf/whole:5.1f}x")
            for c, nm in enumerate(NAMES):
                print(f"  {c:2} {nm:10} {WIDTH[c]}B  {per[c]:>9,}  {100.0*per[c]/pay:5.1f}%")
    finally:
        shutil.rmtree(work, ignore_errors=True)
        shutil.rmtree(tmp, ignore_errors=True)

if __name__ == "__main__":
    main()
