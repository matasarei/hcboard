package net.matasar.keyboard.layout

import kotlinx.serialization.Serializable

/**
 * Where the phone's globe key sits: left, between the page key and the comma (as on the iPhone),
 * or right, between the period and return. The 60% board keeps its own place for it.
 */
@Serializable
enum class GlobeSide { LEFT, RIGHT }
