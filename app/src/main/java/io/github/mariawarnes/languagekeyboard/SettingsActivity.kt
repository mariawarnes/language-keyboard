package io.github.mariawarnes.languagekeyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var downloadButton: Button

    private val languages = Languages.all
    private var downloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        keyboardStatus = findViewById(R.id.keyboard_status)
        typingSpinner = findViewById(R.id.typing_language)
        outputSpinner = findViewById(R.id.output_language)
        modelStatus = findViewById(R.id.model_status)
        downloadButton = findViewById(R.id.download_button)

        findViewById<Button>(R.id.enable_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.choose_button).setOnClickListener {
            inputMethodManager().showInputMethodPicker()
        }

        val names = languages.map { Languages.displayName(it) }
        for (spinner in listOf(typingSpinner, outputSpinner)) {
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }
        typingSpinner.setSelection(indexOf(Prefs.typingLanguage(this)), false)
        outputSpinner.setSelection(indexOf(Prefs.outputLanguage(this)), false)

        val onChange = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = saveLanguages()
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

    private fun indexOf(code: String) = languages.indexOf(code).coerceAtLeast(0)

    private fun selectedTyping() = languages[typingSpinner.selectedItemPosition]
    private fun selectedOutput() = languages[outputSpinner.selectedItemPosition]

    private fun saveLanguages() {
        val typing = selectedTyping()
        val output = selectedOutput()
        if (typing != Prefs.typingLanguage(this) || output != Prefs.outputLanguage(this)) {
            Prefs.setLanguages(this, typing, output)
        }
        refreshModelStatus()
    }

    private fun refreshKeyboardStatus() {
        val enabled = inputMethodManager().enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith("$packageName/")
        keyboardStatus.setText(
            when {
                selected -> R.string.status_selected
                enabled -> R.string.status_enabled
                else -> R.string.status_not_enabled
            }
        )
    }

    private fun refreshModelStatus() {
        if (downloading) return
        val typing = selectedTyping()
        val output = selectedOutput()
        if (typing == output) {
            modelStatus.setText(R.string.same_language)
            downloadButton.isEnabled = false
            return
        }
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
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
