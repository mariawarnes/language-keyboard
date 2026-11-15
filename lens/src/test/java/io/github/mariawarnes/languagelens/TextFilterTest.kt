package io.github.mariawarnes.languagelens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextFilterTest {

    @Test
    fun messagesAreTranslated() {
        assertTrue(TextFilter.worthTranslating("See you tomorrow at the beach!"))
        assertTrue(TextFilter.worthTranslating("How are you?"))
        assertTrue(TextFilter.worthTranslating("Can you call me later"))
    }

    @Test
    fun timesNumbersAndLinksAreNot() {
        assertFalse(TextFilter.worthTranslating("12:45"))
        assertFalse(TextFilter.worthTranslating("9:05 PM"))
        assertFalse(TextFilter.worthTranslating("15/11/2026"))
        assertFalse(TextFilter.worthTranslating("+44 7700 900123"))
        assertFalse(TextFilter.worthTranslating("https://example.com/page"))
        assertFalse(TextFilter.worthTranslating("ok"))
        assertFalse(TextFilter.worthTranslating("😂😂😂"))
    }
}
