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
    fun `layers link to each other the way the iPhone's do`() {
        assertEquals(KeyAction.SwitchLayer(LayerId.SYMBOLS), LettersLayer.rows[3].keys[0].action)
        assertEquals("123", LettersLayer.rows[3].keys[0].label)
        assertEquals(KeyAction.SwitchLayer(LayerId.CODE), SymbolsLayer.rows[2].keys[0].action)
        assertEquals("#+=", SymbolsLayer.rows[2].keys[0].label)
        assertEquals(KeyAction.SwitchLayer(LayerId.SYMBOLS), CodeLayer.rows[2].keys[0].action)
        assertEquals("123", CodeLayer.rows[2].keys[0].label)
        assertEquals(KeyAction.SwitchLayer(LayerId.LETTERS), CodeLayer.rows[3].keys[0].action)
        assertEquals("ABC", SymbolsLayer.rows[3].keys[0].label)
    }

    @Test
    fun `the symbol pages are the iPhone's 123 and #+= pages`() {
        fun labels(layer: Layer, index: Int) = layer.rows[index].keys.joinToString(" ") { it.label }
        assertEquals("1 2 3 4 5 6 7 8 9 0", labels(SymbolsLayer, 0))
        assertEquals("- / : ; ( ) $ & @ \"", labels(SymbolsLayer, 1))
        assertEquals("#+= . , ? ! ' backspace", labels(SymbolsLayer, 2))
        assertEquals("[ ] { } # % ^ * + =", labels(CodeLayer, 0))
        assertEquals("_ \\ | ~ < > € £ ¥ •", labels(CodeLayer, 1))
        assertEquals("123 . , ? ! ' backspace", labels(CodeLayer, 2))
        // What the two pages leave out is a long press away.
        val apostrophe = SymbolsLayer.rows[2].keys.first { it.label == "'" }
        assertEquals("`", apostrophe.longPress.first())
        assertEquals(listOf("–", "—"), SymbolsLayer.rows[1].keys.first { it.label == "-" }.longPress)
    }

    @Test
    fun `the letters bottom row is switch, comma, globe, space, period and a wide return`() {
        val plain = LettersLayer.rows[3].keys
        assertEquals(
            listOf(KeyAction.SwitchLayer(LayerId.SYMBOLS), KeyAction.Text(","), KeyAction.Space, KeyAction.Text("."), KeyAction.Enter),
            plain.map { it.action },
        )
        assertEquals(listOf(1.25f, 1f, 4.75f, 1f, 2f), plain.map { it.width })
        val globe = Languages.english.lettersLayer(withGlobe = true).rows[3].keys
        assertEquals(listOf("123", ",", "globe", "English", ".", "enter"), globe.map { it.label })
        assertEquals(listOf(1.25f, 1f, 1f, 3.75f, 1f, 2f), globe.map { it.width })
        // The symbol pages have both marks on their third row already.
        assertEquals(listOf("ABC", "English", "enter"), SymbolsLayer.rows[3].keys.map { it.label })
    }

    @Test
    fun `an address field swaps the comma for what an address needs, on the letters page only`() {
        fun labels(marks: FieldMarks) =
            phoneLayout(Languages.english, withGlobe = true, marks = marks).layers.getValue(LayerId.LETTERS).rows[3].keys.map { it.label }
        assertEquals(listOf("123", ",", "globe", "English", ".", "enter"), labels(FieldMarks.NONE))
        assertEquals(listOf("123", "@", "globe", "English", ".", "enter"), labels(FieldMarks.EMAIL))
        assertEquals(listOf("123", "/", "globe", "English", ".", "enter"), labels(FieldMarks.URL))
        for (marks in FieldMarks.entries) {
            val layout = phoneLayout(Languages.ukrainian, withGlobe = false, marks = marks)
            for (layer in layout.layers.values) for (row in layer.rows) assertEquals(layer.units, row.totalUnits, 0.001f, "$marks ${layer.id}")
            assertEquals(symbolsLayer(Languages.ukrainian.nativeName, withGlobe = false), layout.layers.getValue(LayerId.SYMBOLS))
        }
    }

    private fun Row.rows() = keys
}
