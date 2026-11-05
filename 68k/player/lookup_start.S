; lookup_start.S - a start through a transformation. YMXR.S includes it
; and the lookup files after it where YMXR_MAP is 1.

; Start through a transformation (SPEC.md 1.10.2). Inputs: a3 the record,
; FX_RUN its table number, d3 the source, d2 the control byte, d4 the
; effect; at the frame a5 the effect's instance of the general lookup
; kind, d5 the page byte of its first fallback page and d1 the vector,
; which the frame's start writes as constants. A source that steps a
; place runs the lookup instance of its kind in bank 0. The general kind's
; operands are at that kind's offsets; every other kind's descriptor,
; address and page byte are read by index from the two tables below and
; ymxr_htab. Its page is the one the table of the source's fill kind
; has for the transformation (FX_LMAPS); outside it, the fallback page in
; use or the other where one has it, and otherwise the other, filled
; with interrupts on (ymxr_lfill). The record caches the
; instance (FX_LOK). Squares and one-row sources store mapped entries in
; handler immediates.
;
; Patch operands and vector at level 7. With place reset clear, read the
; running place and reduce it modulo the new source length at that level
; too, so a tick cannot advance it between read and write (1.8.4).
; Clobbers d0, d1, d3 to d5 and a3 to a5; returns a2 at the chip select
; register.
;
; A deferred start (SPEC.md 4.8) is armed here for ymxr_arm, at the frame
; constants left aside: in the bank FX_ABANK names, through the captured
; mapping FX_MAP, into the record's mirror at FX_APLACE, and with the
; vector left to the installer. It returns a5 the instance and a3 the
; mirror; with WS_ARMING at 1 it fills the page alone and returns a3 the
; record.
ymxr_lstart:    lsl.w   #3,d3                   ; the source's entry
                movea.l WS_SOURCES-8(a0,d3.w),a4    ; its first row
                move.l  WS_SOURCES-4(a0,d3.w),d0    ; its loop cell, or a tag
                bmi.w   .tagged
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                bne.w   .armlk
                .endif
; Marked, of one column, at the frame: the general kind, the marker
; mirror and the loop cell as a displacement from the place's read. The
; cache is written where it names another instance.
                lea     LK_PAGE(a5),a2          ; the page byte's address
                cmpa.l  FX_LBYTE(a3),a2
                beq.s   .lcached
                move.l  a2,FX_LBYTE(a3)
                move.b  d5,FX_LHIGH(a3)
                move.b  #1,FX_LKEY+1(a3)
                move.l  FX_LMAPS+4(a3),FX_LMAP(a3)  ; the marked kind's table
                .if     YMXR_DEFER
                sf      FX_LBANK(a3)
                .endif
.lcached:       move.b  FX_RUN(a3),d4           ; d4.w is this byte: d4 was
                                                ; the effect, below 256
                movea.l FX_LMAP(a3),a2
                move.b  0(a2,d4.w),d4           ; its page in the table, or
                beq.s   .lfall                  ; 0 for the fallback
.lpage:         add.b   d4,d5                   ; the page byte
                move.b  d5,FX_LCUR(a3)
                .if     YMXR_PCREL              ; the loop cell, 0 where the
                tst.l   d0                      ; source plays once
                beq.s   .lonce
                sub.w   a5,d0
                sub.w   #LK_PTR,d0
.lonce:
                .endif
                move.w  sr,-(sp)
                ori.w   #$0700,sr
ymxr_lkwindow   equ     *                       ; inside the window: the rig's
                                                ; boundary (inside)
                moveq   #0,d3                   ; the place: row 0, or the
                btst    #5,d2                   ; row the running handler is
                bne.s   .lfirst                 ; at modulo R of this source
                movea.w d1,a2
                bsr     ymxr_runrow
                move.l  DT_ROWS-DT_PAYLOAD(a4),d4
                bsr     ymxr_rem
                move.b  FX_LCUR(a3),d5          ; the page byte again
.lfirst:        move.b  FX_TARGET(a3),LK_SEL(a5)
                move.l  a4,FX_FIRST(a3)         ; the place at row 0
                .if     YMXR_PCREL
                move.w  a4,d4
                add.w   d3,d4                   ; the row the place is at
                sub.w   a5,d4
                sub.w   #LK_PTR,d4
                move.w  d4,LK_PTR(a5)
                move.w  d0,LK_CELL(a5)
                .else
                adda.l  d3,a4                   ; the row the place is at
                move.l  a4,LK_PTR(a5)
                move.l  d0,LK_CELL(a5)
                .endif
                move.b  d5,LK_PAGE(a5)          ; the page byte
                movea.w d1,a4
                move.l  a5,(a4)                 ; the vector
                move.w  (sp)+,sr
                clr.b   FX_SHAPE(a3)
                st      FX_LOK(a3)
                lea     YM_SELECT.w,a2          ; past the table's read
                rts
; Outside the table: the fallback page in use, then the other, filled
; where neither has the transformation in the marked kind.
.lfall:         move.b  FX_RUN(a3),FX_LKEY(a3)
                move.w  FX_LKEY(a3),d3
                move.b  FX_USE(a3),d4           ; d4.w is this byte, as above
                add.w   d4,d4
                cmp.w   FX_PAGE(a3,d4.w),d3
                beq.s   .lfound
                eori.w  #2,d4
                cmp.w   FX_PAGE(a3,d4.w),d3
                beq.s   .lfound
                move.l  d5,-(sp)
                move.w  d3,d5
                move.w  d4,d3
                bsr     ymxr_lfill
                move.l  (sp)+,d5
.lfound:        lsr.w   #1,d4
                move.b  d4,FX_USE(a3)
                add.b   FX_LFB(a3),d4           ; past the effect's pages
                bra.w   .lpage
                .if     YMXR_DEFER
.armlk:         moveq   #0,d5                   ; an arming of the general kind
                bra.s   .kind
                .endif
.tagged:        addq.l  #1,d0
                beq     .square
                addq.l  #1,d0
                beq     .one
                exg     d0,d3                   ; d0 the entry, d3 the tag
                lea     WS_WIDE(a0),a2          ; a source of several columns,
                swap    d1                      ; or counted: the stride from
                move.w  -4(a2,d0.w),d1          ; one column to the next, in
                swap    d1                      ; the high word of d1, and its
                move.l  -8(a2,d0.w),d0          ; loop cell
                moveq   #2,d5
                addq.l  #1,d3
                beq.s   .two
                moveq   #4,d5
                addq.l  #1,d3
                beq.s   .three
                moveq   #1,d5                   ; counted, of one column
                addq.l  #1,d3
                beq.s   .kind
                moveq   #6,d5                   ; counted, of two
                addq.l  #1,d3
                beq.s   .two
                moveq   #8,d5                   ; counted, of three
                bra.s   .three
.two:           cmp.b   #22,FX_TARGET(a3)       ; setNoiseX maps its first
                bcs.s   .kind                   ; write, the volume, marked
                addq.w  #1,d5                   ; or counted
                bra.s   .kind
.three:         cmp.b   #21,FX_TARGET(a3)       ; setVoiceX maps its middle
                beq.s   .kind                   ; column, the volume
                addq.w  #1,d5
; d5 the kind, d0 the loop cell, a4 the first row, and the stride in the
; high word of d1. The descriptor, the instance and the page byte of the
; effect's first fallback page for the instance into the record's cache.
.kind:          move.w  d5,d3
                mulu    #KD_SIZE,d3
                lea     ymxr_kinds(pc),a2
                adda.w  d3,a2                   ; the descriptor
                move.w  d4,d3
                add.w   d3,d3
                move.w  (a2,d3.w),d4            ; KD_AT, the effect's instance
                mulu    #LOOK_KINDS/2,d3        ; page bytes of this effect,
                add.w   d5,d3                   ; one a kind
                .if     YMXR_DEFER
                sf      FX_LBANK(a3)            ; bank 0, or an arming's
                tst.b   WS_ARMING(a0)
                beq.s   .bank
                tst.w   FX_ABANK(a3)
                beq.s   .bank
                add.w   #BANK,d4
                add.w   #4*LOOK_KINDS,d3                  ; bank 1's page bytes
                move.b  #1,FX_LBANK(a3)
.bank:
                .endif
                lea     ymxr_htab(pc),a5
                move.b  (a5,d3.w),FX_LHIGH(a3)
                move.l  a1,d3                   ; a1 kept
                lea     ymxr_tick0(pc),a5
                adda.w  d4,a5                   ; the instance
                move.w  KD_PAGE(a2),d4
                lea     0(a5,d4.w),a1
                move.l  a1,FX_LBYTE(a3)         ; its page byte, cached
; The fill kind, a counted source on setR7 the ports' bits, and the page.
                move.b  KD_FILL(a2),d4
                subq.w  #1,d5
                bne.s   .fill
                cmp.b   #7,FX_TARGET(a3)
                bne.s   .fill
                moveq   #2,d4
.fill:          move.b  d4,FX_LKEY+1(a3)        ; the fill kind, cached
                add.w   d4,d4
                add.w   d4,d4
                movea.l FX_LMAPS(a3,d4.w),a1    ; its table, cached
                move.l  a1,FX_LMAP(a3)
                moveq   #0,d5
                move.b  FX_RUN(a3),d5
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; an arming's captured mapping
                beq.s   .run
                move.b  FX_MAP(a3),d5
.run:
                .endif
                move.b  0(a1,d5.w),d4           ; the page, or 0 for the
                movea.l d3,a1                   ; fallback
                beq.w   .kfall
.kpage:         move.b  FX_LHIGH(a3),d5
                add.b   d4,d5                   ; the page byte
                move.b  d5,FX_LCUR(a3)
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; an arming writes the mirror,
                beq.s   .rec                    ; and at 1 the page alone
                bgt.w   .started
                lea     ARMOFF(a3),a3
.rec:
                .endif
                move.l  DT_ROWS-DT_PAYLOAD(a4),FX_ROWS(a3)  ; R
; One column: the loop cell, under YMXR_PCREL a displacement from the
; place's read at TICK_PTR, as every lookup kind has it. Several: the
; registers the loop below uses kept, the loop cell, the map's row of the
; target, eight bytes from target 14 (SPEC.md 2.1), and the offset of the
; loop cells.
                move.w  KD_COLS(a2),d4
                bne.s   .wide
                .if     YMXR_PCREL
                tst.l   d0
                beq.s   .level
                sub.w   a5,d0
                sub.w   #TICK_PTR,d0
                .endif
                bra.s   .level
.wide:          movem.l d2/d6-d7/a1/a6,-(sp)
                movea.l d0,a6
                moveq   #0,d6
                move.b  FX_TARGET(a3),d6
                lsl.w   #3,d6
                lea     ymxr_wide-(14*8)(pc),a1
                adda.w  d6,a1
                move.w  KD_CELLS(a2),d7
.level:         move.w  sr,-(sp)
                ori.w   #$0700,sr
ymxr_lwindow    equ     *                       ; inside the window: the rig's
                                                ; boundary (inside)
                moveq   #0,d3                   ; the place: row 0, an
                .if     YMXR_DEFER              ; arming's at FX_APLACE, or the
                tst.b   WS_ARMING(a0)           ; row the running handler is
                beq.s   .now                    ; at modulo R of this source,
                move.l  FX_APLACE-ARMOFF(a3),d3 ; out of the record before
                bra.s   .first                  ; the columns below write it
.now:
                .endif
                btst    #5,d2
                bne.s   .first
                move.l  a2,-(sp)
                movea.w d1,a2
                bsr     ymxr_runrow
                move.l  DT_ROWS-DT_PAYLOAD(a4),d4
                bsr     ymxr_rem
                movea.l (sp)+,a2
                move.b  FX_LCUR(a3),d5          ; the page byte again
.first:         move.w  KD_PAGE(a2),d4
                move.b  d5,(a5,d4.w)            ; the page byte
                move.w  KD_COLS(a2),d4
                bne.s   .cols
