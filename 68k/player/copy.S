; copy.S - copy from frame (SPEC.md 3.1.7): the frame values, the start of
; copy from frame on a target, and init's work for a tune that copies.
; YMXR.S includes it where the lookup is assembled in, the core that reads
; a tune of version 12; play.S has the register steps of a frame that
; leaves a register copy from frame runs on to the ticks. Every part is a
; piece of the block `copy`, which a link keeps for a song a tune of which
; copies from frame (doc/LINKER.md), and init aims a branch at it for such
; a tune alone, so a tune that copies from no frame runs the frame and the
; starts it ran before.

                PIECE   copy_4
; The frame value of each register, R0 to R13, FV_STRIDE bytes apart: the
; byte the frame last wrote to it, R7's with the ports' bits (1.4.2), and
; the register's number in the byte after it. Copy from frame is a source
; of one row repeating to row 0 whose column a register is the register's
; frame value: the general handler reads the marker in bit 7 of a register
; of seven bits or fewer, which a column's set bit leaves there, and the
; counted handler counts the one row of a register that reads every bit
; of its byte. Each frame value is the first row of a source of one
; column, behind a source's header (DTX, SPEC.md 1): R 1, C 1 and RR 0,
; which the start, the recut and a deferred start read as they read a
; table's. Init writes the values of ymxr_fv0, the frame value 0 of each,
; before the first frame.
                .macro  FVROW   rn
                dc.b    0,\rn                   ; the frame value, its register
                dc.l    1                       ; R, C and RR of the next
                dc.w    1
                dc.l    0
                dc.w    0
                .endm
                .even
                dc.w    0
                dc.l    1                       ; R, C and RR of R0's
                dc.w    1
                dc.l    0
                dc.w    0
ymxr_fv:        FVROW   0
                FVROW   1
                FVROW   2
                FVROW   3
                FVROW   4
                FVROW   5
                FVROW   6
                FVROW   7
                FVROW   8
                FVROW   9
                FVROW   10
                FVROW   11
                FVROW   12
                FVROW   13
ymxr_fvend:
                .if     (ymxr_fvend-ymxr_fv)<>(14*FV_STRIDE)
                .error  "ymxr_fv: a frame value is FV_STRIDE bytes from the next"
                .endif
ymxr_fv0:       dc.b    $00,$80,$00,$80,$00,$80,$80,$C0,$80,$80,$80,$00,$00,$80
                .even

; The registers that read every bit of their byte, a bit a register: R0,
; R2, R4, R7, R11 and R12 (SPEC.md 2.1.2). Copy from frame on one runs
; the counted handler.
COPY_COUNTED    equ     $1895

; The targets of several registers as copy from frame reads them: a
; register and the column of copy from frame that writes it, which is the
; register, FV_STRIDE bytes a column, one slot a column and eight bytes a
; target, from target 14, in the order of ymxr_wide.
ymxr_wcopy:     dc.b    0,0,  1,1,  0,0,  0,0   ; 14 setToneA:     R0 R1
                dc.b    2,2,  3,3,  0,0,  0,0   ; 15 setToneB:     R2 R3
                dc.b    4,4,  5,5,  0,0,  0,0   ; 16 setToneC:     R4 R5
                dc.b    0,0,  8,8,  1,1,  0,0   ; 17 setVoiceA:    R0 R1 R8
                dc.b    2,2,  9,9,  3,3,  0,0   ; 18 setVoiceB:    R2 R3 R9
                dc.b    4,4, 10,10, 5,5,  0,0   ; 19 setVoiceC:    R4 R5 R10
                dc.b   11,11, 12,12, 0,0, 0,0   ; 20 setEnvelope:  R11 R12
                dc.b   11,11, 12,12, 13,13, 0,0 ; 21 setBuzzer:    R11 R12 R13
                dc.b    8,8,  6,6,  0,0,  0,0   ; 22 setNoiseA:    R6 R8
                dc.b    9,9,  6,6,  0,0,  0,0   ; 23 setNoiseB:    R6 R9
                dc.b   10,10, 6,6,  0,0,  0,0   ; 24 setNoiseC:    R6 R10

; The start of a source the start's tag sends past the shapes of one
; column, in a tune that copies from frame: copy from frame's tag, -8,
; which the start passes as -7, starts copy from frame, and every other
; tag the start of several columns. Init aims each effect's call of
; ymxr_widestart and the arming's here for a tune that copies.
ymxr_copywide:  cmp.l   #-7,d0
                beq.s   ymxr_copystart
                bra.w   ymxr_widestart

