package net.matasar.keyboard.input.glide

import kotlin.math.sqrt

/**
 * Decides whether a press that started on a letter key has become a glide, and collects the
 * path once it has. Pure Kotlin: the screen feeds it positions, it answers with a state.
 *
 * A press becomes a glide when the finger has travelled at least three quarters of a key
 * width and is over a different letter key. A press that has not travelled that far by the
 * long-press timeout is abandoned, so accents and the trackpad keep working. A finger that has
 * travelled is never a long press, even over no key at all: crossing the gap between the split
 * board's halves takes a moment.
 */
class GlideGesture(
    private val startKey: Char,
    private val keyWidthPx: Float,
    private val longPressMs: Long,
    private val downTimeMs: Long,
    startX: Float,
    startY: Float,
) {
    enum class State { PENDING, GLIDING, ABANDONED }

    var state: State = State.PENDING
        private set

    private val points = mutableListOf(GlidePoint(startX, startY))

    /** The path so far, in the coordinate space the positions were given in. */
    val path: List<GlidePoint> get() = points

    /** Feeds one more pointer position; [keyUnder] is the letter under it, or null. */
    fun add(x: Float, y: Float, timeMs: Long, keyUnder: Char?): State {
        when (state) {
            State.ABANDONED -> return state
            State.GLIDING -> points += GlidePoint(x, y)
            State.PENDING -> {
                points += GlidePoint(x, y)
                val first = points.first()
                val travelledFar = distance(first.x, first.y, x, y) >= keyWidthPx * START_FRACTION
                val leftStartKey = keyUnder != null && keyUnder != startKey
                state = when {
                    travelledFar && leftStartKey -> State.GLIDING
                    timeMs - downTimeMs > longPressMs && !travelledFar -> State.ABANDONED
                    else -> State.PENDING
                }
            }
        }
        return state
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        /** How far, as a fraction of a key width, the finger must travel before a glide starts. */
        const val START_FRACTION = 0.75f
    }
}
