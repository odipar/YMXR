# sources

One row a tune of the kit: the dump under `ym/test` it was converted from, or
the builder it was built by; the name and the author the dump's own header
gives, the only attribution the file holds; the options `ym-to-ymxr` took;
what the tune file runs to; the first sixteen hex digits of its sha256; and
what reading it exercises. `ConformanceTest` converts or builds every tune
again from these and compares the file in `tunes/` with it, so a row here is
one the converter gives.

The two built dumps, `Digidrum preempt, built.ym` and `Retrigger retune,
built.ym`, come out of `BuiltTunes` in the test tree, carried from YMX,
and `BuiltTunesTest` holds the files to it. `four-timers` is built there
too, row by row rather than from a dump, since no dump reaches all four
effects; `wrong-version` is `chambers` with another version word.

Beside every `NAME.ymxr` stands `NAME.rows`: the tune's table as a DTX0
file, its header giving `R` and `RR` and then row 0 to `R` minus one,
each its thirty columns in order (DTX, SPEC.md 2.1), so that a reader
takes the table without reading the DTX2 table's packing, which is DTX's
and not this specification's. A file of another version has no rows.

| tune | dump | name | author | options | bytes | sha256 | exercises |
|---|---|---|---|---|---|---|---|
| `chambers` | `Chambers of Shaolin 5 - you blew it!.ym` | Chambers of Shaolin: you blew it. | Jochen Hippel | none | 1968 | 6510400208c0a4a7 | no effect; R13 once, with both envelope-period-0 bits beside it; a YM6 dump |
| `circus` | `Circus Attractions  2.ym` | Circus Attractions #2 | Mad Max | none | 1356 | fbcad5fbc11a7c4e | four frames, fewer than a period; a YM5 dump |
| `plays-once` | `Circus Attractions  2.ym` | Circus Attractions #2 | Mad Max | `-r` | 1236 | 8916cf045b98f217 | four frames whose RR is R: the frame after the last row reports -1 |
| `turrican` | `Turrican - world 4-3.ym` | Turrican | Jochen Hippel (Chris Huelsbeck) | none | 3148 | b435be5bf0f1bb9b | three drums on Timer D, each ending by its marker; RR at 160, a loop longer than the ring replayed at its exact rows; R13 restated |
| `turrican-2` | `Turrican 2 - world completed 1.ym` | Turrican 2 - World completed | Jochen Hippel (Chris Huelsbeck) | none | 2024 | d7c3c2481549cb93 | a loop of one row, RR at 177, odd, so the table packs at unit 1; six drums before it, the last stopped by a row |
| `synergy` | `Synergy Credits.ym` | Synergy Credits Screen. | Scavenger / Synergy | none | 8176 | ceb9f43c2705b4d7 | nine SIDs on Timers A and D at once, six of them named by both; the select changed without the source, and the count alone; a running source stopped by a row; a tone fine byte 0 with the coarse bit beside it; 5,377 rows, odd, so the table packs at unit 1 |
| `preempt` | `Digidrum preempt, built.ym` | Synthetic | Test | none | 2168 | 8a6e497af856af92 | a drum starting on the voice a SID runs on stops the SID first, and the SID starts again when the drum ends; R8 passed between them with its column unset |
| `retune` | `Retrigger retune, built.ym` | Synthetic | Test | none | 2016 | 801dc70555d5f28c | a one-row buzzer source on R13, restarted over a running timer with a new rate; select 7; stopped at the wrap alone |
| `fine-zero` | `Big - Samantha Fox Strip Poker 6.ym` | Samantha Fox Strip Poker #6 | Mad Max | none | 1476 | 8550e7d90f8996da | a tone fine byte moving to 0: on voice A without the coarse set bit, on B and C with it |
| `four-timers` | `BuiltTunes.fourTimers` | none | none | none | 2036 | a0328d952c28f790 | all four effects on Timers A, D, B and C at 60 Hz; the rows section 4 allows that no dump gives: a count alone, a select alone with the count kept, bit 5 alone, bit 5 with a new source on a running timer, bit 6 alone, a stop with the volume set, the same source again, a target set while running and taken at the next start, a target that is not a volume register, a drum closing on 5, R13 set beside a buzzer, a source repeating to its row 2, a stop with nothing running, values under a clear set bit, a fine byte and an envelope period byte that are not 0 with the bit beside them |
| `wrong-version` | `ConformanceTest.wrongVersion` | none | none | none | 1968 | 44ab39fbbe3cf72b | chambers with the version word $0003: a reader reports nothing of it |