; A start of copy from frame on effect d4: the handler of the kept
; target's registers aimed at their frame values, a source of one row
; repeating to row 0 at row 0 (SPEC.md 3.1.7, 5.1). The registers and the
; return are those of ymxr_widestart: a3 the record, its mirror where a
; deferred start is armed (4.8), a5 the handler, and NZ, a source of
; several columns' return. d0, d1, d3 to d5, a2 and a4 clobbered.
ymxr_copystart:
                move.l  d6,-(sp)                ; the effect, kept while the
                move.w  d4,d6                   ; handler is resolved
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; the effect's record
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; a deferred start is armed in
                beq.s   .rec                    ; the record's mirror (4.8)
                lea     ARMOFF(a3),a3
                .endif
.rec:           moveq   #1,d0
                move.l  d0,FX_ROWS(a3)          ; R, one row
                lea     ymxr_fv(pc),a4          ; row 0 of the column of each
                move.l  a4,d0                   ; register, the loop cell
                moveq   #0,d3                   ; the place, row 0
                moveq   #FV_STRIDE,d1           ; a column a frame value apart
                moveq   #0,d5
                move.b  FX_TARGET(a3),d5
                cmp.w   #14,d5
                bcc.w   .wide
; A target of one register: its frame value, the place and the loop cell.
                move.w  d5,d4
                mulu    #FV_STRIDE,d4
                adda.w  d4,a4
                move.l  a4,d0
                move.w  #COPY_COUNTED,d4
                btst    d5,d4
                bne.s   .counted
                lea     ymxr_at_tick(pc),a5
                bsr.w   .instance
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  d5,TICK_SEL(a5)
                .if     YMXR_PCREL
                lea     TICK_PTR(a5),a2         ; the place and the cell, each
                move.l  a4,d1                   ; a displacement from the
                sub.l   a2,d1                   ; instruction that reads the
                move.w  d1,TICK_PTR(a5)         ; row
                move.w  d1,TICK_CELL(a5)
                .else
                move.l  a4,TICK_PTR(a5)
                move.l  a4,TICK_CELL(a5)
                .endif
                clr.b   FX_SHAPE(a3)
                move.l  a4,FX_FIRST(a3)
                bra.w   .aimed
.counted:       lea     ymxr_at_counted(pc),a5
                bsr.w   .instance
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  d5,TICKC_SEL(a5)
                .if     YMXR_PCREL
                move.l  a4,d1
                sub.l   a5,d1
                sub.l   #TICKC_PTR,d1
                move.w  d1,TICKC_PTR(a5)
                move.w  d1,TICKC_CELL(a5)
                .else
                move.l  a4,TICKC_PTR(a5)
                move.l  a4,TICKC_CELL(a5)
                .endif
                move.w  #1,TICKC_LEFT(a5)       ; one row, and a pass through
                move.w  #1,TICKC_RELD(a5)       ; the loop of one
                move.b  #4,FX_SHAPE(a3)
                move.l  a4,FX_FIRST(a3)
                bra.w   .aimed
; A target of several registers: the slots of the map, each column at its
; register's frame value.
.wide:          move.w  d5,d4
                sub.w   #14,d4
                lsl.w   #3,d4
                lea     ymxr_wcopy(pc),a2
                adda.w  d4,a2
                cmp.b   #20,d5                  ; setEnvelope: two registers
                beq.w   .envelope               ; that read every bit
                cmp.b   #17,d5
                bcs.w   .two
                cmp.b   #21,d5
                bhi.w   .two
                lea     ymxr_at_three(pc),a5    ; setVoice and setBuzzer
                bsr.w   .instance
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                WSLOT   TW3_SEL1,TW3_PTR1,TW3_CELLS,FX_FIRST,0
                WSLOT   TW3_SEL2,TW3_PTR2,TW3_CELLS+CELL_SIZE,FX_FIRST2,2
                WSLOT   TW3_SEL3,TW3_PTR3,TW3_CELLS+CELL_SIZE+CELL_SIZE,FX_FIRST3,4
                move.b  #3,FX_SHAPE(a3)
                bra.w   .aimed
.two:           lea     ymxr_at_two(pc),a5      ; setTone and setNoise
                bsr.w   .instance
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                WSLOT   TW2_SEL1,TW2_PTR1,TW2_CELLS,FX_FIRST,0
                WSLOT   TW2_SEL2,TW2_PTR2,TW2_CELLS+CELL_SIZE,FX_FIRST2,2
                move.b  #2,FX_SHAPE(a3)
                bra.w   .aimed
