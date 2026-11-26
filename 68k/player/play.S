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
                PIECE   effect0_1
                EFFECT  WS_FX,_14,_15,_16,_17,_30,$FFFFFA19,$FFFFFA1F,$FFFFFA07,5,$F0,0,0,ymxr_tick0,ymxr_sq0,ymxr_one0,effect0,$134,ymxr_lk0,_34
                ENDPIECE effect0_5
                PIECE   effect1_1
                EFFECT  WS_FX+FX_SIZE,_18,_19,_20,_21,_31,$FFFFFA1D,$FFFFFA25,$FFFFFA09,4,$F0,0,1,ymxr_tick1,ymxr_sq1,ymxr_one1,effect1,$110,ymxr_lk1,_35
                ENDPIECE effect1_5
                PIECE   effect2_1
                EFFECT  WS_FX+FX_SIZE+FX_SIZE,_22,_23,_24,_25,_32,$FFFFFA1B,$FFFFFA21,$FFFFFA07,0,$F0,0,0,ymxr_tick2,ymxr_sq2,ymxr_one2,effect2,$120,ymxr_lk2,_36
                ENDPIECE effect2_5
                PIECE   effect3_1
                EFFECT  WS_FX+FX_SIZE+FX_SIZE+FX_SIZE,_26,_27,_28,_29,_33,$FFFFFA1D,$FFFFFA23,$FFFFFA09,5,$0F,4,1,ymxr_tick3,ymxr_sq3,ymxr_one3,effect3,$114,ymxr_lk3,_37
                ENDPIECE effect3_5
; 4: the tone periods. Steps 4 to 8 are between this label and
; ymxr_played, where the rig counts what the register columns cost.
ymxr_regs:
                TONE    _0,_1,$0000,$0100
                TONE    _2,_3,$0200,$0300
                TONE    _4,_5,$0400,$0500
; 5: the noise period, and the envelope period beside the shape's bits:
;    a period byte is written where it is not 0, and a 0 where its bit in
;    the shape column is set. The sync's register steps of R0 to R5 come
;    back here (sync.S).
ymxr_noise:     WRITE   _6,$0600
                READ2   _13
                RESERVED _11,6,$0B00
                RESERVED _12,5,$0C00
; 6: the volumes.
                WRITE   _8,$0800
                WRITE   _9,$0900
                WRITE   _10,$0A00
; 7: the mixer, with the host's port directions.
                READ    _7
                bpl.s   .no7
                or.b    #YM_PORTS,d1
                move.w  #$0700,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no7:
; 8: the shape, read above into d2: a row that leaves the column 0
; leaves its set bit clear.
                tst.b   d2
                bpl.s   ymxr_played
                move.w  #$0D00,d1
                move.b  d2,d1
                movep.w d1,(a2)

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

                .if     YMXR_MAP
                PIECE   copy_3
; The register of the frame value at d5, its bit set in d3 where d5 is
; one: a6 the frame values, each register's number in the byte after its
; value.
ymxr_cbit:      sub.l   a6,d5
                cmp.l   #13*FV_STRIDE,d5
                bhi.s   .out
                move.b  1(a6,d5.w),d5
                bset    d5,d3
.out:           rts

; The register steps of a frame of a tune that copies from frame (SPEC.md
; 3.1.7, 4.4): init writes a branch here over the first read of ymxr_regs
; for such a tune alone. d3 is the registers the ticks of copy from frame
; write: for each effect the tune runs whose timer counts, the record of
; the handler its vector names, the mirror where a tick has performed the
; start pending on it (4.8), and each column of that handler whose row 0
; is a frame value, the register of that frame value. Each register the
; row sets has the byte the frame writes, masked to the register's width,
; as its frame value, and the frame writes it to the chip where its bit of
; d3 is 0.
ymxr_cregs:     move.l  a6,-(sp)
                lea     ymxr_fv(pc),a6
; The registers change with a row that sets a column of an effect, which
; marks the frame (copy.S, ymxr_csome), and with a tick that performs an
; armed start (4.8), so the records are read again where a row marked the
; frame or a start is armed: on every other frame d3 is the mask the last
; read found.
                tst.b   WS_CDIRTY(a0)
                bne.s   ymxr_cread
                .if     YMXR_DEFER
                tst.b   WS_FX+FX_PENDING(a0)
                bne.s   ymxr_cread
                tst.b   WS_FX+FX_SIZE+FX_PENDING(a0)
                bne.s   ymxr_cread
                tst.b   WS_FX+FX_SIZE+FX_SIZE+FX_PENDING(a0)
                bne.s   ymxr_cread
                tst.b   WS_FX+FX_SIZE+FX_SIZE+FX_SIZE+FX_PENDING(a0)
                bne.s   ymxr_cread
                .endif
                move.w  ymxr_cmask-ymxr_fv(a6),d3
                bra.w   ymxr_csteps
ymxr_cread:     sf      WS_CDIRTY(a0)
                moveq   #0,d3
                lea     WS_FX+FX_SIZE+FX_SIZE+FX_SIZE(a0),a4    ; effect 3's
                lea     ymxr_timers+8+8+8(pc),a5                ; record, timer
                moveq   #3,d4
.fx:            btst    d4,WS_EFFECTS(a0)
                beq.s   .next
                movea.w 2(a5),a3                ; the timer's control register
                move.b  (a3),d5
                and.b   6(a5),d5                ; its select, 0 where stopped
                beq.s   .next
                movea.l a4,a3
                .if     YMXR_DEFER
                tst.b   FX_PENDING(a4)
                beq.s   .rec
                movea.w (a5),a3
                movea.l (a3),a3                 ; the handler the vector names
                cmpa.l  FX_ARMED(a4),a3
                movea.l a4,a3
                bne.s   .rec
                lea     ARMOFF(a4),a3           ; the start a tick performed
                .endif
.rec:           move.b  FX_SHAPE(a3),d5         ; the handler's columns: 0, 4
                beq.s   .one                    ; and 7 one, 2, 5 and 8 two, 3,
                cmp.b   #4,d5                   ; 6 and 9 three, the counted
                beq.s   .one                    ; ones under a cut among them;
                cmp.b   #7,d5                   ; a square and a source of one
                beq.s   .one                    ; row are never copy from frame
                cmp.b   #2,d5
                beq.s   .two
                cmp.b   #5,d5
                beq.s   .two
                cmp.b   #8,d5
                beq.s   .two
                cmp.b   #3,d5
                beq.s   .three
                cmp.b   #6,d5
                beq.s   .three
                cmp.b   #9,d5
                bne.s   .next
.three:         move.l  FX_FIRST3(a3),d5
                bsr.w   ymxr_cbit
.two:           move.l  FX_FIRST2(a3),d5
                bsr.w   ymxr_cbit
.one:           move.l  FX_FIRST(a3),d5
                bsr.w   ymxr_cbit
.next:          lea     -FX_SIZE(a4),a4
                subq.l  #8,a5
                dbra    d4,.fx
                move.w  d3,ymxr_cmask-ymxr_fv(a6)
ymxr_csteps:    movea.l a6,a3                   ; the frame values
                movea.l (sp)+,a6
                KTONE   _0,_1,$0000,$0100,0,1
                KTONE   _2,_3,$0200,$0300,2,3
                KTONE   _4,_5,$0400,$0500,4,5
ymxr_cnoise:    KWRITE  _6,$0600,6,$1F
                KREAD2  _13
                KRESERVED _11,6,$0B00,11
                KRESERVED _12,5,$0C00,12
                KWRITE  _8,$0800,8,$1F
                KWRITE  _9,$0900,9,$1F
                KWRITE  _10,$0A00,10,$1F
                KREAD   _7
                bpl.s   .no7
                or.b    #YM_PORTS,d1
                move.b  d1,7*FV_STRIDE(a3)
                btst    #7,d3
                bne.s   .no7
                move.w  #$0700,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no7:           tst.b   d2
                bpl.s   .played
                move.b  d2,d0
                and.b   #$0F,d0
                move.b  d0,13*FV_STRIDE(a3)
                btst    #13,d3
                bne.s   .played
                move.w  #$0D00,d1
                move.b  d2,d1
                movep.w d1,(a2)
.played:        bra.w   ymxr_played

; The read sites of ymxr_cregs, as the frame's are written (ymxr_col), and
; each effect's read at the head of the paths of a row that sets its
; columns copied into its ymxr_csome (copy.S), on every init, so each
; subtune of a set finds them as it finds them alone; and for a tune that
; copies from frame the branch to ymxr_cregs over the first read of
; ymxr_regs and a bsr.w to each ymxr_csome over the read it copies, which
; ymxr_copyinit writes back as the frame's reads before init writes the
; frame's sites. a0 the table's payload, d3 N, a4 ymxr_frame, a6 the
; workspace; every register kept.
ymxr_copysites: movem.l d0-d4/a1/a3/a5,-(sp)
                lea     ymxr_kreads(pc),a3
                moveq   #0,d1
                moveq   #14-1,d2
