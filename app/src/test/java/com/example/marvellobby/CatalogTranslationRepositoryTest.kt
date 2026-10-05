package com.example.marvellobby

import com.example.marvellobby.data.remote.CatalogTranslator
import com.example.marvellobby.data.repository.CatalogTranslationRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class CatalogTranslationRepositoryTest {
    @get:Rule val files=TemporaryFolder()
    @Test fun cachedTranslationSurvivesRepositoryRecreationWithoutNetwork() = runBlocking<Unit> {
        val directory=files.newFolder()
        var calls=0
        val repository=CatalogTranslationRepository(CatalogTranslator { calls++;"Spider-Man escala paredes." },directory)
        assertEquals("Spider-Man escala paredes.",repository.portuguese("Spider-Man climbs walls."))
        val offline=CatalogTranslationRepository(CatalogTranslator { throw IOException("Offline") },directory)
        assertEquals("Spider-Man escala paredes.",offline.portuguese("Spider-Man climbs walls."))
        assertEquals(1,calls)
    }
    @Test fun existingTranslationCacheIsReusedAfterProviderChange() = runBlocking<Unit> {
        val directory=files.newFolder()
        val repository=CatalogTranslationRepository(CatalogTranslator { error("Must reuse the existing cache") },directory)
        val source="Flight permits movement through the air."
        val digest=java.security.MessageDigest.getInstance("SHA-256").digest(("pt-v1:"+source).toByteArray())
            .joinToString("") { "%02x".format(it) }
        File(directory,"$digest.txt").writeText("Flight permite movimentar-se pelo ar.")
        assertEquals("Flight permite movimentar-se pelo ar.",repository.portuguese(source))
    }
    @Test fun failedTranslationIsNotCachedAndCanBeRetried() = runBlocking<Unit> {
        val directory=files.newFolder()
        var calls=0
        val repository=CatalogTranslationRepository(CatalogTranslator {
            calls++;if(calls==1)throw IOException("Groq quota") else "Wolverine pode regenerar-se."
        },directory)
        try { repository.portuguese("Wolverine can regenerate.");fail("Expected an error") } catch(_: IOException) { }
        assertFalse(File(directory,repository.key("Wolverine can regenerate.")+".txt").exists())
        assertEquals("Wolverine pode regenerar-se.",repository.portuguese("Wolverine can regenerate."))
        assertEquals(2,calls)
    }
    @Test fun simultaneousRequestsForOneExcerptConsumeOnlyOneCall() = runBlocking<Unit> {
        val calls=AtomicInteger()
        val repository=CatalogTranslationRepository(CatalogTranslator {
            calls.incrementAndGet();delay(100);"Jean Grey possui telepatia."
        },files.newFolder())
        val results=List(4) { async { repository.portuguese("Jean Grey has telepathy.") } }.awaitAll()
        assertTrue(results.all { it=="Jean Grey possui telepatia." })
        assertEquals(1,calls.get())
    }
}
