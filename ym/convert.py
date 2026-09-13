"""A YM dump into the schema's columns, and what each column packs to.

Usage: convert.py corpus   - every corpus tune as whole DTX2 files at each
                             unit, the per-column breakdown, the ceilings
       convert.py pairs    - the tunes with a .ymx beside them, against it
       convert.py envelope - the envelope columns, the reserved 0 against
                             set bits in the shape column
       convert.py frame    - what a frame procedure has to do, per frame

The corpus comes from YM_CORPUS (measure.py). The DTX2 files are written
by DTX's dtx-write, named by DTX_WRITE or found on the path, and built
from DTX's go/cmd/dtx-write; DTX_RING sets the ring in bytes, 960 being
dtx-write's default. JOBS tunes are converted at once.
"""
import collections, os, struct, subprocess, sys, tempfile, shutil
from concurrent.futures import ProcessPoolExecutor
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import measure as M

# doc/SPEC.md section 1: thirty columns of one byte, column i reaching Ri
# for 0 to 13, then four columns an effect
C = 30
NAMES = ["toneA.f", "toneA.c", "toneB.f", "toneB.c", "toneC.f", "toneC.c",
         "noise", "mix", "volA", "volB", "volC", "env.f", "env.c", "shape"]
NAMES += [f"{w}{i}" for i in range(4)
          for w in ("target", "source", "control", "count")]
EFFECT = 14                      # the first effect's target column
# a column that fills its byte, and the bit beside it that keeps 0 a value
# (SPEC 1.1); the four counts have no such bit
BESIDE = {0: (1, 0x40), 2: (3, 0x40), 4: (5, 0x40),
          11: (13, 0x40), 12: (13, 0x20)}
COUNTS = (17, 21, 25, 29)
# the control column's high bits (SPEC 1.9)
TIMER_RESET, PLACE_RESET = 0x40, 0x20

# YM6 code nibble, type in bits 7-6 (YMX YmEffects.java) -> source kind
YM_KIND = {0x00: 1, 0x40: 2, 0x80: 3, 0xC0: 4}
DTX_WRITE = os.environ.get("DTX_WRITE", "dtx-write")
RING = int(os.environ.get("DTX_RING", "960"))
# DTX2 packs copies from the literal stream where this names the flag,
# "-copies" or "-copiesS" for S seconds of search (DTX, dtx-write).
COPIES = os.environ.get("DTX_COPIES", "")
JOBS = int(os.environ.get("JOBS", str(os.cpu_count() or 1)))
PAIRS = os.environ.get("YMX_PAIRS", os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "YMX", "ym", "test")))

def load(path, tmp):
    """(nf, get, ym6) for one corpus file, or None."""
    d = M.payload(path, tmp)
    got = M.regs(d) if d else None
    if not got:
        return None
    nf, g = got
    return nf, g, d[:4] == b"YM6!"

def effect_slots(r, ym6):
    """The two YM effect slots of one frame: (kind, target, data, select,
    count), kind 0 for an empty slot. YM6 files each slot's kind in the
    code's bits 7-6; YM5 has no kind bits, its first slot is a SID voice
    and its second a digidrum (YMX, YmEffects.java). The dump's prescaler
    select is the MFP's, 1 to 7, as SPEC 1.9 reads it."""
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

def registers(r):
    """R0 to R13 out of one frame, a shape of None where the dump does not
    write R13 (YM5/6 keep effect flags in the spare bits, measure.py)."""
    return [r[0], r[1] & 15, r[2], r[3] & 15, r[4], r[5] & 15,
            r[6] & 0x1F, r[7] & 0x3F, r[8] & 0x1F, r[9] & 0x1F, r[10] & 0x1F,
            r[11], r[12], (r[13] & 15) if r[13] != 0xFF else None]

