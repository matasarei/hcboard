package net.matasar.keyboard.layout

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.w3c.dom.Element

/**
 * `res/xml/method.xml` declares a subtype per language, and the keyboard enables them by
 * [Language.subtypeId]: the XML and [Languages.all] must name the same languages with the same ids,
 * or a language would be missing from Android's list or pushed under the wrong name.
 */
class MethodXmlTest {

    private val android = "http://schemas.android.com/apk/res/android"

    private fun parse(path: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File(path))

    private val subtypes: List<Element> = parse("src/main/res/xml/method.xml").getElementsByTagName("subtype")
        .let { list -> (0 until list.length).map { list.item(it) as Element } }

    private val strings: Map<String, String> = parse("src/main/res/values/strings.xml").getElementsByTagName("string")
        .let { list -> (0 until list.length).map { list.item(it) as Element } }
        .associate { it.getAttribute("name") to it.textContent }

    private fun Element.attr(name: String) = getAttributeNS(android, name)

    @Test
    fun `every language has exactly one subtype, in the same order, with its id and locale`() {
        assertEquals(Languages.all.map { it.tag }, subtypes.map { it.attr("imeSubtypeLocale") })
        for ((language, subtype) in Languages.all.zip(subtypes)) {
            assertEquals(language.subtypeId, Integer.decode(subtype.attr("subtypeId")), language.tag)
            assertEquals(language.tag.replace('_', '-'), subtype.attr("languageTag"), language.tag)
            assertEquals("keyboard", subtype.attr("imeSubtypeMode"), language.tag)
        }
    }

    @Test
    fun `each subtype is named with the language's own name`() {
        for ((language, subtype) in Languages.all.zip(subtypes)) {
            val label = subtype.attr("label").removePrefix("@string/")
            assertEquals(language.nativeName, strings[label], language.tag)
        }
    }

    @Test
    fun `only English stands in for the implicitly enabled subtypes`() {
        val overriding = subtypes.filter { it.attr("overridesImplicitlyEnabledSubtype") == "true" }
        assertEquals(listOf(Languages.english.tag), overriding.map { it.attr("imeSubtypeLocale") })
    }
}
