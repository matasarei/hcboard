package net.matasar.keyboard.ime

/**
 * How much of the system's bottom bar, [barPx] tall, still reaches into the input view when the
 * window already leaves [spaceBelowViewPx] under the view (the decor's own padding on stock
 * Android). Never negative: a decor that pads more than the bar needs nothing from us.
 */
internal fun bottomBarOverlap(barPx: Int, spaceBelowViewPx: Int): Int = (barPx - spaceBelowViewPx).coerceAtLeast(0)

/**
 * The bottom bar to clear, in px. The insets the window reports win; when they say nothing and
 * the device is on gesture navigation, the pill is still drawn over the window on some skins
 * (Samsung's One UI reports no inset to the IME), so the system's own navigation-bar height is
 * used instead. Three-button navigation always reports its bar, so it never gets here.
 */
internal fun bottomBarHeight(reportedPx: Int, gestureNavigation: Boolean, systemBarHeightPx: Int): Int =
    if (reportedPx > 0) reportedPx else if (gestureNavigation) systemBarHeightPx.coerceAtLeast(0) else 0
