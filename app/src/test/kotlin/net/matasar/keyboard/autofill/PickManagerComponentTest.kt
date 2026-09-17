package net.matasar.keyboard.autofill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PickManagerComponentTest {

    @Test
    fun `the preferred credential provider wins on android 14 and later`() {
        val picked = pickManagerComponent(
            primaryCredentialProvider = "io.enpass.app/.credentials.EnpassCredentialService",
            autofillService = "com.google.android.gms/.autofill.service.AutofillService",
            credentialProviders = "com.google.android.gms/.auth.api.credentials.CredentialProviderService",
        )
        assertEquals("io.enpass.app/.credentials.EnpassCredentialService", picked)
    }

    @Test
    fun `an empty primary falls back to the autofill service, then the provider list`() {
        assertEquals(
            "com.google.android.gms/.autofill.service.AutofillService",
            pickManagerComponent("", "com.google.android.gms/.autofill.service.AutofillService", null),
        )
        assertEquals(
            "io.enpass.app/.Cred",
            pickManagerComponent(null, null, "io.enpass.app/.Cred:com.samsung.android.samsungpass/.Cred"),
        )
    }

    @Test
    fun `nothing set means no manager`() {
        assertNull(pickManagerComponent(null, null, null))
        assertNull(pickManagerComponent("", "", ""))
        assertNull(pickManagerComponent("PLACEHOLDER", null, null))
    }

    @Test
    fun `a placeholder at the head of the provider list is skipped for the real one`() {
        assertEquals(
            "io.enpass.app/.Cred",
            pickManagerComponent(null, null, "PLACEHOLDER_HINT:io.enpass.app/.Cred"),
        )
    }

    @Test
    fun `every usable component is kept in preference order, once each`() {
        // Google's usual picture: the same package three times, the provider named twice.
        val google = "com.google.android.gms/.auth.api.credentials.credman.service.PasswordAndPasskeyService"
        assertEquals(
            listOf(google, "com.google.android.gms/.autofill.service.AutofillService"),
            managerComponentCandidates(google, "com.google.android.gms/.autofill.service.AutofillService", google),
        )
        // When the preferred one has nothing to open, the next is the one tried.
        assertEquals(
            listOf("io.enpass.app/.Cred", "com.google.android.gms/.autofill.service.AutofillService", "com.samsung.android.samsungpass/.Cred"),
            managerComponentCandidates(
                "io.enpass.app/.Cred",
                "com.google.android.gms/.autofill.service.AutofillService",
                " PLACEHOLDER : com.samsung.android.samsungpass/.Cred ",
            ),
        )
        assertEquals(emptyList(), managerComponentCandidates(null, "", ""))
    }

    @Test
    fun `a relative settings activity belongs to the manager's package`() {
        assertEquals("com.google.android.gms.autofill.ui.AutofillSettingsActivity",
            qualifiedClassName("com.google.android.gms", ".autofill.ui.AutofillSettingsActivity"))
        assertEquals("io.enpass.app.SettingsActivity", qualifiedClassName("com.other", "io.enpass.app.SettingsActivity"))
    }
}
