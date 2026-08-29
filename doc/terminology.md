# terminology

The terms every other document in this repository uses, and for which no
second word is used (requirements.md, R0.6 to R0.8).

The two chips are ported from YMX 0.8.3, which got them right and where
nothing depends on how a tune is stored. What sits on top of them is this
repository's to name, and is written as the schema settles.

---

## The sound chip

A **YM2149**, Yamaha's AY-3-8910, running at 2 MHz on an Atari ST.

A **register** is one byte of chip state. There are sixteen: fourteen steer
the sound, two are peripheral I/O ports the ST borrowed for other duties.
Writing a register is the only way software changes anything, and a register
holds its value until written again.

| register | bits | what it holds |
|---|---|---|
| R0, R1 | 8 + 4 | voice A **tone period**, fine and coarse: one 12-bit number |
| R2, R3 | 8 + 4 | voice B tone period |
| R4, R5 | 8 + 4 | voice C tone period |
| R6 | 5 | **noise period** |
| R7 | 8 | the mixer register: **mixing** - which generators reach which voice - plus the I/O port directions |
| R8, R9, R10 | 5 each | voice A, B, C **volume**: bits 3-0 the level, bit 4 meaning "follow the envelope" |
| R11, R12 | 8 + 8 | **envelope period**, fine and coarse: a 16-bit divider, coarse as a pitch |
| R13 | 4 | **envelope shape** |
| R14, R15 | 8 each | the two I/O ports. Not sound |

A **signal** is a series of values with a rate: a square wave, a run of
noise, a sample. Five **generators** make them:

- three **tone generators**, each a counter that flips its output when it
  runs out, making a square wave;
- one **noise generator**, a shift register producing a random-sounding bit
  pattern;
- one **envelope generator**, a counter walking one of sixteen shapes.
  Normally a note's rise and fall; run fast, the same sweep is a pitch.

How long a generator takes per cycle is its **period**. Bigger period, lower
pitch:

    tone frequency     = 2,000,000 / (16 x tone period)
    noise clock        = 2,000,000 / (16 x noise period)
    envelope frequency = 2,000,000 / (256 x envelope period)

Tone period 284 is about 440 Hz. Envelope period 18 is about 434 Hz - the
same note from the envelope generator.

**Noise period** sets how bright the noise is, not how loud: five bits, 1 to
31. At 1 it is a wide hiss, around 15 a coarser rush, at 31 slow enough to
take on a pitch. One generator feeds all three voices, so only its volume
can differ between them.

**Envelope shapes** are four bits, and only four of the sixteen repeat: two
sawtooths and two triangles. The other twelve run once and hold, which suits
a decay and is useless as an oscillator.

**The envelope's pitch resolution is coarse, and worsens as pitch rises.**
The divisor is 256, so neighbouring periods are far apart: period 18 is 434
Hz, period 17 is 460 Hz - just over a semitone between adjacent settings.
That is the limit on a buzzer part. A sync buzzer's pitch comes from a timer
instead, which at 440 Hz lands within a few cents.

Three **voices**, A, B and C. Each has a **volume** and a **mixing** setting
- which generator signals reach it: tone, noise, both or neither. The volume
scales what arrives. "Follow the envelope" is a real bit, bit 4 of the
volume register: with it set, the level bits are ignored and the envelope
supplies the level.

**The DAC** is a ladder of levels, close to logarithmic: about 3 dB a step,
a factor of 1.4 in amplitude. A volume register picks one of 16 steps, the
envelope walks 32, and the bottom of the ladder is irregular, so a measured
table serves where a formula does not. This matters for **samples**: a
recording is linear amplitudes, the register takes a logarithmic index, so
filtering or resampling happens on the amplitudes and converts afterwards.
The ladder spans about 54 dB top to bottom, the bottom step being a jump of
8 dB where the rest average nearer 3.3, so material with its peaks near the
top keeps the most detail.

**Writing the envelope shape restarts the envelope.** Writing the same shape
twice is a restart, which is the mechanism behind the sync buzzer. A format
therefore needs a way to say "leave the shape alone" on a row that must not
restart it.

---

## The timers

The **MFP** (MC68901) has four timers, A to D. All four are reachable, and
Timer C costs more than the others: it is the operating system's 200 Hz
clock, so a tune that takes it stops that clock and cannot be hosted from a
Timer C hook.

The MFP's own clock runs at 2,457,600 a second, unrelated to the YM2149's. A
timer divides it twice: by a **prescaler**, one of 4, 10, 16, 50, 64, 100 or
200, then by a **timer count**, 1 to 255, and 0, which the MFP reads as 256.

    rate = 2,457,600 / (prescaler x timer count)

The timer counts down at the divided speed and raises an interrupt at zero.
That interrupt is the **tick**. Both divisors are divisors; a generator's
counter is a different thing.

The slowest rate is 48 a second and the fastest 614,400. Above about 25,600
the interrupt alone takes a quarter of an 8 MHz 68000, which is the
practical ceiling. For scale, 69 tunes of the 543-tune collection play
samples, mostly between 5,000 and 6,100 a second.

---

## Two clocks

A tune is stepped at one rate and its effects run at another.

The **frame** is one call to the player, usually 50 a second, and it is the
rate a tune's own values move at: a note changes on a frame, not between
two.

A **tick** is one step of a timer, from 48 to 25,600 a second in practice.
An effect lives here. Everything that makes an ST sound like an ST is a
register written faster than a frame.

---

## Rates

An effect runs at a rate, and a rate comes from one of two places.

A sample's rate is the recording's own. Nothing else sets it, and a note
under it does not move it. A square chopping a voice takes its rate from the
note playing instead, and the two hold in ratio or the pitch moves with
every note.

That difference decides when a rate may change. A rate the effect owns is
settled when the effect starts. A rate taken from a note is renewed as often
as the note may move, which is every frame.

The schema names these two, and this document takes the names from it.

---

## Open

- **Row against frame.** DTX gives rows and a player is called once a frame,
  one for one. Which word this repository uses for the other's sake is not
  settled, and R0.7 asks that only one of them be used for a thing.
