; cut.S - a source under a cut (SPEC.md 1.11): the rows of the running
; cut, the loop of the counted handler under it, and a start, a recut and
; a place's reset under it. YMXR.S includes it where YMXR_MAP is 1.
;
; A start or a recut under a cut 1 to 127 runs the counted kind of the
; source's columns, through the identity or through the mapping, whose
; counter of rows left ends a pass at the cut's last row L, and whose loop
; cells are at the cut's loop row (TICKC and TICKW, ticks.S; the lookup's
; counted kinds, lookup_ticks.S). It writes each byte of a marked source
; whole, its marker bit included, and through a mapping reads the page of
; the marked form, and the record has the shape of a marked source on a
; counted kind at 7 to 9, of one to three columns (equates.S).

; The routines are the piece cut_1 of the cut's block, which a link keeps
; where it keeps an effect's cut piece (doc/LINKER.md).
                PIECE   cut_1

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

; The running source's first row in a4, out of the record a3: the lowest
; of its columns' rows 0, FX_FIRST to FX_FIRST3 by the shape, since a
; handler of several columns writes each column once, in the order of
; the target's map. d0 clobbered.
ymxr_xhead:     movea.l FX_FIRST(a3),a4
                moveq   #0,d0
                move.b  FX_SHAPE(a3),d0
                bmi.s   .one                    ; a square
                move.b  .cols(pc,d0.w),d0       ; the columns less 1
                beq.s   .one
                cmpa.l  FX_FIRST2(a3),a4
                bls.s   .two
                movea.l FX_FIRST2(a3),a4
.two:           subq.b  #1,d0
                beq.s   .one
                cmpa.l  FX_FIRST3(a3),a4
                bls.s   .one
                movea.l FX_FIRST3(a3),a4
.one:           rts
.cols:          dc.b    0,0,1,2,0,1,2,0,1,2     ; by FX_SHAPE, 0 to 9

; The loop of the cut F to L, d0 and d1, of the source whose first row is
; a4 (SPEC.md 1.11.4): in d3 the loop row, row RR where F to L includes it
; and row F where it excludes it, and -1 where the source plays once; in
; d6 the rows of a pass, L + 1 less the loop row.
ymxr_xpass:     move.l  DT_REPEAT-DT_PAYLOAD(a4),d3     ; RR
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
                rts
.once:          moveq   #-1,d3
                moveq   #1,d6
                rts

; The operands of a counted kind under a cut, at level 7: in the handler
; a5, which the descriptor a2 describes, each column's place at row d3
; and its loop cell at row d5, 0 where d5 is -1, past the column's row 0
; in the record a3 (FX_FIRST, FX_FIRST2, FX_FIRST3); the rows left, d1
; less d3 plus 1, and the rows of a pass, d6. Under YMXR_PCREL a place
; and a cell are displacements from the column's read.
ymxr_xput:      movem.l d0-d2/d4/d6-d7/a1-a2,-(sp)
                sub.l   d3,d1
                addq.l  #1,d1
                move.w  KD_LEFT(a2),d0
                move.w  d1,(a5,d0.w)            ; the rows left
                move.w  KD_RELD(a2),d0
                move.w  d6,(a5,d0.w)            ; the rows of a pass
                move.w  KD_COLS(a2),d7
                move.w  KD_CELLS(a2),d4
                lea     FX_FIRST(a3),a1
.col:           move.l  (a1)+,d2                ; the column's row 0
                moveq   #0,d0
                move.w  KD_PTR(a2),d0           ; its read
                moveq   #0,d6
                tst.l   d5
                bmi.s   .cell                   ; 0 where it plays once
                move.l  d2,d6
                add.l   d5,d6                   ; the loop row
                .if     YMXR_PCREL
                sub.l   a5,d6
                sub.l   d0,d6
                .endif
