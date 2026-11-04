; equates.S - the raster monitor's figures, the offsets of the bound tune,
; the image, a DTX1 table and the workspace, and the addresses of the chip
; and the MFP.

MFP_VECTOR      equ     $FFFFFA17       ; bits 7 to 4 the vector's high half,
                                        ; bit 3 the software end of interrupt

PERF_SYNC       equ     8000            ; the wait's ceiling, over a frame
                                        ; of turns, so a call from
                                        ; anywhere runs on
PERF_FRAME      equ     $700            ; the work of the call itself
PERF_BAR        equ     $770            ; the timers' burnt bar
PERF_COLOUR     equ     $FFFF8240       ; the background
PERF_BEAM       equ     $FFFF8209       ; the video address counter's low
                                        ; byte, which moves while the chip
                                        ; fetches pixels
; What a tick of each kind costs the frame, in cycles, with neither the
; interrupt level dropped nor an end of interrupt written. The raster
; monitor counts a tick at its kind's figure and what the two switches
; add to that path, rounded to the nearest turn of ten cycles, and the
; call burns a bar for the total. performance.md, A tick, measures each
; of the five, and PlayerTest checks the five against those figures.
; Under YMXR_PCREL a row is read through a displacement rather than an
; absolute address: the read costs 4 cycles less, the step 8 and the
; loop's test 4, so the three paths that read a row cost less and the two
; that write a patched value cost what they cost.
                .if     YMXR_PCREL
PERF_ON         equ     64      ; a row written, the place stepped
PERF_SQ         equ     56      ; a square's two rows, no place stepped
PERF_ONEROW     equ     32      ; a source of one row, no place stepped
PERF_LOOP       equ     98      ; the marker, the place to row RR
PERF_STOP       equ     108     ; the marker, the timer stopped
PERF_SWITCH     equ     150     ; the marker, a pending start performed
                .else
PERF_ON         equ     76      ; a row written, the place stepped
PERF_SQ         equ     56      ; a square's two rows, no place stepped
PERF_ONEROW     equ     32      ; a source of one row, no place stepped
PERF_LOOP       equ     114     ; the marker, the place to row RR
PERF_STOP       equ     116     ; the marker, the timer stopped
PERF_SWITCH     equ     150     ; the marker, a pending start performed
                .endif

; What each switch adds to a path: the level is dropped on the three
; paths that write a row's value, and an end of interrupt on all five.
PERF_DROP       equ     16*YMXR_NEST
PERF_END        equ     16*(1-YMXR_AEOI)

; The bound tune (BINARIES.md).
TF_VERSION      equ     4
TF_RATE         equ     6
TF_EFFECTS      equ     8
TF_SOURCES      equ     9
TF_STATE        equ     12
TF_IMAGE        equ     16
TF_TABLE        equ     20              ; where this tune's table is in it
TF_INDEX        equ     24
                .if     YMXR_MAP
TF_TRANS        equ     24              ; version 10: the transformations'
                                        ; block, a signed long from the
                                        ; bound tune's first byte, and the
                                        ; source index 4 bytes on
                .endif

; The image's format block (DTX, abi.md 1), and its slots.
IM_FORMAT       equ     16
IM_VARIANT      equ     19
IM_INIT         equ     0
IM_METADATA     equ     4
IM_ADVANCE      equ     12
IM_POINTER      equ     8               ; the state block's row pointer, which
                                        ; an advance leaves at column 0's value

; A DTX1 table's header, and where a source's rows begin (DTX, SPEC.md 1).
DT_ROWS         equ     4
DT_COLUMNS      equ     8
DT_REPEAT       equ     10
DT_PAYLOAD      equ     16

; The workspace.
WS_TUNE         equ     0               ; the bound tune
WS_IMAGE        equ     4               ; the image in it
WS_ROWS         equ     8               ; rows to the last, or to the wrap
WS_R            equ     12
WS_RR           equ     16
WS_EFFECTS      equ     20              ; a byte: the effects the tune runs
WS_ARMING       equ     22              ; a byte: set while a deferred start
                                        ; is armed, so widestart patches the
                                        ; record's mirror and the other bank,
                                        ; and lstart with the lookup; 1 while
                                        ; lstart fills an arming's page alone
