package net.matasar.keyboard.settings

import kotlinx.serialization.Serializable

/**
 * The keyboard's highlight: the Enter key, an armed or locked modifier, the chips. [SYSTEM] is the
 * phone's own palette (Android 12+, from the wallpaper; the fixed blue below that), the rest are
 * fixed colours picked from swatches under the theme.
 */
@Serializable
enum class AccentColour { SYSTEM, RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, INDIGO, PURPLE, PINK }
