; play.S - YMXR_play, the frame procedure it runs inline, and the tables
; init reads to patch the frame's read sites.

YMXR_play:
                tst.l   WS_ROWS(a0)
                beq     ymxr_over
                .if     YMXR_PERF
                move.w  PERF_COLOUR.w,-(sp)     ; the background, kept
                move.b  PERF_BEAM.w,d0          ; before a tick can paint
                move.w  #PERF_SYNC,d1           ; over it; then the wait for
.beam:          cmp.b   PERF_BEAM.w,d0          ; the display, which the
                dbne    d1,.beam                ; counter marks by moving,
                                                ; and the ceiling ends where
                                                ; the counter stops
                move.w  #PERF_FRAME,PERF_COLOUR.w
                .endif
                movea.l WS_STATE+IM_POINTER(a0),a1  ; the row this call
                lea     YM_SELECT.w,a2          ; writes, where the advance
                                                ; before it left the image

; ---------------------------------------------------------------- frame

; The frame procedure (SPEC.md 4), inline in the call: a1 the row, a2 the
; chip's select register, a0 the workspace.
ymxr_frame:
; 1 to 3: the effects, each its four columns.
                EFFECT  WS_FX,_14,_15,_16,_17,_30,$FFFFFA19,$FFFFFA1F,$FFFFFA07,5,$F0,0,0,ymxr_tick0,ymxr_sq0,ymxr_one0,ymxr_two0,ymxr_three0,$134,ymxr_lk0
                EFFECT  WS_FX+FX_SIZE,_18,_19,_20,_21,_31,$FFFFFA1D,$FFFFFA25,$FFFFFA09,4,$F0,0,1,ymxr_tick1,ymxr_sq1,ymxr_one1,ymxr_two1,ymxr_three1,$110,ymxr_lk1
                EFFECT  WS_FX+FX_SIZE+FX_SIZE,_22,_23,_24,_25,_32,$FFFFFA1B,$FFFFFA21,$FFFFFA07,0,$F0,0,0,ymxr_tick2,ymxr_sq2,ymxr_one2,ymxr_two2,ymxr_three2,$120,ymxr_lk2
                EFFECT  WS_FX+FX_SIZE+FX_SIZE+FX_SIZE,_26,_27,_28,_29,_33,$FFFFFA1D,$FFFFFA23,$FFFFFA09,5,$0F,4,1,ymxr_tick3,ymxr_sq3,ymxr_one3,ymxr_two3,ymxr_three3,$114,ymxr_lk3
; 4: the tone periods. Steps 4 to 8 are between this label and
; ymxr_played, where the rig counts what the register columns cost.
ymxr_regs:
                TONE    _0,_1,$0000,$0100
                TONE    _2,_3,$0200,$0300
                TONE    _4,_5,$0400,$0500
; 5: the noise period, and the envelope period beside the shape's bits:
;    where the shape column is 0, no bit beside is set, and a period byte
;    is written where it is not 0, with no further test.
                WRITE   _6,$0600
                READ2   _13
                beq.s   ymxr_plain
                RESERVED _11,6,$0B00
                RESERVED _12,5,$0C00
; 6: the volumes.
                WRITE   _8,$0800
                WRITE   _9,$0900
                WRITE   _10,$0A00
; 7: the mixer, with the host's port directions.
                READ    _7
                bpl.s   .no7
                move.w  #$0700|YM_PORTS,d0
                or.b    d1,d0
                movep.w d0,(a2)
.no7:
; 8: the shape, read above into d2. Step 5 found the column set, so the
; test here is of its set bit alone.
                tst.b   d2
                bpl.s   ymxr_played
                move.w  #$0D00,d1
                move.b  d2,d1
                movep.w d1,(a2)
                bra.s   ymxr_played

; Steps 6 and 7 again, for the row whose envelope column step 5 found 0:
; step 8 leaves every register as it is on that row, and this copy ends without
; the test that would say so.
ymxr_plain:     PLAIN   _11,$0B00
                PLAIN   _12,$0C00
                RWRITE  _8,$0800
                RWRITE  _9,$0900
                RWRITE  _10,$0A00
                RREAD   _7
                bpl.s   ymxr_played
                move.w  #$0700|YM_PORTS,d0
                or.b    d1,d0
                movep.w d0,(a2)
; The row is written: the next one is read now, after every write this
; call makes, so no part of the advance's cost is between a host's clock
; and the chip.
ymxr_played:
                subq.l  #1,WS_ROWS(a0)
                bne.s   ymxr_next
                move.l  WS_R(a0),d0             ; the last row: the loop's
                sub.l   WS_RR(a0),d0            ; rows, or none
                move.l  d0,WS_ROWS(a0)
                beq.s   ymxr_return             ; none: no row to read
; The state block, which the advance reads from a6 (DTX, abi.md 2). This
; call restores a6 to its host, and the advance clobbers d0-d5 and
; a0-a5, so the stack is where a6 waits.
ymxr_next:      move.l  a6,-(sp)
                lea     WS_STATE(a0),a6
