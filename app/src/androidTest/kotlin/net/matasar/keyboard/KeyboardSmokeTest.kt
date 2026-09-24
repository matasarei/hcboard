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
import kotlinx.coroutines.flow.first
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

    /** What the instrumentation's accessibility flags were before touch exploration was asked for. */
    private var flagsBeforeExploring: Int? = null

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
        assertTrue("keyboard never showed (touch exploration: ${device.executeShellCommand("dumpsys accessibility").contains("touchExplorationEnabled=true")})", shown)
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
     * On the wide board (a phone turned sideways) the gear opens the same sheet as on the phone,
     * with the three switches that only change the phone board dimmed: they say so, and a tap on
     * one does nothing.
     */
    @Test
    fun theWideBoardsGearSheetDimsThePhoneOnlyRows() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        try {
            device.setOrientationLandscape()
            context.startActivity(
                Intent(context, net.matasar.keyboard.debug.PlainFieldActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            assertTrue("the plain field never came up", device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5_000))
            focusFieldAndShowKeyboard()
            openGearSheet()
            assertTrue(
                "the wide board's sheet does not mark three rows as phone-only; texts: ${describeImeTexts()}",
                waitUntil(3_000) { describeImeTexts().split("'Phone board only'").size - 1 == 3 },
            )

            val bounds = android.graphics.Rect()
            imeNodeWithText("Developer mode")!!.getBoundsInScreen(bounds)
            device.click(bounds.centerX(), bounds.centerY())
            device.waitForIdle()
            Thread.sleep(500)
            assertNotNull("a tap on a dimmed row closed the sheet; texts: ${describeImeTexts()}", imeNodeWithText("Developer mode"))
            assertTrue("a tap on a dimmed row turned developer mode on", KeyboardService.instance?.controller?.developerMode == false)
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { KeyboardService.instance?.controller?.settingsSheetOpen = false }
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
    }

    /**
     * A field that asks for no suggestions gets none — until the gear sheet's row says this app
     * may. `0xa4001` is what YouTube's comment box declares: text, sentence caps, multi-line, no
     * suggestions, and no autocorrect to contradict it.
     */
    @Test
    fun aNoSuggestionsFieldSuggestsOnlyOnceTheAppIsAllowed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.startActivity(
            Intent(context, net.matasar.keyboard.debug.PlainFieldActivity::class.java)
                .putExtra(net.matasar.keyboard.debug.PlainFieldActivity.EXTRA_INPUT_TYPE, 0xa4001)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        assertTrue("the plain field never came up", device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5_000))
        focusFieldAndShowKeyboard()

        // The field asked for none, and this app is not on the list: nothing is offered.
        typeOnKeys("Chek")
        assertTrue("the field did not take the keys (it reads '${fieldText()}')", waitUntil(2_000) { fieldText() == "Chek" })
        assertTrue("a candidate showed though the field asked for none", !imeHasCandidate("check"))

        // The gear sheet offers the row here, because allowing the app would change something.
        openGearSheet()
        assertTrue(
            "the gear sheet never offered the row (offered=${KeyboardService.instance?.controller?.suggestInAppOffered}); texts: ${describeImeTexts()}",
            waitUntil(3_000) { imeNodeWithText("Suggest in this app") != null },
        )
        // Tapped at its own bounds: the row's click sits on the Row, and the node that carries the
        // text is the Text inside it.
        val bounds = android.graphics.Rect()
        imeNodeWithText("Suggest in this app")!!.getBoundsInScreen(bounds)
        device.click(bounds.centerX(), bounds.centerY())
        assertTrue(
            "the sheet stayed open after the row was tapped; texts: ${describeImeTexts()}",
            waitUntil(3_000) { imeNodeWithText("Developer mode") == null },
        )

        try {
            // Same field, same app: the strip starts working without leaving it.
            typeOnKeys(" chek")
            assertTrue(
                "no candidate after allowing the app; the keyboard shows: ${describeImeTexts()}",
                waitUntil(3_000) { imeHasCandidate("check") },
            )
        } finally {
            // The answer is persisted for this package, so it would outlive the test.
            kotlinx.coroutines.runBlocking { Prefs(context).setSuggestInApp(context.packageName, false) }
        }
    }

    /**
     * An app allowed earlier suggests in a field opened afresh: the answer is read back from the
     * store when the field starts, not left over in the state a tap set.
     */
    @Test
    fun aFieldAllowedEarlierSuggestsWhenOpenedAgain() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        kotlinx.coroutines.runBlocking { Prefs(context).setSuggestInApp(context.packageName, true) }
        try {
            context.startActivity(
                Intent(context, net.matasar.keyboard.debug.PlainFieldActivity::class.java)
                    .putExtra(net.matasar.keyboard.debug.PlainFieldActivity.EXTRA_INPUT_TYPE, 0xa4001)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            assertTrue("the plain field never came up", device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5_000))
            focusFieldAndShowKeyboard()
            typeOnKeys("chek")
            assertTrue(
                "the stored answer did not come back with the field; the keyboard shows: ${describeImeTexts()}",
                waitUntil(3_000) { imeHasCandidate("check") },
            )
        } finally {
            // The answer is persisted for this package, so it would outlive the test.
            kotlinx.coroutines.runBlocking { Prefs(context).setSuggestInApp(context.packageName, false) }
        }
    }

    /** The gear sheet's Number row puts the digits over the letters, and they type. */
    @Test
    fun theGearSheetNumberRowTypesDigitsFromTheLettersPage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.startActivity(
            Intent(context, net.matasar.keyboard.debug.PlainFieldActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        assertTrue("the plain field never came up", device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5_000))
        focusFieldAndShowKeyboard()
        assertTrue("the letters page had digits before the row was on", waitForImeNode("1", timeoutMs = 500) == null)

        openGearSheet()
        val bounds = android.graphics.Rect()
        assertTrue("the gear sheet has no Number row; texts: ${describeImeTexts()}", waitUntil(3_000) { imeNodeWithText("Number row") != null })
        imeNodeWithText("Number row")!!.getBoundsInScreen(bounds)
        device.click(bounds.centerX(), bounds.centerY())
        try {
            assertTrue(
                "the sheet stayed open after the row was tapped; texts: ${describeImeTexts()}",
                waitUntil(3_000) { imeNodeWithText("Developer mode") == null },
            )
            assertTrue("no digit key on the letters page; the keyboard shows: ${describeImeNodes()}", waitForImeNode("1") != null)
            typeOnKeys("12")
            assertTrue("the digits did not reach the field (it reads '${fieldText()}')", waitUntil(2_000) { fieldText() == "12" })
        } finally {
            // The switch is persisted, so it would outlive the test.
            kotlinx.coroutines.runBlocking { Prefs(context).setNumberRow(false) }
        }
    }

    /**
     * Opens the gear's sheet. On a phone the toolbar's buttons fold behind a chevron, and a node
     * looked up before the strip recomposed is stale and takes no click — so each attempt looks
     * its node up again.
     */
    private fun openGearSheet() {
        // The gear toggles the sheet, so each click is given time to land before another follows:
        // clicking twice in a row would open it and close it again.
        repeat(3) {
            val button = waitForImeNode("Settings", timeoutMs = 500) ?: waitForImeNode("Show toolbar buttons", timeoutMs = 500)
            button?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            device.waitForIdle()
            if (waitUntil(2_000) { imeNodeWithText("Developer mode") != null }) return
        }
        assertTrue("the gear sheet never opened; the keyboard shows: ${describeImeNodes()}", false)
    }

    /** Clicks each key of [text] by its accessibility node, which is what the keys are named for. */
    private fun typeOnKeys(text: String) {
        for (character in text) {
            val name = if (character == ' ') "Space" else character.toString()
            val key = waitForImeNode(name, ignoreCase = true)
            assertNotNull("no key node '$name'; saw: ${describeImeNodes()}; texts: ${describeImeTexts()}", key)
            assertTrue("the '$name' key did not take a click", key!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            device.waitForIdle()
        }
    }

    /** Whether [word] is offered as a candidate, in whichever case the sentence capital left it. */
    private fun imeHasCandidate(word: String): Boolean =
        imeNodeWithText(word) != null || imeNodeWithText(word.replaceFirstChar { it.uppercase() }) != null

    /** Every text in the keyboard window, for a failure message. */
    private fun describeImeTexts(): String {
        val ime = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
            .firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return "no keyboard window"
        val texts = mutableListOf<String>()
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo) {
            node.text?.let { texts += "'$it'" }
            for (i in 0 until node.childCount) node.getChild(i)?.let(::walk)
        }
        ime.root?.let(::walk)
        return texts.joinToString()
    }

    /** The first node in the keyboard window whose own text is [text]: a candidate, a sheet row. */
    private fun imeNodeWithText(text: String): android.view.accessibility.AccessibilityNodeInfo? {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val ime = automation.windows.firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return null
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
            if (node.text?.toString() == text) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { child -> walk(child)?.let { return it } }
            return null
        }
        return ime.root?.let(::walk)
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

    /**
     * What holding a key opens — the accents, the cursor trackpad — never reaches a screen
     * reader, which takes the finger for touch exploration. Each is an action on the key's node
     * instead: this asks the node for them and performs them, which is what TalkBack does when
     * the user picks one from its Actions menu.
     */
    @Test
    fun keysCarryTheirLongPressActionsWhileExploringByTouch() {
        // The keyboard has to be up before exploring starts: after it, a tap explores instead.
        focusFieldAndShowKeyboard()
        val field = device.findObject(By.clazz("android.widget.EditText"))
        exploreByTouch(true)
        try {
            assertTrue("the keys carry no actions while exploring", waitUntil(5_000) { imeNodeWithAction("Move cursor left") != null })

            // An accent, typed from the e key's own actions.
            val e = waitForImeNode("e", ignoreCase = true)
            assertNotNull("no node for the e key; saw: ${describeImeNodes()}", e)
            val accent = e!!.actionList.firstOrNull { it.label?.toString()?.lowercase() == "é" }
            assertNotNull("the e key offers no é action; it offers ${e.actionList.mapNotNull { it.label }}", accent)
            assertTrue(e.performAction(accent!!.id))
            assertTrue("é was not typed (field: '${fieldText()}')", waitUntil(2_000) { fieldText()?.lowercase()?.endsWith("é") == true })

            // The cursor moves the trackpad makes, without the trackpad: type, step left, type.
            field.text = ""
            typeThroughNodes("ab")
            assertTrue("typing through the nodes gave '${fieldText()}', not 'ab'", waitUntil(2_000) { fieldText()?.lowercase() == "ab" })
            val space = imeNodeWithAction("Move cursor left")
            assertNotNull("Space offers no cursor move", space)
            val left = space!!.actionList.first { it.label?.toString() == "Move cursor left" }
            assertTrue(space.performAction(left.id))
            typeThroughNodes("c")
            assertTrue("the cursor did not move left (field: '${fieldText()}')", waitUntil(2_000) { fieldText()?.lowercase() == "acb" })
        } finally {
            exploreByTouch(false)
        }
    }

    /** The globe holds the language picker behind a long press; it is an action too. */
    @Test
    fun theGlobeOffersTheLanguagePickerWhileExploringByTouch() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(context)
        runBlocking { prefs.setLanguageEnabled("uk", true) }
        focusFieldAndShowKeyboard()
        exploreByTouch(true)
        try {
            assertNotNull("no globe on the board with two languages on; saw: ${describeImeNodes()}", waitForImeNode("Next language"))
            // The board rebuilds when the second language arrives, so the node is looked up again
            // for each attempt: an action performed on a node from the older tree goes nowhere.
            // The board rebuilds when the second language arrives, so the node is looked up again
            // for each attempt: an action performed on a node from the older tree goes nowhere.
            // The sheet's rows carry text, not a description, which is what to look for.
            var opened = false
            repeat(5) {
                if (opened) return@repeat
                performOnKey("Next language", "Choose language")
                opened = waitUntil(2_000) { device.hasObject(By.text("Українська")) }
            }
            assertTrue("the language sheet did not open; saw: ${describeImeNodes()}", opened)
        } finally {
            exploreByTouch(false)
            // Leave no sheet open over the keys: the next test would find no keyboard to show.
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                KeyboardService.instance?.controller?.languageSheetOpen = false
            }
            runBlocking { prefs.setLanguageEnabled("uk", false) }
            Thread.sleep(1_500)
        }
    }

    /** Types [text] by performing each key's click action, the way a screen reader does. */
    private fun typeThroughNodes(text: String) {
        for (letter in text) {
            val key = waitForImeNode(letter.toString(), ignoreCase = true)
            assertNotNull("no node for the $letter key", key)
            assertTrue(key!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
            device.waitForIdle()
        }
    }

    /** The first node in the keyboard window offering an action labelled [label]. */
    private fun imeNodeWithAction(label: String): android.view.accessibility.AccessibilityNodeInfo? {
        val ime = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
            .firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return null
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
            if (node.actionList.any { it.label?.toString() == label }) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { child -> walk(child)?.let { return it } }
            return null
        }
        return ime.root?.let(::walk)
    }

    /**
     * Turns the instrumentation's own accessibility connection into an exploring one, or back.
     * The flags it had are put back exactly: UiDevice keeps its own in there, and losing them
     * leaves every later test unable to find a thing.
     */
    private fun exploreByTouch(on: Boolean) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        if (on) {
            flagsBeforeExploring = info.flags
            info.flags = info.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        } else {
            info.flags = flagsBeforeExploring ?: return
            flagsBeforeExploring = null
        }
        automation.serviceInfo = info
        // Asking is asynchronous: without waiting for the real state, the test runs on before
        // exploring starts, or the next test taps while it is still on and never sees a keyboard.
        val manager = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(android.view.accessibility.AccessibilityManager::class.java)
        assertTrue(
            "touch exploration did not turn ${if (on) "on" else "off"}",
            waitUntil(10_000) { manager.isTouchExplorationEnabled == on },
        )
        device.waitForIdle()
    }

    /** Performs the action labelled [action] on the key described [description], freshly looked up. */
    private fun performOnKey(description: String, action: String): Boolean {
        val node = waitForImeNode(description, timeoutMs = 2_000) ?: return false
        val entry = node.actionList.firstOrNull { it.label?.toString() == action } ?: return false
        return node.performAction(entry.id)
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
     * Runs after [zGlidesUkrainian] (name order), and like it switches the language and back.
     * Ukrainian ь and Russian ь are the same key to Compose (label and action), so the key that
     * stays on screen across the switch must still hold Russian ь's accents: before the fix it
     * kept Ukrainian ь's listener, and a long press typed ь (no accents there) instead of ъ.
     */
    @Test
    fun zLongPressOffersTheAccentsOfTheLanguageOnScreen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(context)
        runBlocking {
            prefs.setLanguageEnabled("uk", true)
            prefs.setLanguageEnabled("ru", true)
            prefs.setCurrentLanguage("uk")
        }
        try {
            Thread.sleep(2_000) // the Ukrainian board is composed, ь included
            keyCentre("ь")
            runBlocking { prefs.setCurrentLanguage("ru") }
            assertNotNull("the Russian board never showed: ${describeImeNodes()}", waitForImeNode("ы", ignoreCase = true)) // no ы key on the Ukrainian board
            val key = keyCentre("ь")
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val downTime = SystemClock.uptimeMillis()
            fun inject(action: Int) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, key.x.toFloat(), key.y.toFloat(), 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                assertTrue("injecting action $action failed", instrumentation.uiAutomation.injectInputEvent(event, true))
                event.recycle()
            }
            inject(MotionEvent.ACTION_DOWN)
            SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 400)
            inject(MotionEvent.ACTION_UP)
            // Releasing where the finger went down picks the first accent; the field may capitalise it.
            val typed = waitUntil(3_000) { fieldText()?.lowercase() == "ъ" }
            assertTrue("expected ъ from a long press on Russian ь, field holds '${fieldText()}'", typed)
        } finally {
            runBlocking {
                prefs.setCurrentLanguage("en_US")
                prefs.setLanguageEnabled("ru", false)
                prefs.setLanguageEnabled("uk", false)
            }
            Thread.sleep(1_500)
        }
    }

    /**
     * Runs last (name order) and restores the languages. Portuguese's spelling picks its word
     * list while Portuguese is open: "proj" completes to the Brazilian "projeto", and after the
     * switch to Portugal to "projecto", which the Portuguese list spells and the Brazilian one
     * ranks below it.
     */
    @Test
    fun zSpellingPicksPortuguesesWordListWhileItIsOpen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(context)
        runBlocking {
            prefs.setLanguageEnabled("pt", true)
            prefs.setCurrentLanguage("pt")
            prefs.setPortugueseSpelling(net.matasar.keyboard.layout.PortugueseSpelling.BRAZIL)
        }
        try {
            Thread.sleep(2_000) // the Portuguese board is up and the Brazilian list loaded
            typeOnKeys("proj")
            assertTrue("the field reads '${fieldText()}', not proj", waitUntil(3_000) { fieldText()?.lowercase() == "proj" })
            assertTrue("no 'projeto' with Brazilian spelling; the keyboard shows: ${describeImeTexts()}", waitUntil(5_000) { imeHasCandidate("projeto") })
            runBlocking { prefs.setPortugueseSpelling(net.matasar.keyboard.layout.PortugueseSpelling.PORTUGAL) }
            Thread.sleep(2_000) // the Portuguese list loads
            device.findObject(By.clazz("android.widget.EditText"))?.text = ""
            // The field reports the cleared text back; a key typed before that lands is lost.
            assertTrue("the field did not clear", waitUntil(2_000) { fieldText().isNullOrEmpty() })
            device.waitForIdle()
            typeOnKeys("proj")
            assertTrue("the field reads '${fieldText()}', not proj", waitUntil(3_000) { fieldText()?.lowercase() == "proj" })
            assertTrue("no 'projecto' with Portugal's spelling; the keyboard shows: ${describeImeTexts()}", waitUntil(5_000) { imeHasCandidate("projecto") })
            assertTrue("'projeto' is still offered after the switch: ${describeImeTexts()}", !imeHasCandidate("projeto"))
        } finally {
            runBlocking {
                prefs.setPortugueseSpelling(net.matasar.keyboard.layout.PortugueseSpelling.PORTUGAL)
                prefs.setCurrentLanguage("en_US")
                prefs.setLanguageEnabled("pt", false)
            }
            Thread.sleep(1_500)
        }
    }

    /**
     * The languages are on their own screen: Settings shows the enabled ones and a button.
     * Switching Croatian on there turns it on for the keyboard, keeps its row where it was (under
     * More languages, until the screen opens again), and Settings names it on the way back.
     * Named to run last: the test after it in the same run could not find the Settings field,
     * for a reason not yet found (it passes alone, and so do the others).
     */
    @Test
    fun zzLanguagesScreenSwitchesALanguageInPlace() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = Prefs(context)
        try {
            // Hide the keyboard the field opened; Back with none up would leave Settings instead.
            if (device.executeShellCommand("dumpsys input_method").contains("mIsInputViewShown=true")) {
                device.pressBack()
                waitUntil(2_000) { !device.executeShellCommand("dumpsys input_method").contains("mIsInputViewShown=true") }
            }
            var button = device.findObject(By.text("Choose languages"))
            repeat(20) {
                if (button != null) return@repeat
                slowScroll(up = true)
                button = device.findObject(By.text("Choose languages"))
            }
            assertNotNull("no Choose languages button on the Settings screen", button)
            // Near the bottom edge a tap lands on the navigation bar: bring the button up first.
            if (button!!.visibleCenter.y > device.displayHeight * 2 / 3) slowScroll(up = false)
            var opened = false
            repeat(2) {
                if (opened) return@repeat
                device.findObject(By.text("Choose languages"))?.click()
                opened = device.wait(Until.hasObject(By.text("More languages")), 3_000)
            }
            assertTrue("the Languages screen never opened", opened)
            val croatian = "Hrvatski · Croatian"
            var row = device.findObject(By.text(croatian))
            repeat(20) {
                if (row != null) return@repeat
                slowScroll(up = false)
                row = device.findObject(By.text(croatian))
            }
            assertNotNull("no Croatian row", row)
            val before = row!!.visibleBounds
            // The switch sits at the row's end, apart from the text.
            device.click(device.displayWidth - before.height(), before.centerY())
            val on = waitUntil(3_000) { runBlocking { "hr" in prefs.settings.first().enabledLanguages } }
            assertTrue("Croatian was not switched on", on)
            device.waitForIdle()
            assertEquals("the Croatian row moved when switched", before.top, device.findObject(By.text(croatian)).visibleBounds.top)
            device.pressBack()
            assertTrue("Settings does not name Croatian after the switch", device.wait(Until.hasObject(By.textContains("Hrvatski")), 5_000))
        } finally {
            runBlocking { prefs.setLanguageEnabled("hr", false) }
        }
    }

    /**
     * Scrolls a screen by about a third, slowly enough not to fling: a quick drag flings past what
     * is being looked for, and the next one cannot bring it back.
     */
    private fun slowScroll(up: Boolean) {
        val x = device.displayWidth / 2
        val (from, to) = device.displayHeight * 2 / 3 to device.displayHeight / 3
        if (up) device.swipe(x, to, x, from, 100) else device.swipe(x, from, x, to, 100)
        device.waitForIdle()
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