; One column: the target's register at TICK_SEL and the place at
; TICK_PTR, as every lookup kind has them, the loop cell and, counted,
; the rows from the place on and the rows of a pass through the loop.
                move.b  FX_TARGET(a3),TICK_SEL(a5)
                .if     YMXR_PCREL
                move.w  a4,d4
                add.w   d3,d4                   ; the row the place is at
                sub.w   a5,d4
                sub.w   #TICK_PTR,d4
                move.w  d4,TICK_PTR(a5)
                move.w  KD_CELLS(a2),d4
                move.w  d0,(a5,d4.w)
                .else
                move.l  a4,d4
                add.l   d3,d4                   ; the row the place is at
                move.l  d4,TICK_PTR(a5)
                move.w  KD_CELLS(a2),d4
                move.l  d0,(a5,d4.w)
                .endif
                move.w  KD_LEFT(a2),d4
                beq.s   .one1
                move.w  FX_ROWS+2(a3),d5        ; R less the place
                sub.w   d5,d3
                neg.w   d3
                move.w  d3,(a5,d4.w)
                move.w  d5,d3                   ; R less the row the loop
                tst.l   d0                      ; cell is at, or R where the
                beq.s   .reld                   ; source plays once
                move.w  d0,d4
                .if     YMXR_PCREL
                add.w   a5,d4
                add.w   #TICK_PTR,d4
                .endif
                sub.w   a4,d4
                sub.w   d4,d3
.reld:          move.w  KD_RELD(a2),d4
                move.w  d3,(a5,d4.w)
.one1:          move.l  a4,FX_FIRST(a3)         ; the place at row 0
                move.b  KD_SHAPE(a2),FX_SHAPE(a3)
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; the installer writes an
                bne.s   .armed1                 ; armed start's vector
                .endif
                movea.w d1,a4
                move.l  a5,(a4)                 ; the vector
                move.w  (sp)+,sr
                st      FX_LOK(a3)
                lea     YM_SELECT.w,a2
                rts
                .if     YMXR_DEFER
.armed1:        move.w  (sp)+,sr
                lea     YM_SELECT.w,a2
                rts
                .endif
; Several: a register, a place and a loop cell a column, as a start
; through the identity writes them.
.cols:          swap    d1                      ; the stride
                moveq   #0,d6                   ; four bytes a column
.col:           move.w  KD_SEL(a2,d6.w),d2
                move.b  (a1)+,(a5,d2.w)         ; the register
                moveq   #0,d5
                move.b  (a1)+,d5
                mulu    d1,d5                   ; the column it writes
                add.l   a4,d5                   ; at row 0, kept for the
                move.l  d5,FX_FIRST(a3,d6.w)    ; place's reset
                move.w  KD_PTR(a2,d6.w),d0      ; the column's read
                .if     YMXR_PCREL
                sub.w   a5,d5                   ; row 0 as a displacement
                sub.w   d0,d5                   ; from the read
                move.l  a6,d2                   ; the column's loop cell, or
                beq.s   .none                   ; 0 where the source plays once
                sub.w   a4,d2
                add.w   d5,d2
.none:          move.w  d2,(a5,d7.w)
                add.w   d3,d5                   ; the row the place is at
                move.w  d5,(a5,d0.w)
                .else
                move.l  a6,d2                   ; the column's loop cell, or
                beq.s   .none                   ; 0 where the source plays once
                sub.l   a4,d2
                add.l   d5,d2
.none:          move.l  d2,(a5,d7.w)
                add.l   d3,d5                   ; the row the place is at
                move.l  d5,(a5,d0.w)
                .endif
                addq.w  #4,d6                   ; the next column
                addq.w  #CELL_SIZE,d7
                dbra    d4,.col
                swap    d1
                move.w  KD_LEFT(a2),d4          ; counted: the rows from the
                beq.s   .aim                    ; place on, and the rows of a
                move.w  FX_ROWS+2(a3),d5        ; pass through the loop, R
                move.w  d5,d0                   ; less RR, or R where it plays
                sub.w   d3,d0                   ; once
                move.w  d0,(a5,d4.w)
                move.l  a6,d0
                beq.s   .pass
                sub.w   a4,d0
                sub.w   d0,d5
.pass:          move.w  KD_RELD(a2),d4
                move.w  d5,(a5,d4.w)
; The vector, which the installer writes for an arming, and the shape.
.aim:
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                bne.s   .armed
                .endif
                movea.w d1,a4
                move.l  a5,(a4)                 ; the vector
                st      FX_LOK(a3)
