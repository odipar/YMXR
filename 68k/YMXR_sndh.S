; YMXR_sndh.S - SNDH host for the player (doc/BINARIES.md).
; Tools append bound tunes and patch the descriptor's table and workspace
; offsets. Entries preserve all registers:
;
;   +0   init  d0.w=subtune, numbered from 1; out of range selects 1
;   +4   exit
;   +8   play  one tune row
;
; Init saves timer vectors, control and interrupt bits, and vector $60.
; Exit restores vector $60 and the timers claimed by this tune. Timers
; outside the tune remain available to the host. Data registers read the
; live count rather than the reload value, so a host restores reloads
; separately when required.
;
; Descriptor offsets from the core's first byte:
;
;   +12  'YMXS'
;   +16  descriptor version, 1
;   +18  highest supported bound version: 9, or 10 with lookup support
;   +20  fixed workspace bytes before DTX state, YMXR_FIXED
;   +22  flags, a word:
;          bit 0  raster monitor, YMXR_PERF=1
;          bit 1  lean ticks, YMXR_NEST=0 and YMXR_AEOI=1
;          bit 2  program-counter source reads, YMXR_PCREL=1
;          bit 3  deferred starts, YMXR_DEFER=1; required by effects bit 4
;          bit 4  optional lookups, YMXR_MAP=1; required by effects bit 5
;   +24  state-byte offset: bit 0 while playing, bit 1 after a tune ends
;   +26  source-reach origin: first handler with lookup support, else 0
;   +28  patched subtune-table offset: word count, then one long per tune
;   +32  patched workspace offset
;
; Lean ticks require host MFP handlers without nesting and player control
; of the vector register. Program-counter reads require sources within
; signed-word reach of the origin at +26. Init aligns workspace to four
; bytes; tools reserve two extra bytes for an SNDH loaded even.

sndh_core:
                bra.w   sndh_init
                bra.w   sndh_exit
                bra.w   sndh_play
                dc.b    'YMXS'
                dc.w    1
                dc.w    YMXR_VERSION
                dc.w    YMXR_FIXED
                dc.w    YMXR_PERF|(YMXR_AEOI<<1)|(YMXR_PCREL<<2)|(YMXR_DEFER<<3)|(YMXR_MAP<<4)
                                        ; flags: monitor, lean, PC-relative,
                                        ; deferred start, optional lookup
                dc.w    sndh_state-sndh_core
                dc.w    (ymxr_tick0-sndh_core)*YMXR_MAP ; where the reach is
                                        ; measured from
sndh_table:     dc.l    0
sndh_work:      dc.l    0

; The kept state of the timers, in timer order A, B, C, D.
SAV_VEC         equ     0               ; four vectors
SAV_CTRL        equ     16              ; TACR, TBCR, TCDCR
SAV_IER         equ     19              ; IERA, IERB
SAV_IMR         equ     21              ; IMRA, IMRB
SAV_SPUR        equ     24              ; the vector at $60
SAV_SIZE        equ     28

; A timer restored where d2 has its bit: its vector, its bits of its
; control register (keep is the other timer's), and its enable and mask
; bits as they were kept. slot is its byte's place in the kept control
; bytes, and ier the enable register, IERA or IERB, with the kept byte at
; kept: the mask register and its kept byte are 12 and 2 on.
                .macro  PUTBACK i,vector,ctrl,keep,slot,ier,bit,kept
                btst    #\i,d2
                beq.s   .no\~
                move.l  SAV_VEC+\i*4(a5),\vector.w
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
                move.w  d0,d7                   ; the subtune, kept through the stop
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
                move.b  $FFFFFA07.w,SAV_IER(a5)
                move.b  $FFFFFA09.w,SAV_IER+1(a5)
                move.b  $FFFFFA13.w,SAV_IMR(a5)
                move.b  $FFFFFA15.w,SAV_IMR+1(a5)
                move.w  (sp)+,sr

; The subtune's bound tune, and the workspace.
                lea     sndh_core(pc),a0
                movea.l a0,a1
                adda.l  sndh_table(pc),a1
                subq.w  #1,d7
                cmp.w   (a1)+,d7
                bcs.s   .known
                moveq   #0,d7
.known:         add.w   d7,d7
                add.w   d7,d7
                adda.l  0(a1,d7.w),a0
                bsr     sndh_workspace
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

; The vector at $60, the 68000's spurious interrupt. The MFP raises an
; interrupt and the 68000 acknowledges it an instruction or more later,
; and a write of the player's that clears the pending bit or the enable
; bit inside that window leaves the MFP with no vector to place on the
; bus: the 68000 runs exception 24 rather than the timer's handler. The
; handler below returns from it, since the tick it would have run is the
; one the write cancelled. Raising the interrupt level around the write
; is no substitute: an acknowledge the 68000 has begun runs to its end at
; any level. The host's vector is back at exit.
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                lea     sndh_saved(pc),a5
                move.l  $60.w,SAV_SPUR(a5)
                lea     sndh_spurious(pc),a1
                move.l  a1,$60.w
                move.w  (sp)+,sr
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
; they were kept.
sndh_stop:
                clr.b   (a6)
                bsr     sndh_workspace
                movea.l a1,a0
                bsr     YMXR_stop
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                moveq   #0,d2
                move.b  sndh_claims-sndh_state(a6),d2
                lea     sndh_saved(pc),a5
                move.l  SAV_SPUR(a5),$60.w
                PUTBACK 0,$134,$FFFFFA19,$F0,0,$FFFFFA07,5,SAV_IER
                PUTBACK 1,$120,$FFFFFA1B,$F0,1,$FFFFFA07,0,SAV_IER
                PUTBACK 2,$114,$FFFFFA1D,$0F,2,$FFFFFA09,5,SAV_IER+1
                PUTBACK 3,$110,$FFFFFA1D,$F0,2,$FFFFFA09,4,SAV_IER+1
                move.w  (sp)+,sr
                rts

; a1 the workspace: the patched offset from the core, rounded up to a
; long. d0 clobbered.
sndh_workspace:
                lea     sndh_core(pc),a1
                adda.l  sndh_work(pc),a1
                move.l  a1,d0
                addq.l  #3,d0
                and.b   #$FC,d0
                movea.l d0,a1
                rts

; ------------------------------------------------------------------ play

sndh_play:
                movem.l d0-d7/a0-a6,-(sp)
                lea     sndh_state(pc),a6
                btst    #0,(a6)
                beq.s   .done
                bsr     sndh_workspace
                movea.l a1,a0
                bsr     YMXR_play
                tst.l   d0
                bpl.s   .done
                bset    #1,(a6)                 ; the last row has played
.done:          movem.l (sp)+,d0-d7/a0-a6
                rts

; Exception 24, for as long as a tune plays: the acknowledge found no
; vector, and the tick it belonged to is one the player already cancelled.
sndh_spurious:  rte

sndh_state:     dc.b    0
sndh_claims:    dc.b    0
                .even
sndh_saved:     dcb.b   SAV_SIZE,0

; ---------------------------------------------------------------- player

                .include "YMXR.S"
