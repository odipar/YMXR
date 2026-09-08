; YMXR.S - the player: a tune's rows to the YM2149 and the MFP, one row a
; frame, and a source's rows to one register at a timer's rate.
;
; doc/SPEC.md is the contract: section 4 is YMXR_play and section 5 the
; tick handlers. What this player takes is the bound tune of
; doc/BINARIES.md: the tune file's tables, and no code, bound by a tool
; with the image DTX packaged the table with. The player calls that
; image's slots (DTX, doc/abi.md) and reads the row they give.
;
; The calls, by the jump table at the top of the binary:
;
;   YMXR_init   a0 the bound tune, on an even address; a1 the workspace,
;               on a long. d0 is 0, or -1 for one this player does not
;               read.
;   YMXR_play   a0 the workspace. One row. d0 is 0, or -1 where the tune
;               has played its last row and does not repeat: nothing is
;               written then.
;   YMXR_stop   a0 the workspace. The claimed timers stopped, disabled and
;               masked; the three volumes silenced.
;
; Every call clobbers d0-d5 and a0-a5 and leaves d6, d7 and a6 as they
; were. YMXR_play runs in supervisor mode, at any interrupt level: every
; write of a select and a value is one movep, which no tick lands inside.
;
; A call writes the row the call before it took, and takes the next when
; its writes are made: the row's writes then leave a host's clock at one
; offset, where an advance before them would move by what the refill of
; the row costs. Init takes the first row.
;
; The workspace is YMXR_FIXED bytes, then the state block the image's
; format block asks for, which the bound tune states at offset 12
; (BINARIES.md): a host allocates YMXR_FIXED plus that. The row a call
; writes is the image's own pointer, IM_POINTER of that state block
; (DTX, abi.md 3), which an advance leaves at column 0's value, so the
; player keeps no copy of it.
;
; The host owns the machine. This player saves and restores no vector, no
; timer control and no interrupt enable: around init and stop a host keeps
; what it needs of the vectors at $134, $120, $114 and $110, of TACR, TBCR
; and both nibbles of TCDCR, and of bits 5 and 0 of IERA and IMRA and bits
; 5 and 4 of IERB and IMRB. A timer an effect does not run is never
; touched. The MFP is taken in software end-of-interrupt mode, as TOS
; leaves it, and the vectors are the 68000's at zero.
;
; The player patches itself: the row's displacements and the branches
; over the effects the tune does not run at init, and the tick handlers'
; operands at every start. It runs from RAM, and one loaded copy serves
; one workspace.
;
; YMXR_PERF equ 1 assembles the raster monitor in: the play call paints
; the background red for as long as its work runs, one scanline to 512
; cycles, and each tick handler paints its own colour where the beam
; stands. A tick's band is a few hundred cycles wide and hard to read,
; so each handler adds a count of what its own instructions cost, which
; the next call burns off as a yellow bar after its own work, the
; register writes among them: the bar shows what the handlers cost the
; frame, and no chip write moves for the bar. What a tick costs beyond
; its handler, the 44 cycles the 68000 spends entering an interrupt and
; the 20 on the rte, stands where the tick landed and is not counted
; here. The call waits for the display to start before it paints, since
; the VBL fires far above the screen and an unsynced bar lands in the
; top border; the wait stands before the red mark, so it costs the
; figures nothing. Each count is an estimate, in turns of ten cycles,
; and the monitor is for reading a run: with the switch off, the
; default, no byte and no cycle of it stands.

YMXR_MAGIC      equ     $594D5842       ; YMXB, the bound tune
YMXR_VERSION    equ     $0001           ; its version
YMXR_COLUMNS    equ     30
YMXR_SITES      equ     22              ; the other read sites (ymxr_sites)

; The raster monitor, off unless the assembly defines it (rmac
; -dYMXR_PERF=1).
                .if     ^^defined YMXR_PERF
                .else
YMXR_PERF       equ     0
                .endif
