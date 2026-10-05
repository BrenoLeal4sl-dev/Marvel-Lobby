package com.example.marvellobby

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.marvellobby.data.local.*
import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPersistenceTest {
    @Test fun favoritesSurviveReopeningAndRemainIsolatedByAccount() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="persistence-test-${System.nanoTime()}.db"
        var db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
        try {
            val record=ComicEntity(1440,ResourceType.CHARACTER,"Test record",null,"Summary",null,null,ComicReference(31,"Marvel"),1,null,null,null,emptyList(),emptyList(),emptyList(),emptyList())
            var library=LibraryRepository(db.archive())
            library.record("first@example.test",record)
            library.record("first@example.test",record,true)
            assertTrue(library.all("second@example.test").isEmpty())
            db.close()
            db=Room.databaseBuilder(context,MarvelDatabase::class.java,name).build()
            library=LibraryRepository(db.archive())
            val saved=library.all("first@example.test").single()
            assertTrue(saved.favorite)
            assertTrue(saved.viewedAt>0)
            assertEquals(1440,saved.entity.id)
            library.clearHistory("first@example.test")
            assertTrue(library.all("first@example.test").single().favorite)
            assertEquals(0L,library.all("first@example.test").single().viewedAt)
            library.record("first@example.test",record,true)
            assertFalse(library.all("first@example.test").single().favorite)
        } finally { db.close();context.deleteDatabase(name) }
    }
    @Test fun localAuthenticationRejectsWrongPasswordAndDuplicateEmail() = runBlocking {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val context=object : android.content.ContextWrapper(target) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir(): java.io.File = java.io.File(target.cacheDir,"auth-test-preferences").apply { mkdirs() }
        }
        val db=Room.inMemoryDatabaseBuilder(context,MarvelDatabase::class.java).build()
        try {
            val account=AccountRepository(db.archive(),PreferencesStore(context))
            val profile=account.register("Test Explorer","Test@Example.test","Password2026","Password2026")
            assertEquals("test@example.test",profile.email)
            assertEquals(profile,account.login("TEST@example.test","Password2026"))
            assertTrue(runCatching { account.login("test@example.test","wrong") }.isFailure)
            assertTrue(runCatching { account.register("Second","test@example.test","Password2026","Password2026") }.isFailure)
            assertTrue(runCatching { account.register("Test","invalid","Password2026","Password2026") }.isFailure)
            assertTrue(runCatching { account.register("Test","new@example.test","Password2026","Mismatch2026") }.isFailure)
        } finally { db.close() }
    }
}
