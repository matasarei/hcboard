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
        assertTrue(Dimens.legendTextSize(eightyPercent).value >= 7.5f, "the 80% setting must stay readable")
        assertTrue(Dimens.legendTextSize(landscape).value >= 7.5f)
        assertTrue(Dimens.glyphSize(landscape).value >= 10f)
    }

    @Test
    fun `a full-height key spends the room it has on its glyph`() {
        // The complaint the sizes came from: a 46 dp key had most of a line of slack under its
        // glyph. The fit test above is the ceiling; these are the floor.
        val full = 46.dp
        assertTrue(Dimens.glyphSize(full).value >= 22f, "glyph on a full key: ${Dimens.glyphSize(full).value}")
        assertTrue(Dimens.plainGlyphSize(full).value >= 25f, "plain glyph on a full key: ${Dimens.plainGlyphSize(full).value}")
        val slack = full.value - Dimens.legendLine(full).value - Dimens.glyphSize(full).value * Dimens.glyphLineHeightRatio
        assertTrue(slack < Dimens.legendTextSize(full).value, "a full key still has ${slack}dp going spare")
    }

    @Test
    fun `a word label never outgrows the glyph beside it`() {
        for (height in listOf(26.dp, 36.8.dp, 42.dp, 46.dp, 56.dp)) {
            assertTrue(Dimens.wordSize(height).value <= Dimens.glyphSize(height).value, "word label at $height")
            val plain = Dimens.wordSize(height, hasLegendLine = false)
            assertTrue(plain.value <= Dimens.plainGlyphSize(height).value, "word label with no legend line at $height")
            assertTrue(plain.value >= Dimens.wordSize(height).value, "a key with no legend line has more room, not less")
            assertTrue(Dimens.wordSize(height).value <= Dimens.labelSize.value, "word label at $height")
        }
        assertEquals(Dimens.labelSize.value, Dimens.wordSize(46.dp).value)
    }

    @Test
    fun `the centered glyph fits within the key height across all heights`() {
        var height = 26f
        while (height <= 56f) {
            val key = height.dp
            val glyphLineBox = Dimens.glyphSize(key).value * Dimens.glyphLineHeightRatio
            assertTrue(glyphLineBox <= height, "glyph line box ${glyphLineBox}dp exceeds ${height}dp")
            val topMargin = (height - glyphLineBox) / 2f
            assertTrue(topMargin >= 0f, "centered glyph has negative top margin at ${height}dp")
            height += 0.4f
        }
    }

    @Test
    fun `width scale adds proportional side padding and keeps unit width positive`() {
        val screenWidths = listOf(360.dp, 390.dp, 412.dp)
        val scales = listOf(0.7f, 0.75f, 0.8f, 0.85f, 0.9f, 0.95f, 1.0f)
        val unitCounts = listOf(10f, 12f) // 10 units for Latin, 12 units for Cyrillic (ru, uk)

        for (maxWidth in screenWidths) {
            for (scale in scales) {
                val extraSidePadding = (maxWidth * ((1f - scale.coerceIn(0.7f, 1f)) / 2f)).coerceAtLeast(0.dp)
                val baseSidePadding = Dimens.sidePadding
                val sidePadding = baseSidePadding + extraSidePadding

                if (scale == 1.0f) {
                    assertEquals(0f, extraSidePadding.value, 0.001f)
                    assertEquals(Dimens.sidePadding, sidePadding)
                } else {
                    assertTrue(extraSidePadding.value > 0f)
                }

                for (units in unitCounts) {
                    val unitWidth = (maxWidth - sidePadding * 2 - Dimens.keyGap * (units.toInt() - 1)) / units
                    assertTrue(unitWidth.value > 15f, "unit width too small at width $maxWidth, scale $scale, units $units: $unitWidth")
                    val totalRowWidth = sidePadding * 2 + unitWidth * units + Dimens.keyGap * (units.toInt() - 1)
                    assertEquals(maxWidth.value, totalRowWidth.value, 0.01f)
                }
            }
        }
    }

    @Test
    fun `extra side padding is clamped between 70 percent and 100 percent`() {
        val maxWidth = 400.dp
        val belowMin = (maxWidth * ((1f - 0.5f.coerceIn(0.7f, 1f)) / 2f)).coerceAtLeast(0.dp)
        val atMin = (maxWidth * ((1f - 0.7f.coerceIn(0.7f, 1f)) / 2f)).coerceAtLeast(0.dp)
        assertEquals(atMin, belowMin)

        val aboveMax = (maxWidth * ((1f - 1.2f.coerceIn(0.7f, 1f)) / 2f)).coerceAtLeast(0.dp)
        val atMax = (maxWidth * ((1f - 1.0f.coerceIn(0.7f, 1f)) / 2f)).coerceAtLeast(0.dp)
        assertEquals(atMax, aboveMax)
        assertEquals(0.dp, atMax)
    }
}
