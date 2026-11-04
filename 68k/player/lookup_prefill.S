; lookup_prefill.S - init's fill of the pages the bound tune lists, and
; of each effect's tables.

; The pages of the bound tune's list (BINARIES.md 1.1, 5.6), each filled
; in its form into the page area at a2, which the host provides (3.1,
; 5.1), and each effect's tables into its record. The area begins with a
; table of T bytes, T the transformations, for each effect and form the
; list names, effect 0's first and each effect's in the order of the
; forms: byte n - 1 the page of transformation n, in 256 bytes from the
; effect's reference, and 0 for one outside the pages filled. FX_LMAPS
; has the address 1 below each table. The pages follow the tables: each
; effect's one after another, then its fallback where a page of it is
; left out, at the least offset past the tables that is its offset
; modulo 256 plus 256 bytes for each page of every effect whose offset
; past the tables modulo 256 is lower. The pages filled are the first k
; of the list, the most of them that fit within the reach of the first
; handler with the fallbacks of the effects of the rest. d0 0, or -1 for
; a version 10 tune without a list, an entry out of range, an area of a
; list of pages that is odd, before the player's end or past the reach,
; where the area is left as it was. a6 the workspace.
ymxr_prefill:   movem.l d1-d7/a0-a6,-(sp)
                movea.l a6,a0                   ; the workspace, as ymxr_tab
                lea     WS_FX(a0),a3            ; reads it
                lea     ymxr_nomap-1(pc),a1
                moveq   #3,d0
.none:          clr.l   FX_LMAP(a3)             ; init's counts
                move.l  a1,FX_LMAPS(a3)
                move.l  a1,FX_LMAPS+4(a3)
                move.l  a1,FX_LMAPS+8(a3)
                clr.b   FX_LFB(a3)
                lea     FX_SIZE(a3),a3
                dbra    d0,.none
                move.l  WS_TRANS(a0),d0
                beq     .ok                     ; versions 7 to 9
                movea.l d0,a1                   ; the block: T, then the list
                moveq   #0,d7                   ; past the index and past each
                move.w  (a1),d7                 ; transformation
                beq     .bad
                move.l  d7,d1
                lsl.l   #2,d1
                addq.l  #2,d1
                move.w  d7,d2
                subq.w  #1,d2
                lea     2(a1),a4
.ends:          move.l  (a4)+,d3
                moveq   #0,d4
                move.w  0(a1,d3.l),d4
                add.l   d3,d4
                addq.l  #2,d4
                cmp.l   d4,d1
                bcc.s   .later
                move.l  d4,d1
.later:         dbra    d2,.ends
                lea     0(a1,d1.l),a5
                cmp.b   #'Y',(a5)+              ; the list's tag
                bne     .bad
                cmp.b   #'P',(a5)+
                bne     .bad
                moveq   #0,d6
                move.b  (a5)+,d6                ; N, then its entries
                beq     .ok
                move.l  a2,d0                   ; the area, even and at or
                btst    #0,d0                   ; past the player's end
                bne     .bad
                lea     ymxr_end(pc),a4
                cmpa.l  a4,a2
                bcs     .bad
; Each entry: the pages of its effect, the effect's last entry plus 1 and
; its forms, and Q, the pairs of an effect and a form.
                lea     WS_FX(a0),a3
                movea.l a5,a4
                moveq   #0,d2
                moveq   #0,d4
.count:         bsr     .entry
                beq     .bad
                mulu    #FX_SIZE,d0
                addq.b  #1,FX_LN(a3,d0.w)
                move.b  d2,FX_LLAST(a3,d0.w)
                addq.b  #1,FX_LLAST(a3,d0.w)
                moveq   #0,d1
                move.b  1(a4),d1
                bset    d1,FX_LFORMS(a3,d0.w)
                bne.s   .known
                addq.w  #1,d4
.known:         addq.l  #3,a4
                addq.w  #1,d2
                cmp.w   d6,d2
                bcs.s   .count
; The pages that fit: QT + 255 + 256 X within 32,767 bytes of the first
; handler, X the pages filled and the fallback pages; k the pages filled,
; N where every page fits, and otherwise the most below N.
                lea     ymxr_tick0(pc),a4
                move.l  a4,d0
                add.l   #32767,d0
                sub.l   a2,d0
                move.w  d4,d1
                mulu    d7,d1
                sub.l   d1,d0
                sub.l   #255,d0
                bmi     .bad
                asr.l   #8,d0
                move.l  d0,d3                   ; X at most
                move.l  d6,d5                   ; k
                cmp.l   d3,d5
                bls.s   .fits
.fewer:         subq.w  #1,d5
                bmi     .bad
                moveq   #0,d0                   ; the effects of an entry at
                lea     WS_FX+FX_LLAST(a0),a1   ; or past k
                moveq   #3,d1
.past:          cmp.b   (a1),d5
                bcc.s   .before
                addq.w  #1,d0
.before:        lea     FX_SIZE(a1),a1
                dbra    d1,.past
                mulu    #FB_PAGES,d0
                add.l   d5,d0
                cmp.l   d3,d0
                bhi.s   .fewer
; Each effect's pages in the area: its pages of the first k entries, and
; its fallback where an entry past them is its, marked in FX_LFB.
.fits:          moveq   #3,d0
.zero:          clr.b   FX_LN(a3)
                lea     FX_SIZE(a3),a3
                dbra    d0,.zero
                lea     WS_FX(a0),a3
                movea.l a5,a4
                move.w  d5,d2
                bra.s   .tally
