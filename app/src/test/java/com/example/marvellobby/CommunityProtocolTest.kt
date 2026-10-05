package com.example.marvellobby

import com.example.marvellobby.data.remote.CommunityProtocol
import com.example.marvellobby.data.model.*
import com.example.marvellobby.presentation.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CommunityProtocolTest {
    private val user="11111111-1111-4111-8111-111111111111"
    private val conversation="22222222-2222-4222-8222-222222222222"
    private val nonce="33333333-3333-4333-8333-333333333333"
    private val profile="""{"id":"$user","name":"Breno","username":"breno","bio":"My bio","avatarId":1440,"joinedAt":"2026-10-05T12:00:00Z","email":"private@example.invalid"}"""
    private fun message(id: String="1",conv: String=conversation,text: String="Hello")="""{"id":"$id","conversationId":"$conv","senderId":"$user","clientId":"$nonce","text":"$text","sentAt":"2026-10-05T12:00:00Z"}"""

    @Test fun publicProfilesDiscardPrivateFieldsAndKeepServerIdentity() {
        val data=CommunityProtocol.social("""{"profile":$profile,"followers":2,"following":3,"isFollowing":true,"followsYou":false,"isSelf":false}""") { "avatar:$it" }
        assertEquals(user,data.profile.id);assertEquals("",data.profile.email)
        assertEquals("avatar:1440",data.profile.avatar);assertEquals(2,data.followers)
        assertTrue(data.isFollowing)
    }
    @Test fun malformedIdsCountsAndUnapprovedAvatarsAreRejected() {
        val good="""{"profile":$profile,"followers":2,"following":3,"isFollowing":true,"followsYou":false,"isSelf":false}"""
        for(bad in listOf(good.replace(user,"1-1-1-1-1"),good.replace("1440","999"),good.replace("\"followers\":2","\"followers\":-1"),good.replace("\"followers\":2","\"followers\":2147483648"))) {
            assertThrows(IOException::class.java) { CommunityProtocol.social(bad) { "" } }
        }
    }
    @Test fun crossConversationMessagesAndOversizedBodiesAreRejected() {
        val page="""{"peer":$profile,"items":[${message()}],"hasMore":false,"peerLastRead":"0"}"""
        assertEquals(1,CommunityProtocol.messages(page,conversation) { "" }.items.size)
        assertThrows(IOException::class.java) { CommunityProtocol.messages(page,user) { "" } }
        assertThrows(IOException::class.java) { CommunityProtocol.message(message(id="0")) }
        assertThrows(IOException::class.java) { CommunityProtocol.message(message(text="a".repeat(2001))) }
        assertEquals(2000,CommunityProtocol.message(message(text="a".repeat(2000))).text.length)
    }
    @Test fun anAcknowledgementAndLiveUpdateMergeOnceInChronologicalOrder() {
        val ack=CommunityProtocol.message(message("12"))
        val earlier=ack.copy(id=10,clientId=user)
        val later=ack.copy(id=15,clientId=conversation)
        val merged=mergeDirectMessages(listOf(ack,later),listOf(earlier,ack))
        assertEquals(listOf(10L,12L,15L),merged.map { it.id })
        assertEquals(merged,mergeDirectMessages(merged,listOf(ack)))
    }
    @Test fun realtimeSignalsValidateTypeAndConversationWithoutContainingTexts() {
        assertEquals("ready",CommunityProtocol.event("""{"type":"ready"}""").type)
        assertEquals(conversation,CommunityProtocol.event("""{"type":"read","conversationId":"$conversation"}""").conversationId)
        assertThrows(IOException::class.java) { CommunityProtocol.event("""{"type":"arbitrary"}""") }
        assertThrows(IOException::class.java) { CommunityProtocol.event("""{"type":"messages","conversationId":"bad"}""") }
        assertThrows(IOException::class.java) { CommunityProtocol.event(" ".repeat(257)) }
    }
    @Test fun followingAndFollowersKeepDistinctSearchesAndChatRestoresBackNavigation() {
        val followers=Route("socialPeople",title="followers",userId=user)
        val following=followers.copy(title="following")
        assertNotEquals(followers.key,following.key)
        val navigator=AppNavigator()
        navigator.restore(Route("directChat",userId=conversation),listOf(Route("community"),followers,Route("publicProfile",userId=user)))
        assertEquals("home",navigator.current.section)
        assertEquals("publicProfile",navigator.back(true)!!.screen)
        assertEquals(followers.key,navigator.back(true)!!.key)
        assertEquals("community",navigator.back(true)!!.screen)
        navigator.restore(Route("socialPeople",title="invalid",userId=user),emptyList())
        assertEquals("home",navigator.current.screen)
    }
}
