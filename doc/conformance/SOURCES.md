# sources

One row a tune of the kit: the dump under `ym/test` it was converted from,
or the builder it was built by; the name and author the dump's own header
gives, the only attribution the file holds; the options `ym-to-ymxr`
took; what the tune file runs to; the first sixteen hex digits of its
sha256; and what reading it exercises. `ConformanceTest` converts or
builds every tune again from these and compares the file in `tunes/`
with it, so a row here is one the converter gives.

The two built dumps, `Digidrum preempt, built.ym` and `Retrigger retune,
built.ym`, come out of `BuiltTunes` in the test tree, carried from YMX,
and `BuiltTunesTest` holds the files to it. `four-timers` is built there
too, row by row rather than from a dump, since no dump reaches all four
effects; `wrong-version` is `chambers` with another version word.

Beside every `NAME.ymxr` stands `NAME.rows`: the table's rows as DTX0
lays them out, row 0 to `R` minus one, each its thirty columns in order
(DTX, SPEC.md 2.1), so that a reader takes the rows without reading the
image's packing, which is DTX's and not this specification's. A file of
another version has no rows.

| tune | dump | name, author | options | bytes | sha256 | exercises |
|---|---|---|---|---|---|---|
| `chambers` | `Chambers of Shaolin 5 - you blew it!.ym` | Chambers of Shaolin: you blew it., Jochen Hippel | none | 3632 | b8cf0572f4faf6de | no effect; R13 once, with both envelope-period-0 bits beside it; a YM6 dump |
| `circus` | `Circus Attractions  2.ym` | Circus Attractions #2, Mad Max | none | 3032 | fc3d556588a96ab2 | four frames padded to ninety: rows that set nothing; a YM5 dump |
| `plays-once` | `Circus Attractions  2.ym` | Circus Attractions #2, Mad Max | `-r` | 3032 | a71a2160da95ab4c | a table of one period whose RR is R: the frame after the last row reports -1 |
| `turrican` | `Turrican - world 4-3.ym` | Turrican, Jochen Hippel (Chris Huelsbeck) | none | 4948 | 01070a286d5d3da0 | three drums on Timer D, each ending by its marker; RR at 180 with twenty silent rows before it; R13 restated |
| `turrican-2` | `Turrican 2 - world completed 1.ym` | Turrican 2 - World completed, Jochen Hippel (Chris Huelsbeck) | none | 3936 | 1905999e22662c03 | a loop of one row padded to ninety; a drum running into the wrap |
| `synergy` | `Synergy Credits.ym` | Synergy Credits Screen., Scavenger / Synergy | none | 9908 | f8b58a6866319a0e | nine SIDs on Timers A and D at once, one source named by both; select-only and count-only changes; a running source stopped by a row; a tone fine byte 0 with the coarse bit beside it |
| `preempt` | `Digidrum preempt, built.ym` | Synthetic, Test | none | 3840 | 5f74fce2eac67c71 | a drum starting on the voice a SID runs on stops the SID first, and the SID starts again when the drum ends; R8 passed between them with its column unset |
| `retune` | `Retrigger retune, built.ym` | Synthetic, Test | none | 3672 | c140c33d15a76cff | a one-row buzzer source on R13, restarted over a running timer with a new rate; select 7; stopped at the wrap alone |
| `fine-zero` | `Big - Samantha Fox Strip Poker 6.ym` | Samantha Fox Strip Poker #6, Mad Max | none | 3168 | 76ca19509c80c2f5 | a tone fine byte moving to 0: on voice A without the coarse set bit, on B and C with it |
| `four-timers` | built | built | none | 3812 | c77f2c77abe66ff4 | all four effects on Timers A, D, B and C at 60 Hz; the rows section 4 allows that no dump gives: a count alone, a select alone with the count kept, bit 5 alone, bit 5 with a new source on a running timer, bit 6 alone, a stop with the volume set, the same source again, a target set while running and taken at the next start, a target that is not a volume register, a drum closing on 5, R13 set beside a buzzer, a source repeating to its row 2, a stop with nothing running, values under a clear set bit, a fine byte and an envelope period byte that are not 0 with the bit beside them |
| `wrong-version` | built | built | none | 3632 | 0210fdc01f4a7f68 | chambers with the version word $0002: a reader reports nothing of it |
