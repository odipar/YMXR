; switches.S - the switches the player is assembled with, each at its
; default unless rmac defines it (-dNAME=value), and the figures each
; one selects.

; The raster monitor, off unless the assembly defines it (rmac
; -dYMXR_PERF=1).
                .if     ^^defined YMXR_PERF
                .else
YMXR_PERF       equ     0
                .endif
; A tick drops the interrupt level once its writes are made, so a faster
; timer nests inside a slower one; and it clears its in-service bit, the
; MFP running in software end-of-interrupt mode as TOS leaves it. A host
; that requires neither assembles the player with YMXR_NEST=0 and
; YMXR_AEOI=1 (rmac -dYMXR_NEST=0 -dYMXR_AEOI=1), which cuts 32 cycles
; from a tick that writes a row and 16 from one that ends a source, and
; requires two things of it: no MFP interrupt nests inside another, and
; the MFP's vector register is the player's to set, which it keeps over a
; call and restores at stop. The two go together, since automatic end of
; interrupt sets no in-service bit and only the level the tick sets keeps
; a lower timer out.
                .if     ^^defined YMXR_NEST
                .else
YMXR_NEST       equ     1
                .endif
                .if     ^^defined YMXR_AEOI
                .else
YMXR_AEOI       equ     0
                .endif
                .if     YMXR_AEOI&YMXR_NEST
                .error  "YMXR_AEOI=1 requires YMXR_NEST=0"
                .endif
; Deferred starts (SPEC.md 4.8): a second bank of handlers, an installer
; an effect, and the frame's paths that arm, settle and drop a pending
; start, off unless the assembly defines it (rmac -dYMXR_DEFER=1). A core
; without them reports -1 at init for a tune whose effects used have bit
; 4 set, and a tool binds such a tune on a core with them (BINARIES.md
; 2.1); every other tune runs a core without them, which is as long and
; as fast as the player was before deferred starts.
                .if     ^^defined YMXR_DEFER
                .else
YMXR_DEFER      equ     0
                .endif
; Optional transformation support (SPEC.md 1.10, 3.4). YMXR_MAP=1 adds
; version 10: a lookup can replace only the target's last value; mapping
; 0 writes source values unchanged. A remap changes values while
; preserving source place and timing.
;
; Each tick writes its source key into the low byte of the next read's
; displacement; the high byte selects a 256-byte page, and that read
; writes the selected entry. Init fills each page the bound tune lists in
; the page area after the core, in its form (ymxr_prefill, BINARIES.md
; 1.2 and 3.1): at the frame a start and a remap write the page byte of a
; listed page and copy no entry. A page the list or the area leaves out
; is filled at the frame into the fallback, two pages of one effect in
; each bank (ymxr_lfill). Marker mirrors and setR7 port bits are prepared
; by ymxr_fill.
;
; With lookup support, init accepts versions 7 to 10. Without it, init
; accepts 7 to 9 and reports -1 for 10. Code from ymxr_tick0 onward
; contains handlers, installers and alignment padding; tools measure
; source reach from there (BINARIES.md 2.1).
                .if     ^^defined YMXR_MAP
                .else
YMXR_MAP        equ     0
                .endif
                .if     YMXR_MAP
YMXR_VERSION    equ     $000A           ; reads versions 7 to 10,
                                        ; including mapping columns
YMXR_COLUMNS    equ     34
                .else
YMXR_VERSION    equ     $0009           ; the version it reads, a bound tune
                                        ; whose table is DTX3 (SPEC.md 3.3.5),
                                        ; and 8, read as 9, and 7, whose
                                        ; control columns have bit 3 at 0
YMXR_COLUMNS    equ     30
                .endif
; A tick reads its row through the program counter: the place is a
; displacement in the instruction that reads it, so the read costs 4
; cycles less than an absolute address and the step that moves it 8. The
; displacement is a signed word, so this player requires every row of
; every source within 32,767 bytes of the handlers; init measures each
; source against them and reports -1 for one further off. A square's
; handler and a one-row source's write a value the start patched into
; them, so both cost the same either way.
;
; A host that places a tune further off assembles the player with
; YMXR_PCREL=0 (rmac -dYMXR_PCREL=0), whose ticks read an absolute
; address and reach any offset.
                .if     ^^defined YMXR_PCREL
                .else
YMXR_PCREL      equ     1
                .endif
; The bytes of a handler's loop cell: a row's address, or its
; displacement from the instruction that reads the row.
                .if     YMXR_PCREL
CELL_SIZE       equ     2
                .else
CELL_SIZE       equ     4
                .endif
