; lookup_ticks.S - the macros of the lookup instances of the tick
; handlers (SPEC.md 1.10), the links init writes into them, the kinds'
; descriptors and the checks of the instances' layout; and the offset of
; an instance's page read and the stride from one effect's to the next.

; Lookup instances of TICK, TICKC and TICKW. A tick writes its source
; key into the next read's low displacement byte; the high byte selects
; an effect page. The read writes the lookup entry. For a marked column,
; page[k + 128] is page[k] with bit 7 set, preserving the marker in N.
; Counted sources end on the place of their last row.
;
; Each kind branches to one halt per effect, through a bra.w after its
; loop that the loop word reaches where the source plays once: the
; general halt, or LHALT under the monitor for lookup-cycle counts. The
; key write's source operand is the place at +8, as in TICK.
                .macro  LKEY    name,ptr,at,page
                .if     YMXR_PCREL
\{name}_\{ptr}: move.b  \{name}_\{at}(pc),($12345678).l ; +2 the place,
                .else                           ; +4 the key's address
\{name}_\{ptr}: move.b  ($12345678).l,($12345678).l ; +2 the place, +6 the
                .endif                          ; key's address
\{name}_key:    move.b  \{name}_key+2(pc),YM_SELECT+2.w ; init writes the page
                .endm

; A column the instance writes as TICKW writes it.
                .macro  LROW    name,ptr,at
                .if     YMXR_PCREL
\{name}_\{ptr}: move.b  \{name}_\{at}(pc),YM_SELECT+2.w
                .else
\{name}_\{ptr}: move.b  ($12345678).l,YM_SELECT+2.w
                .endif
                .endm

; The general and the counted lookup instance of one column.
                .macro  TICKL   name,counted,page,halt,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_row:    move.b  #8,YM_SELECT.w          ; +3: the target register
                LKEY    \name,ptr,last,\page
                .if     \counted
\{name}_cnt:    cmpi.w  #0,($12345678).l        ; +2 the last row's place, +4
                beq.s   \{name}_last            ; the place's address
                .else
                bmi.s   \{name}_last
                .endif
                .if     YMXR_NEST
\{name}_sr:     move.w  #$2500,sr
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
\{name}_loop:   move.w  #0,($12345678).l        ; +2 the loop cell, +4 the
                .else                           ; place's address
\{name}_loop:   move.l  #0,($12345678).l        ; +2 the loop cell, +6 the
                .endif                          ; place's address
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_halt:   bra.w   \halt                   ; a source that plays once
                .if     YMXR_DEFER
\{name}_hook:   bra.w   \inst
                .endif
                .endm

; Multi-column lookup instance. Only column \look of \columns uses a
; transformation; all other columns follow TICKW unchanged (SPEC.md
; 1.10.1).
                .macro  TICKWL  name,columns,counted,look,page,halt,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_sel1:   move.b  #8,YM_SELECT.w          ; +3: the first register
                .if     \look=1
                LKEY    \name,ptr1,last,\page
                .else
                LROW    \name,ptr1,last
                .endif
\{name}_sel2:   move.b  #8,YM_SELECT.w
                .if     \look=2
                LKEY    \name,ptr2,last,\page
                .else
                LROW    \name,ptr2,last
                .endif
                .if     \columns=3
\{name}_sel3:   move.b  #8,YM_SELECT.w
                .if     \look=3
                LKEY    \name,ptr3,last,\page
                .else
                LROW    \name,ptr3,last
                .endif
                .endif
                .if     \counted
\{name}_cnt:    cmpi.w  #0,($12345678).l        ; +2 the last row's place, +4
                beq.s   \{name}_last            ; the place's address
                .else
                bmi.s   \{name}_last
                .endif
                .if     YMXR_NEST
\{name}_sr:     move.w  #$2500,sr
                .endif
                .if     YMXR_PCREL
\{name}_step1:  addq.w  #1,($12345678).l
\{name}_step2:  addq.w  #1,($12345678).l
                .if     \columns=3
\{name}_step3:  addq.w  #1,($12345678).l
                .endif
                .else
