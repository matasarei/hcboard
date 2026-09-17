package net.matasar.keyboard.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The metrics from the mocks. Heights scale with the height setting; widths come from the row. */
object Dimens {
    val keyHeight = 42.dp
    val keyGap = 6.dp
    val rowGap = 12.dp
    val sidePadding = 4.dp
    val topPadding = 8.dp
    val bottomPadding = 12.dp
    val keyRadius = 10.dp
    val toolbarHeight = 44.dp
    val letterSize = 22.sp
    val compactLetterSize = 17.sp
    val compactKeyHeight = 40.dp
    /** Letters on the 60% board, whose keys keep their top line for legends; small enough to clear it on a 40 dp key. */
    val wideLetterSize = 19.sp
    val labelSize = 12.sp
    val legendSize = 9.sp
    val topLegendSize = 10.sp
    /** Labels on the developer strip, whose keys are one unit wide. */
    val stripLabelSize = 10.sp
    /** The main glyph on a key that also shows its shifted symbol. */
    val dualMainSize = 17.sp
    val popupRadius = 16.dp

    /** The keyboard never takes more than this share of the screen height. */
    const val maxHeightFraction = 0.6f

    /** From here up the window gets the 60% board. */
    val wideBreakpoint = 600.dp
    val wideKeyHeight = 46.dp
    val wideRowGap = 10.dp
    val wideSidePadding = 8.dp
}
