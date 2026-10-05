package com.example.marvellobby

import com.example.marvellobby.data.model.descriptionPages
import org.junit.Assert.*
import org.junit.Test

class DescriptionPagesTest {
    @Test fun longBiographyIsPaginatedWithoutLosingWords() {
        val text=(1..500).joinToString(" ") { "word$it" }
        val pages=descriptionPages(text,300)
        assertTrue(pages.size>1)
        assertTrue(pages.all { it.length<=300 })
        assertEquals(text,pages.joinToString(" "))
    }
    @Test fun missingDescriptionProducesNoInventedText() {
        assertTrue(descriptionPages("  ").isEmpty())
        assertEquals(listOf("A short description"),descriptionPages(" A short description "))
    }
    @Test fun paginationDoesNotCutAnEmojiSurrogatePair() {
        val source="a".repeat(99)+"\uD83D\uDE00"+"b".repeat(150)
        val pages=descriptionPages(source,100)
        assertEquals(source,pages.joinToString(""))
        assertFalse(pages.any { Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first()) })
    }
}
