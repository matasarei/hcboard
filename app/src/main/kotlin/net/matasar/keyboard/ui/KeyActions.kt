package net.matasar.keyboard.ui

import net.matasar.keyboard.layout.Key
import net.matasar.keyboard.layout.KeyAction

/**
 * What a key offers TalkBack besides typing. Holding a key opens the accents, the cursor
 * trackpad or the language picker, and touch exploration never delivers a long press — the
 * finger belongs to TalkBack — so each of those becomes an action on the key's own node.
 */
sealed interface KeyAccessibilityAction {
    /** One of the accents a long press offers; [text] is both the label and what it types. */
    data class Accent(val text: String) : KeyAccessibilityAction

    /** What the trackpad does by sliding: the cursor moves [steps] characters, or words. */
    data class MoveCursor(val steps: Int, val byWord: Boolean) : KeyAccessibilityAction

    /** What a long press on the globe opens. */
    data object ChooseLanguage : KeyAccessibilityAction
}

/**
 * The actions [key] offers, given the [accents] the controller says it has now (none in a
 * password field, upper case while shift is on) and whether the globe is on the board at all.
 * Keys that hold nothing behind a long press offer nothing.
 */
internal fun keyActions(key: Key, accents: List<String>, withGlobe: Boolean): List<KeyAccessibilityAction> = when {
    accents.isNotEmpty() -> accents.map(KeyAccessibilityAction::Accent)
    key.action == KeyAction.Space -> listOf(
        KeyAccessibilityAction.MoveCursor(steps = -1, byWord = false),
        KeyAccessibilityAction.MoveCursor(steps = 1, byWord = false),
        KeyAccessibilityAction.MoveCursor(steps = -1, byWord = true),
        KeyAccessibilityAction.MoveCursor(steps = 1, byWord = true),
    )
    key.action == KeyAction.SwitchLanguage && withGlobe -> listOf(KeyAccessibilityAction.ChooseLanguage)
    else -> emptyList()
}
