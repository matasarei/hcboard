package net.matasar.keyboard.settings

import kotlinx.serialization.Serializable

/**
 * Where a phone board narrower than the screen sits: against the left edge, in the middle, or
 * against the right edge, for a thumb that types from one side. The 60% board always centres.
 */
@Serializable
enum class BoardSide { LEFT, CENTRE, RIGHT }
