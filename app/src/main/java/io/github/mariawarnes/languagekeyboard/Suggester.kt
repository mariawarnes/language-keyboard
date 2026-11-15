package io.github.mariawarnes.languagekeyboard

import kotlin.math.ln

/** What the keyboard knows about the word being typed. */
class SuggestionResult(
    val typed: String,
    /** The typed word is a real word (or one you taught the keyboard). */
    val known: Boolean,
    /** What autocorrect would change it to, if anything. */
    val correction: String?,
    /** Other words you might mean, best first. Never contains [typed] itself. */
    val suggestions: List<String>,
)

object Suggester {

    fun suggest(dict: Dictionary, typed: String, userWords: Set<String>): SuggestionResult {
        val locale = dict.locale
        val folded = Dictionary.fold(typed, locale)
        val lowerTyped = typed.lowercase(locale)
        val taught = lowerTyped in userWords
        val rank = dict.rankOf(typed)
        val candidates = dict.corrections(typed, if (folded.length <= 4) 1 else 2, 6)

        // Word lists built from subtitles contain common accent mistakes ("tambien"). If the same
        // letters with accents are far more common, treat what was typed as a mistake.
        var accentVariant: Int? = null
        for (c in candidates) {
            if (c.distance == 0 && dict.word(c.rank).lowercase(locale) != lowerTyped) {
                if (rank == null || rank > 10 * c.rank + 200) accentVariant = c.rank
                break
            }
        }
        val known = taught || (rank != null && accentVariant == null)

        var correction: String? = null
        if (!taught && correctable(typed)) {
            correction = when {
                accentVariant != null -> dict.word(accentVariant)
                // English "i" -> "I", "i'm" -> "I'm".
                rank != null && dict.word(rank)[0].isUpperCase() && typed[0].isLowerCase() -> dict.word(rank)
                !known && typed.length >= 2 -> candidates.firstOrNull()?.takeIf {
                    it.distance == 0 ||
                        (it.distance == 1 && it.rank < 20_000) ||
                        (it.distance == 2 && folded.length >= 6 && it.rank < 10_000)
                }?.let { dict.word(it.rank) }
                else -> null
            }
        }

        // Rank completions ("hab" -> "hablar") and corrections together.
        val scored = ArrayList<Pair<Double, Int>>()
        for (r in dict.completions(typed, 5)) {
            val key = dict.key(r)
            if (key != folded) scored += (0.3 + 0.12 * ln(r + 1.0) + 0.1 * (key.length - folded.length)) to r
        }
        for (c in candidates) scored += c.cost to c.rank
        scored.sortBy { it.first }

        // Show each spelling once, keeping the most common accent variant ("cómo", not also "cômo").
        val seen = HashSet<String>()
        val out = ArrayList<String>(4)
        for ((_, r) in scored) {
            val word = dict.word(r)
            if (word.lowercase(locale) == lowerTyped) continue
            if (!seen.add(dict.key(r))) continue
            out += matchCase(typed, word, dict)
            if (out.size == 4) break
        }
        return SuggestionResult(typed, known, correction?.let { matchCase(typed, it, dict) }, out)
    }

    /** Leave alone things that look deliberate: numbers, ALL-CAPS, single letters. */
    private fun correctable(typed: String): Boolean =
        typed.none { it.isDigit() } && !(typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() })

    fun matchCase(typed: String, word: String, dict: Dictionary): String = when {
        typed.isEmpty() -> word
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase(dict.locale)
        typed[0].isUpperCase() -> word.replaceFirstChar { it.titlecase(dict.locale) }
        else -> word
    }
}
