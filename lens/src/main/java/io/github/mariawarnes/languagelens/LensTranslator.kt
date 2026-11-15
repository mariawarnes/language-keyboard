package io.github.mariawarnes.languagelens

import android.util.LruCache
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Detects which messages are in your language and translates them into the one you're learning,
 * all on the phone with ML Kit. Results are kept in memory only (never written anywhere).
 * Callbacks arrive on the main thread.
 */
class LensTranslator(private val onUpdated: () -> Unit) {

    private val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.5f).build(),
    )
    private var translator: Translator? = null
    private var native = ""
    private var learning = ""
    private var ready = false

    /** original → translation, or SKIP when it isn't in your language. */
    private val cache = LruCache<String, String>(600)
    private val pending = HashSet<String>()

    fun configure(nativeLanguage: String, learningLanguage: String) {
        if (nativeLanguage == native && learningLanguage == learning && translator != null) return
        translator?.close()
        native = nativeLanguage
        learning = learningLanguage
        cache.evictAll()
        pending.clear()
        ready = false
        if (native == learning) {
            translator = null
            return
        }
        val client = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(native).setTargetLanguage(learning).build(),
        )
        translator = client
        client.downloadModelIfNeeded().addOnSuccessListener {
            if (translator === client) {
                ready = true
                onUpdated()
            }
        }
    }

    /** The translation of [text] if it's ready; otherwise starts working on it and returns null. */
    fun lookup(text: String): String? {
        cache.get(text)?.let { return if (it === SKIP) null else it }
        if (!ready || text in pending) return null
        pending += text
        val client = translator ?: return null
        identifier.identifyLanguage(text)
            .addOnSuccessListener { language ->
                // "en", "es", "zh-Latn", … ; "und" when it can't tell.
                if (language.substringBefore('-') != native) {
                    cache.put(text, SKIP)
                    pending -= text
                    return@addOnSuccessListener
                }
                client.translate(text)
                    .addOnSuccessListener { translated ->
                        pending -= text
                        if (translator !== client) return@addOnSuccessListener
                        cache.put(text, translated)
                        onUpdated()
                    }
                    .addOnFailureListener { pending -= text }
            }
            .addOnFailureListener { pending -= text }
        return null
    }

    fun close() {
        translator?.close()
        translator = null
        identifier.close()
    }

    private companion object {
        val SKIP = String(charArrayOf('\u0000'))
    }
}
