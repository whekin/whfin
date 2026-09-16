package dev.whekin.whfin.data.importer

import dev.whekin.whfin.ui.counterpartyLabel
import dev.whekin.whfin.ui.counterpartyMatches
import org.junit.Assert.*
import org.junit.Test

/**
 * Reading a Georgian name, as the country's other banks print it.
 *
 * Synthetic names throughout: common Georgian words and given names picked for the letters they
 * exercise — the aspirated pairs, the digraphs, and the two letters romanization spells with more
 * than one character.
 */
class GeorgianRomanizationTest {

    @Test fun aNameIsReadTheWayTheOtherBanksSpellIt() {
        assertEquals("Giorgi Kharadze", GeorgianRomanization.romanize("გიორგი ხარაძე"))
        assertEquals("Shps Tsiskari", GeorgianRomanization.romanize("შპს ცისკარი"))
        assertEquals("Ghvinis Jikhuri", GeorgianRomanization.romanize("ღვინის ჯიხური"))
        assertEquals("Nino Chqonia", GeorgianRomanization.romanize("ნინო ჭყონია"))
    }

    /**
     * The distinction the skeleton throws away is exactly the one a reader needs.
     *
     * `ხ` is `kh` to a reader and `h` to the comparer, so sharing one table between them would
     * either misspell every name or stop matching them.
     */
    @Test fun readingIsNotComparing() {
        assertEquals("Kharadze", GeorgianRomanization.romanize("ხარაძე"))
        assertEquals("haradze", GeorgianLatin.skeleton("ხარაძე"))
    }

    @Test fun textThatIsNotGeorgianIsLeftExactlyAsItArrived() {
        assertFalse(GeorgianRomanization.isGeorgian("SPAR"))
        assertEquals("SPAR", counterpartyLabel("SPAR", latin = true))
        assertEquals("ANTHROPIC* CLAUDE.AI", counterpartyLabel("ANTHROPIC* CLAUDE.AI", latin = true))
    }

    @Test fun theSwitchChangesTheReadingAndNothingElse() {
        assertEquals("გიორგი ხარაძე", counterpartyLabel("გიორგი ხარაძე", latin = false))
        assertEquals("Giorgi Kharadze", counterpartyLabel("გიორგი ხარაძე", latin = true))
    }

    @Test fun searchAnswersToBothAlphabets() {
        assertTrue(counterpartyMatches("გიორგი ხარაძე", "kharadze"))
        assertTrue(counterpartyMatches("გიორგი ხარაძე", "ხარაძე"))
        assertFalse(counterpartyMatches("გიორგი ხარაძე", "tsiskari"))
    }
}
