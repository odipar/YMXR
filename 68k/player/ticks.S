; ticks.S - the macros of the tick handlers (SPEC.md 5): a timer claimed
; and released, the raster monitor's mark and count, each shape of
; handler, the hook of a deferred start and the installer that performs
; it. init.S, stop.S, handlers.S and lookup_checks.S expand them.

; One timer claimed, where the tune runs its effect: stopped, aimed at
; its handler, enabled and unmasked.
                .macro  CLAIM   i,ctrl,keep,vector,ier,bit,tick
                btst    #\i,WS_EFFECTS(a6)
                beq.s   .no\~
                andi.b  #\keep,\ctrl.w
                lea     \tick(pc),a3
                move.l  a3,\vector.w
                move.b  #255-(1<<\bit),(\ier+4).w
                bset    #\bit,\ier.w
                bset    #\bit,(\ier+12).w
.no\~:
                .endm

; One timer handed back: stopped, disabled, masked, its pending dropped.
                .macro  RELEASE i,ctrl,keep,ier,bit
                btst    #\i,WS_EFFECTS(a6)
                beq.s   .no\~
                andi.b  #\keep,\ctrl.w
                bclr    #\bit,\ier.w
                bclr    #\bit,(\ier+12).w
                move.b  #255-(1<<\bit),(\ier+4).w
.no\~:
                .endm

; A start pending at the stop has its hook in the running handler, and
; the next init of the same loaded player runs that handler: the hooked
; word is written back and the start dropped, as a row that drops it
; does (deferred.S, ymxr_pend). a1 clobbered.
                .macro  UNHOOK  i
                .if     YMXR_DEFER
                tst.b   \i*FX_SIZE+WS_FX+FX_PENDING(a6)
                beq.s   .no\~
                movea.l \i*FX_SIZE+WS_FX+FX_HOOK(a6),a1
                move.w  \i*FX_SIZE+WS_FX+FX_HOOKW(a6),(a1)
                clr.b   \i*FX_SIZE+WS_FX+FX_PENDING(a6)
.no\~:
                .endif
                .endm

; The monitor's mark: the background kept on the stack and this handler's
; colour written.
                .macro  MARK    colour
                .if     YMXR_PERF
                move.w  PERF_COLOUR.w,-(sp)
                move.w  #\colour,PERF_COLOUR.w
                .endif
                .endm

; The other end of a mark: this tick's cost counted, in turns of ten
; cycles, and the background put back. The path's cycles are its kind's
; figure, the end of interrupt every path writes, and the level the two
; that write a row's value drop. It is after the write the tick
; makes and before the end of interrupt, so a nested tick of a higher
; timer marks and unmarks inside it.
                .macro  COUNT   cycles,drop
                .if     YMXR_PERF
                move.l  a0,-(sp)
                lea     ymxr_perf_acc(pc),a0
                add.w   #(\cycles+\drop+PERF_END+5)/10,(a0)
                movea.l (sp)+,a0
                move.w  (sp)+,PERF_COLOUR.w
                .endif
                .endm

; A tick (SPEC.md 5): the place's row to the target, the place advanced;
; on the marker, the place to row RR, or the timer stopped. No register
; is used: the place is the write's operand, stepped in place. The
; interrupt level drops once the select and the value are written, so a
; higher timer may nest, and the in-service bit is cleared by hand, as
; software end-of-interrupt mode asks. The loop cell, the place of row
; RR, is the immediate of the move at the head of the marker's path; a
; start writes a bra.s to the halt over that move's first word where the
; source plays once (TICK_ONCEW), and the move's first word where it
; repeats (LOOPW).
                .macro  TICK    name,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_row:    move.b  #8,YM_SELECT.w          ; +3: the target register
                .if     YMXR_PCREL
                move.b  \{name}_last(pc),YM_SELECT+2.w ; +8: the place, a
                .else                           ; displacement under YMXR_PCREL
                move.b  ($12345678).l,YM_SELECT+2.w ; +8: the place
                .endif
                bmi.s   \{name}_last
                .if     YMXR_NEST
\{name}_sr:     move.w  #$2500,sr
                .endif
