; YMXR.S - the player: a tune's rows to the YM2149 and the MFP, one row a
; frame, and a source's rows to one register at a timer's rate.
;
; doc/SPEC.md is the contract: section 4 is YMXR_play and section 5 the
; tick handlers. This player reads the bound tune of doc/BINARIES.md: the
; tune file's tables, and no code, bound by a tool with the image DTX
; packaged the table with. The player calls that image's slots (DTX,
; doc/abi.md) and reads the row they return.
;
; The calls, by the jump table at the top of the binary:
;
;   YMXR_init   a0 the bound tune, on an even address; a1 the workspace,
;               on a long. d0 is 0, or -1 for one this player does not
;               read.
;   YMXR_play   a0 the workspace. One row. d0 is 0, or -1 where the tune
;               has played its last row and does not repeat: no register
;               is written then.
;   YMXR_stop   a0 the workspace. The claimed timers stopped, disabled and
;               masked; the three volumes silenced.
;
; Every call clobbers d0-d5 and a0-a5 and leaves d6, d7 and a6 as they
; were. YMXR_play runs in supervisor mode, at any interrupt level: every
; write of a select and a value is one movep, which no tick lands inside.
;
; A call writes the row the call before it read, and reads the next when
; its writes are made: the row's writes then leave a host's clock at one
; offset, where an advance before them would move by what the refill of
; the row costs. Init reads the first row.
;
; The workspace is YMXR_FIXED bytes, then the state block the image's
; format block requires, which the bound tune records at offset 12
; (BINARIES.md): a host allocates YMXR_FIXED plus that. The row a call
; writes is the image's pointer, IM_POINTER of that state block
; (DTX, abi.md 3), which an advance leaves at column 0's value, so the
; player keeps no copy of it.
;
; The host owns the machine. This player saves and restores no vector, no
; timer control and no interrupt enable: around init and stop a host keeps
; what it needs of the vectors at $134, $120, $114 and $110, of TACR, TBCR
; and both nibbles of TCDCR, and of bits 5 and 0 of IERA and IMRA and bits
; 5 and 4 of IERB and IMRB. A timer an effect does not run is left
; alone. The MFP runs in software end-of-interrupt mode, as TOS leaves
; it, and the vectors are the 68000's at zero.
;
; A host also needs a handler at $60 while this player runs. A write here
; that clears a pending or an enable bit between the MFP raising an
; interrupt and the 68000 acknowledging it leaves the MFP with no vector
; to place on the bus, and the 68000 runs exception 24 rather than the
; timer's handler. An rte is the handler: the tick the acknowledge
; belonged to is the one the write cancelled. YMXR_sndh.S keeps the host's vector
; and puts an rte there for as long as a tune plays.
;
; The player patches itself: the row's displacements and the branches
; over the effects the tune does not run at init, and the tick handlers'
; operands at every start. It runs from RAM, and one loaded copy serves
; one workspace.
;
; YMXR_PERF equ 1 assembles the raster monitor in: the play call paints
; the background red for as long as its work runs, one scanline to 512
; cycles, and each tick handler paints a separate colour where the beam
; is. A tick's band is a few hundred cycles wide and hard to read, so
; each handler adds a count of what its instructions cost, which the next
; call burns off as a yellow bar after its work, the register writes among
; them: the bar shows what the handlers cost the frame, and no chip write
; moves for the bar. What a tick costs beyond
; its handler, the 44 cycles the 68000 spends entering an interrupt and
; the 20 on the rte, is where the tick landed and is not counted
; here. The call waits for the display to start before it paints, since
; the VBL fires far above the screen and an unsynced bar lands in the
; top border; the wait is before the red mark, so it is outside the
; figures. Each count is an estimate, in turns of ten cycles,
; and the monitor is for reading a run: with the switch off, the
; default, no byte and no cycle of it remains.

YMXR_MAGIC      equ     $594D5842       ; YMXB, the bound tune

                .include "player/switches.S"
                .include "player/equates.S"

; ---------------------------------------------------------------- macros

                .include "player/columns.S"
                .include "player/effect.S"
                .include "player/ticks.S"
                .include "player/lookup_ticks.S"

; ---------------------------------------------------------------- calls

                bra.w   YMXR_init
                bra.w   YMXR_play
                bra.w   YMXR_stop

                .include "player/init.S"
                .include "player/stop.S"
                .include "player/play.S"

; Handler and offset macros. Without lookups, expand both here; with
; lookups, expand them after frame routines. This keeps handlers,
; installers and padding together from ymxr_tick0 for reach measurement.
; Offsets follow their labels because rmac requires prior definitions.
                .include "player/handlers.S"

                .if     YMXR_MAP=0
                HANDLERS
                HANDLEQU
                .endif

                .include "player/start.S"

                .if     YMXR_DEFER
                .include "player/deferred.S"
                .endif

                .include "player/tables.S"

                .if     YMXR_MAP
; Start through a transformation (SPEC.md 1.10.2). Inputs: a3 the record,
; FX_RUN its table number, d3 the source, d2 the control byte, d4 the
; effect; at the frame a5 the effect's instance of the general lookup
; kind, d5 the page byte of its first fallback page and d1 the vector,
; which the frame's start writes as constants. A source that steps a
; place runs the lookup instance of its kind in bank 0. The general kind's
; operands are at that kind's offsets; every other kind's descriptor,
; address and page byte are read by index from the two tables below and
; ymxr_htab. Its page is the one the table of the source's fill kind
; has for the transformation (FX_LMAPS); outside it, the fallback page in
; use or the other where one has it, and otherwise the other, filled
; with interrupts on (ymxr_lfill). The record caches the
; instance (FX_LOK). Squares and one-row sources store mapped entries in
; handler immediates.
;
; Patch operands and vector at level 7. With place reset clear, read the
; running place and reduce it modulo the new source length at that level
; too, so a tick cannot advance it between read and write (1.8.4).
; Clobbers d0, d1, d3 to d5 and a3 to a5; returns a2 at the chip select
; register.
;
; A deferred start (SPEC.md 4.8) is armed here for ymxr_arm, at the frame
; constants left aside: in the bank FX_ABANK names, through the captured
; mapping FX_MAP, into the record's mirror at FX_APLACE, and with the
; vector left to the installer. It returns a5 the instance and a3 the
; mirror; with WS_ARMING at 1 it fills the page alone and returns a3 the
; record.
ymxr_lstart:    move.w  d3,d0
                lsl.w   #3,d0                   ; the source's entry
                movea.l WS_SOURCES-8(a0,d0.w),a4    ; its first row
                move.l  WS_SOURCES-4(a0,d0.w),d3    ; its loop cell, or a tag
                bmi.w   .tagged
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                bne.w   .armlk
                .endif
; Marked, of one column, at the frame: the general kind, the marker
; mirror and the loop cell as a displacement from the place's read. The
; cache is written where it names another instance.
                move.l  d3,d0                   ; the loop cell
                moveq   #LK_PAGE,d3
                add.l   a5,d3                   ; the page byte's address
                cmp.l   FX_LBYTE(a3),d3
                beq.s   .lcached
                move.l  d3,FX_LBYTE(a3)
                move.b  d5,FX_LHIGH(a3)
                move.b  #1,FX_LKEY+1(a3)
                move.l  FX_LMAPS+4(a3),FX_LMAP(a3)  ; the marked kind's table
                .if     YMXR_DEFER
                sf      FX_LBANK(a3)
                .endif
.lcached:       moveq   #0,d4
                move.b  FX_RUN(a3),d4
                movea.l FX_LMAP(a3),a2
                move.b  0(a2,d4.w),d4           ; its page in the table, or
                beq.w   .lfall                  ; 0 for the fallback
.lpage:         add.b   d4,d5                   ; the page byte
                move.b  d5,FX_LCUR(a3)
                .if     YMXR_PCREL              ; the loop cell, 0 where the
                tst.l   d0                      ; source plays once
                beq.s   .lonce
                sub.l   a5,d0
                sub.w   #LK_PTR,d0
.lonce:
                .endif
                move.w  sr,-(sp)
                ori.w   #$0700,sr
