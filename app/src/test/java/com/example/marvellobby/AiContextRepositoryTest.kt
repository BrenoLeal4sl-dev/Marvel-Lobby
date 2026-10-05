package com.example.marvellobby

import com.example.marvellobby.data.api.*
import com.example.marvellobby.data.model.*
import com.example.marvellobby.data.repository.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiContextRepositoryTest {
    private fun record(id: Int=1,name: String="Spider-Man")=ComicEntity(id,ResourceType.CHARACTER,name,
        "Peter Parker","Catalog summary",null,null,ComicReference(31,"Marvel Comics"),0,null,null,null,
        emptyList(),emptyList(),emptyList(),emptyList())

    private class Source : ComicVineSource {
        val searches=mutableListOf<Pair<ResourceType,String>>()
        val details=mutableListOf<Int>()
        var page: (ResourceType,String)->ComicPage={ _,_->ComicPage(emptyList(),0,0) }
        var full: (Int)->ComicEntity={ error("Unexpected detail request") }
        override suspend fun list(type: ResourceType,query: String,offset: Int,limit: Int,sort: String): ComicPage {
            searches.add(type to query);return page(type,query)
        }
        override suspend fun detail(type: ResourceType,id: Int): ComicEntity { details.add(id);return full(id) }
        override suspend fun issues(ids: List<Int>)=emptyList<ComicEntity>()
    }

    @Test fun portugueseQuestionRetrievesExactCharacterAndReusesItForFollowUp()=runBlocking {
        val actual=record()
        val source=Source().apply {
            page={ _,_->ComicPage(listOf(record(2,"Spider-Man (Ultimate)"),actual),2,2) }
            full={ actual.copy(descriptionHtml="Complete Comic Vine description") }
        }
        val context=AiContextRepository(MarvelRepository(source))
        val found=context.records("Quem foi o homem aranha?",null,emptyList())
        assertEquals(listOf(ResourceType.CHARACTER to "Spider-Man"),source.searches)
        assertEquals(listOf(1),source.details)
        assertEquals("Complete Comic Vine description",found.single().descriptionHtml)
        assertEquals(found,context.records("Quais são os poderes dele?",null,found))
        assertEquals(found,context.records("E os poderes?",null,found))
        assertEquals(1,source.searches.size)
    }

    @Test fun explicitPortugueseSubjectCanChangeAnExistingDetailContext()=runBlocking {
        val ironMan=record(3,"Iron Man")
        val source=Source().apply { page={ _,_->ComicPage(listOf(ironMan),1,1) };full={ ironMan } }
        val found=AiContextRepository(MarvelRepository(source)).records("E quem foi o homem de ferro?",record(4,"Wolverine"),emptyList())
        assertEquals("Iron Man",found.single().name)
        assertEquals(listOf(ResourceType.CHARACTER to "Iron Man"),source.searches)
    }

    @Test fun alreadySelectedLocalizedSubjectDoesNotRequireAnotherApiCall()=runBlocking {
        val source=Source()
        val selected=record()
        assertEquals(listOf(selected),AiContextRepository(MarvelRepository(source)).records("Quem foi o homem aranha?",selected,emptyList()))
        assertTrue(source.searches.isEmpty());assertTrue(source.details.isEmpty())
    }

    @Test fun blockedDetailKeepsRetrievedSummaryAndBlockedListingDoesNotDisableModelAnswer()=runBlocking {
        val actual=record()
        val source=Source().apply {
            page={ _,_->ComicPage(listOf(actual),1,1) }
            full={ throw ComicVineException("Blocked",ComicVineFailure.ACCESS_BLOCKED) }
        }
        val context=AiContextRepository(MarvelRepository(source))
        assertEquals(listOf(actual),context.records("Quem é o Homem-Aranha?",null,emptyList()))
        source.page={ _,_->throw ComicVineException("Blocked",ComicVineFailure.ACCESS_BLOCKED) }
        assertTrue(context.records("Quem é o Homem-Aranha?",null,emptyList()).isEmpty())
    }

    @Test fun unfamiliarNameUsesCleanQueryInsteadOfSendingPortugueseQuestionWords()=runBlocking {
        val source=Source()
        AiContextRepository(MarvelRepository(source)).records("Quem foi a Karma?",null,emptyList())
        assertEquals("Karma",source.searches.first().second)
        assertTrue(source.searches.all { it.second=="Karma" })
    }

    @Test fun cancellationIsNotConvertedIntoAnEmptyContext()=runBlocking {
        val source=Source().apply { page={ _,_->throw CancellationException("Replaced question") } }
        try {
            AiContextRepository(MarvelRepository(source)).records("Quem é o Homem-Aranha?",null,emptyList())
            fail("Expected cancellation")
        } catch(_: CancellationException) { }
    }
}
