package net.matasar.keyboard.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
    val labelSize = 12.sp
    /** Labels on the developer strip, whose keys are one unit wide. */
    val stripLabelSize = 10.sp
    val popupRadius = 16.dp

    /** The keyboard never takes more than this share of the screen height. */
    const val maxHeightFraction = 0.6f

    /**
     * The legend line across the top of a key, where the shifted symbol and the Fn meaning sit.
     * It scales with the key's own height rather than switching off below one: the height setting
     * runs from 80% (a 36.8 dp key on the 60% board) and a short landscape window squeezes the
     * keys further still, and a legend nobody can see is the same as no legend at all.
     */
    fun legendLine(keyHeight: Dp): Dp = (keyHeight * 0.26f).coerceIn(8.dp, 14.dp)

    /** The two legends on that line, sized to fit it. */
    fun legendTextSize(keyHeight: Dp): TextUnit = (legendLine(keyHeight).value * 0.72f).coerceIn(6.5f, 10f).sp

    /** The glyph under the line: what the key types right now, in what the line leaves. */
    fun glyphSize(keyHeight: Dp): TextUnit = ((keyHeight - legendLine(keyHeight)).value * 0.62f).coerceIn(11f, 19f).sp

    /** The glyph on a key with no legend line (the phone's letters): the whole key is its own. */
    fun plainGlyphSize(keyHeight: Dp): TextUnit = (keyHeight.value * 0.52f).coerceIn(13f, 22f).sp

    /** A key's icon: 22 dp where there is room, shrinking with the glyph where there is not. */
    fun iconSize(keyHeight: Dp, legendLine: Boolean): Dp =
        minOf(22.dp, (if (legendLine) glyphSize(keyHeight) else plainGlyphSize(keyHeight)).value.dp * 1.15f)

    /** A word label (Esc, Del, the space bar's language); never larger than the glyph beside it. */
    fun wordSize(keyHeight: Dp, base: TextUnit = labelSize): TextUnit =
        minOf(base.value, glyphSize(keyHeight).value).sp

    /** From here up the window gets the 60% board. */
    val wideBreakpoint = 600.dp
    val wideKeyHeight = 46.dp
    val wideRowGap = 10.dp
    val wideSidePadding = 8.dp
}