ymxr_lkwindow   equ     *                       ; inside the window: the rig's
                                                ; boundary (inside)
                moveq   #0,d3                   ; the place: row 0, or the
                btst    #5,d2                   ; row the running handler is
                bne.s   .lfirst                 ; at modulo R of this source
                movea.w d1,a2
                bsr     ymxr_runrow
                move.l  DT_ROWS-DT_PAYLOAD(a4),d4
                bsr     ymxr_rem
                lea     YM_SELECT.w,a2
                move.b  FX_LCUR(a3),d5          ; the page byte again
.lfirst:        move.b  FX_TARGET(a3),LK_SEL(a5)
                move.l  a4,FX_FIRST(a3)         ; the place at row 0
                adda.l  d3,a4                   ; the row the place is at
                .if     YMXR_PCREL
                move.l  a4,d4
                sub.l   a5,d4
                sub.w   #LK_PTR,d4
                move.w  d4,LK_PTR(a5)
                move.w  d0,LK_CELL(a5)
                .else
                move.l  a4,LK_PTR(a5)
                move.l  d0,LK_CELL(a5)
                .endif
                move.b  d5,LK_PAGE(a5)          ; the page byte
                movea.w d1,a4
                move.l  a5,(a4)                 ; the vector
                move.w  (sp)+,sr
                clr.b   FX_SHAPE(a3)
                st      FX_LOK(a3)
                lea     YM_SELECT.w,a2          ; past the table's read
                rts
; Outside the table: the fallback page in use, then the other, filled
; where neither has the transformation in the marked kind.
.lfall:         move.b  FX_RUN(a3),FX_LKEY(a3)
                move.w  FX_LKEY(a3),d3
                moveq   #0,d4
                move.b  FX_USE(a3),d4
                add.w   d4,d4
                cmp.w   FX_PAGE(a3,d4.w),d3
                beq.s   .lfound
                eori.w  #2,d4
                cmp.w   FX_PAGE(a3,d4.w),d3
                beq.s   .lfound
                move.l  d5,-(sp)
                move.w  d3,d5
                move.w  d4,d3
                bsr     ymxr_lfill
                move.l  (sp)+,d5
.lfound:        lsr.w   #1,d4
                move.b  d4,FX_USE(a3)
                add.b   FX_LFB(a3),d4           ; past the effect's pages
                bra.w   .lpage
                .if     YMXR_DEFER
.armlk:         move.l  d3,d0                   ; an arming of the general kind
                moveq   #0,d5
                bra.s   .kind
                .endif
; A kind's descriptor past ymxr_kinds, and an effect's lookup instances
; past effect 0's.
.kd:            dc.b    0,KD_SIZE,2*KD_SIZE,3*KD_SIZE
                dc.b    4*KD_SIZE,5*KD_SIZE,6*KD_SIZE,7*KD_SIZE
.eff:           dc.w    0,LOOK_STRIDE,2*LOOK_STRIDE,3*LOOK_STRIDE
.tagged:        addq.l  #1,d3
                beq     .square
                addq.l  #1,d3
                beq     .one
                lea     WS_WIDE(a0),a2          ; a source of several columns,
                swap    d1                      ; or counted: the stride from
                move.w  -4(a2,d0.w),d1          ; one column to the next, in
                swap    d1                      ; the high word of d1, and its
                move.l  -8(a2,d0.w),d0          ; loop cell
                moveq   #2,d5
                addq.l  #1,d3
                beq.s   .two
                moveq   #4,d5
                addq.l  #1,d3
                beq.s   .three
                moveq   #1,d5                   ; counted, of one column
                addq.l  #1,d3
                beq.s   .kind
                moveq   #6,d5                   ; counted, of two
.two:           cmp.b   #22,FX_TARGET(a3)       ; setNoiseX maps its first
                bcs.s   .kind                   ; write, the volume, marked
                addq.w  #1,d5                   ; or counted
                bra.s   .kind
.three:         cmp.b   #21,FX_TARGET(a3)       ; setVoiceX maps its middle
                beq.s   .kind                   ; column, the volume
                moveq   #5,d5
; d5 the kind, d0 the loop cell, a4 the first row, and the stride in the
; high word of d1. The descriptor, the instance and the page byte of the
; effect's first fallback page for the instance into the record's cache.
.kind:          moveq   #0,d3
                move.b  .kd(pc,d5.w),d3
                lea     ymxr_kinds(pc),a2
                adda.w  d3,a2                   ; the descriptor
                move.w  d4,d3
                add.w   d3,d3
                move.w  .eff(pc,d3.w),d4        ; two bytes an effect
                lsl.w   #2,d3                   ; eight page bytes an effect,
                add.w   d5,d3                   ; one a kind
                .if     YMXR_DEFER
                sf      FX_LBANK(a3)            ; bank 0, or an arming's
                tst.b   WS_ARMING(a0)
                beq.s   .bank
                tst.w   FX_ABANK(a3)
                beq.s   .bank
                add.w   #BANK,d4
                add.w   #32,d3                  ; bank 1's page bytes
                move.b  #1,FX_LBANK(a3)
.bank:
                .endif
                lea     ymxr_htab(pc),a5
                move.b  (a5,d3.w),FX_LHIGH(a3)
                lea     ymxr_tick0(pc),a5
                adda.w  KD_BASE(a2),a5
                adda.w  d4,a5                   ; the instance
                move.w  KD_PAGE(a2),d4
                pea     0(a5,d4.w)
                move.l  (sp)+,FX_LBYTE(a3)      ; its page byte, cached
; The fill kind, a counted source on setR7 the ports' bits, and the page.
                move.b  KD_FILL(a2),d4
                subq.w  #1,d5
                bne.s   .fill
                cmp.b   #7,FX_TARGET(a3)
                bne.s   .fill
                moveq   #2,d4
.fill:          move.b  d4,FX_LKEY+1(a3)        ; the fill kind, cached
                move.l  a1,d3                   ; a1 kept
                add.w   d4,d4
                add.w   d4,d4
                movea.l FX_LMAPS(a3,d4.w),a1    ; its table, cached
                move.l  a1,FX_LMAP(a3)
                moveq   #0,d5
                move.b  FX_RUN(a3),d5
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; an arming's captured mapping
                beq.s   .run
                move.b  FX_MAP(a3),d5
.run:
                .endif
                move.b  0(a1,d5.w),d4           ; the page, or 0 for the
                movea.l d3,a1                   ; fallback
                beq.w   .kfall
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; at 1 the page alone
                bgt.w   .started
                .endif
.kpage:         move.b  FX_LHIGH(a3),d5
                add.b   d4,d5                   ; the page byte
                move.b  d5,FX_LCUR(a3)
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; an arming writes the mirror
                beq.s   .rec
                lea     ARMOFF(a3),a3
.rec:
                .endif
                move.l  DT_ROWS-DT_PAYLOAD(a4),FX_ROWS(a3)  ; R
; One column: the loop cell, under YMXR_PCREL a displacement from the
; place's read at TICK_PTR, as every lookup kind has it. Several: the
; registers the loop below uses kept, the loop cell, the map's row of the
; target, eight bytes from target 14 (SPEC.md 2.1), and the offset of the
; loop cells.
                move.w  KD_COLS(a2),d4
                bne.s   .wide
                .if     YMXR_PCREL
                tst.l   d0
                beq.s   .level
                sub.l   a5,d0
                sub.w   #TICK_PTR,d0
                .endif
                bra.s   .level
.wide:          movem.l d2/d6-d7/a1/a6,-(sp)
                movea.l d0,a6
                moveq   #0,d6
                move.b  FX_TARGET(a3),d6
                sub.w   #14,d6
                lsl.w   #3,d6
                lea     ymxr_wide(pc),a1
                adda.w  d6,a1
                move.w  KD_CELLS(a2),d7
.level:         move.w  sr,-(sp)
                ori.w   #$0700,sr
ymxr_lwindow    equ     *                       ; inside the window: the rig's
                                                ; boundary (inside)
                moveq   #0,d3                   ; the place: row 0, an
                .if     YMXR_DEFER              ; arming's at FX_APLACE, or the
                tst.b   WS_ARMING(a0)           ; row the running handler is
                beq.s   .now                    ; at modulo R of this source,
                move.l  FX_APLACE-ARMOFF(a3),d3 ; out of the record before
                bra.s   .first                  ; the columns below write it
.now:
                .endif
                btst    #5,d2
                bne.s   .first
                move.l  a2,-(sp)
                movea.w d1,a2
                bsr     ymxr_runrow
                move.l  DT_ROWS-DT_PAYLOAD(a4),d4
                bsr     ymxr_rem
                movea.l (sp)+,a2
                move.b  FX_LCUR(a3),d5          ; the page byte again
