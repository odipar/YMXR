; effect.S - EFFECT, the four columns of one effect at the frame, which
; play.S expands once an effect.

; One effect: the target kept, the source started or stopped, the timer's
; count and select written, with the timer's and the place's reset.
                .macro  EFFECT  fx,ct,cs,cc,cn,cm,ctrl,data,ier,bit,keep,shift,shared,tick,sq,one,two,three,vector,lk
; The head is the effect's first read where the tune runs the effect, and
; a branch over its columns where it does not: init writes the one word
; that separates them, and the word after it has the read's
; displacement or the branch's, by which head it is.
\{tick}_over:
q\{ct}:         bra.w   \{tick}_done
                QOR     \cs                     ; the set bits at once
                QOR     \cc
                .if     YMXR_MAP
                QOR     \cm                     ; and the mapping's
                .endif
                bmi.s   \{tick}_some
                QREAD   \cn                     ; the count alone, or no column
                beq.w   \{tick}_done
                move.b  d1,\data.w              ; the count, the timer running on
                move.b  d1,\fx+FX_COUNT(a0)
                bra.w   \{tick}_done
\{tick}_some:
                READ2   \cc
                tst.b   d2                      ; the timer's reset stops the
                bpl.s   \{tick}_kept            ; timer before the source is
\{tick}_rs:     btst    #6,d2                   ; switched, so no tick of the
                beq.s   \{tick}_kept            ; old rate reads the new
\{tick}_rstop:  andi.b  #\keep,\ctrl.w          ; source, and the tick latched
                move.b  #255-(1<<\bit),(\ier+4).w ; before the stop is dropped
\{tick}_kept:
                READ    \ct
                bpl.s   .t\~
                and.b   #$7F,d1
                move.b  d1,\fx+FX_TARGET(a0)
.t\~:           READ    \cs
\{tick}_unset:  bpl.w   \{tick}_rate            ; _retune where a row defers
                and.w   #$7F,d1                 ; a start, and bra.w _check
\{tick}_set:    beq.w   \{tick}_stop            ; below (4.8)
; a start: the source as init resolved it, the handler aimed at its rows
\{tick}_now:
                .if     YMXR_MAP
                bra.w   \{tick}_mapped          ; init aims it at _start in a
\{tick}_start:                                  ; tune that maps no value
                .endif
                lsl.w   #3,d1
                movea.l WS_SOURCES-8(a0,d1.w),a4    ; its first row
                move.l  WS_SOURCES-4(a0,d1.w),d0    ; the loop cell: row RR,
\{tick}_pick:   bmi.w   \{tick}_shaped  ; 0 where it plays once,
                                                    ; -1 for a square and -2
                                                    ; for a source of one row
; the handler's three operands are under one mask, since the timer may
; run: a tick between the target register and the place writes the old
; source's row to the new target, and one between the place and the loop
; cell reads the new place against the cell the old source left
\{tick}_aim:    lea     \tick(pc),a5
; The three behind level 7, so a tick reads none of them half made, and
; the vector's write with them, so a tick due in the window runs the new
; handler. With the window three writes wide, the ticks of a tune running
; one effect kept their period to 0.31 per cent with it and to 0.31
; without, where the reference player's keep 0.33; the vector's write
; adds 16 cycles to it. The place the row does not reset is read in the
; window too, since a tick between the read and the write steps it (1.9).
; Under YMXR_PCREL the arithmetic that turns a row's address into a
; displacement is in the window as well, a dozen instructions more.
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  \fx+FX_TARGET(a0),TICK_SEL(a5)
                move.l  a4,d1                   ; the place: the first row,
                btst    #5,d2                   ; or the row the running
                bne.s   \{tick}_put             ; handler is at, modulo R of
                move.b  \fx+FX_SHAPE(a0),d3     ; this source (1.8.4): every
                beq.s   \{tick}_tickat          ; handler that steps a place
                bpl.s   \{tick}_other           ; keeps it at one offset, a
                .if     YMXR_DEFER              ; square in bit 7 of its
                movea.l \vector.w,a3            ; immediate, and a source of
                .else                           ; one row is at row 0. With
                lea     \sq(pc),a3              ; two banks the running one
                .endif                          ; is the one the vector
                tst.b   SQ_VAL(a3)              ; names
                bpl.s   \{tick}_put
                moveq   #1,d3
                bra.s   \{tick}_putm
\{tick}_other:  cmp.b   #1,d3
                beq.s   \{tick}_put
                movea.l \vector.w,a3
                bra.s   \{tick}_same
\{tick}_tickat:
                .if     YMXR_DEFER|YMXR_MAP
                movea.l \vector.w,a3
                .else
                lea     \tick(pc),a3
                .endif
\{tick}_same:
                .if     YMXR_PCREL
                move.w  TICK_PTR(a3),d3         ; the place, a displacement
                ext.l   d3                      ; from the instruction that
                lea     TICK_PTR(a3),a3         ; reads the row
                add.l   a3,d3
                .else
                move.l  TICK_PTR(a3),d3
                .endif
                sub.l   \fx+FX_FIRST(a0),d3     ; its row number
\{tick}_putm:   move.l  DT_ROWS-DT_PAYLOAD(a4),d4   ; R, out of the source's
                cmp.l   d4,d3                       ; DTX1 header
                bcs.s   .inr\~
                bsr.w   ymxr_rem
.inr\~:         add.l   d3,d1
\{tick}_put:
                .if     YMXR_PCREL
                lea     TICK_PTR(a5),a3
                sub.l   a3,d1
                move.w  d1,TICK_PTR(a5)
                move.l  d0,d3                   ; the loop cell, 0 where the
                beq.s   .once\~                 ; source plays once
                sub.l   a3,d3
.once\~:        move.w  d3,TICK_CELL(a5)
                .else
                move.l  d1,TICK_PTR(a5)
                move.l  d0,TICK_CELL(a5)
                .endif
                move.l  a5,\vector.w            ; a tick due in the window
                move.w  (sp)+,sr                ; runs the new handler
                move.l  a4,\fx+FX_FIRST(a0)
                clr.b   \fx+FX_SHAPE(a0)
; the start placed the source, so the row's place reset is made: a tick
; before the select steps the place on, and the reset below would undo it
                bclr    #5,d2
                bra.w   \{tick}_rate

; A start of a source of several columns (SPEC.md 3.1.3), in the routine
; every effect shares: the effect alone is here, since the branch
; above has a byte's displacement and the routine is further off than
; that. The routine aims the vector at the handler its columns pick, and
; returns Z where the source is one row.
\{tick}_widen:  moveq   #(\fx-WS_FX)/FX_SIZE,d4
                bsr.w   ymxr_widestart
                beq.w   \{tick}_one
                bclr    #5,d2
                bra.w   \{tick}_rate

; The cell separates the two shapes: -1 two rows alternating, -2 one
; row repeating. Init drops this test where the tune uses one of the two
; and not the other, the branch above going straight to that shape.
\{tick}_shaped: addq.l  #1,d0
\{tick}_wide:   bne.s   \{tick}_one
; A square: the two rows into the handler's immediate and the difference
; of the two into the one instruction that moves between them. The row the
; immediate is at is the place, and a start that moves no place leaves
; the half it is in, which bit 7 marks.
\{tick}_square: lea     \sq(pc),a5
                move.b  (a4),d3                 ; the first row
                move.b  1(a4),d4                ; the marker
                move.b  d3,d5
                eor.b   d4,d5
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  \fx+FX_TARGET(a0),SQ_SEL(a5)
                move.b  d5,SQ_MASK(a5)
                btst    #5,d2
                bne.s   .first\~
                .if     YMXR_DEFER|YMXR_MAP
                movea.l \vector.w,a3            ; the running handler: a
                .endif                          ; square at the row of its
                move.b  \fx+FX_SHAPE(a0),d0     ; immediate, a source of one
                beq.s   .tick\~                 ; row at row 0, and every
                bpl.s   .other\~                ; other at its place, whose
                .if     YMXR_DEFER|YMXR_MAP     ; row number modulo 2 is the
                tst.b   SQ_VAL(a3)              ; row of the two (1.8.4).
                .else                           ; With one bank the running
                tst.b   SQ_VAL(a5)              ; square is this one
                .endif
                bmi.s   .marked\~
                bra.s   .first\~
.other\~:       cmp.b   #1,d0
                beq.s   .first\~
                .if     (YMXR_DEFER|YMXR_MAP)=0
                movea.l \vector.w,a3
                .endif
                bra.s   .fromrow\~
.tick\~:
                .if     (YMXR_DEFER|YMXR_MAP)=0
                lea     \tick(pc),a3
                .endif
.fromrow\~:
                .if     YMXR_PCREL
                move.w  TICK_PTR(a3),d0
                ext.l   d0
                add.l   a3,d0
                add.l   #TICK_PTR,d0
                .else
                move.l  TICK_PTR(a3),d0
                .endif
                sub.l   \fx+FX_FIRST(a0),d0
                btst    #0,d0
                bne.s   .marked\~
