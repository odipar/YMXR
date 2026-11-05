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
; The page byte of each instance's page 0, ordered by bank, effect, then
; kind as in ymxr_kinds; a page 256 bytes past it is 1 more. Init
; computes each one from the instance's address and its effect's
; reference (LINKKEY), so it is right in a linked player.
ymxr_htab:      dcb.b   32*(1+YMXR_DEFER),0
