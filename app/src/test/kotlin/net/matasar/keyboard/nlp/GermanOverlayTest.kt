package net.matasar.keyboard.nlp

/** The curated everyday-German overlay and `de.txt`; nouns keep the list's capital. */
class GermanOverlayTest : DictionaryOverlayTest("de-everyday.tsv", "de.txt", setOf(200, 180, 165), 150, capitalisedNouns = true)