ymxr_call:      jsr     ($12345678).l           ; the advance's address,
                                                ; resolved at init
                movea.l (sp)+,a6
ymxr_return:
                .if     YMXR_PERF
                move.w  #PERF_BAR,PERF_COLOUR.w ; the timers' bar, burnt
                lea     ymxr_perf_acc(pc),a0    ; after the writes this call
                move.w  (a0),d0                 ; makes, so none of them
                clr.w   (a0)                    ; moves for it
.bar:           dbra    d0,.bar
                move.w  (sp)+,PERF_COLOUR.w
                .endif
                moveq   #0,d0
                rts
ymxr_over:      moveq   #-1,d0
                rts

                .if     YMXR_PERF
ymxr_perf_acc:  dc.w    0                       ; the ticks' counted cost
                .endif                          ; since the last call

; The reads' displacement words, one a column, in column order.
ymxr_reads:
                dc.w    c_0-ymxr_frame+2,c_1-ymxr_frame+2,c_2-ymxr_frame+2
                dc.w    c_3-ymxr_frame+2,c_4-ymxr_frame+2,c_5-ymxr_frame+2
                dc.w    c_6-ymxr_frame+2,c_7-ymxr_frame+2,c_8-ymxr_frame+2
                dc.w    c_9-ymxr_frame+2,c_10-ymxr_frame+2,c_11-ymxr_frame+2
                dc.w    c_12-ymxr_frame+2,c_13-ymxr_frame+2,c_14-ymxr_frame+2
                dc.w    c_15-ymxr_frame+2,c_16-ymxr_frame+2,c_17-ymxr_frame+2
                dc.w    c_18-ymxr_frame+2,c_19-ymxr_frame+2,c_20-ymxr_frame+2
                dc.w    c_21-ymxr_frame+2,c_22-ymxr_frame+2,c_23-ymxr_frame+2
                dc.w    c_24-ymxr_frame+2,c_25-ymxr_frame+2,c_26-ymxr_frame+2
                dc.w    c_27-ymxr_frame+2,c_28-ymxr_frame+2,c_29-ymxr_frame+2
                .if     YMXR_MAP
                dc.w    c_30-ymxr_frame+2,c_31-ymxr_frame+2,c_32-ymxr_frame+2
                dc.w    c_33-ymxr_frame+2
                .endif

; The four effects' heads, effect 3 first, as offsets into the frame.
ymxr_overs:
                dc.w    ymxr_tick3_over-ymxr_frame,ymxr_tick2_over-ymxr_frame
                dc.w    ymxr_tick1_over-ymxr_frame,ymxr_tick0_over-ymxr_frame

; The other sites a column is read at, each with its column: the effects'
; one-pass tests, and the envelope period's plain path.
ymxr_resets:    dc.w    ymxr_tick0_reset-ymxr_frame+1
                dc.w    ymxr_tick1_reset-ymxr_frame+1
                dc.w    ymxr_tick2_reset-ymxr_frame+1
                dc.w    ymxr_tick3_reset-ymxr_frame+1

ymxr_sites:
                dc.w    q_14-ymxr_frame+2,14,q_15-ymxr_frame+2,15
                dc.w    q_16-ymxr_frame+2,16,q_17-ymxr_frame+2,17
                dc.w    q_18-ymxr_frame+2,18,q_19-ymxr_frame+2,19
                dc.w    q_20-ymxr_frame+2,20,q_21-ymxr_frame+2,21
                dc.w    q_22-ymxr_frame+2,22,q_23-ymxr_frame+2,23
                dc.w    q_24-ymxr_frame+2,24,q_25-ymxr_frame+2,25
                dc.w    q_26-ymxr_frame+2,26,q_27-ymxr_frame+2,27
                dc.w    q_28-ymxr_frame+2,28,q_29-ymxr_frame+2,29
                dc.w    q_11-ymxr_frame+2,11,q_12-ymxr_frame+2,12
                dc.w    r_8-ymxr_frame+2,8,r_9-ymxr_frame+2,9
                dc.w    r_10-ymxr_frame+2,10,r_7-ymxr_frame+2,7
                .if     YMXR_MAP
                dc.w    q_30-ymxr_frame+2,30,q_31-ymxr_frame+2,31
                dc.w    q_32-ymxr_frame+2,32,q_33-ymxr_frame+2,33
                dc.w    m_30-ymxr_frame+2,30,m_31-ymxr_frame+2,31
                dc.w    m_32-ymxr_frame+2,32,m_33-ymxr_frame+2,33
                .endif
                .if     YMXR_DEFER
                dc.w    d_17-ymxr_frame+2,17,d_21-ymxr_frame+2,21
                dc.w    d_25-ymxr_frame+2,25,d_29-ymxr_frame+2,29
                .if     YMXR_MAP
                dc.w    d_30-ymxr_frame+2,30,d_31-ymxr_frame+2,31
                dc.w    d_32-ymxr_frame+2,32,d_33-ymxr_frame+2,33
                .endif
                .endif
