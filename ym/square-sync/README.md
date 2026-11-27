# square-sync

A hard sync from RhYMe, a tracker that writes YMXS structures: copy from
frame on `syncToneA` at the rate of Timer A, whose prescaler and count
change with the rows, and a period that changes every four rows. Voice
A's sync length in a row is 2 where RhYMe lists the voice among the row's
even syncs and 1 otherwise, the sync lengths of structure 11 (YMXS,
SPEC.md 1.6, 3.9).

| file | what it is |
|---|---|
| `square-sync.rhs` | the RhYMe song |
| `square-sync.json` | the YMXS structure RhYMe exports from it, version 11 |
| `square-sync.ymxr` | `bin/ymxs-to-ymxr < square-sync.json`, a tune file of version 15 |

`test_ymxr.py -hatari` plays the tune file after the conformance kit's
tunes (tools.md).
