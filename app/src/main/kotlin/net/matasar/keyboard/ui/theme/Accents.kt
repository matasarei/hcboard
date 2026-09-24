package net.matasar.keyboard.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import net.matasar.keyboard.settings.AccentColour

/** The four accent roles of a Material scheme, which are all the keyboard's highlight reads. */
internal data class AccentTones(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

internal data class AccentPair(val light: AccentTones, val dark: AccentTones) {
    fun of(dark: Boolean) = if (dark) this.dark else light
}

/**
 * The fixed accents: Material tonal palettes from each seed, light tones 40/100/90/10 and dark
 * 80/20/30/90, as Material's own schemes take them. Generated once with Google's
 * material-color-utilities (Apache-2.0) and kept as numbers; rerun it from the seeds, never
 * hand-tune one role, or its contrast with the role it sits on is no longer checked by design.
 */
internal val Accents: Map<AccentColour, AccentPair> = mapOf(
    AccentColour.RED to AccentPair( // seed #E53935
        light = AccentTones(Color(0xFFBB171C), Color(0xFFFFFFFF), Color(0xFFFFDAD6), Color(0xFF410002)),
        dark = AccentTones(Color(0xFFFFB4AC), Color(0xFF690006), Color(0xFF93000D), Color(0xFFFFDAD6)),
    ),
    AccentColour.ORANGE to AccentPair( // seed #FB8C00
        light = AccentTones(Color(0xFF8F4E00), Color(0xFFFFFFFF), Color(0xFFFFDCC2), Color(0xFF2E1500)),
        dark = AccentTones(Color(0xFFFFB77B), Color(0xFF4C2700), Color(0xFF6D3A00), Color(0xFFFFDCC2)),
    ),
    AccentColour.YELLOW to AccentPair( // seed #FDD835
        light = AccentTones(Color(0xFF705D00), Color(0xFFFFFFFF), Color(0xFFFFE16E), Color(0xFF221B00)),
        dark = AccentTones(Color(0xFFE8C41D), Color(0xFF3A3000), Color(0xFF544600), Color(0xFFFFE16E)),
    ),
    AccentColour.GREEN to AccentPair( // seed #43A047
        light = AccentTones(Color(0xFF006E1C), Color(0xFFFFFFFF), Color(0xFF98F994), Color(0xFF002204)),
        dark = AccentTones(Color(0xFF7DDC7A), Color(0xFF00390A), Color(0xFF005313), Color(0xFF98F994)),
    ),
    AccentColour.TEAL to AccentPair( // seed #00897B
        light = AccentTones(Color(0xFF006B5F), Color(0xFFFFFFFF), Color(0xFF8DF5E4), Color(0xFF00201C)),
        dark = AccentTones(Color(0xFF70D8C8), Color(0xFF003731), Color(0xFF005048), Color(0xFF8DF5E4)),
    ),
    AccentColour.BLUE to AccentPair( // seed #1E88E5
        light = AccentTones(Color(0xFF0060A8), Color(0xFFFFFFFF), Color(0xFFD3E4FF), Color(0xFF001C38)),
        dark = AccentTones(Color(0xFFA2C9FF), Color(0xFF00315B), Color(0xFF004881), Color(0xFFD3E4FF)),
    ),
    AccentColour.INDIGO to AccentPair( // seed #3949AB
        light = AccentTones(Color(0xFF4555B7), Color(0xFFFFFFFF), Color(0xFFDEE0FF), Color(0xFF000E5E)),
        dark = AccentTones(Color(0xFFBBC3FF), Color(0xFF0E2288), Color(0xFF2C3C9E), Color(0xFFDEE0FF)),
    ),
    AccentColour.PURPLE to AccentPair( // seed #8E24AA
        light = AccentTones(Color(0xFF942CB0), Color(0xFFFFFFFF), Color(0xFFFDD6FF), Color(0xFF340042)),
        dark = AccentTones(Color(0xFFF3AEFF), Color(0xFF55006A), Color(0xFF790096), Color(0xFFFDD6FF)),
    ),
    AccentColour.PINK to AccentPair( // seed #D81B60
        light = AccentTones(Color(0xFFBC004F), Color(0xFFFFFFFF), Color(0xFFFFD9DE), Color(0xFF3F0016)),
        dark = AccentTones(Color(0xFFFFB2BF), Color(0xFF660028), Color(0xFF90003B), Color(0xFFFFD9DE)),
    ),
)

/**
 * This scheme with [accent]'s four roles in place of its own; [AccentColour.SYSTEM] keeps it as it is.
 * The secondary container takes the accent's container too: the keyboard never reads it, but
 * Material's selected chips on the settings screens do, and would stay the phone's colour.
 */
internal fun ColorScheme.withAccent(accent: AccentColour, dark: Boolean): ColorScheme {
    val tones = Accents[accent]?.of(dark) ?: return this
    return copy(
        primary = tones.primary,
        onPrimary = tones.onPrimary,
        primaryContainer = tones.primaryContainer,
        onPrimaryContainer = tones.onPrimaryContainer,
        secondaryContainer = tones.primaryContainer,
        onSecondaryContainer = tones.onPrimaryContainer,
    )
}
