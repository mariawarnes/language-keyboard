package io.github.mariawarnes.languagekeyboard

import android.content.Context
import android.content.SharedPreferences
import com.google.mlkit.nl.translate.TranslateLanguage

/** Small wrapper around the app's SharedPreferences, shared by the keyboard and the settings screen. */
object Prefs {
    private const val FILE = "language_keyboard"

    /** The language you are practising: what you type on the keyboard. */
    const val KEY_TYPING_LANGUAGE = "typing_language"

    /** Your friends' language: what actually gets inserted into the text field. */
    const val KEY_OUTPUT_LANGUAGE = "output_language"

    const val KEY_TRANSLATE_ENABLED = "translate_enabled"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun typingLanguage(context: Context): String =
        get(context).getString(KEY_TYPING_LANGUAGE, null) ?: TranslateLanguage.SPANISH

    fun outputLanguage(context: Context): String =
        get(context).getString(KEY_OUTPUT_LANGUAGE, null) ?: TranslateLanguage.ENGLISH

    fun translateEnabled(context: Context): Boolean =
        get(context).getBoolean(KEY_TRANSLATE_ENABLED, true)

    fun setLanguages(context: Context, typing: String, output: String) {
        get(context).edit()
            .putString(KEY_TYPING_LANGUAGE, typing)
            .putString(KEY_OUTPUT_LANGUAGE, output)
            .apply()
    }

    fun setTranslateEnabled(context: Context, enabled: Boolean) {
        get(context).edit().putBoolean(KEY_TRANSLATE_ENABLED, enabled).apply()
    }
}
