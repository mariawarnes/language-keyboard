package io.github.mariawarnes.languagekeyboard

/**
 * Offline check for one common learner mistake that a word list can't catch, because every word is
 * real: a demonstrative that doesn't agree with what follows "is", e.g. "este es una prueba" →
 * "esta es una prueba". Works without the online grammar check.
 *
 * Each hit says what gives the gender away (the article, "una") and which noun it belongs to
 * ("prueba"), so the keyboard can explain the fix in your own language.
 */
object Agreement {

    class Hit(
        val start: Int,
        val end: Int,
        /** What was typed ("este") and what it should be ("esta"). */
        val word: String,
        val fix: String,
        /** Both forms, masculine first: "este/esta". */
        val pair: String,
        /** The article that shows the gender ("una", "un'"). */
        val article: String,
        /** The noun it belongs to ("prueba"), or null when it isn't typed yet. */
        val noun: String?,
        val feminine: Boolean,
        /** End of the article: while the cursor is there it may still be being typed. */
        val checkedUpTo: Int,
    )

    private class Rules(
        /** Masculine ↔ feminine demonstratives (singular and plural). */
        val pairs: List<Pair<String, String>>,
        val be: String,
        val masculine: Set<String>,
        val feminine: Set<String>,
        /** Adjectives that often come before the noun ("una buena idea"): the noun is the next word. */
        val before: Set<String>,
    )

    private val RULES = mapOf(
        "es" to Rules(
            pairs = listOf("este" to "esta", "ese" to "esa", "aquel" to "aquella",
                "estos" to "estas", "esos" to "esas", "aquellos" to "aquellas"),
            be = "es|era|fue|será|sería|sea|son|eran|fueron|serán|serían|sean",
            masculine = setOf("un", "el", "unos", "los"),
            feminine = setOf("una", "la", "unas", "las"),
            before = plurals("buen", "bueno", "buena", "mal", "malo", "mala", "gran", "grande", "nuevo", "nueva",
                "primer", "primero", "primera", "último", "última", "otro", "otra", "mismo", "misma", "pequeño",
                "pequeña", "mejor", "peor", "verdadero", "verdadera", "segundo", "segunda", "único", "única",
                "propio", "propia", "viejo", "vieja"),
        ),
        "pt" to Rules(
            pairs = listOf("este" to "esta", "esse" to "essa", "aquele" to "aquela",
                "estes" to "estas", "esses" to "essas", "aqueles" to "aquelas"),
            be = "é|era|foi|será|seria|seja|são|eram|foram|serão|seriam|sejam",
            masculine = setOf("um", "o", "uns", "os"),
            feminine = setOf("uma", "a", "umas", "as"),
            before = plurals("bom", "boa", "mau", "má", "grande", "novo", "nova", "primeiro", "primeira", "último",
                "última", "outro", "outra", "mesmo", "mesma", "pequeno", "pequena", "melhor", "pior", "verdadeiro",
                "verdadeira", "segundo", "segunda", "único", "única", "próprio", "própria", "velho", "velha"),
        ),
        "it" to Rules(
            pairs = listOf("questo" to "questa", "quello" to "quella", "questi" to "queste", "quelli" to "quelle"),
            be = "è|era|fu|sarà|sarebbe|sia|sono|erano|furono|saranno|sarebbero|siano",
            masculine = setOf("un", "uno", "il", "lo", "i", "gli"),
            feminine = setOf("una", "la", "le"),
            before = plurals("buon", "buono", "buona", "bel", "bello", "bella", "gran", "grande", "nuovo", "nuova",
                "primo", "prima", "ultimo", "ultima", "altro", "altra", "stesso", "stessa", "piccolo", "piccola",
                "migliore", "peggiore", "vero", "vera", "secondo", "seconda", "unico", "unica", "vecchio", "vecchia"),
        ),
    )

    /** The words plus rough plurals (-s, -es, -i, -e): close enough for a lookup list. */
    private fun plurals(vararg words: String) =
        words.flatMap { listOf(it, it + "s", it + "es", it.dropLast(1) + "i", it.dropLast(1) + "e") }.toSet()

    private val STRESSED_A = Regex("h?[aá].*")

    fun isSupported(language: String) = language in RULES

    fun check(text: String, language: String): List<Hit> {
        val rules = RULES[language] ?: return emptyList()
        val demonstratives = rules.pairs.flatMap { listOf(it.first, it.second) }.joinToString("|")
        // demonstrative, optional "no", a form of "to be", article; then (looked at, not consumed)
        // the next one or two words, to find the noun.
        val pattern = Regex(
            "(?<![\\p{L}'])($demonstratives)\\s+(?:(no|non|não)\\s+)?(${rules.be})\\s+(\\p{L}+)(['’])?" +
                "(?=(?:\\s*(\\p{L}+))?(?:\\s+(\\p{L}+))?)",
            RegexOption.IGNORE_CASE,
        )
        val hits = ArrayList<Hit>()
        for (m in pattern.findAll(text)) {
            val word = m.groupValues[1]
            val article = m.groupValues[4].lowercase()
            val elided = m.groupValues[5].isNotEmpty()
            val next = m.groupValues[6].takeIf { it.isNotEmpty() }
            val after = m.groupValues[7].takeIf { it.isNotEmpty() }
            val noun = if (next != null && after != null && next.lowercase() in rules.before) after else next
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
                        if (language == "es" && (article == "el" || article == "un") &&
                            next?.lowercase()?.matches(STRESSED_A) == true
                        ) continue
                        false
                    }
                    else -> continue
                }
            }
            if (isFeminine == wantFeminine) continue
            hits += Hit(
                start = m.range.first,
                end = m.range.first + word.length,
                word = word,
                fix = (if (wantFeminine) pair.second else pair.first).matchCase(word),
                pair = "${pair.first}/${pair.second}",
                article = m.groupValues[4] + m.groupValues[5],
                noun = noun,
                feminine = wantFeminine,
                checkedUpTo = m.groups[4]!!.range.last + 1,
            )
        }
        return hits
    }

    private fun String.matchCase(like: String) = when {
        like.all { !it.isLetter() || it.isUpperCase() } && like.length > 1 -> uppercase()
        like.first().isUpperCase() -> replaceFirstChar { it.uppercase() }
        else -> this
    }
}
