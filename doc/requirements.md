# What YMXR has to do

## R0. The house style and the terms

The specification is what this repository produces. How it is written comes
before what it describes, and what things are called comes before both.

- **R0.1** `AGENTS.md` gives the rules, for every document, code comment
  and commit message.
- **R0.2** A test reads every document against a list of phrases struck in
  review, and names the file and line of each hit.
- **R0.3** The test walks the tree for documents. A document is held because
  it is there, not because someone listed it.
- **R0.4** Striking a phrase adds it to the list, in the same change.
- **R0.5** Using a struck phrase again removes it from the list, in the same
  change.
- **R0.6** [glossary.md](glossary.md) lists every term and names the
  document that explains it. The terms are the ubiquitous language of
  R3.2.
- **R0.7** Every document, comment and name in this repository uses those
  terms, and no second word for a thing that has one.
- **R0.8** A term that changes in the glossary changes everywhere in the
  same change.
- **R0.9** A test reads terminology.md and fails when a term it explains
  has no glossary entry.

## DTX and YMXR

**DTX** is a data engine, and a repository of its own. It takes a table of
`R` rows and `C` columns, compiles it into a binary, and gives a caller one
row at a time through a single function. It says nothing about what a
column holds.

**YMXR** is one use of DTX. Its columns hold what an Atari ST's sound chip
and timers are set to, and its player turns each row into writes to them.
YMXR defines what a column holds, and how it reaches the hardware. It
says nothing about how a row is stored, packed or unpacked.

The two meet at one function and nowhere else. DTX fills a row buffer, and
YMXR reads it.

YMXR takes the name YMX when it is done. R1 to R6 are requirements of that
format, and bind anyone who writes or plays a tune. R0 binds this
repository.

## R1. What DTX gives

DTX's specification defines these. They are recorded here because YMXR is
written against them, and they change in that repository rather than this
one.

- **R1.1** The input is a table of `R` rows and `C` columns, in column-major
  order. A column is 1, 2 or 4 bytes wide, and metadata describes the
  columns.
- **R1.2** A compile step turns that input into a binary with an ABI.
- **R1.3** The ABI is one function, `nextRow`, taking a pointer to a mutable
  row buffer.
- **R1.4** The row buffer holds a bitmap of the columns that differ from the
  previous row, then the columns themselves, `col0` through `col(C-1)`.
  `nextRow` sets the bitmap on the call that fills the buffer.
- **R1.5** A build may repeat to an earlier row `RR` once the last row is
  done, so the rows run without end.
- **R1.6** A build is compiled for memory, for speed, or for both.
- **R1.7** DTX says nothing about what a column holds.

## R2. What YMXR defines

- **R2.1** What each column holds.
- **R2.2** How a column reaches the YM2149 and the MFP.
- **R2.3** A tune's sample and wave tables, which DTX does not hold.
- **R2.4** The two roles that read a tune: a player, which writes to the
  two chips as it goes, and a reader, which reports what a tune holds and
  writes to no chip.
- **R2.5** Nothing about the table's packing, its engine, or its ABI.

## R3. The schema

- **R3.1** The schema covers the YM2149's and the MFP's registers, and the
  effects an ST tune drives them with: SID voices, sync buzzers, samples,
  waveforms, and the others in common use.
- **R3.2** The schema is an abstraction over those effects rather than one
  tracker's arrangement of them. It is the ubiquitous language trackers
  map onto.
- **R3.3** A player works nothing out while a tune plays. Every choice is
  compiled into the data.
- **R3.4** R3.3 costs columns, and a column is cheap under DTX.
- **R3.5** At most 32 columns, so the bitmap is one long and a 68000 holds
  it in a register.
- **R3.6** A column 2 or 4 bytes wide holds values that are one thing: a
  period's two halves, or a timer's prescaler and count. Width says what
  belongs together and not what packs well, since correlation across
  columns is what the compile step resolves.
- **R3.7** R3.1's coverage is measured against the 543-tune collection YMX
  0.8.3 was measured on. An effect no tune in it plays is outside R3.1
  until a change puts it in.

## R4. The player

- **R4.1** A player calls `nextRow` once a frame for every table the tune
  runs, and writes what the rows give to the YM2149 and the MFP.
- **R4.2** The bitmap is what a player reads first. A column that did not
  change costs nothing.
- **R4.3** The mapping is the player's work for the frame.
- **R4.4** A frame costs what its row changed. There is no fixed cost per
  frame, and none is wanted.
- **R4.5** The worst frame stays near YMX 0.8.3's, which 13 scanlines
  cover over every shape it produces. That is the call's own work, with
  what the timers take counted apart. R4.4 spends the average; the worst
  frame is what a demo budgets for, and it does not move.

## R5. The tables

R3 sits behind R2.1 and R4 behind R2.2. This sits behind R2.3.

- **R5.1** A tune holds its sample and wave tables outside the DTX table.
  Their values are read at a tick's rate rather than a row's, so a row that
  did not change still feeds them.
- **R5.2** A column selects which table a voice plays, and at what rate.
- **R5.3** A table holds what the registers take. A recording is linear
  amplitudes and a volume register takes a logarithmic index, so the
  conversion is the producer's work under R3.3.
- **R5.4** The tables are the tune's. A player holds none of its own.
- **R5.5** How many tables a tune holds, how large one is, and what an
  entry holds are SPEC.md's.

## R6. Version and extension

- **R6.1** A tune states the version it was written for. Where it states
  it, and what a player does with a version it was not built for, are
  SPEC.md's.
- **R6.2** A column's meaning holds once assigned. A later version assigns
  a column this one leaves unassigned, and redefines none.
- **R6.3** R3.5's ceiling of 32 holds at this version and at every later
  one.
- **R6.4** A schema outgrowing 32 columns runs a second DTX table beside
  the first, one `nextRow` a table a frame. The ceiling is one table's, so
  the bitmap stays one long.
- **R6.5** Columns are split evenly across the tables a tune runs. A schema
  of 33 columns is 16 and 17, not 32 and 1, so no call spends a whole
  `nextRow` on one value.
- **R6.6** R4.5's worst frame counts every table a tune runs.
