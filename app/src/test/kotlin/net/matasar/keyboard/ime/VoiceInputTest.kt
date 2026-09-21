package net.matasar.keyboard.ime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceInputTest {

    private val own = "net.matasar.keyboard"
    private val google = "com.google.android.tts/.VoiceInputMethodService"
    private val samsung = "com.samsung.android.svoiceime/.VoiceIme"

    @Test
    fun `android's shortcut entry wins over any other voice keyboard`() {
        val candidates = listOf(
            VoiceCandidate(samsung, 0, shortcut = false),
            VoiceCandidate(google, 1, shortcut = true),
        )
        assertEquals(google, chooseVoiceTarget(candidates, own)?.imeId)
    }

    @Test
    fun `with no shortcut the first enabled voice keyboard is taken`() {
        val candidates = listOf(
            VoiceCandidate(samsung, 0, shortcut = false),
            VoiceCandidate(google, 1, shortcut = false),
        )
        assertEquals(samsung, chooseVoiceTarget(candidates, own)?.imeId)
    }

    @Test
    fun `the keyboard never hands off to itself`() {
        val candidates = listOf(VoiceCandidate("$own/.ime.KeyboardService", 0, shortcut = true))
        assertNull(chooseVoiceTarget(candidates, own))
    }

    @Test
    fun `the keyboard picked in settings wins over the shortcut`() {
        val candidates = listOf(
            VoiceCandidate(google, 0, shortcut = true),
            VoiceCandidate(samsung, 1, shortcut = false),
        )
        assertEquals(samsung, chooseVoiceTarget(candidates, own, preferredId = samsung)?.imeId)
    }

    @Test
    fun `a picked keyboard that is no longer enabled falls back to the automatic choice`() {
        val candidates = listOf(VoiceCandidate(google, 0, shortcut = true))
        assertEquals(google, chooseVoiceTarget(candidates, own, preferredId = samsung)?.imeId)
    }

    @Test
    fun `picking this keyboard itself is ignored`() {
        val self = "$own/.ime.KeyboardService"
        val candidates = listOf(VoiceCandidate(self, 0, shortcut = false), VoiceCandidate(google, 1, shortcut = false))
        assertEquals(google, chooseVoiceTarget(candidates, own, preferredId = self)?.imeId)
    }

    @Test
    fun `nothing to hand off to means no target`() {
        assertNull(chooseVoiceTarget(emptyList(), own))
    }
}