.first:         move.w  KD_PAGE(a2),d4
                move.b  d5,(a5,d4.w)            ; the page byte
                move.w  KD_COLS(a2),d4
                bne.s   .cols
; One column: the target's register at TICK_SEL and the place at
; TICK_PTR, as every lookup kind has them, the loop cell and, counted,
; the rows from the place on and the rows of a pass through the loop.
                move.b  FX_TARGET(a3),TICK_SEL(a5)
                move.l  a4,d4
                add.l   d3,d4                   ; the row the place is at
                .if     YMXR_PCREL
                sub.l   a5,d4
                sub.w   #TICK_PTR,d4
                move.w  d4,TICK_PTR(a5)
                move.w  KD_CELLS(a2),d4
                move.w  d0,(a5,d4.w)
                .else
                move.l  d4,TICK_PTR(a5)
                move.w  KD_CELLS(a2),d4
                move.l  d0,(a5,d4.w)
                .endif
                move.w  KD_LEFT(a2),d4
                beq.s   .one1
                move.w  FX_ROWS+2(a3),(a5,d4.w) ; R less the place
                sub.w   d3,(a5,d4.w)
                move.w  FX_ROWS+2(a3),d3        ; R less the row the loop
                tst.l   d0                      ; cell is at, or R where the
                beq.s   .reld                   ; source plays once
                move.l  d0,d4
                .if     YMXR_PCREL
                add.l   a5,d4
                add.w   #TICK_PTR,d4
                .endif
                sub.l   a4,d4
                sub.w   d4,d3
.reld:          move.w  KD_RELD(a2),d4
                move.w  d3,(a5,d4.w)
.one1:          move.l  a4,FX_FIRST(a3)         ; the place at row 0
                move.b  KD_SHAPE(a2),FX_SHAPE(a3)
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; the installer writes an
                bne.s   .armed1                 ; armed start's vector
                .endif
                movea.w d1,a4
                move.l  a5,(a4)                 ; the vector
                move.w  (sp)+,sr
                st      FX_LOK(a3)
                lea     YM_SELECT.w,a2
                rts
                .if     YMXR_DEFER
.armed1:        move.w  (sp)+,sr
                lea     YM_SELECT.w,a2
                rts
                .endif
; Several: a register, a place and a loop cell a column, as a start
; through the identity writes them.
.cols:          swap    d1                      ; the stride
                moveq   #0,d6                   ; four bytes a column
.col:           move.w  KD_SEL(a2,d6.w),d2
                move.b  (a1)+,(a5,d2.w)         ; the register
                moveq   #0,d0
                move.w  KD_PTR(a2,d6.w),d0
                add.l   a5,d0                   ; the column's read
                moveq   #0,d5
                move.b  (a1)+,d5
                mulu    d1,d5                   ; the column it writes
                add.l   a4,d5                   ; at row 0, kept for the
                move.l  d5,FX_FIRST(a3,d6.w)    ; place's reset
                move.l  a6,d2                   ; the column's loop cell, or
                beq.s   .none                   ; 0 where the source plays once
                sub.l   a4,d2
                add.l   d5,d2
                .if     YMXR_PCREL
                sub.l   d0,d2                   ; a displacement from the read
.none:          move.w  d2,(a5,d7.w)
                .else
.none:          move.l  d2,(a5,d7.w)
                .endif
                add.l   d3,d5                   ; the row the place is at
                move.w  KD_PTR(a2,d6.w),d2
                .if     YMXR_PCREL
                sub.l   d0,d5
                move.w  d5,(a5,d2.w)
                .else
                move.l  d5,(a5,d2.w)
                .endif
                addq.w  #4,d6                   ; the next column
                addq.w  #CELL_SIZE,d7
                dbra    d4,.col
                swap    d1
                move.w  KD_LEFT(a2),d4          ; counted: the rows from the
                beq.s   .aim                    ; place on, and the rows of a
                move.l  FX_ROWS(a3),d5          ; pass through the loop, R
                move.w  d5,(a5,d4.w)            ; less RR, or R where it plays
                sub.w   d3,(a5,d4.w)            ; once
                move.l  a6,d0
                beq.s   .pass
                sub.l   a4,d0
                sub.w   d0,d5
.pass:          move.w  KD_RELD(a2),d4
                move.w  d5,(a5,d4.w)
; The vector, which the installer writes for an arming, and the shape.
.aim:
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                bne.s   .armed
                .endif
                movea.w d1,a4
                move.l  a5,(a4)                 ; the vector
                st      FX_LOK(a3)
.armed:         move.w  (sp)+,sr
                move.b  KD_SHAPE(a2),FX_SHAPE(a3)
                movem.l (sp)+,d2/d6-d7/a1/a6
.started:       lea     YM_SELECT.w,a2
                rts
; Outside the table: the fallback page of the bank in use, then the
; other, filled where neither has the transformation in the fill kind.
.kfall:         move.b  d5,FX_LKEY(a3)
                move.w  FX_LKEY(a3),d5
                .if     YMXR_DEFER
                moveq   #0,d4
                move.b  FX_LBANK(a3),d4
                moveq   #0,d3
                move.b  FX_USE(a3,d4.w),d3
                add.w   d4,d4
                add.w   d4,d3
                .else
                moveq   #0,d3
                move.b  FX_USE(a3),d3
                .endif
                add.w   d3,d3                   ; four bytes a bank, two a page
                cmp.w   FX_PAGE(a3,d3.w),d5
                beq.s   .found
                eori.w  #2,d3
                cmp.w   FX_PAGE(a3,d3.w),d5
                beq.s   .found
                bsr     ymxr_lfill
.found:         moveq   #2,d4
                and.w   d3,d4
                lsr.w   #1,d4                   ; the page in the bank
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)           ; at 1 the page alone
                bgt.w   .started
                moveq   #0,d5
                move.b  FX_LBANK(a3),d5
                move.b  d4,FX_USE(a3,d5.w)
                .else
                move.b  d4,FX_USE(a3)
                .endif
                move.w  d3,d4
                lsr.w   #1,d4                   ; 2 a bank, and the page,
                add.b   FX_LFB(a3),d4           ; past the effect's pages
                bra.w   .kpage
; A square: the entries of its two keys, the second the marker's.
.square:        movem.l d2/d6-d7/a1/a6,-(sp)
                move.w  d4,d6                   ; the effect
                moveq   #0,d0
                move.b  FX_RUN(a3),d0
                bsr     ymxr_tab
                move.b  (a4),d0
                bsr     ymxr_entry
                move.b  d0,d1                   ; row 0's level
                move.b  1(a4),d0
                bsr     ymxr_entry              ; row 1's
                move.b  d1,FX_LEVEL(a3)
                move.b  d1,d4
                eor.b   d0,d4                   ; the difference of the two
                move.w  d6,d5
                mulu    #SQ_SIZE,d5
                lea     ymxr_sq0(pc),a5
                adda.w  d5,a5
                lea     ymxr_timers(pc),a1
                lsl.w   #3,d6
                movea.w (a1,d6.w),a1            ; the vector
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                btst    #5,d2                   ; row 0, or the row of the
                bne.s   .row0                   ; two the running handler is
                movea.l a1,a2                   ; at, its row number modulo 2
                bsr     ymxr_runrow             ; (1.8.4)
                btst    #0,d3
                beq.s   .row0
                move.b  d0,d1
.row0:          move.b  FX_TARGET(a3),SQ_SEL(a5)
                move.b  d4,SQ_MASK(a5)
                move.b  d1,SQ_VAL(a5)
                move.l  a5,(a1)
                move.w  (sp)+,sr
                move.l  a4,FX_FIRST(a3)
                st      FX_SHAPE(a3)
                sf      FX_LOK(a3)
                bra.s   .done
