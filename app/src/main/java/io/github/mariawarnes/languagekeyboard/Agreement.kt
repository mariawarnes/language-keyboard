package io.github.mariawarnes.languagekeyboard

/**
 * Offline check for one common learner mistake that a word list can't catch, because every word is
 * real: a demonstrative that doesn't agree with what follows "is", e.g. "este es una prueba" →
 * "esta es una prueba". Works without the online grammar check.
 */
object Agreement {

    /** [checkedUpTo]: end of the article the demonstrative was checked against. */
    class Hit(val start: Int, val end: Int, val word: String, val fix: String, val message: String, val checkedUpTo: Int)

    private class Rules(
        /** Masculine ↔ feminine demonstratives (singular and plural). */
        val pairs: List<Pair<String, String>>,
        val be: String,
        val masculine: Set<String>,
        val feminine: Set<String>,
        val message: String,
    )

    private val RULES = mapOf(
        "es" to Rules(
            pairs = listOf("este" to "esta", "ese" to "esa", "aquel" to "aquella",
                "estos" to "estas", "esos" to "esas", "aquellos" to "aquellas"),
            be = "es|era|fue|será|sería|sea|son|eran|fueron|serán|serían|sean",
            masculine = setOf("un", "el", "unos", "los"),
            feminine = setOf("una", "la", "unas", "las"),
            message = "«%1\$s» debe concordar en género con el sustantivo: «%2\$s».",
        ),
        "pt" to Rules(
            pairs = listOf("este" to "esta", "esse" to "essa", "aquele" to "aquela",
                "estes" to "estas", "esses" to "essas", "aqueles" to "aquelas"),
            be = "é|era|foi|será|seria|seja|são|eram|foram|serão|seriam|sejam",
            masculine = setOf("um", "o", "uns", "os"),
            feminine = setOf("uma", "a", "umas", "as"),
            message = "«%1\$s» deve concordar em género com o substantivo: «%2\$s».",
        ),
        "it" to Rules(
            pairs = listOf("questo" to "questa", "quello" to "quella", "questi" to "queste", "quelli" to "quelle"),
            be = "è|era|fu|sarà|sarebbe|sia|sono|erano|furono|saranno|sarebbero|siano",
            masculine = setOf("un", "uno", "il", "lo", "i", "gli"),
            feminine = setOf("una", "la", "le"),
            message = "«%1\$s» deve concordare nel genere con il sostantivo: «%2\$s».",
        ),
    )

    private val STRESSED_A = Regex("h?[aá].*")

    fun isSupported(language: String) = language in RULES

    fun check(text: String, language: String): List<Hit> {
        val rules = RULES[language] ?: return emptyList()
        val demonstratives = rules.pairs.flatMap { listOf(it.first, it.second) }.joinToString("|")
        // demonstrative, optional "no", a form of "to be", article, next word
        val pattern = Regex(
            "(?<![\\p{L}'])($demonstratives)\\s+(?:(no|non|não)\\s+)?(${rules.be})\\s+(\\p{L}+)(['’])?(?:\\s*(\\p{L}+))?",
            RegexOption.IGNORE_CASE,
        )
        val hits = ArrayList<Hit>()
        for (m in pattern.findAll(text)) {
            val word = m.groupValues[1]
            val article = m.groupValues[4].lowercase()
            val elided = m.groupValues[5].isNotEmpty()
            val noun = m.groupValues[6].lowercase()
            val lower = word.lowercase()
            val pair = rules.pairs.first { lower == it.first || lower == it.second }
            val isFeminine = lower == pair.second
            val wantFeminine = when {
                // Italian "un'amica" is feminine; "l'" could be either.
                elided -> if (language == "it" && article == "un") true else continue
                else -> when (article) {
                    in rules.feminine -> true
                    in rules.masculine -> {
                        // Spanish feminine nouns starting with a stressed "a" take "el"/"un": "esta es el agua".
                        if (language == "es" && (article == "el" || article == "un") && noun.matches(STRESSED_A)) continue
                        false
                    }
                    else -> continue
                }
            }
            if (isFeminine == wantFeminine) continue
            val fix = (if (wantFeminine) pair.second else pair.first).matchCase(word)
            val correct = fix + " " + m.value.substring(word.length).trim().replace(Regex("\\s+"), " ")
            hits += Hit(m.range.first, m.range.first + word.length, word, fix, rules.message.format(word, correct), m.groups[4]!!.range.last + 1)
        }
        return hits
    }

    private fun String.matchCase(like: String) = when {
        like.all { !it.isLetter() || it.isUpperCase() } && like.length > 1 -> uppercase()
        like.first().isUpperCase() -> replaceFirstChar { it.uppercase() }
        else -> this
    }
}
