package net.matasar.keyboard.settings

import kotlinx.serialization.Serializable

/**
 * What a tap on the globe does with three or more languages on: back to the language used
 * before (the iPhone's way), or on to the next one in the list (Gboard's). With two they agree.
 */
@Serializable
enum class GlobeTap { LAST_USED, NEXT }
