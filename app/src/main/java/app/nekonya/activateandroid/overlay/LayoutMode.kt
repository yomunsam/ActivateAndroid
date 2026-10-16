package app.nekonya.activateandroid.overlay

enum class LayoutMode {
    /** Narrow phone / folded inner often still phone. */
    PHONE,
    /** Fold / tablet / large window (sw >= 600 typically). */
    EXPANDED,
    /** Samsung DeX or desktop UI mode. */
    DEX,
}
