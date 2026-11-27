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
; The voices whose syncs during the row are even, bit 0 voice A, bit 2
; voice B and bit 4 voice C, the number of the voice's fine register:
; bit 4 of each coarse column, read on every row and written once a
; frame, before its syncs, which the frame's and every tick's sync read
; (SPEC.md 1.12).
ymxr_seven:     dc.b    0,0
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
; to its four bits, and with \imm at 1 to the register's immediate as
; well (columns.S, CPUT); the voice's bit into d4 where bit 5 of the
; coarse column is 1, and bit 8 + \ro of d4 where bit 4 is 1, read on
; every row (1.12.1).
                .macro  SVOICE  p,fine,coarse,v,ro,fo,stride,imm
\{p}\{coarse}:  move.b  $7FFF(a1),d2
\{p}\{fine}:    move.b  $7FFF(a1),d1
                bne.s   .fs\~
                btst    #6,d2
                beq.s   .nf\~
.fs\~:          move.b  d1,\fo(a3)
                .if     \imm
                CPUT    \ro,d1
                .endif
.nf\~:          move.b  d1,\ro(a4)
                move.b  d2,\ro+1(a4)
                bpl.s   .nc\~
                move.b  d2,d0
                and.b   #$0F,d0
                move.b  d0,\fo+\stride(a3)
                .if     \imm
                CPUT    \ro+1,d0
                .endif
.nc\~:          btst    #5,d2
                beq.s   .ns\~
                bset    #\v,d4
.ns\~:          btst    #4,d2
                beq.s   .ne\~
                bset    #8+\ro,d4
.ne\~:
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
; (1.12.3), one after another in the order A, B, C at level 7: each as
; SYNC1 writes it, with the frame values at a3, \stride bytes apart. The
; even bits of the row, bits 8 to 15 of d4, reach ymxr_seven first, at
; 10 from a4, so the ticks of the frame read them.
                .macro  SSYNC   stride
                .if     YMXR_DEFER
                SKEEP   0,WS_FX,$134
                SKEEP   1,WS_FX+FX_SIZE,$110
                SKEEP   2,WS_FX+FX_SIZE+FX_SIZE,$120
                SKEEP   3,WS_FX+FX_SIZE+FX_SIZE+FX_SIZE,$114
                .endif
                move.w  d4,d0
                lsr.w   #8,d0
                move.b  10(a4),11(a4)           ; the row before's
                move.b  d0,10(a4)               ; ymxr_seven
                tst.b   d4
                beq.w   .done\~
                lea     ymxr_svoice(pc),a5
                SBUSY   0,WS_FX,$FFFFFA19,$07
                SBUSY   1,WS_FX+FX_SIZE,$FFFFFA1D,$07
                SBUSY   2,WS_FX+FX_SIZE+FX_SIZE,$FFFFFA1B,$07
                SBUSY   3,WS_FX+FX_SIZE+FX_SIZE+FX_SIZE,$FFFFFA1D,$70
                tst.b   d4
                beq.w   .done\~
                move.w  sr,-(sp)
                move.w  #$2700,sr
                SYNC1   0,\stride
                SYNC1   1,\stride
                SYNC1   2,\stride
                move.w  (sp)+,sr
.done\~:
                .endm

; The sync of voice \v where bit \v of d4 is 1 (YMXS, SPEC.md 3.9): 0 to
; its two registers, the coarse one first unless the chip's value of the
; fine one is 0, the second 0 a movep.w one step after the coarse 0, then
; its frame values, fine then coarse, the steps between of HOLD_F: even
; where bit 2 * \v + 8 of d4 is 1, and those of the coarse write where the
; fine is 0. a2 the select register; d0 to d2 clobbered.
                .macro  SYNC1   v,stride
                btst    #\v,d4
                beq.w   .no\~
                move.w  #(2*\v)<<8,d0
                move.b  2*\v*\stride(a3),d0
                move.w  #(2*\v+1)<<8,d1
                move.b  (2*\v+1)*\stride(a3),d1
                move.b  #2*\v,(a2)
                move.b  (a2),d2
                bne.s   .cf\~
                move.w  #(2*\v+1)<<8,d2
                move.b  #0,2(a2)
                bra.s   .zero\~
