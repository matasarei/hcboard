package net.matasar.keyboard.macro

import kotlinx.coroutines.yield
import net.matasar.keyboard.input.EditingAction
import net.matasar.keyboard.input.InputDispatcher
import net.matasar.keyboard.input.keyStrokeFor
import net.matasar.keyboard.input.metaStateOf
import net.matasar.keyboard.layout.ModifierKey
import kotlin.random.Random

/** What the runner needs to know about the focused field: the same two facts a typed combination uses. */
data class MacroField(val terminal: Boolean, val editingShortcuts: Boolean)

/** A macro whose repeats add up to more than [MacroRunner.MAX_STEPS] steps; none of it is played. */
class MacroTooLong(val steps: Int) : IllegalArgumentException("macro expands to more than ${MacroRunner.MAX_STEPS} steps")

/**
 * Plays a [Macro] into the editor through the dispatcher. Repeats are expanded and counted before
 * the first step goes out, so a macro is played whole or not at all; cancellation lands between
 * steps. Random keys are made per run from [random] and never leave this class.
 */
class MacroRunner(
    private val dispatcher: InputDispatcher,
    private val clipboardText: () -> String?,
    private val random: () -> Random,
    private val delay: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {

    suspend fun run(macro: Macro, field: MacroField) {
        val steps = expand(macro.blocks)
        val random = random()
        for (step in steps) {
            yield()
            play(step, field, random)
        }
    }

    private suspend fun play(step: Block, field: MacroField, random: Random) {
        when (step) {
            is Block.TypeText -> if (step.text.isNotEmpty()) dispatcher.commitText(step.text)
            is Block.PasteClipboard -> clipboardText()?.takeIf { it.isNotEmpty() }?.let { dispatcher.commitText(it) }
            is Block.Wait -> delay(step.duration)
            is Block.PressKey -> pressKey(step, field)
            is Block.RandomKeys -> for (char in randomKeys(step, random)) {
                keyStrokeFor(char)?.let { dispatcher.sendCombo(it, 0) }
            }
            is Block.Repeat -> error("repeats are expanded before playing")
        }
    }

    /** A named key or character with its modifiers; Ctrl+A/C/V/X in a text field is the editor's own action, as when typed. */
    private fun pressKey(step: Block.PressKey, field: MacroField) {
        val stroke = MacroKeys.strokeFor(step.key) ?: return
        val modifiers = step.modifiers - ModifierKey.FN
        if (modifiers == setOf(ModifierKey.CTRL) && field.editingShortcuts && !field.terminal) {
            EditingAction.forLetter(step.key)?.let { action ->
                if (dispatcher.sendEditingAction(action)) return
            }
        }
        dispatcher.sendCombo(stroke, metaStateOf(modifiers))
    }

    companion object {
        const val MAX_STEPS = 2_000

        /** [blocks] with every repeat unrolled; throws [MacroTooLong] past [MAX_STEPS]. */
        fun expand(blocks: List<Block>): List<Block> {
            val count = stepCount(blocks)
            if (count > MAX_STEPS) throw MacroTooLong(count)
            return buildList { unroll(blocks, this) }
        }

        /** How many steps [blocks] play, counted without unrolling; stops counting just past [MAX_STEPS]. */
        fun stepCount(blocks: List<Block>): Int {
            var total = 0L
            for (block in blocks) {
                total += if (block is Block.Repeat) block.count.toLong() * stepCount(block.blocks) else 1L
                if (total > MAX_STEPS) return MAX_STEPS + 1
            }
            return total.toInt()
        }

        private fun unroll(blocks: List<Block>, into: MutableList<Block>) {
            for (block in blocks) {
                if (block is Block.Repeat) repeat(block.count) { unroll(block.blocks, into) } else into += block
            }
        }
    }
}
