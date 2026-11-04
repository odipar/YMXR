; handlers.S - HANDLERS, the tick handlers of each shape and effect, and
; HANDLEQU, the offsets of their operands. YMXR.S expands both.

                .macro  HANDLERS
; ---------------------------------------------------------------- ticks

                TICK    ymxr_tick0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICK    ymxr_tick1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICK    ymxr_tick2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICK    ymxr_tick3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3

                SQUARE  ymxr_sq0,$DF,$FFFFFA0F,$070,ymxr_inst0
                SQUARE  ymxr_sq1,$EF,$FFFFFA11,$007,ymxr_inst1
                SQUARE  ymxr_sq2,$FE,$FFFFFA0F,$707,ymxr_inst2
                SQUARE  ymxr_sq3,$DF,$FFFFFA11,$077,ymxr_inst3

                ONEROW  ymxr_one0,$DF,$FFFFFA0F,$070,ymxr_inst0
                ONEROW  ymxr_one1,$EF,$FFFFFA11,$007,ymxr_inst1
                ONEROW  ymxr_one2,$FE,$FFFFFA0F,$707,ymxr_inst2
                ONEROW  ymxr_one3,$DF,$FFFFFA11,$077,ymxr_inst3

                TICKC   ymxr_cnt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKC   ymxr_cnt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKC   ymxr_cnt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKC   ymxr_cnt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3

; The handlers of a target that writes two registers, and of one that
; writes three: one an effect, as the three above are.
                TICKW   ymxr_two0,2,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_two1,2,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_two2,2,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_two3,2,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3

                TICKW   ymxr_three0,3,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_three1,3,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_three2,3,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_three3,3,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3

; The counted shape of two columns: `setEnvelope` writes R11 and R12 and
; both read every bit of their byte, so the rows of a source on it are
; whole bytes and a tick counts them (SPEC.md 2.1.3, 3.1.6).
                TICKW   ymxr_env0,2,1,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_env1,2,1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_env2,2,1,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_env3,2,1,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                .if     YMXR_MAP
; Bank-0 lookup instances follow identity handlers. Pad each of eight
; kinds per effect so its page-read displacement matches its pages
; modulo 256. With deferred support, align bank 1 to the same residue.
; Without the monitor, all kinds use the general halt.
                .if     YMXR_PERF=0
ymxr_lhalt0     equ     ymxr_tick0_halt
ymxr_lhalt1     equ     ymxr_tick1_halt
ymxr_lhalt2     equ     ymxr_tick2_halt
ymxr_lhalt3     equ     ymxr_tick3_halt
                .endif
