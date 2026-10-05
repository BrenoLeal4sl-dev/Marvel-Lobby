package com.example.marvellobby

import com.example.marvellobby.data.model.*
import org.junit.Assert.*
import org.junit.Test

class CharacterStatisticsTest {
    private fun character()=ComicEntity(1,ResourceType.CHARACTER,"Character",null,null,null,null,null,
        0,null,null,null,emptyList(),emptyList(),emptyList(),emptyList())

    @Test fun unknownFieldsAreNotInventedAsZero() {
        assertTrue(character().characterStatistics().all { it.count==null })
        val known=character().copy(availableFields=setOf("powers","teams","story_arc_credits","count_of_issue_appearances"))
        assertTrue(known.characterStatistics().all { it.count==0 })
    }
    @Test fun countsUseDistinctRecordsRatherThanDuplicateCredits() {
        val ref=ComicReference(5,"Power")
        val stats=character().copy(appearanceCount=200,powers=listOf(ref,ref),teams=listOf(ref,ref)).characterStatistics()
        assertEquals(200,stats[0].count);assertEquals(1,stats[1].count);assertEquals(1,stats[2].count)
        assertNull(stats[3].count)
    }
    @Test fun comparisonsShareScaleAndHandleZeroAndUnavailableData() {
        assertEquals(0.25f,comparisonFraction(5,20)!!,0f)
        assertEquals(1f,comparisonFraction(20,5)!!,0f)
        assertEquals(0f,comparisonFraction(0,0)!!,0f)
        assertNull(comparisonFraction(null,5));assertNull(comparisonFraction(5,null))
    }
    @Test fun legacyMissingEditionTitleIsIdentifiedWithoutTreatingIdAsIssueNumber() {
        assertFalse(ComicReference(500,"Sem título").hasTitle)
        assertFalse(ComicReference(500,"Untitled").hasTitle)
        assertTrue(ComicReference(500,"A real story title").hasTitle)
    }
}
