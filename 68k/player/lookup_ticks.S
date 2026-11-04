; lookup_ticks.S - the macros of the lookup instances of the tick
; handlers (SPEC.md 1.10), the links init writes into them, the kinds'
; descriptors and the checks of the instances' layout; and the offset of
; an instance's page read and the stride from one effect's to the next.

; Lookup instances of TICK, TICKC and TICKW. A tick writes its source
; key into the next read's low displacement byte; the high byte selects
; an effect page. The read writes the lookup entry. For a marked column,
; page[k + 128] is page[k] with bit 7 set, preserving the marker in N.
; Counted sources end on their counter.
;
; Each kind branches to one halt per effect: the general halt, or LHALT
; under the monitor for lookup-cycle counts. The key write's source
; operand is the place at +8, as in TICK.
                .macro  LKEY    name,ptr,cells,page
                .if     YMXR_PCREL
\{name}_\{ptr}: move.b  \{name}_\{cells}(pc),($12345678).l ; +2 the place,
                .else                           ; +4 the key's address
\{name}_\{ptr}: move.b  ($12345678).l,($12345678).l ; +2 the place, +6 the
                .endif                          ; key's address
\{name}_key:    move.b  \page(pc),YM_SELECT+2.w ; +2 the page, +3 the key
                .endm

; A column the instance writes as TICKW writes it.
                .macro  LROW    name,ptr,cells
                .if     YMXR_PCREL
\{name}_\{ptr}: move.b  \{name}_\{cells}(pc),YM_SELECT+2.w
                .else
\{name}_\{ptr}: move.b  ($12345678).l,YM_SELECT+2.w
                .endif
                .endm

; The general and the counted lookup instance of one column.
                .macro  TICKL   name,counted,page,halt,eoi,eoir,ctrl,keep,ier,bit,colour,inst
\name:
                MARK    \colour
\{name}_row:    move.b  #8,YM_SELECT.w          ; +3: the target register
                LKEY    \name,ptr,cell,\page
                .if     \counted
\{name}_cnt:    subq.w  #1,($12345678).l        ; +2: the rows left
                beq.s   \{name}_last
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
                tst.w   ($12345678).l           ; +2: the loop cell
                beq.w   \halt
\{name}_loop:   move.w  ($12345678).l,($12345678).l ; +2 the cell, +6 the
                .else                           ; place
                tst.l   ($12345678).l
                beq.w   \halt
\{name}_loop:   move.l  ($12345678).l,($12345678).l
                .endif
                .if     \counted
\{name}_reld:   move.w  #1,($12345678).l        ; +2: the rows of a pass, +4:
                .endif                          ; the counter
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_cell:
                .if     YMXR_PCREL
                dc.w    0                       ; the loop cell
                .else
                dc.l    0
                .endif
                .if     \counted
\{name}_left:   dc.w    0                       ; the rows left
                .endif
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
                LKEY    \name,ptr1,cells,\page
                .else
                LROW    \name,ptr1,cells
                .endif
\{name}_sel2:   move.b  #8,YM_SELECT.w
                .if     \look=2
                LKEY    \name,ptr2,cells,\page
                .else
                LROW    \name,ptr2,cells
                .endif
                .if     \columns=3
\{name}_sel3:   move.b  #8,YM_SELECT.w
                .if     \look=3
                LKEY    \name,ptr3,cells,\page
                .else
                LROW    \name,ptr3,cells
                .endif
                .endif
                .if     \counted
\{name}_cnt:    subq.w  #1,($12345678).l        ; +2: the rows left
                beq.s   \{name}_last
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
\{name}_loopt:  tst.w   ($12345678).l
                beq.w   \halt
\{name}_loop1:  move.w  ($12345678).l,($12345678).l
\{name}_loop2:  move.w  ($12345678).l,($12345678).l
                .if     \columns=3
\{name}_loop3:  move.w  ($12345678).l,($12345678).l
                .endif
                .else
\{name}_loopt:  tst.l   ($12345678).l
                beq.w   \halt
\{name}_loop1:  move.l  ($12345678).l,($12345678).l
\{name}_loop2:  move.l  ($12345678).l,($12345678).l
                .if     \columns=3
