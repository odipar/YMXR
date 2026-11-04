; lookup_ref.S - the raster monitor's halts for the lookup kinds, each
; effect's reference, and the page byte of each instance's reference.
; The handlers follow it, and lookup_checks.S follows them.

; Monitor halt for lookup kinds: one per effect, with lookup-cycle
; counts.
                .if     YMXR_PERF
                LHALT   ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5
                LHALT   ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4
                LHALT   ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0
                LHALT   ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5
                .endif

; Each effect's reference: e * LOOK_STRIDE modulo 256 past ymxr_ref, the
; offset modulo 256 its lookup reads match, and the page a page byte of
; ymxr_htab selects. The pages are in the page area after the player
; (ymxr_prefill), each a multiple of 256 bytes from its effect's
; reference.
FB_AT1          equ     (1*LOOK_STRIDE)&255
FB_AT2          equ     (2*LOOK_STRIDE)&255
FB_AT3          equ     (3*LOOK_STRIDE)&255
FB_PAGES        equ     2*(1+YMXR_DEFER)    ; an effect's fallback pages
                .if     (LOOK_STRIDE&127)=0
                .error  "two effects read their pages on one residue"
                .endif
ymxr_ref:
ymxr_ref0       equ     ymxr_ref
ymxr_ref1       equ     ymxr_ref+FB_AT1
ymxr_ref2       equ     ymxr_ref+FB_AT2
ymxr_ref3       equ     ymxr_ref+FB_AT3
; The page byte of each instance's reference, ordered by bank, effect,
; then kind as in ymxr_kinds; a page 256 bytes past it is 1 more.
ymxr_htab:
                dc.b    (ymxr_ref0-(ymxr_lk0_key+2))/256,(ymxr_ref0-(ymxr_lc0_key+2))/256,(ymxr_ref0-(ymxr_lt0_key+2))/256,(ymxr_ref0-(ymxr_ln0_key+2))/256,(ymxr_ref0-(ymxr_lb0_key+2))/256,(ymxr_ref0-(ymxr_lv0_key+2))/256,(ymxr_ref0-(ymxr_le0_key+2))/256,(ymxr_ref0-(ymxr_lf0_key+2))/256
                dc.b    (ymxr_ref1-(ymxr_lk1_key+2))/256,(ymxr_ref1-(ymxr_lc1_key+2))/256,(ymxr_ref1-(ymxr_lt1_key+2))/256,(ymxr_ref1-(ymxr_ln1_key+2))/256,(ymxr_ref1-(ymxr_lb1_key+2))/256,(ymxr_ref1-(ymxr_lv1_key+2))/256,(ymxr_ref1-(ymxr_le1_key+2))/256,(ymxr_ref1-(ymxr_lf1_key+2))/256
                dc.b    (ymxr_ref2-(ymxr_lk2_key+2))/256,(ymxr_ref2-(ymxr_lc2_key+2))/256,(ymxr_ref2-(ymxr_lt2_key+2))/256,(ymxr_ref2-(ymxr_ln2_key+2))/256,(ymxr_ref2-(ymxr_lb2_key+2))/256,(ymxr_ref2-(ymxr_lv2_key+2))/256,(ymxr_ref2-(ymxr_le2_key+2))/256,(ymxr_ref2-(ymxr_lf2_key+2))/256
                dc.b    (ymxr_ref3-(ymxr_lk3_key+2))/256,(ymxr_ref3-(ymxr_lc3_key+2))/256,(ymxr_ref3-(ymxr_lt3_key+2))/256,(ymxr_ref3-(ymxr_ln3_key+2))/256,(ymxr_ref3-(ymxr_lb3_key+2))/256,(ymxr_ref3-(ymxr_lv3_key+2))/256,(ymxr_ref3-(ymxr_le3_key+2))/256,(ymxr_ref3-(ymxr_lf3_key+2))/256
                .if     YMXR_DEFER
                dc.b    (ymxr_ref0-(ymxr_lk0b_key+2))/256,(ymxr_ref0-(ymxr_lc0b_key+2))/256,(ymxr_ref0-(ymxr_lt0b_key+2))/256,(ymxr_ref0-(ymxr_ln0b_key+2))/256,(ymxr_ref0-(ymxr_lb0b_key+2))/256,(ymxr_ref0-(ymxr_lv0b_key+2))/256,(ymxr_ref0-(ymxr_le0b_key+2))/256,(ymxr_ref0-(ymxr_lf0b_key+2))/256
                dc.b    (ymxr_ref1-(ymxr_lk1b_key+2))/256,(ymxr_ref1-(ymxr_lc1b_key+2))/256,(ymxr_ref1-(ymxr_lt1b_key+2))/256,(ymxr_ref1-(ymxr_ln1b_key+2))/256,(ymxr_ref1-(ymxr_lb1b_key+2))/256,(ymxr_ref1-(ymxr_lv1b_key+2))/256,(ymxr_ref1-(ymxr_le1b_key+2))/256,(ymxr_ref1-(ymxr_lf1b_key+2))/256
                dc.b    (ymxr_ref2-(ymxr_lk2b_key+2))/256,(ymxr_ref2-(ymxr_lc2b_key+2))/256,(ymxr_ref2-(ymxr_lt2b_key+2))/256,(ymxr_ref2-(ymxr_ln2b_key+2))/256,(ymxr_ref2-(ymxr_lb2b_key+2))/256,(ymxr_ref2-(ymxr_lv2b_key+2))/256,(ymxr_ref2-(ymxr_le2b_key+2))/256,(ymxr_ref2-(ymxr_lf2b_key+2))/256
                dc.b    (ymxr_ref3-(ymxr_lk3b_key+2))/256,(ymxr_ref3-(ymxr_lc3b_key+2))/256,(ymxr_ref3-(ymxr_lt3b_key+2))/256,(ymxr_ref3-(ymxr_ln3b_key+2))/256,(ymxr_ref3-(ymxr_lb3b_key+2))/256,(ymxr_ref3-(ymxr_lv3b_key+2))/256,(ymxr_ref3-(ymxr_le3b_key+2))/256,(ymxr_ref3-(ymxr_lf3b_key+2))/256
                .endif