.placed:        moveq   #0,d0
                move.b  (a4),d0
                mulu    #FX_SIZE,d0
                addq.b  #1,FX_LN(a3,d0.w)
                addq.l  #3,a4
.tally:         dbra    d2,.placed
                moveq   #3,d0
.drops:         cmp.b   FX_LLAST(a3),d5
                bcc.s   .whole
                addq.b  #FB_PAGES,FX_LN(a3)
                st      FX_LFB(a3)
.whole:         lea     FX_SIZE(a3),a3
                dbra    d0,.drops
; The tables, each 0 to its end, and their pointers.
                movea.l a2,a1
                lea     WS_FX(a0),a3
                moveq   #3,d0
.tables:        moveq   #0,d1                   ; the form
.form:          btst    d1,FX_LFORMS(a3)
                beq.s   .untabled
                move.w  d1,d2
                add.w   d2,d2
                add.w   d2,d2
                lea     -1(a1),a4
                move.l  a4,FX_LMAPS(a3,d2.w)
                move.w  d7,d2
                subq.w  #1,d2
.clear:         clr.b   (a1)+
                dbra    d2,.clear
.untabled:      addq.w  #1,d1
                cmp.w   #3,d1
                bcs.s   .form
                lea     FX_SIZE(a3),a3
                dbra    d0,.tables
                move.w  d5,d7                   ; k, past the tables
; Each effect's offset past the tables modulo 256, in FX_LRES.
                move.l  a1,d4                   ; the first byte past them
                lea     ymxr_ref(pc),a4
                lea     ymxr_refat(pc),a1
                lea     WS_FX(a0),a3
                moveq   #3,d0
.residue:       move.l  a4,d1
                add.w   (a1)+,d1
                sub.b   d4,d1
                move.b  d1,FX_LRES(a3)
                lea     FX_SIZE(a3),a3
                dbra    d0,.residue
; Each effect's pages, their places in its tables, and its fallback.
                lea     WS_FX(a0),a3
                moveq   #0,d3                   ; the effect
.effect:        tst.b   FX_LN(a3)
                beq     .next
                moveq   #0,d0
                move.b  FX_LRES(a3),d0          ; its offset, and the pages of
                moveq   #0,d1                   ; the effects at lower ones
                lea     WS_FX(a0),a1
                moveq   #3,d2
.low:           cmp.b   FX_LRES(a1),d0
                bls.s   .lower
                add.b   FX_LN(a1),d1
.lower:         lea     FX_SIZE(a1),a1
                dbra    d2,.low
                lsl.l   #8,d1
                add.l   d4,d1
                add.l   d0,d1
                movea.l d1,a6                   ; its first page
                move.w  d3,d0
                add.w   d0,d0
                lea     ymxr_refat(pc),a2
                move.w  0(a2,d0.w),d0
                lea     ymxr_ref(pc),a2
                adda.w  d0,a2                   ; its reference
                sub.l   a2,d1
                asr.l   #8,d1
                move.b  d1,d5                   ; the first page past it
                movea.l a5,a4
                move.w  d7,d2
                bra.s   .scanned
.scan:          moveq   #0,d0
                move.b  (a4),d0
                cmp.w   d3,d0
                bne.s   .pass
                moveq   #0,d1
                move.b  2(a4),d1                ; its transformation, in the
                moveq   #0,d0                   ; table of its form
                move.b  1(a4),d0
                add.w   d0,d0
                add.w   d0,d0
                movea.l FX_LMAPS(a3,d0.w),a1
                move.b  d5,0(a1,d1.w)           ; the page
                movem.l d2/d5/a4,-(sp)
                moveq   #0,d5
                move.b  1(a4),d5                ; the fill kind
                move.w  d1,d0
                bsr     ymxr_tab
                movea.l a6,a4
                bsr     ymxr_fill
ymxr_filled     equ     *                       ; past a page's copy: the rig's
                movem.l (sp)+,d2/d5/a4          ; boundary
                lea     256(a6),a6
                addq.b  #1,d5
.pass:          addq.l  #3,a4
.scanned:       dbra    d2,.scan
                tst.b   FX_LFB(a3)
                beq.s   .next
                move.b  d5,FX_LFB(a3)           ; its fallback, past its pages
.next:          lea     FX_SIZE(a3),a3
                addq.w  #1,d3
                cmp.w   #4,d3
                bcs     .effect
.ok:            lea     WS_FX(a0),a3
                lea     ymxr_nomap-1(pc),a1
                moveq   #3,d0
.cached:        move.l  a1,FX_LMAP(a3)
                lea     FX_SIZE(a3),a3
                dbra    d0,.cached
                moveq   #0,d0
                movem.l (sp)+,d1-d7/a0-a6
                rts
.bad:           moveq   #-1,d0
                movem.l (sp)+,d1-d7/a0-a6
                rts
; The list's entry at a4: d0 its effect, d1 its transformation, and Z with
; d1 0 where the effect, the form or the transformation is out of range.
.entry:         moveq   #0,d0
                moveq   #0,d1
                move.b  (a4),d0
                cmp.b   #3,d0
                bhi.s   .out
                cmp.b   #2,1(a4)
                bhi.s   .out
                move.b  2(a4),d1
                cmp.w   d7,d1
                bls.s   .in
.out:           moveq   #0,d1
.in:            tst.w   d1
                rts