.site:          move.w  (a3)+,d4
                move.w  d1,d0
                bsr     ymxr_col
                addq.w  #1,d1
                dbra    d2,.site
; Each effect the link kept, effect 3 first, as ymxr_overs has the heads.
                lea     ymxr_overs(pc),a3
                lea     ymxr_csome(pc),a5
                moveq   #3,d2
.some:          move.w  (a3)+,d4
                btst    d2,ymxr_k_effect+1(pc)
                beq.s   .snext
                lea     (a4,d4.w),a1
                lea     SOME_AT(a1),a1          ; the read
                move.l  (a1),4(a5)              ; after the stub's st
                btst    #7,WS_EFFECTS(a6)       ; copy from frame (3.1.7)
                beq.s   .snext
                move.w  #$6100,(a1)+            ; bsr.w
                move.l  a5,d0
                sub.l   a1,d0
                move.w  d0,(a1)
.snext:         lea     CSOME_SIZE(a5),a5
                dbra    d2,.some
                btst    #7,WS_EFFECTS(a6)
                beq.s   .done
                lea     ymxr_regs(pc),a1
                move.w  #$6000,(a1)+            ; bra.w
                lea     ymxr_cregs(pc),a3
                move.l  a3,d0
                sub.l   a1,d0
                move.w  d0,(a1)
                st      WS_CDIRTY(a6)           ; the first frame reads the
.done:          movem.l (sp)+,d0-d4/a1/a3/a5    ; records
                rts

; The read sites of ymxr_cregs, one a register in column order, as
; ymxr_reads has the frame's.
ymxr_kreads:    dc.w    k_0-ymxr_frame+2,k_1-ymxr_frame+2,k_2-ymxr_frame+2
                dc.w    k_3-ymxr_frame+2,k_4-ymxr_frame+2,k_5-ymxr_frame+2
                dc.w    k_6-ymxr_frame+2,k_7-ymxr_frame+2,k_8-ymxr_frame+2
                dc.w    k_9-ymxr_frame+2,k_10-ymxr_frame+2,k_11-ymxr_frame+2
                dc.w    k_12-ymxr_frame+2,k_13-ymxr_frame+2
                ENDPIECE copy_3
                .endif

                .if     YMXR_PERF&(YMXR_MAP=0)
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

; The four effects' heads, effect 3 first, as offsets into the frame. This
; table and the next are a piece of the block of the effects, which init's
; loop over the heads reads.
                PIECE   effects_7
ymxr_overs:
                dc.w    ymxr_tick3_over-ymxr_frame,ymxr_tick2_over-ymxr_frame
                dc.w    ymxr_tick1_over-ymxr_frame,ymxr_tick0_over-ymxr_frame

; Each effect's place's reset, in the order of the heads above, effect 3
; first, and the bytes from the head's end to each piece of the frame
; code past it that the effect keeps: the start of a source of one row,
; the cut's, the remap's, the deferred start's and the rest (RES_ in
; start.S). Init reads the two tables side by side, and leaves the
; entries of an effect a link left out.
ymxr_resets:    dc.w    ymxr_tick3_reset-ymxr_frame+1
                dc.w    ymxr_p_one3_3-ymxr_e_effect3_1
                .if     YMXR_MAP
                dc.w    ymxr_p_cut3_1-ymxr_e_effect3_1
                dc.w    ymxr_p_map3_1-ymxr_e_effect3_1
                .endif
                .if     YMXR_DEFER
                dc.w    ymxr_p_defer3_1-ymxr_e_effect3_1
                .endif
                dc.w    ymxr_p_effect3_5-ymxr_e_effect3_1
                dc.w    ymxr_tick2_reset-ymxr_frame+1
                dc.w    ymxr_p_one2_3-ymxr_e_effect2_1
                .if     YMXR_MAP
                dc.w    ymxr_p_cut2_1-ymxr_e_effect2_1
                dc.w    ymxr_p_map2_1-ymxr_e_effect2_1
                .endif
                .if     YMXR_DEFER
                dc.w    ymxr_p_defer2_1-ymxr_e_effect2_1
                .endif
                dc.w    ymxr_p_effect2_5-ymxr_e_effect2_1
                dc.w    ymxr_tick1_reset-ymxr_frame+1
                dc.w    ymxr_p_one1_3-ymxr_e_effect1_1
                .if     YMXR_MAP
                dc.w    ymxr_p_cut1_1-ymxr_e_effect1_1
                dc.w    ymxr_p_map1_1-ymxr_e_effect1_1
                .endif
                .if     YMXR_DEFER
                dc.w    ymxr_p_defer1_1-ymxr_e_effect1_1
                .endif
                dc.w    ymxr_p_effect1_5-ymxr_e_effect1_1
                dc.w    ymxr_tick0_reset-ymxr_frame+1
                dc.w    ymxr_p_one0_3-ymxr_e_effect0_1
                .if     YMXR_MAP
                dc.w    ymxr_p_cut0_1-ymxr_e_effect0_1
                dc.w    ymxr_p_map0_1-ymxr_e_effect0_1
                .endif
                .if     YMXR_DEFER
                dc.w    ymxr_p_defer0_1-ymxr_e_effect0_1
                .endif
                dc.w    ymxr_p_effect0_5-ymxr_e_effect0_1
                ENDPIECE effects_7

