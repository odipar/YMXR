; lookup_pages.S - the pages a lookup reads: a fallback page filled and
; selected, a transformation resolved and a page filled from it; and the
; row the running handler is at.

; Fill a fallback page of the lookup instance the record a3 caches with
; interrupts enabled: d3 the offset of the page's two bytes past FX_PAGE,
; four a bank and two a page, and d5 the two bytes, the transformation and
; the fill kind, which the record then lists for the page. The page is the
; instance's page byte, at FX_LBYTE, plus its high byte times 256: FX_LHIGH,
; and 2 more a bank and 1 more for the bank's page 1. Preserves every
; register.
ymxr_lfill:     movem.l d0-d1/d3/a2/a4,-(sp)
                tst.b   FX_LFB(a3)              ; an effect without one: no
                beq.s   .none                   ; page written
                move.w  d5,FX_PAGE(a3,d3.w)
                lsr.w   #1,d3                   ; the page
                add.b   FX_LFB(a3),d3
                add.b   FX_LHIGH(a3),d3
                lsl.w   #8,d3
                movea.l FX_LBYTE(a3),a4         ; the page read's displacement
                adda.w  d3,a4                   ; word, the page past it
                move.w  d5,d0
                lsr.w   #8,d0                   ; the transformation
                bsr     ymxr_tab
                bsr     ymxr_fill
.none:          movem.l (sp)+,d0-d1/d3/a2/a4
                rts

; The page for FX_RUN, effect d6, bank d4 and fill kind d5; a3 is the
; record. The page the table of the fill kind has for the transformation
; (FX_LMAPS); where it has 0, the bank's fallback page in use where it
; matches, and else the other, which the bank then uses, filled where it
; has another transformation or fill kind. Fill only an unused page, with
; interrupts enabled. Return d0 the page in 256 bytes from the effect's
; first fallback page, a fallback page 2 times the bank plus the page in
; the bank; preserve other registers.
;
; While arming, use the row's captured mapping. FX_USE identifies any
; page read by an existing pending start, so filling preserves that
; page.
ymxr_page:      movem.l d1/a4,-(sp)
                moveq   #0,d1
                move.b  FX_RUN(a3),d1
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                beq.s   .run
                move.b  FX_MAP(a3),d1
.run:
                .endif
                moveq   #0,d0                   ; the table of the fill kind
                move.b  d5,d0
                add.w   d0,d0
                add.w   d0,d0
                movea.l FX_LMAPS(a3,d0.w),a4
                move.b  0(a4,d1.w),d0           ; the page, or 0 outside it
                beq.s   .fall
                movem.l (sp)+,d1/a4
                rts
.fall:          move.w  d4,d0
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
.fill:          tst.b   FX_LFB(a3)              ; an effect without one: no
                beq.s   .found                  ; page written
                move.b  d1,(a4,d0.w)
                move.b  d5,1(a4,d0.w)
                movem.l d2/a2,-(sp)
                move.w  d6,d2                   ; the page: the effect's
                add.w   d2,d2                   ; fallback pages past its
                lea     ymxr_refat(pc),a4       ; reference, 512 bytes a bank,
                move.w  (a4,d2.w),d2            ; 256 a page
                lea     ymxr_ref(pc),a4
                adda.w  d2,a4
                moveq   #0,d2
                move.b  FX_LFB(a3),d2
                lsl.l   #8,d2
                adda.l  d2,a4
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
                movem.l (sp)+,d2/a2
.found:         lsr.w   #1,d0
                move.b  d0,FX_USE(a3,d4.w)      ; the bank's page in use
                add.b   d4,d0
                add.b   d4,d0
                add.b   FX_LFB(a3),d0           ; past the effect's pages
                movem.l (sp)+,d1/a4
                rts

; Resolve transformation d0 (1 to 127) into a2: E at offset 0, entry k
; at 2 + k (SPEC.md 3.4.2). Clobbers d0.
ymxr_tab:       movea.l WS_TRANS(a0),a2
                add.w   d0,d0
                add.w   d0,d0
                adda.l  -2(a2,d0.w),a2
                rts

; Map marked byte d0 through table a2: use bits 6 to 0 as the key,
; replace those bits with the entry, and preserve source bit 7. d5 is
; scratch.
ymxr_entry:     moveq   #$7F,d5
                and.b   d0,d5
                and.b   #$80,d0
                or.b    2(a2,d5.w),d0
                rts

; Fill page a4 from transformation a2, according to kind d5 (SPEC.md
; 2.1.5). Plain: copy entries as longs, eight per movem. Marker mirror:
; copy up to 128 entries, then duplicate them at offset 128 with bit 7
; set. setR7: copy keys 0 to 63 at offset 192 with both port bits set.
; Page storage allows the three extra bytes read by a final long.
; Clobbers d0, d1, a2 and a4.
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
