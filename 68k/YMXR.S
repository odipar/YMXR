; YMXR.S - the player: a tune's rows to the YM2149 and the MFP, one row a
; frame, and a source's rows to one register at a timer's rate.
;
; doc/SPEC.md is the contract: section 4 is YMXR_play and section 5 the
; tick handlers. This player reads the bound tune of doc/BINARIES.md: the
; tune file's tables, and no code, bound by a tool with the image DTX
; packaged the table with. The player calls that image's slots (DTX,
; doc/abi.md) and reads the row they return.
;
; The calls, by the jump table at the top of the binary:
;
;   YMXR_init   a0 the bound tune, on an even address; a1 the workspace,
;               on a long. d0 is 0, or -1 for one this player does not
;               read.
;   YMXR_play   a0 the workspace. One row. d0 is 0, or -1 where the tune
;               has played its last row and does not repeat: no register
;               is written then.
;   YMXR_stop   a0 the workspace. The claimed timers stopped, disabled and
;               masked; the three volumes silenced.
;
; Every call clobbers d0-d5 and a0-a5 and leaves d6, d7 and a6 as they
; were. YMXR_play runs in supervisor mode, at any interrupt level: every
; write of a select and a value is one movep, which no tick lands inside.
;
; A call writes the row the call before it read, and reads the next when
; its writes are made: the row's writes then leave a host's clock at one
; offset, where an advance before them would move by what the refill of
; the row costs. Init reads the first row.
;
; The workspace is YMXR_FIXED bytes, then the state block the image's
; format block requires, which the bound tune records at offset 12
; (BINARIES.md): a host allocates YMXR_FIXED plus that. The row a call
; writes is the image's pointer, IM_POINTER of that state block
; (DTX, abi.md 3), which an advance leaves at column 0's value, so the
; player keeps no copy of it.
;
; The host owns the machine. This player saves and restores no vector, no
; timer control and no interrupt enable: around init and stop a host keeps
; what it needs of the vectors at $134, $120, $114 and $110, of TACR, TBCR
; and both nibbles of TCDCR, and of bits 5 and 0 of IERA and IMRA and bits
; 5 and 4 of IERB and IMRB. A timer an effect does not run is left
; alone. The MFP runs in software end-of-interrupt mode, as TOS leaves
; it, and the vectors are the 68000's at zero.
;
; A host also needs a handler at $60 while this player runs. A write here
; that clears a pending or an enable bit between the MFP raising an
; interrupt and the 68000 acknowledging it leaves the MFP with no vector
; to place on the bus, and the 68000 runs exception 24 rather than the
; timer's handler. An rte is the handler: the tick the acknowledge
; belonged to is the one the write cancelled. YMXR_sndh.S keeps the host's vector
; and puts an rte there for as long as a tune plays.
;
; The player patches itself: the row's displacements and the branches
; over the effects the tune does not run at init, and the tick handlers'
; operands at every start. It runs from RAM, and one loaded copy serves
; one workspace.
;
; YMXR_PERF equ 1 assembles the raster monitor in: the play call paints
; the background red for as long as its work runs, one scanline to 512
; cycles, and each tick handler paints a separate colour where the beam
; is. A tick's band is a few hundred cycles wide and hard to read, so
; each handler adds a count of what its instructions cost, which the next
; call burns off as a yellow bar after its work, the register writes among
; them: the bar shows what the handlers cost the frame, and no chip write
; moves for the bar. What a tick costs beyond
; its handler, the 44 cycles the 68000 spends entering an interrupt and
; the 20 on the rte, is where the tick landed and is not counted
; here. The call waits for the display to start before it paints, since
; the VBL fires far above the screen and an unsynced bar lands in the
; top border; the wait is before the red mark, so it is outside the
; figures. Each count is an estimate, in turns of ten cycles,
; and the monitor is for reading a run: with the switch off, the
; default, no byte and no cycle of it remains.

YMXR_MAGIC      equ     $594D5842       ; YMXB, the bound tune

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
; The lookup (SPEC.md 1.10, 3.4): a tune of version 10 maps the last value
; of an effect's target through a transformation, a table of entries that
; a row's key indexes. YMXR_MAP=1 (rmac -dYMXR_MAP=1) assembles it in: a
; core with it reads versions 7 to 10, and a tick of a source through a
; transformation writes the key into the low byte of the displacement of
; the read that follows, whose high byte selects one of two pages of 256
; bytes, and that read writes the entry. A core without it reads versions
; 7 to 9, reports -1 at init for version 10, and assembles to the bytes
; it had before the lookup. In a core with it every byte from ymxr_tick0
; on is a handler, an installer, or the padding that places a lookup
; instance's page read and bank 1, so the reach a tool measures from there
; (BINARIES.md 2.1) leaves out the frame's routines and the pages.
                .if     ^^defined YMXR_MAP
                .else
YMXR_MAP        equ     0
                .endif
                .if     YMXR_MAP
YMXR_VERSION    equ     $000A           ; the version it reads: 10, whose
                                        ; table has the four mapping
                                        ; columns, and 7 to 9
YMXR_COLUMNS    equ     34
                .if     YMXR_DEFER
YMXR_SITES      equ     34              ; the other read sites (ymxr_sites)
                .else
YMXR_SITES      equ     30
                .endif
                .else
YMXR_VERSION    equ     $0009           ; the version it reads, a bound tune
                                        ; whose table is DTX3 (SPEC.md 3.3.5),
                                        ; and 8, read as 9, and 7, whose
                                        ; control columns have bit 3 at 0
YMXR_COLUMNS    equ     30
                .if     YMXR_DEFER
YMXR_SITES      equ     26              ; the other read sites (ymxr_sites)
                .else
YMXR_SITES      equ     22
                .endif
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
MFP_VECTOR      equ     $FFFFFA17       ; bits 7 to 4 the vector's high half,
                                        ; bit 3 the software end of interrupt

PERF_SYNC       equ     8000            ; the wait's ceiling, over a frame
                                        ; of turns, so a call from
                                        ; anywhere runs on
PERF_FRAME      equ     $700            ; the work of the call itself
PERF_BAR        equ     $770            ; the timers' burnt bar
PERF_COLOUR     equ     $FFFF8240       ; the background
PERF_BEAM       equ     $FFFF8209       ; the video address counter's low
                                        ; byte, which moves while the chip
                                        ; fetches pixels
; What a tick of each kind costs the frame, in cycles, with neither the
; interrupt level dropped nor an end of interrupt written. The raster
; monitor counts a tick at its kind's figure and what the two switches
; add to that path, rounded to the nearest turn of ten cycles, and the
; call burns a bar for the total. performance.md, A tick, measures each
; of the five, and PlayerTest checks the five against those figures.
; Under YMXR_PCREL a row is read through a displacement rather than an
; absolute address: the read costs 4 cycles less, the step 8 and the
; loop's test 4, so the three paths that read a row cost less and the two
; that write a patched value cost what they cost.
                .if     YMXR_PCREL
PERF_ON         equ     64      ; a row written, the place stepped
PERF_SQ         equ     56      ; a square's two rows, no place stepped
PERF_ONEROW     equ     32      ; a source of one row, no place stepped
PERF_LOOP       equ     98      ; the marker, the place to row RR
PERF_STOP       equ     108     ; the marker, the timer stopped
PERF_SWITCH     equ     150     ; the marker, a pending start performed
                .else
PERF_ON         equ     76      ; a row written, the place stepped
PERF_SQ         equ     56      ; a square's two rows, no place stepped
PERF_ONEROW     equ     32      ; a source of one row, no place stepped
PERF_LOOP       equ     114     ; the marker, the place to row RR
PERF_STOP       equ     116     ; the marker, the timer stopped
PERF_SWITCH     equ     150     ; the marker, a pending start performed
                .endif

; What each switch adds to a path: the level is dropped on the three
; paths that write a row's value, and an end of interrupt on all five.
PERF_DROP       equ     16*YMXR_NEST
PERF_END        equ     16*(1-YMXR_AEOI)

; The bound tune (BINARIES.md).
TF_VERSION      equ     4
TF_RATE         equ     6
TF_EFFECTS      equ     8
TF_SOURCES      equ     9
TF_STATE        equ     12
TF_IMAGE        equ     16
TF_TABLE        equ     20              ; where this tune's table is in it
TF_INDEX        equ     24
                .if     YMXR_MAP
TF_TRANS        equ     24              ; version 10: the transformations'
                                        ; block, a signed long from the
                                        ; bound tune's first byte, and the
                                        ; source index 4 bytes on
                .endif

; The image's format block (DTX, abi.md 1), and its slots.
IM_FORMAT       equ     16
IM_VARIANT      equ     19
IM_INIT         equ     0
IM_METADATA     equ     4
IM_ADVANCE      equ     12
IM_POINTER      equ     8               ; the state block's row pointer, which
                                        ; an advance leaves at column 0's value

; A DTX1 table's header, and where a source's rows begin (DTX, SPEC.md 1).
DT_ROWS         equ     4
DT_COLUMNS      equ     8
DT_REPEAT       equ     10
DT_PAYLOAD      equ     16

; The workspace.
WS_TUNE         equ     0               ; the bound tune
WS_IMAGE        equ     4               ; the image in it
WS_ROWS         equ     8               ; rows to the last, or to the wrap
WS_R            equ     12
WS_RR           equ     16
WS_EFFECTS      equ     20              ; a byte: the effects the tune runs
WS_ARMING       equ     22              ; a byte: set while a deferred start
                                        ; is armed, so widestart patches the
                                        ; record's mirror and the other bank
WS_VECTOR       equ     21              ; a byte: the MFP's vector register as
                                        ; init found it, kept where the player
                                        ; runs automatic end of interrupt
WS_SOURCES      equ     24              ; a source's first row and its loop
                                        ; cell, eight bytes each, resolved at
                                        ; init: the source column's seven bits
                                        ; reach 127, so the room is there whether
                                        ; the tune names that many or one. A
                                        ; start indexes it off the workspace
                                        ; with a byte's displacement, so it is
                                        ; first
WS_WIDE         equ     1040            ; a source of several columns: its loop
                                        ; cell and the stride from one column
                                        ; to the next, eight bytes each and one
                                        ; a source, resolved at init beside it
WS_FX           equ     2056            ; four effects, FX_SIZE each. The
                                        ; instances below add FX_SIZE a time
                                        ; rather than multiplying: rmac reads
                                        ; an expression left to right, so
                                        ; WS_FX+FX_SIZE*2 is (WS_FX+FX_SIZE)*2
FX_TARGET       equ     0               ; the target the player keeps
FX_SELECT       equ     1               ; the select it last read
FX_COUNT        equ     2               ; the count it last read
FX_SHAPE        equ     3               ; which handler the timer's vector
                                        ; points at: 0 the general one, $FF
                                        ; the square's, 1 the one-row
                                        ; source's. The last two encode the
                                        ; place as an immediate
FX_FIRST        equ     4               ; the place at row 0: the running
                                        ; source's first row, and the first row
                                        ; of the column the running handler
                                        ; writes first where it writes several
FX_FIRST2       equ     8               ; the same for the second column a
FX_FIRST3       equ     12              ; handler of several writes, and the
                                        ; third
FX_ROWS         equ     16              ; R of the running source, where it
                                        ; has several columns or is counted
FX_PENDING      equ     20              ; a byte: a deferred start is armed
                                        ; and the running handler hooked
FX_HOOKW        equ     22              ; the word the hook wrote over
FX_ARMED        equ     24              ; the handler the armed start aims at
FX_HOOK         equ     28              ; the hooked word's address
ARMOFF          equ     32              ; the mirror: the armed start's target,
                                        ; shape, places at row 0 and R, at
                                        ; the offsets above plus ARMOFF
FX_APLACE       equ     52              ; the row the armed start is at
FX_ABANK        equ     56              ; the bank it is armed in, 0 or BANK
                .if     YMXR_MAP
; The lookup's bytes (SPEC.md 1.10.3): the kept mapping, 0 the identity
; and 1 to 127 a transformation; the mapping the running source plays
; through; a square's level at row 0, which the place's reset writes; for
; each bank the page its lookup instances read, 0 or 1; and for each of its
; two pages the transformation it was filled with, 0 before a fill, and the
; fill kind: 0 plain, 1 the mirror of a marked column, 2 the ports of
; setR7 (ymxr_fill).
                .if     YMXR_DEFER
FX_MAP          equ     21              ; the kept mapping
FX_AMAP         equ     33              ; an armed start's mapping and its
FX_ALEVEL       equ     34              ; square's level at row 0, in the
                                        ; mirror's bytes of FX_SELECT and
                                        ; FX_COUNT, which the arming leaves
                                        ; as they are; the switch copies the
                                        ; level into FX_LEVEL
FX_LEVEL        equ     58              ; the running square's level at row 0
FX_RUN          equ     59              ; the running source's mapping
FX_USE          equ     60              ; the page in use, bank 0's then 1's
FX_PAGE         equ     64              ; two bytes a page, 4 a bank
FX_SIZE         equ     72
WS_TRANS        equ     2344            ; the transformations' block, a long
YMXR_FIXED      equ     2348
WS_STATE        equ     2348            ; the image's state block, on a long
                .else
FX_RUN          equ     20
FX_MAP          equ     21
FX_USE          equ     22
FX_LEVEL        equ     23
FX_PAGE         equ     24
FX_SIZE         equ     28
WS_TRANS        equ     2168
YMXR_FIXED      equ     2172
WS_STATE        equ     2172
                .endif
                .if     (WS_FX+(4*FX_SIZE))<>WS_TRANS
                .error  "the transformations' long is apart from the records"
                .endif
                .else
                .if     YMXR_DEFER
FX_SIZE         equ     64
YMXR_FIXED      equ     2312
WS_STATE        equ     2312            ; the image's state block, on a long
                .else
FX_SIZE         equ     20              ; up to FX_ROWS, which a place's
YMXR_FIXED      equ     2136            ; reset on a counted source reads
WS_STATE        equ     2136
                .endif
                .endif

; The chip.
YM_SELECT       equ     $FFFF8800
YM_PORTS        equ     $C0             ; R7's port directions, the ST's

; The MFP: the timers by effect (SPEC.md 2.3).
;                       control   data      vector  IER     bit  keep  shift
; effect 0, Timer A     $FFFA19   $FFFA1F   $134    $FFFA07 5    $F0   0
; effect 1, Timer D     $FFFA1D   $FFFA25   $110    $FFFA09 4    $F0   0
; effect 2, Timer B     $FFFA1B   $FFFA21   $120    $FFFA07 0    $F0   0
; effect 3, Timer C     $FFFA1D   $FFFA23   $114    $FFFA09 5    $0F   4
; IER+4 is the pending register, IER+8 the in-service, IER+12 the mask.
; A pending bit and an in-service bit clear on a written 0 and are unmoved
; by a written 1, so the whole byte is written rather than read and
; written back. bclr's read and its write are two bus cycles, and the MFP
; latches a tick of another timer between them where one comes: the
; write-back would carry the 0 that read had and drop it. The enable and
; the mask are written by software alone, so bclr is used there.

; A tick handler's operands, patched at a start, are at fixed offsets
; into it; the equates are below the handlers, since the raster monitor's
; marks are between the three blocks a handler is made of and the
; assembler measures the distances.

; ---------------------------------------------------------------- macros

; A column into d1, or into d2. The displacement is i strides, patched at
; init: rmac would fold a zero away, so a placeholder is here.
                .macro  READ    col
c\{col}:
                move.b  $7FFF(a1),d1
                .endm

                .macro  READ2   col
c\{col}:
                move.b  $7FFF(a1),d2
                .endm

; A second read of a column, or a column ored into d1, at a separate site
; that init patches from the table of sites (ymxr_sites).
                .macro  QREAD   col
q\{col}:
                move.b  $7FFF(a1),d1
                .endm

; The same, at a column's third site: the copy of steps 6 and 7 the row
; that sets no envelope column falls into.
                .macro  RREAD   col
r\{col}:
                move.b  $7FFF(a1),d1
                .endm

; The count column at a deferred start's site (4.8), which init patches
; from the table of sites as it patches the two above.
                .macro  DREAD   col
d\{col}:
                move.b  $7FFF(a1),d1
                .endm

; The mapping column at a remap's site (SPEC.md 1.10.2), which init patches
; from the table of sites as it patches the three above.
                .macro  MREAD   col
m\{col}:
                move.b  $7FFF(a1),d1
                .endm

; A column with a set bit, written at that third site.
                .macro  RWRITE  col,select
                RREAD   \col
                bpl.s   .no\~
                move.w  #\select,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no\~:
                .endm

                .macro  QOR     col
q\{col}:
                or.b    $7FFF(a1),d1
                .endm

; A column that fills its byte, on the row where the column beside it is
; 0: written where it is not 0, with no further test.
                .macro  PLAIN   col,select
                QREAD   \col
                beq.s   .no\~
                move.w  #\select,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no\~:
                .endm

; A column with a set bit, to its register: the column read, and where
; it is set the select put above it and one movep for the pair.
                .macro  WRITE   col,select
                READ    \col
                bpl.s   .no\~
                move.w  #\select,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no\~:
                .endm

; A column that fills its byte, reserving 0: written where it is not 0,
; or where the bit beside it, in d2, says 0 is the value.
                .macro  RESERVED col,bit,select
                READ    \col
                bne.s   .go\~
                btst    #\bit,d2
                beq.s   .no\~
.go\~:          move.w  #\select,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no\~:
                .endm

; A tone period: the coarse column into d2, for its bit 6 and its
; value, then the fine column against it.
                .macro  TONE    fine,coarse,fsel,csel
                READ2   \coarse
                READ    \fine
                bne.s   .go\~
                tst.b   d2                      ; a coarse column of 0 has no
                beq.s   .nc\~                   ; set bit and no bit beside
                btst    #6,d2
                beq.s   .nf\~
.go\~:          move.w  #\fsel,d0
                move.b  d1,d0
                movep.w d0,(a2)
.nf\~:          tst.b   d2
                bpl.s   .nc\~
                move.w  #\csel,d1
                move.b  d2,d1
                movep.w d1,(a2)
.nc\~:
                .endm

; One effect: the target kept, the source started or stopped, the timer's
; count and select written, with the timer's and the place's reset.
                .macro  EFFECT  fx,ct,cs,cc,cn,cm,ctrl,data,ier,bit,keep,shift,shared,tick,sq,one,two,three,vector
; The head is the effect's first read where the tune runs the effect, and
; a branch over its columns where it does not: init writes the one word
; that separates them, and the word after it has the read's
; displacement or the branch's, by which head it is.
\{tick}_over:
q\{ct}:         bra.w   \{tick}_done
                QOR     \cs                     ; the set bits at once
                QOR     \cc
                .if     YMXR_MAP
                QOR     \cm                     ; and the mapping's
                .endif
                bmi.s   \{tick}_some
                QREAD   \cn                     ; the count alone, or no column
                beq.w   \{tick}_done
                move.b  d1,\data.w              ; the count, the timer running on
                move.b  d1,\fx+FX_COUNT(a0)
                bra.w   \{tick}_done
\{tick}_some:
                READ2   \cc
                tst.b   d2                      ; the timer's reset stops the
                bpl.s   \{tick}_kept            ; timer before the source is
\{tick}_rs:     btst    #6,d2                   ; switched, so no tick of the
                beq.s   \{tick}_kept            ; old rate reads the new
\{tick}_rstop:  andi.b  #\keep,\ctrl.w          ; source, and the tick latched
                move.b  #255-(1<<\bit),(\ier+4).w ; before the stop is dropped
\{tick}_kept:
                READ    \ct
                bpl.s   .t\~
                and.b   #$7F,d1
                move.b  d1,\fx+FX_TARGET(a0)
.t\~:           READ    \cs
\{tick}_unset:  bpl.w   \{tick}_rate            ; _retune where a row defers
                and.w   #$7F,d1                 ; a start, and bra.w _check
\{tick}_set:    beq.w   \{tick}_stop            ; below (4.8)
; a start: the source as init resolved it, the handler aimed at its rows
\{tick}_now:
                .if     YMXR_MAP
                bra.w   \{tick}_mapped          ; init aims it at _start in a
\{tick}_start:                                  ; tune that maps no value
                .endif
                lsl.w   #3,d1
                movea.l WS_SOURCES-8(a0,d1.w),a4    ; its first row
                move.l  WS_SOURCES-4(a0,d1.w),d0    ; the loop cell: row RR,
\{tick}_pick:   bmi.w   \{tick}_shaped  ; 0 where it plays once,
                                                    ; -1 for a square and -2
                                                    ; for a source of one row
; the handler's three operands are under one mask, since the timer may
; run: a tick between the target register and the place writes the old
; source's row to the new target, and one between the place and the loop
; cell reads the new place against the cell the old source left
\{tick}_aim:    lea     \tick(pc),a5
; The three behind level 7, so a tick reads none of them half made. The
; window is three writes wide and costs no measurable jitter: the ticks
; of a tune running one effect keep their period to 0.31 per cent with
; it and to 0.31 without, where the reference player's keep 0.33. The
; place the row does not reset is read in the window too, since a tick
; between the read and the write steps it (1.9). Under YMXR_PCREL the
; arithmetic that turns a row's address into a displacement is in the
; window as well, a dozen instructions more.
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  \fx+FX_TARGET(a0),TICK_SEL(a5)
                move.l  a4,d1                   ; the place: the first row,
                btst    #5,d2                   ; or the row the running
                bne.s   \{tick}_put             ; handler is at, modulo R of
                move.b  \fx+FX_SHAPE(a0),d3     ; this source (1.8.4): every
                beq.s   \{tick}_tickat          ; handler that steps a place
                bpl.s   \{tick}_other           ; keeps it at one offset, a
                .if     YMXR_DEFER              ; square in bit 7 of its
                movea.l \vector.w,a3            ; immediate, and a source of
                .else                           ; one row is at row 0. With
                lea     \sq(pc),a3              ; two banks the running one
                .endif                          ; is the one the vector
                tst.b   SQ_VAL(a3)              ; names
                bpl.s   \{tick}_put
                moveq   #1,d3
                bra.s   \{tick}_putm
\{tick}_other:  cmp.b   #1,d3
                beq.s   \{tick}_put
                movea.l \vector.w,a3
                bra.s   \{tick}_same
\{tick}_tickat:
                .if     YMXR_DEFER|YMXR_MAP
                movea.l \vector.w,a3
                .else
                lea     \tick(pc),a3
                .endif
\{tick}_same:
                .if     YMXR_PCREL
                move.w  TICK_PTR(a3),d3         ; the place, a displacement
                ext.l   d3                      ; from the instruction that
                lea     TICK_PTR(a3),a3         ; reads the row
                add.l   a3,d3
                .else
                move.l  TICK_PTR(a3),d3
                .endif
                sub.l   \fx+FX_FIRST(a0),d3     ; its row number
\{tick}_putm:   move.l  DT_ROWS-DT_PAYLOAD(a4),d4   ; R, out of the source's
                cmp.l   d4,d3                       ; DTX1 header
                bcs.s   .inr\~
                bsr.w   ymxr_rem
.inr\~:         add.l   d3,d1
\{tick}_put:
                .if     YMXR_PCREL
                lea     TICK_PTR(a5),a3
                sub.l   a3,d1
                move.w  d1,TICK_PTR(a5)
                move.l  d0,d3                   ; the loop cell, 0 where the
                beq.s   .once\~                 ; source plays once
                sub.l   a3,d3
.once\~:        move.w  d3,TICK_CELL(a5)
                .else
                move.l  d1,TICK_PTR(a5)
                move.l  d0,TICK_CELL(a5)
                .endif
                move.w  (sp)+,sr
                move.l  a4,\fx+FX_FIRST(a0)
                move.l  a5,\vector.w
                clr.b   \fx+FX_SHAPE(a0)
; the start placed the source, so the row's place reset is made: a tick
; before the select steps the place on, and the reset below would undo it
                bclr    #5,d2
                bra.w   \{tick}_rate

; A start of a source of several columns (SPEC.md 3.1.3), in the routine
; every effect shares: the effect alone is here, since the branch
; above has a byte's displacement and the routine is further off than
; that. The routine leaves
; the handler its columns pick, or the general handler's shape where the
; source is one row.
\{tick}_widen:  moveq   #(\fx-WS_FX)/FX_SIZE,d4
                bsr.w   ymxr_widestart
                beq.w   \{tick}_one
                move.l  a5,\vector.w
                bclr    #5,d2
                bra.w   \{tick}_rate

; The cell separates the two shapes: -1 two rows alternating, -2 one
; row repeating. Init drops this test where the tune uses one of the two
; and not the other, the branch above going straight to that shape.
\{tick}_shaped: addq.l  #1,d0
\{tick}_wide:   bne.s   \{tick}_one
; A square: the two rows into the handler's immediate and the difference
; of the two into the one instruction that moves between them. The row the
; immediate is at is the place, and a start that moves no place leaves
; the half it is in, which bit 7 marks.
\{tick}_square: lea     \sq(pc),a5
                move.b  (a4),d3                 ; the first row
                move.b  1(a4),d4                ; the marker
                move.b  d3,d5
                eor.b   d4,d5
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  \fx+FX_TARGET(a0),SQ_SEL(a5)
                move.b  d5,SQ_MASK(a5)
                btst    #5,d2
                bne.s   .first\~
                .if     YMXR_DEFER|YMXR_MAP
                movea.l \vector.w,a3            ; the running handler: a
                .endif                          ; square at the row of its
                move.b  \fx+FX_SHAPE(a0),d0     ; immediate, a source of one
                beq.s   .tick\~                 ; row at row 0, and every
                bpl.s   .other\~                ; other at its place, whose
                .if     YMXR_DEFER|YMXR_MAP     ; row number modulo 2 is the
                tst.b   SQ_VAL(a3)              ; row of the two (1.8.4).
                .else                           ; With one bank the running
                tst.b   SQ_VAL(a5)              ; square is this one
                .endif
                bmi.s   .marked\~
                bra.s   .first\~
.other\~:       cmp.b   #1,d0
                beq.s   .first\~
                .if     (YMXR_DEFER|YMXR_MAP)=0
                movea.l \vector.w,a3
                .endif
                bra.s   .fromrow\~
.tick\~:
                .if     (YMXR_DEFER|YMXR_MAP)=0
                lea     \tick(pc),a3
                .endif
