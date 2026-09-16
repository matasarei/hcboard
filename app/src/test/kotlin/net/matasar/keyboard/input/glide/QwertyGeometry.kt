package net.matasar.keyboard.input.glide

/** The phone letters layer in pixels at density 1: 35 by 42 keys, 6 dp gaps, rows 54 dp apart, home row indented half a key. */
object QwertyGeometry {
    private const val KEY_W = 35f
    private const val KEY_H = 42f
    private const val GAP = 6f
    private const val SIDE = 4f
    private const val ROW_PITCH = 54f
    private val rows = listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f)

    val keys: List<GlideKey> = rows.flatMapIndexed { rowIndex, (letters, offsetUnits) ->
        letters.mapIndexed { i, c ->
            val x = SIDE + (offsetUnits + i) * (KEY_W + GAP) + KEY_W / 2
            val y = rowIndex * ROW_PITCH + KEY_H / 2
            GlideKey(c, x, y, KEY_W, KEY_H)
        }
    }

    fun center(c: Char): GlidePoint = keys.first { it.char == c }.let { GlidePoint(it.centerX, it.centerY) }

    /** A straight-line path through the centres of [letters], with [steps] points per segment. */
    fun path(letters: String, steps: Int = 8): List<GlidePoint> {
        val centres = letters.map { center(it) }
        val points = mutableListOf(centres.first())
        for ((a, b) in centres.zipWithNext()) {
            for (s in 1..steps) {
                val t = s / steps.toFloat()
                points += GlidePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
        }
        return points
    }
}
