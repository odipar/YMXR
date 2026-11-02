; XPATCH.S - the read of a displacement that the instruction before it
; has patched, measured on the machine the program runs on (README.md
; beside this file).
;
; Each case but Z stores a key into the low byte of the displacement of a
; move.b d16(pc), the read that follows the store, and Z stores it onto a
; spare byte. A case counts which byte the read returns: 'N' through the
; new displacement, 'O' through the old one, anything else X. An
; iteration first writes the old displacement back from d3, set from an
; immediate before the loop, so each iteration starts from the old one.
; The 68000 fetches an instruction's extension word ahead of it, and a
; case reads N where that fetch comes after the store.
;
;   Z   the key stored elsewhere, move.b d16(pc),abs.l onto a spare byte:
;       the read is through the old displacement every time
;   A   move.b d16(pc),abs.l, the read adjacent
;   H   move.b d16(pc),-(An), one nop between it and the read
;   E   move.b d16(pc),-(An), the read adjacent: the read's extension
;       word is fetched before the store, unless an interrupt between the
;       two refills the prefetch
;   I   move.b abs.l,abs.l, the read adjacent: the addressing of the core
;       built with YMXR_PCREL=0
;   P   A's pair onto the PSG: register 0 selected, the read writing the
;       data port and the byte read back through the select port
;
; Each case runs ITER iterations at the interrupt level TOS leaves, 3,
; and then at 4, 5, 6 and 7, and writes a line: its letter and level, the
; counts N, O and X, and its form. On a Mega STE every case runs twice:
; at 8 MHz with the cache off and at 16 MHz with the cache on, $FFFF8E21
; at 0 and then at 3, set back to its first value at the end.
;
; The lines go to the screen and to XPATCH.TXT in the folder the program
; runs from, after a line naming the machine by its _MCH and _CPU cookies
; and one naming the TOS by its header. The program then waits for a key.

GEMDOS          equ     1
Pterm0          equ     0
Cnecin          equ     8
Cconws          equ     9
Cconis          equ     11
Dgetdrv         equ     $19
Super           equ     $20
Fcreate         equ     $3C
Fopen           equ     $3D
Fclose          equ     $3E
Fwrite          equ     $40
Fseek           equ     $42
Dgetpath        equ     $47

ITER            equ     50000                   ; the iterations of a case
SYSBASE         equ     $4F2                    ; the TOS header
COOKIES         equ     $5A0                    ; the cookie jar, or 0
PSG_SELECT      equ     $FFFF8800
PSG_WRITE       equ     $FFFF8802
SPEED           equ     $FFFF8E21               ; a Mega STE's clock and cache
MEGA_STE        equ     $00010010               ; _MCH on a Mega STE

; The bytes the lines are kept in for XPATCH.TXT: on a Mega STE, the
; three opening lines, the two lines that name the clock and the sixty
; lines of the six cases, each of 41 bytes at most with its CR and LF.
KEPT            equ     4096

; ---------------------------------------------------------------- macros

; The start of a case's routine: a0 the low byte of the read's
; displacement, d3 the old displacement, a1 the store, a2 the spare byte.
                .macro  HEAD    L
run\{L}:        lea     inst\{L}+3(pc),a0
                move.b  #old\{L}-inst\{L}-2,d3
                lea     patch\{L}(pc),a1
                lea     spare(pc),a2
                .endm

; The counts cleared, d4 N, d5 O and d6 X, and d7 the iterations.
                .macro  COUNTS
                moveq   #0,d4
                moveq   #0,d5
                moveq   #0,d6
                move.w  #ITER-1,d7
                .endm

; The byte the read returned, in d0, counted, and the bytes the case
; reads: the old byte, the new byte, and the key, the new displacement.
; The two displacements are under 128, so the high byte of the
; displacement is 0 for both and the store of the low byte alone moves
; the read from one to the other.
                .macro  TAIL    L
                cmp.b   #'N',d0
                beq.s   n\{L}
                cmp.b   #'O',d0
                beq.s   o\{L}
                addq.w  #1,d6
                bra.s   x\{L}
