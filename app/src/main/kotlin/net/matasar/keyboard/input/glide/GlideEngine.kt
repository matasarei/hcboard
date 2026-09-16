package net.matasar.keyboard.input.glide

import android.content.Context
import net.matasar.keyboard.nlp.WordList

/**
 * Owns a language's word list and classifier. Layout and classification are cheap to call
 * repeatedly: the classifier rebuilds its index only when the keys actually changed.
 */
class GlideEngine(wordList: WordList) {

    private val classifier = GlideClassifier(wordList)

    val ready: Boolean get() = classifier.ready

    /** The letter keys of the layer gestures are drawn over, in the gesture's coordinate space. */
    fun setLayout(keys: List<GlideKey>) = classifier.setLayout(keys)

    /** The best words for [path], best first; empty when the layout is not set or nothing matches. */
    fun classify(path: List<GlidePoint>, maxSuggestions: Int = MAX_SUGGESTIONS): List<String> =
        classifier.classify(path, maxSuggestions)

    companion object {
        const val MAX_SUGGESTIONS = 4

        /** Loads the bundled list for [language]; an engine over an empty list never suggests. */
        fun load(context: Context, language: String = "en_US"): GlideEngine =
            GlideEngine(WordList.load(context, language))
    }
}
