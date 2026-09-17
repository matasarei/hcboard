package net.matasar.keyboard.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The metrics from the mocks. Heights scale with the height setting; widths come from the row. */
object Dimens {
    /**
     * The phone board's proportions follow Samsung's keyboard, measured from a screenshot of it:
     * a key about 1.3 times as tall as it is wide, and gaps that are a small share of the pitch.
     */
    val keyHeight = 46.dp
    val keyGap = 5.dp
    /** At 100% height; it shrinks and grows with the height setting, as the keys do. */
    val rowGap = 10.dp
    val sidePadding = 4.dp
    val topPadding = 8.dp
    val bottomPadding = 12.dp
    val keyRadius = 10.dp
    val toolbarHeight = 44.dp
    /** Word keys: Esc, Tab, Caps, Shift, the modifiers, F1-F12 under Fn, ?123, the space bar. */
    val labelSize = 14.sp
    /** Labels on the developer strip, whose keys are one unit wide. */
    val stripLabelSize = 12.sp
    val popupRadius = 16.dp

    /** The keyboard never takes more than this share of the screen height. */
    const val maxHeightFraction = 0.6f

    /**
     * The legend line across the top of a key, where the shifted symbol and the Fn meaning sit.
     * It scales with the key's own height rather than switching off below one: the height setting
     * runs from 80% (a 36.8 dp key on the 60% board) and a short landscape window squeezes the
     * keys further still, and a legend nobody can see is the same as no legend at all.
     */
    fun legendLine(keyHeight: Dp): Dp = (legendTextSize(keyHeight).value * 1.5f).dp

    /**
     * What a pinned text line box costs, as a multiple of the font size. [KeyButton] sets these on
     * its `Text`s rather than leaving the line to the font's own metrics, which are far taller than
     * the size suggests: the two zones are measured in advance, so their line boxes have to be
     * known here, where the fit is proved against the same numbers.
     */
    const val legendLineHeightRatio = 1.25f
    const val glyphLineHeightRatio = 1.15f

    /**
     * The two legends on that line. The legend leads and the line follows it, not the other way
     * round: a legend has a size below which it stops being readable at all, so it holds at 7.5 sp
     * on the shortest keys and gives the glyph what is left.
     */
    fun legendTextSize(keyHeight: Dp): TextUnit = (keyHeight.value * 0.22f).coerceIn(7.5f, 10f).sp

    /**
     * The glyph under the line: what the key types right now, in what the line leaves. It takes
     * three quarters of that room, which with [glyphLineHeightRatio] is what fits: the old share
     * left a full-height key with most of a line of slack under its glyph.
     */
    fun glyphSize(keyHeight: Dp): TextUnit = ((keyHeight - legendLine(keyHeight)).value * 0.75f).coerceIn(10f, 24f).sp

    /** The glyph on a key with no legend line (the phone's letters): the whole key is its own. */
    fun plainGlyphSize(keyHeight: Dp): TextUnit = (keyHeight.value * 0.62f).coerceIn(13f, 26f).sp

    /** A key's icon: 22 dp where there is room, shrinking with the glyph where there is not. */
    fun iconSize(keyHeight: Dp, hasLegendLine: Boolean): Dp =
        minOf(22.dp, (if (hasLegendLine) glyphSize(keyHeight) else plainGlyphSize(keyHeight)).value.dp * 1.15f)

    /** A word label (Esc, Del, the space bar's language); never larger than the glyph beside it. */
    fun wordSize(keyHeight: Dp, base: TextUnit = labelSize, hasLegendLine: Boolean = true): TextUnit =
        minOf(base.value, (if (hasLegendLine) glyphSize(keyHeight) else plainGlyphSize(keyHeight)).value).sp

    /** From here up the window gets the 60% board. */
    val wideBreakpoint = 600.dp
    val wideKeyHeight = 46.dp
    /** At 100% height, like [rowGap]. */
    val wideRowGap = 10.dp
    val wideSidePadding = 8.dp
}
