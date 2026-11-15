package io.github.mariawarnes.languagekeyboard

import android.content.Intent
import android.content.SharedPreferences
import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

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
    private var typedText: PracticeTextView? = null
    private var translatingIndicator: TextView? = null

    // --- What you typed and what's in the field ---------------------------------------------

    /** Text in the language you're practising, not yet locked in. */
    private val buffer = StringBuilder()

    /** Where in [buffer] typing goes (slide on the space bar, or tap the practice bar, to move it). */
    private var cursor = 0

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

    // --- Suggestions, autocorrect and gesture typing ----------------------------------------

    private var suggestionBar: View? = null
    private val suggestionViews = arrayOfNulls<TextView>(3)

    private var suggestionsEnabled = true
    private var autocorrectEnabled = true
    private var gestureEnabled = true

    /** Word list for [layoutLanguage], once loaded. */
    private var dictionary: Dictionary? = null
    private var requestedDictionary: String? = null
    private var userWords: Set<String> = emptySet()

    /** Latest suggestions, for the word they were computed for. */
    private var lastResult: SuggestionResult? = null
    private var suggestionRequest = 0
    private var gestureRequest = 0

    /** After a swipe: the word it typed and the other guesses (first = the one typed). */
    private var gestureWords: List<String>? = null

    /** Lets backspace right after an autocorrection put back what you typed. */
    private class AutocorrectUndo(val original: String, val corrected: String, val separator: String)
    private var undoAutocorrect: AutocorrectUndo? = null

    /** A space was added after a picked suggestion (punctuation typed next replaces it). */
    private var autoSpaced = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private enum class SlotKind { VERBATIM, WORD, GESTURE, INFO }
    private class Slot(val text: String, val kind: SlotKind, val emphasised: Boolean = false)

    // --- Checking the practice text ---------------------------------------------------------

    private enum class MarkKind { SPELLING, ACCENT, GRAMMAR }

    /** A possible mistake in [buffer] between [start] and [end] (which held [original]). */
    private class Mark(
        val start: Int, val end: Int, val original: String,
        val kind: MarkKind, val message: String?, val replacements: List<String>,
    ) {
        fun sameAs(other: Mark) = start == other.start && end == other.end && original == other.original
        fun shiftedBy(delta: Int) = Mark(start + delta, end + delta, original, kind, message, replacements)
    }

    private var practiceScroll: HorizontalScrollView? = null
    private var correctionPanel: View? = null
    private var correctionMessage: TextView? = null
    private val correctionFixes = arrayOfNulls<TextView>(2)

    private var markMistakes = true
    private var grammarCheck = false
    private var wordMarks: List<Mark> = emptyList()
    private var grammarMarks: List<Mark> = emptyList()
    private var selectedMark: Mark? = null
    private var markRequest = 0
    private var grammarRequest = 0
    private var lastGrammarText = ""
    private var warnedBeforeLockIn = false
    private val grammarExecutor = Executors.newSingleThreadExecutor()
    private val grammarRunnable = Runnable { runGrammarCheck() }

    // Only touched on Dictionaries.worker.
    private val markCache = HashMap<String, SuggestionResult>()
    private var markCacheDictionary: Dictionary? = null

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

    /**
     * Whose keyboard layout to show: the language you're practising while translating, your
     * friends' language when translation is off, and plain QWERTY for emails, URLs and passwords.
     */
    private val layoutLanguage: String
        get() = when {
            !fieldAllowsTranslation -> "en"
            translationActive -> Prefs.typingLanguage(this)
            else -> Prefs.outputLanguage(this)
        }

    // --- Lifecycle --------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        engine = TranslationEngine(this)
        translateEnabled = Prefs.translateEnabled(this)
        readTypingOptions()
        Prefs.get(this).registerOnSharedPreferenceChangeListener(this)
        configureEngine()
    }

    override fun onDestroy() {
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(this)
        engine.close()
        grammarExecutor.shutdown()
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
        suggestionBar = root.findViewById(R.id.suggestions)
        practiceScroll = root.findViewById(R.id.practice_scroll)
        correctionPanel = root.findViewById(R.id.correction_panel)
        correctionMessage = root.findViewById(R.id.correction_message)
        correctionFixes[0] = root.findViewById(R.id.correction_fix_1)
        correctionFixes[1] = root.findViewById(R.id.correction_fix_2)
        root.findViewById<View>(R.id.correction_close).setOnClickListener {
            selectedMark = null
            refreshUi()
        }
        typedText?.setOnTouchListener(::onPracticeTouch)
        suggestionViews[0] = root.findViewById(R.id.suggestion_left)
        suggestionViews[1] = root.findViewById(R.id.suggestion_center)
        suggestionViews[2] = root.findViewById(R.id.suggestion_right)

        // Android 15+ draws keyboards edge-to-edge: keep the keys clear of the gesture bar /
        // navigation buttons (the keyboard's background still fills the space behind them).
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout())
            // With gesture navigation the reported bar is short (~24dp), but the keyboard's own
            // ⌄ / 🌐 buttons sit there too, so leave at least 48dp for them.
            val minimum = (48 * resources.displayMetrics.density).toInt()
            val bottom = if (bars.bottom > 0) maxOf(bars.bottom, minimum) else 0
            v.setPadding(bars.left, 0, bars.right, bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

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
        readTypingOptions()
        requestedDictionary = null // retry a failed word-list download each time the keyboard opens
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
                explanationTranslations.clear()
                lockInPhrase()
                configureEngine()
            }
            Prefs.KEY_TRANSLATE_ENABLED -> translateEnabled = Prefs.translateEnabled(this)
            Prefs.KEY_GESTURE_TYPING, Prefs.KEY_SUGGESTIONS, Prefs.KEY_AUTOCORRECT -> {
                readTypingOptions()
                requestedDictionary = null
            }
            Prefs.KEY_MARK_MISTAKES, Prefs.KEY_GRAMMAR_CHECK -> {
                readTypingOptions()
                lastGrammarText = ""
                scheduleChecks()
            }
        }
        refreshUi()
    }

    private fun readTypingOptions() {
        suggestionsEnabled = Prefs.suggestions(this)
        autocorrectEnabled = Prefs.autocorrect(this)
        gestureEnabled = Prefs.gestureTyping(this)
        markMistakes = Prefs.markMistakes(this)
        grammarCheck = Prefs.grammarCheck(this)
    }

    private fun configureEngine() {
        val typing = Prefs.typingLanguage(this)
        val output = Prefs.outputLanguage(this)
        if (typing != output) engine.configure(typing, output)
    }

    // --- Keys -------------------------------------------------------------------------------

    override fun onText(text: String) {
        var spaced = false
        var undo: AutocorrectUndo? = null
        when {
            // The space after a picked suggestion is already there.
            autoSpaced && text == " " -> Unit
            // "word |" + "." -> "word. |"
            autoSpaced && text in PUNCTUATION && textBefore().endsWith(" ") -> {
                replaceBefore(1, "$text ")
                spaced = true
            }
            (text == " " || text in PUNCTUATION) && autocorrectEnabled && dictionary != null -> {
                undo = autocorrect(text)
                if (undo == null) insert(text)
            }
            else -> insert(text)
        }
        autoSpaced = spaced
        undoAutocorrect = undo
        gestureWords = null
        if (shiftState == ShiftState.ONE_SHOT) shiftState = ShiftState.OFF
        updateAutoShift()
        updateSuggestions()
    }

    /** Text before the cursor that typing aids work on: the practice text, or the field's own text. */
    private fun textBefore(): String =
        if (translationActive) buffer.substring(0, cursor)
        else currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty()

    private fun isWordChar(c: Char) =
        c.isLetter() || c == '\'' || c == '’' || Character.getType(c) == Character.NON_SPACING_MARK.toInt()

    /** The (partial) word just before the cursor. */
    private fun currentWord(before: String = textBefore()): String =
        before.takeLastWhile { isWordChar(it) }.trimStart('\'', '’')

    private fun insert(text: String) {
        if (translationActive) {
            buffer.insert(cursor, text)
            cursor += text.length
            onBufferChanged()
        } else {
            currentInputConnection?.commitText(text, 1)
        }
    }

    /** Replace the last [count] characters before the cursor with [text]. */
    private fun replaceBefore(count: Int, text: String) {
        if (translationActive) {
            val start = maxOf(0, cursor - count)
            buffer.replace(start, cursor, text)
            cursor = start + text.length
            onBufferChanged()
        } else {
            val ic = currentInputConnection ?: return
            ic.beginBatchEdit()
            if (count > 0) ic.deleteSurroundingText(count, 0)
            ic.commitText(text, 1)
            ic.endBatchEdit()
        }
    }

    /** On space/punctuation: fix the word just typed if autocorrect is sure. Returns how to undo it. */
    private fun autocorrect(separator: String): AutocorrectUndo? {
        val dict = dictionary ?: return null
        val word = currentWord()
        if (word.length < 2) return null
        val result = lastResult?.takeIf { it.typed == word } ?: Suggester.suggest(dict, word, userWords)
        val correction = result.correction ?: return null
        if (correction == word) return null
        replaceBefore(word.length, correction + separator)
        return AutocorrectUndo(word, correction, separator)
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

    private fun onDelete() {
        val undo = undoAutocorrect
        val swiped = gestureWords?.firstOrNull()
        undoAutocorrect = null
        gestureWords = null
        autoSpaced = false
        val before = if (undo != null || swiped != null) textBefore() else ""
        if (undo != null && before.endsWith(undo.corrected + undo.separator)) {
            // Backspace straight after an autocorrection puts back what you typed.
            replaceBefore(undo.corrected.length + undo.separator.length, undo.original + undo.separator)
        } else if (swiped != null && before.endsWith(swiped)) {
            // Backspace straight after a swipe removes the whole word.
            replaceBefore(swiped.length, "")
        } else if (translationActive && buffer.isNotEmpty()) {
            if (cursor > 0) {
                val cut = buffer.offsetByCodePoints(cursor, -1)
                buffer.delete(cut, cursor)
                cursor = cut
                onBufferChanged()
            }
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        updateAutoShift()
        updateSuggestions()
    }

    private fun onEnter() {
        if (translationActive && buffer.isNotEmpty()) {
            val marks = visibleMarks()
            if (marks.isNotEmpty() && !warnedBeforeLockIn) {
                // Give a chance to fix marked mistakes first; pressing ✓ again sends anyway.
                warnedBeforeLockIn = true
                selectedMark = marks.first()
                statusMessage = getString(R.string.marks_before_send)
                refreshUi()
                return
            }
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
        refreshUi()
    }

    /** Capitalise the first letter of a sentence when the app asks for it. */
    private fun updateAutoShift() {
        if (shiftState == ShiftState.LOCKED) {
            keyboardView?.shiftState = shiftState
            return
        }
        if (!LanguageLayouts.forLanguage(layoutLanguage).cased) {
            shiftState = ShiftState.OFF
            keyboardView?.shiftState = shiftState
            return
        }
        val info = currentInputEditorInfo
        val capsRequested = info != null && (info.inputType and (InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)) != 0
        val cap = when {
            info == null || !capsRequested -> false
            translationActive && buffer.isNotEmpty() -> {
                val before = textBefore()
                val trimmed = before.trimEnd()
                before.isNotEmpty() && before.last() == ' ' && trimmed.isNotEmpty() && trimmed.last() in ".?!"
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
        warnedBeforeLockIn = false
        cursor = cursor.coerceIn(0, buffer.length)
        scheduleChecks()
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
        cursor = 0
        wordMarks = emptyList()
        grammarMarks = emptyList()
        selectedMark = null
        lastGrammarText = ""
        warnedBeforeLockIn = false
        markRequest++
        grammarRequest++
        mainHandler.removeCallbacks(grammarRunnable)
        generation = 0
        appliedGeneration = 0
        composed = ""
        composeStart = -1
        leadingSpace = null
        commitPending = false
        statusMessage = null
        recentExpectedCursors.clear()
        gestureWords = null
        undoAutocorrect = null
        autoSpaced = false
    }

    private fun toggleTranslation() {
        if (!fieldAllowsTranslation) return
        lockInPhrase()
        translateEnabled = !translateEnabled
        Prefs.setTranslateEnabled(this, translateEnabled)
        if (shiftState == ShiftState.LOCKED) shiftState = ShiftState.OFF
        updateAutoShift()
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
        if (newSelStart != lastSlideTarget) lastSlideTarget = -1
        if (buffer.isEmpty() && composed.isEmpty()) {
            updateAutoShift()
            updateSuggestions()
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

        ensureDictionary()
        keyboardView?.let { kv ->
            val language = layoutLanguage
            kv.rows = KeyboardLayouts.forMode(mode, language)
            kv.gestureEnabled = gestureEnabled && dictionary != null && mode == KeyboardMode.LETTERS
            kv.caseLocale = Locale.forLanguageTag(language)
            kv.shiftState = shiftState
            kv.enterLabel = if (active && buffer.isNotEmpty()) "✓" else enterLabelFor(currentInputEditorInfo)
            kv.spaceLabel = if (active) "${name(typing)} → ${name(output)}"
            else name(language)
        }

        languageChip?.let { chip ->
            // An on/off toggle styled like Shift: blue when translating, grey when not.
            chip.text = "${Languages.shortLabel(typing)} → ${Languages.shortLabel(output)}"
            chip.background = GradientDrawable().apply {
                cornerRadius = 14 * resources.displayMetrics.density
                setColor(ContextCompat.getColor(this@LanguageKeyboardService, if (active) R.color.kb_key_accent else R.color.kb_key_function))
            }
            chip.setTextColor(ContextCompat.getColor(this, if (active) R.color.kb_key_text_on_accent else R.color.kb_key_text))
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
                    getString(R.string.strip_downloading, name(typing), name(output))
                engine.state == TranslationEngine.State.DOWNLOAD_FAILED -> getString(R.string.strip_download_failed)
                else -> statusMessage
            }
            val marks = visibleMarks()
            tv.cursorColor = ContextCompat.getColor(this, R.color.brand)
            tv.markHighlightColor = ContextCompat.getColor(this, R.color.mark_selected)
            if (active && buffer.isNotEmpty()) {
                val text = SpannableStringBuilder(buffer)
                tv.cursorOffset = cursor
                tv.marks = marks.map {
                    PracticeTextView.Mark(it.start, it.end, markColor(it.kind), selectedMark?.sameAs(it) == true)
                }
                if (message != null) {
                    val start = text.length
                    text.append("  ").append(message)
                    text.setSpan(ForegroundColorSpan(secondary), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                tv.text = text
                tv.setTextColor(primary)
                tv.post { scrollPracticeToCursor() }
            } else {
                tv.cursorOffset = -1
                tv.marks = emptyList()
                tv.text = message ?: getString(R.string.strip_hint, name(typing))
                tv.setTextColor(secondary)
                practiceScroll?.scrollTo(0, 0)
            }
        }

        showCorrection()
        updateSuggestions()
    }

    // --- Practice bar: cursor and marked mistakes -------------------------------------------

    /** Keep the cursor in view, with some margin, as the text scrolls sideways. */
    private fun scrollPracticeToCursor() {
        val sv = practiceScroll ?: return
        val layout = typedText?.layout ?: return
        if (cursor > layout.text.length) return
        val x = layout.getPrimaryHorizontal(cursor).toInt()
        val margin = sv.width / 4
        val target = when {
            x < sv.scrollX + margin -> x - margin
            x > sv.scrollX + sv.width - margin -> x - sv.width + margin
            else -> return
        }
        sv.smoothScrollTo(maxOf(0, target), 0)
    }

    /**
     * Slide on the space bar: the finger's position across the keyboard places the cursor in the
     * practice text (or, with nothing there, in the app's own text box).
     */
    override fun onCursorSlide(fraction: Float): Boolean {
        gestureWords = null
        undoAutocorrect = null
        autoSpaced = false
        if (translationActive && buffer.isNotEmpty()) {
            var target = (fraction * buffer.length).roundToInt().coerceIn(0, buffer.length)
            // Don't land between the two halves of an emoji or other surrogate pair.
            if (target in 1 until buffer.length &&
                Character.isLowSurrogate(buffer[target]) && Character.isHighSurrogate(buffer[target - 1])
            ) target--
            if (target == cursor) return false
            cursor = target
            lastResult = null
            updateAutoShift()
            refreshUi()
            return true
        }
        val ic = currentInputConnection ?: return false
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return false
        val length = extracted.text?.length ?: 0
        val target = extracted.startOffset + (fraction * length).roundToInt().coerceIn(0, length)
        if (target == lastSlideTarget) return false
        lastSlideTarget = target
        ic.setSelection(target, target)
        return true
    }

    /** Last cursor position set by sliding in the app's own text, to avoid repeating it. */
    private var lastSlideTarget = -1

    /** Tap the practice text to put the cursor there (and open a marked mistake). */
    @SuppressLint("ClickableViewAccessibility")
    private fun onPracticeTouch(v: View, event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP || !translationActive || buffer.isEmpty()) return true
        val tv = v as TextView
        val layout = tv.layout ?: return true
        val offset = layout.getOffsetForHorizontal(0, event.x - tv.totalPaddingLeft).coerceIn(0, buffer.length)
        selectedMark = visibleMarks().firstOrNull { offset in it.start..it.end }
        cursor = selectedMark?.end ?: offset
        gestureWords = null
        undoAutocorrect = null
        autoSpaced = false
        lastResult = null
        updateAutoShift()
        refreshUi()
        return true
    }

    /** Language names are shown in your own (native) language, not the phone's. */
    private fun name(code: String) = Languages.displayName(code, Prefs.outputLanguage(this))

    /** Grammar explanations, translated into your own language (original -> translation). */
    private val explanationTranslations = HashMap<String, String>()

    private fun translateExplanations(marks: List<Mark>) {
        for (message in marks.mapNotNull { it.message }.distinct()) {
            if (message in explanationTranslations) continue
            explanationTranslations[message] = message // placeholder so it's only requested once
            engine.translateText(message) { translated ->
                if (translated != null) {
                    explanationTranslations[message] = translated
                    refreshUi()
                } else {
                    explanationTranslations.remove(message) // try again next time
                }
            }
        }
    }

    private fun markColor(kind: MarkKind) = ContextCompat.getColor(
        this,
        when (kind) {
            // Like a word processor: red for spelling (accents included), blue for grammar.
            MarkKind.SPELLING, MarkKind.ACCENT -> R.color.mark_spelling
            MarkKind.GRAMMAR -> R.color.mark_grammar
        },
    )

    /** Marks that still match the text (edits elsewhere may have moved or removed them). */
    private fun visibleMarks(): List<Mark> {
        if (!translationActive || buffer.isEmpty()) return emptyList()
        val text = buffer.toString()
        fun valid(m: Mark) = m.start >= 0 && m.end <= text.length && text.substring(m.start, m.end) == m.original
        val words = if (markMistakes) wordMarks.filter { valid(it) } else emptyList()
        val grammar = if (grammarCheck) {
            grammarMarks.filter { g -> valid(g) && words.none { w -> w.start < g.end && g.start < w.end } }
        } else {
            emptyList()
        }
        return (words + grammar).sortedBy { it.start }
    }

    /** After an edit: re-check words now, and grammar once typing pauses. */
    private fun scheduleChecks() {
        mainHandler.removeCallbacks(grammarRunnable)
        if (!translationActive || buffer.isBlank()) return
        val text = buffer.toString()
        val dict = dictionary
        if (markMistakes && dict != null) {
            val request = ++markRequest
            val cursorAt = cursor
            val taught = userWords
            Dictionaries.worker.execute {
                val marks = findWordMarks(dict, text, cursorAt, taught)
                mainHandler.post {
                    if (request == markRequest) {
                        wordMarks = marks
                        refreshUi()
                    }
                }
            }
        }
        if (grammarCheck && LanguageTool.isSupported(Prefs.typingLanguage(this))) {
            mainHandler.postDelayed(grammarRunnable, GRAMMAR_DELAY_MS)
        }
    }

    /** Runs on Dictionaries.worker. */
    private fun findWordMarks(dict: Dictionary, text: String, cursorAt: Int, taught: Set<String>): List<Mark> {
        if (markCacheDictionary !== dict) {
            markCache.clear()
            markCacheDictionary = dict
        }
        val marks = ArrayList<Mark>()
        for (match in WORD.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            val word = match.value
            if (cursorAt in start..end || word.length < 2) continue // still being typed
            val result = markCache.getOrPut(word) { Suggester.suggest(dict, word, taught) }
            if (word.lowercase(dict.locale) in taught) continue
            val correction = result.correction
            val accentOnly = correction != null &&
                correction.lowercase(dict.locale) != word.lowercase(dict.locale) &&
                Dictionary.fold(correction, dict.locale) == Dictionary.fold(word, dict.locale)
            when {
                accentOnly -> marks += Mark(start, end, word, MarkKind.ACCENT, null, listOf(correction!!))
                // Capitalised unknown words are probably names: leave them alone.
                !result.known && !word[0].isUpperCase() -> {
                    val fixes = (listOfNotNull(correction) + result.suggestions).distinct().take(2)
                    marks += Mark(start, end, word, MarkKind.SPELLING, null, fixes)
                }
            }
        }
        return marks
    }

    private fun runGrammarCheck() {
        if (!grammarCheck || !translationActive) return
        val text = buffer.toString()
        if (text.isBlank() || text == lastGrammarText) return
        val language = Prefs.typingLanguage(this)
        val native = Prefs.outputLanguage(this)
        val dictionaryHandlesSpelling = markMistakes && dictionary != null
        val request = ++grammarRequest
        lastGrammarText = text
        grammarExecutor.execute {
            val marks = try {
                LanguageTool.check(text, language, native)
                    .filter { it.length > 0 && it.offset >= 0 && it.offset + it.length <= text.length }
                    .filter { !(dictionaryHandlesSpelling && it.issueType == "misspelling") }
                    .map {
                        Mark(
                            it.offset, it.offset + it.length, text.substring(it.offset, it.offset + it.length),
                            if (it.issueType == "misspelling") MarkKind.SPELLING else MarkKind.GRAMMAR,
                            it.message, it.replacements,
                        )
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Grammar check failed", e)
                null
            }
            mainHandler.post {
                if (request != grammarRequest) return@post
                if (marks == null) {
                    lastGrammarText = "" // try again after the next pause
                } else {
                    grammarMarks = marks
                    translateExplanations(marks)
                    refreshUi()
                }
            }
        }
    }

    /** Explain the selected mistake and offer up to two fixes. */
    private fun showCorrection() {
        val panel = correctionPanel ?: return
        val selected = selectedMark
        val mark = selected?.let { s -> visibleMarks().firstOrNull { it.sameAs(s) } }
        if (mark == null) {
            selectedMark = null
            panel.visibility = View.GONE
            return
        }
        panel.visibility = View.VISIBLE
        val languageName = name(Prefs.typingLanguage(this))
        correctionMessage?.text = when (mark.kind) {
            MarkKind.ACCENT -> getString(R.string.mark_accent, mark.original, mark.replacements.firstOrNull().orEmpty())
            MarkKind.SPELLING -> mark.message?.let { explanation(it) } ?: if (mark.replacements.isNotEmpty()) {
                getString(R.string.mark_spelling_fix, mark.original)
            } else {
                getString(R.string.mark_spelling, mark.original, languageName)
            }
            MarkKind.GRAMMAR -> explanation(mark.message.orEmpty()) +
                if (mark.replacements.isEmpty()) "\n" + getString(R.string.mark_no_fix) else ""
        }
        correctionFixes.forEachIndexed { i, view ->
            val fix = mark.replacements.getOrNull(i)
            view?.visibility = if (fix != null) View.VISIBLE else View.GONE
            view?.text = fix
            view?.setOnClickListener(if (fix != null) View.OnClickListener { applyFix(mark, fix) } else null)
        }
    }

    /** LanguageTool explains in the language being checked; show it in your own language. */
    private fun explanation(message: String) = explanationTranslations[message] ?: message

    private fun applyFix(mark: Mark, replacement: String) {
        if (mark.end > buffer.length || buffer.substring(mark.start, mark.end) != mark.original) return
        buffer.replace(mark.start, mark.end, replacement)
        val delta = replacement.length - mark.original.length
        cursor = when {
            cursor >= mark.end -> cursor + delta
            cursor > mark.start -> mark.start + replacement.length
            else -> cursor
        }
        // Grammar marks after the fix just move along; the next check refreshes them.
        grammarMarks = grammarMarks.mapNotNull {
            when {
                it.end <= mark.start -> it
                it.start >= mark.end -> it.shiftedBy(delta)
                else -> null
            }
        }
        selectedMark = null
        lastResult = null
        gestureWords = null
        undoAutocorrect = null
        autoSpaced = false
        onBufferChanged()
    }

    // --- Suggestions, autocorrect, gesture typing -------------------------------------------

    /** Make sure the word list for the language being typed is loaded (downloading it once). */
    private fun ensureDictionary() {
        val language = layoutLanguage
        val wanted = fieldAllowsTranslation && (suggestionsEnabled || autocorrectEnabled || gestureEnabled) &&
            Dictionaries.isSupported(language)
        if (!wanted) {
            dictionary = null
            return
        }
        if (dictionary?.language == language) return
        dictionary = Dictionaries.cached(language)
        userWords = Prefs.userWords(this, language)
        lastResult = null
        gestureWords = null
        if (dictionary == null && requestedDictionary != language) {
            requestedDictionary = language
            Dictionaries.load(this, language, download = true) { loaded ->
                if (loaded != null && loaded.language == layoutLanguage) {
                    dictionary = loaded
                    scheduleChecks()
                    refreshUi()
                } else {
                    updateSuggestions()
                }
            }
        }
    }

    override fun onGesture(xs: FloatArray, ys: FloatArray) {
        val dict = dictionary ?: return
        val keyMap = keyboardView?.keyMap() ?: return
        val request = ++gestureRequest
        val shift = shiftState
        Dictionaries.worker.execute {
            val words = GestureDecoder.decode(xs, ys, keyMap, dict, 4)
            mainHandler.post {
                if (request == gestureRequest && dictionary === dict && words.isNotEmpty()) typeGestureWord(words, shift)
            }
        }
    }

    private fun typeGestureWord(words: List<String>, shift: ShiftState) {
        val locale = dictionary?.locale ?: Locale.ROOT
        val cased = words.map {
            when (shift) {
                ShiftState.LOCKED -> it.uppercase(locale)
                ShiftState.ONE_SHOT -> it.replaceFirstChar { c -> c.titlecase(locale) }
                ShiftState.OFF -> it
            }
        }
        val before = textBefore()
        val needsSpace = before.isNotEmpty() && !before.last().isWhitespace() && before.last() !in OPENERS
        insert((if (needsSpace) " " else "") + cased[0])
        gestureWords = cased
        undoAutocorrect = null
        autoSpaced = false
        if (shiftState == ShiftState.ONE_SHOT) shiftState = ShiftState.OFF
        updateAutoShift()
        updateSuggestions()
    }

    private fun updateSuggestions() {
        val bar = suggestionBar ?: return
        val visible = suggestionsEnabled && fieldAllowsTranslation && Dictionaries.isSupported(layoutLanguage) &&
            selectedMark == null
        bar.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) return

        gestureWords?.let { words ->
            showSlots(listOf(
                words.getOrNull(1)?.let { Slot(it, SlotKind.GESTURE) },
                Slot(words[0], SlotKind.GESTURE, emphasised = true),
                words.getOrNull(2)?.let { Slot(it, SlotKind.GESTURE) },
            ))
            return
        }
        val dict = dictionary
        if (dict == null) {
            val loading = Dictionaries.isLoading(layoutLanguage)
            showSlots(listOf(null, if (loading) Slot(getString(R.string.suggestions_loading), SlotKind.INFO) else null, null))
            return
        }
        val word = currentWord()
        if (word.isEmpty()) {
            showSlots(listOf(null, null, null))
            return
        }
        lastResult?.takeIf { it.typed == word }?.let { showSlots(slotsFor(it)); return }
        val request = ++suggestionRequest
        val taught = userWords
        Dictionaries.worker.execute {
            val result = Suggester.suggest(dict, word, taught)
            mainHandler.post {
                if (request == suggestionRequest && dictionary === dict) {
                    lastResult = result
                    showSlots(slotsFor(result))
                }
            }
        }
    }

    /**
     * Middle slot: what space will type (the correction, or your word if it's fine).
     * Left: your word exactly as typed, in quotes, when it isn't a known word.
     */
    private fun slotsFor(r: SuggestionResult): List<Slot?> {
        val others = r.suggestions.filter { it != r.correction }
        val verbatim = Slot(if (r.known) r.typed else "“${r.typed}”", SlotKind.VERBATIM)
        return when {
            r.correction != null -> listOf(
                verbatim,
                Slot(r.correction, SlotKind.WORD, emphasised = autocorrectEnabled),
                others.getOrNull(0)?.let { Slot(it, SlotKind.WORD) },
            )
            r.known -> listOf(
                others.getOrNull(0)?.let { Slot(it, SlotKind.WORD) },
                Slot(r.typed, SlotKind.VERBATIM, emphasised = true),
                others.getOrNull(1)?.let { Slot(it, SlotKind.WORD) },
            )
            else -> listOf(
                verbatim,
                others.getOrNull(0)?.let { Slot(it, SlotKind.WORD) },
                others.getOrNull(1)?.let { Slot(it, SlotKind.WORD) },
            )
        }
    }

    private fun showSlots(slots: List<Slot?>) {
        slots.forEachIndexed { i, slot ->
            val tv = suggestionViews[i] ?: return@forEachIndexed
            tv.text = slot?.text.orEmpty()
            tv.setTypeface(null, if (slot?.emphasised == true) Typeface.BOLD else Typeface.NORMAL)
            tv.alpha = if (slot?.kind == SlotKind.INFO) 0.6f else 1f
            val clickable = slot != null && slot.kind != SlotKind.INFO
            tv.isClickable = clickable
            tv.setOnClickListener(if (clickable) View.OnClickListener { pickSuggestion(slot!!) } else null)
        }
    }

    private fun pickSuggestion(slot: Slot) {
        when (slot.kind) {
            SlotKind.GESTURE -> {
                // Swap the swiped word for another guess.
                val words = gestureWords ?: return
                val current = words[0]
                if (!textBefore().endsWith(current)) return
                replaceBefore(current.length, slot.text)
                gestureWords = listOf(slot.text) + words.filter { it != slot.text }
            }
            SlotKind.VERBATIM -> {
                // Keep the word exactly as typed, and don't autocorrect it in future.
                val word = currentWord()
                val dict = dictionary
                if (dict != null && word.isNotEmpty() && lastResult?.known == false) {
                    val lower = word.lowercase(dict.locale)
                    Prefs.addUserWord(this, dict.language, lower)
                    userWords = userWords + lower
                }
                insert(" ")
                autoSpaced = true
                undoAutocorrect = null
            }
            SlotKind.WORD -> {
                replaceBefore(currentWord().length, slot.text + " ")
                autoSpaced = true
                undoAutocorrect = null
                gestureWords = null
            }
            SlotKind.INFO -> return
        }
        lastResult = null
        if (shiftState == ShiftState.ONE_SHOT) shiftState = ShiftState.OFF
        updateAutoShift()
        updateSuggestions()
    }

    private fun enterLabelFor(info: EditorInfo?): String {
        if (info == null || (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) return "⏎"
        // "Send" and "Search" are shown as icons; the other actions as words in the language being typed.
        val language = layoutLanguage
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEND -> "Send"
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_GO -> ActionLabels.label(ActionLabels.Action.GO, language)
            EditorInfo.IME_ACTION_NEXT -> ActionLabels.label(ActionLabels.Action.NEXT, language)
            EditorInfo.IME_ACTION_PREVIOUS -> ActionLabels.label(ActionLabels.Action.PREVIOUS, language)
            EditorInfo.IME_ACTION_DONE -> ActionLabels.label(ActionLabels.Action.DONE, language)
            else -> "⏎"
        }
    }

    private companion object {
        const val TAG = "LanguageKeyboard"
        const val DOUBLE_TAP_MS = 350L

        /** Punctuation that ends a word (and triggers autocorrect). */
        val PUNCTUATION = setOf(".", ",", "!", "?", ":", ";", "،", "؟", "؛")

        const val GRAMMAR_DELAY_MS = 1500L

        /** A word: letters and combining marks, with apostrophes only inside ("don't", "l'eau"). */
        val WORD = Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*")

        /** No space is added after these before a swiped word. */
        const val OPENERS = "([{¿¡\"'“‘«"
    }
}