n\{L}:          addq.w  #1,d4
                bra.s   x\{L}
o\{L}:          addq.w  #1,d5
x\{L}:          dbra    d7,loop\{L}
                rts
old\{L}:        dc.b    'O'
new\{L}:        dc.b    'N'
key\{L}:        dc.b    new\{L}-inst\{L}-2
                even
                .if     old\{L}-inst\{L}-2>127
                .error  "the old byte is past the reach of the low byte"
                .endc
                .if     new\{L}-inst\{L}-2>127
                .error  "the new byte is past the reach of the low byte"
                .endc
                .endm

; A case at every level, by its routine and its name.
                .macro  CASE    L
                lea     run\{L}(pc),a4
                lea     name\{L}(pc),a5
                bsr     levels
                .endm

; ---------------------------------------------------------------- the program

                .text
start:          clr.l   -(sp)
                move.w  #Super,-(sp)
                trap    #GEMDOS
                addq.l  #6,sp
                lea     oldssp(pc),a0
                move.l  d0,(a0)
                lea     kept(pc),a0
                lea     fill(pc),a1
                move.l  a0,(a1)
                dc.w    $A00A                   ; Line-A: the mouse hidden
                lea     clear(pc),a0
                bsr     screen
                bsr     opening
                move.w  sr,-(sp)
                move.w  #$2700,sr
                move.b  #0,PSG_SELECT.w
                lea     psg(pc),a0
                move.b  PSG_SELECT.w,(a0)       ; register 0, set back after P
                move.w  (sp)+,sr
                move.l  mch(pc),d0
                cmp.l   #MEGA_STE,d0
                beq.s   .mega
                bsr     cases
                bra.s   .ran
.mega:          lea     speed(pc),a0
                move.b  SPEED.w,(a0)
                move.b  #0,SPEED.w
                lea     slow(pc),a0
                bsr     print
                bsr     cases
                move.b  #3,SPEED.w
                lea     fast(pc),a0
                bsr     print
                bsr     cases
                move.b  speed(pc),SPEED.w
.ran:           move.w  sr,-(sp)
                move.w  #$2700,sr
                move.b  #0,PSG_SELECT.w
                move.b  psg(pc),PSG_WRITE.w
                move.w  (sp)+,sr
                move.l  oldssp(pc),-(sp)
                move.w  #Super,-(sp)
                trap    #GEMDOS
                addq.l  #6,sp
                dc.w    $A000                   ; Line-A: a0 its variables
                movea.l 8(a0),a1                ; INTIN
                clr.w   (a1)                    ; 0: the count of hides reset
                dc.w    $A009                   ; Line-A: the mouse shown, for
                                                ; an alert TOS shows on a
                                                ; write-protected disk
                bsr     file
                lea     press(pc),a0
                bsr     screen
.flush:         move.w  #Cconis,-(sp)           ; the keys pressed while the
                trap    #GEMDOS                 ; cases ran, read off
                addq.l  #2,sp
                tst.w   d0
                beq.s   .key
                move.w  #Cnecin,-(sp)
                trap    #GEMDOS
                addq.l  #2,sp
                bra.s   .flush
.key:           move.w  #Cnecin,-(sp)
                trap    #GEMDOS
                addq.l  #2,sp
                dc.w    $A000                   ; Line-A: a0 its variables
                movea.l 8(a0),a1                ; INTIN
                clr.w   (a1)                    ; 0: the count of hides reset
                dc.w    $A009                   ; Line-A: the mouse shown
                move.w  #Pterm0,-(sp)
                trap    #GEMDOS

; The first three lines: the iterations, the machine and the TOS.
opening:        lea     line(pc),a1
                lea     title(pc),a0
                bsr     copy
                move.l  #ITER,d1
                bsr     decimal
                lea     iterations(pc),a0
                bsr     copy
                bsr     ended
                bsr     print
