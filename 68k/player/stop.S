; stop.S - YMXR_stop: the claimed timers released and the chip silenced.

YMXR_stop:
                move.l  a6,-(sp)
                movea.l a0,a6
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                RELEASE 0,$FFFFFA19,$F0,$FFFFFA07,5
                RELEASE 1,$FFFFFA1D,$F0,$FFFFFA09,4
                RELEASE 2,$FFFFFA1B,$F0,$FFFFFA07,0
                RELEASE 3,$FFFFFA1D,$0F,$FFFFFA09,5
                move.w  (sp)+,sr
; The chip left as the next tune finds it. A row sets the registers it
; names alone (SPEC.md 4), so a tune whose first row leaves one
; unset would read the register the tune before it wrote: the envelope
; shape reaches furthest, since writing it retriggers the generator and
; a tune that sets it late runs on the shape the tune before it left.
; The three
; voices go first, so the rest is written into silence, then every
; register is zero and the mixer has each channel off. A long write
; selects and writes in one bus operation, so these are outside the
; mask a host's interrupts reach the chip through.
                move.l  #$08000000,YM_SELECT.w
                move.l  #$09000000,YM_SELECT.w
                move.l  #$0A000000,YM_SELECT.w
                moveq   #13,d0
.hush:          move.l  d0,d1
                swap    d1
                lsl.l   #8,d1                   ; the register in the select
                move.l  d1,YM_SELECT.w          ; byte, the value zero
                dbra    d0,.hush
                move.l  #$0700FF00,YM_SELECT.w  ; $3F, each channel off,
                                                ; beside the port directions
                .if     YMXR_AEOI
                move.b  WS_VECTOR(a6),MFP_VECTOR.w
                .endif
                movea.l (sp)+,a6
                rts