; A source of one row: its entry.
.one:           movem.l d2/d6-d7/a1/a6,-(sp)
                move.w  d4,d6                   ; the effect
                moveq   #0,d0
                move.b  FX_RUN(a3),d0
                bsr     ymxr_tab
                move.b  (a4),d0
                bsr     ymxr_entry
                move.w  d6,d1
                mulu    #ONE_SIZE,d1
                lea     ymxr_one0(pc),a5
                adda.w  d1,a5
                lea     ymxr_timers(pc),a1
                lsl.w   #3,d6
                movea.w (a1,d6.w),a1
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  FX_TARGET(a3),ONE_SEL(a5)
                move.b  d0,ONE_VAL(a5)
                move.l  a5,(a1)
                move.w  (sp)+,sr
                move.l  a4,FX_FIRST(a3)
                move.b  #1,FX_SHAPE(a3)
                sf      FX_LOK(a3)
.done:          movem.l (sp)+,d2/d6-d7/a1/a6
                lea     YM_SELECT.w,a2
                rts

; Each timer's select bits in its control register, by effect.
ymxr_sels:      dc.b    $07,$07,$07,$70

; Remap (SPEC.md 1.10.2). Inputs: d1=mapping number 0 to 127, d4=effect,
; a3 its record, d0 its timer's control register, d2=control byte,
; d3=pre-reset timer control when applicable.
; Replace kept. If running, replace its mapping while preserving source,
; place and rate; if stopped, preserve the new mapping for a later start.
;
; The frame switches the page itself where the cache is in force (the
; remap of EFFECT). Here squares and one-row sources replace immediate
; entries, and stepping sources switch the running instance's page
; (ymxr_page) or move between identity and lookup handlers (ymxr_move);
; the record caches a lookup instance. Clobbers d0, d1, d3 to d5 and a3
; to a5; returns a2 at the chip select register.
;
; A pending start retains its captured mapping. First update the record
; for any completed installation, then remap at level 7. Page filling
; alone enables interrupts; if installation occurs during the fill,
; remap the newly running source. Moving handlers also moves the pending
; hook (ymxr_rehook), including a square's mask.
ymxr_remap:     move.b  d1,FX_MAP(a3)           ; the kept mapping
                .if     YMXR_DEFER
                tst.b   FX_PENDING(a3)          ; a start pending: below
                bne.w   .slow
                .endif
                tst.b   d2                      ; the select bits: 0 on a
                bpl.s   .live                   ; stopped timer, and as they
                btst    #6,d2                   ; were before the reset where
                beq.s   .live                   ; the row resets the timer
                move.b  d3,d0
.live:          and.b   ymxr_sels(pc,d4.w),d0
                beq.s   .kept
                cmp.b   FX_RUN(a3),d1           ; the mapping it runs
                bne.s   .moved
.kept:          rts
; Every other remap on a running timer: the running mapping in d7 and the
; new one in the record, and the vector in a6. The frame enters here with
; the kept mapping written, the timer running and no start pending.
.moved:
ymxr_rmove      equ     *
                movem.l d2/d6-d7/a1/a6,-(sp)
                .if     YMXR_DEFER
                move.w  sr,-(sp)
                .endif
                move.w  d4,d6                   ; the effect
                move.b  FX_RUN(a3),d7
                move.b  d1,FX_RUN(a3)
                lsl.w   #3,d4
                lea     ymxr_timers(pc),a6
                movea.w (a6,d4.w),a6            ; the vector
                .if     YMXR_DEFER
                bra.s   .vec
; A start pending: the record follows a switch the tick has made, then
; the timer's select bits as above.
.slow:          movem.l d2/d6-d7/a1/a6,-(sp)
                move.w  sr,-(sp)
                move.w  d4,d6                   ; the effect
                ori.w   #$0700,sr
                moveq   #0,d5                   ; left pending
                bsr.w   ymxr_pend
                bne.s   .on
                move.w  (sp),sr                 ; the tick has performed it
.on:            lea     ymxr_timers(pc),a6
                move.w  d6,d0
                lsl.w   #3,d0
                adda.w  d0,a6                   ; its timer
                movea.w 2(a6),a4                ; the select bits of its
                move.b  (a4),d5                 ; control register: 0 on a
                tst.b   d2                      ; stopped timer, and as they
                bpl.s   .sel                    ; were before the reset where
                btst    #6,d2                   ; the row resets the timer
                beq.s   .sel
                move.b  d3,d5
.sel:           and.b   6(a6),d5
                beq     .done
                move.b  FX_RUN(a3),d7           ; the mapping it runs
                cmp.b   d7,d1
                beq     .done
                move.b  d1,FX_RUN(a3)
                movea.w (a6),a6                 ; the vector
                .endif
.vec:           movea.l (a6),a5                 ; the running handler
                moveq   #0,d4                   ; and its bank
                .if     YMXR_DEFER
                lea     ymxr_bank1(pc),a4
                cmpa.l  a4,a5
                bcs.s   .bank
                moveq   #1,d4
.bank:
                .endif
                move.b  FX_SHAPE(a3),d0
                bmi     .square
                cmp.b   #1,d0
                beq     .one
                bsr     ymxr_kindof
                move.w  d0,d2                   ; the lookup kind
                move.w  d1,d3                   ; the identity's
                tst.b   FX_RUN(a3)
                beq.w   .toid
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2
                move.b  KD_FILL(a2),d5
                cmp.w   #1,d2                   ; a counted source on setR7
                bne.s   .kind                   ; writes the ports' bits: R7
                cmp.b   #7,TICK_SEL(a5)         ; in the running handler, as
                bne.s   .kind                   ; ymxr_kindof reads it
                moveq   #2,d5
.kind:
                .if     YMXR_DEFER
                tst.b   FX_PENDING(a3)
                bne     .pfill
                .endif
                bsr     ymxr_page               ; d0 the page
.held:          moveq   #0,d1                   ; the cache: the fill kind's
                move.b  d5,d1                   ; table,
                add.w   d1,d1
                add.w   d1,d1
                move.l  FX_LMAPS(a3,d1.w),FX_LMAP(a3)
                move.b  d5,FX_LKEY+1(a3)        ; the fill kind and
                .if     YMXR_DEFER              ; the bank, in force with no
                move.b  d4,FX_LBANK(a3)         ; start pending
                tst.b   FX_PENDING(a3)
                seq     FX_LOK(a3)
                .else
                st      FX_LOK(a3)
                .endif
                move.w  d4,d1                   ; its page byte: bank, effect,
                lsl.w   #5,d1                   ; then kind
                move.w  d6,d5
                lsl.w   #3,d5
                add.w   d5,d1
                add.w   d2,d1
                lea     ymxr_htab(pc),a4
                move.b  (a4,d1.w),FX_LHIGH(a3)  ; the first fallback page's
                add.b   FX_LHIGH(a3),d0
                move.w  KD_PAGE(a2),d1
                tst.b   d7
                beq.s   .tolook
                lea     0(a5,d1.w),a4           ; the page byte, cached
                move.l  a4,FX_LBYTE(a3)
                move.b  d0,(a4)                 ; one write, which no tick
                bra     .done                   ; splits
.tolook:        bsr     ymxr_at                 ; the instance, its page byte
                pea     0(a4,d1.w)              ; cached, and written before
                move.l  (sp)+,FX_LBYTE(a3)      ; the vector names it
                move.b  d0,(a4,d1.w)
                movea.l a2,a1                   ; names it
                move.w  d3,d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2                   ; from the identity's handler
                .if     YMXR_DEFER
                move.w  d2,d0
                bsr     ymxr_rehook
                .endif
                bsr     ymxr_move
                bra     .done
.toid:          sf      FX_LOK(a3)              ; the identity's handler
                move.w  d3,d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2
                bsr     ymxr_at                 ; the identity's handler
                movea.l a2,a1
                move.w  d2,d0
                mulu    #KD_SIZE,d0
                lea     ymxr_kinds(pc),a2
                adda.w  d0,a2                   ; from the lookup instance
                .if     YMXR_DEFER
                move.w  d3,d0
                bsr     ymxr_rehook
                .endif
                bsr     ymxr_move
                bra     .done
                .if     YMXR_DEFER
; Fill with interrupts enabled, then re-read the vector at level 7. If a
; tick installed the pending start meanwhile, update the record and
; remap the new running source.
.pfill:         move.w  (sp),sr
                bsr     ymxr_page
                ori.w   #$0700,sr
                cmpa.l  (a6),a5
                beq     .held
                bsr.w   ymxr_take
                move.w  (sp),sr
                move.b  FX_MAP(a3),d1
                move.b  FX_RUN(a3),d7
                cmp.b   d7,d1
                beq     .done
                move.b  d1,FX_RUN(a3)
                bra     .vec
                .endif
