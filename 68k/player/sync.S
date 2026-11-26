; sync.S - the sync of a voice at a frame (SPEC.md 1.12, 4.4.1): the
; register steps of R0 to R5 of a tune that syncs a voice, the frame values
; of those registers, and init's work for such a tune. Every part is a
; piece of the block `sync`, which a link keeps for a song a tune of which
; syncs a voice (doc/LINKER.md), and init aims a branch at it for such a
; tune alone, so a tune that syncs no voice runs the frame it ran before.

                PIECE   sync_3
; The frame values of R0 to R5, a byte each: the value the rows last set,
; masked to the register's width, 0 before a row sets the column (SPEC.md
; 3.1.7, 4.1 step 4); the row's bytes of columns 0 to 5, read once; and,
; with the deferred start, the target each effect's running handler
; writes, which step 4 of every frame keeps: the kept target, other than
; while a deferred start is armed and no tick has performed it, when the
; running handler writes the target it wrote before the row that armed
; it, whose target column moves the kept target first (effect.S).
                .even
ymxr_sfv:       dc.b    0,0,0,0,0,0
ymxr_srow:      dc.b    0,0,0,0,0,0
ymxr_srun:      dc.b    0,0,0,0
; The voices whose period a target writes, bit 0 voice A to bit 2 voice C,
; targets 0 to 27 (SPEC.md 2.1).
ymxr_svoice:    dc.b    1,1,2,2,4,4,0,0,0,0,0,0,0,0     ; setR0 to setR13
                dc.b    1,2,4,1,2,4,0,0,0,0,0           ; 14 to 24
                dc.b    1,2,4                           ; 25 to 27
                .even

; A row's fine and coarse column of a voice, each at a site init writes as
; it writes the frame's (ymxr_col), labelled \p and the column: the two
; bytes into ymxr_srow at a4, \ro and \ro + 1; the frame value of each
; column the row sets at a3, \fo and \fo + \stride, the coarse one masked
; to its four bits; and the voice's bit into d4 where bit 5 of the coarse
; column is 1, read on every row (1.12.1).
                .macro  SVOICE  p,fine,coarse,v,ro,fo,stride
\{p}\{coarse}:  move.b  $7FFF(a1),d2
\{p}\{fine}:    move.b  $7FFF(a1),d1
                bne.s   .fs\~
                btst    #6,d2
                beq.s   .nf\~
.fs\~:          move.b  d1,\fo(a3)
.nf\~:          move.b  d1,\ro(a4)
                move.b  d2,\ro+1(a4)
                bpl.s   .nc\~
                move.b  d2,d0
                and.b   #$0F,d0
                move.b  d0,\fo+\stride(a3)
.nc\~:          btst    #5,d2
                beq.s   .ns\~
                bset    #\v,d4
.ns\~:
                .endm

; With the deferred start, effect i's running target into ymxr_srun, at
; 6 + i from a4: the kept target where no start is armed or a tick has
; performed it, the vector naming the armed handler (deferred.S).
                .macro  SKEEP   i,fx,vector
                btst    #\i,WS_EFFECTS(a0)
                beq.s   .no\~
                tst.b   \fx+FX_PENDING(a0)
                beq.s   .set\~
                move.l  \vector.w,d0
                cmp.l   \fx+FX_ARMED(a0),d0
                bne.s   .no\~
.set\~:         move.b  \fx+FX_TARGET(a0),6+\i(a4)
.no\~:
                .endm

; The voices of d4 less those on a register of whose period effect i
; runs: its timer counts, and its running target writes the register
; (4.4.1). a5 ymxr_svoice.
                .macro  SBUSY   i,fx,ctrl,mask
                btst    #\i,WS_EFFECTS(a0)
                beq.s   .no\~
                move.b  \ctrl.w,d0
                and.b   #\mask,d0
                beq.s   .no\~
                moveq   #0,d0
                .if     YMXR_DEFER
                move.b  6+\i(a4),d0
                .else
                move.b  \fx+FX_TARGET(a0),d0
                .endif
                cmp.w   #28,d0
                bcc.s   .no\~
                move.b  (a5,d0.w),d0
                not.b   d0
                and.b   d0,d4
.no\~:
                .endm

