package com.example.marvellobby

import com.example.marvellobby.data.api.*
import com.example.marvellobby.data.model.ResourceType
import org.junit.Assert.*
import org.junit.Test

class ComicVineRequestsTest {
    @Test fun characterSearchUsesRelevanceIndexInsteadOfLiteralNameFilter() {
        val request = ComicVineRequests.list(ResourceType.CHARACTER, "  spider   man ", 40, 40, ComicVineRequests.RECENT)
        assertEquals("search/", request.path)
        assertEquals("spider man", request.parameters["query"])
        assertEquals("character", request.parameters["resources"])
        assertEquals("40", request.parameters["offset"])
        assertFalse(request.parameters.containsKey("filter"))
        assertFalse(request.parameters.containsKey("sort"))
    }

    @Test fun browsingUsesSupportedRecentOrderAndKeepsAlphabeticalOrderAvailable() {
        assertEquals(ComicVineRequests.RECENT, ComicVineRequests.list(ResourceType.CHARACTER,"",0,40,ComicVineRequests.RECENT).parameters["sort"])
        assertEquals("name:asc", ComicVineRequests.list(ResourceType.CHARACTER,"",0,40,"name:asc").parameters["sort"])
        assertEquals("name:asc", ComicVineRequests.list(ResourceType.POWER,"",0,40,ComicVineRequests.RECENT).parameters["sort"])
    }

    @Test fun powersKeepTheirSupportedCollectionFilter() {
        val request=ComicVineRequests.list(ResourceType.POWER,"Healing",0,40,ComicVineRequests.RECENT)
        assertEquals("powers/",request.path)
        assertEquals("name:Healing",request.parameters["filter"])
    }

    @Test fun unsupportedAppearanceSortCannotBeSentAgain() {
        val request=ComicVineRequests.list(ResourceType.CHARACTER,"",0,40,"count_of_issue_appearances:desc")
        assertEquals("date_last_updated:desc",request.parameters["sort"])
    }

    @Test fun storyArcsUseCollectionFilterBecauseSearchIndexReturnsEmpty() {
        val request=ComicVineRequests.list(ResourceType.STORY_ARC,"Civil War",0,40,ComicVineRequests.RECENT)
        assertEquals("story_arcs/",request.path)
        assertEquals("name:Civil War",request.parameters["filter"])
        assertFalse(request.parameters.containsKey("resources"))
    }

    @Test fun firewallHtmlIsNotReportedAsAnInvalidKey() {
        assertEquals(ComicVineFailure.ACCESS_BLOCKED,ComicVineErrors.classify(403,null))
        assertEquals(ComicVineFailure.INVALID_KEY,ComicVineErrors.classify(200,100))
        assertEquals(ComicVineFailure.INVALID_KEY,ComicVineErrors.classify(403,100))
        assertEquals(ComicVineFailure.RATE_LIMIT,ComicVineErrors.classify(429,null))
        assertEquals(ComicVineFailure.API,ComicVineErrors.classify(200,null))
        assertNull(ComicVineErrors.classify(200,1))
    }
}