def rows(nf, g, ym6):
    """The thirty column streams of one tune, one byte a frame each, and
    how many sources it names. A column is set where its value differs
    from the value the player keeps; an unset value is 0, which R3.6 does not
    read and which packs smallest of the fills tried.

    A YM dump names an effect by kind and by a value out of a volume
    register, so each distinct pair becomes a separate source of the tune,
    numbered from 1 as it is first met."""
    cols = [bytearray() for _ in range(C)]
    kept = [None] * 14               # what the player keeps of R0 to R13
    fx_held = [None] * 4             # the (target, source) an effect runs
    target_held = [None] * 4
    rate_held = [None] * 4           # the (select, count) a timer runs at
    sources = {}
    for f in range(nf):
        r = [g(i, f) for i in range(16)]
        fx = effect_slots(r, ym6)
        owned = {t for k, t, _, _, _ in fx if k}
        reg = registers(r)
        out = [0] * C
        # the columns that fill their byte: written where they move, and
        # a move to 0 sets the bit beside them (SPEC 1.1)
        for c, (beside, bit) in BESIDE.items():
            if reg[c] != kept[c]:
                out[c] = reg[c]
                if reg[c] == 0:
                    out[beside] |= bit
                kept[c] = reg[c]
        # the columns with a set bit: coarse, noise, mixing, volumes. A row
        # leaves a register an effect owns (section 6), and the row that
        # stops the effect sets it again (1.3)
        for c in (1, 3, 5, 6, 7, 8, 9, 10):
            if c in owned:
                kept[c] = None
                continue
            if reg[c] != kept[c]:
                out[c] |= 0x80 | reg[c]
                kept[c] = reg[c]
        # the shape: set where the dump writes R13, which restarts the
        # envelope (1.6), unless an effect owns R13
        if reg[13] is not None and 13 not in owned:
            out[13] |= 0x80 | reg[13]
        # the effects: a start sets the source, the rate, and the timer's
        # and the place's reset; a stop sets source 0 (1.8, 1.9, section 6)
        for i, (kind, target, data, pre, cnt) in enumerate(fx):
            t, s, ctl, count = (EFFECT + 4*i + j for j in range(4))
            if not kind:
                if fx_held[i] is not None:
                    out[s] = 0x80
                    fx_held[i] = None
                continue
            number = sources.setdefault((kind, data), len(sources) + 1)
            if target != target_held[i]:
                out[t] = 0x80 | target
                target_held[i] = target
            starting = fx_held[i] != (target, number)
            if starting:
                out[s] = 0x80 | (number & 0x7F)
                out[ctl] = 0x80 | TIMER_RESET | PLACE_RESET | pre
                out[count] = cnt
                fx_held[i] = (target, number)
            else:
                if rate_held[i][0] != pre:
                    out[ctl] = 0x80 | pre
                if rate_held[i][1] != cnt:
                    out[count] = cnt
            rate_held[i] = (pre, cnt)
        for c in range(C):
            cols[c].append(out[c])
    return cols, len(sources), {k for k, _ in sources}

def is_set(cols, c, f):
    """Whether row f sets column c: its set bit, or for a column that
    fills its byte a value other than 0 or the bit beside it (SPEC 1.1)."""
    v = cols[c][f]
    if c in BESIDE:
        beside, bit = BESIDE[c]
        return v != 0 or bool(cols[beside][f] & bit)
    if c in COUNTS:
        return v != 0
    return bool(v & 0x80)

def frame_work(cols, nf):
    """Per frame: (columns set, YM register writes, MFP writes, effects
    started or stopped), from the columns the converter writes, so it is
    the same rows the packing figures measure. Column i of 0 to 13 is one
    register write. A stop is one control write; a start, or any control
    column with the timer's reset, is three (SPEC 1.9); a control or a
    count written to a running timer is one each."""
    out = []
    for f in range(nf):
        set_columns = ym = mfp = fx = 0
        for c in range(C):
            if is_set(cols, c, f):
                set_columns += 1
                if c < 14:
                    ym += 1
        for i in range(4):
            t, s, ctl, count = (EFFECT + 4*i + j for j in range(4))
            if is_set(cols, s, f):
                fx += 1
                if cols[s][f] == 0x80:
                    mfp += 1
            if is_set(cols, ctl, f):
                mfp += 3 if cols[ctl][f] & TIMER_RESET else 1
            if is_set(cols, count, f) and not (cols[ctl][f] & TIMER_RESET):
                mfp += 1
        out.append((set_columns, ym, mfp, fx))
    return out

def envelope_designs(nf, g, ym6):
    """The three envelope columns twice, shape first: as SPEC.md has them,
    the two period bytes reserving 0 with a bit beside them in the shape
    (1.7), and with a set bit for each byte in the shape column instead. The
    period bytes are the same bytes under both; only the shape differs."""
    spec = [bytearray() for _ in range(3)]
    hosted = [bytearray() for _ in range(3)]
    kept = [None, None]
    for f in range(nf):
        r = [g(i, f) for i in range(16)]
        owned = {t for k, t, _, _, _ in effect_slots(r, ym6) if k}
        shape = (r[13] & 15) if r[13] != 0xFF else None
        a = b = (0x80 | shape) if shape is not None and 13 not in owned else 0
        period = [0, 0]
        for j, (v, bit) in enumerate(((r[11], 0x40), (r[12], 0x20))):
            if v != kept[j]:
                period[j] = v
                b |= bit
                if v == 0:
                    a |= bit
                kept[j] = v
        spec[0].append(a); hosted[0].append(b)
        for j in range(2):
            spec[j + 1].append(period[j]); hosted[j + 1].append(period[j])
    return spec, hosted

def padded(cols, nf, k):
    """R rows up to a multiple of k (DTX R5.6), the last frame repeated."""
    if nf % k == 0:
        return cols, nf
    more = k - nf % k
    return [col + col[-1:] * more for col in cols], nf + more

def dtx2(cols, rn, k, work):
    """The columns as one DTX2 file written by dtx-write at unit k: its
    size, and the bytes each column's data set spans in it, the pad that
    puts the next one on a long included (DTX SPEC.md 2.3)."""
    src, dst = os.path.join(work, "t.csv"), os.path.join(work, "t.dtx")
    with open(src, "w") as out:
        out.write(f"# {rn} rows, {len(cols)} columns, width 1, RR {rn}\n")
        for row in zip(*cols):
            out.write(",".join(map(str, row)))
            out.write("\n")
    r = subprocess.run([DTX_WRITE, src, dst, "-v2", "-w1", f"-k{k}",
                        f"-m{RING}"] + ([COPIES] if COPIES else []),
                       capture_output=True)
    if r.returncode or not os.path.exists(dst):
        raise SystemExit(f"dtx-write did not run: {r.stderr.decode()[:300]}")
    d = open(dst, "rb").read()
    c = struct.unpack(">H", d[8:10])[0]
    offsets = struct.unpack(f">{c}I", d[20:20 + 4 * c])
    spans = [(offsets[i + 1] if i + 1 < c else len(d) - 16) - offsets[i]
             for i in range(c)]
    return len(d), spans

def one(args):
    """Everything a mode measures of one tune."""
    path, mode = args
    tmp, work = tempfile.mkdtemp(), tempfile.mkdtemp()
    try:
        got = load(path, tmp)
        if not got:
            return None
        nf, g, ym6 = got
        out = {"name": os.path.basename(path), "nf": nf, "ym6": ym6}
        if mode in ("corpus", "pairs"):
            cols, out["sources"], out["kinds"] = rows(nf, g, ym6)
            out["files"] = {}
            for k in (1, 2, 4):
                pc, rn = padded(cols, nf, k)
                size, spans = dtx2(pc, rn, k, work)
                out["files"][k] = (size, rn - nf, spans)
        elif mode == "envelope":
            spec, hosted = envelope_designs(nf, g, ym6)
            out["spec"] = sum(dtx2(spec, nf, 1, work)[1])
            out["hosted"] = sum(dtx2(hosted, nf, 1, work)[1])
        elif mode == "frame":
            cols, _, _ = rows(nf, g, ym6)
            out["frames"] = frame_work(cols, nf)
        return out
    finally:
        shutil.rmtree(work, ignore_errors=True)
        shutil.rmtree(tmp, ignore_errors=True)

def each(paths, mode):
    with ProcessPoolExecutor(JOBS) as pool:
        for got in pool.map(one, [(p, mode) for p in paths]):
            if got:
                yield got

def corpus():
    return [os.path.join(M.CORPUS, f) for f in sorted(os.listdir(M.CORPUS))
            if f.lower().endswith(".ym")]

def table(rows, frames, raw=None):
    """A packing table's rows: label, bytes, a frame, and the ratio against
    raw where the caller passes raw, else against the first row."""
    first = rows[0][1]
    for label, size in rows:
        ratio = (f"{raw / size:.1f}x" if raw else f"{size / first:.2f}x")
        print(f"  {label:34} {size:>12,}   {size / frames:5.2f}   {ratio}")