ymxr_look:
                LPAD    KEY_LT,ymxr_ref0
                TICKWL  ymxr_lt0,2,0,2,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lt0_end:
                LPAD    KEY_LT,ymxr_ref1
                TICKWL  ymxr_lt1,2,0,2,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lt1_end:
                LPAD    KEY_LT,ymxr_ref2
                TICKWL  ymxr_lt2,2,0,2,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lt2_end:
                LPAD    KEY_LT,ymxr_ref3
                TICKWL  ymxr_lt3,2,0,2,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lt3_end:
                LPAD    KEY_LE,ymxr_ref0
                TICKWL  ymxr_le0,2,1,2,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_le0_end:
                LPAD    KEY_LE,ymxr_ref1
                TICKWL  ymxr_le1,2,1,2,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_le1_end:
                LPAD    KEY_LE,ymxr_ref2
                TICKWL  ymxr_le2,2,1,2,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_le2_end:
                LPAD    KEY_LE,ymxr_ref3
                TICKWL  ymxr_le3,2,1,2,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_le3_end:
                LPAD    KEY_LF,ymxr_ref0
                TICKWL  ymxr_lf0,2,1,1,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lf0_end:
                LPAD    KEY_LF,ymxr_ref1
                TICKWL  ymxr_lf1,2,1,1,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lf1_end:
                LPAD    KEY_LF,ymxr_ref2
                TICKWL  ymxr_lf2,2,1,1,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lf2_end:
                LPAD    KEY_LF,ymxr_ref3
                TICKWL  ymxr_lf3,2,1,1,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lf3_end:
                LPAD    KEY_LK,ymxr_ref0
                TICKL   ymxr_lk0,0,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lk0_end:
                LPAD    KEY_LK,ymxr_ref1
                TICKL   ymxr_lk1,0,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lk1_end:
                LPAD    KEY_LK,ymxr_ref2
                TICKL   ymxr_lk2,0,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lk2_end:
                LPAD    KEY_LK,ymxr_ref3
                TICKL   ymxr_lk3,0,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lk3_end:
                LPAD    KEY_LB,ymxr_ref0
                TICKWL  ymxr_lb0,3,0,3,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lb0_end:
                LPAD    KEY_LB,ymxr_ref1
                TICKWL  ymxr_lb1,3,0,3,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lb1_end:
                LPAD    KEY_LB,ymxr_ref2
                TICKWL  ymxr_lb2,3,0,3,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lb2_end:
                LPAD    KEY_LB,ymxr_ref3
                TICKWL  ymxr_lb3,3,0,3,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lb3_end:
                LPAD    KEY_LN,ymxr_ref0
                TICKWL  ymxr_ln0,2,0,1,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_ln0_end:
                LPAD    KEY_LN,ymxr_ref1
                TICKWL  ymxr_ln1,2,0,1,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_ln1_end:
                LPAD    KEY_LN,ymxr_ref2
                TICKWL  ymxr_ln2,2,0,1,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_ln2_end:
                LPAD    KEY_LN,ymxr_ref3
                TICKWL  ymxr_ln3,2,0,1,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_ln3_end:
                LPAD    KEY_LC,ymxr_ref0
                TICKL   ymxr_lc0,1,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lc0_end:
                LPAD    KEY_LC,ymxr_ref1
                TICKL   ymxr_lc1,1,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lc1_end:
                LPAD    KEY_LC,ymxr_ref2
                TICKL   ymxr_lc2,1,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lc2_end:
                LPAD    KEY_LC,ymxr_ref3
                TICKL   ymxr_lc3,1,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lc3_end:
                LPAD    KEY_LV,ymxr_ref0
                TICKWL  ymxr_lv0,3,0,2,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lv0_end:
                LPAD    KEY_LV,ymxr_ref1
                TICKWL  ymxr_lv1,3,0,2,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lv1_end:
                LPAD    KEY_LV,ymxr_ref2
                TICKWL  ymxr_lv2,3,0,2,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lv2_end:
                LPAD    KEY_LV,ymxr_ref3
                TICKWL  ymxr_lv3,3,0,2,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lv3_end:
                .if     YMXR_DEFER
                ds.b    (ymxr_tick0-*)&255
                .endif
ymxr_bank0end:
                .endif

                .if     YMXR_DEFER
; Bank 1: every handler again, in the order of bank 0, BANK bytes on. A
; deferred start on a running timer is armed in the bank its vector does
; not point into, so the handler its first tick reads is patched whole
; while the running one plays on (SPEC.md 4.8).
ymxr_bank1:
                TICK    ymxr_tick0b,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICK    ymxr_tick1b,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICK    ymxr_tick2b,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICK    ymxr_tick3b,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                SQUARE  ymxr_sq0b,$DF,$FFFFFA0F,$070,ymxr_inst0
                SQUARE  ymxr_sq1b,$EF,$FFFFFA11,$007,ymxr_inst1
                SQUARE  ymxr_sq2b,$FE,$FFFFFA0F,$707,ymxr_inst2
                SQUARE  ymxr_sq3b,$DF,$FFFFFA11,$077,ymxr_inst3
                ONEROW  ymxr_one0b,$DF,$FFFFFA0F,$070,ymxr_inst0
                ONEROW  ymxr_one1b,$EF,$FFFFFA11,$007,ymxr_inst1
                ONEROW  ymxr_one2b,$FE,$FFFFFA0F,$707,ymxr_inst2
                ONEROW  ymxr_one3b,$DF,$FFFFFA11,$077,ymxr_inst3
                TICKC   ymxr_cnt0b,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKC   ymxr_cnt1b,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKC   ymxr_cnt2b,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKC   ymxr_cnt3b,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                TICKW   ymxr_two0b,2,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_two1b,2,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_two2b,2,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_two3b,2,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                TICKW   ymxr_three0b,3,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_three1b,3,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_three2b,3,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_three3b,3,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                TICKW   ymxr_env0b,2,1,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                TICKW   ymxr_env1b,2,1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                TICKW   ymxr_env2b,2,1,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                TICKW   ymxr_env3b,2,1,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                .if     YMXR_MAP
                LPAD    KEY_LT,ymxr_ref0
                TICKWL  ymxr_lt0b,2,0,2,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lt0b_end:
                LPAD    KEY_LT,ymxr_ref1
                TICKWL  ymxr_lt1b,2,0,2,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lt1b_end:
                LPAD    KEY_LT,ymxr_ref2
                TICKWL  ymxr_lt2b,2,0,2,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lt2b_end:
                LPAD    KEY_LT,ymxr_ref3
                TICKWL  ymxr_lt3b,2,0,2,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lt3b_end:
                LPAD    KEY_LE,ymxr_ref0
                TICKWL  ymxr_le0b,2,1,2,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_le0b_end:
                LPAD    KEY_LE,ymxr_ref1
                TICKWL  ymxr_le1b,2,1,2,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_le1b_end:
                LPAD    KEY_LE,ymxr_ref2
                TICKWL  ymxr_le2b,2,1,2,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_le2b_end:
                LPAD    KEY_LE,ymxr_ref3
                TICKWL  ymxr_le3b,2,1,2,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_le3b_end:
                LPAD    KEY_LF,ymxr_ref0
                TICKWL  ymxr_lf0b,2,1,1,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lf0b_end:
                LPAD    KEY_LF,ymxr_ref1
                TICKWL  ymxr_lf1b,2,1,1,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lf1b_end:
                LPAD    KEY_LF,ymxr_ref2
                TICKWL  ymxr_lf2b,2,1,1,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lf2b_end:
                LPAD    KEY_LF,ymxr_ref3
                TICKWL  ymxr_lf3b,2,1,1,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lf3b_end:
                LPAD    KEY_LK,ymxr_ref0
                TICKL   ymxr_lk0b,0,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lk0b_end:
                LPAD    KEY_LK,ymxr_ref1
                TICKL   ymxr_lk1b,0,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lk1b_end:
                LPAD    KEY_LK,ymxr_ref2
                TICKL   ymxr_lk2b,0,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lk2b_end:
                LPAD    KEY_LK,ymxr_ref3
                TICKL   ymxr_lk3b,0,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lk3b_end:
                LPAD    KEY_LB,ymxr_ref0
                TICKWL  ymxr_lb0b,3,0,3,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lb0b_end:
                LPAD    KEY_LB,ymxr_ref1
                TICKWL  ymxr_lb1b,3,0,3,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lb1b_end:
                LPAD    KEY_LB,ymxr_ref2
                TICKWL  ymxr_lb2b,3,0,3,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lb2b_end:
                LPAD    KEY_LB,ymxr_ref3
                TICKWL  ymxr_lb3b,3,0,3,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lb3b_end:
                LPAD    KEY_LN,ymxr_ref0
                TICKWL  ymxr_ln0b,2,0,1,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_ln0b_end:
                LPAD    KEY_LN,ymxr_ref1
                TICKWL  ymxr_ln1b,2,0,1,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_ln1b_end:
                LPAD    KEY_LN,ymxr_ref2
                TICKWL  ymxr_ln2b,2,0,1,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_ln2b_end:
                LPAD    KEY_LN,ymxr_ref3
                TICKWL  ymxr_ln3b,2,0,1,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_ln3b_end:
                LPAD    KEY_LC,ymxr_ref0
                TICKL   ymxr_lc0b,1,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lc0b_end:
                LPAD    KEY_LC,ymxr_ref1
                TICKL   ymxr_lc1b,1,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lc1b_end:
                LPAD    KEY_LC,ymxr_ref2
                TICKL   ymxr_lc2b,1,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lc2b_end:
                LPAD    KEY_LC,ymxr_ref3
                TICKL   ymxr_lc3b,1,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lc3b_end:
                LPAD    KEY_LV,ymxr_ref0
                TICKWL  ymxr_lv0b,3,0,2,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lv0b_end:
                LPAD    KEY_LV,ymxr_ref1
                TICKWL  ymxr_lv1b,3,0,2,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lv1b_end:
                LPAD    KEY_LV,ymxr_ref2
                TICKWL  ymxr_lv2b,3,0,2,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lv2b_end:
                LPAD    KEY_LV,ymxr_ref3
                TICKWL  ymxr_lv3b,3,0,2,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lv3b_end:
ymxr_bank1end:
                .endif

; The installers, one an effect: the start a deferred row armed, performed
; at the tick that reads the running source's last row. The hook in the
; running handler branches here: the word it replaced is written back,
; the timer is stopped where the start sets timerReset, the count and the
; select are written, and the vector is aimed at the armed handler, whose
; next tick reads the new source (SPEC.md 4.8). Every operand is patched
; at the frame that arms the start.
                INSTALL ymxr_inst0,$FFFFFA19,$FFFFFA1F,$F0,$FFFFFA07,5,0,0,$134,$DF,$FFFFFA0F
                INSTALL ymxr_inst1,$FFFFFA1D,$FFFFFA25,$F0,$FFFFFA09,4,0,1,$110,$EF,$FFFFFA11
                INSTALL ymxr_inst2,$FFFFFA1B,$FFFFFA21,$F0,$FFFFFA07,0,0,0,$120,$FE,$FFFFFA0F
                INSTALL ymxr_inst3,$FFFFFA1D,$FFFFFA23,$0F,$FFFFFA09,5,4,1,$114,$DF,$FFFFFA11
                .endif
ymxr_reach:                             ; past the last handler, which the
                                        ; reach init measures runs to
                .endm

                .macro  HANDLEQU
; The offsets the patching writes at, measured off the first handler's
; labels: the row it writes, the marker it reads, and its loop cell.
; A monitor's mark is between those blocks, and the distances follow
; it. Every handler is laid out by the one macro, so one handler's
; offsets are all four handlers'. The rig reads them back.
TICK_SEL        equ     ymxr_tick0_row-ymxr_tick0+3   ; the target register
TICK_PTR        equ     ymxr_tick0_row-ymxr_tick0+8   ; the place: a tick's row
TICK_STEP       equ     ymxr_tick0_step-ymxr_tick0+2  ; the place's address
TICK_LOOPT      equ     ymxr_tick0_mark-ymxr_tick0+2  ; the loop cell's address
TICK_LOOPR      equ     ymxr_tick0_mark-ymxr_tick0+10 ; the same
TICK_LOOPW      equ     ymxr_tick0_mark-ymxr_tick0+14 ; the place's address
TICK_CELL       equ     ymxr_tick0_cell-ymxr_tick0    ; row RR, or 0 once
                .if     YMXR_NEST
TICK_SR         equ     ymxr_tick0_sr-ymxr_tick0      ; the level it drops
                .endif
TICK_SIZE       equ     ymxr_tick1-ymxr_tick0

; The counted handler, the same way: the register it selects, the place,
; the address of the place, the counter's address in the subq that steps
; it, the loop cell and its address, the rows of a pass through the loop
; and the counter the reload writes to.
TICKC_SEL       equ     ymxr_cnt0_row-ymxr_cnt0+3
TICKC_PTR       equ     ymxr_cnt0_row-ymxr_cnt0+8
TICKC_STEP      equ     ymxr_cnt0_step-ymxr_cnt0+2
TICKC_CNTA      equ     ymxr_cnt0_cnt-ymxr_cnt0+2
TICKC_LOOPT     equ     ymxr_cnt0_mark-ymxr_cnt0+2
TICKC_LOOPR     equ     ymxr_cnt0_mark-ymxr_cnt0+10
TICKC_LOOPW     equ     ymxr_cnt0_mark-ymxr_cnt0+14
TICKC_RELD      equ     ymxr_cnt0_reld-ymxr_cnt0+2
TICKC_RELDA     equ     ymxr_cnt0_reld-ymxr_cnt0+4
TICKC_CELL      equ     ymxr_cnt0_cell-ymxr_cnt0
TICKC_LEFT      equ     ymxr_cnt0_left-ymxr_cnt0
TICKC_SIZE      equ     ymxr_cnt1-ymxr_cnt0

; A square's handler, the same way: the register it selects, the row it
; is at, the two rows' difference, and the address of the row it
; is at, which init writes once.
SQ_SEL          equ     ymxr_sq0_sel-ymxr_sq0+3
SQ_VAL          equ     ymxr_sq0_val-ymxr_sq0+3
SQ_MASK         equ     ymxr_sq0_tog-ymxr_sq0+3
SQ_PTR          equ     ymxr_sq0_tog-ymxr_sq0+4
SQ_SR           equ     ymxr_sq0_val-ymxr_sq0+6
SQ_SIZE         equ     ymxr_sq1-ymxr_sq0

; A one-row source's handler: the register it selects and the row it
; writes, both immediates a start puts there, those two fields alone.
ONE_SEL         equ     ymxr_one0_sel-ymxr_one0+3
ONE_VAL         equ     ymxr_one0_val-ymxr_one0+3
ONE_SR          equ     ymxr_one0_val-ymxr_one0+6
ONE_SIZE        equ     ymxr_one1-ymxr_one0

                TWAT    TW2,ymxr_two0
                TWAT    TW3,ymxr_three0
TW3_SEL3        equ     ymxr_three0_sel3-ymxr_three0+3
TW3_PTR3        equ     ymxr_three0_ptr3-ymxr_three0+2
TW3_STEP3       equ     ymxr_three0_step3-ymxr_three0+2
TW3_LOOP3R      equ     ymxr_three0_loop3-ymxr_three0+2
TW3_LOOP3W      equ     ymxr_three0_loop3-ymxr_three0+6
TW2_SIZE        equ     ymxr_two1-ymxr_two0
TW3_SIZE        equ     ymxr_three1-ymxr_three0

; The counted shape of two columns, the offsets above and four besides:
; the counter's address in the subq that steps it, the rows of a pass
; through the loop, the counter the reload writes to, and the counter.
                TWAT    TWC,ymxr_env0
TWC_CNT         equ     ymxr_env0_cnt-ymxr_env0+2
TWC_RELD        equ     ymxr_env0_reld-ymxr_env0+2
TWC_RELDA       equ     ymxr_env0_reld-ymxr_env0+4
TWC_LEFT        equ     ymxr_env0_left-ymxr_env0
TWC_SIZE        equ     ymxr_env1-ymxr_env0

                .if     YMXR_DEFER
; A pending start (SPEC.md 4.8): bank 1 at a fixed distance from bank 0;
; in each handler the word the hook writes over, at the head of the path
; that ends the source, and the hook it branches to; the square's copy of
; the eori that moves to its other row; and the installer's operands.
BANK            equ     ymxr_bank1-ymxr_tick0
TICK_LAST       equ     ymxr_tick0_mark-ymxr_tick0
TICK_HOOK       equ     ymxr_tick0_hook-ymxr_tick0
TICKC_LAST      equ     ymxr_cnt0_mark-ymxr_cnt0
TICKC_HOOK      equ     ymxr_cnt0_hook-ymxr_cnt0
SQ_LAST         equ     ymxr_sq0_tog-ymxr_sq0
SQ_HOOK         equ     ymxr_sq0_hook-ymxr_sq0
SQ_HMASK        equ     ymxr_sq0_htog-ymxr_sq0+3
SQ_HPTR         equ     ymxr_sq0_htog-ymxr_sq0+4
ONE_LAST        equ     ymxr_one0_end-ymxr_one0
ONE_HOOK        equ     ymxr_one0_hook-ymxr_one0
INST_BACKW      equ     ymxr_inst0_back-ymxr_inst0+2
INST_BACKA      equ     ymxr_inst0_back-ymxr_inst0+4
INST_SKIP       equ     ymxr_inst0_skip-ymxr_inst0
INST_SKIPW      equ     $6000|(ymxr_inst0_run-ymxr_inst0_skip-2)
INST_CNT        equ     ymxr_inst0_cnt-ymxr_inst0+3
INST_VEC        equ     ymxr_inst0_vec-ymxr_inst0+2
INST_SEL        equ     ymxr_inst0_sel-ymxr_inst0+3   ; a control register for one timer
INSTS_SEL       equ     ymxr_inst1_sel-ymxr_inst1+3   ; one that two timers share
                .endif

; Every handler that steps a place keeps it at one offset, which a start
; reads through the vector for every shape the timer runs; bank 1 lays the
; handlers out as bank 0 does; and each hook is within the byte a bra.s
; branches by.
                .if     (TICKC_PTR<>TICK_PTR)|(TW2_PTR1<>TICK_PTR)|(TW3_PTR1<>TICK_PTR)|(TWC_PTR1<>TICK_PTR)
                .error  "a handler that steps a place keeps it at another offset"
                .endif
                .if     YMXR_DEFER
                .if     (ymxr_env3b-ymxr_env3)<>BANK
                .error  "bank 1 is laid out apart from bank 0"
                .endif
                .if     (TICK_HOOK-TICK_LAST-2)>127
                .error  "the general handler's hook is past a bra.s"
                .endif
                .if     (TICKC_HOOK-TICKC_LAST-2)>127
                .error  "the counted handler's hook is past a bra.s"
                .endif
                .if     (TW2_HOOK-TW2_LAST-2)>127
                .error  "the handler of two columns has its hook past a bra.s"
                .endif
                .if     (TW3_HOOK-TW3_LAST-2)>127
                .error  "the handler of three columns has its hook past a bra.s"
                .endif
                .if     (TWC_HOOK-TWC_LAST-2)>127
                .error  "the counted handler of two columns has its hook past a bra.s"
                .endif
                .if     (SQ_HOOK-SQ_LAST-2)>127
                .error  "the square's hook is past a bra.s"
                .endif
                .if     (ONE_HOOK-ONE_LAST-2)>127
                .error  "the one-row handler's hook is past a bra.s"
                .endif
; The word a hook writes, out of the macro defined above the handlers.
                HOOKW   TICK
                HOOKW   TICKC
                HOOKW   TW2
                HOOKW   TW3
                HOOKW   TWC
                HOOKW   SQ
                HOOKW   ONE
                .if     (ymxr_inst1_skip-ymxr_inst1)<>INST_SKIP
                .error  "the installers place the skip apart"
                .endif
                .endif
                .endm
