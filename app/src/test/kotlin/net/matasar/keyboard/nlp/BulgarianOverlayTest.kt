package net.matasar.keyboard.nlp

/** The curated everyday-Bulgarian overlay and `bg.txt`; the list is flat at 255, so its tiers are higher. */
class BulgarianOverlayTest : DictionaryOverlayTest("bg-everyday.tsv", "bg.txt", setOf(255, 230, 210), 150)
