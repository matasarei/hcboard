package net.matasar.keyboard.autofill

import kotlin.test.Test
import kotlin.test.assertEquals

class ManagerDetectionTest {

    private val own = "net.matasar.keyboard"
    private val enpass = "io.enpass.app"
    private val google = "com.google.android.gms"
    private val samsung = "com.samsung.android.samsungpass"

    @Test
    fun `a preferred manager Android reports is the only one offered`() {
        assertEquals(listOf(enpass), managersToOffer(listOf(google, enpass, samsung), enpass, own))
    }

    @Test
    fun `with the preferred one unknown every installed manager is offered, in a stable order`() {
        // What an Android 14+ phone gives an app: the provider is in an unreadable setting, and the
        // readable one holds the placeholder, which names no package.
        val installed = listOf(samsung, enpass, google, enpass)
        // By package name here; the sheet sorts by the names people read.
        assertEquals(listOf(google, samsung, enpass), managersToOffer(installed, null, own))
        assertEquals(listOf(google, samsung, enpass), managersToOffer(installed, "credential-provider", own))
    }

    @Test
    fun `the credential manager proxy is never a manager, not even as the preferred one`() {
        val installed = listOf(CREDENTIAL_MANAGER_PROXY, enpass)
        assertEquals(listOf(enpass), managersToOffer(installed, CREDENTIAL_MANAGER_PROXY, own))
    }

    @Test
    fun `the keyboard itself is never offered, and a preferred app that is not installed is ignored`() {
        assertEquals(listOf(enpass), managersToOffer(listOf(own, enpass), own, own))
        assertEquals(listOf(google, enpass), managersToOffer(listOf(enpass, google), "com.gone.app", own))
    }

    @Test
    fun `nothing installed means nothing to offer`() {
        assertEquals(emptyList(), managersToOffer(emptyList(), null, own))
        assertEquals(emptyList(), managersToOffer(listOf(own, CREDENTIAL_MANAGER_PROXY), null, own))
    }

    @Test
    fun `a relative settings activity belongs to the manager's package`() {
        assertEquals("com.google.android.gms.autofill.ui.AutofillSettingsActivity",
            qualifiedClassName("com.google.android.gms", ".autofill.ui.AutofillSettingsActivity"))
        assertEquals("io.enpass.app.SettingsActivity", qualifiedClassName("com.other", "io.enpass.app.SettingsActivity"))
    }
}
