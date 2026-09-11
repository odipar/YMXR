# What YMXR has to do

## R0. The house style and the terms

This repository produces the specification. How it is written comes
before what it describes, and what things are called comes before both.

- **R0.1** `AGENTS.md` defines the rules, for every document, code comment
  and commit message.
- **R0.2** A test reads every document against a list of phrases struck in
  review, and names the file and line of each hit.
- **R0.3** The test walks the tree for documents. A document is read
  because it is there, not because someone listed it.
- **R0.4** Striking a phrase adds it to the list, in the same change.
- **R0.5** Using a struck phrase again removes it from the list, in the same
  change.
- **R0.6** [glossary.md](glossary.md) lists every term and names the
  document that explains it. The terms are this repository's ubiquitous
  language.
- **R0.7** Every document, comment and name in this repository uses those
  terms, and no second word for a thing that has one.
- **R0.8** A term that changes in the glossary changes everywhere in the
  same change.
- **R0.9** A test reads terminology.md and fails when a term it explains
  has no glossary entry.
- **R0.10** A test reads the documents for what can be recomputed or
  followed: the figures, the citations, the links, the glossary's order,
  the one wrap width.

## DTX, YMXS and YMXR

**DTX** is a data format, and a separate repository. It defines a table of
`R` rows and `C` columns, and no meaning for a column. The table is data: a
compile step or a calling convention belongs to a reader, not to the
format.

**YMXS** is the tune data structure, and a separate repository. It defines
what a tune is - rows of registers and effects, the sources those effects
run, one rate a tune - what each register reaches on the two chips, and
what a player does with a row and a tick. Its SPEC.md is the base SPEC.md
here stands on.

**YMXR** is one use of DTX, and one encoding of YMXS. Its columns are the
settings of an Atari ST's sound chip and timers, and its player turns each
row into writes to them. YMXR defines the meaning of each column and how it
reaches the hardware. How a row is stored, packed or unpacked is outside
YMXR, and so is what a tune is.

The three meet at the table and the structure and nowhere else. DTX defines
the table's shape, YMXS what a row means as music, and YMXR the columns
that carry one into the other.

YMXR assumes the name YMX when it is done. R1 to R6 are requirements of
that format, and bind anyone who writes or plays a tune. R0 binds this
repository.

## R1. What DTX defines

DTX's specification defines these, and 0.4.0 is the release this
repository reads them from. They are recorded here because YMXR is written
against them, and they change in that repository rather than this one.

- **R1.1** A tune's data is a table: `R` rows and `C` columns, every value
  `W` bytes, 1, 2 or 4, and a row `RR` it repeats to once the last row is
  done. Those four are its metadata.
- **R1.2** The layout of the table, row by row or column by column, and
  how a row is read from it, belong to that format and are defined in that
  repository.
- **R1.3** DTX defines no meaning for a column.

## R2. What YMXR defines

- **R2.1** The meaning of each column.
- **R2.2** How a column reaches the YM2149 and the MFP.
- **R2.3** A tune's sources and their index, and the values fixed for a
  whole tune, neither of which DTX defines.
- **R2.4** The two roles that read a tune: a player, which writes to the
  two chips as it goes, and a reader, which reports the result and
  writes to no chip.
- **R2.5** No part of the table's layout or its packing.

## R3. The schema

- **R3.1** The schema covers the YM2149's and the MFP's registers, and the
  effects an ST tune drives them with: SID voices, sync buzzers, samples,
  waveforms, and the others in common use. Coverage is measured against the
  543-tune corpus YMX 0.8.3 was measured on, and an effect no tune in
  it plays is outside the schema until a change puts it in.
- **R3.2** The schema is an abstraction over those effects rather than one
  tracker's arrangement of them. It is the ubiquitous language trackers map
  onto.
- **R3.3** A player computes no decision while a tune plays. Every choice
  is compiled into the data, which costs columns, and a column is cheap.
- **R3.4** At most 32 columns.
- **R3.5** A column is one value, and every column is one byte: a table's
  values are one width (R1.1), and a register is a byte. A register's value
  comes from one column, a column names its target, and a value wider than
  a byte is a column a byte: a period's two halves, a timer's prescaler and
  its count.
- **R3.6** Most columns define a set bit, their top bit: 1 sets the value,
  0 does not, and a value not set is not read - its bits are arbitrary.
  Some columns define a bit for another column, and such a bit is read on
  every row. A row may set a value the register already has: the bit marks
  what to write, not what changed.
- **R3.7** Each column defines its set bit. One column of all the set bits
  would move for every reason any column moves, where a bit beside its
  value moves with that value and packs with it. A column whose value fills
  its width reserves a value for the same purpose, and where the reserved
  value needs a qualifying bit, another column defines it (R3.6).

## R4. The player

- **R4.1** A player runs one method at two rates: a clock advances a table
  one row and a procedure writes that row. The frame clock advances the
  tune's table, once a frame for every table it runs, and a timer advances
  a separate source.
- **R4.2** A column the row does not set costs a player the test and no
  more.
- **R4.3** The mapping is the player's work: the frame's procedure for a
  row of the tune's table, a target's for a row of a source.
- **R4.4** A frame costs the row it reads and what that row sets. It does
  not grow with the count of columns. R3.6 puts what a row sets in the
  writer's hands, and the frame's cost with it.
- **R4.5** The worst frame stays near YMX 0.8.3's, which 13 scanlines
  cover over every shape it produces. That is the work of the call itself,
  with the cost of the timers counted apart. R4.4 spends the average; a
  demo budgets for the worst frame, and it does not move.
- **R4.6** A player keeps what it requires of a value it read. The row is
  not that store: R3.6 leaves an unset value uninterpreted.

## R5. Outside the DTX table

R3 sits behind R2.1 and R4 behind R2.2. This sits behind R2.3, and lists
what a tune requires beyond a row.

- **R5.1** A tune keeps its sources outside the DTX table. Their rows are
  read at a tick's rate rather than a row's, so a row that did not change
  still feeds them.
- **R5.2** Which source plays, on which target, and at what rate, comes
  from the columns.
- **R5.3** A source's rows are values that fit the register its target
  writes, which is YMXS's rule (YMXS, SPEC.md 3.2). The conversion a
  recording needs is the writer's work under R3.3.
- **R5.4** The sources belong to the tune. A player defines none.
- **R5.5** How many sources a tune runs, how large one is, and the content
  of an index entry are SPEC.md's.
- **R5.6** A value fixed for a whole tune is not a column. It would set a
  column once and repeat it on every row after it.
- **R5.7** How often a player is called, and which timers it claims before
  the first row, are the tune's. A player reads one row at a time, so it
  finds neither by reading ahead.

## R6. Version and extension

- **R6.1** A tune records the version it was written for. Where it records
  it, and what a player does with a version it was not built for, are
  SPEC.md's.
- **R6.2** A meaning is fixed once assigned: a column's, a bit's within its
  column, a value's within its field. A later version assigns what this one
  leaves unassigned, and redefines none.
- **R6.3** R3.4's ceiling of 32 stands at this version and at every later
  one.
- **R6.4** A schema outgrowing 32 columns runs a second DTX table beside
  the first, a row of each a frame. The ceiling is one table's.
- **R6.5** Columns are split evenly across the tables a tune runs. A schema
  of 33 columns is 16 and 17, not 32 and 1, so no table is read for the
  sake of one value.
- **R6.6** R4.5's worst frame counts every table a tune runs.