\{name}_loop3:  move.l  ($12345678).l,($12345678).l
                .endif
                .endif
                .if     \counted
\{name}_reld:   move.w  #1,($12345678).l
                .endif
                COUNT   PERF_LOOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
\{name}_cells:
                .if     YMXR_PCREL
                dc.w    0,0,0
                .else
                dc.l    0,0,0
                .endif
                .if     \counted
\{name}_left:   dc.w    0
                .endif
                .if     YMXR_DEFER
\{name}_hook:   bra.w   \inst
                .endif
                .endm

; The pad to a residue: the next instance's page read, \key bytes into
; it, has its displacement word on \page modulo 256, so the page byte
; selects a page and the key indexes it.
                .macro  LPAD    key,page
                ds.b    (\page-(*+\key))&255
                .endm

; Monitor halt at the marker: one per effect, shared by its eight lookup
; kinds.
                .macro  LHALT   name,eoi,eoir,ctrl,keep,ier,bit
\name:          andi.b  #\keep,\ctrl.w
                move.b  #255-(1<<\bit),(\ier+4).w
                COUNT   PERF_STOP,0
                .if     YMXR_AEOI=0
                move.b  #\eoi,\eoir.w
                .endif
                rte
                .endm

; Link four instances of a lookup kind at init in bank d6. Patch step,
; loop and key addresses, initialize the counter to 1, and select page 0
; from the page-byte table at \kind(a4), eight bytes per effect.
                .macro  LINKKEY p
                lea     \{p}_KEY(a5),a3
                move.l  a3,\{p}_KEYA(a5)
                move.b  (a2),\{p}_PAGE(a5)
                addq.l  #8,a2
                lea     LOOK_STRIDE(a5),a5
                .endm

                .macro  LINK1   p,first,kind,counted
                lea     \first(pc),a5
                adda.w  d6,a5
                lea     \kind(a4),a2
                moveq   #3,d2
.l\~:           lea     \{p}_PTR(a5),a3
                move.l  a3,\{p}_STEP(a5)
                move.l  a3,\{p}_LOOPW(a5)
                lea     \{p}_CELL(a5),a3
                move.l  a3,\{p}_LOOPT(a5)
                move.l  a3,\{p}_LOOPR(a5)
                .if     \counted
                lea     \{p}_LEFT(a5),a3
                move.l  a3,\{p}_CNTA(a5)
                move.l  a3,\{p}_RELDA(a5)
                move.w  #1,\{p}_LEFT(a5)
                .endif
                LINKKEY \p
                dbra    d2,.l\~
                .endm

                .macro  LINKW   p,first,kind,three,counted
                lea     \first(pc),a5
                adda.w  d6,a5
                lea     \kind(a4),a2
                moveq   #3,d2
.l\~:           lea     \{p}_CELLS(a5),a3
                move.l  a3,\{p}_LOOPT(a5)
                move.l  a3,\{p}_LOOP1R(a5)
                addq.l  #CELL_SIZE,a3
                move.l  a3,\{p}_LOOP2R(a5)
                .if     \three
                addq.l  #CELL_SIZE,a3
                move.l  a3,\{p}_LOOP3R(a5)
                .endif
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
                lea     \{p}_LEFT(a5),a3
                move.l  a3,\{p}_CNT(a5)
                move.l  a3,\{p}_RELDA(a5)
                move.w  #1,\{p}_LEFT(a5)
                .endif
                LINKKEY \p
                dbra    d2,.l\~
                .endm

; Stepping-handler descriptor: effect-0 offset from ymxr_tick0, effect
; stride, column count minus 1, first loop cell, remaining/pass counters
; (0 when absent), page byte (0 for identity), shape, fill kind, then
; register and place offsets per column.
KD_BASE         equ     0
KD_STEP         equ     2
KD_COLS         equ     4
KD_CELLS        equ     6
KD_LEFT         equ     8
KD_RELD         equ     10
KD_PAGE         equ     12
KD_SHAPE        equ     14
KD_FILL         equ     15
KD_SEL          equ     16              ; column 1's, then 2's and 3's, four
KD_PTR          equ     18              ; bytes a column
KD_SIZE         equ     28
                .macro  KIND    base,step,cols,cells,left,reld,page,shape,fill,s1,p1,s2,p2,s3,p3
                dc.w    \base-ymxr_tick0,\step,\cols-1,\cells,\left,\reld,\page
                dc.b    \shape,\fill
                dc.w    \s1,\p1,\s2,\p2,\s3,\p3
                .endm

