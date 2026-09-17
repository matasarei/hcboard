/*
 * Copyright (C) 2025 The FlorisBoard Contributors
 * Copyright (C) 2026 hcboard contributors (modifications, see below)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Derived from StatisticalGlideTypingClassifier.kt in FlorisBoard
 * (https://github.com/florisboard/florisboard). Modifications for hcboard:
 * FlorisBoard's TextKey/KeyData, Subtype and NLP manager are replaced by
 * GlideKey, a plain word list with frequencies and a single language; the
 * gesture is classified in one call from a list of points instead of being
 * accumulated; the per-subtype pruner and suggestion caches are dropped and
 * androidx.collection is not needed. The algorithm, thresholds and the
 * Gesture and Pruner classes are unchanged.
 */

package net.matasar.keyboard.input.glide

import net.matasar.keyboard.nlp.WordList
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Classifies a glide gesture by comparing it with the "ideal gesture" of every plausible word.
 *
 * Check out Étienne Desticourt's excellent write up at https://github.com/AnySoftKeyboard/AnySoftKeyboard/pull/1870
 */
class GlideClassifier(private val wordList: WordList) {

    private var keysByCharacter: Map<Char, GlideKey> = emptyMap()
    private var keys: List<GlideKey> = emptyList()
    private var pruner: Pruner? = null

    /** The minimum distance between points to be added to a gesture. */
    private var distanceThresholdSquared = 0f

    val ready: Boolean get() = pruner != null

    companion object {
        /**
         * Describes the allowed length variance in a gesture. If a gesture is too long or too short, it is immediately
         * discarded to save cycles.
         */
        private const val PRUNING_LENGTH_THRESHOLD = 8.42

        /** Describes the number of points to sample a gesture at, i.e the resolution. */
        private const val SAMPLING_POINTS: Int = 200

        /**
         * Standard deviation of the distribution of distances between the shapes of two gestures
         * representing the same word. It's expressed for normalized gestures and is therefore
         * independent of the keyboard or key size.
         */
        private const val SHAPE_STD = 22.08f

        /**
         * Standard deviation of the distribution of distances between the locations of two gestures
         * representing the same word. It's expressed as a factor of key radius as it's applied to
         * un-normalized gestures and is therefore dependent on the size of the keys/keyboard.
         */
        private const val LOCATION_STD = 0.5109f
    }

    /** Sets the letter keys the gestures are drawn over; rebuilds the pruner when they changed. */
    fun setLayout(letterKeys: List<GlideKey>) {
        if (letterKeys == keys && pruner != null) return
        keys = letterKeys
        keysByCharacter = letterKeys.associateBy { it.char }
        val first = letterKeys.firstOrNull()
        if (first == null) {
            pruner = null
            return
        }
        distanceThresholdSquared = (first.width / 4).let { it * it }
        pruner = Pruner(PRUNING_LENGTH_THRESHOLD, wordList.words, keysByCharacter)
    }

    /** The best [maxSuggestionCount] words for the pointer path, best first; empty when not ready. */
    fun classify(path: List<GlidePoint>, maxSuggestionCount: Int): List<String> {
        val pruner = pruner ?: return emptyList()
        val gesture = Gesture()
        for (point in path) {
            if (gesture.isEmpty) {
                gesture.addPoint(point.x, point.y)
            } else {
                val dx = gesture.getLastX() - point.x
                val dy = gesture.getLastY() - point.y
                if (dx * dx + dy * dy > distanceThresholdSquared) gesture.addPoint(point.x, point.y)
            }
        }
        if (gesture.isEmpty) return emptyList()
        return suggestions(gesture, pruner, maxSuggestionCount)
    }

