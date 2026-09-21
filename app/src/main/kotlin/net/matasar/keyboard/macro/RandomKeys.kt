package net.matasar.keyboard.macro

import kotlin.random.Random

/** The character sets a [Block.RandomKeys] draws from; every character has a US key stroke. */
object RandomKeySets {
    const val LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val DIGITS = "0123456789"

    /** No space, quotes, backslash, backtick, `|` or `~`: shells and forms mangle those. */
    const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"
}

/**
 * [block]'s characters: its length (clamped) drawn from the chosen sets, each chosen set at least
 * once, shuffled with the same [random]. No set chosen counts as all three. The result is typed
 * and dropped; nothing keeps it.
 */
fun randomKeys(block: Block.RandomKeys, random: Random): String {
    val sets = buildList {
        if (block.letters) add(RandomKeySets.LETTERS)
        if (block.digits) add(RandomKeySets.DIGITS)
        if (block.symbols) add(RandomKeySets.SYMBOLS)
    }.ifEmpty { listOf(RandomKeySets.LETTERS, RandomKeySets.DIGITS, RandomKeySets.SYMBOLS) }
    val all = sets.joinToString("")
    val chars = CharArray(block.size)
    // The first characters guarantee each set; the shuffle below moves them anywhere.
    for (i in chars.indices) {
        val from = sets.getOrNull(i) ?: all
        chars[i] = from[random.nextInt(from.length)]
    }
    for (i in chars.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val swap = chars[i]
        chars[i] = chars[j]
        chars[j] = swap
    }
    return String(chars)
}
