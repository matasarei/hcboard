package net.matasar.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.layout.LayerId
import net.matasar.keyboard.layout.Languages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSheetTest {

    private val controller = KeyboardController(InputDispatcher(FakeEditorPort()), clock = { 1000L }).apply {
        onStartInput(field())
    }

    private fun field() = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }

    @Test
    fun `the number row follows its switch, and a password field shows it regardless`() {
        val rows = { controller.phoneLayout.layers.getValue(LayerId.LETTERS).rows.size }
        assertEquals(4, rows())
        controller.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        assertEquals(5, rows())
        controller.onStartInput(field())
        assertEquals(4, rows())
        // Focus moving between fields of one screen restarts input without a new start.
        controller.updateFieldKind(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD })
        assertEquals(5, rows())
        controller.updateFieldKind(field())
        assertEquals(4, rows())

        controller.toggleSettingsSheet()
        controller.toggleNumberRow()
        assertTrue(controller.numberRow)
        assertFalse(controller.settingsSheetOpen)
        assertEquals(5, rows())
        controller.enabledLanguages = setOf(Languages.english.tag, Languages.ukrainian.tag)
        controller.switchLanguage(Languages.ukrainian)
        assertEquals("1", controller.phoneLayout.layers.getValue(LayerId.LETTERS).rows[0].keys[0].label)
        controller.toggleNumberRow()
        assertEquals(4, rows())
    }

    @Test
    fun `the gear opens the sheet and a second tap closes it`() {
        controller.toggleSettingsSheet()
        assertTrue(controller.settingsSheetOpen)
        controller.toggleSettingsSheet()
        assertFalse(controller.settingsSheetOpen)
    }

    @Test
    fun `opening the password or macro sheet closes the settings sheet`() {
        controller.toggleSettingsSheet()
        controller.toggleManagerSheet()
        assertTrue(controller.managerSheetOpen)
        assertFalse(controller.settingsSheetOpen)

        controller.toggleSettingsSheet()
        controller.toggleMacroSheet()
        assertTrue(controller.macroSheetOpen)
        assertFalse(controller.settingsSheetOpen)
    }

    @Test
    fun `opening the settings sheet closes the password and macro sheets`() {
        controller.toggleManagerSheet()
        controller.toggleSettingsSheet()
        assertTrue(controller.settingsSheetOpen)
        assertFalse(controller.managerSheetOpen)

        controller.toggleSettingsSheet()
        controller.toggleMacroSheet()
        controller.toggleSettingsSheet()
        assertTrue(controller.settingsSheetOpen)
        assertFalse(controller.macroSheetOpen)
    }

    @Test
    fun `toggling developer mode flips it and closes the sheet`() {
        controller.toggleSettingsSheet()
        controller.toggleDeveloperMode()
        assertTrue(controller.developerMode)
        assertFalse(controller.settingsSheetOpen)

        controller.toggleSettingsSheet()
        controller.toggleDeveloperMode()
        assertFalse(controller.developerMode)
        assertFalse(controller.settingsSheetOpen)
    }

    @Test
    fun `a new field or a finished one closes the sheet`() {
        controller.toggleSettingsSheet()
        controller.onFinishInput()
        assertFalse(controller.settingsSheetOpen)

        controller.toggleSettingsSheet()
        controller.onStartInput(field())
        assertFalse(controller.settingsSheetOpen)
    }
}
