# square-sync

A hard sync from RhYMe, a tracker that writes YMXS structures: copy from
frame on `syncToneA` at the rate of Timer A, whose prescaler and count
change with the rows, and a period that changes every four rows. The
voice is listed among the row's even syncs where the writer counts an
even number of turns of the square between two ticks, so the square
restarts at one level at every tick (YMXS, SPEC.md 3.9.4).

| file | what it is |
|---|---|
| `square-sync.rhs` | the RhYMe song |
| `square-sync.json` | the YMXS structure RhYMe exports from it, version 11 |
| `square-sync.ymxr` | `bin/ymxs-to-ymxr < square-sync.json`, a tune file of version 14 |

`test_ymxr.py -hatari` plays the tune file after the conformance kit's
tunes (tools.md).
