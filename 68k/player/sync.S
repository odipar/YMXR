; sync.S - the sync of a voice at a frame (SPEC.md 1.12, 4.4.1): the
; register steps of R0 to R5 of a tune that syncs a voice, the frame values
; of those registers, the sync lengths, and init's work for such a tune.
; Every part is a piece of the block `sync`, which a link keeps for a song
; a tune of which syncs a voice (doc/LINKER.md), and init aims a branch at
; it for such a tune alone, so a tune that syncs no voice runs the frame it
; ran before.

                PIECE   sync_3
; The frame values of R0 to R5, a byte each: the value the rows last set,
; masked to the register's width, 0 before a row sets the column (SPEC.md
; 3.1.7, 4.1 step 4); the row's bytes of columns 0 to 5, read once; with
; the deferred start, the target each effect's running handler writes,
; which step 4 of every frame keeps: the kept target, other than while a
; deferred start is armed and no tick has performed it, when the running
; handler writes the target it wrote before the row that armed it, whose
; target column moves the kept target first (effect.S); the row's sync
; length less 1 of each voice, A, B and C, at 10 from ymxr_srow; and at
; 14 from it each voice's current one, which the frame's syncs and the
; ticks read (SPEC.md 1.12.2, 4.4.1).
                .even
ymxr_sfv:       dc.b    0,0,0,0,0,0
ymxr_srow:      dc.b    0,0,0,0,0,0
ymxr_srun:      dc.b    0,0,0,0
ymxr_snew:      dc.b    0,0,0,0
ymxr_slen:      dc.b    0,0,0,0
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
; coarse column is 1, read on every row (1.12.1); and the voice's sync
; length less 1 into ymxr_snew, read on every row at the site \p\len,
; whose mask follows it (ymxr_syncinit): the voice's sync length column in
; a tune of version 15, and bit 4 of the coarse column otherwise (1.12.2).
                .macro  SVOICE  p,fine,coarse,len,v,ro,fo,stride,imm
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
.ns\~:
\{p}\{len}:     move.b  $7FFF(a1),d1
\{p}\{len}m:    and.b   #7,d1                   ; +2: the mask
                move.b  d1,10+\v(a4)            ; ymxr_snew
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

; The row's sync lengths made current, ymxr_snew to ymxr_slen at a4 (1.12.2,
; 4.4.1), the effects' first bytes before them (ymxr_sfirst), at level 7;
; and with the deferred start, the running targets, the frame's part of
; SBUSY. d0 clobbered.
                .macro  SLEN
                move.w  10(a4),14(a4)
                move.b  12(a4),16(a4)
                move.w  (sp)+,sr
                .if     YMXR_DEFER
                SKEEP   0,WS_FX,$134
                SKEEP   1,WS_FX+FX_SIZE,$110
                SKEEP   2,WS_FX+FX_SIZE+FX_SIZE,$120
                SKEEP   3,WS_FX+FX_SIZE+FX_SIZE+FX_SIZE,$114
                .endif
                .endm

; The sync of the voices of d4 an effect leaves (1.12.3), one after
; another in the order A, B, C at level 7: each as SYNC1 writes it, with
; the frame values at a3, \stride bytes apart.
                .macro  SSYNC   stride
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

; The sync of voice \v where bit \v of d4 is 1 (1.12.3), with its current
; sync length, to its frame values: the registers and the values in d0 to
; d3 and a5 as ymxr_ssync reads them. a2 the select register; d0 to d2
; and a5 clobbered.
                .macro  SYNC1   v,stride
                btst    #\v,d4
                beq.s   .no\~
                move.l  d3,-(sp)
                move.l  #(2*\v+1)<<24,d3        ; the coarse register and 0
                move.l  #(2*\v)<<24,d2          ; the fine register and 0
                move.l  d3,d0
                movep.w (2*\v+1)*\stride(a3),d0 ; the coarse, bits 15 to 8
                moveq   #7,d1
                sub.b   14+\v(a4),d1            ; the pads to leave out
                lsl.w   #4,d1
                movea.w d1,a5
                move.b  2*\v*\stride(a3),d1     ; the fine
                bsr.w   ymxr_ssync
                move.l  (sp)+,d3
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
                moveq   #0,d4                   ; the voices the row syncs
                SVOICE  s,_0,_1,_l0,0,0,0,1,0
                SVOICE  s,_2,_3,_l1,1,2,2,1,0
                SVOICE  s,_4,_5,_l2,2,4,4,1,0
                move.w  sr,-(sp)
                move.w  #$2700,sr
                ENDPIECE sync_3
