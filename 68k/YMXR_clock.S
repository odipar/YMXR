; The entries of a mixed-rate SNDH file. The ordinary core follows
; this code. The writer patches the callback rate at 18.
clock:
                bra.w   init
                bra.w   core+4
                bra.w   play
                dc.b    'YMXC'
                dc.w    core-clock
rate:           dc.w    0

; Read the selected bound tune's rate and start a new phase.
init:
                movem.l d0/a0-a1,-(sp)
                lea     core(pc),a0
                movea.l a0,a1
                adda.l  28(a0),a1
                subq.w  #1,d0
                cmp.w   (a1)+,d0
                bcs.s   .known
                moveq   #0,d0
.known:         lsl.w   #2,d0
                adda.l  0(a1,d0.w),a0
                lea     phase(pc),a1
                clr.w   (a1)
                move.w  6(a0),2(a1)
                movem.l (sp)+,d0/a0-a1
                bra.w   core

; Add the subtune rate once a callback. A row is due when the phase
; reaches the callback rate. The program stub counts the rows played.
play:
                movem.l d0-d1/a0,-(sp)
                lea     phase(pc),a0
                clr.b   4(a0)
                moveq   #0,d0
                move.w  (a0),d0
                moveq   #0,d1
                move.w  2(a0),d1
                add.l   d1,d0
                move.w  rate(pc),d1
                cmp.l   d1,d0
                bcs.s   .wait
                sub.l   d1,d0
                move.w  d0,(a0)
                st      4(a0)
                movem.l (sp)+,d0-d1/a0
                bra.w   core+8
.wait:          move.w  d0,(a0)
                movem.l (sp)+,d0-d1/a0
                rts

phase:          dc.w    0
active:         dc.w    0
played:         dc.b    0
                .even
core:
