; lookup_remap.S - a remap: the mapping of a running source replaced,
; the source moved between identity and lookup handlers, and a pending
; hook moved with it.

; Each timer's select bits in its control register, by effect.
ymxr_sels:      dc.b    $07,$07,$07,$70

; Remap (SPEC.md 1.10.2). Inputs: d1=mapping number 0 to 127, d4=effect,
; a3 its record, d0 its timer's control register, d2=control byte,
; d3=pre-reset timer control when applicable.
; Replace kept. If running, replace its mapping while preserving source,
; place and rate; if stopped, preserve the new mapping for a later start.
;
; The frame switches the page itself where the cache is in force (the
; remap of EFFECT). Here squares and one-row sources replace immediate
; entries, and stepping sources switch the running instance's page
; (ymxr_page) or move between identity and lookup handlers (ymxr_move);
; the record caches a lookup instance. Clobbers d0, d1, d3 to d5 and a3
; to a5; returns a2 at the chip select register.
;
; A pending start retains its captured mapping. First update the record
; for any completed installation, then remap at level 7. Page filling
; alone enables interrupts; if installation occurs during the fill,
; remap the newly running source. Moving handlers also moves the pending
; hook (ymxr_rehook), including a square's mask.
ymxr_remap:     move.b  d1,FX_MAP(a3)           ; the kept mapping
                .if     YMXR_DEFER
                tst.b   FX_PENDING(a3)          ; a start pending: below
                bne.w   .slow
                .endif
                tst.b   d2                      ; the select bits: 0 on a
                bpl.s   .live                   ; stopped timer, and as they
                btst    #6,d2                   ; were before the reset where
                beq.s   .live                   ; the row resets the timer
                move.b  d3,d0
.live:          and.b   ymxr_sels(pc,d4.w),d0
                beq.s   .kept
                cmp.b   FX_RUN(a3),d1           ; the mapping it runs
                bne.s   .moved
.kept:          rts
; Every other remap on a running timer: the running mapping in d7 and the
; new one in the record, and the vector in a6. The frame enters here with
; the kept mapping written, the timer running and no start pending.
.moved:
ymxr_rmove      equ     *
                movem.l d2/d6-d7/a1/a6,-(sp)
                .if     YMXR_DEFER
                move.w  sr,-(sp)
                .endif
                move.w  d4,d6                   ; the effect
                move.b  FX_RUN(a3),d7
                move.b  d1,FX_RUN(a3)
                lsl.w   #3,d4
                lea     ymxr_timers(pc),a6
                movea.w (a6,d4.w),a6            ; the vector
                .if     YMXR_DEFER
                bra.s   .vec
; A start pending: the record follows a switch the tick has made, then
; the timer's select bits as above.
.slow:          movem.l d2/d6-d7/a1/a6,-(sp)
                move.w  sr,-(sp)
                move.w  d4,d6                   ; the effect
                ori.w   #$0700,sr
                moveq   #0,d5                   ; left pending
                bsr.w   ymxr_pend
                bne.s   .on
                move.w  (sp),sr                 ; the tick has performed it
.on:            lea     ymxr_timers(pc),a6
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
                .endif
.vec:           movea.l (a6),a5                 ; the running handler
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
                beq.w   .toid
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2
                move.b  KD_FILL(a2),d5
                cmp.w   #1,d2                   ; a counted source on setR7
                bne.s   .kind                   ; writes the ports' bits: R7
                cmp.b   #7,TICK_SEL(a5)         ; in the running handler, as
                bne.s   .kind                   ; ymxr_kindof reads it
                moveq   #2,d5
.kind:
                .if     YMXR_DEFER
                tst.b   FX_PENDING(a3)
                bne     .pfill
                .endif
                bsr     ymxr_page               ; d0 the page
