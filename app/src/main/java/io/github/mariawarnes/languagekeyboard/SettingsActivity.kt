package io.github.mariawarnes.languagekeyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CompoundButton
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class SettingsActivity : AppCompatActivity() {

    private lateinit var keyboardStatus: TextView
    private lateinit var typingSpinner: Spinner
    private lateinit var outputSpinner: Spinner
    private lateinit var modelStatus: TextView
    private lateinit var layoutNote: TextView
    private lateinit var enableButton: Button
    private lateinit var downloadButton: Button
    private lateinit var wordListStatus: TextView
    private lateinit var downloadWordsButton: Button

    private var languages: List<String> = emptyList()

    /** The language the spinner lists are currently written in (your native language). */
    private var namesLanguage = ""
    private var updatingSpinners = false
    private var downloading = false

    /** Show this screen in the app language chosen in settings. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        applyEdgeToEdgeInsets(findViewById(R.id.scroll))

        keyboardStatus = findViewById(R.id.keyboard_status)
        typingSpinner = findViewById(R.id.typing_language)
        outputSpinner = findViewById(R.id.output_language)
        modelStatus = findViewById(R.id.model_status)
        layoutNote = findViewById(R.id.layout_note)
        enableButton = findViewById(R.id.enable_button)
        downloadButton = findViewById(R.id.download_button)

        enableButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.choose_button).setOnClickListener {
            inputMethodManager().showInputMethodPicker()
        }

        fillLanguageLists()
        setUpAppLanguage()

        val onChange = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!updatingSpinners) saveLanguages()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        typingSpinner.onItemSelectedListener = onChange
        outputSpinner.onItemSelectedListener = onChange

        findViewById<Button>(R.id.swap_button).setOnClickListener {
            val typing = typingSpinner.selectedItemPosition
            typingSpinner.setSelection(outputSpinner.selectedItemPosition, false)
            outputSpinner.setSelection(typing, false)
            saveLanguages()
        }

        downloadButton.setOnClickListener { downloadModels() }
        refreshModelStatus()

        bindSwitch(R.id.gesture_switch, Prefs.KEY_GESTURE_TYPING, Prefs.gestureTyping(this))
        bindSwitch(R.id.suggestions_switch, Prefs.KEY_SUGGESTIONS, Prefs.suggestions(this))
        bindSwitch(R.id.autocorrect_switch, Prefs.KEY_AUTOCORRECT, Prefs.autocorrect(this))
        bindSwitch(R.id.mark_switch, Prefs.KEY_MARK_MISTAKES, Prefs.markMistakes(this))
        bindSwitch(R.id.grammar_switch, Prefs.KEY_GRAMMAR_CHECK, Prefs.grammarCheck(this))
        wordListStatus = findViewById(R.id.word_list_status)
        downloadWordsButton = findViewById(R.id.download_words_button)
        downloadWordsButton.setOnClickListener { downloadWordLists() }
        refreshWordListStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshKeyboardStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The keyboard picker is a dialog; refresh after it closes.
        if (hasFocus) refreshKeyboardStatus()
    }

    private fun inputMethodManager() = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

    /** Language names are shown in the app language. */
    private fun name(code: String) = Languages.displayName(code, AppLocale.language(this))

    /** The first setting: which language this app's own text is in. */
    private fun setUpAppLanguage() {
        val spinner = findViewById<Spinner>(R.id.app_language)
        val codes = listOf("") + AppLocale.AVAILABLE
        val labels = codes.map { code ->
            if (code.isEmpty()) {
                getString(R.string.app_language_auto)
            } else {
                Languages.displayName(code, code) // each language in its own name: "Español"
            }
        }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.setSelection(codes.indexOf(Prefs.appLanguage(this)).coerceAtLeast(0), false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val code = codes[position]
                if (code == Prefs.appLanguage(this@SettingsActivity)) return
                Prefs.setAppLanguage(this@SettingsActivity, code)
                recreate() // redraw everything in the new language
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    /** (Re)build both language lists, named and sorted in your native language. */
    private fun fillLanguageLists() {
        val appLanguage = AppLocale.language(this)
        namesLanguage = appLanguage
        languages = Languages.all(appLanguage)
        val names = languages.map { Languages.displayName(it, appLanguage) }
        updatingSpinners = true
        for (spinner in listOf(typingSpinner, outputSpinner)) {
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }
        typingSpinner.setSelection(indexOf(Prefs.typingLanguage(this)), false)
        outputSpinner.setSelection(indexOf(Prefs.outputLanguage(this)), false)
        // Selection callbacks from the new adapters arrive after layout; ignore those.
        typingSpinner.post { updatingSpinners = false }
    }

    private fun indexOf(code: String) = languages.indexOf(code).coerceAtLeast(0)

    private fun selectedTyping() = languages[typingSpinner.selectedItemPosition]
    private fun selectedOutput() = languages[outputSpinner.selectedItemPosition]

    private fun saveLanguages() {
        val typing = selectedTyping()
        val output = selectedOutput()
        if (typing != Prefs.typingLanguage(this) || output != Prefs.outputLanguage(this)) {
            Prefs.setLanguages(this, typing, output)
        }
        if (AppLocale.language(this) != namesLanguage) {
            // The app follows your language and that just changed: redraw in it.
            typingSpinner.post { recreate() }
        }
        refreshModelStatus()
    }

    private fun refreshKeyboardStatus() {
        val enabled = inputMethodManager().enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith("$packageName/")
        enableButton.visibility = if (enabled) View.GONE else View.VISIBLE
        keyboardStatus.setText(
            when {
                selected -> R.string.status_selected
                enabled -> R.string.status_enabled
                else -> R.string.status_not_enabled
            }
        )
    }

    /**
     * Android 15+ draws apps edge-to-edge. Keep the content clear of the status bar, and shrink
     * the scroll area above the keyboard so the focused "try it" box scrolls into view.
     */
    private fun applyEdgeToEdgeInsets(scroll: View) {
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                val bottom = maxOf(bars.bottom, ime.bottom)
                if (lp.bottomMargin != bottom) {
                    lp.bottomMargin = bottom
                    v.layoutParams = lp
                }
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun refreshLayoutNote(typing: String, output: String) {
        val typingLayout = LanguageLayouts.forLanguage(typing)
        val outputLayout = LanguageLayouts.forLanguage(output)
        val lines = mutableListOf(
            if (typingLayout.dedicated) getString(R.string.layout_note, typingLayout.name)
            else getString(R.string.layout_missing, name(typing))
        )
        if (outputLayout.name != typingLayout.name) {
            lines += getString(R.string.layout_note_off, name(output))
            if (!outputLayout.dedicated) lines += getString(R.string.layout_missing, name(output))
        }
        layoutNote.text = lines.joinToString("\n")
    }

    private fun bindSwitch(id: Int, key: String, value: Boolean) {
        findViewById<CompoundButton>(id).apply {
            isChecked = value
            setOnCheckedChangeListener { _, checked -> Prefs.setFlag(this@SettingsActivity, key, checked) }
        }
    }

    private fun wordListLanguages() = listOf(selectedTyping(), selectedOutput()).distinct()

    private fun refreshWordListStatus() {
        if (!::wordListStatus.isInitialized) return
        val parts = wordListLanguages().map { code ->
            val languageName = name(code)
            when {
                !Dictionaries.isSupported(code) -> getString(R.string.word_list_unsupported, languageName)
                Dictionaries.isLoading(code) -> getString(R.string.word_list_loading, languageName)
                Dictionaries.isDownloaded(this, code) -> getString(R.string.word_list_ready, languageName)
                else -> getString(R.string.word_list_missing, languageName)
            }
        }
        wordListStatus.text = getString(R.string.word_lists, parts.joinToString(", "))
        downloadWordsButton.isEnabled = wordListLanguages().any {
            Dictionaries.isSupported(it) && !Dictionaries.isDownloaded(this, it) && !Dictionaries.isLoading(it)
        }
    }

    private fun downloadWordLists() {
        for (code in wordListLanguages()) {
            if (!Dictionaries.isSupported(code) || Dictionaries.isDownloaded(this, code)) continue
            Dictionaries.load(this, code, download = true) { if (!isDestroyed) refreshWordListStatus() }
        }
        refreshWordListStatus()
    }

    private fun refreshCheckingSection(typing: String) {
        val languageName = name(typing)
        findViewById<TextView>(R.id.checking_title).text = getString(R.string.section_checking, languageName)
        findViewById<TextView>(R.id.grammar_note).apply {
            visibility = if (LanguageTool.isSupported(typing)) View.GONE else View.VISIBLE
            text = getString(R.string.grammar_unsupported, languageName)
        }
    }

    private fun refreshModelStatus() {
        val typing = selectedTyping()
        val output = selectedOutput()
        refreshLayoutNote(typing, output)
        refreshCheckingSection(typing)
        refreshWordListStatus()
        if (downloading) return
        if (typing == output) {
            modelStatus.setText(R.string.same_language)
            downloadButton.isEnabled = false
            return
        }
        val models = try {
            RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
        } catch (e: Exception) {
            return // ML Kit not available; leave the status empty rather than crash
        }
        models
            .addOnSuccessListener { models ->
                if (downloading || typing != selectedTyping() || output != selectedOutput()) return@addOnSuccessListener
                val have = models.map { it.language }.toSet()
                // English is the pivot language and ships with ML Kit.
                val ready = listOf(typing, output).filter { it != TranslateLanguage.ENGLISH }.all { it in have }
                modelStatus.setText(if (ready) R.string.models_ready else R.string.models_missing)
                downloadButton.isEnabled = !ready
            }
    }

    private fun downloadModels() {
        val typing = selectedTyping()
        val output = selectedOutput()
        if (typing == output) return
        downloading = true
        downloadButton.isEnabled = false
        modelStatus.setText(R.string.models_downloading)

        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(typing).setTargetLanguage(output).build()
        )
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnCompleteListener { task ->
                translator.close()
                downloading = false
                if (isDestroyed) return@addOnCompleteListener
                if (task.isSuccessful) {
                    refreshModelStatus()
                } else {
                    modelStatus.text = getString(R.string.models_failed, task.exception?.localizedMessage ?: "")
                    downloadButton.isEnabled = true
                }
            }
    }
}
