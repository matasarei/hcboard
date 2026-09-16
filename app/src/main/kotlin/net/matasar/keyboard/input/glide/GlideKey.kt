package net.matasar.keyboard.input.glide

/** A letter key's geometry as the classifier sees it: centre and size, in the same pixel space as the gesture. */
data class GlideKey(
    val char: Char,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
)

/** One sampled pointer position, in the same pixel space as the keys. */
data class GlidePoint(val x: Float, val y: Float)
