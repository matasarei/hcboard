package net.matasar.keyboard.autofill

import net.matasar.keyboard.ime.FieldKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingFillTest {

    private var now = 1_000L
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    private val fill = PendingFill(clock = { now }, schedule = { delay, task -> scheduled += (now + delay) to task })

    /** Moves the clock on and runs every task that is due, as the main looper would. */
    private fun advanceTo(time: Long) {
        now = time
        scheduled.filter { it.first <= time }.forEach { it.second() }
        scheduled.removeAll { it.first <= time }
    }
    private val termux = FillTarget("com.termux", 7)
    private val login = FillTarget("com.bank", 3)

    private fun secret() = "s3cret".toCharArray()
    private fun alice() = "alice".toCharArray()
    private fun CharArray.wiped() = all { it == PendingFill.WIPED }

    private fun field(
        target: FillTarget = termux,
        kind: FieldKind = FieldKind.TERMINAL,
        canGoNext: Boolean = false,
        empty: Boolean = true,
    ) = FocusedField(target, kind, canGoNext) { empty }

    /** Records what a fill did, in order, as the controller would have done it. */
    private class Recorder : FillTyper {
        val done = mutableListOf<String>()
        override fun typeUsername(text: CharSequence) { done += "user:$text" }
        override fun typePassword(text: CharSequence) { done += "pass:$text" }
        override fun goNext() { done += "next" }
    }

    private fun deliver(field: FocusedField?): List<String> = Recorder().also { fill.deliverTo(field, it) }.done

    @Test
    fun `the password goes to the package it was asked for, once`() {
        fill.offer(termux, secret())
        assertTrue(fill.waiting)
        assertEquals(listOf("pass:s3cret"), deliver(field()))
        assertFalse(fill.waiting)
        assertEquals(emptyList(), deliver(field()))
    }

    @Test
    fun `another package gets nothing, and the password is gone for the right one too`() {
        fill.offer(termux, secret())
        assertEquals(emptyList(), deliver(field(FillTarget("com.evil.app", 7))))
        assertEquals(emptyList(), deliver(field()))
    }

    @Test
    fun `a password nobody takes is wiped when it expires, and a newer one outlives the old timer`() {
        val first = secret()
        fill.offer(termux, first)
        advanceTo(now + PendingFill.TTL_MS - 1)
        assertTrue(fill.waiting)
        advanceTo(now + 1)
        assertFalse(fill.waiting)
        assertTrue(first.wiped())

        fill.offer(termux, secret())
        advanceTo(now + 10_000)
        fill.offer(termux, secret()) // replaces it; the first timer fires before this one expires
        advanceTo(now + PendingFill.TTL_MS - 10_000)
        assertTrue(fill.waiting)
        advanceTo(now + 10_000)
        assertFalse(fill.waiting)
    }

    @Test
    fun `another field of the same app gets nothing`() {
        fill.offer(termux, secret())
        assertEquals(emptyList(), deliver(field(termux.copy(fieldId = 8))))
        assertFalse(fill.waiting)
    }

    @Test
    fun `an unknown field gets nothing`() {
        fill.offer(termux, secret())
        assertEquals(emptyList(), deliver(null))
        assertFalse(fill.waiting)
    }

    @Test
    fun `it expires`() {
        fill.offer(termux, secret())
        now += PendingFill.TTL_MS - 1
        assertEquals(listOf("pass:s3cret"), deliver(field()))
        fill.offer(termux, secret())
        now += PendingFill.TTL_MS
        assertEquals(emptyList(), deliver(field()))
    }

    @Test
    fun `the characters are wiped after use, and when they went nowhere`() {
        val used = secret()
        fill.offer(termux, used)
        deliver(field())
        assertTrue(used.wiped())

        val refused = secret()
        fill.offer(termux, refused)
        deliver(field(FillTarget("com.other", 7)))
        assertTrue(refused.wiped())
    }

    @Test
    fun `a new password replaces the old one and wipes it, and clear wipes too`() {
        val first = secret()
        fill.offer(termux, first)
        fill.offer(termux, "second".toCharArray())
        assertTrue(first.wiped())
        assertEquals(listOf("pass:second"), deliver(field()))

        val cleared = secret()
        val user = alice()
        fill.offer(termux, cleared, user)
        fill.clear()
        assertTrue(cleared.wiped())
        assertTrue(user.wiped())
        assertFalse(fill.waiting)
    }

    @Test
    fun `the characters are wiped even when using them fails`() {
        val chars = secret()
        fill.offer(termux, chars)
        val failing = object : FillTyper {
            override fun typeUsername(text: CharSequence) = Unit
            override fun typePassword(text: CharSequence) = error("the editor went away")
            override fun goNext() = Unit
        }
        runCatching { fill.deliverTo(field(), failing) }
        assertTrue(chars.wiped())
        assertFalse(fill.waiting)
    }

    // ---- username and password ----

    @Test
    fun `from a username field the username goes there, the keyboard moves on, and the password goes into the password field`() {
        val user = alice()
        val password = secret()
        fill.offer(login, password, user)
        assertEquals(listOf("user:alice", "next"), deliver(field(login, FieldKind.TEXT, canGoNext = true)))
        assertTrue(user.wiped())
        assertTrue(fill.waiting)
        assertEquals(listOf("pass:s3cret"), deliver(field(login.copy(fieldId = 4), FieldKind.PASSWORD)))
        assertTrue(password.wiped())
        assertFalse(fill.waiting)
    }

    @Test
    fun `a username field that holds text keeps it, and the password still follows`() {
        val user = alice()
        fill.offer(login, secret(), user)
        assertEquals(listOf("next"), deliver(field(login, FieldKind.TEXT, canGoNext = true, empty = false)))
        assertTrue(user.wiped())
        assertEquals(listOf("pass:s3cret"), deliver(field(login.copy(fieldId = 4), FieldKind.PASSWORD)))
    }

    @Test
    fun `a field that says nothing follows it is not left, and the password waits for the next page`() {
        fill.offer(login, secret(), alice())
        assertEquals(listOf("user:alice"), deliver(field(login, FieldKind.TEXT)))
        // The same field restarting, and a text field on the way, leave the password waiting.
        assertEquals(emptyList(), deliver(field(login, FieldKind.TEXT)))
        assertEquals(emptyList(), deliver(field(login.copy(fieldId = 5), FieldKind.TEXT)))
        assertTrue(fill.waiting)
        assertEquals(listOf("pass:s3cret"), deliver(field(login.copy(fieldId = 6), FieldKind.PASSWORD)))
    }

    @Test
    fun `from a password field, a terminal or a pin only the password is typed`() {
        for (kind in listOf(FieldKind.PASSWORD, FieldKind.TERMINAL, FieldKind.NUMBER)) {
            val user = alice()
            var asked = false
            fill.offer(login, secret(), user)
            val done = Recorder().also { fill.deliverTo(FocusedField(login, kind, canGoNext = true) { asked = true; true }, it) }.done
            assertEquals(listOf("pass:s3cret"), done, "$kind")
            assertTrue(user.wiped(), "$kind")
            assertFalse(asked, "$kind: the field is not read")
            assertFalse(fill.waiting, "$kind")
        }
    }

    @Test
    fun `without a username a text field gets the password, as a single fill`() {
        fill.offer(login, secret())
        assertEquals(listOf("pass:s3cret"), deliver(field(login, FieldKind.TEXT, canGoNext = true)))
        assertFalse(fill.waiting)
    }

    @Test
    fun `leaving the app between the stages wipes the password`() {
        val password = secret()
        fill.offer(login, password, alice())
        deliver(field(login, FieldKind.TEXT, canGoNext = true))
        assertEquals(emptyList(), deliver(field(FillTarget("com.chat", 4), FieldKind.PASSWORD)))
        assertTrue(password.wiped())
        assertEquals(emptyList(), deliver(field(login.copy(fieldId = 4), FieldKind.PASSWORD)))
    }

    @Test
    fun `the password waiting for its field still expires`() {
        fill.offer(login, secret(), alice())
        deliver(field(login, FieldKind.TEXT, canGoNext = true))
        advanceTo(now + PendingFill.TTL_MS)
        assertFalse(fill.waiting)
        assertEquals(emptyList(), deliver(field(login.copy(fieldId = 4), FieldKind.PASSWORD)))
    }

    @Test
    fun `the first stage only happens in the field the fill started from`() {
        fill.offer(login, secret(), alice())
        assertEquals(emptyList(), deliver(field(login.copy(fieldId = 9), FieldKind.TEXT, canGoNext = true)))
        assertFalse(fill.waiting)
    }

    @Test
    fun `a username that cannot be typed takes the password with it`() {
        val user = alice()
        val password = secret()
        fill.offer(login, password, user)
        val failing = object : FillTyper {
            override fun typeUsername(text: CharSequence) = error("the editor went away")
            override fun typePassword(text: CharSequence) = Unit
            override fun goNext() = Unit
        }
        runCatching { fill.deliverTo(field(login, FieldKind.TEXT, canGoNext = true), failing) }
        assertTrue(user.wiped())
        assertTrue(password.wiped())
        assertFalse(fill.waiting)
    }
}
