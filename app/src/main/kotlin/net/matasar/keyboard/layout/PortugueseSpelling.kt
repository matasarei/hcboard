package net.matasar.keyboard.layout

import kotlinx.serialization.Serializable

/**
 * Which Portuguese the word list spells: Portugal's (the default) or Brazil's. The board is the
 * same; only the words it suggests, corrects and glides to differ (ônibus is only Brazil's).
 */
@Serializable
enum class PortugueseSpelling { PORTUGAL, BRAZIL }
