; start.S - a start of a source: the offsets of the branches init aims in
; each effect, the reset of a place, the start of a source of several
; columns or of a counted one, and a row number modulo R.

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
                .if     YMXR_MAP&YMXR_DEFER
; Remap exit for an unset mapping column: displacements to ordinary rate
; processing and _retune.
REMAP_AT        equ     ymxr_tick0_mexit-ymxr_tick0_over+2
REMAP_RATE      equ     ymxr_tick0_rate-ymxr_tick0_mexit-2
REMAP_RETUNE    equ     ymxr_tick0_retune-ymxr_tick0_mexit-2
                .if     (ymxr_tick3_mexit-ymxr_tick3_over+2)<>REMAP_AT
                .error  "effect 3 lays its remap's exit out apart from effect 0"
                .endif
                .if     (ymxr_tick3_rate-ymxr_tick3_mexit-2)<>REMAP_RATE
                .error  "effect 3 lays the rate after its remap out apart from effect 0"
                .endif
                .if     (ymxr_tick3_retune-ymxr_tick3_mexit-2)<>REMAP_RETUNE
                .error  "effect 3 lays _retune out apart from effect 0"
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
; Init branch bytes for the larger effect record used with lookup
; support.
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
; Place reset for counted or multi-column handlers. Use the running
; kind's offsets for identity or lookup instances (SPEC.md 1.9). d3 is
; the effect; clobbers d0 to d5, a3 and a5.
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
; byte, a4 the source's first row. a5 the new handler, and Z where the
; source is one row, which the general handler's shape runs.
;
; The writes are behind level 7, as the general handler's three do: a
; tick between a select and a place writes one column's row to another
; column's register, and one between two places reads two rows. The
; window opens before the read of the place the row does not reset, since
; a tick between the read and the writes steps it (SPEC.md 1.8.4), and
; closes once the vector is aimed at the new handler: a tick due inside
; the window fires where it closes and runs the new handler from the
; place read. The installer aims an armed start's vector (4.8).
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
; R, which a pending start and a place's reset read; then level 7 from the
; place's read to the vector's write.
                move.l  DT_ROWS-DT_PAYLOAD(a4),FX_ROWS(a3)
                move.w  sr,-(sp)
                ori.w   #$0700,sr
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
; of the map.
.at:            cmp.w   #1,d4                   ; a source whose column fills
                beq.w   .countedaim             ; its byte
                cmp.w   #4,d4                   ; a counted source of two
                beq.w   .envaim                 ; columns
                moveq   #0,d5
                move.b  FX_TARGET(a3),d5
                sub.w   #14,d5                  ; the targets of several
                lsl.w   #3,d5                   ; registers begin at 14 (SPEC.md
                lea     ymxr_wide(pc),a2        ; 2.1): eight bytes a target in
                adda.w  d5,a2                   ; the map, two a column
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
; The vector, at level 7: a tick due in the window fires once it points
; at the new handler.
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
                move.w  (sp)+,(sp)              ; the level pushed, over R
                bra.w   .aimed

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
