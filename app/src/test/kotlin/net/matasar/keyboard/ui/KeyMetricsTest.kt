package net.matasar.keyboard.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The key's two zones are arithmetic, so they are checked here rather than on a device: a legend
 * line and a glyph that both fit inside the key at every height the settings and the height budget
 * can produce.
 */
class KeyMetricsTest {

    @Test
    fun `the legend line and the glyph fit inside the key at every height`() {
        // 26 dp is the shortest key the layout produces: a phone in landscape is over the 600 dp
        // breakpoint, so it draws the 60% board's five rows inside the height budget.
        var height = 26f
        while (height <= 56f) {
            val key = height.dp
            // The ratios KeyButton pins on its own Texts, so the proof and the code cannot drift.
            val used = Dimens.legendLine(key).value + Dimens.glyphSize(key).value * Dimens.glyphLineHeightRatio
            assertTrue(used <= height, "a ${height}dp key needs ${used}dp for its legend line and glyph")
            val legendBox = Dimens.legendTextSize(key).value * Dimens.legendLineHeightRatio
            assertTrue(legendBox <= Dimens.legendLine(key).value, "the legend's line box overflows its reserve at ${height}dp")
            val withIcon = Dimens.legendLine(key).value + Dimens.iconSize(key, hasLegendLine = true).value
            assertTrue(withIcon <= height, "a ${height}dp key needs ${withIcon}dp for its legend line and icon")
            assertTrue(Dimens.iconSize(key, hasLegendLine = false).value <= height, "icon over a ${height}dp key")
            height += 0.4f
        }
    }

    @Test
    fun `both zones shrink with the key and neither ever disappears`() {
        val full = 46.dp
        val eightyPercent = 36.8.dp
        val landscape = 26.dp
        assertTrue(Dimens.glyphSize(eightyPercent).value < Dimens.glyphSize(full).value)
        assertTrue(Dimens.glyphSize(landscape).value < Dimens.glyphSize(eightyPercent).value)
        assertTrue(Dimens.legendLine(landscape) < Dimens.legendLine(full))
        // The height setting's minimum is 80%, which is where the legends used to vanish entirely.
        assertTrue(Dimens.legendTextSize(eightyPercent).value >= 8f, "the 80% setting must stay readable")
        assertTrue(Dimens.legendTextSize(landscape).value >= 7.5f)
        assertTrue(Dimens.glyphSize(landscape).value >= 10f)
    }

    @Test
    fun `a word label never outgrows the glyph beside it`() {
        for (height in listOf(26.dp, 36.8.dp, 42.dp, 46.dp, 56.dp)) {
            assertTrue(Dimens.wordSize(height).value <= Dimens.glyphSize(height).value, "word label at $height")
            assertTrue(Dimens.wordSize(height).value <= Dimens.labelSize.value, "word label at $height")
        }
        assertEquals(Dimens.labelSize.value, Dimens.wordSize(46.dp).value)
    }
}
