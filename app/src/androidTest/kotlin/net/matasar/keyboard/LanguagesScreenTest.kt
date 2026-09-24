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
import net.matasar.keyboard.settings.Prefs
import net.matasar.keyboard.settings.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The languages are on their own screen: Settings shows the enabled ones and a button. Apart
 * from the keyboard tests, since it needs no keyboard and leaves no field behind for them.
 */
@RunWith(AndroidJUnit4::class)
class LanguagesScreenTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * Switching Croatian on turns it on for the keyboard, keeps its row where it was (under More
     * languages, until the screen opens again), and Settings names it on the way back.
     */
    @Test
    fun aLanguageSwitchedOnStaysInPlaceAndSettingsNamesIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(context)
        runBlocking { prefs.setLanguageEnabled("hr", false) }
        try {
            context.startActivity(
                Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            val button = scrollTo("Choose languages", down = true)
            assertNotNull("no Choose languages button on the Settings screen", button)
            button!!.click()
            assertTrue("the Languages screen never opened", device.wait(Until.hasObject(By.text("More languages")), 5_000))

            val croatian = "Hrvatski · Croatian"
            val row = scrollTo(croatian, down = true)
            assertNotNull("no Croatian row", row)
            val before = row!!.visibleBounds
            // The row's own switch: the toggle on the same line as its name.
            val switch = device.findObjects(By.checkable(true)).firstOrNull { it.visibleCenter.y in before.top..before.bottom }
            assertNotNull("no switch on the Croatian row", switch)
            switch!!.click()
            assertTrue(
                "Croatian was not switched on",
                waitUntil(3_000) { runBlocking { "hr" in prefs.settings.first().enabledLanguages } },
            )
            device.waitForIdle()
            assertEquals("the Croatian row moved when switched", before.top, device.findObject(By.text(croatian)).visibleBounds.top)

            device.pressBack()
            assertTrue("Settings does not name Croatian after the switch", device.wait(Until.hasObject(By.textContains("Hrvatski")), 5_000))
        } finally {
            runBlocking { prefs.setLanguageEnabled("hr", false) }
        }
    }

    /**
     * [text] brought to the upper half of the screen, scrolling a third of a screen at a time,
     * slowly enough not to fling past it, and clear of the bottom bar a tap there would hit.
     */
    private fun scrollTo(text: String, down: Boolean): UiObject2? {
        val x = device.displayWidth / 2
        val (low, high) = device.displayHeight * 2 / 3 to device.displayHeight / 3
        repeat(20) {
            val found = device.wait(Until.findObject(By.text(text)), 500)
            if (found != null && found.visibleCenter.y < low) {
                // A swipe flings on after the finger lifts: let the page stop, then look again.
                device.waitForIdle()
                SystemClock.sleep(500)
                return device.findObject(By.text(text))
            }
            if (down) device.swipe(x, low, x, high, 100) else device.swipe(x, high, x, low, 100)
            device.waitForIdle()
        }
        return null
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }
}
