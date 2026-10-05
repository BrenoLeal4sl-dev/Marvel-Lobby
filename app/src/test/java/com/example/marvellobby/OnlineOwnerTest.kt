package com.example.marvellobby

import com.example.marvellobby.data.repository.UserProfile
import org.junit.Assert.*
import org.junit.Test

class OnlineOwnerTest {
    @Test fun remoteLibraryOwnerSurvivesChangesToEmailAndUsername() {
        val before=UserProfile("Hero","old@example.invalid",username="before",id="74ad39c4-273b-4b5b-997a-00e01ff7ff21")
        val after=before.copy(email="new@example.invalid",username="after")
        assertTrue(before.online);assertEquals(before.ownerKey,after.ownerKey)
        assertNotEquals(before.email,before.ownerKey)
        val local=UserProfile("Local","local@example.invalid")
        assertFalse(local.online);assertEquals(local.email,local.ownerKey)
    }
}
