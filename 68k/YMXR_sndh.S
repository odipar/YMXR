; YMXR_sndh.S - the SNDH core: the player under SNDH's three entries, with
; what a host needs of the machine kept and put back. A tool binds it with
; one bound tune or more into an SNDH file (doc/BINARIES.md) and patches
; the two offsets in the descriptor; the core moves no tune itself.
;
;   +0   init  d0.w the subtune, 1 up; one out of range plays the first
;   +4   exit
;   +8   play  one row
;
; Each keeps every register. Init keeps the four timers' vectors, control,
; data and interrupt bits as it finds them, and exit puts back those of
; the timers the tune claimed: the player claims a timer only for an
; effect the tune runs, and the bound tune's effects byte says which. A
; timer the tune does not run is never touched, so what a host runs on it
; runs on through the tune.
;
; The descriptor, from the core's first byte:
;
;   +12  'YMXS'
;   +16  the descriptor's version, 1
;   +18  the bound tune's version this core reads
;   +20  the workspace's bytes before the state block, YMXR_FIXED
;   +22  where the state byte is, a word: bit 0 set while a tune plays,
;        bit 1 once it has played its last row and does not repeat
;   +24  the subtune table, a long: a word count, then a long an entry,
;        each the bound tune's offset. Patched by the tool.
;   +28  the workspace, a long. Patched by the tool.
;
; Every offset counts from the core's first byte.

sndh_core:
                bra.w   sndh_init
                bra.w   sndh_exit
                bra.w   sndh_play
                dc.b    'YMXS'
                dc.w    1
                dc.w    YMXR_VERSION
                dc.w    YMXR_FIXED
                dc.w    sndh_state-sndh_core
sndh_table:     dc.l    0
sndh_work:      dc.l    0

; The kept state of the timers, in timer order A, B, C, D.
SAV_VEC         equ     0               ; four vectors
SAV_CTRL        equ     16              ; TACR, TBCR, TCDCR
SAV_DATA        equ     19              ; four data registers
SAV_IER         equ     23              ; IERA, IERB
SAV_IMR         equ     25              ; IMRA, IMRB
SAV_SIZE        equ     28

; A timer put back where d2 has its bit: its vector, its data, its own
; bits of its control register (keep is the other timer's), and its
; enable and mask bits as they were kept. slot is its byte's place in the
; kept control bytes, and ier the enable register, IERA or IERB, with the
; kept byte at kept: the mask register and its kept byte stand 12 and 2
; on.
                .macro  PUTBACK i,vector,ctrl,keep,slot,data,ier,bit,kept
                btst    #\i,d2
                beq.s   .no\~
                move.l  SAV_VEC+\i*4(a5),\vector.w
                move.b  SAV_DATA+\i(a5),\data.w
                move.b  \ctrl.w,d0
                and.b   #\keep,d0
                move.b  SAV_CTRL+\slot(a5),d1
                and.b   #$FF-\keep,d1
                or.b    d1,d0
                move.b  d0,\ctrl.w
                bclr    #\bit,(\ier+4).w
                btst    #\bit,\kept(a5)
                beq.s   .ier\~
                bset    #\bit,\ier.w
.ier\~:         btst    #\bit,\kept+2(a5)
                beq.s   .no\~
                bset    #\bit,(\ier+12).w
.no\~:
                .endm

; The bound tune's effect i runs the timer with bit t in the claims.
                .macro  CLAIMS  i,t
                btst    #\i,d6
                beq.s   .no\~
                bset    #\t,d2
.no\~:
                .endm

; ------------------------------------------------------------------ init

sndh_init:
                movem.l d0-d7/a0-a6,-(sp)
                lea     sndh_state(pc),a6
                btst    #0,(a6)
                beq.s   .fresh
                bsr     sndh_stop               ; an init without an exit
.fresh:         move.w  sr,-(sp)
                ori.w   #$0700,sr
                lea     sndh_saved(pc),a5
                move.l  $134.w,SAV_VEC(a5)
                move.l  $120.w,SAV_VEC+4(a5)
                move.l  $114.w,SAV_VEC+8(a5)
                move.l  $110.w,SAV_VEC+12(a5)
                move.b  $FFFFFA19.w,SAV_CTRL(a5)
                move.b  $FFFFFA1B.w,SAV_CTRL+1(a5)
                move.b  $FFFFFA1D.w,SAV_CTRL+2(a5)
                move.b  $FFFFFA1F.w,SAV_DATA(a5)
                move.b  $FFFFFA21.w,SAV_DATA+1(a5)
                move.b  $FFFFFA23.w,SAV_DATA+2(a5)
                move.b  $FFFFFA25.w,SAV_DATA+3(a5)
                move.b  $FFFFFA07.w,SAV_IER(a5)
                move.b  $FFFFFA09.w,SAV_IER+1(a5)
                move.b  $FFFFFA13.w,SAV_IMR(a5)
                move.b  $FFFFFA15.w,SAV_IMR+1(a5)
                move.w  (sp)+,sr

; The subtune's bound tune, and the workspace.
                lea     sndh_core(pc),a0
                movea.l a0,a1
                adda.l  sndh_table(pc),a1
                subq.w  #1,d0
                cmp.w   (a1)+,d0
                bcs.s   .known
                moveq   #0,d0
.known:         add.w   d0,d0
                add.w   d0,d0
                adda.l  0(a1,d0.w),a0
                lea     sndh_core(pc),a1
                adda.l  sndh_work(pc),a1
                moveq   #0,d6
                move.b  TF_EFFECTS(a0),d6       ; kept through the call
                bsr     YMXR_init
                tst.l   d0
                bmi.s   .done

; The timers the player claimed: effects 0 to 3 run Timers A, D, B, C.
                moveq   #0,d2
                CLAIMS  0,0
                CLAIMS  1,3
                CLAIMS  2,1
                CLAIMS  3,2
                move.b  d2,sndh_claims-sndh_state(a6)
                move.b  #1,(a6)
.done:          movem.l (sp)+,d0-d7/a0-a6
                rts

; ------------------------------------------------------------------ exit

sndh_exit:
                movem.l d0-d7/a0-a6,-(sp)
                lea     sndh_state(pc),a6
                btst    #0,(a6)
                beq.s   .done
                bsr     sndh_stop
.done:          movem.l (sp)+,d0-d7/a0-a6
                rts

; a6 the state byte: the player stopped, the claimed timers put back as
; they were kept, the data before the control.
sndh_stop:
                clr.b   (a6)
                lea     sndh_core(pc),a0
                adda.l  sndh_work(pc),a0
                bsr     YMXR_stop
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                moveq   #0,d2
                move.b  sndh_claims-sndh_state(a6),d2
                lea     sndh_saved(pc),a5
                PUTBACK 0,$134,$FFFFFA19,$F0,0,$FFFFFA1F,$FFFFFA07,5,SAV_IER
                PUTBACK 1,$120,$FFFFFA1B,$F0,1,$FFFFFA21,$FFFFFA07,0,SAV_IER
                PUTBACK 2,$114,$FFFFFA1D,$0F,2,$FFFFFA23,$FFFFFA09,5,SAV_IER+1
                PUTBACK 3,$110,$FFFFFA1D,$F0,2,$FFFFFA25,$FFFFFA09,4,SAV_IER+1
                move.w  (sp)+,sr
                rts

; ------------------------------------------------------------------ play

sndh_play:
                movem.l d0-d7/a0-a6,-(sp)
                lea     sndh_state(pc),a6
                btst    #0,(a6)
                beq.s   .done
                lea     sndh_core(pc),a0
                adda.l  sndh_work(pc),a0
                bsr     YMXR_play
                tst.l   d0
                bpl.s   .done
                bset    #1,(a6)                 ; the last row has played
.done:          movem.l (sp)+,d0-d7/a0-a6
                rts

sndh_state:     dc.b    0
sndh_claims:    dc.b    0
sndh_saved:     dcb.b   SAV_SIZE,0
                .even

; ---------------------------------------------------------------- player

                .include "YMXR.S"
