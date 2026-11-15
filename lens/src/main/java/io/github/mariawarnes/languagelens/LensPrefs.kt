package io.github.mariawarnes.languagelens

import android.content.Context
import android.content.SharedPreferences

object LensPrefs {
    private const val FILE = "language_lens"
    const val KEY_LEARNING = "learning_language"
    const val KEY_NATIVE = "native_language"
    const val KEY_PAUSED = "paused"
    const val KEY_WHATSAPP = "app_whatsapp"
    const val KEY_MESSENGER = "app_messenger"

    val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
    val MESSENGER = setOf("com.facebook.orca", "com.facebook.mlite")

    fun get(context: Context): SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun learning(context: Context) = get(context).getString(KEY_LEARNING, null) ?: "es"
    fun native(context: Context) = get(context).getString(KEY_NATIVE, null) ?: "en"
    fun paused(context: Context) = get(context).getBoolean(KEY_PAUSED, false)

    fun setPaused(context: Context, paused: Boolean) = get(context).edit().putBoolean(KEY_PAUSED, paused).apply()

    /** The apps Language Lens works in right now. */
    fun packages(context: Context): Set<String> {
        val prefs = get(context)
        return buildSet {
            if (prefs.getBoolean(KEY_WHATSAPP, true)) addAll(WHATSAPP)
            if (prefs.getBoolean(KEY_MESSENGER, true)) addAll(MESSENGER)
        }
    }
}
