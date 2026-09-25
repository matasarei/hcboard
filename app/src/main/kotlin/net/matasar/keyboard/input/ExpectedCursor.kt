package net.matasar.keyboard.input

/**
 * Where the keyboard expects the cursor after its own edits, so a cursor update the app reports
 * can be told apart: the answer to one of our edits, perhaps arriving late, or the user moving the
 * cursor. The rule is AOSP LatinIME's (`RichInputConnection.isBelatedExpectedUpdate`):
 *
 * 1. the update lands where the cursor is expected: ours;
 * 2. it starts where the cursor is expected and goes elsewhere: the user's;
 * 3. otherwise ours when the cursor collapsed and moved towards the expected place, which is how a
 *    late update for an earlier edit looks.
 *
 * When the place is not known ([lost] after an edit whose effect cannot be worked out here, or
 * Android's -1), an update is taken as ours and learnt: dropping state on a guess is worse than
 * keeping it.
 */
class ExpectedCursor {

    var start: Int? = null
        private set
    var end: Int? = null
        private set

    /** The selection is [start]..[end]; a negative value, as Android sends when it does not know, is unknown. */
    fun reset(start: Int, end: Int) {
        if (start < 0 || end < 0) return lost()
        this.start = start
        this.end = end
    }

    /** [length] characters were committed at the cursor, replacing any selection. */
    fun committed(length: Int) {
        val at = start ?: return
        start = at + length
        end = at + length
    }

    /** [before] characters before a collapsed cursor were deleted; around a selection it is not worked out. */
    fun deleted(before: Int) {
        val at = start ?: return
        if (at != end) return lost()
        start = (at - before).coerceAtLeast(0)
        end = start
    }

    /** An edit whose effect on the cursor cannot be worked out here: a key event, an editor action. */
    fun lost() {
        start = null
        end = null
    }

    /** Whether the update from [oldStart]..[oldEnd] to [newStart]..[newEnd] answers one of our edits. */
    fun isOurs(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int): Boolean {
        val expectedStart = start ?: return true
        val expectedEnd = end ?: return true
        if (newStart == expectedStart && newEnd == expectedEnd) return true
        if (oldStart == expectedStart && oldEnd == expectedEnd && (oldStart != newStart || oldEnd != newEnd)) return false
        return newStart == newEnd &&
            (newStart - oldStart).toLong() * (expectedStart - newStart) >= 0 &&
            (newEnd - oldEnd).toLong() * (expectedEnd - newEnd) >= 0
    }

    /**
     * Takes a cursor update; true when it was the user's. The user's move becomes what is expected
     * from now on, and so does an answer while the place was unknown.
     */
    fun update(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int): Boolean {
        val unknown = start == null
        val ours = isOurs(oldStart, oldEnd, newStart, newEnd)
        if (!ours || unknown) reset(newStart, newEnd)
        return !ours
    }
}
