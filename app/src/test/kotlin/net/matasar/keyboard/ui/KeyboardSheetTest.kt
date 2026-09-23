package net.matasar.keyboard.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KeyboardSheetTest {

    private val density = Density(2f)
    private val shape = SheetShape(room = 800f)
    private fun outline(height: Float) = shape.createOutline(Size(1080f, height), LayoutDirection.Ltr, density)

    @Test
    fun `a sheet that fills the room is square, flush with the toolbar`() {
        assertIs<Outline.Rectangle>(outline(800f))
        // Half a pixel short is still full: the measured height is rounded, the room is not.
        assertIs<Outline.Rectangle>(outline(799.6f))
    }

    @Test
    fun `a shorter sheet keeps its rounded top corners and square bottom ones`() {
        val rounded = assertIs<Outline.Rounded>(outline(400f))
        val corner = 24f * density.density
        assertEquals(corner, rounded.roundRect.topLeftCornerRadius.x)
        assertEquals(corner, rounded.roundRect.topRightCornerRadius.x)
        assertEquals(0f, rounded.roundRect.bottomLeftCornerRadius.x)
    }
}
