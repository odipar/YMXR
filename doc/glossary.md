# glossary

Every term this repository defines, one line each. The document beside a
term explains it at length. A term that changes here changes there in the
same change (requirements.md, R0.6 to R0.8).

| term | what it is | explained in |
|---|---|---|
| bound tune | A tune file's tables bound with DTX's reader into the layout a player reads: the tune file's layout under its magic and version, the image where the file has its DTX2 table, and the state block's bytes before the index. | BINARIES.md |
| `C` | The table's column count. At most 32. | requirements.md R1.1, R3.4 |
| column | One field of a row, `W` bytes wide: one byte in a tune's table. | requirements.md R1.1 |
| corpus | The 544 YM files YMX 0.8.3 is tested against, 543 of which read. Every figure here is measured over it. | experiments.md |
| DAC | The YM2149's ladder of output levels, close to logarithmic: about 3 dB a step, spanning about 54 dB. | terminology.md, the sound chip |
| DTX | The data format: a table of `R` rows and `C` columns, every value `W` bytes. The meaning of a column belongs to YMXR. A separate repository. | README.md |
| effect | A source connected to a target on one timer, at the rate its rate columns define. Four columns define it: the target, the source, the timer's control and its count. The scene's names: a two-row source on a volume register is a SID voice, a long one is a digidrum, a one-row source on R13 is a sync buzzer. | SPEC.md 1.8, 1.9 |
| envelope generator | A counter walking one of sixteen shapes. | terminology.md, the sound chip |
| envelope period | R11 and R12, a 16-bit divider. Run fast, the sweep is a pitch. | terminology.md, the sound chip |
| envelope shape | R13, four bits. Four of the sixteen repeat; the other twelve run once and stop. | terminology.md, the sound chip |
| frame | One turn of the method at the tune's clock: a row of the tune's table, read as music. | terminology.md, frames and ticks |
| generator | One of the YM2149's five: three tone, one noise, one envelope. | terminology.md, the sound chip |
| image | What DTX packages a table with: its reader's code, its column table and the table, read through four calls at its first bytes. A bound tune contains one; a tune file does not. | BINARIES.md |
| index entry | Where a source's DTX1 table begins in the tune file, a 4-byte offset. The table's header defines `R` and `RR`. | SPEC.md 3.1, 3.3 |
| marker | Bit 7 of a source's last row, which a tick tests after the write: the next tick reads row `RR`, or the timer stops. | SPEC.md 3.2, 5 |
| metadata | What describes a table rather than filling it: `R`, `C`, `RR` and `W`. | terminology.md, tables, rows and procedures |
| MFP | The MC68901: four timers, A to D, on a clock of 2,457,600 a second. | terminology.md, the timers |
| mixing | Which generator signals reach a voice: tone, noise, both or neither. R7. | terminology.md, the sound chip |
| noise generator | A shift register producing a random-sounding bit pattern. One feeds all three voices. | terminology.md, the sound chip |
| noise period | R6, five bits, 1 to 31. How bright the noise is, not how loud. | terminology.md, the sound chip |
| period | The length of one cycle of a generator. Bigger period, lower pitch. | terminology.md, the sound chip |
| place | The row a timer's next tick reads from its source. A start puts it at the first row, and bit 5 of the control column puts it there again. | SPEC.md 1.9 |
| player | Advances the tune's table one row a frame and turns that row into writes to the YM2149 and the MFP, and advances a source on every tick of its timer. | requirements.md R4 |
| prescaler | The MFP's first divisor: 4, 10, 16, 50, 64, 100 or 200. | terminology.md, the timers |
| procedure | What a clock calls with a row: it writes the row to the chips. The frame's writes a row of the tune's table, a target a row of a source. | terminology.md, tables, rows and procedures |
| `R` | The table's row count. | requirements.md R1.1 |
| raster monitor | The play call painting the background red while its work runs and yellow while it burns the timers' counted cost, and each tick handler painting a separate colour: a build of the player, off by default, which a run under an emulator reads back as cycles. | performance.md |
| rate | How often a tick comes. | terminology.md, rates |
| reader | Reads a tune and reports the result, writing to no chip. The conformance kit is written against one. | requirements.md R2.4 |
| register | One byte of YM2149 state. Sixteen of them: fourteen steer the sound, two are I/O ports. | terminology.md, the sound chip |
| row | One step of a table: `C` values, with no meaning attached to any of them. | terminology.md, tables, rows and procedures |
| `RR` | The row a table repeats to once the last row is done. | requirements.md R1.1 |
| sample | A recording played through a voice's volume register, one byte a tick. Its rate is the recording's, and it is a source of many rows. | terminology.md; SPEC.md 2.2 |
| schema | The meaning of each column: an abstraction over the ST's effects rather than one tracker's arrangement of them. | requirements.md R3 |
| set bit | A column's top bit: 1 sets the value, 0 does not, and a value not set is not read. A column with no bit to spare reserves a value instead. | requirements.md R3.6, R3.7 |
| signal | A series of values with a rate: a square wave, a run of noise, a sample. | terminology.md, the sound chip |
| SNDH core | The player under SNDH's three entries, keeping the four timers at init and putting the claimed ones back at exit; assembled once and carried in the jar, and combined with bound tunes into an SNDH file by a tool. | BINARIES.md 2 |
| source | A DTX1 table of one column of one byte, repeating at `RR`, advanced a row each tick, its last row the marker. An effect's source column names one, 1 to 127. | SPEC.md 1.8, 2.2, 3.1 |
| source index | One index entry a source, in source-number order. | SPEC.md 3.1 |
| stub | The program stub: a block a tool puts in front of an SNDH file to make a TOS program, which claims the machine, plays the file from the VBL or Timer C, and releases the machine at the end. Assembled once and stored in the jar. | BINARIES.md 4 |
| table | What yields rows: `R` of them, `C` columns wide, every value `W` bytes, repeating at `RR`. One that repeats yields rows without end. The layout of its rows belongs to the format. | terminology.md, tables, rows and procedures |
| target | The procedure a timer's tick calls with a source's row. Those named write one register from a one-byte row: `setR0` to `setR13`. An effect's target column names one, 0 to 127. | SPEC.md 1.8, 2.1 |
| tick | One turn of the method at a timer's rate: a row of a source advanced, its target called. | terminology.md, frames and ticks |
| timer | One of the MFP's four. It counts down at the divided rate and raises an interrupt at zero. | terminology.md, the timers |
| timer control register | One of the MFP's, with a timer's prescaler select in three bits: 1 to 7 for 4 to 200, 0 for stopped. Timers C and D share one. | terminology.md, the timers |
| timer count | The MFP's second divisor: 1 to 255, and 0 read as 256. | terminology.md, the timers |
| timer data register | One of the MFP's, with a timer's count in it. | terminology.md, the timers |
| tone generator | A counter that flips its output when it runs out, making a square wave. Three of them. | terminology.md, the sound chip |
| tone period | A voice's 12-bit tone divider: R0 and R1, R2 and R3, R4 and R5. | terminology.md, the sound chip |
| tune file | The fixed values of a tune, its DTX2 table, and one DTX1 table a source, in one file: its tables, and no code. | SPEC.md 3.3 |
| ubiquitous language | The terms in this glossary. A tracker maps its terms onto them. | requirements.md R3.2 |
| voice | One of three outputs, A, B and C. Each has a volume and a mixing setting. | terminology.md, the sound chip |
| volume | A voice's level: bits 3-0 of R8, R9 or R10, with bit 4 following the envelope instead. | terminology.md, the sound chip |
| `W` | The width of every value of a table: 1, 2 or 4 bytes. One, in a tune's table. | requirements.md R1.1 |
| wave | One cycle of a signal, played through a register at a timer's rate. A source of a cycle's rows, which its entry usually repeats. | SPEC.md 2.2 |
| workspace | What a host allocates for the player: the player's fields, then the state block of the bound tune's image, on a long. | tools.md, the player |
| yielding | One row of a table at a time, in order. A clock advances to a next row and keeps a place in the table. | terminology.md, tables, rows and procedures |
| YM2149 | The sound chip, Yamaha's AY-3-8910, running at 2 MHz on an Atari ST. | terminology.md, the sound chip |
| YMX | The format that plays today. YMXR assumes the name when it is done. | README.md |
| YMXR | This format: one use of DTX, its columns YM2149 and MFP state. | README.md |

## Named, not yet defined

These appear in the documents without an entry above. Each gets one as the
schema settles.

- **tracker** and **tune** - what produces a table, and what a player plays.
- The two kinds of rate: one belonging to an effect, one read from the
  note playing. Terminology.md, rates, describes both without naming
  either.
