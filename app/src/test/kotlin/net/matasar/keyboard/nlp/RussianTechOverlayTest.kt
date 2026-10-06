package net.matasar.keyboard.nlp

/**
 * The IT and chat Russian overlay, generated from `ru-tech.lemmas.tsv` by
 * `scripts/expand-paradigms.py`, and `ru.txt`: lemmas at 155 or 135, their forms 20 below.
 */
class RussianTechOverlayTest : DictionaryOverlayTest("ru-tech.tsv", "ru.txt", setOf(155, 135, 115), 1500)
