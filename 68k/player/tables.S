; tables.S - each effect's timer, the registers of the targets that write
; several, and the parked place.

; The effects whose frame code and general handler a player has, a bit
; an effect: init writes the read sites, the head and the reset of these
; effects alone, and links their handlers. A core as carried has all
; four, and a link writes the effects of the song (doc/LINKER.md).
ymxr_k_effect:  dc.w    $000F
; The instances of each kind of handler a player has, a bit an effect,
; which init links: a link writes the effects that start a source of the
; kind.
ymxr_k_square:  dc.w    $000F
ymxr_k_one:     dc.w    $000F
ymxr_k_counted: dc.w    $000F
ymxr_k_two:     dc.w    $000F
ymxr_k_three:   dc.w    $000F
ymxr_k_envelope: dc.w   $000F
; The effects that keep the remap's piece and the deferred start's of
; their frame code, a bit an effect: init aims an effect's branches at the
; two where its bit is set and the tune maps a value or defers a start.
                .if     YMXR_MAP
ymxr_k_map:     dc.w    $000F
                .endif
                .if     YMXR_DEFER
ymxr_k_defer:   dc.w    $000F
                .endif

; Each effect's handler of a kind, from the kind's table: a start reads
; the instance there, which a link moves as it leaves instances out.
                .macro  AT      kind,first
ymxr_at_\kind:  dc.w    \{first}0-ymxr_at_\kind,\{first}1-ymxr_at_\kind
                dc.w    \{first}2-ymxr_at_\kind,\{first}3-ymxr_at_\kind
                .endm
                .if     YMXR_DEFER|YMXR_MAP
                AT      tick,ymxr_tick
                AT      square,ymxr_sq
                AT      one,ymxr_one
                .endif
                .if     YMXR_MAP
; The instances of each lookup kind a player has, a bit an effect, which
; init links: a link writes the effects that map a source of the kind.
ymxr_k_lk:      dc.w    $000F
ymxr_k_lc:      dc.w    $000F
ymxr_k_lt:      dc.w    $000F
ymxr_k_ln:      dc.w    $000F
ymxr_k_lb:      dc.w    $000F
ymxr_k_lv:      dc.w    $000F
ymxr_k_le:      dc.w    $000F
ymxr_k_lf:      dc.w    $000F
                .endif

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
                AT      counted,ymxr_cnt
                AT      two,ymxr_two
                AT      three,ymxr_three
                AT      envelope,ymxr_env
                ENDPIECE several_2

; A parked place: a marker with no loop cell, so a tick of a timer no row
; has started writes $80 to R0 and stops its timer (SPEC.md 5.2.1).
ymxr_park:      dc.b    $80
                .even
