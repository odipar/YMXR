; YMXR_prg.S - the program stub: a block a tool puts in front of an SNDH
; file to make a TOS program (doc/BINARIES.md). Position-independent; the
; SNDH file begins after the stub's last byte, and the tool patches the
; descriptor:
;
;   +4   'YMXT'
;   +8   the descriptor's version, 1
;   +10  the subtunes, a word
;   +12  flags, a word: bit 0 clears the screen before the banner, which
;        the tool sets where the core has the raster monitor in, so that
;        the bars stand where the desktop's pixels were; bit 1 plays from
;        the VBL, which the tool sets where the set claims Timer C; with
;        it clear the program uses the VBL where the screen's rate is the
;        tune's, and Timer C at the rate where it is not
;   +14  the rate, a word, rows a second
;   +16  the rows to play, a long, 0 to play on until a key stops it
;   +20  the core's offset from the SNDH file's first byte, a long
;
; The machine is claimed under Supexec: the VBL vector, the four
; timers' vectors and the enable, mask and control registers kept, every
; MFP interrupt off and every timer stopped, the core's init called, the
; rows played from the VBL or Timer C, and the state put back once SPACE
; or ESC has stopped the program, or the rows patched in have played. A
; tune that plays its last row stops no program: it goes quiet and the
; keys stand. 1 to 9 pick a subtune, and the banner offers the keys the
; set has, on a separate line since the screen is forty columns. Timer C's
; count is written back as the 192 of the system's 200 Hz, since the
; register reads as the live count and writes as the reload. The SNDH
; file's address is printed first, so that a trace can be read against
; the core's addresses (the rig, 68k/test/emu/test_ymxr.py, -hatari).
;
; The keyboard's ACIA is read directly: its interrupt is off with the
; MFP's others, so every byte the IKBD sends waits in it, and each report
; is read whole so that TOS finds no packet half read when it has the
; keyboard back. A byte of $F6 to $FF opens a report of that many bytes
; more, which are not keys: a status report 7, an absolute mouse 5, a
; relative mouse 2, the time 6, both joysticks 2, one joystick 1. A key
; pressed is a scan code with bit 7 clear; a key released has it set.
;
; The mouse is turned off at the chip while the program runs and set back
; to reporting where it moves before the machine is handed back, and what
; the chip has already sent is read off between the two. So the packets
; TOS's handler would have read are not sent, rather than read
; here and lost.

GEMDOS          equ     1
XBIOS           equ     14
Cconws          equ     9
Pterm0          equ     0
Supexec         equ     38
VBL             equ     $70
SPACE           equ     $39
ESC             equ     $01

stub:           bra.w   main
                dc.b    'YMXT'
                dc.w    1
subtunes:       dc.w    1
flags:          dc.w    0
rate:           dc.w    50
rows:           dc.l    0
core:           dc.l    0

main:           btst    #0,flags+1(pc)
                beq.s   .banner
                pea     clear(pc)               ; the desktop's pixels away,
                move.w  #Cconws,-(sp)           ; so the monitor's bars show
                trap    #GEMDOS
                addq.l  #6,sp
.banner:        lea     hex(pc),a0
                lea     sndh(pc),a1
                lea     digits(pc),a2
                move.l  a1,d0
                moveq   #7,d1
.digit:         rol.l   #4,d0
                move.b  d0,d2
                and.w   #15,d2
                move.b  (a2,d2.w),(a0)+
                dbra    d1,.digit
                pea     text(pc)
                move.w  #Cconws,-(sp)
                trap    #GEMDOS
                addq.l  #6,sp
; The subtunes a key reaches. The keys are 1 to 9, so a set of one has
; none to offer and a larger set offers what it has, nine at most. The
; screen is forty columns, so each line stands alone.
                move.w  subtunes(pc),d0
                cmp.w   #1,d0
                bls.s   .alone
                cmp.w   #9,d0
                bls.s   .most
                moveq   #9,d0
.most:          lea     picks(pc),a0
                add.b   #'0',d0
                move.b  d0,5(a0)                ; the N of "1 to N"
                pea     (a0)
                move.w  #Cconws,-(sp)
                trap    #GEMDOS
                addq.l  #6,sp
.alone:         pea     play(pc)
                move.w  #Supexec,-(sp)
                trap    #XBIOS
                addq.l  #6,sp
                clr.w   -(sp)
                trap    #GEMDOS

