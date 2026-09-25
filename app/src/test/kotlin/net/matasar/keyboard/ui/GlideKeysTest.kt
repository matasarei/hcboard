package net.matasar.keyboard.ui

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class GlideKeysTest {

    @Test
    fun `glide is given the letter keys only, never an apostrophe key`() {
        val bounds = mapOf(
            'в' to Rect(0f, 0f, 10f, 20f),
            '\'' to Rect(10f, 0f, 20f, 20f),
            'я' to Rect(20f, 0f, 30f, 20f),
        )
        val keys = glideKeys(bounds)
        assertEquals(listOf('в', 'я'), keys.map { it.char })
        assertEquals(25f, keys.last().centerX)
        assertEquals(10f, keys.last().centerY)
    }
}
