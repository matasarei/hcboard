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
        // confirmed in the field before the next; a tap that fell into that gap is retried. The
        // field asks for sentence capitals, so the first letter arrives as a capital on its own.
        val typed = StringBuilder()
        for (letter in "Hello") {
            typed.append(letter)
            var landed = false
            repeat(3) {
                if (landed) return@repeat
                tapLetter(letter)
                landed = waitUntil(1_500) { fieldText() == typed.toString() }
            }
            assertTrue("expected '$typed', field holds '${fieldText()}'", landed)
        }
        assertEquals("Hello", fieldText())
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
        // The field starts a sentence, so the glided word comes with its capital.
        val arrived = waitUntil(5_000) { fieldText()?.trim() == "Hello" }
        assertTrue("expected 'Hello', field holds '${fieldText()}'", arrived)
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

    /**
     * Landscape on a phone is where Android puts up its fullscreen extract editor unless the
     * keyboard says no. A plain platform field, since Compose fields opt out on their own.
     */
    @Test
    fun landscapeNeverGoesFullscreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            device.setOrientationLandscape()
            context.startActivity(
                Intent(context, net.matasar.keyboard.debug.PlainFieldActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            assertTrue("the plain field never came up", device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5_000))
            focusFieldAndShowKeyboard()
            val service = KeyboardService.instance
            assertNotNull("the service is not running", service)
            var fullscreen = true
            instrumentation.runOnMainSync { fullscreen = service!!.isFullscreenMode }
            assertTrue("the keyboard went fullscreen in landscape", !fullscreen)
        } finally {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    /**
     * What TalkBack works with: the keyboard window's accessibility nodes. Each key is one node
     * named for what it types, and clicking it (TalkBack's double-tap, and its lift-to-type) types.
     */
    @Test
    fun keysAreAccessibilityNodesThatTypeWhenClicked() {
        // The field is empty, so the automatic capital is on and the key says, and types, Q.
        val q = waitForImeNode("Q")
        assertNotNull("no accessibility node for the Q key in the keyboard window; saw: ${describeImeNodes()}", q)
        assertNotNull("no accessibility node for Space", waitForImeNode("Space"))
        assertTrue("the Q key does not offer a click", q!!.isClickable)
        assertTrue("clicking the Q node did nothing", q.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        assertTrue("clicking the Q node typed nothing (field: '${fieldText()}')", waitUntil(2_000) { fieldText() == "Q" })

        // A latching key says its state after its name, and the page says what it is.
        val shift = waitForImeNode("Shift")
        assertEquals("Off", shift?.stateDescription?.toString())
        assertTrue(shift!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        assertTrue(
            "Shift's state did not follow the click (it reads '${waitForImeNode("Shift")?.stateDescription}')",
            waitUntil(2_000) { waitForImeNode("Shift")?.stateDescription?.toString() == "On for the next key" },
        )
        assertTrue("no page title 'Letters, English' in the keyboard window", imeHasPaneTitle("Letters, English"))
    }

    /** In a password field, with nothing but the speaker to hear it, no key says its character. */
    @Test
    fun keysSayDotInAPasswordField() {
        // The keyboard @Before raised covers the password field below the first one.
        device.pressBack()
        waitUntil(2_000) { !device.executeShellCommand("dumpsys input_method").contains("mIsInputViewShown=true") }
        val password = device.findObjects(By.clazz("android.widget.EditText")).getOrNull(1)
        assertNotNull("the settings screen has no password field in view", password)
        password!!.click()
        assertTrue("the password field's keys never said Dot; saw: ${describeImeNodes()}", waitUntil(3_000) { waitForImeNode("Dot") != null })
        for (letter in listOf("q", "Q", "p", "P")) assertEquals("a key still says $letter", null, waitForImeNode(letter, timeoutMs = 0))
        assertNotNull("Space lost its name in the password field", waitForImeNode("Space"))
    }

    private fun imeHasPaneTitle(title: String): Boolean {
        val ime = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
            .firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return false
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo): Boolean =
            node.paneTitle?.toString() == title || (0 until node.childCount).any { i -> node.getChild(i)?.let(::walk) == true }
        return ime.root?.let(::walk) == true
    }

    /** The first node in the input method's window whose description is [description], once it shows. */
    private fun waitForImeNode(description: String, timeoutMs: Long = 3_000, ignoreCase: Boolean = false): android.view.accessibility.AccessibilityNodeInfo? {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        var found: android.view.accessibility.AccessibilityNodeInfo? = null
        waitUntil(timeoutMs) {
            val ime = automation.windows.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            found = ime?.root?.let { find(it, description, ignoreCase) }
            found != null
        }
        return found
    }

    /** Every described node in the input method's window, for a failure message. */
    private fun describeImeNodes(): String {
        val ime = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
            .firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return "no keyboard window"
        val described = mutableListOf<String>()
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo) {
            node.contentDescription?.let { described += it.toString() }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        ime.root?.let(::walk)
        return described.joinToString()
    }

    private fun find(node: android.view.accessibility.AccessibilityNodeInfo, description: String, ignoreCase: Boolean = false): android.view.accessibility.AccessibilityNodeInfo? {
        if (node.contentDescription?.toString().equals(description, ignoreCase)) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { child -> find(child, description, ignoreCase)?.let { return it } }
        return null
    }

    /** The space bar's centre on screen, where the keyboard window really has it. */
    private fun spaceCentre(): android.graphics.Point = keyCentre("Space")

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
            val segments = "дякую".map { letterCentre(it) }.toTypedArray()
            device.swipe(segments, 10)
            val arrived = waitUntil(5_000) { fieldText()?.trim() == "Дякую" }
            assertTrue("expected 'Дякую', field holds '${fieldText()}'", arrived)
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

    /** A letter key's centre on screen; its case does not matter (an automatic capital says Q). */
    private fun letterCentre(letter: Char): android.graphics.Point = keyCentre(letter.toString())

    /**
     * A key's centre on screen, from its node in the keyboard window: where it really is, whatever
     * the height setting, the bars the keyboard pads for, or the board. Worked out from the layout
     * constants instead, every tap landed on a row's bottom edge once the keyboard padded for the
     * IME's own bottom bar, which is taller than the system's navigation_bar_height.
     */
    private fun keyCentre(description: String): android.graphics.Point {
        val node = waitForImeNode(description, ignoreCase = true)
        assertNotNull("no key '$description' in the keyboard window; saw: ${describeImeNodes()}", node)
        val bounds = android.graphics.Rect().also { node!!.getBoundsInScreen(it) }
        return android.graphics.Point(bounds.centerX(), bounds.centerY())
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
}
