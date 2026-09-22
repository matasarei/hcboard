package net.matasar.keyboard

import android.content.Intent
import android.os.SystemClock
import net.matasar.keyboard.ime.KeyboardService
import android.view.ViewConfiguration
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import net.matasar.keyboard.layout.Language
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.settings.Prefs
import net.matasar.keyboard.settings.SettingsActivity
import net.matasar.keyboard.ui.Dimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.AfterClass
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The end-to-end check: make this keyboard the current one, open the settings screen's text
 * field, tap keys on the real input view and read the text back.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
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
        // The settings screen is longer than the display; the field is at the bottom.
        var found = device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5_000)
        repeat(8) {
            if (found) return@repeat
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 20)
            device.waitForIdle()
            found = device.hasObject(By.clazz("android.widget.EditText"))
        }
        assertTrue("the text field never came into view", found)
        focusFieldAndShowKeyboard()
        device.findObject(By.clazz("android.widget.EditText"))?.text = ""
    }

    /** Clicks the field until the IME window is up; a freshly selected keyboard can miss the first request. */
    private fun focusFieldAndShowKeyboard() {
        // A keyboard still up from the previous test is bound to a field that no longer exists;
        // Back dismisses it so the click below opens a fresh connection to this field.
        if (device.executeShellCommand("dumpsys input_method").contains("mIsInputViewShown=true")) {
            device.pressBack()
            waitUntil(2_000) { !device.executeShellCommand("dumpsys input_method").contains("mIsInputViewShown=true") }
        }
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
    }

    @Test
    fun typesHello() {
        assertEquals(ime, device.executeShellCommand("settings get secure default_input_method").trim())

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
     * A long press on Space opens the cursor trackpad; when the system takes the pointer (a
     * navigation gesture from the bar right under the space bar), the trackpad must end even
     * though no release ever arrives. Drives the input view directly, since only the framework
     * can produce a real cancel.
     */
    @Test
    fun mTrackpadEndsWhenThePointerIsCancelled() {
        focusFieldAndShowKeyboard()
        val service = KeyboardService.instance
        assertNotNull("the service is not running", service)
        val controller = service!!.controller
        // The window reports shown a moment before it takes touches: a tap that reaches the
        // field proves the keyboard is live before the gesture that must not be lost.
        var awake = false
        repeat(6) {
            if (awake) return@repeat
            tapLetter('a')
            awake = waitUntil(1_000) { fieldText()?.contains('a') == true }
        }
        assertTrue("the keyboard never took a tap", awake)
        val space = spaceCentre()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Injected through the system like a finger, so the window, the pointer ids and the
        // coordinates are exactly what a real gesture brings; only the cancel is unusual.
        val downTime = SystemClock.uptimeMillis()
        fun inject(action: Int, dx: Int) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, (space.x + dx).toFloat(), space.y.toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue("injecting action $action failed", instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        // Snapshot state is written on the main thread; read it there too, or a stale value shows.
        fun trackpadOn(): Boolean {
            var on = false
            instrumentation.runOnMainSync { on = controller.trackpad }
            return on
        }
        inject(MotionEvent.ACTION_DOWN, 0)
        val longPress = ViewConfiguration.getLongPressTimeout().toLong()
        SystemClock.sleep(longPress + 300)
        inject(MotionEvent.ACTION_MOVE, 40)
        assertTrue("the trackpad never started (field: '${fieldText()}')", waitUntil(2_000) { trackpadOn() })
        inject(MotionEvent.ACTION_CANCEL, 40)
        assertTrue("the trackpad stayed on after the cancel", waitUntil(2_000) { !trackpadOn() })
    }

    /**
     * A rotation rebuilds the input view; the view it replaces must let go of its composition, or
     * every fold and rotation leaves one more keyboard recomposing on each keystroke.
     */
    @Test
    fun rebuildDisposesTheOldComposition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val service = KeyboardService.instance
        assertNotNull("the service is not running", service)
        var first: android.view.View? = null
        instrumentation.runOnMainSync { first = service!!.inputView }
        val old = first as androidx.compose.ui.platform.ComposeView
        try {
            device.setOrientationLandscape()
            assertTrue("the input view was never rebuilt", waitUntil(5_000) {
                var current: android.view.View? = null
                instrumentation.runOnMainSync { current = service!!.inputView }
                current !== old
            })
            var disposed = false
            instrumentation.runOnMainSync { disposed = !old.hasComposition }
            assertTrue("the replaced input view still holds its composition", disposed)
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    /** The space bar's centre on screen: the middle of the bottom row of the phone letters layer. */
    private fun spaceCentre(): android.graphics.Point {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val keyH = Dimens.keyHeight.value * density
        val y = device.displayHeight - navigationBarHeightPx() - Dimens.bottomPadding.value * density - keyH / 2
        return android.graphics.Point(device.displayWidth / 2, y.toInt())
    }

    /** Runs last (name order): it switches the keyboard's language and switches it back. */
    @Test
    fun zGlidesUkrainian() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(context)
        runBlocking {
            prefs.setLanguageEnabled("uk", true)
            prefs.setCurrentLanguage("uk")
        }
        try {
            Thread.sleep(2_000) // the layer switches and the Ukrainian list loads
            // "дякую" (thanks): a common word with no close neighbour in the list.
            val segments = "дякую".map { letterCentre(it, Languages.ukrainian, withGlobe = true) }.toTypedArray()
            device.swipe(segments, 10)
            val arrived = waitUntil(5_000) { fieldText()?.trim() == "дякую" }
            assertTrue("expected 'дякую', field holds '${fieldText()}'", arrived)
        } finally {
            runBlocking {
                prefs.setCurrentLanguage("en_US")
                prefs.setLanguageEnabled("uk", false)
            }
            Thread.sleep(1_500)
        }
    }

    /**
     * Keys live in the IME window, which the accessibility tree does not always expose, so the
     * tap lands on the key's computed position: rows from the bottom of the screen, columns
     * from the layer geometry (4 dp side padding, 6 dp gaps, 42 dp keys, 12 dp gaps, the
     * language's units per row).
     */
    private fun tapLetter(letter: Char) {
        val centre = letterCentre(letter)
        device.click(centre.x, centre.y)
        device.waitForIdle()
    }

    private fun letterCentre(letter: Char, language: Language = Languages.english, withGlobe: Boolean = false): android.graphics.Point {
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val width = device.displayWidth
        val navBar = navigationBarHeightPx()
        val rows = language.rows
        val rowIndex = rows.indexOfFirst { letter in it }
        val row = rows[rowIndex]
        val units = language.units
        val unit = (width - 2 * Dimens.sidePadding.value * density - (units - 1) * Dimens.keyGap.value * density) / units
        val gap = Dimens.keyGap.value * density
        val offsetUnits = (units - row.length) / 2f
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
