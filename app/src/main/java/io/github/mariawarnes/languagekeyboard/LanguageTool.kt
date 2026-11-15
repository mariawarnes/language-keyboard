package io.github.mariawarnes.languagekeyboard

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Online grammar check using LanguageTool's free public API (https://languagetool.org).
 * No account needed; limited to about 20 checks a minute, which is plenty for a keyboard.
 * Only used when the user turns "Grammar check" on, since it sends the practice text there.
 */
object LanguageTool {

    private const val ENDPOINT = "https://api.languagetool.org/v2/check"

    class Match(
        val offset: Int,
        val length: Int,
        val message: String,
        val replacements: List<String>,
        /** e.g. "grammar", "misspelling", "typographical", "style". */
        val issueType: String,
    )

    /** Languages LanguageTool checks, mapped to the codes it expects (some need a regional variant). */
    private val CODES = mapOf(
        "en" to "en-US", "de" to "de-DE", "pt" to "pt-BR", "ca" to "ca-ES",
        "es" to "es", "fr" to "fr", "it" to "it", "nl" to "nl", "pl" to "pl", "ru" to "ru", "uk" to "uk",
        "sv" to "sv", "da" to "da", "el" to "el", "gl" to "gl", "ro" to "ro", "sk" to "sk", "sl" to "sl",
        "be" to "be", "ar" to "ar", "fa" to "fa", "ga" to "ga", "eo" to "eo", "tl" to "tl",
    )

    fun isSupported(language: String): Boolean = language in CODES

    /** Blocking network call: run it off the main thread. */
    fun check(text: String, language: String, nativeLanguage: String): List<Match> {
        val code = CODES[language] ?: return emptyList()
        val body = buildString {
            append("text=").append(URLEncoder.encode(text, "UTF-8"))
            append("&language=").append(code)
            // Lets LanguageTool spot "false friends" between your two languages.
            CODES[nativeLanguage]?.let { append("&motherTongue=").append(it) }
        }
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("LanguageTool HTTP ${connection.responseCode}")
            }
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val matches = json.optJSONArray("matches") ?: return emptyList()
            return (0 until matches.length()).map { i ->
                val m = matches.getJSONObject(i)
                val reps = m.optJSONArray("replacements")
                Match(
                    offset = m.getInt("offset"),
                    length = m.getInt("length"),
                    message = m.optString("message"),
                    replacements = (0 until (reps?.length() ?: 0)).take(3).map { reps!!.getJSONObject(it).optString("value") },
                    issueType = m.optJSONObject("rule")?.optString("issueType").orEmpty(),
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
