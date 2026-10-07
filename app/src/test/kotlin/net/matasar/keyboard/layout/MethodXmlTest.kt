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

    /** The `<bool>` resources in `res/<dir>/bools.xml`, by name. */
    private fun bools(dir: String): Map<String, String> = parse("src/main/res/$dir/bools.xml").getElementsByTagName("bool")
        .let { list -> (0 until list.length).map { list.item(it) as Element } }
        .associate { it.getAttribute("name") to it.textContent.trim() }

    /** The locales whose subtype overrides the implicitly enabled ones, with `@bool/` resolved from [bools]. */
    private fun overriding(bools: Map<String, String>): List<String> = subtypes.filter {
        val value = it.attr("overridesImplicitlyEnabledSubtype")
        (if (value.startsWith("@bool/")) bools[value.removePrefix("@bool/")] else value) == "true"
    }.map { it.attr("imeSubtypeLocale") }

    @Test
    fun `below Android 14 only English stands in for the implicitly enabled subtypes`() {
        assertEquals(listOf(Languages.english.tag), overriding(bools("values")))
    }

    @Test
    fun `from Android 14 no subtype stands in, so the switcher names English like the rest`() {
        assertEquals(emptyList(), overriding(bools("values") + bools("values-v34")))
    }
}
