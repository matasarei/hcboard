package net.matasar.keyboard.macro

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** How the macro list is kept: `{"version":1,"macros":[…]}`, each block tagged by its `type`. */
object MacroJson {

    @Serializable
    private data class Stored(val version: Int = VERSION, val macros: List<Macro>)

    private const val VERSION = 1

    // Defaults are written out: a stored macro keeps its meaning when a default changes later.
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    fun encode(macros: List<Macro>): String = json.encodeToString(Stored.serializer(), Stored(macros = macros))

    /** The macros in [text], or null when it cannot be read. */
    fun decode(text: String): List<Macro>? = try {
        json.decodeFromString(Stored.serializer(), text).macros
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
