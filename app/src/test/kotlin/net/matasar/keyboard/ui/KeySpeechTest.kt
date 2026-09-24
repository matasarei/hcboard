package net.matasar.keyboard.ui

import net.matasar.keyboard.R
import net.matasar.keyboard.ime.KeyboardController
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.layout.LayerId
import net.matasar.keyboard.layout.DeveloperStrip
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.LettersLayer
import net.matasar.keyboard.layout.ModifierKey
import net.matasar.keyboard.layout.SymbolsLayer
import net.matasar.keyboard.layout.phoneLayout
import net.matasar.keyboard.layout.sixtyPercentLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeySpeechTest {

    private val controller = KeyboardController(InputDispatcher(FakeEditorPort()), clock = { 1000L })
    private val letters = LettersLayer.rows.flatMap { it.keys }

    /** What the key says on screen now, the way KeyButton asks. */
    private fun spoken(key: Key) = spokenKey(key, controller.displayLabel(key), iconShown = key.icon != null && controller.showsIcon(key))

    @Test
    fun `a letter says what it would type, following shift`() {
        val q = letters.first { it.label == "q" }
        assertEquals(Spoken.Text("q"), spoken(q))
        controller.onKey(letters.first { it.action == KeyAction.Shift })
        assertEquals(Spoken.Text("Q"), spoken(q))
    }

    @Test
    fun `keys that do something rather than type are named`() {
        val keys = phoneLayout(Languages.english, withGlobe = true).layers.values.flatMap { l -> l.rows.flatMap { it.keys } }
        fun named(action: KeyAction) = spoken(keys.first { it.action == action })
        assertEquals(Spoken.Named(R.string.a11y_key_space), named(KeyAction.Space))
        assertEquals(Spoken.Named(R.string.a11y_key_enter), named(KeyAction.Enter))
        assertEquals(Spoken.Named(R.string.a11y_key_backspace), named(KeyAction.Backspace))
        assertEquals(Spoken.Named(R.string.a11y_key_shift), named(KeyAction.Shift))
        assertEquals(Spoken.Named(R.string.a11y_key_next_language), named(KeyAction.SwitchLanguage))
        assertEquals(Spoken.Named(R.string.a11y_key_symbols), named(KeyAction.SwitchLayer(net.matasar.keyboard.layout.LayerId.SYMBOLS)))
        assertEquals(Spoken.Named(R.string.a11y_key_letters), named(KeyAction.SwitchLayer(net.matasar.keyboard.layout.LayerId.LETTERS)))
    }

    @Test
    fun `an arrow is named, and says its Fn meaning once the icon steps aside`() {
        val left = DeveloperStrip.keys.first { it.label == "left" }
        assertEquals(Spoken.Named(R.string.a11y_key_left), spokenKey(left, "left", iconShown = true))
        assertEquals(Spoken.Text("Home"), spokenKey(left, "Home", iconShown = false))
        val backspace = letters.first { it.action == KeyAction.Backspace }
        assertEquals(Spoken.Text("Del"), spokenKey(backspace, "Del", iconShown = false))
    }

    @Test
    fun `each modifier has its own name`() {
        assertEquals(
            ModifierKey.entries.size,
            ModifierKey.entries.map { spokenKey(Key(it.name, KeyAction.Modifier(it)), it.name, iconShown = false) }.toSet().size,
        )
    }

    @Test
    fun `space with no language on it still says Space, never nothing`() {
        val space = letters.first { it.action == KeyAction.Space }
        assertEquals("", controller.displayLabel(space))
        assertEquals(Spoken.Named(R.string.a11y_key_space), spoken(space))
    }

    @Test
    fun `every key on every board says something`() {
        for (language in Languages.all) for (globe in listOf(false, true)) {
            val layers = phoneLayout(language, globe).layers.values + sixtyPercentLayer(language, globe)
            for (key in layers.flatMap { l -> l.rows.flatMap { it.keys } }) {
                val said = spokenKey(key, key.label, iconShown = key.icon != null)
                assertTrue(said is Spoken.Named || (said as Spoken.Text).text.isNotBlank(), "${language.tag} ${key.id}")
            }
        }
    }

    private fun state(key: Key) = spokenState(key, controller.shift.state, controller.modifiers::state, controller.modifiers.held)

    @Test
    fun `shift says off, on for the next key, then locked`() {
        val shift = letters.first { it.action == KeyAction.Shift }
        assertEquals(R.string.a11y_state_off, state(shift))
        controller.onKey(shift)
        assertEquals(R.string.a11y_state_armed, state(shift))
        controller.onKey(shift)
        assertEquals(LatchState.LOCKED, controller.shift.state)
        assertEquals(R.string.a11y_state_locked, state(shift))
    }

    @Test
    fun `caps lock is on only while shift is locked`() {
        val caps = Key("Caps", KeyAction.CapsLock)
        assertEquals(R.string.a11y_state_off, spokenState(caps, LatchState.ARMED, { LatchState.IDLE }, emptySet()))
        assertEquals(R.string.a11y_state_on, spokenState(caps, LatchState.LOCKED, { LatchState.IDLE }, emptySet()))
    }

    @Test
    fun `a modifier follows its latch, and held reads as armed`() {
        val ctrl = Key("Ctrl", KeyAction.Modifier(ModifierKey.CTRL))
        assertEquals(R.string.a11y_state_off, spokenState(ctrl, LatchState.IDLE, { LatchState.IDLE }, emptySet()))
        assertEquals(R.string.a11y_state_armed, spokenState(ctrl, LatchState.IDLE, { LatchState.IDLE }, setOf(ModifierKey.CTRL)))
        assertEquals(R.string.a11y_state_locked, spokenState(ctrl, LatchState.IDLE, { LatchState.LOCKED }, setOf(ModifierKey.CTRL)))
        assertEquals(R.string.a11y_state_armed, spokenState(ctrl, LatchState.IDLE, { if (it == ModifierKey.CTRL) LatchState.ARMED else LatchState.IDLE }, emptySet()))
    }

    @Test
    fun `keys that do not latch have no state`() {
        for (key in letters.filter { it.action != KeyAction.Shift }) assertNull(state(key), key.id)
    }

    @Test
    fun `each page has its own title, and only the letters name the language`() {
        assertEquals(setOf(R.string.a11y_layer_letters, R.string.a11y_key_symbols, R.string.a11y_key_code), LayerId.entries.map(::layerTitle).toSet())
    }

    @Test
    fun `in a password spoken out loud a character key says Dot, and named keys keep their names`() {
        val p = letters.first { it.label == "p" }
        val space = letters.first { it.action == KeyAction.Space }
        val comma = SymbolsLayer.rows.flatMap { it.keys }.first { it.label == "," }
        val tab = Key("Tab", KeyAction.KeyCode(android.view.KeyEvent.KEYCODE_TAB))
        assertEquals(Spoken.Named(R.string.a11y_key_dot), spokenKey(p, "p", iconShown = false, obscured = true))
        assertEquals(Spoken.Named(R.string.a11y_key_dot), spokenKey(comma, ",", iconShown = false, obscured = true))
        assertEquals(Spoken.Named(R.string.a11y_key_space), spokenKey(space, "", iconShown = false, obscured = true))
        assertEquals(Spoken.Text("Tab"), spokenKey(tab, "Tab", iconShown = false, obscured = true))
        assertEquals(Spoken.Text("p"), spokenKey(p, "p", iconShown = false, obscured = false))
    }

    @Test
    fun `only headphones, headsets and hearing aids count as private`() {
        assertTrue(isPrivateOutput(android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(isPrivateOutput(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertTrue(isPrivateOutput(android.media.AudioDeviceInfo.TYPE_HEARING_AID))
        assertTrue(!isPrivateOutput(android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertTrue(!isPrivateOutput(android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
    }

    @Test
    fun `enter says the field's action, and Enter when there is none`() {
        val enter = letters.first { it.action == KeyAction.Enter }
        fun said(action: Int?) = spokenKey(enter, "", iconShown = true, editorAction = action)
        assertEquals(Spoken.Named(R.string.a11y_key_search), said(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH))
        assertEquals(Spoken.Named(R.string.a11y_key_send), said(android.view.inputmethod.EditorInfo.IME_ACTION_SEND))
        assertEquals(Spoken.Named(R.string.a11y_key_go), said(android.view.inputmethod.EditorInfo.IME_ACTION_GO))
        assertEquals(Spoken.Named(R.string.a11y_key_next), said(android.view.inputmethod.EditorInfo.IME_ACTION_NEXT))
        assertEquals(Spoken.Named(R.string.a11y_key_done), said(android.view.inputmethod.EditorInfo.IME_ACTION_DONE))
        assertEquals(Spoken.Named(R.string.a11y_key_enter), said(null))
    }
}
