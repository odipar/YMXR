# glossary

Every term this repository defines, one line each. The document beside a
term explains it at length. A term that changes here changes there in the
same change (requirements.md, R0.6 to R0.8).

| term | what it is | explained in |
|---|---|---|
| ABI | The one function a build exposes, `nextRow`. | requirements.md R1.3 |
| build | What a compile step produces: a binary with an ABI, compiled for memory, for speed, or for both. | requirements.md R1.2, R1.6 |
| `C` | The table's column count. At most 32. | requirements.md R1.1, R3.4 |
| caller | What asks a table for a row, holding its own place in it. A player is one, for the tune's table and for every source it runs. | terminology.md, tables, rows and procedures |
| column | One field of a row, 1, 2 or 4 bytes wide. | requirements.md R1.1 |
| column-major | The table's order: a column's values together rather than a row's. | requirements.md R1.1 |
| compile step | Turns a table into a build. | requirements.md R1.2 |
| corpus | The 544 YM files YMX 0.8.3 is tested against, 543 of which read. Every figure here is measured over it. | experiments.md |
| DAC | The YM2149's ladder of output levels, close to logarithmic: about 3 dB a step, spanning about 54 dB. | terminology.md, the sound chip |
| DTX | The data engine: a table in, one row a call out. A repository of its own. | README.md |
| effect | A source connected to a target on one timer, at the rate its rate column gives. Two columns give it: the connection and the rate. What the scene names: a two-row source on a volume register is a SID voice, a long one is a digidrum, a one-row source on R13 is a sync buzzer. | SPEC.md 1.8, 1.9 |
| envelope generator | A counter walking one of sixteen shapes. | terminology.md, the sound chip |
| envelope period | R11 and R12, a 16-bit divider. Run fast, the sweep is a pitch. | terminology.md, the sound chip |
| envelope shape | R13, four bits. Four of the sixteen repeat; the other twelve run once and hold. | terminology.md, the sound chip |
| frame | One turn of the method at the tune's own clock: a row of the tune's table, read as music. | terminology.md, frames and ticks |
| generator | One of the YM2149's five: three tone, one noise, one envelope. | terminology.md, the sound chip |
| index entry | A source's metadata and where its rows are: a 4-byte offset, `R` minus one, then bit 15 the repeat and bits 14 to 0 `RR`. | SPEC.md 3.1 |
| metadata | What describes a table without holding it: `R`, `C`, `RR` and each column's width. | terminology.md, tables, rows and procedures |
| MFP | The MC68901: four timers, A to D, on a clock of 2,457,600 a second. | terminology.md, the timers |
| mixing | Which generator signals reach a voice: tone, noise, both or neither. R7. | terminology.md, the sound chip |
| `nextRow` | DTX's one function. It fills a row buffer. | requirements.md R1.3 |
| noise generator | A shift register producing a random-sounding bit pattern. One feeds all three voices. | terminology.md, the sound chip |
| noise period | R6, five bits, 1 to 31. How bright the noise is, not how loud. | terminology.md, the sound chip |
| period | How long a generator takes per cycle. Bigger period, lower pitch. | terminology.md, the sound chip |
| player | Calls `nextRow` once a frame for every table the tune runs, and turns the frame into writes to the YM2149 and the MFP. | requirements.md R4 |
| prescaler | The MFP's first divisor: 4, 10, 16, 50, 64, 100 or 200. | terminology.md, the timers |
| procedure | What a clock calls with a row: it writes the row to the chips. The frame's writes a row of the tune's table, a target a row of a source. | terminology.md, tables, rows and procedures |
| `R` | The table's row count. | requirements.md R1.1 |
| rate | How often a tick comes. | terminology.md, rates |
| reader | Reads a tune and reports what it holds, writing to no chip. The conformance kit is written against one. | requirements.md R2.4 |
| register | One byte of YM2149 state. Sixteen of them: fourteen steer the sound, two are I/O ports. | terminology.md, the sound chip |
| row | One step of a table: `C` values, with nothing in it about what any of them is for. | terminology.md, tables, rows and procedures |
| row buffer | What `nextRow` fills: `col0` through `col(C-1)`. Good for one call, and not a store of state. | requirements.md R1.4 |
| `RR` | The row a build repeats to once the last row is done. | requirements.md R1.5 |
| sample | A recording played through a voice's volume register, one byte a tick. Its rate is the recording's own, and it is a source of many rows. | terminology.md; SPEC.md 2.2 |
| schema | What each column holds: an abstraction over the ST's effects rather than one tracker's arrangement of them. | requirements.md R3 |
| set bit | A column's top bit: 1 sets the value, 0 does not, and a value not set is not read. A column with no bit to spare reserves a value instead. | requirements.md R3.6, R3.7 |
| signal | A series of values with a rate: a square wave, a run of noise, a sample. | terminology.md, the sound chip |
| source | A table of `R` rows and `C` columns repeating at `RR`, advanced a row each tick, and typically one column of one byte. An effect column's low byte names one, 0 to 255. | SPEC.md 1.8, 2.2 |
| source index | One index entry a source, in source-number order. | SPEC.md 3.1 |
| table | What yields rows: `R` of them, `C` columns wide, repeating at `RR`. One that repeats yields rows without end, and a build holds its rows or works them out. | terminology.md, tables, rows and procedures |
| target | The procedure a timer's tick calls with a source's row. Those named write one register from a one-byte row: `setR0` to `setR13`. An effect column names one, 0 to 127. | SPEC.md 1.8, 2.1 |
| tick | One turn of the method at a timer's rate: a row of a source advanced, its target called. | terminology.md, frames and ticks |
| timer | One of the MFP's four. It counts down at the divided rate and raises an interrupt at zero. | terminology.md, the timers |
| timer count | The MFP's second divisor: 1 to 255, and 0 read as 256. | terminology.md, the timers |
| tone generator | A counter that flips its output when it runs out, making a square wave. Three of them. | terminology.md, the sound chip |
| tone period | A voice's 12-bit tone divider: R0 and R1, R2 and R3, R4 and R5. | terminology.md, the sound chip |
| ubiquitous language | The terms in this glossary. A tracker maps its own onto them. | requirements.md R3.2 |
| voice | One of three outputs, A, B and C. Each has a volume and a mixing setting. | terminology.md, the sound chip |
| volume | A voice's level: bits 3-0 of R8, R9 or R10, with bit 4 following the envelope instead. | terminology.md, the sound chip |
| wave | One cycle of a signal, played through a register at a timer's rate. A source of a cycle's rows, which its entry usually repeats. | SPEC.md 2.2 |
| yielding | Giving one row of a table, in order and when asked. A caller holds its own place in the table. | terminology.md, tables, rows and procedures |
| YM2149 | The sound chip, Yamaha's AY-3-8910, running at 2 MHz on an Atari ST. | terminology.md, the sound chip |
| YMX | The format that plays today. YMXR takes the name when it is done. | README.md |
| YMXR | This format: one use of DTX, its columns holding YM2149 and MFP state. | README.md |

## Named, not yet defined

These appear in the documents without an entry above. Each gets one as the
schema settles.

- **tracker** and **tune** - what produces a table, and what a player plays.
- The two kinds of rate: one an effect owns, one taken from the note
  playing. Terminology.md, rates, describes both without naming either.
