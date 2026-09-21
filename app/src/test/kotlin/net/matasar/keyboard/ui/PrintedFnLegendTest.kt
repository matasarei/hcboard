package net.matasar.keyboard.ui

import net.matasar.keyboard.layout.KeyAction
import net.matasar.keyboard.layout.Languages
import net.matasar.keyboard.layout.sixtyPercentLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrintedFnLegendTest {

    private fun key(language: net.matasar.keyboard.layout.Language, label: String) =
        sixtyPercentLayer(language, withGlobe = true).rows.flatMap { it.keys }.first { it.label == label }

    @Test
    fun `symbols fn types are printed, both of them`() {
        assertEquals("`~", printedFnLegend(key(Languages.english, "Esc")))
        assertEquals("[{", printedFnLegend(key(Languages.ukrainian, "х")))
        assertEquals("]}", printedFnLegend(key(Languages.ukrainian, "ї")))
        assertEquals(";:", printedFnLegend(key(Languages.ukrainian, "ж")))
        assertEquals("'\"", printedFnLegend(key(Languages.ukrainian, "є")))
        assertEquals(",<", printedFnLegend(key(Languages.ukrainian, "б")))
        assertEquals(".>", printedFnLegend(key(Languages.ukrainian, "ю")))
        assertEquals("]}", printedFnLegend(key(Languages.russian, "ъ")))
        assertEquals("'\"", printedFnLegend(key(Languages.russian, "э")))
    }

    @Test
    fun `named meanings are not printed`() {
        for (label in listOf("1", "0", "-", "=", "i", "j", "u", "h", "backspace")) {
            assertNull(printedFnLegend(key(Languages.english, label)), label)
        }
        assertNull(printedFnLegend(key(Languages.ukrainian, "ш")), "ш carries ↑") // the slot of i
    }

    @Test
    fun `every printed legend is a symbol fn types, on every board`() {
        for (language in Languages.all) for (globe in listOf(false, true)) {
            for (k in sixtyPercentLayer(language, globe).rows.flatMap { it.keys }) {
                val printed = printedFnLegend(k) ?: continue
                val action = k.fnAction as KeyAction.Text
                assertEquals(printed.first().toString(), action.text, "${language.tag} ${k.label}")
            }
        }
    }
}
