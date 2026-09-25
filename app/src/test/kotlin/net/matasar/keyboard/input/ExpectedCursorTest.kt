package net.matasar.keyboard.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpectedCursorTest {

    private val cursor = ExpectedCursor()

    @Test
    fun `an update that lands where the keyboard expects the cursor is ours`() {
        cursor.reset(3, 3)
        cursor.committed(2)
        assertFalse(cursor.update(3, 3, 5, 5))
        assertEquals(5, cursor.start)
    }

    @Test
    fun `a late update for an earlier edit is ours, and the expected cursor stays`() {
        cursor.reset(3, 3)
        cursor.committed(1) // 4
        cursor.committed(1) // 5
        assertFalse(cursor.update(3, 3, 4, 4)) // the first edit's update, arriving after the second
        assertEquals(5, cursor.start)
        assertFalse(cursor.update(4, 4, 5, 5))
    }

    @Test
    fun `a move away from where the keyboard expects the cursor is the user's, and becomes the new expectation`() {
        cursor.reset(5, 5)
        assertTrue(cursor.update(5, 5, 1, 1)) // back into the text
        assertEquals(1, cursor.start)
        cursor.reset(5, 5)
        assertTrue(cursor.update(5, 5, 9, 9)) // on past it
        assertEquals(9, cursor.start)
    }

    @Test
    fun `a selection made by the user is not a late update`() {
        cursor.reset(5, 5)
        assertTrue(cursor.update(5, 5, 2, 5))
        assertEquals(2, cursor.start)
        assertEquals(5, cursor.end)
    }

    @Test
    fun `when the keyboard does not know where the cursor is, an update is taken as ours and learnt`() {
        cursor.reset(5, 5)
        cursor.lost()
        assertNull(cursor.start)
        assertFalse(cursor.update(5, 5, 1, 1))
        assertEquals(1, cursor.start)
        // Android's -1 for an unknown selection is unknown too.
        cursor.reset(-1, -1)
        assertNull(cursor.start)
    }

    @Test
    fun `an update that matches nothing and moves away from the expectation is still ours when it is not clearly the user's`() {
        cursor.reset(3, 3)
        cursor.committed(2) // 5
        // Neither lands on 5 nor starts from it; heads towards it, so it is a late update.
        assertFalse(cursor.update(2, 2, 4, 4))
        assertEquals(5, cursor.start)
    }

    @Test
    fun `commits and deletes move the expected cursor as the editor would`() {
        cursor.reset(2, 5) // a selection
        cursor.committed(3) // replaces it
        assertEquals(5, cursor.start)
        assertEquals(5, cursor.end)
        cursor.deleted(2)
        assertEquals(3, cursor.start)
        cursor.reset(2, 5)
        cursor.deleted(1) // around a selection: not worked out
        assertNull(cursor.start)
    }
}
