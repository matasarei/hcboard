package net.matasar.keyboard.ime

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
 * The voice keyboard the mic hands off to: the first shortcut entry, else the first enabled
 * keyboard with a voice subtype; never this keyboard itself, which has no voice mode.
 */
fun chooseVoiceTarget(candidates: List<VoiceCandidate>, ownPackage: String): VoiceCandidate? {
    val others = candidates.filterNot { it.imeId.substringBefore('/') == ownPackage }
    return others.firstOrNull { it.shortcut } ?: others.firstOrNull()
}

/** A keyboard and the voice subtype to switch to. */
data class VoiceTarget(val imeId: String, val subtype: InputMethodSubtype)

/**
 * Finds the voice keyboard to hand dictation to, or null when no enabled keyboard offers one. Only
 * reads what Android lists; nothing here records or hears anything.
 */
fun findVoiceTarget(imm: InputMethodManager, ownPackage: String): VoiceTarget? {
    val subtypes = mutableListOf<Pair<String, InputMethodSubtype>>()
    val candidates = mutableListOf<VoiceCandidate>()
    fun add(imeId: String, subtype: InputMethodSubtype, shortcut: Boolean) {
        if (subtype.mode != VOICE_SUBTYPE_MODE) return
        candidates += VoiceCandidate(imeId, subtypes.size, shortcut)
        subtypes += imeId to subtype
    }
    runCatching { imm.shortcutInputMethodsAndSubtypes }.getOrNull()?.forEach { (info, list) ->
        list.forEach { add(info.id, it, shortcut = true) }
    }
    runCatching { imm.enabledInputMethodList }.getOrNull()?.forEach { info ->
        imm.getEnabledInputMethodSubtypeList(info, true).forEach { add(info.id, it, shortcut = false) }
    }
    val chosen = chooseVoiceTarget(candidates, ownPackage) ?: return null
    return subtypes[chosen.subtypeIndex].let { (id, subtype) -> VoiceTarget(id, subtype) }
}
