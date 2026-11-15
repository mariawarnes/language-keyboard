package io.github.mariawarnes.languagekeyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Downloads, stores and loads word lists for suggestions, autocorrect and gesture typing.
 *
 * Lists come from FrequencyWords by Hermit Dave (https://github.com/hermitdave/FrequencyWords),
 * built from OpenSubtitles and shared under CC BY-SA 4.0. Each is ~650 KB and downloaded once.
 *
 * Call everything except [worker] from the main thread; callbacks arrive on the main thread.
 */
object Dictionaries {

    private const val TAG = "Dictionaries"
    private const val BASE = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018"
    private const val MAX_WORDS = 50_000

    /** Languages with a 50k list upstream. */
    private val FIFTY_K = setOf(
        "sq", "ar", "bg", "ca", "hr", "cs", "da", "nl", "en", "et", "fi", "fr", "gl", "ka", "de", "el", "he",
        "hu", "is", "id", "it", "lt", "lv", "mk", "ms", "no", "fa", "pl", "ro", "ru", "sk", "sl", "es", "sv",
        "tr", "uk", "vi", "ko", "bn",
    )

    /** Smaller languages that only have a full list upstream (we keep the top 50k). */
    private val FULL_ONLY = setOf("af", "eo", "tl", "ur")

    private fun sourcePath(language: String): String? = when (language) {
        "pt" -> "pt_br/pt_br_50k.txt" // Brazilian Portuguese has far more data
        "zh" -> "zh_cn/zh_cn_50k.txt" // Simplified Chinese, for pinyin conversion
        in FIFTY_K -> "$language/${language}_50k.txt"
        in FULL_ONLY -> "$language/${language}_full.txt"
        else -> null
    }

    /** Word lists only make sense for languages we also have a proper keyboard layout for. */
    fun isSupported(language: String): Boolean =
        sourcePath(language) != null && LanguageLayouts.forLanguage(language).dedicated

    /** Background thread for loading and for suggestion / gesture work on loaded dictionaries. */
    val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    // Keep the few most recently used lists in memory (the language you're learning + your own).
    private val loaded = object : LinkedHashMap<String, Dictionary>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Dictionary>?) = size > 3
    }
    private val pending = HashMap<String, MutableList<(Dictionary?) -> Unit>>()

    private fun file(context: Context, language: String) = File(File(context.filesDir, "dictionaries"), "$language.txt")

    fun isDownloaded(context: Context, language: String): Boolean = file(context, language).exists()

    fun cached(language: String): Dictionary? = loaded[language]

    fun isLoading(language: String): Boolean = language in pending

    // --- Japanese kana → kanji (SKK dictionary) ---------------------------------------------

    /**
     * SKK-JISYO.M from the SKK project (https://github.com/skk-dev/dict, GPL-2.0-or-later):
     * readings in hiragana → kanji, about 140 KB, downloaded once like the word lists.
     */
    private const val SKK_URL = "https://raw.githubusercontent.com/skk-dev/dict/master/SKK-JISYO.M"
    private var kanji: Map<String, List<String>>? = null
    private var kanjiPending: MutableList<(Map<String, List<String>>?) -> Unit>? = null

    fun isKanjiLoading(): Boolean = kanjiPending != null

    fun loadKanji(context: Context, callback: (Map<String, List<String>>?) -> Unit) {
        kanji?.let { callback(it); return }
        kanjiPending?.let { it += callback; return }
        kanjiPending = mutableListOf(callback)
        val appContext = context.applicationContext
        io.execute {
            val result = try {
                val f = File(File(appContext.filesDir, "dictionaries"), "ja-skk.txt")
                if (!f.exists()) {
                    f.parentFile?.mkdirs()
                    val connection = URL(SKK_URL).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    try {
                        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                            throw IllegalStateException("HTTP ${connection.responseCode}")
                        }
                        val temp = File(f.parentFile, "ja-skk.part")
                        connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                        if (!temp.renameTo(f)) throw IllegalStateException("Couldn't save SKK dictionary")
                    } finally {
                        connection.disconnect()
                    }
                }
                f.inputStream().bufferedReader(java.nio.charset.Charset.forName("EUC-JP")).useLines {
                    JapaneseConverter.parseSkk(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load the Japanese dictionary", e)
                null
            }
            main.post {
                if (result != null) kanji = result
                kanjiPending?.forEach { it(result) }
                kanjiPending = null
            }
        }
    }

    /** Load [language], downloading it first if needed and [download] is true. */
    fun load(context: Context, language: String, download: Boolean, callback: (Dictionary?) -> Unit) {
        loaded[language]?.let { callback(it); return }
        if (!isSupported(language)) {
            callback(null)
            return
        }
        pending[language]?.let { it += callback; return }
        pending[language] = mutableListOf(callback)
        val appContext = context.applicationContext
        io.execute {
            val dictionary = try {
                val f = file(appContext, language)
                if (!f.exists() && download) fetch(language, f)
                if (f.exists()) parse(language, f) else null
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load word list for $language", e)
                null
            }
            main.post {
                if (dictionary != null) loaded[language] = dictionary
                pending.remove(language)?.forEach { it(dictionary) }
            }
        }
    }

    private fun fetch(language: String, target: File) {
        val path = sourcePath(language) ?: return
        val connection = URL("$BASE/$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.part")
            // Keep only the first MAX_WORDS lines so "full" lists don't take up extra space.
            connection.inputStream.bufferedReader().useLines { lines ->
                temp.bufferedWriter().use { out ->
                    lines.take(MAX_WORDS).forEach { out.write(it); out.write('\n'.code) }
                }
            }
            if (!temp.renameTo(target)) throw IllegalStateException("Couldn't save word list")
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(language: String, file: File): Dictionary {
        val locale = java.util.Locale.forLanguageTag(language)
        val seen = HashSet<String>()
        val words = ArrayList<String>(MAX_WORDS)
        file.bufferedReader().useLines { lines ->
            for (line in lines) {
                var word = line.substringBefore(' ').trim()
                // Skip junk like "quiero-" or "'bout": words must start and end with a letter.
                if (word.isEmpty() || !word.first().isLetter() || !word.last().isLetter()) continue
                if (!word.all { it.isLetter() || it == '\'' || it == '’' || it == '-' || Character.getType(it) == Character.NON_SPACING_MARK.toInt() }) continue
                if (language == "en") word = englishCase(word)
                if (seen.add(word.lowercase(locale))) words += word
                if (words.size >= MAX_WORDS) break
            }
        }
        return Dictionary(language, words)
    }

    /** The lists are all lower case; English "I" and its contractions are always capitalised. */
    private fun englishCase(word: String): String =
        if (word == "i" || word.startsWith("i'")) "I" + word.substring(1) else word
}