; A square: both levels, and the one its immediate is at by bit 7.
.square:        movea.l FX_FIRST(a3),a4
                move.b  (a4),d1
                move.b  1(a4),d3
                moveq   #0,d0
                move.b  FX_RUN(a3),d0
                beq.s   .levels
                bsr     ymxr_tab
                move.b  d1,d0
                bsr     ymxr_entry
                move.b  d0,d1                   ; row 0's level
                move.b  d3,d0
                bsr     ymxr_entry
                move.b  d0,d3                   ; row 1's
.levels:        move.b  d1,FX_LEVEL(a3)
                move.b  d1,d4
                eor.b   d3,d4
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  d4,SQ_MASK(a5)
                .if     YMXR_DEFER
                move.b  d4,SQ_HMASK(a5)         ; and the hook's copy (4.8)
                .endif
ymxr_rlevel     equ     *                       ; between the mask and the
                tst.b   SQ_VAL(a5)              ; level: the rig's boundary
                bpl.s   .row0                   ; (inside)
                move.b  d3,d1
.row0:          move.b  d1,SQ_VAL(a5)
                move.w  (sp)+,sr
                bra.s   .done
; A source of one row: its entry, one write.
.one:           movea.l FX_FIRST(a3),a4
                move.b  (a4),d1
                moveq   #0,d0
                move.b  FX_RUN(a3),d0
                beq.s   .put
                bsr     ymxr_tab
                move.b  d1,d0
                bsr     ymxr_entry
                move.b  d0,d1
.put:           move.b  d1,ONE_VAL(a5)
.done:
                .if     YMXR_DEFER
                move.w  (sp)+,sr
                .endif
                movem.l (sp)+,d2/d6-d7/a1/a6
                lea     YM_SELECT.w,a2
                rts

; The running source moved from the handler a5, which a2 describes, to
; the handler a4 of the same shape, which a1 describes: each column's
; register and loop cell and the rows of a pass with interrupts on, since
; no tick reads a handler the vector does not name; then behind level 7
; each column's place, the rows left and the vector a6, so the next tick
; reads the row the last one left. Under YMXR_PCREL a place and a loop
; cell are displacements from the column's read, moved by the distance
; between the two reads. d0 to d5 and d7 clobbered.
ymxr_move:      move.w  KD_COLS(a2),d4
                moveq   #0,d3                   ; four bytes a column
                moveq   #0,d7                   ; CELL_SIZE a column
.pre:           move.w  KD_SEL(a2,d3.w),d0
                move.w  KD_SEL(a1,d3.w),d1
                move.b  (a5,d0.w),(a4,d1.w)     ; the register
                move.w  KD_CELLS(a2),d0
                add.w   d7,d0
                move.w  KD_CELLS(a1),d1
                add.w   d7,d1
                .if     YMXR_PCREL
                move.w  (a5,d0.w),d0            ; the loop cell, 0 where the
                beq.s   .once                   ; source plays once
                bsr.s   .moved
.once:          move.w  d0,(a4,d1.w)
                .else
                move.l  (a5,d0.w),(a4,d1.w)
                .endif
                addq.w  #4,d3
                addq.w  #CELL_SIZE,d7
                dbra    d4,.pre
                move.w  KD_RELD(a2),d0          ; the rows of a pass
                beq.s   .level
                move.w  KD_RELD(a1),d1
                move.w  (a5,d0.w),(a4,d1.w)
.level:         move.w  KD_COLS(a2),d4
                moveq   #0,d3
                move.w  sr,-(sp)
                ori.w   #$0700,sr
.place:         move.w  KD_PTR(a2,d3.w),d0
                move.w  KD_PTR(a1,d3.w),d1
                .if     YMXR_PCREL
                move.w  (a5,d0.w),d0
                bsr.s   .moved
                move.w  d0,(a4,d1.w)
                .else
                move.l  (a5,d0.w),(a4,d1.w)
                .endif
                addq.w  #4,d3
                dbra    d4,.place
                move.w  KD_LEFT(a2),d0          ; the rows left
                beq.s   .aim
                move.w  KD_LEFT(a1),d1
                move.w  (a5,d0.w),(a4,d1.w)
.aim:           move.l  a4,(a6)
                move.w  (sp)+,sr
                rts
                .if     YMXR_PCREL
; d0 a displacement from column d3's read in a5, as one from its read in a4.
.moved:         ext.l   d0
                add.l   a5,d0
                move.w  KD_PTR(a2,d3.w),d2
                ext.l   d2
                add.l   d2,d0                   ; the row's address
                sub.l   a4,d0
                move.w  KD_PTR(a1,d3.w),d2
                ext.l   d2
                sub.l   d2,d0
                rts
                .endif

                .if     YMXR_DEFER
; Move a pending hook when remapping to handler a4, kind d0 in
; ymxr_kinds. Restore the old handler's end-path word, hook a4, and save
; its overwritten word for installation or cancellation. Runs at level 7
; while the vector still selects the old handler. a3=record, d6=effect;
; clobbers d0.
ymxr_rehook:    tst.b   FX_PENDING(a3)
                beq.s   .none
                movem.l d1/a1-a2,-(sp)
                movea.l FX_HOOK(a3),a2
                move.w  FX_HOOKW(a3),(a2)       ; the handler it leaves
                lea     ymxr_hooks(pc),a2
                add.w   d0,d0
                add.w   d0,d0
                move.w  2(a2,d0.w),d1           ; the word the hook writes
                move.w  (a2,d0.w),d0
                lea     (a4,d0.w),a2            ; and where
                lea     ymxr_timers(pc),a1
                move.w  d6,d0
                lsl.w   #3,d0
                adda.w  4(a1,d0.w),a1           ; the effect's installer
                move.w  (a2),FX_HOOKW(a3)
                move.w  (a2),INST_BACKW(a1)
                move.l  a2,INST_BACKA(a1)
                move.l  a2,FX_HOOK(a3)
                move.w  d1,(a2)
                movem.l (sp)+,d1/a1-a2
.none:          rts
                .endif

; Fill a fallback page of the lookup instance the record a3 caches with
; interrupts enabled: d3 the offset of the page's two bytes past FX_PAGE,
; four a bank and two a page, and d5 the two bytes, the transformation and
; the fill kind, which the record then lists for the page. The page is the
; instance's page byte, at FX_LBYTE, plus its high byte times 256: FX_LHIGH,
; and 2 more a bank and 1 more for the bank's page 1. Preserves every
; register.
ymxr_lfill:     movem.l d0-d1/d3/a2/a4,-(sp)
                tst.b   FX_LFB(a3)              ; an effect without one: no
                beq.s   .none                   ; page written
                move.w  d5,FX_PAGE(a3,d3.w)
                lsr.w   #1,d3                   ; the page
                add.b   FX_LFB(a3),d3
                add.b   FX_LHIGH(a3),d3
                lsl.w   #8,d3
                movea.l FX_LBYTE(a3),a4         ; the page read's displacement
                adda.w  d3,a4                   ; word, the page past it
                move.w  d5,d0
                lsr.w   #8,d0                   ; the transformation
                bsr     ymxr_tab
                bsr     ymxr_fill
.none:          movem.l (sp)+,d0-d1/d3/a2/a4
                rts

; The page for FX_RUN, effect d6, bank d4 and fill kind d5; a3 is the
; record. The page the table of the fill kind has for the transformation
; (FX_LMAPS); where it has 0, the bank's fallback page in use where it
; matches, and else the other, which the bank then uses, filled where it
; has another transformation or fill kind. Fill only an unused page, with
; interrupts enabled. Return d0 the page in 256 bytes from the effect's
; first fallback page, a fallback page 2 times the bank plus the page in
; the bank; preserve other registers.
;
; While arming, use the row's captured mapping. FX_USE identifies any
; page read by an existing pending start, so filling preserves that
; page.
ymxr_page:      movem.l d1/a4,-(sp)
                moveq   #0,d1
                move.b  FX_RUN(a3),d1
                .if     YMXR_DEFER
                tst.b   WS_ARMING(a0)
                beq.s   .run
                move.b  FX_MAP(a3),d1
.run:
                .endif
                moveq   #0,d0                   ; the table of the fill kind
                move.b  d5,d0
                add.w   d0,d0
                add.w   d0,d0
                movea.l FX_LMAPS(a3,d0.w),a4
                move.b  0(a4,d1.w),d0           ; the page, or 0 outside it
                beq.s   .fall
                movem.l (sp)+,d1/a4
                rts