; Offsets within an effect-0 lookup instance, plus assertions for each
; instance.
                .macro  LOOKAT1 p,name,counted
\{p}_SEL        equ     \{name}_row-\name+3
\{p}_PTR        equ     \{name}_ptr-\name+2
\{p}_STEP       equ     \{name}_step-\name+2
\{p}_LOOPT      equ     \{name}_mark-\name+2
\{p}_LOOPR      equ     \{name}_loop-\name+2
\{p}_LOOPW      equ     \{name}_loop-\name+6
\{p}_CELL       equ     \{name}_cell-\name
\{p}_LAST       equ     \{name}_mark-\name
                .if     \counted
\{p}_CNTA       equ     \{name}_cnt-\name+2
\{p}_RELD       equ     \{name}_reld-\name+2
\{p}_RELDA      equ     \{name}_reld-\name+4
\{p}_LEFT       equ     \{name}_left-\name
                .endif
                LOOKAT  \p,\name
                .endm

                .macro  LOOKATW p,name,three,counted
\{p}_SEL1       equ     \{name}_sel1-\name+3
\{p}_PTR1       equ     \{name}_ptr1-\name+2
\{p}_STEP1      equ     \{name}_step1-\name+2
\{p}_LOOP1R     equ     \{name}_loop1-\name+2
\{p}_LOOP1W     equ     \{name}_loop1-\name+6
\{p}_SEL2       equ     \{name}_sel2-\name+3
\{p}_PTR2       equ     \{name}_ptr2-\name+2
\{p}_STEP2      equ     \{name}_step2-\name+2
\{p}_LOOP2R     equ     \{name}_loop2-\name+2
\{p}_LOOP2W     equ     \{name}_loop2-\name+6
                .if     \three
\{p}_SEL3       equ     \{name}_sel3-\name+3
\{p}_PTR3       equ     \{name}_ptr3-\name+2
\{p}_STEP3      equ     \{name}_step3-\name+2
\{p}_LOOP3R     equ     \{name}_loop3-\name+2
\{p}_LOOP3W     equ     \{name}_loop3-\name+6
                .endif
\{p}_PTR        equ     \{p}_PTR1
\{p}_LOOPT      equ     \{name}_loopt-\name+2
\{p}_CELLS      equ     \{name}_cells-\name
\{p}_LAST       equ     \{name}_loopt-\name
                .if     \counted
\{p}_CNT        equ     \{name}_cnt-\name+2
\{p}_RELD       equ     \{name}_reld-\name+2
\{p}_RELDA      equ     \{name}_reld-\name+4
\{p}_LEFT       equ     \{name}_left-\name
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
                .if     \{p}_SIZE>LOOK_STRIDE
                .error  "a lookup instance is larger than LOOK_STRIDE"
                .endif
                .endm

; An instance's read on its effect's pages modulo 256 and within reach of
; them, and the instance inside its bank.
                .macro  LCHECK  inst,page,lo,hi
                .if     ((\page-(\{inst}_key+2))&255)<>0
                .error  "a lookup instance reads its page off the residue"
                .endif
                .if     ((\{inst}_key+2)-\page)>32768
                .error  "a lookup instance is past the reach of its pages"
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

; A kind's four instances LOOK_STRIDE apart.
                .macro  LSTRIDE k
                .if     ((\{k}1-\{k}0)<>LOOK_STRIDE)|((\{k}2-\{k}1)<>LOOK_STRIDE)|((\{k}3-\{k}2)<>LOOK_STRIDE)
                .error  "a lookup kind has its instances apart from LOOK_STRIDE"
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
; Common lookup-instance stride, sized for three columns. Effect e's
; page read is e * LOOK_STRIDE past effect 0's modulo 256, matching that
; effect's pages.
LOOK_STRIDE     equ     114+(4*YMXR_NEST)+(12*(1-YMXR_AEOI))+(42*YMXR_PERF)+(12*(1-YMXR_PCREL))+(4*YMXR_DEFER)
                .endif