.fromrow\~:
                .if     YMXR_PCREL
                move.w  TICK_PTR(a3),d0
                ext.l   d0
                add.l   a3,d0
                add.l   #TICK_PTR,d0
                .else
                move.l  TICK_PTR(a3),d0
                .endif
                sub.l   \fx+FX_FIRST(a0),d0
                btst    #0,d0
                bne.s   .marked\~
.first\~:       move.b  d3,SQ_VAL(a5)
                bra.s   .aimed\~
.marked\~:      move.b  d4,SQ_VAL(a5)
.aimed\~:       move.l  a5,\vector.w
                move.w  (sp)+,sr
                move.l  a4,\fx+FX_FIRST(a0)
                st      \fx+FX_SHAPE(a0)
                .if     YMXR_MAP
                move.b  d3,\fx+FX_LEVEL(a0)     ; row 0's level, for the reset
                .endif
                bclr    #5,d2
                bra.w   \{tick}_rate

; A source of one row: the row into the handler's immediate. The
; handler moves no place, so a start resets none.
\{tick}_one:    lea     \one(pc),a5
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  \fx+FX_TARGET(a0),ONE_SEL(a5)
                move.b  (a4),ONE_VAL(a5)
                move.l  a5,\vector.w
                move.w  (sp)+,sr
                move.l  a4,\fx+FX_FIRST(a0)
                move.b  #1,\fx+FX_SHAPE(a0)
                bclr    #5,d2
                bra.w   \{tick}_rate
                .if     YMXR_MAP
; A start in a tune that maps a value (SPEC.md 1.10.2), which init aims
; the branch at _now at: the mapping column, or the kept mapping where the
; row leaves it unset, is the mapping the source runs through. The
; identity runs the start above; a transformation the routine every
; effect shares. d1 the source.
\{tick}_mapped: move.w  d1,d3
                READ    \cm                     ; the mapping column
                bpl.s   .kept\~
                and.b   #$7F,d1
                move.b  d1,\fx+FX_MAP(a0)
.kept\~:        move.b  \fx+FX_MAP(a0),\fx+FX_RUN(a0)
                bne.s   .look\~
                move.w  d3,d1
                bra.w   \{tick}_start
.look\~:        moveq   #(\fx-WS_FX)/FX_SIZE,d4
                bsr.w   ymxr_lstart
                bclr    #5,d2
                bra.w   \{tick}_rate
; A remap (1.10.2): a row that sets the mapping column and leaves the
; source column unset, which init aims _unset at in a tune that maps a
; value. A row that sets neither reads the rate.
\{tick}_remapck:
                MREAD   \cm
                bpl.w   \{tick}_rate
                and.b   #$7F,d1
                moveq   #(\fx-WS_FX)/FX_SIZE,d4
                bsr.w   ymxr_remap
                bra.w   \{tick}_rate
; The timer's reset in a tune that maps a value, which init aims the
; reset's test at: d3 is the control register's byte before the reset
; stops the timer, so a remap on the row reads the timer as it was before
; the row, a remap, then a retune (1.10.2).
\{tick}_rmk:    btst    #6,d2
                beq.w   \{tick}_kept
                move.b  \ctrl.w,d3
                bra.w   \{tick}_rstop
                .endif
                .if     YMXR_DEFER
; A tune whose rows defer a start (SPEC.md 3.3, 4.8) has init aim three
; branches of each effect here: the timer's reset, which a deferred start
; makes at the switch; a start or a stop, with a start pending or one to
; arm; and a row that leaves the source column unset. Every other tune
; runs the branches the assembler wrote, and the frame of a version 7
; tune.
\{tick}_rck:    btst    #6,d2
                beq.w   \{tick}_kept
                btst    #3,d2
                bne.w   \{tick}_kept
                bra.w   \{tick}_rstop
\{tick}_check:  beq.s   \{tick}_drop            ; source 0, the and's Z: a stop
                tst.b   \fx+FX_PENDING(a0)      ; a start pending on the timer:
                bne.s   \{tick}_pending         ; switched, dropped or replaced
\{tick}_go:     tst.b   d2                      ; a deferred start
                bpl.w   \{tick}_now
                btst    #3,d2
                beq.w   \{tick}_now
; on a running timer it is armed, its rate and the handler it aims at
; written into the installer and the running handler hooked, and the row
; leaves the timer as it is; on a stopped timer it is a start at the
; frame. d1 the source.
\{tick}_defer:  move.w  d1,d3
                DREAD   \cn                     ; the count column
                moveq   #(\fx-WS_FX)/FX_SIZE,d4
                bsr.w   ymxr_arm
                beq.w   \{tick}_done
                bclr    #3,d2                   ; the timer is stopped: d1 the
                bra.w   \{tick}_now             ; source, started at once
; the record follows a switch the tick has made, a start at the frame
; drops the pending start, and a deferred start replaces it
\{tick}_pending:
                moveq   #(\fx-WS_FX)/FX_SIZE,d4
                moveq   #1,d5                   ; dropped
                tst.b   d2
                bpl.s   .pend\~
                btst    #3,d2
                beq.s   .pend\~
                moveq   #0,d5                   ; replaced: the record settled
.pend\~:        bsr.w   ymxr_pend
                bra.s   \{tick}_go
\{tick}_drop:   tst.b   \fx+FX_PENDING(a0)      ; a stop drops a pending start
                beq.w   \{tick}_stop
                moveq   #(\fx-WS_FX)/FX_SIZE,d4
                moveq   #1,d5
                bsr.w   ymxr_pend
                bra.w   \{tick}_stop
; a row that leaves the source column unset leaves a pending start
; pending, and reads the record a switch has made
\{tick}_retune: tst.b   \fx+FX_PENDING(a0)
                beq.w   \{tick}_rate
                moveq   #(\fx-WS_FX)/FX_SIZE,d4
                moveq   #0,d5
                bsr.w   ymxr_pend
                bra.w   \{tick}_rate
                .endif

\{tick}_stop:   andi.b  #\keep,\ctrl.w          ; select 0: the timer stops
                move.b  #255-(1<<\bit),(\ier+4).w ; and drops its latched tick
\{tick}_rate:   READ    \cn
                tst.b   d2
                bpl.s   .live\~
                btst    #6,d2
                beq.s   .set\~
; the timer's reset: stopped above, the count, then the select. Bit 4
; marks a count column of 0 as the value the MFP counts 256 for (1.9), so
; the kept count is used only where the row sets neither.
                tst.b   d1
                bne.s   .cnt\~
                btst    #4,d2
                bne.s   .cnt\~
                move.b  \fx+FX_COUNT(a0),d1
.cnt\~:         move.b  d1,\data.w
                move.b  d1,\fx+FX_COUNT(a0)
                bra.s   .sel\~
.live\~:        tst.b   d1
                beq.s   \{tick}_done
                move.b  d1,\data.w            ; the count, the timer running on
                move.b  d1,\fx+FX_COUNT(a0)
                bra.s   \{tick}_done
.set\~:         tst.b   d1
                bne.s   .put\~
                btst    #4,d2                 ; a count of 0, marked a value
                beq.s   .sel\~
.put\~:         move.b  d1,\data.w
                move.b  d1,\fx+FX_COUNT(a0)
.sel\~:         move.b  d2,d1
                and.b   #7,d1
                move.b  d1,\fx+FX_SELECT(a0)
                .if     \shift
                lsl.b   #\shift,d1
                .endif
; the control register needs one write: a select written to a running
; timer neither stops nor reloads the running count (SPEC.md 1.9), and
; select 0 in front of the new select stops the timer and costs it the
; prescaler's partial count. Timer A's and Timer B's control register is
; the timer's alone, so one move writes it and no interrupt can fall
; inside an instruction; Timers C and D share theirs, and only there
; does the read, the mask and the or run at level 7.
                .if     \shared
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  \ctrl.w,d0
                and.b   #\keep,d0
                or.b    d1,d0
                move.b  d0,\ctrl.w
                move.w  (sp)+,sr
                .else
                move.b  d1,\ctrl.w
                .endif
                btst    #5,d2                 ; the place's reset, on the
                beq.s   \{tick}_done                ; handler the effect runs
                tst.b   \fx+FX_SHAPE(a0)
                beq.s   \{tick}_place
\{tick}_reset:  bpl.s   \{tick}_done    ; a source of one row is at
                .if     YMXR_DEFER              ; row 0 and moves no place;
                movea.l \vector.w,a5            ; with two banks the square
                .else                           ; is the one the vector
                lea     \sq(pc),a5              ; names
                .endif
                .if     YMXR_MAP
                move.b  \fx+FX_LEVEL(a0),SQ_VAL(a5) ; row 0's level, one
                .else                           ; write, which no tick splits
                movea.l \fx+FX_FIRST(a0),a3
                move.b  (a3),SQ_VAL(a5)
                .endif
                bra.s   \{tick}_done
; The place's reset on a handler of several columns, in the routine every
; effect shares: the effect alone is here, since the branch that
; reaches it has a byte's displacement and the routine is further off.
\{tick}_wrow:   moveq   #(\fx-WS_FX)/FX_SIZE,d3
                bsr.w   ymxr_widereset
                bra.s   \{tick}_done

\{tick}_place:
                .if     YMXR_DEFER|YMXR_MAP
                movea.l \vector.w,a5
                .else
                lea     \tick(pc),a5
                .endif
                .if     YMXR_PCREL
                move.l  \fx+FX_FIRST(a0),d1
                lea     TICK_PTR(a5),a3
                sub.l   a3,d1
                move.w  d1,TICK_PTR(a5)
                .else
                move.l  \fx+FX_FIRST(a0),TICK_PTR(a5)
                .endif
\{tick}_done:
                .endm

; One timer claimed, where the tune runs its effect: stopped, aimed at
; its handler, enabled and unmasked.
                .macro  CLAIM   i,ctrl,keep,vector,ier,bit,tick
                btst    #\i,WS_EFFECTS(a6)
                beq.s   .no\~
                andi.b  #\keep,\ctrl.w
                lea     \tick(pc),a3
                move.l  a3,\vector.w
                move.b  #255-(1<<\bit),(\ier+4).w
                bset    #\bit,\ier.w
                bset    #\bit,(\ier+12).w
.no\~:
                .endm

; One timer handed back: stopped, disabled, masked, its pending dropped.
                .macro  RELEASE i,ctrl,keep,ier,bit
                btst    #\i,WS_EFFECTS(a6)
                beq.s   .no\~
                andi.b  #\keep,\ctrl.w
                bclr    #\bit,\ier.w
                bclr    #\bit,(\ier+12).w
                move.b  #255-(1<<\bit),(\ier+4).w
.no\~:
                .endm

; The monitor's mark: the background kept on the stack and this handler's
; colour written.
                .macro  MARK    colour
                .if     YMXR_PERF
                move.w  PERF_COLOUR.w,-(sp)
                move.w  #\colour,PERF_COLOUR.w
                .endif
                .endm

; The other end of a mark: this tick's cost counted, in turns of ten
; cycles, and the background put back. The path's cycles are its kind's
; figure, the end of interrupt every path writes, and the level the two
; that write a row's value drop. It is after the write the tick
; makes and before the end of interrupt, so a nested tick of a higher
; timer marks and unmarks inside it.
                .macro  COUNT   cycles,drop
                .if     YMXR_PERF
                move.l  a0,-(sp)
                lea     ymxr_perf_acc(pc),a0
                add.w   #(\cycles+\drop+PERF_END+5)/10,(a0)
                movea.l (sp)+,a0
                move.w  (sp)+,PERF_COLOUR.w
                .endif
                .endm

; A tick (SPEC.md 5): the place's row to the target, the place advanced;
; on the marker, the place to row RR, or the timer stopped. No register
; is used: the place is the write's operand, stepped in place. The
; interrupt level drops once the select and the value are written, so a
; higher timer may nest, and the in-service bit is cleared by hand, as
; software end-of-interrupt mode asks.
                .macro  TICK    name,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_row:    move.b  #8,YM_SELECT.w          ; +3: the target register
                .if     YMXR_PCREL
                move.b  \{name}_cell(pc),YM_SELECT+2.w ; +8: the place, a
                .else                           ; displacement under YMXR_PCREL
                move.b  ($12345678).l,YM_SELECT+2.w ; +8: the place
                .endif
                bmi.s   \{name}_last
                .if     YMXR_NEST
\{name}_sr:     move.w  #$2500,sr
                .endif
\{name}_step:
                .if     YMXR_PCREL
                addq.w  #1,($12345678).l        ; +2: the place, stepped
                .else
                addq.l  #1,($12345678).l        ; +2: the place, stepped
                .endif
                COUNT   PERF_ON,PERF_DROP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_last:
\{name}_mark:
                .if     YMXR_PCREL
                tst.w   ($12345678).l           ; +2: the loop cell
                beq.s   \{name}_halt
                move.w  ($12345678).l,($12345678).l ; +10 the cell, +14 the
                .else                           ; place
                tst.l   ($12345678).l           ; +2: the loop cell
                beq.s   \{name}_halt
                move.l  ($12345678).l,($12345678).l ; +10 the cell, +14 the place
                .endif
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_halt:   andi.b  #\keep,\ctrl.w
; The timer may have counted to zero again while this handler ran, which
; latches a tick the stop above does not withdraw: at a rate whose
; period is near the handler's cost the two race, and that tick would
; read a place past the source's last row. The row that stops an effect
; drops it the same way (EFFECT, .stop).
                move.b  #255-(1<<\bit),(\ier+4).w
                COUNT   PERF_STOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_cell:
                .if     YMXR_PCREL
                dc.w    0                       ; the loop cell
                .else
                dc.l    0                       ; the loop cell
                .endif
                .if     YMXR_DEFER
; A start pending on the timer writes one word at the head of the last
; row's path, a bra.s here, so the source ends at its last row and the
; effect's installer performs the start (SPEC.md 4.8).
\{name}_hook:   bra.w   \inst
                .endif
                .endm

; A tick of a source whose one column fills its byte: every bit of the
; byte is the register's, so bit 7 is a value rather than a marker and
; the end of the source is in a counter beside the place. A word
; counts the rows left; the subq that steps it leaves Z on the row last,
; and the loop reloads it with the rows of a pass through the loop. The register, the
; place, the counter and that reload are the immediates a start patches,
; so this handler uses no register either. A source whose column leaves
; bit 7 runs the handler above, which reads the marker and costs 20
; cycles less (doc/performance.md).
;
; The two branch targets are named rather than local: the labels the
; patching reads are between a branch and its target, and a label ends
; the run a local one belongs to.
                .macro  TICKC   name,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_row:    move.b  #8,YM_SELECT.w          ; +3: the target register
                .if     YMXR_PCREL
                move.b  \{name}_cell(pc),YM_SELECT+2.w ; +8: the place, a
\{name}_step:   addq.w  #1,($12345678).l        ; displacement under
                .else                           ; YMXR_PCREL; +2: the place,
                move.b  ($12345678).l,YM_SELECT+2.w ; stepped
\{name}_step:   addq.l  #1,($12345678).l
                .endif
\{name}_cnt:    subq.w  #1,($12345678).l        ; +2: the rows left
                beq.s   \{name}_last
                .if     YMXR_NEST
                move.w  #$2500,sr
                .endif
                COUNT   PERF_ON,PERF_DROP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_last:
\{name}_mark:
                .if     YMXR_PCREL
                tst.w   ($12345678).l           ; +2: the loop cell
                beq.s   \{name}_halt
                move.w  ($12345678).l,($12345678).l ; +10 the cell, +14 the
                .else                           ; place
                tst.l   ($12345678).l           ; +2: the loop cell
                beq.s   \{name}_halt
                move.l  ($12345678).l,($12345678).l ; +10 the cell, +14 the place
                .endif
\{name}_reld:   move.w  #1,($12345678).l        ; +2: the rows of a pass, +4:
                COUNT   PERF_LOOP,0             ; the counter
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_halt:   andi.b  #\keep,\ctrl.w
                move.b  #255-(1<<\bit),(\ier+4).w
                COUNT   PERF_STOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_cell:
                .if     YMXR_PCREL
                dc.w    0                       ; the loop cell
                .else
                dc.l    0                       ; the loop cell
                .endif
\{name}_left:   dc.w    0                       ; the rows left
                .if     YMXR_DEFER
\{name}_hook:   bra.w   \inst                   ; a pending start, as above
                .endif
                .endm

; A tick of a target that writes several registers (SPEC.md 2.1): a
; select and a value a column, a place a column stepped where the row is
; not the last, and the column the target names for the marker written
; last, so the move that writes it leaves the marker in N and this
; handler tests it as the general one does. A start puts the columns in
; that order, and the places, the registers and the loop cells are the
; immediates it patches, so this handler uses no register either. A tune
; whose sources are one column enters none of this.
                .macro  TICKW   name,columns,counted,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_sel1:   move.b  #8,YM_SELECT.w          ; +3: the first register
                .if     YMXR_PCREL
\{name}_ptr1:   move.b  \{name}_cells(pc),YM_SELECT+2.w ; +2: the first place
\{name}_sel2:   move.b  #8,YM_SELECT.w
\{name}_ptr2:   move.b  \{name}_cells(pc),YM_SELECT+2.w
                .if     \columns=3
\{name}_sel3:   move.b  #8,YM_SELECT.w
\{name}_ptr3:   move.b  \{name}_cells(pc),YM_SELECT+2.w
                .endif
                .else
\{name}_ptr1:   move.b  ($12345678).l,YM_SELECT+2.w ; +2: the first place
\{name}_sel2:   move.b  #8,YM_SELECT.w
\{name}_ptr2:   move.b  ($12345678).l,YM_SELECT+2.w
                .if     \columns=3
\{name}_sel3:   move.b  #8,YM_SELECT.w
\{name}_ptr3:   move.b  ($12345678).l,YM_SELECT+2.w
                .endif
                .endif
                .if     \counted
\{name}_cnt:    subq.w  #1,($12345678).l        ; +2: the rows left, which a
                beq.s   \{name}_last            ; source of whole bytes ends on
                .else
                bmi.s   \{name}_last            ; the marker of the row last
                .endif                          ; written
                .if     YMXR_NEST
                move.w  #$2500,sr
                .endif
                .if     YMXR_PCREL
\{name}_step1:  addq.w  #1,($12345678).l        ; a place a column, stepped
\{name}_step2:  addq.w  #1,($12345678).l
                .if     \columns=3
\{name}_step3:  addq.w  #1,($12345678).l
                .endif
                .else
\{name}_step1:  addq.l  #1,($12345678).l        ; a place a column, stepped
\{name}_step2:  addq.l  #1,($12345678).l
                .if     \columns=3
\{name}_step3:  addq.l  #1,($12345678).l
                .endif
                .endif
                COUNT   PERF_ON,PERF_DROP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_last:
                .if     YMXR_PCREL
\{name}_loopt:  tst.w   ($12345678).l           ; the loop cell of column 0
                beq.s   \{name}_halt
\{name}_loop1:  move.w  ($12345678).l,($12345678).l ; the cell, the place
\{name}_loop2:  move.w  ($12345678).l,($12345678).l
                .if     \columns=3
\{name}_loop3:  move.w  ($12345678).l,($12345678).l
                .endif
                .else
\{name}_loopt:  tst.l   ($12345678).l           ; the loop cell of column 0
                beq.s   \{name}_halt
\{name}_loop1:  move.l  ($12345678).l,($12345678).l ; the cell, the place
\{name}_loop2:  move.l  ($12345678).l,($12345678).l
                .if     \columns=3
\{name}_loop3:  move.l  ($12345678).l,($12345678).l
                .endif
                .endif
                .if     \counted
\{name}_reld:   move.w  #1,($12345678).l        ; +2: the rows of a pass, +4:
                .endif                          ; the counter
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_halt:   andi.b  #\keep,\ctrl.w
                move.b  #255-(1<<\bit),(\ier+4).w
                COUNT   PERF_STOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_cells:
                .if     YMXR_PCREL
                dc.w    0,0,0                   ; the loop cell a column
                .else
                dc.l    0,0,0                   ; the loop cell a column
                .endif
                .if     \counted
\{name}_left:   dc.w    0                       ; the rows left
                .endif
                .if     YMXR_DEFER
\{name}_hook:   bra.w   \inst                   ; a pending start, as above
                .endif
                .endm

; A square's tick: a source of two rows that repeats to row 0 is two
; values and no more, so this handler encodes them as its
; immediate and moves between them by the difference of the two. It
; steps no place, tests no marker and reads no row. The place SPEC.md
; 1.9 defines for a square is which of the two rows the immediate is at,
; and bit 7 tells them apart, the last row of a source being its marker
; (3.2). A source of any other shape runs the handler above.
                .macro  SQUARE  name,eoi,eoir,colour,inst
\name:
                MARK    \colour
\{name}_sel:    move.b  #8,YM_SELECT.w          ; +3: the target register
\{name}_val:    move.b  #$0F,YM_SELECT+2.w      ; +3: the row it is at
                .if     YMXR_NEST
                move.w  #$2500,sr
                .endif
\{name}_tog:    eori.b  #$0F,($12345678).l      ; +3 the two rows' difference,
\{name}_after:  COUNT   PERF_SQ,PERF_DROP       ; +4 the row's address
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .if     YMXR_DEFER
; A start pending on the timer writes a bra.s here over the eori's first
; word: the row written last ends the source where it is the marker, row
; 1, and the installer performs the start; at row 0 the eori below moves
; to row 1 as the handler's does (SPEC.md 4.8).
\{name}_hook:   btst    #7,\{name}_val+3(pc)
                bne.w   \inst
\{name}_htog:   eori.b  #$0F,($12345678).l      ; +3 and +4, as the eori above
                bra.s   \{name}_after
                .endif
                .endm

; A source of one row repeating writes that row on every tick and moves
; no place (SPEC.md 1.9, 3.2), so this handler encodes the row as its
; immediate and writes it. It steps no place, tests no marker, reads no
; row and reads no loop cell. The place SPEC.md 1.9 defines for such a source
; is row 0, which it is at from the start, so bit 5 of the control
; column places no requirement on it. The target belongs to the effect, as in
; the two handlers above: a start writes the register into the
; select, and the source's shape picks the handler where the register
; does not.
                .macro  ONEROW  name,eoi,eoir,colour,inst
\name:
                MARK    \colour
\{name}_sel:    move.b  #8,YM_SELECT.w          ; +3: the target register
\{name}_val:    move.b  #$0F,YM_SELECT+2.w      ; +3: the source's one row
\{name}_end:
                .if     YMXR_NEST
                move.w  #$2500,sr
                .endif
                COUNT   PERF_ONEROW,PERF_DROP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .if     YMXR_DEFER
; A start pending on the timer writes a bra.s here over the first word
; after the row's write: the one row is the last (SPEC.md 4.8).
\{name}_hook:   bra.w   \inst
                .endif
                .endm

; The word a hook writes: a bra.s to the stub, or a nop where the stub
; follows the word, as in a one-row handler of the lean tick, whose word
; is its rte; a bra.s of 0 would read the next word as a displacement.
                .macro  HOOKW   p
                .if     (\{p}_HOOK-\{p}_LAST-2)=0
\{p}_HOOKW      equ     $4E71
                .else
\{p}_HOOKW      equ     $6000|(\{p}_HOOK-\{p}_LAST-2)
                .endif
                .endm

; The handlers of a target that writes several registers, the same way:
; a register and a place a column, a place a column stepped, the loop
; cell read and the cells written into the places. The two macro
; instances lay out two shapes, so a shape's offsets are its four
; handlers'.
                .macro  TWAT    p,name
\{p}_SEL1       equ     \{name}_sel1-\name+3
\{p}_PTR1       equ     \{name}_ptr1-\name+2
\{p}_SEL2       equ     \{name}_sel2-\name+3
\{p}_PTR2       equ     \{name}_ptr2-\name+2
\{p}_STEP1      equ     \{name}_step1-\name+2
\{p}_STEP2      equ     \{name}_step2-\name+2
\{p}_LOOPT      equ     \{name}_loopt-\name+2
\{p}_LOOP1R     equ     \{name}_loop1-\name+2
\{p}_LOOP1W     equ     \{name}_loop1-\name+6
\{p}_LOOP2R     equ     \{name}_loop2-\name+2
\{p}_LOOP2W     equ     \{name}_loop2-\name+6
\{p}_CELLS      equ     \{name}_cells-\name
\{p}_LAST       equ     \{name}_loopt-\name
                .if     YMXR_DEFER
\{p}_HOOK       equ     \{name}_hook-\name
                .endif
                .endm

; The lookup instances, beside TICK, TICKC and TICKW: a tick writes
; its place's byte, the key, into the low byte of the displacement of the
; read that follows, whose high byte selects one of the effect's two
; pages, and that read writes the entry to the chip. A marked column's page
; has entry k + 128 equal to entry k with bit 7 set, so the read leaves the
; marker in N; a counted source ends on its counter. Each kind stops at the
; marker by a beq.w to one halt an effect: the general handler's, whose
; instructions are the same, or with the monitor an LHALT, whose count is
; the lookup's.

; The key's write and the page's read. The place is the key write's
; source, at +8 as in TICK.
                .macro  LKEY    name,ptr,cells,page
                .if     YMXR_PCREL
\{name}_\{ptr}: move.b  \{name}_\{cells}(pc),($12345678).l ; +2 the place,
                .else                           ; +4 the key's address
\{name}_\{ptr}: move.b  ($12345678).l,($12345678).l ; +2 the place, +6 the
                .endif                          ; key's address
\{name}_key:    move.b  \page(pc),YM_SELECT+2.w ; +2 the page, +3 the key
                .endm

; A column the instance writes as TICKW writes it.
                .macro  LROW    name,ptr,cells
                .if     YMXR_PCREL
\{name}_\{ptr}: move.b  \{name}_\{cells}(pc),YM_SELECT+2.w
                .else
\{name}_\{ptr}: move.b  ($12345678).l,YM_SELECT+2.w
                .endif
                .endm

; The general and the counted lookup instance of one column.
                .macro  TICKL   name,counted,page,halt,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_row:    move.b  #8,YM_SELECT.w          ; +3: the target register
                LKEY    \name,ptr,cell,\page
                .if     \counted
\{name}_step:
                .if     YMXR_PCREL
                addq.w  #1,($12345678).l        ; +2: the place, stepped
                .else
                addq.l  #1,($12345678).l
                .endif
\{name}_cnt:    subq.w  #1,($12345678).l        ; +2: the rows left
                beq.s   \{name}_last
                .if     YMXR_NEST
\{name}_sr:     move.w  #$2500,sr
                .endif
                .else
                bmi.s   \{name}_last
                .if     YMXR_NEST