; The cookie jar, which TOS 1.06 and later set up: a cookie is a name and
; a value, and the jar ends at a name of 0.
                moveq   #-1,d6                  ; _MCH, -1 where it is absent
                moveq   #-1,d7                  ; _CPU
                move.l  COOKIES.w,d0
                beq.s   .jar
                movea.l d0,a0
.cookie:        move.l  (a0)+,d0
                beq.s   .jar
                move.l  (a0)+,d1
                cmp.l   #'_MCH',d0
                bne.s   .cpu
                move.l  d1,d6
.cpu:           cmp.l   #'_CPU',d0
                bne.s   .cookie
                move.l  d1,d7
                bra.s   .cookie
.jar:           lea     mch(pc),a0
                move.l  d6,(a0)
                lea     line(pc),a1
                lea     mchis(pc),a0
                bsr     copy
                cmp.l   #-1,d6
                bne.s   .mch
                lea     absent(pc),a0
                bsr     copy
                bra.s   .cpuis
.mch:           move.b  #'$',(a1)+
                move.l  d6,d1
                bsr     hex8
                lea     machines(pc),a2
.machine:       move.l  (a2)+,d0
                move.w  (a2)+,d1
                beq.s   .cpuis                  ; a machine without a name here
                cmp.l   d6,d0
                bne.s   .machine
                move.b  #' ',(a1)+
                lea     machines(pc),a0
                adda.w  d1,a0
                bsr     copy
.cpuis:         lea     cpuis(pc),a0
                bsr     copy
                cmp.l   #-1,d7
                bne.s   .cpu68
                lea     absent(pc),a0
                bsr     copy
                bra.s   .tos
.cpu68:         move.l  d7,d1
                bsr     decimal
.tos:           bsr     ended
                bsr     print
; The TOS: the header of the ROM, at os_beg of the header _sysbase points
; at, its version a word of BCD at 2, and EmuTOS's 'ETOS' at $2C.
                lea     line(pc),a1
                movea.l SYSBASE.w,a3
                movea.l 8(a3),a3
                cmp.l   #'ETOS',$2C(a3)
                bne.s   .atari
                lea     emutos(pc),a0
                bsr     copy
.atari:         lea     tos(pc),a0
                bsr     copy
                move.w  2(a3),d1
                move.w  d1,d0
                lsr.w   #8,d0
                lsr.w   #4,d0
                beq.s   .major
                bsr     nibble
.major:         move.w  d1,d0
                lsr.w   #8,d0
                bsr     nibble
                move.b  #'.',(a1)+
                move.w  d1,d0
                lsr.w   #4,d0
                bsr     nibble
                move.w  d1,d0
                bsr     nibble
                cmp.l   #'ETOS',$2C(a3)
                bne.s   .named
                lea     header(pc),a0
                bsr     copy
.named:         bsr     ended
                bra     print

; The six cases, each at every level.
cases:          CASE    Z
                CASE    A
                CASE    H
                CASE    E
                CASE    I
                CASE    P
                rts

; The case a4 at the level TOS leaves, then at 4 to 7, a line each.
levels:         move.w  sr,d2
                lsr.w   #8,d2
                and.w   #7,d2
                bsr.s   once
                moveq   #4,d2
.up:            move.w  d2,-(sp)
                bsr.s   once
                move.w  (sp)+,d2
                addq.w  #1,d2
                cmp.w   #8,d2
                bcs.s   .up
                rts

; The case a4 at level d2, and its line.
once:           move.w  d2,-(sp)
                move.w  sr,-(sp)
                move.w  sr,d1
                and.w   #$F8FF,d1
                lsl.w   #8,d2
                or.w    d2,d1
                move.w  d1,sr
                jsr     (a4)
                move.w  (sp)+,sr
                move.w  (sp)+,d2
