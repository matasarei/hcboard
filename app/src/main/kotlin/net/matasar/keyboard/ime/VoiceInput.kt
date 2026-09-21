package net.matasar.keyboard.ime

import android.content.Context
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype

/** The subtype mode Android gives a keyboard's dictation mode, as Google voice typing declares it. */
const val VOICE_SUBTYPE_MODE = "voice"

/**
 * An enabled keyboard's voice subtype, as plain data so the choice is tested without Android:
 * [subtypeIndex] points into the list it came from, and [shortcut] says it came from Android's own
 * shortcut list, which is what the system offers as the voice keyboard.
 */
data class VoiceCandidate(val imeId: String, val subtypeIndex: Int, val shortcut: Boolean)

/**
 * The voice keyboard the mic hands off to: the one the user picked in settings ([preferredId]) while
 * it is still enabled, else the first shortcut entry, else the first enabled keyboard with a voice
 * subtype; never this keyboard itself, which has no voice mode.
 */
fun chooseVoiceTarget(candidates: List<VoiceCandidate>, ownPackage: String, preferredId: String? = null): VoiceCandidate? {
    val others = candidates.filterNot { it.imeId.substringBefore('/') == ownPackage }
    return others.firstOrNull { it.imeId == preferredId }
        ?: others.firstOrNull { it.shortcut }
        ?: others.firstOrNull()
}

/** A keyboard and the voice subtype to switch to. */
data class VoiceTarget(val imeId: String, val subtype: InputMethodSubtype)

/** An enabled keyboard that offers voice input, as the settings list shows it. */
data class VoiceKeyboard(val imeId: String, val label: String)

/** Every enabled keyboard's voice subtype, shortcut entries first; a failing system call counts as none. */
private class VoiceSubtypes(imm: InputMethodManager) {
    val candidates = mutableListOf<VoiceCandidate>()
    val subtypes = mutableListOf<Pair<InputMethodInfo, InputMethodSubtype>>()

    init {
        runCatching { imm.shortcutInputMethodsAndSubtypes }.getOrNull()?.forEach { (info, list) ->
            list.forEach { add(info, it, shortcut = true) }
        }
        runCatching { imm.enabledInputMethodList }.getOrNull()?.forEach { info ->
            runCatching { imm.getEnabledInputMethodSubtypeList(info, true) }.getOrNull()?.forEach { add(info, it, shortcut = false) }
        }
    }

    private fun add(info: InputMethodInfo, subtype: InputMethodSubtype, shortcut: Boolean) {
        if (subtype.mode != VOICE_SUBTYPE_MODE) return
        candidates += VoiceCandidate(info.id, subtypes.size, shortcut)
        subtypes += info to subtype
    }
}

/**
 * Finds the voice keyboard to hand dictation to, or null when no enabled keyboard offers one. Only
 * reads what Android lists; nothing here records or hears anything.
 */
fun findVoiceTarget(imm: InputMethodManager, ownPackage: String, preferredId: String? = null): VoiceTarget? {
    val found = VoiceSubtypes(imm)
    val chosen = chooseVoiceTarget(found.candidates, ownPackage, preferredId) ?: return null
    return found.subtypes[chosen.subtypeIndex].let { (info, subtype) -> VoiceTarget(info.id, subtype) }
}

/** The enabled keyboards the mic could hand off to, once each, named as Android names them. */
fun listVoiceKeyboards(context: Context): List<VoiceKeyboard> {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    return VoiceSubtypes(imm).subtypes
        .map { (info, _) -> info }
        .distinctBy { it.id }
        .filterNot { it.packageName == context.packageName }
        .map { VoiceKeyboard(it.id, it.loadLabel(context.packageManager).toString()) }
}
