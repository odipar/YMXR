# What YMXR has to do

## R0. The house style, held by a test

The specification is what this repository produces. How it is written comes
before what it describes.

- **R0.1** `AGENTS.md` gives the rules, for every document, code comment
  and commit message.
- **R0.2** A test reads every document against a list of phrases struck in
  review, and names the file and line of each hit.
- **R0.3** The test walks the tree for documents. A document is held because
  it is there, not because someone listed it.
- **R0.4** Striking a phrase adds it to the list, in the same change.
- **R0.5** Using a struck phrase again removes it from the list, in the same
  change.

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

YMXR takes the name YMX when it is done. Every requirement here is a
requirement of that format.

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
- **R1.6** A build is compiled for memory, for speed, or for both. Which one
  a player carries is a build choice.
- **R1.7** DTX says nothing about what a column holds.

## R2. What YMXR defines

- **R2.1** What each column holds.
- **R2.2** How a column reaches the YM2149 and the MFP.
- **R2.3** The sample and wave tables a tune carries, which DTX does not
  hold.
- **R2.4** Nothing about the table's packing, its engine, or its ABI.

## R3. The schema

- **R3.1** The schema covers the effects an ST tune uses: SID voices, sync
  buzzers, samples, waveforms, and the others in common use.
- **R3.2** The schema is an abstraction over those effects rather than one
  tracker's arrangement of them, so that trackers map onto it in one
  language.
- **R3.3** A player works nothing out while a tune plays. Every choice is
  compiled into the data.
- **R3.4** R3.3 costs columns, and a column is cheap under DTX.
- **R3.5** At most 32 columns.

## R4. The player

- **R4.1** A player calls `nextRow` once a frame and writes what the row
  gives to the YM2149 and the MFP.
- **R4.2** The bitmap is what a player reads first. A column that did not
  change costs nothing.
- **R4.3** The mapping is the player's work for the frame.
- **R4.4** A frame costs what its row changed. There is no fixed cost per
  frame, and none is wanted.
- **R4.5** The worst frame stays near YMX 0.8.3's, which 13 scanlines
  cover over every shape it produces. That is the call's own work, with
  what the timers take counted apart. R4.4 spends the average; the worst
  frame is what a demo budgets for, and it does not move.
