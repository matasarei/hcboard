package net.matasar.keyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import net.matasar.keyboard.layout.KeyIcon
import net.matasar.keyboard.layout.LayerId
import kotlin.test.Test
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
}
