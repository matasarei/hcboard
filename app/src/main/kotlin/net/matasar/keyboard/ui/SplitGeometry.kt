package net.matasar.keyboard.ui

import net.matasar.keyboard.settings.SplitMode

/**
 * Whether the wide board is drawn as two halves. Only a wide board ever splits. Auto splits when a
 * hinge runs down the window, which no key may cover, and on a phone turned sideways, which is
 * held in two hands; an unfolded Fold held flat, or a tablet, stays whole.
 */
fun shouldSplit(mode: SplitMode, wide: Boolean, hingeSeparating: Boolean, phoneLandscape: Boolean): Boolean = when {
    !wide -> false
    mode == SplitMode.OFF -> false
    mode == SplitMode.ALWAYS -> true
    else -> hingeSeparating || phoneLandscape
}

/** A phone on its side: wider than tall, and too short for the screen to be anything but a phone's. */
fun isPhoneLandscape(screenWidthDp: Int, screenHeightDp: Int): Boolean =
    screenWidthDp > screenHeightDp && screenHeightDp < COMPACT_HEIGHT_DP

/** Material's compact height class ends here. */
private const val COMPACT_HEIGHT_DP = 480

/** How wide a half of [units] key units is, at [unit] per key and [gap] between keys. */
fun halfWidth(units: Float, unit: Float, gap: Float): Float = units * (unit + gap) - gap

/**
 * The width of one key unit, gaps excluded (as the whole board counts it), for the two halves of
 * [leftUnits] and [rightUnits] within [width]. Each half sits against its own outer edge, inside
 * [sidePadding]. With a [hinge] (its left and right edge, in the same coordinates as [width]),
 * each half must end [hingeMargin] short of it, so the smaller room decides. Without one, the
 * halves and a gap of [gapUnits] key units share the width. All values in one unit, px or dp.
 */
fun splitUnit(
    width: Float,
    sidePadding: Float,
    gap: Float,
    leftUnits: Float,
    rightUnits: Float,
    hinge: ClosedFloatingPointRange<Float>?,
    hingeMargin: Float,
    gapUnits: Float,
): Float {
    val unit = if (hinge != null) {
        val leftRoom = hinge.start - hingeMargin - sidePadding
        val rightRoom = width - sidePadding - hinge.endInclusive - hingeMargin
        minOf((leftRoom + gap) / leftUnits, (rightRoom + gap) / rightUnits) - gap
    } else {
        // Two halves plus a gap of gapUnits units, which is one more key gap wide than its units.
        (width - 2 * sidePadding + gap) / (leftUnits + rightUnits + gapUnits) - gap
    }
    return unit.coerceAtLeast(0f)
}
