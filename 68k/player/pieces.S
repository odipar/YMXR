; pieces.S - PIECE and ENDPIECE, the bounds of a piece: bytes a player
; linked for one song leaves out where the song runs no source of the
; piece's kind (doc/LINKER.md). A piece is named for its block and
; numbered within it, as square_1, and its bounds are the labels
; ymxr_p_<name> and ymxr_e_<name>.
;
; Neither macro writes a byte. Where the assembly defines YMXR_PAD_<name>
; (rmac -dYMXR_PAD_square_1=1 -dYMXR_PADSIZE=2), ENDPIECE writes
; YMXR_PADSIZE zero bytes past the piece's last byte: the tool that cuts
; the library assembles the player once with each piece padded, and a
; field whose value moves with the pad refers across the piece's end.
; Both labels are equates of the location counter, so a local label
; before a piece is in scope past it.

                .macro  PIECE   name
ymxr_p_\name    equ     *
                .endm

; A piece of the second bank (deferred.S), the copy of a piece of the
; first: the two keep one layout, so the cut pads both at once and a link
; leaves both out. ymxr_t_<name> is the first one's start.
                .macro  PIECE2  name,twin
ymxr_p_\name    equ     *
ymxr_t_\name    equ     ymxr_p_\twin
                .endm

                .macro  ENDPIECE name
ymxr_e_\name    equ     *
                .if     ^^defined YMXR_PAD_\name
                ds.b    YMXR_PADSIZE
                .endif
                .endm