.armed:         move.w  (sp)+,sr
                move.b  KD_SHAPE(a2),FX_SHAPE(a3)
                movem.l (sp)+,d2/d6-d7/a1/a6
.started:       lea     YM_SELECT.w,a2
                rts
; Outside the table: the fallback page of the bank in use, then the
; other, filled where neither has the transformation in the fill kind.
.kfall:         move.b  d5,FX_LKEY(a3)
                move.w  FX_LKEY(a3),d5
                .if     YMXR_DEFER
                moveq   #0,d4
                move.b  FX_LBANK(a3),d4
                moveq   #0,d3
                move.b  FX_USE(a3,d4.w),d3
                add.w   d4,d4
                add.w   d4,d3
                .else
                moveq   #0,d3
                move.b  FX_USE(a3),d3
                .endif
                add.w   d3,d3                   ; four bytes a bank, two a page
                cmp.w   FX_PAGE(a3,d3.w),d5
                beq.s   .found
                eori.w  #2,d3
                cmp.w   FX_PAGE(a3,d3.w),d5
                beq.s   .found
                bsr     ymxr_lfill
.found:         moveq   #2,d4
                and.w   d3,d4
                lsr.w   #1,d4                   ; the page in the bank
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; at 1 the page alone
                bgt.s   .started
                moveq   #0,d5
                move.b  FX_LBANK(a3),d5
                move.b  d4,FX_USE(a3,d5.w)
                .else
                move.b  d4,FX_USE(a3)
                .endif
                move.w  d3,d4
                lsr.w   #1,d4                   ; 2 a bank, and the page,
                add.b   FX_LFB(a3),d4           ; past the effect's pages
                bra.w   .kpage
; A square: the entries of its two keys, the second the marker's.
; ymxr_arm passes no square here, so d1 is the vector the frame writes.
.square:        add.w   d4,d4                   ; the effect's square, out of
                lea     ymxr_at_square(pc),a5   ; the kind's table
                adda.w  (a5,d4.w),a5
                moveq   #0,d0
                move.b  FX_RUN(a3),d0
                bsr     ymxr_tab
                move.b  (a4),d0
                bsr     ymxr_entry
                move.b  d0,d4                   ; row 0's level
                move.b  1(a4),d0
                bsr     ymxr_entry              ; row 1's
                move.b  d4,FX_LEVEL(a3)
                move.b  d4,d3
                eor.b   d0,d3                   ; the difference of the two
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  FX_TARGET(a3),SQ_SEL(a5)
                move.b  d3,SQ_MASK(a5)
                btst    #5,d2                   ; row 0, or the row of the
                bne.s   .row0                   ; two the running handler is
                movea.w d1,a2                   ; at, its row number modulo 2
                bsr     ymxr_runrow             ; (1.8.4)
                btst    #0,d3
                beq.s   .row0
                move.b  d0,d4
.row0:          move.b  d4,SQ_VAL(a5)
                movea.w d1,a2
                move.l  a5,(a2)
                move.w  (sp)+,sr
                move.l  a4,FX_FIRST(a3)
                st      FX_SHAPE(a3)
                sf      FX_LOK(a3)
                lea     YM_SELECT.w,a2
                rts
; A source of one row: its entry. ymxr_arm passes no source of one row
; here, so d1 is the vector the frame writes.
.one:           moveq   #0,d0
                move.b  FX_RUN(a3),d0
                bsr     ymxr_tab
                move.b  (a4),d0
                bsr     ymxr_entry
                add.w   d4,d4                   ; the effect's source of one
                lea     ymxr_at_one(pc),a5      ; row, out of the kind's table
                adda.w  (a5,d4.w),a5
                movea.w d1,a2
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  FX_TARGET(a3),ONE_SEL(a5)
                move.b  d0,ONE_VAL(a5)
                move.l  a5,(a2)
                move.w  (sp)+,sr
                move.l  a4,FX_FIRST(a3)
                move.b  #1,FX_SHAPE(a3)
                sf      FX_LOK(a3)
                lea     YM_SELECT.w,a2
                rts