; The first bytes of the handlers of a target of a sync, in a tune that
; starts a source on one: a piece of that handler's block.
                PIECE   synced_7
                bsr.w   ymxr_sfirst
                ENDPIECE synced_7
                PIECE   sync_8
                SLEN
                SSYNC   1
                STONE   0,0,$0000,$0100,0,1,0
                STONE   1,2,$0200,$0300,2,3,0
                STONE   2,4,$0400,$0500,4,5,0
                bra.w   ymxr_noise

; The sync of a voice (1.12.3), at level 7: 0 to the coarse register, d3,
; and a step later 0 to the fine one, d2; then a5 bytes into the pads,
; the sync length's, each a step, and the fine in d1 to the fine register;
; a step later the coarse register and its value, d0. A step is 32 cycles,
; and SSYNC_STEP1 to SSYNC_STEP3 are the nops that time the three gaps as
; SYNC_STEP1 to SYNC_STEP3 time a tick's (ticks.S). a2 the select register.
SSYNC_STEP1     equ     4
SSYNC_STEP2     equ     0
SSYNC_STEP3     equ     4
ymxr_ssync:     move.l  d3,(a2)                 ; the coarse register's 0
                NOPS    SSYNC_STEP1
                move.l  d2,(a2)                 ; the fine register's 0
                NOPS    SSYNC_STEP2
                jmp     .pads(pc,a5.w)
.pads:          .rept   7
                NOPS    8                       ; a pad
                .endr
                move.b  d1,2(a2)                ; the fine
                NOPS    SSYNC_STEP3
                move.l  d0,(a2)                 ; the coarse register and value
                rts

                .if     YMXR_MAP
                ENDPIECE sync_8
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
                SVOICE  t,_0,_1,_l0,0,0,0,FV_STRIDE,1
                SVOICE  t,_2,_3,_l1,1,2,2*FV_STRIDE,FV_STRIDE,1
                SVOICE  t,_4,_5,_l2,2,4,4*FV_STRIDE,FV_STRIDE,1
                move.w  sr,-(sp)
                move.w  #$2700,sr
                ENDPIECE copysync_1
; The first bytes as in ymxr_sregs.
                PIECE   synced_9
                bsr.w   ymxr_sfirst
                ENDPIECE synced_9
                PIECE   copysync_5
                SLEN
                SSYNC   FV_STRIDE
                STONE   0,0,$0000,$0100,0,1,1
                STONE   1,2,$0200,$0300,2,3,1
                STONE   2,4,$0400,$0500,4,5,1
                bra.w   ymxr_cnoise
; Its read sites, columns 0 to 5, as ymxr_kreads has those of the steps
; of copy from frame, and its sites of the sync lengths and their masks.
ymxr_treads:    dc.w    t_0-ymxr_frame+2,t_1-ymxr_frame+2,t_2-ymxr_frame+2
                dc.w    t_3-ymxr_frame+2,t_4-ymxr_frame+2,t_5-ymxr_frame+2
