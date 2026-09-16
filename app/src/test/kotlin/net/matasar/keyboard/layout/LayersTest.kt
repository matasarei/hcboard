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
