package com.example.marvellobby

import com.example.marvellobby.data.model.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ComicInformationTest {
    private fun record(type: ResourceType) = ComicEntity(1,type,"Record",null,null,null,null,null,
        0,null,null,null,emptyList(),emptyList(),emptyList(),emptyList())

    @Test fun issueInformationUsesVolumeAndNumberWithoutInventingMissingData() {
        val issue=record(ResourceType.ISSUE).copy(issueNumber="180",volume=ComicReference(2406,"The Incredible Hulk"))
        assertEquals("The Incredible Hulk #180",issue.issueLabel)
        assertNull(record(ResourceType.ISSUE).issueLabel)
        assertEquals("#180 · And the Wind Howls... Wendigo!",ComicReference(14667,"And the Wind Howls... Wendigo!","180").displayName)
    }

    @Test fun oldSavedRecordsRemainReadableWhenNewOptionalFieldsAreAbsent() {
        val record=Gson().fromJson("""{"id":1,"type":"ISSUE","name":"Saved issue"}""",ComicEntity::class.java)
        assertNull(record.issueLabel)
        val ref=Gson().fromJson("""{"id":1,"name":"Saved reference"}""",ComicReference::class.java)
        assertEquals("Saved reference",ref.displayName)
    }

    @Test fun relationshipLabelsDoNotCallStoryCharactersTeamMembers() {
        assertEquals("RELATED / CHARACTERS",record(ResourceType.STORY_ARC).characterRelationshipLabel)
        assertEquals("CHARACTERS WITH THIS POWER",record(ResourceType.POWER).characterRelationshipLabel)
        assertEquals("MEMBERS / RELATED CHARACTERS",record(ResourceType.TEAM).characterRelationshipLabel)
        assertFalse(ResourceType.ISSUE.canFavorite)
        assertTrue(ResourceType.CHARACTER.canFavorite)
    }
}