\{name}_step:
                .if     YMXR_PCREL
                addq.w  #1,($12345678).l        ; +2: the place, stepped
                .else
                addq.l  #1,($12345678).l        ; +2: the place, stepped
                .endif
                COUNT   PERF_ON,PERF_DROP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_last:
\{name}_mark:
                .if     YMXR_PCREL
                move.w  #0,($12345678).l        ; +2 the loop cell, +4 the
                .else                           ; place's address
                move.l  #0,($12345678).l        ; +2 the loop cell, +6 the
                .endif                          ; place's address
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_halt:   andi.b  #\keep,\ctrl.w
; The timer may have counted to zero again while this handler ran, which
; latches a tick the stop above does not withdraw: at a rate whose
; period is near the handler's cost the two race, and that tick would
; read a place past the source's last row. The row that stops an effect
; drops it the same way (EFFECT, .stop).
                move.b  #255-(1<<\bit),(\ier+4).w
                COUNT   PERF_STOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .if     YMXR_DEFER
; A start pending on the timer writes one word at the head of the last
; row's path, a bra.s here, so the source ends at its last row and the
; effect's installer performs the start (SPEC.md 4.8).
\{name}_hook:   bra.w   \inst
                .endif
                .endm

; A tick of a source whose one column fills its byte: every bit of the
; byte is the register's, so bit 7 is a value rather than a marker and
; the end of a pass is the place of the last row, an immediate the
; handler compares the place with: Z on the row last. Under YMXR_PCREL=0
; the compare reads the low word of the place's address, which differs
; between any two rows of a pass of fewer than 65,536 rows. The place is
; stepped after that test, so the last row leaves it at R - 1 (SPEC.md 5.1
; step 5), as the handler above does. The register, the place and the
; last row's place are the immediates a start patches, with the loop cell
; and the loop word as the handler above has them, so this handler uses
; no register either. A source whose column leaves bit 7 runs the
; handler above, which reads the marker and costs 20 cycles less
; (doc/performance.md).
;
; The two branch targets are named rather than local: the labels the
; patching reads are between a branch and its target, and a label ends
; the run a local one belongs to.
                .macro  TICKC   name,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_row:    move.b  #8,YM_SELECT.w          ; +3: the target register
                .if     YMXR_PCREL
                move.b  \{name}_last(pc),YM_SELECT+2.w ; +8: the place, a
                .else                           ; displacement under YMXR_PCREL
                move.b  ($12345678).l,YM_SELECT+2.w
                .endif
\{name}_cnt:    cmpi.w  #0,($12345678).l        ; +2 the last row's place, +4
                beq.s   \{name}_last            ; the place's address
                .if     YMXR_NEST
                move.w  #$2500,sr
                .endif
\{name}_step:
                .if     YMXR_PCREL
                addq.w  #1,($12345678).l        ; +2: the place, stepped
                .else
                addq.l  #1,($12345678).l
                .endif
                COUNT   PERF_ON,PERF_DROP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
; The last row leaves the place where it is, so a select after this tick
; reads the last row again and stops the timer again.
\{name}_last:
\{name}_mark:
                .if     YMXR_PCREL
                move.w  #0,($12345678).l        ; +2 the loop cell, +4 the
                .else                           ; place's address
                move.l  #0,($12345678).l        ; +2 the loop cell, +6 the
                .endif                          ; place's address
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_halt:   andi.b  #\keep,\ctrl.w
                move.b  #255-(1<<\bit),(\ier+4).w
                COUNT   PERF_STOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .if     YMXR_DEFER
\{name}_hook:   bra.w   \inst                   ; a pending start, as above
                .endif
                .endm