\{name}_step1:  addq.l  #1,($12345678).l
\{name}_step2:  addq.l  #1,($12345678).l
                .if     \columns=3
\{name}_step3:  addq.l  #1,($12345678).l
                .endif
                .endif
                COUNT   PERF_ON,PERF_DROP
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_last:
                .if     YMXR_PCREL
\{name}_loop1:  move.w  #0,($12345678).l
\{name}_loop2:  move.w  #0,($12345678).l
                .if     \columns=3
\{name}_loop3:  move.w  #0,($12345678).l
                .endif
                .else
\{name}_loop1:  move.l  #0,($12345678).l
\{name}_loop2:  move.l  #0,($12345678).l
                .if     \columns=3
\{name}_loop3:  move.l  #0,($12345678).l
                .endif
                .endif
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_halt:   bra.w   \halt
                .if     YMXR_DEFER
\{name}_hook:   bra.w   \inst
                .endif
                .if     \columns=3
                .if     \counted=0
                ds.b    LOOK_PAD                ; the stride off 128
                .endif
                .endif
                .endm

; The pad to a residue: the next instance's page read, \key bytes into
; it, has its displacement word on \page modulo 256, so the page byte
; selects a page and the key indexes it.
                .macro  LPAD    key,page
                ds.b    (\page-(*+\key))&255
                .endm

; Halt a lookup instance at the end of a source.
                .macro  LHALT   name,eoi,eoir,ctrl,keep,ier,bit
\name:          andi.b  #\keep,\ctrl.w
                move.b  #255-(1<<\bit),(\ier+4).w
                COUNT   PERF_STOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .endm

; Link the instances of a lookup kind the player has at init, in bank d6,
; a bit an effect in the kind's mask (doc/LINKER.md): each instance's
; address out of the kind's descriptor, \kind in ymxr_kinds; step, loop,
; compare and key addresses patched, and the page byte of page 0
; computed from the instance's address and its effect's reference and
; written into the instance and into the page-byte table at \kind(a4),
; LOOK_KINDS bytes an effect. d0 to d2, a1 to a3 and a5 clobbered.
                .macro  LINKKEY p
                lea     \{p}_KEY(a5),a3
                move.l  a3,\{p}_KEYA(a5)
                lea     \{p}_PAGE(a5),a3
                bsr     ymxr_page0
                .endm

; The head of a loop over a kind's instances: a5 the instance of effect
; d2 where the mask has its bit, and a branch past where it has not.
                .macro  LEACH   kind,mask,next
                btst    d2,\mask+1(pc)
                beq.w   \next
                bsr     ymxr_inst
                .endm

                .macro  LINK1   p,mask,kind,counted,drop
                lea     ymxr_kinds(pc),a1       ; the kind's descriptor: rmac
                adda.w  #\kind*KD_SIZE,a1       ; reads left to right
                lea     \kind(a4),a2
                moveq   #0,d2
.l\~:           LEACH   \kind,\mask,.n\~
                lea     \{p}_PTR(a5),a3
                move.l  a3,\{p}_STEP(a5)
                move.l  a3,\{p}_LOOPW(a5)
                .if     \counted
                lea     \{p}_PTR+PLACE_LO(a5),a3
                move.l  a3,\{p}_CNTA(a5)       ; the place, compared
                .endif
                .if     \drop&YMXR_NEST
                move.l  d3,\{p}_SR(a5)
                .endif
                LINKKEY \p
.n\~:           lea     LOOK_KINDS(a2),a2
                addq.w  #1,d2
                cmp.w   #4,d2
                bne.w   .l\~
                .endm

                .macro  LINKW   p,mask,kind,three,counted
                lea     ymxr_kinds(pc),a1       ; the kind's descriptor: rmac
                adda.w  #\kind*KD_SIZE,a1       ; reads left to right
                lea     \kind(a4),a2
                moveq   #0,d2
