package net.matasar.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import net.matasar.keyboard.input.FakeEditorPort
import net.matasar.keyboard.input.InputDispatcher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSheetTest {

    private val controller = KeyboardController(InputDispatcher(FakeEditorPort()), clock = { 1000L }).apply {
        onStartInput(field())
    }

    private fun field() = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }

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
