package io.github.mariawarnes.languagekeyboard

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/** The languages ML Kit can translate between, with human-readable names. */
object Languages {

    fun displayName(code: String): String {
        val name = Locale.forLanguageTag(code).getDisplayName(Locale.getDefault())
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    /** Short label for the keyboard bar, e.g. "ES". */
    fun shortLabel(code: String): String = code.uppercase(Locale.ROOT)

    val all: List<String> by lazy {
        TranslateLanguage.getAllLanguages().sortedBy { displayName(it) }
    }
}
