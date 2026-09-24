package net.matasar.keyboard.layout

import kotlinx.serialization.Serializable

/**
 * How Bulgarian is laid out: the phonetic board (я в е р т, letters where their Latin sounds are)
 * or the standard one (БДС, у е и ш щ), which is what the iPhone ships.
 */
@Serializable
enum class BulgarianLayout { PHONETIC, STANDARD }
