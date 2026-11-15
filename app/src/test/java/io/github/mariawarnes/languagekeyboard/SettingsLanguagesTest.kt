package io.github.mariawarnes.languagekeyboard

import android.content.Context
import android.os.Looper
import android.widget.Spinner
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Changing languages in settings must settle on what was picked. A regression test for the screen
 * flicking between languages: changing "My language" switched the app language, the screen was
 * recreated, and the dropdowns restored their old positions in a list now sorted differently.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsLanguagesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Prefs.get(context).edit().clear().commit()
        Prefs.setLanguages(context, "es", "en")
        Prefs.setAppLanguage(context, "")
    }

    private fun idle() = repeat(5) { shadowOf(Looper.getMainLooper()).idle() }

    /** What a user tapping an item does: select it and tell the listener. */
    private fun pick(spinner: Spinner, position: Int) {
        spinner.setSelection(position)
        spinner.onItemSelectedListener?.onItemSelected(spinner, spinner.selectedView, position, position.toLong())
    }

    private fun Spinner.selectedCode(activity: SettingsActivity, appLanguage: String) =
        Languages.all(appLanguage)[selectedItemPosition]

    private fun assertSpinnersMatchSettings(activity: SettingsActivity) {
        val appLanguage = AppLocale.language(activity)
        val typing = activity.findViewById<Spinner>(R.id.typing_language)
        val output = activity.findViewById<Spinner>(R.id.output_language)
        assertEquals(Prefs.typingLanguage(context), typing.selectedCode(activity, appLanguage))
        assertEquals(Prefs.outputLanguage(context), output.selectedCode(activity, appLanguage))
    }

    /** The current activity must still be [expected]: no further recreate happened. */
    private fun assertSettled(scenario: ActivityScenario<SettingsActivity>) {
        var first: SettingsActivity? = null
        scenario.onActivity { first = it }
        idle()
        var second: SettingsActivity? = null
        scenario.onActivity { second = it }
        assertSame("settings kept recreating", first, second)
    }

    @Test
    fun changingMyLanguageToGermanSettles() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val output = activity.findViewById<Spinner>(R.id.output_language)
                pick(output, Languages.all("en").indexOf("de"))
            }
            idle()
            assertEquals("de", Prefs.outputLanguage(context))
            assertEquals("es", Prefs.typingLanguage(context))
            assertEquals("de", AppLocale.language(context)) // app follows my language

            // The screen redraws in German; the dropdowns must still show Spanish / German.
            scenario.onActivity { assertSpinnersMatchSettings(it) }
            assertSettled(scenario)

            // Simulate the selection callbacks Android fires after a redraw.
            scenario.onActivity { activity ->
                for (id in listOf(R.id.typing_language, R.id.output_language)) {
                    val s = activity.findViewById<Spinner>(id)
                    s.onItemSelectedListener?.onItemSelected(s, s.selectedView, s.selectedItemPosition, 0)
                }
            }
            idle()
            assertEquals("de", Prefs.outputLanguage(context))
            assertEquals("es", Prefs.typingLanguage(context))

            // Like rotating the phone: still the same languages.
            scenario.recreate()
            idle()
            scenario.onActivity { assertSpinnersMatchSettings(it) }
            assertEquals("de", Prefs.outputLanguage(context))
            assertEquals("es", Prefs.typingLanguage(context))
        }
    }

    @Test
    fun changingBackAndForthSettlesEachTime() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            for (target in listOf("de", "fr", "en", "it", "pt", "en")) {
                scenario.onActivity { activity ->
                    val output = activity.findViewById<Spinner>(R.id.output_language)
                    pick(output, Languages.all(AppLocale.language(activity)).indexOf(target))
                }
                idle()
                assertEquals(target, Prefs.outputLanguage(context))
                assertEquals("es", Prefs.typingLanguage(context))
                scenario.onActivity { assertSpinnersMatchSettings(it) }
                assertSettled(scenario)
            }
        }
    }

    @Test
    fun choosingAnAppLanguageKeepsMyLanguages() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val app = activity.findViewById<Spinner>(R.id.app_language)
                pick(app, listOf("", *AppLocale.AVAILABLE.toTypedArray()).indexOf("de"))
            }
            idle()
            assertEquals("de", Prefs.appLanguage(context))
            assertEquals("es", Prefs.typingLanguage(context))
            assertEquals("en", Prefs.outputLanguage(context))
            scenario.onActivity { assertSpinnersMatchSettings(it) }
            assertSettled(scenario)

            // A language with no app translation falls back to English, without looping.
            scenario.onActivity { activity ->
                val app = activity.findViewById<Spinner>(R.id.app_language)
                pick(app, 0) // "Same as my language"
            }
            idle()
            scenario.onActivity { activity ->
                val output = activity.findViewById<Spinner>(R.id.output_language)
                pick(output, Languages.all(AppLocale.language(activity)).indexOf("ja"))
            }
            idle()
            assertEquals("ja", Prefs.outputLanguage(context))
            assertEquals("en", AppLocale.language(context))
            scenario.onActivity { assertSpinnersMatchSettings(it) }
            assertSettled(scenario)
        }
    }

    @Test
    fun swapExchangesTheTwoLanguages() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<android.view.View>(R.id.swap_button).performClick() }
            idle()
            assertEquals("en", Prefs.typingLanguage(context))
            assertEquals("es", Prefs.outputLanguage(context))
            assertEquals("es", AppLocale.language(context))
            scenario.onActivity { assertSpinnersMatchSettings(it) }
            assertSettled(scenario)
        }
    }
}
