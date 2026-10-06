package com.example.marvellobby

import com.example.marvellobby.data.remote.PublicFavoritesProtocol
import com.example.marvellobby.data.model.ResourceType
import com.example.marvellobby.data.model.PublicFavorite
import com.example.marvellobby.data.model.PublicFavoritesPage
import com.example.marvellobby.presentation.social.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PublicFavoritesProtocolTest {
    @Test fun hidingAProfileAlsoClearsItsPreviouslyCachedCategoriesWithoutAffectingOtherPeople() {
        val hero=PublicFavorite(1,ResourceType.CHARACTER,"Spider-Man",null)
        val cached=PublicFavoritesState(items=listOf(hero),visible=true,loaded=true)
        val before=CommunityState(publicFavorites=mapOf("one:CHARACTER" to cached,"one:POWER" to cached,"two:CHARACTER" to cached))
        val after=before.receiveFavorites("one",ResourceType.CHARACTER,PublicFavoritesPage(emptyList(),null,false),false)
        assertTrue(after.publicFavorites["one:CHARACTER"]!!.items.isEmpty())
        assertTrue(after.publicFavorites["one:POWER"]!!.items.isEmpty())
        assertEquals(listOf(hero),after.publicFavorites["two:CHARACTER"]!!.items)
        assertFalse(after.publicFavorites["one:POWER"]!!.visible)
    }
    private val record="""{"id":1,"type":"character","name":"Spider-Man","imageUrl":"https://comicvine.gamespot.com/a.png"}"""
    @Test fun publicProjectionKeepsCatalogIdentityAndOmitsUntrustedImages() {
        val page=PublicFavoritesProtocol.page("""{"visible":true,"items":[$record],"next":null}""",ResourceType.CHARACTER,0)
        assertEquals("Spider-Man",page.items.single().name);assertEquals(1,page.items.single().id)
        val blocked=PublicFavoritesProtocol.page("""{"visible":true,"items":[${record.replace("comicvine.gamespot.com","example.invalid")}],"next":null}""",ResourceType.CHARACTER,0)
        assertNull(blocked.items.single().imageUrl)
    }
    @Test fun hiddenProfilesCannotLeakItemsAndWrongCategoriesCannotOpenIncorrectDetails() {
        for(json in listOf("""{"visible":false,"items":[$record],"next":null}""","""{"visible":true,"items":[$record,$record],"next":null}""","""{"visible":true,"items":[$record],"next":0}""","""{"visible":true,"items":[${record.replace("\"id\":1","\"id\":1.2")}],"next":null}"""))
            assertThrows(IOException::class.java) { PublicFavoritesProtocol.page(json,ResourceType.CHARACTER,0) }
        assertThrows(IOException::class.java) { PublicFavoritesProtocol.page("""{"visible":true,"items":[$record],"next":null}""",ResourceType.POWER,0) }
        assertTrue(PublicFavoritesProtocol.page("""{"visible":false,"items":[],"next":null}""",ResourceType.CHARACTER,0).items.isEmpty())
    }
}
