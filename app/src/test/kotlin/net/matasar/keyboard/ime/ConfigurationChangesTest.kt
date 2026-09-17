package net.matasar.keyboard.ime

import android.content.pm.ActivityInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigurationChangesTest {

    @Test
    fun `a change that moves or resizes the display rebuilds the input view`() {
        for (change in listOf(
            ActivityInfo.CONFIG_SCREEN_SIZE, // unfolding a Fold
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE,
            ActivityInfo.CONFIG_SCREEN_LAYOUT,
            ActivityInfo.CONFIG_ORIENTATION,
            ActivityInfo.CONFIG_DENSITY,
        )) {
            assertTrue(rebuildsInputView(change), "change $change")
        }
        // A fold reports several at once.
        assertTrue(rebuildsInputView(ActivityInfo.CONFIG_SCREEN_SIZE or ActivityInfo.CONFIG_ORIENTATION))
        assertTrue(rebuildsInputView(ActivityInfo.CONFIG_LOCALE or ActivityInfo.CONFIG_DENSITY))
    }

    @Test
    fun `a change the composition handles itself does not`() {
        assertFalse(rebuildsInputView(0))
        for (change in listOf(
            ActivityInfo.CONFIG_LOCALE,
            ActivityInfo.CONFIG_FONT_SCALE,
            ActivityInfo.CONFIG_UI_MODE,
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN,
            ActivityInfo.CONFIG_TOUCHSCREEN,
        )) {
            assertFalse(rebuildsInputView(change), "change $change")
        }
    }
}
