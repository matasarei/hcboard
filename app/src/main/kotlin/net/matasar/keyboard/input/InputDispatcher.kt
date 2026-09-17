package net.matasar.keyboard.input

import android.view.KeyEvent

/**
 * The only place that talks to the editor. Every key ends up here as one of a handful of
 * operations, so the rules about how text reaches the app live in one file.
 */
class InputDispatcher(private val port: EditorPort) {

    fun commitText(text: CharSequence) = port.commitText(text)

    /**
     * Deletes the selection if there is one, otherwise one code point before the cursor:
     * a surrogate pair (an emoji) goes in one press.
     */
    fun backspace() {
        val selected = port.selectedText()
        if (!selected.isNullOrEmpty()) {
            port.commitText("")
            return
        }
        val before = port.textBeforeCursor(2)
        val length = if (before != null && before.length == 2 &&
            Character.isHighSurrogate(before[0]) && Character.isLowSurrogate(before[1])
        ) 2 else 1
        port.deleteSurroundingText(length, 0)
    }

    /**
     * Whether a word committed at the cursor needs a space put in front of it: true when the
     * character before it ends a word — a letter or a digit, or the punctuation that closes one —
     * and false at the start of the field, after a space or a newline, and after a character a
     * space should not follow (an opening bracket, a hyphen, a slash).
     */
    fun needsSpaceBefore(): Boolean {
        val previous = port.textBeforeCursor(1)?.lastOrNull() ?: return false
        return previous.isLetterOrDigit() || previous in WORD_ENDING_PUNCTUATION
    }

    /**
     * The letters immediately before the cursor, the word being typed; empty when the text ends
     * in a separator, and empty when a letter follows the cursor, because a cursor inside a word
     * is not typing that word. Reads at most [MAX_WORD_LENGTH] characters.
     */
    fun wordBeforeCursor(): String {
        val before = port.textBeforeCursor(MAX_WORD_LENGTH) ?: return ""
        val word = before.takeLastWhile { it.isLetter() }.toString()
        if (word.isEmpty()) return ""
        if (port.textAfterCursor(1)?.firstOrNull()?.isLetter() == true) return ""
        return word
    }

    /** Whether the text before the cursor ends with [suffix]; the undo of a correction checks it is still there. */
    fun textEndsWith(suffix: String): Boolean = port.textBeforeCursor(suffix.length)?.toString() == suffix

    /** Replaces the [old] word before the cursor (as [wordBeforeCursor] returned it) with [new]. */
    fun replaceWordBeforeCursor(old: String, new: String) {
        port.deleteSurroundingText(old.length, 0)
        port.commitText(new)
    }

    /** Forward delete of one character after the cursor. */
    fun forwardDelete() = port.deleteSurroundingText(0, 1)

    /**
     * Enter: performs the field's own action (Search, Send, Go…) when it has one, otherwise
     * sends a real Enter key so multi-line fields get a newline and terminals get a return.
     */
    fun enter(editorActionId: Int? = null) {
        if (editorActionId != null && port.performEditorAction(editorActionId)) return
        sendKey(KeyEvent.KEYCODE_ENTER)
    }

    /** A key event down/up pair with the given meta state. */
    fun sendKey(keyCode: Int, metaState: Int = 0) = port.sendKey(keyCode, metaState)

    /** A modifier combination: the stroke's own meta (Shift for symbols) plus the modifiers'. */
    fun sendCombo(stroke: KeyStroke, metaState: Int) = port.sendKey(stroke.keyCode, stroke.metaState or metaState)

    /**
     * The editor's own select-all / copy / paste / cut, which every text field honours
     * whether or not it listens to key events. Returns false when the field refused.
     */
    fun sendEditingAction(action: EditingAction): Boolean = port.performContextMenuAction(action.id)

    /**
     * Moves the cursor by [steps] characters (negative is left) with arrow keys, which every
     * editor and terminal honours; setSelection would need the absolute position first.
     */
    fun moveCursor(steps: Int) {
        val keyCode = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { port.sendKey(keyCode, 0) }
    }
}

/** How much text the word before the cursor may span; longer runs are cut, not suggested. */
const val MAX_WORD_LENGTH = 32

/** What a word can end with, after which the next word needs a space of its own. */
private const val WORD_ENDING_PUNCTUATION = ",.!?;:)]}\"'"

/** The four editing shortcuts that have a context-menu equivalent. */
enum class EditingAction(val id: Int) {
    SELECT_ALL(android.R.id.selectAll),
    COPY(android.R.id.copy),
    PASTE(android.R.id.paste),
    CUT(android.R.id.cut);

    companion object {
        /** The editing action for Ctrl + [letter], or null. */
        fun forLetter(letter: String): EditingAction? = when (letter.lowercase()) {
            "a" -> SELECT_ALL
            "c" -> COPY
            "v" -> PASTE
            "x" -> CUT
            else -> null
        }
    }
}