; A tick of a target that writes several registers (SPEC.md 2.1): a
; select and a value a column, a place a column stepped where the row is
; not the last, and the column the target names for the marker written
; last, so the move that writes it leaves the marker in N and this
; handler tests it as the general one does. A start puts the columns in
; that order, and the places, the registers, the loop cells and the loop
; word are the immediates it patches, so this handler uses no register
; either. A tune whose sources are one column enters none of this.
; The steps of a sync (YMXS, SPEC.md 3.9.2): a pad between the 0 that sets
; a voice's period to 0 and the write that sets it other than 0, so the
; instructions between them are a whole number of steps of the tone
; generator, 32 cycles each on an ST. HOLD_T's pads are shifts of d0,
; which the head saved and restores after them, named by path: 1 and 3
; odd, 2 and 4 even, 1 and 2 where the fine register's 0 is the second and
; 3 and 4 where the coarse one's is, then \fine; HOLD_L the count of lsl.l
; #8, 24 cycles each, and HOLD_A and HOLD_B the k of a lsl.w #k of 6 + 2k
; cycles where k is above 0. HOLD_F's pads are nops: \steps 1 odd and 2
; even. \fine is 1 where the fine is 0 and the coarse write ends the
; steps. HOLD_T times a tick's sync (ticks.S) and HOLD_F a frame's
; (sync.S), and HOLD_FZ is the nops of a frame's sync from its coarse 0 to
; its fine 0, one step. HOLD_CE and HOLD_FE are the nops of the even heads
; the frame writes, of copy from frame and of a source's rows, whose odd
; heads keep the period at 0 for one step with none and HOLD_R's 56 cycles
; (sync.S, ymxr_hro to ymxr_hce). The rig reads each sync's steps under
; Hatari, and the step from each coarse 0 to its fine 0 (68k/test/emu/
; test_ymxr.py).
HOLD_CE         equ     8
HOLD_FE         equ     8
; The room a handler of a target of a sync has for the head the frame
; writes (TICKW, sync.S): 8 bytes for the move that aims the slot at the
; general head after a tick of the other parity, and the longest head,
; copy from frame's even one where the lookup is in, and a source's even
; one otherwise.
                .if     YMXR_MAP
HREG            equ     2*HOLD_CE+52
                .else
HREG            equ     2*HOLD_FE+40
                .endif
HOLD_L10        equ     1
HOLD_A10        equ     0
HOLD_B10        equ     0
HOLD_L20        equ     1
HOLD_A20        equ     1
HOLD_B20        equ     0
HOLD_L11        equ     1
HOLD_A11        equ     6
HOLD_B11        equ     0
HOLD_L21        equ     0
HOLD_A21        equ     0
HOLD_B21        equ     0
HOLD_L30        equ     1
HOLD_A30        equ     1
HOLD_B30        equ     0
HOLD_L31        equ     1
HOLD_A31        equ     7
HOLD_B31        equ     1
HOLD_L40        equ     0
HOLD_A40        equ     0
HOLD_B40        equ     0
HOLD_L41        equ     0
HOLD_A41        equ     7
HOLD_B41        equ     0
HOLD_F10        equ     11
HOLD_F11        equ     5
HOLD_F20        equ     3
HOLD_F21        equ     13
HOLD_FZ         equ     3
                .macro  HOLD_R
                move.l  d0,-(sp)
                lsl.l   #8,d0
                move.l  (sp)+,d0
                nop
                nop
                .endm
                .macro  HOLD_T  l,a,b
                .if     \l>0
                .rept   \l
                lsl.l   #8,d0
                .endr
                .endif
                .if     \a>0
                lsl.w   #\a,d0
                .endif
                .if     \b>0
                lsl.w   #\b,d0
                .endif
                .endm
                .macro  HOLD_F  steps,fine
                .if     \steps=1
                .if     \fine=0
                .rept   HOLD_F10
                nop
                .endr
                .else
                .rept   HOLD_F11
                nop
                .endr
                .endif
                .else
                .if     \fine=0
                .rept   HOLD_F20
                nop
                .endr
                .else
                .rept   HOLD_F21
                nop
                .endr
                .endif
                .endif
                .endm

; Whether the row's fine at the place of \name's first column is 0, in Z;
; d0 clobbered (a0 kept where a row is read through an absolute address).
                .macro  FINE0   name
                .if     YMXR_PCREL
                move.w  \{name}_ptr1+2(pc),d0
                move.b  \{name}_ptr1+2(pc,d0.w),d0
                .else
                move.l  a0,-(sp)
                movea.l \{name}_ptr1+2(pc),a0
                tst.b   (a0)
                movea.l (sp)+,a0
                .endif
                .endm

; FINE0 from before the handler, beyond the reach of a byte's
; displacement: the place and its distance from the read, through the
; program counter.
                .macro  FINE0N  name
                .if     YMXR_PCREL
                move.w  \{name}_ptr1+2(pc),d0
                add.w   #\{name}_ptr1+2-.fn\~-2,d0
.fn\~:          move.b  .fn\~+2(pc,d0.w),d0
                .else
                FINE0   \name
                .endif
                .endm

; FINE0 from past the handler, beyond the reach of a byte's displacement.
                .macro  FINE0F  name
                move.l  a0,-(sp)
                .if     YMXR_PCREL
                lea     \{name}_ptr1+2(pc),a0
                adda.w  (a0),a0
                .else
                movea.l \{name}_ptr1+2(pc),a0
                .endif
                tst.b   (a0)
                movea.l (sp)+,a0
                .endm

                .macro  TICKW   name,columns,counted,eoi,eoir,ctrl,keep,ier,bit,colour,inst,sync
; With \sync, the handler of a target of a sync (SPEC.md 5.1): the head
; below, before the first byte, which the first select's slot branches
; to; the slot keeps the register at +3, where a start writes it and the
; remap and the recut read it. At the timer's level, which masks every
; other timer, the head writes 0 to the voice's two registers, the coarse
; one first unless the chip's value of the fine one is 0, and goes back to
; the handler's write with the fine register selected, so the period is 0
; from the second 0 to the handler's write of the row's fine, or of its
; coarse where the fine is 0 (SPEC.md 1.12.3). HOLD_T times those steps.
; The registers are immediates the start writes (sync.S, ymxr_syncwide).
; Each path's parity word is the first word of its test of the row's
; fine, which goes on to the odd steps, or a bra.s to the even ones: the
; frame writes it from the voice's bit of ymxr_seven, or of the row before
; for one tick (sync.S, ymxr_sheads). This general head reads the chip and
; the row at every tick; the frame aims the slot instead at a head it has
; written, of a source's rows or of copy from frame, odd or even, where
; those reads would come out one way until the next frame.
                .if     \sync
\{name}_head:   move.l  d0,-(sp)
\{name}_hf1:    move.b  #0,YM_SELECT.w          ; +3: the fine register
                move.b  YM_SELECT.w,d0          ; its value
                bne.s   \{name}_hmain
; The fine register's value 0: its 0 first, then the coarse register's,
; and the fine selected again after the steps.
                move.b  #0,YM_SELECT+2.w
\{name}_hc1:    move.b  #0,YM_SELECT.w          ; +3: the coarse register
\{name}_fpar:   FINE0N  \name                   ; the parity word: odd
                beq.w   \{name}_sodd0
\{name}_sodd:   move.b  #0,YM_SELECT+2.w
                HOLD_T  HOLD_L30,HOLD_A30,HOLD_B30
                move.l  (sp)+,d0
\{name}_sf1:    move.b  #0,YM_SELECT.w          ; +3: the fine register
                bra.w   \{name}_ptr1
\{name}_sev:    FINE0N  \name
                beq.w   \{name}_seven0
\{name}_seven:  move.b  #0,YM_SELECT+2.w
                HOLD_T  HOLD_L40,HOLD_A40,HOLD_B40
                move.l  (sp)+,d0
\{name}_sf2:    move.b  #0,YM_SELECT.w          ; +3: the fine register
                bra.w   \{name}_ptr1
; The fine register's value other than 0: whether the row's fine is 0,
; into d0, then the coarse register's 0, one step before the fine's, which
; stays selected for the handler's write.
\{name}_hmain:
                .if     YMXR_PCREL
                FINE0N  \name
                .else
                move.l  a0,-(sp)
                movea.l \{name}_ptr1+2(pc),a0
                move.b  (a0),d0
                movea.l (sp)+,a0
                .endif
\{name}_hc2:    move.l  #$01000000,YM_SELECT.w  ; +2: the coarse register
                nop
\{name}_hf2:    move.l  #$00000000,(YM_SELECT).l ; +2: the fine register
\{name}_mpar:   tst.b   d0                      ; the parity word: odd
                beq.w   \{name}_odd0