.first\~:       move.b  d3,SQ_VAL(a5)
                bra.s   .aimed\~
.marked\~:      move.b  d4,SQ_VAL(a5)
.aimed\~:       move.l  a5,\vector.w
                move.w  (sp)+,sr
                move.l  a4,\fx+FX_FIRST(a0)
                st      \fx+FX_SHAPE(a0)
                .if     YMXR_MAP
                move.b  d3,\fx+FX_LEVEL(a0)     ; row 0's level, for the reset
                .endif
                bclr    #5,d2
                bra.w   \{tick}_rate

; A source of one row: the row into the handler's immediate. The
; handler moves no place, so a start resets none.
\{tick}_one:    lea     \one(pc),a5
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  \fx+FX_TARGET(a0),ONE_SEL(a5)
                move.b  (a4),ONE_VAL(a5)
                move.l  a5,\vector.w
                move.w  (sp)+,sr
                move.l  a4,\fx+FX_FIRST(a0)
                move.b  #1,\fx+FX_SHAPE(a0)
                bclr    #5,d2
                bra.w   \{tick}_rate
                .if     YMXR_MAP
; Start with optional lookup support (SPEC.md 1.10.2). Init redirects
; _now here. A set mapping column replaces kept; an unset column reuses
; it. Identity uses the start above; a transformation uses the shared
; lookup-start routine. d1 is the source number.
\{tick}_mapped: move.w  d1,d3
                READ    \cm                     ; the mapping column
                bpl.s   .kept\~
                and.b   #$7F,d1
                move.b  d1,\fx+FX_MAP(a0)
.kept\~:        move.b  \fx+FX_MAP(a0),\fx+FX_RUN(a0)
                bne.s   .look\~
                sf      \fx+FX_LOK(a0)          ; the identity's handler
                move.w  d3,d1
                bra.w   \{tick}_start
.look\~:        lea     \fx(a0),a3              ; the record, the effect's
                lea     \lk(pc),a5              ; instance of the general
                move.b  ymxr_htab+(8*((\fx-WS_FX)/FX_SIZE))(pc),d5 ; lookup kind, the page byte
                move.w  #\vector,d1             ; of its page 0 and the vector
                moveq   #(\fx-WS_FX)/FX_SIZE,d4
                bsr.w   ymxr_lstart
                bclr    #5,d2
                bra.w   \{tick}_rate
; Remap entry (SPEC.md 1.10.2). Init redirects _unset here for a tune
; using lookups. A set mapping column changes current ticks; an unset
; one continues to rate processing.
\{tick}_remapck:
                MREAD   \cm
\{tick}_mexit:  bpl.w   \{tick}_rate            ; _retune where a row defers
                and.w   #$7F,d1                 ; a start (4.8)
; With the cache in force (FX_LOK) and the timer running, a remap between
; two transformations reads its page in the table of the instance's fill
; kind (FX_LMAP) and writes its page byte. Where the table has 0, the
; fallback page the instance does not read is compared with the new one
; and filled where it differs (ymxr_lfill). A stopped timer and the
; mapping it runs keep the mapping alone. Every other remap is
; ymxr_remap's.
\{tick}_remap:  lea     \fx(a0),a3              ; the record and the effect
                moveq   #(\fx-WS_FX)/FX_SIZE,d4
                move.b  \ctrl.w,d0              ; the timer's control register
                tst.b   FX_LOK(a3)
                beq.s   \{tick}_rslow
                move.b  d1,FX_MAP(a3)           ; the kept mapping
                tst.b   d2                      ; the select bits, as they were
                bmi.s   \{tick}_rreset          ; before the reset where the
                                                ; row resets the timer
\{tick}_rrun:   and.b   #255-\keep,d0
                beq.s   \{tick}_remapped        ; a stopped timer
                cmp.b   FX_RUN(a3),d1
                beq.s   \{tick}_remapped        ; the mapping it runs
                tst.b   d1
                beq.s   \{tick}_rid            ; to the identity
                move.b  d1,FX_RUN(a3)
                movea.l FX_LMAP(a3),a5          ; its page in the table of the
                move.b  0(a5,d1.w),d0           ; fill kind, 0 outside it
                beq.s   \{tick}_rmiss
\{tick}_rhigh:  add.b   FX_LHIGH(a3),d0         ; its page byte
                movea.l FX_LBYTE(a3),a5
\{tick}_rpage:  move.b  d0,(a5)                 ; one write, which no tick
\{tick}_remapped:                               ; splits
                bra.w   \{tick}_rate
\{tick}_rreset: btst    #6,d2
                beq.s   \{tick}_rrun
                move.b  d3,d0
                bra.s   \{tick}_rrun
