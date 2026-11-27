; copy.S - copy from frame (SPEC.md 3.1.7): the frame values, the start of
; copy from frame on a target, and init's work for a tune that copies.
; YMXR.S includes it where the lookup is assembled in, the core that reads
; a tune of version 12; play.S has the register steps of a frame that
; leaves a register copy from frame runs on to the ticks. Every part is a
; piece of the block `copy`, which a link keeps for a song a tune of which
; copies from frame (doc/LINKER.md), and init aims a branch at it for such
; a tune alone, so a tune that copies from no frame runs the frame and the
; starts it ran before.

                PIECE   copy_4
; The frame value of each register, R0 to R13, FV_STRIDE bytes apart: the
; value the frame last wrote to it, masked to the register's width, R7's
; with the ports' bits (1.4.2), and the register's number in the byte
; after it. Copy from frame is a source of one row repeating to row 0
; whose column a register is the register's frame value, which a counted
; handler runs, one row left and one row a pass, so a frame value has no
; marker and is the key of a transformation as it is (2.1.5). Each frame
; value is the first row of a source of one column, behind a source's
; header (DTX, SPEC.md 1): R 1, C 1 and RR 0, which the start, the recut
; and a deferred start read as they read a table's. Init writes the values
; of ymxr_fv0, the frame value 0 of each, before the first frame.
                .macro  FVROW   rn
                dc.b    0,\rn                   ; the frame value, its register
                dc.l    1                       ; R, C and RR of the next
                dc.w    1
                dc.l    0
                dc.w    0
                .endm
                .even
                dc.w    0
                dc.l    1                       ; R, C and RR of R0's
                dc.w    1
                dc.l    0
                dc.w    0
ymxr_fv:        FVROW   0
                FVROW   1
                FVROW   2
                FVROW   3
                FVROW   4
                FVROW   5
                FVROW   6
                FVROW   7
                FVROW   8
                FVROW   9
                FVROW   10
                FVROW   11
                FVROW   12
                FVROW   13
ymxr_fvend:
                .if     (ymxr_fvend-ymxr_fv)<>(14*FV_STRIDE)
                .error  "ymxr_fv: a frame value is FV_STRIDE bytes from the next"
                .endif
ymxr_fv0:       dc.b    $00,$00,$00,$00,$00,$00,$00,$C0,$00,$00,$00,$00,$00,$00
                .even
; The registers the ticks of copy from frame write, a bit each, as the
; frame last read them out of the effects' records (play.S, ymxr_cregs).
ymxr_cmask:     dc.w    0

; Each effect's read of its control column at the head of the paths of a
; row that sets a column of the effect (EFFECT, _some), in a tune that
; copies from frame: init writes a bsr.w over that read to here, and this
; read as it wrote that one. Such a row can start, stop, aim or settle
; the effect's handler, so the stub marks the frame, which reads the
; effects' records again (play.S, ymxr_cregs). Before the read, a vector
; the frame aimed at the effect's handler of copy from frame (ymxr_cxon)
; goes back to the handler the effect's record names, which a start, a
; stop, an arming and a place's reset read through the vector. Effect 3
; first, as ymxr_overs has the heads, each with its vector; CSOME_READ is
; the read's offset.
                .macro  CSOME   i,vector
                st      WS_CDIRTY(a0)
                btst    #\i,ymxr_xon(pc)
                beq.s   .k\~
                move.l  4*\i+ymxr_xback(pc),\vector.w
                move.l  a2,-(sp)
                lea     ymxr_xon(pc),a2
                bclr    #\i,(a2)
                movea.l (sp)+,a2
.k\~:           move.b  $7FFF(a1),d2
                rts
                .endm
ymxr_csome:     CSOME   3,$114
ymxr_csome2:    CSOME   2,$120
                CSOME   1,$110
                CSOME   0,$134
ymxr_csomeend:
CSOME_SIZE      equ     ymxr_csome2-ymxr_csome
CSOME_READ      equ     ymxr_csome2-ymxr_csome-6
; Each effect's handler the record names where the frame aimed its vector
; at its handler of copy from frame (ymxr_cxon); each register's
; immediate, which the frame writes each value it keeps to as well
; (columns.S, CPUT): the value of the move.l of the handler of copy from
; frame the frame last aimed a vector at for the register, or ymxr_cspare;
; each register's owner, the effect of that handler plus 1, or 0; and a
; bit each, the effects whose vectors the frame aimed and those it tests
; on the next frames that read no record, bit 7 set where the frame read
; the records.
ymxr_xback:     dc.l    0,0,0,0
ymxr_cptr:
                .rept   14
                dc.l    0
                .endr
