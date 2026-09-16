package net.matasar.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import net.matasar.keyboard.layout.KeyIcon
import net.matasar.keyboard.layout.LayerId

/** What kind of field has focus, as far as the keyboard cares. */
enum class FieldKind { TEXT, NUMBER, PASSWORD, TERMINAL }

/** Classifies a field from its input type. Pure, so it is unit-tested without Android. */
fun fieldKindOf(inputType: Int): FieldKind {
    if (inputType == InputType.TYPE_NULL) return FieldKind.TERMINAL
    val klass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (klass) {
        InputType.TYPE_CLASS_NUMBER ->
            if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) FieldKind.PASSWORD else FieldKind.NUMBER
        InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> FieldKind.NUMBER
        else -> when (variation) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            -> FieldKind.PASSWORD
            else -> FieldKind.TEXT
        }
    }
}

/** The layer a field opens on: digits for number and phone fields, letters otherwise. */
fun FieldKind.initialLayer(): LayerId = if (this == FieldKind.NUMBER) LayerId.SYMBOLS else LayerId.LETTERS

/** The icon the Enter key shows for the field's action. */
fun enterIconFor(editorActionId: Int?): KeyIcon = when (editorActionId) {
    EditorInfo.IME_ACTION_SEARCH -> KeyIcon.SEARCH
    EditorInfo.IME_ACTION_SEND -> KeyIcon.SEND
    EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> KeyIcon.ARROW_RIGHT
    EditorInfo.IME_ACTION_DONE -> KeyIcon.CHECK
    else -> KeyIcon.ENTER
}
