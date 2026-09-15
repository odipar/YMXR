# glossary

Every term of this repository, one row each: the term, its definition,
and the document that defines it in full. A term is defined once and
used with one meaning in every document, comment and name
(requirements.md R0.6 to R0.8); a term that changes here changes there in
the same change.

A term whose document is YMXS is defined there, and this repository
encodes that structure; a term whose document is DTX is defined there,
and this repository reads that table.

| term | what it is | defined in |
|---|---|---|
| bound tune | A tune file's tables bound with DTX's reader into the layout the player reads: a header, the source index, the image and the DTX1 tables. | BINARIES.md 1 |
| `C` | The column count of a table, 1 to 256 (DTX, SPEC.md 1); at most 32 in a tune's table (R3.4): 30 here, and 1 in a source's. | requirements.md R1.1, R3.4 |
| claim | What a player does to a timer at init, for each effect the effects used byte marks: it stops the timer, writes its vector, clears its pending bit, and sets its enable bit and its mask bit. | terminology.md, 2. The timers |
| column | One field of a row, `W` bytes wide: one byte in a tune's table. | requirements.md R1.1 |
| conformance kit | The tune files under `doc/conformance/tunes`, each with its table unpacked beside it and, in `MANIFEST.txt`, the size and the digest of the tune file, of the unpacked table and of the record a reader produces; a reader is tested against it. | README.md |
| control column | Column 14 + 4i + 2 of effect i: bit 7 the set bit, bit 6 the timer's reset, bit 5 the place's reset, bit 4 the count column's 0 as a value, bits 2 to 0 the select. | SPEC.md 1.9 |
| corpus | The 544 YM files YMX 0.8.3 was tested against, 543 of which read; the figures of experiments.md and performance.md are measured over it. | experiments.md |
| count column | Column 14 + 4i + 3 of effect i: the timer count, the byte written to the timer's data register; 0 where the row leaves it unset, and bit 4 of the control column marks 0 as a value. | SPEC.md 1.9 |
| DAC | The YM2149's ladder of output levels, close to logarithmic: 16 for a volume register, 32 for the envelope. | terminology.md, 1. The sound chip |
| digidrum | A sample run on a voice's volume register: a source of many rows that plays once, at the rate of the recording. | terminology.md, 5. Effects |
| DTX | The table format, a separate repository: `R` rows and `C` columns, every value `W` bytes, in one of three variants. The meaning of a column is left to the format built on it. | README.md |
| effect | A source connected to a target on one timer, at the rate of its prescaler and count (YMXS, SPEC.md 1.7). Four columns encode it: the target column, the source column, the control column and the count column. | YMXS, SPEC.md 1.7; SPEC.md 1.8, 1.9 |
| effect rate | The ticks a second of an effect's timer: 2,457,600 divided by the prescaler and by the count, 0 counting 256. The control column and the count column encode it. | SPEC.md 1.9 |
| effects used | A byte of the tune file, bits 3 to 0: bit i is 1 where a row starts effect i. A player claims the timers of those effects at init, before the first row is written. | SPEC.md 3.3 |
| envelope generator | The YM2149's counter walking one of sixteen shapes at the envelope period. | terminology.md, 1. The sound chip |
| envelope period | R11 and R12, the 16-bit divider of the envelope generator. | terminology.md, 1. The sound chip |
| envelope shape | R13, four bits. Four of the sixteen shapes repeat; the other twelve run once and stop. | terminology.md, 1. The sound chip |
| frame | One call of the player: one row of the tune's table read, and the columns it sets written to the two chips. After the end of a tune that plays once, a call leaves every register as it is and reports -1 (SPEC.md 4). | terminology.md, 4. Frames and ticks |
| frame rate | The frames a second at which the host calls the player, recorded once in the tune file. | SPEC.md 3.3 |
| generator | One of the YM2149's five: three tone, one noise, one envelope. | terminology.md, 1. The sound chip |
| host | The program that calls the player once a frame, provides the workspace, and runs every timer outside the tune's. | BINARIES.md 5 |
| image | DTX's reader packaged with a table: its code, its column table and the table, entered through calls at its first bytes. A bound tune has one; a tune file has the table alone. | BINARIES.md 1 |
| index entry | A 4-byte offset in the tune file: where a source's DTX1 table begins. | SPEC.md 3.1, 3.3 |
| kept value | The target and the count of an effect as the player keeps them from the last row that set them: a row that leaves the column unset leaves the kept value, and bit 6 of the control column writes the kept count (SPEC.md 1.8, 1.9). | requirements.md R4.6 |
| loop | The frames from the one that reads the repeat row to the one that reads the last row. | YMXS, SPEC.md 4.5 |
| marker | Bit 7 of a source's last row, set in that row alone: a tick tests it after the write; where the source repeats, the next tick reads row `RR`, and otherwise the timer stops. | SPEC.md 3.2, 5 |
| metadata | `R`, `C`, `RR` and `W`: the four values that describe a table. | terminology.md, 3. Tables, rows and procedures |
| MFP | The MC68901: four timers, A to D, on a clock of 2,457,600 a second. | terminology.md, 2. The timers |
| mixing | R7, bits 5 to 0: which generators reach each voice. | terminology.md, 1. The sound chip |
| multi file | Several tune files in one file, each under a name. | BINARIES.md 0 |
| noise generator | The YM2149's shift register, one for the three voices, clocked at the noise period. | terminology.md, 1. The sound chip |
| noise period | R6, five bits: the divider of the noise generator. | terminology.md, 1. The sound chip |
| pass | The frames from the one that reads row 0 to the one that reads the last row. | YMXS, SPEC.md 4.5 |
| period | The length of one cycle of a generator, in steps of its clock. | terminology.md, 1. The sound chip |
| place | The row number of a source that the next tick of a timer reads (YMXS, SPEC.md 3.4.2). A tick moves it (YMXS, SPEC.md 5.2), and bit 5 of the control column sets it to 0 (YMXS, SPEC.md 3.4.3). | YMXS, SPEC.md 3.4; SPEC.md 1.9 |
| player | The program that reads the tune's table one row a frame, writes the columns that row sets to the YM2149 and the MFP, and at each tick of a timer writes one row of its source. | requirements.md R4 |
| prescaler | The first divisor of a timer: 4, 10, 16, 50, 64, 100 or 200, its select in the timer control register 1 to 7. | terminology.md, 2. The timers |
| procedure | What a clock calls with a row: it writes the row to the chips. The frame's procedure writes the columns a row of the tune's table sets, a target a row of a source. | terminology.md, 3. Tables, rows and procedures |
| program stub | A block prepended to an SNDH file, making a TOS program: it keeps the vectors and the timer registers it uses, plays the file from the VBL or Timer C, and restores them when a key or the row count ends the program. | BINARIES.md 4 |
| `R` | The row count of a table, 1 upward. | requirements.md R1.1 |
| raster monitor | A build of the player, `YMXR_PERF`, off by default, whose play call sets the background colour red for its duration and yellow for the counted cost of the ticks, and whose tick handlers each set a colour; the cycles are measured off the screen of a cycle-exact machine. | performance.md |
| rate | The ticks a second of a timer (terminology.md 2.2); the frames a second of a tune is its frame rate. | terminology.md, 2. The timers |
| reader | The program that reads a tune file and reports the record of SPEC.md 7, every register write and every timer operation recorded in place of the chips; YMXS, SPEC.md 7.1 names this role a recorder. The conformance kit is written against one. | requirements.md R2.4 |
| record | The lines a reader produces of a tune file: the fixed values on the first line, then one entry a frame. | SPEC.md 7 |
| register | One byte of the YM2149's settings. Sixteen: fourteen are the sound, two are I/O ports. | terminology.md, 1. The sound chip |
| release | What a player does to a claimed timer at stop: it stops the timer, clears its enable bit and its mask bit, and clears its pending bit. | terminology.md, 2. The timers |
| ring | The bytes a column of a DTX2 table unpacks through, one size for the payload (DTX, SPEC.md 2.3): 960 by default; the tools of this repository round it to a multiple of 30, a row's bytes, from 60 to 1,110, the largest multiple of 30 a 16-bit displacement reaches. | tools.md |
| row | One step of a table: `C` values, one a column. | terminology.md, 3. Tables, rows and procedures |
| `RR` | The row a table repeats to after its last row; `R` where the table plays once. | requirements.md R1.1 |
| sample | A recording, one value a tick, played as a source through a voice's volume register at the rate of the recording. | terminology.md, 5. Effects |
| schema | The meaning of each of the 30 columns: an abstraction over the effects of an ST tune rather than one tracker's arrangement of them. | requirements.md R3 |
| select | Bits 2 to 0 of the control column, 1 to 7: the prescaler select the player writes to the timer control register. | SPEC.md 1.9 |
| set bit | Bit 7 of a column: at 1 the row sets the column's value, at 0 the value's bits are arbitrary and left unread. A column whose value fills its byte reserves 0 instead, and a bit of the column beside it marks 0 as a value. | requirements.md R3.6, R3.7 |
| SID voice | A square wave run on a voice's volume register, at a rate in ratio to the note the voice plays. | terminology.md, 5. Effects |
| signal | A series of values at a rate: a square wave, noise, a sample. | terminology.md, 1. The sound chip |
| SNDH core | The player under SNDH's three entries: init keeps the vectors, the control bits and the interrupt bits of the four timers, and exit restores those of the claimed ones. Assembled once and kept in the Java tree and the Go tree; a tool combines it with bound tunes into an SNDH file. | BINARIES.md 2 |
| SNDH file | Three branch entries, SNDH's tags, then the SNDH core, a table of subtunes, the images, the bound tunes and the workspace of a set. | BINARIES.md 3 |
| source | A table a timer advances one row a tick, its target writing each row (YMXS, SPEC.md 3.2). Here a DTX1 table of one column of one byte, repeating at `RR`, bit 7 of its last row the marker; the source column of an effect selects one by number, 1 to 127. | YMXS, SPEC.md 3.2; SPEC.md 2.2, 3.1 |
| source column | Column 14 + 4i + 1 of effect i: bit 7 the set bit, bits 6 to 0 the source number, 1 to 127, or 0, the stop. | SPEC.md 1.8 |
| source index | One index entry a source, in source-number order, from byte 16 of the tune file. | SPEC.md 3.1 |
| square wave | A source of two rows, a level and 0, repeating to row 0. | terminology.md, 5. Effects |
| state block | The bytes DTX's reader keeps for a table while it reads it, sized by the image's format block; the host allocates them at the end of the workspace. | BINARIES.md 1 |
| stop | A source column set to 0: the player writes select 0 to the timer's control register and clears its pending bit. | SPEC.md 1.8 |
| sync buzzer | A source of one row run on `setR13`: each tick restarts the envelope, so the rate of the timer is the pitch. | terminology.md, 5. Effects |
| table | `R` rows of `C` columns, every value `W` bytes, repeating at `RR`. A tune's table and a source's are one kind: the player reads one a row a frame and the other a row a tick. | terminology.md, 3. Tables, rows and procedures |
| target | The procedure a tick calls with a source's row (YMXS, SPEC.md 3.1). `setR0` to `setR13` write the row to that register; a source's target at this version is one whose register ignores bit 7, `setR1`, `setR3`, `setR5`, `setR6`, `setR8` to `setR10` or `setR13` (SPEC.md 2.1); an effect's target column numbers one, 0 to 127. | YMXS, SPEC.md 3.1; SPEC.md 2.1 |
| target column | Column 14 + 4i of effect i: bit 7 the set bit, bits 6 to 0 the target number, 0 to 127. | SPEC.md 1.8 |
| tick | One interrupt of a timer: one row of its source read and written by its target. | terminology.md, 4. Frames and ticks |
| timer | One of the MFP's four. It counts the divided clock down from its count and raises an interrupt at zero. | terminology.md, 2. The timers |
| timer control register | The MFP register with a timer's prescaler select in three bits: 1 to 7 for 4 to 200, 0 for stopped. Timers C and D share one. | terminology.md, 2. The timers |
| timer count | The second divisor of a timer: 1 to 255, and 0, which counts 256. | terminology.md, 2. The timers |
| timer data register | The MFP register with a timer's count. | terminology.md, 2. The timers |
| tone generator | One of the YM2149's three counters that flip their output at the end of each period, producing a square wave. | terminology.md, 1. The sound chip |
| tone period | A voice's 12-bit divider: R0 and R1, R2 and R3, R4 and R5. | terminology.md, 1. The sound chip |
| tracker | A program that produces tunes; its terms map onto the schema (R3.2). | requirements.md R3.2 |
| tune | A rate and a table of rows, with a title, a composer and a writer for display (YMXS, SPEC.md 1.3); a tune file encodes the rate, the table and the title, as its name (SPEC.md 3.3). | YMXS, SPEC.md 1.3; SPEC.md 3 |
| tune file | The values fixed for a tune, its name, its DTX2 table and one DTX1 table a source, in one file (SPEC.md 3.3). A bound tune adds DTX's reader to it. | SPEC.md 3.3 |
| ubiquitous language | The terms of this glossary, used with one meaning in every document, comment and name. | requirements.md R0.6 |
| unit | `k`, the bytes ST4 packs at once, 1, 2 or 4, one for every column of a DTX2 table (DTX, SPEC.md 2.3): 2 by default. `R` and `RR` divide by it (SPEC.md 6, rule 6). | tools.md |
| unset row | A row of the tune's table that leaves every column unset: a frame that reads it leaves every register as it is and every timer running (SPEC.md 6, rule 6). | terminology.md, 3. Tables, rows and procedures |
| version | The word at offset 4 of a tune file, a multi file and a bound tune: $0003. A reader reads a file of this version alone (SPEC.md 3.3). | SPEC.md 3.3 |
| voice | One of the YM2149's three outputs, A, B and C, each with a volume and a mixing setting. | terminology.md, 1. The sound chip |
| volume | R8, R9 or R10: bits 3 to 0 the level of a voice, and bit 4 set selects the level of the envelope instead. | terminology.md, 1. The sound chip |
| `W` | The bytes of every value of a table: 1, 2 or 4. One in a tune's table and in a source's. | requirements.md R1.1 |
| workspace | The bytes the host allocates for the player: `YMXR_FIXED`, the 1,072 bytes of the player's fields, then the state block of the bound tune's image, on a long. | BINARIES.md 1 |
| writer | The program that produces a tune file, a converter or a tracker; it satisfies the rules of SPEC.md 6 and of YMXS, SPEC.md 6. | SPEC.md 6 |
| yielding | Reading the rows of a table one at a time, in order: row 0 first, and after row `R` - 1 row `RR`, or the end where the table plays once. | terminology.md, 3. Tables, rows and procedures |
| YM2149 | The sound chip, Yamaha's AY-3-8910, clocked at 2 MHz on an Atari ST. | terminology.md, 1. The sound chip |
| YMX | The family this repository belongs to: a design document defining how YMXS, YMXR, DTX and ST4 fit together. It was a format and a player until 0.10.1, which YMXR replaces. | README.md |
| YMXR | This format: one use of DTX, its columns the settings of the YM2149 and the MFP, and one encoding of YMXS. | README.md |
| YMXS | The tune data structure, a separate repository: rows of registers and effects, the sources those effects run, and one rate a tune. Its SPEC.md defines the structure; SPEC.md here defines one encoding of it. | ymxs.md |