ymxr_cown:      dc.b    0,0,0,0,0,0,0,0,0,0,0,0,0,0
ymxr_xon:       dc.b    0
ymxr_xnew:      dc.b    0
ymxr_cspare:    dc.w    0

; The targets of several registers as copy from frame reads them: a
; register and the column of copy from frame that writes it, which is the
; register, FV_STRIDE bytes a column, one slot a column and eight bytes a
; target, from target 14, in the order of ymxr_wide.
ymxr_wcopy:     dc.b    0,0,  1,1,  0,0,  0,0   ; 14 setToneA:     R0 R1
                dc.b    2,2,  3,3,  0,0,  0,0   ; 15 setToneB:     R2 R3
                dc.b    4,4,  5,5,  0,0,  0,0   ; 16 setToneC:     R4 R5
                dc.b    0,0,  8,8,  1,1,  0,0   ; 17 setVoiceA:    R0 R1 R8
                dc.b    2,2,  9,9,  3,3,  0,0   ; 18 setVoiceB:    R2 R3 R9
                dc.b    4,4, 10,10, 5,5,  0,0   ; 19 setVoiceC:    R4 R5 R10
                dc.b   11,11, 12,12, 0,0, 0,0   ; 20 setEnvelope:  R11 R12
                dc.b   11,11, 12,12, 13,13, 0,0 ; 21 setBuzzer:    R11 R12 R13
                dc.b    8,8,  6,6,  0,0,  0,0   ; 22 setNoiseA:    R6 R8
                dc.b    9,9,  6,6,  0,0,  0,0   ; 23 setNoiseB:    R6 R9
                dc.b   10,10, 6,6,  0,0,  0,0   ; 24 setNoiseC:    R6 R10

; The start of a source the start's tag sends past the shapes of one
; column, in a tune that copies from frame: copy from frame's tag, -8,
; which the start passes as -7, starts copy from frame, and every other
; tag the start of several columns. Init aims each effect's call of
; ymxr_widestart and the arming's here for a tune that copies.
ymxr_copywide:  cmp.l   #-7,d0
                beq.s   ymxr_copystart
                bra.w   ymxr_widestart

; A start of copy from frame on effect d4: the counted handler of the
; kept target's columns aimed at their frame values, a source of one row
; repeating to row 0 at row 0 (SPEC.md 3.1.7, 5.1). The registers and the
; return are those of ymxr_widestart: a3 the record, its mirror where a
; deferred start is armed (4.8), a5 the handler, and NZ, a source of
; several columns' return. d0, d1, d3 to d5, a2 and a4 clobbered.
ymxr_copystart:
                move.l  d6,-(sp)                ; the effect, kept while the
                move.w  d4,d6                   ; handler is resolved
                mulu    #FX_SIZE,d4
                lea     WS_FX(a0),a3
                adda.w  d4,a3                   ; the effect's record
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; a deferred start is armed in
                beq.s   .rec                    ; the record's mirror (4.8)
                lea     ARMOFF(a3),a3
                .endif
.rec:           moveq   #1,d0
                move.l  d0,FX_ROWS(a3)          ; R, one row
                lea     ymxr_fv(pc),a4          ; row 0 of the column of each
                move.l  a4,d0                   ; register, the loop cell
                moveq   #0,d3                   ; the place, row 0
                moveq   #FV_STRIDE,d1           ; a column a frame value apart
                moveq   #0,d5
                move.b  FX_TARGET(a3),d5
                cmp.w   #14,d5
                bcc.w   .wide
; A target of one register: the counted handler at its frame value, the
; place and the loop cell, one row left and one row a pass.
                move.w  d5,d4
                mulu    #FV_STRIDE,d4
                adda.w  d4,a4
                move.l  a4,d0
                lea     ymxr_at_counted(pc),a5
                bsr.w   .instance
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  d5,TICKC_SEL(a5)
                .if     YMXR_PCREL
                move.l  a4,d1
                sub.l   a5,d1
                sub.l   #TICKC_PTR,d1
                move.w  d1,TICKC_PTR(a5)
                move.w  d1,TICKC_CELL(a5)
                .else
                move.l  a4,TICKC_PTR(a5)
                move.l  a4,TICKC_CELL(a5)
                .endif
                move.w  #1,TICKC_LEFT(a5)
                move.w  #1,TICKC_RELD(a5)
                move.b  #4,FX_SHAPE(a3)
                move.l  a4,FX_FIRST(a3)
                bra.w   .aimed
; A target of several registers: the counted handler of its columns, the
; slots of the map, each column at its register's frame value.
.wide:          move.w  d5,d4
                sub.w   #14,d4
                lsl.w   #3,d4
                lea     ymxr_wcopy(pc),a2
                adda.w  d4,a2
                cmp.b   #20,d5                  ; setEnvelope: two registers
                beq.w   .two
                cmp.b   #17,d5
                bcs.w   .two
                cmp.b   #21,d5
                bhi.w   .two
                lea     ymxr_at_countedthree(pc),a5 ; setVoice and setBuzzer
                bsr.w   .instance
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                WSLOT   TWCT_SEL1,TWCT_PTR1,TWCT_CELLS,FX_FIRST,0
                WSLOT   TWCT_SEL2,TWCT_PTR2,TWCT_CELLS+CELL_SIZE,FX_FIRST2,2
                WSLOT   TWCT_SEL3,TWCT_PTR3,TWCT_CELLS+CELL_SIZE+CELL_SIZE,FX_FIRST3,4
                move.w  #1,TWCT_LEFT(a5)
                move.w  #1,TWCT_RELD(a5)
                move.b  #6,FX_SHAPE(a3)
                bra.w   .aimed
.two:           lea     ymxr_at_envelope(pc),a5 ; setTone, setNoise and
; The counted handler of two columns of the kind a5's table names, which
; copy from frame on a target of a sync enters with its registers set as
; here (sync.S).
ymxr_copy_twoat equ *
                bsr.w   .instance               ; setEnvelope
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                WSLOT   TWC_SEL1,TWC_PTR1,TWC_CELLS,FX_FIRST,0
                WSLOT   TWC_SEL2,TWC_PTR2,TWC_CELLS+CELL_SIZE,FX_FIRST2,2
                move.w  #1,TWC_LEFT(a5)
                move.w  #1,TWC_RELD(a5)
                move.b  #5,FX_SHAPE(a3)
; The vector, at level 7, as ymxr_widestart aims it.
.aimed:
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; an armed start (4.8)
                bne.s   .level
                .endif
                lea     ymxr_timers(pc),a2
                lsl.w   #3,d6                   ; the effect's, eight bytes a
                movea.w (a2,d6.w),a2            ; timer
                move.l  a5,(a2)
.level:         move.w  (sp)+,sr
                lea     YM_SELECT.w,a2          ; the chip's select register,
                move.l  (sp)+,d6                ; which the frame reads it in
                moveq   #1,d0                   ; NZ: several columns' return
                rts
; This effect's handler of the kind of table a5, in the bank of an armed
; start. d4 clobbered.
.instance:      move.w  d6,d4
                add.w   d4,d4
                adda.w  (a5,d4.w),a5
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                beq.s   .bank
                adda.w  FX_ABANK-ARMOFF(a3),a5
.bank:
                .endif
                rts

; A start through a transformation in a tune that copies from frame (3.1.7,
; 2.1.5): init aims each call of ymxr_lstart here, the frame's and the
; arming's, and a source other than copy from frame goes on to it. Copy
; from frame starts on the counted lookup instance of its target's
; columns, at the frame values, through the path of the kinds of
; ymxr_lstart: on a register, its frame value the first row and the loop
; cell; on a target of several registers, a register a column, FV_STRIDE
; bytes apart, which ymxr_wcopy maps in place of ymxr_wide while the path
; runs. The registers, the return and the arming are ymxr_lstart's, a1 and
; a2 kept across the map's write.
ymxr_copylook:  move.w  d3,d0
                lsl.w   #3,d0
                cmp.l   #-8,WS_SOURCES-4(a0,d0.w)   ; copy from frame's tag
                bne.w   ymxr_lstart
                moveq   #0,d3
                move.b  FX_TARGET(a3),d3        ; the target
                lea     ymxr_copykinds(pc),a4
                moveq   #0,d0
                move.b  (a4,d3.w),d0            ; the kind of its handler
                lea     ymxr_fv(pc),a4          ; row 0 of each register
                cmp.w   #14,d3
                bcc.s   .wide
                mulu    #FV_STRIDE,d3
                adda.w  d3,a4                   ; the register's frame value
                move.w  d0,d5
                move.l  a4,d0                   ; its loop cell
                bra.w   ymxr_lkind
.wide:          move.w  d0,d5
                move.l  a4,d0                   ; column 0's loop cell
                swap    d1
                move.w  #FV_STRIDE,d1           ; the stride, the high word
                swap    d1
                movem.l a1-a2,-(sp)
                lea     ymxr_lwmap+2(pc),a1     ; the map, the registers
                lea     ymxr_wcopy-(14*8)(pc),a2
                move.l  a2,d3
                sub.l   a1,d3
                move.w  d3,(a1)
                movem.l (sp)+,a1-a2
                bsr.w   ymxr_lkind
                movem.l a1-a2,-(sp)
                lea     ymxr_lwmap+2(pc),a1     ; and ymxr_wide again
                lea     ymxr_wide-(14*8)(pc),a2
                move.l  a2,d3
                sub.l   a1,d3
                move.w  d3,(a1)
                movem.l (sp)+,a1-a2
                rts

; The counted lookup kind copy from frame runs on each target, 0 to 24,
; the index ymxr_lstart reads: 1 on a register, 6 on the tones and
; setEnvelope, 7 on the noises, 8 on setBuzzer and 9 on the voices.
ymxr_copykinds: dc.b    1,1,1,1,1,1,1,1,1,1,1,1,1,1
                dc.b    6,6,6,9,9,9,6,8,7,7,7
                .even

; Each effect's call of ymxr_lstart at the frame, effect 3 first, as
; ymxr_overs has the heads: its displacement from ymxr_frame.
ymxr_lcalls:    dc.w    ymxr_tick3_lcall-ymxr_frame+2,ymxr_tick2_lcall-ymxr_frame+2
                dc.w    ymxr_tick1_lcall-ymxr_frame+2,ymxr_tick0_lcall-ymxr_frame+2

; Init's work on every tune, in a core with the block copy, after the
; sources are resolved (4.1). On every tune: the frame's first read at
; ymxr_regs and each effect's read at the head of the paths of a row that
; sets its columns written back as the move into d2 that READ2 assembles,
; where a tune before this one wrote a branch of ymxr_copysites over it,
; so the site loop writes them as it writes them alone (ymxr_col reads the
; opcode); the frame values at 0; and each call of ymxr_widestart aimed
; back, each effect's the link kept and the arming's in a core with the
; deferred start, which every link keeps. On a tune that copies from
; frame: copy from frame as source S, after the tables, its first row the
; frame values and its tag -8; bit 2 of d5, a tag among the sources, so
; each effect's start keeps the test of the shapes; and those calls aimed
; here. A set inits one loaded copy for each subtune, so each write is
; made on every init. a6 the workspace; d5 the bits of the shapes. Every
; other register kept.
ymxr_copyinit:  movem.l d0-d4/a1-a4,-(sp)
                lea     ymxr_xback(pc),a1
                moveq   #4-1,d0
.xb:            clr.l   (a1)+
                dbra    d0,.xb
                lea     ymxr_cspare(pc),a2      ; ymxr_cptr follows
                moveq   #14-1,d0
.cp:            move.l  a2,(a1)+
                dbra    d0,.cp
                moveq   #4-1,d0                 ; the owners, the aimed and
.cl:            clr.l   (a1)+                   ; those to test
                dbra    d0,.cl
                lea     ymxr_regs(pc),a1
                move.w  #$1429,(a1)             ; move.b d16(a1),d2
                lea     ymxr_fv(pc),a3
                lea     ymxr_fv0(pc),a4
                moveq   #14-1,d0
.fv:            move.b  (a4)+,(a3)
                lea     FV_STRIDE(a3),a3
                dbra    d0,.fv
                lea     ymxr_widestart(pc),a3
                btst    #7,WS_EFFECTS(a6)       ; copy from frame (3.1.7)
                beq.s   .aim
                lea     ymxr_copywide(pc),a3
                movea.l WS_TUNE(a6),a1          ; source S: the bound tune
                moveq   #0,d0                   ; counts the tables
                move.b  TF_SOURCES(a1),d0
                lsl.w   #3,d0
                lea     WS_SOURCES(a6),a4
                lea     ymxr_fv(pc),a2
                move.l  a2,(a4,d0.w)
                moveq   #-8,d1
                move.l  d1,4(a4,d0.w)
                bset    #2,d5
