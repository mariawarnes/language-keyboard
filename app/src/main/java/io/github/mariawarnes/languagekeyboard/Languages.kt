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

    /** Short label for the keyboard bar, e.g. "ES". */
    fun shortLabel(code: String): String = code.uppercase(Locale.ROOT)

    /** Every language ML Kit translates, sorted by name in language [inLanguage]. */
    fun all(inLanguage: String): List<String> =
        TranslateLanguage.getAllLanguages().sortedBy { displayName(it, inLanguage) }
}
