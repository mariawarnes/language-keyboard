package io.github.mariawarnes.languagekeyboard

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

/**
 * A word list for one language, most frequent word first (so a word's index is its frequency rank).
 * Immutable once built, so it can be read from any thread.
 *
 * Matching is accent-insensitive: "cancion" finds "canción", "esta" also offers "está".
 */
class Dictionary(val language: String, entries: List<String>) {

    val locale: Locale = Locale.forLanguageTag(language)

    private val words: Array<String> = entries.toTypedArray()
    private val keys: Array<String> = Array(words.size) { fold(words[it], locale) }
    private val byKey: IntArray = words.indices.sortedWith(compareBy<Int>({ keys[it] }, { it })).toIntArray()
    private val lowerIndex: HashMap<String, Int> = HashMap<String, Int>(words.size * 2).also { map ->
        words.forEachIndexed { i, w -> map.putIfAbsent(w.lowercase(locale), i) }
    }

    val size: Int get() = words.size

    fun word(rank: Int): String = words[rank]

    /** The accent-free lower-case form used for matching. */
    fun key(rank: Int): String = keys[rank]

    fun rankOf(word: String): Int? = lowerIndex[word.lowercase(locale)]

    fun contains(word: String): Boolean = rankOf(word) != null

    /** Ranks of words starting with [prefix] (accent-insensitive), most frequent first. */
    fun completions(prefix: String, limit: Int): List<Int> {
        val p = fold(prefix, locale)
        if (p.isEmpty()) return emptyList()
        var lo = 0
        var hi = byKey.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[byKey[mid]] < p) lo = mid + 1 else hi = mid
        }
        val best = ArrayList<Int>(limit + 1)
        var i = lo
        while (i < byKey.size && keys[byKey[i]].startsWith(p)) {
            insertSorted(best, byKey[i], limit)
            i++
        }
        return best
    }

    class Candidate(val rank: Int, val distance: Int, val cost: Double)

    /**
     * Words within [maxDistance] typos of [typed] (insert, delete, substitute or swap two letters;
     * accents don't count), cheapest first. Cost mixes typo count and how common the word is.
     */
    fun corrections(typed: String, maxDistance: Int, limit: Int): List<Candidate> {
        val t = fold(typed, locale)
        if (t.isEmpty()) return emptyList()
        val best = ArrayList<Candidate>(limit + 1)
        val prev2 = IntArray(t.length + 1)
        val prev = IntArray(t.length + 1)
        val cur = IntArray(t.length + 1)
        for (rank in words.indices) {
            val k = keys[rank]
            if (abs(k.length - t.length) > maxDistance) continue
            val d = distance(t, k, maxDistance, prev2, prev, cur)
            if (d > maxDistance) continue
            val cost = d + 0.12 * ln(rank + 1.0)
            if (best.size == limit && cost >= best.last().cost) continue
            val c = Candidate(rank, d, cost)
            val at = best.indexOfFirst { it.cost > cost }.let { if (it < 0) best.size else it }
            best.add(at, c)
            if (best.size > limit) best.removeAt(best.size - 1)
        }
        return best
    }

    companion object {
        private val MARKS = Regex("\\p{Mn}+")

        /** Lower-case and strip accents/diacritics, for matching. */
        fun fold(s: String, locale: Locale): String =
            MARKS.replace(Normalizer.normalize(s.lowercase(locale), Normalizer.Form.NFD), "")

        private fun insertSorted(list: ArrayList<Int>, value: Int, limit: Int) {
            if (list.size == limit && value >= list.last()) return
            var at = list.size
            while (at > 0 && list[at - 1] > value) at--
            list.add(at, value)
            if (list.size > limit) list.removeAt(list.size - 1)
        }

        /**
         * Optimal-string-alignment distance between [a] and [b], giving up early (returning
         * [max] + 1) once it must exceed [max]. Scratch arrays are passed in to avoid allocations.
         */
        private fun distance(a: String, b: String, max: Int, prev2: IntArray, prev: IntArray, cur: IntArray): Int {
            val n = a.length
            for (i in 0..n) prev[i] = i
            for (j in 1..b.length) {
                cur[0] = j
                var rowMin = cur[0]
                for (i in 1..n) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    var v = minOf(prev[i] + 1, cur[i - 1] + 1, prev[i - 1] + cost)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                        v = minOf(v, prev2[i - 2] + 1)
                    }
                    cur[i] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > max) return max + 1
                System.arraycopy(prev, 0, prev2, 0, n + 1)
                System.arraycopy(cur, 0, prev, 0, n + 1)
            }
            return prev[n]
        }
    }
}
