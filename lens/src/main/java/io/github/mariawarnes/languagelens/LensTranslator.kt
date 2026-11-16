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
        // Low threshold: we want every guess with its confidence, and decide ourselves (see lookup).
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.01f).build(),
    )
    private var translator: Translator? = null
    private var native = ""
    private var learning = ""
    private var ready = false

    /** Put abbreviations back in the learning language's own shorthand ("por favor" → "porfa"). */
    var shorthand = true
        set(value) {
            if (field != value) {
                field = value
                cache.evictAll() // redo translations with or without shorthand
                pending.clear()
            }
        }

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
        identifier.identifyPossibleLanguages(text)
            .addOnSuccessListener { guesses ->
                // Tags like "en", "es", "zh-Latn"; "und" when it can't tell.
                fun confidence(code: String) =
                    guesses.filter { it.languageTag.substringBefore('-') == code }.maxOfOrNull { it.confidence } ?: 0f
                val mine = confidence(native)
                val top = guesses.maxByOrNull { it.confidence }
                val topCode = top?.languageTag?.substringBefore('-')
                val clearlyOther = top != null && topCode != "und" && topCode != native &&
                    top.confidence >= OTHER_SURE && mine < MINE_POSSIBLE
                if (clearlyOther) {
                    // Already in the language you're learning, or a third language: leave it.
                    cache.put(text, SKIP)
                    pending -= text
                    return@addOnSuccessListener
                }
                // In your language, or too short to tell ("Super busy"): translate, and if nothing
                // changes it was already in the language you're learning. Chat abbreviations are
                // spelt out first ("pls" → "please") and put back in the other language's own
                // shorthand after ("por favor" → "porfa"), so the tone stays the same.
                val expanded = Slang.expand(text, native)
                client.translate(expanded.text)
                    .addOnSuccessListener { translated ->
                        pending -= text
                        if (translator !== client) return@addOnSuccessListener
                        val result = if (shorthand) Slang.shorten(translated, expanded.concepts, learning) else translated
                        cache.put(text, if (same(translated, expanded.text) || same(result, text)) SKIP else result)
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

    private fun same(a: String, b: String): Boolean {
        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        return norm(a) == norm(b)
    }

    private companion object {
        val SKIP = String(charArrayOf('\u0000'))

        /** Skip a message only when detection is at least this sure it's another language… */
        const val OTHER_SURE = 0.5f

        /** …and gives your language less than this. */
        const val MINE_POSSIBLE = 0.2f
    }
}
