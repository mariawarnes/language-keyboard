package io.github.mariawarnes.languagekeyboard

/**
 * Offline gender agreement for Spanish, Portuguese and Italian. A word list can't catch these
 * mistakes because every word is real ("este es el ciudad").
 *
 * The noun decides: its gender comes from its ending (-dad, -ción… are feminine) or from a list of
 * common nouns and exceptions ("problema" is masculine), never from the article in front of it,
 * because that article may be the mistake. Then:
 *  - articles and demonstratives right before the noun must match it ("el ciudad" → "la ciudad");
 *  - "este es una prueba": the demonstrative must match the noun after "is" ("esta es…").
 * Only when the noun's gender is unknown does the article stand in for it.
 *
 * Each hit carries how the gender is known, so the keyboard can explain it in your language.
 */
object Agreement {

    enum class Kind { DEMONSTRATIVE, ARTICLE, NOT_A_NOUN }

    /** How the gender is known. */
    enum class Reason { ENDING_ALWAYS, ENDING_USUALLY, EXCEPTION, LISTED, ARTICLE, STRESSED_A }

    class Hit(
        val start: Int,
        val end: Int,
        /** What was typed and what it should be. */
        val word: String,
        val fix: String,
        val kind: Kind,
        val reason: Reason,
        val feminine: Boolean,
        /** The noun (spelt correctly); for [Reason.ARTICLE] the article that shows the gender. */
        val evidence: String,
        /** The ending behind the rule ("dad"), for the ENDING / EXCEPTION reasons. */
        val ending: String?,
        /** The noun with its article, to learn them together: "el coche". */
        val withArticle: String,
        /** For demonstratives, both forms: "este/esta". */
        val pair: String?,
        /** For [Kind.NOT_A_NOUN], the article it follows. */
        val determiner: String?,
        /** End of the last word used: while the cursor is there that word may still be being typed. */
        val checkedUpTo: Int,
    )

    class Gender(val feminine: Boolean, val reason: Reason, val ending: String?, val plural: Boolean, val singular: String)

    private enum class DetKind { DEFINITE, INDEFINITE, DEMONSTRATIVE }

    private class Det(val feminine: Boolean, val plural: Boolean, val kind: DetKind, val other: String)

    private class Lang(
        val code: String,
        val determiners: Map<String, Det>,
        /** Articles that are too often something else (a preposition, a pronoun) to mark on their own. */
        val unreliable: Set<String>,
        val be: Set<String>,
        val negation: Set<String>,
        /** Endings that decide the gender (exceptions are listed). */
        val strong: List<Pair<String, Boolean>>,
        /** -a / -o: right most of the time, but only trusted where the next word must be a noun. */
        val usual: List<Pair<String, Boolean>>,
        val exceptions: Map<String, Boolean>,
        val listed: Map<String, Boolean>,
        /** Either gender, or the gender changes the meaning: never marked. */
        val ambiguous: Set<String>,
        val ambiguousEndings: List<String>,
        /** Feminine nouns starting with a stressed "a" that take "el"/"un" in the singular. */
        val stressedA: Set<String>,
        /** Adjectives that often come before the noun ("una buena idea"). */
        val before: Set<String>,
        /** Real words that after an article are almost always a typo for a noun. */
        val confusables: Map<String, String>,
        val singulars: (String) -> List<String>,
    )

    // --- Data ----------------------------------------------------------------------------------

    private fun det(kind: DetKind, plural: Boolean, masculine: String, feminine: String) = listOf(
        masculine to Det(false, plural, kind, feminine),
        feminine to Det(true, plural, kind, masculine),
    )

    private fun words(s: String) = s.split(" ").filter { it.isNotBlank() }.toSet()

    private fun genders(feminine: String, masculine: String): Map<String, Boolean> =
        words(feminine).associateWith { true } + words(masculine).associateWith { false }

