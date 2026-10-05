package com.example.marvellobby

import com.example.marvellobby.presentation.*
import com.example.marvellobby.data.model.ResourceType
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class AppNavigatorTest {
    @Test fun nestedRelationshipsReturnThroughEveryScreenInOrder() {
        val nav=AppNavigator()
        nav.navigate(Route("explore"),clearHistory=true)
        nav.navigate(Route("catalog",ResourceType.CHARACTER))
        nav.navigate(Route("detail",ResourceType.CHARACTER,1440,"Wolverine"))
        nav.navigate(Route("detail",ResourceType.POWER,1,"Flight"))
        nav.navigate(Route("detail",ResourceType.CHARACTER,2,"Related record"))
        assertEquals("explore",nav.current.section)
        assertEquals(ResourceType.POWER,nav.back(true)!!.type)
        assertEquals(1440,nav.back(true)!!.id)
        assertEquals("catalog",nav.back(true)!!.screen)
        assertEquals("explore",nav.back(true)!!.screen)
        assertEquals("home",nav.back(true)!!.screen)
        assertNull(nav.back(true))
    }

    @Test fun aiSourcesReturnToChatThenOriginalFavorite() {
        val nav=AppNavigator()
        nav.navigate(Route("favorites"),clearHistory=true)
        nav.navigate(Route("detail",ResourceType.CHARACTER,1440))
        nav.navigate(Route("ai"))
        nav.navigate(Route("detail",ResourceType.TEAM,3173))
        assertEquals("ai",nav.current.section)
        assertEquals("ai",nav.back(true)!!.screen)
        assertEquals("favorites",nav.back(true)!!.section)
        assertEquals("favorites",nav.back(true)!!.screen)
    }

    @Test fun finishingOnboardingAndSwitchingAuthDoesNotReopenWelcome() {
        val nav=AppNavigator()
        nav.navigate(Route("welcome"),clearHistory=true)
        nav.navigate(Route("register"),clearHistory=true)
        nav.navigate(Route("login"),replaceCurrent=true)
        nav.navigate(Route("register"),replaceCurrent=true)
        assertTrue(nav.backStack.isEmpty())
        assertEquals("login",nav.back(false)!!.screen)
        assertNull(nav.back(false))
    }

    @Test fun editProfileReturnsToItsCallerWithoutLosingPreviousScreens() {
        val nav=AppNavigator()
        nav.navigate(Route("home"),clearHistory=true)
        nav.navigate(Route("profile"))
        nav.navigate(Route("settings"))
        nav.navigate(Route("editProfile"))
        assertEquals("settings",nav.back(true)!!.screen)
        assertEquals("profile",nav.back(true)!!.screen)
        assertEquals("home",nav.back(true)!!.screen)
    }

    @Test fun processRecreationRestoresTheBackPathAndSelectedSection() {
        val old=AppNavigator()
        old.navigate(Route("favorites"),clearHistory=true)
        old.navigate(Route("detail",ResourceType.CHARACTER,1440))
        old.navigate(Route("detail",ResourceType.POWER,1))
        val gson=Gson()
        val restored=AppNavigator()
        restored.restore(gson.fromJson(gson.toJson(old.current),Route::class.java),
            gson.fromJson(gson.toJson(old.backStack),Array<Route>::class.java).toList())
        assertEquals("favorites",restored.current.section)
        assertEquals(1440,restored.back(true)!!.id)
        assertEquals("favorites",restored.back(true)!!.screen)
    }

    @Test fun publicProfileRestoresOnlyCanonicalUuidAndReturnsToCaller() {
        val nav=AppNavigator()
        val id="74ad39c4-273b-4b5b-997a-00e01ff7ff21"
        nav.restore(Route("publicProfile",userId=id),listOf(Route("profile")))
        assertEquals(id,nav.current.userId)
        assertEquals("home",nav.current.section)
        assertEquals("profile",nav.back(true)!!.screen)
        nav.restore(Route("publicProfile",userId="not-a-uuid"),emptyList())
        assertEquals("home",nav.current.screen)
    }

    @Test fun logoutClearsProtectedRoutesAndSameRouteDoesNotDuplicateHistory() {
        val nav=AppNavigator()
        nav.navigate(Route("home"),clearHistory=true)
        nav.navigate(Route("profile"))
        nav.navigate(Route("profile"))
        assertEquals(1,nav.backStack.size)
        nav.navigate(Route("login"),clearHistory=true)
        assertNull(nav.back(false))
        assertTrue(nav.backStack.isEmpty())
    }
}
