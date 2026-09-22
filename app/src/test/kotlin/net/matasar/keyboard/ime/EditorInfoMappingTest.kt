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
    fun `a field has a next one when its action is next or it carries the navigate-next flag`() {
        assertTrue(canNavigateNext(EditorInfo.IME_ACTION_NEXT))
        assertTrue(canNavigateNext(EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NAVIGATE_NEXT))
        assertTrue(canNavigateNext(EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NAVIGATE_NEXT))
        assertFalse(canNavigateNext(EditorInfo.IME_ACTION_DONE))
        assertFalse(canNavigateNext(EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS))
        assertFalse(canNavigateNext(EditorInfo.IME_NULL))
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
    fun `the field report names the numbers and what they decided, and carries no text`() {
        val info = EditorInfo().apply {
            packageName = "com.google.android.apps.bard"
            fieldId = 42
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEND
            hintText = "Ask Gemini"
            initialSelStart = 3
        }
        val report = fieldReport(info)
        assertTrue(report.contains("com.google.android.apps.bard"), report)
        assertTrue(report.contains("fieldId=42"), report)
        assertTrue(report.contains("inputType=0x${Integer.toHexString(info.inputType)}"), report)
        assertTrue(report.contains("suggestions off"), report)
        assertTrue(report.contains("TEXT"), report)
        // Never the field's own words: a report is pasted into bug reports.
        assertFalse(report.contains("Ask Gemini"), report)
    }

    @Test
    fun `the field report says what an ordinary text field allows`() {
        val report = fieldReport(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT })
        assertTrue(report.contains("suggestions allowed"), report)
        assertTrue(report.contains("glide allowed"), report)
    }

    @Test
    fun `candidates are allowed in plain text only`() {
        assertTrue(suggestionsAllowed(InputType.TYPE_CLASS_TEXT))
        assertTrue(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT))
        assertTrue(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        // A field asking for no suggestions and for autocorrect at once contradicts itself; the
        // flag that asks for correction wins, because correcting needs candidates. 0xac001 is
        // what the Google app's prompt (Gemini) reported on a Fold: text, sentence caps,
        // autocorrect, multi-line, no suggestions.
        assertTrue(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT))
        assertTrue(suggestionsAllowed(0xac001))
        // Both flags do not rescue a field that is off for another reason.
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT))
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

    @Test
    fun `an allowed app gets candidates where it asked for none`() {
        // 0xa4001 is what YouTube's comment box reports: text, sentence caps, multi-line, no
        // suggestions — and no autocorrect, which is the one bit that separates it from 0xac001.
        val youtube = 0xa4001
        assertFalse(suggestionsAllowed(youtube))
        assertTrue(suggestionsAllowed(youtube, appAllowed = true))
        assertTrue(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, appAllowed = true))
    }

    @Test
    fun `allowing an app reaches the no-suggestions flag and nothing else`() {
        val allowed = true
        val noSuggestions = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        // The user allowed the app, not every field in it: these are quiet for their own reasons.
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or noSuggestions, allowed))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or noSuggestions, allowed))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD or noSuggestions, allowed))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or noSuggestions, allowed))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS or noSuggestions, allowed))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or noSuggestions, allowed))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_NUMBER, allowed))
        assertFalse(suggestionsAllowed(InputType.TYPE_CLASS_PHONE, allowed))
        assertFalse(suggestionsAllowed(InputType.TYPE_NULL, allowed))
    }

    @Test
    fun `the gear sheet offers the override only where it would change something`() {
        assertTrue(noSuggestionsOverridable(0xa4001))
        assertTrue(noSuggestionsOverridable(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        // Nothing to allow: these already suggest, or are off for a reason allowing cannot touch.
        assertFalse(noSuggestionsOverridable(InputType.TYPE_CLASS_TEXT))
        assertFalse(noSuggestionsOverridable(0xac001))
        assertFalse(noSuggestionsOverridable(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        assertFalse(noSuggestionsOverridable(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        assertFalse(noSuggestionsOverridable(InputType.TYPE_NULL))
    }

    @Test
    fun `the field report says whether the app was allowed`() {
        val youtube = EditorInfo().apply { inputType = 0xa4001 }
        assertTrue(fieldReport(youtube).contains("suggestions off (this app asked"), fieldReport(youtube))
        val onList = fieldReport(youtube, appAllowed = true)
        assertTrue(onList.contains("suggestions allowed (this app is on your list)"), onList)
        // A field nobody can override says neither thing.
        val plain = fieldReport(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, appAllowed = true)
        assertTrue(plain.contains("suggestions allowed,"), plain)
    }

    @Test
    fun `only plain text fields ask for capitals, and only the ones they name`() {
        val sentences = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        assertEquals(sentences, capsModesOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE or sentences))
        assertEquals(InputType.TYPE_TEXT_FLAG_CAP_WORDS, capsModesOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS))
        assertEquals(0, capsModesOf(InputType.TYPE_CLASS_TEXT))
        assertEquals(0, capsModesOf(InputType.TYPE_NULL))
        assertEquals(0, capsModesOf(InputType.TYPE_CLASS_NUMBER or sentences))
        assertEquals(0, capsModesOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD or sentences))
        assertEquals(0, capsModesOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI or sentences))
        assertEquals(0, capsModesOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or sentences))
    }

    @Test
    fun `the field report says which capitals the field asks for`() {
        val chat = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
        assertTrue(fieldReport(chat).contains("caps sentences"), fieldReport(chat))
        val plain = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }
        assertTrue(fieldReport(plain).contains("caps none"), fieldReport(plain))
    }

    @Test
    fun `the mic is offered in text fields but never in a password field`() {
        assertTrue(micAllowed(InputType.TYPE_CLASS_TEXT, null))
        assertTrue(micAllowed(InputType.TYPE_NULL, ""))
        assertFalse(micAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, null))
        assertFalse(micAllowed(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, null))
    }

    @Test
    fun `an app that asks for no mic in either spelling gets none`() {
        assertFalse(micAllowed(InputType.TYPE_CLASS_TEXT, "nm"))
        assertFalse(micAllowed(InputType.TYPE_CLASS_TEXT, "foo, com.google.android.inputmethod.latin.noMicrophoneKey"))
        assertTrue(micAllowed(InputType.TYPE_CLASS_TEXT, "nmx,other"))
    }

    @Test
    fun `the fill screen's username field gets no mic`() {
        val username = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        assertTrue(micAllowed(username, null))
        assertFalse(micAllowed(username, net.matasar.keyboard.autofill.FILL_SCREEN_IME_OPTION))
    }
}
