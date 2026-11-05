; handlers.S - HANDLERS, the tick handlers of each shape and effect, and
; HANDLEQU, the offsets of their operands. YMXR.S expands both.

                .macro  INSTALLERS
; The installers, one an effect: the start a deferred row armed, performed
; at the tick that reads the running source's last row. The hook in the
; running handler branches here: the word it replaced is written back,
; the timer is stopped where the start sets timerReset, the count and the
; select are written, and the vector is aimed at the armed handler, whose
; next tick reads the new source (SPEC.md 4.8). Every operand is patched
; at the frame that arms the start. Each is a piece of its effect's
; deferred start's block, defer<e>_2, as a hook reaches it where the
; effect defers a start alone (doc/LINKER.md).
                PIECE   defer0_2
                INSTALL ymxr_inst0,$FFFFFA19,$FFFFFA1F,$F0,$FFFFFA07,5,0,0,$134,$DF,$FFFFFA0F
                ENDPIECE defer0_2
                PIECE   defer1_2
                INSTALL ymxr_inst1,$FFFFFA1D,$FFFFFA25,$F0,$FFFFFA09,4,0,1,$110,$EF,$FFFFFA11
                ENDPIECE defer1_2
                PIECE   defer2_2
                INSTALL ymxr_inst2,$FFFFFA1B,$FFFFFA21,$F0,$FFFFFA07,0,0,0,$120,$FE,$FFFFFA0F
                ENDPIECE defer2_2
                PIECE   defer3_2
                INSTALL ymxr_inst3,$FFFFFA1D,$FFFFFA23,$0F,$FFFFFA09,5,4,1,$114,$DF,$FFFFFA11
                ENDPIECE defer3_2
                .endm

                .macro  HANDLERS
; ---------------------------------------------------------------- ticks

                PIECE   effect0_2
                TICK    ymxr_tick0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE effect0_2
                PIECE   effect1_2
                TICK    ymxr_tick1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE effect1_2
                PIECE   effect2_2
                TICK    ymxr_tick2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE effect2_2
                PIECE   effect3_2
                TICK    ymxr_tick3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE effect3_2

                PIECE   square0_1
                SQUARE  ymxr_sq0,$DF,$FFFFFA0F,$070,ymxr_inst0
                ENDPIECE square0_1
                PIECE   square1_1
                SQUARE  ymxr_sq1,$EF,$FFFFFA11,$007,ymxr_inst1
                ENDPIECE square1_1
                PIECE   square2_1
                SQUARE  ymxr_sq2,$FE,$FFFFFA0F,$707,ymxr_inst2
                ENDPIECE square2_1
                PIECE   square3_1
                SQUARE  ymxr_sq3,$DF,$FFFFFA11,$077,ymxr_inst3
                ENDPIECE square3_1

                PIECE   one0_1
                ONEROW  ymxr_one0,$DF,$FFFFFA0F,$070,ymxr_inst0
                ENDPIECE one0_1
                PIECE   one1_1
                ONEROW  ymxr_one1,$EF,$FFFFFA11,$007,ymxr_inst1
                ENDPIECE one1_1
                PIECE   one2_1
                ONEROW  ymxr_one2,$FE,$FFFFFA0F,$707,ymxr_inst2
                ENDPIECE one2_1
                PIECE   one3_1
                ONEROW  ymxr_one3,$DF,$FFFFFA11,$077,ymxr_inst3
                ENDPIECE one3_1

                PIECE   counted0_1
                TICKC   ymxr_cnt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE counted0_1
                PIECE   counted1_1
                TICKC   ymxr_cnt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE counted1_1
                PIECE   counted2_1
                TICKC   ymxr_cnt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE counted2_1
                PIECE   counted3_1
                TICKC   ymxr_cnt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE counted3_1

; The handlers of a target that writes two registers, and of one that
; writes three: one an effect, as the three above are.
                PIECE   two0_1
                TICKW   ymxr_two0,2,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE two0_1
                PIECE   two1_1
                TICKW   ymxr_two1,2,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE two1_1
                PIECE   two2_1
                TICKW   ymxr_two2,2,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE two2_1
                PIECE   two3_1
                TICKW   ymxr_two3,2,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE two3_1

                PIECE   three0_1
                TICKW   ymxr_three0,3,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE three0_1
                PIECE   three1_1
                TICKW   ymxr_three1,3,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE three1_1
                PIECE   three2_1
                TICKW   ymxr_three2,3,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE three2_1
                PIECE   three3_1
                TICKW   ymxr_three3,3,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE three3_1

; The counted shape of two columns: `setEnvelope` writes R11 and R12 and
; both read every bit of their byte, so the rows of a source on it are
; whole bytes and a tick counts them (SPEC.md 2.1.3, 3.1.6).
                PIECE   envelope0_1
                TICKW   ymxr_env0,2,1,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE envelope0_1
                PIECE   envelope1_1
                TICKW   ymxr_env1,2,1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE envelope1_1
                PIECE   envelope2_1
                TICKW   ymxr_env2,2,1,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE envelope2_1
                PIECE   envelope3_1
                TICKW   ymxr_env3,2,1,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE envelope3_1
                PIECE   countedthree0_1
                TICKW   ymxr_cwide0,3,1,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE countedthree0_1
                PIECE   countedthree1_1
                TICKW   ymxr_cwide1,3,1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE countedthree1_1
                PIECE   countedthree2_1
                TICKW   ymxr_cwide2,3,1,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE countedthree2_1
                PIECE   countedthree3_1
                TICKW   ymxr_cwide3,3,1,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE countedthree3_1
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
                PIECE   lt0_1
                LPAD    KEY_LT,ymxr_ref0
                ENDPIECE lt0_1
                PIECE   lt0_2
                TICKWL  ymxr_lt0,2,0,2,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lt0_end:
ymxr_a_lt0_1 equ     ymxr_lt0_key+2
ymxr_r_lt0_1 equ     ymxr_ref
ymxr_n_lt0_1 equ     ymxr_ref0-ymxr_ref
ymxr_w_lt0_1 equ     ymxr_refat+0
                ENDPIECE lt0_2
                PIECE   lt1_1
                LPAD    KEY_LT,ymxr_ref1
                ENDPIECE lt1_1
                PIECE   lt1_2
                TICKWL  ymxr_lt1,2,0,2,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lt1_end:
ymxr_a_lt1_1 equ     ymxr_lt1_key+2
ymxr_r_lt1_1 equ     ymxr_ref
ymxr_n_lt1_1 equ     ymxr_ref1-ymxr_ref
ymxr_w_lt1_1 equ     ymxr_refat+2
                ENDPIECE lt1_2
                PIECE   lt2_1
                LPAD    KEY_LT,ymxr_ref2
                ENDPIECE lt2_1
                PIECE   lt2_2
                TICKWL  ymxr_lt2,2,0,2,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lt2_end:
ymxr_a_lt2_1 equ     ymxr_lt2_key+2
ymxr_r_lt2_1 equ     ymxr_ref
ymxr_n_lt2_1 equ     ymxr_ref2-ymxr_ref
ymxr_w_lt2_1 equ     ymxr_refat+4
                ENDPIECE lt2_2
                PIECE   lt3_1
                LPAD    KEY_LT,ymxr_ref3
                ENDPIECE lt3_1
                PIECE   lt3_2
                TICKWL  ymxr_lt3,2,0,2,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lt3_end:
ymxr_a_lt3_1 equ     ymxr_lt3_key+2
ymxr_r_lt3_1 equ     ymxr_ref
ymxr_n_lt3_1 equ     ymxr_ref3-ymxr_ref
ymxr_w_lt3_1 equ     ymxr_refat+6
                ENDPIECE lt3_2
                PIECE   le0_1
                LPAD    KEY_LE,ymxr_ref0
                ENDPIECE le0_1
                PIECE   le0_2
                TICKWL  ymxr_le0,2,1,2,ymxr_ref0,ymxr_clhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_le0_end:
ymxr_a_le0_1 equ     ymxr_le0_key+2
ymxr_r_le0_1 equ     ymxr_ref
ymxr_n_le0_1 equ     ymxr_ref0-ymxr_ref
ymxr_w_le0_1 equ     ymxr_refat+0
                ENDPIECE le0_2
                PIECE   le1_1
                LPAD    KEY_LE,ymxr_ref1
                ENDPIECE le1_1
                PIECE   le1_2
                TICKWL  ymxr_le1,2,1,2,ymxr_ref1,ymxr_clhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_le1_end:
ymxr_a_le1_1 equ     ymxr_le1_key+2
ymxr_r_le1_1 equ     ymxr_ref
ymxr_n_le1_1 equ     ymxr_ref1-ymxr_ref
ymxr_w_le1_1 equ     ymxr_refat+2
                ENDPIECE le1_2
                PIECE   le2_1
                LPAD    KEY_LE,ymxr_ref2
                ENDPIECE le2_1
                PIECE   le2_2
                TICKWL  ymxr_le2,2,1,2,ymxr_ref2,ymxr_clhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_le2_end:
ymxr_a_le2_1 equ     ymxr_le2_key+2
ymxr_r_le2_1 equ     ymxr_ref
ymxr_n_le2_1 equ     ymxr_ref2-ymxr_ref
ymxr_w_le2_1 equ     ymxr_refat+4
                ENDPIECE le2_2
                PIECE   le3_1
                LPAD    KEY_LE,ymxr_ref3
                ENDPIECE le3_1
                PIECE   le3_2
                TICKWL  ymxr_le3,2,1,2,ymxr_ref3,ymxr_clhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_le3_end:
ymxr_a_le3_1 equ     ymxr_le3_key+2
ymxr_r_le3_1 equ     ymxr_ref
ymxr_n_le3_1 equ     ymxr_ref3-ymxr_ref
ymxr_w_le3_1 equ     ymxr_refat+6
                ENDPIECE le3_2
                PIECE   lf0_1
                LPAD    KEY_LF,ymxr_ref0
                ENDPIECE lf0_1
                PIECE   lf0_2
                TICKWL  ymxr_lf0,2,1,1,ymxr_ref0,ymxr_clhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lf0_end:
ymxr_a_lf0_1 equ     ymxr_lf0_key+2
ymxr_r_lf0_1 equ     ymxr_ref
ymxr_n_lf0_1 equ     ymxr_ref0-ymxr_ref
ymxr_w_lf0_1 equ     ymxr_refat+0
                ENDPIECE lf0_2
                PIECE   lf1_1
                LPAD    KEY_LF,ymxr_ref1
                ENDPIECE lf1_1
                PIECE   lf1_2
                TICKWL  ymxr_lf1,2,1,1,ymxr_ref1,ymxr_clhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lf1_end:
ymxr_a_lf1_1 equ     ymxr_lf1_key+2
ymxr_r_lf1_1 equ     ymxr_ref
ymxr_n_lf1_1 equ     ymxr_ref1-ymxr_ref
ymxr_w_lf1_1 equ     ymxr_refat+2
                ENDPIECE lf1_2
                PIECE   lf2_1
                LPAD    KEY_LF,ymxr_ref2
                ENDPIECE lf2_1
                PIECE   lf2_2
                TICKWL  ymxr_lf2,2,1,1,ymxr_ref2,ymxr_clhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lf2_end:
ymxr_a_lf2_1 equ     ymxr_lf2_key+2
ymxr_r_lf2_1 equ     ymxr_ref
ymxr_n_lf2_1 equ     ymxr_ref2-ymxr_ref
ymxr_w_lf2_1 equ     ymxr_refat+4
                ENDPIECE lf2_2
                PIECE   lf3_1
                LPAD    KEY_LF,ymxr_ref3
                ENDPIECE lf3_1
                PIECE   lf3_2
                TICKWL  ymxr_lf3,2,1,1,ymxr_ref3,ymxr_clhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lf3_end:
ymxr_a_lf3_1 equ     ymxr_lf3_key+2
ymxr_r_lf3_1 equ     ymxr_ref
ymxr_n_lf3_1 equ     ymxr_ref3-ymxr_ref
ymxr_w_lf3_1 equ     ymxr_refat+6
                ENDPIECE lf3_2
                PIECE   lk0_1
                LPAD    KEY_LK,ymxr_ref0
                ENDPIECE lk0_1
                PIECE   lk0_2
                TICKL   ymxr_lk0,0,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lk0_end:
ymxr_a_lk0_1 equ     ymxr_lk0_key+2
ymxr_r_lk0_1 equ     ymxr_ref
ymxr_n_lk0_1 equ     ymxr_ref0-ymxr_ref
ymxr_w_lk0_1 equ     ymxr_refat+0
                ENDPIECE lk0_2
                PIECE   lk1_1
                LPAD    KEY_LK,ymxr_ref1
                ENDPIECE lk1_1
                PIECE   lk1_2
                TICKL   ymxr_lk1,0,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lk1_end:
ymxr_a_lk1_1 equ     ymxr_lk1_key+2
ymxr_r_lk1_1 equ     ymxr_ref
ymxr_n_lk1_1 equ     ymxr_ref1-ymxr_ref
ymxr_w_lk1_1 equ     ymxr_refat+2
                ENDPIECE lk1_2
                PIECE   lk2_1
                LPAD    KEY_LK,ymxr_ref2
                ENDPIECE lk2_1
                PIECE   lk2_2
                TICKL   ymxr_lk2,0,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lk2_end:
ymxr_a_lk2_1 equ     ymxr_lk2_key+2
ymxr_r_lk2_1 equ     ymxr_ref
ymxr_n_lk2_1 equ     ymxr_ref2-ymxr_ref
ymxr_w_lk2_1 equ     ymxr_refat+4
                ENDPIECE lk2_2
                PIECE   lk3_1
                LPAD    KEY_LK,ymxr_ref3
                ENDPIECE lk3_1
                PIECE   lk3_2
                TICKL   ymxr_lk3,0,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lk3_end:
ymxr_a_lk3_1 equ     ymxr_lk3_key+2
ymxr_r_lk3_1 equ     ymxr_ref
ymxr_n_lk3_1 equ     ymxr_ref3-ymxr_ref
ymxr_w_lk3_1 equ     ymxr_refat+6
                ENDPIECE lk3_2
                PIECE   lb0_1
                LPAD    KEY_LB,ymxr_ref0
                ENDPIECE lb0_1
                PIECE   lb0_2
                TICKWL  ymxr_lb0,3,0,3,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lb0_end:
ymxr_a_lb0_1 equ     ymxr_lb0_key+2
ymxr_r_lb0_1 equ     ymxr_ref
ymxr_n_lb0_1 equ     ymxr_ref0-ymxr_ref
ymxr_w_lb0_1 equ     ymxr_refat+0
                ENDPIECE lb0_2
                PIECE   lb1_1
                LPAD    KEY_LB,ymxr_ref1
                ENDPIECE lb1_1
                PIECE   lb1_2
                TICKWL  ymxr_lb1,3,0,3,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lb1_end:
ymxr_a_lb1_1 equ     ymxr_lb1_key+2
ymxr_r_lb1_1 equ     ymxr_ref
ymxr_n_lb1_1 equ     ymxr_ref1-ymxr_ref
ymxr_w_lb1_1 equ     ymxr_refat+2
                ENDPIECE lb1_2
                PIECE   lb2_1
                LPAD    KEY_LB,ymxr_ref2
                ENDPIECE lb2_1
                PIECE   lb2_2
                TICKWL  ymxr_lb2,3,0,3,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lb2_end:
ymxr_a_lb2_1 equ     ymxr_lb2_key+2
ymxr_r_lb2_1 equ     ymxr_ref
ymxr_n_lb2_1 equ     ymxr_ref2-ymxr_ref
ymxr_w_lb2_1 equ     ymxr_refat+4
                ENDPIECE lb2_2
                PIECE   lb3_1
                LPAD    KEY_LB,ymxr_ref3
                ENDPIECE lb3_1
                PIECE   lb3_2
                TICKWL  ymxr_lb3,3,0,3,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lb3_end:
ymxr_a_lb3_1 equ     ymxr_lb3_key+2
ymxr_r_lb3_1 equ     ymxr_ref
ymxr_n_lb3_1 equ     ymxr_ref3-ymxr_ref
ymxr_w_lb3_1 equ     ymxr_refat+6
                ENDPIECE lb3_2
                PIECE   ln0_1
                LPAD    KEY_LN,ymxr_ref0
                ENDPIECE ln0_1
                PIECE   ln0_2
                TICKWL  ymxr_ln0,2,0,1,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_ln0_end:
ymxr_a_ln0_1 equ     ymxr_ln0_key+2
ymxr_r_ln0_1 equ     ymxr_ref
ymxr_n_ln0_1 equ     ymxr_ref0-ymxr_ref
ymxr_w_ln0_1 equ     ymxr_refat+0
                ENDPIECE ln0_2
                PIECE   ln1_1
                LPAD    KEY_LN,ymxr_ref1
                ENDPIECE ln1_1
                PIECE   ln1_2
                TICKWL  ymxr_ln1,2,0,1,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_ln1_end:
ymxr_a_ln1_1 equ     ymxr_ln1_key+2
ymxr_r_ln1_1 equ     ymxr_ref
ymxr_n_ln1_1 equ     ymxr_ref1-ymxr_ref
ymxr_w_ln1_1 equ     ymxr_refat+2
                ENDPIECE ln1_2
                PIECE   ln2_1
                LPAD    KEY_LN,ymxr_ref2
                ENDPIECE ln2_1
                PIECE   ln2_2
                TICKWL  ymxr_ln2,2,0,1,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_ln2_end:
ymxr_a_ln2_1 equ     ymxr_ln2_key+2
ymxr_r_ln2_1 equ     ymxr_ref
ymxr_n_ln2_1 equ     ymxr_ref2-ymxr_ref
ymxr_w_ln2_1 equ     ymxr_refat+4
                ENDPIECE ln2_2
                PIECE   ln3_1
                LPAD    KEY_LN,ymxr_ref3
                ENDPIECE ln3_1
                PIECE   ln3_2
                TICKWL  ymxr_ln3,2,0,1,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_ln3_end:
ymxr_a_ln3_1 equ     ymxr_ln3_key+2
ymxr_r_ln3_1 equ     ymxr_ref
ymxr_n_ln3_1 equ     ymxr_ref3-ymxr_ref
ymxr_w_ln3_1 equ     ymxr_refat+6
                ENDPIECE ln3_2
                PIECE   lc0_1
                LPAD    KEY_LC,ymxr_ref0
                ENDPIECE lc0_1
                PIECE   lc0_2
                TICKL   ymxr_lc0,1,ymxr_ref0,ymxr_clhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lc0_end:
ymxr_a_lc0_1 equ     ymxr_lc0_key+2
ymxr_r_lc0_1 equ     ymxr_ref
ymxr_n_lc0_1 equ     ymxr_ref0-ymxr_ref
ymxr_w_lc0_1 equ     ymxr_refat+0
                ENDPIECE lc0_2
                PIECE   lc1_1
                LPAD    KEY_LC,ymxr_ref1
                ENDPIECE lc1_1
                PIECE   lc1_2
                TICKL   ymxr_lc1,1,ymxr_ref1,ymxr_clhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lc1_end:
ymxr_a_lc1_1 equ     ymxr_lc1_key+2
ymxr_r_lc1_1 equ     ymxr_ref
ymxr_n_lc1_1 equ     ymxr_ref1-ymxr_ref
ymxr_w_lc1_1 equ     ymxr_refat+2
                ENDPIECE lc1_2
                PIECE   lc2_1
                LPAD    KEY_LC,ymxr_ref2
                ENDPIECE lc2_1
                PIECE   lc2_2
                TICKL   ymxr_lc2,1,ymxr_ref2,ymxr_clhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lc2_end:
ymxr_a_lc2_1 equ     ymxr_lc2_key+2
ymxr_r_lc2_1 equ     ymxr_ref
ymxr_n_lc2_1 equ     ymxr_ref2-ymxr_ref
ymxr_w_lc2_1 equ     ymxr_refat+4
                ENDPIECE lc2_2
                PIECE   lc3_1
                LPAD    KEY_LC,ymxr_ref3
                ENDPIECE lc3_1
                PIECE   lc3_2
                TICKL   ymxr_lc3,1,ymxr_ref3,ymxr_clhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lc3_end:
ymxr_a_lc3_1 equ     ymxr_lc3_key+2
ymxr_r_lc3_1 equ     ymxr_ref
ymxr_n_lc3_1 equ     ymxr_ref3-ymxr_ref
ymxr_w_lc3_1 equ     ymxr_refat+6
                ENDPIECE lc3_2
                PIECE   lv0_1
                LPAD    KEY_LV,ymxr_ref0
                ENDPIECE lv0_1
                PIECE   lv0_2
                TICKWL  ymxr_lv0,3,0,2,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lv0_end:
ymxr_a_lv0_1 equ     ymxr_lv0_key+2
ymxr_r_lv0_1 equ     ymxr_ref
ymxr_n_lv0_1 equ     ymxr_ref0-ymxr_ref
ymxr_w_lv0_1 equ     ymxr_refat+0
                ENDPIECE lv0_2
                PIECE   lv1_1
                LPAD    KEY_LV,ymxr_ref1
                ENDPIECE lv1_1
                PIECE   lv1_2
                TICKWL  ymxr_lv1,3,0,2,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lv1_end:
ymxr_a_lv1_1 equ     ymxr_lv1_key+2
ymxr_r_lv1_1 equ     ymxr_ref
ymxr_n_lv1_1 equ     ymxr_ref1-ymxr_ref
ymxr_w_lv1_1 equ     ymxr_refat+2
                ENDPIECE lv1_2
                PIECE   lv2_1
                LPAD    KEY_LV,ymxr_ref2
                ENDPIECE lv2_1
                PIECE   lv2_2
                TICKWL  ymxr_lv2,3,0,2,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lv2_end:
ymxr_a_lv2_1 equ     ymxr_lv2_key+2
ymxr_r_lv2_1 equ     ymxr_ref
ymxr_n_lv2_1 equ     ymxr_ref2-ymxr_ref
ymxr_w_lv2_1 equ     ymxr_refat+4
                ENDPIECE lv2_2
                PIECE   lv3_1
                LPAD    KEY_LV,ymxr_ref3
                ENDPIECE lv3_1
                PIECE   lv3_2
                TICKWL  ymxr_lv3,3,0,2,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lv3_end:
ymxr_a_lv3_1 equ     ymxr_lv3_key+2
ymxr_r_lv3_1 equ     ymxr_ref
ymxr_n_lv3_1 equ     ymxr_ref3-ymxr_ref
ymxr_w_lv3_1 equ     ymxr_refat+6
                ENDPIECE lv3_2
; This order reduces the padding between the counted instances.
                PIECE   lh3_1
                LPAD    KEY_LH,ymxr_ref3
                ENDPIECE lh3_1
                PIECE   lh3_2
                TICKWL  ymxr_lh3,3,1,2,ymxr_ref3,ymxr_clhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lh3_end:
ymxr_a_lh3_1 equ     ymxr_lh3_key+2
ymxr_r_lh3_1 equ     ymxr_ref
ymxr_n_lh3_1 equ     ymxr_ref3-ymxr_ref
ymxr_w_lh3_1 equ     ymxr_refat+6
                ENDPIECE lh3_2
                PIECE   lg3_1
                LPAD    KEY_LG,ymxr_ref3
                ENDPIECE lg3_1
                PIECE   lg3_2
                TICKWL  ymxr_lg3,3,1,3,ymxr_ref3,ymxr_clhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lg3_end:
ymxr_a_lg3_1 equ     ymxr_lg3_key+2
ymxr_r_lg3_1 equ     ymxr_ref
ymxr_n_lg3_1 equ     ymxr_ref3-ymxr_ref
ymxr_w_lg3_1 equ     ymxr_refat+6
                ENDPIECE lg3_2
                PIECE   lh0_1
                LPAD    KEY_LH,ymxr_ref0
                ENDPIECE lh0_1
                PIECE   lh0_2
                TICKWL  ymxr_lh0,3,1,2,ymxr_ref0,ymxr_clhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lh0_end:
ymxr_a_lh0_1 equ     ymxr_lh0_key+2
ymxr_r_lh0_1 equ     ymxr_ref
ymxr_n_lh0_1 equ     ymxr_ref0-ymxr_ref
ymxr_w_lh0_1 equ     ymxr_refat+0
                ENDPIECE lh0_2
                PIECE   lg0_1
                LPAD    KEY_LG,ymxr_ref0
                ENDPIECE lg0_1
                PIECE   lg0_2
                TICKWL  ymxr_lg0,3,1,3,ymxr_ref0,ymxr_clhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lg0_end:
ymxr_a_lg0_1 equ     ymxr_lg0_key+2
ymxr_r_lg0_1 equ     ymxr_ref
ymxr_n_lg0_1 equ     ymxr_ref0-ymxr_ref
ymxr_w_lg0_1 equ     ymxr_refat+0
                ENDPIECE lg0_2
                PIECE   lh2_1
                LPAD    KEY_LH,ymxr_ref2
                ENDPIECE lh2_1
                PIECE   lh2_2
                TICKWL  ymxr_lh2,3,1,2,ymxr_ref2,ymxr_clhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lh2_end:
ymxr_a_lh2_1 equ     ymxr_lh2_key+2
ymxr_r_lh2_1 equ     ymxr_ref
ymxr_n_lh2_1 equ     ymxr_ref2-ymxr_ref
ymxr_w_lh2_1 equ     ymxr_refat+4
                ENDPIECE lh2_2
                PIECE   lg2_1
                LPAD    KEY_LG,ymxr_ref2
                ENDPIECE lg2_1
                PIECE   lg2_2
                TICKWL  ymxr_lg2,3,1,3,ymxr_ref2,ymxr_clhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lg2_end:
ymxr_a_lg2_1 equ     ymxr_lg2_key+2
ymxr_r_lg2_1 equ     ymxr_ref
ymxr_n_lg2_1 equ     ymxr_ref2-ymxr_ref
ymxr_w_lg2_1 equ     ymxr_refat+4
                ENDPIECE lg2_2
                PIECE   lh1_1
                LPAD    KEY_LH,ymxr_ref1
                ENDPIECE lh1_1
                PIECE   lh1_2
                TICKWL  ymxr_lh1,3,1,2,ymxr_ref1,ymxr_clhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lh1_end:
ymxr_a_lh1_1 equ     ymxr_lh1_key+2
ymxr_r_lh1_1 equ     ymxr_ref
ymxr_n_lh1_1 equ     ymxr_ref1-ymxr_ref
ymxr_w_lh1_1 equ     ymxr_refat+2
                ENDPIECE lh1_2
                PIECE   lg1_1
                LPAD    KEY_LG,ymxr_ref1
                ENDPIECE lg1_1
                PIECE   lg1_2
                TICKWL  ymxr_lg1,3,1,3,ymxr_ref1,ymxr_clhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lg1_end:
ymxr_a_lg1_1 equ     ymxr_lg1_key+2
ymxr_r_lg1_1 equ     ymxr_ref
ymxr_n_lg1_1 equ     ymxr_ref1-ymxr_ref
ymxr_w_lg1_1 equ     ymxr_refat+2
                ENDPIECE lg1_2
; Monitor halt for lookup kinds: one per effect, with lookup-cycle
; counts.
                .if     YMXR_PERF
                LHALT   ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,0
                LHALT   ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,0
                LHALT   ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,0
                LHALT   ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,0
                .endif

; Counted lookup instances restore the last row before this halt.
                LHALT   ymxr_clhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,30
                LHALT   ymxr_clhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,30
                LHALT   ymxr_clhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,30
                LHALT   ymxr_clhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,30
                .if     YMXR_PERF
ymxr_perf_acc:  dc.w    0                       ; the ticks' counted cost
                .endif                          ; since the last call
                .if     YMXR_DEFER
                INSTALLERS
                PIECE   align_1
                ds.b    (ymxr_tick0-*)&255
                ENDPIECE align_1
ymxr_a_align_1 equ    ymxr_e_align_1
ymxr_r_align_1 equ    ymxr_tick0
ymxr_n_align_1 equ    0
                .endif
ymxr_bank0end:
                .endif

                .if     YMXR_DEFER
; Bank 1: every handler again, in the order of bank 0, BANK bytes on. A
; deferred start on a running timer is armed in the bank its vector does
; not point into, so the handler its first tick reads is patched whole
; while the running one plays on (SPEC.md 4.8).
ymxr_bank1:
                PIECE2  effect0_3,effect0_2
                TICK    ymxr_tick0b,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE effect0_3
                PIECE2  effect1_3,effect1_2
                TICK    ymxr_tick1b,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE effect1_3
                PIECE2  effect2_3,effect2_2
                TICK    ymxr_tick2b,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE effect2_3
                PIECE2  effect3_3,effect3_2
                TICK    ymxr_tick3b,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE effect3_3
                PIECE2  square0_2,square0_1
                SQUARE  ymxr_sq0b,$DF,$FFFFFA0F,$070,ymxr_inst0
                ENDPIECE square0_2
                PIECE2  square1_2,square1_1
                SQUARE  ymxr_sq1b,$EF,$FFFFFA11,$007,ymxr_inst1
                ENDPIECE square1_2
                PIECE2  square2_2,square2_1
                SQUARE  ymxr_sq2b,$FE,$FFFFFA0F,$707,ymxr_inst2
                ENDPIECE square2_2
                PIECE2  square3_2,square3_1
                SQUARE  ymxr_sq3b,$DF,$FFFFFA11,$077,ymxr_inst3
                ENDPIECE square3_2
                PIECE2  one0_2,one0_1
                ONEROW  ymxr_one0b,$DF,$FFFFFA0F,$070,ymxr_inst0
                ENDPIECE one0_2
                PIECE2  one1_2,one1_1
                ONEROW  ymxr_one1b,$EF,$FFFFFA11,$007,ymxr_inst1
                ENDPIECE one1_2
                PIECE2  one2_2,one2_1
                ONEROW  ymxr_one2b,$FE,$FFFFFA0F,$707,ymxr_inst2
                ENDPIECE one2_2
                PIECE2  one3_2,one3_1
                ONEROW  ymxr_one3b,$DF,$FFFFFA11,$077,ymxr_inst3
                ENDPIECE one3_2
                PIECE2  counted0_2,counted0_1
                TICKC   ymxr_cnt0b,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE counted0_2
                PIECE2  counted1_2,counted1_1
                TICKC   ymxr_cnt1b,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE counted1_2
                PIECE2  counted2_2,counted2_1
                TICKC   ymxr_cnt2b,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE counted2_2
                PIECE2  counted3_2,counted3_1
                TICKC   ymxr_cnt3b,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE counted3_2
                PIECE2  two0_2,two0_1
                TICKW   ymxr_two0b,2,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE two0_2
                PIECE2  two1_2,two1_1
                TICKW   ymxr_two1b,2,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE two1_2
                PIECE2  two2_2,two2_1
                TICKW   ymxr_two2b,2,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE two2_2
                PIECE2  two3_2,two3_1
                TICKW   ymxr_two3b,2,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE two3_2
                PIECE2  three0_2,three0_1
                TICKW   ymxr_three0b,3,0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE three0_2
                PIECE2  three1_2,three1_1
                TICKW   ymxr_three1b,3,0,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE three1_2
                PIECE2  three2_2,three2_1
                TICKW   ymxr_three2b,3,0,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE three2_2
                PIECE2  three3_2,three3_1
                TICKW   ymxr_three3b,3,0,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE three3_2
                PIECE2  envelope0_2,envelope0_1
                TICKW   ymxr_env0b,2,1,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE envelope0_2
                PIECE2  envelope1_2,envelope1_1
                TICKW   ymxr_env1b,2,1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE envelope1_2
                PIECE2  envelope2_2,envelope2_1
                TICKW   ymxr_env2b,2,1,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE envelope2_2
                PIECE2  envelope3_2,envelope3_1
                TICKW   ymxr_env3b,2,1,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE envelope3_2
                PIECE2  countedthree0_2,countedthree0_1
                TICKW   ymxr_cwide0b,3,1,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
                ENDPIECE countedthree0_2
                PIECE2  countedthree1_2,countedthree1_1
                TICKW   ymxr_cwide1b,3,1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
                ENDPIECE countedthree1_2
                PIECE2  countedthree2_2,countedthree2_1
                TICKW   ymxr_cwide2b,3,1,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
                ENDPIECE countedthree2_2
                PIECE2  countedthree3_2,countedthree3_1
                TICKW   ymxr_cwide3b,3,1,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
                ENDPIECE countedthree3_2
                .if     YMXR_MAP
                PIECE2  lt0_3,lt0_1
                LPAD    KEY_LT,ymxr_ref0
                ENDPIECE lt0_3
                PIECE2  lt0_4,lt0_2
                TICKWL  ymxr_lt0b,2,0,2,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lt0b_end:
ymxr_a_lt0_3 equ     ymxr_lt0b_key+2
ymxr_r_lt0_3 equ     ymxr_ref
ymxr_n_lt0_3 equ     ymxr_ref0-ymxr_ref
ymxr_w_lt0_3 equ     ymxr_refat+0
                ENDPIECE lt0_4
                PIECE2  lt1_3,lt1_1
                LPAD    KEY_LT,ymxr_ref1
                ENDPIECE lt1_3
                PIECE2  lt1_4,lt1_2
                TICKWL  ymxr_lt1b,2,0,2,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lt1b_end:
ymxr_a_lt1_3 equ     ymxr_lt1b_key+2
ymxr_r_lt1_3 equ     ymxr_ref
ymxr_n_lt1_3 equ     ymxr_ref1-ymxr_ref
ymxr_w_lt1_3 equ     ymxr_refat+2
                ENDPIECE lt1_4
                PIECE2  lt2_3,lt2_1
                LPAD    KEY_LT,ymxr_ref2
                ENDPIECE lt2_3
                PIECE2  lt2_4,lt2_2
                TICKWL  ymxr_lt2b,2,0,2,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lt2b_end:
ymxr_a_lt2_3 equ     ymxr_lt2b_key+2
ymxr_r_lt2_3 equ     ymxr_ref
ymxr_n_lt2_3 equ     ymxr_ref2-ymxr_ref
ymxr_w_lt2_3 equ     ymxr_refat+4
                ENDPIECE lt2_4
                PIECE2  lt3_3,lt3_1
                LPAD    KEY_LT,ymxr_ref3
                ENDPIECE lt3_3
                PIECE2  lt3_4,lt3_2
                TICKWL  ymxr_lt3b,2,0,2,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lt3b_end:
ymxr_a_lt3_3 equ     ymxr_lt3b_key+2
ymxr_r_lt3_3 equ     ymxr_ref
ymxr_n_lt3_3 equ     ymxr_ref3-ymxr_ref
ymxr_w_lt3_3 equ     ymxr_refat+6
                ENDPIECE lt3_4
                PIECE2  le0_3,le0_1
                LPAD    KEY_LE,ymxr_ref0
                ENDPIECE le0_3
                PIECE2  le0_4,le0_2
                TICKWL  ymxr_le0b,2,1,2,ymxr_ref0,ymxr_clhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_le0b_end:
ymxr_a_le0_3 equ     ymxr_le0b_key+2
ymxr_r_le0_3 equ     ymxr_ref
ymxr_n_le0_3 equ     ymxr_ref0-ymxr_ref
ymxr_w_le0_3 equ     ymxr_refat+0
                ENDPIECE le0_4
                PIECE2  le1_3,le1_1
                LPAD    KEY_LE,ymxr_ref1
                ENDPIECE le1_3
                PIECE2  le1_4,le1_2
                TICKWL  ymxr_le1b,2,1,2,ymxr_ref1,ymxr_clhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_le1b_end:
ymxr_a_le1_3 equ     ymxr_le1b_key+2
ymxr_r_le1_3 equ     ymxr_ref
ymxr_n_le1_3 equ     ymxr_ref1-ymxr_ref
ymxr_w_le1_3 equ     ymxr_refat+2
                ENDPIECE le1_4
                PIECE2  le2_3,le2_1
                LPAD    KEY_LE,ymxr_ref2
                ENDPIECE le2_3
                PIECE2  le2_4,le2_2
                TICKWL  ymxr_le2b,2,1,2,ymxr_ref2,ymxr_clhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_le2b_end:
ymxr_a_le2_3 equ     ymxr_le2b_key+2
ymxr_r_le2_3 equ     ymxr_ref
ymxr_n_le2_3 equ     ymxr_ref2-ymxr_ref
ymxr_w_le2_3 equ     ymxr_refat+4
                ENDPIECE le2_4
                PIECE2  le3_3,le3_1
                LPAD    KEY_LE,ymxr_ref3
                ENDPIECE le3_3
                PIECE2  le3_4,le3_2
                TICKWL  ymxr_le3b,2,1,2,ymxr_ref3,ymxr_clhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_le3b_end:
ymxr_a_le3_3 equ     ymxr_le3b_key+2
ymxr_r_le3_3 equ     ymxr_ref
ymxr_n_le3_3 equ     ymxr_ref3-ymxr_ref
ymxr_w_le3_3 equ     ymxr_refat+6
                ENDPIECE le3_4
                PIECE2  lf0_3,lf0_1
                LPAD    KEY_LF,ymxr_ref0
                ENDPIECE lf0_3
                PIECE2  lf0_4,lf0_2
                TICKWL  ymxr_lf0b,2,1,1,ymxr_ref0,ymxr_clhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lf0b_end:
ymxr_a_lf0_3 equ     ymxr_lf0b_key+2
ymxr_r_lf0_3 equ     ymxr_ref
ymxr_n_lf0_3 equ     ymxr_ref0-ymxr_ref
ymxr_w_lf0_3 equ     ymxr_refat+0
                ENDPIECE lf0_4
                PIECE2  lf1_3,lf1_1
                LPAD    KEY_LF,ymxr_ref1
                ENDPIECE lf1_3
                PIECE2  lf1_4,lf1_2
                TICKWL  ymxr_lf1b,2,1,1,ymxr_ref1,ymxr_clhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lf1b_end:
ymxr_a_lf1_3 equ     ymxr_lf1b_key+2
ymxr_r_lf1_3 equ     ymxr_ref
ymxr_n_lf1_3 equ     ymxr_ref1-ymxr_ref
ymxr_w_lf1_3 equ     ymxr_refat+2
                ENDPIECE lf1_4
                PIECE2  lf2_3,lf2_1
                LPAD    KEY_LF,ymxr_ref2
                ENDPIECE lf2_3
                PIECE2  lf2_4,lf2_2
                TICKWL  ymxr_lf2b,2,1,1,ymxr_ref2,ymxr_clhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lf2b_end:
ymxr_a_lf2_3 equ     ymxr_lf2b_key+2
ymxr_r_lf2_3 equ     ymxr_ref
ymxr_n_lf2_3 equ     ymxr_ref2-ymxr_ref
ymxr_w_lf2_3 equ     ymxr_refat+4
                ENDPIECE lf2_4
                PIECE2  lf3_3,lf3_1
                LPAD    KEY_LF,ymxr_ref3
                ENDPIECE lf3_3
                PIECE2  lf3_4,lf3_2
                TICKWL  ymxr_lf3b,2,1,1,ymxr_ref3,ymxr_clhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lf3b_end:
ymxr_a_lf3_3 equ     ymxr_lf3b_key+2
ymxr_r_lf3_3 equ     ymxr_ref
ymxr_n_lf3_3 equ     ymxr_ref3-ymxr_ref
ymxr_w_lf3_3 equ     ymxr_refat+6
                ENDPIECE lf3_4
                PIECE2  lk0_3,lk0_1
                LPAD    KEY_LK,ymxr_ref0
                ENDPIECE lk0_3
                PIECE2  lk0_4,lk0_2
                TICKL   ymxr_lk0b,0,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lk0b_end:
ymxr_a_lk0_3 equ     ymxr_lk0b_key+2
ymxr_r_lk0_3 equ     ymxr_ref
ymxr_n_lk0_3 equ     ymxr_ref0-ymxr_ref
ymxr_w_lk0_3 equ     ymxr_refat+0
                ENDPIECE lk0_4
                PIECE2  lk1_3,lk1_1
                LPAD    KEY_LK,ymxr_ref1
                ENDPIECE lk1_3
                PIECE2  lk1_4,lk1_2
                TICKL   ymxr_lk1b,0,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lk1b_end:
ymxr_a_lk1_3 equ     ymxr_lk1b_key+2
ymxr_r_lk1_3 equ     ymxr_ref
ymxr_n_lk1_3 equ     ymxr_ref1-ymxr_ref
ymxr_w_lk1_3 equ     ymxr_refat+2
                ENDPIECE lk1_4
                PIECE2  lk2_3,lk2_1
                LPAD    KEY_LK,ymxr_ref2
                ENDPIECE lk2_3
                PIECE2  lk2_4,lk2_2
                TICKL   ymxr_lk2b,0,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lk2b_end:
ymxr_a_lk2_3 equ     ymxr_lk2b_key+2
ymxr_r_lk2_3 equ     ymxr_ref
ymxr_n_lk2_3 equ     ymxr_ref2-ymxr_ref
ymxr_w_lk2_3 equ     ymxr_refat+4
                ENDPIECE lk2_4
                PIECE2  lk3_3,lk3_1
                LPAD    KEY_LK,ymxr_ref3
                ENDPIECE lk3_3
                PIECE2  lk3_4,lk3_2
                TICKL   ymxr_lk3b,0,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lk3b_end:
ymxr_a_lk3_3 equ     ymxr_lk3b_key+2
ymxr_r_lk3_3 equ     ymxr_ref
ymxr_n_lk3_3 equ     ymxr_ref3-ymxr_ref
ymxr_w_lk3_3 equ     ymxr_refat+6
                ENDPIECE lk3_4
                PIECE2  lb0_3,lb0_1
                LPAD    KEY_LB,ymxr_ref0
                ENDPIECE lb0_3
                PIECE2  lb0_4,lb0_2
                TICKWL  ymxr_lb0b,3,0,3,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lb0b_end:
ymxr_a_lb0_3 equ     ymxr_lb0b_key+2
ymxr_r_lb0_3 equ     ymxr_ref
ymxr_n_lb0_3 equ     ymxr_ref0-ymxr_ref
ymxr_w_lb0_3 equ     ymxr_refat+0
                ENDPIECE lb0_4
                PIECE2  lb1_3,lb1_1
                LPAD    KEY_LB,ymxr_ref1
                ENDPIECE lb1_3
                PIECE2  lb1_4,lb1_2
                TICKWL  ymxr_lb1b,3,0,3,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lb1b_end:
ymxr_a_lb1_3 equ     ymxr_lb1b_key+2
ymxr_r_lb1_3 equ     ymxr_ref
ymxr_n_lb1_3 equ     ymxr_ref1-ymxr_ref
ymxr_w_lb1_3 equ     ymxr_refat+2
                ENDPIECE lb1_4
                PIECE2  lb2_3,lb2_1
                LPAD    KEY_LB,ymxr_ref2
                ENDPIECE lb2_3
                PIECE2  lb2_4,lb2_2
                TICKWL  ymxr_lb2b,3,0,3,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lb2b_end:
ymxr_a_lb2_3 equ     ymxr_lb2b_key+2
ymxr_r_lb2_3 equ     ymxr_ref
ymxr_n_lb2_3 equ     ymxr_ref2-ymxr_ref
ymxr_w_lb2_3 equ     ymxr_refat+4
                ENDPIECE lb2_4
                PIECE2  lb3_3,lb3_1
                LPAD    KEY_LB,ymxr_ref3
                ENDPIECE lb3_3
                PIECE2  lb3_4,lb3_2
                TICKWL  ymxr_lb3b,3,0,3,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lb3b_end:
ymxr_a_lb3_3 equ     ymxr_lb3b_key+2
ymxr_r_lb3_3 equ     ymxr_ref
ymxr_n_lb3_3 equ     ymxr_ref3-ymxr_ref
ymxr_w_lb3_3 equ     ymxr_refat+6
                ENDPIECE lb3_4
                PIECE2  ln0_3,ln0_1
                LPAD    KEY_LN,ymxr_ref0
                ENDPIECE ln0_3
                PIECE2  ln0_4,ln0_2
                TICKWL  ymxr_ln0b,2,0,1,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_ln0b_end:
ymxr_a_ln0_3 equ     ymxr_ln0b_key+2
ymxr_r_ln0_3 equ     ymxr_ref
ymxr_n_ln0_3 equ     ymxr_ref0-ymxr_ref
ymxr_w_ln0_3 equ     ymxr_refat+0
                ENDPIECE ln0_4
                PIECE2  ln1_3,ln1_1
                LPAD    KEY_LN,ymxr_ref1
                ENDPIECE ln1_3
                PIECE2  ln1_4,ln1_2
                TICKWL  ymxr_ln1b,2,0,1,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_ln1b_end:
ymxr_a_ln1_3 equ     ymxr_ln1b_key+2
ymxr_r_ln1_3 equ     ymxr_ref
ymxr_n_ln1_3 equ     ymxr_ref1-ymxr_ref
ymxr_w_ln1_3 equ     ymxr_refat+2
                ENDPIECE ln1_4
                PIECE2  ln2_3,ln2_1
                LPAD    KEY_LN,ymxr_ref2
                ENDPIECE ln2_3
                PIECE2  ln2_4,ln2_2
                TICKWL  ymxr_ln2b,2,0,1,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_ln2b_end:
ymxr_a_ln2_3 equ     ymxr_ln2b_key+2
ymxr_r_ln2_3 equ     ymxr_ref
ymxr_n_ln2_3 equ     ymxr_ref2-ymxr_ref
ymxr_w_ln2_3 equ     ymxr_refat+4
                ENDPIECE ln2_4
                PIECE2  ln3_3,ln3_1
                LPAD    KEY_LN,ymxr_ref3
                ENDPIECE ln3_3
                PIECE2  ln3_4,ln3_2
                TICKWL  ymxr_ln3b,2,0,1,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_ln3b_end:
ymxr_a_ln3_3 equ     ymxr_ln3b_key+2
ymxr_r_ln3_3 equ     ymxr_ref
ymxr_n_ln3_3 equ     ymxr_ref3-ymxr_ref
ymxr_w_ln3_3 equ     ymxr_refat+6
                ENDPIECE ln3_4
                PIECE2  lc0_3,lc0_1
                LPAD    KEY_LC,ymxr_ref0
                ENDPIECE lc0_3
                PIECE2  lc0_4,lc0_2
                TICKL   ymxr_lc0b,1,ymxr_ref0,ymxr_clhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lc0b_end:
ymxr_a_lc0_3 equ     ymxr_lc0b_key+2
ymxr_r_lc0_3 equ     ymxr_ref
ymxr_n_lc0_3 equ     ymxr_ref0-ymxr_ref
ymxr_w_lc0_3 equ     ymxr_refat+0
                ENDPIECE lc0_4
                PIECE2  lc1_3,lc1_1
                LPAD    KEY_LC,ymxr_ref1
                ENDPIECE lc1_3
                PIECE2  lc1_4,lc1_2
                TICKL   ymxr_lc1b,1,ymxr_ref1,ymxr_clhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lc1b_end:
ymxr_a_lc1_3 equ     ymxr_lc1b_key+2
ymxr_r_lc1_3 equ     ymxr_ref
ymxr_n_lc1_3 equ     ymxr_ref1-ymxr_ref
ymxr_w_lc1_3 equ     ymxr_refat+2
                ENDPIECE lc1_4
                PIECE2  lc2_3,lc2_1
                LPAD    KEY_LC,ymxr_ref2
                ENDPIECE lc2_3
                PIECE2  lc2_4,lc2_2
                TICKL   ymxr_lc2b,1,ymxr_ref2,ymxr_clhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lc2b_end:
ymxr_a_lc2_3 equ     ymxr_lc2b_key+2
ymxr_r_lc2_3 equ     ymxr_ref
ymxr_n_lc2_3 equ     ymxr_ref2-ymxr_ref
ymxr_w_lc2_3 equ     ymxr_refat+4
                ENDPIECE lc2_4
                PIECE2  lc3_3,lc3_1
                LPAD    KEY_LC,ymxr_ref3
                ENDPIECE lc3_3
                PIECE2  lc3_4,lc3_2
                TICKL   ymxr_lc3b,1,ymxr_ref3,ymxr_clhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lc3b_end:
ymxr_a_lc3_3 equ     ymxr_lc3b_key+2
ymxr_r_lc3_3 equ     ymxr_ref
ymxr_n_lc3_3 equ     ymxr_ref3-ymxr_ref
ymxr_w_lc3_3 equ     ymxr_refat+6
                ENDPIECE lc3_4
                PIECE2  lv0_3,lv0_1
                LPAD    KEY_LV,ymxr_ref0
                ENDPIECE lv0_3
                PIECE2  lv0_4,lv0_2
                TICKWL  ymxr_lv0b,3,0,2,ymxr_ref0,ymxr_lhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lv0b_end:
ymxr_a_lv0_3 equ     ymxr_lv0b_key+2
ymxr_r_lv0_3 equ     ymxr_ref
ymxr_n_lv0_3 equ     ymxr_ref0-ymxr_ref
ymxr_w_lv0_3 equ     ymxr_refat+0
                ENDPIECE lv0_4
                PIECE2  lv1_3,lv1_1
                LPAD    KEY_LV,ymxr_ref1
                ENDPIECE lv1_3
                PIECE2  lv1_4,lv1_2
                TICKWL  ymxr_lv1b,3,0,2,ymxr_ref1,ymxr_lhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lv1b_end:
ymxr_a_lv1_3 equ     ymxr_lv1b_key+2
ymxr_r_lv1_3 equ     ymxr_ref
ymxr_n_lv1_3 equ     ymxr_ref1-ymxr_ref
ymxr_w_lv1_3 equ     ymxr_refat+2
                ENDPIECE lv1_4
                PIECE2  lv2_3,lv2_1
                LPAD    KEY_LV,ymxr_ref2
                ENDPIECE lv2_3
                PIECE2  lv2_4,lv2_2
                TICKWL  ymxr_lv2b,3,0,2,ymxr_ref2,ymxr_lhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lv2b_end:
ymxr_a_lv2_3 equ     ymxr_lv2b_key+2
ymxr_r_lv2_3 equ     ymxr_ref
ymxr_n_lv2_3 equ     ymxr_ref2-ymxr_ref
ymxr_w_lv2_3 equ     ymxr_refat+4
                ENDPIECE lv2_4
                PIECE2  lv3_3,lv3_1
                LPAD    KEY_LV,ymxr_ref3
                ENDPIECE lv3_3
                PIECE2  lv3_4,lv3_2
                TICKWL  ymxr_lv3b,3,0,2,ymxr_ref3,ymxr_lhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lv3b_end:
ymxr_a_lv3_3 equ     ymxr_lv3b_key+2
ymxr_r_lv3_3 equ     ymxr_ref
ymxr_n_lv3_3 equ     ymxr_ref3-ymxr_ref
ymxr_w_lv3_3 equ     ymxr_refat+6
                ENDPIECE lv3_4
                PIECE2  lh3_3,lh3_1
                LPAD    KEY_LH,ymxr_ref3
                ENDPIECE lh3_3
                PIECE2  lh3_4,lh3_2
                TICKWL  ymxr_lh3b,3,1,2,ymxr_ref3,ymxr_clhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lh3b_end:
ymxr_a_lh3_3 equ     ymxr_lh3b_key+2
ymxr_r_lh3_3 equ     ymxr_ref
ymxr_n_lh3_3 equ     ymxr_ref3-ymxr_ref
ymxr_w_lh3_3 equ     ymxr_refat+6
                ENDPIECE lh3_4
                PIECE2  lg3_3,lg3_1
                LPAD    KEY_LG,ymxr_ref3
                ENDPIECE lg3_3
                PIECE2  lg3_4,lg3_2
                TICKWL  ymxr_lg3b,3,1,3,ymxr_ref3,ymxr_clhalt3,$DF,$FFFFFA11,$FFFFFA1D,$0F,$FFFFFA09,5,$077,ymxr_inst3
ymxr_lg3b_end:
ymxr_a_lg3_3 equ     ymxr_lg3b_key+2
ymxr_r_lg3_3 equ     ymxr_ref
ymxr_n_lg3_3 equ     ymxr_ref3-ymxr_ref
ymxr_w_lg3_3 equ     ymxr_refat+6
                ENDPIECE lg3_4
                PIECE2  lh0_3,lh0_1
                LPAD    KEY_LH,ymxr_ref0
                ENDPIECE lh0_3
                PIECE2  lh0_4,lh0_2
                TICKWL  ymxr_lh0b,3,1,2,ymxr_ref0,ymxr_clhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lh0b_end:
ymxr_a_lh0_3 equ     ymxr_lh0b_key+2
ymxr_r_lh0_3 equ     ymxr_ref
ymxr_n_lh0_3 equ     ymxr_ref0-ymxr_ref
ymxr_w_lh0_3 equ     ymxr_refat+0
                ENDPIECE lh0_4
                PIECE2  lg0_3,lg0_1
                LPAD    KEY_LG,ymxr_ref0
                ENDPIECE lg0_3
                PIECE2  lg0_4,lg0_2
                TICKWL  ymxr_lg0b,3,1,3,ymxr_ref0,ymxr_clhalt0,$DF,$FFFFFA0F,$FFFFFA19,$F0,$FFFFFA07,5,$070,ymxr_inst0
ymxr_lg0b_end:
ymxr_a_lg0_3 equ     ymxr_lg0b_key+2
ymxr_r_lg0_3 equ     ymxr_ref
ymxr_n_lg0_3 equ     ymxr_ref0-ymxr_ref
ymxr_w_lg0_3 equ     ymxr_refat+0
                ENDPIECE lg0_4
                PIECE2  lh2_3,lh2_1
                LPAD    KEY_LH,ymxr_ref2
                ENDPIECE lh2_3
                PIECE2  lh2_4,lh2_2
                TICKWL  ymxr_lh2b,3,1,2,ymxr_ref2,ymxr_clhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lh2b_end:
ymxr_a_lh2_3 equ     ymxr_lh2b_key+2
ymxr_r_lh2_3 equ     ymxr_ref
ymxr_n_lh2_3 equ     ymxr_ref2-ymxr_ref
ymxr_w_lh2_3 equ     ymxr_refat+4
                ENDPIECE lh2_4
                PIECE2  lg2_3,lg2_1
                LPAD    KEY_LG,ymxr_ref2
                ENDPIECE lg2_3
                PIECE2  lg2_4,lg2_2
                TICKWL  ymxr_lg2b,3,1,3,ymxr_ref2,ymxr_clhalt2,$FE,$FFFFFA0F,$FFFFFA1B,$F0,$FFFFFA07,0,$707,ymxr_inst2
ymxr_lg2b_end:
ymxr_a_lg2_3 equ     ymxr_lg2b_key+2
ymxr_r_lg2_3 equ     ymxr_ref
ymxr_n_lg2_3 equ     ymxr_ref2-ymxr_ref
ymxr_w_lg2_3 equ     ymxr_refat+4
                ENDPIECE lg2_4
                PIECE2  lh1_3,lh1_1
                LPAD    KEY_LH,ymxr_ref1
                ENDPIECE lh1_3
                PIECE2  lh1_4,lh1_2
                TICKWL  ymxr_lh1b,3,1,2,ymxr_ref1,ymxr_clhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lh1b_end:
ymxr_a_lh1_3 equ     ymxr_lh1b_key+2
ymxr_r_lh1_3 equ     ymxr_ref
ymxr_n_lh1_3 equ     ymxr_ref1-ymxr_ref
ymxr_w_lh1_3 equ     ymxr_refat+2
                ENDPIECE lh1_4
                PIECE2  lg1_3,lg1_1
                LPAD    KEY_LG,ymxr_ref1
                ENDPIECE lg1_3
                PIECE2  lg1_4,lg1_2
                TICKWL  ymxr_lg1b,3,1,3,ymxr_ref1,ymxr_clhalt1,$EF,$FFFFFA11,$FFFFFA1D,$F0,$FFFFFA09,4,$007,ymxr_inst1
ymxr_lg1b_end:
ymxr_a_lg1_3 equ     ymxr_lg1b_key+2
ymxr_r_lg1_3 equ     ymxr_ref
ymxr_n_lg1_3 equ     ymxr_ref1-ymxr_ref
ymxr_w_lg1_3 equ     ymxr_refat+2
                ENDPIECE lg1_4
ymxr_bank1end:
                .endif

                .if     YMXR_MAP=0
                INSTALLERS
                .endif
                .endif
ymxr_reach:                             ; past the last handler, which the
                                        ; reach init measures runs to
                .endm

                .macro  HANDLEQU
; The offsets the patching writes at, measured off the first handler's
; labels: the row it writes, the marker it reads, and its loop cell.
; A monitor's mark is between those blocks, and the distances follow
; it. Every handler is laid out by the one macro, so one handler's
; offsets are all four handlers'. The rig reads them back. A size is
; measured to the end of the first handler's piece (pieces.S): both its
; labels are in one piece, so a link that leaves handlers out leaves it
; as it is.
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
TICK_SIZE       equ     ymxr_e_effect0_2-ymxr_tick0

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
TICKC_PARK      equ     ymxr_cnt0_park-ymxr_cnt0+2
TICKC_LEFT      equ     ymxr_cnt0_left-ymxr_cnt0
TICKC_SIZE      equ     ymxr_e_counted0_1-ymxr_cnt0

; A square's handler, the same way: the register it selects, the row it
; is at, the two rows' difference, and the address of the row it
; is at, which init writes once.
SQ_SEL          equ     ymxr_sq0_sel-ymxr_sq0+2
SQ_VAL          equ     ymxr_sq0_sel-ymxr_sq0+4
SQ_MASK         equ     ymxr_sq0_tog-ymxr_sq0+3
SQ_PTR          equ     ymxr_sq0_tog-ymxr_sq0+4
SQ_SR           equ     ymxr_sq0_sel-ymxr_sq0+8
SQ_SIZE         equ     ymxr_e_square0_1-ymxr_sq0

; A one-row source's handler: the register it selects and the row it
; writes, both immediates a start puts there, those two fields alone.
ONE_SEL         equ     ymxr_one0_sel-ymxr_one0+2
ONE_VAL         equ     ymxr_one0_sel-ymxr_one0+4
ONE_SR          equ     ymxr_one0_sel-ymxr_one0+8
ONE_SIZE        equ     ymxr_e_one0_1-ymxr_one0

                TWAT    TW2,ymxr_two0
                TWAT    TW3,ymxr_three0
TW3_SEL3        equ     ymxr_three0_sel3-ymxr_three0+3
TW3_PTR3        equ     ymxr_three0_ptr3-ymxr_three0+2
TW3_STEP3       equ     ymxr_three0_step3-ymxr_three0+2
TW3_LOOP3R      equ     ymxr_three0_loop3-ymxr_three0+2
TW3_LOOP3W      equ     ymxr_three0_loop3-ymxr_three0+6
TW2_SIZE        equ     ymxr_e_two0_1-ymxr_two0
TW3_SIZE        equ     ymxr_e_three0_1-ymxr_three0

; The counted shape of two columns, the offsets above and four besides:
; the counter's address in the subq that steps it, the rows of a pass
; through the loop, the counter the reload writes to, and the counter.
                TWAT    TWC,ymxr_env0
TWC_CNT         equ     ymxr_env0_cnt-ymxr_env0+2
TWC_RELD        equ     ymxr_env0_reld-ymxr_env0+2
TWC_RELDA       equ     ymxr_env0_reld-ymxr_env0+4
TWC_PARK        equ     ymxr_env0_park-ymxr_env0+2
TWC_LEFT        equ     ymxr_env0_left-ymxr_env0
TWC_SIZE        equ     ymxr_e_envelope0_1-ymxr_env0
                TWAT    TWCT,ymxr_cwide0
TWCT_CNT         equ     ymxr_cwide0_cnt-ymxr_cwide0+2
TWCT_RELD        equ     ymxr_cwide0_reld-ymxr_cwide0+2
TWCT_RELDA       equ     ymxr_cwide0_reld-ymxr_cwide0+4
TWCT_PARK        equ     ymxr_cwide0_park-ymxr_cwide0+2
TWCT_LEFT        equ     ymxr_cwide0_left-ymxr_cwide0
TWCT_SIZE        equ     ymxr_e_countedthree0_1-ymxr_cwide0
TWCT_SEL3        equ     ymxr_cwide0_sel3-ymxr_cwide0+3
TWCT_PTR3        equ     ymxr_cwide0_ptr3-ymxr_cwide0+2
TWCT_STEP3       equ     ymxr_cwide0_step3-ymxr_cwide0+2
TWCT_LOOP3R      equ     ymxr_cwide0_loop3-ymxr_cwide0+2
TWCT_LOOP3W      equ     ymxr_cwide0_loop3-ymxr_cwide0+6

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
                HOOKW   TWCT
                HOOKW   SQ
                HOOKW   ONE
                .if     (ymxr_inst1_skip-ymxr_inst1)<>INST_SKIP
                .error  "the installers place the skip apart"
                .endif
                .endif
                .endm
