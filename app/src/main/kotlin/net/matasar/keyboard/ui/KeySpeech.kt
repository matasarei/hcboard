package net.matasar.keyboard.ui

import android.view.KeyEvent
import androidx.annotation.StringRes
import net.matasar.keyboard.R
import net.matasar.keyboard.input.LatchState
import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.LayerId
import net.matasar.keyboard.layout.ModifierKey

/**
 * What TalkBack says for a key: the text it would type now, read as it is, or the name of what
 * it does, from the string resources. Kept apart from Compose so the rules run on the JVM.
 */
sealed interface Spoken {
    data class Text(val text: String) : Spoken
    data class Named(@StringRes val id: Int) : Spoken
}

/**
 * The spoken form of [key], showing [shown] (what [net.matasar.keyboard.ime.KeyboardController.displayLabel]
 * says it types now) and drawn with an icon when [iconShown]. A key whose icon stepped aside for
 * an Fn meaning with a name (backspace reading Del, an arrow reading Home) says that name.
 */
internal fun spokenKey(key: Key, shown: String, iconShown: Boolean): Spoken {
    when (val action = key.action) {
        KeyAction.Space -> return Spoken.Named(R.string.a11y_key_space)
        KeyAction.Enter -> return Spoken.Named(R.string.a11y_key_enter)
        KeyAction.Shift -> return Spoken.Named(R.string.a11y_key_shift)
        KeyAction.CapsLock -> return Spoken.Named(R.string.a11y_key_caps_lock)
        KeyAction.HideKeyboard -> return Spoken.Named(R.string.a11y_key_hide)
        KeyAction.SwitchLanguage -> return Spoken.Named(R.string.a11y_key_next_language)
        KeyAction.Backspace -> if (iconShown) return Spoken.Named(R.string.a11y_key_backspace)
        is KeyAction.Modifier -> return Spoken.Named(modifierName(action.modifier))
        is KeyAction.SwitchLayer -> return Spoken.Named(
            when (action.layer) {
                LayerId.LETTERS -> R.string.a11y_key_letters
                LayerId.SYMBOLS -> R.string.a11y_key_symbols
                LayerId.CODE -> R.string.a11y_key_code
            },
        )
        is KeyAction.KeyCode -> if (iconShown) arrowName(action.keyCode)?.let { return Spoken.Named(it) }
        else -> Unit
    }
    ArrowDirection.fromString(shown)?.let { return Spoken.Named(arrowName(it)) }
    return Spoken.Text(shown.ifEmpty { key.label })
}

/**
 * What TalkBack adds after a key's name: the state of a key that latches (Shift, Caps Lock, the
 * modifiers), from [shift] and [modifier], a held modifier counting as armed, as it looks; null for
 * every other key.
 */
@StringRes
internal fun spokenState(key: Key, shift: LatchState, modifier: (ModifierKey) -> LatchState, held: Set<ModifierKey>): Int? =
    when (val action = key.action) {
        KeyAction.Shift -> latchName(shift)
        KeyAction.CapsLock -> if (shift == LatchState.LOCKED) R.string.a11y_state_on else R.string.a11y_state_off
        is KeyAction.Modifier -> latchName(
            modifier(action.modifier).takeUnless { it == LatchState.IDLE && action.modifier in held } ?: LatchState.ARMED,
        )
        else -> null
    }

/** The page on the keys, as TalkBack announces it when it changes; the letters say their language. */
@StringRes
internal fun layerTitle(layer: LayerId): Int = when (layer) {
    LayerId.LETTERS -> R.string.a11y_layer_letters
    LayerId.SYMBOLS -> R.string.a11y_key_symbols
    LayerId.CODE -> R.string.a11y_key_code
}

@StringRes
private fun latchName(state: LatchState): Int = when (state) {
    LatchState.IDLE -> R.string.a11y_state_off
    LatchState.ARMED -> R.string.a11y_state_armed
    LatchState.LOCKED -> R.string.a11y_state_locked
}

@StringRes
internal fun modifierName(modifier: ModifierKey): Int = when (modifier) {
    ModifierKey.CTRL -> R.string.a11y_key_ctrl
    ModifierKey.ALT -> R.string.a11y_key_alt
    ModifierKey.SHIFT -> R.string.a11y_key_shift
    ModifierKey.META -> R.string.a11y_key_meta
    ModifierKey.FN -> R.string.a11y_key_fn
}

@StringRes
private fun arrowName(keyCode: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT -> R.string.a11y_key_left
    KeyEvent.KEYCODE_DPAD_RIGHT -> R.string.a11y_key_right
    KeyEvent.KEYCODE_DPAD_UP -> R.string.a11y_key_up
    KeyEvent.KEYCODE_DPAD_DOWN -> R.string.a11y_key_down
    else -> null
}

@StringRes
private fun arrowName(direction: ArrowDirection): Int = when (direction) {
    ArrowDirection.LEFT -> R.string.a11y_key_left
    ArrowDirection.RIGHT -> R.string.a11y_key_right
    ArrowDirection.UP -> R.string.a11y_key_up
    ArrowDirection.DOWN -> R.string.a11y_key_down
}
