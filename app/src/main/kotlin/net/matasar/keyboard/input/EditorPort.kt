package net.matasar.keyboard.input

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * The slice of [InputConnection] the keyboard uses, as an interface so the dispatcher can be
 * tested with a fake. [AndroidEditorPort] is the real one.
 */
interface EditorPort {
    fun commitText(text: CharSequence)
    fun deleteSurroundingText(before: Int, after: Int)
    fun textBeforeCursor(length: Int): CharSequence?
    fun textAfterCursor(length: Int): CharSequence?
    fun selectedText(): CharSequence?
    fun sendKey(keyCode: Int, metaState: Int)
    fun performEditorAction(actionId: Int): Boolean
    fun performContextMenuAction(id: Int): Boolean
    fun setSelection(start: Int, end: Int): Boolean

    /**
     * The capitalization the field wants at the cursor, among [reqModes] (the `TYPE_TEXT_FLAG_CAP_*`
     * flags): the editor works it out from its own text, so nothing is read into the keyboard.
     */
    fun cursorCapsMode(reqModes: Int): Int
}

class AndroidEditorPort(private val connection: () -> InputConnection?) : EditorPort {

    override fun commitText(text: CharSequence) {
        connection()?.commitText(text, 1)
    }

    override fun deleteSurroundingText(before: Int, after: Int) {
        connection()?.deleteSurroundingText(before, after)
    }

    override fun textBeforeCursor(length: Int): CharSequence? = connection()?.getTextBeforeCursor(length, 0)

    override fun textAfterCursor(length: Int): CharSequence? = connection()?.getTextAfterCursor(length, 0)

    override fun selectedText(): CharSequence? = connection()?.getSelectedText(0)

    override fun sendKey(keyCode: Int, metaState: Int) {
        val ic = connection() ?: return
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(keyEvent(now, KeyEvent.ACTION_DOWN, keyCode, metaState))
        ic.sendKeyEvent(keyEvent(now, KeyEvent.ACTION_UP, keyCode, metaState))
    }

    override fun performEditorAction(actionId: Int): Boolean = connection()?.performEditorAction(actionId) ?: false

    override fun performContextMenuAction(id: Int): Boolean = connection()?.performContextMenuAction(id) ?: false

    override fun setSelection(start: Int, end: Int): Boolean = connection()?.setSelection(start, end) ?: false

    override fun cursorCapsMode(reqModes: Int): Int = connection()?.getCursorCapsMode(reqModes) ?: 0

    private fun keyEvent(time: Long, action: Int, keyCode: Int, metaState: Int) = KeyEvent(
        time, time, action, keyCode, 0, metaState,
        KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
        KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
    )
}
