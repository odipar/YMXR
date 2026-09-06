; YMXR_prg.S - a TOS program around the player: the machine taken over,
; the tune played on the VBL, the player stopped and the machine handed
; back. Built as it stands it plays FRAMES frames for a run under Hatari,
; painting the background red around each call; built with PLAYING
; defined (rmac -dPLAYING) it paints nothing and plays until a key is
; pressed, or the tune has played once.
;
; The player and the tune are taken in as YMXR.bin and TUNE.YMXR from the
; include path, and the text address is printed first, so that a trace of
; the chip writes can be read against the player's own addresses
; (68k/test/emu/test_ymxr.py, -hatari).

GEMDOS          equ     1
XBIOS           equ     14
Cconws          equ     9
Pterm0          equ     0
Supexec         equ     38
VBL             equ     $70
FRAMES          equ     2000

                .text
start:
                lea     stack_top,sp
                lea     hex(pc),a0
                lea     start(pc),a1
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
; stopped, the tune played, the state put back. The player claims only
; the timers its tune runs, so the rest stand quiet.
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
                lea     tune(pc),a0
                lea     workspace,a1
                jsr     player
                tst.l   d0
                bmi.s   .back
                move.w  #FRAMES,frames_left
                lea     vbl(pc),a0
                move.l  a0,VBL.w
                move.w  #$2300,sr
                .if ^^defined PLAYING
; The keyboard's ACIA, read directly: its interrupt is off with the MFP's
; others, so a byte waits in it. A key pressed is a scan code with bit 7
; clear; a key released has it set and does not stop the tune.
.wait:          btst    #0,$FFFFFC00.w
                beq.s   .played
                move.b  $FFFFFC02.w,d0
                bpl.s   .stop
.played:        tst.w   frames_left
                bne.s   .wait
.stop:
                .else
.wait:          tst.w   frames_left
                bne.s   .wait
                .endif
                move.w  #$2700,sr
                lea     workspace,a0
                jsr     player+8
.back:          lea     saved(pc),a5
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
                move.b  (a5)+,$FFFFFA1D.w
                move.b  #192,$FFFFFA23.w        ; the system's 200 Hz count
                move.w  (sp)+,sr
                rts

; The VBL: one call a frame, FRAMES times, or until the tune has played
; once with PLAYING. The background is red while the call runs and yellow
; at its end, which a trace of the palette writes turns into the call's
; cycles, as YMX's cost.py reads them; a program that plays paints nothing.
vbl:
                movem.l d0-d7/a0-a6,-(sp)
                tst.w   frames_left
                beq.s   .done
                .if ^^defined PLAYING
                lea     workspace,a0
                jsr     player+4
                tst.l   d0
                bpl.s   .done
                clr.w   frames_left             ; the tune has played once
                .else
                move.w  $FFFF8240.w,-(sp)
                move.w  #$700,$FFFF8240.w
                lea     workspace,a0
                jsr     player+4
                move.w  #$770,$FFFF8240.w
                move.w  (sp)+,$FFFF8240.w
                subq.w  #1,frames_left
                .endif
.done:          movem.l (sp)+,d0-d7/a0-a6
                rte

text:           dc.b    "YMXR at $"
hex:            dc.b    "00000000",13,10
                .if ^^defined PLAYING
                dc.b    "a key stops the tune",13,10
                .endif
                dc.b    0
digits:         dc.b    "0123456789ABCDEF"
                .even
player:         .incbin "YMXR.bin"
                .even
tune:           .incbin "TUNE.YMXR"
                .even

                .bss
                .align  4
saved:          ds.b    32
frames_left:    ds.w    1
                .align  4
workspace:      ds.b    40000
stack:          ds.b    4096
stack_top:
