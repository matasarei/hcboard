package net.matasar.keyboard.autofill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingFillTest {

    private var now = 1_000L
    private val fill = PendingFill { now }
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
