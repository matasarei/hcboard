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
    fun `the report opens with the version, then the field, the insets and the window events`() {
        assertEquals(
            "hcboard 1.2.3 (local build)\n\nfield line\n\ninsets line\n\nevents line",
            KeyboardDiagnostics.report("hcboard 1.2.3 (local build)", "field line", "insets line", "events line"),
        )
    }

    @Test
    fun `window events keep the newest, oldest first, with their uptime`() {
        repeat(KeyboardDiagnostics.EVENT_LIMIT + 6) { KeyboardDiagnostics.event("move $it", at = 1000L + it) }
        val lines = KeyboardDiagnostics.events.lines()
        assertEquals("window events (uptime):", lines.first())
        assertEquals(KeyboardDiagnostics.EVENT_LIMIT, lines.size - 1)
        assertEquals("1006ms move 6", lines[1])
        assertEquals("1029ms move 29", lines.last())
        assertTrue(KeyboardDiagnostics.report().endsWith("1029ms move 29"))
    }

    @Test
    fun `the report defaults to what the keyboard last learned`() {
        KeyboardDiagnostics.field = "inputType=0x1 package=com.example"
        KeyboardDiagnostics.insets = "navigation bar 48 px"
        val report = KeyboardDiagnostics.report()
        assertTrue(report.startsWith(KeyboardDiagnostics.versionLine() + "\n\n"))
        assertTrue(report.endsWith("inputType=0x1 package=com.example\n\nnavigation bar 48 px\n\n" + KeyboardDiagnostics.events))
    }
}