.cf\~:          move.w  #(2*\v)<<8,d2
                move.b  #2*\v+1,(a2)
                move.b  #0,2(a2)
                .rept   HOLD_FZ
                nop
                .endr
.zero\~:        movep.w d2,0(a2)
                tst.b   d0
                beq.s   .z\~
                btst    #2*\v+8,d4
                bne.s   .e\~
                HOLD_F  1,0
                movep.w d0,0(a2)
                movep.w d1,0(a2)
                bra.w   .no\~
.e\~:           HOLD_F  2,0
                movep.w d0,0(a2)
                movep.w d1,0(a2)
                bra.w   .no\~
.z\~:           btst    #2*\v+8,d4
                bne.s   .ez\~
                HOLD_F  1,1
                movep.w d0,0(a2)
                movep.w d1,0(a2)
                bra.s   .no\~
.ez\~:          HOLD_F  2,1
                movep.w d0,0(a2)
                movep.w d1,0(a2)
.no\~:
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
                ENDPIECE sync_3
; The general heads while the row's values change, and the heads the
; frame writes after them, in a tune that starts a source on a target of a
; sync (ymxr_sheads): pieces of that handler's block.
                PIECE   synced_7
                moveq   #0,d0
                bsr.w   ymxr_sheads
                ENDPIECE synced_7
                PIECE   sync_8
                moveq   #0,d4                   ; the voices the row syncs
                SVOICE  s,_0,_1,0,0,0,1,0
                SVOICE  s,_2,_3,1,2,2,1,0
                SVOICE  s,_4,_5,2,4,4,1,0
                SSYNC   1
                ENDPIECE sync_8
                PIECE   synced_8
                moveq   #1,d0
                bsr.w   ymxr_sheads
                ENDPIECE synced_8
                PIECE   sync_9
                STONE   0,0,$0000,$0100,0,1,0
                STONE   1,2,$0200,$0300,2,3,0
                STONE   2,4,$0400,$0500,4,5,0
                bra.w   ymxr_noise

                .if     YMXR_MAP
                ENDPIECE sync_9
; The block copysync, which a link keeps for a song that copies from frame
; and syncs a voice (doc/LINKER.md): the register steps of R0 to R5 of a
; frame of a tune that syncs a voice and copies from frame (play.S,
; ymxr_csteps), where init writes a branch over the first read of
; ymxr_csteps for such a tune alone. a3 the frame values (copy.S,
; ymxr_fv), FV_STRIDE bytes apart, and d3 the registers the ticks of copy
; from frame write, which the frame leaves to them.
                PIECE   copysync_1
ymxr_skregs:    lea     ymxr_srow(pc),a4
                ENDPIECE copysync_1
; The heads as in ymxr_sregs.
                PIECE   synced_9
                moveq   #0,d0
                bsr.w   ymxr_sheads
                ENDPIECE synced_9
                PIECE   copysync_5
                moveq   #0,d4
                SVOICE  t,_0,_1,0,0,0,FV_STRIDE,1
                SVOICE  t,_2,_3,1,2,2*FV_STRIDE,FV_STRIDE,1
                SVOICE  t,_4,_5,2,4,4*FV_STRIDE,FV_STRIDE,1
                SSYNC   FV_STRIDE
                ENDPIECE copysync_5
                PIECE   synced_10
                moveq   #1,d0
                bsr.w   ymxr_sheads
                ENDPIECE synced_10
                PIECE   copysync_6
                STONE   0,0,$0000,$0100,0,1,1
                STONE   1,2,$0200,$0300,2,3,1
                STONE   2,4,$0400,$0500,4,5,1
                bra.w   ymxr_cnoise