WS_VECTOR       equ     21              ; a byte: the MFP's vector register as
                                        ; init found it, kept where the player
                                        ; runs automatic end of interrupt
WS_SOURCES      equ     24              ; a source's first row and its loop
                                        ; cell, eight bytes each, resolved at
                                        ; init: the source column's seven bits
                                        ; reach 127, so the room is there whether
                                        ; the tune names that many or one. A
                                        ; start indexes it off the workspace
                                        ; with a byte's displacement, so it is
                                        ; first
WS_WIDE         equ     1040            ; a source of several columns: its loop
                                        ; cell and the stride from one column
                                        ; to the next, eight bytes each and one
                                        ; a source, resolved at init beside it
WS_FX           equ     2056            ; four effects, FX_SIZE each. The
                                        ; instances below add FX_SIZE a time
                                        ; rather than multiplying: rmac reads
                                        ; an expression left to right, so
                                        ; WS_FX+FX_SIZE*2 is (WS_FX+FX_SIZE)*2
FX_TARGET       equ     0               ; the target the player keeps
FX_SELECT       equ     1               ; the select it last read
FX_COUNT        equ     2               ; the count it last read
FX_SHAPE        equ     3               ; which handler the timer's vector
                                        ; points at: 0 the general one, $FF
                                        ; the square's, 1 the one-row
                                        ; source's. The last two encode the
                                        ; place as an immediate
FX_FIRST        equ     4               ; the place at row 0: the running
                                        ; source's first row, and the first row
                                        ; of the column the running handler
                                        ; writes first where it writes several
FX_FIRST2       equ     8               ; the same for the second column a
FX_FIRST3       equ     12              ; handler of several writes, and the
                                        ; third
FX_ROWS         equ     16              ; R of the running source, where it
                                        ; has several columns or is counted
FX_PENDING      equ     20              ; a byte: a deferred start is armed
                                        ; and the running handler hooked
FX_HOOKW        equ     22              ; the word the hook wrote over
FX_ARMED        equ     24              ; the handler the armed start aims at
FX_HOOK         equ     28              ; the hooked word's address
ARMOFF          equ     32              ; the mirror: the armed start's target,
                                        ; shape, places at row 0 and R, at
                                        ; the offsets above plus ARMOFF
FX_APLACE       equ     52              ; the row the armed start is at
FX_ABANK        equ     56              ; the bank it is armed in, 0 or BANK
                .if     YMXR_MAP
; Lookup state (SPEC.md 1.10.3): kept mapping for later starts, running
; mapping for current ticks, and a square's row-0 level for place reset.
; Mapping 0 is identity; 1 to 127 select tables. Each bank records the
; fallback page it selects and each fallback page's transformation and
; fill kind: 0 plain, 1 marker mirror, 2 setR7 ports (ymxr_fill).
;
; A start and a remap into a lookup instance cache it: FX_LBYTE the
; address of its page byte, FX_LHIGH the page byte of the effect's first
; fallback page, FX_LBANK its bank and FX_LKEY the fill kind of its
; source, and FX_LOK set where the record describes the running handler
; and no start is pending. A remap between two transformations reads
; FX_LOK alone to use the cache. A start of any other handler and the
; arming of a deferred start clear FX_LOK. FX_LKEY is the transformation
; and the fill kind as one word, its first byte written before each
; compare with a page.
;
; Init writes the effect's tables (ymxr_prefill): FX_LMAPS the address 1
; below the table of each fill kind, whose byte n - 1 is the page of
; transformation n, in 256 bytes from the effect's reference (ymxr_ref),
; or 0 outside the pages init fills; and FX_LFB the page of its first
; fallback page, 0 for an effect without one. A start caches the table of
; its fill kind in FX_LMAP, whose four bytes are init's counts until
; then. FX_LCUR is the page byte a start writes.
                .if     YMXR_DEFER
FX_MAP          equ     21              ; the kept mapping
FX_AMAP         equ     33              ; an armed start's mapping and its
FX_ALEVEL       equ     34              ; square's level at row 0, in the
                                        ; mirror's bytes of FX_SELECT and
                                        ; FX_COUNT, which the arming leaves
                                        ; as they are; the switch copies the
                                        ; two into FX_RUN and FX_LEVEL
