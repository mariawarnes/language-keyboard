package io.github.mariawarnes.languagelens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlangTest {

    /** What the Lens does around the translator, with the translation supplied by the test. */
    private fun roundTrip(original: String, from: String, to: String, translate: (String) -> String): String {
        val expanded = Slang.expand(original, from)
        return Slang.shorten(translate(expanded.text), expanded.concepts, to)
    }

    @Test
    fun abbreviationsAreSpeltOutForTheTranslator() {
        assertEquals("please send it", Slang.expand("pls send it", "en").text)
        assertEquals("I don't know haha", Slang.expand("idk lol", "en").text)
        assertEquals("Please call me tomorrow", Slang.expand("Pls call me tmrw", "en").text)
        assertEquals("are you coming", Slang.expand("r u coming", "en").text)
        assertEquals("coffee without sugar", Slang.expand("coffee w/o sugar", "en").text)
    }

    @Test
    fun ordinaryWordsAreLeftAlone() {
        assertEquals("Please, khaki bus", Slang.expand("Please, khaki bus", "en").text)
        assertEquals("U2 is a band", Slang.expand("U2 is a band", "en").text) // only lower-case "u" on its own
        assertTrue(Slang.expand("hello there", "en").concepts.isEmpty())
    }

    @Test
    fun theTranslationUsesTheOtherLanguagesShorthand() {
        assertEquals("Porfa envíalo", roundTrip("pls send it", "en", "es") { "Por favor envíalo" })
        assertEquals("nse jajaja", roundTrip("idk lol", "en", "es") { "No sé jaja" })
        assertEquals("nos vemos mñn", roundTrip("see you tmrw", "en", "es") { "nos vemos mañana" })
        assertEquals("te mando un msj", roundTrip("I'll send u a msg", "en", "es") { "te mando un mensaje" })
        assertEquals("jsp mdr", roundTrip("idk lol", "en", "fr") { "je sais pas haha" })
        assertEquals("obg, vc é demais", roundTrip("thx, u r the best", "en", "pt") { "obrigado, você é demais" })
    }

    @Test
    fun onlyIdeasThatWereAbbreviatedGetShortened() {
        // "please" was written out in full, so "por favor" stays in full too.
        assertEquals("Por favor, nos vemos mñn", roundTrip("Please, see you tmrw", "en", "es") { "Por favor, nos vemos mañana" })
    }

    @Test
    fun noShorthandMeansTheFullPhraseStays() {
        assertEquals("la verdad, es genial", roundTrip("tbh it's great", "en", "es") { "la verdad, es genial" })
    }

    @Test
    fun worksTheOtherWayRound() {
        assertEquals("pls send it", roundTrip("xfa envíalo", "es", "en") { "please send it" })
    }
}