    private fun suggestions(gesture: Gesture, pruner: Pruner, maxSuggestionCount: Int): List<String> {
        val candidates = arrayListOf<String>()
        val candidateWeights = arrayListOf<Float>()
        val key = keys.firstOrNull() ?: return listOf()
        val radius = min(key.height, key.width)
        var remainingWords = pruner.pruneByExtremities(gesture, this.keys)
        val userGesture = gesture.resample(SAMPLING_POINTS)
        val normalizedUserGesture: Gesture = userGesture.normalizeByBoxSide()
        remainingWords = pruner.pruneByLength(gesture, remainingWords, keysByCharacter, keys)

        for (i in remainingWords.indices) {
            val word = remainingWords[i]
            val idealGestures = Gesture.generateIdealGestures(word, keysByCharacter)

            for (idealGesture in idealGestures) {
                val wordGesture = idealGesture.resample(SAMPLING_POINTS)
                val normalizedGesture: Gesture = wordGesture.normalizeByBoxSide()
                val shapeDistance = calcShapeDistance(normalizedGesture, normalizedUserGesture)
                val locationDistance = calcLocationDistance(wordGesture, userGesture)
                val shapeProbability = calcGaussianProbability(shapeDistance, 0.0f, SHAPE_STD)
                val locationProbability = calcGaussianProbability(locationDistance, 0.0f, LOCATION_STD * radius)
                val frequency = max(1f, wordList.frequency(word).toFloat())
                val confidence = 1.0f / (shapeProbability * locationProbability * frequency)

                var candidateDistanceSortedIndex = 0
                var duplicateIndex = Int.MAX_VALUE

                while (candidateDistanceSortedIndex < candidateWeights.size
                    && candidateWeights[candidateDistanceSortedIndex] <= confidence
                ) {
                    if (candidates[candidateDistanceSortedIndex].contentEquals(word)) duplicateIndex =
                        candidateDistanceSortedIndex
                    candidateDistanceSortedIndex++
                }
                if (candidateDistanceSortedIndex < maxSuggestionCount && candidateDistanceSortedIndex <= duplicateIndex) {
                    if (duplicateIndex < Int.MAX_VALUE) {
                        candidateWeights.removeAt(duplicateIndex)
                        candidates.removeAt(duplicateIndex)
                    }
                    candidateWeights.add(candidateDistanceSortedIndex, confidence)
                    candidates.add(candidateDistanceSortedIndex, word)
                    if (candidateWeights.size > maxSuggestionCount) {
                        candidateWeights.removeAt(maxSuggestionCount)
                        candidates.removeAt(maxSuggestionCount)
                    }
                }
            }
        }

        return candidates
    }

    private fun calcLocationDistance(gesture1: Gesture, gesture2: Gesture): Float {
        var totalDistance = 0.0f
        for (i in 0 until SAMPLING_POINTS) {
            val x1 = gesture1.getX(i)
            val x2 = gesture2.getX(i)
            val y1 = gesture1.getY(i)
            val y2 = gesture2.getY(i)
            val distance = abs(x1 - x2) + abs(y1 - y2)
            totalDistance += distance
        }
        return totalDistance / SAMPLING_POINTS / 2
    }

    private fun calcGaussianProbability(value: Float, mean: Float, standardDeviation: Float): Float {
        val factor = 1.0 / (standardDeviation * sqrt(2 * PI))
        val exponent = ((value - mean) / standardDeviation).toDouble().pow(2.0)
        val probability = factor * exp(-1.0 / 2 * exponent)
        return probability.toFloat()
    }

    private fun calcShapeDistance(gesture1: Gesture, gesture2: Gesture): Float {
        var distance: Float
        var totalDistance = 0.0f
        for (i in 0 until SAMPLING_POINTS) {
            val x1 = gesture1.getX(i)
            val x2 = gesture2.getX(i)
            val y1 = gesture1.getY(i)
            val y2 = gesture2.getY(i)
            distance = Gesture.distance(x1, y1, x2, y2)
            totalDistance += distance
        }
        return totalDistance
    }

    class Pruner(
        /**
         * The length difference between a user gesture and a word gesture above which a word will
         * be pruned.
         */
        private val lengthThreshold: Double,
        words: List<String>,
        keysByCharacter: Map<Char, GlideKey>,
    ) {

        /** A tree that provides fast access to words based on their first and last letter.  */
        private val wordTree = HashMap<Pair<Char, Char>, ArrayList<String>>()

        /**
         * Finds the words whose start and end letter are closest to the start and end points of the
         * user gesture.
         */
        fun pruneByExtremities(userGesture: Gesture, keys: Iterable<GlideKey>): ArrayList<String> {
            val remainingWords = ArrayList<String>()
            val startKeys = findNClosestKeys(userGesture.getFirstX(), userGesture.getFirstY(), 2, keys)
            val endKeys = findNClosestKeys(userGesture.getLastX(), userGesture.getLastY(), 2, keys)
            for (startKey in startKeys) {
                for (endKey in endKeys) {
                    wordTree[Pair(startKey, endKey)]?.let { remainingWords.addAll(it) }
                }
            }
            return remainingWords
        }

        /**
         * Finds the words whose ideal gesture length is within a certain threshold of the user
         * gesture's length.
         */
        fun pruneByLength(
            userGesture: Gesture,
            words: ArrayList<String>,
            keysByCharacter: Map<Char, GlideKey>,
            keys: List<GlideKey>,
        ): ArrayList<String> {
            val remainingWords = ArrayList<String>()
            val key = keys.firstOrNull() ?: return arrayListOf()
            val radius = min(key.height, key.width)
            val userLength = userGesture.getLength()
            for (word in words) {
                val idealGestures = Gesture.generateIdealGestures(word, keysByCharacter)
                for (idealGesture in idealGestures) {
                    val wordIdealLength = getCachedIdealLength(word, idealGesture)
                    if (abs(userLength - wordIdealLength) < lengthThreshold * radius) {
                        remainingWords.add(word)
                    }
                }
            }
            return remainingWords
        }

        private val cachedIdealLength = ConcurrentHashMap<String, Float>()
        private fun getCachedIdealLength(word: String, idealGesture: Gesture): Float {
            return cachedIdealLength.getOrPut(word) { idealGesture.getLength() }
        }

        companion object {
            private fun getFirstKeyLastKey(word: String, keysByCharacter: Map<Char, GlideKey>): Pair<Char, Char>? {
                val firstBaseChar = Normalizer.normalize(word[0].toString(), Normalizer.Form.NFD)[0]
                val lastBaseChar = Normalizer.normalize(word[word.length - 1].toString(), Normalizer.Form.NFD)[0]
                val firstKey = keysByCharacter[firstBaseChar] ?: return null
                val lastKey = keysByCharacter[lastBaseChar] ?: return null
                return firstKey.char to lastKey.char
            }

            /** Finds a chosen number of keys closest to a given point on the keyboard. */
            private fun findNClosestKeys(x: Float, y: Float, n: Int, keys: Iterable<GlideKey>): Iterable<Char> {
                return keys
                    .map { it to Gesture.distance(it.centerX, it.centerY, x, y) }
                    .sortedBy { it.second }
                    .take(n)
                    .map { it.first.char }
            }
        }

        init {
            for (word in words) {
                getFirstKeyLastKey(word, keysByCharacter)?.let { keyPair ->
                    wordTree.getOrPut(keyPair) { arrayListOf() }.add(word)
                }
            }
        }
    }

    class Gesture(
        private val xs: FloatArray = FloatArray(MAX_SIZE),
        private val ys: FloatArray = FloatArray(MAX_SIZE),
        private var size: Int = 0,
    ) {
        companion object {
            private const val MAX_SIZE = 500

            fun generateIdealGestures(word: String, keysByCharacter: Map<Char, GlideKey>): List<Gesture> {
                val idealGesture = Gesture()
                val idealGestureWithLoops = Gesture()
                var previousLetter = ' '
                var hasLoops = false

                // Add points for each key
                for (c in word) {
                    val lc = Character.toLowerCase(c)
                    var key = keysByCharacter[lc]
                    if (key == null) {
                        // Try finding the base character instead, e.g., the "e" key instead of "é"
                        val baseCharacter: Char = Normalizer.normalize(lc.toString(), Normalizer.Form.NFD)[0]
                        key = keysByCharacter[baseCharacter]
                        if (key == null) {
                            continue
                        }
                    }

                    // We add a little loop on the key for duplicate letters
                    // so that we can differentiate words like pool and poll, lull and lul, etc...
                    if (previousLetter == lc) {
                        idealGestureWithLoops.addPoint(key.centerX + key.width / 4.0f, key.centerY + key.height / 4.0f)
                        idealGestureWithLoops.addPoint(key.centerX + key.width / 4.0f, key.centerY - key.height / 4.0f)
                        idealGestureWithLoops.addPoint(key.centerX - key.width / 4.0f, key.centerY - key.height / 4.0f)
                        idealGestureWithLoops.addPoint(key.centerX - key.width / 4.0f, key.centerY + key.height / 4.0f)
                        hasLoops = true
                        idealGesture.addPoint(key.centerX, key.centerY)
                    } else {
                        idealGesture.addPoint(key.centerX, key.centerY)
                        idealGestureWithLoops.addPoint(key.centerX, key.centerY)
                    }
                    previousLetter = lc
                }
                return when (hasLoops) {
                    true -> listOf(idealGesture, idealGestureWithLoops)
                    false -> listOf(idealGesture)
                }
            }

            fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
                return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
            }
        }

