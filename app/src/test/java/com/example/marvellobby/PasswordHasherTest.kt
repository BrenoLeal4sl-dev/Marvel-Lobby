package com.example.marvellobby

import com.example.marvellobby.data.local.PasswordHasher
import org.junit.Assert.*
import org.junit.Test

class PasswordHasherTest {
    @Test fun correctPasswordVerifiesAndWrongPasswordDoesNot() {
        val salt=PasswordHasher.salt()
        val hash=PasswordHasher.hash("Test password 2026",salt)
        assertTrue(PasswordHasher.verify("Test password 2026",salt,hash))
        assertFalse(PasswordHasher.verify("wrong password",salt,hash))
        assertNotEquals("Test password 2026",hash)
    }
    @Test fun eachAccountGetsAnIndependentSalt() {
        val first=PasswordHasher.salt()
        val second=PasswordHasher.salt()
        assertNotEquals(first,second)
        assertNotEquals(PasswordHasher.hash("Same password 1",first),PasswordHasher.hash("Same password 1",second))
    }
}