; Its read sites, columns 0 to 5, as ymxr_kreads has those of the steps
; of copy from frame.
ymxr_treads:    dc.w    t_0-ymxr_frame+2,t_1-ymxr_frame+2,t_2-ymxr_frame+2
                dc.w    t_3-ymxr_frame+2,t_4-ymxr_frame+2,t_5-ymxr_frame+2
                ENDPIECE copysync_6
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
                moveq   #18-1,d0
.clear:         clr.b   (a3)+
                dbra    d0,.clear
                .if     YMXR_MAP
                ENDPIECE sync_5
                .else
                ENDPIECE sync_9
                .endif
; In a tune that starts a source on a target of a sync, each effect's
; byte of ymxr_sfx 0, and the sources of two values none of whose rows has
; a fine of 0, a bit each (ymxr_sclean), off each source's first column: a
; piece of that handler's block.
                PIECE   synced_11
                lea     ymxr_sfx(pc),a3
                clr.l   (a3)
                lea     ymxr_sclean(pc),a3
                clr.l   (a3)
                clr.l   4(a3)
                clr.l   8(a3)
                clr.l   12(a3)
                movea.l WS_TUNE(a6),a1
                moveq   #0,d1
                move.b  TF_SOURCES(a1),d1       ; S
                lea     WS_SOURCES(a6),a1
                moveq   #0,d4                   ; the source
.scan:          cmp.w   d1,d4
                bcc.s   .scanned
                movea.l (a1),a5                 ; its first row
                cmp.w   #2,DT_COLUMNS-DT_PAYLOAD(a5)
                bne.s   .nextsrc
                move.l  DT_ROWS-DT_PAYLOAD(a5),d2   ; R
                beq.s   .nextsrc
.fine:          tst.b   (a5)+
                beq.s   .nextsrc                ; a fine of 0
                subq.l  #1,d2
                bne.s   .fine
                move.w  d4,d0
                lsr.w   #3,d0
                bset    d4,(a3,d0.w)            ; bit s mod 8
.nextsrc:       addq.l  #8,a1
                addq.w  #1,d4
                bra.s   .scan
.scanned:
                ENDPIECE synced_11
                PIECE   sync_10
                lea     ymxr_sreads(pc),a3
                bsr.w   .sites
                .if     YMXR_MAP
                ENDPIECE sync_10
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
                ENDPIECE sync_10
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
; The handler's slot at the general head, which runs every source and
; every chip right: a frame aims it at a head it has written (ymxr_sheads).
; And ymxr_sfx's byte of the effect: 1 for a source whose bit of
; ymxr_sclean is 1, 0 for any other and for copy from frame.
                lea     ymxr_at_synced(pc),a5
                move.w  d6,d5
                add.w   d5,d5
                adda.w  (a5,d5.w),a5
                move.b  #TWS_TOGEN,TWS_SLOT(a5)
; The general head's registers, immediates there: the target's voice v,
; its fine register 2v and coarse 2v + 1. And its parity words by the
; voice's bit of ymxr_seven, which the frame writes from then on
; (ymxr_sheads). At level 7, as a tick of the running handler reads them.
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                moveq   #0,d5
                move.b  FX_TARGET(a3),d5
                sub.w   #25,d5
                add.w   d5,d5                   ; the fine register
                move.b  d5,TWS_F1(a5)
                move.b  d5,TWS_F2(a5)
                move.b  d5,TWS_F3(a5)
                move.b  d5,TWS_F4(a5)
                move.b  d5,TWS_F5(a5)
                move.b  d5,TWS_F6(a5)
                move.w  #TWS_MODDW,TWS_MPAR(a5)
                move.w  #TWS_ODDW,TWS_FPAR(a5)
                btst    d5,ymxr_seven(pc)
                beq.s   .odd
                move.w  #TWS_EVW,TWS_MPAR(a5)
                move.w  #TWS_SEVW,TWS_FPAR(a5)
