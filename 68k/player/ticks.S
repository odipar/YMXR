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
; The sync of a tick of a target of a sync (SPEC.md 1.12.3, 5.1), timed
; in steps of the tone generator, 32 cycles of an 8 MHz 68000: SYNC_STEP1
; the nops from the write of the coarse register's 0 to that of the fine
; one's, SYNC_STEP2 those after the fine one's before the branch into the
; pads, and SYNC_STEP3 those from the write of the fine value to that of
; the coarse; each pad is a step. Hatari stamps a write by the instruction
; that makes it, and the rig reads each gap off its trace
; (68k/test/emu/test_ymxr.py).
SYNC_STEP1      equ     1
SYNC_STEP2      equ     0
SYNC_STEP3      equ     3
                .macro  NOPS    n
                .if     \n>0
                .rept   \n
                nop
                .endr
                .endif
                .endm

; The place of a column into a0, read off the operand of its read at \ptr:
; through the displacement a tick steps, or the address.
                .macro  PLACEA  ptr
                .if     YMXR_PCREL
                lea     \ptr+2(pc),a0
                adda.w  (a0),a0
                .else
                movea.l \ptr+2(pc),a0
                .endif
                .endm

                .macro  TICKW   name,columns,counted,eoi,eoir,ctrl,keep,ier,bit,colour,inst,sync
                .if     \sync
; With \sync, the handler of a target of a sync (SPEC.md 5.1): the head
; below, before the first byte, which the first select's slot branches
; to; the slot keeps the register at +3, where a start writes it and the
; remap and the recut read it. At the timer's level, which masks every
; other timer, the head writes the sync of SPEC.md 1.12.3 from the row at
; the places: 0 to the voice's coarse register, 0 to its fine one a step
; later, n steps later the row's fine and a step after it the row's
; coarse; it then goes on to the handler's test of the last row. The
; branch at \{name}_first runs n - 1 pads: its displacement is the sync
; length, a byte the frame writes, and after the coarse the tick writes
; it from the immediate at \{name}_rowl, the row's, which the frame
; writes too (sync.S, ymxr_sfirst). The registers are immediates the
; start writes (sync.S, ymxr_syncwide).
\{name}_pads:
                .rept   7
                lsl.l   #8,d2                   ; a pad: 24 cycles and 8
                nop
                nop
                .endr
\{name}_w3:     move.b  (a0),YM_SELECT+2.w      ; the row's fine
                NOPS    SYNC_STEP3
                move.l  d0,YM_SELECT.w          ; the coarse register, the row's
\{name}_rowl:   move.b  #0,($12345678).l        ; coarse; +3 the row's length, +4
                movem.l (sp)+,d0/d2/a0          ; the first byte's address
                bra.w   \{name}_cnt
\{name}_head:   movem.l d0/d2/a0,-(sp)
                PLACEA  \{name}_ptr2
\{name}_c2:     move.l  #$01000000,d0           ; +2: the coarse register
                movep.w 0(a0),d0                ; the row's coarse, bits 15 to 8
                PLACEA  \{name}_ptr1
\{name}_f1:     move.l  #$00000000,d2           ; +2: the fine register
\{name}_c1:     move.l  #$01000000,YM_SELECT.w  ; +2: the coarse register, its 0
                NOPS    SYNC_STEP1
                move.l  d2,YM_SELECT.w          ; the fine register's 0
                NOPS    SYNC_STEP2
\{name}_first:  bra.s   \{name}_w3              ; +1: the sync length's
                .endif
\name:
                MARK    \colour
                .if     \sync
\{name}_sel1:   bra.s   \{name}_head
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
; The handler's end, where TWC's ends (handlers.S).
\{name}_past:
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
