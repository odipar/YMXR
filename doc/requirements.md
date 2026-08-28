# What the encoding has to do

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
