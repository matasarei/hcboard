package net.matasar.keyboard.nlp

/**
 * The IT and chat Ukrainian overlay, generated from `uk-tech.lemmas.tsv` by
 * `scripts/expand-paradigms.py`, and `uk.txt`: lemmas at 180 or 165, their forms 15 below.
 */
class UkrainianTechOverlayTest : DictionaryOverlayTest("uk-tech.tsv", "uk.txt", setOf(180, 165, 150), 1500)
