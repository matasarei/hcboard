package net.matasar.keyboard.ime

import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.LayerId
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.ModifierKey
import net.matasar.keyboard.layout.sixtyPercentLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a key shows is what it would type: the glyph follows Shift, Fn and Fn+Shift so the board
 * says what it is about to do rather than making the user remember it.
 */
class KeyLabelTest {

    private val port = FakeEditorPort()
    private var now = 1000L
    private val controller = KeyboardController(InputDispatcher(port), clock = { now })

    private val board = sixtyPercentLayer(Languages.ukrainian, withGlobe = false)
    private val keys = board.rows.flatMap { it.keys }
    private fun key(label: String) = keys.first { it.label == label }

    private val shiftKey = key("Shift")
    private val fnKey = keys.first { it.action == KeyAction.Modifier(ModifierKey.FN) }
    private val ctrlKey = keys.first { it.action == KeyAction.Modifier(ModifierKey.CTRL) }

    private fun shown(label: String) = controller.displayLabel(key(label))

    @Test
    fun `at rest a key shows its own symbol`() {
        assertEquals("1", shown("1"))
        assertEquals("й", shown("й"))
        assertEquals("х", shown("х"))
        assertEquals(".", shown("."))
    }

    @Test
    fun `shift shows what shift would type, punctuation included`() {
        controller.onKey(shiftKey)
        assertEquals("!", shown("1"))
        assertEquals("_", shown("-"))
        assertEquals(",", shown("."))
        assertEquals("Й", shown("й"))
    }

    @Test
    fun `fn shows the fn meaning as the glyph`() {
        controller.onKey(fnKey)
        assertEquals("F1", shown("1"))
        assertEquals("F12", shown("="))
        assertEquals("`", shown("Esc"))
        assertEquals("↑", shown("ш"))
        assertEquals("Home", shown("г"))
        assertEquals("[", shown("х"))
        assertEquals(",", shown("б"))
        // The `.` key has no Fn meaning of its own (б and ю carry `,<` and `.>`): it stays `.`.
        assertEquals(".", shown("."))
    }

    @Test
    fun `fn and shift together show the shifted fn symbol`() {
        controller.onKey(fnKey)
        controller.onKey(shiftKey)
        assertTrue(controller.shiftActive)
        assertEquals("{", shown("х"))
        assertEquals("}", shown("ї"))
        assertEquals("<", shown("б"))
        assertEquals(">", shown("ю"))
        assertEquals("~", shown("Esc"))
        // A letter with no fn meaning of its own still follows shift.
        assertEquals("Ф", shown("ф"))
        // An f-key has no shifted form; shift must not blank it.
        assertEquals("F1", shown("1"))
    }

    @Test
    fun `an icon steps aside only for an fn meaning that has a name`() {
        val backspace = keys.first { it.action == KeyAction.Backspace }
        val enter = keys.first { it.action == KeyAction.Enter }
        assertTrue(controller.showsIcon(backspace))
        controller.onKey(fnKey)
        assertFalse(controller.showsIcon(backspace))
        assertEquals("Del", controller.displayLabel(backspace))
        assertTrue(controller.showsIcon(enter))
    }

    @Test
    fun `a combination modifier still shows the slot, so ctrl combos read as they send`() {
        controller.onKey(ctrlKey)
        assertEquals("Q", shown("й"))
        assertEquals("[", shown("х"))
        assertEquals("]", shown("ї"))
        assertEquals("1", shown("1"))
    }

    @Test
    fun `a key with no shifted symbol of its own keeps it under shift`() {
        val tab = keys.first { it.label == "Tab" }
        assertEquals("\\", shown("\\"))
        controller.onKey(shiftKey)
        assertEquals("Tab", controller.displayLabel(tab))
        assertEquals("|", shown("\\"))
    }

    @Test
    fun `the phone board's letters follow shift as they always did`() {
        val phone = Languages.russian.lettersLayer(withGlobe = false)
        val a: Key = phone.rows[1].keys.first { it.label == "ф" }
        assertEquals("ф", controller.displayLabel(a))
        controller.onKey(shiftKey)
        assertEquals("Ф", controller.displayLabel(a))
    }

    @Test
    fun `a legend is live only when its modifier is what makes the glyph`() {
        controller.onKey(fnKey)
        controller.onKey(shiftKey)
        // Fn+Shift on a digit types F1 whether or not Shift is armed, so only the Fn legend is live.
        assertTrue(controller.fnLive(key("1")))
        assertFalse(controller.shiftLive(key("1")))
        // On a bracket slot Shift does change it, from [ to {, so both legends are live.
        assertTrue(controller.fnLive(key("х")))
        assertTrue(controller.shiftLive(key("х")))
        // A letter with no Fn meaning of its own: Shift alone makes its glyph.
        assertFalse(controller.fnLive(key("ф")))
        assertTrue(controller.shiftLive(key("ф")))
    }

    @Test
    fun `with one modifier armed its own legend is the live one`() {
        // Nothing armed, nothing live.
        assertFalse(controller.fnLive(key("1")))
        assertFalse(controller.shiftLive(key("1")))
        controller.onKey(shiftKey)
        assertTrue(controller.shiftLive(key("1")))
        assertFalse(controller.fnLive(key("1")))
        controller.onStartInput(null) // a new field releases both
        controller.onKey(fnKey)
        assertTrue(controller.fnLive(key("1")))
        assertFalse(controller.shiftLive(key("1")))
    }

    @Test
    fun `space names the language only when there is another one to switch to`() {
        fun space() = controller.phoneLayout.layers.getValue(LayerId.LETTERS).rows.last().keys.first { it.action == KeyAction.Space }
        assertEquals("", controller.displayLabel(space()))

        controller.enabledLanguages = setOf(Languages.english.tag, Languages.ukrainian.tag)
        assertEquals(Languages.english.nativeName, controller.displayLabel(space()))
        // The key keeps its name for TalkBack and its id either way; only the glyph goes.
        assertEquals(Languages.english.nativeName, space().label)
    }
}