.cell:
                .if     YMXR_PCREL
                move.w  d6,(a5,d4.w)
                .else
                move.l  d6,(a5,d4.w)
                .endif
                add.l   d3,d2                   ; the place
                .if     YMXR_PCREL
                sub.l   a5,d2
                sub.l   d0,d2
                move.w  d2,(a5,d0.w)
                .else
                move.l  d2,(a5,d0.w)
                .endif
                addq.l  #4,a2
                addq.w  #CELL_SIZE,d4
                dbra    d7,.col
                movem.l (sp)+,d0-d2/d4/d6-d7/a1-a2
                rts

; The fill kind of the lookup kind d2 on the record's shape d0, d1 the
; first register: on a marked source, shape 7 to 9, the marker's mirror
; where the kind maps the marker's column, as the marked kind of its
; columns reads; on a counted one the plain page, and the ports' bits on
; setR7 (ymxr_lstart). Returns d5.
ymxr_xfill:     moveq   #0,d5
                cmp.b   #7,d0
                bcs.s   .counted
                move.b  .mirror(pc,d2.w),d5
                rts
.counted:       cmp.w   #1,d2
                bne.s   .done
                cmp.b   #7,d1
                bne.s   .done
                moveq   #2,d5
.done:          rts
.mirror:        dc.b    0,1,0,0,0,0,1,0,1,0     ; lc, le and lg map the marker

; The page byte of the lookup instance a5 of kind d2, which the descriptor
; a2 describes, in bank d4 of effect d6, for the fill kind d5: the page
; ymxr_page finds or fills, past the page byte of the instance's first
; fallback page, in d0 and FX_LCUR; and the record's cache, which a remap
; between two transformations reads (equates.S). a3 the record.
ymxr_xpage:     movem.l d1/a4,-(sp)
                bsr     ymxr_page               ; d0 the page
                moveq   #0,d1
                move.b  d5,d1
                add.w   d1,d1
                add.w   d1,d1
                move.l  FX_LMAPS(a3,d1.w),FX_LMAP(a3)
                move.b  d5,FX_LKEY+1(a3)
                .if     YMXR_DEFER
                move.b  d4,FX_LBANK(a3)
                move.w  d4,d1
                lsl.w   #2,d1
                add.w   d6,d1
                .else
                move.w  d6,d1
                .endif
                mulu    #LOOK_KINDS,d1
                add.w   d2,d1
                lea     ymxr_htab(pc),a4
                move.b  (a4,d1.w),d1
                move.b  d1,FX_LHIGH(a3)
                add.b   d1,d0                   ; the page byte
                move.b  d0,FX_LCUR(a3)
                move.w  KD_PAGE(a2),d1
                lea     (a5,d1.w),a4
                move.l  a4,FX_LBYTE(a3)
                movem.l (sp)+,d1/a4
                rts

; A start under a cut 1 to 127 (SPEC.md 4.3 step 3): the counted kind of
; the source's columns, through the identity or through the mapping,
; aimed at the cut's rows with the kept target. Its place is row F of the
; cut where the row sets bit 5, and otherwise the row the running handler
; is at, modulo R of this source, and row F where that row is outside the
; cut; its counter and loop are ymxr_xput's. At the frame the running cut
; is the kept cut (_xnow), the mapping the running one and the handler
; bank 0's, and the vector is aimed in the window that reads the place.
; With WS_ARMING set the start is the armed one (4.8): its cut FX_CUT,
; its mapping FX_MAP, its place FX_APLACE and its bank FX_ABANK, written
; into the record's mirror, and the installer aims the vector. d1 the
; source, d2 the control byte, d4 the effect. Returns a5 the handler, a3
; the record and a2 at the chip's select register; clobbers d0, d1, d3 to
; d5 and a4.
XS_SHAPE        equ     -2                      ; the shape, 4 to 9
XS_KIND         equ     -4                      ; the kind, in ymxr_kinds
XS_STRIDE       equ     -6                      ; the stride of the columns
XS_PAGE         equ     -8                      ; the page byte
XS_VECTOR       equ     -10                     ; the vector's address
ymxr_kstart:    movem.l d2/d6-d7/a1/a6,-(sp)
                link    a6,#-10
                move.w  d4,d6                   ; the effect
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; its record
                lea     ymxr_timers(pc),a2
                move.w  d6,d0
                lsl.w   #3,d0
                move.w  (a2,d0.w),XS_VECTOR(a6)
                lsl.w   #3,d1
                movea.l WS_SOURCES-8(a0,d1.w),a4    ; the source's first row
                move.l  WS_SOURCES-4(a0,d1.w),d0    ; its cell, or its tag
                lea     WS_WIDE(a0),a2
                move.w  -4(a2,d1.w),XS_STRIDE(a6)
; The shape: 4 to 6 a counted source of one to three columns, 7 to 9 a
; marked one, by the tag init wrote (init.S).
                moveq   #7,d4
                tst.l   d0
                bpl.s   .shaped                 ; marked, of one column
                neg.l   d0
                subq.w  #3,d0
                bmi.s   .shaped                 ; a square, one row
                cmp.w   #2,d0
                bcc.s   .counted
                add.w   d0,d4                   ; marked, of two or three
                addq.w  #1,d4
                bra.s   .shaped
.counted:       subq.w  #3,d4                   ; counted, of one to three
                subq.w  #2,d0
                add.w   d0,d4
.shaped:        move.w  d4,XS_SHAPE(a6)
; The kind: the identity's counted kind of the columns, or the lookup's,
; by the target where the source has several (ymxr_kindof).
                moveq   #0,d0
                move.b  FX_RUN(a3),d0
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                beq.s   .run
                move.b  FX_MAP(a3),d0
.run:
                .endif
                move.w  d4,d7
                subq.w  #4,d7
                cmp.w   #3,d7
                bcs.s   .cols
                subq.w  #3,d7                   ; the columns less 1
.cols:          moveq   #0,d5
                tst.b   d0
                bne.s   .look
                move.b  .ident(pc,d7.w),d5
                bra.s   .kind
.look:          move.b  .lookup(pc,d7.w),d5
                cmp.w   #1,d7
                bcs.s   .kind                   ; one column
                bne.s   .three
                cmp.b   #22,FX_TARGET(a3)       ; setNoiseX maps its first
                bcs.s   .kind                   ; write
                addq.w  #1,d5
                bra.s   .kind
.three:         cmp.b   #21,FX_TARGET(a3)       ; setVoiceX its middle one
                beq.s   .kind
                addq.w  #1,d5
                bra.s   .kind
.ident:         dc.b    11,14,15,0              ; cnt, env, cwide
.lookup:        dc.b    1,6,8,0                 ; lc, le, lg
.kind:          move.w  d5,XS_KIND(a6)
                move.w  d5,d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2                   ; its descriptor
                move.w  d6,d0
                add.w   d0,d0
                move.w  (a2,d0.w),d0            ; KD_AT, the effect's instance
                lea     ymxr_tick0(pc),a5
                adda.w  d0,a5
                moveq   #0,d4                   ; in bank 0, or the arming's
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                beq.s   .bank
                tst.w   FX_ABANK(a3)
                beq.s   .bank
                adda.w  #BANK,a5
                moveq   #1,d4
.bank:
                .endif
                sf      FX_LOK(a3)
                cmp.w   #LOOK_KINDS,d5
                bcc.s   .paged                  ; through the identity
                move.l  d2,-(sp)
                move.w  d5,d2
                move.w  XS_SHAPE(a6),d0
                move.b  FX_TARGET(a3),d1
                bsr     ymxr_xfill
                bsr     ymxr_xpage
                move.b  d0,XS_PAGE(a6)
                move.l  (sp)+,d2
; The rows: F and L of the cut, and the cut's loop.
.paged:         moveq   #0,d0
                move.b  FX_CRUN(a3),d0
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                beq.s   .cut
                move.b  FX_CUT(a3),d0
.cut:
                .endif
                bsr     ymxr_xrows              ; F and L
                bsr     ymxr_xpass              ; the loop row and a pass
                move.l  d3,d5
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.l  d0,d3                   ; row F where the row resets
                .if     YMXR_DEFER              ; the place, or the arming's
                tst.b   WS_ARMING(a0)
                beq.s   .now
                move.l  FX_APLACE(a3),d3
                lea     ARMOFF(a3),a3           ; into the mirror
                bra.s   .placed
.now:
                .endif
                btst    #5,d2
                bne.s   .placed
                movem.l d0/d5/a2,-(sp)
                movea.w XS_VECTOR(a6),a2
                bsr     ymxr_runrow             ; the running row
                move.l  DT_ROWS-DT_PAYLOAD(a4),d4
                bsr     ymxr_rem                ; modulo R
                movem.l (sp)+,d0/d5/a2
                cmp.l   d0,d3
                bcs.s   .outside
                cmp.l   d1,d3
                bls.s   .placed
.outside:       move.l  d0,d3
; Each column's register and row 0, out of the target's row of the map
; where the source has several (ymxr_wide), then ymxr_xput.
.placed:        tst.w   d7
                bne.s   .several
                move.b  FX_TARGET(a3),TICK_SEL(a5)
                move.l  a4,FX_FIRST(a3)
                bra.s   .put
.several:       moveq   #0,d0
                move.b  FX_TARGET(a3),d0
                sub.w   #14,d0
                lsl.w   #3,d0
                lea     ymxr_wide(pc),a1
                adda.w  d0,a1
                moveq   #0,d2                   ; four bytes a column
.col:           move.w  KD_SEL(a2,d2.w),d0
                move.b  (a1)+,(a5,d0.w)         ; the register
                moveq   #0,d0
                move.b  (a1)+,d0                ; the column it writes
                mulu    XS_STRIDE(a6),d0        ; by the stride
                add.l   a4,d0
                move.l  d0,FX_FIRST(a3,d2.w)
                addq.w  #4,d2
                dbra    d7,.col
.put:           bsr     ymxr_xput
                cmp.w   #LOOK_KINDS,XS_KIND(a6)
                bcc.s   .aim
                move.w  KD_PAGE(a2),d0
                move.b  XS_PAGE(a6),(a5,d0.w)   ; the page byte
.aim:
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                bne.s   .armed
                .endif
                movea.w XS_VECTOR(a6),a1
                move.l  a5,(a1)                 ; the vector
                cmp.w   #LOOK_KINDS,XS_KIND(a6) ; the cache of a lookup
                scs     FX_LOK(a3)              ; instance in force
.armed:         move.w  (sp)+,sr
                move.l  DT_ROWS-DT_PAYLOAD(a4),FX_ROWS(a3)
                move.b  XS_SHAPE+1(a6),FX_SHAPE(a3)
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                beq.s   .record
                lea     -ARMOFF(a3),a3          ; the record, past its mirror
.record:
                .endif
                unlk    a6
                movem.l (sp)+,d2/d6-d7/a1/a6
                lea     YM_SELECT.w,a2
                rts

; A recut on a running timer (SPEC.md 4.3.2): the kept cut runs, and the
; running source moves to the counted kind of its columns in the running
; bank, through its running mapping, with its running registers, at the
; row the running handler is at where the cut includes it and at row F
; where it excludes it; its counter and loop are ymxr_xput's. Under cut 0
; the rows are 0 to R - 1, and a recut from cut 0 to cut 0 leaves the
; handler as it is; a timer before the first start has no source to cut.
; With a start pending after the record's settling (ymxr_pend) the recut
; is at level 7 to the vector's write, a page's fill among it, so the
; switch is before the recut or after it, and the hook (ymxr_rehook) and
; the armed place (ymxr_xplace) follow the new cut. d4 the effect.
; Clobbers d0, d1, d4, d5 and a3 to a5.
XR_EFFECT       equ     -2                      ; the effect
XR_VECTOR       equ     -4                      ; its vector's address
XR_OLD          equ     -5                      ; the running shape
XR_SHAPE        equ     -6                      ; the counted kind's shape
XR_KIND         equ     -8                      ; the counted kind
XR_RUNNING      equ     -10                     ; the running kind, or -1
XR_PAGE         equ     -12                     ; the page byte
XR_BANK         equ     -14                     ; the running bank, 0 or 1
ymxr_recut:     movem.l d2-d3/d6-d7/a1-a2/a6,-(sp)
                move.w  sr,-(sp)
                link    a6,#-14
                move.w  d4,XR_EFFECT(a6)
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; its record
                lea     ymxr_park(pc),a4
                cmpa.l  FX_FIRST(a3),a4
                beq.w   .kept
                .if     YMXR_DEFER
                tst.b   FX_PENDING(a3)
                beq.s   .settled
                ori.w   #$0700,sr
                move.w  XR_EFFECT(a6),d4
                moveq   #0,d5
                bsr.w   ymxr_pend
                bne.s   .settled                ; still pending: level 7
                move.w  4(a6),sr
.settled:
                .endif
                move.b  FX_CRUN(a3),d0
                or.b    FX_CUT(a3),d0
                beq.w   .kept                   ; cut 0 to cut 0
                move.b  FX_CUT(a3),FX_CRUN(a3)  ; the kept cut runs
                lea     ymxr_timers(pc),a4
                move.w  XR_EFFECT(a6),d0
                lsl.w   #3,d0
                move.w  (a4,d0.w),XR_VECTOR(a6)
                movea.w XR_VECTOR(a6),a4
                movea.l (a4),a4                 ; the running handler
                clr.w   XR_BANK(a6)
                .if     YMXR_DEFER
                lea     ymxr_bank1(pc),a5
                cmpa.l  a5,a4
                bcs.s   .bank
                move.w  #1,XR_BANK(a6)
.bank:
                .endif
; The counted kind's shape, a marked source's 7 to 9 and a counted one's
; as it is, and its kind by ymxr_kindof on that shape; the running kind,
; where the running handler steps a place, for its registers.
                moveq   #0,d0
                move.b  FX_SHAPE(a3),d0
                move.b  d0,XR_OLD(a6)
                moveq   #7,d2                   ; a square
                tst.b   d0
                bmi.s   .to
                move.b  .tos(pc,d0.w),d2
                bra.s   .to
.tos:           dc.b    7,7,8,9,4,5,6,7,8,9
.to:            move.b  d2,XR_SHAPE(a6)
                move.b  d2,FX_SHAPE(a3)
                movea.l a4,a5
                bsr     ymxr_kindof
                tst.b   FX_RUN(a3)
                bne.s   .k1
                move.w  d1,d0
.k1:            move.w  d0,XR_KIND(a6)
                move.b  XR_OLD(a6),FX_SHAPE(a3)
                move.w  #-1,XR_RUNNING(a6)
                move.b  XR_OLD(a6),d0
                bmi.s   .k0                     ; a square, and one row, have
                cmp.b   #1,d0                   ; one register at its select
                beq.s   .k0
                bsr     ymxr_kindof
                tst.b   FX_RUN(a3)
                bne.s   .k0l
                move.w  d1,d0
.k0l:           move.w  d0,XR_RUNNING(a6)
.k0:            move.w  XR_KIND(a6),d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2                   ; the descriptor
                move.w  XR_EFFECT(a6),d0
                add.w   d0,d0
                move.w  (a2,d0.w),d0
                lea     ymxr_tick0(pc),a5
                adda.w  d0,a5                   ; the instance
                .if     YMXR_DEFER
                tst.w   XR_BANK(a6)
                beq.s   .inst
                adda.w  #BANK,a5
.inst:
                .endif
                move.b  TICK_SEL(a4),d7         ; the first register: at
                move.b  XR_OLD(a6),d0           ; TICK_SEL where the handler
                bpl.s   .one                    ; steps a place, and at the
                move.b  SQ_SEL(a4),d7           ; select of a square and of
                bra.s   .rsel                   ; one row