\{name}_odd:    HOLD_T  HOLD_L10,HOLD_A10,HOLD_B10
                move.l  (sp)+,d0
                bra.w   \{name}_ptr1
\{name}_ev:     tst.b   d0
                beq.w   \{name}_even0
                bra.w   \{name}_even
; The head the frame writes, of a source's rows or of copy from frame, odd
; or even, which the slot branches to once the frame has written it
; (sync.S, ymxr_sheads): HREG bytes, room for the longest of the heads it
; copies in (sync.S, ymxr_hro to ymxr_hce).
\{name}_hreg:
                .rept   HREG
                dc.b    0
                .endr
; The slot's way to the general head, which a bra.s does not reach past
; that room.
\{name}_gen:    bra.w   \{name}_head
                .endif
\name:
                MARK    \colour
                .if     \sync
\{name}_sel1:   bra.s   \{name}_gen
                dc.b    0,8                     ; +3: the first register
                dc.w    0
                .else
\{name}_sel1:   move.b  #8,YM_SELECT.w          ; +3: the first register
                .endif
                .if     YMXR_PCREL
\{name}_ptr1:   move.b  \{name}_last(pc),YM_SELECT+2.w ; +2: the first place
\{name}_sel2:   move.b  #8,YM_SELECT.w
\{name}_ptr2:   move.b  \{name}_last(pc),YM_SELECT+2.w
                .if     \columns=3
\{name}_sel3:   move.b  #8,YM_SELECT.w
\{name}_ptr3:   move.b  \{name}_last(pc),YM_SELECT+2.w
                .endif
                .else
\{name}_ptr1:   move.b  ($12345678).l,YM_SELECT+2.w ; +2: the first place
\{name}_sel2:   move.b  #8,YM_SELECT.w
\{name}_ptr2:   move.b  ($12345678).l,YM_SELECT+2.w
                .if     \columns=3
\{name}_sel3:   move.b  #8,YM_SELECT.w
\{name}_ptr3:   move.b  ($12345678).l,YM_SELECT+2.w
                .endif
                .endif
                .if     \counted
\{name}_cnt:    cmpi.w  #0,($12345678).l        ; +2 the last row's place, +4
                beq.s   \{name}_last            ; the first place's address:
                .else                           ; a source of whole bytes
                bmi.s   \{name}_last            ; ends on its last row, a
                .endif                          ; marked one on the marker
                .if     YMXR_NEST
                move.w  #$2500,sr
                .endif
                .if     YMXR_PCREL
\{name}_step1:  addq.w  #1,($12345678).l        ; a place a column, stepped
\{name}_step2:  addq.w  #1,($12345678).l
                .if     \columns=3
\{name}_step3:  addq.w  #1,($12345678).l
                .endif
                .else
\{name}_step1:  addq.l  #1,($12345678).l        ; a place a column, stepped
\{name}_step2:  addq.l  #1,($12345678).l
                .if     \columns=3
\{name}_step3:  addq.l  #1,($12345678).l
                .endif
                .endif
                COUNT   PERF_ON,PERF_DROP
                .if     YMXR_AEOI=0
\{name}_eoi:    move.b  #\eoi,\eoir.w
                .endif
                rte
; The head of the path that ends a pass, as the general handler has it: a
; column's loop cell, the move's immediate, to its place, the loop word
; where the source repeats and a bra.s to the halt where it plays once.
\{name}_last:
                .if     YMXR_PCREL
\{name}_loop1:  move.w  #0,($12345678).l        ; +2 the cell, +4 the place's
\{name}_loop2:  move.w  #0,($12345678).l        ; address
                .if     \columns=3
\{name}_loop3:  move.w  #0,($12345678).l
                .endif
                .else
\{name}_loop1:  move.l  #0,($12345678).l        ; +2 the cell, +6 the place's
\{name}_loop2:  move.l  #0,($12345678).l        ; address
                .if     \columns=3
\{name}_loop3:  move.l  #0,($12345678).l
                .endif
                .endif
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_halt:   andi.b  #\keep,\ctrl.w
                move.b  #255-(1<<\bit),(\ier+4).w
                COUNT   PERF_STOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .if     YMXR_DEFER
