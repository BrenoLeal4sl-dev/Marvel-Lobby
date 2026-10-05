package com.example.marvellobby

import com.example.marvellobby.data.model.DirectMessage
import com.example.marvellobby.presentation.social.*
import org.junit.Assert.*
import org.junit.Test

class CommunitySessionStateTest {
    @Test fun requiringSignInPausesRequestsWithoutLosingMessagesOrRetryIdentity() {
        val message=DirectMessage(1,"chat","me","sent-nonce","Hello",1)
        val pending=PendingDirectMessage("Pending","retry-nonce")
        val initial=CommunityState(connected=true,opening=true,profileLoading=setOf("me"),followBusy=setOf("peer"),
            people=mapOf("community" to PeopleState(query="hero",loading=true)),
            chats=mapOf("chat" to DirectChatState(messages=listOf(message),pending=pending,sending=true,
                loading=true,loaded=true,syncedThrough=1)),inbox=InboxState(loading=true,unreadTotal=3))
        val paused=initial.sessionRequired(true)
        assertTrue(paused.requiresSignIn);assertFalse(paused.connected);assertFalse(paused.opening)
        assertTrue(paused.profileLoading.isEmpty());assertTrue(paused.followBusy.isEmpty())
        assertFalse(paused.people.getValue("community").loading)
        assertEquals("hero",paused.people.getValue("community").query)
        val chat=paused.chats.getValue("chat")
        assertEquals(listOf(message),chat.messages);assertEquals(pending,chat.pending)
        assertEquals(1L,chat.syncedThrough);assertTrue(chat.loaded)
        assertFalse(chat.loading);assertFalse(chat.sending)
        assertFalse(paused.inbox.loading);assertEquals(3,paused.inbox.unreadTotal)
    }
    @Test fun newSignInClearsStaleErrorsAndKeepsTheChatSyncCursor() {
        val initial=CommunityState(requiresSignIn=true,notice="Expired",profileErrors=mapOf("me" to "Expired"),
            people=mapOf("community" to PeopleState(query="hero",error="Expired")),inbox=InboxState(error="Expired"),
            chats=mapOf("chat" to DirectChatState(loaded=true,syncedThrough=12,error="Expired",sendError="Expired",
                pending=PendingDirectMessage("Pending","same-nonce"))))
        val recovered=initial.sessionRequired(false)
        assertFalse(recovered.requiresSignIn);assertNull(recovered.notice)
        assertTrue(recovered.profileErrors.isEmpty());assertNull(recovered.people.getValue("community").error)
        assertNull(recovered.inbox.error)
        val chat=recovered.chats.getValue("chat")
        assertNull(chat.error);assertEquals("The previous send was interrupted. Retry to confirm delivery.",chat.sendError)
        assertEquals(12L,chat.syncedThrough);assertEquals("same-nonce",chat.pending!!.clientId)
    }
}
