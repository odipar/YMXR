# What the encoding has to do

The format is what these documents state. YMX 0.8.3 is not authoritative
here: it appears as a measurement or a cost, never as a rule.

## R1. The house style, held by a test

The specification is what this repository produces. How it is written comes
before what it describes.

- **R1.1** `AGENTS.md` states the rules, for every document, code comment
  and commit message.
- **R1.2** A test reads every document against a list of phrases struck in
  review, and names the file and line of each hit.
- **R1.3** The test walks the tree for documents. A document is held because
  it is there, not because someone listed it.
- **R1.4** Striking a phrase adds it to the list, in the same change.
- **R1.5** Using a struck phrase again removes it from the list, in the same
  change.

## R2. The operational shape is YMX 0.8.3's

The parts carry over, and what each does. Nothing here states what this
format produces.

- **R2.1** A 68000 player, called once a frame, writing the YM2149's sound
  registers and programming the MFP's timers.
- **R2.2** A converter that reads a YM5 or YM6 source and writes this
  format.
- **R2.3** Three consumers. A reader gives the values a frame writes. A
  player drives the chip. A checker reads a file back against the rules a
  player does not check.
- **R2.4** The converter resolves what a source carries and writes the
  outcome down. The player reads it and compares nothing.

## R3. Two layers

- **R3.1** Layer 0 is a container: `S` streams, each packed by ST4, each
  delivering one value a frame.
- **R3.2** At Layer 0 a stream is an index and a sequence of bytes. Layer 0
  states nothing about what those bytes hold.
- **R3.3** Layer 1 is what the streams hold, and how a consumer reads them.
- **R3.4** A change at Layer 1 leaves Layer 0 as it is.
- **R3.5** YMX 0.8.3 states the two together. Holding them apart is what
  this redesign is for.

## R4. Layer 0's constraints

Layer 0 states five parameters:

| | |
|---|---|
| `S` | how many streams the file holds |
| `O` | how many values each stream carries |
| `N` | the ring size in bytes |
| `C` | how many values one refill decodes |
| the unit | ST4's unit size in bytes |

ST4 and the 68000 bind them. Layer 1 does not lift these.

- **R4.1** One `N` for every stream. A consumer holds one ring of `N` bytes
  for each stream it reads.
- **R4.2** The 68000 bounds `N`. A stream's ring is reached through a
  displacement assembled into the player, and the largest of those fits a
  signed 16-bit displacement.
- **R4.3** A back-reference reaches no further than `N` bytes, so a stream
  decodes inside its own ring.
- **R4.4** `C` divides `N`, and `N` is at least `2C`.
- **R4.5** `C` covers the longest list of streams a consumer of a file
  reads.
- **R4.6** The unit size is 1, 2 or 4. Above 1, `C` and `O` are multiples of
  it.
- **R4.7** A section begins on a long boundary, and so does each stream
  inside it.
