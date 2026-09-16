package net.matasar.keyboard.autofill

import android.provider.Settings
import kotlin.test.Test
import kotlin.test.assertEquals

class ManagerSheetTest {

    @Test
    fun `android 14 and later open the credential provider screen, older the autofill picker`() {
        assertEquals("android.settings.CREDENTIAL_PROVIDER", settingsActionFor(34))
        assertEquals("android.settings.CREDENTIAL_PROVIDER", settingsActionFor(36))
        assertEquals(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, settingsActionFor(30))
        assertEquals(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, settingsActionFor(26))
    }
}
