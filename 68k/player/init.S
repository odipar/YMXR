; init.S - YMXR_init, and the routines it calls to measure a source's
; reach, patch a read site and link a handler.

; A branch's displacement whose target is past the starts of the two
; shapes an effect's frame code has, into the word at the offset of the
; effect's head a1: the displacement as the starts left out make it, and
; d3 the bytes of the starts the effect keeps (start.S). d4 clobbered.
                .macro  AIM     disp,at
                move.w  #\disp,d4
                add.w   d3,d4
                move.w  d4,\at(a1)
                .endm

YMXR_init:
                cmp.l   #YMXR_MAGIC,(a0)
                bne     .bad
                move.w  TF_VERSION(a0),d0               ; version 7 to 9, a
                .if     YMXR_MAP                        ; DTX3 table (SPEC.md
                subq.w  #7,d0                           ; 3.3.5), and 10
                cmp.w   #3,d0
                .else
                subq.w  #YMXR_VERSION-2,d0
                cmp.w   #2,d0
                .endif
                bhi     .bad
                move.l  a6,-(sp)
                .if     YMXR_MAP
                move.l  a2,-(sp)                ; the page area (5.1)
                .endif
                movea.l a1,a6
                move.l  a0,WS_TUNE(a6)
                movea.l a0,a2
                adda.l  TF_IMAGE(a0),a2
                move.l  a2,WS_IMAGE(a6)
                move.b  TF_EFFECTS(a0),WS_EFFECTS(a6)
                .if     YMXR_DEFER=0
                btst    #4,WS_EFFECTS(a6)       ; a tune that defers a start
                bne     .bad6                   ; needs a core with them
                .endif
                .if     YMXR_MAP
; Version 10 bound layout: signed transformation-block offset at 24,
; source index at 28 (BINARIES.md 1.2). Earlier versions use identity
; and a zero block pointer. Effects bit 5 marks version 10 (SPEC.md
; 3.3.5).
                moveq   #0,d1
                subq.w  #3,d0                   ; the version less 10
                bne.s   .v9
                move.l  a0,d1
                add.l   TF_TRANS(a0),d1
.v9:            move.l  d1,WS_TRANS(a6)
                btst    #5,WS_EFFECTS(a6)
                beq.s   .v10
                tst.l   d1
                beq     .bad6
.v10:
                .endif
                cmp.b   #3,IM_VARIANT(a2)       ; a DTX3 image: its rows come
                bne     .bad6                   ; round by themselves
; The table to read, which the image may carry beside others: subtunes
; that agree on what an image fixes once share one, so the code in it
; appears once for them and every bound tune names its table (DTX abi.md
; 1). R and RR come out of that table's header (DTX SPEC.md 1) and not out
; of the metadata call, which returns the image's first table.
                movea.l a2,a1
                adda.l  TF_TABLE(a0),a1
                move.l  DT_ROWS(a1),d0
                move.l  DT_REPEAT(a1),d2
                move.l  d0,WS_R(a6)
                move.l  d0,WS_ROWS(a6)
                move.l  d2,WS_RR(a6)
                lea     WS_STATE(a6),a6         ; the block, in a6 (DTX,
                jsr     IM_INIT(a2)             ; abi.md 2); a1 the table's
                lea     -WS_STATE(a6),a6        ; header, and the workspace
                                                ; back at a fixed offset of it

; Every source resolved once: its first row, and its loop cell, which is
; row RR where it repeats, 0 where it plays once, -1 where it is two
; rows repeating to row 0, and -2 where it is one row repeating. The
; last two are the shapes with a separate handler. A start then
; reads two longs where it walked the index and read the source's
; header.
                movea.l WS_TUNE(a6),a1
                moveq   #0,d1
                moveq   #0,d5                   ; bit 0 a square among the
                move.b  TF_SOURCES(a1),d1       ; sources, bit 1 one row
                subq.w  #1,d1
                bmi     .resolved
                lea     WS_SOURCES(a6),a4
                moveq   #0,d4
                .if     YMXR_MAP
                tst.l   WS_TRANS(a6)            ; version 10: the index at 28
                beq.s   .source
                moveq   #4,d4
                .endif
.source:        movea.l a1,a3
                move.l  TF_INDEX(a3,d4.w),d0
                bclr    #31,d0                  ; the index's bit 31: a source
                sne     d3                      ; whose column fills its byte
                adda.l  d0,a3                   ; the source's DTX1 table
                lea     DT_PAYLOAD(a3),a5       ; its first row
                move.l  a5,(a4)+
                .if     YMXR_PCREL
                bsr.w   ymxr_inreach
                bcs     .bad6
                .endif
                move.l  DT_ROWS(a3),d2          ; R
                move.l  DT_REPEAT(a3),d0
                sub.l   d2,d0                   ; 0 where it plays once
                beq.s   .kind
                add.l   d2,d0
                add.l   a5,d0                   ; row RR, the loop cell
.kind:          tst.b   d3
                bne.s   .counted
                cmp.w   #1,DT_COLUMNS(a3)       ; a source of several columns
                bne.s   .several                ; runs the handler its columns
                cmp.l   a5,d0                   ; pick, at any rows; and a
                bne.s   .cell                   ; source of one column
                subq.l  #3,d2                   ; repeating to row 0 of one
                bcc.s   .cell                   ; row or of two has a
                move.l  d2,d0                   ; separate handler: -2 and
                not.l   d2                      ; bit 1, or -1 and bit 0
                bset    d2,d5
                bra.s   .cell
; A source of several columns: its loop cell and the stride from one
; column to the next are in the table beside, and the tag in the cell's
; place sends a start to the handler its columns pick.
.several:       move.l  d0,WS_WIDE-WS_SOURCES-4(a4)
                moveq   #-3,d0                  ; two columns
                cmp.w   #2,DT_COLUMNS(a3)
                beq.s   .stride
                moveq   #-4,d0                  ; three
.stride:        addq.l  #1,d2                   ; DTX1 pads a column to a word
                and.w   #$FFFE,d2               ; (DTX, SPEC.md 2.2)
                move.w  d2,WS_WIDE-WS_SOURCES(a4)
.tagged:        bset    #2,d5
.cell:          move.l  d0,(a4)+
                addq.w  #4,d4
                dbra    d1,.source
                bra.s   .resolved

; A source whose one column fills its byte: its rows carry no marker, so
; the rows of a pass and the rows of the loop are in the table beside,
; with its loop cell, and the tag in the cell's place sends a start to
; the routine every effect shares, which tells this tag from the tags of
; a source of several columns. A counted source of several columns: the
; stride from one column to the next and R are beside its loop cell, and
; a start reads the rows of a pass through the loop off the cell and the
; first row (SPEC.md 3.1.6).
.counted:       move.l  d0,WS_WIDE-WS_SOURCES-4(a4)
                cmp.w   #1,DT_COLUMNS(a3)       ; a counted source of several
                bne.s   .ccolumns               ; columns
                move.w  d2,WS_WIDE-WS_SOURCES(a4)   ; R, a pass through it
                sub.l   DT_REPEAT(a3),d2
                move.w  d2,WS_WIDE-WS_SOURCES+2(a4) ; R less RR, the loop
                moveq   #-5,d0
                bra.s   .tagged
.ccolumns:      move.w  d2,WS_WIDE-WS_SOURCES+2(a4) ; R, beside the stride
                moveq   #-6,d0
                bra.s   .stride
.resolved:

; The row's columns, as the table's column map defines them (DTX, abi.md
; 2 and SPEC.md 2.4): a live column is its slot's ring, slot times N past
; the pointer, and a constant column is its value. Every read site is
; written whole, an immediate of the value where its column is constant:
; a set inits one loaded copy for each subtune, and a column live in one
; tune is constant in another. A player linked for a song has the frame
; code of the effects the song runs, and the sites of the others are
; left as they are (ymxr_kept).
                movea.l WS_IMAGE(a6),a0
                adda.l  TF_TABLE(a1),a0
                lea     DT_PAYLOAD(a0),a0       ; the table's payload
                moveq   #0,d3
                move.w  (a0),d3                 ; N, the stride from one slot
                                                ; to the next
                lea     ymxr_reads(pc),a3
                lea     ymxr_frame(pc),a4
                moveq   #0,d1
                moveq   #YMXR_COLUMNS-1,d2
.patch:         move.w  (a3)+,d4
                move.w  d1,d0
                bsr     ymxr_kept
                beq.s   .apart
                move.w  d1,d0
                bsr     ymxr_col
.apart:         addq.w  #1,d1
                dbra    d2,.patch
                lea     ymxr_sites(pc),a3
                moveq   #YMXR_SITES-1,d2
.site:          move.w  (a3)+,d4
                move.w  (a3)+,d1
                move.w  d1,d0
                bsr     ymxr_kept
                beq.s   .away
                move.w  d1,d0
                bsr     ymxr_col
.away:          dbra    d2,.site

                .if     YMXR_AEOI
                move.b  MFP_VECTOR.w,WS_VECTOR(a6)  ; the mode kept, and the
                bclr    #3,MFP_VECTOR.w             ; the MFP ends each
                .endif

                .if     YMXR_NEST
; A tune that runs one effect has no second timer to nest inside a tick,
; so the level that tick drops buys it no safety: two nops are where the
; drop would be. A tune running two or more keeps the drop. The word is
; written into the general handler, the square's and the one-row
; source's of every effect: a timer the tune does not run is left
; unclaimed, and its handlers do not run. Every handler's word is
; written on every init: a set inits one loaded copy for each subtune,
; and a subtune that ran one effect alone left its nops where the next,
; running that effect beside another, needs the drop.
                move.l  #$46FC2500,d3           ; move.w #$2500,sr, the drop,
                move.b  WS_EFFECTS(a6),d0       ; which a handler keeps unless
                and.b   #$0F,d0                 ; its effect runs alone
                move.b  d0,d1
                subq.b  #1,d1
                and.b   d1,d0                   ; more than one bit set
                bne.s   .nests
                move.l  #$4E714E71,d3           ; nop nop, for the one it runs
.nests:
                .endif

; Where deferred starts are assembled in, every handler is in two banks
; (4.8), and each bank is patched the same way: the loop below runs bank 0
; with d6 at 0 and bank 1 with d6 at BANK; otherwise it runs bank 0
; alone.
                move.l  d6,-(sp)
                moveq   #0,d6
.banks:
; The tick handlers: each one's step and loop operands name a
; separate pair of cells, its place is parked on a marker until a start,
; and the register it selects is R0 until a start aims it (SPEC.md 5.2.1).
; Each loop below walks the instances of one kind the player has, a bit
; an effect in the kind's mask: a player linked for a song has the
; instances of the effects that start a source of the kind, one after
; the other (doc/LINKER.md).
                lea     ymxr_tick0(pc),a5
                adda.w  d6,a5
                move.b  ymxr_k_effect+1(pc),d4
                moveq   #3,d2
.link:          lsr.b   #1,d4
                bcc.s   .nolink
                bsr     ymxr_link
                .if     YMXR_NEST
                move.l  d3,TICK_SR(a5)
                .endif
                lea     TICK_SIZE(a5),a5
.nolink:        dbra    d2,.link
                PIECE   counted_1
                lea     ymxr_cnt0(pc),a5
                adda.w  d6,a5
                move.b  ymxr_k_counted+1(pc),d4
                moveq   #3,d2
.linkc:         lsr.b   #1,d4
                bcc.s   .nolinkc
                bsr     ymxr_linkc
                lea     TICKC_SIZE(a5),a5
.nolinkc:       dbra    d2,.linkc
                ENDPIECE counted_1

                .if     YMXR_NEST
                PIECE   one_1
                lea     ymxr_one0(pc),a5
                adda.w  d6,a5
                move.b  ymxr_k_one+1(pc),d4
                moveq   #3,d2
.one:           lsr.b   #1,d4
                bcc.s   .noone
                move.l  d3,ONE_SR(a5)
                lea     ONE_SIZE(a5),a5
.noone:         dbra    d2,.one
                ENDPIECE one_1
                .endif

; A square's handler names the row it is at in the one instruction
; that moves between the two, and so does the copy of that instruction in
; its hook (4.8).
                PIECE   square_1
                lea     ymxr_sq0(pc),a5
                adda.w  d6,a5
                move.b  ymxr_k_square+1(pc),d4
                moveq   #3,d2
.sq:            lsr.b   #1,d4
                bcc.s   .nosq
                lea     SQ_VAL(a5),a3
                move.l  a3,SQ_PTR(a5)
                .if     YMXR_NEST
                move.l  d3,SQ_SR(a5)
                .endif
                .if     YMXR_DEFER
                move.l  a3,SQ_HPTR(a5)
                .endif
                lea     SQ_SIZE(a5),a5
.nosq:          dbra    d2,.sq
                ENDPIECE square_1

; The handlers of several columns: the loop cells they read and the
; places they step are in the handler, so one link writes every address
; the loop path and the step read, as a general handler's link does. A
; start writes the values, and no timer reaches one of these handlers
; before a start has aimed its vector there.
                PIECE   two_1
                lea     ymxr_two0(pc),a5
                adda.w  d6,a5
                move.b  ymxr_k_two+1(pc),d4
                moveq   #3,d2
.linktwo:       lsr.b   #1,d4
                bcc.s   .nolinktwo
                lea     TW2_CELLS(a5),a3
                move.l  a3,TW2_LOOPT(a5)
                move.l  a3,TW2_LOOP1R(a5)
                addq.l  #CELL_SIZE,a3
                move.l  a3,TW2_LOOP2R(a5)
                lea     TW2_PTR1(a5),a3
                move.l  a3,TW2_STEP1(a5)
                move.l  a3,TW2_LOOP1W(a5)
                lea     TW2_PTR2(a5),a3
                move.l  a3,TW2_STEP2(a5)
                move.l  a3,TW2_LOOP2W(a5)
                lea     TW2_SIZE(a5),a5
.nolinktwo:     dbra    d2,.linktwo
                ENDPIECE two_1
                PIECE   three_1
                lea     ymxr_three0(pc),a5
                adda.w  d6,a5
                move.b  ymxr_k_three+1(pc),d4
                moveq   #3,d2
.linkthree:     lsr.b   #1,d4
                bcc.s   .nolinkthree
                lea     TW3_CELLS(a5),a3
                move.l  a3,TW3_LOOPT(a5)
                move.l  a3,TW3_LOOP1R(a5)
                addq.l  #CELL_SIZE,a3
                move.l  a3,TW3_LOOP2R(a5)
                addq.l  #CELL_SIZE,a3
                move.l  a3,TW3_LOOP3R(a5)
                lea     TW3_PTR1(a5),a3
                move.l  a3,TW3_STEP1(a5)
                move.l  a3,TW3_LOOP1W(a5)
                lea     TW3_PTR2(a5),a3
                move.l  a3,TW3_STEP2(a5)
                move.l  a3,TW3_LOOP2W(a5)
                lea     TW3_PTR3(a5),a3
                move.l  a3,TW3_STEP3(a5)
                move.l  a3,TW3_LOOP3W(a5)
                lea     TW3_SIZE(a5),a5
.nolinkthree:   dbra    d2,.linkthree
                ENDPIECE three_1
                PIECE   envelope_1
                lea     ymxr_env0(pc),a5
                adda.w  d6,a5
                move.b  ymxr_k_envelope+1(pc),d4
                moveq   #3,d2
.linkenv:       lsr.b   #1,d4
                bcc.s   .nolinkenv
                lea     TWC_CELLS(a5),a3
                move.l  a3,TWC_LOOPT(a5)
                move.l  a3,TWC_LOOP1R(a5)
                addq.l  #CELL_SIZE,a3
                move.l  a3,TWC_LOOP2R(a5)
                lea     TWC_PTR1(a5),a3
                move.l  a3,TWC_STEP1(a5)
                move.l  a3,TWC_LOOP1W(a5)
                lea     TWC_PTR2(a5),a3
                move.l  a3,TWC_STEP2(a5)
                move.l  a3,TWC_LOOP2W(a5)
                lea     TWC_LEFT(a5),a3
                move.l  a3,TWC_CNT(a5)
                move.l  a3,TWC_RELDA(a5)
                move.w  #1,TWC_LEFT(a5)
                lea     TWC_SIZE(a5),a5
.nolinkenv:     dbra    d2,.linkenv
                ENDPIECE envelope_1
                .if     YMXR_MAP
; Link lookup instances as above, selecting page 0 from the bank's
; page-byte table. Clearing the records below leaves every page
; uncached.
                lea     ymxr_htab(pc),a4
                tst.w   d6
                beq.s   .htab
                lea     32(a4),a4
.htab:
                PIECE   lk_1                    ; with the level drop the
                LINK1   LK,ymxr_k_lk,0,0,1      ; general handler has
                ENDPIECE lk_1
                PIECE   lc_1
                LINK1   LC,ymxr_k_lc,1,1,0
                ENDPIECE lc_1
                PIECE   lt_1
                LINKW   LT,ymxr_k_lt,2,0,0
                ENDPIECE lt_1
                PIECE   ln_1
                LINKW   LN,ymxr_k_ln,3,0,0
                ENDPIECE ln_1
                PIECE   lb_1
                LINKW   LB,ymxr_k_lb,4,1,0
                ENDPIECE lb_1
                PIECE   lv_1
                LINKW   LV,ymxr_k_lv,5,1,0
                ENDPIECE lv_1
                PIECE   le_1
                LINKW   LE,ymxr_k_le,6,0,1
                ENDPIECE le_1
                PIECE   lf_1
                LINKW   LF,ymxr_k_lf,7,0,1
                ENDPIECE lf_1
                .endif
                .if     YMXR_DEFER
                tst.w   d6
                bne.s   .banked
                move.w  #BANK,d6
                bra     .banks
.banked:
                .endif
                move.l  (sp)+,d6

; The effects' records: the kept target, select, count and shape empty
; until a start, the first row the parked place, so a start that moves
; no place is at row 0 of the source it names (SPEC.md 4.1 step 4), and
; no start pending.
                lea     WS_FX(a6),a0
                lea     ymxr_park(pc),a1
                moveq   #3,d0
.clear:         movea.l a0,a2
                moveq   #FX_SIZE/4-1,d1
.zero:          clr.l   (a2)+
                dbra    d1,.zero
                move.l  a1,FX_FIRST(a0)
                move.l  a1,FX_FIRST2(a0)
                move.l  a1,FX_FIRST3(a0)
                lea     FX_SIZE(a0),a0
                dbra    d0,.clear
                .if     YMXR_DEFER
                sf      WS_ARMING(a6)
                .endif
                .if     YMXR_MAP
                movea.l (sp),a2                 ; the page area
                bsr     ymxr_prefill
                tst.l   d0
                bmi     .bad6
                .endif

; The start tells three shapes apart off one cell, and a tune using one
; of the two shapes with a separate handler and not the other needs no
; test: the branch that leaves the general handler's path goes straight
; to that shape's block, and the start pays no test for the shape it
; does not use. A tune using both, or neither, keeps the test. The word
; is written on every path: a tune before this one that used one shape
; wrote its block there, and a set inits one loaded copy for every
; subtune.
; Bit 16 of d1 and bit 8 of d0 mark a branch to the start of a source of
; one row, past the square's, whose bytes init adds for each effect.
                move.l  #PICK_BOTH&$FFFF,d1
                move.b  d5,d0
                btst    #2,d0                   ; a tune with a source of
                bne.s   .only                   ; several columns keeps the
                tst.b   d0                      ; test in: such a source's
                beq.s   .only                   ; cell is a tag past the two
                cmp.b   #3,d0
                beq.s   .only
                move.l  #$10000|(PICK_ONE&$FFFF),d1
                btst    #0,d0
                beq.s   .only
                move.l  #PICK_SQ&$FFFF,d1
.only:          move.l  #((WIDE_ONE&$FFFF)<<16)|$100|(RESET_DONE&$FF),d0
                btst    #2,d5                   ; and the three branches that
                beq.s   .aims                   ; lie elsewhere where it has
                move.l  #((WIDE_WIDEN&$FFFF)<<16)|(RESET_WIDE&$FF),d0
.aims:

; The effects the tune runs are read every row; the others are jumped
; over by the branch at each macro's head, where the tune does not run
; its effect.
; The head of each effect: the effect's first read where the tune runs
; it, and where it does not, a branch, its displacement to the head of
; the next effect the tune does run or past the last of them. A run of
; effects the tune does not run then costs one branch. This is after the
; sites above, which write the read whole into the two words a skipped
; effect's branch occupies, so a head the tune runs is that read already.
                lea     ymxr_frame(pc),a4
                lea     ymxr_overs(pc),a3
                lea     ymxr_resets(pc),a2
                lea     ymxr_regs(pc),a5        ; past effect 3's frame code
                moveq   #3,d2
.over:          move.w  (a3)+,d4
                btst    d2,ymxr_k_effect+1(pc)  ; an effect the link left
                bne.s   .linked                 ; out: no head, and its reset
                addq.l  #6,a2                   ; is not there
                bra.w   .unlinked
.linked:        lea     (a4,d4.w),a1
                btst    d2,WS_EFFECTS(a6)
                beq.s   .skip
                movea.l a1,a5                   ; the effect's first read
                bra.s   .pick
.skip:          move.w  #$6000,(a1)             ; bra.w: the head the assembler
                move.l  a5,d3                   ; wrote, which a tune before
                sub.l   a1,d3                   ; this one that ran the effect
                subq.l  #2,d3                   ; replaced with the read
                move.w  d3,2(a1)
.pick:          move.l  d0,d5
                move.w  (a2)+,d4
                move.b  d5,(a4,d4.w)            ; the place's reset, at the site
                move.w  (a2)+,d3                ; the table names; the bytes of
                move.w  d1,d4                   ; the square's start the effect
                btst    #16,d1                  ; keeps, past which the source
                beq.s   .pset                   ; of one row's starts
                add.w   d3,d4
.pset:          move.w  d4,PICK_AT(a1)
                swap    d5
                btst    #8,d0
                beq.s   .wset
                add.w   d3,d5
.wset:          move.w  d5,WIDE_AT(a1)
                move.w  (a2)+,d3                ; and of both starts, past
                lea     (a1,d3.w),a0            ; which the rest of the frame
                                                ; code is

                .if     YMXR_DEFER
; A tune that defers a start, bit 4 of the effects used (SPEC.md 3.3),
; aims three branches of every effect at the paths that read a pending
; start (4.8); every other tune keeps the three the assembler wrote, so
; its frame costs what it cost before deferred starts. Each is written
; on every init, as the heads are.
                btst    #4,WS_EFFECTS(a6)
                beq.s   .plain
                move.w  #$6000,RS_AT(a1)        ; bra.w
                AIM     RS_DEFER,RS_AT+2
                AIM     UNSET_DEFER,UNSET_AT
                move.w  #$6000,SET_AT(a1)       ; bra.w
                AIM     SET_DEFER,SET_AT+2
                bra.s   .aimed
.plain:         move.l  #$08020006,RS_AT(a1)    ; btst #6,d2
                AIM     UNSET_RATE,UNSET_AT
                move.w  #$6700,SET_AT(a1)       ; beq.w
                AIM     SET_STOP,SET_AT+2
.aimed:
                .endif

                .if     YMXR_MAP
; For effects bit 5, redirect each effect's start, unset-source and
; timer-reset branches to mapping-aware paths. Reset preserves the
; control byte in d3 for remaps. Other tunes use identity start and
; ordinary rate/reset paths. Rewrite every branch at each init.
;
; With deferred support, an unset mapping continues through _retune when
; the tune also defers starts. That path updates the record after a tick
; installs a pending start; other tunes continue directly to rate
; processing.
                .if     YMXR_DEFER
                move.w  #REMAP_RATE,REMAP_AT(a0)
                .endif
                btst    #5,WS_EFFECTS(a6)
                beq.s   .identity
                AIM     NOW_MAP,NOW_AT
                AIM     UNSET_MAP,UNSET_AT
                move.w  #$6000,RS_AT(a1)        ; bra.w
                AIM     RS_MAP,RS_AT+2
                .if     YMXR_DEFER
                btst    #4,WS_EFFECTS(a6)
                beq.s   .mapsnext
                move.w  #REMAP_RETUNE,REMAP_AT(a0)
                .endif
                bra.s   .mapsnext
.identity:      move.w  #NOW_START,NOW_AT(a1)
                .if     YMXR_DEFER=0
                AIM     UNSET_RATE,UNSET_AT
                move.l  #$08020006,RS_AT(a1)    ; btst #6,d2
                .endif
.mapsnext:
                .endif
.unlinked:      dbra    d2,.over

; The advance's address, written into the call that plays: a slot is
; a bra.w (DTX, abi.md 1), so the body is two bytes past the slot
; plus the branch's displacement, and a play call reaches it in one jsr.
                movea.l WS_IMAGE(a6),a2
                lea     IM_ADVANCE(a2),a3
                move.w  2(a3),d0
                ext.l   d0
                lea     2(a3,d0.l),a3
                lea     ymxr_call+2(pc),a4
                move.l  a3,(a4)

; The first row, read here so that a call writes a row it already has
; (SPEC.md 4): the writes then leave a host's clock at one offset,
; by the advance for the next row.
                move.l  a6,-(sp)
                lea     WS_STATE(a6),a6         ; the block, in a6
                jsr     (a3)                    ; a1 the row
                movea.l (sp)+,a6

; The timers the tune runs, claimed. The others are left as they are.
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                CLAIM   0,$FFFFFA19,$F0,$134,$FFFFFA07,5,ymxr_tick0
                CLAIM   1,$FFFFFA1D,$F0,$110,$FFFFFA09,4,ymxr_tick1
                CLAIM   2,$FFFFFA1B,$F0,$120,$FFFFFA07,0,ymxr_tick2
                CLAIM   3,$FFFFFA1D,$0F,$114,$FFFFFA09,5,ymxr_tick3
                move.w  (sp)+,sr
                .if     YMXR_MAP
                addq.l  #4,sp
                .endif
                movea.l (sp)+,a6
                moveq   #0,d0
                rts
.bad6:
                .if     YMXR_MAP
                addq.l  #4,sp
                .endif
                movea.l (sp)+,a6
.bad:           moveq   #-1,d0
                rts

; Under YMXR_PCREL a tick reads its row through a signed word
; displacement from the instruction that reads it, so every row of every
; source is within reach of every handler. a5 the source's first row,
; a3 its DTX1 table: the rows run (C - 1) strides and R bytes from the
; first (SPEC.md 3.1.2), and the handlers run from ymxr_tick0 to
; ymxr_reach. Carry marks a source a displacement does not reach, which
; init reports -1 for. d0, d2 and a0 clobbered.
                .if     YMXR_PCREL
ymxr_inreach:   move.l  DT_ROWS(a3),d0
                addq.l  #1,d0
                and.w   #$FFFE,d0               ; align(R), a column's stride
                moveq   #0,d2                   ; (DTX, SPEC.md 2.2)
                move.w  DT_COLUMNS(a3),d2
                subq.w  #1,d2
                mulu    d2,d0                   ; the columns before the last
                add.l   DT_ROWS(a3),d0
                lea     -1(a5,d0.l),a0          ; the source's last byte
                move.l  a0,d0
                lea     ymxr_tick0(pc),a0
                sub.l   a0,d0                   ; from the first handler
                cmp.l   #32767,d0
                bgt.s   .far
                lea     ymxr_reach(pc),a0
                move.l  a0,d0
                sub.l   a5,d0                   ; to the source's first byte
                cmp.l   #32768,d0
                bgt.s   .far
                andi.b  #$FE,ccr
                rts
.far:           ori.b   #1,ccr
                rts
                .endif

; One read site written whole, of column d0, out of the payload in a0 and
; N in d3 (DTX, SPEC.md 2.4): the word at (a4,d4.w) is its operand and
; the word before it its opcode. A live column is read at its
; displacement, its slot times N, and a constant column's read is the
; same instruction with its value as an immediate, which costs 8 cycles
; to the displacement's 12 and sets the flags as the read sets them. The
; constant values follow the map in column order, so a constant column's
; value is one byte past the value of each constant column before it.
; The opcode as it is defines the read, whichever the last init wrote: an
; or into d1, its bits 14 to 12 clear; a move into d2, bit 10 set; or a
; move into d1, the head of an effect's bra.w being one. d0, d4, a1 and a5
; clobbered.
ymxr_col:       lea     (a4,d4.w),a5            ; the operand
                .if     YMXR_MAP
                cmp.w   DT_COLUMNS-DT_PAYLOAD(a0),d0    ; a column at or past
                bcs.s   .in                             ; the table's C, a
                move.w  #$8000,d0                       ; mapping column of a
                bra.s   .put                            ; version 9 tune: the
.in:                                                    ; constant 0
                .endif
                moveq   #0,d4
                move.b  8(a0,d0.w),d4           ; the column's byte of the map
                cmp.b   #$FF,d4
                beq.s   .value
                mulu.w  d3,d4                   ; its slot times N
                move.w  d4,d0
                bra.s   .put
                .if     YMXR_MAP
.value:         lea     8(a0),a1                ; the values, behind the map
                adda.w  DT_COLUMNS-DT_PAYLOAD(a0),a1    ; of C columns
                .else
.value:         lea     8+YMXR_COLUMNS(a0),a1   ; the values, behind the map
                .endif
                subq.w  #1,d0
                bmi.s   .at
.before:        cmp.b   #$FF,8(a0,d0.w)
                bne.s   .slot
                addq.l  #1,a1
.slot:          dbra    d0,.before
.at:            moveq   #0,d0
                move.b  (a1),d0
                bset    #15,d0
.put:           move.w  #$7000,d4
                and.w   -2(a5),d4
                beq.s   .or
                move.w  #$0400,d4
                and.w   -2(a5),d4               ; bit 10: a move into d2
                lsr.w   #1,d4
                add.w   #$1229,d4               ; move.b d16(a1),d1 or d2
                tst.w   d0
                bpl.s   .live
                eori.w  #$0015,d4               ; move.b #v,d1 or d2
                bra.s   .immediate
.or:            move.w  #$8229,d4               ; or.b d16(a1),d1
                tst.w   d0
                bpl.s   .live
                moveq   #$0001,d4               ; or.b #v,d1
.immediate:     and.w   #$00FF,d0
.live:          move.w  d4,-2(a5)
                move.w  d0,(a5)
                rts

; Z set where column d0 is of an effect whose frame code a link left out
; (doc/LINKER.md): its target, source, control and count columns, 14 to
; 29, and its mapping column, 30 to 33. A register's column is kept. d0
; clobbered.
ymxr_kept:      sub.w   #14,d0
                bcs.s   .register
                cmp.w   #16,d0
                bcs.s   .effect
                sub.w   #16,d0                  ; a mapping column's effect
                bra.s   .at
.effect:        lsr.w   #2,d0
.at:            btst    d0,ymxr_k_effect+1(pc)
                rts
.register:      moveq   #1,d0
                rts

; a5 a tick handler: its operands linked, its place parked.
ymxr_link:
                lea     TICK_PTR(a5),a3
                move.l  a3,TICK_STEP(a5)
                move.l  a3,TICK_LOOPW(a5)
                lea     TICK_CELL(a5),a3
                move.l  a3,TICK_LOOPT(a5)
                move.l  a3,TICK_LOOPR(a5)
                .if     YMXR_PCREL
                clr.w   TICK_CELL(a5)
                lea     ymxr_park(pc),a3
                suba.l  a5,a3
                suba.w  #TICK_PTR,a3
                move.w  a3,TICK_PTR(a5)
                .else
                clr.l   TICK_CELL(a5)
                lea     ymxr_park(pc),a3
                move.l  a3,TICK_PTR(a5)
                .endif
                clr.b   TICK_SEL(a5)            ; R0, the register of the kept
                rts                             ; target init leaves (SPEC.md 5.2.1)

                PIECE   counted_2
; a5 a counted handler, the same way: its operands linked, its counter
; at 1 and its cell at 0, so a tick before the first start writes the
; parked marker and stops the timer, as the handler above does.
ymxr_linkc:
                lea     TICKC_PTR(a5),a3
                move.l  a3,TICKC_STEP(a5)
                move.l  a3,TICKC_LOOPW(a5)
                lea     TICKC_CELL(a5),a3
                move.l  a3,TICKC_LOOPT(a5)
                move.l  a3,TICKC_LOOPR(a5)
                .if     YMXR_PCREL
                clr.w   TICKC_CELL(a5)
                .else
                clr.l   TICKC_CELL(a5)
                .endif
                lea     TICKC_LEFT(a5),a3
                move.l  a3,TICKC_CNTA(a5)
                move.l  a3,TICKC_RELDA(a5)
                move.w  #1,TICKC_LEFT(a5)
                lea     ymxr_park(pc),a3
                .if     YMXR_PCREL
                suba.l  a5,a3
                suba.w  #TICKC_PTR,a3
                move.w  a3,TICKC_PTR(a5)
                .else
                move.l  a3,TICKC_PTR(a5)
                .endif
                clr.b   TICKC_SEL(a5)           ; R0, as init leaves the kept
                rts                             ; target (SPEC.md 5.2.1)
                ENDPIECE counted_2