\{tick}_rid:    bsr.w   ymxr_rmove              ; past the tests above
                bra.w   \{tick}_rate
\{tick}_rslow:  bsr.w   ymxr_remap
                bra.w   \{tick}_rate
; Outside the pages: the fallback page of the bank the instance does not
; read, filled where it has another transformation or fill kind, its
; page 2 times the bank plus the page in the bank.
\{tick}_rmiss:  move.b  d1,FX_LKEY(a3)          ; the transformation and the
                move.w  FX_LKEY(a3),d5          ; fill kind
                .if     YMXR_DEFER
                moveq   #0,d4
                move.b  FX_LBANK(a3),d4
                moveq   #1,d0
                sub.b   FX_USE(a3,d4.w),d0
                move.w  d4,d3
                add.w   d3,d3
                add.w   d0,d3
                .else
                moveq   #1,d0
                sub.b   FX_USE(a3),d0
                move.w  d0,d3
                .endif
                add.w   d3,d3                   ; its two bytes
                cmp.w   FX_PAGE(a3,d3.w),d5
                beq.s   \{tick}_rswitch
                bsr.w   ymxr_lfill
\{tick}_rswitch:
                .if     YMXR_DEFER
                move.b  d0,FX_USE(a3,d4.w)
                .else
                move.b  d0,FX_USE(a3)
                .endif
                move.w  d3,d0
                lsr.w   #1,d0
                add.b   FX_LFB(a3),d0           ; past the effect's pages
                bra.s   \{tick}_rhigh
; Timer reset with lookup support. Init redirects the reset test here.
; Preserve the pre-reset control byte in d3 so remap processing reads
; the timer's state before this row: remap precedes retune (SPEC.md
; 1.10.2). A deferred row delays reset until installation, as at _rck
; below.
\{tick}_rmk:    btst    #6,d2
                beq.w   \{tick}_kept
                move.b  \ctrl.w,d3
                .if     YMXR_DEFER
                btst    #3,d2
                bne.w   \{tick}_kept
                .endif
                bra.w   \{tick}_rstop
                .endif
                .if     YMXR_DEFER
; A tune whose rows defer a start (SPEC.md 3.3, 4.8) has init aim three
; branches of each effect here: the timer's reset, which a deferred start
; makes at the switch; a start or a stop, with a start pending or one to
; arm; and a row that leaves the source column unset. Every other tune
; runs the branches the assembler wrote, and the frame of a version 7
; tune.
\{tick}_rck:    btst    #6,d2
                beq.w   \{tick}_kept
                btst    #3,d2
                bne.w   \{tick}_kept
                bra.w   \{tick}_rstop
\{tick}_check:  beq.s   \{tick}_drop            ; source 0, the and's Z: a stop
                tst.b   \fx+FX_PENDING(a0)      ; a start pending on the timer:
                bne.s   \{tick}_pending         ; switched, dropped or replaced
\{tick}_go:     tst.b   d2                      ; a deferred start
                bpl.w   \{tick}_now
                btst    #3,d2
                beq.w   \{tick}_now
; on a running timer it is armed, its rate and the handler it aims at
; written into the installer and the running handler hooked, and the row
; leaves the timer as it is; on a stopped timer it is a start at the
; frame. d1 the source.
\{tick}_defer:  move.w  d1,d3
                .if     YMXR_MAP
                DREAD   \cm                     ; the mapping column, the
                bpl.s   .amap\~                 ; kept mapping where the row
                and.b   #$7F,d1                 ; sets it: the arming reads
                move.b  d1,\fx+FX_MAP(a0)       ; the kept mapping after the
.amap\~:                                        ; row (1.10.3)
                .endif
                DREAD   \cn                     ; the count column
                moveq   #(\fx-WS_FX)/FX_SIZE,d4
                bsr.w   ymxr_arm
                beq.w   \{tick}_done
                bclr    #3,d2                   ; the timer is stopped: d1 the
                bra.w   \{tick}_now             ; source, started at once
; the record follows a switch the tick has made, a start at the frame
; drops the pending start, and a deferred start replaces it
\{tick}_pending:
                moveq   #(\fx-WS_FX)/FX_SIZE,d4
                moveq   #1,d5                   ; dropped
                tst.b   d2
                bpl.s   .pend\~
                btst    #3,d2
                beq.s   .pend\~
                moveq   #0,d5                   ; replaced: the record settled
.pend\~:        bsr.w   ymxr_pend
                bra.s   \{tick}_go