; The running targets, and the sync of the voices of d4 an effect leaves
; (1.12.2): 0 to the two registers of each, fine then coarse, a wait, and
; each frame value at a3, \stride bytes apart, fine then coarse. The
; instructions from the last 0 to the first frame value are the wait, at
; least 36 cycles (YMXS, SPEC.md 3.9.2), and a tick within them lengthens
; it.
                .macro  SSYNC   stride
                .if     YMXR_DEFER
                SKEEP   0,WS_FX,$134
                SKEEP   1,WS_FX+FX_SIZE,$110
                SKEEP   2,WS_FX+FX_SIZE+FX_SIZE,$120
                SKEEP   3,WS_FX+FX_SIZE+FX_SIZE+FX_SIZE,$114
                .endif
                tst.b   d4
                beq.w   .done\~
                lea     ymxr_svoice(pc),a5
                SBUSY   0,WS_FX,$FFFFFA19,$07
                SBUSY   1,WS_FX+FX_SIZE,$FFFFFA1D,$07
                SBUSY   2,WS_FX+FX_SIZE+FX_SIZE,$FFFFFA1B,$07
                SBUSY   3,WS_FX+FX_SIZE+FX_SIZE+FX_SIZE,$FFFFFA1D,$70
                tst.b   d4
                beq.w   .done\~
                btst    #0,d4
                beq.s   .zb\~
                move.w  #$0000,d0
                movep.w d0,(a2)
                move.w  #$0100,d0
                movep.w d0,(a2)
.zb\~:          btst    #1,d4
                beq.s   .zc\~
                move.w  #$0200,d0
                movep.w d0,(a2)
                move.w  #$0300,d0
                movep.w d0,(a2)
.zc\~:          btst    #2,d4
                beq.s   .pa\~
                move.w  #$0400,d0
                movep.w d0,(a2)
                move.w  #$0500,d0
                movep.w d0,(a2)
.pa\~:          btst    #0,d4
                beq.s   .pb\~
                move.w  #$0000,d0
                move.b  0(a3),d0
                movep.w d0,(a2)
                move.w  #$0100,d0
                move.b  \stride(a3),d0
                movep.w d0,(a2)
.pb\~:          btst    #1,d4
                beq.s   .pc\~
                move.w  #$0200,d0
                move.b  2*\stride(a3),d0
                movep.w d0,(a2)
                move.w  #$0300,d0
                move.b  3*\stride(a3),d0
                movep.w d0,(a2)
.pc\~:          btst    #2,d4
                beq.s   .done\~
                move.w  #$0400,d0
                move.b  4*\stride(a3),d0
                movep.w d0,(a2)
                move.w  #$0500,d0
                move.b  5*\stride(a3),d0
                movep.w d0,(a2)
.done\~:
                .endm

; A voice the sync left out: the row's columns of its period out of
; ymxr_srow at a4, \ro and \ro + 1, as TONE writes them (columns.S), and,
; with \copy at 1, as KTONE does, to the chip where the register's bit of
; d3 is 0.
                .macro  STONE   v,ro,fsel,csel,fn,cn,copy
                btst    #\v,d4
                bne.s   .nc\~
                move.b  \ro(a4),d1
                move.b  \ro+1(a4),d2
                tst.b   d1
                bne.s   .go\~
                tst.b   d2
                beq.s   .nc\~
                btst    #6,d2
                beq.s   .nf\~
.go\~:
                .if     \copy
                btst    #\fn,d3
                bne.s   .nf\~
                .endif
                move.w  #\fsel,d0
                move.b  d1,d0
                movep.w d0,(a2)
.nf\~:          tst.b   d2
                bpl.s   .nc\~
                .if     \copy
                btst    #\cn,d3
                bne.s   .nc\~
                .endif
                move.w  #\csel,d1
                move.b  d2,d1
                movep.w d1,(a2)
.nc\~:
                .endm

; The register steps of R0 to R5 of a frame of a tune that syncs a voice
; (4.4, 4.4.1): init writes a branch here over the first read of
; ymxr_regs for such a tune alone. a0 the workspace, a1 the row, a2 the
; select register; d0 to d4 and a3 to a5 clobbered.
ymxr_sregs:     lea     ymxr_sfv(pc),a3
                lea     ymxr_srow(pc),a4
                moveq   #0,d4                   ; the voices the row syncs
                SVOICE  s,_0,_1,0,0,0,1
                SVOICE  s,_2,_3,1,2,2,1
                SVOICE  s,_4,_5,2,4,4,1
                SSYNC   1
                STONE   0,0,$0000,$0100,0,1,0
                STONE   1,2,$0200,$0300,2,3,0
                STONE   2,4,$0400,$0500,4,5,0
                bra.w   ymxr_noise

                .if     YMXR_MAP
                ENDPIECE sync_3
; The block copysync, which a link keeps for a song that copies from frame
; and syncs a voice (doc/LINKER.md): the register steps of R0 to R5 of a
; frame of a tune that syncs a voice and copies from frame (play.S,
; ymxr_csteps), where init writes a branch over the first read of
; ymxr_csteps for such a tune alone. a3 the frame values (copy.S,
; ymxr_fv), FV_STRIDE bytes apart, and d3 the registers the ticks of copy
; from frame write, which the frame leaves to them.
                PIECE   copysync_1
ymxr_skregs:    lea     ymxr_srow(pc),a4
                moveq   #0,d4
                SVOICE  t,_0,_1,0,0,0,FV_STRIDE
                SVOICE  t,_2,_3,1,2,2*FV_STRIDE,FV_STRIDE
                SVOICE  t,_4,_5,2,4,4*FV_STRIDE,FV_STRIDE
                SSYNC   FV_STRIDE
                STONE   0,0,$0000,$0100,0,1,1
                STONE   1,2,$0200,$0300,2,3,1
                STONE   2,4,$0400,$0500,4,5,1
                bra.w   ymxr_cnoise
; Its read sites, columns 0 to 5, as ymxr_kreads has those of the steps
; of copy from frame.
ymxr_treads:    dc.w    t_0-ymxr_frame+2,t_1-ymxr_frame+2,t_2-ymxr_frame+2
                dc.w    t_3-ymxr_frame+2,t_4-ymxr_frame+2,t_5-ymxr_frame+2
                ENDPIECE copysync_1
                PIECE   sync_4
                .endif

; Init's work before it writes the frame's read sites: the first read of
; the register steps put back, and in a tune that copies from frame the
; first read of those of copy from frame, since ymxr_col reads which
; register a site moves into off the instruction there, over which the
; init of a tune that syncs wrote a branch. a6 the workspace; every
; register kept.
ymxr_syncfix:   move.l  a5,-(sp)
                lea     ymxr_regs(pc),a5
                move.w  #$1429,(a5)             ; move.b d16(a1),d2
                .if     YMXR_MAP
                ENDPIECE sync_4
                PIECE   copysync_2
                lea     k_1(pc),a5
                move.w  #$1429,(a5)
                ENDPIECE copysync_2
                PIECE   sync_5
                .endif
                movea.l (sp)+,a5
                rts

; Init's work after the frame's read sites (SPEC.md 4.1 step 4): the frame
; values of R0 to R5 and the running targets 0, and the block's read
; sites, on every init, so each subtune of a set finds them as it finds
; them alone; and for a tune that syncs a voice, bit 7 of byte 10 of the
; bound tune (BINARIES.md 1.5), a branch over the first read of the
; frame's register steps, or of those of copy from frame where the tune
; copies from frame. a0 the table's payload, d3 N, a4 ymxr_frame, a6 the
; workspace; every register kept.
ymxr_syncinit:  movem.l d0-d4/a1/a3/a5,-(sp)
                lea     ymxr_sfv(pc),a3
                moveq   #16-1,d0
.clear:         clr.b   (a3)+
                dbra    d0,.clear
                lea     ymxr_sreads(pc),a3
                bsr.w   .sites
                .if     YMXR_MAP
                ENDPIECE sync_5
                PIECE   copysync_3
                lea     ymxr_treads(pc),a3
                bsr.w   .sites
                ENDPIECE copysync_3
                PIECE   sync_6
                .endif
                movea.l WS_TUNE(a6),a1
                tst.b   TF_SYNC(a1)
                bpl.w   .done
                lea     ymxr_regs(pc),a1
                lea     ymxr_sregs(pc),a3
                .if     YMXR_MAP
                ENDPIECE sync_6
                PIECE   copysync_4
                tst.b   WS_EFFECTS(a6)          ; copy from frame
                bpl.w   .aim
                lea     k_1(pc),a1
                lea     ymxr_skregs(pc),a3
                ENDPIECE copysync_4
                PIECE   sync_7
                .endif
.aim:           move.w  #$6000,(a1)+            ; bra.w
                move.l  a3,d0
                sub.l   a1,d0
                move.w  d0,(a1)
.done:          movem.l (sp)+,d0-d4/a1/a3/a5
                rts
; Six read sites, columns 0 to 5, the offsets of their displacement words
; at a3. d1, d2 and d4 clobbered.
.sites:         moveq   #0,d1
                moveq   #6-1,d2
.site:          move.w  (a3)+,d4
                move.w  d1,d0
                bsr     ymxr_col
                addq.w  #1,d1
                dbra    d2,.site
                rts

; The read sites of the block, columns 0 to 5, as ymxr_reads has the
; frame's.
ymxr_sreads:    dc.w    s_0-ymxr_frame+2,s_1-ymxr_frame+2,s_2-ymxr_frame+2
                dc.w    s_3-ymxr_frame+2,s_4-ymxr_frame+2,s_5-ymxr_frame+2
                .if     YMXR_MAP
                ENDPIECE sync_7
                .else
                ENDPIECE sync_3
                .endif

