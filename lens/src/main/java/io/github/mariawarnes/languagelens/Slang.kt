package io.github.mariawarnes.languagelens

import java.util.Locale

/**
 * Chat abbreviations and slang ("pls", "idk", "lol"…), so translations keep the same tone.
 *
 * 1. [expand]: before translating, abbreviations in the message's language become full words, which
 *    the translator understands ("pls send it" → "please send it").
 * 2. [shorten]: after translating, for each idea that was abbreviated in the original, the full
 *    phrase in the translation is swapped for the usual abbreviation in that language, if it has
 *    one ("por favor envíalo" → "porfa envíalo"). Nothing that wasn't abbreviated is touched.
 */
object Slang {

    /** One idea, as written in each language: chat abbreviations, then the full phrases. */
    private class Concept(val forms: Map<String, Forms>)

    private class Forms(val short: List<String>, val full: List<String>)

    /** "pls,plz|please" → abbreviations | full forms (the first full form is used for expanding). */
    private fun concept(vararg byLanguage: Pair<String, String>) = Concept(
        byLanguage.associate { (lang, spec) ->
            val (short, full) = spec.split('|').let { it[0] to it.getOrElse(1) { "" } }
            lang to Forms(split(short), split(full))
        },
    )

    private fun split(s: String) = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private val CONCEPTS: List<Concept> = listOf(
        concept(
            "en" to "pls,plz,plox,pleez|please", "es" to "porfa,xfa|por favor", "fr" to "stp,svp|s'il te plaît,s'il vous plaît",
            "it" to "xfavore|per favore", "pt" to "pfv|por favor", "de" to "|bitte", "nl" to "aub|alsjeblieft,alstublieft",
        ),
        concept(
            "en" to "thx,thnx,tnx,ty|thanks,thank you", "es" to "grax|gracias", "fr" to "mrc|merci", "it" to "grz|grazie",
            "pt" to "obg,vlw|obrigado,obrigada,valeu", "de" to "dnk|danke", "nl" to "thx|bedankt,dank je",
        ),
        concept("en" to "tysm|thank you so much", "es" to "|muchas gracias", "fr" to "|merci beaucoup", "it" to "|grazie mille", "pt" to "|muito obrigado,muito obrigada", "de" to "|vielen Dank"),
        concept(
            "en" to "lol,lmao,lmfao,rofl,haha,hahaha|haha", "es" to "jajaja|jajaja,jaja,ja ja,haha", "fr" to "mdr|haha,mdr",
            "it" to "ahah|haha,ahah", "pt" to "kkkk|kkkk,haha,rsrs", "de" to "haha|haha", "nl" to "haha|haha",
        ),
        concept(
            "en" to "idk,dunno|I don't know", "es" to "nse|no sé", "fr" to "jsp|je sais pas,je ne sais pas",
            "it" to "nn lo so|non lo so", "pt" to "n sei|não sei", "de" to "kA|keine Ahnung,ich weiß nicht", "nl" to "|ik weet het niet,weet ik niet",
        ),
        concept(
            "en" to "ily,ilu|I love you", "es" to "tq|te quiero,te amo", "fr" to "jtm|je t'aime", "it" to "tvb|ti voglio bene,ti amo",
            "pt" to "|te amo,eu te amo", "de" to "ild|ich liebe dich", "nl" to "hvj|ik hou van je",
        ),
        concept("en" to "lysm,ilysm|I love you so much", "es" to "tqm|te quiero mucho", "it" to "tvtb|ti voglio tanto bene", "pt" to "|te amo muito"),
        concept(
            "en" to "btw|by the way", "es" to "x cierto|por cierto", "fr" to "|au fait", "it" to "cmq|comunque,a proposito",
            "pt" to "|aliás,por falar nisso", "de" to "btw|übrigens",
        ),
        concept(
            "en" to "bc,bcuz,cuz,coz,cos|because", "es" to "xq|porque", "fr" to "pcq|parce que", "it" to "xké|perché",
            "pt" to "pq|porque", "de" to "|weil", "nl" to "|omdat",
        ),
        concept("en" to "wat,wut|what", "es" to "|qué", "fr" to "koi|quoi", "it" to "ke|che cosa,cosa", "pt" to "oq|o que"),
        concept("en" to "u,ya|you", "pt" to "vc|você", "es" to "|tú"),
        concept("en" to "ur|your", "pt" to "|seu,sua"),
        concept("en" to "r|are"),
        concept(
            "en" to "tmrw,tmr,tmw,2moro,2morrow|tomorrow", "es" to "mñn|mañana", "fr" to "dm1|demain", "it" to "dmn|domani",
            "pt" to "amnh|amanhã", "de" to "|morgen",
        ),
        concept("en" to "2day|today", "es" to "|hoy", "fr" to "ajd|aujourd'hui", "it" to "|oggi", "pt" to "hj|hoje"),
        concept("en" to "2nite,tonite|tonight", "es" to "|esta noche", "pt" to "|hoje à noite"),
        concept(
            "en" to "msg|message", "es" to "msj|mensaje", "fr" to "msg|message", "it" to "msg|messaggio", "pt" to "msg|mensagem",
            "de" to "|Nachricht",
        ),
        concept(
            "en" to "wknd,wkend|weekend", "es" to "finde|fin de semana", "fr" to "we|week-end", "it" to "|weekend,fine settimana",
            "pt" to "fds|fim de semana", "de" to "WE|Wochenende",
        ),
        concept(
            "en" to "np,nps|no problem", "es" to "np|no hay problema,no pasa nada", "fr" to "|pas de problème,pas de souci",
            "it" to "np|nessun problema", "pt" to "|sem problema", "de" to "kp|kein Problem",
        ),
        concept(
            "en" to "k,kk,okk|okay", "es" to "ok|de acuerdo,está bien,vale", "fr" to "ok|d'accord", "it" to "ok|va bene,d'accordo",
            "pt" to "blz|beleza,tudo bem", "de" to "ok|in Ordnung", "nl" to "oké|goed",
        ),
        concept(
            "en" to "sry,soz|sorry", "es" to "|lo siento,perdón", "fr" to "dsl|désolé,désolée", "it" to "|scusa,mi dispiace",
            "pt" to "|desculpa,desculpe", "de" to "sry|Entschuldigung",
        ),
        concept(
            "en" to "omg|oh my god", "es" to "omg|oh dios mío,dios mío", "fr" to "omg|oh mon dieu", "it" to "omg|oh mio dio,oddio",
            "pt" to "omg|ai meu deus,meu deus", "de" to "omg|oh mein Gott",
        ),
        concept("en" to "brb|be right back", "es" to "|ahora vuelvo,ya vuelvo,vuelvo enseguida", "fr" to "jreviens|je reviens", "pt" to "|já volto"),
        concept("en" to "gtg,g2g|got to go", "es" to "|me tengo que ir,tengo que irme", "fr" to "|je dois y aller"),
        concept("en" to "ttyl|talk to you later", "es" to "|hablamos luego,hablamos más tarde", "fr" to "a+|à plus tard,à plus", "it" to "|ci sentiamo dopo"),
        concept("en" to "cya,cu|see you", "es" to "|nos vemos,hasta luego", "fr" to "a+|à plus", "pt" to "|até mais"),
        concept("en" to "l8r,ltr|later", "es" to "|luego,más tarde"),
        concept("en" to "tbh|to be honest", "es" to "|para ser honesto,la verdad"),
        concept("en" to "imo,imho|in my opinion", "es" to "|en mi opinión", "fr" to "|à mon avis"),
        concept("en" to "asap|as soon as possible", "es" to "|lo antes posible,cuanto antes", "fr" to "asap|dès que possible", "it" to "asap|il prima possibile"),
        concept("en" to "rn|right now", "es" to "|ahora mismo,ahorita"),
        concept("en" to "nvm|never mind", "es" to "|no importa,olvídalo"),
        concept("en" to "ofc|of course", "es" to "|por supuesto,claro", "fr" to "|bien sûr", "pt" to "clr|claro"),
        concept("en" to "w/|with", "es" to "c/|con", "pt" to "c/|com"),
        concept("en" to "w/o|without", "es" to "s/|sin", "pt" to "s/|sem"),
        concept(
            "en" to "xx,xxx,xoxo|kisses", "es" to "bss|besos,besitos", "fr" to "bsx|bisous", "it" to "|baci,bacioni",
            "pt" to "bjs|beijos", "de" to "|Küsse",
        ),
        concept("en" to "omw|on my way", "es" to "|voy en camino,de camino"),
        concept("en" to "wyd|what are you doing", "es" to "q haces|qué haces,qué estás haciendo", "fr" to "tfq|tu fais quoi"),
        concept("en" to "hbu,wbu|what about you", "es" to "|y tú"),
        concept("en" to "bday,b-day|birthday", "es" to "cumple|cumpleaños", "fr" to "anniv|anniversaire", "pt" to "niver|aniversário"),
        concept("en" to "jk,jks|just kidding", "es" to "|es broma,era broma", "fr" to "|je rigole"),
        concept("en" to "fyi|for your information", "es" to "|para tu información"),
        concept("en" to "gn,gnite|good night", "es" to "|buenas noches", "pt" to "|boa noite"),
        concept("en" to "ppl|people", "es" to "|gente"),
        concept("en" to "b4|before", "es" to "|antes"),
        concept("en" to "gr8|great"),
        concept("en" to "tho|though"),
        concept("en" to "abt|about"),
        concept("en" to "prob,prolly,probs|probably", "es" to "|probablemente"),
        concept("en" to "sth,smth|something", "es" to "|algo"),
        concept("en" to "rly|really", "es" to "|de verdad,realmente"),
        concept("en" to "sup,wassup|what's up", "es" to "|qué tal,qué pasa"),
        concept("en" to "wanna|want to"),
        concept("en" to "gonna|going to"),
        concept("en" to "gotta|got to"),
        concept("en" to "lemme|let me"),
        concept("en" to "gimme|give me"),
        // Abbreviations in the other languages, for when they're the one being translated from.
        concept("es" to "tmb,tb|también", "en" to "|also,too", "pt" to "tb|também"),
        concept("es" to "salu2|saludos", "en" to "|greetings"),
        concept("es" to "q,k|que", "en" to "|that"),
        concept("es" to "xfa|por favor", "en" to "pls|please"),
        concept("es" to "ntp|no te preocupes", "en" to "|don't worry"),
        concept("fr" to "bcp|beaucoup", "en" to "|a lot,lots"),
        concept("fr" to "c|c'est", "en" to "|it's"),
        concept("fr" to "tkt|t'inquiète", "en" to "|don't worry"),
        concept("pt" to "td|tudo", "en" to "|everything"),
        concept("pt" to "n|não", "en" to "|no,not"),
        concept("it" to "nn|non", "en" to "|not,no"),
        concept("de" to "vllt|vielleicht", "en" to "|maybe,perhaps"),
        concept("de" to "lg|liebe Grüße", "en" to "|best wishes"),
    )