ymxr_tlreads:   dc.w    t_l0-ymxr_frame+2,t_l1-ymxr_frame+2,t_l2-ymxr_frame+2
                dc.w    t_l0m-ymxr_frame+2,t_l1m-ymxr_frame+2,t_l2m-ymxr_frame+2
                ENDPIECE copysync_5
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
; values of R0 to R5, the running targets and the sync lengths 0, and the
; block's read sites, on every init, so each subtune of a set finds them
; as it finds them alone; and for a tune that syncs a voice, bit 7 of byte
; 10 of the bound tune (BINARIES.md 1.5), a branch over the first read of
; the frame's register steps, or of those of copy from frame where the
; tune copies from frame. a0 the table's payload, d3 N, a4 ymxr_frame, a6
; the workspace; every register kept.
ymxr_syncinit:  movem.l d0-d4/a1/a3/a5,-(sp)
                lea     ymxr_sfv(pc),a3
                moveq   #24-1,d0
.clear:         clr.b   (a3)+
                dbra    d0,.clear
                .if     YMXR_MAP
                ENDPIECE sync_5
                .else
                ENDPIECE sync_8
                .endif
; In a tune that starts a source on a target of a sync, each effect's
; voice and mark of a start 0 (ymxr_sv): a piece of that handler's block.
                PIECE   synced_11
                lea     ymxr_sv(pc),a3
                clr.l   (a3)+
                clr.l   (a3)
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
; at a3; then three of the sync lengths and their masks, the offsets after
; them: in a tune of version 15, bit 6 of byte 10 of the bound tune
; (BINARIES.md 1.2), each its voice's column, the last three of the table,
; and the mask 7; otherwise a move of the coarse column and a shift that
; brings its bit 4 to bit 0, and the mask 1 (SPEC.md 1.12.2). d0 to d2,
; d4, a1 and a5 clobbered.
.sites:         moveq   #0,d1
                moveq   #6-1,d2
.site:          move.w  (a3)+,d4
                move.w  d1,d0
                bsr     ymxr_col
                addq.w  #1,d1
                dbra    d2,.site
                move.w  DT_COLUMNS-DT_PAYLOAD(a0),d1
                subq.w  #3,d1                   ; voice A's sync length column
                moveq   #3-1,d2
.lsite:         move.w  (a3)+,d4
                movea.l WS_TUNE(a6),a5
                btst    #6,TF_SYNC(a5)
                beq.s   .bit4
                move.w  d1,d0
                lea     (a4,d4.w),a5
                bsr     ymxr_colsync
                moveq   #7,d0
                bra.s   .mask
.bit4:          lea     (a4,d4.w),a5
                move.l  #$1202E809,-2(a5)       ; move.b d2,d1 and lsr.b #4,d1
                moveq   #1,d0
.mask:          move.w  4(a3),d4
                move.w  d0,(a4,d4.w)
                addq.w  #1,d1
                dbra    d2,.lsite
                rts

; The read sites of the block, columns 0 to 5, as ymxr_reads has the
; frame's, and its sites of the sync lengths and their masks.
ymxr_sreads:    dc.w    s_0-ymxr_frame+2,s_1-ymxr_frame+2,s_2-ymxr_frame+2
                dc.w    s_3-ymxr_frame+2,s_4-ymxr_frame+2,s_5-ymxr_frame+2
                dc.w    s_l0-ymxr_frame+2,s_l1-ymxr_frame+2,s_l2-ymxr_frame+2
                dc.w    s_l0m-ymxr_frame+2,s_l1m-ymxr_frame+2,s_l2m-ymxr_frame+2
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
; of ymxr_widestart; a2, a3, d4 and d5 clobbered before it.
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
; The handler's registers, immediates its head reads: the target's voice
; v, its fine register 2v and coarse 2v + 1. Its two bytes of the sync
; length, the voice's current one, and the effect's voice and its mark of
; a start, which the frame reads and clears at its register steps
; (ymxr_sfirst). At level 7, as a tick of the running handler reads them.
                lea     ymxr_at_synced(pc),a5
                move.w  d6,d5
                add.w   d5,d5
                adda.w  (a5,d5.w),a5
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                moveq   #0,d5
                move.b  FX_TARGET(a3),d5
                sub.w   #25,d5                  ; the voice
                lea     ymxr_sv(pc),a2
                move.b  d5,(a2,d6.w)
                st      4(a2,d6.w)              ; ymxr_sfresh
                lea     ymxr_slen(pc),a2
                moveq   #0,d4
                move.b  (a2,d5.w),d4            ; the current sync length less 1
                lea     ymxr_sdisp(pc),a2
                move.b  (a2,d4.w),d4
                move.b  d4,TWS_FIRST(a5)
                move.b  d4,TWS_ROWL(a5)
                add.w   d5,d5                   ; the fine register
                move.b  d5,TWS_F1(a5)
                addq.b  #1,d5                   ; the coarse register
                move.b  d5,TWS_C1(a5)
                move.b  d5,TWS_C2(a5)
                move.w  (sp)+,sr
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