; The block of the handler of a target of a sync (handlers.S), which a
; link keeps for a song that starts a source on one: the start of several
; columns that sends a start on a target of a sync to that handler, and
; init's aim of each effect's call at it.
                PIECE   synced_4
; The start of several columns of a tune that syncs a voice: init aims
; each effect's call of ymxr_widestart and the arming's here for such a
; tune alone, so every other tune runs the start it ran before. A start
; on a target of a sync, the record's or, where a deferred start is armed,
; the mirror's (4.8), runs the counted handler of two columns with the
; sync at its head, whose offsets are TWC_'s, at every shape of source of
; two columns: copy from frame's frame values, or the source's rows, R out
; of its header. Every other start goes on to ymxr_widestart, or in a
; tune that copies from frame to ymxr_copywide. The registers are those
; of ymxr_widestart; a3 and d5 clobbered before it.
ymxr_syncwide:  move.w  d4,d5
                mulu    #FX_SIZE,d5
                lea     WS_FX(a0),a3
                adda.w  d5,a3                   ; the effect's record
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                beq.s   .rec
                lea     ARMOFF(a3),a3
.rec:
                .endif
                cmp.b   #25,FX_TARGET(a3)       ; a target of a sync (SPEC.md
                bcc.s   .sync                   ; 5.1)
                .if     YMXR_MAP
                tst.b   WS_EFFECTS(a0)          ; copy from frame
                bmi.w   ymxr_copywide
                .endif
                bra.w   ymxr_widestart
.sync:          move.l  d6,-(sp)                ; the effect, as the two starts
                move.w  d4,d6                   ; keep it
                .if     YMXR_MAP
                cmp.l   #-7,d0                  ; copy from frame
                beq.s   .copy
                .endif
                lea     WS_WIDE(a0),a2
                move.l  -8(a2,d1.w),d5          ; the source's loop cell
                moveq   #4,d4                   ; the counted shape of two
                lea     ymxr_at_synced(pc),a5   ; columns
                move.w  DT_ROWS-DT_PAYLOAD+2(a4),-(sp)  ; R
                move.w  -4(a2,d1.w),d1          ; the stride
                bra.w   ymxr_wstart_handler
                .if     YMXR_MAP
; Copy from frame, a source of one row at row 0 whose columns are the
; frame values of the target's registers (copy.S, ymxr_copystart).
.copy:          moveq   #1,d0
                move.l  d0,FX_ROWS(a3)
                lea     ymxr_fv(pc),a4
                move.l  a4,d0                   ; the loop cell
                moveq   #0,d3                   ; the place, row 0
                moveq   #FV_STRIDE,d1
                moveq   #0,d5
                move.b  FX_TARGET(a3),d5
                move.w  d5,d4
                sub.w   #25,d4                  ; the row of setToneA to setToneC
                lsl.w   #3,d4                   ; in the map, whose registers
                lea     ymxr_wcopy(pc),a2       ; are the target's (copy.S)
                adda.w  d4,a2
                lea     ymxr_at_synced(pc),a5
                bra.w   ymxr_copy_twoat
                .endif

; Init's aim of each effect's call of ymxr_widestart that the link kept,
; and the arming's: ymxr_syncwide for a tune that syncs a voice, bit 7 of
; byte 10 of its bound tune (BINARIES.md 1.5), and otherwise ymxr_widestart,
; or ymxr_copywide for a tune that copies from frame, as ymxr_copyinit
; aims them, on every init. a6 the workspace; every register kept.
ymxr_syncaim:   movem.l d1-d2/d4/a1-a4,-(sp)
                lea     ymxr_widestart(pc),a3
                .if     YMXR_MAP
                tst.b   WS_EFFECTS(a6)          ; copy from frame
                bpl.s   .tune
                lea     ymxr_copywide(pc),a3
.tune:
                .endif
                movea.l WS_TUNE(a6),a1
                tst.b   TF_SYNC(a1)
                bpl.s   .aim
                lea     ymxr_syncwide(pc),a3
.aim:           lea     ymxr_frame(pc),a4
                lea     ymxr_overs(pc),a2
                moveq   #3,d2
.each:          move.w  (a2)+,d4
                btst    d2,ymxr_k_effect+1(pc)
                beq.s   .next
                lea     (a4,d4.w),a1            ; the effect's head
                lea     WIDEN_CALL(a1),a1
                move.l  a3,d1
                sub.l   a1,d1
                move.w  d1,(a1)
.next:          dbra    d2,.each
                .if     YMXR_DEFER
                lea     ymxr_arm_wide+2(pc),a1  ; the arming's (deferred.S)
                move.l  a3,d1
                sub.l   a1,d1
                move.w  d1,(a1)
                .endif
                movem.l (sp)+,d1-d2/d4/a1-a4
                rts
                ENDPIECE synced_4
