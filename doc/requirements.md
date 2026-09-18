# requirements

The requirements of this repository and of the format. R0 binds the
repository: its documents, its comments and its terms. R1 records what
DTX defines. R2 to R6 are requirements of the format and bind a writer,
a player and a reader of a tune (R2.4). A requirement is cited by number,
R3.6; one that rests on DTX or YMXS cites the clause it rests on.

## R0. The house style and the terms

- **R0.1** `AGENTS.md` defines the rules of prose, for every document,
  code comment and commit message.
- **R0.2** A test reads every document and every code comment against a
  list of phrases struck in review, and names the file and line of each
  hit.
- **R0.3** The test walks the tree for documents and for sources, and
  reads each file it finds. In a source the comments are read and the
  code is left unread. The five languages are Java, Go,
  68000 assembly, Python and the shell, and a comment opens differently
  in each.
- **R0.4** Striking a phrase adds it to the list, in the same change.
- **R0.5** Using a struck phrase again removes it from the list, in the
  same change.
- **R0.6** [glossary.md](glossary.md) lists every term and names the
  document that defines it. The terms are the ubiquitous language of
  this repository.
- **R0.7** Every document, comment and name uses those terms, one word
  for each thing.
- **R0.8** A term that changes in the glossary changes everywhere in the
  same change.
- **R0.9** A test reads terminology.md and fails where a term it defines
  is absent from the glossary.
- **R0.10** A test reads the documents for every figure that can be
  recomputed and every reference that can be followed: the figures, the
  citations, the links, the glossary's order and the one wrap width.
- **R0.11** DTX is the table format, a separate repository: a table of
  `R` rows and `C` columns, the meaning of a column left to the format
  built on it. The table is data; DTX's reader defines its compile step
  and its calling convention (DTX, abi.md).
- **R0.12** YMXS is the tune data structure, a separate repository: rows
  of registers and effects, the sources those effects run, one rate a
  tune, what each register reaches on the two chips, and what a player
  does with a row and with a tick. SPEC.md here defines one encoding of
  that structure and cites it.
- **R0.13** YMXR is one use of DTX and one encoding of YMXS: its columns
  are the settings of the sound chip and the timers of an Atari ST, and
  its player writes each row to them. YMXR defines the meaning of each
  column and how it reaches the hardware; DTX defines the layout, the
  packing and the unpacking of a row, and YMXS defines a tune.

## R1. What DTX defines

DTX, SPEC.md defines these; 0.11.8 is the release the Java tree and the
Go tree read. Note: they change in that repository.

- **R1.1** A tune's data is a table: `R` rows and `C` columns, every
  value `W` bytes, 1, 2 or 4, and a row `RR` it repeats to after its last
  row. Those four are its metadata.
- **R1.2** The layout of the table, row by row or column by column, and
  how a row is read from it, are defined in that repository.
- **R1.3** The meaning of a column is left to the format built on DTX.

## R2. What YMXR defines

- **R2.1** The meaning of each column.
- **R2.2** How a column reaches the YM2149 and the MFP.
- **R2.3** A tune's sources and their index, and the values fixed for a
  whole tune, both outside DTX.
- **R2.4** The two roles that read a tune: a player, which writes to the
  two chips as it reads, and a reader, which reports the record with
  every write recorded in place of the chips (SPEC.md 7).
- **R2.5** The layout of the table and its packing are DTX's (R1.2),
  outside YMXR.

## R3. The schema

- **R3.1** The schema covers the registers of the YM2149 and the MFP and
  the effects of the corpus (glossary.md): SID voices, sync buzzers,
  samples and waveforms. An effect absent from the corpus is outside the
  schema until a change puts it in.
- **R3.2** The schema is an abstraction over those effects rather than
  one tracker's arrangement of them: the ubiquitous language a tracker
  maps its terms onto.
- **R3.3** Every choice is compiled into the data, at the cost of
  columns, and a player reads a row and writes it.
- **R3.4** At most 32 columns.
- **R3.5** A column is one value of one byte: a table's values are one
  width (R1.1), and a register is a byte. A register's value comes from
  one column, and a value wider than a byte is one column a byte: the
  two halves of a period.
- **R3.6** A column with a bit to spare defines a set bit, its top bit:
  at 1 the row sets the value, at 0 the value's bits are arbitrary and
  left unread. A column may define a bit for another column: such a bit
  in a register column is read on every row, and one in an effect's
  control column is read where the row sets the control column (SPEC.md
  1.1). A row may set a value the register already has: the bit marks
  what to write, the value changed or the same.
- **R3.7** Each column defines its set bit. A column whose value fills
  its byte reserves a value for the row that leaves it unset, and where
  the reserved value is also a value of the register, a bit of another
  column marks it as one (R3.6). Note: one column of every set bit would
  move where any column moves; a bit beside its value packs with it.

## R4. The player

- **R4.1** A player advances two tables at two rates: a clock advances a
  table one row and a procedure writes that row. The frame clock advances
  each table of the tune one row a frame (R6.4), and a timer advances a
  source.
- **R4.2** A column the row leaves unset costs a player one test.
- **R4.3** A player performs R2.2: the frame's procedure writes a row of
  the tune's table to the chips, and a target writes a row of a source.
- **R4.4** A frame costs the row it reads and the columns that row sets,
  independent of the column count. R3.6 leaves what a row sets to the
  writer, and the frame's cost with it.
- **R4.5** A play call costs at most 6,656 cycles, 13 scanlines of 512,
  the ticks counted apart; performance.md records the costliest call of
  each tune. Note: a demo budgets for the worst frame; R4.4 bounds the
  average.
- **R4.6** A player keeps every value it requires of a row it has read:
  the kept value of an effect is its target and its count. R3.6 leaves an
  unset value uninterpreted, so a row is read once, and a value the
  player requires on a later row is the kept value.

## R5. Outside the DTX table

R3 defines R2.1 and R4 defines R2.2; this section defines R2.3, what a
tune requires beyond a row.

- **R5.1** A tune keeps its sources outside the DTX table: a timer reads
  a source's rows at its ticks, between frames, at the rate the tune's
  rows set (R5.2), so a row of the tune that leaves every column unset
  leaves every timer running.
- **R5.2** The four columns of an effect select the source, the target
  and the rate (SPEC.md 1.8, 1.9).
- **R5.3** A source's rows are values that fit the register its target
  writes (YMXS, SPEC.md 3.2.2); a writer converts a recording to such
  values (R3.3).
- **R5.4** A player reads the sources from the tune file alone (SPEC.md
  3.1).
- **R5.5** SPEC.md 3 defines the source count, the size of a source and
  the index entry.
- **R5.6** A value fixed for a whole tune is recorded once, outside the
  table. Note: as a column it would be set once and repeated on every
  row after.
- **R5.7** The frame rate and the effects used are recorded once (R5.6,
  SPEC.md 3.3); a player reads both before the first row.

## R6. Version and extension

- **R6.1** A tune records the version it was written for. Where it
  records it, and what a player does with another version, are SPEC.md's.
- **R6.2** A meaning is fixed once assigned: a column's, a bit's within
  its column, a value's within its field. A later version assigns what
  this one leaves unassigned and leaves every assigned meaning as it is.
- **R6.3** R3.4's ceiling of 32 binds this version and every later one.
- **R6.4** A schema of more than 32 columns runs a second DTX table
  beside the first, a row of each a frame; the ceiling is one table's.
- **R6.5** The columns are split evenly across the tables a tune runs: a
  schema of 33 columns is 16 and 17.
- **R6.6** R4.5's worst frame counts every table a tune runs.
