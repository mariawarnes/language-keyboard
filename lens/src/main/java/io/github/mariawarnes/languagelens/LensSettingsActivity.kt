package io.github.mariawarnes.languagelens

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
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
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.Locale

class LensSettingsActivity : AppCompatActivity() {

    private val languages = TranslateLanguage.getAllLanguages().sortedBy { name(it) }
    private lateinit var learning: Spinner
    private lateinit var native: Spinner
    private lateinit var packStatus: TextView
    private lateinit var download: Button

    private fun name(code: String): String =
        Locale.forLanguageTag(code).getDisplayName(Locale.ENGLISH).replaceFirstChar { it.titlecase(Locale.ENGLISH) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lens)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scroll)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, 0)
            (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                if (lp.bottomMargin != bars.bottom) {
                    lp.bottomMargin = bars.bottom
                    v.layoutParams = lp
                }
            }
            WindowInsetsCompat.CONSUMED
        }

        learning = findViewById(R.id.learning)
        native = findViewById(R.id.native_language)
        packStatus = findViewById(R.id.pack_status)
        download = findViewById(R.id.download)

        val names = languages.map(::name)
        for (spinner in listOf(learning, native)) {
            spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        }
        learning.setSelection(languages.indexOf(LensPrefs.learning(this)).coerceAtLeast(0), false)
        native.setSelection(languages.indexOf(LensPrefs.native(this)).coerceAtLeast(0), false)
        val onChange = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = save()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        learning.onItemSelectedListener = onChange
        native.onItemSelectedListener = onChange

        download.setOnClickListener { downloadPacks() }
        findViewById<Button>(R.id.open_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.open_app_info).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        bindSwitch(R.id.whatsapp, LensPrefs.KEY_WHATSAPP, true)
        bindSwitch(R.id.messenger, LensPrefs.KEY_MESSENGER, true)
        bindSwitch(R.id.paused, LensPrefs.KEY_PAUSED, false)
        packStatus.setText(R.string.packs_missing)
    }

    override fun onResume() {
        super.onResume()
        val manager = getSystemService(AccessibilityManager::class.java)
        val me = ComponentName(this, LensService::class.java).flattenToString()
        val on = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id == me || it.resolveInfo?.serviceInfo?.packageName == packageName }
        findViewById<TextView>(R.id.service_status).setText(if (on) R.string.status_on else R.string.status_off)
        findViewById<CompoundButton>(R.id.paused).isChecked = LensPrefs.paused(this)
    }

    private fun bindSwitch(id: Int, key: String, default: Boolean) {
        findViewById<CompoundButton>(id).apply {
            isChecked = LensPrefs.get(this@LensSettingsActivity).getBoolean(key, default)
            setOnCheckedChangeListener { _, checked ->
                LensPrefs.get(this@LensSettingsActivity).edit().putBoolean(key, checked).apply()
            }
        }
    }

    private fun save() {
        LensPrefs.get(this).edit()
            .putString(LensPrefs.KEY_LEARNING, languages[learning.selectedItemPosition])
            .putString(LensPrefs.KEY_NATIVE, languages[native.selectedItemPosition])
            .apply()
    }

    private fun downloadPacks() {
        save()
        val source = LensPrefs.native(this)
        val target = LensPrefs.learning(this)
        if (source == target) return
        download.isEnabled = false
        packStatus.setText(R.string.packs_downloading)
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build(),
        )
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnCompleteListener { task ->
                translator.close()
                if (isDestroyed) return@addOnCompleteListener
                download.isEnabled = true
                if (task.isSuccessful) {
                    packStatus.setText(R.string.packs_ready)
                } else {
                    packStatus.text = getString(R.string.packs_failed, task.exception?.localizedMessage ?: "")
                }
            }
    }
}