.fall:          move.w  d4,d0
                add.w   d0,d0
                add.w   d0,d0
                lea     FX_PAGE(a3,d0.w),a4     ; the bank's two pages
                moveq   #0,d0
                move.b  FX_USE(a3,d4.w),d0
                add.w   d0,d0                   ; two bytes a page
                cmp.b   (a4,d0.w),d1
                bne.s   .other
                cmp.b   1(a4,d0.w),d5
                beq.s   .found
.other:         eori.w  #2,d0
                cmp.b   (a4,d0.w),d1
                bne.s   .fill
                cmp.b   1(a4,d0.w),d5
                beq.s   .found
.fill:          tst.b   FX_LFB(a3)              ; an effect without one: no
                beq.s   .found                  ; page written
                move.b  d1,(a4,d0.w)
                move.b  d5,1(a4,d0.w)
                movem.l d2/a2,-(sp)
                move.w  d6,d2                   ; the page: the effect's
                add.w   d2,d2                   ; fallback pages past its
                lea     ymxr_refat(pc),a4       ; reference, 512 bytes a bank,
                move.w  (a4,d2.w),d2            ; 256 a page
                lea     ymxr_ref(pc),a4
                adda.w  d2,a4
                moveq   #0,d2
                move.b  FX_LFB(a3),d2
                lsl.l   #8,d2
                adda.l  d2,a4
                move.w  d4,d2
                lsl.w   #8,d2
                add.w   d2,d2
                adda.w  d2,a4
                move.w  d0,d2
                lsl.w   #7,d2
                adda.w  d2,a4
                move.w  d0,-(sp)
                moveq   #0,d0
                move.b  d1,d0
                bsr.s   ymxr_tab
                bsr.s   ymxr_fill
                move.w  (sp)+,d0
                movem.l (sp)+,d2/a2
.found:         lsr.w   #1,d0
                move.b  d0,FX_USE(a3,d4.w)      ; the bank's page in use
                add.b   d4,d0
                add.b   d4,d0
                add.b   FX_LFB(a3),d0           ; past the effect's pages
                movem.l (sp)+,d1/a4
                rts

; Resolve transformation d0 (1 to 127) into a2: E at offset 0, entry k
; at 2 + k (SPEC.md 3.4.2). Clobbers d0.
ymxr_tab:       movea.l WS_TRANS(a0),a2
                add.w   d0,d0
                add.w   d0,d0
                adda.l  -2(a2,d0.w),a2
                rts

; Map marked byte d0 through table a2: use bits 6 to 0 as the key,
; replace those bits with the entry, and preserve source bit 7. d5 is
; scratch.
ymxr_entry:     moveq   #$7F,d5
                and.b   d0,d5
                and.b   #$80,d0
                or.b    2(a2,d5.w),d0
                rts

; Fill page a4 from transformation a2, according to kind d5 (SPEC.md
; 2.1.5). Plain: copy entries as longs, eight per movem. Marker mirror:
; copy up to 128 entries, then duplicate them at offset 128 with bit 7
; set. setR7: copy keys 0 to 63 at offset 192 with both port bits set.
; Page storage allows the three extra bytes read by a final long.
; Clobbers d0, d1, a2 and a4.
ymxr_fill:      move.w  (a2)+,d0                ; E
                tst.b   d5
                beq.s   .plain
                cmp.b   #1,d5
                beq.s   .mirror
                cmp.w   #64,d0
                bls.s   .ports
                moveq   #64,d0
.ports:         lea     192(a4),a4
                addq.w  #3,d0
                lsr.w   #2,d0
                bra.s   .pnext
.port:          move.l  (a2)+,d1
                or.l    #$C0C0C0C0,d1
                move.l  d1,(a4)+
.pnext:         dbra    d0,.port
                rts
.mirror:        cmp.w   #128,d0
                bls.s   .half
                move.w  #128,d0
.half:          addq.w  #3,d0
                lsr.w   #2,d0
                bra.s   .mnext
.mir:           move.l  (a2)+,d1
                move.l  d1,(a4)+
                or.l    #$80808080,d1
                move.l  d1,124(a4)
.mnext:         dbra    d0,.mir
                rts
.plain:         addq.w  #3,d0
                lsr.w   #2,d0                   ; E in longs
                movem.l d2-d7/a3,-(sp)
                subq.w  #8,d0
                bcs.s   .rest
.eight:         movem.l (a2)+,d1-d7/a3
                movem.l d1-d7/a3,(a4)
                lea     32(a4),a4
                subq.w  #8,d0
                bcc.s   .eight
.rest:          addq.w  #8,d0
                bra.s   .lnext
.long:          move.l  (a2)+,(a4)+
.lnext:         dbra    d0,.long
                movem.l (sp)+,d2-d7/a3
                rts

; d3 the row the running handler is at, a2 the vector that names it and a3
; the effect's record: a square's by bit 7 of its immediate, a source of
; one row's 0, and every other's its place less its place at row 0, the
; place of every handler that steps one being at TICK_PTR. a2 and d5
; clobbered.
ymxr_runrow:    moveq   #0,d3
                movea.l (a2),a2                 ; the running handler
                move.b  FX_SHAPE(a3),d5
                beq.s   .steps
                bmi.s   .square
                cmp.b   #1,d5
                beq.s   .row
.steps:
                .if     YMXR_PCREL
                move.w  TICK_PTR(a2),d3
                ext.l   d3
                add.l   a2,d3
                add.l   #TICK_PTR,d3
                .else
                move.l  TICK_PTR(a2),d3
                .endif
                sub.l   FX_FIRST(a3),d3
                rts
.square:        tst.b   SQ_VAL(a2)
                bpl.s   .row
                moveq   #1,d3
.row:           rts

; The pages of the bound tune's list (BINARIES.md 1.1, 5.6), each filled
; in its form into the page area at a2, which the host provides (3.1,
; 5.1), and each effect's tables into its record. The area begins with a
; table of T bytes, T the transformations, for each effect and form the
; list names, effect 0's first and each effect's in the order of the
; forms: byte n - 1 the page of transformation n, in 256 bytes from the
; effect's reference, and 0 for one outside the pages filled. FX_LMAPS
; has the address 1 below each table. The pages follow the tables: each
; effect's one after another, then its fallback where a page of it is
; left out, at the least offset past the tables that is its offset
; modulo 256 plus 256 bytes for each page of every effect whose offset
; past the tables modulo 256 is lower. The pages filled are the first k
; of the list, the most of them that fit within the reach of the first
; handler with the fallbacks of the effects of the rest. d0 0, or -1 for
; a version 10 tune without a list, an entry out of range, an area of a
; list of pages that is odd, before the player's end or past the reach,
; where the area is left as it was. a6 the workspace.
ymxr_prefill:   movem.l d1-d7/a0-a6,-(sp)
                movea.l a6,a0                   ; the workspace, as ymxr_tab
                lea     WS_FX(a0),a3            ; reads it
                lea     ymxr_nomap-1(pc),a1
                moveq   #3,d0
.none:          clr.l   FX_LMAP(a3)             ; init's counts
                move.l  a1,FX_LMAPS(a3)
                move.l  a1,FX_LMAPS+4(a3)
                move.l  a1,FX_LMAPS+8(a3)
                clr.b   FX_LFB(a3)
                lea     FX_SIZE(a3),a3
                dbra    d0,.none
                move.l  WS_TRANS(a0),d0
                beq     .ok                     ; versions 7 to 9
                movea.l d0,a1                   ; the block: T, then the list
                moveq   #0,d7                   ; past the index and past each
                move.w  (a1),d7                 ; transformation
                beq     .bad
                move.l  d7,d1
                lsl.l   #2,d1
                addq.l  #2,d1
                move.w  d7,d2
                subq.w  #1,d2
                lea     2(a1),a4
.ends:          move.l  (a4)+,d3
                moveq   #0,d4
                move.w  0(a1,d3.l),d4
                add.l   d3,d4
                addq.l  #2,d4
                cmp.l   d4,d1
                bcc.s   .later
                move.l  d4,d1