.one:           cmp.b   #1,d0
                bne.s   .rsel
                move.b  ONE_SEL(a4),d7
; Through a mapping, the page of the source's form (ymxr_xpage), which
; the running instance reads where it is a lookup kind.
.rsel:          cmp.w   #LOOK_KINDS,XR_KIND(a6)
                bcc.s   .paged
                move.w  XR_KIND(a6),d2
                moveq   #0,d0
                move.b  XR_SHAPE(a6),d0
                move.b  d7,d1
                bsr     ymxr_xfill
                move.w  XR_BANK(a6),d4
                move.w  XR_EFFECT(a6),d6
                bsr     ymxr_xpage
                move.w  d0,XR_PAGE(a6)
; The rows: F and L of the cut, R - 1 under cut 0, and the cut's loop.
.paged:         move.l  a4,-(sp)                ; the running handler
                bsr     ymxr_xhead              ; the running source
                move.l  DT_ROWS-DT_PAYLOAD(a4),FX_ROWS(a3)
                moveq   #0,d0
                move.l  DT_ROWS-DT_PAYLOAD(a4),d1
                subq.l  #1,d1
                tst.b   FX_CRUN(a3)
                beq.s   .rows
                bsr.w   ymxr_xcut
.rows:          bsr.w   ymxr_xpass              ; the loop row and a pass
                move.l  d3,d5
                movea.l (sp)+,a4
                ori.w   #$0700,sr
                movem.l d0/d5/a2,-(sp)
                movea.w XR_VECTOR(a6),a2
                bsr     ymxr_runrow             ; the running row
                movem.l (sp)+,d0/d5/a2
                cmp.l   d0,d3                   ; the place: the running row
                bcs.s   .outside                ; where the cut includes it,
                cmp.l   d1,d3                   ; and row F where it excludes
                bls.s   .placed                 ; it
.outside:       move.l  d0,d3
; The registers: the first, or each column's out of the running kind's.
.placed:        move.w  KD_COLS(a2),d4
                bne.s   .several
                move.b  d7,TICK_SEL(a5)
                bra.s   .put
.several:       move.w  XR_RUNNING(a6),d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a1
                adda.w  d0,a1                   ; the running kind's
                moveq   #0,d2                   ; four bytes a column
.col:           move.w  KD_SEL(a1,d2.w),d0
                move.b  (a4,d0.w),d7            ; its register
                move.w  KD_SEL(a2,d2.w),d0
                move.b  d7,(a5,d0.w)
                addq.w  #4,d2
                dbra    d4,.col
.put:           bsr     ymxr_xput
                cmp.w   #LOOK_KINDS,XR_KIND(a6)
                bcc.s   .id
                move.w  KD_PAGE(a2),d0
                move.b  XR_PAGE+1(a6),(a5,d0.w) ; the page byte
.id:
                .if     YMXR_DEFER
                tst.b   FX_PENDING(a3)
                beq.s   .aim
                movea.l a5,a4                   ; the hook on the counted
                move.w  XR_EFFECT(a6),d6        ; kind
                move.w  XR_KIND(a6),d0
                bsr.w   ymxr_rehook
                bsr.w   ymxr_xplace             ; the armed place
.aim:
                .endif
                movea.w XR_VECTOR(a6),a1
                move.l  a5,(a1)                 ; the vector
                move.b  XR_SHAPE(a6),FX_SHAPE(a3)
                sf      FX_LOK(a3)
                cmp.w   #LOOK_KINDS,XR_KIND(a6) ; a lookup instance's cache,
                bcc.s   .kept                   ; in force with no start
                .if     YMXR_DEFER              ; pending
                tst.b   FX_PENDING(a3)
                bne.s   .kept
                .endif
                st      FX_LOK(a3)
.kept:          unlk    a6
                move.w  (sp)+,sr
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
                move.l  FX_ROWS+ARMOFF(a3),d4
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
                ENDPIECE cut_1
