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
/**
 * Whether word candidates may be read and shown for a field: plain text only, so no passwords,
 * numbers, terminals, addresses or e-mail, and not when the app asks for no suggestions — unless
 * it asks for autocorrect in the same breath, which a search box does.
 */
fun suggestionsAllowed(inputType: Int): Boolean {
    if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
    // A field that asks for no suggestions and for autocorrect in the same breath contradicts
    // itself, and search boxes do it: the Google app's prompt, where Gemini is typed, reports
    // both. Correcting a word needs candidates to correct it with, so the flag that asks for
    // correction wins; a field that asks for no suggestions and nothing else is still obeyed.
    val noSuggestions = inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
    val autoCorrect = inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT != 0
    if (noSuggestions && !autoCorrect) return false
    return when (inputType and InputType.TYPE_MASK_VARIATION) {
        InputType.TYPE_TEXT_VARIATION_URI,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        -> false
        else -> true
    }
}

/**
 * Whether a field may offer the mic: never a password, which would be spoken aloud, and not when
 * the app asks for no mic through `privateImeOptions`, in the two spellings keyboards honour: the
 * old `nm` and Google's `com.google.android.inputmethod.latin.noMicrophoneKey`.
 */
fun micAllowed(inputType: Int, privateImeOptions: String?): Boolean {
    if (fieldKindOf(inputType) == FieldKind.PASSWORD) return false
    val options = privateImeOptions?.split(',')?.map { it.trim() }.orEmpty()
    return NO_MIC_OPTIONS.none { it in options }
}

private val NO_MIC_OPTIONS = listOf("nm", "com.google.android.inputmethod.latin.noMicrophoneKey")

/**
 * The capitalization a field asks for (`TYPE_TEXT_FLAG_CAP_*`), or 0: only plain text can ask,
 * so a terminal, a number, a password, an address or an e-mail field never gets a capital it did
 * not type. A chat box asks for sentences; a code editor asks for nothing.
 */
fun capsModesOf(inputType: Int): Int {
    if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return 0
    return when (inputType and InputType.TYPE_MASK_VARIATION) {
        InputType.TYPE_TEXT_VARIATION_URI,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        -> 0
        else -> inputType and CAPS_MODES
    }
}

private const val CAPS_MODES = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
    InputType.TYPE_TEXT_FLAG_CAP_WORDS or
    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

/** How the field report names what [capsModesOf] found. */
private fun capsName(modes: Int): String = when {
    modes and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 -> "characters"
    modes and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 -> "words"
    modes and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 -> "sentences"
    else -> "none"
}

/**
 * Whether the field says there is another field after it: its action is Next, or it carries the
 * navigate-next flag beside another action (a web form's username field often shows Go).
 */
fun canNavigateNext(imeOptions: Int): Boolean =
    imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_NEXT ||
        imeOptions and EditorInfo.IME_FLAG_NAVIGATE_NEXT != 0

/**
 * What a field told the keyboard, and what the keyboard made of it, for the diagnostics on the
 * settings screen. The numbers are the field's own declaration, which is what decides whether
 * suggestions, glide typing and the developer strip are offered at all; an app that sets the
 * no-suggestions flag or asks for a web or e-mail variation turns the strip off, and this is how
 * to tell from the phone which of those happened. Carries no text: not the field's content, not
 * its hint.
 */
fun fieldReport(info: EditorInfo): String {
    val kind = fieldKindOf(info.inputType)
    return listOf(
        "app: ${info.packageName} fieldId=${info.fieldId}",
        "inputType=0x${Integer.toHexString(info.inputType)} imeOptions=0x${Integer.toHexString(info.imeOptions)} privateImeOptions=${info.privateImeOptions}",
        "read as: $kind, suggestions ${if (suggestionsAllowed(info.inputType)) "allowed" else "off"}, glide ${if (kind == FieldKind.TEXT) "allowed" else "off"}, caps ${capsName(capsModesOf(info.inputType))}",
    ).joinToString("\n")
}

fun FieldKind.initialLayer(): LayerId = if (this == FieldKind.NUMBER) LayerId.SYMBOLS else LayerId.LETTERS

/** The icon the Enter key shows for the field's action. */
fun enterIconFor(editorActionId: Int?): KeyIcon = when (editorActionId) {
    EditorInfo.IME_ACTION_SEARCH -> KeyIcon.SEARCH
    EditorInfo.IME_ACTION_SEND -> KeyIcon.SEND
    EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> KeyIcon.ARROW_RIGHT
    EditorInfo.IME_ACTION_DONE -> KeyIcon.CHECK
    else -> KeyIcon.ENTER
}