; Under Supexec: the machine's state kept, every MFP interrupt and timer
; stopped, the first subtune started, the keys read until the tune is
; over, the state put back.
play:
                move.w  sr,-(sp)
                move.w  #$2700,sr
                lea     saved(pc),a5
                move.l  VBL.w,(a5)+
                move.l  $134.w,(a5)+
                move.l  $120.w,(a5)+
                move.l  $114.w,(a5)+
                move.l  $110.w,(a5)+
                move.b  $FFFFFA07.w,(a5)+
                move.b  $FFFFFA09.w,(a5)+
                move.b  $FFFFFA13.w,(a5)+
                move.b  $FFFFFA15.w,(a5)+
                move.b  $FFFFFA19.w,(a5)+
                move.b  $FFFFFA1B.w,(a5)+
                move.b  $FFFFFA1D.w,(a5)+
                clr.b   $FFFFFA07.w
                clr.b   $FFFFFA09.w
                clr.b   $FFFFFA13.w
                clr.b   $FFFFFA15.w
                clr.b   $FFFFFA19.w
                clr.b   $FFFFFA1B.w
                clr.b   $FFFFFA1D.w
; The keyboard chip is silenced of its mouse: this loop reads the ACIA
; itself, so a mouse packet it reads is one the operating system's
; handler does not, and a packet half read leaves that handler mid
; packet when the program returns.
                moveq   #$12,d0                 ; the mouse off
                bsr     ikbd
                moveq   #1,d0
                bsr     start
                move.w  #$2300,sr
                clr.w   d1                      ; the report's bytes to skip
.wait:          btst    #0,$FFFFFC00.w
                beq.s   .read
                moveq   #0,d0
                move.b  $FFFFFC02.w,d0
                tst.w   d1
                beq.s   .fresh
                subq.w  #1,d1                   ; inside a report
                bra.s   .read
.fresh:         cmp.b   #$F6,d0
                bcs.s   .key
                lea     reports(pc),a0
                sub.b   #$F6,d0
                and.w   #15,d0
                move.b  (a0,d0.w),d1
                bra.s   .read
.key:           tst.b   d0
                bmi.s   .read                   ; a key released
                cmp.b   #SPACE,d0
                beq.s   .stop
                cmp.b   #ESC,d0
                beq.s   .stop
                subq.b  #1,d0                   ; 1 to 9 are $02 to $0A
                beq.s   .read
                cmp.b   #9,d0
                bhi.s   .read
                cmp.w   subtunes(pc),d0
                bhi.s   .read
                move.w  #$2700,sr
                bsr     start
                move.w  #$2300,sr
.read:          move.b  done(pc),d0
                beq.s   .wait
.stop:          move.w  #$2700,sr
                lea     sndh(pc),a0
                jsr     4(a0)                   ; exit
; What the chip has already sent is read off before the mouse wakes, so
; the operating system's handler reads the next packet from its first
; byte.
.drain:         btst    #0,$FFFFFC00.w
                beq.s   .drained
                move.b  $FFFFFC02.w,d0
                bra.s   .drain
.drained:       moveq   #8,d0                   ; the mouse on, reporting
                bsr     ikbd                    ; where it moves, as it was
                lea     saved(pc),a5
                move.l  (a5)+,VBL.w
                move.l  (a5)+,$134.w
                move.l  (a5)+,$120.w
                move.l  (a5)+,$114.w
                move.l  (a5)+,$110.w
                move.b  (a5)+,$FFFFFA07.w
                move.b  (a5)+,$FFFFFA09.w
                move.b  (a5)+,$FFFFFA13.w
                move.b  (a5)+,$FFFFFA15.w
                move.b  (a5)+,$FFFFFA19.w
                move.b  (a5)+,$FFFFFA1B.w
                move.b  #192,$FFFFFA23.w        ; the system's 200 Hz count
                move.b  (a5)+,$FFFFFA1D.w
                move.w  (sp)+,sr
                rts

; One byte to the keyboard chip, d0.b, once its ACIA accepts one.
ikbd:           btst    #1,$FFFFFC00.w
                beq.s   ikbd
                move.b  d0,$FFFFFC02.w
                rts

; d0.w the subtune: the one playing stopped, this one started and its
; clock armed, at level 7. The VBL where the tool asked for it, or where
; the screen refreshes at the tune's rate; Timer C where it does not.
start:
                lea     sndh(pc),a0
                jsr     4(a0)                   ; exit: skipped where none plays
                jsr     (a0)                    ; init
                lea     done(pc),a0
                clr.b   (a0)
                move.l  rows(pc),rows_left-done(a0)
                btst    #1,flags+1(pc)
                bne.s   .vbl
                bsr     screen_hz
                cmp.w   rate(pc),d0
                bne.s   .timer
.vbl:           lea     vbl(pc),a0
                move.l  a0,VBL.w
                rts
.timer:         lea     timer_c(pc),a0          ; Timer C at 200 Hz, and the
                move.l  a0,$114.w               ; rate's share of its ticks
                lea     accumulated(pc),a0
                clr.w   (a0)
                move.b  #192,$FFFFFA23.w
                and.b   #$0F,$FFFFFA1D.w
                or.b    #$50,$FFFFFA1D.w
                bclr    #5,$FFFFFA0D.w
                bset    #5,$FFFFFA09.w
                bset    #5,$FFFFFA15.w
                rts

; d0 the screen's rate: 71 in high resolution, else 50 or 60 by the sync
; bit.
screen_hz:
                moveq   #71,d0
                cmp.b   #2,$FFFF8260.w
                beq.s   .known
                moveq   #60,d0
                btst    #1,$FFFF820A.w
                beq.s   .known
                moveq   #50,d0
.known:         rts

; The VBL: one row a frame.
vbl:
                bsr     tick
                rte

; One row from the core. The rows asked for run out and the program is
; done; a tune that plays its last row does not end the program, and the
; core's play writes no register after that row, so a subtune of a set
; that ends leaves the keys to pick another. Every register kept. A core
; with the raster monitor in paints the call itself (the player,
; doc/performance.md), and this program clears the screen for it.
tick:
                movem.l d0-d7/a0-a6,-(sp)
                move.b  done(pc),d0
                bne.s   .done
                lea     sndh(pc),a1
                jsr     8(a1)
                lea     rows_left(pc),a0
                move.l  (a0),d0
                beq.s   .done                   ; no count asked for
                subq.l  #1,d0
                move.l  d0,(a0)
                bne.s   .done
                lea     done(pc),a0
                st      (a0)
.done:          movem.l (sp)+,d0-d7/a0-a6
                rts

; Timer C, 200 times a second: the rate counted against 200, a row for
; every 200 the count reaches and the remainder carried, so a rate that
; does not divide 200 lands its rows over the second without drift and a
; rate above 200 lands more than one row on a tick. The interrupt is
; ended first and the level lowered so that the effects' timers tick
; through the row; a tick of this timer that lands inside the row adds
; its rate to the count and plays no row, and the row's caller, or the
; next tick, plays what the count has reached. The count is moved in
; single instructions below the level, which a tick cannot split.
timer_c:
                bclr    #5,$FFFFFA11.w
                move.l  a0,-(sp)
                move.w  d0,-(sp)
                lea     accumulated(pc),a0
                move.w  (a0),d0
                add.w   rate(pc),d0
                cmp.w   #200,d0
                bcs.s   .kept
                tas     busy-accumulated(a0)
                bne.s   .kept                   ; inside a row
                move.w  d0,(a0)
                move.w  #$2500,sr
.row:           sub.w   #200,(a0)
                bsr     tick
                cmpi.w  #200,(a0)
                bcc.s   .row
                lea     busy(pc),a0
                clr.b   (a0)
                bra.s   .out
.kept:          move.w  d0,(a0)
.out:           move.w  (sp)+,d0
                movea.l (sp)+,a0
                rte

clear:          dc.b    27,"E",0                ; VT52: the screen cleared
text:           dc.b    "YMXR at $"
hex:            dc.b    "00000000",13,10
                dc.b    "SPACE or ESC ends the program",13,10,0
picks:          dc.b    "1 to 9 pick a subtune",13,10,0
digits:         dc.b    "0123456789ABCDEF"
reports:        dc.b    7,5,2,2,2,2,6,2,1,1     ; the bytes after $F6 to $FF
                .even
saved:          dcb.b   28,0
rows_left:      dc.l    0
accumulated:    dc.w    0
done:           dc.b    0
busy:           dc.b    0
                .even
sndh:
