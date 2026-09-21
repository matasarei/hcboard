package net.matasar.keyboard.macro

import net.matasar.keyboard.input.label
import net.matasar.keyboard.layout.ModifierKey

/** A block in a few words, as the sheet and the editor print it: "Press Ctrl+Shift+F1". */
fun Block.summary(): String = when (this) {
    is Block.TypeText -> if (secret) "Type $SECRET_MASK" else "Type “${text.replace('\n', '↵').take(SUMMARY_TEXT)}${if (text.length > SUMMARY_TEXT) "…" else ""}”"
    is Block.PressKey -> "Press ${keyCombination()}"
    is Block.RandomKeys -> "Random keys · $size"
    is Block.Repeat -> "Repeat $count×"
    is Block.Wait -> "Wait $duration ms"
    Block.PasteClipboard -> "Paste clipboard"
}

/** The key with its modifiers in the usual order: "Ctrl+Shift+F1". */
fun Block.PressKey.keyCombination(): String =
    (ModifierKey.entries.filter { it in modifiers && it != ModifierKey.FN }.map { it.label } + key).joinToString("+")

/** The macro's first blocks in a line, for the sheet: "Press Esc · Type “:wq” · …". */
fun Macro.summary(): String = when {
    blocks.isEmpty() -> "Empty"
    blocks.size <= 2 -> blocks.joinToString(" · ") { it.summary() }
    else -> blocks.take(2).joinToString(" · ") { it.summary() } + " · …"
}

private const val SUMMARY_TEXT = 16

/** A secret's stand-in, always the same length so the secret's own does not show. */
const val SECRET_MASK = "••••••"