\{tick}_drop:   tst.b   \fx+FX_PENDING(a0)      ; a stop drops a pending start
                beq.w   \{tick}_stop
                moveq   #(\fx-WS_FX)/FX_SIZE,d4
                moveq   #1,d5
                bsr.w   ymxr_pend
                bra.w   \{tick}_stop
; a row that leaves the source column unset leaves a pending start
; pending, and reads the record a switch has made
\{tick}_retune: tst.b   \fx+FX_PENDING(a0)
                beq.w   \{tick}_rate
                moveq   #(\fx-WS_FX)/FX_SIZE,d4
                moveq   #0,d5
                bsr.w   ymxr_pend
                bra.w   \{tick}_rate
                .endif

\{tick}_stop:   andi.b  #\keep,\ctrl.w          ; select 0: the timer stops
                move.b  #255-(1<<\bit),(\ier+4).w ; and drops its latched tick
\{tick}_rate:   READ    \cn
                tst.b   d2
                bpl.s   .live\~
                btst    #6,d2
                beq.s   .set\~
; the timer's reset: stopped above, the count, then the select. Bit 4
; marks a count column of 0 as the value the MFP counts 256 for (1.9), so
; the kept count is used only where the row sets neither.
                tst.b   d1
                bne.s   .cnt\~
                btst    #4,d2
                bne.s   .cnt\~
                move.b  \fx+FX_COUNT(a0),d1
.cnt\~:         move.b  d1,\data.w
                move.b  d1,\fx+FX_COUNT(a0)
                bra.s   .sel\~
.live\~:        tst.b   d1
                beq.s   \{tick}_done
                move.b  d1,\data.w            ; the count, the timer running on
                move.b  d1,\fx+FX_COUNT(a0)
                bra.s   \{tick}_done
.set\~:         tst.b   d1
                bne.s   .put\~
                btst    #4,d2                 ; a count of 0, marked a value
                beq.s   .sel\~
.put\~:         move.b  d1,\data.w
                move.b  d1,\fx+FX_COUNT(a0)
.sel\~:         move.b  d2,d1
                and.b   #7,d1
                move.b  d1,\fx+FX_SELECT(a0)
                .if     \shift
                lsl.b   #\shift,d1
                .endif
; the control register needs one write: a select written to a running
; timer neither stops nor reloads the running count (SPEC.md 1.9), and
; select 0 in front of the new select stops the timer and costs it the
; prescaler's partial count. Timer A's and Timer B's control register is
; the timer's alone, so one move writes it and no interrupt can fall
; inside an instruction; Timers C and D share theirs, and only there
; does the read, the mask and the or run at level 7.
                .if     \shared
                move.w  sr,-(sp)
                ori.w   #$0700,sr
                move.b  \ctrl.w,d0
                and.b   #\keep,d0
                or.b    d1,d0
                move.b  d0,\ctrl.w
                move.w  (sp)+,sr
                .else
                move.b  d1,\ctrl.w
                .endif
                btst    #5,d2                 ; the place's reset, on the
                beq.s   \{tick}_done                ; handler the effect runs
                tst.b   \fx+FX_SHAPE(a0)
                beq.s   \{tick}_place
\{tick}_reset:  bpl.s   \{tick}_done    ; a source of one row is at
                .if     YMXR_DEFER              ; row 0 and moves no place;
                movea.l \vector.w,a5            ; with two banks the square
                .else                           ; is the one the vector
                lea     \sq(pc),a5              ; names
                .endif
                .if     YMXR_MAP
                move.b  \fx+FX_LEVEL(a0),SQ_VAL(a5) ; row 0's level, one
                .else                           ; write, which no tick splits
                movea.l \fx+FX_FIRST(a0),a3
                move.b  (a3),SQ_VAL(a5)
                .endif
                bra.s   \{tick}_done
; The place's reset on a handler of several columns, in the routine every
; effect shares: the effect alone is here, since the branch that
; reaches it has a byte's displacement and the routine is further off.
\{tick}_wrow:   moveq   #(\fx-WS_FX)/FX_SIZE,d3
                bsr.w   ymxr_widereset
                bra.s   \{tick}_done

\{tick}_place:
                .if     YMXR_DEFER|YMXR_MAP
                movea.l \vector.w,a5
                .else
                lea     \tick(pc),a5
                .endif
                .if     YMXR_PCREL
                move.l  \fx+FX_FIRST(a0),d1
                lea     TICK_PTR(a5),a3
                sub.l   a3,d1
                move.w  d1,TICK_PTR(a5)
                .else
                move.l  \fx+FX_FIRST(a0),TICK_PTR(a5)
                .endif
\{tick}_done:
                .endm
