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

; The player's parts are under player/, included below in the order rmac
; assembles them. A file included inside an .if is assembled where that
; switch is set.
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
; lookups, expand them after the lookup routines below. This keeps
; handlers, installers and padding together from ymxr_tick0 for reach
; measurement. Offsets follow their labels because rmac requires prior
; definitions.
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
                .include "player/lookup_start.S"
                .include "player/lookup_remap.S"
                .include "player/lookup_pages.S"
                .include "player/lookup_prefill.S"
                .include "player/lookup_kinds.S"
                .include "player/lookup_ref.S"
                HANDLERS
                HANDLEQU
                .include "player/lookup_checks.S"
                .endif

; The player's last byte, which a page area begins at or past (5.1).
ymxr_end:
