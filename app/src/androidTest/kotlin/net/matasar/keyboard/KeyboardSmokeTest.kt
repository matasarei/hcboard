package net.matasar.keyboard

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import net.matasar.keyboard.settings.SettingsActivity
import net.matasar.keyboard.ui.Dimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The end-to-end check: make this keyboard the current one, open the settings screen's text
 * field, tap keys on the real input view and read the text back.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardSmokeTest {

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val ime = IME

    companion object {
        private const val IME = "net.matasar.keyboard/.ime.KeyboardService"

        /** Device-wide settings the class changes once, restored once in [restoreSettings]. */
        private var previousIme = ""
        private var previousShowWithHardKeyboard = ""

        @JvmStatic
        @BeforeClass
        fun selectKeyboard() {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            previousIme = device.executeShellCommand("settings get secure default_input_method").trim()
            previousShowWithHardKeyboard = device.executeShellCommand("settings get secure show_ime_with_hard_keyboard").trim()
            device.executeShellCommand("ime enable $IME")
            device.executeShellCommand("ime set $IME")
            device.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        }

        @JvmStatic
        @AfterClass
        fun restoreSettings() {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            if (previousShowWithHardKeyboard == "null" || previousShowWithHardKeyboard.isEmpty()) {
                device.executeShellCommand("settings delete secure show_ime_with_hard_keyboard")
            } else {
                device.executeShellCommand("settings put secure show_ime_with_hard_keyboard $previousShowWithHardKeyboard")
            }
            if (previousIme.isNotEmpty() && previousIme != "null" && previousIme != IME) {
                device.executeShellCommand("ime set $previousIme")
            }
        }
    }

    @Before
    fun openTheField() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.startActivity(
            Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 10_000))
    }

    @Test
    fun typesHello() {
        assertEquals(ime, device.executeShellCommand("settings get secure default_input_method").trim())
        val field = device.findObject(By.clazz("android.widget.EditText"))
        assertNotNull(field)
        // Click until the IME window is up; a freshly enabled keyboard can miss the first request.
        var shown = false
        repeat(6) {
            if (shown) return@repeat
            field.click()
            device.waitForIdle()
            shown = waitUntil(2_000) { device.executeShellCommand("dumpsys input_method").contains("mIsInputViewShown=true") }
        }
        assertTrue("keyboard never showed", shown)

        // The window reports shown a moment before it takes touches, so each letter is
        // confirmed in the field before the next; a tap that fell into that gap is retried.
        val typed = StringBuilder()
        for (letter in "hello") {
            typed.append(letter)
            var landed = false
            repeat(3) {
                if (landed) return@repeat
                tapLetter(letter)
                landed = waitUntil(1_500) { fieldText() == typed.toString() }
            }
            assertTrue("expected '$typed', field holds '${fieldText()}'", landed)
        }
        assertEquals("hello", fieldText())
    }

    @Test
    fun glidesHello() {
        assertEquals(ime, device.executeShellCommand("settings get secure default_input_method").trim())
        val field = device.findObject(By.clazz("android.widget.EditText"))
        assertNotNull(field)
        var shown = false
        repeat(6) {
            if (shown) return@repeat
            field.click()
            device.waitForIdle()
            shown = waitUntil(2_000) { device.executeShellCommand("dumpsys input_method").contains("mIsInputViewShown=true") }
        }
        assertTrue("keyboard never showed", shown)
        // The word list loads off the main thread after the service starts; give it a moment.
        Thread.sleep(1_500)

        // One pointer stream through h, e, l, l, o: the double l is a small loop on the key, the
        // way a finger marks a repeated letter and the way the classifier models one.
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val quarterW = (Dimens.keyHeight.value * density / 4).toInt()
        val l = letterCentre('l')
        val segments = arrayOf(
            letterCentre('h'), letterCentre('e'), l,
            android.graphics.Point(l.x + quarterW, l.y + quarterW), android.graphics.Point(l.x + quarterW, l.y - quarterW),
            android.graphics.Point(l.x - quarterW, l.y - quarterW), android.graphics.Point(l.x - quarterW, l.y + quarterW),
            l, letterCentre('o'),
        )
        device.swipe(segments, 10)
        val arrived = waitUntil(5_000) { fieldText()?.trim() == "hello" }
        assertTrue("expected 'hello', field holds '${fieldText()}'", arrived)
    }

    /**
     * Keys live in the IME window, which the accessibility tree does not always expose, so the
     * tap lands on the key's computed position: rows from the bottom of the screen, columns
     * from the layer geometry (10 units, 4 dp side padding, 6 dp gaps, 42 dp keys, 12 dp gaps).
     */
    private fun tapLetter(letter: Char) {
        val centre = letterCentre(letter)
        device.click(centre.x, centre.y)
        device.waitForIdle()
    }

    private fun letterCentre(letter: Char): android.graphics.Point {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val width = device.displayWidth
        val navBar = navigationBarHeightPx()
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val rowIndex = rows.indexOfFirst { letter in it }
        val row = rows[rowIndex]
        val unit = (width - 2 * Dimens.sidePadding.value * density - 9 * Dimens.keyGap.value * density) / 10f
        val gap = Dimens.keyGap.value * density
        val offsetUnits = when (rowIndex) { 1 -> 0.5f; 2 -> 1.5f; else -> 0f }
        val col = row.indexOf(letter)
        val x = Dimens.sidePadding.value * density + offsetUnits * (unit + gap) + col * (unit + gap) + unit / 2
        // Bottom row centre, then two rows up per row index from the bottom (bottom row is index 3).
        val keyH = Dimens.keyHeight.value * density
        val rowPitch = keyH + Dimens.rowGap.value * density
        val bottomRowCentre = device.displayHeight - navBar - Dimens.bottomPadding.value * density - keyH / 2
        val y = bottomRowCentre - (3 - rowIndex) * rowPitch
        return android.graphics.Point(x.toInt(), y.toInt())
    }

    private fun fieldText(): String? = device.findObject(By.clazz("android.widget.EditText"))?.text

    /** Polls [condition] until it holds or [timeoutMs] passes. */
    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }

    private fun navigationBarHeightPx(): Int {
        val res = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val id = res.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) res.getDimensionPixelSize(id) else 0
    }
}
