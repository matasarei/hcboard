package net.matasar.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.Latch
import net.matasar.keyboard.input.TrackpadGesture
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.KeyboardLayout
import net.matasar.keyboard.layout.LayerId
import net.matasar.keyboard.layout.PhoneLayout

/**
 * Keyboard state and the meaning of every key tap. Pure Kotlin over Compose snapshot state, so
 * the UI observes it and the tests drive it without a device.
 */
class KeyboardController(
    private val dispatcher: InputDispatcher,
    val layout: KeyboardLayout = PhoneLayout,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var layer: LayerId by mutableStateOf(LayerId.LETTERS)
        private set

    var shift: Latch by mutableStateOf(Latch())
        private set

    /** The editor action Enter performs, or null when Enter should be a real key. */
    var editorActionId: Int? by mutableStateOf(null)
        private set

    /** True while space is held and the finger moves the cursor. Keys hide their labels. */
    var trackpad: Boolean by mutableStateOf(false)
        private set

    private var trackpadGesture: TrackpadGesture? = null

    /** What a letter key shows and commits right now. */
    fun displayLabel(key: Key): String = when (val action = key.action) {
        is KeyAction.Letter -> if (shift.active) action.upper else action.lower
        else -> key.label
    }

    fun onStartInput(info: EditorInfo?) {
        layer = LayerId.LETTERS
        shift = Latch()
        editorActionId = info?.let { editorActionFor(it.imeOptions, it.inputType) }
    }

    fun onFinishInput() {
        shift = Latch()
        endTrackpad()
    }

    fun onKey(key: Key) {
        when (val action = key.action) {
            is KeyAction.Letter -> {
                dispatcher.commitText(if (shift.active) action.upper else action.lower)
                shift = shift.consume()
            }
            is KeyAction.Text -> dispatcher.commitText(action.text)
            KeyAction.Space -> dispatcher.commitText(" ")
            KeyAction.Backspace -> dispatcher.backspace()
            KeyAction.Enter -> dispatcher.enter(editorActionId)
            KeyAction.Shift -> shift = shift.tap(clock())
            is KeyAction.SwitchLayer -> layer = action.layer
            is KeyAction.KeyCode -> dispatcher.sendKey(action.keyCode)
            is KeyAction.Modifier, KeyAction.HideKeyboard, KeyAction.SwitchLanguage -> Unit // step 5 and 6
        }
    }

    /** The accent candidates a long press on [key] offers, in the current case. */
    fun accentsFor(key: Key): List<String> =
        if (shift.active) key.longPress.map { it.uppercase() } else key.longPress

    /** A chosen accent goes in like a letter: it consumes a one-shot shift. */
    fun commitAccent(text: String) {
        dispatcher.commitText(text)
        shift = shift.consume()
    }

    fun startTrackpad(stepPx: Float) {
        trackpadGesture = TrackpadGesture(stepPx)
        trackpad = true
    }

    /** More horizontal travel while space is held. */
    fun trackpadMove(dxPx: Float) {
        val steps = trackpadGesture?.move(dxPx) ?: return
        if (steps != 0) dispatcher.moveCursor(steps)
    }

    fun endTrackpad() {
        trackpadGesture = null
        trackpad = false
    }

    /** Backspace held down: one more deletion per repeat tick. */
    fun onKeyRepeat(key: Key) {
        if (key.action == KeyAction.Backspace) dispatcher.backspace()
    }

    fun onKeyLongPress(key: Key) {
        if (key.action == KeyAction.Shift) shift = shift.longPress()
    }

    companion object {
        /**
         * The action Enter should perform for a field: the field's own IME action when it has
         * one and allows it, otherwise null so a real Enter key goes through (newline in
         * multi-line fields, return in terminals).
         */
        fun editorActionFor(imeOptions: Int, inputType: Int): Int? {
            val multiLine = inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
            val noEnterAction = imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
            val action = imeOptions and EditorInfo.IME_MASK_ACTION
            val hasAction = action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED
            return if (hasAction && !noEnterAction && !multiLine) action else null
        }
    }
}