.held:          moveq   #0,d1                   ; the cache: the fill kind's
                move.b  d5,d1                   ; table,
                add.w   d1,d1
                add.w   d1,d1
                move.l  FX_LMAPS(a3,d1.w),FX_LMAP(a3)
                move.b  d5,FX_LKEY+1(a3)        ; the fill kind and
                .if     YMXR_DEFER              ; the bank, in force with no
                move.b  d4,FX_LBANK(a3)         ; start pending
                tst.b   FX_PENDING(a3)
                seq     FX_LOK(a3)
                .else
                st      FX_LOK(a3)
                .endif
                .if     YMXR_DEFER              ; its page byte: bank, effect,
                move.w  d4,d1                   ; then kind
                lsl.w   #2,d1
                add.w   d6,d1
                .else
                move.w  d6,d1
                .endif
                mulu    #LOOK_KINDS,d1
                add.w   d2,d1
                lea     ymxr_htab(pc),a4
                move.b  (a4,d1.w),d5            ; the first fallback page's
                move.b  d5,FX_LHIGH(a3)
                add.b   d5,d0
                move.w  KD_PAGE(a2),d1
                tst.b   d7
                beq.s   .tolook
                lea     0(a5,d1.w),a4           ; the page byte, cached
                move.l  a4,FX_LBYTE(a3)
                move.b  d0,(a4)                 ; one write, which no tick
                bra     .done                   ; splits
.tolook:        bsr     ymxr_at                 ; the instance, its page byte
                lea     0(a4,d1.w),a1           ; cached, and written before
                move.l  a1,FX_LBYTE(a3)         ; the vector names it
                move.b  d0,(a1)
                movea.l a2,a1                   ; names it
                move.w  d3,d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2                   ; from the identity's handler
                .if     YMXR_DEFER
                move.w  d2,d0
                bsr     ymxr_rehook
                .endif
                bsr     ymxr_move
                bra     .done
.toid:          sf      FX_LOK(a3)              ; the identity's handler
                move.w  d3,d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2
                bsr     ymxr_at                 ; the identity's handler
                movea.l a2,a1
                move.w  d2,d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2                   ; from the lookup instance
                .if     YMXR_DEFER
                move.w  d3,d0
                bsr     ymxr_rehook
                .endif
                bsr     ymxr_move
                bra     .done
                .if     YMXR_DEFER
; Fill with interrupts enabled, then re-read the vector at level 7. If a
; tick installed the pending start meanwhile, update the record and
; remap the new running source.
.pfill:         move.w  (sp),sr
                bsr     ymxr_page
                ori.w   #$0700,sr
                cmpa.l  (a6),a5
                beq     .held
                bsr.w   ymxr_take
                move.w  (sp),sr
                move.b  FX_MAP(a3),d1
                move.b  FX_RUN(a3),d7
                cmp.b   d7,d1
                beq     .done
                move.b  d1,FX_RUN(a3)
                bra     .vec
                .endif
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
                .if     YMXR_DEFER
                move.b  d4,SQ_HMASK(a5)         ; and the hook's copy (4.8)
                .endif
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
.done:
                .if     YMXR_DEFER
                move.w  (sp)+,sr
                .endif
                movem.l (sp)+,d2/d6-d7/a1/a6
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
                add.w   a5,d0                   ; the row, in its low word
                add.w   KD_PTR(a2,d3.w),d0
                sub.w   a4,d0                   ; from the new read
                sub.w   KD_PTR(a1,d3.w),d0
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
                move.w  (a5,d0.w),d2            ; the place, from the old read
                add.w   d0,d2
                add.w   a5,d2
                sub.w   d1,d2                   ; from the new read
                sub.w   a4,d2
                move.w  d2,(a4,d1.w)
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

                .if     YMXR_DEFER
; Move a pending hook when remapping to handler a4, kind d0 in
; ymxr_kinds. Restore the old handler's end-path word, hook a4, and save
; its overwritten word for installation or cancellation. Runs at level 7
; while the vector still selects the old handler. a3=record, d6=effect;
; clobbers d0.
ymxr_rehook:    tst.b   FX_PENDING(a3)
                beq.s   .none
                movem.l d1/a1-a2,-(sp)
                movea.l FX_HOOK(a3),a2
                move.w  FX_HOOKW(a3),(a2)       ; the handler it leaves
                lea     ymxr_hooks(pc),a2
                add.w   d0,d0
                add.w   d0,d0
                move.w  2(a2,d0.w),d1           ; the word the hook writes
                move.w  (a2,d0.w),d0
                lea     (a4,d0.w),a2            ; and where
                lea     ymxr_timers(pc),a1
                move.w  d6,d0
                lsl.w   #3,d0
                adda.w  4(a1,d0.w),a1           ; the effect's installer
                move.w  (a2),FX_HOOKW(a3)
                move.w  (a2),INST_BACKW(a1)
                move.l  a2,INST_BACKA(a1)
                move.l  a2,FX_HOOK(a3)
                move.w  d1,(a2)
                movem.l (sp)+,d1/a1-a2
.none:          rts
                .endif
