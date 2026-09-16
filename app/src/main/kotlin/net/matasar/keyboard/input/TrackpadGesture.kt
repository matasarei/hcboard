package net.matasar.keyboard.input

/**
 * Turns horizontal finger travel while holding space into whole cursor steps, keeping the
 * fractional remainder so slow drags still add up.
 */
class TrackpadGesture(private val stepPx: Float) {
    private var carry = 0f

    /** Returns how many characters to move for [dxPx] more travel: negative is left. */
    fun move(dxPx: Float): Int {
        carry += dxPx
        val steps = (carry / stepPx).toInt()
        carry -= steps * stepPx
        return steps
    }

    fun reset() {
        carry = 0f
    }
}
