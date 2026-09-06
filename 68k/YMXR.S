; YMXR.S - the player: a tune's rows to the YM2149 and the MFP, one row a
; frame, and a source's rows to one register at a timer's rate.
;
; doc/SPEC.md is the contract. Section 4 is YMXR_play, section 5 the tick
; handlers, and the tune file section 3.3. DTX reads the tune's table: the
; tune carries the image DTX packaged it with, and this player calls that
; image's slots (DTX, doc/abi.md) and reads the row they give.
;
; The calls, by the jump table at the top of the binary:
;
;   YMXR_init   a0 the tune, on an even address; a1 the workspace, on a
;               long. d0 is 0, or -1 for a file this player does not read.
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
; The workspace is YMXR_FIXED bytes, then the state block the image's
; format block asks for, which the tune file states at offset 12
; (SPEC.md 3.3): a host allocates YMXR_FIXED plus that.
;
; The host owns the machine. This player saves and restores no vector, no
; timer control and no interrupt enable: around init and stop a host keeps
; what it needs of the vectors at $134, $120, $114 and $110, of TACR, TBCR
; and both nibbles of TCDCR, and of bits 5 and 0 of IERA and IMRA and bits
; 5 and 4 of IERB and IMRB. A timer an effect does not run is never
; touched. The MFP is taken in software end-of-interrupt mode, as TOS
; leaves it, and the vectors are the 68000's at zero.
;
; The player patches itself: the row's displacements at init, and the tick
; handlers' operands at every start. It runs from RAM, and one loaded copy
; serves one workspace.

YMXR_MAGIC      equ     $594D5852       ; YMXR
YMXR_VERSION    equ     $0001
YMXR_COLUMNS    equ     30

; The tune file (SPEC.md 3.3).
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

; A DTX1 table's header, and where a source's rows begin (DTX, SPEC.md 1).
DT_ROWS         equ     4
DT_REPEAT       equ     10
DT_PAYLOAD      equ     16

; The workspace.
WS_TUNE         equ     0               ; the tune
WS_IMAGE        equ     4               ; the image in it
WS_ROWS         equ     8               ; rows to the last, or to the wrap
WS_R            equ     12
WS_RR           equ     16
WS_EFFECTS      equ     20              ; a byte: the effects the tune runs
WS_FX           equ     24              ; four effects, FX_SIZE each
FX_TARGET       equ     0               ; the target the player holds
FX_SELECT       equ     1               ; the select it last took
FX_COUNT        equ     2               ; the count it last took
FX_FIRST        equ     4               ; the running source's first row
FX_SIZE         equ     8
YMXR_FIXED      equ     56
WS_STATE        equ     56              ; the image's state block, on a long

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

; A tick handler's operands, patched at a start: the byte offsets into a
; handler as the TICK macro lays it out. The rig reads them back.
TICK_SEL        equ     3               ; the target register
TICK_PTR        equ     8               ; the place: the row the next tick takes
TICK_STEP       equ     22              ; the place's own address, set at init
TICK_LOOPT      equ     36              ; the loop cell's address, set at init
TICK_LOOPR      equ     44              ; the same
TICK_LOOPW      equ     48              ; the place's address, set at init
TICK_CELL       equ     74              ; the loop cell: row RR, or 0 once

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

; A column with a set bit, to its register: the select in d1's high byte,
; the column read into its low byte, and one movep for the pair.
                .macro  WRITE   col,select
                move.w  #\select,d1
                READ    \col
                bpl.s   .no\~
                movep.w d1,(a2)
.no\~:
                .endm

; A column that fills its byte, reserving 0: written where it is not 0,
; or where the bit beside it, in d2, says 0 is the value.
                .macro  RESERVED col,bit,select
                move.w  #\select,d1
                READ    \col
                bne.s   .go\~
                btst    #\bit,d2
                beq.s   .no\~
.go\~:          movep.w d1,(a2)
.no\~:
                .endm