.l\~:           LEACH   \kind,\mask,.n\~
                lea     \{p}_PTR1(a5),a3
                move.l  a3,\{p}_STEP1(a5)
                move.l  a3,\{p}_LOOP1W(a5)
                lea     \{p}_PTR2(a5),a3
                move.l  a3,\{p}_STEP2(a5)
                move.l  a3,\{p}_LOOP2W(a5)
                .if     \three
                lea     \{p}_PTR3(a5),a3
                move.l  a3,\{p}_STEP3(a5)
                move.l  a3,\{p}_LOOP3W(a5)
                .endif
                .if     \counted
                lea     \{p}_PTR1+PLACE_LO(a5),a3
                move.l  a3,\{p}_CNT(a5)        ; the first place, compared
                .endif
                LINKKEY \p
.n\~:           lea     LOOK_KINDS(a2),a2
                addq.w  #1,d2
                cmp.w   #4,d2
                bne.w   .l\~
                .endm

; Stepping-handler descriptor: each effect's instance from ymxr_tick0, a
; word an effect, which a link moves as it leaves instances out
; (doc/LINKER.md); column count minus 1, first loop cell, whose loop word
; is the word before it, the last row's place of a counted kind (0 for a
; marked one), the word that halts at the head of the path that ends a
; pass, page byte (0 for identity), shape, fill kind, then register and
; place offsets per column.
KD_AT           equ     0
KD_COLS         equ     8
KD_CELLS        equ     10
KD_LASTP        equ     12
KD_ONCEW        equ     14
KD_PAGE         equ     16
KD_SHAPE        equ     18
KD_FILL         equ     19
KD_SEL          equ     20              ; column 1's, then 2's and 3's, four
KD_PTR          equ     22              ; bytes a column
KD_SIZE         equ     32
                .macro  KIND    first,cols,cells,lastp,page,shape,fill,s1,p1,s2,p2,s3,p3
                dc.w    \{first}0-ymxr_tick0,\{first}1-ymxr_tick0
                dc.w    \{first}2-ymxr_tick0,\{first}3-ymxr_tick0
                dc.w    \cols-1,\cells,\lastp,(\cols-1)*CELL_STRIDE+TICK_ONCEW,\page
                dc.b    \shape,\fill
                dc.w    \s1,\p1,\s2,\p2,\s3,\p3
                .endm

; Offsets within an effect-0 lookup instance, plus assertions for each
; instance.
                .macro  LOOKAT1 p,name,counted
\{p}_SEL        equ     \{name}_row-\name+3
\{p}_PTR        equ     \{name}_ptr-\name+2
\{p}_STEP       equ     \{name}_step-\name+2
\{p}_LOOPW      equ     \{name}_loop-\name+2+CELL_SIZE
\{p}_CELL       equ     \{name}_loop-\name+2
\{p}_LAST       equ     \{name}_mark-\name
\{p}_ONCEW      equ     $6000|(\{name}_halt-\{name}_mark-2)
                .if     \counted
\{p}_LASTP      equ     \{name}_cnt-\name+2
\{p}_CNTA       equ     \{name}_cnt-\name+4
                .endif
                LOOKAT  \p,\name
                .endm

                .macro  LOOKATW p,name,three,counted
\{p}_SEL1       equ     \{name}_sel1-\name+3
\{p}_PTR1       equ     \{name}_ptr1-\name+2
\{p}_STEP1      equ     \{name}_step1-\name+2
\{p}_LOOP1W     equ     \{name}_loop1-\name+2+CELL_SIZE
\{p}_SEL2       equ     \{name}_sel2-\name+3
\{p}_PTR2       equ     \{name}_ptr2-\name+2
\{p}_STEP2      equ     \{name}_step2-\name+2
\{p}_LOOP2W     equ     \{name}_loop2-\name+2+CELL_SIZE
                .if     \three
\{p}_SEL3       equ     \{name}_sel3-\name+3
\{p}_PTR3       equ     \{name}_ptr3-\name+2
\{p}_STEP3      equ     \{name}_step3-\name+2
\{p}_LOOP3W     equ     \{name}_loop3-\name+2+CELL_SIZE
                .endif
\{p}_PTR        equ     \{p}_PTR1
\{p}_CELLS      equ     \{name}_loop1-\name+2
\{p}_LAST       equ     \{name}_last-\name
\{p}_ONCEW      equ     $6000|(\{name}_halt-\{name}_last-2)
                .if     \counted