.odd:           addq.b  #1,d5                   ; the coarse register
                move.b  d5,TWS_C1(a5)
                move.b  d5,TWS_C2(a5)
                move.w  (sp)+,sr
                lea     ymxr_sfx(pc),a5
                clr.b   (a5,d6.w)
                st      ymxr_sfresh-ymxr_sfx(a5,d6.w) ; no tick yet
                .if     YMXR_MAP
                cmp.l   #-7,d0                  ; copy from frame
                beq.s   .copy
                .endif
                move.w  d1,d4                   ; eight times the source's
                lsr.w   #3,d4                   ; number, which is 1 for the
                subq.w  #1,d4                   ; first: the source
                move.w  d4,d0
                lsr.w   #3,d0
                lea     ymxr_sclean(pc),a2
                btst    d4,(a2,d0.w)            ; bit s mod 8 of byte s / 8
                sne     (a5,d6.w)
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

; Each effect's 1 where its start on a target of a sync was of a source
; whose bit of ymxr_sclean is 1, and 0 otherwise (ymxr_syncwide).
ymxr_sfx:       dc.b    0,0,0,0
; Each effect's 1 from a start on a target of a sync to the frame after
; it, whose first tick ends no cycle of a period of the row before
; (ymxr_sheads).
ymxr_sfresh:    dc.b    0,0,0,0
                .even
; The sources of two values none of whose rows has a fine of 0, the first
; value 0, a bit a source, bit s of byte s / 8 source s, which init writes
; (ymxr_syncinit): the heads of a source's rows read no row (ymxr_sheads).
ymxr_sclean:    dc.l    0,0,0,0
; The end of a head of copy from frame: with the monitor in, a bra.w to
; the handler's end (ticks.S, \{name}_htail), and otherwise the handler's
; end of interrupt, which the frame copies, and the rte.
                .macro  HEND    t
                .if     YMXR_PERF
\{t}_e:         bra.w   *
                .else
                .if     YMXR_AEOI=0
\{t}_e:         move.b  #0,$FFFFFA0F.w
                .endif
                rte
                .endif
                .endm

; A head copied into the handler of a5's room at TWS_HREG, past the
; move that a tick of the other parity runs first, a4 left at the head.
; a3 and d6 clobbered.
                .macro  HCOPY   t
                lea     \t(pc),a3
                lea     TWS_HREG(a5),a4
                moveq   #(\{t}end-\t)/2-1,d6
.c\~:           move.w  (a3)+,(a4)+
                dbra    d6,.c\~
                lea     TWS_HREG(a5),a4
                .endm

; The registers and values of a head of copy from frame at a4: d1 the
; fine register, d0 the fine frame value, a1 the frame values (copy.S,
; ymxr_fv) at the fine's.
                .macro  CPATCH  t
                move.b  d1,\{t}_z-\t+2(a4)       ; the second 0's register
                move.b  d1,\{t}_f-\t+2(a4)
                move.b  d0,\{t}_f-\t+4(a4)
                addq.b  #1,d1                   ; the coarse register
                move.b  d1,2(a4)                ; the first 0's
                move.b  d1,\{t}_c-\t+2(a4)
                move.b  FV_STRIDE(a1),\{t}_c-\t+4(a4)
                subq.b  #1,d1
                .if     YMXR_PERF
                move.w  #TWS_TAIL-TWS_HREG-(\{t}_e-\t)-2,\{t}_e-\t+2(a4)
                .else
                .if     YMXR_AEOI=0
                move.l  TWS_EOI+2(a5),\{t}_e-\t+2(a4)
                .endif
                .endif
                .endm

; The heads the frame copies into a handler's room (ticks.S, TICKW,
; \{name}_hreg) and then writes the registers and values of: a source's
; rows, odd and even, which go on to the handler's write of the row's
; fine; and with the lookup in, copy from frame, odd and even, its frame
; values as immediates. The chip's fine and the fine written are other
; than 0, so the coarse 0 is first and the fine 0 one step after it, the
; fine stays selected, and the steps run from the fine 0 to the write of
; the fine: odd, and even HOLD_FE's or HOLD_CE's nops more (ticks.S). A
; bra.w reaches the handler, whose displacement the frame writes.
                .even