\{name}_hook:   bra.w   \inst                   ; a pending start, as above
                .endif
                .if     \sync
; The ends of the head that the head reaches through a bra.w: HOLD_T's
; steps, and where the row's fine is 0 those of the coarse write, after
; the second 0, which the head writes where it is the fine register's and
; the end where it is the coarse one's. Where the fine register's 0 is the
; second it stays selected for the handler's write; where the coarse
; one's is, the steps select the fine register.
\{name}_tails:
\{name}_even:   HOLD_T  HOLD_L20,HOLD_A20,HOLD_B20
                move.l  (sp)+,d0
                bra.w   \{name}_ptr1
\{name}_odd0:   HOLD_T  HOLD_L11,HOLD_A11,HOLD_B11
                move.l  (sp)+,d0
                bra.w   \{name}_ptr1
\{name}_even0:  HOLD_T  HOLD_L21,HOLD_A21,HOLD_B21
                move.l  (sp)+,d0
                bra.w   \{name}_ptr1
\{name}_sodd0:  move.b  #0,YM_SELECT+2.w
                HOLD_T  HOLD_L31,HOLD_A31,HOLD_B31
                move.l  (sp)+,d0
\{name}_sf3:    move.b  #0,YM_SELECT.w          ; +3: the fine register
                bra.w   \{name}_ptr1
\{name}_seven0: move.b  #0,YM_SELECT+2.w
                HOLD_T  HOLD_L41,HOLD_A41,HOLD_B41
                move.l  (sp)+,d0
\{name}_sf4:    move.b  #0,YM_SELECT.w          ; +3: the fine register
                bra.w   \{name}_ptr1
                .if     YMXR_MAP&YMXR_PERF
; The end of the heads of copy from frame the frame writes in a build with
; the monitor, whose count reads its accumulator through the program
; counter, so a copied head branches here (sync.S, ymxr_hco).
\{name}_htail:  COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .endif
                .endif
                .endm

; A square's tick: a source of two rows that repeats to row 0 is two
; values and no more, so this handler encodes them as its
; immediate and moves between them by the difference of the two. It
; steps no place, tests no marker and reads no row. The place SPEC.md
; 1.9 defines for a square is which of the two rows the immediate is at,
; and bit 7 tells them apart, the last row of a source being its marker
; (3.2). A source of any other shape runs the handler above.
                .macro  SQUARE  name,eoi,eoir,colour,inst
\name:
                MARK    \colour
\{name}_sel:    move.l  #$08000F00,YM_SELECT.w  ; +2: the target register,
                                                ; +4: the row it is at
                .if     YMXR_NEST
                move.w  #$2500,sr
                .endif
\{name}_tog:    eori.b  #$0F,($12345678).l      ; +3 the two rows' difference,
\{name}_after:  COUNT   PERF_SQ,PERF_DROP       ; +4 the row's address
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .if     YMXR_DEFER
; A start pending on the timer writes a bra.s here over the eori's first
; word: the row written last ends the source where it is the marker, row
; 1, and the installer performs the start; at row 0 the eori below moves
; to row 1 as the handler's does (SPEC.md 4.8).
\{name}_hook:   btst    #7,\{name}_sel+4(pc)
                bne.w   \inst
\{name}_htog:   eori.b  #$0F,($12345678).l      ; +3 and +4, as the eori above
                bra.s   \{name}_after
                .endif
                .endm

; A source of one row repeating writes that row on every tick and moves
; no place (SPEC.md 1.9, 3.2), so this handler encodes the row as its
; immediate and writes it. It steps no place, tests no marker, reads no
; row and reads no loop cell. The place SPEC.md 1.9 defines for such a source
; is row 0, which it is at from the start, so bit 5 of the control
; column places no requirement on it. The target belongs to the effect, as in
; the two handlers above: a start writes the register into the
; select, and the source's shape picks the handler where the register
; does not.
                .macro  ONEROW  name,eoi,eoir,colour,inst
\name:
                MARK    \colour
\{name}_sel:    move.l  #$08000F00,YM_SELECT.w  ; +2: the target register,
                                                ; +4: the source's one row
\{name}_end:
                .if     YMXR_NEST
                move.w  #$2500,sr
                .endif
                COUNT   PERF_ONEROW,PERF_DROP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .if     YMXR_DEFER
