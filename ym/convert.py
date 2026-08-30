"""A YM dump into the schema's columns, and what each column packs to.

Usage: convert.py pairs    - the tunes with a .ymx beside them, against it
       convert.py corpus   - every corpus tune, per-column breakdown
       convert.py envelope - the envelope columns, the reserved-0 design
                             against a plain 4-byte period column
       convert.py frame    - what a frame procedure has to do, per frame
       convert.py dtx2     - one ST4 unit for every column, as DTX2 asks,
                             at each of the three units, whole DTX2 files

The corpus comes from YM_CORPUS (measure.py). The st4 packer comes from ST4,
or from the path, and is built from YMX's go/cmd/st4.
"""
import os, re, subprocess, sys, tempfile, shutil, zlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import measure as M

# doc/SPEC.md section 1: width in bytes, one entry a column
WIDTH = [2,1, 2,1, 2,1, 1, 1, 1, 2] + [2,2]*4
NAMES = ["toneA","volA","toneB","volB","toneC","volC","mix","noise",
         "envshape","envperiod"] + [f"{w}{i}" for i in range(4)
                                    for w in ("effect", "rate")]
ROW = sum(WIDTH)

# YM6 code nibble, type in bits 7-6 (YMX YmEffects.java) -> source number
YM_KIND = {0x00: 1, 0x40: 2, 0x80: 3, 0xC0: 4}
ST4 = os.environ.get("ST4", "st4")
RING = int(os.environ.get("ST4_RING", "0"))   # bytes; 0 leaves st4's default
# One unit for every column of a payload (DTX, R5.2). 0 packs each column
# at its own width instead, which DTX2 does not admit.
UNIT = int(os.environ.get("ST4_UNIT", "0"))
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

def rows(nf, g, ym6, census=None):
    """The 18 column streams for one tune. An unset value is zero-filled:
    R3.6 does not read it, and zero packs smallest of the fills tried.

    An effect column holds a target and a source number (SPEC 1.8). A YM
    dump names an effect by kind and by a value out of a volume register,
    so each distinct pair becomes a source of the tune's own, numbered
    from 1 as it is first met."""
    cols = [bytearray() for _ in WIDTH]
    prev = [None] * len(WIDTH)
    sources = {}
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
            if kind:
                number = sources.setdefault((kind, data), len(sources) + 1)
                v[10 + 2*i] = (target << 8) | (number & 0xFF)
            else:
                v[10 + 2*i] = 0
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
    if census is not None:
        census.append(len(sources))
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

# What one set column costs the frame procedure, in writes (SPEC section 4).
# A tone period and the envelope period reach two registers; the rest one.
# A rate reaches its timer's control and data registers.
YM_WRITES = {0: 2, 2: 2, 4: 2, 1: 1, 3: 1, 5: 1, 6: 1, 7: 1, 8: 1, 9: 2}
MFP_WRITES = {11: 2, 13: 2, 15: 2, 17: 2}
EFFECTS = (10, 12, 14, 16)

def frame_work(nf, g, ym6):
    """Per frame: (columns set, YM register writes, MFP writes, effects
    started or stopped). Read from the columns the converter writes, so it
    is the same rows the packing figures measure."""
    cols = rows(nf, g, ym6)
    at = [0] * len(WIDTH)
    out = []
    for f in range(nf):
        set_columns = ym = mfp = fx = 0
        for c, w in enumerate(WIDTH):
            v = int.from_bytes(cols[c][f * w:(f + 1) * w], "big")
            if c == 9:
                on = v != 0                      # the reserved 0 (1.7)
            elif w == 1:
                on = bool(v & 0x80)
            else:
                on = bool(v & 0x8000)
            if not on:
                continue
            set_columns += 1
            ym += YM_WRITES.get(c, 0)
            mfp += MFP_WRITES.get(c, 0)
            if c in EFFECTS:
                fx += 1
        out.append((set_columns, ym, mfp, fx))
    return out

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

