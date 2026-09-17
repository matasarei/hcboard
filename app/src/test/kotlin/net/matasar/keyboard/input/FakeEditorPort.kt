package net.matasar.keyboard.input

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
) : EditorPort {
    val committed = mutableListOf<String>()
    val deletions = mutableListOf<Pair<Int, Int>>()
    val keys = mutableListOf<Pair<Int, Int>>()
    val editorActions = mutableListOf<Int>()
    val contextActions = mutableListOf<Int>()
    val selections = mutableListOf<Pair<Int, Int>>()

    var textReads = 0
        private set

    override fun commitText(text: CharSequence) { committed += text.toString(); before += text }
    override fun deleteSurroundingText(before: Int, after: Int) { deletions += before to after; this.before = this.before.dropLast(before) }
    override fun textBeforeCursor(length: Int): CharSequence { textReads++; return before.takeLast(length) }
    override fun textAfterCursor(length: Int): CharSequence { textReads++; return after.take(length) }
    override fun selectedText(): CharSequence = selected
    override fun sendKey(keyCode: Int, metaState: Int) { keys += keyCode to metaState }
    override fun performEditorAction(actionId: Int): Boolean { editorActions += actionId; return acceptsEditorAction }
    override fun performContextMenuAction(id: Int): Boolean { contextActions += id; return true }
    override fun setSelection(start: Int, end: Int): Boolean { selections += start to end; return acceptsSetSelection }
}
