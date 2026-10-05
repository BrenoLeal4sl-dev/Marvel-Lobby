package com.example.marvellobby

import com.example.marvellobby.data.model.AiCatalogQuery
import com.example.marvellobby.data.model.ResourceType
import org.junit.Assert.*
import org.junit.Test

class AiCatalogQueryTest {
    @Test fun portugueseSpiderManQuestionsResolveWithoutTranslatingThroughAnotherModel() {
        val expected=listOf(AiCatalogQuery.Subject(ResourceType.CHARACTER,"Spider-Man"))
        listOf("Quem foi o homem aranha?","Quem é o Homem-Aranha?","HOMEM—ARANHA","Tell me about Spider Man","Spider-Man e Peter Parker")
            .forEach { assertEquals(it,expected,AiCatalogQuery.subjects(it)) }
    }
    @Test fun accentsTeamsPowersAndArcsResolveToTheAppropriateResource() {
        assertEquals(AiCatalogQuery.Subject(ResourceType.CHARACTER,"Captain America"),AiCatalogQuery.subjects("Quem foi o capitão américa?").single())
        assertEquals(AiCatalogQuery.Subject(ResourceType.TEAM,"Avengers"),AiCatalogQuery.subjects("Quem faz parte dos Vingadores?").single())
        assertEquals(AiCatalogQuery.Subject(ResourceType.POWER,"Healing"),AiCatalogQuery.subjects("Quais personagens têm regeneração?").single())
        assertEquals(AiCatalogQuery.Subject(ResourceType.STORY_ARC,"Civil War"),AiCatalogQuery.subjects("Me explique Guerra Civil.").single())
    }
    @Test fun multipleSubjectsRetainTheirMentionOrderWithoutDuplicates() {
        assertEquals(listOf("Spider-Man","Iron Man"),AiCatalogQuery.subjects("Compare o Homem-Aranha com o Homem de Ferro e Peter Parker.").map { it.name })
    }
    @Test fun aliasesRespectWordBoundariesAndDoNotTurnCommonNounsIntoCharacters() {
        assertTrue(AiCatalogQuery.subjects("hulkbuster e homem aranhado").isEmpty())
        assertTrue(AiCatalogQuery.subjects("Qual sua visão sobre essa coisa?").isEmpty())
        assertEquals("Vision",AiCatalogQuery.subjects("Quem é o visão?").single().name)
        assertEquals("Thing",AiCatalogQuery.subjects("Quem foi o Coisa?").single().name)
        assertEquals("Beast",AiCatalogQuery.subjects("Quais os poderes do fera?").single().name)
        assertEquals("Miles Morales",AiCatalogQuery.subjects("Quem é o Homem-Aranha Miles Morales?").single().name)
    }
    @Test fun fallbackRemovesPastTenseAndFollowUpsRecognizePronouns() {
        assertEquals("Karma",AiCatalogQuery.searchText("Quem foi a Karma?"))
        assertEquals("Karma",AiCatalogQuery.searchText("Who was Karma?"))
        assertTrue(AiCatalogQuery.isFollowUp("Quais são os poderes dele?"))
        assertTrue(AiCatalogQuery.isFollowUp("What are his powers?"))
        assertTrue(AiCatalogQuery.isFollowUp("E os poderes?"))
        assertFalse(AiCatalogQuery.isFollowUp("Quem é Wolverine?"))
    }
}