; A tone period: the coarse column into d2, for its bit 6 and its own
; value, then the fine column against it.
                .macro  TONE    fine,coarse,fsel,csel
                READ2   \coarse
                move.w  #\fsel,d1
                READ    \fine
                bne.s   .go\~
                btst    #6,d2
                beq.s   .nf\~
.go\~:          movep.w d1,(a2)
.nf\~:          tst.b   d2
                bpl.s   .nc\~
                move.w  #\csel,d1
                move.b  d2,d1
                movep.w d1,(a2)
.nc\~:
                .endm

; One effect: the target kept, the source started or stopped, the timer's
; count and select written, with the timer's and the place's reset.
                .macro  EFFECT  fx,ct,cs,cc,cn,ctrl,data,keep,shift,tick
                READ2   \cc
                tst.b   d2                      ; the timer's reset stops the
                bpl.s   .kept\~                 ; timer before the source is
                btst    #6,d2                   ; switched, so no tick of the
                beq.s   .kept\~                 ; old rate takes the new source
                andi.b  #\keep,\ctrl.w
.kept\~:
                READ    \ct
                bpl.s   .t\~
                and.b   #$7F,d1
                move.b  d1,\fx+FX_TARGET(a6)
.t\~:           READ    \cs
                bpl.s   .rate\~
                and.w   #$7F,d1
                beq.s   .stop\~
; a start: the source's rows through the index, the handler aimed at them
                movea.l WS_TUNE(a6),a3
                lsl.w   #2,d1
                move.l  TF_INDEX-4(a3,d1.w),d0
                adda.l  d0,a3                   ; the source's DTX1 table
                lea     DT_PAYLOAD(a3),a4       ; its first row
                move.l  a4,\fx+FX_FIRST(a6)
                lea     \tick(pc),a5
                move.b  \fx+FX_TARGET(a6),TICK_SEL(a5)
                move.l  a4,TICK_PTR(a5)
                move.l  DT_REPEAT(a3),d0
                cmp.l   DT_ROWS(a3),d0
                beq.s   .once\~
                add.l   a4,d0
                move.l  d0,TICK_CELL(a5)
                bra.s   .rate\~
.once\~:        clr.l   TICK_CELL(a5)
                bra.s   .rate\~
.stop\~:        andi.b  #\keep,\ctrl.w      ; select 0: the timer stops
.rate\~:        READ    \cn
                tst.b   d2
                bpl.s   .live\~
                btst    #6,d2
                beq.s   .set\~
; the timer's reset: stopped above, the count, then the select
                tst.b   d1
                bne.s   .cnt\~
                move.b  \fx+FX_COUNT(a6),d1
.cnt\~:         move.b  d1,\data.w
                move.b  d1,\fx+FX_COUNT(a6)
                bra.s   .sel\~
.live\~:        tst.b   d1
                beq.s   .done\~
                move.b  d1,\data.w            ; the count, the timer running on
                move.b  d1,\fx+FX_COUNT(a6)
                bra.s   .done\~
.set\~:         tst.b   d1
                beq.s   .sel\~
                move.b  d1,\data.w
                move.b  d1,\fx+FX_COUNT(a6)
.sel\~:         move.b  d2,d1
                and.b   #7,d1
                move.b  d1,\fx+FX_SELECT(a6)
                .if     \shift
                lsl.b   #\shift,d1
                .endif
                andi.b  #\keep,\ctrl.w
                or.b    d1,\ctrl.w
                btst    #5,d2
                beq.s   .done\~
                lea     \tick(pc),a5          ; the place's reset
                move.l  \fx+FX_FIRST(a6),TICK_PTR(a5)
.done\~:
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

; A tick (SPEC.md 5): the place's row to the target, the place advanced;
; on the marker, the place to row RR, or the timer stopped. No register
; is used: the place is the write's own operand, stepped in place. The
; interrupt level drops once the select and the value are written, so a
; higher timer may nest, and the in-service bit is cleared by hand, as
; software end-of-interrupt mode asks.
                .macro  TICK    name,eoi,eoir,ctrl,keep
\name:
                move.b  #8,YM_SELECT.w          ; +3: the target register
                move.b  ($12345678).l,YM_SELECT+2.w ; +8: the place
                bmi.s   .last\~
                move.w  #$2500,sr
                addq.l  #1,($12345678).l        ; +22: the place, stepped
                move.b  #\eoi,\eoir.w
                rte
.last\~:        tst.l   ($12345678).l           ; +36: the loop cell
                beq.s   .stop\~
                move.l  ($12345678).l,($12345678).l ; +44 the cell, +48 the place
                move.b  #\eoi,\eoir.w
                rte
.stop\~:        andi.b  #\keep,\ctrl.w
                move.b  #\eoi,\eoir.w
                rte
                dc.l    0                       ; +74: the loop cell
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

; The effects' records: nothing held yet.
                lea     WS_FX(a6),a0
                moveq   #7,d0
.clear:         clr.l   (a0)+
                dbra    d0,.clear

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
                movea.l (sp)+,a6
                rts

YMXR_play:
                move.l  a6,-(sp)
                movea.l a0,a6
                tst.l   WS_ROWS(a6)
                beq     .over
                lea     WS_STATE(a6),a0
                movea.l WS_IMAGE(a6),a2
                jsr     IM_ADVANCE(a2)          ; a1 the row
                lea     YM_SELECT.w,a2
                bsr     ymxr_frame
                subq.l  #1,WS_ROWS(a6)
                bne.s   .played
                move.l  WS_R(a6),d0             ; the last row: the loop's
                sub.l   WS_RR(a6),d0            ; rows, or none
                move.l  d0,WS_ROWS(a6)
.played:        moveq   #0,d0
                movea.l (sp)+,a6
                rts
.over:          moveq   #-1,d0
                movea.l (sp)+,a6
                rts

; ---------------------------------------------------------------- frame

; The frame procedure (SPEC.md 4): a1 the row, a2 the chip's select
; register, a6 the workspace.
ymxr_frame:
; 1 to 3: the effects, each its four columns.
                EFFECT  WS_FX,_14,_15,_16,_17,$FFFFFA19,$FFFFFA1F,$F0,0,ymxr_tick0
                EFFECT  WS_FX+8,_18,_19,_20,_21,$FFFFFA1D,$FFFFFA25,$F0,0,ymxr_tick1
                EFFECT  WS_FX+16,_22,_23,_24,_25,$FFFFFA1B,$FFFFFA21,$F0,0,ymxr_tick2
                EFFECT  WS_FX+24,_26,_27,_28,_29,$FFFFFA1D,$FFFFFA23,$0F,4,ymxr_tick3
; 4: the tone periods.
                TONE    _0,_1,$0000,$0100
                TONE    _2,_3,$0200,$0300
                TONE    _4,_5,$0400,$0500
; 5: the noise period, and the envelope period beside the shape's bits.
                READ2   _13
                WRITE   _6,$0600
                RESERVED _11,6,$0B00
                RESERVED _12,5,$0C00
; 6: the volumes.
                WRITE   _8,$0800
                WRITE   _9,$0900
                WRITE   _10,$0A00
; 7: the mixer, with the host's port directions.
                move.w  #$0700,d1
                READ    _7
                bpl.s   .no7
                or.b    #YM_PORTS,d1
                movep.w d1,(a2)
.no7:
; 8: the shape, read above into d2.
                tst.b   d2
                bpl.s   .no13
                move.w  #$0D00,d1
                move.b  d2,d1
                movep.w d1,(a2)
.no13:          rts

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

; ---------------------------------------------------------------- ticks

                TICK    ymxr_tick0,$DF,$FFFFFA0F,$FFFFFA19,$F0
                TICK    ymxr_tick1,$EF,$FFFFFA11,$FFFFFA1D,$F0
                TICK    ymxr_tick2,$FE,$FFFFFA0F,$FFFFFA1B,$F0
                TICK    ymxr_tick3,$DF,$FFFFFA11,$FFFFFA1D,$0F

; A parked place: a marker with no loop cell, so a stray tick stops its
; timer.
ymxr_park:      dc.b    $80
                .even