    class Expanded(val text: String, val concepts: Set<Int>)

    private fun boundary(phrase: String, ignoreCase: Boolean) = Regex(
        "(?<![\\p{L}\\p{N}])" + Regex.escape(phrase) + "(?![\\p{L}\\p{N}])",
        if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet(),
    )

    /** Abbreviations in [text] (written in [language]) replaced by full words, plus which ideas they were. */
    fun expand(text: String, language: String): Expanded {
        var out = text
        val used = HashSet<Int>()
        CONCEPTS.forEachIndexed { index, concept ->
            val forms = concept.forms[language] ?: return@forEachIndexed
            val full = forms.full.firstOrNull() ?: return@forEachIndexed
            for (short in forms.short.sortedByDescending { it.length }) {
                // Single letters ("u", "r", "k", "n", "c", "q") only count in lower case, on their own.
                val regex = boundary(short, ignoreCase = short.length > 1)
                if (regex.containsMatchIn(out)) {
                    out = regex.replace(out) { m -> matchCase(m.value, full) }
                    used += index
                }
            }
        }
        return Expanded(out, used)
    }

    /** For each idea in [concepts], its full phrase in [text] (written in [language]) → that language's abbreviation. */
    fun shorten(text: String, concepts: Set<Int>, language: String): String {
        var out = text
        for (index in concepts) {
            val forms = CONCEPTS[index].forms[language] ?: continue
            val short = forms.short.firstOrNull() ?: continue
            for (full in forms.full.sortedByDescending { it.length }) {
                out = boundary(full, ignoreCase = true).replace(out) { m ->
                    // Longer abbreviations ("Porfa") keep a capital; short ones ("nse") stay lower case.
                    if (m.value.first().isUpperCase() && short.length >= 4 && short.first().isLowerCase()) {
                        short.replaceFirstChar { it.titlecase(Locale.ROOT) }
                    } else {
                        short
                    }
                }
            }
        }
        return out
    }

    /** "Pls" → "Please", "PLS" → "PLEASE", "pls" → "please". */
    private fun matchCase(original: String, replacement: String): String = when {
        original.length > 1 && original.all { !it.isLetter() || it.isUpperCase() } -> replacement.uppercase(Locale.ROOT)
        original.first().isUpperCase() -> replacement.replaceFirstChar { it.titlecase(Locale.ROOT) }
        else -> replacement
    }
}
