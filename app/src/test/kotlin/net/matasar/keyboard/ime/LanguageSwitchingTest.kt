package net.matasar.keyboard.ime

import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.layout.BulgarianLayout
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.Language
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.settings.GlobeTap
import net.matasar.keyboard.layout.LayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguageSwitchingTest {

    private val port = FakeEditorPort()
    private val switched = mutableListOf<Language>()
    private val nextInputMethodCalls = mutableListOf<Unit>()
    private val controller = KeyboardController(InputDispatcher(port), clock = { 1000L }).apply {
        onLanguageChanged = { switched += it }
        systemActions = object : SystemActions {
            override fun hideKeyboard() = Unit
            override fun switchToNextInputMethod() { nextInputMethodCalls += Unit }
        }
    }

    private fun key(label: String) = controller.phoneLayout.layer(LayerId.LETTERS).rows.flatMap { it.keys }.first { it.label == label }

    private fun globe() = controller.phoneLayout.layer(net.matasar.keyboard.layout.LayerId.LETTERS).rows[3].keys.firstOrNull { it.action == KeyAction.SwitchLanguage }

    @Test
    fun `one enabled language means no globe and the system switcher on a switch action`() {
        assertFalse(controller.withGlobe)
        assertNull(globe())
        controller.onKey(net.matasar.keyboard.layout.Key("globe", KeyAction.SwitchLanguage))
        assertEquals(1, nextInputMethodCalls.size)
        assertEquals(Languages.english, controller.language)
    }

    @Test
    fun `set to Next language, the globe cycles them in order and reports each change`() {
        controller.globeTap = GlobeTap.NEXT
        controller.enabledLanguages = setOf("en_US", "uk", "fr")
        assertTrue(controller.withGlobe)
        val globe = globe()!!
        controller.onKey(globe)
        assertEquals(Languages.ukrainian, controller.language)
        controller.onKey(globe)
        assertEquals(Languages.french, controller.language)
        controller.onKey(globe)
        assertEquals(Languages.english, controller.language)
        assertEquals(listOf(Languages.ukrainian, Languages.french, Languages.english), switched)
        assertTrue(nextInputMethodCalls.isEmpty())
    }

    @Test
    fun `the letters layer and the space bar follow the language`() {
        controller.enabledLanguages = setOf("en_US", "ru")
        controller.switchLanguage(Languages.russian)
        val letters = controller.phoneLayout.layer(net.matasar.keyboard.layout.LayerId.LETTERS)
        assertEquals("й", letters.rows[0].keys.first().label)
        assertEquals("Русский", letters.rows[3].keys.first { it.action == KeyAction.Space }.label)
        assertEquals(11f, letters.units) // the iPhone's eleven keys, ъ on a long press
    }

    @Test
    fun `bulgarian's layout setting swaps its boards and leaves the other languages alone`() {
        controller.enabledLanguages = setOf("en_US", "ru", "bg")
        controller.switchLanguage(Languages.bulgarian)
        fun topRow() = controller.phoneLayout.layer(LayerId.LETTERS).rows[0].keys.joinToString("") { it.label }
        fun wideTop() = controller.wideLayout.layer(LayerId.LETTERS).rows[1].keys.drop(1).take(11).joinToString("") { it.label }
        assertEquals("явертъуиопч", topRow())
        controller.bulgarianLayout = BulgarianLayout.STANDARD
        assertEquals("уеишщксдзцб", topRow())
        assertEquals("уеишщксдзцб", wideTop())
        assertEquals(Languages.bulgarian, controller.language) // the same language, on another board
        controller.switchLanguage(Languages.russian)
        assertEquals("йцукенгшщзх", topRow())
        controller.switchLanguage(Languages.bulgarian)
        controller.bulgarianLayout = BulgarianLayout.PHONETIC
        assertEquals("явертъуиопч", topRow())
    }

    @Test
    fun `set to Last used, a first tap goes back and taps in a row go on through the list, as on an iPhone`() {
        assertEquals(GlobeTap.LAST_USED, controller.globeTap) // the default
        controller.enabledLanguages = setOf("en_US", "uk", "fr")
        val globe = globe()!!
        fun tap() = controller.onKey(globe).let { controller.language }
        // Nothing to go back to yet: on through the list, all the way round.
        assertEquals(listOf(Languages.ukrainian, Languages.french, Languages.english), List(3) { tap() })
        // A letter ends the run: the next tap goes back to French, where English was switched from,
        // and taps after it go on round from there.
        controller.onKey(key("a"))
        assertEquals(listOf(Languages.french, Languages.english, Languages.ukrainian), List(3) { tap() })
        // A pick from the list counts as a switch and ends the run too.
        controller.switchLanguage(Languages.french)
        assertEquals(Languages.ukrainian, tap())
        assertEquals(Languages.french, tap())
    }

    @Test
    fun `a new field ends a run of globe taps`() {
        controller.enabledLanguages = setOf("en_US", "uk", "fr")
        controller.onKey(globe()!!) // en to uk
        controller.onStartInput(null)
        controller.onKey(globe()!!)
        assertEquals(Languages.english, controller.language) // back, not on to French
    }

    @Test
    fun `Android echoing the switch back as a subtype change does not end a run of globe taps`() {
        controller.enabledLanguages = setOf("en_US", "uk", "fr")
        controller.onKey(globe()!!) // en to uk
        controller.switchLanguage(controller.language) // onCurrentInputMethodSubtypeChanged
        controller.onKey(globe()!!)
        assertEquals(Languages.byTag("fr"), controller.language) // on, not back to English
    }

    @Test
    fun `the globe's target, which TalkBack names, is where a tap goes in either setting`() {
        controller.enabledLanguages = setOf("en_US", "uk", "fr")
        for (tap in GlobeTap.entries) {
            controller.globeTap = tap
            repeat(4) {
                val target = controller.globeTarget()
                controller.onKey(globe()!!)
                assertEquals(target, controller.language, "$tap")
            }
        }
    }

    @Test
    fun `a language to go back to that was switched off, or is the current one, is passed over for the next`() {
        controller.enabledLanguages = setOf("en_US", "uk", "fr")
        controller.previousLanguage = Languages.german // not enabled
        controller.onKey(globe()!!)
        assertEquals(Languages.ukrainian, controller.language)
        controller.previousLanguage = Languages.ukrainian // the current one
        controller.onKey(globe()!!)
        assertEquals(Languages.french, controller.language)
    }

    @Test
    fun `with two languages both settings toggle between them`() {
        for (tap in GlobeTap.entries) {
            controller.globeTap = tap
            controller.enabledLanguages = setOf("en_US", "uk")
            controller.switchLanguage(Languages.english)
            controller.onKey(globe()!!)
            assertEquals(Languages.ukrainian, controller.language, "$tap")
            controller.onKey(globe()!!)
            assertEquals(Languages.english, controller.language, "$tap")
        }
    }

    @Test
    fun `a long press on the globe opens the picker and picking switches and closes it`() {
        controller.enabledLanguages = setOf("en_US", "de")
        controller.onKeyLongPress(globe()!!)
        assertTrue(controller.languageSheetOpen)
        controller.switchLanguage(Languages.german)
        assertFalse(controller.languageSheetOpen)
        assertEquals(Languages.german, controller.language)
    }

    @Test
    fun `the wide board follows the language and its globe cycles`() {
        controller.enabledLanguages = setOf("en_US", "ru")
        controller.switchLanguage(Languages.russian)
        val board = controller.wideLayout.layer(net.matasar.keyboard.layout.LayerId.LETTERS)
        assertEquals("й", board.rows[1].keys[1].label)
        assertEquals("Русский", board.rows[4].keys.first { it.action == KeyAction.Space }.label)
        val globe = board.rows[4].keys.first { it.label == "globe" }
        controller.onKey(globe)
        assertEquals(Languages.english, controller.language)
        assertEquals("q", controller.wideLayout.layer(net.matasar.keyboard.layout.LayerId.LETTERS).rows[1].keys[1].label)
        controller.enabledLanguages = setOf("en_US")
        assertNull(controller.wideLayout.layer(net.matasar.keyboard.layout.LayerId.LETTERS).rows[4].keys.firstOrNull { it.label == "globe" })
    }

    @Test
    fun `letters show their latin slot while ctrl is armed and their own letter otherwise`() {
        controller.enabledLanguages = setOf("en_US", "ru")
        controller.switchLanguage(Languages.russian)
        controller.onStartInput(android.view.inputmethod.EditorInfo().apply { inputType = android.text.InputType.TYPE_NULL })
        val board = controller.wideLayout.layer(net.matasar.keyboard.layout.LayerId.LETTERS)
        val q = board.rows[1].keys[1]
        val ctrl = board.rows[4].keys.first { it.action == KeyAction.Modifier(net.matasar.keyboard.layout.ModifierKey.CTRL) }
        val shift = board.rows[3].keys.first()
        val fn = board.rows[4].keys.first { it.action == KeyAction.Modifier(net.matasar.keyboard.layout.ModifierKey.FN) }
        assertEquals("й", controller.displayLabel(q))
        controller.onKey(ctrl)
        assertEquals("Q", controller.displayLabel(q))
        controller.onKey(q)
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_Q to (android.view.KeyEvent.META_CTRL_ON or android.view.KeyEvent.META_CTRL_LEFT_ON)), port.keys)
        assertEquals("й", controller.displayLabel(q))
        controller.onKey(shift)
        assertEquals("Й", controller.displayLabel(q))
        controller.onKey(q)
        controller.onKey(fn)
        assertEquals("й", controller.displayLabel(q))
    }

    @Test
    fun `restoring a language does not report it and switching to the same language is a no-op`() {
        controller.restoreLanguage(Languages.polish)
        assertEquals(Languages.polish, controller.language)
        controller.switchLanguage(Languages.polish)
        assertTrue(switched.isEmpty())
    }
}
