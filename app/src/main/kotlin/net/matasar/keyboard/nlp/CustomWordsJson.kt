package net.matasar.keyboard.nlp

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** How custom words are kept: `{"version":1,"languages":{"en_US":{"kubectl":230,"ducking":0}}}`. */
object CustomWordsJson {

    @Serializable
    private data class Stored(val version: Int = VERSION, val languages: Map<String, Map<String, Int>> = emptyMap())

    private const val VERSION = 1

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(words: CustomWords): String = json.encodeToString(Stored.serializer(), Stored(languages = sanitized(words)))

    /** The words in [text], cleaned as [sanitized] does, or null when it cannot be read. */
    fun decode(text: String): CustomWords? = try {
        sanitized(json.decodeFromString(Stored.serializer(), text).languages)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** [words] with every entry a word the lists could hold, frequencies within 0–255, and no empty language. */
    fun sanitized(words: CustomWords): CustomWords = words
        .mapValues { (_, entries) ->
            buildMap {
                for ((word, frequency) in entries) {
                    CustomWord.normalize(word)?.let { put(it, frequency.coerceIn(0, 255)) }
                }
            }
        }
        .filterValues { it.isNotEmpty() }
}
