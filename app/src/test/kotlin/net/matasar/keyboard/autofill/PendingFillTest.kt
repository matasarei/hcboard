package net.matasar.keyboard.autofill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    private val termux = "com.termux"

    private fun secret() = "s3cret".toCharArray()
    private fun CharArray.wiped() = all { it == PendingFill.WIPED }

    @Test
    fun `the password goes to the package it was asked for, once`() {
        fill.offer(termux, secret())
        assertTrue(fill.waiting)
        assertEquals("s3cret", fill.takeFor(termux) { String(it) })
        assertFalse(fill.waiting)
        assertNull(fill.takeFor(termux) { String(it) })
    }

    @Test
    fun `another package gets nothing, and the password is gone for the right one too`() {
        fill.offer(termux, secret())
        assertNull(fill.takeFor("com.evil.app") { String(it) })
        assertNull(fill.takeFor(termux) { String(it) })
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
    fun `an unknown package gets nothing`() {
        fill.offer(termux, secret())
        assertNull(fill.takeFor(null) { String(it) })
        assertFalse(fill.waiting)
    }

    @Test
    fun `it expires`() {
        fill.offer(termux, secret())
        now += PendingFill.TTL_MS - 1
        assertEquals("s3cret", fill.takeFor(termux) { String(it) })
        fill.offer(termux, secret())
        now += PendingFill.TTL_MS
        assertNull(fill.takeFor(termux) { String(it) })
    }

    @Test
    fun `the characters are wiped after use, and when they went nowhere`() {
        val used = secret()
        fill.offer(termux, used)
        fill.takeFor(termux) { }
        assertTrue(used.wiped())

        val refused = secret()
        fill.offer(termux, refused)
        fill.takeFor("com.other") { }
        assertTrue(refused.wiped())
    }

    @Test
    fun `a new password replaces the old one and wipes it, and clear wipes too`() {
        val first = secret()
        fill.offer(termux, first)
        fill.offer(termux, "second".toCharArray())
        assertTrue(first.wiped())
        assertEquals("second", fill.takeFor(termux) { String(it) })

        val cleared = secret()
        fill.offer(termux, cleared)
        fill.clear()
        assertTrue(cleared.wiped())
        assertFalse(fill.waiting)
    }

    @Test
    fun `the characters are wiped even when using them fails`() {
        val chars = secret()
        fill.offer(termux, chars)
        runCatching { fill.takeFor(termux) { error("the editor went away") } }
        assertTrue(chars.wiped())
        assertFalse(fill.waiting)
    }
}