\{name}_sr:     move.w  #$2500,sr
                .endif
\{name}_step:
                .if     YMXR_PCREL
                addq.w  #1,($12345678).l        ; +2: the place, stepped
                .else
                addq.l  #1,($12345678).l
                .endif
                .endif
                COUNT   PERF_ON,PERF_DROP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_last:
\{name}_mark:
                .if     YMXR_PCREL
                tst.w   ($12345678).l           ; +2: the loop cell
                beq.w   \halt
\{name}_loop:   move.w  ($12345678).l,($12345678).l ; +2 the cell, +6 the
                .else                           ; place
                tst.l   ($12345678).l
                beq.w   \halt
\{name}_loop:   move.l  ($12345678).l,($12345678).l
                .endif
                .if     \counted
\{name}_reld:   move.w  #1,($12345678).l        ; +2: the rows of a pass, +4:
                .endif                          ; the counter
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_cell:
                .if     YMXR_PCREL
                dc.w    0                       ; the loop cell
                .else
                dc.l    0
                .endif
                .if     \counted
\{name}_left:   dc.w    0                       ; the rows left
                .endif
                .if     YMXR_DEFER
\{name}_hook:   bra.w   \inst
                .endif
                .endm

; A lookup instance of several columns: column \look of \columns is the
; one mapped (SPEC.md 1.10.1), and every other is written as TICKW writes
; it.
                .macro  TICKWL  name,columns,counted,look,page,halt,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_sel1:   move.b  #8,YM_SELECT.w          ; +3: the first register
                .if     \look=1
                LKEY    \name,ptr1,cells,\page
                .else
                LROW    \name,ptr1,cells
                .endif
\{name}_sel2:   move.b  #8,YM_SELECT.w
                .if     \look=2
                LKEY    \name,ptr2,cells,\page
                .else
                LROW    \name,ptr2,cells
                .endif
                .if     \columns=3
\{name}_sel3:   move.b  #8,YM_SELECT.w
                .if     \look=3
                LKEY    \name,ptr3,cells,\page
                .else
                LROW    \name,ptr3,cells
                .endif
                .endif
                .if     \counted
\{name}_cnt:    subq.w  #1,($12345678).l        ; +2: the rows left
                beq.s   \{name}_last
                .else
                bmi.s   \{name}_last
                .endif
                .if     YMXR_NEST
\{name}_sr:     move.w  #$2500,sr
                .endif
                .if     YMXR_PCREL
\{name}_step1:  addq.w  #1,($12345678).l
\{name}_step2:  addq.w  #1,($12345678).l
                .if     \columns=3
\{name}_step3:  addq.w  #1,($12345678).l
                .endif
                .else
\{name}_step1:  addq.l  #1,($12345678).l
\{name}_step2:  addq.l  #1,($12345678).l
                .if     \columns=3
\{name}_step3:  addq.l  #1,($12345678).l
                .endif
                .endif
                COUNT   PERF_ON,PERF_DROP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_last:
                .if     YMXR_PCREL
\{name}_loopt:  tst.w   ($12345678).l
                beq.w   \halt
\{name}_loop1:  move.w  ($12345678).l,($12345678).l
\{name}_loop2:  move.w  ($12345678).l,($12345678).l
                .if     \columns=3
\{name}_loop3:  move.w  ($12345678).l,($12345678).l
                .endif
                .else
\{name}_loopt:  tst.l   ($12345678).l
                beq.w   \halt
\{name}_loop1:  move.l  ($12345678).l,($12345678).l
\{name}_loop2:  move.l  ($12345678).l,($12345678).l
                .if     \columns=3
\{name}_loop3:  move.l  ($12345678).l,($12345678).l
                .endif
                .endif
                .if     \counted
\{name}_reld:   move.w  #1,($12345678).l
                .endif
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_cells:
                .if     YMXR_PCREL
                dc.w    0,0,0
                .else
                dc.l    0,0,0
                .endif
                .if     \counted
\{name}_left:   dc.w    0
                .endif
                .if     YMXR_DEFER
\{name}_hook:   bra.w   \inst
                .endif
                .endm

; The pad to a residue: the next instance's page read, \key bytes into
; it, has its displacement word on \page modulo 256, so the page byte
; selects a page and the key indexes it.
                .macro  LPAD    key,page
                ds.b    (\page-(*+\key))&255
                .endm

; The stop at the marker under the monitor, one an effect for its eight
; lookup kinds.
                .macro  LHALT   name,eoi,eoir,ctrl,keep,ier,bit
\name:          andi.b  #\keep,\ctrl.w
                move.b  #255-(1<<\bit),(\ier+4).w
                COUNT   PERF_STOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .endm

; A lookup kind's four instances linked at init, in the bank d6 names, as
; the identity's handlers are: the addresses its steps and its loop read,
; its key's address, its counter at 1, and the page byte of page 0, out of
; the table of page bytes from \kind(a4) on, eight bytes an effect.
                .macro  LINKKEY p
                lea     \{p}_KEY(a5),a3
                move.l  a3,\{p}_KEYA(a5)
                move.b  (a2),\{p}_PAGE(a5)
                addq.l  #8,a2
                lea     LOOK_STRIDE(a5),a5
                .endm

                .macro  LINK1   p,first,kind,counted
                lea     \first(pc),a5
                adda.w  d6,a5
                lea     \kind(a4),a2
                moveq   #3,d2
.l\~:           lea     \{p}_PTR(a5),a3
                move.l  a3,\{p}_STEP(a5)
                move.l  a3,\{p}_LOOPW(a5)
                lea     \{p}_CELL(a5),a3
                move.l  a3,\{p}_LOOPT(a5)
                move.l  a3,\{p}_LOOPR(a5)
                .if     \counted
                lea     \{p}_LEFT(a5),a3
                move.l  a3,\{p}_CNTA(a5)
                move.l  a3,\{p}_RELDA(a5)
                move.w  #1,\{p}_LEFT(a5)
                .endif
                LINKKEY \p
                dbra    d2,.l\~
                .endm

                .macro  LINKW   p,first,kind,three,counted
                lea     \first(pc),a5
                adda.w  d6,a5
                lea     \kind(a4),a2
                moveq   #3,d2
.l\~:           lea     \{p}_CELLS(a5),a3
                move.l  a3,\{p}_LOOPT(a5)
                move.l  a3,\{p}_LOOP1R(a5)
                addq.l  #CELL_SIZE,a3
                move.l  a3,\{p}_LOOP2R(a5)
                .if     \three
                addq.l  #CELL_SIZE,a3
                move.l  a3,\{p}_LOOP3R(a5)
                .endif
                lea     \{p}_PTR1(a5),a3
                move.l  a3,\{p}_STEP1(a5)
                move.l  a3,\{p}_LOOP1W(a5)
                lea     \{p}_PTR2(a5),a3
                move.l  a3,\{p}_STEP2(a5)
                move.l  a3,\{p}_LOOP2W(a5)
                .if     \three
                lea     \{p}_PTR3(a5),a3
                move.l  a3,\{p}_STEP3(a5)
                move.l  a3,\{p}_LOOP3W(a5)
                .endif
                .if     \counted
                lea     \{p}_LEFT(a5),a3
                move.l  a3,\{p}_CNT(a5)
                move.l  a3,\{p}_RELDA(a5)
                move.w  #1,\{p}_LEFT(a5)
                .endif
                LINKKEY \p
                dbra    d2,.l\~
                .endm

; A stepping handler's kind as the lookup's routines read it: its effect-0
; instance from ymxr_tick0, the stride to the next effect's, its columns
; less 1, its first loop cell, its rows left and rows of a pass (0 where
; it counts none), its page byte (0 for the identity), the shape the
; record names it by, its fill kind, and a register and a place a column.
KD_BASE         equ     0
KD_STEP         equ     2
KD_COLS         equ     4
KD_CELLS        equ     6
KD_LEFT         equ     8
KD_RELD         equ     10
KD_PAGE         equ     12
KD_SHAPE        equ     14
KD_FILL         equ     15
KD_SEL          equ     16              ; column 1's, then 2's and 3's, four
KD_PTR          equ     18              ; bytes a column
KD_SIZE         equ     28
                .macro  KIND    base,step,cols,cells,left,reld,page,shape,fill,s1,p1,s2,p2,s3,p3
                dc.w    \base-ymxr_tick0,\step,\cols-1,\cells,\left,\reld,\page
                dc.b    \shape,\fill
                dc.w    \s1,\p1,\s2,\p2,\s3,\p3
                .endm

; The offsets of a lookup kind, measured off its effect-0 instance as the
; handlers' are, and the assertions on its instances.
                .macro  LOOKAT1 p,name,counted
\{p}_SEL        equ     \{name}_row-\name+3
\{p}_PTR        equ     \{name}_ptr-\name+2
\{p}_STEP       equ     \{name}_step-\name+2
\{p}_LOOPT      equ     \{name}_mark-\name+2
\{p}_LOOPR      equ     \{name}_loop-\name+2
\{p}_LOOPW      equ     \{name}_loop-\name+6
\{p}_CELL       equ     \{name}_cell-\name
\{p}_LAST       equ     \{name}_mark-\name
                .if     \counted
\{p}_CNTA       equ     \{name}_cnt-\name+2
\{p}_RELD       equ     \{name}_reld-\name+2
\{p}_RELDA      equ     \{name}_reld-\name+4
\{p}_LEFT       equ     \{name}_left-\name
                .endif
                LOOKAT  \p,\name
                .endm

                .macro  LOOKATW p,name,three,counted
\{p}_SEL1       equ     \{name}_sel1-\name+3
\{p}_PTR1       equ     \{name}_ptr1-\name+2
\{p}_STEP1      equ     \{name}_step1-\name+2
\{p}_LOOP1R     equ     \{name}_loop1-\name+2
\{p}_LOOP1W     equ     \{name}_loop1-\name+6
\{p}_SEL2       equ     \{name}_sel2-\name+3
\{p}_PTR2       equ     \{name}_ptr2-\name+2
\{p}_STEP2      equ     \{name}_step2-\name+2
\{p}_LOOP2R     equ     \{name}_loop2-\name+2
\{p}_LOOP2W     equ     \{name}_loop2-\name+6
                .if     \three
\{p}_SEL3       equ     \{name}_sel3-\name+3
\{p}_PTR3       equ     \{name}_ptr3-\name+2
\{p}_STEP3      equ     \{name}_step3-\name+2
\{p}_LOOP3R     equ     \{name}_loop3-\name+2
\{p}_LOOP3W     equ     \{name}_loop3-\name+6
                .endif
\{p}_PTR        equ     \{p}_PTR1
\{p}_LOOPT      equ     \{name}_loopt-\name+2
\{p}_CELLS      equ     \{name}_cells-\name
\{p}_LAST       equ     \{name}_loopt-\name
                .if     \counted
\{p}_CNT        equ     \{name}_cnt-\name+2
\{p}_RELD       equ     \{name}_reld-\name+2
\{p}_RELDA      equ     \{name}_reld-\name+4
\{p}_LEFT       equ     \{name}_left-\name
                .endif
                LOOKAT  \p,\name
                .endm

                .macro  LOOKAT  p,name
\{p}_KEY        equ     \{name}_key-\name+3
\{p}_KEYA       equ     \{name}_key-\name-4     ; the key write's destination
\{p}_PAGE       equ     \{name}_key-\name+2
\{p}_SIZE       equ     \{name}_end-\name
                .if     YMXR_NEST
\{p}_SR         equ     \{name}_sr-\name
                .endif
                .if     YMXR_DEFER
\{p}_HOOK       equ     \{name}_hook-\name
                .if     (\{p}_HOOK-\{p}_LAST-2)>127
                .error  "a lookup instance has its hook past a bra.s"
                .endif
                .endif
                .if     \{p}_PTR<>TICK_PTR
                .error  "a lookup instance has its place at another offset than TICK_PTR"
                .endif
                .if     (\{p}_KEY-1)<>KEY_\{p}
                .error  "a lookup instance reads its page at another offset than its KEY_ equate"
                .endif
                .if     \{p}_SIZE>LOOK_STRIDE
                .error  "a lookup instance is larger than LOOK_STRIDE"
                .endif
                .endm

; An instance's read on its effect's pages modulo 256 and within reach of
; them, and the instance inside its bank.
                .macro  LCHECK  inst,page,lo,hi
                .if     ((\page-(\{inst}_key+2))&255)<>0
                .error  "a lookup instance reads its page off the residue"
                .endif
                .if     ((\{inst}_key+2)-\page)>32768
                .error  "a lookup instance is past the reach of its pages"
                .endif
                .if     (\inst<\lo)|(\{inst}_end>\hi)
                .error  "a lookup instance is outside its bank"
                .endif
                .endm

; Bank 1's instance BANK bytes from its twin in bank 0.
                .macro  LTWIN   inst
                .if     (\{inst}b-\inst)<>BANK
                .error  "a lookup instance of bank 1 is apart from its twin in bank 0"
                .endif
                .endm

; A kind's four instances LOOK_STRIDE apart.
                .macro  LSTRIDE k
                .if     ((\{k}1-\{k}0)<>LOOK_STRIDE)|((\{k}2-\{k}1)<>LOOK_STRIDE)|((\{k}3-\{k}2)<>LOOK_STRIDE)
                .error  "a lookup kind has its instances apart from LOOK_STRIDE"
                .endif
                .endm

                .if     YMXR_MAP
; The displacement word of the page read, from the first byte of an
; instance of each kind: the monitor's mark, the select's write, then the
; writes of the columns before the key's, then the key's write. rmac
; places an instance by these before it has assembled one.
LOOK_MK         equ     10*YMXR_PERF            ; the monitor's mark
LOOK_KW         equ     10-(2*YMXR_PCREL)       ; the key's write
LOOK_RD         equ     8-(2*YMXR_PCREL)        ; a column's read
KEY_LK          equ     LOOK_MK+6+LOOK_KW+2
KEY_LC          equ     LOOK_MK+6+LOOK_KW+2
KEY_LN          equ     LOOK_MK+6+LOOK_KW+2
KEY_LT          equ     LOOK_MK+6+LOOK_RD+6+LOOK_KW+2
KEY_LV          equ     LOOK_MK+6+LOOK_RD+6+LOOK_KW+2
KEY_LE          equ     LOOK_MK+6+LOOK_RD+6+LOOK_KW+2
KEY_LF          equ     LOOK_MK+6+LOOK_KW+2
KEY_LB          equ     LOOK_MK+6+LOOK_RD+6+LOOK_RD+6+LOOK_KW+2
; The stride of every lookup kind, the size of the two of three columns:
; each kind's four instances are this far apart, so effect e's page read
; is e times this past effect 0's modulo 256, the residue of its pages.
LOOK_STRIDE     equ     114+(4*YMXR_NEST)+(12*(1-YMXR_AEOI))+(42*YMXR_PERF)+(12*(1-YMXR_PCREL))+(4*YMXR_DEFER)
                .endif

; An effect's installer, which the hook of 4.8 branches to at the tick
; that reads the last row of the running source. Every operand is
; patched at the frame that arms the start: the hooked word and its
; address, the skip over the stop where the start leaves timerReset
; clear, the count, the armed handler and the select. The tick runs at
; the timer's level, so no tick of the other timer of a shared control
; register lands between the read and the write of it.
                .macro  INSTALL name,ctrl,data,keep,ier,bit,shift,shared,vector,eoi,eoir
\name:
\{name}_back:   move.w  #$4E71,($12345678).l    ; +2 the word, +4 its address
\{name}_skip:   bra.s   \{name}_run             ; a nop where the start sets
                andi.b  #\keep,\ctrl.w          ; timerReset: the timer stopped
                move.b  #255-(1<<\bit),(\ier+4).w ; and its latched tick dropped
\{name}_run:
\{name}_cnt:    move.b  #0,\data.w              ; +3: the count
\{name}_vec:    move.l  #$12345678,\vector.w    ; +2: the armed handler
                .if     \shared
                move.w  d0,-(sp)                ; the other timer's bits kept, at
                ori.w   #$0700,sr               ; level 7 as the frame keeps them:
                move.b  \ctrl.w,d0              ; a tick of the other timer nests
                and.b   #\keep,d0               ; otherwise between read and write
\{name}_sel:    or.b    #0,d0                   ; +3: the select, shifted
                move.b  d0,\ctrl.w
                move.w  (sp)+,d0                ; rte restores the level
                .else
\{name}_sel:    move.b  #0,\ctrl.w              ; +3: the select
                .endif
                COUNT   PERF_SWITCH,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .endm

; ---------------------------------------------------------------- calls

                bra.w   YMXR_init
                bra.w   YMXR_play
                bra.w   YMXR_stop

YMXR_init:
                cmp.l   #YMXR_MAGIC,(a0)
                bne     .bad
                move.w  TF_VERSION(a0),d0               ; version 7 to 9, a
                .if     YMXR_MAP                        ; DTX3 table (SPEC.md
                subq.w  #7,d0                           ; 3.3.5), and 10
                cmp.w   #3,d0
                .else
                subq.w  #YMXR_VERSION-2,d0
                cmp.w   #2,d0
                .endif
                bhi     .bad
                move.l  a6,-(sp)
                movea.l a1,a6
                move.l  a0,WS_TUNE(a6)
                movea.l a0,a2
                adda.l  TF_IMAGE(a0),a2
                move.l  a2,WS_IMAGE(a6)
                move.b  TF_EFFECTS(a0),WS_EFFECTS(a6)
                .if     YMXR_DEFER=0
                btst    #4,WS_EFFECTS(a6)       ; a tune that defers a start
                bne     .bad6                   ; needs a core with them
                .endif
                .if     YMXR_MAP
; Version 10: the transformations' block, the signed long at 24 from the
; bound tune's first byte, and the source index at 28 (BINARIES.md 1.2);
; 0 for versions 7 to 9, whose effects run through the identity. A tune
; that maps a value, bit 5 of the effects used, is version 10 (SPEC.md
; 3.3.5), and with this core defers no start.
                moveq   #0,d0
                cmp.w   #10,TF_VERSION(a0)
                bne.s   .v9
                move.l  a0,d0
                add.l   TF_TRANS(a0),d0
.v9:            move.l  d0,WS_TRANS(a6)
                btst    #5,WS_EFFECTS(a6)
                beq.s   .v10
                tst.l   d0
                beq     .bad6
                .if     YMXR_DEFER
                btst    #4,WS_EFFECTS(a6)
                bne     .bad6
                .endif
.v10:
                .endif
                cmp.b   #3,IM_VARIANT(a2)       ; a DTX3 image: its rows come
                bne     .bad6                   ; round by themselves
; The table to read, which the image may carry beside others: subtunes
; that agree on what an image fixes once share one, so the code in it
; appears once for them and every bound tune names its table (DTX abi.md
; 1). R and RR come out of that table's header (DTX SPEC.md 1) and not out
; of the metadata call, which returns the image's first table.
                movea.l a2,a1
                adda.l  TF_TABLE(a0),a1
                move.l  DT_ROWS(a1),d0
                move.l  DT_REPEAT(a1),d2
                move.l  d0,WS_R(a6)
                move.l  d0,WS_ROWS(a6)
                move.l  d2,WS_RR(a6)
                lea     WS_STATE(a6),a6         ; the block, in a6 (DTX,
                jsr     IM_INIT(a2)             ; abi.md 2); a1 the table's
                lea     -WS_STATE(a6),a6        ; header, and the workspace
                                                ; back at a fixed offset of it

; Every source resolved once: its first row, and its loop cell, which is
; row RR where it repeats, 0 where it plays once, -1 where it is two
; rows repeating to row 0, and -2 where it is one row repeating. The
; last two are the shapes with a separate handler. A start then
; reads two longs where it walked the index and read the source's
; header.
                movea.l WS_TUNE(a6),a1
                moveq   #0,d1
                moveq   #0,d5                   ; bit 0 a square among the
                move.b  TF_SOURCES(a1),d1       ; sources, bit 1 one row
                subq.w  #1,d1
                bmi     .resolved
                lea     WS_SOURCES(a6),a4
                moveq   #0,d4
                .if     YMXR_MAP
                tst.l   WS_TRANS(a6)            ; version 10: the index at 28
                beq.s   .source
                moveq   #4,d4
                .endif
.source:        movea.l WS_TUNE(a6),a3
                move.l  TF_INDEX(a3,d4.w),d0
                bclr    #31,d0                  ; the index's bit 31: a source
                .if     YMXR_PCREL              ; whose column fills its byte
                bne.w   .counted
                .else
                bne.s   .counted
                .endif
                adda.l  d0,a3                   ; the source's DTX1 table
                lea     DT_PAYLOAD(a3),a5       ; its first row
                move.l  a5,(a4)+
                .if     YMXR_PCREL
                bsr.w   ymxr_inreach
                bcs     .bad6
                .endif
                move.l  DT_REPEAT(a3),d0
                cmp.l   DT_ROWS(a3),d0          ; the row it repeats to, or
                beq.s   .plays                  ; none where it plays once
                add.l   a5,d0
                cmp.w   #1,DT_COLUMNS(a3)       ; a source of several columns
                bne.s   .several                ; runs the handler its columns
                tst.l   DT_REPEAT(a3)           ; pick, at any rows; and a
                bne.s   .cell                   ; source of one column
                                                ; repeating to row 0 of one
                cmp.l   #1,DT_ROWS(a3)          ; row or of two has a
                bne.s   .pair                   ; separate handler
                moveq   #-2,d0
                bset    #1,d5
                bra.s   .cell
.pair:          cmp.l   #2,DT_ROWS(a3)
                bne.s   .cell
                moveq   #-1,d0
                bset    #0,d5
                bra.s   .cell
.plays:         moveq   #0,d0
                cmp.w   #1,DT_COLUMNS(a3)
                beq.s   .cell
; A source of several columns: its loop cell and the stride from one
; column to the next are in the table beside, and the tag in the cell's
; place sends a start to the handler its columns pick.
.several:       move.l  d0,WS_WIDE-WS_SOURCES-4(a4)
                move.l  DT_ROWS(a3),d0
                addq.l  #1,d0                   ; DTX1 pads a column to a word
                and.w   #$FFFE,d0               ; (DTX, SPEC.md 2.2)
                move.w  d0,WS_WIDE-WS_SOURCES(a4)
                moveq   #-3,d0                  ; two columns
                cmp.w   #2,DT_COLUMNS(a3)
                beq.s   .tagged
                moveq   #-4,d0                  ; three
.tagged:        bset    #2,d5
.cell:          move.l  d0,(a4)+
                addq.w  #4,d4
                dbra    d1,.source
                bra.s   .resolved

; A source whose one column fills its byte: its rows carry no marker, so
; the rows of a pass and the rows of the loop are in the table beside,
; with its loop cell, and the tag in the cell's place sends a start to
; the routine every effect shares, which tells this tag from the tags of
; a source of several columns.
.counted:       adda.l  d0,a3                   ; the source's DTX1 table
                lea     DT_PAYLOAD(a3),a5       ; its first row
                move.l  a5,(a4)+
                .if     YMXR_PCREL
                bsr.w   ymxr_inreach
                bcs     .bad6
                .endif
                cmp.w   #1,DT_COLUMNS(a3)       ; a counted source of several
                bne.w   .ccolumns               ; columns
                move.l  DT_ROWS(a3),d0
                move.w  d0,WS_WIDE-WS_SOURCES(a4)   ; R, a pass through it
                sub.l   DT_REPEAT(a3),d0
                move.w  d0,WS_WIDE-WS_SOURCES+2(a4) ; R less RR, the loop
                move.l  DT_REPEAT(a3),d0
                cmp.l   DT_ROWS(a3),d0          ; the row it repeats to, or
                beq.s   .conce                  ; none where it plays once
                add.l   a5,d0
                bra.s   .ccell
.conce:         moveq   #0,d0
.ccell:         move.l  d0,WS_WIDE-WS_SOURCES-4(a4)
                moveq   #-5,d0
                bset    #2,d5
                bra.w   .cell

; A counted source of several columns: the stride from one column to the
; next and R are beside its loop cell, and a start reads the rows of a
; pass through the loop off the cell and the first row (SPEC.md 3.1.6).
.ccolumns:      move.l  DT_ROWS(a3),d0
                addq.l  #1,d0                   ; DTX1 pads a column to a word
                and.w   #$FFFE,d0               ; (DTX, SPEC.md 2.2)
                move.w  d0,WS_WIDE-WS_SOURCES(a4)
                move.l  DT_ROWS(a3),d0
                move.w  d0,WS_WIDE-WS_SOURCES+2(a4)
                move.l  DT_REPEAT(a3),d0
                cmp.l   DT_ROWS(a3),d0          ; the row it repeats to, or
                beq.s   .wonce                  ; none where it plays once
                add.l   a5,d0
                bra.s   .wcell
.wonce:         moveq   #0,d0
.wcell:         move.l  d0,WS_WIDE-WS_SOURCES-4(a4)
                moveq   #-6,d0
                bset    #2,d5
                bra.w   .cell
.resolved:

; The row's columns, as the table's column map defines them (DTX, abi.md
; 2 and SPEC.md 2.4): a live column is its slot's ring, slot times N past
; the pointer, and a constant column is its value. Every read site is
; written whole, an immediate of the value where its column is constant:
; a set inits one loaded copy for each subtune, and a column live in one
; tune is constant in another.
                movea.l WS_TUNE(a6),a1
                movea.l WS_IMAGE(a6),a0
                adda.l  TF_TABLE(a1),a0
                lea     DT_PAYLOAD(a0),a0       ; the table's payload
                moveq   #0,d3
                move.w  (a0),d3                 ; N, the stride from one slot
                                                ; to the next
                lea     ymxr_reads(pc),a3
                lea     ymxr_frame(pc),a4
                moveq   #0,d1
                moveq   #YMXR_COLUMNS-1,d2
.patch:         move.w  (a3)+,d4
                move.w  d1,d0
                bsr     ymxr_col
                bsr     ymxr_put
                addq.w  #1,d1
                dbra    d2,.patch
                lea     ymxr_sites(pc),a3
                moveq   #YMXR_SITES-1,d2
.site:          move.w  (a3)+,d4
                move.w  (a3)+,d0
                bsr     ymxr_col
                bsr     ymxr_put
                dbra    d2,.site

                .if     YMXR_AEOI
                move.b  MFP_VECTOR.w,WS_VECTOR(a6)  ; the mode kept, and the
                bclr    #3,MFP_VECTOR.w             ; the MFP ends each
                .endif

                .if     YMXR_NEST
; A tune that runs one effect has no second timer to nest inside a tick,
; so the level that tick drops buys it no safety: two nops are where the
; drop would, in all three of that effect's handlers. A tune running two
; or more keeps the drop, and one running none skips the handlers. Every
; handler's word is written on every init: a set inits one loaded copy
; for each subtune, and a subtune that ran one effect alone left its nops
; where the next, running that effect beside another, needs the drop.
                move.l  #$46FC2500,d3           ; move.w #$2500,sr, the drop,
                move.b  WS_EFFECTS(a6),d0       ; which a handler keeps unless
                and.b   #$0F,d0                 ; its effect runs alone
                move.b  d0,d1
                subq.b  #1,d1
                and.b   d1,d0                   ; more than one bit set
                bne.s   .nests
                move.l  #$4E714E71,d3           ; nop nop, for the one it runs
.nests:
                .endif

; Where deferred starts are assembled in, every handler is in two banks
; (4.8), and each bank is patched the same way: the loop below runs bank 0
; with d6 at 0 and bank 1 with d6 at BANK; otherwise it runs bank 0
; alone.
                move.l  d6,-(sp)
                moveq   #0,d6
.banks:
; The tick handlers: each one's step and loop operands name a
; separate pair of cells, its place is parked on a marker until a start,
; and the register it selects is R0 until a start aims it (SPEC.md 5.2.1).
                lea     ymxr_tick0(pc),a5
                adda.w  d6,a5
                moveq   #3,d2
.link:          bsr     ymxr_link
                lea     TICK_SIZE(a5),a5
                dbra    d2,.link
                lea     ymxr_cnt0(pc),a5
                adda.w  d6,a5
                moveq   #3,d2
.linkc:         bsr     ymxr_linkc
                lea     TICKC_SIZE(a5),a5
                dbra    d2,.linkc

                .if     YMXR_NEST
                lea     ymxr_tick0(pc),a5
                adda.w  d6,a5
                lea     ymxr_sq0(pc),a4
                adda.w  d6,a4
                lea     ymxr_one0(pc),a3
                adda.w  d6,a3
                .if     YMXR_MAP
                lea     ymxr_lk0(pc),a1         ; and the general lookup
                adda.w  d6,a1                   ; instance's
                .endif
                moveq   #0,d4
                moveq   #3,d2
.each:          move.l  #$46FC2500,d0           ; a handler the tune does not
                btst    d4,WS_EFFECTS(a6)       ; run keeps the drop, and one
                beq.s   .word                   ; it runs is written d3
                move.l  d3,d0
.word:          move.l  d0,TICK_SR(a5)
                move.l  d0,SQ_SR(a4)
                move.l  d0,ONE_SR(a3)
                .if     YMXR_MAP
                move.l  d0,LK_SR(a1)
                lea     LOOK_STRIDE(a1),a1
                .endif
                lea     TICK_SIZE(a5),a5
                lea     SQ_SIZE(a4),a4
                lea     ONE_SIZE(a3),a3
                addq.w  #1,d4
                dbra    d2,.each
                .endif

; A square's handler names the row it is at in the one instruction
; that moves between the two, and so does the copy of that instruction in
; its hook (4.8).
                lea     ymxr_sq0(pc),a5
                adda.w  d6,a5
                moveq   #3,d2
.sq:            lea     SQ_VAL(a5),a3
                move.l  a3,SQ_PTR(a5)
                .if     YMXR_DEFER
                move.l  a3,SQ_HPTR(a5)
                .endif
                lea     SQ_SIZE(a5),a5
                dbra    d2,.sq

; The handlers of several columns: the loop cells they read and the
; places they step are in the handler, so one link writes every address
; the loop path and the step read, as a general handler's link does. A
; start writes the values, and no timer reaches one of these handlers
; before a start has aimed its vector there.
                lea     ymxr_two0(pc),a5
                adda.w  d6,a5
                moveq   #3,d2
.linktwo:       lea     TW2_CELLS(a5),a3
                move.l  a3,TW2_LOOPT(a5)
                move.l  a3,TW2_LOOP1R(a5)
                addq.l  #CELL_SIZE,a3
                move.l  a3,TW2_LOOP2R(a5)
                lea     TW2_PTR1(a5),a3
                move.l  a3,TW2_STEP1(a5)
                move.l  a3,TW2_LOOP1W(a5)
                lea     TW2_PTR2(a5),a3
                move.l  a3,TW2_STEP2(a5)
                move.l  a3,TW2_LOOP2W(a5)
                lea     TW2_SIZE(a5),a5
                dbra    d2,.linktwo
                lea     ymxr_three0(pc),a5
                adda.w  d6,a5
                moveq   #3,d2
.linkthree:     lea     TW3_CELLS(a5),a3
                move.l  a3,TW3_LOOPT(a5)
                move.l  a3,TW3_LOOP1R(a5)
                addq.l  #CELL_SIZE,a3
                move.l  a3,TW3_LOOP2R(a5)
                addq.l  #CELL_SIZE,a3
                move.l  a3,TW3_LOOP3R(a5)
                lea     TW3_PTR1(a5),a3
                move.l  a3,TW3_STEP1(a5)
                move.l  a3,TW3_LOOP1W(a5)
                lea     TW3_PTR2(a5),a3
                move.l  a3,TW3_STEP2(a5)
                move.l  a3,TW3_LOOP2W(a5)
                lea     TW3_PTR3(a5),a3
                move.l  a3,TW3_STEP3(a5)
                move.l  a3,TW3_LOOP3W(a5)
                lea     TW3_SIZE(a5),a5
                dbra    d2,.linkthree
                lea     ymxr_env0(pc),a5
                adda.w  d6,a5
                moveq   #3,d2
.linkenv:       lea     TWC_CELLS(a5),a3
                move.l  a3,TWC_LOOPT(a5)
                move.l  a3,TWC_LOOP1R(a5)
                addq.l  #CELL_SIZE,a3
                move.l  a3,TWC_LOOP2R(a5)
                lea     TWC_PTR1(a5),a3
                move.l  a3,TWC_STEP1(a5)
                move.l  a3,TWC_LOOP1W(a5)
                lea     TWC_PTR2(a5),a3
                move.l  a3,TWC_STEP2(a5)
                move.l  a3,TWC_LOOP2W(a5)
                lea     TWC_LEFT(a5),a3
                move.l  a3,TWC_CNT(a5)
                move.l  a3,TWC_RELDA(a5)
                move.w  #1,TWC_LEFT(a5)
                lea     TWC_SIZE(a5),a5
                dbra    d2,.linkenv
                .if     YMXR_MAP
; The lookup instances, linked as the handlers above are, with each one's
; page byte for page 0 out of the bank's 32 bytes of the table. The
; records cleared below load no page.
                lea     ymxr_htab(pc),a4
                tst.w   d6
                beq.s   .htab
                lea     32(a4),a4
.htab:          LINK1   LK,ymxr_lk0,0,0
                LINK1   LC,ymxr_lc0,1,1
                LINKW   LT,ymxr_lt0,2,0,0
                LINKW   LN,ymxr_ln0,3,0,0
                LINKW   LB,ymxr_lb0,4,1,0
                LINKW   LV,ymxr_lv0,5,1,0
                LINKW   LE,ymxr_le0,6,0,1
                LINKW   LF,ymxr_lf0,7,0,1
                .endif
                .if     YMXR_DEFER
                tst.w   d6
                bne.s   .banked
                move.w  #BANK,d6
                bra     .banks
.banked:
                .endif
                move.l  (sp)+,d6

; The effects' records: the kept target, select, count and shape empty
; until a start, the first row the parked place, so a start that moves
; no place is at row 0 of the source it names (SPEC.md 4.1 step 4), and
; no start pending.
                lea     WS_FX(a6),a0
                lea     ymxr_park(pc),a1
                moveq   #3,d0
.clear:         movea.l a0,a2
                moveq   #FX_SIZE/4-1,d1
.zero:          clr.l   (a2)+
                dbra    d1,.zero
                move.l  a1,FX_FIRST(a0)
                move.l  a1,FX_FIRST2(a0)
                move.l  a1,FX_FIRST3(a0)
                lea     FX_SIZE(a0),a0
                dbra    d0,.clear
                .if     YMXR_DEFER
                sf      WS_ARMING(a6)
                .endif

; The effects the tune runs are read every row; the others are jumped
; over by the branch at each macro's head, where the tune does not run
; its effect.
; The head of each effect: the effect's first read where the tune runs
; it, and where it does not, a branch, its displacement to the head of
; the next effect the tune does run or past the last of them. A run of
; effects the tune does not run then costs one branch. This is after the
; sites above, which write the read whole into the two words a skipped
; effect's branch occupies, so a head the tune runs is that read already.
                lea     ymxr_frame(pc),a4
                lea     ymxr_overs(pc),a3
                lea     ymxr_tick3_done(pc),a5
                moveq   #3,d2
.over:          move.w  (a3)+,d4
                lea     (a4,d4.w),a1
                btst    d2,WS_EFFECTS(a6)
                beq.s   .skip
                movea.l a1,a5                   ; the effect's first read
                bra.s   .next
.skip:          move.w  #$6000,(a1)             ; bra.w: the head the assembler
                move.l  a5,d0                   ; wrote, which a tune before
                sub.l   a1,d0                   ; this one that ran the effect
                subq.l  #2,d0                   ; replaced with the read
                move.w  d0,2(a1)
.next:          dbra    d2,.over

; The start tells three shapes apart off one cell, and a tune using one
; of the two shapes with a separate handler and not the other needs no
; test: the branch that leaves the general handler's path goes straight
; to that shape's block, and the start pays no test for the shape it
; does not use. A tune using both, or neither, keeps the test. The word
; is written on every path: a tune before this one that used one shape
; wrote its block there, and a set inits one loaded copy for every
; subtune.
                move.w  #PICK_BOTH,d1
                move.b  d5,d0
                btst    #2,d0                   ; a tune with a source of
                bne.s   .only                   ; several columns keeps the
                tst.b   d0                      ; test in: such a source's
                beq.s   .only                   ; cell is a tag past the two
                cmp.b   #3,d0
                beq.s   .only
                move.w  #PICK_ONE,d1
                btst    #0,d0
                beq.s   .only
                move.w  #PICK_SQ,d1
.only:          move.l  #((WIDE_ONE&$FF)<<16)|(RESET_DONE&$FF),d0
                btst    #2,d5                   ; and the three branches that
                beq.s   .aims                   ; lie elsewhere where it has
                move.l  #((WIDE_WIDEN&$FF)<<16)|(RESET_WIDE&$FF),d0
.aims:          lea     ymxr_frame(pc),a4
                lea     ymxr_overs(pc),a3
                lea     ymxr_resets(pc),a2
                moveq   #3,d2
.pick:          move.w  (a3)+,d4
                lea     (a4,d4.w),a1
                move.w  d1,PICK_AT(a1)
                move.l  d0,d5
                move.w  (a2)+,d4
                move.b  d5,(a4,d4.w)            ; the place's reset, at the site
                swap    d5                      ; the table names
                move.b  d5,WIDE_AT(a1)
                dbra    d2,.pick

                .if     YMXR_DEFER
; A tune that defers a start, bit 4 of the effects used (SPEC.md 3.3),
; aims three branches of every effect at the paths that read a pending
; start (4.8); every other tune keeps the three the assembler wrote, so
; its frame costs what it cost before deferred starts. Each is written
; on every init, as the heads are.
                lea     ymxr_frame(pc),a4
                lea     ymxr_overs(pc),a3
                moveq   #3,d2
.defers:        move.w  (a3)+,d4
                lea     (a4,d4.w),a1            ; the effect's head
                btst    #4,WS_EFFECTS(a6)
                beq.s   .plain
                move.w  #$6000,RS_AT(a1)        ; bra.w
                move.w  #RS_DEFER,RS_AT+2(a1)
                move.w  #UNSET_DEFER,UNSET_AT(a1)
                move.w  #$6000,SET_AT(a1)       ; bra.w
                move.w  #SET_DEFER,SET_AT+2(a1)
                bra.s   .aimed
.plain:         move.l  #$08020006,RS_AT(a1)    ; btst #6,d2
                move.w  #UNSET_RATE,UNSET_AT(a1)
                move.w  #$6700,SET_AT(a1)       ; beq.w
                move.w  #SET_STOP,SET_AT+2(a1)
.aimed:         dbra    d2,.defers
                .endif

                .if     YMXR_MAP
; A tune that maps a value, bit 5 of the effects used (SPEC.md 3.3), aims
; three branches of every effect at the paths that read the mapping
; column: the start's, the one of a row that leaves the source column
; unset, and the timer's reset, whose path copies the control register
; into d3 for a remap on the row. Every other tune runs the start through
; the identity, the rate and the reset. Each is written on every init, as
; the heads are; a core with deferred starts writes the reset's branch of
; every other tune above.
                lea     ymxr_frame(pc),a4
                lea     ymxr_overs(pc),a3
                moveq   #3,d2
.maps:          move.w  (a3)+,d4
                lea     (a4,d4.w),a1            ; the effect's head
                btst    #5,WS_EFFECTS(a6)
                beq.s   .identity
                move.w  #NOW_MAP,NOW_AT(a1)
                move.w  #UNSET_MAP,UNSET_AT(a1)
                move.w  #$6000,RS_AT(a1)        ; bra.w
                move.w  #RS_MAP,RS_AT+2(a1)
                bra.s   .mapsnext
.identity:      move.w  #NOW_START,NOW_AT(a1)
                .if     YMXR_DEFER=0
                move.w  #UNSET_RATE,UNSET_AT(a1)
                move.l  #$08020006,RS_AT(a1)    ; btst #6,d2
                .endif
.mapsnext:      dbra    d2,.maps
                .endif

; The advance's address, written into the call that plays: a slot is
; a bra.w (DTX, abi.md 1), so the body is two bytes past the slot
; plus the branch's displacement, and a play call reaches it in one jsr.
                movea.l WS_IMAGE(a6),a2
                lea     IM_ADVANCE(a2),a3
                move.w  2(a3),d0
                ext.l   d0
                lea     2(a3,d0.l),a3
                lea     ymxr_call+2(pc),a4
                move.l  a3,(a4)

; The first row, read here so that a call writes a row it already has
; (SPEC.md 4): the writes then leave a host's clock at one offset,
; by the advance for the next row.
                move.l  a6,-(sp)
                lea     WS_STATE(a6),a6         ; the block, in a6
                jsr     (a3)                    ; a1 the row
                movea.l (sp)+,a6

; The timers the tune runs, claimed. The others are left as they are.
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                CLAIM   0,$FFFFFA19,$F0,$134,$FFFFFA07,5,ymxr_tick0
                CLAIM   1,$FFFFFA1D,$F0,$110,$FFFFFA09,4,ymxr_tick1
                CLAIM   2,$FFFFFA1B,$F0,$120,$FFFFFA07,0,ymxr_tick2
                CLAIM   3,$FFFFFA1D,$0F,$114,$FFFFFA09,5,ymxr_tick3
                move.w  (sp)+,sr
                movea.l (sp)+,a6
                moveq   #0,d0
                rts
.bad6:          movea.l (sp)+,a6
.bad:           moveq   #-1,d0
                rts

; Under YMXR_PCREL a tick reads its row through a signed word
; displacement from the instruction that reads it, so every row of every
; source is within reach of every handler. a5 the source's first row,
; a3 its DTX1 table: the rows run (C - 1) strides and R bytes from the
; first (SPEC.md 3.1.2), and the handlers run from ymxr_tick0 to
; ymxr_reach. Carry marks a source a displacement does not reach, which
; init reports -1 for.
                .if     YMXR_PCREL
ymxr_inreach:   movem.l d0-d1/a0-a1,-(sp)
                move.l  DT_ROWS(a3),d0
                addq.l  #1,d0
                and.w   #$FFFE,d0               ; align(R), a column's stride
                moveq   #0,d1                   ; (DTX, SPEC.md 2.2)
                move.w  DT_COLUMNS(a3),d1
                subq.w  #1,d1
                mulu    d1,d0                   ; the columns before the last
                add.l   DT_ROWS(a3),d0
                lea     -1(a5,d0.l),a0          ; the source's last byte
                lea     ymxr_tick0(pc),a1
                move.l  a0,d0
                sub.l   a1,d0                   ; from the first handler
                cmp.l   #32767,d0
                bgt.s   .far
                lea     ymxr_reach(pc),a1
                move.l  a1,d0
                sub.l   a5,d0                   ; to the source's first byte
                cmp.l   #32768,d0
                bgt.s   .far
                movem.l (sp)+,d0-d1/a0-a1
                andi.b  #$FE,ccr
                rts
.far:           movem.l (sp)+,d0-d1/a0-a1
                ori.b   #1,ccr
                rts
                .endif

; The word a read of column d0 is written with, out of the payload in a0
; and N in d3 (DTX, SPEC.md 2.4): a live column's displacement, its slot
; times N, or a constant column's value with bit 15 set. The constant
; values follow the map in column order, so a constant column's value is
; one byte past the value of each constant column before it.
ymxr_col:       movem.l d1/a1,-(sp)
                .if     YMXR_MAP
                cmp.w   DT_COLUMNS-DT_PAYLOAD(a0),d0    ; a column at or past
                bcs.s   .in                             ; the table's C, a
                move.w  #$8000,d0                       ; mapping column of a
                bra.s   .done                           ; version 9 tune: the
.in:                                                    ; constant 0
                .endif
                moveq   #0,d1
                move.b  8(a0,d0.w),d1           ; the column's byte of the map
                cmp.b   #$FF,d1
                beq.s   .value
                mulu.w  d3,d1                   ; its slot times N
                move.w  d1,d0
                bra.s   .done
                .if     YMXR_MAP
.value:         lea     8(a0),a1                ; the values, behind the map
                adda.w  DT_COLUMNS-DT_PAYLOAD(a0),a1    ; of C columns
                .else
.value:         lea     8+YMXR_COLUMNS(a0),a1   ; the values, behind the map
                .endif
                subq.w  #1,d0
                bmi.s   .at
.before:        cmp.b   #$FF,8(a0,d0.w)
                bne.s   .live
                addq.l  #1,a1
.live:          dbra    d0,.before
.at:            moveq   #0,d0
                move.b  (a1),d0
                bset    #15,d0
.done:          movem.l (sp)+,d1/a1
                rts

; One read site written whole: the word at (a4,d4.w) is its operand and
; the word before it its opcode, and d0 is ymxr_col's word for its
; column. The opcode as it is defines the read, whichever the last init
; wrote: a move into d1, the head of an effect being one; a move into d2;
; or an or into d1. A live column is read at its displacement, and a
; constant column's read is the same instruction with its value as an
; immediate, which costs 8 cycles to the displacement's 12 and sets the
; flags as the read sets them.
ymxr_put:       movem.l d1-d2/a5,-(sp)
                lea     (a4,d4.w),a5
                move.w  -2(a5),d1               ; the opcode as it is
                move.w  #$8229,d2               ; or.b d16(a1),d1
                cmp.w   d2,d1
                beq.s   .or
                cmp.w   #$0001,d1               ; or.b #v,d1
                beq.s   .or
                move.w  #$1429,d2               ; move.b d16(a1),d2
                cmp.w   d2,d1
                beq.s   .move
                cmp.w   #$143C,d1               ; move.b #v,d2
                beq.s   .move
                move.w  #$1229,d2               ; move.b d16(a1),d1, or its
                                                ; immediate, or a head's bra.w
.move:          tst.w   d0
                bpl.s   .live
                move.w  d2,d1                   ; move.b #v: the register's
                and.w   #$FE00,d1               ; bits kept, the source the
                or.w    #$003C,d1               ; immediate
                bra.s   .value
.or:            tst.w   d0
                bpl.s   .live
                move.w  #$0001,d1               ; or.b #v,d1
.value:         move.w  d1,-2(a5)
                and.w   #$00FF,d0
                move.w  d0,(a5)
                bra.s   .done
.live:          move.w  d2,-2(a5)
                move.w  d0,(a5)
.done:          movem.l (sp)+,d1-d2/a5
                rts

; a5 a tick handler: its operands linked, its place parked.
ymxr_link:
                lea     TICK_PTR(a5),a3
                move.l  a3,TICK_STEP(a5)
                move.l  a3,TICK_LOOPW(a5)
                lea     TICK_CELL(a5),a3
                move.l  a3,TICK_LOOPT(a5)
                move.l  a3,TICK_LOOPR(a5)
                .if     YMXR_PCREL
                clr.w   TICK_CELL(a5)
                lea     ymxr_park(pc),a3
                suba.l  a5,a3
                suba.w  #TICK_PTR,a3
                move.w  a3,TICK_PTR(a5)
                .else
                clr.l   TICK_CELL(a5)
                lea     ymxr_park(pc),a3
                move.l  a3,TICK_PTR(a5)
                .endif
                clr.b   TICK_SEL(a5)            ; R0, the register of the kept
                rts                             ; target init leaves (SPEC.md 5.2.1)

; a5 a counted handler, the same way: its operands linked, its counter
; at 1 and its cell at 0, so a tick before the first start writes the
; parked marker and stops the timer, as the handler above does.
ymxr_linkc:
                lea     TICKC_PTR(a5),a3
                move.l  a3,TICKC_STEP(a5)
                move.l  a3,TICKC_LOOPW(a5)
                lea     TICKC_CELL(a5),a3
                move.l  a3,TICKC_LOOPT(a5)
                move.l  a3,TICKC_LOOPR(a5)
                .if     YMXR_PCREL
                clr.w   TICKC_CELL(a5)
                .else
                clr.l   TICKC_CELL(a5)
                .endif
                lea     TICKC_LEFT(a5),a3
                move.l  a3,TICKC_CNTA(a5)
                move.l  a3,TICKC_RELDA(a5)
                move.w  #1,TICKC_LEFT(a5)
                lea     ymxr_park(pc),a3
                .if     YMXR_PCREL
                suba.l  a5,a3
                suba.w  #TICKC_PTR,a3
                move.w  a3,TICKC_PTR(a5)
                .else
                move.l  a3,TICKC_PTR(a5)
                .endif
                clr.b   TICKC_SEL(a5)           ; R0, as init leaves the kept
                rts                             ; target (SPEC.md 5.2.1)

YMXR_stop:
                move.l  a6,-(sp)
                movea.l a0,a6
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                RELEASE 0,$FFFFFA19,$F0,$FFFFFA07,5
                RELEASE 1,$FFFFFA1D,$F0,$FFFFFA09,4
                RELEASE 2,$FFFFFA1B,$F0,$FFFFFA07,0
                RELEASE 3,$FFFFFA1D,$0F,$FFFFFA09,5
                move.w  (sp)+,sr
; The chip left as the next tune finds it. A row sets the registers it
; names alone (SPEC.md 4), so a tune whose first row leaves one
; unset would read the register the tune before it wrote: the envelope
; shape reaches furthest, since writing it retriggers the generator and
; a tune that sets it late runs on the shape the tune before it left.
; The three
; voices go first, so the rest is written into silence, then every
; register is zero and the mixer has each channel off. A long write
; selects and writes in one bus operation, so these are outside the
; mask a host's interrupts reach the chip through.
                move.l  #$08000000,YM_SELECT.w
                move.l  #$09000000,YM_SELECT.w
                move.l  #$0A000000,YM_SELECT.w
                moveq   #13,d0
.hush:          move.l  d0,d1
                swap    d1
                lsl.l   #8,d1                   ; the register in the select
                move.l  d1,YM_SELECT.w          ; byte, the value zero
                dbra    d0,.hush
                move.l  #$0700FF00,YM_SELECT.w  ; $3F, each channel off,
                                                ; beside the port directions
                .if     YMXR_AEOI
                move.b  WS_VECTOR(a6),MFP_VECTOR.w
                .endif
                movea.l (sp)+,a6
                rts

YMXR_play:
                tst.l   WS_ROWS(a0)
                beq     ymxr_over
                .if     YMXR_PERF
                move.w  PERF_COLOUR.w,-(sp)     ; the background, kept
                move.b  PERF_BEAM.w,d0          ; before a tick can paint
                move.w  #PERF_SYNC,d1           ; over it; then the wait for
.beam:          cmp.b   PERF_BEAM.w,d0          ; the display, which the
                dbne    d1,.beam                ; counter marks by moving,
                                                ; and the ceiling ends where
                                                ; the counter stops
                move.w  #PERF_FRAME,PERF_COLOUR.w
                .endif
                movea.l WS_STATE+IM_POINTER(a0),a1  ; the row this call
                lea     YM_SELECT.w,a2          ; writes, where the advance
                                                ; before it left the image

; ---------------------------------------------------------------- frame

; The frame procedure (SPEC.md 4), inline in the call: a1 the row, a2 the
; chip's select register, a0 the workspace.
ymxr_frame:
; 1 to 3: the effects, each its four columns.
                EFFECT  WS_FX,_14,_15,_16,_17,_30,$FFFFFA19,$FFFFFA1F,$FFFFFA07,5,$F0,0,0,ymxr_tick0,ymxr_sq0,ymxr_one0,ymxr_two0,ymxr_three0,$134
                EFFECT  WS_FX+FX_SIZE,_18,_19,_20,_21,_31,$FFFFFA1D,$FFFFFA25,$FFFFFA09,4,$F0,0,1,ymxr_tick1,ymxr_sq1,ymxr_one1,ymxr_two1,ymxr_three1,$110
                EFFECT  WS_FX+FX_SIZE+FX_SIZE,_22,_23,_24,_25,_32,$FFFFFA1B,$FFFFFA21,$FFFFFA07,0,$F0,0,0,ymxr_tick2,ymxr_sq2,ymxr_one2,ymxr_two2,ymxr_three2,$120
                EFFECT  WS_FX+FX_SIZE+FX_SIZE+FX_SIZE,_26,_27,_28,_29,_33,$FFFFFA1D,$FFFFFA23,$FFFFFA09,5,$0F,4,1,ymxr_tick3,ymxr_sq3,ymxr_one3,ymxr_two3,ymxr_three3,$114
; 4: the tone periods. Steps 4 to 8 are between this label and
; ymxr_played, where the rig counts what the register columns cost.
ymxr_regs:
                TONE    _0,_1,$0000,$0100
                TONE    _2,_3,$0200,$0300
                TONE    _4,_5,$0400,$0500
; 5: the noise period, and the envelope period beside the shape's bits:
;    where the shape column is 0, no bit beside is set, and a period byte
;    is written where it is not 0, with no further test.
                WRITE   _6,$0600
                READ2   _13
                beq.w   ymxr_plain
                RESERVED _11,6,$0B00
                RESERVED _12,5,$0C00
; 6: the volumes.
                WRITE   _8,$0800
                WRITE   _9,$0900
                WRITE   _10,$0A00
; 7: the mixer, with the host's port directions.
                READ    _7
                bpl.s   .no7
                or.b    #YM_PORTS,d1
                move.w  #$0700,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no7:
; 8: the shape, read above into d2. Step 5 found the column set, so the
; test here is of its set bit alone.
                tst.b   d2
                bpl.s   ymxr_played
                move.w  #$0D00,d1
                move.b  d2,d1
                movep.w d1,(a2)
                bra.s   ymxr_played

; Steps 6 and 7 again, for the row whose envelope column step 5 found 0:
; step 8 leaves every register as it is on that row, and this copy ends without
; the test that would say so.
ymxr_plain:     PLAIN   _11,$0B00
                PLAIN   _12,$0C00
                RWRITE  _8,$0800
                RWRITE  _9,$0900
                RWRITE  _10,$0A00
                RREAD   _7
                bpl.s   ymxr_played
                or.b    #YM_PORTS,d1
                move.w  #$0700,d0
                move.b  d1,d0
                movep.w d0,(a2)
; The row is written: the next one is read now, after every write this
; call makes, so no part of the advance's cost is between a host's clock
; and the chip.
ymxr_played:
                subq.l  #1,WS_ROWS(a0)
                bne.s   ymxr_next
                move.l  WS_R(a0),d0             ; the last row: the loop's
                sub.l   WS_RR(a0),d0            ; rows, or none
                move.l  d0,WS_ROWS(a0)
                beq.s   ymxr_return             ; none: no row to read
; The state block, which the advance reads from a6 (DTX, abi.md 2). This
; call restores a6 to its host, and the advance clobbers d0-d5 and
; a0-a5, so the stack is where a6 waits.
ymxr_next:      move.l  a6,-(sp)
                lea     WS_STATE(a0),a6
ymxr_call:      jsr     ($12345678).l           ; the advance's address,
                                                ; resolved at init
                movea.l (sp)+,a6
ymxr_return:
                .if     YMXR_PERF
                move.w  #PERF_BAR,PERF_COLOUR.w ; the timers' bar, burnt
                lea     ymxr_perf_acc(pc),a0    ; after the writes this call
                move.w  (a0),d0                 ; makes, so none of them
                clr.w   (a0)                    ; moves for it
.bar:           dbra    d0,.bar
                move.w  (sp)+,PERF_COLOUR.w
                .endif
                moveq   #0,d0
                rts
ymxr_over:      moveq   #-1,d0
                rts

                .if     YMXR_PERF
ymxr_perf_acc:  dc.w    0                       ; the ticks' counted cost
                .endif                          ; since the last call

; The reads' displacement words, one a column, in column order.
ymxr_reads:
                dc.w    c_0-ymxr_frame+2,c_1-ymxr_frame+2,c_2-ymxr_frame+2
                dc.w    c_3-ymxr_frame+2,c_4-ymxr_frame+2,c_5-ymxr_frame+2
                dc.w    c_6-ymxr_frame+2,c_7-ymxr_frame+2,c_8-ymxr_frame+2
                dc.w    c_9-ymxr_frame+2,c_10-ymxr_frame+2,c_11-ymxr_frame+2
                dc.w    c_12-ymxr_frame+2,c_13-ymxr_frame+2,c_14-ymxr_frame+2
                dc.w    c_15-ymxr_frame+2,c_16-ymxr_frame+2,c_17-ymxr_frame+2
                dc.w    c_18-ymxr_frame+2,c_19-ymxr_frame+2,c_20-ymxr_frame+2
                dc.w    c_21-ymxr_frame+2,c_22-ymxr_frame+2,c_23-ymxr_frame+2
                dc.w    c_24-ymxr_frame+2,c_25-ymxr_frame+2,c_26-ymxr_frame+2
                dc.w    c_27-ymxr_frame+2,c_28-ymxr_frame+2,c_29-ymxr_frame+2
                .if     YMXR_MAP
                dc.w    c_30-ymxr_frame+2,c_31-ymxr_frame+2,c_32-ymxr_frame+2
                dc.w    c_33-ymxr_frame+2
                .endif

; The four effects' heads, effect 3 first, as offsets into the frame.
ymxr_overs:
                dc.w    ymxr_tick3_over-ymxr_frame,ymxr_tick2_over-ymxr_frame
                dc.w    ymxr_tick1_over-ymxr_frame,ymxr_tick0_over-ymxr_frame

; The other sites a column is read at, each with its column: the effects'
; one-pass tests, and the envelope period's plain path.
ymxr_resets:    dc.w    ymxr_tick0_reset-ymxr_frame+1
                dc.w    ymxr_tick1_reset-ymxr_frame+1
                dc.w    ymxr_tick2_reset-ymxr_frame+1
                dc.w    ymxr_tick3_reset-ymxr_frame+1

ymxr_sites:
                dc.w    q_14-ymxr_frame+2,14,q_15-ymxr_frame+2,15
                dc.w    q_16-ymxr_frame+2,16,q_17-ymxr_frame+2,17
                dc.w    q_18-ymxr_frame+2,18,q_19-ymxr_frame+2,19
                dc.w    q_20-ymxr_frame+2,20,q_21-ymxr_frame+2,21
                dc.w    q_22-ymxr_frame+2,22,q_23-ymxr_frame+2,23
                dc.w    q_24-ymxr_frame+2,24,q_25-ymxr_frame+2,25
                dc.w    q_26-ymxr_frame+2,26,q_27-ymxr_frame+2,27
                dc.w    q_28-ymxr_frame+2,28,q_29-ymxr_frame+2,29
                dc.w    q_11-ymxr_frame+2,11,q_12-ymxr_frame+2,12
                dc.w    r_8-ymxr_frame+2,8,r_9-ymxr_frame+2,9
                dc.w    r_10-ymxr_frame+2,10,r_7-ymxr_frame+2,7
                .if     YMXR_MAP
                dc.w    q_30-ymxr_frame+2,30,q_31-ymxr_frame+2,31
                dc.w    q_32-ymxr_frame+2,32,q_33-ymxr_frame+2,33
                dc.w    m_30-ymxr_frame+2,30,m_31-ymxr_frame+2,31
                dc.w    m_32-ymxr_frame+2,32,m_33-ymxr_frame+2,33
                .endif
                .if     YMXR_DEFER
                dc.w    d_17-ymxr_frame+2,17,d_21-ymxr_frame+2,21
                dc.w    d_25-ymxr_frame+2,25,d_29-ymxr_frame+2,29
                .endif

; The handlers, from ymxr_tick0 to ymxr_reach, and the offsets measured
; off them, as two macros: a core without the lookup places both here, and
; one with it after the frame's routines, so that every byte from
; ymxr_tick0 on is a handler, an installer or padding (YMXR_MAP above).
; rmac measures an equate off labels before it, so the offsets follow the
; handlers.
                .macro  HANDLERS
; ---------------------------------------------------------------- ticks

                TICK    ymxr_tick0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICK    ymxr_tick1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICK    ymxr_tick2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICK    ymxr_tick3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3

                SQUARE  ymxr_sq0,$DF,$FFFFFA0F,$070,ymxr_inst0
                SQUARE  ymxr_sq1,$EF,$FFFFFA11,$007,ymxr_inst1
                SQUARE  ymxr_sq2,$FE,$FFFFFA0F,$707,ymxr_inst2
                SQUARE  ymxr_sq3,$DF,$FFFFFA11,$077,ymxr_inst3

                ONEROW  ymxr_one0,$DF,$FFFFFA0F,$070,ymxr_inst0
                ONEROW  ymxr_one1,$EF,$FFFFFA11,$007,ymxr_inst1
                ONEROW  ymxr_one2,$FE,$FFFFFA0F,$707,ymxr_inst2
                ONEROW  ymxr_one3,$DF,$FFFFFA11,$077,ymxr_inst3

                TICKC   ymxr_cnt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKC   ymxr_cnt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKC   ymxr_cnt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKC   ymxr_cnt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3

; The handlers of a target that writes two registers, and of one that
; writes three: one an effect, as the three above are.
                TICKW   ymxr_two0,2,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_two1,2,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_two2,2,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_two3,2,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3

                TICKW   ymxr_three0,3,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_three1,3,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_three2,3,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_three3,3,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3

; The counted shape of two columns: `setEnvelope` writes R11 and R12 and
; both read every bit of their byte, so the rows of a source on it are
; whole bytes and a tick counts them (SPEC.md 2.1.3, 3.1.6).
                TICKW   ymxr_env0,2,1,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_env1,2,1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_env2,2,1,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_env3,2,1,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                .if     YMXR_MAP
; The lookup instances of bank 0, after the identity's handlers: eight
; kinds an effect, each instance padded so that its page read's
; displacement is on its effect's pages modulo 256, and under YMXR_DEFER
; the bank padded to a multiple of 256, so bank 1's are on the same.
; Without the monitor each kind stops at the general handler's halt.
                .if     YMXR_PERF=0
ymxr_lhalt0     equ     ymxr_tick0_halt
ymxr_lhalt1     equ     ymxr_tick1_halt
ymxr_lhalt2     equ     ymxr_tick2_halt
ymxr_lhalt3     equ     ymxr_tick3_halt
                .endif
ymxr_look:
                LPAD    KEY_LT,ymxr_pg0
                TICKWL  ymxr_lt0,2,0,2,ymxr_pg0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lt0_end:
                LPAD    KEY_LT,ymxr_pg1
                TICKWL  ymxr_lt1,2,0,2,ymxr_pg1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lt1_end:
                LPAD    KEY_LT,ymxr_pg2
                TICKWL  ymxr_lt2,2,0,2,ymxr_pg2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lt2_end:
                LPAD    KEY_LT,ymxr_pg3
                TICKWL  ymxr_lt3,2,0,2,ymxr_pg3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lt3_end:
                LPAD    KEY_LE,ymxr_pg0
                TICKWL  ymxr_le0,2,1,2,ymxr_pg0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_le0_end:
                LPAD    KEY_LE,ymxr_pg1
                TICKWL  ymxr_le1,2,1,2,ymxr_pg1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_le1_end:
                LPAD    KEY_LE,ymxr_pg2
                TICKWL  ymxr_le2,2,1,2,ymxr_pg2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_le2_end:
                LPAD    KEY_LE,ymxr_pg3
                TICKWL  ymxr_le3,2,1,2,ymxr_pg3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_le3_end:
                LPAD    KEY_LF,ymxr_pg0
                TICKWL  ymxr_lf0,2,1,1,ymxr_pg0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lf0_end:
                LPAD    KEY_LF,ymxr_pg1
                TICKWL  ymxr_lf1,2,1,1,ymxr_pg1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lf1_end:
                LPAD    KEY_LF,ymxr_pg2
                TICKWL  ymxr_lf2,2,1,1,ymxr_pg2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lf2_end:
                LPAD    KEY_LF,ymxr_pg3
                TICKWL  ymxr_lf3,2,1,1,ymxr_pg3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lf3_end:
                LPAD    KEY_LK,ymxr_pg0
                TICKL   ymxr_lk0,0,ymxr_pg0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lk0_end:
                LPAD    KEY_LK,ymxr_pg1
                TICKL   ymxr_lk1,0,ymxr_pg1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lk1_end:
                LPAD    KEY_LK,ymxr_pg2
                TICKL   ymxr_lk2,0,ymxr_pg2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lk2_end:
                LPAD    KEY_LK,ymxr_pg3
                TICKL   ymxr_lk3,0,ymxr_pg3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lk3_end:
                LPAD    KEY_LB,ymxr_pg0
                TICKWL  ymxr_lb0,3,0,3,ymxr_pg0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lb0_end:
                LPAD    KEY_LB,ymxr_pg1
                TICKWL  ymxr_lb1,3,0,3,ymxr_pg1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lb1_end:
                LPAD    KEY_LB,ymxr_pg2
                TICKWL  ymxr_lb2,3,0,3,ymxr_pg2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lb2_end:
                LPAD    KEY_LB,ymxr_pg3
                TICKWL  ymxr_lb3,3,0,3,ymxr_pg3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lb3_end:
                LPAD    KEY_LN,ymxr_pg0
                TICKWL  ymxr_ln0,2,0,1,ymxr_pg0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_ln0_end:
                LPAD    KEY_LN,ymxr_pg1
                TICKWL  ymxr_ln1,2,0,1,ymxr_pg1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_ln1_end:
                LPAD    KEY_LN,ymxr_pg2
                TICKWL  ymxr_ln2,2,0,1,ymxr_pg2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_ln2_end:
                LPAD    KEY_LN,ymxr_pg3
                TICKWL  ymxr_ln3,2,0,1,ymxr_pg3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_ln3_end:
                LPAD    KEY_LC,ymxr_pg0
                TICKL   ymxr_lc0,1,ymxr_pg0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lc0_end:
                LPAD    KEY_LC,ymxr_pg1
                TICKL   ymxr_lc1,1,ymxr_pg1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lc1_end:
                LPAD    KEY_LC,ymxr_pg2
                TICKL   ymxr_lc2,1,ymxr_pg2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lc2_end:
                LPAD    KEY_LC,ymxr_pg3
                TICKL   ymxr_lc3,1,ymxr_pg3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lc3_end:
                LPAD    KEY_LV,ymxr_pg0
                TICKWL  ymxr_lv0,3,0,2,ymxr_pg0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lv0_end:
                LPAD    KEY_LV,ymxr_pg1
                TICKWL  ymxr_lv1,3,0,2,ymxr_pg1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lv1_end:
                LPAD    KEY_LV,ymxr_pg2
                TICKWL  ymxr_lv2,3,0,2,ymxr_pg2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lv2_end:
                LPAD    KEY_LV,ymxr_pg3
                TICKWL  ymxr_lv3,3,0,2,ymxr_pg3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lv3_end:
                .if     YMXR_DEFER
                ds.b    (ymxr_tick0-*)&255
                .endif
ymxr_bank0end:
                .endif

                .if     YMXR_DEFER
; Bank 1: every handler again, in the order of bank 0, BANK bytes on. A
; deferred start on a running timer is armed in the bank its vector does
; not point into, so the handler its first tick reads is patched whole
; while the running one plays on (SPEC.md 4.8).
ymxr_bank1:
                TICK    ymxr_tick0b,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICK    ymxr_tick1b,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICK    ymxr_tick2b,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICK    ymxr_tick3b,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                SQUARE  ymxr_sq0b,$DF,$FFFFFA0F,$070,ymxr_inst0
                SQUARE  ymxr_sq1b,$EF,$FFFFFA11,$007,ymxr_inst1
                SQUARE  ymxr_sq2b,$FE,$FFFFFA0F,$707,ymxr_inst2
                SQUARE  ymxr_sq3b,$DF,$FFFFFA11,$077,ymxr_inst3
                ONEROW  ymxr_one0b,$DF,$FFFFFA0F,$070,ymxr_inst0
                ONEROW  ymxr_one1b,$EF,$FFFFFA11,$007,ymxr_inst1
                ONEROW  ymxr_one2b,$FE,$FFFFFA0F,$707,ymxr_inst2
                ONEROW  ymxr_one3b,$DF,$FFFFFA11,$077,ymxr_inst3
                TICKC   ymxr_cnt0b,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKC   ymxr_cnt1b,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKC   ymxr_cnt2b,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKC   ymxr_cnt3b,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                TICKW   ymxr_two0b,2,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_two1b,2,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_two2b,2,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_two3b,2,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                TICKW   ymxr_three0b,3,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_three1b,3,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_three2b,3,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_three3b,3,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                TICKW   ymxr_env0b,2,1,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_env1b,2,1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_env2b,2,1,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_env3b,2,1,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                .if     YMXR_MAP
                LPAD    KEY_LT,ymxr_pg0b
                TICKWL  ymxr_lt0b,2,0,2,ymxr_pg0b,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lt0b_end:
                LPAD    KEY_LT,ymxr_pg1b
                TICKWL  ymxr_lt1b,2,0,2,ymxr_pg1b,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lt1b_end:
                LPAD    KEY_LT,ymxr_pg2b
                TICKWL  ymxr_lt2b,2,0,2,ymxr_pg2b,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lt2b_end:
                LPAD    KEY_LT,ymxr_pg3b
                TICKWL  ymxr_lt3b,2,0,2,ymxr_pg3b,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lt3b_end:
                LPAD    KEY_LE,ymxr_pg0b
                TICKWL  ymxr_le0b,2,1,2,ymxr_pg0b,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_le0b_end:
                LPAD    KEY_LE,ymxr_pg1b
                TICKWL  ymxr_le1b,2,1,2,ymxr_pg1b,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_le1b_end:
                LPAD    KEY_LE,ymxr_pg2b
                TICKWL  ymxr_le2b,2,1,2,ymxr_pg2b,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_le2b_end:
                LPAD    KEY_LE,ymxr_pg3b
                TICKWL  ymxr_le3b,2,1,2,ymxr_pg3b,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_le3b_end:
                LPAD    KEY_LF,ymxr_pg0b
                TICKWL  ymxr_lf0b,2,1,1,ymxr_pg0b,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lf0b_end:
                LPAD    KEY_LF,ymxr_pg1b
                TICKWL  ymxr_lf1b,2,1,1,ymxr_pg1b,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lf1b_end:
                LPAD    KEY_LF,ymxr_pg2b
                TICKWL  ymxr_lf2b,2,1,1,ymxr_pg2b,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lf2b_end:
                LPAD    KEY_LF,ymxr_pg3b
                TICKWL  ymxr_lf3b,2,1,1,ymxr_pg3b,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lf3b_end:
                LPAD    KEY_LK,ymxr_pg0b
                TICKL   ymxr_lk0b,0,ymxr_pg0b,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lk0b_end:
                LPAD    KEY_LK,ymxr_pg1b
                TICKL   ymxr_lk1b,0,ymxr_pg1b,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lk1b_end:
                LPAD    KEY_LK,ymxr_pg2b
                TICKL   ymxr_lk2b,0,ymxr_pg2b,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lk2b_end:
                LPAD    KEY_LK,ymxr_pg3b
                TICKL   ymxr_lk3b,0,ymxr_pg3b,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lk3b_end:
                LPAD    KEY_LB,ymxr_pg0b
                TICKWL  ymxr_lb0b,3,0,3,ymxr_pg0b,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lb0b_end:
                LPAD    KEY_LB,ymxr_pg1b
                TICKWL  ymxr_lb1b,3,0,3,ymxr_pg1b,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lb1b_end:
                LPAD    KEY_LB,ymxr_pg2b
                TICKWL  ymxr_lb2b,3,0,3,ymxr_pg2b,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lb2b_end:
                LPAD    KEY_LB,ymxr_pg3b
                TICKWL  ymxr_lb3b,3,0,3,ymxr_pg3b,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lb3b_end:
                LPAD    KEY_LN,ymxr_pg0b
                TICKWL  ymxr_ln0b,2,0,1,ymxr_pg0b,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_ln0b_end:
                LPAD    KEY_LN,ymxr_pg1b
                TICKWL  ymxr_ln1b,2,0,1,ymxr_pg1b,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_ln1b_end:
                LPAD    KEY_LN,ymxr_pg2b
                TICKWL  ymxr_ln2b,2,0,1,ymxr_pg2b,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_ln2b_end:
                LPAD    KEY_LN,ymxr_pg3b
                TICKWL  ymxr_ln3b,2,0,1,ymxr_pg3b,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_ln3b_end:
                LPAD    KEY_LC,ymxr_pg0b
                TICKL   ymxr_lc0b,1,ymxr_pg0b,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lc0b_end:
                LPAD    KEY_LC,ymxr_pg1b
                TICKL   ymxr_lc1b,1,ymxr_pg1b,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lc1b_end:
                LPAD    KEY_LC,ymxr_pg2b
                TICKL   ymxr_lc2b,1,ymxr_pg2b,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lc2b_end:
                LPAD    KEY_LC,ymxr_pg3b
                TICKL   ymxr_lc3b,1,ymxr_pg3b,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lc3b_end:
                LPAD    KEY_LV,ymxr_pg0b
                TICKWL  ymxr_lv0b,3,0,2,ymxr_pg0b,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lv0b_end:
                LPAD    KEY_LV,ymxr_pg1b
                TICKWL  ymxr_lv1b,3,0,2,ymxr_pg1b,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lv1b_end:
                LPAD    KEY_LV,ymxr_pg2b
                TICKWL  ymxr_lv2b,3,0,2,ymxr_pg2b,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lv2b_end:
                LPAD    KEY_LV,ymxr_pg3b
                TICKWL  ymxr_lv3b,3,0,2,ymxr_pg3b,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lv3b_end:
ymxr_bank1end:
                .endif

; The installers, one an effect: the start a deferred row armed, performed
; at the tick that reads the running source's last row. The hook in the
; running handler branches here: the word it replaced is written back,
; the timer is stopped where the start sets timerReset, the count and the
; select are written, and the vector is aimed at the armed handler, whose
; next tick reads the new source (SPEC.md 4.8). Every operand is patched
; at the frame that arms the start.
                INSTALL ymxr_inst0,$FFFFFA19,$FFFFFA1F,$F0,$FFFFFA07,5,0,0,$134,$DF,$FFFFFA0F
                INSTALL ymxr_inst1,$FFFFFA1D,$FFFFFA25,$F0,$FFFFFA09,4,0,1,$110,$EF,$FFFFFA11
                INSTALL ymxr_inst2,$FFFFFA1B,$FFFFFA21,$F0,$FFFFFA07,0,0,0,$120,$FE,$FFFFFA0F
                INSTALL ymxr_inst3,$FFFFFA1D,$FFFFFA23,$0F,$FFFFFA09,5,4,1,$114,$DF,$FFFFFA11
                .endif
ymxr_reach:                             ; past the last handler, which the
                                        ; reach init measures runs to
                .endm

                .macro  HANDLEQU
; The offsets the patching writes at, measured off the first handler's
; labels: the row it writes, the marker it reads, and its loop cell.
; A monitor's mark is between those blocks, and the distances follow
; it. Every handler is laid out by the one macro, so one handler's
; offsets are all four handlers'. The rig reads them back.
TICK_SEL        equ     ymxr_tick0_row-ymxr_tick0+3   ; the target register
TICK_PTR        equ     ymxr_tick0_row-ymxr_tick0+8   ; the place: a tick's row
TICK_STEP       equ     ymxr_tick0_step-ymxr_tick0+2  ; the place's address
TICK_LOOPT      equ     ymxr_tick0_mark-ymxr_tick0+2  ; the loop cell's address
TICK_LOOPR      equ     ymxr_tick0_mark-ymxr_tick0+10 ; the same
TICK_LOOPW      equ     ymxr_tick0_mark-ymxr_tick0+14 ; the place's address
TICK_CELL       equ     ymxr_tick0_cell-ymxr_tick0    ; row RR, or 0 once
                .if     YMXR_NEST
TICK_SR         equ     ymxr_tick0_sr-ymxr_tick0      ; the level it drops
                .endif
TICK_SIZE       equ     ymxr_tick1-ymxr_tick0

; The counted handler, the same way: the register it selects, the place,
; the address of the place, the counter's address in the subq that steps
; it, the loop cell and its address, the rows of a pass through the loop
; and the counter the reload writes to.
TICKC_SEL       equ     ymxr_cnt0_row-ymxr_cnt0+3
TICKC_PTR       equ     ymxr_cnt0_row-ymxr_cnt0+8
TICKC_STEP      equ     ymxr_cnt0_step-ymxr_cnt0+2
TICKC_CNTA      equ     ymxr_cnt0_cnt-ymxr_cnt0+2
TICKC_LOOPT     equ     ymxr_cnt0_mark-ymxr_cnt0+2
TICKC_LOOPR     equ     ymxr_cnt0_mark-ymxr_cnt0+10
TICKC_LOOPW     equ     ymxr_cnt0_mark-ymxr_cnt0+14
TICKC_RELD      equ     ymxr_cnt0_reld-ymxr_cnt0+2
TICKC_RELDA     equ     ymxr_cnt0_reld-ymxr_cnt0+4
TICKC_CELL      equ     ymxr_cnt0_cell-ymxr_cnt0
TICKC_LEFT      equ     ymxr_cnt0_left-ymxr_cnt0
TICKC_SIZE      equ     ymxr_cnt1-ymxr_cnt0

; A square's handler, the same way: the register it selects, the row it
; is at, the two rows' difference, and the address of the row it
; is at, which init writes once.
SQ_SEL          equ     ymxr_sq0_sel-ymxr_sq0+3
SQ_VAL          equ     ymxr_sq0_val-ymxr_sq0+3
SQ_MASK         equ     ymxr_sq0_tog-ymxr_sq0+3
SQ_PTR          equ     ymxr_sq0_tog-ymxr_sq0+4
SQ_SR           equ     ymxr_sq0_val-ymxr_sq0+6
SQ_SIZE         equ     ymxr_sq1-ymxr_sq0

; A one-row source's handler: the register it selects and the row it
; writes, both immediates a start puts there, those two fields alone.
ONE_SEL         equ     ymxr_one0_sel-ymxr_one0+3
ONE_VAL         equ     ymxr_one0_val-ymxr_one0+3
ONE_SR          equ     ymxr_one0_val-ymxr_one0+6
ONE_SIZE        equ     ymxr_one1-ymxr_one0

                TWAT    TW2,ymxr_two0
                TWAT    TW3,ymxr_three0
TW3_SEL3        equ     ymxr_three0_sel3-ymxr_three0+3
TW3_PTR3        equ     ymxr_three0_ptr3-ymxr_three0+2
TW3_STEP3       equ     ymxr_three0_step3-ymxr_three0+2
TW3_LOOP3R      equ     ymxr_three0_loop3-ymxr_three0+2
TW3_LOOP3W      equ     ymxr_three0_loop3-ymxr_three0+6
TW2_SIZE        equ     ymxr_two1-ymxr_two0
TW3_SIZE        equ     ymxr_three1-ymxr_three0

; The counted shape of two columns, the offsets above and four besides:
; the counter's address in the subq that steps it, the rows of a pass
; through the loop, the counter the reload writes to, and the counter.
                TWAT    TWC,ymxr_env0
TWC_CNT         equ     ymxr_env0_cnt-ymxr_env0+2
TWC_RELD        equ     ymxr_env0_reld-ymxr_env0+2
TWC_RELDA       equ     ymxr_env0_reld-ymxr_env0+4
TWC_LEFT        equ     ymxr_env0_left-ymxr_env0
TWC_SIZE        equ     ymxr_env1-ymxr_env0

                .if     YMXR_DEFER
; A pending start (SPEC.md 4.8): bank 1 at a fixed distance from bank 0;
; in each handler the word the hook writes over, at the head of the path
; that ends the source, and the hook it branches to; the square's copy of
; the eori that moves to its other row; and the installer's operands.
BANK            equ     ymxr_bank1-ymxr_tick0
TICK_LAST       equ     ymxr_tick0_mark-ymxr_tick0
TICK_HOOK       equ     ymxr_tick0_hook-ymxr_tick0
TICKC_LAST      equ     ymxr_cnt0_mark-ymxr_cnt0
TICKC_HOOK      equ     ymxr_cnt0_hook-ymxr_cnt0
SQ_LAST         equ     ymxr_sq0_tog-ymxr_sq0
SQ_HOOK         equ     ymxr_sq0_hook-ymxr_sq0
SQ_HMASK        equ     ymxr_sq0_htog-ymxr_sq0+3
SQ_HPTR         equ     ymxr_sq0_htog-ymxr_sq0+4
ONE_LAST        equ     ymxr_one0_end-ymxr_one0
ONE_HOOK        equ     ymxr_one0_hook-ymxr_one0
INST_BACKW      equ     ymxr_inst0_back-ymxr_inst0+2
INST_BACKA      equ     ymxr_inst0_back-ymxr_inst0+4
INST_SKIP       equ     ymxr_inst0_skip-ymxr_inst0
INST_SKIPW      equ     $6000|(ymxr_inst0_run-ymxr_inst0_skip-2)
INST_CNT        equ     ymxr_inst0_cnt-ymxr_inst0+3
INST_VEC        equ     ymxr_inst0_vec-ymxr_inst0+2
INST_SEL        equ     ymxr_inst0_sel-ymxr_inst0+3   ; a control register for one timer
INSTS_SEL       equ     ymxr_inst1_sel-ymxr_inst1+3   ; one that two timers share
                .endif

; Every handler that steps a place keeps it at one offset, which a start
; reads through the vector for every shape the timer runs; bank 1 lays the
; handlers out as bank 0 does; and each hook is within the byte a bra.s
; branches by.
                .if     (TICKC_PTR<>TICK_PTR)|(TW2_PTR1<>TICK_PTR)|(TW3_PTR1<>TICK_PTR)|(TWC_PTR1<>TICK_PTR)
                .error  "a handler that steps a place keeps it at another offset"
                .endif
                .if     YMXR_DEFER
                .if     (ymxr_env3b-ymxr_env3)<>BANK
                .error  "bank 1 is laid out apart from bank 0"
                .endif
                .if     (TICK_HOOK-TICK_LAST-2)>127
                .error  "the general handler's hook is past a bra.s"
                .endif
                .if     (TICKC_HOOK-TICKC_LAST-2)>127
                .error  "the counted handler's hook is past a bra.s"
                .endif
                .if     (TW2_HOOK-TW2_LAST-2)>127
                .error  "the handler of two columns has its hook past a bra.s"
                .endif
                .if     (TW3_HOOK-TW3_LAST-2)>127
                .error  "the handler of three columns has its hook past a bra.s"
                .endif
                .if     (TWC_HOOK-TWC_LAST-2)>127
                .error  "the counted handler of two columns has its hook past a bra.s"
                .endif
                .if     (SQ_HOOK-SQ_LAST-2)>127
                .error  "the square's hook is past a bra.s"
                .endif
                .if     (ONE_HOOK-ONE_LAST-2)>127
                .error  "the one-row handler's hook is past a bra.s"
                .endif
; The word a hook writes, out of the macro defined above the handlers.
                HOOKW   TICK
                HOOKW   TICKC
                HOOKW   TW2
                HOOKW   TW3
                HOOKW   TWC
                HOOKW   SQ
                HOOKW   ONE
                .if     (ymxr_inst1_skip-ymxr_inst1)<>INST_SKIP
                .error  "the installers place the skip apart"
                .endif
                .endif
                .endm

                .if     YMXR_MAP=0
                HANDLERS
                HANDLEQU
                .endif

; The start's dispatch: where the branch that leaves the general
; handler's path has its displacement, and where the two shapes with
; a separate handler lie relative to it. One macro lays out every effect and no
; timer's field is between the three, so one effect's distances are
; all four's.
PICK_AT         equ     ymxr_tick0_pick-ymxr_tick0_over+2
PICK_SQ         equ     ymxr_tick0_square-ymxr_tick0_pick-2
PICK_ONE        equ     ymxr_tick0_one-ymxr_tick0_pick-2
PICK_BOTH       equ     ymxr_tick0_shaped-ymxr_tick0_pick-2

; The three branches a tune with a source of several columns aims
; elsewhere: the one that tells the shapes apart, the one that places a
; start whose running source has several columns, and the one that resets
; such a place. Each is a byte displacement, and init writes the value the
; tune's sources need. A tune whose sources are one column keeps all
; three where the assembler wrote them.
;
; The first two are at one offset from an effect's head under every
; effect. The third does not: the rate's path to it runs through the
; blocks the shared control register and the select's shift add, so the
; table below names that site an effect.
WIDE_AT         equ     ymxr_tick0_wide-ymxr_tick0_over+1
WIDE_ONE        equ     ymxr_tick0_one-ymxr_tick0_wide-2
WIDE_WIDEN      equ     ymxr_tick0_widen-ymxr_tick0_wide-2
RESET_DONE      equ     ymxr_tick0_done-ymxr_tick0_reset-2
; The three branches a tune that defers a start aims at the paths that
; read a pending one (4.8), each at one offset from an effect's head, and
; the displacements of both aims. A tune that maps a value aims the first
; and the second of them too, and a branch at the start (1.10).
                .if     YMXR_DEFER|YMXR_MAP
RS_AT           equ     ymxr_tick0_rs-ymxr_tick0_over
UNSET_AT        equ     ymxr_tick0_unset-ymxr_tick0_over+2
UNSET_RATE      equ     ymxr_tick0_rate-ymxr_tick0_unset-2
                .if     (ymxr_tick3_rs-ymxr_tick3_over)<>RS_AT
                .error  "effect 3 lays the timer's reset out apart from effect 0"
                .endif
                .if     (ymxr_tick3_rate-ymxr_tick3_unset-2)<>UNSET_RATE
                .error  "effect 3 lays its rate out apart from effect 0"
                .endif
                .endif
                .if     YMXR_MAP
NOW_AT          equ     ymxr_tick0_now-ymxr_tick0_over+2
NOW_START       equ     ymxr_tick0_start-ymxr_tick0_now-2
NOW_MAP         equ     ymxr_tick0_mapped-ymxr_tick0_now-2
UNSET_MAP       equ     ymxr_tick0_remapck-ymxr_tick0_unset-2
RS_MAP          equ     ymxr_tick0_rmk-ymxr_tick0_rs-2
                .if     (ymxr_tick3_now-ymxr_tick3_over+2)<>NOW_AT
                .error  "effect 3 lays its start out apart from effect 0"
                .endif
                .if     (ymxr_tick3_mapped-ymxr_tick3_now-2)<>NOW_MAP
                .error  "effect 3 lays its mapped start out apart from effect 0"
                .endif
                .if     (ymxr_tick3_remapck-ymxr_tick3_unset-2)<>UNSET_MAP
                .error  "effect 3 lays its remap out apart from effect 0"
                .endif
                .if     (ymxr_tick3_rmk-ymxr_tick3_rs-2)<>RS_MAP
                .error  "effect 3 lays its remap's reset out apart from effect 0"
                .endif
                .endif
                .if     YMXR_DEFER
RS_DEFER        equ     ymxr_tick0_rck-ymxr_tick0_rs-2
UNSET_DEFER     equ     ymxr_tick0_retune-ymxr_tick0_unset-2
SET_AT          equ     ymxr_tick0_set-ymxr_tick0_over
SET_STOP        equ     ymxr_tick0_stop-ymxr_tick0_set-2
SET_DEFER       equ     ymxr_tick0_check-ymxr_tick0_set-2
                .if     (ymxr_tick3_check-ymxr_tick3_set-2)<>SET_DEFER
                .error  "effect 3 lays its pending paths out apart from effect 0"
                .endif
                .endif
RESET_WIDE      equ     ymxr_tick0_wrow-ymxr_tick0_reset-2
                .if     YMXR_MAP
; The byte branches init writes, against the larger effect of a core with
; the lookup.
                .if     (((WIDE_ONE+128)|(WIDE_WIDEN+128)|(RESET_DONE+128)|(RESET_WIDE+128))&$FFFFFF00)<>0
                .error  "a branch init writes is past a byte's displacement"
                .endif
                .endif

; One column of a start of a source of several columns: the register the
; map names for the slot, the column's loop cell, its place at row 0, and
; the row that place is at.
                .macro  WSLOT   sel,ptr,cell,first,at
                moveq   #0,d5
                move.b  \at+1(a2),d5            ; the column this slot writes
                mulu    d1,d5                   ; where it is from column 0
                move.b  \at(a2),\sel(a5)        ; the register it selects
                move.l  d0,d4                   ; the column's loop cell, or
                beq.s   .none\~                 ; none where the source plays
                add.l   d5,d4                   ; once
                .if     YMXR_PCREL
                sub.l   a5,d4                   ; the displacement from the
                sub.l   #\ptr,d4                ; instruction that reads the
                .endif                          ; column
.none\~:
                .if     YMXR_PCREL
                move.w  d4,\cell(a5)
                .else
                move.l  d4,\cell(a5)
                .endif
                add.l   a4,d5                   ; the place at row 0, kept for
                move.l  d5,\first(a3)           ; the place's reset
                add.l   d3,d5                   ; and the row it is at
                .if     YMXR_PCREL
                sub.l   a5,d5
                sub.l   #\ptr,d5
                move.w  d5,\ptr(a5)
                .else
                move.l  d5,\ptr(a5)
                .endif
                .endm

; The place's reset on a handler of several columns (SPEC.md 1.9): a
; place a column, out of the places at row 0 the start wrote into the
; effect's record. d3 the effect.
;
; The places are behind level 7: a tick between two of them reads one
; column at the row the reset names and another at the row it left.
; A place into the instruction that reads a column: the row's address, or
; its displacement from that instruction under YMXR_PCREL. a3 the
; effect's record, a5 the handler, d0 scratch.
                .macro  WPLACE  first,ptr
                .if     YMXR_PCREL
                move.l  \first(a3),d0
                sub.l   a5,d0
                sub.l   #\ptr,d0
                move.w  d0,\ptr(a5)
                .else
                move.l  \first(a3),\ptr(a5)
                .endif
                .endm

                .if     YMXR_MAP
; The place's reset on a handler of several columns or a counted one
; (SPEC.md 1.9), by the offsets of the kind the effect runs: the
; identity's handler, or the lookup instance of its shape where it runs
; through a transformation. d3 the effect; d0 to d5, a3 and a5 clobbered.
ymxr_widereset:
                move.w  d3,d4
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; the effect's record
                cmp.b   #1,FX_SHAPE(a3)         ; a source of one row is at
                beq.s   .moves                  ; row 0 and moves no place
                movem.l a1-a2,-(sp)
                lea     ymxr_timers(pc),a5
                lsl.w   #3,d3
                movea.w (a5,d3.w),a5
                movea.l (a5),a5                 ; the running handler
                bsr     ymxr_kindof             ; d0 the lookup kind, d1 the
                tst.b   FX_RUN(a3)              ; identity's
                bne.s   .kind
                move.w  d1,d0
.kind:          mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2
                move.w  KD_LEFT(a2),d5
                move.w  KD_COLS(a2),d4
                lea     FX_FIRST(a3),a1         ; a column's place at row 0
                move.w  sr,-(sp)
                ori.w   #$0700,sr
.col:           moveq   #0,d1
                move.w  KD_PTR(a2),d1
                move.l  (a1)+,d0
                .if     YMXR_PCREL
                sub.l   a5,d0
                sub.l   d1,d0
                move.w  d0,(a5,d1.w)
                .else
                move.l  d0,(a5,d1.w)
                .endif
                addq.l  #4,a2
                dbra    d4,.col
                tst.w   d5                      ; R rows left
                beq.s   .reset
                move.w  FX_ROWS+2(a3),(a5,d5.w)
.reset:         move.w  (sp)+,sr
                movem.l (sp)+,a1-a2
.moves:         rts
                .else
ymxr_widereset:
                move.w  d3,d4
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; the effect's record
                cmp.b   #1,FX_SHAPE(a3)         ; a source of one row is at
                beq.w   .moves                  ; row 0 and moves no place
                lea     ymxr_timers(pc),a5
                lsl.w   #3,d3
                movea.w (a5,d3.w),a5
                movea.l (a5),a5                 ; the running handler
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                cmp.b   #2,FX_SHAPE(a3)
                beq.w   .two
                cmp.b   #3,FX_SHAPE(a3)
                beq.s   .three
                cmp.b   #4,FX_SHAPE(a3)
                beq.s   .counted
                WPLACE  FX_FIRST2,TWC_PTR2      ; a counted source of two
                WPLACE  FX_FIRST,TWC_PTR1       ; columns: both places, and
                move.w  FX_ROWS+2(a3),TWC_LEFT(a5) ; R rows left
                bra.s   .reset
.counted:       WPLACE  FX_FIRST,TICKC_PTR      ; the counted handler: the
                move.w  FX_ROWS+2(a3),TICKC_LEFT(a5) ; place and R rows left
                bra.s   .reset
.three:         WPLACE  FX_FIRST3,TW3_PTR3
                WPLACE  FX_FIRST2,TW3_PTR2
                WPLACE  FX_FIRST,TW3_PTR1
                bra.s   .reset
.two:           WPLACE  FX_FIRST2,TW2_PTR2
                WPLACE  FX_FIRST,TW2_PTR1
.reset:         move.w  (sp)+,sr
.moves:         rts
                .endif

; The start of a source of several columns, which every effect reaches:
; the handler its columns pick, and a register, a place and a loop cell a
; column written into it. d4 the effect, d0 the source's cell as init
; tagged it, d1 the source's number times eight, d2 the row's control
; byte, a4 the source's first row. a5 the handler to aim at, and Z where
; the source is one row, which the general handler's shape runs.
;
; The writes are behind level 7, as the general handler's three do: a
; tick between a select and a place writes one column's row to another
; column's register, and one between two places reads two rows.
ymxr_widestart:
                addq.l  #1,d0                   ; -1: a source of one row, at
                beq.w   .one                    ; any columns
                move.l  d6,-(sp)                ; the effect, kept while the
                move.w  d4,d6                   ; handlers are resolved
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; the effect's record
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; a deferred start is armed in
                beq.s   .rec                    ; the record's mirror (4.8)
                lea     ARMOFF(a3),a3
                .endif
.rec:           lea     WS_WIDE(a0),a2
                move.l  -8(a2,d1.w),d5          ; the source's loop cell
                addq.l  #1,d0
                beq.s   .two                    ; two columns
                addq.l  #1,d0
                bne.w   .counted                ; three columns, or the tag of
                moveq   #3,d4                   ; a source whose column fills
                move.w  d6,d0                   ; its byte
                mulu    #TW3_SIZE,d0
                move.w  -4(a2,d1.w),d1          ; the stride from one column to
                lea     ymxr_three0(pc),a5      ; the next
                bra.s   .handler
.two:           moveq   #2,d4
                move.w  d6,d0
                mulu    #TW2_SIZE,d0
                move.w  -4(a2,d1.w),d1
                lea     ymxr_two0(pc),a5
.handler:       adda.w  d0,a5                   ; this effect's handler
                move.l  d5,d0                   ; the loop cell
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; a deferred start: the bank and
                beq.s   .running                ; the row the arming chose (4.8)
                adda.w  FX_ABANK-ARMOFF(a3),a5
                move.l  FX_APLACE-ARMOFF(a3),d3
                bra.w   .at
                .endif
; The row the place is at, off the running handler through the vector
; where the row does not reset the place, modulo R of this source (SPEC.md
; 1.8.4): every handler that steps a place keeps it at one offset, a
; square in bit 7 of its immediate, and a source of one row is at row 0.
.running:       moveq   #0,d3
                btst    #5,d2
                bne.w   .at
                lea     ymxr_timers(pc),a2
                move.w  d6,d5
                lsl.w   #3,d5
                movea.w (a2,d5.w),a2
                movea.l (a2),a2                 ; the running handler
                move.b  FX_SHAPE(a3),d5
                beq.s   .steps
                bmi.s   .square
                cmp.b   #1,d5
                beq.w   .at
.steps:
                .if     YMXR_PCREL
                move.w  TICK_PTR(a2),d3
                ext.l   d3
                add.l   a2,d3
                add.l   #TICK_PTR,d3
                .else
                move.l  TICK_PTR(a2),d3
                .endif
                sub.l   FX_FIRST(a3),d3         ; the place against the place at
                bra.s   .mod                    ; row 0
.square:        tst.b   SQ_VAL(a2)
                bpl.w   .at
                moveq   #1,d3
.mod:           move.l  d4,-(sp)
                move.l  DT_ROWS-DT_PAYLOAD(a4),d4
                bsr.w   ymxr_rem
                move.l  (sp)+,d4
; A register, a place and a loop cell a column, out of the target's row
; of the map; and R, which a pending start and a place's reset read.
.at:            move.l  DT_ROWS-DT_PAYLOAD(a4),FX_ROWS(a3)
                cmp.w   #1,d4                   ; a source whose column fills
                beq.w   .countedaim             ; its byte
                cmp.w   #4,d4                   ; a counted source of two
                beq.w   .envaim                 ; columns
                moveq   #0,d5
                move.b  FX_TARGET(a3),d5
                sub.w   #14,d5                  ; the targets of several
                lsl.w   #3,d5                   ; registers begin at 14 (SPEC.md
                lea     ymxr_wide(pc),a2        ; 2.1): eight bytes a target in
                adda.w  d5,a2                   ; the map, two a column
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                cmp.w   #3,d4
                beq.s   .slots
                WSLOT   TW2_SEL1,TW2_PTR1,TW2_CELLS,FX_FIRST,0
                WSLOT   TW2_SEL2,TW2_PTR2,TW2_CELLS+CELL_SIZE,FX_FIRST2,2
                move.b  #2,FX_SHAPE(a3)
                .if     YMXR_PCREL
                bra.w   .aimed
                .else
                bra.s   .aimed
                .endif