ymxr_hro:       move.l  #$01000000,YM_SELECT.w  ; +2: the coarse register
                nop
ymxr_hro_z:     move.l  #$00000000,YM_SELECT.w  ; +2: the fine register
                HOLD_R
ymxr_hro_b:     bra.w   *                       ; the handler's first place
ymxr_hroend:
ymxr_hre:       move.l  #$01000000,YM_SELECT.w
                nop
ymxr_hre_z:     move.l  #$00000000,YM_SELECT.w
                HOLD_R
                .if     HOLD_FE>0
                .rept   HOLD_FE
                nop
                .endr
                .endif
ymxr_hre_b:     bra.w   *
ymxr_hreend:
                .if     YMXR_MAP
; Copy from frame: the four writes of the period, each a register and a
; value. The second 0 has an absolute long address, whose 4 cycles more
; make the time from it to the fine's write one step, and the nop before
; it makes the time from the first 0 to it one step.
ymxr_hco:       move.l  #$01000000,YM_SELECT.w  ; +2: the coarse register
                nop
ymxr_hco_z:     move.l  #$00000000,(YM_SELECT).l ; +2: the fine register
ymxr_hco_f:     move.l  #$00000000,YM_SELECT.w ; +2: the fine register, +4: the fine
ymxr_hco_c:     move.l  #$01000000,YM_SELECT.w  ; +2: the coarse register, +4: the coarse
                HEND    ymxr_hco
ymxr_hcoend:
ymxr_hce:       move.l  #$01000000,YM_SELECT.w
                nop
ymxr_hce_z:     move.l  #$00000000,(YM_SELECT).l
                .if     HOLD_CE>0
                .rept   HOLD_CE
                nop
                .endr
                .endif
ymxr_hce_f:     move.l  #$00000000,YM_SELECT.w
ymxr_hce_c:     move.l  #$01000000,YM_SELECT.w
                HEND    ymxr_hce
ymxr_hceend:
                .endif
                .if     (ymxr_hro_z-ymxr_hro)<>(ymxr_hre_z-ymxr_hre)
                .error  "the two heads of a source's rows have the fine register apart"
                .endif
                .if     (ymxr_hroend-ymxr_hro>(HREG-8))|(ymxr_hreend-ymxr_hre>(HREG-8))
                .error  "a head of a source's rows is longer than a handler's room"
                .endif
                .if     YMXR_MAP
                .if     (ymxr_hcoend-ymxr_hco>(HREG-8))|(ymxr_hceend-ymxr_hce>(HREG-8))
                .error  "a head of copy from frame is longer than a handler's room"
                .endif
                .endif

; The heads the frame writes of the handlers of a target of a sync
; (ticks.S, TICKW), for each effect that runs one: with d0 other than 0,
; after the row's values change, the general head's parity words by the
; voice's bit of ymxr_seven, a start armed or not; and with no start
; armed, with d0 0 each aimed at its general head, before the row's values
; change, and otherwise each written and aimed at its even or its odd
; head, by that bit, where the general head's reads would come out one
; way until the next frame. A source's rows: where none has a fine of 0
; (ymxr_sfx) and the chip's fine is other than 0, the voice's registers.
; Copy from frame: where the voice's fine frame value and the chip's fine
; are other than 0, those and the voice's frame values (copy.S, ymxr_fv).
; Where the voice's bit of the row before differs, and the frame is not
; the first since the start (ymxr_sfresh), the first tick after the frame
; runs that bit's parity: the head of that bit, or the general head with
; its words, and the slot then moves to the general head with the row's
; (SPEC.md 5.1). A tick runs the head the slot aims at and one byte moves
; the slot, so the frame runs at its level. The target is read
; before the table of those handlers, which a link keeps for a song that
; starts a source on a target of a sync, and the tune's effects before
; the frame values, which it keeps for a song that copies from frame. a0
; the workspace, a2 the select register; every register kept.
ymxr_sheads:    movem.l d0-d6/a1/a3-a6,-(sp)
                move.b  d0,d2
                lea     ymxr_timers(pc),a6
                moveq   #0,d5                   ; the effect