        val isEmpty: Boolean
            get() = size == 0

        fun addPoint(x: Float, y: Float) {
            if (size >= MAX_SIZE) {
                return
            }
            xs[size] = x
            ys[size] = y
            size += 1
        }

        /**
         * Resamples the gesture into a new gesture with the chosen number of points by oversampling
         * it.
         */
        fun resample(numPoints: Int): Gesture {
            val interpointDistance = (getLength() / numPoints)
            val resampledGesture = Gesture()
            resampledGesture.addPoint(xs[0], ys[0])
            var lastX = xs[0]
            var lastY = ys[0]
            var newX: Float
            var newY: Float
            var cumulativeError = 0.0f

            // otherwise nothing happens if size is only 1:
            if (this.size == 1) {
                for (i in 0 until SAMPLING_POINTS) {
                    resampledGesture.addPoint(xs[0], ys[0])
                }
            }

            for (i in 0 until size - 1) {
                // We calculate the unit vector from the two points we're between in the actual
                // gesture
                var dx = xs[i + 1] - xs[i]
                var dy = ys[i + 1] - ys[i]
                val norm = sqrt(dx.pow(2.0f) + dy.pow(2.0f))
                dx /= norm
                dy /= norm

                // The number of evenly sampled points that fit between the two actual points
                var numNewPoints = norm / interpointDistance

                // The number of point that'd fit between the two actual points is often not round,
                // which means we'll get an increasingly large error as we resample the gesture
                // and round down that number. To compensate for this we keep track of the error
                // and add additional points when it gets too large.
                cumulativeError += numNewPoints - numNewPoints.toInt()
                if (cumulativeError > 1) {
                    numNewPoints = (numNewPoints.toInt() + cumulativeError.toInt()).toFloat()
                    cumulativeError %= 1
                }
                for (j in 0 until numNewPoints.toInt()) {
                    newX = lastX + dx * interpointDistance
                    newY = lastY + dy * interpointDistance
                    lastX = newX
                    lastY = newY
                    resampledGesture.addPoint(newX, newY)
                }
            }
            return resampledGesture
        }

        fun normalizeByBoxSide(): Gesture {
            val normalizedGesture = Gesture()

            var maxX = -1.0f
            var maxY = -1.0f
            var minX = 10000.0f
            var minY = 10000.0f

            for (i in 0 until size) {
                maxX = max(xs[i], maxX)
                maxY = max(ys[i], maxY)
                minX = min(xs[i], minX)
                minY = min(ys[i], minY)
            }

            val width = maxX - minX
            val height = maxY - minY
            val longestSide = max(max(width, height), 0.00001f)

            val centroidX = (width / 2 + minX) / longestSide
            val centroidY = (height / 2 + minY) / longestSide

            for (i in 0 until size) {
                val x = xs[i] / longestSide - centroidX
                val y = ys[i] / longestSide - centroidY
                normalizedGesture.addPoint(x, y)
            }

            return normalizedGesture
        }

        fun getFirstX(): Float = xs.getOrElse(0) { 0f }
        fun getFirstY(): Float = ys.getOrElse(0) { 0f }
        fun getLastX(): Float = xs.getOrElse(size - 1) { 0f }
        fun getLastY(): Float = ys.getOrElse(size - 1) { 0f }

        fun getLength(): Float {
            var length = 0f
            for (i in 1 until size) {
                length += distance(xs[i - 1], ys[i - 1], xs[i], ys[i])
            }
            return length
        }

        fun getX(i: Int): Float = xs.getOrElse(i) { 0f }
        fun getY(i: Int): Float = ys.getOrElse(i) { 0f }
    }
}