.slots:         WSLOT   TW3_SEL1,TW3_PTR1,TW3_CELLS,FX_FIRST,0
                WSLOT   TW3_SEL2,TW3_PTR2,TW3_CELLS+CELL_SIZE,FX_FIRST2,2
                WSLOT   TW3_SEL3,TW3_PTR3,TW3_CELLS+CELL_SIZE+CELL_SIZE,FX_FIRST3,4
                move.b  #3,FX_SHAPE(a3)
.aimed:         move.w  (sp)+,sr
                lea     YM_SELECT.w,a2          ; the chip's select register,
                move.l  (sp)+,d6                ; which the frame reads it in
                moveq   #1,d0                   ; a source of several columns:
.one:           rts                             ; Z where it is one row

; A start of a source whose one column fills its byte: the counted
; handler aimed at its rows, its counter at the rows from the place to
; the end and its reload at the rows of the loop, which init resolved
; beside the source's loop cell.
.counted:       addq.l  #1,d0                   ; the tag of a counted source
                bne.s   .env                    ; of several columns
                moveq   #1,d4                   ; the shape this start leaves
                move.w  d6,d0
                mulu    #TICKC_SIZE,d0
                lea     ymxr_cnt0(pc),a5
                bra.w   .handler

; A counted source of two columns, which `setEnvelope` runs (SPEC.md
; 2.1.3): R waits on the stack until the slots are written, since the
; stride is read into the register that indexes the source's place in the
; workspace.
.env:           moveq   #4,d4
                move.w  -2(a2,d1.w),-(sp)       ; R, a pass through the source
                move.w  d6,d0
                mulu    #TWC_SIZE,d0
                move.w  -4(a2,d1.w),d1          ; the stride
                lea     ymxr_env0(pc),a5
                bra.w   .handler
