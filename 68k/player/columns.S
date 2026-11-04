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

; The same, at a column's third site: the copy of steps 6 and 7 the row
; that sets no envelope column falls into.
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

; A column with a set bit, written at that third site.
                .macro  RWRITE  col,select
                RREAD   \col
                bpl.s   .no\~
                move.w  #\select,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no\~:
                .endm

                .macro  QOR     col
q\{col}:
                or.b    $7FFF(a1),d1
                .endm

; A column that fills its byte, on the row where the column beside it is
; 0: written where it is not 0, with no further test.
                .macro  PLAIN   col,select
                QREAD   \col
                beq.s   .no\~
                move.w  #\select,d0
                move.b  d1,d0
                movep.w d0,(a2)
.no\~:
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
                btst    #6,d2                   ; btst sets Z alone, so N is
                beq.s   .ns\~                   ; still the coarse column's
.go\~:
                .if     \fsel
                move.w  #\fsel,d0
                .else
                moveq   #0,d0                   ; R0's select
                .endif
                move.b  d1,d0
                movep.w d0,(a2)
                tst.b   d2
.ns\~:          bpl.s   .nc\~
                move.w  #\csel,d1
                move.b  d2,d1
                movep.w d1,(a2)
.nc\~:
                .endm
