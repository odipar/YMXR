; lookup_kinds.S - the kinds of the handlers that step a place: a running
; handler's kind, an instance's address, and the tables of kinds, hooks
; and fallback pages.

; Resolve the running shape's lookup kind into d0 and identity kind into
; d1. a3=record, a5=running handler. Lookup selects the target's last
; value, which write order places first for noise, last for
; tone/envelope/buzzer, and in the middle for voice (SPEC.md 1.10.1).
;
; Use the running handler's first register at TICK_SEL, rather than the
; kept target for a later start. R8 to R10 identify noise versus
; tone/envelope; R11 identifies buzzer versus voice (2.1). Applies to
; marked and counted sources.
ymxr_kindof:    moveq   #0,d0
                move.b  FX_SHAPE(a3),d0
                moveq   #0,d1
                move.b  .ident(pc,d0.w),d1
                move.b  .look(pc,d0.w),d0
                cmp.b   #1,d0                   ; one column
                bls.s   .kind
                cmp.b   #4,d0
                beq.s   .three
.two:           cmp.b   #8,TICK_SEL(a5)
                bcs.s   .kind
                cmp.b   #11,TICK_SEL(a5)
                beq.s   .kind
                addq.w  #1,d0                   ; R8 to R10: the lookup first
                rts
.three:         cmp.b   #11,TICK_SEL(a5)
                beq.s   .kind
                moveq   #5,d0
.kind:          rts
.look:          dc.b    0,0,2,4,1,6             ; by FX_SHAPE, 0 to 5
.ident:         dc.b    8,0,10,11,9,12

; a4 the handler of the kind a2 describes, of effect d6, in bank d4. d5
; clobbered.
ymxr_at:        move.w  d6,d5
                add.w   d5,d5
                move.w  (a2,d5.w),d5            ; KD_AT, the effect's instance
                lea     ymxr_tick0(pc),a4
                adda.w  d5,a4
                .if     YMXR_DEFER
                tst.w   d4
                beq.s   .bank
                adda.w  #BANK,a4
.bank:
                .endif
                rts

                .if     KD_AT<>0
                .error  "a kind's descriptor has its instances past its first word"
                .endif

; a5 the instance of effect d2 of the kind a1 describes, in bank d6 (the
; bank's offset), out of the descriptor's KD_AT; d0 the effect times 2.
; d1 clobbered.
ymxr_inst:      move.w  d2,d0
                add.w   d0,d0
                move.w  (a1,d0.w),d1            ; KD_AT
                lea     ymxr_tick0(pc),a5
                adda.w  d1,a5
                adda.w  d6,a5
                rts

; The page byte of page 0 of an instance, a3 its page byte, d0 its
; effect times 2: the page byte the effect's reference is at from the
; page read's base, 256 bytes a page, written there and at (a2), the
; page-byte table's entry. d1 and a5 clobbered.
ymxr_page0:     lea     ymxr_refat(pc),a5
                move.w  (a5,d0.w),d1            ; the effect's reference,
                lea     ymxr_ref(pc),a5         ; past ymxr_ref
                adda.w  d1,a5
                move.l  a5,d1
                sub.l   a3,d1
                asr.l   #8,d1
                move.b  d1,(a3)
                move.b  d1,(a2)
                rts

; Stepping-handler kinds: eight lookup kinds in page-byte-table order,
; then five identity kinds.
ymxr_kinds:     KIND    ymxr_lk,1,LK_CELL,0,0,LK_PAGE,0,1,LK_SEL,LK_PTR,0,0,0,0
                KIND    ymxr_lc,1,LC_CELL,LC_LEFT,LC_RELD,LC_PAGE,4,0,LC_SEL,LC_PTR,0,0,0,0
                KIND    ymxr_lt,2,LT_CELLS,0,0,LT_PAGE,2,1,LT_SEL1,LT_PTR1,LT_SEL2,LT_PTR2,0,0
                KIND    ymxr_ln,2,LN_CELLS,0,0,LN_PAGE,2,0,LN_SEL1,LN_PTR1,LN_SEL2,LN_PTR2,0,0
                KIND    ymxr_lb,3,LB_CELLS,0,0,LB_PAGE,3,1,LB_SEL1,LB_PTR1,LB_SEL2,LB_PTR2,LB_SEL3,LB_PTR3
                KIND    ymxr_lv,3,LV_CELLS,0,0,LV_PAGE,3,0,LV_SEL1,LV_PTR1,LV_SEL2,LV_PTR2,LV_SEL3,LV_PTR3
                KIND    ymxr_le,2,LE_CELLS,LE_LEFT,LE_RELD,LE_PAGE,5,0,LE_SEL1,LE_PTR1,LE_SEL2,LE_PTR2,0,0
                KIND    ymxr_lf,2,LF_CELLS,LF_LEFT,LF_RELD,LF_PAGE,5,0,LF_SEL1,LF_PTR1,LF_SEL2,LF_PTR2,0,0
                KIND    ymxr_tick,1,TICK_CELL,0,0,0,0,0,TICK_SEL,TICK_PTR,0,0,0,0
                KIND    ymxr_cnt,1,TICKC_CELL,TICKC_LEFT,TICKC_RELD,0,4,0,TICKC_SEL,TICKC_PTR,0,0,0,0
                KIND    ymxr_two,2,TW2_CELLS,0,0,0,2,0,TW2_SEL1,TW2_PTR1,TW2_SEL2,TW2_PTR2,0,0
                KIND    ymxr_three,3,TW3_CELLS,0,0,0,3,0,TW3_SEL1,TW3_PTR1,TW3_SEL2,TW3_PTR2,TW3_SEL3,TW3_PTR3
                KIND    ymxr_env,2,TWC_CELLS,TWC_LEFT,TWC_RELD,0,5,0,TWC_SEL1,TWC_PTR1,TWC_SEL2,TWC_PTR2,0,0
                .if     YMXR_DEFER
; The hook of each kind in the order above (SPEC.md 4.8): the offset of
; the head of the path that ends a source, and the word a hook writes
; there.
ymxr_hooks:     dc.w    LK_LAST,LK_HOOKW,LC_LAST,LC_HOOKW,LT_LAST,LT_HOOKW
                dc.w    LN_LAST,LN_HOOKW,LB_LAST,LB_HOOKW,LV_LAST,LV_HOOKW
                dc.w    LE_LAST,LE_HOOKW,LF_LAST,LF_HOOKW
                dc.w    TICK_LAST,TICK_HOOKW,TICKC_LAST,TICKC_HOOKW
                dc.w    TW2_LAST,TW2_HOOKW,TW3_LAST,TW3_HOOKW
                dc.w    TWC_LAST,TWC_HOOKW
                .endif

; Each effect's reference past ymxr_ref, a word an effect, which a link
; writes with the reference it picks (doc/LINKER.md).
ymxr_refat:     dc.w    ymxr_ref0-ymxr_ref,ymxr_ref1-ymxr_ref
                dc.w    ymxr_ref2-ymxr_ref,ymxr_ref3-ymxr_ref
; The table of a fill kind without pages: 0 for every transformation,
; each page to the fallback (ymxr_prefill).
ymxr_nomap:     dcb.b   128,0
