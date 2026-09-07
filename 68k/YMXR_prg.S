; YMXR_prg.S - the program stub: a block a tool puts in front of an SNDH
; file to make a TOS program (doc/BINARIES.md). Position-independent; the
; SNDH file begins after the stub's last byte, and the tool patches the
; descriptor:
;
;   +4   'YMXT'
;   +8   the descriptor's version, 1
;   +10  the subtunes, a word
;   +12  flags, a word: bit 0 paints the background around each play, for
;        a run under Hatari that reads the palette back; bit 1 plays from
;        the VBL, which the tool sets where the set claims Timer C; with
;        it clear the program takes the VBL where the screen's rate is the
;        tune's, and Timer C at the rate where it is not
;   +14  the rate, a word, rows a second
;   +16  the rows to play, a long, 0 for as many as the tune gives
;   +20  the core's offset from the SNDH file's first byte, a long
;
; The machine is taken over under Supexec: the VBL vector, the four
; timers' vectors and the enable, mask and control registers kept, every
; MFP interrupt off and every timer stopped, the core's init called, the
; rows played from the VBL or Timer C, and the state put back once the
; rows have played, the core's state byte marks the tune over, or a key
; has stopped it. SPACE and ESC stop; 1 to 9 pick a subtune. Timer C's
; count is written back as the 192 of the system's 200 Hz, since the
; register reads as the live count and writes as the reload. The SNDH
; file's address is printed first, so that a trace can be read against
; the core's addresses (the rig, 68k/test/emu/test_ymxr.py, -hatari).
;
; The keyboard's ACIA is read directly: its interrupt is off with the
; MFP's others, so every byte the IKBD sends waits in it, the mouse's
; among them, and each report is taken whole so that TOS finds no packet
; half read when it has the keyboard back. A byte of $F6 to $FF opens a
; report of that many bytes more, which are not keys: a status report 7,
; an absolute mouse 5, a relative mouse 2, the time 6, both joysticks 2,
; one joystick 1. A key pressed is a scan code with bit 7 clear; a key
; released has it set.

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

main:           lea     hex(pc),a0
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
                pea     play(pc)
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

; d0.w the subtune: the one playing stopped, this one started and its
; clock armed, at level 7. The VBL where the tool asked for it, or where
; the screen refreshes at the tune's rate; Timer C where it does not.
start:
                lea     sndh(pc),a0
                jsr     4(a0)                   ; exit: nothing where none plays
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

; One row from the core, and the background red while the call runs and
; yellow at its end where the tool asked for the paint, which a trace of
; the palette writes turns into the call's cycles. The tune is done once
; the core says its last row has played, or the rows asked for have.
; Every register kept.
tick:
                movem.l d0-d7/a0-a6,-(sp)
                move.b  done(pc),d0
                bne.s   .done
                lea     sndh(pc),a1
                btst    #0,flags+1(pc)
                beq.s   .plain
                move.w  $FFFF8240.w,-(sp)
                move.w  #$700,$FFFF8240.w
                jsr     8(a1)
                move.w  #$770,$FFFF8240.w
                move.w  (sp)+,$FFFF8240.w
                bra.s   .played
.plain:         jsr     8(a1)
.played:        adda.l  core(pc),a1
                move.w  22(a1),d0
                btst    #1,(a1,d0.w)
                bne.s   .over
                lea     rows_left(pc),a0
                move.l  (a0),d0
                beq.s   .done
                subq.l  #1,d0
                move.l  d0,(a0)
                bne.s   .done
.over:          lea     done(pc),a0
                st      (a0)
.done:          movem.l (sp)+,d0-d7/a0-a6
                rts

; Timer C, 200 times a second: the rate counted against 200, a row for
; every 200 the count reaches and the remainder carried, so a rate that
; does not divide 200 lands its rows over the second without drift and a
; rate above 200 lands more than one row on a tick. The interrupt is
; ended first and the level lowered so that the effects' timers tick
; through the row; a tick of this timer that lands inside the row keeps
; its count, and the row's caller plays what the count has reached.
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
                move.w  #$2500,sr
.row:           sub.w   #200,d0
                move.w  d0,(a0)
                bsr     tick
                move.w  (a0),d0
                cmp.w   #200,d0
                bcc.s   .row
                lea     busy(pc),a0
                clr.b   (a0)
                bra.s   .out
.kept:          move.w  d0,(a0)
.out:           move.w  (sp)+,d0
                movea.l (sp)+,a0
                rte

text:           dc.b    "YMXR at $"
hex:            dc.b    "00000000",13,10
                dc.b    "SPACE stops the tune, 1 to 9 pick a subtune",13,10,0
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
