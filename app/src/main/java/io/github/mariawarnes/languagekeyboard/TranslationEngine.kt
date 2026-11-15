package io.github.mariawarnes.languagekeyboard

import android.os.Handler
import android.os.Looper
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Wraps an ML Kit [Translator] for one language pair.
 *
 * - Downloads the language pack the first time it is needed.
 * - Debounces requests while you type, so we don't translate on every single key press.
 * - Each request carries a [Request] tag so the caller can ignore results that arrive too late.
 *
 * All callbacks are delivered on the main thread.
 */
class TranslationEngine(private val listener: Listener) {

    enum class State { IDLE, PREPARING, DOWNLOADING, READY, DOWNLOAD_FAILED }

    data class Request(val session: Int, val generation: Int, val text: String)

    interface Listener {
        fun onTranslated(request: Request, translated: String)
        fun onTranslationFailed(request: Request, error: Exception)
        fun onEngineStateChanged(state: State)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var translator: Translator? = null
    private var waiting: Request? = null // latest request received while the model wasn't ready yet
    private var debounced: Runnable? = null
    private var inFlight = 0

    var sourceLanguage: String? = null
        private set
    var targetLanguage: String? = null
        private set

    var state: State = State.IDLE
        private set(value) {
            if (field != value) {
                field = value
                listener.onEngineStateChanged(value)
            }
        }

    val isBusy: Boolean get() = inFlight > 0 || debounced != null || waiting != null

    private val showDownloading = Runnable { if (state == State.PREPARING) state = State.DOWNLOADING }

    /** Switch to a language pair. Does nothing if that pair is already configured. */
    fun configure(source: String, target: String) {
        if (source == sourceLanguage && target == targetLanguage && translator != null &&
            state != State.DOWNLOAD_FAILED
        ) return
        close()
        sourceLanguage = source
        targetLanguage = target
        if (source == target) return

        val client = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )
        translator = client
        state = State.PREPARING
        // If the pack is already on the phone this finishes almost instantly; only show
        // "downloading" if it is actually taking a while.
        handler.postDelayed(showDownloading, 600)
        client.downloadModelIfNeeded()
            .addOnSuccessListener {
                if (translator !== client) return@addOnSuccessListener
                handler.removeCallbacks(showDownloading)
                state = State.READY
                waiting?.let { waiting = null; run(it) }
            }
            .addOnFailureListener {
                if (translator !== client) return@addOnFailureListener
                handler.removeCallbacks(showDownloading)
                state = State.DOWNLOAD_FAILED
                waiting = null
            }
    }

    /** Ask for a translation. With [immediate] = false, waits for a short pause in typing first. */
    fun translate(request: Request, immediate: Boolean) {
        debounced?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            debounced = null
            run(request)
        }
        debounced = task
        handler.postDelayed(task, if (immediate) 0L else DEBOUNCE_MS)
    }

    fun cancelPending() {
        debounced?.let { handler.removeCallbacks(it) }
        debounced = null
        waiting = null
    }

    private fun run(request: Request) {
        val client = translator ?: return
        if (state != State.READY) {
            waiting = request
            return
        }
        inFlight++
        client.translate(request.text)
            .addOnSuccessListener { result ->
                inFlight = maxOf(0, inFlight - 1)
                if (translator === client) listener.onTranslated(request, result)
            }
            .addOnFailureListener { e ->
                inFlight = maxOf(0, inFlight - 1)
                if (translator === client) listener.onTranslationFailed(request, e)
            }
    }

    fun close() {
        cancelPending()
        handler.removeCallbacks(showDownloading)
        translator?.close()
        translator = null
        inFlight = 0
        sourceLanguage = null
        targetLanguage = null
        state = State.IDLE
    }

    private companion object {
        const val DEBOUNCE_MS = 120L
    }
}
