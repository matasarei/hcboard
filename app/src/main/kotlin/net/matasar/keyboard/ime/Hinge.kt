package net.matasar.keyboard.ime

/**
 * A hinge's left and right edge in px from the keyboard view's left edge, from its bounds in the
 * window; null when it does not run across the keyboard. A fold that is only a crease has no
 * width, and is still a place no key may sit on.
 */
fun hingeInView(leftInWindow: Int, rightInWindow: Int, viewLeftInWindow: Int, viewWidth: Int): ClosedFloatingPointRange<Float>? {
    val left = (leftInWindow - viewLeftInWindow).toFloat()
    val right = (rightInWindow - viewLeftInWindow).toFloat()
    if (right < 0f || left > viewWidth) return null
    return left..right
}

/** The diagnostics line: where the hinge is, in px from the keyboard's left edge, or that there is none. */
fun hingeLine(hinge: ClosedFloatingPointRange<Float>?, source: String): String =
    "hinge: " + (hinge?.let { "${it.start.toInt()}..${it.endInclusive.toInt()} px" } ?: "none") + " ($source)"
