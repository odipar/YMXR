; tables.S - each effect's timer, the registers of the targets that write
; several, and the parked place.

; Each effect's timer, as a start reads it: its vector, by which a start
; finds the running handler, and where deferred starts are assembled in,
; its control register, its installer from this table, the select bits of
; that register, and the shift of the select into them, bit 7 set where
; two timers share the register.
                .macro  TIMERAT vector,ctrl,inst,mask,shift
                .if     YMXR_DEFER
                dc.w    \vector,\ctrl,\inst-ymxr_timers
                .else
                dc.w    \vector,\ctrl,0
                .endif
                dc.b    \mask,\shift
                .endm
ymxr_timers:    TIMERAT $134,$FA19,ymxr_inst0,$07,$00
                TIMERAT $110,$FA1D,ymxr_inst1,$07,$80
                TIMERAT $120,$FA1B,ymxr_inst2,$07,$00
                TIMERAT $114,$FA1D,ymxr_inst3,$70,$84

                PIECE   several_2
; The targets that write several registers (SPEC.md 2.1): a register and
; the column that writes it, one slot a column and eight bytes a target,
; from target 14. The column the marker is in comes last, so the move
; that writes it leaves the marker in N and the handler tests it there.
ymxr_wide:      dc.b    0,0,  1,1,  0,0,  0,0   ; 14 setToneA:     R0 R1
                dc.b    2,0,  3,1,  0,0,  0,0   ; 15 setToneB:     R2 R3
                dc.b    4,0,  5,1,  0,0,  0,0   ; 16 setToneC:     R4 R5
                dc.b    0,0,  8,2,  1,1,  0,0   ; 17 setVoiceA:    R0 R1 R8
                dc.b    2,0,  9,2,  3,1,  0,0   ; 18 setVoiceB:    R2 R3 R9
                dc.b    4,0, 10,2,  5,1,  0,0   ; 19 setVoiceC:    R4 R5 R10
                dc.b   11,0, 12,1,  0,0,  0,0   ; 20 setEnvelope:  R11 R12
                dc.b   11,0, 12,1, 13,2,  0,0   ; 21 setBuzzer:    R11 R12 R13
                dc.b    8,1,  6,0,  0,0,  0,0   ; 22 setNoiseA:    R6 R8
                dc.b    9,1,  6,0,  0,0,  0,0   ; 23 setNoiseB:    R6 R9
                dc.b   10,1,  6,0,  0,0,  0,0   ; 24 setNoiseC:    R6 R10
                ENDPIECE several_2

; A parked place: a marker with no loop cell, so a tick of a timer no row
; has started writes $80 to R0 and stops its timer (SPEC.md 5.2.1).
ymxr_park:      dc.b    $80
                .even
