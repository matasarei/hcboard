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
}