; A tick drops the interrupt level once its writes are made, so a faster
; timer nests inside a slower one; and it clears its own in-service bit,
; the MFP being taken in software end-of-interrupt mode as TOS leaves it.
; A host that wants neither assembles the player with YMXR_NEST=0 and
; YMXR_AEOI=1 (rmac -dYMXR_NEST=0 -dYMXR_AEOI=1), which takes 32 cycles
; off every tick and asks two things of it: no MFP interrupt nests inside
; another, and the MFP's vector register is the player's to set, which it
; keeps over a call and puts back at stop. The two go together: automatic
; end-of-interrupt sets no in-service bit, so nothing but the level the
; tick holds keeps a lower timer out.
                .if     ^^defined YMXR_NEST
                .else
YMXR_NEST       equ     1
                .endif
                .if     ^^defined YMXR_AEOI
                .else
YMXR_AEOI       equ     0
                .endif
                .if     YMXR_AEOI&YMXR_NEST
                .error  "YMXR_AEOI=1 asks for YMXR_NEST=0"
                .endif
MFP_VECTOR      equ     $FFFFFA17       ; bits 7 to 4 the vector's high half,
                                        ; bit 3 the software end of interrupt

PERF_SYNC       equ     8000            ; the wait's ceiling, over a frame
                                        ; of turns, so a call from
                                        ; anywhere runs on
PERF_FRAME      equ     $700            ; the call's own work
PERF_BAR        equ     $770            ; the timers' burnt bar
PERF_COLOUR     equ     $FFFF8240       ; the background
PERF_BEAM       equ     $FFFF8209       ; the video address counter's low
                                        ; byte, which moves while the chip
                                        ; fetches pixels
; What a tick of each kind is counted at, in turns of ten cycles, beside
; what performance.md measures each at: a row written 108 cycles, the
; marker with the place to row RR 130, the marker with the timer stopped
; 116, and a square's own tick 88. Each count stands within a twentieth of its measurement, and
; PlayerTest holds the three to the document's figures.
PERF_ON         equ     11
PERF_SQ         equ     9
PERF_LOOP       equ     13
PERF_STOP       equ     12

; The bound tune (BINARIES.md).
TF_VERSION      equ     4
TF_RATE         equ     6
TF_EFFECTS      equ     8
TF_SOURCES      equ     9
TF_STATE        equ     12
TF_IMAGE        equ     16
TF_INDEX        equ     20

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
DT_REPEAT       equ     10
DT_PAYLOAD      equ     16

; The workspace.
WS_TUNE         equ     0               ; the bound tune
WS_IMAGE        equ     4               ; the image in it
WS_ROWS         equ     8               ; rows to the last, or to the wrap
WS_R            equ     12
WS_RR           equ     16
WS_EFFECTS      equ     20              ; a byte: the effects the tune runs
WS_VECTOR       equ     21              ; a byte: the MFP's vector register as
                                        ; init found it, kept where the player
                                        ; takes automatic end of interrupt
WS_FX           equ     24              ; four effects, FX_SIZE each
FX_TARGET       equ     0               ; the target the player holds
FX_SELECT       equ     1               ; the select it last took
FX_COUNT        equ     2               ; the count it last took
FX_SQUARE       equ     3               ; not 0 where the timer's vector is
                                        ; this effect's square handler, which
                                        ; holds the place as its own immediate
FX_FIRST        equ     4               ; the running source's first row
FX_SIZE         equ     8
WS_SOURCES      equ     56              ; a source's first row and its loop
                                        ; cell, eight bytes each, resolved at
                                        ; init: the source column's seven bits
                                        ; reach 127, so the room stands whether
                                        ; the tune names that many or one
YMXR_FIXED      equ     1072
WS_STATE        equ     1072            ; the image's state block, on a long

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

; A tick handler's operands, patched at a start, stand at fixed offsets
; into it; the equates are below the handlers, since the raster monitor's
; marks stand between the three blocks a handler is made of and the
; assembler measures the distances.

; ---------------------------------------------------------------- macros

; A column into d1, or into d2. The displacement is i strides, patched at
; init: rmac would fold a zero away, so a placeholder stands here.
                .macro  READ    col
c\{col}:
                move.b  $7FFF(a1),d1
                .endm

                .macro  READ2   col
c\{col}:
                move.b  $7FFF(a1),d2
                .endm

; A second read of a column, or a column ored into d1, at a site of its
; own that init patches from the table of sites (ymxr_sites).
                .macro  QREAD   col
q\{col}:
                move.b  $7FFF(a1),d1
                .endm

; The same, at the third site a column takes: the copy of steps 6 and 7
; the row that sets no envelope column falls into.
                .macro  RREAD   col
r\{col}:
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
; 0: written where it is not 0, and nothing else to test.
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

; A tone period: the coarse column into d2, for its bit 6 and its own
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
                .macro  EFFECT  fx,ct,cs,cc,cn,ctrl,data,ier,bit,keep,shift,shared,tick,sq,vector
; The head is the effect's first read where the tune runs the effect, and
; a branch over its columns where it does not: init writes the one word
; that tells them apart, and the word after it holds the read's
; displacement or the branch's, whichever the head takes.
\{tick}_over:
q\{ct}:         bra.w   \{tick}_done
                QOR     \cs                     ; the three set bits at once
                QOR     \cc
                bmi.s   \{tick}_some
                QREAD   \cn                     ; the count alone, or nothing
                beq.w   \{tick}_done
                move.b  d1,\data.w              ; the count, the timer running on
                move.b  d1,\fx+FX_COUNT(a0)
                bra.w   \{tick}_done
\{tick}_some:
                READ2   \cc
                tst.b   d2                      ; the timer's reset stops the
                bpl.s   .kept\~                 ; timer before the source is
                btst    #6,d2                   ; switched, so no tick of the
                beq.s   .kept\~                 ; old rate takes the new
                andi.b  #\keep,\ctrl.w          ; source, and the tick latched
                bclr    #\bit,(\ier+4).w        ; before the stop is dropped
.kept\~:
                READ    \ct
                bpl.s   .t\~
                and.b   #$7F,d1
                move.b  d1,\fx+FX_TARGET(a0)
.t\~:           READ    \cs
                bpl.w   .rate\~
                and.w   #$7F,d1
                beq.w   .stop\~
; a start: the source as init resolved it, the handler aimed at its rows
                lsl.w   #3,d1
                movea.l WS_SOURCES-8(a0,d1.w),a4    ; its first row
                move.l  WS_SOURCES-4(a0,d1.w),d0    ; the loop cell: row RR,
                bmi.w   .square\~                   ; 0 where it plays once,
                                                    ; and -1 for a square
; the handler's three operands stand under one mask, since the timer may
; run: a tick between the target register and the place writes the old
; source's row to the new target, and one between the place and the loop
; cell takes the new place against the cell the old source left
.aim\~:         lea     \tick(pc),a5
; The three behind level 7, so a tick takes none of them half made. The
; window is three writes wide and costs no measurable jitter: the ticks
; of a tune running one effect keep their period to 0.31 per cent with
; it and to 0.31 without, where the reference player's keep 0.33. The
; place the row does not reset is read in the window too, since a tick
; between the read and the write steps it (1.9).
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  \fx+FX_TARGET(a0),TICK_SEL(a5)
                move.l  a4,d1                   ; the place: the first row,
                btst    #5,d2                   ; or the row it stands on in
                bne.s   .put\~                  ; the rows it replaces, which
                tst.b   \fx+FX_SQUARE(a0)       ; the square holds as bit 7 of
                beq.s   .held\~                 ; its own immediate
                lea     \sq(pc),a3
                tst.b   SQ_VAL(a3)
                bpl.s   .put\~
                addq.l  #1,d1
                bra.s   .put\~
.held\~:        add.l   TICK_PTR(a5),d1
                sub.l   \fx+FX_FIRST(a0),d1
.put\~:         move.l  d1,TICK_PTR(a5)
                move.l  d0,TICK_CELL(a5)
                move.w  (sp)+,sr
                move.l  a4,\fx+FX_FIRST(a0)
                move.l  a5,\vector.w
                clr.b   \fx+FX_SQUARE(a0)
; the start placed the source, so the row's place reset is made: a tick
; before the select steps the place on, and the reset below would undo it
                bclr    #5,d2
                bra.w   .rate\~

; A square: the two rows into the handler's own immediate and the
; difference of the two into the one instruction that moves between
; them. The row the immediate stands at is the place, and a start that
; moves none leaves the half it is in, which bit 7 gives.
.square\~:      lea     \sq(pc),a5
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
                tst.b   \fx+FX_SQUARE(a0)
                beq.s   .fromrow\~
                tst.b   SQ_VAL(a5)
                bmi.s   .marked\~
                bra.s   .first\~
.fromrow\~:     lea     \tick(pc),a3
                move.l  TICK_PTR(a3),d0
                sub.l   \fx+FX_FIRST(a0),d0
                btst    #0,d0
                bne.s   .marked\~
.first\~:       move.b  d3,SQ_VAL(a5)
                bra.s   .aimed\~
.marked\~:      move.b  d4,SQ_VAL(a5)
.aimed\~:       move.l  a5,\vector.w
                move.w  (sp)+,sr
                move.l  a4,\fx+FX_FIRST(a0)
                st      \fx+FX_SQUARE(a0)
                bclr    #5,d2
                bra.w   .rate\~
.stop\~:        andi.b  #\keep,\ctrl.w      ; select 0: the timer stops
                bclr    #\bit,(\ier+4).w    ; and drops its latched tick
.rate\~:        READ    \cn
                tst.b   d2
                bpl.s   .live\~
                btst    #6,d2
                beq.s   .set\~
; the timer's reset: stopped above, the count, then the select
                tst.b   d1
                bne.s   .cnt\~
                move.b  \fx+FX_COUNT(a0),d1
.cnt\~:         move.b  d1,\data.w
                move.b  d1,\fx+FX_COUNT(a0)
                bra.s   .sel\~
.live\~:        tst.b   d1
                beq.s   .done\~
                move.b  d1,\data.w            ; the count, the timer running on
                move.b  d1,\fx+FX_COUNT(a0)
                bra.s   .done\~
.set\~:         tst.b   d1
                beq.s   .sel\~
                move.b  d1,\data.w
                move.b  d1,\fx+FX_COUNT(a0)
.sel\~:         move.b  d2,d1
                and.b   #7,d1
                move.b  d1,\fx+FX_SELECT(a0)
                .if     \shift
                lsl.b   #\shift,d1
                .endif
; the control register takes one write: a select written to a running
; timer neither stops nor reloads the running count (SPEC.md 1.9), and
; select 0 in front of the new select stops the timer and costs it the
; prescaler's partial count. Timer A's and Timer B's control register is
; the timer's own, so one move writes it and no interrupt can fall
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
                beq.s   .done\~                ; handler the effect runs
                tst.b   \fx+FX_SQUARE(a0)
                beq.s   .row\~
                lea     \sq(pc),a5
                movea.l \fx+FX_FIRST(a0),a3
                move.b  (a3),SQ_VAL(a5)
                bra.s   .done\~
.row\~:         lea     \tick(pc),a5
                move.l  \fx+FX_FIRST(a0),TICK_PTR(a5)
.done\~:
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
                bclr    #\bit,(\ier+4).w
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
                bclr    #\bit,(\ier+4).w
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

; The other end of a mark: this tick's estimated cost counted, in turns
; of ten cycles, and the background put back. It stands after the write
; the tick makes and before the end of interrupt, so a nested tick of a
; higher timer marks and unmarks inside it.
                .macro  COUNT   turns
                .if     YMXR_PERF
                move.l  a0,-(sp)
                lea     ymxr_perf_acc(pc),a0
                add.w   #\turns,(a0)
                movea.l (sp)+,a0
                move.w  (sp)+,PERF_COLOUR.w
                .endif
                .endm

; A tick (SPEC.md 5): the place's row to the target, the place advanced;
; on the marker, the place to row RR, or the timer stopped. No register
; is used: the place is the write's own operand, stepped in place. The
; interrupt level drops once the select and the value are written, so a
; higher timer may nest, and the in-service bit is cleared by hand, as
; software end-of-interrupt mode asks.
                .macro  TICK    name,eoi,eoir,ctrl,keep,colour
\name:
                MARK    \colour
\{name}_row:    move.b  #8,YM_SELECT.w          ; +3: the target register
                move.b  ($12345678).l,YM_SELECT+2.w ; +8: the place
                bmi.s   .last\~
                .if     YMXR_NEST
                move.w  #$2500,sr
                .endif
                addq.l  #1,($12345678).l        ; +22: the place, stepped
                COUNT   PERF_ON
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
.last\~:
\{name}_mark:   tst.l   ($12345678).l           ; +2: the loop cell
                beq.s   .stop\~
                move.l  ($12345678).l,($12345678).l ; +10 the cell, +14 the place
                COUNT   PERF_LOOP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
.stop\~:        andi.b  #\keep,\ctrl.w
                COUNT   PERF_STOP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_cell:   dc.l    0                       ; the loop cell
                .endm

; A square's tick: a source of two rows that repeats to row 0 holds two
; values and nothing else, so this handler holds them as its own
; immediate and moves between them by the difference of the two. It
; steps no place, tests no marker and reads no row. The place SPEC.md
; 1.9 gives a square is which of the two rows the immediate stands at,
; and bit 7 tells them apart, the last row of a source being its marker
; (3.2). A source of any other shape takes the handler above.
                .macro  SQUARE  name,eoi,eoir,colour
\name:
                MARK    \colour
\{name}_sel:    move.b  #8,YM_SELECT.w          ; +3: the target register
\{name}_val:    move.b  #$0F,YM_SELECT+2.w      ; +3: the row it stands at
                .if     YMXR_NEST
                move.w  #$2500,sr
                .endif
\{name}_tog:    eori.b  #$0F,($12345678).l      ; +3 the two rows' difference,
                COUNT   PERF_SQ                 ; +4 the row's own address
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
                cmp.w   #YMXR_VERSION,TF_VERSION(a0)
                bne     .bad
                move.l  a6,-(sp)
                movea.l a1,a6
                move.l  a0,WS_TUNE(a6)
                movea.l a0,a2
                adda.l  TF_IMAGE(a0),a2
                move.l  a2,WS_IMAGE(a6)
                move.b  TF_EFFECTS(a0),WS_EFFECTS(a6)
                cmp.b   #2,IM_VARIANT(a2)       ; a DTX2 image: its rows come
                bne     .bad6                   ; round on their own
                lea     WS_STATE(a6),a0
                jsr     IM_INIT(a2)
                movea.l WS_IMAGE(a6),a2
                jsr     IM_METADATA(a2)         ; d0 R, d2 RR, d3 the stride
                move.l  d0,WS_R(a6)
                move.l  d0,WS_ROWS(a6)
                move.l  d2,WS_RR(a6)

; Every source resolved once: its first row, and its loop cell, which is
; row RR where it repeats, 0 where it plays once, and -1 where it is two
; rows repeating to row 0, the shape a square's own handler takes. A
; start then reads two longs where it walked the index and read the
; source's header.
                movea.l WS_TUNE(a6),a1
                moveq   #0,d1
                move.b  TF_SOURCES(a1),d1
                subq.w  #1,d1
                bmi.s   .resolved
                lea     WS_SOURCES(a6),a4
                moveq   #0,d4
.source:        movea.l WS_TUNE(a6),a3
                move.l  TF_INDEX(a3,d4.w),d0
                adda.l  d0,a3                   ; the source's DTX1 table
                lea     DT_PAYLOAD(a3),a5       ; its first row
                move.l  a5,(a4)+
                move.l  DT_REPEAT(a3),d0
                cmp.l   DT_ROWS(a3),d0          ; the row it repeats to, or
                beq.s   .plays                  ; none where it plays once
                add.l   a5,d0
                cmp.l   #2,DT_ROWS(a3)          ; two rows repeating to row 0
                bne.s   .cell                   ; is a square
                tst.l   DT_REPEAT(a3)
                bne.s   .cell
                moveq   #-1,d0
                bra.s   .cell
.plays:         moveq   #0,d0
.cell:          move.l  d0,(a4)+
                addq.w  #4,d4
                dbra    d1,.source
.resolved:

; The row's columns stand a stride apart (DTX, abi.md 2): column i's read
; takes i strides as its displacement.
                lea     ymxr_reads(pc),a3
                lea     ymxr_frame(pc),a4
                moveq   #0,d1
                moveq   #YMXR_COLUMNS-1,d2
.patch:         move.w  (a3)+,d4
                move.w  d1,(a4,d4.w)
                add.w   d3,d1
                dbra    d2,.patch
                lea     ymxr_sites(pc),a3
                moveq   #YMXR_SITES-1,d2
.site:          move.w  (a3)+,d4
                move.w  (a3)+,d0
                mulu.w  d3,d0
                move.w  d0,(a4,d4.w)
                dbra    d2,.site

; The tick handlers: each one's step and loop operands name its own
; cells, and its place is parked on a marker until a start.
                lea     ymxr_tick0(pc),a5
                bsr     ymxr_link
                lea     ymxr_tick1(pc),a5
                bsr     ymxr_link
                lea     ymxr_tick2(pc),a5
                bsr     ymxr_link
                lea     ymxr_tick3(pc),a5
                bsr     ymxr_link

                .if     YMXR_AEOI
                move.b  MFP_VECTOR.w,WS_VECTOR(a6)  ; the mode kept, and the
                bclr    #3,MFP_VECTOR.w             ; MFP ending its own
                .endif

                .if     YMXR_NEST
; A tune that runs one effect has nothing to nest inside a tick, so the
; level that tick drops buys it nothing: two nops stand where the drop
; would, in both of that effect's handlers. A tune running two or more
; keeps the drop, and one running none reaches no handler at all.
                move.b  WS_EFFECTS(a6),d0
                move.b  d0,d1
                subq.b  #1,d1
                and.b   d1,d0                   ; more than one bit set
                bne.s   .nests
                lea     ymxr_tick0(pc),a5
                lea     ymxr_sq0(pc),a4
                moveq   #0,d4
                moveq   #3,d2
.alone:         btst    d4,WS_EFFECTS(a6)
                beq.s   .quiet
                move.l  #$4E714E71,TICK_SR(a5)
                move.l  #$4E714E71,SQ_SR(a4)
.quiet:         lea     TICK_SIZE(a5),a5
                lea     SQ_SIZE(a4),a4
                addq.w  #1,d4
                dbra    d2,.alone
.nests:
                .endif

; A square's handler names the row it stands at in the one instruction
; that moves between the two.
                lea     ymxr_sq0(pc),a5
                moveq   #3,d2
.sq:            lea     SQ_VAL(a5),a3
                move.l  a3,SQ_PTR(a5)
                lea     SQ_SIZE(a5),a5
                dbra    d2,.sq

; The effects' records: nothing held yet.
                lea     WS_FX(a6),a0
                moveq   #7,d0
.clear:         clr.l   (a0)+
                dbra    d0,.clear

; The effects the tune runs are read every row; the others are jumped
; over, the branch at each macro's head standing where the tune does not
; run its effect.
; The head of each effect: the effect's own first read where the tune
; runs it, and where it does not, the branch it holds already, its
; displacement moved to the head of the next effect the tune does run or
; past the last of them. A run of effects the tune does not run then
; costs one branch. This stands after the sites above, which write the
; read's displacement into the word a skipped effect's branch takes.
                lea     ymxr_frame(pc),a4
                lea     ymxr_overs(pc),a3
                lea     ymxr_tick3_done(pc),a5
                moveq   #3,d2
.over:          move.w  (a3)+,d4
                lea     (a4,d4.w),a1
                btst    d2,WS_EFFECTS(a6)
                beq.s   .skip
                move.w  #$1229,(a1)             ; move.b d16(a1),d1: the
                movea.l a1,a5                   ; effect's first read
                bra.s   .next
.skip:          move.l  a5,d0
                sub.l   a1,d0
                subq.l  #2,d0
                move.w  d0,2(a1)
.next:          dbra    d2,.over

; The advance's own address, written into the call that plays: a slot is
; a bra.w (DTX, abi.md 1), so the body stands two bytes past the slot
; plus the branch's displacement, and a play call reaches it in one jsr.
                movea.l WS_IMAGE(a6),a2
                lea     IM_ADVANCE(a2),a3
                move.w  2(a3),d0
                ext.l   d0
                lea     2(a3,d0.l),a3
                lea     ymxr_call+2(pc),a4
                move.l  a3,(a4)

; The first row, taken here so that a call writes a row it already has
; (SPEC.md 4): the writes then leave a host's clock at one offset,
; whatever the advance for the next row costs.
                lea     WS_STATE(a6),a0
                jsr     (a3)                    ; a1 the row

; The timers the tune runs, claimed. The others stand as they are.
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

; a5 a tick handler: its operands linked, its place parked.
ymxr_link:
                lea     TICK_PTR(a5),a3
                move.l  a3,TICK_STEP(a5)
                move.l  a3,TICK_LOOPW(a5)
                lea     TICK_CELL(a5),a3
                move.l  a3,TICK_LOOPT(a5)
                move.l  a3,TICK_LOOPR(a5)
                clr.l   TICK_CELL(a5)
                lea     ymxr_park(pc),a3
                move.l  a3,TICK_PTR(a5)
                rts

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
                move.l  #$08000000,YM_SELECT.w
                move.l  #$09000000,YM_SELECT.w
                move.l  #$0A000000,YM_SELECT.w
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
                                                ; the counter stands still
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
                EFFECT  WS_FX,_14,_15,_16,_17,$FFFFFA19,$FFFFFA1F,$FFFFFA07,5,$F0,0,0,ymxr_tick0,ymxr_sq0,$134
                EFFECT  WS_FX+8,_18,_19,_20,_21,$FFFFFA1D,$FFFFFA25,$FFFFFA09,4,$F0,0,1,ymxr_tick1,ymxr_sq1,$110
                EFFECT  WS_FX+16,_22,_23,_24,_25,$FFFFFA1B,$FFFFFA21,$FFFFFA07,0,$F0,0,0,ymxr_tick2,ymxr_sq2,$120
                EFFECT  WS_FX+24,_26,_27,_28,_29,$FFFFFA1D,$FFFFFA23,$FFFFFA09,5,$0F,4,1,ymxr_tick3,ymxr_sq3,$114
; 4: the tone periods.
                TONE    _0,_1,$0000,$0100
                TONE    _2,_3,$0200,$0300
                TONE    _4,_5,$0400,$0500
; 5: the noise period, and the envelope period beside the shape's bits:
;    where the shape column is 0, no bit beside is set, and a period byte
;    is written where it is not 0 and nothing else is tested.
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
; test here is of its own set bit alone.
                tst.b   d2
                bpl.s   ymxr_played
                move.w  #$0D00,d1
                move.b  d2,d1
                movep.w d1,(a2)
                bra.s   ymxr_played

; Steps 6 and 7 again, for the row whose envelope column step 5 found 0:
; step 8 has nothing to write on that row, and this copy ends without
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
; The row is written: the next one is taken now, after every write this
; call makes, so nothing the advance costs stands between a host's clock
; and the chip.
ymxr_played:
                subq.l  #1,WS_ROWS(a0)
                bne.s   ymxr_next
                move.l  WS_R(a0),d0             ; the last row: the loop's
                sub.l   WS_RR(a0),d0            ; rows, or none
                move.l  d0,WS_ROWS(a0)
                beq.s   ymxr_return             ; none: no row to take
ymxr_next:      lea     WS_STATE(a0),a0
ymxr_call:      jsr     ($12345678).l           ; the advance's own address,
                                                ; resolved at init
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

; The four effects' heads, effect 3 first, as offsets into the frame.
ymxr_overs:
                dc.w    ymxr_tick3_over-ymxr_frame,ymxr_tick2_over-ymxr_frame
                dc.w    ymxr_tick1_over-ymxr_frame,ymxr_tick0_over-ymxr_frame

; The other sites a column is read at, each with its column: the effects'
; one-pass tests, and the envelope period's plain path.
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

; ---------------------------------------------------------------- ticks

                TICK    ymxr_tick0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$070
                TICK    ymxr_tick1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$007
                TICK    ymxr_tick2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$707
                TICK    ymxr_tick3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$077

                SQUARE  ymxr_sq0,$DF,$FFFFFA0F,$070
                SQUARE  ymxr_sq1,$EF,$FFFFFA11,$007
                SQUARE  ymxr_sq2,$FE,$FFFFFA0F,$707
                SQUARE  ymxr_sq3,$DF,$FFFFFA11,$077

; The offsets the patching writes at, measured off the first handler's
; own labels: the row it writes, the marker it reads, and its loop cell.
; A monitor's mark stands between those blocks, and the distances follow
; it. Every handler is laid out by the one macro, so one handler's
; offsets are all four handlers'. The rig reads them back.
TICK_SEL        equ     ymxr_tick0_row-ymxr_tick0+3   ; the target register
TICK_PTR        equ     ymxr_tick0_row-ymxr_tick0+8   ; the place: a tick's row
TICK_STEP       equ     ymxr_tick0_row-ymxr_tick0+18+(YMXR_NEST*4)
                                        ; the place's own address, which the
                                        ; level's drop stands in front of
                                        ; where a tick drops it
TICK_LOOPT      equ     ymxr_tick0_mark-ymxr_tick0+2  ; the loop cell's address
TICK_LOOPR      equ     ymxr_tick0_mark-ymxr_tick0+10 ; the same
TICK_LOOPW      equ     ymxr_tick0_mark-ymxr_tick0+14 ; the place's address
TICK_CELL       equ     ymxr_tick0_cell-ymxr_tick0    ; row RR, or 0 once
TICK_SR         equ     ymxr_tick0_row-ymxr_tick0+16  ; the level it drops
TICK_SIZE       equ     ymxr_tick1-ymxr_tick0

; A square's handler, the same way: the register it selects, the row it
; stands at, the two rows' difference, and the address of the row it
; stands at, which init writes once.
SQ_SEL          equ     ymxr_sq0_sel-ymxr_sq0+3
SQ_VAL          equ     ymxr_sq0_val-ymxr_sq0+3
SQ_MASK         equ     ymxr_sq0_tog-ymxr_sq0+3
SQ_PTR          equ     ymxr_sq0_tog-ymxr_sq0+4
SQ_SR           equ     ymxr_sq0_val-ymxr_sq0+6
SQ_SIZE         equ     ymxr_sq1-ymxr_sq0

; A parked place: a marker with no loop cell, so a stray tick stops its
; timer.
ymxr_park:      dc.b    $80
                .even