.envelope:      lea     ymxr_at_envelope(pc),a5
                bsr.w   .instance
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                WSLOT   TWC_SEL1,TWC_PTR1,TWC_CELLS,FX_FIRST,0
                WSLOT   TWC_SEL2,TWC_PTR2,TWC_CELLS+CELL_SIZE,FX_FIRST2,2
                move.w  #1,TWC_LEFT(a5)
                move.w  #1,TWC_RELD(a5)
                move.b  #5,FX_SHAPE(a3)
; The vector, at level 7, as ymxr_widestart aims it.
.aimed:
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; an armed start (4.8)
                bne.s   .level
                .endif
                lea     ymxr_timers(pc),a2
                lsl.w   #3,d6                   ; the effect's, eight bytes a
                movea.w (a2,d6.w),a2            ; timer
                move.l  a5,(a2)
.level:         move.w  (sp)+,sr
                lea     YM_SELECT.w,a2          ; the chip's select register,
                move.l  (sp)+,d6                ; which the frame reads it in
                moveq   #1,d0                   ; NZ: several columns' return
                rts
; This effect's handler of the kind of table a5, in the bank of an armed
; start. d4 clobbered.
.instance:      move.w  d6,d4
                add.w   d4,d4
                adda.w  (a5,d4.w),a5
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                beq.s   .bank
                adda.w  FX_ABANK-ARMOFF(a3),a5
.bank:
                .endif
                rts

; Init's work on every tune, in a core with the block copy, after the
; sources are resolved (4.1). On every tune: the frame's first read at
; ymxr_regs written back as the move into d2 that READ2 assembles, where a
; tune before this one wrote the branch of ymxr_copysites over it, so the
; site loop writes it as it writes it alone (ymxr_col reads the opcode);
; the frame values at 0; and each call of ymxr_widestart aimed back, each
; effect's the link kept and the arming's in a core with the deferred
; start, which every link keeps. On a tune that copies from frame: copy
; from frame as source S, after the tables, its first row the frame
; values and its tag -8; bit 2 of d5, a tag among the sources, so each
; effect's start keeps the test of the shapes; and those calls aimed here.
; A set inits one loaded copy for each subtune, so each write is made on
; every init. a6 the workspace; d5 the bits of the shapes. Every other
; register kept.
ymxr_copyinit:  movem.l d0-d4/a1-a4,-(sp)
                lea     ymxr_regs(pc),a1
                move.w  #$1429,(a1)             ; move.b d16(a1),d2
                lea     ymxr_fv(pc),a3
                lea     ymxr_fv0(pc),a4
                moveq   #14-1,d0
.fv:            move.b  (a4)+,(a3)
                lea     FV_STRIDE(a3),a3
                dbra    d0,.fv
                lea     ymxr_widestart(pc),a3
                btst    #7,WS_EFFECTS(a6)       ; copy from frame (3.1.7)
                beq.s   .aim
                lea     ymxr_copywide(pc),a3
                movea.l WS_TUNE(a6),a1          ; source S: the bound tune
                moveq   #0,d0                   ; counts the tables
                move.b  TF_SOURCES(a1),d0
                lsl.w   #3,d0
                lea     WS_SOURCES(a6),a4
                lea     ymxr_fv(pc),a2
                move.l  a2,(a4,d0.w)
                moveq   #-8,d1
                move.l  d1,4(a4,d0.w)
                bset    #2,d5
; The call of each effect the link kept, at its head's distance.
.aim:           lea     ymxr_frame(pc),a4
                lea     ymxr_overs(pc),a2
                moveq   #3,d2
.each:          move.w  (a2)+,d4
                btst    d2,ymxr_k_effect+1(pc)
                beq.s   .next
                lea     (a4,d4.w),a1            ; the effect's head
                lea     WIDEN_CALL(a1),a1
                move.l  a3,d1
                sub.l   a1,d1
                move.w  d1,(a1)
.next:          dbra    d2,.each
                .if     YMXR_DEFER
                lea     ymxr_arm_wide+2(pc),a1  ; the arming's (deferred.S)
                move.l  a3,d1
                sub.l   a1,d1
                move.w  d1,(a1)
                .endif
                movem.l (sp)+,d0-d4/a1-a4
                rts
                ENDPIECE copy_4
