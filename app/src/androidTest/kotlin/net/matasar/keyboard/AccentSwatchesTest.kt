package net.matasar.keyboard

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.matasar.keyboard.settings.AccentColour
import net.matasar.keyboard.settings.Prefs
import net.matasar.keyboard.settings.SettingsActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The accent swatches under the theme: no names on them, but each one is spoken and picks its colour. */
@RunWith(AndroidJUnit4::class)
class AccentSwatchesTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun aSwatchPicksItsColourAndReadsAsSelected() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(context)
        val before = runBlocking { prefs.settings.first().accent }
        runBlocking { prefs.setAccent(AccentColour.SYSTEM) }
        try {
            context.startActivity(
                Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            // The phone's palette is the default, first and selected (the emulator is Android 12+).
            val phone = device.wait(Until.findObject(By.desc("Phone colours")), 5_000)
            assertNotNull("no Phone colours swatch", phone)
            assertTrue("Phone colours is not selected by default", selected(phone!!))

            val red = device.findObject(By.desc("Red"))
            assertNotNull("no Red swatch", red)
            assertFalse("Red is selected before it was picked", selected(red!!))
            red.click()
            assertTrue(
                "Red was not stored",
                waitUntil(3_000) { runBlocking { prefs.settings.first().accent } == AccentColour.RED },
            )
            assertTrue("Red does not read as selected", waitUntil(3_000) { selected(device.findObject(By.desc("Red"))) })
            assertFalse("Phone colours still reads as selected", selected(device.findObject(By.desc("Phone colours"))))
            // Every swatch is spoken by its colour, and none shows a name of its own.
            for (name in listOf("Orange", "Yellow", "Green", "Teal", "Blue", "Indigo", "Purple", "Pink", "Neutral")) {
                assertNotNull("no $name swatch", device.findObject(By.desc(name)))
                assertFalse("the $name swatch shows its name", device.hasObject(By.text(name)))
            }
        } finally {
            runBlocking { prefs.setAccent(before) }
        }
    }

    /**
     * Compose reports the swatch as a checked radio on the node that takes the tap, with its name on
     * a child that TalkBack merges into it; the name's node or its parent carries the state.
     */
    private fun selected(swatch: UiObject2?): Boolean =
        swatch != null && (swatch.isChecked || swatch.parent?.let { it.isCheckable && it.isChecked } == true)

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }
}
