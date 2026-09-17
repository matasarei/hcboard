package net.matasar.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import net.matasar.keyboard.layout.KeyIcon
import net.matasar.keyboard.layout.LayerId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class EditorInfoMappingTest {

    @Test
    fun `passwords of every flavour are password fields`() {
        assertEquals(FieldKind.PASSWORD, fieldKindOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(FieldKind.PASSWORD, fieldKindOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertEquals(FieldKind.PASSWORD, fieldKindOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertEquals(FieldKind.PASSWORD, fieldKindOf(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }

    @Test
    fun `numbers phones and dates open on the digits page`() {
        assertEquals(FieldKind.NUMBER, fieldKindOf(InputType.TYPE_CLASS_NUMBER))
        assertEquals(FieldKind.NUMBER, fieldKindOf(InputType.TYPE_CLASS_PHONE))
        assertEquals(FieldKind.NUMBER, fieldKindOf(InputType.TYPE_CLASS_DATETIME))
        assertEquals(LayerId.SYMBOLS, FieldKind.NUMBER.initialLayer())
        assertEquals(LayerId.LETTERS, FieldKind.TEXT.initialLayer())
    }

    @Test
    fun `a null input type is a terminal and plain text is text`() {
        assertEquals(FieldKind.TERMINAL, fieldKindOf(InputType.TYPE_NULL))
        assertEquals(FieldKind.TEXT, fieldKindOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
    }

    @Test
    fun `enter shows the field action`() {
        assertEquals(KeyIcon.SEARCH, enterIconFor(EditorInfo.IME_ACTION_SEARCH))
        assertEquals(KeyIcon.SEND, enterIconFor(EditorInfo.IME_ACTION_SEND))
        assertEquals(KeyIcon.ARROW_RIGHT, enterIconFor(EditorInfo.IME_ACTION_GO))
        assertEquals(KeyIcon.ARROW_RIGHT, enterIconFor(EditorInfo.IME_ACTION_NEXT))
        assertEquals(KeyIcon.CHECK, enterIconFor(EditorInfo.IME_ACTION_DONE))
        assertEquals(KeyIcon.ENTER, enterIconFor(null))
    }

    @Test
    fun `candidates are allowed in plain text only`() {
        assertTrue(suggestionsAllowed(InputType.TYPE_CLASS_TEXT))
        assertTrue(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT))
        assertTrue(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_NUMBER))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_PHONE))
        assertFalse(suggestionsAllowed(InputType.TYPE_NULL))
    }
}
