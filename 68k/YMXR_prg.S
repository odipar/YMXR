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
Getrez          equ     4
Cconws          equ     9
Pterm0          equ     0
Supexec         equ     38
VBL             equ     $70
SPACE           equ     $39
ESC             equ     $01
LEFT            equ     $4B
RIGHT           equ     $4D
UP              equ     $48
DOWN            equ     $50

; The frames a number typed waits for a digit after it, at the tune's
; rate: half a second at 50 Hz. A set of more than nine needs two digits,
; and a digit is the whole number where no second follows it.
PAUSE           equ     25

; The bytes a line is built in, wide enough for the longest line either
; kind makes: a name of LIST_WIDE and the six columns before it, or a
; name of eighty columns less the words before it.
LINE_WIDE       equ     128

; The name a line of the list writes, at most. The list writes a name
; whole, so this is the guard on the buffer and not a width the screen
; fixes.
LIST_WIDE       equ     96

; The columns the line that says which subtune plays leaves a name: the
; screen's, less "playing NN/NN  " and the column an auto-wrap would take.
SAYING_LESS     equ     16

stub:           bra.w   main
                dc.b    'YMXT'
                dc.w    1
subtunes:       dc.w    1
flags:          dc.w    0
rate:           dc.w    50
rows:           dc.l    0
core:           dc.l    0

main:           move.w  #Getrez,-(sp)           ; forty columns in low
                trap    #XBIOS                  ; resolution, eighty in the
                addq.l  #2,sp                   ; other two
                moveq   #80,d1
                tst.w   d0
                bne.s   .wide
                moveq   #40,d1
.wide:          lea     wide(pc),a0
                move.w  d1,(a0)
                btst    #0,flags+1(pc)
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
; The set, one subtune a line: its number and the name the SNDH file's
; '!#SN' tag names it by (BINARIES.md 3). A set of one has no list, since
; the banner names the tune already. The screen is forty columns, so each
; line stands alone.
                move.w  subtunes(pc),d0
                cmp.w   #1,d0
                bls.s   .alone
                pea     picks(pc)
                move.w  #Cconws,-(sp)
                trap    #GEMDOS
                addq.l  #6,sp
                moveq   #0,d7
.one:           move.w  d7,d0
                bsr     entry
                pea     line(pc)
                move.w  #Cconws,-(sp)
                trap    #GEMDOS
                addq.l  #6,sp
                addq.w  #1,d7
                cmp.w   subtunes(pc),d7
                bcs.s   .one
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
                bsr     saying
                clr.w   d1                      ; the report's bytes to skip
.wait:          btst    #0,$FFFFFC00.w
                beq.w   .read
                moveq   #0,d0
                move.b  $FFFFFC02.w,d0
                tst.w   d1
                beq.s   .fresh
                subq.w  #1,d1                   ; inside a report
                bra.w   .read
.fresh:         cmp.b   #$F6,d0
                bcs.s   .key
                lea     reports(pc),a0
                sub.b   #$F6,d0
                and.w   #15,d0
                move.b  (a0,d0.w),d1
                bra.w   .read
.key:           tst.b   d0
                bmi.w   .read                   ; a key released
                cmp.b   #SPACE,d0
                beq.w   .stop
                cmp.b   #ESC,d0
                beq.w   .stop
                cmp.b   #LEFT,d0
                beq.s   .back
                cmp.b   #UP,d0
                beq.s   .back
                cmp.b   #RIGHT,d0
                beq.s   .on
                cmp.b   #DOWN,d0
                beq.s   .on
                subq.b  #1,d0                   ; 1 to 9 are $02 to $0A,
                beq.w   .read                   ; and 0 is $0B
                cmp.b   #10,d0
                bhi.w   .read
                cmp.b   #10,d0
                bne.s   .typed
                moveq   #0,d0
.typed:         bsr     typed
                bra.w   .read
; The arrows walk the set and wrap at each end, so a set of any size is
; reached by them where the nine keys run out.
.back:          move.w  playing(pc),d0
                subq.w  #1,d0
                bne.s   .pick
                move.w  subtunes(pc),d0
                bra.s   .pick
.on:            move.w  playing(pc),d0
                addq.w  #1,d0
                cmp.w   subtunes(pc),d0
                bls.s   .pick
                moveq   #1,d0
.pick:          move.w  #$2700,sr
                bsr     start
                move.w  #$2300,sr
                bsr     saying
.read:          move.w  pending(pc),d0          ; a number typed, and the
                beq.s   .idle                   ; pause after it run out
                move.w  waiting(pc),d0
                bne.s   .idle
                bsr     take
.idle:          move.b  done(pc),d0
                beq.w   .wait
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

; ---------------------------------------------------------- the subtunes

; a0 the SNDH file's '!#SN' tag, or 0 where the file has none. d0 is
; clobbered. The tag
; block runs from 'SNDH' at offset 12 to 'HDNS' (BINARIES.md 3), and
; every tag this reads is at an even address, since the block pads
; before FRMS and the names follow it.
names:
                lea     sndh(pc),a0
                lea     12(a0),a0
                move.w  #2048,d0
.look:          cmpi.l  #'!#SN',(a0)
                beq.s   .found
                cmpi.l  #'HDNS',(a0)
                beq.s   .none
                addq.l  #2,a0
                dbra    d0,.look
.none:          suba.l  a0,a0
.found:         rts

; a0 the name of subtune d0.w, 0 up, or 0 where the file has none to
; give. d0 and d1 are clobbered.
;
; A file that names its subtunes has the '!#SN' tag, and each name's
; offset stands in the word table after it, counted from the tag's first
; byte. A file of one subtune has no such tag, and its title is the name:
; 'TITL' opens the block and its text follows, so the two stand at a
; fixed place (BINARIES.md 3).
name_of:
                move.w  d0,d1
                bsr     names
                move.l  a0,d0
                beq.s   .title
                add.w   d1,d1
                adda.w  4(a0,d1.w),a0
                rts
.title:         lea     sndh(pc),a0
                cmpi.l  #'TITL',16(a0)
                bne.s   .none
                lea     20(a0),a0
                rts
.none:          suba.l  a0,a0
                rts

; d1.b and d2.b the two digits of d0.w, which is 0 to 99. d0 is
; clobbered: the set is at
; most 99 subtunes, the two digits of the '##' tag (BINARIES.md 3).
two:
                ext.l   d0
                divu    #10,d0
                move.w  d0,d1
                add.b   #'0',d1
                swap    d0
                move.w  d0,d2
                add.b   #'0',d2
                swap    d0
                rts

; The name of subtune d0.w, 0 up, into (a1), the columns the screen
; leaves it: a longer name is cut and a shorter one padded, so the line
; covers the one written over it. a1 ends past the last byte. Clobbers
; d0-d3/a0.
padded:
                bsr     name_of
                move.l  a0,d1
                bne.s   .named
                lea     unnamed(pc),a0          ; "subtune", where the file
.named:         move.w  wide(pc),d3             ; names none
                sub.w   #SAYING_LESS,d3
                subq.w  #1,d3
.name:          move.b  (a0)+,d0
                beq.s   .pad
                move.b  d0,(a1)+
                dbra    d3,.name
                rts
.pad:           move.b  #' ',(a1)+
                dbra    d3,.pad
                rts

; The line the list has for subtune d0.w, 0 up, into line: two spaces, its
; number 1 up, two spaces, its name, and a new line.
entry:
                movem.l d0-d4/a0-a1,-(sp)
                move.w  d0,d4                   ; two and padded clobber d0
                lea     line(pc),a1
                move.b  #' ',(a1)+
                move.b  #' ',(a1)+
                move.w  d4,d0
                addq.w  #1,d0
                bsr     two
                move.b  d1,(a1)+
                move.b  d2,(a1)+
                move.b  #' ',(a1)+
                move.b  #' ',(a1)+
                move.w  d4,d0
                bsr     name_of
                move.l  a0,d1
                bne.s   .named
                lea     unnamed(pc),a0
.named:         moveq   #LIST_WIDE-1,d3
.name:          move.b  (a0)+,d0
                beq.s   .ends
                move.b  d0,(a1)+
                dbra    d3,.name
.ends:          move.b  #13,(a1)+
                move.b  #10,(a1)+
                clr.b   (a1)
                movem.l (sp)+,d0-d4/a0-a1
                rts

; The line that says which subtune plays, written over the line before
; it: a carriage return opens it and no new line closes it, so the next
; one stands where this one did. Called under Supexec with the player's
; interrupts on, which write the chip and the MFP and no more.
saying:
                movem.l d0-d3/a0-a2,-(sp)
                lea     line(pc),a1
                move.b  #13,(a1)+
                lea     plays(pc),a0
.said:          move.b  (a0)+,d0
                beq.s   .number
                move.b  d0,(a1)+
                bra.s   .said
.number:        move.w  playing(pc),d0
                bsr     two
                move.b  d1,(a1)+
                move.b  d2,(a1)+
                move.b  #'/',(a1)+
                move.w  subtunes(pc),d0
                bsr     two
                move.b  d1,(a1)+
                move.b  d2,(a1)+
                move.b  #' ',(a1)+
                move.b  #' ',(a1)+
                move.w  playing(pc),d0
                subq.w  #1,d0
                bsr     padded
                clr.b   (a1)
                pea     line(pc)
                move.w  #Cconws,-(sp)
                trap    #GEMDOS
                addq.l  #6,sp
                movem.l (sp)+,d0-d3/a0-a2
                rts

; One digit d0.b, 0 to 9, onto the number typed, and the pause started.
; A digit that would put the number past the set starts it again at that
; digit, so 9 in a set of thirteen is 9 and 1 then 3 is 13. A number no
; second digit can grow reaches the set at once, so 2 in that set starts
; its subtune as the key is read.
typed:
                movem.l d0-d1/a0,-(sp)
                move.w  pending(pc),d1
                mulu    #10,d1
                add.w   d0,d1
                cmp.w   subtunes(pc),d1
                bls.s   .keep
                move.w  d0,d1
.keep:          lea     pending(pc),a0
                move.w  d1,(a0)
                move.w  #PAUSE,waiting-pending(a0)
                mulu    #10,d1
                cmp.w   subtunes(pc),d1
                bls.s   .wait
                clr.w   waiting-pending(a0)
.wait:          movem.l (sp)+,d0-d1/a0
                rts

; The number typed, once the pause after its last digit has run out: the
; subtune it names started, and one outside the set left alone.
take:
                movem.l d0-d1/a0,-(sp)
                move.w  pending(pc),d0
                lea     pending(pc),a0
                clr.w   (a0)
                tst.w   d0
                beq.s   .no
                cmp.w   subtunes(pc),d0
                bhi.s   .no
                move.w  #$2700,sr
                bsr     start
                move.w  #$2300,sr
                bsr     saying
.no:            movem.l (sp)+,d0-d1/a0
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
                lea     playing(pc),a0
                move.w  d0,(a0)
                clr.w   pending-playing(a0)     ; a key that picks a subtune
                clr.w   waiting-playing(a0)     ; drops a number half typed
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
                lea     waiting(pc),a0          ; the pause after a digit,
                tst.w   (a0)                    ; which the key loop reads
                beq.s   .counted
                subq.w  #1,(a0)
.counted:       move.b  done(pc),d0
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
picks:          dc.b    "LEFT and RIGHT pick a subtune",13,10
                dc.b    "or type its number",13,10,13,10,0
plays:          dc.b    "playing ",0
unnamed:        dc.b    "subtune",0
digits:         dc.b    "0123456789ABCDEF"
reports:        dc.b    7,5,2,2,2,2,6,2,1,1     ; the bytes after $F6 to $FF
                .even
saved:          dcb.b   28,0
rows_left:      dc.l    0
accumulated:    dc.w    0
playing:        dc.w    1
pending:        dc.w    0
waiting:        dc.w    0
wide:           dc.w    40
line:           dcb.b   LINE_WIDE,0
done:           dc.b    0
busy:           dc.b    0
                .even
sndh:
