package net.matasar.keyboard

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import org.w3c.dom.Element

/**
 * Android's backup and device transfer take the three DataStores in files/datastore/ (settings,
 * custom words, macros) and nothing else, as the README says: the rule files must say exactly
 * that, and the manifest must point at them, or a later edit widens the backup unnoticed.
 * Like MethodXmlTest, this reads files under src/main/res, which the test task does not track:
 * run it with --rerun after editing only those.
 */
class BackupRulesXmlTest {

    private val android = "http://schemas.android.com/apk/res/android"

    private fun parse(path: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File(path))
        .documentElement

    private fun Element.children(): List<Element> =
        (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }

    /** Each rule under [section] as "tag domain:path". */
    private fun Element.rules(): List<String> = children().map { "${it.tagName} ${it.getAttribute("domain")}:${it.getAttribute("path")}" }

    @Test
    fun `the manifest turns backup on and points at both rule files`() {
        val application = parse("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        assertEquals("true", application.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(android, "dataExtractionRules"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(android, "fullBackupContent"))
    }

    @Test
    fun `cloud backup and device transfer take the DataStores and nothing else`() {
        val rules = parse("src/main/res/xml/data_extraction_rules.xml")
        assertEquals(listOf("cloud-backup", "device-transfer"), rules.children().map { it.tagName })
        for (section in rules.children()) {
            assertEquals(listOf("include file:datastore/"), section.rules(), section.tagName)
        }
    }

    @Test
    fun `full backup before Android 12 takes the same`() {
        assertEquals(listOf("include file:datastore/"), parse("src/main/res/xml/backup_rules.xml").rules())
    }
}
