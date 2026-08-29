"""A YM dump into the schema's columns, and what each column packs to.

Usage: convert.py pairs   - the tunes with a .ymx beside them, against it
       convert.py corpus  - every corpus tune, per-column breakdown

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

def rows(nf, g):
    """The 18 column streams for one tune. An unset column is zero-filled:
    R3.6 leaves its bytes undefined, and zero packs best (experiments.md)."""
    cols = [bytearray() for _ in WIDTH]
    prev = [None] * len(WIDTH)
    for f in range(nf):
        r = [g(i, f) for i in range(16)]
        fx = []
        for slot, (code_r, pre_r, cnt_r) in enumerate(((1, 6, 14), (3, 8, 15))):
            code = r[code_r] & 0xF0
            voice = ((code >> 4) & 3) - 1
            pre, cnt = r[pre_r] >> 5, r[cnt_r]
            if voice < 0 or pre == 0 or cnt == 0:
                fx.append((0, 0, 0, 0, 0)); continue
            kind = YM_KIND[code & 0xC0]
            target = 13 if kind == 4 else 8 + voice
            fx.append((kind, target, r[8 + voice] & 0x1F, pre, cnt))
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
            v[11 + 2*i] = ((pre & 7) << 8) | (cnt & 0xFF) if kind else 0

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

def tune(nf, g, work, per=None):
    pay = whole = 0
    for c, col in enumerate(rows(nf, g)):
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
                got = M.regs(M.payload(ym, tmp) or b"")
                if not got: continue
                nf, g = got
                pay, whole = tune(nf, g, work)
                n += 1; tf += nf
                to += os.path.getsize(os.path.join(D, f)); tp += pay; tw += whole
            print(f"{n} tunes, {tf:,} frames")
            print(f"  YMX 0.7 files      {to:>10,}   {to/tf:5.2f} bytes a frame")
            print(f"  columns, ST4       {tp:>10,}   {tp/tf:5.2f}   {tp/to:.2f}x")
            print(f"  with containers    {tw:>10,}   {tw/tf:5.2f}   {tw/to:.2f}x")
        else:
            per = [0] * len(WIDTH)
            tf = pay = whole = n = 0
            for name in sorted(f for f in os.listdir(M.CORPUS)
                               if f.lower().endswith(".ym")):
                got = M.regs(M.payload(os.path.join(M.CORPUS, name), tmp) or b"")
                if not got: continue
                nf, g = got
                p, w = tune(nf, g, work, per)
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