.countedaim:    lea     WS_WIDE(a0),a2
                move.w  -4(a2,d1.w),d4          ; R, a pass through the source
                sub.w   d3,d4                   ; the rows from the place on
                move.w  -2(a2,d1.w),d5          ; R less RR, the rows of a pass
                move.l  a4,d1                   ; through the loop
                add.l   d3,d1                   ; the row the place is at
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  FX_TARGET(a3),TICKC_SEL(a5)
                .if     YMXR_PCREL
                sub.l   a5,d1                   ; the displacement from the
                sub.l   #TICKC_PTR,d1           ; instruction that reads the row
                move.w  d1,TICKC_PTR(a5)
                .else
                move.l  d1,TICKC_PTR(a5)
                .endif
                move.w  d4,TICKC_LEFT(a5)
                move.w  d5,TICKC_RELD(a5)
                .if     YMXR_PCREL
                tst.l   d0                      ; 0: the source plays once
                beq.s   .celled
                sub.l   a5,d0
                sub.l   #TICKC_PTR,d0
.celled:        move.w  d0,TICKC_CELL(a5)
                .else
                move.l  d0,TICKC_CELL(a5)
                .endif
                move.b  #4,FX_SHAPE(a3)
                move.l  a4,FX_FIRST(a3)
                bra.w   .aimed

; A start of a counted source of two columns: the two slots of the target
; as the marked shape writes them, and the counter and its reload as the
; counted handler of one column reads them. The rows of a pass through
; the loop are R less the row the cell is at, which the first row and
; the cell give.
.envaim:        moveq   #0,d5
                move.b  FX_TARGET(a3),d5
                sub.w   #14,d5                  ; the targets of several
                lsl.w   #3,d5                   ; registers begin at 14
                lea     ymxr_wide(pc),a2
                adda.w  d5,a2
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                WSLOT   TWC_SEL1,TWC_PTR1,TWC_CELLS,FX_FIRST,0
                WSLOT   TWC_SEL2,TWC_PTR2,TWC_CELLS+CELL_SIZE,FX_FIRST2,2
                move.w  2(sp),d4                ; R, under the level pushed
                sub.w   d3,d4                   ; the rows from the place on
                move.w  d4,TWC_LEFT(a5)
                move.w  2(sp),d4                ; the rows of a pass through
                tst.l   d0                      ; the loop, R where the source
                beq.s   .envonce                ; plays once
                move.l  d0,d5
                sub.l   a4,d5                   ; the row the cell is at
                sub.w   d5,d4
.envonce:       move.w  d4,TWC_RELD(a5)
                move.b  #5,FX_SHAPE(a3)
                move.w  (sp)+,sr
                addq.l  #2,sp                   ; R, off the stack
                lea     YM_SELECT.w,a2
                move.l  (sp)+,d6
                moveq   #1,d0
                rts

; A row number modulo R (SPEC.md 1.8.4): d3 the number, d4 R; d5
; scratch. A number below R is one compare. At R or above, one divu: its
; quotient fits a word where the number is below R times 65,536, which
; every place of a core reading a row through the program counter is,
; and where divu overflows, a source of 65,536 rows or more, two divu, the
; high word over R and then its remainder above the low word; where R
; itself is past a word, subtractions of R shifted.
ymxr_rem:       cmp.l   d4,d3
                bcs.s   .in
                cmp.l   #$FFFF,d4
                bhi.s   .long
                move.l  d3,d5
                divu    d4,d5
                bvc.s   .one
                move.l  d3,d5                   ; the quotient past a word
                clr.w   d5
                swap    d5
                divu    d4,d5
                move.w  d3,d5
                divu    d4,d5
.one:           clr.w   d5
                swap    d5                      ; the remainder
                move.l  d5,d3
.in:            rts
.long:          move.l  d4,d5                   ; R doubled while at or below
.up:            cmp.l   d3,d5                   ; the number
                bhi.s   .down
                add.l   d5,d5
                bra.s   .up
.down:          cmp.l   d4,d5                   ; then halved back to R, and
                bcs.s   .in                     ; subtracted where it fits
                cmp.l   d5,d3
                bcs.s   .half
                sub.l   d5,d3
.half:          lsr.l   #1,d5
                bra.s   .down

                .if     YMXR_DEFER
; A switch the installer has made (SPEC.md 4.8): the armed start's shape,
; places at row 0 and row count become the record's, and no start is
; pending. a3 the record.
ymxr_take:      move.b  FX_SHAPE+ARMOFF(a3),FX_SHAPE(a3)
                move.l  FX_FIRST+ARMOFF(a3),FX_FIRST(a3)
                move.l  FX_FIRST2+ARMOFF(a3),FX_FIRST2(a3)
                move.l  FX_FIRST3+ARMOFF(a3),FX_FIRST3(a3)
                move.l  FX_ROWS+ARMOFF(a3),FX_ROWS(a3)
                .if     YMXR_MAP
                move.b  FX_ALEVEL(a3),FX_LEVEL(a3)
                .endif
                clr.b   FX_PENDING(a3)
                rts

; A row that acts on an effect with a start pending (SPEC.md 4.8), at
; level 7, so the tick that would perform it is either before the test or
; after the row: where the vector names the armed handler, the switch has
; run and the record is the armed start's; otherwise, where d5 is 1, the
; start is dropped and the hooked word written back. d4 the effect. Z
; where no start is pending after it. a3 the record; a5 scratch.
ymxr_pend:      movem.l d4/a6,-(sp)
                lea     ymxr_timers(pc),a6
                lsl.w   #3,d4
                adda.w  d4,a6                   ; the effect's timer
                lsr.w   #3,d4
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; its record
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                movea.w (a6),a5
                movea.l (a5),a5                 ; the handler the vector names
                cmpa.l  FX_ARMED(a3),a5
                bne.s   .still
                bsr.s   ymxr_take
                bra.s   .done
.still:         tst.w   d5
                beq.s   .done                   ; left pending
                movea.l FX_HOOK(a3),a5          ; dropped
                move.w  FX_HOOKW(a3),(a5)
                clr.b   FX_PENDING(a3)
.done:          move.w  (sp)+,sr
                movem.l (sp)+,d4/a6
                tst.b   FX_PENDING(a3)
                rts

