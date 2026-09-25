package net.matasar.keyboard.input

import android.text.InputType
import android.view.KeyEvent
import net.matasar.keyboard.nlp.Apostrophes

/**
 * The only place that talks to the editor. Every key ends up here as one of a handful of
 * operations, so the rules about how text reaches the app live in one file.
 */
class InputDispatcher(private val port: EditorPort) {

    /** Where our own edits should leave the cursor, to tell the app's answers from the user's moves. */
    private val cursor = ExpectedCursor()

    /** Every commit goes through here, so the expected cursor follows it. */
    private fun commit(text: CharSequence) {
        port.commitText(text)
        cursor.committed(text.length)
    }

    /** Every delete goes through here, so the expected cursor follows it. */
    private fun delete(before: Int, after: Int) {
        port.deleteSurroundingText(before, after)
        cursor.deleted(before)
    }

    /** The field's selection when it starts, from `EditorInfo.initialSelStart/End` (-1 when unknown). */
    fun resetCursor(start: Int, end: Int) = cursor.reset(start, end)

    /**
     * The app reports the cursor moved from [oldStart]..[oldEnd] to [newStart]..[newEnd]; true when
     * that was the user, false when it answers one of our own edits, even late (see [ExpectedCursor]).
     */
    fun cursorUpdate(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int): Boolean =
        cursor.update(oldStart, oldEnd, newStart, newEnd)

    fun commitText(text: CharSequence) = commit(text)

    /**
     * Deletes the selection if there is one, otherwise one code point before the cursor:
     * a surrogate pair (an emoji) goes in one press.
     */
    fun backspace() {
        val selected = port.selectedText()
        if (!selected.isNullOrEmpty()) {
            commit("")
            return
        }
        val before = port.textBeforeCursor(2)
        val length = if (before != null && before.length == 2 &&
            Character.isHighSurrogate(before[0]) && Character.isLowSurrogate(before[1])
        ) 2 else 1
        delete(length, 0)
    }

    /**
     * Whether the next letter should be a capital: the field asked for [reqModes] (sentences, words
     * or characters) and the text at the cursor starts one. No modes asked for, no question asked.
     *
     * [spaceAfter]: a phantom space is owed at the cursor. Android's answer needs the space to be
     * in the text (after a full stop, a sentence starts only past a space), so the question is
     * answered here as if it were: every mode starts a word there, and a sentence when the text
     * ends in `.` `!` `?`, possibly behind closing quotes and brackets.
     */
    fun capitalAtCursor(reqModes: Int, spaceAfter: Boolean = false): Boolean {
        if (reqModes == 0) return false
        if (!spaceAfter) return port.cursorCapsMode(reqModes) != 0
        if (reqModes and (InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0) return true
        val before = port.textBeforeCursor(SENTENCE_END_LOOKBACK)?.toString() ?: return false
        return before.trimEnd { it in CLOSING_MARKS }.lastOrNull()?.let { it in SENTENCE_ENDS } == true
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
     * The same question on the other side: a word committed at the cursor needs a space after it
     * when a letter follows, or it joins the word already there. The cursor sits at the end of
     * the text far more often than in front of a word, so this is the rarer half of the pair.
     */
    fun needsSpaceAfter(): Boolean = port.textAfterCursor(1)?.firstOrNull()?.isLetter() == true

    /**
     * Whether a word picked at the cursor should get no space after it: the field already has
     * whitespace there, or a mark that follows a word directly (a comma, a closing bracket).
     */
    fun nextCharAvoidsSpace(): Boolean {
        val next = port.textAfterCursor(1)?.firstOrNull() ?: return false
        return next.isWhitespace() || next in NO_SPACE_BEFORE
    }

    /**
     * The word immediately before the cursor, the word being typed: its letters and the
     * apostrophes inside it (don't, розв'я, and a trailing one, don'), but not an apostrophe
     * before it, which is an opening quote. Empty when the text ends in a separator, and empty
     * when the word goes on after the cursor (a letter, or an apostrophe and a letter), because a
     * cursor inside a word is not typing that word. Reads at most [MAX_WORD_LENGTH] characters.
     */
    fun wordBeforeCursor(): String {
        val before = port.textBeforeCursor(MAX_WORD_LENGTH) ?: return ""
        val word = before.takeLastWhile { Apostrophes.isWordChar(it) }.trimStart { Apostrophes.isApostrophe(it) }.toString()
        if (word.isEmpty()) return ""
        val after = port.textAfterCursor(2)?.toString().orEmpty()
        val wordGoesOn = after.firstOrNull()?.isLetter() == true ||
            after.length == 2 && Apostrophes.isApostrophe(after[0]) && after[1].isLetter()
        if (wordGoesOn) return ""
        return word
    }

    /** Whether the text before the cursor is a letter or digit and then one space: where a double space may type ". ". */
    fun endsWithSpaceAfterWord(): Boolean {
        val before = port.textBeforeCursor(2) ?: return false
        return before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()
    }

    /** Whether the text before the cursor ends with [suffix]; the undo of a correction checks it is still there. */
    fun textEndsWith(suffix: String): Boolean = port.textBeforeCursor(suffix.length)?.toString() == suffix

    /**
     * Replaces the [old] word before the cursor (as [wordBeforeCursor] returned it) with [new], in
     * one batch: the app never sees the field with the word gone and the new one not yet there.
     */
    fun replaceWordBeforeCursor(old: String, new: String) = port.batch {
        delete(old.length, 0)
        commit(new)
    }

    /** Runs [edits] as one batch edit, for a change made of several calls (see [EditorPort.batch]). */
    fun batch(edits: () -> Unit) = port.batch(edits)

    /**
     * Forward delete: clears the selection when there is one (same as [backspace]), otherwise
     * deletes one character after the cursor. In a [terminal] field the delete is a
     * [KeyEvent.KEYCODE_FORWARD_DEL] key event, because terminal emulators ignore
     * `deleteSurroundingText`.
     */
    fun forwardDelete(terminal: Boolean = false) {
        val selected = port.selectedText()
        if (!selected.isNullOrEmpty()) {
            commit("")
            return
        }
        if (terminal) {
            sendKey(KeyEvent.KEYCODE_FORWARD_DEL)
        } else {
            val after = port.textAfterCursor(2)
            val length = if (after != null && after.length == 2 &&
                Character.isHighSurrogate(after[0]) && Character.isLowSurrogate(after[1])
            ) 2 else 1
            delete(0, length)
        }
    }

    /**
     * Enter: performs the field's own action (Search, Send, Go…) when it has one, otherwise
     * sends a real Enter key so multi-line fields get a newline and terminals get a return.
     */
    fun enter(editorActionId: Int? = null) {
        if (editorActionId != null && performEditorAction(editorActionId)) return
        sendKey(KeyEvent.KEYCODE_ENTER)
    }

    /** Performs an editor action as the field's own action key would; false when the field refused. */
    fun performEditorAction(actionId: Int): Boolean {
        cursor.lost() // the field may clear itself, move on, or do nothing
        return port.performEditorAction(actionId)
    }

    /**
     * Whether the field holds no text around the cursor and none selected; a field that cannot
     * say (its connection went away) counts as holding some. Reads one character either side.
     */
    fun fieldIsEmpty(): Boolean {
        val before = port.textBeforeCursor(1) ?: return false
        val after = port.textAfterCursor(1) ?: return false
        return before.isEmpty() && after.isEmpty() && port.selectedText().isNullOrEmpty()
    }

    /** The field's whole text as it is now, or null when the field will not say (a terminal, a lost connection). */
    fun fieldText(): String? = port.fieldText()?.toString()

    /** A key event down/up pair with the given meta state. */
    fun sendKey(keyCode: Int, metaState: Int = 0) {
        cursor.lost() // what a key event does to the text is the editor's to decide
        port.sendKey(keyCode, metaState)
    }

    /** A modifier combination: the stroke's own meta (Shift for symbols) plus the modifiers'. */
    fun sendCombo(stroke: KeyStroke, metaState: Int) = sendKey(stroke.keyCode, stroke.metaState or metaState)

    /**
     * The editor's own select-all / copy / paste / cut, which every text field honours
     * whether or not it listens to key events. Returns false when the field refused.
     */
    fun sendEditingAction(action: EditingAction): Boolean {
        cursor.lost() // a paste or cut moves the cursor by what the clipboard or selection held
        return port.performContextMenuAction(action.id)
    }

    /**
     * Moves the cursor by [steps] characters (negative is left) with arrow keys, which every
     * editor and terminal honours; setSelection would need the absolute position first.
     */
    fun moveCursor(steps: Int) {
        val keyCode = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(steps)) { sendKey(keyCode) }
    }
}

/** How much text the word before the cursor may span; longer runs are cut, not suggested. */
const val MAX_WORD_LENGTH = 32

/**
 * What a word can end with, after which the next word needs a space of its own. The curly quotes
 * and the guillemets are here too: a keyboard that offers them has to space what follows them.
 */
/** What a picked word's space would only separate from the word. */
private const val NO_SPACE_BEFORE = ",.!?;:)]}"

/** What ends a sentence, as Android's caps mode reads it, and the marks that may close it after. */
private const val SENTENCE_ENDS = ".!?"
private const val CLOSING_MARKS = ")]}\"'\u201d\u2019\u00bb"
private const val SENTENCE_END_LOOKBACK = 8

private const val WORD_ENDING_PUNCTUATION = ",.!?;:)]}\"'\u201d\u2019\u00bb"

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
