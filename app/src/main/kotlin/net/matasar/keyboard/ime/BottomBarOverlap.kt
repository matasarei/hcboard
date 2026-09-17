package net.matasar.keyboard.ime

/**
 * How much of the system's bottom bar, [barPx] tall, still reaches into the input view when the
 * window already leaves [spaceBelowViewPx] under the view (the decor's own padding on stock
 * Android). Never negative: a decor that pads more than the bar needs nothing from us.
 */
internal fun bottomBarOverlap(barPx: Int, spaceBelowViewPx: Int): Int = (barPx - spaceBelowViewPx).coerceAtLeast(0)
