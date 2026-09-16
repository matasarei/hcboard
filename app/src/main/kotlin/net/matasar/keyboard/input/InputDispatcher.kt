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
}
