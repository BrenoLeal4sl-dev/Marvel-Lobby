package com.example.marvellobby

import com.example.marvellobby.data.model.Usernames
import org.junit.Assert.*
import org.junit.Test

class UsernamesTest {
    @Test fun generatedHandlesNormalizeAccentsAndStayWithinAllowedLength() {
        val handle=Usernames.create("Brêno Leal da Silva Junior")
        assertTrue(handle.startsWith("brenoleal"))
        assertTrue(handle.length<=24)
        assertTrue(Usernames.valid(handle))
        assertTrue(Usernames.valid(Usernames.create("🦸")))
    }
    @Test fun userInputAllowsAtPrefixAndRejectsEmailsOrWhitespace() {
        assertEquals("brenoleal1234",Usernames.normalize(" @BrenoLeal1234 "))
        assertTrue(Usernames.valid("brenoleal1234"))
        listOf("breno@test.com","two words","ab","___","a".repeat(25)).forEach { assertFalse(Usernames.valid(it)) }
    }
}