; The line of a case: the letter a5 points at, the level d2, the counts N
; d4, O d5 and X d6, and the form after the letter.
                lea     line(pc),a1
                move.b  (a5),(a1)+
                move.b  d2,d0
                add.b   #'0',d0
                move.b  d0,(a1)+
                move.b  #' ',(a1)+
                move.b  #'N',(a1)+
                move.b  #'=',(a1)+
                move.w  d4,d1
                bsr     dec5
                move.b  #' ',(a1)+
                move.b  #'O',(a1)+
                move.b  #'=',(a1)+
                move.w  d5,d1
                bsr     dec5
                move.b  #' ',(a1)+
                move.b  #'X',(a1)+
                move.b  #'=',(a1)+
                move.w  d6,d1
                bsr     dec5
                move.b  #' ',(a1)+
                lea     1(a5),a0
                bsr     copy
                bsr     ended
                bra     print

; XPATCH.TXT written from the kept lines into the current directory, the
; folder the desktop runs the program from, and a line on the screen
; alone with the file's path and "written", or "not written:" and the
; GEMDOS error or the bytes Fwrite wrote short of the whole.
; The path is the current drive's letter, a colon, Dgetpath's path, and
; the name after a backslash. Where the file exists, the lines are
; appended to it, so runs on several machines from one disk come one
; after another in the file.
file:           move.w  #Dgetdrv,-(sp)
                trap    #GEMDOS
                addq.l  #2,sp
                lea     path(pc),a1
                add.b   #'A',d0
                move.b  d0,(a1)+
                move.b  #':',(a1)+
                clr.w   -(sp)                   ; the current drive
                move.l  a1,-(sp)
                move.w  #Dgetpath,-(sp)
                trap    #GEMDOS
                addq.l  #8,sp
                lea     path+2(pc),a1
.end:           tst.b   (a1)+
                bne.s   .end
                subq.l  #1,a1
                lea     name(pc),a0
                cmpi.b  #92,-1(a1)              ; a path that ends at a
                bne.s   .named                  ; backslash, the root's
                addq.l  #1,a0
.named:         bsr     copy
                move.w  #1,-(sp)                ; for writing
                pea     path(pc)
                move.w  #Fopen,-(sp)
                trap    #GEMDOS
                addq.l  #8,sp
                tst.l   d0
                bmi.s   .create                 ; no such file
                move.w  d0,d7                   ; the handle
                move.w  #2,-(sp)                ; from the end
                move.w  d7,-(sp)
                clr.l   -(sp)
                move.w  #Fseek,-(sp)
                trap    #GEMDOS
                lea     10(sp),sp
                tst.l   d0
                bpl.s   .open
                move.l  d0,d5
                move.w  d7,-(sp)
                move.w  #Fclose,-(sp)
                trap    #GEMDOS
                addq.l  #4,sp
                move.l  d5,d0
                bra.s   .failed
.create:        clr.w   -(sp)
                pea     path(pc)
                move.w  #Fcreate,-(sp)
                trap    #GEMDOS
                addq.l  #8,sp
                tst.l   d0
                bmi.s   .failed
                move.w  d0,d7                   ; the handle
.open:
                lea     kept(pc),a0
                move.l  fill(pc),d6
                sub.l   a0,d6                   ; the bytes kept
                pea     (a0)
                move.l  d6,-(sp)
                move.w  d7,-(sp)
                move.w  #Fwrite,-(sp)
                trap    #GEMDOS
                lea     12(sp),sp
                move.l  d0,d5
                move.w  d7,-(sp)
                move.w  #Fclose,-(sp)
                trap    #GEMDOS
                addq.l  #4,sp
                move.l  d5,d0
                cmp.l   d6,d0
                bne.s   .failed
                dc.w    $A00A                   ; Line-A: the mouse hidden
                lea     line(pc),a1
                lea     path(pc),a0
                bsr     copy
                lea     written(pc),a0
                bsr     copy
                bsr     ended
                bra     screen
; d0 the error, or the bytes Fwrite wrote short of the d6 bytes kept: the
; error as a negative number, or the two counts as "N of M bytes"
.failed:        move.l  d0,d4
                dc.w    $A00A                   ; Line-A: the mouse hidden
                lea     line(pc),a1
                lea     path(pc),a0
                bsr     copy
                lea     unwritten(pc),a0
                bsr     copy
                move.l  d4,d1
                bpl.s   .short
                move.b  #'-',(a1)+
                neg.l   d1
                bsr     decimal
                bra.s   .said
.short:         bsr     decimal
                lea     of(pc),a0
                bsr     copy
                move.l  d6,d1
                bsr     decimal
                lea     bytes(pc),a0
                bsr     copy
.said:          bsr     ended
                bra     screen

; ---------------------------------------------------------------- lines

; a0 a line, to the screen and kept for XPATCH.TXT
print:          movea.l fill(pc),a1
                movea.l a0,a2
.copy:          move.b  (a2)+,(a1)+
                bne.s   .copy
                subq.l  #1,a1
                lea     fill(pc),a2
                move.l  a1,(a2)
; a0 a line, to the screen alone
screen:         move.l  a0,-(sp)
                move.w  #Cconws,-(sp)
                trap    #GEMDOS
                addq.l  #6,sp
                rts

; the string a0 to a1, a1 left on its 0
copy:           move.b  (a0)+,(a1)+
                bne.s   copy
                subq.l  #1,a1
                rts

; CR, LF and the 0 at a1, and a0 the line
ended:          move.b  #13,(a1)+
                move.b  #10,(a1)+
                clr.b   (a1)
                lea     line(pc),a0
                rts

; d1.w as five decimal digits at a1
dec5:           moveq   #0,d2
                move.w  d1,d2
                lea     5(a1),a1
                moveq   #4,d0
.digit:         divu    #10,d2
                swap    d2
                add.b   #'0',d2
                move.b  d2,-(a1)
                clr.w   d2
                swap    d2
                dbra    d0,.digit
                lea     5(a1),a1
                rts

; d1.l, under 655,360, in decimal at a1
decimal:        moveq   #0,d0
.divide:        divu    #10,d1
                swap    d1
                move.w  d1,-(sp)
                clr.w   d1
                swap    d1
                addq.w  #1,d0
                tst.l   d1
                bne.s   .divide
.digit:         move.w  (sp)+,d1
                add.b   #'0',d1
                move.b  d1,(a1)+
                subq.w  #1,d0
                bne.s   .digit
                rts

; d1.l as eight hex digits at a1
hex8:           moveq   #7,d0
.digit:         rol.l   #4,d1
                move.w  d1,d2
                and.w   #15,d2
                move.b  hex(pc,d2.w),(a1)+
                dbra    d0,.digit
                rts
hex:            dc.b    '0123456789ABCDEF'

; the low nibble of d0 as a hex digit at a1
nibble:         and.w   #15,d0
                move.b  hex(pc,d0.w),(a1)+
                rts

; ---------------------------------------------------------------- the cases

; Z: the store's address is the spare byte, so the read's displacement
; keeps the old value.
                HEAD    Z
                move.l  a2,4(a1)
                COUNTS
loopZ:          move.b  d3,(a0)
                nop
                nop
                nop
patchZ:         move.b  keyZ(pc),$12345678
instZ:          move.b  oldZ(pc),d0
                TAIL    Z

; A: move.b d16(pc),abs.l onto the read after it.
                HEAD    A
                move.l  a0,4(a1)
                COUNTS
loopA:          move.b  d3,(a0)
                nop
                nop
                nop
patchA:         move.b  keyA(pc),$12345678
instA:          move.b  oldA(pc),d0
                TAIL    A

; H: move.b d16(pc),-(An), a2 one past the low byte, and a nop between.
                HEAD    H
                COUNTS
loopH:          move.b  d3,(a0)
                lea     instH+4(pc),a2
                nop
                nop
patchH:         move.b  keyH(pc),-(a2)
                nop
instH:          move.b  oldH(pc),d0
                TAIL    H

; E: move.b d16(pc),-(An) onto the read after it.
                HEAD    E
                COUNTS
loopE:          move.b  d3,(a0)
                lea     instE+4(pc),a2
                nop
                nop
patchE:         move.b  keyE(pc),-(a2)
instE:          move.b  oldE(pc),d0
                TAIL    E

; I: move.b abs.l,abs.l, the key's address the source and the low byte
; the destination.
                HEAD    I
                lea     keyI(pc),a3
                move.l  a3,2(a1)
                move.l  a0,6(a1)
                COUNTS
loopI:          move.b  d3,(a0)
                nop
                nop
                nop
patchI:         move.b  $12345678,$12345678
instI:          move.b  oldI(pc),d0
                TAIL    I

; P: A's pair, the read writing the PSG's register 0 and the byte read
; back through the select port. Another register selected between the
; select and the read-back reads as X.
                HEAD    P
                move.l  a0,4(a1)
                COUNTS
loopP:          move.b  d3,(a0)
                nop
                move.b  #0,PSG_SELECT.w
                nop
patchP:         move.b  keyP(pc),$12345678
instP:          move.b  oldP(pc),PSG_WRITE.w
                move.b  PSG_SELECT.w,d0
                TAIL    P

; ---------------------------------------------------------------- data

                .data
clear:          dc.b    27,'E',27,'v',0         ; VT52: the screen cleared,
                                                ; and a long line wrapped
title:          dc.b    'XPATCH, ',0
iterations:     dc.b    ' iterations a case',0
mchis:          dc.b    '_MCH ',0
cpuis:          dc.b    ', _CPU ',0
absent:         dc.b    'absent',0
emutos:         dc.b    'EmuTOS, ',0
tos:            dc.b    'TOS ',0
header:         dc.b    ' in the header',0
slow:           dc.b    '8 MHz, the cache off',13,10,0
fast:           dc.b    '16 MHz, the cache on',13,10,0
name:           dc.b    92,'XPATCH.TXT',0       ; 92 the backslash
written:        dc.b    ' written',0
unwritten:      dc.b    ' not written: ',0
of:             dc.b    ' of ',0
bytes:          dc.b    ' bytes',0
press:          dc.b    'Press a key.',13,10,0
; a case's letter, then its form
nameZ:          dc.b    'Zelsewhere',0
nameA:          dc.b    'Apc,abs.l',0
nameH:          dc.b    'Hpc,-(An) nop',0
nameE:          dc.b    'Epc,-(An)',0
nameI:          dc.b    'Iabs.l,abs.l',0
nameP:          dc.b    'Ppc,abs.l PSG',0
; the machines _MCH names: a value, then the offset of its name from
; machines; the list ends at an offset of 0
                even
machines:       dc.l    $00000000
                dc.w    st-machines
                dc.l    $00010000
                dc.w    ste-machines
                dc.l    $00010001
                dc.w    book-machines
                dc.l    $00010010
                dc.w    megaste-machines
                dc.l    $00020000
                dc.w    tt-machines
                dc.l    $00030000
                dc.w    falcon-machines
                dc.l    0
                dc.w    0
st:             dc.b    'ST',0
ste:            dc.b    'STE',0
book:           dc.b    'ST Book',0
megaste:        dc.b    'Mega STE',0
tt:             dc.b    'TT',0
falcon:         dc.b    'Falcon',0

                .bss
                even
oldssp:         ds.l    1                       ; the stack Super returns
mch:            ds.l    1                       ; _MCH, or -1
fill:           ds.l    1                       ; the end of the kept lines
speed:          ds.b    1                       ; $FFFF8E21 as it was
psg:            ds.b    1                       ; the PSG's register 0 as it was
spare:          ds.b    2                       ; the byte Z stores onto
line:           ds.b    192                     ; a path, and the words around it
path:           ds.b    160                     ; Dgetpath's 128 bytes, and more
kept:           ds.b    KEPT
