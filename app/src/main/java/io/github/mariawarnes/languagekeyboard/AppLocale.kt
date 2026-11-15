package io.github.mariawarnes.languagekeyboard

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The language of the app's own text (settings and keyboard messages), chosen in settings.
 * "Automatic" follows "My language" when the app has a translation for it, else English.
 */
object AppLocale {

    /** Languages the app's text is translated into (res/values-xx). */
    val AVAILABLE = listOf("en", "es", "fr", "de", "it", "pt")

    /** The language the app's text is shown in right now. */
    fun language(context: Context): String {
        val chosen = Prefs.appLanguage(context)
        if (chosen in AVAILABLE) return chosen
        return automatic(context)
    }

    /** What "Same as my language" resolves to. */
    fun automatic(context: Context): String {
        val mine = Prefs.outputLanguage(context)
        return if (mine in AVAILABLE) mine else "en"
    }

    /** [context] with its resources switched to the app language. */
    fun wrap(context: Context): Context {
        val locale = Locale.forLanguageTag(language(context))
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