.each:          btst    d5,WS_EFFECTS(a0)
                beq.w   .next
                move.w  d5,d1
                mulu    #FX_SIZE,d1
                lea     WS_FX(a0),a1
                adda.w  d1,a1                   ; the effect's record
                moveq   #0,d1
                move.b  FX_TARGET(a1),d1
                sub.w   #25,d1                  ; a target of a sync, of voice
                bcs.w   .next                   ; d1
                cmp.w   #3,d1
                bcc.w   .next
                movea.w (a6),a5
                movea.l (a5),a5                 ; the running handler
                lea     ymxr_at_synced(pc),a4
                move.w  d5,d0
                add.w   d0,d0
                adda.w  (a4,d0.w),a4            ; the effect's handler of a
                cmpa.l  a4,a5                   ; target of a sync
                bne.w   .next
                add.w   d1,d1                   ; the fine register
                moveq   #0,d4
                tst.b   d2
                beq.s   .parity
; d4 other than 0 where the voice's bit of the row differs from the row
; before's, which the first tick after the frame reads, since that tick
; ends a cycle of the period the row before set.
                move.b  ymxr_seven(pc),d3
                move.b  ymxr_seven+1(pc),d4
                eor.b   d3,d4
                btst    d1,d4
                sne     d4
                lea     ymxr_sfresh(pc),a3
                tst.b   (a3,d5.w)
                beq.s   .ticked
                clr.b   (a3,d5.w)
                moveq   #0,d4
.ticked:
                move.w  #TWS_MODDW,d0           ; the general head's parity
                move.w  #TWS_ODDW,d6            ; words
                btst    d1,d3
                beq.s   .podd
                move.w  #TWS_EVW,d0
                move.w  #TWS_SEVW,d6
.podd:          move.w  d0,TWS_MPAR(a5)
                move.w  d6,TWS_FPAR(a5)
.parity:
                .if     YMXR_DEFER
                tst.b   FX_PENDING(a1)          ; a start armed, whose hook the
                bne.w   .next                   ; general head's path reaches
                .endif
                movea.w 2(a6),a3                ; the timer's control register
                move.b  (a3),d0
                and.b   6(a6),d0                ; its select, 0 where stopped
                beq.w   .next
                .if     YMXR_MAP
                tst.b   WS_EFFECTS(a0)          ; copy from frame
                bpl.s   .rows
                move.w  d1,d6
                mulu    #FV_STRIDE,d6
                lea     ymxr_fv(pc),a3
                adda.w  d6,a3                   ; the fine's frame value, which
                cmpa.l  FX_FIRST(a1),a3         ; copy from frame's place at
                beq.w   .copy                   ; row 0 is
                .endif
.rows:          lea     ymxr_sfx(pc),a3
                tst.b   (a3,d5.w)
                beq.w   .stubs
                tst.b   d2
                beq.w   .gen
                move.b  d1,(a2)                 ; the chip's fine
                tst.b   (a2)
                beq.w   .stubs
                move.b  ymxr_seven(pc),d3       ; the row's bit, or the row
                tst.b   d4                      ; before's where they differ
                beq.s   .rbit
                move.b  ymxr_seven+1(pc),d3
.rbit:          btst    d1,d3
                bne.s   .reven
                HCOPY   ymxr_hro
                move.w  #TWS_PTR1-TWS_HREG-(ymxr_hro_b-ymxr_hro)-2,ymxr_hro_b-ymxr_hro+2(a4)
                bra.s   .rregs
.reven:         HCOPY   ymxr_hre
                move.w  #TWS_PTR1-TWS_HREG-(ymxr_hre_b-ymxr_hre)-2,ymxr_hre_b-ymxr_hre+2(a4)