; A start pending on the timer writes a bra.s here over the first word
; after the row's write: the one row is the last (SPEC.md 4.8).
\{name}_hook:   bra.w   \inst
                .endif
                .endm

; The loop word of the handler a5, whose first loop cell is at \cell: the
; loop's first move where Z is clear, the move or test before this macro
; having read that cell, and \once, a bra.s to the halt, where Z is set
; and the source plays once. Where the source repeats, one branch runs.
                .macro  LOOPHD  cell,once
                bne.s   .l\~
                move.w  #\once,\cell-2(a5)
                bra.s   .d\~
.l\~:           move.w  #LOOPW,\cell-2(a5)
.d\~:
                .endm

; The word a hook writes: a bra.s to the stub, or a nop where the stub
; follows the word, as in a one-row handler of the lean tick, whose word
; is its rte; a bra.s of 0 would read the next word as a displacement.
                .macro  HOOKW   p
                .if     (\{p}_HOOK-\{p}_LAST-2)=0
\{p}_HOOKW      equ     $4E71
                .else
\{p}_HOOKW      equ     $6000|(\{p}_HOOK-\{p}_LAST-2)
                .endif
                .endm

; The handlers of a target that writes several registers, the same way:
; a register and a place a column, a place a column stepped, the loop
; cells written into the places, and the head of the path that ends a
; pass with the word that halts there. The two macro instances lay out
; two shapes, so a shape's offsets are its four handlers'.
                .macro  TWAT    p,name
\{p}_SEL1       equ     \{name}_sel1-\name+3
\{p}_PTR1       equ     \{name}_ptr1-\name+2
\{p}_SEL2       equ     \{name}_sel2-\name+3
\{p}_PTR2       equ     \{name}_ptr2-\name+2
\{p}_STEP1      equ     \{name}_step1-\name+2
\{p}_STEP2      equ     \{name}_step2-\name+2
\{p}_LOOP1W     equ     \{name}_loop1-\name+2+CELL_SIZE
\{p}_LOOP2W     equ     \{name}_loop2-\name+2+CELL_SIZE
\{p}_CELLS      equ     \{name}_loop1-\name+2
\{p}_LAST       equ     \{name}_last-\name
\{p}_ONCEW      equ     $6000|(\{name}_halt-\{name}_last-2)
                .if     YMXR_DEFER
\{p}_HOOK       equ     \{name}_hook-\name
                .endif
                .endm

; An effect's installer, which the hook of 4.8 branches to at the tick
; that reads the last row of the running source. Every operand is
; patched at the frame that arms the start: the hooked word and its
; address, the skip over the stop where the start leaves timerReset
; clear, the count, the armed handler and the select. The tick runs at
; the timer's level, so no tick of the other timer of a shared control
; register lands between the read and the write of it.
                .macro  INSTALL name,ctrl,data,keep,ier,bit,shift,shared,vector,eoi,eoir
\name:
\{name}_back:   move.w  #$4E71,($12345678).l    ; +2 the word, +4 its address
\{name}_skip:   bra.s   \{name}_run             ; a nop where the start sets
                andi.b  #\keep,\ctrl.w          ; timerReset: the timer stopped
                move.b  #255-(1<<\bit),(\ier+4).w ; and its latched tick dropped
\{name}_run:
\{name}_cnt:    move.b  #0,\data.w              ; +3: the count
\{name}_vec:    move.l  #$12345678,\vector.w    ; +2: the armed handler
                .if     \shared
                move.w  d0,-(sp)                ; the other timer's bits kept, at
                ori.w   #$0700,sr               ; level 7 as the frame keeps them:
                move.b  \ctrl.w,d0              ; a tick of the other timer nests
                and.b   #\keep,d0               ; otherwise between read and write
\{name}_sel:    or.b    #0,d0                   ; +3: the select, shifted
                move.b  d0,\ctrl.w
                move.w  (sp)+,d0                ; rte restores the level
                .else
\{name}_sel:    move.b  #0,\ctrl.w              ; +3: the select
                .endif
                COUNT   PERF_SWITCH,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .endm