# DTX SPEC.md 1 and 2.3: the header is 14 plus `C` rounded up to a long,
# and a DTX2 payload opens with `N`, `k`, a zero byte and an offset a
# column, then the data sets, each beginning on a long.
def dtx_header(columns):
    return -(-(14 + columns) // 4) * 4

def dtx2_file(sizes):
    at = 4 + 4 * len(sizes)
    for w in sizes:
        at = -(-at // 4) * 4 + w
    return dtx_header(len(sizes)) + at

def padded(cols, nf, k):
    """R rows up to a multiple of k (DTX R5.6), the last frame repeated."""
    if nf % k == 0:
        return cols, nf
    more = k - nf % k
    out = []
    for c, col in enumerate(cols):
        w = WIDTH[c]
        out.append(col + col[-w:] * more)
    return out, nf + more

def tune_at(nf, g, ym6, work, k, per=None):
    """One tune as a DTX2 file with every column packed at unit k."""
    cols, rn = padded(list(rows(nf, g, ym6)), nf, k)
    sizes = []
    for c, col in enumerate(cols):
        p, w = st4(col, k, work)
        if per is not None:
            per[c] += p
        sizes.append(w)
    return dtx2_file(sizes), rn

def tune(nf, g, ym6, work, per=None, census=None):
    pay = whole = 0
    for c, col in enumerate(rows(nf, g, ym6, census)):
        p, w = st4(col, UNIT or WIDTH[c], work)
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
                # one unit for every column of a payload (DTX, R5.2), and
                # k = 1 is what the corpus packs smallest at
                for c in (8, 9):
                    reserved += st4(cols[c], 1, work)[0]
                sh, pe = rows_env_plain(nf, g)
                plain += st4(sh, 1, work)[0] + st4(pe, 1, work)[0]
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
        elif mode == "dtx2":
            D = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             "..", "..", "YMX", "ym", "test")
            D = os.environ.get("YMX_PAIRS", os.path.normpath(D))
            pairs = []
            for f in sorted(os.listdir(D)):
                if not f.endswith(".ymx"): continue
                ym = os.path.join(D, f[:-4] + ".ym")
                if os.path.exists(ym):
                    pairs.append((ym, os.path.join(D, f)))
            print(f"ring {RING or 'unlimited'} bytes, {len(WIDTH)} columns,"
                  f" DTX header {dtx_header(len(WIDTH))} bytes")
            print()
            print(f"{len(pairs)} tunes with a .ymx beside them")
            base = mixed = frames = 0
            for ym, ymx in pairs:
                got = load(ym, tmp)
                if not got: continue
                nf, g, ym6 = got
                frames += nf
                base += os.path.getsize(ymx)
                sizes = [st4(col, WIDTH[c], work)[1]
                         for c, col in enumerate(rows(nf, g, ym6))]
                mixed += dtx2_file(sizes)
            print(f"  YMX 0.7 files            {base:>10,}"
                  f"   {base/frames:5.2f} bytes a frame")
            print(f"  a unit a column          {mixed:>10,}"
                  f"   {mixed/frames:5.2f}   {mixed/base:.2f}x   (DTX2 forbids)")
            for k in (1, 2, 4):
                tot = pad = 0
                for ym, ymx in pairs:
                    got = load(ym, tmp)
                    if not got: continue
                    nf, g, ym6 = got
                    size, rn = tune_at(nf, g, ym6, work, k)
                    tot += size; pad += rn - nf
                print(f"  one unit k={k}             {tot:>10,}"
                      f"   {tot/frames:5.2f}   {tot/base:.2f}x"
                      f"   {pad} padded frames")
            print()
            names = sorted(f for f in os.listdir(M.CORPUS)
                           if f.lower().endswith(".ym"))
            for k in (1, 2, 4):
                tot = tf = pad = short = n = 0
                for name in names:
                    got = load(os.path.join(M.CORPUS, name), tmp)
                    if not got: continue
                    nf, g, ym6 = got
                    size, rn = tune_at(nf, g, ym6, work, k)
                    n += 1; tf += nf; tot += size; pad += rn - nf
                    short += 1 if rn != nf else 0
                print(f"  corpus, one unit k={k}: {n} tunes, {tf:,} frames,"
                      f" {tot:,} bytes, {tot/tf:5.2f} a frame,"
                      f" {short} tunes padded by {pad} frames")
        elif mode == "frame":
            import collections
            hist = collections.Counter()
            worst = (0, 0, 0, 0)
            worst_tune = ""
            frames = tunes = 0
            totals = [0, 0, 0, 0]
            for name in sorted(f for f in os.listdir(M.CORPUS)
                               if f.lower().endswith(".ym")):
                got = load(os.path.join(M.CORPUS, name), tmp)
                if not got: continue
                nf, g, ym6 = got
                tunes += 1
                for row in frame_work(nf, g, ym6):
                    frames += 1
                    hist[row[1] + row[2]] += 1
                    for i in range(4): totals[i] += row[i]
                    if row[1] + row[2] > worst[1] + worst[2]:
                        worst, worst_tune = row, name
            print(f"{tunes} tunes, {frames:,} frames")
            print(f"  a frame sets {totals[0]/frames:5.2f} columns on average,"
                  f" of 18")
            print(f"  and makes {totals[1]/frames:5.2f} YM register writes,"
                  f" {totals[2]/frames:4.2f} MFP writes,"
                  f" {totals[3]/frames:5.3f} effect starts or stops")
            run = 0
            for writes in sorted(hist):
                run += hist[writes]
                if run >= frames * 0.99:
                    print(f"  p99: {writes} register writes a frame")
                    break
            print(f"  the worst frame: {worst[0]} columns, {worst[1]} YM"
                  f" writes, {worst[2]} MFP writes, {worst[3]} effects"
                  f"  ({worst_tune})")
            most = max(hist)
            print(f"  the most any frame writes: {most} registers,"
                  f" in {hist[most]:,} frames")
        else:
            per = [0] * len(WIDTH)
            census = []
            tf = pay = whole = n = 0
            for name in sorted(f for f in os.listdir(M.CORPUS)
                               if f.lower().endswith(".ym")):
                got = load(os.path.join(M.CORPUS, name), tmp)
                if not got: continue
                nf, g, ym6 = got
                p, w = tune(nf, g, ym6, work, per, census)
                n += 1; tf += nf; pay += p; whole += w
            print(f"{n} tunes, {tf:,} frames")
            print(f"  raw rows        {ROW*tf:>12,}   {ROW:.2f} bytes a frame")
            print(f"  ST4 payload     {pay:>12,}   {pay/tf:5.2f}   {ROW*tf/pay:5.1f}x")
            print(f"  with containers {whole:>12,}   {whole/tf:5.2f}   {ROW*tf/whole:5.1f}x")
            for c, nm in enumerate(NAMES):
                print(f"  {c:2} {nm:10} {WIDTH[c]}B  {per[c]:>9,}  {100.0*per[c]/pay:5.1f}%")
            print(f"  sources a tune needs: most {max(census)}, "
                  f"{sum(1 for x in census if x > 255)} tunes over 255")
    finally:
        shutil.rmtree(work, ignore_errors=True)
        shutil.rmtree(tmp, ignore_errors=True)

if __name__ == "__main__":
    main()