.later:         dbra    d2,.ends
                lea     0(a1,d1.l),a5
                cmp.b   #'Y',(a5)+              ; the list's tag
                bne     .bad
                cmp.b   #'P',(a5)+
                bne     .bad
                moveq   #0,d6
                move.b  (a5)+,d6                ; N, then its entries
                beq     .ok
                move.l  a2,d0                   ; the area, even and at or
                btst    #0,d0                   ; past the player's end
                bne     .bad
                lea     ymxr_end(pc),a4
                cmpa.l  a4,a2
                bcs     .bad
; Each entry: the pages of its effect, the effect's last entry plus 1 and
; its forms, and Q, the pairs of an effect and a form.
                lea     WS_FX(a0),a3
                movea.l a5,a4
                moveq   #0,d2
                moveq   #0,d4
.count:         bsr     .entry
                beq     .bad
                mulu    #FX_SIZE,d0
                addq.b  #1,FX_LN(a3,d0.w)
                move.b  d2,FX_LLAST(a3,d0.w)
                addq.b  #1,FX_LLAST(a3,d0.w)
                moveq   #0,d1
                move.b  1(a4),d1
                bset    d1,FX_LFORMS(a3,d0.w)
                bne.s   .known
                addq.w  #1,d4
.known:         addq.l  #3,a4
                addq.w  #1,d2
                cmp.w   d6,d2
                bcs.s   .count
; The pages that fit: QT + 255 + 256 X within 32,767 bytes of the first
; handler, X the pages filled and the fallback pages; k the pages filled,
; N where every page fits, and otherwise the most below N.
                lea     ymxr_tick0(pc),a4
                move.l  a4,d0
                add.l   #32767,d0
                sub.l   a2,d0
                move.w  d4,d1
                mulu    d7,d1
                sub.l   d1,d0
                sub.l   #255,d0
                bmi     .bad
                asr.l   #8,d0
                move.l  d0,d3                   ; X at most
                move.l  d6,d5                   ; k
                cmp.l   d3,d5
                bls.s   .fits
.fewer:         subq.w  #1,d5
                bmi     .bad
                moveq   #0,d0                   ; the effects of an entry at
                cmp.b   WS_FX+FX_LLAST(a0),d5   ; or past k
                bcc.s   .in0
                addq.w  #1,d0
.in0:           cmp.b   WS_FX+FX_SIZE+FX_LLAST(a0),d5
                bcc.s   .in1
                addq.w  #1,d0
.in1:           cmp.b   WS_FX+FX_SIZE+FX_SIZE+FX_LLAST(a0),d5
                bcc.s   .in2
                addq.w  #1,d0
.in2:           cmp.b   WS_FX+FX_SIZE+FX_SIZE+FX_SIZE+FX_LLAST(a0),d5
                bcc.s   .in3
                addq.w  #1,d0
.in3:           mulu    #FB_PAGES,d0
                add.l   d5,d0
                cmp.l   d3,d0
                bhi.s   .fewer
; Each effect's pages in the area: its pages of the first k entries, and
; its fallback where an entry past them is its, marked in FX_LFB.
.fits:          moveq   #3,d0
.zero:          clr.b   FX_LN(a3)
                lea     FX_SIZE(a3),a3
                dbra    d0,.zero
                lea     WS_FX(a0),a3
                movea.l a5,a4
                move.w  d5,d2
                bra.s   .tally
.placed:        moveq   #0,d0
                move.b  (a4),d0
                mulu    #FX_SIZE,d0
                addq.b  #1,FX_LN(a3,d0.w)
                addq.l  #3,a4
.tally:         dbra    d2,.placed
                moveq   #3,d0
.drops:         cmp.b   FX_LLAST(a3),d5
                bcc.s   .whole
                addq.b  #FB_PAGES,FX_LN(a3)
                st      FX_LFB(a3)
.whole:         lea     FX_SIZE(a3),a3
                dbra    d0,.drops
; The tables, each 0 to its end, and their pointers.
                movea.l a2,a1
                lea     WS_FX(a0),a3
                moveq   #3,d0
.tables:        moveq   #0,d1                   ; the form
.form:          btst    d1,FX_LFORMS(a3)
                beq.s   .untabled
                move.w  d1,d2
                add.w   d2,d2
                add.w   d2,d2
                lea     -1(a1),a4
                move.l  a4,FX_LMAPS(a3,d2.w)
                move.w  d7,d2
                subq.w  #1,d2
.clear:         clr.b   (a1)+
                dbra    d2,.clear
.untabled:      addq.w  #1,d1
                cmp.w   #3,d1
                bcs.s   .form
                lea     FX_SIZE(a3),a3
                dbra    d0,.tables
                move.w  d5,d7                   ; k, past the tables
; Each effect's offset past the tables modulo 256, in FX_LRES.
                move.l  a1,d4                   ; the first byte past them
                lea     ymxr_ref(pc),a4
                lea     ymxr_refat(pc),a1
                lea     WS_FX(a0),a3
                moveq   #3,d0
.residue:       move.l  a4,d1
                add.w   (a1)+,d1
                sub.b   d4,d1
                move.b  d1,FX_LRES(a3)
                lea     FX_SIZE(a3),a3
                dbra    d0,.residue
; Each effect's pages, their places in its tables, and its fallback.
                lea     WS_FX(a0),a3
                moveq   #0,d3                   ; the effect
.effect:        tst.b   FX_LN(a3)
                beq     .next
                moveq   #0,d0
                move.b  FX_LRES(a3),d0          ; its offset, and the pages of
                moveq   #0,d1                   ; the effects at lower ones
                cmp.b   WS_FX+FX_LRES(a0),d0
                bls.s   .low0
                add.b   WS_FX+FX_LN(a0),d1
.low0:          cmp.b   WS_FX+FX_SIZE+FX_LRES(a0),d0
                bls.s   .low1
                add.b   WS_FX+FX_SIZE+FX_LN(a0),d1
.low1:          cmp.b   WS_FX+FX_SIZE+FX_SIZE+FX_LRES(a0),d0
                bls.s   .low2
                add.b   WS_FX+FX_SIZE+FX_SIZE+FX_LN(a0),d1
.low2:          cmp.b   WS_FX+FX_SIZE+FX_SIZE+FX_SIZE+FX_LRES(a0),d0
                bls.s   .low3
                add.b   WS_FX+FX_SIZE+FX_SIZE+FX_SIZE+FX_LN(a0),d1
.low3:          lsl.l   #8,d1
                add.l   d4,d1
                add.l   d0,d1
                movea.l d1,a6                   ; its first page
                move.w  d3,d0
                add.w   d0,d0
                lea     ymxr_refat(pc),a2
                move.w  0(a2,d0.w),d0
                lea     ymxr_ref(pc),a2
                adda.w  d0,a2                   ; its reference
                sub.l   a2,d1
                asr.l   #8,d1
                move.b  d1,d5                   ; the first page past it
                movea.l a5,a4
                move.w  d7,d2
                bra.s   .scanned
.scan:          moveq   #0,d0
                move.b  (a4),d0
                cmp.w   d3,d0
                bne.s   .pass
                moveq   #0,d1
                move.b  2(a4),d1                ; its transformation, in the
                moveq   #0,d0                   ; table of its form
                move.b  1(a4),d0
                add.w   d0,d0
                add.w   d0,d0
                movea.l FX_LMAPS(a3,d0.w),a1
                move.b  d5,0(a1,d1.w)           ; the page
                movem.l d2/d5/a4,-(sp)
                moveq   #0,d5
                move.b  1(a4),d5                ; the fill kind
                move.w  d1,d0
                bsr     ymxr_tab
                movea.l a6,a4
                bsr     ymxr_fill
ymxr_filled     equ     *                       ; past a page's copy: the rig's
                movem.l (sp)+,d2/d5/a4          ; boundary
                lea     256(a6),a6
                addq.b  #1,d5
.pass:          addq.l  #3,a4
.scanned:       dbra    d2,.scan
                tst.b   FX_LFB(a3)
                beq.s   .next
                move.b  d5,FX_LFB(a3)           ; its fallback, past its pages
.next:          lea     FX_SIZE(a3),a3
                addq.w  #1,d3
                cmp.w   #4,d3
                bcs     .effect
.ok:            lea     WS_FX(a0),a3
                lea     ymxr_nomap-1(pc),a1
                moveq   #3,d0
