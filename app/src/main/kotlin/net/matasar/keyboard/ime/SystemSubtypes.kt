package net.matasar.keyboard.ime

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import net.matasar.keyboard.layout.Language
import net.matasar.keyboard.layout.Languages

/*
 * Android's side of the keyboard's languages. `res/xml/method.xml` declares a subtype per language,
 * and Android's keyboard list names the enabled ones under the keyboard's name. The keyboard's own
 * settings stay authoritative: the enabled set is pushed to Android, never read back, and Android's
 * current subtype follows the globe. A subtype with a fixed id has that id as its hash code, which
 * is what Android's calls take and what [Language.subtypeId] holds.
 */

/** The subtypes to enable for [enabledTags]: the enabled languages in globe order, English always among them. */
fun subtypeIdsFor(enabledTags: Set<String>): IntArray =
    Languages.all.filter { it.tag in enabledTags || it == Languages.english }.map { it.subtypeId }.toIntArray()

/** What to do with a pick of subtype [id] from Android's switcher: its language when enabled here, else null (keep ours). */
fun languageForPick(id: Int, enabledTags: Set<String>): Language? =
    Languages.bySubtypeId(id)?.takeIf { it.tag in enabledTags }

private fun inputMethodManager(context: Context) = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

/** This keyboard as Android lists it; null when the call fails. */
private fun ownInfo(context: Context, imm: InputMethodManager): InputMethodInfo? =
    runCatching { imm.inputMethodList }.getOrNull()?.firstOrNull { it.packageName == context.packageName }

/**
 * Makes Android's enabled subtypes the keyboard's enabled languages, so its keyboard list names
 * them. Android 14 is the first that lets a keyboard enable its own subtypes; before it the list
 * keeps English, the subtype that stands in for the implicitly enabled ones. A failing call is
 * left alone: the list is a label, and the keys never depend on it.
 */
fun pushEnabledSubtypes(context: Context, enabledTags: Set<String>) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val imm = inputMethodManager(context)
    val info = ownInfo(context, imm) ?: return
    val wanted = subtypeIdsFor(enabledTags)
    val enabled = runCatching { imm.getEnabledInputMethodSubtypeList(info, false) }.getOrNull()?.map { it.hashCode() }
    if (enabled?.toSet() == wanted.toSet()) return
    runCatching { imm.setExplicitlyEnabledInputMethodSubtypes(info.id, wanted) }
}

/**
 * Tells Android the keyboard now types [language], so its current subtype is not left on an older
 * one: a pick of that older one in Android's switcher would otherwise look like no change and never
 * reach the keys. Does nothing when Android already agrees or has not enabled that language.
 */
fun reportCurrentSubtype(service: InputMethodService, language: Language) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val imm = inputMethodManager(service)
    if (runCatching { imm.currentInputMethodSubtype }.getOrNull()?.hashCode() == language.subtypeId) return
    val info = ownInfo(service, imm) ?: return
    val subtype = runCatching { imm.getEnabledInputMethodSubtypeList(info, true) }.getOrNull()
        ?.firstOrNull { it.hashCode() == language.subtypeId } ?: return
    runCatching { service.switchInputMethod(info.id, subtype) }
}
