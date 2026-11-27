; columns.S - the macros that read a row's column and write it to the
; chip, which the frame (play.S) and EFFECT (effect.S) expand.

; A column into d1, or into d2. The displacement is i strides, patched at
; init: rmac would fold a zero away, so a placeholder is here.
                .macro  READ    col
c\{col}:
                move.b  $7FFF(a1),d1
                .endm

                .macro  READ2   col
c\{col}:
                move.b  $7FFF(a1),d2
                .endm

; A second read of a column, or a column ored into d1, at a separate site
; that init patches from the table of sites (ymxr_sites).
                .macro  QREAD   col
q\{col}:
                move.b  $7FFF(a1),d1
                .endm

; The same, at a column's third site: the mapping column the cut's path
; of an effect reads (effect.S).
                .macro  RREAD   col
r\{col}:
                move.b  $7FFF(a1),d1
                .endm

; Deferred-start count-column site (SPEC.md 4.8). With lookup support,
; the mapping-column site follows. Init patches both from the site
; table.
                .macro  DREAD   col
d\{col}:
                move.b  $7FFF(a1),d1
                .endm

; Remap mapping-column site (SPEC.md 1.10.2), patched by init from the
; site table.
                .macro  MREAD   col
m\{col}:
                move.b  $7FFF(a1),d1
                .endm

                .macro  QOR     col
q\{col}:
                or.b    $7FFF(a1),d1
                .endm

; A column with a set bit, to its register: the column read, and where
; it is set the select put above it and one movep for the pair.
                .macro  WRITE   col,select
                READ    \col
                bpl.s   .no\~
                move.w  #\select,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no\~:
                .endm

; A column that fills its byte, reserving 0: written where it is not 0,
; or where the bit beside it, in d2, says 0 is the value.
                .macro  RESERVED col,bit,select
                READ    \col
                bne.s   .go\~
                btst    #\bit,d2
                beq.s   .no\~
.go\~:          move.w  #\select,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no\~:
                .endm

; A tone period: the coarse column into d2, for its bit 6 and its
; value, then the fine column against it.
                .macro  TONE    fine,coarse,fsel,csel
                READ2   \coarse
                READ    \fine
                bne.s   .go\~
                tst.b   d2                      ; a coarse column of 0 has no
                beq.s   .nc\~                   ; set bit and no bit beside
                btst    #6,d2
                beq.s   .nf\~
.go\~:          move.w  #\fsel,d0
                move.b  d1,d0
                movep.w d0,(a2)
.nf\~:          tst.b   d2
                bpl.s   .nc\~
                move.w  #\csel,d1
                move.b  d2,d1
                movep.w d1,(a2)
.nc\~:
                .endm

; The reads and the writes of the register steps of a frame that copies
; from frame (play.S, ymxr_cregs): each read at a separate site, k_n, which
; init writes as it writes c_n; each write the byte the frame writes to the
; register, kept as its frame value masked to the register's width,
; FV_STRIDE bytes a register from a3, and written to the chip where the
; register's bit of d3, the registers the ticks of copy from frame write,
; is 0, and to the register's immediate where the bit is 1 (SPEC.md
; 3.1.7, 4.4).
                .macro  KREAD   col
k\{col}:
                move.b  $7FFF(a1),d1
                .endm
                .macro  KREAD2  col
k\{col}:
                move.b  $7FFF(a1),d2
                .endm
; The frame value of register \rn, in \dn, to the register's immediate
; (copy.S, ymxr_cptr): the value of the move.l of the handler of copy from
; frame that writes the register, or ymxr_cspare. a5 clobbered.
                .macro  CPUT    rn,dn
                movea.l \rn*4+ymxr_cptr(pc),a5
                move.b  \dn,(a5)
                .endm
                .macro  KWRITE  col,select,rn,mask
                KREAD   \col
                bpl.s   .no\~
                move.b  d1,d0
                and.b   #\mask,d0
                move.b  d0,\rn*FV_STRIDE(a3)
                btst    #\rn,d3
                beq.s   .ch\~
                CPUT    \rn,d0
                bra.s   .no\~
.ch\~:          move.w  #\select,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no\~:
                .endm
                .macro  KRESERVED col,bit,select,rn
                KREAD   \col
                bne.s   .go\~
                btst    #\bit,d2
                beq.s   .no\~
.go\~:          move.b  d1,\rn*FV_STRIDE(a3)
                btst    #\rn,d3
                beq.s   .ch\~
                CPUT    \rn,d1
                bra.s   .no\~
.ch\~:          move.w  #\select,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no\~:
                .endm
                .macro  KTONE   fine,coarse,fsel,csel,fn,cn
                KREAD2  \coarse
                KREAD   \fine
                bne.s   .go\~
                tst.b   d2
                beq.s   .nc\~
                btst    #6,d2
                beq.s   .nf\~
.go\~:          move.b  d1,\fn*FV_STRIDE(a3)
                btst    #\fn,d3
                beq.s   .cf\~
                CPUT    \fn,d1
                bra.s   .nf\~
.cf\~:          move.w  #\fsel,d0
                move.b  d1,d0
                movep.w d0,(a2)
.nf\~:          tst.b   d2
                bpl.s   .nc\~
                move.b  d2,d0
                and.b   #$0F,d0
                move.b  d0,\cn*FV_STRIDE(a3)
                btst    #\cn,d3
                beq.s   .cc\~
                CPUT    \cn,d0
                bra.s   .nc\~
.cc\~:          move.w  #\csel,d1
                move.b  d2,d1
                movep.w d1,(a2)
.nc\~:
                .endm
