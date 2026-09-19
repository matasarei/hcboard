package net.matasar.keyboard.ime

import net.matasar.keyboard.BuildConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeyboardDiagnosticsTest {

    @Test
    fun `the version line names the version and the commit`() {
        val commit = "0123456789abcdef0123456789abcdef01234567"
        assertEquals("hcboard 1.2.3 ($commit)", KeyboardDiagnostics.versionLine("1.2.3", commit))
    }

    @Test
    fun `the version line defaults to this build`() {
        assertEquals(
            "hcboard ${BuildConfig.VERSION_NAME} (${BuildConfig.COMMIT})",
            KeyboardDiagnostics.versionLine(),
        )
    }

    @Test
    fun `the report opens with the version, then the field, then the insets`() {
        assertEquals(
            "hcboard 1.2.3 (local build)\n\nfield line\n\ninsets line",
            KeyboardDiagnostics.report("hcboard 1.2.3 (local build)", "field line", "insets line"),
        )
    }

    @Test
    fun `the report defaults to what the keyboard last learned`() {
        KeyboardDiagnostics.field = "inputType=0x1 package=com.example"
        KeyboardDiagnostics.insets = "navigation bar 48 px"
        val report = KeyboardDiagnostics.report()
        assertTrue(report.startsWith(KeyboardDiagnostics.versionLine() + "\n\n"))
        assertTrue(report.endsWith("inputType=0x1 package=com.example\n\nnavigation bar 48 px"))
    }
}