\{p}_LASTP      equ     \{name}_cnt-\name+2
\{p}_CNT        equ     \{name}_cnt-\name+4
                .endif
                LOOKAT  \p,\name
                .endm

                .macro  LOOKAT  p,name
\{p}_KEY        equ     \{name}_key-\name+3
\{p}_KEYA       equ     \{name}_key-\name-4     ; the key write's destination
\{p}_PAGE       equ     \{name}_key-\name+2
\{p}_SIZE       equ     \{name}_end-\name
                .if     YMXR_NEST
\{p}_SR         equ     \{name}_sr-\name
                .endif
                .if     YMXR_DEFER
\{p}_HOOK       equ     \{name}_hook-\name
                .if     (\{p}_HOOK-\{p}_LAST-2)>127
                .error  "a lookup instance has its hook past a bra.s"
                .endif
                .endif
                .if     \{p}_PTR<>TICK_PTR
                .error  "a lookup instance has its place at another offset than TICK_PTR"
                .endif
                .if     (\{p}_KEY-1)<>KEY_\{p}
                .error  "a lookup instance reads its page at another offset than its KEY_ equate"
                .endif
                .if     \{p}_SIZE>256
                .error  "a lookup instance is larger than a page"
                .endif
                .endm

; An instance's read on its effect's pages modulo 256 and within reach of
; them, and the instance inside its bank.
                .macro  LCHECK  inst,page,lo,hi
                .if     ((\page-(\{inst}_key+2))&255)<>0
                .error  "a lookup instance reads its page off the residue"
                .endif
                .if     (\inst<\lo)|(\{inst}_end>\hi)
                .error  "a lookup instance is outside its bank"
                .endif
                .endm

; Bank 1's instance BANK bytes from its twin in bank 0.
                .macro  LTWIN   inst
                .if     (\{inst}b-\inst)<>BANK
                .error  "a lookup instance of bank 1 is apart from its twin in bank 0"
                .endif
                .endm


                .if     YMXR_MAP
; The displacement word of the page read, from the first byte of an
; instance of each kind: the monitor's mark, the select's write, then the
; writes of the columns before the key's, then the key's write. rmac
; places an instance by these before it has assembled one.
LOOK_MK         equ     10*YMXR_PERF            ; the monitor's mark
LOOK_KW         equ     10-(2*YMXR_PCREL)       ; the key's write
LOOK_RD         equ     8-(2*YMXR_PCREL)        ; a column's read
KEY_LK          equ     LOOK_MK+6+LOOK_KW+2
KEY_LC          equ     LOOK_MK+6+LOOK_KW+2
KEY_LN          equ     LOOK_MK+6+LOOK_KW+2
KEY_LT          equ     LOOK_MK+6+LOOK_RD+6+LOOK_KW+2
KEY_LV          equ     LOOK_MK+6+LOOK_RD+6+LOOK_KW+2
KEY_LE          equ     LOOK_MK+6+LOOK_RD+6+LOOK_KW+2
KEY_LF          equ     LOOK_MK+6+LOOK_KW+2
KEY_LB          equ     LOOK_MK+6+LOOK_RD+6+LOOK_RD+6+LOOK_KW+2
KEY_LG          equ     KEY_LB
KEY_LH          equ     KEY_LV
LOOK_KINDS      equ     10
; The step between the effects' references modulo 256, the size of a
; marked instance of three columns: the gap before each instance puts its
; page read e * LOOK_STRIDE past effect 0's modulo 256, matching effect
; e's pages. A step of 128 puts two effects on one residue, so such an
; instance has two bytes after its last instruction where its code is
; 128 bytes (TICKWL).
LOOK_CODE       equ     96+(4*YMXR_NEST)+(12*(1-YMXR_AEOI))+(42*YMXR_PERF)+(12*(1-YMXR_PCREL))+(4*YMXR_DEFER)
                .if     (LOOK_CODE&127)=0
LOOK_PAD        equ     2
                .else
LOOK_PAD        equ     0
                .endif
LOOK_STRIDE     equ     LOOK_CODE+LOOK_PAD
                .endif
