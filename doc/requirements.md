# What the encoding has to do

This document and the specification that follows it state the format. YMX
0.8.3 holds no authority here: where it is named, it is named as evidence of
what an encoding did and what that cost, not as a rule this format follows.

## R1. The house style holds, and a test holds it

The specification is the deliverable. How it is written comes before what it
describes.

- **R1.1** `AGENTS.md` states the rules, for every document, code comment
  and commit message.
- **R1.2** A test reads every document in the tree against the phrases
  struck in review, and names the file and line of each hit.
- **R1.3** The test finds the documents rather than listing them, so a
  document added to the tree is held without being added to anything.
- **R1.4** A phrase struck in review joins the list in the change that
  strikes it. One that is right in a new context comes off the list in the
  change that uses it.

## R2. The operational shape is YMX 0.8.3's

The parts carry over, and what each does. Nothing here states what this
format produces.

- **R2.1** A 68000 player, called once a frame, writing the YM2149's sound
  registers and programming the MFP's timers.
- **R2.2** A converter that reads a YM5 or YM6 source and writes this
  format.
- **R2.3** Three consumers: a reader gives the values a frame writes, a
  player drives the chip, a checker reads a file back against the rules a
  player does not check.
- **R2.4** The converter resolves what a source carries and writes the
  outcome down. The player reads it and compares nothing.

## R3. Two layers

- **R3.1** Layer 0 is a container: `S` streams, each packed by ST4, each
  delivering one value a frame.
- **R3.2** Layer 0 states nothing about what a stream holds. At Layer 0 a
  stream is an index and a sequence of bytes.
- **R3.3** Layer 1 is what the streams hold, and how a consumer reads them.
- **R3.4** The two are stated apart. A change at Layer 1 leaves Layer 0 as
  it is.
- **R3.5** YMX 0.8.3 states the two together. Holding them apart is what
  this redesign is for.
