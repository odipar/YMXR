# What the encoding has to do

## R1. The operational shape is YMX 0.8.3's

The parts, and what each is for, carry over. What they produce is this
format's own: nothing here binds this format to 0.8.3's output.

- **R1.1** A 68000 player, called once a frame, driving the YM2149's sound
  registers and the MFP's timers.
- **R1.2** A converter reading YM5 and YM6 sources into this format.
- **R1.3** The consumer roles stand: a reader produces the values a frame
  writes, a player drives the chip, a checker reads a file back against the
  rules a player does not check.
- **R1.4** What a source implies, the converter resolves and writes down.
  The player derives nothing it can be told.
