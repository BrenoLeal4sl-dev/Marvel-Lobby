package com.example.marvellobby

import com.example.marvellobby.data.api.*
import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.repository.MarvelRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MarvelRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    private fun entity(type: ResourceType=ResourceType.CHARACTER,publisherId: Int=31) = ComicEntity(
        1,type,"Test record",null,null,null,null,ComicReference(publisherId,"Test publisher"),
        10,null,null,null,emptyList(),emptyList(),emptyList(),emptyList()
    )

    private class Source : ComicVineSource {
        val calls=mutableListOf<Pair<ResourceType,Int>>()
        var listing: (ResourceType,Int)->ComicPage = { _,_->ComicPage(emptyList(),0,0) }
        var record: ()->ComicEntity = { error("No detail fixture") }
        var issueRecords: (List<Int>)->List<ComicEntity> = { emptyList() }
        override suspend fun list(type: ResourceType,query: String,offset: Int,limit: Int,sort: String): ComicPage {
            calls.add(type to offset)
            return listing(type,offset)
        }
        override suspend fun detail(type: ResourceType,id: Int) = record()
        override suspend fun issues(ids: List<Int>) = issueRecords(ids)
    }
    @Test fun issueMetadataIsBatchedAndFiltersUnexpectedRecords() = runBlocking {
        var requested=emptyList<Int>()
        val source=Source().apply { issueRecords={ ids ->
            requested=ids
            listOf(entity(ResourceType.ISSUE),entity(ResourceType.ISSUE).copy(id=999),entity())
        } }
        val result=MarvelRepository(source).issueSummaries(listOf(1,1))
        assertEquals(listOf(1),requested)
        assertEquals(listOf(1),result.items.map { it.id })
        assertEquals(ResourceType.ISSUE,result.items.single().type)
    }
    @Test fun issueMetadataUsesExpiredCacheWhenOffline() = runBlocking {
        val dir=folder.newFolder()
        var requests=0
        val source=Source().apply { issueRecords={ requests++;listOf(entity(ResourceType.ISSUE)) } }
        val repository=MarvelRepository(source,dir)
        repository.issueSummaries(listOf(1));repository.issueSummaries(listOf(1))
        assertEquals(1,requests)
        dir.listFiles()!!.forEach { assertTrue(it.setLastModified(System.currentTimeMillis()-700_000)) }
        source.issueRecords={ throw ComicVineException("Blocked",ComicVineFailure.ACCESS_BLOCKED) }
        val saved=repository.issueSummaries(listOf(1))
        assertTrue(saved.offline);assertEquals(1,saved.items.single().id)
    }

    @Test fun marvelFilterKeepsApiOffsetsAfterSkippingOtherPublishers() = runBlocking {
        val source=Source().apply {
            listing={ _,offset -> if(offset==0) ComicPage(listOf(entity(publisherId=10)),40,90)
                else ComicPage(listOf(entity()),80,90) }
        }
        val result=MarvelRepository(source).browse(ResourceType.CHARACTER)
        assertEquals(listOf(0,40),source.calls.map { it.second })
        assertEquals(31,result.items.single().publisher!!.id)
        assertEquals(80,result.nextOffset)
        assertTrue(result.hasMore)
    }

    @Test fun hostBlockStillReturnsSavedPowerWithoutRequestingMoreEndpoints() = runBlocking {
        val source=Source().apply { listing={ type,_->ComicPage(listOf(entity(type)),1,1) } }
        val repository=MarvelRepository(source,folder.newFolder())
        repository.browse(ResourceType.POWER,"Healing")
        source.calls.clear()
        source.listing={ _,_->throw ComicVineException("Blocked",ComicVineFailure.ACCESS_BLOCKED) }
        val result=repository.searchResults("Healing")
        assertEquals(listOf(ResourceType.CHARACTER to 0),source.calls)
        assertEquals(ResourceType.POWER,result.items.single().type)
        assertTrue(result.offline)
        assertTrue(result.partial)
    }

    @Test fun cachedDetailRemainsReadableAfterExpiryAndHostBlock() = runBlocking {
        val directory=folder.newFolder()
        val source=Source().apply { record={ entity(ResourceType.POWER) } }
        val repository=MarvelRepository(source,directory)
        assertFalse(repository.loadDetail(ResourceType.POWER,1).offline)
        directory.listFiles()!!.forEach { assertTrue(it.setLastModified(System.currentTimeMillis()-700_000)) }
        source.record={ throw ComicVineException("Blocked",ComicVineFailure.ACCESS_BLOCKED) }
        val result=repository.loadDetail(ResourceType.POWER,1)
        assertTrue(result.offline)
        assertEquals(ResourceType.POWER,result.entity.type)
    }

    @Test fun unavailableSearchWithNoSavedResultsRemainsAnErrorNotAnEmptySuccess() = runBlocking {
        val expected=ComicVineException("Blocked",ComicVineFailure.ACCESS_BLOCKED)
        val source=Source().apply { listing={ _,_->throw expected } }
        try {
            MarvelRepository(source).searchResults("spider man")
            fail("Expected the actual access error")
        } catch (error: ComicVineException) { assertSame(expected,error) }
        assertEquals(1,source.calls.size)
    }
}