    /** The words plus rough plurals (-s, -es, -i, -e): close enough for a lookup list. */
    private fun withPlurals(s: String) =
        words(s).flatMap { listOf(it, it + "s", it + "es", it.dropLast(1) + "i", it.dropLast(1) + "e") }.toSet()

    private val SPANISH = Lang(
        code = "es",
        determiners = (
            det(DetKind.DEFINITE, false, "el", "la") + det(DetKind.DEFINITE, true, "los", "las") +
                det(DetKind.INDEFINITE, false, "un", "una") + det(DetKind.INDEFINITE, true, "unos", "unas") +
                det(DetKind.DEMONSTRATIVE, false, "este", "esta") + det(DetKind.DEMONSTRATIVE, true, "estos", "estas") +
                det(DetKind.DEMONSTRATIVE, false, "ese", "esa") + det(DetKind.DEMONSTRATIVE, true, "esos", "esas") +
                det(DetKind.DEMONSTRATIVE, false, "aquel", "aquella") +
                det(DetKind.DEMONSTRATIVE, true, "aquellos", "aquellas")
            ).toMap(),
        unreliable = emptySet(),
        be = words("es era fue será sería sea son eran fueron serán serían sean"),
        negation = words("no"),
        strong = listOf(
            "ción" to true, "sión" to true, "xión" to true, "dad" to true, "tad" to true, "tud" to true,
            "umbre" to true, "aje" to false, "or" to false,
        ),
        usual = listOf("a" to true, "o" to false),
        exceptions = genders(
            feminine = "mano foto moto libido flor labor coliflor",
            masculine = "día mapa problema tema sistema programa idioma clima planeta poema drama esquema dilema " +
                "diploma fantasma pijama dogma enigma panorama síntoma telegrama crucigrama aroma lema prisma " +
                "trauma diagrama teorema emblema karma tranvía",
        ),
        listed = genders(
            feminine = "noche calle gente leche llave nube carne clase muerte suerte fuente sangre torre tarde madre " +
                "mente frase base nieve fe ley voz luz paz vez cruz nariz raíz piel miel sal cárcel mujer imagen " +
                "razón red pared sed catedral señal crisis tesis hipótesis dosis especie superficie serie gripe " +
                "opinión religión región unión mesa puerta ventana comida persona familia semana hora vida palabra " +
                "idea playa escuela iglesia tienda cama silla botella taza película música historia letra página " +
                "fiesta cerveza tierra luna guerra empresa oficina manzana naranja fruta bicicleta mochila montaña " +
                "lengua boca cabeza cara pierna espalda",
            masculine = "coche café hombre padre parque aire bosque cine diente golpe estante lunes martes miércoles " +
                "jueves viernes país mes árbol papel hotel hospital animal sol pan tren avión camión corazón examen " +
                "jardín reloj lápiz pez autobús móvil té champú menú postre puente accidente restaurante ambiente " +
                "deporte norte sur oeste chocolate tomate aguacate fin rey plan bar hogar lugar dólar interés inglés " +
                "francés pie arroz maíz ajedrez sillón balcón salón ratón jamón limón melón botón cajón pantalón " +
                "millón almacén volumen crimen origen análisis virus paraguas cumpleaños sofá libro perro gato año " +
                "mundo dinero tiempo cuerpo ojo brazo dedo vaso plato queso huevo barco edificio museo teatro banco " +
                "aeropuerto campo cielo techo dormitorio mercado momento minuto periódico teléfono ejemplo invierno " +
                "verano otoño lago océano",
        ),
        ambiguous = words(
            "radio capital frente orden cometa mar arte azúcar internet modelo guía policía testigo joven cónyuge " +
                "mañana cura corte final pendiente margen este parte pirata poeta profeta astronauta atleta idiota " +
                "colega psiquiatra pediatra burócrata demócrata homicida suicida patriota piloto soldado miembro rehén",
        ),
        ambiguousEndings = listOf("ista", "nte"),
        stressedA = words(
            "agua águila alma arma aula área ala ave hambre hacha hada haba arca asa ama alba ancla ansia aspa aria",
        ),
        before = withPlurals(
            "buen bueno buena mal malo mala gran grande nuevo nueva primer primero primera último última otro otra " +
                "mismo misma pequeño pequeña mejor peor verdadero verdadera segundo segunda único única propio propia " +
                "viejo vieja",
        ),
        confusables = mapOf("cuidad" to "ciudad", "cuidades" to "ciudades"),
        singulars = { w ->
            when {
                w.endsWith("iones") -> listOf(w.dropLast(5) + "ión")
                w.endsWith("ces") -> listOf(w.dropLast(3) + "z")
                w.endsWith("es") -> listOf(w.dropLast(1), w.dropLast(2))
                w.endsWith("s") -> listOf(w.dropLast(1))
                else -> emptyList()
            }
        },
    )

    private val PORTUGUESE = Lang(
        code = "pt",
        determiners = (
            det(DetKind.DEFINITE, false, "o", "a") + det(DetKind.DEFINITE, true, "os", "as") +
                det(DetKind.INDEFINITE, false, "um", "uma") + det(DetKind.INDEFINITE, true, "uns", "umas") +
                det(DetKind.DEMONSTRATIVE, false, "este", "esta") + det(DetKind.DEMONSTRATIVE, true, "estes", "estas") +
                det(DetKind.DEMONSTRATIVE, false, "esse", "essa") + det(DetKind.DEMONSTRATIVE, true, "esses", "essas") +
                det(DetKind.DEMONSTRATIVE, false, "aquele", "aquela") +
                det(DetKind.DEMONSTRATIVE, true, "aqueles", "aquelas")
            ).toMap(),
        // "a" is also a preposition ("a pé") and "o"/"a" are pronouns.
        unreliable = words("o a"),
        be = words("é era foi será seria seja são eram foram serão seriam sejam"),
        negation = words("não"),
        strong = listOf("ção" to true, "são" to true, "dade" to true, "tude" to true, "agem" to true),
        usual = listOf("a" to true, "o" to false),
        exceptions = genders(
            feminine = "tribo foto moto libido",
            masculine = "dia mapa problema tema sistema programa idioma clima planeta poema drama esquema dilema " +
                "diploma fantasma pijama cinema",
        ),
        listed = genders(
            feminine = "mãe noite cor dor flor vez luz paz voz lei rede parede gente morte sorte tarde fonte chave " +
                "nuvem ordem mão casa mesa porta janela comida pessoa família semana hora vida palavra ideia praia " +
                "escola igreja loja cama cadeira garrafa música história carta festa cerveja terra lua guerra empresa",
            masculine = "leite nome homem pai café chá mês país hotel papel pão coração avião carro livro cão gato ano " +
                "mundo dinheiro tempo corpo olho braço dedo copo prato queijo ovo peixe barco prédio museu teatro " +
                "banco aeroporto povo campo céu momento minuto jornal telefone exemplo inverno verão rio lago dente sol",
        ),
        ambiguous = words(
            "radio rádio capital cometa modelo guia policia polícia personagem estudante parte poeta piloto atleta " +
                "colega pirata idiota",
        ),
        ambiguousEndings = listOf("ista", "nte"),
        stressedA = emptySet(),
        before = withPlurals(
            "bom boa mau má grande novo nova primeiro primeira último última outro outra mesmo mesma pequeno pequena " +
                "melhor pior verdadeiro verdadeira segundo segunda único única próprio própria velho velha",
        ),
        confusables = emptyMap(),
        singulars = { w ->
            when {
                w.endsWith("ões") || w.endsWith("ães") -> listOf(w.dropLast(3) + "ão")
                w.endsWith("es") -> listOf(w.dropLast(1), w.dropLast(2))
                w.endsWith("s") -> listOf(w.dropLast(1))
                else -> emptyList()
            }
        },
    )

    private val ITALIAN = Lang(
        code = "it",
        // Singular only for articles: Italian plurals change the ending (-o → -i, -a → -e).
        determiners = (
            det(DetKind.DEFINITE, false, "il", "la") + det(DetKind.INDEFINITE, false, "un", "una") +
                det(DetKind.DEMONSTRATIVE, false, "questo", "questa") +
                det(DetKind.DEMONSTRATIVE, false, "quello", "quella") +
                det(DetKind.DEMONSTRATIVE, true, "questi", "queste") +
                det(DetKind.DEMONSTRATIVE, true, "quelli", "quelle")
            ).toMap() + mapOf(
            "lo" to Det(false, false, DetKind.DEFINITE, "la"),
            "uno" to Det(false, false, DetKind.INDEFINITE, "una"),
        ),
        unreliable = emptySet(),
        be = words("è era fu sarà sarebbe sia sono erano furono saranno sarebbero siano"),
        negation = words("non"),
        strong = listOf("zione" to true, "sione" to true, "tà" to true, "tù" to true, "udine" to true, "ore" to false),
        usual = listOf("a" to true, "o" to false),
        exceptions = genders(
            feminine = "mano radio foto moto auto",
            masculine = "problema tema sistema programma clima pianeta cinema dramma poema schema diploma panorama papa",
        ),
        listed = genders(
            feminine = "madre notte chiave gente morte fame sete carne classe frase nave neve luce voce pace croce " +
                "fonte torre casa finestra persona famiglia settimana vita parola idea scuola chiesa birra terra " +
                "luna guerra",
            masculine = "padre nome mare pane fiore sole cane ponte latte caffè tè mese paese pesce giornale " +
                "ospedale animale film sport bar libro gatto anno mondo tempo corpo occhio braccio dito bicchiere " +
                "piatto momento minuto esempio inverno fiume lago",
        ),
        ambiguous = words(
            "radio capitale fronte fine arte modello guida cantante nipote parte poeta pilota atleta collega pirata " +
                "idiota psichiatra pediatra",
        ),
        ambiguousEndings = listOf("ista", "nte"),
        stressedA = emptySet(),
        before = withPlurals(
            "buon buono buona bel bello bella gran grande nuovo nuova primo prima ultimo ultima altro altra stesso " +
                "stessa piccolo piccola migliore peggiore vero vera secondo seconda unico unica vecchio vecchia",
        ),
        confusables = emptyMap(),
        singulars = { emptyList() },
    )

    private val LANGS = listOf(SPANISH, PORTUGUESE, ITALIAN).associateBy { it.code }

    fun isSupported(language: String) = language in LANGS

    // --- Gender of a noun ------------------------------------------------------------------------

    /** The gender of [word] (singular or plural), or null when it can't be told. */
    fun gender(word: String, language: String): Gender? = LANGS[language]?.let { gender(it, word) }

    private fun gender(lang: Lang, word: String): Gender? {
        val w = word.lowercase()
        classify(lang, w)?.let { return Gender(it.first, it.second, it.third, false, w) }
        for (s in lang.singulars(w)) {
            if (s.length < 2) continue
            classify(lang, s)?.let { return Gender(it.first, it.second, it.third, true, s) }
        }
        return null
    }

    private fun classify(lang: Lang, w: String): Triple<Boolean, Reason, String?>? {
        lang.exceptions[w]?.let { feminine ->
            val rule = (lang.strong + lang.usual).firstOrNull { w.endsWith(it.first) }?.first
            return Triple(feminine, if (rule != null) Reason.EXCEPTION else Reason.LISTED, rule)
        }
        lang.listed[w]?.let { return Triple(it, Reason.LISTED, null) }
        if (w in lang.stressedA) return Triple(true, Reason.LISTED, null)
        if (w in lang.ambiguous || lang.ambiguousEndings.any { w.endsWith(it) }) return null
        lang.strong.firstOrNull { w.endsWith(it.first) && w.length > it.first.length + 1 }
            ?.let { return Triple(it.second, Reason.ENDING_ALWAYS, it.first) }
        lang.usual.firstOrNull { w.endsWith(it.first) && w.length > it.first.length + 1 }
            ?.let { return Triple(it.second, Reason.ENDING_USUALLY, it.first) }
        return null
    }

    /** "la ciudad", "el agua", "lo zaino", "l'amica". */
    private fun withArticle(lang: Lang, g: Gender): String {
        val n = g.singular
        val article = when (lang.code) {
            "es" -> if (g.feminine && n !in lang.stressedA) "la" else "el"
            "pt" -> if (g.feminine) "a" else "o"
            else -> when {
                startsWithVowel(n) -> "l'"
                g.feminine -> "la"
                takesLo(n) -> "lo"
                else -> "il"
            }
        }
        return if (article.endsWith("'")) article + n else "$article $n"
    }

    private fun startsWithVowel(n: String) = n.isNotEmpty() && n[0].lowercaseChar() in "aeiouàèéìòùh"

    /** Italian "lo"/"uno": before s + consonant, z, gn, ps, pn, x, y. */
    private fun takesLo(n: String): Boolean {
        val w = n.lowercase()
        return w.startsWith("gn") || w.startsWith("ps") || w.startsWith("pn") || (w.isNotEmpty() && w[0] in "zxy") ||
            (w.length > 1 && w[0] == 's' && w[1] !in "aeiouàèéìòù")
    }

    // --- Check -----------------------------------------------------------------------------------

    private class Token(val text: String, val start: Int, val end: Int, val elided: Boolean) {
        val lower = text.lowercase()
    }

    private val WORD = Regex("\\p{L}+")

    fun check(text: String, language: String): List<Hit> {
        val lang = LANGS[language] ?: return emptyList()
        val tokens = WORD.findAll(text).map {
            val end = it.range.last + 1
            Token(it.value, it.range.first, end, end < text.length && text[end] in "'’")
        }.toList()

        /** Only spaces between tokens [a] and [b] (or [a] is elided straight onto [b]). */
        fun adjacent(a: Int, b: Int): Boolean {
            if (a < 0 || b >= tokens.size) return false
            val gap = text.substring(tokens[a].end, tokens[b].start)
            return if (tokens[a].elided) gap.length == 1 else gap.isNotEmpty() && gap.isBlank()
        }

        /** The noun after the word at [i], skipping an adjective like "buena". */
        fun nounAfter(i: Int): Int? {
            if (!adjacent(i, i + 1)) return null
            if (tokens[i + 1].lower in lang.before) return if (adjacent(i + 1, i + 2)) i + 2 else null
            return i + 1
        }

        fun spelt(t: Token) = lang.confusables[t.lower]?.matchCase(t.text) ?: t.text

        val hits = ArrayList<Hit>()

        // 1. Article or demonstrative right before its noun: "el ciudad" → "la ciudad".
        for (i in tokens.indices) {
            val typed = tokens[i]
            val d = lang.determiners[typed.lower] ?: continue
            if (typed.elided || typed.lower in lang.unreliable) continue
            val n = nounAfter(i) ?: continue
            val nounToken = tokens[n]
            val noun = spelt(nounToken)
            if (noun != nounToken.text) {
                hits += Hit(
                    nounToken.start, nounToken.end, nounToken.text, noun, Kind.NOT_A_NOUN, Reason.LISTED, false,
                    noun, null, noun, null, typed.text, nounToken.end,
                )
            }
            val g = gender(lang, noun) ?: continue
            if (g.plural != d.plural) continue
            // "-a is feminine" isn't safe after "la"/"el" ("la trabajo" = I work it; "el" for "él") or
            // demonstratives ("ese trabaja" = that one works); after "un"/"una" it is.
            if (g.reason == Reason.ENDING_USUALLY && d.kind != DetKind.INDEFINITE) continue
            val article = withArticle(lang, g)
            val stressed = lang.code == "es" && g.feminine && !g.plural && g.singular in lang.stressedA
            if (stressed && d.kind != DetKind.DEMONSTRATIVE) {
                // "el agua", "un arma": feminine, but "la"/"una" become "el"/"un".
                if (d.feminine) {
                    hits += Hit(
                        typed.start, typed.end, typed.text, d.other.matchCase(typed.text), Kind.ARTICLE,
                        Reason.STRESSED_A, true, noun, null, article, null, null, nounToken.end,
                    )
                }
                continue
            }
            if (d.feminine == g.feminine) continue
            val demonstrative = d.kind == DetKind.DEMONSTRATIVE
            val fix = (if (lang.code == "it" && !demonstrative) italianArticle(noun, g.feminine, d.kind) else d.other)
                ?: continue
            hits += Hit(
                typed.start, typed.end, typed.text, fix.matchCase(typed.text),
                if (demonstrative) Kind.DEMONSTRATIVE else Kind.ARTICLE, g.reason, g.feminine, noun, g.ending,
                article, if (demonstrative) pair(typed.lower, d) else null, null, nounToken.end,
            )
        }

        // 2. "este es una prueba": the demonstrative goes with the noun after "is".
        for (j in tokens.indices) {
            val typed = tokens[j]
            val p = lang.determiners[typed.lower]?.takeIf { it.kind == DetKind.DEMONSTRATIVE } ?: continue
            var k = j + 1
            if (adjacent(j, k) && tokens[k].lower in lang.negation) k++
            if (!adjacent(k - 1, k) || tokens[k].lower !in lang.be) continue
            val a = k + 1
            if (!adjacent(k, a)) continue
            val articleToken = tokens[a]
            val article = lang.determiners[articleToken.lower]?.takeIf { it.kind != DetKind.DEMONSTRATIVE }
            val elidedArticle = articleToken.elided && (articleToken.lower == "l" || articleToken.lower == "un")
            if (article == null && !elidedArticle) continue
            val n = if (articleToken.elided) (if (adjacent(a, a + 1)) a + 1 else null) else nounAfter(a)
            val noun = n?.let { spelt(tokens[it]) }
            val g = noun?.let { gender(lang, it) }
            val feminine: Boolean
            val reason: Reason
            val evidence: String
            if (noun != null && g != null) {
                feminine = g.feminine
                reason = g.reason
                evidence = noun
            } else {
                // Noun unknown or not typed yet: the article is the best clue.
                feminine = when {
                    articleToken.elided -> if (lang.code == "it" && articleToken.lower == "un") true else continue
                    article!!.feminine -> true
                    // "esta es el agua": "el" doesn't make it masculine.
                    lang.code == "es" && noun != null && noun.lowercase() in lang.stressedA -> continue
                    else -> false
                }
                reason = Reason.ARTICLE
                evidence = articleToken.text + if (articleToken.elided) "'" else ""
            }
            if (p.feminine == feminine) continue
            hits += Hit(
                typed.start, typed.end, typed.text, p.other.matchCase(typed.text), Kind.DEMONSTRATIVE, reason,
                feminine, evidence, g?.ending, g?.let { withArticle(lang, it) } ?: "", pair(typed.lower, p), null,
                n?.let { tokens[it].end } ?: articleToken.end,
            )
        }
        return hits.sortedBy { it.start }
    }

    private fun pair(typed: String, d: Det) = if (d.feminine) "${d.other}/$typed" else "$typed/${d.other}"

    /** Italian articles depend on the next sound; null where it would need an apostrophe. */
    private fun italianArticle(noun: String, feminine: Boolean, kind: DetKind): String? {
        val indefinite = kind == DetKind.INDEFINITE
        if (startsWithVowel(noun)) return if (indefinite && !feminine) "un" else null
        return when {
            feminine -> if (indefinite) "una" else "la"
            takesLo(noun) -> if (indefinite) "uno" else "lo"
            else -> if (indefinite) "un" else "il"
        }
    }

    private fun String.matchCase(like: String) = when {
        like.length > 1 && like.all { !it.isLetter() || it.isUpperCase() } -> uppercase()
        like.first().isUpperCase() -> replaceFirstChar { it.uppercase() }
        else -> this
    }
}
