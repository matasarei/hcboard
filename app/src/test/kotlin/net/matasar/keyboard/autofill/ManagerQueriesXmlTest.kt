package net.matasar.keyboard.autofill

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Package visibility decides which password managers the key button's sheet can see. The two
 * service actions reach a manager only when its services are exported; Enpass's are not (seen in
 * its 6.11 manifest), so it is named by package, or the sheet drops the user's own choice and lists
 * every other manager instead. Reads src/main, which the test task does not track: run it with
 * --rerun after editing only the manifest.
 */
class ManagerQueriesXmlTest {

    private val android = "http://schemas.android.com/apk/res/android"

    private val manifest: Element = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml"))
        .documentElement

    private val queries = manifest.getElementsByTagName("queries").item(0) as Element

    private fun Element.named(tag: String): List<String> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(android, "name") }
    }

    @Test
    fun `every manager that offers either service is visible`() {
        assertEquals(
            listOf("android.service.autofill.AutofillService", "android.service.credentials.CredentialProviderService"),
            queries.named("action"),
        )
    }

    @Test
    fun `Enpass is visible by package, since its services are not exported`() {
        assertTrue("io.enpass.app" in queries.named("package"))
    }

    @Test
    fun `no permission widens visibility to every installed app`() {
        assertTrue("android.permission.QUERY_ALL_PACKAGES" !in manifest.named("uses-permission"))
    }
}
