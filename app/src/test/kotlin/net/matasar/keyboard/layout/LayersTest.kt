package net.matasar.keyboard.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayersTest {

    @Test
    fun `every phone row adds up to ten units`() {
        for (layer in PhoneLayout.layers.values) {
            for ((index, row) in layer.rows.withIndex()) {
                assertEquals(10f, row.totalUnits, "${layer.id} row $index")
            }
        }
    }

    @Test
    fun `the number row tops the letters page in every language, full width, and nothing else changes`() {
        for (language in listOf(Languages.english, Languages.ukrainian)) {
            val plain = phoneLayout(language, withGlobe = true)
            val withDigits = phoneLayout(language, withGlobe = true, numberRow = true)
            val letters = withDigits.layers.getValue(LayerId.LETTERS)
            val digits = letters.rows[0]
            assertEquals((1..9).map { "$it" } + "0", digits.keys.map { it.label }, language.tag)
            assertTrue(digits.keys.all { it.action == KeyAction.Text(it.label) && it.style == KeyStyle.LETTER }, language.tag)
            assertEquals(letters.units, digits.totalUnits, 0.001f, language.tag)
            assertEquals(plain.layers.getValue(LayerId.LETTERS).rows, letters.rows.drop(1), language.tag)
            assertEquals(plain.layers.getValue(LayerId.SYMBOLS), withDigits.layers.getValue(LayerId.SYMBOLS))
            assertEquals(plain.layers.getValue(LayerId.CODE), withDigits.layers.getValue(LayerId.CODE))
        }
    }

    @Test
    fun `letters commit lower and upper case`() {
        val q = LettersLayer.rows[0].keys[0]
        assertEquals(KeyAction.Letter("q", "Q"), q.action)
    }

    @Test
    fun `vowels carry accents and consonants without them do not`() {
        val keys = LettersLayer.rows.flatMap { it.rows() }
        assertTrue(keys.first { it.label == "e" }.longPress.contains("é"))
        assertTrue(keys.first { it.label == "q" }.longPress.isEmpty())
    }

    @Test
    fun `layers link to each other the way the mocks do`() {
        assertEquals(KeyAction.SwitchLayer(LayerId.SYMBOLS), LettersLayer.rows[3].keys[0].action)
        assertEquals(KeyAction.SwitchLayer(LayerId.CODE), SymbolsLayer.rows[2].keys[0].action)
        assertEquals(KeyAction.SwitchLayer(LayerId.SYMBOLS), CodeLayer.rows[2].keys[0].action)
        assertEquals(KeyAction.SwitchLayer(LayerId.LETTERS), CodeLayer.rows[3].keys[0].action)
    }

    @Test
    fun `the code page starts with braces and brackets`() {
        assertEquals(listOf("{", "}", "[", "]", "|", "\\", "~", "`", "<", ">"), CodeLayer.rows[0].keys.map { it.label })
    }

    private fun Row.rows() = keys
}