; The displacement of the branch into the pads of a handler of a target
; of a sync, for each sync length less 1, 0 to 7 (ticks.S, TICKW): n - 1
; pads before the fine's write.
ymxr_sdisp:     dc.b    TWS_D1,TWS_D1-6,TWS_D1-12,TWS_D1-18
                dc.b    TWS_D1-24,TWS_D1-30,TWS_D1-36,TWS_D1-42
; Each effect's voice, 0 to 2, where its last start was on a target of a
; sync, and 4 bytes on its mark of that start, set until the next frame's
; register steps, whose first tick has the row's sync length (SPEC.md
; 5.1).
ymxr_sv:        dc.b    0,0,0,0
ymxr_sfresh:    dc.b    0,0,0,0

; The first bytes of the handlers of a target of a sync at a frame's
; register steps, before the row's sync lengths become current (SPEC.md
; 5.1): of each effect whose vector is its handler of a target of a sync,
; the length of its first tick after them, the voice's current one, or the
; row's where the start was since the frame before or a start is pending
; on the timer; and the immediate the tick writes it from after that
; tick, the row's. At level 7. a0 the workspace, a4 ymxr_srow; every
; register kept.
ymxr_sfirst:    movem.l d0-d2/a1/a3/a5-a6,-(sp)
                lea     ymxr_timers(pc),a6
                moveq   #0,d2                   ; the effect
.each:          btst    d2,WS_EFFECTS(a0)
                beq.s   .next
                movea.w (a6),a5
                movea.l (a5),a5                 ; the running handler
                lea     ymxr_at_synced(pc),a3
                move.w  d2,d0
                add.w   d0,d0
                adda.w  (a3,d0.w),a3            ; the effect's handler of a
                cmpa.l  a3,a5                   ; target of a sync
                bne.s   .next
                lea     ymxr_sv(pc),a5
                moveq   #0,d0
                move.b  (a5,d2.w),d0            ; its voice
                lea     ymxr_sdisp(pc),a1
                moveq   #0,d1
                move.b  10(a4,d0.w),d1          ; the row's length less 1
                move.b  (a1,d1.w),TWS_ROWL(a3)
                tst.b   4(a5,d2.w)              ; a start since the frame before:
                sf      4(a5,d2.w)              ; the row's length, in d1
                bne.s   .first
                .if     YMXR_DEFER
                move.w  d2,d1
                mulu    #FX_SIZE,d1
                lea     WS_FX(a0),a5
                tst.b   FX_PENDING(a5,d1.w)     ; a start pending: the row's
                beq.s   .cur
                moveq   #0,d1
                move.b  10(a4,d0.w),d1
                bra.s   .first
.cur:
                .endif
                moveq   #0,d1
                move.b  14(a4,d0.w),d1          ; the current length less 1
.first:         move.b  (a1,d1.w),TWS_FIRST(a3)
.next:          addq.l  #8,a6
                addq.w  #1,d2
                cmp.w   #4,d2
                bne.s   .each
                movem.l (sp)+,d0-d2/a1/a3/a5-a6
                rts

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
