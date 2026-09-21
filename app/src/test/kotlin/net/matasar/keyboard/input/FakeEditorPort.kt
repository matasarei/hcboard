package net.matasar.keyboard.input

import android.text.InputType

/**
 * Records every call so tests can assert what reached the editor, and keeps [before] as the
 * text before the cursor: commits append to it and deletions trim it.
 */
class FakeEditorPort(
    var before: String = "",
    var after: String = "",
    var selected: String = "",
    var acceptsEditorAction: Boolean = true,
    var acceptsSetSelection: Boolean = true,
    /** False for a field that will not give its text, as a terminal. */
    var tellsFieldText: Boolean = true,
) : EditorPort {
    val committed = mutableListOf<String>()
    val deletions = mutableListOf<Pair<Int, Int>>()
    val keys = mutableListOf<Pair<Int, Int>>()
    val editorActions = mutableListOf<Int>()
    val contextActions = mutableListOf<Int>()
    val selections = mutableListOf<Pair<Int, Int>>()

    var textReads = 0
        private set

    /** How many times the capitalization at the cursor was asked for. */
    var capsQueries = 0
        private set

    override fun commitText(text: CharSequence) { committed += text.toString(); before += text }
    override fun deleteSurroundingText(before: Int, after: Int) { deletions += before to after; this.before = this.before.dropLast(before) }
    override fun textBeforeCursor(length: Int): CharSequence { textReads++; return before.takeLast(length) }
    override fun textAfterCursor(length: Int): CharSequence { textReads++; return after.take(length) }
    override fun selectedText(): CharSequence = selected
    override fun fieldText(): CharSequence? { textReads++; return if (tellsFieldText) before + selected + after else null }
    override fun sendKey(keyCode: Int, metaState: Int) { keys += keyCode to metaState }
    override fun performEditorAction(actionId: Int): Boolean { editorActions += actionId; return acceptsEditorAction }
    override fun performContextMenuAction(id: Int): Boolean { contextActions += id; return true }
    override fun setSelection(start: Int, end: Int): Boolean { selections += start to end; return acceptsSetSelection }

    /**
     * A rough stand-in for the framework's `TextUtils.getCapsMode`: characters always; words at the
     * start or after a space; sentences at the start, after a newline, or after `.`, `!` or `?`
     * and a space.
     */
    override fun cursorCapsMode(reqModes: Int): Int {
        capsQueries++
        var mode = reqModes and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        val wordStart = before.isEmpty() || before.last().isWhitespace()
        if (wordStart) mode = mode or (reqModes and InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val trimmed = before.trimEnd(' ')
        val sentenceStart = trimmed.isEmpty() || before.endsWith("\n") ||
            (trimmed.length < before.length && trimmed.last() in ".!?")
        if (sentenceStart) mode = mode or (reqModes and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        return mode
    }
}
