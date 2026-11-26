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
; software end-of-interrupt mode asks.
                .macro  TICK    name,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_row:    move.b  #8,YM_SELECT.w          ; +3: the target register
                .if     YMXR_PCREL
                move.b  \{name}_cell(pc),YM_SELECT+2.w ; +8: the place, a
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
                tst.w   ($12345678).l           ; +2: the loop cell
                beq.s   \{name}_halt
                move.w  ($12345678).l,($12345678).l ; +10 the cell, +14 the
                .else                           ; place
                tst.l   ($12345678).l           ; +2: the loop cell
                beq.s   \{name}_halt
                move.l  ($12345678).l,($12345678).l ; +10 the cell, +14 the place
                .endif
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
\{name}_cell:
                .if     YMXR_PCREL
                dc.w    0                       ; the loop cell
                .else
                dc.l    0                       ; the loop cell
                .endif
                .if     YMXR_DEFER
; A start pending on the timer writes one word at the head of the last
; row's path, a bra.s here, so the source ends at its last row and the
; effect's installer performs the start (SPEC.md 4.8).
\{name}_hook:   bra.w   \inst
                .endif
                .endm

; A tick of a source whose one column fills its byte: every bit of the
; byte is the register's, so bit 7 is a value rather than a marker and
; the end of the source is in a counter beside the place. A word
; counts the rows left; the subq that steps it leaves Z on the row last,
; and the loop reloads it with the rows of a pass through the loop. The
; place is stepped after that test, so the last row leaves it at R - 1
; (SPEC.md 5.1 step 5), as the handler above does. The register, the
; place, the counter and that reload are the immediates a start patches,
; so this handler uses no register either. A source whose column leaves
; bit 7 runs the handler above, which reads the marker and costs 20
; cycles less (doc/performance.md).
;
; The two branch targets are named rather than local: the labels the
; patching reads are between a branch and its target, and a label ends
; the run a local one belongs to.
                .macro  TICKC   name,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_row:    move.b  #8,YM_SELECT.w          ; +3: the target register
                .if     YMXR_PCREL
                move.b  \{name}_cell(pc),YM_SELECT+2.w ; +8: the place, a
                .else                           ; displacement under YMXR_PCREL
                move.b  ($12345678).l,YM_SELECT+2.w
                .endif
\{name}_cnt:    subq.w  #1,($12345678).l        ; +2: the rows left
                beq.s   \{name}_last
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
\{name}_last:
\{name}_mark:
                .if     YMXR_PCREL
                tst.w   ($12345678).l           ; +2: the loop cell
                beq.s   \{name}_halt
                move.w  ($12345678).l,($12345678).l ; +10 the cell, +14 the
                .else                           ; place
                tst.l   ($12345678).l           ; +2: the loop cell
                beq.s   \{name}_halt
                move.l  ($12345678).l,($12345678).l ; +10 the cell, +14 the place
                .endif
\{name}_reld:   move.w  #1,($12345678).l        ; +2: the rows of a pass, +4:
                COUNT   PERF_LOOP,0             ; the counter
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_halt:
\{name}_park:   addq.w  #1,($12345678).l        ; keep the last row for a
                                                ; select after this tick
                andi.b  #\keep,\ctrl.w
                move.b  #255-(1<<\bit),(\ier+4).w
                COUNT   PERF_STOP+20,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_cell:
                .if     YMXR_PCREL
                dc.w    0                       ; the loop cell
                .else
                dc.l    0                       ; the loop cell
                .endif
\{name}_left:   dc.w    0                       ; the rows left
                .if     YMXR_DEFER
\{name}_hook:   bra.w   \inst                   ; a pending start, as above
                .endif
                .endm

; A tick of a target that writes several registers (SPEC.md 2.1): a
; select and a value a column, a place a column stepped where the row is
; not the last, and the column the target names for the marker written
; last, so the move that writes it leaves the marker in N and this
; handler tests it as the general one does. A start puts the columns in
; that order, and the places, the registers and the loop cells are the
; immediates it patches, so this handler uses no register either. A tune
; whose sources are one column enters none of this.
; The steps of a sync (YMXS, SPEC.md 3.9.2): nops between the 0 that sets
; a voice's period to 0 and the write that sets it other than 0, so the
; instructions between them are a whole number of steps of the tone
; generator, 32 cycles each on an ST: HOLD_T's \path 1 and 3 odd, 2 and 4
; even, 1 and 2 where the fine register's 0 is the second and 3 and 4
; where the coarse one's is; HOLD_F's \steps 1 odd and 2 even; \fine 1 where
; the fine is 0 and the coarse write ends the steps. HOLD_T
; times a tick's sync (ticks.S) and HOLD_F a frame's (sync.S); HOLD_CE
; and HOLD_FE are the nops of the even heads the frame writes, of copy
; from frame and of a source's rows, whose odd heads keep the period at 0
; for one step with none (sync.S, ymxr_hro to ymxr_hce). The rig reads
; each sync's steps under Hatari (68k/test/emu/test_ymxr.py).
HOLD_CE         equ     8
HOLD_FE         equ     8
; The room a handler of a target of a sync has for the head the frame
; writes (TICKW, sync.S): the longest head, copy from frame's even one
; where the lookup is in, and a source's even one otherwise.
                .if     YMXR_MAP
HREG            equ     2*HOLD_CE+42
                .else
HREG            equ     2*HOLD_FE+24
                .endif
HOLD_TF10       equ     0
HOLD_TF11       equ     5
HOLD_TF20       equ     8
HOLD_TF21       equ     13
HOLD_TS10       equ     10
HOLD_TS11       equ     15
HOLD_TS20       equ     2
HOLD_TS21       equ     7
HOLD_F10        equ     3
HOLD_F11        equ     14
HOLD_F20        equ     11
HOLD_F21        equ     6
                .macro  HOLD_T  path,fine
                .if     (\path=1)&(\fine=0)
                .if     HOLD_TF10>0
                .rept   HOLD_TF10
                nop
                .endr
                .endif
                .endif
                .if     (\path=1)&(\fine=1)
                .if     HOLD_TF11>0
                .rept   HOLD_TF11
                nop
                .endr
                .endif
                .endif
                .if     (\path=2)&(\fine=0)
                .if     HOLD_TF20>0
                .rept   HOLD_TF20
                nop
                .endr
                .endif
                .endif
                .if     (\path=2)&(\fine=1)
                .if     HOLD_TF21>0
                .rept   HOLD_TF21
                nop
                .endr
                .endif
                .endif
                .if     (\path=3)&(\fine=0)
                .if     HOLD_TS10>0
                .rept   HOLD_TS10
                nop
                .endr
                .endif
                .endif
                .if     (\path=3)&(\fine=1)
                .if     HOLD_TS11>0
                .rept   HOLD_TS11
                nop
                .endr
                .endif
                .endif
                .if     (\path=4)&(\fine=0)
                .if     HOLD_TS20>0
                .rept   HOLD_TS20
                nop
                .endr
                .endif
                .endif
                .if     (\path=4)&(\fine=1)
                .if     HOLD_TS21>0
                .rept   HOLD_TS21
                nop
                .endr
                .endif
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
; other timer, the head writes 0 to the two registers the selects name,
; the coarse one first unless the chip's value of the fine one is 0, and
; goes back to the handler's write with the fine register selected, so the
; period is 0 from the second 0 to the handler's write of the row's fine,
; or of its coarse where the fine is 0 (SPEC.md 1.12.3). HOLD_T times
; those steps: even where the voice's bit of ymxr_seven is 1 (sync.S), odd
; otherwise. Where the fine register's value is 0 the head goes on past
; the handler, after its last rte. This general head reads the chip, the
; row and ymxr_seven at every tick; the frame aims the slot instead at a
; head it has written, of a source's rows or of copy from frame, odd or
; even, where those reads would come out one way until the next frame
; (sync.S, ymxr_sheads).
                .if     \sync
\{name}_head:   move.l  d0,-(sp)
                move.b  \{name}_sel1+3(pc),YM_SELECT.w
                move.b  YM_SELECT.w,d0          ; the fine register's value
                beq.w   \{name}_ffirst
                move.b  \{name}_sel2+3(pc),YM_SELECT.w
                move.b  #0,YM_SELECT+2.w        ; the coarse 0 first
                move.b  \{name}_sel1+3(pc),YM_SELECT.w
\{name}_fine:   moveq   #0,d0
                move.b  \{name}_sel1+3(pc),d0   ; the voice's bit: 0, 2 or 4
                btst    d0,ymxr_seven(pc)
                bne.s   \{name}_ev
                FINE0   \name
                beq.w   \{name}_odd0
                bra.w   \{name}_odd
\{name}_ev:     FINE0   \name
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
\{name}_ptr1:   move.b  \{name}_cells(pc),YM_SELECT+2.w ; +2: the first place
\{name}_sel2:   move.b  #8,YM_SELECT.w
\{name}_ptr2:   move.b  \{name}_cells(pc),YM_SELECT+2.w
                .if     \columns=3
\{name}_sel3:   move.b  #8,YM_SELECT.w
\{name}_ptr3:   move.b  \{name}_cells(pc),YM_SELECT+2.w
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
\{name}_cnt:    subq.w  #1,($12345678).l        ; +2: the rows left, which a
                beq.s   \{name}_last            ; source of whole bytes ends on
                .else
                bmi.s   \{name}_last            ; the marker of the row last
                .endif                          ; written
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
\{name}_last:
                .if     YMXR_PCREL
\{name}_loopt:  tst.w   ($12345678).l           ; the loop cell of column 0
                beq.s   \{name}_halt
\{name}_loop1:  move.w  ($12345678).l,($12345678).l ; the cell, the place
\{name}_loop2:  move.w  ($12345678).l,($12345678).l
                .if     \columns=3
\{name}_loop3:  move.w  ($12345678).l,($12345678).l
                .endif
                .else
\{name}_loopt:  tst.l   ($12345678).l           ; the loop cell of column 0
                beq.s   \{name}_halt
\{name}_loop1:  move.l  ($12345678).l,($12345678).l ; the cell, the place
\{name}_loop2:  move.l  ($12345678).l,($12345678).l
                .if     \columns=3
\{name}_loop3:  move.l  ($12345678).l,($12345678).l
                .endif
                .endif
                .if     \counted
\{name}_reld:   move.w  #1,($12345678).l        ; +2: the rows of a pass, +4:
                .endif                          ; the counter
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_halt:
                .if     \counted
\{name}_park:   addq.w  #1,($12345678).l        ; keep the last row for a
                .endif                          ; select after this tick
                andi.b  #\keep,\ctrl.w
                move.b  #255-(1<<\bit),(\ier+4).w
                .if     \counted
                COUNT   PERF_STOP+20,0
                .else
                COUNT   PERF_STOP,0
                .endif
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_cells:
                .if     YMXR_PCREL
                dc.w    0,0,0                   ; the loop cell a column
                .else
                dc.l    0,0,0                   ; the loop cell a column
                .endif
                .if     \counted
\{name}_left:   dc.w    0                       ; the rows left
                .endif
                .if     YMXR_DEFER
\{name}_hook:   bra.w   \inst                   ; a pending start, as above
                .endif
                .if     \sync
; The ends of the head: the second 0, then HOLD_T's steps, odd or even,
; and where the row's fine is 0 those of the coarse write. Where the fine
; register's 0 is the second it stays selected for the handler's write;
; where the coarse one's is, the steps select the fine register.
\{name}_odd:    move.l  (sp)+,d0
                move.b  #0,YM_SELECT+2.w
                HOLD_T  1,0
                bra.w   \{name}_ptr1
\{name}_even:   move.l  (sp)+,d0
                move.b  #0,YM_SELECT+2.w
                HOLD_T  2,0
                bra.w   \{name}_ptr1
\{name}_odd0:   move.l  (sp)+,d0
                move.b  #0,YM_SELECT+2.w
                HOLD_T  1,1
                bra.w   \{name}_ptr1
\{name}_even0:  move.l  (sp)+,d0
                move.b  #0,YM_SELECT+2.w
                HOLD_T  2,1
                bra.w   \{name}_ptr1
\{name}_ffirst: move.b  #0,YM_SELECT+2.w        ; a fine of 0, selected: its 0 first
                move.b  \{name}_sel2+3(pc),YM_SELECT.w
                moveq   #0,d0
                move.b  \{name}_sel1+3(pc),d0
                btst    d0,ymxr_seven(pc)
                bne.s   \{name}_sev
                FINE0F  \name
                beq.w   \{name}_sodd0
                bra.w   \{name}_sodd
\{name}_sev:    FINE0F  \name
                beq.w   \{name}_seven0
                bra.w   \{name}_seven
\{name}_sodd:   move.l  (sp)+,d0
                move.b  #0,YM_SELECT+2.w
                HOLD_T  3,0
                move.b  \{name}_sel1+3(pc),YM_SELECT.w
                bra.w   \{name}_ptr1
\{name}_seven:  move.l  (sp)+,d0
                move.b  #0,YM_SELECT+2.w
                HOLD_T  4,0
                move.b  \{name}_sel1+3(pc),YM_SELECT.w
                bra.w   \{name}_ptr1
\{name}_sodd0:  move.l  (sp)+,d0
                move.b  #0,YM_SELECT+2.w
                HOLD_T  3,1
                move.b  \{name}_sel1+3(pc),YM_SELECT.w
                bra.w   \{name}_ptr1
\{name}_seven0: move.l  (sp)+,d0
                move.b  #0,YM_SELECT+2.w
                HOLD_T  4,1
                move.b  \{name}_sel1+3(pc),YM_SELECT.w
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
; cell read and the cells written into the places. The two macro
; instances lay out two shapes, so a shape's offsets are its four
; handlers'.
                .macro  TWAT    p,name
\{p}_SEL1       equ     \{name}_sel1-\name+3
\{p}_PTR1       equ     \{name}_ptr1-\name+2
\{p}_SEL2       equ     \{name}_sel2-\name+3
\{p}_PTR2       equ     \{name}_ptr2-\name+2
\{p}_STEP1      equ     \{name}_step1-\name+2
\{p}_STEP2      equ     \{name}_step2-\name+2
\{p}_LOOPT      equ     \{name}_loopt-\name+2
\{p}_LOOP1R     equ     \{name}_loop1-\name+2
\{p}_LOOP1W     equ     \{name}_loop1-\name+6
\{p}_LOOP2R     equ     \{name}_loop2-\name+2
\{p}_LOOP2W     equ     \{name}_loop2-\name+6
\{p}_CELLS      equ     \{name}_cells-\name
\{p}_LAST       equ     \{name}_loopt-\name
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
