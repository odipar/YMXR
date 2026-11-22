; cut.S - a source under a cut (SPEC.md 1.11): the rows of the running
; cut, the loop of the counted handler under it, and a start, a recut and
; a place's reset under it. YMXR.S includes it where YMXR_MAP is 1.
;
; A start or a recut under a cut 1 to 127 runs the counted handler, whose
; counter of rows left ends a pass at the cut's last row L, and whose loop
; cell is the cut's loop row (TICKC, ticks.S). It writes each byte of a
; marked source whole, its marker bit included.

; The rows F and L of the running cut of the record a3, or of cut d0 at
; ymxr_xrows, in d0 and d1, out of the cut index the bound tune's field
; at 28 locates (BINARIES.md 1.2): a word U, then a long first row and a
; long last row a cut. a1 clobbered.
ymxr_xcut:      moveq   #0,d0
                move.b  FX_CRUN(a3),d0
ymxr_xrows:     movea.l WS_TUNE(a0),a1
                adda.l  TF_CUTS(a1),a1
                lsl.w   #3,d0
                move.l  -2(a1,d0.w),d1          ; L, 8n - 2 bytes on for cut n
                move.l  -6(a1,d0.w),d0          ; F
                rts

; The loop of the counted handler a5 under the rows F to L, d0 and d1, of
; the source whose first row is a4 (SPEC.md 1.11.4): in a1 its cell, at
; the loop row, row RR where F to L includes it and row F where it
; excludes it, and 0 where the source plays once; in d6 the rows of a
; pass, L + 1 less the loop row. d3 clobbered.
ymxr_xloop:     move.l  DT_REPEAT-DT_PAYLOAD(a4),d3     ; RR
                cmp.l   DT_ROWS-DT_PAYLOAD(a4),d3
                beq.s   .once                   ; RR at R: it plays once
                cmp.l   d0,d3
                bcs.s   .first
                cmp.l   d1,d3
                bls.s   .row
.first:         move.l  d0,d3
.row:           move.l  d1,d6
                addq.l  #1,d6
                sub.l   d3,d6                   ; the rows of a pass
                lea     (a4,d3.l),a1            ; the loop row
                .if     YMXR_PCREL
                suba.l  a5,a1                   ; its displacement from the
                suba.w  #TICKC_PTR,a1           ; instruction that reads it
                .endif
                rts
.once:          suba.l  a1,a1
                moveq   #1,d6
                rts

; A start under a cut 1 to 127 at the frame (SPEC.md 4.3 step 3), the
; running cut the kept cut (_xnow): the counted handler of the effect's
; bank 0 is aimed at the source's rows with the kept target. Its place is
; row F of the cut where the row sets bit 5, and otherwise the row the
; running handler is at, modulo R of this source, and row F where that row
; is outside the cut; its counter is the rows from the place to L, and its
; loop that of ymxr_xloop. The place's read and the handler's writes are
; behind level 7, as at every start, and the vector is aimed with them. d1
; the source, d2 the control byte, d4 the effect. Clobbers d0, d1, d3 to
; d5 and a3 to a5; returns a2 at the chip's select register.
ymxr_xstart:    movem.l d6-d7/a1,-(sp)
                move.w  d4,d6                   ; the effect
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; its record
                lsl.w   #3,d1
                movea.l WS_SOURCES-8(a0,d1.w),a4    ; the source's first row
                lea     ymxr_at_counted(pc),a5
                move.w  d6,d4
                add.w   d4,d4
                adda.w  (a5,d4.w),a5            ; the effect's counted handler
                lea     ymxr_timers(pc),a2
                lsl.w   #3,d6
                movea.w (a2,d6.w),a2            ; and its vector
                move.l  DT_ROWS-DT_PAYLOAD(a4),d7   ; R
                bsr.s   ymxr_xcut               ; F and L
                bsr.s   ymxr_xloop              ; the loop cell and a pass
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.l  d0,d3                   ; row F, where the row resets
                btst    #5,d2                   ; the place
                bne.s   .placed
                move.l  a2,-(sp)
                bsr.w   ymxr_runrow             ; the running row
                movea.l (sp)+,a2
                move.l  d7,d4
                bsr.w   ymxr_rem                ; modulo R
                cmp.l   d0,d3
                bcs.s   .outside
                cmp.l   d1,d3
                bls.s   .placed
.outside:       move.l  d0,d3
.placed:        sub.l   d3,d1
                addq.l  #1,d1                   ; the rows left, L - P + 1
                move.b  FX_TARGET(a3),TICKC_SEL(a5)
                move.l  a4,d0
                add.l   d3,d0                   ; the place
                .if     YMXR_PCREL
                sub.l   a5,d0
                sub.l   #TICKC_PTR,d0
                move.w  d0,TICKC_PTR(a5)
                move.w  a1,TICKC_CELL(a5)
                .else
                move.l  d0,TICKC_PTR(a5)
                move.l  a1,TICKC_CELL(a5)
                .endif
                move.w  d1,TICKC_LEFT(a5)
                move.w  d6,TICKC_RELD(a5)
                move.l  a5,(a2)                 ; the vector
                move.w  (sp)+,sr
                move.l  a4,FX_FIRST(a3)
                move.l  d7,FX_ROWS(a3)
                move.b  #4,FX_SHAPE(a3)
                movem.l (sp)+,d6-d7/a1
                lea     YM_SELECT.w,a2
                rts

; A recut on a running timer (SPEC.md 4.3.2): the kept cut runs, and the
; counted handler of the running bank is aimed at the running source's
; rows under it, with the running target, at the row the running handler
; is at where the cut includes it and at row F where it excludes it.
; Under cut 0 the rows are 0 to R - 1, and a recut from cut 0 to cut 0
; leaves the handler as it is; a timer before the first start has no
; source to cut. With a start pending the recut is at level 7 from the
; record's settling (ymxr_pend) to the vector's write, so the switch is
; before it or after it, and the hook (ymxr_rehook) and the armed place
; (ymxr_xplace) follow the new cut. d4 the effect. Clobbers d0, d1, d4,
; d5 and a3 to a5.
ymxr_recut:     movem.l d2-d3/d6-d7/a1-a2/a6,-(sp)
                move.w  sr,-(sp)
                move.w  d4,d2                   ; the effect
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; its record
                lea     ymxr_park(pc),a4
                cmpa.l  FX_FIRST(a3),a4
                beq.w   .kept
                .if     YMXR_DEFER
                tst.b   FX_PENDING(a3)          ; the record of a switch a tick
                beq.s   .settled                ; has made since the frame
                ori.w   #$0700,sr               ; before
                move.w  d2,d4
                moveq   #0,d5
                bsr.w   ymxr_pend
.settled:
                .endif
                move.b  FX_CRUN(a3),d0
                or.b    FX_CUT(a3),d0
                beq.w   .kept                   ; cut 0 to cut 0
                move.b  FX_CUT(a3),FX_CRUN(a3)  ; the kept cut runs
                movea.l FX_FIRST(a3),a4         ; the running source
                lea     ymxr_at_counted(pc),a5
                move.w  d2,d4
                add.w   d4,d4
                adda.w  (a5,d4.w),a5            ; the effect's counted handler
                lea     ymxr_timers(pc),a6
                move.w  d2,d4
                lsl.w   #3,d4
                adda.w  d4,a6                   ; its timer
                movea.w (a6),a2                 ; and its vector
                .if     YMXR_DEFER
                move.l  (a2),d4                 ; in the bank the running
                lea     ymxr_bank1(pc),a1       ; handler is in
                cmp.l   a1,d4
                bcs.s   .bank
                adda.w  #BANK,a5
.bank:
                .endif
                moveq   #0,d0                   ; rows 0 to R - 1 under cut 0
                move.l  DT_ROWS-DT_PAYLOAD(a4),d1
                subq.l  #1,d1
                tst.b   FX_CRUN(a3)
                beq.s   .rows
                bsr.w   ymxr_xcut               ; F and L
.rows:          bsr.w   ymxr_xloop              ; the loop cell and a pass
                ori.w   #$0700,sr
                move.l  a2,-(sp)
                movea.l (a2),a2                 ; the running handler, and
                move.b  FX_SHAPE(a3),d5         ; the target it writes, read
                beq.s   .tsel                   ; by its shape
                bmi.s   .ssel
                cmp.b   #1,d5
                bne.s   .tsel
                move.b  ONE_SEL(a2),d7
                bra.s   .sel
.ssel:          move.b  SQ_SEL(a2),d7
                bra.s   .sel
.tsel:          move.b  TICK_SEL(a2),d7
.sel:           movea.l (sp),a2
                bsr.w   ymxr_runrow             ; the running row
                movea.l (sp)+,a2
                cmp.l   d0,d3                   ; the place: the running row
                bcs.s   .outside                ; where the cut includes it,
                cmp.l   d1,d3                   ; and row F where it excludes
                bls.s   .placed                 ; it
.outside:       move.l  d0,d3
.placed:        move.l  d1,d5
                sub.l   d3,d5
                addq.l  #1,d5                   ; the rows left, L - P + 1
                move.b  d7,TICKC_SEL(a5)
                move.l  a4,d0
                add.l   d3,d0                   ; the place
                .if     YMXR_PCREL
                sub.l   a5,d0
                sub.l   #TICKC_PTR,d0
                move.w  d0,TICKC_PTR(a5)
                move.w  a1,TICKC_CELL(a5)
                .else
                move.l  d0,TICKC_PTR(a5)
                move.l  a1,TICKC_CELL(a5)
                .endif
                move.w  d5,TICKC_LEFT(a5)
                move.w  d6,TICKC_RELD(a5)
                .if     YMXR_DEFER
                tst.b   FX_PENDING(a3)
                beq.s   .aim
                movem.l d1/a4,-(sp)
                move.w  d2,d6                   ; the hook on the counted
                movea.l a5,a4                   ; handler
                moveq   #LOOK_KINDS+1,d0
                bsr.w   ymxr_rehook
                movem.l (sp)+,d1/a4
                bsr.w   ymxr_xplace             ; the armed place
.aim:
                .endif
                move.l  a5,(a2)                 ; the vector
                move.l  DT_ROWS-DT_PAYLOAD(a4),FX_ROWS(a3)
                move.b  #4,FX_SHAPE(a3)
.kept:          move.w  (sp)+,sr
                movem.l (sp)+,d2-d3/d6-d7/a1-a2/a6
                rts

                .if     YMXR_DEFER
; A recut while a start is pending whose place is L + 1 of the running
; cut (SPEC.md 4.8), at level 7: the armed handler placed again at L + 1
; of the new cut, d1 its L, modulo R of the armed source and inside the
; armed cut, the place a column and the rows left of a counted kind; a
; square at the level of that row. a3 the record.
ymxr_xplace:    btst    #7,FX_ACUT(a3)
                bne.w   .kept                   ; at its cut's first row
                movem.l d0-d5/a1-a2/a4-a5,-(sp)
                move.l  d1,d3
                addq.l  #1,d3                   ; L + 1
                movea.l FX_FIRST+ARMOFF(a3),a4
                move.l  DT_ROWS-DT_PAYLOAD(a4),d4
                bsr.w   ymxr_rem                ; modulo R of the armed source
                moveq   #0,d0
                move.b  FX_ACUT(a3),d0
                beq.s   .whole
                bsr.w   ymxr_xrows              ; inside its cut
                cmp.l   d0,d3
                bcs.s   .first
                cmp.l   d1,d3
                bls.s   .row
.first:         move.l  d0,d3
                bra.s   .row
.whole:         move.l  d4,d1                   ; rows 0 to R - 1
                subq.l  #1,d1
.row:           move.l  d3,FX_APLACE(a3)
                movea.l FX_ARMED(a3),a5
                move.b  FX_SHAPE+ARMOFF(a3),d5
                bpl.s   .steps
                move.b  FX_ALEVEL(a3),d0        ; a square: row 0's level, or
                btst    #0,d3                   ; row 1's, the difference of
                beq.s   .sq                     ; the two away
                move.b  SQ_MASK(a5),d2
                eor.b   d2,d0
.sq:            move.b  d0,SQ_VAL(a5)
                bra.s   .done
.steps:         cmp.b   #1,d5
                beq.s   .done                   ; a source of one row is at 0
                movem.l d1/a3,-(sp)
                lea     ARMOFF(a3),a3           ; the mirror, its kind
                bsr     ymxr_kindof
                tst.b   FX_SELECT(a3)           ; the armed mapping (FX_AMAP)
                bne.s   .kind
                move.w  d1,d0
.kind:          movem.l (sp)+,d1/a3
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2
                move.w  KD_LEFT(a2),d5
                move.w  KD_COLS(a2),d4
                lea     FX_FIRST+ARMOFF(a3),a1
.col:           moveq   #0,d0
                move.w  KD_PTR(a2),d0
                move.l  (a1)+,d2
                add.l   d3,d2                   ; the column's place
                .if     YMXR_PCREL
                sub.l   a5,d2
                sub.l   d0,d2
                move.w  d2,(a5,d0.w)
                .else
                move.l  d2,(a5,d0.w)
                .endif
                addq.l  #4,a2
                dbra    d4,.col
                tst.w   d5
                beq.s   .done
                sub.l   d3,d1
                addq.l  #1,d1
                move.w  d1,(a5,d5.w)            ; the rows left
.done:          movem.l (sp)+,d0-d5/a1-a2/a4-a5
.kept:          rts
                .endif

; The place's reset in a tune that cuts a source (SPEC.md 4.3 step 6):
; under a cut 1 to 127, the place in every column at row F of the running
; cut and L - F + 1 rows left, through the kind of the running handler;
; under cut 0, ymxr_widereset's. d3 the effect; clobbers d0 to d5, a3 and
; a5.
ymxr_xreset:    move.w  d3,d4
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; the effect's record
                tst.b   FX_CRUN(a3)
                beq.w   ymxr_widereset
                movem.l d6-d7/a1-a2,-(sp)
                bsr.w   ymxr_xcut               ; F and L
                move.l  d0,d6                   ; row F
                move.l  d1,d7
                sub.l   d0,d7
                addq.l  #1,d7                   ; L - F + 1 rows left
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
                add.l   d6,d0                   ; at row F
                .if     YMXR_PCREL
                sub.l   a5,d0
                sub.l   d1,d0
                move.w  d0,(a5,d1.w)
                .else
                move.l  d0,(a5,d1.w)
                .endif
                addq.l  #4,a2
                dbra    d4,.col
                move.w  d7,(a5,d5.w)            ; every kind a cut runs counts
                move.w  (sp)+,sr
                movem.l (sp)+,d6-d7/a1-a2
                rts
