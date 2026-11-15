package io.github.mariawarnes.languagekeyboard

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/** The languages ML Kit can translate between, with human-readable names. */
object Languages {

    /** The name of language [code], written in language [inLanguage] (e.g. "es" in "en" -> "Spanish"). */
    fun displayName(code: String, inLanguage: String): String {
        val locale = Locale.forLanguageTag(inLanguage)
        val name = Locale.forLanguageTag(code).getDisplayName(locale)
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    /** "Type in <language>…", written in that language, for the empty practice bar. */
    private val TYPE_IN = mapOf(
        "en" to "Type in English…",
        "es" to "Escribe en español…",
        "fr" to "Écris en français…",
        "de" to "Schreib auf Deutsch…",
        "it" to "Scrivi in italiano…",
        "pt" to "Escreva em português…",
        "nl" to "Typ in het Nederlands…",
        "ca" to "Escriu en català…",
        "gl" to "Escribe en galego…",
        "pl" to "Pisz po polsku…",
        "ro" to "Scrie în română…",
        "sv" to "Skriv på svenska…",
        "da" to "Skriv på dansk…",
        "no" to "Skriv på norsk…",
        "fi" to "Kirjoita suomeksi…",
        "is" to "Skrifaðu á íslensku…",
        "et" to "Kirjuta eesti keeles…",
        "lt" to "Rašyk lietuviškai…",
        "lv" to "Raksti latviski…",
        "cs" to "Piš česky…",
        "sk" to "Píš po slovensky…",
        "hu" to "Írj magyarul…",
        "hr" to "Piši na hrvatskom…",
        "sl" to "Piši v slovenščini…",
        "sq" to "Shkruaj në shqip…",
        "tr" to "Türkçe yaz…",
        "ru" to "Пиши по-русски…",
        "uk" to "Пиши українською…",
        "be" to "Пішы па-беларуску…",
        "bg" to "Пиши на български…",
        "mk" to "Пиши на македонски…",
        "el" to "Γράψε στα ελληνικά…",
        "he" to "כתבו בעברית…",
        "ar" to "اكتب بالعربية…",
        "fa" to "به فارسی بنویس…",
        "ur" to "اردو میں لکھیں…",
        "ka" to "დაწერე ქართულად…",
        "vi" to "Nhập bằng tiếng Việt…",
        "id" to "Ketik dalam bahasa Indonesia…",
        "ms" to "Taip dalam bahasa Melayu…",
        "sw" to "Andika kwa Kiswahili…",
        "tl" to "Mag-type sa Tagalog…",
        "af" to "Tik in Afrikaans…",
        "ga" to "Scríobh i nGaeilge…",
        "cy" to "Teipia yn Gymraeg…",
        "ht" to "Ekri an kreyòl…",
        "eo" to "Tajpu en Esperanto…",
        "mt" to "Ikteb bil-Malti…",
        "ja" to "日本語で入力…",
        "zh" to "用中文输入…",
        "ko" to "한국어로 입력…",
        "hi" to "हिंदी में लिखें…",
        "bn" to "বাংলায় লিখুন…",
        "mr" to "मराठीत लिहा…",
        "gu" to "ગુજરાતીમાં લખો…",
        "kn" to "ಕನ್ನಡದಲ್ಲಿ ಬರೆಯಿರಿ…",
        "ta" to "தமிழில் எழுதுங்கள்…",
        "te" to "తెలుగులో రాయండి…",
        "th" to "พิมพ์เป็นภาษาไทย…",
    )

    fun typeInHint(code: String): String = TYPE_IN[code] ?: "Type in ${displayName(code, "en")}…"

    /** Short label for the keyboard bar, e.g. "ES". */
    fun shortLabel(code: String): String = code.uppercase(Locale.ROOT)

    /** Every language ML Kit translates, sorted by name in language [inLanguage]. */
    fun all(inLanguage: String): List<String> =
        TranslateLanguage.getAllLanguages().sortedBy { displayName(it, inLanguage) }
}