; The call of each effect the link kept, at its head's distance.
.aim:           lea     ymxr_frame(pc),a4
                lea     ymxr_overs(pc),a2
                moveq   #3,d2
.each:          move.w  (a2)+,d4
                btst    d2,ymxr_k_effect+1(pc)
                beq.s   .next
                lea     (a4,d4.w),a1            ; the effect's head
                move.w  #$1429,SOME_AT(a1)      ; move.b d16(a1),d2
                lea     WIDEN_CALL(a1),a1
                move.l  a3,d1
                sub.l   a1,d1
                move.w  d1,(a1)
.next:          dbra    d2,.each
                .if     YMXR_DEFER
                lea     ymxr_arm_wide+2(pc),a1  ; the arming's (deferred.S)
                move.l  a3,d1
                sub.l   a1,d1
                move.w  d1,(a1)
                .endif
; Each call of ymxr_lstart aimed here or back: each effect's the link
; kept, with the effect's remap, and the arming's two.
                lea     ymxr_lstart(pc),a3
                btst    #7,WS_EFFECTS(a6)
                beq.s   .look
                lea     ymxr_copylook(pc),a3
.look:          lea     ymxr_lcalls(pc),a2
                moveq   #3,d2
.leach:         move.w  (a2)+,d4
                btst    d2,ymxr_k_map+1(pc)
                beq.s   .lnext
                lea     (a4,d4.w),a1
                move.l  a3,d1
                sub.l   a1,d1
                move.w  d1,(a1)
.lnext:         dbra    d2,.leach
                .if     YMXR_DEFER
                lea     ymxr_arm_look1+2(pc),a1
                move.l  a3,d1
                sub.l   a1,d1
                move.w  d1,(a1)
                lea     ymxr_arm_look2+2(pc),a1
                move.l  a3,d1
                sub.l   a1,d1
                move.w  d1,(a1)
                .endif
                movem.l (sp)+,d0-d4/a1-a4
                rts

; The handler of copy from frame through the identity on effect \i's
; timer, which the frame aims the vector at (ymxr_cxon): three writes of a
; register and its frame value, each a move.l whose immediate the frame
; writes, entered at the first, the second or the third for three, two or
; one columns, so it reads no place, no counter and no loop cell. With the
; monitor in each entry marks first.
                .macro  CXH     name,eoi,eoir,colour
                .if     YMXR_PERF
\{name}_e3:     MARK    \colour
                bra.s   \{name}_w3
\{name}_e2:     MARK    \colour
                bra.s   \{name}_w2
\{name}_e1:     MARK    \colour
                bra.s   \{name}_w1
                .endif
\{name}_w3:     move.l  #$08000000,YM_SELECT.w  ; +2: the register, +4: the value
\{name}_w2:     move.l  #$08000000,YM_SELECT.w
\{name}_w1:     move.l  #$08000000,YM_SELECT.w
                .if     YMXR_NEST
                move.w  #$2500,sr
                .endif
                COUNT   PERF_ONEROW,PERF_DROP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .endm
ymxr_cx0:       CXH     ymxr_cx0,$DF,$FFFFFA0F,$070
ymxr_cx1:       CXH     ymxr_cx1,$EF,$FFFFFA11,$007
ymxr_cx2:       CXH     ymxr_cx2,$FE,$FFFFFA0F,$707
ymxr_cx3:       CXH     ymxr_cx3,$DF,$FFFFFA11,$077
CX_SIZE         equ     ymxr_cx1-ymxr_cx0
CX_W3           equ     ymxr_cx0_w3-ymxr_cx0
                .if     YMXR_PERF
CX_E3           equ     ymxr_cx0_e3-ymxr_cx0
CX_ESTEP        equ     ymxr_cx0_e2-ymxr_cx0_e3
                .endif

; The handler of copy from frame of an effect the frame tests, one effect
; a frame that reads no record, effect 3 first, after a frame that read
; the records marked the four (play.S, ymxr_cread), so a start costs no
; more. The frame aims the effect's vector at it where the effect runs
; copy from frame through the identity, on a target other than a target
; of a sync (sync.S, ymxr_sheads), on a counted handler of one to three
; columns, with its timer counting and no start armed, and where no other
; aimed effect's handler of copy from frame writes one of its registers:
; each column's register and frame value written into a slot, the slot's
; value the register's immediate (ymxr_cptr) and the effect its owner
; (ymxr_cown), the record's handler kept in ymxr_xback, and the vector at
; the entry, written last. A frame value's register is the byte after it
; (FVROW). a0 the workspace; d0 to d5 and a1 to a5 clobbered.
ymxr_cxon:      move.b  ymxr_xnew(pc),d0        ; the effects to test
                bne.s   .some
                rts
.some:          lea     ymxr_xnew(pc),a5
                bclr    #7,(a5)                 ; set where the frame read
                beq.s   .test                   ; the records
                rts
.test:          moveq   #3,d4
.find:          btst    d4,d0
                dbne    d4,.find
                bclr    d4,(a5)
                move.b  ymxr_xon-ymxr_xnew(a5),d5   ; the effects aimed
                btst    d4,d5
                bne.w   .out
                btst    d4,WS_EFFECTS(a0)
                beq.w   .out
                lea     ymxr_timers(pc),a3
                move.w  d4,d1
                lsl.w   #3,d1
                adda.w  d1,a3                   ; the effect's timer
                movea.w 2(a3),a2                ; its control register
                move.b  (a2),d1
                and.b   6(a3),d1                ; its select, 0 where stopped
                beq.w   .out
                move.w  d4,d1
                mulu    #FX_SIZE,d1
                lea     WS_FX(a0),a1
                adda.w  d1,a1                   ; the effect's record
                cmp.b   #25,FX_TARGET(a1)       ; a target of a sync
                bcc.w   .out
                .if     YMXR_DEFER
                tst.b   FX_PENDING(a1)          ; a start armed, whose hook the
                bne.w   .out                    ; counted handler's path reaches
                .endif
                tst.b   FX_RUN(a1)              ; the identity
                bne.w   .out
                moveq   #0,d1
                move.b  FX_SHAPE(a1),d1
                subq.w  #4,d1                   ; the counted shapes of one to
                bcs.w   .out                    ; three columns, columns less 1
                cmp.w   #3,d1
                bcc.w   .out
                lea     ymxr_fv(pc),a4
                lea     ymxr_cown(pc),a3
                lea     FX_FIRST(a1),a2         ; a column's place, a long each
                move.w  d1,d2
.chk:           move.l  (a2)+,d0
                sub.l   a4,d0                   ; among the frame values: copy
                cmp.l   #13*FV_STRIDE,d0        ; from frame
                bhi.w   .out
                move.b  1(a4,d0.w),d0           ; the register
                moveq   #0,d3
                move.b  (a3,d0.w),d3            ; its owner
                beq.s   .free
                subq.w  #1,d3
                cmp.w   d4,d3
                beq.s   .free
                btst    d3,d5                   ; aimed: the register's
                bne.w   .out                    ; immediate is that handler's
.free:          dbra    d2,.chk
                bset    d4,ymxr_xon-ymxr_xnew(a5)
                move.w  d4,d2
                mulu    #CX_SIZE,d2
                lea     ymxr_cx0(pc),a4
                adda.w  d2,a4                   ; the effect's handler
                moveq   #2,d3
                sub.w   d1,d3                   ; the slots before the first
                move.w  d3,d2                   ; column's
                lsl.w   #3,d2
                lea     CX_W3(a4,d2.w),a5       ; the first column's slot
                lea     FX_FIRST(a1),a2
                moveq   #1,d5
                add.w   d4,d5                   ; the owner, the effect plus 1
.col:           movea.l (a2)+,a1                ; the column's frame value
                moveq   #0,d2
                move.b  1(a1),d2                ; the register
                move.b  d2,2(a5)
                move.b  (a1),4(a5)              ; the value
                move.b  d5,(a3,d2.w)
                add.w   d2,d2
                add.w   d2,d2
                lea     4(a5),a1
                move.l  a1,d0
                lea     ymxr_cptr(pc),a1
                move.l  d0,(a1,d2.w)            ; the register's immediate
                addq.l  #8,a5
                dbra    d1,.col
                .if     YMXR_PERF
                mulu    #CX_ESTEP,d3
                lea     CX_E3(a4,d3.w),a5
                .else
                lsl.w   #3,d3
                lea     CX_W3(a4,d3.w),a5
                .endif
                lea     ymxr_timers(pc),a3
                lsl.w   #3,d4
                movea.w (a3,d4.w),a3            ; the timer's vector
                lsr.w   #1,d4
                lea     ymxr_xback(pc),a2
                move.l  (a3),(a2,d4.w)          ; the record's handler, kept
                move.l  a5,(a3)
.out:           rts
                ENDPIECE copy_4