; A deferred start on a running timer, armed (SPEC.md 4.8): the handler of
; its source's shape, in the bank the vector does not point into, patched
; as a start patches it, at the last row of the running source modulo R
; of this one; its count, select, timer reset and handler into the
; effect's installer; and one word of the running handler, the head of the
; path that ends its source, written as a bra.s to its hook. A start
; pending already is replaced: its hook stays, and the arming runs at
; level 7, since the installer it patches may run. d1 the count column,
; d2 the control byte, d3 the source, d4 the effect. Z where the start is
; pending; NZ where the timer is stopped, with d1 the source, which the
; frame then starts at once. d0, d3 to d5 and a3 to a5 are clobbered.
ymxr_arm:       movem.l d3/d6-d7/a6,-(sp)       ; the source at 4(sp) below
                move.w  sr,-(sp)
                move.w  d4,d6                   ; the effect
                lea     ymxr_timers(pc),a6
                lsl.w   #3,d4
                adda.w  d4,a6                   ; its timer
                move.w  d6,d4
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; its record
                tst.b   FX_PENDING(a3)
                beq.s   .first
                ori.w   #$0700,sr               ; a start pending: replaced,
                movea.w (a6),a5                 ; unless the tick performed it
                movea.l (a5),a5                 ; since the frame read it
                cmpa.l  FX_ARMED(a3),a5
                bne.s   .live
                bsr.w   ymxr_take
.first:         lea     ymxr_park(pc),a4        ; no source connected before
                cmpa.l  FX_FIRST(a3),a4         ; the first start
                beq.w   .stopped
                movea.w 2(a6),a4                ; the select bits of the
                move.b  (a4),d5                 ; timer's control register
                and.b   6(a6),d5
                beq.w   .stopped
; The rate the start writes at the switch, kept as a frame keeps it (4.3):
; the count column, or the kept count where it is 0 and bit 4 is clear;
; and the select. The target its source runs on is the mirror's.
.live:          tst.b   d1
                bne.s   .cnt
                btst    #4,d2
                bne.s   .cnt
                move.b  FX_COUNT(a3),d1
.cnt:           move.b  d1,FX_COUNT(a3)
                move.b  d1,d7                   ; the count
                move.b  d2,d5
                and.b   #7,d5
                move.b  d5,FX_SELECT(a3)
                move.b  FX_TARGET(a3),FX_TARGET+ARMOFF(a3)
; The source, and the row it starts at: row 0 with bit 5, and otherwise
; R of the running source modulo R of this one, the count of the rows the
; running source played.
                move.w  4(sp),d3
                lsl.w   #3,d3
                movea.l WS_SOURCES-8(a0,d3.w),a4    ; its first row
                move.l  WS_SOURCES-4(a0,d3.w),d0    ; its cell
                move.w  d3,d1                   ; its entry, for widestart
                moveq   #0,d3
                btst    #5,d2
                bne.s   .placed
                move.b  FX_SHAPE(a3),d5         ; R of a running source of
                cmp.b   #2,d5                   ; several columns, or of a
                bcs.s   .header                 ; counted one, in the record;
                cmp.b   #5,d5                   ; of any other, in the header
                bhi.s   .header                 ; before its first row
                move.l  FX_ROWS(a3),d3
                bra.s   .last
.header:        movea.l FX_FIRST(a3),a5
                move.l  DT_ROWS-DT_PAYLOAD(a5),d3
.last:          move.l  DT_ROWS-DT_PAYLOAD(a4),d4
                bsr.w   ymxr_rem
.placed:        move.l  d3,FX_APLACE(a3)
; The bank the vector does not point into.
                moveq   #0,d4
                movea.w (a6),a5
                movea.l (a5),a5                 ; the running handler
                lea     ymxr_bank1(pc),a2
                cmpa.l  a2,a5
                bcc.s   .bank
                move.w  #BANK,d4
.bank:          move.w  d4,FX_ABANK(a3)
                tst.l   d0
                bmi.s   .shaped
; the general handler: the target, the place and the loop cell
                move.w  d6,d5
                mulu    #TICK_SIZE,d5
                lea     ymxr_tick0(pc),a5
                adda.w  d5,a5
                adda.w  d4,a5
                move.b  FX_TARGET(a3),TICK_SEL(a5)
                add.l   a4,d3                   ; the row the place is at
                .if     YMXR_PCREL
                lea     TICK_PTR(a5),a2
                sub.l   a2,d3
                move.w  d3,TICK_PTR(a5)
                tst.l   d0                      ; 0: the source plays once
                beq.s   .once
                sub.l   a2,d0
.once:          move.w  d0,TICK_CELL(a5)
                .else
                move.l  d3,TICK_PTR(a5)
                move.l  d0,TICK_CELL(a5)
                .endif
                clr.b   FX_SHAPE+ARMOFF(a3)
                bra.s   .single
.shaped:        addq.l  #1,d0
                bne.s   .notsq
; a square: its two rows, and the row the place is at
                move.w  d6,d5
                mulu    #SQ_SIZE,d5
                lea     ymxr_sq0(pc),a5
                adda.w  d5,a5
                adda.w  d4,a5
                move.b  FX_TARGET(a3),SQ_SEL(a5)
                move.b  (a4),d5
                move.b  1(a4),d0
                eor.b   d0,d5
                move.b  d5,SQ_MASK(a5)
                move.b  (a4,d3.w),SQ_VAL(a5)
                st      FX_SHAPE+ARMOFF(a3)
                .if     YMXR_MAP
                move.b  (a4),FX_ALEVEL(a3)      ; row 0's level, which the
                .endif                          ; switch makes the record's
                bra.s   .single
.notsq:         addq.l  #1,d0
                bne.s   .wide
; a source of one row
                move.w  d6,d5
                mulu    #ONE_SIZE,d5
                lea     ymxr_one0(pc),a5
                adda.w  d5,a5
                adda.w  d4,a5
                move.b  FX_TARGET(a3),ONE_SEL(a5)
                move.b  (a4),ONE_VAL(a5)
                move.b  #1,FX_SHAPE+ARMOFF(a3)
.single:        move.l  a4,FX_FIRST+ARMOFF(a3)
                move.l  DT_ROWS-DT_PAYLOAD(a4),FX_ROWS+ARMOFF(a3)
                bra.s   .armed
; a source of several columns, or counted: widestart into the mirror, at
; the bank and the row above
.wide:          subq.l  #1,d0                   ; the tag as a start passes it
                move.w  d6,d4
                st      WS_ARMING(a0)
                bsr.w   ymxr_widestart
                sf      WS_ARMING(a0)
                lea     -ARMOFF(a3),a3          ; the record, past its mirror
; the installer: the count, the handler, the select and the timer reset
.armed:         move.l  a5,FX_ARMED(a3)
                lea     ymxr_timers(pc),a4
                adda.w  4(a6),a4                ; the effect's installer
                move.b  d7,INST_CNT(a4)
                move.l  a5,INST_VEC(a4)
                move.b  FX_SELECT(a3),d5
                move.b  7(a6),d0
                and.b   #$7F,d0
                lsl.b   d0,d5                   ; at the timer's bits
                tst.b   7(a6)
                bmi.s   .shares
                move.b  d5,INST_SEL(a4)
                bra.s   .sel
.shares:        move.b  d5,INSTS_SEL(a4)
.sel:           move.w  #INST_SKIPW,INST_SKIP(a4)
                btst    #6,d2
                beq.s   .reset
                move.w  #$4E71,INST_SKIP(a4)    ; a nop: the stop runs
.reset:         tst.b   FX_PENDING(a3)
                bne.w   .hooked                 ; replaced: the hook stays
; the hook: the word at the head of the running handler's path that ends
; its source, by the shape it runs, and the branch to its stub
                movea.w (a6),a5
                movea.l (a5),a5                 ; the running handler
                move.b  FX_SHAPE(a3),d5
                bne.s   .h1
                lea     TICK_LAST(a5),a2
                move.w  #TICK_HOOKW,d4
                bra.s   .hook
.h1:            bpl.s   .h2
                move.b  SQ_MASK(a5),SQ_HMASK(a5)    ; the square's other row
                lea     SQ_LAST(a5),a2
                move.w  #SQ_HOOKW,d4
                bra.s   .hook
.h2:            cmp.b   #1,d5
                bne.s   .h3
                lea     ONE_LAST(a5),a2
                move.w  #ONE_HOOKW,d4
                bra.s   .hook
.h3:            cmp.b   #2,d5
                bne.s   .h4
                lea     TW2_LAST(a5),a2
                move.w  #TW2_HOOKW,d4
                bra.s   .hook
.h4:            cmp.b   #3,d5
                bne.s   .h5
                lea     TW3_LAST(a5),a2
                move.w  #TW3_HOOKW,d4
                bra.s   .hook
.h5:            cmp.b   #4,d5
                bne.s   .h6
                lea     TICKC_LAST(a5),a2
                move.w  #TICKC_HOOKW,d4
                bra.s   .hook
.h6:            lea     TWC_LAST(a5),a2
                move.w  #TWC_HOOKW,d4
.hook:          move.w  (a2),FX_HOOKW(a3)       ; the word as it is, written
                move.w  (a2),INST_BACKW(a4)     ; back at the switch or the
                move.l  a2,INST_BACKA(a4)       ; drop
                move.l  a2,FX_HOOK(a3)
                ori.w   #$0700,sr               ; the timer still running, and
                movea.w 2(a6),a5                ; the hook in place: one test
                move.b  (a5),d5                 ; and one word against a tick
                and.b   6(a6),d5
                beq.s   .stopped
                move.w  d4,(a2)
                st      FX_PENDING(a3)
.hooked:        move.w  (sp)+,sr
                movem.l (sp)+,d3/d6-d7/a6
                lea     YM_SELECT.w,a2
                moveq   #0,d0
                rts
.stopped:       move.w  (sp)+,sr
                movem.l (sp)+,d3/d6-d7/a6
                move.w  d3,d1                   ; the source
                lea     YM_SELECT.w,a2
                moveq   #1,d0
                rts
                .endif

; Each effect's timer, as a start reads it: its vector, by which a start
; finds the running handler, and where deferred starts are assembled in,
; its control register, its installer from this table, the select bits of
; that register, and the shift of the select into them, bit 7 set where
; two timers share the register.
                .macro  TIMERAT vector,ctrl,inst,mask,shift
                .if     YMXR_DEFER
                dc.w    \vector,\ctrl,\inst-ymxr_timers
                .else
                dc.w    \vector,\ctrl,0
                .endif
                dc.b    \mask,\shift
                .endm
ymxr_timers:    TIMERAT $134,$FA19,ymxr_inst0,$07,$00
                TIMERAT $110,$FA1D,ymxr_inst1,$07,$80
                TIMERAT $120,$FA1B,ymxr_inst2,$07,$00
                TIMERAT $114,$FA1D,ymxr_inst3,$70,$84

; The targets that write several registers (SPEC.md 2.1): a register and
; the column that writes it, one slot a column and eight bytes a target,
; from target 14. The column the marker is in comes last, so the move
; that writes it leaves the marker in N and the handler tests it there.
ymxr_wide:      dc.b    0,0,  1,1,  0,0,  0,0   ; 14 setToneA:     R0 R1
                dc.b    2,0,  3,1,  0,0,  0,0   ; 15 setToneB:     R2 R3
                dc.b    4,0,  5,1,  0,0,  0,0   ; 16 setToneC:     R4 R5
                dc.b    0,0,  8,2,  1,1,  0,0   ; 17 setVoiceA:    R0 R1 R8
                dc.b    2,0,  9,2,  3,1,  0,0   ; 18 setVoiceB:    R2 R3 R9
                dc.b    4,0, 10,2,  5,1,  0,0   ; 19 setVoiceC:    R4 R5 R10
                dc.b   11,0, 12,1,  0,0,  0,0   ; 20 setEnvelope:  R11 R12
                dc.b   11,0, 12,1, 13,2,  0,0   ; 21 setBuzzer:    R11 R12 R13
                dc.b    8,1,  6,0,  0,0,  0,0   ; 22 setNoiseA:    R6 R8
                dc.b    9,1,  6,0,  0,0,  0,0   ; 23 setNoiseB:    R6 R9
                dc.b   10,1,  6,0,  0,0,  0,0   ; 24 setNoiseC:    R6 R10

; A parked place: a marker with no loop cell, so a tick of a timer no row
; has started writes $80 to R0 and stops its timer (SPEC.md 5.2.1).
ymxr_park:      dc.b    $80
                .even

                .if     YMXR_MAP
; ---------------------------------------------------------------- lookup

; A start through a transformation (SPEC.md 1.10.2), which every effect
; reaches: FX_RUN the transformation, 1 to 127, d3 the source, d2 the
; row's control byte, d4 the effect. A source that steps a place runs the
; lookup instance of its shape in bank 0, whose page is filled first with
; interrupts on (ymxr_page); a square and a source of one row run their
; handler, with entries in place of the keys in its immediates. The
; handler's operands and the vector are written behind level 7, as a
; start through the identity writes them, and the place is the row the
; running handler is at modulo R where the row leaves bit 5 clear, read
; behind level 7 too, since a tick between the read and the write steps
; it (1.8.4). d0, d1, d3 to d5 and a3 to a5 are clobbered, and a2 is the
; chip's select register on return.
ymxr_lstart:    movem.l d2/d6-d7/a1/a6,-(sp)
                move.w  d4,d6                   ; the effect
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; its record
                move.w  d3,d1
                lsl.w   #3,d1                   ; the source's entry
                movea.l WS_SOURCES-8(a0,d1.w),a4    ; its first row
                move.l  WS_SOURCES-4(a0,d1.w),d7    ; its cell
                moveq   #0,d0                   ; the general handler's kind,
                movea.l d7,a6                   ; and its loop cell
                tst.l   d7
                bpl.s   .steps
                addq.l  #1,d7
                beq     .square
                addq.l  #1,d7
                beq     .one
                lea     WS_WIDE(a0),a2          ; a source of several columns,
                movea.l -8(a2,d1.w),a6          ; or counted: its loop cell
                move.w  -4(a2,d1.w),d1          ; and the stride from one
                moveq   #2,d0                   ; column to the next
                addq.l  #1,d7
                beq.s   .two
                moveq   #4,d0
                addq.l  #1,d7
                beq.s   .three
                moveq   #1,d0                   ; counted, of one column
                addq.l  #1,d7
                beq.s   .steps
                moveq   #6,d0                   ; counted, of two
.two:           cmp.b   #22,FX_TARGET(a3)       ; setNoiseX maps its first
                bcs.s   .steps                  ; write, the volume, marked
                addq.w  #1,d0                   ; or counted
                bra.s   .steps
.three:         cmp.b   #21,FX_TARGET(a3)       ; setVoiceX maps its middle
                beq.s   .steps                  ; column, the volume
                moveq   #5,d0
; The page, the instance and its page byte, then a register, a place and a
; loop cell a column, as a start through the identity writes them.
.steps:         move.w  d0,d7                   ; the kind
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2                   ; its descriptor
                move.b  KD_FILL(a2),d5
                cmp.w   #1,d7                   ; a counted source on setR7
                bne.s   .kind                   ; writes the ports' bits
                cmp.b   #7,FX_TARGET(a3)
                bne.s   .kind
                moveq   #2,d5
.kind:          moveq   #0,d4                   ; bank 0
                bsr     ymxr_page               ; d0 the page
                move.b  d0,FX_USE(a3)
                move.l  DT_ROWS-DT_PAYLOAD(a4),FX_ROWS(a3)
                move.w  d6,d4
                lsl.w   #3,d4                   ; eight kinds an effect
                add.w   d7,d4
                lea     ymxr_htab(pc),a5
                add.b   (a5,d4.w),d0            ; the page byte
                move.w  KD_STEP(a2),d4
                mulu    d6,d4
                add.w   KD_BASE(a2),d4
                lea     ymxr_tick0(pc),a5
                adda.w  d4,a5                   ; the instance
                lea     ymxr_timers(pc),a1
                lsl.w   #3,d6
                move.w  (a1,d6.w),d6            ; the effect's vector, and
                swap    d6                      ; the page byte below it
                move.b  d0,d6
                lea     FX_TARGET(a3),a1        ; one column: the target's
                moveq   #0,d0                   ; register, at the offset 0
                tst.w   KD_COLS(a2)
                bne.s   .map
                moveq   #0,d1
                bra.s   .write
.map:           move.b  FX_TARGET(a3),d0        ; several: the map's row of
                sub.w   #14,d0                  ; the target, eight bytes
                lsl.w   #3,d0                   ; from target 14 (SPEC.md
                lea     ymxr_wide(pc),a1        ; 2.1)
                adda.w  d0,a1
.write:         move.w  KD_CELLS(a2),d7
                movem.l a2-a3,-(sp)
                move.w  sr,-(sp)
                ori.w   #$0700,sr
ymxr_lwindow    equ     *                       ; inside the window: the rig's
                                                ; boundary (inside)
                moveq   #0,d3                   ; the place: row 0, or the
                btst    #5,d2                   ; row the running handler is
                bne.s   .first                  ; at modulo R of this source,
                swap    d6                      ; out of the record before
                movea.w d6,a2                   ; the columns below write it
                swap    d6
                bsr     ymxr_runrow
                move.l  DT_ROWS-DT_PAYLOAD(a4),d4
                bsr     ymxr_rem
                movea.l 2(sp),a2                ; the kind, under the level
.first:         move.w  KD_COLS(a2),d4          ; pushed
.col:           move.w  KD_SEL(a2),d2
                move.b  (a1)+,(a5,d2.w)         ; the register
                moveq   #0,d0
                move.w  KD_PTR(a2),d0
                add.l   a5,d0                   ; the column's read
                moveq   #0,d5
                move.b  (a1)+,d5
                mulu    d1,d5                   ; the column it writes
                add.l   a4,d5                   ; at row 0, kept for the
                move.l  d5,FX_FIRST(a3)         ; place's reset
                move.l  a6,d2                   ; the column's loop cell, or
                beq.s   .once                   ; 0 where the source plays once
                sub.l   a4,d2
                add.l   d5,d2
                .if     YMXR_PCREL
                sub.l   d0,d2                   ; a displacement from the read
.once:          move.w  d2,(a5,d7.w)
                .else
.once:          move.l  d2,(a5,d7.w)
                .endif
                add.l   d3,d5                   ; the row the place is at
                move.w  KD_PTR(a2),d2
                .if     YMXR_PCREL
                sub.l   d0,d5
                move.w  d5,(a5,d2.w)
                .else
                move.l  d5,(a5,d2.w)
                .endif
                addq.l  #4,a2                   ; the next column
                addq.l  #4,a3
                addq.w  #CELL_SIZE,d7
                dbra    d4,.col
                movem.l 2(sp),a2-a3             ; under the level pushed
                move.w  KD_LEFT(a2),d2          ; a counted source: the rows
                beq.s   .page                   ; from the place on, and the
                move.l  FX_ROWS(a3),d5          ; rows of a pass through the
                move.w  d5,d0                   ; loop, R less RR, or R where
                sub.w   d3,d0                   ; it plays once
                move.w  d0,(a5,d2.w)
                move.l  a6,d0
                beq.s   .pass
                sub.l   a4,d0
                sub.w   d0,d5
.pass:          move.w  KD_RELD(a2),d2
                move.w  d5,(a5,d2.w)
.page:          move.w  KD_PAGE(a2),d2
                move.b  d6,(a5,d2.w)            ; the page byte
                swap    d6
                movea.w d6,a1
                move.l  a5,(a1)                 ; the vector
                move.w  (sp)+,sr
                addq.l  #8,sp
                move.b  KD_SHAPE(a2),FX_SHAPE(a3)
                bra     .done
; A square: the entries of its two keys, the second the marker's.
.square:        moveq   #0,d0
                move.b  FX_RUN(a3),d0
                bsr     ymxr_tab
                move.b  (a4),d0
                bsr     ymxr_entry
                move.b  d0,d1                   ; row 0's level
                move.b  1(a4),d0
                bsr     ymxr_entry              ; row 1's
                move.b  d1,FX_LEVEL(a3)
                move.b  d1,d4
                eor.b   d0,d4                   ; the difference of the two
                move.w  d6,d5
                mulu    #SQ_SIZE,d5
                lea     ymxr_sq0(pc),a5
                adda.w  d5,a5
                lea     ymxr_timers(pc),a1
                lsl.w   #3,d6
                movea.w (a1,d6.w),a1            ; the vector
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                btst    #5,d2                   ; row 0, or the row of the
                bne.s   .row0                   ; two the running handler is
                movea.l a1,a2                   ; at, its row number modulo 2
                bsr     ymxr_runrow             ; (1.8.4)
                btst    #0,d3
                beq.s   .row0
                move.b  d0,d1
.row0:          move.b  FX_TARGET(a3),SQ_SEL(a5)
                move.b  d4,SQ_MASK(a5)
                move.b  d1,SQ_VAL(a5)
                move.l  a5,(a1)
                move.w  (sp)+,sr
                move.l  a4,FX_FIRST(a3)
                st      FX_SHAPE(a3)
                bra.s   .done
; A source of one row: its entry.
.one:           moveq   #0,d0
                move.b  FX_RUN(a3),d0
                bsr     ymxr_tab
                move.b  (a4),d0
                bsr     ymxr_entry
                move.w  d6,d1
                mulu    #ONE_SIZE,d1
                lea     ymxr_one0(pc),a5
                adda.w  d1,a5
                lea     ymxr_timers(pc),a1
                lsl.w   #3,d6
                movea.w (a1,d6.w),a1
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  FX_TARGET(a3),ONE_SEL(a5)
                move.b  d0,ONE_VAL(a5)
                move.l  a5,(a1)
                move.w  (sp)+,sr
                move.l  a4,FX_FIRST(a3)
                move.b  #1,FX_SHAPE(a3)
.done:          movem.l (sp)+,d2/d6-d7/a1/a6
                lea     YM_SELECT.w,a2
                rts

; A remap (SPEC.md 1.10.2): d1 the mapping a row sets with the source
; column unset, 0 to 127, d4 the effect, d2 the row's control byte, and
; d3 the timer's control register before the reset where the row resets
; the timer (_rmk). The kept mapping is set; on a timer running at the
; row the running source plays through the mapping from its next tick,
; its place and its rate as they are, and on a stopped timer the next
; start keeps it. A square and a source of one row have their entries
; rewritten. A source that steps a place runs the lookup instance
; of its shape in the bank of the running handler: between two
; transformations the instance's page byte moves to the other page, which
; has the new one (ymxr_page); between the identity and a transformation
; the source moves to the other handler (ymxr_move). d0, d1, d3 to d5
; and a3 to a5 are clobbered, and a2 is the chip's select register on
; return.
ymxr_remap:     movem.l d2/d6-d7/a1/a6,-(sp)
                move.w  d4,d6                   ; the effect
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; its record
                move.b  d1,FX_MAP(a3)
                lea     ymxr_timers(pc),a6
                move.w  d6,d0
                lsl.w   #3,d0
                adda.w  d0,a6                   ; its timer
                movea.w 2(a6),a4                ; the select bits of its
                move.b  (a4),d5                 ; control register: 0 on a
                tst.b   d2                      ; stopped timer, and as they
                bpl.s   .sel                    ; were before the reset where
                btst    #6,d2                   ; the row resets the timer
                beq.s   .sel
                move.b  d3,d5
.sel:           and.b   6(a6),d5
                beq     .done
                move.b  FX_RUN(a3),d7           ; the mapping it runs
                cmp.b   d7,d1
                beq     .done
                move.b  d1,FX_RUN(a3)
                movea.w (a6),a6                 ; the vector
                movea.l (a6),a5                 ; the running handler
                moveq   #0,d4                   ; and its bank
                .if     YMXR_DEFER
                lea     ymxr_bank1(pc),a4
                cmpa.l  a4,a5
                bcs.s   .bank
                moveq   #1,d4
.bank:
                .endif
                move.b  FX_SHAPE(a3),d0
                bmi     .square
                cmp.b   #1,d0
                beq     .one
                bsr     ymxr_kindof
                move.w  d0,d2                   ; the lookup kind
                move.w  d1,d3                   ; the identity's
                tst.b   FX_RUN(a3)
                beq.s   .toid
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2
                move.b  KD_FILL(a2),d5
                cmp.w   #1,d2                   ; a counted source on setR7
                bne.s   .kind                   ; writes the ports' bits: R7
                cmp.b   #7,TICK_SEL(a5)         ; in the running handler, as
                bne.s   .kind                   ; ymxr_kindof reads it
                moveq   #2,d5
.kind:          bsr     ymxr_page               ; d0 the page
                move.b  d0,FX_USE(a3,d4.w)
                move.w  d4,d1                   ; its page byte: bank, effect,
                lsl.w   #5,d1                   ; then kind
                move.w  d6,d5
                lsl.w   #3,d5
                add.w   d5,d1
                add.w   d2,d1
                lea     ymxr_htab(pc),a4
                add.b   (a4,d1.w),d0
                move.w  KD_PAGE(a2),d1
                tst.b   d7
                beq.s   .tolook
ymxr_rpage      equ     *                       ; the rig's boundary (inside)
                move.b  d0,(a5,d1.w)            ; one write, which no tick
                bra     .done                   ; splits
.tolook:        bsr     ymxr_at                 ; the instance, its page byte
                move.b  d0,(a4,d1.w)            ; written before the vector
                movea.l a2,a1                   ; names it
                move.w  d3,d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2                   ; from the identity's handler
                bsr     ymxr_move
                bra     .done
.toid:          move.w  d3,d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2
                bsr     ymxr_at                 ; the identity's handler
                movea.l a2,a1
                move.w  d2,d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2                   ; from the lookup instance
                bsr     ymxr_move
                bra     .done
; A square: both levels, and the one its immediate is at by bit 7.
.square:        movea.l FX_FIRST(a3),a4
                move.b  (a4),d1
                move.b  1(a4),d3
                moveq   #0,d0
                move.b  FX_RUN(a3),d0
                beq.s   .levels
                bsr     ymxr_tab
                move.b  d1,d0
                bsr     ymxr_entry
                move.b  d0,d1                   ; row 0's level
                move.b  d3,d0
                bsr     ymxr_entry
                move.b  d0,d3                   ; row 1's
.levels:        move.b  d1,FX_LEVEL(a3)
                move.b  d1,d4
                eor.b   d3,d4
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  d4,SQ_MASK(a5)
ymxr_rlevel     equ     *                       ; between the mask and the
                tst.b   SQ_VAL(a5)              ; level: the rig's boundary
                bpl.s   .row0                   ; (inside)
                move.b  d3,d1
.row0:          move.b  d1,SQ_VAL(a5)
                move.w  (sp)+,sr
                bra.s   .done
; A source of one row: its entry, one write.
.one:           movea.l FX_FIRST(a3),a4
                move.b  (a4),d1
                moveq   #0,d0
                move.b  FX_RUN(a3),d0
                beq.s   .put
                bsr     ymxr_tab
                move.b  d1,d0
                bsr     ymxr_entry
                move.b  d0,d1
.put:           move.b  d1,ONE_VAL(a5)
.done:          movem.l (sp)+,d2/d6-d7/a1/a6
                lea     YM_SELECT.w,a2
                rts

; The running source moved from the handler a5, which a2 describes, to
; the handler a4 of the same shape, which a1 describes: each column's
; register and loop cell and the rows of a pass with interrupts on, since
; no tick reads a handler the vector does not name; then behind level 7
; each column's place, the rows left and the vector a6, so the next tick
; reads the row the last one left. Under YMXR_PCREL a place and a loop
; cell are displacements from the column's read, moved by the distance
; between the two reads. d0 to d5 and d7 clobbered.
ymxr_move:      move.w  KD_COLS(a2),d4
                moveq   #0,d3                   ; four bytes a column
                moveq   #0,d7                   ; CELL_SIZE a column
.pre:           move.w  KD_SEL(a2,d3.w),d0
                move.w  KD_SEL(a1,d3.w),d1
                move.b  (a5,d0.w),(a4,d1.w)     ; the register
                move.w  KD_CELLS(a2),d0
                add.w   d7,d0
                move.w  KD_CELLS(a1),d1
                add.w   d7,d1
                .if     YMXR_PCREL
                move.w  (a5,d0.w),d0            ; the loop cell, 0 where the
                beq.s   .once                   ; source plays once
                bsr.s   .moved
.once:          move.w  d0,(a4,d1.w)
                .else
                move.l  (a5,d0.w),(a4,d1.w)
                .endif
                addq.w  #4,d3
                addq.w  #CELL_SIZE,d7
                dbra    d4,.pre
                move.w  KD_RELD(a2),d0          ; the rows of a pass
                beq.s   .level
                move.w  KD_RELD(a1),d1
                move.w  (a5,d0.w),(a4,d1.w)
.level:         move.w  KD_COLS(a2),d4
                moveq   #0,d3
                move.w  sr,-(sp)
                ori.w   #$0700,sr
.place:         move.w  KD_PTR(a2,d3.w),d0
                move.w  KD_PTR(a1,d3.w),d1
                .if     YMXR_PCREL
                move.w  (a5,d0.w),d0
                bsr.s   .moved
                move.w  d0,(a4,d1.w)
                .else
                move.l  (a5,d0.w),(a4,d1.w)
                .endif
                addq.w  #4,d3
                dbra    d4,.place
                move.w  KD_LEFT(a2),d0          ; the rows left
                beq.s   .aim
                move.w  KD_LEFT(a1),d1
                move.w  (a5,d0.w),(a4,d1.w)
.aim:           move.l  a4,(a6)
                move.w  (sp)+,sr
                rts
                .if     YMXR_PCREL
; d0 a displacement from column d3's read in a5, as one from its read in a4.
.moved:         ext.l   d0
                add.l   a5,d0
                move.w  KD_PTR(a2,d3.w),d2
                ext.l   d2
                add.l   d2,d0                   ; the row's address
                sub.l   a4,d0
                move.w  KD_PTR(a1,d3.w),d2
                ext.l   d2
                sub.l   d2,d0
                rts
                .endif

; The page of effect d6 in bank d4 that has FX_RUN's transformation in the
; fill kind d5, a3 the record: the page in use where it has it, else the
; other, filled where it has another. A page the running instance does not
; read is filled with interrupts on. d0 the page, 0 or 1; the other
; registers are kept.
ymxr_page:      movem.l d1-d5/a2/a4-a5,-(sp)
                move.b  FX_RUN(a3),d1
                move.w  d4,d0
                add.w   d0,d0
                add.w   d0,d0
                lea     FX_PAGE(a3,d0.w),a4     ; the bank's two pages
                moveq   #0,d0
                move.b  FX_USE(a3,d4.w),d0
                add.w   d0,d0                   ; two bytes a page
                cmp.b   (a4,d0.w),d1
                bne.s   .other
                cmp.b   1(a4,d0.w),d5
                beq.s   .found
.other:         eori.w  #2,d0
                cmp.b   (a4,d0.w),d1
                bne.s   .fill
                cmp.b   1(a4,d0.w),d5
                beq.s   .found
.fill:          move.b  d1,(a4,d0.w)
                move.b  d5,1(a4,d0.w)
                move.w  d6,d2                   ; the page: the effect's
                add.w   d2,d2                   ; pages, 512 bytes a bank,
                lea     ymxr_pgat(pc),a4        ; 256 a page
                move.w  (a4,d2.w),d2
                lea     ymxr_pg0(pc),a4
                adda.w  d2,a4
                move.w  d4,d2
                lsl.w   #8,d2
                add.w   d2,d2
                adda.w  d2,a4
                move.w  d0,d2
                lsl.w   #7,d2
                adda.w  d2,a4
                move.w  d0,-(sp)
                moveq   #0,d0
                move.b  d1,d0
                bsr.s   ymxr_tab
                bsr.s   ymxr_fill
                move.w  (sp)+,d0
.found:         lsr.w   #1,d0
                movem.l (sp)+,d1-d5/a2/a4-a5
                rts

; a2 transformation d0, 1 to 127: its E at 0 and entry k at 2 + k (SPEC.md
; 3.4.2). d0 clobbered.
ymxr_tab:       movea.l WS_TRANS(a0),a2
                add.w   d0,d0
                add.w   d0,d0
                adda.l  -2(a2,d0.w),a2
                rts

; d0 the entry of the key d0 of a marked column in the transformation a2:
; entry k of the key's bits 6 to 0, with the key's bit 7, the marker. d5
; scratch.
ymxr_entry:     moveq   #$7F,d5
                and.b   d0,d5
                and.b   #$80,d0
                or.b    2(a2,d5.w),d0
                rts

; The page a4 filled with the transformation a2 in the fill kind d5
; (SPEC.md 1.10.1, 3.2): plain, the E entries as longs, eight at a time
; through movem; the mirror of a marked column, entries 0 to E' - 1 and
; again at 128 with bit 7 set, E' the lesser of E and 128; the ports of
; setR7, entries 0 to 63 at 192 with bits 7 and 6 set, the rows of a
; counted source on it having both (SPEC.md rule 2(f)). A page has room
; for the 3 entries past E a long reads. d0, d1, a2 and a4 clobbered.
ymxr_fill:      move.w  (a2)+,d0                ; E
                tst.b   d5
                beq.s   .plain
                cmp.b   #1,d5
                beq.s   .mirror
                cmp.w   #64,d0
                bls.s   .ports
                moveq   #64,d0
.ports:         lea     192(a4),a4
                addq.w  #3,d0
                lsr.w   #2,d0
                bra.s   .pnext
.port:          move.l  (a2)+,d1
                or.l    #$C0C0C0C0,d1
                move.l  d1,(a4)+
.pnext:         dbra    d0,.port
                rts
.mirror:        cmp.w   #128,d0
                bls.s   .half
                move.w  #128,d0
.half:          addq.w  #3,d0
                lsr.w   #2,d0
                bra.s   .mnext
.mir:           move.l  (a2)+,d1
                move.l  d1,(a4)+
                or.l    #$80808080,d1
                move.l  d1,124(a4)
.mnext:         dbra    d0,.mir
                rts
.plain:         addq.w  #3,d0
                lsr.w   #2,d0                   ; E in longs
                movem.l d2-d7/a3,-(sp)
                subq.w  #8,d0
                bcs.s   .rest
.eight:         movem.l (a2)+,d1-d7/a3
                movem.l d1-d7/a3,(a4)
                lea     32(a4),a4
                subq.w  #8,d0
                bcc.s   .eight
.rest:          addq.w  #8,d0
                bra.s   .lnext
.long:          move.l  (a2)+,(a4)+
.lnext:         dbra    d0,.long
                movem.l (sp)+,d2-d7/a3
                rts

; d3 the row the running handler is at, a2 the vector that names it and a3
; the effect's record: a square's by bit 7 of its immediate, a source of
; one row's 0, and every other's its place less its place at row 0, the
; place of every handler that steps one being at TICK_PTR. a2 and d5
; clobbered.
ymxr_runrow:    moveq   #0,d3
                movea.l (a2),a2                 ; the running handler
                move.b  FX_SHAPE(a3),d5
                beq.s   .steps
                bmi.s   .square
                cmp.b   #1,d5
                beq.s   .row
.steps:
                .if     YMXR_PCREL
                move.w  TICK_PTR(a2),d3
                ext.l   d3
                add.l   a2,d3
                add.l   #TICK_PTR,d3
                .else
                move.l  TICK_PTR(a2),d3
                .endif
                sub.l   FX_FIRST(a3),d3
                rts
.square:        tst.b   SQ_VAL(a2)
                bpl.s   .row
                moveq   #1,d3
.row:           rts

; d0 the lookup kind of the stepping shape the record a3 runs, its number
; in ymxr_kinds, and d1 the identity's, a5 the running handler: the
; column a target maps is its last value (SPEC.md 1.10.1), written first
; of two on setNoiseX, last of two on setToneX and setEnvelope, last of
; three on setBuzzer and the middle on setVoiceX, in a marked and in a
; counted source. The target is the running source's, and the kept
; target is the next start's (1.8.2), so the handler's first register, at
; TICK_SEL in every handler that steps a place, separates them: R8 to R10
; on setNoiseX against R0, R2 or R4 on setToneX and R11 on setEnvelope,
; and R11 on setBuzzer against R0, R2 or R4 on setVoiceX (2.1).
ymxr_kindof:    moveq   #0,d0
                move.b  FX_SHAPE(a3),d0
                moveq   #0,d1
                move.b  .ident(pc,d0.w),d1
                move.b  .look(pc,d0.w),d0
                cmp.b   #4,d0
                beq.s   .three
                cmp.b   #2,d0                   ; two columns, marked
                beq.s   .two
                cmp.b   #6,d0                   ; or counted
                bne.s   .kind
.two:           cmp.b   #8,TICK_SEL(a5)
                bcs.s   .kind
                cmp.b   #11,TICK_SEL(a5)
                beq.s   .kind
                addq.w  #1,d0                   ; R8 to R10: the lookup first
                rts
.three:         cmp.b   #11,TICK_SEL(a5)
                beq.s   .kind
                moveq   #5,d0
.kind:          rts
.look:          dc.b    0,0,2,4,1,6             ; by FX_SHAPE, 0 to 5
.ident:         dc.b    8,0,10,11,9,12

; a4 the handler of the kind a2 describes, of effect d6, in bank d4. d5
; clobbered.
ymxr_at:        move.w  KD_STEP(a2),d5
                mulu    d6,d5
                add.w   KD_BASE(a2),d5
                lea     ymxr_tick0(pc),a4
                adda.w  d5,a4
                .if     YMXR_DEFER
                tst.w   d4
                beq.s   .bank
                adda.w  #BANK,a4
.bank:
                .endif
                rts

; The kinds of handler that step a place: the eight lookup kinds in the
; order of the table of page bytes, then the identity's five.
ymxr_kinds:     KIND    ymxr_lk0,LOOK_STRIDE,1,LK_CELL,0,0,LK_PAGE,0,1,LK_SEL,LK_PTR,0,0,0,0
                KIND    ymxr_lc0,LOOK_STRIDE,1,LC_CELL,LC_LEFT,LC_RELD,LC_PAGE,4,0,LC_SEL,LC_PTR,0,0,0,0
                KIND    ymxr_lt0,LOOK_STRIDE,2,LT_CELLS,0,0,LT_PAGE,2,1,LT_SEL1,LT_PTR1,LT_SEL2,LT_PTR2,0,0
                KIND    ymxr_ln0,LOOK_STRIDE,2,LN_CELLS,0,0,LN_PAGE,2,0,LN_SEL1,LN_PTR1,LN_SEL2,LN_PTR2,0,0
                KIND    ymxr_lb0,LOOK_STRIDE,3,LB_CELLS,0,0,LB_PAGE,3,1,LB_SEL1,LB_PTR1,LB_SEL2,LB_PTR2,LB_SEL3,LB_PTR3
                KIND    ymxr_lv0,LOOK_STRIDE,3,LV_CELLS,0,0,LV_PAGE,3,0,LV_SEL1,LV_PTR1,LV_SEL2,LV_PTR2,LV_SEL3,LV_PTR3
                KIND    ymxr_le0,LOOK_STRIDE,2,LE_CELLS,LE_LEFT,LE_RELD,LE_PAGE,5,0,LE_SEL1,LE_PTR1,LE_SEL2,LE_PTR2,0,0
                KIND    ymxr_lf0,LOOK_STRIDE,2,LF_CELLS,LF_LEFT,LF_RELD,LF_PAGE,5,0,LF_SEL1,LF_PTR1,LF_SEL2,LF_PTR2,0,0
                KIND    ymxr_tick0,TICK_SIZE,1,TICK_CELL,0,0,0,0,0,TICK_SEL,TICK_PTR,0,0,0,0
                KIND    ymxr_cnt0,TICKC_SIZE,1,TICKC_CELL,TICKC_LEFT,TICKC_RELD,0,4,0,TICKC_SEL,TICKC_PTR,0,0,0,0
                KIND    ymxr_two0,TW2_SIZE,2,TW2_CELLS,0,0,0,2,0,TW2_SEL1,TW2_PTR1,TW2_SEL2,TW2_PTR2,0,0
                KIND    ymxr_three0,TW3_SIZE,3,TW3_CELLS,0,0,0,3,0,TW3_SEL1,TW3_PTR1,TW3_SEL2,TW3_PTR2,TW3_SEL3,TW3_PTR3
                KIND    ymxr_env0,TWC_SIZE,2,TWC_CELLS,TWC_LEFT,TWC_RELD,0,5,0,TWC_SEL1,TWC_PTR1,TWC_SEL2,TWC_PTR2,0,0

; Each effect's pages, from effect 0's.
ymxr_pgat:      dc.w    0,ymxr_pg1-ymxr_pg0,ymxr_pg2-ymxr_pg0,ymxr_pg3-ymxr_pg0
                .endif

                .if     YMXR_MAP
; The stop at the marker of the lookup kinds under the monitor, one an
; effect, whose count is the lookup's.
                .if     YMXR_PERF
                LHALT   ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5
                LHALT   ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4
                LHALT   ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0
                LHALT   ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5
                .endif

; The pages: two of 256 bytes an effect a bank, bank 1's 512 bytes
; after bank 0's, and effect e's on effect 0's plus e times LOOK_STRIDE
; modulo 256, the residue of its lookup instances' reads.
ymxr_pages:
ymxr_pg0:       ds.b    512
                .if     YMXR_DEFER
ymxr_pg0b:      ds.b    512
                .endif
; The page byte of each lookup instance for page 0, the high
; byte of its read's displacement: bank, then effect, then kind in
; the order of ymxr_kinds. Page 1's is this plus 1.
ymxr_htab:
                dc.b    (ymxr_pg0-(ymxr_lk0_key+2))/256,(ymxr_pg0-(ymxr_lc0_key+2))/256,(ymxr_pg0-(ymxr_lt0_key+2))/256,(ymxr_pg0-(ymxr_ln0_key+2))/256,(ymxr_pg0-(ymxr_lb0_key+2))/256,(ymxr_pg0-(ymxr_lv0_key+2))/256,(ymxr_pg0-(ymxr_le0_key+2))/256,(ymxr_pg0-(ymxr_lf0_key+2))/256
                dc.b    (ymxr_pg1-(ymxr_lk1_key+2))/256,(ymxr_pg1-(ymxr_lc1_key+2))/256,(ymxr_pg1-(ymxr_lt1_key+2))/256,(ymxr_pg1-(ymxr_ln1_key+2))/256,(ymxr_pg1-(ymxr_lb1_key+2))/256,(ymxr_pg1-(ymxr_lv1_key+2))/256,(ymxr_pg1-(ymxr_le1_key+2))/256,(ymxr_pg1-(ymxr_lf1_key+2))/256
                dc.b    (ymxr_pg2-(ymxr_lk2_key+2))/256,(ymxr_pg2-(ymxr_lc2_key+2))/256,(ymxr_pg2-(ymxr_lt2_key+2))/256,(ymxr_pg2-(ymxr_ln2_key+2))/256,(ymxr_pg2-(ymxr_lb2_key+2))/256,(ymxr_pg2-(ymxr_lv2_key+2))/256,(ymxr_pg2-(ymxr_le2_key+2))/256,(ymxr_pg2-(ymxr_lf2_key+2))/256
                dc.b    (ymxr_pg3-(ymxr_lk3_key+2))/256,(ymxr_pg3-(ymxr_lc3_key+2))/256,(ymxr_pg3-(ymxr_lt3_key+2))/256,(ymxr_pg3-(ymxr_ln3_key+2))/256,(ymxr_pg3-(ymxr_lb3_key+2))/256,(ymxr_pg3-(ymxr_lv3_key+2))/256,(ymxr_pg3-(ymxr_le3_key+2))/256,(ymxr_pg3-(ymxr_lf3_key+2))/256
                .if     YMXR_DEFER
                dc.b    (ymxr_pg0b-(ymxr_lk0b_key+2))/256,(ymxr_pg0b-(ymxr_lc0b_key+2))/256,(ymxr_pg0b-(ymxr_lt0b_key+2))/256,(ymxr_pg0b-(ymxr_ln0b_key+2))/256,(ymxr_pg0b-(ymxr_lb0b_key+2))/256,(ymxr_pg0b-(ymxr_lv0b_key+2))/256,(ymxr_pg0b-(ymxr_le0b_key+2))/256,(ymxr_pg0b-(ymxr_lf0b_key+2))/256
                dc.b    (ymxr_pg1b-(ymxr_lk1b_key+2))/256,(ymxr_pg1b-(ymxr_lc1b_key+2))/256,(ymxr_pg1b-(ymxr_lt1b_key+2))/256,(ymxr_pg1b-(ymxr_ln1b_key+2))/256,(ymxr_pg1b-(ymxr_lb1b_key+2))/256,(ymxr_pg1b-(ymxr_lv1b_key+2))/256,(ymxr_pg1b-(ymxr_le1b_key+2))/256,(ymxr_pg1b-(ymxr_lf1b_key+2))/256
                dc.b    (ymxr_pg2b-(ymxr_lk2b_key+2))/256,(ymxr_pg2b-(ymxr_lc2b_key+2))/256,(ymxr_pg2b-(ymxr_lt2b_key+2))/256,(ymxr_pg2b-(ymxr_ln2b_key+2))/256,(ymxr_pg2b-(ymxr_lb2b_key+2))/256,(ymxr_pg2b-(ymxr_lv2b_key+2))/256,(ymxr_pg2b-(ymxr_le2b_key+2))/256,(ymxr_pg2b-(ymxr_lf2b_key+2))/256
                dc.b    (ymxr_pg3b-(ymxr_lk3b_key+2))/256,(ymxr_pg3b-(ymxr_lc3b_key+2))/256,(ymxr_pg3b-(ymxr_lt3b_key+2))/256,(ymxr_pg3b-(ymxr_ln3b_key+2))/256,(ymxr_pg3b-(ymxr_lb3b_key+2))/256,(ymxr_pg3b-(ymxr_lv3b_key+2))/256,(ymxr_pg3b-(ymxr_le3b_key+2))/256,(ymxr_pg3b-(ymxr_lf3b_key+2))/256
                .endif
                ds.b    ((ymxr_pg0+(LOOK_STRIDE*1))-*)&255
ymxr_pg1:       ds.b    512
                .if     YMXR_DEFER
ymxr_pg1b:      ds.b    512
                .endif
                ds.b    ((ymxr_pg0+(LOOK_STRIDE*2))-*)&255
ymxr_pg2:       ds.b    512
                .if     YMXR_DEFER
ymxr_pg2b:      ds.b    512
                .endif
                ds.b    ((ymxr_pg0+(LOOK_STRIDE*3))-*)&255
ymxr_pg3:       ds.b    512
                .if     YMXR_DEFER
ymxr_pg3b:      ds.b    512
                .endif

                HANDLERS
                HANDLEQU
; The offsets of the eight lookup kinds, measured off effect 0's
; instances, and the assertions on every instance: its read on its pages'
; residue and within their reach, inside its bank, bank 1's BANK bytes
; from bank 0's, and each kind's four LOOK_STRIDE apart.
                LOOKAT1 LK,ymxr_lk0,0
                LOOKAT1 LC,ymxr_lc0,1
                LOOKATW LT,ymxr_lt0,0,0
                LOOKATW LN,ymxr_ln0,0,0
                LOOKATW LB,ymxr_lb0,1,0
                LOOKATW LV,ymxr_lv0,1,0
                LOOKATW LE,ymxr_le0,0,1
                LOOKATW LF,ymxr_lf0,0,1
                .if     (LOOK_STRIDE<>LB_SIZE)|(LOOK_STRIDE<>LV_SIZE)
                .error  "LOOK_STRIDE is apart from the size of the kinds of three columns"
                .endif
; The first register of each of the thirteen kinds is at TICK_SEL, where
; ymxr_kindof and ymxr_remap read the running source's.
                .if     (TICKC_SEL<>TICK_SEL)|(TW2_SEL1<>TICK_SEL)|(TW3_SEL1<>TICK_SEL)|(TWC_SEL1<>TICK_SEL)
                .error  "a handler of the identity has its first register at another offset than TICK_SEL"
                .endif
                .if     (LK_SEL<>TICK_SEL)|(LC_SEL<>TICK_SEL)|(LT_SEL1<>TICK_SEL)|(LN_SEL1<>TICK_SEL)
                .error  "a lookup instance has its first register at another offset than TICK_SEL"
                .endif
                .if     (LB_SEL1<>TICK_SEL)|(LV_SEL1<>TICK_SEL)|(LE_SEL1<>TICK_SEL)|(LF_SEL1<>TICK_SEL)
                .error  "a lookup instance has its first register at another offset than TICK_SEL"
                .endif
                .if     YMXR_DEFER
                .if     (BANK&255)<>0
                .error  "BANK is off a multiple of 256"
                .endif
                .endif
                LCHECK  ymxr_lk0,ymxr_pg0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lk1,ymxr_pg1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lk2,ymxr_pg2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lk3,ymxr_pg3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lc0,ymxr_pg0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lc1,ymxr_pg1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lc2,ymxr_pg2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lc3,ymxr_pg3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lt0,ymxr_pg0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lt1,ymxr_pg1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lt2,ymxr_pg2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lt3,ymxr_pg3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_ln0,ymxr_pg0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_ln1,ymxr_pg1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_ln2,ymxr_pg2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_ln3,ymxr_pg3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lb0,ymxr_pg0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lb1,ymxr_pg1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lb2,ymxr_pg2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lb3,ymxr_pg3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lv0,ymxr_pg0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lv1,ymxr_pg1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lv2,ymxr_pg2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lv3,ymxr_pg3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_le0,ymxr_pg0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_le1,ymxr_pg1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_le2,ymxr_pg2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_le3,ymxr_pg3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lf0,ymxr_pg0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lf1,ymxr_pg1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lf2,ymxr_pg2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lf3,ymxr_pg3,ymxr_tick0,ymxr_bank0end
                .if     YMXR_DEFER
                LCHECK  ymxr_lk0b,ymxr_pg0b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lk0
                LCHECK  ymxr_lk1b,ymxr_pg1b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lk1
                LCHECK  ymxr_lk2b,ymxr_pg2b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lk2
                LCHECK  ymxr_lk3b,ymxr_pg3b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lk3
                LCHECK  ymxr_lc0b,ymxr_pg0b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lc0
                LCHECK  ymxr_lc1b,ymxr_pg1b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lc1
                LCHECK  ymxr_lc2b,ymxr_pg2b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lc2
                LCHECK  ymxr_lc3b,ymxr_pg3b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lc3
                LCHECK  ymxr_lt0b,ymxr_pg0b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lt0
                LCHECK  ymxr_lt1b,ymxr_pg1b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lt1
                LCHECK  ymxr_lt2b,ymxr_pg2b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lt2
                LCHECK  ymxr_lt3b,ymxr_pg3b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lt3
                LCHECK  ymxr_ln0b,ymxr_pg0b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_ln0
                LCHECK  ymxr_ln1b,ymxr_pg1b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_ln1
                LCHECK  ymxr_ln2b,ymxr_pg2b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_ln2
                LCHECK  ymxr_ln3b,ymxr_pg3b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_ln3
                LCHECK  ymxr_lb0b,ymxr_pg0b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lb0
                LCHECK  ymxr_lb1b,ymxr_pg1b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lb1
                LCHECK  ymxr_lb2b,ymxr_pg2b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lb2
                LCHECK  ymxr_lb3b,ymxr_pg3b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lb3
                LCHECK  ymxr_lv0b,ymxr_pg0b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lv0
                LCHECK  ymxr_lv1b,ymxr_pg1b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lv1
                LCHECK  ymxr_lv2b,ymxr_pg2b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lv2
                LCHECK  ymxr_lv3b,ymxr_pg3b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lv3
                LCHECK  ymxr_le0b,ymxr_pg0b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_le0
                LCHECK  ymxr_le1b,ymxr_pg1b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_le1
                LCHECK  ymxr_le2b,ymxr_pg2b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_le2
                LCHECK  ymxr_le3b,ymxr_pg3b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_le3
                LCHECK  ymxr_lf0b,ymxr_pg0b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lf0
                LCHECK  ymxr_lf1b,ymxr_pg1b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lf1
                LCHECK  ymxr_lf2b,ymxr_pg2b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lf2
                LCHECK  ymxr_lf3b,ymxr_pg3b,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lf3
                .endif
                LSTRIDE ymxr_lk
                LSTRIDE ymxr_lc
                LSTRIDE ymxr_lt
                LSTRIDE ymxr_ln
                LSTRIDE ymxr_lb
                LSTRIDE ymxr_lv
                LSTRIDE ymxr_le
                LSTRIDE ymxr_lf
                .endif