def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "corpus"
    if mode == "corpus":
        n = frames = 0
        total = {k: 0 for k in (1, 2, 4)}
        padded_tunes = {k: 0 for k in (1, 2, 4)}
        padded_frames = {k: 0 for k in (1, 2, 4)}
        per = [0] * C
        sources = []
        # which kinds a tune's effects name, a tune counted once a kind
        kinds = {k: 0 for k in (1, 2, 3, 4)}
        named = 0
        ym5 = ym6 = 0
        for got in each(corpus(), "corpus"):
            n += 1; frames += got["nf"]
            ym6 += got["ym6"]; ym5 += not got["ym6"]
            sources.append(got["sources"])
            named += bool(got["kinds"])
            for k in got["kinds"]:
                kinds[k] += 1
            for k, (size, pad, spans) in got["files"].items():
                total[k] += size
                padded_tunes[k] += pad > 0
                padded_frames[k] += pad
                if k == 1:
                    for c in range(C):
                        per[c] += spans[c]
        raw = C * frames
        print(f"{n} tunes, {ym5} YM5! and {ym6} YM6!, {frames:,} frames,"
              f" ring {RING}, {C} columns of one byte")
        print(f"  raw rows                           {raw:>12,}"
              f"   {C:5.2f}")
        table([(f"DTX2 files at k = {k}", total[k]) for k in (1, 2, 4)],
              frames, raw)
        for k in (2, 4):
            print(f"  at k = {k}, {padded_tunes[k]} tunes padded by"
                  f" {padded_frames[k]} frames")
        packed = sum(per)
        print(f"  the data sets at k = 1: {packed:,} bytes")
        for c in range(C):
            print(f"  {c:2} {NAMES[c]:10} {per[c]:>10,}  {100.0 * per[c] / packed:5.1f}%")
        tone = [per[v] + per[v + 1] for v in (0, 2, 4)]
        print(f"  tone periods, fine and coarse: "
              + ", ".join(f"{100.0 * t / packed:.1f}%" for t in tone)
              + f", {100.0 * sum(tone) / packed:.1f}% together")
        print(f"  effects, all sixteen columns: "
              f"{100.0 * sum(per[EFFECT:]) / packed:.1f}%;"
              f" the shape {100.0 * per[13] / packed:.1f}%")
        print(f"  columns 22 to 29, each: "
              + ", ".join(f"{per[c]:,}" for c in range(22, 30)))
        print(f"  sources a tune needs: most {max(sources)},"
              f" {sum(1 for x in sources if x > 127)} tunes over 127")
        print(f"  {named} tunes name a source: " + ", ".join(
            f"{kinds[k]} {name}" for k, name in
            ((1, "a square wave"), (2, "a digidrum"),
             (3, "a sinus SID"), (4, "a sync buzzer"))))
    elif mode == "pairs":
        pairs = []
        said = set()
        for f in sorted(os.listdir(PAIRS)):
            if f.endswith(".ymx") and os.path.exists(os.path.join(PAIRS, f[:-4] + ".ym")):
                at = os.path.join(PAIRS, f)
                pairs.append((os.path.join(PAIRS, f[:-4] + ".ym"),
                              os.path.getsize(at)))
                # the version the file opens with, so the row below names
                # the format these were written by rather than one a reader
                # of this remembers (YMX, SPEC.md 2)
                with open(at, "rb") as one:
                    head = one.read(6)
                if head[:4] == b"YMX!":
                    said.add("%d.%d" % (head[4], head[5]))
        n = frames = ymx = 0
        total = {k: 0 for k in (1, 2, 4)}
        for got, (_, size) in zip(each([p for p, _ in pairs], "pairs"), pairs):
            n += 1; frames += got["nf"]; ymx += size
            for k, (dsize, _, _) in got["files"].items():
                total[k] += dsize
        print(f"{n} tunes with a .ymx beside them, {frames:,} frames, ring {RING}")
        version = ", ".join(sorted(said)) if said else "of no version read"
        table([(f"YMX {version}, the .ymx files", ymx)]
              + [(f"YMXR, DTX2 files at k = {k}", total[k]) for k in (1, 2, 4)],
              frames)
    elif mode == "envelope":
        n = frames = spec = hosted = 0
        for got in each(corpus(), "envelope"):
            n += 1; frames += got["nf"]
            spec += got["spec"]; hosted += got["hosted"]
        print(f"{n} tunes, {frames:,} frames, ring {RING}, k = 1")
        print(f"  envelope columns, 0 reserved and the bits beside  {spec:>10,}")
        print(f"  envelope columns, set bits in the shape column   {hosted:>10,}")
        print(f"  the reserved value saves                          {hosted - spec:>10,}")
    elif mode == "frame":
        hist = collections.Counter()
        worst, worst_tune = (0, 0, 0, 0), ""
        n = frames = 0
        totals = [0, 0, 0, 0]
        for got in each(corpus(), "frame"):
            n += 1
            for row in got["frames"]:
                frames += 1
                hist[row[1] + row[2]] += 1
                for i in range(4):
                    totals[i] += row[i]
                if row[1] + row[2] > worst[1] + worst[2]:
                    worst, worst_tune = row, got["name"]
        print(f"{n} tunes, {frames:,} frames")
        print(f"  a frame sets {totals[0] / frames:5.2f} columns on average,"
              f" of {C}")
        print(f"  and makes {totals[1] / frames:5.2f} YM register writes,"
              f" {totals[2] / frames:4.2f} MFP writes,"
              f" {totals[3] / frames:5.3f} effect starts or stops")
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
        raise SystemExit(__doc__)

if __name__ == "__main__":
    main()
