; deferred.S - deferred starts (SPEC.md 4.8): the frame's paths that arm,
; settle and drop a pending start, and the record updated once the
; installer has performed it. YMXR.S includes it where YMXR_DEFER is 1.

; Update the record after installation (SPEC.md 4.8): copy the armed
; shape, row-0 places and row count, then clear pending. With lookups,
; install its captured mapping as running and preserve kept (1.10.3).
; Copy its square level for reset; FX_USE selects the armed bank's
; prepared page. a3 is the record.
ymxr_take:      move.b  FX_SHAPE+ARMOFF(a3),FX_SHAPE(a3)
                move.l  FX_FIRST+ARMOFF(a3),FX_FIRST(a3)
                move.l  FX_FIRST2+ARMOFF(a3),FX_FIRST2(a3)
                move.l  FX_FIRST3+ARMOFF(a3),FX_FIRST3(a3)
                move.l  FX_ROWS+ARMOFF(a3),FX_ROWS(a3)
                .if     YMXR_MAP
                move.b  FX_ALEVEL(a3),FX_LEVEL(a3)
                move.b  FX_AMAP(a3),FX_RUN(a3)
                .endif
                clr.b   FX_PENDING(a3)
                rts

; A row that acts on an effect with a start pending (SPEC.md 4.8), at
; level 7, so the tick that would perform it is either before the test or
; after the row: where the vector names the armed handler, the switch has
; run and the record is the armed start's; otherwise, where d5 is 1, the
; start is dropped and the hooked word written back. d4 the effect. Z
; where no start is pending after it. a3 the record; d0, d4, a4 and a5
; scratch.
ymxr_pend:      lea     ymxr_timers(pc),a4
                move.w  d4,d0
                lsl.w   #3,d0
                movea.w (a4,d0.w),a4            ; the effect's vector
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; its record
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                movea.l (a4),a5                 ; the handler the vector names
                cmpa.l  FX_ARMED(a3),a5
                bne.s   .still
                bsr.s   ymxr_take
                bra.s   .done
.still:         tst.w   d5
                beq.s   .done                   ; left pending
                movea.l FX_HOOK(a3),a5          ; dropped
                move.w  FX_HOOKW(a3),(a5)
                clr.b   FX_PENDING(a3)
.done:          move.w  (sp)+,sr
                tst.b   FX_PENDING(a3)
                rts

; Arm a deferred start on a running timer (SPEC.md 4.8). Patch the
; inactive bank's handler at the running source's row count modulo the
; new source length. Store count, select and timer reset in the
; installer; replace the running handler's end-path word with a branch
; to its hook. Replacing a pending start retains the hook and patches
; the installer at level 7.
;
; d1=count column, d2=control byte, d3=source, d4=effect. Z means
; pending; NZ means stopped, with source in d1 for an immediate start.
; Clobbers d0, d3 to d5 and a3 to a5.
;
; With lookups, capture the row's kept mapping in FX_AMAP. Squares and
; one-row sources store mapped entries as immediates. Stepping sources
; use the inactive bank's lookup instance, with its page prepared while
; interrupts remain enabled (ymxr_lstart).
ymxr_arm:       movem.l d3/d6-d7/a6,-(sp)       ; the source at 4(sp) below
                move.w  sr,-(sp)
                move.w  d4,d6                   ; the effect
                lea     ymxr_timers(pc),a6
                lsl.w   #3,d4
                adda.w  d4,a6                   ; its timer
                move.w  d6,d4
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; its record
                tst.b   FX_PENDING(a3)
                beq.s   .first
                .if     YMXR_MAP
; Replacing a pending lookup start first fills an unused page with
; interrupts enabled. If a tick installs the old pending start during
; that fill, the vector changes banks. Re-read it and fill the newly
; inactive bank once more. The old pending start has completed, so the
; bank remains stable until the new hook is installed (SPEC.md 4.8).
                tst.b   FX_MAP(a3)              ; the start's mapping
                beq.s   .filled
                move.w  d3,d0
                lsl.w   #3,d0
                move.l  WS_SOURCES-4(a0,d0.w),d0    ; its cell: -1 a square
                addq.l  #2,d0                   ; and -2 one row, whose
                cmp.l   #1,d0                   ; entries are in the handler
                bls.s   .filled
                moveq   #0,d4
                movea.w (a6),a5
                movea.l (a5),a5
                lea     ymxr_bank1(pc),a2
                cmpa.l  a2,a5
                bcc.s   .fbank
                move.w  #BANK,d4
.fbank:         move.w  d4,FX_ABANK(a3)
                move.w  d1,-(sp)                ; the count column
                move.w  d6,d4
                move.b  #1,WS_ARMING(a0)        ; the page alone
                bsr.w   ymxr_lstart
                sf      WS_ARMING(a0)
                move.w  (sp)+,d1
.filled:
                .endif
                ori.w   #$0700,sr               ; a start pending: replaced,
                movea.w (a6),a5                 ; unless the tick performed it
                movea.l (a5),a5                 ; since the frame read it
                cmpa.l  FX_ARMED(a3),a5
                bne.s   .live
                bsr.w   ymxr_take
                .if     YMXR_MAP
                move.w  (sp),sr                 ; none pending: the arming
                .endif                          ; runs with interrupts on
.first:         lea     ymxr_park(pc),a4        ; no source connected before
                cmpa.l  FX_FIRST(a3),a4         ; the first start
                beq.w   .stopped
                movea.w 2(a6),a4                ; the select bits of the
                move.b  (a4),d5                 ; timer's control register
                and.b   6(a6),d5
                beq.w   .stopped
; Capture the installation rate using frame rules (SPEC.md 4.3):
; explicit count, or kept count when the column is unset, plus select.
; Target comes from the mirror; mapping is the row's resulting kept
; mapping.
.live:
                .if     YMXR_MAP
                move.b  FX_MAP(a3),FX_AMAP(a3)
                sf      FX_LOK(a3)              ; the cache, until a remap
                .endif
                tst.b   d1
                bne.s   .cnt
                btst    #4,d2
                bne.s   .cnt
                move.b  FX_COUNT(a3),d1
.cnt:           move.b  d1,FX_COUNT(a3)
                move.b  d1,d7                   ; the count
                move.b  d2,d5
                and.b   #7,d5
                move.b  d5,FX_SELECT(a3)
                move.b  FX_TARGET(a3),FX_TARGET+ARMOFF(a3)
; The source, and the row it starts at: row 0 with bit 5, and otherwise
; R of the running source modulo R of this one, the count of the rows the
; running source played.
                move.w  4(sp),d3
                lsl.w   #3,d3
                movea.l WS_SOURCES-8(a0,d3.w),a4    ; its first row
                move.l  WS_SOURCES-4(a0,d3.w),d0    ; its cell
                move.w  d3,d1                   ; its entry, for widestart
                moveq   #0,d3
                btst    #5,d2
                bne.s   .placed
                move.b  FX_SHAPE(a3),d5         ; R of a running source of
                cmp.b   #2,d5                   ; several columns, or of a
                bcs.s   .header                 ; counted one, in the record;
                cmp.b   #5,d5                   ; of any other, in the header
                bhi.s   .header                 ; before its first row
                move.l  FX_ROWS(a3),d3
                bra.s   .last
.header:        movea.l FX_FIRST(a3),a5
                move.l  DT_ROWS-DT_PAYLOAD(a5),d3
.last:          move.l  DT_ROWS-DT_PAYLOAD(a4),d4
                bsr.w   ymxr_rem
.placed:        move.l  d3,FX_APLACE(a3)
; The bank the vector does not point into.
                moveq   #0,d4
                movea.w (a6),a5
                movea.l (a5),a5                 ; the running handler
                lea     ymxr_bank1(pc),a2
                cmpa.l  a2,a5
                bcc.s   .bank
                move.w  #BANK,d4
.bank:          move.w  d4,FX_ABANK(a3)
                .if     YMXR_MAP
                tst.b   FX_AMAP(a3)             ; a source that steps a place,
                beq.s   .ident                  ; through a transformation: the
                move.l  d0,d5                   ; lookup instance of its shape
                addq.l  #2,d5                   ; in that bank, at the row
                cmp.l   #1,d5                   ; above, written into the
                bls.s   .ident                  ; mirror
                move.w  4(sp),d3
                move.w  d6,d4
                st      WS_ARMING(a0)
                bsr.w   ymxr_lstart
                sf      WS_ARMING(a0)
                lea     -ARMOFF(a3),a3          ; the record, past its mirror
                bra.w   .armed
.ident:
                .endif
                tst.l   d0
                bmi.s   .shaped
; the general handler: the target, the place and the loop cell
                move.w  d6,d5
                mulu    #TICK_SIZE,d5
                lea     ymxr_tick0(pc),a5
                adda.w  d5,a5
                adda.w  d4,a5
                move.b  FX_TARGET(a3),TICK_SEL(a5)
                add.l   a4,d3                   ; the row the place is at
                .if     YMXR_PCREL
                lea     TICK_PTR(a5),a2
                sub.l   a2,d3
                move.w  d3,TICK_PTR(a5)
                tst.l   d0                      ; 0: the source plays once
                beq.s   .once
                sub.l   a2,d0
.once:          move.w  d0,TICK_CELL(a5)
                .else
                move.l  d3,TICK_PTR(a5)
                move.l  d0,TICK_CELL(a5)
                .endif
                clr.b   FX_SHAPE+ARMOFF(a3)
                .if     YMXR_MAP
                bra.w   .single
                .else
                bra.s   .single
                .endif
.shaped:        addq.l  #1,d0
                bne.s   .notsq
; a square: its two rows, and the row the place is at
                move.w  d6,d5
                mulu    #SQ_SIZE,d5
                lea     ymxr_sq0(pc),a5
                adda.w  d5,a5
                adda.w  d4,a5
                move.b  FX_TARGET(a3),SQ_SEL(a5)
                move.b  (a4),d5
                move.b  1(a4),d0
                eor.b   d0,d5
                move.b  d5,SQ_MASK(a5)
                move.b  (a4,d3.w),SQ_VAL(a5)
                st      FX_SHAPE+ARMOFF(a3)
                .if     YMXR_MAP
                move.b  (a4),FX_ALEVEL(a3)      ; row 0's level, which the
                tst.b   FX_AMAP(a3)             ; switch makes the record's;
                beq.s   .single                 ; through a transformation the
                move.b  (a4),d1                 ; entries of the two rows in
                move.b  1(a4),d4                ; their place (1.10.2)
                moveq   #0,d0
                move.b  FX_AMAP(a3),d0
                bsr.w   ymxr_tab
                move.b  d1,d0
                bsr.w   ymxr_entry
                move.b  d0,d1                   ; row 0's
                move.b  d4,d0
                bsr.w   ymxr_entry              ; row 1's
                move.b  d1,FX_ALEVEL(a3)
                move.b  d1,d5
                eor.b   d0,d5
                move.b  d5,SQ_MASK(a5)
                tst.l   d3                      ; the row the place is at
                beq.s   .row0
                move.b  d0,d1
.row0:          move.b  d1,SQ_VAL(a5)
                .endif
                bra.s   .single
.notsq:         addq.l  #1,d0
                bne.s   .wide
; a source of one row
                move.w  d6,d5
                mulu    #ONE_SIZE,d5
                lea     ymxr_one0(pc),a5
                adda.w  d5,a5
                adda.w  d4,a5
                move.b  FX_TARGET(a3),ONE_SEL(a5)
                move.b  (a4),ONE_VAL(a5)
                .if     YMXR_MAP
                moveq   #0,d0                   ; through a transformation,
                move.b  FX_AMAP(a3),d0          ; its entry
                beq.s   .oneid
                bsr.w   ymxr_tab
                move.b  (a4),d0
                bsr.w   ymxr_entry
                move.b  d0,ONE_VAL(a5)
.oneid:
                .endif
                move.b  #1,FX_SHAPE+ARMOFF(a3)
.single:        move.l  a4,FX_FIRST+ARMOFF(a3)
                move.l  DT_ROWS-DT_PAYLOAD(a4),FX_ROWS+ARMOFF(a3)
                bra.s   .armed
; a source of several columns, or counted: widestart into the mirror, at
; the bank and the row above
.wide:          subq.l  #1,d0                   ; the tag as a start passes it
                move.w  d6,d4
                st      WS_ARMING(a0)
                bsr.w   ymxr_widestart
                sf      WS_ARMING(a0)
                lea     -ARMOFF(a3),a3          ; the record, past its mirror
; the installer: the count, the handler, the select and the timer reset
.armed:         move.l  a5,FX_ARMED(a3)
                lea     ymxr_timers(pc),a4
                adda.w  4(a6),a4                ; the effect's installer
                move.b  d7,INST_CNT(a4)
                move.l  a5,INST_VEC(a4)
                move.b  FX_SELECT(a3),d5
                move.b  7(a6),d0
                and.b   #$7F,d0
                lsl.b   d0,d5                   ; at the timer's bits
                tst.b   7(a6)
                bmi.s   .shares
                move.b  d5,INST_SEL(a4)
                bra.s   .sel
.shares:        move.b  d5,INSTS_SEL(a4)
.sel:           move.w  #INST_SKIPW,INST_SKIP(a4)
                btst    #6,d2
                beq.s   .reset
                move.w  #$4E71,INST_SKIP(a4)    ; a nop: the stop runs
.reset:         tst.b   FX_PENDING(a3)
                bne.w   .hooked                 ; replaced: the hook stays
; the hook: the word at the head of the running handler's path that ends
; its source, by the shape it runs, and the branch to its stub
                movea.w (a6),a5
                movea.l (a5),a5                 ; the running handler
                move.b  FX_SHAPE(a3),d5
                .if     YMXR_MAP
                bmi.s   .h1                     ; a square and one row by
                cmp.b   #1,d5                   ; their handler; a handler
                beq.s   .h2                     ; that steps a place by its
                bsr.w   ymxr_kindof             ; kind, the lookup instance's
                tst.b   FX_RUN(a3)              ; where the source runs
                bne.s   .hk                     ; through a transformation
                move.w  d1,d0
.hk:            add.w   d0,d0
                add.w   d0,d0
                lea     ymxr_hooks(pc),a2
                move.w  2(a2,d0.w),d4
                move.w  (a2,d0.w),d0
                lea     (a5,d0.w),a2
                bra.s   .hook
                .else
                bne.s   .h1
                lea     TICK_LAST(a5),a2
                move.w  #TICK_HOOKW,d4
                bra.s   .hook
                .endif
.h1:            bpl.s   .h2
                move.b  SQ_MASK(a5),SQ_HMASK(a5)    ; the square's other row
                lea     SQ_LAST(a5),a2
                move.w  #SQ_HOOKW,d4
                bra.s   .hook
.h2:
                .if     YMXR_MAP
                lea     ONE_LAST(a5),a2
                move.w  #ONE_HOOKW,d4
                .else
                cmp.b   #1,d5
                bne.s   .h3
                lea     ONE_LAST(a5),a2
                move.w  #ONE_HOOKW,d4
                bra.s   .hook
.h3:            cmp.b   #2,d5
                bne.s   .h4
                lea     TW2_LAST(a5),a2
                move.w  #TW2_HOOKW,d4
                bra.s   .hook
.h4:            cmp.b   #3,d5
                bne.s   .h5
                lea     TW3_LAST(a5),a2
                move.w  #TW3_HOOKW,d4
                bra.s   .hook
.h5:            cmp.b   #4,d5
                bne.s   .h6
                lea     TICKC_LAST(a5),a2
                move.w  #TICKC_HOOKW,d4
                bra.s   .hook
.h6:            lea     TWC_LAST(a5),a2
                move.w  #TWC_HOOKW,d4
                .endif
.hook:          move.w  (a2),FX_HOOKW(a3)       ; the word as it is, written
                move.w  (a2),INST_BACKW(a4)     ; back at the switch or the
                move.l  a2,INST_BACKA(a4)       ; drop
                move.l  a2,FX_HOOK(a3)
                ori.w   #$0700,sr               ; the timer still running, and
                movea.w 2(a6),a5                ; the hook in place: one test
                move.b  (a5),d5                 ; and one word against a tick
                and.b   6(a6),d5
                beq.s   .stopped
                move.w  d4,(a2)
                st      FX_PENDING(a3)
.hooked:        move.w  (sp)+,sr
                movem.l (sp)+,d3/d6-d7/a6
                lea     YM_SELECT.w,a2
                moveq   #0,d0
                rts
.stopped:       move.w  (sp)+,sr
                movem.l (sp)+,d3/d6-d7/a6
                move.w  d3,d1                   ; the source
                lea     YM_SELECT.w,a2
                moveq   #1,d0
                rts
