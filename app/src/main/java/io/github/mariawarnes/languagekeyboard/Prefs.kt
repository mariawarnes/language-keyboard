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
    const val KEY_GESTURE_TYPING = "gesture_typing"
    const val KEY_SUGGESTIONS = "show_suggestions"
    const val KEY_AUTOCORRECT = "autocorrect"
    const val KEY_MARK_MISTAKES = "mark_mistakes"
    const val KEY_GRAMMAR_CHECK = "grammar_check"
    private const val KEY_USER_WORDS = "user_words"

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

    fun gestureTyping(context: Context): Boolean = get(context).getBoolean(KEY_GESTURE_TYPING, true)
    fun suggestions(context: Context): Boolean = get(context).getBoolean(KEY_SUGGESTIONS, true)
    fun autocorrect(context: Context): Boolean = get(context).getBoolean(KEY_AUTOCORRECT, true)
    fun markMistakes(context: Context): Boolean = get(context).getBoolean(KEY_MARK_MISTAKES, true)

    /** Off by default: it sends the practice text to LanguageTool's servers. */
    fun grammarCheck(context: Context): Boolean = get(context).getBoolean(KEY_GRAMMAR_CHECK, false)

    fun setFlag(context: Context, key: String, value: Boolean) {
        get(context).edit().putBoolean(key, value).apply()
    }

    /** Words you've chosen to keep as typed, so they're never autocorrected again. */
    fun userWords(context: Context, language: String): Set<String> {
        val prefix = "$language|"
        return get(context).getStringSet(KEY_USER_WORDS, emptySet()).orEmpty()
            .filter { it.startsWith(prefix) }
            .mapTo(HashSet()) { it.removePrefix(prefix) }
    }

    fun addUserWord(context: Context, language: String, word: String) {
        val all = HashSet(get(context).getStringSet(KEY_USER_WORDS, emptySet()).orEmpty())
        if (all.add("$language|$word")) get(context).edit().putStringSet(KEY_USER_WORDS, all).apply()
    }
}