.rregs:         move.b  d1,ymxr_hro_z-ymxr_hro+2(a4) ; the fine register
                addq.b  #1,d1
                move.b  d1,2(a4)                ; the coarse register
                bra.w   .aim
.gen:           move.b  #TWS_TOGEN,TWS_SLOT(a5)
                bra.w   .next
                .if     YMXR_MAP
.copy:          tst.b   d2
                beq.s   .gen
                move.b  (a3),d0                 ; the fine frame value
                beq.w   .stubs
                move.b  d1,(a2)                 ; the chip's fine
                tst.b   (a2)
                beq.w   .stubs
                movea.l a3,a1                   ; the frame values
                move.b  ymxr_seven(pc),d3       ; the row's bit, or the row
                tst.b   d4                      ; before's where they differ
                beq.s   .cbit
                move.b  ymxr_seven+1(pc),d3
.cbit:          btst    d1,d3
                bne.s   .ceven
                HCOPY   ymxr_hco
                CPATCH  ymxr_hco
                bra.s   .aim
.ceven:         HCOPY   ymxr_hce
                CPATCH  ymxr_hce
                .endif
; The slot at the head; where the two bits differ, at a move before it
; that aims the slot at the general head, whose parity words are the
; row's, for the ticks after the first.
.aim:           move.b  #TWS_TOHEAD,TWS_SLOT(a5)
                tst.b   d4
                beq.w   .next
                lea     TWS_REG(a5),a4
                lea     TWS_SLOT(a5),a3
                move.w  #$13FC,(a4)+            ; move.b #TWS_TOGEN,(slot).l
                move.w  #TWS_TOGEN&$FF,(a4)+
                move.l  a3,(a4)
                move.b  #TWS_TOREG,TWS_SLOT(a5)
                bra.w   .next
; The general head, where the two bits differ: its parity words the row
; before's, and two pieces in the room, which the slot aims at in turn and
; each of which goes on to the general head. The first aims the slot at
; the second, and the second writes the row's parity words and aims the
; slot at the general head.
.stubs:         tst.b   d4
                beq.w   .next
                lea     TWS_REG(a5),a4
                lea     TWS_SLOT(a5),a3
                move.w  #$13FC,(a4)+            ; move.b #TWS_TOB,(slot).l
                move.w  #TWS_TOB&$FF,(a4)+
                move.l  a3,(a4)+
                move.w  #$6000,(a4)+            ; bra.w the general head
                move.w  #TWS_GENH-TWS_REG-10,(a4)+
                move.w  #$33FC,(a4)+            ; move.w #the row's word,(mpar).l
                move.w  TWS_MPAR(a5),(a4)+
                lea     TWS_MPAR(a5),a3
                move.l  a3,(a4)+
                move.w  #$33FC,(a4)+            ; and the same for fpar
                move.w  TWS_FPAR(a5),(a4)+
                lea     TWS_FPAR(a5),a3
                move.l  a3,(a4)+
                move.w  #$13FC,(a4)+            ; move.b #TWS_TOGEN,(slot).l
                move.w  #TWS_TOGEN&$FF,(a4)+
                lea     TWS_SLOT(a5),a3
                move.l  a3,(a4)+
                move.w  #$6000,(a4)+            ; bra.w the general head
                move.w  #TWS_GENH-TWS_REG-38,(a4)
                move.w  #TWS_MODDW,d0           ; the row before's words
                move.w  #TWS_ODDW,d6
                btst    d1,ymxr_seven+1(pc)
                beq.s   .sodd
                move.w  #TWS_EVW,d0
                move.w  #TWS_SEVW,d6
.sodd:          move.w  d0,TWS_MPAR(a5)
                move.w  d6,TWS_FPAR(a5)
                move.b  #TWS_TOREG,TWS_SLOT(a5)
.next:          addq.l  #8,a6
                addq.w  #1,d5
                cmp.w   #4,d5
                bne.w   .each
                movem.l (sp)+,d0-d6/a1/a3-a6
                rts
                ENDPIECE synced_4