FX_LEVEL        equ     58              ; the running square's level at row 0
FX_RUN          equ     59              ; the running source's mapping
FX_USE          equ     60              ; the page in use, bank 0's then 1's
FX_LBANK        equ     62              ; the bank of the cache
FX_PAGE         equ     64              ; two bytes a page, 4 a bank
FX_LBYTE        equ     72              ; the cache: the page byte's address
FX_LKEY         equ     76              ; a transformation, and the fill kind
FX_LHIGH        equ     78              ; the page byte of the first fallback
FX_LOK          equ     79              ; the cache in force
FX_LMAP         equ     80              ; the table of the cache's fill kind
FX_LN           equ     80              ; init's: an effect's pages, their
FX_LRES         equ     81              ; offset modulo 256, their fill
FX_LFORMS       equ     82              ; kinds, and its last entry plus 1
FX_LLAST        equ     83
FX_LMAPS        equ     84              ; the three fill kinds' tables
FX_LCUR         equ     96              ; the page byte a start writes
FX_LFB          equ     97              ; the page of the first fallback
FX_SIZE         equ     100
WS_TRANS        equ     2456            ; the transformations' block, a long
YMXR_FIXED      equ     2460
WS_STATE        equ     2460            ; the image's state block, on a long
                .else
FX_RUN          equ     20
FX_MAP          equ     21
FX_USE          equ     22
FX_LEVEL        equ     23
FX_PAGE         equ     24
FX_LBYTE        equ     28
FX_LKEY         equ     32
FX_LHIGH        equ     34
FX_LOK          equ     35
FX_LMAP         equ     36
FX_LN           equ     36
FX_LRES         equ     37
FX_LFORMS       equ     38
FX_LLAST        equ     39
FX_LMAPS        equ     40
FX_LCUR         equ     52
FX_LFB          equ     53
FX_SIZE         equ     56
WS_TRANS        equ     2280
YMXR_FIXED      equ     2284
WS_STATE        equ     2284
                .endif
                .if     (WS_FX+(4*FX_SIZE))<>WS_TRANS
                .error  "the transformations' long is apart from the records"
                .endif
                .if     FX_LN<>FX_LMAP
                .error  "init's counts are apart from FX_LMAP"
                .endif
                .else
                .if     YMXR_DEFER
FX_SIZE         equ     64
YMXR_FIXED      equ     2312
WS_STATE        equ     2312            ; the image's state block, on a long
                .else
FX_SIZE         equ     20              ; up to FX_ROWS, which a place's
YMXR_FIXED      equ     2136            ; reset on a counted source reads
WS_STATE        equ     2136
                .endif
                .endif

; The chip.
YM_SELECT       equ     $FFFF8800
YM_PORTS        equ     $C0             ; R7's port directions, the ST's

; The MFP: the timers by effect (SPEC.md 2.3).
;                       control   data      vector  IER     bit  keep  shift
; effect 0, Timer A     $FFFA19   $FFFA1F   $134    $FFFA07 5    $F0   0
; effect 1, Timer D     $FFFA1D   $FFFA25   $110    $FFFA09 4    $F0   0
; effect 2, Timer B     $FFFA1B   $FFFA21   $120    $FFFA07 0    $F0   0
; effect 3, Timer C     $FFFA1D   $FFFA23   $114    $FFFA09 5    $0F   4
; IER+4 is the pending register, IER+8 the in-service, IER+12 the mask.
; A pending bit and an in-service bit clear on a written 0 and are unmoved
; by a written 1, so the whole byte is written rather than read and
; written back. bclr's read and its write are two bus cycles, and the MFP
; latches a tick of another timer between them where one comes: the
; write-back would carry the 0 that read had and drop it. The enable and
; the mask are written by software alone, so bclr is used there.

; A tick handler's operands, patched at a start, are at fixed offsets
; into it; the equates are below the handlers, since the raster monitor's
; marks are between the three blocks a handler is made of and the
; assembler measures the distances.
