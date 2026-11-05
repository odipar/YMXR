; lookup_checks.S - the offsets of the lookup kinds, the words their hooks
; write, and the checks of each instance's page residue, reach and bank.

; Lookup-kind offsets relative to effect 0. Assert each read's page
; residue and reach, bank bounds and BANK spacing between banks.
                LOOKAT1 LK,ymxr_lk0,0
                LOOKAT1 LC,ymxr_lc0,1
                LOOKATW LT,ymxr_lt0,0,0
                LOOKATW LN,ymxr_ln0,0,0
                LOOKATW LB,ymxr_lb0,1,0
                LOOKATW LV,ymxr_lv0,1,0
                LOOKATW LE,ymxr_le0,0,1
                LOOKATW LF,ymxr_lf0,0,1
                LOOKATW LG,ymxr_lg0,1,1
                LOOKATW LH,ymxr_lh0,1,1
                .if     YMXR_DEFER
; Hook words for lookup kinds, equivalent to HOOKW for identity
; handlers.
                HOOKW   LK
                HOOKW   LC
                HOOKW   LT
                HOOKW   LN
                HOOKW   LB
                HOOKW   LV
                HOOKW   LE
                HOOKW   LF
                HOOKW   LG
                HOOKW   LH
                .endif
                .if     (LOOK_STRIDE<>LB_SIZE)|(LOOK_STRIDE<>LV_SIZE)
                .error  "LOOK_STRIDE is apart from the size of the kinds of three columns"
                .endif
; The first register of every kind is at TICK_SEL, where
; ymxr_kindof and ymxr_remap read the running source's.
                .if     (TICKC_SEL<>TICK_SEL)|(TW2_SEL1<>TICK_SEL)|(TW3_SEL1<>TICK_SEL)|(TWC_SEL1<>TICK_SEL)
                .error  "a handler of the identity has its first register at another offset than TICK_SEL"
                .endif
                .if     (LK_SEL<>TICK_SEL)|(LC_SEL<>TICK_SEL)|(LT_SEL1<>TICK_SEL)|(LN_SEL1<>TICK_SEL)
                .error  "a lookup instance has its first register at another offset than TICK_SEL"
                .endif
                .if     (LB_SEL1<>TICK_SEL)|(LV_SEL1<>TICK_SEL)|(LE_SEL1<>TICK_SEL)|(LF_SEL1<>TICK_SEL)
                .error  "a lookup instance has its first register at another offset than TICK_SEL"
                .endif
                .if     YMXR_DEFER
                .if     (BANK&255)<>0
                .error  "BANK is off a multiple of 256"
                .endif
                .endif
                LCHECK  ymxr_lk0,ymxr_ref0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lk1,ymxr_ref1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lk2,ymxr_ref2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lk3,ymxr_ref3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lc0,ymxr_ref0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lc1,ymxr_ref1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lc2,ymxr_ref2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lc3,ymxr_ref3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lt0,ymxr_ref0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lt1,ymxr_ref1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lt2,ymxr_ref2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lt3,ymxr_ref3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_ln0,ymxr_ref0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_ln1,ymxr_ref1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_ln2,ymxr_ref2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_ln3,ymxr_ref3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lb0,ymxr_ref0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lb1,ymxr_ref1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lb2,ymxr_ref2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lb3,ymxr_ref3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lv0,ymxr_ref0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lv1,ymxr_ref1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lv2,ymxr_ref2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lv3,ymxr_ref3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_le0,ymxr_ref0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_le1,ymxr_ref1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_le2,ymxr_ref2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_le3,ymxr_ref3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lf0,ymxr_ref0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lf1,ymxr_ref1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lf2,ymxr_ref2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lf3,ymxr_ref3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lg0,ymxr_ref0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lg1,ymxr_ref1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lg2,ymxr_ref2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lg3,ymxr_ref3,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lh0,ymxr_ref0,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lh1,ymxr_ref1,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lh2,ymxr_ref2,ymxr_tick0,ymxr_bank0end
                LCHECK  ymxr_lh3,ymxr_ref3,ymxr_tick0,ymxr_bank0end
                .if     YMXR_DEFER
                LCHECK  ymxr_lk0b,ymxr_ref0,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lk0
                LCHECK  ymxr_lk1b,ymxr_ref1,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lk1
                LCHECK  ymxr_lk2b,ymxr_ref2,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lk2
                LCHECK  ymxr_lk3b,ymxr_ref3,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lk3
                LCHECK  ymxr_lc0b,ymxr_ref0,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lc0
                LCHECK  ymxr_lc1b,ymxr_ref1,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lc1
                LCHECK  ymxr_lc2b,ymxr_ref2,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lc2
                LCHECK  ymxr_lc3b,ymxr_ref3,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lc3
                LCHECK  ymxr_lt0b,ymxr_ref0,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lt0
                LCHECK  ymxr_lt1b,ymxr_ref1,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lt1
                LCHECK  ymxr_lt2b,ymxr_ref2,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lt2
                LCHECK  ymxr_lt3b,ymxr_ref3,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lt3
                LCHECK  ymxr_ln0b,ymxr_ref0,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_ln0
                LCHECK  ymxr_ln1b,ymxr_ref1,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_ln1
                LCHECK  ymxr_ln2b,ymxr_ref2,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_ln2
                LCHECK  ymxr_ln3b,ymxr_ref3,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_ln3
                LCHECK  ymxr_lb0b,ymxr_ref0,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lb0
                LCHECK  ymxr_lb1b,ymxr_ref1,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lb1
                LCHECK  ymxr_lb2b,ymxr_ref2,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lb2
                LCHECK  ymxr_lb3b,ymxr_ref3,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lb3
                LCHECK  ymxr_lv0b,ymxr_ref0,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lv0
                LCHECK  ymxr_lv1b,ymxr_ref1,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lv1
                LCHECK  ymxr_lv2b,ymxr_ref2,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lv2
                LCHECK  ymxr_lv3b,ymxr_ref3,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lv3
                LCHECK  ymxr_le0b,ymxr_ref0,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_le0
                LCHECK  ymxr_le1b,ymxr_ref1,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_le1
                LCHECK  ymxr_le2b,ymxr_ref2,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_le2
                LCHECK  ymxr_le3b,ymxr_ref3,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_le3
                LCHECK  ymxr_lf0b,ymxr_ref0,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lf0
                LCHECK  ymxr_lf1b,ymxr_ref1,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lf1
                LCHECK  ymxr_lf2b,ymxr_ref2,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lf2
                LCHECK  ymxr_lf3b,ymxr_ref3,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lf3
                LCHECK  ymxr_lg0b,ymxr_ref0,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lg0
                LCHECK  ymxr_lg1b,ymxr_ref1,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lg1
                LCHECK  ymxr_lg2b,ymxr_ref2,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lg2
                LCHECK  ymxr_lg3b,ymxr_ref3,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lg3
                LCHECK  ymxr_lh0b,ymxr_ref0,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lh0
                LCHECK  ymxr_lh1b,ymxr_ref1,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lh1
                LCHECK  ymxr_lh2b,ymxr_ref2,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lh2
                LCHECK  ymxr_lh3b,ymxr_ref3,ymxr_bank1,ymxr_bank1end
                LTWIN   ymxr_lh3
                .endif