.cached:        move.l  a1,FX_LMAP(a3)
                lea     FX_SIZE(a3),a3
                dbra    d0,.cached
                moveq   #0,d0
                movem.l (sp)+,d1-d7/a0-a6
                rts
.bad:           moveq   #-1,d0
                movem.l (sp)+,d1-d7/a0-a6
                rts
; The list's entry at a4: d0 its effect, d1 its transformation, and Z with
; d1 0 where the effect, the form or the transformation is out of range.
.entry:         moveq   #0,d0
                moveq   #0,d1
                move.b  (a4),d0
                cmp.b   #3,d0
                bhi.s   .out
                cmp.b   #2,1(a4)
                bhi.s   .out
                move.b  2(a4),d1
                cmp.w   d7,d1
                bls.s   .in
.out:           moveq   #0,d1
.in:            tst.w   d1
                rts

; Resolve the running shape's lookup kind into d0 and identity kind into
; d1. a3=record, a5=running handler. Lookup selects the target's last
; value, which write order places first for noise, last for
; tone/envelope/buzzer, and in the middle for voice (SPEC.md 1.10.1).
;
; Use the running handler's first register at TICK_SEL, rather than the
; kept target for a later start. R8 to R10 identify noise versus
; tone/envelope; R11 identifies buzzer versus voice (2.1). Applies to
; marked and counted sources.
ymxr_kindof:    moveq   #0,d0
                move.b  FX_SHAPE(a3),d0
                moveq   #0,d1
                move.b  .ident(pc,d0.w),d1
                move.b  .look(pc,d0.w),d0
                cmp.b   #4,d0
                beq.s   .three
                cmp.b   #2,d0                   ; two columns, marked
                beq.s   .two
                cmp.b   #6,d0                   ; or counted
                bne.s   .kind
.two:           cmp.b   #8,TICK_SEL(a5)
                bcs.s   .kind
                cmp.b   #11,TICK_SEL(a5)
                beq.s   .kind
                addq.w  #1,d0                   ; R8 to R10: the lookup first
                rts
.three:         cmp.b   #11,TICK_SEL(a5)
                beq.s   .kind
                moveq   #5,d0
.kind:          rts
.look:          dc.b    0,0,2,4,1,6             ; by FX_SHAPE, 0 to 5
.ident:         dc.b    8,0,10,11,9,12

; a4 the handler of the kind a2 describes, of effect d6, in bank d4. d5
; clobbered.
ymxr_at:        move.w  KD_STEP(a2),d5
                mulu    d6,d5
                add.w   KD_BASE(a2),d5
                lea     ymxr_tick0(pc),a4
                adda.w  d5,a4
                .if     YMXR_DEFER
                tst.w   d4
                beq.s   .bank
                adda.w  #BANK,a4
.bank:
                .endif
                rts

; Stepping-handler kinds: eight lookup kinds in page-byte-table order,
; then five identity kinds.
ymxr_kinds:     KIND    ymxr_lk0,LOOK_STRIDE,1,LK_CELL,0,0,LK_PAGE,0,1,LK_SEL,LK_PTR,0,0,0,0
                KIND    ymxr_lc0,LOOK_STRIDE,1,LC_CELL,LC_LEFT,LC_RELD,LC_PAGE,4,0,LC_SEL,LC_PTR,0,0,0,0
                KIND    ymxr_lt0,LOOK_STRIDE,2,LT_CELLS,0,0,LT_PAGE,2,1,LT_SEL1,LT_PTR1,LT_SEL2,LT_PTR2,0,0
                KIND    ymxr_ln0,LOOK_STRIDE,2,LN_CELLS,0,0,LN_PAGE,2,0,LN_SEL1,LN_PTR1,LN_SEL2,LN_PTR2,0,0
                KIND    ymxr_lb0,LOOK_STRIDE,3,LB_CELLS,0,0,LB_PAGE,3,1,LB_SEL1,LB_PTR1,LB_SEL2,LB_PTR2,LB_SEL3,LB_PTR3
                KIND    ymxr_lv0,LOOK_STRIDE,3,LV_CELLS,0,0,LV_PAGE,3,0,LV_SEL1,LV_PTR1,LV_SEL2,LV_PTR2,LV_SEL3,LV_PTR3
                KIND    ymxr_le0,LOOK_STRIDE,2,LE_CELLS,LE_LEFT,LE_RELD,LE_PAGE,5,0,LE_SEL1,LE_PTR1,LE_SEL2,LE_PTR2,0,0
                KIND    ymxr_lf0,LOOK_STRIDE,2,LF_CELLS,LF_LEFT,LF_RELD,LF_PAGE,5,0,LF_SEL1,LF_PTR1,LF_SEL2,LF_PTR2,0,0
                KIND    ymxr_tick0,TICK_SIZE,1,TICK_CELL,0,0,0,0,0,TICK_SEL,TICK_PTR,0,0,0,0
                KIND    ymxr_cnt0,TICKC_SIZE,1,TICKC_CELL,TICKC_LEFT,TICKC_RELD,0,4,0,TICKC_SEL,TICKC_PTR,0,0,0,0
                KIND    ymxr_two0,TW2_SIZE,2,TW2_CELLS,0,0,0,2,0,TW2_SEL1,TW2_PTR1,TW2_SEL2,TW2_PTR2,0,0
                KIND    ymxr_three0,TW3_SIZE,3,TW3_CELLS,0,0,0,3,0,TW3_SEL1,TW3_PTR1,TW3_SEL2,TW3_PTR2,TW3_SEL3,TW3_PTR3
                KIND    ymxr_env0,TWC_SIZE,2,TWC_CELLS,TWC_LEFT,TWC_RELD,0,5,0,TWC_SEL1,TWC_PTR1,TWC_SEL2,TWC_PTR2,0,0
                .if     YMXR_DEFER
; The hook of each kind in the order above (SPEC.md 4.8): the offset of
; the head of the path that ends a source, and the word a hook writes
; there.
ymxr_hooks:     dc.w    LK_LAST,LK_HOOKW,LC_LAST,LC_HOOKW,LT_LAST,LT_HOOKW
                dc.w    LN_LAST,LN_HOOKW,LB_LAST,LB_HOOKW,LV_LAST,LV_HOOKW
                dc.w    LE_LAST,LE_HOOKW,LF_LAST,LF_HOOKW
                dc.w    TICK_LAST,TICK_HOOKW,TICKC_LAST,TICKC_HOOKW
                dc.w    TW2_LAST,TW2_HOOKW,TW3_LAST,TW3_HOOKW
                dc.w    TWC_LAST,TWC_HOOKW
                .endif

; Each effect's fallback pages, from the fallback's first byte.
ymxr_refat:      dc.w    0,ymxr_ref1-ymxr_ref,ymxr_ref2-ymxr_ref,ymxr_ref3-ymxr_ref
; The table of a fill kind without pages: 0 for every transformation,
; each page to the fallback (ymxr_prefill).
ymxr_nomap:     dcb.b   128,0
                .endif

                .if     YMXR_MAP
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

                HANDLERS
                HANDLEQU
; Lookup-kind offsets relative to effect 0. Assert each read's page
; residue and reach, bank bounds, BANK spacing between banks and
; LOOK_STRIDE spacing between effects.
                LOOKAT1 LK,ymxr_lk0,0
                LOOKAT1 LC,ymxr_lc0,1
                LOOKATW LT,ymxr_lt0,0,0
                LOOKATW LN,ymxr_ln0,0,0
                LOOKATW LB,ymxr_lb0,1,0
                LOOKATW LV,ymxr_lv0,1,0
                LOOKATW LE,ymxr_le0,0,1
                LOOKATW LF,ymxr_lf0,0,1
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
                .endif
                .if     (LOOK_STRIDE<>LB_SIZE)|(LOOK_STRIDE<>LV_SIZE)
                .error  "LOOK_STRIDE is apart from the size of the kinds of three columns"
                .endif
; The first register of each of the thirteen kinds is at TICK_SEL, where
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
                .endif
                LSTRIDE ymxr_lk
                LSTRIDE ymxr_lc
                LSTRIDE ymxr_lt
                LSTRIDE ymxr_ln
                LSTRIDE ymxr_lb
                LSTRIDE ymxr_lv
                LSTRIDE ymxr_le
                LSTRIDE ymxr_lf
                .endif

; The player's last byte, which a page area begins at or past (5.1).
ymxr_end:
