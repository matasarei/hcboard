package net.matasar.keyboard.macro

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.matasar.keyboard.layout.ModifierKey

/** A named script the keyboard plays into the field: a stack of [Block]s, run top to bottom. */
@Serializable
data class Macro(val id: String, val name: String, val blocks: List<Block>)

/**
 * One block of a macro. The limits are applied where a block is read ([Block.Repeat.count],
 * [Block.Wait.duration], [Block.RandomKeys.size]), so a hand-edited or old value cannot get past them.
 */
@Serializable
sealed interface Block {

    /**
     * Commits [text] as it is. A [secret] text is masked wherever it is shown and kept sealed on
     * disk (see [sealSecrets]); in memory [text] is always the plain text. [keptSealed] is a sealed
     * text this phone could not open, written back unchanged until a new one is typed.
     */
    @Serializable
    @SerialName("text")
    data class TypeText(
        val text: String,
        val secret: Boolean = false,
        @Transient val keptSealed: String? = null,
    ) : Block

    /**
     * Presses one key, named as [MacroKeys] names it ("Esc", "F1", "Shift") or a single character,
     * with [modifiers] held. Fn is not a modifier here: the named keys already cover its meanings.
     */
    @Serializable
    @SerialName("key")
    data class PressKey(val key: String, val modifiers: Set<ModifierKey> = emptySet()) : Block

    /** Types [length] random characters from the chosen sets, each as a key event; never stored. */
    @Serializable
    @SerialName("random")
    data class RandomKeys(
        val length: Int = DEFAULT_LENGTH,
        val letters: Boolean = true,
        val digits: Boolean = true,
        val symbols: Boolean = true,
    ) : Block {
        val size: Int get() = length.coerceIn(MIN_LENGTH, MAX_LENGTH)

        companion object {
            const val DEFAULT_LENGTH = 16
            const val MIN_LENGTH = 4
            const val MAX_LENGTH = 128
        }
    }

    /** Runs [blocks] [times] times over. */
    @Serializable
    @SerialName("repeat")
    data class Repeat(val times: Int, val blocks: List<Block> = emptyList()) : Block {
        val count: Int get() = times.coerceIn(1, MAX_TIMES)

        companion object {
            const val MAX_TIMES = 100
        }
    }

    /** Pauses for [millis], for apps that need a moment after Esc or Enter. */
    @Serializable
    @SerialName("wait")
    data class Wait(val millis: Long) : Block {
        val duration: Long get() = millis.coerceIn(0L, MAX_MILLIS)

        companion object {
            const val MAX_MILLIS = 10_000L
        }
    }

    /** Commits the clipboard's text, as the toolbar's paste button does. */
    @Serializable
    @SerialName("paste")
    data object PasteClipboard : Block
}
