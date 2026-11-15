package io.github.mariawarnes.languagekeyboard

import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * The keyboard.
 *
 * You type in the language you're practising. That text is kept in [buffer] and shown in the
 * bar above the keys; the text field only ever receives the translation, which is written as
 * "composing text" (the underlined, still-editable region) and replaced live as you keep typing.
 * Pressing ✓ (or tapping out of the field, or the app sending the message) locks it in.
 */
class LanguageKeyboardService : InputMethodService(),
    KeyboardView.Listener,
    TranslationEngine.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var engine: TranslationEngine

    private var keyboardView: KeyboardView? = null
    private var languageChip: TextView? = null
    private var typedText: TextView? = null
    private var translatingIndicator: TextView? = null

    // --- What you typed and what's in the field ---------------------------------------------

    /** Text in the language you're practising, not yet locked in. */
    private val buffer = StringBuilder()

    /** Bumped whenever the current phrase is finished or abandoned, so late results are ignored. */
    private var session = 0

    /** Bumped on every change to [buffer]. */
    private var generation = 0

    /** The newest [generation] whose translation is currently in the field. */
    private var appliedGeneration = 0

    /** Text we've put into the field's composing region for this phrase. */
    private var composed = ""

    /** Whether this phrase starts with a space because it follows other text. */
    private var leadingSpace: Boolean? = null

    /** Lock the phrase in as soon as the translation of the latest text arrives. */
    private var commitPending = false

    // Where the editor's cursor is, so we can tell our own edits from the user moving the cursor.
    private var selStart = 0
    private var selEnd = 0
    private var composeStart = -1
    private val recentExpectedCursors = ArrayDeque<Int>()

    private var statusMessage: String? = null

    // --- Keyboard state ---------------------------------------------------------------------

    private var mode = KeyboardMode.LETTERS
    private var shiftState = ShiftState.OFF
    private var lastShiftTap = 0L

    /** User's on/off switch (the language chip). */
    private var translateEnabled = true

    /** False in password, email, URL, number fields and the like. */
    private var fieldAllowsTranslation = true

    private val translationActive: Boolean
        get() = translateEnabled && fieldAllowsTranslation &&
            Prefs.typingLanguage(this) != Prefs.outputLanguage(this)

    // --- Lifecycle --------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        engine = TranslationEngine(this)
        translateEnabled = Prefs.translateEnabled(this)
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this)
        configureEngine()
    }

    override fun onDestroy() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this)
        engine.close()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.input_view, null)
        keyboardView = root.findViewById<KeyboardView>(R.id.keyboard).also { it.listener = this }
        languageChip = root.findViewById<TextView>(R.id.language_chip).also { chip ->
            chip.setOnClickListener { toggleTranslation() }
        }
        typedText = root.findViewById(R.id.typed_text)
        translatingIndicator = root.findViewById(R.id.translating_indicator)
        root.findViewById<View>(R.id.clear_button).setOnClickListener { clearPhrase() }
        root.findViewById<View>(R.id.settings_button).setOnClickListener { openSettings() }
        refreshUi()
        return root
    }

    /** Keep the app's own text field visible in landscape instead of a full-screen editor. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        resetPhrase()
        selStart = attribute.initialSelStart
        selEnd = attribute.initialSelEnd

        val cls = attribute.inputType and InputType.TYPE_MASK_CLASS
        val variation = attribute.inputType and InputType.TYPE_MASK_VARIATION
        fieldAllowsTranslation = cls == InputType.TYPE_CLASS_TEXT && variation !in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER,
        )
        mode = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> KeyboardMode.SYMBOLS
            else -> KeyboardMode.LETTERS
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        configureEngine()
        if (shiftState != ShiftState.LOCKED) shiftState = ShiftState.OFF
        updateAutoShift()
        refreshUi()
    }

    override fun onFinishInput() {
        // The field keeps whatever translation it was showing.
        resetPhrase()
        super.onFinishInput()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        when (key) {
            Prefs.KEY_TYPING_LANGUAGE, Prefs.KEY_OUTPUT_LANGUAGE -> {
                lockInPhrase()
                configureEngine()
            }
            Prefs.KEY_TRANSLATE_ENABLED -> translateEnabled = Prefs.translateEnabled(this)
        }
        refreshUi()
    }

    private fun configureEngine() {
        val typing = Prefs.typingLanguage(this)
        val output = Prefs.outputLanguage(this)
        if (typing != output) engine.configure(typing, output)
    }

    // --- Keys -------------------------------------------------------------------------------

    override fun onText(text: String) {
        if (translationActive) {
            buffer.append(text)
            onBufferChanged()
        } else {
            currentInputConnection?.commitText(text, 1)
        }
        if (shiftState == ShiftState.ONE_SHOT) shiftState = ShiftState.OFF
        updateAutoShift()
    }

    override fun onKey(code: Int) {
        when (code) {
            Codes.SPACE -> onText(" ")
            Codes.DELETE -> onDelete()
            Codes.ENTER -> onEnter()
            Codes.SHIFT -> onShift()
            Codes.MODE_LETTERS -> setMode(KeyboardMode.LETTERS)
            Codes.MODE_SYMBOLS -> setMode(KeyboardMode.SYMBOLS)
            Codes.MODE_SYMBOLS_2 -> setMode(KeyboardMode.SYMBOLS_2)
        }
    }

    override fun onSpaceLongPress() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    private fun onDelete() {
        if (translationActive && buffer.isNotEmpty()) {
            val cut = buffer.offsetByCodePoints(buffer.length, -1)
            buffer.setLength(cut)
            onBufferChanged()
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        updateAutoShift()
    }

    private fun onEnter() {
        if (translationActive && buffer.isNotEmpty()) {
            requestLockIn()
            return
        }
        val info = currentInputEditorInfo ?: return
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED
        if (noAction) {
            sendKeyChar('\n')
        } else {
            currentInputConnection?.performEditorAction(action)
        }
        updateAutoShift()
    }

    private fun onShift() {
        val now = System.currentTimeMillis()
        shiftState = when (shiftState) {
            ShiftState.OFF -> ShiftState.ONE_SHOT
            ShiftState.ONE_SHOT -> if (now - lastShiftTap < DOUBLE_TAP_MS) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }
        lastShiftTap = now
        keyboardView?.shiftState = shiftState
    }

    private fun setMode(newMode: KeyboardMode) {
        mode = newMode
        keyboardView?.rows = KeyboardLayouts.forMode(mode)
    }

    /** Capitalise the first letter of a sentence when the app asks for it. */
    private fun updateAutoShift() {
        if (shiftState == ShiftState.LOCKED) {
            keyboardView?.shiftState = shiftState
            return
        }
        val info = currentInputEditorInfo
        val capsRequested = info != null && (info.inputType and (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)) != 0
        val cap = when {
            info == null || !capsRequested -> false
            translationActive && buffer.isNotEmpty() -> {
                val trimmed = buffer.trimEnd()
                buffer.last() == ' ' && trimmed.isNotEmpty() && trimmed.last() in ".?!"
            }
            else -> (currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0) != 0
        }
        shiftState = if (cap) ShiftState.ONE_SHOT else ShiftState.OFF
        keyboardView?.shiftState = shiftState
    }

    // --- Translation ------------------------------------------------------------------------

    private fun onBufferChanged() {
        generation++
        statusMessage = null
        if (buffer.isEmpty()) {
            engine.cancelPending()
            appliedGeneration = generation
            writeComposition("")
        } else {
            engine.translate(TranslationEngine.Request(session, generation, buffer.toString()), immediate = false)
        }
        refreshUi()
    }

    override fun onTranslated(request: TranslationEngine.Request, translated: String) {
        if (request.session != session || request.generation <= appliedGeneration) return
        appliedGeneration = request.generation
        writeComposition(translated)
        if (commitPending && appliedGeneration == generation) lockInPhrase()
        refreshUi()
    }

    override fun onTranslationFailed(request: TranslationEngine.Request, error: Exception) {
        if (request.session != session) return
        Log.w(TAG, "Translation failed", error)
        commitPending = false
        statusMessage = getString(R.string.strip_translate_failed)
        refreshUi()
    }

    override fun onEngineStateChanged(state: TranslationEngine.State) {
        if (state == TranslationEngine.State.READY && buffer.isNotEmpty() && appliedGeneration != generation) {
            engine.translate(TranslationEngine.Request(session, generation, buffer.toString()), immediate = true)
        }
        if (state == TranslationEngine.State.DOWNLOAD_FAILED) commitPending = false
        refreshUi()
    }

    /** Replace the composing region in the text field with [translation]. */
    private fun writeComposition(translation: String) {
        val ic = currentInputConnection ?: return
        var text = translation
        if (text.isNotEmpty()) {
            if (leadingSpace == null) {
                val before = ic.getTextBeforeCursor(1, 0)
                leadingSpace = selStart == selEnd && !before.isNullOrEmpty() && !before[0].isWhitespace()
            }
            if (leadingSpace == true) text = " $text"
        }
        if (text == composed) return

        if (composeStart < 0) composeStart = minOf(selStart, selEnd)
        ic.setComposingText(text, 1)
        composed = text
        rememberExpectedCursor(composeStart + text.length)
        if (text.isEmpty()) {
            composeStart = -1
            leadingSpace = null
        }
    }

    private fun rememberExpectedCursor(position: Int) {
        recentExpectedCursors.addLast(position)
        while (recentExpectedCursors.size > 4) recentExpectedCursors.removeFirst()
    }

    /** ✓ pressed: lock the phrase in once its translation is up to date. */
    private fun requestLockIn() {
        when (engine.state) {
            TranslationEngine.State.READY -> {
                if (appliedGeneration == generation && !engine.isBusy) {
                    lockInPhrase()
                } else {
                    commitPending = true
                    engine.translate(TranslationEngine.Request(session, generation, buffer.toString()), immediate = true)
                }
            }
            TranslationEngine.State.DOWNLOAD_FAILED -> {
                configureEngine() // try the download again
                commitPending = true
            }
            else -> commitPending = true // will lock in when the pack has downloaded
        }
        refreshUi()
    }

    /** Keep whatever translation is in the field and start a new phrase. */
    private fun lockInPhrase() {
        if (composed.isNotEmpty()) currentInputConnection?.finishComposingText()
        resetPhrase()
        updateAutoShift()
        refreshUi()
    }

    /** ✕ pressed: remove this phrase's translation from the field and forget what was typed. */
    private fun clearPhrase() {
        if (composed.isNotEmpty()) {
            currentInputConnection?.let {
                it.setComposingText("", 1)
                it.finishComposingText()
            }
        }
        resetPhrase()
        updateAutoShift()
        refreshUi()
    }

    private fun resetPhrase() {
        engine.cancelPending()
        session++
        buffer.setLength(0)
        generation = 0
        appliedGeneration = 0
        composed = ""
        composeStart = -1
        leadingSpace = null
        commitPending = false
        statusMessage = null
        recentExpectedCursors.clear()
    }

    private fun toggleTranslation() {
        if (!fieldAllowsTranslation) return
        lockInPhrase()
        translateEnabled = !translateEnabled
        Prefs.setTranslateEnabled(this, translateEnabled)
        refreshUi()
    }

    /**
     * The editor tells us whenever its cursor or composing region changes. If that wasn't caused
     * by us (you tapped elsewhere in the text, or the app sent the message and cleared the box),
     * the current phrase is finished.
     */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        if (buffer.isEmpty() && composed.isEmpty()) {
            updateAutoShift()
            return
        }

        val cursorCollapsed = newSelStart == newSelEnd
        if (candidatesStart >= 0 && cursorCollapsed && newSelStart == candidatesEnd) {
            composeStart = candidatesStart // our composition, cursor at its end: all good
            return
        }
        if (candidatesStart < 0 && cursorCollapsed && newSelStart in recentExpectedCursors) {
            return // a slightly late report of one of our own edits
        }

        // Something else changed the field.
        if (candidatesStart >= 0) currentInputConnection?.finishComposingText()
        resetPhrase()
        updateAutoShift()
        refreshUi()
    }

    // --- UI ---------------------------------------------------------------------------------

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun refreshUi() {
        val typing = Prefs.typingLanguage(this)
        val output = Prefs.outputLanguage(this)
        val active = translationActive

        keyboardView?.let { kv ->
            kv.rows = KeyboardLayouts.forMode(mode)
            kv.shiftState = shiftState
            kv.enterLabel = if (active && buffer.isNotEmpty()) "✓" else enterLabelFor(currentInputEditorInfo)
            kv.spaceLabel = if (active) "${Languages.displayName(typing)} → ${Languages.displayName(output)}"
            else Languages.displayName(typing)
        }

        languageChip?.let { chip ->
            chip.text = if (active) "${Languages.shortLabel(typing)} → ${Languages.shortLabel(output)}"
            else "${Languages.shortLabel(typing)} → ${Languages.shortLabel(output)}  off"
            chip.setBackgroundResource(if (active) R.drawable.strip_chip else R.drawable.strip_chip_off)
            chip.alpha = if (fieldAllowsTranslation) 1f else 0.5f
        }

        translatingIndicator?.visibility =
            if (active && buffer.isNotEmpty() && appliedGeneration != generation) View.VISIBLE else View.INVISIBLE

        typedText?.let { tv ->
            val secondary = ContextCompat.getColor(this, R.color.strip_text_secondary)
            val primary = ContextCompat.getColor(this, R.color.strip_text)
            val message: String? = when {
                !fieldAllowsTranslation -> getString(R.string.strip_no_translate_field)
                !active -> getString(R.string.strip_passthrough)
                engine.state == TranslationEngine.State.DOWNLOADING ->
                    getString(R.string.strip_downloading, Languages.displayName(typing), Languages.displayName(output))
                engine.state == TranslationEngine.State.DOWNLOAD_FAILED -> getString(R.string.strip_download_failed)
                else -> statusMessage
            }
            if (active && buffer.isNotEmpty()) {
                val text = SpannableStringBuilder(buffer).append("▏")
                text.setSpan(ForegroundColorSpan(secondary), text.length - 1, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (message != null) {
                    val start = text.length
                    text.append("  ").append(message)
                    text.setSpan(ForegroundColorSpan(secondary), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                tv.text = text
                tv.setTextColor(primary)
            } else {
                tv.text = message ?: getString(R.string.strip_hint, Languages.displayName(typing))
                tv.setTextColor(secondary)
            }
        }
    }

    private fun enterLabelFor(info: EditorInfo?): String {
        if (info == null || (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "⏎"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_GO -> "Go"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_NEXT -> "Next"
            EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
            EditorInfo.IME_ACTION_DONE -> "Done"
            else -> "⏎"
        }
    }

    private companion object {
        const val TAG = "LanguageKeyboard"
        const val DOUBLE_TAP_MS = 350L
    }
}