; The other read sites, each with its column and the piece it needs.
; The high byte is zero for a site in the effect's base code.
SITE_MAP        equ     $0100
SITE_DEFER      equ     $0200
SITE_CUT        equ     $0400
ymxr_sites:
                dc.w    q_14-ymxr_frame+2,14,q_15-ymxr_frame+2,15
                dc.w    q_16-ymxr_frame+2,16,q_17-ymxr_frame+2,17
                dc.w    q_18-ymxr_frame+2,18,q_19-ymxr_frame+2,19
                dc.w    q_20-ymxr_frame+2,20,q_21-ymxr_frame+2,21
                dc.w    q_22-ymxr_frame+2,22,q_23-ymxr_frame+2,23
                dc.w    q_24-ymxr_frame+2,24,q_25-ymxr_frame+2,25
                dc.w    q_26-ymxr_frame+2,26,q_27-ymxr_frame+2,27
                dc.w    q_28-ymxr_frame+2,28,q_29-ymxr_frame+2,29
                .if     YMXR_MAP
                dc.w    q_30-ymxr_frame+2,30,q_31-ymxr_frame+2,31
                dc.w    q_32-ymxr_frame+2,32,q_33-ymxr_frame+2,33
                dc.w    c_30-ymxr_frame+2,SITE_MAP+30,c_31-ymxr_frame+2,SITE_MAP+31
                dc.w    c_32-ymxr_frame+2,SITE_MAP+32,c_33-ymxr_frame+2,SITE_MAP+33
                dc.w    m_30-ymxr_frame+2,SITE_MAP+30,m_31-ymxr_frame+2,SITE_MAP+31
                dc.w    m_32-ymxr_frame+2,SITE_MAP+32,m_33-ymxr_frame+2,SITE_MAP+33
                dc.w    r_30-ymxr_frame+2,SITE_CUT+30,r_31-ymxr_frame+2,SITE_CUT+31
                dc.w    r_32-ymxr_frame+2,SITE_CUT+32,r_33-ymxr_frame+2,SITE_CUT+33
                dc.w    q_34-ymxr_frame+2,34,q_35-ymxr_frame+2,35
                dc.w    q_36-ymxr_frame+2,36,q_37-ymxr_frame+2,37
                dc.w    c_34-ymxr_frame+2,SITE_CUT+34,c_35-ymxr_frame+2,SITE_CUT+35
                dc.w    c_36-ymxr_frame+2,SITE_CUT+36,c_37-ymxr_frame+2,SITE_CUT+37
                dc.w    m_34-ymxr_frame+2,SITE_CUT+34,m_35-ymxr_frame+2,SITE_CUT+35
                dc.w    m_36-ymxr_frame+2,SITE_CUT+36,m_37-ymxr_frame+2,SITE_CUT+37
                .endif
                .if     YMXR_DEFER
                dc.w    d_17-ymxr_frame+2,SITE_DEFER+17,d_21-ymxr_frame+2,SITE_DEFER+21
                dc.w    d_25-ymxr_frame+2,SITE_DEFER+25,d_29-ymxr_frame+2,SITE_DEFER+29
                .if     YMXR_MAP
                dc.w    d_30-ymxr_frame+2,SITE_DEFER+30,d_31-ymxr_frame+2,SITE_DEFER+31
                dc.w    d_32-ymxr_frame+2,SITE_DEFER+32,d_33-ymxr_frame+2,SITE_DEFER+33
                dc.w    d_34-ymxr_frame+2,SITE_CUT+34,d_35-ymxr_frame+2,SITE_CUT+35
                dc.w    d_36-ymxr_frame+2,SITE_CUT+36,d_37-ymxr_frame+2,SITE_CUT+37
                .endif
                .endif
ymxr_sites_end:
